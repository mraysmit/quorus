# Quorus Vert.x 5.x Migration & Architecture Guide

**Version**: 2.0 (Enhanced with PeeGeeQ Production Patterns)
**Date**: December 2025
**Target**: Vert.x 5.0.0+ (Java 17+)
**Status**: Production-Ready Migration Guide

---

## 1. Executive Summary

This guide prescribes the architecture for migrating **Quorus Controller** from a blocking, thread-per-request model (Apache HttpClient 5, JDK HttpServer) to a high-performance, non-blocking **Vert.x 5.x** reactive architecture.

**Why the change?**
The current prototype uses blocking I/O which scales poorly for the "System Design" goals of high-throughput file transfer coordination. Vert.x 5 provides the Event Loop model required for sub-millisecond Raft consensus and high-concurrency agent management.

### 1.1. What's New in Version 2.0

This guide has been **significantly enhanced** with production-tested patterns from the **PeeGeeQ Vert.x 5.x migration**, which achieved:
- **483% performance improvement** through proper pool configuration
- **Zero resource leaks** through shutdown coordination patterns
- **Sub-10ms latency** for database operations
- **Production-grade reliability** with comprehensive error handling

**Key Additions:**
1. **Critical Anti-Patterns Section** - Learn what NOT to do (never create Vert.x in factories!)
2. **Pool Configuration Best Practices** - Research-based settings that achieved 483% improvement
3. **Shutdown Coordination Patterns** - Avoid race conditions during shutdown
4. **Event Loop Detection Guards** - Prevent deadlocks from blocking operations
5. **Comprehensive Testing Strategy** - TestContainers, reactive tests, performance validation
6. **Security Best Practices** - Password sanitization, SSL configuration, input validation
7. **Troubleshooting Guide** - Common errors and their solutions
8. **Production Deployment Checklist** - Ensure nothing is missed

### 1.2. Migration Confidence

By following this enhanced guide, you will avoid the common pitfalls that plague Vert.x migrations:
- ❌ Thread leaks from multiple Vert.x instances
- ❌ Pool exhaustion from incorrect sizing
- ❌ Shutdown race conditions
- ❌ Event loop blocking and deadlocks
- ❌ Resource leaks from improper cleanup
- ❌ Poor performance from default configurations

Instead, you'll achieve:
- ✅ Single shared Vert.x instance with proper lifecycle
- ✅ Optimized pool configuration (100+ connections, shared pools)
- ✅ Graceful shutdown with no resource leaks
- ✅ Non-blocking reactive patterns throughout
- ✅ Production-grade error handling and monitoring
- ✅ High performance (>1000 ops/sec, <10ms latency)

---

## 1.3. Quick Reference - Critical Patterns

### ✅ DO: Inject Vert.x Instance
```java
public RaftNode(Vertx vertx, Pool pool, String nodeId) {
    this.vertx = Objects.requireNonNull(vertx);
}
```

### ❌ DON'T: Create Vert.x in Components
```java
// NEVER DO THIS!
private final Vertx vertx = Vertx.vertx();
```

### ✅ DO: Configure Pools Properly
```java
PoolOptions poolOptions = new PoolOptions()
    .setMaxSize(100)              // NOT 4!
    .setShared(true)              // CRITICAL
    .setMaxWaitQueueSize(1000);   // 10x pool size
```

### ❌ DON'T: Use Default Pool Settings
```java
// Default maxSize=4 is too small!
new PoolOptions() // Missing shared=true and proper sizing
```

### ✅ DO: Use Vert.x Timers
```java
long timerId = vertx.setPeriodic(30000, id -> {
    vertx.executeBlocking(() -> doWork());
});
vertx.cancelTimer(timerId); // On shutdown
```

### ❌ DON'T: Use ScheduledExecutorService
```java
// Bypasses Vert.x context!
ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
```

### ✅ DO: Check Shutdown State
```java
if (closed.get()) {
    return Future.failedFuture(new IllegalStateException("Closed"));
}
```

### ❌ DON'T: Ignore Shutdown Coordination
```java
// Race condition - pool might be closing!
pool.withConnection(conn -> ...);
```

---

## 2. Technical Stack

| Component | Current Implementation (Legacy) | New Vert.x 5 Implementation |
|---|---|---|
| **Core Runtime** | `QuorusControllerApplication` (Blocking) | `QuorusControllerVerticle` (Reactive) |
| **Raft Transport** | `Apache HttpClient` (Blocking) | `Vert.x gRPC` (Protobuf/HTTP2) |
| **Public API** | `JDK HttpServer` (Blocking) | `Vert.x Web Router` (REST) |
| **HTTP Client** | `Apache HttpClient` (Blocking) | `WebClient` (External Integration only) |
| **Consensus Timer** | `ScheduledExecutorService` | `vertx.setPeriodic()` |
| **Async Model** | `CompletableFuture` / Blocking | `io.vertx.core.Future` (Composable) |

---

## 3. Core Architectural Patterns

### 3.1. Hybrid Architecture (gRPC + REST)
The system will use a hybrid approach:
*   **Internal (Raft)**: **gRPC** for high-performance, strongly-typed consensus.
*   **External (API)**: **Vert.x Web** for easy JSON/REST integration with clients.

### 3.2. Dependency Injection (CRITICAL)
All components (`RaftNode`, `GrpcRaftTransport`, `HttpApiServer`) must accept the `Vertx` instance in their constructor.

**✅ CORRECT Pattern:**
```java
public class RaftNode {
    private final Vertx vertx;

    public RaftNode(Vertx vertx, String nodeId, ...) {
        this.vertx = Objects.requireNonNull(vertx, "Vertx instance cannot be null");
    }
}
```

**❌ NEVER DO THIS:**
```java
// ANTI-PATTERN: Creating Vert.x in components/factories
public class RaftNode {
    private final Vertx vertx = Vertx.vertx(); // Creates new event-loop threads!
}

// ANTI-PATTERN: Creating Vert.x in factory methods
public RaftTransport createTransport() {
    return new GrpcRaftTransport(Vertx.vertx()); // Thread leak!
}
```

**Why This Matters:**
- Creating `Vertx.vertx()` spawns new event-loop threads (2 × CPU cores by default)
- Creates independent lifecycle you don't control
- Causes thread leaks, double metrics, unreliable shutdown
- Wastes resources with duplicate thread pools

**Fallback Pattern (if Vertx not provided):**
```java
public QuorusControllerVerticle(Vertx vertx, ...) {
    // Prefer: provided instance > current context > new instance
    this.vertx = (vertx != null) ? vertx
        : (Vertx.currentContext() != null ? Vertx.currentContext().owner() : Vertx.vertx());

    if (vertx != null) {
        logger.info("Using provided Vert.x instance");
    } else if (Vertx.currentContext() != null) {
        logger.info("Using Vert.x from current context");
    } else {
        logger.info("Created new Vert.x instance");
    }
}
```

### 3.3. Composable Futures (No Callbacks)
Vert.x 5 replaces callbacks with composable Futures.

---

## 3.4. Critical Anti-Patterns to Avoid

### ❌ Anti-Pattern 1: Never Create Vert.x in Factories
```java
// WRONG - Creates new event loops and thread pools
public class TransportFactory {
    public RaftTransport createTransport() {
        return new GrpcRaftTransport(Vertx.vertx()); // NEVER DO THIS
    }
}

// CORRECT - Inject shared Vert.x instance
public class TransportFactory {
    private final Vertx vertx;

    public TransportFactory(Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    public RaftTransport createTransport() {
        return new GrpcRaftTransport(vertx); // Use injected instance
    }
}
```

### ❌ Anti-Pattern 2: Don't Mix ScheduledThreadPoolExecutor with Vert.x
```java
// WRONG - Bypasses Vert.x context
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
scheduler.scheduleAtFixedRate(task, 0, 30, SECONDS);

// CORRECT - Use Vert.x timers
long timerId = vertx.setPeriodic(TimeUnit.SECONDS.toMillis(30), id -> {
    vertx.executeBlocking(() -> {
        // Potentially blocking work here
        return null;
    });
});

// Remember to cancel on shutdown
vertx.cancelTimer(timerId);
```

### ❌ Anti-Pattern 3: Avoid Blocking on Event Loop
```java
// WRONG - Can deadlock
public void start() {
    startReactive().toCompletionStage().toCompletableFuture().get(30, SECONDS);
}

// CORRECT - Guard against event loop blocking
public void start() {
    if (Vertx.currentContext() != null && Vertx.currentContext().isEventLoopContext()) {
        throw new IllegalStateException("Do not call blocking start() on event-loop thread");
    }
    // Proceed with blocking call (or better: use startReactive() directly)
}
```

### ❌ Anti-Pattern 4: Don't Use JDBC Patterns in Reactive Code
```java
// WRONG - JDBC-style configuration that Vert.x ignores
private final long connectionTimeout; // ambiguous units
private final int minimumIdle;        // not used by Vert.x
private final boolean autoCommit;     // JDBC-only concept

// CORRECT - Vert.x-native configuration
private final Duration connectionTimeout;
private final int maxWaitQueueSize; // Vert.x pool concept
private final SslMode sslMode;      // Vert.x SSL configuration
```

### ❌ Anti-Pattern 5: Per-Request Parser Instantiation
```java
// WRONG - Creates expensive parser on every request
public class YamlWorkflowDefinitionParser {
    private final Yaml yaml;

    public YamlWorkflowDefinitionParser() {
        this.yaml = new Yaml(new SafeConstructor(loaderOptions));  // EXPENSIVE!
    }
}

// CORRECT - Static singleton parser (thread-safe)
public class YamlWorkflowDefinitionParser {
    private static final Yaml YAML_PARSER = createYamlParser();

    private static Yaml createYamlParser() {
        LoaderOptions loaderOptions = new LoaderOptions();
        return new Yaml(new SafeConstructor(loaderOptions));
    }
}
```

**Why This Matters**:
- Parser creation is expensive (classpath scanning, reflection, introspection)
- At 1000 req/sec: 1000 parser instances/sec = catastrophic overhead
- APEX achieved 70-80% performance improvement with this fix
- SnakeYAML Yaml is thread-safe (like Jackson ObjectMapper)
- **Quorus Fix**: Applied December 16, 2025 - all 134 tests passing

---

## 4. Component Refactoring Plan

### 4.1. `RaftTransport` (The Networking Layer)
**Goal**: Replace HttpRaftTransport with `GrpcRaftTransport`.

**Changes**:
1.  **Protocol**: Define `raft.proto` with `VoteRequest`, `VoteResponse`, `AppendEntriesRequest`, etc.
2.  **Server**: Implement `Verticle` starting a `GrpcServer`.
3.  **Client**: Use `GrpcClient` stubs for inter-node communication.
    
```java
// raft.proto
service RaftService {
  rpc RequestVote (VoteRequest) returns (VoteResponse);
  rpc AppendEntries (AppendEntriesRequest) returns (AppendEntriesResponse);
}
```

### 4.2. `HttpApiServer` (The API Layer)
**Goal**: Replace JDK HttpServer with Vert.x Router for **Public API only**.

**Changes**:
1.  **Router**: Use `Router.router(vertx)` to define routes.
2.  **Handlers**: Create non-blocking handlers for `/api/v1/jobs`.
3.  **Body Handling**: Use `BodyHandler.create()` for JSON parsing.

```java
public Future<Void> start() {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    
    // Public API endpoints
    router.post("/api/v1/jobs").respond(this::handleJobSubmission);
    
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

**Timer Management Pattern:**
```java
public class RaftNode {
    private final Vertx vertx;
    private long electionTimerId = 0;
    private long heartbeatTimerId = 0;

    private void startElectionTimer() {
        cancelElectionTimer(); // Cancel existing timer

        long timeout = randomElectionTimeout(); // 150-300ms
        electionTimerId = vertx.setTimer(timeout, id -> {
            // Election timeout - become candidate
            becomeCandidate();
        });
    }

    private void cancelElectionTimer() {
        if (electionTimerId != 0) {
            vertx.cancelTimer(electionTimerId);
            electionTimerId = 0;
        }
    }

    private void startHeartbeatTimer() {
        if (heartbeatTimerId != 0) return; // Already running

        heartbeatTimerId = vertx.setPeriodic(HEARTBEAT_INTERVAL_MS, id -> {
            sendHeartbeats();
        });
    }

    private void stopAllTimers() {
        cancelElectionTimer();
        if (heartbeatTimerId != 0) {
            vertx.cancelTimer(heartbeatTimerId);
            heartbeatTimerId = 0;
        }
    }
}
```

### 4.4. `quorus-core` (Transfer Engine)
**Goal**: converting blocking I/O to Reactive Streams.

**Refactoring `TransferProtocol` Interface**:
```java
// BEFORE (Blocking)
TransferResult transfer(TransferRequest request, TransferContext context) throws TransferException;

// AFTER (Reactive)
Future<TransferResult> transfer(TransferRequest request, TransferContext context);
```

**Refactoring `HttpTransferProtocol`**:
Use `WebClient` piping directly to `AsyncFile`.
```java
public Future<TransferResult> transfer(TransferRequest req, TransferContext ctx) {
    return vertx.fileSystem().open(destPath, new OpenOptions())
        .compose(asyncFile -> 
            webClient.getAbs(req.getUri())
                .as(BodyCodec.pipe(asyncFile))
                .send()
                .map(response -> {
                    // Logic to build TransferResult
                })
        );
}
```

### 4.5. `quorus-tenant` (Tenant Service)
**Goal**: Remove synchronization locks.
**Concept**: In Vert.x, a Verticle runs on a single thread. If `TenantService` is kept isolated within a Verticle, you **do not need synchronization**.

1.  **Remove** `synchronized(lock)` blocks.
2.  **Return** `Future<T>` for all methods.
3.  **Note**: For an in-memory prototype, simply wrap results: `return Future.succeededFuture(tenant);`.

### 4.6. `quorus-workflow` (Workflow Engine)
**Goal**: Parallel Execution with `Future.all`.
Instead of submitting tasks to an `ExecutorService`, create a list of Futures.

```java
List<Future> batchFutures = batch.stream()
    .map(transfer -> transferEngine.submit(transfer))
    .collect(Collectors.toList());

return Future.all(batchFutures)
    .onSuccess(composite -> { /* Check all results */ });
```

---

## 5. API Migration Logic (Quorus-API)

**Goal**: Map JAX-RS annotations to Vert.x Router.

| JAX-RS (Old) | Vert.x Web (New) |
|---|---|
| `@Path("/transfers")` | `router.route("/transfers")` |
| `@POST` | `.method(HttpMethod.POST)` |
| `@PathParam("id")` | `ctx.pathParam("id")` |
| `@Inject` | Constructor Injection (Pass Service instance) |

**Example Mapping**:
```java
// JAX-RS
@POST @Path("/{id}")
public Response create(@PathParam("id") String id, Dto dto) { ... }

// Vert.x
router.post("/:id").handler(ctx -> {
    String id = ctx.pathParam("id");
    Dto dto = ctx.body().asPojo(Dto.class);
    // ... logic
    ctx.json(responseDto);
});
```

---

## 6. Health Checks & Monitoring

### 6.1. Real Health Check Implementation

**❌ WRONG - Synthetic health check:**
```java
public boolean isHealthy() {
    return pool != null; // Doesn't actually test the database!
}
```

**✅ CORRECT - Real database health check:**
```java
public Future<Boolean> checkHealth() {
    if (pool == null) return Future.succeededFuture(false);

    return pool.withConnection(conn ->
        conn.query("SELECT 1").execute().map(rs -> true)
    ).recover(err -> {
        logger.warn("Health check failed: {}", err.getMessage());
        return Future.succeededFuture(false);
    });
}
```

### 6.2. Health Check Manager Pattern

```java
public class HealthCheckManager {
    private final Vertx vertx;
    private final Pool pool;
    private final Duration checkInterval;
    private long timerId = 0;
    private volatile boolean healthy = false;

    public Future<Void> startReactive() {
        if (timerId != 0) return Future.succeededFuture();

        timerId = vertx.setPeriodic(checkInterval.toMillis(), id -> {
            pool.withConnection(conn ->
                conn.query("SELECT 1").execute()
            ).onSuccess(rs -> {
                healthy = true;
                logger.debug("Health check passed");
            }).onFailure(err -> {
                healthy = false;
                logger.warn("Health check failed: {}", err.getMessage());
            });
        });

        return Future.succeededFuture();
    }

    public Future<Void> stopReactive() {
        if (timerId != 0) {
            vertx.cancelTimer(timerId);
            timerId = 0;
        }
        return Future.succeededFuture();
    }

    public boolean isHealthy() {
        return healthy;
    }
}
```

---

## 7. Migration Checklist

### Phase 1: Core Infrastructure
1.  [ ] **BOM & Dependencies**: Update `pom.xml` with `vertx-dependencies` (5.x), `vertx-core`, `vertx-web`, `vertx-web-client`, `vertx-grpc`.
2.  [ ] **Entry Point**: Create `QuorusControllerVerticle`.
3.  [ ] **Vert.x Instance Management**: Ensure single shared Vert.x instance, never create in factories.
4.  [ ] **Pool Configuration**: Configure with `setShared(true)`, proper sizing, and pipelining.
5.  [ ] **Event Loop Detection**: Add guards to all blocking methods.

### Phase 2: Controller Module
6.  [ ] **Raft Transport**: Create `raft.proto` and implement gRPC Service.
7.  [ ] **API Server**: Rewrite `HttpApiServer` as `PublicApiVerticle`.
8.  [ ] **Raft Core**: Update `RaftNode.java` to use `Vertx` timers and remove `ExecutorService`.
9.  [ ] **Timer Management**: Implement proper timer cancellation in shutdown.
10. [ ] **Health Checks**: Implement real database health checks.

### Phase 3: Core/Workflow Modules
11. [ ] **Transfer Interface**: Update `TransferProtocol` to return `Future`.
12. [ ] **Http Protocol**: Re-implement `HttpTransferProtocol` using `WebClient` + `AsyncFile`.
13. [ ] **Workflow Engine**: Refactor `SimpleWorkflowEngine` to use `Future.all()`.
14. [ ] **Tenant Service**: Remove `synchronized`, return `Future`.
15. [ ] **Shutdown Coordination**: Add `closed` state checking to all async operations.

### Phase 4: API & Examples
16. [ ] **API Module**: Rewrite `TransferResource` logic into `Router` handlers.
17. [ ] **Examples**: Update integration examples to use `WebClient`.
18. [ ] **Testing**: Set up TestContainers with PostgreSQL for integration tests.

### Phase 5: Cleanup & Validation
19. [ ] **Cleanup**: Remove `Apache HttpClient`, `JDK Http Server`, `ExecutorService`, `Quarkus`, and blocking I/O code.
20. [ ] **Remove JDBC Artifacts**: Deprecate JDBC methods, remove HikariCP dependencies.
21. [ ] **Security**: Sanitize passwords in `toString()` methods.
22. [ ] **Performance Testing**: Validate pool sizing and throughput.
23. [ ] **Shutdown Testing**: Verify clean shutdown with no resource leaks.

---

## 7. Pool Configuration & Performance Optimization

### 7.1. Connection Pool Configuration (CRITICAL)

**Research-Based Pool Configuration:**
```java
// Based on PeeGeeQ production testing - achieved 483% performance improvement
PoolOptions poolOptions = new PoolOptions()
    .setMaxSize(100)              // NOT default 4! Start at 16-32, tune to 100+ for high load
    .setShared(true)              // CRITICAL: Share one pool across all verticles
    .setName("quorus-raft-pool")  // Named pools for monitoring
    .setMaxWaitQueueSize(1000)    // 10x pool size for burst handling (Vert.x 5 default is -1/unbounded)
    .setConnectionTimeout(30000)  // 30 seconds to acquire from pool
    .setIdleTimeout(600000);      // 10 minutes idle timeout

PgConnectOptions connectOptions = new PgConnectOptions()
    .setHost(config.getHost())
    .setPort(config.getPort())
    .setDatabase(config.getDatabase())
    .setUser(config.getUsername())
    .setPassword(config.getPassword())
    .setPipeliningLimit(256)      // Enable connection-level pipelining
    .setConnectTimeout(30000)     // TCP/DB connect timeout
    .setCachePreparedStatements(true)
    .setPreparedStatementCacheMaxSize(256);

Pool pool = PgBuilder.pool()
    .with(poolOptions)
    .connectingTo(connectOptions)
    .using(vertx)  // Use injected Vert.x instance
    .build();
```

**Why These Settings Matter:**
- **maxSize=100**: Default of 4 is too small for high-concurrency Raft operations
- **shared=true**: Prevents duplicate pools and connection waste
- **maxWaitQueueSize=1000**: Handles burst traffic during leader elections
- **pipeliningLimit=256**: Enables multiple in-flight commands on same connection
- **Named pools**: Essential for monitoring and debugging

### 7.2. Idempotent Pool Creation Pattern

```java
public class ConnectionManager {
    private final ConcurrentHashMap<String, Pool> pools = new ConcurrentHashMap<>();

    public Pool getOrCreatePool(String nodeId, PoolConfig config) {
        return pools.computeIfAbsent(nodeId, id -> {
            try {
                Pool pool = createPool(config);
                logger.info("Created pool for node '{}'", id);
                return pool;
            } catch (Exception e) {
                pools.remove(id); // Clean up on failure
                throw new RuntimeException("Failed to create pool for node: " + id, e);
            }
        });
    }

    public Future<Void> removePoolAsync(String nodeId) {
        Pool pool = pools.remove(nodeId);
        if (pool == null) return Future.succeededFuture();

        return pool.close()
            .onSuccess(v -> logger.info("Closed pool for node '{}'", nodeId))
            .onFailure(err -> logger.warn("Error closing pool: {}", err.getMessage()))
            .mapEmpty();
    }

    public Future<Void> closeAllAsync() {
        var futures = pools.keySet().stream()
            .map(this::removePoolAsync)
            .toList();

        pools.clear();
        return Future.all(futures)
            .onSuccess(v -> logger.info("All pools closed"))
            .mapEmpty();
    }
}
```

### 7.3. Performance Tuning Guidelines

**Event Loop Configuration:**
```java
VertxOptions vertxOptions = new VertxOptions()
    .setEventLoopPoolSize(16)     // ≈ CPU cores for I/O-bound work
    .setWorkerPoolSize(32)         // For blocking operations
    .setPreferNativeTransport(true); // Use native epoll/kqueue

Vertx vertx = Vertx.vertx(vertxOptions);
```

**Batch Operations for Raft Log:**
```java
// Use executeBatch for multiple log entries
public Future<Void> appendEntries(List<LogEntry> entries) {
    List<Tuple> batchParams = entries.stream()
        .map(entry -> Tuple.of(entry.getTerm(), entry.getIndex(), entry.getData()))
        .collect(Collectors.toList());

    return pool.preparedQuery(INSERT_LOG_SQL)
        .executeBatch(batchParams)
        .mapEmpty();
}
```

---

## 8. Shutdown Coordination & Lifecycle Management

### 8.1. Graceful Shutdown Pattern

**The Problem:** Race conditions between background tasks and shutdown can cause errors.

**The Solution:** Shutdown coordination with state checking.

```java
public class RaftNode {
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private long electionTimerId = 0;
    private long heartbeatTimerId = 0;

    public Future<Void> stopReactive() {
        if (closed.getAndSet(true)) {
            return Future.succeededFuture(); // Already stopped
        }

        logger.info("Stopping Raft node...");

        // 1. Stop accepting new operations
        stopAllTimers();

        // 2. Wait for in-flight operations (if needed)
        // 3. Close resources
        return Future.succeededFuture()
            .onSuccess(v -> logger.info("Raft node stopped"));
    }

    private void stopAllTimers() {
        if (electionTimerId != 0) {
            vertx.cancelTimer(electionTimerId);
            electionTimerId = 0;
        }
        if (heartbeatTimerId != 0) {
            vertx.cancelTimer(heartbeatTimerId);
            heartbeatTimerId = 0;
        }
    }

    // Check shutdown state before operations
    private Future<Void> appendLog(LogEntry entry) {
        if (closed.get()) {
            logger.debug("Node is closed, skipping log append");
            return Future.failedFuture(new IllegalStateException("Node is closed"));
        }

        return pool.withTransaction(conn -> {
            // Double-check after acquiring connection
            if (closed.get()) {
                return Future.failedFuture(new IllegalStateException("Node is closed"));
            }

            return conn.preparedQuery(INSERT_SQL).execute(params);
        }).recover(err -> {
            // Handle shutdown errors gracefully
            if (closed.get() && err.getMessage().contains("closed")) {
                logger.debug("Pool closed during shutdown - expected");
                return Future.succeededFuture();
            }
            return Future.failedFuture(err);
        });
    }
}
```

### 8.2. Lifecycle Symmetry

```java
public class QuorusControllerVerticle extends AbstractVerticle {
    private RaftNode raftNode;
    private GrpcServer grpcServer;
    private HttpServer httpServer;

    @Override
    public void start(Promise<Void> startPromise) {
        // Start in order: infrastructure → services → servers
        initializeRaftNode()
            .compose(v -> startGrpcServer())
            .compose(v -> startHttpServer())
            .onSuccess(v -> {
                logger.info("Quorus Controller started");
                startPromise.complete();
            })
            .onFailure(startPromise::fail);
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        // Stop in reverse order: servers → services → infrastructure
        stopHttpServer()
            .compose(v -> stopGrpcServer())
            .compose(v -> raftNode.stopReactive())
            .onSuccess(v -> {
                logger.info("Quorus Controller stopped");
                stopPromise.complete();
            })
            .onFailure(stopPromise::fail);
    }
}
```

---

## 10. Testing Strategy

### 10.1. TestContainers Setup

**Use standardized PostgreSQL container:**
```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RaftNodeIntegrationTest {
    private static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15.13-alpine3.20")
            .withDatabaseName("quorus_test")
            .withUsername("test")
            .withPassword("test");

    private Vertx vertx;
    private Pool pool;

    @BeforeAll
    void setUp() {
        postgres.start();

        vertx = Vertx.vertx();

        PgConnectOptions connectOptions = new PgConnectOptions()
            .setHost(postgres.getHost())
            .setPort(postgres.getFirstMappedPort())
            .setDatabase(postgres.getDatabaseName())
            .setUser(postgres.getUsername())
            .setPassword(postgres.getPassword());

        PoolOptions poolOptions = new PoolOptions()
            .setMaxSize(8)
            .setMaxWaitQueueSize(80);

        pool = PgBuilder.pool()
            .with(poolOptions)
            .connectingTo(connectOptions)
            .using(vertx)
            .build();
    }

    @AfterAll
    void tearDown() {
        if (pool != null) pool.close();
        if (vertx != null) vertx.close();
        postgres.stop();
    }

    @Test
    void testRaftLogAppend() {
        RaftNode node = new RaftNode(vertx, pool, "node1");

        node.appendLog(new LogEntry(1, 1, "data"))
            .onSuccess(v -> {
                // Verify log entry
            })
            .toCompletionStage()
            .toCompletableFuture()
            .join();
    }
}
```

### 10.2. Reactive Test Patterns

**Use VertxTestContext for async tests:**
```java
@ExtendWith(VertxExtension.class)
public class RaftTransportTest {

    @Test
    void testVoteRequest(Vertx vertx, VertxTestContext testContext) {
        GrpcRaftTransport transport = new GrpcRaftTransport(vertx, config);

        VoteRequest request = VoteRequest.newBuilder()
            .setTerm(1)
            .setCandidateId("node1")
            .build();

        transport.requestVote("node2", request)
            .onSuccess(response -> {
                testContext.verify(() -> {
                    assertEquals(1, response.getTerm());
                    assertTrue(response.getVoteGranted());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
}
```

### 10.3. Performance Testing

```java
@Test
void testRaftLogThroughput() {
    int messageCount = 1000;
    long startTime = System.currentTimeMillis();

    List<Future> futures = IntStream.range(0, messageCount)
        .mapToObj(i -> raftNode.appendLog(createEntry(i)))
        .collect(Collectors.toList());

    Future.all(futures)
        .onSuccess(v -> {
            long duration = System.currentTimeMillis() - startTime;
            double throughput = messageCount * 1000.0 / duration;
            logger.info("Raft log throughput: {} entries/sec", throughput);
            assertTrue(throughput > 500, "Should achieve >500 entries/sec");
        })
        .toCompletionStage()
        .toCompletableFuture()
        .join();
}
```

### 10.4. Shutdown Testing

```java
@Test
void testGracefulShutdown() {
    RaftNode node = new RaftNode(vertx, pool, "node1");

    // Start node
    node.startReactive()
        .compose(v -> {
            // Perform some operations
            return node.appendLog(createEntry(1));
        })
        .compose(v -> {
            // Stop node
            return node.stopReactive();
        })
        .compose(v -> {
            // Verify no operations accepted after shutdown
            return node.appendLog(createEntry(2));
        })
        .onSuccess(v -> fail("Should reject operations after shutdown"))
        .onFailure(err -> {
            assertTrue(err.getMessage().contains("closed"));
        })
        .toCompletionStage()
        .toCompletableFuture()
        .join();
}
```

---

## 11. Security Best Practices

### 11.1. Password Handling

**Never log passwords:**
```java
public class DatabaseConfig {
    private final String host;
    private final String database;
    private final String username;
    private final String password; // Consider char[] for better security

    @Override
    public String toString() {
        return "DatabaseConfig{" +
            "host='" + host + '\'' +
            ", database='" + database + '\'' +
            ", username='" + username + '\'' +
            // NO PASSWORD HERE!
            '}';
    }
}
```

### 11.2. SSL Configuration

```java
PgConnectOptions connectOptions = new PgConnectOptions()
    .setHost(config.getHost())
    .setPort(config.getPort())
    .setDatabase(config.getDatabase())
    .setUser(config.getUsername())
    .setPassword(config.getPassword())
    .setSslMode(SslMode.REQUIRE)  // Require SSL in production
    .setTrustAll(false)            // Don't trust all certificates
    .setPemTrustOptions(new PemTrustOptions()
        .addCertPath("/path/to/ca-cert.pem"));
```

### 11.3. Input Validation

```java
public class RaftNode {
    public RaftNode(Vertx vertx, Pool pool, String nodeId) {
        this.vertx = Objects.requireNonNull(vertx, "Vertx cannot be null");
        this.pool = Objects.requireNonNull(pool, "Pool cannot be null");

        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must be non-blank");
        }
        this.nodeId = nodeId;
    }
}
```

---

## 12. Troubleshooting Guide

### 12.1. Common Error Messages

**"You're already on a Vert.x context, are you sure you want to create a new Vertx instance?"**
- **Cause**: Creating `Vertx.vertx()` in components that already run on Vert.x context
- **Fix**: Use dependency injection to pass shared Vert.x instance

**"Pool is closed"**
- **Cause**: Attempting to use pool after it's been closed
- **Fix**: Ensure proper lifecycle management and check `closed` state before operations

**"Connection pool reached max wait queue size"**
- **Cause**: Pool size too small or wait queue bounded too low
- **Fix**: Increase `maxSize` and `maxWaitQueueSize` in PoolOptions

**"Connection timeout"**
- **Cause**: Database connection taking too long
- **Fix**: Tune `connectionTimeout` in PoolOptions, check database health

### 12.2. Performance Issues

**High thread count:**
- **Cause**: Multiple Vert.x instances creating duplicate thread pools
- **Fix**: Consolidate to single shared Vert.x instance

**Memory leaks:**
- **Cause**: Unbounded connection queues or unclosed resources
- **Fix**: Set `maxWaitQueueSize`, implement proper resource cleanup

**Slow response times:**
- **Cause**: Blocking operations on event loop
- **Fix**: Use `executeBlocking` for I/O operations, add event loop detection

**Poor Raft consensus performance:**
- **Cause**: Small pool size, no pipelining, blocking timers
- **Fix**: Increase pool size to 100+, enable pipelining, use Vert.x timers

### 12.3. Debugging Techniques

```java
// Enable detailed logging for troubleshooting
logger.debug("Using Vert.x instance: {} for component: {}",
             System.identityHashCode(vertx), getClass().getSimpleName());

// Track resource usage
logger.info("Active pools: {}, Active connections: {}",
            pools.size(), getTotalActiveConnections());

// Monitor event loop utilization
vertx.eventBus().consumer("__vertx.metrics", message -> {
    logger.debug("Event loop metrics: {}", message.body());
});
```

---

## 13. Production Deployment Checklist

### 13.1. Configuration Validation
- [ ] Pool size configured (100+ for high load)
- [ ] `setShared(true)` enabled on all pools
- [ ] Named pools for monitoring
- [ ] SSL/TLS enabled for production
- [ ] Connection timeouts configured
- [ ] Pipelining enabled

### 13.2. Monitoring & Observability
- [ ] Health check endpoints implemented
- [ ] Metrics collection enabled
- [ ] Logging levels configured (INFO for production)
- [ ] No passwords in logs
- [ ] Event loop metrics monitored

### 13.3. Resilience & Recovery
- [ ] Graceful shutdown implemented
- [ ] Shutdown coordination with state checking
- [ ] Timer cleanup in shutdown methods
- [ ] Connection pool cleanup
- [ ] Error recovery patterns implemented

### 13.4. Performance Validation
- [ ] Load testing completed
- [ ] Raft consensus latency < 50ms P95
- [ ] No event loop blocking detected
- [ ] Resource leak testing passed
- [ ] Throughput targets met

---

## 14. Critical Reminders (From PeeGeeQ Production Experience)

### **Architecture & Design**
*   **Single Vert.x Instance**: Never create `Vertx.vertx()` in factories or components - always inject
*   **Pool Sharing**: Always use `setShared(true)` and named pools for monitoring
*   **Dependency Injection**: Pass Vertx and Pool instances via constructors
*   **Idempotent Creation**: Use `computeIfAbsent` pattern with cleanup on failure
*   **Parser Singletons**: Use static singleton for YAML/JSON parsers (70-80% performance gain)

### **Reactive Patterns**
*   **Composable Futures**: Use `.compose()`, `.onSuccess()`, `.onFailure()` - no callbacks
*   **Avoid Blocking**: Never call `Thread.sleep()` or blocking DB calls on event loop
*   **Event Loop Detection**: Guard all blocking methods with event loop checks
*   **Timer Management**: Use `vertx.setPeriodic()` instead of `ScheduledExecutorService`

### **Lifecycle & Shutdown**
*   **Graceful Shutdown**: Implement `stopReactive()` to close servers and pools cleanly
*   **Shutdown Coordination**: Check `closed` state before all async operations
*   **Timer Cleanup**: Always cancel timers in shutdown methods
*   **Resource Cleanup**: Use `Future.all()` to aggregate async cleanup operations

### **Performance & Optimization**
*   **Pool Sizing**: Start at 16-32, tune to 100+ for high load (NOT default 4!)
*   **Wait Queue**: Set to 10x pool size for burst handling
*   **Pipelining**: Enable with `setPipeliningLimit(256)` for throughput
*   **Batch Operations**: Use `executeBatch()` for multiple operations

### **Security & Validation**
*   **Fail Fast**: Validate inputs (ports, cluster configs) immediately on startup
*   **Password Safety**: Never log passwords - sanitize `toString()` methods
*   **SSL/TLS**: Use `SslMode.REQUIRE` in production
*   **Input Validation**: Validate all inputs with `Objects.requireNonNull()` and null/blank checks

### **Testing & Quality**
*   **TestContainers**: Use `postgres:15.13-alpine3.20` for integration tests
*   **Reactive Tests**: Use `VertxTestContext` for async test patterns
*   **Shutdown Tests**: Verify clean shutdown with no resource leaks
*   **Performance Tests**: Validate throughput and latency targets

### **Monitoring & Operations**
*   **Health Checks**: Implement real database checks with `SELECT 1`
*   **Codecs**: Don't forget `BodyHandler.create()` to enable JSON parsing
*   **Logging**: Use appropriate levels (DEBUG for development, INFO for production)
*   **Metrics**: Track pool utilization, connection times, and event loop metrics

---

## 15. Success Metrics

### **Performance Targets**
- Raft consensus latency: < 10ms P95 (sub-millisecond goal)
- Log append throughput: > 1000 entries/sec
- Pool utilization: < 80% under normal load
- Connection acquisition: < 10ms

### **Quality Metrics**
- Zero event loop blocking violations
- Zero resource leaks
- Clean shutdown in < 5 seconds
- All integration tests passing

### **Operational Metrics**
- Health check success rate: > 99.9%
- Zero "Pool is closed" errors
- Zero "multiple Vert.x instance" warnings
- Graceful handling of leader elections

---

## 16. References

### **Internal Documentation**
- PeeGeeQ Vert.x 5.x Patterns Guide
- PeeGeeQ Migration General Guide
- PeeGeeQ Shutdown Coordination Patterns
- PeeGeeQ Performance Optimization Guide

### **External Resources**
- [Vert.x 5 Migration Guide](https://vertx.io/docs/guides/vertx-5-migration-guide/)
- [Vert.x PostgreSQL Client Documentation](https://vertx.io/docs/vertx-pg-client/java/)
- [Vert.x Core Documentation](https://vertx.io/docs/vertx-core/java/)
- [gRPC Vert.x Documentation](https://vertx.io/docs/vertx-grpc/java/)

---

**Document Version**: 2.0 (Enhanced with PeeGeeQ Production Patterns)
**Last Updated**: December 2025
**Status**: Production-Ready Migration Guide
