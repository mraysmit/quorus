/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
  *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.mars.quorus.simulator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * JUnit 5 extension that provides clear, structured logging for simulator tests.
 * <p>
 * This extension logs:
 * <ul>
 *   <li>Test class/suite start with simulator name identification</li>
 *   <li>Nested class context (e.g., ChaosEngineeringTests, LifecycleTests)</li>
 *   <li>Individual test method start/end with timing</li>
 *   <li>Test outcomes (PASSED, FAILED, SKIPPED)</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 * {@literal @}ExtendWith(SimulatorTestLoggingExtension.class)
 * {@literal @}DisplayName("InMemoryAgentSimulator Tests")
 * class InMemoryAgentSimulatorTest {
 *     // tests...
 * }
 * </pre>
 */
public class SimulatorTestLoggingExtension implements 
        BeforeAllCallback, AfterAllCallback,
        BeforeEachCallback, AfterEachCallback,
        TestWatcher {

    private static final Logger log = LoggerFactory.getLogger("SimulatorTestRunner");
    
    private static final String BANNER_LINE = "═".repeat(80);
    private static final String SECTION_LINE = "─".repeat(60);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z").withZone(ZoneId.systemDefault());
    
    // MDC keys for contextual logging
    private static final String MDC_SIMULATOR = "simulator";
    private static final String MDC_TEST_CLASS = "testClass";
    private static final String MDC_TEST_METHOD = "testMethod";
    private static final String MDC_TEST_PATH = "testPath";
    private static final String MDC_CORRELATION_ID = "correlationId";
    
    // JVM monitoring
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private static final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private static final ClassLoadingMXBean classLoadingBean = ManagementFactory.getClassLoadingMXBean();
    private static final Runtime runtime = Runtime.getRuntime();
    
    // Store start time for duration calculation
    private static final ExtensionContext.Namespace NAMESPACE = 
        ExtensionContext.Namespace.create(SimulatorTestLoggingExtension.class);
    private static final String START_TIME_KEY = "startTime";
    private static final String MEMORY_START_KEY = "memoryStart";
    private static final String THREAD_COUNT_START_KEY = "threadCountStart";
    private static final String CPU_TIME_START_KEY = "cpuTimeStart";
    private static final String CLASS_COUNT_START_KEY = "classCountStart";

    @Override
    public void beforeAll(ExtensionContext context) {
        try {
            String simulatorName = extractSimulatorName(context);
            String displayName = getDisplayName(context);
            String testClassName = context.getRequiredTestClass().getSimpleName();
            
            // Set MDC for the test suite
            MDC.put(MDC_SIMULATOR, simulatorName);
            MDC.put(MDC_TEST_CLASS, testClassName);
            
            log.info("\n{}", BANNER_LINE);
            log.info("▶ SIMULATOR TEST SUITE: {}", simulatorName);
            log.info("  Display Name: {}", displayName);
            log.info("  Test Class: {}", testClassName);
            log.info("{}", BANNER_LINE);
            
            // Enhanced TRACE logging for environment/OS context
            log.trace("  [TRACE] ══════════════════════════ ENVIRONMENT ══════════════════════════");
            log.trace("  [TRACE] Initializing test suite for simulator: {}", simulatorName);
            log.trace("  [TRACE] Test class location: {}", context.getRequiredTestClass().getName());
            logEnvironmentContext();
            log.trace("  [TRACE] ══════════════════════════════════════════════════════════════════");
        } catch (Exception e) {
            // Graceful recovery - never let logging failure affect tests
            System.err.println("[SimulatorTestLoggingExtension] Failed in beforeAll: " + e.getMessage());
            log.trace("SimulatorTestLoggingExtension beforeAll error", e);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        try {
            String simulatorName = extractSimulatorName(context);
            
            // Suite summary at TRACE level (avoid log spam)
            log.trace("  [TRACE] ══════════════════════ SUITE SUMMARY ════════════════════════════");
            logJvmStatsTrace("Suite End");
            log.trace("  [TRACE] ══════════════════════════════════════════════════════════════════");
            
            // Minimal suite completion at INFO
            log.info("◀ {} complete", simulatorName);
        } catch (Exception e) {
            // Graceful recovery - never let logging failure affect tests
            System.err.println("[SimulatorTestLoggingExtension] Failed in afterAll: " + e.getMessage());
            log.trace("SimulatorTestLoggingExtension afterAll error", e);
        } finally {
            // Always clear MDC for test suite
            MDC.remove(MDC_SIMULATOR);
            MDC.remove(MDC_TEST_CLASS);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        try {
            // Store start time and JVM state for delta calculation
            Instant startTime = Instant.now();
            context.getStore(NAMESPACE).put(START_TIME_KEY, startTime);
            context.getStore(NAMESPACE).put(MEMORY_START_KEY, getUsedMemoryMB());
            context.getStore(NAMESPACE).put(THREAD_COUNT_START_KEY, threadBean.getThreadCount());
            context.getStore(NAMESPACE).put(CPU_TIME_START_KEY, getCurrentThreadCpuTimeMs());
            context.getStore(NAMESPACE).put(CLASS_COUNT_START_KEY, getLoadedClassCount());
            
            String testPath = buildTestPath(context);
            String displayName = context.getDisplayName();
            String simulatorName = extractSimulatorName(context);
            String methodName = context.getTestMethod().map(Method::getName).orElse("unknown");
            String correlationId = generateCorrelationId();
            
            // Set MDC for this specific test
            MDC.put(MDC_SIMULATOR, simulatorName);
            MDC.put(MDC_TEST_METHOD, methodName);
            MDC.put(MDC_TEST_PATH, testPath);
            MDC.put(MDC_CORRELATION_ID, correlationId);
            
            // Minimal INFO logging - just test name (one line)
            log.info("  ▶ {}", displayName);
            
            // Detailed metrics at TRACE level only (for deep debugging)
            log.trace("  │  [TRACE] ════════════════════════════════════════════════════════");
            log.trace("  │  [TRACE] CORRELATION ID: {}", correlationId);
            log.trace("  │  [TRACE] SIMULATOR: {}", simulatorName);
            log.trace("  │  [TRACE] TEST METHOD: {}", methodName);
            log.trace("  │  [TRACE] FULL PATH: {}", testPath);
            log.trace("  │  [TRACE] START TIME: {}", TIMESTAMP_FORMAT.format(startTime));
            log.trace("  │  [TRACE] THREAD: name={}, id={}, priority={}, group={}", 
                Thread.currentThread().getName(), 
                Thread.currentThread().threadId(),
                Thread.currentThread().getPriority(),
                Thread.currentThread().getThreadGroup().getName());
            log.trace("  │  [TRACE] MEMORY: heap={}MB, nonHeap={}MB, free={}MB, used={}MB", 
                getHeapMemoryMB(), getNonHeapMemoryMB(), getFreeMemoryMB(), getUsedMemoryMB());
            log.trace("  │  [TRACE] THREADS: active={}, peak={}, daemon={}", 
                threadBean.getThreadCount(),
                threadBean.getPeakThreadCount(),
                threadBean.getDaemonThreadCount());
            log.trace("  │  [TRACE] CLASSES: loaded={}, total={}, unloaded={}", 
                getLoadedClassCount(),
                getTotalLoadedClassCount(),
                getUnloadedClassCount());
            log.trace("  │  [TRACE] ════════════════════════════════════════════════════════");
        } catch (Exception e) {
            // Graceful recovery - never let logging failure affect tests
            System.err.println("[SimulatorTestLoggingExtension] Failed in beforeEach: " + e.getMessage());
            log.trace("SimulatorTestLoggingExtension beforeEach error", e);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        // Always clear test-specific MDC entries, even if other operations fail
        try {
            MDC.remove(MDC_TEST_METHOD);
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_TEST_PATH);
        } catch (Exception e) {
            // Graceful recovery - MDC cleanup should never fail tests
            System.err.println("[SimulatorTestLoggingExtension] Failed to clear MDC: " + e.getMessage());
        }
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        try {
            Duration duration = calculateDuration(context);
            // Minimal success logging - just result (no metrics spam)
            log.info("    ✓ PASSED ({}ms)", duration.toMillis());
            // Detailed completion metrics only at TRACE level for successful tests
            logTestCompletionTrace(context, "PASSED", duration);
        } catch (Exception e) {
            // Graceful recovery - never let logging failure affect tests
            System.err.println("[SimulatorTestLoggingExtension] Failed to log test success: " + e.getMessage());
        }
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        try {
            Duration duration = calculateDuration(context);
            
            // Brief failure summary at ERROR level - no stack trace
            log.error("    ✗ FAILED ({}ms) - {} - {}", 
                duration.toMillis(),
                cause.getClass().getSimpleName(), 
                truncateMessage(cause.getMessage(), 80));
            
            // Detailed failure info at DEBUG level (failures warrant more detail)
            log.debug("  │  [DEBUG] ════════════════════ FAILURE DETAILS ════════════════════");
            log.debug("  │  [DEBUG] ERROR TYPE: {}", cause.getClass().getName());
            log.debug("  │  [DEBUG] ERROR MESSAGE: {}", cause.getMessage());
            if (cause.getCause() != null) {
                log.debug("  │  [DEBUG] ROOT CAUSE: {} - {}", 
                    cause.getCause().getClass().getName(), cause.getCause().getMessage());
            }
            // Show completion metrics at DEBUG for failures (helps diagnose resource issues)
            logTestCompletionDebug(context, "FAILED", duration);
            log.debug("  │  [DEBUG] ══════════════════════════════════════════════════════════");
            
            // Full stack trace ONLY at TRACE level
            log.trace("  │  [TRACE] ════════════════════ FULL STACK TRACE ════════════════════");
            StackTraceElement[] stack = cause.getStackTrace();
            for (int i = 0; i < stack.length; i++) {
                log.trace("  │  [TRACE]   at {}", stack[i]);
            }
            // Log nested causes at TRACE level
            Throwable nested = cause.getCause();
            while (nested != null) {
                log.trace("  │  [TRACE] Caused by: {}: {}", 
                    nested.getClass().getName(), nested.getMessage());
                for (StackTraceElement element : nested.getStackTrace()) {
                    log.trace("  │  [TRACE]   at {}", element);
                }
                nested = nested.getCause();
            }
            log.trace("  │  [TRACE] ══════════════════════════════════════════════════════════");
        } catch (Exception e) {
            // Graceful recovery - never let logging failure propagate
            System.err.println("[SimulatorTestLoggingExtension] Failed to log test failure: " + e.getMessage());
        }
    }
    
    /**
     * Truncates a message to maxLength, adding "..." if truncated.
     */
    private String truncateMessage(String message, int maxLength) {
        if (message == null) {
            return "null";
        }
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength - 3) + "...";
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        try {
            Duration duration = calculateDuration(context);
            log.warn("    ⊘ ABORTED ({}ms) - {}", duration.toMillis(), 
                cause != null ? cause.getMessage() : "unknown");
            
            // Log full abort details at TRACE level only
            if (cause != null) {
                log.trace("Test aborted with exception", cause);
            }
        } catch (Exception e) {
            // Graceful recovery - never let logging failure affect tests
            System.err.println("[SimulatorTestLoggingExtension] Failed to log test abort: " + e.getMessage());
        }
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        try {
            String displayName = context.getDisplayName();
            log.info("    \u2296 SKIPPED: {} - {}", displayName, reason.orElse("no reason"));
        } catch (Exception e) {
            // Graceful recovery - never let logging failure affect tests
            System.err.println("[SimulatorTestLoggingExtension] Failed to log disabled test: " + e.getMessage());
        }
    }

    /**
     * Extracts the simulator name from the test class name.
     * E.g., "InMemoryAgentSimulatorTest" -> "InMemoryAgentSimulator"
     */
    private String extractSimulatorName(ExtensionContext context) {
        String className = context.getRequiredTestClass().getSimpleName();
        
        // Handle nested classes
        if (className.contains("$")) {
            className = className.substring(0, className.indexOf("$"));
        }
        
        // Remove "Test" suffix
        if (className.endsWith("Test")) {
            className = className.substring(0, className.length() - 4);
        }
        
        return className;
    }

    /**
     * Gets the @DisplayName value or falls back to class name.
     */
    private String getDisplayName(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        DisplayName displayName = testClass.getAnnotation(DisplayName.class);
        return displayName != null ? displayName.value() : testClass.getSimpleName();
    }

    /**
     * Builds the full test path including nested class context.
     * E.g., "InMemoryAgentSimulatorTest > ChaosEngineeringTests > testNetworkPartition"
     */
    private String buildTestPath(ExtensionContext context) {
        StringBuilder path = new StringBuilder();
        
        // Build the path from parent to current
        ExtensionContext current = context;
        java.util.Deque<String> segments = new java.util.ArrayDeque<>();
        
        while (current != null) {
            if (current.getTestMethod().isPresent()) {
                segments.addFirst(current.getTestMethod().get().getName());
            } else if (current.getTestClass().isPresent()) {
                String className = current.getTestClass().get().getSimpleName();
                // For nested classes, just use the simple name
                if (className.contains("$")) {
                    className = className.substring(className.lastIndexOf("$") + 1);
                }
                segments.addFirst(className);
            }
            current = current.getParent().orElse(null);
        }
        
        return String.join(" > ", segments);
    }

    /**
     * Calculates the duration since test start.
     */
    private Duration calculateDuration(ExtensionContext context) {
        Instant startTime = context.getStore(NAMESPACE).get(START_TIME_KEY, Instant.class);
        if (startTime != null) {
            return Duration.between(startTime, Instant.now());
        }
        return Duration.ZERO;
    }
    
    // ==================== DevOps Diagnostic Helper Methods ====================
    
    /**
     * Generates a unique correlation ID for tracing test execution across logs.
     */
    private String generateCorrelationId() {
        return String.format("test-%d-%04d", 
            System.currentTimeMillis() % 100000000,
            (int)(Math.random() * 10000));
    }
    
    /**
     * Logs environment context for DevOps troubleshooting (TRACE level).
     */
    private void logEnvironmentContext() {
        // Host and network info
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            log.trace("  [TRACE] HOSTNAME: {} ({})", hostname, hostAddress);
        } catch (Exception e) {
            log.trace("  [TRACE] HOSTNAME: unknown ({})", e.getMessage());
        }
        
        // Java runtime info
        log.trace("  [TRACE] JAVA VERSION: {} ({})", 
            System.getProperty("java.version"), 
            System.getProperty("java.vendor"));
        log.trace("  [TRACE] JAVA HOME: {}", System.getProperty("java.home"));
        log.trace("  [TRACE] JVM: {} {}", 
            System.getProperty("java.vm.name"),
            System.getProperty("java.vm.version"));
        
        // OS info
        log.trace("  [TRACE] OS: {} {} ({})", 
            System.getProperty("os.name"),
            System.getProperty("os.version"),
            System.getProperty("os.arch"));
        log.trace("  [TRACE] USER: {} @ {}", 
            System.getProperty("user.name"),
            System.getProperty("user.dir"));
        
        // Resource limits
        log.trace("  [TRACE] PROCESSORS: {}", runtime.availableProcessors());
        log.trace("  [TRACE] MAX MEMORY: {}MB", runtime.maxMemory() / (1024 * 1024));
        log.trace("  [TRACE] INITIAL HEAP: {}MB", runtime.totalMemory() / (1024 * 1024));
        log.trace("  [TRACE] TIMEZONE: {}", ZoneId.systemDefault());
        
        // File system info for I/O tests
        log.trace("  [TRACE] TEMP DIR: {}", System.getProperty("java.io.tmpdir"));
        log.trace("  [TRACE] FILE ENCODING: {}", System.getProperty("file.encoding"));
        log.trace("  [TRACE] LINE SEPARATOR: {}", 
            System.getProperty("line.separator").replace("\r", "\\r").replace("\n", "\\n"));
        
        // JVM arguments for debugging configuration issues
        logJvmArguments();
        
        // Maven/test framework info
        logTestFrameworkContext();
    }
    
    /**
     * Logs JVM arguments - critical for DevOps to understand test configuration (TRACE level).
     */
    private void logJvmArguments() {
        var runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        var jvmArgs = runtimeMxBean.getInputArguments();
        if (!jvmArgs.isEmpty()) {
            log.trace("  [TRACE] JVM ARGS: {}", String.join(" ", jvmArgs));
        } else {
            log.trace("  [TRACE] JVM ARGS: (none)");
        }
        log.trace("  [TRACE] PID: {}", ProcessHandle.current().pid());
        log.trace("  [TRACE] UPTIME: {}s", runtimeMxBean.getUptime() / 1000);
    }
    
    /**
     * Logs test framework and build tool context (TRACE level).
     */
    private void logTestFrameworkContext() {
        // Detect Maven vs Gradle vs IDE
        String mvnHome = System.getProperty("maven.home");
        String gradleVersion = System.getProperty("gradle.version");
        String surefireVersion = System.getProperty("surefire.version");
        
        if (mvnHome != null) {
            log.trace("  [TRACE] BUILD TOOL: Maven ({})", mvnHome);
        } else if (gradleVersion != null) {
            log.trace("  [TRACE] BUILD TOOL: Gradle {}", gradleVersion);
        } else {
            log.trace("  [TRACE] BUILD TOOL: IDE or direct execution");
        }
        
        if (surefireVersion != null) {
            log.trace("  [TRACE] SUREFIRE VERSION: {}", surefireVersion);
        }
        
        // Parallel execution detection
        String forkCount = System.getProperty("surefire.forkCount");
        String threadCount = System.getProperty("surefire.threadCount");
        if (forkCount != null || threadCount != null) {
            log.trace("  [TRACE] PARALLEL CONFIG: forks={}, threads={}", 
                forkCount != null ? forkCount : "1", 
                threadCount != null ? threadCount : "1");
        }
    }
    
    /**
     * Logs current JVM statistics at TRACE level.
     */
    private void logJvmStatsTrace(String phase) {
        log.trace("  [TRACE] JVM STATS ({}): heap={}MB, nonHeap={}MB, threads={}, gcCount={}", 
            phase,
            getHeapMemoryMB(),
            getNonHeapMemoryMB(),
            threadBean.getThreadCount(),
            getGcCount());
    }
    
    /**
     * Logs test completion metrics at TRACE level (for successful tests).
     */
    private void logTestCompletionTrace(ExtensionContext context, String status, Duration duration) {
        Long memoryStart = context.getStore(NAMESPACE).get(MEMORY_START_KEY, Long.class);
        Integer threadStart = context.getStore(NAMESPACE).get(THREAD_COUNT_START_KEY, Integer.class);
        
        long memoryDelta = memoryStart != null ? getUsedMemoryMB() - memoryStart : 0;
        int threadDelta = threadStart != null ? threadBean.getThreadCount() - threadStart : 0;
        
        log.trace("  │  [TRACE] {} in {}ms, mem={}, threads={}", 
            status, duration.toMillis(),
            memoryDelta >= 0 ? "+" + memoryDelta + "MB" : memoryDelta + "MB",
            threadDelta >= 0 ? "+" + threadDelta : threadDelta);
    }
    
    /**
     * Logs test completion metrics at DEBUG level (for failed tests - more detail needed).
     */
    private void logTestCompletionDebug(ExtensionContext context, String status, Duration duration) {
        Long memoryStart = context.getStore(NAMESPACE).get(MEMORY_START_KEY, Long.class);
        Integer threadStart = context.getStore(NAMESPACE).get(THREAD_COUNT_START_KEY, Integer.class);
        Long cpuTimeStart = context.getStore(NAMESPACE).get(CPU_TIME_START_KEY, Long.class);
        Integer classCountStart = context.getStore(NAMESPACE).get(CLASS_COUNT_START_KEY, Integer.class);
        
        long memoryDelta = memoryStart != null ? getUsedMemoryMB() - memoryStart : 0;
        int threadDelta = threadStart != null ? threadBean.getThreadCount() - threadStart : 0;
        long cpuTimeDelta = cpuTimeStart != null ? getCurrentThreadCpuTimeMs() - cpuTimeStart : 0;
        int classCountDelta = classCountStart != null ? getLoadedClassCount() - classCountStart : 0;
        
        log.debug("  │  [DEBUG] WALL TIME: {}ms, CPU: {}ms (delta: {}ms)", 
            duration.toMillis(), getCurrentThreadCpuTimeMs(), 
            cpuTimeDelta >= 0 ? "+" + cpuTimeDelta : cpuTimeDelta);
        log.debug("  │  [DEBUG] MEMORY: {}MB (current: {}MB), THREADS: {} (current: {})", 
            memoryDelta >= 0 ? "+" + memoryDelta : memoryDelta, getUsedMemoryMB(),
            threadDelta >= 0 ? "+" + threadDelta : threadDelta, threadBean.getThreadCount());
        log.debug("  │  [DEBUG] CLASSES: {} (loaded: {}), GC: {} collections, {}ms", 
            classCountDelta >= 0 ? "+" + classCountDelta : classCountDelta, getLoadedClassCount(),
            getGcCount(), getGcTimeMs());
    }
    
    // ==================== Memory and Thread Metrics ====================
    
    private long getHeapMemoryMB() {
        return memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
    }
    
    private long getNonHeapMemoryMB() {
        return memoryBean.getNonHeapMemoryUsage().getUsed() / (1024 * 1024);
    }
    
    private long getFreeMemoryMB() {
        return runtime.freeMemory() / (1024 * 1024);
    }
    
    private long getUsedMemoryMB() {
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }
    
    private long getGcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
            .mapToLong(gc -> gc.getCollectionCount())
            .filter(count -> count >= 0)
            .sum();
    }
    
    private long getGcTimeMs() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
            .mapToLong(gc -> gc.getCollectionTime())
            .filter(time -> time >= 0)
            .sum();
    }
    
    private long getCurrentThreadCpuTimeMs() {
        if (threadBean.isCurrentThreadCpuTimeSupported()) {
            return threadBean.getCurrentThreadCpuTime() / 1_000_000; // nano to ms
        }
        return -1;
    }
    
    private int getLoadedClassCount() {
        return classLoadingBean.getLoadedClassCount();
    }
    
    private long getTotalLoadedClassCount() {
        return classLoadingBean.getTotalLoadedClassCount();
    }
    
    private long getUnloadedClassCount() {
        return classLoadingBean.getUnloadedClassCount();
    }
}
