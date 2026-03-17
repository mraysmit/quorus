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
import dev.mars.quorus.controller.config.AppConfig;
import dev.mars.quorus.core.*;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.controller.state.CommandResult;
import dev.mars.quorus.controller.state.JobAssignmentCommand;
import dev.mars.quorus.controller.state.JobQueueCommand;
import dev.mars.quorus.controller.raft.RaftNode;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service responsible for orchestrating job assignments in the distributed file transfer system.
 * Handles the complete lifecycle from job queuing to agent assignment and monitoring.
 *
 * <p>Vert.x 5 Migration: Converted from ScheduledExecutorService to Vert.x timers
 * for better integration with the event loop and reduced thread overhead.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-10-28
 * @version 1.0
 */
public class JobAssignmentService {

    private static final Logger logger = LoggerFactory.getLogger(JobAssignmentService.class);

    private final Vertx vertx;
    private final RaftNode raftNode;
    private final AgentSelectionService agentSelectionService;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Vert.x timer IDs (replacing ScheduledExecutorService)
    private long assignmentProcessorTimerId = 0;
    private long timeoutMonitorTimerId = 0;

    // In-memory caches for performance (synchronized with Raft state)
    private final Map<String, QueuedJob> jobQueue = new ConcurrentHashMap<>();
    private final Map<String, JobAssignment> activeAssignments = new ConcurrentHashMap<>();
    private final Map<String, AgentInfo> availableAgents = new ConcurrentHashMap<>();
    private final Map<String, AgentLoad> agentLoads = new ConcurrentHashMap<>();

    /**
     * Create a new JobAssignmentService with Vert.x integration (recommended).
     *
     * @param vertx the Vert.x instance for reactive operations
     * @param raftNode the Raft node for distributed consensus
     * @param agentSelectionService the agent selection service
     */
    public JobAssignmentService(Vertx vertx, RaftNode raftNode, AgentSelectionService agentSelectionService) {
        this.vertx = Objects.requireNonNull(vertx, "Vertx cannot be null");
        this.raftNode = Objects.requireNonNull(raftNode, "RaftNode cannot be null");
        this.agentSelectionService = Objects.requireNonNull(agentSelectionService, "AgentSelectionService cannot be null");

        logger.info("Creating JobAssignmentService: vertxId={} (using Vert.x timers)", 
            System.identityHashCode(vertx));

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
    public Future<QueuedJob> submitJob(TransferRequest transferRequest, 
                                                 JobRequirements requirements, 
                                                 JobPriority priority) {
        logger.debug("Submitting job: requestId={}, priority={}, requirements={}", 
            transferRequest.getRequestId(), priority, requirements);

        // Create transfer job
        TransferJob transferJob = new TransferJob(transferRequest);
        logger.debug("Created transfer job: jobId={}, source={}, dest={}", 
            transferJob.getJobId(), transferRequest.getSourceUri(), transferRequest.getDestinationPath());

        // Create queued job
        QueuedJob queuedJob = new QueuedJob.Builder()
                .transferJob(transferJob)
                .requirements(requirements)
                .priority(priority)
                .build();

        // Submit to Raft for distributed consensus (reactive)
        logger.debug("Submitting enqueue command to Raft: jobId={}", queuedJob.getJobId());
        JobQueueCommand command = JobQueueCommand.enqueue(queuedJob);

        return raftNode.submitCommand(command)
            .compose(result -> {
                if (result instanceof CommandResult.Success<?> success
                        && success.entity() instanceof QueuedJob enqueuedJob) {
                    jobQueue.put(enqueuedJob.getJobId(), enqueuedJob);
                    logger.info("Job queued: jobId={}, priority={}, queueSize={}", 
                        enqueuedJob.getJobId(), priority, jobQueue.size());
                    return Future.succeededFuture(enqueuedJob);
                } else {
                    logger.error("Failed to enqueue job: requestId={}, resultType={}", 
                        transferRequest.getRequestId(), result != null ? result.getClass().getName() : "null");
                    return Future.failedFuture(
                        new RuntimeException("Failed to enqueue job: " + transferRequest.getRequestId()));
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
    public Future<JobAssignment> assignJob(String jobId, String agentId) {
        logger.debug("Assigning job: jobId={}, requestedAgentId={}", jobId, agentId);

        QueuedJob queuedJob = jobQueue.get(jobId);
        if (queuedJob == null) {
            logger.warn("Job not found in queue: jobId={}", jobId);
            return Future.failedFuture(new IllegalArgumentException("Job not found in queue: " + jobId));
        }

        // Select agent if not specified
        String selectedAgentId = agentId;
        if (selectedAgentId == null) {
            // Tenant isolation: only consider agents belonging to the same tenant as the job
            String tenantId = queuedJob.getRequirements() != null ? queuedJob.getRequirements().getTenantId() : null;
            Map<String, AgentInfo> tenantAgents = tenantId == null ? availableAgents
                    : availableAgents.entrySet().stream()
                            .filter(e -> tenantId.equals(e.getValue().getTenantId()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            logger.debug("Auto-selecting agent for job: jobId={}, tenantId={}, tenantAgents={}, totalAgents={}",
                jobId, tenantId, tenantAgents.size(), availableAgents.size());
            selectedAgentId = agentSelectionService.selectAgent(queuedJob, tenantAgents, agentLoads);
            if (selectedAgentId == null) {
                logger.warn("No suitable agent found: jobId={}, tenantId={}, tenantAgents={}", jobId, tenantId, tenantAgents.size());
                return Future.failedFuture(new RuntimeException("No suitable agent found for job: " + jobId));
            }
            logger.debug("Auto-selected agent: jobId={}, selectedAgentId={}", jobId, selectedAgentId);
        }

        // Validate agent availability
        AgentInfo agent = availableAgents.get(selectedAgentId);
        if (agent == null || !agent.getStatus().isAvailableForWork()) {
            logger.warn("Agent not available: agentId={}, exists={}, status={}", 
                selectedAgentId, agent != null, agent != null ? agent.getStatus() : "N/A");
            return Future.failedFuture(new IllegalArgumentException("Agent not available: " + selectedAgentId));
        }

        // Create job assignment (carry tenantId for audit trail)
        String reqTenantId = queuedJob.getRequirements() != null ? queuedJob.getRequirements().getTenantId() : null;
        JobAssignment assignment = new JobAssignment.Builder()
                .jobId(jobId)
                .agentId(selectedAgentId)
                .tenantId(reqTenantId)
                .status(JobAssignmentStatus.ASSIGNED)
                .assignedAt(Instant.now())
                .build();

        // Submit assignment command to Raft (reactive)
        logger.debug("Submitting assignment to Raft: jobId={}, agentId={}", jobId, selectedAgentId);
        JobAssignmentCommand assignCommand = JobAssignmentCommand.assign(assignment);

        return raftNode.submitCommand(assignCommand)
            .compose(result -> {
                if (result instanceof CommandResult.Success<?> success
                        && success.entity() instanceof JobAssignment createdAssignment) {
                    activeAssignments.put(createdAssignment.getJobId(), createdAssignment);

                    // Remove from queue
                    JobQueueCommand dequeueCommand = JobQueueCommand.dequeue(jobId);
                    raftNode.submitCommand(dequeueCommand);
                    jobQueue.remove(jobId);

                    logger.info("Job assigned: jobId={}, agentId={}, activeAssignments={}, queueSize={}", 
                        jobId, createdAssignment.getAgentId(), activeAssignments.size(), jobQueue.size());

                    return Future.succeededFuture(createdAssignment);
                } else {
                    logger.error("Failed to create assignment: jobId={}", jobId);
                    return Future.failedFuture(
                        new RuntimeException("Failed to create assignment for job: " + jobId));
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
    public Future<JobAssignment> updateAssignmentStatus(String jobId, JobAssignmentStatus newStatus) {
        logger.debug("Updating assignment status: jobId={}, newStatus={}", jobId, newStatus);

        JobAssignment existing = activeAssignments.get(jobId);
        if (existing == null) {
            logger.warn("Assignment not found for status update: jobId={}", jobId);
            return Future.failedFuture(new IllegalArgumentException("Assignment not found: " + jobId));
        }

        JobAssignmentStatus oldStatus = existing.getStatus();

        // Create update command
        JobAssignmentCommand updateCommand = JobAssignmentCommand.updateStatus(
                existing.getJobId() + ":" + existing.getAgentId(), oldStatus, newStatus);

        return raftNode.submitCommand(updateCommand)
            .compose(result -> {
                if (result instanceof CommandResult.Success<?> success
                        && success.entity() instanceof JobAssignment updatedAssignment) {
                    activeAssignments.put(jobId, updatedAssignment);

                    logger.info("Assignment status updated: jobId={}, oldStatus={}, newStatus={}", 
                        jobId, oldStatus, newStatus);

                    // Clean up completed/failed assignments
                    if (newStatus.isTerminal()) {
                        activeAssignments.remove(jobId);
                        logger.debug("Removed terminal assignment: jobId={}, activeAssignments={}", 
                            jobId, activeAssignments.size());
                    }

                    return Future.succeededFuture(updatedAssignment);
                } else {
                    logger.error("Failed to update assignment status: jobId={}", jobId);
                    return Future.failedFuture(
                        new RuntimeException("Failed to update assignment status: " + jobId));
                }
            });
    }
    
    /**
     * Cancel a job assignment.
     * 
     * @param jobId the job to cancel
     * @return future indicating completion
     */
    public Future<Void> cancelAssignment(String jobId) {
        logger.debug("Cancelling assignment: jobId={}", jobId);

        Future<Void> cancelFuture = Future.succeededFuture();

        JobAssignment existing = activeAssignments.get(jobId);
        if (existing != null) {
            logger.debug("Cancelling active assignment: jobId={}, agentId={}", jobId, existing.getAgentId());
            JobAssignmentCommand cancelCommand = JobAssignmentCommand.cancel(
                    existing.getJobId() + ":" + existing.getAgentId(), "User requested cancellation");
            cancelFuture = raftNode.submitCommand(cancelCommand).mapEmpty();
            activeAssignments.remove(jobId);
        }

        return cancelFuture.compose(v -> {
            if (jobQueue.containsKey(jobId)) {
                logger.debug("Removing job from queue: jobId={}", jobId);
                JobQueueCommand removeCommand = JobQueueCommand.remove(jobId, "Job cancelled by user");
                return raftNode.submitCommand(removeCommand)
                    .map(r -> {
                        jobQueue.remove(jobId);
                        return (Void) null;
                    });
            }
            return Future.succeededFuture();
        }).onSuccess(v -> logger.info("Job cancelled: jobId={}, activeAssignments={}, queueSize={}", 
            jobId, activeAssignments.size(), jobQueue.size()));
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
     * Uses Vert.x periodic timer instead of ScheduledExecutorService.
     */
    private void startAssignmentProcessor() {
        AppConfig config = AppConfig.get();
        long initialDelay = config.getAssignmentInitialDelayMs();
        long interval = config.getAssignmentIntervalMs();
        
        vertx.setTimer(initialDelay, id -> {
            if (!closed.get()) {
                assignmentProcessorTimerId = vertx.setPeriodic(interval, timerId -> {
                    if (!closed.get()) {
                        try {
                            processQueuedJobs();
                        } catch (Exception e) {
                            logger.warn("Error in assignment processor: {}", e.getMessage());
                            logger.debug("Stack trace for assignment processor error", e);
                        }
                    }
                });
            }
        });
        logger.info("Assignment processor started: initialDelay={}ms, interval={}ms", initialDelay, interval);
    }

    /**
     * Start monitor for assignment timeouts.
     * Uses Vert.x periodic timer instead of ScheduledExecutorService.
     */
    private void startTimeoutMonitor() {
        AppConfig config = AppConfig.get();
        long initialDelay = config.getTimeoutInitialDelayMs();
        long interval = config.getTimeoutIntervalMs();
        
        vertx.setTimer(initialDelay, id -> {
            if (!closed.get()) {
                timeoutMonitorTimerId = vertx.setPeriodic(interval, timerId -> {
                    if (!closed.get()) {
                        try {
                            checkAssignmentTimeouts();
                        } catch (Exception e) {
                            logger.warn("Error in timeout monitor: {}", e.getMessage());
                            logger.debug("Stack trace for timeout monitor error", e);
                        }
                    }
                });
            }
        });
        logger.info("Timeout monitor started: initialDelay={}ms, interval={}ms", initialDelay, interval);
    }
    
    /**
     * Process queued jobs for automatic assignment.
     */
    private void processQueuedJobs() {
        logger.debug("Processing queued jobs: queueSize={}", jobQueue.size());
        for (QueuedJob queuedJob : jobQueue.values()) {
            try {
                // Skip if job has specific assignment requirements that need manual intervention
                if (queuedJob.getRequirements() != null && 
                    queuedJob.getRequirements().getSelectionStrategy() == JobRequirements.SelectionStrategy.PREFERRED_AGENT &&
                    !queuedJob.getRequirements().getPreferredAgents().isEmpty()) {
                    logger.debug("Skipping job with preferred agent requirements: jobId={}", queuedJob.getJobId());
                    continue; // Let manual assignment handle preferred agents
                }
                
                // Try automatic assignment
                logger.debug("Auto-assigning job: jobId={}, priority={}", queuedJob.getJobId(), queuedJob.getPriority());
                assignJob(queuedJob.getJobId(), null);
                
            } catch (Exception e) {
                logger.warn("Failed to auto-assign job: jobId={}, message={}", queuedJob.getJobId(), e.getMessage());
                logger.debug("Stack trace for auto-assignment failure: jobId={}", queuedJob.getJobId(), e);
            }
        }
    }
    
    /**
     * Check for assignment timeouts and handle them.
     */
    private void checkAssignmentTimeouts() {
        Instant now = Instant.now();
        logger.debug("Checking assignment timeouts: activeAssignments={}", activeAssignments.size());
        
        for (JobAssignment assignment : activeAssignments.values()) {
            if (assignment.getStatus() == JobAssignmentStatus.ASSIGNED) {
                // Check if assignment has been pending too long (5 minutes)
                if (assignment.getAssignedAt() != null &&
                    assignment.getAssignedAt().plusSeconds(300).isBefore(now)) {
                    
                    logger.warn("Assignment timeout detected: jobId={}, agentId={}, assignedAt={}", 
                        assignment.getJobId(), assignment.getAgentId(), assignment.getAssignedAt());
                    
                    try {
                        // Mark as timeout and try reassignment
                        updateAssignmentStatus(assignment.getJobId(), JobAssignmentStatus.TIMEOUT);
                        
                        logger.warn("Assignment timed out: jobId={}, manual intervention may be required", 
                            assignment.getJobId());
                        
                    } catch (Exception e) {
                        logger.error("Error handling timeout: jobId={}, message={}", assignment.getJobId(), e.getMessage());
                        logger.debug("Stack trace for assignment timeout handling failure: jobId={}", assignment.getJobId(), e);
                    }
                }
            }
        }
    }
    
    /**
     * Shutdown the service.
     * Cancels all Vert.x timers and clears caches.
     */
    public void shutdown() {
        if (closed.getAndSet(true)) {
            logger.debug("JobAssignmentService already shutdown (idempotent)");
            return; // Already shutdown (idempotent)
        }

        logger.info("Shutting down JobAssignmentService: queueSize={}, activeAssignments={}", 
            jobQueue.size(), activeAssignments.size());

        // Cancel Vert.x timers
        if (assignmentProcessorTimerId != 0) {
            vertx.cancelTimer(assignmentProcessorTimerId);
            assignmentProcessorTimerId = 0;
            logger.debug("Assignment processor timer cancelled");
        }

        if (timeoutMonitorTimerId != 0) {
            vertx.cancelTimer(timeoutMonitorTimerId);
            timeoutMonitorTimerId = 0;
            logger.debug("Timeout monitor timer cancelled");
        }

        // Clear caches
        jobQueue.clear();
        activeAssignments.clear();
        availableAgents.clear();
        agentLoads.clear();

        logger.info("JobAssignmentService shutdown complete");
    }
}
