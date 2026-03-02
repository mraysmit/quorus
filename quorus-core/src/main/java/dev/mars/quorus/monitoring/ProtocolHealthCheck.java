package dev.mars.quorus.monitoring;

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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Health check result for a transfer protocol.
 * Provides detailed health status and diagnostic information.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-17
 * @version 1.0
 */
public class ProtocolHealthCheck {
    
    /**
     * @deprecated Use {@link HealthStatus} instead. This inner enum will be removed in a future release.
     */
    @Deprecated(forRemoval = true)
    public enum Status {
        UP,      // Protocol is healthy and operational
        DOWN,    // Protocol is not operational
        DEGRADED; // Protocol is operational but experiencing issues
        
        /**
         * Converts this deprecated Status to the new HealthStatus enum.
         */
        public HealthStatus toHealthStatus() {
            return switch (this) {
                case UP -> HealthStatus.UP;
                case DOWN -> HealthStatus.DOWN;
                case DEGRADED -> HealthStatus.DEGRADED;
            };
        }
        
        /**
         * Converts from the new HealthStatus enum.
         */
        public static Status fromHealthStatus(HealthStatus healthStatus) {
            return switch (healthStatus) {
                case UP -> UP;
                case DOWN -> DOWN;
                case DEGRADED -> DEGRADED;
            };
        }
    }
    
    private final String protocolName;
    private final Status status;
    private final Instant timestamp;
    private final Map<String, Object> details;
    private final String message;
    
    private ProtocolHealthCheck(Builder builder) {
        this.protocolName = Objects.requireNonNull(builder.protocolName, "Protocol name cannot be null");
        this.status = Objects.requireNonNull(builder.status, "Status cannot be null");
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.details = new HashMap<>(builder.details);
        this.message = builder.message;
    }
    
    public String getProtocolName() {
        return protocolName;
    }
    
    /**
     * @deprecated Use {@link #getHealthStatus()} instead.
     */
    @Deprecated(forRemoval = true)
    public Status getStatus() {
        return status;
    }
    
    /**
     * Returns the health status using the unified HealthStatus enum.
     * @return the health status
     */
    public HealthStatus getHealthStatus() {
        return status.toHealthStatus();
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public Map<String, Object> getDetails() {
        return new HashMap<>(details);
    }
    
    public String getMessage() {
        return message;
    }
    
    public boolean isHealthy() {
        return status == Status.UP;
    }
    
    /**
     * Converts this protocol health check to a HealthDetail record.
     * @return a HealthDetail representation of this health check
     */
    public HealthDetail toHealthDetail() {
        HealthDetail.Builder builder = HealthDetail.builder(protocolName)
            .status(getHealthStatus())
            .timestamp(timestamp);
        
        if (message != null) {
            builder.message(message);
        }
        
        // Convert Object details to String metadata
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            builder.metadata(entry.getKey(), String.valueOf(entry.getValue()));
        }
        
        return builder.build();
    }
    
    /**
     * @deprecated Use {@link #toHealthDetail()} and then {@link HealthDetail#toMap()} if needed.
     */
    @Deprecated(forRemoval = true)
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("protocol", protocolName);
        map.put("status", status.name());
        map.put("timestamp", timestamp.toString());
        if (message != null) {
            map.put("message", message);
        }
        if (!details.isEmpty()) {
            map.put("details", new HashMap<>(details));
        }
        return map;
    }
    
    public static Builder builder(String protocolName) {
        return new Builder(protocolName);
    }
    
    public static class Builder {
        private final String protocolName;
        private Status status = Status.UP;
        private Instant timestamp;
        private final Map<String, Object> details = new HashMap<>();
        private String message;
        
        private Builder(String protocolName) {
            this.protocolName = protocolName;
        }
        
        /**
         * @deprecated Use {@link #healthStatus(HealthStatus)} instead.
         */
        @Deprecated(forRemoval = true)
        public Builder status(Status status) {
            this.status = status;
            return this;
        }
        
        /**
         * Sets the status using the unified HealthStatus enum.
         */
        public Builder healthStatus(HealthStatus healthStatus) {
            this.status = Status.fromHealthStatus(healthStatus);
            return this;
        }
        
        public Builder up() {
            this.status = Status.UP;
            return this;
        }
        
        public Builder down() {
            this.status = Status.DOWN;
            return this;
        }
        
        public Builder degraded() {
            this.status = Status.DEGRADED;
            return this;
        }
        
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder detail(String key, Object value) {
            this.details.put(key, value);
            return this;
        }
        
        public Builder details(Map<String, Object> details) {
            this.details.putAll(details);
            return this;
        }
        
        public ProtocolHealthCheck build() {
            return new ProtocolHealthCheck(this);
        }
    }
}

