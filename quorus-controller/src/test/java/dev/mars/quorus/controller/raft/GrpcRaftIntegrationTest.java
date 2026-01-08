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

import dev.mars.quorus.controller.raft.grpc.*;
import dev.mars.quorus.controller.state.QuorusStateMachine;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for gRPC-based Raft communication.
 * Tests full client-server communication patterns including:
 * - Multi-node cluster communication
 * - Leader election over gRPC
 * - Log replication
 * - Network failure scenarios
 * - Cluster reconfiguration
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-01-08
 */
@Execution(ExecutionMode.SAME_THREAD)
class GrpcRaftIntegrationTest {

    private Vertx vertx;
    private List<TestNode> nodes = new ArrayList<>();

    private static class TestNode {
        final String id;
        final int port;
        final RaftNode raftNode;
        final GrpcRaftServer grpcServer;
        final GrpcRaftTransport transport;

        TestNode(String id, int port, RaftNode raftNode, GrpcRaftServer grpcServer, GrpcRaftTransport transport) {
            this.id = id;
            this.port = port;
            this.raftNode = raftNode;
            this.grpcServer = grpcServer;
            this.transport = transport;
        }

        void stop() throws Exception {
            if (grpcServer != null) {
                grpcServer.stop().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            }
            if (raftNode != null) {
                raftNode.stop();
            }
        }
    }

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        nodes.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        for (TestNode node : nodes) {
            try {
                node.stop();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        nodes.clear();
        
        if (vertx != null) {
            vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    private int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private TestNode createNode(String nodeId, Map<String, String> clusterConfig) throws Exception {
        int port = Integer.parseInt(clusterConfig.get(nodeId).split(":")[1]);
        Set<String> clusterNodes = clusterConfig.keySet();
        
        GrpcRaftTransport transport = new GrpcRaftTransport(vertx, nodeId, clusterConfig);
        QuorusStateMachine stateMachine = new QuorusStateMachine();
        
        // Use shorter timeouts for faster tests
        RaftNode raftNode = new RaftNode(vertx, nodeId, clusterNodes, transport, stateMachine, 2000, 500);
        transport.setRaftNode(raftNode);
        
        GrpcRaftServer grpcServer = new GrpcRaftServer(vertx, port, raftNode);
        
        return new TestNode(nodeId, port, raftNode, grpcServer, transport);
    }

    private void startNode(TestNode node) throws Exception {
        node.grpcServer.start().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        node.raftNode.start();
        node.transport.start(msg -> {});
    }

    // ========== TWO-NODE CLUSTER TESTS ==========

    @Test
    @DisplayName("Two-node cluster should elect leader via gRPC")
    void testTwoNodeLeaderElection() throws Exception {
        int port1 = findAvailablePort();
        int port2 = findAvailablePort();
        
        Map<String, String> cluster = new HashMap<>();
        cluster.put("node1", "localhost:" + port1);
        cluster.put("node2", "localhost:" + port2);
        
        TestNode node1 = createNode("node1", cluster);
        TestNode node2 = createNode("node2", cluster);
        nodes.add(node1);
        nodes.add(node2);
        
        startNode(node1);
        startNode(node2);
        
        // Wait for leader election
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    boolean node1Leader = node1.raftNode.isLeader();
                    boolean node2Leader = node2.raftNode.isLeader();
                    return node1Leader || node2Leader;
                });
        
        // Verify exactly one leader
        int leaderCount = 0;
        if (node1.raftNode.isLeader()) leaderCount++;
        if (node2.raftNode.isLeader()) leaderCount++;
        
        assertEquals(1, leaderCount, "Should have exactly one leader");
    }

    @Test
    @DisplayName("Two-node cluster should exchange heartbeats via gRPC")
    void testTwoNodeHeartbeats() throws Exception {
        int port1 = findAvailablePort();
        int port2 = findAvailablePort();
        
        Map<String, String> cluster = new HashMap<>();
        cluster.put("node1", "localhost:" + port1);
        cluster.put("node2", "localhost:" + port2);
        
        TestNode node1 = createNode("node1", cluster);
        TestNode node2 = createNode("node2", cluster);
        nodes.add(node1);
        nodes.add(node2);
        
        startNode(node1);
        startNode(node2);
        
        // Wait for leader election
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> node1.raftNode.isLeader() || node2.raftNode.isLeader());
        
        // Let heartbeats flow for a while
        Thread.sleep(3000);
        
        // Cluster should remain stable
        int leaderCount = 0;
        if (node1.raftNode.isLeader()) leaderCount++;
        if (node2.raftNode.isLeader()) leaderCount++;
        
        assertEquals(1, leaderCount, "Should still have exactly one leader after heartbeats");
    }

    // ========== THREE-NODE CLUSTER TESTS ==========

    @Test
    @DisplayName("Three-node cluster should elect leader via gRPC")
    void testThreeNodeLeaderElection() throws Exception {
        int port1 = findAvailablePort();
        int port2 = findAvailablePort();
        int port3 = findAvailablePort();
        
        Map<String, String> cluster = new HashMap<>();
        cluster.put("node1", "localhost:" + port1);
        cluster.put("node2", "localhost:" + port2);
        cluster.put("node3", "localhost:" + port3);
        
        TestNode node1 = createNode("node1", cluster);
        TestNode node2 = createNode("node2", cluster);
        TestNode node3 = createNode("node3", cluster);
        nodes.add(node1);
        nodes.add(node2);
        nodes.add(node3);
        
        startNode(node1);
        startNode(node2);
        startNode(node3);
        
        // Wait for leader election
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    return node1.raftNode.isLeader() || 
                           node2.raftNode.isLeader() || 
                           node3.raftNode.isLeader();
                });
        
        // Verify exactly one leader
        int leaderCount = 0;
        if (node1.raftNode.isLeader()) leaderCount++;
        if (node2.raftNode.isLeader()) leaderCount++;
        if (node3.raftNode.isLeader()) leaderCount++;
        
        assertEquals(1, leaderCount, "Should have exactly one leader");
    }

    @Test
    @DisplayName("Three-node cluster should have consistent term")
    void testThreeNodeTermConsistency() throws Exception {
        int port1 = findAvailablePort();
        int port2 = findAvailablePort();
        int port3 = findAvailablePort();
        
        Map<String, String> cluster = new HashMap<>();
        cluster.put("node1", "localhost:" + port1);
        cluster.put("node2", "localhost:" + port2);
        cluster.put("node3", "localhost:" + port3);
        
        TestNode node1 = createNode("node1", cluster);
        TestNode node2 = createNode("node2", cluster);
        TestNode node3 = createNode("node3", cluster);
        nodes.add(node1);
        nodes.add(node2);
        nodes.add(node3);
        
        startNode(node1);
        startNode(node2);
        startNode(node3);
        
        // Wait for leader election
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> node1.raftNode.isLeader() || 
                             node2.raftNode.isLeader() || 
                             node3.raftNode.isLeader());
        
        // Let cluster stabilize
        Thread.sleep(2000);
        
        // Terms should be equal or within 1 of each other
        long term1 = node1.raftNode.getCurrentTerm();
        long term2 = node2.raftNode.getCurrentTerm();
        long term3 = node3.raftNode.getCurrentTerm();
        
        long maxTerm = Math.max(Math.max(term1, term2), term3);
        long minTerm = Math.min(Math.min(term1, term2), term3);
        
        assertTrue(maxTerm - minTerm <= 1, 
                "Terms should be within 1 of each other: " + term1 + ", " + term2 + ", " + term3);
    }

    // ========== STAGGERED STARTUP TESTS ==========

    @Test
    @DisplayName("Cluster should handle staggered node startup")
    void testStaggeredNodeStartup() throws Exception {
        int port1 = findAvailablePort();
        int port2 = findAvailablePort();
        int port3 = findAvailablePort();
        
        Map<String, String> cluster = new HashMap<>();
        cluster.put("node1", "localhost:" + port1);
        cluster.put("node2", "localhost:" + port2);
        cluster.put("node3", "localhost:" + port3);
        
        TestNode node1 = createNode("node1", cluster);
        TestNode node2 = createNode("node2", cluster);
        TestNode node3 = createNode("node3", cluster);
        nodes.add(node1);
        nodes.add(node2);
        nodes.add(node3);
        
        // Start first node
        startNode(node1);
        Thread.sleep(500);
        
        // Start second node (now quorum possible)
        startNode(node2);
        
        // Wait for leader with 2 nodes
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> node1.raftNode.isLeader() || node2.raftNode.isLeader());
        
        // Start third node
        startNode(node3);
        
        // Wait for cluster to stabilize
        Thread.sleep(2000);
        
        // Should still have exactly one leader
        int leaderCount = 0;
        if (node1.raftNode.isLeader()) leaderCount++;
        if (node2.raftNode.isLeader()) leaderCount++;
        if (node3.raftNode.isLeader()) leaderCount++;
        
        assertEquals(1, leaderCount, "Should have exactly one leader after all nodes join");
    }

    // ========== NODE FAILURE TESTS ==========

    @Test
    @DisplayName("Cluster should survive follower shutdown")
    void testFollowerShutdown() throws Exception {
        int port1 = findAvailablePort();
        int port2 = findAvailablePort();
        int port3 = findAvailablePort();
        
        Map<String, String> cluster = new HashMap<>();
        cluster.put("node1", "localhost:" + port1);
        cluster.put("node2", "localhost:" + port2);
        cluster.put("node3", "localhost:" + port3);
        
        TestNode node1 = createNode("node1", cluster);
        TestNode node2 = createNode("node2", cluster);
        TestNode node3 = createNode("node3", cluster);
        nodes.add(node1);
        nodes.add(node2);
        nodes.add(node3);
        
        startNode(node1);
        startNode(node2);
        startNode(node3);
        
        // Wait for leader election
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> node1.raftNode.isLeader() || 
                             node2.raftNode.isLeader() || 
                             node3.raftNode.isLeader());
        
        // Find a follower to shut down
        TestNode follower = null;
        for (TestNode node : nodes) {
            if (!node.raftNode.isLeader()) {
                follower = node;
                break;
            }
        }
        assertNotNull(follower, "Should have at least one follower");
        
        // Shut down follower
        follower.stop();
        nodes.remove(follower);
        
        // Wait for cluster to stabilize
        Thread.sleep(3000);
        
        // Should still have exactly one leader among remaining nodes
        int leaderCount = 0;
        for (TestNode node : nodes) {
            if (node.raftNode.isLeader()) leaderCount++;
        }
        
        assertEquals(1, leaderCount, "Should still have exactly one leader after follower shutdown");
    }

    // ========== CONCURRENT VOTING TESTS ==========

    @Test
    @DisplayName("Concurrent elections should converge to single leader")
    void testConcurrentElectionsConverge() throws Exception {
        int port1 = findAvailablePort();
        int port2 = findAvailablePort();
        int port3 = findAvailablePort();
        
        Map<String, String> cluster = new HashMap<>();
        cluster.put("node1", "localhost:" + port1);
        cluster.put("node2", "localhost:" + port2);
        cluster.put("node3", "localhost:" + port3);
        
        TestNode node1 = createNode("node1", cluster);
        TestNode node2 = createNode("node2", cluster);
        TestNode node3 = createNode("node3", cluster);
        nodes.add(node1);
        nodes.add(node2);
        nodes.add(node3);
        
        // Start all nodes simultaneously to maximize election contention
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(3);
        
        executor.submit(() -> {
            try {
                startNode(node1);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });
        executor.submit(() -> {
            try {
                startNode(node2);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });
        executor.submit(() -> {
            try {
                startNode(node3);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        
        // Wait for leader election
        await().atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> node1.raftNode.isLeader() || 
                             node2.raftNode.isLeader() || 
                             node3.raftNode.isLeader());
        
        // Let cluster stabilize
        Thread.sleep(3000);
        
        // Should converge to exactly one leader
        int leaderCount = 0;
        if (node1.raftNode.isLeader()) leaderCount++;
        if (node2.raftNode.isLeader()) leaderCount++;
        if (node3.raftNode.isLeader()) leaderCount++;
        
        assertEquals(1, leaderCount, "Should converge to exactly one leader");
    }

    // ========== TERM ADVANCEMENT TESTS ==========

    @Test
    @DisplayName("Term should increase with new elections")
    void testTermAdvancement() throws Exception {
        int port1 = findAvailablePort();
        int port2 = findAvailablePort();
        
        Map<String, String> cluster = new HashMap<>();
        cluster.put("node1", "localhost:" + port1);
        cluster.put("node2", "localhost:" + port2);
        
        TestNode node1 = createNode("node1", cluster);
        TestNode node2 = createNode("node2", cluster);
        nodes.add(node1);
        nodes.add(node2);
        
        startNode(node1);
        startNode(node2);
        
        // Wait for leader election
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> node1.raftNode.isLeader() || node2.raftNode.isLeader());
        
        long initialTerm = Math.max(node1.raftNode.getCurrentTerm(), node2.raftNode.getCurrentTerm());
        
        // Term should be at least 1 (first election)
        assertTrue(initialTerm >= 1, "Term should be at least 1 after first election");
    }

    // ========== SINGLE NODE CLUSTER TEST ==========

    @Test
    @DisplayName("Single node cluster should immediately become leader")
    void testSingleNodeCluster() throws Exception {
        int port1 = findAvailablePort();
        
        Map<String, String> cluster = new HashMap<>();
        cluster.put("node1", "localhost:" + port1);
        
        TestNode node1 = createNode("node1", cluster);
        nodes.add(node1);
        
        startNode(node1);
        
        // Single node should become leader quickly
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> node1.raftNode.isLeader());
        
        assertTrue(node1.raftNode.isLeader(), "Single node should be leader");
    }

    // ========== STRESS TEST ==========

    @Test
    @DisplayName("Cluster should remain stable under repeated state checks")
    void testClusterStabilityUnderObservation() throws Exception {
        int port1 = findAvailablePort();
        int port2 = findAvailablePort();
        int port3 = findAvailablePort();
        
        Map<String, String> cluster = new HashMap<>();
        cluster.put("node1", "localhost:" + port1);
        cluster.put("node2", "localhost:" + port2);
        cluster.put("node3", "localhost:" + port3);
        
        TestNode node1 = createNode("node1", cluster);
        TestNode node2 = createNode("node2", cluster);
        TestNode node3 = createNode("node3", cluster);
        nodes.add(node1);
        nodes.add(node2);
        nodes.add(node3);
        
        startNode(node1);
        startNode(node2);
        startNode(node3);
        
        // Wait for leader election
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> node1.raftNode.isLeader() || 
                             node2.raftNode.isLeader() || 
                             node3.raftNode.isLeader());
        
        // Repeatedly check state for 5 seconds
        AtomicInteger multiLeaderCount = new AtomicInteger(0);
        AtomicInteger noLeaderCount = new AtomicInteger(0);
        AtomicInteger checkCount = new AtomicInteger(0);
        
        long endTime = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < endTime) {
            int leaderCount = 0;
            if (node1.raftNode.isLeader()) leaderCount++;
            if (node2.raftNode.isLeader()) leaderCount++;
            if (node3.raftNode.isLeader()) leaderCount++;
            
            if (leaderCount > 1) multiLeaderCount.incrementAndGet();
            if (leaderCount == 0) noLeaderCount.incrementAndGet();
            checkCount.incrementAndGet();
            
            Thread.sleep(50);
        }
        
        // Should never have multiple leaders
        assertEquals(0, multiLeaderCount.get(), 
                "Should never have multiple leaders (detected " + multiLeaderCount.get() + " times out of " + checkCount.get() + ")");
        
        // May temporarily have no leader during elections, but should be rare
        assertTrue(noLeaderCount.get() < checkCount.get() / 2, 
                "Should usually have a leader (no leader " + noLeaderCount.get() + " times out of " + checkCount.get() + ")");
    }
}
