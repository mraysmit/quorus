package dev.mars.quorus.monitoring;

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

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Metrics collection for transfer operations.
 * Thread-safe metrics tracking for protocol performance monitoring.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0 (Phase 2 - Dec 2025)
 */
public class TransferMetrics {
    
    private final String protocolName;
    private final Instant startTime;
    
    // Transfer counters
    private final LongAdder totalTransfers = new LongAdder();
    private final LongAdder successfulTransfers = new LongAdder();
    private final LongAdder failedTransfers = new LongAdder();
    private final AtomicLong activeTransfers = new AtomicLong(0);
    
    // Byte counters
    private final LongAdder totalBytesTransferred = new LongAdder();
    
    // Duration tracking
    private final LongAdder totalDurationMs = new LongAdder();
    private final AtomicLong minDurationMs = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxDurationMs = new AtomicLong(0);
    
    // Error tracking
    private final Map<String, LongAdder> errorCounts = new ConcurrentHashMap<>();
    
    // Last transfer tracking
    private volatile Instant lastTransferTime;
    private volatile boolean lastTransferSuccess;
    
    public TransferMetrics(String protocolName) {
        this.protocolName = protocolName;
        this.startTime = Instant.now();
    }
    
    /**
     * Record the start of a transfer.
     */
    public void recordTransferStart() {
        totalTransfers.increment();
        activeTransfers.incrementAndGet();
    }
    
    /**
     * Record a successful transfer completion.
     */
    public void recordTransferSuccess(long bytesTransferred, Duration duration) {
        successfulTransfers.increment();
        activeTransfers.decrementAndGet();
        totalBytesTransferred.add(bytesTransferred);
        
        long durationMs = duration.toMillis();
        totalDurationMs.add(durationMs);
        
        // Update min/max duration
        minDurationMs.updateAndGet(current -> Math.min(current, durationMs));
        maxDurationMs.updateAndGet(current -> Math.max(current, durationMs));
        
        lastTransferTime = Instant.now();
        lastTransferSuccess = true;
    }
    
    /**
     * Record a failed transfer.
     */
    public void recordTransferFailure(String errorType) {
        failedTransfers.increment();
        activeTransfers.decrementAndGet();
        
        errorCounts.computeIfAbsent(errorType, k -> new LongAdder()).increment();
        
        lastTransferTime = Instant.now();
        lastTransferSuccess = false;
    }
    
    /**
     * Get current metrics as a map.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> metrics = new HashMap<>();
        
        metrics.put("protocol", protocolName);
        metrics.put("startTime", startTime.toString());
        metrics.put("uptime", Duration.between(startTime, Instant.now()).toString());
        
        // Transfer counts
        long total = totalTransfers.sum();
        long successful = successfulTransfers.sum();
        long failed = failedTransfers.sum();
        long active = activeTransfers.get();
        
        metrics.put("totalTransfers", total);
        metrics.put("successfulTransfers", successful);
        metrics.put("failedTransfers", failed);
        metrics.put("activeTransfers", active);
        
        // Success rate
        if (total > 0) {
            double successRate = (successful * 100.0) / total;
            metrics.put("successRate", String.format("%.2f%%", successRate));
        } else {
            metrics.put("successRate", "N/A");
        }
        
        // Byte metrics
        long totalBytes = totalBytesTransferred.sum();
        metrics.put("totalBytesTransferred", totalBytes);
        metrics.put("totalBytesTransferredMB", String.format("%.2f MB", totalBytes / (1024.0 * 1024.0)));
        
        // Duration metrics
        if (successful > 0) {
            long avgDurationMs = totalDurationMs.sum() / successful;
            metrics.put("averageDurationMs", avgDurationMs);
            metrics.put("minDurationMs", minDurationMs.get() == Long.MAX_VALUE ? 0 : minDurationMs.get());
            metrics.put("maxDurationMs", maxDurationMs.get());
        }
        
        // Throughput
        Duration uptime = Duration.between(startTime, Instant.now());
        if (uptime.getSeconds() > 0) {
            double transfersPerSecond = total / (double) uptime.getSeconds();
            double bytesPerSecond = totalBytes / (double) uptime.getSeconds();
            metrics.put("transfersPerSecond", String.format("%.2f", transfersPerSecond));
            metrics.put("bytesPerSecond", String.format("%.2f", bytesPerSecond));
        }
        
        // Last transfer
        if (lastTransferTime != null) {
            metrics.put("lastTransferTime", lastTransferTime.toString());
            metrics.put("lastTransferSuccess", lastTransferSuccess);
        }
        
        // Error breakdown
        if (!errorCounts.isEmpty()) {
            Map<String, Long> errors = new HashMap<>();
            errorCounts.forEach((type, count) -> errors.put(type, count.sum()));
            metrics.put("errors", errors);
        }
        
        return metrics;
    }
}

