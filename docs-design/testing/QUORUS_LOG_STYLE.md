# Quorus Simulator Test Log Style

## 1. Scope

This guide describes the log output of `SimulatorTestLoggingExtension`
(`quorus-core/src/test/java/dev/mars/quorus/simulator/SimulatorTestLoggingExtension.java`). The
extension is used only by the eight simulator test classes in `quorus-core`:

| Test class | Package (`dev.mars.quorus.simulator…`) |
|---|---|
| `InMemorySimulatorTest` | `simulator` |
| `InMemoryAgentSimulatorTest` | `simulator.agent` |
| `InMemoryControllerClientSimulatorTest` | `simulator.client` |
| `InMemoryFileSystemSimulatorTest` | `simulator.fs` |
| `InMemoryTransferProtocolSimulatorTest` | `simulator.protocol` |
| `InMemoryFtpsProtocolSimulatorTest` | `simulator.protocol` |
| `InMemoryTransferEngineSimulatorTest` | `simulator.transfer` |
| `InMemoryWorkflowEngineSimulatorTest` | `simulator.workflow` |

No other module or test class uses it. Where test logs go (the consolidated
`test-logs/quorus-test-<timestamp>.log`), each module's `logback-test.xml`, and the expected-error
banners of `@ExpectsError` are described in the [Testing Guide](QUORUS_TESTING_README.md).

To use the extension in another simulator test:

```java
@ExtendWith(SimulatorTestLoggingExtension.class)
@DisplayName("MySimulator Tests")
class MySimulatorTest { }
```

---

## 2. Logger, levels and MDC

The extension logs through a logger named `SimulatorTestRunner`. `quorus-core`'s `logback-test.xml`
sets it to DEBUG, so INFO and DEBUG output appear by default and TRACE output does not.

| Level | What is logged |
|---|---|
| INFO | Suite banner, one line per test start, pass line, disabled-test line, suite completion |
| WARN | Aborted test (failed assumption) |
| ERROR | Failed test: one line with duration, exception type and the first 80 characters of the message, with no stack trace |
| DEBUG | Failure details for failed tests; a one-line resource summary for passed tests |
| TRACE | Environment at suite start, per-test start metrics, full stack traces and nested causes, suite JVM statistics, the extension's own internal errors |

There is no system property for the level. To see TRACE output, change the logger locally in
`quorus-core/src/test/resources/logback-test.xml` and do not commit the change:

```xml
<logger name="SimulatorTestRunner" level="TRACE"/>
```

The TRACE environment block records the host name and address, the user name, the working directory,
`java.home` and the temporary directory. Do not paste it into shared documents or tickets.

MDC keys set by the extension:

| Key | Value |
|---|---|
| `simulator` | Test class name without the `Test` suffix; for a `@Nested` class, the nested class name |
| `testClass` | Test class simple name |
| `testMethod` | Current test method name |
| `testPath` | `TestClass > Nested > method` |
| `correlationId` | `test-<digits>-<4 digits>`, unique per test |

The `quorus-core` file pattern prints `[%X{simulator}/%X{testMethod}]` after the level, for example:

```
2026-10-03 09:15:42.123 [main] INFO  [BasicTransferTests/testSubmitAndComplete] SimulatorTestRunner -   >> Should submit and complete transfer
```

---

## 3. Output markers

All markers are ASCII except the disabled-test line, which uses `⊖`.

Suite start (INFO), logged for the test class and again for each `@Nested` class:

```
================================================================================
>> SIMULATOR TEST SUITE: InMemoryTransferEngineSimulator
  Display Name: InMemoryTransferEngineSimulator Tests
  Test Class: InMemoryTransferEngineSimulatorTest
================================================================================
```

Per test:

```
  >> Should submit and complete transfer              (INFO, test start: display name)
    [PASS] (102ms)                                    (INFO)
    [FAIL] (63ms) - AssertionError - expected: <5> but was: <3>   (ERROR)
    [SKIP] ABORTED (4ms) - Assumption failed: ...     (WARN)
    ⊖ SKIPPED: Should retry - Disabled until RT-07    (INFO, @Disabled)
```

Suite end (INFO):

```
<< InMemoryTransferEngineSimulator complete
```

---

## 4. DEBUG and TRACE detail

A passed test adds one DEBUG line. Its text carries a `[TRACE]` label, but it is logged at DEBUG:

```
  |  [TRACE] PASSED in 102ms, mem=+1MB, threads=+0
```

A failed test adds a DEBUG block:

```
  |  [DEBUG] ==================== FAILURE DETAILS ====================
  |  [DEBUG] ERROR TYPE: java.lang.AssertionError
  |  [DEBUG] ERROR MESSAGE: expected: <5> but was: <3>
  |  [DEBUG] ROOT CAUSE: java.io.IOException - Connection refused
  |  [DEBUG] WALL TIME: 63ms, CPU: 796ms (delta: +63ms)
  |  [DEBUG] MEMORY: +2MB (current: 15MB), THREADS: +0 (current: 9)
  |  [DEBUG] CLASSES: +227 (loaded: 3553), GC: 1 collections, 4ms
  |  [DEBUG] =================================================================
```

`ROOT CAUSE` appears only when the exception has a cause. The full stack trace, including every nested
`Caused by:`, follows at TRACE under `FULL STACK TRACE`.

At TRACE the extension also logs:

- **At suite start**, an `ENVIRONMENT` block: `HOSTNAME`, `JAVA VERSION`, `JAVA HOME`, `JVM`, `OS`,
  `USER`, `PROCESSORS`, `MAX MEMORY`, `INITIAL HEAP`, `TIMEZONE`, `TEMP DIR`, `FILE ENCODING`,
  `LINE SEPARATOR`, `JVM ARGS`, `PID`, `UPTIME`, `BUILD TOOL`, and `SUREFIRE VERSION` and
  `PARALLEL CONFIG` when Surefire sets the corresponding system properties.
- **At each test start**: `CORRELATION ID`, `SIMULATOR`, `TEST METHOD`, `FULL PATH`, `START TIME`,
  `THREAD`, `MEMORY`, `THREADS` and `CLASSES`.
- **At suite end**: `JVM STATS (Suite End)` with heap, non-heap, thread count and GC count.

| Field | Source |
|---|---|
| `HOSTNAME` | `InetAddress.getLocalHost()` |
| `JAVA VERSION`, `JAVA HOME`, `JVM`, `OS`, `USER` | System properties |
| `PROCESSORS`, `MAX MEMORY`, `INITIAL HEAP` | `Runtime` |
| `JVM ARGS`, `PID`, `UPTIME` | `RuntimeMXBean`, `ProcessHandle` |
| `BUILD TOOL` | `maven.home` or `gradle.version`, otherwise "IDE or direct execution" |
| `WALL TIME` | Time between the extension's `beforeEach` and the test result |
| `CPU` | `ThreadMXBean.getCurrentThreadCpuTime()` |
| `MEMORY`, `THREADS`, `CLASSES` deltas | `Runtime`, `ThreadMXBean`, `ClassLoadingMXBean`, measured from test start |
| `GC` | Sum over `GarbageCollectorMXBean`s |

---

## 5. Failure isolation

Logging never changes a test result:

- Every callback is wrapped in `try`/`catch`. A failure prints one line to standard error, for example
  `[SimulatorTestLoggingExtension] Failed in beforeEach: <message>`, and the lifecycle callbacks log
  the exception at TRACE.
- MDC entries are removed in `afterEach` and, for the suite keys, in a `finally` block in `afterAll`.

---

## 6. Message conventions in simulators

Simulators log as `operation: description key=value`, at DEBUG for steps and INFO for outcomes:

```java
log.debug("submitTransfer: Received request jobId={}, source={}", jobId, source);
log.info("submitTransfer: Created transfer jobId={}, size={} bytes", jobId, size);
```

---

## 7. Using the output

- **Slow tests:** compare `WALL TIME` with `CPU`. High wall time and low CPU means waiting or I/O.
- **Leaks:** a positive `THREADS` delta after a test usually means an executor was not shut down; a
  large `MEMORY` delta means heavy allocation.
- **Tracing one test:** search the log for its `correlationId` (TRACE only).

*Last updated: 2026-10-03*
