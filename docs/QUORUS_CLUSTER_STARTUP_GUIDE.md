# Quorus Cluster Startup Guide

**Version:** 2.1  
**Date:** 2026-03-17

This guide documents the current repository-local path for starting a controller cluster and observability stack with the compose files that exist today.

## Prerequisites

- JDK 25
- Maven 3.9+
- Docker Desktop
- PowerShell 7 on Windows

## Current Compose Files

The repository currently ships these relevant compose files in `docker/compose`:

- `docker-compose-controller-first.yml`
- `docker-compose-single-controller.yml`
- `docker-compose-cluster.yml`
- `docker-compose-5node.yml`
- `docker-compose-observability.yml`
- `docker-compose-observability-cluster.yml`
- `docker-compose-loki.yml`
- `docker-compose-full-network.yml`
- `docker-compose-network-test.yml`
- `docker-compose-protocol-servers.yml`

## Build the Project

Build with JDK 25 before starting containers.

```powershell
mvn clean package 2>&1 | Tee-Object -FilePath temp\cluster-build.txt
```

## Start a Single Controller

```powershell
docker compose -f docker/compose/docker-compose-single-controller.yml up -d
```

## Start a Multi-Node Controller Cluster

```powershell
docker compose -f docker/compose/docker-compose-controller-first.yml up -d
```

Alternative cluster definitions are available in `docker/compose` when you need a different topology.

## Start Observability

For the current observability stack:

```powershell
docker compose -f docker/compose/docker-compose-observability.yml up -d
```

For cluster-focused observability:

```powershell
docker compose -f docker/compose/docker-compose-observability-cluster.yml up -d
```

## Verify the Controller API

Once controllers are up, verify:

```powershell
curl http://localhost:8080/health/live
curl http://localhost:8080/health/ready
curl http://localhost:8080/health
curl http://localhost:8080/raft/status
curl http://localhost:8080/api/v1/info
curl http://localhost:8080/metrics
```

Adjust the port if the compose file maps controller HTTP to a different host port.

## Notes About Routes

Current controller startup brings up:

- Raft transport
- Raft storage
- Raft node
- gRPC server
- embedded HTTP API
- graceful shutdown coordinator

It does not currently wire an autonomous route trigger evaluator in the startup path. Route CRUD and route replication are live; route-triggered autonomous execution should not be assumed from cluster startup alone.

## Agent Tenant Configuration

Each agent must be assigned to a tenant before it can receive jobs. Set the following environment variable in the agent container or process:

```bash
AGENT_TENANT_ID=<your-tenant-id>
```

Alternatively, set the property in `quorus-agent.properties`:

```properties
quorus.agent.tenant.id=<your-tenant-id>
```

An agent that registers without a `tenantId` is rejected by the controller with `400 Bad Request`. Transfer jobs without a `tenantId` are equally rejected. See `docs/QUORUS_USER_GUIDE.md` for the full tenant isolation model.

## Current Source of Truth

- `quorus-controller/src/main/java/dev/mars/quorus/controller/QuorusControllerVerticle.java`
- `quorus-controller/src/main/java/dev/mars/quorus/controller/config/AppConfig.java`
- `docker/compose/`