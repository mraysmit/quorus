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

import dev.mars.quorus.controller.raft.grpc.VoteRequest;
import dev.mars.quorus.controller.raft.grpc.VoteResponse;
import dev.mars.quorus.controller.state.QuorusStateMachine;
import dev.mars.quorus.controller.state.SystemMetadataCommand;
import org.awaitility.Awaitility;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RaftNode implementation using real components (no mocking).
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */
class RaftNodeTest {

    private io.vertx.core.Vertx vertx;
    private RaftNode node1;
    private RaftNode node2;
    private RaftNode node3;
    private InMemoryTransport transport1;
    private InMemoryTransport transport2;
    private InMemoryTransport transport3;
    private QuorusStateMachine stateMachine1;
    private QuorusStateMachine stateMachine2;
    private QuorusStateMachine stateMachine3;

    @BeforeEach
    void setUp() {
        vertx = io.vertx.core.Vertx.vertx();
        // Clear any existing transports
        InMemoryTransport.clearAllTransports();

        // Create cluster nodes
        Set<String> clusterNodes = Set.of("node1", "node2", "node3");

        // Create transports
        transport1 = new InMemoryTransport("node1");
        transport2 = new InMemoryTransport("node2");
        transport3 = new InMemoryTransport("node3");

        // Create state machines
        stateMachine1 = new QuorusStateMachine();
        stateMachine2 = new QuorusStateMachine();
        stateMachine3 = new QuorusStateMachine();

        // Create Raft nodes with shorter timeouts for testing
        node1 = new RaftNode(vertx, "node1", clusterNodes, transport1, stateMachine1, 1000, 200);
        node2 = new RaftNode(vertx, "node2", clusterNodes, transport2, stateMachine2, 1000, 200);
        node3 = new RaftNode(vertx, "node3", clusterNodes, transport3, stateMachine3, 1000, 200);
    }

    @AfterEach
    void tearDown() {
        if (node1 != null) node1.stop();
        if (node2 != null) node2.stop();
        if (node3 != null) node3.stop();
        if (vertx != null) vertx.close();
        InMemoryTransport.clearAllTransports();
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
        
        node1.start().join();
        assertTrue(transport1.isRunning());
        assertEquals(RaftNode.State.FOLLOWER, node1.getState());
        
        node1.stop().join();
        assertFalse(transport1.isRunning());
    }

    @Test
    void testSingleNodeElection() {
        // Create a single-node cluster
        Set<String> singleNodeCluster = Set.of("node1");
        RaftNode singleNode = new RaftNode(vertx, "node1", singleNodeCluster, transport1, stateMachine1, 500, 100);
        
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
        
        // Wait for a leader to be elected
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .until(() -> {
                    long leaderCount = Set.of(node1, node2, node3).stream()
                            .mapToLong(node -> node.isLeader() ? 1 : 0)
                            .sum();
                    return leaderCount == 1;
                });
        
        // Verify exactly one leader
        long leaderCount = Set.of(node1, node2, node3).stream()
                .mapToLong(node -> node.isLeader() ? 1 : 0)
                .sum();
        assertEquals(1, leaderCount);
        
        // Verify all nodes are in valid states
        Set.of(node1, node2, node3).forEach(node -> {
            assertTrue(node.getState() == RaftNode.State.LEADER || 
                      node.getState() == RaftNode.State.FOLLOWER);
        });
    }

    @Test
    void testCommandSubmissionToNonLeader() {
        node1.start().join();
        
        // Node starts as follower, command submission should fail
        SystemMetadataCommand command = SystemMetadataCommand.set("test-key", "test-value");
        
        CompletableFuture<Object> future = node1.submitCommand(command);
        
        // Verify the exception
        assertThrows(Exception.class, () -> {
            try {
                future.get(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                assertTrue(e.getCause() instanceof IllegalStateException);
                assertTrue(e.getCause().getMessage().contains("Not the leader"));
                throw e;
            }
        });
    }

    @Test
    void testStateMachineOperations() {
        // Test state machine directly
        SystemMetadataCommand setCommand = SystemMetadataCommand.set("version", "2.1");
        Object result = stateMachine1.apply(setCommand);
        
        assertEquals("2.0", result); // Previous value
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
        CompletableFuture<VoteResponse> future = transport1.sendVoteRequest("node2", voteRequest);
        
        assertDoesNotThrow(() -> {
            VoteResponse response = future.get(1, TimeUnit.SECONDS);
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
        RaftNode singleNode = new RaftNode(vertx, "node1", singleNodeCluster, 
                                          new InMemoryTransport("node1"), 
                                          new QuorusStateMachine(), 500, 100);
        singleNode.start();
        
        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .until(() -> singleNode.getState() == RaftNode.State.LEADER);
        
        singleNode.stop();
    }
}
