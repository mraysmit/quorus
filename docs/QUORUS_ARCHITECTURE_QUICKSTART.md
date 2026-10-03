<img src="quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Architecture Quickstart

**Version:** 2.7  
**Date:** 2026-10-03  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Scope:** Current implementation snapshot

## What Quorus Is

Quorus is a Java 27 file transfer platform with two practical execution modes:

- **Direct execution** via `quorus-core`, where an application or workflow runs transfers in-process through `SimpleTransferEngine`
- **Distributed execution** via `quorus-controller` and `quorus-agent`, where controller nodes replicate cluster state with Raft and agents execute transfer work

Of the modules in the default build, only `quorus-controller` still uses Vert.x 5, while it migrates to plain Java under [ADR-0012](../docs-design/architecture-decisions/ADR-0012-JAVA-RUNTIME-AND-STRUCTURED-CONCURRENCY.md). The others use blocking APIs and virtual threads and have no Vert.x dependency.

The core implementation anchors are:

- `quorus-core` for transfer models, protocol adapters, and the transfer engine
- `quorus-workflow` for YAML parsing, validation, variable resolution, and workflow execution
- `quorus-controller` for the embedded HTTP API, Raft, and replicated state
- `quorus-agent` for agent registration, heartbeat, polling, and job execution
- `quorus-tenant` for tenant and quota related services
- `quorus-integration-examples` for runnable transfer, workflow, tenant, and agent examples
- `quorus-benchmarks` for the benchmark harness, built only with `-Pbenchmarks`; its Raft commit benchmark drives the controller's engine and so uses Vert.x

## Controller-First Design

Each controller node embeds:

- an HTTP API server
- a Raft node with gRPC transport
- a replicated state store

This is wired in `QuorusControllerVerticle`, which loads configuration, creates Raft transport and storage, builds the Raft node, starts the gRPC server, starts Raft, and only then starts the HTTP API.

The live startup sequence is implemented in `quorus-controller/src/main/java/dev/mars/quorus/controller/QuorusControllerVerticle.java`.

## Runtime Defaults

Current controller defaults come from `AppConfig`:

- HTTP bind address: `127.0.0.1` (the controller image sets `QUORUS_HTTP_HOST=0.0.0.0`)
- HTTP port: `8080`
- Raft port: `9080`
- Security profile: `production`, so HTTP and Raft mutual TLS are required unless a development profile is selected explicitly (as the Compose topologies do)
- Raft storage type: `raftlog` (external library only)
- Snapshot enabled: `true`
- Snapshot threshold: `10000` entries
- Snapshot eligibility check interval: `60000` ms
- Raft I/O pool size: `10`
- Raft I/O queue size: `1000`
These values are sourced from `quorus-controller/src/main/java/dev/mars/quorus/controller/config/AppConfig.java` and the packaged `quorus-controller.properties`.

The product version is the root pom version (`1.0-SNAPSHOT` today), written into the build and reported by the controller at `/api/v1/info` and `/health` and by the agent in its registration and `/status`. It is not a setting. The OpenAPI contract's `info.version` (`1.3.2-alpha`) is the API contract version, a separate thing. See the [versioning policy](../docs-design/reference/QUORUS_VERSIONING_AND_COMPATIBILITY_POLICY.md#product-version).

## Operational Model

### Direct Transfer Execution

`SimpleTransferEngine` is the current execution primitive for actual transfers. It:

- validates requests
- routes work to protocol adapters through `ProtocolFactory`
- runs each transfer on the calling thread (blocking), with retries and a concurrency limit
- tracks direction-aware metrics
- exposes shutdown, cancellation, pause, and resume operations at engine level

This implementation lives in `quorus-core/src/main/java/dev/mars/quorus/transfer/SimpleTransferEngine.java`.

### Workflow Execution

`SimpleWorkflowEngine` executes workflows by:

- validating definitions with `YamlWorkflowDefinitionParser`
- resolving variables
- building a dependency graph
- running transfer groups in normal, dry run, or virtual run mode

The current YAML parser supports:

- `metadata`
- `spec.variables`
- `spec.execution`
- `spec.transferGroups`
- group-level `description`, `dependsOn`, `condition`, `variables`, `continueOnError`, `retryCount`
- transfer-level `name`, `source`, `destination`, `protocol`, `options`, `condition`

Conditions are parsed and variable-resolved today. The current workflow engine does not implement a separate condition evaluation subsystem beyond carrying those resolved strings through execution.

### Distributed Controller and Agent State

The controller stores and replicates:

- agents
- transfer jobs, with their ordered event ledger
- job assignments, transfer attempts and their fencing generations
- the job queue
- routes
- the service-connection and secret-reference registry, with its security events
- system metadata

These mutations are applied in `QuorusStateStore` through Raft commands.

## Routes: Current Status

Routes are **implemented as replicated configuration and API-managed lifecycle state**.

Current implementation includes:

- `RouteConfiguration`
- `TriggerConfiguration`
- `RouteStatus`
- route CRUD and suspend/resume endpoints in the controller API
- route replication in Raft commands, codecs, snapshots, and state store

Current implementation does **not** show a controller startup service that evaluates route triggers and dispatches transfers automatically. `QuorusControllerVerticle` starts Raft, HTTP, and graceful shutdown hooks, but does not wire a route trigger evaluator or transfer dispatcher.

That means the route model and route HTTP API are live, while automatic route-triggered execution remains a separate implementation concern.

## HTTP Surface

The [OpenAPI contract](../quorus-controller/src/main/resources/openapi/quorus-controller-v1.yaml) is the reference for the current controller API; a running controller also serves it at `GET /api/v1/openapi.yaml`, and a test fails if it and the registered routes disagree. The routes are registered in `quorus-controller/src/main/java/dev/mars/quorus/controller/http/HttpApiServer.java`.

## Protocol Support

The protocol factory currently registers:

- HTTP and HTTPS
- FTP and FTPS
- SFTP
- SMB and CIFS
- NFS

Every adapter is blocking and runs on the calling thread. Adapter-level resume support is reported as disabled by every adapter. Only the HTTP adapter reports pause support.

## Observability

Current observability features include:

- `/health/live`
- `/health/ready`
- `/health`
- `/status`
- `/raft/status`
- `/api/v1/info`
- `/metrics`

Controller metrics are exposed through the embedded HTTP server, and transfer and workflow execution record OpenTelemetry-backed metrics in the core and workflow modules.

## Tenant-Agent Isolation

From the controller's perspective, the agent fleet carries tenant partitioning fields. A single agent declares exactly one tenant, and selected polling and status paths enforce matching tenant values.

The enforcement path is:

1. Agent registers with `tenantId` (required field — `400` if absent)
2. Transfer job is created with `tenantId` (required field — `400` if absent)
3. A caller assigns the job to an agent with `POST /api/v1/assignments`; the assignment reference and tenant invariants are enforced when it is committed
4. `GET /api/v1/agents/:agentId/jobs` filters the assignment list to the agent's own tenant before returning
5. `POST /api/v1/jobs/:jobId/status` verifies the submitting agent's tenant matches the job's tenant (`403` if not)

These checks run inside the Vert.x controller against Raft-replicated state. The controller runs no scheduler: `AgentSelectionService` and `JobAssignmentService` exist but are not started, so nothing selects an agent or assigns a submitted job automatically (register item `ENG-01`). The tenant model is stored as a field on `AgentInfo` and `TransferJobSnapshot` in `QuorusStateStore`.

In the production profile, the API derives tenant authority from an authenticated mTLS or trusted-gateway identity, and assignment references and tenant invariants are enforced both at the handler boundary and during replicated state application. A supplied `tenantId` is not identity by itself, and the development Compose profiles intentionally disable this boundary. See [Architecture Specification §3](QUORUS_ARCHITECTURE_SPECIFICATION.md#3-capability-status) and the [Security Deployment Guide](QUORUS_SECURITY_DEPLOYMENT_GUIDE.md).

## Java Baseline

The repository root `pom.xml` sets:

- `java.version = 27`
- `maven.compiler.release = ${java.version}`

`.java-version` also names 27. Use JDK 27 for builds, tests, and IDE tooling in this repository.

## Recommended Reading

- `docs/QUORUS_ARCHITECTURE_SPECIFICATION.md` — canonical architecture, guarantees, and release requirements
- `docs/QUORUS_REST_API_SPECIFICATION.md` — complete normative REST control and operations contract
- `quorus-controller/src/main/resources/openapi/quorus-controller-v1.yaml` — the OpenAPI contract for the current controller API, also served at `GET /api/v1/openapi.yaml`
- `docs/QUORUS_USER_GUIDE.md`
- `docs/QUORUS_WORKFLOWS_README.md`
- `docs/QUORUS_YAML_SYNTAX_GUIDE.md`
- `docs-design/task/QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md` — the delivery plan
- `docs-design/task/QUORUS_OUTSTANDING_WORK_REGISTER.md` — every open task and decision
