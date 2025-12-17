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
 * @since 2.0 (Phase 2 - Dec 2025)
 */
public class ProtocolHealthCheck {
    
    public enum Status {
        UP,      // Protocol is healthy and operational
        DOWN,    // Protocol is not operational
        DEGRADED // Protocol is operational but experiencing issues
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
    
    public Status getStatus() {
        return status;
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
        
        public Builder status(Status status) {
            this.status = status;
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

