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

import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.TransferJobCommand;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test to prove that metadata is properly transferred and preserved
 * during Raft leader changes. This test demonstrates the core data consistency
 * guarantees of the Quorus distributed controller architecture.
 * 
 * <p>NOTE: These tests are marked as flaky because they are timing-sensitive and
 * may fail when run as part of the full test suite due to resource contention.
 * They pass reliably when run in isolation.</p>
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-27
 */
@Tag("flaky")
public class MetadataPersistenceTest {

    private static final Logger logger = Logger.getLogger(MetadataPersistenceTest.class.getName());
    
    private List<RaftNode> cluster;
    private Map<String, MockRaftTransport> transports;
    private io.vertx.core.Vertx vertx;
    private static final int CLUSTER_SIZE = 5;
    private static final String[] NODE_IDS = {"node1", "node2", "node3", "node4", "node5"};

    @BeforeEach
    void setUp() {
        logger.info("=== SETTING UP METADATA PERSISTENCE TEST ===");
        
        vertx = io.vertx.core.Vertx.vertx();
        cluster = new ArrayList<>();
        transports = new HashMap<>();
        
        // Create transport layer for inter-node communication
        for (String nodeId : NODE_IDS) {
            transports.put(nodeId, new MockRaftTransport(nodeId));
        }
        
        // Connect all transports to each other
        for (MockRaftTransport transport : transports.values()) {
            transport.setTransports(transports);
        }
        
        // Create cluster nodes with QuorusStateStore
        Set<String> clusterNodes = Set.of(NODE_IDS);
        for (String nodeId : NODE_IDS) {
            QuorusStateStore stateMachine = new QuorusStateStore();
            // Use shorter timeouts for testing: 1000ms election, 200ms heartbeat
            RaftNode node = new RaftNode(vertx, nodeId, clusterNodes, transports.get(nodeId), stateMachine, 1000, 200);
            cluster.add(node);
        }
        
        // Start all nodes
        for (RaftNode node : cluster) {
            node.start();
        }
        
        logger.info("Cluster started with " + CLUSTER_SIZE + " nodes");
    }

    @AfterEach
    void tearDown() {
        logger.info("=== TEARING DOWN METADATA PERSISTENCE TEST ===");
        
        if (cluster != null) {
            for (RaftNode node : cluster) {
                try {
                    node.stop();
                } catch (Exception e) {
                    logger.warning("Error stopping node " + node.getNodeId() + ": " + e.getMessage());
                }
            }
        }
        if (vertx != null) {
            vertx.close();
        }
    }

    @Test
    @DisplayName("Prove metadata replication across all nodes")
    void testBasicMetadataReplication() throws Exception {
        logger.info("=== TEST: Basic Metadata Replication ===");
        
        // Wait for leader election
        RaftNode leader = waitForLeaderElection();
        assertNotNull(leader, "Leader should be elected");
        logger.info("Leader elected: " + leader.getNodeId());
        
        // Create test transfer job metadata
        TransferJob testJob = createTestTransferJob("job-001", "Basic replication test");
        TransferJobCommand command = TransferJobCommand.create(testJob);
        
        // Submit metadata to leader
        logger.info("Submitting transfer job metadata to leader: " + testJob.getJobId());
        io.vertx.core.Future<Object> result = leader.submitCommand(command);
        
        // Wait for command to be processed
        Object response = result.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(response, "Command should be processed successfully");
        logger.info("Command processed successfully: " + response);
        
        // Wait for replication to complete (reactive: poll until all nodes have the job)
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(200))
            .until(() -> allNodesHaveJob(testJob.getJobId()));
        
        // Verify metadata exists on all nodes
        logger.info("Verifying metadata replication across all nodes...");
        for (RaftNode node : cluster) {
            QuorusStateStore stateMachine = (QuorusStateStore) node.getStateStore();
            assertTrue(stateMachine.hasTransferJob(testJob.getJobId()), 
                "Node " + node.getNodeId() + " should have transfer job " + testJob.getJobId());
            logger.info("✓ Node " + node.getNodeId() + " has transfer job " + testJob.getJobId());
        }
        
        logger.info("✅ Basic metadata replication test PASSED");
    }

    @Test
    @DisplayName("Prove metadata preservation during leader change")
    void testMetadataPreservationDuringLeaderChange() throws Exception {
        logger.info("=== TEST: Metadata Preservation During Leader Change ===");
        
        // Wait for initial leader election
        RaftNode originalLeader = waitForLeaderElection();
        assertNotNull(originalLeader, "Original leader should be elected");
        logger.info("Original leader: " + originalLeader.getNodeId());
        
        // Submit multiple transfer jobs to original leader
        List<TransferJob> testJobs = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            TransferJob job = createTestTransferJob("job-00" + i, "Leader change test job " + i);
            testJobs.add(job);
            
            TransferJobCommand command = TransferJobCommand.create(job);
            io.vertx.core.Future<Object> result = originalLeader.submitCommand(command);
            result.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            logger.info("Submitted job " + job.getJobId() + " to original leader");
        }
        
        // Wait for replication (reactive: poll until all nodes have all jobs)
        String lastJobId = testJobs.get(testJobs.size() - 1).getJobId();
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(200))
            .until(() -> allNodesHaveJob(lastJobId));
        
        // Verify all jobs are replicated before leader change
        logger.info("Verifying initial replication...");
        for (RaftNode node : cluster) {
            QuorusStateStore stateMachine = (QuorusStateStore) node.getStateStore();
            for (TransferJob job : testJobs) {
                assertTrue(stateMachine.hasTransferJob(job.getJobId()),
                    "Node " + node.getNodeId() + " should have job " + job.getJobId() + " before leader change");
            }
        }
        
        // Force leader change by stopping original leader
        logger.info("Forcing leader change by stopping " + originalLeader.getNodeId());
        originalLeader.stop();
        
        // Wait for new leader election (reactive: poll until a new leader emerges)
        await().atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(200))
            .until(() -> {
                RaftNode leader = findCurrentLeader();
                return leader != null && !leader.getNodeId().equals(originalLeader.getNodeId());
            });
        RaftNode newLeader = findCurrentLeader();
        assertNotNull(newLeader, "New leader should be elected");
        assertNotEquals(originalLeader.getNodeId(), newLeader.getNodeId(), 
            "New leader should be different from original leader");
        logger.info("New leader elected: " + newLeader.getNodeId());
        
        // Verify all metadata still exists on remaining nodes
        logger.info("Verifying metadata preservation after leader change...");
        List<RaftNode> remainingNodes = cluster.stream()
            .filter(node -> !node.getNodeId().equals(originalLeader.getNodeId()))
            .toList();
            
        for (RaftNode node : remainingNodes) {
            QuorusStateStore stateMachine = (QuorusStateStore) node.getStateStore();
            for (TransferJob job : testJobs) {
                assertTrue(stateMachine.hasTransferJob(job.getJobId()),
                    "Node " + node.getNodeId() + " should still have job " + job.getJobId() + " after leader change");
                logger.info("✓ Node " + node.getNodeId() + " preserved job " + job.getJobId());
            }
        }
        
        // Submit new job to new leader to prove it's functional
        TransferJob newJob = createTestTransferJob("job-new", "Post leader change test");
        TransferJobCommand newCommand = TransferJobCommand.create(newJob);
        io.vertx.core.Future<Object> newResult = newLeader.submitCommand(newCommand);
        newResult.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        logger.info("Successfully submitted new job to new leader: " + newJob.getJobId());
        
        // Wait for replication (reactive: poll until remaining nodes have the new job)
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(200))
            .until(() -> remainingNodes.stream().allMatch(node -> 
                ((QuorusStateStore) node.getStateStore()).hasTransferJob(newJob.getJobId())));
        
        // Verify new job is replicated to all remaining nodes
        for (RaftNode node : remainingNodes) {
            QuorusStateStore stateMachine = (QuorusStateStore) node.getStateStore();
            assertTrue(stateMachine.hasTransferJob(newJob.getJobId()),
                "Node " + node.getNodeId() + " should have new job " + newJob.getJobId());
            logger.info("✓ Node " + node.getNodeId() + " received new job " + newJob.getJobId());
        }
        
        logger.info("✅ Metadata preservation during leader change test PASSED");
    }

    @Test
    @DisplayName("Prove failed node recovery with complete metadata")
    void testFailedNodeRecovery() throws Exception {
        logger.info("=== TEST: Failed Node Recovery ===");
        
        // Wait for leader election
        RaftNode leader = waitForLeaderElection();
        assertNotNull(leader, "Leader should be elected");
        logger.info("Leader elected: " + leader.getNodeId());
        
        // Choose a follower to fail
        RaftNode followerToFail = cluster.stream()
            .filter(node -> !node.getNodeId().equals(leader.getNodeId()))
            .findFirst()
            .orElseThrow();
        logger.info("Will fail follower: " + followerToFail.getNodeId());
        
        // Stop the follower
        followerToFail.stop();
        logger.info("Stopped follower: " + followerToFail.getNodeId());
        
        // Submit metadata while follower is down
        List<TransferJob> jobsWhileDown = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            TransferJob job = createTestTransferJob("recovery-job-00" + i, "Recovery test job " + i);
            jobsWhileDown.add(job);
            
            TransferJobCommand command = TransferJobCommand.create(job);
            io.vertx.core.Future<Object> result = leader.submitCommand(command);
            result.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            logger.info("Submitted job " + job.getJobId() + " while follower was down");
        }
        
        // Wait for replication to remaining nodes (reactive: poll until committed)
        String lastJobId = jobsWhileDown.get(jobsWhileDown.size() - 1).getJobId();
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(200))
            .until(() -> {
                // Check at least majority of running nodes have the job
                return cluster.stream()
                    .filter(n -> !n.getNodeId().equals(followerToFail.getNodeId()))
                    .filter(n -> ((QuorusStateStore) n.getStateStore()).hasTransferJob(lastJobId))
                    .count() >= 3;
            });
        
        // Restart the failed follower
        logger.info("Restarting failed follower: " + followerToFail.getNodeId());
        followerToFail.start();
        
        // Wait for recovery and catch-up (reactive: poll until recovered node has the data)
        await().atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(200))
            .until(() -> {
                QuorusStateStore sm = (QuorusStateStore) followerToFail.getStateStore();
                return jobsWhileDown.stream().allMatch(job -> sm.hasTransferJob(job.getJobId()));
            });
        
        // Verify recovered node has all metadata
        logger.info("Verifying recovered node has all metadata...");
        QuorusStateStore recoveredStateMachine = (QuorusStateStore) followerToFail.getStateStore();
        
        for (TransferJob job : jobsWhileDown) {
            assertTrue(recoveredStateMachine.hasTransferJob(job.getJobId()),
                "Recovered node should have job " + job.getJobId() + " that was submitted while it was down");
            logger.info("✓ Recovered node has job " + job.getJobId());
        }
        
        logger.info("✅ Failed node recovery test PASSED");
    }

    // Helper methods
    
    private RaftNode waitForLeaderElection() {
        await().atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(200))
            .until(() -> findCurrentLeader() != null);
        return findCurrentLeader();
    }
    
    private RaftNode findCurrentLeader() {
        return cluster.stream()
            .filter(node -> node.getState() == RaftNode.State.LEADER)
            .findFirst()
            .orElse(null);
    }
    
    private boolean allNodesHaveJob(String jobId) {
        return cluster.stream().allMatch(node -> 
            ((QuorusStateStore) node.getStateStore()).hasTransferJob(jobId));
    }
    
    private TransferJob createTestTransferJob(String jobId, String description) {
        try {
            TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("sftp://testhost/test/source/" + jobId + ".txt"))
                .destinationPath(Paths.get("/test/dest/" + jobId + ".txt"))
                .expectedSize(1024L)
                .metadata("description", description)
                .metadata("testId", jobId)
                .build();
            
            return new TransferJob(request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test transfer job", e);
        }
    }
}
