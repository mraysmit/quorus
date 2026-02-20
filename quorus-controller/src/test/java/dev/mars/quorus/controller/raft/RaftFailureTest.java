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

import dev.mars.quorus.controller.raft.grpc.AppendEntriesRequest;
import dev.mars.quorus.controller.raft.grpc.AppendEntriesResponse;
import dev.mars.quorus.controller.raft.grpc.InstallSnapshotRequest;
import dev.mars.quorus.controller.raft.grpc.InstallSnapshotResponse;
import io.vertx.core.Future;
import dev.mars.quorus.controller.raft.grpc.VoteRequest;
import dev.mars.quorus.controller.raft.grpc.VoteResponse;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.RaftCommand;
import dev.mars.quorus.controller.state.SystemMetadataCommand;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Raft failure scenarios and edge cases.
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */
class RaftFailureTest {

    private RaftNode node1;
    private RaftNode node2;
    private RaftNode node3;
    private InMemoryTransportSimulator transport1;
    private InMemoryTransportSimulator transport2;
    private InMemoryTransportSimulator transport3;
    private io.vertx.core.Vertx vertx;

    @BeforeEach
    void setUp() {
        vertx = io.vertx.core.Vertx.vertx();
        InMemoryTransportSimulator.clearAllTransports();

        Set<String> clusterNodes = Set.of("node1", "node2", "node3");

        transport1 = new InMemoryTransportSimulator("node1");
        transport2 = new InMemoryTransportSimulator("node2");
        transport3 = new InMemoryTransportSimulator("node3");

        node1 = new RaftNode(vertx, "node1", clusterNodes, transport1, new QuorusStateStore(), 600, 120);
        node2 = new RaftNode(vertx, "node2", clusterNodes, transport2, new QuorusStateStore(), 600, 120);
        node3 = new RaftNode(vertx, "node3", clusterNodes, transport3, new QuorusStateStore(), 600, 120);
    }

    @AfterEach
    void tearDown() {
        if (node1 != null) node1.stop();
        if (node2 != null) node2.stop();
        if (node3 != null) node3.stop();
        if (vertx != null) vertx.close();
        InMemoryTransportSimulator.clearAllTransports();
    }

    @Test
    void testCommandSubmissionToFollower() {
        node1.start();
        
        // Node starts as follower
        assertEquals(RaftNode.State.FOLLOWER, node1.getState());
        
        // Try to submit command to follower
        SystemMetadataCommand command = SystemMetadataCommand.set("key", "value");
        Future<Object> future = node1.submitCommand(command);
        
        // Should fail
        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            future.toCompletionStage().toCompletableFuture().get(1, TimeUnit.SECONDS);
        });
        
        assertTrue(exception.getCause() instanceof IllegalStateException);
        assertTrue(exception.getCause().getMessage().contains("Not the leader"));
    }

    @Test
    void testDoubleStartStop() {
        // Test double start
        node1.start().toCompletionStage().toCompletableFuture().join();
        assertTrue(transport1.isRunning());
        
        // Second start should be safe
        node1.start().toCompletionStage().toCompletableFuture().join();
        assertTrue(transport1.isRunning());
        
        // Test double stop
        node1.stop().toCompletionStage().toCompletableFuture().join();
        assertFalse(transport1.isRunning());
        
        // Second stop should be safe
        node1.stop().toCompletionStage().toCompletableFuture().join();
        assertFalse(transport1.isRunning());
    }

    @Test
    void testLeaderFailureAndRecovery() {
        // Start all nodes
        node1.start().toCompletionStage().toCompletableFuture().join();
        node2.start().toCompletionStage().toCompletableFuture().join();
        node3.start().toCompletionStage().toCompletableFuture().join();
        
        // Wait for leader election
        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .until(() -> Set.of(node1, node2, node3).stream()
                        .anyMatch(RaftNode::isLeader));
        
        // Find and stop the leader
        RaftNode leader = Set.of(node1, node2, node3).stream()
                .filter(RaftNode::isLeader)
                .findFirst()
                .orElse(null);
        
        assertNotNull(leader);
        leader.stop().toCompletionStage().toCompletableFuture().join();
        
        // Wait for new leader election among remaining nodes
        Set<RaftNode> remainingNodes = Set.of(node1, node2, node3).stream()
                .filter(node -> node != leader)
                .collect(java.util.stream.Collectors.toSet());
        
        Awaitility.await()
                .atMost(Duration.ofSeconds(4))
                .until(() -> remainingNodes.stream()
                        .anyMatch(RaftNode::isLeader));
        
        // Verify exactly one new leader
        long leaderCount = remainingNodes.stream()
                .mapToLong(node -> node.isLeader() ? 1 : 0)
                .sum();
        assertEquals(1, leaderCount);
    }

    @Test
    void testNetworkPartition() {
        // Start all nodes
        node1.start().toCompletionStage().toCompletableFuture().join();
        node2.start().toCompletionStage().toCompletableFuture().join();
        node3.start().toCompletionStage().toCompletableFuture().join();
        
        // Wait for initial leader
        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .until(() -> Set.of(node1, node2, node3).stream()
                        .anyMatch(RaftNode::isLeader));
        
        // Simulate network partition by stopping one node
        node3.stop().toCompletionStage().toCompletableFuture().join();
        
        // Remaining nodes should still have a leader (majority)
        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .until(() -> Set.of(node1, node2).stream()
                        .anyMatch(RaftNode::isLeader));
        
        long leaderCount = Set.of(node1, node2).stream()
                .mapToLong(node -> node.isLeader() ? 1 : 0)
                .sum();
        assertEquals(1, leaderCount);
    }

    @Test
    void testInvalidClusterConfiguration() {
        // Test empty cluster - should not throw exception but should handle gracefully
        RaftNode emptyClusterNode = new RaftNode(vertx, "test", Set.of(), transport1, new QuorusStateStore());
        assertNotNull(emptyClusterNode);
        assertEquals("test", emptyClusterNode.getNodeId());
        assertEquals(RaftNode.State.FOLLOWER, emptyClusterNode.getState());
    }

    @Test
    void testTransportFailures() {
        // Test transport that fails to start
        RaftTransport failingTransport = new RaftTransport() {
            @Override
            public void start(java.util.function.Consumer<RaftMessage> messageHandler) {
                throw new RuntimeException("Transport failed to start");
            }
            
            @Override
            public void stop() {}
            
            @Override
            public Future<VoteResponse> sendVoteRequest(String nodeId, VoteRequest request) {
                return Future.failedFuture(new RuntimeException("Network error"));
            }
            
            @Override
            public Future<AppendEntriesResponse> sendAppendEntries(String nodeId, AppendEntriesRequest request) {
                return Future.failedFuture(new RuntimeException("Network error"));
            }

            @Override
            public Future<InstallSnapshotResponse> sendInstallSnapshot(String nodeId, InstallSnapshotRequest request) {
                return Future.failedFuture(new RuntimeException("Network error"));
            }
            
            public String getLocalNodeId() {
                return "failing";
            }
            
            public boolean isRunning() {
                return false;
            }
        };
        
        RaftNode failingNode = new RaftNode(vertx, "failing", Set.of("failing"), 
                failingTransport, new QuorusStateStore());
        
        // Should handle transport failure gracefully
        Future<Void> future = failingNode.start();
        
        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            future.toCompletionStage().toCompletableFuture().get(1, TimeUnit.SECONDS);
        });
        
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertEquals("Transport failed to start", exception.getCause().getMessage());
    }

    @Test
    void testStateMachineFailures() {
        // Test state machine that throws exceptions
        RaftLogApplicator failingStateMachine = new RaftLogApplicator() {
            @Override
            public Object apply(RaftCommand command) {
                throw new RuntimeException("State machine error");
            }
            
            @Override
            public byte[] takeSnapshot() {
                throw new RuntimeException("Snapshot error");
            }
            
            @Override
            public void restoreSnapshot(byte[] snapshot) {
                throw new RuntimeException("Restore error");
            }
            
            @Override
            public long getLastAppliedIndex() {
                return 0;
            }

            @Override
            public void setLastAppliedIndex(long index) {
                throw new RuntimeException("Set index error");
            }

            @Override
            public void reset() {
                throw new RuntimeException("Reset error");
            }
        };
        
        // Test snapshot failure
        assertThrows(RuntimeException.class, () -> {
            failingStateMachine.takeSnapshot();
        });
        
        // Test restore failure
        assertThrows(RuntimeException.class, () -> {
            failingStateMachine.restoreSnapshot(new byte[0]);
        });
        
        // Test reset failure
        assertThrows(RuntimeException.class, () -> {
            failingStateMachine.reset();
        });
    }

    @Test
    void testConcurrentElections() {
        // Start nodes with very short election timeouts to force concurrent elections
        Set<String> clusterNodes = Set.of("fast1", "fast2", "fast3");
        
        RaftNode fast1 = new RaftNode(vertx, "fast1", clusterNodes, 
                new InMemoryTransportSimulator("fast1"), new QuorusStateStore(), 100, 50);
        RaftNode fast2 = new RaftNode(vertx, "fast2", clusterNodes, 
                new InMemoryTransportSimulator("fast2"), new QuorusStateStore(), 100, 50);
        RaftNode fast3 = new RaftNode(vertx, "fast3", clusterNodes, 
                new InMemoryTransportSimulator("fast3"), new QuorusStateStore(), 100, 50);
        
        try {
            // Start all nodes simultaneously
            fast1.start();
            fast2.start();
            fast3.start();
            
            // Eventually should converge to one leader
            Awaitility.await()
                    .atMost(Duration.ofSeconds(5))
                    .until(() -> {
                        long leaderCount = Set.of(fast1, fast2, fast3).stream()
                                .mapToLong(node -> node.isLeader() ? 1 : 0)
                                .sum();
                        return leaderCount == 1;
                    });
            
            // Verify exactly one leader
            long finalLeaderCount = Set.of(fast1, fast2, fast3).stream()
                    .mapToLong(node -> node.isLeader() ? 1 : 0)
                    .sum();
            assertEquals(1, finalLeaderCount);
            
        } finally {
            fast1.stop();
            fast2.stop();
            fast3.stop();
        }
    }

    @Test
    void testMessageValidation() {
        // Test vote request validation
        VoteRequest voteRequest = VoteRequest.newBuilder()
                .setTerm(1)
                .setCandidateId("candidate")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        assertNotNull(voteRequest.toString());
        assertEquals(1, voteRequest.getTerm());
        assertEquals("candidate", voteRequest.getCandidateId());
        
        // Test vote response validation
        VoteResponse voteResponse = VoteResponse.newBuilder()
                .setTerm(1)
                .setVoteGranted(true)
                .build();
        assertNotNull(voteResponse.toString());
        assertEquals(1, voteResponse.getTerm());
        assertTrue(voteResponse.getVoteGranted());
        
        // Test append entries request validation
        AppendEntriesRequest appendRequest = AppendEntriesRequest.newBuilder()
                .setTerm(1)
                .setLeaderId("leader")
                .setPrevLogIndex(0)
                .setPrevLogTerm(0)
                .setLeaderCommit(0)
                .build();
        assertNotNull(appendRequest.toString());
        assertEquals(0, appendRequest.getEntriesCount());
        
        // Test append entries response validation
        AppendEntriesResponse appendResponse = AppendEntriesResponse.newBuilder()
                .setTerm(1)
                .setSuccess(true)
                .setMatchIndex(0)
                .build();
        assertNotNull(appendResponse.toString());
        assertTrue(appendResponse.getSuccess());
    }

    @Test
    void testNodeIdValidation() {
        assertEquals("node1", node1.getNodeId());
        assertEquals("node2", node2.getNodeId());
        assertEquals("node3", node3.getNodeId());
        
        // Test leader ID when not leader
        assertNull(node1.getLeaderId()); // Not leader initially
        
        // Test single node becoming leader
        Set<String> singleNode = Set.of("single");
        RaftNode single = new RaftNode(vertx, "single", singleNode, 
                new InMemoryTransportSimulator("single"), new QuorusStateStore(), 300, 100);
        
        single.start();
        
        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .until(single::isLeader);
        
        assertEquals("single", single.getLeaderId());
        
        single.stop();
    }
}
