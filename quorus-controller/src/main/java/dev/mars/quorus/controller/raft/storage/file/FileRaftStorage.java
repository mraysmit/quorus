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

package dev.mars.quorus.controller.raft.storage.file;

import dev.mars.quorus.controller.raft.storage.RaftStorage;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.CRC32C;

/**
 * File-based implementation of {@link RaftStorage} using a custom Write-Ahead Log.
 *
 * <p><b>Storage Layout:</b></p>
 * <pre>
 * {dataDir}/
 *   ├── meta.dat      // currentTerm + votedFor (atomic replace)
 *   └── raft.log      // append-only WAL with TRUNCATE/APPEND records
 * </pre>
 *
 * <p><b>Design Principles:</b></p>
 * <ul>
 *   <li>Correctness over performance</li>
 *   <li>Zero external dependencies (pure Java)</li>
 *   <li>Self-framing binary records with CRC32C checksums</li>
 *   <li>Atomic metadata updates via temp-file → rename</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> All I/O operations are blocking and must be executed
 * on a dedicated {@link WorkerExecutor} with pool size 1 to ensure serialization.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-01-29
 * @see <a href="docs-design/design/QUORUS_RAFT_WAL_DESIGN.md">WAL Design Document</a>
 */
public final class FileRaftStorage implements RaftStorage {

    private static final Logger LOG = LoggerFactory.getLogger(FileRaftStorage.class);

    // =========================================================================
    // WAL Record Format Constants
    // =========================================================================

    /** Magic number: 'RAFT' in ASCII */
    private static final int MAGIC = 0x52414654;

    /** Current WAL format version */
    private static final short VERSION = 1;

    /** Record type: Truncate suffix (delete entries >= index) */
    private static final byte TYPE_TRUNCATE = 1;

    /** Record type: Append entry */
    private static final byte TYPE_APPEND = 2;

    /**
     * Header size: magic(4) + version(2) + type(1) + index(8) + term(8) + payloadLen(4)
     */
    private static final int HEADER_SIZE = 4 + 2 + 1 + 8 + 8 + 4;

    /** CRC32C checksum size */
    private static final int CRC_SIZE = 4;

    /** Maximum allowed payload size (16 MB) to prevent memory exhaustion on corrupt files */
    private static final int MAX_PAYLOAD_SIZE = 16 * 1024 * 1024;

    // =========================================================================
    // Instance State
    // =========================================================================

    private final Vertx vertx;
    private final WorkerExecutor executor;
    private final boolean fsyncEnabled;

    private Path dataDir;
    private FileChannel logChannel;
    private boolean opened = false;

    /**
     * Creates a new FileRaftStorage with default fsync enabled.
     *
     * @param vertx    the Vert.x instance
     * @param executor the worker executor for blocking I/O (should have pool size 1)
     */
    public FileRaftStorage(Vertx vertx, WorkerExecutor executor) {
        this(vertx, executor, null, true);
    }

    /**
     * Creates a new FileRaftStorage with the specified path and fsync setting.
     *
     * @param vertx        the Vert.x instance
     * @param executor     the worker executor for blocking I/O (should have pool size 1)
     * @param dataDir      the directory for storage files (will be created if not exists)
     * @param fsyncEnabled whether to fsync after writes (true for durability, false for testing)
     */
    public FileRaftStorage(Vertx vertx, WorkerExecutor executor, Path dataDir, boolean fsyncEnabled) {
        this.vertx = vertx;
        this.executor = executor;
        this.dataDir = dataDir;
        this.fsyncEnabled = fsyncEnabled;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    public Future<Void> open(Path dataDir) {
        if (this.dataDir == null) {
            this.dataDir = dataDir;
        }
        return doOpen();
    }

    /**
     * Opens the storage using the path provided in the constructor.
     * <p>This is a convenience method when the path is already set.</p>
     *
     * @return Future that completes when storage is opened
     * @throws IllegalStateException if dataDir was not set in constructor
     */
    public Future<Void> open() {
        if (this.dataDir == null) {
            return Future.failedFuture(new IllegalStateException(
                    "dataDir not set. Use open(Path) or provide dataDir in constructor."));
        }
        return doOpen();
    }

    private Future<Void> doOpen() {
        return vertx.executeBlocking(() -> {
            Files.createDirectories(dataDir);

            Path logPath = dataDir.resolve("raft.log");
            this.logChannel = FileChannel.open(
                    logPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE
            );
            // Position at end for appending
            logChannel.position(logChannel.size());

            opened = true;
            LOG.info("FileRaftStorage opened: {} (log size: {} bytes)", dataDir, logChannel.size());
            return null;
        }, false);
    }

    @Override
    public Future<Void> close() {
        return vertx.executeBlocking(() -> {
            opened = false;
            if (logChannel != null) {
                try {
                    logChannel.close();
                    LOG.info("FileRaftStorage closed: {}", dataDir);
                } catch (IOException e) {
                    LOG.warn("Error closing log channel", e);
                }
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
            Path tmp = dataDir.resolve("meta.dat.tmp");
            Path dst = dataDir.resolve("meta.dat");

            byte[] voteBytes = votedFor
                    .map(s -> s.getBytes(StandardCharsets.UTF_8))
                    .orElse(new byte[0]);

            // Format: term(8) + voteLen(4) + voteBytes + crc(4)
            ByteBuffer buf = ByteBuffer.allocate(8 + 4 + voteBytes.length + 4);
            buf.putLong(currentTerm);
            buf.putInt(voteBytes.length);
            buf.put(voteBytes);

            // Calculate CRC over term + voteLen + voteBytes
            CRC32C crc = new CRC32C();
            crc.update(buf.array(), 0, 8 + 4 + voteBytes.length);
            buf.putInt((int) crc.getValue());
            buf.flip();

            // Step 1: Write to temp file
            try (FileChannel ch = FileChannel.open(tmp,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                while (buf.hasRemaining()) {
                    ch.write(buf);
                }
                // Step 2: Fsync the temp file (if enabled)
                if (fsyncEnabled) {
                    ch.force(true);
                }
            }

            // Step 3: Atomic rename
            Files.move(tmp, dst,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            // Step 4: Fsync the directory (critical on Linux ext4/xfs)
            if (fsyncEnabled) {
                syncDirectory(dataDir);
            }

            LOG.debug("Metadata updated: term={}, votedFor={}", currentTerm, votedFor.orElse("(none)"));
            return null;
        }, false);
    }

    @Override
    public Future<PersistentMeta> loadMetadata() {
        return vertx.executeBlocking(() -> {
            Path metaPath = dataDir.resolve("meta.dat");

            if (!Files.exists(metaPath)) {
                LOG.debug("No metadata file found, returning empty metadata");
                return PersistentMeta.empty();
            }

            byte[] data;
            try {
                data = Files.readAllBytes(metaPath);
            } catch (NoSuchFileException e) {
                return PersistentMeta.empty();
            }

            if (data.length < 16) { // Minimum: term(8) + voteLen(4) + crc(4)
                throw new IOException("Corrupt meta.dat: file too short (" + data.length + " bytes)");
            }

            ByteBuffer buf = ByteBuffer.wrap(data);
            long term = buf.getLong();
            int voteLen = buf.getInt();

            // Validate vote length
            if (voteLen < 0 || voteLen > data.length - 16) {
                throw new IOException("Corrupt meta.dat: invalid vote length " + voteLen);
            }

            byte[] voteBytes = new byte[voteLen];
            buf.get(voteBytes);

            int storedCrc = buf.getInt();

            // Verify CRC
            CRC32C crc = new CRC32C();
            crc.update(data, 0, 8 + 4 + voteLen);
            if ((int) crc.getValue() != storedCrc) {
                throw new IOException("Corrupt meta.dat: CRC mismatch");
            }

            Optional<String> votedFor = voteLen == 0
                    ? Optional.empty()
                    : Optional.of(new String(voteBytes, StandardCharsets.UTF_8));

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
            for (LogEntryData entry : entries) {
                writeRecord(TYPE_APPEND, entry.index(), entry.term(), entry.payload());
            }
            LOG.debug("Appended {} entries to WAL", entries.size());
            return null;
        }, false);
    }

    @Override
    public Future<Void> truncateSuffix(long fromIndex) {
        return vertx.executeBlocking(() -> {
            writeRecord(TYPE_TRUNCATE, fromIndex, 0L, new byte[0]);
            LOG.debug("Recorded truncation from index {}", fromIndex);
            return null;
        }, false);
    }

    @Override
    public Future<Void> sync() {
        return vertx.executeBlocking(() -> {
            if (fsyncEnabled) {
                logChannel.force(true);
                LOG.trace("WAL synced to disk");
            } else {
                LOG.trace("WAL sync skipped (fsync disabled)");
            }
            return null;
        }, false);
    }

    @Override
    public Future<List<LogEntryData>> replayLog() {
        return vertx.executeBlocking(() -> {
            Path logPath = dataDir.resolve("raft.log");

            if (!Files.exists(logPath)) {
                LOG.debug("No WAL file found, starting with empty log");
                return List.of();
            }

            List<LogEntryData> result = new ArrayList<>();

            try (FileChannel ch = FileChannel.open(logPath,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE)) {

                long pos = 0;
                long lastGoodPos = 0;
                ByteBuffer headerBuf = ByteBuffer.allocate(HEADER_SIZE);

                while (true) {
                    // Read header
                    headerBuf.clear();
                    int bytesRead = ch.read(headerBuf, pos);
                    if (bytesRead < HEADER_SIZE) {
                        // Incomplete header - stop here
                        break;
                    }
                    headerBuf.flip();

                    // Parse header
                    int magic = headerBuf.getInt();
                    short version = headerBuf.getShort();
                    byte type = headerBuf.get();
                    long index = headerBuf.getLong();
                    long term = headerBuf.getLong();
                    int payloadLen = headerBuf.getInt();

                    // Validate header
                    if (magic != MAGIC) {
                        LOG.warn("Invalid magic at position {}: expected 0x{}, got 0x{}",
                                pos, Integer.toHexString(MAGIC), Integer.toHexString(magic));
                        break;
                    }
                    if (version != VERSION) {
                        LOG.warn("Unsupported version at position {}: {}", pos, version);
                        break;
                    }
                    if (payloadLen < 0 || payloadLen > MAX_PAYLOAD_SIZE) {
                        LOG.warn("Invalid payload length at position {}: {}", pos, payloadLen);
                        break;
                    }

                    // Read payload
                    ByteBuffer payloadBuf = ByteBuffer.allocate(payloadLen);
                    int payloadRead = ch.read(payloadBuf, pos + HEADER_SIZE);
                    if (payloadRead < payloadLen) {
                        LOG.warn("Incomplete payload at position {}", pos);
                        break;
                    }
                    payloadBuf.flip();

                    // Read CRC
                    ByteBuffer crcBuf = ByteBuffer.allocate(CRC_SIZE);
                    int crcRead = ch.read(crcBuf, pos + HEADER_SIZE + payloadLen);
                    if (crcRead < CRC_SIZE) {
                        LOG.warn("Incomplete CRC at position {}", pos);
                        break;
                    }
                    crcBuf.flip();
                    int storedCrc = crcBuf.getInt();

                    // Verify CRC
                    CRC32C crc = new CRC32C();
                    headerBuf.rewind();
                    crc.update(headerBuf);
                    crc.update(payloadBuf.duplicate());
                    if ((int) crc.getValue() != storedCrc) {
                        LOG.warn("CRC mismatch at position {}", pos);
                        break;
                    }

                    // Process record
                    if (type == TYPE_TRUNCATE) {
                        // Remove all entries >= index
                        long truncateFrom = index;
                        int originalSize = result.size();
                        result.removeIf(e -> e.index() >= truncateFrom);
                        LOG.debug("Replay: truncated {} entries from index {}",
                                originalSize - result.size(), truncateFrom);
                    } else if (type == TYPE_APPEND) {
                        byte[] payload = new byte[payloadLen];
                        payloadBuf.get(payload);
                        result.add(new LogEntryData(index, term, payload));
                        LOG.trace("Replay: appended entry at index {}, term {}", index, term);
                    } else {
                        LOG.warn("Unknown record type at position {}: {}", pos, type);
                        break;
                    }

                    // Advance to next record
                    lastGoodPos = pos + HEADER_SIZE + payloadLen + CRC_SIZE;
                    pos = lastGoodPos;
                }

                // Truncate file to last good position (removes torn writes)
                if (ch.size() > lastGoodPos) {
                    LOG.info("Truncating WAL from {} to {} bytes (removing torn writes)",
                            ch.size(), lastGoodPos);
                    ch.truncate(lastGoodPos);
                }
            }

            LOG.info("WAL replay complete: {} entries recovered", result.size());
            return result;
        }, false);
    }

    // =========================================================================
    // Snapshot Operations
    // =========================================================================

    @Override
    public Future<Void> saveSnapshot(byte[] data, long lastIncludedIndex, long lastIncludedTerm) {
        return vertx.executeBlocking(() -> {
            Path snapshotPath = dataDir.resolve("snapshot.dat");
            Path tmpPath = dataDir.resolve("snapshot.dat.tmp");

            // Format: lastIncludedIndex(8) + lastIncludedTerm(8) + dataLen(4) + data + crc(4)
            int totalSize = 8 + 8 + 4 + data.length + 4;
            ByteBuffer buf = ByteBuffer.allocate(totalSize);
            buf.putLong(lastIncludedIndex);
            buf.putLong(lastIncludedTerm);
            buf.putInt(data.length);
            buf.put(data);

            // CRC over everything except the CRC itself
            CRC32C crc = new CRC32C();
            crc.update(buf.array(), 0, 8 + 8 + 4 + data.length);
            buf.putInt((int) crc.getValue());
            buf.flip();

            // Write to temp file, then atomic rename
            try (FileChannel ch = FileChannel.open(tmpPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                while (buf.hasRemaining()) {
                    ch.write(buf);
                }
                if (fsyncEnabled) {
                    ch.force(true);
                }
            }

            Files.move(tmpPath, snapshotPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            if (fsyncEnabled) {
                syncDirectory(dataDir);
            }

            LOG.info("Snapshot saved: lastIncludedIndex={}, lastIncludedTerm={}, size={}bytes",
                    lastIncludedIndex, lastIncludedTerm, data.length);
            return null;
        }, false);
    }

    @Override
    public Future<Optional<SnapshotData>> loadSnapshot() {
        return vertx.executeBlocking(() -> {
            Path snapshotPath = dataDir.resolve("snapshot.dat");

            if (!Files.exists(snapshotPath)) {
                LOG.debug("No snapshot file found");
                return Optional.<SnapshotData>empty();
            }

            byte[] raw;
            try {
                raw = Files.readAllBytes(snapshotPath);
            } catch (NoSuchFileException e) {
                return Optional.<SnapshotData>empty();
            }

            // Minimum: index(8) + term(8) + dataLen(4) + crc(4) = 24
            if (raw.length < 24) {
                throw new IOException("Corrupt snapshot.dat: file too short (" + raw.length + " bytes)");
            }

            ByteBuffer buf = ByteBuffer.wrap(raw);
            long lastIncludedIndex = buf.getLong();
            long lastIncludedTerm = buf.getLong();
            int dataLen = buf.getInt();

            if (dataLen < 0 || dataLen > raw.length - 24) {
                throw new IOException("Corrupt snapshot.dat: invalid data length " + dataLen);
            }

            byte[] data = new byte[dataLen];
            buf.get(data);

            int storedCrc = buf.getInt();

            // Verify CRC
            CRC32C crc = new CRC32C();
            crc.update(raw, 0, 8 + 8 + 4 + dataLen);
            if ((int) crc.getValue() != storedCrc) {
                throw new IOException("Corrupt snapshot.dat: CRC mismatch");
            }

            LOG.info("Snapshot loaded: lastIncludedIndex={}, lastIncludedTerm={}, size={}bytes",
                    lastIncludedIndex, lastIncludedTerm, dataLen);
            return Optional.of(new SnapshotData(data, lastIncludedIndex, lastIncludedTerm));
        }, false);
    }

    @Override
    public Future<Void> truncatePrefix(long toIndex) {
        return vertx.executeBlocking(() -> {
            // Prefix truncation in a WAL is achieved by rewriting the log file
            // excluding entries with index <= toIndex.
            // For efficiency, we replay the current log, filter, and rewrite.
            LOG.info("Truncating WAL prefix: removing entries with index <= {}", toIndex);

            Path logPath = dataDir.resolve("raft.log");
            Path tmpLogPath = dataDir.resolve("raft.log.tmp");

            if (!Files.exists(logPath)) {
                return null;
            }

            // Close current channel to allow rewrite
            if (logChannel != null) {
                logChannel.close();
            }

            // Read all entries, filter out prefix
            List<LogEntryData> entries = replayLogBlocking();
            int originalSize = entries.size();
            entries.removeIf(e -> e.index() <= toIndex);

            // Rewrite the log with remaining entries
            try (FileChannel tmpChannel = FileChannel.open(tmpLogPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                for (LogEntryData entry : entries) {
                    writeRecordToChannel(tmpChannel, TYPE_APPEND, entry.index(), entry.term(), entry.payload());
                }
                if (fsyncEnabled) {
                    tmpChannel.force(true);
                }
            }

            // Atomic replace
            Files.move(tmpLogPath, logPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            if (fsyncEnabled) {
                syncDirectory(dataDir);
            }

            // Reopen the log channel for continued appending
            logChannel = FileChannel.open(logPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
            logChannel.position(logChannel.size());

            LOG.info("WAL prefix truncation complete: removed {} entries, {} remaining",
                    originalSize - entries.size(), entries.size());
            return null;
        }, false);
    }

    /**
     * Blocking replay for use within executeBlocking contexts.
     */
    private List<LogEntryData> replayLogBlocking() throws IOException {
        Path logPath = dataDir.resolve("raft.log");
        if (!Files.exists(logPath)) {
            return new ArrayList<>();
        }

        List<LogEntryData> result = new ArrayList<>();
        try (FileChannel ch = FileChannel.open(logPath, StandardOpenOption.READ)) {
            long pos = 0;
            ByteBuffer headerBuf = ByteBuffer.allocate(HEADER_SIZE);

            while (true) {
                headerBuf.clear();
                int bytesRead = ch.read(headerBuf, pos);
                if (bytesRead < HEADER_SIZE) break;
                headerBuf.flip();

                int magic = headerBuf.getInt();
                short version = headerBuf.getShort();
                byte type = headerBuf.get();
                long index = headerBuf.getLong();
                long term = headerBuf.getLong();
                int payloadLen = headerBuf.getInt();

                if (magic != MAGIC || version != VERSION || payloadLen < 0 || payloadLen > MAX_PAYLOAD_SIZE) break;

                ByteBuffer payloadBuf = ByteBuffer.allocate(payloadLen);
                if (ch.read(payloadBuf, pos + HEADER_SIZE) < payloadLen) break;
                payloadBuf.flip();

                ByteBuffer crcBuf = ByteBuffer.allocate(CRC_SIZE);
                if (ch.read(crcBuf, pos + HEADER_SIZE + payloadLen) < CRC_SIZE) break;
                crcBuf.flip();
                int storedCrc = crcBuf.getInt();

                CRC32C crc = new CRC32C();
                headerBuf.rewind();
                crc.update(headerBuf);
                crc.update(payloadBuf.duplicate());
                if ((int) crc.getValue() != storedCrc) break;

                if (type == TYPE_TRUNCATE) {
                    long truncateFrom = index;
                    result.removeIf(e -> e.index() >= truncateFrom);
                } else if (type == TYPE_APPEND) {
                    byte[] payload = new byte[payloadLen];
                    payloadBuf.get(payload);
                    result.add(new LogEntryData(index, term, payload));
                }

                pos += HEADER_SIZE + payloadLen + CRC_SIZE;
            }
        }
        return result;
    }

    /**
     * Writes a record to a specific FileChannel (used during log rewrite).
     */
    private void writeRecordToChannel(FileChannel ch, byte type, long index, long term, byte[] payload)
            throws IOException {
        byte[] safePayload = payload != null ? payload : new byte[0];
        int totalSize = HEADER_SIZE + safePayload.length + CRC_SIZE;
        ByteBuffer buf = ByteBuffer.allocate(totalSize);

        buf.putInt(MAGIC);
        buf.putShort(VERSION);
        buf.put(type);
        buf.putLong(index);
        buf.putLong(term);
        buf.putInt(safePayload.length);
        buf.put(safePayload);

        CRC32C crc = new CRC32C();
        crc.update(buf.array(), 0, HEADER_SIZE + safePayload.length);
        buf.putInt((int) crc.getValue());
        buf.flip();

        while (buf.hasRemaining()) {
            ch.write(buf);
        }
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    /**
     * Writes a record to the WAL.
     *
     * <p>Record format:</p>
     * <pre>
     * | magic(4) | version(2) | type(1) | index(8) | term(8) | payloadLen(4) | payload | crc(4) |
     * </pre>
     */
    private void writeRecord(byte type, long index, long term, byte[] payload) throws IOException {
        byte[] safePayload = payload != null ? payload : new byte[0];
        int totalSize = HEADER_SIZE + safePayload.length + CRC_SIZE;

        ByteBuffer buf = ByteBuffer.allocate(totalSize);

        // Write header
        buf.putInt(MAGIC);
        buf.putShort(VERSION);
        buf.put(type);
        buf.putLong(index);
        buf.putLong(term);
        buf.putInt(safePayload.length);
        buf.put(safePayload);

        // Calculate and write CRC over header + payload
        CRC32C crc = new CRC32C();
        crc.update(buf.array(), 0, HEADER_SIZE + safePayload.length);
        buf.putInt((int) crc.getValue());

        buf.flip();

        // Write to channel
        while (buf.hasRemaining()) {
            logChannel.write(buf);
        }
    }

    /**
     * Syncs a directory to ensure renames are durable.
     *
     * <p>On Windows, this is a no-op (directory sync not supported).
     * On Linux, this is critical for durability on ext4/xfs.</p>
     */
    private void syncDirectory(Path dir) throws IOException {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            // Windows doesn't support directory fsync
            return;
        }
        try (FileChannel dirChannel = FileChannel.open(dir, StandardOpenOption.READ)) {
            dirChannel.force(true);
        }
    }
}
