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
 * Mutable representation of an active file transfer job that tracks real-time progress and state.
 *
 * <p>This class encapsulates the runtime state of a file transfer operation, providing thread-safe
 * access to progress information, status updates, and error conditions. Unlike the immutable
 * {@link TransferRequest}, this class is designed to be updated throughout the transfer lifecycle
 * by the transfer engine and monitored by external systems.</p>
 *
 * <h3>Thread Safety:</h3>
 * <p>This class is thread-safe and designed for concurrent access. All mutable state is protected
 * by atomic references and volatile fields, allowing safe updates from transfer threads while
 * providing consistent reads from monitoring threads.</p>
 *
 * <h3>State Management:</h3>
 * <p>The transfer job maintains several types of state:</p>
 * <ul>
 *   <li><strong>Status:</strong> Current transfer state (PENDING, IN_PROGRESS, COMPLETED, etc.)</li>
 *   <li><strong>Progress:</strong> Bytes transferred and total size for percentage calculation</li>
 *   <li><strong>Timing:</strong> Start time and last update timestamp for rate calculation</li>
 *   <li><strong>Error Handling:</strong> Error messages and exception details for debugging</li>
 * </ul>
 *
 * <h3>Progress Tracking:</h3>
 * <p>Progress is tracked through atomic counters that can be safely updated from transfer threads
 * and read from monitoring systems. The class provides calculated fields like progress percentage
 * and transfer rate based on the underlying atomic values.</p>
 *
 * <h3>Integration Points:</h3>
 * <ul>
 *   <li><strong>Transfer Engine:</strong> Updates progress and status during transfer execution</li>
 *   <li><strong>REST API:</strong> Provides status and progress information to clients</li>
 *   <li><strong>Distributed State:</strong> Synchronized across cluster via Raft consensus</li>
 *   <li><strong>Monitoring:</strong> Real-time metrics and alerting systems</li>
 *   <li><strong>Workflow Engine:</strong> Dependency tracking and sequencing</li>
 * </ul>
 *
 * <h3>Lifecycle:</h3>
 * <pre>
 * 1. Created with PENDING status
 * 2. Status updated to IN_PROGRESS when transfer starts
 * 3. Progress updated continuously during transfer
 * 4. Final status set to COMPLETED, FAILED, or CANCELLED
 * 5. Job archived or cleaned up after completion
 * </pre>
 *
 * @author Quorus Development Team
 * @since 1.0
 * @see TransferRequest
 * @see TransferStatus
 * @see TransferResult
 */
public class TransferJob {

    /**
     * The immutable transfer request that initiated this job.
     * Contains source URI, destination path, and transfer parameters.
     */
    private final TransferRequest request;

    /**
     * Current status of the transfer operation.
     * Thread-safe atomic reference allowing concurrent status updates and reads.
     */
    private final AtomicReference<TransferStatus> status;

    /**
     * Number of bytes successfully transferred so far.
     * Atomic counter updated continuously during transfer for progress tracking.
     */
    private final AtomicLong bytesTransferred;

    /**
     * Timestamp when the transfer actually started (moved from PENDING to IN_PROGRESS).
     * Used for calculating total transfer time and transfer rate.
     */
    private final AtomicReference<Instant> startTime;

    /**
     * Timestamp of the last progress update or status change.
     * Used for detecting stalled transfers and calculating current transfer rate.
     */
    private final AtomicReference<Instant> lastUpdateTime;

    /**
     * Human-readable error message if the transfer failed.
     * Contains user-friendly description of what went wrong.
     */
    private final AtomicReference<String> errorMessage;

    /**
     * The actual exception that caused the transfer to fail.
     * Used for detailed error analysis and debugging.
     */
    private final AtomicReference<Throwable> lastError;

    /**
     * Total size of the file being transferred in bytes.
     * May be updated during transfer if initially unknown (-1).
     */
    private volatile long totalBytes;

    /**
     * Actual checksum of the transferred file after completion.
     * Used for integrity verification against expected checksum.
     */
    private volatile String actualChecksum;

    /**
     * Creates a new transfer job from the given transfer request.
     *
     * <p>The job is initialized in PENDING status with zero bytes transferred.
     * All atomic references are initialized to safe default values, and the
     * last update time is set to the current timestamp.</p>
     *
     * <p><strong>Initial State:</strong></p>
     * <ul>
     *   <li>Status: PENDING</li>
     *   <li>Bytes transferred: 0</li>
     *   <li>Start time: null (set when transfer actually starts)</li>
     *   <li>Last update time: current timestamp</li>
     *   <li>Total bytes: from request expected size (may be -1 if unknown)</li>
     * </ul>
     *
     * @param request the transfer request containing source, destination, and parameters
     * @throws NullPointerException if request is null
     */
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

    // ========== GETTER METHODS ==========

    /**
     * Returns the immutable transfer request that initiated this job.
     *
     * @return the original transfer request, never null
     */
    public TransferRequest getRequest() { return request; }

    /**
     * Returns the unique job identifier.
     *
     * <p>This is a convenience method that returns the request ID from the
     * underlying transfer request. The job ID is used for tracking and
     * correlation across distributed components.</p>
     *
     * @return the unique job identifier, never null
     */
    public String getJobId() { return request.getRequestId(); }

    /**
     * Returns the current status of the transfer operation.
     *
     * <p>This method provides a thread-safe read of the current transfer status.
     * The status may change between calls as the transfer progresses.</p>
     *
     * @return the current transfer status, never null
     */
    public TransferStatus getStatus() { return status.get(); }

    /**
     * Returns the number of bytes successfully transferred so far.
     *
     * <p>This value is updated continuously during transfer and provides
     * the basis for progress percentage calculations. The value is thread-safe
     * and monotonically increasing during active transfers.</p>
     *
     * @return the number of bytes transferred, always >= 0
     */
    public long getBytesTransferred() { return bytesTransferred.get(); }

    /**
     * Returns the total size of the file being transferred.
     *
     * <p>This may be the expected size from the original request, or it may
     * be updated during transfer if the actual size becomes known. A value
     * of -1 indicates the total size is unknown.</p>
     *
     * @return the total file size in bytes, or -1 if unknown
     */
    public long getTotalBytes() { return totalBytes; }

    /**
     * Returns the timestamp when the transfer actually started.
     *
     * <p>This is set when the status transitions from PENDING to IN_PROGRESS.
     * It's used for calculating total transfer time and average transfer rate.</p>
     *
     * @return the start timestamp, or null if transfer hasn't started yet
     */
    public Instant getStartTime() { return startTime.get(); }

    /**
     * Returns the timestamp of the last progress update or status change.
     *
     * <p>This timestamp is updated whenever progress is reported or status
     * changes. It's used for detecting stalled transfers and calculating
     * current transfer rates.</p>
     *
     * @return the last update timestamp, never null
     */
    public Instant getLastUpdateTime() { return lastUpdateTime.get(); }

    /**
     * Returns the human-readable error message if the transfer failed.
     *
     * @return the error message, or null if no error occurred
     */
    public String getErrorMessage() { return errorMessage.get(); }

    /**
     * Returns the actual exception that caused the transfer to fail.
     *
     * <p>This provides detailed technical information about the failure
     * for debugging and error analysis purposes.</p>
     *
     * @return the exception that caused failure, or null if no error occurred
     */
    public Throwable getLastError() { return lastError.get(); }

    /**
     * Returns the actual checksum of the transferred file.
     *
     * <p>This is calculated after transfer completion and can be compared
     * against the expected checksum for integrity verification.</p>
     *
     * @return the actual file checksum, or null if not calculated yet
     */
    public String getActualChecksum() { return actualChecksum; }

    // ========== STATUS MANAGEMENT METHODS ==========

    /**
     * Starts the transfer by transitioning from PENDING to IN_PROGRESS status.
     *
     * <p>This method uses atomic compare-and-set to ensure thread-safe status
     * transitions. It will only succeed if the current status is PENDING.
     * When successful, it records the start time and updates the last update timestamp.</p>
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe and can be called
     * concurrently. Only one thread will successfully transition the status.</p>
     *
     * <p><strong>Side Effects:</strong></p>
     * <ul>
     *   <li>Sets start time to current timestamp</li>
     *   <li>Updates last update time</li>
     *   <li>Status becomes IN_PROGRESS</li>
     * </ul>
     *
     * @return true if the transfer was started, false if already started or not in PENDING state
     */
    public boolean start() {
        if (status.compareAndSet(TransferStatus.PENDING, TransferStatus.IN_PROGRESS)) {
            startTime.set(Instant.now());
            updateLastUpdateTime();
            return true;
        }
        return false;
    }

    /**
     * Pauses an active transfer by transitioning from IN_PROGRESS to PAUSED status.
     *
     * <p>This method temporarily suspends the transfer while preserving the current
     * progress. The transfer can be resumed later from the same position. This is
     * useful for bandwidth management or scheduled maintenance windows.</p>
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe but uses a
     * read-then-write pattern. Multiple concurrent calls may result in only
     * one successful pause operation.</p>
     *
     * @return true if the transfer was paused, false if not currently in progress
     */
    public boolean pause() {
        TransferStatus current = status.get();
        if (current == TransferStatus.IN_PROGRESS) {
            status.set(TransferStatus.PAUSED);
            updateLastUpdateTime();
            return true;
        }
        return false;
    }

    /**
     * Resumes a paused transfer by transitioning from PAUSED to IN_PROGRESS status.
     *
     * <p>This method restarts a previously paused transfer from where it left off.
     * The transfer will continue with the same progress and settings as before
     * the pause.</p>
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe and uses
     * atomic compare-and-set to ensure only one thread can resume the transfer.</p>
     *
     * @return true if the transfer was resumed, false if not currently paused
     */
    public boolean resume() {
        if (status.compareAndSet(TransferStatus.PAUSED, TransferStatus.IN_PROGRESS)) {
            updateLastUpdateTime();
            return true;
        }
        return false;
    }

    /**
     * Marks the transfer as successfully completed with optional checksum verification.
     *
     * <p>This method transitions the transfer to COMPLETED status and records the
     * actual checksum of the transferred file. This is a terminal state - no
     * further status transitions are possible.</p>
     *
     * <p><strong>Checksum Verification:</strong> If a checksum is provided, it can
     * be compared against the expected checksum from the original request for
     * integrity verification.</p>
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe and can be
     * called concurrently, though typically only called once per transfer.</p>
     *
     * @param checksum the actual checksum of the transferred file, may be null
     */
    public void complete(String checksum) {
        this.actualChecksum = checksum;
        status.set(TransferStatus.COMPLETED);
        updateLastUpdateTime();
    }

    /**
     * Marks the transfer as failed with error details.
     *
     * <p>This method transitions the transfer to FAILED status and records both
     * a human-readable error message and the technical exception details. This
     * is a terminal state, though the transfer may be retried by creating a
     * new transfer job.</p>
     *
     * <p><strong>Error Information:</strong></p>
     * <ul>
     *   <li>Message: User-friendly description of the failure</li>
     *   <li>Cause: Technical exception for debugging and analysis</li>
     * </ul>
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe and can be
     * called concurrently from error handling code.</p>
     *
     * @param message human-readable error description, may be null
     * @param cause the exception that caused the failure, may be null
     */
    public void fail(String message, Throwable cause) {
        this.errorMessage.set(message);
        this.lastError.set(cause);
        status.set(TransferStatus.FAILED);
        updateLastUpdateTime();
    }

    /**
     * Cancels the transfer by transitioning to CANCELLED status.
     *
     * <p>This method marks the transfer as cancelled, either by user request
     * or system policy. This is a terminal state - no further status transitions
     * are possible. Any partial transfer data may be cleaned up.</p>
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe and can be
     * called concurrently from cancellation handlers.</p>
     */
    public void cancel() {
        status.set(TransferStatus.CANCELLED);
        updateLastUpdateTime();
    }

    // ========== PROGRESS TRACKING METHODS ==========

    /**
     * Updates the total number of bytes transferred to an absolute value.
     *
     * <p>This method sets the bytes transferred counter to the specified value,
     * typically used when the transfer protocol provides absolute position
     * information rather than incremental updates.</p>
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe and uses
     * atomic operations to ensure consistent state updates.</p>
     *
     * <p><strong>Usage:</strong> Typically called by transfer engines that
     * track absolute position (e.g., HTTP Range requests, FTP REST).</p>
     *
     * @param bytesTransferred the total number of bytes transferred so far
     * @throws IllegalArgumentException if bytesTransferred is negative
     */
    public void updateProgress(long bytesTransferred) {
        if (bytesTransferred < 0) {
            throw new IllegalArgumentException("Bytes transferred cannot be negative");
        }
        this.bytesTransferred.set(bytesTransferred);
        updateLastUpdateTime();
    }

    /**
     * Adds additional bytes to the total bytes transferred counter.
     *
     * <p>This method atomically adds the specified number of bytes to the
     * current total, typically used for incremental progress updates as
     * data chunks are processed.</p>
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe and uses
     * atomic add-and-get operations to prevent race conditions.</p>
     *
     * <p><strong>Usage:</strong> Typically called by transfer engines for
     * each data chunk processed (e.g., after reading a buffer from stream).</p>
     *
     * @param additionalBytes the number of additional bytes transferred
     * @throws IllegalArgumentException if additionalBytes is negative
     */
    public void addBytesTransferred(long additionalBytes) {
        if (additionalBytes < 0) {
            throw new IllegalArgumentException("Additional bytes cannot be negative");
        }
        this.bytesTransferred.addAndGet(additionalBytes);
        updateLastUpdateTime();
    }

    /**
     * Updates the total file size when it becomes known during transfer.
     *
     * <p>This method updates the total bytes field when the actual file size
     * is determined during transfer. This is common when the initial request
     * had an unknown size (-1) but the protocol provides size information
     * (e.g., HTTP Content-Length header).</p>
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe though
     * typically called only once when size is determined.</p>
     *
     * @param totalBytes the total size of the file being transferred
     */
    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
        updateLastUpdateTime();
    }

    /**
     * Calculates the current progress as a percentage between 0.0 and 1.0.
     *
     * <p>This method provides a normalized progress value that can be used
     * for progress bars, percentage displays, and completion estimates.
     * The calculation is based on bytes transferred versus total bytes.</p>
     *
     * <p><strong>Return Values:</strong></p>
     * <ul>
     *   <li>0.0 - No progress or unknown total size</li>
     *   <li>0.0 to 1.0 - Actual progress percentage</li>
     *   <li>1.0 - Transfer complete (capped at 100%)</li>
     * </ul>
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe and provides
     * a consistent snapshot of progress at the time of call.</p>
     *
     * @return progress percentage as a double between 0.0 and 1.0
     */
    public double getProgressPercentage() {
        if (totalBytes <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) bytesTransferred.get() / totalBytes);
    }

    /**
     * Estimates the remaining transfer time in seconds based on current transfer rate.
     *
     * <p>This method calculates an estimated time to completion based on the
     * average transfer rate since the transfer started. The estimate becomes
     * more accurate as more data is transferred and the rate stabilizes.</p>
     *
     * <p><strong>Calculation Method:</strong></p>
     * <ol>
     *   <li>Calculate elapsed time since transfer start</li>
     *   <li>Calculate average transfer rate (bytes/second)</li>
     *   <li>Estimate remaining time based on remaining bytes and rate</li>
     * </ol>
     *
     * <p><strong>Return Values:</strong></p>
     * <ul>
     *   <li>-1 - Cannot estimate (unknown size, not started, or no progress)</li>
     *   <li>>= 0 - Estimated remaining seconds</li>
     * </ul>
     *
     * <p><strong>Accuracy:</strong> Estimates are more accurate for transfers
     * with consistent rates and become less reliable for variable-rate transfers.</p>
     *
     * @return estimated remaining seconds, or -1 if cannot be calculated
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

    // ========== UTILITY METHODS ==========

    /**
     * Creates an immutable TransferResult snapshot from the current job state.
     *
     * <p>This method creates a point-in-time snapshot of the transfer job's
     * current state as an immutable TransferResult object. This is useful
     * for archiving completed transfers, reporting, and API responses.</p>
     *
     * <p><strong>Snapshot Behavior:</strong> The returned TransferResult
     * reflects the job state at the time this method is called. Subsequent
     * changes to the job will not affect the returned result.</p>
     *
     * <p><strong>Usage:</strong> Typically called when a transfer reaches
     * a terminal state (COMPLETED, FAILED, CANCELLED) for final reporting.</p>
     *
     * @return an immutable TransferResult containing the current job state
     * @see TransferResult
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

    /**
     * Updates the last update timestamp to the current time.
     *
     * <p>This private utility method is called by all state-changing methods
     * to maintain an accurate timestamp of when the job was last modified.
     * This timestamp is used for monitoring, timeout detection, and rate
     * calculations.</p>
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe and uses
     * atomic reference updates.</p>
     */
    private void updateLastUpdateTime() {
        lastUpdateTime.set(Instant.now());
    }

    // ========== OBJECT METHODS ==========

    /**
     * Compares this TransferJob with another object for equality.
     *
     * <p>Two TransferJob objects are considered equal if they have the same
     * job ID. This is because job IDs are unique identifiers that distinguish
     * one transfer job from another, regardless of their current state.</p>
     *
     * <p><strong>Design Note:</strong> Equality is based solely on job ID
     * rather than all fields, as transfer jobs are mutable and their state
     * changes over time. The job ID remains constant throughout the lifecycle.</p>
     *
     * @param o the object to compare with
     * @return true if the objects represent the same transfer job, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransferJob that = (TransferJob) o;
        return Objects.equals(getJobId(), that.getJobId());
    }

    /**
     * Returns the hash code for this TransferJob.
     *
     * <p>The hash code is based solely on the job ID, which is consistent
     * with the equals() implementation. This ensures that transfer jobs
     * can be safely used in hash-based collections.</p>
     *
     * @return the hash code value for this transfer job
     */
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
