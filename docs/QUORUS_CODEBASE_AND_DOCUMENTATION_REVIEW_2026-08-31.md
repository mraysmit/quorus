<img src="quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Codebase and Documentation Review — 2026-08-31

**Version:** 1.0  
**Date:** 2026-08-31  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Status:** Point-in-time technical review  
**Scope:** Repository state inspected on 2026-08-31

## Document purpose

This document records a point-in-time review of the Quorus source code, automated tests, runtime configuration, deployment assets, and user-facing documentation. The review focuses on correctness, durability, security, operability, maintainability, and consistency between documented behavior and the implementation.

The review is based on the repository state inspected on 2026-08-31. It is a technical assessment rather than a certification, penetration test, or exhaustive line-by-line audit of every experimental and archived artifact.

The current normative requirements are maintained in [QUORUS_ARCHITECTURE_SPECIFICATION.md](QUORUS_ARCHITECTURE_SPECIFICATION.md) and [QUORUS_REST_API_SPECIFICATION.md](QUORUS_REST_API_SPECIFICATION.md). Those specifications supersede this review when later implementation or architecture decisions differ.

## Executive summary

Quorus has a coherent modular structure and a solid foundation for a distributed file-transfer platform. The domain model is reasonably explicit, the controller uses a replicated state-machine design, protocol implementations are separated from orchestration, and the principal documentation is unusually candid about incomplete functionality and the absence of built-in authentication.

The current repository should nevertheless be treated as a development-stage system rather than a production-ready release. The review identified five release-blocking issues:

1. A successful agent transfer cannot make the required `ACCEPTED -> IN_PROGRESS -> COMPLETED` controller transition because the production agent never reports `IN_PROGRESS`.
2. The packaged default Raft storage path resolves outside the volume mounted by the recommended Docker Compose deployment, risking loss of consensus state when containers are recreated.
3. SFTP host-key checking is unconditionally disabled.
4. several advertised Docker Compose topologies use obsolete environment-variable names, and one omits the agent tenant identifier required at startup.
5. The agent test sources do not compile from a clean checkout.

Additional concerns include missing referential and tenant validation when assignments are created, race-prone assignment state transitions, whole-file buffering in the HTTP transfer implementation, and configuration options that are documented but ignored or rejected.

The most important next step is to restore one verified, end-to-end path in which a tenant-scoped transfer is assigned, accepted, executed, progressed, completed, persisted across a controller restart, and observed through the API. That path should run in CI using real serialization and protocol-level fixtures, without mocking frameworks.

## Repository overview

Quorus is a Java 25, Maven-based multi-module project built primarily on Vert.x. The reviewed repository contains approximately 164 main Java source files and 144 Java test files across the following modules:

| Module | Main responsibility | Approximate main/test source count |
| --- | --- | ---: |
| `quorus-core` | Domain types, transfer protocols, shared networking and utilities | 49 / 76 |
| `quorus-workflow` | Workflow definitions and execution behavior | 15 / 11 |
| `quorus-controller` | HTTP API, Raft, replicated state, scheduling and orchestration | 64 / 43 |
| `quorus-agent` | Registration, polling, transfer execution and status reporting | 11 / 8 |
| `quorus-tenant` | Tenant-related behavior and supporting types | 8 / 5 |
| `quorus-examples` | Usage examples and demonstrations | 17 / 1 |

This separation is a useful architectural property: protocol code is not embedded directly in HTTP handlers, and replicated-state concerns are concentrated in the controller. The main risks found by this review occur at the boundaries between those modules—particularly agent/controller lifecycle integration, configuration/deployment integration, and API/state-machine validation.

## Findings summary

| ID | Severity | Finding | Primary impact |
| --- | --- | --- | --- |
| QR-01 | Critical | Agent omits the `IN_PROGRESS` transition | Successful transfers remain incomplete in controller state |
| QR-02 | Critical | Default Raft data is not stored in the mounted Compose volume | Consensus state can be lost on container recreation |
| QR-03 | High | SFTP host-key verification is disabled | Credentials and data are exposed to machine-in-the-middle attacks |
| QR-04 | High | Advertised Compose configurations use obsolete or incomplete configuration | Multi-node deployments fail or form the wrong topology |
| QR-05 | High | Clean agent test compilation fails | CI and reproducible validation are blocked |
| QR-06 | High | Assignment creation lacks referential and tenant validation | Invalid or cross-tenant state can enter the replicated store |
| QR-07 | High | Some assignment transitions do not compare expected committed state | Concurrent commands can overwrite terminal outcomes |
| QR-08 | Medium | HTTP transfers buffer complete files in heap | Large transfers can exhaust memory |
| QR-09 | Medium | HTTP bind-host configuration is ignored | Operators cannot reliably restrict API exposure |
| QR-10 | Medium | RocksDB is advertised but rejected by normal startup validation | Documented configuration cannot be used |
| QR-11 | Low | Documentation contains broken and stale guidance | Users are directed to invalid links and deployments |

## Detailed findings

### QR-01 — Agent omits the required `IN_PROGRESS` transition

**Severity:** Critical  
**Components:** `quorus-agent`, `quorus-controller`, `quorus-core`

The production agent reports that it accepted a job and then immediately executes it. On successful completion, it reports `COMPLETED`; it does not call the existing `reportInProgress` operation before or during execution.

Evidence:

- [`QuorusAgent.processJob`](../quorus-agent/src/main/java/dev/mars/quorus/agent/QuorusAgent.java#L413) reports `ACCEPTED` and starts execution.
- [`QuorusAgent.handleTransferSuccess`](../quorus-agent/src/main/java/dev/mars/quorus/agent/QuorusAgent.java#L446) reports `COMPLETED` directly.
- [`JobStatusReportingService`](../quorus-agent/src/main/java/dev/mars/quorus/agent/service/JobStatusReportingService.java#L67) provides `reportInProgress`, but production execution does not invoke it.
- [`JobAssignmentStatus`](../quorus-core/src/main/java/dev/mars/quorus/core/JobAssignmentStatus.java#L170) permits `ACCEPTED -> IN_PROGRESS`, but not `ACCEPTED -> COMPLETED`.
- [`JobStatusHandler`](../quorus-controller/src/main/java/dev/mars/quorus/controller/http/handlers/JobStatusHandler.java#L105) rejects invalid transitions.

Consequently, a successful physical transfer receives an HTTP conflict when it attempts to report completion. Its replicated assignment remains `ACCEPTED`, continues to appear in active polling results, and cannot be completed through the normal production path. On a later poll, reporting `ACCEPTED` again is itself invalid, so the job is effectively stranded.

**Recommendation:** Make `IN_PROGRESS` an explicit, acknowledged step before data transfer begins. Report incremental progress during transfer where the protocol supports it. Treat a failure to establish `IN_PROGRESS` as a reason not to start the transfer. Add an integration test that exercises the production agent against the real controller handlers and proves the complete assignment lifecycle.

### QR-02 — Default Raft storage is outside the persisted Compose volume

**Severity:** Critical  
**Components:** controller configuration, Raft storage, Docker Compose

`AppConfig.getRaftStoragePath()` defines `./data/raft/{nodeId}` as its fallback. However, the packaged properties file explicitly supplies an empty value for `quorus.raft.storage.path`. Java properties therefore return an empty string rather than treating the setting as absent. The controller passes that value to `Path.of`, which resolves to the process working directory.

Evidence:

- [`quorus-controller.properties`](../quorus-controller/src/main/resources/quorus-controller.properties#L49) defines an empty storage path.
- [`AppConfig.getRaftStoragePath`](../quorus-controller/src/main/java/dev/mars/quorus/controller/config/AppConfig.java#L145) only applies its fallback when the property is absent.
- [`QuorusControllerVerticle`](../quorus-controller/src/main/java/dev/mars/quorus/controller/QuorusControllerVerticle.java#L102) constructs a path directly from the returned string.
- The recommended controller-first Compose file mounts only [`/app/data`](../docker/compose/docker-compose-controller-first.yml#L38).

Under the container layout, the effective Raft files are therefore written beneath `/app`, not `/app/data`. Recreating a controller container can discard its write-ahead log and snapshots even though the deployment appears to configure a persistent volume.

**Recommendation:** Remove the empty packaged property or treat blank values as absent. Prefer an explicit container value such as `QUORUS_RAFT_STORAGE_PATH=/app/data/raft/controller1` for every controller. Add a restart/recreation test that commits state, recreates the container, and verifies recovery from the mounted volume.

### QR-03 — SFTP host-key verification is disabled

**Severity:** High  
**Components:** SFTP transfer protocol, security configuration

The SFTP implementation unconditionally sets `StrictHostKeyChecking` to `no` in production code at [`SftpTransferProtocol.java`](../quorus-core/src/main/java/dev/mars/quorus/protocol/SftpTransferProtocol.java#L380). This makes an encrypted connection but does not authenticate the remote server. An attacker able to intercept the connection can impersonate the destination, capture credentials, and read or modify transferred data.

The inline comment describes this as being for demonstration purposes, but the behavior is not isolated to examples or a development profile.

**Recommendation:** Require a known-hosts file or pinned host-key fingerprint by default. If an insecure development mode is retained, name it explicitly, disable it by default, log a prominent warning, and prevent its use in production profiles. Add protocol-level tests for accepted keys, unknown keys, and changed keys.

### QR-04 — Advertised Compose deployments use obsolete or incomplete settings

**Severity:** High  
**Components:** Docker assets, deployment documentation, controller and agent configuration

The controller maps properties such as `quorus.http.port` to environment variables with a `QUORUS_` prefix. The controller-first Compose file follows that contract, but several other advertised files do not.

For example, [`docker-compose-full-network.yml`](../docker/compose/docker-compose-full-network.yml#L21) uses `NODE_ID`, `RAFT_PORT`, `HTTP_PORT`, and `CLUSTER_NODES`. These values are ignored by [`AppConfig`](../quorus-controller/src/main/java/dev/mars/quorus/controller/config/AppConfig.java#L268), causing controllers to fall back to generated/default identities and membership rather than the requested three-node cluster. The same Compose topology does not set `AGENT_TENANT_ID`, even though [`AgentConfiguration`](../quorus-agent/src/main/java/dev/mars/quorus/agent/config/AgentConfiguration.java#L76) requires it.

Similar obsolete controller variables appear in other Compose variants and in the controller Dockerfile. Documentation currently presents several of these files as runnable alternatives.

**Recommendation:** Define one canonical environment-variable contract and mechanically update every Dockerfile, Compose file, guide, and example. Add `docker compose config` checks plus a lightweight startup smoke test for each supported topology. Move obsolete experiments into an explicitly archived directory or remove them from user-facing guides.

### QR-05 — Agent tests do not compile from a clean build

**Severity:** High  
**Components:** agent test suite, CI readiness

A clean Maven test compilation fails at [`AgentTelemetryIntegrationTest.java`](../quorus-agent/src/test/java/dev/mars/quorus/agent/integration/AgentTelemetryIntegrationTest.java#L182). The test calls `ConditionFactory.failMessage(String)`, which is not provided by the configured Awaitility version.

The following command was used:

```text
mvn clean test-compile -pl quorus-agent -am
```

Result:

```text
cannot find symbol
  symbol:   method failMessage(java.lang.String)
  location: class org.awaitility.core.ConditionFactory
BUILD FAILURE
```

Before the clean compilation, a broader test run also exposed agent tests whose shared setup omits the now-required tenant ID, producing repeated setup timeouts. The clean compiler failure prevents a definitive full-suite result until it is corrected.

**Recommendation:** Replace the unsupported Awaitility call with the supported equivalent for the pinned version, provide tenant IDs in every agent fixture, and eliminate long timeouts for deterministic setup failures. Require clean `test` or `verify` execution in CI so stale compiled test classes cannot hide source incompatibilities.

### QR-06 — Assignment creation lacks referential and tenant validation

**Severity:** High  
**Components:** assignment API, replicated state, tenant isolation

[`JobAssignmentHandler.handleAssign`](../quorus-controller/src/main/java/dev/mars/quorus/controller/http/handlers/JobAssignmentHandler.java#L76) maps the request into an assignment command without verifying that:

- the referenced transfer exists;
- the referenced agent exists;
- the transfer and agent belong to the same tenant;
- the assignment tenant is present and consistent with both objects.

[`QuorusStateStore.applyJobAssignmentCommand`](../quorus-controller/src/main/java/dev/mars/quorus/controller/state/QuorusStateStore.java#L351) then inserts the assignment without enforcing those invariants. Polling filters can prevent a mismatched assignment from being delivered, but that only converts the problem into permanently stranded replicated state.

This behavior conflicts with the statement in [`QUORUS_API_REFERENCE.md`](QUORUS_API_REFERENCE.md#L23) that tenant isolation is enforced at every write path.

**Recommendation:** Validate all referenced entities and derive tenant identity from authoritative stored objects rather than trusting request duplication. Repeat the invariant checks inside deterministic state-machine application so every command path, including future internal callers, receives the same protection.

### QR-07 — Assignment transition checks are not atomic with state application

**Severity:** High  
**Components:** assignment API, Raft state machine, concurrency

Assignment handlers validate a transition against current state before submitting a Raft command. The committed state may change between that read and command application. The specialized accept, reject, timeout, and cancel command handlers then overwrite the current assignment without comparing its committed status to the expected status. Examples begin at [`QuorusStateStore.java`](../quorus-controller/src/main/java/dev/mars/quorus/controller/state/QuorusStateStore.java#L365).

Raft serializes commands, but serialization alone does not preserve the invariant: two handlers can both observe `ASSIGNED`, submit competing commands, and have the later command overwrite the first command's terminal result.

The generic status-update command already performs an expected-status comparison, demonstrating the appropriate model.

**Recommendation:** Include `expectedStatus` in every transition command and enforce it at state-machine application time. Return a deterministic compare-and-set conflict when committed state no longer matches. Add concurrent tests for accept/cancel, accept/timeout, reject/accept, and repeated idempotent requests.

### QR-08 — HTTP transfers buffer complete files in heap

**Severity:** Medium  
**Components:** HTTP transfer protocol, memory use, progress reporting

HTTP downloads materialize the full response with [`response.body()`](../quorus-core/src/main/java/dev/mars/quorus/protocol/HttpTransferProtocol.java#L193), and uploads read the complete source using [`readFile()`](../quorus-core/src/main/java/dev/mars/quorus/protocol/HttpTransferProtocol.java#L285) before calling `sendBuffer`. Checksum calculation creates additional byte-array views/copies.

Memory consumption therefore scales with transfer size and concurrent-transfer count. Large files can exhaust the agent heap, and progress cannot be reported accurately until an entire body has been buffered.

**Recommendation:** Stream network and file data with Vert.x backpressure, calculate checksums incrementally, and update progress as chunks are acknowledged. Test with files larger than the configured JVM heap and with multiple concurrent transfers.

### QR-09 — HTTP bind-host configuration is ignored

**Severity:** Medium  
**Components:** controller API server, network exposure

`AppConfig` reads and logs `quorus.http.host`, but [`HttpApiServer.start`](../quorus-controller/src/main/java/dev/mars/quorus/controller/http/HttpApiServer.java#L153) calls `listen(port)` without supplying that host. Operators who configure loopback or a specific interface do not receive the requested binding.

This is especially important because the API documentation correctly states that Quorus has no built-in authentication and expects authentication and authorization to be supplied by external infrastructure.

**Recommendation:** Pass the configured host to the HTTP server listen operation and add an integration test that verifies binding to a selected interface. Document the supported reverse-proxy or service-mesh security boundary.

### QR-10 — RocksDB is documented but rejected by normal startup

**Severity:** Medium  
**Components:** controller configuration, storage documentation

The packaged properties list RocksDB as a supported backend at [`quorus-controller.properties`](../quorus-controller/src/main/resources/quorus-controller.properties#L39), and [`RaftStorageFactory`](../quorus-controller/src/main/java/dev/mars/quorus/controller/raft/storage/RaftStorageFactory.java#L230) parses the option. Normal application configuration validation, however, permits only `raftlog`, `file`, and `memory`, so `rocksdb` is rejected before the factory is reached.

**Recommendation:** Decide whether RocksDB is supported. If it is, align validation and dependencies and add a persistence test. If it is experimental or unsupported, remove it from normal configuration documentation and expose it only through an explicitly experimental profile.

### QR-11 — Broken links and stale documentation guidance

**Severity:** Low  
**Components:** README, design documents, engineering guidance

The root README links to `docs/QUORUS_SYSTEM_DESIGN.md` at [`README.md`](../README.md#L142), but the referenced file is stored under `docs-design/design/`. An internal Markdown-link scan found this as the principal broken link in the root README and active docs.

Some noncanonical design material also recommends Mockito even though current repository engineering rules prohibit Mockito and substitute mocking frameworks. No Mockito dependency or usage was found in active implementation or test code, so this is documentation drift rather than an implementation violation.

**Recommendation:** Repair or remove the system-design link, label archived design documents clearly, and update historical testing guidance so generated work does not reintroduce prohibited approaches. Add an automated internal-link check for Markdown files.

## Testing and verification assessment

The project has substantial unit and component-level test coverage by source count. During this review:

- the controller test suite reported 425 tests passed with no failures or errors before Maven reached the agent module;
- clean test compilation succeeded for `quorus-core` and `quorus-workflow`;
- clean agent test compilation failed on the unsupported Awaitility method;
- the earlier non-clean agent execution also exposed missing tenant configuration in shared test setup;
- no Mockito usage was found in active code or tests.

The current suite tests many HTTP operations individually, but it does not protect the most important production integration seam. In particular, individual tests demonstrate that `ACCEPTED -> COMPLETED` is invalid, while the production agent still attempts exactly that transition. This is a classic case where locally correct components produce a broken integrated workflow.

Recommended test priorities are:

1. A real controller/agent lifecycle test covering assignment through completion.
2. Controller restart and Raft recovery from the actual Docker-mounted path.
3. Three-controller Compose formation and leader election using the documented configuration.
4. Cross-tenant and dangling-reference assignment rejection.
5. Concurrent assignment transition tests at the replicated-state boundary.
6. SFTP host identity tests using an ephemeral real SSH/SFTP server fixture.
7. Large streaming HTTP transfer tests with bounded heap.

Tests should continue to exercise observable behavior using real implementations, protocol-level fixtures, purpose-built fakes, and integration environments appropriate to each boundary. Mockito and replacement mocking frameworks should not be introduced.

## Documentation assessment

### Strengths

- The root README gives a useful architectural overview and accurately identifies Java 25.
- The API reference explicitly discloses that authentication is an external concern.
- Current limitations such as incomplete route-trigger evaluation and adapter resume behavior are acknowledged rather than presented as finished functionality.
- Deployment, startup, observability, API, and design subjects are separated into focused documents.

### Weaknesses

- Multiple deployment guides advertise Compose files that do not match the current configuration contract.
- Tenant-isolation claims are stronger than the assignment implementation warrants.
- Storage-path comments describe a default that the packaged blank property defeats.
- Supported-storage documentation contradicts startup validation.
- Canonical and historical design documents are not distinguished clearly enough.

The documentation should be generated or checked against configuration metadata where practical. At minimum, environment-variable names, required agent fields, supported storage types, default paths, exposed ports, and Compose topology names should have automated consistency checks.

## Recommended remediation plan

### Phase 1 — Restore a trustworthy build and lifecycle

1. Fix clean agent test compilation and all tenant-less agent fixtures.
2. Add the missing `IN_PROGRESS` production transition.
3. Add an end-to-end controller/agent completion test.
4. Make every assignment transition compare expected committed state.
5. Reject missing, dangling, and cross-tenant assignments.

### Phase 2 — Protect durability and deployment correctness

1. Correct blank-value handling for the Raft storage path.
2. Set explicit per-controller paths beneath `/app/data`.
3. Verify recovery after container recreation.
4. Normalize all Docker environment variables to `QUORUS_*`.
5. Add required tenant configuration to every agent service.
6. Smoke-test every Compose topology still presented as supported.

### Phase 3 — Close security and scalability gaps

1. Enable SFTP host-key verification by default.
2. Honor the configured HTTP bind host.
3. Document and validate the external authentication boundary.
4. Stream HTTP uploads and downloads with bounded memory.
5. Add realistic failure, cancellation, retry, and cleanup tests at protocol boundaries.

### Phase 4 — Reconcile documentation and optional features

1. Decide and document whether RocksDB is supported.
2. Repair internal links and archive stale design guidance.
3. Revalidate all API tenant-isolation statements.
4. Add automated checks for Markdown links and deployment/configuration drift.

## Release-readiness recommendation

Quorus should not currently be promoted as production-ready or enterprise-ready. QR-01 through QR-05 should be treated as release blockers. QR-06 and QR-07 should also be resolved before exposing assignment APIs to multiple tenants or concurrent operators. Once those findings are fixed, the project should pass a clean Maven verification build and a documented multi-node Docker acceptance test before a release candidate is cut.

The underlying architecture is serviceable, and most findings can be addressed without a wholesale redesign. The central requirement is to make invariants—job lifecycle, tenant ownership, committed-state transitions, durable storage locations, and secure peer identity—executable and testable rather than relying on coordination between independently correct components.
