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
    DELETED
}
