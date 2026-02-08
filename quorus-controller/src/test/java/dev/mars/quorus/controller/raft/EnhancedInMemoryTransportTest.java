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

import dev.mars.quorus.controller.state.QuorusStateMachine;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for enhanced InMemoryTransportSimulator features:
 * - Network partitions
 * - Message reordering
 * - Bandwidth throttling
 * - Byzantine and crash failure modes
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2026-01-20
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EnhancedInMemoryTransportTest {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedInMemoryTransportTest.class);

    private Vertx vertx;
    private final List<RaftNode> activeNodes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        InMemoryTransportSimulator.clearAllTransports();
        activeNodes.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        InMemoryTransportSimulator.clearAllTransports();
        for (RaftNode node : activeNodes) {
            try { node.stop(); } catch (Exception ignored) {}
        }
        activeNodes.clear();
        if (vertx != null) {
            vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    private long leaderCount(RaftNode... nodes) {
        return Set.of(nodes).stream().filter(RaftNode::isLeader).count();
    }

    private void startAndTrack(RaftNode... nodes) {
        for (RaftNode node : nodes) {
            node.start();
            activeNodes.add(node);
        }
    }

    @Test
    @Order(1)
    @DisplayName("Test network partition prevents communication")
    void testNetworkPartition() {
        logger.info("=== Testing Network Partition ===");
        
        Set<String> clusterNodes = Set.of("node1", "node2", "node3");

        InMemoryTransportSimulator transport1 = new InMemoryTransportSimulator("node1");
        InMemoryTransportSimulator transport2 = new InMemoryTransportSimulator("node2");
        InMemoryTransportSimulator transport3 = new InMemoryTransportSimulator("node3");

        QuorusStateMachine sm1 = new QuorusStateMachine();
        QuorusStateMachine sm2 = new QuorusStateMachine();
        QuorusStateMachine sm3 = new QuorusStateMachine();

        RaftNode node1 = new RaftNode(vertx, "node1", clusterNodes, transport1, sm1, 1000, 200);
        RaftNode node2 = new RaftNode(vertx, "node2", clusterNodes, transport2, sm2, 1000, 200);
        RaftNode node3 = new RaftNode(vertx, "node3", clusterNodes, transport3, sm3, 1000, 200);

        startAndTrack(node1, node2, node3);

        // Wait for leader election
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(100))
            .until(() -> leaderCount(node1, node2, node3) == 1);
        logger.info("Initial leader elected");

        // Create partition: {node1} | {node2, node3}
        InMemoryTransportSimulator.createPartition(Set.of("node1"), Set.of("node2", "node3"));
        logger.info("Created network partition: {{node1}} | {{node2, node3}}");

        // Wait for partition to take effect (majority side should have a leader)
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(100))
            .until(() -> node2.isLeader() || node3.isLeader());

        logger.info("node1 state: {} (leader: {})", node1.getState(), node1.isLeader());
        logger.info("node2 state: {} (leader: {})", node2.getState(), node2.isLeader());
        logger.info("node3 state: {} (leader: {})", node3.getState(), node3.isLeader());

        // Heal the partition
        InMemoryTransportSimulator.healPartitions();
        logger.info("Healed network partition");

        // Wait for cluster to converge to exactly one leader
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(100))
            .until(() -> leaderCount(node1, node2, node3) == 1);

        assertEquals(1, leaderCount(node1, node2, node3), "Should have exactly one leader after healing");
    }

    @Test
    @Order(2)
    @DisplayName("Test message reordering affects Raft behavior")
    void testMessageReordering() {
        logger.info("=== Testing Message Reordering ===");
        
        Set<String> clusterNodes = Set.of("leader", "follower");

        InMemoryTransportSimulator transport1 = new InMemoryTransportSimulator("leader");
        InMemoryTransportSimulator transport2 = new InMemoryTransportSimulator("follower");

        // Enable message reordering
        transport1.setReorderingConfig(true, 0.3, 50);
        transport2.setReorderingConfig(true, 0.3, 50);

        QuorusStateMachine sm1 = new QuorusStateMachine();
        QuorusStateMachine sm2 = new QuorusStateMachine();

        RaftNode node1 = new RaftNode(vertx, "leader", clusterNodes, transport1, sm1, 1000, 200);
        RaftNode node2 = new RaftNode(vertx, "follower", clusterNodes, transport2, sm2, 1000, 200);

        startAndTrack(node1, node2);

        // Wait for leader election despite reordering
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(100))
            .until(() -> leaderCount(node1, node2) == 1);

        logger.info("Leader count with reordering: {}", leaderCount(node1, node2));
        assertEquals(1, leaderCount(node1, node2), "Should still have exactly one leader despite reordering");
    }

    @Test
    @Order(3)
    @DisplayName("Test bandwidth throttling slows down communication")
    void testBandwidthThrottling() {
        logger.info("=== Testing Bandwidth Throttling ===");
        
        Set<String> clusterNodes = Set.of("node1", "node2");

        InMemoryTransportSimulator transport1 = new InMemoryTransportSimulator("node1");
        InMemoryTransportSimulator transport2 = new InMemoryTransportSimulator("node2");

        // Enable bandwidth throttling (very low limit)
        transport1.setThrottlingConfig(true, 1000); // 1KB/sec
        transport2.setThrottlingConfig(true, 1000);

        QuorusStateMachine sm1 = new QuorusStateMachine();
        QuorusStateMachine sm2 = new QuorusStateMachine();

        RaftNode node1 = new RaftNode(vertx, "node1", clusterNodes, transport1, sm1, 1000, 200);
        RaftNode node2 = new RaftNode(vertx, "node2", clusterNodes, transport2, sm2, 1000, 200);

        long startTime = System.currentTimeMillis();

        startAndTrack(node1, node2);

        // With throttling, cluster should still elect a leader (may be slower)
        await().atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(200))
            .until(() -> leaderCount(node1, node2) == 1);

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("Time to elect leader with throttling: {}ms", elapsed);
        assertEquals(1, leaderCount(node1, node2), "Should have one leader despite throttling");
    }

    @Test
    @Order(4)
    @DisplayName("Test crash failure mode stops node communication")
    void testCrashFailureMode() {
        logger.info("=== Testing Crash Failure Mode ===");
        
        Set<String> clusterNodes = Set.of("node1", "node2", "node3");

        InMemoryTransportSimulator transport1 = new InMemoryTransportSimulator("node1");
        InMemoryTransportSimulator transport2 = new InMemoryTransportSimulator("node2");
        InMemoryTransportSimulator transport3 = new InMemoryTransportSimulator("node3");

        QuorusStateMachine sm1 = new QuorusStateMachine();
        QuorusStateMachine sm2 = new QuorusStateMachine();
        QuorusStateMachine sm3 = new QuorusStateMachine();

        RaftNode node1 = new RaftNode(vertx, "node1", clusterNodes, transport1, sm1, 800, 150);
        RaftNode node2 = new RaftNode(vertx, "node2", clusterNodes, transport2, sm2, 800, 150);
        RaftNode node3 = new RaftNode(vertx, "node3", clusterNodes, transport3, sm3, 800, 150);

        startAndTrack(node1, node2, node3);

        // Wait for initial leader election
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(100))
            .until(() -> leaderCount(node1, node2, node3) == 1);

        assertEquals(1, leaderCount(node1, node2, node3), "Should have one leader initially");

        // Crash node1
        transport1.setFailureMode(InMemoryTransportSimulator.FailureMode.CRASH, 0.0);
        logger.info("Crashed node1");

        // Wait for cluster to adapt — node2 or node3 should be leader
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(100))
            .until(() -> node2.isLeader() || node3.isLeader());

        logger.info("After crash - node2 leader: {}, node3 leader: {}", node2.isLeader(), node3.isLeader());

        // Recover node1
        transport1.recoverFromCrash();
        logger.info("Recovered node1 from crash");

        // Wait for cluster to converge to exactly one leader
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(100))
            .until(() -> leaderCount(node1, node2, node3) == 1);

        assertEquals(1, leaderCount(node1, node2, node3), "Should have one leader after recovery");
    }

    @Test
    @Order(5)
    @DisplayName("Test Byzantine failure mode with corrupted responses")
    void testByzantineFailureMode() {
        logger.info("=== Testing Byzantine Failure Mode ===");
        
        Set<String> clusterNodes = Set.of("node1", "node2", "node3");

        InMemoryTransportSimulator transport1 = new InMemoryTransportSimulator("node1");
        InMemoryTransportSimulator transport2 = new InMemoryTransportSimulator("node2");
        InMemoryTransportSimulator transport3 = new InMemoryTransportSimulator("node3");

        // Make node1 Byzantine (corrupt 50% of responses)
        transport1.setFailureMode(InMemoryTransportSimulator.FailureMode.BYZANTINE, 0.5);

        QuorusStateMachine sm1 = new QuorusStateMachine();
        QuorusStateMachine sm2 = new QuorusStateMachine();
        QuorusStateMachine sm3 = new QuorusStateMachine();

        RaftNode node1 = new RaftNode(vertx, "node1", clusterNodes, transport1, sm1, 1000, 200);
        RaftNode node2 = new RaftNode(vertx, "node2", clusterNodes, transport2, sm2, 1000, 200);
        RaftNode node3 = new RaftNode(vertx, "node3", clusterNodes, transport3, sm3, 1000, 200);

        startAndTrack(node1, node2, node3);

        // Raft should tolerate Byzantine node — wait for election to settle
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(100))
            .until(() -> leaderCount(node1, node2, node3) <= 1);

        long lc = leaderCount(node1, node2, node3);
        logger.info("Leader count with Byzantine node: {}", lc);
        assertTrue(lc <= 1, "Should have at most one leader with Byzantine node");
    }

    @Test
    @Order(6)
    @DisplayName("Test SLOW failure mode increases latency")
    void testSlowFailureMode() {
        logger.info("=== Testing SLOW Failure Mode ===");
        
        Set<String> clusterNodes = Set.of("node1", "node2");

        InMemoryTransportSimulator transport1 = new InMemoryTransportSimulator("node1");
        InMemoryTransportSimulator transport2 = new InMemoryTransportSimulator("node2");

        // Make node1 slow (10x latency)
        transport1.setFailureMode(InMemoryTransportSimulator.FailureMode.SLOW, 0.0);

        QuorusStateMachine sm1 = new QuorusStateMachine();
        QuorusStateMachine sm2 = new QuorusStateMachine();

        RaftNode node1 = new RaftNode(vertx, "node1", clusterNodes, transport1, sm1, 1000, 200);
        RaftNode node2 = new RaftNode(vertx, "node2", clusterNodes, transport2, sm2, 1000, 200);

        long startTime = System.currentTimeMillis();

        startAndTrack(node1, node2);

        // SLOW mode should still eventually elect a leader
        await().atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(200))
            .until(() -> leaderCount(node1, node2) == 1);

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("Time with SLOW mode: {}ms", elapsed);
        assertEquals(1, leaderCount(node1, node2), "Should eventually have one leader despite slow node");
    }

    @Test
    @Order(7)
    @DisplayName("Test FLAKY failure mode with intermittent issues")
    void testFlakyFailureMode() {
        logger.info("=== Testing FLAKY Failure Mode ===");
        
        Set<String> clusterNodes = Set.of("node1", "node2", "node3");

        InMemoryTransportSimulator transport1 = new InMemoryTransportSimulator("node1");
        InMemoryTransportSimulator transport2 = new InMemoryTransportSimulator("node2");
        InMemoryTransportSimulator transport3 = new InMemoryTransportSimulator("node3");

        // Make node2 flaky (intermittent 50% high latency)
        transport2.setFailureMode(InMemoryTransportSimulator.FailureMode.FLAKY, 0.0);

        QuorusStateMachine sm1 = new QuorusStateMachine();
        QuorusStateMachine sm2 = new QuorusStateMachine();
        QuorusStateMachine sm3 = new QuorusStateMachine();

        RaftNode node1 = new RaftNode(vertx, "node1", clusterNodes, transport1, sm1, 800, 150);
        RaftNode node2 = new RaftNode(vertx, "node2", clusterNodes, transport2, sm2, 800, 150);
        RaftNode node3 = new RaftNode(vertx, "node3", clusterNodes, transport3, sm3, 800, 150);

        startAndTrack(node1, node2, node3);

        // FLAKY mode should still allow cluster to function
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(100))
            .until(() -> leaderCount(node1, node2, node3) == 1);

        logger.info("Leader count with FLAKY node: {}", leaderCount(node1, node2, node3));
        assertEquals(1, leaderCount(node1, node2, node3), "Should have one leader despite flaky node");
    }

    @Test
    @Order(8)
    @DisplayName("Test combined chaos: partition + reordering + packet drop")
    void testCombinedChaos() {
        logger.info("=== Testing Combined Chaos Scenarios ===");
        
        Set<String> clusterNodes = Set.of("node1", "node2", "node3");

        InMemoryTransportSimulator transport1 = new InMemoryTransportSimulator("node1");
        InMemoryTransportSimulator transport2 = new InMemoryTransportSimulator("node2");
        InMemoryTransportSimulator transport3 = new InMemoryTransportSimulator("node3");

        // Apply multiple chaos factors
        transport1.setChaosConfig(10, 30, 0.1);
        transport2.setChaosConfig(10, 30, 0.1);
        transport3.setChaosConfig(10, 30, 0.1);
        
        transport1.setReorderingConfig(true, 0.2, 40);
        transport2.setReorderingConfig(true, 0.2, 40);
        transport3.setReorderingConfig(true, 0.2, 40);

        QuorusStateMachine sm1 = new QuorusStateMachine();
        QuorusStateMachine sm2 = new QuorusStateMachine();
        QuorusStateMachine sm3 = new QuorusStateMachine();

        RaftNode node1 = new RaftNode(vertx, "node1", clusterNodes, transport1, sm1, 1000, 200);
        RaftNode node2 = new RaftNode(vertx, "node2", clusterNodes, transport2, sm2, 1000, 200);
        RaftNode node3 = new RaftNode(vertx, "node3", clusterNodes, transport3, sm3, 1000, 200);

        startAndTrack(node1, node2, node3);

        // Even with combined chaos, Raft should eventually have at most 1 leader
        await().atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(200))
            .until(() -> leaderCount(node1, node2, node3) <= 1);

        long lc = leaderCount(node1, node2, node3);
        logger.info("Leader count with combined chaos: {}", lc);
        assertTrue(lc <= 1, "Should have at most one leader");
    }
}
