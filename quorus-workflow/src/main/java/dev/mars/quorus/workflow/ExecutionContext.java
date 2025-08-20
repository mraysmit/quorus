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

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public class ExecutionContext {
    
    private final String executionId;
    private final Instant startTime;
    private final Map<String, Object> variables;
    private final ExecutionMode mode;
    private final String userId;
    private final Map<String, String> metadata;
    
    public ExecutionContext(String executionId, ExecutionMode mode, Map<String, Object> variables, 
                           String userId, Map<String, String> metadata) {
        this.executionId = Objects.requireNonNull(executionId, "Execution ID cannot be null");
        this.mode = Objects.requireNonNull(mode, "Execution mode cannot be null");
        this.startTime = Instant.now();
        this.variables = variables != null ? Map.copyOf(variables) : Map.of();
        this.userId = userId;
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
    
    public String getExecutionId() {
        return executionId;
    }
    
    public Instant getStartTime() {
        return startTime;
    }
    
    public Map<String, Object> getVariables() {
        return variables;
    }
    
    public ExecutionMode getMode() {
        return mode;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public ExecutionContext withVariables(Map<String, Object> additionalVariables) {
        if (additionalVariables == null || additionalVariables.isEmpty()) {
            return this;
        }
        
        Map<String, Object> mergedVariables = new java.util.HashMap<>(this.variables);
        mergedVariables.putAll(additionalVariables);
        
        return new ExecutionContext(executionId, mode, mergedVariables, userId, metadata);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionContext that = (ExecutionContext) o;
        return Objects.equals(executionId, that.executionId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(executionId);
    }
    
    @Override
    public String toString() {
        return "ExecutionContext{" +
               "executionId='" + executionId + '\'' +
               ", mode=" + mode +
               ", startTime=" + startTime +
               ", userId='" + userId + '\'' +
               '}';
    }
    
    public enum ExecutionMode {
        NORMAL,     // Normal execution with actual transfers
        DRY_RUN,    // Validation only, no actual transfers
        VIRTUAL_RUN // Simulated execution with mock transfers
    }
    
    /**
     * Builder for ExecutionContext.
     */
    public static class Builder {
        private String executionId;
        private ExecutionMode mode = ExecutionMode.NORMAL;
        private Map<String, Object> variables = Map.of();
        private String userId;
        private Map<String, String> metadata = Map.of();
        
        public Builder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }
        
        public Builder mode(ExecutionMode mode) {
            this.mode = mode;
            return this;
        }
        
        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }
        
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }
        
        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public ExecutionContext build() {
            if (executionId == null) {
                executionId = java.util.UUID.randomUUID().toString();
            }
            return new ExecutionContext(executionId, mode, variables, userId, metadata);
        }
    }
}
