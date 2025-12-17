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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Overall health check for the transfer engine.
 * Aggregates health status from all protocols and provides system-level diagnostics.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0 (Phase 2 - Dec 2025)
 */
public class TransferEngineHealthCheck {
    
    public enum Status {
        UP,      // All systems operational
        DOWN,    // Transfer engine is not operational
        DEGRADED // Some protocols are experiencing issues
    }
    
    private final Status status;
    private final Instant timestamp;
    private final List<ProtocolHealthCheck> protocolHealthChecks;
    private final Map<String, Object> systemMetrics;
    private final String message;
    
    private TransferEngineHealthCheck(Builder builder) {
        this.status = builder.status;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.protocolHealthChecks = new ArrayList<>(builder.protocolHealthChecks);
        this.systemMetrics = new HashMap<>(builder.systemMetrics);
        this.message = builder.message;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public List<ProtocolHealthCheck> getProtocolHealthChecks() {
        return new ArrayList<>(protocolHealthChecks);
    }
    
    public Map<String, Object> getSystemMetrics() {
        return new HashMap<>(systemMetrics);
    }
    
    public String getMessage() {
        return message;
    }
    
    public boolean isHealthy() {
        return status == Status.UP;
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("status", status.name());
        map.put("timestamp", timestamp.toString());
        
        if (message != null) {
            map.put("message", message);
        }
        
        // Protocol health checks
        List<Map<String, Object>> protocols = new ArrayList<>();
        for (ProtocolHealthCheck check : protocolHealthChecks) {
            protocols.add(check.toMap());
        }
        map.put("protocols", protocols);
        
        // System metrics
        if (!systemMetrics.isEmpty()) {
            map.put("system", new HashMap<>(systemMetrics));
        }
        
        // Summary statistics
        long totalProtocols = protocolHealthChecks.size();
        long healthyProtocols = protocolHealthChecks.stream()
                .filter(ProtocolHealthCheck::isHealthy)
                .count();
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalProtocols", totalProtocols);
        summary.put("healthyProtocols", healthyProtocols);
        summary.put("unhealthyProtocols", totalProtocols - healthyProtocols);
        map.put("summary", summary);
        
        return map;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private Status status = Status.UP;
        private Instant timestamp;
        private final List<ProtocolHealthCheck> protocolHealthChecks = new ArrayList<>();
        private final Map<String, Object> systemMetrics = new HashMap<>();
        private String message;
        
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
        
        public Builder addProtocolHealthCheck(ProtocolHealthCheck check) {
            this.protocolHealthChecks.add(check);
            return this;
        }
        
        public Builder systemMetric(String key, Object value) {
            this.systemMetrics.put(key, value);
            return this;
        }
        
        public Builder systemMetrics(Map<String, Object> metrics) {
            this.systemMetrics.putAll(metrics);
            return this;
        }
        
        public TransferEngineHealthCheck build() {
            return new TransferEngineHealthCheck(this);
        }
    }
}

