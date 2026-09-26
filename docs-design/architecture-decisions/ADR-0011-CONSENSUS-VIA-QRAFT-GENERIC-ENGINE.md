<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# ADR-0011: Consensus Through the Generic QRaft Engine

**Version:** 1.0  
**Date:** 2026-09-26  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

**Status:** Accepted as direction by project authority on 2026-09-26. The interface details below are proposed and are settled by workstream `CE` in the [enterprise implementation plan](../task/QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md#20-platform-migration-workstreams).

**Supersedes:** the in-repository Raft engine (`RaftNode`, gRPC Raft transport, `RaftLogStorageAdapter`, and the snapshot sidecar) as the long-term consensus implementation. The planned ADR-0006 (raftlog-core WAL and snapshot sidecar) is narrowed to recording the current state until this ADR is implemented.

## Context

Quorus implements its own Raft engine in `quorus-controller` on Vert.x 5 and depends on `io.github.mraysmit:raftlog-core` 1.2.0 directly for its write-ahead log.

QRaft (the sibling `qraft` project, groupId `dev.mars`) began on 2026-03-15 as a copy of Quorus. It has since become a generic, Vert.x-free Raft platform on Java 25 and uses raftlog-core 1.4.0. Its design assigns reusable consensus contracts and primitives to `qraft-raft-engine`, which "must not depend on service discovery, tenancy, HTTP, or a runtime mode". Its own service catalog and key/value state are built on that engine.

As observed on 2026-09-26:

| Area | QRaft state |
|---|---|
| `qraft-raft-engine` | Four interfaces only: `ReplicatedCommand` (extends `Serializable`), `CommandCodec<C>`, `ReplicatedStateMachine<C, R>` and `SnapshotStore` (`CompletableFuture`-based) |
| Raft implementation | `RaftNode` (3,033 lines), gRPC transport, storage factory and `FileSnapshotStore` live in the application module `qraft-controller`, alongside HTTP, catalog and runtime code, and a private `Future`/`Promise`/`WorkerExecutor` shim |
| Transport security | Raft gRPC is plaintext (`usePlaintext()`); there is no TLS and no peer authorization |
| Distribution | Not published. The local Maven copies of most modules date from 2026-03 |
| Quorus coupling | None. Neither project references the other |

The controller calls a narrow part of Quorus's own engine: `submitCommand` (30 call sites) and a few leadership and state queries (`isLeader`, `getLeaderId`, `getState`, `getCurrentTerm`, `getNodeId`, `getCommitIndex`, `isRunning`), plus lifecycle and the inbound RPC handlers.

## Decision

1. **Quorus consumes consensus only through QRaft's public engine API.** After migration, Quorus has no Raft node, Raft transport or WAL adapter of its own, and no direct `raftlog-core` dependency. raftlog remains the only WAL, reached through QRaft.

2. **The engine interface is 100% generic.** Nothing in QRaft's API, implementation, configuration, error model, metrics or tests may name or model a Quorus concept: transfers, jobs, assignments, attempts, agents, routes, workflows, tenants, service connections, Quorus roles, scopes or HTTP resources. Specifically:
   - Commands and state-machine results cross the boundary as opaque, application-encoded values through `CommandCodec`. The engine never inspects payloads. `ReplicatedCommand` should not require `java.io.Serializable`, because Quorus encodes commands with versioned protobuf.
   - The application supplies its state machine (`ReplicatedStateMachine`), codec, snapshot content and snapshot schema version. The engine owns ordering, commitment, log compaction, snapshot publication and recovery.
   - Identity is generic: a node has an opaque node ID and an address. Static membership is supplied as configuration.
   - Security is supplied through generic service-provider interfaces: a TLS key and trust material provider (a JDK `SSLContext` or equivalent), and a peer authorizer that receives the peer's verified certificate chain and the node ID it claims, and returns allow or deny. It is consulted on connection and on every RPC, so revocation takes effect on established connections. The engine enforces TLS 1.3 mutual authentication and fails closed when material is missing, unless an explicit, warned development mode is selected.
   - Observability is supplied through a generic listener or metrics interface that carries no application names.
   - Errors are generic and typed: not-leader (with an optional leader hint), outcome unknown, timeout, shutting down, and storage failure.
   - The public API uses JDK types only. It exposes no Vert.x types, no QRaft-private future shim, and no gRPC or protobuf types.

3. **Genericity is enforced, not asserted.** QRaft's own catalog and key/value state machines must use the engine only through the same public API that Quorus uses. The engine artifacts' dependency tree must contain no `dev.mars:quorus*` artifact and no `io.vertx` artifact. Both checks run in QRaft's build.

4. **Quorus-specific behaviour stays in Quorus:**
   - `QuorusStateStore` becomes a `ReplicatedStateMachine`;
   - the existing versioned protobuf codec becomes a `CommandCodec`;
   - `CertificateTrustState`, Phase 1 trust configuration and revocation supply the TLS and peer-authorizer implementations;
   - leader guarding, HTTP translation of engine errors, and audit remain Quorus concerns.

5. **Security does not regress.** Migration cannot complete until every Phase 1 Raft trust test passes through the QRaft engine: mutual TLS, unknown-certificate rejection, revocation on established connections, and rotation overlap. The peer authorizer binds the certificate identity to the configured node ID, which also closes register item `SEC-04`.

6. **No mixed-engine cluster.** Moving from the in-repository engine to QRaft is a coordinated cutover with a tested migration and rollback path for existing Raft state (workstream item `CE-09`), in the same spirit as the schema-3 upgrade.

## Alternatives considered

- **Keep Quorus's own engine.** Rejected. Two diverging copies of the same Raft implementation double the effort on correctness, durability and security, and QRaft already carries fixes and verification that Quorus lacks.
- **Depend on `qraft-controller` directly.** Rejected. It is an application module containing service-discovery, HTTP and runtime code; depending on it would couple Quorus to QRaft's product.
- **Copy QRaft's engine into Quorus.** Rejected, because it recreates the fork.

## Consequences

- QRaft must first extract a reusable engine, add transport security and publish versioned artifacts (workstream items `CE-01` to `CE-06`). These are QRaft deliverables, and Quorus's migration depends on them.
- raftlog moves from 1.2.0 to QRaft's version (1.4.0 at this date). On-disk compatibility between the two raftlog versions, and between Quorus's snapshot sidecar and QRaft's `SnapshotStore` format, has not been established. It must be proven or migrated before cutover.
- The R1 durability evidence was gathered against Quorus's own engine. R1-2 (production filesystem) and R1-3 (power loss) should be run once, against the QRaft-based build. R1-1 must be repeated after the cutover.
- The controller's Raft integration becomes a thin adapter. Until the Vert.x exit ([ADR-0012](ADR-0012-JAVA-RUNTIME-AND-STRUCTURED-CONCURRENCY.md)) reaches the controller, one Quorus-side class may convert JDK futures to Vert.x futures. That class is removed when the controller leaves Vert.x, and no such bridge may exist in QRaft.
