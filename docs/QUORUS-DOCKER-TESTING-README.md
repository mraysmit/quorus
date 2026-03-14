# Quorus Docker Testing README

**Version:** 2.0  
**Date:** 2026-03-14

This document summarizes the Docker assets that exist in the repository today and the testing scenarios they support.

## What Exists Today

Current Docker and compose assets in this repository support:

- controller cluster startup
- single-controller startup
- observability stacks
- full network test environments
- protocol server test environments
- network partition and cluster test scenarios

Relevant compose files are in `docker/compose`.

## Current Compose Inventory

- `docker-compose.yml`
- `docker-compose-single-controller.yml`
- `docker-compose-controller-first.yml`
- `docker-compose-cluster.yml`
- `docker-compose-5node.yml`
- `docker-compose-full-network.yml`
- `docker-compose-network-test.yml`
- `docker-compose-protocol-servers.yml`
- `docker-compose-loki.yml`
- `docker-compose-observability.yml`
- `docker-compose-observability-cluster.yml`
- `docker-compose-elk.yml`
- `docker-compose-fluentd.yml`

## Current Scripts

Current helper scripts in `docker/scripts`:

- `start-full-network.ps1`
- `test-transfers.ps1`
- `setup-logging.ps1`
- `demo-logging.ps1`
- `log-extraction-demo.ps1`
- `simple-log-demo.ps1`

## Current Testing Guidance

### Controller and Cluster Validation

Use the controller compose files together with the controller API endpoints:

- `/health/live`
- `/health/ready`
- `/health`
- `/raft/status`
- `/api/v1/info`
- `/metrics`

### Protocol Testing

Use `docker-compose-protocol-servers.yml` and the `quorus-core` integration tests for FTP, FTPS, SFTP, SMB, and related scenarios.

### Full Network Scenarios

Use `docker-compose-full-network.yml` when you need a larger end-to-end environment.

## Important Corrections from Older Docs

- The repository is on **Java 25**.
- The active controller transport is gRPC/Raft as wired by `GrpcRaftTransport`, not an `HttpRaftTransport` class.
- Older references to broad trigger-mechanism Docker tests should not be read as proof that route-trigger execution is fully wired in the controller runtime. Route API and route state replication are implemented; autonomous trigger evaluation is a separate concern.
- Adapter-level resume capability should not be advertised as implemented.

## Recommended Validation Pattern

1. Build the project on JDK 25.
2. Start the compose environment you need.
3. Verify controller health and Raft state first.
4. Run the matching Maven tests or PowerShell helper scripts.
5. Inspect logs and metrics through the observability stack if enabled.

## Source of Truth

- `docker/compose/`
- `docker/scripts/`
- `quorus-controller/src/main/java/dev/mars/quorus/controller/http/HttpApiServer.java`
- `quorus-controller/src/main/java/dev/mars/quorus/controller/QuorusControllerVerticle.java`