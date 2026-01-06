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

package dev.mars.quorus.core;

/**
 * Represents the lifecycle status of a job assignment.
 * 
 * The typical flow is:
 * ASSIGNED -> ACCEPTED -> IN_PROGRESS -> COMPLETED
 * 
 * Alternative flows:
 * ASSIGNED -> REJECTED (agent cannot accept)
 * ASSIGNED -> TIMEOUT (agent doesn't respond)
 * Any state -> CANCELLED (manual cancellation)
 * IN_PROGRESS -> FAILED (execution failure)
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-10-28
 * @version 1.0
 */
public enum JobAssignmentStatus {
    
    /**
     * Job has been assigned to an agent but not yet acknowledged.
     * This is the initial state when a job is first assigned.
     */
    ASSIGNED("Job assigned to agent", false, false),
    
    /**
     * Agent has acknowledged and accepted the job assignment.
     * The agent is preparing to start execution.
     */
    ACCEPTED("Job accepted by agent", false, false),
    
    /**
     * Agent has started executing the job.
     * Transfer is actively in progress.
     */
    IN_PROGRESS("Job execution in progress", false, false),
    
    /**
     * Job has been completed successfully.
     * This is a terminal state.
     */
    COMPLETED("Job completed successfully", true, true),
    
    /**
     * Job execution failed.
     * This is a terminal state, but the job may be reassigned.
     */
    FAILED("Job execution failed", true, false),
    
    /**
     * Agent rejected the job assignment.
     * The job will need to be reassigned to another agent.
     */
    REJECTED("Job rejected by agent", true, false),
    
    /**
     * Agent did not respond to the assignment within the timeout period.
     * The job will need to be reassigned to another agent.
     */
    TIMEOUT("Assignment timeout", true, false),
    
    /**
     * Job assignment was manually cancelled.
     * This is a terminal state.
     */
    CANCELLED("Job assignment cancelled", true, false);
    
    private final String description;
    private final boolean terminal;
    private final boolean successful;
    
    JobAssignmentStatus(String description, boolean terminal, boolean successful) {
        this.description = description;
        this.terminal = terminal;
        this.successful = successful;
    }
    
    /**
     * Get a human-readable description of this status.
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Check if this status represents a terminal state.
     * Terminal states cannot transition to other states.
     */
    public boolean isTerminal() {
        return terminal;
    }
    
    /**
     * Check if this status represents a successful completion.
     */
    public boolean isSuccessful() {
        return successful;
    }
    
    /**
     * Check if this status indicates the job is currently active.
     * Active jobs are those that are assigned, accepted, or in progress.
     */
    public boolean isActive() {
        return this == ASSIGNED || this == ACCEPTED || this == IN_PROGRESS;
    }
    
    /**
     * Check if this status indicates the job needs reassignment.
     * Jobs that are rejected, timed out, or failed may be reassigned.
     */
    public boolean needsReassignment() {
        return this == REJECTED || this == TIMEOUT || this == FAILED;
    }
    
    /**
     * Get the priority for job reassignment.
     * Higher values indicate higher priority for reassignment.
     * 
     * @return priority level (0-10)
     */
    public int getReassignmentPriority() {
        switch (this) {
            case TIMEOUT:
                return 10;  // Highest priority - agent is unresponsive
            case REJECTED:
                return 8;   // High priority - agent explicitly declined
            case FAILED:
                return 6;   // Medium priority - execution failed
            case CANCELLED:
                return 0;   // No reassignment - manually cancelled
            default:
                return 0;   // No reassignment needed
        }
    }
    
    /**
     * Check if transition from this status to the target status is valid.
     * 
     * @param target the target status to transition to
     * @return true if the transition is valid
     */
    public boolean canTransitionTo(JobAssignmentStatus target) {
        if (this.isTerminal()) {
            return false; // Terminal states cannot transition
        }
        
        switch (this) {
            case ASSIGNED:
                return target == ACCEPTED || target == REJECTED || 
                       target == TIMEOUT || target == CANCELLED;
                       
            case ACCEPTED:
                return target == IN_PROGRESS || target == CANCELLED ||
                       target == FAILED;
                       
            case IN_PROGRESS:
                return target == COMPLETED || target == FAILED || 
                       target == CANCELLED;
                       
            default:
                return false;
        }
    }
    
    /**
     * Get all valid transition targets from this status.
     * 
     * @return array of valid target statuses
     */
    public JobAssignmentStatus[] getValidTransitions() {
        switch (this) {
            case ASSIGNED:
                return new JobAssignmentStatus[]{ACCEPTED, REJECTED, TIMEOUT, CANCELLED};
            case ACCEPTED:
                return new JobAssignmentStatus[]{IN_PROGRESS, CANCELLED, FAILED};
            case IN_PROGRESS:
                return new JobAssignmentStatus[]{COMPLETED, FAILED, CANCELLED};
            default:
                return new JobAssignmentStatus[0]; // Terminal states have no transitions
        }
    }
    
    @Override
    public String toString() {
        return name() + " (" + description + ")";
    }
}
