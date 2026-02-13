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

package dev.mars.quorus.controller.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.agent.AgentCapabilities;
import dev.mars.quorus.controller.raft.RaftStateMachine;
import dev.mars.quorus.core.JobPriority;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.JobAssignment;
import dev.mars.quorus.core.QueuedJob;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Description for QuorusStateMachine
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */

public class QuorusStateMachine implements RaftStateMachine {

    private static final Logger logger = LoggerFactory.getLogger(QuorusStateMachine.class);

    // State data
    private final Map<String, TransferJobSnapshot> transferJobs = new ConcurrentHashMap<>();
    private final Map<String, AgentInfo> agents = new ConcurrentHashMap<>();
    private final Map<String, String> systemMetadata = new ConcurrentHashMap<>();
    private final Map<String, JobAssignment> jobAssignments = new ConcurrentHashMap<>();
    private final Map<String, QueuedJob> jobQueue = new ConcurrentHashMap<>();
    private final AtomicLong lastAppliedIndex = new AtomicLong(0);

    // JSON serialization
    private final ObjectMapper objectMapper = new ObjectMapper();

    {
        objectMapper.registerModule(new JavaTimeModule());
    }

    // Default metadata version for compatibility tracking
    private static final String DEFAULT_VERSION = "2.0";

    public QuorusStateMachine(Map<String, String> initialMetadata) {
        // Set default metadata first
        initializeDefaultMetadata();
        
        // Override with any provided initial metadata
        if (initialMetadata != null) {
            systemMetadata.putAll(initialMetadata);
        }

        // Initialize OpenTelemetry Metrics
        Meter meter = GlobalOpenTelemetry.getMeter("quorus-controller");

        meter.gaugeBuilder("quorus.agents.total")
                .setDescription("Total number of registered agents")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(agents.size()));

        meter.gaugeBuilder("quorus.jobs.total")
                .setDescription("Total number of transfer jobs")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(transferJobs.size()));

        meter.gaugeBuilder("quorus.jobs.queued")
                .setDescription("Number of jobs in the queue")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(jobQueue.size()));

        meter.gaugeBuilder("quorus.jobs.assignments")
                .setDescription("Total number of job assignments")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(jobAssignments.size()));
    }

    public QuorusStateMachine() {
        this(null);
    }

    /**
     * Initializes default metadata values including version information.
     * Called during construction and reset to ensure consistent initial state.
     */
    private void initializeDefaultMetadata() {
        logger.debug("Initializing default metadata: version={}", DEFAULT_VERSION);
        systemMetadata.put("version", DEFAULT_VERSION);
    }

    @Override
    public Object apply(Object command) {
        if (command == null) {
            logger.debug("Received null command, returning null");
            return null; // No-op command
        }

        logger.debug("Applying command: type={}", command.getClass().getSimpleName());

        try {
            Object result;
            if (command instanceof TransferJobCommand) {
                result = applyTransferJobCommand((TransferJobCommand) command);
            } else if (command instanceof AgentCommand) {
                result = applyAgentCommand((AgentCommand) command);
            } else if (command instanceof SystemMetadataCommand) {
                result = applySystemMetadataCommand((SystemMetadataCommand) command);
            } else if (command instanceof JobAssignmentCommand) {
                result = applyJobAssignmentCommand((JobAssignmentCommand) command);
            } else if (command instanceof JobQueueCommand) {
                result = applyJobQueueCommand((JobQueueCommand) command);
            } else {
                logger.warn("Unknown command type: {}", command.getClass().getName());
                return null;
            }
            logger.debug("Command applied successfully: type={}, result={}", 
                command.getClass().getSimpleName(), result != null ? "non-null" : "null");
            return result;
        } catch (Exception e) {
            logger.error("Failed to apply command: type={}", command.getClass().getSimpleName(), e);
            throw new RuntimeException("Failed to apply command", e);
        }
    }

    private Object applyTransferJobCommand(TransferJobCommand command) {
        String jobId = command.getJobId();

        switch (command.getType()) {
            case CREATE:
                TransferJob job = command.getTransferJob();
                logger.debug("Creating transfer job: jobId={}, sourceUri={}, destPath={}", 
                    jobId, job.getRequest().getSourceUri(), job.getRequest().getDestinationPath());
                TransferJobSnapshot snapshot = TransferJobSnapshot.fromTransferJob(job);
                transferJobs.put(jobId, snapshot);
                logger.info("Created transfer job: jobId={}, protocol={}, totalJobs={}", 
                    jobId, job.getRequest().getProtocol(), transferJobs.size());
                return job;

            case UPDATE_STATUS:
                logger.debug("Updating transfer job status: jobId={}, newStatus={}", jobId, command.getStatus());
                TransferJobSnapshot existingJob = transferJobs.get(jobId);
                if (existingJob != null) {
                    TransferStatus oldStatus = existingJob.getStatus();
                    // Create updated snapshot with new status
                    TransferJobSnapshot updatedJob = new TransferJobSnapshot(
                            existingJob.getJobId(),
                            existingJob.getSourceUri(),
                            existingJob.getDestinationPath(),
                            command.getStatus(),
                            existingJob.getBytesTransferred(),
                            existingJob.getTotalBytes(),
                            existingJob.getStartTime(),
                            java.time.Instant.now(),
                            existingJob.getErrorMessage(),
                            existingJob.getDescription());
                    transferJobs.put(jobId, updatedJob);
                    logger.info("Updated transfer job status: jobId={}, oldStatus={}, newStatus={}", 
                        jobId, oldStatus, command.getStatus());
                    return updatedJob;
                } else {
                    logger.warn("Transfer job not found for update: jobId={}", jobId);
                    return null;
                }

            case UPDATE_PROGRESS:
                logger.debug("Updating transfer job progress: jobId={}, bytesTransferred={}", 
                    jobId, command.getBytesTransferred());
                TransferJobSnapshot progressJob = transferJobs.get(jobId);
                if (progressJob != null) {
                    long oldBytes = progressJob.getBytesTransferred();
                    // Create updated snapshot with new bytes transferred
                    TransferJobSnapshot updatedJob = new TransferJobSnapshot(
                            progressJob.getJobId(),
                            progressJob.getSourceUri(),
                            progressJob.getDestinationPath(),
                            progressJob.getStatus(),
                            command.getBytesTransferred(),
                            progressJob.getTotalBytes(),
                            progressJob.getStartTime(),
                            java.time.Instant.now(),
                            progressJob.getErrorMessage(),
                            progressJob.getDescription());
                    transferJobs.put(jobId, updatedJob);
                    logger.debug("Updated transfer job progress: jobId={}, oldBytes={}, newBytes={}, totalBytes={}", 
                        jobId, oldBytes, command.getBytesTransferred(), progressJob.getTotalBytes());
                    return updatedJob;
                } else {
                    logger.warn("Transfer job not found for progress update: jobId={}", jobId);
                    return null;
                }

            case DELETE:
                logger.debug("Deleting transfer job: jobId={}", jobId);
                TransferJobSnapshot removedJob = transferJobs.remove(jobId);
                if (removedJob != null) {
                    logger.info("Deleted transfer job: jobId={}, finalStatus={}, totalJobs={}", 
                        jobId, removedJob.getStatus(), transferJobs.size());
                    return removedJob;
                } else {
                    logger.warn("Transfer job not found for deletion: jobId={}", jobId);
                    return null;
                }

            default:
                logger.warn("Unknown transfer job command type: {}", command.getType());
                return null;
        }
    }

    private Object applyAgentCommand(AgentCommand command) {
        String agentId = command.getAgentId();
        logger.debug("Processing agent command: agentId={}, type={}", agentId, command.getType());

        switch (command.getType()) {
            case REGISTER:
                AgentInfo agentInfo = command.getAgentInfo();
                logger.debug("Registering agent: agentId={}, endpoint={}, status={}", 
                    agentId, agentInfo.getEndpoint(), agentInfo.getStatus());
                agents.put(agentId, agentInfo);
                logger.info("Registered agent: agentId={}, endpoint={}, totalAgents={}", 
                    agentId, agentInfo.getEndpoint(), agents.size());
                return agentInfo;

            case DEREGISTER:
                logger.debug("Deregistering agent: agentId={}", agentId);
                AgentInfo removedAgent = agents.remove(agentId);
                if (removedAgent != null) {
                    logger.info("Deregistered agent: agentId={}, endpoint={}, totalAgents={}", 
                        agentId, removedAgent.getEndpoint(), agents.size());
                    return removedAgent;
                } else {
                    logger.warn("Agent not found for deregistration: agentId={}", agentId);
                    return null;
                }

            case UPDATE_STATUS:
                logger.debug("Updating agent status: agentId={}, newStatus={}", agentId, command.getNewStatus());
                AgentInfo existingAgent = agents.get(agentId);
                if (existingAgent != null) {
                    AgentStatus oldStatus = existingAgent.getStatus();
                    AgentStatus newStatus = command.getNewStatus();
                    existingAgent.setStatus(newStatus);
                    existingAgent.setLastHeartbeat(Instant.now());
                    agents.put(agentId, existingAgent);
                    logger.info("Updated agent status: agentId={}, oldStatus={}, newStatus={}", 
                        agentId, oldStatus, newStatus);
                    return existingAgent;
                } else {
                    logger.warn("Agent not found for status update: agentId={}", agentId);
                    return null;
                }

            case UPDATE_CAPABILITIES:
                logger.debug("Updating agent capabilities: agentId={}", agentId);
                AgentInfo agentToUpdate = agents.get(agentId);
                if (agentToUpdate != null) {
                    AgentCapabilities newCapabilities = command.getNewCapabilities();
                    agentToUpdate.setCapabilities(newCapabilities);
                    agentToUpdate.setLastHeartbeat(Instant.now());
                    agents.put(agentId, agentToUpdate);
                    logger.info("Updated agent capabilities: agentId={}, protocols={}", 
                        agentId, newCapabilities != null ? newCapabilities.getSupportedProtocols() : "null");
                    return agentToUpdate;
                } else {
                    logger.warn("Agent not found for capabilities update: agentId={}", agentId);
                    return null;
                }

            case HEARTBEAT:
                logger.debug("Processing heartbeat: agentId={}", agentId);
                AgentInfo agentForHeartbeat = agents.get(agentId);
                if (agentForHeartbeat != null) {
                    agentForHeartbeat.setLastHeartbeat(Instant.now());
                    // Update status to HEALTHY if it was in a transitional state
                    if (agentForHeartbeat.getStatus() == AgentStatus.REGISTERING) {
                        logger.debug("Agent transitioning from REGISTERING to HEALTHY: agentId={}", agentId);
                        agentForHeartbeat.setStatus(AgentStatus.HEALTHY);
                    }
                    agents.put(agentId, agentForHeartbeat);
                    logger.debug("Heartbeat received: agentId={}, status={}", agentId, agentForHeartbeat.getStatus());
                    return agentForHeartbeat;
                } else {
                    logger.warn("Agent not found for heartbeat: agentId={}", agentId);
                    return null;
                }

            default:
                logger.warn("Unknown agent command type: type={}", command.getType());
                return null;
        }
    }

    private Object applySystemMetadataCommand(SystemMetadataCommand command) {
        String key = command.getKey();
        logger.debug("Processing system metadata command: key={}, type={}", key, command.getType());

        switch (command.getType()) {
            case SET:
                String oldValue = systemMetadata.put(key, command.getValue());
                logger.info("Set system metadata: key={}, value={}, previousValue={}", 
                    key, command.getValue(), oldValue);
                return oldValue;

            case DELETE:
                String removedValue = systemMetadata.remove(key);
                logger.info("Deleted system metadata: key={}, removedValue={}", key, removedValue);
                return removedValue;

            default:
                logger.warn("Unknown system metadata command type: type={}", command.getType());
                return null;
        }
    }

    private Object applyJobAssignmentCommand(JobAssignmentCommand command) {
        String assignmentId = command.getAssignmentId();
        logger.debug("Processing job assignment command: assignmentId={}, type={}", assignmentId, command.getType());

        switch (command.getType()) {
            case ASSIGN:
                JobAssignment assignment = command.getJobAssignment();
                logger.debug("Creating job assignment: assignmentId={}, jobId={}, agentId={}", 
                    assignmentId, assignment.getJobId(), assignment.getAgentId());
                jobAssignments.put(assignmentId, assignment);
                logger.info("Created job assignment: assignmentId={}, jobId={}, agentId={}, totalAssignments={}", 
                    assignmentId, assignment.getJobId(), assignment.getAgentId(), jobAssignments.size());
                return assignment;

            case ACCEPT:
                logger.debug("Processing assignment accept: assignmentId={}", assignmentId);
                JobAssignment existing = jobAssignments.get(assignmentId);
                if (existing != null) {
                    JobAssignment updated = existing.withStatusAndTimestamp(command.getNewStatus(),
                            command.getTimestamp());
                    jobAssignments.put(assignmentId, updated);
                    logger.info("Job assignment accepted: assignmentId={}, jobId={}, agentId={}", 
                        assignmentId, existing.getJobId(), existing.getAgentId());
                    return updated;
                } else {
                    logger.warn("Job assignment not found for accept: assignmentId={}", assignmentId);
                    return null;
                }

            case REJECT:
                logger.debug("Processing assignment reject: assignmentId={}, reason={}", 
                    assignmentId, command.getReason());
                JobAssignment rejectedAssignment = jobAssignments.get(assignmentId);
                if (rejectedAssignment != null) {
                    JobAssignment updated = rejectedAssignment.withStatusAndTimestamp(command.getNewStatus(),
                            command.getTimestamp());
                    jobAssignments.put(assignmentId, updated);
                    logger.info("Job assignment rejected: assignmentId={}, jobId={}, reason={}", 
                        assignmentId, rejectedAssignment.getJobId(), command.getReason());
                    return updated;
                } else {
                    logger.warn("Job assignment not found for reject: assignmentId={}", assignmentId);
                    return null;
                }

            case UPDATE_STATUS:
                logger.debug("Updating assignment status: assignmentId={}, newStatus={}", 
                    assignmentId, command.getNewStatus());
                JobAssignment statusAssignment = jobAssignments.get(assignmentId);
                if (statusAssignment != null) {
                    JobAssignment updated = statusAssignment.withStatusAndTimestamp(command.getNewStatus(),
                            command.getTimestamp());
                    jobAssignments.put(assignmentId, updated);
                    logger.info("Updated job assignment status: assignmentId={}, newStatus={}", 
                        assignmentId, command.getNewStatus());
                    return updated;
                } else {
                    logger.warn("Job assignment not found for status update: assignmentId={}", assignmentId);
                    return null;
                }

            case TIMEOUT:
                logger.debug("Processing assignment timeout: assignmentId={}", assignmentId);
                JobAssignment timeoutAssignment = jobAssignments.get(assignmentId);
                if (timeoutAssignment != null) {
                    JobAssignment updated = timeoutAssignment.withStatusAndTimestamp(command.getNewStatus(),
                            command.getTimestamp());
                    jobAssignments.put(assignmentId, updated);
                    logger.info("Job assignment timed out: assignmentId={}, jobId={}", 
                        assignmentId, timeoutAssignment.getJobId());
                    return updated;
                } else {
                    logger.warn("Job assignment not found for timeout: assignmentId={}", assignmentId);
                    return null;
                }

            case CANCEL:
                logger.debug("Processing assignment cancel: assignmentId={}, reason={}", 
                    assignmentId, command.getReason());
                JobAssignment cancelAssignment = jobAssignments.get(assignmentId);
                if (cancelAssignment != null) {
                    JobAssignment updated = cancelAssignment.withStatusAndTimestamp(command.getNewStatus(),
                            command.getTimestamp());
                    jobAssignments.put(assignmentId, updated);
                    logger.info("Job assignment cancelled: assignmentId={}, jobId={}, reason={}", 
                        assignmentId, cancelAssignment.getJobId(), command.getReason());
                    return updated;
                } else {
                    logger.warn("Job assignment not found for cancel: assignmentId={}", assignmentId);
                    return null;
                }

            case REMOVE:
                logger.debug("Removing job assignment: assignmentId={}", assignmentId);
                JobAssignment removed = jobAssignments.remove(assignmentId);
                if (removed != null) {
                    logger.info("Removed job assignment: assignmentId={}, totalAssignments={}", 
                        assignmentId, jobAssignments.size());
                    return removed;
                } else {
                    logger.warn("Job assignment not found for removal: assignmentId={}", assignmentId);
                    return null;
                }

            default:
                logger.warn("Unknown job assignment command type: type={}", command.getType());
                return null;
        }
    }

    private Object applyJobQueueCommand(JobQueueCommand command) {
        String jobId = command.getJobId();
        logger.debug("Processing job queue command: jobId={}, type={}", jobId, command.getType());

        switch (command.getType()) {
            case ENQUEUE:
                QueuedJob queuedJob = command.getQueuedJob();
                logger.debug("Enqueueing job: jobId={}, priority={}", jobId, queuedJob.getPriority());
                jobQueue.put(jobId, queuedJob);
                logger.info("Enqueued job: jobId={}, priority={}, queueSize={}", 
                    jobId, queuedJob.getPriority(), jobQueue.size());
                return queuedJob;

            case DEQUEUE:
                logger.debug("Dequeueing job: jobId={}", jobId);
                QueuedJob dequeuedJob = jobQueue.remove(jobId);
                if (dequeuedJob != null) {
                    logger.info("Dequeued job: jobId={}, queueSize={}", jobId, jobQueue.size());
                    return dequeuedJob;
                } else {
                    logger.warn("Job not found in queue for dequeue: jobId={}", jobId);
                    return null;
                }

            case PRIORITIZE:
                logger.debug("Updating job priority: jobId={}, newPriority={}", jobId, command.getNewPriority());
                QueuedJob existingJob = jobQueue.get(jobId);
                if (existingJob != null) {
                    JobPriority oldPriority = existingJob.getPriority();
                    QueuedJob updatedJob = existingJob.withPriority(command.getNewPriority());
                    jobQueue.put(jobId, updatedJob);
                    logger.info("Updated job priority: jobId={}, oldPriority={}, newPriority={}, reason={}", 
                        jobId, oldPriority, command.getNewPriority(), command.getReason());
                    return updatedJob;
                } else {
                    logger.warn("Job not found in queue for prioritize: jobId={}", jobId);
                    return null;
                }

            case REMOVE:
                logger.debug("Removing job from queue: jobId={}", jobId);
                QueuedJob removedJob = jobQueue.remove(jobId);
                if (removedJob != null) {
                    logger.info("Removed job from queue: jobId={}, reason={}, queueSize={}", 
                        jobId, command.getReason(), jobQueue.size());
                    return removedJob;
                } else {
                    logger.warn("Job not found in queue for removal: jobId={}", jobId);
                    return null;
                }

            case EXPEDITE:
                logger.debug("Expediting job: jobId={}", jobId);
                QueuedJob expediteJob = jobQueue.get(jobId);
                if (expediteJob != null) {
                    // For expedite, we could implement special logic to move to front
                    // For now, we'll just log it - actual queue ordering would be handled by the
                    // queue service
                    logger.info("Expedited job: jobId={}, reason={}", jobId, command.getReason());
                    return expediteJob;
                } else {
                    logger.warn("Job not found in queue for expedite: jobId={}", jobId);
                    return null;
                }

            case UPDATE_REQUIREMENTS:
                logger.debug("Updating job requirements: jobId={}", jobId);
                QueuedJob updatedJob = command.getQueuedJob();
                jobQueue.put(jobId, updatedJob);
                logger.info("Updated job requirements: jobId={}", jobId);
                return updatedJob;

            default:
                logger.warn("Unknown job queue command type: type={}", command.getType());
                return null;
        }
    }

    @Override
    public byte[] takeSnapshot() {
        logger.debug("Taking state machine snapshot: jobs={}, agents={}, metadata={}, assignments={}, queue={}", 
            transferJobs.size(), agents.size(), systemMetadata.size(), jobAssignments.size(), jobQueue.size());
        try {
            QuorusSnapshot snapshot = new QuorusSnapshot();
            snapshot.setTransferJobs(new ConcurrentHashMap<>(transferJobs));
            snapshot.setAgents(new ConcurrentHashMap<>(agents));
            snapshot.setSystemMetadata(new ConcurrentHashMap<>(systemMetadata));
            snapshot.setJobAssignments(new ConcurrentHashMap<>(jobAssignments));
            snapshot.setJobQueue(new ConcurrentHashMap<>(jobQueue));
            snapshot.setLastAppliedIndex(lastAppliedIndex.get());

            byte[] data = objectMapper.writeValueAsBytes(snapshot);
            logger.info("Created snapshot: size={}bytes, jobs={}, agents={}, assignments={}, queue={}, lastAppliedIndex={}", 
                data.length, transferJobs.size(), agents.size(), jobAssignments.size(), jobQueue.size(), lastAppliedIndex.get());
            return data;
        } catch (IOException e) {
            logger.error("Failed to create snapshot: jobs={}, agents={}", transferJobs.size(), agents.size(), e);
            throw new RuntimeException("Failed to create snapshot", e);
        }
    }

    @Override
    public void restoreSnapshot(byte[] snapshot) {
        logger.debug("Restoring state machine snapshot: snapshotSize={}bytes", snapshot.length);
        try {
            QuorusSnapshot restoredSnapshot = objectMapper.readValue(snapshot, QuorusSnapshot.class);

            transferJobs.clear();
            transferJobs.putAll(restoredSnapshot.getTransferJobs());

            agents.clear();
            if (restoredSnapshot.getAgents() != null) {
                agents.putAll(restoredSnapshot.getAgents());
            }

            systemMetadata.clear();
            systemMetadata.putAll(restoredSnapshot.getSystemMetadata());

            jobAssignments.clear();
            if (restoredSnapshot.getJobAssignments() != null) {
                jobAssignments.putAll(restoredSnapshot.getJobAssignments());
            }

            jobQueue.clear();
            if (restoredSnapshot.getJobQueue() != null) {
                jobQueue.putAll(restoredSnapshot.getJobQueue());
            }

            lastAppliedIndex.set(restoredSnapshot.getLastAppliedIndex());

            logger.info("Restored snapshot: jobs={}, agents={}, metadata={}, assignments={}, queue={}, lastAppliedIndex={}", 
                transferJobs.size(), agents.size(), systemMetadata.size(), 
                jobAssignments.size(), jobQueue.size(), lastAppliedIndex.get());
        } catch (IOException e) {
            logger.error("Failed to restore snapshot: snapshotSize={}bytes, error={}", snapshot.length, e.getMessage());
            logger.trace("Stack trace for snapshot restore failure", e);
            throw new RuntimeException("Failed to restore snapshot", e);
        }
    }

    @Override
    public long getLastAppliedIndex() {
        return lastAppliedIndex.get();
    }

    @Override
    public void reset() {
        logger.debug("Resetting state machine: jobs={}, agents={}, metadata={}", 
            transferJobs.size(), agents.size(), systemMetadata.size());
        transferJobs.clear();
        agents.clear();
        systemMetadata.clear();
        jobAssignments.clear();
        jobQueue.clear();
        lastAppliedIndex.set(0);
        
        // Restore default metadata after clearing
        initializeDefaultMetadata();
        
        logger.info("State machine reset completed: version={}", systemMetadata.get("version"));
    }

    public Map<String, TransferJobSnapshot> getTransferJobs() {
        return new ConcurrentHashMap<>(transferJobs);
    }

    public TransferJobSnapshot getTransferJob(String jobId) {
        return transferJobs.get(jobId);
    }

    public Map<String, AgentInfo> getAgents() {
        return new ConcurrentHashMap<>(agents);
    }

    public AgentInfo getAgent(String agentId) {
        return agents.get(agentId);
    }

    public int getAgentCount() {
        return agents.size();
    }

    public Map<String, String> getSystemMetadata() {
        return new ConcurrentHashMap<>(systemMetadata);
    }

    public String getMetadata(String key) {
        return systemMetadata.get(key);
    }

    public int getTransferJobCount() {
        return transferJobs.size();
    }

    /**
     * Check if a transfer job exists in the state machine.
     */
    public boolean hasTransferJob(String jobId) {
        return transferJobs.containsKey(jobId);
    }

    /**
     * Update the last applied index.
     */
    public void setLastAppliedIndex(long index) {
        lastAppliedIndex.set(index);
    }

    /**
     * Get all job assignments.
     */
    public Map<String, JobAssignment> getJobAssignments() {
        return new ConcurrentHashMap<>(jobAssignments);
    }

    /**
     * Get a specific job assignment.
     */
    public JobAssignment getJobAssignment(String assignmentId) {
        return jobAssignments.get(assignmentId);
    }

    /**
     * Get all queued jobs.
     */
    public Map<String, QueuedJob> getJobQueue() {
        return new ConcurrentHashMap<>(jobQueue);
    }

    /**
     * Get a specific queued job.
     */
    public QueuedJob getQueuedJob(String jobId) {
        return jobQueue.get(jobId);
    }
}
