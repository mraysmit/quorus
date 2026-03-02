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

package dev.mars.quorus.monitoring;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable record representing detailed health check information for a component.
 * Provides type-safe health data that serializes cleanly to JSON.
 *
 * <p>This record replaces ad-hoc {@code Map<String, Object>} usage in health check responses
 * with a well-defined structure.
 *
 * @param component the name of the component being checked (e.g., "http-protocol", "transfer-engine")
 * @param status the health status of the component
 * @param message optional human-readable message describing the health state
 * @param metadata additional key-value pairs providing detailed diagnostic information
 * @param timestamp when this health check was performed
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-17
 * @version 1.0
 */
public record HealthDetail(
    String component,
    HealthStatus status,
    String message,
    Map<String, String> metadata,
    Instant timestamp
) {
    /**
     * Compact constructor with validation.
     */
    public HealthDetail {
        Objects.requireNonNull(component, "component cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        // Defensive copy of metadata map
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        timestamp = timestamp != null ? timestamp : Instant.now();
    }
    
    /**
     * Creates a HealthDetail with minimal required fields.
     *
     * @param component the component name
     * @param status the health status
     * @return a new HealthDetail instance
     */
    public static HealthDetail of(String component, HealthStatus status) {
        return new HealthDetail(component, status, null, Map.of(), Instant.now());
    }
    
    /**
     * Creates a HealthDetail with a message.
     *
     * @param component the component name
     * @param status the health status
     * @param message descriptive message
     * @return a new HealthDetail instance
     */
    public static HealthDetail of(String component, HealthStatus status, String message) {
        return new HealthDetail(component, status, message, Map.of(), Instant.now());
    }
    
    /**
     * Creates a new builder for constructing HealthDetail instances.
     *
     * @param component the component name
     * @return a new Builder instance
     */
    public static Builder builder(String component) {
        return new Builder(component);
    }
    
    /**
     * Returns true if this health check indicates the component is healthy.
     */
    public boolean isHealthy() {
        return status.isHealthy();
    }
    
    /**
     * Returns true if this health check indicates the component is operational.
     */
    public boolean isOperational() {
        return status.isOperational();
    }
    
    /**
     * Converts this HealthDetail to a Map for JSON serialization.
     * This provides backward compatibility with existing Map-based APIs.
     *
     * @return a map representation of this health detail
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("component", component);
        map.put("status", status.name());
        map.put("timestamp", timestamp.toString());
        if (message != null) {
            map.put("message", message);
        }
        if (!metadata.isEmpty()) {
            map.put("metadata", new HashMap<>(metadata));
        }
        return map;
    }
    
    /**
     * Builder for constructing HealthDetail instances with fluent API.
     */
    public static final class Builder {
        private final String component;
        private HealthStatus status = HealthStatus.UP;
        private String message;
        private final Map<String, String> metadata = new HashMap<>();
        private Instant timestamp;
        
        private Builder(String component) {
            this.component = Objects.requireNonNull(component, "component cannot be null");
        }
        
        public Builder status(HealthStatus status) {
            this.status = Objects.requireNonNull(status, "status cannot be null");
            return this;
        }
        
        public Builder up() {
            this.status = HealthStatus.UP;
            return this;
        }
        
        public Builder down() {
            this.status = HealthStatus.DOWN;
            return this;
        }
        
        public Builder degraded() {
            this.status = HealthStatus.DEGRADED;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        /**
         * Adds a string metadata entry.
         */
        public Builder metadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }
        
        /**
         * Adds a numeric metadata entry (converted to string).
         */
        public Builder metadata(String key, long value) {
            this.metadata.put(key, String.valueOf(value));
            return this;
        }
        
        /**
         * Adds a boolean metadata entry (converted to string).
         */
        public Builder metadata(String key, boolean value) {
            this.metadata.put(key, String.valueOf(value));
            return this;
        }
        
        /**
         * Adds all entries from the given map.
         */
        public Builder metadata(Map<String, String> entries) {
            this.metadata.putAll(entries);
            return this;
        }
        
        public HealthDetail build() {
            return new HealthDetail(component, status, message, metadata, timestamp);
        }
    }
}
