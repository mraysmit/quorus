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


/**
 * Enumeration representing the various states of a file transfer operation in the Quorus system.
 *
 * <p>This enum defines the complete lifecycle of a transfer job from initial creation through
 * final completion or termination. The status transitions follow a specific flow that ensures
 * proper state management across the distributed system.</p>
 *
 * <h3>State Transition Flow:</h3>
 * <pre>
 * PENDING → IN_PROGRESS → {COMPLETED | FAILED}
 *    ↓           ↓             ↑
 * CANCELLED   PAUSED ←→ IN_PROGRESS
 * </pre>
 *
 * <h3>State Transition Rules:</h3>
 * <ul>
 *   <li>All transfers start in PENDING state</li>
 *   <li>PENDING can transition to IN_PROGRESS or CANCELLED</li>
 *   <li>IN_PROGRESS can transition to COMPLETED, FAILED, PAUSED, or CANCELLED</li>
 *   <li>PAUSED can transition back to IN_PROGRESS or to CANCELLED</li>
 *   <li>COMPLETED, FAILED, and CANCELLED are terminal states</li>
 * </ul>
 *
 * <h3>Usage Across Quorus Components:</h3>
 * <ul>
 *   <li><strong>Transfer Engine:</strong> Local state tracking and progress monitoring</li>
 *   <li><strong>Distributed State Machine:</strong> Consensus-based state replication via Raft</li>
 *   <li><strong>REST API:</strong> Status reporting to external clients</li>
 *   <li><strong>Client SDK:</strong> Status queries and monitoring</li>
 *   <li><strong>Workflow Engine:</strong> Dependency and sequencing logic</li>
 * </ul>
 *
 * <h3>Thread Safety:</h3>
 * <p>This enum is inherently thread-safe as enum constants are immutable. However,
 * transitions between states in TransferJob instances must be properly synchronized.</p>
 *
 * @author Quorus Development Team
 * @since 1.0
 * @see TransferJob
 * @see dev.mars.quorus.controller.state.TransferJobCommand
 * @see dev.mars.quorus.workflow.WorkflowEngine
 */
public enum TransferStatus {
    /**
     * Transfer has been created and validated but not yet started.
     *
     * <p>This is the initial state for all transfer jobs. The transfer request has been
     * validated, queued, and resources may be allocated, but actual data transfer has
     * not begun. No network connections have been established yet.</p>
     *
     * <p><strong>Characteristics:</strong></p>
     * <ul>
     *   <li>Transfer request has been validated</li>
     *   <li>Job has been queued for execution</li>
     *   <li>No network activity yet</li>
     *   <li>Resources may be pre-allocated</li>
     * </ul>
     *
     * <p><strong>Typical Duration:</strong> Milliseconds to seconds</p>
     * <p><strong>Next States:</strong> IN_PROGRESS, CANCELLED</p>
     */
    PENDING,

    /**
     * Transfer is actively in progress with data being moved.
     *
     * <p>Data is currently being transferred from source to destination. Network
     * connections are active, bytes are being moved, and progress metrics are
     * being updated in real-time. This indicates the transfer engine is actively
     * working on the job.</p>
     *
     * <p><strong>Active Characteristics:</strong></p>
     * <ul>
     *   <li>Network connections are established and active</li>
     *   <li>Bytes transferred counter is continuously increasing</li>
     *   <li>Progress percentage is being updated</li>
     *   <li>Transfer rate is being calculated and monitored</li>
     *   <li>Heartbeat signals are being sent to distributed state</li>
     * </ul>
     *
     * <p><strong>Monitoring:</strong> This state supports real-time progress tracking</p>
     * <p><strong>Next States:</strong> COMPLETED, FAILED, PAUSED, CANCELLED</p>
     */
    IN_PROGRESS,

    /**
     * Transfer completed successfully with all data verified.
     *
     * <p>All data has been successfully transferred from source to destination.
     * Checksums have been verified (if applicable), file integrity has been
     * confirmed, and the transfer is considered complete. This is a terminal
     * state indicating successful completion.</p>
     *
     * <p><strong>Success Criteria Met:</strong></p>
     * <ul>
     *   <li>All bytes transferred successfully (bytesTransferred == totalBytes)</li>
     *   <li>Checksum verification passed (if enabled)</li>
     *   <li>Destination file is accessible and complete</li>
     *   <li>No errors occurred during the entire transfer process</li>
     *   <li>File permissions and metadata preserved (if configured)</li>
     * </ul>
     *
     * <p><strong>Post-Completion:</strong> Cleanup tasks may run, logs are finalized</p>
     * <p><strong>Terminal State:</strong> No further transitions possible</p>
     */
    COMPLETED,

    /**
     * Transfer failed due to an unrecoverable error condition.
     *
     * <p>The transfer could not be completed due to an error condition that
     * prevented successful completion. This could be due to network issues,
     * file system problems, permission errors, or other technical failures.
     * Detailed error information is stored in the transfer job's error message.</p>
     *
     * <p><strong>Common Failure Scenarios:</strong></p>
     * <ul>
     *   <li>Network connectivity lost or timeout exceeded</li>
     *   <li>Source file not found, moved, or became inaccessible</li>
     *   <li>Destination path permission denied or read-only</li>
     *   <li>Disk space exhausted on destination</li>
     *   <li>Checksum verification failure indicating corruption</li>
     *   <li>Authentication or authorization failure</li>
     *   <li>Protocol-specific errors (HTTP 404, FTP connection refused, etc.)</li>
     * </ul>
     *
     * <p><strong>Error Handling:</strong> Error details logged, notifications sent</p>
     * <p><strong>Recovery:</strong> May require manual intervention or retry with different parameters</p>
     * <p><strong>Terminal State:</strong> No automatic transitions, but can be manually retried</p>
     */
    FAILED,

    /**
     * Transfer was explicitly cancelled by user request or system policy.
     *
     * <p>The transfer was intentionally terminated either by explicit user request
     * or by system policy enforcement (e.g., shutdown, resource constraints,
     * administrative intervention). This represents intentional termination
     * rather than technical failure.</p>
     *
     * <p><strong>Cancellation Triggers:</strong></p>
     * <ul>
     *   <li>User-initiated cancellation via REST API or CLI</li>
     *   <li>System shutdown or restart procedures</li>
     *   <li>Resource constraints or policy limit enforcement</li>
     *   <li>Administrative intervention or emergency stop</li>
     *   <li>Workflow dependency failure causing cascade cancellation</li>
     *   <li>Timeout policies or scheduled maintenance windows</li>
     * </ul>
     *
     * <p><strong>Cleanup Actions:</strong> Partial files may be cleaned up, resources released</p>
     * <p><strong>Graceful Termination:</strong> Connections closed cleanly when possible</p>
     * <p><strong>Terminal State:</strong> No further transitions possible</p>
     */
    CANCELLED,

    /**
     * Transfer is temporarily paused and can be resumed later.
     *
     * <p>The transfer has been temporarily suspended but can be resumed from
     * the current position. This state is useful for bandwidth management,
     * scheduled maintenance windows, or user-requested temporary stops.
     * The transfer context and progress are preserved.</p>
     *
     * <p><strong>Pause Scenarios:</strong></p>
     * <ul>
     *   <li>User-requested pause for bandwidth management</li>
     *   <li>Scheduled maintenance window approaching</li>
     *   <li>Resource contention requiring temporary suspension</li>
     *   <li>Network conditions requiring backoff</li>
     *   <li>Workflow coordination requiring synchronization</li>
     * </ul>
     *
     * <p><strong>State Preservation:</strong></p>
     * <ul>
     *   <li>Current progress and byte position maintained</li>
     *   <li>Connection state may be preserved or re-established</li>
     *   <li>Resume capability depends on protocol support</li>
     *   <li>Timeout policies may still apply during pause</li>
     * </ul>
     *
     * <p><strong>Resume Capability:</strong> Can transition back to IN_PROGRESS</p>
     * <p><strong>Next States:</strong> IN_PROGRESS, CANCELLED</p>
     */
    PAUSED;
    
    /**
     * Determines if the transfer is in a terminal state where no further transitions are possible.
     *
     * <p>Terminal states represent the final outcome of a transfer operation. Once a transfer
     * reaches a terminal state, it cannot transition to any other state without external
     * intervention (such as creating a new transfer job for retry scenarios).</p>
     *
     * <p><strong>Terminal States:</strong></p>
     * <ul>
     *   <li>{@link #COMPLETED} - Transfer finished successfully</li>
     *   <li>{@link #FAILED} - Transfer failed due to error</li>
     *   <li>{@link #CANCELLED} - Transfer was cancelled</li>
     * </ul>
     *
     * <p><strong>Usage:</strong> This method is commonly used in workflow engines and
     * monitoring systems to determine when a transfer job has reached its final state
     * and can be archived or cleaned up.</p>
     *
     * @return {@code true} if the transfer is in a terminal state (COMPLETED, FAILED, or CANCELLED),
     *         {@code false} if the transfer can still transition to other states
     *
     * @see #isActive()
     * @see #canResume()
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    /**
     * Determines if the transfer is currently in an active state where work is being performed.
     *
     * <p>Active states indicate that the transfer is either currently moving data or is
     * temporarily suspended but ready to resume. These states represent ongoing transfer
     * operations that require system resources and monitoring.</p>
     *
     * <p><strong>Active States:</strong></p>
     * <ul>
     *   <li>{@link #IN_PROGRESS} - Data is actively being transferred</li>
     *   <li>{@link #PAUSED} - Transfer is suspended but can resume</li>
     * </ul>
     *
     * <p><strong>Resource Management:</strong> Active transfers consume system resources
     * such as network connections, memory buffers, and CPU cycles. This method helps
     * resource managers track and limit concurrent active transfers.</p>
     *
     * <p><strong>Monitoring:</strong> Active transfers should be monitored for progress,
     * health checks, and timeout conditions.</p>
     *
     * @return {@code true} if the transfer is active (IN_PROGRESS or PAUSED),
     *         {@code false} if the transfer is pending, terminal, or inactive
     *
     * @see #isTerminal()
     * @see #canResume()
     */
    public boolean isActive() {
        return this == IN_PROGRESS || this == PAUSED;
    }

    /**
     * Determines if the transfer can be resumed from its current state.
     *
     * <p>Resumable states allow the transfer to continue from where it left off,
     * either immediately (for paused transfers) or after addressing the underlying
     * issue (for failed transfers). This capability is essential for handling
     * network interruptions and implementing retry logic.</p>
     *
     * <p><strong>Resumable States:</strong></p>
     * <ul>
     *   <li>{@link #PAUSED} - Can resume immediately from current position</li>
     *   <li>{@link #FAILED} - Can retry after addressing the failure cause</li>
     * </ul>
     *
     * <p><strong>Resume Behavior:</strong></p>
     * <ul>
     *   <li><strong>PAUSED:</strong> Resume from exact byte position where paused</li>
     *   <li><strong>FAILED:</strong> May resume from last checkpoint or restart completely</li>
     * </ul>
     *
     * <p><strong>Protocol Support:</strong> Resume capability depends on the underlying
     * transfer protocol supporting range requests or partial transfers (HTTP Range,
     * FTP REST command, etc.).</p>
     *
     * @return {@code true} if the transfer can be resumed (PAUSED or FAILED),
     *         {@code false} if the transfer cannot be resumed from its current state
     *
     * @see #isActive()
     * @see #isTerminal()
     */
    public boolean canResume() {
        return this == PAUSED || this == FAILED;
    }
}
