<img src="quorus-logo.png" alt="Quorus" width="120"/>

# Quorus REST API Specification

**Version:** 2.4  
**Date:** 2026-09-05  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Status:** Canonical and normative  
**Scope:** Complete REST control, operations, security, and administration interface

## 1. Purpose

This document defines the complete REST API contract required to operate Quorus. The API is the versioned control and operations interface for all system functionality, including critical transfer execution, transfer-process observability, workflows, routes, agents, service connectivity, tenant controls, security, audit, and controller administration.

The API MUST expose the state and controls required by technology operations teams to run time-sensitive file-transfer services without requiring database access, controller shell access, or knowledge of internal Java classes. It MUST NOT be limited to CRUD operations around transfer records.

The terms **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** are normative. Endpoint rows use these implementation states:

- **Current:** registered by the active controller HTTP server.
- **Required:** part of this production API contract but not necessarily implemented.
- **Planned:** dependent on a planned platform capability and unavailable until that capability is implemented.

The current endpoint reference is [QUORUS_API_REFERENCE.md](QUORUS_API_REFERENCE.md). Where that reference and this specification differ, it describes the implementation and this document defines the required contract. The architecture and security requirements in [QUORUS_ARCHITECTURE_SPECIFICATION.md](QUORUS_ARCHITECTURE_SPECIFICATION.md) also apply.

## 2. API Boundary and Principles

### 2.1 Boundary

The REST API controls and observes transfers; it does not carry file contents. Source and destination data moves between enterprise services and Quorus agents through the configured transfer protocol.

Direct in-process use of `quorus-core` and `quorus-workflow` remains a Java library contract. The REST API MUST provide the remotely operable controller equivalents for transfer and workflow execution, but it does not wrap arbitrary caller-local Java objects or callbacks.

The API MUST NOT:

- proxy file payloads through a controller;
- return secret values, private keys, passwords, or bearer tokens;
- accept credentials embedded in endpoint URIs;
- make a follower authoritative for a write;
- imply that creation of a route or workflow guarantees its execution;
- hide retries, reassignment, reconciliation, integrity failure, or publication failure behind one undifferentiated status.

### 2.2 Contract principles

1. All production functionality MUST have a supported API resource, action, or observable representation.
2. OpenAPI 3.1 MUST be the machine-readable source of truth for paths, schemas, security requirements, and error responses.
3. Operations that may outlive an HTTP request MUST be asynchronous and represented as operation resources.
4. Every state-changing request MUST be attributable to an authenticated principal and an immutable audit event.
5. Tenant scope MUST be derived from trusted identity and authorization policy, not accepted solely from a caller-provided `tenantId`.
6. Transfer status MUST be supported by an append-only event history and immutable execution-attempt records.
7. Secrets MUST be referenced by opaque secret identifiers and resolved only by an authorized execution component.
8. List resources MUST support bounded pagination and operationally useful filtering.
9. Concurrent mutation MUST use explicit preconditions.
10. The API MUST remain useful during incidents: failures must be structured, correlated, retriable when safe, and clear about leader and consistency state.

## 3. Protocol Conventions

### 3.1 Base path and representation

- Versioned resources use `/api/v1`.
- JSON requests use `Content-Type: application/json`.
- JSON responses use `application/json`; errors use `application/problem+json`.
- Times use UTC RFC 3339 timestamps with an explicit `Z` offset.
- Durations use ISO 8601 duration strings.
- Resource identifiers are opaque strings. Clients MUST NOT derive meaning from an identifier.
- Enum values are uppercase snake case.
- Unknown request fields are rejected by default. Forward-compatible extension objects MAY explicitly permit them.

### 3.2 Standard headers

| Header | Direction | Requirement |
|---|---|---|
| `Authorization` | Request | Required for every production `/api/v1` request except explicitly public health probes |
| `Idempotency-Key` | Request | Required for transfer submission and other safely repeatable creates/actions |
| `X-Correlation-ID` | Both | Accepted from trusted callers or generated; returned and recorded on all events |
| `traceparent` | Both | W3C trace context propagated through controller, agent, and supported protocol spans |
| `If-Match` | Request | Required for updates, deletes, and state-transition actions on mutable resources |
| `ETag` | Response | Returned for mutable resources and effective configuration |
| `Prefer: respond-async` | Request | Requests an operation resource for work that might otherwise complete synchronously |
| `Retry-After` | Response | Required for throttling, transient unavailability, and non-leader responses when known |
| `X-Quorus-Leader` | Response | Identifies the current leader endpoint when safely known; it is not a bearer credential |
| `X-Quorus-Read-Consistency` | Both | Reports the applied read mode and observed Raft commit index |

### 3.3 Resource representation

Every resource SHOULD contain:

```json
{
  "id": "resource-id",
  "tenantId": "tenant-id",
  "version": 7,
  "createdAt": "2026-09-01T09:00:00Z",
  "updatedAt": "2026-09-01T09:01:12Z",
  "links": {
    "self": "/api/v1/resources/resource-id"
  }
}
```

The `tenantId` field is omitted for global resources. Its presence does not grant access; it states the resource's enforced ownership scope.

### 3.4 Problem response

All errors MUST use a stable problem format:

```json
{
  "type": "https://quorus.dev/problems/precondition-failed",
  "title": "Resource version does not match",
  "status": 412,
  "code": "PRECONDITION_FAILED",
  "detail": "The transfer was changed after version 7.",
  "instance": "/api/v1/transfers/tr-123",
  "correlationId": "corr-456",
  "retryable": false,
  "errors": [
    {"field": "If-Match", "reason": "EXPECTED_VERSION_8"}
  ]
}
```

Error `code` values are stable API values. Human-readable text is not a machine contract.

### 3.5 Pagination, filtering, and sorting

Collection responses MUST use opaque cursor pagination:

```json
{
  "items": [],
  "page": {
    "limit": 100,
    "nextCursor": "opaque-cursor",
    "hasMore": false
  }
}
```

The default and maximum limits MUST be documented in OpenAPI. Collections SHOULD support `filter`, named query parameters for common operational fields, `sort`, and `fields`. Filters MUST be bounded by authorization and indexed for the supported retention window. Invalid or unbounded filters return `400`.

### 3.6 Idempotency and concurrency

- The server MUST persist an idempotency key with the authenticated principal, tenant, request fingerprint, response, and expiry.
- Reuse with the same fingerprint returns the original result. Reuse with a different fingerprint returns `409 IDEMPOTENCY_KEY_REUSED`.
- Mutable resources MUST return an `ETag`. Mutation without the required `If-Match` returns `428`; a stale value returns `412`.
- Agent assignment transitions additionally require `expectedState`, `attemptId`, and the active lease or fencing token.

The implemented agent status contract permits `ACCEPTED → FAILED` for preparation
rejections (authorization, secret resolution, or local-path validation). The same
committed lifecycle command sets the assignment and transfer to `FAILED`; a transfer
can therefore move directly from `PENDING` to `FAILED`, without an `IN_PROGRESS` report
or a transfer-start event. Cancellation, lease, fencing and sequence checks still apply.

For attempt-aware status reports, the agent retries transport failures, HTTP 408/429
and 5xx responses using the **identical payload and report sequence** (three sends
maximum, with 100 ms then 200 ms delays). Each send uses the configured
`quorus.agent.http.idle-timeout-ms` as its response deadline. Other non-2xx responses,
including 403 and 409, are not retried. An unresolved start acknowledgement never
authorizes file I/O or a guessed terminal transition. Legacy reports without attempt
identity are not automatically replayed. See the security/deployment guide for
operator reconciliation after the bounded retry budget is exhausted.

### 3.7 Asynchronous operations

Long-running validation, connection tests, exports, deployments, and administrative actions return `202 Accepted` with `Location: /api/v1/operations/{operationId}`.

An operation contains `type`, `status`, `requestedBy`, `requestedAt`, `startedAt`, `completedAt`, `percentComplete`, `resource`, `result`, and `problem`. Operation states are `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, and `CANCELLED`.

### 3.8 Leader routing and read consistency

Writes are accepted only by the current Raft leader. A follower returns `503 NOT_LEADER`, `Retry-After`, and `X-Quorus-Leader` when known. Controllers MUST NOT issue automatic HTTP redirects for authenticated write requests.

Reads support:

- `consistency=linearizable` for leader-confirmed state;
- `consistency=bounded&maxStaleness=PT5S` when the server can prove the bound;
- `consistency=local` only for explicitly documented diagnostics.

The response reports the applied consistency and commit index. The default for security, assignment, and administrative state is `linearizable`.

## 4. Authentication, Authorization, and Audit

### 4.1 Identity

Production deployments MUST authenticate human and workload callers through enterprise identity. Agent-only endpoints require the enrolled agent identity and mutual TLS. A gateway MAY validate external credentials, but Quorus MUST receive cryptographically protected identity, tenant, role, and correlation claims from a trusted boundary.

The Phase 1 controller implements this boundary with TLS 1.3 mutual authentication. Exact trusted gateway certificate subjects may supply `X-Quorus-*` assertions; direct workloads are resolved from exact certificate-subject bindings. The production profile cannot disable authentication or TLS and refuses incomplete trust configuration. Legacy plaintext constructors exist only for explicitly insecure development and existing test fixtures.

The API MUST distinguish:

- human operators;
- service integrations;
- Quorus controllers;
- Quorus agents;
- deployment automation.

### 4.2 Authorization scopes

| Scope | Representative permissions |
|---|---|
| `transfers.read` | View transfer state, attempts, progress, and events within authorized tenant/service scope |
| `transfers.submit` | Create transfers and approved retries |
| `transfers.control` | Cancel, pause, resume, or reconcile transfers |
| `operations.read` | View critical, late, at-risk, stalled, and failed transfer operations |
| `routes.manage` | Create, validate, activate, suspend, and trigger routes |
| `workflows.manage` | Manage workflow definitions and executions |
| `agents.read` | View agent inventory, health, capabilities, and posture |
| `agents.manage` | Drain, resume, quarantine, rotate, upgrade, revoke, and decommission agents |
| `services.manage` | Manage service aliases, trust policy, and secret references |
| `tenants.manage` | Manage tenants, quotas, ownership, and tenant policy |
| `security.audit.read` | Search and export immutable security and change events |
| `cluster.read` | View nodes, leader, replication, snapshots, and effective controller configuration |
| `cluster.manage` | Execute explicitly supported controller administrative operations |

Resource ownership, business-service scope, environment, and tenant policy further constrain every scope. Listing a resource requires the same authorization as reading it.

### 4.3 Audit contract

Every authentication decision, authorization denial, mutation, privileged read, secret-reference use, agent identity change, service connection test, and administrative action MUST create an immutable audit event. Audit events include actor, subject, action, resource, tenant, business service, decision, reason, source address, correlation and trace identifiers, request fingerprint, timestamp, and resulting resource version. Secret values and sensitive payload fields MUST be redacted.

## 5. Platform and Operation Resources

| Method | Path | State | Purpose |
|---|---|---|---|
| `GET` | `/health/live` | Current | Process liveness only; no sensitive details |
| `GET` | `/health/ready` | Current | Readiness to serve the configured role |
| `GET` | `/health` | Current | Authenticated detailed health |
| `GET` | `/status` | Current | Controller status summary |
| `GET` | `/metrics` | Current | Prometheus metrics under protected operational access |
| `GET` | `/api/v1/info` | Current | Version and controller information |
| `GET` | `/api/v1/capabilities` | Required | Protocol, feature, API, and compatibility capabilities |
| `GET` | `/api/v1/openapi` | Required | Canonical OpenAPI 3.1 document |
| `GET` | `/api/v1/operations` | Required | Search caller-visible asynchronous operations |
| `GET` | `/api/v1/operations/{operationId}` | Required | Read operation status and result |
| `DELETE` | `/api/v1/operations/{operationId}` | Required | Cancel a cancellable operation |

Health endpoints MUST distinguish process health from dependency readiness. A degraded dependency MUST NOT make liveness fail unless the process must be restarted.

## 6. Transfer Resources

### 6.1 Transfer endpoints

| Method | Path | State | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/transfers` | Current | Submit a transfer; production contract requires idempotency and policy validation |
| `GET` | `/api/v1/transfers` | Required | Search transfers by tenant, service, state, time, criticality, route, workflow, agent, and deadline risk |
| `GET` | `/api/v1/transfers/{transferId}` | Current | Read authoritative transfer summary |
| `DELETE` | `/api/v1/transfers/{transferId}` | Current | Compatibility cancellation; returns the resulting transfer or operation |
| `POST` | `/api/v1/jobs/{jobId}/status` | Current | Attempt-aware agent report with expected state, fence, lease, ordered sequence, atomic multi-entity application, and legacy-assignment compatibility |
| `POST` | `/api/v1/transfers/{transferId}:cancel` | Required | Explicit conditional cancellation with reason |
| `POST` | `/api/v1/transfers/{transferId}:pause` | Required | Pause when the active adapter supports safe pause |
| `POST` | `/api/v1/transfers/{transferId}:resume` | Required | Resume a paused transfer when supported |
| `POST` | `/api/v1/transfers/{transferId}:retry` | Required | Create a governed new attempt or replacement transfer |
| `POST` | `/api/v1/transfers/{transferId}:reconcile` | Required | Reconcile uncertain execution or publication state |
| `GET` | `/api/v1/transfers/{transferId}/attempts` | Current | Immutable execution-attempt history and active fence |
| `GET` | `/api/v1/transfers/{transferId}/attempts/{attemptId}` | Current | One attempt, lease, fence, sequence, agent, timings, and outcome |
| `GET` | `/api/v1/transfers/{transferId}/progress` | Current | Tenant-checked ownership, bytes, size semantics, missing/stale telemetry, configured policy windows, attempt, deadline, condition, low-confidence rate, and ETA view; historical evidence and calibrated prediction remain required |
| `GET` | `/api/v1/transfers/{transferId}/events` | Current | Initial ordered submission-event ledger; complete lifecycle vocabulary, pagination, replay, and retention remain required |
| `GET` | `/api/v1/transfers/{transferId}/timeline` | Required | Operator-oriented end-to-end timeline |
| `GET` | `/api/v1/transfers/{transferId}/integrity` | Required | Configured and observed integrity evidence |
| `GET` | `/api/v1/transfers/{transferId}/publication` | Required | Destination staging, commit, and publication state |

### 6.2 Submission contract

A production transfer submission MUST support:

```json
{
  "tenantId": "payments",
  "businessService": "settlement-reporting",
  "owner": "settlement-operations",
  "criticality": "CRITICAL",
  "source": {"serviceConnectionId": "svc-ledger-out", "path": "/close/2026-09-01.dat"},
  "destination": {"serviceConnectionId": "svc-clearing-in", "path": "/incoming/2026-09-01.dat"},
  "expectedStartAt": "2026-09-01T16:00:00Z",
  "requiredCompletionAt": "2026-09-01T16:10:00Z",
  "priority": 90,
  "integrityPolicy": {"algorithm": "SHA-256", "expectedDigest": null},
  "publicationPolicy": {"mode": "ATOMIC_RENAME", "overwrite": "DENY"},
  "retryPolicy": {"maximumAttempts": 3, "maximumElapsedTime": "PT8M"},
  "runbook": {"url": "https://runbooks.example/settlement-transfer"},
  "labels": {"market": "EU", "processingDate": "2026-09-01"}
}
```

Raw credentials and credential-bearing URIs are rejected. `serviceConnectionId` resolves an authorized endpoint, trust policy, network policy, and secret reference without exposing the secret.

**Current implementation boundary:** production transfer submission uses a tenant-scoped service connection alias, remote path, and agent pool. The controller resolves and authorizes the alias without retrieving credentials, and the executing agent repeats the committed policy version and digest, pool, network zone, path, direction, host, port, CIDR, DNS, trust, and local-root checks before resolving the opaque secret reference. The governed endpoint is constructed from that authorization, and HTTPS, FTPS, and SFTP sockets connect to an approved resolved address while preserving the original hostname for peer verification. Direct URI submission is development-only; URI user-info is rejected before request mapping or Raft command submission. A redacted scanner supports legacy migration without returning credential contents.

### 6.3 Transfer states

The canonical summary state is one of:

`SUBMITTED`, `VALIDATING`, `QUEUED`, `ASSIGNED`, `RUNNING`, `PAUSING`, `PAUSED`, `CANCELLING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `TIMED_OUT`, `RECONCILIATION_REQUIRED`, or `QUARANTINED`.

Operational condition is reported separately as `ON_TRACK`, `AT_RISK`, `LATE`, `STALLED`, `DEGRADED`, or `UNKNOWN`. A successful byte copy is not `SUCCEEDED` until required integrity verification and destination publication have completed.

Each attempt has its own state and identity. Reassignment MUST create a new attempt and fencing token. Historical attempts are never overwritten.

### 6.4 Progress and deadline risk

Progress MUST include `observedAt`, `bytesTransferred`, `totalBytes`, `percentComplete`, rolling and average throughput, last-progress time, estimated completion, required completion, time remaining, risk state, risk reason, condition onset and duration where applicable, active attempt, agent, source and destination service aliases, retry count, and confidence.

The API MUST distinguish “no bytes expected yet,” “source size unknown,” “telemetry stale,” and “transfer stalled.” Absence of telemetry MUST NOT be represented as zero progress.

### 6.5 Transfer events

Every event contains `eventId`, `sequence`, `eventType`, `occurredAt`, `recordedAt`, `transferId`, `attemptId`, actor, agent, tenant, business service, correlation and trace identifiers, previous and current state, reason code, and redacted details.

At minimum, the event vocabulary covers submission, validation, queueing, assignment, acceptance, rejection, start, progress, pause, resume, retry scheduling, lease expiry, reassignment, cancellation, source connection, destination connection, integrity verification, staging, publication, completion, failure, deadline-risk change, stall detection, reconciliation, and operator acknowledgement.

**Current implementation boundary:** the controller currently emits the canonical submission, assignment, acceptance, start, and progress events from replicated state-machine commands. It exposes them through the tenant-checked per-transfer event resource and persists them in controller snapshots. This partial implementation does not reduce the complete required vocabulary or the pagination, replay, retention, and streaming requirements.

## 7. Assignment Resources and Agent Protocol

| Method | Path | State | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/assignments` | Current | Administrative assignment creation; tenant and reference invariants are required |
| `GET` | `/api/v1/assignments` | Current | List assignments; production contract requires filtering and pagination |
| `GET` | `/api/v1/assignments/{assignmentId}` | Current | Read assignment |
| `PUT` | `/api/v1/assignments/{assignmentId}/accept` | Current | Agent accepts the offered attempt |
| `PUT` | `/api/v1/assignments/{assignmentId}/reject` | Current | Agent rejects with a stable reason code |
| `PUT` | `/api/v1/assignments/{assignmentId}/status` | Current | Compatibility status update |
| `PUT` | `/api/v1/assignments/{assignmentId}/cancel` | Current | Request assignment cancellation |
| `DELETE` | `/api/v1/assignments/{assignmentId}` | Current | Administrative removal subject to lifecycle restrictions |
| `POST` | `/api/v1/assignments/{assignmentId}:start` | Required | Enter running state with attempt and fencing checks |
| `POST` | `/api/v1/assignments/{assignmentId}:progress` | Required | Report monotonic progress and protocol observations |
| `POST` | `/api/v1/assignments/{assignmentId}:complete` | Required | Report integrity and publication evidence before completion |
| `POST` | `/api/v1/assignments/{assignmentId}:fail` | Required | Report classified failure and retry evidence |
| `POST` | `/api/v1/assignments/{assignmentId}/lease:renew` | Required | Renew the active attempt lease |

Agent transitions require the authenticated agent ID, assignment ID, transfer ID, attempt ID, expected state, monotonically increasing report sequence, and current lease/fencing token. The current attempt-aware status resource applies attempt, assignment, transfer status, and progress atomically. Duplicate reports, including terminal retries after a lost response, are idempotent; stale or cross-tenant reports return `409` or `403` without changing state.

## 8. Agent and Deployment Resources

### 8.1 Agent inventory and control

| Method | Path | State | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/agents/register` | Current | Alpha registration; replaced by authenticated enrollment in production |
| `POST` | `/api/v1/agents/heartbeat` | Current | Alpha heartbeat; production requests require enrolled identity |
| `GET` | `/api/v1/agents` | Current | Search agent inventory |
| `GET` | `/api/v1/agents/{agentId}` | Required | Agent identity, health, capacity, version, posture, and state |
| `GET` | `/api/v1/agents/{agentId}/jobs` | Current | Agent work polling compatibility endpoint |
| `GET` | `/api/v1/agents/{agentId}/capabilities` | Required | Protocol and execution capabilities |
| `GET` | `/api/v1/agents/{agentId}/effective-policy` | Required | Redacted service, tenant, transfer, and egress policy |
| `GET` | `/api/v1/agents/{agentId}/events` | Required | Lifecycle, health, security, and deployment events |
| `POST` | `/api/v1/agents/{agentId}:drain` | Required | Stop new assignments and finish or safely stop active work |
| `POST` | `/api/v1/agents/{agentId}:resume` | Required | Make an eligible drained agent schedulable |
| `POST` | `/api/v1/agents/{agentId}:quarantine` | Required | Immediately prevent new work and isolate according to policy |
| `POST` | `/api/v1/agents/{agentId}:rotate-identity` | Required | Start controlled credential rotation |
| `POST` | `/api/v1/agents/{agentId}:revoke` | Required | Revoke identity and authorization |
| `POST` | `/api/v1/agents/{agentId}:decommission` | Required | Irreversibly remove an already drained and revoked agent |

### 8.2 Enrollment and deployment

| Method | Path | State | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/agent-enrollments` | Required | Issue a short-lived, constrained enrollment operation |
| `GET` | `/api/v1/agent-enrollments/{enrollmentId}` | Required | Enrollment status without returning reusable bootstrap secrets |
| `POST` | `/api/v1/agent-enrollments/{enrollmentId}:revoke` | Required | Revoke unused or compromised enrollment authority |
| `POST` | `/api/v1/agent-deployments` | Required | Start signed, policy-constrained rollout |
| `GET` | `/api/v1/agent-deployments` | Required | Search deployment and upgrade history |
| `GET` | `/api/v1/agent-deployments/{deploymentId}` | Required | Rollout targets, versions, health gates, and result |
| `POST` | `/api/v1/agent-deployments/{deploymentId}:pause` | Required | Pause rollout without changing completed targets |
| `POST` | `/api/v1/agent-deployments/{deploymentId}:resume` | Required | Resume after policy and health checks |
| `POST` | `/api/v1/agent-deployments/{deploymentId}:rollback` | Required | Controlled rollback to an approved signed version |

Deployment representations MUST include artifact digest, signature verification, SBOM and provenance references, target selector, approved version, rollout strategy, drain policy, health gates, failure threshold, initiator, and audit correlation. The API MUST reject unsigned or unapproved artifacts.

## 9. Service Connectivity and Secret References

The current registry isolates complete tenant/resource pairs, including identifiers that
contain dots. HTTP CRUD, validation and collection reads use the authenticated tenant;
secret-reference and security-event lists obey the same boundary. A colliding identifier
owned by another tenant is not an existing resource in the caller's namespace: item
reads/updates/deletes return `404`, and independent creates may coexist. Conflicting
caller-supplied tenant claims remain forbidden.

Ownership is checked again during replicated state application. Malformed ownership or
ambiguous legacy migration is rejected with a redacted problem response; records are not
silently reassigned or partially migrated. This correction does not add linearizable
follower reads or the later-phase API features below.

| Method | Path | State | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/service-connections` | Current | Create a tenant-scoped service alias |
| `GET` | `/api/v1/service-connections` | Current | List authorized aliases and posture |
| `GET` | `/api/v1/service-connections/{serviceConnectionId}` | Current | Redacted endpoint, protocol, ownership, trust, and policy |
| `PUT` | `/api/v1/service-connections/{serviceConnectionId}` | Current | Update and increment the policy version |
| `DELETE` | `/api/v1/service-connections/{serviceConnectionId}` | Current | Retire an alias |
| `POST` | `/api/v1/service-connections/{serviceConnectionId}/validate` | Current | Validate schema, policy, DNS, and references; optionally perform a bounded route probe without secret retrieval |
| `POST` | `/api/v1/service-connections/{connectionId}:test` | Required | Authorized asynchronous connectivity and trust test |
| `GET` | `/api/v1/service-connections/{connectionId}/trust` | Required | Host keys, CA references, pinning policy, and verification status |
| `GET` | `/api/v1/service-connections/{connectionId}/events` | Required | Changes, tests, denials, and use history |
| `GET` | `/api/v1/secret-references` | Current | Authorized metadata only: provider, status, expiry, and rotation time |
| `POST` | `/api/v1/secret-references` | Current | Register an opaque external-provider reference; values are forbidden |
| `GET` | `/api/v1/secret-references/{secretReferenceId}` | Current | Read redacted reference metadata |
| `PUT` | `/api/v1/secret-references/{secretReferenceId}` | Current | Rotate, expire, or revoke a reference |
| `DELETE` | `/api/v1/secret-references/{secretReferenceId}` | Current | Delete only when no alias references it |
| `GET` | `/api/v1/security-events` | Current | Tenant-scoped connection and secret lifecycle evidence, bounded by `limit` and opaque `cursor` |
| `POST` | `/api/v1/secret-references:validate` | Required | Validate reference existence and access without returning a value |

Service connections MUST define protocol, endpoint, permitted path or bucket scope, tenant, business owner, allowed agent pools, environment, service identity verification, encryption minimum, egress rule, timeout, and a secret reference where needed. SFTP host-key verification and TLS certificate verification are mandatory and cannot be silently disabled in production. Authentication types are protocol constrained. TLS approved-CA identifiers are SHA-256 certificate fingerprints that restrict an otherwise valid PKIX chain; optional peer fingerprints pin the leaf certificate.

Connection tests MUST return redacted stage results for DNS, route/egress policy, network connection, TLS or SSH negotiation, peer identity, authentication, authorization, and optional read/write capability. They MUST NOT upload production-like data unless an explicitly approved test path and operation are configured.

The current validation resource is policy-only unless `probeNetwork` is true. An active probe connects only to a controller-approved address within the bounded `probeTimeoutMillis` and may return `ROUTE_VERIFIED`; negotiation, peer identity, authentication, authorization, and read/write capability remain unexecuted and MUST NOT be inferred from a successful TCP route. Submission records `SERVICE_CONNECTION_AUTHORIZED`, while `SERVICE_CONNECTION_LAST_USED` is recorded only after agent-side policy enforcement and secret-authority resolution. A reference past `expiresAt` is durably transitioned to `EXPIRED` and audited before transfer denial.

Route probes MUST apply the same protocol default-port contract as transfer policy when
an endpoint omits its port. A partial connection update MUST retain every omitted trust
field, including approved CA identifiers, SSH host-key pins, TLS peer pins and minimum
TLS version. `GET /api/v1/security-events` accepts `limit` 1–1000 (default 100) and an
opaque cursor, and returns `events`, the page-size `total`, and nullable `nextCursor`.
This bounded query does not define automatic pruning: current authoritative events remain
in snapshot state until Phase 9 supplies archive, retention and legal-hold policy.

Controller DNS authorization for validation and transfer submission MUST run off the
HTTP event loop with shared bounded admission and a deadline that includes worker
queue time. Capacity exhaustion returns HTTP 503; deadline expiry returns HTTP 504.
A timed-out native lookup retains its slot until completion, and its late result
MUST NOT authorize a transfer or initiate a validation probe. Changed registry
authority during resolution returns HTTP 409. Defaults and configuration are documented
in the [deployment guide](QUORUS_SECURITY_DEPLOYMENT_GUIDE.md#14-bounded-controller-dns-authorization).

The service-connection `remotePath` field is a literal absolute path, not a pre-encoded
URI. Root scope `/` permits descendants. Filename punctuation (`#`, `?`, `%`, spaces)
and Unicode MUST survive endpoint encoding; traversal segments and backslashes are
rejected. Portless FTPS uses explicit `AUTH TLS` on port 21. Implicit TLS requires an
explicit port 990 and an egress allowlist containing that port.

## 10. Route Resources

| Method | Path | State | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/routes` | Current | Create route definition |
| `GET` | `/api/v1/routes` | Current | List routes; production contract adds filtering and pagination |
| `GET` | `/api/v1/routes/{routeId}` | Current | Read route |
| `PUT` | `/api/v1/routes/{routeId}` | Current | Conditional update |
| `DELETE` | `/api/v1/routes/{routeId}` | Current | Delete eligible inactive route |
| `PUT` | `/api/v1/routes/{routeId}/suspend` | Current | Suspend route |
| `PUT` | `/api/v1/routes/{routeId}/resume` | Current | Resume route |
| `POST` | `/api/v1/routes/{routeId}:validate` | Required | Validate services, trigger, policies, and execution graph |
| `POST` | `/api/v1/routes/{routeId}:activate` | Planned | Activate after route evaluator exists and validation passes |
| `POST` | `/api/v1/routes/{routeId}:trigger` | Planned | Authorized manual trigger using the same policy path as automatic execution |
| `GET` | `/api/v1/routes/{routeId}/executions` | Planned | Route execution history and linked transfers |
| `GET` | `/api/v1/routes/{routeId}/events` | Required | Definition and lifecycle history |

Creating or resuming a route MUST NOT report it as executable unless the trigger evaluator is running, service connections are valid, and policy checks pass. Route execution creates traceable transfer or workflow resources; it does not create anonymous background work.

## 11. Workflow Resources

| Method | Path | State | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/workflows` | Required | Create immutable-versioned workflow definition |
| `GET` | `/api/v1/workflows` | Required | Search definitions and versions |
| `GET` | `/api/v1/workflows/{workflowId}` | Required | Read definition metadata and current version |
| `PUT` | `/api/v1/workflows/{workflowId}` | Required | Create a new definition version using a precondition |
| `DELETE` | `/api/v1/workflows/{workflowId}` | Required | Retire when retention and execution references permit |
| `POST` | `/api/v1/workflows/{workflowId}:validate` | Required | Validate schema, dependencies, variables, and policy |
| `POST` | `/api/v1/workflows/{workflowId}:plan` | Required | Return resolved execution plan without side effects |
| `POST` | `/api/v1/workflow-executions` | Required | Execute a pinned workflow version in normal, dry-run, or virtual mode |
| `GET` | `/api/v1/workflow-executions` | Required | Search executions by state, service, deadline, definition, and time |
| `GET` | `/api/v1/workflow-executions/{executionId}` | Required | Execution summary and aggregate outcome |
| `POST` | `/api/v1/workflow-executions/{executionId}:cancel` | Required | Governed cancellation of remaining work |
| `POST` | `/api/v1/workflow-executions/{executionId}:pause` | Required | Pause scheduling and safely pause supported active steps |
| `POST` | `/api/v1/workflow-executions/{executionId}:resume` | Required | Resume after validation of current dependencies and policy |
| `POST` | `/api/v1/workflow-executions/{executionId}:retry` | Required | Retry eligible failed steps under a recorded policy |
| `GET` | `/api/v1/workflow-executions/{executionId}/steps` | Required | Step dependencies, state, timings, attempts, and linked transfers |
| `GET` | `/api/v1/workflow-executions/{executionId}/events` | Required | Ordered workflow timeline |

Definitions MUST preserve the exact submitted and normalized form, version, digest, variables schema, and validation result. An execution pins the definition version and resolved non-secret inputs. Secret values are never stored in the execution representation.

## 12. Tenant, Quota, and Policy Resources

| Method | Path | State | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/tenants` | Required | Create tenant with ownership and hierarchy policy |
| `GET` | `/api/v1/tenants` | Required | Search authorized tenants |
| `GET` | `/api/v1/tenants/{tenantId}` | Required | Read tenant state and hierarchy |
| `PUT` | `/api/v1/tenants/{tenantId}` | Required | Conditional update |
| `DELETE` | `/api/v1/tenants/{tenantId}` | Required | Retire only after governed dependency checks |
| `POST` | `/api/v1/tenants/{tenantId}:suspend` | Required | Stop new work under documented active-work policy |
| `POST` | `/api/v1/tenants/{tenantId}:activate` | Required | Restore eligibility after policy validation |
| `GET` | `/api/v1/tenants/{tenantId}/usage` | Required | Current and historical transfer, byte, agent, and concurrency usage |
| `GET` | `/api/v1/tenants/{tenantId}/quotas` | Required | Effective quotas and reservation state |
| `PUT` | `/api/v1/tenants/{tenantId}/quotas` | Required | Conditional quota configuration |
| `GET` | `/api/v1/tenants/{tenantId}/policies` | Required | Effective inherited and local policies |
| `GET` | `/api/v1/tenants/{tenantId}/service-connections` | Required | Authorized service aliases |
| `GET` | `/api/v1/tenants/{tenantId}/agents` | Required | Authorized agent inventory |
| `GET` | `/api/v1/tenants/{tenantId}/transfers` | Required | Tenant-scoped transfer search |

Quota admission and reservation MUST be atomic with transfer admission in authoritative state. The response must identify the effective limit, current use, reservation, and retry conditions without disclosing another tenant's data.

## 13. Operations Monitoring and Telemetry

Transfer-process monitoring is a primary Quorus API outcome. Infrastructure health supports this outcome but does not replace it.

| Method | Path | State | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/transfer-operations/critical` | Required | Active and recently completed critical transfers |
| `GET` | `/api/v1/transfer-operations/at-risk` | Required | Transfers predicted to miss a required completion time |
| `GET` | `/api/v1/transfer-operations/late` | Required | Transfers whose completion deadline has passed |
| `GET` | `/api/v1/transfer-operations/stalled` | Required | Transfers with policy-defined loss of progress |
| `GET` | `/api/v1/transfer-operations/degraded` | Required | Transfers affected by retry, capacity, service, agent, or telemetry degradation |
| `GET` | `/api/v1/operational-events` | Required | Authorized cross-resource event search |
| `GET` | `/api/v1/operational-events/stream` | Required | Resumable server-sent event stream with bounded filters |
| `GET` | `/api/v1/alerts` | Required | Search active and historical actionable alerts |
| `GET` | `/api/v1/alerts/{alertId}` | Required | Alert evidence, affected transfers, policy, and state |
| `POST` | `/api/v1/alerts/{alertId}:acknowledge` | Required | Record operator ownership and comment |
| `POST` | `/api/v1/alerts/{alertId}:resolve` | Required | Resolve with reason and supporting evidence |
| `POST` | `/api/v1/alerts/{alertId}:suppress` | Required | Time-bounded, policy-authorized suppression; never delete evidence |

Operational queries MUST support tenant, business service, owner, criticality, environment, route, workflow, agent, service connection, state, condition, deadline interval, processing date, and labels.

The event stream MUST support `Last-Event-ID`, heartbeats, authorization re-evaluation, bounded replay, and explicit gap notification. Slow consumers MUST be disconnected with a resumable position rather than causing controller memory growth. Streaming does not replace durable event queries.

Alerts MUST be actionable. Each alert includes policy, severity, first and last occurrence, detection evidence, affected resources, deadline impact, runbook, owner, acknowledgements, suppression, and resolution. Alert notification delivery state is observable and auditable.

## 14. Security and Audit Resources

| Method | Path | State | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/security/me` | Current | Effective identity, tenant, environment, roles, scopes, and elevation expiry |
| `GET` | `/api/v1/security/authorization/explain` | Current | Query-form compatibility endpoint for an explainable decision |
| `POST` | `/api/v1/security/authorization/check` | Current | Explain an authorization decision without performing the action |
| `GET` | `/api/v1/security/trust` | Current | Active runtime trust-policy version, revoked count, and caller-certificate expiry posture |
| `PUT` | `/api/v1/security/trust/revocations` | Current | Elevated atomic replacement of runtime certificate serial revocations |
| `GET` | `/api/v1/security/trust-bundles` | Required | Metadata and versions for controller, agent, and service trust bundles |
| `GET` | `/api/v1/security/certificates` | Required | Certificate metadata, expiry, owner, and rotation state; never private material |
| `GET` | `/api/v1/security/revocations` | Required | Revoked agent, certificate, enrollment, and token identifiers |
| `GET` | `/api/v1/audit-events` | Required | Immutable, authorized audit search |
| `GET` | `/api/v1/audit-events/{eventId}` | Required | One complete redacted audit event |
| `POST` | `/api/v1/audit-exports` | Required | Asynchronous signed evidence export |
| `GET` | `/api/v1/audit-exports/{exportId}` | Required | Export status, digest, retention, and authorized retrieval link |

Authorization checks MUST use the same policy engine as real requests. A runtime revocation replacement MUST be re-evaluated on subsequent authenticated HTTP requests and Raft RPCs, including existing TLS connections; it does not itself reload certificate, private-key, CRL, or PEM trust-anchor files. Audit exports MUST be integrity-protected, access-controlled, time-limited, and themselves audited.

## 15. Controller Cluster and Configuration

| Method | Path | State | Purpose |
|---|---|---|---|
| `GET` | `/raft/status` | Current | Compatibility Raft status |
| `GET` | `/api/v1/cluster` | Required | Cluster identity, leader, term, quorum, and readiness |
| `GET` | `/api/v1/cluster/nodes` | Required | Node role, health, compatibility, and replication position |
| `GET` | `/api/v1/cluster/nodes/{nodeId}` | Required | Detailed node state |
| `GET` | `/api/v1/cluster/replication` | Required | Commit, applied, lag, quorum, and snapshot state |
| `GET` | `/api/v1/cluster/snapshots` | Required | Snapshot metadata and restore compatibility |
| `POST` | `/api/v1/cluster/snapshots` | Required | Request an asynchronous snapshot under policy |
| `GET` | `/api/v1/configuration/schema` | Required | Supported configuration schema and restart semantics |
| `GET` | `/api/v1/configuration/effective` | Required | Redacted effective configuration with source and version |
| `POST` | `/api/v1/configuration:validate` | Required | Validate candidate configuration without applying it |

Dynamic membership writes are not defined while Quorus membership is static. An API MUST NOT advertise node add/remove operations until the consensus implementation safely supports joint membership, recovery, compatibility validation, and audited rollback.

Effective configuration MUST redact secrets and identify whether each field is static, reloadable, node-local, or cluster-wide. Configuration mutation is not part of this version unless an atomic replicated application model is implemented.

## 16. Status Codes and Stable Errors

| HTTP status | Use |
|---|---|
| `200` | Successful read or synchronous action |
| `201` | Resource created; `Location` identifies it |
| `202` | Asynchronous operation accepted |
| `204` | Successful action with no representation |
| `400` | Invalid syntax, filter, or request schema |
| `401` | Missing or invalid authentication |
| `403` | Authenticated but not authorized, including tenant or agent mismatch |
| `404` | Resource absent or concealed by authorization policy |
| `409` | Lifecycle conflict, stale attempt, lease/fencing conflict, or idempotency-key conflict |
| `412` | `If-Match` precondition failed |
| `422` | Semantically invalid definition or policy |
| `428` | Required precondition omitted |
| `429` | Rate or quota limit; includes retry guidance when applicable |
| `503` | Not leader, quorum unavailable, or required dependency unavailable |
| `504` | Bounded upstream or operation wait expired |

Stable error codes include at least `NOT_LEADER`, `QUORUM_UNAVAILABLE`, `AUTHENTICATION_REQUIRED`, `ACCESS_DENIED`, `TENANT_MISMATCH`, `AGENT_IDENTITY_MISMATCH`, `VALIDATION_FAILED`, `PRECONDITION_REQUIRED`, `PRECONDITION_FAILED`, `INVALID_STATE_TRANSITION`, `IDEMPOTENCY_KEY_REUSED`, `STALE_ATTEMPT`, `LEASE_EXPIRED`, `FENCING_TOKEN_REJECTED`, `QUOTA_EXCEEDED`, `SERVICE_POLICY_DENIED`, `SERVICE_IDENTITY_FAILED`, `SECRET_REFERENCE_INVALID`, `CAPABILITY_UNAVAILABLE`, `TELEMETRY_STALE`, and `RECONCILIATION_REQUIRED`.

## 17. Retention, Query, and Export

Transfer summaries, attempts, events, audit events, alerts, workflow executions, deployment evidence, and connectivity tests MUST have explicit tenant-aware retention policies. Deleting a definition or live resource MUST NOT delete evidence still subject to retention.

Large event and audit exports are asynchronous. An export records the exact filter, authorization context, creation time, row count, schema version, digest, expiry, and retrieval audit. API pagination is not a substitute for a bulk evidence export.

## 18. Versioning and Compatibility

- Breaking changes require a new major base path.
- Compatible fields and enum values MAY be added only where clients are required to tolerate them.
- Removed fields and paths require published deprecation metadata and a supported migration period.
- Responses SHOULD include the server API version and build version.
- Agent protocol compatibility MUST be negotiated during enrollment and heartbeat.
- Stored workflow, route, policy, and service-connection resources include a schema version and migration status.

## 19. OpenAPI and Conformance Requirements

The repository MUST contain the canonical OpenAPI 3.1 document and reusable JSON Schemas. CI MUST verify:

1. every registered `/api/v1` path is declared;
2. every declared current endpoint is registered;
3. request and response examples validate against schemas;
4. authentication and required scopes are declared for every operation;
5. all error responses use the problem schema;
6. generated client compatibility tests pass;
7. leader, follower, quorum-loss, retry, idempotency, and precondition behavior passes contract tests;
8. tenant and resource-ownership isolation tests cover every collection and item path;
9. agent endpoints reject non-agent and mismatched identities;
10. no response or log fixture exposes a secret value;
11. operational event ordering, replay, gap, and retention behavior is tested;
12. transfer completion cannot precede required integrity and publication evidence.

Release documentation MUST publish a generated endpoint coverage report with `Current`, `Required`, and `Planned` counts. Required endpoints cannot be represented as implemented until their contract, authorization, audit, persistence, and failure-path tests pass.

## 20. Current Conformance Gaps

| ID | Severity | Gap | Production impact |
|---|---|---|---|
| API-01 | Critical | No canonical OpenAPI 3.1 contract and automated registered-path coverage | Integrations cannot rely on a complete machine-verifiable contract |
| API-02 | Critical | No built-in authenticated identity, tenant derivation, or scope enforcement | Caller and tenant claims cannot be trusted at the controller boundary |
| API-03 | Critical | Transfer API exposes attempt history and an initial dedicated progress view but lacks collection search, timeline, integrity, publication, retry, pause, resume, and reconciliation resources | Technology operations cannot fully run or investigate critical transfers through the API |
| API-04 | Critical | Per-transfer progress applies configured freshness/stall windows and distinguishes missing and stale telemetry, but the active stall boundary, configurable deadline-risk policy, operational queries, alerts, durable events, timelines, and streaming are incomplete | Time-sensitive transfer failures cannot yet be detected, distributed, and actioned reliably at fleet scale |
| API-05 | Critical | Agent registration is not a complete enrollment, rotation, quarantine, revocation, and decommissioning API | Enterprise agent trust lifecycle is incomplete |
| API-06 | Closed in Phase 4 | Service-connection, trust, egress, opaque-secret-reference, validation, and security-event APIs are active and represented in OpenAPI | Remaining asynchronous active test and per-resource projections are additive API work, not a production-transfer bypass |
| API-07 | High | The attempt-aware status resource enforces attempt, lease, fencing, expected-state, sequence, monotonic progress, and atomic lifecycle rules, but specialized assignment actions, lease renewal, and integrity/publication completion evidence remain incomplete | Retry and reassignment remain unsafe until every mutation and destination commit uses the full contract |
| API-08 | High | Workflow functionality has no controller REST resources | Workflow definitions and executions cannot be governed or observed consistently |
| API-09 | High | Tenant, hierarchy, quota, usage, and policy services have no controller REST resources | Administrative behavior requires internal integration rather than a supported contract |
| API-10 | High | Route API exposes configuration without validation, trigger execution, or execution history | Route CRUD can be mistaken for an operating route service |
| API-11 | High | No immutable audit query and evidence-export API | Security and operational investigations lack supported evidence access |
| API-12 | High | No general idempotency, ETag/precondition, asynchronous-operation, pagination, or standard problem contract | Client retry and concurrent administration behavior is unsafe or inconsistent |
| API-13 | High | Cluster and configuration endpoints do not expose complete consistency, replication, snapshot, and redacted effective-configuration state | Operators lack a supported administrative view of controller health and configuration |
| API-14 | Medium | API/agent compatibility, deprecation, retention, export, and event-stream replay contracts are not implemented | Long-lived integrations and evidence handling remain fragile |

Critical gaps block protected production use. High gaps block the affected production capability. A gap is closed only when implementation, OpenAPI, authorization, audit, persistence, and conformance tests are present.

## 21. Functional Coverage Checklist

| System capability | Required API coverage |
|---|---|
| File transfer execution | Submission, lifecycle controls, attempts, progress, events, integrity, publication, retry, reconciliation |
| Critical transfer operations | Deadline and risk views, stalls, degradation, alerts, acknowledgement, durable event stream |
| Protocol adapters | Capability discovery, service alias validation, trust result, classified protocol failure |
| Distributed scheduling | Assignment state, attempt identity, leases, fencing, agent capacity, rejection reasons |
| Agent operations | Inventory, health, capability, effective policy, drain, resume, quarantine, identity rotation, revocation |
| Agent deployment | Enrollment, signed artifact rollout, health gates, pause, resume, rollback, provenance evidence |
| Service connectivity | Alias CRUD, trust, egress and path policy, secret references, staged connection tests |
| Routes | CRUD, validation, activation, suspension, trigger, execution history, events |
| Workflows | Versioned definitions, validation, plan, execution, step state, controls, events |
| Tenancy | Tenant lifecycle, hierarchy, quotas, reservations, usage, policy, scoped resource views |
| Security | Effective identity, authorization explanation, trust and certificate metadata, revocations |
| Audit and evidence | Immutable search, item read, signed asynchronous export, retention metadata |
| Controller cluster | Leader, term, quorum, nodes, replication, snapshots, readiness, read consistency |
| Configuration | Schema, redacted effective values, sources, compatibility, candidate validation |
| Integration contract | OpenAPI 3.1, standard errors, pagination, idempotency, preconditions, async operations, versioning |

An API release is complete only when every implemented system capability is mapped to this checklist and every supported operation can be performed and observed without using internal storage or undocumented endpoints.

## 22. Related Documents

- [Quorus Architecture Specification](QUORUS_ARCHITECTURE_SPECIFICATION.md)
- [Quorus HTTP API Reference](QUORUS_API_REFERENCE.md)
- [Quorus Architecture Quickstart](QUORUS_ARCHITECTURE_QUICKSTART.md)
- [Quorus YAML Syntax Guide](QUORUS_YAML_SYNTAX_GUIDE.md)
- [Quorus Enterprise Implementation Plan](../docs-design/task/QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md)
