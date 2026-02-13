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

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Command for job assignment operations in the Raft state machine.
 * This class represents commands that can be submitted to the distributed controller
 * for managing job assignments to agents in the fleet.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-10-28
 * @version 1.0
 */
public class JobAssignmentCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Types of job assignment commands.
     */
    public enum CommandType {
        /** Assign a job to an agent */
        ASSIGN,
        /** Agent accepts the assignment */
        ACCEPT,
        /** Agent rejects the assignment */
        REJECT,
        /** Update assignment status */
        UPDATE_STATUS,
        /** Assignment has timed out */
        TIMEOUT,
        /** Cancel the assignment */
        CANCEL,
        /** Remove completed/failed assignment */
        REMOVE
    }

    private final CommandType type;
    private final String assignmentId;
    private final JobAssignment jobAssignment;
    private final JobAssignmentStatus newStatus;
    private final String reason;
    private final Instant timestamp;

    /**
     * Private constructor for creating commands.
     */
    private JobAssignmentCommand(CommandType type, String assignmentId, JobAssignment jobAssignment,
                                JobAssignmentStatus newStatus, String reason) {
        this(type, assignmentId, jobAssignment, newStatus, reason, null);
    }

    /**
     * Package-private constructor for protobuf deserialization with explicit timestamp.
     */
    JobAssignmentCommand(CommandType type, String assignmentId, JobAssignment jobAssignment,
                         JobAssignmentStatus newStatus, String reason, Instant timestamp) {
        this.type = Objects.requireNonNull(type, "Command type cannot be null");
        this.assignmentId = assignmentId;
        this.jobAssignment = jobAssignment;
        this.newStatus = newStatus;
        this.reason = reason;
        this.timestamp = (timestamp != null) ? timestamp : Instant.now();
        
        // Validate command parameters
        validateCommand();
    }

    /**
     * Create a command to assign a job to an agent.
     *
     * @param jobAssignment the job assignment to create
     * @return the command
     * @throws IllegalArgumentException if jobAssignment is null
     */
    public static JobAssignmentCommand assign(JobAssignment jobAssignment) {
        Objects.requireNonNull(jobAssignment, "Job assignment cannot be null");
        String assignmentId = generateAssignmentId(jobAssignment.getJobId(), jobAssignment.getAgentId());
        return new JobAssignmentCommand(CommandType.ASSIGN, assignmentId,
                                       jobAssignment, null, null);
    }

    /**
     * Create a command for an agent to accept an assignment.
     * 
     * @param assignmentId the assignment ID to accept
     * @return the command
     * @throws IllegalArgumentException if assignmentId is null or empty
     */
    public static JobAssignmentCommand accept(String assignmentId) {
        validateAssignmentId(assignmentId);
        return new JobAssignmentCommand(CommandType.ACCEPT, assignmentId, null, 
                                       JobAssignmentStatus.ACCEPTED, null);
    }

    /**
     * Create a command for an agent to reject an assignment.
     * 
     * @param assignmentId the assignment ID to reject
     * @param reason the reason for rejection (optional)
     * @return the command
     * @throws IllegalArgumentException if assignmentId is null or empty
     */
    public static JobAssignmentCommand reject(String assignmentId, String reason) {
        validateAssignmentId(assignmentId);
        return new JobAssignmentCommand(CommandType.REJECT, assignmentId, null, 
                                       JobAssignmentStatus.REJECTED, reason);
    }

    /**
     * Create a command to update assignment status.
     * 
     * @param assignmentId the assignment ID to update
     * @param newStatus the new status
     * @return the command
     * @throws IllegalArgumentException if assignmentId is null/empty or newStatus is null
     */
    public static JobAssignmentCommand updateStatus(String assignmentId, JobAssignmentStatus newStatus) {
        validateAssignmentId(assignmentId);
        Objects.requireNonNull(newStatus, "New status cannot be null");
        return new JobAssignmentCommand(CommandType.UPDATE_STATUS, assignmentId, null, newStatus, null);
    }

    /**
     * Create a command to mark an assignment as timed out.
     * 
     * @param assignmentId the assignment ID that timed out
     * @return the command
     * @throws IllegalArgumentException if assignmentId is null or empty
     */
    public static JobAssignmentCommand timeout(String assignmentId) {
        validateAssignmentId(assignmentId);
        return new JobAssignmentCommand(CommandType.TIMEOUT, assignmentId, null, 
                                       JobAssignmentStatus.TIMEOUT, "Assignment timed out");
    }

    /**
     * Create a command to cancel an assignment.
     * 
     * @param assignmentId the assignment ID to cancel
     * @param reason the reason for cancellation (optional)
     * @return the command
     * @throws IllegalArgumentException if assignmentId is null or empty
     */
    public static JobAssignmentCommand cancel(String assignmentId, String reason) {
        validateAssignmentId(assignmentId);
        return new JobAssignmentCommand(CommandType.CANCEL, assignmentId, null, 
                                       JobAssignmentStatus.CANCELLED, reason);
    }

    /**
     * Create a command to remove a completed or failed assignment.
     * 
     * @param assignmentId the assignment ID to remove
     * @return the command
     * @throws IllegalArgumentException if assignmentId is null or empty
     */
    public static JobAssignmentCommand remove(String assignmentId) {
        validateAssignmentId(assignmentId);
        return new JobAssignmentCommand(CommandType.REMOVE, assignmentId, null, null, null);
    }

    // Getters
    public CommandType getType() {
        return type;
    }

    public String getAssignmentId() {
        return assignmentId;
    }

    public JobAssignment getJobAssignment() {
        return jobAssignment;
    }

    public JobAssignmentStatus getNewStatus() {
        return newStatus;
    }

    public String getReason() {
        return reason;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    // Validation methods
    private static void validateAssignmentId(String assignmentId) {
        if (assignmentId == null || assignmentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Assignment ID cannot be null or empty");
        }
    }

    /**
     * Generate a unique assignment ID from job ID and agent ID.
     */
    private static String generateAssignmentId(String jobId, String agentId) {
        return jobId + ":" + agentId;
    }

    private void validateCommand() {
        switch (type) {
            case ASSIGN:
                if (jobAssignment == null) {
                    throw new IllegalArgumentException("Job assignment is required for ASSIGN command");
                }
                String expectedId = generateAssignmentId(jobAssignment.getJobId(), jobAssignment.getAgentId());
                if (assignmentId == null || !assignmentId.equals(expectedId)) {
                    throw new IllegalArgumentException("Assignment ID must match job assignment ID (expected: " + expectedId + ")");
                }
                break;
                
            case ACCEPT:
            case REJECT:
            case UPDATE_STATUS:
            case TIMEOUT:
            case CANCEL:
            case REMOVE:
                if (assignmentId == null || assignmentId.trim().isEmpty()) {
                    throw new IllegalArgumentException("Assignment ID is required for " + type + " command");
                }
                break;
                
            default:
                throw new IllegalArgumentException("Unknown command type: " + type);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobAssignmentCommand that = (JobAssignmentCommand) o;
        return type == that.type &&
               Objects.equals(assignmentId, that.assignmentId) &&
               Objects.equals(jobAssignment, that.jobAssignment) &&
               newStatus == that.newStatus &&
               Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, assignmentId, jobAssignment, newStatus, reason);
    }

    @Override
    public String toString() {
        return "JobAssignmentCommand{" +
                "type=" + type +
                ", assignmentId='" + assignmentId + '\'' +
                ", newStatus=" + newStatus +
                ", reason='" + reason + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
