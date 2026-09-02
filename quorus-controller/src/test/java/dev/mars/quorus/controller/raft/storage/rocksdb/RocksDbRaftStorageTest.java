/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.raft.storage.rocksdb;

import dev.mars.quorus.controller.raft.storage.RaftStorage.LogEntryData;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Behavioral contract tests for the production RocksDB Raft storage adapter. */
@ExtendWith(VertxExtension.class)
class RocksDbRaftStorageTest {

    @TempDir
    Path tempDir;

    private WorkerExecutor executor;
    private RocksDbRaftStorage storage;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext context) {
        executor = vertx.createSharedWorkerExecutor("rocksdb-storage-test", 1);
        storage = new RocksDbRaftStorage(vertx, executor, tempDir, false);
        storage.open().onComplete(context.succeedingThenComplete());
    }

    @AfterEach
    void tearDown(VertxTestContext context) {
        storage.close().onComplete(result -> {
            executor.close();
            if (result.succeeded()) {
                context.completeNow();
            } else {
                context.failNow(result.cause());
            }
        });
    }

    @Test
    void metadataIsAtomicAndVoteCanBeCleared(VertxTestContext context) {
        storage.loadMetadata()
                .compose(fresh -> {
                    context.verify(() -> {
                        assertEquals(0, fresh.currentTerm());
                        assertTrue(fresh.votedFor().isEmpty());
                    });
                    return storage.updateMetadata(7, Optional.of("controller-b"));
                })
                .compose(ignored -> storage.loadMetadata())
                .compose(persisted -> {
                    context.verify(() -> {
                        assertEquals(7, persisted.currentTerm());
                        assertEquals(Optional.of("controller-b"), persisted.votedFor());
                    });
                    return storage.updateMetadata(8, Optional.empty());
                })
                .compose(ignored -> storage.loadMetadata())
                .onComplete(context.succeeding(cleared -> context.verify(() -> {
                    assertEquals(8, cleared.currentTerm());
                    assertTrue(cleared.votedFor().isEmpty());
                    context.completeNow();
                })));
    }

    @Test
    void logReplayHonoursSuffixAndPrefixTruncation(VertxTestContext context) {
        List<LogEntryData> entries = List.of(
                entry(1, 1, "one"), entry(2, 1, "two"), entry(3, 2, "three"));

        storage.appendEntries(List.of())
                .compose(ignored -> storage.appendEntries(entries))
                .compose(ignored -> storage.sync())
                .compose(ignored -> storage.replayLog())
                .compose(replayed -> {
                    context.verify(() -> {
                        assertEquals(List.of(1L, 2L, 3L), replayed.stream().map(LogEntryData::index).toList());
                        assertArrayEquals("three".getBytes(StandardCharsets.UTF_8), replayed.get(2).payload());
                    });
                    return storage.truncateSuffix(3);
                })
                .compose(ignored -> storage.replayLog())
                .compose(replayed -> {
                    context.verify(() -> assertEquals(List.of(1L, 2L),
                            replayed.stream().map(LogEntryData::index).toList()));
                    return storage.truncatePrefix(1);
                })
                .compose(ignored -> storage.replayLog())
                .onComplete(context.succeeding(replayed -> context.verify(() -> {
                    assertEquals(1, replayed.size());
                    assertEquals(2, replayed.getFirst().index());
                    context.completeNow();
                })));
    }

    @Test
    void snapshotRoundTripsWithRaftCoordinates(VertxTestContext context) {
        byte[] snapshot = "authoritative-state".getBytes(StandardCharsets.UTF_8);

        storage.loadSnapshot()
                .compose(empty -> {
                    context.verify(() -> assertTrue(empty.isEmpty()));
                    return storage.saveSnapshot(snapshot, 42, 9);
                })
                .compose(ignored -> storage.loadSnapshot())
                .onComplete(context.succeeding(loaded -> context.verify(() -> {
                    assertTrue(loaded.isPresent());
                    assertArrayEquals(snapshot, loaded.orElseThrow().data());
                    assertEquals(42, loaded.orElseThrow().lastIncludedIndex());
                    assertEquals(9, loaded.orElseThrow().lastIncludedTerm());
                    context.completeNow();
                })));
    }

    private static LogEntryData entry(long index, long term, String payload) {
        return new LogEntryData(index, term, payload.getBytes(StandardCharsets.UTF_8));
    }
}
