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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Agent Discovery Example - Demonstrates Quorus agent discovery and fleet management.
 * 
 * This example shows how to:
 * - Register agents with the fleet
 * - Discover agents by region, status, and capabilities
 * - Monitor agent health and availability
 * - Query fleet statistics
 * - Filter agents for job assignment
 * 
 * In production, this would connect to the Quorus API service which maintains
 * a distributed agent registry using Raft consensus.
 * 
 * Run with: mvn exec:java -Dexec.mainClass="dev.mars.quorus.examples.AgentDiscoveryExample" -pl quorus-integration-examples
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-01-08
 * @version 1.0
 */
public class AgentDiscoveryExample {
    
    private static final ExampleLogger log = ExampleLogger.getLogger(AgentDiscoveryExample.class);
    
    // Simulated agent registry (in production, this would be the distributed Raft-based registry)
    private final Map<String, AgentInfo> agentRegistry = new ConcurrentHashMap<>();
    
    public static void main(String[] args) {
        log.exampleStart("Agent Discovery Example");
        log.detail("Demonstrating agent registration, discovery, and fleet management.");
        
        try {
            AgentDiscoveryExample example = new AgentDiscoveryExample();
            example.runExample();
            log.exampleComplete("Agent Discovery Example");
        } catch (Exception e) {
            log.unexpectedError("Agent Discovery Example", e);
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public void runExample() throws Exception {
        log.testSection("1. Registering agents with the fleet", false);
        registerAgents();
        
        log.testSection("2. Discovering agents by region", false);
        discoverByRegion();
        
        log.testSection("3. Filtering agents by status", false);
        filterByStatus();
        
        log.testSection("4. Finding agents by capabilities", false);
        findByCapabilities();
        
        log.testSection("5. Fleet statistics and health", false);
        displayFleetStatistics();
        
        log.testSection("6. Agent health monitoring", false);
        demonstrateHealthMonitoring();
        
        log.testSection("7. Finding best agent for job", false);
        findBestAgentForJob();
    }
    
    private int stepCounter = 1;
    
    private void registerAgents() {
        log.step(stepCounter++, "Simulating agent registration across multiple regions...");
        
        // Register agents in US East region
        registerAgent("agent-nyc-001", "nyc-agent-01.corp.com", "10.1.1.10", 8080,
                "us-east-1", "nyc-dc1", createHighCapabilityAgent());
        registerAgent("agent-nyc-002", "nyc-agent-02.corp.com", "10.1.1.11", 8080,
                "us-east-1", "nyc-dc1", createStandardCapabilityAgent());
        registerAgent("agent-nyc-003", "nyc-agent-03.corp.com", "10.1.1.12", 8080,
                "us-east-1", "nyc-dc2", createStandardCapabilityAgent());
        
        // Register agents in US West region
        registerAgent("agent-sfo-001", "sfo-agent-01.corp.com", "10.2.1.10", 8080,
                "us-west-2", "sfo-dc1", createHighCapabilityAgent());
        registerAgent("agent-sfo-002", "sfo-agent-02.corp.com", "10.2.1.11", 8080,
                "us-west-2", "sfo-dc1", createStandardCapabilityAgent());
        
        // Register agents in EU region
        registerAgent("agent-lon-001", "lon-agent-01.corp.com", "10.3.1.10", 8080,
                "eu-west-1", "lon-dc1", createHighCapabilityAgent());
        registerAgent("agent-fra-001", "fra-agent-01.corp.com", "10.3.2.10", 8080,
                "eu-central-1", "fra-dc1", createStandardCapabilityAgent());
        
        // Register agents in APAC region
        registerAgent("agent-tok-001", "tok-agent-01.corp.com", "10.4.1.10", 8080,
                "ap-northeast-1", "tok-dc1", createStandardCapabilityAgent());
        
        log.section("Agent Registration Summary");
        log.indentedKeyValue("Total agents registered", String.valueOf(agentRegistry.size()));
        log.indentedKeyValue("Regions covered", String.valueOf(getUniqueRegions().size()));
        log.indentedKeyValue("Datacenters", String.valueOf(getUniqueDatacenters().size()));
    }
    
    private void registerAgent(String agentId, String hostname, String address, int port,
                               String region, String datacenter, AgentCapabilities capabilities) {
        AgentInfo agent = new AgentInfo(agentId, hostname, address, port);
        agent.setRegion(region);
        agent.setDatacenter(datacenter);
        agent.setCapabilities(capabilities);
        agent.setStatus(AgentStatus.HEALTHY);
        agent.setVersion("1.0.0");
        agent.setLastHeartbeat(Instant.now());
        
        agentRegistry.put(agentId, agent);
        log.expectedSuccess("Registered: " + agentId + " in " + region + "/" + datacenter);
    }
    
    private void discoverByRegion() {
        log.step(stepCounter++, "Discovering agents by region...");
        
        // US East region
        List<AgentInfo> usEastAgents = findAgentsByRegion("us-east-1");
        log.section("US East Region (us-east-1)");
        log.indentedKeyValue("Agent count", String.valueOf(usEastAgents.size()));
        usEastAgents.forEach(agent -> 
            log.bullet(agent.getAgentId() + " @ " + agent.getDatacenter()));
        
        // US West region
        List<AgentInfo> usWestAgents = findAgentsByRegion("us-west-2");
        log.section("US West Region (us-west-2)");
        log.indentedKeyValue("Agent count", String.valueOf(usWestAgents.size()));
        usWestAgents.forEach(agent -> 
            log.bullet(agent.getAgentId() + " @ " + agent.getDatacenter()));
        
        // EU regions
        List<AgentInfo> euAgents = agentRegistry.values().stream()
                .filter(a -> a.getRegion() != null && a.getRegion().startsWith("eu-"))
                .collect(Collectors.toList());
        log.section("EU Regions");
        log.indentedKeyValue("Agent count", String.valueOf(euAgents.size()));
        euAgents.forEach(agent -> 
            log.bullet(agent.getAgentId() + " in " + agent.getRegion()));
        
        // All regions summary
        log.section("All Regions");
        getUniqueRegions().forEach(region -> {
            long count = agentRegistry.values().stream()
                    .filter(a -> region.equals(a.getRegion()))
                    .count();
            log.indentedKeyValue(region, count + " agents");
        });
    }
    
    private void filterByStatus() {
        log.step(stepCounter++, "Filtering agents by operational status...");
        
        // Simulate some agents in different states
        agentRegistry.get("agent-nyc-002").setStatus(AgentStatus.ACTIVE);
        agentRegistry.get("agent-sfo-002").setStatus(AgentStatus.DEGRADED);
        agentRegistry.get("agent-fra-001").setStatus(AgentStatus.MAINTENANCE);
        
        // Filter healthy agents
        List<AgentInfo> healthyAgents = findAgentsByStatus(AgentStatus.HEALTHY);
        log.section("Healthy Agents");
        log.indentedKeyValue("Count", String.valueOf(healthyAgents.size()));
        healthyAgents.forEach(agent -> log.bullet(agent.getAgentId()));
        
        // Filter active agents
        List<AgentInfo> activeAgents = findAgentsByStatus(AgentStatus.ACTIVE);
        log.section("Active Agents (processing jobs)");
        log.indentedKeyValue("Count", String.valueOf(activeAgents.size()));
        activeAgents.forEach(agent -> log.bullet(agent.getAgentId()));
        
        // Filter degraded agents
        List<AgentInfo> degradedAgents = findAgentsByStatus(AgentStatus.DEGRADED);
        log.section("Degraded Agents");
        log.indentedKeyValue("Count", String.valueOf(degradedAgents.size()));
        degradedAgents.forEach(agent -> {
            log.warning(agent.getAgentId() + " - performance issues detected");
        });
        
        // Filter agents available for work
        List<AgentInfo> availableAgents = agentRegistry.values().stream()
                .filter(a -> a.getStatus().isAvailableForWork())
                .collect(Collectors.toList());
        log.section("Agents Available for Work");
        log.indentedKeyValue("Total available", String.valueOf(availableAgents.size()));
    }
    
    private void findByCapabilities() {
        log.step(stepCounter++, "Finding agents by capability requirements...");
        
        // Find agents supporting SFTP
        List<AgentInfo> sftpAgents = findAgentsByProtocol("SFTP");
        log.section("Agents with SFTP Support");
        log.indentedKeyValue("Count", String.valueOf(sftpAgents.size()));
        sftpAgents.forEach(agent -> 
            log.bullet(agent.getAgentId() + " - Protocols: " + 
                    agent.getCapabilities().getSupportedProtocols()));
        
        // Find high-capacity agents (max concurrent > 15)
        List<AgentInfo> highCapacityAgents = agentRegistry.values().stream()
                .filter(a -> a.getCapabilities().getMaxConcurrentTransfers() > 15)
                .collect(Collectors.toList());
        log.section("High-Capacity Agents (>15 concurrent transfers)");
        log.indentedKeyValue("Count", String.valueOf(highCapacityAgents.size()));
        highCapacityAgents.forEach(agent -> 
            log.bullet(agent.getAgentId() + " - Max: " + 
                    agent.getCapabilities().getMaxConcurrentTransfers() + " transfers"));
        
        // Find agents with encryption support
        List<AgentInfo> encryptionAgents = agentRegistry.values().stream()
                .filter(a -> !a.getCapabilities().getSupportedEncryptionTypes().isEmpty())
                .collect(Collectors.toList());
        log.section("Agents with Encryption Support");
        log.indentedKeyValue("Count", String.valueOf(encryptionAgents.size()));
        encryptionAgents.forEach(agent -> 
            log.bullet(agent.getAgentId() + " - Encryption: " + 
                    agent.getCapabilities().getSupportedEncryptionTypes()));
    }
    
    private void displayFleetStatistics() {
        log.step(stepCounter++, "Calculating fleet statistics...");
        
        long totalAgents = agentRegistry.size();
        long healthyCount = agentRegistry.values().stream()
                .filter(a -> a.getStatus() == AgentStatus.HEALTHY || a.getStatus() == AgentStatus.ACTIVE)
                .count();
        long availableCount = agentRegistry.values().stream()
                .filter(a -> a.getStatus().isAvailableForWork())
                .count();
        long degradedCount = agentRegistry.values().stream()
                .filter(a -> a.getStatus() == AgentStatus.DEGRADED)
                .count();
        
        log.section("Fleet Statistics");
        log.indentedKeyValue("Total agents", String.valueOf(totalAgents));
        log.indentedKeyValue("Healthy agents", String.valueOf(healthyCount));
        log.indentedKeyValue("Available for work", String.valueOf(availableCount));
        log.indentedKeyValue("Degraded", String.valueOf(degradedCount));
        log.indentedKeyValue("Health percentage", 
                String.format("%.1f%%", (healthyCount * 100.0) / totalAgents));
        
        // Status breakdown
        log.section("Status Breakdown");
        Map<AgentStatus, Long> statusCounts = agentRegistry.values().stream()
                .collect(Collectors.groupingBy(AgentInfo::getStatus, Collectors.counting()));
        statusCounts.forEach((status, count) -> 
            log.indentedKeyValue(status.toString(), count + " agents"));
        
        // Regional breakdown
        log.section("Regional Distribution");
        Map<String, Long> regionCounts = agentRegistry.values().stream()
                .collect(Collectors.groupingBy(AgentInfo::getRegion, Collectors.counting()));
        regionCounts.forEach((region, count) -> 
            log.indentedKeyValue(region, count + " agents"));
        
        // Capacity summary
        int totalCapacity = agentRegistry.values().stream()
                .mapToInt(a -> a.getCapabilities().getMaxConcurrentTransfers())
                .sum();
        log.section("Capacity Summary");
        log.indentedKeyValue("Total concurrent transfer capacity", String.valueOf(totalCapacity));
        log.indentedKeyValue("Average per agent", 
                String.format("%.1f", totalCapacity / (double) totalAgents));
    }
    
    private void demonstrateHealthMonitoring() {
        log.step(stepCounter++, "Demonstrating health monitoring...");
        
        // Simulate heartbeat check
        Instant now = Instant.now();
        long heartbeatThreshold = 90000; // 90 seconds
        
        log.section("Heartbeat Status");
        agentRegistry.values().forEach(agent -> {
            long millisSinceHeartbeat = now.toEpochMilli() - agent.getLastHeartbeat().toEpochMilli();
            String status = millisSinceHeartbeat < heartbeatThreshold ? "OK" : "STALE";
            log.bullet(String.format("%s: %s (last heartbeat %dms ago)", 
                    agent.getAgentId(), status, millisSinceHeartbeat));
        });
        
        // Simulate detecting an unhealthy agent
        log.section("Simulating Unhealthy Agent Detection");
        AgentInfo unhealthyAgent = agentRegistry.get("agent-tok-001");
        unhealthyAgent.setLastHeartbeat(Instant.now().minusSeconds(120)); // 2 minutes ago
        unhealthyAgent.setStatus(AgentStatus.UNREACHABLE);
        
        log.warning("Agent " + unhealthyAgent.getAgentId() + " marked as UNREACHABLE");
        log.detail("Last heartbeat: 120 seconds ago (threshold: 90s)");
        log.detail("Action: Agent will be excluded from job assignment");
        
        // Show recovery simulation
        log.section("Simulating Agent Recovery");
        unhealthyAgent.setLastHeartbeat(Instant.now());
        unhealthyAgent.setStatus(AgentStatus.HEALTHY);
        log.success("Agent " + unhealthyAgent.getAgentId() + " recovered and marked HEALTHY");
    }
    
    private void findBestAgentForJob() {
        log.step(stepCounter++, "Finding best agent for a sample job...");
        
        // Sample job requirements
        String requiredProtocol = "SFTP";
        String preferredRegion = "us-east-1";
        int minConcurrentCapacity = 5;
        
        log.section("Job Requirements");
        log.indentedKeyValue("Protocol", requiredProtocol);
        log.indentedKeyValue("Preferred region", preferredRegion);
        log.indentedKeyValue("Min concurrent capacity", String.valueOf(minConcurrentCapacity));
        
        // Find matching agents
        List<AgentInfo> matchingAgents = agentRegistry.values().stream()
                .filter(a -> a.getStatus().isAvailableForWork())
                .filter(a -> a.getCapabilities().getSupportedProtocols().contains(requiredProtocol))
                .filter(a -> a.getCapabilities().getMaxConcurrentTransfers() >= minConcurrentCapacity)
                .sorted((a1, a2) -> {
                    // Prefer agents in the target region
                    boolean a1InRegion = preferredRegion.equals(a1.getRegion());
                    boolean a2InRegion = preferredRegion.equals(a2.getRegion());
                    if (a1InRegion != a2InRegion) {
                        return a1InRegion ? -1 : 1;
                    }
                    // Then prefer higher capacity
                    return Integer.compare(
                            a2.getCapabilities().getMaxConcurrentTransfers(),
                            a1.getCapabilities().getMaxConcurrentTransfers());
                })
                .collect(Collectors.toList());
        
        log.section("Matching Agents (ranked)");
        for (int i = 0; i < matchingAgents.size(); i++) {
            AgentInfo agent = matchingAgents.get(i);
            String regionMatch = preferredRegion.equals(agent.getRegion()) ? "✓" : "✗";
            log.numberedItem(i + 1, String.format("%s [Region: %s %s, Capacity: %d]",
                    agent.getAgentId(), agent.getRegion(), regionMatch,
                    agent.getCapabilities().getMaxConcurrentTransfers()));
        }
        
        if (!matchingAgents.isEmpty()) {
            AgentInfo selectedAgent = matchingAgents.get(0);
            log.section("Selected Agent");
            log.success("Best match: " + selectedAgent.getAgentId());
            log.indentedKeyValue("Region", selectedAgent.getRegion());
            log.indentedKeyValue("Datacenter", selectedAgent.getDatacenter());
            log.indentedKeyValue("Address", selectedAgent.getAddress() + ":" + selectedAgent.getPort());
            log.indentedKeyValue("Max concurrent transfers", 
                    String.valueOf(selectedAgent.getCapabilities().getMaxConcurrentTransfers()));
        } else {
            log.warning("No agents match the job requirements");
        }
    }
    
    // Helper methods
    
    private List<AgentInfo> findAgentsByRegion(String region) {
        return agentRegistry.values().stream()
                .filter(a -> region.equals(a.getRegion()))
                .collect(Collectors.toList());
    }
    
    private List<AgentInfo> findAgentsByStatus(AgentStatus status) {
        return agentRegistry.values().stream()
                .filter(a -> a.getStatus() == status)
                .collect(Collectors.toList());
    }
    
    private List<AgentInfo> findAgentsByProtocol(String protocol) {
        return agentRegistry.values().stream()
                .filter(a -> a.getCapabilities().getSupportedProtocols().contains(protocol))
                .collect(Collectors.toList());
    }
    
    private Set<String> getUniqueRegions() {
        return agentRegistry.values().stream()
                .map(AgentInfo::getRegion)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
    
    private Set<String> getUniqueDatacenters() {
        return agentRegistry.values().stream()
                .map(AgentInfo::getDatacenter)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
    
    private AgentCapabilities createHighCapabilityAgent() {
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setSupportedProtocols(new HashSet<>(
                Arrays.asList("HTTP", "HTTPS", "FTP", "SFTP", "SMB", "S3")));
        capabilities.setMaxConcurrentTransfers(20);
        capabilities.setMaxTransferSize(10L * 1024 * 1024 * 1024); // 10 GB
        capabilities.setMaxBandwidth(1024L * 1024 * 1024); // 1 GB/s
        capabilities.setSupportedCompressionTypes(new HashSet<>(Arrays.asList("gzip", "lz4", "zstd")));
        capabilities.setSupportedEncryptionTypes(new HashSet<>(Arrays.asList("AES-256", "TLS-1.3")));
        return capabilities;
    }
    
    private AgentCapabilities createStandardCapabilityAgent() {
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setSupportedProtocols(new HashSet<>(
                Arrays.asList("HTTP", "HTTPS", "SFTP")));
        capabilities.setMaxConcurrentTransfers(10);
        capabilities.setMaxTransferSize(5L * 1024 * 1024 * 1024); // 5 GB
        capabilities.setMaxBandwidth(500L * 1024 * 1024); // 500 MB/s
        capabilities.setSupportedCompressionTypes(new HashSet<>(Arrays.asList("gzip")));
        capabilities.setSupportedEncryptionTypes(new HashSet<>(Arrays.asList("AES-256")));
        return capabilities;
    }
}
