# Quorus Testing Guide

## Command Syntax Reference

### Stream Redirection: `2>&1`

In PowerShell/Bash, there are two output streams:
- **Stream 1 (stdout)**: Standard output - normal command output
- **Stream 2 (stderr)**: Standard error - error messages and warnings

| Syntax | Meaning |
|--------|---------|
| `2>&1` | Redirect stderr (2) to stdout (1) - combines all output into one stream |
| `>` | Redirect stdout to file (overwrites) |
| `>>` | Redirect stdout to file (appends) |
| `2>` | Redirect stderr only to file |

**Why use `2>&1`?**  
Maven outputs some messages to stderr (like `[INFO]`, `[WARNING]`). Without `2>&1`, `tee` / `Tee-Object` only captures stdout, missing important log lines.

### Pipeline: `|`

The pipe operator sends the output of one command as input to another:

**PowerShell:**
```powershell
mvn test | Tee-Object -FilePath file.log
#         ↑
#         Output of 'mvn test' flows into 'Tee-Object'
```

**Linux/Bash:**
```bash
mvn test | tee file.log
#         ↑
#         Output of 'mvn test' flows into 'tee'
```

### Tee-Object (PowerShell) / tee (Linux)

Splits output to both:
1. The console (so you can watch it)
2. A file (so you can review it later)

**PowerShell:**
```powershell
command | Tee-Object -FilePath "path/to/file.log"
```

**Linux/Bash:**
```bash
command | tee path/to/file.log
```

### Variable Interpolation / Command Substitution

**PowerShell — `$()`:**
```powershell
"logs/test-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
#           ↑
#           Becomes: logs/test-20260206-143022.log
```

**Linux/Bash — `$()`:**
```bash
"logs/test-$(date +'%Y%m%d-%H%M%S').log"
#           ↑
#           Becomes: logs/test-20260206-143022.log
```

---

## Running Tests with Console and File Output

### Option 1: Output to target directory (Maven convention, gitignored)

**PowerShell:**
```powershell
mvn test 2>&1 | Tee-Object -FilePath target/test-output.log
```

**Linux/Bash:**
```bash
mvn test 2>&1 | tee target/test-output.log
```

### Option 2: Output to logs directory

**PowerShell:**
```powershell
mvn test 2>&1 | Tee-Object -FilePath logs/test-output.log
```

**Linux/Bash:**
```bash
mvn test 2>&1 | tee logs/test-output.log
```

### Option 3: Timestamped output in logs directory

**PowerShell:**
```powershell
mvn test 2>&1 | Tee-Object -FilePath "logs/test-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
```

**Linux/Bash:**
```bash
mvn test 2>&1 | tee "logs/test-$(date +'%Y%m%d-%H%M%S').log"
```

## Running Specific Module Tests

**PowerShell:**
```powershell
# Single module
mvn test -pl quorus-core 2>&1 | Tee-Object -FilePath target/test-core.log

# Multiple modules
mvn test -pl quorus-core,quorus-controller 2>&1 | Tee-Object -FilePath target/test-output.log
```

**Linux/Bash:**
```bash
# Single module
mvn test -pl quorus-core 2>&1 | tee target/test-core.log

# Multiple modules
mvn test -pl quorus-core,quorus-controller 2>&1 | tee target/test-output.log
```

## Excluding Flaky Tests

**PowerShell:**
```powershell
mvn test -Dgroups='!flaky' 2>&1 | Tee-Object -FilePath target/test-stable.log
```

**Linux/Bash:**
```bash
mvn test -Dgroups='!flaky' 2>&1 | tee target/test-stable.log
```

## Running Only Negative Tests

**PowerShell:**
```powershell
mvn test -Dgroups='negative' 2>&1 | Tee-Object -FilePath target/test-negative.log
```

**Linux/Bash:**
```bash
mvn test -Dgroups='negative' 2>&1 | tee target/test-negative.log
```

## Running Tests with Coverage

**PowerShell:**
```powershell
mvn test jacoco:report 2>&1 | Tee-Object -FilePath target/test-coverage.log
```

**Linux/Bash:**
```bash
mvn test jacoco:report 2>&1 | tee target/test-coverage.log
```

## Background Test Execution with Live Tail

**PowerShell:**

Terminal 1 - Start tests in background:
```powershell
Start-Process -NoNewWindow -FilePath mvn -ArgumentList "test" -RedirectStandardOutput target/test-output.log -RedirectStandardError target/test-errors.log
```

Terminal 2 - Tail the log:
```powershell
Get-Content -Path target/test-output.log -Wait -Tail 100
```

**Linux/Bash:**

Terminal 1 - Start tests in background:
```bash
nohup mvn test > target/test-output.log 2> target/test-errors.log &
```

Terminal 2 - Tail the log:
```bash
tail -f target/test-output.log
```

## Quick Build Without Tests

**PowerShell:**
```powershell
mvn compile -pl quorus-core,quorus-controller,quorus-api,quorus-agent -q
```

**Linux/Bash:**
```bash
mvn compile -pl quorus-core,quorus-controller,quorus-api,quorus-agent -q
```

---

## Test Logging

### Consolidated Log File

All modules write to a **single consolidated log file** during test runs. Every `mvn test` or `mvn verify` invocation produces one file at the project root:

```
test-logs/quorus-test-2026-02-11_14-30-22.log
```

This file contains chronologically interleaved output from all modules (quorus-core, quorus-workflow, quorus-tenant, quorus-controller, quorus-api, quorus-agent, quorus-integration-examples). Each line includes the full logger name, so you can identify which module produced it:

```
2026-02-11 11:44:19.848 [main] INFO  d.m.quorus.workflow.SimpleWorkflowEngine - SimpleWorkflowEngine initialized
2026-02-11 11:44:21.927 [main] INFO  d.m.q.tenant.service.SimpleTenantService  - Created tenant: test-tenant
2026-02-11 11:44:48.567 [vert.x-eventloop-thread-0] INFO  d.mars.quorus.controller.raft.RaftNode - Starting Raft node
```

### How It Works

Maven Surefire forks a separate JVM per module, so each module's tests run in isolation. The consolidated logging is achieved by:

1. **Shared timestamp**: The parent `pom.xml` defines `maven.build.timestamp.format` and passes `maven.build.timestamp` as the system property `testRunTimestamp` to every Surefire fork. All modules in the same build receive the identical timestamp.

2. **Common file path**: Each module's `logback-test.xml` writes to `../test-logs/quorus-test-${testRunTimestamp}.log` — a relative path that resolves to `test-logs/` at the project root regardless of which module is running.

3. **Cross-JVM safety**: The `<prudent>true</prudent>` setting in each `logback-test.xml` enables file-level locking, ensuring safe concurrent writes if multiple module JVMs overlap.

4. **Append mode**: `<append>true</append>` ensures each module adds to the file rather than overwriting it.

### Configuration Files

| File | Role |
|------|------|
| `pom.xml` (parent) | Defines `maven.build.timestamp.format` and passes `testRunTimestamp` to Surefire |
| `<module>/src/test/resources/logback-test.xml` | Configures FILE appender with shared path and prudent mode |

### Log File Location

* **Path**: `test-logs/quorus-test-<timestamp>.log` at the project root
* **Gitignored**: The `test-logs/` directory is listed in `.gitignore`
* **Cleaned by**: `mvn clean` does **not** remove `test-logs/` (it's outside `target/`). Delete manually or use `Remove-Item test-logs -Recurse` when needed.
* **One file per build**: Each `mvn test` / `mvn verify` invocation creates a new timestamped file. Repeated runs accumulate files.

### Filtering Logs by Module

To view only a specific module's output from the consolidated log:

**PowerShell:**
```powershell
# Show only controller module entries
Get-Content test-logs/quorus-test-*.log | Select-String "quorus.controller"

# Show only workflow module entries
Get-Content test-logs/quorus-test-*.log | Select-String "quorus.workflow"
```

**Linux/Bash:**
```bash
# Show only controller module entries
grep "quorus.controller" test-logs/quorus-test-*.log

# Show only workflow module entries
grep "quorus.workflow" test-logs/quorus-test-*.log
```

### Console Output

Console logging is independent — each module still prints to its own Maven console output during the build. The consolidated file is an additional record for post-run analysis. You can capture console output separately using `Tee-Object` / `tee` as described in the sections above.

### Running a Single Module

When you run tests for a single module (e.g., `mvn test -pl quorus-controller`), the same mechanism applies — the log file is written to `test-logs/` at the project root and contains only that module's output.

---

## Appendix: Quorus Testing Truths

Testing principles for the Quorus distributed file transfer system. These are non-negotiable standards for integration testing across all modules.

### Testcontainers Are Required for Integration Tests

Testcontainers is mandatory for any test involving:

* Protocol servers (FTP, SFTP, SMB)
* OpenTelemetry collectors
* Multi-container Raft cluster scenarios
* Any external I/O dependency

Mocks are not acceptable substitutes for I/O integration layers. They hide real protocol edge cases and timing issues that only surface against actual servers.

**Quorus examples:**
* `InfrastructureWithTelemetryTest` — uses `GenericContainer` for the OpenTelemetry collector
* `ProtocolServersLifecycleIT` — validates FTP, SFTP, and SMB against real Docker containers

### JUnit Lifecycle: Static vs Per-Test Containers

* `static @Container` (class-level):
  * Faster — container starts once per test class
  * Shared state — must reset between tests if needed
* Per-test containers:
  * Slower — new container per test method
  * Cleaner isolation

**Quorus convention:** Use `static @Container` for protocol servers and infrastructure (OTel collector). These are stateless or easily reset between tests.

### Container Strategy

* **Single dependency** (e.g., SFTP server alone) — `GenericContainer`
* **Multi-service scenarios** (e.g., protocol servers + Raft cluster) — external Docker Compose started before tests

**Quorus practice:** Protocol server tests use externally-managed Docker Compose (`docker-compose-protocol-servers.yml`) started before the test suite. The `@AfterAll` cleanup in `ProtocolServersLifecycleIT` tears down the stack automatically.

### Networking Between Containers

When tests require multiple containers to communicate, use a shared Testcontainers `Network`:

```java
Network network = Network.newNetwork();

GenericContainer<?> sftp = new GenericContainer<>("atmoz/sftp")
    .withNetwork(network)
    .withNetworkAliases("sftp");

GenericContainer<?> app = new GenericContainer<>("quorus-agent")
    .withNetwork(network)
    .withEnv("SFTP_HOST", "sftp");
```

Never use `localhost` for container-to-container communication. Use network aliases.

### Performance

Container startup overhead is manageable with proper test design:

* Use `static @Container` to share containers across test methods
* Enable container reuse: set `testcontainers.reuse.enable=true` in `~/.testcontainers.properties`
* Do not restart containers between tests
* Do not use `@DirtiesContext`

### When NOT to Use Testcontainers

Testcontainers are unnecessary for:

* Pure algorithmic logic (e.g., `DependencyGraph` resolution, variable substitution)
* In-memory deterministic data structures (e.g., Raft log operations without persistence)
* Stateless transformations (e.g., YAML parsing, checksum computation)

Use plain JUnit 5 unit tests for these. Once I/O is involved, Testcontainers should be used.

### Quorus Test Classification

| Test Type | Naming Convention | Containers Required | Example |
|-----------|-------------------|---------------------|---------|
| Unit test | `*Test.java` | No | `RaftNodeTest`, `DependencyGraphTest` |
| Integration test | `*IT.java` | Yes | `ProtocolServersLifecycleIT` |
| Infrastructure test | `*Test.java` (with `@Testcontainers`) | Yes | `InfrastructureWithTelemetryTest` |

All integration tests use JUnit 5 with `@ExtendWith(VertxExtension.class)` where Vert.x async operations are tested.
