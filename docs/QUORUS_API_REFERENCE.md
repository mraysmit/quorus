# Quorus HTTP API Reference

This document provides a complete reference for the Quorus Controller HTTP API. The API is embedded directly within each controller node and provides endpoints for health monitoring, cluster management, agent communication, and transfer job management.

**Base URL:** `http://{controller-host}:{port}` (default port: `8080`)

**Implementation:** [HttpApiServer.java](../quorus-controller/src/main/java/dev/mars/quorus/controller/http/HttpApiServer.java)

---

## Table of Contents

1. [Overview](#overview)
2. [Infrastructure Endpoints](#infrastructure-endpoints)
   - [Health Check](#health-check)
   - [Raft Status](#raft-status)
   - [Metrics](#metrics)
3. [Agent Endpoints](#agent-endpoints)
   - [Register Agent](#register-agent)
   - [Get Agent Jobs](#get-agent-jobs)
4. [Transfer Job Endpoints](#transfer-job-endpoints)
   - [Create Transfer Job](#create-transfer-job)
   - [Get Transfer Job](#get-transfer-job)
   - [Assign Job to Agent](#assign-job-to-agent)
   - [Update Job Status](#update-job-status)
5. [Generic Command Endpoint](#generic-command-endpoint)
6. [Data Models](#data-models)
7. [Error Handling](#error-handling)

---

## Overview

### Architecture

The HTTP API is embedded within each Quorus controller node. When a controller is the Raft **leader**, it processes write operations (POST requests) by submitting commands to the Raft consensus layer. All nodes can serve read operations.

```
┌─────────────────────────────────────────────────────┐
│                 Controller Node                      │
│  ┌─────────────┐    ┌─────────────┐    ┌──────────┐ │
│  │ HTTP API    │───▶│ Raft Node   │───▶│ State    │ │
│  │ Server      │    │ (Consensus) │    │ Machine  │ │
│  └─────────────┘    └─────────────┘    └──────────┘ │
└─────────────────────────────────────────────────────┘
```

### Request/Response Format

- **Content-Type:** `application/json`
- **Accept:** `application/json`
- All timestamps are in ISO 8601 format (UTC)

### Authentication

Currently, the API does not enforce authentication. For production deployments, place the controllers behind a reverse proxy (e.g., Nginx) with authentication.

---

## Infrastructure Endpoints

### Health Check

Returns the health status of the controller node and its Raft state.

#### Liveness Probe

**Endpoint:** `GET /health/live`

Returns `200 OK` if the process is alive and responsive. Not dependent on Raft state.

```json
{
  "status": "UP",
  "timestamp": "2026-02-13T10:00:00Z"
}
```

#### Readiness Probe

**Endpoint:** `GET /health/ready`

Returns `200 OK` if the node is ready to serve traffic (Raft running and leader elected), otherwise `503`.

```json
{
  "status": "UP",
  "timestamp": "2026-02-13T10:00:00Z",
  "checks": {
    "raftRunning": "UP",
    "clusterHasLeader": "UP"
  }
}
```

#### Full Health Check

**Endpoint:** `GET /health`

**Response:** `200 OK` (or `503` if degraded)

```json
{
  "status": "UP",
  "version": "1.0.0-alpha",
  "timestamp": "2026-02-13T10:00:00Z",
  "nodeId": "controller1",
  "raft": {
    "state": "LEADER",
    "term": 42,
    "commitIndex": 1000,
    "isLeader": true,
    "leaderId": "controller1"
  },
  "checks": {
    "raftCluster": "UP",
    "diskSpace": "UP",
    "memory": "UP"
  }
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `status` | string | `"UP"` or `"DEGRADED"` based on all checks |
| `version` | string | Application version |
| `timestamp` | string | ISO 8601 UTC timestamp |
| `nodeId` | string | Unique identifier of this controller node |
| `raft.state` | string | Current Raft state: `FOLLOWER`, `CANDIDATE`, or `LEADER` |
| `raft.term` | number | Current Raft election term |
| `raft.commitIndex` | number | Last committed log entry index |
| `raft.isLeader` | boolean | `true` if this node is the current cluster leader |
| `raft.leaderId` | string | ID of the current cluster leader (null if no leader) |
| `checks.raftCluster` | string | `"UP"` or `"DOWN"` — is the Raft node running |
| `checks.diskSpace` | string | `"UP"` or `"WARNING"` — free disk > 100MB |
| `checks.memory` | string | `"UP"` or `"WARNING"` — free memory > 10% of max |

**Example:**

```bash
# Liveness (always UP)
curl http://localhost:8080/health/live

# Readiness (Raft-dependent)
curl http://localhost:8080/health/ready

# Full health with dependency checks
curl http://localhost:8080/health
```

---

### Raft Status

Returns detailed information about the Raft consensus state.

**Endpoint:** `GET /raft/status`

**Response:** `200 OK`

```json
{
  "nodeId": "node1",
  "state": "LEADER",
  "currentTerm": 42,
  "isLeader": true,
  "isRunning": true,
  "leaderId": "node1"
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `nodeId` | string | This node's identifier |
| `state` | string | Raft state: `FOLLOWER`, `CANDIDATE`, or `LEADER` |
| `currentTerm` | integer | Current Raft term number |
| `isLeader` | boolean | Whether this node is the leader |
| `isRunning` | boolean | Whether the Raft engine is running |
| `leaderId` | string | ID of the current leader (may be null during election) |

**Example:**

```bash
curl http://localhost:8080/raft/status
```

---

### Metrics

Returns Prometheus-format metrics for monitoring. Proxies to the OpenTelemetry Prometheus exporter.

**Endpoint:** `GET /metrics`

**Response:** `200 OK` (text/plain, Prometheus format)

```
# HELP quorus_cluster_term Current Raft term
# TYPE quorus_cluster_term gauge
quorus_cluster_term{node_id="node1"} 42

# HELP quorus_transfer_bytes_total Total bytes transferred
# TYPE quorus_transfer_bytes_total counter
quorus_transfer_bytes_total{protocol="HTTP"} 1048576
```

**Example:**

```bash
curl http://localhost:8080/metrics
```

---

## Agent Endpoints

### Register Agent

Registers a new agent with the controller cluster. The registration is replicated via Raft consensus.

**Endpoint:** `POST /api/v1/agents/register`

**Request Body:**

```json
{
  "agentId": "agent-nyc-001",
  "hostname": "agent-nyc-001.corp.local",
  "address": "10.0.1.50",
  "port": 9090,
  "region": "us-east",
  "datacenter": "nyc-dc1",
  "version": "1.0.0",
  "capabilities": {
    "maxConcurrentTransfers": 5,
    "supportedProtocols": ["HTTP", "SFTP", "FTP", "SMB"],
    "maxBandwidthBytesPerSecond": 104857600
  },
  "metadata": {
    "environment": "production",
    "team": "data-engineering"
  }
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `agentId` | string | Yes | Unique identifier for the agent |
| `hostname` | string | Yes | Agent's hostname |
| `address` | string | Yes | Agent's IP address |
| `port` | integer | Yes | Port the agent listens on |
| `region` | string | No | Geographic region (e.g., `us-east`) |
| `datacenter` | string | No | Data center identifier |
| `version` | string | No | Agent software version |
| `capabilities` | object | No | Agent capabilities (see below) |
| `metadata` | object | No | Custom key-value metadata |

**Capabilities Object:**

| Field | Type | Description |
|-------|------|-------------|
| `maxConcurrentTransfers` | integer | Maximum parallel transfers |
| `supportedProtocols` | array | List of supported protocols: `HTTP`, `SFTP`, `FTP`, `SMB` |
| `maxBandwidthBytesPerSecond` | long | Bandwidth limit in bytes/second |

**Response:** `201 Created`

```json
{
  "success": true,
  "agentId": "agent-nyc-001"
}
```

**Example:**

```bash
curl -X POST http://localhost:8080/api/v1/agents/register \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "agent-nyc-001",
    "hostname": "agent-nyc-001.corp.local",
    "address": "10.0.1.50",
    "port": 9090,
    "region": "us-east",
    "datacenter": "nyc-dc1"
  }'
```

---

### Get Agent Jobs

Retrieves pending job assignments for a specific agent. Only returns jobs that are **not** `COMPLETED` or `FAILED`.

**Endpoint:** `GET /api/v1/agents/:agentId/jobs`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `agentId` | string | The agent's unique identifier |

**Response:** `200 OK`

```json
[
  {
    "jobId": "transfer-001",
    "agentId": "agent-nyc-001",
    "assignedAt": "2026-01-30T10:00:00Z",
    "status": "ASSIGNED",
    "retryCount": 0
  },
  {
    "jobId": "transfer-002",
    "agentId": "agent-nyc-001",
    "assignedAt": "2026-01-30T10:05:00Z",
    "status": "IN_PROGRESS",
    "retryCount": 0
  }
]
```

**Response Fields (per assignment):**

| Field | Type | Description |
|-------|------|-------------|
| `jobId` | string | The transfer job ID |
| `agentId` | string | The assigned agent ID |
| `assignedAt` | string | ISO 8601 timestamp of assignment |
| `status` | string | Assignment status (see [JobAssignmentStatus](#jobassignmentstatus)) |
| `retryCount` | integer | Number of retry attempts |

**Example:**

```bash
curl http://localhost:8080/api/v1/agents/agent-nyc-001/jobs
```

---

## Transfer Job Endpoints

### Create Transfer Job

Creates a new transfer job. The job is stored in the replicated state machine.

**Endpoint:** `POST /api/v1/transfers`

**Request Body:**

```json
{
  "jobId": "transfer-001",
  "sourceUri": "https://data.corp.com/exports/daily-report.csv",
  "destinationPath": "/data/imports/daily-report.csv",
  "totalBytes": 10485760,
  "description": "Daily sales report transfer"
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `jobId` | string | Yes | Unique identifier for the job |
| `sourceUri` | string (URI) | Yes | Source location (URL or protocol-specific URI) |
| `destinationPath` | string | Yes | Destination file path |
| `totalBytes` | long | No | Expected file size in bytes (0 if unknown) |
| `description` | string | No | Human-readable description |

**Response:** `201 Created`

```json
{
  "success": true,
  "jobId": "transfer-001"
}
```

**Example:**

```bash
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "jobId": "transfer-001",
    "sourceUri": "sftp://files.corp.com/exports/data.zip",
    "destinationPath": "/archive/data.zip",
    "totalBytes": 52428800
  }'
```

---

### Get Transfer Job

Retrieves the status and details of a specific transfer job.

**Endpoint:** `GET /api/v1/transfers/:jobId`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `jobId` | string | The transfer job's unique identifier |

**Response:** `200 OK`

```json
{
  "jobId": "transfer-001",
  "sourceUri": "https://data.corp.com/exports/daily-report.csv",
  "destinationPath": "/data/imports/daily-report.csv",
  "totalBytes": 10485760,
  "bytesTransferred": 5242880,
  "status": "IN_PROGRESS"
}
```

**Response:** `404 Not Found`

```json
{
  "error": "Job not found"
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `jobId` | string | The job identifier |
| `sourceUri` | string | Source location |
| `destinationPath` | string | Destination path |
| `totalBytes` | long | Expected total size in bytes |
| `bytesTransferred` | long | Bytes transferred so far |
| `status` | string | Current status (from assignment if exists, otherwise from job) |

**Example:**

```bash
curl http://localhost:8080/api/v1/transfers/transfer-001
```

---

### Assign Job to Agent

Assigns a transfer job to a specific agent for execution.

**Endpoint:** `POST /api/v1/jobs/assign`

**Request Body:**

```json
{
  "jobId": "transfer-001",
  "agentId": "agent-nyc-001",
  "assignedAt": "2026-01-30T10:00:00Z",
  "status": "ASSIGNED",
  "estimatedDurationMs": 60000,
  "assignmentStrategy": "ROUND_ROBIN"
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `jobId` | string | Yes | The transfer job to assign |
| `agentId` | string | Yes | The agent to assign the job to |
| `assignedAt` | string | Yes | ISO 8601 timestamp |
| `status` | string | Yes | Initial status (typically `ASSIGNED`) |
| `estimatedDurationMs` | long | No | Estimated duration in milliseconds |
| `assignmentStrategy` | string | No | Strategy used for assignment |

**Response:** `201 Created`

```json
{
  "success": true,
  "assignmentId": "transfer-001:agent-nyc-001"
}
```

**Note:** The `assignmentId` follows the convention `{jobId}:{agentId}`.

**Example:**

```bash
curl -X POST http://localhost:8080/api/v1/jobs/assign \
  -H "Content-Type: application/json" \
  -d '{
    "jobId": "transfer-001",
    "agentId": "agent-nyc-001",
    "assignedAt": "2026-01-30T10:00:00Z",
    "status": "ASSIGNED"
  }'
```

---

### Update Job Status

Updates the status of a job assignment and optionally reports transfer progress.

**Endpoint:** `POST /api/v1/jobs/:jobId/status`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `jobId` | string | The transfer job's unique identifier |

**Request Body:**

```json
{
  "agentId": "agent-nyc-001",
  "status": "IN_PROGRESS",
  "bytesTransferred": 5242880
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `agentId` | string | Yes | The agent reporting the status |
| `status` | string | Yes | New status (see [JobAssignmentStatus](#jobassignmentstatus)) |
| `bytesTransferred` | long | No | Current bytes transferred (updates job progress) |

**Response:** `200 OK`

```json
{
  "success": true
}
```

**Example - Report Progress:**

```bash
curl -X POST http://localhost:8080/api/v1/jobs/transfer-001/status \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "agent-nyc-001",
    "status": "IN_PROGRESS",
    "bytesTransferred": 5242880
  }'
```

**Example - Report Completion:**

```bash
curl -X POST http://localhost:8080/api/v1/jobs/transfer-001/status \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "agent-nyc-001",
    "status": "COMPLETED",
    "bytesTransferred": 10485760
  }'
```

**Example - Report Failure:**

```bash
curl -X POST http://localhost:8080/api/v1/jobs/transfer-001/status \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "agent-nyc-001",
    "status": "FAILED"
  }'
```

---

## Generic Command Endpoint

Submits a generic command to the Raft state machine. Used for advanced operations.

**Endpoint:** `POST /api/v1/command`

**Request Body:** Any valid JSON object

```json
{
  "type": "CUSTOM_OPERATION",
  "data": {
    "key": "value"
  }
}
```

**Response:** `200 OK`

```json
{
  "result": "OK",
  "data": { ... }
}
```

**Example:**

```bash
curl -X POST http://localhost:8080/api/v1/command \
  -H "Content-Type: application/json" \
  -d '{"type": "PING"}'
```

---

## Data Models

### JobAssignmentStatus

The lifecycle status of a job assignment:

| Status | Terminal | Description |
|--------|----------|-------------|
| `ASSIGNED` | No | Job assigned to agent, awaiting acknowledgment |
| `ACCEPTED` | No | Agent accepted the job, preparing to execute |
| `IN_PROGRESS` | No | Transfer actively in progress |
| `COMPLETED` | Yes | Job completed successfully |
| `FAILED` | Yes | Job execution failed (may be reassigned) |
| `REJECTED` | Yes | Agent rejected the assignment |
| `TIMEOUT` | Yes | Agent did not respond within timeout |
| `CANCELLED` | Yes | Job manually cancelled |

**Typical Flow:**
```
ASSIGNED → ACCEPTED → IN_PROGRESS → COMPLETED
```

**Alternative Flows:**
```
ASSIGNED → REJECTED       (agent cannot accept)
ASSIGNED → TIMEOUT        (agent unresponsive)
IN_PROGRESS → FAILED      (execution error)
Any State → CANCELLED     (manual cancellation)
```

### AgentStatus

The status of an agent in the fleet:

| Status | Description |
|--------|-------------|
| `REGISTERING` | Agent is in the process of registering |
| `ACTIVE` | Agent is healthy and accepting jobs |
| `BUSY` | Agent is at capacity |
| `DRAINING` | Agent is completing current jobs before shutdown |
| `OFFLINE` | Agent is not reachable |

---

## Error Handling

### Error Response Format

All errors return a standardized JSON response with a nested `error` object:

```json
{
  "error": {
    "code": "TRANSFER_NOT_FOUND",
    "message": "Transfer job 'xyz' not found",
    "timestamp": "2026-02-13T10:00:00Z",
    "path": "/api/v1/transfers/xyz",
    "requestId": "req-a1b2c3d4"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `code` | string | Machine-readable error code (see table below) |
| `message` | string | Human-readable error description |
| `timestamp` | string | ISO 8601 UTC timestamp of the error |
| `path` | string | Request path that caused the error |
| `requestId` | string | Correlation ID for log tracing (from `X-Request-ID` header) |

**Implementation:** [`ErrorResponse.java`](../quorus-controller/src/main/java/dev/mars/quorus/controller/http/ErrorResponse.java), [`GlobalErrorHandler.java`](../quorus-controller/src/main/java/dev/mars/quorus/controller/http/GlobalErrorHandler.java)

### Correlation ID

Every request is assigned a correlation ID via the `X-Request-ID` header:
- If the client sends `X-Request-ID`, that value is echoed back and used in logs
- If no header is provided, the server generates one (format: `req-XXXXXXXX`)
- The ID appears in all log entries for the request and in error responses

### Error Codes

#### General Errors (400-499)

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `BAD_REQUEST` | 400 | Request body is missing or malformed |
| `VALIDATION_ERROR` | 400 | Request validation failed |
| `MISSING_REQUIRED_FIELD` | 400 | A required field is missing |
| `UNAUTHORIZED` | 401 | Authentication required but not provided |
| `FORBIDDEN` | 403 | Authenticated but not authorized |
| `NOT_FOUND` | 404 | Generic resource not found |
| `METHOD_NOT_ALLOWED` | 405 | HTTP method not supported for endpoint |
| `CONFLICT` | 409 | Resource state conflict |

#### Transfer Errors

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `TRANSFER_NOT_FOUND` | 404 | Transfer job does not exist |
| `TRANSFER_INVALID` | 400 | Transfer request is invalid |
| `TRANSFER_DUPLICATE` | 409 | Transfer job already exists |
| `TRANSFER_STATE_CONFLICT` | 409 | Transfer is in a state that doesn't allow this operation |

#### Agent Errors

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `AGENT_NOT_FOUND` | 404 | Agent does not exist |
| `AGENT_INVALID` | 400 | Agent registration is invalid |
| `AGENT_DUPLICATE` | 409 | Agent is already registered |
| `AGENT_UNAVAILABLE` | 503 | Agent is offline or unresponsive |

#### Job Assignment Errors

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `ASSIGNMENT_NOT_FOUND` | 404 | Job assignment does not exist |
| `ASSIGNMENT_INVALID` | 400 | Job assignment is invalid |
| `NO_AVAILABLE_AGENTS` | 503 | No agents available to process the job |

#### Cluster/Raft Errors

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `NOT_LEADER` | 503 | Node is not the cluster leader (write operations) |
| `NO_LEADER` | 503 | Cluster has no elected leader |
| `CLUSTER_NOT_READY` | 503 | Cluster is not ready to accept requests |
| `RAFT_COMMIT_FAILED` | 500 | Failed to commit operation to Raft log |

#### Workflow Errors

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `WORKFLOW_NOT_FOUND` | 404 | Workflow does not exist |
| `WORKFLOW_INVALID` | 400 | Workflow definition is invalid |
| `WORKFLOW_EXECUTION_NOT_FOUND` | 404 | Workflow execution does not exist |

#### Tenant Errors

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `TENANT_NOT_FOUND` | 404 | Tenant does not exist |
| `TENANT_QUOTA_EXCEEDED` | 429 | Tenant quota exceeded |

#### Server Errors (500+)

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `INTERNAL_ERROR` | 500 | Unexpected internal error |
| `SERVICE_UNAVAILABLE` | 503 | Service temporarily unavailable |
| `TIMEOUT` | 504 | Request timed out |

### Exception Mapping

The `GlobalErrorHandler` maps exceptions to error codes automatically:

| Exception | Error Code |
|-----------|------------|
| `QuorusApiException` | Uses exception's `ErrorCode` |
| `IllegalArgumentException` | `VALIDATION_ERROR` (400) |
| `DecodeException` (JSON) | `BAD_REQUEST` (400) |
| `NullPointerException` | `INTERNAL_ERROR` (500) — details not exposed |
| All other exceptions | `INTERNAL_ERROR` (500) — details not exposed |

### HTTP Status Codes Summary

| Code | Meaning | When Used |
|------|---------|-----------|
| `200` | OK | Successful GET or status update |
| `201` | Created | Successful POST creating a resource |
| `400` | Bad Request | Invalid JSON or missing required fields |
| `401` | Unauthorized | Authentication required |
| `403` | Forbidden | Permission denied |
| `404` | Not Found | Resource (job, agent) does not exist |
| `409` | Conflict | Resource state conflict |
| `429` | Too Many Requests | Rate limit / quota exceeded |
| `500` | Internal Server Error | Unexpected server error |
| `503` | Service Unavailable | Node not ready or Raft consensus issue |
| `504` | Gateway Timeout | Request timed out |

---

## Complete Agent Workflow Example

This example shows the full lifecycle of an agent interacting with the controller:

```bash
# 1. Register the agent
curl -X POST http://localhost:8080/api/v1/agents/register \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "agent-nyc-001",
    "hostname": "agent-nyc-001.corp.local",
    "address": "10.0.1.50",
    "port": 9090,
    "region": "us-east"
  }'

# 2. Create a transfer job (typically done by scheduler/user)
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "jobId": "job-20260130-001",
    "sourceUri": "sftp://files.corp.com/data/report.csv",
    "destinationPath": "/archive/report.csv",
    "totalBytes": 1048576
  }'

# 3. Assign the job to the agent
curl -X POST http://localhost:8080/api/v1/jobs/assign \
  -H "Content-Type: application/json" \
  -d '{
    "jobId": "job-20260130-001",
    "agentId": "agent-nyc-001",
    "assignedAt": "2026-01-30T10:00:00Z",
    "status": "ASSIGNED"
  }'

# 4. Agent polls for jobs
curl http://localhost:8080/api/v1/agents/agent-nyc-001/jobs

# 5. Agent reports progress
curl -X POST http://localhost:8080/api/v1/jobs/job-20260130-001/status \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "agent-nyc-001",
    "status": "IN_PROGRESS",
    "bytesTransferred": 524288
  }'

# 6. Agent reports completion
curl -X POST http://localhost:8080/api/v1/jobs/job-20260130-001/status \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "agent-nyc-001",
    "status": "COMPLETED",
    "bytesTransferred": 1048576
  }'

# 7. Verify job status
curl http://localhost:8080/api/v1/transfers/job-20260130-001
```

---

## Configuration

All Quorus configuration properties can be set via multiple sources. Resolution order (highest to lowest priority):

1. **Environment Variable** — e.g., `QUORUS_HTTP_PORT=8080`
2. **System Property** — e.g., `-Dquorus.http.port=8080`
3. **Properties File** — e.g., `quorus-controller.properties`
4. **Default Value**

### Example: HTTP Port

| Source | Setting |
|--------|---------|
| Environment Variable | `QUORUS_HTTP_PORT=9090` |
| System Property | `-Dquorus.http.port=9090` |
| Properties File | `quorus.http.port=9090` |
| Default | `8080` |

### Common Properties

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `quorus.node.id` | `QUORUS_NODE_ID` | (hostname) | Unique node identifier |
| `quorus.http.port` | `QUORUS_HTTP_PORT` | `8080` | HTTP API port |
| `quorus.http.host` | `QUORUS_HTTP_HOST` | `0.0.0.0` | HTTP bind address |
| `quorus.raft.port` | `QUORUS_RAFT_PORT` | `9080` | Raft gRPC port |
| `quorus.cluster.nodes` | `QUORUS_CLUSTER_NODES` | (auto) | Cluster node list |

---

## See Also

- [QUORUS_SYSTEM_DESIGN.md](QUORUS_SYSTEM_DESIGN.md) - Architecture overview
- [QUORUS_USER_GUIDE.md](QUORUS_USER_GUIDE.md) - User guide with protocol details
- [QUORUS-DOCKER-TESTING-README.md](QUORUS-DOCKER-TESTING-README.md) - Docker testing examples
