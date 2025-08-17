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


import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents the result of a completed file transfer operation.
 * Contains information about the transfer outcome, timing, and any errors.
 */
public final class TransferResult {
    private final String requestId;
    private final TransferStatus finalStatus;
    private final long bytesTransferred;
    private final Instant startTime;
    private final Instant endTime;
    private final String actualChecksum;
    private final String errorMessage;
    private final Throwable cause;
    
    private TransferResult(Builder builder) {
        this.requestId = Objects.requireNonNull(builder.requestId, "Request ID cannot be null");
        this.finalStatus = Objects.requireNonNull(builder.finalStatus, "Final status cannot be null");
        this.bytesTransferred = builder.bytesTransferred;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.actualChecksum = builder.actualChecksum;
        this.errorMessage = builder.errorMessage;
        this.cause = builder.cause;
    }
    
    public String getRequestId() { return requestId; }
    public TransferStatus getFinalStatus() { return finalStatus; }
    public long getBytesTransferred() { return bytesTransferred; }
    public Optional<Instant> getStartTime() { return Optional.ofNullable(startTime); }
    public Optional<Instant> getEndTime() { return Optional.ofNullable(endTime); }
    public Optional<String> getActualChecksum() { return Optional.ofNullable(actualChecksum); }
    public Optional<String> getErrorMessage() { return Optional.ofNullable(errorMessage); }
    public Optional<Throwable> getCause() { return Optional.ofNullable(cause); }
    
    /**
     * Calculate the duration of the transfer if both start and end times are available
     */
    public Optional<Duration> getDuration() {
        if (startTime != null && endTime != null) {
            return Optional.of(Duration.between(startTime, endTime));
        }
        return Optional.empty();
    }
    
    /**
     * Calculate the average transfer rate in bytes per second
     */
    public Optional<Double> getAverageRateBytesPerSecond() {
        return getDuration()
                .filter(duration -> !duration.isZero())
                .map(duration -> (double) bytesTransferred / duration.toMillis() * 1000);
    }
    
    /**
     * Check if the transfer was successful
     */
    public boolean isSuccessful() {
        return finalStatus == TransferStatus.COMPLETED;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String requestId;
        private TransferStatus finalStatus;
        private long bytesTransferred;
        private Instant startTime;
        private Instant endTime;
        private String actualChecksum;
        private String errorMessage;
        private Throwable cause;
        
        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }
        
        public Builder finalStatus(TransferStatus finalStatus) {
            this.finalStatus = finalStatus;
            return this;
        }
        
        public Builder bytesTransferred(long bytesTransferred) {
            this.bytesTransferred = bytesTransferred;
            return this;
        }
        
        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }
        
        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }
        
        public Builder actualChecksum(String actualChecksum) {
            this.actualChecksum = actualChecksum;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }
        
        public TransferResult build() {
            return new TransferResult(this);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransferResult that = (TransferResult) o;
        return Objects.equals(requestId, that.requestId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(requestId);
    }
    
    @Override
    public String toString() {
        return "TransferResult{" +
                "requestId='" + requestId + '\'' +
                ", finalStatus=" + finalStatus +
                ", bytesTransferred=" + bytesTransferred +
                ", duration=" + getDuration().map(Duration::toString).orElse("unknown") +
                '}';
    }
}
