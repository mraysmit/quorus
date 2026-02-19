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
import dev.mars.quorus.tenant.observability.TenantMetrics;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory implementation of ResourceManagementService using lock-free atomic
 * counters for simple updates and per-tenant {@link StampedLock} for compound
 * operations.
 * 
 * <h2>Concurrency design</h2>
 * <ul>
 *   <li>Each tenant has its own {@link TenantCounters} with {@link AtomicLong}
 *       gauges and {@link LongAdder} monotonic counters — simple updates
 *       ({@code updateConcurrentTransfers}, {@code updateBandwidthUsage},
 *       {@code updateStorageUsage}, {@code recordTransferCompletion}) are
 *       <b>lock-free</b>.</li>
 *   <li>Compound operations that must be atomic across multiple counters
 *       ({@code reserveResources}, {@code releaseResources}, {@code recordUsage},
 *       {@code resetDailyUsage}) use a <b>per-tenant StampedLock</b> — no
 *       global bottleneck.</li>
 *   <li>{@code reserveResources} validates <b>inside</b> the per-tenant lock
 *       to eliminate the TOCTOU race present in the original design.</li>
 * </ul>
 * 
 * <p>For production use, consider implementing a persistent storage backend.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-18
 * @version 2.0
 */
public class SimpleResourceManagementService implements ResourceManagementService {
    
    private static final Logger logger = LoggerFactory.getLogger(SimpleResourceManagementService.class);
    
    private final TenantService tenantService;
    
    /** Per-tenant mutable counters — the single source of truth for current usage. */
    private final ConcurrentHashMap<String, TenantCounters> tenantCounters = new ConcurrentHashMap<>();
    
    /** Immutable usage snapshots indexed by date. */
    private final ConcurrentHashMap<String, Map<LocalDate, ResourceUsage>> usageHistory = new ConcurrentHashMap<>();
    
    /** Active resource reservations. */
    private final ConcurrentHashMap<String, ResourceReservation> reservations = new ConcurrentHashMap<>();
    
    private final AtomicLong reservationCounter = new AtomicLong(0);
    
    /** Aggregate operation counter for observability. */
    private final LongAdder totalOperationCount = new LongAdder();
    
    // OpenTelemetry metrics
    private final TenantMetrics metrics = TenantMetrics.getInstance();
    
    public SimpleResourceManagementService(TenantService tenantService) {
        this.tenantService = tenantService;
    }
    
    // ── Lookup / factory ─────────────────────────────────────────────────
    
    private TenantCounters getOrCreateCounters(String tenantId) {
        return tenantCounters.computeIfAbsent(tenantId, id -> {
            TenantCounters counters = new TenantCounters(id);
            // Register observable gauges for per-tenant resource monitoring
            metrics.registerTenantGauges(id,
                    () -> counters.concurrentTransfers.get(),
                    () -> counters.bandwidthBytesPerSecond.get());
            return counters;
        });
    }
    
    // ── Public API ───────────────────────────────────────────────────────
    
    @Override
    public void recordUsage(ResourceUsage usage) throws ResourceManagementException {
        TenantCounters counters = getOrCreateCounters(usage.getTenantId());
        long stamp = counters.lock.writeLock();
        try {
            counters.setFrom(usage);
            
            // Persist the exact snapshot into history
            usageHistory.computeIfAbsent(usage.getTenantId(), k -> new ConcurrentHashMap<>())
                       .put(usage.getUsageDate(), usage);
            
            logger.debug("Recorded usage for tenant: {}", usage.getTenantId());
        } finally {
            counters.lock.unlockWrite(stamp);
        }
        totalOperationCount.increment();
        metrics.recordResourceOperation(usage.getTenantId());
    }
    
    @Override
    public Optional<ResourceUsage> getCurrentUsage(String tenantId) {
        TenantCounters counters = tenantCounters.get(tenantId);
        return counters != null ? Optional.of(counters.snapshot()) : Optional.empty();
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
                for (String violation : violations) {
                    String violationType = violation.contains("concurrent") ? "concurrent_transfers" :
                                          violation.contains("Bandwidth") ? "bandwidth" :
                                          violation.contains("size") ? "transfer_size" : "daily_limit";
                    metrics.recordQuotaViolation(tenantId, violationType);
                }
                return ResourceValidationResult.denied("Resource limits would be exceeded", violations);
            }
            
        } catch (Exception e) {
            logger.error("Error validating transfer request: {}", e.getMessage());
            return ResourceValidationResult.denied("Validation error: " + e.getMessage(), 
                    List.of("Internal validation error"));
        }
    }
    
    /**
     * Reserve resources atomically — validation happens <b>inside</b> the per-tenant
     * lock so that the gap between "check" and "update" cannot be exploited.
     */
    @Override
    public String reserveResources(String tenantId, long transferSizeBytes, long estimatedBandwidth) 
            throws ResourceManagementException {
        
        TenantCounters counters = getOrCreateCounters(tenantId);
        long stamp = counters.lock.writeLock();
        try {
            // Validate INSIDE the lock to prevent TOCTOU race
            ResourceValidationResult validation = validateTransferRequest(tenantId, transferSizeBytes, estimatedBandwidth);
            if (!validation.isAllowed()) {
                metrics.recordResourceReservation(tenantId, "transfer", false);
                throw new ResourceManagementException("Resource reservation denied: " + validation.getReason());
            }
            
            String reservationToken = "RES-" + reservationCounter.incrementAndGet();
            ResourceReservation reservation = new ResourceReservation(
                    reservationToken, tenantId, transferSizeBytes, estimatedBandwidth);
            
            reservations.put(reservationToken, reservation);
            
            // Update counters directly (StampedLock is non-reentrant)
            counters.concurrentTransfers.incrementAndGet();
            counters.bandwidthBytesPerSecond.addAndGet(estimatedBandwidth);
            
            metrics.recordResourceReservation(tenantId, "transfer", true);
            
            logger.info("Reserved resources for tenant {}: {}", tenantId, reservationToken);
            return reservationToken;
        } finally {
            counters.lock.unlockWrite(stamp);
        }
    }
    
    @Override
    public void releaseResources(String reservationToken, long actualBytesTransferred, long actualBandwidthUsed) 
            throws ResourceManagementException {
        
        ResourceReservation reservation = reservations.remove(reservationToken);
        if (reservation == null) {
            throw new ResourceManagementException("Reservation not found: " + reservationToken);
        }
        
        TenantCounters counters = getOrCreateCounters(reservation.tenantId);
        long stamp = counters.lock.writeLock();
        try {
            // Decrement concurrent transfers (floor at 0)
            long newConcurrent = counters.concurrentTransfers.decrementAndGet();
            if (newConcurrent < 0) {
                counters.concurrentTransfers.set(0);
            }
            
            // Reduce bandwidth (floor at 0)
            long newBandwidth = counters.bandwidthBytesPerSecond.addAndGet(-reservation.estimatedBandwidth);
            if (newBandwidth < 0) {
                counters.bandwidthBytesPerSecond.set(0);
            }
            
            // Record transfer completion
            counters.dailyTransferCount.increment();
            counters.dailyBytesTransferred.add(actualBytesTransferred);
            counters.totalTransferCount.increment();
            counters.totalBytesTransferred.add(actualBytesTransferred);
        } finally {
            counters.lock.unlockWrite(stamp);
        }
        
        metrics.recordResourceRelease(reservation.tenantId, "transfer");
        totalOperationCount.increment();
        metrics.recordResourceOperation(reservation.tenantId);
        logger.info("Released resources for reservation: {}", reservationToken);
    }
    
    // ── Lock-free counter updates ────────────────────────────────────────
    
    @Override
    public void updateConcurrentTransfers(String tenantId, int delta) throws ResourceManagementException {
        TenantCounters counters = getOrCreateCounters(tenantId);
        long newCount = counters.concurrentTransfers.addAndGet(delta);
        if (newCount < 0) {
            counters.concurrentTransfers.set(0);
        }
        totalOperationCount.increment();
        metrics.recordResourceOperation(tenantId);
    }
    
    @Override
    public void updateBandwidthUsage(String tenantId, long bandwidthBytesPerSecond) throws ResourceManagementException {
        getOrCreateCounters(tenantId).bandwidthBytesPerSecond.set(bandwidthBytesPerSecond);
        totalOperationCount.increment();
        metrics.recordResourceOperation(tenantId);
    }
    
    @Override
    public void updateStorageUsage(String tenantId, long storageBytes) throws ResourceManagementException {
        getOrCreateCounters(tenantId).storageBytes.set(storageBytes);
        totalOperationCount.increment();
        metrics.recordResourceOperation(tenantId);
    }
    
    @Override
    public void recordTransferCompletion(String tenantId, long bytesTransferred, boolean successful) 
            throws ResourceManagementException {
        TenantCounters counters = getOrCreateCounters(tenantId);
        counters.dailyTransferCount.increment();
        counters.dailyBytesTransferred.add(bytesTransferred);
        counters.totalTransferCount.increment();
        counters.totalBytesTransferred.add(bytesTransferred);
        if (!successful) {
            counters.dailyFailedTransfers.increment();
            counters.totalFailedTransfers.increment();
        }
        totalOperationCount.increment();
        metrics.recordResourceOperation(tenantId);
    }
    
    // ── Read-only queries ────────────────────────────────────────────────
    
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
        return tenantCounters.keySet().stream()
                .filter(tenantId -> {
                    ResourceUtilization utilization = getResourceUtilization(tenantId);
                    return utilization.isApproachingLimits(thresholdPercentage);
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public List<String> getTenantsExceedingLimits() {
        return tenantCounters.keySet().stream()
                .filter(tenantId -> {
                    ResourceUtilization utilization = getResourceUtilization(tenantId);
                    return utilization.isExceedingLimits();
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public void resetDailyUsage() {
        LocalDate today = LocalDate.now();
        
        for (Map.Entry<String, TenantCounters> entry : tenantCounters.entrySet()) {
            TenantCounters counters = entry.getValue();
            long stamp = counters.lock.writeLock();
            try {
                // Snapshot current state before resetting for history
                ResourceUsage snapshot = counters.snapshot();
                usageHistory.computeIfAbsent(entry.getKey(), k -> new ConcurrentHashMap<>())
                           .put(snapshot.getUsageDate(), snapshot);
                
                counters.resetDaily();
            } finally {
                counters.lock.unlockWrite(stamp);
            }
        }
        
        totalOperationCount.increment();
        // Record a single operation for the reset sweep
        metrics.recordResourceOperation("system");
        logger.info("Reset daily usage for all tenants");
    }
    
    @Override
    public AggregatedUsageStats getAggregatedUsageStats() {
        // Snapshot all tenants — lock-free reads of atomic counters
        List<ResourceUsage> allUsage = tenantCounters.values().stream()
                .map(TenantCounters::snapshot)
                .collect(Collectors.toList());
        
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
    
    /**
     * Returns the total number of operations processed by this service.
     * Useful for observability and performance monitoring.
     */
    long getTotalOperationCount() {
        return totalOperationCount.sum();
    }
    
    // ── Inner classes ────────────────────────────────────────────────────
    
    /**
     * Per-tenant mutable counter holder. Uses {@link AtomicLong} for gauge-style
     * metrics (concurrent transfers, bandwidth, storage) and {@link LongAdder}
     * for monotonically increasing counters (transfer counts, bytes transferred,
     * failure counts).
     * 
     * <p>A {@link StampedLock} protects compound operations that must be atomic
     * across multiple counters. Simple single-counter updates are lock-free.
     */
    static class TenantCounters {
        final String tenantId;
        
        // Gauges — can go up or down
        final AtomicLong concurrentTransfers = new AtomicLong(0);
        final AtomicLong bandwidthBytesPerSecond = new AtomicLong(0);
        final AtomicLong storageBytes = new AtomicLong(0);
        
        // Monotonic counters — daily (resettable)
        final LongAdder dailyTransferCount = new LongAdder();
        final LongAdder dailyBytesTransferred = new LongAdder();
        final LongAdder dailyFailedTransfers = new LongAdder();
        
        // Monotonic counters — lifetime totals
        final LongAdder totalTransferCount = new LongAdder();
        final LongAdder totalBytesTransferred = new LongAdder();
        final LongAdder totalFailedTransfers = new LongAdder();
        
        /** Per-tenant lock for compound operations. Non-reentrant. */
        final StampedLock lock = new StampedLock();
        
        TenantCounters(String tenantId) {
            this.tenantId = tenantId;
        }
        
        /** Set all counters from an immutable {@link ResourceUsage} snapshot. */
        void setFrom(ResourceUsage usage) {
            concurrentTransfers.set(usage.getCurrentConcurrentTransfers());
            bandwidthBytesPerSecond.set(usage.getCurrentBandwidthBytesPerSecond());
            storageBytes.set(usage.getCurrentStorageBytes());
            
            // LongAdder has no set() — reset then add
            dailyTransferCount.reset();
            dailyTransferCount.add(usage.getDailyTransferCount());
            
            dailyBytesTransferred.reset();
            dailyBytesTransferred.add(usage.getDailyBytesTransferred());
            
            dailyFailedTransfers.reset();
            dailyFailedTransfers.add(usage.getDailyFailedTransfers());
            
            totalTransferCount.reset();
            totalTransferCount.add(usage.getTotalTransferCount());
            
            totalBytesTransferred.reset();
            totalBytesTransferred.add(usage.getTotalBytesTransferred());
            
            totalFailedTransfers.reset();
            totalFailedTransfers.add(usage.getTotalFailedTransfers());
        }
        
        /** Create an immutable {@link ResourceUsage} snapshot from current counter values. */
        ResourceUsage snapshot() {
            return ResourceUsage.builder()
                    .tenantId(tenantId)
                    .currentConcurrentTransfers(concurrentTransfers.get())
                    .currentBandwidthBytesPerSecond(bandwidthBytesPerSecond.get())
                    .currentStorageBytes(storageBytes.get())
                    .dailyTransferCount(dailyTransferCount.sum())
                    .dailyBytesTransferred(dailyBytesTransferred.sum())
                    .dailyFailedTransfers(dailyFailedTransfers.sum())
                    .totalTransferCount(totalTransferCount.sum())
                    .totalBytesTransferred(totalBytesTransferred.sum())
                    .totalFailedTransfers(totalFailedTransfers.sum())
                    .build();
        }
        
        /** Reset daily counters to zero. Must be called under writeLock. */
        void resetDaily() {
            dailyTransferCount.reset();
            dailyBytesTransferred.reset();
            dailyFailedTransfers.reset();
        }
    }
    
    /** Represents an active resource reservation. */
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
