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
| P7 | Enabled JUnit 5 parallel class execution; `AdvancedNetworkTest` marked `@Isolated` (mutates cluster network); other 3 classes run concurrently | `junit-platform.properties`, `AdvancedNetworkTest.java` |

## Measurements

Recorded 2026-02-23 on Docker Desktop 29.2.1 / Windows, `quorus-controller:test` image cached.

### Container Startup (image cached)

| Cluster | `compose up` → containers started | Health ready | Total startup |
|---------|----------------------------------|--------------|---------------|
| 3-node  | ~1s                              | ~6s          | **~7s**       |
| 5-node  | ~2s                              | ~5s          | **~7s**       |

### Test Class Timings (individual Maven invocations)

| Test Class | Tests | Result | Surefire Time | Maven Wall Time |
|------------|-------|--------|---------------|-----------------|
| `DockerRaftClusterTest` (3-node) | 4 | 4 pass | 19.4s | 25s |
| `AdvancedNetworkTest` (5-node) | 4 | 3 pass, 1 skip | 76.7s | 82s |
| `NetworkPartitionTest` (5-node) | 3 | 2 pass, 1 skip | 24.5s | 31s |
| `ConfigurableRaftClusterTest` (mixed) | 8 | 8 pass | 46.9s | 56s |
| **Sum (individual runs)** | **19** | **17 pass, 2 skip** | 167.5s | **194s** |

### Combined Run (single Maven invocation — clusters shared)

`SharedDockerCluster` singletons share clusters across test classes when run in the
same JVM. Surefire defaults to `forkCount=1, reuseForks=true`, so a single
`mvn test` invocation shares the JVM across all 4 classes.

| Test Class | Tests | Surefire Time | Cluster startup |
|------------|-------|---------------|-----------------|
| `AdvancedNetworkTest` (5-node) | 4 (1 skip) | 80.2s | starts 5-node (~10s) |
| `ConfigurableRaftClusterTest` (mixed) | 8 | 33.7s | starts 3-node (~6s), reuses 5-node |
| `DockerRaftClusterTest` (3-node) | 4 | 8.2s | reuses 3-node |
| `NetworkPartitionTest` (5-node) | 3 (1 skip) | 11.2s | reuses 5-node |
| **Total** | **19** | **133.3s** | **2 startups** (vs 5 individual) |

**Maven wall time (sequential): 2 min 21s** (vs 3 min 14s individual = **27% faster**)

Only 1 Ryuk container and 2 `compose up` calls for the entire run.

### Parallel Run (P7 — classes + methods run concurrently, AdvancedNetworkTest isolated)

`junit-platform.properties` enables parallel but defaults to `same_thread`.
Docker test classes opt in via `@Execution(ExecutionMode.CONCURRENT)`:
- `DockerRaftClusterTest`, `ConfigurableRaftClusterTest`, `NetworkPartitionTest` — concurrent
- `AdvancedNetworkTest` — `@Isolated` (mutates cluster network)

`@Execution(CONCURRENT)` also makes methods within each class run concurrently
(JUnit 5 PER_METHOD lifecycle creates a fresh instance per test — no shared mutable state).

| Phase | Classes | Surefire Time | Wall contribution |
|-------|---------|---------------|-------------------|
| Isolated | `AdvancedNetworkTest` | ~89s | ~89s (runs alone) |
| Concurrent | `ConfigurableRaftClusterTest` | ~12s (8 methods parallel) | ~14s (bottleneck) |
| Concurrent | `DockerRaftClusterTest` | ~13s | overlaps |
| Concurrent | `NetworkPartitionTest` | ~14s | overlaps |

**Maven wall time (parallel): ~2 min 10s**

Note: `ConfigurableRaftClusterTest` drops from 34s → 12s because its 8 parameterised tests
now run concurrently. Variance in wall time is dominated by `AdvancedNetworkTest` (76–89s)
due to real Docker network partition/restore operations.

### Before vs After Summary

| Scenario | Before (estimated) | After (measured) | Improvement |
|----------|-------------------|------------------|-------------|
| 3-node cluster startup (image cached) | 60–90s | **7s** | ~10× faster |
| 5-node cluster startup (image cached) | 90–150s | **7s** | ~15× faster |
| `DockerRaftClusterTest` full class | 2–5 min | **25s** | ~6× faster |
| `AdvancedNetworkTest` full class | 3–5 min | **82s** | ~3× faster |
| All 19 docker tests (sequential) | 5–10 min | **2 min 21s** | ~3× faster |
| All 19 docker tests (parallel) | 5–10 min | **~2 min 10s** | ~3× faster |

## How to run

```bash
# Default build — skips docker and slow tests
mvn test -pl quorus-controller

# Docker tests only
mvn test -pl quorus-controller -Dtest.excludedGroups= -Dgroups=docker

# Slow (timing-sensitive) tests only
mvn test -pl quorus-controller -Dtest.excludedGroups= -Dgroups=slow

# Everything
mvn test -pl quorus-controller -Dtest.excludedGroups=
```
