# Quorus User Guide

**Version:** 2.0  
**Date:** 2026-03-14  
**Scope:** Current implementation guide

## Introduction

Quorus is a file transfer platform that can run in two modes:

- **Direct mode** using `quorus-core` and optionally `quorus-workflow`
- **Distributed mode** using `quorus-controller` and `quorus-agent`

The direct path is the most complete end-user path today for executing transfer workloads. The distributed path provides controller-managed state, agent registration, assignments, and route management on top of Raft-replicated controller state.

## What Is Implemented Today

### Transfer Engine

`SimpleTransferEngine` is the live transfer execution component. It supports:

- transfer submission through Vert.x futures
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

The workflow engine currently executes transfer groups after validation and dependency sorting. YAML `condition` values are parsed and resolved, but the current workflow engine does not expose a separate condition evaluation subsystem in its execution path.

### Controller API

The embedded controller HTTP API currently provides:

- health and readiness endpoints
- metrics and status endpoints
- agent registration and heartbeat endpoints
- transfer CRUD
- job status updates
- assignment CRUD and lifecycle operations
- route CRUD and suspend/resume operations

### Routes

Routes are implemented today as:

- a replicated route model in `quorus-core`
- route commands and codecs in `quorus-controller`
- route storage in `QuorusStateStore`
- route CRUD and lifecycle endpoints in the HTTP API

What is **not** currently wired by controller startup is an always-on background trigger evaluator that watches routes and dispatches transfers automatically. In other words, route persistence and route APIs are live, but automatic route-triggered execution should not be documented as a completed runtime feature.

## Getting Started

### Java Baseline

This repository builds with Java 25. The root Maven build sets source and target to 25.

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
- transfer assignment distribution
- route CRUD through the controller API

## Current Protocol Guidance

### HTTP and HTTPS

Current implementation supports:

- direct HTTP download and upload paths
- custom request options carried through transfer definitions
- reactive execution through Vert.x Web Client

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
- blocking transfer execution offloaded away from the Vert.x event loop

Do **not** assume adapter-level resume support. The current adapter reports `supportsResume() == false`.

### SMB

Current implementation supports SMB and CIFS registration in the protocol factory.

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

## Documentation Boundaries

When reading older Quorus material, keep these distinctions in mind:

- **Implemented now:** controller-first API, Raft-backed state, transfer execution, workflows, route CRUD
- **Modeled but not fully wired for autonomous runtime execution:** automatic route trigger evaluation
- **Not supported by current adapter code:** adapter-level resume, broad OAuth2 claims, workflow notification/cleanup/SLA YAML sections

## Recommended Next Documents

- `docs/QUORUS_ARCHITECTURE_QUICKSTART.md`
- `docs/QUORUS_API_REFERENCE.md`
- `docs/QUORUS_WORKFLOWS_README.md`
- `docs/QUORUS_YAML_SYNTAX_GUIDE.md`
- `docs/QUORUS_CLUSTER_STARTUP_GUIDE.md`