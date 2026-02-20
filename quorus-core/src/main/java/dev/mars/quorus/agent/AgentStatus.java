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

package dev.mars.quorus.agent;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumeration of possible agent states in the Quorus fleet.
 * These states represent the operational status and availability of agents.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-26
 * @version 1.0
 */
public enum AgentStatus {

    /**
     * Agent is in the process of registering with the controller.
     * This is the initial state when an agent first connects.
     */
    REGISTERING("registering", "Agent is registering with the controller", false, false),

    /**
     * Agent is healthy and ready to accept new work.
     * This is the optimal state for an agent.
     */
    HEALTHY("healthy", "Agent is healthy and available for work", true, true),

    /**
     * Agent is actively processing transfer jobs.
     * The agent is healthy but currently busy.
     */
    ACTIVE("active", "Agent is actively processing jobs", true, true),

    /**
     * Agent is healthy but currently idle (no active jobs).
     * This agent is immediately available for new work.
     */
    IDLE("idle", "Agent is idle and ready for work", true, true),

    /**
     * Agent is experiencing performance issues but still operational.
     * The agent can accept work but with reduced priority.
     */
    DEGRADED("degraded", "Agent is experiencing performance issues", true, false),

    /**
     * Agent is at or near capacity limits.
     * The agent should not receive new work until load decreases.
     */
    OVERLOADED("overloaded", "Agent is overloaded and cannot accept new work", true, false),

    /**
     * Agent is in planned maintenance mode.
     * The agent will not accept new work and should drain existing jobs.
     */
    MAINTENANCE("maintenance", "Agent is in maintenance mode", false, false),

    /**
     * Agent is being gracefully shut down.
     * The agent is draining existing jobs and will not accept new work.
     */
    DRAINING("draining", "Agent is draining jobs before shutdown", false, false),

    /**
     * Agent has failed to respond to heartbeat requests.
     * The agent is considered unreachable and unavailable.
     */
    UNREACHABLE("unreachable", "Agent is unreachable", false, false),

    /**
     * Agent has encountered a critical error.
     * The agent requires intervention before it can return to service.
     */
    FAILED("failed", "Agent has failed and requires intervention", false, false),

    /**
     * Agent has been permanently removed from the fleet.
     * This is a terminal state.
     */
    DEREGISTERED("deregistered", "Agent has been deregistered", false, false);

    private final String value;
    private final String description;
    private final boolean operational;
    private final boolean availableForWork;

    /**
     * Constructor for agent status.
     * 
     * @param value the string representation of the status
     * @param description human-readable description of the status
     * @param operational whether the agent is operational (can process existing work)
     * @param availableForWork whether the agent can accept new work
     */
    AgentStatus(String value, String description, boolean operational, boolean availableForWork) {
        this.value = value;
        this.description = description;
        this.operational = operational;
        this.availableForWork = availableForWork;
    }

    /**
     * Get the string representation of the status.
     * 
     * @return the status value
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Get the human-readable description of the status.
     * 
     * @return the status description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Check if the agent is operational.
     * Operational agents can continue processing existing work.
     * 
     * @return true if the agent is operational
     */
    public boolean isOperational() {
        return operational;
    }

    /**
     * Check if the agent is available for new work.
     * 
     * @return true if the agent can accept new jobs
     */
    public boolean isAvailableForWork() {
        return availableForWork;
    }

    /**
     * Check if the agent is in a healthy state.
     * 
     * @return true if the agent is healthy
     */
    public boolean isHealthy() {
        return this == HEALTHY || this == ACTIVE || this == IDLE;
    }

    /**
     * Check if the agent is in a problematic state.
     * 
     * @return true if the agent has issues
     */
    public boolean isProblematic() {
        return this == DEGRADED || this == OVERLOADED || this == UNREACHABLE || this == FAILED;
    }

    /**
     * Check if the agent is in a transitional state.
     * 
     * @return true if the agent is transitioning
     */
    public boolean isTransitional() {
        return this == REGISTERING || this == MAINTENANCE || this == DRAINING;
    }

    /**
     * Check if the agent is in a terminal state.
     * 
     * @return true if the agent is in a terminal state
     */
    public boolean isTerminal() {
        return this == FAILED || this == DEREGISTERED;
    }

    /**
     * Get the priority level for job assignment.
     * Higher values indicate higher priority for receiving new jobs.
     * 
     * @return priority level (0-10)
     */
    public int getJobAssignmentPriority() {
        switch (this) {
            case IDLE:
                return 10;  // Highest priority - agent is ready
            case HEALTHY:
                return 8;   // High priority - agent is available
            case ACTIVE:
                return 6;   // Medium priority - agent is working but can take more
            case DEGRADED:
                return 3;   // Low priority - agent has issues
            case OVERLOADED:
            case MAINTENANCE:
            case DRAINING:
            case UNREACHABLE:
            case FAILED:
            case DEREGISTERED:
                return 0;   // No priority - agent unavailable
            case REGISTERING:
                return 1;   // Very low priority - agent not ready
            default:
                return 0;
        }
    }

    /**
     * Parse a status from its string value.
     * 
     * @param value the status value
     * @return the corresponding AgentStatus
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static AgentStatus fromValue(String value) {
        for (AgentStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown agent status: " + value);
    }

    /**
     * Checks whether a transition from this status to the given target status is valid.
     *
     * <p><strong>Valid transitions:</strong></p>
     * <pre>
     *   REGISTERING → HEALTHY, FAILED
     *   HEALTHY     → ACTIVE, IDLE, DEGRADED, OVERLOADED, MAINTENANCE, DRAINING, UNREACHABLE, FAILED, DEREGISTERED
     *   ACTIVE      → HEALTHY, IDLE, DEGRADED, OVERLOADED, DRAINING, UNREACHABLE, FAILED, DEREGISTERED
     *   IDLE        → HEALTHY, ACTIVE, DEGRADED, MAINTENANCE, DRAINING, UNREACHABLE, FAILED, DEREGISTERED
     *   DEGRADED    → HEALTHY, ACTIVE, IDLE, OVERLOADED, MAINTENANCE, DRAINING, UNREACHABLE, FAILED, DEREGISTERED
     *   OVERLOADED  → HEALTHY, ACTIVE, IDLE, DEGRADED, DRAINING, UNREACHABLE, FAILED, DEREGISTERED
     *   MAINTENANCE → HEALTHY, DRAINING, UNREACHABLE, FAILED, DEREGISTERED
     *   DRAINING    → HEALTHY, UNREACHABLE, FAILED, DEREGISTERED
     *   UNREACHABLE → HEALTHY, FAILED, DEREGISTERED
     *   FAILED      → DEREGISTERED
     *   DEREGISTERED→ (terminal — no transitions)
     * </pre>
     *
     * @param target the target status to transition to
     * @return {@code true} if the transition is valid, {@code false} otherwise
     */
    public boolean canTransitionTo(AgentStatus target) {
        return switch (this) {
            case REGISTERING -> target == HEALTHY || target == FAILED;
            case HEALTHY -> target == ACTIVE || target == IDLE || target == DEGRADED
                         || target == OVERLOADED || target == MAINTENANCE || target == DRAINING
                         || target == UNREACHABLE || target == FAILED || target == DEREGISTERED;
            case ACTIVE -> target == HEALTHY || target == IDLE || target == DEGRADED
                        || target == OVERLOADED || target == DRAINING || target == UNREACHABLE
                        || target == FAILED || target == DEREGISTERED;
            case IDLE -> target == HEALTHY || target == ACTIVE || target == DEGRADED
                      || target == MAINTENANCE || target == DRAINING || target == UNREACHABLE
                      || target == FAILED || target == DEREGISTERED;
            case DEGRADED -> target == HEALTHY || target == ACTIVE || target == IDLE
                          || target == OVERLOADED || target == MAINTENANCE || target == DRAINING
                          || target == UNREACHABLE || target == FAILED || target == DEREGISTERED;
            case OVERLOADED -> target == HEALTHY || target == ACTIVE || target == IDLE
                            || target == DEGRADED || target == DRAINING || target == UNREACHABLE
                            || target == FAILED || target == DEREGISTERED;
            case MAINTENANCE -> target == HEALTHY || target == DRAINING || target == UNREACHABLE
                             || target == FAILED || target == DEREGISTERED;
            case DRAINING -> target == HEALTHY || target == UNREACHABLE
                          || target == FAILED || target == DEREGISTERED;
            case UNREACHABLE -> target == HEALTHY || target == FAILED || target == DEREGISTERED;
            case FAILED -> target == DEREGISTERED;
            case DEREGISTERED -> false;
        };
    }

    /**
     * Returns all valid target statuses that this status can transition to.
     *
     * @return array of valid target statuses (empty for terminal states)
     */
    public AgentStatus[] getValidTransitions() {
        return switch (this) {
            case REGISTERING -> new AgentStatus[]{HEALTHY, FAILED};
            case HEALTHY -> new AgentStatus[]{ACTIVE, IDLE, DEGRADED, OVERLOADED,
                    MAINTENANCE, DRAINING, UNREACHABLE, FAILED, DEREGISTERED};
            case ACTIVE -> new AgentStatus[]{HEALTHY, IDLE, DEGRADED, OVERLOADED,
                    DRAINING, UNREACHABLE, FAILED, DEREGISTERED};
            case IDLE -> new AgentStatus[]{HEALTHY, ACTIVE, DEGRADED, MAINTENANCE,
                    DRAINING, UNREACHABLE, FAILED, DEREGISTERED};
            case DEGRADED -> new AgentStatus[]{HEALTHY, ACTIVE, IDLE, OVERLOADED,
                    MAINTENANCE, DRAINING, UNREACHABLE, FAILED, DEREGISTERED};
            case OVERLOADED -> new AgentStatus[]{HEALTHY, ACTIVE, IDLE, DEGRADED,
                    DRAINING, UNREACHABLE, FAILED, DEREGISTERED};
            case MAINTENANCE -> new AgentStatus[]{HEALTHY, DRAINING, UNREACHABLE,
                    FAILED, DEREGISTERED};
            case DRAINING -> new AgentStatus[]{HEALTHY, UNREACHABLE, FAILED, DEREGISTERED};
            case UNREACHABLE -> new AgentStatus[]{HEALTHY, FAILED, DEREGISTERED};
            case FAILED -> new AgentStatus[]{DEREGISTERED};
            case DEREGISTERED -> new AgentStatus[0];
        };
    }

    @Override
    public String toString() {
        return value;
    }
}
