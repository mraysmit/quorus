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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the current load and capacity information for an agent.
 * This class tracks active jobs, resource utilization, and performance metrics
 * to enable intelligent load balancing and job assignment decisions.
 * 
 * @author Quorus Team
 * @since 1.0.0
 */
public class AgentLoad implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final String agentId;
    private final int currentJobs;
    private final int maxConcurrentJobs;
    private final Set<String> activeJobIds;
    private final long totalBytesTransferring;
    private final long currentBandwidthUsage;
    private final long maxBandwidth;
    private final double cpuUtilization;
    private final double memoryUtilization;
    private final double diskUtilization;
    private final Instant lastUpdated;
    private final long completedJobsCount;
    private final long failedJobsCount;
    private final double averageJobDurationMs;
    private final double successRate;
    
    private AgentLoad(Builder builder) {
        this.agentId = Objects.requireNonNull(builder.agentId, "Agent ID cannot be null");
        this.currentJobs = Math.max(0, builder.currentJobs);
        this.maxConcurrentJobs = Math.max(1, builder.maxConcurrentJobs);
        this.activeJobIds = builder.activeJobIds != null ? Set.copyOf(builder.activeJobIds) : Set.of();
        this.totalBytesTransferring = Math.max(0, builder.totalBytesTransferring);
        this.currentBandwidthUsage = Math.max(0, builder.currentBandwidthUsage);
        this.maxBandwidth = Math.max(0, builder.maxBandwidth);
        this.cpuUtilization = Math.max(0.0, Math.min(1.0, builder.cpuUtilization));
        this.memoryUtilization = Math.max(0.0, Math.min(1.0, builder.memoryUtilization));
        this.diskUtilization = Math.max(0.0, Math.min(1.0, builder.diskUtilization));
        this.lastUpdated = Objects.requireNonNull(builder.lastUpdated, "Last updated timestamp cannot be null");
        this.completedJobsCount = Math.max(0, builder.completedJobsCount);
        this.failedJobsCount = Math.max(0, builder.failedJobsCount);
        this.averageJobDurationMs = Math.max(0, builder.averageJobDurationMs);
        this.successRate = Math.max(0.0, Math.min(1.0, builder.successRate));
    }
    
    public String getAgentId() {
        return agentId;
    }
    
    public int getCurrentJobs() {
        return currentJobs;
    }
    
    public int getMaxConcurrentJobs() {
        return maxConcurrentJobs;
    }
    
    public Set<String> getActiveJobIds() {
        return activeJobIds;
    }
    
    public long getTotalBytesTransferring() {
        return totalBytesTransferring;
    }
    
    public long getCurrentBandwidthUsage() {
        return currentBandwidthUsage;
    }
    
    public long getMaxBandwidth() {
        return maxBandwidth;
    }
    
    public double getCpuUtilization() {
        return cpuUtilization;
    }
    
    public double getMemoryUtilization() {
        return memoryUtilization;
    }
    
    public double getDiskUtilization() {
        return diskUtilization;
    }
    
    public Instant getLastUpdated() {
        return lastUpdated;
    }
    
    public long getCompletedJobsCount() {
        return completedJobsCount;
    }
    
    public long getFailedJobsCount() {
        return failedJobsCount;
    }
    
    public double getAverageJobDurationMs() {
        return averageJobDurationMs;
    }
    
    public double getSuccessRate() {
        return successRate;
    }
    
    /**
     * Calculate the load percentage based on current vs maximum concurrent jobs.
     * 
     * @return load percentage (0.0 to 1.0)
     */
    public double getLoadPercentage() {
        if (maxConcurrentJobs == 0) {
            return 1.0; // Fully loaded if no capacity
        }
        return (double) currentJobs / maxConcurrentJobs;
    }
    
    /**
     * Calculate the bandwidth utilization percentage.
     * 
     * @return bandwidth utilization (0.0 to 1.0)
     */
    public double getBandwidthUtilization() {
        if (maxBandwidth == 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) currentBandwidthUsage / maxBandwidth);
    }
    
    /**
     * Get the number of available job slots.
     * 
     * @return number of jobs this agent can still accept
     */
    public int getAvailableCapacity() {
        return Math.max(0, maxConcurrentJobs - currentJobs);
    }
    
    /**
     * Check if the agent can accept more jobs.
     * 
     * @return true if the agent has available capacity
     */
    public boolean canAcceptMoreJobs() {
        return getAvailableCapacity() > 0;
    }
    
    /**
     * Check if the agent is considered overloaded.
     * An agent is overloaded if it's at capacity or has high resource utilization.
     * 
     * @return true if the agent is overloaded
     */
    public boolean isOverloaded() {
        return getLoadPercentage() >= 1.0 || 
               cpuUtilization > 0.9 || 
               memoryUtilization > 0.9 || 
               diskUtilization > 0.9 ||
               getBandwidthUtilization() > 0.9;
    }
    
    /**
     * Check if the agent is lightly loaded and can take priority jobs.
     * 
     * @return true if the agent is lightly loaded
     */
    public boolean isLightlyLoaded() {
        return getLoadPercentage() < 0.5 && 
               cpuUtilization < 0.5 && 
               memoryUtilization < 0.5 && 
               diskUtilization < 0.5 &&
               getBandwidthUtilization() < 0.5;
    }
    
    /**
     * Calculate a comprehensive load score for job assignment.
     * Lower scores indicate better candidates for new job assignments.
     * 
     * @return load score (0.0 = best, 1.0 = worst)
     */
    public double getLoadScore() {
        double jobLoadWeight = 0.4;
        double cpuWeight = 0.2;
        double memoryWeight = 0.15;
        double diskWeight = 0.15;
        double bandwidthWeight = 0.1;
        
        return (getLoadPercentage() * jobLoadWeight) +
               (cpuUtilization * cpuWeight) +
               (memoryUtilization * memoryWeight) +
               (diskUtilization * diskWeight) +
               (getBandwidthUtilization() * bandwidthWeight);
    }
    
    /**
     * Get the total number of jobs processed (completed + failed).
     * 
     * @return total jobs processed
     */
    public long getTotalJobsProcessed() {
        return completedJobsCount + failedJobsCount;
    }
    
    /**
     * Check if this load information is stale.
     * 
     * @param maxAgeMs maximum age in milliseconds
     * @return true if the load data is older than maxAgeMs
     */
    public boolean isStale(long maxAgeMs) {
        return Instant.now().toEpochMilli() - lastUpdated.toEpochMilli() > maxAgeMs;
    }
    
    /**
     * Create a new AgentLoad with an additional active job.
     */
    public AgentLoad withAddedJob(String jobId, long estimatedBytes) {
        Set<String> newActiveJobs = ConcurrentHashMap.<String>newKeySet();
        newActiveJobs.addAll(activeJobIds);
        newActiveJobs.add(jobId);

        return new Builder(this)
                .currentJobs(currentJobs + 1)
                .activeJobIds(newActiveJobs)
                .totalBytesTransferring(totalBytesTransferring + estimatedBytes)
                .lastUpdated(Instant.now())
                .build();
    }

    /**
     * Create a new AgentLoad with a removed active job.
     */
    public AgentLoad withRemovedJob(String jobId, long transferredBytes, boolean successful) {
        Set<String> newActiveJobs = ConcurrentHashMap.<String>newKeySet();
        newActiveJobs.addAll(activeJobIds);
        newActiveJobs.remove(jobId);
        
        Builder builder = new Builder(this)
                .currentJobs(Math.max(0, currentJobs - 1))
                .activeJobIds(newActiveJobs)
                .totalBytesTransferring(Math.max(0, totalBytesTransferring - transferredBytes))
                .lastUpdated(Instant.now());
        
        if (successful) {
            builder.completedJobsCount(completedJobsCount + 1);
        } else {
            builder.failedJobsCount(failedJobsCount + 1);
        }
        
        // Recalculate success rate
        long totalJobs = builder.completedJobsCount + builder.failedJobsCount;
        if (totalJobs > 0) {
            builder.successRate((double) builder.completedJobsCount / totalJobs);
        }
        
        return builder.build();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentLoad agentLoad = (AgentLoad) o;
        return Objects.equals(agentId, agentLoad.agentId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(agentId);
    }
    
    @Override
    public String toString() {
        return "AgentLoad{" +
                "agentId='" + agentId + '\'' +
                ", currentJobs=" + currentJobs +
                ", maxConcurrentJobs=" + maxConcurrentJobs +
                ", loadPercentage=" + String.format("%.1f%%", getLoadPercentage() * 100) +
                ", cpuUtilization=" + String.format("%.1f%%", cpuUtilization * 100) +
                ", successRate=" + String.format("%.1f%%", successRate * 100) +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
    
    /**
     * Builder for creating AgentLoad instances.
     */
    public static class Builder {
        private String agentId;
        private int currentJobs;
        private int maxConcurrentJobs = 10; // Default value
        private Set<String> activeJobIds;
        private long totalBytesTransferring;
        private long currentBandwidthUsage;
        private long maxBandwidth;
        private double cpuUtilization;
        private double memoryUtilization;
        private double diskUtilization;
        private Instant lastUpdated;
        private long completedJobsCount;
        private long failedJobsCount;
        private double averageJobDurationMs;
        private double successRate = 1.0; // Default to 100% for new agents
        
        public Builder() {
            this.lastUpdated = Instant.now();
        }
        
        public Builder(AgentLoad existing) {
            this.agentId = existing.agentId;
            this.currentJobs = existing.currentJobs;
            this.maxConcurrentJobs = existing.maxConcurrentJobs;
            this.activeJobIds = existing.activeJobIds;
            this.totalBytesTransferring = existing.totalBytesTransferring;
            this.currentBandwidthUsage = existing.currentBandwidthUsage;
            this.maxBandwidth = existing.maxBandwidth;
            this.cpuUtilization = existing.cpuUtilization;
            this.memoryUtilization = existing.memoryUtilization;
            this.diskUtilization = existing.diskUtilization;
            this.lastUpdated = existing.lastUpdated;
            this.completedJobsCount = existing.completedJobsCount;
            this.failedJobsCount = existing.failedJobsCount;
            this.averageJobDurationMs = existing.averageJobDurationMs;
            this.successRate = existing.successRate;
        }
        
        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }
        
        public Builder currentJobs(int currentJobs) {
            this.currentJobs = currentJobs;
            return this;
        }
        
        public Builder maxConcurrentJobs(int maxConcurrentJobs) {
            this.maxConcurrentJobs = maxConcurrentJobs;
            return this;
        }
        
        public Builder activeJobIds(Set<String> activeJobIds) {
            this.activeJobIds = activeJobIds;
            return this;
        }
        
        public Builder totalBytesTransferring(long totalBytesTransferring) {
            this.totalBytesTransferring = totalBytesTransferring;
            return this;
        }
        
        public Builder currentBandwidthUsage(long currentBandwidthUsage) {
            this.currentBandwidthUsage = currentBandwidthUsage;
            return this;
        }
        
        public Builder maxBandwidth(long maxBandwidth) {
            this.maxBandwidth = maxBandwidth;
            return this;
        }
        
        public Builder cpuUtilization(double cpuUtilization) {
            this.cpuUtilization = cpuUtilization;
            return this;
        }
        
        public Builder memoryUtilization(double memoryUtilization) {
            this.memoryUtilization = memoryUtilization;
            return this;
        }
        
        public Builder diskUtilization(double diskUtilization) {
            this.diskUtilization = diskUtilization;
            return this;
        }
        
        public Builder lastUpdated(Instant lastUpdated) {
            this.lastUpdated = lastUpdated;
            return this;
        }
        
        public Builder completedJobsCount(long completedJobsCount) {
            this.completedJobsCount = completedJobsCount;
            return this;
        }
        
        public Builder failedJobsCount(long failedJobsCount) {
            this.failedJobsCount = failedJobsCount;
            return this;
        }
        
        public Builder averageJobDurationMs(double averageJobDurationMs) {
            this.averageJobDurationMs = averageJobDurationMs;
            return this;
        }
        
        public Builder successRate(double successRate) {
            this.successRate = successRate;
            return this;
        }
        
        public AgentLoad build() {
            return new AgentLoad(this);
        }
    }
}
