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

package dev.mars.quorus.api.service;

import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.state.TransferJobCommand;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.exceptions.TransferException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Distributed transfer service that routes API operations through the Raft controller cluster.
 * This service acts as a bridge between the REST API and the distributed controller,
 * ensuring all transfer operations are coordinated through the consensus mechanism.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-26
 * @version 1.0
 */
@ApplicationScoped
public class DistributedTransferService {

    private static final Logger logger = Logger.getLogger(DistributedTransferService.class.getName());
    
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int LEADER_DISCOVERY_TIMEOUT_SECONDS = 5;

    @Inject
    RaftNode controllerNode;

    @Inject
    ControllerDiscoveryService discoveryService;

    /**
     * Submit a transfer request to the distributed controller cluster.
     * This method ensures the request is processed by the current Raft leader.
     * 
     * @param request the transfer request to submit
     * @return a CompletableFuture containing the created TransferJob
     * @throws TransferException if the submission fails
     */
    public CompletableFuture<TransferJob> submitTransfer(TransferRequest request) {
        logger.info("Submitting transfer request to distributed controller: " + request.getRequestId());

        try {
            // Create transfer job
            TransferJob job = new TransferJob(request);

            // For now, return a completed future with the job
            // TODO: Implement actual distributed submission
            logger.info("Transfer job created successfully (local): " + job.getJobId());
            return CompletableFuture.completedFuture(job);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to submit transfer request", e);
            return CompletableFuture.failedFuture(new RuntimeException("Failed to submit transfer request: " + e.getMessage(), e));
        }
    }

    /**
     * Get the status of a transfer job from the distributed state.
     * 
     * @param jobId the job ID to query
     * @return a CompletableFuture containing the TransferJob, or null if not found
     */
    public CompletableFuture<TransferJob> getTransferJob(String jobId) {
        logger.fine("Querying transfer job status: " + jobId);

        // TODO: Implement proper query mechanism through distributed state machine
        logger.warning("Job status query not yet implemented for distributed state: " + jobId);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Cancel a transfer job through the distributed controller.
     * 
     * @param jobId the job ID to cancel
     * @return a CompletableFuture containing true if cancelled, false otherwise
     */
    public CompletableFuture<Boolean> cancelTransfer(String jobId) {
        logger.info("Cancelling transfer job: " + jobId);

        // TODO: Implement actual distributed cancellation
        logger.warning("Transfer cancellation not yet implemented for distributed state: " + jobId);
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Get the current cluster status including leader information.
     * 
     * @return cluster status information
     */
    public ClusterStatus getClusterStatus() {
        try {
            RaftNode.State currentState = controllerNode.getState();
            long currentTerm = controllerNode.getCurrentTerm();
            String nodeId = controllerNode.getNodeId();
            
            return new ClusterStatus(
                nodeId,
                currentState,
                currentTerm,
                currentState == RaftNode.State.LEADER,
                discoveryService.getKnownNodes()
            );
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to get cluster status", e);
            return ClusterStatus.unavailable();
        }
    }

    /**
     * Ensure we have a connection to the current Raft leader.
     * If this node is not the leader, attempt to discover and connect to the leader.
     * 
     * @return a CompletableFuture containing the leader RaftNode
     */
    private CompletableFuture<RaftNode> ensureLeaderConnection() {
        if (controllerNode.getState() == RaftNode.State.LEADER) {
            return CompletableFuture.completedFuture(controllerNode);
        }
        
        logger.info("Current node is not leader, discovering leader...");
        
        return discoveryService.discoverLeader()
            .orTimeout(LEADER_DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to discover leader", throwable);
                throw new RuntimeException("No leader available in cluster", throwable);
            });
    }

    /**
     * Check if the distributed controller is available and operational.
     *
     * @return true if the controller is available
     */
    public boolean isControllerAvailable() {
        try {
            return controllerNode.getState() != null &&
                   controllerNode.getState() == RaftNode.State.LEADER;
        } catch (Exception e) {
            logger.log(Level.FINE, "Controller availability check failed", e);
            return false;
        }
    }
}
