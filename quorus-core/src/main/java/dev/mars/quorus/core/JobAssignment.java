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

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents the assignment of a transfer job to a specific agent.
 * This class tracks the complete lifecycle of a job assignment from
 * initial assignment through completion or failure.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-10-28
 * @version 1.0
 */
@JsonDeserialize(builder = JobAssignment.Builder.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobAssignment implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final String jobId;
    private final String agentId;
    private final Instant assignedAt;
    private final Instant acceptedAt;
    private final Instant startedAt;
    private final Instant completedAt;
    private final JobAssignmentStatus status;
    private final String failureReason;
    private final int retryCount;
    private final long estimatedDurationMs;
    private final String assignmentStrategy;
    
    private JobAssignment(Builder builder) {
        this.jobId = Objects.requireNonNull(builder.jobId, "Job ID cannot be null");
        this.agentId = Objects.requireNonNull(builder.agentId, "Agent ID cannot be null");
        this.assignedAt = Objects.requireNonNull(builder.assignedAt, "Assigned timestamp cannot be null");
        this.acceptedAt = builder.acceptedAt;
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.status = Objects.requireNonNull(builder.status, "Status cannot be null");
        this.failureReason = builder.failureReason;
        this.retryCount = Math.max(0, builder.retryCount);
        this.estimatedDurationMs = Math.max(0, builder.estimatedDurationMs);
        this.assignmentStrategy = builder.assignmentStrategy;
    }
    
    public String getJobId() {
        return jobId;
    }
    
    public String getAgentId() {
        return agentId;
    }
    
    public Instant getAssignedAt() {
        return assignedAt;
    }
    
    public Instant getAcceptedAt() {
        return acceptedAt;
    }
    
    public Instant getStartedAt() {
        return startedAt;
    }
    
    public Instant getCompletedAt() {
        return completedAt;
    }
    
    public JobAssignmentStatus getStatus() {
        return status;
    }
    
    public String getFailureReason() {
        return failureReason;
    }
    
    public int getRetryCount() {
        return retryCount;
    }
    
    public long getEstimatedDurationMs() {
        return estimatedDurationMs;
    }
    
    public String getAssignmentStrategy() {
        return assignmentStrategy;
    }
    
    /**
     * Check if this assignment is in a terminal state (completed or failed).
     */
    public boolean isTerminal() {
        return status == JobAssignmentStatus.COMPLETED || 
               status == JobAssignmentStatus.FAILED ||
               status == JobAssignmentStatus.CANCELLED;
    }
    
    /**
     * Check if this assignment is currently active (assigned, accepted, or in progress).
     */
    public boolean isActive() {
        return status == JobAssignmentStatus.ASSIGNED ||
               status == JobAssignmentStatus.ACCEPTED ||
               status == JobAssignmentStatus.IN_PROGRESS;
    }
    
    /**
     * Get the total duration from assignment to completion (if completed).
     */
    public long getTotalDurationMs() {
        if (completedAt != null && assignedAt != null) {
            return completedAt.toEpochMilli() - assignedAt.toEpochMilli();
        }
        return -1;
    }
    
    /**
     * Get the execution duration from start to completion (if completed).
     */
    public long getExecutionDurationMs() {
        if (completedAt != null && startedAt != null) {
            return completedAt.toEpochMilli() - startedAt.toEpochMilli();
        }
        return -1;
    }
    
    /**
     * Create a new assignment with updated status.
     */
    public JobAssignment withStatus(JobAssignmentStatus newStatus) {
        return new Builder(this).status(newStatus).build();
    }
    
    /**
     * Create a new assignment with updated status and timestamp.
     */
    public JobAssignment withStatusAndTimestamp(JobAssignmentStatus newStatus, Instant timestamp) {
        Builder builder = new Builder(this).status(newStatus);

        switch (newStatus) {
            case ASSIGNED:
                // No additional timestamp needed for assignment
                break;
            case ACCEPTED:
                builder.acceptedAt(timestamp);
                break;
            case IN_PROGRESS:
                builder.startedAt(timestamp);
                break;
            case COMPLETED:
            case FAILED:
            case CANCELLED:
                builder.completedAt(timestamp);
                break;
            case REJECTED:
            case TIMEOUT:
                // These are terminal states but don't set completedAt
                break;
        }

        return builder.build();
    }
    
    /**
     * Create a new assignment with failure information.
     */
    public JobAssignment withFailure(String reason, Instant timestamp) {
        return new Builder(this)
                .status(JobAssignmentStatus.FAILED)
                .failureReason(reason)
                .completedAt(timestamp)
                .build();
    }
    
    /**
     * Create a new assignment with incremented retry count.
     */
    public JobAssignment withRetry() {
        return new Builder(this)
                .retryCount(retryCount + 1)
                .status(JobAssignmentStatus.ASSIGNED)
                .build();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobAssignment that = (JobAssignment) o;
        return Objects.equals(jobId, that.jobId) && Objects.equals(agentId, that.agentId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(jobId, agentId);
    }
    
    @Override
    public String toString() {
        return "JobAssignment{" +
                "jobId='" + jobId + '\'' +
                ", agentId='" + agentId + '\'' +
                ", status=" + status +
                ", assignedAt=" + assignedAt +
                ", retryCount=" + retryCount +
                '}';
    }
    
    /**
     * Builder for creating JobAssignment instances.
     */
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private String jobId;
        private String agentId;
        private Instant assignedAt;
        private Instant acceptedAt;
        private Instant startedAt;
        private Instant completedAt;
        private JobAssignmentStatus status;
        private String failureReason;
        private int retryCount;
        private long estimatedDurationMs;
        private String assignmentStrategy;
        
        public Builder() {
            this.assignedAt = Instant.now();
            this.status = JobAssignmentStatus.ASSIGNED;
            this.retryCount = 0;
        }
        
        public Builder(JobAssignment existing) {
            this.jobId = existing.jobId;
            this.agentId = existing.agentId;
            this.assignedAt = existing.assignedAt;
            this.acceptedAt = existing.acceptedAt;
            this.startedAt = existing.startedAt;
            this.completedAt = existing.completedAt;
            this.status = existing.status;
            this.failureReason = existing.failureReason;
            this.retryCount = existing.retryCount;
            this.estimatedDurationMs = existing.estimatedDurationMs;
            this.assignmentStrategy = existing.assignmentStrategy;
        }
        
        public Builder jobId(String jobId) {
            this.jobId = jobId;
            return this;
        }
        
        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }
        
        public Builder assignedAt(Instant assignedAt) {
            this.assignedAt = assignedAt;
            return this;
        }
        
        public Builder acceptedAt(Instant acceptedAt) {
            this.acceptedAt = acceptedAt;
            return this;
        }
        
        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }
        
        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }
        
        public Builder status(JobAssignmentStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder failureReason(String failureReason) {
            this.failureReason = failureReason;
            return this;
        }
        
        public Builder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }
        
        public Builder estimatedDurationMs(long estimatedDurationMs) {
            this.estimatedDurationMs = estimatedDurationMs;
            return this;
        }
        
        public Builder assignmentStrategy(String assignmentStrategy) {
            this.assignmentStrategy = assignmentStrategy;
            return this;
        }
        
        public JobAssignment build() {
            return new JobAssignment(this);
        }
    }
}
