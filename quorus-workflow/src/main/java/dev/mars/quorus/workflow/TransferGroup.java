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

import dev.mars.quorus.core.TransferRequest;

import java.util.List;
import java.util.Map;
import java.util.Objects;
/**
 * Description for TransferGroup
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-18
 */

public class TransferGroup {
    
    private final String name;
    private final String description;
    private final List<String> dependsOn;
    private final String condition;
    private final Map<String, Object> variables;
    private final List<TransferDefinition> transfers;
    private final boolean continueOnError;
    private final int retryCount;
    
    public TransferGroup(String name, String description, List<String> dependsOn, String condition,
                        Map<String, Object> variables, List<TransferDefinition> transfers,
                        boolean continueOnError, int retryCount) {
        this.name = Objects.requireNonNull(name, "Name cannot be null");
        this.description = description;
        this.dependsOn = dependsOn != null ? List.copyOf(dependsOn) : List.of();
        this.condition = condition;
        this.variables = variables != null ? Map.copyOf(variables) : Map.of();
        this.transfers = transfers != null ? List.copyOf(transfers) : List.of();
        this.continueOnError = continueOnError;
        this.retryCount = Math.max(0, retryCount);
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public List<String> getDependsOn() {
        return dependsOn;
    }
    
    public String getCondition() {
        return condition;
    }
    
    public Map<String, Object> getVariables() {
        return variables;
    }
    
    public List<TransferDefinition> getTransfers() {
        return transfers;
    }
    
    public boolean isContinueOnError() {
        return continueOnError;
    }
    
    public int getRetryCount() {
        return retryCount;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransferGroup that = (TransferGroup) o;
        return continueOnError == that.continueOnError &&
               retryCount == that.retryCount &&
               Objects.equals(name, that.name) &&
               Objects.equals(description, that.description) &&
               Objects.equals(dependsOn, that.dependsOn) &&
               Objects.equals(condition, that.condition) &&
               Objects.equals(variables, that.variables) &&
               Objects.equals(transfers, that.transfers);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, description, dependsOn, condition, variables, transfers, continueOnError, retryCount);
    }
    
    @Override
    public String toString() {
        return "TransferGroup{" +
               "name='" + name + '\'' +
               ", description='" + description + '\'' +
               ", dependsOn=" + dependsOn +
               ", condition='" + condition + '\'' +
               ", variables=" + variables +
               ", transfers=" + transfers +
               ", continueOnError=" + continueOnError +
               ", retryCount=" + retryCount +
               '}';
    }
    
    public static class TransferDefinition {
        private final String name;
        private final String source;
        private final String destination;
        private final String protocol;
        private final Map<String, Object> options;
        private final String condition;
        
        public TransferDefinition(String name, String source, String destination, String protocol,
                                Map<String, Object> options, String condition) {
            this.name = Objects.requireNonNull(name, "Name cannot be null");
            this.source = Objects.requireNonNull(source, "Source cannot be null");
            this.destination = Objects.requireNonNull(destination, "Destination cannot be null");
            this.protocol = protocol != null ? protocol : "http";
            this.options = options != null ? Map.copyOf(options) : Map.of();
            this.condition = condition;
        }
        
        public String getName() {
            return name;
        }
        
        public String getSource() {
            return source;
        }
        
        public String getDestination() {
            return destination;
        }
        
        public String getProtocol() {
            return protocol;
        }
        
        public Map<String, Object> getOptions() {
            return options;
        }
        
        public String getCondition() {
            return condition;
        }
        
        /**
         * Converts this transfer definition to a TransferRequest.
         * Variables should be resolved before calling this method.
         */
        public TransferRequest toTransferRequest() {
            try {
                return TransferRequest.builder()
                        .sourceUri(java.net.URI.create(source))
                        .destinationPath(java.nio.file.Paths.get(destination))
                        .protocol(protocol)
                        .build();
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid source URI or destination path", e);
            }
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TransferDefinition that = (TransferDefinition) o;
            return Objects.equals(name, that.name) &&
                   Objects.equals(source, that.source) &&
                   Objects.equals(destination, that.destination) &&
                   Objects.equals(protocol, that.protocol) &&
                   Objects.equals(options, that.options) &&
                   Objects.equals(condition, that.condition);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(name, source, destination, protocol, options, condition);
        }
        
        @Override
        public String toString() {
            return "TransferDefinition{" +
                   "name='" + name + '\'' +
                   ", source='" + source + '\'' +
                   ", destination='" + destination + '\'' +
                   ", protocol='" + protocol + '\'' +
                   ", options=" + options +
                   ", condition='" + condition + '\'' +
                   '}';
        }
    }
}
