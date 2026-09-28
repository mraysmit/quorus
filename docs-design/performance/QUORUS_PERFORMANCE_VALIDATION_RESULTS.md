<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Performance Validation Results

**Version:** 2.2  
**Date:** 2026-09-28  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Status:** Results log. Baselines are recorded for the current code: B-09 (commit latency) and B-08 (controller API). The benchmarks are defined in
[QUORUS_PERFORMANCE_BENCHMARKS.md](QUORUS_PERFORMANCE_BENCHMARKS.md) and delivered by register item
`ENG-15`. Appendices A and B keep the earlier Vert.x migration figures as history; they do not describe
the current code and must not be quoted as Quorus performance.

---

## 1. How a result is recorded

One row per run, added in the same commit as the benchmark source or configuration it used. The row
carries everything the Architecture Specification §13 publication rule requires; a figure without all
of it is not recorded.

| Field | Content |
|---|---|
| Benchmark | ID from the benchmark specification (B-01 to B-11) |
| Commit | The commit measured (and, for a comparison, the other side's commit) |
| Invocation | The exact command that reproduces the run |
| Environment | CPU, memory, storage, network, OS, JDK build and flags, container runtime |
| Workload | File sizes and distribution, protocol mix, concurrency, duration |
| Results | Throughput; p50, p95 and p99 latency; error and retry rates; memory and threads where measured |
| Gate | The gate or decision the result serves, and whether it passes |

## 2. Results

| Benchmark | Commit | Date | Result | Gate outcome |
|---|---|---|---|---|
| B-09 (commit latency) | `8946faa` plus the uncommitted `quorus-benchmarks` module; engine code unchanged | 2026-09-28 | About 270 commits/s at every concurrency; p50 3.7 ms at 1 client (§2.1) | No gate; baseline for `CE-07` |
| B-08 (controller API) | `23bcb6f` plus the uncommitted B-08 harness; controller code unchanged | 2026-09-28 | About 100 requests/s for reads and 70 for writes at every concurrency, no errors (§2.2) | No gate; baseline for `RT-06` (`RT-Q2`). Found `ENG-16` |

### 2.1 B-09 baseline: in-repository Vert.x engine, 2026-09-28

| Field | Value |
|---|---|
| Invocation | `mvn -B -Pbenchmarks -pl quorus-benchmarks exec:java -Dexec.args="B-09 --storage local-disk-of-development-workstation --network loopback"` (after `mvn -B -Pbenchmarks -pl quorus-benchmarks -am install -DskipTests`) |
| Environment | Intel Core Ultra 9 185H (22 logical processors), 95 GB RAM, Windows 11 (10.0, amd64); OpenJDK 27+35, default heap limit (24 GB), run inside Maven `exec:java` |
| Storage and network | Each node's WAL in the system temporary directory on a Samsung SSD 990 PRO (NVMe); fsync on. Loopback; gRPC with TLS 1.3 mutual authentication |
| Workload | Three engine nodes in one JVM with the controller's state machine and the packaged Raft settings (election timeout 5,000 ms, heartbeat 1,000 ms). 128-byte `SystemMetadataCommand.Set` commands; 500 warm-up commands; 2,000 commands per concurrency level, closed loop |
| Errors and retries | 0 errors at every level; the harness does not retry |

| Concurrent clients | Commits/s | p50 | p95 | p99 | Max |
|---|---|---|---|---|---|
| 1 | 252 | 3.7 ms | 4.9 ms | 5.5 ms | 18.3 ms |
| 10 | 269 | 37.7 ms | 38.7 ms | 42.0 ms | 44.1 ms |
| 50 | 270 | 186.8 ms | 191.7 ms | 193.2 ms | 196.1 ms |

**Reading.** Throughput does not rise with concurrency and latency grows in proportion to the queue:
the engine replicates and syncs one command at a time, with no batching of concurrent commands. This is
the engine's own ceiling on this machine, before HTTP. The commit is recorded with an uncommitted working
tree (the benchmark module itself), so the measurement should be repeated from a clean commit before it is
used in a published comparison.

### 2.2 B-08 baseline: Vert.x controller, 2026-09-28

| Field | Value |
|---|---|
| Invocation | `mvn -B -Pbenchmarks -pl quorus-benchmarks exec:java -Dexec.args="B-08 --storage local-disk-of-development-workstation --network loopback"` |
| Environment | As §2.1 (Intel Core Ultra 9 185H, 22 logical processors, 95 GB RAM, Windows 11, OpenJDK 27+35). The client runs in the Maven JVM; each controller is a separate JVM with `-Xmx1g` from the host-built shaded jar |
| Storage and network | Each controller's raftlog WAL and audit logs in the system temporary directory on a Samsung SSD 990 PRO (NVMe); fsync on. Loopback |
| Cluster and security | Three controller processes. HTTP and Raft over TLS 1.3 with required client certificates; request authentication, authorization and hash-chained audit on. `development` security profile, so transfers are submitted without a governed service connection (the measured path is the HTTP stack and the Raft write). The client is a trusted gateway asserting an operator or agent identity |
| Workload | Closed loop against the leader, each scenario at 10, 100 and 500 clients for 30 s after a 5 s warm-up. `submit`: `POST /api/v1/transfers`; `heartbeat`: `POST /api/v1/agents/heartbeat` from 500 registered agents; `poll`: `GET /api/v1/agents/{id}/jobs`; `read`: `GET /api/v1/transfers/{id}` over 100 jobs |
| Errors and retries | 0 errors in every scenario; the harness does not retry |

| Scenario | Clients | Requests/s | p50 | p95 | p99 | Max |
|---|---|---|---|---|---|---|
| submit | 10 | 70 | 142 ms | 161 ms | 179 ms | 225 ms |
| submit | 100 | 67 | 1.42 s | 1.60 s | 2.00 s | 2.29 s |
| submit | 500 | 66 | 6.65 s | 9.13 s | 10.25 s | 10.50 s |
| heartbeat | 10 | 76 | 130 ms | 146 ms | 154 ms | 209 ms |
| heartbeat | 100 | 79 | 1.21 s | 1.49 s | 1.64 s | 1.92 s |
| heartbeat | 500 | 72 | 6.40 s | 7.74 s | 8.86 s | 9.21 s |
| poll | 10 | 107 | 92 ms | 126 ms | 143 ms | 167 ms |
| poll | 100 | 103 | 944 ms | 1.06 s | 1.91 s | 2.01 s |
| poll | 500 | 99 | 5.12 s | 5.77 s | 5.83 s | 5.85 s |
| read | 10 | 101 | 96 ms | 141 ms | 159 ms | 200 ms |
| read | 100 | 106 | 920 ms | 1.29 s | 1.90 s | 2.00 s |
| read | 500 | 100 | 5.03 s | 5.30 s | 5.35 s | 5.39 s |

**Reading.** Reads that never touch Raft are capped at about 100 requests per second, and latency grows in
proportion to the queue, so the ceiling is a serial step in the HTTP path, not consensus (B-09 shows the
engine alone commits about 270 commands per second). The step is the security audit: every request
records at least two audit events (authentication and authorization); each event is written to two
hash-chained logs (retained evidence and operational), and `HashChainedAuditLog.append` is `synchronized`
and calls `FileChannel.force(true)` per write. The handlers call it on the Vert.x event loop, so each
request performs at least four disk syncs on the thread that serves every request. Writes are slower
again because the Raft commit follows. Recorded as `ENG-16`. This baseline measures the controller as it
is; the comparison after `RT-06` has to state whether `ENG-16` was fixed in between.

## 3. Revision history

| Version | Date | Change |
|---|---|---|
| 2.2 | 2026-09-28 | B-08 baseline on the Vert.x controller (§2.2); the audit write path found to cap the API (`ENG-16`) |
| 2.1 | 2026-09-28 | First baseline: B-09 commit latency on the in-repository Vert.x engine (§2.1) |
| 2.0 | 2026-09-28 | Rewritten as the results log for the benchmark specification. The January 2026 and December 2025 Vert.x migration figures moved to Appendices A and B as history, with the reasons they do not describe the current code |
| 1.0 | 2026-01-05 | *Vert.x 5.x Migration — Performance Validation Results* |

---

## Appendix A. Vert.x migration validation, 2026-01-05 (historical)

> **Not a Quorus performance result.** The figures below came from `VertxPerformanceBenchmark`, which
> the documentation review of 2026-09-24 found timed `vertx.executeBlocking(() -> "result")`, a call
> that does no Quorus work. The class was deleted in `RT-03f` (2026-09-27). The Vert.x runtime it
> exercised is no longer used by any module except the controller. The document's claims of a
> "Phase 4 PostgreSQL" connection-pool migration and of `quorus-api` tests also do not describe this
> repository. Kept unchanged below for the record.

### Recorded figures

| Test | Recorded result |
|---|---|
| Thread count, 100 concurrent operations | 12 initial, 33 peak threads; 109 ms |
| Throughput, 10,000 operations after warm-up | 670,322 operations/s; 14 ms in total |
| Latency, 1,000 operations | p50 28 µs, p95 80 µs, p99 218 µs, max 1,432 µs |
| Memory, 1,000 concurrent futures | 7 MB before and after |
| Shutdown with 50 two-second operations | 21 ms |

The original document compared these with targets (for example "exceeds 1,000 ops/sec by 670x") and
with estimated "traditional" figures that were never measured.

## Appendix B. Vert.x migration benchmarks, 2025-12-17 (historical)

> **Not a Quorus performance result.** The document said its benchmark code was in
> `quorus-integration-examples/benchmarks/`; that directory has never existed in this repository, so
> no figure below can be reproduced. The connection pool it measured was removed in `RT-03a`, the
> Vert.x WebClient HTTP adapter was replaced in `RT-03b`, and the reactive engine and workflow engine
> were replaced in `RT-03c` and `RT-04`. The documentation review of 2026-09-24 also found that its
> "before (blocking)" and "after (reactive)" connection-pool figures were the default-versus-production
> pool preset comparison from `CONNECTION_POOL_BENCHMARK_RESULTS.md`, relabelled. Recorded environment:
> Windows 11, Java 24, Intel i7 with 24 cores and 32 GB. Kept below for the record.

### Recorded figures

| Area | Before | After |
|---|---|---|
| HTTP connection pool, 100 concurrent requests for 60 s | 642 req/s; p95 280 ms; p99 350 ms | 3,136 req/s; p95 58 ms; p99 85 ms |
| Threads | 7 pools, about 50 to 70 threads | 2 pools, about 25 to 40 threads |
| Workflow, 10 groups × 5 transfers of 1 MB | 25.3 s | 5.8 s |
| HTTP transfers, 20 × 10 MB | 45 MB/s; 20 threads | 180 MB/s; 1 event-loop thread |
| REST API, `POST /api/v1/transfers`, 100 concurrent for 60 s | 1,250 req/s; p95 150 ms; p99 220 ms | 4,800 req/s; p95 42 ms; p99 68 ms |
| Steady-state heap | 245 MB | 160 MB |
| Startup to first request | 3.8 s | 2.4 s |

Two of these areas remain worth measuring on the current code: the workflow run (benchmark B-05) and the
REST API (B-08, which is needed as the baseline for `RT-06`). The earlier figures are not a baseline for
either, because their source does not exist.
