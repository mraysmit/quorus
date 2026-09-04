/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.raft.storage;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.zip.CRC32C;

/** Atomic snapshot sidecar; the log itself remains exclusively owned by raftlog-core. */
final class FileSnapshotStore {
    private static final int MAGIC = 0x51534e50; // QSNP
    private static final int VERSION = 1;
    private static final int OVERHEAD = 32;
    private final Vertx vertx;
    private final Path directory;
    private Future<Void> pending = Future.succeededFuture();

    FileSnapshotStore(Vertx vertx, Path directory) {
        this.vertx = vertx;
        this.directory = directory;
    }

    Future<Void> save(byte[] data, long index, long term) {
        byte[] copy = data.clone();
        new RaftStorage.SnapshotData(copy, index, term);
        return ordered(() -> {
            Optional<RaftStorage.SnapshotData> previous = read();
            if (previous.isPresent() && (index < previous.get().lastIncludedIndex()
                    || term < previous.get().lastIncludedTerm())) {
                throw new IOException("Snapshot coordinates must not regress");
            }
            ByteBuffer buffer = ByteBuffer.allocate(Math.addExact(copy.length, OVERHEAD));
            buffer.putInt(MAGIC).putInt(VERSION).putLong(index).putLong(term).putInt(copy.length).put(copy);
            CRC32C crc = new CRC32C();
            crc.update(buffer.array(), 0, buffer.position());
            buffer.putInt((int) crc.getValue()).flip();
            publish("snapshot.dat", buffer);
            return null;
        });
    }

    private void publish(String name, ByteBuffer buffer) throws IOException {
        Path temporary = directory.resolve(name + ".tmp");
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        Files.move(temporary, directory.resolve(name),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        // Match the existing WAL design: Windows is development-only for directory durability.
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")) {
            try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
                channel.force(true);
            }
        }
    }

    Future<Optional<RaftStorage.SnapshotData>> load() {
        return ordered(this::read);
    }

    /** Persist the recovery dependency before the WAL is allowed to delete any covered entries. */
    Future<Void> requireSnapshot(long index) {
        return ordered(() -> {
            RaftStorage.SnapshotData saved = read().orElseThrow(
                    () -> new IOException("Cannot compact without a durable snapshot"));
            if (index > saved.lastIncludedIndex()) {
                throw new IOException("Cannot compact beyond the durable snapshot boundary");
            }
            long boundary = Math.max(index, requiredIndex());
            ByteBuffer marker = ByteBuffer.allocate(12).putLong(boundary);
            CRC32C crc = new CRC32C();
            crc.update(marker.array(), 0, Long.BYTES);
            marker.putInt((int) crc.getValue()).flip();
            publish("snapshot.required", marker);
            return null;
        });
    }

    private long requiredIndex() throws IOException {
        Path marker = directory.resolve("snapshot.required");
        if (!Files.exists(marker)) return -1;
        byte[] raw = Files.readAllBytes(marker);
        if (raw.length != 12) throw new IOException("Invalid snapshot dependency marker");
        CRC32C crc = new CRC32C();
        crc.update(raw, 0, Long.BYTES);
        ByteBuffer buffer = ByteBuffer.wrap(raw);
        long index = buffer.getLong();
        if (index < 0 || buffer.getInt() != (int) crc.getValue()) {
            throw new IOException("Invalid snapshot dependency marker");
        }
        return index;
    }

    private Optional<RaftStorage.SnapshotData> read() throws IOException {
        long required = requiredIndex();
        Path snapshot = directory.resolve("snapshot.dat");
        if (!Files.exists(snapshot)) {
            if (required >= 0) throw new IOException("Required snapshot is missing after WAL compaction");
            return Optional.empty();
        }
        byte[] raw = Files.readAllBytes(snapshot);
        if (raw.length < 24) throw new IOException("Invalid snapshot: truncated header");
        ByteBuffer buffer = ByteBuffer.wrap(raw);
        int overhead = 24; // Legacy snapshot.dat: index, term, length, payload, CRC32C.
        if (buffer.getInt(0) == MAGIC) {
            if (raw.length < OVERHEAD || buffer.getInt(4) != VERSION) {
                throw new IOException("Invalid snapshot: unsupported format/version");
            }
            buffer.position(8);
            overhead = OVERHEAD;
        }
        long index = buffer.getLong();
        long term = buffer.getLong();
        int length = buffer.getInt();
        if (length <= 0 || length != raw.length - overhead || index < 0 || term < 0 || index < required) {
            throw new IOException("Invalid snapshot: length or coordinates");
        }
        CRC32C crc = new CRC32C();
        crc.update(raw, 0, raw.length - Integer.BYTES);
        if ((int) crc.getValue() != buffer.getInt(raw.length - Integer.BYTES)) {
            throw new IOException("Invalid snapshot: checksum mismatch");
        }
        byte[] data = new byte[length];
        buffer.get(data);
        return Optional.of(new RaftStorage.SnapshotData(data, index, term));
    }

    synchronized Future<Void> drain() {
        return pending;
    }

    private synchronized <T> Future<T> ordered(Callable<T> operation) {
        Future<T> result = pending.transform(ignored -> vertx.executeBlocking(operation, false));
        pending = result.<Void>mapEmpty().otherwiseEmpty();
        return result;
    }
}
