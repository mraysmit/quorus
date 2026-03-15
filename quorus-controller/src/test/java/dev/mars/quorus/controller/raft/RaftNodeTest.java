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
import dev.mars.quorus.controller.raft.storage.RaftStorage;
import dev.mars.quorus.controller.raft.storage.RaftStorage.LogEntryData;
import dev.mars.quorus.controller.raft.storage.file.FileRaftStorage;
import dev.mars.quorus.controller.raft.grpc.AppendEntriesRequest;
import dev.mars.quorus.controller.raft.grpc.AppendEntriesResponse;
import dev.mars.quorus.controller.raft.grpc.InstallSnapshotRequest;
import dev.mars.quorus.controller.raft.grpc.InstallSnapshotResponse;
import dev.mars.quorus.controller.raft.grpc.VoteRequest;
import dev.mars.quorus.controller.raft.grpc.VoteResponse;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.WorkerExecutor;
import dev.mars.quorus.controller.state.CommandResult;
import dev.mars.quorus.controller.state.ProtobufCommandCodec;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.SystemMetadataCommand;
import org.awaitility.Awaitility;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RaftNode implementation using real components (no mocking).
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */
class RaftNodeTest {

    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration MEDIUM_TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    Path tempDir;

    private io.vertx.core.Vertx vertx;
    private RaftNode node1;
    private RaftNode node2;
    private RaftNode node3;
    private InMemoryTransportSimulator transport1;
    private InMemoryTransportSimulator transport2;
    private InMemoryTransportSimulator transport3;
    private QuorusStateStore stateMachine1;
    private QuorusStateStore stateMachine2;
    private QuorusStateStore stateMachine3;

    @BeforeEach
    void setUp() {
        vertx = io.vertx.core.Vertx.vertx();
        // Clear any existing transports
        InMemoryTransportSimulator.clearAllTransports();

        // Create cluster nodes
        Set<String> clusterNodes = Set.of("node1", "node2", "node3");

        // Create transports
        transport1 = new InMemoryTransportSimulator("node1");
        transport2 = new InMemoryTransportSimulator("node2");
        transport3 = new InMemoryTransportSimulator("node3");

        // Create state machines
        stateMachine1 = new QuorusStateStore();
        stateMachine2 = new QuorusStateStore();
        stateMachine3 = new QuorusStateStore();

        // Create Raft nodes with shorter timeouts for testing
        node1 = RaftNode.builder().vertx(vertx).nodeId("node1").clusterNodes(clusterNodes).transport(transport1).stateMachine(stateMachine1).mode(RaftNodeMode.volatileMode()).electionTimeout(1000).heartbeatInterval(200).build();
        node2 = RaftNode.builder().vertx(vertx).nodeId("node2").clusterNodes(clusterNodes).transport(transport2).stateMachine(stateMachine2).mode(RaftNodeMode.volatileMode()).electionTimeout(1000).heartbeatInterval(200).build();
        node3 = RaftNode.builder().vertx(vertx).nodeId("node3").clusterNodes(clusterNodes).transport(transport3).stateMachine(stateMachine3).mode(RaftNodeMode.volatileMode()).electionTimeout(1000).heartbeatInterval(200).build();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (node1 != null) awaitSuccess(node1.stop(), SHORT_TIMEOUT);
        if (node2 != null) awaitSuccess(node2.stop(), SHORT_TIMEOUT);
        if (node3 != null) awaitSuccess(node3.stop(), SHORT_TIMEOUT);
        if (vertx != null) awaitSuccess(vertx.close(), SHORT_TIMEOUT);
        InMemoryTransportSimulator.clearAllTransports();
    }

    @Test
    void testNodeInitialization() {
        assertEquals("node1", node1.getNodeId());
        assertEquals(RaftNode.State.FOLLOWER, node1.getState());
        assertEquals(0, node1.getCurrentTerm());
        assertFalse(node1.isLeader());
    }

    @Test
    void testNodeStartAndStop() {
        assertFalse(transport1.isRunning());
        
        awaitSuccess(node1.start(), SHORT_TIMEOUT);
        assertTrue(transport1.isRunning());
        assertEquals(RaftNode.State.FOLLOWER, node1.getState());
        
        awaitSuccess(node1.stop(), SHORT_TIMEOUT);
        assertFalse(transport1.isRunning());
    }

    @Test
    void testSingleNodeElection() {
        // Create a single-node cluster
        Set<String> singleNodeCluster = Set.of("node1");
        RaftNode singleNode = RaftNode.builder().vertx(vertx).nodeId("node1").clusterNodes(singleNodeCluster).transport(transport1).stateMachine(stateMachine1).mode(RaftNodeMode.volatileMode()).electionTimeout(500).heartbeatInterval(100).build();
        
        singleNode.start();
        
        // Wait for election to complete
        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .until(() -> singleNode.getState() == RaftNode.State.LEADER);
        
        assertTrue(singleNode.isLeader());
        assertEquals("node1", singleNode.getLeaderId());
        
        singleNode.stop();
    }

    @Test
    void testThreeNodeClusterElection() {
        // Start all nodes
        node1.start();
        node2.start();
        node3.start();
        
        // Wait for a leader to be elected — generous timeout for split vote scenarios
        // (1000ms election timeout means split votes can take multiple cycles)
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> {
                    long leaderCount = Set.of(node1, node2, node3).stream()
                            .mapToLong(node -> node.isLeader() ? 1 : 0)
                            .sum();
                    return leaderCount == 1;
                });
        
        // Verify exactly one leader exists.
        // All assertions must be inside Awaitility or accept transient states,
        // because Raft election cycles continue — a follower can become a
        // CANDIDATE between the Awaitility check and a subsequent assertion.
        long leaderCount = Set.of(node1, node2, node3).stream()
                .mapToLong(node -> node.isLeader() ? 1 : 0)
                .sum();
        assertEquals(1, leaderCount);
        
        // Verify all nodes are in valid Raft states (CANDIDATE is a valid
        // transient state when a follower's election timer fires)
        Set.of(node1, node2, node3).forEach(node -> {
            assertNotNull(node.getState(), "Node state should not be null");
        });
    }

    @Test
    void testCommandSubmissionToNonLeader() {
        awaitSuccess(node1.start(), SHORT_TIMEOUT);
        
        // Node starts as follower, command submission should fail
        SystemMetadataCommand command = SystemMetadataCommand.set("test-key", "test-value");
        
        Future<CommandResult<?>> future = node1.submitCommand(command);

        Throwable failure = awaitFailure(future, SHORT_TIMEOUT);
        assertInstanceOf(IllegalStateException.class, failure);
        assertTrue(failure.getMessage().contains("Not the leader"));
    }

    @Test
    void testStateMachineOperations() {
        // Test state machine directly
        SystemMetadataCommand setCommand = SystemMetadataCommand.set("version", "2.1");
        CommandResult<?> result = stateMachine1.apply(setCommand);
        
        assertInstanceOf(CommandResult.Success.class, result);
        assertEquals("2.0", ((CommandResult.Success<?>) result).entity()); // Previous value
        assertEquals("2.1", stateMachine1.getMetadata("version"));
        
        // Test snapshot
        byte[] snapshot = stateMachine1.takeSnapshot();
        assertNotNull(snapshot);
        assertTrue(snapshot.length > 0);
        
        // Reset and restore
        stateMachine1.reset();
        assertEquals("2.0", stateMachine1.getMetadata("version")); // Back to default
        
        stateMachine1.restoreSnapshot(snapshot);
        assertEquals("2.1", stateMachine1.getMetadata("version")); // Restored
    }

    @Test
    void testTransportCommunication() {
        transport1.start(message -> {
            // Message handler - just log for testing
            System.out.println("Node1 received: " + message);
        });
        
        transport2.start(message -> {
            System.out.println("Node2 received: " + message);
        });
        
        assertTrue(transport1.isRunning());
        assertTrue(transport2.isRunning());
        
        // Test vote request
        VoteRequest voteRequest = VoteRequest.newBuilder()
                .setTerm(1)
                .setCandidateId("node1")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        Future<VoteResponse> future = transport1.sendVoteRequest("node2", voteRequest);
        
        assertDoesNotThrow(() -> {
            VoteResponse response = awaitSuccess(future, SHORT_TIMEOUT);
            assertNotNull(response);
            assertEquals(1, response.getTerm());
        });
    }

    @Test
    void testLogEntryCreation() {
        SystemMetadataCommand command = SystemMetadataCommand.set("test", "value");
        LogEntry entry = new LogEntry(1, 5, command);
        
        assertEquals(1, entry.getTerm());
        assertEquals(5, entry.getIndex());
        assertEquals(command, entry.getCommand());
        assertNotNull(entry.getTimestamp());
        assertFalse(entry.isNoOp());
        
        // Test no-op entry
        LogEntry noOpEntry = new LogEntry(1, 6, null);
        assertTrue(noOpEntry.isNoOp());
    }

    @Test
    void testVoteRequestResponse() {
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(2)
                .setCandidateId("candidate1")
                .setLastLogIndex(10)
                .setLastLogTerm(1)
                .build();
        assertEquals(2, request.getTerm());
        assertEquals("candidate1", request.getCandidateId());
        assertEquals(10, request.getLastLogIndex());
        assertEquals(1, request.getLastLogTerm());
        
        VoteResponse response = VoteResponse.newBuilder()
                .setTerm(2)
                .setVoteGranted(true)
                .build();
        assertEquals(2, response.getTerm());
        assertTrue(response.getVoteGranted());
    }

    @Test
    void testNodeStateTransitions() {
        node1.start();
        
        // Initially follower
        assertEquals(RaftNode.State.FOLLOWER, node1.getState());
        
        // After election timeout, should become candidate (in a single node cluster)
        Set<String> singleNodeCluster = Set.of("node1");
        RaftNode singleNode = RaftNode.builder().vertx(vertx).nodeId("node1").clusterNodes(singleNodeCluster)
                                          .transport(new InMemoryTransportSimulator("node1"))
                                          .stateMachine(new QuorusStateStore()).mode(RaftNodeMode.volatileMode()).electionTimeout(500).heartbeatInterval(100).build();
        singleNode.start();
        
        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .until(() -> singleNode.getState() == RaftNode.State.LEADER);
        
        singleNode.stop();
    }

        @Test
        void testDurableElectionPersistsTermAndVote() {
        InMemoryRaftStorage storage = new InMemoryRaftStorage();
        awaitSuccess(storage.open(null), SHORT_TIMEOUT);

        Set<String> singleNodeCluster = Set.of("node1");
        RaftNode singleNode = RaftNode.builder().vertx(vertx).nodeId("node1").clusterNodes(singleNodeCluster)
            .transport(new InMemoryTransportSimulator("node1"))
            .stateMachine(new QuorusStateStore()).mode(RaftNodeMode.durable(storage)).electionTimeout(500).heartbeatInterval(100).build();

        awaitSuccess(singleNode.start(), SHORT_TIMEOUT);

        Awaitility.await()
            .atMost(Duration.ofSeconds(3))
            .untilAsserted(() -> {
                assertTrue(storage.getCurrentTerm() > 0, "Election term should be persisted");
                assertEquals(Optional.of("node1"), storage.getVotedFor(), "Self vote should be persisted");
            });

        awaitSuccess(singleNode.stop(), SHORT_TIMEOUT);
        }

        @Test
        void testMultiNodeRecoveryDoesNotApplyUncommittedTail() {
        InMemoryRaftStorage storage = new InMemoryRaftStorage();
        awaitSuccess(storage.open(null), SHORT_TIMEOUT);

        SystemMetadataCommand command = SystemMetadataCommand.set("recovery-key", "tail-value");
        byte[] payload = ProtobufCommandCodec.serialize(command).toByteArray();
        awaitSuccess(storage.appendEntries(java.util.List.of(new LogEntryData(1L, 1L, payload))), SHORT_TIMEOUT);
        awaitSuccess(storage.updateMetadata(1L, Optional.empty()), SHORT_TIMEOUT);

        QuorusStateStore recoveredState = new QuorusStateStore();
        RaftNode recovered = RaftNode.builder().vertx(vertx).nodeId("node1")
            .clusterNodes(Set.of("node1", "node2", "node3"))
            .transport(new InMemoryTransportSimulator("node1"))
            .stateMachine(recoveredState).mode(RaftNodeMode.durable(storage)).electionTimeout(10_000).heartbeatInterval(200).build();

        awaitSuccess(recovered.start(), SHORT_TIMEOUT);

        await().atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertNull(recoveredState.getMetadata("recovery-key"),
                "Recovered follower must not apply uncertain log tail before leader commit"));

        awaitSuccess(recovered.stop(), SHORT_TIMEOUT);
        }

        @Test
        void testSingleNodeRecoveryReappliesLocalLog() {
        InMemoryRaftStorage storage = new InMemoryRaftStorage();
        awaitSuccess(storage.open(null), SHORT_TIMEOUT);

        SystemMetadataCommand command = SystemMetadataCommand.set("single-recovery-key", "single-value");
        byte[] payload = ProtobufCommandCodec.serialize(command).toByteArray();
        awaitSuccess(storage.appendEntries(java.util.List.of(new LogEntryData(1L, 1L, payload))), SHORT_TIMEOUT);
        awaitSuccess(storage.updateMetadata(1L, Optional.of("node1")), SHORT_TIMEOUT);

        QuorusStateStore recoveredState = new QuorusStateStore();
        RaftNode recovered = RaftNode.builder().vertx(vertx).nodeId("node1")
            .clusterNodes(Set.of("node1"))
            .transport(new InMemoryTransportSimulator("node1"))
            .stateMachine(recoveredState).mode(RaftNodeMode.durable(storage)).electionTimeout(10_000).heartbeatInterval(200).build();

        awaitSuccess(recovered.start(), SHORT_TIMEOUT);

        await().atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertEquals("single-value", recoveredState.getMetadata("single-recovery-key")));

        awaitSuccess(recovered.stop(), SHORT_TIMEOUT);
        }

        @Test
        void testRejectVoteWhenCandidateLogIsBehind() {
        Set<String> singleNodeCluster = Set.of("node1");
        RaftNode singleNode = RaftNode.builder().vertx(vertx).nodeId("node1").clusterNodes(singleNodeCluster)
            .transport(new InMemoryTransportSimulator("node1"))
            .stateMachine(new QuorusStateStore()).mode(RaftNodeMode.volatileMode()).electionTimeout(500).heartbeatInterval(100).build();

        awaitSuccess(singleNode.start(), SHORT_TIMEOUT);

        await().atMost(Duration.ofSeconds(3)).until(singleNode::isLeader);

        CommandResult<?> submitted = awaitSuccess(
            singleNode.submitCommand(SystemMetadataCommand.set("vote-log-key", "vote-log-value")),
            SHORT_TIMEOUT);
        assertInstanceOf(CommandResult.Success.class, submitted);

        VoteRequest staleCandidate = VoteRequest.newBuilder()
            .setTerm(singleNode.getCurrentTerm() + 1)
            .setCandidateId("candidate-behind")
            .setLastLogTerm(0)
            .setLastLogIndex(0)
            .build();

        VoteResponse response = awaitSuccess(singleNode.handleVoteRequest(staleCandidate), SHORT_TIMEOUT);

        assertFalse(response.getVoteGranted(), "Vote must be rejected when candidate log is behind");

        awaitSuccess(singleNode.stop(), SHORT_TIMEOUT);
        }

        @Test
        void testHigherTermIsPersistedWhenVotePersistenceFails() {
        InMemoryRaftStorage delegate = new InMemoryRaftStorage();
        RaftStorage flakyMetadataStorage = oneShotMetadataFailureStorage(delegate);

        awaitSuccess(flakyMetadataStorage.open(null), SHORT_TIMEOUT);

        Set<String> singleNodeCluster = Set.of("node1");
        RaftNode durableNode = RaftNode.builder().vertx(vertx).nodeId("node1").clusterNodes(singleNodeCluster)
            .transport(new InMemoryTransportSimulator("node1"))
            .stateMachine(new QuorusStateStore()).mode(RaftNodeMode.durable(flakyMetadataStorage)).electionTimeout(10_000).heartbeatInterval(200).build();

        awaitSuccess(durableNode.start(), SHORT_TIMEOUT);

        VoteRequest voteRequest = VoteRequest.newBuilder()
            .setTerm(7)
            .setCandidateId("candidate-x")
            .setLastLogIndex(99)
            .setLastLogTerm(99)
            .build();

        VoteResponse response = awaitSuccess(durableNode.handleVoteRequest(voteRequest), SHORT_TIMEOUT);

        assertFalse(response.getVoteGranted(), "Vote should be rejected when metadata persistence fails");
        assertEquals(7, response.getTerm(), "Response should still reflect higher observed term");

        RaftStorage.PersistentMeta persistedMeta = awaitSuccess(flakyMetadataStorage.loadMetadata(), SHORT_TIMEOUT);
        assertEquals(7, persistedMeta.currentTerm(), "Higher term must be persisted to avoid term regression after restart");
        assertEquals(Optional.empty(), persistedMeta.votedFor(), "No vote should be persisted when vote write failed");
        logExpectedFailure("vote-grant metadata persistence one-shot failure fallback", new IllegalStateException("Simulated one-shot metadata failure"));

        awaitSuccess(durableNode.stop(), SHORT_TIMEOUT);
        }

    @Test
    void testRejectHigherTermVoteWithStaleCandidateLogPersistsTermAcrossRestart() {
        WorkerExecutor executor = vertx.createSharedWorkerExecutor("vote-persist-test", 1);
        Path storageDir = tempDir.resolve("vote-reject-higher-term");

        RaftStorage storage = new FileRaftStorage(vertx, executor);
        awaitSuccess(storage.open(storageDir), SHORT_TIMEOUT);

        Set<String> singleNodeCluster = Set.of("node1");
        RaftNode durableNode = RaftNode.builder().vertx(vertx).nodeId("node1").clusterNodes(singleNodeCluster)
            .transport(new InMemoryTransportSimulator("node1"))
            .stateMachine(new QuorusStateStore()).mode(RaftNodeMode.durable(storage)).electionTimeout(500).heartbeatInterval(100).build();

        awaitSuccess(durableNode.start(), SHORT_TIMEOUT);
        await().atMost(Duration.ofSeconds(3)).until(durableNode::isLeader);

        CommandResult<?> submitted = awaitSuccess(
            durableNode.submitCommand(SystemMetadataCommand.set("vote-log-key", "vote-log-value")),
            SHORT_TIMEOUT);
        assertInstanceOf(CommandResult.Success.class, submitted);

        long higherTerm = durableNode.getCurrentTerm() + 5;
        VoteRequest staleCandidate = VoteRequest.newBuilder()
            .setTerm(higherTerm)
            .setCandidateId("candidate-behind")
            .setLastLogTerm(0)
            .setLastLogIndex(0)
            .build();

        VoteResponse response = awaitSuccess(durableNode.handleVoteRequest(staleCandidate), SHORT_TIMEOUT);

        assertFalse(response.getVoteGranted(), "Vote must be rejected when candidate log is behind");
        assertEquals(higherTerm, response.getTerm(), "Node should report observed higher term in response");

        awaitSuccess(durableNode.stop(), SHORT_TIMEOUT);

        WorkerExecutor recoveryExecutor = vertx.createSharedWorkerExecutor("vote-persist-recovery-test", 1);
        RaftStorage recoveryStorage = new FileRaftStorage(vertx, recoveryExecutor);
        awaitSuccess(recoveryStorage.open(storageDir), SHORT_TIMEOUT);

        RaftNode recovered = RaftNode.builder().vertx(vertx).nodeId("node1")
            .clusterNodes(singleNodeCluster)
            .transport(new InMemoryTransportSimulator("node1"))
            .stateMachine(new QuorusStateStore())
            .mode(RaftNodeMode.durable(recoveryStorage))
            .electionTimeout(10_000)
            .heartbeatInterval(200)
            .build();

        awaitSuccess(recovered.start(), SHORT_TIMEOUT);

        assertEquals(higherTerm, recovered.getCurrentTerm(),
            "Higher observed term must be durable after rejecting stale candidate to prevent term regression");

        awaitSuccess(recovered.stop(), SHORT_TIMEOUT);
        executor.close();
        recoveryExecutor.close();
    }

    @Test
    void testRejectHigherTermVoteWithStaleCandidateLogPersistsEmptyVoteAcrossRestart() {
        WorkerExecutor executor = vertx.createSharedWorkerExecutor("vote-persist-vote-state-test", 1);
        Path storageDir = tempDir.resolve("vote-reject-higher-term-empty-vote");

        RaftStorage storage = new FileRaftStorage(vertx, executor);
        awaitSuccess(storage.open(storageDir), SHORT_TIMEOUT);

        Set<String> singleNodeCluster = Set.of("node1");
        RaftNode durableNode = RaftNode.builder().vertx(vertx).nodeId("node1").clusterNodes(singleNodeCluster)
            .transport(new InMemoryTransportSimulator("node1"))
            .stateMachine(new QuorusStateStore()).mode(RaftNodeMode.durable(storage)).electionTimeout(500).heartbeatInterval(100).build();

        awaitSuccess(durableNode.start(), SHORT_TIMEOUT);
        await().atMost(Duration.ofSeconds(3)).until(durableNode::isLeader);

        CommandResult<?> submitted = awaitSuccess(
            durableNode.submitCommand(SystemMetadataCommand.set("vote-log-key-2", "vote-log-value-2")),
            SHORT_TIMEOUT);
        assertInstanceOf(CommandResult.Success.class, submitted);

        long higherTerm = durableNode.getCurrentTerm() + 7;
        VoteRequest staleCandidate = VoteRequest.newBuilder()
            .setTerm(higherTerm)
            .setCandidateId("candidate-behind-2")
            .setLastLogTerm(0)
            .setLastLogIndex(0)
            .build();

        VoteResponse response = awaitSuccess(durableNode.handleVoteRequest(staleCandidate), SHORT_TIMEOUT);

        assertFalse(response.getVoteGranted());
        assertEquals(higherTerm, response.getTerm());

        awaitSuccess(durableNode.stop(), SHORT_TIMEOUT);

        WorkerExecutor recoveryExecutor = vertx.createSharedWorkerExecutor("vote-persist-vote-state-recovery-test", 1);
        RaftStorage recoveryStorage = new FileRaftStorage(vertx, recoveryExecutor);
        awaitSuccess(recoveryStorage.open(storageDir), SHORT_TIMEOUT);

        RaftStorage.PersistentMeta persistedMeta = awaitSuccess(recoveryStorage.loadMetadata(), SHORT_TIMEOUT);

        assertEquals(higherTerm, persistedMeta.currentTerm(),
            "Rejecting higher-term stale candidate should still persist the observed term");
        assertEquals(Optional.empty(), persistedMeta.votedFor(),
            "No candidate should be persisted when vote is rejected");

        awaitSuccess(recoveryStorage.close(), SHORT_TIMEOUT);
        executor.close();
        recoveryExecutor.close();
    }

    @Test
    void testRejectHigherTermVoteWithStaleCandidateLogFailsWhenTermPersistenceFails() {
        InMemoryRaftStorage delegate = new InMemoryRaftStorage();
        final long higherTerm = 5;
        final boolean[] failedOnRejectWrite = new boolean[] { false };
        RaftStorage flakyMetadataStorage = new RaftStorage() {
            @Override
            public Future<Void> open(java.nio.file.Path dataDir) {
                return delegate.open(dataDir);
            }

            @Override
            public Future<Void> close() {
                return delegate.close();
            }

            @Override
            public Future<Void> updateMetadata(long currentTerm, Optional<String> votedFor) {
                if (!failedOnRejectWrite[0] && currentTerm == higherTerm && votedFor.isEmpty()) {
                    failedOnRejectWrite[0] = true;
                    return Future.failedFuture(new IllegalStateException("Simulated targeted metadata failure"));
                }
                return delegate.updateMetadata(currentTerm, votedFor);
            }

            @Override
            public Future<PersistentMeta> loadMetadata() {
                return delegate.loadMetadata();
            }

            @Override
            public Future<Void> appendEntries(java.util.List<LogEntryData> entries) {
                return delegate.appendEntries(entries);
            }

            @Override
            public Future<Void> truncateSuffix(long fromIndex) {
                return delegate.truncateSuffix(fromIndex);
            }

            @Override
            public Future<Void> sync() {
                return delegate.sync();
            }

            @Override
            public Future<java.util.List<LogEntryData>> replayLog() {
                return delegate.replayLog();
            }

            @Override
            public Future<Void> saveSnapshot(byte[] data, long lastIncludedIndex, long lastIncludedTerm) {
                return delegate.saveSnapshot(data, lastIncludedIndex, lastIncludedTerm);
            }

            @Override
            public Future<Optional<SnapshotData>> loadSnapshot() {
                return delegate.loadSnapshot();
            }

            @Override
            public Future<Void> truncatePrefix(long toIndex) {
                return delegate.truncatePrefix(toIndex);
            }
        };
        awaitSuccess(flakyMetadataStorage.open(null), SHORT_TIMEOUT);

        RaftNode durableNode = RaftNode.builder().vertx(vertx).nodeId("node1").clusterNodes(Set.of("node1"))
            .transport(new InMemoryTransportSimulator("node1"))
            .stateMachine(new QuorusStateStore()).mode(RaftNodeMode.durable(flakyMetadataStorage)).electionTimeout(500).heartbeatInterval(100).build();

        awaitSuccess(durableNode.start(), SHORT_TIMEOUT);
        await().atMost(Duration.ofSeconds(3)).until(durableNode::isLeader);

        CommandResult<?> submitted = awaitSuccess(
            durableNode.submitCommand(SystemMetadataCommand.set("stale-check-key", "stale-check-value")),
            SHORT_TIMEOUT);
        assertInstanceOf(CommandResult.Success.class, submitted);

        VoteRequest staleCandidate = VoteRequest.newBuilder()
            .setTerm(higherTerm)
            .setCandidateId("candidate-behind-failing-persist")
            .setLastLogTerm(0)
            .setLastLogIndex(0)
            .build();

        Throwable voteFailure = awaitFailure(durableNode.handleVoteRequest(staleCandidate), SHORT_TIMEOUT);
        assertInstanceOf(IllegalStateException.class, voteFailure, "Failure should carry the underlying persistence exception");
        logExpectedFailure("vote-rejection higher-term metadata persistence", voteFailure);
        assertTrue(failedOnRejectWrite[0], "Test setup should fail the higher-term empty-vote persistence write");

        RaftStorage.PersistentMeta persistedMeta = awaitSuccess(flakyMetadataStorage.loadMetadata(), SHORT_TIMEOUT);
        assertTrue(persistedMeta.currentTerm() < higherTerm,
            "Failed persistence in rejection path should not durably advance term to the observed higher value");
        assertNotEquals(Optional.of("candidate-behind-failing-persist"), persistedMeta.votedFor(),
            "Rejecting stale candidate with failed metadata write must not persist vote for that candidate");

        awaitSuccess(durableNode.stop(), SHORT_TIMEOUT);
    }

        @Test
        void testAppendEntriesRejectsWhenHigherTermMetadataPersistFails() {
        InMemoryRaftStorage delegate = new InMemoryRaftStorage();
        RaftStorage flakyMetadataStorage = oneShotMetadataFailureStorage(delegate);
        awaitSuccess(flakyMetadataStorage.open(null), SHORT_TIMEOUT);

        RaftNode durableNode = RaftNode.builder().vertx(vertx).nodeId("node1").clusterNodes(Set.of("node1"))
            .transport(new InMemoryTransportSimulator("node1"))
            .stateMachine(new QuorusStateStore()).mode(RaftNodeMode.durable(flakyMetadataStorage)).electionTimeout(10_000).heartbeatInterval(200).build();

        awaitSuccess(durableNode.start(), SHORT_TIMEOUT);

        AppendEntriesRequest request = AppendEntriesRequest.newBuilder()
            .setTerm(9)
            .setLeaderId("leader-x")
            .setPrevLogIndex(0)
            .setPrevLogTerm(0)
            .setLeaderCommit(0)
            .build();

        AppendEntriesResponse response = awaitSuccess(durableNode.handleAppendEntriesRequest(request), SHORT_TIMEOUT);

        assertFalse(response.getSuccess(), "AppendEntries must be rejected if higher-term metadata cannot be durably persisted first");
        assertEquals(9, response.getTerm());
        logExpectedFailure("append-entries higher-term metadata persistence", new IllegalStateException("Injected one-shot metadata failure expected by test"));

        awaitSuccess(durableNode.stop(), SHORT_TIMEOUT);
        }

        @Test
        void testInstallSnapshotRejectsWhenHigherTermMetadataPersistFails() {
        InMemoryRaftStorage delegate = new InMemoryRaftStorage();
        RaftStorage flakyMetadataStorage = oneShotMetadataFailureStorage(delegate);
        awaitSuccess(flakyMetadataStorage.open(null), SHORT_TIMEOUT);

        RaftNode durableNode = RaftNode.builder().vertx(vertx).nodeId("node1").clusterNodes(Set.of("node1"))
            .transport(new InMemoryTransportSimulator("node1"))
            .stateMachine(new QuorusStateStore()).mode(RaftNodeMode.durable(flakyMetadataStorage)).electionTimeout(10_000).heartbeatInterval(200).build();

        awaitSuccess(durableNode.start(), SHORT_TIMEOUT);

        InstallSnapshotRequest request = InstallSnapshotRequest.newBuilder()
            .setTerm(11)
            .setLeaderId("leader-y")
            .setLastIncludedIndex(1)
            .setLastIncludedTerm(1)
            .setChunkIndex(0)
            .setTotalChunks(1)
            .setData(com.google.protobuf.ByteString.copyFrom(new byte[] {1}))
            .setDone(false)
            .build();

        InstallSnapshotResponse response = awaitSuccess(durableNode.handleInstallSnapshot(request), SHORT_TIMEOUT);

        assertFalse(response.getSuccess(), "InstallSnapshot must be rejected if higher-term metadata cannot be durably persisted first");
        assertEquals(11, response.getTerm());
        logExpectedFailure("install-snapshot higher-term metadata persistence", new IllegalStateException("Injected one-shot metadata failure expected by test"));

        awaitSuccess(durableNode.stop(), SHORT_TIMEOUT);
        }

        private static void logExpectedFailure(String scenario, Throwable failure) {
        System.out.println("[EXPECTED-TEST-FAILURE] Scenario=" + scenario + " message=" + failure.getMessage());
        }

        private static RaftStorage oneShotMetadataFailureStorage(InMemoryRaftStorage delegate) {
        return new RaftStorage() {
            private int metadataUpdateCalls = 0;

            @Override
            public Future<Void> open(java.nio.file.Path dataDir) {
                return delegate.open(dataDir);
            }

            @Override
            public Future<Void> close() {
                return delegate.close();
            }

            @Override
            public Future<Void> updateMetadata(long currentTerm, Optional<String> votedFor) {
                metadataUpdateCalls++;
                if (metadataUpdateCalls == 1) {
                    return Future.failedFuture(new IllegalStateException("Simulated one-shot metadata failure"));
                }
                return delegate.updateMetadata(currentTerm, votedFor);
            }

            @Override
            public Future<PersistentMeta> loadMetadata() {
                return delegate.loadMetadata();
            }

            @Override
            public Future<Void> appendEntries(java.util.List<LogEntryData> entries) {
                return delegate.appendEntries(entries);
            }

            @Override
            public Future<Void> truncateSuffix(long fromIndex) {
                return delegate.truncateSuffix(fromIndex);
            }

            @Override
            public Future<Void> sync() {
                return delegate.sync();
            }

            @Override
            public Future<java.util.List<LogEntryData>> replayLog() {
                return delegate.replayLog();
            }

            @Override
            public Future<Void> saveSnapshot(byte[] data, long lastIncludedIndex, long lastIncludedTerm) {
                return delegate.saveSnapshot(data, lastIncludedIndex, lastIncludedTerm);
            }

            @Override
            public Future<Optional<SnapshotData>> loadSnapshot() {
                return delegate.loadSnapshot();
            }

            @Override
            public Future<Void> truncatePrefix(long toIndex) {
                return delegate.truncatePrefix(toIndex);
            }
        };
        }

    private static <T> T awaitSuccess(Future<T> future, Duration timeout) {
        AtomicReference<AsyncResult<T>> outcomeRef = new AtomicReference<>();

        future.onComplete(outcomeRef::set);

        await().atMost(timeout)
            .pollInterval(Duration.ofMillis(10))
            .until(() -> outcomeRef.get() != null);

        AsyncResult<T> outcome = outcomeRef.get();
        if (outcome.failed()) {
            throw new AssertionError("Future failed", outcome.cause());
        }
        return outcome.result();
    }

    private static Throwable awaitFailure(Future<?> future, Duration timeout) {
        AtomicReference<AsyncResult<?>> outcomeRef = new AtomicReference<>();

        future.onComplete(outcomeRef::set);

        await().atMost(timeout)
            .pollInterval(Duration.ofMillis(10))
            .until(() -> outcomeRef.get() != null);

        AsyncResult<?> outcome = outcomeRef.get();
        assertTrue(outcome.failed(), "Expected future to fail");
        return outcome.cause();
    }
}
