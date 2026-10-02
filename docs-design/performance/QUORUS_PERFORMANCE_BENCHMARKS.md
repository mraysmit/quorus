<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Performance Benchmarks

**Version:** 2.2  
**Date:** 2026-09-28  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Status:** Specification. B-08 (four scenarios) and B-09 (commit latency) are implemented and have recorded baselines; the other benchmarks are not yet implemented. Results are
recorded in [QUORUS_PERFORMANCE_VALIDATION_RESULTS.md](QUORUS_PERFORMANCE_VALIDATION_RESULTS.md).  
**Delivery:** register item `ENG-15` (benchmark module). Supersedes version 1.0, *Vert.x 5.x Migration —
Performance Benchmarks* (December 2025), whose figures are kept as history in the results document.

---

## 1. Purpose and rules

This document defines what Quorus measures, on which code paths, with which workloads, and which gate
or decision each measurement serves. It is the specification for a Quorus benchmark module (`ENG-15`).

Every published figure follows the Architecture Specification §13 rule. A capacity or latency figure
is published only with:

- the workload and file-size distribution;
- the hardware, JVM, storage and network configuration;
- the protocol mix and concurrency;
- p50, p95 and p99 latency;
- the error and retry rates;
- the benchmark source in this repository and a reproducible invocation.

Three further rules follow from what went wrong with the earlier figures:

1. **A benchmark measures Quorus code.** Timing a framework call, a stub or an empty task is not a
   Quorus benchmark. The earlier headline figure (670,322 operations per second) timed
   `vertx.executeBlocking(() -> "result")`, which does no Quorus work.
2. **The source is in the repository before a figure is.** The earlier document cited benchmark code in
   `quorus-integration-examples/benchmarks/`, which never existed.
3. **A comparison states both sides' code.** A "before and after" figure names the commit of each side
   and runs both on the same machine in the same session.

Processes under test run from jars built on the host. Nothing is compiled inside a container.

## 2. The code being measured

The runtime changed after the earlier figures, so none of them describe the current code:

| Area | Current implementation | Since |
|---|---|---|
| Transfer engine | `SimpleTransferEngine`: each transfer runs on the calling thread under a concurrency limit, with retries; cancellation interrupts the transfer's thread | `RT-03c` |
| HTTP adapter | Apache HttpClient 5, blocking, streaming to and from files, with governed address pinning | `RT-03b` |
| Other adapters | FTP/FTPS, SFTP (JSch), SMB and NFS, blocking on the calling thread | `RT-03d` |
| Connection pool | Removed: it was never used by a transfer | `RT-03a` |
| Workflow engine | Blocking; groups in dependency order, up to `parallelism` at a time, each group's transfers in a `TaskScope`; timeout, cancel and group retries | `RT-04`, `ENG-12`, `ENG-13` |
| Agent | Virtual threads it owns; `java.net.http` controller client; a virtual thread per job; bounded shutdown | `RT-05` |
| Controller | Still Vert.x 5 (HTTP API, Raft, gRPC) until `RT-06` and `CE-07` to `CE-11` | — |

## 3. Benchmark catalogue

Levels: **micro** (one method, JMH), **component** (one module against real local servers),
**process** (real controller and agent processes).

| ID | Level | What it measures | Code under test | Workload | Metrics | Gate or decision it serves |
|---|---|---|---|---|---|---|
| **B-01** | micro | Engine overhead per transfer, and behaviour at the concurrency limit | `SimpleTransferEngine.transfer` over a local file-to-file transfer | 1 KB and 1 MB files; concurrency 1, limit, 2 × limit | transfers/s; p50, p95, p99 overhead | Baseline for `RT-03c`; no gate |
| **B-02** | component | HTTP adapter streaming throughput and memory | `HttpTransferProtocol` download and upload against a local JDK HTTPS server, governed and ungoverned | 1 MB, 100 MB, 1 GB; concurrency 1, 10, 50 | MB/s; p50, p95, p99 transfer time; peak heap and RSS | Architecture Specification §13 *Large HTTP transfer memory*: ten concurrent files larger than the agent heap with bounded memory |
| **B-03** | component | FTP/FTPS, SFTP, SMB and NFS throughput | Each adapter against its test container | 1 MB, 100 MB, 1 GB; concurrency 1 and 10 | MB/s; p50, p95, p99 transfer time; error rate | Baseline per protocol; no gate |
| **B-04** | component | Cost of governed authorization before a transfer | `AgentConnectionPolicyService` (policy, DNS, secret resolution) with a local Vault | 1,000 authorizations; 1 and 10 threads | p50, p95, p99 latency | Baseline; Phase 4 hardening |
| **B-05** | component | Workflow scheduling and control latency | `SimpleWorkflowEngine` against a local HTTP server | 10 groups × 5 transfers of 1 MB; `parallelism` 1, 2, 5; dependency chains | wall time; transfers/s; cancel and timeout latency | Baseline for `RT-04`; Phase 7 |
| **B-06** | component | Agent cancellation and shutdown latency | `QuorusAgent` shutdown with transfers blocked in socket reads | 1, 10, 50 stalled transfers | time from shutdown to last transfer ended | The `RT-05` claim that a blocked transfer stops at once |
| **B-07** | process | Agent footprint | Agent process | idle; 10 and 50 concurrent 100 MB transfers | platform and virtual thread counts; heap; RSS; CPU | Baseline; capacity planning |
| **B-08** | process | Controller HTTP API throughput and latency | Controller processes from the host-built jar: `POST /api/v1/transfers`, heartbeats, agent job polling, transfer reads and (later) status reports, over TLS 1.3 with required client certificates, authentication, authorization and audit | 10, 100, 500 concurrent clients; 60 s steady state | requests/s; p50, p95, p99; error rate | `RT-Q2`: `RT-06` must prove the JDK `HttpsServer` on throughput. Run on the Vert.x controller **before** `RT-06`, then after |
| **B-09** | component | Raft command commit latency and failover | Three engine nodes in one JVM over real gRPC with TLS 1.3 mutual authentication on loopback, durable raftlog WALs, the controller's state machine | 2,000 small commands per concurrency level (1, 10, 50); 100 induced leader failures | submit-to-commit p50, p95, p99; write-resume time | Architecture Specification §13 *Leader failover*; baseline for `CE-07` to `CE-11` (QRaft) |
| **B-10** | process | End-to-end transfer throughput and correctness | Controller cluster, N agents, file servers | 1,000 consecutive assigned transfers; mixed sizes and protocols | transfers/s; submit-to-terminal p50, p95, p99; terminal-state correctness; progress freshness | Architecture Specification §13 *End-to-end lifecycle* and *Progress freshness*; Phase 12 |
| **B-11** | process | Soak | As B-10 | 24 hours at 50% of the B-10 peak | throughput drift; heap, RSS and thread trends; error rate | Phase 12 soak report |

## 4. Method

- **Micro (B-01):** JMH, with forks, warm-up and measurement iterations stated in the result.
- **Component (B-02 to B-06):** the benchmark drives the Quorus API directly against real local servers
  (the JDK HTTP(S) server, Testcontainers for FTP, SFTP, SMB and Vault). It records latencies in an
  HDR histogram and samples heap, RSS and threads.
- **Process (B-07 to B-11):** a harness starts the controller and agent processes from their host-built
  jars (or the host-built images), generates load through the public API, and collects the same
  measurements from each process. Correctness is checked from controller state, not assumed.
- **Environment capture:** every run records the commit, JDK build and flags, OS, CPU, memory, storage,
  network and container runtime, as §13 requires.
- **Comparisons:** both sides run in the same session on the same machine, each at a named commit.
- **Recording:** a run's figures and environment go into the results document and the commit message
  that adds them (plan §6.1).

## 5. The benchmark module (`ENG-15`)

A Maven module, `quorus-benchmarks`, that is not part of the default build:

- built and run only with an explicit profile (for example `-Pbenchmarks`), so `mvn verify` stays a
  correctness build;
- JMH for the micro level; a small harness for the component and process levels;
- depends on the modules it measures, and on no test fixture that replaces the code under test;
- each benchmark ID above maps to one class and one documented invocation;
- output: a machine-readable result per run plus the environment record, which the results document
  summarises.

The controller baseline (B-08, and B-09 for QRaft) is needed **before** `RT-06` and `CE-07`, because
their acceptance compares against it. B-02, B-10 and B-11 serve the Phase 12 gates.

## 6. Status

| ID | Status |
|---|---|
| B-09 | Commit latency implemented (`RaftCommitBenchmark`); baseline recorded 2026-09-28. Leader failover not yet implemented |
| B-08 | Submit, heartbeat, poll and read implemented (`ControllerApiBenchmark`); baseline recorded 2026-09-28. Agent status reports not yet implemented: they need the assignment and attempt lifecycle first |
| B-01 to B-07, B-10, B-11 | Not implemented |

## 7. Revision history

| Version | Date | Change |
|---|---|---|
| 2.2 | 2026-09-28 | B-08 implemented for four scenarios (real controller processes under the production TLS and request-security configuration, `development` profile for ungoverned submissions) |
| 2.1 | 2026-09-28 | B-09 moved to the component level (engine nodes in one JVM over real gRPC and TLS, so the engine can be compared without HTTP and process start-up); its commit-latency part is implemented in `quorus-benchmarks` |
| 2.0 | 2026-09-28 | Rewritten as the benchmark specification for the code after `RT-03` to `RT-05`: the catalogue (B-01 to B-11), the method, the publication rules and the benchmark module. Renamed from `VERTX5_PERFORMANCE_BENCHMARKS.md`. The version 1.0 figures moved to the results document as history |
| 1.0 | 2025-12-17 | *Vert.x 5.x Migration — Performance Benchmarks*: before-and-after figures for the Vert.x migration |
