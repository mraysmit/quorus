<img src="quorus-logo.png" alt="Quorus" width="120"/>

# Quorus HTTP API Reference

**Version:** 3.6  
**Date:** 2026-09-04  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Status:** Current implementation reference  
**Scope:** Active controller HTTP surface  
**Implementation:** `quorus-controller/src/main/java/dev/mars/quorus/controller/http/HttpApiServer.java`

This document reflects the endpoints currently registered by the embedded controller HTTP server.

The complete production REST contract is defined in [QUORUS_REST_API_SPECIFICATION.md](QUORUS_REST_API_SPECIFICATION.md). The canonical security, consistency, and release requirements are defined in [QUORUS_ARCHITECTURE_SPECIFICATION.md](QUORUS_ARCHITECTURE_SPECIFICATION.md). This reference deliberately remains limited to the active HTTP surface: an endpoint described only in the REST API specification is required or planned, not implicitly implemented.

## Base URL

`https://{controller-host}:8080`

Plaintext is available only to legacy development/test constructors. The packaged production profile requires TLS 1.3 mutual authentication.

## Request Model

- Content type: `application/json` for JSON request bodies
- All write requests are subject to leader guarding
- A follower rejects a write with `503 NOT_LEADER`; the implementation does not issue an HTTP redirect
- All endpoints except liveness, readiness, and the OpenAPI document require a verified identity
- Human and integration assertions are accepted only from configured trusted-gateway certificate subjects
- Direct agents and workloads are resolved from exact client-certificate subject bindings
- Production configuration cannot disable authentication, HTTP TLS, or Raft mutual TLS

## Tenant Isolation

Every authenticated identity has one tenant and environment. Agent registration and transfer creation derive tenant from that identity. A supplied `tenantId` is optional narrowing input and must match:

- An authenticated registration or transfer may omit `tenantId`; Quorus supplies the authenticated tenant
- A supplied tenant that differs from the authenticated tenant → `403 Forbidden`
- Heartbeat carrying a `tenantId` that does not match the registered agent's tenant → `403 Forbidden`
- Job status update from an agent for a job belonging to a different tenant → `403 Forbidden`
- Agent, assignment, and route collections are filtered to the authenticated tenant
- Transfer, assignment, route, heartbeat, polling, and status items enforce tenant ownership
- An agent certificate may register, heartbeat, poll, or report only its bound `agentId`

Uniform state-machine enforcement remains subject to `ARCH-06`; the HTTP boundary must not be treated as the only invariant layer. See [Quorus Security Deployment Guide](QUORUS_SECURITY_DEPLOYMENT_GUIDE.md).

## Security Endpoints

### `GET /api/v1/security/me`

Returns the effective principal, identity type, tenant, environment, roles, scopes, assertion expiry, and privileged-elevation expiry.

### `GET /api/v1/security/authorization/explain`

Evaluates query parameters `method`, `path`, `tenantId`, `environment`, and `classification` without performing the proposed operation.

### `POST /api/v1/security/authorization/check`

Evaluates the same fields in a JSON request body through the active policy engine. The result contains `allowed`, a stable `decisionCode`, `reason`, `requiredScope`, and the effective identity.

### `GET /api/v1/security/trust`

Returns the active runtime trust-policy version and load time, revoked-certificate count, and the authenticated caller certificate's subject, expiry, seconds remaining, warning threshold, and `OK`, `WARNING`, or `EXPIRED` alert state. It exposes metadata only, never certificates, trust anchors, private material, or the revoked serial set.

### `PUT /api/v1/security/trust/revocations`

Atomically replaces the runtime revoked-certificate serial set and advances its version. The JSON body requires `trustBundleVersion` and the complete `revokedCertificateSerials` array. This route requires the `security:trust:write` scope and an active privileged elevation. The change applies to subsequent controller HTTP requests and Raft RPCs, including established TLS connections, and emits a `SECURITY_CONFIGURATION_CHANGE` audit event.

Authentication, authorization, certificate-lifecycle, configuration-change, and protected completion events are written to the separately configured operational and retained hash-chained audit files. Certificate and assertion configuration is documented in the [Security Deployment Guide](QUORUS_SECURITY_DEPLOYMENT_GUIDE.md).

## Infrastructure Endpoints

### `GET /health/live`

Liveness probe. Returns process-level health.

### `GET /health/ready`

Readiness probe. Uses Raft readiness to determine whether the node is ready to serve traffic.

### `GET /health`

Full health response including controller and Raft details.

### `GET /status`

Controller status summary endpoint.

### `GET /raft/status`

Detailed Raft status endpoint.

### `GET /api/v1/info`

Controller info endpoint. Exposes controller version and node-level information.

### `GET /metrics`

Prometheus-format metrics.

## Agent Endpoints

### `POST /api/v1/agents/register`

Registers an agent through a Raft-replicated write. Returns `201 Created` on success.

**Required fields:**

| Field | Type | Description |
|-------|------|-------------|
| `agentId` | string | Unique agent identifier |
| `hostname` | string | Agent hostname |
| `address` | string | Agent IP address |
| `tenantId` | string | Optional narrowing value; must match the authenticated tenant |

**Optional fields:**

| Field | Type | Description |
|-------|------|-------------|
| `port` | integer | Agent port (default `0`) |
| `region` | string | Geographic region label |
| `datacenter` | string | Datacenter label |
| `version` | string | Agent software version |
| `capabilities` | object | Agent capabilities (protocols, max concurrent jobs, etc.) |
| `metadata` | object | Arbitrary key-value metadata |

**Example:**

```json
{
  "agentId": "agent-nyc-01",
  "hostname": "agent-nyc-01.example.com",
  "address": "10.0.1.5",
  "port": 0,
  "tenantId": "payments-operations",
  "region": "us-east",
  "datacenter": "nyc3"
}
```

**Response (201):**

```json
{
  "success": true,
  "agentId": "agent-nyc-01"
}
```

### `POST /api/v1/agents/heartbeat`

Updates agent health and capacity state. Returns `200` on success.

**Required fields:**

| Field | Type | Description |
|-------|------|-------------|
| `agentId` | string | ID of the registered agent |

**Optional fields:**

| Field | Type | Description |
|-------|------|-------------|
| `tenantId` | string | If provided, must match the agent's registered tenant or `403` is returned |
| `status` | string | Agent status (`ACTIVE`, `IDLE`, `BUSY`, etc.) |
| `sequenceNumber` | integer | Monotonically increasing counter; echoed back in the response |

**Example:**

```json
{
  "agentId": "agent-nyc-01",
  "tenantId": "payments-operations",
  "status": "ACTIVE",
  "sequenceNumber": 42
}
```

**Response (200):**

```json
{
  "success": true,
  "agentId": "agent-nyc-01",
  "status": "ACTIVE",
  "lastHeartbeat": "2026-03-17T10:00:00Z",
  "acknowledgedSequenceNumber": 42
}
```

### `GET /api/v1/agents`

Lists agents from controller state.

### `GET /api/v1/agents/:agentId/jobs`

Returns jobs assigned to a specific agent. Only returns jobs whose `tenantId` matches the agent's registered tenant. An active assignment includes the authoritative `attemptId`, `fencingGeneration`, `leaseExpiresAt`, and `lastReportSequence`; the agent must use these values when reporting lifecycle changes.

## Transfer Endpoints

### `POST /api/v1/transfers`

Creates a transfer job. Returns `201 Created` on success.

**Required fields:**

| Field | Type | Description |
|-------|------|-------------|
| `tenantId` | string | Optional narrowing value; must match the authenticated tenant and executing agent |
| `jobId` | string | Unique job identifier |
| `serviceConnectionId` | string | Production service alias |
| `remotePath` | string | Absolute remote path within the alias policy |
| `agentPool` | string | Approved executing agent pool |

Governed downloads additionally require `destinationPath`. Governed uploads require `direction: "UPLOAD"` and a local `file:` `sourceUri`. Development-profile direct transfers require a credential-free `sourceUri` and `destinationPath`.

**Optional fields:**

| Field | Type | Description |
|-------|------|-------------|
| `direction` | string | `DOWNLOAD` (default) or `UPLOAD` |
| `totalBytes` | long | Expected transfer size in bytes |
| `description` | string | Human-readable description |
| `businessService` | string | Business service responsible for the transfer |
| `owner` | string | Operational owner responsible for intervention |
| `criticality` | string | `LOW`, `STANDARD`, `HIGH`, or `CRITICAL` |
| `environment` | string | Runtime environment, such as `PRODUCTION` |
| `processingDate` | date | Business processing date |
| `expectedStartAt` | timestamp | Expected transfer start |
| `requiredCompletionAt` | timestamp | Operational completion deadline |
| `runbookUrl` | URI | Operator runbook for this transfer |

**Example:**

```json
{
  "tenantId": "payments-operations",
  "jobId": "settlement-2026-09-03-001",
  "serviceConnectionId": "clearing-sftp",
  "remotePath": "/outbound/settlement-2026-09-03.dat",
  "agentPool": "payments-agents",
  "destinationPath": "C:/quorus/settlement/settlement-2026-09-03.dat",
  "totalBytes": 102400,
  "description": "Time-critical settlement file"
}
```

**Response (201):**

```json
{
  "success": true,
  "jobId": "settlement-2026-09-03-001"
}
```

**Current security boundary:** production submissions require `serviceConnectionId`, `remotePath`, and `agentPool`; the controller resolves a credential-free endpoint only after tenant, path, direction, pool, host, CIDR, port, DNS, trust, secret-reference, and status checks. The governed model preserves direction: downloads bind the alias to the remote source, while uploads bind it to the remote destination and require a local `file:` source. Credential-free direct transfer remains a development-profile compatibility input. URI user-info always returns `400 VALIDATION_ERROR` before request mapping or Raft submission.

## Governed Service Connectivity Endpoints

The active API provides full tenant-scoped CRUD at `/api/v1/service-connections` and `/api/v1/service-connections/:serviceConnectionId`, full opaque-reference CRUD at `/api/v1/secret-references` and `/api/v1/secret-references/:secretReferenceId`, policy validation at `POST /api/v1/service-connections/:serviceConnectionId/validate`, and redacted lifecycle history at `GET /api/v1/security-events`.

Service connections expose protocol, credential-free endpoint, service identity and authentication type, owner, environment, classification, network zone, path/direction/pool constraints, trust rules, egress allowlists, status, version, and timestamps. Authentication is constrained by protocol: SFTP accepts `PASSWORD` or `SSH_PRIVATE_KEY`, HTTPS accepts `BASIC` or `BEARER`, FTPS accepts `PASSWORD`, and governed SMB/NFS accepts `KERBEROS`. TLS `approvedCaIds` are SHA-256 certificate fingerprints in `SHA256:<base64>` form; they restrict a normally valid PKIX chain, while `tlsPeerFingerprints` optionally pin the leaf certificate. Secret-reference requests accept only provider/path/key/version and lifecycle metadata. Fields such as `secretValue`, `password`, `token`, `privateKey`, and nested equivalents are rejected and never enter Raft state.

The executing agent receives the exact policy version and digest, controller-resolved address pins, redacted service connection, and opaque reference. It repeats authorization, including its deployment-configured pool and network zone, before contacting Vault KV v2. Upload sources and download destinations must remain under the agent's configured local roots after canonical and symbolic-link resolution. HTTPS, FTPS, and SFTP sockets bind to an approved resolved address while retaining the original service hostname for TLS or SSH identity verification. Runtime credentials are memory-only and wiped after completion.

`POST /api/v1/service-connections/:serviceConnectionId/validate` is policy-only by default. With `probeNetwork: true`, it additionally performs a bounded TCP route probe to a controller-approved address and returns `ROUTE_VERIFIED`; it does not retrieve a secret, authenticate to the service, or claim application-level readiness. Submission emits `SERVICE_CONNECTION_AUTHORIZED`. `SERVICE_CONNECTION_LAST_USED` is emitted only after the executing agent has passed its policy checks and resolved secret authority. If an active secret reference is past `expiresAt`, transfer authorization durably marks it `EXPIRED`, records the expiry event, and returns `409`.

See [Quorus Service Connection Operations Runbook](QUORUS_SERVICE_CONNECTION_OPERATIONS_RUNBOOK.md).

### `GET /api/v1/transfers/:jobId`

Returns transfer job details.

### `GET /api/v1/transfers/:jobId/progress`

Returns the current tenant-checked operator progress view. The response includes observation and actual last-progress times, bytes and known-size percentage semantics, telemetry freshness, active attempt and agent, retry count, operational ownership and deadline context, time remaining, and `ON_TRACK`, `AT_RISK`, `LATE`, `STALLED`, `DEGRADED`, or `UNKNOWN` condition independently of transfer lifecycle state. A transfer with no increasing-byte report returns `UNKNOWN` telemetry without an invented last-progress timestamp. A stalled active transfer includes stable `conditionSince` and `stallDurationSeconds` values derived from its last real progress and the governed threshold. The response discloses `freshnessWindowSeconds`, `stallWindowSeconds`, and `telemetryPolicySource`; controller properties `quorus.telemetry.transfer.fresh-window-ms` and `quorus.telemetry.transfer.stall-window-ms` govern the effective windows. Average throughput and estimated completion are returned only when an active attempt has enough elapsed observation time; confidence remains explicit.

**Current boundary:** the windows are configurable and the missing, stale, and active-transfer stalled cases are externally verified. Durable sample history, calibrated rolling throughput and ETA, deadline-risk policy, collection queries, timelines, alerts, and streaming remain Phase 3 work.

### `GET /api/v1/transfers/:jobId/events`

Returns the tenant-checked ordered transfer event ledger. The current implementation records `TRANSFER_SUBMITTED`, `TRANSFER_ASSIGNED`, `TRANSFER_ACCEPTED`, `TRANSFER_STARTED`, and `TRANSFER_PROGRESS` with deterministic per-transfer sequence and event identity. Assignment and lifecycle events include attempt and agent identity; progress events also include bytes, total size, and agent report sequence. The ledger is part of controller snapshots, and assignment ordering has been verified across snapshot reset and restore. Remaining terminal and exceptional lifecycle events, pagination, replay, retention gaps, and streaming remain Phase 3 work.

### `GET /api/v1/transfers/:jobId/attempts`

Returns immutable execution-attempt history for the transfer, ordered by `attemptNumber`. The response includes `activeAttemptId` while an authoritative attempt fence is active. The authenticated caller must have `transfers:read` and belong to the transfer tenant.

### `GET /api/v1/transfers/:jobId/attempts/:attemptId`

Returns one immutable execution-attempt record, including its agent, lifecycle status, classified outcome, lease expiry, fencing generation, report sequence, progress, and timestamps. An attempt ID belonging to another transfer is treated as not found. The authenticated caller must have `transfers:read` and belong to the attempt tenant.

### `DELETE /api/v1/transfers/:jobId`

Deletes a transfer job.

## Job Status Endpoint

### `POST /api/v1/jobs/:jobId/status`

Updates status for an existing transfer job. Only the agent that owns the assignment — and belongs to the same tenant as the job — may update status. When an active attempt exists, the report is guarded by the attempt identity, expected state, fencing generation, lease, and report sequence.

**Required fields:**

| Field | Type | Description |
|-------|------|-------------|
| `agentId` | string | ID of the agent submitting the update |
| `status` | string | New assignment status (e.g., `IN_PROGRESS`, `COMPLETED`, `FAILED`) |
| `attemptId` | string | Active attempt ID supplied by the polling response |
| `expectedState` | string | Attempt state the agent expects before this report is applied |
| `fencingGeneration` | long | Active fencing generation supplied by the polling response |
| `reportSequence` | long | Next monotonically increasing sequence for this attempt |

**Optional fields:**

| Field | Type | Description |
|-------|------|-------------|
| `bytesTransferred` | long | Running byte count for progress tracking |
| `errorMessage` | string | Redacted failure reason; never include credentials or secret-provider payloads |

**Example:**

```json
{
  "agentId": "agent-nyc-01",
  "status": "COMPLETED",
  "attemptId": "9d1e2c1a-6f47-4f31-b125-2ab816fe8237",
  "expectedState": "IN_PROGRESS",
  "fencingGeneration": 1,
  "reportSequence": 3,
  "bytesTransferred": 102400
}
```

The controller returns `409 Conflict` for a stale fence, stale or gapped sequence, expired lease, expected-state mismatch, or illegal lifecycle transition. For attempt-aware reports, it validates and applies the attempt, assignment, transfer status, and transfer progress as one atomic replicated lifecycle command; rejection leaves every view unchanged. An exact retry of an already accepted report is idempotent and returns `200 OK`, including a terminal retry after the original response was lost, without advancing the report sequence or reopening the active fence.

Preparation rejection uses `status: FAILED`, `expectedState: ACCEPTED`, zero bytes,
and the next sequence (normally 2 after acceptance). It atomically fails the pending
transfer and accepted assignment/attempt without reporting `IN_PROGRESS` first.
After a lost response, resend the identical report rather than advancing its sequence.
The agent implements three-send bounded replay for transient errors; unresolved start
reports do not authorize file I/O. See [reconciliation guidance](QUORUS_SECURITY_DEPLOYMENT_GUIDE.md#12-pre-execution-failure-and-acknowledgement-reconciliation)
for exhausted retries and terminal acknowledgements.

## Assignment Endpoints

### `POST /api/v1/assignments`

Creates a job assignment and its first authoritative transfer attempt atomically in one replicated command. A successful `201 Created` response contains `assignmentId` and `attemptId`; the attempt receives the configured initial lease and fencing generation.

**Current boundary:** creation is atomic, but the specialized accept, reject, status, cancel, and remove assignment endpoints do not yet apply corresponding attempt and transfer lifecycle changes atomically. Uniform referential and tenant invariant enforcement in state application also remains subject to `ARCH-06`.

### `GET /api/v1/assignments`

Lists job assignments.

### `GET /api/v1/assignments/:assignmentId`

Returns a single assignment.

### `PUT /api/v1/assignments/:assignmentId/accept`

Marks an assignment as accepted.

### `PUT /api/v1/assignments/:assignmentId/reject`

Marks an assignment as rejected.

### `PUT /api/v1/assignments/:assignmentId/status`

Updates assignment status.

### `PUT /api/v1/assignments/:assignmentId/cancel`

Cancels an assignment.

### `DELETE /api/v1/assignments/:assignmentId`

Removes an assignment.

## Route Endpoints

Routes are part of the live controller API and are replicated through Raft.

### `POST /api/v1/routes`

Creates a route. Client-provided status is normalized to `CONFIGURED` on create.

### `GET /api/v1/routes`

Lists routes. Supports optional `status` filtering.

### `GET /api/v1/routes/:routeId`

Returns a route.

### `PUT /api/v1/routes/:routeId`

Updates a route definition.

### `DELETE /api/v1/routes/:routeId`

Deletes a route.

### `PUT /api/v1/routes/:routeId/suspend`

Transitions a route to `SUSPENDED` when the lifecycle allows it.

### `PUT /api/v1/routes/:routeId/resume`

Transitions a route back to `ACTIVE` when the lifecycle allows it.

## Route Data Model

The route payload maps to `RouteConfiguration` in `quorus-core`.

Core fields:

- `routeId`
- `name`
- `description`
- `sourceAgentId`
- `sourceLocation`
- `destinationAgentId`
- `destinationLocation`
- `trigger`
- `status`
- `options`
- `createdAt`
- `updatedAt`

Route lifecycle values currently include:

- `CONFIGURED`
- `ACTIVE`
- `TRIGGERED`
- `TRANSFERRING`
- `SUSPENDED`
- `DEGRADED`
- `FAILED`
- `DELETED`

## What Is Not in the Live API

The current `HttpApiServer` does **not** register a generic `/api/v1/commands` style endpoint. Earlier documentation that described a generic command endpoint was stale and has been removed from this reference.

## Current API Caveats

- Route CRUD and route lifecycle endpoints are live.
- This API surface does not by itself prove that a background route trigger evaluator is running. The controller startup path currently wires route persistence and route HTTP operations, but not a separate trigger execution service.
- Corporate identity issuance remains deployment-specific, but the production controller enforces the Phase 1 mTLS identity, tenant, scope, elevation, and audit boundary.

## Source of Truth

For endpoint registration, use:

- `quorus-controller/src/main/java/dev/mars/quorus/controller/http/HttpApiServer.java`

For route request handling, use:

- `quorus-controller/src/main/java/dev/mars/quorus/controller/http/handlers/RouteHandler.java`
