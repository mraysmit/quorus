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


import java.io.Serializable;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
/**
 * Description for TransferJob
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-17
 */

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TransferJob implements Serializable {

    private static final long serialVersionUID = 1L;

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

    @JsonCreator
    public static TransferJob create(
            @JsonProperty("jobId") String jobId,
            @JsonProperty("sourceUri") URI sourceUri,
            @JsonProperty("destinationPath") String destinationPath,
            @JsonProperty("totalBytes") long totalBytes,
            @JsonProperty("description") String description) {
        
        TransferRequest.Builder builder = TransferRequest.builder()
                .requestId(jobId)
                .sourceUri(sourceUri)
                .destinationPath(destinationPath)
                .expectedSize(totalBytes);
                
        if (description != null) {
            builder.metadata("description", description);
        }
        
        return new TransferJob(builder.build());
    }

    // ========== GETTER METHODS ==========

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

    // ========== STATUS MANAGEMENT METHODS ==========

    public boolean start() {
        if (status.compareAndSet(TransferStatus.PENDING, TransferStatus.IN_PROGRESS)) {
            startTime.set(Instant.now());
            updateLastUpdateTime();
            return true;
        }
        return false;
    }

    public boolean pause() {
        TransferStatus current = status.get();
        if (current == TransferStatus.IN_PROGRESS) {
            status.set(TransferStatus.PAUSED);
            updateLastUpdateTime();
            return true;
        }
        return false;
    }

    public boolean resume() {
        if (status.compareAndSet(TransferStatus.PAUSED, TransferStatus.IN_PROGRESS)) {
            updateLastUpdateTime();
            return true;
        }
        return false;
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

    // ========== PROGRESS TRACKING METHODS ==========

    public void updateProgress(long bytesTransferred) {
        if (bytesTransferred < 0) {
            throw new IllegalArgumentException("Bytes transferred cannot be negative");
        }
        this.bytesTransferred.set(bytesTransferred);
        updateLastUpdateTime();
    }

    public void addBytesTransferred(long additionalBytes) {
        if (additionalBytes < 0) {
            throw new IllegalArgumentException("Additional bytes cannot be negative");
        }
        this.bytesTransferred.addAndGet(additionalBytes);
        updateLastUpdateTime();
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
        updateLastUpdateTime();
    }

    public double getProgressPercentage() {
        if (totalBytes <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) bytesTransferred.get() / totalBytes);
    }

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

    // ========== UTILITY METHODS ==========

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

    // ========== OBJECT METHODS ==========

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

    /**
     * Returns a string representation of this TransferJob for debugging and logging.
     *
     * <p>The string includes key information about the transfer job including
     * job ID, current status, progress percentage, and byte counts. This
     * representation is designed to be useful for debugging, logging, and
     * monitoring purposes.</p>
     *
     * <p><strong>Format:</strong> The string includes progress as a percentage
     * for easy readability, and byte counts for detailed analysis.</p>
     *
     * @return a string representation of this transfer job
     */
    @Override
    public String toString() {
        return "TransferJob{" +
                "jobId='" + getJobId() + '\'' +
                ", status=" + getStatus() +
                ", progress=" + String.format("%.1f%%", getProgressPercentage() * 100) +
                ", bytesTransferred=" + getBytesTransferred() +
                ", totalBytes=" + getTotalBytes() +
                ", startTime=" + getStartTime() +
                ", lastUpdate=" + getLastUpdateTime() +
                '}';
    }
}
