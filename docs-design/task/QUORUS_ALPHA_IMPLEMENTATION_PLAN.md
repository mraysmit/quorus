# Quorus Alpha Implementation Plan

**Version:** 1.4  
**Date:** February 2, 2026  
**Author:** Mark Andrew Ray-Smith Cityline Ltd

---

## Revision History

| Version | Date | Changes |
|---------|------|---------|
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

### T2.1: Health Endpoint Enhancements

**Goal:** Richer health information for operational visibility.

**Effort:** 1 day  
**Priority:** 🔴 CRITICAL  
**Dependencies:** None

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Add `/health/ready` endpoint | HttpApiServer | 1 hour | ⬜ Pending |
| Add `/health/live` endpoint | HttpApiServer | 1 hour | ⬜ Pending |
| Include dependency health in response | HttpApiServer | 2 hours | ⬜ Pending |
| Add version info to health response | HttpApiServer | 30 min | ⬜ Pending |
| Create health check tests | quorus-controller/test | 2 hours | ⬜ Pending |

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

### T2.2: Error Response Standardization

**Goal:** Consistent error responses across all endpoints.

**Effort:** 1 day  
**Priority:** 🟡 HIGH  
**Dependencies:** None

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Create `ErrorResponse` record | quorus-controller | 30 min | ⬜ Pending |
| Create `GlobalErrorHandler` | HttpApiServer | 2 hours | ⬜ Pending |
| Apply error handler to all routes | HttpApiServer | 1 hour | ⬜ Pending |
| Add error code constants | quorus-core | 1 hour | ⬜ Pending |
| Document error codes | docs/QUORUS_API_REFERENCE.md | 1 hour | ⬜ Pending |

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

### T2.3: Graceful Shutdown

**Goal:** Clean shutdown without losing in-flight work.

**Effort:** 1 day  
**Priority:** 🟡 HIGH  
**Dependencies:** None

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Add shutdown hook to QuorusControllerApplication | quorus-controller | 1 hour | ⬜ Pending |
| Implement agent drain mode | quorus-agent | 2 hours | ⬜ Pending |
| Wait for active transfers before shutdown | SimpleTransferEngine | 2 hours | ⬜ Pending |
| Add graceful shutdown tests | All modules | 2 hours | ⬜ Pending |

---

## Stage 3: Agent & Threading Fixes (2-3 days each)

Fix critical blocking I/O and thread management issues.

### T3.1: Vert.x WebClient Migration (Agent) ✅ COMPLETE

**Goal:** Fix blocking I/O in agent event loop.

**Effort:** 3-4 days  
**Priority:** 🔴 CRITICAL  
**Dependencies:** None  
**Status:** ✅ COMPLETE (verified 2025-01-30)

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
- [ ] **Stage 1**: All documentation and configuration tasks (T1.1, T1.2, T1.3)
- [ ] **Stage 2**: T2.1 (Health Endpoints), T2.2 (Error Responses)

### Week 3-4: Stability & Agent Fixes
- [ ] **Stage 2**: T2.3 (Graceful Shutdown)
- [x] **Stage 3**: T3.1 (Vert.x WebClient Migration) - ✅ COMPLETE
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
| 2 | Core API | T2.1 Health endpoints |
| 3 | Stability | T2.3 Graceful shutdown |
| 4 | Agent | ~~T3.1 Vert.x WebClient~~ ✅ COMPLETE |
| 5 | Threading | T3.2 Bounded thread pools |
| 6 | Protocols | T4.1 NFS adapter |
| 7-8 | Persistence | ~~T5.1 Raft WAL~~ ✅ COMPLETE |
| 9-10 | Compaction | T5.2 Snapshots (scheduling/truncation remain) |
| 11-12 | Raft | T5.3 InstallSnapshot, T5.4 Protobuf |
| 15+ | Security + Routes | T6.1-T6.6 (Security), T6.7 (Routes) |

### Summary by Priority

| Priority | Total Tasks | Completed | Remaining |
|----------|-------------|-----------|-----------|
| 🔴 CRITICAL | 4 | 2 (T5.1, T3.1) | 2 |
| 🟡 HIGH | 7 | 0 | 7 |
| 🟠 MEDIUM (Security - Deferred) | 11 | 0 | 11 |
| **TOTAL** | **22** | **1** | **21** |

> **Notes:**
> - T5.1 (Raft WAL) marked COMPLETE via raftlog-core library
> - T5.2 takeSnapshot/restoreSnapshot methods are COMPLETE; scheduling remains
> - T6.7 changed from "decision point" to implementation task (routes are core)
> - T1.1 reduced scope (routes are not "future" features)

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
**Last Updated**: 2026-02-02  
**Version**: 1.4  
**Owner**: Development Team
