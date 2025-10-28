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
import dev.mars.quorus.core.*;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.controller.state.JobAssignmentCommand;
import dev.mars.quorus.controller.state.JobQueueCommand;
import dev.mars.quorus.controller.raft.RaftNode;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Service responsible for orchestrating job assignments in the distributed file transfer system.
 * Handles the complete lifecycle from job queuing to agent assignment and monitoring.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
public class JobAssignmentService {
    
    private static final Logger logger = Logger.getLogger(JobAssignmentService.class.getName());
    
    private final RaftNode raftNode;
    private final AgentSelectionService agentSelectionService;
    private final ScheduledExecutorService scheduler;
    
    // In-memory caches for performance (synchronized with Raft state)
    private final Map<String, QueuedJob> jobQueue = new ConcurrentHashMap<>();
    private final Map<String, JobAssignment> activeAssignments = new ConcurrentHashMap<>();
    private final Map<String, AgentInfo> availableAgents = new ConcurrentHashMap<>();
    private final Map<String, AgentLoad> agentLoads = new ConcurrentHashMap<>();
    
    public JobAssignmentService(RaftNode raftNode, AgentSelectionService agentSelectionService) {
        this.raftNode = raftNode;
        this.agentSelectionService = agentSelectionService;
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        // Start background assignment processor
        startAssignmentProcessor();
        
        // Start assignment timeout monitor
        startTimeoutMonitor();
    }
    
    /**
     * Submit a new transfer job for assignment.
     * 
     * @param transferRequest the transfer request
     * @param requirements job requirements and preferences
     * @param priority job priority level
     * @return future containing the queued job
     */
    public CompletableFuture<QueuedJob> submitJob(TransferRequest transferRequest, 
                                                 JobRequirements requirements, 
                                                 JobPriority priority) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create transfer job first
                TransferJob transferJob = new TransferJob(transferRequest);

                // Create queued job
                QueuedJob queuedJob = new QueuedJob.Builder()
                        .transferJob(transferJob)
                        .requirements(requirements)
                        .priority(priority)
                        .build();
                
                // Submit to Raft for distributed consensus
                JobQueueCommand command = JobQueueCommand.enqueue(queuedJob);
                Object result = raftNode.submitCommand(command);
                
                if (result instanceof QueuedJob) {
                    QueuedJob enqueuedJob = (QueuedJob) result;
                    jobQueue.put(enqueuedJob.getJobId(), enqueuedJob);
                    
                    logger.info("Job queued successfully: " + enqueuedJob.getJobId() + 
                               " with priority " + priority);
                    
                    return enqueuedJob;
                } else {
                    throw new RuntimeException("Failed to enqueue job: " + transferRequest.getRequestId());
                }
                
            } catch (Exception e) {
                logger.severe("Error submitting job: " + e.getMessage());
                throw new RuntimeException("Failed to submit job", e);
            }
        });
    }
    
    /**
     * Assign a specific job to an agent.
     * 
     * @param jobId the job to assign
     * @param agentId the target agent (null for automatic selection)
     * @return future containing the job assignment
     */
    public CompletableFuture<JobAssignment> assignJob(String jobId, String agentId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                QueuedJob queuedJob = jobQueue.get(jobId);
                if (queuedJob == null) {
                    throw new IllegalArgumentException("Job not found in queue: " + jobId);
                }
                
                // Select agent if not specified
                String selectedAgentId = agentId;
                if (selectedAgentId == null) {
                    selectedAgentId = agentSelectionService.selectAgent(queuedJob, availableAgents, agentLoads);
                    if (selectedAgentId == null) {
                        throw new RuntimeException("No suitable agent found for job: " + jobId);
                    }
                }
                
                // Validate agent availability
                AgentInfo agent = availableAgents.get(selectedAgentId);
                if (agent == null || !agent.getStatus().isAvailableForWork()) {
                    throw new IllegalArgumentException("Agent not available: " + selectedAgentId);
                }
                
                // Create job assignment
                JobAssignment assignment = new JobAssignment.Builder()
                        .jobId(jobId)
                        .agentId(selectedAgentId)
                        .status(JobAssignmentStatus.ASSIGNED)
                        .assignedAt(Instant.now())
                        .build();
                
                // Submit assignment command to Raft
                JobAssignmentCommand assignCommand = JobAssignmentCommand.assign(assignment);
                Object result = raftNode.submitCommand(assignCommand);
                
                if (result instanceof JobAssignment) {
                    JobAssignment createdAssignment = (JobAssignment) result;
                    activeAssignments.put(createdAssignment.getJobId(), createdAssignment);
                    
                    // Remove from queue
                    JobQueueCommand dequeueCommand = JobQueueCommand.dequeue(jobId);
                    raftNode.submitCommand(dequeueCommand);
                    jobQueue.remove(jobId);
                    
                    logger.info("Job assigned successfully: " + jobId + " -> " + selectedAgentId);
                    
                    return createdAssignment;
                } else {
                    throw new RuntimeException("Failed to create assignment for job: " + jobId);
                }
                
            } catch (Exception e) {
                logger.severe("Error assigning job " + jobId + ": " + e.getMessage());
                throw new RuntimeException("Failed to assign job", e);
            }
        });
    }
    
    /**
     * Update the status of a job assignment.
     * 
     * @param jobId the job ID
     * @param newStatus the new status
     * @return future containing the updated assignment
     */
    public CompletableFuture<JobAssignment> updateAssignmentStatus(String jobId, JobAssignmentStatus newStatus) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JobAssignment existing = activeAssignments.get(jobId);
                if (existing == null) {
                    throw new IllegalArgumentException("Assignment not found: " + jobId);
                }
                
                // Create update command
                JobAssignmentCommand updateCommand = JobAssignmentCommand.updateStatus(
                        existing.getJobId() + ":" + existing.getAgentId(), newStatus);
                
                Object result = raftNode.submitCommand(updateCommand);
                
                if (result instanceof JobAssignment) {
                    JobAssignment updatedAssignment = (JobAssignment) result;
                    activeAssignments.put(jobId, updatedAssignment);
                    
                    logger.info("Assignment status updated: " + jobId + " -> " + newStatus);
                    
                    // Clean up completed/failed assignments
                    if (newStatus.isTerminal()) {
                        activeAssignments.remove(jobId);
                    }
                    
                    return updatedAssignment;
                } else {
                    throw new RuntimeException("Failed to update assignment status: " + jobId);
                }
                
            } catch (Exception e) {
                logger.severe("Error updating assignment status for " + jobId + ": " + e.getMessage());
                throw new RuntimeException("Failed to update assignment status", e);
            }
        });
    }
    
    /**
     * Cancel a job assignment.
     * 
     * @param jobId the job to cancel
     * @return future indicating completion
     */
    public CompletableFuture<Void> cancelAssignment(String jobId) {
        return CompletableFuture.runAsync(() -> {
            try {
                JobAssignment existing = activeAssignments.get(jobId);
                if (existing != null) {
                    JobAssignmentCommand cancelCommand = JobAssignmentCommand.cancel(
                            existing.getJobId() + ":" + existing.getAgentId(), "User requested cancellation");
                    raftNode.submitCommand(cancelCommand);
                    activeAssignments.remove(jobId);
                }
                
                // Also remove from queue if still there
                if (jobQueue.containsKey(jobId)) {
                    JobQueueCommand removeCommand = JobQueueCommand.remove(jobId, "Job cancelled by user");
                    raftNode.submitCommand(removeCommand);
                    jobQueue.remove(jobId);
                }
                
                logger.info("Job cancelled: " + jobId);
                
            } catch (Exception e) {
                logger.severe("Error cancelling job " + jobId + ": " + e.getMessage());
                throw new RuntimeException("Failed to cancel job", e);
            }
        });
    }
    
    /**
     * Get current job queue status.
     */
    public Map<String, QueuedJob> getJobQueue() {
        return Map.copyOf(jobQueue);
    }
    
    /**
     * Get current active assignments.
     */
    public Map<String, JobAssignment> getActiveAssignments() {
        return Map.copyOf(activeAssignments);
    }
    
    /**
     * Update agent information (called by agent registry).
     */
    public void updateAgentInfo(AgentInfo agentInfo) {
        availableAgents.put(agentInfo.getAgentId(), agentInfo);
    }
    
    /**
     * Update agent load information (called by monitoring service).
     */
    public void updateAgentLoad(AgentLoad agentLoad) {
        agentLoads.put(agentLoad.getAgentId(), agentLoad);
    }
    
    /**
     * Remove agent from available pool.
     */
    public void removeAgent(String agentId) {
        availableAgents.remove(agentId);
        agentLoads.remove(agentId);
    }
    
    /**
     * Start background processor for automatic job assignment.
     */
    private void startAssignmentProcessor() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                processQueuedJobs();
            } catch (Exception e) {
                logger.warning("Error in assignment processor: " + e.getMessage());
            }
        }, 5, 10, TimeUnit.SECONDS); // Process every 10 seconds
    }
    
    /**
     * Start monitor for assignment timeouts.
     */
    private void startTimeoutMonitor() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                checkAssignmentTimeouts();
            } catch (Exception e) {
                logger.warning("Error in timeout monitor: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS); // Check every 30 seconds
    }
    
    /**
     * Process queued jobs for automatic assignment.
     */
    private void processQueuedJobs() {
        for (QueuedJob queuedJob : jobQueue.values()) {
            try {
                // Skip if job has specific assignment requirements that need manual intervention
                if (queuedJob.getRequirements() != null && 
                    queuedJob.getRequirements().getSelectionStrategy() == JobRequirements.SelectionStrategy.PREFERRED_AGENT &&
                    !queuedJob.getRequirements().getPreferredAgents().isEmpty()) {
                    continue; // Let manual assignment handle preferred agents
                }
                
                // Try automatic assignment
                assignJob(queuedJob.getJobId(), null);
                
            } catch (Exception e) {
                logger.warning("Failed to auto-assign job " + queuedJob.getJobId() + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Check for assignment timeouts and handle them.
     */
    private void checkAssignmentTimeouts() {
        Instant now = Instant.now();
        
        for (JobAssignment assignment : activeAssignments.values()) {
            if (assignment.getStatus() == JobAssignmentStatus.ASSIGNED) {
                // Check if assignment has been pending too long (5 minutes)
                if (assignment.getAssignedAt() != null &&
                    assignment.getAssignedAt().plusSeconds(300).isBefore(now)) {
                    
                    logger.warning("Assignment timeout detected for job: " + assignment.getJobId());
                    
                    try {
                        // Mark as timeout and try reassignment
                        updateAssignmentStatus(assignment.getJobId(), JobAssignmentStatus.TIMEOUT);
                        
                        // For now, just log the timeout - re-queuing would need access to original TransferRequest
                        logger.warning("Assignment timed out for job: " + assignment.getJobId() +
                                     ". Manual intervention may be required.");
                        
                    } catch (Exception e) {
                        logger.severe("Error handling timeout for job " + assignment.getJobId() + ": " + e.getMessage());
                    }
                }
            }
        }
    }
    
    /**
     * Shutdown the service.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
