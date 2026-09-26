<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# ADR-0012: Leave Vert.x for Java 27 Structured Concurrency

**Version:** 1.1  
**Date:** 2026-09-26  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

**Status:** Accepted by project authority on 2026-09-26, including decisions `RT-Q1` to `RT-Q4` below. Delivery is workstream `RT` in the [enterprise implementation plan](../task/QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md#20-platform-migration-workstreams).

## Context

Quorus is built on Java 25 and Vert.x 5. Vert.x types appear in 78 main-source files: 48 in `quorus-controller`, 12 in `quorus-core`, 10 in `quorus-agent`, 6 in `quorus-integration-examples` and 2 in `quorus-workflow`. They cover:
- the controller HTTP server and middleware;
- the agent's controller client;
- the HTTP transfer protocol's `WebClient`;
- `executeBlocking` for the FTP, SFTP and SMB adapters;
- timers for heartbeats and polling;
- future composition throughout;
- the project's asynchronous test standard (plan §6.1 and the Copilot instructions).

QRaft, which Quorus will consume for consensus ([ADR-0011](ADR-0011-CONSENSUS-VIA-QRAFT-GENERIC-ENGINE.md)), prohibits Vert.x and uses standard Java concurrency.

**Java 27 status, verified on 2026-09-26** with OpenJDK 27 GA (build 27+35) installed locally:

| Facility | Status in JDK 27 |
|---|---|
| Virtual threads (`Executors.newVirtualThreadPerTaskExecutor`) | Final. Compiles and runs without flags |
| `ScopedValue` | Final. Compiles and runs without flags |
| `StructuredTaskScope` | **Preview.** It fails to compile without `--enable-preview` and runs with it |

Class files compiled with preview features only run on the exact Java feature release they were compiled for. The API has also changed between previews; for example, it moved to `StructuredTaskScope.open()`. Java 27 is also a non-LTS release. The LTS releases either side are 25 and, on the usual cadence, 29.

## Decision

1. **Quorus leaves Vert.x completely.** The target has no `io.vertx` dependency in any module. Enforcement is a build check that fails on any `io.vertx` artifact once the workstream completes.

2. **The concurrency model is structured concurrency on virtual threads:**
   - blocking-style code runs on virtual threads;
   - concurrent subtasks run in structured scopes, with a defined owner, cancellation and deadline;
   - request and security context is carried in `ScopedValue` rather than thread-locals or Vert.x context;
   - blocking protocol I/O runs directly on virtual threads, with no `executeBlocking` and no event-loop discipline.

3. **The Java baseline moves to 27, then follows each six-monthly feature release (`RT-Q3`).** This covers the poms, `.java-version`, CI images, Docker build and runtime images, and documentation.

4. **Replacements follow QRaft's standard of JDK facilities first:**
   - **Agent-to-controller and HTTP transfers:** `java.net.http.HttpClient`, which supports mutual TLS through `SSLContext` and HTTP/2. HTTP downloads stream to a file with a body handler, which also removes the whole-body buffering in `ARCH-09`.
   - **Controller HTTP API:** the JDK `HttpsServer` (`RT-Q2`).
   - **Timers and periodic work:** loops in structured scopes on virtual threads, with explicit shutdown.
   - **gRPC:** stays on grpc-java, as in QRaft.

5. **Asynchronous test standard.** Tests exercise blocking APIs on virtual threads, with explicit preemptive timeouts and real HTTP, agent, protocol and cluster boundaries. Awaitility, sleeps used for synchronization, and non-deterministic polling remain prohibited. Plan §6.1's requirement for "Vert.x test facilities" applies only to code that is still on Vert.x, and is retired as each module leaves.

6. **The migration is incremental and module by module.** Each module leaves Vert.x under the §6.1 TDD protocol with its existing boundary tests kept green. No Vert.x compatibility wrapper is introduced. A module that still uses Vert.x may call a module that has left it only through JDK types. The only temporary bridge allowed is the single consensus adapter class described in ADR-0011.

## Decisions taken on 2026-09-26

- **`RT-Q1` — structured concurrency without preview in production. Decided: option (b).**
  Quorus code is written in structured form against a small Quorus-owned task-scope
  abstraction implemented on final APIs: virtual-thread executors, explicit cancellation,
  deadlines, and failure propagation from subtask to owner. Production artifacts never compile
  with `--enable-preview`. When `StructuredTaskScope` becomes final in a Java release that
  Quorus has adopted, only the abstraction's implementation changes to use it, with the
  abstraction's tests as the regression gate. The abstraction is a project-level concurrency
  API, not a Vert.x compatibility layer, and it exposes JDK types only. Rejected: (a) preview in
  production, and (c) preview confined to non-production modules.

- **`RT-Q2` — controller HTTP server. Decided: the JDK `HttpsServer`**
  (`com.sun.net.httpserver`), matching QRaft. `RT-06` must show, through real HTTP boundary
  tests, that it meets:
  - TLS 1.3 with required client certificates;
  - the authentication, authorization and audit middleware chain;
  - request-size limits;
  - server-sent event streaming with backpressure for Phase 3's resumable stream;
  - the throughput needed for the controller workload.

  If it demonstrably fails one of these, reopen this decision with the evidence; do not
  substitute a server silently.

- **`RT-Q3` — Java support policy. Decided: follow each six-monthly Java feature release.**
  - Quorus moves to Java 27 now and adopts every later GA feature release (28, 29, …) within
    its update window, because a non-LTS release stops receiving updates once the next one
    ships.
  - Each move is a planned upgrade: toolchain, CI and container images; a full reactor with
    coverage gates; the Docker and slow lanes; and the Raft durability and restart lanes on
    the new runtime.
  - Because `RT-Q1` avoids preview features, an upgrade never requires source changes to
    preview APIs.
  - When `StructuredTaskScope` is final in an adopted release, switch the task-scope
    implementation in that upgrade.
  - QRaft's minimum Java version must not exceed the version Quorus runs; QRaft currently
    requires Java 25 or later.

- **`RT-Q4` — Java 27 container images. Decided: Amazon Corretto 27.** Eclipse Temurin had
  published no Java 27 images, and the official `openjdk` image offers only non-production
  `27-rc` tags (both checked 2026-09-26). Corretto 27 images exist for amd64 and arm64 on
  Amazon Linux 2023 and Alpine. No official `maven` image carries Java 27, so the builder stage
  adds a pinned, checksum-verified Maven. Image tags pin an exact Corretto release (for example
  `27.0.0-…`), and `RT-09` moves them with each Java release. The runtime variant (Alpine JDK,
  Amazon Linux 2023 headless, or a jlink-built runtime) is confirmed before `RT-01b` starts.

## Alternatives considered

- **Stay on Vert.x.** Rejected by project direction. It also keeps Quorus inconsistent with QRaft and would need a permanent bridge at the consensus boundary.
- **Virtual threads without structured concurrency.** Partly adopted through `RT-Q1` option (b). Unstructured virtual threads alone would lose ownership, cancellation and deadline propagation.

## Consequences

- This is a whole-codebase migration. Plan workstream `RT` sequences it by module (core, workflow and examples, agent, controller) and places the controller step before the bulk of Phase 6 REST work, so that new endpoints are not written twice.
- Every existing Vert.x-based test must be rewritten for the modules that change. The rewritten tests are regression coverage, not TDD evidence, unless they express new behaviour.
- The Copilot instructions, plan §6.1 and the testing documents describe Vert.x as the standard. They must be updated as each module moves, and they already note this ADR as the direction of travel.
- Observability loses Vert.x's built-in tracing integration. OpenTelemetry instrumentation for the new HTTP server and client is part of `RT-07`.
