<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Quorus and QRaft: Integration Assessment

**Version:** 1.0  
**Date:** 2026-09-28  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Status:** Assessment, read-only on the QRaft side. It records where both engines stand and what workstream
`CE` has to do. Decisions are taken in [ADR-0011](../architecture-decisions/ADR-0011-CONSENSUS-VIA-QRAFT-GENERIC-ENGINE.md)
and tracked as items `CE-01` to `CE-11` and decisions `CE-Q1` to `CE-Q5` in the
[register](../task/QUORUS_OUTSTANDING_WORK_REGISTER.md).  
**Observed:** QRaft `main` at `1bab473` (with uncommitted test changes; the owner is still changing it) and
Quorus after `RT-05`.

---

## 1. Summary

- QRaft's public engine module still holds only contracts. The working engine lives inside QRaft's own
  server module and is typed to QRaft's own commands.
- QRaft's `RaftNode` is an evolved sibling of Quorus's: the same builder, state-machine interface name and
  storage split, plus check-quorum, fencing on apply failure, a transition sequencer, an explicit
  "outcome unknown" error and snapshot publication outcomes. Integration is therefore mainly extraction and
  generalisation of existing QRaft code, not new engine work.
- QRaft's Raft transport is plaintext. Quorus already has TLS 1.3 mutual authentication, revocation on
  established connections and rotation for Raft; that capability has to move into QRaft in generic form.
- Neither engine has linearizable reads, dynamic membership, leader forwarding or a published artifact.

## 2. QRaft as observed

| Area | State |
|---|---|
| Build | Java 27 (`maven.compiler.release` 27, enforcer `[27,)`); `dev.mars:qraft:1.0-SNAPSHOT`; raftlog-core **1.4.0**; grpc-java 1.68.1, protobuf 3.25.5; OpenTelemetry 1.59.0. No Vert.x, no preview features |
| Modules | `qraft-raft-engine` (contracts), `qraft-distributed-state` (key/value command and catalog), `qraft-core`, `qraft-agent`, `qraft-tenant` (unused), `qraft-controller` (Raft, gRPC, HTTP, telemetry; shaded), `qraft-runtime` (shaded composition root) |
| Engine contracts (`dev.mars.qraft.raft.api`) | `ReplicatedCommand extends Serializable` (marker); `CommandCodec<C>` (`byte[] serialize(C)`, `C deserialize(byte[])`); `ReplicatedStateMachine<C, R>` (`apply`, `takeSnapshot`, `restoreSnapshot`, `getLastAppliedIndex`, `setLastAppliedIndex`, `reset`); `SnapshotStore` (`CompletableFuture`-based, `SnapshotData(data, lastIncludedIndex, lastIncludedTerm, formatVersion)`, publication outcome on failure) |
| Engine implementation | `qraft-controller/.../raft/RaftNode.java` (3,183 lines). Builder with runtime, node ID, cluster nodes, transport, state machine (`RaftLogApplicator`), codec (`CommandCodec<RaftCommand>`), mode, election timeout 5,000 ms, heartbeat 1,000 ms, snapshot settings, log hard limit. `Future<RaftCommandResult<?>> submitCommand(RaftCommand)` is leader-only. Status, leadership and state-change observation. `Future` is QRaft's own wrapper over `CompletableFuture` |
| Concurrency | One platform state-loop thread (`qraft-state-loop`) for all Raft transitions, through `RaftTransitionSequencer`, plus virtual-thread workers |
| Errors | `CommandOutcomeUnknownException` when commitment is ambiguous; the node fences itself if `apply` throws. Queue-full, draining and fenced errors are not public |
| Transport | gRPC `RaftService { RequestVote; AppendEntries; InstallSnapshot }` (chunked snapshots). `RaftTransport` is typed to generated protobuf classes. Channels use `usePlaintext()`; the server uses `ServerBuilder.forPort`. No TLS, no peer authorizer |
| Storage | raftlog `FileRaftStorage` for the WAL (with raftlog's `AppendPlan` for follower conflicts). `RaftNodeMode` is `Volatile` or `Durable(storage, snapshotStore)`. `FileSnapshotStore` writes `snapshot.dat` through `.tmp` with magic `QRSN`, a header and a CRC32C trailer; an orphan `.tmp` fences startup |
| Observability | Direct `GlobalOpenTelemetry` meters and a singleton `RaftMetrics`; no interface for an embedder |
| Membership, reads | Static membership from configuration. Reads return the answering node's applied state; leader forwarding is out of QRaft's scope by its own decision (a leader hint is returned) |
| Tests | About 130 test files: `RaftNode` model and sequencing tests, real storage and snapshot recovery, check-quorum, apply failure, Docker partition and durable-restart tests. An in-memory transport and cluster harness is published in the controller test jar |
| Publication | None: no `distributionManagement` or Maven Central configuration |
| Quorus in QRaft | No integration plan. One line in `docs/PROJECT_STANDARDS.md` refers to components inherited from Quorus. QRaft's product direction is a Consul-like platform |

## 3. Quorus as observed

| Area | State |
|---|---|
| Engine | `quorus-controller/.../raft/`: `RaftNode` (2,111 lines, Vert.x `Context`, timers and `Future`s), `GrpcRaftTransport` and `GrpcRaftServer` (Netty), `RaftTransport`, `RaftMessage`, `RaftNodeMode`, `LogEntry`, storage (`RaftStorage`, `RaftLogStorageAdapter` over raftlog `FileRaftStorage`, `FileSnapshotStore`, `RaftStorageFactory`) |
| raftlog | `io.github.mraysmit:raftlog-core` **1.2.0**, used directly |
| State machine | `QuorusStateStore implements RaftLogApplicator` (1,442 lines). Sealed `RaftCommand` (transfer job, agent, system metadata, job assignment, job queue, route, transfer attempt); sealed `CommandResult` (`Success`, `NotFound`, `NoOp`, `CasMismatch`, `Rejected(code, message)`). Snapshot is Jackson JSON `QuorusSnapshot`, schema versions 0 to 3 |
| Command encoding | `ProtobufCommandCodec` over `commands.proto` (`RaftCommandMessage` with a `oneof` per command family and a `schema_version`) |
| Callers | 30 `raftNode.submitCommand(...)` call sites in HTTP handlers and `JobAssignmentService`, all consuming a Vert.x `Future<CommandResult<?>>`; leadership and state queries in the cluster, health, readiness, info and status handlers |
| Leadership | `LeaderGuardHandler` rejects mutating `/api/**` requests on a non-leader with `NOT_LEADER` (Q-5001) or `NO_LEADER` (Q-5002), 503. No forwarding. Losing leadership after the guard is not mapped to `NOT_LEADER` (unverified) |
| Reads | Handlers read the local `QuorusStateStore`; a follower can serve stale data |
| Raft security | TLS 1.3, required client certificates, `RaftPeerAuthorizationInterceptor` rejecting revoked or unverified peers on every call; `RaftMtlsBoundaryIntegrationTest` covers trust, rejection, revocation on an open channel and rotation overlap. Certificates are not bound to node IDs (`SEC-04`) |
| Storage on disk | `quorus.raft.storage.path` holds raftlog's files and `snapshot.dat` (magic `QSNP`, version 1, CRC32C, a legacy header-less reader) with a `snapshot.required` marker |
| Configuration | `quorus.node.id`, `quorus.cluster.nodes` (`id=host:port,...`), `quorus.raft.*` timeouts, snapshot and log limits, gRPC pool sizes |
| Tests | About 20 engine test classes (default, `slow` and `docker` lanes) and about 20 more that use the node or state store through HTTP |

## 4. Work per item

| Item | Side | From | To |
|---|---|---|---|
| `CE-01` | QRaft | Engine in `qraft-controller`, typed to QRaft commands | `RaftNode`, transport, storage and snapshot store in the engine module, generic in command and result |
| `CE-02` | QRaft | Private `Future`; `Serializable` marker; private error types | JDK-typed API (see `CE-Q1`); typed errors: not leader (with hint), outcome unknown, timeout, shutting down, storage failure; the result type is the embedder's (Quorus needs `Rejected(code, message)`) |
| `CE-03` | QRaft | Plaintext | TLS 1.3 mutual authentication and a peer authorizer given the verified chain and the claimed node ID, consulted on connect and on every call; fail closed unless an explicit, warned development mode is chosen. Quorus's interceptor is the reference |
| `CE-04` | QRaft | Global OpenTelemetry calls, singleton metrics | A metrics and listener interface with no application names |
| `CE-05` | QRaft | No Vert.x already | Build checks: no `dev.mars:quorus*` or `io.vertx` in the engine's dependency tree; QRaft's own state machines use only the public API |
| `CE-06` | QRaft | Unpublished | Versioned artifacts that Quorus can resolve |
| `CE-07` | Quorus | 30 call sites and leader queries on `RaftNode` | An adapter: `QuorusStateStore` as `ReplicatedStateMachine`, `ProtobufCommandCodec` as `CommandCodec`, engine errors mapped to `NOT_LEADER`, `NO_LEADER` and the conflict mapping; at most one temporary class bridging to Vert.x until `RT-06` |
| `CE-08` | Quorus | `RaftTlsConfig`, `CertificateTrustState` | Trust material and authorizer supplied to QRaft; certificates bound to node IDs, which closes `SEC-04`; the Phase 1 Raft trust tests pass through QRaft |
| `CE-09` | Quorus | raftlog 1.2.0 files; `QSNP` snapshots | Proof of compatibility or a migration to raftlog 1.4.0 and QRaft's `QRSN` snapshots, with rollback; no mixed-engine cluster. The JSON state payload stays Quorus's |
| `CE-10` | Quorus | 12 engine classes, `raft.proto`, direct raftlog dependency | Removed; engine tests moved to the QRaft boundary; `commands.proto` stays |
| `CE-11` | Quorus | R1 durability lanes on the in-repository engine | The same lanes on the QRaft build; R1-2 and R1-3 run against it |

## 5. Open questions

Recorded as decisions `CE-Q1` to `CE-Q5` in the register's decision log.

1. **API style (`CE-Q1`).** `CompletableFuture`, or blocking calls with a timeout for virtual threads? The
   Quorus conventions favour blocking calls; the controller is still on Vert.x until `RT-06`, so either works
   through the one permitted bridge class.
2. **Reads (`CE-Q2`).** Does the first engine version offer a linearizable read (ReadIndex or leader lease),
   or does Quorus keep leader-only writes and accept stale follower reads, for example for agent polling after
   a failover?
3. **Membership (`CE-Q3`).** Static in both engines. Quorus's `ARCH-10` needs dynamic membership eventually.
   Out of scope for the first QRaft version?
4. **raftlog 1.2.0 to 1.4.0 on disk (`CE-Q4`).** Are 1.2.0 files readable by 1.4.0? The answer decides whether
   `CE-09` is a proof or a converter.
5. **Engine packaging (`CE-Q5`).** Is the engine a standalone, unshaded `qraft-raft-engine` artifact, and are
   QRaft's own key/value and catalog state machines moved onto the same public API first, as ADR-0011 requires
   for the genericity proof?

## 6. Quorus preparation that does not wait for QRaft

- **Baselines (`ENG-15`).** Measure Raft commit latency (B-09) and the controller API (B-08) on the current
  engine and HTTP server, because `CE-07` and `RT-06` are judged against them.
- **A narrow engine boundary.** Route the 30 `submitCommand` call sites and the leadership queries through one
  Quorus-owned interface with JDK types, with characterisation tests. `CE-07` then replaces one implementation
  instead of touching every handler, and the controller's HTTP migration (`RT-06`) no longer depends on the
  engine's Vert.x types.
