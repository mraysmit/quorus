/*
 * Copyright 2024 Quorus Project
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

import dev.mars.quorus.controller.state.QuorusStateMachine;
import dev.mars.quorus.controller.state.SystemMetadataCommand;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Raft failure scenarios and edge cases.
 */
class RaftFailureTest {

    private RaftNode node1;
    private RaftNode node2;
    private RaftNode node3;
    private InMemoryTransport transport1;
    private InMemoryTransport transport2;
    private InMemoryTransport transport3;

    @BeforeEach
    void setUp() {
        InMemoryTransport.clearAllTransports();

        Set<String> clusterNodes = Set.of("node1", "node2", "node3");

        transport1 = new InMemoryTransport("node1");
        transport2 = new InMemoryTransport("node2");
        transport3 = new InMemoryTransport("node3");

        node1 = new RaftNode("node1", clusterNodes, transport1, new QuorusStateMachine(), 600, 120);
        node2 = new RaftNode("node2", clusterNodes, transport2, new QuorusStateMachine(), 600, 120);
        node3 = new RaftNode("node3", clusterNodes, transport3, new QuorusStateMachine(), 600, 120);
    }

    @AfterEach
    void tearDown() {
        if (node1 != null) node1.stop();
        if (node2 != null) node2.stop();
        if (node3 != null) node3.stop();
        InMemoryTransport.clearAllTransports();
    }

    @Test
    void testCommandSubmissionToFollower() {
        node1.start();
        
        // Node starts as follower
        assertEquals(RaftNode.State.FOLLOWER, node1.getState());
        
        // Try to submit command to follower
        SystemMetadataCommand command = SystemMetadataCommand.set("key", "value");
        CompletableFuture<Object> future = node1.submitCommand(command);
        
        // Should fail immediately
        assertTrue(future.isCompletedExceptionally());
        
        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            future.get();
        });
        
        assertTrue(exception.getCause() instanceof IllegalStateException);
        assertTrue(exception.getCause().getMessage().contains("Not the leader"));
    }

    @Test
    void testDoubleStartStop() {
        // Test double start
        node1.start();
        assertTrue(transport1.isRunning());
        
        // Second start should be safe
        node1.start();
        assertTrue(transport1.isRunning());
        
        // Test double stop
        node1.stop();
        assertFalse(transport1.isRunning());
        
        // Second stop should be safe
        node1.stop();
        assertFalse(transport1.isRunning());
    }

    @Test
    void testLeaderFailureAndRecovery() {
        // Start all nodes
        node1.start();
        node2.start();
        node3.start();
        
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
        leader.stop();
        
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
        node1.start();
        node2.start();
        node3.start();
        
        // Wait for initial leader
        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .until(() -> Set.of(node1, node2, node3).stream()
                        .anyMatch(RaftNode::isLeader));
        
        // Simulate network partition by stopping one node
        node3.stop();
        
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
        // Test empty cluster
        assertThrows(Exception.class, () -> {
            new RaftNode("test", Set.of(), transport1, new QuorusStateMachine());
        });
    }

    @Test
    void testTransportFailures() {
        // Test transport that fails to start
        RaftTransport failingTransport = new RaftTransport() {
            @Override
            public void start(java.util.function.Consumer<Object> messageHandler) {
                throw new RuntimeException("Transport failed to start");
            }
            
            @Override
            public void stop() {}
            
            @Override
            public CompletableFuture<VoteResponse> sendVoteRequest(String nodeId, VoteRequest request) {
                return CompletableFuture.failedFuture(new RuntimeException("Network error"));
            }
            
            @Override
            public CompletableFuture<AppendEntriesResponse> sendAppendEntries(String nodeId, AppendEntriesRequest request) {
                return CompletableFuture.failedFuture(new RuntimeException("Network error"));
            }
            
            @Override
            public String getLocalNodeId() {
                return "failing";
            }
            
            @Override
            public boolean isRunning() {
                return false;
            }
        };
        
        RaftNode failingNode = new RaftNode("failing", Set.of("failing"), 
                failingTransport, new QuorusStateMachine());
        
        // Should handle transport failure gracefully
        assertThrows(RuntimeException.class, () -> {
            failingNode.start();
        });
    }

    @Test
    void testStateMachineFailures() {
        // Test state machine that throws exceptions
        RaftStateMachine failingStateMachine = new RaftStateMachine() {
            @Override
            public Object apply(Object command) {
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
        
        RaftNode fast1 = new RaftNode("fast1", clusterNodes, 
                new InMemoryTransport("fast1"), new QuorusStateMachine(), 100, 50);
        RaftNode fast2 = new RaftNode("fast2", clusterNodes, 
                new InMemoryTransport("fast2"), new QuorusStateMachine(), 100, 50);
        RaftNode fast3 = new RaftNode("fast3", clusterNodes, 
                new InMemoryTransport("fast3"), new QuorusStateMachine(), 100, 50);
        
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
        VoteRequest voteRequest = new VoteRequest(1, "candidate", 0, 0);
        assertNotNull(voteRequest.toString());
        assertEquals(1, voteRequest.getTerm());
        assertEquals("candidate", voteRequest.getCandidateId());
        
        // Test vote response validation
        VoteResponse voteResponse = new VoteResponse(1, true, "voter");
        assertNotNull(voteResponse.toString());
        assertEquals(1, voteResponse.getTerm());
        assertTrue(voteResponse.isVoteGranted());
        assertEquals("voter", voteResponse.getNodeId());
        
        // Test append entries request validation
        AppendEntriesRequest appendRequest = new AppendEntriesRequest(1, "leader", 0, 0, null, 0);
        assertNotNull(appendRequest.toString());
        assertTrue(appendRequest.isHeartbeat());
        
        // Test append entries response validation
        AppendEntriesResponse appendResponse = new AppendEntriesResponse(1, true, "follower", 0);
        assertNotNull(appendResponse.toString());
        assertTrue(appendResponse.isSuccess());
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
        RaftNode single = new RaftNode("single", singleNode, 
                new InMemoryTransport("single"), new QuorusStateMachine(), 300, 100);
        
        single.start();
        
        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .until(single::isLeader);
        
        assertEquals("single", single.getLeaderId());
        
        single.stop();
    }
}
