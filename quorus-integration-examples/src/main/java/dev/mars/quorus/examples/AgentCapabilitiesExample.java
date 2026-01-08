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

import java.util.*;

/**
 * Agent Capabilities Example - Demonstrates Quorus agent capability configuration and matching.
 * 
 * This example shows how to:
 * - Configure agent capabilities (protocols, limits, regions)
 * - Define transfer size and bandwidth constraints
 * - Set up compression and encryption support
 * - Match agents to job requirements based on capabilities
 * - Validate capability compatibility
 * 
 * Agent capabilities are key to intelligent job routing in Quorus, ensuring
 * that file transfers are assigned to agents that can handle them efficiently.
 * 
 * Run with: mvn exec:java -Dexec.mainClass="dev.mars.quorus.examples.AgentCapabilitiesExample" -pl quorus-integration-examples
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-01-08
 * @version 1.0
 */
public class AgentCapabilitiesExample {
    
    private static final ExampleLogger log = ExampleLogger.getLogger(AgentCapabilitiesExample.class);
    
    // Sample agents with different capability profiles
    private final List<AgentInfo> agents = new ArrayList<>();
    private int stepCounter = 1;
    
    public static void main(String[] args) {
        log.exampleStart("Agent Capabilities Example");
        log.detail("Demonstrating agent capability configuration, matching, and validation.");
        
        try {
            AgentCapabilitiesExample example = new AgentCapabilitiesExample();
            example.runExample();
            log.exampleComplete("Agent Capabilities Example");
        } catch (Exception e) {
            log.unexpectedError("Agent Capabilities Example", e);
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public void runExample() throws Exception {
        log.testSection("1. Creating agents with different capability profiles", false);
        createAgentsWithDifferentProfiles();
        
        log.testSection("2. Protocol capability matching", false);
        demonstrateProtocolMatching();
        
        log.testSection("3. Transfer size and bandwidth constraints", false);
        demonstrateSizeAndBandwidthConstraints();
        
        log.testSection("4. Compression and encryption capabilities", false);
        demonstrateCompressionAndEncryption();
        
        log.testSection("5. Region and locality capabilities", false);
        demonstrateRegionCapabilities();
        
        log.testSection("6. Custom capability extensions", false);
        demonstrateCustomCapabilities();
        
        log.testSection("7. Capability-based agent selection", false);
        demonstrateCapabilityBasedSelection();
        
        log.testSection("8. Capability validation", false);
        demonstrateCapabilityValidation();
    }
    
    private void createAgentsWithDifferentProfiles() {
        log.step(stepCounter++, "Creating agents with different capability profiles...");
        
        // Enterprise-grade agent
        log.section("Enterprise Agent Profile");
        AgentInfo enterpriseAgent = createEnterpriseAgent();
        agents.add(enterpriseAgent);
        displayAgentCapabilities(enterpriseAgent);
        
        // Standard office agent
        log.section("Standard Office Agent Profile");
        AgentInfo standardAgent = createStandardAgent();
        agents.add(standardAgent);
        displayAgentCapabilities(standardAgent);
        
        // Edge/remote agent
        log.section("Edge/Remote Agent Profile");
        AgentInfo edgeAgent = createEdgeAgent();
        agents.add(edgeAgent);
        displayAgentCapabilities(edgeAgent);
        
        // Cloud-native agent
        log.section("Cloud-Native Agent Profile");
        AgentInfo cloudAgent = createCloudAgent();
        agents.add(cloudAgent);
        displayAgentCapabilities(cloudAgent);
        
        log.success("Created " + agents.size() + " agents with different capability profiles");
    }
    
    private void demonstrateProtocolMatching() {
        log.step(stepCounter++, "Demonstrating protocol capability matching...");
        
        // Define transfer requirements
        String[] protocols = {"SFTP", "S3", "AZURE_BLOB", "SMB"};
        
        for (String protocol : protocols) {
            log.section("Finding agents for protocol: " + protocol);
            
            List<AgentInfo> matchingAgents = new ArrayList<>();
            for (AgentInfo agent : agents) {
                if (agent.getCapabilities().getSupportedProtocols().contains(protocol)) {
                    matchingAgents.add(agent);
                }
            }
            
            if (matchingAgents.isEmpty()) {
                log.warning("No agents support " + protocol);
            } else {
                log.indentedKeyValue("Matching agents", String.valueOf(matchingAgents.size()));
                for (AgentInfo agent : matchingAgents) {
                    log.bullet(agent.getAgentId() + " - All protocols: " + 
                            agent.getCapabilities().getSupportedProtocols());
                }
            }
        }
        
        // Multi-protocol requirement
        log.section("Multi-Protocol Requirement (SFTP + S3)");
        List<AgentInfo> multiProtocolAgents = new ArrayList<>();
        for (AgentInfo agent : agents) {
            Set<String> protocols_set = agent.getCapabilities().getSupportedProtocols();
            if (protocols_set.contains("SFTP") && protocols_set.contains("S3")) {
                multiProtocolAgents.add(agent);
            }
        }
        log.indentedKeyValue("Agents supporting both SFTP and S3", 
                String.valueOf(multiProtocolAgents.size()));
        multiProtocolAgents.forEach(a -> log.bullet(a.getAgentId()));
    }
    
    private void demonstrateSizeAndBandwidthConstraints() {
        log.step(stepCounter++, "Demonstrating transfer size and bandwidth constraints...");
        
        // Small file transfer
        long smallFile = 100L * 1024 * 1024; // 100 MB
        log.section("Small File Transfer (100 MB)");
        checkTransferCapability(smallFile);
        
        // Large file transfer
        long largeFile = 8L * 1024 * 1024 * 1024; // 8 GB
        log.section("Large File Transfer (8 GB)");
        checkTransferCapability(largeFile);
        
        // Very large file transfer
        long veryLargeFile = 50L * 1024 * 1024 * 1024; // 50 GB
        log.section("Very Large File Transfer (50 GB)");
        checkTransferCapability(veryLargeFile);
        
        // Bandwidth requirements
        log.section("Bandwidth Analysis");
        for (AgentInfo agent : agents) {
            AgentCapabilities caps = agent.getCapabilities();
            long bandwidthMBps = caps.getMaxBandwidth() / (1024 * 1024);
            double estimatedTime8GB = (8L * 1024) / (double) bandwidthMBps; // in seconds
            log.indentedKeyValue(agent.getAgentId(),
                    String.format("%d MB/s - 8GB transfer: ~%.0f seconds", bandwidthMBps, estimatedTime8GB));
        }
    }
    
    private void checkTransferCapability(long fileSize) {
        String sizeStr = formatSize(fileSize);
        List<String> capable = new ArrayList<>();
        List<String> notCapable = new ArrayList<>();
        
        for (AgentInfo agent : agents) {
            long maxSize = agent.getCapabilities().getMaxTransferSize();
            if (maxSize >= fileSize) {
                capable.add(agent.getAgentId() + " (max: " + formatSize(maxSize) + ")");
            } else {
                notCapable.add(agent.getAgentId() + " (max: " + formatSize(maxSize) + ")");
            }
        }
        
        log.indentedKeyValue("File size", sizeStr);
        log.indentedKeyValue("Capable agents", String.valueOf(capable.size()));
        capable.forEach(a -> log.bullet("✓ " + a));
        if (!notCapable.isEmpty()) {
            log.detail("Not capable:");
            notCapable.forEach(a -> log.bullet("✗ " + a));
        }
    }
    
    private void demonstrateCompressionAndEncryption() {
        log.step(stepCounter++, "Demonstrating compression and encryption capabilities...");
        
        // Compression capabilities
        log.section("Compression Support");
        for (AgentInfo agent : agents) {
            Set<String> compression = agent.getCapabilities().getSupportedCompressionTypes();
            log.indentedKeyValue(agent.getAgentId(), 
                    compression.isEmpty() ? "None" : String.join(", ", compression));
        }
        
        // Find agents with specific compression
        log.section("Finding Agents with ZSTD Compression");
        for (AgentInfo agent : agents) {
            if (agent.getCapabilities().getSupportedCompressionTypes().contains("zstd")) {
                log.bullet(agent.getAgentId() + " supports zstd compression");
            }
        }
        
        // Encryption capabilities
        log.section("Encryption Support");
        for (AgentInfo agent : agents) {
            Set<String> encryption = agent.getCapabilities().getSupportedEncryptionTypes();
            log.indentedKeyValue(agent.getAgentId(),
                    encryption.isEmpty() ? "None" : String.join(", ", encryption));
        }
        
        // Security compliance check
        log.section("Security Compliance Check (AES-256 + TLS-1.3)");
        for (AgentInfo agent : agents) {
            Set<String> encryption = agent.getCapabilities().getSupportedEncryptionTypes();
            boolean hasAES256 = encryption.contains("AES-256");
            boolean hasTLS13 = encryption.contains("TLS-1.3");
            
            if (hasAES256 && hasTLS13) {
                log.expectedSuccess(agent.getAgentId() + " - Fully compliant");
            } else {
                List<String> missing = new ArrayList<>();
                if (!hasAES256) missing.add("AES-256");
                if (!hasTLS13) missing.add("TLS-1.3");
                log.warning(agent.getAgentId() + " - Missing: " + String.join(", ", missing));
            }
        }
    }
    
    private void demonstrateRegionCapabilities() {
        log.step(stepCounter++, "Demonstrating region and locality capabilities...");
        
        // Display region coverage
        log.section("Agent Region Placement");
        for (AgentInfo agent : agents) {
            log.indentedKeyValue(agent.getAgentId(), 
                    agent.getRegion() + " / " + agent.getDatacenter());
        }
        
        // Available regions from capabilities
        log.section("Available Regions (from capabilities)");
        for (AgentInfo agent : agents) {
            Set<String> availableRegions = agent.getCapabilities().getAvailableRegions();
            if (availableRegions != null && !availableRegions.isEmpty()) {
                log.indentedKeyValue(agent.getAgentId(), String.join(", ", availableRegions));
            } else {
                log.indentedKeyValue(agent.getAgentId(), agent.getRegion() + " (home region only)");
            }
        }
        
        // Cross-region capability
        log.section("Cross-Region Capable Agents");
        for (AgentInfo agent : agents) {
            Set<String> regions = agent.getCapabilities().getAvailableRegions();
            if (regions != null && regions.size() > 1) {
                log.bullet(agent.getAgentId() + " can serve " + regions.size() + " regions");
            }
        }
    }
    
    private void demonstrateCustomCapabilities() {
        log.step(stepCounter++, "Demonstrating custom capability extensions...");
        
        // Add custom capabilities
        AgentInfo enterpriseAgent = agents.get(0);
        Map<String, Object> customCaps = new HashMap<>();
        customCaps.put("delta-sync", "true");
        customCaps.put("deduplication", "block-level");
        customCaps.put("checkpoint-restart", "true");
        customCaps.put("bandwidth-throttling", "true");
        customCaps.put("compliance-mode", "SOX,HIPAA,PCI");
        enterpriseAgent.getCapabilities().setCustomCapabilities(customCaps);
        
        log.section("Enterprise Agent Custom Capabilities");
        customCaps.forEach((key, value) -> log.indentedKeyValue(key, String.valueOf(value)));
        
        // Add custom capabilities to cloud agent
        AgentInfo cloudAgent = agents.get(3);
        Map<String, Object> cloudCustomCaps = new HashMap<>();
        cloudCustomCaps.put("auto-scaling", "true");
        cloudCustomCaps.put("spot-instance-aware", "true");
        cloudCustomCaps.put("multi-cloud", "true");
        cloudCustomCaps.put("serverless-mode", "true");
        cloudAgent.getCapabilities().setCustomCapabilities(cloudCustomCaps);
        
        log.section("Cloud Agent Custom Capabilities");
        cloudCustomCaps.forEach((key, value) -> log.indentedKeyValue(key, String.valueOf(value)));
        
        // Find agents with specific custom capability
        log.section("Finding Agents with Delta-Sync Support");
        for (AgentInfo agent : agents) {
            Map<String, Object> caps = agent.getCapabilities().getCustomCapabilities();
            if (caps != null && "true".equals(String.valueOf(caps.get("delta-sync")))) {
                log.expectedSuccess(agent.getAgentId() + " supports delta-sync");
            }
        }
    }
    
    private void demonstrateCapabilityBasedSelection() {
        log.step(stepCounter++, "Demonstrating capability-based agent selection...");
        
        // Job 1: Large SFTP transfer with compression
        log.section("Job 1: Large SFTP Transfer (5GB, with compression)");
        JobRequirements job1 = new JobRequirements()
                .withProtocol("SFTP")
                .withMinSize(5L * 1024 * 1024 * 1024)
                .withCompressionRequired("gzip");
        selectBestAgent(job1);
        
        // Job 2: Cloud-to-cloud S3 transfer
        log.section("Job 2: S3 to S3 Cloud Transfer");
        JobRequirements job2 = new JobRequirements()
                .withProtocol("S3")
                .withPreferredRegion("us-west-2");
        selectBestAgent(job2);
        
        // Job 3: Secure transfer with encryption
        log.section("Job 3: Highly Secure Transfer (AES-256 + TLS-1.3)");
        JobRequirements job3 = new JobRequirements()
                .withProtocol("SFTP")
                .withEncryptionRequired("AES-256")
                .withEncryptionRequired("TLS-1.3");
        selectBestAgent(job3);
        
        // Job 4: High-throughput batch transfer
        log.section("Job 4: High-Throughput Batch (20 concurrent transfers)");
        JobRequirements job4 = new JobRequirements()
                .withProtocol("HTTPS")
                .withMinConcurrentTransfers(20);
        selectBestAgent(job4);
    }
    
    private void selectBestAgent(JobRequirements requirements) {
        log.detail("Requirements: " + requirements);
        
        List<AgentInfo> matchingAgents = new ArrayList<>();
        for (AgentInfo agent : agents) {
            if (!agent.getStatus().isAvailableForWork()) {
                continue;
            }
            
            AgentCapabilities caps = agent.getCapabilities();
            
            // Check protocol
            if (requirements.protocol != null && 
                    !caps.getSupportedProtocols().contains(requirements.protocol)) {
                continue;
            }
            
            // Check transfer size
            if (requirements.minSize > 0 && caps.getMaxTransferSize() < requirements.minSize) {
                continue;
            }
            
            // Check concurrent transfers
            if (requirements.minConcurrentTransfers > 0 && 
                    caps.getMaxConcurrentTransfers() < requirements.minConcurrentTransfers) {
                continue;
            }
            
            // Check compression
            if (requirements.compressionRequired != null && 
                    !caps.getSupportedCompressionTypes().contains(requirements.compressionRequired)) {
                continue;
            }
            
            // Check encryption
            if (!requirements.encryptionRequired.isEmpty()) {
                boolean hasAllEncryption = caps.getSupportedEncryptionTypes()
                        .containsAll(requirements.encryptionRequired);
                if (!hasAllEncryption) {
                    continue;
                }
            }
            
            matchingAgents.add(agent);
        }
        
        // Sort by preferred region, then by capacity
        matchingAgents.sort((a1, a2) -> {
            if (requirements.preferredRegion != null) {
                boolean a1Match = requirements.preferredRegion.equals(a1.getRegion());
                boolean a2Match = requirements.preferredRegion.equals(a2.getRegion());
                if (a1Match != a2Match) {
                    return a1Match ? -1 : 1;
                }
            }
            // Higher capacity preferred
            return Integer.compare(
                    a2.getCapabilities().getMaxConcurrentTransfers(),
                    a1.getCapabilities().getMaxConcurrentTransfers());
        });
        
        if (matchingAgents.isEmpty()) {
            log.error("No agents match the requirements");
        } else {
            AgentInfo selected = matchingAgents.get(0);
            log.success("Selected: " + selected.getAgentId());
            log.indentedKeyValue("Region", selected.getRegion());
            log.indentedKeyValue("Max concurrent", 
                    String.valueOf(selected.getCapabilities().getMaxConcurrentTransfers()));
            log.indentedKeyValue("Max size", 
                    formatSize(selected.getCapabilities().getMaxTransferSize()));
            log.indentedKeyValue("Alternatives", String.valueOf(matchingAgents.size() - 1));
        }
    }
    
    private void demonstrateCapabilityValidation() {
        log.step(stepCounter++, "Demonstrating capability validation...");
        
        // Validate enterprise agent
        log.section("Enterprise Agent Validation");
        validateCapabilities(agents.get(0));
        
        // Validate edge agent (should show warnings)
        log.section("Edge Agent Validation");
        validateCapabilities(agents.get(2));
        
        // Capability comparison
        log.section("Capability Comparison Matrix");
        displayCapabilityMatrix();
    }
    
    private void validateCapabilities(AgentInfo agent) {
        log.indentedKeyValue("Agent", agent.getAgentId());
        
        AgentCapabilities caps = agent.getCapabilities();
        List<String> warnings = new ArrayList<>();
        List<String> passed = new ArrayList<>();
        
        // Protocol check
        if (caps.getSupportedProtocols().size() >= 3) {
            passed.add("Protocol diversity: " + caps.getSupportedProtocols().size() + " protocols");
        } else {
            warnings.add("Limited protocols: " + caps.getSupportedProtocols().size() + " (recommend 3+)");
        }
        
        // Transfer size check
        long gbSize = caps.getMaxTransferSize() / (1024L * 1024 * 1024);
        if (gbSize >= 10) {
            passed.add("Large file support: " + gbSize + " GB");
        } else {
            warnings.add("Limited file size: " + gbSize + " GB (recommend 10GB+)");
        }
        
        // Concurrent transfers check
        if (caps.getMaxConcurrentTransfers() >= 10) {
            passed.add("Concurrent transfers: " + caps.getMaxConcurrentTransfers());
        } else {
            warnings.add("Limited concurrency: " + caps.getMaxConcurrentTransfers() + " (recommend 10+)");
        }
        
        // Encryption check
        if (caps.getSupportedEncryptionTypes().contains("AES-256")) {
            passed.add("Strong encryption: AES-256");
        } else {
            warnings.add("Missing AES-256 encryption");
        }
        
        // Display results
        log.detail("Passed checks:");
        passed.forEach(p -> log.bullet("✓ " + p));
        
        if (!warnings.isEmpty()) {
            log.detail("Warnings:");
            warnings.forEach(w -> log.bullet("⚠ " + w));
        }
    }
    
    private void displayCapabilityMatrix() {
        log.detail("Capability comparison across agents:");
        
        // Headers
        log.detail(String.format("%-20s | %-12s | %-10s | %-15s | %-12s",
                "Agent", "Protocols", "Max Size", "Concurrency", "Encryption"));
        log.detail("-".repeat(75));
        
        for (AgentInfo agent : agents) {
            AgentCapabilities caps = agent.getCapabilities();
            log.detail(String.format("%-20s | %-12d | %-10s | %-15d | %-12s",
                    agent.getAgentId(),
                    caps.getSupportedProtocols().size(),
                    formatSizeShort(caps.getMaxTransferSize()),
                    caps.getMaxConcurrentTransfers(),
                    caps.getSupportedEncryptionTypes().contains("AES-256") ? "AES-256" : "Basic"));
        }
    }
    
    // Agent creation methods
    
    private AgentInfo createEnterpriseAgent() {
        AgentInfo agent = new AgentInfo("enterprise-agent-001", "ent-agent-01.corp.com", "10.0.1.10", 8080);
        agent.setRegion("us-east-1");
        agent.setDatacenter("nyc-dc1");
        agent.setStatus(AgentStatus.HEALTHY);
        
        AgentCapabilities caps = new AgentCapabilities();
        caps.setSupportedProtocols(new HashSet<>(Arrays.asList(
                "HTTP", "HTTPS", "FTP", "SFTP", "SMB", "S3", "AZURE_BLOB", "GCS")));
        caps.setMaxConcurrentTransfers(25);
        caps.setMaxTransferSize(100L * 1024 * 1024 * 1024); // 100 GB
        caps.setMaxBandwidth(10L * 1024 * 1024 * 1024); // 10 GB/s
        caps.setSupportedCompressionTypes(new HashSet<>(Arrays.asList("gzip", "lz4", "zstd", "brotli")));
        caps.setSupportedEncryptionTypes(new HashSet<>(Arrays.asList("AES-256", "AES-128", "TLS-1.3", "TLS-1.2")));
        caps.setAvailableRegions(new HashSet<>(Arrays.asList("us-east-1", "us-west-2", "eu-west-1")));
        agent.setCapabilities(caps);
        
        return agent;
    }
    
    private AgentInfo createStandardAgent() {
        AgentInfo agent = new AgentInfo("standard-agent-001", "std-agent-01.corp.com", "10.0.2.10", 8080);
        agent.setRegion("us-west-2");
        agent.setDatacenter("sfo-dc1");
        agent.setStatus(AgentStatus.HEALTHY);
        
        AgentCapabilities caps = new AgentCapabilities();
        caps.setSupportedProtocols(new HashSet<>(Arrays.asList("HTTP", "HTTPS", "SFTP", "SMB")));
        caps.setMaxConcurrentTransfers(10);
        caps.setMaxTransferSize(10L * 1024 * 1024 * 1024); // 10 GB
        caps.setMaxBandwidth(1024L * 1024 * 1024); // 1 GB/s
        caps.setSupportedCompressionTypes(new HashSet<>(Arrays.asList("gzip", "lz4")));
        caps.setSupportedEncryptionTypes(new HashSet<>(Arrays.asList("AES-256", "TLS-1.2")));
        agent.setCapabilities(caps);
        
        return agent;
    }
    
    private AgentInfo createEdgeAgent() {
        AgentInfo agent = new AgentInfo("edge-agent-001", "edge-agent-01.remote.com", "192.168.1.10", 8080);
        agent.setRegion("ap-southeast-1");
        agent.setDatacenter("edge-site-001");
        agent.setStatus(AgentStatus.HEALTHY);
        
        AgentCapabilities caps = new AgentCapabilities();
        caps.setSupportedProtocols(new HashSet<>(Arrays.asList("HTTP", "HTTPS", "SFTP")));
        caps.setMaxConcurrentTransfers(5);
        caps.setMaxTransferSize(2L * 1024 * 1024 * 1024); // 2 GB
        caps.setMaxBandwidth(100L * 1024 * 1024); // 100 MB/s
        caps.setSupportedCompressionTypes(new HashSet<>(Arrays.asList("gzip")));
        caps.setSupportedEncryptionTypes(new HashSet<>(Arrays.asList("TLS-1.2")));
        agent.setCapabilities(caps);
        
        return agent;
    }
    
    private AgentInfo createCloudAgent() {
        AgentInfo agent = new AgentInfo("cloud-agent-001", "cloud-agent-01.aws.com", "172.31.1.10", 8080);
        agent.setRegion("us-west-2");
        agent.setDatacenter("aws-us-west-2a");
        agent.setStatus(AgentStatus.HEALTHY);
        
        AgentCapabilities caps = new AgentCapabilities();
        caps.setSupportedProtocols(new HashSet<>(Arrays.asList("HTTPS", "S3", "AZURE_BLOB", "GCS")));
        caps.setMaxConcurrentTransfers(50);
        caps.setMaxTransferSize(5L * 1024 * 1024 * 1024 * 1024); // 5 TB
        caps.setMaxBandwidth(25L * 1024 * 1024 * 1024); // 25 GB/s
        caps.setSupportedCompressionTypes(new HashSet<>(Arrays.asList("gzip", "lz4", "zstd")));
        caps.setSupportedEncryptionTypes(new HashSet<>(Arrays.asList("AES-256", "TLS-1.3")));
        caps.setAvailableRegions(new HashSet<>(Arrays.asList(
                "us-west-2", "us-east-1", "eu-west-1", "ap-northeast-1")));
        agent.setCapabilities(caps);
        
        return agent;
    }
    
    private void displayAgentCapabilities(AgentInfo agent) {
        log.indentedKeyValue("Agent ID", agent.getAgentId());
        log.indentedKeyValue("Region", agent.getRegion() + " / " + agent.getDatacenter());
        
        AgentCapabilities caps = agent.getCapabilities();
        log.indentedKeyValue("Protocols", String.join(", ", caps.getSupportedProtocols()));
        log.indentedKeyValue("Max concurrent transfers", String.valueOf(caps.getMaxConcurrentTransfers()));
        log.indentedKeyValue("Max transfer size", formatSize(caps.getMaxTransferSize()));
        log.indentedKeyValue("Max bandwidth", formatSize(caps.getMaxBandwidth()) + "/s");
        log.indentedKeyValue("Compression", String.join(", ", caps.getSupportedCompressionTypes()));
        log.indentedKeyValue("Encryption", String.join(", ", caps.getSupportedEncryptionTypes()));
    }
    
    // Utility methods
    
    private String formatSize(long bytes) {
        if (bytes >= 1024L * 1024 * 1024 * 1024) {
            return String.format("%.1f TB", bytes / (1024.0 * 1024 * 1024 * 1024));
        } else if (bytes >= 1024L * 1024 * 1024) {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        } else if (bytes >= 1024L * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f KB", bytes / 1024.0);
        }
    }
    
    private String formatSizeShort(long bytes) {
        if (bytes >= 1024L * 1024 * 1024 * 1024) {
            return String.format("%.0fTB", bytes / (1024.0 * 1024 * 1024 * 1024));
        } else if (bytes >= 1024L * 1024 * 1024) {
            return String.format("%.0fGB", bytes / (1024.0 * 1024 * 1024));
        } else {
            return String.format("%.0fMB", bytes / (1024.0 * 1024));
        }
    }
    
    // Helper class for job requirements
    
    private static class JobRequirements {
        String protocol;
        long minSize;
        int minConcurrentTransfers;
        String compressionRequired;
        Set<String> encryptionRequired = new HashSet<>();
        String preferredRegion;
        
        JobRequirements withProtocol(String protocol) {
            this.protocol = protocol;
            return this;
        }
        
        JobRequirements withMinSize(long minSize) {
            this.minSize = minSize;
            return this;
        }
        
        JobRequirements withMinConcurrentTransfers(int min) {
            this.minConcurrentTransfers = min;
            return this;
        }
        
        JobRequirements withCompressionRequired(String compression) {
            this.compressionRequired = compression;
            return this;
        }
        
        JobRequirements withEncryptionRequired(String encryption) {
            this.encryptionRequired.add(encryption);
            return this;
        }
        
        JobRequirements withPreferredRegion(String region) {
            this.preferredRegion = region;
            return this;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (protocol != null) sb.append("protocol=").append(protocol).append(", ");
            if (minSize > 0) sb.append("minSize=").append(minSize / (1024*1024*1024)).append("GB, ");
            if (minConcurrentTransfers > 0) sb.append("minConcurrent=").append(minConcurrentTransfers).append(", ");
            if (compressionRequired != null) sb.append("compression=").append(compressionRequired).append(", ");
            if (!encryptionRequired.isEmpty()) sb.append("encryption=").append(encryptionRequired).append(", ");
            if (preferredRegion != null) sb.append("region=").append(preferredRegion);
            return sb.toString().replaceAll(", $", "");
        }
    }
}
