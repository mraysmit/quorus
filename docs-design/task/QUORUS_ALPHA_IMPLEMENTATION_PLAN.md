# Quorus Alpha Implementation Plan

**Version:** 1.7  
**Date:** February 19, 2026  
**Author:** Mark Andrew Ray-Smith Cityline Ltd

---

## Revision History

| Version | Date | Changes |
|---------|------|---------|
| 1.7 | 2026-02-19 | Consistency fixes: updated Appendix A to match completed tasks (T4.1, T4.2, T5.2, T5.3, T5.4), corrected summary counts (14/22 completed), fixed version header, fixed 2025→2026 date in T3.1, expanded route risk mitigation |
| 1.6 | 2026-02-13 | Consolidated: merged IMPLEMENTATION_STATUS, CHANGELOG, .env.example into appendices |
| 1.5 | 2026-02-13 | Stage 1 & Stage 2 COMPLETE: All documentation, config, logging, health, error, and shutdown tasks |
| 1.4 | 2026-02-02 | Third review: Fixed schedule checkboxes, corrected line references |
| 1.3 | 2026-02-01 | **MAJOR CORRECTIONS**: Routes are core architecture (not "future"), T5.2 snapshots partially complete, T6.7 changed from "decision" to implementation task |
| 1.2 | 2026-02-01 | T5.1 Raft WAL marked COMPLETE (raftlog-core verified) |
| 1.1 | 2026-01-31 | Initial version with Stage-based organization |

---

## Executive Summary

This implementation plan prioritizes **core functionality and stability** before adding security layers. The rationale: security features like TLS and authentication are easier to add once the underlying system is robust and well-tested. The plan is organized into 6 Stages, progressing from quick wins through critical architecture fixes, with security deferred to the final Stage.

### Guiding Principles

From [QUORUS_ALPHA_ARCH_REVIEW_JAN_2026.md](../archive/QUORUS_ALPHA_ARCH_REVIEW_JAN_2026.md):

* **No reflection**: Tests must not use Java reflection API
* **No mocking**: Tests must not use mocking frameworks (Mockito, PowerMock, EasyMock)
* **Use Testcontainers**: Integration tests must use Testcontainers for external services
* **Real objects with simulation**: Unit tests should use real objects with built-in simulation modes
* **Test isolation**: Each test must be independent and not rely on execution order
* **All tests must pass**: Never proceed with failing tests
* **Fix broken tests immediately**: Fix tests as part of the same change
* **Update tests for API changes**: All affected tests must be updated when APIs change

---

## Implementation Stages Overview

| Stage | Effort | Timeline | Focus Area |
|------|--------|----------|------------|
| **Stage 1** | 1-2 hours each | Week 1 | Documentation & Configuration |
| **Stage 2** | 1 day each | Week 1-2 | Quick Code Fixes (Core) |
| **Stage 3** | 2-3 days each | Week 2-4 | Agent & Threading Fixes |
| **Stage 4** | 3-5 days each | Week 4-8 | Protocol & Services |
| **Stage 5** | 1-2 weeks each | Week 8-14 | Architecture (Raft Persistence) |
| **Stage 6** | 2-4 weeks each | Week 14+ | Security & Enterprise Features |

---

## Stage 1: Same-Day Wins (1-2 hours each)

These items require minimal code changes and deliver immediate value.

### T1.1: Documentation Alignment ✅ COMPLETE

**Goal:** Ensure implementation status is accurately reflected in documentation.

| Task | File | Effort | Status |
|------|------|--------|--------|
| ~~Move route architecture to "Future" section~~ | ~~`docs/QUORUS_SYSTEM_DESIGN.md`~~ | ~~30 min~~ | ❌ REMOVED (routes are core design) |
| ~~Mark trigger evaluation as "Planned"~~ | ~~`docs/QUORUS_SYSTEM_DESIGN.md`~~ | ~~15 min~~ | ❌ REMOVED (triggers are core to routes) |
| Document route implementation status | See Appendix A below | 1 hour | ✅ Done |
| Create CHANGELOG.md | See Appendix B below | 1 hour | ✅ Done |

> **Note (2026-02-01):** Routes and triggers are **core** to the Quorus architecture, not future features.
> The system is explicitly designed as a "route-based distributed file transfer system."
> Routes define source→destination agent mappings with triggers (EVENT, TIME, INTERVAL, BATCH, SIZE, COMPOSITE).
> See [QUORUS_SYSTEM_DESIGN.md](../design/QUORUS_SYSTEM_DESIGN.md) lines 373-800 for full route specification.

**Acceptance Criteria:**
- [x] Clear separation between implemented features and not-yet-implemented features
- [x] Routes and triggers documented as "designed but not yet implemented"
- [x] No reader confusion about what exists vs what's designed-but-pending

---

### T1.2: Configuration Improvements ✅ COMPLETE

**Goal:** Ensure all configurable values have sensible defaults and documentation.

| Task | File | Effort | Status |
|------|------|--------|--------|
| Document all environment variables in README | `README.md` | 1 hour | ✅ Done |
| Add default values to AgentConfig | `quorus-agent/` | 30 min | ✅ Done (already had defaults) |
| Add validation for required config properties | All config classes | 1 hour | ✅ Done |
| Create configuration reference | See Appendix C below | 30 min | ✅ Done |

---

### T1.3: Logging Standardization ✅ COMPLETE

**Goal:** Consistent, structured logging across all modules.

| Task | File | Effort | Status |
|------|------|--------|--------|
| Add correlation ID to all log statements | All modules | 2 hours | ✅ Done |
| Standardize log levels (DEBUG/INFO/WARN/ERROR) | All modules | 1 hour | ✅ Done |
| Add startup banner with version info | Main classes | 30 min | ✅ Done |

---

## Stage 2: Quick Code Fixes (1 day each)

These are targeted code changes that fix known issues in core functionality.

### T2.1: Health Endpoint Enhancements ✅ COMPLETE

**Goal:** Richer health information for operational visibility.

**Effort:** 1 day  
**Priority:** 🔴 CRITICAL  
**Dependencies:** None  
**Status:** ✅ COMPLETE (verified 2026-02-13)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Add `/health/ready` endpoint | HttpApiServer | 1 hour | ✅ Done (already existed) |
| Add `/health/live` endpoint | HttpApiServer | 1 hour | ✅ Done (already existed) |
| Include dependency health in response | HttpApiServer | 2 hours | ✅ Done (already existed: disk, memory, raft) |
| Add version info to health response | HttpApiServer | 30 min | ✅ Done (already existed) |
| Create health check tests | quorus-controller/test | 2 hours | ✅ Done (HttpApiServerHealthTest: 10 tests) |

**Target Response Format:**
```json
{
  "status": "UP",
  "version": "1.0.0-alpha",
  "timestamp": "2026-02-01T10:00:00Z",
  "nodeId": "controller1",
  "raft": {
    "state": "LEADER",
    "term": 42,
    "commitIndex": 1000
  },
  "checks": {
    "raftCluster": "UP",
    "diskSpace": "UP",
    "memory": "UP"
  }
}
```

---

### T2.2: Error Response Standardization ✅ COMPLETE

**Goal:** Consistent error responses across all endpoints.

**Effort:** 1 day  
**Priority:** 🟡 HIGH  
**Dependencies:** None  
**Status:** ✅ COMPLETE (verified 2026-02-13)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Create `ErrorResponse` record | quorus-controller | 30 min | ✅ Done (already existed) |
| Create `GlobalErrorHandler` | HttpApiServer | 2 hours | ✅ Done (already existed) |
| Apply error handler to all routes | HttpApiServer | 1 hour | ✅ Done (already existed) |
| Add error code constants | quorus-core | 1 hour | ✅ Done (28 ErrorCode constants existed) |
| Document error codes | docs/QUORUS_API_REFERENCE.md | 1 hour | ✅ Done (all 28 codes documented) |

**Target Error Format:**
```json
{
  "error": {
    "code": "TRANSFER_NOT_FOUND",
    "message": "Transfer job with ID 'xyz' not found",
    "timestamp": "2026-02-01T10:00:00Z",
    "path": "/api/v1/transfers/xyz",
    "requestId": "req-123-456"
  }
}
```

---

### T2.3: Graceful Shutdown ✅ COMPLETE

**Goal:** Clean shutdown without losing in-flight work.

**Effort:** 1 day  
**Priority:** 🟡 HIGH  
**Dependencies:** None  
**Status:** ✅ COMPLETE (verified 2026-02-13)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Add shutdown hook to QuorusControllerApplication | quorus-controller | 1 hour | ✅ Done (ShutdownCoordinator existed) |
| Implement agent drain mode | quorus-agent | 2 hours | ✅ Done (drain mode existed in HttpApiServer) |
| Wait for active transfers before shutdown | SimpleTransferEngine | 2 hours | ✅ Done (4-phase shutdown existed) |
| Add graceful shutdown tests | All modules | 2 hours | ✅ Done (GracefulShutdownIntegrationTest: 3 tests) |

---

## Stage 3: Agent & Threading Fixes (2-3 days each)

Fix critical blocking I/O and thread management issues.

### T3.1: Vert.x WebClient Migration (Agent) ✅ COMPLETE

**Goal:** Fix blocking I/O in agent event loop.

**Effort:** 3-4 days  
**Priority:** 🔴 CRITICAL  
**Dependencies:** None  
**Status:** ✅ COMPLETE (verified 2026-01-30)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Replace HeartbeatService HttpClient | quorus-agent | 4 hours | ✅ Done |
| Replace JobPollingService HttpClient | quorus-agent | 4 hours | ✅ Done |
| Replace JobStatusReportingService HttpClient | quorus-agent | 4 hours | ✅ Done |
| Replace AgentRegistrationService HttpClient | quorus-agent | 4 hours | ✅ Done |
| Remove Apache HttpClient dependency | pom.xml | 30 min | ✅ Done |
| Update all agent service tests | quorus-agent/test | 8 hours | ✅ Done |

**Migration Pattern:**
```java
// Before (blocking)
try (CloseableHttpClient client = HttpClients.createDefault()) {
    HttpPost post = new HttpPost(url);
    post.setEntity(new StringEntity(json));
    client.execute(post);
}

// After (reactive)
webClient.postAbs(url)
    .putHeader("Content-Type", "application/json")
    .sendJsonObject(jsonObject)
    .onSuccess(response -> { ... })
    .onFailure(err -> { ... });
```

---

### T3.2: Bounded Thread Pools ✅ COMPLETE

**Goal:** Replace unbounded thread pools with controlled resources.

**Effort:** 3 days  
**Priority:** 🟡 HIGH  
**Dependencies:** None  
**Status:** ✅ COMPLETE (verified 2026-02-05)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Replace newCachedThreadPool in GrpcRaftTransport | quorus-controller | 2 hours | ✅ Done |
| Use bounded ThreadPoolExecutor with named threads | quorus-controller | 2 hours | ✅ Done |
| Add thread pool metrics (RaftMetrics) | quorus-controller | 4 hours | ✅ Done |
| Configure pool sizes via properties | AppConfig | 2 hours | ✅ Done |
| Add thread pool exhaustion tests | quorus-controller/test | 4 hours | ✅ Done |

---

## Stage 4: Protocol & Services (3-5 days each)

Extend capabilities with new protocols and services.

### T4.1: NFS Protocol Adapter ✅ COMPLETE

**Goal:** Enable NFS file transfers for corporate networks.

**Effort:** 4-5 days  
**Priority:** 🟡 HIGH  
**Dependencies:** None  
**Status:** ✅ **IMPLEMENTED** — mount-path-based NFS adapter with simulation mode

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Create NfsTransferProtocol interface impl | quorus-core | 8 hours | ✅ Complete |
| Add NFS client library dependency | pom.xml | 1 hour | ✅ N/A (uses java.nio mount paths) |
| Register NFS in ProtocolFactory | quorus-core | 1 hour | ✅ Complete |
| Add NFS configuration properties | quorus.properties | 1 hour | ✅ Complete (quorus.nfs.mount.root) |
| Create NFS Testcontainer setup | quorus-core/test | 4 hours | ✅ Complete (simulation mode + @TempDir) |
| Add NFS integration tests | quorus-core/test | 8 hours | ✅ Complete (44 tests) |
| Document NFS usage | docs/ | 2 hours | ✅ Inline Javadoc |

**Implementation Notes:**
- `NfsTransferProtocol` (~420 lines): mount-path-based adapter mapping `nfs://host/export/path` to local mount points
- URI parsing: `NfsConnectionInfo` extracts host, port, exportPath, filePath from NFS URIs
- Mount resolution: configurable via `quorus.nfs.mount.root` system property (default `/mnt/nfs`)
- Simulation mode: detected via hostname patterns (testserver, localhost.test, simulated-nfs-server)
- Checksum: SHA-256 verification on all transfers
- Tests: 44 total (36 in NfsTransferProtocolTest + 8 in NfsTransferProtocolUploadTest)

---

### T4.2: Tenant Resource Management Improvements ✅ COMPLETE

**Goal:** Remove synchronization bottlenecks in tenant resource tracking.

**Effort:** 3 days  
**Priority:** 🟡 HIGH  
**Dependencies:** None  
**Status:** ✅ **IMPLEMENTED** — per-tenant StampedLock + lock-free atomic counters

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Refactor SimpleResourceManagementService to use LongAdder | quorus-tenant | 4 hours | ✅ Complete |
| Remove synchronized(lock) global bottleneck | quorus-tenant | 4 hours | ✅ Complete |
| Add concurrent access stress tests | quorus-tenant/test | 8 hours | ✅ Complete (15 tests) |
| Add resource tracking metrics | quorus-tenant | 4 hours | ✅ Complete |

**Implementation Notes:**
- Replaced global `Object lock` with per-tenant `StampedLock` in `TenantCounters` inner class
- Lock-free operations: `updateConcurrentTransfers`, `updateBandwidthUsage`, `updateStorageUsage`, `recordTransferCompletion` use `AtomicLong`/`LongAdder` directly
- Compound operations: `reserveResources`, `releaseResources`, `recordUsage`, `resetDailyUsage` use per-tenant write lock
- Fixed TOCTOU race: `reserveResources` now validates INSIDE the per-tenant lock
- Gauges: AtomicLong for concurrent transfers, bandwidth, storage; LongAdder for daily/total counters
- New OTel metrics: `quorus.tenant.resource.operations`, `quorus.tenant.resource.concurrent_transfers`, `quorus.tenant.resource.bandwidth` (10 total)
- Tests: 15 concurrent stress tests in `ResourceManagementConcurrencyTest` (16 threads × 500 ops)
- Total quorus-tenant tests: 64 (49 existing + 15 new), all passing

---

## Stage 5: Architecture Improvements (1-2 weeks each)

Larger changes that improve system reliability.

### T5.1: Raft Persistence (Custom WAL) ✅ COMPLETE

**Goal:** Survive controller restarts without data loss.

**Effort:** 2 weeks  
**Priority:** 🔴 CRITICAL  
**Dependencies:** None  
**Status:** ✅ **IMPLEMENTED** via `raftlog-core` library (dev.mars.raftlog:raftlog-core:1.0)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Design WAL file format | docs-design/ | 1 day | ✅ Complete |
| Implement FileRaftWAL class | quorus-controller | 3 days | ✅ Complete (RaftLogStorageAdapter) |
| Implement raft-meta.dat persistence | quorus-controller | 1 day | ✅ Complete |
| Wire WAL into RaftNode | quorus-controller | 2 days | ✅ Complete (line 76: `private final RaftStorage storage`) |
| Implement log replay on startup | quorus-controller | 1 day | ✅ Complete (recoverFromStorage()) |
| Add fsync configuration | AppConfig | 2 hours | ✅ Complete |
| Create WAL corruption tests | quorus-controller/test | 2 days | ✅ Complete |
| Add recovery integration tests | quorus-controller/test | 2 days | ✅ Complete (RaftLogClusterIntegrationTest)

**Implementation Notes:**
- `RaftLogStorageAdapter` wraps `dev.mars.raftlog.storage.FileRaftStorage`
- Adapts CompletableFuture (raftlog-core) to Vert.x Future (Quorus)
- `RaftStorageFactory` provides configurable storage backends (raftlog, file, memory)
- Recovery flow: `recoverFromStorage()` → `loadMetadata()` → `replayLog()` → `rebuildStateMachine()`

**File Format:**
```
[4 bytes: length][4 bytes: CRC32][N bytes: data]
```

---

### T5.2: Raft Log Compaction & Snapshots ✅ COMPLETE

**Goal:** Prevent infinite memory/disk growth.

**Effort:** 1 week (reduced - core snapshot methods exist)  
**Priority:** 🔴 CRITICAL  
**Dependencies:** T5.1 (WAL)

**Status:** ✅ COMPLETE (verified 2026-02-13)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| ~~Implement QuorusStateMachine.takeSnapshot()~~ | ~~quorus-controller~~ | ~~2 days~~ | ✅ Complete |
| ~~Implement QuorusStateMachine.restoreSnapshot()~~ | ~~quorus-controller~~ | ~~1 day~~ | ✅ Complete |
| Add snapshot scheduling | RaftNode | 1 day | ✅ Complete |
| Implement log truncation after snapshot | RaftNode | 1 day | ✅ Complete |
| Add snapshot metrics | quorus-controller | 4 hours | ✅ Complete |
| Create snapshot scheduling tests | quorus-controller/test | 1 day | ✅ Complete |

**Implementation Details (2026-02-13):**
- RaftNode: offset tracking (`snapshotLastIndex`/`snapshotLastTerm`), periodic `checkAndTakeSnapshot()`, full `takeSnapshot()` flow (capture → save → truncate → trim)
- RaftStorage SPI: added `saveSnapshot()`, `loadSnapshot()`, `truncatePrefix()`, `SnapshotData` record
- All 4 storage backends updated: FileRaftStorage, InMemoryRaftStorage, RaftLogStorageAdapter, RocksDbRaftStorage
- AppConfig: `quorus.snapshot.enabled`, `quorus.snapshot.threshold`, `quorus.snapshot.check-interval-ms`
- QuorusSnapshot: extended with `jobAssignments` and `jobQueue` fields (all 5 state machine maps captured)
- Recovery: snapshot-first recovery in `recoverFromStorage()` — load snapshot → restore state machine → replay only post-snapshot entries
- Metrics: `quorus.raft.snapshot.total`, `quorus.raft.snapshot.duration`, `quorus.raft.log.compacted.entries`
- Tests: 7 in RaftSnapshotTest, 4 in InMemoryRaftStorageTest, 1 in QuorusStateMachineTest (12 new tests total)
- Bug fixes: Jackson annotations for QueuedJob/JobRequirements/TransferRequest deserialization

---

### T5.3: InstallSnapshot RPC

**Goal:** Catch up lagging followers efficiently.

**Effort:** 1 week  
**Priority:** 🟡 HIGH  
**Dependencies:** T5.2 (Snapshots)
**Status:** ✅ COMPLETE (2026-02-13)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Add InstallSnapshot to raft.proto | quorus-controller | 2 hours | ✅ Complete |
| Implement InstallSnapshot handler | GrpcRaftServer | 1 day | ✅ Complete |
| Implement InstallSnapshot sender | GrpcRaftTransport | 1 day | ✅ Complete |
| Add chunked transfer for large snapshots | quorus-controller | 1 day | ✅ Complete |
| Create slow follower tests | quorus-controller/test | 2 days | ✅ Complete |

**Implementation Details:**
- **raft.proto**: Added `InstallSnapshot` RPC, `InstallSnapshotRequest` (term, leader_id, last_included_index, last_included_term, chunk_index, total_chunks, data, done), `InstallSnapshotResponse` (term, success, next_chunk_index)
- **RaftTransport**: Added `sendInstallSnapshot()` method (interface version 3.0)
- **GrpcRaftTransport**: Delegates to gRPC stub `installSnapshot()`
- **GrpcRaftServer**: `installSnapshot()` handler delegates to `raftNode.handleInstallSnapshot()`
- **RaftNode (leader side)**: `sendInstallSnapshot()` loads snapshot from storage, splits into 1MB chunks, sends sequentially via `sendSnapshotChunk()`. On final ACK updates `nextIndex`/`matchIndex`. Guards against concurrent installs per follower.
- **RaftNode (follower side)**: `handleInstallSnapshot()` validates term, reassembles chunks via `SnapshotChunkAssembler`, persists to storage, restores state machine, resets log to snapshot boundary.
- **sendAppendEntries()**: Modified to detect `nextIdx <= snapshotLastIndex` and redirect to `sendInstallSnapshot()`
- **Metrics**: `installSnapshotSent` and `installSnapshotReceived` counters
- **Tests (InstallSnapshotTest.java, 7 tests)**: Leader sends snapshot to partitioned-then-healed follower, follower state machine restore + continued replication, leader index tracking after install, stale term rejection, chunk assembler multi-chunk, chunk assembler single-chunk, follower persists snapshot to storage
- **All 5 RaftTransport implementations updated**: GrpcRaftTransport, InMemoryTransportSimulator, MockRaftTransport, RaftFailureTest (anonymous), RaftNodeIntegrationTest.TestRaftTransport

---

### T5.4: Replace Java Serialization with Protobuf

**Goal:** Version-safe, efficient state machine replication.

**Effort:** 1.5 weeks  
**Priority:** 🟡 HIGH  
**Dependencies:** T5.1 (WAL)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Define Command messages in raft.proto | quorus-controller | 1 day | ✅ DONE |
| Generate Protobuf classes | quorus-controller | 2 hours | ✅ DONE |
| Migrate TransferJobCommand to Protobuf | quorus-controller | 1 day | ✅ DONE |
| Migrate AgentCommand to Protobuf | quorus-controller | 1 day | ✅ DONE |
| Migrate all other commands | quorus-controller | 2 days | ✅ DONE |
| Update LogEntry to use bytes | quorus-controller | 4 hours | ✅ DONE |
| Add backward compatibility tests | quorus-controller/test | 2 days | ✅ DONE |

**Implementation Notes:**
- **commands.proto**: 5 domain enums, 5 command type enums, 9 domain messages, 5 command messages, RaftCommand wrapper with oneof
- **ProtobufCommandCodec.java**: ~750-line static codec with serialize/deserialize for all command types and domain objects
- **RaftNode.java**: serialize()/deserialize() methods now delegate to ProtobufCommandCodec (LogEntry.data remains bytes, now Protobuf-encoded)
- **Command classes**: AgentCommand, JobAssignmentCommand, JobQueueCommand gained package-private constructors for timestamp preservation
- **Tests (ProtobufCommandCodecTest.java, 31 tests)**: Roundtrip tests for all 5 command types, all enum values, deep nested objects, null handling, timestamp preservation
- **Controller module**: 317 tests, 0 failures, 0 errors, 2 skipped

---

## Stage 6: Security & Enterprise Features (2-4 weeks each)

Security features implemented **after** core functionality is stable and well-tested.

> **Rationale:** Security layers are easier to add to a stable, well-tested core. Adding authentication/TLS to buggy infrastructure creates debugging complexity. Complete Stages 1-5 first.

### T6.1: API Key Authentication (Basic)

**Goal:** Simple API authentication to protect endpoints.

**Effort:** 1-2 days  
**Priority:** 🟠 MEDIUM (deferred until core complete)  
**Dependencies:** T5.1-T5.4 (core stability)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Create `ApiKeyAuthHandler` | quorus-controller | 2 hours | ⬜ Pending |
| Add API key configuration property | quorus-controller | 30 min | ⬜ Pending |
| Apply handler to protected routes | HttpApiServer | 1 hour | ⬜ Pending |
| Add tests for auth handler | quorus-controller/test | 2 hours | ⬜ Pending |
| Document API key usage | docs/ | 30 min | ⬜ Pending |

**Acceptance Criteria:**
- [ ] All `/api/v1/*` endpoints require `X-API-Key` header
- [ ] `/health` and `/metrics` remain public
- [ ] Invalid/missing key returns 401 Unauthorized
- [ ] API keys loaded from configuration/environment

---

### T6.2: TLS for HTTP API

**Goal:** Encrypted HTTP communication.

**Effort:** 2 days  
**Priority:** 🟠 MEDIUM  
**Dependencies:** T6.1 (API Key Auth)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Add TLS configuration properties | AppConfig | 1 hour | ⬜ Pending |
| Configure Vert.x HttpServerOptions for TLS | HttpApiServer | 2 hours | ⬜ Pending |
| Generate dev/test certificates | scripts/ | 1 hour | ⬜ Pending |
| Update Docker compose with TLS | docker/compose/ | 2 hours | ⬜ Pending |
| Add TLS health check | HttpApiServer | 1 hour | ⬜ Pending |
| Document TLS setup | docs/ | 2 hours | ⬜ Pending |

---

### T6.3: TLS for gRPC Raft

**Goal:** Encrypted Raft cluster communication.

**Effort:** 2-3 days  
**Priority:** 🟠 MEDIUM  
**Dependencies:** T6.2 (TLS HTTP)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Add TLS to GrpcRaftServer | quorus-controller | 3 hours | ⬜ Pending |
| Add TLS to GrpcRaftTransport | quorus-controller | 3 hours | ⬜ Pending |
| Configure mutual TLS (mTLS) | quorus-controller | 4 hours | ⬜ Pending |
| Update cluster configuration | docker/compose/ | 2 hours | ⬜ Pending |
| Add TLS validation tests | quorus-controller/test | 4 hours | ⬜ Pending |

---

### T6.4: TenantSecurityService

**Goal:** Tenant-level access control foundation.

**Effort:** 5 days  
**Priority:** 🟠 MEDIUM  
**Dependencies:** T6.1 (API Key Auth)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Create TenantSecurityService interface | quorus-tenant | 2 hours | ⬜ Pending |
| Implement SimpleTenantSecurityService | quorus-tenant | 8 hours | ⬜ Pending |
| Add tenant isolation checks | quorus-tenant | 4 hours | ⬜ Pending |
| Integrate with HTTP handlers | quorus-controller | 4 hours | ⬜ Pending |
| Add permission model | quorus-tenant | 4 hours | ⬜ Pending |
| Create security tests | quorus-tenant/test | 8 hours | ⬜ Pending |

---

### T6.5: Request Rate Limiting

**Goal:** Protect API from abuse.

**Effort:** 2 days  
**Priority:** 🟠 MEDIUM  
**Dependencies:** T6.1 (API Key for per-client limits)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Implement RateLimitHandler | quorus-controller | 4 hours | ⬜ Pending |
| Add rate limit configuration | AppConfig | 1 hour | ⬜ Pending |
| Apply to API endpoints | HttpApiServer | 2 hours | ⬜ Pending |
| Add 429 response handling | HttpApiServer | 1 hour | ⬜ Pending |
| Add rate limit tests | quorus-controller/test | 3 hours | ⬜ Pending |

---

### T6.6: OAuth2/JWT Authentication

**Goal:** Enterprise authentication integration.

**Effort:** 3 weeks  
**Priority:** 🟠 MEDIUM  
**Dependencies:** T6.2 (TLS), T6.1 (API Key as fallback)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Add JWT validation library | pom.xml | 2 hours | ⬜ Pending |
| Implement JwtAuthHandler | quorus-controller | 3 days | ⬜ Pending |
| Add JWKS endpoint support | quorus-controller | 2 days | ⬜ Pending |
| Implement token refresh handling | quorus-controller | 2 days | ⬜ Pending |
| Add OAuth2 provider configuration | AppConfig | 1 day | ⬜ Pending |
| Create auth integration tests | quorus-controller/test | 3 days | ⬜ Pending |
| Document OAuth2 setup | docs/ | 2 days | ⬜ Pending |

---

### T6.7: Route Architecture Implementation

**Goal:** Implement the route-based transfer system as specified in QUORUS_SYSTEM_DESIGN.md.

**Effort:** 4-6 weeks  
**Priority:** 🟡 HIGH (Core feature, not optional)  
**Dependencies:** T5.1-T5.3 (Raft stability)

> **Note (2026-02-01):** Routes are **core** to Quorus architecture, not a decision point.
> The system is explicitly designed as a "route-based distributed file transfer system."
> See [QUORUS_SYSTEM_DESIGN.md](../design/QUORUS_SYSTEM_DESIGN.md) for full specification.
> 
> **Routes vs Workflows:**
> - **Routes**: Event-driven, continuous monitoring, predefined source→destination with triggers
> - **Workflows**: Manual/scheduled submission, complex multi-step operations, batch processing
> - Both are core features serving different use cases.

**Implementation Tasks:**

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Create RouteConfiguration model | quorus-controller | 2 days | ⬜ Pending |
| Implement RouteStatus lifecycle (CONFIGURED→ACTIVE→TRIGGERED) | quorus-controller | 2 days | ⬜ Pending |
| Implement Trigger Evaluation Engine (Leader-only) | quorus-controller | 1 week | ⬜ Pending |
| Add EVENT trigger (file watching on agent) | quorus-agent | 1 week | ⬜ Pending |
| Add TIME trigger (cron scheduler) | quorus-controller | 3 days | ⬜ Pending |
| Add INTERVAL trigger (periodic) | quorus-controller | 2 days | ⬜ Pending |
| Add BATCH trigger (file count threshold) | quorus-controller | 2 days | ⬜ Pending |
| Add SIZE trigger (cumulative size) | quorus-controller | 2 days | ⬜ Pending |
| Add COMPOSITE trigger (AND/OR logic) | quorus-controller | 3 days | ⬜ Pending |
| Create route API endpoints | HttpApiServer | 3 days | ⬜ Pending |
| Replicate routes via Raft | quorus-controller | 2 days | ⬜ Pending |
| Create route integration tests | quorus-controller/test | 1 week | ⬜ Pending |

**Route Trigger Types (from design):**

| Trigger | Description | Configuration |
|---------|-------------|---------------|
| EVENT | File system events (CREATE, MODIFY, DELETE) | patterns, event types |
| TIME | Cron-based scheduling | cron expression, timezone |
| INTERVAL | Periodic execution | duration (e.g., "15m") |
| BATCH | File count threshold | minFiles, maxWait |
| SIZE | Cumulative size threshold | sizeThreshold, maxWait |
| COMPOSITE | AND/OR of multiple triggers | operator, triggers[] |

---

### T6.8: TenantAwareStorageService

**Goal:** Storage isolation per tenant.

**Effort:** 2 weeks  
**Priority:** 🟠 MEDIUM  
**Dependencies:** T6.4 (TenantSecurityService)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Design storage isolation strategy | docs-design/ | 1 day | ⬜ Pending |
| Create TenantAwareStorageService interface | quorus-tenant | 2 hours | ⬜ Pending |
| Implement path-based isolation | quorus-tenant | 3 days | ⬜ Pending |
| Add storage quota enforcement | quorus-tenant | 2 days | ⬜ Pending |
| Integrate with TransferEngine | quorus-core | 2 days | ⬜ Pending |
| Create isolation tests | quorus-tenant/test | 3 days | ⬜ Pending |

---

## Implementation Schedule

### Week 1-2: Foundation & Quick Wins
- [x] Gap analysis complete
- [x] **Stage 1**: All documentation and configuration tasks (T1.1, T1.2, T1.3) - ✅ COMPLETE
- [x] **Stage 2**: T2.1 (Health Endpoints), T2.2 (Error Responses) - ✅ COMPLETE

### Week 3-4: Stability & Agent Fixes
- [x] **Stage 2**: T2.3 (Graceful Shutdown) - ✅ COMPLETE
- [x] **Stage 3**: T3.1 (Vert.x WebClient Migration) - ✅ COMPLETE
- [x] **Stage 3**: T3.2 (Bounded Thread Pools) - ✅ COMPLETE

### Week 5-6: Protocols & Services
- [x] **Stage 4**: T4.1 (NFS Protocol Adapter) - ✅ COMPLETE (44 tests)
- [x] **Stage 4**: T4.2 (Tenant Resource Management) - ✅ COMPLETE (15 stress tests)

### Week 7-10: Raft Persistence (CRITICAL)
- [x] **Stage 5**: T5.1 (Raft WAL) - ✅ COMPLETE via raftlog-core
- [x] **Stage 5**: T5.2 (Snapshots) - ✅ COMPLETE (scheduling, truncation, metrics, 7 tests)

### Week 11-14: Raft Maturity
- [x] **Stage 5**: T5.3 (InstallSnapshot RPC)
- [x] **Stage 5**: T5.4 (Replace Java Serialization with Protobuf) - ✅ COMPLETE

### Week 15+: Security & Enterprise Features
- [ ] **Stage 6**: T6.1 (API Key Authentication)
- [ ] **Stage 6**: T6.2 (TLS for HTTP)
- [ ] **Stage 6**: T6.3 (TLS for gRPC)
- [ ] **Stage 6**: T6.4 (TenantSecurityService)
- [ ] **Stage 6**: T6.5 (Rate Limiting)
- [ ] **Stage 6**: T6.6 (OAuth2/JWT)
- [ ] **Stage 6**: T6.7 (Route Architecture Implementation)
- [ ] **Stage 6**: T6.8 (TenantAwareStorageService)

---

## Progress Tracking

### Quick Reference: What to Work On Next

| Week | Focus | Critical Path Item |
|------|-------|-------------------|
| 1 | Documentation | ~~T1.1 Documentation alignment~~ ✅ COMPLETE |
| 2 | Core API | ~~T2.1 Health endpoints~~ ✅ COMPLETE |
| 3 | Stability | ~~T2.3 Graceful shutdown~~ ✅ COMPLETE |
| 4 | Agent | ~~T3.1 Vert.x WebClient~~ ✅ COMPLETE |
| 5 | Threading | ~~T3.2 Bounded thread pools~~ ✅ COMPLETE |
| 6 | Protocols | ~~T4.1 NFS adapter~~ ✅ COMPLETE |
| 6 | Resources | ~~T4.2 Tenant Resource Management~~ ✅ COMPLETE |
| 7-8 | Persistence | ~~T5.1 Raft WAL~~ ✅ COMPLETE |
| 9-10 | Compaction | ~~T5.2 Snapshots~~ ✅ COMPLETE |
| 11-12 | Raft | ~~T5.3 InstallSnapshot, T5.4 Protobuf~~ ✅ COMPLETE |
| 15+ | Security + Routes | T6.1-T6.6 (Security), T6.7 (Routes) |

### Summary by Priority

| Priority | Total Tasks | Completed | Remaining |
|----------|-------------|-----------|-----------|
| 🔴 CRITICAL | 4 | 4 (T3.1, T5.1, T5.2, T5.3) | 0 |
| 🟡 HIGH | 7 | 7 (T2.1, T2.2, T2.3, T3.2, T4.1, T4.2, T5.4) | 0 |
| 🟢 LOW | 3 | 3 (T1.1, T1.2, T1.3) | 0 |
| 🟠 MEDIUM (Security - Deferred) | 6 | 0 | 6 |
| 🟡 HIGH (Routes - Core) | 1 | 0 | 1 |
| 🟠 MEDIUM (Enterprise) | 1 | 0 | 1 |
| **TOTAL** | **22** | **14** | **8** |

> **Notes:**
> - T5.1 (Raft WAL) marked COMPLETE via raftlog-core library
> - T3.1 (WebClient Migration) marked COMPLETE with dedicated tests
> - T3.2 (Bounded Thread Pools) marked COMPLETE with RaftMetrics (87% coverage)
> - T5.2 takeSnapshot/restoreSnapshot methods are COMPLETE; ✅ COMPLETE (scheduling, truncation, metrics, 7 tests)
> - T5.3 (InstallSnapshot RPC) marked COMPLETE with 7 tests, all 5 RaftTransport implementations updated
> - T5.4 (Protobuf Serialization) marked COMPLETE: commands.proto, ProtobufCommandCodec, 31 roundtrip tests (317 total controller tests)
> - T4.1 (NFS Protocol Adapter) marked COMPLETE: NfsTransferProtocol (~420 lines), mount-path-based, simulation mode, 44 tests
> - T4.2 (Tenant Resource Management) marked COMPLETE: per-tenant StampedLock + LongAdder, TOCTOU fix, 3 new OTel metrics, 15 concurrent stress tests (64 total tenant tests)
> - T6.7 changed from "decision point" to implementation task (routes are core)
> - T1.1 reduced scope (routes are not "future" features)
> - **Stage 1 COMPLETE (2026-02-13):** T1.1 (docs/QUORUS_IMPLEMENTATION_STATUS.md + CHANGELOG.md), T1.2 (.env.example, README config, AgentConfig/AppConfig validate()), T1.3 (CorrelationIdHandler, MDC logback patterns, startup banners)
> - **Stage 2 COMPLETE (2026-02-13):** T2.1 (health endpoints already existed, added commitIndex + 10 dedicated tests), T2.2 (error system already existed with 28 codes, documented all in API reference), T2.3 (ShutdownCoordinator already existed with 4-phase shutdown, added 3 integration tests)

---

## Dependencies Graph

```
T1.1 (Docs) ─────────────────────────────────────────────────────────┐
                                                                     │
T2.1 (Health) ──► T2.2 (Errors) ──► T2.3 (Shutdown) ─────────────────┤
                                                                     │
T3.1 (WebClient) ──► T3.2 (Thread Pools) ────────────────────────────┤
                                                                     │
T4.1 (NFS) ──────────────────────────────────────────────────────────┤
                                                                     │
T4.2 (Tenant Resources) ─────────────────────────────────────────────┤
                                                                     │
T5.1 (WAL) ──► T5.2 (Snapshots) ──► T5.3 (InstallSnapshot) ──────────┤
       │                                                             │
       └──► T5.4 (Protobuf) ─────────────────────────────────────────┤
                                                                     │
                          ┌──────────────────────────────────────────┘
                          │  SECURITY & ROUTES (After Core Complete)
                          ▼
T6.1 (API Key) ──► T6.2 (TLS HTTP) ──► T6.3 (TLS gRPC)
       │
       ├──► T6.4 (TenantSecurity) ──► T6.8 (TenantStorage)
       │
       ├──► T6.5 (Rate Limiting)
       │
       └──► T6.6 (OAuth2/JWT)

T5.3 (InstallSnapshot) ──► T6.7 (Routes) [HIGH priority - core feature]
```

---

## Risk Mitigation

| Risk | Mitigation | Owner |
|------|------------|-------|
| ~~Raft persistence delays~~ | ~~Start T5.1 early (Week 7)~~ ✅ COMPLETE via raftlog-core | Arch Team |
| Agent blocking I/O | T3.1 is on critical path; prioritize in Week 4 | Dev Team |
| Security deferred too long | Core must be stable by Week 14 to start security | PM |
| Test failures | Follow "all tests must pass" principle strictly | All |
| Route implementation scope | Routes are core (4-6 weeks); plan resources for Stage 6. Break into sub-milestones (triggers individually) to limit scope creep. Track each trigger type as independent deliverable. | PM |

---

## Definition of Done

Each task is complete when:

1. ✅ Code implemented and compiles
2. ✅ Unit tests written and passing
3. ✅ Integration tests written and passing (where applicable)
4. ✅ No reflection or mocking in tests
5. ✅ Documentation updated
6. ✅ Code reviewed and merged
7. ✅ No regression in existing tests

---

## Appendix A: Implementation Status by Module

### Status Key

| Symbol | Meaning |
|--------|---------|
| ✅ | Fully implemented and tested |
| 🔶 | Partially implemented (core exists, gaps noted) |
| ⬜ | Designed but not yet implemented |

### quorus-core (Transfer Engine)

| Feature | Status | Evidence |
|---------|--------|----------|
| `TransferEngine` interface | ✅ | `TransferEngine.java` |
| `SimpleTransferEngine` implementation | ✅ | 656 lines, concurrent transfers, retry, cancel, pause/resume |
| `ProtocolFactory` protocol registry | ✅ | Registers HTTP, HTTPS, FTP, SFTP, SMB |
| `HttpTransferProtocol` (reactive) | ✅ | Non-blocking Vert.x WebClient-based |
| `SftpTransferProtocol` | ✅ | Blocking, uses WorkerExecutor |
| `FtpTransferProtocol` | ✅ | Blocking, uses WorkerExecutor |
| `SmbTransferProtocol` | ✅ | Blocking, uses WorkerExecutor |
| NFS protocol adapter | ✅ | `NfsTransferProtocol` (~420 lines), mount-path-based, simulation mode, 44 tests (T4.1) |
| SHA-256 checksum verification | ✅ | `ChecksumMismatchException` |
| Transfer metrics (OpenTelemetry) | ✅ | `TransferTelemetryMetrics` |
| Transfer health checks | ✅ | `TransferEngineHealthCheck` (183 lines) |
| Exception hierarchy | ✅ | `QuorusException` → `TransferException`, `ChecksumMismatchException` |
| Graceful shutdown | ✅ | `SimpleTransferEngine` `shutdown()`, `awaitActiveTransfers()` |

### quorus-workflow (YAML Workflow Engine)

| Feature | Status | Evidence |
|---------|--------|----------|
| YAML workflow parsing | ✅ | `YamlWorkflowDefinitionParser` |
| Variable substitution (`{{var}}`) | ✅ | Parser handles `{{TODAY}}` and custom variables |
| Dependency graph resolution | ✅ | `DependencyGraph` with topological sorting |
| Workflow execution engine | ✅ | Execution modes: normal, dry run, virtual run |
| Workflow validation | ✅ | Cycle detection, missing dependency checks |

### quorus-controller (Raft Consensus & HTTP API)

| Feature | Status | Evidence |
|---------|--------|----------|
| **Raft Consensus** | | |
| `RaftNode` state machine (FOLLOWER→CANDIDATE→LEADER) | ✅ | 1081 lines |
| Leader election with randomized timeouts | ✅ | `resetElectionTimer()` |
| Log replication (AppendEntries) | ✅ | `sendAppendEntries()` |
| WAL persistence | ✅ | `RaftLogStorageAdapter` wraps `raftlog-core:1.0` |
| Raft metadata persistence (term, votedFor) | ✅ | `persistVote()`, `persistAppendEntries()` |
| Recovery on restart | ✅ | `recoverFromStorage()` → `loadMetadata()` → `replayLog()` |
| Snapshot: `takeSnapshot()` / `restoreSnapshot()` | ✅ | `QuorusStateMachine` |
| Snapshot scheduling & log truncation | ✅ | Periodic `checkAndTakeSnapshot()`, configurable threshold/interval, log truncation after snapshot (T5.2) |
| InstallSnapshot RPC | ✅ | `raft.proto` InstallSnapshot RPC, chunked transfer, 7 tests, all 5 transport impls updated (T5.3) |
| Protobuf serialization for commands | ✅ | `commands.proto`, `ProtobufCommandCodec` (~750 lines), 31 roundtrip tests (T5.4) |
| **gRPC Transport** | | |
| `GrpcRaftTransport` (inter-node, bounded pools) | ✅ | Named threads `raft-io-*` |
| `GrpcRaftServer` (handles RPCs) | ✅ | Vote + AppendEntries handlers |
| TLS/mTLS for gRPC | ⬜ | Not yet implemented |
| **HTTP API** | | |
| `HttpApiServer` (embedded Vert.x Web) | ✅ | All endpoints |
| `GET /health`, `/health/live`, `/health/ready` | ✅ | Raft state, disk, memory, version |
| `GET /raft/status` | ✅ | Node state, term, leader info |
| `GET /metrics` (Prometheus proxy) | ✅ | `MetricsHandler` |
| Agent + Transfer + Job endpoints | ✅ | Full REST API |
| TLS for HTTP | ⬜ | Not yet implemented |
| API key authentication | ⬜ | Not yet implemented |
| Rate limiting | ⬜ | Not yet implemented |
| **Error Handling** | | |
| `ErrorCode` enum (28 error codes) | ✅ | All HTTP status mappings |
| `ErrorResponse` record | ✅ | Nested `error` object JSON format |
| `GlobalErrorHandler` | ✅ | Exception→error mapping |
| `QuorusApiException` with factory methods | ✅ | `notFound()`, `badRequest()`, `conflict()`, etc. |
| **State Machine** | | |
| `QuorusStateMachine` | ✅ | 674 lines, all command types |
| **Lifecycle** | | |
| `ShutdownCoordinator` (4-phase) | ✅ | DRAIN→AWAIT→STOP→CLOSE |
| Drain mode | ✅ | `HttpApiServer.enterDrainMode()` |
| Configurable shutdown timeouts | ✅ | `quorus.shutdown.drain.timeout.ms` etc. |
| **Observability** | | |
| OpenTelemetry metrics | ✅ | Raft, cluster, job gauges/counters |
| Prometheus endpoint | ✅ | Port 9464 (configurable) |

### quorus-agent (Transfer Agent)

| Feature | Status | Evidence |
|---------|--------|----------|
| `QuorusAgent` main class | ✅ | Full lifecycle |
| All services (reactive WebClient) | ✅ | Registration, Heartbeat, JobPolling, StatusReporting |
| `TransferExecutionService` | ✅ | Executes transfers via protocol adapters |
| `HealthService` (JDK HttpServer) | ✅ | `/health` + `/status` |
| Graceful shutdown (idempotent) | ✅ | Cancel timers, stop services, deregister |
| `AgentMetrics` (OpenTelemetry) | ✅ | Registration, heartbeat, status gauges |
| Agent configuration (`AgentConfig`) | ✅ | Properties + env var + system property resolution |

### quorus-tenant (Multi-Tenancy)

| Feature | Status | Evidence |
|---------|--------|----------|
| `TenantService` / `SimpleTenantService` | ✅ | In-memory tenant CRUD |
| `ResourceManagementService` | ✅ | Quota tracking |
| `SimpleResourceManagementService` | ✅ | Per-tenant StampedLock + LongAdder, TOCTOU fix, 15 concurrent stress tests (T4.2) |
| `TenantSecurityService` | ⬜ | Not yet implemented |
| `TenantAwareStorageService` | ⬜ | Not yet implemented |

### Route Architecture (Core Design — Not Yet Implemented)

| Trigger | Description | Status |
|---------|-------------|--------|
| EVENT | File system events (CREATE, MODIFY, DELETE) | ⬜ |
| TIME | Cron-based scheduling | ⬜ |
| INTERVAL | Periodic execution | ⬜ |
| BATCH | File count threshold | ⬜ |
| SIZE | Cumulative size threshold | ⬜ |
| COMPOSITE | AND/OR of multiple triggers | ⬜ |

### Test Coverage

| Module | Test Files | Key Test Types |
|--------|-----------|----------------|
| quorus-controller | 32+ | Unit, Integration, Docker cluster, Raft consensus |
| quorus-agent | 6 | Unit, Service tests with real HTTP simulation |
| quorus-core | Multiple | Protocol adapters, transfer engine |
| quorus-workflow | Multiple | YAML parsing, dependency resolution |
| quorus-tenant | Multiple | Tenant CRUD, resource management |

---

## Appendix B: Changelog

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

### [Unreleased]

**Added:**
- Correlation ID middleware (`CorrelationIdHandler`) for HTTP request tracing
- Startup banners for Controller and Agent applications
- `commitIndex` field in `/health` endpoint response
- Dedicated health endpoint test suite (`HttpApiServerHealthTest` — 10 tests)
- Graceful shutdown integration tests (`GracefulShutdownIntegrationTest` — 3 tests)
- Configuration validation (`AgentConfig.validate()`, `AppConfig.validate()`)
- Environment variable documentation in README
- Logback MDC patterns with `requestId` for correlation
- Error codes reference section in API documentation

### [1.0.0-alpha.3] - 2026-02-05

**Fixed:**
- Replaced unbounded `newCachedThreadPool` in `GrpcRaftTransport` with bounded `ThreadPoolExecutor` (T3.2)
- Thread pools now use named threads (`raft-io-*`), sizes configurable via `quorus.raft.io.pool-size`
- Added `RaftMetrics` thread pool monitoring (87% test coverage)

### [1.0.0-alpha.2] - 2026-02-04

**Added:**
- `ErrorCode` enum with 28 standardized error codes
- `ErrorResponse` record, `GlobalErrorHandler`, `QuorusApiException`
- `ShutdownCoordinator` with 4-phase graceful shutdown (DRAIN→AWAIT→STOP→CLOSE)
- Drain mode — rejects non-health requests with 503 + `Retry-After`

**Changed:**
- All agent HTTP services migrated from Apache `HttpClient` to Vert.x `WebClient` (T3.1)
- Removed Apache HttpClient dependency from agent

### [1.0.0-alpha.1] - 2026-01-30

**Added:**
- Raft WAL persistence via `raftlog-core:1.0` library (T5.1)
- `RaftLogStorageAdapter`, `RaftStorageFactory`, recovery flow
- Persist-before-response pattern for all Raft state changes
- `QuorusStateMachine.takeSnapshot()` and `restoreSnapshot()`

### [1.0.0-alpha.0] - 2025-12-16

**Added:**
- Controller-first architecture with embedded HTTP API
- Raft consensus (`RaftNode`, 1081 lines), gRPC transport
- `QuorusStateMachine` with commands for transfers, agents, jobs, queue, metadata
- `HttpApiServer` with health, raft status, agent, transfer, and job endpoints
- `SimpleTransferEngine` with concurrent transfers, retry, cancel, pause/resume
- Protocol adapters: HTTP, HTTPS, FTP, SFTP, SMB
- YAML workflow engine with dependency graphs and variable substitution
- `QuorusAgent` with reactive WebClient services
- OpenTelemetry integration (metrics + tracing)
- Multi-tenancy foundation
- Docker Compose configurations for cluster deployment and monitoring
- 150+ tests (JUnit 5, Testcontainers, no mocking)

---

## Appendix C: Configuration Reference

Configuration resolution order (highest to lowest priority):
1. Environment variable (e.g., `QUORUS_HTTP_PORT=8080`)
2. System property (e.g., `-Dquorus.http.port=8080`)
3. Properties file (`quorus-controller.properties` / `quorus-agent.properties`)
4. Default value

Environment variable naming: replace dots with underscores, uppercase (`quorus.http.port` → `QUORUS_HTTP_PORT`).

### Controller Settings

| Variable | Description | Default |
|----------|-------------|---------|
| `QUORUS_NODE_ID` | Unique node identifier | hostname |
| `QUORUS_HTTP_PORT` | HTTP API port | `8080` |
| `QUORUS_HTTP_HOST` | HTTP API bind address | `0.0.0.0` |
| `QUORUS_RAFT_PORT` | Raft gRPC port | `9080` |
| `QUORUS_CLUSTER_NODES` | Cluster node list (`nodeId=host:port,...`) | `{nodeId}=localhost:{raftPort}` |
| `QUORUS_RAFT_STORAGE_TYPE` | Storage backend: `raftlog`, `file`, `memory` | `file` |
| `QUORUS_RAFT_STORAGE_PATH` | Raft log/metadata directory | `./data/raft/{nodeId}` |
| `QUORUS_RAFT_STORAGE_FSYNC` | Enable fsync for durability | `true` |
| `QUORUS_RAFT_IO_POOL_SIZE` | I/O thread pool size | `10` |
| `QUORUS_JOBS_ASSIGNMENT_INITIAL_DELAY_MS` | Delay before first job assignment | `5000` |
| `QUORUS_JOBS_ASSIGNMENT_INTERVAL_MS` | Job assignment sweep interval | `10000` |
| `QUORUS_JOBS_TIMEOUT_INITIAL_DELAY_MS` | Delay before first timeout check | `30000` |
| `QUORUS_JOBS_TIMEOUT_INTERVAL_MS` | Timeout check interval | `30000` |
| `QUORUS_SHUTDOWN_DRAIN_TIMEOUT_MS` | Drain timeout for in-flight requests | `5000` |
| `QUORUS_SHUTDOWN_TIMEOUT_MS` | Total shutdown timeout | `30000` |
| `QUORUS_TELEMETRY_ENABLED` | Enable OpenTelemetry | `true` |
| `QUORUS_TELEMETRY_OTLP_ENDPOINT` | OTLP exporter endpoint (gRPC) | `http://localhost:4317` |
| `QUORUS_TELEMETRY_PROMETHEUS_PORT` | Prometheus scrape port | `9464` |
| `QUORUS_TELEMETRY_SERVICE_NAME` | Telemetry service name | `quorus-controller` |

### Agent Settings

| Variable | Description | Default |
|----------|-------------|---------|
| `QUORUS_AGENT_ID` | Unique agent identifier | hostname-derived |
| `QUORUS_AGENT_VERSION` | Agent version string | `1.0.0` |
| `QUORUS_AGENT_CONTROLLER_URL` | Controller base URL | `http://localhost:8080/api/v1` |
| `QUORUS_AGENT_PORT` | Agent health/status port | `8090` |
| `QUORUS_AGENT_REGION` | Deployment region | `us-east-1` |
| `QUORUS_AGENT_DATACENTER` | Datacenter name | `dc1` |
| `QUORUS_AGENT_TRANSFERS_MAX_CONCURRENT` | Max concurrent transfers | `5` |
| `QUORUS_AGENT_PROTOCOLS` | Supported protocols (comma-separated) | `HTTP,HTTPS` |
| `QUORUS_AGENT_HEARTBEAT_INTERVAL_MS` | Heartbeat interval | `30000` |
| `QUORUS_AGENT_JOBS_POLLING_INITIAL_DELAY_MS` | Initial delay before first job poll | `5000` |
| `QUORUS_AGENT_JOBS_POLLING_INTERVAL_MS` | Job polling interval | `10000` |
| `QUORUS_AGENT_TELEMETRY_ENABLED` | Enable agent telemetry | `true` |
| `QUORUS_AGENT_TELEMETRY_PROMETHEUS_PORT` | Agent Prometheus port | `9465` |
| `QUORUS_AGENT_TELEMETRY_OTLP_ENDPOINT` | Agent OTLP endpoint | `http://localhost:4317` |

---

**Document Status**: Active Implementation Plan  
**Last Updated**: 2026-02-19  
**Version**: 1.7  
**Owner**: Development Team
