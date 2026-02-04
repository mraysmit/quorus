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

### Why This Works

When negative tests are tagged and excluded from the default run:
- Default `mvn test` runs only happy-path tests
- Any ERROR in the default test output is a **real bug**
- Negative tests run separately with `-Pnegative-tests`
- Production code remains clean — no test-aware logic

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
    
    <!-- Quorus packages at DEBUG level -->
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

---

## Files to Modify

| File | Changes |
|------|---------|
| `quorus-core/pom.xml` | Add Surefire excludedGroups and Maven profiles |
| `ProtocolErrorHandlingTestBase.java` | Create base class with `@Tag("negative")` |
| `*ErrorHandlingTest.java` | Create test classes with `@Tag("negative")` |

**Important:** Production code (protocols, engines) remains unchanged. No test-detection logic in production.

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

## Key Principles

1. **Separate by test category** — Put intentional failure tests in a separate package
2. **Use JUnit 5 `@Tag`** — Gate execution with tags like `@Tag("negative")`
3. **Exclude from default run** — Configure Maven Surefire to exclude `negative` tag by default
4. **Don't assert on logs** — Assert on exceptions, error payloads, metrics, or state
5. **Keep production code clean** — No test-detection logic in production classes

### Why "separate package" alone isn't enough

Moving tests into a separate package helps humans browse, but unless you **change how they are executed**, they'll still run together and write to the same log sink.

**Solution:** Separate package + `@Tag` + Maven profile exclusion.

This gives testing staff a clean signal: if the default test suite shows ERROR in logs, it's a real bug.
