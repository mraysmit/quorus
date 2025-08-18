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
 * Service interface for resource management and quota enforcement.
 * Tracks resource usage, enforces limits, and provides usage analytics.
 */
public interface ResourceManagementService {
    
    /**
     * Record resource usage for a tenant
     * 
     * @param usage the resource usage to record
     * @throws ResourceManagementException if recording fails
     */
    void recordUsage(ResourceUsage usage) throws ResourceManagementException;
    
    /**
     * Get current resource usage for a tenant
     * 
     * @param tenantId the tenant ID
     * @return current resource usage if available
     */
    Optional<ResourceUsage> getCurrentUsage(String tenantId);
    
    /**
     * Get resource usage for a specific date
     * 
     * @param tenantId the tenant ID
     * @param date the date
     * @return resource usage for the date if available
     */
    Optional<ResourceUsage> getUsageForDate(String tenantId, LocalDate date);
    
    /**
     * Get resource usage history for a tenant
     * 
     * @param tenantId the tenant ID
     * @param fromDate start date (inclusive)
     * @param toDate end date (inclusive)
     * @return list of resource usage records
     */
    List<ResourceUsage> getUsageHistory(String tenantId, LocalDate fromDate, LocalDate toDate);
    
    /**
     * Check if a tenant can start a new transfer without exceeding limits
     * 
     * @param tenantId the tenant ID
     * @param transferSizeBytes the size of the transfer in bytes
     * @param estimatedBandwidth estimated bandwidth usage in bytes per second
     * @return validation result
     */
    ResourceValidationResult validateTransferRequest(String tenantId, long transferSizeBytes, long estimatedBandwidth);
    
    /**
     * Reserve resources for a transfer
     * 
     * @param tenantId the tenant ID
     * @param transferSizeBytes the size of the transfer in bytes
     * @param estimatedBandwidth estimated bandwidth usage in bytes per second
     * @return reservation token
     * @throws ResourceManagementException if reservation fails
     */
    String reserveResources(String tenantId, long transferSizeBytes, long estimatedBandwidth) 
            throws ResourceManagementException;
    
    /**
     * Release reserved resources
     * 
     * @param reservationToken the reservation token
     * @param actualBytesTransferred actual bytes transferred
     * @param actualBandwidthUsed actual bandwidth used
     * @throws ResourceManagementException if release fails
     */
    void releaseResources(String reservationToken, long actualBytesTransferred, long actualBandwidthUsed) 
            throws ResourceManagementException;
    
    /**
     * Update concurrent transfer count for a tenant
     * 
     * @param tenantId the tenant ID
     * @param delta the change in concurrent transfers (positive or negative)
     * @throws ResourceManagementException if update fails
     */
    void updateConcurrentTransfers(String tenantId, int delta) throws ResourceManagementException;
    
    /**
     * Update bandwidth usage for a tenant
     * 
     * @param tenantId the tenant ID
     * @param bandwidthBytesPerSecond current bandwidth usage
     * @throws ResourceManagementException if update fails
     */
    void updateBandwidthUsage(String tenantId, long bandwidthBytesPerSecond) throws ResourceManagementException;
    
    /**
     * Update storage usage for a tenant
     * 
     * @param tenantId the tenant ID
     * @param storageBytes current storage usage
     * @throws ResourceManagementException if update fails
     */
    void updateStorageUsage(String tenantId, long storageBytes) throws ResourceManagementException;
    
    /**
     * Record a completed transfer
     * 
     * @param tenantId the tenant ID
     * @param bytesTransferred bytes transferred
     * @param successful whether the transfer was successful
     * @throws ResourceManagementException if recording fails
     */
    void recordTransferCompletion(String tenantId, long bytesTransferred, boolean successful) 
            throws ResourceManagementException;
    
    /**
     * Get resource utilization percentage for a tenant
     * 
     * @param tenantId the tenant ID
     * @return utilization percentages for different resources
     */
    ResourceUtilization getResourceUtilization(String tenantId);
    
    /**
     * Get tenants that are approaching or exceeding their limits
     * 
     * @param thresholdPercentage threshold percentage (e.g., 80.0 for 80%)
     * @return list of tenant IDs approaching limits
     */
    List<String> getTenantsApproachingLimits(double thresholdPercentage);
    
    /**
     * Get tenants that have exceeded their limits
     * 
     * @return list of tenant IDs that have exceeded limits
     */
    List<String> getTenantsExceedingLimits();
    
    /**
     * Reset daily usage counters for all tenants
     * This should be called at the start of each day
     */
    void resetDailyUsage();
    
    /**
     * Get aggregated usage statistics across all tenants
     * 
     * @return aggregated usage statistics
     */
    AggregatedUsageStats getAggregatedUsageStats();
    
    /**
     * Exception thrown by resource management operations
     */
    class ResourceManagementException extends Exception {
        public ResourceManagementException(String message) {
            super(message);
        }
        
        public ResourceManagementException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Result of resource validation
     */
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
    
    /**
     * Resource utilization information
     */
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
