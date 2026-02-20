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
 * Lifecycle states for a route configuration.
 * <p>
 * Routes follow a defined lifecycle:
 * <pre>
 *   CONFIGURED → ACTIVE → TRIGGERED → TRANSFERRING → ACTIVE (loop)
 *   ACTIVE → SUSPENDED (manual) → ACTIVE (resume)
 *   ACTIVE → DEGRADED → ACTIVE (recovery)
 *   TRANSFERRING → FAILED (max retries)
 *   FAILED → CONFIGURED (reconfigure)
 * </pre>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-02-19
 */
public enum RouteStatus {

    /**
     * Route has been created but not yet activated.
     * Initial state after configuration.
     */
    CONFIGURED,

    /**
     * Route is active and trigger conditions are being evaluated.
     * The controller evaluates triggers on each check interval.
     */
    ACTIVE,

    /**
     * Route trigger conditions have been met.
     * A transfer job is about to be created.
     */
    TRIGGERED,

    /**
     * A transfer is currently in progress for this route.
     */
    TRANSFERRING,

    /**
     * Route has been manually suspended by an operator.
     * No trigger evaluation occurs while suspended.
     */
    SUSPENDED,

    /**
     * Route is in a degraded state (e.g., source or destination agent unavailable).
     * Trigger evaluation continues but transfers may not succeed.
     */
    DEGRADED,

    /**
     * Route transfer has failed after exhausting retries.
     * Requires manual intervention or reconfiguration.
     */
    FAILED,

    /**
     * Route has been deleted. Terminal state.
     */
    DELETED;

    /**
     * Check if this status represents a terminal state.
     * Terminal states cannot transition to other states (except FAILED which can be reconfigured).
     *
     * @return {@code true} if the route is in a terminal state
     */
    public boolean isTerminal() {
        return this == DELETED;
    }

    /**
     * Check if this status indicates the route is currently active or processing.
     *
     * @return {@code true} if the route is in an active processing state
     */
    public boolean isActive() {
        return this == ACTIVE || this == TRIGGERED || this == TRANSFERRING;
    }

    /**
     * Checks whether a transition from this status to the given target status is valid.
     *
     * <p><strong>Valid transitions:</strong></p>
     * <pre>
     *   CONFIGURED   → ACTIVE, DELETED
     *   ACTIVE       → TRIGGERED, SUSPENDED, DEGRADED, DELETED
     *   TRIGGERED    → TRANSFERRING, ACTIVE
     *   TRANSFERRING → ACTIVE, FAILED
     *   SUSPENDED    → ACTIVE, DELETED
     *   DEGRADED     → ACTIVE, DELETED
     *   FAILED       → CONFIGURED, DELETED
     *   DELETED      → (terminal — no transitions)
     * </pre>
     *
     * @param target the target status to transition to
     * @return {@code true} if the transition is valid, {@code false} otherwise
     */
    public boolean canTransitionTo(RouteStatus target) {
        return switch (this) {
            case CONFIGURED -> target == ACTIVE || target == DELETED;
            case ACTIVE -> target == TRIGGERED || target == SUSPENDED
                        || target == DEGRADED || target == DELETED;
            case TRIGGERED -> target == TRANSFERRING || target == ACTIVE;
            case TRANSFERRING -> target == ACTIVE || target == FAILED;
            case SUSPENDED -> target == ACTIVE || target == DELETED;
            case DEGRADED -> target == ACTIVE || target == DELETED;
            case FAILED -> target == CONFIGURED || target == DELETED;
            case DELETED -> false;
        };
    }

    /**
     * Returns all valid target statuses that this status can transition to.
     *
     * @return array of valid target statuses (empty for terminal states)
     */
    public RouteStatus[] getValidTransitions() {
        return switch (this) {
            case CONFIGURED -> new RouteStatus[]{ACTIVE, DELETED};
            case ACTIVE -> new RouteStatus[]{TRIGGERED, SUSPENDED, DEGRADED, DELETED};
            case TRIGGERED -> new RouteStatus[]{TRANSFERRING, ACTIVE};
            case TRANSFERRING -> new RouteStatus[]{ACTIVE, FAILED};
            case SUSPENDED -> new RouteStatus[]{ACTIVE, DELETED};
            case DEGRADED -> new RouteStatus[]{ACTIVE, DELETED};
            case FAILED -> new RouteStatus[]{CONFIGURED, DELETED};
            case DELETED -> new RouteStatus[0];
        };
    }
}
