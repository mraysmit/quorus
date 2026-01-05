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
import dev.mars.quorus.controller.state.TransferJobCommand;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferStatus;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chaos testing for the Raft cluster using InMemoryTransport's fault injection capabilities.
 * Tests resilience against packet loss and high latency.
 */
public class RaftChaosTest {

    private static final Logger logger = LoggerFactory.getLogger(RaftChaosTest.class);
    
    private List<RaftNode> cluster;
    private Map<String, InMemoryTransport> transports;
    private io.vertx.core.Vertx vertx;
    private static final int CLUSTER_SIZE = 5;
    private static final String[] NODE_IDS = {"node1", "node2", "node3", "node4", "node5"};

    @BeforeEach
    void setUp() {
        logger.info("=== SETTING UP RAFT CHAOS TEST ===");
        
        // Clear any static state in InMemoryTransport
        InMemoryTransport.clearAllTransports();
        
        vertx = io.vertx.core.Vertx.vertx();
        cluster = new ArrayList<>();
        transports = new HashMap<>();
        
        // Create transport layer for inter-node communication
        for (String nodeId : NODE_IDS) {
            transports.put(nodeId, new InMemoryTransport(nodeId));
        }
        
        // Create cluster nodes with QuorusStateMachine
        Set<String> clusterNodes = Set.of(NODE_IDS);
        for (String nodeId : NODE_IDS) {
            QuorusStateMachine stateMachine = new QuorusStateMachine();
            // Use standard timeouts: 1000ms election, 200ms heartbeat
            RaftNode node = new RaftNode(vertx, nodeId, clusterNodes, transports.get(nodeId), stateMachine, 1000, 200);
            cluster.add(node);
        }
        
        // Start all nodes
        List<Future<Void>> futures = new ArrayList<>();
        for (RaftNode node : cluster) {
            futures.add(node.start());
        }
        Future.all(futures)
            .toCompletionStage().toCompletableFuture().join();
        
        logger.info("Cluster started with {} nodes", CLUSTER_SIZE);
    }

    @AfterEach
    void tearDown() {
        logger.info("=== TEARING DOWN RAFT CHAOS TEST ===");
        
        if (cluster != null) {
            List<io.vertx.core.Future<Void>> futures = new ArrayList<>();
            for (RaftNode node : cluster) {
                try {
                    futures.add(node.stop());
                } catch (Exception e) {
                    logger.warn("Error stopping node {}: {}", node.getNodeId(), e.getMessage());
                }
            }
            try {
                Future.all(futures)
                    .toCompletionStage().toCompletableFuture().join();
            } catch (Exception e) {
                logger.warn("Error waiting for nodes to stop", e);
            }
        }
        
        if (vertx != null) {
            vertx.close();
        }
        
        InMemoryTransport.clearAllTransports();
    }

    @Test
    @DisplayName("Cluster should reach consensus despite 25% packet loss")
    void testReplicationWithPacketLoss() throws InterruptedException {
        logger.info("=== TEST: Replication with 25% Packet Loss ===");
        
        // Configure 25% packet loss on ALL nodes
        for (InMemoryTransport transport : transports.values()) {
            transport.setChaosConfig(5, 15, 0.25);
        }
        
        // Wait for leader election (might take longer due to packet loss)
        RaftNode leader = waitForLeader(10000);
        assertNotNull(leader, "Leader should be elected despite packet loss");
        logger.info("Leader elected: {}", leader.getNodeId());
        
        // Submit a job
        String jobId = UUID.randomUUID().toString();
        TransferRequest request = TransferRequest.builder()
            .requestId(jobId)
            .sourceUri(Paths.get("/tmp/source.txt").toUri())
            .destinationPath(Paths.get("/tmp/dest.txt"))
            .build();
            
        TransferJob job = new TransferJob(request);
        TransferJobCommand command = TransferJobCommand.create(job);
        
        logger.info("Submitting job {} to leader", jobId);
        Future<Object> future = leader.submitCommand(command);
        
        // Wait for commit (might take longer)
        try {
            future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            logger.info("Job committed successfully");
        } catch (Exception e) {
            fail("Failed to commit job under packet loss conditions: " + e.getMessage());
        }
        
        // Verify replication to majority (allow time for commit index propagation)
        int nodesWithData = 0;
        long deadline = System.currentTimeMillis() + 10000; // Wait up to 10s for propagation in lossy network
        
        while (System.currentTimeMillis() < deadline) {
            nodesWithData = 0;
            for (RaftNode node : cluster) {
                QuorusStateMachine sm = (QuorusStateMachine) node.getStateMachine();
                if (sm.getTransferJob(jobId) != null) {
                    nodesWithData++;
                }
            }
            
            if (nodesWithData >= 3) {
                break;
            }
            Thread.sleep(200);
        }
        
        logger.info("{} nodes have the data", nodesWithData);
        assertTrue(nodesWithData >= 3, "Data should be replicated to at least a majority (3) of nodes");
    }

    @Test
    @DisplayName("Cluster should remain stable with high latency (100-200ms)")
    void testReplicationWithHighLatency() throws InterruptedException {
        logger.info("=== TEST: Replication with High Latency ===");
        
        // Configure high latency (100-200ms) on ALL nodes
        // Heartbeat is 200ms, so this is borderline and might cause occasional re-elections
        for (InMemoryTransport transport : transports.values()) {
            transport.setChaosConfig(100, 200, 0.0);
        }
        
        // Wait for leader election
        RaftNode leader = waitForLeader(10000);
        assertNotNull(leader, "Leader should be elected despite high latency");
        logger.info("Leader elected: {}", leader.getNodeId());
        
        // Submit multiple jobs to stress the latency
        int jobCount = 5;
        List<Future<Object>> futures = new ArrayList<>();
        
        for (int i = 0; i < jobCount; i++) {
            String jobId = UUID.randomUUID().toString();
            TransferRequest request = TransferRequest.builder()
                .requestId(jobId)
                .sourceUri(Paths.get("/tmp/source" + i + ".txt").toUri())
                .destinationPath(Paths.get("/tmp/dest" + i + ".txt"))
                .build();
                
            TransferJob job = new TransferJob(request);
            TransferJobCommand command = TransferJobCommand.create(job);
            
            futures.add(leader.submitCommand(command));
        }
        
        logger.info("Submitted {} jobs", jobCount);
        
        // Wait for all to commit
        try {
            for (Future<Object> future : futures) {
                future.toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);
            }
            logger.info("All jobs committed successfully");
        } catch (Exception e) {
            fail("Failed to commit jobs under high latency conditions: " + e.getMessage());
        }
    }

    private RaftNode waitForLeader(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (RaftNode node : cluster) {
                if (node.getState() == RaftNode.State.LEADER) {
                    return node;
                }
            }
            Thread.sleep(100);
        }
        return null;
    }
}
