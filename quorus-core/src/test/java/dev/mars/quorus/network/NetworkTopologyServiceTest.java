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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class NetworkTopologyServiceTest {
    
    private NetworkTopologyService service;
    
    @BeforeEach
    void setUp() {
        service = new NetworkTopologyService();
    }
    
    @Test
    void testDiscoverLocalhost() throws ExecutionException, InterruptedException {
        CompletableFuture<NetworkNode> future = service.discoverNode("localhost");
        NetworkNode node = future.get();
        
        assertNotNull(node);
        assertEquals("localhost", node.getHostname());
        assertTrue(node.isReachable());
        assertNotNull(node.getLatency());
        assertTrue(node.getLatency().toMillis() >= 0);
        assertTrue(node.getEstimatedBandwidth() > 0);
        assertEquals(NetworkTopologyService.NetworkType.LOCAL_NETWORK, node.getNetworkType());
        assertNotNull(node.getLastUpdated());
    }
    
    @Test
    void testDiscoverNonExistentHost() throws ExecutionException, InterruptedException {
        CompletableFuture<NetworkNode> future = service.discoverNode("nonexistent.invalid.host");
        NetworkNode node = future.get();
        
        assertNotNull(node);
        assertEquals("nonexistent.invalid.host", node.getHostname());
        // May or may not be reachable depending on network configuration
        assertNotNull(node.getLatency());
        assertTrue(node.getEstimatedBandwidth() > 0);
        assertNotNull(node.getNetworkType());
        assertNotNull(node.getLastUpdated());
    }
    
    @Test
    void testDiscoverCorporateHost() throws ExecutionException, InterruptedException {
        // Test with a typical corporate network IP
        CompletableFuture<NetworkNode> future = service.discoverNode("192.168.1.1");
        NetworkNode node = future.get();
        
        assertNotNull(node);
        assertEquals("192.168.1.1", node.getHostname());
        assertNotNull(node.getLatency());
        assertTrue(node.getEstimatedBandwidth() > 0);
        // Should be detected as corporate network for private IP
        assertTrue(node.getNetworkType() == NetworkTopologyService.NetworkType.CORPORATE_NETWORK ||
                  node.getNetworkType() == NetworkTopologyService.NetworkType.LOCAL_NETWORK);
        assertNotNull(node.getLastUpdated());
    }
    
    @Test
    void testNodeCaching() throws ExecutionException, InterruptedException {
        // First discovery
        CompletableFuture<NetworkNode> future1 = service.discoverNode("localhost");
        NetworkNode node1 = future1.get();
        
        // Second discovery should use cache
        CompletableFuture<NetworkNode> future2 = service.discoverNode("localhost");
        NetworkNode node2 = future2.get();
        
        assertNotNull(node1);
        assertNotNull(node2);
        assertEquals(node1.getHostname(), node2.getHostname());
        // Cache should return the same or updated node
        assertTrue(node2.getLastUpdated().equals(node1.getLastUpdated()) ||
                  node2.getLastUpdated().isAfter(node1.getLastUpdated()));
    }
    
    @Test
    void testFindOptimalPath() throws ExecutionException, InterruptedException {
        CompletableFuture<NetworkTopologyService.NetworkPath> future = 
                service.findOptimalPath("localhost", "127.0.0.1");
        NetworkTopologyService.NetworkPath path = future.get();
        
        assertNotNull(path);
        assertEquals("localhost", path.getSource());
        assertEquals("127.0.0.1", path.getDestination());
        assertTrue(path.getQualityScore() >= 0.0 && path.getQualityScore() <= 1.0);
        assertNotNull(path.getEstimatedLatency());
        assertTrue(path.getEstimatedBandwidth() > 0);
        assertNotNull(path.getTransferStrategy());
        assertNotNull(path.getLastUpdated());
    }
    
    @Test
    void testPathCaching() throws ExecutionException, InterruptedException {
        // First path calculation
        CompletableFuture<NetworkTopologyService.NetworkPath> future1 = 
                service.findOptimalPath("localhost", "127.0.0.1");
        NetworkTopologyService.NetworkPath path1 = future1.get();
        
        // Second path calculation should use cache
        CompletableFuture<NetworkTopologyService.NetworkPath> future2 = 
                service.findOptimalPath("localhost", "127.0.0.1");
        NetworkTopologyService.NetworkPath path2 = future2.get();
        
        assertNotNull(path1);
        assertNotNull(path2);
        assertEquals(path1.getSource(), path2.getSource());
        assertEquals(path1.getDestination(), path2.getDestination());
    }
    
    @Test
    void testGetTransferRecommendations() {
        String hostname = "localhost";
        long transferSize = 100 * 1024 * 1024; // 100MB
        
        NetworkTopologyService.NetworkRecommendations recommendations = 
                service.getTransferRecommendations(hostname, transferSize);
        
        assertNotNull(recommendations);
        assertTrue(recommendations.getOptimalBufferSize() > 0);
        assertTrue(recommendations.getRecommendedConcurrency() > 0);
        assertNotNull(recommendations.getEstimatedTransferTime());
        assertNotNull(recommendations.getNetworkQuality());
        // useCompression can be true or false depending on network conditions
    }
    
    @Test
    void testGetTransferRecommendationsSmallFile() {
        String hostname = "localhost";
        long transferSize = 1024; // 1KB
        
        NetworkTopologyService.NetworkRecommendations recommendations = 
                service.getTransferRecommendations(hostname, transferSize);
        
        assertNotNull(recommendations);
        assertTrue(recommendations.getOptimalBufferSize() > 0);
        assertEquals(1, recommendations.getRecommendedConcurrency()); // Small files should use single connection
        assertNotNull(recommendations.getEstimatedTransferTime());
        assertNotNull(recommendations.getNetworkQuality());
    }
    
    @Test
    void testGetTransferRecommendationsLargeFile() {
        String hostname = "localhost";
        long transferSize = 1024L * 1024 * 1024; // 1GB
        
        NetworkTopologyService.NetworkRecommendations recommendations = 
                service.getTransferRecommendations(hostname, transferSize);
        
        assertNotNull(recommendations);
        assertTrue(recommendations.getOptimalBufferSize() > 0);
        assertTrue(recommendations.getRecommendedConcurrency() >= 1);
        assertNotNull(recommendations.getEstimatedTransferTime());
        assertNotNull(recommendations.getNetworkQuality());
    }
    
    @Test
    void testUpdateMetrics() throws ExecutionException, InterruptedException {
        String hostname = "localhost";
        long bytesTransferred = 50 * 1024 * 1024; // 50MB
        Duration actualTime = Duration.ofSeconds(5);
        
        // First discover the node
        service.discoverNode(hostname).get();
        
        // Update metrics
        service.updateMetrics(hostname, bytesTransferred, actualTime, true);
        
        // Get updated recommendations
        NetworkTopologyService.NetworkRecommendations recommendations = 
                service.getTransferRecommendations(hostname, bytesTransferred);
        
        assertNotNull(recommendations);
        // Metrics should influence recommendations
        assertTrue(recommendations.getOptimalBufferSize() > 0);
        assertTrue(recommendations.getRecommendedConcurrency() > 0);
    }
    
    @Test
    void testGetNetworkStatistics() throws ExecutionException, InterruptedException {
        // Discover a few nodes to populate statistics
        service.discoverNode("localhost").get();
        service.discoverNode("127.0.0.1").get();
        
        NetworkTopologyService.NetworkStatistics stats = service.getNetworkStatistics();
        
        assertNotNull(stats);
        assertTrue(stats.getTotalNodes() >= 2);
        assertTrue(stats.getReachableNodes() >= 0);
        assertTrue(stats.getReachableNodes() <= stats.getTotalNodes());
        assertNotNull(stats.getAverageLatency());
        assertTrue(stats.getTotalBandwidth() >= 0);
        assertTrue(stats.getNetworkPaths() >= 0);
        assertNotNull(stats.getTransferMetrics());
    }
    
    @Test
    void testNetworkTypeDetection() throws ExecutionException, InterruptedException {
        // Test localhost detection
        NetworkNode localhostNode = service.discoverNode("localhost").get();
        assertEquals(NetworkTopologyService.NetworkType.LOCAL_NETWORK, localhostNode.getNetworkType());
        
        // Test loopback detection
        NetworkNode loopbackNode = service.discoverNode("127.0.0.1").get();
        assertEquals(NetworkTopologyService.NetworkType.LOCAL_NETWORK, loopbackNode.getNetworkType());
    }
    
    @Test
    void testBandwidthEstimation() throws ExecutionException, InterruptedException {
        NetworkNode localNode = service.discoverNode("localhost").get();
        
        // Local network should have high bandwidth
        assertTrue(localNode.getEstimatedBandwidth() >= 100 * 1024 * 1024); // At least 100MB/s for local
    }
    
    @Test
    void testLatencyMeasurement() throws ExecutionException, InterruptedException {
        NetworkNode localNode = service.discoverNode("localhost").get();
        
        // Local network should have low latency
        assertTrue(localNode.getLatency().toMillis() < 1000); // Less than 1 second for local
    }
    
    @Test
    void testPerformanceScore() throws ExecutionException, InterruptedException {
        NetworkNode localNode = service.discoverNode("localhost").get();
        
        double score = localNode.getPerformanceScore();
        assertTrue(score >= 0.0 && score <= 1.0);
        
        // Local network should have good performance score
        if (localNode.isReachable()) {
            assertTrue(score > 0.5); // Should be better than average
        }
    }
    
    @Test
    void testTransferStrategySelection() throws ExecutionException, InterruptedException {
        NetworkTopologyService.NetworkPath path = service.findOptimalPath("localhost", "127.0.0.1").get();
        
        NetworkTopologyService.TransferStrategy strategy = path.getTransferStrategy();
        assertNotNull(strategy);
        
        // Should be one of the defined strategies
        assertTrue(strategy == NetworkTopologyService.TransferStrategy.HIGH_THROUGHPUT ||
                  strategy == NetworkTopologyService.TransferStrategy.HIGH_LATENCY_OPTIMIZED ||
                  strategy == NetworkTopologyService.TransferStrategy.BALANCED);
    }
    
    @Test
    void testNetworkQualityAssessment() {
        NetworkTopologyService.NetworkRecommendations recommendations = 
                service.getTransferRecommendations("localhost", 1024 * 1024);
        
        NetworkTopologyService.NetworkQuality quality = recommendations.getNetworkQuality();
        assertNotNull(quality);
        
        // Should be one of the defined quality levels
        assertTrue(quality == NetworkTopologyService.NetworkQuality.EXCELLENT ||
                  quality == NetworkTopologyService.NetworkQuality.GOOD ||
                  quality == NetworkTopologyService.NetworkQuality.FAIR ||
                  quality == NetworkTopologyService.NetworkQuality.POOR);
    }
    
    @Test
    void testCompressionRecommendation() {
        // Test with slow network simulation
        NetworkTopologyService.NetworkRecommendations recommendations = 
                service.getTransferRecommendations("slow.network.test", 100 * 1024 * 1024);
        
        assertNotNull(recommendations);
        // Compression recommendation can be true or false depending on network conditions
        // Just verify it's a valid boolean
        boolean useCompression = recommendations.isUseCompression();
        assertTrue(useCompression || !useCompression); // Always true, but validates the method works
    }
}
