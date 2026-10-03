<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Raft Storage Reference

**Version:** 1.0  
**Date:** 2026-10-03  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Status:** Current for the in-repository Raft engine on `raftlog-core` 1.2.0. Under [ADR-0011](../architecture-decisions/ADR-0011-CONSENSUS-VIA-QRAFT-GENERIC-ENGINE.md) this engine and its direct `raftlog-core` use are replaced by the QRaft engine (register item `CE-10`); this reference then becomes history.

## Contents

1. [Scope](#1-scope)
2. [Dependency and Provenance](#2-dependency-and-provenance)
3. [Layering](#3-layering)
4. [Storage Method Contract](#4-storage-method-contract)
5. [RaftLog Integration Contract](#5-raftlog-integration-contract)
6. [On-Disk Layout](#6-on-disk-layout)
7. [Configuration](#7-configuration)
8. [Startup and Recovery Order](#8-startup-and-recovery-order)
9. [Snapshots, Compaction and InstallSnapshot](#9-snapshots-compaction-and-installsnapshot)
10. [Platform Durability](#10-platform-durability)
11. [Operator Rules](#11-operator-rules)
12. [Test Map](#12-test-map)
13. [Not Proven and Known Gaps](#13-not-proven-and-known-gaps)
14. [After QRaft](#14-after-qraft)

---

## 1. Scope

This reference describes how the Quorus controller stores Raft state today: the external write-ahead log (WAL), the Quorus snapshot sidecar, their configuration, recovery and the rules for operating a data directory. Every statement was checked against the source tree on 2026-10-03. Paths are relative to the repository root unless stated. Statements about `raftlog-core` internals were checked against `raftlog-core-1.2.0-sources.jar`, the sources artifact published with the binary.

The normative persistence requirements are in [Architecture Specification §5.5](../../docs/QUORUS_ARCHITECTURE_SPECIFICATION.md#55-persistence-requirements). Where this reference and the specification disagree, the specification wins and this reference is wrong.

The earlier design document, which proposed an in-repository file WAL that was never built, is archived as [QUORUS_RAFT_WAL_DESIGN.md](../archive/QUORUS_RAFT_WAL_DESIGN.md).

## 2. Dependency and Provenance

| Item | Value | Source |
|---|---|---|
| Coordinates | `io.github.mraysmit:raftlog-core:1.2.0`, from Maven Central | `pom.xml:32` (`raftlog.version`), `pom.xml:63-67`; `quorus-controller/pom.xml:78-81` |
| Release | Commit `1c5af80f13a149663926c01eb15f88c14c4f2d25`, tag `v1.2.0`, in the separate raftlog repository | Recorded on 2026-09-05; not re-verified from this repository |
| Manifest | `Implementation-Version: 1.2.0`, `Build-Jdk-Spec: 25` | `META-INF/MANIFEST.MF` of the jar |
| Classes Quorus uses | `dev.mars.raftlog.storage.FileRaftStorage`, `RaftStorage`, `RaftStorageConfig` | `RaftLogStorageAdapter.java:19-20`, `RaftStorageFactory.java:41-44` |
| Classes Quorus does not use | The library's `AppendPlan` | No reference in `quorus-*/src` |

`raftlog-core` is the only WAL and Raft metadata store. Quorus has no internal file WAL, no RocksDB backend and no memory storage backend.

## 3. Layering

```
QuorusControllerVerticle ──► RaftStorageFactory.create(vertx, type, path, fsync)
                                   │  builds RaftStorageConfig(dataDir, syncEnabled)
                                   ▼
RaftNode ──► RaftStorage (Quorus interface, Vert.x Future)
                 │
                 ▼
            RaftLogStorageAdapter ──► FileRaftStorage (raftlog-core: meta.dat, raft.log, raft.lock)
                 │
                 └──────────────────► FileSnapshotStore (Quorus: snapshot.dat, snapshot.required)
```

| Layer | Role | Source |
|---|---|---|
| `QuorusControllerVerticle` | Reads type, path and fsync from `AppConfig`, opens storage through the factory, then builds `RaftNode` in durable mode | `QuorusControllerVerticle.java:115-133`, `:156-169` |
| `RaftStorageFactory` | Rejects any type other than blank, `raftlog` or `wal`; builds the library configuration; opens the adapter | `RaftStorageFactory.java:33-47` |
| `RaftStorage` | Quorus's storage interface: lifecycle, metadata, log and snapshot operations, returning Vert.x `Future`s | `RaftStorage.java:45-278` |
| `RaftLogStorageAdapter` | The only production implementation. Delegates metadata, append, suffix truncation, sync, replay and prefix truncation to the library, converting its `CompletableFuture`s onto the Vert.x context. Delegates snapshots to `FileSnapshotStore` | `RaftLogStorageAdapter.java:55-226` |
| `FileSnapshotStore` | Package-private sidecar for the application snapshot and the compaction dependency marker. It stores no log entries | `FileSnapshotStore.java:22-152` |
| `RaftNodeMode` | `Durable(storage)` or `Volatile()`. Volatile mode has no storage and is used only by tests and simulations | `RaftNodeMode.java` |

The library runs all WAL and metadata I/O on one single-thread executor in submission order (`FileRaftStorage.java:184`, every operation submits to `walExecutor`). `FileSnapshotStore` runs its file I/O on Vert.x worker threads, chained so that one operation finishes before the next starts (`FileSnapshotStore.java:147-151`). The adapter waits for pending snapshot work before closing the library off the event loop (`RaftLogStorageAdapter.java:114-122`).

## 4. Storage Method Contract

| Method | Contract | Durable when the future completes? |
|---|---|---|
| `open(dataDir)` | Creates the directory, takes the exclusive lock, opens `raft.log`; then the adapter creates the snapshot store | n/a |
| `updateMetadata(term, votedFor)` | Atomically replaces `meta.dat` | Yes, when fsync is enabled |
| `loadMetadata()` | Returns term 0 and no vote if `meta.dat` is absent; fails on a corrupt file | n/a |
| `appendEntries(entries)` | Appends APPEND records | **No.** Durable only after `sync()` |
| `truncateSuffix(fromIndex)` | Appends a TRUNCATE marker that removes indexes `>= fromIndex` at replay | **No.** Durable only after `sync()` (or a later prefix compaction, which rewrites the file) |
| `sync()` | Forces `raft.log` to disk. The durability barrier for appends and truncations | Yes. A no-op when fsync is disabled |
| `replayLog()` | Returns the logical log in record order, repairing a torn tail | n/a |
| `saveSnapshot(data, index, term)` | Atomically publishes `snapshot.dat`; rejects coordinates older than the published snapshot | Yes |
| `loadSnapshot()` | Returns the published snapshot, or empty for a fresh node. Fails on a corrupt snapshot, a corrupt marker, or when the marker requires a snapshot that is missing or older | n/a |
| `truncatePrefix(toIndex)` | Requires a published snapshot covering `toIndex`, publishes `snapshot.required`, then has the library rewrite `raft.log` without indexes `<= toIndex` | Yes, always forced, even with fsync disabled |
| `close()` | Drains snapshot work, closes the WAL and releases the lock | n/a |

Sources: `RaftStorage.java:51-222`; `RaftLogStorageAdapter.java:106-207`; `FileSnapshotStore.java:36-141`; library `FileRaftStorage.java:250-581`.

`RaftNode` applies the contract as follows:

- **Persist before responding.** A follower persists the truncation and appends of an `AppendEntries` request, calls `sync()`, and only then changes its in-memory log and replies success (`RaftNode.java:1426-1462`, `:1467-1495`). A leader persists and syncs each new entry before adding it to its in-memory log (`RaftNode.java:657-661`, `:712-719`).
- **Persist before granting.** A vote is granted only after `updateMetadata` succeeds (`RaftNode.java:1227-1240`). A candidate persists its new term and self-vote before sending vote requests (`RaftNode.java:988-996`). A higher term seen in a rejected vote request, or in `AppendEntries` or `InstallSnapshot`, is persisted before the reply (`RaftNode.java:1269-1284`, `:1360-1373`, `:1912-1925`).
- **One log mutation at a time.** Leader submits, `AppendEntries`, vote requests, snapshot capture and snapshot installation are queued behind each other (`serializeLogMutation`, `RaftNode.java:691-707`). The follower decides what is new by reading its in-memory log, which reflects an entry only after the WAL write completes; without the queue, two overlapping requests could persist the same index twice.
- **`lastApplied` is not persisted.** `meta.dat` holds only term and vote. Recovery derives `lastApplied` from the snapshot or by re-applying the log (§8).

Log payloads are Protobuf `RaftCommandMessage` bytes (`ProtobufCommandCodec.java`); snapshot payloads are Jackson JSON of `QuorusSnapshot` (`QuorusStateStore.java:1140-1165`). Both carry a schema version. The current version of both contracts is 3 and versions 0 to 3 are readable (`SchemaVersionRegistry.java:40-41`); a newer version is rejected before it is applied (`ProtobufCommandCodec.java:94-96`, `QuorusStateStore.java:1172-1174`).

## 5. RaftLog Integration Contract

This section carries forward the integration contract recorded on 2026-09-05 as Appendix F.5 of the archived WAL design. Each point was re-checked against the 1.2.0 sources and the Quorus code on 2026-10-03.

- **Append and replay do not deduplicate.** Replay returns every APPEND record and applies each TRUNCATE marker by removing indexes at or above it; it does not reject duplicate indexes or gaps (`FileRaftStorage.java`, `readLog`). Quorus therefore owns the append plan: the follower skips incoming entries whose index and term already match, truncates from the first conflicting index, persists, then syncs before replying and before changing memory (`RaftNode.java:1401-1462`). A matching shorter request leaves the follower's tail in place. On recovery Quorus places each record at its own index: a repeat with the same term is ignored, a different term supersedes the tail from that index, and a gap fails recovery (`RaftNode.java:538-561`). The library's `AppendPlan` assumes a log starting at index 1 and is not used, because Quorus's in-memory log starts at the snapshot boundary.
- **Prefix compaction.** `truncatePrefix(toIndex)` resolves existing TRUNCATE markers, keeps entries with index greater than `toIndex`, and rewrites them with their index, term, payload and order unchanged. `meta.dat` is not touched. Zero is a no-op and a negative boundary fails. Suffix truncation alone only appends a marker and reclaims no space (`FileRaftStorage.java:484-497`, `:523-566`).
- **Snapshot ownership.** Quorus owns the application snapshot, its last-included index and term, and the dependency marker. A covering snapshot is published before prefix compaction is requested, and memory is trimmed only after compaction succeeds (`RaftNode.java:1708-1722`; `RaftLogStorageAdapter.java:203-207`). The library records no minimum append index, so Quorus must never re-append a compacted index.
- **Compaction durability and failure.** On its single executor the library writes `raft.log.tmp`, forces it, atomically replaces `raft.log`, forces the directory except on Windows, and reopens the WAL before completing; no extra `sync()` is needed (`FileRaftStorage.java:523-566`, `CompactionIo.java:19-30`). A failure before replacement deletes the temporary file and keeps the old WAL. A failure after replacement has started fences the instance: every later operation fails until the storage is closed and reopened, and the snapshot and marker must be kept. Compaction refuses a WAL with a corrupt or incomplete tail; that tail must first be repaired by replay. On open, a leftover `raft.log.tmp` is deleted when `raft.log` exists, and opening fails when only the temporary file exists (`FileRaftStorage.java:263-266`).
- **Compatibility.** The WAL record format is version 1. The library interface's default `truncatePrefix` fails explicitly for an implementation without compaction (library `RaftStorage.java:145-147`). Compaction reads the whole logical log into memory and writes the retained tail to a temporary file, so it needs memory and free disk space; the library itself never compacts in the background.
- **What this does not establish.** The library's own test results and the earlier "41 selected Quorus tests" figure were reported on 2026-09-05 and are not re-verified here. None of this closes production-filesystem or power-loss acceptance (§13).

## 6. On-Disk Layout

One directory per controller, set by `quorus.raft.storage.path` (§7).

| File | Owner | Content | Written by |
|---|---|---|---|
| `raft.lock` | raftlog | Empty lock file; an exclusive OS file lock is held while open | `open` (`FileRaftStorage.java:823-845`) |
| `meta.dat` | raftlog | Term (8 bytes), vote length (4), vote (UTF-8), CRC32C (4) | `updateMetadata` via `meta.dat.tmp` and an atomic move (`FileRaftStorage.java:320-376`) |
| `raft.log` | raftlog | Records: magic `0x52414654` ("RAFT", 4 bytes), version 1 (2), type (1: TRUNCATE, 2: APPEND), index (8), term (8), payload length (4), payload, CRC32C over header and payload (4) | Appends and markers; replaced whole by compaction (`FileRaftStorage.java:89-104`) |
| `raft.log.tmp` | raftlog | Unpublished compaction output; never read as authority | `truncatePrefix` |
| `meta.dat.tmp` | raftlog | Unpublished metadata | `updateMetadata` |
| `snapshot.dat` | Quorus | Magic `0x51534E50` ("QSNP"), format version 1, last-included index (8), term (8), payload length (4), payload, CRC32C over everything before it (4). The older unversioned layout (index, term, length, payload, CRC32C) is still readable | `saveSnapshot` (`FileSnapshotStore.java:36-53`, `:109-141`) |
| `snapshot.required` | Quorus | Minimum recovery index (8) and CRC32C (4). Its index only increases | `truncatePrefix`, before the library deletes anything (`FileSnapshotStore.java:77-107`) |
| `snapshot.dat.tmp`, `snapshot.required.tmp` | Quorus | Unpublished output; never read | `publish` (`FileSnapshotStore.java:55-70`) |

The controller image sets `QUORUS_RAFT_STORAGE_PATH=/app/data/raft` (`quorus-controller/Dockerfile:47`), and the Compose files mount a named volume at `/app/data` (for example `docker/compose/docker-compose.yml:34-35`). The image packages the host-built controller jar on `amazoncorretto:27.0.0-alpine3.24`; nothing is compiled in Docker (`quorus-controller/Dockerfile:1-26`).

## 7. Configuration

### 7.1 Quorus keys

Each key can be set in `quorus-controller.properties`, a profile file, or an environment variable formed by upper-casing the key and replacing `.` and `-` with `_` (`LayeredProperties.java:41-46`, `:78-80`). This works for keys that are absent from the packaged file too, because a lookup falls back to the environment. JVM system properties are not a Quorus configuration source (`AppConfig.java:33-36`).

| Key | Environment variable | Default | Notes | Source |
|---|---|---|---|---|
| `quorus.raft.storage.type` | `QUORUS_RAFT_STORAGE_TYPE` | `raftlog` | `AppConfig.validate()` rejects anything else. The factory alone also accepts blank and `wal`, for direct callers | `quorus-controller.properties:93`; `AppConfig.java:162-164`, `:410-415`; `RaftStorageFactory.java:35-40` |
| `quorus.raft.storage.path` | `QUORUS_RAFT_STORAGE_PATH` | `./data/raft/{nodeId}` when blank | Must differ for each node. Images set `/app/data/raft` | `quorus-controller.properties:99`; `AppConfig.java:170-173` |
| `quorus.raft.storage.fsync` | `QUORUS_RAFT_STORAGE_FSYNC` | `true` | When `false`, `sync()` does nothing and `meta.dat` is not forced; snapshot files and compaction are still forced | `quorus-controller.properties:103`; `AppConfig.java:179-181` |
| `quorus.raft.snapshot.enabled` | `QUORUS_RAFT_SNAPSHOT_ENABLED` | `true` | Ignored (off) in volatile mode | `AppConfig.java:189-191`; `RaftNode.java:297` |
| `quorus.raft.snapshot.threshold` | `QUORUS_RAFT_SNAPSHOT_THRESHOLD` | `10000` | Applied entries since the last snapshot that trigger a new one. Must be at least 1 | `AppConfig.java:198-200`, `:401-404` |
| `quorus.raft.snapshot.check-interval-ms` | `QUORUS_RAFT_SNAPSHOT_CHECK_INTERVAL_MS` | `60000` | How often the leader checks the threshold. Must be at least 1000 | `AppConfig.java:207-209`, `:405-408` |
| `quorus.raft.log.hard-limit` | `QUORUS_RAFT_LOG_HARD_LIMIT` | `100000` | The leader rejects a new command with "Raft log at capacity" when its in-memory log holds this many entries. Not validated; followers and replay do not apply it | `quorus-controller.properties:111`; `AppConfig.java:218-220`; `RaftNode.java:141`, `:645-651` |

The snapshot keys are not in the packaged properties file; their defaults come from `AppConfig`. There is no soft limit, no `LogCapacityExceededException` and no log-utilisation metric. The test classpath's `quorus-controller.properties` sets `quorus.raft.storage.fsync=false` (`quorus-controller/src/test/resources/quorus-controller.properties:13`).

### 7.2 Library settings Quorus does not expose

Quorus sets only the data directory and fsync flag on the library configuration (`RaftStorageFactory.java:41-44`). The library resolves its other settings itself, in the order JVM system property, environment variable, `raftlog.properties` on the classpath or working directory, then default (`RaftStorageConfig.java:87-91`, `:229-246`):

| Setting | System property | Environment variable | Default | Effect |
|---|---|---|---|---|
| Minimum free space | `raftlog.minFreeSpaceMb` | `RAFTLOG_MIN_FREE_SPACE_MB` | 64 MB | `open` and compaction fail below it |
| Maximum payload | `raftlog.maxPayloadSizeMb` | `RAFTLOG_MAX_PAYLOAD_SIZE_MB` | 16 MB | Larger appends fail; at replay a record above it ends the valid log (§11) |
| Write verification | `raftlog.verifyWrites` | `RAFTLOG_VERIFY_WRITES` | `false` | Reads back each record to check its CRC |

No Quorus file or image sets these, so the defaults apply.

## 8. Startup and Recovery Order

1. `QuorusControllerApplication` validates `AppConfig`, including the storage type and snapshot settings (`QuorusControllerApplication.java:64`).
2. The verticle opens storage through the factory (`QuorusControllerVerticle.java:115-133`). The library creates the directory, takes `raft.lock` (failing if another process or the same JVM holds it), refuses a directory holding `raft.log.tmp` without `raft.log`, deletes a stale `raft.log.tmp`, checks free space and opens `raft.log` (`FileRaftStorage.java:250-288`).
3. The verticle builds `RaftNode`, starts the gRPC server, then calls `RaftNode.start()` (`QuorusControllerVerticle.java:156-182`).
4. `RaftNode.start()` recovers before it starts its transport listener and election timer (`RaftNode.java:395-407`). Recovery runs in this order (`RaftNode.java:428-528`):
   1. Load metadata: term and vote.
   2. Load the snapshot. Recovery fails if the snapshot or marker is corrupt, or if the marker requires a snapshot that is missing or older than the marker (`FileSnapshotStore.java:94-141`). If a snapshot exists, the state machine is restored from it, and `lastApplied` and `commitIndex` are set to its index.
   3. Replay the WAL. The library stops at the first record with a bad header, length or CRC, and truncates the file there.
   4. If the WAL still holds the snapshot's boundary index with a different term (an installation interrupted after publishing the snapshot), the suffix after the boundary is truncated and prefix compaction is completed before anything else (`RaftNode.java:471-485`).
   5. Place each record by index (§5). Records at or below the snapshot boundary are skipped.
   6. Commit: a single-node cluster treats its whole local log as committed and applies it. A multi-node cluster applies nothing beyond the snapshot until a leader's commit index arrives (`RaftNode.java:500-508`, `:567-586`). Without a snapshot, the state machine is reset before re-application.

A failure at any step fails `RaftNode.start()` and the controller does not start.

## 9. Snapshots, Compaction and InstallSnapshot

### 9.1 Taking a snapshot

- Only the leader schedules snapshots. The check timer starts when a node becomes leader (`RaftNode.java:1088`, `:1625-1635`) and does nothing on a non-leader (`RaftNode.java:1641-1656`).
- A snapshot is taken when `lastApplied − snapshotLastIndex >= threshold` (`RaftNode.java:1646-1655`). `takeSnapshot()` is queued behind pending log mutations (`RaftNode.java:1666-1667`).
- Order: capture state on the event loop → `saveSnapshot` → `truncatePrefix` (marker, then WAL rewrite) → trim the in-memory log and update the boundary (`RaftNode.java:1670-1749`).
- A follower never compacts its own WAL. Its WAL and in-memory log shrink only when it installs a snapshot from the leader (§9.2).

### 9.2 InstallSnapshot

Leader side (`RaftNode.java:1497-1504`, `:1770-1877`):

- When a follower's `nextIndex` is at or below the leader's snapshot boundary, the leader loads its snapshot from storage and sends it in chunks of 1 MiB (`SNAPSHOT_CHUNK_SIZE`, `RaftNode.java:145`), one chunk at a time, with at most one installation per follower.
- A rejected chunk is resent from the chunk the follower asks for. A higher term in the reply makes the leader step down. Losing leadership aborts the send.
- After the last chunk is acknowledged, the leader sets the follower's `nextIndex` and `matchIndex` from the snapshot index.

Follower side (`RaftNode.java:1889-2055`), queued with the other log mutations:

1. Reject a stale term. Persist a higher term before continuing.
2. Collect chunks per leader in memory. A different chunk count or snapshot index starts a new collection; an out-of-order chunk is rejected with the expected chunk number. Partial transfers are not persisted.
3. On the last chunk, keep the in-memory suffix only if the follower holds the boundary index with the same term.
4. `saveSnapshot`. If the boundary does not match, `truncateSuffix(index + 1)`, so that a restart cannot bring back entries from a superseded leader. Then `truncatePrefix(index)`.
5. Only after storage succeeds: restore the state machine, rebuild the in-memory log from the boundary plus any retained suffix, and set `snapshotLastIndex`, `lastApplied` and `commitIndex`.
6. On a storage failure, reply failure with chunk 0 and change nothing in memory.

If installation stops after step 4 publishes the snapshot but before compaction finishes, recovery step 4 in §8 completes it.

## 10. Platform Durability

| Operation | Linux and other non-Windows | Windows |
|---|---|---|
| WAL `sync()` | `FileChannel.force(true)` | Same |
| `meta.dat` replacement | File force, atomic move, directory force (both forces only when fsync is enabled); a failed directory force is logged and ignored | Directory force skipped |
| WAL compaction | File force, atomic move, directory force; a failed directory force fails compaction | Directory force skipped |
| Snapshot and marker publication | File force, atomic move, directory force | Directory force skipped |

Sources: `FileRaftStorage.java:500-520`, `:320-376`, `:798-811`; `CompactionIo.java:23-30`; `FileSnapshotStore.java:55-70`.

On Linux file systems such as ext4 and XFS, a rename is not durable until the directory is forced. Windows is a development platform only: results there are not evidence of production durability. On Windows, an open handle on a file (for example from a virus scanner) can make an atomic replacement fail; the library closes its own WAL handle before replacement for this reason (`FileRaftStorage.java:545-550`).

## 11. Operator Rules

1. **One process per directory, one directory per node.** The library's lock refuses a second opener. Never point two controllers at the same path or copy one node's directory to another node.
2. **Back up and restore the whole directory, from a stopped node.** `meta.dat`, `raft.log`, `snapshot.dat` and `snapshot.required` depend on each other. Never back up, restore or replace one of them alone, and never copy a live directory. Validate a backup by restoring it.
3. **Never delete `snapshot.required` to get past a recovery failure.** The marker is what stops a node with a compacted WAL and a lost snapshot from starting as if it were empty.
4. **Never delete `meta.dat`.** A missing file loads as term 0 with no vote, which allows the node to vote twice in a term it has already voted in.
5. **Never delete `raft.log` or `snapshot.dat` from a node that has compacted.** An empty WAL after compaction is not proof of an empty state.
6. **Leave temporary files alone.** `raft.log.tmp` is cleaned up on open when `raft.log` exists. If only `raft.log.tmp` exists, open fails on purpose: preserve the directory and recover from a backup or a healthy replica.
7. **Keep `quorus.raft.storage.fsync=true` in production.** With `false`, acknowledged entries and votes can be lost on a crash.
8. **Do not lower `RAFTLOG_MAX_PAYLOAD_SIZE_MB` (or `raftlog.maxPayloadSizeMb`) on an existing directory.** Replay treats a record above the limit as the end of the valid log and truncates the file there.
9. **Keep free space above 64 MB plus the retained log.** The library refuses to open or compact below its free-space floor, and compaction writes a full copy of the retained log first.
10. **There is no migration path.** No other storage format is supported or converted. Rolling back the binary does not restore compacted entries, and builds older than the snapshot sidecar (commit `e7c9dbc`, 2026-09-04) do not understand `snapshot.required`.
11. **On a recovery failure, preserve the directory as evidence** and restore from a verified backup or a healthy replica.

## 12. Test Map

Tests are under `quorus-controller/src/test/java/dev/mars/quorus/controller/`. "Default" means the test runs in the normal build; `slow` and `docker` tests are excluded by default and run with `-Dgroups=...` (`quorus-controller/pom.xml:20-21`, `:267-271`).

| Test class | What it covers | Run |
|---|---|---|
| `raft/storage/RaftStorageContractTest` | Metadata persistence and reopen; append, reopen and replay; suffix truncation and re-append; torn-write replay; snapshot round trip; compaction followed by a fresh instance; prefix truncation keeps later entries | Default |
| `raft/storage/RaftLogStorageAdapterTest` | The adapter with fsync on and off; the factory with fsync on and off | Default |
| `raft/storage/RaftStorageFactoryTest` | `memory`, `inmemory`, `in-memory`, `test`, `rocksdb`, `rocks` and `file` are rejected before opening; `raftlog` and blank give the library adapter | Default |
| `raft/storage/SnapshotRecoveryBoundaryTest` | No compaction without a snapshot; missing snapshot after compaction fails closed; an older snapshot cannot replace the baseline; the legacy snapshot layout is readable; an interrupted temporary write keeps the published snapshot; a failed publication keeps the old state and WAL; a corrupt snapshot is not treated as a fresh node | Default |
| `raft/RaftSnapshotTest` | Threshold trigger, manual compaction, commands after a snapshot, assignments and queue preserved, snapshots disabled, save and load through storage, snapshot metrics | Default |
| `raft/InstallSnapshotTest` | Leader sends to a lagging follower; follower state restored; leader indexes updated; stale term rejected; chunk reassembly; follower persists the installed snapshot | Default |
| `raft/FollowerRestartConsistencyTest` | Overlapping `AppendEntries` and leader submits do not duplicate indexes; recovery places repeated records by index; snapshot and installation queue behind appends; matching and conflicting suffixes across restart; interrupted installation does not resurrect a conflicting suffix | Default |
| `raft/ConcurrentVoteBoundaryTest` | Overlapping durable votes grant only one candidate and survive reopen | Default |
| `raft/RaftNodeTest` (durable cases) | Election persists term and vote; multi-node recovery does not apply an uncommitted tail; single-node recovery re-applies; higher term persisted when vote persistence fails; `AppendEntries` and `InstallSnapshot` fail when higher-term persistence fails, using failure-injecting `RaftStorage` decorators | Default |
| `raft/DurableTransferRestartTest` | A committed transfer survives a single-controller restart and is visible through REST | Default |
| `raft/ThreeControllerDurableRestartTest` | Full three-controller restart, with and without snapshot compaction of the whole WAL; registry migration survives restart | Default |
| `raft/RaftLogClusterIntegrationTest` | WAL files created; vote metadata, log entries and state survive restart in a three-node in-memory cluster | `slow` |
| `raft/ContainerRecreationDurabilityTest` | Register item R1-1: state survives destroying and recreating every controller container on named volumes, including after compaction and for a single node; removing the volumes loses state, proving the test is not vacuous | `docker` |
| `state/SchemaCompatibilityTest` | Command codec reads version 0 and writes the current version; rejects a future version; legacy commands replay across a snapshot | Default |

Storage-dependent tests use the real adapter on temporary directories. The volatile in-memory transport simulations are not storage tests and prove nothing about durability. No test exercises the log hard limit.

## 13. Not Proven and Known Gaps

| Item | State |
|---|---|
| Container recreation (register R1-1) | **Proven** on 2026-09-07 for Docker container destruction and recreation on named volumes (`ContainerRecreationDurabilityTest`). A graceful container stop is not a power cut, and on Docker Desktop the containers run in a virtual machine with its own page cache |
| Machine power loss (register R1-3) | **Not proven.** No test interrupts host power. Nothing shows that committed state, snapshot and WAL coordinates survive an unclean power loss |
| Production file system (register R1-2) | **Not proven.** The recovery, retained-tail, corruption and concurrent-mutation cases have not been repeated on the supported production file system and storage class |
| Follower compaction | Followers do not take snapshots (§9.1). A follower's WAL and in-memory log grow until it installs a leader snapshot |
| Log hard limit | Applies only to leader submits; not to followers or replay; untested |
| RPCs during recovery | The gRPC server starts before `RaftNode.start()`, and its handlers call `RaftNode` without waiting for recovery (`QuorusControllerVerticle.java:175-182`; `GrpcRaftServer.java:180`, `:218`, `:256`). Whether a peer RPC can be handled before recovery completes has not been assessed |

R1-2 and R1-3 are release blockers in [register §4](../task/QUORUS_OUTSTANDING_WORK_REGISTER.md#4-section-a--r1-durability-acceptance-release-blockers).

## 14. After QRaft

[ADR-0011](../architecture-decisions/ADR-0011-CONSENSUS-VIA-QRAFT-GENERIC-ENGINE.md) moves consensus to the generic QRaft engine. After `CE-10`, Quorus has no Raft node, WAL adapter or direct `raftlog-core` dependency; QRaft owns log compaction, snapshot publication and recovery, and Quorus supplies the state machine, codec and snapshot content. QRaft uses raftlog-core 1.4.0. On-disk compatibility between raftlog 1.2.0 and 1.4.0, and between this snapshot sidecar and QRaft's `SnapshotStore`, has not been established, and the cutover needs a tested migration and rollback path (`CE-09`). Until then, this reference describes the running system. Do not extend it with QRaft's design; link to QRaft's documentation instead.

---

## Revision History

| Version | Date | Changes |
|---|---|---|
| 1.0 | 2026-10-03 | Extracted from the Raft WAL design (now archived) under register item DR-C6, and verified against the source tree and the `raftlog-core` 1.2.0 sources. Carries forward the Status block, §14, the §16.1 serialisation rule, the snapshot checkpoint and operator text of §19, Appendix A and Appendix F (F.5 in §5) |
