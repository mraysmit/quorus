<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Concurrency Conventions

**Version:** 1.1  
**Date:** 2026-09-26  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Status:** Active. Applies to all code that has left Vert.x, and to all new code that does not need Vert.x types  
**Scope:** Concurrency, cancellation, context propagation and asynchronous testing after the Vert.x exit

These conventions implement [ADR-0012](../architecture-decisions/ADR-0012-JAVA-RUNTIME-AND-STRUCTURED-CONCURRENCY.md). The
supporting code is [`TaskScope`](../../quorus-core/src/main/java/dev/mars/quorus/concurrent/TaskScope.java) (plan items
RT-02a and RT-02b). Modules still on Vert.x keep their existing Vert.x rules until their `RT` migration item lands. They
must not gain new Vert.x coupling where a JDK-typed interface would do.

## 1. Execution model

- Write plain blocking code, and run it on virtual threads. There is no event loop to protect, no
  `executeBlocking`, and no callback or `Future` composition for sequencing work.
- Blocking I/O (FTP, SFTP, SMB, NFS, HTTP, file system, gRPC calls) runs directly on the virtual thread
  that needs the result.
- Do not create raw threads, `ExecutorService` pools or `CompletableFuture` chains to run a unit of work
  concurrently. Use a `TaskScope`. An explicitly bounded resource, such as a `Semaphore` capping
  concurrent transfers, is a limit on concurrency, not a separate model.
- `StructuredTaskScope` is a preview API in Java 27. Never compile Quorus with `--enable-preview`
  (decision RT-Q1). `TaskScope` mirrors its API, so the switch is mechanical once it is final in an
  adopted release.

## 2. Using `TaskScope`

```java
// Illustrative: RequestContext and the protocol calls are example names, not existing classes.
try (TaskScope scope = TaskScope.builder("replicate-segment", Duration.ofSeconds(30))
        .inherit(RequestContext.TENANT, RequestContext.IDENTITY)
        .open()) {
    Subtask<Checksum> source = scope.fork(() -> sourceProtocol.checksum(path));
    Subtask<Checksum> target = scope.fork(() -> targetProtocol.checksum(path));
    scope.join();                       // throws FailedException or TimeoutException
    verify(source.get(), target.get());
}
```

- **Always use try-with-resources.** `close()` cancels unfinished subtasks and does not return until
  every subtask thread has ended, so no work outlives the scope.
- **Fork, then join once.** Only the thread that opened the scope may fork, join or close it. Other
  threads get `WrongThreadException`. Forking after `join()`, or joining twice, is an
  `IllegalStateException`.
- **Every scope has a deadline.** Choose it for the whole unit of work, not per call. When the deadline
  expires, `join()` throws `TaskScope.TimeoutException` and the unfinished subtasks are cancelled.
- **Handle both outcomes.** A subtask failure makes `join()` throw `TaskScope.FailedException`, whose
  cause is the first failure, and cancels the siblings. Read results with `Subtask.get()` only after
  a successful `join()`.
- **A subtask must end promptly when interrupted.** Cancellation is interruption. Never swallow
  `InterruptedException`: rethrow it, or restore the interrupt status and return. Blocking calls that
  ignore interruption (some socket and library calls) must have their own timeout, or cancellation
  cannot take effect and `close()` waits for them.
- **Name scopes after the unit of work** (`replicate-segment`, `poll-assignments`). The name becomes the
  span name `taskscope <name>` and the subtask thread-name prefix.
- **Keep subtasks coarse.** Every scope and every subtask produces a span. Fork a transfer, a protocol
  call or a polling loop, not a single field computation.
- **Fork only under the bindings in force when the scope opened.** Do not call `fork` inside a nested
  `ScopedValue.where(...)`, and do not let a scope escape the `where(...)` that opened it. A declared
  key bound differently at `fork` throws `TaskScope.StructureViolationException`. Final APIs cannot
  see undeclared keys, so `TaskScope` cannot detect changes to those, but `StructuredTaskScope` can
  and will reject them after migration. Treat the rule as absolute.
- **Close scopes in reverse order of opening.** Nested try-with-resources does this automatically.
  Closing an outer scope while an inner one is still open closes the inner scope first, then throws
  `TaskScope.StructureViolationException`, with any close failures attached as suppressed exceptions.

## 3. Context propagation

A subtask runs on a fresh virtual thread and starts with no per-thread state. `TaskScope` propagates:

| Context | Propagation | Rule for code |
|---|---|---|
| OpenTelemetry context | Automatic. The scope span is a child of the owner's current span, each subtask span is a child of the scope span, and each subtask span is current while it runs | Start spans normally inside a subtask; they nest under it. Do not pass `Context` by hand |
| Span outcome | Automatic. `quorus.taskscope.outcome` is `success`, `failed` (exception and error status), `cancelled` or `timeout` | Do not set these attributes yourself |
| SLF4J MDC | Automatic. Each fork copies the owner's MDC and adds the subtask's `traceId` and `spanId`; it is cleared when the subtask ends | Set MDC keys in the owner before forking. A subtask may add keys; they never leak back. With tracing disabled, the owner's `traceId` and `spanId` are kept |
| `ScopedValue` | **Only keys declared with `Builder.inherit(...)`**, captured when the scope opens | Declare every request or security context key a subtask needs. An undeclared key is silently unbound in the subtask, so cover each consumer with a test that reads its context inside a subtask |

Request and security context (tenant, identity, request ID) belongs in `ScopedValue`, not in
thread-locals. The MDC stays the bridge to logging, because logback reads only the MDC. The MDC keys in
use are `requestId`, `traceId`, `spanId`, `nodeId`, `raftRole`, `raftTerm`, `rpcType` and `agentId`.

## 4. Migration to `StructuredTaskScope`

`StructuredTaskScope` is a preview API in JDK 27. It is also still preview in JDK 28 early access
(28-ea+17), and no structured-concurrency JEP was targeted to JDK 28 as of 2026-09-26. When a Java
release that Quorus adopts (plan item `RT-09`) makes it final, the switch happens inside `TaskScope`,
and callers do not change:

| `TaskScope` today | After the switch |
|---|---|
| Fork, join, cancellation and deadline on virtual threads | Delegated to `StructuredTaskScope` with its default all-must-succeed policy and the scope deadline as its timeout |
| `FailedException`, `TimeoutException`, `StructureViolationException` | Kept as Quorus types, mapped from the JDK exceptions |
| Structure checks for declared keys and close order | Removed; the JDK enforces the same rules, and more completely |
| `Builder.inherit(...)` | Redundant, because every binding is inherited. The declarations can be deleted |
| Tracing, span outcomes, MDC propagation | Kept. The JDK scope does not carry thread-locals, so each fork keeps wrapping its task |

Code that follows §2 today needs no change at migration. The `TaskScope*Test` classes are the
regression gate for the switch.

## 5. Shared state

- Give every piece of mutable state one owner and state how it is serialised: confined to one thread,
  guarded by a named lock, or immutable.
- Keep critical sections short, and never perform blocking I/O while holding a lock.
- `synchronized` and `ReentrantLock` are both acceptable: since JDK 24, `synchronized` no longer pins
  virtual threads. Use `ReentrantLock` when you need a `Condition` or a timed acquire.
- Prefer immutable records and copy-on-publish over shared mutable collections.

## 6. Asynchronous test standard

This standard replaces the Vert.x test facilities for code that has left Vert.x (plan §6.1).

- **Test the blocking API directly**, from the test thread or a virtual thread. Do not wrap it in
  futures to "await".
- **Every concurrency test has a preemptive timeout:**
  `@Timeout(value = 10, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)`, so a hang
  fails fast instead of stalling the build.
- **Synchronise with handshakes and interruption only.** Use a `CompletableFuture` that one side completes
  and the other joins, or a task that blocks on an incomplete future until it is interrupted. Sleeps
  used for synchronisation, Awaitility, and polling or spin loops over state are prohibited.
- **Establish order explicitly.** If a test cancels or closes a scope and then expects a subtask to
  observe it, the subtask must first signal that it has started. Without the handshake the scope may
  legitimately cancel the subtask before it runs, and the test hangs (seen in RT-02b).
- **To keep a subtask alive past cancellation,** block it in `CompletableFuture.join()`, which ignores
  interruption, and release it from the test.
- **Do not race an interrupt against a release.** A virtual thread may see "the subtask ended" before
  it sees its own interrupt, so a test that interrupts a thread from outside and then releases work
  may never exercise the interrupted path, and still pass. When a test needs the interrupt to be seen,
  have the thread set its own interrupt status at the point under test (seen in RT-02d).
- **Assert spans with the real OpenTelemetry SDK:** an `SdkTracerProvider` with a
  `SimpleSpanProcessor` over an `InMemorySpanExporter`, injected with `Builder.tracer(...)`. No mocks.
- **Assert MDC through real log events, frozen on the logging thread.** Logback evaluates an event's
  MDC lazily, so use an appender that calls `event.prepareForDeferredProcessing()` before storing the
  event. Otherwise the assertion reads the MDC of whichever thread inspects the event, and can pass
  falsely (seen in RT-02b).
- **Guard tests are expected to pass before the change.** Tests that assert something is *not*
  propagated or does *not* leak hold before and after an implementation. Record them as guards, not as
  red evidence.
- **Repeat concurrency tests** as part of regression, 30–50 runs of the affected test classes, and
  retain the result as evidence.
- **Label honestly.** Tests added after the code they cover are retrospective characterization under
  plan §6.1 and must be recorded as such.
