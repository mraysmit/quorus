# Quorus Log Style Guide

## Overview

Quorus uses a structured, hierarchical logging format designed for readability and traceability. This guide documents the conventions used across all modules.

---

## Log Format

### Pattern Structure
```
{timestamp} [{thread}] {level} [{simulator}/{testMethod}] {logger} - {message}
```

**Example:**
```
2026-02-03 13:28:17.569 [main] DEBUG [BasicTransferTests/testSubmitAndComplete] d.m.q.s.t.InMemoryTransferEngineSimulator - submitTransfer: Received request jobId=test-job-1
```

---

## Visual Hierarchy

### Test Suite Banners
```
════════════════════════════════════════════════════════════════════════════════
▶ SIMULATOR TEST SUITE: InMemoryTransferEngineSimulator
  Display Name: InMemoryTransferEngineSimulator Tests
  Test Class: InMemoryTransferEngineSimulatorTest
════════════════════════════════════════════════════════════════════════════════
```

### Individual Test Markers
```
  ┌─ TEST: Should submit and complete transfer
  │  Path: InMemoryTransferEngineSimulatorTest > BasicTransferTests > testSubmitAndComplete
  │  Starting...
  │  [DEBUG] SIMULATOR: BasicTransferTests
  │  [DEBUG] TEST METHOD: testSubmitAndComplete
  │  ✓ PASSED (102ms)
  └─────────────────────────────────────
```

### Suite Completion
```
────────────────────────────────────────────────────────────
◀ COMPLETED: BasicTransferTests test suite
────────────────────────────────────────────────────────────
```

---

## MDC Context Keys

| Key | Description | Example |
|-----|-------------|---------|
| `simulator` | Active simulator or test class name | `InMemoryTransferEngineSimulator` |
| `testMethod` | Current test method name | `testSubmitAndComplete` |
| `testPath` | Full hierarchical test path | `TestClass > Nested > method` |
| `correlationId` | Unique ID for tracing test execution | `test-97670132-0547` |

---

## Log Levels

| Level | Usage |
|-------|-------|
| **ERROR** | Test failures (brief summary only: error type + truncated message) |
| **WARN** | Simulated failures, chaos engineering events, test aborts |
| **INFO** | Test lifecycle events, major operations (transfer complete) |
| **DEBUG** | Per-test metrics, timing, resource deltas, completion status, failure details |
| **TRACE** | OS/environment context, **full stack traces**, nested exception chains |

### Stack Trace Policy

**Stack traces are logged at TRACE level only** to keep ERROR output clean:
- **ERROR level**: Brief failure summary (error type + first 100 chars of message)
- **DEBUG level**: Error type, full message, root cause summary
- **TRACE level**: Complete stack trace with all nested causes

This separation ensures:
1. CI/CD logs remain scannable at default log levels
2. DevOps can enable TRACE for deep diagnostics when needed
3. No stack trace pollution in normal test output

---

## TRACE Level: Environment Context

Environment/OS information is logged at TRACE level to reduce verbosity. Enable by setting `SimulatorTestRunner` logger to TRACE in logback-test.xml.

### Environment Context (logged at suite start, TRACE level)
```
[TRACE] ══════════════════════════ ENVIRONMENT ══════════════════════════
[TRACE] HOSTNAME: LAPTOP-T26VQQ26 (172.20.10.13)
[TRACE] JAVA VERSION: 25 (Oracle Corporation)
[TRACE] JAVA HOME: C:\Users\markr\.jdks\openjdk-25
[TRACE] JVM: OpenJDK 64-Bit Server VM 25+36-3489
[TRACE] OS: Windows 11 10.0 (amd64)
[TRACE] USER: markr @ C:\Users\markr\dev\java\corejava\quorus
[TRACE] PROCESSORS: 12
[TRACE] MAX MEMORY: 8152MB
[TRACE] INITIAL HEAP: 516MB
[TRACE] TIMEZONE: Asia/Shanghai
[TRACE] TEMP DIR: C:\Users\markr\AppData\Local\Temp\
[TRACE] FILE ENCODING: UTF-8
[TRACE] LINE SEPARATOR: \r\n
[TRACE] JVM ARGS: -Xmx8g -XX:+UseG1GC
[TRACE] PID: 27120
[TRACE] UPTIME: 0s
[TRACE] BUILD TOOL: Maven (/path/to/maven) | Gradle 8.x | IDE or direct execution
[TRACE] SUREFIRE VERSION: 3.2.2
[TRACE] PARALLEL CONFIG: forks=1, threads=4
[TRACE] ══════════════════════════════════════════════════════════════════
```

---

## DEBUG Level: Per-Test Diagnostics

The DEBUG logging provides per-test metrics for troubleshooting.

### Per-Test Metrics (logged at test start)
```
[DEBUG] ════════════════════════════════════════════════════════
[DEBUG] CORRELATION ID: test-97882806-7094
[DEBUG] SIMULATOR: ChaosEngineeringTests
[DEBUG] TEST METHOD: testRandomFailure
[DEBUG] FULL PATH: InMemoryFileSystemSimulatorTest > ChaosEngineeringTests > testRandomFailure
[DEBUG] DISPLAY NAME: Should simulate random failures
[DEBUG] START TIME: 2026-02-03 13:51:22.804 CST
[DEBUG] ────────────────────────────────────────────────────────
[DEBUG] THREAD: name=main, id=3, priority=5, group=main
[DEBUG] MEMORY: heap=41MB, nonHeap=16MB, free=466MB, used=49MB
[DEBUG] THREADS: active=9, peak=9, daemon=8
[DEBUG] CLASSES: loaded=3342, total=3342, unloaded=0
[DEBUG] ════════════════════════════════════════════════════════
```

### Completion Metrics (logged at test end)
```
[DEBUG] ════════════════════ COMPLETION METRICS ══════════════════
[DEBUG] STATUS: PASSED
[DEBUG] WALL TIME: 63ms
[DEBUG] CPU TIME: 796ms (delta: +63ms)
[DEBUG] END TIME: 2026-02-03 13:51:22.869 CST
[DEBUG] ────────────────────────────────────────────────────────
[DEBUG] MEMORY DELTA: -34MB (current: 15MB)
[DEBUG] THREAD DELTA: +0 (current: 9)
[DEBUG] CLASS DELTA: +227 (loaded: 3553)
[DEBUG] GC COUNT: 1, GC TIME: 4ms
[DEBUG] ══════════════════════════════════════════════════════════
```

### Failure Details (logged on test failure)

**ERROR level** (always visible):
```
│  ✗ FAILED (63ms)
│  Error: AssertionError - expected:<5> but was:<3>
```

**DEBUG level** (with -Dtest.loglevel=DEBUG):
```
[DEBUG] ════════════════════ FAILURE DETAILS ════════════════════
[DEBUG] ERROR TYPE: java.lang.AssertionError
[DEBUG] ERROR MESSAGE: expected:<5> but was:<3>
[DEBUG] ROOT CAUSE: java.io.IOException - Connection refused
[DEBUG] ══════════════════════════════════════════════════════════
```

**TRACE level** (full stack traces with -Dtest.loglevel=TRACE):
```
[TRACE] ════════════════════ FULL STACK TRACE ════════════════════
[TRACE]   at org.junit.jupiter.api.AssertionUtils.fail(...)
[TRACE]   at dev.mars.quorus.simulator.TransferTest.test(...)
[TRACE]   at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(...)
[TRACE]   at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(...)
[TRACE]   at java.base/java.lang.reflect.Method.invoke(...)
[TRACE] Caused by: java.io.IOException: Connection refused
[TRACE]   at java.net.PlainSocketImpl.connect(...)
[TRACE]   at java.net.Socket.connect(...)
[TRACE] ══════════════════════════════════════════════════════════
```

### DevOps Diagnostic Fields Reference

| Field | Source | Description |
|-------|--------|-------------|
| `HOSTNAME` | `InetAddress.getLocalHost()` | Machine hostname and IP |
| `JAVA VERSION` | System property `java.version` | JDK version and vendor |
| `JAVA HOME` | System property `java.home` | JDK installation path |
| `JVM` | System property `java.vm.name` | JVM implementation details |
| `OS` | System properties `os.*` | Operating system name, version, arch |
| `PROCESSORS` | `Runtime.availableProcessors()` | CPU core count |
| `MAX MEMORY` | `Runtime.maxMemory()` | JVM heap limit |
| `INITIAL HEAP` | `Runtime.totalMemory()` | Initial heap allocation |
| `PID` | `ProcessHandle.current().pid()` | OS process ID |
| `UPTIME` | `RuntimeMXBean.getUptime()` | JVM uptime in seconds |
| `BUILD TOOL` | System properties `maven.home`, `gradle.version` | Detected build tool |
| `SUREFIRE VERSION` | System property `surefire.version` | Maven Surefire version |
| `PARALLEL CONFIG` | System properties `surefire.forkCount`, `surefire.threadCount` | Parallel execution config |
| `WALL TIME` | `Duration.between(start, end)` | Elapsed wall-clock time |
| `CPU TIME` | `ThreadMXBean.getCurrentThreadCpuTime()` | CPU time consumed |
| `MEMORY DELTA` | Runtime memory diff | Memory change during test |
| `THREAD DELTA` | `ThreadMXBean.getThreadCount()` diff | Thread count change |
| `CLASS DELTA` | `ClassLoadingMXBean.getLoadedClassCount()` diff | Classes loaded during test |
| `GC COUNT` | `GarbageCollectorMXBeans` | Total garbage collections |
| `GC TIME` | `GarbageCollectorMXBeans` | Total GC pause time (ms) |

---

## Symbols Reference

| Symbol | Meaning |
|--------|---------|
| `▶` | Suite/test starting |
| `◀` | Suite/test completed |
| `✓` | Test passed |
| `✗` | Test failed |
| `⊘` | Test aborted |
| `⊖` | Test skipped |
| `═` | Major section boundary |
| `─` | Minor section boundary |
| `┌─` | Test start |
| `│` | Test in progress |
| `└─` | Test end |

---

## Graceful Error Recovery

The `SimulatorTestLoggingExtension` is designed to **never interfere with test execution**:

### Resilience Design
- **All callback methods** (`beforeAll`, `afterAll`, `beforeEach`, `afterEach`, `testSuccessful`, `testFailed`, `testAborted`, `testDisabled`) are wrapped in try-catch blocks
- **Logging failures are caught and suppressed** — a brief message is printed to stderr
- **MDC cleanup always runs** — even if logging fails, MDC entries are cleared in `finally` blocks
- **Internal exceptions logged at TRACE** — extension errors are logged via `log.trace(msg, exception)` for debugging

### Recovery Behavior
```java
// If logging fails, the extension prints a minimal warning and continues:
// stderr: [SimulatorTestLoggingExtension] Failed in beforeEach: <error message>
// TRACE log: Full exception with stack trace for later diagnosis
```

### Why This Matters
1. **Test isolation**: Logging infrastructure failures cannot cause test failures
2. **CI/CD stability**: Build pipelines won't break due to logging issues
3. **Observability**: When logging works, full diagnostic data is captured
4. **Debuggability**: When logging fails, TRACE logs capture what went wrong

---

## Implementation

- **Extension:** `SimulatorTestLoggingExtension.java`
- **Config:** `logback-test.xml`
- **Logger:** `SimulatorTestRunner` (test lifecycle)

Add to test classes:
```java
@ExtendWith(SimulatorTestLoggingExtension.class)
@DisplayName("MySimulator Tests")
class MySimulatorTest { }
```

---

## Logback Configuration

Place `logback-test.xml` in `src/test/resources/`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    
    <!-- Console appender -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- File appender with MDC context -->
    <appender name="FILE" class="ch.qos.logback.core.FileAppender">
        <file>target/test-logs/quorus-core-tests.log</file>
        <append>false</append>  <!-- Overwrite each run -->
        <encoder>
            <!-- Pattern includes [simulator/testMethod] from MDC -->
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{simulator}/%X{testMethod}] %logger{40} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- Rolling file appender (size-based rotation) -->
    <appender name="ROLLING_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>target/test-logs/quorus-core-tests-rolling.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>target/test-logs/quorus-core-tests.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxFileSize>10MB</maxFileSize>
            <maxHistory>5</maxHistory>
            <totalSizeCap>50MB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{simulator}/%X{testMethod}] %logger{40} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- SimulatorTestRunner at DEBUG for detailed test context -->
    <logger name="SimulatorTestRunner" level="DEBUG"/>
    
    <!-- Quorus simulator packages at DEBUG -->
    <logger name="dev.mars.quorus.simulator" level="DEBUG"/>
    
    <!-- All Quorus packages at DEBUG -->
    <logger name="dev.mars.quorus" level="DEBUG"/>
    
    <!-- Reduce noise from third-party libraries -->
    <logger name="org.apache" level="WARN"/>
    <logger name="io.vertx" level="WARN"/>
    <logger name="io.netty" level="WARN"/>
    
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>
    
</configuration>
```

### Key Configuration Options

| Setting | Purpose |
|---------|---------|
| `<append>false</append>` | Overwrites log file each test run |
| `<append>true</append>` | Appends to existing log file |
| `%X{simulator}` | MDC key for current simulator name |
| `%X{testMethod}` | MDC key for current test method |
| `%logger{40}` | Logger name truncated to 40 chars |

### Log File Locations

```
target/test-logs/
├── quorus-core-tests.log           # Main test log (overwritten each run)
└── quorus-core-tests-rolling.log   # Rolling log with history
```

### Forcing Log File Creation

The log file is created automatically when tests run. To ensure it exists:

```bash
# Run tests (creates log file)
mvn test -pl quorus-core

# View log file
cat target/test-logs/quorus-core-tests.log

# Run specific test class
mvn test -pl quorus-core -Dtest="InMemoryAgentSimulatorTest"

# Run with DEBUG output to console as well
mvn test -pl quorus-core -Dtest="InMemoryAgentSimulatorTest" -X
```

---

## Message Conventions

### Operation Logging
```java
log.debug("methodName: Action description param={}", value);
log.info("methodName: Outcome description key1={}, key2={}", v1, v2);
```

### Examples
```
submitTransfer: Received request jobId=test-job-1, source=sftp://host/file.txt
submitTransfer: Created transfer jobId=test-job-1, size=1000 bytes
completeTransfer: Transfer completed successfully jobId=test-job-1, bytes=1000, duration=102ms
```

---

## DevOps Troubleshooting Guide

### Common Issues and What to Look For

#### Test Failures in CI/CD
1. Check `ENVIRONMENT` section for differences from local:
   - Different JDK version or vendor
   - Memory limits (`MAX MEMORY` too low)
   - Different `TIMEZONE` affecting date comparisons
   - Missing temp directory access

2. Compare `COMPLETION METRICS`:
   - High `MEMORY DELTA` suggests memory leak
   - Non-zero `THREAD DELTA` indicates thread leak
   - Excessive `GC COUNT` shows memory pressure

#### Memory Issues
```bash
# Find tests with highest memory delta
grep "MEMORY DELTA" test.log | sort -t: -k5 -n | tail -10
```

- `MEMORY DELTA: +500MB` → Test allocating excessive objects
- `heap > 80% MAX MEMORY` → Increase heap or optimize test

#### Thread Leaks
```bash
# Find tests that leaked threads
grep "THREAD DELTA: +[1-9]" test.log
```

- `THREAD DELTA: +2` after test → Async executors not shut down
- Check `THREAD: group=` to identify thread pool source

#### Slow Tests
```bash
# Find tests with wall time > 5s
grep "WALL TIME:" test.log | awk -F: '{if ($NF > 5000) print}'
```

- High `WALL TIME`, low `CPU TIME` → I/O bound or waiting
- High `CPU TIME` → Computation-heavy, may need optimization
- High `CLASS DELTA` → First test loading many classes (normal)

#### Flaky Tests
Use `CORRELATION ID` to trace specific test runs across distributed logs:
```bash
grep "test-97882806-7094" *.log
```

### Log Aggregation Tips

#### Structured Log Parsing (ELK/Loki)
The MDC fields enable powerful queries:
```
{simulator="InMemoryTransferEngine"} |= "FAILED"
```

#### Key Fields for Dashboards
| Field | Dashboard Use |
|-------|--------------|
| `WALL TIME` | Test duration histogram |
| `MEMORY DELTA` | Memory trend over time |
| `GC TIME / WALL TIME` | GC overhead percentage |
| `CLASS DELTA` | Class loading spikes |
| `STATUS` | Pass/fail rate |

### Environment Comparison Checklist

When tests pass locally but fail in CI:

| Check | Local | CI |
|-------|-------|-----|
| JAVA VERSION | ? | ? |
| PROCESSORS | ? | ? |
| MAX MEMORY | ? | ? |
| TIMEZONE | ? | ? |
| FILE ENCODING | ? | ? |
| TEMP DIR writable | ? | ? |
| JVM ARGS | ? | ? |

---

*Last updated: February 2026*
