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
 * @since 2025-12-17
 * @version 1.0
 */
public class TransferEngineHealthCheck {

    private final HealthStatus status;
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
    
    public HealthStatus getHealthStatus() {
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
        return status.isHealthy();
    }
    
    /**
     * Converts this health check to a HealthDetail record for the transfer engine component.
     * @return a HealthDetail representation of this health check
     */
    public HealthDetail toHealthDetail() {
        HealthDetail.Builder builder = HealthDetail.builder("transfer-engine")
            .status(getHealthStatus())
            .timestamp(timestamp);
        
        if (message != null) {
            builder.message(message);
        }
        
        // Add protocol count as metadata
        long healthyCount = protocolHealthChecks.stream()
            .filter(ProtocolHealthCheck::isHealthy)
            .count();
        builder.metadata("totalProtocols", protocolHealthChecks.size());
        builder.metadata("healthyProtocols", healthyCount);
        builder.metadata("unhealthyProtocols", protocolHealthChecks.size() - healthyCount);
        
        // Convert system metrics to String metadata
        for (Map.Entry<String, Object> entry : systemMetrics.entrySet()) {
            builder.metadata(entry.getKey(), String.valueOf(entry.getValue()));
        }
        
        return builder.build();
    }
    
    /**
     * Returns a list of HealthDetail records for all protocol health checks.
     * @return list of HealthDetail records
     */
    public List<HealthDetail> getProtocolHealthDetails() {
        return protocolHealthChecks.stream()
            .map(ProtocolHealthCheck::toHealthDetail)
            .toList();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private HealthStatus status = HealthStatus.UP;
        private Instant timestamp;
        private final List<ProtocolHealthCheck> protocolHealthChecks = new ArrayList<>();
        private final Map<String, Object> systemMetrics = new HashMap<>();
        private String message;

        public Builder status(HealthStatus status) {
            this.status = status;
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

