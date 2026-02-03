# Negative Testing Strategy

This document describes how Quorus separates intentional failure tests from happy-path tests to keep test output clean and signals clear.

---

## Quick Start

### Running Tests

```bash
# Default: Run happy-path tests only (excludes negative tests)
mvn test -pl quorus-core

# Run only negative/error handling tests
mvn test -pl quorus-core -Pnegative-tests

# Run all tests including negative tests
mvn test -pl quorus-core -Pall-tests
```

### Test Counts

| Profile | Tests | Description |
|---------|-------|-------------|
| **Default** | 898 | Happy-path tests, excludes negative |
| **negative-tests** | 27 | Only error handling tests |
| **all-tests** | 925 | Complete test suite |

### Log File Location

Test logs are written to both console and file:

```
quorus-core/target/test-logs/quorus-core-tests.log
```

---

## How It Works

### Package Structure

Negative/error handling tests are in a dedicated package:

```
quorus-core/src/test/java/dev/mars/quorus/protocol/errorhandling/
├── ProtocolErrorHandlingTestBase.java    # Base class with @Tag("negative")
├── SftpTransferProtocolErrorHandlingTest.java
├── FtpTransferProtocolErrorHandlingTest.java
└── SmbTransferProtocolErrorHandlingTest.java
```

### JUnit 5 Tags

All error handling test classes are annotated with `@Tag("negative")`:

```java
@Tag("negative")
@DisplayName("SFTP Protocol Error Handling Tests")
class SftpTransferProtocolErrorHandlingTest extends ProtocolErrorHandlingTestBase {
    // Tests for invalid URIs, missing hosts, etc.
}
```

### Quiet Logging for Expected Failures

Protocol implementations detect test scenarios and log at INFO instead of ERROR:

```java
// Before: ERROR with stack trace (noisy)
logger.error("SFTP transfer failed: {}", e.getMessage(), e);

// After: INFO without stack trace for test scenarios
logger.info("INTENTIONAL TEST FAILURE: SFTP transfer failed for test case '{}': {}",
           requestId, e.getMessage());
```

### Example Test Output

When running negative tests, you'll see clean INFO-level messages:

```
13:19:27.511 [main] INFO  d.m.q.protocol.FtpTransferProtocol - INTENTIONAL TEST FAILURE: FTP download failed for test case 'test-exception-id': nonexistent.server.com
13:19:27.517 [main] INFO  d.m.q.protocol.FtpTransferProtocol - INTENTIONAL TEST FAILURE: FTP download failed for test case 'test-invalid-ftp': FTP URI must specify a path
13:19:27.521 [main] INFO  d.m.q.protocol.FtpTransferProtocol - INTENTIONAL TEST FAILURE: FTP download failed for test case 'test-missing-host': FTP URI must specify a host
```

---

## Configuration Details

### Maven Surefire Configuration

The `quorus-core/pom.xml` excludes negative tests by default:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <configuration>
                <!-- Exclude negative/error handling tests by default -->
                <excludedGroups>negative</excludedGroups>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### Maven Profiles

```xml
<profiles>
    <!-- Profile to run all tests including negative tests -->
    <profile>
        <id>all-tests</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration combine.self="override">
                        <!-- Include all tests - no exclusions -->
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>

    <!-- Profile to run only negative tests -->
    <profile>
        <id>negative-tests</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration combine.self="override">
                        <groups>negative</groups>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

### Logback Configuration

Test logs output to both console and file. Configuration is in `quorus-core/src/test/resources/logback-test.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    
    <!-- Console appender -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- File appender -->
    <appender name="FILE" class="ch.qos.logback.core.FileAppender">
        <file>target/test-logs/quorus-core-tests.log</file>
        <append>false</append>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- Rolling file appender (rotates by size) -->
    <appender name="ROLLING_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>target/test-logs/quorus-core-tests-rolling.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>target/test-logs/quorus-core-tests.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxFileSize>10MB</maxFileSize>
            <maxHistory>5</maxHistory>
            <totalSizeCap>50MB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- Quorus packages at DEBUG level -->
    <logger name="dev.mars.quorus" level="DEBUG"/>
    
    <!-- Reduce noise from third-party libraries -->
    <logger name="org.apache" level="WARN"/>
    <logger name="io.vertx" level="WARN"/>
    <logger name="io.netty" level="WARN"/>
    
    <root level="INFO">
        <!-- Output to both console AND file -->
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>
    
</configuration>
```

#### Appender Options

| Appender | Description |
|----------|-------------|
| `CONSOLE` | Real-time console output |
| `FILE` | Simple file appender (overwrites each run) |
| `ROLLING_FILE` | Rotates by size/date, keeps history |

#### Log Output Variants

```xml
<!-- Console only -->
<root level="INFO">
    <appender-ref ref="CONSOLE"/>
</root>

<!-- File only (quiet console) -->
<root level="INFO">
    <appender-ref ref="FILE"/>
</root>

<!-- Both (current default) -->
<root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="FILE"/>
</root>
```

---

## Implementation Details

### Intentional Test Failure Detection

Protocol implementations use `isIntentionalTestFailure()` to detect test scenarios:

```java
private boolean isIntentionalTestFailure(String requestId) {
    if (requestId == null) {
        return false;
    }
    return requestId.startsWith("test-") ||
           requestId.contains("-test") ||
           requestId.contains("test-invalid") ||
           requestId.contains("test-missing") ||
           requestId.contains("test-exception") ||
           requestId.contains("test-timeout");
}
```

### Files Modified

| File | Changes |
|------|---------|
| `quorus-core/pom.xml` | Added Surefire excludedGroups and Maven profiles |
| `ProtocolErrorHandlingTestBase.java` | Created base class with `@Tag("negative")` |
| `SftpTransferProtocolErrorHandlingTest.java` | Created with `@Tag("negative")` |
| `FtpTransferProtocolErrorHandlingTest.java` | Created with `@Tag("negative")` |
| `SmbTransferProtocolErrorHandlingTest.java` | Created with `@Tag("negative")` |
| `SftpTransferProtocol.java` | Added `isIntentionalTestFailure()`, updated logging |
| `FtpTransferProtocol.java` | Added `isIntentionalTestFailure()`, updated logging |
| `SmbTransferProtocol.java` | Added `isIntentionalTestFailure()`, updated logging |
| `HttpTransferProtocol.java` | Added `isIntentionalTestFailure()`, updated logging |
| `SimpleTransferEngine.java` | Added `isIntentionalTestFailure()`, updated logging |

---

## CI/CD Integration

```yaml
# GitHub Actions example
jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - name: Run unit tests (excludes negative)
        run: mvn test -pl quorus-core

  negative-tests:
    runs-on: ubuntu-latest
    steps:
      - name: Run negative tests
        run: mvn test -pl quorus-core -Pnegative-tests

  all-tests:
    runs-on: ubuntu-latest
    if: github.event_name == 'push' && github.ref == 'refs/heads/master'
    steps:
      - name: Run complete test suite
        run: mvn test -pl quorus-core -Pall-tests
```

---

## Background: Why Separate Negative Tests?

If a test is *supposed* to fail (or trigger noisy error paths), mixing it into the same suite/log stream as happy-path regression causes confusion and ignored signals.

### Key Principles

1. **Separate by test category** — Put intentional failure tests in a separate package
2. **Use JUnit 5 `@Tag`** — Gate execution with tags like `@Tag("negative")`
3. **Fix logging config** — Expected errors shouldn't pollute logs at ERROR level
4. **Don't assert on logs** — Assert on exceptions, error payloads, metrics, or state
5. **Make expected failures quiet** — Log at INFO/WARN, not ERROR with stack traces

### Why "separate package" alone isn't enough

Moving tests into a separate package helps humans browse, but unless you **change how they are executed**, they'll still run together and write to the same log sink.

**Solution:** Separate + tag + separate execution.

This gives testing staff a clean signal: if the "happy" suite shows errors in logs, it's real.
