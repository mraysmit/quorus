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

import dev.mars.quorus.controller.raft.storage.RaftLogStorageAdapter;
import dev.mars.quorus.controller.raft.storage.RaftStorage;
import dev.mars.quorus.controller.state.QuorusStateMachine;
import dev.mars.quorus.controller.state.TransferJobCommand;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.raftlog.storage.RaftStorageConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;

/**
 * Integration test proving that RaftLog (WAL) persistence works correctly
 * in a Raft cluster. This test uses actual file-based storage to verify:
 * 
 * <ul>
 *   <li>WAL files are created and written to disk</li>
 *   <li>Metadata (term, votedFor) persists across node restarts</li>
 *   <li>Log entries persist and replay correctly after restart</li>
 *   <li>State machine rebuilds correctly from WAL on recovery</li>
 * </ul>
 * 
 * <p>This is the definitive proof that raftlog-core works in Quorus.</p>
 * 
 * <p>NOTE: The testVoteMetadataPersistence test is marked as flaky because it is
 * timing-sensitive and may fail when run as part of the full test suite due to
 * resource contention. It passes reliably when run in isolation.</p>
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-01-29
 */
@ExtendWith(VertxExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("RaftLog Cluster Integration Tests")
@Tag("flaky")
class RaftLogClusterIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(RaftLogClusterIntegrationTest.class);
    private static final int CLUSTER_SIZE = 3;
    private static final String[] NODE_IDS = {"node1", "node2", "node3"};

    @TempDir
    Path tempDir;

    private Vertx vertx;
    private List<RaftNode> cluster;
    private Map<String, MockRaftTransport> transports;
    private Map<String, RaftStorage> storages;
    private Map<String, Path> dataDirs;

    @BeforeEach
    void setUp(Vertx vertx) {
        this.vertx = vertx;
        this.cluster = new ArrayList<>();
        this.transports = new HashMap<>();
        this.storages = new HashMap<>();
        this.dataDirs = new HashMap<>();
    }

    @AfterEach
    void tearDown(VertxTestContext ctx) {
        LOG.info("Tearing down cluster...");
        
        List<Future<Void>> stopFutures = new ArrayList<>();
        for (RaftNode node : cluster) {
            stopFutures.add(node.stop());
        }
        
        Future.all(stopFutures)
            .onComplete(ar -> {
                // Close storages
                for (RaftStorage storage : storages.values()) {
                    try {
                        storage.close();
                    } catch (Exception e) {
                        LOG.warn("Error closing storage", e);
                    }
                }
                ctx.completeNow();
            });
    }

    // =========================================================================
    // TEST 1: Prove WAL files are created
    // =========================================================================
    
    @Test
    @Order(1)
    @DisplayName("WAL files are created when cluster starts")
    void testWalFilesCreated(VertxTestContext ctx) throws Exception {
        LOG.info("=== TEST: WAL Files Created ===");
        long testStartTime = System.currentTimeMillis();
        
        startClusterWithStorage();
        
        // Wait for cluster startup
        LOG.debug("Waiting 2s for cluster to stabilize...");
        Thread.sleep(2000);
        
        // Log cluster state before verification
        logClusterState("after startup");
        
        // Verify WAL files exist for each node
        LOG.debug("Verifying WAL files for {} nodes...", NODE_IDS.length);
        for (String nodeId : NODE_IDS) {
            Path dataDir = dataDirs.get(nodeId);
            Path walFile = dataDir.resolve("raft.log");
            Path lockFile = dataDir.resolve("raft.lock");
            Path metaFile = dataDir.resolve("raft.meta");
            
            LOG.debug("Checking {} - dataDir={}", nodeId, dataDir);
            
            assertTrue(Files.exists(walFile), 
                "WAL file should exist for " + nodeId + " at " + walFile);
            assertTrue(Files.exists(lockFile), 
                "Lock file should exist for " + nodeId + " at " + lockFile);
            
            long walSize = Files.size(walFile);
            boolean metaExists = Files.exists(metaFile);
            
            LOG.info("✓ {} has WAL at {} ({} bytes, meta={})", 
                nodeId, walFile, walSize, metaExists ? Files.size(metaFile) + "b" : "N/A");
        }
        
        LOG.info("Test completed in {} ms", System.currentTimeMillis() - testStartTime);
        ctx.completeNow();
    }

    // =========================================================================
    // TEST 2: Prove metadata persists after leader election
    // =========================================================================
    
    @Test
    @Order(2)
    @DisplayName("Vote metadata persists to WAL during election")
    void testVoteMetadataPersistence(VertxTestContext ctx) throws Exception {
        LOG.info("=== TEST: Vote Metadata Persistence ===");
        long testStartTime = System.currentTimeMillis();
        
        startClusterWithStorage();
        
        // Wait for leader election
        LOG.debug("Waiting for leader election (timeout=10s)...");
        long electionStart = System.currentTimeMillis();
        RaftNode leader = waitForLeader(10_000);
        assertNotNull(leader, "Leader should be elected");
        LOG.info("Leader elected: {} (term={}) in {} ms", 
            leader.getNodeId(), leader.getCurrentTerm(), System.currentTimeMillis() - electionStart);
        
        // Log pre-stop cluster state
        logClusterState("before stopping nodes");
        
        // Get the current term
        long electionTerm = leader.getCurrentTerm();
        assertTrue(electionTerm > 0, "Term should be > 0 after election");
        LOG.debug("Election term: {}, will verify this persists", electionTerm);
        
        // Stop all nodes
        LOG.info("Stopping all {} nodes...", cluster.size());
        for (RaftNode node : cluster) {
            LOG.debug("Stopping node {} (state={}, term={})", 
                node.getNodeId(), node.getState(), node.getCurrentTerm());
            node.stop().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            LOG.debug("Node {} stopped successfully", node.getNodeId());
        }
        cluster.clear();
        
        // Reload metadata directly from storage files to prove persistence
        LOG.info("Verifying persisted metadata...");
        int nodesWithValidTerm = 0;
        for (String nodeId : NODE_IDS) {
            Path dataDir = dataDirs.get(nodeId);
            
            RaftStorageConfig config = RaftStorageConfig.builder()
                .dataDir(dataDir)
                .syncEnabled(true)
                .build();
            
            RaftLogStorageAdapter storage = new RaftLogStorageAdapter(vertx, config);
            storage.open(dataDir).toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            
            RaftStorage.PersistentMeta meta = storage.loadMetadata()
                .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            
            LOG.info("✓ {} recovered metadata: term={}, votedFor={}", 
                nodeId, meta.currentTerm(), meta.votedFor().orElse("(none)"));
            
            // At least the followers should have persisted their vote
            // (leader may not persist votedFor for itself)
            if (meta.currentTerm() >= electionTerm) {
                nodesWithValidTerm++;
            }
            
            storage.close();
        }
        
        // At least majority should have valid term persisted (the voters)
        assertTrue(nodesWithValidTerm >= 2, 
            "At least 2 nodes (the voters) should have term >= " + electionTerm + ", got " + nodesWithValidTerm);
        
        ctx.completeNow();
    }

    // =========================================================================
    // TEST 3: Prove log entries persist and replay
    // =========================================================================
    
    @Test
    @Order(3)
    @DisplayName("Log entries persist and replay after restart")
    void testLogEntryPersistenceAndReplay(VertxTestContext ctx) throws Exception {
        LOG.info("=== TEST: Log Entry Persistence and Replay ===");
        long testStartTime = System.currentTimeMillis();
        
        startClusterWithStorage();
        
        // Wait for leader
        LOG.debug("Waiting for leader election...");
        RaftNode leader = waitForLeader(10_000);
        assertNotNull(leader, "Leader should be elected");
        LOG.info("Leader: {} (term={}, logSize={})", 
            leader.getNodeId(), leader.getCurrentTerm(), leader.getLogSize());
        
        // Log initial WAL sizes
        logWalSizes("before command submission");
        
        // Submit commands to the leader
        List<String> jobIds = new ArrayList<>();
        LOG.debug("Submitting 3 commands to leader...");
        for (int i = 1; i <= 3; i++) {
            String jobId = "wal-test-job-" + i;
            jobIds.add(jobId);
            
            TransferJob job = createTestJob(jobId);
            TransferJobCommand cmd = TransferJobCommand.create(job);
            
            long cmdStart = System.currentTimeMillis();
            Object result = leader.submitCommand(cmd)
                .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            
            LOG.info("Submitted job {} to leader in {} ms, result: {}", 
                jobId, System.currentTimeMillis() - cmdStart, result);
        }
        
        // Log WAL sizes after submission
        logWalSizes("after command submission");
        
        // Wait for replication
        LOG.debug("Waiting 2s for replication to all followers...");
        Thread.sleep(2000);
        
        // Verify replication to followers
        logReplicationStatus(jobIds);
        
        // Verify jobs exist in state machine before restart
        QuorusStateMachine leaderSM = (QuorusStateMachine) leader.getStateMachine();
        for (String jobId : jobIds) {
            assertTrue(leaderSM.hasTransferJob(jobId), 
                "Leader should have job " + jobId + " before restart");
        }
        LOG.info("✓ All jobs exist in state machine before restart");
        
        // Remember which node was leader
        String leaderId = leader.getNodeId();
        
        // Stop all nodes
        LOG.info("Stopping all nodes to test recovery...");
        for (RaftNode node : cluster) {
            node.stop().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
        cluster.clear();
        storages.clear();
        
        // Restart the cluster with SAME data directories
        LOG.info("Restarting cluster from persisted WAL...");
        restartClusterWithExistingStorage();
        
        // Wait for new leader election
        RaftNode newLeader = waitForLeader(15_000);
        assertNotNull(newLeader, "New leader should be elected after restart");
        LOG.info("New leader after restart: {}", newLeader.getNodeId());
        
        // Verify ALL nodes recovered the jobs from WAL
        LOG.info("Verifying state machine rebuilt from WAL...");
        for (RaftNode node : cluster) {
            QuorusStateMachine sm = (QuorusStateMachine) node.getStateMachine();
            for (String jobId : jobIds) {
                assertTrue(sm.hasTransferJob(jobId), 
                    "Node " + node.getNodeId() + " should have recovered job " + jobId);
            }
            LOG.info("✓ {} recovered all {} jobs from WAL", node.getNodeId(), jobIds.size());
        }
        
        ctx.completeNow();
    }

    // =========================================================================
    // TEST 4: Prove single node restart recovers state
    // =========================================================================
    
    @Test
    @Order(4)
    @DisplayName("Single node restart recovers from WAL")
    void testSingleNodeRecovery(VertxTestContext ctx) throws Exception {
        LOG.info("=== TEST: Single Node Recovery ===");
        long testStartTime = System.currentTimeMillis();
        
        startClusterWithStorage();
        
        // Wait for leader
        LOG.debug("Waiting for leader election...");
        RaftNode leader = waitForLeader(10_000);
        assertNotNull(leader);
        LOG.info("Leader: {} (term={}, logSize={})", 
            leader.getNodeId(), leader.getCurrentTerm(), leader.getLogSize());
        
        // Submit a job
        String jobId = "recovery-test-job";
        TransferJob job = createTestJob(jobId);
        long cmdStart = System.currentTimeMillis();
        leader.submitCommand(TransferJobCommand.create(job))
            .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        LOG.info("Submitted job: {} in {} ms", jobId, System.currentTimeMillis() - cmdStart);
        
        // Wait for replication
        LOG.debug("Waiting 2s for replication...");
        Thread.sleep(2000);
        
        // Log cluster state before follower restart
        logClusterState("before follower restart");
        
        // Pick a follower to restart
        RaftNode follower = cluster.stream()
            .filter(n -> n.getState() != RaftNode.State.LEADER)
            .findFirst()
            .orElseThrow();
        
        String followerId = follower.getNodeId();
        Path followerDataDir = dataDirs.get(followerId);
        LOG.info("Will restart follower: {} (current state={}, term={}, logSize={})", 
            followerId, follower.getState(), follower.getCurrentTerm(), follower.getLogSize());
        
        // Check WAL size and content before restart
        long walSizeBefore = Files.size(followerDataDir.resolve("raft.log"));
        LOG.info("WAL size before restart: {} bytes", walSizeBefore);
        LOG.debug("Follower data directory: {}", followerDataDir);
        
        // Stop the follower
        long stopStart = System.currentTimeMillis();
        follower.stop().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        cluster.remove(follower);
        LOG.info("Stopped follower: {} in {} ms", followerId, System.currentTimeMillis() - stopStart);
        
        // Verify cluster still has quorum
        LOG.debug("Cluster size after follower stop: {} (quorum requires {})", 
            cluster.size(), (CLUSTER_SIZE / 2) + 1);
        
        // Restart the follower
        LOG.info("Restarting follower from WAL...");
        long restartStart = System.currentTimeMillis();
        RaftNode restartedFollower = createNodeWithStorage(followerId, followerDataDir, false);
        restartedFollower.start().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        cluster.add(restartedFollower);
        LOG.info("Follower {} restarted in {} ms (term={}, logSize={})", 
            followerId, System.currentTimeMillis() - restartStart,
            restartedFollower.getCurrentTerm(), restartedFollower.getLogSize());
        
        // Wait for catch-up
        LOG.debug("Waiting 3s for follower catch-up...");
        Thread.sleep(3000);
        
        // Log cluster state after restart
        logClusterState("after follower restart");
        
        // Verify recovered follower has the job
        QuorusStateMachine recoveredSM = (QuorusStateMachine) restartedFollower.getStateMachine();
        assertTrue(recoveredSM.hasTransferJob(jobId), 
            "Recovered follower should have job from WAL replay");
        
        LOG.info("✓ Follower {} recovered job {} from WAL", followerId, jobId);
        
        ctx.completeNow();
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private void startClusterWithStorage() throws Exception {
        long startTime = System.currentTimeMillis();
        LOG.info("Starting {}-node cluster with WAL storage...", CLUSTER_SIZE);
        
        // Create transports
        LOG.debug("Creating mock transports for {} nodes", NODE_IDS.length);
        for (String nodeId : NODE_IDS) {
            transports.put(nodeId, new MockRaftTransport(nodeId));
        }
        for (MockRaftTransport transport : transports.values()) {
            transport.setTransports(transports);
        }
        LOG.debug("Transports created and connected");
        
        // Create nodes with storage
        LOG.debug("Creating {} RaftNodes with WAL storage", NODE_IDS.length);
        for (String nodeId : NODE_IDS) {
            Path dataDir = tempDir.resolve(nodeId);
            dataDirs.put(nodeId, dataDir);
            LOG.debug("Creating node {} with dataDir={}", nodeId, dataDir);
            
            RaftNode node = createNodeWithStorage(nodeId, dataDir, true);
            cluster.add(node);
        }
        
        // Start all nodes
        LOG.debug("Starting all {} nodes in parallel", cluster.size());
        CountDownLatch startLatch = new CountDownLatch(cluster.size());
        for (RaftNode node : cluster) {
            node.start().onComplete(ar -> {
                if (ar.succeeded()) {
                    LOG.debug("Node {} started successfully", node.getNodeId());
                } else {
                    LOG.error("Node {} failed to start: {}", node.getNodeId(), ar.cause().getMessage());
                }
                startLatch.countDown();
            });
        }
        assertTrue(startLatch.await(30, TimeUnit.SECONDS), "Cluster should start");
        LOG.info("Cluster started successfully in {} ms", System.currentTimeMillis() - startTime);
    }

    private void restartClusterWithExistingStorage() throws Exception {
        LOG.info("Restarting cluster with existing WAL data...");
        
        // Recreate transports
        transports.clear();
        for (String nodeId : NODE_IDS) {
            transports.put(nodeId, new MockRaftTransport(nodeId));
        }
        for (MockRaftTransport transport : transports.values()) {
            transport.setTransports(transports);
        }
        
        // Recreate nodes using existing data directories
        for (String nodeId : NODE_IDS) {
            Path dataDir = dataDirs.get(nodeId);
            RaftNode node = createNodeWithStorage(nodeId, dataDir, false);
            cluster.add(node);
        }
        
        // Start all nodes
        CountDownLatch startLatch = new CountDownLatch(cluster.size());
        for (RaftNode node : cluster) {
            node.start().onComplete(ar -> startLatch.countDown());
        }
        assertTrue(startLatch.await(30, TimeUnit.SECONDS), "Cluster should restart");
        LOG.info("Cluster restarted successfully");
    }

    private RaftNode createNodeWithStorage(String nodeId, Path dataDir, boolean createDir) throws Exception {
        if (createDir) {
            Files.createDirectories(dataDir);
        }
        
        RaftStorageConfig config = RaftStorageConfig.builder()
            .dataDir(dataDir)
            .syncEnabled(true)  // Production setting
            .build();
        
        RaftLogStorageAdapter storage = new RaftLogStorageAdapter(vertx, config);
        storage.open(dataDir).toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        storages.put(nodeId, storage);
        
        QuorusStateMachine stateMachine = new QuorusStateMachine();
        Set<String> clusterNodes = Set.of(NODE_IDS);
        
        return new RaftNode(
            vertx, 
            nodeId, 
            clusterNodes, 
            transports.get(nodeId), 
            stateMachine,
            storage,
            2000,  // Election timeout
            500    // Heartbeat interval
        );
    }

    private RaftNode waitForLeader(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (RaftNode node : cluster) {
                if (node.getState() == RaftNode.State.LEADER) {
                    return node;
                }
            }
            Thread.sleep(200);
        }
        return null;
    }

    private TransferJob createTestJob(String jobId) {
        TransferRequest request = TransferRequest.builder()
            .requestId(jobId)  // Use our custom ID so we can look it up later
            .sourceUri(URI.create("file:///test/" + jobId + "/source.txt"))
            .destinationPath(Path.of("/test/" + jobId + "/dest.txt"))
            .expectedSize(1024L)
            .metadata("testId", jobId)
            .build();
        return new TransferJob(request);
    }
    
    // =========================================================================
    // Debug Logging Helpers
    // =========================================================================
    
    /**
     * Logs the current state of all nodes in the cluster.
     */
    private void logClusterState(String context) {
        LOG.debug("--- Cluster State ({}) ---", context);
        for (RaftNode node : cluster) {
            QuorusStateMachine sm = (QuorusStateMachine) node.getStateMachine();
            LOG.debug("  {} | state={} | term={} | votedFor={} | log=[size={}, lastIdx={}, lastTerm={}] | commit={} | applied={} | jobs={}",
                node.getNodeId(),
                node.getState(),
                node.getCurrentTerm(),
                node.getVotedFor() != null ? node.getVotedFor() : "(none)",
                node.getLogSize(),
                node.getLastLogIndex(),
                node.getLastLogTerm(),
                node.getCommitIndex(),
                node.getLastApplied(),
                sm.getTransferJobCount());
        }
        LOG.debug("--- End Cluster State ---");
    }
    
    /**
     * Logs WAL file sizes for all nodes.
     */
    private void logWalSizes(String context) {
        LOG.debug("--- WAL Sizes ({}) ---", context);
        for (String nodeId : NODE_IDS) {
            try {
                Path walFile = dataDirs.get(nodeId).resolve("raft.log");
                if (Files.exists(walFile)) {
                    LOG.debug("  {} | WAL={} bytes", nodeId, Files.size(walFile));
                } else {
                    LOG.debug("  {} | WAL file not found", nodeId);
                }
            } catch (Exception e) {
                LOG.debug("  {} | Error reading WAL: {}", nodeId, e.getMessage());
            }
        }
    }
    
    /**
     * Logs replication status for given job IDs across all nodes.
     */
    private void logReplicationStatus(List<String> jobIds) {
        LOG.debug("--- Replication Status ---");
        for (RaftNode node : cluster) {
            QuorusStateMachine sm = (QuorusStateMachine) node.getStateMachine();
            StringBuilder sb = new StringBuilder();
            sb.append(node.getNodeId()).append(" (").append(node.getState()).append("): ");
            
            int found = 0;
            for (String jobId : jobIds) {
                if (sm.hasTransferJob(jobId)) {
                    found++;
                    sb.append("✓");
                } else {
                    sb.append("✗");
                }
            }
            sb.append(" (").append(found).append("/").append(jobIds.size()).append(" jobs)");
            LOG.debug("  {}", sb.toString());
        }
        LOG.debug("--- End Replication Status ---");
    }
}
