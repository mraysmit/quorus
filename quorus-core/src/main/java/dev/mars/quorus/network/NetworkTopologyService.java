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

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class NetworkTopologyService {
    
    private static final Logger logger = Logger.getLogger(NetworkTopologyService.class.getName());
    
    private final Vertx vertx;
    private final Map<String, NetworkNode> networkNodes = new ConcurrentHashMap<>();
    private final Map<String, NetworkPath> networkPaths = new ConcurrentHashMap<>();
    private final NetworkMetrics networkMetrics = new NetworkMetrics();

    public NetworkTopologyService(Vertx vertx) {
        this.vertx = vertx;
    }
    
    public Future<NetworkNode> discoverNode(String hostname) {
        logger.info("Discovering network topology for host: " + hostname);
        
        // Check if we already have cached information
        NetworkNode cachedNode = networkNodes.get(hostname);
        if (cachedNode != null && !cachedNode.isStale()) {
            logger.fine("Using cached network information for: " + hostname);
            return Future.succeededFuture(cachedNode);
        }
        
        // Perform network discovery (blocking)
        return vertx.executeBlocking(() -> performNetworkDiscovery(hostname))
            .onSuccess(node -> {
                networkNodes.put(hostname, node);
                logger.info("Network discovery completed for " + hostname + 
                           " - Latency: " + node.getLatency().toMillis() + "ms, " +
                           "Bandwidth: " + node.getEstimatedBandwidth() / (1024 * 1024) + " MB/s");
            })
            .onFailure(err -> {
                logger.warning("Network discovery failed for " + hostname + ": " + err.getMessage());
            })
            .recover(err -> {
                // Return a default node with conservative estimates
                return Future.succeededFuture(NetworkNode.builder()
                        .hostname(hostname)
                        .reachable(false)
                        .latency(Duration.ofSeconds(1))
                        .estimatedBandwidth(1024 * 1024) // 1 MB/s default
                        .networkType(NetworkType.UNKNOWN)
                        .lastUpdated(Instant.now())
                        .build());
            });
    }
    
    public Future<NetworkPath> findOptimalPath(String source, String destination) {
        String pathKey = source + "->" + destination;
        
        // Check cached path
        NetworkPath cachedPath = networkPaths.get(pathKey);
        if (cachedPath != null && !cachedPath.isStale()) {
            return Future.succeededFuture(cachedPath);
        }
        
        // Discover both nodes
        return Future.all(discoverNode(source), discoverNode(destination))
            .map(composite -> {
                NetworkNode sourceNode = composite.resultAt(0);
                NetworkNode destNode = composite.resultAt(1);
                
                // Calculate optimal path
                NetworkPath path = calculateOptimalPath(sourceNode, destNode);
                networkPaths.put(pathKey, path);
                
                logger.info("Optimal path calculated: " + source + " -> " + destination + 
                           " (Quality: " + path.getQualityScore() + ")");
                return path;
            });
    }
    
    public Future<NetworkRecommendations> getTransferRecommendations(String hostname, long transferSize) {
        NetworkNode node = networkNodes.get(hostname);
        Future<NetworkNode> nodeFuture;
        
        if (node == null) {
            nodeFuture = discoverNode(hostname);
        } else {
            nodeFuture = Future.succeededFuture(node);
        }
        
        return nodeFuture.map(n -> NetworkRecommendations.builder()
                .optimalBufferSize(calculateOptimalBufferSize(n, transferSize))
                .recommendedConcurrency(calculateOptimalConcurrency(n, transferSize))
                .estimatedTransferTime(estimateTransferTime(n, transferSize))
                .networkQuality(assessNetworkQuality(n))
                .useCompression(shouldUseCompression(n, transferSize))
                .build());
    }
    
    public void updateMetrics(String hostname, long bytesTransferred, Duration actualTime, boolean successful) {
        networkMetrics.recordTransfer(hostname, bytesTransferred, actualTime, successful);
        
        // Update node information based on actual performance
        NetworkNode node = networkNodes.get(hostname);
        if (node != null) {
            long actualBandwidth = bytesTransferred * 1000 / actualTime.toMillis();
            NetworkNode updatedNode = node.withUpdatedBandwidth(actualBandwidth);
            networkNodes.put(hostname, updatedNode);
        }
    }
    
    public NetworkStatistics getNetworkStatistics() {
        return NetworkStatistics.builder()
                .totalNodes(networkNodes.size())
                .reachableNodes(networkNodes.values().stream().mapToInt(n -> n.isReachable() ? 1 : 0).sum())
                .averageLatency(calculateAverageLatency())
                .totalBandwidth(calculateTotalBandwidth())
                .networkPaths(networkPaths.size())
                .transferMetrics(networkMetrics.getAggregatedMetrics())
                .build();
    }
    
    private NetworkNode performNetworkDiscovery(String hostname) throws Exception {
        Instant startTime = Instant.now();
        
        // Test reachability
        InetAddress address = InetAddress.getByName(hostname);
        boolean reachable = address.isReachable(5000); // 5 second timeout
        
        // Measure latency
        Duration latency = measureLatency(address);
        
        // Estimate bandwidth
        long estimatedBandwidth = estimateBandwidth(hostname, address);
        
        // Determine network type
        NetworkType networkType = determineNetworkType(address);
        
        return NetworkNode.builder()
                .hostname(hostname)
                .ipAddress(address.getHostAddress())
                .reachable(reachable)
                .latency(latency)
                .estimatedBandwidth(estimatedBandwidth)
                .networkType(networkType)
                .lastUpdated(Instant.now())
                .build();
    }
    
    private Duration measureLatency(InetAddress address) {
        try {
            long startTime = System.nanoTime();
            boolean reachable = address.isReachable(1000);
            long endTime = System.nanoTime();
            
            if (reachable) {
                return Duration.ofNanos(endTime - startTime);
            } else {
                return Duration.ofSeconds(1); // Default high latency for unreachable hosts
            }
        } catch (Exception e) {
            return Duration.ofSeconds(1);
        }
    }
    
    private long estimateBandwidth(String hostname, InetAddress address) {
        // For corporate networks, use heuristics based on network type
        NetworkType networkType = determineNetworkType(address);
        
        switch (networkType) {
            case LOCAL_NETWORK:
                return 1024L * 1024 * 1024; // 1 GB/s for local network
            case CORPORATE_NETWORK:
                return 100L * 1024 * 1024; // 100 MB/s for corporate network
            case INTERNET:
                return 10L * 1024 * 1024; // 10 MB/s for internet
            default:
                return 1024 * 1024; // 1 MB/s default
        }
    }
    
    private NetworkType determineNetworkType(InetAddress address) {
        if (address.isLoopbackAddress()) {
            return NetworkType.LOCAL_NETWORK;
        } else if (address.isSiteLocalAddress()) {
            return NetworkType.CORPORATE_NETWORK;
        } else if (address.isLinkLocalAddress()) {
            return NetworkType.LOCAL_NETWORK;
        } else {
            return NetworkType.INTERNET;
        }
    }
    
    private NetworkPath calculateOptimalPath(NetworkNode source, NetworkNode destination) {
        // Calculate path quality based on both nodes
        double qualityScore = calculatePathQuality(source, destination);
        
        // Determine optimal transfer strategy
        TransferStrategy strategy = determineTransferStrategy(source, destination);
        
        return NetworkPath.builder()
                .source(source.getHostname())
                .destination(destination.getHostname())
                .qualityScore(qualityScore)
                .estimatedLatency(source.getLatency().plus(destination.getLatency()))
                .estimatedBandwidth(Math.min(source.getEstimatedBandwidth(), destination.getEstimatedBandwidth()))
                .transferStrategy(strategy)
                .lastUpdated(Instant.now())
                .build();
    }
    
    private double calculatePathQuality(NetworkNode source, NetworkNode destination) {
        if (!source.isReachable() || !destination.isReachable()) {
            return 0.0;
        }
        
        // Quality based on latency and bandwidth
        double latencyScore = Math.max(0, 1.0 - source.getLatency().toMillis() / 1000.0);
        double bandwidthScore = Math.min(1.0, source.getEstimatedBandwidth() / (100.0 * 1024 * 1024));
        
        return (latencyScore + bandwidthScore) / 2.0;
    }
    
    private TransferStrategy determineTransferStrategy(NetworkNode source, NetworkNode destination) {
        long minBandwidth = Math.min(source.getEstimatedBandwidth(), destination.getEstimatedBandwidth());
        Duration maxLatency = source.getLatency().compareTo(destination.getLatency()) > 0 ? 
                source.getLatency() : destination.getLatency();
        
        if (minBandwidth > 100L * 1024 * 1024 && maxLatency.toMillis() < 10) {
            return TransferStrategy.HIGH_THROUGHPUT;
        } else if (maxLatency.toMillis() > 100) {
            return TransferStrategy.HIGH_LATENCY_OPTIMIZED;
        } else {
            return TransferStrategy.BALANCED;
        }
    }
    
    private int calculateOptimalBufferSize(NetworkNode node, long transferSize) {
        // Buffer size based on bandwidth and latency
        long bandwidthMBps = node.getEstimatedBandwidth() / (1024 * 1024);
        long latencyMs = node.getLatency().toMillis();
        
        // Bandwidth-delay product
        int optimalSize = (int) Math.min(bandwidthMBps * latencyMs * 1024, 1024 * 1024); // Max 1MB
        
        // Ensure minimum buffer size
        return Math.max(optimalSize, 8192); // Min 8KB
    }
    
    private int calculateOptimalConcurrency(NetworkNode node, long transferSize) {
        if (transferSize < 1024 * 1024) { // < 1MB
            return 1;
        } else if (node.getEstimatedBandwidth() > 100L * 1024 * 1024) { // > 100MB/s
            return Math.min(8, (int) (transferSize / (10L * 1024 * 1024))); // Max 8 connections
        } else {
            return Math.min(4, (int) (transferSize / (50L * 1024 * 1024))); // Max 4 connections
        }
    }
    
    private Duration estimateTransferTime(NetworkNode node, long transferSize) {
        long bandwidthBps = node.getEstimatedBandwidth();
        long transferTimeMs = (transferSize * 1000) / bandwidthBps;
        return Duration.ofMillis(transferTimeMs).plus(node.getLatency());
    }
    
    private NetworkQuality assessNetworkQuality(NetworkNode node) {
        if (!node.isReachable()) {
            return NetworkQuality.POOR;
        }
        
        long bandwidthMBps = node.getEstimatedBandwidth() / (1024 * 1024);
        long latencyMs = node.getLatency().toMillis();
        
        if (bandwidthMBps > 100 && latencyMs < 10) {
            return NetworkQuality.EXCELLENT;
        } else if (bandwidthMBps > 50 && latencyMs < 50) {
            return NetworkQuality.GOOD;
        } else if (bandwidthMBps > 10 && latencyMs < 200) {
            return NetworkQuality.FAIR;
        } else {
            return NetworkQuality.POOR;
        }
    }
    
    private boolean shouldUseCompression(NetworkNode node, long transferSize) {
        // Use compression for slow networks or large transfers
        long bandwidthMBps = node.getEstimatedBandwidth() / (1024 * 1024);
        return bandwidthMBps < 50 || transferSize > 100L * 1024 * 1024; // < 50MB/s or > 100MB file
    }
    
    private Duration calculateAverageLatency() {
        return networkNodes.values().stream()
                .filter(NetworkNode::isReachable)
                .map(NetworkNode::getLatency)
                .reduce(Duration.ZERO, Duration::plus)
                .dividedBy(Math.max(1, networkNodes.size()));
    }
    
    private long calculateTotalBandwidth() {
        return networkNodes.values().stream()
                .filter(NetworkNode::isReachable)
                .mapToLong(NetworkNode::getEstimatedBandwidth)
                .sum();
    }
    
    public enum NetworkType {
        LOCAL_NETWORK,
        CORPORATE_NETWORK,
        INTERNET,
        UNKNOWN
    }
    
    public enum TransferStrategy {
        HIGH_THROUGHPUT,
        HIGH_LATENCY_OPTIMIZED,
        BALANCED
    }
    
    public enum NetworkQuality {
        EXCELLENT,
        GOOD,
        FAIR,
        POOR
    }

    // Supporting classes for network topology service

    public static class NetworkPath {
        private final String source;
        private final String destination;
        private final double qualityScore;
        private final Duration estimatedLatency;
        private final long estimatedBandwidth;
        private final TransferStrategy transferStrategy;
        private final Instant lastUpdated;

        private NetworkPath(Builder builder) {
            this.source = builder.source;
            this.destination = builder.destination;
            this.qualityScore = builder.qualityScore;
            this.estimatedLatency = builder.estimatedLatency;
            this.estimatedBandwidth = builder.estimatedBandwidth;
            this.transferStrategy = builder.transferStrategy;
            this.lastUpdated = builder.lastUpdated;
        }

        public String getSource() { return source; }
        public String getDestination() { return destination; }
        public double getQualityScore() { return qualityScore; }
        public Duration getEstimatedLatency() { return estimatedLatency; }
        public long getEstimatedBandwidth() { return estimatedBandwidth; }
        public TransferStrategy getTransferStrategy() { return transferStrategy; }
        public Instant getLastUpdated() { return lastUpdated; }

        public boolean isStale() {
            return Instant.now().isAfter(lastUpdated.plus(Duration.ofMinutes(5)));
        }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String source;
            private String destination;
            private double qualityScore;
            private Duration estimatedLatency = Duration.ZERO;
            private long estimatedBandwidth = 0;
            private TransferStrategy transferStrategy = TransferStrategy.BALANCED;
            private Instant lastUpdated = Instant.now();

            public Builder source(String source) { this.source = source; return this; }
            public Builder destination(String destination) { this.destination = destination; return this; }
            public Builder qualityScore(double qualityScore) { this.qualityScore = qualityScore; return this; }
            public Builder estimatedLatency(Duration estimatedLatency) { this.estimatedLatency = estimatedLatency; return this; }
            public Builder estimatedBandwidth(long estimatedBandwidth) { this.estimatedBandwidth = estimatedBandwidth; return this; }
            public Builder transferStrategy(TransferStrategy transferStrategy) { this.transferStrategy = transferStrategy; return this; }
            public Builder lastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; return this; }

            public NetworkPath build() { return new NetworkPath(this); }
        }
    }

    public static class NetworkRecommendations {
        private final int optimalBufferSize;
        private final int recommendedConcurrency;
        private final Duration estimatedTransferTime;
        private final NetworkQuality networkQuality;
        private final boolean useCompression;

        private NetworkRecommendations(Builder builder) {
            this.optimalBufferSize = builder.optimalBufferSize;
            this.recommendedConcurrency = builder.recommendedConcurrency;
            this.estimatedTransferTime = builder.estimatedTransferTime;
            this.networkQuality = builder.networkQuality;
            this.useCompression = builder.useCompression;
        }

        public int getOptimalBufferSize() { return optimalBufferSize; }
        public int getRecommendedConcurrency() { return recommendedConcurrency; }
        public Duration getEstimatedTransferTime() { return estimatedTransferTime; }
        public NetworkQuality getNetworkQuality() { return networkQuality; }
        public boolean isUseCompression() { return useCompression; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private int optimalBufferSize = 64 * 1024;
            private int recommendedConcurrency = 1;
            private Duration estimatedTransferTime = Duration.ZERO;
            private NetworkQuality networkQuality = NetworkQuality.FAIR;
            private boolean useCompression = false;

            public Builder optimalBufferSize(int optimalBufferSize) { this.optimalBufferSize = optimalBufferSize; return this; }
            public Builder recommendedConcurrency(int recommendedConcurrency) { this.recommendedConcurrency = recommendedConcurrency; return this; }
            public Builder estimatedTransferTime(Duration estimatedTransferTime) { this.estimatedTransferTime = estimatedTransferTime; return this; }
            public Builder networkQuality(NetworkQuality networkQuality) { this.networkQuality = networkQuality; return this; }
            public Builder useCompression(boolean useCompression) { this.useCompression = useCompression; return this; }

            public NetworkRecommendations build() { return new NetworkRecommendations(this); }
        }
    }

    public static class NetworkStatistics {
        private final int totalNodes;
        private final int reachableNodes;
        private final Duration averageLatency;
        private final long totalBandwidth;
        private final int networkPaths;
        private final Object transferMetrics;

        private NetworkStatistics(Builder builder) {
            this.totalNodes = builder.totalNodes;
            this.reachableNodes = builder.reachableNodes;
            this.averageLatency = builder.averageLatency;
            this.totalBandwidth = builder.totalBandwidth;
            this.networkPaths = builder.networkPaths;
            this.transferMetrics = builder.transferMetrics;
        }

        public int getTotalNodes() { return totalNodes; }
        public int getReachableNodes() { return reachableNodes; }
        public Duration getAverageLatency() { return averageLatency; }
        public long getTotalBandwidth() { return totalBandwidth; }
        public int getNetworkPaths() { return networkPaths; }
        public Object getTransferMetrics() { return transferMetrics; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private int totalNodes = 0;
            private int reachableNodes = 0;
            private Duration averageLatency = Duration.ZERO;
            private long totalBandwidth = 0;
            private int networkPaths = 0;
            private Object transferMetrics = null;

            public Builder totalNodes(int totalNodes) { this.totalNodes = totalNodes; return this; }
            public Builder reachableNodes(int reachableNodes) { this.reachableNodes = reachableNodes; return this; }
            public Builder averageLatency(Duration averageLatency) { this.averageLatency = averageLatency; return this; }
            public Builder totalBandwidth(long totalBandwidth) { this.totalBandwidth = totalBandwidth; return this; }
            public Builder networkPaths(int networkPaths) { this.networkPaths = networkPaths; return this; }
            public Builder transferMetrics(Object transferMetrics) { this.transferMetrics = transferMetrics; return this; }

            public NetworkStatistics build() { return new NetworkStatistics(this); }
        }
    }

    /**
     * Network metrics tracking
     */
    public static class NetworkMetrics {
        private final Map<String, List<TransferMetric>> hostMetrics = new ConcurrentHashMap<>();

        public void recordTransfer(String hostname, long bytesTransferred, Duration actualTime, boolean successful) {
            TransferMetric metric = new TransferMetric(bytesTransferred, actualTime, successful, Instant.now());
            hostMetrics.computeIfAbsent(hostname, k -> new ArrayList<>()).add(metric);
        }

        public Object getAggregatedMetrics() {
            return "Aggregated metrics for " + hostMetrics.size() + " hosts";
        }

        private static class TransferMetric {
            final long bytesTransferred;
            final Duration transferTime;
            final boolean successful;
            final Instant timestamp;

            TransferMetric(long bytesTransferred, Duration transferTime, boolean successful, Instant timestamp) {
                this.bytesTransferred = bytesTransferred;
                this.transferTime = transferTime;
                this.successful = successful;
                this.timestamp = timestamp;
            }
        }
    }
}
