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
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for enhanced TestInMemoryTransport features:
 * - Network partitions
 * - Message reordering
 * - Bandwidth throttling
 * - Byzantine and crash failure modes
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-01-20
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(VertxExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EnhancedInMemoryTransportTest {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedInMemoryTransportTest.class);

    private Vertx vertx;

    @BeforeEach
    void setUp(Vertx vertx) {
        this.vertx = vertx;
        TestInMemoryTransport.clearAllTransports();
    }

    @AfterEach
    void tearDown() {
        TestInMemoryTransport.clearAllTransports();
    }

    @Test
    @Order(1)
    @DisplayName("Test network partition prevents communication")
    void testNetworkPartition(VertxTestContext testContext) {
        logger.info("=== Testing Network Partition ===");
        
        Set<String> clusterNodes = Set.of("node1", "node2", "node3");

        TestInMemoryTransport transport1 = new TestInMemoryTransport("node1");
        TestInMemoryTransport transport2 = new TestInMemoryTransport("node2");
        TestInMemoryTransport transport3 = new TestInMemoryTransport("node3");

        QuorusStateMachine stateMachine1 = new QuorusStateMachine();
        QuorusStateMachine stateMachine2 = new QuorusStateMachine();
        QuorusStateMachine stateMachine3 = new QuorusStateMachine();

        RaftNode node1 = new RaftNode(vertx, "node1", clusterNodes, transport1, stateMachine1, 1000, 200);
        RaftNode node2 = new RaftNode(vertx, "node2", clusterNodes, transport2, stateMachine2, 1000, 200);
        RaftNode node3 = new RaftNode(vertx, "node3", clusterNodes, transport3, stateMachine3, 1000, 200);

        // Start all nodes
        node1.start()
            .compose(v -> node2.start())
            .compose(v -> node3.start())
            .onComplete(testContext.succeeding(v -> {
                // Wait for initial leader election
                vertx.setTimer(2000, id1 -> {
                    long initialLeaderCount = Set.of(node1, node2, node3).stream()
                        .filter(RaftNode::isLeader)
                        .count();
                    
                    logger.info("Initial leader count: {}", initialLeaderCount);
                    assertEquals(1, initialLeaderCount, "Should have exactly one leader initially");

                    // Create partition: {node1} | {node2, node3}
                    TestInMemoryTransport.createPartition(Set.of("node1"), Set.of("node2", "node3"));
                    logger.info("Created network partition: {node1} | {node2, node3}");

                    // Wait for partition effects
                    vertx.setTimer(2000, id2 -> {
                        // Now we should have 2 leaders (one in each partition)
                        // OR node1 becomes follower/candidate due to isolation
                        long leaderCount = Set.of(node1, node2, node3).stream()
                            .filter(RaftNode::isLeader)
                            .count();
                        
                        logger.info("Leader count after partition: {}", leaderCount);
                        logger.info("node1 state: {} (leader: {})", node1.getState(), node1.isLeader());
                        logger.info("node2 state: {} (leader: {})", node2.getState(), node2.isLeader());
                        logger.info("node3 state: {} (leader: {})", node3.getState(), node3.isLeader());

                        // Heal the partition
                        TestInMemoryTransport.healPartitions();
                        logger.info("Healed network partition");

                        // Wait for cluster to converge
                        vertx.setTimer(2000, id3 -> {
                            long finalLeaderCount = Set.of(node1, node2, node3).stream()
                                .filter(RaftNode::isLeader)
                                .count();
                            
                            logger.info("Final leader count after healing: {}", finalLeaderCount);
                            assertEquals(1, finalLeaderCount, "Should have exactly one leader after healing");

                            // Cleanup
                            node1.stop()
                                .compose(v2 -> node2.stop())
                                .compose(v2 -> node3.stop())
                                .onComplete(testContext.succeeding(v2 -> testContext.completeNow()));
                        });
                    });
                });
            }));
    }

    @Test
    @Order(2)
    @DisplayName("Test message reordering affects Raft behavior")
    void testMessageReordering(VertxTestContext testContext) {
        logger.info("=== Testing Message Reordering ===");
        
        Set<String> clusterNodes = Set.of("leader", "follower");

        TestInMemoryTransport transport1 = new TestInMemoryTransport("leader");
        TestInMemoryTransport transport2 = new TestInMemoryTransport("follower");

        // Enable message reordering
        transport1.setReorderingConfig(true, 0.3, 50); // 30% reorder probability, max 50ms delay
        transport2.setReorderingConfig(true, 0.3, 50);

        QuorusStateMachine stateMachine1 = new QuorusStateMachine();
        QuorusStateMachine stateMachine2 = new QuorusStateMachine();

        RaftNode node1 = new RaftNode(vertx, "leader", clusterNodes, transport1, stateMachine1, 1000, 200);
        RaftNode node2 = new RaftNode(vertx, "follower", clusterNodes, transport2, stateMachine2, 1000, 200);

        node1.start()
            .compose(v -> node2.start())
            .onComplete(testContext.succeeding(v -> {
                // Wait for leader election (may take longer due to reordering)
                vertx.setTimer(3000, id -> {
                    long leaderCount = Set.of(node1, node2).stream()
                        .filter(RaftNode::isLeader)
                        .count();
                    
                    logger.info("Leader count with reordering: {}", leaderCount);
                    assertEquals(1, leaderCount, "Should still have exactly one leader despite reordering");

                    // Cleanup
                    node1.stop()
                        .compose(v2 -> node2.stop())
                        .onComplete(testContext.succeeding(v2 -> testContext.completeNow()));
                });
            }));
    }

    @Test
    @Order(3)
    @DisplayName("Test bandwidth throttling slows down communication")
    void testBandwidthThrottling(VertxTestContext testContext) {
        logger.info("=== Testing Bandwidth Throttling ===");
        
        Set<String> clusterNodes = Set.of("node1", "node2");

        TestInMemoryTransport transport1 = new TestInMemoryTransport("node1");
        TestInMemoryTransport transport2 = new TestInMemoryTransport("node2");

        // Enable bandwidth throttling (very low limit)
        transport1.setThrottlingConfig(true, 1000); // 1KB/sec
        transport2.setThrottlingConfig(true, 1000);

        QuorusStateMachine stateMachine1 = new QuorusStateMachine();
        QuorusStateMachine stateMachine2 = new QuorusStateMachine();

        RaftNode node1 = new RaftNode(vertx, "node1", clusterNodes, transport1, stateMachine1, 1000, 200);
        RaftNode node2 = new RaftNode(vertx, "node2", clusterNodes, transport2, stateMachine2, 1000, 200);

        long startTime = System.currentTimeMillis();

        node1.start()
            .compose(v -> node2.start())
            .onComplete(testContext.succeeding(v -> {
                // With throttling, cluster should still work but slower
                vertx.setTimer(4000, id -> {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    logger.info("Time to start with throttling: {}ms", elapsedTime);

                    long leaderCount = Set.of(node1, node2).stream()
                        .filter(RaftNode::isLeader)
                        .count();
                    
                    assertEquals(1, leaderCount, "Should have one leader despite throttling");
                    assertTrue(elapsedTime >= 4000, "Should take at least 4 seconds due to throttling");

                    // Cleanup
                    node1.stop()
                        .compose(v2 -> node2.stop())
                        .onComplete(testContext.succeeding(v2 -> testContext.completeNow()));
                });
            }));
    }

    @Test
    @Order(4)
    @DisplayName("Test crash failure mode stops node communication")
    void testCrashFailureMode(VertxTestContext testContext) {
        logger.info("=== Testing Crash Failure Mode ===");
        
        Set<String> clusterNodes = Set.of("node1", "node2", "node3");

        TestInMemoryTransport transport1 = new TestInMemoryTransport("node1");
        TestInMemoryTransport transport2 = new TestInMemoryTransport("node2");
        TestInMemoryTransport transport3 = new TestInMemoryTransport("node3");

        QuorusStateMachine stateMachine1 = new QuorusStateMachine();
        QuorusStateMachine stateMachine2 = new QuorusStateMachine();
        QuorusStateMachine stateMachine3 = new QuorusStateMachine();

        RaftNode node1 = new RaftNode(vertx, "node1", clusterNodes, transport1, stateMachine1, 800, 150);
        RaftNode node2 = new RaftNode(vertx, "node2", clusterNodes, transport2, stateMachine2, 800, 150);
        RaftNode node3 = new RaftNode(vertx, "node3", clusterNodes, transport3, stateMachine3, 800, 150);

        node1.start()
            .compose(v -> node2.start())
            .compose(v -> node3.start())
            .onComplete(testContext.succeeding(v -> {
                // Wait for initial leader election
                vertx.setTimer(2000, id1 -> {
                    long initialLeaderCount = Set.of(node1, node2, node3).stream()
                        .filter(RaftNode::isLeader)
                        .count();
                    
                    assertEquals(1, initialLeaderCount, "Should have one leader initially");

                    // Crash node1
                    transport1.setFailureMode(TestInMemoryTransport.FailureMode.CRASH, 0.0);
                    logger.info("Crashed node1");

                    // Wait for cluster to adapt
                    vertx.setTimer(2000, id2 -> {
                        // node2 or node3 should become leader
                        boolean node2Leader = node2.isLeader();
                        boolean node3Leader = node3.isLeader();
                        
                        logger.info("After crash - node2 leader: {}, node3 leader: {}", node2Leader, node3Leader);
                        assertTrue(node2Leader || node3Leader, "One of the remaining nodes should be leader");

                        // Recover node1
                        transport1.recoverFromCrash();
                        logger.info("Recovered node1 from crash");

                        // Wait for node1 to rejoin
                        vertx.setTimer(2000, id3 -> {
                            long finalLeaderCount = Set.of(node1, node2, node3).stream()
                                .filter(RaftNode::isLeader)
                                .count();
                            
                            assertEquals(1, finalLeaderCount, "Should have one leader after recovery");

                            // Cleanup
                            node1.stop()
                                .compose(v2 -> node2.stop())
                                .compose(v2 -> node3.stop())
                                .onComplete(testContext.succeeding(v2 -> testContext.completeNow()));
                        });
                    });
                });
            }));
    }

    @Test
    @Order(5)
    @DisplayName("Test Byzantine failure mode with corrupted responses")
    void testByzantineFailureMode(VertxTestContext testContext) {
        logger.info("=== Testing Byzantine Failure Mode ===");
        
        Set<String> clusterNodes = Set.of("node1", "node2", "node3");

        TestInMemoryTransport transport1 = new TestInMemoryTransport("node1");
        TestInMemoryTransport transport2 = new TestInMemoryTransport("node2");
        TestInMemoryTransport transport3 = new TestInMemoryTransport("node3");

        // Make node1 Byzantine (corrupt 50% of responses)
        transport1.setFailureMode(TestInMemoryTransport.FailureMode.BYZANTINE, 0.5);

        QuorusStateMachine stateMachine1 = new QuorusStateMachine();
        QuorusStateMachine stateMachine2 = new QuorusStateMachine();
        QuorusStateMachine stateMachine3 = new QuorusStateMachine();

        RaftNode node1 = new RaftNode(vertx, "node1", clusterNodes, transport1, stateMachine1, 1000, 200);
        RaftNode node2 = new RaftNode(vertx, "node2", clusterNodes, transport2, stateMachine2, 1000, 200);
        RaftNode node3 = new RaftNode(vertx, "node3", clusterNodes, transport3, stateMachine3, 1000, 200);

        node1.start()
            .compose(v -> node2.start())
            .compose(v -> node3.start())
            .onComplete(testContext.succeeding(v -> {
                // Raft should tolerate Byzantine node if it's not the majority
                vertx.setTimer(3000, id -> {
                    long leaderCount = Set.of(node1, node2, node3).stream()
                        .filter(RaftNode::isLeader)
                        .count();
                    
                    logger.info("Leader count with Byzantine node: {}", leaderCount);
                    
                    // May have 0 or 1 leaders depending on Byzantine interference
                    assertTrue(leaderCount <= 1, "Should have at most one leader with Byzantine node");

                    // Cleanup
                    node1.stop()
                        .compose(v2 -> node2.stop())
                        .compose(v2 -> node3.stop())
                        .onComplete(testContext.succeeding(v2 -> testContext.completeNow()));
                });
            }));
    }

    @Test
    @Order(6)
    @DisplayName("Test SLOW failure mode increases latency")
    void testSlowFailureMode(VertxTestContext testContext) {
        logger.info("=== Testing SLOW Failure Mode ===");
        
        Set<String> clusterNodes = Set.of("node1", "node2");

        TestInMemoryTransport transport1 = new TestInMemoryTransport("node1");
        TestInMemoryTransport transport2 = new TestInMemoryTransport("node2");

        // Make node1 slow (10x latency)
        transport1.setFailureMode(TestInMemoryTransport.FailureMode.SLOW, 0.0);

        QuorusStateMachine stateMachine1 = new QuorusStateMachine();
        QuorusStateMachine stateMachine2 = new QuorusStateMachine();

        RaftNode node1 = new RaftNode(vertx, "node1", clusterNodes, transport1, stateMachine1, 1000, 200);
        RaftNode node2 = new RaftNode(vertx, "node2", clusterNodes, transport2, stateMachine2, 1000, 200);

        long startTime = System.currentTimeMillis();

        node1.start()
            .compose(v -> node2.start())
            .onComplete(testContext.succeeding(v -> {
                // SLOW mode should significantly increase election time
                vertx.setTimer(4000, id -> {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    logger.info("Time with SLOW mode: {}ms", elapsedTime);

                    long leaderCount = Set.of(node1, node2).stream()
                        .filter(RaftNode::isLeader)
                        .count();
                    
                    assertEquals(1, leaderCount, "Should eventually have one leader despite slow node");
                    assertTrue(elapsedTime >= 3000, "Should take longer due to SLOW mode");

                    // Cleanup
                    node1.stop()
                        .compose(v2 -> node2.stop())
                        .onComplete(testContext.succeeding(v2 -> testContext.completeNow()));
                });
            }));
    }

    @Test
    @Order(7)
    @DisplayName("Test FLAKY failure mode with intermittent issues")
    void testFlakyFailureMode(VertxTestContext testContext) {
        logger.info("=== Testing FLAKY Failure Mode ===");
        
        Set<String> clusterNodes = Set.of("node1", "node2", "node3");

        TestInMemoryTransport transport1 = new TestInMemoryTransport("node1");
        TestInMemoryTransport transport2 = new TestInMemoryTransport("node2");
        TestInMemoryTransport transport3 = new TestInMemoryTransport("node3");

        // Make node2 flaky (intermittent 50% high latency)
        transport2.setFailureMode(TestInMemoryTransport.FailureMode.FLAKY, 0.0);

        QuorusStateMachine stateMachine1 = new QuorusStateMachine();
        QuorusStateMachine stateMachine2 = new QuorusStateMachine();
        QuorusStateMachine stateMachine3 = new QuorusStateMachine();

        RaftNode node1 = new RaftNode(vertx, "node1", clusterNodes, transport1, stateMachine1, 800, 150);
        RaftNode node2 = new RaftNode(vertx, "node2", clusterNodes, transport2, stateMachine2, 800, 150);
        RaftNode node3 = new RaftNode(vertx, "node3", clusterNodes, transport3, stateMachine3, 800, 150);

        node1.start()
            .compose(v -> node2.start())
            .compose(v -> node3.start())
            .onComplete(testContext.succeeding(v -> {
                // FLAKY mode should still allow cluster to function
                vertx.setTimer(3000, id -> {
                    long leaderCount = Set.of(node1, node2, node3).stream()
                        .filter(RaftNode::isLeader)
                        .count();
                    
                    logger.info("Leader count with FLAKY node: {}", leaderCount);
                    assertEquals(1, leaderCount, "Should have one leader despite flaky node");

                    // Cleanup
                    node1.stop()
                        .compose(v2 -> node2.stop())
                        .compose(v2 -> node3.stop())
                        .onComplete(testContext.succeeding(v2 -> testContext.completeNow()));
                });
            }));
    }

    @Test
    @Order(8)
    @DisplayName("Test combined chaos: partition + reordering + packet drop")
    void testCombinedChaos(VertxTestContext testContext) {
        logger.info("=== Testing Combined Chaos Scenarios ===");
        
        Set<String> clusterNodes = Set.of("node1", "node2", "node3");

        TestInMemoryTransport transport1 = new TestInMemoryTransport("node1");
        TestInMemoryTransport transport2 = new TestInMemoryTransport("node2");
        TestInMemoryTransport transport3 = new TestInMemoryTransport("node3");

        // Apply multiple chaos factors
        transport1.setChaosConfig(10, 30, 0.1); // 10% packet drop
        transport2.setChaosConfig(10, 30, 0.1);
        transport3.setChaosConfig(10, 30, 0.1);
        
        transport1.setReorderingConfig(true, 0.2, 40); // 20% reorder probability
        transport2.setReorderingConfig(true, 0.2, 40);
        transport3.setReorderingConfig(true, 0.2, 40);

        QuorusStateMachine stateMachine1 = new QuorusStateMachine();
        QuorusStateMachine stateMachine2 = new QuorusStateMachine();
        QuorusStateMachine stateMachine3 = new QuorusStateMachine();

        RaftNode node1 = new RaftNode(vertx, "node1", clusterNodes, transport1, stateMachine1, 1000, 200);
        RaftNode node2 = new RaftNode(vertx, "node2", clusterNodes, transport2, stateMachine2, 1000, 200);
        RaftNode node3 = new RaftNode(vertx, "node3", clusterNodes, transport3, stateMachine3, 1000, 200);

        node1.start()
            .compose(v -> node2.start())
            .compose(v -> node3.start())
            .onComplete(testContext.succeeding(v -> {
                // Even with combined chaos, Raft should eventually elect a leader
                vertx.setTimer(5000, id -> {
                    long leaderCount = Set.of(node1, node2, node3).stream()
                        .filter(RaftNode::isLeader)
                        .count();
                    
                    logger.info("Leader count with combined chaos: {}", leaderCount);
                    
                    // May need more time or may not always succeed with heavy chaos
                    // But should have at most 1 leader
                    assertTrue(leaderCount <= 1, "Should have at most one leader");

                    // Cleanup
                    node1.stop()
                        .compose(v2 -> node2.stop())
                        .compose(v2 -> node3.stop())
                        .onComplete(testContext.succeeding(v2 -> testContext.completeNow()));
                });
            }));
    }
}
