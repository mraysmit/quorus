# Quorus Architecture Quickstart

**Version:** 2.0  
**Date:** 2026-03-14  
**Scope:** Current implementation snapshot

## What Quorus Is

Quorus is a Java 25, Vert.x 5 based file transfer platform with two practical execution modes:

- **Direct execution** via `quorus-core`, where an application or workflow runs transfers in-process through `SimpleTransferEngine`
- **Distributed execution** via `quorus-controller` and `quorus-agent`, where controller nodes replicate cluster state with Raft and agents execute transfer work

The core implementation anchors are:

- `quorus-core` for transfer models, protocol adapters, and the transfer engine
- `quorus-workflow` for YAML parsing, validation, variable resolution, and workflow execution
- `quorus-controller` for the embedded HTTP API, Raft, and replicated state
- `quorus-agent` for agent registration, heartbeat, polling, and job execution
- `quorus-tenant` for tenant and quota related services

## Controller-First Design

Each controller node embeds:

- an HTTP API server
- a Raft node with gRPC transport
- a replicated state store

This is wired in `QuorusControllerVerticle`, which loads configuration, creates Raft transport and storage, builds the Raft node, starts the gRPC server, starts Raft, and only then starts the HTTP API.

The live startup sequence is implemented in `quorus-controller/src/main/java/dev/mars/quorus/controller/QuorusControllerVerticle.java`.

## Runtime Defaults

Current controller defaults come from `AppConfig`:

- HTTP port: `8080`
- Raft port: `9080`
- Raft storage type: `file`
- Snapshot enabled: `true`
- Snapshot threshold: `10000` entries
- Snapshot eligibility check interval: `60000` ms
- Raft I/O pool size: `10`
- Raft I/O queue size: `1000`
- Reported application version default: `2.0-ext`

These values are sourced from `quorus-controller/src/main/java/dev/mars/quorus/controller/config/AppConfig.java`.

## Operational Model

### Direct Transfer Execution

`SimpleTransferEngine` is the current execution primitive for actual transfers. It:

- validates requests
- routes work to protocol adapters through `ProtocolFactory`
- executes reactively with Vert.x futures
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
- transfer jobs
- job assignments
- routes
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

The embedded controller API currently exposes:

- health and status endpoints
- metrics
- controller info
- agent registration and heartbeat endpoints
- transfer CRUD
- job status updates
- job assignment endpoints
- route CRUD and lifecycle endpoints

The routes are registered in `quorus-controller/src/main/java/dev/mars/quorus/controller/http/HttpApiServer.java`.

## Protocol Support

The protocol factory currently registers:

- HTTP and HTTPS
- FTP and FTPS
- SFTP
- SMB and CIFS
- NFS

Adapter-level resume support is currently reported as disabled by the protocol implementations. HTTP reports pause support; the blocking adapters currently report pause support as disabled.

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

## Java Baseline

The repository root `pom.xml` sets:

- `maven.compiler.source = 25`
- `maven.compiler.target = 25`

Use JDK 25 for builds, tests, and IDE tooling in this repository.

## Recommended Reading

- `docs/QUORUS_API_REFERENCE.md`
- `docs/QUORUS_USER_GUIDE.md`
- `docs/QUORUS_WORKFLOWS_README.md`
- `docs/QUORUS_YAML_SYNTAX_GUIDE.md`
- `docs-design/task/QUORUS_ALPHA_IMPLEMENTATION_PLAN.md`