package dev.mars.quorus.core;

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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents an active file transfer job that tracks the progress and state
 * of a transfer operation. This is a mutable object that gets updated during
 * the transfer process.
 */
public class TransferJob {
    private final TransferRequest request;
    private final AtomicReference<TransferStatus> status;
    private final AtomicLong bytesTransferred;
    private final AtomicReference<Instant> startTime;
    private final AtomicReference<Instant> lastUpdateTime;
    private final AtomicReference<String> errorMessage;
    private final AtomicReference<Throwable> lastError;
    private volatile long totalBytes;
    private volatile String actualChecksum;
    
    public TransferJob(TransferRequest request) {
        this.request = Objects.requireNonNull(request, "Transfer request cannot be null");
        this.status = new AtomicReference<>(TransferStatus.PENDING);
        this.bytesTransferred = new AtomicLong(0);
        this.startTime = new AtomicReference<>();
        this.lastUpdateTime = new AtomicReference<>(Instant.now());
        this.errorMessage = new AtomicReference<>();
        this.lastError = new AtomicReference<>();
        this.totalBytes = request.getExpectedSize();
    }
    
    // Getters
    public TransferRequest getRequest() { return request; }
    public String getJobId() { return request.getRequestId(); }
    public TransferStatus getStatus() { return status.get(); }
    public long getBytesTransferred() { return bytesTransferred.get(); }
    public long getTotalBytes() { return totalBytes; }
    public Instant getStartTime() { return startTime.get(); }
    public Instant getLastUpdateTime() { return lastUpdateTime.get(); }
    public String getErrorMessage() { return errorMessage.get(); }
    public Throwable getLastError() { return lastError.get(); }
    public String getActualChecksum() { return actualChecksum; }
    
    // Status management
    public void start() {
        if (status.compareAndSet(TransferStatus.PENDING, TransferStatus.IN_PROGRESS)) {
            startTime.set(Instant.now());
            updateLastUpdateTime();
        }
    }
    
    public void pause() {
        TransferStatus current = status.get();
        if (current == TransferStatus.IN_PROGRESS) {
            status.set(TransferStatus.PAUSED);
            updateLastUpdateTime();
        }
    }
    
    public void resume() {
        if (status.compareAndSet(TransferStatus.PAUSED, TransferStatus.IN_PROGRESS)) {
            updateLastUpdateTime();
        }
    }
    
    public void complete(String checksum) {
        this.actualChecksum = checksum;
        status.set(TransferStatus.COMPLETED);
        updateLastUpdateTime();
    }
    
    public void fail(String message, Throwable cause) {
        this.errorMessage.set(message);
        this.lastError.set(cause);
        status.set(TransferStatus.FAILED);
        updateLastUpdateTime();
    }
    
    public void cancel() {
        status.set(TransferStatus.CANCELLED);
        updateLastUpdateTime();
    }
    
    // Progress tracking
    public void updateProgress(long bytesTransferred) {
        this.bytesTransferred.set(bytesTransferred);
        updateLastUpdateTime();
    }
    
    public void addBytesTransferred(long additionalBytes) {
        this.bytesTransferred.addAndGet(additionalBytes);
        updateLastUpdateTime();
    }
    
    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
        updateLastUpdateTime();
    }
    
    /**
     * Calculate the progress percentage (0.0 to 1.0)
     */
    public double getProgressPercentage() {
        if (totalBytes <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) bytesTransferred.get() / totalBytes);
    }
    
    /**
     * Estimate remaining time based on current transfer rate
     */
    public long getEstimatedRemainingSeconds() {
        if (totalBytes <= 0 || startTime.get() == null) {
            return -1;
        }
        
        long transferred = bytesTransferred.get();
        if (transferred <= 0) {
            return -1;
        }
        
        long elapsedSeconds = java.time.Duration.between(startTime.get(), Instant.now()).toSeconds();
        if (elapsedSeconds <= 0) {
            return -1;
        }
        
        double rate = (double) transferred / elapsedSeconds;
        long remaining = totalBytes - transferred;
        
        return (long) (remaining / rate);
    }
    
    /**
     * Create a TransferResult from the current job state
     */
    public TransferResult toResult() {
        return TransferResult.builder()
                .requestId(getJobId())
                .finalStatus(getStatus())
                .bytesTransferred(getBytesTransferred())
                .startTime(getStartTime())
                .endTime(getLastUpdateTime())
                .actualChecksum(getActualChecksum())
                .errorMessage(getErrorMessage())
                .cause(getLastError())
                .build();
    }
    
    private void updateLastUpdateTime() {
        lastUpdateTime.set(Instant.now());
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransferJob that = (TransferJob) o;
        return Objects.equals(getJobId(), that.getJobId());
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(getJobId());
    }
    
    @Override
    public String toString() {
        return "TransferJob{" +
                "jobId='" + getJobId() + '\'' +
                ", status=" + getStatus() +
                ", progress=" + String.format("%.1f%%", getProgressPercentage() * 100) +
                ", bytesTransferred=" + getBytesTransferred() +
                ", totalBytes=" + getTotalBytes() +
                '}';
    }
}
