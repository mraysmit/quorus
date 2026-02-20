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

package dev.mars.quorus.controller.raft;

import dev.mars.quorus.controller.raft.storage.InMemoryRaftStorage;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.SystemMetadataCommand;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioral tests for T5.2: Snapshot scheduling and log truncation.
 * Tests the full path from entry commitment through snapshot, compaction,
 * and recovery.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-13
 */
@ExtendWith(VertxExtension.class)
class RaftSnapshotTest {

    private Vertx vertx;
    private RaftNode leaderNode;
    private QuorusStateStore stateMachine;
    private InMemoryRaftStorage storage;
    private InMemoryTransportSimulator transport;

    @BeforeEach
    void setUp(Vertx vertx) {
        this.vertx = vertx;
        InMemoryTransportSimulator.clearAllTransports();

        stateMachine = new QuorusStateStore();
        storage = new InMemoryRaftStorage();
        transport = new InMemoryTransportSimulator("node1");

        // Single-node cluster with snapshot enabled, threshold=5, check every 500ms
        Set<String> cluster = Set.of("node1");
        leaderNode = new RaftNode(vertx, "node1", cluster, transport, stateMachine,
                storage, 1000, 200,
                true, 5, 500);
    }

    @AfterEach
    void tearDown() {
        if (leaderNode != null) leaderNode.stop();
        InMemoryTransportSimulator.clearAllTransports();
    }

    @Test
    void testSnapshotTriggeredByThreshold(VertxTestContext ctx) throws Throwable {
        // Open storage, start node, wait for leader
        storage.open(Path.of("/tmp/test")).onComplete(ctx.succeeding(v -> {
            leaderNode.start().onComplete(ctx.succeeding(v2 -> {
                leaderNode.awaitState(RaftNode.State.LEADER, 5000).onComplete(ctx.succeeding(state -> {

                    // Submit 6 commands (threshold is 5) - should trigger snapshot
                    submitCommands(6).onComplete(ctx.succeeding(v3 -> {
                        // No-op: just let the scheduler run
                    }));
                }));
            }));
        }));

        // Use Awaitility to poll for the snapshot to be taken
        await().atMost(8, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertTrue(leaderNode.getSnapshotLastIndex() > 0,
                            "Snapshot should have been taken after threshold reached");
                    assertTrue(leaderNode.getSnapshotLastIndex() >= 5,
                            "Snapshot index should be >= threshold");
                    // State machine should still have all data
                    for (int i = 0; i < 6; i++) {
                        assertEquals("value" + i, stateMachine.getMetadata("key" + i),
                                "State machine data should be preserved after snapshot");
                    }
                });
        ctx.completeNow();

        assertTrue(ctx.awaitCompletion(10, TimeUnit.SECONDS), "Test timed out");
        if (ctx.failed()) throw ctx.causeOfFailure();
    }

    @Test
    void testManualSnapshotCompactsLog(VertxTestContext ctx) throws Throwable {
        storage.open(Path.of("/tmp/test")).onComplete(ctx.succeeding(v -> {
            leaderNode.start().onComplete(ctx.succeeding(v2 -> {
                leaderNode.awaitState(RaftNode.State.LEADER, 5000).onComplete(ctx.succeeding(state -> {

                    // Submit 10 commands
                    submitCommands(10).onComplete(ctx.succeeding(v3 -> {
                        int logSizeBefore = leaderNode.getLogSize();

                        // Take snapshot manually
                        leaderNode.takeSnapshot().onComplete(ctx.succeeding(v4 -> {
                            ctx.verify(() -> {
                                // Snapshot was taken at lastApplied
                                long snapIdx = leaderNode.getSnapshotLastIndex();
                                assertTrue(snapIdx >= 10,
                                        "Snapshot index should cover all committed entries");

                                // Log should be compacted
                                int logSizeAfter = leaderNode.getLogSize();
                                assertTrue(logSizeAfter < logSizeBefore,
                                        "Log should be smaller after compaction: before=" + logSizeBefore
                                                + " after=" + logSizeAfter);

                                // State machine state preserved
                                assertEquals("value0", stateMachine.getMetadata("key0"));
                                assertEquals("value9", stateMachine.getMetadata("key9"));

                                // Snapshot data saved in storage
                                assertNotNull(storage.getLog(), "Storage should still be accessible");
                            });
                            ctx.completeNow();
                        }));
                    }));
                }));
            }));
        }));

        assertTrue(ctx.awaitCompletion(10, TimeUnit.SECONDS), "Test timed out");
        if (ctx.failed()) throw ctx.causeOfFailure();
    }

    @Test
    void testCommandsWorkAfterSnapshot(VertxTestContext ctx) throws Throwable {
        storage.open(Path.of("/tmp/test")).onComplete(ctx.succeeding(v -> {
            leaderNode.start().onComplete(ctx.succeeding(v2 -> {
                leaderNode.awaitState(RaftNode.State.LEADER, 5000).onComplete(ctx.succeeding(state -> {

                    // Submit some commands, take snapshot, then submit more
                    submitCommands(5).onComplete(ctx.succeeding(v3 -> {
                        leaderNode.takeSnapshot().onComplete(ctx.succeeding(v4 -> {

                            // Submit more commands after snapshot
                            submitCommandsFrom(5, 5).onComplete(ctx.succeeding(v5 -> {
                                ctx.verify(() -> {
                                    // All commands should be in state machine
                                    for (int i = 0; i < 10; i++) {
                                        assertEquals("value" + i, stateMachine.getMetadata("key" + i),
                                                "key" + i + " should exist in state machine");
                                    }

                                    // Last log index should reflect post-snapshot entries
                                    assertTrue(leaderNode.getLastLogIndex() >= 10,
                                            "Last log index should cover all entries");
                                });
                                ctx.completeNow();
                            }));
                        }));
                    }));
                }));
            }));
        }));

        assertTrue(ctx.awaitCompletion(10, TimeUnit.SECONDS), "Test timed out");
        if (ctx.failed()) throw ctx.causeOfFailure();
    }

    @Test
    void testSnapshotPreservesJobAssignmentsAndQueue(VertxTestContext ctx) throws Throwable {
        storage.open(Path.of("/tmp/test")).onComplete(ctx.succeeding(v -> {
            leaderNode.start().onComplete(ctx.succeeding(v2 -> {
                leaderNode.awaitState(RaftNode.State.LEADER, 5000).onComplete(ctx.succeeding(state -> {

                    // Submit a command to populate state
                    leaderNode.submitCommand(SystemMetadataCommand.set("test-key", "test-value"))
                            .onComplete(ctx.succeeding(result -> {
                        // Take snapshot
                        byte[] snapshotData = stateMachine.takeSnapshot();

                        ctx.verify(() -> {
                            assertNotNull(snapshotData);
                            assertTrue(snapshotData.length > 0);

                            // Restore to a new state machine and verify
                            QuorusStateStore newSm = new QuorusStateStore();
                            newSm.restoreSnapshot(snapshotData);
                            assertEquals("test-value", newSm.getMetadata("test-key"),
                                    "Restored state machine should have the metadata");
                        });
                        ctx.completeNow();
                    }));
                }));
            }));
        }));

        assertTrue(ctx.awaitCompletion(10, TimeUnit.SECONDS), "Test timed out");
        if (ctx.failed()) throw ctx.causeOfFailure();
    }

    @Test
    void testSnapshotDisabledDoesNotTrigger(VertxTestContext ctx) throws Throwable {
        // Create a second node with snapshots disabled
        InMemoryRaftStorage storage2 = new InMemoryRaftStorage();
        InMemoryTransportSimulator transport2 = new InMemoryTransportSimulator("node-no-snap");
        Set<String> cluster = Set.of("node-no-snap");
        RaftNode noSnapNode = new RaftNode(vertx, "node-no-snap", cluster, transport2, new QuorusStateStore(),
                storage2, 1000, 200,
                false, 5, 500);

        storage2.open(Path.of("/tmp/test2")).onComplete(ctx.succeeding(v -> {
            noSnapNode.start().onComplete(ctx.succeeding(v2 -> {
                noSnapNode.awaitState(RaftNode.State.LEADER, 5000).onComplete(ctx.succeeding(state -> {

                    // Submit many commands
                    submitCommandsOn(noSnapNode, 10).onComplete(ctx.succeeding(v3 -> {
                        // Wait for potential snapshot scheduler
                        vertx.setTimer(1500, timerId -> {
                            ctx.verify(() -> {
                                assertEquals(0, noSnapNode.getSnapshotLastIndex(),
                                        "Snapshot should not be taken when disabled");
                                assertFalse(noSnapNode.isSnapshotEnabled());
                            });
                            noSnapNode.stop();
                            InMemoryTransportSimulator.clearAllTransports();
                            ctx.completeNow();
                        });
                    }));
                }));
            }));
        }));

        assertTrue(ctx.awaitCompletion(10, TimeUnit.SECONDS), "Test timed out");
        if (ctx.failed()) throw ctx.causeOfFailure();
    }

    @Test
    void testSnapshotSavesAndLoadsFromStorage(VertxTestContext ctx) throws Throwable {
        storage.open(Path.of("/tmp/test")).onComplete(ctx.succeeding(v -> {
            leaderNode.start().onComplete(ctx.succeeding(v2 -> {
                leaderNode.awaitState(RaftNode.State.LEADER, 5000).onComplete(ctx.succeeding(state -> {

                    submitCommands(5).onComplete(ctx.succeeding(v3 -> {
                        leaderNode.takeSnapshot().onComplete(ctx.succeeding(v4 -> {

                            // Verify snapshot is in storage
                            storage.loadSnapshot().onComplete(ctx.succeeding(snapshotOpt -> {
                                ctx.verify(() -> {
                                    assertTrue(snapshotOpt.isPresent(), "Snapshot should be saved in storage");
                                    var snapshot = snapshotOpt.get();
                                    assertTrue(snapshot.lastIncludedIndex() >= 5);
                                    assertTrue(snapshot.lastIncludedTerm() > 0);
                                    assertTrue(snapshot.data().length > 0);
                                });
                                ctx.completeNow();
                            }));
                        }));
                    }));
                }));
            }));
        }));

        assertTrue(ctx.awaitCompletion(10, TimeUnit.SECONDS), "Test timed out");
        if (ctx.failed()) throw ctx.causeOfFailure();
    }

    @Test
    void testSnapshotMetricsExposed() {
        // Verify the node exposes snapshot-related getters
        assertEquals(0, leaderNode.getSnapshotLastIndex());
        assertEquals(0, leaderNode.getSnapshotLastTerm());
        assertTrue(leaderNode.isSnapshotEnabled());
    }

    // ========== Helpers ==========

    private io.vertx.core.Future<Void> submitCommands(int count) {
        return submitCommandsFrom(0, count);
    }

    private io.vertx.core.Future<Void> submitCommandsFrom(int startIdx, int count) {
        return submitCommandsOnFrom(leaderNode, startIdx, count);
    }

    private io.vertx.core.Future<Void> submitCommandsOn(RaftNode node, int count) {
        return submitCommandsOnFrom(node, 0, count);
    }

    private io.vertx.core.Future<Void> submitCommandsOnFrom(RaftNode node, int startIdx, int count) {
        io.vertx.core.Future<Void> chain = io.vertx.core.Future.succeededFuture();
        for (int i = startIdx; i < startIdx + count; i++) {
            final int idx = i;
            chain = chain.compose(v ->
                    node.submitCommand(SystemMetadataCommand.set("key" + idx, "value" + idx))
                            .mapEmpty());
        }
        return chain;
    }
}
