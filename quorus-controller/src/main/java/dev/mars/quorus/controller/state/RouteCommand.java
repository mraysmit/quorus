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

import dev.mars.quorus.core.RouteConfiguration;
import dev.mars.quorus.core.RouteStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Sealed interface for route lifecycle commands.
 *
 * <p>Each permitted subtype carries only the fields relevant to its operation,
 * eliminating nullable "bag-of-fields" patterns. Pattern matching in
 * {@code switch} expressions provides compile-time exhaustiveness.
 *
 * <h3>Permitted subtypes</h3>
 * <ul>
 *   <li>{@link Create} — create a new route</li>
 *   <li>{@link Update} — update route configuration</li>
 *   <li>{@link Delete} — delete a route</li>
 *   <li>{@link Suspend} — suspend a route</li>
 *   <li>{@link Resume} — resume a suspended route</li>
 *   <li>{@link UpdateStatus} — change route status</li>
 * </ul>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2026-02-19
 */
public sealed interface RouteCommand extends RaftCommand
        permits RouteCommand.Create,
                RouteCommand.Update,
                RouteCommand.Delete,
                RouteCommand.Suspend,
                RouteCommand.Resume,
                RouteCommand.UpdateStatus {

    /** Common accessor: every subtype carries a route ID. */
    String routeId();

    /** Common accessor: every subtype carries a timestamp. */
    Instant timestamp();

    /**
     * Create a new route.
     *
     * @param routeId            the route identifier
     * @param routeConfiguration the full route configuration
     * @param timestamp          the command timestamp
     */
    record Create(String routeId, RouteConfiguration routeConfiguration, Instant timestamp) implements RouteCommand {
        private static final long serialVersionUID = 1L;

        public Create {
            Objects.requireNonNull(routeId, "routeId");
            Objects.requireNonNull(routeConfiguration, "routeConfiguration");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    /**
     * Update an existing route's configuration.
     *
     * @param routeId            the route identifier
     * @param routeConfiguration the updated configuration
     * @param timestamp          the command timestamp
     */
    record Update(String routeId, RouteConfiguration routeConfiguration, Instant timestamp) implements RouteCommand {
        private static final long serialVersionUID = 1L;

        public Update {
            Objects.requireNonNull(routeId, "routeId");
            Objects.requireNonNull(routeConfiguration, "routeConfiguration");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    /**
     * Delete a route.
     *
     * @param routeId   the route identifier
     * @param timestamp the command timestamp
     */
    record Delete(String routeId, Instant timestamp) implements RouteCommand {
        private static final long serialVersionUID = 1L;

        public Delete {
            Objects.requireNonNull(routeId, "routeId");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    /**
     * Suspend a route.
     *
     * @param routeId   the route identifier
     * @param reason    optional reason for suspension (may be null)
     * @param timestamp the command timestamp
     */
    record Suspend(String routeId, String reason, Instant timestamp) implements RouteCommand {
        private static final long serialVersionUID = 1L;

        public Suspend {
            Objects.requireNonNull(routeId, "routeId");
            Objects.requireNonNull(timestamp, "timestamp");
            // reason may be null
        }
    }

    /**
     * Resume a suspended route.
     *
     * @param routeId   the route identifier
     * @param timestamp the command timestamp
     */
    record Resume(String routeId, Instant timestamp) implements RouteCommand {
        private static final long serialVersionUID = 1L;

        public Resume {
            Objects.requireNonNull(routeId, "routeId");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    /**
     * Update a route's status.
     *
     * @param routeId        the route identifier
     * @param expectedStatus the expected current status for CAS validation (null to skip check)
     * @param newStatus      the new status
     * @param reason         optional reason for the status change (may be null)
     * @param timestamp      the command timestamp
     */
    record UpdateStatus(String routeId, RouteStatus expectedStatus, RouteStatus newStatus, String reason, Instant timestamp) implements RouteCommand {
        private static final long serialVersionUID = 1L;

        public UpdateStatus {
            Objects.requireNonNull(routeId, "routeId");
            Objects.requireNonNull(expectedStatus, "expectedStatus");
            Objects.requireNonNull(newStatus, "newStatus");
            Objects.requireNonNull(timestamp, "timestamp");
            // reason may be null
        }
    }

    // ── Factory methods (preserve existing API) ─────────────────

    /**
     * Create a command to register a new route.
     */
    static RouteCommand create(RouteConfiguration routeConfiguration) {
        return new Create(routeConfiguration.getRouteId(), routeConfiguration, Instant.now());
    }

    /**
     * Create a command to update an existing route's configuration.
     */
    static RouteCommand update(String routeId, RouteConfiguration routeConfiguration) {
        return new Update(routeId, routeConfiguration, Instant.now());
    }

    /**
     * Create a command to delete a route.
     */
    static RouteCommand delete(String routeId) {
        return new Delete(routeId, Instant.now());
    }

    /**
     * Create a command to suspend a route.
     */
    static RouteCommand suspend(String routeId, String reason) {
        return new Suspend(routeId, reason, Instant.now());
    }

    /**
     * Create a command to resume a suspended route.
     */
    static RouteCommand resume(String routeId) {
        return new Resume(routeId, Instant.now());
    }

    /**
     * Create a command to update a route's status with CAS protection.
     *
     * @param routeId        the route identifier
     * @param expectedStatus the expected current status (must match for command to apply)
     * @param newStatus      the new status
     * @param reason         optional reason for the status change
     */
    static RouteCommand updateStatus(String routeId, RouteStatus expectedStatus, RouteStatus newStatus, String reason) {
        return new UpdateStatus(routeId, expectedStatus, newStatus, reason, Instant.now());
    }
}
