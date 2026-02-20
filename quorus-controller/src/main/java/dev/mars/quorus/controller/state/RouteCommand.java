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

/**
 * Command for route operations in the Raft state machine.
 * <p>
 * Routes are managed via distributed consensus — all write operations
 * (create, update, delete, suspend, resume, status changes) are submitted
 * as commands through the Raft log.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-02-19
 */
public final class RouteCommand implements RaftCommand {

    private static final long serialVersionUID = 1L;

    /**
     * Types of route commands.
     */
    public enum CommandType {
        CREATE,
        UPDATE,
        DELETE,
        SUSPEND,
        RESUME,
        UPDATE_STATUS
    }

    private final CommandType type;
    private final String routeId;
    private final RouteConfiguration routeConfiguration;
    private final RouteStatus newStatus;
    private final String reason;
    private final Instant timestamp;

    /**
     * Private constructor for creating commands via factory methods.
     */
    private RouteCommand(CommandType type, String routeId, RouteConfiguration routeConfiguration,
                         RouteStatus newStatus, String reason) {
        this(type, routeId, routeConfiguration, newStatus, reason, null);
    }

    /**
     * Package-private constructor for protobuf deserialization with explicit timestamp.
     */
    RouteCommand(CommandType type, String routeId, RouteConfiguration routeConfiguration,
                 RouteStatus newStatus, String reason, Instant timestamp) {
        this.type = type;
        this.routeId = routeId;
        this.routeConfiguration = routeConfiguration;
        this.newStatus = newStatus;
        this.reason = reason;
        this.timestamp = (timestamp != null) ? timestamp : Instant.now();
    }

    /**
     * Create a command to register a new route.
     *
     * @param routeConfiguration the route to create
     * @return the command
     */
    public static RouteCommand create(RouteConfiguration routeConfiguration) {
        return new RouteCommand(CommandType.CREATE, routeConfiguration.getRouteId(),
                routeConfiguration, null, null);
    }

    /**
     * Create a command to update an existing route's configuration.
     *
     * @param routeId the route ID to update
     * @param routeConfiguration the updated configuration
     * @return the command
     */
    public static RouteCommand update(String routeId, RouteConfiguration routeConfiguration) {
        return new RouteCommand(CommandType.UPDATE, routeId, routeConfiguration, null, null);
    }

    /**
     * Create a command to delete a route.
     *
     * @param routeId the route ID to delete
     * @return the command
     */
    public static RouteCommand delete(String routeId) {
        return new RouteCommand(CommandType.DELETE, routeId, null, null, null);
    }

    /**
     * Create a command to suspend a route.
     *
     * @param routeId the route ID to suspend
     * @param reason optional reason for suspension
     * @return the command
     */
    public static RouteCommand suspend(String routeId, String reason) {
        return new RouteCommand(CommandType.SUSPEND, routeId, null,
                RouteStatus.SUSPENDED, reason);
    }

    /**
     * Create a command to resume a suspended route.
     *
     * @param routeId the route ID to resume
     * @return the command
     */
    public static RouteCommand resume(String routeId) {
        return new RouteCommand(CommandType.RESUME, routeId, null,
                RouteStatus.ACTIVE, null);
    }

    /**
     * Create a command to update a route's status.
     * Used internally by the trigger evaluation engine.
     *
     * @param routeId the route ID
     * @param newStatus the new status
     * @param reason optional reason for the status change
     * @return the command
     */
    public static RouteCommand updateStatus(String routeId, RouteStatus newStatus, String reason) {
        return new RouteCommand(CommandType.UPDATE_STATUS, routeId, null, newStatus, reason);
    }

    public CommandType getType() {
        return type;
    }

    public String getRouteId() {
        return routeId;
    }

    public RouteConfiguration getRouteConfiguration() {
        return routeConfiguration;
    }

    public RouteStatus getNewStatus() {
        return newStatus;
    }

    public String getReason() {
        return reason;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "RouteCommand{" +
                "type=" + type +
                ", routeId='" + routeId + '\'' +
                ", newStatus=" + newStatus +
                ", reason='" + reason + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RouteCommand that = (RouteCommand) o;

        if (type != that.type) return false;
        if (!routeId.equals(that.routeId)) return false;
        if (routeConfiguration != null ? !routeConfiguration.equals(that.routeConfiguration) : that.routeConfiguration != null)
            return false;
        if (newStatus != that.newStatus) return false;
        if (reason != null ? !reason.equals(that.reason) : that.reason != null) return false;
        return timestamp.equals(that.timestamp);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + routeId.hashCode();
        result = 31 * result + (routeConfiguration != null ? routeConfiguration.hashCode() : 0);
        result = 31 * result + (newStatus != null ? newStatus.hashCode() : 0);
        result = 31 * result + (reason != null ? reason.hashCode() : 0);
        result = 31 * result + timestamp.hashCode();
        return result;
    }
}
