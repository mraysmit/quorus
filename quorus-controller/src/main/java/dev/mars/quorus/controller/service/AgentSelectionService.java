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

package dev.mars.quorus.controller.service;

import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.core.AgentLoad;
import dev.mars.quorus.core.JobRequirements;
import dev.mars.quorus.core.QueuedJob;
import dev.mars.quorus.core.TransferRequest;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Service responsible for intelligent agent selection for job assignments.
 * Implements multiple selection strategies optimized for distributed file transfer scenarios.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-10-28
 * @version 1.0
 */
public class AgentSelectionService {
    
    private static final Logger logger = Logger.getLogger(AgentSelectionService.class.getName());
    
    // Round-robin state tracking
    private final Map<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();
    
    // Agent performance tracking for weighted scoring
    private final Map<String, AgentPerformanceMetrics> performanceMetrics = new ConcurrentHashMap<>();
    
    /**
     * Select the best agent for a queued job based on its requirements.
     * 
     * @param job the queued job needing assignment
     * @param availableAgents map of available agents by ID
     * @param agentLoads map of current agent loads by ID
     * @return the selected agent ID, or null if no suitable agent found
     */
    public String selectAgent(QueuedJob job, Map<String, AgentInfo> availableAgents, Map<String, AgentLoad> agentLoads) {
        if (availableAgents.isEmpty()) {
            logger.warning("No available agents for job assignment: " + job.getJobId());
            return null;
        }
        
        JobRequirements requirements = job.getRequirements();
        if (requirements == null) {
            logger.warning("Job has no requirements, using default selection: " + job.getJobId());
            return selectByLeastLoaded(availableAgents, agentLoads);
        }
        
        // Filter agents based on basic eligibility
        List<AgentInfo> eligibleAgents = filterEligibleAgents(job, availableAgents.values(), agentLoads);
        
        if (eligibleAgents.isEmpty()) {
            logger.warning("No eligible agents found for job: " + job.getJobId());
            return null;
        }
        
        // Apply selection strategy
        String selectedAgentId = applySelectionStrategy(job, eligibleAgents, agentLoads, requirements.getSelectionStrategy());
        
        if (selectedAgentId != null) {
            logger.info("Selected agent " + selectedAgentId + " for job " + job.getJobId() + 
                       " using strategy " + requirements.getSelectionStrategy());
        }
        
        return selectedAgentId;
    }
    
    /**
     * Filter agents based on basic eligibility criteria.
     */
    private List<AgentInfo> filterEligibleAgents(QueuedJob job, Collection<AgentInfo> agents, Map<String, AgentLoad> agentLoads) {
        JobRequirements requirements = job.getRequirements();
        TransferRequest request = job.getTransferJob().getRequest();
        
        return agents.stream()
                .filter(agent -> isAgentEligible(agent, requirements, request, agentLoads.get(agent.getAgentId())))
                .collect(Collectors.toList());
    }
    
    /**
     * Check if an agent is eligible for a job assignment.
     */
    private boolean isAgentEligible(AgentInfo agent, JobRequirements requirements, TransferRequest request, AgentLoad load) {
        // Check agent status - must be available for work
        if (!agent.getStatus().isAvailableForWork()) {
            return false;
        }
        
        // Check if agent is explicitly excluded
        if (requirements.isAgentExcluded(agent.getAgentId())) {
            return false;
        }
        
        // Check if agent is overloaded
        if (load != null && load.getCurrentJobs() >= load.getMaxConcurrentJobs()) {
            return false;
        }
        
        // Check protocol compatibility
        String sourceProtocol = extractProtocol(request.getSourceUri());
        String destProtocol = extractProtocolFromPath(request.getDestinationPath());

        // Check source protocol support
        if (!agent.getCapabilities().supportsProtocol(sourceProtocol)) {
            return false;
        }

        // Check destination protocol support (file protocol is always supported for local writes)
        if (!"file".equals(destProtocol) && !agent.getCapabilities().supportsProtocol(destProtocol)) {
            return false;
        }
        
        // Check transfer size limits
        if (!agent.getCapabilities().canHandleTransferSize(request.getExpectedSize())) {
            return false;
        }
        
        // Check bandwidth requirements
        if (requirements.getMinBandwidth() > 0 && 
            agent.getCapabilities().getMaxBandwidth() < requirements.getMinBandwidth()) {
            return false;
        }
        
        // Check encryption requirements
        if (requirements.isRequiresEncryption() && 
            agent.getCapabilities().getSupportedEncryptionTypes().isEmpty()) {
            return false;
        }
        
        // Check compression requirements
        if (requirements.isRequiresCompression() && 
            agent.getCapabilities().getSupportedCompressionTypes().isEmpty()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Apply the specified selection strategy to choose from eligible agents.
     */
    private String applySelectionStrategy(QueuedJob job, List<AgentInfo> eligibleAgents, 
                                        Map<String, AgentLoad> agentLoads, 
                                        JobRequirements.SelectionStrategy strategy) {
        switch (strategy) {
            case ROUND_ROBIN:
                return selectByRoundRobin(eligibleAgents, job.getRequirements().getTenantId());
                
            case LEAST_LOADED:
                return selectByLeastLoaded(eligibleAgents, agentLoads);
                
            case CAPABILITY_BASED:
                return selectByCapabilityMatch(job, eligibleAgents);
                
            case LOCALITY_AWARE:
                return selectByLocality(job, eligibleAgents);
                
            case WEIGHTED_SCORE:
                return selectByWeightedScore(job, eligibleAgents, agentLoads);
                
            case PREFERRED_AGENT:
                return selectByPreference(job, eligibleAgents);
                
            default:
                logger.warning("Unknown selection strategy: " + strategy + ", falling back to LEAST_LOADED");
                return selectByLeastLoaded(eligibleAgents, agentLoads);
        }
    }
    
    /**
     * Extract protocol from URI (e.g., "https" from "https://example.com/file").
     */
    private String extractProtocol(URI uri) {
        return uri.getScheme() != null ? uri.getScheme().toLowerCase() : "file";
    }

    /**
     * Extract protocol from Path. For local paths, assume "file" protocol.
     * For paths that look like URIs, extract the scheme.
     */
    private String extractProtocolFromPath(Path path) {
        String pathStr = path.toString();
        if (pathStr.contains("://")) {
            // Path contains URI-like scheme
            int schemeEnd = pathStr.indexOf("://");
            return pathStr.substring(0, schemeEnd).toLowerCase();
        }
        return "file"; // Default to file protocol for local paths
    }
    
    /**
     * Round-robin selection within tenant scope.
     */
    private String selectByRoundRobin(List<AgentInfo> agents, String tenantId) {
        String key = tenantId != null ? tenantId : "default";
        AtomicInteger counter = roundRobinCounters.computeIfAbsent(key, k -> new AtomicInteger(0));
        
        int index = counter.getAndIncrement() % agents.size();
        return agents.get(index).getAgentId();
    }
    
    /**
     * Select agent with lowest current load.
     */
    private String selectByLeastLoaded(Collection<AgentInfo> agents, Map<String, AgentLoad> agentLoads) {
        return agents.stream()
                .min((a1, a2) -> {
                    AgentLoad load1 = agentLoads.get(a1.getAgentId());
                    AgentLoad load2 = agentLoads.get(a2.getAgentId());
                    
                    double score1 = load1 != null ? load1.getLoadScore() : 0.0;
                    double score2 = load2 != null ? load2.getLoadScore() : 0.0;
                    
                    return Double.compare(score1, score2);
                })
                .map(AgentInfo::getAgentId)
                .orElse(null);
    }
    
    /**
     * Select agent with best capability match.
     */
    private String selectByCapabilityMatch(QueuedJob job, List<AgentInfo> agents) {
        JobRequirements requirements = job.getRequirements();
        TransferRequest request = job.getTransferJob().getRequest();
        
        String sourceProtocol = extractProtocol(request.getSourceUri());
        
        return agents.stream()
                .max((a1, a2) -> {
                    double score1 = a1.getCapabilities().calculateCompatibilityScore(
                            sourceProtocol, request.getExpectedSize(), requirements.getTargetRegion());
                    double score2 = a2.getCapabilities().calculateCompatibilityScore(
                            sourceProtocol, request.getExpectedSize(), requirements.getTargetRegion());
                    return Double.compare(score1, score2);
                })
                .map(AgentInfo::getAgentId)
                .orElse(null);
    }
    
    /**
     * Select agent based on geographic locality.
     */
    private String selectByLocality(QueuedJob job, List<AgentInfo> agents) {
        String targetRegion = job.getRequirements().getTargetRegion();
        
        // First try agents in target region
        if (targetRegion != null) {
            Optional<AgentInfo> regionAgent = agents.stream()
                    .filter(agent -> targetRegion.equals(agent.getRegion()))
                    .findFirst();
            if (regionAgent.isPresent()) {
                return regionAgent.get().getAgentId();
            }
        }
        
        // Then try preferred regions
        for (String preferredRegion : job.getRequirements().getPreferredRegions()) {
            Optional<AgentInfo> regionAgent = agents.stream()
                    .filter(agent -> preferredRegion.equals(agent.getRegion()))
                    .findFirst();
            if (regionAgent.isPresent()) {
                return regionAgent.get().getAgentId();
            }
        }
        
        // Fall back to any available agent
        return agents.isEmpty() ? null : agents.get(0).getAgentId();
    }
    
    /**
     * Select agent using weighted scoring algorithm.
     */
    private String selectByWeightedScore(QueuedJob job, List<AgentInfo> agents, Map<String, AgentLoad> agentLoads) {
        return agents.stream()
                .max((a1, a2) -> {
                    double score1 = calculateWeightedScore(job, a1, agentLoads.get(a1.getAgentId()));
                    double score2 = calculateWeightedScore(job, a2, agentLoads.get(a2.getAgentId()));
                    return Double.compare(score1, score2);
                })
                .map(AgentInfo::getAgentId)
                .orElse(null);
    }
    
    /**
     * Select preferred agent if available.
     */
    private String selectByPreference(QueuedJob job, List<AgentInfo> agents) {
        // Try preferred agents first
        for (String preferredAgentId : job.getRequirements().getPreferredAgents()) {
            Optional<AgentInfo> preferredAgent = agents.stream()
                    .filter(agent -> preferredAgentId.equals(agent.getAgentId()))
                    .findFirst();
            if (preferredAgent.isPresent()) {
                return preferredAgentId;
            }
        }
        
        // Fall back to least loaded
        return selectByLeastLoaded(agents, Collections.emptyMap());
    }
    
    /**
     * Calculate weighted score for an agent considering multiple factors.
     */
    private double calculateWeightedScore(QueuedJob job, AgentInfo agent, AgentLoad load) {
        double capabilityWeight = 0.4;
        double loadWeight = 0.3;
        double priorityWeight = 0.2;
        double localityWeight = 0.1;
        
        // Capability score
        JobRequirements requirements = job.getRequirements();
        TransferRequest request = job.getTransferJob().getRequest();
        String sourceProtocol = extractProtocol(request.getSourceUri());
        double capabilityScore = agent.getCapabilities().calculateCompatibilityScore(
                sourceProtocol, request.getExpectedSize(), requirements.getTargetRegion());
        
        // Load score (inverted - lower load is better)
        double loadScore = load != null ? (1.0 - load.getLoadScore()) : 1.0;
        
        // Priority score based on agent status
        double priorityScore = agent.getStatus().getJobAssignmentPriority() / 10.0;
        
        // Locality score
        double localityScore = calculateLocalityScore(job, agent);
        
        return (capabilityScore * capabilityWeight) +
               (loadScore * loadWeight) +
               (priorityScore * priorityWeight) +
               (localityScore * localityWeight);
    }
    
    /**
     * Calculate locality score based on region matching.
     */
    private double calculateLocalityScore(QueuedJob job, AgentInfo agent) {
        String targetRegion = job.getRequirements().getTargetRegion();
        String agentRegion = agent.getRegion();
        
        if (targetRegion != null && targetRegion.equals(agentRegion)) {
            return 1.0; // Perfect match
        }
        
        if (job.getRequirements().getPreferredRegions().contains(agentRegion)) {
            return 0.8; // Good match
        }
        
        return 0.5; // Neutral
    }
    
    /**
     * Helper method for backward compatibility.
     */
    private String selectByLeastLoaded(Map<String, AgentInfo> availableAgents, Map<String, AgentLoad> agentLoads) {
        return selectByLeastLoaded(availableAgents.values(), agentLoads);
    }
    
    /**
     * Performance metrics for agents (for future enhancements).
     */
    private static class AgentPerformanceMetrics {
        private double averageTransferSpeed;
        private double successRate;
        private long totalTransfers;
        
        // Constructor and methods would be implemented for performance tracking
    }
}
