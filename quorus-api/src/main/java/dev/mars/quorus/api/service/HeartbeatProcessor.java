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

import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service responsible for processing agent heartbeats and managing agent health.
 * This service handles incoming heartbeat messages, updates agent status,
 * and detects failed agents based on missed heartbeats.
 *
 * <p>Vert.x 5 Migration: Converted from ScheduledExecutorService to Vert.x timers
 * for better integration with the event loop and reduced thread overhead.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-26
 * @version 1.0
 */
@ApplicationScoped
public class HeartbeatProcessor {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatProcessor.class);

    // Configuration constants
    private static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 30000; // 30 seconds
    private static final long AGENT_TIMEOUT_MS = 90000; // 90 seconds (3 missed heartbeats)
    private static final long FAILURE_CHECK_INTERVAL_MS = 15000; // Check every 15 seconds
    private static final long DEGRADED_THRESHOLD_MS = 60000; // 60 seconds for degraded status

    @Inject
    Vertx vertx;

    @Inject
    RaftNode raftNode;

    @Inject
    AgentRegistryService agentRegistryService;

    // Track last heartbeat times for failure detection
    private final ConcurrentHashMap<String, Instant> lastHeartbeatTimes = new ConcurrentHashMap<>();

    // Track heartbeat sequence numbers to detect duplicates/out-of-order
    private final ConcurrentHashMap<String, Long> lastSequenceNumbers = new ConcurrentHashMap<>();

    // Vert.x timer ID for failure detection (replaces ScheduledExecutorService)
    private long failureCheckTimerId = 0;

    // Shutdown coordination
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile boolean started = false;

    /**
     * Start the heartbeat processor.
     * This initializes the failure detection monitoring using Vert.x timers.
     */
    public void start() {
        logger.debug("start() called");
        if (closed.get()) {
            logger.error("Cannot start HeartbeatProcessor - already closed");
            throw new IllegalStateException("HeartbeatProcessor is closed, cannot start");
        }

        if (started) {
            logger.warn("HeartbeatProcessor already started, ignoring duplicate start request");
            return;
        }

        logger.info("Starting HeartbeatProcessor (Vert.x reactive mode)");
        logger.debug("Configuration: heartbeatInterval={}ms, agentTimeout={}ms, failureCheckInterval={}ms, degradedThreshold={}ms",
                DEFAULT_HEARTBEAT_INTERVAL_MS, AGENT_TIMEOUT_MS, FAILURE_CHECK_INTERVAL_MS, DEGRADED_THRESHOLD_MS);

        // Start failure detection monitoring using Vert.x timer (no ScheduledExecutorService!)
        failureCheckTimerId = vertx.setPeriodic(
            FAILURE_CHECK_INTERVAL_MS,
            id -> {
                if (!closed.get() && started) {
                    try {
                        logger.debug("Running periodic failure detection check");
                        checkForFailedAgents();
                    } catch (Exception e) {
                        logger.warn("Error in failure detection check: {}", e.getMessage(), e);
                    }
                }
            }
        );

        started = true;
        logger.info("HeartbeatProcessor started successfully: timerId={}, interval={}ms (0 extra threads)", 
                failureCheckTimerId, FAILURE_CHECK_INTERVAL_MS);
    }

    /**
     * Stop the heartbeat processor.
     * Idempotent shutdown with proper timer cancellation.
     */
    public void stop() {
        logger.debug("stop() called");
        // Idempotent shutdown
        if (closed.getAndSet(true)) {
            logger.info("HeartbeatProcessor already closed, ignoring duplicate stop request");
            return;
        }

        if (!started) {
            logger.info("HeartbeatProcessor not started, skipping stop");
            return;
        }

        logger.info("Stopping HeartbeatProcessor (reactive mode)");

        // Cancel Vert.x timer (no ScheduledExecutorService to shutdown!)
        if (failureCheckTimerId != 0) {
            boolean cancelled = vertx.cancelTimer(failureCheckTimerId);
            logger.debug("Failure check timer cancelled: timerId={}, cancelled={}", failureCheckTimerId, cancelled);
            failureCheckTimerId = 0;
        }

        started = false;
        logger.info("HeartbeatProcessor stopped (0 threads to cleanup)");
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
                logger.debug("processHeartbeat() called: agentId={}, seq={}, status={}", 
                        agentId, request.getSequenceNumber(), request.getStatus());

                // Validate the heartbeat request
                logger.debug("Validating heartbeat request: agentId={}", agentId);
                validateHeartbeatRequest(request);

                // Get the agent info
                AgentInfo agentInfo = agentRegistryService.getAgent(agentId);
                if (agentInfo == null) {
                    logger.warn("Received heartbeat from unregistered agent: agentId={}", agentId);
                    return AgentHeartbeatResponse.failure(agentId, 
                        "Agent not registered", "AGENT_NOT_REGISTERED");
                }

                // Check sequence number for duplicate detection
                // Note: Allow sequence number reset after server restart by checking if this is
                // the first heartbeat we've seen from this agent since startup
                Long lastSeqNum = lastSequenceNumbers.get(agentId);
                if (lastSeqNum != null && request.getSequenceNumber() <= lastSeqNum) {
                    logger.warn("Received duplicate or out-of-order heartbeat: agentId={}, receivedSeq={}, lastSeq={}", 
                            agentId, request.getSequenceNumber(), lastSeqNum);
                    return AgentHeartbeatResponse.failure(agentId,
                        "Duplicate or out-of-order heartbeat", "INVALID_SEQUENCE");
                } else if (lastSeqNum == null) {
                    // First heartbeat from this agent since server startup
                    logger.info("First heartbeat received from agent since server startup: agentId={}, seq={}", 
                            agentId, request.getSequenceNumber());
                }

                // Update heartbeat tracking
                Instant now = Instant.now();
                lastHeartbeatTimes.put(agentId, now);
                lastSequenceNumbers.put(agentId, request.getSequenceNumber());
                logger.debug("Heartbeat tracking updated: agentId={}, seq={}, time={}", 
                        agentId, request.getSequenceNumber(), now);

                // Update agent information
                updateAgentFromHeartbeat(agentInfo, request, now);

                // Submit status update to distributed state if needed
                if (shouldUpdateDistributedState(agentInfo, request)) {
                    logger.debug("Status change detected, submitting to distributed state: agentId={}", agentId);
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

                logger.debug("Heartbeat processed successfully: agentId={}, seq={}", agentId, request.getSequenceNumber());
                return response;

            } catch (Exception e) {
                logger.error("Failed to process heartbeat: agentId={}, error={}", request.getAgentId(), e.getMessage(), e);
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
            int totalAgents = agentRegistryService.getAllAgents().size();
            logger.debug("checkForFailedAgents() checking {} registered agents", totalAgents);
            
            // Get all registered agents
            agentRegistryService.getAllAgents().forEach(agentInfo -> {
                String agentId = agentInfo.getAgentId();
                Instant lastHeartbeat = lastHeartbeatTimes.get(agentId);
                
                if (lastHeartbeat == null) {
                    // No heartbeat received yet - use registration time
                    lastHeartbeat = agentInfo.getRegistrationTime();
                    logger.debug("No heartbeat received yet for agent, using registration time: agentId={}", agentId);
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
            logger.warn("Error during failure detection check: {}", e.getMessage(), e);
        }
    }

    /**
     * Validate the heartbeat request.
     */
    private void validateHeartbeatRequest(AgentHeartbeatRequest request) {
        logger.debug("validateHeartbeatRequest() called: agentId={}", request.getAgentId());
        
        if (request.getAgentId() == null || request.getAgentId().trim().isEmpty()) {
            logger.debug("Validation failed: Agent ID is required");
            throw new IllegalArgumentException("Agent ID is required");
        }
        
        if (request.getTimestamp() == null) {
            logger.debug("Validation failed: Timestamp is required for agentId={}", request.getAgentId());
            throw new IllegalArgumentException("Timestamp is required");
        }
        
        if (request.getStatus() == null) {
            logger.debug("Validation failed: Status is required for agentId={}", request.getAgentId());
            throw new IllegalArgumentException("Status is required");
        }
        
        // Check timestamp is not too far in the future or past
        Instant now = Instant.now();
        Duration timeDiff = Duration.between(request.getTimestamp(), now).abs();
        if (timeDiff.toMinutes() > 5) {
            logger.debug("Validation failed: Timestamp too far from server time for agentId={}, diff={}min", 
                    request.getAgentId(), timeDiff.toMinutes());
            throw new IllegalArgumentException("Timestamp is too far from server time");
        }
        
        logger.debug("validateHeartbeatRequest() passed for agentId={}", request.getAgentId());
    }

    /**
     * Update agent information from heartbeat.
     */
    private void updateAgentFromHeartbeat(AgentInfo agentInfo, AgentHeartbeatRequest request, Instant now) {
        logger.debug("updateAgentFromHeartbeat() called: agentId={}, newStatus={}", 
                agentInfo.getAgentId(), request.getStatus());
        
        // Update basic information
        AgentStatus previousStatus = agentInfo.getStatus();
        agentInfo.setLastHeartbeat(now);
        agentInfo.setStatus(request.getStatus());
        
        if (previousStatus != request.getStatus()) {
            logger.debug("Agent status changed: agentId={}, previousStatus={}, newStatus={}", 
                    agentInfo.getAgentId(), previousStatus, request.getStatus());
        }
        
        // Update system and network info if provided
        if (request.getSystemInfo() != null) {
            agentInfo.getCapabilities().setSystemInfo(request.getSystemInfo());
            logger.debug("Updated system info for agent: agentId={}", agentInfo.getAgentId());
        }
        
        if (request.getNetworkInfo() != null) {
            agentInfo.getCapabilities().setNetworkInfo(request.getNetworkInfo());
            logger.debug("Updated network info for agent: agentId={}", agentInfo.getAgentId());
        }
        
        // Update metadata
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            agentInfo.getMetadata().putAll(request.getMetadata());
            logger.debug("Updated metadata for agent: agentId={}, metadataKeys={}", 
                    agentInfo.getAgentId(), request.getMetadata().keySet());
        }
    }

    /**
     * Determine if we should update the distributed state.
     */
    private boolean shouldUpdateDistributedState(AgentInfo agentInfo, AgentHeartbeatRequest request) {
        // Update if status changed or if it's been a while since last update
        boolean shouldUpdate = !agentInfo.getStatus().equals(request.getStatus()) ||
               Duration.between(agentInfo.getLastHeartbeat(), Instant.now()).toMinutes() > 5;
        logger.debug("shouldUpdateDistributedState() result: agentId={}, shouldUpdate={}", 
                agentInfo.getAgentId(), shouldUpdate);
        return shouldUpdate;
    }

    /**
     * Submit agent status update to distributed state.
     */
    private void submitAgentStatusUpdate(AgentInfo agentInfo, AgentHeartbeatRequest request) {
        if (raftNode.isLeader()) {
            try {
                AgentCommand command = AgentCommand.heartbeat(agentInfo.getAgentId());
                logger.debug("Submitting agent status update to distributed state: agentId={}", agentInfo.getAgentId());
                
                raftNode.submitCommand(command);
                logger.debug("Agent status update submitted successfully: agentId={}", agentInfo.getAgentId());
                
            } catch (Exception e) {
                logger.warn("Failed to submit agent status update: agentId={}, error={}", 
                        agentInfo.getAgentId(), e.getMessage(), e);
            }
        } else {
            logger.debug("Skipping distributed state update - not leader: agentId={}", agentInfo.getAgentId());
        }
    }

    /**
     * Add instructions for the agent based on current state.
     */
    private void addAgentInstructions(AgentHeartbeatResponse response, AgentInfo agentInfo, 
                                    AgentHeartbeatRequest request) {
        logger.debug("addAgentInstructions() called: agentId={}", agentInfo.getAgentId());
        
        // Add maintenance instructions if needed
        if (request.getNextMaintenanceWindow() != null && 
            request.getNextMaintenanceWindow().isBefore(Instant.now().plus(Duration.ofHours(1)))) {
            
            logger.debug("Adding maintenance instruction for agent: agentId={}, maintenanceWindow={}", 
                    agentInfo.getAgentId(), request.getNextMaintenanceWindow());
            
            AgentHeartbeatResponse.AgentInstruction instruction = 
                new AgentHeartbeatResponse.AgentInstruction("maintenance", "prepare_for_maintenance");
            instruction.setPriority(5);
            instruction.setDeadline(request.getNextMaintenanceWindow());
            response.addInstruction(instruction);
        }
        
        // Add cluster health information
        String clusterHealth = raftNode.isLeader() ? "healthy" : "follower";
        response.setClusterHealth(clusterHealth);
        logger.debug("Set cluster health in response: agentId={}, clusterHealth={}", 
                agentInfo.getAgentId(), clusterHealth);
    }

    /**
     * Handle a failed agent.
     */
    private void handleFailedAgent(AgentInfo agentInfo, Duration timeSinceLastHeartbeat) {
        if (agentInfo.getStatus() != AgentStatus.UNREACHABLE && 
            agentInfo.getStatus() != AgentStatus.FAILED) {
            
            logger.warn("Agent failed: agentId={}, timeSinceLastHeartbeat={}s, previousStatus={}", 
                    agentInfo.getAgentId(), timeSinceLastHeartbeat.toSeconds(), agentInfo.getStatus());
            
            // Update status to unreachable
            agentInfo.setStatus(AgentStatus.UNREACHABLE);
            logger.debug("Agent status changed to UNREACHABLE: agentId={}", agentInfo.getAgentId());
            
            // Submit to distributed state
            if (raftNode.isLeader()) {
                try {
                    AgentCommand command = AgentCommand.updateStatus(
                        agentInfo.getAgentId(), 
                        AgentStatus.UNREACHABLE
                    );
                    logger.debug("Submitting failed agent status to distributed state: agentId={}", agentInfo.getAgentId());
                    raftNode.submitCommand(command);
                } catch (Exception e) {
                    logger.warn("Failed to update failed agent status in distributed state: agentId={}, error={}", 
                            agentInfo.getAgentId(), e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Handle a degraded agent.
     */
    private void handleDegradedAgent(AgentInfo agentInfo, Duration timeSinceLastHeartbeat) {
        if (agentInfo.getStatus() == AgentStatus.HEALTHY || agentInfo.getStatus() == AgentStatus.ACTIVE) {
            logger.info("Agent degraded: agentId={}, timeSinceLastHeartbeat={}s, previousStatus={}", 
                    agentInfo.getAgentId(), timeSinceLastHeartbeat.toSeconds(), agentInfo.getStatus());
            
            agentInfo.setStatus(AgentStatus.DEGRADED);
            logger.debug("Agent status changed to DEGRADED: agentId={}", agentInfo.getAgentId());
        }
    }

    /**
     * Reset sequence number tracking for an agent.
     * This is useful when an agent reconnects after a server restart.
     */
    public void resetAgentSequenceNumber(String agentId) {
        logger.debug("resetAgentSequenceNumber() called: agentId={}", agentId);
        lastSequenceNumbers.remove(agentId);
        logger.info("Reset sequence number tracking for agent: agentId={}", agentId);
    }

    /**
     * Reset sequence number tracking for all agents.
     * This can be called during server startup to handle restart scenarios.
     */
    public void resetAllSequenceNumbers() {
        logger.debug("resetAllSequenceNumbers() called");
        int count = lastSequenceNumbers.size();
        lastSequenceNumbers.clear();
        logger.info("Reset sequence number tracking for {} agents", count);
    }

    /**
     * Get heartbeat statistics.
     */
    public HeartbeatStatistics getHeartbeatStatistics() {
        logger.debug("getHeartbeatStatistics() called");
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
        
        logger.debug("getHeartbeatStatistics() result: total={}, healthy={}, degraded={}, failed={}", 
                totalAgents, healthyAgents, degradedAgents, failedAgents);
        
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
