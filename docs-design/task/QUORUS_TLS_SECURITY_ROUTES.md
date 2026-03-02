# Quorus Stage 6: Security & Enterprise Features

**Version:** 1.0  
**Date:** March 2, 2026  
**Status:** NOT STARTED  
**Dependencies:** Core infrastructure complete

---

## Overview

Stage 6 contains 8 tasks covering security, authentication, and route architecture. These are implemented **after** core functionality is stable and well-tested.

> **Rationale:** Security layers are easier to add to a stable, well-tested core. Adding authentication/TLS to buggy infrastructure creates debugging complexity.

### Task Summary

| Task | Name | Effort | Priority | Status |
|------|------|--------|----------|--------|
| T6.1 | API Key Authentication | 1-2 days | 🟠 MEDIUM | ⬜ Pending |
| T6.2 | TLS for HTTP API | 2 days | 🟠 MEDIUM | ⬜ Pending |
| T6.3 | TLS for gRPC Raft | 2-3 days | 🟠 MEDIUM | ⬜ Pending |
| T6.4 | TenantSecurityService | 5 days | 🟠 MEDIUM | ⬜ Pending |
| T6.5 | Request Rate Limiting | 2 days | 🟠 MEDIUM | ⬜ Pending |
| T6.6 | OAuth2/JWT Authentication | 3 weeks | 🟠 MEDIUM | ⬜ Pending |
| T6.7 | Route Architecture | 4-6 weeks | 🟡 HIGH | ⬜ Pending |
| T6.8 | TenantAwareStorageService | 2 weeks | 🟠 MEDIUM | ⬜ Pending |

**Total Estimated Effort:** 10-14 weeks

---

## Dependencies Graph

```
                          ┌──────────────────────────────────────────┐
                          │  Core Infrastructure Complete             │
                          └──────────────────────────────────────────┘
                                              │
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

## T6.1: API Key Authentication (Basic)

**Goal:** Simple API authentication to protect endpoints.

**Effort:** 1-2 days  
**Priority:** 🟠 MEDIUM  
**Dependencies:** T5.1-T5.4 (core stability)

### Tasks

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Create `ApiKeyAuthHandler` | quorus-controller | 2 hours | ⬜ Pending |
| Add API key configuration property | quorus-controller | 30 min | ⬜ Pending |
| Apply handler to protected routes | HttpApiServer | 1 hour | ⬜ Pending |
| Add tests for auth handler | quorus-controller/test | 2 hours | ⬜ Pending |
| Document API key usage | docs/ | 30 min | ⬜ Pending |

### Acceptance Criteria

- [ ] All `/api/v1/*` endpoints require `X-API-Key` header
- [ ] `/health` and `/metrics` remain public
- [ ] Invalid/missing key returns 401 Unauthorized
- [ ] API keys loaded from configuration/environment

### Implementation Notes

```java
// ApiKeyAuthHandler.java
public class ApiKeyAuthHandler implements Handler<RoutingContext> {
    private final Set<String> validApiKeys;
    
    @Override
    public void handle(RoutingContext ctx) {
        String apiKey = ctx.request().getHeader("X-API-Key");
        if (apiKey == null || !validApiKeys.contains(apiKey)) {
            ctx.response()
                .setStatusCode(401)
                .putHeader("Content-Type", "application/json")
                .end(Json.encode(ErrorResponse.unauthorized("Invalid or missing API key")));
            return;
        }
        ctx.next();
    }
}
```

### Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `QUORUS_API_KEYS` | Comma-separated list of valid API keys | (required) |
| `QUORUS_AUTH_ENABLED` | Enable/disable authentication | `true` |

---

## T6.2: TLS for HTTP API

**Goal:** Encrypted HTTP communication.

**Effort:** 2 days  
**Priority:** 🟠 MEDIUM  
**Dependencies:** T6.1 (API Key Auth)

### Tasks

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Add TLS configuration properties | AppConfig | 1 hour | ⬜ Pending |
| Configure Vert.x HttpServerOptions for TLS | HttpApiServer | 2 hours | ⬜ Pending |
| Generate dev/test certificates | scripts/ | 1 hour | ⬜ Pending |
| Update Docker compose with TLS | docker/compose/ | 2 hours | ⬜ Pending |
| Add TLS health check | HttpApiServer | 1 hour | ⬜ Pending |
| Document TLS setup | docs/ | 2 hours | ⬜ Pending |

### Acceptance Criteria

- [ ] HTTPS endpoint available on configured port
- [ ] HTTP can be disabled or redirected
- [ ] Certificate rotation without restart (optional)
- [ ] Health endpoint accessible via HTTPS

### Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `QUORUS_TLS_ENABLED` | Enable TLS for HTTP API | `false` |
| `QUORUS_TLS_CERT_PATH` | Path to certificate file (PEM) | - |
| `QUORUS_TLS_KEY_PATH` | Path to private key file (PEM) | - |
| `QUORUS_TLS_KEY_PASSWORD` | Private key password (if encrypted) | - |

### Implementation Notes

```java
// HttpApiServer.java - TLS configuration
HttpServerOptions options = new HttpServerOptions()
    .setPort(config.getHttpPort())
    .setHost(config.getHttpHost());

if (config.isTlsEnabled()) {
    options.setSsl(true)
           .setPemKeyCertOptions(new PemKeyCertOptions()
               .setCertPath(config.getTlsCertPath())
               .setKeyPath(config.getTlsKeyPath()));
}
```

---

## T6.3: TLS for gRPC Raft

**Goal:** Encrypted Raft cluster communication.

**Effort:** 2-3 days  
**Priority:** 🟠 MEDIUM  
**Dependencies:** T6.2 (TLS HTTP)

### Tasks

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Add TLS to GrpcRaftServer | quorus-controller | 3 hours | ⬜ Pending |
| Add TLS to GrpcRaftTransport | quorus-controller | 3 hours | ⬜ Pending |
| Configure mutual TLS (mTLS) | quorus-controller | 4 hours | ⬜ Pending |
| Update cluster configuration | docker/compose/ | 2 hours | ⬜ Pending |
| Add TLS validation tests | quorus-controller/test | 4 hours | ⬜ Pending |

### Acceptance Criteria

- [ ] All Raft gRPC communication encrypted
- [ ] mTLS validates both client and server certificates
- [ ] Cluster refuses connections from untrusted nodes
- [ ] Certificate rotation documented

### Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `QUORUS_RAFT_TLS_ENABLED` | Enable TLS for Raft gRPC | `false` |
| `QUORUS_RAFT_TLS_CERT_PATH` | Path to node certificate | - |
| `QUORUS_RAFT_TLS_KEY_PATH` | Path to node private key | - |
| `QUORUS_RAFT_TLS_CA_PATH` | Path to CA certificate for mTLS | - |
| `QUORUS_RAFT_TLS_MTLS_ENABLED` | Require client certificate | `true` |

---

## T6.4: TenantSecurityService

**Goal:** Tenant-level access control foundation.

**Effort:** 5 days  
**Priority:** 🟠 MEDIUM  
**Dependencies:** T6.1 (API Key Auth)

### Tasks

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Create TenantSecurityService interface | quorus-tenant | 2 hours | ⬜ Pending |
| Implement SimpleTenantSecurityService | quorus-tenant | 8 hours | ⬜ Pending |
| Add tenant isolation checks | quorus-tenant | 4 hours | ⬜ Pending |
| Integrate with HTTP handlers | quorus-controller | 4 hours | ⬜ Pending |
| Add permission model | quorus-tenant | 4 hours | ⬜ Pending |
| Create security tests | quorus-tenant/test | 8 hours | ⬜ Pending |

### Acceptance Criteria

- [ ] API keys associated with tenants
- [ ] Requests scoped to tenant data only
- [ ] Cross-tenant access prevented
- [ ] Permission model enforced (read/write/admin)

### Interface Design

```java
public interface TenantSecurityService {
    /** Resolve tenant from API key */
    Future<Optional<TenantContext>> resolveFromApiKey(String apiKey);
    
    /** Check if tenant has permission for operation */
    Future<Boolean> hasPermission(String tenantId, String resource, Permission permission);
    
    /** Validate tenant can access specific resource */
    Future<Boolean> canAccess(String tenantId, String resourceId);
}

public enum Permission {
    READ, WRITE, DELETE, ADMIN
}

public record TenantContext(String tenantId, String apiKeyId, Set<Permission> permissions) {}
```

---

## T6.5: Request Rate Limiting

**Goal:** Protect API from abuse.

**Effort:** 2 days  
**Priority:** 🟠 MEDIUM  
**Dependencies:** T6.1 (API Key for per-client limits)

### Tasks

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Implement RateLimitHandler | quorus-controller | 4 hours | ⬜ Pending |
| Add rate limit configuration | AppConfig | 1 hour | ⬜ Pending |
| Apply to API endpoints | HttpApiServer | 2 hours | ⬜ Pending |
| Add 429 response handling | HttpApiServer | 1 hour | ⬜ Pending |
| Add rate limit tests | quorus-controller/test | 3 hours | ⬜ Pending |

### Acceptance Criteria

- [ ] Per-client rate limits (by API key or IP)
- [ ] 429 Too Many Requests with `Retry-After` header
- [ ] Configurable limits per endpoint group
- [ ] Rate limit metrics exposed

### Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `QUORUS_RATE_LIMIT_ENABLED` | Enable rate limiting | `true` |
| `QUORUS_RATE_LIMIT_REQUESTS_PER_MINUTE` | Default requests per minute | `1000` |
| `QUORUS_RATE_LIMIT_BURST` | Burst allowance | `100` |

### Implementation Notes

Use Vert.x `RateLimiter` or sliding window counter with `ConcurrentHashMap<String, AtomicLong>`.

---

## T6.6: OAuth2/JWT Authentication

**Goal:** Enterprise authentication integration.

**Effort:** 3 weeks  
**Priority:** 🟠 MEDIUM  
**Dependencies:** T6.2 (TLS), T6.1 (API Key as fallback)

### Tasks

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Add JWT validation library | pom.xml | 2 hours | ⬜ Pending |
| Implement JwtAuthHandler | quorus-controller | 3 days | ⬜ Pending |
| Add JWKS endpoint support | quorus-controller | 2 days | ⬜ Pending |
| Implement token refresh handling | quorus-controller | 2 days | ⬜ Pending |
| Add OAuth2 provider configuration | AppConfig | 1 day | ⬜ Pending |
| Create auth integration tests | quorus-controller/test | 3 days | ⬜ Pending |
| Document OAuth2 setup | docs/ | 2 days | ⬜ Pending |

### Acceptance Criteria

- [ ] JWT Bearer tokens accepted in `Authorization` header
- [ ] JWKS endpoint for key rotation support
- [ ] Token claims mapped to tenant context
- [ ] Expired/invalid tokens rejected with 401
- [ ] API key fallback when JWT not provided

### Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `QUORUS_OAUTH2_ENABLED` | Enable OAuth2/JWT auth | `false` |
| `QUORUS_OAUTH2_ISSUER` | Expected token issuer | - |
| `QUORUS_OAUTH2_JWKS_URI` | JWKS endpoint URL | - |
| `QUORUS_OAUTH2_AUDIENCE` | Expected audience claim | - |
| `QUORUS_OAUTH2_TENANT_CLAIM` | Claim containing tenant ID | `tenant_id` |

### Implementation Notes

Use `vertx-auth-jwt` for JWT validation:

```xml
<dependency>
    <groupId>io.vertx</groupId>
    <artifactId>vertx-auth-jwt</artifactId>
</dependency>
```

---

## T6.7: Route Architecture Implementation

**Goal:** Implement the route-based transfer system as specified in QUORUS_SYSTEM_DESIGN.md.

**Effort:** 4-6 weeks  
**Priority:** 🟡 HIGH (Core feature, not optional)  
**Dependencies:** T5.1-T5.3 (Raft stability)

> **Note:** Routes are **core** to Quorus architecture, not a decision point.
> The system is explicitly designed as a "route-based distributed file transfer system."
> See [QUORUS_SYSTEM_DESIGN.md](../design/QUORUS_SYSTEM_DESIGN.md) for full specification.

### Routes vs Workflows

| Aspect | Routes | Workflows |
|--------|--------|-----------|
| Invocation | Event-driven, continuous | Manual/scheduled |
| Use Case | Real-time monitoring, CDC | Batch processing, complex pipelines |
| Trigger | File events, time, size | API call, schedule |
| Complexity | Single source→destination | Multi-step, dependencies |

### Implementation Tasks

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

### Route Trigger Types

| Trigger | Description | Configuration |
|---------|-------------|---------------|
| EVENT | File system events (CREATE, MODIFY, DELETE) | patterns, event types |
| TIME | Cron-based scheduling | cron expression, timezone |
| INTERVAL | Periodic execution | duration (e.g., "15m") |
| BATCH | File count threshold | minFiles, maxWait |
| SIZE | Cumulative size threshold | sizeThreshold, maxWait |
| COMPOSITE | AND/OR of multiple triggers | operator, triggers[] |

### Route Configuration Model

```java
public record RouteConfiguration(
    String routeId,
    String name,
    String description,
    String sourceAgentId,
    String destinationAgentId,
    String sourcePattern,        // e.g., "/data/inbox/*.csv"
    String destinationPath,      // e.g., "/data/processed/"
    TriggerConfig trigger,
    TransferOptions options,
    boolean enabled
) {}

public sealed interface TriggerConfig {
    record EventTrigger(Set<FileEventType> eventTypes, String pattern) implements TriggerConfig {}
    record TimeTrigger(String cronExpression, ZoneId timezone) implements TriggerConfig {}
    record IntervalTrigger(Duration interval) implements TriggerConfig {}
    record BatchTrigger(int minFiles, Duration maxWait) implements TriggerConfig {}
    record SizeTrigger(long sizeThresholdBytes, Duration maxWait) implements TriggerConfig {}
    record CompositeTrigger(CompositeOperator operator, List<TriggerConfig> triggers) implements TriggerConfig {}
}

public enum CompositeOperator { AND, OR }
public enum FileEventType { CREATE, MODIFY, DELETE }
```

### Route Status Lifecycle

```
CONFIGURED ──► ACTIVE ──► TRIGGERED ──► ACTIVE
     │            │           │
     │            ▼           │
     │         PAUSED ◄───────┘
     │            │
     └────────► DISABLED
```

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/routes` | Create route |
| GET | `/api/v1/routes` | List routes |
| GET | `/api/v1/routes/{id}` | Get route |
| PUT | `/api/v1/routes/{id}` | Update route |
| DELETE | `/api/v1/routes/{id}` | Delete route |
| POST | `/api/v1/routes/{id}/activate` | Activate route |
| POST | `/api/v1/routes/{id}/pause` | Pause route |
| POST | `/api/v1/routes/{id}/resume` | Resume route |

### Raft Integration

Routes replicated via `RouteCommand`:
- `RouteCommand.Create` — new route
- `RouteCommand.Update` — configuration change
- `RouteCommand.Delete` — remove route
- `RouteCommand.Suspend` — pause with reason
- `RouteCommand.Resume` — reactivate
- `RouteCommand.UpdateStatus` — status transitions

Trigger evaluation runs **only on the leader** to avoid duplicate triggers.

---

## T6.8: TenantAwareStorageService

**Goal:** Storage isolation per tenant.

**Effort:** 2 weeks  
**Priority:** 🟠 MEDIUM  
**Dependencies:** T6.4 (TenantSecurityService)

### Tasks

| Task | Module | Effort | Status |
|------|--------|--------|--------|
| Design storage isolation strategy | docs-design/ | 1 day | ⬜ Pending |
| Create TenantAwareStorageService interface | quorus-tenant | 2 hours | ⬜ Pending |
| Implement path-based isolation | quorus-tenant | 3 days | ⬜ Pending |
| Add storage quota enforcement | quorus-tenant | 2 days | ⬜ Pending |
| Integrate with TransferEngine | quorus-core | 2 days | ⬜ Pending |
| Create isolation tests | quorus-tenant/test | 3 days | ⬜ Pending |

### Acceptance Criteria

- [ ] Each tenant's files isolated to tenant-specific paths
- [ ] Path traversal attacks prevented
- [ ] Storage quotas per tenant enforced
- [ ] Quota exceeded returns 507 Insufficient Storage

### Interface Design

```java
public interface TenantAwareStorageService {
    /** Resolve tenant-scoped path (validates no path traversal) */
    Future<Path> resolvePath(String tenantId, String relativePath);
    
    /** Check if tenant has storage quota available */
    Future<Boolean> hasQuotaAvailable(String tenantId, long requiredBytes);
    
    /** Record storage usage after transfer */
    Future<Void> recordUsage(String tenantId, long bytesUsed);
    
    /** Get tenant storage stats */
    Future<StorageStats> getStats(String tenantId);
}

public record StorageStats(
    long usedBytes,
    long quotaBytes,
    long fileCount
) {}
```

---

## Implementation Schedule

### Recommended Order

| Week | Tasks | Notes |
|------|-------|-------|
| 1-2 | T6.1 (API Key) | Foundation for all auth |
| 2-3 | T6.2 (TLS HTTP) | Secure communication |
| 3-4 | T6.3 (TLS gRPC) | Cluster security |
| 4-5 | T6.5 (Rate Limiting) | Protection layer |
| 5-7 | T6.4 (TenantSecurity) | Multi-tenant foundation |
| 7-9 | T6.8 (TenantStorage) | Storage isolation |
| 3-9 | T6.7 (Routes) | **Parallel track** — core feature |
| 10-12 | T6.6 (OAuth2) | Enterprise auth (can be deferred) |

### Parallel Tracks

**Track A (Security):** T6.1 → T6.2 → T6.3 → T6.5  
**Track B (Multi-tenancy):** T6.4 → T6.8  
**Track C (Routes):** T6.7 (independent after T5.3)

---

## Risk Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Route implementation scope creep | Delay | Break into trigger milestones; each trigger is independent deliverable |
| TLS certificate management | Operational complexity | Document rotation procedure; consider cert-manager integration |
| OAuth2 provider variations | Integration issues | Test with multiple providers (Keycloak, Auth0, Azure AD) |
| Rate limiting performance | Latency | Use efficient data structures (sliding window); consider distributed rate limiting for multi-controller |
| Tenant isolation bugs | Security breach | Extensive security tests; path traversal fuzzing |

---
