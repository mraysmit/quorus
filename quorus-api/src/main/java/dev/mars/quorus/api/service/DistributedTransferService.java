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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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

    private static final Logger logger = LoggerFactory.getLogger(DistributedTransferService.class);
    
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
        logger.debug("submitTransfer() called: requestId={}, source={}, destination={}", 
                request.getRequestId(), request.getSourceUri(), request.getDestinationUri());
        logger.info("Submitting transfer request to distributed controller: requestId={}", request.getRequestId());

        try {
            // Create transfer job
            logger.debug("Creating TransferJob from request: requestId={}", request.getRequestId());
            TransferJob job = new TransferJob(request);
            logger.debug("TransferJob created: jobId={}, requestId={}", job.getJobId(), request.getRequestId());

            // For now, return a completed future with the job
            // TODO: Implement actual distributed submission
            logger.info("Transfer job created successfully (local): jobId={}, requestId={}", 
                    job.getJobId(), request.getRequestId());
            return CompletableFuture.completedFuture(job);

        } catch (Exception e) {
            logger.error("Failed to submit transfer request: requestId={}", request.getRequestId(), e);
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
        logger.debug("getTransferJob() called: jobId={}", jobId);

        // TODO: Implement proper query mechanism through distributed state machine
        logger.warn("Job status query not yet implemented for distributed state: jobId={}", jobId);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Cancel a transfer job through the distributed controller.
     * 
     * @param jobId the job ID to cancel
     * @return a CompletableFuture containing true if cancelled, false otherwise
     */
    public CompletableFuture<Boolean> cancelTransfer(String jobId) {
        logger.debug("cancelTransfer() called: jobId={}", jobId);
        logger.info("Cancelling transfer job: jobId={}", jobId);

        // TODO: Implement actual distributed cancellation
        logger.warn("Transfer cancellation not yet implemented for distributed state: jobId={}", jobId);
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Get the current cluster status including leader information.
     * 
     * @return cluster status information
     */
    public ClusterStatus getClusterStatus() {
        logger.debug("getClusterStatus() called");
        try {
            RaftNode.State currentState = controllerNode.getState();
            long currentTerm = controllerNode.getCurrentTerm();
            String nodeId = controllerNode.getNodeId();
            
            logger.debug("Cluster status retrieved: nodeId={}, state={}, term={}, isLeader={}", 
                    nodeId, currentState, currentTerm, currentState == RaftNode.State.LEADER);
            
            return new ClusterStatus(
                nodeId,
                currentState,
                currentTerm,
                currentState == RaftNode.State.LEADER,
                discoveryService.getKnownNodes()
            );
        } catch (Exception e) {
            logger.warn("Failed to get cluster status: {}", e.getMessage());
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
        logger.debug("ensureLeaderConnection() called");
        if (controllerNode.getState() == RaftNode.State.LEADER) {
            logger.debug("Current node is leader, returning local node");
            return CompletableFuture.completedFuture(controllerNode);
        }
        
        logger.info("Current node is not leader (state={}), discovering leader...", controllerNode.getState());
        
        return discoveryService.discoverLeader()
            .orTimeout(LEADER_DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .exceptionally(throwable -> {
                logger.error("Failed to discover leader within timeout: {}s", LEADER_DISCOVERY_TIMEOUT_SECONDS, throwable);
                throw new RuntimeException("No leader available in cluster", throwable);
            });
    }

    /**
     * Check if the distributed controller is available and operational.
     *
     * @return true if the controller is available
     */
    public boolean isControllerAvailable() {
        logger.trace("isControllerAvailable() called");
        try {
            RaftNode.State state = controllerNode.getState();
            boolean available = state != null && state == RaftNode.State.LEADER;
            logger.trace("isControllerAvailable() result: state={}, available={}", state, available);
            return available;
        } catch (Exception e) {
            logger.debug("Controller availability check failed: {}", e.getMessage());
            return false;
        }
    }
}
