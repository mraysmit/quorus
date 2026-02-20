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

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Enumeration of possible agent states in the Quorus fleet.
 * These states represent the operational status and availability of agents.
 *
 * <p>
 * <strong>State categories:</strong>
 * </p>
 * <ul>
 * <li><strong>Healthy</strong> ({@code HEALTHY}, {@code ACTIVE}, {@code IDLE})
 * —
 * fully operational, in good health, and available for work.</li>
 * <li><strong>Problematic</strong> ({@code DEGRADED}, {@code OVERLOADED},
 * {@code UNREACHABLE}, {@code FAILED}) —
 * experiencing issues that affect availability.</li>
 * <li><strong>Transitional</strong> ({@code REGISTERING}, {@code MAINTENANCE},
 * {@code DRAINING}) —
 * the agent is moving between operational states.</li>
 * <li><strong>Terminal</strong> ({@code DEREGISTERED}) —
 * the agent has been permanently removed; no further transitions are
 * possible.</li>
 * </ul>
 *
 * <p>
 * {@code FAILED} is <em>not</em> terminal: a failed agent can still be
 * explicitly
 * deregistered. Only {@code DEREGISTERED} is a true terminal state.
 * </p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-26
 * @version 1.1
 */
public enum AgentStatus {

    /**
     * Agent is in the process of registering with the controller.
     * This is the initial state when an agent first connects.
     */
    REGISTERING("registering", "Agent is registering with the controller", false, false),

    /**
     * Agent is healthy and ready to accept new work.
     * This is the optimal steady-state for an agent that has no active jobs
     * but has not yet been classified as {@link #IDLE}.
     */
    HEALTHY("healthy", "Agent is healthy and available for work", true, true),

    /**
     * Agent is actively processing transfer jobs.
     * The agent is in good health but currently busy.
     */
    ACTIVE("active", "Agent is actively processing jobs", true, true),

    /**
     * Agent is healthy but currently idle (no active jobs).
     * This agent is immediately available for new work and receives the
     * highest job-assignment priority.
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
     * This is <em>not</em> a terminal state — the agent can still be
     * {@linkplain #DEREGISTERED deregistered}.
     */
    FAILED("failed", "Agent has failed and requires intervention", false, false),

    /**
     * Agent has been permanently removed from the fleet.
     * This is the only terminal state — no outgoing transitions are possible.
     */
    DEREGISTERED("deregistered", "Agent has been deregistered", false, false);

    // ── Transition table (single source of truth) ──────────────────────

    private static final Map<AgentStatus, Set<AgentStatus>> TRANSITIONS;

    static {
        var map = new EnumMap<AgentStatus, Set<AgentStatus>>(AgentStatus.class);
        map.put(REGISTERING, EnumSet.of(HEALTHY, FAILED));
        map.put(HEALTHY, EnumSet.of(ACTIVE, IDLE, DEGRADED, OVERLOADED,
                MAINTENANCE, DRAINING, UNREACHABLE, FAILED, DEREGISTERED));
        map.put(ACTIVE, EnumSet.of(HEALTHY, IDLE, DEGRADED, OVERLOADED,
                DRAINING, UNREACHABLE, FAILED, DEREGISTERED));
        map.put(IDLE, EnumSet.of(HEALTHY, ACTIVE, DEGRADED, MAINTENANCE,
                DRAINING, UNREACHABLE, FAILED, DEREGISTERED));
        map.put(DEGRADED, EnumSet.of(HEALTHY, ACTIVE, IDLE, OVERLOADED,
                MAINTENANCE, DRAINING, UNREACHABLE, FAILED, DEREGISTERED));
        map.put(OVERLOADED, EnumSet.of(HEALTHY, ACTIVE, IDLE, DEGRADED,
                DRAINING, UNREACHABLE, FAILED, DEREGISTERED));
        map.put(MAINTENANCE, EnumSet.of(HEALTHY, DRAINING, UNREACHABLE, FAILED, DEREGISTERED));
        map.put(DRAINING, EnumSet.of(HEALTHY, UNREACHABLE, FAILED, DEREGISTERED));
        map.put(UNREACHABLE, EnumSet.of(HEALTHY, FAILED, DEREGISTERED));
        map.put(FAILED, EnumSet.of(DEREGISTERED));
        map.put(DEREGISTERED, EnumSet.noneOf(AgentStatus.class));
        map.replaceAll((k, v) -> Collections.unmodifiableSet(v));
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    // ── Instance fields ────────────────────────────────────────────────

    private final String value;
    private final String description;
    private final boolean operational;
    private final boolean availableForWork;

    /**
     * Constructor for agent status.
     *
     * @param value            the string representation of the status
     * @param description      human-readable description of the status
     * @param operational      whether the agent is operational (can process
     *                         existing work)
     * @param availableForWork whether the agent can accept new work
     */
    AgentStatus(String value, String description, boolean operational, boolean availableForWork) {
        this.value = value;
        this.description = description;
        this.operational = operational;
        this.availableForWork = availableForWork;
    }

    // ── Accessors ──────────────────────────────────────────────────────

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

    // ── Category queries ───────────────────────────────────────────────

    /**
     * Check if the agent is in a healthy state.
     * A healthy agent is fully operational with no performance concerns.
     * This includes {@code HEALTHY}, {@code ACTIVE}, and {@code IDLE}.
     *
     * <p>
     * Note: this is stricter than {@link #isOperational()}, which also
     * includes {@code DEGRADED} and {@code OVERLOADED} agents.
     * </p>
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
     * A terminal state has no valid outgoing transitions.
     * Only {@link #DEREGISTERED} is terminal.
     *
     * @return true if the agent is in a terminal state
     */
    public boolean isTerminal() {
        return this == DEREGISTERED;
    }

    // ── Job assignment ─────────────────────────────────────────────────

    /**
     * Get the priority level for job assignment.
     * Higher values indicate higher priority for receiving new jobs.
     *
     * @return priority level (0-10)
     */
    public int getJobAssignmentPriority() {
        return switch (this) {
            case IDLE -> 10; // Highest priority — agent is ready
            case HEALTHY -> 8; // High priority — agent is available
            case ACTIVE -> 6; // Medium priority — agent is working but can take more
            case DEGRADED -> 3; // Low priority — agent has issues
            case REGISTERING -> 1; // Very low priority — agent not ready
            case OVERLOADED, MAINTENANCE, DRAINING, UNREACHABLE, FAILED, DEREGISTERED -> 0;
        };
    }

    // ── Parsing ────────────────────────────────────────────────────────

    /**
     * Parse a status from its string value.
     *
     * @param value the status value (must not be {@code null})
     * @return the corresponding AgentStatus
     * @throws IllegalArgumentException if the value is {@code null} or not
     *                                  recognized
     */
    public static AgentStatus fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Agent status value must not be null");
        }
        for (AgentStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown agent status: " + value);
    }

    // ── State-machine transitions ──────────────────────────────────────

    /**
     * Checks whether a transition from this status to the given target status is
     * valid.
     *
     * <p>
     * <strong>Valid transitions:</strong>
     * </p>
     * 
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
        return TRANSITIONS.getOrDefault(this, EnumSet.noneOf(AgentStatus.class)).contains(target);
    }

    /**
     * Returns all valid target statuses that this status can transition to.
     *
     * @return unmodifiable set of valid target statuses (empty for terminal states)
     */
    public Set<AgentStatus> getValidTransitions() {
        return TRANSITIONS.getOrDefault(this, Collections.emptySet());
    }

    @Override
    public String toString() {
        return value;
    }
}
