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
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InMemoryRaftStorage}.
 *
 * <p>These tests verify the in-memory storage implementation and its
 * test helper methods for simulating failures.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-01-29
 */
@ExtendWith(VertxExtension.class)
class InMemoryRaftStorageTest {

    private InMemoryRaftStorage storage;

    @BeforeEach
    void setUp(VertxTestContext ctx) {
        storage = new InMemoryRaftStorage();
        storage.open(Path.of("/dummy/path"))
                .onComplete(ctx.succeedingThenComplete());
    }

    @AfterEach
    void tearDown() {
        if (storage != null) {
            storage.close();
        }
    }

    // =========================================================================
    // Basic Operations
    // =========================================================================

    @Test
    @DisplayName("metadata operations work correctly")
    void metadataOperations(VertxTestContext ctx) {
        storage.updateMetadata(10L, Optional.of("candidate-1"))
                .compose(v -> storage.loadMetadata())
                .onComplete(ctx.succeeding(meta -> {
                    assertEquals(10L, meta.currentTerm());
                    assertEquals(Optional.of("candidate-1"), meta.votedFor());
                    ctx.completeNow();
                }));
    }

    @Test
    @DisplayName("log operations work correctly")
    void logOperations(VertxTestContext ctx) {
        List<LogEntryData> entries = List.of(
                new LogEntryData(1, 1, "cmd-1".getBytes()),
                new LogEntryData(2, 1, "cmd-2".getBytes())
        );

        storage.appendEntries(entries)
                .compose(v -> storage.replayLog())
                .onComplete(ctx.succeeding(replayed -> {
                    assertEquals(2, replayed.size());
                    assertEquals(1, replayed.get(0).index());
                    assertEquals(2, replayed.get(1).index());
                    ctx.completeNow();
                }));
    }

    @Test
    @DisplayName("truncateSuffix removes correct entries")
    void truncateSuffix(VertxTestContext ctx) {
        List<LogEntryData> entries = List.of(
                new LogEntryData(1, 1, "keep".getBytes()),
                new LogEntryData(2, 1, "remove".getBytes()),
                new LogEntryData(3, 1, "remove".getBytes())
        );

        storage.appendEntries(entries)
                .compose(v -> storage.truncateSuffix(2))
                .compose(v -> storage.replayLog())
                .onComplete(ctx.succeeding(replayed -> {
                    assertEquals(1, replayed.size());
                    assertEquals(1, replayed.get(0).index());
                    ctx.completeNow();
                }));
    }

    // =========================================================================
    // Failure Simulation Tests
    // =========================================================================

    @Test
    @DisplayName("setFailOnSync causes sync to fail")
    void setFailOnSync_causesSyncFailure(VertxTestContext ctx) {
        storage.setFailOnSync(true);

        storage.sync()
                .onComplete(ctx.failing(err -> {
                    assertTrue(err.getMessage().contains("Simulated sync failure"));
                    ctx.completeNow();
                }));
    }

    @Test
    @DisplayName("setFailOnAppend causes append to fail")
    void setFailOnAppend_causesAppendFailure(VertxTestContext ctx) {
        storage.setFailOnAppend(true);

        storage.appendEntries(List.of(new LogEntryData(1, 1, "test".getBytes())))
                .onComplete(ctx.failing(err -> {
                    assertTrue(err.getMessage().contains("Simulated append failure"));
                    ctx.completeNow();
                }));
    }

    @Test
    @DisplayName("setFailOnMetadataUpdate causes metadata update to fail")
    void setFailOnMetadataUpdate_causesMetadataFailure(VertxTestContext ctx) {
        storage.setFailOnMetadataUpdate(true);

        storage.updateMetadata(5L, Optional.of("node-1"))
                .onComplete(ctx.failing(err -> {
                    assertTrue(err.getMessage().contains("Simulated metadata update failure"));
                    ctx.completeNow();
                }));
    }

    // =========================================================================
    // Test Helper Methods
    // =========================================================================

    @Test
    @DisplayName("getLog returns unmodifiable view")
    void getLog_returnsUnmodifiableView(VertxTestContext ctx) {
        List<LogEntryData> entries = List.of(new LogEntryData(1, 1, "test".getBytes()));

        storage.appendEntries(entries)
                .onComplete(ctx.succeeding(v -> {
                    List<LogEntryData> log = storage.getLog();
                    assertEquals(1, log.size());
                    
                    // Should throw on modification attempt
                    assertThrows(UnsupportedOperationException.class, () -> 
                            log.add(new LogEntryData(2, 1, "illegal".getBytes())));
                    
                    ctx.completeNow();
                }));
    }

    @Test
    @DisplayName("reset clears all state")
    void reset_clearsAllState(VertxTestContext ctx) {
        storage.updateMetadata(10L, Optional.of("node-1"))
                .compose(v -> storage.appendEntries(List.of(new LogEntryData(1, 1, "test".getBytes()))))
                .onComplete(ctx.succeeding(v -> {
                    storage.setFailOnSync(true);
                    
                    // Reset
                    storage.reset();
                    
                    // Verify everything is cleared
                    assertEquals(0L, storage.getCurrentTerm());
                    assertTrue(storage.getVotedFor().isEmpty());
                    assertTrue(storage.getLog().isEmpty());
                    
                    // Verify failure flags are reset
                    storage.sync().onComplete(ctx.succeeding(v2 -> ctx.completeNow()));
                }));
    }

    @Test
    @DisplayName("isOpen returns correct state")
    void isOpen_returnsCorrectState() {
        assertTrue(storage.isOpen());
        
        storage.close();
        
        assertFalse(storage.isOpen());
    }

    @Test
    @DisplayName("operations fail after close")
    void operations_failAfterClose(VertxTestContext ctx) {
        storage.close();

        storage.appendEntries(List.of(new LogEntryData(1, 1, "test".getBytes())))
                .onComplete(ctx.failing(err -> {
                    assertTrue(err.getMessage().contains("not open") || 
                               err.getMessage().contains("closed"));
                    ctx.completeNow();
                }));
    }

    @Test
    @DisplayName("saveSnapshot and loadSnapshot round-trip")
    void saveAndLoadSnapshot(VertxTestContext ctx) {
        byte[] data = "snapshot-data-payload".getBytes();
        storage.saveSnapshot(data, 42, 3)
                .compose(v -> storage.loadSnapshot())
                .onComplete(ctx.succeeding(opt -> {
                    assertTrue(opt.isPresent(), "Snapshot should be present after save");
                    var snapshot = opt.get();
                    assertEquals(42, snapshot.lastIncludedIndex());
                    assertEquals(3, snapshot.lastIncludedTerm());
                    assertArrayEquals(data, snapshot.data());
                    ctx.completeNow();
                }));
    }

    @Test
    @DisplayName("loadSnapshot returns empty when no snapshot saved")
    void loadSnapshotEmpty(VertxTestContext ctx) {
        storage.loadSnapshot()
                .onComplete(ctx.succeeding(opt -> {
                    assertTrue(opt.isEmpty(), "No snapshot should be present initially");
                    ctx.completeNow();
                }));
    }

    @Test
    @DisplayName("truncatePrefix removes entries up to given index")
    void truncatePrefix(VertxTestContext ctx) {
        storage.appendEntries(List.of(
                new LogEntryData(1, 1, "a".getBytes()),
                new LogEntryData(2, 1, "b".getBytes()),
                new LogEntryData(3, 2, "c".getBytes()),
                new LogEntryData(4, 2, "d".getBytes())
        )).compose(v -> storage.truncatePrefix(2))
          .onComplete(ctx.succeeding(v -> {
              // Entries 1 and 2 should be removed, 3 and 4 remain
              List<LogEntryData> remaining = storage.getLog();
              assertEquals(2, remaining.size());
              assertEquals(3, remaining.get(0).index());
              assertEquals(4, remaining.get(1).index());
              ctx.completeNow();
          }));
    }

    @Test
    @DisplayName("reset clears snapshot state")
    void resetClearsSnapshot(VertxTestContext ctx) {
        storage.saveSnapshot("data".getBytes(), 10, 2)
                .onComplete(ctx.succeeding(v -> {
                    storage.reset();
                    storage.loadSnapshot().onComplete(ctx.succeeding(opt -> {
                        assertTrue(opt.isEmpty(), "Snapshot should be cleared after reset");
                        ctx.completeNow();
                    }));
                }));
    }
}
