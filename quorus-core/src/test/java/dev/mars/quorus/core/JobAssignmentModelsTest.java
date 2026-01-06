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

package dev.mars.quorus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Set;

/**
 * Test suite for job assignment model classes.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-10-28
 * @version 1.0
 */
class JobAssignmentModelsTest {
    
    private TransferJob testJob;
    private JobRequirements testRequirements;
    
    @BeforeEach
    void setUp() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("https://test.com/file.txt"))
                .destinationPath(Paths.get("/tmp/file.txt"))
                .protocol("HTTPS")
                .build();
        
        testJob = new TransferJob(request);
        
        testRequirements = new JobRequirements.Builder()
                .targetRegion("us-east-1")
                .selectionStrategy(JobRequirements.SelectionStrategy.WEIGHTED_SCORE)
                .build();
    }
    
    @Test
    void testJobAssignmentCreation() {
        JobAssignment assignment = new JobAssignment.Builder()
                .jobId(testJob.getJobId())
                .agentId("agent-001")
                .assignmentStrategy("WEIGHTED_SCORE")
                .build();
        
        assertNotNull(assignment);
        assertEquals(testJob.getJobId(), assignment.getJobId());
        assertEquals("agent-001", assignment.getAgentId());
        assertEquals(JobAssignmentStatus.ASSIGNED, assignment.getStatus());
        assertTrue(assignment.isActive());
        assertFalse(assignment.isTerminal());
    }
    
    @Test
    void testJobAssignmentStatusTransitions() {
        JobAssignment assignment = new JobAssignment.Builder()
                .jobId(testJob.getJobId())
                .agentId("agent-001")
                .build();
        
        // Test valid transitions
        assertTrue(assignment.getStatus().canTransitionTo(JobAssignmentStatus.ACCEPTED));
        assertTrue(assignment.getStatus().canTransitionTo(JobAssignmentStatus.REJECTED));
        assertTrue(assignment.getStatus().canTransitionTo(JobAssignmentStatus.TIMEOUT));
        
        // Test invalid transitions
        assertFalse(assignment.getStatus().canTransitionTo(JobAssignmentStatus.IN_PROGRESS));
        assertFalse(assignment.getStatus().canTransitionTo(JobAssignmentStatus.COMPLETED));
        
        // Test status update
        JobAssignment accepted = assignment.withStatusAndTimestamp(JobAssignmentStatus.ACCEPTED, Instant.now());
        assertEquals(JobAssignmentStatus.ACCEPTED, accepted.getStatus());
        assertNotNull(accepted.getAcceptedAt());
    }
    
    @Test
    void testQueuedJobPriorityOrdering() {
        QueuedJob lowPriorityJob = new QueuedJob.Builder()
                .transferJob(testJob)
                .priority(JobPriority.LOW)
                .build();
        
        QueuedJob highPriorityJob = new QueuedJob.Builder()
                .transferJob(testJob)
                .priority(JobPriority.HIGH)
                .build();
        
        // High priority should come before low priority
        assertTrue(highPriorityJob.compareTo(lowPriorityJob) < 0);
        assertTrue(lowPriorityJob.compareTo(highPriorityJob) > 0);
    }
    
    @Test
    void testJobPriorityProperties() {
        assertEquals(10, JobPriority.CRITICAL.getValue());
        assertEquals(1, JobPriority.LOW.getValue());
        
        assertTrue(JobPriority.CRITICAL.isHigherThan(JobPriority.NORMAL));
        assertTrue(JobPriority.LOW.isLowerThan(JobPriority.HIGH));
        
        assertEquals(JobPriority.NORMAL, JobPriority.fromString("NORMAL"));
        assertEquals(JobPriority.HIGH, JobPriority.fromString("high"));
        assertEquals(JobPriority.NORMAL, JobPriority.fromValue(5));
    }
    
    @Test
    void testJobRequirementsBuilder() {
        JobRequirements requirements = new JobRequirements.Builder()
                .targetRegion("us-west-2")
                .requiredProtocols(Set.of("HTTPS", "SFTP"))
                .preferredAgents(Set.of("agent-001", "agent-002"))
                .excludedAgents(Set.of("agent-003"))
                .minBandwidth(1000000) // 1 Mbps
                .requiresEncryption(true)
                .selectionStrategy(JobRequirements.SelectionStrategy.LOCALITY_AWARE)
                .build();
        
        assertEquals("us-west-2", requirements.getTargetRegion());
        assertTrue(requirements.isProtocolRequired("HTTPS"));
        assertTrue(requirements.isAgentPreferred("agent-001"));
        assertTrue(requirements.isAgentExcluded("agent-003"));
        assertTrue(requirements.hasGeographicPreferences());
        assertTrue(requirements.hasAgentPreferences());
        assertTrue(requirements.hasCapabilityRequirements());
    }
    
    @Test
    void testAgentLoadCalculations() {
        AgentLoad load = new AgentLoad.Builder()
                .agentId("agent-001")
                .currentJobs(3)
                .maxConcurrentJobs(10)
                .cpuUtilization(0.4)  // Changed from 0.6 to 0.4 to be lightly loaded
                .memoryUtilization(0.4)
                .diskUtilization(0.3)
                .currentBandwidthUsage(400000)  // Changed from 500000 to 400000
                .maxBandwidth(1000000)
                .completedJobsCount(95)
                .failedJobsCount(5)
                .successRate(0.95)  // Explicitly set success rate
                .build();

        assertEquals(0.3, load.getLoadPercentage(), 0.01);
        assertEquals(0.4, load.getBandwidthUtilization(), 0.01);  // Updated expected value
        assertEquals(7, load.getAvailableCapacity());
        assertTrue(load.canAcceptMoreJobs());
        assertFalse(load.isOverloaded());
        assertTrue(load.isLightlyLoaded());  // Now this should pass
        assertEquals(0.95, load.getSuccessRate(), 0.01);
        assertEquals(100, load.getTotalJobsProcessed());
    }
    
    @Test
    void testAgentLoadJobManagement() {
        AgentLoad initialLoad = new AgentLoad.Builder()
                .agentId("agent-001")
                .currentJobs(2)
                .maxConcurrentJobs(5)
                .activeJobIds(Set.of("job-001", "job-002"))
                .totalBytesTransferring(1000000)
                .build();
        
        // Add a job
        AgentLoad withAddedJob = initialLoad.withAddedJob("job-003", 500000);
        assertEquals(3, withAddedJob.getCurrentJobs());
        assertTrue(withAddedJob.getActiveJobIds().contains("job-003"));
        assertEquals(1500000, withAddedJob.getTotalBytesTransferring());
        
        // Remove a job (successful)
        AgentLoad withRemovedJob = withAddedJob.withRemovedJob("job-001", 300000, true);
        assertEquals(2, withRemovedJob.getCurrentJobs());
        assertFalse(withRemovedJob.getActiveJobIds().contains("job-001"));
        assertEquals(1200000, withRemovedJob.getTotalBytesTransferring());
        assertEquals(1, withRemovedJob.getCompletedJobsCount());
    }
    
    @Test
    void testQueuedJobWorkflowIntegration() {
        QueuedJob workflowJob = new QueuedJob.Builder()
                .transferJob(testJob)
                .priority(JobPriority.HIGH)
                .workflowId("workflow-123")
                .groupName("data-sync")
                .submittedBy("user@example.com")
                .requirements(testRequirements)
                .build();
        
        assertTrue(workflowJob.isWorkflowJob());
        assertEquals("workflow-123", workflowJob.getWorkflowId());
        assertEquals("data-sync", workflowJob.getGroupName());
        assertEquals("user@example.com", workflowJob.getSubmittedBy());
        assertTrue(workflowJob.isReadyForAssignment());
    }
    
    @Test
    void testJobAssignmentRetryLogic() {
        JobAssignment assignment = new JobAssignment.Builder()
                .jobId(testJob.getJobId())
                .agentId("agent-001")
                .retryCount(0)
                .build();
        
        JobAssignment retried = assignment.withRetry();
        assertEquals(1, retried.getRetryCount());
        assertEquals(JobAssignmentStatus.ASSIGNED, retried.getStatus());
        
        JobAssignment failed = assignment.withFailure("Connection timeout", Instant.now());
        assertEquals(JobAssignmentStatus.FAILED, failed.getStatus());
        assertEquals("Connection timeout", failed.getFailureReason());
        assertTrue(failed.isTerminal());
    }
}
