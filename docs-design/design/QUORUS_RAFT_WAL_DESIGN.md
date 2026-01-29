# Minimal Write-Ahead Log (WAL) for Raft
**Design, Requirements, and Integration Strategy**

## Status
**Proposed – Design Review**

## Scope
This document defines a **minimal, Raft-correct Write-Ahead Log (WAL)** design for a Java & Vert.x 5.x Raft implementation.

The WAL is intentionally constrained to support **only**:
- append
- truncate (suffix deletion)
- sequential replay on startup

It is **not** a general-purpose storage engine.

---

## 1. Motivation

Quite often we see Raft implementations storing the following **entirely in memory**:

- `currentTerm`
- `votedFor`
- the Raft log (`List<LogEntry>`)

This violates Raft’s persistence requirements.

### Consequences of the this type of design
- Node restart resets `currentTerm` and `votedFor` → double voting, split elections
- Followers can acknowledge log entries that are lost on crash
- Log divergence and data loss become possible

In short: **this implementation design is unsafe under restart or crash**.

---

## 2. Raft Persistence Rules (Non-Negotiable)

Per the classic Raft paper, the following **must be durably persisted**:

### Persistent State
- `currentTerm`
- `votedFor`
- log entries

### Critical Rule
> **Persist-before-response**  
> A node must not respond positively to `RequestVote` or `AppendEntries` until the corresponding state is durably stored.

Durability means: **written to disk and fsync’d**.

---

## 3. Design Constraints & Assumptions

This WAL is designed around the following assumptions:

- The Raft log is kept fully **in memory during runtime**
- Disk is only used for:
  - crash recovery
  - restart replay
- No random disk reads are required during steady state
- No snapshots or compaction are required initially
- Correctness is prioritised over throughput

These constraints significantly simplify the WAL design.

---

## 4. High-Level Architecture

### Files
```
data/
 ├─ meta.dat     // currentTerm + votedFor
 └─ raft.log     // append-only WAL
```

### Responsibilities
- `meta.dat`
  - Stores `(currentTerm, votedFor)`
  - Atomically rewritten on update
- `raft.log`
  - Append-only
  - Stores both truncation markers and appended log entries
  - Sequentially replayed on startup

---

## 5. WAL Record Model

Only **two record types** are required.

```
RecordType:
  - TRUNCATE
  - APPEND
```

### Record Format (binary)
Each record is self-framing and checksummed:

| Field            | Type   |
|------------------|--------|
| Magic            | int    |
| Version          | short  |
| Record Type      | byte   |
| Log Index        | long   |
| Term             | long   |
| Payload Length   | int    |
| Payload Bytes    | byte[] |
| CRC32C           | int    |

---

## 6. AppendEntries Integration (Critical Path)

Correct sequence per AppendEntries RPC:

1. Validate `prevLogIndex / prevLogTerm`
2. Write WAL records:
   - `TRUNCATE(conflictIndex)` if required
   - `APPEND(index, term, payload)` for each new entry
3. `fsync()` once after all records are written
4. Apply the same truncate + append to in-memory log
5. Reply `success = true`

---

## 7. RequestVote Integration

When granting a vote:

1. Update `(currentTerm, votedFor)`
2. Persist both **together** in `meta.dat`
3. `fsync`
4. Reply `VoteGranted`

---

## 8. Startup Replay

On node startup:

1. Load `meta.dat`
2. Replay `raft.log` sequentially
3. Truncate `raft.log` to the last valid byte offset

---

## 9. Vert.x Integration Model

- WAL operations are blocking
- Run on a dedicated WorkerExecutor
- Raft logic remains on the event loop

---

## 10. Testing Requirements

Mandatory tests:

- Crash during append
- Crash during truncate
- Vote persistence across restart
- CRC corruption detection

---

## 11. Effort Estimate

Total estimated effort: **~2 weeks**

---

## 12. Conclusion

This WAL design is minimal, correct, and well-scoped. It avoids unnecessary complexity while meeting Raft’s safety requirements.

---

## 13. Explicit Non-Goals (Hard Constraints)

To avoid scope creep and accidental re-implementation of a database, the following are **explicit non-goals** of this WAL:

- ❌ No random-access reads from disk during runtime
- ❌ No log segments or segment rotation (initially)
- ❌ No background compaction
- ❌ No snapshots (initially)
- ❌ No key/value semantics
- ❌ No concurrent writers
- ❌ No read-after-write guarantees beyond in-memory state
- ❌ No attempt to make the WAL "fast" by weakening durability rules

The WAL exists **solely** to:
- survive crashes
- enforce Raft safety rules
- rebuild in-memory state on restart

Anything beyond that is out of scope.

### 13.1 No Random-Access Reads from Disk

**What this means:** During normal runtime, the WAL is **write-only**. The system never seeks to a specific position to read a particular entry. All log entries are served from the in-memory `List<LogEntry>`.

**Why it's a non-goal:** Random-access reads require an **index** (entry index → file offset mapping). Building and maintaining an index:
- Adds complexity (index corruption, index-vs-data consistency)
- Requires additional disk I/O (index file reads)
- Is unnecessary when the entire log fits in memory

**Trade-off:** The entire Raft log must fit in memory. For Quorus's expected workload (metadata operations, not bulk data), this is acceptable. If you had millions of entries, you'd need segments + index.

**If you need this later:** Add a separate index file that maps `logIndex → fileOffset`, written during append and read during recovery to enable random access.

### 13.2 No Log Segments or Segment Rotation

**What this means:** There is **one file** that grows unbounded: `raft.wal`. We don't split the log into multiple segment files (e.g., `segment-0001.wal`, `segment-0002.wal`).

**Why it's a non-goal (initially):** Segmentation adds significant complexity:
- Deciding when to rotate (size-based? entry-count-based? time-based?)
- Managing the "active" vs "sealed" segment distinction
- Handling recovery across multiple files
- Coordinating segment deletion after snapshots

For an Alpha release, a single file simplifies everything.

**Trade-off:** File size grows unbounded until snapshots are implemented. For early testing and development, this is acceptable.

**If you need this later:** Implement segment rotation when the current segment exceeds a size threshold (e.g., 64MB), seal the old segment, open a new one, and update recovery to scan all segments in order.

### 13.3 No Background Compaction

**What this means:** Unlike LSM-tree databases (RocksDB, LevelDB), we don't run background threads merging or compacting data files.

**Why it's a non-goal:** Raft log entries are **immutable and ordered**. There's nothing to "compact" in the traditional sense. Compaction in databases removes obsolete key versions—Raft entries don't have versions, they're an append-only sequence.

**Trade-off:** None for correctness. This is simply not applicable to WAL semantics.

**If you need this later:** You don't. What you need instead is **snapshots** (see 13.4) to truncate prefix entries that have been applied to the state machine.

### 13.4 No Snapshots (Initially)

**What this means:** There's no mechanism to "checkpoint" the state machine and truncate old log entries. The log grows forever.

**Why it's a non-goal (initially):** Snapshots are complex:
- Must capture a consistent state machine image
- Must track the "last included index/term" for Raft protocol correctness
- Must handle InstallSnapshot RPC for slow followers
- Must coordinate snapshot creation with ongoing operations

This is significant implementation effort that can be deferred.

**Trade-off:** Unbounded log growth. Acceptable for Alpha/Beta where logs stay small. Must be addressed before production deployment with long-running clusters.

**If you need this later:** Implement a `createSnapshot(lastIncludedIndex, lastIncludedTerm, stateData)` method that writes a snapshot file and truncates all log entries ≤ `lastIncludedIndex`.

### 13.5 No Key/Value Semantics

**What this means:** The WAL treats every entry as an **opaque byte array**. It has no understanding of keys, values, columns, or any schema. It doesn't support "get entry by key" or "delete key".

**Why it's a non-goal:** The WAL's job is to persist the **Raft log**, which is a sequence of commands. The interpretation of those commands belongs to the **state machine**, not the storage layer.

**Trade-off:** The WAL cannot answer queries like "what's the latest value for key X?" — that's the state machine's responsibility.

**If you need this later:** You don't want it in the WAL. You want a state machine that maintains key/value state by processing log entries.

### 13.6 No Concurrent Writers

**What this means:** Exactly **one thread** (the `walExecutor`) performs all write operations. There's no support for multiple threads appending simultaneously.

**Why it's a non-goal:** Raft has a **single leader** at any term. Only the leader appends entries. Even on a single node, the Raft algorithm is sequential—there's no benefit to concurrent writes.

**Trade-off:** Write throughput is limited to single-threaded performance. For Raft metadata operations, this is more than sufficient.

**If you need this later:** You don't for Raft. If you somehow need parallel writes, you're probably building something other than a Raft log.

### 13.7 No Read-After-Write Guarantees Beyond In-Memory State

**What this means:** After you call `appendEntries()`, you can immediately read those entries—from the **in-memory log**. There's no guarantee you can read them **from disk** until recovery.

**Why it's a non-goal:** During normal operation, all reads go to memory. The WAL is purely a durability mechanism. The only time we read from the WAL file is during recovery at startup.

**Trade-off:** You cannot implement "read entry X from disk" during runtime. All reads must go through the in-memory `List<LogEntry>`.

**If you need this later:** This pattern is actually correct for WAL design. If you need disk-based reads during runtime, you're building a database, not a WAL.

### 13.8 No Durability Weakening for Performance

**What this means:** We will **not** implement "optimizations" that trade durability for speed:
- No `fsync` batching across unrelated operations
- No periodic `fsync` instead of per-operation
- No reliance on OS write-back caching
- No `fdatasync` (we use `fsync` which also syncs metadata)

**Why it's a non-goal:** Raft safety requires that once we respond to an RPC, the data **must** be durable. Any "optimization" that violates this breaks Raft's correctness guarantees.

**Acceptable optimizations:**
- Batching multiple entries in a single `appendEntries()` call (with one `fsync` at the end)
- Using memory-mapped files (still with explicit `msync`)
- Using kernel AIO with synchronous completion notification

**Unacceptable optimizations:**
- Responding before `fsync` completes
- Using `O_DIRECT` without explicit `fsync`
- Disabling `fsync` for "testing" and forgetting to re-enable

**Trade-off:** Writes are as slow as `fsync` allows (typically 1-10ms on SSD). For Raft consensus operations, this is acceptable. High-throughput systems should batch multiple entries per append.

**If you need this later:** The only safe path is batching—accumulating multiple entries and syncing once. Never skip or defer `fsync`.

---

## 14. Generic Raft Storage Interface (`RaftStorage`)

To ensure the system can switch between a **Custom WAL** (simple, pure Java) and **RocksDB** (high performance, key-value), we define a backend-agnostic interface. The `RaftNode` will depend solely on this interface.

```java
package dev.mars.quorus.controller.raft.storage;

import io.vertx.core.Future;
import java.io.Closeable;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface RaftStorage extends Closeable {

  /** Opens the storage engine. Idempotent. */
  Future<Void> open(Path dataDir);

  // ---- Metadata (Term & Vote) ----

  /** 
   * Atomically persists the current term and vote. 
   * Implementation MUST ensure durability (fsync) before returning.
   */
  Future<Void> updateMetadata(long currentTerm, Optional<String> votedFor);

  /** Loads metadata on startup. Returns (0, empty) if no state exists. */
  Future<PersistentMeta> loadMetadata();

  record PersistentMeta(long currentTerm, Optional<String> votedFor) {}

  // ---- Log Operations ----

  /** 
   * Appends a batch of entries to the log. 
   * NOT required to fsync immediately (use sync() for that).
   */
  Future<Void> appendEntries(List<LogEntryData> entries);
  
  record LogEntryData(long index, long term, byte[] payload) {}

  /** 
   * Deletes all log entries with index >= fromIndex.
   * Used to resolve conflicts when a follower diverges from the leader.
   */
  Future<Void> truncateSuffix(long fromIndex);

  /**
   * Universal Durability Barrier.
   * Forces all pending appends/truncations to physical disk.
   * Must be called before acknowledging AppendEntries RPCs.
   */
  Future<Void> sync();

  /** 
   * Replays the entire log from disk on startup.
   * For RocksDB: Scans keys `log:1` to `log:N`.
   * For FileWAL: Scans the append-only file sequentially.
   */
  Future<List<LogEntryData>> replayLog();
}
```

### 14.1 Plug-in Implementations

| Feature | **FileRaftStorage** (Custom WAL) | **RocksDbRaftStorage** (Adapter) |
| :--- | :--- | :--- |
| **Metadata** | Atomic rename of `meta.dat` | `batch.put("meta:term", ...)` |
| **Append** | `FileChannel.write()` (append-only) | `batch.put("log:<index>", ...)` |
| **Truncate** | `FileChannel.truncate()` | `db.deleteRange("log:<index>", "log:MAX")` |
| **Sync** | `FileChannel.force(false)` | `db.write(batch, {sync: true})` |
| **Replay** | Sequential read + CRC check | Iterator scan over `log:*` prefix |

---

## 15. Implementation A: FileRaftStorage (The Custom WAL)

This is the default implementation for the Alpha release (Zero-Dependency).

This section describes a minimal, crash-safe implementation using `FileChannel`.

### 15.1 Files
```
data/
 ├─ meta.dat       // currentTerm + votedFor (atomic replace)
 └─ raft.log       // append-only WAL: TRUNCATE and APPEND records
```

### 15.2 Record framing (raft.log)

A simple, robust binary record:

| Field | Type |
|------|------|
| MAGIC | int |
| VERSION | short |
| TYPE | byte | 
| INDEX | long |
| TERM | long |
| PAYLOAD_LEN | int |
| PAYLOAD | byte[] |
| CRC32C | int |

- **CRC32C** covers all bytes from `MAGIC` through `PAYLOAD`.
- On replay, stop at the first:
  - incomplete record
  - CRC mismatch
- Then truncate `raft.log` to the last known-good byte offset.

### 15.3 Types

```java
static final byte TYPE_TRUNCATE = 1;
static final byte TYPE_APPEND   = 2;
```

### 15.4 meta.dat (term + vote)
`meta.dat` must be updated atomically. The simplest safe method:

1. write new bytes to `meta.dat.tmp`
2. `force(true)` the tmp file
3. atomic move/rename `meta.dat.tmp` → `meta.dat`
4. fsync the directory (optional but recommended on Linux for maximum safety)

This avoids partial meta overwrites.

### 15.5 Vert.x offload + serialization model
- `FileChannel` I/O is blocking → run on a **dedicated WorkerExecutor**
- Enforce a **single-writer** discipline:
  - either one-thread worker pool, or
  - internal queue (actor style)

### 15.6 Skeleton: FileRaftWAL (illustrative)

```java
package dev.mars.quorus.controller.raft.storage;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.CRC32C;

import static java.nio.file.StandardOpenOption.*;

public final class FileRaftWAL implements RaftWAL {

  private static final int MAGIC = 0x52414654; // 'RAFT'
  private static final short VERSION = 1;
  private static final byte TYPE_TRUNCATE = 1;
  private static final byte TYPE_APPEND = 2;

  private final Vertx vertx;
  private final WorkerExecutor walExecutor;

  private Path dataDir;
  private FileChannel logCh;

  public FileRaftWAL(Vertx vertx, WorkerExecutor walExecutor) {
    this.vertx = vertx;
    this.walExecutor = walExecutor;
  }

  @Override
  public Future<Void> open(Path dataDir) {
    this.dataDir = dataDir;
    return vertx.executeBlocking(p -> {
      try {
        Files.createDirectories(dataDir);
        this.logCh = FileChannel.open(dataDir.resolve("raft.log"), CREATE, READ, WRITE);
        logCh.position(logCh.size()); // seek to end for appends
        p.complete();
      } catch (Exception e) {
        p.fail(e);
      }
    }, false, walExecutor);
  }

  @Override
  public Future<Void> persistTermAndVote(long currentTerm, Optional<String> votedFor) {
    return vertx.executeBlocking(p -> {
      try {
        Path tmp = dataDir.resolve("meta.dat.tmp");
        Path dst = dataDir.resolve("meta.dat");

        byte[] voteBytes = votedFor.map(s -> s.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                                   .orElse(new byte[0]);

        ByteBuffer buf = ByteBuffer.allocate(8 + 4 + voteBytes.length + 4);
        buf.putLong(currentTerm);
        buf.putInt(voteBytes.length);
        buf.put(voteBytes);

        CRC32C crc = new CRC32C();
        crc.update(buf.array(), 0, 8 + 4 + voteBytes.length);
        buf.putInt((int) crc.getValue());
        buf.flip();

        try (FileChannel ch = FileChannel.open(tmp, CREATE, TRUNCATE_EXISTING, WRITE)) {
          while (buf.hasRemaining()) ch.write(buf);
          ch.force(true);
        }

        Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        
        // Fsync the directory to ensure the rename is durable (critical on Linux ext4/xfs)
        try (FileChannel dirCh = FileChannel.open(dataDir, READ)) {
          dirCh.force(true);
        }
        
        p.complete();
      } catch (Exception e) {
        p.fail(e);
      }
    }, false, walExecutor);
  }

  @Override
  public Future<PersistentMeta> loadMeta() {
    return vertx.executeBlocking(p -> {
      try {
        Path dst = dataDir.resolve("meta.dat");
        if (!Files.exists(dst)) {
          p.complete(new PersistentMeta(0L, Optional.empty()));
          return;
        }

        byte[] all = Files.readAllBytes(dst);
        ByteBuffer b = ByteBuffer.wrap(all);

        long term = b.getLong();
        int len = b.getInt();
        if (len < 0 || len > (all.length - (8 + 4 + 4))) throw new IOException("Corrupt meta.dat");

        byte[] vote = new byte[len];
        b.get(vote);

        int expectedCrc = b.getInt();
        CRC32C crc = new CRC32C();
        crc.update(all, 0, 8 + 4 + len);
        if (((int) crc.getValue()) != expectedCrc) throw new IOException("meta.dat CRC mismatch");

        Optional<String> votedFor = (len == 0) ? Optional.empty()
            : Optional.of(new String(vote, java.nio.charset.StandardCharsets.UTF_8));

        p.complete(new PersistentMeta(term, votedFor));
      } catch (NoSuchFileException e) {
        p.complete(new PersistentMeta(0L, Optional.empty()));
      } catch (Exception e) {
        p.fail(e);
      }
    }, false, walExecutor);
  }

  @Override
  public Future<Void> truncateFrom(long fromIndex) {
    return writeRecord(TYPE_TRUNCATE, fromIndex, 0L, new byte[0]);
  }

  /**
   * Batch append: writes all entries in a single executeBlocking call.
   * This avoids 100 separate FileChannel.write() calls for 100 entries.
   */
  @Override
  public Future<Void> appendBatch(List<LogEntryData> entries) {
    if (entries.isEmpty()) {
      return Future.succeededFuture();
    }
    return vertx.executeBlocking(p -> {
      try {
        for (LogEntryData entry : entries) {
          writeRecordSync(TYPE_APPEND, entry.index(), entry.term(), entry.payload());
        }
        p.complete();
      } catch (Exception e) {
        p.fail(e);
      }
    }, false, walExecutor);
  }

  @Override
  public Future<Void> append(long index, long term, byte[] payload) {
    return writeRecord(TYPE_APPEND, index, term, payload);
  }

  /** Synchronous write - for use within batch operations */
  private void writeRecordSync(byte type, long index, long term, byte[] payload) throws IOException {
    int payloadLen = payload.length;
    int headerLen = 4 + 2 + 1 + 8 + 8 + 4;
    ByteBuffer buf = ByteBuffer.allocate(headerLen + payloadLen + 4);

    buf.putInt(MAGIC);
    buf.putShort(VERSION);
    buf.put(type);
    buf.putLong(index);
    buf.putLong(term);
    buf.putInt(payloadLen);
    buf.put(payload);

    CRC32C crc = new CRC32C();
    crc.update(buf.array(), 0, headerLen + payloadLen);
    buf.putInt((int) crc.getValue());
    buf.flip();

    while (buf.hasRemaining()) logCh.write(buf);
  }

  private Future<Void> writeRecord(byte type, long index, long term, byte[] payload) {
    return vertx.executeBlocking(p -> {
      try {
        int payloadLen = payload.length;
        int headerLen = 4 + 2 + 1 + 8 + 8 + 4;
        ByteBuffer buf = ByteBuffer.allocate(headerLen + payloadLen + 4);

        buf.putInt(MAGIC);
        buf.putShort(VERSION);
        buf.put(type);
        buf.putLong(index);
        buf.putLong(term);
        buf.putInt(payloadLen);
        buf.put(payload);

        CRC32C crc = new CRC32C();
        crc.update(buf.array(), 0, headerLen + payloadLen);
        buf.putInt((int) crc.getValue());
        buf.flip();

        while (buf.hasRemaining()) logCh.write(buf);
        p.complete();
      } catch (Exception e) {
        p.fail(e);
      }
    }, false, walExecutor);
  }

  @Override
  public Future<Void> sync() {
    return vertx.executeBlocking(p -> {
      try {
        logCh.force(false);
        p.complete();
      } catch (Exception e) {
        p.fail(e);
      }
    }, false, walExecutor);
  }

  @Override
  public Future<List<ReplayedEntry>> replayLog() {
    return vertx.executeBlocking(p -> {
      try {
        Path logPath = dataDir.resolve("raft.log");
        if (!Files.exists(logPath)) {
          p.complete(List.of());
          return;
        }

        List<ReplayedEntry> out = new ArrayList<>();
        try (FileChannel ch = FileChannel.open(logPath, READ, WRITE)) {

          long pos = 0;
          long lastGood = 0;
          ByteBuffer hdr = ByteBuffer.allocate(4 + 2 + 1 + 8 + 8 + 4);

          while (true) {
            hdr.clear();
            int r = ch.read(hdr, pos);
            if (r < hdr.capacity()) break;
            hdr.flip();

            int magic = hdr.getInt();
            short ver = hdr.getShort();
            byte type = hdr.get();
            long index = hdr.getLong();
            long term = hdr.getLong();
            int len = hdr.getInt();

            if (magic != MAGIC || ver != VERSION || len < 0) break;

            ByteBuffer payload = ByteBuffer.allocate(len);
            int pr = ch.read(payload, pos + hdr.capacity());
            if (pr < len) break;
            payload.flip();

            ByteBuffer crcBuf = ByteBuffer.allocate(4);
            int cr = ch.read(crcBuf, pos + hdr.capacity() + len);
            if (cr < 4) break;
            crcBuf.flip();
            int expected = crcBuf.getInt();

            // compute CRC over header+payload bytes
            ByteBuffer combined = ByteBuffer.allocate(hdr.capacity() + len);
            hdr.rewind();
            combined.put(hdr);
            combined.put(payload.duplicate());
            CRC32C crc = new CRC32C();
            crc.update(combined.array(), 0, combined.capacity());
            if (((int) crc.getValue()) != expected) break;

            if (type == TYPE_TRUNCATE) {
              long from = index;
              out.removeIf(e -> e.index() >= from);
            } else if (type == TYPE_APPEND) {
              out.add(new ReplayedEntry(index, term, payload.array()));
            } else {
              break;
            }

            lastGood = pos + hdr.capacity() + len + 4;
            pos = lastGood;
          }

          ch.truncate(lastGood);
        }

        p.complete(out);
      } catch (Exception e) {
        p.fail(e);
      }
    }, false, walExecutor);
  }

  @Override
  public void close() throws IOException {
    if (logCh != null) logCh.close();
  }
}
```

**Important:** the above is a *reference outline*. The core semantics are what matter:
- frame + checksum
- stop at first bad tail
- truncate file to last-good
- explicit `sync()` barrier

---

## 16. Wiring Persistence and Application into RaftNode

This section defines how the `RaftStorage` and `AppendPlan` interact with the `RaftNode` state machine, mapping directly to:

- `RaftNode.handleVoteRequest(VoteRequest request)`
- `RaftNode.handleAppendEntriesRequest(AppendEntriesRequest request)`

### 16.1 The AppendEntries Pipeline (Follower Side)

To maintain Raft safety, a follower must never acknowledge an entry until it is durable. The process follows a strict **Prepare → Persist → Apply** sequence.

1. **Consistency Check:** Validate `prevLogIndex` and `prevLogTerm`.
2. **Prepare Plan:** Use `AppendPlan.from(...)` to identify which entries are new and if a truncation is required.
3. **Persist (WAL):**
   - Call `wal.truncateFrom(plan.truncateFromIndex)` (if applicable).
   - Call `wal.appendBatch(plan.entriesToAppend)`.
   - Call `wal.sync()` (The **Durability Barrier**).
4. **Commit & Apply:**
   - On `sync()` success, update the in-memory `List<LogEntry>`.
   - Update `commitIndex` based on `leaderCommit`.
   - Trigger the **Application Loop** (Section 16.4).

**Implementation (Vert.x):**

```java
// after passing prevLogIndex/prevLogTerm consistency check
long startIndex = request.getPrevLogIndex() + 1;

// build plan WITHOUT mutating log
AppendPlan plan = AppendPlan.from(startIndex, request.getEntriesList(), log);

// persist first
Future<Void> f = Future.succeededFuture();
if (plan.truncateFromIndex != null) {
  f = f.compose(v3 -> wal.truncateFrom(plan.truncateFromIndex));
}
for (var e : plan.entriesToAppend) {
  f = f.compose(v3 -> wal.append(e.index, e.term, e.payloadBytes));
}
f = f.compose(v3 -> wal.sync());  // <-- DURABILITY BARRIER

// then mutate in-memory + ACK
f.onSuccess(v3 -> {
  plan.applyTo(log);        // now do subList().clear() and log.add(...)
  
  // Update commitIndex based on leader's commit
  if (request.getLeaderCommit() > commitIndex) {
    commitIndex = Math.min(request.getLeaderCommit(), log.size() - 1);
    applyEntries();  // Trigger state machine application
  }
  
  promise.complete(AppendEntriesResponse.newBuilder()
      .setTerm(currentTerm)
      .setSuccess(true)
      .setMatchIndex(log.size() - 1)
      .build());
}).onFailure(err -> {
  promise.complete(AppendEntriesResponse.newBuilder()
      .setTerm(currentTerm)
      .setSuccess(false)
      .setMatchIndex(log.size() - 1)
      .build());
});
```

### 16.2 RequestVote: Persist-before-Grant

When granting a vote, the candidate's claim to the term must be made durable to prevent "double-voting" if the node crashes and restarts.

1. **Validate:** Check if `reqTerm >= currentTerm` and if the node has already voted.
2. **Persist Meta:** Call `wal.updateMetadata(currentTerm, Optional.of(candidateId))`.
3. **Grant:** On `sync()` success, reply `VoteGranted = true`.

**Implementation (Vert.x):**

```java
if (reqTerm > currentTerm) {
  stepDown(reqTerm); // updates currentTerm, clears votedFor
}

if (reqTerm == currentTerm && (votedFor == null || votedFor.equals(request.getCandidateId()))) {
  String newVote = request.getCandidateId();
  long termToPersist = currentTerm;

  wal.persistTermAndVote(termToPersist, Optional.of(newVote))
     .onSuccess(v2 -> {
        votedFor = newVote;            // mutate in-memory AFTER durability
        resetElectionTimer();
        promise.complete(VoteResponse.newBuilder()
            .setTerm(currentTerm)
            .setVoteGranted(true)
            .build());
     })
     .onFailure(promise::fail);

  return; // critical: prevent fallthrough
}

promise.complete(VoteResponse.newBuilder()
    .setTerm(currentTerm)
    .setVoteGranted(false)
    .build());
```

### 16.3 Why Persist-Before-In-Memory?

Because the follower's ACK must mean: "this change will survive restart."

Persist-first gives you a clean invariant:
- **If we ACK success, the WAL contains the truncation+append and has been fsync'd.**
- **If we grant a vote, the term and votedFor are durable before the response leaves.**

This is the **Durability Barrier** — the single most important correctness property of Raft persistence.

### 16.4 The State Machine Application Loop

The **Log** (WAL) is a sequence of intentions; the **State Machine** is the result of those intentions. The "Application" step is what actually executes the commands.

**Key Invariants:**

| Variable | Description |
|----------|-------------|
| `lastLogIndex` | The highest index in the WAL |
| `commitIndex` | The highest index known to be replicated on a majority |
| `lastApplied` | The highest index actually executed by the State Machine |

**Safety Rule:** `lastApplied <= commitIndex <= lastLogIndex`

#### Logic Flow (Vert.x Context)

Whenever the `commitIndex` is advanced (either by the Leader via majority confirmation or by the Follower via `leaderCommit`), the following loop is triggered:

```java
private void applyEntries() {
    // Catch up the state machine to the last committed index
    while (commitIndex > lastApplied) {
        long indexToApply = lastApplied + 1;
        LogEntryData entry = inMemoryLog.get((int) indexToApply);
        
        // 1. Pass payload to the business logic (e.g., KV store, Job engine)
        stateMachine.execute(entry.payload())
            .onSuccess(result -> {
                // 2. Mark as applied
                lastApplied = indexToApply;
                
                // 3. If we are the Leader, notify the waiting client
                clientRequestMap.complete(indexToApply, result);
            })
            .onFailure(err -> handleCriticalSystemError(err));
    }
}
```

**When is `applyEntries()` called?**

| Role | Trigger |
|------|---------|
| **Leader** | After receiving majority ACKs for an index, advance `commitIndex` and call `applyEntries()` |
| **Follower** | After receiving `AppendEntries` with `leaderCommit > commitIndex`, update `commitIndex` and call `applyEntries()` |

### 16.5 Why `lastApplied` is Not in `meta.dat`

Unlike `currentTerm` and `votedFor`, `lastApplied` is **not** required in the metadata file because it is **reconstructible**. On startup, the node:

1. Replays the WAL to rebuild the in-memory log.
2. Re-initializes the State Machine to a blank state (or the last snapshot).
3. Re-executes all entries from index `0` up to the last known `commitIndex`.

This means `lastApplied` is always derived from re-execution, never persisted directly.

> **Note:** This is why idempotent state machine operations are strongly recommended — entries may be re-applied during recovery.

---

## 17. Practical Integration Notes (Vert.x)

- Create a dedicated executor for WAL operations (pool size **1** is fine and simplifies ordering)
- WAL ops run via `executeBlocking(..., walExecutor)`
- Never block the event loop

---

## 18. Minimal Acceptance Criteria

Before considering the WAL "done":

- ✅ **Restart Safety:** `currentTerm` and `votedFor` are recovered before any RPC is handled
- ✅ **Durability Barrier:** `AppendEntries` response is sent ONLY after `wal.sync()`
- ✅ **Application Order:** Entries are applied to the State Machine strictly in-order
- ✅ **Leader Consistency:** Leader only advances `commitIndex` after a majority `sync()` is confirmed
- ✅ **Replay Safety:** Replay truncates corrupt/partial tail safely
- ✅ **Crash Test (Append):** Kill during append → restart yields prefix-safe log
- ✅ **Crash Test (Truncate+Append):** Kill during truncate+append sequence → replay yields last durable state

---
## 19. Design Review Feedback

**Review Date:** 2026-01-28

This section captures feedback from technical review of the WAL design.

---

### 19.0 Summary Verdict

> **This is a solid, Raft-correct, production-credible WAL design.**
> It is **minimal without being naive**, and—crucially—the **Raft safety invariants are explicit and enforced in wiring**, not hand-waved into comments.

This implementation is no longer "missing persistence" — it is **intentionally implementing Raft's persistence contract**.

If this landed in a design review with senior distributed-systems engineers, it would **pass with minor nits**, not fundamental objections.

---

### 19.1 What Was Done Correctly

#### ✅ Scope Discipline (This Is Rare)

The non-goals section (Section 13) is excellent. The design resists:
- Segmenting
- Snapshots
- Random reads
- Compaction fantasies

This is exactly how people successfully build WALs instead of accidentally rebuilding RocksDB badly. If someone later asks "can we just add X?", this document gives the authority to say **no**.

#### ✅ Persist-Before-Response Is Enforced, Not Described

The design doesn't just quote the Raft paper — it wires it:
- Vote is not granted until `meta.dat` is fsync'd
- AppendEntries is not ACK'd until `truncate + append + sync` completes
- In-memory state mutates **after** durability

That invariant is the entire game. This got it right.

#### ✅ Truncate as a Logical Record (Not Physical Truncate)

This is a good design choice for a minimal WAL.

Why it's correct:
- Physical truncation during runtime is risky
- Logical truncation + replay gives crash safety
- Final physical truncation happens only after replay

This is exactly how "real" systems do it.

#### ✅ Replay Semantics Are Correct

The replay loop:
- Stops on first torn / corrupt record
- Applies truncates in sequence
- Rebuilds in-memory log deterministically
- Truncates the file tail

This is textbook WAL recovery logic.

#### ✅ Meta Handling Is Done Properly

All of the following are implemented correctly:
- Temp file write
- Fsync temp
- Atomic rename
- Fsync directory
- CRC verification

Most implementations miss at least one of these. This design doesn't.

---

### 19.2 Issues to Address

#### ❌ CRITICAL: `truncateFrom()` Is Not Fsync-Isolated

Currently:

```java
truncateFrom(long fromIndex) {
  return writeRecord(TYPE_TRUNCATE, ...);
}
```

But:
- `writeRecord()` does **not** force durability
- Durability is deferred to `sync()`

This is fine **only if** the RaftNode *always* batches truncate + append + sync as one atomic plan.

**Why this matters:**

Someone will eventually call:

```java
wal.truncateFrom(x)
  .onSuccess(...)
```

without a following `sync()`.

Now you have:
- Logical deletion recorded
- No durability barrier
- Possible reorder with later appends

**Fix (simple and worth it):**

Make the contract explicit in the interface:

```java
/**
 * Truncation is not durable until sync() is called.
 * Must not be called standalone in RaftNode.
 */
Future<Void> truncateSuffix(long fromIndex);
```

And in `RaftNode`, enforce:
- Truncate + append **must** go through a single helper (`persistAppendPlan`)
- Never expose raw truncate calls

This is not a code bug yet — it's a **future foot-gun**.

#### ⚠️ FileChannel Positioning vs Concurrent Writes

The implementation relies on:

```java
logCh.position(logCh.size());
```

and then sequential writes.

Because the design enforces:
- Single writer
- Single worker executor

This is **currently safe**.

But document (or assert) that:
- No concurrent append paths exist
- No reopen occurs mid-flight

**Recommendation:** Add a comment or assertion:

```java
// Invariant: all writes are serialized via walExecutor (single thread)
```

Otherwise someone will "optimize" the executor size later and corrupt the log.

#### ⚠️ Replay Uses `READ, WRITE`

In replay:

```java
try (FileChannel ch = FileChannel.open(logPath, READ, WRITE)) {
```

`WRITE` is only needed for `truncate(lastGood)`.

This is acceptable, but be aware:
- Some filesystems behave differently for shared locks
- This implicitly allows replay to mutate while reading

**Note:** Replay **is destructive by design** — document this explicitly.

#### ⚠️ Payload Length Trust Boundary

The code does:

```java
int len = hdr.getInt();
if (len < 0 || len > (all.length - ...)) throw
```

Good — but in replay:

```java
ByteBuffer.allocate(len);
```

If the log is corrupted with a very large `len` (but CRC later fails), memory can still be exhausted.

**Mitigation (cheap):**
- Define a **max entry size** constant
- Reject `len > MAX_LOG_ENTRY_BYTES`

Raft entries are not unbounded in sane systems.

#### ⚠️ Interface Naming Mismatch

The interface is `RaftStorage`, implementation is `FileRaftWAL`, skeleton uses `RaftWAL`.

Not a logic issue, but before others touch this:
- Standardize on **one abstraction name**
- Recommendation: use `RaftStorage` everywhere

This avoids conceptual drift ("is WAL different from storage?").

---

### 19.3 Architectural Recommendation

#### Introduce a Single Persistence Barrier Method in RaftNode

Instead of:

```java
wal.truncate(...)
wal.append(...)
wal.append(...)
wal.sync()
```

Encapsulate:

```java
wal.persist(AppendPlan plan)
```

Where:
- The storage implementation decides batching
- RaftNode cannot misuse the API
- RocksDB and File WAL stay symmetric

The design already conceptually has `AppendPlan`. Making it explicit reduces future mistakes.

---

### 19.4 Truncate-then-Append Ordering

**Concern:** If the node crashes after `TRUNCATE` is persisted but before `APPEND` completes, data may be deleted without replacement.

**Resolution:** The replay logic (Section 15.6) processes records sequentially and applies truncations as it goes. This is **correct** provided:
- The in-memory log is NOT updated until the entire batch (Truncate + all Appends) passes the `wal.sync()` barrier
- The "AppendPlan" approach in Section 16.2 enforces this correctly

---

### 19.5 CRC and Self-Framing

The record format is robust. Stopping at the first CRC mismatch or incomplete record is the standard approach for handling "torn writes" (OS crashes mid-write).

---

### 19.6 Directory Fsync (Critical on Linux)

The `meta.dat` atomic rename strategy is the "gold standard" for small metadata:

1. Write to `.tmp`
2. `fsync` the file
3. `rename` (atomic on POSIX)
4. **`fsync` the parent directory** ← Often overlooked but crucial

> **Important:** On Linux (ext4/xfs), fsyncing the directory ensures the directory entry itself (the pointer to the new inode) is durable. Without this, the rename may not survive a power loss.

The `FileRaftWAL` code in Section 15.6 has been updated to include this step.

---

### 19.7 Batch Append Optimization

**Problem:** The original design showed a loop: `f = f.compose(v3 -> wal.append(...))`. For 100 entries, this results in 100 separate `executeBlocking` calls and 100 `FileChannel.write()` calls.

**Solution:** The `RaftStorage` interface already defines `appendEntries(List<LogEntryData>)`. The `FileRaftWAL` implementation now includes `appendBatch()` which:
- Accepts the full list of entries
- Writes all records in a single `executeBlocking` call
- Performs one `sync()` at the end

This reduces context switches and improves throughput significantly.

---

### 19.8 Log Replay Efficiency

The replay logic is O(n) where n = total records ever written. This is acceptable for a "Minimal WAL."

**Operational Note:** Since snapshots are an explicit non-goal (Section 13), `raft.log` will grow indefinitely. Node restart time scales linearly with total operations since inception. Teams should monitor log file size and plan for future snapshot support if restart times become problematic.

---

### 19.9 Recommended Testing Additions

Beyond the tests in Section 10, add:

1. **Zero-Fill/Corruption Test:** Manually append random garbage bytes to a valid `raft.log` to simulate partial disk writes. Verify WAL recovers the "last good" state.

2. **Directory Fsync Verification:** On Linux, use `strace` or similar to confirm `fsync()` is called on both the file and directory during `persistTermAndVote`.

3. **Batch Performance Test:** Compare latency of 100 single appends vs. one batch append of 100 entries.

---

### 19.10 Next Steps

The only sensible next steps are:

1. Add the safety guardrails mentioned above
2. Write the crash tests
3. Ship it
4. Only *then* think about snapshots

Future extensions to consider (after shipping):
- Threat-model against power loss vs SIGKILL
- Map directly to existing `RaftNode` code line-by-line
- Design the **minimal snapshot extension** without breaking constraints

---

### 19.11 AppendPlan Implementation Details

To keep the `RaftNode` logic clean and prevent the "future foot-guns" identified above, the `AppendPlan` should be a **pure, side-effect-free** calculator. It determines exactly what needs to happen to the log before any mutation or persistence occurs.

#### The `AppendPlan` Implementation

This helper encapsulates the logic for finding the first point of conflict and preparing the data for the WAL and in-memory log.

```java
package dev.mars.quorus.controller.raft.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Calculates the delta between the current in-memory log and an incoming AppendEntries request.
 * This ensures we only persist and apply what is actually necessary.
 */
public record AppendPlan(
    Long truncateFromIndex, 
    List<RaftStorage.LogEntryData> entriesToAppend
) {
    public static AppendPlan from(long startIndex, List<LogEntryData> incomingEntries, List<LogEntryData> currentLog) {
        long truncateAt = -1;
        int incomingIdx = 0;

        // 1. Find the first point of divergence
        for (int i = 0; i < incomingEntries.size(); i++) {
            int logPos = (int) (startIndex + i);
            
            // If we have an entry at this position, check for term conflict
            if (logPos < currentLog.size()) {
                if (currentLog.get(logPos).term() != incomingEntries.get(i).term()) {
                    truncateAt = logPos;
                    incomingIdx = i;
                    break;
                }
            } else {
                // We've reached the end of our local log; everything else is an append
                incomingIdx = i;
                break;
            }
        }

        // 2. Build the list of new entries to actually write
        List<RaftStorage.LogEntryData> toAppend = incomingEntries.subList(incomingIdx, incomingEntries.size());

        return new AppendPlan(
            truncateAt != -1 ? truncateAt : null,
            toAppend
        );
    }

    /**
     * Applies the plan to the in-memory list. 
     * CALL THIS ONLY AFTER WAL PERSISTENCE IS SUCCESSFUL.
     */
    public void applyTo(List<LogEntryData> memoryLog) {
        if (truncateFromIndex != null) {
            int from = truncateFromIndex.intValue();
            if (from < memoryLog.size()) {
                memoryLog.subList(from, memoryLog.size()).clear();
            }
        }
        memoryLog.addAll(entriesToAppend);
    }
}
```

#### Integration into `RaftNode`

By using the plan, the `AppendEntries` handler follows a clear **"Prepare → Persist → Commit"** pipeline:

```java
// Inside RaftNode.handleAppendEntriesRequest
AppendPlan plan = AppendPlan.from(startIndex, request.getEntriesList(), log);

// Step 1: WAL Persistence
Future<Void> persistence = Future.succeededFuture();

if (plan.truncateFromIndex() != null) {
    persistence = persistence.compose(v -> wal.truncateFrom(plan.truncateFromIndex()));
}

if (!plan.entriesToAppend().isEmpty()) {
    persistence = persistence.compose(v -> wal.appendBatch(plan.entriesToAppend()));
}

// Step 2: Sync and Respond
persistence
    .compose(v -> wal.sync()) // The durability barrier
    .onSuccess(v -> {
        // Step 3: Mutate in-memory state only after disk is safe
        plan.applyTo(log);
        
        // Update commit index and respond success
        updateCommitIndex(request.getLeaderCommit());
        promise.complete(successResponse());
    })
    .onFailure(err -> {
        LOG.error("Failed to persist log entries", err);
        promise.fail(err);
    });
```

#### Why This Is Safer

| Benefit | Explanation |
|---------|-------------|
| **Atomicity** | If `wal.appendBatch` fails, the in-memory `log` remains untouched. The node can crash and reboot into a consistent state. |
| **Efficiency** | If the leader sends entries we already have (and they match), `entriesToAppend` will be empty, skipping redundant disk I/O. |
| **Correctness** | It strictly follows the "Persist-before-response" rule. |

This pattern eliminates the possibility of:
- Partial in-memory mutations before persistence completes
- Acknowledging entries that aren't durable
- Misuse of the raw `truncateFrom()` API

---

## Appendix A: OS Support (Windows vs Linux)

Developing on Windows for a Linux deployment is perfectly viable, provided you address a few "filesystem friction" points. You don't need to ban Windows development, but you should standardize how the WAL handles OS-specific behaviors.

### A.1 The Fsync Barrier (Directory vs. File)

As noted in Section 19.6, fsyncing a directory is critical on Linux (ext4/xfs) to ensure metadata/rename durability.

| OS | Behavior |
|----|----------|
| **Linux** | `FileChannel.open(dir, READ).force(true)` is **required** for durability |
| **Windows** | Calling `force()` on a directory `FileChannel` can throw `IOException` (Access Denied) or be a no-op depending on JVM version |

**Strategy:** Use a utility wrapper that swallows "unsupported" directory syncs on Windows but enforces them on Linux.

```java
public void syncDirectory(Path dir) throws IOException {
    if (System.getProperty("os.name").toLowerCase().contains("win")) {
        return; // Skip or handle gracefully on Windows
    }
    try (FileChannel fc = FileChannel.open(dir, StandardOpenOption.READ)) {
        fc.force(true);
    }
}
```

### A.2 Path Formatting & Drive Letters

Windows paths include drive letters (`C:\`) which create URI compatibility issues.

| OS | URI Format |
|----|------------|
| **Linux** | `file:///path/to/file` |
| **Windows** | `file:///C:/path/to/file` |

**The Risk:** If Raft nodes communicate paths to each other (e.g., for snapshots), a Windows path will break a Linux follower.

**Strategy:** Ensure all internal Raft logic uses **Unix-style relative paths** or standardized URI strings, only converting to a local `Path` object at the very last second when hitting the `FileRaftWAL`.

### A.3 Line Endings and File Encoding

Since the WAL is a **binary format**, you are safe from `CRLF` vs `LF` issues. However, if configuration files (`quorus.properties`) are edited on Windows, encoding issues may occur.

**Strategy:** 
- Enforce `UTF-8` for all file reads/writes
- Use `.gitattributes` to ensure `* text eol=lf` in your repository

### A.4 Atomic Move Semantics

`Files.move(..., ATOMIC_MOVE)` is safe on both platforms. However, Windows is much more aggressive about **File Locking**.

| OS | Behavior |
|----|----------|
| **Windows** | If any process (even a virus scanner or a stray `loadMeta()` call) has a handle open on `meta.dat`, the `REPLACE_EXISTING` move will **fail** |
| **Linux** | Linux allows you to delete or rename a file even if another process has it open (the file remains on disk until the last handle is closed) |

**Strategy:** Ensure all handles are closed before calling `Files.move()`.

### A.5 Recommended Development Approach

You don't need to force **Docker Desktop** for everyday coding, but you should use it for **Persistence Validation**.

#### Use Local Windows Development for:
- Unit testing `AppendPlan` logic
- Writing Raft state machine logic
- Standard debugging and IDE work

#### Use TestContainers (Linux Containers) for:
- **The "Kill -9" Test:** Validating that a crash during `sync()` leaves the WAL recoverable
- **The Rename Test:** Ensuring the atomic move and directory fsync work on the actual target filesystem (ext4/xfs)
- **The Performance Test:** Windows I/O performance (especially with `force(true)`) is significantly different from Linux. Do not profile your WAL on Windows.

### A.6 Summary: Windows vs. Linux WAL Behavior

| Feature | Windows Behavior | Linux Behavior | Impact |
|---------|------------------|----------------|--------|
| **Directory fsync** | Often fails/No-op | **Mandatory** for durability | Use an OS-aware wrapper |
| **File Locking** | Very Strict (Locks prevent Move/Delete) | Advisory (Can delete open files) | Close all handles before `Files.move` |
| **Atomic Move** | Supported | Supported | Safe to use |
| **Path URI** | `file:///C:/path` | `file:///path` | Handle in path conversion utilities |

---

## Appendix B: WAL Crash & Recovery Test Suite

The goal of these tests is to prove that **no matter when the process dies**, the node recovers to a state that is consistent with the rest of the Raft cluster.

### B.1 The "Torn Write" Recovery Test

**Scenario:** The node crashes while the OS is physically writing a large batch of log entries to the disk.

**Setup:**
1. Start a `FileRaftWAL` and begin a large `appendBatch`.

**Action:**
1. Simulate a crash by manually cutting the file off mid-record (e.g., write the header but skip the CRC and half the payload).

**Validation:**
1. Call `replayLog()`.

**Expectation:**
- The WAL must detect the MAGIC mismatch or CRC failure at the tail
- Truncate the file back to the last *completely* valid record
- Start normally with the prefix-safe log

#### B.1.1 Implementation

This test uses JUnit 5 and standard Java I/O to create a valid log, "tear" it, and verify that the `FileRaftWAL` gracefully recovers.

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FileRaftWALRecoveryTest {

    @TempDir
    Path tempDir;

    @Test
    void testRecoverFromTornWrite() throws Exception {
        // 1. Initialize WAL and write 2 perfectly valid entries
        FileRaftWAL wal = new FileRaftWAL(vertx, workerExecutor);
        wal.open(tempDir).toCompletionStage().toCompletableFuture().get();

        var entry1 = new RaftStorage.LogEntryData(1, 1, "First Entry".getBytes());
        var entry2 = new RaftStorage.LogEntryData(2, 1, "Second Entry".getBytes());
        
        wal.appendBatch(List.of(entry1, entry2)).toCompletionStage().toCompletableFuture().get();
        wal.sync().toCompletionStage().toCompletableFuture().get();
        wal.close();

        // 2. Simulate a "Torn Write" by manually appending a partial/corrupt record
        Path logPath = tempDir.resolve("raft.log");
        try (FileChannel fc = FileChannel.open(logPath, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            ByteBuffer partialHdr = ByteBuffer.allocate(10);
            partialHdr.putInt(0x52414654); // Valid Magic
            partialHdr.putShort((short) 1); // Valid Version
            partialHdr.put((byte) 2);       // Type Append
            // ... Stop here! No index, term, length, payload, or CRC.
            partialHdr.flip();
            fc.write(partialHdr);
        }

        // 3. Re-open the WAL and attempt replay
        FileRaftWAL recoveryWal = new FileRaftWAL(vertx, workerExecutor);
        recoveryWal.open(tempDir).toCompletionStage().toCompletableFuture().get();
        
        List<ReplayedEntry> entries = recoveryWal.replayLog()
            .toCompletionStage().toCompletableFuture().get();

        // 4. Validate results
        assertEquals(2, entries.size(), "Should have recovered only the 2 valid entries");
        assertEquals("First Entry", new String(entries.get(0).payload()));
        assertEquals("Second Entry", new String(entries.get(1).payload()));

        // 5. Verify physical truncation
        // The file size should now be exactly the size of 2 valid records
        long expectedSize = calculateExpectedSize(entry1) + calculateExpectedSize(entry2);
        assertEquals(expectedSize, Files.size(logPath), "File should be truncated to last good offset");
    }

    private long calculateExpectedSize(RaftStorage.LogEntryData entry) {
        // Header (27 bytes) + Payload + CRC (4 bytes)
        return 27 + entry.payload().length + 4;
    }
}
```

#### B.1.2 Key Recovery Mechanisms Validated

| Mechanism | Description |
|-----------|-------------|
| **Checksum Integrity** | The replay loop calculates the CRC for the 2 valid entries and compares it to the stored CRC |
| **Boundary Check** | The `if (r < hdr.capacity()) break;` logic handles the case where the file ends abruptly before a full header is read |
| **Physical Correction** | The `ch.truncate(lastGood)` call ensures that when the node starts up again, it isn't trying to append data *after* the garbage bytes, which would lead to a permanently unreadable log |

---

### B.2 The "Ghost ACK" Prevention Test (Safety Critical)

**Scenario:** Does a follower ever acknowledge an entry it didn't actually save?

**Setup:**
1. Use a Mock `RaftStorage` that fails the `sync()` call.

**Action:**
1. Send an `AppendEntriesRequest` to the `RaftNode`.

**Validation:**
- The `RaftNode` must **not** update its in-memory log
- Must **not** complete the `AppendEntriesResponse` with `success=true`

**State Machine Check:**
- `lastApplied` must not advance

This test validates the **Durability Barrier** — the core safety property of the WAL.

---

### B.3 The "State Machine Rebuild" Test

**Scenario:** After a clean restart, the State Machine must reach the same state as it had before the shutdown.

**Setup:**
1. Append 100 entries to the WAL.
2. Advance `commitIndex` to 100 and allow the `applyEntries()` loop to finish.
3. Record the "checksum" or state of the `StateMachine`.
4. Shutdown the node.

**Action:**
1. Restart the node.

**Validation:**
- `replayLog()` populates the in-memory log
- The application loop must re-run entries 1–100

**Result:**
- The new `StateMachine` state must match the state recorded in Step 3 exactly

This test proves that `lastApplied` reconstruction works correctly (Section 16.5).

---

### B.4 The "Double Vote" Prevention Test

**Scenario:** A node votes, crashes, and then tries to vote for a different candidate in the same term.

**Setup:**
1. Node receives `RequestVote` for Term 5 from `Candidate_A`.
2. Node persists `votedFor = Candidate_A` and `currentTerm = 5`.

**Action:**
1. Crash the node *after* the `wal.updateMetadata` sync but *before* sending the RPC response.
2. Restart the node.
3. Receive `RequestVote` for Term 5 from `Candidate_B`.

**Validation:**
- On restart, `loadMetadata()` must return `Candidate_A`.

**Result:**
- The node must reject `Candidate_B`'s request because it has already durably committed its vote for that term.

This test validates the **Persist-before-Grant** invariant (Section 16.2).

---

### B.5 Execution Matrix (Testing Tools)

| Test Type | Recommended Tool | Why? |
|-----------|------------------|------|
| **Logic/Unit** | JUnit 5 + Mockito | Best for `AppendPlan` and `applyEntries()` loop logic |
| **I/O Corruption** | Java `RandomAccessFile` | Manually corrupt bytes at the end of the `raft.log` file |
| **Hard Crash** | **TestContainers** | Use `docker kill --signal SIGKILL` to simulate power loss and verify `fsync` effectiveness |
| **Filesystem Stress** | `strace` (Linux) | Verify that `fsync()` and `rename()` syscalls are actually happening in the correct order |

---

### B.6 The "Applier" Guardrail

One final implementation tip: In your `applyEntries()` method, you should protect the State Machine from **duplicate applications** during the replay.

Since the State Machine is rebuilt from scratch on every restart (until you have snapshots), ensure your `stateMachine.execute()` is **idempotent** or that the `lastApplied` counter is strictly managed in memory.

**Best Practices for Idempotent State Machines:**

| Approach | Description |
|----------|-------------|
| **Natural Idempotency** | Operations like "set key X to value Y" are naturally idempotent |
| **Request ID Tracking** | Include a unique request ID in each command; the state machine rejects duplicates |
| **Sequence Numbers** | Each client maintains a sequence number; the state machine only applies if `seq > lastSeenSeq` |

**Example Guard in `applyEntries()`:**

```java
private void applyEntries() {
    while (commitIndex > lastApplied) {
        long indexToApply = lastApplied + 1;
        
        // Guard: Never apply the same entry twice
        if (indexToApply <= lastApplied) {
            throw new IllegalStateException("Attempt to re-apply entry " + indexToApply);
        }
        
        LogEntryData entry = inMemoryLog.get((int) indexToApply);
        stateMachine.execute(entry.payload())
            .onSuccess(result -> {
                lastApplied = indexToApply;  // Only advance after success
                clientRequestMap.complete(indexToApply, result);
            })
            .onFailure(err -> handleCriticalSystemError(err));
    }
}
```

---

### B.7 Test Coverage Summary

| Test | Validates | Critical For |
|------|-----------|--------------|
| **Torn Write** | CRC validation, prefix-safe truncation | Crash during append |
| **Ghost ACK** | Durability Barrier | Raft safety |
| **State Machine Rebuild** | `lastApplied` reconstruction | Recovery correctness |
| **Double Vote** | Persist-before-Grant | Election safety |

These tests, combined with the acceptance criteria in Section 18, provide comprehensive coverage of the WAL's correctness properties.
---

## Appendix C: Configuration Integration

This section describes how to connect the `FileRaftWAL` implementation with the externalized `AppConfig` system, ensuring storage paths and durability settings are configurable without code changes.

### C.1 AppConfig Extensions for Storage

Add these methods to `dev.mars.quorus.controller.config.AppConfig`:

```java
/**
 * Returns the base directory for Raft WAL storage.
 * Default: "data/raft" (relative to working directory)
 */
public String getDataDir() {
    return getString("quorus.raft.storage.path", "data/raft");
}

/**
 * Returns whether to fsync on every write operation.
 * Default: true (required for production durability)
 * 
 * WARNING: Setting to false disables durability guarantees.
 * Only use for high-throughput non-production testing.
 */
public boolean isSyncOnWrite() {
    return getBoolean("quorus.raft.storage.fsync", true);
}
```

### C.2 Properties File Configuration

Add these entries to `quorus-controller.properties`:

```properties
# =============================================================================
# Raft Storage Configuration
# =============================================================================

# Base directory for Raft WAL and metadata files
# On Linux servers, typically: /var/lib/quorus/data
# On Windows development: data/raft (relative to working directory)
quorus.raft.storage.path=/var/lib/quorus/data

# Enable fsync after every write (required for durability)
# WARNING: Only disable for testing - breaks Raft safety guarantees!
quorus.raft.storage.fsync=true
```

### C.3 FileRaftWAL Integration

Update `FileRaftWAL` to consume configuration from `AppConfig`:

```java
public final class FileRaftWAL implements RaftStorage {
    
    private static final Logger LOG = LoggerFactory.getLogger(FileRaftWAL.class);
    
    private final Vertx vertx;
    private final WorkerExecutor walExecutor;
    private final AppConfig config;
    private final Path dataPath;
    private final boolean syncEnabled;
    
    private FileChannel logCh;
    private FileChannel metaCh;

    public FileRaftWAL(Vertx vertx, WorkerExecutor walExecutor) {
        this.vertx = vertx;
        this.walExecutor = walExecutor;
        this.config = AppConfig.get();
        
        // Resolve path from externalized config
        this.dataPath = Paths.get(config.getDataDir());
        this.syncEnabled = config.isSyncOnWrite();
        
        LOG.info("FileRaftWAL configured: dataPath={}, syncEnabled={}", 
                 dataPath.toAbsolutePath(), syncEnabled);
        
        if (!syncEnabled) {
            LOG.warn("⚠️ FSYNC DISABLED - Durability guarantees are OFF. " +
                     "Do NOT use in production!");
        }
    }

    @Override
    public Future<Void> open(Path ignored) {
        // We use the path from externalized config, not the passed parameter
        return vertx.executeBlocking(p -> {
            try {
                Files.createDirectories(dataPath);
                
                this.logCh = FileChannel.open(
                    dataPath.resolve("raft.log"), 
                    StandardOpenOption.CREATE, 
                    StandardOpenOption.READ, 
                    StandardOpenOption.WRITE
                );
                logCh.position(logCh.size());
                
                LOG.info("WAL opened: {} (size={})", 
                         dataPath.resolve("raft.log"), logCh.size());
                p.complete();
            } catch (Exception e) {
                LOG.error("Failed to open WAL at {}", dataPath, e);
                p.fail(e);
            }
        }, false, walExecutor);
    }
    
    @Override
    public Future<Void> sync() {
        if (!syncEnabled) {
            return Future.succeededFuture(); // Skip fsync in test mode
        }
        return vertx.executeBlocking(p -> {
            try {
                logCh.force(true);  // true = sync metadata too
                syncDirectory(dataPath);
                p.complete();
            } catch (IOException e) {
                p.fail(e);
            }
        }, false, walExecutor);
    }
    
    // ... other methods unchanged ...
}
```

### C.4 Environment Variable Override

For containerized deployments, override via environment variables:

```bash
# Docker / Kubernetes deployment
export QUORUS_RAFT_STORAGE_PATH=/mnt/raft-data
export QUORUS_RAFT_STORAGE_FSYNC=true
```

The `AppConfig` class automatically maps environment variables (with `_` replacing `.`) to properties.

### C.5 Linux Deployment Checklist

| Requirement | Action | Verification |
|-------------|--------|--------------|
| **User Permissions** | Ensure JVM user has `rwx` on `quorus.raft.storage.path` | `ls -la /var/lib/quorus/data` |
| **Disk Space** | Monitor `raft.log` size (grows unbounded until snapshots) | `du -sh /var/lib/quorus/data/*` |
| **Mount Type** | Use local SSD, not network storage, for `fsync` performance | `mount | grep quorus` |
| **Filesystem** | Use ext4 or xfs with `data=ordered` (default) | `cat /etc/fstab` |

### C.6 Development vs Production Configuration

| Setting | Development (Windows) | Production (Linux) |
|---------|----------------------|-------------------|
| `quorus.raft.storage.path` | `data/raft` | `/var/lib/quorus/data` |
| `quorus.raft.storage.fsync` | `true` (or `false` for speed) | `true` (mandatory) |
| Directory fsync | Skipped (OS limitation) | Enforced |
| Performance testing | Not representative | Use for benchmarks |

### C.7 Configuration Validation on Startup

Add validation in the controller's startup sequence:

```java
public class QuorusControllerVerticle extends AbstractVerticle {
    
    @Override
    public void start(Promise<Void> startPromise) {
        AppConfig config = AppConfig.get();
        
        // Validate storage configuration
        Path dataPath = Paths.get(config.getDataDir());
        
        if (!Files.exists(dataPath)) {
            try {
                Files.createDirectories(dataPath);
                LOG.info("Created Raft data directory: {}", dataPath.toAbsolutePath());
            } catch (IOException e) {
                startPromise.fail("Cannot create data directory: " + dataPath);
                return;
            }
        }
        
        if (!Files.isWritable(dataPath)) {
            startPromise.fail("Data directory not writable: " + dataPath);
            return;
        }
        
        if (!config.isSyncOnWrite()) {
            LOG.warn("⚠️ Running with fsync DISABLED - NOT SAFE FOR PRODUCTION");
        }
        
        // Continue with WAL initialization...
    }
}
```

---

## Summary: Complete WAL Integration

The WAL design is now fully integrated with the Quorus configuration system:

| Component | Status |
|-----------|--------|
| **Externalized Paths** | ✅ Via `quorus.raft.storage.path` property |
| **Configurable Durability** | ✅ Via `quorus.raft.storage.fsync` property |
| **Environment Override** | ✅ Via `QUORUS_RAFT_STORAGE_*` env vars |
| **Windows Development** | ✅ Relative paths, graceful directory fsync skip |
| **Linux Production** | ✅ Absolute paths, enforced fsync |
| **Startup Validation** | ✅ Directory creation and permission checks |

This completes the WAL design from theory through implementation to deployment configuration.
---

## Appendix D: Linux Deployment & Bootstrap

This section provides production-ready scripts and configurations for deploying the Quorus Controller on Linux servers and in containerized environments.

### D.1 Bootstrap Shell Script (`setup-quorus.sh`)

This script prepares a Linux host for the Controller. It ensures the data directory exists and is writable by the `quorus` service user.

```bash
#!/bin/bash
# =============================================================================
# Quorus Node Bootstrap Script
# =============================================================================
# This script prepares a Linux host for running a Quorus Controller node.
# It creates the required directory structure and sets appropriate permissions.
#
# Usage: sudo ./setup-quorus.sh
# =============================================================================

set -e  # Exit on any error

# 1. Load config or use defaults
DATA_DIR=${QUORUS_RAFT_STORAGE_PATH:-"/var/lib/quorus/data"}
LOG_DIR=${QUORUS_LOG_PATH:-"/var/log/quorus"}
QUORUS_USER=${QUORUS_USER:-"quorus"}
QUORUS_GROUP=${QUORUS_GROUP:-"quorus"}

echo "================================================"
echo "Quorus Node Bootstrap"
echo "================================================"
echo "Data Directory: $DATA_DIR"
echo "Log Directory:  $LOG_DIR"
echo "Service User:   $QUORUS_USER"
echo ""

# 2. Create the quorus user if it doesn't exist
if ! id "$QUORUS_USER" &>/dev/null; then
    echo "Creating user $QUORUS_USER..."
    sudo useradd -r -s /bin/false -d /var/lib/quorus "$QUORUS_USER"
    echo "User created."
fi

# 3. Create the data directory
if [ ! -d "$DATA_DIR" ]; then
    echo "Creating data directory $DATA_DIR..."
    sudo mkdir -p "$DATA_DIR"
    echo "Created."
fi

# 4. Create the log directory
if [ ! -d "$LOG_DIR" ]; then
    echo "Creating log directory $LOG_DIR..."
    sudo mkdir -p "$LOG_DIR"
    echo "Created."
fi

# 5. Set ownership and permissions
echo "Setting permissions..."
sudo chown -R $QUORUS_USER:$QUORUS_GROUP "$DATA_DIR"
sudo chown -R $QUORUS_USER:$QUORUS_GROUP "$LOG_DIR"
sudo chmod -R 750 "$DATA_DIR"
sudo chmod -R 750 "$LOG_DIR"

# 6. Verify filesystem type (warn if not ext4/xfs)
FS_TYPE=$(df -T "$DATA_DIR" | tail -1 | awk '{print $2}')
echo ""
echo "Filesystem type for $DATA_DIR: $FS_TYPE"
if [[ "$FS_TYPE" != "ext4" && "$FS_TYPE" != "xfs" ]]; then
    echo "⚠️  WARNING: Recommended filesystems are ext4 or xfs for Raft durability."
fi

echo ""
echo "================================================"
echo "Storage bootstrap complete."
echo "================================================"
```

### D.2 Production Dockerfile

For containerized deployments, this Dockerfile bakes configuration defaults into the image while allowing overrides via environment variables.

```dockerfile
# =============================================================================
# Quorus Controller Production Dockerfile
# =============================================================================
# Build: docker build -t quorus-controller:latest -f Dockerfile .
# Run:   docker run -d -p 8080:8080 -v /data/quorus:/var/lib/quorus/data quorus-controller:latest
# =============================================================================

# Use Eclipse Temurin JRE 21 on Ubuntu Jammy
FROM eclipse-temurin:21-jre-jammy

LABEL maintainer="Quorus Team"
LABEL description="Quorus Controller - Distributed Job Orchestration"

# Create a non-privileged user for security
RUN useradd -r -s /bin/false -d /var/lib/quorus -m quorus

# Define storage and configuration paths
ENV QUORUS_RAFT_STORAGE_PATH=/var/lib/quorus/data
ENV QUORUS_RAFT_STORAGE_FSYNC=true
ENV QUORUS_LOG_LEVEL=INFO

# Create directory structure with correct ownership
RUN mkdir -p ${QUORUS_RAFT_STORAGE_PATH} \
    && mkdir -p /var/log/quorus \
    && chown -R quorus:quorus /var/lib/quorus \
    && chown -R quorus:quorus /var/log/quorus

# Switch to non-root user
USER quorus
WORKDIR /app

# Copy the fat jar from build context
# In multi-stage build, this would be: COPY --from=builder ...
COPY --chown=quorus:quorus quorus-controller/target/quorus-controller-*-fat.jar app.jar

# Expose ports
# 8080 - HTTP API
# 9080 - Raft RPC (gRPC)
EXPOSE 8080 9080

# Declare the data volume for persistence
VOLUME ["/var/lib/quorus/data"]

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### D.3 Docker Compose for Windows Development

For Windows developers using Docker Desktop, this compose file provides a Linux-like environment:

```yaml
# =============================================================================
# Quorus Development Cluster (Windows/Docker Desktop)
# =============================================================================
# Usage: docker-compose -f docker-compose-dev.yaml up -d
# =============================================================================

version: '3.8'

services:
  raft-node-1:
    image: quorus-controller:latest
    container_name: quorus-node-1
    hostname: node-1
    environment:
      - QUORUS_RAFT_STORAGE_PATH=/data
      - QUORUS_CONTROLLER_NODE_ID=node-1
      - QUORUS_CONTROLLER_CLUSTER_PEERS=node-2:9080,node-3:9080
      - QUORUS_RAFT_STORAGE_FSYNC=true
    volumes:
      # Map Windows folder to Linux container path
      # Docker Desktop handles filesystem translation
      - ./dev-data/node1:/data
    ports:
      - "8081:8080"   # HTTP API
      - "9081:9080"   # Raft RPC
    networks:
      - quorus-net

  raft-node-2:
    image: quorus-controller:latest
    container_name: quorus-node-2
    hostname: node-2
    environment:
      - QUORUS_RAFT_STORAGE_PATH=/data
      - QUORUS_CONTROLLER_NODE_ID=node-2
      - QUORUS_CONTROLLER_CLUSTER_PEERS=node-1:9080,node-3:9080
      - QUORUS_RAFT_STORAGE_FSYNC=true
    volumes:
      - ./dev-data/node2:/data
    ports:
      - "8082:8080"
      - "9082:9080"
    networks:
      - quorus-net

  raft-node-3:
    image: quorus-controller:latest
    container_name: quorus-node-3
    hostname: node-3
    environment:
      - QUORUS_RAFT_STORAGE_PATH=/data
      - QUORUS_CONTROLLER_NODE_ID=node-3
      - QUORUS_CONTROLLER_CLUSTER_PEERS=node-1:9080,node-2:9080
      - QUORUS_RAFT_STORAGE_FSYNC=true
    volumes:
      - ./dev-data/node3:/data
    ports:
      - "8083:8080"
      - "9083:9080"
    networks:
      - quorus-net

networks:
  quorus-net:
    driver: bridge
```

### D.4 Systemd Service Unit (Native Linux)

For bare-metal or VM deployments without containers:

```ini
# /etc/systemd/system/quorus-controller.service
[Unit]
Description=Quorus Controller Node
Documentation=https://github.com/quorus/quorus
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=quorus
Group=quorus
WorkingDirectory=/opt/quorus

# Environment file for configuration
EnvironmentFile=-/etc/quorus/quorus-controller.env

# JVM and application startup
ExecStart=/usr/bin/java \
    -XX:+UseG1GC \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+ExitOnOutOfMemoryError \
    -Dquorus.config.file=/etc/quorus/quorus-controller.properties \
    -jar /opt/quorus/quorus-controller.jar

# Graceful shutdown
ExecStop=/bin/kill -SIGTERM $MAINPID
TimeoutStopSec=30

# Restart policy
Restart=on-failure
RestartSec=10

# Security hardening
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/quorus /var/log/quorus
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

### D.5 Final Deployment Checklist

| Category | Item | Verification Command |
|----------|------|---------------------|
| **Filesystem** | Data partition is ext4 or xfs | `df -T /var/lib/quorus` |
| **Mount Options** | Barriers enabled (default) | `mount | grep quorus` |
| **Permissions** | quorus user owns data dir | `ls -la /var/lib/quorus` |
| **Disk Space** | Adequate space for WAL growth | `df -h /var/lib/quorus` |
| **Network** | Raft ports open between nodes | `nc -zv node-2 9080` |
| **Firewall** | Ports 8080, 9080 allowed | `iptables -L -n` |
| **DNS/Hosts** | Peer hostnames resolvable | `getent hosts node-2` |
| **JVM** | Java 21+ installed | `java -version` |

### D.6 Path Handling Safety

Ensure the `extractPath(URI uri)` method in your code handles cross-platform path differences:

```java
/**
 * Safely extracts a filesystem path from a URI.
 * Handles Windows drive letters and ensures Linux compatibility.
 */
public static Path extractPath(URI uri) {
    if (uri == null) {
        throw new IllegalArgumentException("URI cannot be null");
    }
    
    String scheme = uri.getScheme();
    
    // Handle file:// URIs
    if ("file".equals(scheme)) {
        // Paths.get(URI) handles platform differences
        return Paths.get(uri);
    }
    
    // Handle plain paths (no scheme)
    if (scheme == null) {
        return Paths.get(uri.getPath());
    }
    
    // Reject Windows-style paths on Linux
    if (uri.getPath() != null && uri.getPath().matches("^/[A-Za-z]:.*")) {
        throw new IllegalArgumentException(
            "Windows-style path detected on non-Windows system: " + uri);
    }
    
    throw new IllegalArgumentException("Unsupported URI scheme: " + scheme);
}
```

---

## Summary: Complete WAL Implementation Roadmap

The WAL design is now architecturally complete for an Alpha release:

| Aspect | Status | Details |
|--------|--------|---------|
| **Core WAL** | ✅ Complete | Sections 1-15: Binary format, CRC32C, fsync barriers |
| **RaftNode Wiring** | ✅ Complete | Section 16: AppendPlan, Durability Barrier, Application Loop |
| **Acceptance Criteria** | ✅ Defined | Section 18: 7 validation checkpoints |
| **Design Review** | ✅ Documented | Section 19: Feedback and recommendations |
| **OS Compatibility** | ✅ Addressed | Appendix A: Windows vs Linux differences |
| **Test Suite** | ✅ Designed | Appendix B: Crash & Recovery tests |
| **Configuration** | ✅ Integrated | Appendix C: AppConfig, properties, env vars |
| **Deployment** | ✅ Documented | Appendix D: Scripts, Docker, systemd |

---

## Quorus Persistence & Reliability Roadmap

This roadmap serves as the master blueprint for the Quorus Persistence layer. It bridges the gap between the low-level WAL design, the consensus logic, and the final deployment strategy.

### Phase 1: The Durability Core (Week 1)

**Goal:** Establish the "Persist-before-response" framework.

| Task | Description |
|------|-------------|
| **1.1 Standardize Storage Interface** | Implement the `RaftStorage` interface and the `AppendPlan` calculator to ensure all mutations are planned before they are executed |
| **1.2 Implement FileRaftWAL** | Build the self-framing binary record logic with **CRC32C**. Implement the `meta.dat` atomic rename strategy (Temp → Sync → Move → Sync Dir) |
| **1.3 Windows/Linux Parity** | Add the `syncDirectory` utility to handle OS-specific filesystem differences |
| **1.4 The "Torn Write" Test** | Write a unit test that manually corrupts the end of a log file and verifies the WAL recovers up to the last valid byte |

**Deliverables:**
- [ ] `RaftStorage.java` interface
- [ ] `AppendPlan.java` calculator
- [ ] `FileRaftWAL.java` implementation
- [ ] `FileRaftWALRecoveryTest.java` torn write test

---

### Phase 2: Raft Integration (Week 1–2)

**Goal:** Wire the WAL into the RPC handlers and state machine.

| Task | Description |
|------|-------------|
| **2.1 RequestVote Integration** | Ensure the `votedFor` state is durable before a "VoteGranted" response is sent |
| **2.2 AppendEntries Pipeline** | Implement the `AppendPlan` logic to handle log divergence. Inject the `wal.sync()` barrier into the `AppendEntries` flow |
| **2.3 The Applier Loop** | Create the reactive trigger that moves `lastApplied` up to `commitIndex`. Ensure the state machine can rebuild its state from index 0 on startup |

**Deliverables:**
- [ ] Updated `RaftNode.handleRequestVote()` with persist-before-grant
- [ ] Updated `RaftNode.handleAppendEntries()` with AppendPlan + sync barrier
- [ ] `applyEntries()` application loop
- [ ] State machine replay on startup

---

### Phase 3: Configuration & Environment (Week 2)

**Goal:** Prepare the system for deployment.

| Task | Description |
|------|-------------|
| **3.1 Externalize Settings** | Fully integrate `AppConfig` to allow tuning `quorus.raft.storage.path` and `fsync` via `.properties` or environment variables |
| **3.2 Path Normalization** | Verify `extractPath(URI uri)` handles Windows-style `file:///C:/` URIs without breaking when deployed on Linux |
| **3.3 Bootstrap Scripting** | Finalize the Dockerfile and `setup-quorus.sh` to handle directory creation and permissions in production |

**Deliverables:**
- [ ] `AppConfig` extensions for WAL settings
- [ ] `quorus-controller.properties` with storage configuration
- [ ] `extractPath()` with cross-platform unit tests
- [ ] Production Dockerfile
- [ ] `setup-quorus.sh` bootstrap script

---

### Phase 4: Validation & Stress (Week 2+)

**Goal:** Prove the system is "production-grade."

| Task | Description |
|------|-------------|
| **4.1 TestContainers Suite** | Run a 3-node Raft cluster in Docker and use `SIGKILL` to verify that no committed data is lost across restarts |
| **4.2 Performance Profiling** | Measure the latency impact of the `fsync` barrier |
| **4.3 Documentation** | Update the WAL design document to reflect the final "Applied" logic and configuration keys |

**Deliverables:**
- [ ] `RaftClusterIntegrationTest.java` with TestContainers
- [ ] Performance benchmark results
- [ ] Final documentation updates

---

### Success Criteria

| Criterion | Validation Method |
|-----------|-------------------|
| **Safety** | A majority crash does not result in log divergence |
| **Recovery** | A node restarts and catches up to the Leader automatically |
| **Portability** | A developer can clone the repo on Windows, run the tests, and push to a Linux production server with zero code changes |

---

### Implementation Tracking

```
Week 1:
├── Phase 1.1: RaftStorage interface ........................ [ ]
├── Phase 1.2: FileRaftWAL implementation ................... [ ]
├── Phase 1.3: syncDirectory utility ........................ [ ]
├── Phase 1.4: Torn Write test .............................. [ ]
├── Phase 2.1: RequestVote persist-before-grant ............. [ ]
└── Phase 2.2: AppendEntries pipeline ....................... [ ]

Week 2:
├── Phase 2.3: Applier loop ................................. [ ]
├── Phase 3.1: AppConfig integration ........................ [ ]
├── Phase 3.2: Path normalization ........................... [ ]
├── Phase 3.3: Bootstrap scripts ............................ [ ]
└── Phase 4.1: TestContainers suite ......................... [ ]

Week 2+:
├── Phase 4.2: Performance profiling ........................ [ ]
└── Phase 4.3: Documentation finalization ................... [ ]
```
---

## Appendix E: RaftNode Integration Skeleton

This skeleton provides the high-level wiring for `RaftNode`. It utilizes the `AppendPlan`, the `RaftStorage` (WAL), and the **State Machine Applier** logic to enforce the "Persist-before-response" rule.

### E.1 Complete RaftNode Skeleton

```java
package dev.mars.quorus.controller.raft;

import dev.mars.quorus.controller.raft.storage.RaftStorage;
import dev.mars.quorus.controller.raft.storage.RaftStorage.LogEntryData;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * RaftNode with WAL Integration.
 * 
 * This class implements the "Persist-before-response" rule for all
 * state-changing Raft operations.
 * 
 * Key Invariants:
 * - Vote is never granted until metadata is durable
 * - AppendEntries is never ACK'd until log entries are durable
 * - In-memory state is only mutated AFTER durability is confirmed
 */
public class RaftNode {
    
    private static final Logger LOG = LoggerFactory.getLogger(RaftNode.class);
    
    private final RaftStorage wal;
    private final StateMachine stateMachine;
    private final List<LogEntryData> log = new ArrayList<>();
    
    // Volatile state (rebuilt on restart)
    private long commitIndex = 0;
    private long lastApplied = 0;
    
    // Persistent state (mirrored in memory for speed, recovered from WAL on startup)
    private long currentTerm = 0;
    private String votedFor = null;

    public RaftNode(RaftStorage wal, StateMachine stateMachine) {
        this.wal = wal;
        this.stateMachine = stateMachine;
    }

    // =========================================================================
    // Section 16.1: AppendEntries Pipeline (Follower Side)
    // =========================================================================

    /**
     * Handles AppendEntries RPC from the Leader.
     * 
     * Follows the Prepare → Persist → Apply sequence:
     * 1. Consistency check (prevLogIndex/prevLogTerm)
     * 2. Build AppendPlan (no mutations yet)
     * 3. Persist to WAL with sync barrier
     * 4. Apply to in-memory log
     * 5. Update commitIndex and trigger applier
     */
    public void handleAppendEntries(AppendEntriesRequest request, Promise<AppendEntriesResponse> promise) {
        LOG.debug("AppendEntries from Leader {}: prevLogIndex={}, entries={}",
                  request.getLeaderId(), request.getPrevLogIndex(), request.getEntriesCount());

        // Step 1: Term check
        if (request.getTerm() < currentTerm) {
            LOG.debug("Rejecting AppendEntries: stale term {} < {}", request.getTerm(), currentTerm);
            promise.complete(failResponse("Stale term"));
            return;
        }

        // Update term if needed
        if (request.getTerm() > currentTerm) {
            stepDown(request.getTerm());
        }

        // Step 2: Consistency Check (Standard Raft)
        if (!isLogConsistent(request)) {
            LOG.debug("Rejecting AppendEntries: log inconsistent at prevLogIndex={}",
                      request.getPrevLogIndex());
            promise.complete(failResponse("Log inconsistent"));
            return;
        }

        // Step 3: Prepare the plan (Section 19.11 - AppendPlan)
        long startIndex = request.getPrevLogIndex() + 1;
        AppendPlan plan = AppendPlan.from(startIndex, request.getEntriesList(), log);
        
        LOG.debug("AppendPlan: truncateFrom={}, entriesToAppend={}",
                  plan.truncateFromIndex(), plan.entriesToAppend().size());

        // Step 4: Persist-before-response (The Durability Barrier)
        persistPlan(plan)
            .compose(v -> wal.sync())  // <-- DURABILITY BARRIER
            .onSuccess(v -> {
                // Step 5: Mutate in-memory log (ONLY after durability confirmed)
                plan.applyTo(log);
                
                // Step 6: Update commit index based on leader's commit
                updateCommitIndex(request.getLeaderCommit());
                
                LOG.debug("AppendEntries success: logSize={}, commitIndex={}",
                          log.size(), commitIndex);
                
                promise.complete(successResponse());
            })
            .onFailure(err -> {
                LOG.error("AppendEntries failed during WAL persist", err);
                promise.complete(failResponse("WAL persist failed"));
            });
    }

    // =========================================================================
    // Section 16.2: RequestVote Pipeline
    // =========================================================================

    /**
     * Handles RequestVote RPC from a Candidate.
     * 
     * Implements Persist-before-Grant:
     * - Vote is only granted AFTER metadata is durable
     * - Prevents double-voting after crash/restart
     */
    public void handleVoteRequest(VoteRequest request, Promise<VoteResponse> promise) {
        LOG.debug("VoteRequest from {}: term={}", request.getCandidateId(), request.getTerm());

        // Step 1: Term check
        if (request.getTerm() < currentTerm) {
            LOG.debug("Rejecting vote: stale term {} < {}", request.getTerm(), currentTerm);
            promise.complete(voteRejectedResponse());
            return;
        }

        // Update term if needed
        if (request.getTerm() > currentTerm) {
            stepDown(request.getTerm());
        }

        // Step 2: Check if we can grant vote
        if (shouldGrantVote(request)) {
            // Step 3: Persist metadata BEFORE granting vote
            wal.updateMetadata(request.getTerm(), Optional.of(request.getCandidateId()))
                .onSuccess(v -> {
                    // Step 4: Update in-memory state AFTER durability
                    this.currentTerm = request.getTerm();
                    this.votedFor = request.getCandidateId();
                    
                    LOG.info("Vote granted to {} for term {}", request.getCandidateId(), request.getTerm());
                    promise.complete(voteGrantedResponse());
                })
                .onFailure(err -> {
                    LOG.error("Failed to persist vote metadata", err);
                    promise.fail(err);
                });
        } else {
            LOG.debug("Vote rejected: already voted for {} in term {}", votedFor, currentTerm);
            promise.complete(voteRejectedResponse());
        }
    }

    // =========================================================================
    // Section 16.4: The State Machine Application Loop
    // =========================================================================

    /**
     * Triggers the state machine applier.
     * 
     * Invariant: lastApplied <= commitIndex <= lastLogIndex
     * 
     * This loop catches up the state machine to the committed index.
     * Called after:
     * - Leader advances commitIndex (majority ACKs)
     * - Follower receives leaderCommit in AppendEntries
     */
    private void triggerApplier() {
        // Run application to ensure order
        while (commitIndex > lastApplied) {
            long indexToApply = lastApplied + 1;
            
            if (indexToApply > log.size() - 1) {
                LOG.warn("Cannot apply index {}: log only has {} entries", indexToApply, log.size());
                break;
            }
            
            LogEntryData entry = log.get((int) indexToApply);
            LOG.debug("Applying entry at index {}: term={}", indexToApply, entry.term());

            stateMachine.execute(entry.payload())
                .onSuccess(result -> {
                    lastApplied = indexToApply;
                    LOG.debug("Applied entry {}: lastApplied={}", indexToApply, lastApplied);
                    
                    // If we are the Leader, notify waiting client
                    notifyClientIfLeader(indexToApply, result);
                })
                .onFailure(err -> {
                    LOG.error("CRITICAL: State machine execution failed at index {}", indexToApply, err);
                    handleCriticalSystemError(err);
                });
        }
    }

    // =========================================================================
    // Section 16.5: Startup and Recovery
    // =========================================================================

    /**
     * Recovers node state from WAL on startup.
     * 
     * Order:
     * 1. Load metadata (currentTerm, votedFor)
     * 2. Replay log entries into memory
     * 3. Rebuild state machine by re-applying all entries
     */
    public Future<Void> recover() {
        LOG.info("Starting WAL recovery...");
        
        return wal.loadMetadata()
            .compose(meta -> {
                this.currentTerm = meta.currentTerm();
                this.votedFor = meta.votedFor().orElse(null);
                LOG.info("Recovered metadata: term={}, votedFor={}", currentTerm, votedFor);
                
                return wal.replayLog();
            })
            .compose(entries -> {
                log.clear();
                log.addAll(entries);
                LOG.info("Recovered {} log entries", entries.size());
                
                // Rebuild state machine from log
                return rebuildStateMachine();
            })
            .onSuccess(v -> LOG.info("Recovery complete: logSize={}, lastApplied={}",
                                      log.size(), lastApplied))
            .onFailure(err -> LOG.error("Recovery failed", err));
    }

    /**
     * Rebuilds state machine by replaying all committed entries.
     * 
     * Note: This is why state machine operations should be idempotent.
     */
    private Future<Void> rebuildStateMachine() {
        LOG.info("Rebuilding state machine from {} entries...", log.size());
        
        // Reset state machine to blank state
        stateMachine.reset();
        lastApplied = 0;
        
        // Re-apply all entries up to the last known commit
        // On fresh start, commitIndex may be 0, so we apply everything we have
        commitIndex = log.size() > 0 ? log.size() - 1 : 0;
        
        triggerApplier();
        return Future.succeededFuture();
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private Future<Void> persistPlan(AppendPlan plan) {
        Future<Void> f = Future.succeededFuture();
        
        if (plan.truncateFromIndex() != null) {
            f = f.compose(v -> wal.truncateSuffix(plan.truncateFromIndex()));
        }
        
        if (!plan.entriesToAppend().isEmpty()) {
            f = f.compose(v -> wal.appendEntries(plan.entriesToAppend()));
        }
        
        return f;
    }

    private void updateCommitIndex(long leaderCommit) {
        if (leaderCommit > commitIndex) {
            long newCommitIndex = Math.min(leaderCommit, log.size() - 1);
            if (newCommitIndex > commitIndex) {
                commitIndex = newCommitIndex;
                triggerApplier();
            }
        }
    }

    private void stepDown(long newTerm) {
        LOG.info("Stepping down: term {} -> {}", currentTerm, newTerm);
        currentTerm = newTerm;
        votedFor = null;
        // Cancel any ongoing election timers, etc.
    }

    private boolean isLogConsistent(AppendEntriesRequest request) {
        long prevLogIndex = request.getPrevLogIndex();
        long prevLogTerm = request.getPrevLogTerm();
        
        if (prevLogIndex == -1) {
            return true;  // Empty log case
        }
        
        if (prevLogIndex >= log.size()) {
            return false;  // We don't have this entry
        }
        
        return log.get((int) prevLogIndex).term() == prevLogTerm;
    }

    private boolean shouldGrantVote(VoteRequest request) {
        // Check if we haven't voted or already voted for this candidate
        if (votedFor != null && !votedFor.equals(request.getCandidateId())) {
            return false;
        }
        
        // Check if candidate's log is at least as up-to-date as ours
        return isLogAtLeastAsUpToDate(request.getLastLogIndex(), request.getLastLogTerm());
    }

    private boolean isLogAtLeastAsUpToDate(long candidateLastIndex, long candidateLastTerm) {
        if (log.isEmpty()) {
            return true;
        }
        
        long ourLastTerm = log.get(log.size() - 1).term();
        long ourLastIndex = log.size() - 1;
        
        if (candidateLastTerm != ourLastTerm) {
            return candidateLastTerm > ourLastTerm;
        }
        
        return candidateLastIndex >= ourLastIndex;
    }

    // Response builders (placeholders)
    private AppendEntriesResponse successResponse() { /* ... */ return null; }
    private AppendEntriesResponse failResponse(String reason) { /* ... */ return null; }
    private VoteResponse voteGrantedResponse() { /* ... */ return null; }
    private VoteResponse voteRejectedResponse() { /* ... */ return null; }
    private void notifyClientIfLeader(long index, Object result) { /* ... */ }
    private void handleCriticalSystemError(Throwable err) { /* ... */ }
}
```

### E.2 Key Design Notes

| Aspect | Implementation Detail |
|--------|----------------------|
| **Serialization of WAL Ops** | The `wal` methods return `Future<Void>`. These must be executed on a dedicated `walExecutor` (pool size 1) to ensure disk writes don't overlap or reorder |
| **The Success Handler** | `plan.applyTo(log)` only happens *inside* the `.onSuccess(...)` of the WAL sync. If the disk is full or the file is locked, the in-memory log remains in its original state |
| **The Applier Loop** | The `while` loop is safe because `lastApplied` only advances on successful state machine execution |
| **Recovery Order** | Metadata → Log Replay → State Machine Rebuild ensures correct startup sequence |

### E.3 Thread Safety Considerations

```java
/**
 * Thread Safety Model:
 * 
 * 1. All RPC handlers (handleAppendEntries, handleVoteRequest) run on Vert.x event loop
 * 2. WAL operations are dispatched to walExecutor (single-threaded)
 * 3. State machine operations run on the event loop (via Future callbacks)
 * 4. In-memory state (log, commitIndex, lastApplied) is only mutated on the event loop
 * 
 * This means:
 * - No explicit locking needed for in-memory state
 * - WAL operations are naturally serialized
 * - Callbacks from WAL return to event loop context
 */
```

### E.4 Error Handling Strategy

| Error Type | Handling |
|------------|----------|
| **WAL Write Failure** | Reject RPC, do not mutate in-memory state |
| **WAL Sync Failure** | Reject RPC, do not mutate in-memory state |
| **State Machine Failure** | Call `handleCriticalSystemError()` - node may need to restart |
| **Metadata Load Failure** | Node cannot start - fail startup |
| **Log Replay Failure** | Node cannot start - may need manual intervention |

### E.5 Integration Checklist

```
RaftNode Integration:
├── [ ] Constructor takes RaftStorage and StateMachine
├── [ ] handleAppendEntries() follows Prepare → Persist → Apply
├── [ ] handleVoteRequest() implements Persist-before-Grant
├── [ ] triggerApplier() respects lastApplied <= commitIndex
├── [ ] recover() loads metadata before log replay
├── [ ] rebuildStateMachine() re-applies all entries
├── [ ] stepDown() clears votedFor on term change
├── [ ] isLogConsistent() validates prevLogIndex/prevLogTerm
├── [ ] shouldGrantVote() checks votedFor and log up-to-date
└── [ ] Error handlers don't mutate state on failure
```

---

## Appendix F: Pluggable Storage Architecture

This appendix defines the **Storage Provider Interface (SPI)** pattern that allows the Raft implementation to use either the custom `FileRaftStorage` (our minimal WAL) or a high-performance key-value store like **RocksDB** as a drop-in replacement.

### F.1 Design Goals

| Goal | Rationale |
|------|-----------|
| **Backend Agnostic** | `RaftNode` depends only on the interface, never on concrete implementations |
| **Zero Code Changes** | Switching backends requires only configuration changes |
| **Raft-Correct** | All implementations must honor the "Persist-before-response" contract |
| **Testable** | Easy to inject mock implementations for unit testing |

### F.2 The RaftStorage Interface (SPI)

This is the **single contract** that all storage backends must implement:

```java
package dev.mars.quorus.controller.raft.storage;

import io.vertx.core.Future;
import java.io.Closeable;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Storage Provider Interface (SPI) for Raft persistence.
 * 
 * <p>All implementations MUST guarantee:</p>
 * <ul>
 *   <li>Durability: Data survives process crash after {@link #sync()} returns</li>
 *   <li>Atomicity: Metadata updates are all-or-nothing</li>
 *   <li>Ordering: Log entries maintain strict index ordering</li>
 * </ul>
 * 
 * <p>Implementations:</p>
 * <ul>
 *   <li>{@code FileRaftStorage} - Custom WAL (zero dependencies)</li>
 *   <li>{@code RocksDbRaftStorage} - RocksDB adapter (high performance)</li>
 * </ul>
 */
public interface RaftStorage extends Closeable {

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Opens the storage backend at the specified directory.
     * Creates the directory if it does not exist.
     * Idempotent: calling open() on an already-open storage is a no-op.
     *
     * @param dataDir the directory for storage files
     * @return a Future that completes when the storage is ready
     */
    Future<Void> open(Path dataDir);

    /**
     * Closes the storage backend and releases all resources.
     * Must be called before JVM shutdown to ensure data integrity.
     */
    @Override
    void close();

    // =========================================================================
    // Metadata Operations (Term & Vote)
    // =========================================================================

    /**
     * Atomically persists the current term and voted-for candidate.
     * 
     * <p><b>Raft Safety:</b> This method MUST ensure durability (fsync equivalent)
     * before the Future completes. The caller will grant a vote or update term
     * only after this Future succeeds.</p>
     *
     * @param currentTerm the current Raft term
     * @param votedFor    the candidate voted for in this term (empty if none)
     * @return a Future that completes after durable persistence
     */
    Future<Void> updateMetadata(long currentTerm, Optional<String> votedFor);

    /**
     * Loads persisted metadata on startup.
     * Returns default values (term=0, votedFor=empty) if no state exists.
     *
     * @return a Future containing the persisted metadata
     */
    Future<PersistentMeta> loadMetadata();

    /**
     * Immutable record for persisted Raft metadata.
     */
    record PersistentMeta(long currentTerm, Optional<String> votedFor) {
        public static PersistentMeta empty() {
            return new PersistentMeta(0L, Optional.empty());
        }
    }

    // =========================================================================
    // Log Operations
    // =========================================================================

    /**
     * Appends a batch of log entries.
     * 
     * <p><b>Note:</b> Entries are NOT guaranteed to be durable until {@link #sync()}
     * is called. This allows batching multiple appends before a single fsync.</p>
     *
     * @param entries the entries to append (must have sequential indices)
     * @return a Future that completes when entries are written (but not necessarily synced)
     */
    Future<Void> appendEntries(List<LogEntryData> entries);

    /**
     * Deletes all log entries with index >= fromIndex.
     * Used to resolve log conflicts when a follower diverges from the leader.
     * 
     * <p><b>Note:</b> Truncation is NOT guaranteed to be durable until {@link #sync()}
     * is called.</p>
     *
     * @param fromIndex the first index to delete (inclusive)
     * @return a Future that completes when truncation is recorded
     */
    Future<Void> truncateSuffix(long fromIndex);

    /**
     * Forces all pending writes to durable storage.
     * 
     * <p><b>THE DURABILITY BARRIER:</b> This is the critical synchronization point.
     * After this Future completes successfully, all prior appends and truncations
     * are guaranteed to survive a crash.</p>
     * 
     * <p>The RaftNode MUST call this before acknowledging AppendEntries RPCs.</p>
     *
     * @return a Future that completes after fsync (or equivalent)
     */
    Future<Void> sync();

    /**
     * Replays the entire log from storage on startup.
     * Returns entries in index order, handling any truncation markers.
     *
     * @return a Future containing all valid log entries
     */
    Future<List<LogEntryData>> replayLog();

    /**
     * Immutable record for a log entry.
     */
    record LogEntryData(long index, long term, byte[] payload) {}
}
```

### F.3 Implementation A: FileRaftStorage (Custom WAL)

This is the **default implementation** — zero external dependencies, pure Java.

```java
package dev.mars.quorus.controller.raft.storage.file;

import dev.mars.quorus.controller.raft.storage.RaftStorage;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.CRC32C;

import static java.nio.file.StandardOpenOption.*;

/**
 * File-based Raft storage using a custom Write-Ahead Log.
 * 
 * <p>Storage layout:</p>
 * <pre>
 * {dataDir}/
 *   ├── meta.dat      // currentTerm + votedFor (atomic replace)
 *   └── raft.log      // append-only WAL with TRUNCATE/APPEND records
 * </pre>
 * 
 * <p>This implementation is designed for correctness over performance.
 * For high-throughput scenarios, use {@code RocksDbRaftStorage}.</p>
 */
public final class FileRaftStorage implements RaftStorage {

    private static final int MAGIC = 0x52414654;  // 'RAFT'
    private static final short VERSION = 1;
    private static final byte TYPE_TRUNCATE = 1;
    private static final byte TYPE_APPEND = 2;

    private final Vertx vertx;
    private final WorkerExecutor executor;
    
    private Path dataDir;
    private FileChannel logChannel;

    public FileRaftStorage(Vertx vertx, WorkerExecutor executor) {
        this.vertx = vertx;
        this.executor = executor;
    }

    @Override
    public Future<Void> open(Path dataDir) {
        this.dataDir = dataDir;
        return vertx.executeBlocking(() -> {
            Files.createDirectories(dataDir);
            this.logChannel = FileChannel.open(
                dataDir.resolve("raft.log"), CREATE, READ, WRITE);
            logChannel.position(logChannel.size());
            return null;
        }, false);
    }

    @Override
    public Future<Void> updateMetadata(long currentTerm, Optional<String> votedFor) {
        return vertx.executeBlocking(() -> {
            Path tmp = dataDir.resolve("meta.dat.tmp");
            Path dst = dataDir.resolve("meta.dat");

            byte[] voteBytes = votedFor.map(s -> s.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                                       .orElse(new byte[0]);

            ByteBuffer buf = ByteBuffer.allocate(8 + 4 + voteBytes.length + 4);
            buf.putLong(currentTerm);
            buf.putInt(voteBytes.length);
            buf.put(voteBytes);

            CRC32C crc = new CRC32C();
            crc.update(buf.array(), 0, 8 + 4 + voteBytes.length);
            buf.putInt((int) crc.getValue());
            buf.flip();

            // Write to temp file
            try (FileChannel ch = FileChannel.open(tmp, CREATE, TRUNCATE_EXISTING, WRITE)) {
                while (buf.hasRemaining()) ch.write(buf);
                ch.force(true);  // fsync the file
            }

            // Atomic rename
            Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            
            // Fsync the directory (critical on Linux)
            syncDirectory(dataDir);
            
            return null;
        }, false);
    }

    @Override
    public Future<PersistentMeta> loadMetadata() {
        return vertx.executeBlocking(() -> {
            Path metaPath = dataDir.resolve("meta.dat");
            if (!Files.exists(metaPath)) {
                return PersistentMeta.empty();
            }

            byte[] data = Files.readAllBytes(metaPath);
            ByteBuffer buf = ByteBuffer.wrap(data);

            long term = buf.getLong();
            int voteLen = buf.getInt();
            
            if (voteLen < 0 || voteLen > data.length - 16) {
                throw new IOException("Corrupt meta.dat: invalid vote length");
            }

            byte[] voteBytes = new byte[voteLen];
            buf.get(voteBytes);

            int storedCrc = buf.getInt();
            CRC32C crc = new CRC32C();
            crc.update(data, 0, 8 + 4 + voteLen);
            
            if ((int) crc.getValue() != storedCrc) {
                throw new IOException("Corrupt meta.dat: CRC mismatch");
            }

            Optional<String> votedFor = voteLen == 0 
                ? Optional.empty()
                : Optional.of(new String(voteBytes, java.nio.charset.StandardCharsets.UTF_8));

            return new PersistentMeta(term, votedFor);
        }, false);
    }

    @Override
    public Future<Void> appendEntries(List<LogEntryData> entries) {
        if (entries.isEmpty()) return Future.succeededFuture();
        
        return vertx.executeBlocking(() -> {
            for (LogEntryData entry : entries) {
                writeRecord(TYPE_APPEND, entry.index(), entry.term(), entry.payload());
            }
            return null;
        }, false);
    }

    @Override
    public Future<Void> truncateSuffix(long fromIndex) {
        return vertx.executeBlocking(() -> {
            writeRecord(TYPE_TRUNCATE, fromIndex, 0L, new byte[0]);
            return null;
        }, false);
    }

    @Override
    public Future<Void> sync() {
        return vertx.executeBlocking(() -> {
            logChannel.force(true);
            return null;
        }, false);
    }

    @Override
    public Future<List<LogEntryData>> replayLog() {
        // See Section 15.6 for full implementation
        return vertx.executeBlocking(() -> {
            // ... replay logic with CRC validation and tail truncation
            return new ArrayList<LogEntryData>();
        }, false);
    }

    @Override
    public void close() {
        try {
            if (logChannel != null) logChannel.close();
        } catch (IOException e) {
            // Log warning
        }
    }

    // ---- Private Helpers ----

    private void writeRecord(byte type, long index, long term, byte[] payload) throws IOException {
        int headerLen = 4 + 2 + 1 + 8 + 8 + 4;  // magic + ver + type + index + term + payloadLen
        ByteBuffer buf = ByteBuffer.allocate(headerLen + payload.length + 4);

        buf.putInt(MAGIC);
        buf.putShort(VERSION);
        buf.put(type);
        buf.putLong(index);
        buf.putLong(term);
        buf.putInt(payload.length);
        buf.put(payload);

        CRC32C crc = new CRC32C();
        crc.update(buf.array(), 0, headerLen + payload.length);
        buf.putInt((int) crc.getValue());
        buf.flip();

        while (buf.hasRemaining()) logChannel.write(buf);
    }

    private void syncDirectory(Path dir) throws IOException {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return;  // Windows doesn't support directory fsync
        }
        try (FileChannel dirChannel = FileChannel.open(dir, READ)) {
            dirChannel.force(true);
        }
    }
}
```

### F.4 Implementation B: RocksDbRaftStorage (High Performance)

This adapter wraps RocksDB to provide the same interface with better performance characteristics.

```java
package dev.mars.quorus.controller.raft.storage.rocksdb;

import dev.mars.quorus.controller.raft.storage.RaftStorage;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import org.rocksdb.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * RocksDB-based Raft storage for high-performance deployments.
 * 
 * <p>Key Schema:</p>
 * <pre>
 * meta:term     -> 8-byte long (currentTerm)
 * meta:votedFor -> UTF-8 string (candidateId)
 * log:{index}   -> {term:8bytes}{payload:*}
 * </pre>
 * 
 * <p>RocksDB provides:</p>
 * <ul>
 *   <li>LSM-tree with efficient writes</li>
 *   <li>Built-in compression</li>
 *   <li>Atomic batch writes</li>
 *   <li>Range deletes for truncation</li>
 * </ul>
 */
public final class RocksDbRaftStorage implements RaftStorage {

    static {
        RocksDB.loadLibrary();
    }

    private static final byte[] KEY_TERM = "meta:term".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY_VOTED_FOR = "meta:votedFor".getBytes(StandardCharsets.UTF_8);
    private static final String LOG_PREFIX = "log:";

    private final Vertx vertx;
    private final WorkerExecutor executor;
    
    private RocksDB db;
    private WriteOptions syncWriteOptions;
    private WriteOptions asyncWriteOptions;

    public RocksDbRaftStorage(Vertx vertx, WorkerExecutor executor) {
        this.vertx = vertx;
        this.executor = executor;
    }

    @Override
    public Future<Void> open(Path dataDir) {
        return vertx.executeBlocking(() -> {
            Options options = new Options()
                .setCreateIfMissing(true)
                .setWriteBufferSize(64 * 1024 * 1024)  // 64MB write buffer
                .setMaxWriteBufferNumber(3)
                .setTargetFileSizeBase(64 * 1024 * 1024);

            db = RocksDB.open(options, dataDir.toString());
            
            // Sync writes for durability-critical operations
            syncWriteOptions = new WriteOptions().setSync(true);
            
            // Async writes for batched appends (sync() called separately)
            asyncWriteOptions = new WriteOptions().setSync(false);
            
            return null;
        }, false);
    }

    @Override
    public Future<Void> updateMetadata(long currentTerm, Optional<String> votedFor) {
        return vertx.executeBlocking(() -> {
            try (WriteBatch batch = new WriteBatch()) {
                // Write term
                ByteBuffer termBuf = ByteBuffer.allocate(8);
                termBuf.putLong(currentTerm);
                batch.put(KEY_TERM, termBuf.array());

                // Write votedFor (delete if empty)
                if (votedFor.isPresent()) {
                    batch.put(KEY_VOTED_FOR, votedFor.get().getBytes(StandardCharsets.UTF_8));
                } else {
                    batch.delete(KEY_VOTED_FOR);
                }

                // Atomic, synchronous write
                db.write(syncWriteOptions, batch);
            }
            return null;
        }, false);
    }

    @Override
    public Future<PersistentMeta> loadMetadata() {
        return vertx.executeBlocking(() -> {
            byte[] termBytes = db.get(KEY_TERM);
            byte[] voteBytes = db.get(KEY_VOTED_FOR);

            long term = 0;
            if (termBytes != null && termBytes.length == 8) {
                term = ByteBuffer.wrap(termBytes).getLong();
            }

            Optional<String> votedFor = Optional.empty();
            if (voteBytes != null && voteBytes.length > 0) {
                votedFor = Optional.of(new String(voteBytes, StandardCharsets.UTF_8));
            }

            return new PersistentMeta(term, votedFor);
        }, false);
    }

    @Override
    public Future<Void> appendEntries(List<LogEntryData> entries) {
        if (entries.isEmpty()) return Future.succeededFuture();

        return vertx.executeBlocking(() -> {
            try (WriteBatch batch = new WriteBatch()) {
                for (LogEntryData entry : entries) {
                    byte[] key = logKey(entry.index());
                    byte[] value = encodeEntry(entry.term(), entry.payload());
                    batch.put(key, value);
                }
                // Async write - sync() will be called separately
                db.write(asyncWriteOptions, batch);
            }
            return null;
        }, false);
    }

    @Override
    public Future<Void> truncateSuffix(long fromIndex) {
        return vertx.executeBlocking(() -> {
            // RocksDB range delete: [fromIndex, MAX_LONG)
            byte[] startKey = logKey(fromIndex);
            byte[] endKey = logKey(Long.MAX_VALUE);
            
            try (WriteBatch batch = new WriteBatch()) {
                batch.deleteRange(startKey, endKey);
                db.write(asyncWriteOptions, batch);
            }
            return null;
        }, false);
    }

    @Override
    public Future<Void> sync() {
        return vertx.executeBlocking(() -> {
            // Force WAL sync in RocksDB
            db.flushWal(true);
            return null;
        }, false);
    }

    @Override
    public Future<List<LogEntryData>> replayLog() {
        return vertx.executeBlocking(() -> {
            List<LogEntryData> entries = new ArrayList<>();
            
            byte[] prefix = LOG_PREFIX.getBytes(StandardCharsets.UTF_8);
            
            try (RocksIterator iter = db.newIterator()) {
                iter.seek(prefix);
                
                while (iter.isValid()) {
                    byte[] key = iter.key();
                    
                    // Check if still in log: prefix
                    if (!startsWith(key, prefix)) break;
                    
                    long index = parseLogIndex(key);
                    byte[] value = iter.value();
                    
                    long term = ByteBuffer.wrap(value, 0, 8).getLong();
                    byte[] payload = new byte[value.length - 8];
                    System.arraycopy(value, 8, payload, 0, payload.length);
                    
                    entries.add(new LogEntryData(index, term, payload));
                    iter.next();
                }
            }
            
            return entries;
        }, false);
    }

    @Override
    public void close() {
        if (syncWriteOptions != null) syncWriteOptions.close();
        if (asyncWriteOptions != null) asyncWriteOptions.close();
        if (db != null) db.close();
    }

    // ---- Private Helpers ----

    private byte[] logKey(long index) {
        // Format: "log:{index}" with zero-padded index for lexicographic ordering
        return String.format("log:%020d", index).getBytes(StandardCharsets.UTF_8);
    }

    private long parseLogIndex(byte[] key) {
        String keyStr = new String(key, StandardCharsets.UTF_8);
        return Long.parseLong(keyStr.substring(LOG_PREFIX.length()));
    }

    private byte[] encodeEntry(long term, byte[] payload) {
        ByteBuffer buf = ByteBuffer.allocate(8 + payload.length);
        buf.putLong(term);
        buf.put(payload);
        return buf.array();
    }

    private boolean startsWith(byte[] array, byte[] prefix) {
        if (array.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (array[i] != prefix[i]) return false;
        }
        return true;
    }
}
```

### F.5 Storage Factory & Configuration

Use a factory to instantiate the correct implementation based on configuration:

```java
package dev.mars.quorus.controller.raft.storage;

import dev.mars.quorus.controller.config.AppConfig;
import dev.mars.quorus.controller.raft.storage.file.FileRaftStorage;
import dev.mars.quorus.controller.raft.storage.rocksdb.RocksDbRaftStorage;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;

/**
 * Factory for creating RaftStorage instances based on configuration.
 */
public final class RaftStorageFactory {

    public enum StorageType {
        FILE,       // Custom WAL (default)
        ROCKSDB     // High-performance KV store
    }

    private RaftStorageFactory() {}

    /**
     * Creates a RaftStorage instance based on configuration.
     * 
     * @param vertx    the Vert.x instance
     * @param executor the worker executor for blocking I/O
     * @return the configured RaftStorage implementation
     */
    public static RaftStorage create(Vertx vertx, WorkerExecutor executor) {
        AppConfig config = AppConfig.get();
        String storageType = config.getString("quorus.raft.storage.type", "file");

        return switch (storageType.toLowerCase()) {
            case "rocksdb" -> {
                validateRocksDbAvailable();
                yield new RocksDbRaftStorage(vertx, executor);
            }
            case "file" -> new FileRaftStorage(vertx, executor);
            default -> throw new IllegalArgumentException(
                "Unknown storage type: " + storageType + ". Valid options: file, rocksdb");
        };
    }

    private static void validateRocksDbAvailable() {
        try {
            Class.forName("org.rocksdb.RocksDB");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                "RocksDB storage requested but org.rocksdb:rocksdbjni is not on classpath. " +
                "Add the dependency or use storage.type=file");
        }
    }
}
```

### F.6 Configuration Properties

Add to `quorus-controller.properties`:

```properties
# =============================================================================
# Raft Storage Configuration
# =============================================================================

# Storage backend type: "file" (default) or "rocksdb"
# - file:    Custom WAL, zero dependencies, simpler, lower throughput
# - rocksdb: High-performance LSM-tree, requires rocksdbjni dependency
quorus.raft.storage.type=file

# Base directory for storage files
quorus.raft.storage.path=/var/lib/quorus/data

# Enable fsync after writes (required for durability, disable only for testing)
quorus.raft.storage.fsync=true

# RocksDB-specific settings (ignored if type=file)
quorus.raft.storage.rocksdb.write-buffer-size=67108864
quorus.raft.storage.rocksdb.max-write-buffers=3
```

### F.7 Dependency Management

For RocksDB support, add to `pom.xml` with an optional scope:

```xml
<!-- Optional: High-performance storage backend -->
<dependency>
    <groupId>org.rocksdb</groupId>
    <artifactId>rocksdbjni</artifactId>
    <version>9.0.0</version>
    <optional>true</optional>
</dependency>
```

This allows:
- Default builds to work without RocksDB
- Production deployments to opt-in by including the JAR

### F.8 Comparison Matrix

| Feature | FileRaftStorage | RocksDbRaftStorage |
|---------|-----------------|-------------------|
| **Dependencies** | None (pure Java) | `rocksdbjni` (~40MB) |
| **Write Performance** | ~1,000 ops/sec | ~100,000 ops/sec |
| **Read Performance** | O(n) replay | O(1) point lookups |
| **Compression** | None | LZ4/Snappy/Zstd |
| **Memory Usage** | Minimal | Configurable (write buffers) |
| **Complexity** | Simple | More operational overhead |
| **Best For** | Dev, small clusters | Production, high throughput |

### F.9 Testing with Mock Storage

For unit tests, create a mock implementation:

```java
package dev.mars.quorus.controller.raft.storage;

import io.vertx.core.Future;
import java.nio.file.Path;
import java.util.*;

/**
 * In-memory storage for unit testing.
 * NOT for production use - does not provide durability.
 */
public final class InMemoryRaftStorage implements RaftStorage {

    private long currentTerm = 0;
    private Optional<String> votedFor = Optional.empty();
    private final List<LogEntryData> log = new ArrayList<>();
    
    private boolean failOnSync = false;  // For testing error paths

    @Override
    public Future<Void> open(Path dataDir) {
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> updateMetadata(long currentTerm, Optional<String> votedFor) {
        this.currentTerm = currentTerm;
        this.votedFor = votedFor;
        return Future.succeededFuture();
    }

    @Override
    public Future<PersistentMeta> loadMetadata() {
        return Future.succeededFuture(new PersistentMeta(currentTerm, votedFor));
    }

    @Override
    public Future<Void> appendEntries(List<LogEntryData> entries) {
        log.addAll(entries);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> truncateSuffix(long fromIndex) {
        log.removeIf(e -> e.index() >= fromIndex);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> sync() {
        if (failOnSync) {
            return Future.failedFuture(new IOException("Simulated sync failure"));
        }
        return Future.succeededFuture();
    }

    @Override
    public Future<List<LogEntryData>> replayLog() {
        return Future.succeededFuture(new ArrayList<>(log));
    }

    @Override
    public void close() {
        log.clear();
    }

    // ---- Test Helpers ----
    
    public void setFailOnSync(boolean fail) {
        this.failOnSync = fail;
    }
    
    public List<LogEntryData> getLog() {
        return Collections.unmodifiableList(log);
    }
}
```

### F.10 RaftNode Integration

The `RaftNode` is completely decoupled from storage implementation:

```java
public class RaftNode {
    
    private final RaftStorage storage;  // Interface, not implementation

    public RaftNode(RaftStorage storage, StateMachine stateMachine) {
        this.storage = storage;
        // ...
    }

    // All storage operations go through the interface
    // RaftNode never knows if it's using File or RocksDB
}

// Usage in QuorusControllerVerticle:
WorkerExecutor walExecutor = vertx.createSharedWorkerExecutor("wal", 1);
RaftStorage storage = RaftStorageFactory.create(vertx, walExecutor);
RaftNode raftNode = new RaftNode(storage, stateMachine);
```

### F.11 Migration Path

To switch from FileRaftStorage to RocksDB in production:

1. **Stop the cluster gracefully** (let leader step down)
2. **Backup `data/` directories** on all nodes
3. **Add rocksdbjni dependency** to deployment
4. **Update configuration**: `quorus.raft.storage.type=rocksdb`
5. **Clear data directories** (RocksDB uses different format)
6. **Restart cluster** — it will bootstrap as a new cluster

> **Note:** There is no automatic migration between storage formats. The Raft cluster will re-elect a leader and rebuild state from scratch.

### F.12 Summary

| Aspect | Design Choice |
|--------|---------------|
| **Interface** | Single `RaftStorage` SPI for all backends |
| **Default** | `FileRaftStorage` — zero dependencies |
| **Optional** | `RocksDbRaftStorage` — opt-in high performance |
| **Testing** | `InMemoryRaftStorage` — fast unit tests |
| **Configuration** | `quorus.raft.storage.type` property |
| **Factory** | `RaftStorageFactory.create()` for instantiation |
| **Coupling** | `RaftNode` depends only on interface |