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

import dev.mars.quorus.agent.*;
import dev.mars.quorus.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for AgentSelectionService covering real-world distributed file transfer scenarios.
 */
class AgentSelectionServiceTest {
    
    private AgentSelectionService selectionService;
    private Map<String, AgentInfo> availableAgents;
    private Map<String, AgentLoad> agentLoads;
    
    @BeforeEach
    void setUp() {
        selectionService = new AgentSelectionService();
        availableAgents = new HashMap<>();
        agentLoads = new HashMap<>();
        
        setupTestAgents();
    }
    
    private void setupTestAgents() {
        // Agent 1: US East, SFTP + HTTP specialist, low load
        AgentInfo agent1 = createAgent("agent-001", "us-east-1", "datacenter-1", 
                Set.of("sftp", "http", "https"), 10, 1000000000L); // 1GB max transfer
        availableAgents.put("agent-001", agent1);
        agentLoads.put("agent-001", createAgentLoad("agent-001", 2, 10, 0.3, 0.4, 0.2));
        
        // Agent 2: US West, Multi-protocol, medium load
        AgentInfo agent2 = createAgent("agent-002", "us-west-2", "datacenter-2", 
                Set.of("sftp", "ftp", "smb", "http", "https"), 15, 2000000000L); // 2GB max transfer
        availableAgents.put("agent-002", agent2);
        agentLoads.put("agent-002", createAgentLoad("agent-002", 8, 15, 0.6, 0.5, 0.4));
        
        // Agent 3: EU West, High capacity, high load
        AgentInfo agent3 = createAgent("agent-003", "eu-west-1", "datacenter-3", 
                Set.of("sftp", "http", "https", "s3"), 20, 5000000000L); // 5GB max transfer
        availableAgents.put("agent-003", agent3);
        agentLoads.put("agent-003", createAgentLoad("agent-003", 18, 20, 0.9, 0.8, 0.7));
        
        // Agent 4: US East, Overloaded (should be filtered out)
        AgentInfo agent4 = createAgent("agent-004", "us-east-1", "datacenter-1", 
                Set.of("ftp", "http"), 5, 500000000L); // 500MB max transfer
        agent4.setStatus(AgentStatus.OVERLOADED);
        availableAgents.put("agent-004", agent4);
        agentLoads.put("agent-004", createAgentLoad("agent-004", 5, 5, 1.0, 0.95, 0.9));
    }
    
    @Test
    void testRoundRobinSelection() {
        // Create a simple job with round-robin strategy
        QueuedJob job = createJob("job-001", "https://source.com/file.txt", "/dest/file.txt",
                JobRequirements.SelectionStrategy.ROUND_ROBIN, "tenant-1");
        
        // Test multiple selections to verify round-robin behavior
        Set<String> selectedAgents = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            String selected = selectionService.selectAgent(job, availableAgents, agentLoads);
            assertNotNull(selected, "Should select an agent");
            selectedAgents.add(selected);
        }
        
        // Should have selected multiple different agents (round-robin)
        assertTrue(selectedAgents.size() >= 2, "Round-robin should select different agents");
    }
    
    @Test
    void testLeastLoadedSelection() {
        QueuedJob job = createJob("job-002", "https://source.com/file.txt", "/dest/file.txt",
                JobRequirements.SelectionStrategy.LEAST_LOADED, null);
        
        String selected = selectionService.selectAgent(job, availableAgents, agentLoads);
        
        // Should select agent-001 (lowest load: 2/10 jobs, 0.3 CPU)
        assertEquals("agent-001", selected, "Should select least loaded agent");
    }
    
    @Test
    void testCapabilityBasedSelection() {
        // Job requiring S3 protocol (only agent-003 supports it)
        QueuedJob job = createJob("job-003", "s3://source.com/data.json", "/bucket/data.json",
                JobRequirements.SelectionStrategy.CAPABILITY_BASED, null);
        
        String selected = selectionService.selectAgent(job, availableAgents, agentLoads);
        
        assertEquals("agent-003", selected, "Should select agent with S3 capability");
    }
    
    @Test
    void testLocalityAwareSelection() {
        // Job targeting EU region
        JobRequirements requirements = new JobRequirements.Builder()
                .targetRegion("eu-west-1")
                .selectionStrategy(JobRequirements.SelectionStrategy.LOCALITY_AWARE)
                .build();
        
        QueuedJob job = createJobWithRequirements("job-004", "https://eu-source.com/file.txt",
                "/eu-dest/file.txt", requirements);
        
        String selected = selectionService.selectAgent(job, availableAgents, agentLoads);
        
        assertEquals("agent-003", selected, "Should select agent in target region");
    }
    
    @Test
    void testPreferredAgentSelection() {
        JobRequirements requirements = new JobRequirements.Builder()
                .preferredAgents(Set.of("agent-002"))
                .selectionStrategy(JobRequirements.SelectionStrategy.PREFERRED_AGENT)
                .build();
        
        QueuedJob job = createJobWithRequirements("job-005", "https://source.com/file.txt",
                "/dest/file.txt", requirements);
        
        String selected = selectionService.selectAgent(job, availableAgents, agentLoads);
        
        assertEquals("agent-002", selected, "Should select preferred agent");
    }
    
    @Test
    void testWeightedScoreSelection() {
        // Large file transfer requiring high capacity
        TransferRequest request = new TransferRequest.Builder()
                .sourceUri(URI.create("https://bigdata.com/dataset.zip"))
                .destinationPath(Paths.get("/backup/dataset.zip"))
                .expectedSize(3000000000L) // 3GB file
                .build();

        JobRequirements requirements = new JobRequirements.Builder()
                .selectionStrategy(JobRequirements.SelectionStrategy.WEIGHTED_SCORE)
                .build();

        TransferJob transferJob = new TransferJob(request);
        QueuedJob job = new QueuedJob.Builder()
                .transferJob(transferJob)
                .requirements(requirements)
                .build();
        
        String selected = selectionService.selectAgent(job, availableAgents, agentLoads);
        
        // Should prefer agent-003 (highest capacity: 5GB) despite higher load
        assertEquals("agent-003", selected, "Should select agent with best weighted score for large transfer");
    }
    
    @Test
    void testAgentFiltering() {
        // Job requiring SMB protocol (only agent-002 supports it among available agents)
        QueuedJob job = createJob("job-007", "smb://fileserver/file.txt", "/dest/file.txt",
                JobRequirements.SelectionStrategy.CAPABILITY_BASED, null);
        
        String selected = selectionService.selectAgent(job, availableAgents, agentLoads);
        
        assertEquals("agent-002", selected, "Should filter to agents supporting required protocol");
    }
    
    @Test
    void testExcludedAgentFiltering() {
        JobRequirements requirements = new JobRequirements.Builder()
                .excludedAgents(Set.of("agent-001", "agent-002"))
                .selectionStrategy(JobRequirements.SelectionStrategy.LEAST_LOADED)
                .build();
        
        QueuedJob job = createJobWithRequirements("job-008", "https://source.com/file.txt",
                "/dest/file.txt", requirements);
        
        String selected = selectionService.selectAgent(job, availableAgents, agentLoads);
        
        assertEquals("agent-003", selected, "Should exclude specified agents");
    }
    
    @Test
    void testNoEligibleAgents() {
        // Job requiring a protocol no agent supports
        QueuedJob job = createJob("job-009", "rsync://source.com/file.txt", "/dest/file.txt",
                JobRequirements.SelectionStrategy.CAPABILITY_BASED, null);
        
        String selected = selectionService.selectAgent(job, availableAgents, agentLoads);
        
        assertNull(selected, "Should return null when no agents support required protocol");
    }
    
    @Test
    void testEmptyAgentList() {
        QueuedJob job = createJob("job-010", "https://source.com/file.txt", "/dest/file.txt",
                JobRequirements.SelectionStrategy.LEAST_LOADED, null);
        
        String selected = selectionService.selectAgent(job, Collections.emptyMap(), Collections.emptyMap());
        
        assertNull(selected, "Should return null when no agents available");
    }
    
    // Helper methods
    
    private AgentInfo createAgent(String agentId, String region, String datacenter, 
                                 Set<String> protocols, int maxJobs, long maxTransferSize) {
        AgentInfo agent = new AgentInfo(agentId, agentId + ".example.com", "192.168.1." + agentId.charAt(agentId.length()-1), 8080);
        agent.setRegion(region);
        agent.setDatacenter(datacenter);
        agent.setStatus(AgentStatus.HEALTHY);
        agent.setLastHeartbeat(Instant.now());
        
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setSupportedProtocols(protocols);
        capabilities.setMaxConcurrentTransfers(maxJobs);
        capabilities.setMaxTransferSize(maxTransferSize);
        capabilities.setMaxBandwidth(100000000L); // 100 MB/s
        agent.setCapabilities(capabilities);
        
        return agent;
    }
    
    private AgentLoad createAgentLoad(String agentId, int currentJobs, int maxJobs,
                                     double cpuUtil, double memUtil, double diskUtil) {
        return new AgentLoad.Builder()
                .agentId(agentId)
                .currentJobs(currentJobs)
                .maxConcurrentJobs(maxJobs)
                .cpuUtilization(cpuUtil)
                .memoryUtilization(memUtil)
                .diskUtilization(diskUtil)
                .build();
    }
    
    private QueuedJob createJob(String jobId, String sourceUri, String destUri, 
                               JobRequirements.SelectionStrategy strategy, String tenantId) {
        JobRequirements requirements = new JobRequirements.Builder()
                .selectionStrategy(strategy)
                .tenantId(tenantId)
                .build();
        
        return createJobWithRequirements(jobId, sourceUri, destUri, requirements);
    }
    
    private QueuedJob createJobWithRequirements(String jobId, String sourceUri, String destPath,
                                               JobRequirements requirements) {
        TransferRequest request = new TransferRequest.Builder()
                .sourceUri(URI.create(sourceUri))
                .destinationPath(Paths.get(destPath))
                .expectedSize(100000000L) // 100MB default
                .build();

        TransferJob transferJob = new TransferJob(request);
        return new QueuedJob.Builder()
                .transferJob(transferJob)
                .requirements(requirements)
                .build();
    }
}
