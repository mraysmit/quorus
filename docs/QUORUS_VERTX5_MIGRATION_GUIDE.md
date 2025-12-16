# Quorus Vert.x 5.x Migration & Architecture Guide

**Version**: 1.0 (Draft)
**Date**: December 2025
**Target**: Vert.x 5.0.0+ (Java 17+)

---

## 1. Executive Summary

This guide prescribes the architecture for migrating **Quorus Controller** from a blocking, thread-per-request model (Apache HttpClient 5, JDK HttpServer) to a high-performance, non-blocking **Vert.x 5.x** reactive architecture.

**Why the change?**
The current prototype uses blocking I/O which scales poorly for the "System Design" goals of high-throughput file transfer coordination. Vert.x 5 provides the Event Loop model required for sub-millisecond Raft consensus and high-concurrency agent management.

---

## 2. Technical Stack

| Component | Current Implementation (Legacy) | New Vert.x 5 Implementation |
|---|---|---|
| **Core Runtime** | `QuorusControllerApplication` (Blocking) | `QuorusControllerVerticle` (Reactive) |
| **HTTP Server** | `com.sun.net.httpserver` (Thread/Req) | `io.vertx.core.http.HttpServer` + `Router` |
| **HTTP Client** | `Apache HttpClient 5` (Blocking/Pool) | `io.vertx.ext.web.client.WebClient` |
| **Consensus Timer** | `ScheduledExecutorService` | `vertx.setPeriodic()` |
| **Async Model** | `CompletableFuture` / Blocking | `io.vertx.core.Future` (Composable) |

---

## 3. Core Architectural Patterns

### 3.1. Single Shared Vert.x Instance
**Rule**: Never call `Vertx.vertx()` inside a component.
The application entry point (`QuorusControllerApplication`) will create **one** `Vertx` instance and pass it down.

```java
// ✅ Correct Pattern
public class QuorusControllerApplication {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new QuorusControllerVerticle());
    }
}
```

### 3.2. Dependency Injection
All components (`RaftNode`, `HttpRaftTransport`, `HttpApiServer`) must accept the `Vertx` instance in their constructor.

```java
public class RaftNode {
    private final Vertx vertx;
    
    public RaftNode(Vertx vertx, String nodeId, ...) {
        this.vertx = vertx;
    }
}
```

### 3.3. Composable Futures (No Callbacks)
Vert.x 5 replaces callbacks with composable Futures.

**❌ Legacy/Blocking:**
```java
// Avoid: Blocking get()
VoteResponse response = httpClient.execute(post).get(); 
```

**✅ Vert.x 5 Style:**
```java
return webClient.postAbs(url)
    .sendJson(voteRequest)
    .map(response -> response.bodyAsJson(VoteResponse.class))
    .onFailure(err -> logger.error("Vote failed", err));
```

---

## 4. Component Refactoring Plan

### 4.1. `HttpRaftTransport` (The Networking Layer)
**Goal**: Replace Apache HttpClient with Vert.x WebClient.

**Changes**:
1.  **Constructor**: Inject `Vertx` instance.
2.  **WebClient**: Create a cached `WebClient` shared instance.
3.  **Methods**:
    *   `sendVoteRequest`: Return `Future<VoteResponse>`.
    *   `sendAppendEntries`: Return `Future<AppendEntriesResponse>`.
    
```java
public Future<VoteResponse> sendVoteRequest(String targetId, VoteRequest req) {
    String url = getUrl(targetId, "/raft/vote");
    return webClient.postAbs(url)
        .timeout(200) // Fast fail
        .sendJson(req)
        .map(res -> {
             if (res.statusCode() != 200) throw new RuntimeException("HTTP " + res.statusCode());
             return res.bodyAsJson(VoteResponse.class);
        });
}
```

### 4.2. `HttpApiServer` (The API Layer)
**Goal**: Replace JDK HttpServer with Vert.x Router.

**Changes**:
1.  **Router**: Use `Router.router(vertx)` to define routes.
2.  **Handlers**: Create non-blocking handlers for `/raft/vote`, `/raft/append`, `/api/v1/jobs`.
3.  **Body Handling**: Use `BodyHandler.create()` for JSON parsing.

```java
public Future<Void> start() {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    
    router.post("/raft/vote").respond(ctx -> 
        raftNode.handleVoteRequest(ctx.body().asPojo(VoteRequest.class))
    );
    
    return vertx.createHttpServer()
        .requestHandler(router)
        .listen(port)
        .mapEmpty();
}
```

### 4.3. `RaftNode` (The Consensus Engine)
**Goal**: Make the core logic reactive.

**Changes**:
1.  **Timers**: Replace `ScheduledExecutorService` with `vertx.setPeriodic`.
    *   *Note*: Ensure election timers are reset correctly using `vertx.cancelTimer()`.
2.  **State Machine**: Ensure state transitions happen on the Event Loop to avoid concurrency bugs (no `synchronized` needed if single-threaded event loop!).

### 4.4. `quorus-core` (Transfer Engine)
**Goal**: Replace `ThreadPoolExecutor` with Vert.x async I/O.

**Changes**:
1.  **Dependency**: Implement `VertxTransferEngine` implementing `TransferEngine`.
2.  **Execution**: Instead of `CompletableFuture.supplyAsync`, use `vertx.executeBlocking` (for CPU heavy) or `FileSystem`/`WebClient` (for I/O).
3.  **Protocols**: Update `ProtocolFactory` to produce non-blocking protocols (e.g., `VertxHttpProtocol`).

### 4.5. `quorus-tenant` (Tenant Service)
**Goal**: Prepare for Reactive Database Access (`vertx-pg-client`).

**Changes**:
1.  **Service Interface**: Update `TenantService` to return `Future<Tenant>` instead of blocking objects.
2.  **Implementation**: 
    *   *Phase 1*: Keep `SimpleTenantService` in-memory but wrap results in `Future.succeededFuture()`.
    *   *Phase 2*: Replace Map with `PgPool` and SQL queries (using `vertx-pg-client`).

### 4.6. `quorus-workflow` (Workflow Engine)
**Goal**: Replace `CachedThreadPool` with Vert.x Composition.

**Changes**:
1.  **Execution Model**: Rewrite `SimpleWorkflowEngine` to use `Future.compose()` chains instead of blocking `future.get()`.
2.  **Concurrency**: Use `Future.all()` for parallel execution of transfer groups.
3.  **Lifecycle**: Remove `ExecutorService` management.

---

## 5. Migration Checklist

### 4.7. `quorus-api` (REST API Layer)
**Goal**: Migrate from Quarkus to Vert.x Web.
*Current State*: The `pom.xml` indicates this module uses Quarkus. This is a significant architecture mismatch if the goal is a pure Vert.x 5 application.

**Changes**:
1.  **Framework**: Remove Quarkus dependencies.
2.  **Implementation**: Re-implement as a Vert.x Web module using `Router` and `Verticle`.
3.  **Data Transfer**: Reuse the core `RaftNode` and `TransferEngine` via dependency injection (ensure strict module boundaries).

### 4.8. `quorus-integration-examples`
**Goal**: Update examples to demonstrate Vert.x Client usage.

**Changes**:
1.  **Clients**: Update examples to use `WebClient` instead of blocking `HttpURLConnection` or `Apache HttpClient` if present.
2.  **Async**: Demonstrate proper async coordination in client code (e.g., using `CompletableFuture` wrapping Vert.x client for simpler client-side code).

---

## 5. Migration Checklist

### Core Infrastructure
1.  [ ] **BOM & Dependencies**: Update `pom.xml` with `vertx-dependencies` (5.x), `vertx-core`, `vertx-web`, `vertx-web-client`.
2.  [ ] **Entry Point**: Create `QuorusControllerVerticle`.

### Controller Module
3.  [ ] **Raft Transport**: Rewrite `HttpRaftTransport.java`.
4.  [ ] **API Server**: Rewrite `HttpApiServer.java`.
5.  [ ] **Raft Core**: Update `RaftNode.java` to use `Vertx` timers and remove `ExecutorService`.

### Core/Workflow Modules
6.  [ ] **Transfer Engine**: Create `VertxTransferEngine` (non-blocking).
7.  [ ] **Workflow Engine**: Refactor `SimpleWorkflowEngine` to use `Future` composition.
8.  [ ] **Tenant Service**: Refactor `TenantService` interface to return `Future<T>`.

### API & Examples
9.  [ ] **API Module**: Migrate `quorus-api` from Quarkus to Vert.x Web.
10. [ ] **Examples**: Update integration examples to use `WebClient`.

### Cleanup
11. [ ] **Cleanup**: Remove `Apache HttpClient`, `JDK Http Server`, `ExecutorService`, and `Quarkus` dependencies.

---

## 6. Critical Reminders (From PeeGeeQ Reviews)

*   **Avoid Blocking**: Never call `Thread.sleep()` or blocking DB calls on the event loop.
*   **Fail Fast**: Validate inputs (ports, cluster configs) immediately on startup.
*   **Graceful Shutdown**: Implement `stopReactive()` to close the HTTP server and WebClient cleanly.
