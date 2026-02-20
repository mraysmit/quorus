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

import dev.mars.quorus.controller.raft.grpc.InstallSnapshotRequest;
import dev.mars.quorus.controller.raft.grpc.InstallSnapshotResponse;
import dev.mars.quorus.controller.raft.storage.InMemoryRaftStorage;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.SystemMetadataCommand;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioral tests for T5.3: InstallSnapshot RPC.
 * Tests the full path where a leader sends a snapshot to a lagging follower
 * that has fallen behind the compacted log, the follower restores state,
 * and cluster operation continues.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-13
 */
@ExtendWith(VertxExtension.class)
class InstallSnapshotTest {

    private Vertx vertx;
    private InMemoryRaftStorage leaderStorage;
    private InMemoryRaftStorage followerStorage;

    @BeforeEach
    void setUp(Vertx vertx) {
        this.vertx = vertx;
        InMemoryTransportSimulator.clearAllTransports();
    }

    @AfterEach
    void tearDown() {
        InMemoryTransportSimulator.clearAllTransports();
    }

    /**
     * Test 1: Leader detects lagging follower and sends snapshot.
     *
     * Flow: 3-node cluster starts, leader elected. Partition isolates node3.
     * Leader commits entries and takes snapshot. Partition heals.
     * Leader detects node3's nextIndex <= snapshotLastIndex,
     * triggers sendInstallSnapshot. node3 receives snapshot and restores state.
     */
    @Test
    void testLeaderSendsSnapshotToLaggingFollower(VertxTestContext ctx) throws Throwable {
        leaderStorage = new InMemoryRaftStorage();
        followerStorage = new InMemoryRaftStorage();
        InMemoryRaftStorage node2Storage = new InMemoryRaftStorage();

        Set<String> cluster = Set.of("node1", "node2", "node3");

        InMemoryTransportSimulator t1 = new InMemoryTransportSimulator("node1");
        InMemoryTransportSimulator t2 = new InMemoryTransportSimulator("node2");
        InMemoryTransportSimulator t3 = new InMemoryTransportSimulator("node3");

        QuorusStateStore sm1 = new QuorusStateStore();
        QuorusStateStore sm2 = new QuorusStateStore();
        QuorusStateStore sm3 = new QuorusStateStore();

        RaftNode node1 = new RaftNode(vertx, "node1", cluster, t1, sm1,
                leaderStorage, 1000, 200, true, 5, 300);
        RaftNode node2 = new RaftNode(vertx, "node2", cluster, t2, sm2,
                node2Storage, 1500, 200, true, 5, 300);
        RaftNode node3 = new RaftNode(vertx, "node3", cluster, t3, sm3,
                followerStorage, 15000, 200, true, 5, 300);

        AtomicReference<RaftNode> leaderRef = new AtomicReference<>();

        // Partition node3, then start all nodes and submit commands (fire-and-forget)
        InMemoryTransportSimulator.createPartition(Set.of("node1", "node2"), Set.of("node3"));

        leaderStorage.open(Path.of("/tmp/t1-1"))
                .compose(v -> node2Storage.open(Path.of("/tmp/t1-2")))
                .compose(v -> followerStorage.open(Path.of("/tmp/t1-3")))
                .compose(v -> node1.start())
                .compose(v -> node2.start())
                .compose(v -> node3.start());

        // Phase 1: Wait for leader election on TEST thread (not event loop)
        await().atMost(12, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> node1.getState() == RaftNode.State.LEADER
                        || node2.getState() == RaftNode.State.LEADER);

        RaftNode leader = (node1.getState() == RaftNode.State.LEADER) ? node1 : node2;
        leaderRef.set(leader);

        // Phase 2: Submit commands via event loop, wait for completion on test thread
        CompletableFuture<Void> commandsDone = new CompletableFuture<>();
        vertx.runOnContext(v ->
                submitCommands(leader, 0, 8)
                        .onSuccess(r -> commandsDone.complete(null))
                        .onFailure(commandsDone::completeExceptionally));
        commandsDone.get(15, TimeUnit.SECONDS);

        // Phase 3: Wait for snapshot on test thread
        await().atMost(12, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertTrue(leader.getSnapshotLastIndex() >= 5,
                        "Leader should have taken a snapshot"));

        // Phase 4: Heal partition — node3 can now communicate
        InMemoryTransportSimulator.healPartitions();

        // Phase 5: Wait for node3 to receive snapshot via InstallSnapshot
        await().atMost(15, TimeUnit.SECONDS).pollInterval(300, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertTrue(node3.getSnapshotLastIndex() > 0,
                            "node3 should have received snapshot (snapshotLastIndex=" +
                                    node3.getSnapshotLastIndex() + ")");
                    for (int i = 0; i < 8; i++) {
                        assertEquals("value" + i, sm3.getMetadata("key" + i),
                                "node3 should have key" + i + " from snapshot");
                    }
                });

        node1.stop(); node2.stop(); node3.stop();
        ctx.completeNow();

        assertTrue(ctx.awaitCompletion(5, TimeUnit.SECONDS), "Test timed out");
        if (ctx.failed()) throw ctx.causeOfFailure();
    }

    /**
     * Test 2: Follower state machine is correctly restored after snapshot install,
     * and follower can continue receiving normal log entries after snapshot.
     */
    @Test
    void testFollowerStateMachineRestoredBySnapshot(VertxTestContext ctx) throws Throwable {
        leaderStorage = new InMemoryRaftStorage();
        followerStorage = new InMemoryRaftStorage();
        InMemoryRaftStorage node2Storage = new InMemoryRaftStorage();

        Set<String> cluster = Set.of("node1", "node2", "node3");

        InMemoryTransportSimulator t1 = new InMemoryTransportSimulator("node1");
        InMemoryTransportSimulator t2 = new InMemoryTransportSimulator("node2");
        InMemoryTransportSimulator t3 = new InMemoryTransportSimulator("node3");

        QuorusStateStore sm1 = new QuorusStateStore();
        QuorusStateStore sm2 = new QuorusStateStore();
        QuorusStateStore sm3 = new QuorusStateStore();

        RaftNode node1 = new RaftNode(vertx, "node1", cluster, t1, sm1,
                leaderStorage, 1000, 200, true, 5, 300);
        RaftNode node2 = new RaftNode(vertx, "node2", cluster, t2, sm2,
                node2Storage, 1500, 200, true, 5, 300);
        RaftNode node3 = new RaftNode(vertx, "node3", cluster, t3, sm3,
                followerStorage, 15000, 200, true, 5, 300);

        // Partition node3
        InMemoryTransportSimulator.createPartition(Set.of("node1", "node2"), Set.of("node3"));

        leaderStorage.open(Path.of("/tmp/t2-1"))
                .compose(v -> node2Storage.open(Path.of("/tmp/t2-2")))
                .compose(v -> followerStorage.open(Path.of("/tmp/t2-3")))
                .compose(v -> node1.start())
                .compose(v -> node2.start())
                .compose(v -> node3.start());

        // Wait for leader election
        await().atMost(12, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> node1.getState() == RaftNode.State.LEADER
                        || node2.getState() == RaftNode.State.LEADER);

        RaftNode leader = (node1.getState() == RaftNode.State.LEADER) ? node1 : node2;

        // Submit initial commands
        CompletableFuture<Void> batch1 = new CompletableFuture<>();
        vertx.runOnContext(v ->
                submitCommands(leader, 0, 6)
                        .onSuccess(r -> batch1.complete(null))
                        .onFailure(batch1::completeExceptionally));
        batch1.get(15, TimeUnit.SECONDS);

        // Wait for snapshot
        await().atMost(12, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertTrue(leader.getSnapshotLastIndex() >= 5));

        // Heal partition
        InMemoryTransportSimulator.healPartitions();

        // Wait for node3 to get snapshot
        await().atMost(15, TimeUnit.SECONDS).pollInterval(300, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertTrue(node3.getSnapshotLastIndex() > 0,
                        "node3 should have installed snapshot"));

        // Submit MORE commands after snapshot catch-up
        CompletableFuture<Void> batch2 = new CompletableFuture<>();
        vertx.runOnContext(v ->
                submitCommands(leader, 6, 3)
                        .onSuccess(r -> batch2.complete(null))
                        .onFailure(batch2::completeExceptionally));
        batch2.get(15, TimeUnit.SECONDS);

        // Wait for node3 to replicate post-snapshot entries
        await().atMost(12, TimeUnit.SECONDS).pollInterval(300, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertEquals("value0", sm3.getMetadata("key0"), "Snapshot data should be present");
                    assertEquals("value5", sm3.getMetadata("key5"), "Snapshot data should be present");
                    assertEquals("value6", sm3.getMetadata("key6"), "Post-snapshot replicated data");
                    assertEquals("value8", sm3.getMetadata("key8"), "Post-snapshot replicated data");
                });

        node1.stop(); node2.stop(); node3.stop();
        ctx.completeNow();

        assertTrue(ctx.awaitCompletion(5, TimeUnit.SECONDS), "Test timed out");
        if (ctx.failed()) throw ctx.causeOfFailure();
    }

    /**
     * Test 3: Leader updates nextIndex/matchIndex after successful InstallSnapshot.
     */
    @Test
    void testLeaderUpdatesIndicesAfterSnapshotInstall(VertxTestContext ctx) throws Throwable {
        leaderStorage = new InMemoryRaftStorage();
        followerStorage = new InMemoryRaftStorage();
        InMemoryRaftStorage node2Storage = new InMemoryRaftStorage();

        Set<String> cluster = Set.of("node1", "node2", "node3");

        InMemoryTransportSimulator t1 = new InMemoryTransportSimulator("node1");
        InMemoryTransportSimulator t2 = new InMemoryTransportSimulator("node2");
        InMemoryTransportSimulator t3 = new InMemoryTransportSimulator("node3");

        QuorusStateStore sm1 = new QuorusStateStore();
        QuorusStateStore sm2 = new QuorusStateStore();
        QuorusStateStore sm3 = new QuorusStateStore();

        RaftNode node1 = new RaftNode(vertx, "node1", cluster, t1, sm1,
                leaderStorage, 1000, 200, true, 5, 300);
        RaftNode node2 = new RaftNode(vertx, "node2", cluster, t2, sm2,
                node2Storage, 1500, 200, true, 5, 300);
        RaftNode node3 = new RaftNode(vertx, "node3", cluster, t3, sm3,
                followerStorage, 15000, 200, true, 5, 300);

        InMemoryTransportSimulator.createPartition(Set.of("node1", "node2"), Set.of("node3"));

        leaderStorage.open(Path.of("/tmp/t3-1"))
                .compose(v -> node2Storage.open(Path.of("/tmp/t3-2")))
                .compose(v -> followerStorage.open(Path.of("/tmp/t3-3")))
                .compose(v -> node1.start())
                .compose(v -> node2.start())
                .compose(v -> node3.start());

        // Wait for leader election
        await().atMost(12, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> node1.getState() == RaftNode.State.LEADER
                        || node2.getState() == RaftNode.State.LEADER);

        RaftNode leader = (node1.getState() == RaftNode.State.LEADER) ? node1 : node2;

        CompletableFuture<Void> commandsDone = new CompletableFuture<>();
        vertx.runOnContext(v ->
                submitCommands(leader, 0, 7)
                        .onSuccess(r -> commandsDone.complete(null))
                        .onFailure(commandsDone::completeExceptionally));
        commandsDone.get(15, TimeUnit.SECONDS);

        await().atMost(12, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertTrue(leader.getSnapshotLastIndex() >= 5));

        long leaderSnapIdx = leader.getSnapshotLastIndex();

        // Heal partition
        InMemoryTransportSimulator.healPartitions();

        // Wait for snapshot install and index update
        await().atMost(15, TimeUnit.SECONDS).pollInterval(300, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertTrue(node3.getSnapshotLastIndex() > 0,
                            "node3 should have installed snapshot");
                    long nextIdx = leader.getNextIndex("node3");
                    assertTrue(nextIdx > leaderSnapIdx,
                            "Leader nextIndex for node3 should be > snapshotLastIndex: " +
                                    "nextIdx=" + nextIdx + ", snapIdx=" + leaderSnapIdx);
                });

        node1.stop(); node2.stop(); node3.stop();
        ctx.completeNow();

        assertTrue(ctx.awaitCompletion(5, TimeUnit.SECONDS), "Test timed out");
        if (ctx.failed()) throw ctx.causeOfFailure();
    }

    /**
     * Test 4: Follower rejects InstallSnapshot with stale term.
     *
     * Directly calls handleInstallSnapshot on a follower with a term lower
     * than the follower's current term. The response should indicate rejection.
     */
    @Test
    void testFollowerRejectsStaleTermSnapshot(VertxTestContext ctx) throws Throwable {
        InMemoryRaftStorage storage = new InMemoryRaftStorage();
        InMemoryTransportSimulator transport = new InMemoryTransportSimulator("node1");
        QuorusStateStore sm = new QuorusStateStore();
        Set<String> cluster = Set.of("node1");

        RaftNode node = new RaftNode(vertx, "node1", cluster, transport, sm,
                storage, 1000, 200,
                false, 100, 5000);

        storage.open(Path.of("/tmp/stale-test")).onComplete(ctx.succeeding(v -> {
            node.start().onComplete(ctx.succeeding(v2 -> {
                node.awaitState(RaftNode.State.LEADER, 5000).onComplete(ctx.succeeding(state -> {

                    // The node is now leader with currentTerm >= 1
                    // Send an InstallSnapshot with term 0 (stale)
                    InstallSnapshotRequest staleRequest = InstallSnapshotRequest.newBuilder()
                            .setTerm(0) // stale term
                            .setLeaderId("fake-leader")
                            .setLastIncludedIndex(10)
                            .setLastIncludedTerm(0)
                            .setChunkIndex(0)
                            .setTotalChunks(1)
                            .setData(com.google.protobuf.ByteString.copyFrom(new byte[]{1, 2, 3}))
                            .setDone(true)
                            .build();

                    node.handleInstallSnapshot(staleRequest).onComplete(ctx.succeeding(response -> {
                        ctx.verify(() -> {
                            assertFalse(response.getSuccess(),
                                    "Should reject InstallSnapshot with stale term");
                            assertTrue(response.getTerm() > 0,
                                    "Response should include current term");
                        });
                        node.stop();
                        ctx.completeNow();
                    }));
                }));
            }));
        }));

        assertTrue(ctx.awaitCompletion(10, TimeUnit.SECONDS), "Test timed out");
        if (ctx.failed()) throw ctx.causeOfFailure();
    }

    /**
     * Test 5: SnapshotChunkAssembler correctly reassembles multi-chunk snapshot data.
     *
     * Unit test for the inner assembler class verifying that chunks split
     * across multiple calls are correctly reassembled into the original data.
     */
    @Test
    void testChunkAssemblerReassemblesData() {
        byte[] original = new byte[256];
        for (int i = 0; i < 256; i++) {
            original[i] = (byte) i;
        }

        // Split into 4 chunks of 64 bytes
        int chunkSize = 64;
        int totalChunks = 4;
        RaftNode.SnapshotChunkAssembler assembler = new RaftNode.SnapshotChunkAssembler(totalChunks);

        for (int i = 0; i < totalChunks; i++) {
            byte[] chunk = new byte[chunkSize];
            System.arraycopy(original, i * chunkSize, chunk, 0, chunkSize);
            assembler.addChunk(i, chunk);
            assertEquals(i + 1, assembler.getNextExpectedChunk());
        }

        byte[] reassembled = assembler.assemble();
        assertArrayEquals(original, reassembled, "Reassembled data should match original");
    }

    /**
     * Test 6: SnapshotChunkAssembler handles single-chunk snapshot correctly.
     */
    @Test
    void testChunkAssemblerSingleChunk() {
        byte[] data = "snapshot-data-content".getBytes();
        RaftNode.SnapshotChunkAssembler assembler = new RaftNode.SnapshotChunkAssembler(1);
        assertEquals(0, assembler.getNextExpectedChunk());

        assembler.addChunk(0, data);
        assertEquals(1, assembler.getNextExpectedChunk());

        byte[] result = assembler.assemble();
        assertArrayEquals(data, result);
    }

    /**
     * Test 7: Follower saves snapshot to storage upon install completion.
     *
     * After a follower receives a complete snapshot via handleInstallSnapshot,
     * the snapshot data must be persisted in the follower's RaftStorage.
     */
    @Test
    void testFollowerPersistsInstalledSnapshot(VertxTestContext ctx) throws Throwable {
        InMemoryRaftStorage storage = new InMemoryRaftStorage();
        InMemoryTransportSimulator transport = new InMemoryTransportSimulator("follower-persist");
        QuorusStateStore sm = new QuorusStateStore();
        Set<String> cluster = Set.of("follower-persist");

        RaftNode node = new RaftNode(vertx, "follower-persist", cluster, transport, sm,
                storage, 15000, 200,
                false, 100, 5000);

        storage.open(Path.of("/tmp/persist-test")).onComplete(ctx.succeeding(v -> {
            node.start().onComplete(ctx.succeeding(v2 -> {

                // Build a realistic snapshot: serialize some state machine data
                QuorusStateStore tempSM = new QuorusStateStore();
                tempSM.apply(SystemMetadataCommand.set("snap-key-1", "snap-val-1"));
                tempSM.apply(SystemMetadataCommand.set("snap-key-2", "snap-val-2"));
                byte[] snapshotData = tempSM.takeSnapshot();

                // Send a single-chunk InstallSnapshot directly
                InstallSnapshotRequest request = InstallSnapshotRequest.newBuilder()
                        .setTerm(1)
                        .setLeaderId("some-leader")
                        .setLastIncludedIndex(10)
                        .setLastIncludedTerm(1)
                        .setChunkIndex(0)
                        .setTotalChunks(1)
                        .setData(com.google.protobuf.ByteString.copyFrom(snapshotData))
                        .setDone(true)
                        .build();

                node.handleInstallSnapshot(request).onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertTrue(response.getSuccess(), "InstallSnapshot should succeed");
                    });

                    // Verify snapshot was persisted in storage
                    storage.loadSnapshot().onComplete(ctx.succeeding(snapshotOpt -> {
                        ctx.verify(() -> {
                            assertTrue(snapshotOpt.isPresent(), "Snapshot should be saved in follower's storage");
                            var saved = snapshotOpt.get();
                            assertEquals(10, saved.lastIncludedIndex());
                            assertEquals(1, saved.lastIncludedTerm());
                            assertTrue(saved.data().length > 0);

                            // Verify state machine was restored
                            assertEquals("snap-val-1", sm.getMetadata("snap-key-1"));
                            assertEquals("snap-val-2", sm.getMetadata("snap-key-2"));

                            // Verify node index tracking was updated
                            assertEquals(10, node.getSnapshotLastIndex());
                            assertEquals(1, node.getSnapshotLastTerm());
                        });
                        node.stop();
                        ctx.completeNow();
                    }));
                }));
            }));
        }));

        assertTrue(ctx.awaitCompletion(10, TimeUnit.SECONDS), "Test timed out");
        if (ctx.failed()) throw ctx.causeOfFailure();
    }

    // ========== Helpers ==========

    private Future<Void> submitCommands(RaftNode node, int startIdx, int count) {
        Future<Void> chain = Future.succeededFuture();
        for (int i = startIdx; i < startIdx + count; i++) {
            final int idx = i;
            chain = chain.compose(v ->
                    node.submitCommand(SystemMetadataCommand.set("key" + idx, "value" + idx))
                            .mapEmpty());
        }
        return chain;
    }
}
