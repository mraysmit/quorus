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
import org.junit.jupiter.api.Timeout;

import java.time.Duration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link NetworkTopologyService}, called the way callers use it since RT-03e: blocking, on
 * the test thread. Discovery probes real hosts (localhost, loopback, private and invalid addresses).
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2025-08-18
 */
@Timeout(value = 60, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class NetworkTopologyServiceTest {

    private NetworkTopologyService service;

    @BeforeEach
    void setUp() {
        service = new NetworkTopologyService();
    }

    @Test
    void testDiscoverLocalhost() {
        NetworkNode node = service.discoverNode("localhost");

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
    void testDiscoverNonExistentHost() {
        NetworkNode node = service.discoverNode("nonexistent.invalid.host");

        assertNotNull(node);
        assertEquals("nonexistent.invalid.host", node.getHostname());
        // May or may not be reachable depending on network configuration
        assertNotNull(node.getLatency());
        assertTrue(node.getEstimatedBandwidth() > 0);
        assertNotNull(node.getNetworkType());
        assertNotNull(node.getLastUpdated());
    }

    @Test
    void testDiscoverCorporateHost() {
        // Test with a typical corporate network IP
        NetworkNode node = service.discoverNode("192.168.1.1");

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
    void testNodeCaching() {
        NetworkNode first = service.discoverNode("localhost");
        NetworkNode second = service.discoverNode("localhost");

        assertSame(first, second, "a fresh cached node is returned as is");
    }

    @Test
    void testFindOptimalPath() throws Exception {
        NetworkTopologyService.NetworkPath path = service.findOptimalPath("localhost", "127.0.0.1");

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
    void testPathCaching() throws Exception {
        NetworkTopologyService.NetworkPath first = service.findOptimalPath("localhost", "127.0.0.1");
        NetworkTopologyService.NetworkPath second = service.findOptimalPath("localhost", "127.0.0.1");

        assertSame(first, second, "a fresh cached path is returned as is");
    }

    @Test
    void testGetTransferRecommendations() {
        long transferSize = 100 * 1024 * 1024; // 100MB

        NetworkTopologyService.NetworkRecommendations recommendations =
                service.getTransferRecommendations("localhost", transferSize);

        assertNotNull(recommendations);
        assertTrue(recommendations.getOptimalBufferSize() > 0);
        assertTrue(recommendations.getRecommendedConcurrency() > 0);
        assertNotNull(recommendations.getEstimatedTransferTime());
        assertNotNull(recommendations.getNetworkQuality());
    }

    @Test
    void testGetTransferRecommendationsSmallFile() {
        NetworkTopologyService.NetworkRecommendations recommendations =
                service.getTransferRecommendations("localhost", 1024);

        assertNotNull(recommendations);
        assertTrue(recommendations.getOptimalBufferSize() > 0);
        assertEquals(1, recommendations.getRecommendedConcurrency()); // Small files should use single connection
        assertNotNull(recommendations.getEstimatedTransferTime());
        assertNotNull(recommendations.getNetworkQuality());
    }

    @Test
    void testGetTransferRecommendationsLargeFile() {
        NetworkTopologyService.NetworkRecommendations recommendations =
                service.getTransferRecommendations("localhost", 1024L * 1024 * 1024);

        assertNotNull(recommendations);
        assertTrue(recommendations.getOptimalBufferSize() > 0);
        assertTrue(recommendations.getRecommendedConcurrency() >= 1);
        assertNotNull(recommendations.getEstimatedTransferTime());
        assertNotNull(recommendations.getNetworkQuality());
    }

    @Test
    void testUpdateMetrics() {
        String hostname = "localhost";
        long bytesTransferred = 50 * 1024 * 1024; // 50MB

        service.discoverNode(hostname);
        service.updateMetrics(hostname, bytesTransferred, Duration.ofSeconds(5), true);
        NetworkTopologyService.NetworkRecommendations recommendations =
                service.getTransferRecommendations(hostname, bytesTransferred);

        assertNotNull(recommendations);
        assertTrue(recommendations.getOptimalBufferSize() > 0);
        assertTrue(recommendations.getRecommendedConcurrency() > 0);
    }

    @Test
    void testGetNetworkStatistics() {
        service.discoverNode("localhost");
        service.discoverNode("127.0.0.1");

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
    void testNetworkTypeDetection() {
        assertEquals(NetworkTopologyService.NetworkType.LOCAL_NETWORK, service.discoverNode("localhost").getNetworkType());
        assertEquals(NetworkTopologyService.NetworkType.LOCAL_NETWORK, service.discoverNode("127.0.0.1").getNetworkType());
    }

    @Test
    void testBandwidthEstimation() {
        // Local network should have high bandwidth
        assertTrue(service.discoverNode("localhost").getEstimatedBandwidth() >= 100 * 1024 * 1024);
    }

    @Test
    void testLatencyMeasurement() {
        // Local network should have low latency
        assertTrue(service.discoverNode("localhost").getLatency().toMillis() < 1000);
    }

    @Test
    void testPerformanceScore() {
        NetworkNode localNode = service.discoverNode("localhost");

        double score = localNode.getPerformanceScore();
        assertTrue(score >= 0.0 && score <= 1.0);
        if (localNode.isReachable()) {
            assertTrue(score > 0.5); // Should be better than average
        }
    }

    @Test
    void testTransferStrategySelection() throws Exception {
        NetworkTopologyService.TransferStrategy strategy =
                service.findOptimalPath("localhost", "127.0.0.1").getTransferStrategy();

        assertNotNull(strategy);
        assertTrue(strategy == NetworkTopologyService.TransferStrategy.HIGH_THROUGHPUT ||
                  strategy == NetworkTopologyService.TransferStrategy.HIGH_LATENCY_OPTIMIZED ||
                  strategy == NetworkTopologyService.TransferStrategy.BALANCED);
    }

    @Test
    void testNetworkQualityAssessment() {
        NetworkTopologyService.NetworkQuality quality =
                service.getTransferRecommendations("localhost", 1024 * 1024).getNetworkQuality();

        assertNotNull(quality);
        assertTrue(quality == NetworkTopologyService.NetworkQuality.EXCELLENT ||
                  quality == NetworkTopologyService.NetworkQuality.GOOD ||
                  quality == NetworkTopologyService.NetworkQuality.FAIR ||
                  quality == NetworkTopologyService.NetworkQuality.POOR);
    }

    @Test
    void testRecommendationsForAnUndiscoverableHost() {
        // The former version asserted "useCompression || !useCompression", which is always true.
        NetworkTopologyService.NetworkRecommendations recommendations =
                service.getTransferRecommendations("slow.network.test", 100 * 1024 * 1024);

        assertNotNull(recommendations);
        assertTrue(recommendations.getOptimalBufferSize() > 0);
        assertNotNull(recommendations.getNetworkQuality());
    }
}
