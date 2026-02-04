# Quorus Alpha Implementation Plan

**Version:** 1.9  
**Date:** February 4, 2026  
**Author:** Mark Andrew Ray-Smith Cityline Ltd

---

## Revision History

| Version | Date | Changes |
|---------|------|---------|
| 1.9 | 2026-02-04 | T2.3 (Graceful Shutdown) ✅ COMPLETE - ShutdownCoordinator, drain mode, awaitActiveTransfers |
| 1.8 | 2026-02-04 | Added T5.8 (Test Infrastructure Abstraction) - pluggable test environments for in-memory/Docker |
| 1.7 | 2026-02-04 | Added T5.7 (Dynamic Cluster Membership) for zero-downtime node scaling |
| 1.6 | 2026-02-03 | Added T4.4 (Agent Job Streaming), T4.5 (Tenant Hierarchy), T5.5 (Tenant State Persistence), T5.6 (Workflow State Persistence) from architecture review cross-check |
| 1.5 | 2026-02-03 | Added T2.4 (Strict Node Identity), T3.3 (Transfer Retry Refactor), T4.3 (Scheduling Strategy) |
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

### Total Effort Estimate

| Stage | Tasks | Estimated Effort | Cumulative |
|-------|-------|------------------|------------|
| Stage 1 | 3 | ~6 hours | 1 day |
| Stage 2 | 4 | ~4 days | 1 week |
| Stage 3 | 3 | ~7 days | 2.5 weeks |
| Stage 4 | 5 | ~15 days | 5.5 weeks |
| Stage 5 | 7 | ~7 weeks | 12.5 weeks |
| Stage 6 | 8 | ~12 weeks | **~24.5 weeks** |

> **Resource Note:** Estimates assume 1 developer. Parallelization possible for independent tasks (e.g., T4.1 NFS + T4.2 Tenant Resources).

---

## Stage 1: Same-Day Wins (1-2 hours each)

These items require minimal code changes and deliver immediate value.

### T1.1: Documentation Alignment

**Goal:** Ensure implementation status is accurately reflected in documentation.

| Task | File | Effort | Status |
|------|------|--------|--------|
| ~~Move route architecture to "Future" section~~ | ~~`docs/QUORUS_SYSTEM_DESIGN.md`~~ | ~~30 min~~ | ❌ REMOVED (routes are core design) |
| ~~Mark trigger evaluation as "Planned"~~ | ~~`docs/QUORUS_SYSTEM_DESIGN.md`~~ | ~~15 min~~ | ❌ REMOVED (triggers are core to routes) |
| Document route implementation status | `docs/QUORUS_IMPLEMENTATION_STATUS.md` | 1 hour | ⬜ Pending |
| Create CHANGELOG.md | `docs/` | 1 hour | ⬜ Pending |

> **Note (2026-02-01):** Routes and triggers are **core** to the Quorus architecture, not future features.
> The system is explicitly designed as a "route-based distributed file transfer system."
> Routes define source→destination agent mappings with triggers (EVENT, TIME, INTERVAL, BATCH, SIZE, COMPOSITE).
> See [QUORUS_SYSTEM_DESIGN.md](../design/QUORUS_SYSTEM_DESIGN.md) lines 373-800 for full route specification.

**Acceptance Criteria:**
- [ ] Clear separation between implemented features and not-yet-implemented features
- [ ] Routes and triggers documented as "designed but not yet implemented"
- [ ] No reader confusion about what exists vs what's designed-but-pending

---

### T1.2: Configuration Improvements

**Goal:** Ensure all configurable values have sensible defaults and documentation.

| Task | File | Effort | Status |
|------|------|--------|--------|
| Document all environment variables in README | `README.md` | 1 hour | ⬜ Pending |
| Add default values to AgentConfig | `quorus-agent/` | 30 min | ⬜ Pending |
| Add validation for required config properties | All config classes | 1 hour | ⬜ Pending |
| Create sample `.env.example` file | Root | 30 min | ⬜ Pending |

---

### T1.3: Logging Standardization

**Goal:** Consistent, structured logging across all modules.

| Task | File | Effort | Status |
|------|------|--------|--------|
| Add correlation ID to all log statements | All modules | 2 hours | ⬜ Pending |
| Standardize log levels (DEBUG/INFO/WARN/ERROR) | All modules | 1 hour | ⬜ Pending |
| Add startup banner with version info | Main classes | 30 min | ⬜ Pending |

---

## Stage 2: Quick Code Fixes (1 day each)

These are targeted code changes that fix known issues in core functionality.

### T2.1: Health Endpoint Enhancements ✅ COMPLETE

**Goal:** Richer health information for operational visibility.

**Effort:** 1 day  
**Priority:** 🔴 CRITICAL  
**Dependencies:** None

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Add `/health/ready` endpoint | HttpApiServer | 1 hour | ✅ Complete |
| Add `/health/live` endpoint | HttpApiServer | 1 hour | ✅ Complete |
| Include dependency health in response | HttpApiServer | 2 hours | ✅ Complete |
| Add version info to health response | HttpApiServer | 30 min | ✅ Complete |
| Create health check tests | quorus-controller/test | 2 hours | ✅ Complete |

**Implementation Notes:**
- `/health/live` - Always returns UP (liveness probe)
- `/health/ready` - Checks Raft running + cluster has leader (readiness probe)
- `/health` - Full health with raft, disk space, and memory checks
- Tests in `InfrastructureSmokeTest.java` (testHealthLiveEndpoint, testHealthReadyEndpoint, testFullHealthEndpoint)

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

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Create `ErrorResponse` record | quorus-controller | 30 min | ✅ Complete |
| Create `GlobalErrorHandler` | HttpApiServer | 2 hours | ✅ Complete |
| Apply error handler to all routes | HttpApiServer | 1 hour | ✅ Complete |
| Add error code constants (`ErrorCode` enum) | quorus-controller | 1 hour | ✅ Complete |
| Add `QuorusApiException` for typed errors | quorus-controller | 30 min | ✅ Complete |
| Create error response tests | quorus-controller/test | 1 hour | ✅ Complete |
| Document error codes | docs/QUORUS_API_REFERENCE.md | 1 hour | ⬜ Pending |

**Implementation Notes:**
- `ErrorCode` enum with 25+ error codes covering transfers, agents, cluster, workflows, tenants
- `ErrorResponse` record with `of()` (template args) and `withMessage()` (explicit message)
- `GlobalErrorHandler` maps exceptions to standardized responses
- `QuorusApiException` with factory methods: `notFound()`, `badRequest()`, `notLeader()`, etc.
- Tests in `ErrorResponseTest.java` (14 tests)

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

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Create ShutdownCoordinator with 4-phase shutdown | quorus-controller | 2 hours | ✅ Complete |
| Add HTTP drain mode (503 during shutdown) | HttpApiServer | 1 hour | ✅ Complete |
| Integrate coordinator in QuorusControllerVerticle | quorus-controller | 1 hour | ✅ Complete |
| Add awaitActiveTransfers() to SimpleTransferEngine | quorus-core | 1 hour | ✅ Complete |
| Add graceful shutdown tests (10 tests) | quorus-controller/test | 2 hours | ✅ Complete |

**Implementation Notes:**
- `ShutdownCoordinator` orchestrates 4 phases: DRAIN → AWAIT_COMPLETION → STOP_SERVICES → CLOSE_RESOURCES
- Hooks registered via fluent API: `onDrain()`, `onAwaitCompletion()`, `onServiceStop()`, `onResourceClose()`
- Failure-tolerant: logs errors but continues shutdown sequence
- Idempotent: multiple `shutdown()` calls are safe
- Configurable timeouts: `quorus.shutdown.drain.timeout.ms` (5s), `quorus.shutdown.timeout.ms` (30s)
- HTTP drain mode returns 503 with `Retry-After` header (health probes still allowed)
- `SimpleTransferEngine.awaitActiveTransfers(timeoutMs)` waits for in-flight transfers

---

### T2.4: Strict Node Identity ✅ COMPLETE

**Goal:** Prevent split-brain scenarios in containerized environments.

**Effort:** 2-4 hours  
**Priority:** 🔴 CRITICAL  
**Dependencies:** None

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Remove InetAddress-based node.id guessing | AppConfig | 1 hour | ✅ Complete |
| Add multi-node cluster detection | AppConfig | 30 min | ✅ Complete |
| Throw exception if node.id missing in multi-node cluster | AppConfig | 30 min | ✅ Complete |
| Update Docker entrypoints to require NODE_ID | docker/ | 30 min | ⬜ Pending |
| Add node identity validation tests | quorus-controller/test | 1 hour | ✅ Complete |

**Implementation Notes:**
- Multi-node cluster detected via comma in `quorus.cluster.nodes` property
- `getNodeId()` throws `IllegalStateException` if multi-node cluster and no explicit node ID
- Single-node clusters allow hostname fallback for development convenience
- Configuration precedence: env var > system property (-D) > properties file > default
- Tests in `AppConfigNodeIdentityTest.java` (5 tests covering precedence, multi-node detection)

**Rationale:**
> The current `AppConfig` guesses `node.id` from `InetAddress.getLocalHost()`. In containers/Kubernetes where hostnames are dynamic, this causes nodes to adopt different identities across restarts, leading to split-brain and data corruption.

**Acceptance Criteria:**
- [x] `QUORUS_NODE_ID` environment variable is **required** in multi-node clusters
- [x] Clear error message if node.id is missing: "QUORUS_NODE_ID (or quorus.node.id) must be set explicitly when running in a multi-node cluster"
- [x] Single-node mode may use hostname fallback with warning

---

## Stage 3: Agent & Threading Fixes (2-3 days each)

Fix critical blocking I/O and thread management issues.

### T3.1: Vert.x WebClient Migration (Agent)

**Goal:** Fix blocking I/O in agent event loop.

**Effort:** 3-4 days  
**Priority:** 🔴 CRITICAL  
**Dependencies:** None

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Replace HeartbeatService HttpClient | quorus-agent | 4 hours | ⬜ Pending |
| Replace JobPollingService HttpClient | quorus-agent | 4 hours | ⬜ Pending |
| Replace JobStatusReportingService HttpClient | quorus-agent | 4 hours | ⬜ Pending |
| Replace AgentRegistrationService HttpClient | quorus-agent | 4 hours | ⬜ Pending |
| Remove Apache HttpClient dependency | pom.xml | 30 min | ⬜ Pending |
| Update all agent service tests | quorus-agent/test | 8 hours | ⬜ Pending |

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

### T3.2: Bounded Thread Pools

**Goal:** Replace unbounded thread pools with controlled resources.

**Effort:** 3 days  
**Priority:** 🟡 HIGH  
**Dependencies:** None

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Replace newCachedThreadPool in GrpcRaftTransport | quorus-controller | 2 hours | ⬜ Pending |
| Use vertx.createSharedWorkerExecutor | quorus-controller | 2 hours | ⬜ Pending |
| Add thread pool metrics | quorus-controller | 4 hours | ⬜ Pending |
| Configure pool sizes via properties | AppConfig | 2 hours | ⬜ Pending |
| Add thread pool exhaustion tests | quorus-controller/test | 4 hours | ⬜ Pending |

---

### T3.3: Refactor Transfer Engine Retries

**Goal:** Make transfer retries fully non-blocking/reactive.

**Effort:** 1-2 days  
**Priority:** 🟡 HIGH  
**Dependencies:** None

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Replace Thread.sleep with vertx.setTimer | SimpleTransferEngine | 2 hours | ⬜ Pending |
| Refactor retry logic to use Promise chain | SimpleTransferEngine | 4 hours | ⬜ Pending |
| Add configurable retry delays | CoreConfig | 1 hour | ⬜ Pending |
| Update retry tests for async behavior | quorus-core/test | 4 hours | ⬜ Pending |

**Current Issue:**
```java
// BAD: Blocks worker thread during backoff
Thread.sleep(backoffMs);
```

**Target Pattern:**
```java
// GOOD: Non-blocking timer-based retry
Promise<TransferResult> promise = Promise.promise();
vertx.setTimer(backoffMs, id -> {
    executeTransfer(request)
        .onSuccess(promise::complete)
        .onFailure(err -> scheduleRetry(attempt + 1, promise));
});
return promise.future();
```

**Rationale:**
> The current `Thread.sleep` in retry backoff blocks the WorkerExecutor thread, limiting throughput to the worker pool size. With 20 workers and 5-second backoff, only 20 concurrent retries can be in-flight. Timer-based retries allow thousands of concurrent retry waits without blocking threads.

---

## Stage 4: Protocol & Services (3-5 days each)

Extend capabilities with new protocols and services.

### T4.1: NFS Protocol Adapter

**Goal:** Enable NFS file transfers for corporate networks.

**Effort:** 4-5 days  
**Priority:** 🟡 HIGH  
**Dependencies:** None

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Create NfsTransferProtocol interface impl | quorus-core | 8 hours | ⬜ Pending |
| Add NFS client library dependency | pom.xml | 1 hour | ⬜ Pending |
| Register NFS in ProtocolFactory | quorus-core | 1 hour | ⬜ Pending |
| Add NFS configuration properties | quorus.properties | 1 hour | ⬜ Pending |
| Create NFS Testcontainer setup | quorus-core/test | 4 hours | ⬜ Pending |
| Add NFS integration tests | quorus-core/test | 8 hours | ⬜ Pending |
| Document NFS usage | docs/ | 2 hours | ⬜ Pending |

---

### T4.2: Tenant Resource Management Improvements

**Goal:** Remove synchronization bottlenecks in tenant resource tracking.

**Effort:** 3 days  
**Priority:** 🟡 HIGH  
**Dependencies:** None

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Refactor SimpleResourceManagementService to use LongAdder | quorus-tenant | 4 hours | ⬜ Pending |
| Remove synchronized(lock) global bottleneck | quorus-tenant | 4 hours | ⬜ Pending |
| Add concurrent access stress tests | quorus-tenant/test | 8 hours | ⬜ Pending |
| Add resource tracking metrics | quorus-tenant | 4 hours | ⬜ Pending |

---

### T4.3: Extract Scheduling Strategy

**Goal:** Decouple agent scheduling logic from domain POJOs to enable pluggable strategies.

**Effort:** 2-3 days  
**Priority:** 🟡 HIGH  
**Dependencies:** None (but **blocks T6.7 Routes**)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Create SchedulingStrategy interface | quorus-controller | 2 hours | ⬜ Pending |
| Implement DefaultSchedulingStrategy | quorus-controller | 4 hours | ⬜ Pending |
| Extract scoring logic from AgentCapabilities | quorus-controller | 2 hours | ⬜ Pending |
| Add strategy configuration property | AppConfig | 1 hour | ⬜ Pending |
| Create RouteAwareSchedulingStrategy (stub) | quorus-controller | 2 hours | ⬜ Pending |
| Add scheduling strategy tests | quorus-controller/test | 4 hours | ⬜ Pending |

**Current Issue:**
```java
// In AgentCapabilities.java - hardcoded weights
public double calculateCompatibilityScore(TransferRequest request) {
    return protocolScore * 0.4 + sizeScore * 0.3 + regionScore * 0.3;
}
```

**Target Design:**
```java
public interface SchedulingStrategy {
    double scoreAgent(AgentCapabilities agent, TransferRequest request);
    List<AgentCapabilities> rankAgents(List<AgentCapabilities> agents, TransferRequest request);
}

public class DefaultSchedulingStrategy implements SchedulingStrategy {
    private final double protocolWeight;  // configurable
    private final double sizeWeight;
    private final double regionWeight;
}
```

**Rationale:**
> Routes (T6.7) require agent selection based on route configuration, not just generic compatibility. Hardcoding the scoring in `AgentCapabilities` violates SRP and makes route-aware scheduling impossible without major refactoring. Extract now to avoid tech debt.

---

### T4.4: Agent Job Streaming (Alternative to Polling)

**Goal:** Reduce job assignment latency from 10 seconds to near-instant.

**Effort:** 3-4 days  
**Priority:** 🟠 MEDIUM  
**Dependencies:** T3.1 (WebClient Migration)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Evaluate long-polling vs gRPC streaming | docs-design/ | 4 hours | ⬜ Pending |
| Implement long-polling endpoint `/agents/{id}/jobs/stream` | HttpApiServer | 1 day | ⬜ Pending |
| Update JobPollingService to use long-polling | quorus-agent | 1 day | ⬜ Pending |
| Add connection timeout/retry handling | quorus-agent | 4 hours | ⬜ Pending |
| Create streaming job assignment tests | quorus-agent/test | 1 day | ⬜ Pending |

**Current Behavior:**
> Agent polls `GET /agents/{id}/jobs` every 10 seconds. If a job is assigned immediately after a poll, the agent won't see it for up to 10 seconds.

**Target Behavior:**
> Long-polling: Agent makes request, controller holds connection until a job is available (or timeout). Reduces latency to milliseconds for job assignment.

**Alternative (Future):**
> gRPC bidirectional streaming for instant push notifications. More complex but eliminates polling entirely.

---

### T4.5: Tenant Hierarchy Optimization

**Goal:** Improve cycle detection performance for deep tenant hierarchies.

**Effort:** 2 days  
**Priority:** 🟠 MEDIUM  
**Dependencies:** T4.2 (Tenant Resource Management)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Add `depth` field to Tenant model | quorus-tenant | 2 hours | ⬜ Pending |
| Add `path` materialized view (e.g., "/root/parent/child") | quorus-tenant | 4 hours | ⬜ Pending |
| Refactor `wouldCreateCircularReference` to use path | SimpleTenantService | 4 hours | ⬜ Pending |
| Update path on tenant move/reparent | SimpleTenantService | 2 hours | ⬜ Pending |
| Add hierarchy depth limit configuration | TenantConfig | 1 hour | ⬜ Pending |
| Create deep hierarchy performance tests | quorus-tenant/test | 4 hours | ⬜ Pending |

**Current Issue:**
> `wouldCreateCircularReference()` walks up the tenant tree iteratively. For a hierarchy 10 levels deep, this performs 10 lookups per validation.

**Target Design:**
> Store materialized path (e.g., `"/acme/division1/team-a"`) on each tenant. Cycle detection becomes a simple string prefix check: `O(1)` instead of `O(depth)`.

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

### T5.2: Raft Log Compaction & Snapshots (Partially Complete)

**Goal:** Prevent infinite memory/disk growth.

**Effort:** 1 week (reduced - core snapshot methods exist)  
**Priority:** 🔴 CRITICAL  
**Dependencies:** T5.1 (WAL)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| ~~Implement QuorusStateMachine.takeSnapshot()~~ | ~~quorus-controller~~ | ~~2 days~~ | ✅ Complete (line 530) |
| ~~Implement QuorusStateMachine.restoreSnapshot()~~ | ~~quorus-controller~~ | ~~1 day~~ | ✅ Complete (line 551) |
| Add snapshot scheduling | RaftNode | 1 day | ⬜ Pending |
| Implement log truncation after snapshot | RaftNode | 1 day | ⬜ Pending |
| Add snapshot metrics | quorus-controller | 4 hours | ⬜ Pending |
| Create snapshot scheduling tests | quorus-controller/test | 1 day | ⬜ Pending |

> **Note (2026-02-01):** `takeSnapshot()` and `restoreSnapshot()` are fully implemented in 
> [QuorusStateMachine.java](../../quorus-controller/src/main/java/dev/mars/quorus/controller/state/QuorusStateMachine.java#L530-L577).
> Tests exist in [QuorusStateMachineTest.java](../../quorus-controller/src/test/java/dev/mars/quorus/controller/state/QuorusStateMachineTest.java).
> Remaining work: automatic scheduling, log truncation, and metrics.

---

### T5.3: InstallSnapshot RPC

**Goal:** Catch up lagging followers efficiently.

**Effort:** 1 week  
**Priority:** 🟡 HIGH  
**Dependencies:** T5.2 (Snapshots)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Add InstallSnapshot to raft.proto | quorus-controller | 2 hours | ⬜ Pending |
| Implement InstallSnapshot handler | GrpcRaftServer | 1 day | ⬜ Pending |
| Implement InstallSnapshot sender | GrpcRaftTransport | 1 day | ⬜ Pending |
| Add chunked transfer for large snapshots | quorus-controller | 1 day | ⬜ Pending |
| Create slow follower tests | quorus-controller/test | 2 days | ⬜ Pending |

---

### T5.4: Replace Java Serialization with Protobuf

**Goal:** Version-safe, efficient state machine replication.

**Effort:** 1.5 weeks  
**Priority:** 🟡 HIGH  
**Dependencies:** T5.1 (WAL)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Define Command messages in raft.proto | quorus-controller | 1 day | ⬜ Pending |
| Generate Protobuf classes | quorus-controller | 2 hours | ⬜ Pending |
| Migrate TransferJobCommand to Protobuf | quorus-controller | 1 day | ⬜ Pending |
| Migrate AgentCommand to Protobuf | quorus-controller | 1 day | ⬜ Pending |
| Migrate all other commands | quorus-controller | 2 days | ⬜ Pending |
| Update LogEntry to use bytes | quorus-controller | 4 hours | ⬜ Pending |
| Add backward compatibility tests | quorus-controller/test | 2 days | ⬜ Pending |

---

### T5.5: Persist Tenant State via Raft

**Goal:** Tenant definitions and quotas survive controller restarts.

**Effort:** 3-4 days  
**Priority:** 🔴 CRITICAL  
**Dependencies:** T5.1 (WAL), T5.4 (Protobuf preferred)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Define TenantCommand Protobuf messages | quorus-controller | 4 hours | ⬜ Pending |
| Add TenantCommand to QuorusStateMachine | quorus-controller | 4 hours | ⬜ Pending |
| Create TenantStateSnapshot serialization | quorus-controller | 4 hours | ⬜ Pending |
| Wire SimpleTenantService to Raft commands | quorus-tenant | 1 day | ⬜ Pending |
| Add tenant state recovery on startup | quorus-controller | 4 hours | ⬜ Pending |
| Create tenant persistence integration tests | quorus-controller/test | 1 day | ⬜ Pending |

**Current Issue:**
> `SimpleTenantService` stores tenants in `ConcurrentHashMap`. On restart, all tenant definitions, quotas, and usage tracking are lost.

**Target Design:**
```java
// Tenant mutations go through Raft
public Future<Tenant> createTenant(Tenant tenant) {
    TenantCommand cmd = TenantCommand.newBuilder()
        .setType(CREATE)
        .setTenant(toProto(tenant))
        .build();
    return raftNode.propose(cmd.toByteArray())
        .map(index -> tenant);
}
```

**Impact:**
> Without this, tenant isolation boundaries are lost on restart. Agents may process jobs for non-existent tenants, or quotas may be exceeded.

---

### T5.6: Persist Workflow Execution State

**Goal:** Resume mid-flight workflows after controller restart.

**Effort:** 3-4 days  
**Priority:** 🟠 MEDIUM  
**Dependencies:** T5.1 (WAL), T5.5 (Tenant persistence pattern)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Define WorkflowCommand Protobuf messages | quorus-controller | 4 hours | ⬜ Pending |
| Add WorkflowCommand to QuorusStateMachine | quorus-controller | 4 hours | ⬜ Pending |
| Create WorkflowExecutionSnapshot serialization | quorus-workflow | 4 hours | ⬜ Pending |
| Wire SimpleWorkflowEngine to Raft commands | quorus-workflow | 1 day | ⬜ Pending |
| Implement workflow resume on startup | quorus-workflow | 4 hours | ⬜ Pending |
| Create workflow recovery integration tests | quorus-workflow/test | 1 day | ⬜ Pending |

**Current Issue:**
> `SimpleWorkflowEngine` stores active executions in `ConcurrentHashMap`. If a workflow is at step 3 of 5 when the controller restarts, the workflow is lost. Completed transfers may be re-executed, causing duplicates.

**Target Design:**
> Each workflow state transition (START, STEP_COMPLETE, PAUSE, RESUME, CANCEL) is persisted via Raft. On recovery, workflows resume from their last persisted state.

**State to Persist:**
- Workflow ID, definition, variables
- Current step index
- Completed transfer IDs (for idempotency)
- Execution status (RUNNING, PAUSED, etc.)

---

### T5.7: Dynamic Cluster Membership

**Goal:** Add or remove cluster nodes without restarting the entire cluster.

**Effort:** 1.5-2 weeks  
**Priority:** 🟡 HIGH  
**Dependencies:** T5.1 (WAL), T5.2 (Snapshots), T5.3 (InstallSnapshot)

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Design membership change protocol | docs-design/ | 4 hours | ⬜ Pending |
| Add ConfigurationChange to raft.proto | quorus-controller | 2 hours | ⬜ Pending |
| Implement joint consensus in RaftNode | quorus-controller | 3 days | ⬜ Pending |
| Store cluster config in state machine | quorus-controller | 1 day | ⬜ Pending |
| Add `POST /api/v1/cluster/nodes` endpoint | HttpApiServer | 4 hours | ⬜ Pending |
| Add `DELETE /api/v1/cluster/nodes/{nodeId}` endpoint | HttpApiServer | 4 hours | ⬜ Pending |
| Add `GET /api/v1/cluster/nodes` endpoint | HttpApiServer | 2 hours | ⬜ Pending |
| Implement new node catch-up (uses InstallSnapshot) | quorus-controller | 1 day | ⬜ Pending |
| Add membership change integration tests | quorus-controller/test | 2 days | ⬜ Pending |
| Document cluster operations | docs/ | 4 hours | ⬜ Pending |

**Current Limitation:**
> Cluster membership is static, configured via `quorus.cluster.nodes` property. Adding a 4th node requires updating configuration on ALL nodes and restarting the entire cluster, causing downtime.

**Target Design (Joint Consensus):**
```
1. Operator calls: POST /api/v1/cluster/nodes {"nodeId": "node4", "address": "host4:9080"}
2. Leader creates log entry: C_old → C_old,new (joint configuration)
3. Entry must be replicated to majority of BOTH old AND new configs
4. Once committed, leader creates C_new entry (new configuration only)
5. Node4 receives snapshot via InstallSnapshot RPC and becomes full member
6. Old nodes not in C_new gracefully shut down Raft participation
```

**API Endpoints:**

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/cluster/nodes` | List current cluster membership |
| `POST` | `/api/v1/cluster/nodes` | Add a node to the cluster |
| `DELETE` | `/api/v1/cluster/nodes/{nodeId}` | Remove a node from the cluster |

**Request/Response Examples:**

```json
// POST /api/v1/cluster/nodes
{
  "nodeId": "node4",
  "address": "host4:9080"
}

// Response
{
  "success": true,
  "message": "Node node4 added to cluster",
  "clusterSize": 4,
  "configurationChangeId": "cfg-12345"
}
```

**Safety Guarantees:**
- Only leader can initiate membership changes
- Only one membership change at a time (no concurrent changes)
- New node must catch up via InstallSnapshot before becoming voter
- Removal requires node to not be the leader (leader must step down first)

**Operational Benefits:**
- Zero-downtime cluster scaling
- Safe node replacement for maintenance
- Automatic failover during node removal

---

### T5.8: Test Infrastructure Abstraction Layer

**Goal:** Make simulator tests runnable against both in-memory and Docker environments without code duplication.

**Effort:** 4-5 days  
**Priority:** 🟡 HIGH  
**Dependencies:** Existing simulator tests in quorus-core

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Define `TestClusterEnvironment` interface | quorus-core/test | 2 hours | ⬜ Pending |
| Define `TestEnvironmentConfig` for timeouts/retries | quorus-core/test | 2 hours | ⬜ Pending |
| Create `InMemoryClusterEnvironment` (wraps existing simulators) | quorus-core/test | 4 hours | ⬜ Pending |
| Create `DockerClusterEnvironment` (Testcontainers) | quorus-integration-examples | 6 hours | ⬜ Pending |
| Refactor `InMemoryAgentSimulatorTest` to use abstraction | quorus-core/test | 4 hours | ⬜ Pending |
| Refactor `InMemoryTransferEngineSimulatorTest` to use abstraction | quorus-core/test | 4 hours | ⬜ Pending |
| Refactor `InMemoryWorkflowEngineSimulatorTest` to use abstraction | quorus-core/test | 4 hours | ⬜ Pending |
| Add `@EnabledIfDocker` conditional for CI | quorus-core/test | 1 hour | ⬜ Pending |
| Document test infrastructure pattern | docs/ | 2 hours | ⬜ Pending |

**Architecture:**
```
┌─────────────────────────────────────────────────────────────────────┐
│                    TestClusterEnvironment                           │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  + start() / stop()                                          │  │
│  │  + getControllerUrl()                                        │  │
│  │  + registerAgent(AgentRegistration) → agentId                │  │
│  │  + submitTransfer(TransferRequest) → TransferResult          │  │
│  │  + injectNetworkPartition(nodeId)                            │  │
│  │  + getConfig() → TestEnvironmentConfig                       │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              ▲                                      │
│              ┌───────────────┴───────────────┐                      │
│              │                               │                      │
│  ┌───────────────────────┐    ┌───────────────────────┐            │
│  │ InMemoryClusterEnv    │    │ DockerClusterEnv      │            │
│  │ ───────────────────── │    │ ───────────────────── │            │
│  │ Wraps existing        │    │ Testcontainers        │            │
│  │ simulators            │    │ 3-node Raft cluster   │            │
│  │ Fast (ms)             │    │ Real I/O (seconds)    │            │
│  └───────────────────────┘    └───────────────────────┘            │
└─────────────────────────────────────────────────────────────────────┘
```

**Test Environment Configuration:**
```java
public record TestEnvironmentConfig(
    // Timeouts - vary significantly between in-memory and Docker
    Duration operationTimeout,      // In-memory: 1s, Docker: 30s
    Duration clusterStartTimeout,   // In-memory: 100ms, Docker: 60s
    Duration leaderElectionTimeout, // In-memory: 500ms, Docker: 30s
    Duration transferTimeout,       // In-memory: 1s, Docker: 120s
    
    // Retry configuration
    int maxRetries,                 // In-memory: 1, Docker: 3
    Duration retryDelay,            // In-memory: 10ms, Docker: 1s
    
    // Polling intervals (for async operations)
    Duration pollInterval,          // In-memory: 10ms, Docker: 500ms
    Duration pollTimeout,           // In-memory: 1s, Docker: 60s
    
    // Chaos testing parameters
    Duration chaosRecoveryTimeout,  // In-memory: 500ms, Docker: 30s
    boolean chaosSupported          // In-memory: limited, Docker: full
) {
    public static TestEnvironmentConfig inMemory() {
        return new TestEnvironmentConfig(
            Duration.ofSeconds(1),    // operationTimeout
            Duration.ofMillis(100),   // clusterStartTimeout
            Duration.ofMillis(500),   // leaderElectionTimeout
            Duration.ofSeconds(1),    // transferTimeout
            1,                        // maxRetries
            Duration.ofMillis(10),    // retryDelay
            Duration.ofMillis(10),    // pollInterval
            Duration.ofSeconds(1),    // pollTimeout
            Duration.ofMillis(500),   // chaosRecoveryTimeout
            false                     // chaosSupported (limited)
        );
    }
    
    public static TestEnvironmentConfig docker() {
        return new TestEnvironmentConfig(
            Duration.ofSeconds(30),   // operationTimeout
            Duration.ofSeconds(60),   // clusterStartTimeout
            Duration.ofSeconds(30),   // leaderElectionTimeout
            Duration.ofSeconds(120),  // transferTimeout
            3,                        // maxRetries
            Duration.ofSeconds(1),    // retryDelay
            Duration.ofMillis(500),   // pollInterval
            Duration.ofSeconds(60),   // pollTimeout
            Duration.ofSeconds(30),   // chaosRecoveryTimeout
            true                      // chaosSupported (full)
        );
    }
}
```

**Test Pattern:**
```java
@ParameterizedTest
@MethodSource("environments")
@DisplayName("Agent should register and receive jobs")
void testAgentLifecycle(TestClusterEnvironment env) {
    var config = env.getConfig();
    env.start();
    try {
        // Use config-driven timeouts
        await().atMost(config.operationTimeout())
               .pollInterval(config.pollInterval())
               .untilAsserted(() -> {
                   String agentId = env.registerAgent(registration);
                   assertThat(agentId).isNotNull();
               });
        
        env.submitTransfer(request);
        
        // Polling with environment-specific intervals
        await().atMost(config.pollTimeout())
               .pollInterval(config.pollInterval())
               .until(() -> !env.pollJobs(agentId).isEmpty());
    } finally {
        env.stop();
    }
}

static Stream<TestClusterEnvironment> environments() {
    var envs = new ArrayList<TestClusterEnvironment>();
    envs.add(new InMemoryClusterEnvironment());  // Always runs
    
    if (DockerClusterEnvironment.isDockerAvailable()) {
        envs.add(new DockerClusterEnvironment());  // Only if Docker running
    }
    return envs.stream();
}
```

**Execution Modes:**
| Mode | Command | Speed | Docker Required |
|------|---------|-------|------------------|
| Fast (default) | `mvn test` | ~30s | No |
| Full E2E | `mvn test -Pintegration` | ~5min | Yes |
| Docker only | `mvn test -Dtest.env=docker` | ~5min | Yes |

**Benefits:**
- **Zero test logic duplication** - same assertions validate both environments
- **Environment-aware timeouts** - tests don't fail due to unrealistic expectations
- **Gradual adoption** - refactor tests incrementally, not all at once
- **Easy extensibility** - add Kubernetes, cloud environments later
- **CI flexibility** - fast in-memory for PRs, full Docker on merge to main

**Acceptance Criteria:**
- [ ] Existing simulator tests pass unchanged with in-memory environment
- [ ] Same tests pass against Docker cluster (when available)
- [ ] Timeouts/retries are configurable per environment
- [ ] CI runs in-memory by default, Docker on merge to main
- [ ] < 100 lines of adapter code per environment

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

> ⚠️ **Staging Note:** Routes are **core business logic**, not security. Placed in Stage 6 for scheduling convenience (after Raft stability), but should be prioritized over T6.1-T6.6 security tasks if resources allow.

**Goal:** Implement the route-based transfer system as specified in QUORUS_SYSTEM_DESIGN.md.

**Effort:** 4-6 weeks  
**Priority:** 🟡 HIGH (Core feature, not optional)  
**Dependencies:** T5.1-T5.3 (Raft stability), T4.3 (Scheduling Strategy)

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
- [ ] **Stage 1**: All documentation and configuration tasks (T1.1, T1.2, T1.3)
- [x] **Stage 2**: T2.1 (Health Endpoints) ✅, T2.4 (Node Identity) ✅, T2.2 (Error Responses) ✅, T2.3 (Graceful Shutdown) ✅

### Week 3-4: Stability & Agent Fixes
- [ ] **Stage 3**: T3.1 (Vert.x WebClient Migration) - CRITICAL BLOCKING FIX
- [ ] **Stage 3**: T3.2 (Bounded Thread Pools)

### Week 5-6: Protocols & Services
- [ ] **Stage 4**: T4.1 (NFS Protocol Adapter)
- [ ] **Stage 4**: T4.2 (Tenant Resource Management)

### Week 7-10: Raft Persistence (CRITICAL)
- [x] **Stage 5**: T5.1 (Raft WAL) - ✅ COMPLETE via raftlog-core
- [ ] **Stage 5**: T5.2 (Snapshots) - scheduling/truncation remain

### Week 11-14: Raft Maturity
- [ ] **Stage 5**: T5.3 (InstallSnapshot RPC)
- [ ] **Stage 5**: T5.4 (Replace Java Serialization with Protobuf)

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
| 1 | Documentation | T1.1 Documentation alignment |
| 2 | Core API | T2.1 Health ✅, T2.2 Errors ✅, T2.3 Shutdown ✅, T2.4 Node Identity ✅ |
| 3 | Stability | T3.1 Vert.x WebClient (BLOCKING FIX) |
| 4 | Agent | T3.1 Vert.x WebClient (BLOCKING FIX), **T3.3 Transfer Retries** 🆕 |
| 5 | Threading | T3.2 Bounded thread pools |
| 6 | Protocols | T4.1 NFS adapter, **T4.3 Scheduling Strategy** 🆕 |
| 7-8 | Persistence | ~~T5.1 Raft WAL~~ ✅ COMPLETE, **T5.5 Tenant Persistence** 🆕 |
| 9-10 | Compaction | T5.2 Snapshots (scheduling/truncation remain), **T5.6 Workflow Persistence** 🆕 |
| 11-12 | Raft | T5.3 InstallSnapshot, T5.4 Protobuf |
| 13-14 | Cluster Ops | **T5.7 Dynamic Cluster Membership** 🆕, **T5.8 Test Infrastructure Abstraction** 🆕 |
| 15+ | Security + Routes | T6.1-T6.6 (Security), T6.7 (Routes) |

### Summary by Priority

| Priority | Total Tasks | Completed | Remaining |
|----------|-------------|-----------|-----------|
| 🔴 CRITICAL | 6 | 1 (T5.1) | 5 |
| 🟡 HIGH | 10 | 0 | 10 |
| 🟠 MEDIUM (Security/Enterprise) | 14 | 0 | 14 |
| **TOTAL** | **30** | **1** | **29** |

> **Notes:**
> - T5.1 (Raft WAL) marked COMPLETE via raftlog-core library
> - T5.2 takeSnapshot/restoreSnapshot methods are COMPLETE; scheduling remains
> - T6.7 changed from "decision point" to implementation task (routes are core)
> - T1.1 reduced scope (routes are not "future" features)
> - **v1.5**: Added T2.4, T3.3, T4.3 to address concurrency and stability gaps
> - **v1.6**: Added T4.4, T4.5, T5.5, T5.6 from architecture review cross-reference
> - **v1.7**: Added T5.7 (Dynamic Cluster Membership) for zero-downtime scaling

---

## Dependencies Graph

```
T1.1 (Docs) ─────────────────────────────────────────────────────────┐
                                                                     │
T2.1 (Health) ──► T2.2 (Errors) ──► T2.3 (Shutdown) ─────────────────┤
                                                                     │
T2.4 (Node Identity) ────────────────────────────────────────────────┤
                                                                     │
T3.1 (WebClient) ──► T3.2 (Thread Pools) ────────────────────────────┤
       │                                                             │
       └──► T4.4 (Agent Job Streaming)  ◄── 🆕                       │
                                                                     │
T3.3 (Transfer Retries) ─────────────────────────────────────────────┤
                                                                     │
T4.1 (NFS) ──────────────────────────────────────────────────────────┤
                                                                     │
T4.2 (Tenant Resources) ──► T4.5 (Tenant Hierarchy)  ◄── 🆕          │
                                                                     │
T4.3 (Scheduling Strategy) ──────────────────────────────────────────┤
       │                                                             │
       └─────────────────────────────────────┐                       │
                                             │                       │
T5.1 (WAL) ──► T5.2 (Snapshots) ──► T5.3 (InstallSnapshot) ──────────┤
       │              │                      │                       │
       │              │                      └──► T5.7 (Dynamic Membership) 🆕
       │              │                                              │
       │              └──► T5.5 (Tenant Persistence)  ◄── 🆕 CRITICAL│
       │                          │                                  │
       │                          └──► T5.6 (Workflow Persistence)   │
       │                                                             │
       └──► T5.4 (Protobuf) ─────────────────────────────────────────┤
                                                                     │
Existing Simulator Tests ──► T5.8 (Test Infrastructure Abstraction) 🆕
       │                            │                                │
       │  InMemoryClusterEnv ◄──────┤                                │
       │  DockerClusterEnv ◄────────┘                                │
       │                                                             │
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

T5.3 (InstallSnapshot) ──┬──► T6.7 (Routes) [HIGH priority - core feature]
                         │
T4.3 (Scheduling Strategy) ──┘   ◄── BLOCKS Routes
```

---

## Rollback Strategy for High-Risk Tasks

| Task | Risk Level | Rollback Plan |
|------|------------|---------------|
| T5.4 (Protobuf Migration) | 🔴 HIGH | Keep Java serialization as fallback codec; feature flag `quorus.serialization=java\|protobuf` |
| T5.5 (Tenant Persistence) | 🔴 HIGH | Retain in-memory `SimpleTenantService` as fallback; migrate incrementally |
| T5.6 (Workflow Persistence) | 🟡 MEDIUM | Workflow engine can operate stateless (existing behavior) if Raft integration fails |
| T5.7 (Dynamic Membership) | 🟡 MEDIUM | Static membership via config still works; rolling restart as fallback |
| T5.8 (Test Infrastructure) | 🟢 LOW | Tests continue working with existing pattern; abstraction is additive |
| T6.7 (Routes) | 🟡 MEDIUM | Routes are additive; existing workflow/transfer APIs remain functional |

---

## Risk Mitigation

| Risk | Mitigation | Owner |
|------|------------|-------|
| ~~Raft persistence delays~~ | ~~Start T5.1 early (Week 7)~~ ✅ COMPLETE via raftlog-core | Arch Team |
| Agent blocking I/O | T3.1 is on critical path; prioritize in Week 4 | Dev Team |
| **Split-brain in containers** | **T2.4 Node Identity - require explicit node ID for multi-node clusters** | Dev Team |
| **Transfer throughput limits** | **T3.3 Remove Thread.sleep from retry backoff** 🆕 | Dev Team |
| **Routes blocked by tech debt** | **T4.3 Extract Scheduling Strategy before T6.7** 🆕 | Arch Team |
| **Tenant data loss on restart** | **T5.5 Persist tenant state via Raft** 🆕 | Arch Team |
| **Workflow loss on restart** | **T5.6 Persist workflow execution state** 🆕 | Arch Team |
| **Cluster scaling requires downtime** | **T5.7 Dynamic Membership for zero-downtime node changes** 🆕 | Arch Team |
| **Tests don't validate real infrastructure** | **T5.8 Test Infrastructure Abstraction - same tests run against Docker** 🆕 | QA Team |
| Security deferred too long | Core must be stable by Week 14 to start security | PM |
| Test failures | Follow "all tests must pass" principle strictly | All |
| Route implementation scope | Routes are core (4-6 weeks); plan resources for Stage 6 | PM |

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

**Document Status**: Active Implementation Plan  
**Last Updated**: 2026-02-04  
**Version**: 1.7  
**Owner**: Development Team
