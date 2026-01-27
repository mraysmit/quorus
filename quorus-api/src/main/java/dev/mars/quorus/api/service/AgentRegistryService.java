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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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

    private static final Logger logger = LoggerFactory.getLogger(AgentRegistryService.class);
    
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
        logger.debug("registerAgent() called: agentId={}, hostname={}, address={}:{}", 
                request.getAgentId(), request.getHostname(), request.getAddress(), request.getPort());
        logger.info("Registering agent: {}", request.getAgentId());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate the registration request
                logger.debug("Validating registration request for agent: {}", request.getAgentId());
                validateRegistrationRequest(request);
                logger.debug("Registration request validated successfully for agent: {}", request.getAgentId());
                
                // Create agent info from request
                logger.debug("Creating AgentInfo from registration request");
                AgentInfo agentInfo = createAgentInfo(request);
                logger.debug("AgentInfo created: agentId={}, status={}", agentInfo.getAgentId(), agentInfo.getStatus());
                
                // Submit registration command to Raft cluster
                AgentCommand command = AgentCommand.register(agentInfo);
                logger.debug("Created AgentCommand.register for agent: {}", agentInfo.getAgentId());
                
                if (raftNode.isLeader()) {
                    logger.debug("This node is leader, submitting registration command to Raft cluster");
                    // Submit command to distributed state machine
                    CompletableFuture<Object> future = raftNode.submitCommand(command)
                            .toCompletionStage().toCompletableFuture();
                    
                    try {
                        // Wait for command to be committed
                        logger.debug("Waiting for registration command to be committed: agentId={}", agentInfo.getAgentId());
                        future.get(5, TimeUnit.SECONDS);
                        logger.debug("Registration command committed successfully");
                        
                        // Update local cache
                        agentCache.put(agentInfo.getAgentId(), agentInfo);
                        logger.debug("Agent added to local cache: agentId={}, cacheSize={}", 
                                agentInfo.getAgentId(), agentCache.size());
                        
                        logger.info("Agent registered successfully: agentId={}, region={}, version={}", 
                                agentInfo.getAgentId(), agentInfo.getRegion(), agentInfo.getVersion());
                        return agentInfo;
                        
                    } catch (Exception e) {
                        logger.error("Failed to commit agent registration: agentId={}", agentInfo.getAgentId(), e);
                        throw new RuntimeException("Failed to register agent: " + e.getMessage(), e);
                    }
                } else {
                    logger.warn("Cannot register agent - this node is not the leader: agentId={}", request.getAgentId());
                    throw new RuntimeException("Not the leader - cannot register agent");
                }
                
            } catch (Exception e) {
                logger.error("Agent registration failed: agentId={}", request.getAgentId(), e);
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
        logger.debug("deregisterAgent() called: agentId={}", agentId);
        logger.info("Deregistering agent: {}", agentId);
        
        return CompletableFuture.runAsync(() -> {
            try {
                AgentInfo agentInfo = agentCache.get(agentId);
                if (agentInfo == null) {
                    logger.warn("Agent not found in cache for deregistration: agentId={}", agentId);
                    throw new RuntimeException("Agent not found: " + agentId);
                }
                logger.debug("Found agent in cache: agentId={}, currentStatus={}", agentId, agentInfo.getStatus());
                
                // Update agent status to deregistered
                AgentStatus previousStatus = agentInfo.getStatus();
                agentInfo.setStatus(AgentStatus.DEREGISTERED);
                logger.debug("Agent status changed: agentId={}, previousStatus={}, newStatus=DEREGISTERED", 
                        agentId, previousStatus);
                
                // Submit deregistration command to Raft cluster
                AgentCommand command = AgentCommand.deregister(agentId);
                logger.debug("Created AgentCommand.deregister for agent: {}", agentId);
                
                if (raftNode.isLeader()) {
                    logger.debug("This node is leader, submitting deregistration command to Raft cluster");
                    CompletableFuture<Object> future = raftNode.submitCommand(command)
                            .toCompletionStage().toCompletableFuture();
                    
                    try {
                        logger.debug("Waiting for deregistration command to be committed: agentId={}", agentId);
                        future.get(5, TimeUnit.SECONDS);
                        logger.debug("Deregistration command committed successfully");
                        
                        // Remove from local cache
                        agentCache.remove(agentId);
                        logger.debug("Agent removed from local cache: agentId={}, cacheSize={}", agentId, agentCache.size());
                        
                        logger.info("Agent deregistered successfully: agentId={}", agentId);
                        
                    } catch (Exception e) {
                        logger.error("Failed to commit agent deregistration: agentId={}", agentId, e);
                        throw new RuntimeException("Failed to deregister agent: " + e.getMessage(), e);
                    }
                } else {
                    logger.warn("Cannot deregister agent - this node is not the leader: agentId={}", agentId);
                    throw new RuntimeException("Not the leader - cannot deregister agent");
                }
                
            } catch (Exception e) {
                logger.error("Agent deregistration failed: agentId={}", agentId, e);
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
        logger.debug("updateAgentCapabilities() called: agentId={}", agentId);
        logger.info("Updating capabilities for agent: {}", agentId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                AgentInfo agentInfo = agentCache.get(agentId);
                if (agentInfo == null) {
                    logger.warn("Agent not found in cache for capabilities update: agentId={}", agentId);
                    throw new RuntimeException("Agent not found: " + agentId);
                }
                logger.debug("Found agent in cache: agentId={}, currentStatus={}", agentId, agentInfo.getStatus());
                
                // Update capabilities
                logger.debug("Updating capabilities for agent: agentId={}, protocols={}", 
                        agentId, capabilities.getSupportedProtocols());
                agentInfo.setCapabilities(capabilities);
                
                // Submit update command to Raft cluster
                AgentCommand command = AgentCommand.updateCapabilities(agentId, capabilities);
                logger.debug("Created AgentCommand.updateCapabilities for agent: {}", agentId);
                
                if (raftNode.isLeader()) {
                    logger.debug("This node is leader, submitting capabilities update command to Raft cluster");
                    CompletableFuture<Object> future = raftNode.submitCommand(command)
                            .toCompletionStage().toCompletableFuture();
                    
                    try {
                        logger.debug("Waiting for capabilities update command to be committed: agentId={}", agentId);
                        future.get(5, TimeUnit.SECONDS);
                        logger.debug("Capabilities update command committed successfully");
                        
                        // Update local cache
                        agentCache.put(agentId, agentInfo);
                        logger.debug("Agent capabilities updated in local cache: agentId={}", agentId);
                        
                        logger.info("Agent capabilities updated successfully: agentId={}", agentId);
                        return agentInfo;
                        
                    } catch (Exception e) {
                        logger.error("Failed to commit agent capabilities update: agentId={}", agentId, e);
                        throw new RuntimeException("Failed to update agent capabilities: " + e.getMessage(), e);
                    }
                } else {
                    logger.warn("Cannot update agent capabilities - this node is not the leader: agentId={}", agentId);
                    throw new RuntimeException("Not the leader - cannot update agent capabilities");
                }
                
            } catch (Exception e) {
                logger.error("Agent capabilities update failed: agentId={}", agentId, e);
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
        logger.trace("getAgent() called: agentId={}", agentId);
        AgentInfo agent = agentCache.get(agentId);
        logger.trace("getAgent() result: agentId={}, found={}", agentId, agent != null);
        return agent;
    }

    /**
     * Get all registered agents.
     * 
     * @return list of all agents
     */
    public List<AgentInfo> getAllAgents() {
        logger.trace("getAllAgents() called");
        List<AgentInfo> agents = new ArrayList<>(agentCache.values());
        logger.trace("getAllAgents() returning {} agents", agents.size());
        return agents;
    }

    /**
     * Get healthy agents.
     * 
     * @return list of healthy agents
     */
    public List<AgentInfo> getHealthyAgents() {
        logger.debug("getHealthyAgents() called");
        List<AgentInfo> healthyAgents = agentCache.values().stream()
                .filter(AgentInfo::isHealthy)
                .collect(Collectors.toList());
        logger.debug("getHealthyAgents() returning {} healthy agents out of {} total", 
                healthyAgents.size(), agentCache.size());
        return healthyAgents;
    }

    /**
     * Get available agents for work assignment.
     * 
     * @return list of available agents
     */
    public List<AgentInfo> getAvailableAgents() {
        logger.debug("getAvailableAgents() called");
        List<AgentInfo> availableAgents = agentCache.values().stream()
                .filter(AgentInfo::isAvailable)
                .collect(Collectors.toList());
        logger.debug("getAvailableAgents() returning {} available agents out of {} total", 
                availableAgents.size(), agentCache.size());
        return availableAgents;
    }

    /**
     * Get agents by region.
     * 
     * @param region the region
     * @return list of agents in the region
     */
    public List<AgentInfo> getAgentsByRegion(String region) {
        logger.debug("getAgentsByRegion() called: region={}", region);
        List<AgentInfo> regionAgents = agentCache.values().stream()
                .filter(agent -> region.equals(agent.getRegion()))
                .collect(Collectors.toList());
        logger.debug("getAgentsByRegion() returning {} agents in region '{}'", regionAgents.size(), region);
        return regionAgents;
    }

    /**
     * Get agents supporting a specific protocol.
     * 
     * @param protocol the protocol
     * @return list of agents supporting the protocol
     */
    public List<AgentInfo> getAgentsByProtocol(String protocol) {
        logger.debug("getAgentsByProtocol() called: protocol={}", protocol);
        List<AgentInfo> protocolAgents = agentCache.values().stream()
                .filter(agent -> agent.getCapabilities() != null && 
                               agent.getCapabilities().supportsProtocol(protocol))
                .collect(Collectors.toList());
        logger.debug("getAgentsByProtocol() returning {} agents supporting protocol '{}'", 
                protocolAgents.size(), protocol);
        return protocolAgents;
    }

    /**
     * Get fleet statistics.
     * 
     * @return fleet statistics
     */
    public FleetStatistics getFleetStatistics() {
        logger.debug("getFleetStatistics() called");
        List<AgentInfo> allAgents = getAllAgents();
        
        long totalAgents = allAgents.size();
        long healthyAgents = allAgents.stream().filter(AgentInfo::isHealthy).count();
        long availableAgents = allAgents.stream().filter(AgentInfo::isAvailable).count();
        
        Map<AgentStatus, Long> statusCounts = allAgents.stream()
                .collect(Collectors.groupingBy(AgentInfo::getStatus, Collectors.counting()));
        
        Map<String, Long> regionCounts = allAgents.stream()
                .filter(agent -> agent.getRegion() != null)
                .collect(Collectors.groupingBy(AgentInfo::getRegion, Collectors.counting()));
        
        logger.debug("getFleetStatistics() result: total={}, healthy={}, available={}, regions={}", 
                totalAgents, healthyAgents, availableAgents, regionCounts.keySet());
        
        return new FleetStatistics(totalAgents, healthyAgents, availableAgents, statusCounts, regionCounts);
    }

    /**
     * Validate agent registration request.
     * 
     * @param request the registration request
     * @throws IllegalArgumentException if validation fails
     */
    private void validateRegistrationRequest(AgentRegistrationRequest request) {
        logger.trace("validateRegistrationRequest() called: agentId={}", request.getAgentId());
        
        if (request.getAgentId() == null || request.getAgentId().trim().isEmpty()) {
            logger.debug("Validation failed: Agent ID is required");
            throw new IllegalArgumentException("Agent ID is required");
        }
        
        if (request.getHostname() == null || request.getHostname().trim().isEmpty()) {
            logger.debug("Validation failed: Hostname is required for agentId={}", request.getAgentId());
            throw new IllegalArgumentException("Hostname is required");
        }
        
        if (request.getAddress() == null || request.getAddress().trim().isEmpty()) {
            logger.debug("Validation failed: Address is required for agentId={}", request.getAgentId());
            throw new IllegalArgumentException("Address is required");
        }
        
        if (request.getPort() <= 0 || request.getPort() > 65535) {
            logger.debug("Validation failed: Invalid port number {} for agentId={}", 
                    request.getPort(), request.getAgentId());
            throw new IllegalArgumentException("Invalid port number");
        }
        
        if (request.getCapabilities() == null) {
            logger.debug("Validation failed: Capabilities are required for agentId={}", request.getAgentId());
            throw new IllegalArgumentException("Capabilities are required");
        }
        
        // Check for duplicate agent ID
        if (agentCache.containsKey(request.getAgentId())) {
            logger.debug("Validation failed: Duplicate agent ID: {}", request.getAgentId());
            throw new IllegalArgumentException("Agent ID already exists: " + request.getAgentId());
        }
        
        logger.trace("validateRegistrationRequest() passed for agentId={}", request.getAgentId());
    }

    /**
     * Create agent info from registration request.
     * 
     * @param request the registration request
     * @return the agent info
     */
    private AgentInfo createAgentInfo(AgentRegistrationRequest request) {
        logger.trace("createAgentInfo() called: agentId={}", request.getAgentId());
        
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
        
        logger.debug("AgentInfo created: agentId={}, hostname={}, address={}:{}, region={}, version={}", 
                agentInfo.getAgentId(), agentInfo.getHostname(), agentInfo.getAddress(), 
                agentInfo.getPort(), agentInfo.getRegion(), agentInfo.getVersion());
        
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
