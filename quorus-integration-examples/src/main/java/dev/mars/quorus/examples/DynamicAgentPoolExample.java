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

package dev.mars.quorus.examples;

import dev.mars.quorus.agent.AgentCapabilities;
import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.examples.util.ExampleLogger;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Dynamic Agent Pool Example - Demonstrates intelligent agent pool management and selection strategies.
 * 
 * This example shows how to:
 * - Implement different agent selection strategies (Round Robin, Least Loaded, etc.)
 * - Manage agent pools with dynamic scaling
 * - Handle agent failures and failover
 * - Balance workloads across distributed agents
 * - Monitor pool health and performance
 * 
 * In production, this would integrate with the AgentSelectionService which uses
 * Raft consensus for distributed agent coordination.
 * 
 * Run with: mvn exec:java -Dexec.mainClass="dev.mars.quorus.examples.DynamicAgentPoolExample" -pl quorus-integration-examples
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-01-08
 * @version 1.0
 */
public class DynamicAgentPoolExample {
    
    private static final ExampleLogger log = ExampleLogger.getLogger(DynamicAgentPoolExample.class);
    
    // Simulated agent pool
    private final Map<String, AgentInfo> agentPool = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> agentLoadCounters = new ConcurrentHashMap<>();
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);
    
    // Selection strategies
    public enum SelectionStrategy {
        ROUND_ROBIN,
        LEAST_LOADED,
        CAPABILITY_BASED,
        LOCALITY_AWARE,
        WEIGHTED_SCORE,
        PREFERRED_AGENT
    }
    
    private int stepCounter = 1;
    
    public static void main(String[] args) {
        log.exampleStart("Dynamic Agent Pool Example");
        log.detail("Demonstrating intelligent agent pool management and selection strategies.");
        
        try {
            DynamicAgentPoolExample example = new DynamicAgentPoolExample();
            example.runExample();
            log.exampleComplete("Dynamic Agent Pool Example");
        } catch (Exception e) {
            log.unexpectedError("Dynamic Agent Pool Example", e);
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public void runExample() throws Exception {
        log.testSection("1. Initializing agent pool", false);
        initializeAgentPool();
        
        log.testSection("2. Round Robin selection strategy", false);
        demonstrateRoundRobin();
        
        log.testSection("3. Least Loaded selection strategy", false);
        demonstrateLeastLoaded();
        
        log.testSection("4. Capability-based selection", false);
        demonstrateCapabilityBasedSelection();
        
        log.testSection("5. Locality-aware selection", false);
        demonstrateLocalityAwareSelection();
        
        log.testSection("6. Weighted score selection", false);
        demonstrateWeightedScoreSelection();
        
        log.testSection("7. Dynamic pool scaling", false);
        demonstrateDynamicScaling();
        
        log.testSection("8. Failover and recovery", false);
        demonstrateFailover();
        
        log.testSection("9. Load balancing simulation", false);
        simulateLoadBalancing();
    }
    
    private void initializeAgentPool() {
        log.step(stepCounter++, "Creating initial agent pool...");
        
        // Create a diverse pool of agents
        addAgentToPool("pool-agent-001", "us-east-1", "nyc-dc1", 20, AgentStatus.HEALTHY);
        addAgentToPool("pool-agent-002", "us-east-1", "nyc-dc2", 15, AgentStatus.HEALTHY);
        addAgentToPool("pool-agent-003", "us-west-2", "sfo-dc1", 25, AgentStatus.HEALTHY);
        addAgentToPool("pool-agent-004", "us-west-2", "sfo-dc2", 10, AgentStatus.HEALTHY);
        addAgentToPool("pool-agent-005", "eu-west-1", "lon-dc1", 20, AgentStatus.HEALTHY);
        addAgentToPool("pool-agent-006", "eu-central-1", "fra-dc1", 15, AgentStatus.HEALTHY);
        addAgentToPool("pool-agent-007", "ap-northeast-1", "tok-dc1", 12, AgentStatus.HEALTHY);
        
        log.section("Pool Statistics");
        log.indentedKeyValue("Total agents", String.valueOf(agentPool.size()));
        log.indentedKeyValue("Regions", String.valueOf(getUniqueRegions().size()));
        log.indentedKeyValue("Total capacity", calculateTotalCapacity() + " concurrent transfers");
        
        displayPoolStatus();
    }
    
    private void addAgentToPool(String agentId, String region, String datacenter, 
                                 int maxConcurrent, AgentStatus status) {
        AgentInfo agent = new AgentInfo(agentId, agentId + ".corp.com", 
                "10." + agentPool.size() + ".1.10", 8080);
        agent.setRegion(region);
        agent.setDatacenter(datacenter);
        agent.setStatus(status);
        agent.setLastHeartbeat(Instant.now());
        
        AgentCapabilities caps = new AgentCapabilities();
        caps.setSupportedProtocols(new HashSet<>(Arrays.asList("HTTP", "HTTPS", "SFTP", "S3")));
        caps.setMaxConcurrentTransfers(maxConcurrent);
        caps.setMaxTransferSize(10L * 1024 * 1024 * 1024);
        caps.setMaxBandwidth(1024L * 1024 * 1024);
        caps.setSupportedCompressionTypes(new HashSet<>(Arrays.asList("gzip", "lz4")));
        caps.setSupportedEncryptionTypes(new HashSet<>(Arrays.asList("AES-256", "TLS-1.3")));
        agent.setCapabilities(caps);
        
        agentPool.put(agentId, agent);
        agentLoadCounters.put(agentId, new AtomicInteger(0));
        
        log.bullet("Added: " + agentId + " [" + region + "/" + datacenter + "] capacity=" + maxConcurrent);
    }
    
    private void demonstrateRoundRobin() {
        log.step(stepCounter++, "Demonstrating Round Robin selection...");
        
        List<AgentInfo> availableAgents = getAvailableAgents();
        log.indentedKeyValue("Available agents", String.valueOf(availableAgents.size()));
        
        log.section("Selecting 10 agents in sequence");
        for (int i = 1; i <= 10; i++) {
            AgentInfo selected = selectRoundRobin(availableAgents);
            log.numberedItem(i, "Selected: " + selected.getAgentId() + 
                    " [" + selected.getRegion() + "]");
        }
        
        log.detail("Round Robin ensures even distribution regardless of agent capacity");
    }
    
    private AgentInfo selectRoundRobin(List<AgentInfo> agents) {
        if (agents.isEmpty()) return null;
        int index = roundRobinCounter.getAndIncrement() % agents.size();
        return agents.get(index);
    }
    
    private void demonstrateLeastLoaded() {
        log.step(stepCounter++, "Demonstrating Least Loaded selection...");
        
        // Simulate some load on agents
        simulateLoad("pool-agent-001", 15);
        simulateLoad("pool-agent-002", 8);
        simulateLoad("pool-agent-003", 5);
        simulateLoad("pool-agent-004", 2);
        simulateLoad("pool-agent-005", 10);
        
        log.section("Current Agent Loads");
        displayAgentLoads();
        
        log.section("Selecting 5 agents (Least Loaded)");
        for (int i = 1; i <= 5; i++) {
            AgentInfo selected = selectLeastLoaded();
            if (selected != null) {
                int currentLoad = agentLoadCounters.get(selected.getAgentId()).get();
                int maxCapacity = selected.getCapabilities().getMaxConcurrentTransfers();
                int utilization = (currentLoad * 100) / maxCapacity;
                
                // Increment load for selected agent
                agentLoadCounters.get(selected.getAgentId()).incrementAndGet();
                
                log.numberedItem(i, String.format("Selected: %s (Load: %d/%d = %d%%)",
                        selected.getAgentId(), currentLoad, maxCapacity, utilization));
            }
        }
        
        log.section("Updated Agent Loads");
        displayAgentLoads();
    }
    
    private AgentInfo selectLeastLoaded() {
        return getAvailableAgents().stream()
                .min(Comparator.comparingDouble(this::calculateLoadPercentage))
                .orElse(null);
    }
    
    private double calculateLoadPercentage(AgentInfo agent) {
        int currentLoad = agentLoadCounters.getOrDefault(agent.getAgentId(), new AtomicInteger(0)).get();
        int maxCapacity = agent.getCapabilities().getMaxConcurrentTransfers();
        return (currentLoad * 100.0) / maxCapacity;
    }
    
    private void demonstrateCapabilityBasedSelection() {
        log.step(stepCounter++, "Demonstrating Capability-based selection...");
        
        // Add a specialized agent with limited protocols
        AgentInfo specializedAgent = agentPool.get("pool-agent-007");
        AgentCapabilities caps = specializedAgent.getCapabilities();
        caps.setSupportedProtocols(new HashSet<>(Arrays.asList("S3", "AZURE_BLOB", "GCS")));
        caps.setMaxConcurrentTransfers(50);
        caps.setMaxTransferSize(100L * 1024 * 1024 * 1024);
        
        log.section("Capability Profile: pool-agent-007 (Cloud Specialist)");
        log.indentedKeyValue("Protocols", String.join(", ", caps.getSupportedProtocols()));
        log.indentedKeyValue("Max concurrent", String.valueOf(caps.getMaxConcurrentTransfers()));
        
        // Select for SFTP job
        log.section("Job: SFTP Transfer");
        AgentInfo sftpAgent = selectByCapability("SFTP");
        if (sftpAgent != null) {
            log.success("Selected: " + sftpAgent.getAgentId());
        }
        
        // Select for S3 job
        log.section("Job: S3 Transfer");
        AgentInfo s3Agent = selectByCapability("S3");
        if (s3Agent != null) {
            log.success("Selected: " + s3Agent.getAgentId() + 
                    " (supports S3, capacity: " + 
                    s3Agent.getCapabilities().getMaxConcurrentTransfers() + ")");
        }
        
        // Select for Azure Blob job
        log.section("Job: Azure Blob Transfer");
        AgentInfo azureAgent = selectByCapability("AZURE_BLOB");
        if (azureAgent != null) {
            log.success("Selected: " + azureAgent.getAgentId());
        } else {
            log.warning("No agent supports Azure Blob");
        }
    }
    
    private AgentInfo selectByCapability(String requiredProtocol) {
        return getAvailableAgents().stream()
                .filter(a -> a.getCapabilities().getSupportedProtocols().contains(requiredProtocol))
                .max(Comparator.comparingInt(a -> a.getCapabilities().getMaxConcurrentTransfers()))
                .orElse(null);
    }
    
    private void demonstrateLocalityAwareSelection() {
        log.step(stepCounter++, "Demonstrating Locality-aware selection...");
        
        // Simulate requests from different regions
        String[] sourceRegions = {"us-east-1", "us-west-2", "eu-west-1", "ap-northeast-1"};
        
        for (String sourceRegion : sourceRegions) {
            log.section("Request from: " + sourceRegion);
            
            // First try same region
            List<AgentInfo> sameRegionAgents = getAvailableAgents().stream()
                    .filter(a -> sourceRegion.equals(a.getRegion()))
                    .collect(Collectors.toList());
            
            if (!sameRegionAgents.isEmpty()) {
                AgentInfo selected = sameRegionAgents.get(0);
                log.expectedSuccess("Same region match: " + selected.getAgentId() + 
                        " [" + selected.getRegion() + "/" + selected.getDatacenter() + "]");
            } else {
                // Fallback to nearest region (simplified)
                AgentInfo fallback = selectLeastLoaded();
                if (fallback != null) {
                    log.warning("No local agent, fallback: " + fallback.getAgentId() + 
                            " [" + fallback.getRegion() + "]");
                }
            }
        }
        
        log.section("Locality Selection Statistics");
        Map<String, Long> regionCounts = agentPool.values().stream()
                .collect(Collectors.groupingBy(AgentInfo::getRegion, Collectors.counting()));
        regionCounts.forEach((region, count) -> 
                log.indentedKeyValue(region, count + " agents"));
    }
    
    private void demonstrateWeightedScoreSelection() {
        log.step(stepCounter++, "Demonstrating Weighted Score selection...");
        
        log.section("Scoring Factors");
        log.indentedKeyValue("Load weight", "40% (lower is better)");
        log.indentedKeyValue("Capacity weight", "30% (higher is better)");
        log.indentedKeyValue("Health weight", "20% (HEALTHY=100%, DEGRADED=50%)");
        log.indentedKeyValue("Locality weight", "10% (same region bonus)");
        
        // Simulate scoring for a request from us-east-1
        String requestRegion = "us-east-1";
        log.section("Scoring agents for request from: " + requestRegion);
        
        List<ScoredAgent> scoredAgents = new ArrayList<>();
        for (AgentInfo agent : getAvailableAgents()) {
            double score = calculateWeightedScore(agent, requestRegion);
            scoredAgents.add(new ScoredAgent(agent, score));
        }
        
        scoredAgents.sort((a, b) -> Double.compare(b.score, a.score));
        
        log.detail("Agent scores (higher is better):");
        for (int i = 0; i < Math.min(5, scoredAgents.size()); i++) {
            ScoredAgent sa = scoredAgents.get(i);
            log.numberedItem(i + 1, String.format("%s: %.2f [%s]",
                    sa.agent.getAgentId(), sa.score, sa.agent.getRegion()));
        }
        
        if (!scoredAgents.isEmpty()) {
            log.success("Best agent: " + scoredAgents.get(0).agent.getAgentId());
        }
    }
    
    private double calculateWeightedScore(AgentInfo agent, String requestRegion) {
        // Load score (lower load = higher score)
        double loadPct = calculateLoadPercentage(agent);
        double loadScore = (100 - loadPct) * 0.40;
        
        // Capacity score (normalized to 0-100)
        int maxCapacity = agent.getCapabilities().getMaxConcurrentTransfers();
        double capacityScore = Math.min(maxCapacity * 4, 100) * 0.30; // 25 = max score
        
        // Health score
        double healthScore;
        switch (agent.getStatus()) {
            case HEALTHY:
            case ACTIVE:
                healthScore = 100 * 0.20;
                break;
            case IDLE:
                healthScore = 90 * 0.20;
                break;
            case DEGRADED:
                healthScore = 50 * 0.20;
                break;
            default:
                healthScore = 0;
        }
        
        // Locality score
        double localityScore = requestRegion.equals(agent.getRegion()) ? 100 * 0.10 : 0;
        
        return loadScore + capacityScore + healthScore + localityScore;
    }
    
    private void demonstrateDynamicScaling() {
        log.step(stepCounter++, "Demonstrating dynamic pool scaling...");
        
        // Show current pool
        log.section("Current Pool Size: " + agentPool.size() + " agents");
        
        // Simulate scale-up
        log.section("Simulating Scale-Up (high demand detected)");
        log.detail("Average load: " + calculateAverageLoad() + "%");
        
        // Add new agents
        addAgentToPool("pool-agent-008", "us-east-1", "nyc-dc1", 15, AgentStatus.REGISTERING);
        addAgentToPool("pool-agent-009", "us-west-2", "sfo-dc1", 15, AgentStatus.REGISTERING);
        
        log.detail("Added 2 new agents (REGISTERING status)");
        
        // Simulate registration completion
        agentPool.get("pool-agent-008").setStatus(AgentStatus.HEALTHY);
        agentPool.get("pool-agent-009").setStatus(AgentStatus.HEALTHY);
        log.success("New agents now HEALTHY and available");
        
        log.section("Updated Pool Size: " + agentPool.size() + " agents");
        log.indentedKeyValue("New total capacity", calculateTotalCapacity() + " concurrent transfers");
        
        // Simulate scale-down
        log.section("Simulating Scale-Down (low demand detected)");
        
        // Mark agent for draining
        AgentInfo drainAgent = agentPool.get("pool-agent-009");
        drainAgent.setStatus(AgentStatus.DRAINING);
        log.detail("Agent pool-agent-009 marked as DRAINING");
        log.detail("Agent will be removed after completing current jobs");
        
        // Simulate drain completion
        drainAgent.setStatus(AgentStatus.MAINTENANCE);
        log.detail("Drain complete, agent in MAINTENANCE mode");
        
        // Remove from pool
        agentPool.remove("pool-agent-009");
        agentLoadCounters.remove("pool-agent-009");
        log.success("Agent pool-agent-009 removed from pool");
        
        log.section("Final Pool Size: " + agentPool.size() + " agents");
    }
    
    private void demonstrateFailover() {
        log.step(stepCounter++, "Demonstrating failover and recovery...");
        
        // Simulate agent failure
        log.section("Simulating Agent Failure");
        AgentInfo failingAgent = agentPool.get("pool-agent-003");
        String failedAgentId = failingAgent.getAgentId();
        String failedRegion = failingAgent.getRegion();
        
        log.warning("Agent " + failedAgentId + " detected as UNREACHABLE");
        failingAgent.setStatus(AgentStatus.UNREACHABLE);
        
        // Show impact
        int activeJobs = agentLoadCounters.get(failedAgentId).get();
        log.indentedKeyValue("Jobs affected", String.valueOf(activeJobs));
        
        // Find failover targets
        log.section("Finding Failover Targets");
        List<AgentInfo> failoverCandidates = getAvailableAgents().stream()
                .filter(a -> !a.getAgentId().equals(failedAgentId))
                .sorted(Comparator.comparing(a -> !a.getRegion().equals(failedRegion)))
                .collect(Collectors.toList());
        
        log.detail("Available failover candidates: " + failoverCandidates.size());
        
        // Redistribute jobs
        log.section("Redistributing Jobs");
        int jobsToRedistribute = activeJobs;
        int redistributed = 0;
        
        for (AgentInfo candidate : failoverCandidates) {
            if (jobsToRedistribute <= 0) break;
            
            int availableCapacity = candidate.getCapabilities().getMaxConcurrentTransfers() 
                    - agentLoadCounters.get(candidate.getAgentId()).get();
            
            if (availableCapacity > 0) {
                int toAssign = Math.min(availableCapacity, jobsToRedistribute);
                agentLoadCounters.get(candidate.getAgentId()).addAndGet(toAssign);
                jobsToRedistribute -= toAssign;
                redistributed += toAssign;
                
                log.bullet(candidate.getAgentId() + ": +" + toAssign + " jobs");
            }
        }
        
        // Clear failed agent's load
        agentLoadCounters.get(failedAgentId).set(0);
        
        log.success("Redistributed " + redistributed + " jobs to healthy agents");
        
        // Simulate recovery
        log.section("Simulating Agent Recovery");
        failingAgent.setStatus(AgentStatus.HEALTHY);
        failingAgent.setLastHeartbeat(Instant.now());
        log.expectedSuccess("Agent " + failedAgentId + " recovered and rejoined pool");
    }
    
    private void simulateLoadBalancing() {
        log.step(stepCounter++, "Simulating load balancing with 50 job requests...");
        
        // Reset loads
        agentLoadCounters.values().forEach(c -> c.set(0));
        
        // Simulate 50 job assignments using weighted scoring
        String requestRegion = "us-east-1";
        Map<String, Integer> selectionCounts = new HashMap<>();
        
        for (int i = 0; i < 50; i++) {
            // Use weighted score selection
            AgentInfo selected = getAvailableAgents().stream()
                    .max(Comparator.comparingDouble(a -> calculateWeightedScore(a, requestRegion)))
                    .orElse(null);
            
            if (selected != null) {
                agentLoadCounters.get(selected.getAgentId()).incrementAndGet();
                selectionCounts.merge(selected.getAgentId(), 1, Integer::sum);
            }
        }
        
        log.section("Load Distribution After 50 Jobs");
        displayAgentLoads();
        
        log.section("Selection Distribution");
        selectionCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> {
                    AgentInfo agent = agentPool.get(e.getKey());
                    log.indentedKeyValue(e.getKey() + " [" + agent.getRegion() + "]", 
                            e.getValue() + " selections");
                });
        
        // Calculate balance metrics
        log.section("Balance Metrics");
        double avgLoad = calculateAverageLoad();
        double maxLoad = agentPool.values().stream()
                .mapToDouble(this::calculateLoadPercentage)
                .max().orElse(0);
        double minLoad = agentPool.values().stream()
                .filter(a -> a.getStatus().isAvailableForWork())
                .mapToDouble(this::calculateLoadPercentage)
                .min().orElse(0);
        
        log.indentedKeyValue("Average load", String.format("%.1f%%", avgLoad));
        log.indentedKeyValue("Max load", String.format("%.1f%%", maxLoad));
        log.indentedKeyValue("Min load", String.format("%.1f%%", minLoad));
        log.indentedKeyValue("Load variance", String.format("%.1f%%", maxLoad - minLoad));
        
        if (maxLoad - minLoad < 30) {
            log.success("Good load distribution (variance < 30%)");
        } else {
            log.warning("High load variance detected");
        }
    }
    
    // Helper methods
    
    private List<AgentInfo> getAvailableAgents() {
        return agentPool.values().stream()
                .filter(a -> a.getStatus().isAvailableForWork())
                .collect(Collectors.toList());
    }
    
    private Set<String> getUniqueRegions() {
        return agentPool.values().stream()
                .map(AgentInfo::getRegion)
                .collect(Collectors.toSet());
    }
    
    private int calculateTotalCapacity() {
        return agentPool.values().stream()
                .filter(a -> a.getStatus().isAvailableForWork())
                .mapToInt(a -> a.getCapabilities().getMaxConcurrentTransfers())
                .sum();
    }
    
    private double calculateAverageLoad() {
        return agentPool.values().stream()
                .filter(a -> a.getStatus().isAvailableForWork())
                .mapToDouble(this::calculateLoadPercentage)
                .average()
                .orElse(0);
    }
    
    private void simulateLoad(String agentId, int load) {
        agentLoadCounters.get(agentId).set(load);
    }
    
    private void displayPoolStatus() {
        log.detail("Agent pool status:");
        agentPool.values().stream()
                .sorted(Comparator.comparing(AgentInfo::getRegion))
                .forEach(agent -> {
                    log.bullet(String.format("%s [%s/%s] - %s (capacity: %d)",
                            agent.getAgentId(),
                            agent.getRegion(),
                            agent.getDatacenter(),
                            agent.getStatus(),
                            agent.getCapabilities().getMaxConcurrentTransfers()));
                });
    }
    
    private void displayAgentLoads() {
        agentPool.values().stream()
                .filter(a -> a.getStatus().isAvailableForWork())
                .sorted(Comparator.comparingDouble(this::calculateLoadPercentage))
                .forEach(agent -> {
                    int load = agentLoadCounters.get(agent.getAgentId()).get();
                    int max = agent.getCapabilities().getMaxConcurrentTransfers();
                    int pct = (load * 100) / max;
                    String bar = createLoadBar(pct);
                    log.detail(String.format("%-18s %s %3d%% (%d/%d)",
                            agent.getAgentId(), bar, pct, load, max));
                });
    }
    
    private String createLoadBar(int percentage) {
        int filled = percentage / 10;
        int empty = 10 - filled;
        return "[" + "█".repeat(filled) + "░".repeat(empty) + "]";
    }
    
    // Helper class for scored selection
    private static class ScoredAgent {
        final AgentInfo agent;
        final double score;
        
        ScoredAgent(AgentInfo agent, double score) {
            this.agent = agent;
            this.score = score;
        }
    }
}
