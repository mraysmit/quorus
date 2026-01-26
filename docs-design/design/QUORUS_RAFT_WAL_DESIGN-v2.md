# Minimal Write-Ahead Log (WAL) for Raft
**Design, Requirements, and Integration Strategy**

## Status
**Proposed – Design Review**

## Scope
This document defines a **minimal, Raft-correct Write-Ahead Log (WAL)** design for a Java / Vert.x Raft implementation.

The WAL is intentionally constrained to support **only**:
- append
- truncate (suffix deletion)
- sequential replay on startup

It is **not** a general-purpose storage engine.

---

## 1. Motivation

The current Raft implementation stores the following **entirely in memory**:

- `currentTerm`
- `votedFor`
- the Raft log (`List<LogEntry>`)

This violates Raft’s persistence requirements.

### Consequences of the current design
- Node restart resets `currentTerm` and `votedFor` → double voting, split elections
- Followers can acknowledge log entries that are lost on crash
- Log divergence and data loss become possible

In short: **the implementation is unsafe under restart or crash**.

---

## 2. Raft Persistence Rules (Non-Negotiable)

Per the Raft paper, the following **must be durably persisted**:

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
- ❌ No attempt to make the WAL “fast” by weakening durability rules

The WAL exists **solely** to:
- survive crashes
- enforce Raft safety rules
- rebuild in-memory state on restart

Anything beyond that is out of scope.

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

  @Override
  public Future<Void> append(long index, long term, byte[] payload) {
    return writeRecord(TYPE_APPEND, index, term, payload);
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

## 16. Wiring Persistence Barriers into RaftNode

This section maps directly to your code:

- `RaftNode.handleVoteRequest(VoteRequest request)`
- `RaftNode.handleAppendEntriesRequest(AppendEntriesRequest request)`

### 16.1 RequestVote: persist-before-grant

Your current implementation (from `RaftNode.handleVoteRequest`) grants a vote by updating in-memory `votedFor` and immediately replying.

**Required change:** do not reply granted until meta is durable.

Pseudo-wiring (adapted to your structure using `Promise<VoteResponse>`):

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

### 16.2 AppendEntries: truncate+append must be durable before ACK

Your current implementation (from `RaftNode.handleAppendEntriesRequest`) performs:
- in-memory truncate (via `subList(...).clear()`)
- in-memory append
- then replies success

**Required approach:**
1. validate consistency against in-memory log
2. build a *plan* of what needs truncating and what entries will be appended
3. write to WAL: `truncateFrom(...)`, then `append(...)` for each new entry
4. call `wal.sync()`
5. apply plan to in-memory log
6. reply success

Pseudo-wiring:

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
f = f.compose(v3 -> wal.sync());

// then mutate in-memory + ACK
f.onSuccess(v3 -> {
  plan.applyTo(log);        // now do subList().clear() and log.add(...)
  // commit index / applyLog afterwards
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

### 16.3 Why persist-before-in-memory for AppendEntries?
Because the follower’s ACK must mean: “this change will survive restart.”

Persist-first gives you a clean invariant:
- **If we ACK success, the WAL contains the truncation+append and has been fsync’d.**

---

## 17. Practical Integration Notes (Vert.x)

- Create a dedicated executor for WAL operations (pool size **1** is fine and simplifies ordering)
- WAL ops run via `executeBlocking(..., walExecutor)`
- Never block the event loop

---

## 18. Minimal Acceptance Criteria

Before considering the WAL “done”:

- ✅ restart preserves `currentTerm` and `votedFor`
- ✅ follower never ACKs AppendEntries unless entries are fsync’d
- ✅ replay truncates corrupt/partial tail safely
- ✅ crash test: kill during append → restart yields prefix-safe log
- ✅ crash test: kill during truncate+append sequence → replay yields last durable state
