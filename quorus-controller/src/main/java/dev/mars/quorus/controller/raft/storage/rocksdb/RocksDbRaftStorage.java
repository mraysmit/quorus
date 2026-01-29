/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.mars.quorus.controller.raft.storage.rocksdb;

import dev.mars.quorus.controller.raft.storage.RaftStorage;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import org.rocksdb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * RocksDB-based implementation of {@link RaftStorage} for high-performance deployments.
 *
 * <p><b>Key Schema:</b></p>
 * <pre>
 * meta:term      → 8-byte long (currentTerm)
 * meta:votedFor  → UTF-8 string (candidateId)
 * log:00000000000000000001 → {term:8bytes}{payload:*}
 * </pre>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>LSM-tree with efficient sequential writes</li>
 *   <li>Built-in compression (LZ4 by default)</li>
 *   <li>Atomic batch writes via WriteBatch</li>
 *   <li>Efficient range deletes for truncation</li>
 * </ul>
 *
 * <p><b>Requirements:</b> This class requires {@code org.rocksdb:rocksdbjni} on the classpath.
 * If not present, use {@link dev.mars.quorus.controller.raft.storage.file.FileRaftStorage} instead.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-01-29
 */
public final class RocksDbRaftStorage implements RaftStorage {

    private static final Logger LOG = LoggerFactory.getLogger(RocksDbRaftStorage.class);

    // =========================================================================
    // Key Schema
    // =========================================================================

    private static final byte[] KEY_TERM = "meta:term".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY_VOTED_FOR = "meta:votedFor".getBytes(StandardCharsets.UTF_8);
    private static final String LOG_PREFIX = "log:";

    /** Zero-padded format for log keys to ensure lexicographic ordering */
    private static final String LOG_KEY_FORMAT = "log:%020d";

    // =========================================================================
    // Static Initialization
    // =========================================================================

    static {
        RocksDB.loadLibrary();
    }

    // =========================================================================
    // Instance State
    // =========================================================================

    private final Vertx vertx;
    private final WorkerExecutor executor;
    private final boolean fsyncEnabled;

    private Path dataDir;
    private RocksDB db;
    private WriteOptions syncWriteOptions;
    private WriteOptions asyncWriteOptions;
    private Options dbOptions;
    private boolean opened = false;

    /**
     * Creates a new RocksDbRaftStorage.
     *
     * @param vertx    the Vert.x instance
     * @param executor the worker executor for blocking I/O operations
     */
    public RocksDbRaftStorage(Vertx vertx, WorkerExecutor executor) {
        this(vertx, executor, null, true);
    }

    /**
     * Creates a new RocksDbRaftStorage with the specified path and fsync setting.
     *
     * @param vertx        the Vert.x instance
     * @param executor     the worker executor for blocking I/O operations
     * @param dataDir      the directory for storage files
     * @param fsyncEnabled whether to fsync on writes
     */
    public RocksDbRaftStorage(Vertx vertx, WorkerExecutor executor, Path dataDir, boolean fsyncEnabled) {
        this.vertx = vertx;
        this.executor = executor;
        this.dataDir = dataDir;
        this.fsyncEnabled = fsyncEnabled;
    }

    /**
     * Opens the storage using the path provided in the constructor.
     *
     * @return Future that completes when storage is opened
     * @throws IllegalStateException if dataDir was not set in constructor
     */
    public Future<Void> open() {
        if (this.dataDir == null) {
            return Future.failedFuture(new IllegalStateException(
                    "dataDir not set. Use open(Path) or provide dataDir in constructor."));
        }
        return open(dataDir);
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    public Future<Void> open(Path dataDir) {
        return vertx.executeBlocking(() -> {
            dbOptions = new Options()
                    .setCreateIfMissing(true)
                    .setWriteBufferSize(64 * 1024 * 1024)  // 64MB write buffer
                    .setMaxWriteBufferNumber(3)
                    .setTargetFileSizeBase(64 * 1024 * 1024)
                    .setCompressionType(CompressionType.LZ4_COMPRESSION);

            db = RocksDB.open(dbOptions, dataDir.toString());

            // Sync writes for durability-critical operations (metadata)
            syncWriteOptions = new WriteOptions().setSync(true);

            // Async writes for batched appends (sync() called separately)
            asyncWriteOptions = new WriteOptions().setSync(false);

            opened = true;
            LOG.info("RocksDbRaftStorage opened: {}", dataDir);
            return null;
        }, false);
    }

    @Override
    public Future<Void> close() {
        return vertx.executeBlocking(() -> {
            opened = false;

            if (syncWriteOptions != null) {
                syncWriteOptions.close();
                syncWriteOptions = null;
            }
            if (asyncWriteOptions != null) {
                asyncWriteOptions.close();
                asyncWriteOptions = null;
            }
            if (dbOptions != null) {
                dbOptions.close();
                dbOptions = null;
            }
            if (db != null) {
                db.close();
                db = null;
                LOG.info("RocksDbRaftStorage closed");
            }
            return null;
        }, false);
    }

    // =========================================================================
    // Metadata Operations
    // =========================================================================

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
            LOG.debug("Metadata updated: term={}, votedFor={}", currentTerm, votedFor.orElse("(none)"));
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

            LOG.debug("Metadata loaded: term={}, votedFor={}", term, votedFor.orElse("(none)"));
            return new PersistentMeta(term, votedFor);
        }, false);
    }

    // =========================================================================
    // Log Operations
    // =========================================================================

    @Override
    public Future<Void> appendEntries(List<LogEntryData> entries) {
        if (entries.isEmpty()) {
            return Future.succeededFuture();
        }

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
            LOG.debug("Appended {} entries to RocksDB", entries.size());
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
            LOG.debug("Truncated entries from index {} onwards", fromIndex);
            return null;
        }, false);
    }

    @Override
    public Future<Void> sync() {
        if (!fsyncEnabled) {
            LOG.trace("RocksDB sync skipped (fsync disabled)");
            return Future.succeededFuture();
        }
        return vertx.executeBlocking(() -> {
            // Force WAL sync in RocksDB
            try (FlushOptions flushOptions = new FlushOptions().setWaitForFlush(true)) {
                db.flush(flushOptions);
            }
            LOG.trace("RocksDB synced to disk");
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
                    if (!startsWith(key, prefix)) {
                        break;
                    }

                    long index = parseLogIndex(key);
                    byte[] value = iter.value();

                    if (value.length < 8) {
                        LOG.warn("Corrupt entry at index {}: value too short", index);
                        iter.next();
                        continue;
                    }

                    long term = ByteBuffer.wrap(value, 0, 8).getLong();
                    byte[] payload = new byte[value.length - 8];
                    System.arraycopy(value, 8, payload, 0, payload.length);

                    entries.add(new LogEntryData(index, term, payload));
                    iter.next();
                }
            }

            LOG.info("RocksDB replay complete: {} entries recovered", entries.size());
            return entries;
        }, false);
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    /**
     * Creates a log key with zero-padded index for lexicographic ordering.
     */
    private byte[] logKey(long index) {
        return String.format(LOG_KEY_FORMAT, index).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Parses the index from a log key.
     */
    private long parseLogIndex(byte[] key) {
        String keyStr = new String(key, StandardCharsets.UTF_8);
        return Long.parseLong(keyStr.substring(LOG_PREFIX.length()));
    }

    /**
     * Encodes a log entry value: term (8 bytes) + payload.
     */
    private byte[] encodeEntry(long term, byte[] payload) {
        byte[] safePayload = payload != null ? payload : new byte[0];
        ByteBuffer buf = ByteBuffer.allocate(8 + safePayload.length);
        buf.putLong(term);
        buf.put(safePayload);
        return buf.array();
    }

    /**
     * Checks if array starts with prefix.
     */
    private boolean startsWith(byte[] array, byte[] prefix) {
        if (array.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (array[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
