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
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.JobAssignment;
import dev.mars.quorus.core.QueuedJob;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QuorusStateMachine implements RaftStateMachine {

    private static final Logger logger = Logger.getLogger(QuorusStateMachine.class.getName());

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

    public QuorusStateMachine() {
        // Initialize with system metadata
        systemMetadata.put("version", "2.0");
        systemMetadata.put("phase", "2.3 - Agent Fleet Management");
    }

    @Override
    public Object apply(Object command) {
        if (command == null) {
            return null; // No-op command
        }

        try {
            if (command instanceof TransferJobCommand) {
                return applyTransferJobCommand((TransferJobCommand) command);
            } else if (command instanceof AgentCommand) {
                return applyAgentCommand((AgentCommand) command);
            } else if (command instanceof SystemMetadataCommand) {
                return applySystemMetadataCommand((SystemMetadataCommand) command);
            } else if (command instanceof JobAssignmentCommand) {
                return applyJobAssignmentCommand((JobAssignmentCommand) command);
            } else if (command instanceof JobQueueCommand) {
                return applyJobQueueCommand((JobQueueCommand) command);
            } else {
                logger.warning("Unknown command type: " + command.getClass().getName());
                return null;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to apply command: " + command, e);
            throw new RuntimeException("Failed to apply command", e);
        }
    }

    private Object applyTransferJobCommand(TransferJobCommand command) {
        String jobId = command.getJobId();
        
        switch (command.getType()) {
            case CREATE:
                TransferJob job = command.getTransferJob();
                TransferJobSnapshot snapshot = TransferJobSnapshot.fromTransferJob(job);
                transferJobs.put(jobId, snapshot);
                logger.info("Created transfer job: " + jobId);
                return job;
                
            case UPDATE_STATUS:
                TransferJobSnapshot existingJob = transferJobs.get(jobId);
                if (existingJob != null) {
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
                            existingJob.getDescription()
                    );
                    transferJobs.put(jobId, updatedJob);
                    logger.info("Updated transfer job status: " + jobId + " -> " + command.getStatus());
                    return updatedJob;
                } else {
                    logger.warning("Transfer job not found for update: " + jobId);
                    return null;
                }
                
            case DELETE:
                TransferJobSnapshot removedJob = transferJobs.remove(jobId);
                if (removedJob != null) {
                    logger.info("Deleted transfer job: " + jobId);
                    return removedJob;
                } else {
                    logger.warning("Transfer job not found for deletion: " + jobId);
                    return null;
                }
                
            default:
                logger.warning("Unknown transfer job command type: " + command.getType());
                return null;
        }
    }

    private Object applyAgentCommand(AgentCommand command) {
        String agentId = command.getAgentId();

        switch (command.getType()) {
            case REGISTER:
                AgentInfo agentInfo = command.getAgentInfo();
                agents.put(agentId, agentInfo);
                logger.info("Registered agent: " + agentId + " at " + agentInfo.getEndpoint());
                return agentInfo;

            case DEREGISTER:
                AgentInfo removedAgent = agents.remove(agentId);
                if (removedAgent != null) {
                    logger.info("Deregistered agent: " + agentId);
                    return removedAgent;
                } else {
                    logger.warning("Agent not found for deregistration: " + agentId);
                    return null;
                }

            case UPDATE_STATUS:
                AgentInfo existingAgent = agents.get(agentId);
                if (existingAgent != null) {
                    AgentStatus newStatus = command.getNewStatus();
                    existingAgent.setStatus(newStatus);
                    existingAgent.setLastHeartbeat(Instant.now());
                    agents.put(agentId, existingAgent);
                    logger.info("Updated agent status: " + agentId + " -> " + newStatus);
                    return existingAgent;
                } else {
                    logger.warning("Agent not found for status update: " + agentId);
                    return null;
                }

            case UPDATE_CAPABILITIES:
                AgentInfo agentToUpdate = agents.get(agentId);
                if (agentToUpdate != null) {
                    AgentCapabilities newCapabilities = command.getNewCapabilities();
                    agentToUpdate.setCapabilities(newCapabilities);
                    agentToUpdate.setLastHeartbeat(Instant.now());
                    agents.put(agentId, agentToUpdate);
                    logger.info("Updated agent capabilities: " + agentId);
                    return agentToUpdate;
                } else {
                    logger.warning("Agent not found for capabilities update: " + agentId);
                    return null;
                }

            case HEARTBEAT:
                AgentInfo agentForHeartbeat = agents.get(agentId);
                if (agentForHeartbeat != null) {
                    agentForHeartbeat.setLastHeartbeat(Instant.now());
                    // Update status to HEALTHY if it was in a transitional state
                    if (agentForHeartbeat.getStatus() == AgentStatus.REGISTERING) {
                        agentForHeartbeat.setStatus(AgentStatus.HEALTHY);
                    }
                    agents.put(agentId, agentForHeartbeat);
                    logger.fine("Heartbeat received from agent: " + agentId);
                    return agentForHeartbeat;
                } else {
                    logger.warning("Agent not found for heartbeat: " + agentId);
                    return null;
                }

            default:
                logger.warning("Unknown agent command type: " + command.getType());
                return null;
        }
    }

    private Object applySystemMetadataCommand(SystemMetadataCommand command) {
        String key = command.getKey();

        switch (command.getType()) {
            case SET:
                String oldValue = systemMetadata.put(key, command.getValue());
                logger.info("Set system metadata: " + key + " = " + command.getValue());
                return oldValue;

            case DELETE:
                String removedValue = systemMetadata.remove(key);
                logger.info("Deleted system metadata: " + key);
                return removedValue;

            default:
                logger.warning("Unknown system metadata command type: " + command.getType());
                return null;
        }
    }

    private Object applyJobAssignmentCommand(JobAssignmentCommand command) {
        String assignmentId = command.getAssignmentId();

        switch (command.getType()) {
            case ASSIGN:
                JobAssignment assignment = command.getJobAssignment();
                jobAssignments.put(assignmentId, assignment);
                logger.info("Created job assignment: " + assignmentId + " (job: " +
                           assignment.getJobId() + " -> agent: " + assignment.getAgentId() + ")");
                return assignment;

            case ACCEPT:
                JobAssignment existing = jobAssignments.get(assignmentId);
                if (existing != null) {
                    JobAssignment updated = existing.withStatusAndTimestamp(command.getNewStatus(), command.getTimestamp());
                    jobAssignments.put(assignmentId, updated);
                    logger.info("Job assignment accepted: " + assignmentId);
                    return updated;
                } else {
                    logger.warning("Job assignment not found for accept: " + assignmentId);
                    return null;
                }

            case REJECT:
                JobAssignment rejectedAssignment = jobAssignments.get(assignmentId);
                if (rejectedAssignment != null) {
                    JobAssignment updated = rejectedAssignment.withStatusAndTimestamp(command.getNewStatus(), command.getTimestamp());
                    jobAssignments.put(assignmentId, updated);
                    logger.info("Job assignment rejected: " + assignmentId +
                               (command.getReason() != null ? " (reason: " + command.getReason() + ")" : ""));
                    return updated;
                } else {
                    logger.warning("Job assignment not found for reject: " + assignmentId);
                    return null;
                }

            case UPDATE_STATUS:
                JobAssignment statusAssignment = jobAssignments.get(assignmentId);
                if (statusAssignment != null) {
                    JobAssignment updated = statusAssignment.withStatusAndTimestamp(command.getNewStatus(), command.getTimestamp());
                    jobAssignments.put(assignmentId, updated);
                    logger.info("Updated job assignment status: " + assignmentId + " -> " + command.getNewStatus());
                    return updated;
                } else {
                    logger.warning("Job assignment not found for status update: " + assignmentId);
                    return null;
                }

            case TIMEOUT:
                JobAssignment timeoutAssignment = jobAssignments.get(assignmentId);
                if (timeoutAssignment != null) {
                    JobAssignment updated = timeoutAssignment.withStatusAndTimestamp(command.getNewStatus(), command.getTimestamp());
                    jobAssignments.put(assignmentId, updated);
                    logger.info("Job assignment timed out: " + assignmentId);
                    return updated;
                } else {
                    logger.warning("Job assignment not found for timeout: " + assignmentId);
                    return null;
                }

            case CANCEL:
                JobAssignment cancelAssignment = jobAssignments.get(assignmentId);
                if (cancelAssignment != null) {
                    JobAssignment updated = cancelAssignment.withStatusAndTimestamp(command.getNewStatus(), command.getTimestamp());
                    jobAssignments.put(assignmentId, updated);
                    logger.info("Job assignment cancelled: " + assignmentId +
                               (command.getReason() != null ? " (reason: " + command.getReason() + ")" : ""));
                    return updated;
                } else {
                    logger.warning("Job assignment not found for cancel: " + assignmentId);
                    return null;
                }

            case REMOVE:
                JobAssignment removed = jobAssignments.remove(assignmentId);
                if (removed != null) {
                    logger.info("Removed job assignment: " + assignmentId);
                    return removed;
                } else {
                    logger.warning("Job assignment not found for removal: " + assignmentId);
                    return null;
                }

            default:
                logger.warning("Unknown job assignment command type: " + command.getType());
                return null;
        }
    }

    private Object applyJobQueueCommand(JobQueueCommand command) {
        String jobId = command.getJobId();

        switch (command.getType()) {
            case ENQUEUE:
                QueuedJob queuedJob = command.getQueuedJob();
                jobQueue.put(jobId, queuedJob);
                logger.info("Enqueued job: " + jobId + " (priority: " + queuedJob.getPriority() + ")");
                return queuedJob;

            case DEQUEUE:
                QueuedJob dequeuedJob = jobQueue.remove(jobId);
                if (dequeuedJob != null) {
                    logger.info("Dequeued job: " + jobId + " for assignment");
                    return dequeuedJob;
                } else {
                    logger.warning("Job not found in queue for dequeue: " + jobId);
                    return null;
                }

            case PRIORITIZE:
                QueuedJob existingJob = jobQueue.get(jobId);
                if (existingJob != null) {
                    QueuedJob updatedJob = existingJob.withPriority(command.getNewPriority());
                    jobQueue.put(jobId, updatedJob);
                    logger.info("Updated job priority: " + jobId + " -> " + command.getNewPriority() +
                               (command.getReason() != null ? " (reason: " + command.getReason() + ")" : ""));
                    return updatedJob;
                } else {
                    logger.warning("Job not found in queue for prioritize: " + jobId);
                    return null;
                }

            case REMOVE:
                QueuedJob removedJob = jobQueue.remove(jobId);
                if (removedJob != null) {
                    logger.info("Removed job from queue: " + jobId +
                               (command.getReason() != null ? " (reason: " + command.getReason() + ")" : ""));
                    return removedJob;
                } else {
                    logger.warning("Job not found in queue for removal: " + jobId);
                    return null;
                }

            case EXPEDITE:
                QueuedJob expediteJob = jobQueue.get(jobId);
                if (expediteJob != null) {
                    // For expedite, we could implement special logic to move to front
                    // For now, we'll just log it - actual queue ordering would be handled by the queue service
                    logger.info("Expedited job: " + jobId +
                               (command.getReason() != null ? " (reason: " + command.getReason() + ")" : ""));
                    return expediteJob;
                } else {
                    logger.warning("Job not found in queue for expedite: " + jobId);
                    return null;
                }

            case UPDATE_REQUIREMENTS:
                QueuedJob updatedJob = command.getQueuedJob();
                jobQueue.put(jobId, updatedJob);
                logger.info("Updated job requirements: " + jobId);
                return updatedJob;

            default:
                logger.warning("Unknown job queue command type: " + command.getType());
                return null;
        }
    }

    @Override
    public byte[] takeSnapshot() {
        try {
            QuorusSnapshot snapshot = new QuorusSnapshot();
            snapshot.setTransferJobs(new ConcurrentHashMap<>(transferJobs));
            snapshot.setAgents(new ConcurrentHashMap<>(agents));
            snapshot.setSystemMetadata(new ConcurrentHashMap<>(systemMetadata));
            snapshot.setLastAppliedIndex(lastAppliedIndex.get());

            byte[] data = objectMapper.writeValueAsBytes(snapshot);
            logger.info("Created snapshot with " + transferJobs.size() + " transfer jobs and " + agents.size() + " agents");
            return data;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to create snapshot", e);
            throw new RuntimeException("Failed to create snapshot", e);
        }
    }

    @Override
    public void restoreSnapshot(byte[] snapshot) {
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

            lastAppliedIndex.set(restoredSnapshot.getLastAppliedIndex());

            logger.info("Restored snapshot with " + transferJobs.size() + " transfer jobs and " + agents.size() + " agents");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to restore snapshot", e);
            throw new RuntimeException("Failed to restore snapshot", e);
        }
    }

    @Override
    public long getLastAppliedIndex() {
        return lastAppliedIndex.get();
    }

    @Override
    public void reset() {
        transferJobs.clear();
        agents.clear();
        systemMetadata.clear();
        systemMetadata.put("version", "2.0");
        systemMetadata.put("phase", "2.3 - Agent Fleet Management");
        lastAppliedIndex.set(0);
        logger.info("State machine reset");
    }

    public Map<String, TransferJobSnapshot> getTransferJobs() {
        return new ConcurrentHashMap<>(transferJobs);
    }

    public TransferJobSnapshot getTransferJob(String jobId) {
        return transferJobs.get(jobId);
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
}
