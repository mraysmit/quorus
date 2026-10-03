# Negative Testing Strategy

How Quorus marks tests that deliberately exercise failure paths, so that the ERROR lines they produce
are not mistaken for real failures. Lanes, tags and the consolidated test log are described in the
[Testing Guide](QUORUS_TESTING_README.md); this document does not repeat them.

---

## 1. The convention: `@ExpectsError`

A test that drives production code into an error path, and therefore logs at ERROR, is annotated
with `@ExpectsError` and a short reason:

```java
@Test
@ExpectsError("Connection refused -- no FTP server at 127.0.0.1")
void testTransferWithValidFtpUri() {
    // ... assert on the exception or result, not on the log
}
```

`@ExpectsError` (`quorus-core/src/test/java/dev/mars/quorus/testing/ExpectsError.java`) is a method
annotation meta-annotated with `@ExtendWith(ExpectsErrorExtension.class)`, so no class-level
registration is needed. Before and after the test, `ExpectsErrorExtension` logs a WARN banner:

```
========================================================================
  EXPECTED ERROR TEST: testTransferWithValidFtpUri
  Reason: Connection refused -- no FTP server at 127.0.0.1
  Errors below are INTENTIONAL -- testing error handling paths
========================================================================
... ERROR lines from the code under test ...
========================================================================
  END EXPECTED ERROR TEST: testTransferWithValidFtpUri -- PASSED
========================================================================
```

The closing banner reports `PASSED` or `FAILED`. An ERROR line that is not inside such a banner is
unexpected and should be investigated.

These tests run in the default build. The annotation is used in `quorus-core`, `quorus-workflow` and
`quorus-controller`. `quorus-core` publishes its test classes as a test jar, and a module that uses the
annotation depends on it:

```xml
<dependency>
    <groupId>dev.mars</groupId>
    <artifactId>quorus-core</artifactId>
    <version>${project.version}</version>
    <type>test-jar</type>
    <scope>test</scope>
</dependency>
```

`quorus-workflow`, `quorus-controller` and `quorus-agent` already have it.

---

## 2. The `negative` tag

`quorus-core/src/test/java/dev/mars/quorus/protocol/errorhandling/` holds protocol input-validation
tests tagged `@Tag("negative")`:

| Class | Notes |
|---|---|
| `ProtocolErrorHandlingTestBase` | Abstract base, tagged `negative` |
| `FtpTransferProtocolErrorHandlingTest` | Also uses `@ExpectsError` |
| `SftpTransferProtocolErrorHandlingTest` | |
| `SmbTransferProtocolErrorHandlingTest` | Also uses `@ExpectsError` |

The tag no longer excludes anything: `quorus-core` has no `excludedGroups`, so these tests run in every
default build (since commit `8864c2f`). The tag is kept so that the group can be run on its own:

```bash
mvn test -pl quorus-core -Pnegative-tests
```

The `negative-tests` profile sets `<groups>negative</groups>`. The `all-tests` profile now runs the same
tests as the default build.

---

## 3. Principles

1. **Mark intent, do not hide output.** `@ExpectsError` explains ERROR lines; it does not suppress
   them, and production code has no test-detection logic.
2. **Assert on outcomes, not logs.** Assert the exception, error payload, metrics or state.
3. **Give a reason that names the error.** The reason appears in the banner, so it should say what
   fails and why (for example "Connection refused -- no FTP server").
4. **Use the annotation per method.** Only the methods that log errors carry it, so the banners stay
   specific.
