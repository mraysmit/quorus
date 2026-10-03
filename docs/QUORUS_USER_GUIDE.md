<img src="quorus-logo.png" alt="Quorus" width="120"/>

# Quorus User Guide

**Version:** 2.6  
**Date:** 2026-10-03  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Scope:** Current implementation guide

## Introduction

Quorus is a file transfer platform that can run in two modes:

- **Direct mode** using `quorus-core` and optionally `quorus-workflow`
- **Distributed mode** using `quorus-controller` and `quorus-agent`

The direct path is the most complete end-user path today for executing transfer workloads. The distributed path provides controller-managed state, agent registration, assignments, and route management on top of Raft-replicated controller state.

## What Is Implemented Today

### Transfer Engine

`SimpleTransferEngine` is the live transfer execution component. It supports:

- blocking transfer execution on the calling thread, with a concurrency limit
- retry handling
- progress tracking
- metrics and health reporting
- cancellation
- pause and resume at engine/job level
- graceful shutdown with drain waiting

### Protocol Adapters

The protocol factory currently registers these adapter families:

- HTTP / HTTPS
- FTP / FTPS
- SFTP
- SMB / CIFS
- NFS

Current adapter behavior to be aware of:

- adapter-level resume support is currently reported as disabled
- HTTP reports pause support
- the blocking adapters currently report pause support as disabled

If you need resumable transport semantics, treat them as not implemented at adapter level in the current codebase.

### Workflows

The workflow module supports:

- YAML parsing
- schema and semantic validation
- variable substitution
- dependency graphs
- normal execution
- dry run execution
- virtual run execution

The workflow engine validates every workflow before running it, then executes transfer groups in dependency order. YAML `condition` values are parsed and resolved but never evaluated, and transfer `options` have no effect; the [YAML Syntax Guide](QUORUS_YAML_SYNTAX_GUIDE.md) lists the validation rules and these limits.

### Controller API

The [OpenAPI contract](../quorus-controller/src/main/resources/openapi/quorus-controller-v1.yaml) describes every current endpoint; a running controller serves it at `GET /api/v1/openapi.yaml`. In outline, the API provides:

- health, readiness, status and metrics endpoints
- agent registration, heartbeat and job polling
- transfer submission, lookup and removal, with progress, events and attempts
- job status reporting
- assignment creation, acceptance, rejection, status and cancellation
- route CRUD and suspend/resume operations
- service connections, secret references and security events
- identity, authorization explanation and trust endpoints

The controller does not assign transfers to agents by itself: a submitted transfer runs only after a caller assigns it with `POST /api/v1/assignments`.

### Routes

Routes are implemented today as:

- a replicated route model in `quorus-core`
- route commands and codecs in `quorus-controller`
- route storage in `QuorusStateStore`
- route CRUD and lifecycle endpoints in the HTTP API

What is **not** currently wired by controller startup is an always-on background trigger evaluator that watches routes and dispatches transfers automatically. In other words, route persistence and route APIs are live, but automatic route-triggered execution should not be documented as a completed runtime feature.

## Getting Started

### Java Baseline

This repository builds with Java 27. The root Maven build sets `java.version` to 27 and `maven.compiler.release` to `${java.version}`.

### Direct Execution Path

Use direct execution when you want to run transfers or workflows without deploying a controller cluster.

Typical module set:

- `quorus-core`
- `quorus-workflow` when you want YAML workflows

This is the most straightforward way to use Quorus for application-level transfer orchestration.

### Distributed Execution Path

Use controller plus agents when you need:

- controller-managed state
- multi-node Raft-backed coordination
- agent registration and polling
- transfer assignment to agents through the API
- route CRUD through the controller API

## Current Protocol Guidance

### HTTP and HTTPS

Current implementation supports:

- direct HTTP download and upload paths
- streaming execution on Apache HttpClient 5, with governed address pinning

Workflow transfer `options` are not passed to the adapter.

Do **not** assume the current implementation provides:

- OAuth2 token management
- resumable range downloads/uploads
- fully documented proxy authentication flows

Those claims appeared in older docs but are not backed by the current adapter implementation.

### FTP and FTPS

Current implementation supports:

- FTP and FTPS handling in a single adapter family
- upload and download routing based on transfer direction

Do **not** assume adapter-level resume support. The current adapter reports `supportsResume() == false`.

### SFTP

Current implementation supports:

- upload and download routing based on transfer direction
- blocking transfer execution on the calling thread (in the agent, a virtual thread per job)

Do **not** assume adapter-level resume support. The current adapter reports `supportsResume() == false`.

### SMB

Current implementation supports SMB and CIFS registration in the protocol factory.

Do **not** assume adapter-level resume or pause support. The current adapter reports both as disabled.

### NFS

The NFS adapter does not speak the NFS protocol. It reads and writes an export that the operating system has already mounted, translating `nfs://server/export/path` to `<mount root>/server/export/path`. The mount root is the agent setting `quorus.agent.nfs.mount-root` (environment `QUORUS_AGENT_NFS_MOUNT_ROOT`); when it is empty the adapter uses `/mnt`, or `C:\nfs` on Windows.

A governed transfer that carries a service credential is refused over NFS unless the agent attests that the mount is encrypted and authenticated, with `quorus.agent.nfs.encrypted-authenticated-mount=true` (environment `QUORUS_AGENT_NFS_ENCRYPTED_AUTHENTICATED_MOUNT`). Quorus cannot check that attestation; it is the deployment's responsibility.

Do **not** assume adapter-level resume or pause support. The current adapter reports both as disabled.

## Observability

Current controller observability endpoints:

- `/health/live`
- `/health/ready`
- `/health`
- `/status`
- `/raft/status`
- `/api/v1/info`
- `/metrics`

Current core and workflow modules also emit OpenTelemetry-backed metrics through their observability components.

Each transfer also has three operator resources: `GET /api/v1/transfers/{jobId}/progress` (bytes and percentage, average rate, estimated completion, telemetry freshness, stall state, and time remaining against the required completion time), `GET /api/v1/transfers/{jobId}/events` (the ordered submitted, assigned, accepted, started and progress events), and `GET /api/v1/transfers/{jobId}/attempts` (attempt history). Agents report progress while a transfer runs.

These do not yet make up the complete transfer-operations capability that critical and time-sensitive processing needs. Actionable alerts and escalation on stalls and deadline risk, and a complete end-to-end operator timeline, are required by the canonical architecture and REST API specifications and are still conformance gaps.

## Tenant Isolation

Quorus currently enforces selected tenant checks between registered agents and transfer jobs. Every agent declares one tenant, and polling and selected status paths filter or reject mismatched tenant fields.

The production controller authenticates callers through mTLS or a trusted gateway, derives tenant authority from the verified identity, and enforces assignment references and tenant invariants at both the HTTP and replicated state boundaries. A supplied `tenantId` is not proof of identity by itself, and the development Compose profiles deliberately disable request authentication. See [Architecture Specification §3](QUORUS_ARCHITECTURE_SPECIFICATION.md#3-capability-status) and the [Security Deployment Guide](QUORUS_SECURITY_DEPLOYMENT_GUIDE.md).

### Agent Configuration

Each agent must declare its tenant; it refuses to start without one. Set it in the agent's environment:

```bash
export QUORUS_AGENT_TENANT_ID=acme-corp
```

The legacy name `AGENT_TENANT_ID` is still read, but `QUORUS_AGENT_TENANT_ID` wins when both are set. The property `quorus.agent.tenant.id` in `quorus-agent.properties` is packaged inside the agent jar, so it is not an operator setting.

The agent defaults to the production security profile: it requires an `https` controller URL, its own client certificate and key, and a trust bundle for the controller (see the [Security Deployment Guide](QUORUS_SECURITY_DEPLOYMENT_GUIDE.md#5-agent-production-configuration)). The development Compose topologies select the development profile explicitly.

If `tenantId` is absent from the agent registration payload, the controller returns `400 Bad Request`.

### Transfer Job Tenant Field

Every transfer job must declare a `tenantId` at creation time. Polling filters jobs to the authenticated agent's tenant, update paths reject mismatches, and replicated commands enforce reference and ownership invariants. Broader tenant hierarchy, quota, usage, and inherited-policy management remains incomplete.

### Enforcement Points

| Operation | Enforcement |
|-----------|-------------|
| Agent registration | `tenantId` required — `400` if absent |
| Transfer creation | `tenantId` required — `400` if absent |
| Job polling (`GET /api/v1/agents/:agentId/jobs`) | Jobs filtered to agent's tenant only |
| Job status update | Cross-tenant update blocked — `403` |
| Heartbeat | Cross-tenant `tenantId` in payload blocked — `403` |

## Documentation Boundaries

When reading older Quorus material, keep these distinctions in mind:

- **Implemented now:** controller-first API, Raft-backed state, transfer execution, workflows, route CRUD, tenant-agent isolation
- **Modeled but not fully wired for autonomous runtime execution:** automatic route trigger evaluation, automatic agent selection and assignment
- **Not supported by current adapter code:** adapter-level resume, broad OAuth2 claims, workflow notification/cleanup/SLA YAML sections

## Recommended Next Documents

- `docs/QUORUS_ARCHITECTURE_SPECIFICATION.md` — canonical architecture and production requirements
- `docs/QUORUS_REST_API_SPECIFICATION.md` — complete required REST control and operations contract
- `docs/QUORUS_ARCHITECTURE_QUICKSTART.md`
- `quorus-controller/src/main/resources/openapi/quorus-controller-v1.yaml` — the OpenAPI contract for the current API, also served at `GET /api/v1/openapi.yaml`
- `docs/QUORUS_WORKFLOWS_README.md`
- `docs/QUORUS_YAML_SYNTAX_GUIDE.md`
- `docker/README.md` — the Docker guide: topologies, startup and verification
