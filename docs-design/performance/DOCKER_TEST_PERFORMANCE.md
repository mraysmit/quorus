# Docker Test Performance Enhancements

**Status:** Implemented; P1 superseded and P2 moot (see below)  
**Created:** 2026-02-23  
**Updated:** 2026-10-03  
**Module:** quorus-controller (test infrastructure)

Test lanes and the full list of `docker`-tagged classes are in the
[Testing Guide](../testing/QUORUS_TESTING_README.md#4-tests-that-need-docker).

## Problem

Docker cluster integration tests took 2–5 minutes per test class on local Docker Desktop due to six stacked layers of unnecessary delay.

## Changes

| # | Change | Files | Current state |
|---|--------|-------|---------------|
| C1 | Replaced `@Tag("flaky")` with `@Tag("docker")` on 4 Docker-based tests and `@Tag("slow")` on 3 timing-sensitive in-memory tests; updated Javadocs with accurate descriptions | `DockerRaftClusterTest`, `AdvancedNetworkTest`, `NetworkPartitionTest`, `ConfigurableRaftClusterTest`, `RaftChaosTest`, `MetadataPersistenceTest`, `RaftLogClusterIntegrationTest` | In force. `ContainerRecreationDurabilityTest` and `ContainerImageBaselineTest` were added later with `@Tag("docker")`; no `flaky` tag remains |
| C2 | Added `<excludedGroups>docker,slow</excludedGroups>` to quorus-controller surefire config so heavyweight tests are skipped by default | `quorus-controller/pom.xml` | In force, as `<excludedGroups>${test.excludedGroups}</excludedGroups>` with `test.excludedGroups=docker,slow` |
| P1 | Skipped the Docker image build when `quorus-controller:test` already existed locally | `SharedDockerCluster.java` | **Superseded.** `SharedDockerCluster` now packages the host-built jar into `quorus-controller:test` once per test JVM and never reuses an image from an earlier run: packaging takes seconds, and a cached image could be stale. It fails fast if `target/quorus-controller-1.0-SNAPSHOT.jar` is missing |
| P2 | Removed the entrypoint `nc -z` peer-wait loop (30–60s per node); Raft handles reconnection natively | `docker-entrypoint.sh` | **Moot.** The controller image has no `ENTRYPOINT`; it runs `java $JAVA_OPTS -jar app.jar`, so `quorus-controller/docker-entrypoint.sh` is never run (register item `ENG-20`) |
| P3 | Removed redundant `@BeforeEach` health waits (2–3 min timeouts) — Testcontainers already verified `/health`; network-restore tests kept with 15s cap | `DockerRaftClusterTest`, `AdvancedNetworkTest`, `NetworkPartitionTest`, `ConfigurableRaftClusterTest` | As recorded |
| P4 | Reduced compose `start_period` from 30s → 10s and Dockerfile from 60s → 15s; health poll interval 10s → 5s | `docker-compose-3node-prebuilt.yml`, `docker-compose-5node-prebuilt.yml`, `Dockerfile` | In force: the test compose files use `start_period: 10s` and `interval: 5s`; the Dockerfile `HEALTHCHECK` uses `--start-period=15s --interval=10s` |
| P5 | Simplified Testcontainers wait strategy to `Wait.forHttp("/health")` only; removed redundant `waitingFor(logMessage)` checks; startup timeout 3min → 90s | `SharedDockerCluster.java` | In force |
| P6 | Lowered Raft election timeout from 3000ms → 1500ms and heartbeat from 500ms → 300ms in test compose files | `docker-compose-3node-prebuilt.yml`, `docker-compose-5node-prebuilt.yml` | In force, as `QUORUS_RAFT_ELECTION_TIMEOUT_MS=1500` and `QUORUS_RAFT_HEARTBEAT_INTERVAL_MS=300` (also in `docker-compose-3node-durable.yml`) |
| P7 | Enabled JUnit 5 parallel class execution; `AdvancedNetworkTest` marked `@Isolated` (mutates cluster network); other 3 classes run concurrently | `junit-platform.properties`, `AdvancedNetworkTest.java` | In force |

## Measurements

Recorded 2026-02-23 on Docker Desktop 29.2.1 / Windows, with the `quorus-controller:test` image
already cached (the P1 behaviour of that time). The host CPU and RAM, and the memory given to the
Docker Desktop VM, were **not recorded**, so these figures cannot be compared with a run on other
hardware.

The figures predate the superseding of P1. A run today also includes one image packaging step per test
JVM, which has not been measured. They also predate `ContainerRecreationDurabilityTest` and
`ContainerImageBaselineTest`, which are not measured (see [Not yet measured](#not-yet-measured)).

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

### Not yet measured

| Test Class | Tests | Why it differs |
|------------|-------|----------------|
| `ContainerRecreationDurabilityTest` | 4 | Does not use the shared clusters. It starts its own 3-node cluster from `docker-compose-3node-durable.yml` through `DockerComposeCluster` (plain `docker compose`), destroys and recreates the containers several times, and its negative-control test starts a second compose project. It runs sequentially (no `@Execution(CONCURRENT)`) |
| `ContainerImageBaselineTest` | 2 | Builds fresh `quorus-controller:rt01` and `quorus-agent:rt01` images each run (timeout 10 minutes per test) |

The next measurement must record, with the figures: the host CPU model and core count, host RAM, the
memory and CPUs assigned to the Docker VM (Docker Desktop), the Docker engine version and the commit
measured.

## How to run

The `docker` tests package the host-built controller jar (and `ContainerImageBaselineTest` also the agent
jar), so build both first and do not `clean` in the test command:

```bash
# Build the controller and agent jars on the host
docker/build-runtime.sh                     # or: pwsh docker/build-runtime.ps1

# Default build — skips docker and slow tests
mvn test -pl quorus-controller -am

# Docker tests only
mvn test -pl quorus-controller -am '-Dtest.excludedGroups=' -Dgroups=docker

# Slow (timing-sensitive) tests only — no Docker or jar build needed
mvn test -pl quorus-controller -am '-Dtest.excludedGroups=' -Dgroups=slow

# Everything in quorus-controller
mvn test -pl quorus-controller -am '-Dtest.excludedGroups='

# One class
mvn test -pl quorus-controller -am '-Dtest.excludedGroups=' -Dtest=ContainerRecreationDurabilityTest -Dsurefire.failIfNoSpecifiedTests=false
```

`-am` builds `quorus-core`, `quorus-workflow` and `quorus-tenant` in the same reactor (and runs their
tests too in the default and "Everything" commands); without it, Maven takes them from the local
repository. `-Dgroups=docker` without `'-Dtest.excludedGroups='` selects nothing, because the
exclusion still applies.
