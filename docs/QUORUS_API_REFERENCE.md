# Quorus HTTP API Reference

**Version:** 2.0  
**Date:** 2026-03-14  
**Implementation:** `quorus-controller/src/main/java/dev/mars/quorus/controller/http/HttpApiServer.java`

This document reflects the endpoints currently registered by the embedded controller HTTP server.

## Base URL

`http://{controller-host}:8080`

## Request Model

- Content type: `application/json` for JSON request bodies
- All write requests are subject to leader guarding
- The API currently does **not** enforce built-in authentication

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

Registers an agent through a Raft-replicated write.

### `POST /api/v1/agents/heartbeat`

Updates agent health and capacity state.

### `GET /api/v1/agents`

Lists agents from controller state.

### `GET /api/v1/agents/:agentId/jobs`

Returns jobs assigned to a specific agent.

## Transfer Endpoints

### `POST /api/v1/transfers`

Creates a transfer job.

### `GET /api/v1/transfers/:jobId`

Returns transfer job details.

### `DELETE /api/v1/transfers/:jobId`

Deletes a transfer job.

## Job Status Endpoint

### `POST /api/v1/jobs/:jobId/status`

Updates status for an existing transfer job.

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