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

package dev.mars.quorus.tenant.service;

import dev.mars.quorus.tenant.model.ResourceUsage;
import dev.mars.quorus.tenant.model.TenantConfiguration;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
/**
 * Description for ResourceManagementService
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-18
 */

public interface ResourceManagementService {
    
    void recordUsage(ResourceUsage usage) throws ResourceManagementException;
    
    Optional<ResourceUsage> getCurrentUsage(String tenantId);
    
    Optional<ResourceUsage> getUsageForDate(String tenantId, LocalDate date);
    
    List<ResourceUsage> getUsageHistory(String tenantId, LocalDate fromDate, LocalDate toDate);
    
    ResourceValidationResult validateTransferRequest(String tenantId, long transferSizeBytes, long estimatedBandwidth);
    
    String reserveResources(String tenantId, long transferSizeBytes, long estimatedBandwidth) 
            throws ResourceManagementException;
    
    void releaseResources(String reservationToken, long actualBytesTransferred, long actualBandwidthUsed) 
            throws ResourceManagementException;
    
    void updateConcurrentTransfers(String tenantId, int delta) throws ResourceManagementException;
    
    void updateBandwidthUsage(String tenantId, long bandwidthBytesPerSecond) throws ResourceManagementException;
    
    void updateStorageUsage(String tenantId, long storageBytes) throws ResourceManagementException;
    
    void recordTransferCompletion(String tenantId, long bytesTransferred, boolean successful) 
            throws ResourceManagementException;
    
    ResourceUtilization getResourceUtilization(String tenantId);
    
    List<String> getTenantsApproachingLimits(double thresholdPercentage);
    
    List<String> getTenantsExceedingLimits();
    
    void resetDailyUsage();
    
    AggregatedUsageStats getAggregatedUsageStats();
    
    class ResourceManagementException extends Exception {
        public ResourceManagementException(String message) {
            super(message);
        }
        
        public ResourceManagementException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    class ResourceValidationResult {
        private final boolean allowed;
        private final String reason;
        private final List<String> violations;
        
        public ResourceValidationResult(boolean allowed, String reason, List<String> violations) {
            this.allowed = allowed;
            this.reason = reason;
            this.violations = violations;
        }
        
        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
        public List<String> getViolations() { return violations; }
        
        public static ResourceValidationResult allowed() {
            return new ResourceValidationResult(true, "Request allowed", List.of());
        }
        
        public static ResourceValidationResult denied(String reason, List<String> violations) {
            return new ResourceValidationResult(false, reason, violations);
        }
    }
    
    class ResourceUtilization {
        private final String tenantId;
        private final double concurrentTransfersUtilization;
        private final double bandwidthUtilization;
        private final double storageUtilization;
        private final double dailyTransfersUtilization;
        
        public ResourceUtilization(String tenantId, double concurrentTransfersUtilization, 
                                 double bandwidthUtilization, double storageUtilization, 
                                 double dailyTransfersUtilization) {
            this.tenantId = tenantId;
            this.concurrentTransfersUtilization = concurrentTransfersUtilization;
            this.bandwidthUtilization = bandwidthUtilization;
            this.storageUtilization = storageUtilization;
            this.dailyTransfersUtilization = dailyTransfersUtilization;
        }
        
        public String getTenantId() { return tenantId; }
        public double getConcurrentTransfersUtilization() { return concurrentTransfersUtilization; }
        public double getBandwidthUtilization() { return bandwidthUtilization; }
        public double getStorageUtilization() { return storageUtilization; }
        public double getDailyTransfersUtilization() { return dailyTransfersUtilization; }
        
        public double getMaxUtilization() {
            return Math.max(Math.max(concurrentTransfersUtilization, bandwidthUtilization),
                           Math.max(storageUtilization, dailyTransfersUtilization));
        }
        
        public boolean isApproachingLimits(double threshold) {
            return getMaxUtilization() >= threshold;
        }
        
        public boolean isExceedingLimits() {
            return getMaxUtilization() > 100.0;
        }
    }
    
    /**
     * Aggregated usage statistics
     */
    class AggregatedUsageStats {
        private final long totalTenants;
        private final long activeTenants;
        private final long totalConcurrentTransfers;
        private final long totalBandwidthBytesPerSecond;
        private final long totalStorageBytes;
        private final long totalDailyTransfers;
        private final long totalDailyBytesTransferred;
        private final double averageSuccessRate;
        
        public AggregatedUsageStats(long totalTenants, long activeTenants, long totalConcurrentTransfers,
                                  long totalBandwidthBytesPerSecond, long totalStorageBytes,
                                  long totalDailyTransfers, long totalDailyBytesTransferred,
                                  double averageSuccessRate) {
            this.totalTenants = totalTenants;
            this.activeTenants = activeTenants;
            this.totalConcurrentTransfers = totalConcurrentTransfers;
            this.totalBandwidthBytesPerSecond = totalBandwidthBytesPerSecond;
            this.totalStorageBytes = totalStorageBytes;
            this.totalDailyTransfers = totalDailyTransfers;
            this.totalDailyBytesTransferred = totalDailyBytesTransferred;
            this.averageSuccessRate = averageSuccessRate;
        }
        
        // Getters
        public long getTotalTenants() { return totalTenants; }
        public long getActiveTenants() { return activeTenants; }
        public long getTotalConcurrentTransfers() { return totalConcurrentTransfers; }
        public long getTotalBandwidthBytesPerSecond() { return totalBandwidthBytesPerSecond; }
        public long getTotalStorageBytes() { return totalStorageBytes; }
        public long getTotalDailyTransfers() { return totalDailyTransfers; }
        public long getTotalDailyBytesTransferred() { return totalDailyBytesTransferred; }
        public double getAverageSuccessRate() { return averageSuccessRate; }
    }
}
