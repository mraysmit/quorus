# Quorus Copilot Instructions

## Project Overview
Quorus is an enterprise-grade distributed file transfer system built with **Java 25** and **Vert.x 5.0.8**. It uses a **controller-first architecture** with Raft consensus for distributed state management.

**Direction of travel (accepted 2026-09-26; see plan §20):**
- Quorus will consume consensus through the generic QRaft engine (`../qraft`), not through its own `RaftNode` or a direct `raftlog-core` dependency ([ADR-0011](../docs-design/architecture-decisions/ADR-0011-CONSENSUS-VIA-QRAFT-GENERIC-ENGINE.md)). The Quorus–QRaft interface must stay 100% generic: never add a Quorus concept (transfer, job, agent, tenant, route, role, HTTP resource) to QRaft.
- Quorus will leave Vert.x for Java 27 virtual threads, `ScopedValue` and structured concurrency ([ADR-0012](../docs-design/architecture-decisions/ADR-0012-JAVA-RUNTIME-AND-STRUCTURED-CONCURRENCY.md)). `StructuredTaskScope` is still a preview API in JDK 27. Never compile with `--enable-preview`: write structured code against the Quorus task-scope abstraction (`RT-02`), which moves to `StructuredTaskScope` once it is final. The controller HTTP server will be the JDK `HttpsServer`, and Quorus follows each six-monthly Java release.

The conventions below describe the current Vert.x code. Follow them for existing modules until their `RT` migration item lands. Do not add new Vert.x coupling where a JDK-typed interface would do.

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
| `quorus-core` | Transfer engine, protocol adapters (HTTP/FTP/FTPS/SFTP/SMB/NFS), exceptions |
| `quorus-workflow` | YAML workflow parsing, dependency graphs, execution engine |
| `quorus-controller` | Raft node, gRPC transport, HTTP API server |
| `quorus-agent` | Job polling, transfer execution, heartbeat to controller |
| `quorus-tenant` | Multi-tenancy, quotas, resource management |
| `quorus-integration-examples` | Runnable integration examples and workflow validation CLI |

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
Each module has `src/main/resources/<module>.properties` with override support.

**Resolution order (highest to lowest priority):**
1. Explicit `Properties` passed to the configuration constructor
2. Environment variable: `QUORUS_HTTP_PORT=8080` (key upper-cased, `.` and `-` become `_`)
3. Profile resource: `quorus-controller-<profile>.properties` (profile `default` loads none)
4. Packaged defaults: `quorus.http.port=8080` in `quorus-controller.properties`
5. Accessor default value

JVM system properties (`-Dquorus.*`) are **not** a configuration source. Config classes are per-instance, not singletons: `new AppConfig(profile, overrides)`, `new AgentConfig(profile, overrides)`, `new QuorusConfiguration(profile, overrides)`. Build one at the application boundary and pass it explicitly; controller tests use `ControllerTestConfig.create()`.

## Build & Test Commands

**MANDATORY: When running Maven or any test commands in the terminal, ALWAYS use `Tee-Object` so output is visible in the console AND saved to a file. NEVER use `Out-File` or `>` redirection alone — this hides output from the user.**

**Where the file goes depends on whether it is evidence.** `temp/` is git-ignored scratch space and may be deleted at any time, so it MUST NOT hold anything a plan, register or evidence record will cite. Red, green, regression and verification output that will be cited goes directly to `docs-design/evidence/raw/<slice-id>/`, and its SHA-256 is recorded in the slice's JSON manifest (see `docs-design/evidence/raw/INDEX.md`). Never cite a `temp/` path as evidence.

```powershell
# CORRECT — evidence cited by a plan or manifest:
mvn test -pl quorus-core 2>&1 | Tee-Object -FilePath docs-design\evidence\raw\p2-01\lease-expiry-red.log

# CORRECT — throwaway output that nothing will cite:
mvn test -pl quorus-core 2>&1 | Tee-Object -FilePath temp\test-output.txt

# WRONG — output hidden from user (NEVER DO THIS):
mvn test -pl quorus-core 2>&1 | Out-File temp\test-output.txt
mvn test -pl quorus-core > temp\test-output.txt
```

```bash
# Full build with tests
mvn clean verify

# Build single module
mvn compile -pl quorus-core

# Run tests with coverage
mvn test jacoco:report

# Start controller via the current controller-first runtime
# Prefer Docker compose or launch QuorusControllerVerticle from the IDE.
```

### Docker testing

**Images package host-built jars only. Never compile Java or run Maven inside a Docker image.** Do not add builder stages, Maven installs, `m2cache` build contexts or dependency-download layers. Dockerfiles are single-stage: `FROM amazoncorretto:27.0.0-alpine3.24` plus `COPY <module>/target/<jar>`. `.dockerignore` admits only those jars.

Docker-tagged tests run in Maven's `test` phase, before `package`, so build the jars first and do not `clean` in the same command:

```powershell
# Build the controller and agent jars on the host (clean package, Java 27)
./docker/build-runtime.ps1

# Start the clearly labelled insecure development topology
docker compose -f docker/compose/docker-compose-single-controller.yml up -d --build

# Start the generated-certificate mTLS example
docker compose -f docker/compose/docker-compose-tls-example.yml up -d --build

# Docker and slow test groups (after build-runtime; no clean)
mvn verify '-Dtest.excludedGroups='

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

### Test-concurrency direction

The migration target for Vert.x asynchronous tests is to use Vert.x `Future`, `Promise`, timers,
and `VertxTestContext`, with blocking work isolated through `executeBlocking`. Prefer these patterns
for new or remediated Vert.x boundary tests. Existing tests still contain Java concurrency
primitives and sleeps, so do not describe the migration as complete or expand those legacy
patterns into new tests. Purpose-built thread-safety tests may use Java concurrency primitives
when concurrency itself is the behavior under test.

#### Shared test utility
`TestFutureUtils` in `quorus-core/src/test/java/dev/mars/quorus/testing/TestFutureUtils.java`:
- `awaitSuccess(Future<T>, Duration)` — blocks test thread until future completes or times out
- `awaitFailure(Future<?>, Duration)` — blocks until future fails, returns the cause

## Workflow YAML Structure
```yaml
metadata:
  name: workflow-name
  version: 1.0.0
  description: Transfer the daily input file
  type: transfer-workflow
  author: Quorus Development
  created: "2026-09-25"
  tags:
    - example
    - transfer
spec:
  variables:
    host: files.example.com
  execution:
    dryRun: false
    virtualRun: false
    parallelism: 1
    timeout: 300s
    strategy: sequential
  transferGroups:
    - name: group1
      dependsOn: []  # Dependency resolution via DependencyGraph
      transfers:
        - name: transfer1
          source: "https://{{host}}/file.csv"
          destination: "/data/file.csv"
          protocol: https
```

Variable substitution uses `{{variable}}` syntax. Parser: `YamlWorkflowDefinitionParser`.

## Raft Consensus (quorus-controller)
- Storage uses only the external `raftlog-core` library through `RaftLogStorageAdapter`; after plan item `CE-10` it is reached only through QRaft. Do not add internal WAL, RocksDB or memory storage backends. Storage-dependent tests use the real adapter and per-test temporary directories; fault injection wraps real I/O. Quorus's snapshot sidecar is not a WAL.
- `RaftNode` manages state: FOLLOWER → CANDIDATE → LEADER
- `GrpcRaftTransport` handles inter-node communication
- `QuorusStateStore` applies committed log entries
- Cluster config: `quorus.cluster.nodes=node1=host1:9080,node2=host2:9080`

## Protocol Adapters (quorus-core/protocol/)
Implement `TransferProtocol` interface:
- `HttpTransferProtocol` — reactive, non-blocking
- `FtpTransferProtocol` (FTP/FTPS), `SftpTransferProtocol`, `SmbTransferProtocol`, and
  `NfsTransferProtocol` perform blocking I/O; the default `TransferProtocol.transferReactive()`
  wrapper isolates it with Vert.x `executeBlocking`

## Agent-Controller Communication

Agents communicate with the controller via REST API at `{controller}/api/v1`:

### Lifecycle Flow
```
1. Registration:  POST /agents/register        → agentId, tenantId, hostname, address, port, version, region, datacenter, agentPool, networkZone, capabilities
2. Heartbeat:     POST /agents/heartbeat       → agentId, timestamp, sequenceNumber, status, currentJobs, availableCapacity, metrics
3. Agent listing: GET  /agents                 → returns registered agents
4. Job polling:   GET  /agents/{agentId}/jobs  → returns pendingJobs[]
5. Status report: POST /jobs/{jobId}/status    → agentId, tenantId, status, bytesTransferred, error details and attempt/fencing fields
```

There is currently no agent deregistration route exposed by the controller.

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
- [QuorusControllerVerticle.java](../quorus-controller/src/main/java/dev/mars/quorus/controller/QuorusControllerVerticle.java) — Controller startup sequence
- [RaftNode.java](../quorus-controller/src/main/java/dev/mars/quorus/controller/raft/RaftNode.java) — Raft consensus implementation
- [SimpleTransferEngine.java](../quorus-core/src/main/java/dev/mars/quorus/transfer/SimpleTransferEngine.java) — Transfer execution
- [YamlWorkflowDefinitionParser.java](../quorus-workflow/src/main/java/dev/mars/quorus/workflow/YamlWorkflowDefinitionParser.java) — Workflow parsing
- [Quorus Architecture Specification](../docs/QUORUS_ARCHITECTURE_SPECIFICATION.md) — Canonical architecture and conformance status

## OpenTelemetry Integration

All modules use OpenTelemetry for metrics/tracing. The controller initializes via `TelemetryConfig.configure(vertxOptions, appConfig)` using its injected configuration instance.

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

## Code Validation Requirements (MANDATORY)

**When validating implementation status, gap analysis, or architecture reviews:**

### DO NOT:
- Conclude "not implemented" based solely on grep searches
- Trust design documents as source of truth for implementation status
- Pattern-match class names from docs against codebase
- Make claims without reading actual source files

### MUST DO:
1. **Read the actual Java source files** using `read_file`, not just `grep_search`
2. **Trace dependencies** - check `pom.xml` for relevant libraries
3. **Follow the code path** - if checking persistence, read constructor → fields → methods that use storage
4. **Cite specific evidence** - file path and line numbers for every claim
5. **Check tests** - integration tests often prove functionality exists

### Required Format for Implementation Claims:

**WRONG:**
> "Raft persistence is not implemented"

**CORRECT:**
> "Raft persistence status:
> - Checked `RaftNode.java` lines 70-90: Found `private final RaftStorage storage` field
> - Checked `pom.xml` line 62: Found `raftlog-core` dependency
> - Checked `RaftLogStorageAdapter.java`: Wraps FileRaftStorage
> - **Conclusion: IMPLEMENTED** via raftlog-core library"

### If Uncertain:
Say "I haven't verified this in the source code yet" rather than guessing.

## Implementation Quality Rules (MANDATORY)

These rules prevent incomplete implementations and disconnected code.

### 1. No Dead Code
Every new method, parameter, or overload **must have at least one caller at the time of commit**. If you create a new method signature, you must update all callers in the same change. Before declaring any task complete, run `list_code_usages` on every new public method — if it has zero callers outside its own class and tests, the implementation is incomplete.

### 2. Flow-First Implementation
Before writing any code, **identify the data flow end-to-end**: entry point → every touchpoint → exit point. List the flow before starting. Implement along the flow, not file-by-file. Example: a correlation ID enters via HTTP header → must appear in middleware, error responses, log output, and response headers. All touchpoints are one unit of work.

### 3. Behavioral Tests Required
Every feature must have at least one test that **exercises the full path a real request would take**. Unit tests of individual methods are not sufficient on their own. The test must prove the feature works from the perspective of the external caller (HTTP client, agent, etc.). Example: to test correlation ID propagation, send an HTTP request with `X-Request-ID`, trigger an error, and assert the JSON response `requestId` matches.

### 4. No Document Proliferation
Do not create new standalone documents (markdown files, changelog files, example files) unless explicitly requested. Consolidate into existing working documents. The current delivery roadmap is `docs-design/task/QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md`; `QUORUS_ALPHA_IMPLEMENTATION_PLAN.md` is retained as historical evidence.

## Process Rules: Preventing Incomplete Work (MANDATORY)

These rules exist because of repeated failures where work was declared done but wasn't.

### 1. Complete Elimination, Not Partial Removal
When instructed to remove a pattern (e.g., "remove CompletableFuture"), you MUST:
1. **Grep the ENTIRE scope** for ALL variants of that pattern BEFORE making any changes (e.g., `CompletableFuture`, `CompletionException`, `ExecutionException`, `toCompletionStage`, helper methods that wrap it)
2. **List every occurrence** with file and line number
3. **Remove ALL occurrences** in one pass — not just the obvious ones
4. **Remove helper methods** that only existed to support the banned pattern (e.g., `awaitCompletable()`, `unwrapCompletionError()`)
5. **Verify with grep** after changes — zero matches means done

**WRONG:** Fix the one instance the user pointed out, declare done. User finds more.
**RIGHT:** Grep everything, fix everything, grep again to prove zero remain.

### 2. Understand the Full Scope of a Directive
When given a migration directive, inventory the entire affected scope before editing. For the
Vert.x test-concurrency target, distinguish legacy usages from new code and from purpose-built
thread-safety tests; do not claim a repository-wide ban or completed migration unless a full scan
and verification prove it.

### 3. When Changing a Return Type, Update ALL Callers
When a method signature changes (e.g., `void stop()` → `Future<Void> stop()`):
1. Find ALL callers of that method across the entire codebase
2. Update every caller to handle the new return type
3. Pay special attention to `@AfterEach` / tearDown methods — they commonly fire-and-forget

### 4. Don't Add Unused Imports
When editing a file, only add imports for symbols you are actually introducing. Review your changes before finalizing:
- If you removed the only usage of a symbol, remove its import too
- If you're adding `VertxTestContext` to the imports, verify the test methods actually use it
- Run a quick mental check: "Does every import I added correspond to a new usage in the code?"

### 5. Verify Before Declaring Done
After completing any removal or migration task:
1. **Grep for the banned pattern** — must return zero matches
2. **Grep for related patterns** — banned pattern often has companions
3. **Compile** — catches missing imports, type mismatches
4. **Run tests** — catches runtime issues
Only mark a task complete after ALL four checks pass.

### 6. One Pass, Not Incremental Discovery
Don't discover problems one at a time across multiple user interactions. When asked to clean up a category of issues:
1. Do a comprehensive audit FIRST (grep all test files, read results)
2. Make a complete list of everything that needs to change
3. Execute all changes
4. Verify all changes

The user should never have to point out a second instance of something you were already told to fix.
