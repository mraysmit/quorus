<img src="quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Configuration Isolation Handover — 2026-09-03

**Version:** 1.1  
**Date:** 2026-09-04  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Status:** Superseded — see the post-handover status below  
**Scope:** Original configuration handover at `fab72a6`, with remediation updates through `038da9f` and the uncommitted external-library-only removal

## Post-handover status — 2026-09-04

**External-library-only follow-up:** the internal file WAL was already removed, but RocksDB and memory backends remained. Those implementations, their factory branches, `createInMemory()`, backend-specific tests and RocksDB JNI dependency are now removed. Storage-dependent controller, election and snapshot tests use the external adapter. Seven behavioral rejection cases retain red/green evidence; production code contains only `RaftLogStorageAdapter` as a `RaftStorage` implementation. Quorus retains its Vert.x interface and application-snapshot sidecar, neither of which implements a WAL.

**Current commit boundary:** `038da9f` (`fix(raft): persist snapshots and recover safely after compaction`) contains the R1 snapshot recovery remediation. The external-library-only removal described above is the subsequent uncommitted change. No commit was created as part of this handover update. The snapshot sidecar must remain while `raftlog-core` 1.2.0 has no snapshot API; removing it would reintroduce the snapshot-compaction recovery defect.

**Verification of the removal:** tests ran in the isolated `../quorus-core-tests-20260904` worktree to avoid editor-generated class-file interference. Changed controller sources, resources and POM matched the working tree by file hash.

| Check | Result |
|---|---|
| Behavioral red before production changes | Seven rejection cases failed as intended: six removed factory backend names and controller memory configuration |
| Focused regression after removal | 93 tests passed; no failures, errors or skips |
| Clean controller `verify` | 520 tests passed; no failures, errors or skips; JaCoCo gate passed; BUILD SUCCESS |
| Shaded JAR audit | External `dev/mars/raftlog/storage/FileRaftStorage.class` present; internal WAL implementations and RocksDB JNI absent |

The controller count changed from 530 to 520 because 17 obsolete implementation-specific tests were removed and seven rejection cases were added. Existing node, snapshot and controller tests were migrated to real library storage with per-test temporary directories and awaited shutdown; these migrations are regression coverage, not new TDD evidence. Commands, timestamps, log hashes and the artifact hash are retained under `externalLibraryOnly` in the [Raft evidence record](../docs-design/evidence/raft-log-tdd-evidence-2026-09-04.json). This removal slice did not rerun the full reactor or the configured Docker/slow groups excluded by default controller verification.

**Operational boundary:** controller configuration accepts only `raftlog`; removed backend names fail explicitly. No deployed storage was inspected, migrated or deleted. Preserve any legacy storage before recovery work; switching a property is not an on-disk migration. The remaining R1 production-filesystem/power-loss acceptance gates and R2–R6 remain open; green tests do not close those gates.

**Remediation checkpoint:** the approved review follow-up is tracked in the existing [enterprise implementation plan](../docs-design/task/QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md#remediation-checkpoint--2026-09-04). M0 durability and Phase 4 acceptance are reopened. Real WAL prefix deletion in `43cdd20` exposed the adapter's memory-only snapshots. R1 adds a durable snapshot sidecar, compaction dependency checks, serialized snapshot mutations and interrupted-install recovery; its behavioral red/green evidence is retained in the [Raft evidence record](../docs-design/evidence/raft-log-tdd-evidence-2026-09-04.json). Release remains blocked by the checkpoint's outstanding gates. `ffc3e64` records a full clean reactor against the 1.2.0 adoption patch, but that historical green result did not exercise snapshot-plus-compaction restart. No deployed storage has been changed or assessed by this remediation.

The working tree described here was committed as `b35fb25` (`refactor(config): isolate layered config; drop singletons and -D flags`). A follow-up on 2026-09-04 then addressed the blocking findings:

- Finding 1: `AppConfig.getString` and `AgentConfig.getString` derive the variable name from the key when no properties resource declares it, so hyphenated keys such as `quorus.raft.io.queue-size` are reachable from the environment again.
- Finding 2: `AgentConfig` applies the legacy unprefixed names first and the documented `QUORUS_AGENT_*` names second, so the documented name wins. The `QUORUS_VAULT_ADDR` and `QUORUS_VAULT_TOKEN` entries left the legacy map because they are the documented names and the generic pass already reaches them. `AgentConfig` also gained a package-private constructor that accepts an environment map for tests.
- Finding 3: `AppConfig.validate()` rejects a non-positive attempt lease, and `QuorusControllerVerticle` chains its asynchronous start steps with `compose`, so an exception in any step fails the deployment after stopping the components that already started.
- Finding 8: the Copilot instructions and the Raft WAL design snippet were updated.

Finding 5's dead `createRocksDbStorage` helper is resolved by deleting the entire RocksDB factory path. Its other cleanup items, findings 4, 6 and 7, and every item in section 7 remain open. The rest of this document is preserved as historical evidence from 2026-09-03; its uncommitted-state statements and recommended next steps describe that original handover, not the current checkout. Follow the enterprise implementation plan's remediation checkpoint for current next steps.

## Document purpose

This document hands over an uncommitted refactor of the Quorus configuration layer so that another engineer can review, finish, and commit it without re-deriving context. It records what the change does, which public APIs moved, how the change was verified, what a code review found, and what should happen next.

The change is not committed. Everything described here lives in the working tree only. Nothing is staged.

The normative behaviour of the system remains defined by [QUORUS_ARCHITECTURE_SPECIFICATION.md](QUORUS_ARCHITECTURE_SPECIFICATION.md) and [QUORUS_REST_API_SPECIFICATION.md](QUORUS_REST_API_SPECIFICATION.md). Where this handover describes intent that those documents do not yet capture, the specifications should be updated when the work is committed.

## Executive summary

The working tree replaces the `AppConfig` and `AgentConfig` singletons, and the JVM system-property configuration channel, with per-instance layered configuration objects that are assembled once at the application boundary and passed explicitly to the components that need them. `QuorusConfiguration` in `quorus-core` follows the same contract. The motivation is isolation: two controllers or two agents deployed into the same JVM, or two tests in the same surefire fork, can no longer contaminate each other through static state or `System.setProperty`.

State at handover:

- The refactor is functionally complete. All six modules compile, including test sources.
- Targeted unit tests for the touched classes pass in `quorus-core`, `quorus-controller`, and `quorus-agent`. The full suites and the Docker-backed integration tests were not run.
- A code review found **two behavioural regressions in environment-variable handling**, **one startup failure that hangs instead of failing**, and **one latent bug in an unused entrypoint script**. None block compilation or the tests that exist, but the first two will surprise operators. They are described in section 5 with suggested fixes.
- Two documentation files outside the diff still describe the removed behaviour.

Recommended path: fix findings 1 to 3 in section 5, add the three tests listed in section 6, run `mvn clean verify`, then commit. The cleanup items can follow in a second commit.

## 1. Repository state at handover

| Item | Value |
|---|---|
| Branch | `master` |
| HEAD | `fab72a6` — `feat(security): deliver governed service connections` (2026-09-03) |
| Modified tracked files | 56 |
| New untracked files | 2 |
| Staged | nothing |
| Diff size | 978 insertions, 911 deletions |
| JDK | OpenJDK 26.0.1 |
| Maven | 3.9.16 |
| Platform | Windows 11 |

The repository also contains a `.history/` directory produced by an editor extension. It is ignored by git and should be excluded from any grep across the tree, because it holds stale copies of the files touched here.

## 2. What the change does

### 2.1 The configuration contract

Every configuration class now builds its state in the constructor from four layers, lowest priority first. Later layers overwrite earlier ones.

| Priority | Layer | Controller | Agent | Core |
|---|---|---|---|---|
| 4 (lowest) | Packaged defaults | `quorus-controller.properties` | `quorus-agent.properties` | Built-in defaults, then `quorus.properties` |
| 3 | Profile resource, optional | `quorus-controller-<profile>.properties` | `quorus-agent-<profile>.properties` | `quorus-<profile>.properties` |
| 2 | Environment variables | all `QUORUS_*` variables | derived from loaded keys, plus a legacy-name map | derived from loaded keys |
| 1 (highest) | Explicit `Properties` passed to the constructor | yes | yes | yes |

Rules that apply to all three:

- The profile `"default"` loads no profile resource. Any other profile name loads the profile resource if present and is silent if it is absent.
- A missing packaged resource logs a warning and falls back to accessor defaults. An `IOException` while reading a resource throws `IllegalStateException`.
- JVM system properties are never consulted. A test in each module asserts this.
- Environment variable names are the key upper-cased with `.` and `-` replaced by `_`. For example `quorus.raft.election-timeout-ms` becomes `QUORUS_RAFT_ELECTION_TIMEOUT_MS`.
- In `AppConfig` and `AgentConfig`, a blank value is treated as not configured and the accessor default applies. `QuorusConfiguration` returns blank values as-is. This divergence is deliberate in the existing tests but is worth unifying.

The agent additionally honours the legacy unprefixed names used by the Docker entrypoint and compose files: `AGENT_ID`, `AGENT_TENANT_ID`, `CONTROLLER_URL`, `AGENT_REGION`, `AGENT_DATACENTER`, `AGENT_PORT`, `MAX_CONCURRENT_TRANSFERS`, `HEARTBEAT_INTERVAL`, `HTTP_CONNECTION_TIMEOUT_MS`, `HTTP_IDLE_TIMEOUT_MS`, `AGENT_VERSION`, `SUPPORTED_PROTOCOLS`, `QUORUS_VAULT_ADDR`, and `QUORUS_VAULT_TOKEN`. See finding 2 for the precedence problem this introduces.

The two loaders differ in how they map the environment. `AppConfig` iterates every environment variable that starts with `QUORUS_` and maps it back to a property key. `AgentConfig` and `QuorusConfiguration` iterate the keys already loaded and derive the variable name from each key. Finding 1 and the fragile half of finding 2 both come from this difference.

### 2.2 Public API changes

Callers outside this repository, and any local branches, will need these updates.

**quorus-core**

| Before | After |
|---|---|
| `new QuorusConfiguration()` and `new QuorusConfiguration(Properties)` | `new QuorusConfiguration(String profile, Properties overrides)` only; both arguments non-null |
| `QuorusConfiguration.setProperty(key, value)` | removed; pass overrides to the constructor |
| `MountedFileSystemSecurity.configured(protocol)` read a system property | removed; attestation is a constructor flag on the protocol |
| `NfsTransferProtocol.MOUNT_ROOT_PROPERTY` and the `quorus.nfs.mount.root` system property | removed; mount root is injected via `ProtocolFactory` |
| `new NfsTransferProtocol()` / `(String mountRoot)` | unchanged, plus `(boolean verified)` and `(String mountRoot, boolean verified)`; `isMountSecurityVerified()` added |
| `new SmbTransferProtocol()` | unchanged, plus `(boolean verified)`; `isMountSecurityVerified()` added |
| `new ProtocolFactory(Vertx)` | unchanged, plus `(Vertx, String nfsMountRoot)` and `(Vertx, String nfsMountRoot, boolean smbVerified, boolean nfsVerified)` |
| `new SimpleTransferEngine(vertx, max, retries, delay)` | unchanged, plus a 5-argument form adding `nfsMountRoot` and a 7-argument form adding both attestation flags |

**quorus-controller**

| Before | After |
|---|---|
| `AppConfig.get()` singleton | `new AppConfig(String profile, Properties overrides)`; a package-private constructor also accepts a `Map<String,String>` environment for tests; `getProfile()` added |
| `new QuorusControllerVerticle()` | `new QuorusControllerVerticle(AppConfig)` |
| `TelemetryConfig.configure(options)` plus static `getPrometheusPort()` / `getOtlpEndpoint()` | `TelemetryConfig.configure(options, AppConfig)`; static getters removed, read the config instead |
| `new HttpApiServer(...)` in six public forms | every form takes an `AppConfig` argument after `prometheusPort`; passing `-1` for the port uses the configured Prometheus port |
| `new MetricsHandler(vertx)` | removed; only `(vertx, port)` remains |
| `new JobAssignmentHandler(raftNode, stateStore)` | `(raftNode, stateStore, long attemptLeaseDurationMs)`; throws `IllegalArgumentException` unless positive |
| `new JobAssignmentService(vertx, raftNode, selection)` | `(vertx, raftNode, selection, AppConfig)` |
| `RaftStorageFactory.create(vertx, executor)` and the `String` / `StorageType` overloads | removed; only the asynchronous `create(vertx, storageType, path, fsync)` remains |
| `new RaftLogStorageAdapter(vertx)` | removed; pass a `RaftStorageConfig` |

**quorus-agent**

| Before | After |
|---|---|
| `AgentConfig.get()` singleton | `new AgentConfig(String profile, Properties overrides)`; `getProfile()` added |
| `AgentConfiguration.fromEnvironment()` | `AgentConfiguration.from(AgentConfig)`; it calls `validate()` on the source first |
| `AgentTelemetryConfig.configure(options, agentId)` plus static `getPrometheusPort()` | `configure(options, AgentConfiguration)`; returns the options untouched when telemetry is disabled; static getter removed |
| `AgentConfig.getAgentId()` / `getTenantId()` read `AGENT_ID` / `AGENT_TENANT_ID` directly | they read only the loaded properties; the legacy names are mapped during construction |

`AgentConfig` gained typed accessors for HTTP timeouts, security profile, allow-insecure, TLS enablement and paths, upload and download roots, NFS mount root, NFS and SMB attestations, agent pool, network zone, and Vault address and token. `AgentConfiguration` carries the same values as immutable fields, with matching `Builder` methods and `build()` validation for the numeric ones.

### 2.3 Wiring changes

- `QuorusControllerApplication.main` constructs `new AppConfig("default", new Properties())`, validates it, configures telemetry from it, and deploys the verticle with it.
- `QuorusControllerVerticle` stores the config it is given and no longer calls a singleton anywhere, including in the shutdown coordinator.
- `HttpApiServer` reads body limit, transfer freshness and stall windows, Prometheus port, and attempt lease duration from its config instance.
- `QuorusAgent.main` constructs `new AgentConfig("default", new Properties())`, converts it with `AgentConfiguration.from`, and reads polling delays and the foreign-assignment threshold from the typed configuration.
- `TransferExecutionService` passes the NFS mount root and both mount attestations into `SimpleTransferEngine`, and reads the Vault address and token from configuration rather than the process environment.

### 2.4 Deployment assets

- `quorus-controller/docker-entrypoint.sh` no longer builds `-Dquorus.raft.*` flags. It exports `QUORUS_NODE_ID`, `QUORUS_RAFT_PORT`, `QUORUS_CLUSTER_NODES`, `QUORUS_RAFT_ELECTION_TIMEOUT_MS`, and `QUORUS_RAFT_HEARTBEAT_INTERVAL_MS`. The default Raft port in the script changed from 8080 to 9080 to match the packaged properties and every compose file. Note that the controller `Dockerfile` does not invoke this script; its `CMD` runs `java` directly. See finding 4.
- `quorus-agent/docker-entrypoint.sh` no longer builds `-Dquorus.agent.*` flags. It still exports the legacy names, and the agent `Dockerfile` does use this script.
- `quorus-agent.properties` now ships keys for HTTP timeouts, security profile, TLS, upload and download roots, NFS mount root, attestations, pool, zone, and Vault. The `quorus.properties` header no longer advertises `-D` overrides.

### 2.5 Documentation touched by the diff

- [docs-design/design/QUORUS_RAFT_WAL_DESIGN.md](../docs-design/design/QUORUS_RAFT_WAL_DESIGN.md): the `FileRaftWAL` and verticle snippets take an `AppConfig`; the systemd unit no longer passes `-Dquorus.config.file`; one `RaftStorageFactory` snippet shows the asynchronous signature. A second snippet near line 3642 still shows the removed two-argument form.
- [docs-design/task/QUORUS_ALPHA_IMPLEMENTATION_PLAN.md](../docs-design/task/QUORUS_ALPHA_IMPLEMENTATION_PLAN.md): NFS mount root moved to `quorus.agent.nfs.mount-root`; the `AgentConfig` row and Appendix C describe the new precedence; `QUORUS_HTTP_HOST` default corrected.
- [QUORUS_SERVICE_CONNECTION_OPERATIONS_RUNBOOK.md](QUORUS_SERVICE_CONNECTION_OPERATIONS_RUNBOOK.md): mount attestations are `QUORUS_AGENT_SMB_ENCRYPTED_AUTHENTICATED_MOUNT` and `QUORUS_AGENT_NFS_ENCRYPTED_AUTHENTICATED_MOUNT`.

### 2.6 Tests

- New helper `quorus-controller/src/test/java/dev/mars/quorus/controller/config/ControllerTestConfig.java` creates an isolated `AppConfig("test", overrides)` with a defensive copy of the overrides. About fifteen HTTP and integration tests now pass `ControllerTestConfig.create()` into `HttpApiServer`.
- New resource `quorus-controller/src/test/resources/quorus-controller-precedence.properties` sets `quorus.http.port=18081` and backs the precedence test.
- `AppConfigNodeIdentityTest`, `AgentConfigTest`, and `QuorusConfigurationTest` were rewritten. Tests that set system properties were replaced by tests that assert system properties are ignored, that instances are isolated, and that explicit overrides win.
- `QuorusControllerVerticleTest` gained a test that deploys two controllers in one Vert.x instance with different ports and body limits.
- `TelemetryConfigTest`, `AgentTelemetryIntegrationTest`, and `TransferProgressPolicyHttpIntegrationTest` build their configuration explicitly instead of through system properties.
- `NfsTransferProtocolTest` gained a test that the factory injects an explicit mount root and both attestation flags.

## 3. File inventory

**quorus-core** — `QuorusConfiguration`, `MountedFileSystemSecurity`, `NfsTransferProtocol`, `ProtocolFactory`, `SmbTransferProtocol`, `SimpleTransferEngine`, `quorus.properties`, `QuorusConfigurationTest`, `NfsTransferProtocolTest`.

**quorus-controller main** — `docker-entrypoint.sh`, `QuorusControllerApplication`, `QuorusControllerVerticle`, `config/AppConfig`, `http/HttpApiServer`, `http/handlers/JobAssignmentHandler`, `http/handlers/MetricsHandler`, `observability/TelemetryConfig`, `raft/storage/RaftLogStorageAdapter`, `raft/storage/RaftStorageFactory`, `service/JobAssignmentService`.

**quorus-controller test** — new `config/ControllerTestConfig`, new `resources/quorus-controller-precedence.properties`, `QuorusControllerVerticleTest`, `config/AppConfigNodeIdentityTest`, `observability/TelemetryConfigTest`, `service/JobAssignmentServiceTest`, `raft/DurableTransferRestartTest`, `lifecycle/GracefulShutdownIntegrationTest`, four `integration/*` tests, and twelve `http/*` tests that only changed the `HttpApiServer` constructor call.

**quorus-agent** — `docker-entrypoint.sh`, `QuorusAgent`, `config/AgentConfig`, `config/AgentConfiguration`, `observability/AgentTelemetryConfig`, `service/TransferExecutionService`, `quorus-agent.properties`, `config/AgentConfigTest`, `integration/AgentTelemetryIntegrationTest`.

**quorus-integration-examples** — `BasicTransferExample`, `InternalNetworkTransferExample`.

**docs** — the three files listed in section 2.5.

## 4. Verification performed

All commands were run from the repository root on 2026-09-03.

| Step | Command | Result |
|---|---|---|
| Compile all modules with tests | `mvn -q -DskipTests test-compile` | exit 0 |
| Core config and NFS tests | `mvn -q test -pl quorus-core -Dtest=QuorusConfigurationTest,NfsTransferProtocolTest` | 81 tests, 0 failures |
| Controller config, verticle, and HTTP tests | `mvn -q clean test -pl quorus-controller -Dtest=AppConfigNodeIdentityTest,TelemetryConfigTest,QuorusControllerVerticleTest,JobAssignmentServiceTest,JobAssignmentHandlerTest,HttpApiServerHealthTest` | 57 tests, 0 failures |
| Agent config and service tests | `mvn -q clean test -pl quorus-agent -Dtest=AgentConfigTest,QuorusAgentTest,JobPollingServiceTest,TransferExecutionServiceTest` | 37 tests, 0 failures |

Two caveats:

1. **Stale incremental build output.** Before the `clean`, the controller and agent test runs failed at JUnit discovery with `NoClassDefFoundError` naming unqualified classes such as `AgentInfo`, `TransferResult`, and `RouteStatus`, and one controller test-compile reported `dev.mars.quorus.core.RouteStatus cannot be converted to RouteStatus`. These came from inconsistent class files left in `target/` by earlier incremental builds, not from the diff. A `clean` on the affected module resolved them every time. Run `mvn clean verify` rather than an incremental build before committing.
2. **Not exercised.** The full test suites, the Testcontainers-based integration tests, the Docker Compose topologies, and the `quorus-workflow` and `quorus-tenant` tests beyond compilation were not run. `AgentTelemetryIntegrationTest` needs Docker.

## 5. Review findings

A multi-angle code review was run against the diff. Findings inside the diff were verified by hand and are listed here. Findings against code that was already committed in `fab72a6` are listed separately in section 7 and were not verified.

### 5.1 Fix before committing

**Finding 1 — Controller environment overrides for three hyphenated keys are silently dropped.**  
Location: [AppConfig.java:460](../quorus-controller/src/main/java/dev/mars/quorus/controller/config/AppConfig.java#L460), `resolveEnvironmentKey`.  
`applyEnvironmentOverrides` converts `QUORUS_JOBS_ATTEMPT_LEASE_DURATION_MS` to `quorus.jobs.attempt.lease.duration.ms` and then searches the already-loaded keys for one whose hyphens, replaced by dots, match. The accessors for `quorus.jobs.attempt.lease-duration-ms`, `quorus.raft.io.queue-size`, and `quorus.raft.snapshot.check-interval-ms` are not present in `quorus-controller.properties`, so the search fails, the dotted key is stored, and the accessor default wins with no warning. Before the refactor `getString` derived the variable name from the accessor key at lookup time, so these variables worked.  
Fix options, best first: drive the mapping from a declared set of accessor keys; or map at lookup time from the injected environment map while keeping explicit overrides above it; or as a stopgap add the three keys to the packaged file with blank values, which the blank-means-unset rule makes safe. Add a test using the package-private constructor with a hyphenated key that is absent from the packaged file.

**Finding 2 — Agent legacy variable names now override the documented `QUORUS_AGENT_*` names.**  
Location: [AgentConfig.java:337-360](../quorus-agent/src/main/java/dev/mars/quorus/agent/config/AgentConfig.java#L337-L360).  
The legacy-name map is applied after the generic pass, so `AGENT_ID` beats `QUORUS_AGENT_ID`. Before the refactor the order was the reverse. The consequence is concrete because [quorus-agent/docker-entrypoint.sh:26-32](../quorus-agent/docker-entrypoint.sh#L26-L32) unconditionally exports `AGENT_REGION`, `AGENT_DATACENTER`, `SUPPORTED_PROTOCOLS`, `MAX_CONCURRENT_TRANSFERS`, `HEARTBEAT_INTERVAL`, `AGENT_PORT`, and `AGENT_VERSION` with defaults. In a container the `QUORUS_AGENT_*` forms of those seven settings can therefore never take effect.  
Fix: apply the legacy map first, as a fallback, and the generic `QUORUS_AGENT_*` pass second. Add a test that sets both names and asserts the documented one wins.  
Two related points. The generic pass derives variable names only from keys present in the packaged file, so an accessor key that is missing from it becomes unreachable from the environment; today every accessor key is present, but nothing enforces that. And `AgentConfiguration.from` no longer fails when the agent id is unset, because `AgentConfig.getAgentId()` falls back to the hostname; the Docker entrypoint still rejects a missing `AGENT_ID`, but a bare `java -jar` start does not.

**Finding 3 — A non-positive attempt lease duration hangs controller startup instead of failing it.**  
Location: [JobAssignmentHandler.java:77](../quorus-controller/src/main/java/dev/mars/quorus/controller/http/handlers/JobAssignmentHandler.java#L77), constructed from [QuorusControllerVerticle.java:184](../quorus-controller/src/main/java/dev/mars/quorus/controller/QuorusControllerVerticle.java#L184).  
The handler now rejects a lease of zero or less. It is constructed inside the `node.start().onSuccess` callback, where a thrown exception is logged by Vert.x and the start promise is never completed, so the deployment sits forever. `AppConfig.validate()` does not check the lease.  
Fix: validate `quorus.jobs.attempt.lease-duration-ms` in `AppConfig.validate()` so `main` fails fast, and wrap the callback body in a try/catch that fails the start promise so any future constructor exception surfaces.

### 5.2 Fix soon

**Finding 4 — The controller entrypoint script clobbers compose-provided values.**  
Location: [quorus-controller/docker-entrypoint.sh:43-47](../quorus-controller/docker-entrypoint.sh#L43-L47).  
The script exports `QUORUS_NODE_ID="$NODE_ID"` after defaulting `NODE_ID` to `controller1`, so a container that receives only `QUORUS_NODE_ID=controller2`, which is how every compose file is written, would be renamed to `controller1`. It also exits when `CLUSTER_NODES` is unset even if `QUORUS_CLUSTER_NODES` is set. This is latent because the controller `Dockerfile` does not call the script.  
Fix: make each export fill-only, for example `export QUORUS_NODE_ID="${QUORUS_NODE_ID:-${NODE_ID:-$DEFAULT_NODE_ID}}"`, and validate on `QUORUS_CLUSTER_NODES`; or delete the script and let compose set `QUORUS_*` directly.

### 5.3 Cleanup

**Finding 5 — Dead and uncalled code left by the refactor.**  
- `createRocksDbStorage` at [RaftStorageFactory.java:167](../quorus-controller/src/main/java/dev/mars/quorus/controller/raft/storage/RaftStorageFactory.java#L167) lost its only caller when the synchronous `create` overloads were removed.  
- `ProtocolFactory(Vertx, String)` at [ProtocolFactory.java:62](../quorus-core/src/main/java/dev/mars/quorus/protocol/ProtocolFactory.java#L62) and the five-argument `SimpleTransferEngine` at [SimpleTransferEngine.java:101](../quorus-core/src/main/java/dev/mars/quorus/transfer/SimpleTransferEngine.java#L101) have no callers.  
- The eight-argument `HttpApiServer` constructor at [HttpApiServer.java:111](../quorus-controller/src/main/java/dev/mars/quorus/controller/http/HttpApiServer.java#L111) has no callers. The `prometheusPort` parameter across the constructor chain exists only so one test can pass a port that its `AppConfig` could carry instead.  
- The blank-mount-root decision is a ternary in `ProtocolFactory` plus a boolean-only constructor at [NfsTransferProtocol.java:91](../quorus-core/src/main/java/dev/mars/quorus/protocol/NfsTransferProtocol.java#L91). Resolving blank to the platform default inside `NfsTransferProtocol(String, boolean)` removes both.

**Finding 6 — Three copies of the layered loader that already disagree.**  
`AppConfig`, `AgentConfig`, and `QuorusConfiguration` each carry their own `loadResource`, `applyEnvironmentOverrides`, and typed getters. They differ in environment mapping strategy, in whether the environment is injectable, in trimming, and in blank handling. A single loader in `quorus-core` driven by a declared key set would remove the duplication and fix finding 1 and the fragile half of finding 2 at the same time.

**Finding 7 — `AgentConfiguration.Builder` duplicates defaults.**  
Location: [AgentConfiguration.java:259-266](../quorus-agent/src/main/java/dev/mars/quorus/agent/config/AgentConfiguration.java#L259-L266).  
The builder hard-codes a third copy of the defaults. Its foreign-assignment threshold of 3 disagrees with the packaged value of 1, so tests that build through the `Builder` run with values production never uses. Removing the literal defaults that `from(AgentConfig)` always sets, and steering tests through `AgentConfiguration.from(new AgentConfig("test", overrides))`, leaves one source of truth.

### 5.4 Documentation outside the diff

**Finding 8 — Stale references to removed behaviour.**  
- [.github/copilot-instructions.md:49-53](../.github/copilot-instructions.md#L49-L53) still lists `-Dquorus.http.port=8080` as a configuration channel and describes `AppConfig.get()` and `AgentConfig.get()` as the access pattern. Anyone following it will write code that no longer compiles or configuration that is silently ignored.  
- [QUORUS_RAFT_WAL_DESIGN.md:3642](../docs-design/design/QUORUS_RAFT_WAL_DESIGN.md#L3642) still calls `RaftStorageFactory.create(vertx, walExecutor)`.  
- [QUORUS_ARCHITECTURE_SPECIFICATION.md](QUORUS_ARCHITECTURE_SPECIFICATION.md) should state the new precedence contract if it describes configuration resolution anywhere.

## 6. Recommended next steps

1. Apply the fixes for findings 1, 2, and 3.
2. Add three tests:
   - `AppConfigNodeIdentityTest`: construct with the package-private constructor, an environment map containing `QUORUS_JOBS_ATTEMPT_LEASE_DURATION_MS`, and assert `getAttemptLeaseDurationMs()` reflects it.
   - `AgentConfigTest`: precedence between a legacy name and its `QUORUS_AGENT_*` equivalent. `AgentConfig` reads `System.getenv()` directly, so either add a package-private constructor that accepts an environment map, mirroring `AppConfig`, or assert the ordering through the packaged-key path.
   - `AppConfigNodeIdentityTest`: `validate()` rejects a non-positive lease duration.
3. Update the two stale documents in finding 8 and the architecture specification.
4. Run `mvn clean verify`. If Docker is available, also run the compose smoke test described in [QUORUS-DOCKER-TESTING-README.md](QUORUS-DOCKER-TESTING-README.md), because the agent entrypoint changed.
5. Commit. A suggested split is one commit for the configuration contract and wiring, and a second for the entrypoint and documentation changes. Suggested subject for the first: `refactor(config): replace singletons and system properties with isolated layered configuration`.
6. Schedule the cleanup in findings 5 to 7 as a follow-up. Finding 6 is the largest and should be designed rather than patched.

## 7. Known issues outside this change

The review agents also reported the following against code committed in `fab72a6`. They were **not** verified during this handover and are recorded here so they are not lost. Each should be confirmed before work starts.

- `TransferHandler.handleCreate` and `ServiceConnectionHandler.validateConnection` call `HostResolver.system()` on the Vert.x event loop, which performs blocking DNS resolution.
- After the `reportInProgress` call moved behind governed authorization in `QuorusAgent`, a job that fails before that report sends `FAILED` with expected state `IN_PROGRESS`, which the state store rejects with a CAS mismatch; the attempt then idles until its lease expires.
- `ConnectionPolicyEnforcer.within` never matches an `allowedPaths` entry of `/` for any path other than `/` itself.
- `ServiceConnectionRegistry` builds metadata keys from tenant and resource ids that may both contain `.`, so keys can collide across tenants and prefix listing is not tenant-isolated.
- `ServiceConnectionRouteProbe` uses `URI.getPort()`, which is `-1` for endpoints without an explicit port, and rejects them.
- `PinnedEndpoint.virtualHost()` returns `host:port`; Vert.x uses that string as the TLS peer host and Host header, so governed HTTPS endpoints on a non-default port fail name verification.
- A partial `trustPolicy` object in a `PUT` on a service connection resets pins and minimum TLS version to defaults rather than keeping the existing values.
- `ConnectionPolicyEnforcer.normalizeRemotePath` normalises through `URI.create`, which strips `#` and `?` and rejects some legal path characters.
- Security events are appended under unique keys that are never pruned, and every list operation copies the whole system metadata map.
- `HttpTransferProtocol` builds a new `WebClient` and trust manager per governed transfer, and `TlsPeerPolicy.defaultTrustManager` reloads the JDK trust store on every call.
- `TransferRequest` now rejects user-info URIs, which blocks replay of Raft logs containing pre-upgrade credential-bearing jobs and breaks `docker/scripts/test-transfers.ps1` and the protocol-server testing guide.
- `HttpTransferProtocol` sets `followRedirects(false)` on the shared client, affecting non-governed transfers too.
- FTPS endpoints without an explicit port are authorised on 990 but connected on 21.
- The JSON codec for service connections and secrets is duplicated between the agent and the controller and has already drifted in null handling and case sensitivity.

## 8. Reference

**Environment variable mapping**

| Property key | Environment variable |
|---|---|
| `quorus.node.id` | `QUORUS_NODE_ID` |
| `quorus.http.port` | `QUORUS_HTTP_PORT` |
| `quorus.raft.election-timeout-ms` | `QUORUS_RAFT_ELECTION_TIMEOUT_MS` |
| `quorus.jobs.attempt.lease-duration-ms` | `QUORUS_JOBS_ATTEMPT_LEASE_DURATION_MS` — see finding 1 |
| `quorus.agent.nfs.mount-root` | `QUORUS_AGENT_NFS_MOUNT_ROOT` |
| `quorus.agent.nfs.encrypted-authenticated-mount` | `QUORUS_AGENT_NFS_ENCRYPTED_AUTHENTICATED_MOUNT` |
| `quorus.agent.id` | `QUORUS_AGENT_ID`, or legacy `AGENT_ID` — see finding 2 |
| `quorus.vault.token` | `QUORUS_VAULT_TOKEN` |

**Useful commands**

```powershell
# Full build with tests, from a clean state
mvn clean verify 2>&1 | Tee-Object -FilePath build.log

# Only the configuration-related tests
mvn clean test -pl quorus-core,quorus-controller,quorus-agent `
  -Dtest="QuorusConfigurationTest,AppConfigNodeIdentityTest,AgentConfigTest,TelemetryConfigTest,QuorusControllerVerticleTest" `
  -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | Tee-Object -FilePath config-tests.log

# Working-tree summary, excluding editor history
git status --short
git diff --stat
```

**Key files for the next engineer**

- [AppConfig.java](../quorus-controller/src/main/java/dev/mars/quorus/controller/config/AppConfig.java) — controller loader and the environment mapping in question.
- [AgentConfig.java](../quorus-agent/src/main/java/dev/mars/quorus/agent/config/AgentConfig.java) — agent loader and legacy-name map.
- [AgentConfiguration.java](../quorus-agent/src/main/java/dev/mars/quorus/agent/config/AgentConfiguration.java) — typed agent configuration and builder.
- [QuorusConfiguration.java](../quorus-core/src/main/java/dev/mars/quorus/config/QuorusConfiguration.java) — core loader.
- [ControllerTestConfig.java](../quorus-controller/src/test/java/dev/mars/quorus/controller/config/ControllerTestConfig.java) — test helper used by most controller HTTP tests.
- [quorus-controller.properties](../quorus-controller/src/main/resources/quorus-controller.properties) and [quorus-agent.properties](../quorus-agent/src/main/resources/quorus-agent.properties) — packaged defaults that also define which keys the environment can reach.
