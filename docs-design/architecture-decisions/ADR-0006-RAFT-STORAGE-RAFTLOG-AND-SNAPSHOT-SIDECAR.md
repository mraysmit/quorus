<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# ADR-0006: Raft Storage on raftlog-core with a Quorus Snapshot Sidecar

**Version:** 1.0  
**Date:** 2026-11-03  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

**Status:** Accepted. It records the storage design in force since 2026-09-04 (commits `1439456e`, `606b37c0` and `921c741d`), written down under register item DR-D4 in the [Outstanding Work Register](../task/QUORUS_OUTSTANDING_WORK_REGISTER_2026-11-03.md). **Planned successor:** [ADR-0011](ADR-0011-CONSENSUS-VIA-QRAFT-GENERIC-ENGINE.md). After register item `CE-10`, QRaft owns the log, compaction and snapshot publication, and this ADR becomes history.

**Relates to:** [Architecture Specification §5.5](../../docs/QUORUS_ARCHITECTURE_SPECIFICATION.md#55-persistence-requirements), the normative persistence requirements; the [Raft Storage Reference](../reference/QUORUS_RAFT_STORAGE_REFERENCE.md), which describes the files, configuration, recovery order, operator rules and tests in detail. This ADR records why the design is what it is.

## Context

Raft is safe only if a node's term, its vote and its log entries are durable before it acts on them. A node that forgets a vote can vote twice in one term. A follower that acknowledges entries it then loses can let a leader commit an entry that a majority does not hold. A log that never shrinks needs a snapshot so that the prefix can be deleted. Once the prefix is gone, the snapshot is the only record of that state.

Until 2026-09-04 the controller had four storage backends, chosen by `quorus.raft.storage.type`: the `raftlog-core` library through `RaftLogStorageAdapter`, which production used; an in-repository file WAL, which was a stale fork of the library's `FileRaftStorage` and was used by every durable restart test; RocksDB through a JNI dependency; and a memory backend. Snapshots were held only in memory. So the tests proved a different WAL from the one production ran, configuration could select a non-durable backend, and the library at the time had no prefix truncation, so the on-disk log was never compacted.

`raftlog-core` is a separate project by the same author. Its 1.2.0 release, published to Maven Central from commit `1c5af80` (tag `v1.2.0`), added the prefix compaction Quorus needed.

## Decision

1. **`raftlog-core` is the only WAL and Raft metadata store.** Quorus uses `io.github.mraysmit:raftlog-core` 1.2.0 through `RaftLogStorageAdapter`. The library owns `meta.dat` (term and vote), `raft.log` (append and truncate records, CRC32C-framed) and `raft.lock`. Quorus has no WAL of its own, no RocksDB backend and no memory backend. `AppConfig.validate()` accepts only `raftlog`, and `RaftStorageFactory` refuses the removed names rather than falling back to another backend. A fix the log needs is made in `raftlog-core` and released, not patched into a fork here.

2. **Quorus owns the snapshot, in a sidecar beside the WAL.** The library has no snapshot concept and records no minimum append index. `FileSnapshotStore` writes `snapshot.dat` (magic `QSNP`, format version 1, last-included index and term, payload, CRC32C) by an atomic, forced replacement, and refuses coordinates older than the published snapshot. It also writes `snapshot.required`, a marker whose index only increases and which records the oldest index recovery must be able to reach from a snapshot.

3. **Compaction is ordered so that no step can lose state:**
   1. publish a snapshot that covers the boundary;
   2. publish the marker;
   3. have the library rewrite `raft.log` without the prefix;
   4. trim the in-memory log.

   Recovery fails if the marker requires a snapshot that is missing or older, rather than starting as an empty node. A failure part-way through a snapshot installation is completed at the next start.

4. **Quorus owns the append plan.** The library neither deduplicates nor checks for gaps on append or replay. The follower skips entries that already match, truncates from the first conflict, persists, and calls `sync()` before it replies or changes memory. Recovery places each record at its own index and fails on a gap. All log mutations, including snapshot capture and installation, run one at a time.

5. **Persist before acting.** A vote is granted, a higher term is adopted, and an `AppendEntries` request is acknowledged only after the change is durable. `sync()` is the durability barrier for appends and truncations. `quorus.raft.storage.fsync` must be `true` in production; snapshot files and compaction are forced even when it is `false`. Peer RPCs wait until the first recovery completes, so a vote is never judged against a term that has not been loaded yet.

6. **Only the leader takes snapshots.** A follower's log shrinks only when it installs the leader's snapshot. This keeps one snapshot schedule for the cluster and has no correctness cost; the cost is follower disk and replay time.

7. **One process per directory, one directory per node.** The library's lock refuses a second opener. The files in a directory depend on each other and are backed up, restored and preserved together.

## Alternatives considered

- **Keep the in-repository file WAL.** Rejected: it was a fork that had already drifted from the library, and it was the only WAL the restart tests exercised. Two formats would each need their own durability proof.
- **RocksDB.** Rejected: it brings a native JNI dependency and LSM background compaction to store an append-only log and two values, and its key/value model is not the shape of a Raft log.
- **A memory backend, selectable by configuration.** Rejected: configuration could then select non-durable storage in production. Tests that do not need durability use `RaftNodeMode.volatileMode()`, which has no storage at all, and storage tests use the real adapter on temporary directories.
- **Let the library own snapshots.** Not available in 1.2.0, and the snapshot payload is Quorus's state. Under ADR-0011 the engine owns snapshot publication and Quorus supplies only the content, which is where this belongs once the engine is generic.
- **Followers take their own snapshots.** Deferred: the leader's snapshot already bounds a follower's log when it lags. QRaft owns this after `CE-10`.

## Consequences

- There is no migration path from any other storage format, and none is supported. Rolling back to a build older than the sidecar (`921c741d`) does not understand `snapshot.required`.
- Operators follow the rules in Raft Storage Reference §11, above all: never delete `meta.dat`, `snapshot.required`, or the WAL or snapshot of a compacted node, and never copy one node's directory to another.
- Container recreation on named volumes is proven (`R1-1`). Power loss (`R1-3`) and the production file system (`R1-2`) are not, and they block the release claim.
- Library settings Quorus does not expose (free-space floor, maximum payload, write verification) keep their defaults. Lowering the maximum payload on an existing directory truncates the log at replay.
- QRaft uses `raftlog-core` 1.4.0. Whether 1.2.0 files and this sidecar can be read by QRaft as they are, or must be converted, is open (`CE-Q4`), and the cutover needs a tested migration and rollback path (`CE-09`).

## Revision history

| Version | Date | Changes |
|---|---|---|
| 1.0 | 2026-11-03 | Records the storage design delivered on 2026-09-04, under register item DR-D4 |
