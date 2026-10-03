# Quorus Testing Guide

This is the hub for the Quorus testing documents. It describes the test modules, the JUnit tags, the
default and opt-in Maven lanes, the tests that need Docker, and the consolidated test log. The other
testing documents cover one area each:

| Document | Covers |
|---|---|
| [QUORUS_LOG_STYLE.md](QUORUS_LOG_STYLE.md) | The simulator test logging extension used by the `quorus-core` simulator tests |
| [QUORUS_NEGATIVE_TESTING_STRATEGY.md](QUORUS_NEGATIVE_TESTING_STRATEGY.md) | `@ExpectsError` and the `negative` tag |
| [QUORUS_PROTOCOL_SERVERS_TESTING.md](QUORUS_PROTOCOL_SERVERS_TESTING.md) | FTP, FTPS, SFTP and SMB test servers |
| [QUORUS_RAFT_CLUSTER_TESTING.md](QUORUS_RAFT_CLUSTER_TESTING.md) | The three-controller development cluster and its Raft checks |
| [DOCKER_TEST_PERFORMANCE.md](../performance/DOCKER_TEST_PERFORMANCE.md) | Timings of the `docker`-tagged controller tests |
| [QUORUS_PERFORMANCE_BENCHMARKS.md](../performance/QUORUS_PERFORMANCE_BENCHMARKS.md) | The benchmark specification behind `quorus-benchmarks` |

New and changed tests follow the asynchronous test standard in
[QUORUS_CONCURRENCY_CONVENTIONS.md §6](../dev/QUORUS_CONCURRENCY_CONVENTIONS.md#6-asynchronous-test-standard).

---

## 1. Modules

The default reactor (root `pom.xml`) has six modules. `quorus-benchmarks` is built only with the
`benchmarks` profile.

| Module | In default build | Test style |
|---|---|---|
| `quorus-core` | Yes | §6 standard (many older tests still sleep or use Awaitility; do not copy them) |
| `quorus-workflow` | Yes | §6 standard |
| `quorus-tenant` | Yes | §6 standard (older tests as for core) |
| `quorus-controller` | Yes | Vert.x: `@ExtendWith(VertxExtension.class)` and `VertxTestContext`. The only module whose tests use `VertxExtension` |
| `quorus-agent` | Yes | §6 standard |
| `quorus-integration-examples` | Yes | §6 standard |
| `quorus-benchmarks` | No, `-Pbenchmarks` only | Plain JUnit 5 (no `VertxExtension`); the harness tests have `@Timeout(threadMode = SEPARATE_THREAD)`. Its main-code `RaftCommitBenchmark` drives the controller's Raft engine and so uses Vert.x |

`quorus-workflow`, `quorus-controller` and `quorus-agent` depend on the `quorus-core` test jar, which
publishes `ExpectsError` and `ExpectsErrorExtension` (section 6).

---

## 2. Tags and naming

| Tag | Classes | Default behaviour |
|---|---|---|
| `docker` | `quorus-core`: `ProtocolServersLifecycleIntegrationTest` (a compose stack on fixed host ports; excluded by `test.excludedGroups=docker` in the core pom). `quorus-controller`: `DockerRaftClusterTest`, `ConfigurableRaftClusterTest`, `AdvancedNetworkTest`, `NetworkPartitionTest`, `ContainerRecreationDurabilityTest`, `ContainerImageBaselineTest` | Excluded: the controller's surefire uses `<excludedGroups>${test.excludedGroups}</excludedGroups>` with `test.excludedGroups=docker,slow` |
| `slow` | `quorus-controller`: `MetadataPersistenceTest`, `RaftChaosTest`, `RaftLogClusterIntegrationTest` (timing-sensitive in-process Raft clusters) | Excluded, as above |
| `negative` | `quorus-core`: `ProtocolErrorHandlingTestBase` and its `Ftp`, `Sftp` and `Smb` `*ErrorHandlingTest` subclasses | **Run by default**: `quorus-core` excludes only `docker` |

There is no `flaky` tag. `quorus-core` and `quorus-controller` exclude groups, both through the `test.excludedGroups` property, so `'-Dtest.excludedGroups='` runs the excluded tests of both. The `quorus-core` upload tests that start Testcontainers fixtures on free ports are untagged and still run by default.

| Naming | Meaning |
|---|---|
| `*Test` | Run by Surefire in the default build, unless tagged as above |
| `*IntegrationTest` | Also run by default. Some need Docker (section 4); most do not (for example the controller's HTTP integration tests start in-process servers) |
| `*IT` | **Not used.** Surefire's default includes do not match `*IT` and no module configures Failsafe, so such a class would compile and never run. `TestNamingConventionTest` (in `quorus-core`) fails the build if any class that declares a test has a name Surefire does not select. Keep a heavy test out of the default build with a tag, not with its name (decision DR-Q4 in the [register](../task/QUORUS_OUTSTANDING_WORK_REGISTER.md)) |

---

## 3. Maven lanes

Run from the repository root. In PowerShell, quote any argument that ends in `=` or contains a dot
before `=`, for example `'-Dtest.excludedGroups='`; the same quoting works in Bash.

### 3.1 Default lane

```bash
mvn verify
```

Runs every module's tests except the controller's `docker` and `slow` groups, writes each module's
JaCoCo report (the `report` goal is bound to the `test` phase) and, in `verify`, enforces the JaCoCo
gate of 60% line coverage per package (skipped in `quorus-integration-examples`). `mvn test` runs the
same tests without the gate. Section 4 lists the default-lane tests that start containers.

### 3.2 One module or one class

```bash
mvn test -pl quorus-core
mvn test -pl quorus-core -Dtest=FtpUploadIntegrationTest
```

With `-pl` and no `-am`, Maven takes the other Quorus modules from the local Maven repository, so run
`mvn install -DskipTests` after changing an upstream module, or add `-am` (which also runs the upstream
modules' tests; with `-Dtest=...` add `-Dsurefire.failIfNoSpecifiedTests=false`).

### 3.3 Negative tests only

```bash
mvn test -pl quorus-core -Pnegative-tests
```

The `negative-tests` profile sets `<groups>negative</groups>`. The `all-tests` profile replaces the Surefire
configuration with an empty one; since `quorus-core` excludes nothing, it runs the same tests as the
default lane. See [QUORUS_NEGATIVE_TESTING_STRATEGY.md](QUORUS_NEGATIVE_TESTING_STRATEGY.md).

### 3.4 Docker and slow lanes (`quorus-controller`)

The `docker` tests package the controller jar that the host built; nothing is compiled inside Docker.
They run in Maven's `test` phase, before `package`, so build the jars first and do not `clean` in the
same command:

```bash
# 1. Build the controller and agent jars on the host (clean package -DskipTests)
docker/build-runtime.sh          # or: pwsh docker/build-runtime.ps1

# 2a. Docker tests only
mvn test -pl quorus-controller -am '-Dtest.excludedGroups=' -Dgroups=docker

# 2b. Slow tests only (no Docker needed, so step 1 is not required)
mvn test -pl quorus-controller -am '-Dtest.excludedGroups=' -Dgroups=slow

# 2c. Everything in the default reactor, including docker and slow
mvn verify '-Dtest.excludedGroups='
```

`-Dgroups=docker` alone selects nothing, because `excludedGroups` still applies; empty
`test.excludedGroups` as well. With `-am`, the upstream modules run with the same `-Dgroups` filter and
so run no tests.

### 3.5 Benchmark module

```bash
mvn -B -Pbenchmarks -pl quorus-benchmarks -am install -DskipTests
mvn -Pbenchmarks -pl quorus-benchmarks test
```

`LatenciesTest` is a unit test. `RaftCommitBenchmarkTest` runs the B-09 harness at a tiny size (three
in-JVM engine nodes over gRPC with mutual TLS). `ControllerApiBenchmarkTest` runs the B-08 harness
against a real controller process started from the host-built controller jar. Running the benchmarks
themselves is described in [QUORUS_PERFORMANCE_BENCHMARKS.md](../performance/QUORUS_PERFORMANCE_BENCHMARKS.md).

### 3.6 Protocol server lifecycle check

```bash
mvn test -pl quorus-core -Dtest=ProtocolServersLifecycleIntegrationTest '-Dtest.excludedGroups='
```

The class is tagged `docker`, so it needs the exclusion cleared; it also runs in the Docker lane. It
starts and stops its own Docker Compose stack; see
[QUORUS_PROTOCOL_SERVERS_TESTING.md](QUORUS_PROTOCOL_SERVERS_TESTING.md).

---

## 4. Tests that need Docker

### 4.1 Default-lane Testcontainers tests (no `docker` tag)

Six test classes start containers through Testcontainers in a default build. None carries the
`docker` tag.

| Class | Module | Containers | Without Docker |
|---|---|---|---|
| `FtpUploadIntegrationTest` | core | FTP (vsftpd, `docker-compose-ftp-test.yml`) | Skipped (`assumeTrue(SharedTestContainers.isDockerAvailable())`) |
| `FtpsUploadIntegrationTest` | core | FTPS (vsftpd with TLS, `docker-compose-ftps-test.yml`) | Skipped |
| `SftpUploadIntegrationTest` | core | SFTP (`atmoz/sftp:alpine`, `docker-compose-sftp-abort-test.yml`) | Skipped |
| `AdapterProgressAndStopTest` | core | FTP and SFTP, as above | Skipped (per test) |
| `AgentTelemetryIntegrationTest` | agent | `otel/opentelemetry-collector-contrib:0.96.0` | Fails: `@Testcontainers` without `disabledWithoutDocker` |
| `InfrastructureWithTelemetryTest` | controller | `otel/opentelemetry-collector-contrib:0.96.0` | Fails, as above |

The four `quorus-core` classes share containers through `SharedTestContainers`, a lazy singleton:
each container starts on first use, is shared by every class in the test JVM, and is stopped by a JVM
shutdown hook. FTP and FTPS use host ports chosen per run and direct port mappings (not the
Testcontainers proxy), because vsftpd's passive-mode checks fail through the proxy. The two telemetry
tests use a class-level `static @Container`. The compose files and images are described in
[QUORUS_PROTOCOL_SERVERS_TESTING.md](QUORUS_PROTOCOL_SERVERS_TESTING.md).

### 4.2 `docker`-tagged controller tests

| Class | Cluster |
|---|---|
| `DockerRaftClusterTest` | Shared 3-node cluster (`docker-compose-3node-prebuilt.yml`) |
| `ConfigurableRaftClusterTest` | Shared 3-node and 5-node clusters |
| `NetworkPartitionTest` | Shared 5-node cluster (`docker-compose-5node-prebuilt.yml`) |
| `AdvancedNetworkTest` | Shared 5-node cluster; `@Isolated` because it changes the cluster network |
| `ContainerRecreationDurabilityTest` | Its own 3-node clusters from `docker-compose-3node-durable.yml` (section 4.3) |
| `ContainerImageBaselineTest` | Builds `quorus-controller:rt01` and `quorus-agent:rt01` and checks that each runs Amazon Corretto 27 and ships the host-built jar byte for byte (needs both jars) |

`SharedDockerCluster` builds the `quorus-controller:test` image once per test JVM, from
`docker-compose-build-image.yml` and `quorus-controller/Dockerfile`, which copies
`quorus-controller/target/quorus-controller-*.jar`. It fails fast if
`target/quorus-controller-1.0-SNAPSHOT.jar` is missing. A cached image from an earlier run is never
reused. The shared clusters are Testcontainers `ComposeContainer` instances that wait for `/health` on
every node; they use election timeout 1500 ms and heartbeat 300 ms. Timings are in
[DOCKER_TEST_PERFORMANCE.md](../performance/DOCKER_TEST_PERFORMANCE.md).

### 4.3 `ContainerRecreationDurabilityTest` and `docker-compose-3node-durable.yml`

The R1-1 container-recreation acceptance test. `ThreeControllerDurableRestartTest` restarts
in-process controllers; this test destroys and recreates the controller containers.

- **Fixture:** `quorus-controller/src/test/resources/docker-compose-3node-durable.yml`. Three
  controllers from `quorus-controller:test`, each with a named volume at `/app/data` and
  `QUORUS_RAFT_STORAGE_PATH=/app/data/raft`, `QUORUS_RAFT_STORAGE_FSYNC=true`, snapshots every 3
  entries (checked every 2000 ms), election timeout 1500 ms and heartbeat 300 ms. Security is disabled
  (plaintext development fixture).
- **Lifecycle:** driven by `DockerComposeCluster`, which calls `docker compose` directly, because
  Testcontainers always removes volumes on stop. Each run uses its own compose project name.
- **Tests:** a committed transfer survives `docker compose down` (volumes kept) and `up`; recovery
  after WAL compaction uses the durable snapshot; recreating one follower while two nodes keep quorum
  loses nothing; and a negative control removes the volumes and requires the transfer to be gone.
- **Boundary:** it proves durability across container destruction on the Docker engine it runs on.
  It does not prove R1-3 machine power-loss durability.

---

## 5. Test logging

### 5.1 Consolidated log file

Every module writes to one file per Maven invocation, at the repository root:

```
test-logs/quorus-test-2026-10-03_09-15-42.log
```

Each module's `src/test/resources/logback-test.xml` has a `FileAppender` with:

```xml
<file>../test-logs/quorus-test-${testRunTimestamp}.log</file>
<append>true</append>
<prudent>true</prudent>
```

- The parent `pom.xml` sets `maven.build.timestamp.format` to `yyyy-MM-dd_HH-mm-ss` and passes
  `${maven.build.timestamp}` to every Surefire fork as the system property `testRunTimestamp`, so all
  modules in one build share the file name. Maven's build timestamp is in UTC.
- Surefire runs each module's tests with the module directory as the working directory, so
  `../test-logs` is the repository root.
- `append` adds each module's output to the file; `prudent` adds file locking for JVMs that write at the
  same time.
- Running a test from an IDE does not set `testRunTimestamp`, so Logback names the file
  `quorus-test-testRunTimestamp_IS_UNDEFINED.log`.

The modules are `quorus-core`, `quorus-workflow`, `quorus-tenant`, `quorus-controller`, `quorus-agent`,
`quorus-integration-examples` and, when built, `quorus-benchmarks`. Each line carries the logger name.
The `quorus-core` file pattern adds `[%X{simulator}/%X{testMethod}]` from MDC; the controller and
benchmark patterns add `nodeId`, `raftRole/raftTerm`, `requestId` and `rpcType` when they are set.
All modules log `dev.mars.quorus` at DEBUG with the root at INFO.

`test-logs/` is in `.gitignore`, and `mvn clean` does not remove it (it is outside `target/`).

### 5.2 Filtering by module

```powershell
Select-String -Path test-logs/quorus-test-*.log -Pattern 'quorus\.controller'
```

```bash
grep 'quorus.controller' test-logs/quorus-test-*.log
```

### 5.3 Capturing the console

Console output stays per module. To keep it as well, pipe Maven through `Tee-Object` or `tee`, with
`2>&1` so that standard error is captured too:

```powershell
mvn test 2>&1 | Tee-Object -FilePath "target/test-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
```

```bash
mvn test 2>&1 | tee "target/test-$(date +'%Y%m%d-%H%M%S').log"
```

---

## 6. Expected errors: `@ExpectsError`

A test that deliberately drives production code into an error path, and so produces ERROR log lines,
is annotated with `@ExpectsError("reason")`. `ExpectsErrorExtension` logs a WARN banner before and
after the test, so the ERROR lines between them read as planned behaviour. Such tests run in the default
lane. They are used in `quorus-core`, `quorus-workflow` and `quorus-controller`. Details:
[QUORUS_NEGATIVE_TESTING_STRATEGY.md](QUORUS_NEGATIVE_TESTING_STRATEGY.md).

---

## 7. Principles

- **Real I/O for I/O code.** Protocol adapters and telemetry export are tested against real servers in
  containers, not mocks. Pure logic (dependency graphs, parsing, checksums, in-memory Raft log
  operations) uses plain JUnit tests.
- **Share expensive fixtures within a test JVM.** Use the existing singletons
  (`SharedTestContainers`, `SharedDockerCluster`) rather than starting containers per class.
  `ContainerRecreationDurabilityTest` is the exception, because it must own the container lifecycle.
- **Address containers correctly.** Tests on the host use the mapped host port; containers talk to each
  other by service name on their compose network, never `localhost`.
- **Assert on outcomes, not logs.** Assert exceptions, responses and state; logs are diagnostic.
- **Images package host-built jars.** No test or fixture compiles Java or runs Maven inside Docker.
