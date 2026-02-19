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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * Configuration for a route — the primary orchestration primitive in Quorus.
 * <p>
 * A route defines a source agent → destination agent file transfer mapping
 * with trigger conditions that determine when transfers are initiated.
 * <p>
 * Routes are replicated via Raft consensus and evaluated by the LEADER controller.
 *
 * <h3>Example</h3>
 * <pre>
 *   RouteConfiguration route = new RouteConfiguration(
 *       "route-001",                              // routeId
 *       "crm-to-warehouse",                       // name
 *       "CRM data export to data warehouse",      // description
 *       "agent-crm-001",                          // sourceAgentId
 *       "/corporate-data/crm/export/",            // sourceLocation
 *       "agent-warehouse-001",                    // destinationAgentId
 *       "/corporate-data/warehouse/import/",      // destinationLocation
 *       triggerConfig,                            // trigger
 *       RouteStatus.CONFIGURED,                   // status
 *       routeOptions,                             // options
 *       Instant.now(),                            // createdAt
 *       Instant.now()                             // updatedAt
 *   );
 * </pre>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-02-19
 */
public class RouteConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String routeId;
    private final String name;
    private final String description;
    private final String sourceAgentId;
    private final String sourceLocation;
    private final String destinationAgentId;
    private final String destinationLocation;
    private final TriggerConfiguration trigger;
    private final RouteStatus status;
    private final Map<String, String> options;
    private final Instant createdAt;
    private final Instant updatedAt;

    @JsonCreator
    public RouteConfiguration(
            @JsonProperty("routeId") String routeId,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("sourceAgentId") String sourceAgentId,
            @JsonProperty("sourceLocation") String sourceLocation,
            @JsonProperty("destinationAgentId") String destinationAgentId,
            @JsonProperty("destinationLocation") String destinationLocation,
            @JsonProperty("trigger") TriggerConfiguration trigger,
            @JsonProperty("status") RouteStatus status,
            @JsonProperty("options") Map<String, String> options,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("updatedAt") Instant updatedAt) {
        this.routeId = routeId;
        this.name = name;
        this.description = description;
        this.sourceAgentId = sourceAgentId;
        this.sourceLocation = sourceLocation;
        this.destinationAgentId = destinationAgentId;
        this.destinationLocation = destinationLocation;
        this.trigger = trigger;
        this.status = status;
        this.options = options;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    /**
     * Creates a new RouteConfiguration with an updated status and updatedAt timestamp.
     */
    public RouteConfiguration withStatus(RouteStatus newStatus) {
        return new RouteConfiguration(
                routeId, name, description,
                sourceAgentId, sourceLocation,
                destinationAgentId, destinationLocation,
                trigger, newStatus, options,
                createdAt, Instant.now());
    }

    /**
     * Creates a new RouteConfiguration with updated fields from another configuration.
     * Preserves the routeId and createdAt from this instance.
     */
    public RouteConfiguration withUpdate(RouteConfiguration update) {
        return new RouteConfiguration(
                this.routeId,
                update.name != null ? update.name : this.name,
                update.description != null ? update.description : this.description,
                update.sourceAgentId != null ? update.sourceAgentId : this.sourceAgentId,
                update.sourceLocation != null ? update.sourceLocation : this.sourceLocation,
                update.destinationAgentId != null ? update.destinationAgentId : this.destinationAgentId,
                update.destinationLocation != null ? update.destinationLocation : this.destinationLocation,
                update.trigger != null ? update.trigger : this.trigger,
                this.status,
                update.options != null ? update.options : this.options,
                this.createdAt,
                Instant.now());
    }

    public String getRouteId() {
        return routeId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getSourceAgentId() {
        return sourceAgentId;
    }

    public String getSourceLocation() {
        return sourceLocation;
    }

    public String getDestinationAgentId() {
        return destinationAgentId;
    }

    public String getDestinationLocation() {
        return destinationLocation;
    }

    public TriggerConfiguration getTrigger() {
        return trigger;
    }

    public RouteStatus getStatus() {
        return status;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "RouteConfiguration{" +
                "routeId='" + routeId + '\'' +
                ", name='" + name + '\'' +
                ", sourceAgentId='" + sourceAgentId + '\'' +
                ", destinationAgentId='" + destinationAgentId + '\'' +
                ", trigger=" + trigger +
                ", status=" + status +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RouteConfiguration that = (RouteConfiguration) o;
        return routeId.equals(that.routeId);
    }

    @Override
    public int hashCode() {
        return routeId.hashCode();
    }
}
