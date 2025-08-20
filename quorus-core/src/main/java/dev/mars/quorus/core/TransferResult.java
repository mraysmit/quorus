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
 * Immutable value object representing the final result of a completed file transfer operation.
 *
 * <p>This class encapsulates the complete outcome of a transfer job, including success/failure
 * status, performance metrics, timing information, and error details. It serves as a permanent
 * record of the transfer operation and is used for reporting, auditing, and analysis.</p>
 *
 * <h3>Purpose and Usage:</h3>
 * <ul>
 *   <li><strong>Final Record:</strong> Immutable snapshot of transfer completion state</li>
 *   <li><strong>Reporting:</strong> Provides data for transfer reports and analytics</li>
 *   <li><strong>Auditing:</strong> Permanent record for compliance and tracking</li>
 *   <li><strong>API Responses:</strong> Returned to clients for completed transfers</li>
 *   <li><strong>Workflow Integration:</strong> Used by workflow engines for dependency tracking</li>
 * </ul>
 *
 * <h3>Key Information Captured:</h3>
 * <ul>
 *   <li><strong>Outcome:</strong> Final status (COMPLETED, FAILED, CANCELLED)</li>
 *   <li><strong>Performance:</strong> Bytes transferred, duration, transfer rate</li>
 *   <li><strong>Timing:</strong> Start time, end time, total duration</li>
 *   <li><strong>Integrity:</strong> Actual checksum for verification</li>
 *   <li><strong>Error Details:</strong> Error messages and exception information</li>
 * </ul>
 *
 * <h3>Immutability and Thread Safety:</h3>
 * <p>This class is immutable and thread-safe. All fields are final and the class provides
 * no mutator methods. Once created, a TransferResult cannot be modified, making it safe
 * to share across threads and store in collections.</p>
 *
 * <h3>Builder Pattern:</h3>
 * <p>Instances are created using the Builder pattern, which provides a fluent API and
 * ensures proper validation of required fields. The builder validates that essential
 * information like request ID and final status are provided.</p>
 *
 * <h3>Performance Metrics:</h3>
 * <p>The class provides calculated metrics such as transfer duration and average transfer
 * rate based on the timing and byte count information. These metrics are useful for
 * performance analysis and system optimization.</p>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * TransferResult result = TransferResult.builder()
 *     .requestId("transfer-123")
 *     .finalStatus(TransferStatus.COMPLETED)
 *     .bytesTransferred(1048576)
 *     .startTime(startTime)
 *     .endTime(endTime)
 *     .actualChecksum("sha256:abc123...")
 *     .build();
 *
 * // Access performance metrics
 * Duration duration = result.getDuration();
 * double rate = result.getAverageTransferRate();
 * boolean successful = result.isSuccessful();
 * }</pre>
 *
 * @author Quorus Development Team
 * @since 1.0
 * @see TransferJob
 * @see TransferRequest
 * @see TransferStatus
 */
public final class TransferResult {

    /**
     * Unique identifier of the transfer request that produced this result.
     * Used for correlation with original requests and tracking across systems.
     */
    private final String requestId;

    /**
     * Final status of the transfer operation when it completed.
     * Indicates whether the transfer succeeded, failed, or was cancelled.
     */
    private final TransferStatus finalStatus;

    /**
     * Total number of bytes successfully transferred.
     * Used for calculating transfer rates and verifying completeness.
     */
    private final long bytesTransferred;

    /**
     * Timestamp when the transfer operation actually started.
     * Used for calculating total transfer duration and performance metrics.
     */
    private final Instant startTime;

    /**
     * Timestamp when the transfer operation completed or failed.
     * Used for calculating total transfer duration and completion tracking.
     */
    private final Instant endTime;

    /**
     * Actual checksum of the transferred file for integrity verification.
     * Can be compared against expected checksum to verify transfer integrity.
     */
    private final String actualChecksum;

    /**
     * Human-readable error message if the transfer failed.
     * Provides user-friendly description of what went wrong.
     */
    private final String errorMessage;

    /**
     * The actual exception that caused the transfer to fail.
     * Provides technical details for debugging and error analysis.
     */
    private final Throwable cause;

    /**
     * Private constructor that builds a TransferResult from a Builder instance.
     *
     * <p>This constructor validates required fields and creates the immutable
     * TransferResult instance. It ensures that essential fields like request ID
     * and final status are not null, while allowing optional fields to be null.</p>
     *
     * <p><strong>Validation:</strong></p>
     * <ul>
     *   <li>Request ID must not be null</li>
     *   <li>Final status must not be null</li>
     *   <li>Other fields are optional and may be null</li>
     * </ul>
     *
     * @param builder the Builder instance containing the transfer result data
     * @throws NullPointerException if requestId or finalStatus is null
     */
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

    // ========== GETTER METHODS ==========

    /**
     * Returns the unique identifier of the transfer request.
     *
     * @return the request ID, never null
     */
    public String getRequestId() { return requestId; }

    /**
     * Returns the final status of the transfer operation.
     *
     * @return the final transfer status, never null
     */
    public TransferStatus getFinalStatus() { return finalStatus; }

    /**
     * Returns the total number of bytes successfully transferred.
     *
     * @return the bytes transferred count, always >= 0
     */
    public long getBytesTransferred() { return bytesTransferred; }

    /**
     * Returns the timestamp when the transfer started, if available.
     *
     * @return Optional containing the start time, or empty if not recorded
     */
    public Optional<Instant> getStartTime() { return Optional.ofNullable(startTime); }

    /**
     * Returns the timestamp when the transfer completed, if available.
     *
     * @return Optional containing the end time, or empty if not recorded
     */
    public Optional<Instant> getEndTime() { return Optional.ofNullable(endTime); }

    /**
     * Returns the actual checksum of the transferred file, if calculated.
     *
     * @return Optional containing the checksum, or empty if not calculated
     */
    public Optional<String> getActualChecksum() { return Optional.ofNullable(actualChecksum); }

    /**
     * Returns the error message if the transfer failed.
     *
     * @return Optional containing the error message, or empty if no error
     */
    public Optional<String> getErrorMessage() { return Optional.ofNullable(errorMessage); }

    /**
     * Returns the exception that caused the transfer to fail.
     *
     * @return Optional containing the exception, or empty if no error
     */
    public Optional<Throwable> getCause() { return Optional.ofNullable(cause); }

    // ========== CALCULATED METRICS ==========

    /**
     * Calculates the total duration of the transfer operation.
     *
     * <p>This method computes the time elapsed between the start and end of the
     * transfer operation. The duration is useful for performance analysis,
     * reporting, and calculating transfer rates.</p>
     *
     * <p><strong>Calculation:</strong> Duration = endTime - startTime</p>
     *
     * <p><strong>Return Conditions:</strong></p>
     * <ul>
     *   <li>Present: Both start and end times are available</li>
     *   <li>Empty: Either start time or end time is missing</li>
     * </ul>
     *
     * @return Optional containing the transfer duration, or empty if times unavailable
     */
    public Optional<Duration> getDuration() {
        if (startTime != null && endTime != null) {
            return Optional.of(Duration.between(startTime, endTime));
        }
        return Optional.empty();
    }

    /**
     * Calculates the average transfer rate in bytes per second.
     *
     * <p>This method computes the average data transfer rate over the entire
     * transfer duration. The rate is calculated as total bytes transferred
     * divided by total transfer time.</p>
     *
     * <p><strong>Calculation:</strong> Rate = bytesTransferred / duration.toSeconds()</p>
     *
     * <p><strong>Return Conditions:</strong></p>
     * <ul>
     *   <li>Present: Duration is available and non-zero</li>
     *   <li>Empty: Duration unavailable or zero (instantaneous transfer)</li>
     * </ul>
     *
     * <p><strong>Usage:</strong> Useful for performance analysis, bandwidth
     * utilization reporting, and transfer optimization.</p>
     *
     * @return Optional containing the average transfer rate in bytes/second,
     *         or empty if duration unavailable or zero
     */
    public Optional<Double> getAverageRateBytesPerSecond() {
        return getDuration()
                .filter(duration -> !duration.isZero())
                .map(duration -> (double) bytesTransferred / duration.toMillis() * 1000);
    }

    /**
     * Determines if the transfer completed successfully.
     *
     * <p>This method provides a simple boolean check for transfer success,
     * which is useful for conditional logic, reporting, and workflow decisions.
     * A transfer is considered successful only if it reached COMPLETED status.</p>
     *
     * <p><strong>Success Criteria:</strong></p>
     * <ul>
     *   <li>Final status must be COMPLETED</li>
     *   <li>FAILED and CANCELLED are not considered successful</li>
     *   <li>PENDING, IN_PROGRESS, PAUSED should not appear in final results</li>
     * </ul>
     *
     * @return true if the transfer completed successfully, false otherwise
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
