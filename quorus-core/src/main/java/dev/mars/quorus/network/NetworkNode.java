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

package dev.mars.quorus.network;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a network node with its characteristics and performance metrics.
 */
public class NetworkNode {
    
    private final String hostname;
    private final String ipAddress;
    private final boolean reachable;
    private final Duration latency;
    private final long estimatedBandwidth;
    private final NetworkTopologyService.NetworkType networkType;
    private final Instant lastUpdated;
    
    private static final Duration CACHE_DURATION = Duration.ofMinutes(5);
    
    private NetworkNode(Builder builder) {
        this.hostname = builder.hostname;
        this.ipAddress = builder.ipAddress;
        this.reachable = builder.reachable;
        this.latency = builder.latency;
        this.estimatedBandwidth = builder.estimatedBandwidth;
        this.networkType = builder.networkType;
        this.lastUpdated = builder.lastUpdated;
    }
    
    // Getters
    public String getHostname() { return hostname; }
    public String getIpAddress() { return ipAddress; }
    public boolean isReachable() { return reachable; }
    public Duration getLatency() { return latency; }
    public long getEstimatedBandwidth() { return estimatedBandwidth; }
    public NetworkTopologyService.NetworkType getNetworkType() { return networkType; }
    public Instant getLastUpdated() { return lastUpdated; }
    
    /**
     * Check if this node information is stale and needs refresh
     */
    public boolean isStale() {
        return Instant.now().isAfter(lastUpdated.plus(CACHE_DURATION));
    }
    
    /**
     * Create a new node with updated bandwidth information
     */
    public NetworkNode withUpdatedBandwidth(long newBandwidth) {
        return toBuilder()
                .estimatedBandwidth(newBandwidth)
                .lastUpdated(Instant.now())
                .build();
    }
    
    /**
     * Get network performance score (0.0 to 1.0)
     */
    public double getPerformanceScore() {
        if (!reachable) return 0.0;
        
        // Score based on latency and bandwidth
        double latencyScore = Math.max(0, 1.0 - latency.toMillis() / 1000.0);
        double bandwidthScore = Math.min(1.0, estimatedBandwidth / (100.0 * 1024 * 1024));
        
        return (latencyScore + bandwidthScore) / 2.0;
    }
    
    public Builder toBuilder() {
        return new Builder()
                .hostname(hostname)
                .ipAddress(ipAddress)
                .reachable(reachable)
                .latency(latency)
                .estimatedBandwidth(estimatedBandwidth)
                .networkType(networkType)
                .lastUpdated(lastUpdated);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String hostname;
        private String ipAddress;
        private boolean reachable = false;
        private Duration latency = Duration.ofSeconds(1);
        private long estimatedBandwidth = 1024 * 1024; // 1 MB/s default
        private NetworkTopologyService.NetworkType networkType = NetworkTopologyService.NetworkType.UNKNOWN;
        private Instant lastUpdated = Instant.now();
        
        public Builder hostname(String hostname) { this.hostname = hostname; return this; }
        public Builder ipAddress(String ipAddress) { this.ipAddress = ipAddress; return this; }
        public Builder reachable(boolean reachable) { this.reachable = reachable; return this; }
        public Builder latency(Duration latency) { this.latency = latency; return this; }
        public Builder estimatedBandwidth(long estimatedBandwidth) { this.estimatedBandwidth = estimatedBandwidth; return this; }
        public Builder networkType(NetworkTopologyService.NetworkType networkType) { this.networkType = networkType; return this; }
        public Builder lastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; return this; }
        
        public NetworkNode build() {
            Objects.requireNonNull(hostname, "hostname cannot be null");
            Objects.requireNonNull(latency, "latency cannot be null");
            Objects.requireNonNull(networkType, "networkType cannot be null");
            Objects.requireNonNull(lastUpdated, "lastUpdated cannot be null");
            
            return new NetworkNode(this);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NetworkNode that = (NetworkNode) o;
        return Objects.equals(hostname, that.hostname);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(hostname);
    }
    
    @Override
    public String toString() {
        return "NetworkNode{" +
                "hostname='" + hostname + '\'' +
                ", reachable=" + reachable +
                ", latency=" + latency.toMillis() + "ms" +
                ", bandwidth=" + (estimatedBandwidth / (1024 * 1024)) + "MB/s" +
                ", type=" + networkType +
                '}';
    }
}
