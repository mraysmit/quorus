# Quorus HTTP API Reference

**Version:** 2.1  
**Date:** 2026-03-17  
**Implementation:** `quorus-controller/src/main/java/dev/mars/quorus/controller/http/HttpApiServer.java`

This document reflects the endpoints currently registered by the embedded controller HTTP server.

## Base URL

`http://{controller-host}:8080`

## Request Model

- Content type: `application/json` for JSON request bodies
- All write requests are subject to leader guarding
- The API currently does **not** enforce built-in authentication

## Tenant Isolation

Every agent belongs to exactly one tenant and can only receive jobs from that tenant. `tenantId` is a required field in both agent registration and transfer job creation. The controller enforces this at every write path:

- Agent registration without `tenantId` → `400 Bad Request`
- Transfer creation without `tenantId` → `400 Bad Request`
- Heartbeat carrying a `tenantId` that does not match the registered agent's tenant → `403 Forbidden`
- Job status update from an agent for a job belonging to a different tenant → `403 Forbidden`
- `GET /api/v1/agents/:agentId/jobs` silently filters out jobs whose tenant does not match the agent's registered tenant

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
| `tenantId` | string | Tenant this agent belongs to |

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
  "tenantId": "acme-corp",
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
  "tenantId": "acme-corp",
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

Returns jobs assigned to a specific agent. Only returns jobs whose `tenantId` matches the agent's registered tenant.

## Transfer Endpoints

### `POST /api/v1/transfers`

Creates a transfer job. Returns `201 Created` on success.

**Required fields:**

| Field | Type | Description |
|-------|------|-------------|
| `tenantId` | string | Tenant that owns this job — must match the agent that will execute it |
| `jobId` | string | Unique job identifier |
| `sourceUri` | string | Source URI |
| `destinationPath` | string | Destination path |

**Optional fields:**

| Field | Type | Description |
|-------|------|-------------|
| `totalBytes` | long | Expected transfer size in bytes |
| `description` | string | Human-readable description |

**Example:**

```json
{
  "tenantId": "acme-corp",
  "jobId": "job-2026-03-17-001",
  "sourceUri": "https://files.example.com/report.csv",
  "destinationPath": "/data/reports/report.csv",
  "totalBytes": 102400,
  "description": "Daily report"
}
```

**Response (201):**

```json
{
  "success": true,
  "jobId": "job-2026-03-17-001"
}
```

### `GET /api/v1/transfers/:jobId`

Returns transfer job details.

### `DELETE /api/v1/transfers/:jobId`

Deletes a transfer job.

## Job Status Endpoint

### `POST /api/v1/jobs/:jobId/status`

Updates status for an existing transfer job. Only the agent that owns the assignment — and belongs to the same tenant as the job — may update status.

**Required fields:**

| Field | Type | Description |
|-------|------|-------------|
| `agentId` | string | ID of the agent submitting the update |
| `status` | string | New assignment status (e.g., `IN_PROGRESS`, `COMPLETED`, `FAILED`) |

**Optional fields:**

| Field | Type | Description |
|-------|------|-------------|
| `bytesTransferred` | long | Running byte count for progress tracking |

**Example:**

```json
{
  "agentId": "agent-nyc-01",
  "status": "COMPLETED",
  "bytesTransferred": 102400
}
```

## Assignment Endpoints

### `POST /api/v1/assignments`

Creates a job assignment.

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
- Authentication is still an external deployment concern.

## Source of Truth

For endpoint registration, use:

- `quorus-controller/src/main/java/dev/mars/quorus/controller/http/HttpApiServer.java`

For route request handling, use:

- `quorus-controller/src/main/java/dev/mars/quorus/controller/http/handlers/RouteHandler.java`