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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class WorkflowDefinition {
    
    private final String apiVersion;
    private final String kind;
    private final WorkflowMetadata metadata;
    private final WorkflowSpec spec;
    
    public WorkflowDefinition(String apiVersion, String kind, WorkflowMetadata metadata, WorkflowSpec spec) {
        this.apiVersion = Objects.requireNonNull(apiVersion, "API version cannot be null");
        this.kind = Objects.requireNonNull(kind, "Kind cannot be null");
        this.metadata = Objects.requireNonNull(metadata, "Metadata cannot be null");
        this.spec = Objects.requireNonNull(spec, "Spec cannot be null");
    }
    
    public String getApiVersion() {
        return apiVersion;
    }
    
    public String getKind() {
        return kind;
    }
    
    public WorkflowMetadata getMetadata() {
        return metadata;
    }
    
    public WorkflowSpec getSpec() {
        return spec;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowDefinition that = (WorkflowDefinition) o;
        return Objects.equals(apiVersion, that.apiVersion) &&
               Objects.equals(kind, that.kind) &&
               Objects.equals(metadata, that.metadata) &&
               Objects.equals(spec, that.spec);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(apiVersion, kind, metadata, spec);
    }
    
    @Override
    public String toString() {
        return "WorkflowDefinition{" +
               "apiVersion='" + apiVersion + '\'' +
               ", kind='" + kind + '\'' +
               ", metadata=" + metadata +
               ", spec=" + spec +
               '}';
    }
    
    public static class WorkflowMetadata {
        private final String name;
        private final String version;
        private final String description;
        private final String type;
        private final String author;
        private final Map<String, String> labels;

        public WorkflowMetadata(String name, String version, String description, String type, String author, Map<String, String> labels) {
            this.name = Objects.requireNonNull(name, "Name cannot be null");
            this.version = version;
            this.description = description;
            this.type = type;
            this.author = author;
            this.labels = labels != null ? Map.copyOf(labels) : Map.of();
        }

        // Backward compatibility constructor
        public WorkflowMetadata(String name, String description, Map<String, String> labels) {
            this(name, "1.0.0", description, "workflow", null, labels);
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }

        public String getDescription() {
            return description;
        }

        public String getType() {
            return type;
        }

        public String getAuthor() {
            return author;
        }

        public Map<String, String> getLabels() {
            return labels;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WorkflowMetadata that = (WorkflowMetadata) o;
            return Objects.equals(name, that.name) &&
                   Objects.equals(version, that.version) &&
                   Objects.equals(description, that.description) &&
                   Objects.equals(type, that.type) &&
                   Objects.equals(author, that.author) &&
                   Objects.equals(labels, that.labels);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, version, description, type, author, labels);
        }
        
        @Override
        public String toString() {
            return "WorkflowMetadata{" +
                   "name='" + name + '\'' +
                   ", version='" + version + '\'' +
                   ", description='" + description + '\'' +
                   ", type='" + type + '\'' +
                   ", author='" + author + '\'' +
                   ", labels=" + labels +
                   '}';
        }
    }
    
    public static class WorkflowSpec {
        private final Map<String, Object> variables;
        private final ExecutionConfig execution;
        private final List<TransferGroup> transferGroups;
        
        public WorkflowSpec(Map<String, Object> variables, ExecutionConfig execution, List<TransferGroup> transferGroups) {
            this.variables = variables != null ? Map.copyOf(variables) : Map.of();
            this.execution = Objects.requireNonNull(execution, "Execution config cannot be null");
            this.transferGroups = transferGroups != null ? List.copyOf(transferGroups) : List.of();
        }
        
        public Map<String, Object> getVariables() {
            return variables;
        }
        
        public ExecutionConfig getExecution() {
            return execution;
        }
        
        public List<TransferGroup> getTransferGroups() {
            return transferGroups;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WorkflowSpec that = (WorkflowSpec) o;
            return Objects.equals(variables, that.variables) &&
                   Objects.equals(execution, that.execution) &&
                   Objects.equals(transferGroups, that.transferGroups);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(variables, execution, transferGroups);
        }
        
        @Override
        public String toString() {
            return "WorkflowSpec{" +
                   "variables=" + variables +
                   ", execution=" + execution +
                   ", transferGroups=" + transferGroups +
                   '}';
        }
    }
    
    /**
     * Execution configuration for the workflow.
     */
    public static class ExecutionConfig {
        private final boolean dryRun;
        private final boolean virtualRun;
        private final int parallelism;
        private final Duration timeout;
        private final String strategy;
        
        public ExecutionConfig(boolean dryRun, boolean virtualRun, int parallelism, Duration timeout, String strategy) {
            this.dryRun = dryRun;
            this.virtualRun = virtualRun;
            this.parallelism = Math.max(1, parallelism);
            this.timeout = timeout;
            this.strategy = strategy != null ? strategy : "sequential";
        }
        
        public boolean isDryRun() {
            return dryRun;
        }
        
        public boolean isVirtualRun() {
            return virtualRun;
        }
        
        public int getParallelism() {
            return parallelism;
        }
        
        public Duration getTimeout() {
            return timeout;
        }
        
        public String getStrategy() {
            return strategy;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExecutionConfig that = (ExecutionConfig) o;
            return dryRun == that.dryRun &&
                   virtualRun == that.virtualRun &&
                   parallelism == that.parallelism &&
                   Objects.equals(timeout, that.timeout) &&
                   Objects.equals(strategy, that.strategy);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(dryRun, virtualRun, parallelism, timeout, strategy);
        }
        
        @Override
        public String toString() {
            return "ExecutionConfig{" +
                   "dryRun=" + dryRun +
                   ", virtualRun=" + virtualRun +
                   ", parallelism=" + parallelism +
                   ", timeout=" + timeout +
                   ", strategy='" + strategy + '\'' +
                   '}';
        }
    }
}
