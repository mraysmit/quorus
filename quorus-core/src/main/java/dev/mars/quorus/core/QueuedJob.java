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

/**
 * Represents a transfer job that is queued for assignment to an agent.
 * This class wraps a TransferJob with additional queuing metadata such as
 * priority, queue time, and assignment requirements.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-10-28
 * @version 1.0
 */
public class QueuedJob implements Serializable, Comparable<QueuedJob> {
    
    private static final long serialVersionUID = 1L;
    
    private final TransferJob transferJob;
    private final JobPriority priority;
    private final Instant queueTime;
    private final JobRequirements requirements;
    private final String submittedBy;
    private final String workflowId;
    private final String groupName;
    private final int retryCount;
    private final Instant earliestStartTime;
    
    private QueuedJob(Builder builder) {
        this.transferJob = Objects.requireNonNull(builder.transferJob, "Transfer job cannot be null");
        this.priority = Objects.requireNonNull(builder.priority, "Priority cannot be null");
        this.queueTime = Objects.requireNonNull(builder.queueTime, "Queue time cannot be null");
        this.requirements = builder.requirements != null ? builder.requirements : new JobRequirements.Builder().build();
        this.submittedBy = builder.submittedBy;
        this.workflowId = builder.workflowId;
        this.groupName = builder.groupName;
        this.retryCount = Math.max(0, builder.retryCount);
        this.earliestStartTime = builder.earliestStartTime;
    }
    
    public TransferJob getTransferJob() {
        return transferJob;
    }
    
    public JobPriority getPriority() {
        return priority;
    }
    
    public Instant getQueueTime() {
        return queueTime;
    }
    
    public JobRequirements getRequirements() {
        return requirements;
    }
    
    public String getSubmittedBy() {
        return submittedBy;
    }
    
    public String getWorkflowId() {
        return workflowId;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    public int getRetryCount() {
        return retryCount;
    }
    
    public Instant getEarliestStartTime() {
        return earliestStartTime;
    }
    
    /**
     * Get the job ID from the wrapped transfer job.
     */
    public String getJobId() {
        return transferJob.getJobId();
    }
    
    /**
     * Get how long this job has been waiting in the queue.
     */
    public long getQueueWaitTimeMs() {
        return Instant.now().toEpochMilli() - queueTime.toEpochMilli();
    }
    
    /**
     * Check if this job is ready to be assigned (past earliest start time).
     */
    public boolean isReadyForAssignment() {
        if (earliestStartTime == null) {
            return true;
        }
        return Instant.now().isAfter(earliestStartTime);
    }
    
    /**
     * Check if this job is part of a workflow.
     */
    public boolean isWorkflowJob() {
        return workflowId != null && !workflowId.trim().isEmpty();
    }
    
    /**
     * Create a new queued job with updated priority.
     */
    public QueuedJob withPriority(JobPriority newPriority) {
        return new Builder(this).priority(newPriority).build();
    }
    
    /**
     * Create a new queued job with incremented retry count.
     */
    public QueuedJob withRetry() {
        return new Builder(this).retryCount(retryCount + 1).build();
    }
    
    /**
     * Create a new queued job with updated requirements.
     */
    public QueuedJob withRequirements(JobRequirements newRequirements) {
        return new Builder(this).requirements(newRequirements).build();
    }
    
    /**
     * Compare queued jobs for priority ordering.
     * Higher priority jobs come first, then FIFO within same priority.
     */
    @Override
    public int compareTo(QueuedJob other) {
        // First compare by priority (higher priority first)
        int priorityComparison = Integer.compare(other.priority.getValue(), this.priority.getValue());
        if (priorityComparison != 0) {
            return priorityComparison;
        }
        
        // Then compare by queue time (earlier time first - FIFO)
        return this.queueTime.compareTo(other.queueTime);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueuedJob queuedJob = (QueuedJob) o;
        return Objects.equals(transferJob.getJobId(), queuedJob.transferJob.getJobId());
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(transferJob.getJobId());
    }
    
    @Override
    public String toString() {
        return "QueuedJob{" +
                "jobId='" + getJobId() + '\'' +
                ", priority=" + priority +
                ", queueTime=" + queueTime +
                ", retryCount=" + retryCount +
                ", workflowId='" + workflowId + '\'' +
                '}';
    }
    
    /**
     * Builder for creating QueuedJob instances.
     */
    public static class Builder {
        private TransferJob transferJob;
        private JobPriority priority;
        private Instant queueTime;
        private JobRequirements requirements;
        private String submittedBy;
        private String workflowId;
        private String groupName;
        private int retryCount;
        private Instant earliestStartTime;
        
        public Builder() {
            this.priority = JobPriority.NORMAL;
            this.queueTime = Instant.now();
            this.retryCount = 0;
        }
        
        public Builder(QueuedJob existing) {
            this.transferJob = existing.transferJob;
            this.priority = existing.priority;
            this.queueTime = existing.queueTime;
            this.requirements = existing.requirements;
            this.submittedBy = existing.submittedBy;
            this.workflowId = existing.workflowId;
            this.groupName = existing.groupName;
            this.retryCount = existing.retryCount;
            this.earliestStartTime = existing.earliestStartTime;
        }
        
        public Builder transferJob(TransferJob transferJob) {
            this.transferJob = transferJob;
            return this;
        }
        
        public Builder priority(JobPriority priority) {
            this.priority = priority;
            return this;
        }
        
        public Builder queueTime(Instant queueTime) {
            this.queueTime = queueTime;
            return this;
        }
        
        public Builder requirements(JobRequirements requirements) {
            this.requirements = requirements;
            return this;
        }
        
        public Builder submittedBy(String submittedBy) {
            this.submittedBy = submittedBy;
            return this;
        }
        
        public Builder workflowId(String workflowId) {
            this.workflowId = workflowId;
            return this;
        }
        
        public Builder groupName(String groupName) {
            this.groupName = groupName;
            return this;
        }
        
        public Builder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }
        
        public Builder earliestStartTime(Instant earliestStartTime) {
            this.earliestStartTime = earliestStartTime;
            return this;
        }
        
        public QueuedJob build() {
            return new QueuedJob(this);
        }
    }
}
