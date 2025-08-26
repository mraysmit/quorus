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

import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.api.dto.AgentHeartbeatRequest;
import dev.mars.quorus.api.dto.AgentHeartbeatResponse;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.state.AgentCommand;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service responsible for processing agent heartbeats and managing agent health.
 * This service handles incoming heartbeat messages, updates agent status,
 * and detects failed agents based on missed heartbeats.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
@ApplicationScoped
public class HeartbeatProcessor {

    private static final Logger logger = Logger.getLogger(HeartbeatProcessor.class.getName());
    
    // Configuration constants
    private static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 30000; // 30 seconds
    private static final long AGENT_TIMEOUT_MS = 90000; // 90 seconds (3 missed heartbeats)
    private static final long FAILURE_CHECK_INTERVAL_MS = 15000; // Check every 15 seconds
    private static final long DEGRADED_THRESHOLD_MS = 60000; // 60 seconds for degraded status

    @Inject
    RaftNode raftNode;

    @Inject
    AgentRegistryService agentRegistryService;

    // Track last heartbeat times for failure detection
    private final ConcurrentHashMap<String, Instant> lastHeartbeatTimes = new ConcurrentHashMap<>();
    
    // Track heartbeat sequence numbers to detect duplicates/out-of-order
    private final ConcurrentHashMap<String, Long> lastSequenceNumbers = new ConcurrentHashMap<>();

    // Scheduled executor for failure detection
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    private volatile boolean started = false;

    /**
     * Start the heartbeat processor.
     * This initializes the failure detection monitoring.
     */
    public void start() {
        if (started) {
            logger.warning("HeartbeatProcessor already started");
            return;
        }

        logger.info("Starting HeartbeatProcessor");
        
        // Start failure detection monitoring
        scheduler.scheduleAtFixedRate(
            this::checkForFailedAgents,
            FAILURE_CHECK_INTERVAL_MS,
            FAILURE_CHECK_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        started = true;
        logger.info("HeartbeatProcessor started successfully");
    }

    /**
     * Stop the heartbeat processor.
     */
    public void stop() {
        if (!started) {
            return;
        }

        logger.info("Stopping HeartbeatProcessor");
        scheduler.shutdown();
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        started = false;
        logger.info("HeartbeatProcessor stopped");
    }

    /**
     * Process an incoming heartbeat from an agent.
     * 
     * @param request the heartbeat request
     * @return a future containing the heartbeat response
     */
    public CompletableFuture<AgentHeartbeatResponse> processHeartbeat(AgentHeartbeatRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String agentId = request.getAgentId();
                logger.fine("Processing heartbeat from agent: " + agentId);

                // Validate the heartbeat request
                validateHeartbeatRequest(request);

                // Get the agent info
                AgentInfo agentInfo = agentRegistryService.getAgent(agentId);
                if (agentInfo == null) {
                    logger.warning("Received heartbeat from unregistered agent: " + agentId);
                    return AgentHeartbeatResponse.failure(agentId, 
                        "Agent not registered", "AGENT_NOT_REGISTERED");
                }

                // Check sequence number for duplicate detection
                Long lastSeqNum = lastSequenceNumbers.get(agentId);
                if (lastSeqNum != null && request.getSequenceNumber() <= lastSeqNum) {
                    logger.warning("Received duplicate or out-of-order heartbeat from agent: " + 
                                 agentId + ", seq: " + request.getSequenceNumber() + 
                                 ", last: " + lastSeqNum);
                    return AgentHeartbeatResponse.failure(agentId, 
                        "Duplicate or out-of-order heartbeat", "INVALID_SEQUENCE");
                }

                // Update heartbeat tracking
                Instant now = Instant.now();
                lastHeartbeatTimes.put(agentId, now);
                lastSequenceNumbers.put(agentId, request.getSequenceNumber());

                // Update agent information
                updateAgentFromHeartbeat(agentInfo, request, now);

                // Submit status update to distributed state if needed
                if (shouldUpdateDistributedState(agentInfo, request)) {
                    submitAgentStatusUpdate(agentInfo, request);
                }

                // Create successful response
                AgentHeartbeatResponse response = AgentHeartbeatResponse.success(
                    agentId, 
                    request.getSequenceNumber(), 
                    DEFAULT_HEARTBEAT_INTERVAL_MS
                );

                // Add any instructions for the agent
                addAgentInstructions(response, agentInfo, request);

                logger.fine("Heartbeat processed successfully for agent: " + agentId);
                return response;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to process heartbeat: " + e.getMessage(), e);
                return AgentHeartbeatResponse.failure(request.getAgentId(), 
                    "Internal server error", "PROCESSING_ERROR");
            }
        });
    }

    /**
     * Check for agents that have failed to send heartbeats.
     * This method runs periodically to detect failed agents.
     */
    private void checkForFailedAgents() {
        try {
            Instant now = Instant.now();
            
            // Get all registered agents
            agentRegistryService.getAllAgents().forEach(agentInfo -> {
                String agentId = agentInfo.getAgentId();
                Instant lastHeartbeat = lastHeartbeatTimes.get(agentId);
                
                if (lastHeartbeat == null) {
                    // No heartbeat received yet - use registration time
                    lastHeartbeat = agentInfo.getRegistrationTime();
                }
                
                Duration timeSinceLastHeartbeat = Duration.between(lastHeartbeat, now);
                
                // Check for different failure conditions
                if (timeSinceLastHeartbeat.toMillis() > AGENT_TIMEOUT_MS) {
                    handleFailedAgent(agentInfo, timeSinceLastHeartbeat);
                } else if (timeSinceLastHeartbeat.toMillis() > DEGRADED_THRESHOLD_MS) {
                    handleDegradedAgent(agentInfo, timeSinceLastHeartbeat);
                }
            });
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during failure detection check", e);
        }
    }

    /**
     * Validate the heartbeat request.
     */
    private void validateHeartbeatRequest(AgentHeartbeatRequest request) {
        if (request.getAgentId() == null || request.getAgentId().trim().isEmpty()) {
            throw new IllegalArgumentException("Agent ID is required");
        }
        
        if (request.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }
        
        if (request.getStatus() == null) {
            throw new IllegalArgumentException("Status is required");
        }
        
        // Check timestamp is not too far in the future or past
        Instant now = Instant.now();
        Duration timeDiff = Duration.between(request.getTimestamp(), now).abs();
        if (timeDiff.toMinutes() > 5) {
            throw new IllegalArgumentException("Timestamp is too far from server time");
        }
    }

    /**
     * Update agent information from heartbeat.
     */
    private void updateAgentFromHeartbeat(AgentInfo agentInfo, AgentHeartbeatRequest request, Instant now) {
        // Update basic information
        agentInfo.setLastHeartbeat(now);
        agentInfo.setStatus(request.getStatus());
        
        // Update system and network info if provided
        if (request.getSystemInfo() != null) {
            agentInfo.getCapabilities().setSystemInfo(request.getSystemInfo());
        }
        
        if (request.getNetworkInfo() != null) {
            agentInfo.getCapabilities().setNetworkInfo(request.getNetworkInfo());
        }
        
        // Update metadata
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            agentInfo.getMetadata().putAll(request.getMetadata());
        }
    }

    /**
     * Determine if we should update the distributed state.
     */
    private boolean shouldUpdateDistributedState(AgentInfo agentInfo, AgentHeartbeatRequest request) {
        // Update if status changed or if it's been a while since last update
        return !agentInfo.getStatus().equals(request.getStatus()) ||
               Duration.between(agentInfo.getLastHeartbeat(), Instant.now()).toMinutes() > 5;
    }

    /**
     * Submit agent status update to distributed state.
     */
    private void submitAgentStatusUpdate(AgentInfo agentInfo, AgentHeartbeatRequest request) {
        if (raftNode.isLeader()) {
            try {
                AgentCommand command = AgentCommand.heartbeat(agentInfo.getAgentId());
                
                raftNode.submitCommand(command);
                logger.fine("Submitted agent status update to distributed state: " + agentInfo.getAgentId());
                
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to submit agent status update", e);
            }
        }
    }

    /**
     * Add instructions for the agent based on current state.
     */
    private void addAgentInstructions(AgentHeartbeatResponse response, AgentInfo agentInfo, 
                                    AgentHeartbeatRequest request) {
        // Add maintenance instructions if needed
        if (request.getNextMaintenanceWindow() != null && 
            request.getNextMaintenanceWindow().isBefore(Instant.now().plus(Duration.ofHours(1)))) {
            
            AgentHeartbeatResponse.AgentInstruction instruction = 
                new AgentHeartbeatResponse.AgentInstruction("maintenance", "prepare_for_maintenance");
            instruction.setPriority(5);
            instruction.setDeadline(request.getNextMaintenanceWindow());
            response.addInstruction(instruction);
        }
        
        // Add cluster health information
        response.setClusterHealth(raftNode.isLeader() ? "healthy" : "follower");
    }

    /**
     * Handle a failed agent.
     */
    private void handleFailedAgent(AgentInfo agentInfo, Duration timeSinceLastHeartbeat) {
        if (agentInfo.getStatus() != AgentStatus.UNREACHABLE && 
            agentInfo.getStatus() != AgentStatus.FAILED) {
            
            logger.warning("Agent failed: " + agentInfo.getAgentId() + 
                         " (no heartbeat for " + timeSinceLastHeartbeat.toSeconds() + " seconds)");
            
            // Update status to unreachable
            agentInfo.setStatus(AgentStatus.UNREACHABLE);
            
            // Submit to distributed state
            if (raftNode.isLeader()) {
                try {
                    AgentCommand command = AgentCommand.updateStatus(
                        agentInfo.getAgentId(), 
                        AgentStatus.UNREACHABLE
                    );
                    raftNode.submitCommand(command);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to update failed agent status", e);
                }
            }
        }
    }

    /**
     * Handle a degraded agent.
     */
    private void handleDegradedAgent(AgentInfo agentInfo, Duration timeSinceLastHeartbeat) {
        if (agentInfo.getStatus() == AgentStatus.HEALTHY || agentInfo.getStatus() == AgentStatus.ACTIVE) {
            logger.info("Agent degraded: " + agentInfo.getAgentId() + 
                       " (no heartbeat for " + timeSinceLastHeartbeat.toSeconds() + " seconds)");
            
            agentInfo.setStatus(AgentStatus.DEGRADED);
        }
    }

    /**
     * Get heartbeat statistics.
     */
    public HeartbeatStatistics getHeartbeatStatistics() {
        int totalAgents = lastHeartbeatTimes.size();
        Instant now = Instant.now();
        
        long healthyAgents = lastHeartbeatTimes.entrySet().stream()
            .mapToLong(entry -> {
                Duration timeSince = Duration.between(entry.getValue(), now);
                return timeSince.toMillis() < DEGRADED_THRESHOLD_MS ? 1 : 0;
            })
            .sum();
        
        long degradedAgents = lastHeartbeatTimes.entrySet().stream()
            .mapToLong(entry -> {
                Duration timeSince = Duration.between(entry.getValue(), now);
                return timeSince.toMillis() >= DEGRADED_THRESHOLD_MS && 
                       timeSince.toMillis() < AGENT_TIMEOUT_MS ? 1 : 0;
            })
            .sum();
        
        long failedAgents = lastHeartbeatTimes.entrySet().stream()
            .mapToLong(entry -> {
                Duration timeSince = Duration.between(entry.getValue(), now);
                return timeSince.toMillis() >= AGENT_TIMEOUT_MS ? 1 : 0;
            })
            .sum();
        
        return new HeartbeatStatistics(totalAgents, healthyAgents, degradedAgents, failedAgents);
    }

    /**
     * Heartbeat statistics data class.
     */
    public static class HeartbeatStatistics {
        private final int totalAgents;
        private final long healthyAgents;
        private final long degradedAgents;
        private final long failedAgents;

        public HeartbeatStatistics(int totalAgents, long healthyAgents, long degradedAgents, long failedAgents) {
            this.totalAgents = totalAgents;
            this.healthyAgents = healthyAgents;
            this.degradedAgents = degradedAgents;
            this.failedAgents = failedAgents;
        }

        public int getTotalAgents() { return totalAgents; }
        public long getHealthyAgents() { return healthyAgents; }
        public long getDegradedAgents() { return degradedAgents; }
        public long getFailedAgents() { return failedAgents; }
    }
}
