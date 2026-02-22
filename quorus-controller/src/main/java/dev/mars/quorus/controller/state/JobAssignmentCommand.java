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

package dev.mars.quorus.controller.state;

import dev.mars.quorus.core.JobAssignment;
import dev.mars.quorus.core.JobAssignmentStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Sealed interface for job assignment commands in the Raft state machine.
 *
 * <p>Each permitted subtype carries only the fields relevant to its operation.
 * Pattern matching in {@code switch} expressions provides compile-time exhaustiveness.
 *
 * <h3>Permitted subtypes</h3>
 * <ul>
 *   <li>{@link Assign} — assign a job to an agent</li>
 *   <li>{@link Accept} — agent accepts the assignment</li>
 *   <li>{@link Reject} — agent rejects the assignment</li>
 *   <li>{@link UpdateStatus} — update assignment status</li>
 *   <li>{@link Timeout} — assignment has timed out</li>
 *   <li>{@link Cancel} — cancel the assignment</li>
 *   <li>{@link Remove} — remove completed/failed assignment</li>
 * </ul>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2025-10-28
 */
public sealed interface JobAssignmentCommand extends RaftCommand
        permits JobAssignmentCommand.Assign,
                JobAssignmentCommand.Accept,
                JobAssignmentCommand.Reject,
                JobAssignmentCommand.UpdateStatus,
                JobAssignmentCommand.Timeout,
                JobAssignmentCommand.Cancel,
                JobAssignmentCommand.Remove {

    /** Common accessor: every subtype carries an assignment ID. */
    String assignmentId();

    /** Common accessor: every subtype carries a timestamp. */
    Instant timestamp();

    /**
     * Assign a job to an agent.
     *
     * @param assignmentId  the assignment identifier
     * @param jobAssignment the full job assignment
     * @param timestamp     the command timestamp
     */
    record Assign(String assignmentId, JobAssignment jobAssignment, Instant timestamp) implements JobAssignmentCommand {
        private static final long serialVersionUID = 1L;

        public Assign {
            Objects.requireNonNull(assignmentId, "assignmentId");
            Objects.requireNonNull(jobAssignment, "jobAssignment");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    /**
     * Agent accepts the assignment.
     *
     * @param assignmentId the assignment identifier
     * @param newStatus    the new status (ACCEPTED)
     * @param timestamp    the command timestamp
     */
    record Accept(String assignmentId, JobAssignmentStatus newStatus, Instant timestamp) implements JobAssignmentCommand {
        private static final long serialVersionUID = 1L;

        public Accept {
            Objects.requireNonNull(assignmentId, "assignmentId");
            Objects.requireNonNull(newStatus, "newStatus");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    /**
     * Agent rejects the assignment.
     *
     * @param assignmentId the assignment identifier
     * @param newStatus    the new status (REJECTED)
     * @param reason       optional reason for rejection (may be null)
     * @param timestamp    the command timestamp
     */
    record Reject(String assignmentId, JobAssignmentStatus newStatus, String reason, Instant timestamp) implements JobAssignmentCommand {
        private static final long serialVersionUID = 1L;

        public Reject {
            Objects.requireNonNull(assignmentId, "assignmentId");
            Objects.requireNonNull(newStatus, "newStatus");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    /**
     * Update assignment status.
     *
     * @param assignmentId   the assignment identifier
     * @param expectedStatus the expected current status for CAS validation (null to skip check)
     * @param newStatus      the new status
     * @param timestamp      the command timestamp
     */
    record UpdateStatus(String assignmentId, JobAssignmentStatus expectedStatus, JobAssignmentStatus newStatus, Instant timestamp) implements JobAssignmentCommand {
        private static final long serialVersionUID = 1L;

        public UpdateStatus {
            Objects.requireNonNull(assignmentId, "assignmentId");
            Objects.requireNonNull(expectedStatus, "expectedStatus");
            Objects.requireNonNull(newStatus, "newStatus");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    /**
     * Assignment has timed out.
     *
     * @param assignmentId the assignment identifier
     * @param newStatus    the new status (TIMEOUT)
     * @param reason       the timeout reason (may be null)
     * @param timestamp    the command timestamp
     */
    record Timeout(String assignmentId, JobAssignmentStatus newStatus, String reason, Instant timestamp) implements JobAssignmentCommand {
        private static final long serialVersionUID = 1L;

        public Timeout {
            Objects.requireNonNull(assignmentId, "assignmentId");
            Objects.requireNonNull(newStatus, "newStatus");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    /**
     * Cancel the assignment.
     *
     * @param assignmentId the assignment identifier
     * @param newStatus    the new status (CANCELLED)
     * @param reason       optional reason for cancellation (may be null)
     * @param timestamp    the command timestamp
     */
    record Cancel(String assignmentId, JobAssignmentStatus newStatus, String reason, Instant timestamp) implements JobAssignmentCommand {
        private static final long serialVersionUID = 1L;

        public Cancel {
            Objects.requireNonNull(assignmentId, "assignmentId");
            Objects.requireNonNull(newStatus, "newStatus");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    /**
     * Remove a completed or failed assignment.
     *
     * @param assignmentId the assignment identifier
     * @param timestamp    the command timestamp
     */
    record Remove(String assignmentId, Instant timestamp) implements JobAssignmentCommand {
        private static final long serialVersionUID = 1L;

        public Remove {
            Objects.requireNonNull(assignmentId, "assignmentId");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    // ── Factory methods (preserve existing API) ─────────────────

    /**
     * Generate a unique assignment ID from job ID and agent ID.
     */
    private static String generateAssignmentId(String jobId, String agentId) {
        return jobId + ":" + agentId;
    }

    /**
     * Create a command to assign a job to an agent.
     */
    static JobAssignmentCommand assign(JobAssignment jobAssignment) {
        Objects.requireNonNull(jobAssignment, "Job assignment cannot be null");
        String assignmentId = generateAssignmentId(jobAssignment.getJobId(), jobAssignment.getAgentId());
        return new Assign(assignmentId, jobAssignment, Instant.now());
    }

    /**
     * Create a command for an agent to accept an assignment.
     */
    static JobAssignmentCommand accept(String assignmentId) {
        return new Accept(assignmentId, JobAssignmentStatus.ACCEPTED, Instant.now());
    }

    /**
     * Create a command for an agent to reject an assignment.
     */
    static JobAssignmentCommand reject(String assignmentId, String reason) {
        return new Reject(assignmentId, JobAssignmentStatus.REJECTED, reason, Instant.now());
    }

    /**
     * Create a command to update assignment status with CAS protection.
     *
     * @param assignmentId   the assignment identifier
     * @param expectedStatus the expected current status (must match for command to apply)
     * @param newStatus      the new status
     */
    static JobAssignmentCommand updateStatus(String assignmentId, JobAssignmentStatus expectedStatus, JobAssignmentStatus newStatus) {
        return new UpdateStatus(assignmentId, expectedStatus, newStatus, Instant.now());
    }

    /**
     * Create a command to mark an assignment as timed out.
     */
    static JobAssignmentCommand timeout(String assignmentId) {
        return new Timeout(assignmentId, JobAssignmentStatus.TIMEOUT, "Assignment timed out", Instant.now());
    }

    /**
     * Create a command to cancel an assignment.
     */
    static JobAssignmentCommand cancel(String assignmentId, String reason) {
        return new Cancel(assignmentId, JobAssignmentStatus.CANCELLED, reason, Instant.now());
    }

    /**
     * Create a command to remove a completed or failed assignment.
     */
    static JobAssignmentCommand remove(String assignmentId) {
        return new Remove(assignmentId, Instant.now());
    }
}
