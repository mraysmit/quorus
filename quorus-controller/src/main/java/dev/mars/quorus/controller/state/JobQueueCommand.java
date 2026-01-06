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

import dev.mars.quorus.core.QueuedJob;
import dev.mars.quorus.core.JobPriority;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Command for job queue operations in the Raft state machine.
 * This class represents commands that can be submitted to the distributed controller
 * for managing the job queue and job prioritization.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-10-28
 * @version 1.0
 */
public class JobQueueCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Types of job queue commands.
     */
    public enum CommandType {
        /** Add a job to the queue */
        ENQUEUE,
        /** Remove a job from the queue (for assignment) */
        DEQUEUE,
        /** Change job priority */
        PRIORITIZE,
        /** Remove a job from the queue (cancel/delete) */
        REMOVE,
        /** Move job to front of queue */
        EXPEDITE,
        /** Update job requirements */
        UPDATE_REQUIREMENTS
    }

    private final CommandType type;
    private final String jobId;
    private final QueuedJob queuedJob;
    private final JobPriority newPriority;
    private final String reason;
    private final Instant timestamp;

    /**
     * Private constructor for creating commands.
     */
    private JobQueueCommand(CommandType type, String jobId, QueuedJob queuedJob,
                           JobPriority newPriority, String reason) {
        this.type = Objects.requireNonNull(type, "Command type cannot be null");
        this.jobId = jobId;
        this.queuedJob = queuedJob;
        this.newPriority = newPriority;
        this.reason = reason;
        this.timestamp = Instant.now();
        
        // Validate command parameters
        validateCommand();
    }

    /**
     * Create a command to add a job to the queue.
     * 
     * @param queuedJob the job to add to the queue
     * @return the command
     * @throws IllegalArgumentException if queuedJob is null
     */
    public static JobQueueCommand enqueue(QueuedJob queuedJob) {
        Objects.requireNonNull(queuedJob, "Queued job cannot be null");
        return new JobQueueCommand(CommandType.ENQUEUE, queuedJob.getJobId(), 
                                  queuedJob, null, null);
    }

    /**
     * Create a command to remove a job from the queue for assignment.
     * 
     * @param jobId the job ID to dequeue
     * @return the command
     * @throws IllegalArgumentException if jobId is null or empty
     */
    public static JobQueueCommand dequeue(String jobId) {
        validateJobId(jobId);
        return new JobQueueCommand(CommandType.DEQUEUE, jobId, null, null, "Job assigned to agent");
    }

    /**
     * Create a command to change job priority.
     * 
     * @param jobId the job ID to prioritize
     * @param newPriority the new priority level
     * @param reason the reason for priority change (optional)
     * @return the command
     * @throws IllegalArgumentException if jobId is null/empty or newPriority is null
     */
    public static JobQueueCommand prioritize(String jobId, JobPriority newPriority, String reason) {
        validateJobId(jobId);
        Objects.requireNonNull(newPriority, "New priority cannot be null");
        return new JobQueueCommand(CommandType.PRIORITIZE, jobId, null, newPriority, reason);
    }

    /**
     * Create a command to remove a job from the queue (cancel/delete).
     * 
     * @param jobId the job ID to remove
     * @param reason the reason for removal
     * @return the command
     * @throws IllegalArgumentException if jobId is null or empty
     */
    public static JobQueueCommand remove(String jobId, String reason) {
        validateJobId(jobId);
        return new JobQueueCommand(CommandType.REMOVE, jobId, null, null, reason);
    }

    /**
     * Create a command to expedite a job (move to front of queue).
     * 
     * @param jobId the job ID to expedite
     * @param reason the reason for expediting
     * @return the command
     * @throws IllegalArgumentException if jobId is null or empty
     */
    public static JobQueueCommand expedite(String jobId, String reason) {
        validateJobId(jobId);
        return new JobQueueCommand(CommandType.EXPEDITE, jobId, null, null, reason);
    }

    /**
     * Create a command to update job requirements.
     * 
     * @param queuedJob the updated queued job with new requirements
     * @return the command
     * @throws IllegalArgumentException if queuedJob is null
     */
    public static JobQueueCommand updateRequirements(QueuedJob queuedJob) {
        Objects.requireNonNull(queuedJob, "Queued job cannot be null");
        return new JobQueueCommand(CommandType.UPDATE_REQUIREMENTS, queuedJob.getJobId(), 
                                  queuedJob, null, "Job requirements updated");
    }

    // Getters
    public CommandType getType() {
        return type;
    }

    public String getJobId() {
        return jobId;
    }

    public QueuedJob getQueuedJob() {
        return queuedJob;
    }

    public JobPriority getNewPriority() {
        return newPriority;
    }

    public String getReason() {
        return reason;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    // Validation methods
    private static void validateJobId(String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) {
            throw new IllegalArgumentException("Job ID cannot be null or empty");
        }
    }

    private void validateCommand() {
        switch (type) {
            case ENQUEUE:
            case UPDATE_REQUIREMENTS:
                if (queuedJob == null) {
                    throw new IllegalArgumentException("Queued job is required for " + type + " command");
                }
                if (jobId == null || !jobId.equals(queuedJob.getJobId())) {
                    throw new IllegalArgumentException("Job ID must match queued job ID");
                }
                break;
                
            case DEQUEUE:
            case REMOVE:
            case EXPEDITE:
                if (jobId == null || jobId.trim().isEmpty()) {
                    throw new IllegalArgumentException("Job ID is required for " + type + " command");
                }
                break;
                
            case PRIORITIZE:
                if (jobId == null || jobId.trim().isEmpty()) {
                    throw new IllegalArgumentException("Job ID is required for PRIORITIZE command");
                }
                if (newPriority == null) {
                    throw new IllegalArgumentException("New priority is required for PRIORITIZE command");
                }
                break;
                
            default:
                throw new IllegalArgumentException("Unknown command type: " + type);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobQueueCommand that = (JobQueueCommand) o;
        return type == that.type &&
               Objects.equals(jobId, that.jobId) &&
               Objects.equals(queuedJob, that.queuedJob) &&
               newPriority == that.newPriority &&
               Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, jobId, queuedJob, newPriority, reason);
    }

    @Override
    public String toString() {
        return "JobQueueCommand{" +
                "type=" + type +
                ", jobId='" + jobId + '\'' +
                ", newPriority=" + newPriority +
                ", reason='" + reason + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
