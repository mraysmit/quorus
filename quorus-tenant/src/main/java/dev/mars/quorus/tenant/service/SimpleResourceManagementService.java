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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Simple in-memory implementation of ResourceManagementService.
 * This implementation is suitable for development and testing.
 * For production use, consider implementing a persistent storage backend.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 1.0
 */
public class SimpleResourceManagementService implements ResourceManagementService {
    
    private static final Logger logger = Logger.getLogger(SimpleResourceManagementService.class.getName());
    
    private final TenantService tenantService;
    private final Map<String, ResourceUsage> currentUsage = new ConcurrentHashMap<>();
    private final Map<String, Map<LocalDate, ResourceUsage>> usageHistory = new ConcurrentHashMap<>();
    private final Map<String, ResourceReservation> reservations = new ConcurrentHashMap<>();
    private final AtomicLong reservationCounter = new AtomicLong(0);
    private final Object lock = new Object();
    
    public SimpleResourceManagementService(TenantService tenantService) {
        this.tenantService = tenantService;
    }
    
    @Override
    public void recordUsage(ResourceUsage usage) throws ResourceManagementException {
        synchronized (lock) {
            // Update current usage
            currentUsage.put(usage.getTenantId(), usage);
            
            // Update usage history
            usageHistory.computeIfAbsent(usage.getTenantId(), k -> new ConcurrentHashMap<>())
                       .put(usage.getUsageDate(), usage);
            
            logger.fine("Recorded usage for tenant: " + usage.getTenantId());
        }
    }
    
    @Override
    public Optional<ResourceUsage> getCurrentUsage(String tenantId) {
        return Optional.ofNullable(currentUsage.get(tenantId));
    }
    
    @Override
    public Optional<ResourceUsage> getUsageForDate(String tenantId, LocalDate date) {
        Map<LocalDate, ResourceUsage> tenantHistory = usageHistory.get(tenantId);
        if (tenantHistory != null) {
            return Optional.ofNullable(tenantHistory.get(date));
        }
        return Optional.empty();
    }
    
    @Override
    public List<ResourceUsage> getUsageHistory(String tenantId, LocalDate fromDate, LocalDate toDate) {
        Map<LocalDate, ResourceUsage> tenantHistory = usageHistory.get(tenantId);
        if (tenantHistory == null) {
            return List.of();
        }
        
        return tenantHistory.entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(fromDate) && !entry.getKey().isAfter(toDate))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(ResourceUsage::getUsageDate))
                .collect(Collectors.toList());
    }
    
    @Override
    public ResourceValidationResult validateTransferRequest(String tenantId, long transferSizeBytes, long estimatedBandwidth) {
        try {
            // Get tenant configuration
            TenantConfiguration config = tenantService.getEffectiveConfiguration(tenantId);
            if (config == null) {
                return ResourceValidationResult.denied("No configuration found for tenant", 
                        List.of("Tenant configuration not available"));
            }
            
            TenantConfiguration.ResourceLimits limits = config.getResourceLimits();
            ResourceUsage usage = getCurrentUsage(tenantId).orElse(
                    ResourceUsage.builder().tenantId(tenantId).build());
            
            List<String> violations = new ArrayList<>();
            
            // Check concurrent transfers
            if (usage.getCurrentConcurrentTransfers() >= limits.getMaxConcurrentTransfers()) {
                violations.add("Maximum concurrent transfers exceeded: " + 
                              usage.getCurrentConcurrentTransfers() + "/" + limits.getMaxConcurrentTransfers());
            }
            
            // Check bandwidth
            if (usage.getCurrentBandwidthBytesPerSecond() + estimatedBandwidth > limits.getMaxBandwidthBytesPerSecond()) {
                violations.add("Bandwidth limit would be exceeded: " + 
                              (usage.getCurrentBandwidthBytesPerSecond() + estimatedBandwidth) + "/" + 
                              limits.getMaxBandwidthBytesPerSecond() + " bytes/sec");
            }
            
            // Check transfer size
            if (transferSizeBytes > limits.getMaxTransferSizeBytes()) {
                violations.add("Transfer size exceeds limit: " + 
                              transferSizeBytes + "/" + limits.getMaxTransferSizeBytes() + " bytes");
            }
            
            // Check daily transfers
            if (usage.getDailyTransferCount() >= limits.getMaxTransfersPerDay()) {
                violations.add("Daily transfer limit exceeded: " + 
                              usage.getDailyTransferCount() + "/" + limits.getMaxTransfersPerDay());
            }
            
            if (violations.isEmpty()) {
                return ResourceValidationResult.allowed();
            } else {
                return ResourceValidationResult.denied("Resource limits would be exceeded", violations);
            }
            
        } catch (Exception e) {
            logger.severe("Error validating transfer request: " + e.getMessage());
            return ResourceValidationResult.denied("Validation error: " + e.getMessage(), 
                    List.of("Internal validation error"));
        }
    }
    
    @Override
    public String reserveResources(String tenantId, long transferSizeBytes, long estimatedBandwidth) 
            throws ResourceManagementException {
        
        ResourceValidationResult validation = validateTransferRequest(tenantId, transferSizeBytes, estimatedBandwidth);
        if (!validation.isAllowed()) {
            throw new ResourceManagementException("Resource reservation denied: " + validation.getReason());
        }
        
        synchronized (lock) {
            String reservationToken = "RES-" + reservationCounter.incrementAndGet();
            ResourceReservation reservation = new ResourceReservation(
                    reservationToken, tenantId, transferSizeBytes, estimatedBandwidth);
            
            reservations.put(reservationToken, reservation);
            
            // Update current usage to reflect reservation
            updateConcurrentTransfers(tenantId, 1);
            updateBandwidthUsage(tenantId, 
                    getCurrentUsage(tenantId).map(ResourceUsage::getCurrentBandwidthBytesPerSecond).orElse(0L) + 
                    estimatedBandwidth);
            
            logger.info("Reserved resources for tenant " + tenantId + ": " + reservationToken);
            return reservationToken;
        }
    }
    
    @Override
    public void releaseResources(String reservationToken, long actualBytesTransferred, long actualBandwidthUsed) 
            throws ResourceManagementException {
        
        synchronized (lock) {
            ResourceReservation reservation = reservations.remove(reservationToken);
            if (reservation == null) {
                throw new ResourceManagementException("Reservation not found: " + reservationToken);
            }
            
            // Update current usage to reflect release
            updateConcurrentTransfers(reservation.tenantId, -1);
            
            ResourceUsage currentUsage = getCurrentUsage(reservation.tenantId).orElse(
                    ResourceUsage.builder().tenantId(reservation.tenantId).build());
            
            long newBandwidth = Math.max(0, currentUsage.getCurrentBandwidthBytesPerSecond() - reservation.estimatedBandwidth);
            updateBandwidthUsage(reservation.tenantId, newBandwidth);
            
            // Record transfer completion
            recordTransferCompletion(reservation.tenantId, actualBytesTransferred, true);
            
            logger.info("Released resources for reservation: " + reservationToken);
        }
    }
    
    @Override
    public void updateConcurrentTransfers(String tenantId, int delta) throws ResourceManagementException {
        synchronized (lock) {
            ResourceUsage current = getCurrentUsage(tenantId).orElse(
                    ResourceUsage.builder().tenantId(tenantId).build());
            
            long newCount = Math.max(0, current.getCurrentConcurrentTransfers() + delta);
            
            ResourceUsage updated = ResourceUsage.builder()
                    .tenantId(tenantId)
                    .currentConcurrentTransfers(newCount)
                    .currentBandwidthBytesPerSecond(current.getCurrentBandwidthBytesPerSecond())
                    .currentStorageBytes(current.getCurrentStorageBytes())
                    .dailyTransferCount(current.getDailyTransferCount())
                    .dailyBytesTransferred(current.getDailyBytesTransferred())
                    .dailyFailedTransfers(current.getDailyFailedTransfers())
                    .totalTransferCount(current.getTotalTransferCount())
                    .totalBytesTransferred(current.getTotalBytesTransferred())
                    .totalFailedTransfers(current.getTotalFailedTransfers())
                    .build();
            
            recordUsage(updated);
        }
    }
    
    @Override
    public void updateBandwidthUsage(String tenantId, long bandwidthBytesPerSecond) throws ResourceManagementException {
        synchronized (lock) {
            ResourceUsage current = getCurrentUsage(tenantId).orElse(
                    ResourceUsage.builder().tenantId(tenantId).build());
            
            ResourceUsage updated = ResourceUsage.builder()
                    .tenantId(tenantId)
                    .currentConcurrentTransfers(current.getCurrentConcurrentTransfers())
                    .currentBandwidthBytesPerSecond(bandwidthBytesPerSecond)
                    .currentStorageBytes(current.getCurrentStorageBytes())
                    .dailyTransferCount(current.getDailyTransferCount())
                    .dailyBytesTransferred(current.getDailyBytesTransferred())
                    .dailyFailedTransfers(current.getDailyFailedTransfers())
                    .totalTransferCount(current.getTotalTransferCount())
                    .totalBytesTransferred(current.getTotalBytesTransferred())
                    .totalFailedTransfers(current.getTotalFailedTransfers())
                    .build();
            
            recordUsage(updated);
        }
    }
    
    @Override
    public void updateStorageUsage(String tenantId, long storageBytes) throws ResourceManagementException {
        synchronized (lock) {
            ResourceUsage current = getCurrentUsage(tenantId).orElse(
                    ResourceUsage.builder().tenantId(tenantId).build());
            
            ResourceUsage updated = ResourceUsage.builder()
                    .tenantId(tenantId)
                    .currentConcurrentTransfers(current.getCurrentConcurrentTransfers())
                    .currentBandwidthBytesPerSecond(current.getCurrentBandwidthBytesPerSecond())
                    .currentStorageBytes(storageBytes)
                    .dailyTransferCount(current.getDailyTransferCount())
                    .dailyBytesTransferred(current.getDailyBytesTransferred())
                    .dailyFailedTransfers(current.getDailyFailedTransfers())
                    .totalTransferCount(current.getTotalTransferCount())
                    .totalBytesTransferred(current.getTotalBytesTransferred())
                    .totalFailedTransfers(current.getTotalFailedTransfers())
                    .build();
            
            recordUsage(updated);
        }
    }
    
    @Override
    public void recordTransferCompletion(String tenantId, long bytesTransferred, boolean successful) 
            throws ResourceManagementException {
        synchronized (lock) {
            ResourceUsage current = getCurrentUsage(tenantId).orElse(
                    ResourceUsage.builder().tenantId(tenantId).build());
            
            ResourceUsage updated = ResourceUsage.builder()
                    .tenantId(tenantId)
                    .currentConcurrentTransfers(current.getCurrentConcurrentTransfers())
                    .currentBandwidthBytesPerSecond(current.getCurrentBandwidthBytesPerSecond())
                    .currentStorageBytes(current.getCurrentStorageBytes())
                    .dailyTransferCount(current.getDailyTransferCount() + 1)
                    .dailyBytesTransferred(current.getDailyBytesTransferred() + bytesTransferred)
                    .dailyFailedTransfers(current.getDailyFailedTransfers() + (successful ? 0 : 1))
                    .totalTransferCount(current.getTotalTransferCount() + 1)
                    .totalBytesTransferred(current.getTotalBytesTransferred() + bytesTransferred)
                    .totalFailedTransfers(current.getTotalFailedTransfers() + (successful ? 0 : 1))
                    .build();
            
            recordUsage(updated);
        }
    }
    
    @Override
    public ResourceUtilization getResourceUtilization(String tenantId) {
        TenantConfiguration config = tenantService.getEffectiveConfiguration(tenantId);
        if (config == null) {
            return new ResourceUtilization(tenantId, 0, 0, 0, 0);
        }
        
        ResourceUsage usage = getCurrentUsage(tenantId).orElse(
                ResourceUsage.builder().tenantId(tenantId).build());
        
        TenantConfiguration.ResourceLimits limits = config.getResourceLimits();
        
        return new ResourceUtilization(
                tenantId,
                usage.getConcurrentTransfersUsagePercentage(limits),
                usage.getBandwidthUsagePercentage(limits),
                usage.getStorageUsagePercentage(limits),
                usage.getDailyTransfersUsagePercentage(limits)
        );
    }
    
    @Override
    public List<String> getTenantsApproachingLimits(double thresholdPercentage) {
        return currentUsage.keySet().stream()
                .filter(tenantId -> {
                    ResourceUtilization utilization = getResourceUtilization(tenantId);
                    return utilization.isApproachingLimits(thresholdPercentage);
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public List<String> getTenantsExceedingLimits() {
        return currentUsage.keySet().stream()
                .filter(tenantId -> {
                    ResourceUtilization utilization = getResourceUtilization(tenantId);
                    return utilization.isExceedingLimits();
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public void resetDailyUsage() {
        synchronized (lock) {
            LocalDate today = LocalDate.now();
            
            for (String tenantId : currentUsage.keySet()) {
                ResourceUsage current = currentUsage.get(tenantId);
                if (current != null) {
                    ResourceUsage reset = ResourceUsage.builder()
                            .tenantId(tenantId)
                            .usageDate(today)
                            .currentConcurrentTransfers(current.getCurrentConcurrentTransfers())
                            .currentBandwidthBytesPerSecond(current.getCurrentBandwidthBytesPerSecond())
                            .currentStorageBytes(current.getCurrentStorageBytes())
                            .dailyTransferCount(0)
                            .dailyBytesTransferred(0)
                            .dailyFailedTransfers(0)
                            .totalTransferCount(current.getTotalTransferCount())
                            .totalBytesTransferred(current.getTotalBytesTransferred())
                            .totalFailedTransfers(current.getTotalFailedTransfers())
                            .build();
                    
                    try {
                        recordUsage(reset);
                    } catch (ResourceManagementException e) {
                        logger.severe("Error resetting daily usage for tenant " + tenantId + ": " + e.getMessage());
                    }
                }
            }
            
            logger.info("Reset daily usage for all tenants");
        }
    }
    
    @Override
    public AggregatedUsageStats getAggregatedUsageStats() {
        List<ResourceUsage> allUsage = new ArrayList<>(currentUsage.values());
        
        long totalTenants = allUsage.size();
        long activeTenants = tenantService.getActiveTenants().size();
        
        long totalConcurrentTransfers = allUsage.stream()
                .mapToLong(ResourceUsage::getCurrentConcurrentTransfers)
                .sum();
        
        long totalBandwidth = allUsage.stream()
                .mapToLong(ResourceUsage::getCurrentBandwidthBytesPerSecond)
                .sum();
        
        long totalStorage = allUsage.stream()
                .mapToLong(ResourceUsage::getCurrentStorageBytes)
                .sum();
        
        long totalDailyTransfers = allUsage.stream()
                .mapToLong(ResourceUsage::getDailyTransferCount)
                .sum();
        
        long totalDailyBytes = allUsage.stream()
                .mapToLong(ResourceUsage::getDailyBytesTransferred)
                .sum();
        
        double averageSuccessRate = allUsage.stream()
                .mapToDouble(ResourceUsage::getDailySuccessRate)
                .average()
                .orElse(1.0);
        
        return new AggregatedUsageStats(
                totalTenants, activeTenants, totalConcurrentTransfers,
                totalBandwidth, totalStorage, totalDailyTransfers,
                totalDailyBytes, averageSuccessRate
        );
    }
    
    // Helper class for resource reservations
    private static class ResourceReservation {
        final String token;
        final String tenantId;
        final long transferSizeBytes;
        final long estimatedBandwidth;
        
        ResourceReservation(String token, String tenantId, long transferSizeBytes, long estimatedBandwidth) {
            this.token = token;
            this.tenantId = tenantId;
            this.transferSizeBytes = transferSizeBytes;
            this.estimatedBandwidth = estimatedBandwidth;
        }
    }
}
