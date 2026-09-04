/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.raft.storage;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32C;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class SnapshotRecoveryBoundaryTest {
    @TempDir Path directory;
    private Vertx vertx;
    private RaftStorage storage;
    private static final byte[] STATE = "committed-bank-ledger".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void open(Vertx vertx, VertxTestContext ctx) {
        this.vertx = vertx;
        reopen().onComplete(ctx.succeedingThenComplete());
    }

    private Future<Void> reopen() {
        return RaftStorageFactory.create(vertx, "raftlog", directory, true)
                .onSuccess(value -> storage = value).mapEmpty();
    }

    @AfterEach
    void close(VertxTestContext ctx) {
        storage.close().onComplete(ctx.succeedingThenComplete());
    }

    @Test
    void compactionWithoutSnapshotMustNotRemoveWal(VertxTestContext ctx) {
        storage.appendEntries(List.of(new RaftStorage.LogEntryData(1, 2, STATE)))
                .compose(v -> storage.sync())
                .compose(v -> storage.truncatePrefix(1).transform(result -> {
                    ctx.verify(() -> assertTrue(result.failed(), "Compaction requires a durable covering snapshot"));
                    return storage.replayLog();
                }))
                .onComplete(ctx.succeeding(log -> ctx.verify(() -> {
                    assertEquals(List.of(1L), log.stream().map(RaftStorage.LogEntryData::index).toList());
                    ctx.completeNow();
                })));
    }

    @Test
    void missingSnapshotAfterCompleteCompactionFailsClosed(VertxTestContext ctx) {
        storage.appendEntries(List.of(new RaftStorage.LogEntryData(1, 2, STATE)))
                .compose(v -> storage.saveSnapshot(STATE, 1, 2))
                .compose(v -> storage.truncatePrefix(1))
                .compose(v -> storage.close())
                .compose(v -> vertx.fileSystem().delete(directory.resolve("snapshot.dat").toString()))
                .compose(v -> reopen())
                .compose(v -> storage.loadSnapshot())
                .onComplete(ctx.failing(cause -> ctx.verify(() -> {
                    assertTrue(cause.getMessage().contains("snapshot"));
                    ctx.completeNow();
                })));
    }

    @Test
    void olderSnapshotCannotReplaceRecoveryBaseline(VertxTestContext ctx) {
        storage.saveSnapshot(STATE, 5, 2)
                .compose(v -> storage.saveSnapshot("old".getBytes(StandardCharsets.UTF_8), 3, 1)
                        .transform(result -> {
                            ctx.verify(() -> assertTrue(result.failed(), "Snapshot coordinates must never regress"));
                            return storage.close();
                        }))
                .compose(v -> reopen())
                .compose(v -> storage.loadSnapshot())
                .onComplete(ctx.succeeding(snapshot -> ctx.verify(() -> {
                    assertEquals(5, snapshot.orElseThrow().lastIncludedIndex());
                    assertArrayEquals(STATE, snapshot.orElseThrow().data());
                    ctx.completeNow();
                })));
    }

    @Test
    void legacySnapshotFromRemovedStorageRemainsReadable(VertxTestContext ctx) {
        ByteBuffer legacy = ByteBuffer.allocate(24 + STATE.length);
        legacy.putLong(5).putLong(2).putInt(STATE.length).put(STATE);
        CRC32C checksum = new CRC32C();
        checksum.update(legacy.array(), 0, legacy.position());
        legacy.putInt((int) checksum.getValue());
        vertx.fileSystem().writeFile(directory.resolve("snapshot.dat").toString(), Buffer.buffer(legacy.array()))
                .compose(v -> storage.loadSnapshot())
                .onComplete(ctx.succeeding(snapshot -> ctx.verify(() -> {
                    assertEquals(5, snapshot.orElseThrow().lastIncludedIndex());
                    assertEquals(2, snapshot.orElseThrow().lastIncludedTerm());
                    assertArrayEquals(STATE, snapshot.orElseThrow().data());
                    ctx.completeNow();
                })));
    }

    @Test
    void interruptedTemporaryWriteKeepsPublishedSnapshot(VertxTestContext ctx) {
        storage.saveSnapshot(STATE, 5, 2)
                .compose(v -> storage.close())
                .compose(v -> vertx.fileSystem().writeFile(directory.resolve("snapshot.dat.tmp").toString(),
                        Buffer.buffer("interrupted-write")))
                .compose(v -> reopen())
                .compose(v -> storage.loadSnapshot())
                .onComplete(ctx.succeeding(snapshot -> ctx.verify(() -> {
                    assertArrayEquals(STATE, snapshot.orElseThrow().data());
                    ctx.completeNow();
                })));
    }

    @Test
    void failedSnapshotPublicationPreservesOldStateAndWal(VertxTestContext ctx) {
        storage.appendEntries(List.of(new RaftStorage.LogEntryData(6, 2, STATE)))
                .compose(v -> storage.saveSnapshot(STATE, 5, 2))
                .compose(v -> vertx.fileSystem().mkdir(directory.resolve("snapshot.dat.tmp").toString()))
                .compose(v -> storage.saveSnapshot("new".getBytes(StandardCharsets.UTF_8), 6, 2)
                        .transform(result -> {
                            ctx.verify(() -> assertTrue(result.failed()));
                            return storage.loadSnapshot();
                        }))
                .compose(snapshot -> {
                    ctx.verify(() -> assertEquals(5, snapshot.orElseThrow().lastIncludedIndex()));
                    return storage.replayLog();
                })
                .onComplete(ctx.succeeding(log -> ctx.verify(() -> {
                    assertEquals(6, log.getFirst().index());
                    ctx.completeNow();
                })));
    }

    @Test
    void corruptPublishedSnapshotIsNotTreatedAsFreshStorage(VertxTestContext ctx) {
        storage.saveSnapshot(STATE, 5, 2)
                .compose(v -> vertx.fileSystem().writeFile(directory.resolve("snapshot.dat").toString(),
                        Buffer.buffer("corrupt")))
                .compose(v -> storage.loadSnapshot())
                .onComplete(ctx.failingThenComplete());
    }
}
