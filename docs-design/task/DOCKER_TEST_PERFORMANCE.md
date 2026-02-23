# Docker Test Performance Enhancements

**Status:** Implemented  
**Created:** 2026-02-23  
**Module:** quorus-controller (test infrastructure)

## Problem

Docker cluster integration tests take 2–5 minutes per test class on local Docker Desktop due to six stacked layers of unnecessary delay.

## Completed

| # | Change | Files |
|---|--------|-------|
| C1 | Replaced `@Tag("flaky")` with `@Tag("docker")` on 4 Docker-based tests and `@Tag("slow")` on 3 timing-sensitive in-memory tests; updated Javadocs with accurate descriptions | `DockerRaftClusterTest`, `AdvancedNetworkTest`, `NetworkPartitionTest`, `ConfigurableRaftClusterTest`, `RaftChaosTest`, `MetadataPersistenceTest`, `RaftLogClusterIntegrationTest` |
| C2 | Added `<excludedGroups>docker,slow</excludedGroups>` to quorus-controller surefire config so heavyweight tests are skipped by default | `quorus-controller/pom.xml` |
| P1 | Skip Docker image build when `quorus-controller:test` already exists locally — uses `docker image inspect` before `docker compose build` | `SharedDockerCluster.java` |
| P2 | Removed entrypoint `nc -z` peer-wait loop (30–60s per node); Raft handles reconnection natively | `docker-entrypoint.sh` |
| P3 | Removed redundant `@BeforeEach` health waits (2–3 min timeouts) — Testcontainers already verified `/health`; network-restore tests kept with 15s cap | `DockerRaftClusterTest`, `AdvancedNetworkTest`, `NetworkPartitionTest`, `ConfigurableRaftClusterTest` |
| P4 | Reduced compose `start_period` from 30s → 10s and Dockerfile from 60s → 15s; health poll interval 10s → 5s | `docker-compose-3node-prebuilt.yml`, `docker-compose-5node-prebuilt.yml`, `Dockerfile` |
| P5 | Simplified Testcontainers wait strategy to `Wait.forHttp("/health")` only; removed redundant `waitingFor(logMessage)` checks; startup timeout 3min → 90s | `SharedDockerCluster.java` |
| P6 | Lowered Raft election timeout from 3000ms → 1500ms and heartbeat from 500ms → 300ms in test compose files | `docker-compose-3node-prebuilt.yml`, `docker-compose-5node-prebuilt.yml` |

## Measurements

Baseline and post-fix timings to be recorded here once changes are applied.

| Scenario | Before | After |
|----------|--------|-------|
| 3-node cluster startup (image cached) | — | — |
| 5-node cluster startup (image cached) | — | — |
| `DockerRaftClusterTest` full class | — | — |
| `AdvancedNetworkTest` full class | — | — |

## How to run

```bash
# Default build — skips docker and slow tests
mvn test -pl quorus-controller

# Docker tests only
mvn test -pl quorus-controller -Dgroups=docker

# Slow (timing-sensitive) tests only
mvn test -pl quorus-controller -Dgroups=slow

# Everything
mvn test -pl quorus-controller -Dgroups=docker,slow
```
