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

import dev.mars.raftlog.storage.RaftStorageConfig;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RaftLogStorageAdapter} which wraps the raftlog-core library.
 * 
 * <p>These tests run with {@code syncEnabled=true} (production configuration)
 * to ensure the fsync path is properly tested.</p>
 */
@ExtendWith(VertxExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RaftLogStorageAdapterTest {

    @TempDir
    Path tempDir;

    private RaftLogStorageAdapter storage;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext ctx) {
        // Use production config with fsync ENABLED
        RaftStorageConfig config = RaftStorageConfig.builder()
                .dataDir(tempDir)
                .syncEnabled(true)  // Production setting - tests real durability path
                .build();
        
        storage = new RaftLogStorageAdapter(vertx, config);
        storage.open(tempDir)
                .onComplete(ctx.succeedingThenComplete());
    }

    @AfterEach
    void tearDown(VertxTestContext ctx) {
        if (storage != null) {
            storage.close()
                    .onComplete(ctx.succeedingThenComplete());
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should persist and load metadata")
    void testMetadataPersistence(VertxTestContext ctx) {
        storage.updateMetadata(5, Optional.of("node-2"))
                .compose(v -> storage.loadMetadata())
                .onComplete(ctx.succeeding(meta -> {
                    ctx.verify(() -> {
                        assertEquals(5, meta.currentTerm());
                        assertEquals(Optional.of("node-2"), meta.votedFor());
                    });
                    ctx.completeNow();
                }));
    }

    @Test
    @Order(2)
    @DisplayName("Should append and replay log entries")
    void testAppendAndReplay(VertxTestContext ctx) {
        List<RaftStorage.LogEntryData> entries = List.of(
                new RaftStorage.LogEntryData(1, 1, "cmd1".getBytes()),
                new RaftStorage.LogEntryData(2, 1, "cmd2".getBytes()),
                new RaftStorage.LogEntryData(3, 2, "cmd3".getBytes())
        );

        storage.appendEntries(entries)
                .compose(v -> storage.sync())
                .compose(v -> storage.replayLog())
                .onComplete(ctx.succeeding(replayed -> {
                    ctx.verify(() -> {
                        assertEquals(3, replayed.size());
                        assertEquals(1, replayed.get(0).index());
                        assertEquals(2, replayed.get(1).index());
                        assertEquals(3, replayed.get(2).index());
                        assertArrayEquals("cmd1".getBytes(), replayed.get(0).payload());
                    });
                    ctx.completeNow();
                }));
    }

    @Test
    @Order(3)
    @DisplayName("Should truncate log suffix")
    void testTruncateSuffix(VertxTestContext ctx) {
        List<RaftStorage.LogEntryData> entries = List.of(
                new RaftStorage.LogEntryData(1, 1, "a".getBytes()),
                new RaftStorage.LogEntryData(2, 1, "b".getBytes()),
                new RaftStorage.LogEntryData(3, 1, "c".getBytes())
        );

        storage.appendEntries(entries)
                .compose(v -> storage.sync())
                .compose(v -> storage.truncateSuffix(2))  // Remove index 2 and 3
                .compose(v -> storage.sync())
                .compose(v -> storage.replayLog())
                .onComplete(ctx.succeeding(replayed -> {
                    ctx.verify(() -> {
                        assertEquals(1, replayed.size());
                        assertEquals(1, replayed.get(0).index());
                    });
                    ctx.completeNow();
                }));
    }

    @Test
    @Order(4)
    @DisplayName("Should return empty metadata when no state exists")
    void testEmptyMetadata(Vertx vertx, VertxTestContext ctx) {
        // Create new storage in a fresh directory with production settings
        Path freshDir = tempDir.resolve("fresh");
        RaftStorageConfig config = RaftStorageConfig.builder()
                .dataDir(freshDir)
                .syncEnabled(true)  // Production setting
                .build();
        RaftLogStorageAdapter freshStorage = new RaftLogStorageAdapter(vertx, config);

        freshStorage.open(freshDir)
                .compose(v -> freshStorage.loadMetadata())
                .onComplete(ctx.succeeding(meta -> {
                    ctx.verify(() -> {
                        assertEquals(0, meta.currentTerm());
                        assertTrue(meta.votedFor().isEmpty());
                    });
                    freshStorage.close()
                            .onComplete(ctx.succeedingThenComplete());
                }));
    }

    @Test
    @Order(5)
    @DisplayName("Factory should create RaftLogStorageAdapter with fsync enabled")
    void testFactoryCreatesRaftLogWithFsync(Vertx vertx, VertxTestContext ctx) {
        Path factoryDir = tempDir.resolve("factory");
        
        // Test with fsync=true (production setting)
        RaftStorageFactory.create(vertx, "raftlog", factoryDir, true)
                .onComplete(ctx.succeeding(createdStorage -> {
                    ctx.verify(() -> {
                        assertInstanceOf(RaftLogStorageAdapter.class, createdStorage);
                    });
                    createdStorage.close()
                            .onComplete(ctx.succeedingThenComplete());
                }));
    }

    @Test
    @Order(6)
    @DisplayName("Factory should default to raftlog with fsync when no type specified")
    void testFactoryDefaultsToRaftLogWithFsync(Vertx vertx, VertxTestContext ctx) {
        Path factoryDir = tempDir.resolve("default");
        
        // Pass empty type and fsync=true to test production default behavior
        RaftStorageFactory.create(vertx, "", factoryDir, true)
                .onComplete(ctx.succeeding(createdStorage -> {
                    ctx.verify(() -> {
                        assertInstanceOf(RaftLogStorageAdapter.class, createdStorage);
                    });
                    createdStorage.close()
                            .onComplete(ctx.succeedingThenComplete());
                }));
    }

    // =========================================================================
    // Tests with syncEnabled=false (testing the no-fsync code path)
    // =========================================================================

    @Nested
    @DisplayName("With syncEnabled=false")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class SyncDisabledTests {

        @TempDir
        Path noSyncTempDir;

        private RaftLogStorageAdapter noSyncStorage;

        @BeforeEach
        void setUp(Vertx vertx, VertxTestContext ctx) {
            RaftStorageConfig config = RaftStorageConfig.builder()
                    .dataDir(noSyncTempDir)
                    .syncEnabled(false)  // Test the no-fsync path
                    .build();

            noSyncStorage = new RaftLogStorageAdapter(vertx, config);
            noSyncStorage.open(noSyncTempDir)
                    .onComplete(ctx.succeedingThenComplete());
        }

        @AfterEach
        void tearDown(VertxTestContext ctx) {
            if (noSyncStorage != null) {
                noSyncStorage.close()
                        .onComplete(ctx.succeedingThenComplete());
            }
        }

        @Test
        @Order(1)
        @DisplayName("Should persist and load metadata without fsync")
        void testMetadataPersistenceNoSync(VertxTestContext ctx) {
            noSyncStorage.updateMetadata(10, Optional.of("node-x"))
                    .compose(v -> noSyncStorage.loadMetadata())
                    .onComplete(ctx.succeeding(meta -> {
                        ctx.verify(() -> {
                            assertEquals(10, meta.currentTerm());
                            assertEquals(Optional.of("node-x"), meta.votedFor());
                        });
                        ctx.completeNow();
                    }));
        }

        @Test
        @Order(2)
        @DisplayName("Should append and replay entries without fsync")
        void testAppendAndReplayNoSync(VertxTestContext ctx) {
            List<RaftStorage.LogEntryData> entries = List.of(
                    new RaftStorage.LogEntryData(1, 1, "fast1".getBytes()),
                    new RaftStorage.LogEntryData(2, 1, "fast2".getBytes())
            );

            noSyncStorage.appendEntries(entries)
                    .compose(v -> noSyncStorage.sync())  // sync() is a no-op when disabled
                    .compose(v -> noSyncStorage.replayLog())
                    .onComplete(ctx.succeeding(replayed -> {
                        ctx.verify(() -> {
                            assertEquals(2, replayed.size());
                            assertEquals(1, replayed.get(0).index());
                            assertEquals(2, replayed.get(1).index());
                        });
                        ctx.completeNow();
                    }));
        }

        @Test
        @Order(3)
        @DisplayName("Factory should create storage with fsync disabled")
        void testFactoryWithFsyncDisabled(Vertx vertx, VertxTestContext ctx) {
            Path factoryDir = noSyncTempDir.resolve("factory-nosync");

            RaftStorageFactory.create(vertx, "raftlog", factoryDir, false)
                    .onComplete(ctx.succeeding(createdStorage -> {
                        ctx.verify(() -> {
                            assertInstanceOf(RaftLogStorageAdapter.class, createdStorage);
                        });
                        createdStorage.close()
                                .onComplete(ctx.succeedingThenComplete());
                    }));
        }
    }
}
