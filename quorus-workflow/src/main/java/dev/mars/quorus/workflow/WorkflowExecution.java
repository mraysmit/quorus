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

package dev.mars.quorus.workflow;

import dev.mars.quorus.core.TransferResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class WorkflowExecution {
    
    private final String executionId;
    private final WorkflowDefinition definition;
    private final ExecutionContext context;
    private final WorkflowStatus status;
    private final Instant startTime;
    private final Instant endTime;
    private final List<GroupExecution> groupExecutions;
    private final String errorMessage;
    private final Throwable cause;
    
    public WorkflowExecution(String executionId, WorkflowDefinition definition, ExecutionContext context,
                           WorkflowStatus status, Instant startTime, Instant endTime,
                           List<GroupExecution> groupExecutions, String errorMessage, Throwable cause) {
        this.executionId = Objects.requireNonNull(executionId, "Execution ID cannot be null");
        this.definition = Objects.requireNonNull(definition, "Workflow definition cannot be null");
        this.context = Objects.requireNonNull(context, "Execution context cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.startTime = Objects.requireNonNull(startTime, "Start time cannot be null");
        this.endTime = endTime;
        this.groupExecutions = groupExecutions != null ? List.copyOf(groupExecutions) : List.of();
        this.errorMessage = errorMessage;
        this.cause = cause;
    }
    
    public String getExecutionId() {
        return executionId;
    }
    
    public WorkflowDefinition getDefinition() {
        return definition;
    }
    
    public ExecutionContext getContext() {
        return context;
    }
    
    public WorkflowStatus getStatus() {
        return status;
    }
    
    public Instant getStartTime() {
        return startTime;
    }
    
    public Optional<Instant> getEndTime() {
        return Optional.ofNullable(endTime);
    }
    
    public Optional<Duration> getDuration() {
        return endTime != null ? Optional.of(Duration.between(startTime, endTime)) : Optional.empty();
    }
    
    public List<GroupExecution> getGroupExecutions() {
        return groupExecutions;
    }
    
    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }
    
    public Optional<Throwable> getCause() {
        return Optional.ofNullable(cause);
    }
    
    public boolean isSuccessful() {
        return status == WorkflowStatus.COMPLETED;
    }
    
    public boolean isRunning() {
        return status == WorkflowStatus.RUNNING || status == WorkflowStatus.PAUSED;
    }
    
    public boolean isCompleted() {
        return status == WorkflowStatus.COMPLETED || status == WorkflowStatus.FAILED || status == WorkflowStatus.CANCELLED;
    }
    
    public int getTotalTransferCount() {
        return groupExecutions.stream()
                .mapToInt(group -> group.getTransferResults().size())
                .sum();
    }
    
    public int getSuccessfulTransferCount() {
        return groupExecutions.stream()
                .mapToInt(group -> (int) group.getTransferResults().values().stream()
                        .filter(TransferResult::isSuccessful)
                        .count())
                .sum();
    }
    
    public long getTotalBytesTransferred() {
        return groupExecutions.stream()
                .mapToLong(group -> group.getTransferResults().values().stream()
                        .mapToLong(TransferResult::getBytesTransferred)
                        .sum())
                .sum();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowExecution that = (WorkflowExecution) o;
        return Objects.equals(executionId, that.executionId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(executionId);
    }
    
    @Override
    public String toString() {
        return "WorkflowExecution{" +
               "executionId='" + executionId + '\'' +
               ", status=" + status +
               ", startTime=" + startTime +
               ", endTime=" + endTime +
               ", groupCount=" + groupExecutions.size() +
               '}';
    }
    
    /**
     * Represents the execution result of a single transfer group.
     */
    public static class GroupExecution {
        private final String groupName;
        private final WorkflowStatus status;
        private final Instant startTime;
        private final Instant endTime;
        private final Map<String, TransferResult> transferResults;
        private final String errorMessage;
        
        public GroupExecution(String groupName, WorkflowStatus status, Instant startTime, Instant endTime,
                            Map<String, TransferResult> transferResults, String errorMessage) {
            this.groupName = Objects.requireNonNull(groupName, "Group name cannot be null");
            this.status = Objects.requireNonNull(status, "Status cannot be null");
            this.startTime = Objects.requireNonNull(startTime, "Start time cannot be null");
            this.endTime = endTime;
            this.transferResults = transferResults != null ? Map.copyOf(transferResults) : Map.of();
            this.errorMessage = errorMessage;
        }
        
        public String getGroupName() {
            return groupName;
        }
        
        public WorkflowStatus getStatus() {
            return status;
        }
        
        public Instant getStartTime() {
            return startTime;
        }
        
        public Optional<Instant> getEndTime() {
            return Optional.ofNullable(endTime);
        }
        
        public Optional<Duration> getDuration() {
            return endTime != null ? Optional.of(Duration.between(startTime, endTime)) : Optional.empty();
        }
        
        public Map<String, TransferResult> getTransferResults() {
            return transferResults;
        }
        
        public Optional<String> getErrorMessage() {
            return Optional.ofNullable(errorMessage);
        }
        
        public boolean isSuccessful() {
            return status == WorkflowStatus.COMPLETED && 
                   transferResults.values().stream().allMatch(TransferResult::isSuccessful);
        }
        
        @Override
        public String toString() {
            return "GroupExecution{" +
                   "groupName='" + groupName + '\'' +
                   ", status=" + status +
                   ", transferCount=" + transferResults.size() +
                   '}';
        }
    }
}
