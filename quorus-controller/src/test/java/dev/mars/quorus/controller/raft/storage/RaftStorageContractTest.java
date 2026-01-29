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

package dev.mars.quorus.controller.raft.storage;

import dev.mars.quorus.controller.raft.storage.RaftStorage.LogEntryData;
import dev.mars.quorus.controller.raft.storage.RaftStorage.PersistentMeta;
import dev.mars.quorus.controller.raft.storage.file.FileRaftStorage;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for {@link RaftStorage} implementations.
 *
 * <p>These tests verify that all storage implementations correctly implement
 * the RaftStorage interface contract, including durability and atomicity guarantees.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-01-29
 */
@ExtendWith(VertxExtension.class)
class RaftStorageContractTest {

    @TempDir
    Path tempDir;

    private Vertx vertx;
    private WorkerExecutor executor;
    private RaftStorage storage;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext ctx) {
        this.vertx = vertx;
        this.executor = vertx.createSharedWorkerExecutor("wal-test", 1);
        this.storage = new FileRaftStorage(vertx, executor);

        storage.open(tempDir)
                .onComplete(ctx.succeedingThenComplete());
    }

    @AfterEach
    void tearDown() {
        if (storage != null) {
            storage.close();
        }
        if (executor != null) {
            executor.close();
        }
    }

    // =========================================================================
    // Metadata Tests
    // =========================================================================

    @Test
    @DisplayName("loadMetadata returns empty on fresh storage")
    void loadMetadata_freshStorage_returnsEmpty(VertxTestContext ctx) {
        storage.loadMetadata()
                .onComplete(ctx.succeeding(meta -> {
                    assertEquals(0L, meta.currentTerm());
                    assertTrue(meta.votedFor().isEmpty());
                    ctx.completeNow();
                }));
    }

    @Test
    @DisplayName("updateMetadata persists term and votedFor")
    void updateMetadata_persistsData(VertxTestContext ctx) {
        storage.updateMetadata(5L, Optional.of("node-1"))
                .compose(v -> storage.loadMetadata())
                .onComplete(ctx.succeeding(meta -> {
                    assertEquals(5L, meta.currentTerm());
                    assertEquals(Optional.of("node-1"), meta.votedFor());
                    ctx.completeNow();
                }));
    }

    @Test
    @DisplayName("updateMetadata with empty votedFor clears previous vote")
    void updateMetadata_emptyVote_clearsPreviousVote(VertxTestContext ctx) {
        storage.updateMetadata(5L, Optional.of("node-1"))
                .compose(v -> storage.updateMetadata(6L, Optional.empty()))
                .compose(v -> storage.loadMetadata())
                .onComplete(ctx.succeeding(meta -> {
                    assertEquals(6L, meta.currentTerm());
                    assertTrue(meta.votedFor().isEmpty());
                    ctx.completeNow();
                }));
    }

    @Test
    @DisplayName("metadata survives close and reopen")
    void metadata_survivesReopen(VertxTestContext ctx) {
        storage.updateMetadata(10L, Optional.of("leader-node"))
                .compose(v -> {
                    storage.close();
                    RaftStorage newStorage = new FileRaftStorage(vertx, executor);
                    return newStorage.open(tempDir)
                            .compose(v2 -> newStorage.loadMetadata())
                            .onComplete(ar -> newStorage.close());
                })
                .onComplete(ctx.succeeding(meta -> {
                    assertEquals(10L, meta.currentTerm());
                    assertEquals(Optional.of("leader-node"), meta.votedFor());
                    ctx.completeNow();
                }));
    }

    // =========================================================================
    // Log Append Tests
    // =========================================================================

    @Test
    @DisplayName("appendEntries stores entries that can be replayed")
    void appendEntries_storesEntries(VertxTestContext ctx) {
        List<LogEntryData> entries = List.of(
                new LogEntryData(1, 1, "command-1".getBytes(StandardCharsets.UTF_8)),
                new LogEntryData(2, 1, "command-2".getBytes(StandardCharsets.UTF_8)),
                new LogEntryData(3, 2, "command-3".getBytes(StandardCharsets.UTF_8))
        );

        storage.appendEntries(entries)
                .compose(v -> storage.sync())
                .compose(v -> storage.replayLog())
                .onComplete(ctx.succeeding(replayed -> {
                    assertEquals(3, replayed.size());

                    assertEquals(1, replayed.get(0).index());
                    assertEquals(1, replayed.get(0).term());
                    assertArrayEquals("command-1".getBytes(), replayed.get(0).payload());

                    assertEquals(2, replayed.get(1).index());
                    assertEquals(3, replayed.get(2).index());
                    assertEquals(2, replayed.get(2).term());

                    ctx.completeNow();
                }));
    }

    @Test
    @DisplayName("appendEntries with empty list is no-op")
    void appendEntries_emptyList_isNoOp(VertxTestContext ctx) {
        storage.appendEntries(List.of())
                .compose(v -> storage.replayLog())
                .onComplete(ctx.succeeding(replayed -> {
                    assertTrue(replayed.isEmpty());
                    ctx.completeNow();
                }));
    }

    @Test
    @DisplayName("entries survive close and reopen")
    void entries_surviveReopen(VertxTestContext ctx) {
        List<LogEntryData> entries = List.of(
                new LogEntryData(1, 5, "persistent-data".getBytes(StandardCharsets.UTF_8))
        );

        storage.appendEntries(entries)
                .compose(v -> storage.sync())
                .compose(v -> {
                    storage.close();
                    RaftStorage newStorage = new FileRaftStorage(vertx, executor);
                    return newStorage.open(tempDir)
                            .compose(v2 -> newStorage.replayLog())
                            .onComplete(ar -> newStorage.close());
                })
                .onComplete(ctx.succeeding(replayed -> {
                    assertEquals(1, replayed.size());
                    assertEquals(1, replayed.get(0).index());
                    assertEquals(5, replayed.get(0).term());
                    assertArrayEquals("persistent-data".getBytes(), replayed.get(0).payload());
                    ctx.completeNow();
                }));
    }

    // =========================================================================
    // Truncation Tests
    // =========================================================================

    @Test
    @DisplayName("truncateSuffix removes entries at and after index")
    void truncateSuffix_removesEntries(VertxTestContext ctx) {
        List<LogEntryData> entries = List.of(
                new LogEntryData(1, 1, "keep".getBytes()),
                new LogEntryData(2, 1, "keep".getBytes()),
                new LogEntryData(3, 1, "remove".getBytes()),
                new LogEntryData(4, 1, "remove".getBytes())
        );

        storage.appendEntries(entries)
                .compose(v -> storage.sync())
                .compose(v -> storage.truncateSuffix(3))
                .compose(v -> storage.sync())
                .compose(v -> storage.replayLog())
                .onComplete(ctx.succeeding(replayed -> {
                    assertEquals(2, replayed.size());
                    assertEquals(1, replayed.get(0).index());
                    assertEquals(2, replayed.get(1).index());
                    ctx.completeNow();
                }));
    }

    @Test
    @DisplayName("truncateSuffix then append replaces entries")
    void truncateSuffix_thenAppend_replacesEntries(VertxTestContext ctx) {
        List<LogEntryData> original = List.of(
                new LogEntryData(1, 1, "original-1".getBytes()),
                new LogEntryData(2, 1, "original-2".getBytes()),
                new LogEntryData(3, 1, "original-3".getBytes())
        );

        List<LogEntryData> replacement = List.of(
                new LogEntryData(2, 2, "replacement-2".getBytes()),
                new LogEntryData(3, 2, "replacement-3".getBytes())
        );

        storage.appendEntries(original)
                .compose(v -> storage.sync())
                .compose(v -> storage.truncateSuffix(2))
                .compose(v -> storage.appendEntries(replacement))
                .compose(v -> storage.sync())
                .compose(v -> storage.replayLog())
                .onComplete(ctx.succeeding(replayed -> {
                    assertEquals(3, replayed.size());

                    // First entry unchanged
                    assertEquals(1, replayed.get(0).index());
                    assertEquals(1, replayed.get(0).term());
                    assertArrayEquals("original-1".getBytes(), replayed.get(0).payload());

                    // Replaced entries have new term
                    assertEquals(2, replayed.get(1).index());
                    assertEquals(2, replayed.get(1).term());
                    assertArrayEquals("replacement-2".getBytes(), replayed.get(1).payload());

                    assertEquals(3, replayed.get(2).index());
                    assertEquals(2, replayed.get(2).term());

                    ctx.completeNow();
                }));
    }

    // =========================================================================
    // Torn Write Recovery Test
    // =========================================================================

    @Test
    @DisplayName("replayLog recovers from torn write (corrupt tail)")
    void replayLog_recoversFromTornWrite(VertxTestContext ctx) throws Exception {
        // Step 1: Write valid entries
        List<LogEntryData> entries = List.of(
                new LogEntryData(1, 1, "valid-1".getBytes()),
                new LogEntryData(2, 1, "valid-2".getBytes())
        );

        storage.appendEntries(entries)
                .compose(v -> storage.sync())
                .onComplete(ctx.succeeding(v -> {
                    try {
                        // Step 2: Close storage and corrupt the file
                        storage.close();

                        Path logPath = tempDir.resolve("raft.log");
                        
                        // Append partial/corrupt data (incomplete record)
                        try (FileChannel fc = FileChannel.open(logPath,
                                StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                            ByteBuffer garbage = ByteBuffer.allocate(10);
                            garbage.putInt(0x52414654); // Valid magic
                            garbage.putShort((short) 1); // Valid version
                            garbage.put((byte) 2);      // Type APPEND
                            // Missing: index, term, payload, CRC
                            garbage.flip();
                            fc.write(garbage);
                        }

                        // Step 3: Reopen and replay - should recover only valid entries
                        RaftStorage newStorage = new FileRaftStorage(vertx, executor);
                        newStorage.open(tempDir)
                                .compose(v2 -> newStorage.replayLog())
                                .onComplete(ar -> newStorage.close())
                                .onComplete(ctx.succeeding(replayed -> {
                                    assertEquals(2, replayed.size(),
                                            "Should recover only the 2 valid entries");
                                    assertEquals(1, replayed.get(0).index());
                                    assertEquals(2, replayed.get(1).index());
                                    ctx.completeNow();
                                }));
                    } catch (Exception e) {
                        ctx.failNow(e);
                    }
                }));
    }
}
