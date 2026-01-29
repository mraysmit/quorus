# Quorus Copilot Instructions

## Project Overview
Quorus is an enterprise-grade distributed file transfer system built with **Java 21** and **Vert.x 5.0.2**. It uses a **controller-first architecture** with Raft consensus for distributed state management.

## Architecture (Controller-First Pattern)

```
Controller (main) ─┬─ HTTP API (embedded)
                   ├─ Raft consensus (gRPC)
                   └─ State machine
Agents poll controller for jobs, execute transfers via protocol adapters
```

**Module structure:**
| Module | Purpose |
|--------|---------|
| `quorus-core` | Transfer engine, protocol adapters (HTTP/FTP/SFTP/SMB), exceptions |
| `quorus-workflow` | YAML workflow parsing, dependency graphs, execution engine |
| `quorus-controller` | Raft node, gRPC transport, HTTP API server |
| `quorus-agent` | Job polling, transfer execution, heartbeat to controller |
| `quorus-tenant` | Multi-tenancy, quotas, resource management |
| `quorus-api` | Legacy REST API (being phased out) |

## Key Conventions

### Reactive Patterns
- All async operations use **`io.vertx.core.Future<T>`** (not CompletableFuture)
- Controllers run on Vert.x event loop — avoid blocking operations
- Protocol adapters: use `transferReactive()` over deprecated `transfer()`

### Interface Implementation Pattern
```java
// Interface in quorus-core:   TransferEngine, TransferProtocol
// Impl with Simple prefix:    SimpleTransferEngine, SimpleTenantService
```

### Exception Hierarchy
All exceptions extend `QuorusException`:
- `TransferException` — includes `transferId` in message
- `ChecksumMismatchException` — integrity verification failures
- `WorkflowParseException` — YAML parsing errors

### Configuration
Each module has `src/main/resources/<module>.properties` with env var overrides:
```properties
quorus.http.port=8080  →  QUORUS_HTTP_PORT
quorus.node.id=        →  QUORUS_NODE_ID
```
Config classes use singleton pattern: `AppConfig.get()`, `AgentConfig.get()`

## Build & Test Commands

```bash
# Full build with tests
mvn clean verify

# Build single module
mvn compile -pl quorus-core

# Run tests with coverage
mvn test jacoco:report

# Start controller in dev mode (if using Quarkus legacy API)
mvn quarkus:dev -pl quorus-api
```

### Docker testing
```powershell
# Start 3-node cluster with monitoring
docker-compose -f docker/compose/docker-compose.yml up -d
docker-compose -f docker/compose/docker-compose-loki.yml up -d

# Validate Raft consensus
./scripts/prove-metadata-persistence.ps1
./scripts/test-log-integrity.ps1
```

## Testing Patterns

- Use **JUnit 5** with `@ExtendWith(VertxExtension.class)` for async tests
- Use `VertxTestContext` for Future assertions
- TestContainers for integration tests requiring Docker

```java
@ExtendWith(VertxExtension.class)
class MyTest {
    @Test
    void testAsync(Vertx vertx, VertxTestContext ctx) {
        engine.submitTransfer(request)
            .onComplete(ctx.succeedingThenComplete());
    }
}
```

## Workflow YAML Structure
```yaml
metadata:
  name: "workflow-name"
  version: "1.0.0"
spec:
  variables:
    date: "{{TODAY}}"
  transferGroups:
    - name: group1
      dependsOn: []  # Dependency resolution via DependencyGraph
      transfers:
        - name: transfer1
          source: "https://{{host}}/file.csv"
          destination: "/data/file.csv"
          protocol: https
```

Variable substitution uses `{{variable}}` syntax. Parser: `YamlWorkflowDefinitionParser` (uses static singleton for performance).

## Raft Consensus (quorus-controller)
- `RaftNode` manages state: FOLLOWER → CANDIDATE → LEADER
- `GrpcRaftTransport` handles inter-node communication
- `QuorusStateMachine` applies committed log entries
- Cluster config: `quorus.cluster.nodes=node1=host1:9080,node2=host2:9080`

## Protocol Adapters (quorus-core/protocol/)
Implement `TransferProtocol` interface:
- `HttpTransferProtocol` — reactive, non-blocking
- `SftpTransferProtocol`, `FtpTransferProtocol`, `SmbTransferProtocol` — blocking, use WorkerExecutor

## Agent-Controller Communication

Agents communicate with the controller via REST API at `{controller}/api/v1`:

### Lifecycle Flow
```
1. Registration:  POST /agents/register    → agentId, region, datacenter, protocols, capacity
2. Heartbeat:     POST /agents/heartbeat   → agentId, timestamp, sequenceNumber, status, currentJobs
3. Job Polling:   GET  /agents/{id}/jobs   → returns pendingJobs[]
4. Status Report: POST /agents/{id}/status → jobId, status, bytesTransferred, error
5. Deregister:    DELETE /agents/{id}      → graceful shutdown
```

### Key Services (quorus-agent/service/)
| Service | Responsibility | Interval |
|---------|----------------|----------|
| `AgentRegistrationService` | Initial registration with capabilities | Once at startup |
| `HeartbeatService` | Keep-alive with capacity updates | `quorus.agent.heartbeat.interval-ms` (30s default) |
| `JobPollingService` | Fetch pending job assignments | `quorus.agent.jobs.polling.interval-ms` (10s default) |
| `JobStatusReportingService` | Report transfer progress/completion | Per-job events |
| `TransferExecutionService` | Execute transfers via protocol adapters | On job receipt |

### Heartbeat Payload
```json
{
  "agentId": "agent-nyc-01",
  "timestamp": "2026-01-29T10:00:00Z",
  "sequenceNumber": 42,
  "status": "active",
  "currentJobs": 2,
  "availableCapacity": 3
}
```

## Key Files
- [QuorusControllerVerticle.java](quorus-controller/src/main/java/dev/mars/quorus/controller/QuorusControllerVerticle.java) — Controller startup sequence
- [RaftNode.java](quorus-controller/src/main/java/dev/mars/quorus/controller/raft/RaftNode.java) — Raft consensus implementation
- [SimpleTransferEngine.java](quorus-core/src/main/java/dev/mars/quorus/transfer/SimpleTransferEngine.java) — Transfer execution
- [YamlWorkflowDefinitionParser.java](quorus-workflow/src/main/java/dev/mars/quorus/workflow/YamlWorkflowDefinitionParser.java) — Workflow parsing
- [docs/QUORUS_SYSTEM_DESIGN.md](docs/QUORUS_SYSTEM_DESIGN.md) — Comprehensive architecture documentation

## OpenTelemetry Integration

All modules use OpenTelemetry for metrics/tracing. Initialize via `TelemetryConfig.configure(vertxOptions)`.

### Metrics Pattern
```java
Meter meter = GlobalOpenTelemetry.getMeter("quorus-module");
LongCounter counter = meter.counterBuilder("quorus.module.operation.total")
    .setDescription("Total operations")
    .setUnit("1")
    .build();
```

### Metric Naming Convention
- Format: `quorus.<module>.<metric>.<unit>`
- Examples: `quorus.workflow.total`, `quorus.cluster.term`, `quorus.transfer.bytes`

### Endpoints
- Prometheus metrics: `:9464/metrics` (controller), `:9465/metrics` (agent)
- OTLP traces: `http://localhost:4317` (gRPC)

## gRPC/Protobuf (Raft Transport)

Proto definitions in `quorus-controller/src/main/proto/raft.proto`. 

### Regenerating gRPC stubs
```bash
mvn compile -pl quorus-controller  # protobuf-maven-plugin auto-generates
```

### Generated classes (do not edit manually)
- `dev.mars.quorus.controller.raft.grpc.VoteRequest/Response`
- `dev.mars.quorus.controller.raft.grpc.AppendEntriesRequest/Response`
- `dev.mars.quorus.controller.raft.grpc.RaftServiceGrpc`

### Adding new Raft messages
1. Edit `raft.proto` with new message/service definitions
2. Run `mvn compile -pl quorus-controller`
3. Implement handlers in `GrpcRaftServer.java`

## Legacy API Module (quorus-api) — DEPRECATED

**Status:** Being removed entirely. Do not add new code here.

The `quorus-api` module used Quarkus with a separate API layer. The controller-first architecture embeds HTTP directly in `quorus-controller` via `HttpApiServer`.

**Migration path:**
- New endpoints → `quorus-controller/http/HttpApiServer.java`
- Agent endpoints → Already migrated to controller
- Do not reference `quorus-api` in new code

## Testing: Critical Truths

### Testcontainers Required For
- Any database, message broker, filesystem, SFTP/FTP tests
- **Mocks are not acceptable** for I/O integration layers

### JUnit 5 + Testcontainers Pattern
```java
@Testcontainers
@ExtendWith(VertxExtension.class)
class IntegrationTest {
    static Network network = Network.newNetwork();
    
    @Container
    static GenericContainer<?> sftp = new GenericContainer<>("atmoz/sftp")
        .withNetwork(network)
        .withNetworkAliases("sftp");
}
```

### Container Networking
- **Never use `localhost`** inside containers — use network aliases
- Create shared `Network.newNetwork()` for multi-container tests

### Performance
- Use **static `@Container`** for shared state (faster)
- Reuse containers: set `testcontainers.reuse.enable=true` in `~/.testcontainers.properties`
- **Never** use `@DirtiesContext` unless absolutely necessary

### When NOT to use Testcontainers
- Pure algorithmic logic
- Stateless transformations
- In-memory deterministic data structures
