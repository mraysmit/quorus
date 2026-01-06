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
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents the requirements and constraints for job assignment.
 * This class defines what kind of agent is needed to execute a job,
 * including capabilities, geographic preferences, and assignment strategies.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-10-28
 * @version 1.0
 */
public class JobRequirements implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final String targetRegion;
    private final Set<String> requiredProtocols;
    private final Set<String> preferredRegions;
    private final Set<String> excludedAgents;
    private final Set<String> preferredAgents;
    private final long minBandwidth;
    private final long maxTransferSize;
    private final boolean requiresEncryption;
    private final boolean requiresCompression;
    private final SelectionStrategy selectionStrategy;
    private final Map<String, String> customAttributes;
    private final int maxConcurrentJobs;
    private final String tenantId;
    private final String namespace;
    
    private JobRequirements(Builder builder) {
        this.targetRegion = builder.targetRegion;
        this.requiredProtocols = builder.requiredProtocols != null ? Set.copyOf(builder.requiredProtocols) : Set.of();
        this.preferredRegions = builder.preferredRegions != null ? Set.copyOf(builder.preferredRegions) : Set.of();
        this.excludedAgents = builder.excludedAgents != null ? Set.copyOf(builder.excludedAgents) : Set.of();
        this.preferredAgents = builder.preferredAgents != null ? Set.copyOf(builder.preferredAgents) : Set.of();
        this.minBandwidth = Math.max(0, builder.minBandwidth);
        this.maxTransferSize = Math.max(0, builder.maxTransferSize);
        this.requiresEncryption = builder.requiresEncryption;
        this.requiresCompression = builder.requiresCompression;
        this.selectionStrategy = builder.selectionStrategy != null ? builder.selectionStrategy : SelectionStrategy.WEIGHTED_SCORE;
        this.customAttributes = builder.customAttributes != null ? Map.copyOf(builder.customAttributes) : Map.of();
        this.maxConcurrentJobs = Math.max(1, builder.maxConcurrentJobs);
        this.tenantId = builder.tenantId;
        this.namespace = builder.namespace;
    }
    
    /**
     * Agent selection strategies for job assignment.
     */
    public enum SelectionStrategy {
        /**
         * Round-robin assignment across available agents.
         */
        ROUND_ROBIN,
        
        /**
         * Assign to the agent with the least current load.
         */
        LEAST_LOADED,
        
        /**
         * Assign based on agent capabilities matching job requirements.
         */
        CAPABILITY_BASED,
        
        /**
         * Prefer agents in the same region or closest geographic location.
         */
        LOCALITY_AWARE,
        
        /**
         * Use a weighted scoring algorithm considering multiple factors.
         */
        WEIGHTED_SCORE,
        
        /**
         * Assign to a specific preferred agent if available.
         */
        PREFERRED_AGENT
    }
    
    public String getTargetRegion() {
        return targetRegion;
    }
    
    public Set<String> getRequiredProtocols() {
        return requiredProtocols;
    }
    
    public Set<String> getPreferredRegions() {
        return preferredRegions;
    }
    
    public Set<String> getExcludedAgents() {
        return excludedAgents;
    }
    
    public Set<String> getPreferredAgents() {
        return preferredAgents;
    }
    
    public long getMinBandwidth() {
        return minBandwidth;
    }
    
    public long getMaxTransferSize() {
        return maxTransferSize;
    }
    
    public boolean isRequiresEncryption() {
        return requiresEncryption;
    }
    
    public boolean isRequiresCompression() {
        return requiresCompression;
    }
    
    public SelectionStrategy getSelectionStrategy() {
        return selectionStrategy;
    }
    
    public Map<String, String> getCustomAttributes() {
        return customAttributes;
    }
    
    public int getMaxConcurrentJobs() {
        return maxConcurrentJobs;
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    /**
     * Check if an agent is explicitly excluded from assignment.
     */
    public boolean isAgentExcluded(String agentId) {
        return excludedAgents.contains(agentId);
    }
    
    /**
     * Check if an agent is in the preferred list.
     */
    public boolean isAgentPreferred(String agentId) {
        return preferredAgents.contains(agentId);
    }
    
    /**
     * Check if a protocol is required for this job.
     */
    public boolean isProtocolRequired(String protocol) {
        return requiredProtocols.isEmpty() || requiredProtocols.contains(protocol);
    }
    
    /**
     * Check if a region is preferred for this job.
     */
    public boolean isRegionPreferred(String region) {
        return preferredRegions.isEmpty() || preferredRegions.contains(region);
    }
    
    /**
     * Get a custom attribute value.
     */
    public String getCustomAttribute(String key) {
        return customAttributes.get(key);
    }
    
    /**
     * Check if this job has any geographic preferences.
     */
    public boolean hasGeographicPreferences() {
        return targetRegion != null || !preferredRegions.isEmpty();
    }
    
    /**
     * Check if this job has specific agent preferences.
     */
    public boolean hasAgentPreferences() {
        return !preferredAgents.isEmpty() || !excludedAgents.isEmpty();
    }
    
    /**
     * Check if this job has capability requirements.
     */
    public boolean hasCapabilityRequirements() {
        return !requiredProtocols.isEmpty() || requiresEncryption || 
               requiresCompression || minBandwidth > 0 || maxTransferSize > 0;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobRequirements that = (JobRequirements) o;
        return minBandwidth == that.minBandwidth &&
               maxTransferSize == that.maxTransferSize &&
               requiresEncryption == that.requiresEncryption &&
               requiresCompression == that.requiresCompression &&
               maxConcurrentJobs == that.maxConcurrentJobs &&
               Objects.equals(targetRegion, that.targetRegion) &&
               Objects.equals(requiredProtocols, that.requiredProtocols) &&
               Objects.equals(preferredRegions, that.preferredRegions) &&
               Objects.equals(excludedAgents, that.excludedAgents) &&
               Objects.equals(preferredAgents, that.preferredAgents) &&
               selectionStrategy == that.selectionStrategy &&
               Objects.equals(customAttributes, that.customAttributes) &&
               Objects.equals(tenantId, that.tenantId) &&
               Objects.equals(namespace, that.namespace);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(targetRegion, requiredProtocols, preferredRegions, 
                          excludedAgents, preferredAgents, minBandwidth, maxTransferSize,
                          requiresEncryption, requiresCompression, selectionStrategy,
                          customAttributes, maxConcurrentJobs, tenantId, namespace);
    }
    
    @Override
    public String toString() {
        return "JobRequirements{" +
                "targetRegion='" + targetRegion + '\'' +
                ", selectionStrategy=" + selectionStrategy +
                ", requiredProtocols=" + requiredProtocols +
                ", preferredRegions=" + preferredRegions +
                ", minBandwidth=" + minBandwidth +
                ", maxTransferSize=" + maxTransferSize +
                ", requiresEncryption=" + requiresEncryption +
                ", requiresCompression=" + requiresCompression +
                '}';
    }
    
    /**
     * Builder for creating JobRequirements instances.
     */
    public static class Builder {
        private String targetRegion;
        private Set<String> requiredProtocols;
        private Set<String> preferredRegions;
        private Set<String> excludedAgents;
        private Set<String> preferredAgents;
        private long minBandwidth;
        private long maxTransferSize;
        private boolean requiresEncryption;
        private boolean requiresCompression;
        private SelectionStrategy selectionStrategy;
        private Map<String, String> customAttributes;
        private int maxConcurrentJobs = 10; // Default value
        private String tenantId;
        private String namespace;
        
        public Builder targetRegion(String targetRegion) {
            this.targetRegion = targetRegion;
            return this;
        }
        
        public Builder requiredProtocols(Set<String> requiredProtocols) {
            this.requiredProtocols = requiredProtocols;
            return this;
        }
        
        public Builder preferredRegions(Set<String> preferredRegions) {
            this.preferredRegions = preferredRegions;
            return this;
        }
        
        public Builder excludedAgents(Set<String> excludedAgents) {
            this.excludedAgents = excludedAgents;
            return this;
        }
        
        public Builder preferredAgents(Set<String> preferredAgents) {
            this.preferredAgents = preferredAgents;
            return this;
        }
        
        public Builder minBandwidth(long minBandwidth) {
            this.minBandwidth = minBandwidth;
            return this;
        }
        
        public Builder maxTransferSize(long maxTransferSize) {
            this.maxTransferSize = maxTransferSize;
            return this;
        }
        
        public Builder requiresEncryption(boolean requiresEncryption) {
            this.requiresEncryption = requiresEncryption;
            return this;
        }
        
        public Builder requiresCompression(boolean requiresCompression) {
            this.requiresCompression = requiresCompression;
            return this;
        }
        
        public Builder selectionStrategy(SelectionStrategy selectionStrategy) {
            this.selectionStrategy = selectionStrategy;
            return this;
        }
        
        public Builder customAttributes(Map<String, String> customAttributes) {
            this.customAttributes = customAttributes;
            return this;
        }
        
        public Builder maxConcurrentJobs(int maxConcurrentJobs) {
            this.maxConcurrentJobs = maxConcurrentJobs;
            return this;
        }
        
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }
        
        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }
        
        public JobRequirements build() {
            return new JobRequirements(this);
        }
    }
}
