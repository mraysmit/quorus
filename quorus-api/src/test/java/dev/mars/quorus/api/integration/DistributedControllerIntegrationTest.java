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

package dev.mars.quorus.api.integration;

import dev.mars.quorus.api.service.DistributedTransferService;
import dev.mars.quorus.api.service.ClusterStatus;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.net.URI;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the distributed controller functionality.
 * This test verifies that the REST API can successfully integrate with the Raft controller.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
@QuarkusTest
public class DistributedControllerIntegrationTest {

    @Inject
    DistributedTransferService distributedTransferService;

    @Inject
    RaftNode raftNode;

    @BeforeEach
    void setUp() {
        // Ensure the Raft node is started
        // Note: The node is automatically started by the configuration

        // Wait for the node to become leader (single node cluster)
        try {
            // Wait up to 5 seconds for the node to become leader
            for (int i = 0; i < 50; i++) {
                if (raftNode.getState() == RaftNode.State.LEADER) {
                    break;
                }
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void testControllerAvailability() {
        // Test that the distributed controller is available
        assertTrue(distributedTransferService.isControllerAvailable(), 
                  "Distributed controller should be available");
    }

    @Test
    void testClusterStatus() {
        // Test cluster status retrieval
        ClusterStatus status = distributedTransferService.getClusterStatus();
        
        assertNotNull(status, "Cluster status should not be null");
        assertTrue(status.isAvailable(), "Cluster should be available");
        assertNotNull(status.getNodeId(), "Node ID should not be null");
        assertTrue(status.getNodeId().startsWith("api-node"), "Node ID should have correct prefix");
        
        // In a single-node cluster, this node should be the leader
        assertTrue(status.isLeader() || status.getState() == RaftNode.State.LEADER, 
                  "Single node should be leader");
    }

    @Test
    void testDistributedTransferSubmission() throws Exception {
        // Create a test transfer request
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("file:///test/source.txt"))
                .destinationPath(Paths.get("/test/destination.txt"))
                .build();

        // Submit the transfer through the distributed service
        CompletableFuture<TransferJob> future = distributedTransferService.submitTransfer(request);

        // Wait for completion
        TransferJob job = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(job, "Transfer job should be created");
        assertNotNull(job.getJobId(), "Job ID should not be null");
        assertEquals(request.getRequestId(), job.getRequest().getRequestId(), 
                    "Request ID should match");
    }

    @Test
    void testTransferJobQuery() throws Exception {
        // For now, this should return null since query is not implemented
        CompletableFuture<TransferJob> future = distributedTransferService.getTransferJob("test-job-id");

        TransferJob job = future.get(3, TimeUnit.SECONDS);

        // Currently returns null as query is not implemented
        assertNull(job, "Job query should return null (not yet implemented)");
    }

    @Test
    void testTransferJobCancellation() throws Exception {
        // For now, this should return false since cancellation is not implemented
        CompletableFuture<Boolean> future = distributedTransferService.cancelTransfer("test-job-id");
        
        Boolean cancelled = future.get(3, TimeUnit.SECONDS);
        
        // Currently returns false as cancellation is not implemented
        assertFalse(cancelled, "Job cancellation should return false (not yet implemented)");
    }

    @Test
    void testRaftNodeIntegration() {
        // Test that the Raft node is properly configured and running
        assertNotNull(raftNode, "Raft node should be injected");
        assertNotNull(raftNode.getNodeId(), "Node ID should not be null");
        assertTrue(raftNode.getNodeId().startsWith("api-node"), "Node ID should have correct prefix");

        // In a single-node cluster, it should become leader
        assertEquals(RaftNode.State.LEADER, raftNode.getState(),
                    "Single node should be in LEADER state");
        assertTrue(raftNode.isLeader(), "Single node should be leader");
    }
}
