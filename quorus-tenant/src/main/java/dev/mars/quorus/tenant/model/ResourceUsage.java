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

package dev.mars.quorus.tenant.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public class ResourceUsage {
    
    private final String tenantId;
    private final Instant timestamp;
    private final LocalDate usageDate;
    
    // Current usage metrics
    private final long currentConcurrentTransfers;
    private final long currentBandwidthBytesPerSecond;
    private final long currentStorageBytes;
    
    // Daily usage metrics
    private final long dailyTransferCount;
    private final long dailyBytesTransferred;
    private final long dailyFailedTransfers;
    
    // Historical metrics
    private final long totalTransferCount;
    private final long totalBytesTransferred;
    private final long totalFailedTransfers;
    
    private ResourceUsage(Builder builder) {
        this.tenantId = builder.tenantId;
        this.timestamp = builder.timestamp;
        this.usageDate = builder.usageDate;
        this.currentConcurrentTransfers = builder.currentConcurrentTransfers;
        this.currentBandwidthBytesPerSecond = builder.currentBandwidthBytesPerSecond;
        this.currentStorageBytes = builder.currentStorageBytes;
        this.dailyTransferCount = builder.dailyTransferCount;
        this.dailyBytesTransferred = builder.dailyBytesTransferred;
        this.dailyFailedTransfers = builder.dailyFailedTransfers;
        this.totalTransferCount = builder.totalTransferCount;
        this.totalBytesTransferred = builder.totalBytesTransferred;
        this.totalFailedTransfers = builder.totalFailedTransfers;
    }
    
    // Getters
    public String getTenantId() { return tenantId; }
    public Instant getTimestamp() { return timestamp; }
    public LocalDate getUsageDate() { return usageDate; }
    public long getCurrentConcurrentTransfers() { return currentConcurrentTransfers; }
    public long getCurrentBandwidthBytesPerSecond() { return currentBandwidthBytesPerSecond; }
    public long getCurrentStorageBytes() { return currentStorageBytes; }
    public long getDailyTransferCount() { return dailyTransferCount; }
    public long getDailyBytesTransferred() { return dailyBytesTransferred; }
    public long getDailyFailedTransfers() { return dailyFailedTransfers; }
    public long getTotalTransferCount() { return totalTransferCount; }
    public long getTotalBytesTransferred() { return totalBytesTransferred; }
    public long getTotalFailedTransfers() { return totalFailedTransfers; }
    
    public double getDailySuccessRate() {
        if (dailyTransferCount == 0) return 1.0;
        return (double) (dailyTransferCount - dailyFailedTransfers) / dailyTransferCount;
    }
    
    public double getOverallSuccessRate() {
        if (totalTransferCount == 0) return 1.0;
        return (double) (totalTransferCount - totalFailedTransfers) / totalTransferCount;
    }
    
    public boolean exceedsLimits(TenantConfiguration.ResourceLimits limits) {
        return currentConcurrentTransfers > limits.getMaxConcurrentTransfers() ||
               currentBandwidthBytesPerSecond > limits.getMaxBandwidthBytesPerSecond() ||
               currentStorageBytes > limits.getMaxStorageBytes() ||
               dailyTransferCount > limits.getMaxTransfersPerDay();
    }
    
    public double getConcurrentTransfersUsagePercentage(TenantConfiguration.ResourceLimits limits) {
        if (limits.getMaxConcurrentTransfers() == 0) return 0.0;
        return (double) currentConcurrentTransfers / limits.getMaxConcurrentTransfers() * 100.0;
    }
    
    public double getBandwidthUsagePercentage(TenantConfiguration.ResourceLimits limits) {
        if (limits.getMaxBandwidthBytesPerSecond() == 0) return 0.0;
        return (double) currentBandwidthBytesPerSecond / limits.getMaxBandwidthBytesPerSecond() * 100.0;
    }
    
    public double getStorageUsagePercentage(TenantConfiguration.ResourceLimits limits) {
        if (limits.getMaxStorageBytes() == 0) return 0.0;
        return (double) currentStorageBytes / limits.getMaxStorageBytes() * 100.0;
    }
    
    /**
     * Get usage percentage for daily transfers
     */
    public double getDailyTransfersUsagePercentage(TenantConfiguration.ResourceLimits limits) {
        if (limits.getMaxTransfersPerDay() == 0) return 0.0;
        return (double) dailyTransferCount / limits.getMaxTransfersPerDay() * 100.0;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String tenantId;
        private Instant timestamp = Instant.now();
        private LocalDate usageDate = LocalDate.now();
        private long currentConcurrentTransfers = 0;
        private long currentBandwidthBytesPerSecond = 0;
        private long currentStorageBytes = 0;
        private long dailyTransferCount = 0;
        private long dailyBytesTransferred = 0;
        private long dailyFailedTransfers = 0;
        private long totalTransferCount = 0;
        private long totalBytesTransferred = 0;
        private long totalFailedTransfers = 0;
        
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder usageDate(LocalDate usageDate) { this.usageDate = usageDate; return this; }
        public Builder currentConcurrentTransfers(long currentConcurrentTransfers) { 
            this.currentConcurrentTransfers = currentConcurrentTransfers; return this; 
        }
        public Builder currentBandwidthBytesPerSecond(long currentBandwidthBytesPerSecond) { 
            this.currentBandwidthBytesPerSecond = currentBandwidthBytesPerSecond; return this; 
        }
        public Builder currentStorageBytes(long currentStorageBytes) { 
            this.currentStorageBytes = currentStorageBytes; return this; 
        }
        public Builder dailyTransferCount(long dailyTransferCount) { 
            this.dailyTransferCount = dailyTransferCount; return this; 
        }
        public Builder dailyBytesTransferred(long dailyBytesTransferred) { 
            this.dailyBytesTransferred = dailyBytesTransferred; return this; 
        }
        public Builder dailyFailedTransfers(long dailyFailedTransfers) { 
            this.dailyFailedTransfers = dailyFailedTransfers; return this; 
        }
        public Builder totalTransferCount(long totalTransferCount) { 
            this.totalTransferCount = totalTransferCount; return this; 
        }
        public Builder totalBytesTransferred(long totalBytesTransferred) { 
            this.totalBytesTransferred = totalBytesTransferred; return this; 
        }
        public Builder totalFailedTransfers(long totalFailedTransfers) { 
            this.totalFailedTransfers = totalFailedTransfers; return this; 
        }
        
        public ResourceUsage build() {
            Objects.requireNonNull(tenantId, "tenantId cannot be null");
            Objects.requireNonNull(timestamp, "timestamp cannot be null");
            Objects.requireNonNull(usageDate, "usageDate cannot be null");
            
            return new ResourceUsage(this);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceUsage that = (ResourceUsage) o;
        return Objects.equals(tenantId, that.tenantId) &&
               Objects.equals(usageDate, that.usageDate);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(tenantId, usageDate);
    }
    
    @Override
    public String toString() {
        return "ResourceUsage{" +
                "tenantId='" + tenantId + '\'' +
                ", usageDate=" + usageDate +
                ", currentConcurrentTransfers=" + currentConcurrentTransfers +
                ", dailyTransferCount=" + dailyTransferCount +
                ", dailySuccessRate=" + String.format("%.2f%%", getDailySuccessRate() * 100) +
                '}';
    }
}
