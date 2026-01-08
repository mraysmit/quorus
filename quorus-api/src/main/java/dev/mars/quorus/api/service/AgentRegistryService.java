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
import dev.mars.quorus.agent.AgentCapabilities;
import dev.mars.quorus.api.dto.AgentRegistrationRequest;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.state.AgentCommand;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Service for managing agent registration and fleet state.
 * This service handles agent lifecycle operations and maintains the distributed agent registry.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-26
 * @version 1.0
 */
@ApplicationScoped
public class AgentRegistryService {

    private static final Logger logger = Logger.getLogger(AgentRegistryService.class.getName());
    
    private static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 30000; // 30 seconds
    private static final long AGENT_TIMEOUT_MS = 90000; // 90 seconds (3 missed heartbeats)

    @Inject
    RaftNode raftNode;

    // Local cache of agent information for fast lookups
    private final ConcurrentHashMap<String, AgentInfo> agentCache = new ConcurrentHashMap<>();

    /**
     * Register a new agent with the fleet.
     * 
     * @param request the registration request
     * @return a future containing the registration result
     */
    public CompletableFuture<AgentInfo> registerAgent(AgentRegistrationRequest request) {
        logger.info("Registering agent: " + request.getAgentId());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate the registration request
                validateRegistrationRequest(request);
                
                // Create agent info from request
                AgentInfo agentInfo = createAgentInfo(request);
                
                // Submit registration command to Raft cluster
                AgentCommand command = AgentCommand.register(agentInfo);
                
                if (raftNode.isLeader()) {
                    // Submit command to distributed state machine
                    CompletableFuture<Object> future = raftNode.submitCommand(command)
                            .toCompletionStage().toCompletableFuture();
                    
                    try {
                        // Wait for command to be committed
                        future.get(5, TimeUnit.SECONDS);
                        
                        // Update local cache
                        agentCache.put(agentInfo.getAgentId(), agentInfo);
                        
                        logger.info("Agent registered successfully: " + agentInfo.getAgentId());
                        return agentInfo;
                        
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Failed to commit agent registration", e);
                        throw new RuntimeException("Failed to register agent: " + e.getMessage(), e);
                    }
                } else {
                    throw new RuntimeException("Not the leader - cannot register agent");
                }
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Agent registration failed", e);
                throw new RuntimeException("Agent registration failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Deregister an agent from the fleet.
     * 
     * @param agentId the agent ID to deregister
     * @return a future indicating completion
     */
    public CompletableFuture<Void> deregisterAgent(String agentId) {
        logger.info("Deregistering agent: " + agentId);
        
        return CompletableFuture.runAsync(() -> {
            try {
                AgentInfo agentInfo = agentCache.get(agentId);
                if (agentInfo == null) {
                    throw new RuntimeException("Agent not found: " + agentId);
                }
                
                // Update agent status to deregistered
                agentInfo.setStatus(AgentStatus.DEREGISTERED);
                
                // Submit deregistration command to Raft cluster
                AgentCommand command = AgentCommand.deregister(agentId);
                
                if (raftNode.isLeader()) {
                    CompletableFuture<Object> future = raftNode.submitCommand(command)
                            .toCompletionStage().toCompletableFuture();
                    
                    try {
                        future.get(5, TimeUnit.SECONDS);
                        
                        // Remove from local cache
                        agentCache.remove(agentId);
                        
                        logger.info("Agent deregistered successfully: " + agentId);
                        
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Failed to commit agent deregistration", e);
                        throw new RuntimeException("Failed to deregister agent: " + e.getMessage(), e);
                    }
                } else {
                    throw new RuntimeException("Not the leader - cannot deregister agent");
                }
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Agent deregistration failed", e);
                throw new RuntimeException("Agent deregistration failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Update agent capabilities.
     * 
     * @param agentId the agent ID
     * @param capabilities the new capabilities
     * @return a future containing the updated agent info
     */
    public CompletableFuture<AgentInfo> updateAgentCapabilities(String agentId, AgentCapabilities capabilities) {
        logger.info("Updating capabilities for agent: " + agentId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                AgentInfo agentInfo = agentCache.get(agentId);
                if (agentInfo == null) {
                    throw new RuntimeException("Agent not found: " + agentId);
                }
                
                // Update capabilities
                agentInfo.setCapabilities(capabilities);
                
                // Submit update command to Raft cluster
                AgentCommand command = AgentCommand.updateCapabilities(agentId, capabilities);
                
                if (raftNode.isLeader()) {
                    CompletableFuture<Object> future = raftNode.submitCommand(command)
                            .toCompletionStage().toCompletableFuture();
                    
                    try {
                        future.get(5, TimeUnit.SECONDS);
                        
                        // Update local cache
                        agentCache.put(agentId, agentInfo);
                        
                        logger.info("Agent capabilities updated successfully: " + agentId);
                        return agentInfo;
                        
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Failed to commit agent capabilities update", e);
                        throw new RuntimeException("Failed to update agent capabilities: " + e.getMessage(), e);
                    }
                } else {
                    throw new RuntimeException("Not the leader - cannot update agent capabilities");
                }
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Agent capabilities update failed", e);
                throw new RuntimeException("Agent capabilities update failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Get agent information by ID.
     * 
     * @param agentId the agent ID
     * @return the agent info, or null if not found
     */
    public AgentInfo getAgent(String agentId) {
        return agentCache.get(agentId);
    }

    /**
     * Get all registered agents.
     * 
     * @return list of all agents
     */
    public List<AgentInfo> getAllAgents() {
        return new ArrayList<>(agentCache.values());
    }

    /**
     * Get healthy agents.
     * 
     * @return list of healthy agents
     */
    public List<AgentInfo> getHealthyAgents() {
        return agentCache.values().stream()
                .filter(AgentInfo::isHealthy)
                .collect(Collectors.toList());
    }

    /**
     * Get available agents for work assignment.
     * 
     * @return list of available agents
     */
    public List<AgentInfo> getAvailableAgents() {
        return agentCache.values().stream()
                .filter(AgentInfo::isAvailable)
                .collect(Collectors.toList());
    }

    /**
     * Get agents by region.
     * 
     * @param region the region
     * @return list of agents in the region
     */
    public List<AgentInfo> getAgentsByRegion(String region) {
        return agentCache.values().stream()
                .filter(agent -> region.equals(agent.getRegion()))
                .collect(Collectors.toList());
    }

    /**
     * Get agents supporting a specific protocol.
     * 
     * @param protocol the protocol
     * @return list of agents supporting the protocol
     */
    public List<AgentInfo> getAgentsByProtocol(String protocol) {
        return agentCache.values().stream()
                .filter(agent -> agent.getCapabilities() != null && 
                               agent.getCapabilities().supportsProtocol(protocol))
                .collect(Collectors.toList());
    }

    /**
     * Get fleet statistics.
     * 
     * @return fleet statistics
     */
    public FleetStatistics getFleetStatistics() {
        List<AgentInfo> allAgents = getAllAgents();
        
        long totalAgents = allAgents.size();
        long healthyAgents = allAgents.stream().filter(AgentInfo::isHealthy).count();
        long availableAgents = allAgents.stream().filter(AgentInfo::isAvailable).count();
        
        Map<AgentStatus, Long> statusCounts = allAgents.stream()
                .collect(Collectors.groupingBy(AgentInfo::getStatus, Collectors.counting()));
        
        Map<String, Long> regionCounts = allAgents.stream()
                .filter(agent -> agent.getRegion() != null)
                .collect(Collectors.groupingBy(AgentInfo::getRegion, Collectors.counting()));
        
        return new FleetStatistics(totalAgents, healthyAgents, availableAgents, statusCounts, regionCounts);
    }

    /**
     * Validate agent registration request.
     * 
     * @param request the registration request
     * @throws IllegalArgumentException if validation fails
     */
    private void validateRegistrationRequest(AgentRegistrationRequest request) {
        if (request.getAgentId() == null || request.getAgentId().trim().isEmpty()) {
            throw new IllegalArgumentException("Agent ID is required");
        }
        
        if (request.getHostname() == null || request.getHostname().trim().isEmpty()) {
            throw new IllegalArgumentException("Hostname is required");
        }
        
        if (request.getAddress() == null || request.getAddress().trim().isEmpty()) {
            throw new IllegalArgumentException("Address is required");
        }
        
        if (request.getPort() <= 0 || request.getPort() > 65535) {
            throw new IllegalArgumentException("Invalid port number");
        }
        
        if (request.getCapabilities() == null) {
            throw new IllegalArgumentException("Capabilities are required");
        }
        
        // Check for duplicate agent ID
        if (agentCache.containsKey(request.getAgentId())) {
            throw new IllegalArgumentException("Agent ID already exists: " + request.getAgentId());
        }
    }

    /**
     * Create agent info from registration request.
     * 
     * @param request the registration request
     * @return the agent info
     */
    private AgentInfo createAgentInfo(AgentRegistrationRequest request) {
        AgentInfo agentInfo = new AgentInfo(
            request.getAgentId(),
            request.getHostname(),
            request.getAddress(),
            request.getPort()
        );
        
        agentInfo.setCapabilities(request.getCapabilities());
        agentInfo.setVersion(request.getVersion());
        agentInfo.setRegion(request.getRegion());
        agentInfo.setDatacenter(request.getDatacenter());
        agentInfo.setMetadata(request.getMetadata());
        agentInfo.setStatus(AgentStatus.HEALTHY); // Start as healthy
        agentInfo.setRegistrationTime(Instant.now());
        agentInfo.setLastHeartbeat(Instant.now());
        
        return agentInfo;
    }

    /**
     * Fleet statistics data class.
     */
    public static class FleetStatistics {
        private final long totalAgents;
        private final long healthyAgents;
        private final long availableAgents;
        private final Map<AgentStatus, Long> statusCounts;
        private final Map<String, Long> regionCounts;

        public FleetStatistics(long totalAgents, long healthyAgents, long availableAgents,
                             Map<AgentStatus, Long> statusCounts, Map<String, Long> regionCounts) {
            this.totalAgents = totalAgents;
            this.healthyAgents = healthyAgents;
            this.availableAgents = availableAgents;
            this.statusCounts = statusCounts;
            this.regionCounts = regionCounts;
        }

        public long getTotalAgents() { return totalAgents; }
        public long getHealthyAgents() { return healthyAgents; }
        public long getAvailableAgents() { return availableAgents; }
        public Map<AgentStatus, Long> getStatusCounts() { return statusCounts; }
        public Map<String, Long> getRegionCounts() { return regionCounts; }
    }
}
