package dev.mars.quorus.transfer;

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


import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks progress of a file transfer operation with rate calculation and ETA estimation.
 * Thread-safe implementation for concurrent access during transfers.
 *
 * <p>A tracker created from a {@link TransferContext} is bound to that transfer (ENG-10): the total
 * and progress it records are also recorded on the context's job, which the engine and its callers
 * read, and {@link #stopRequested()} tells the adapter's copy loop when to stop.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-17
 * @version 1.0
 */
public class ProgressTracker {
    private final String jobId;
    private final AtomicLong totalBytes;
    private final AtomicLong transferredBytes;
    private final AtomicReference<Instant> startTime;
    private final AtomicReference<Instant> lastUpdateTime;
    private final AtomicLong lastTransferredBytes;
    private final AtomicReference<Double> currentRate; // bytes per second
    private final TransferContext context;               // null when not bound to a transfer

    public ProgressTracker(String jobId) {
        this(jobId, null);
    }

    /** Creates a tracker bound to a transfer: progress is also recorded on its job. */
    public ProgressTracker(TransferContext context) {
        this(context.getJobId(), context);
    }

    private ProgressTracker(String jobId, TransferContext context) {
        this.jobId = jobId;
        this.context = context;
        this.totalBytes = new AtomicLong(-1);
        this.transferredBytes = new AtomicLong(0);
        this.startTime = new AtomicReference<>();
        this.lastUpdateTime = new AtomicReference<>();
        this.lastTransferredBytes = new AtomicLong(0);
        this.currentRate = new AtomicReference<>(0.0);
    }
    
    public String getJobId() {
        return jobId;
    }
    
    public void start() {
        Instant now = Instant.now();
        startTime.set(now);
        lastUpdateTime.set(now);
    }
    
    public void setTotalBytes(long totalBytes) {
        this.totalBytes.set(totalBytes);
        if (context != null && totalBytes >= 0) {
            context.getJob().setTotalBytes(totalBytes);
        }
    }
    
    public long getTotalBytes() {
        return totalBytes.get();
    }
    
    public void updateProgress(long bytesTransferred) {
        Instant now = Instant.now();
        this.transferredBytes.set(bytesTransferred);
        if (context != null) {
            context.getJob().updateProgress(bytesTransferred);
        }

        // Update rate calculation
        updateTransferRate(bytesTransferred, now);
        
        lastUpdateTime.set(now);
    }
    
    public void addBytesTransferred(long additionalBytes) {
        long newTotal = transferredBytes.addAndGet(additionalBytes);
        updateProgress(newTotal);
    }
    
    /**
     * Returns whether the copy loop should stop: the transfer was cancelled or paused, or the
     * thread was interrupted. An adapter checks this between buffers.
     */
    public boolean stopRequested() {
        return (context != null && !context.shouldContinue()) || Thread.currentThread().isInterrupted();
    }

    public long getTransferredBytes() {
        return transferredBytes.get();
    }
    
    public double getProgressPercentage() {
        long total = totalBytes.get();
        if (total <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) transferredBytes.get() / total);
    }
    
    public double getCurrentRateBytesPerSecond() {
        return currentRate.get();
    }
    
    public double getCurrentRateMBytesPerSecond() {
        return getCurrentRateBytesPerSecond() / (1024.0 * 1024.0);
    }
    
    public long getEstimatedRemainingSeconds() {
        long total = totalBytes.get();
        long transferred = transferredBytes.get();
        double rate = currentRate.get();
        
        if (total <= 0 || transferred <= 0 || rate <= 0) {
            return -1;
        }
        
        long remaining = total - transferred;
        return (long) (remaining / rate);
    }
    
    public String getEstimatedRemainingTime() {
        long seconds = getEstimatedRemainingSeconds();
        if (seconds < 0) {
            return "Unknown";
        }
        
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        }
    }
    
    public Instant getStartTime() {
        return startTime.get();
    }
    
    public Instant getLastUpdateTime() {
        return lastUpdateTime.get();
    }
    
    public long getElapsedTimeSeconds() {
        Instant start = startTime.get();
        if (start == null) {
            return 0;
        }
        return java.time.Duration.between(start, Instant.now()).toSeconds();
    }
    
    public double getAverageRateBytesPerSecond() {
        Instant start = startTime.get();
        if (start == null) {
            return 0.0;
        }
        
        long elapsedMs = java.time.Duration.between(start, Instant.now()).toMillis();
        if (elapsedMs <= 0) {
            return 0.0;
        }
        
        return (double) transferredBytes.get() / elapsedMs * 1000.0;
    }
    
    private void updateTransferRate(long currentBytes, Instant now) {
        Instant lastUpdate = lastUpdateTime.get();
        if (lastUpdate == null) {
            return;
        }
        
        long timeDiffMs = java.time.Duration.between(lastUpdate, now).toMillis();
        if (timeDiffMs < 1000) { // Don't update rate too frequently
            return;
        }
        
        long bytesDiff = currentBytes - lastTransferredBytes.get();
        if (bytesDiff > 0 && timeDiffMs > 0) {
            double instantRate = (double) bytesDiff / timeDiffMs * 1000.0;
            
            // Smooth the rate using exponential moving average
            double currentRateValue = currentRate.get();
            double smoothedRate = currentRateValue == 0.0 ? instantRate : 
                (currentRateValue * 0.7 + instantRate * 0.3);
            
            currentRate.set(smoothedRate);
            lastTransferredBytes.set(currentBytes);
        }
    }
    
    @Override
    public String toString() {
        return String.format("ProgressTracker{jobId='%s', progress=%.1f%%, rate=%.2f MB/s, ETA=%s}",
                jobId,
                getProgressPercentage() * 100,
                getCurrentRateMBytesPerSecond(),
                getEstimatedRemainingTime());
    }
}
