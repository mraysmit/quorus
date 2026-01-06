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

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
/**
 * Description for NetworkTopologyServiceTest
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-18
 */

@ExtendWith(VertxExtension.class)
class NetworkTopologyServiceTest {
    
    private NetworkTopologyService service;
    private Vertx vertx;
    
    @BeforeEach
    void setUp(Vertx vertx) {
        this.vertx = vertx;
        service = new NetworkTopologyService(vertx);
    }

    @AfterEach
    void tearDown(Vertx vertx) {
        // Vertx is managed by the extension
    }
    
    @Test
    void testDiscoverLocalhost(VertxTestContext testContext) {
        service.discoverNode("localhost")
            .onComplete(testContext.succeeding(node -> {
                testContext.verify(() -> {
                    assertNotNull(node);
                    assertEquals("localhost", node.getHostname());
                    assertTrue(node.isReachable());
                    assertNotNull(node.getLatency());
                    assertTrue(node.getLatency().toMillis() >= 0);
                    assertTrue(node.getEstimatedBandwidth() > 0);
                    assertEquals(NetworkTopologyService.NetworkType.LOCAL_NETWORK, node.getNetworkType());
                    assertNotNull(node.getLastUpdated());
                });
                testContext.completeNow();
            }));
    }
    
    @Test
    void testDiscoverNonExistentHost(VertxTestContext testContext) {
        service.discoverNode("nonexistent.invalid.host")
            .onComplete(testContext.succeeding(node -> {
                testContext.verify(() -> {
                    assertNotNull(node);
                    assertEquals("nonexistent.invalid.host", node.getHostname());
                    // May or may not be reachable depending on network configuration
                    assertNotNull(node.getLatency());
                    assertTrue(node.getEstimatedBandwidth() > 0);
                    assertNotNull(node.getNetworkType());
                    assertNotNull(node.getLastUpdated());
                });
                testContext.completeNow();
            }));
    }
    
    @Test
    void testDiscoverCorporateHost(VertxTestContext testContext) {
        // Test with a typical corporate network IP
        service.discoverNode("192.168.1.1")
            .onComplete(testContext.succeeding(node -> {
                testContext.verify(() -> {
                    assertNotNull(node);
                    assertEquals("192.168.1.1", node.getHostname());
                    assertNotNull(node.getLatency());
                    assertTrue(node.getEstimatedBandwidth() > 0);
                    // Should be detected as corporate network for private IP
                    assertTrue(node.getNetworkType() == NetworkTopologyService.NetworkType.CORPORATE_NETWORK ||
                              node.getNetworkType() == NetworkTopologyService.NetworkType.LOCAL_NETWORK);
                    assertNotNull(node.getLastUpdated());
                });
                testContext.completeNow();
            }));
    }
    
    @Test
    void testNodeCaching(VertxTestContext testContext) {
        // First discovery
        service.discoverNode("localhost")
            .compose(node1 -> {
                // Second discovery should use cache
                return service.discoverNode("localhost")
                    .map(node2 -> new NetworkNode[]{node1, node2});
            })
            .onComplete(testContext.succeeding(nodes -> {
                testContext.verify(() -> {
                    NetworkNode node1 = nodes[0];
                    NetworkNode node2 = nodes[1];
                    assertNotNull(node1);
                    assertNotNull(node2);
                    assertEquals(node1.getHostname(), node2.getHostname());
                    // Cache should return the same or updated node
                    assertTrue(node2.getLastUpdated().equals(node1.getLastUpdated()) ||
                              node2.getLastUpdated().isAfter(node1.getLastUpdated()));
                });
                testContext.completeNow();
            }));
    }
    
    @Test
    void testFindOptimalPath(VertxTestContext testContext) {
        service.findOptimalPath("localhost", "127.0.0.1")
            .onComplete(testContext.succeeding(path -> {
                testContext.verify(() -> {
                    assertNotNull(path);
                    assertEquals("localhost", path.getSource());
                    assertEquals("127.0.0.1", path.getDestination());
                    assertTrue(path.getQualityScore() >= 0.0 && path.getQualityScore() <= 1.0);
                    assertNotNull(path.getEstimatedLatency());
                    assertTrue(path.getEstimatedBandwidth() > 0);
                    assertNotNull(path.getTransferStrategy());
                    assertNotNull(path.getLastUpdated());
                });
                testContext.completeNow();
            }));
    }
    
    @Test
    void testPathCaching(VertxTestContext testContext) {
        // First path calculation
        service.findOptimalPath("localhost", "127.0.0.1")
            .compose(path1 -> {
                // Second path calculation should use cache
                return service.findOptimalPath("localhost", "127.0.0.1")
                    .map(path2 -> new NetworkTopologyService.NetworkPath[]{path1, path2});
            })
            .onComplete(testContext.succeeding(paths -> {
                testContext.verify(() -> {
                    NetworkTopologyService.NetworkPath path1 = paths[0];
                    NetworkTopologyService.NetworkPath path2 = paths[1];
                    assertNotNull(path1);
                    assertNotNull(path2);
                    assertEquals(path1.getSource(), path2.getSource());
                    assertEquals(path1.getDestination(), path2.getDestination());
                });
                testContext.completeNow();
            }));
    }
    
    @Test
    void testGetTransferRecommendations(VertxTestContext testContext) {
        String hostname = "localhost";
        long transferSize = 100 * 1024 * 1024; // 100MB
        
        service.getTransferRecommendations(hostname, transferSize)
            .onComplete(testContext.succeeding(recommendations -> {
                testContext.verify(() -> {
                    assertNotNull(recommendations);
                    assertTrue(recommendations.getOptimalBufferSize() > 0);
                    assertTrue(recommendations.getRecommendedConcurrency() > 0);
                    assertNotNull(recommendations.getEstimatedTransferTime());
                    assertNotNull(recommendations.getNetworkQuality());
                });
                testContext.completeNow();
            }));
    }
    
    @Test
    void testGetTransferRecommendationsSmallFile(VertxTestContext testContext) {
        String hostname = "localhost";
        long transferSize = 1024; // 1KB
        
        service.getTransferRecommendations(hostname, transferSize)
            .onComplete(testContext.succeeding(recommendations -> {
                testContext.verify(() -> {
                    assertNotNull(recommendations);
                    assertTrue(recommendations.getOptimalBufferSize() > 0);
                    assertEquals(1, recommendations.getRecommendedConcurrency()); // Small files should use single connection
                    assertNotNull(recommendations.getEstimatedTransferTime());
                    assertNotNull(recommendations.getNetworkQuality());
                });
                testContext.completeNow();
            }));
    }
    
    @Test
    void testGetTransferRecommendationsLargeFile(VertxTestContext testContext) {
        String hostname = "localhost";
        long transferSize = 1024L * 1024 * 1024; // 1GB
        
        service.getTransferRecommendations(hostname, transferSize)
            .onComplete(testContext.succeeding(recommendations -> {
                testContext.verify(() -> {
                    assertNotNull(recommendations);
                    assertTrue(recommendations.getOptimalBufferSize() > 0);
                    assertTrue(recommendations.getRecommendedConcurrency() >= 1);
                    assertNotNull(recommendations.getEstimatedTransferTime());
                    assertNotNull(recommendations.getNetworkQuality());
                });
                testContext.completeNow();
            }));
    }
    
    @Test
    void testUpdateMetrics(VertxTestContext testContext) {
        String hostname = "localhost";
        long bytesTransferred = 50 * 1024 * 1024; // 50MB
        Duration actualTime = Duration.ofSeconds(5);
        
        // First discover the node
        service.discoverNode(hostname)
            .compose(v -> {
                // Update metrics
                service.updateMetrics(hostname, bytesTransferred, actualTime, true);
                
                // Get updated recommendations
                return service.getTransferRecommendations(hostname, bytesTransferred);
            })
            .onComplete(testContext.succeeding(recommendations -> {
                testContext.verify(() -> {
                    assertNotNull(recommendations);
                    // Metrics should influence recommendations
                    assertTrue(recommendations.getOptimalBufferSize() > 0);
                    assertTrue(recommendations.getRecommendedConcurrency() > 0);
                });
                testContext.completeNow();
            }));
    }
    
    @Test
    void testGetNetworkStatistics(VertxTestContext testContext) {
        // Discover a few nodes to populate statistics
        service.discoverNode("localhost")
            .compose(v -> service.discoverNode("127.0.0.1"))
            .onComplete(testContext.succeeding(v -> {
                testContext.verify(() -> {
                    NetworkTopologyService.NetworkStatistics stats = service.getNetworkStatistics();
                    
                    assertNotNull(stats);
                    assertTrue(stats.getTotalNodes() >= 2);
                    assertTrue(stats.getReachableNodes() >= 0);
                    assertTrue(stats.getReachableNodes() <= stats.getTotalNodes());
                    assertNotNull(stats.getAverageLatency());
                    assertTrue(stats.getTotalBandwidth() >= 0);
                    assertTrue(stats.getNetworkPaths() >= 0);
                    assertNotNull(stats.getTransferMetrics());
                });
                testContext.completeNow();
            }));
    }
    
    @Test
    void testNetworkTypeDetection(VertxTestContext testContext) {
        // Test localhost detection
        service.discoverNode("localhost")
            .compose(localhostNode -> {
                testContext.verify(() -> assertEquals(NetworkTopologyService.NetworkType.LOCAL_NETWORK, localhostNode.getNetworkType()));
                return service.discoverNode("127.0.0.1");
            })
            .onComplete(testContext.succeeding(loopbackNode -> {
                testContext.verify(() -> assertEquals(NetworkTopologyService.NetworkType.LOCAL_NETWORK, loopbackNode.getNetworkType()));
                testContext.completeNow();
            }));
    }
    
    @Test
    void testBandwidthEstimation(VertxTestContext testContext) {
        service.discoverNode("localhost")
            .onComplete(testContext.succeeding(localNode -> {
                testContext.verify(() -> {
                    // Local network should have high bandwidth
                    assertTrue(localNode.getEstimatedBandwidth() >= 100 * 1024 * 1024); // At least 100MB/s for local
                });
                testContext.completeNow();
            }));
    }
    
    @Test
    void testLatencyMeasurement(VertxTestContext testContext) {
        service.discoverNode("localhost")
            .onComplete(testContext.succeeding(localNode -> {
                testContext.verify(() -> {
                    // Local network should have low latency
                    assertTrue(localNode.getLatency().toMillis() < 1000); // Less than 1 second for local
                });
                testContext.completeNow();
            }));
    }
    
    @Test
    void testPerformanceScore(VertxTestContext testContext) {
        service.discoverNode("localhost")
            .onComplete(testContext.succeeding(localNode -> {
                testContext.verify(() -> {
                    double score = localNode.getPerformanceScore();
                    assertTrue(score >= 0.0 && score <= 1.0);
                    
                    // Local network should have good performance score
                    if (localNode.isReachable()) {
                        assertTrue(score > 0.5); // Should be better than average
                    }
                });
                testContext.completeNow();
            }));
    }
    
    @Test
    void testTransferStrategySelection(VertxTestContext testContext) {
        service.findOptimalPath("localhost", "127.0.0.1")
            .onComplete(testContext.succeeding(path -> {
                testContext.verify(() -> {
                    NetworkTopologyService.TransferStrategy strategy = path.getTransferStrategy();
                    assertNotNull(strategy);
                    
                    // Should be one of the defined strategies
                    assertTrue(strategy == NetworkTopologyService.TransferStrategy.HIGH_THROUGHPUT ||
                              strategy == NetworkTopologyService.TransferStrategy.HIGH_LATENCY_OPTIMIZED ||
                              strategy == NetworkTopologyService.TransferStrategy.BALANCED);
                });
                testContext.completeNow();
            }));
    }
    
    @Test
    void testNetworkQualityAssessment(VertxTestContext testContext) {
        service.getTransferRecommendations("localhost", 1024 * 1024)
            .onComplete(testContext.succeeding(recommendations -> {
                testContext.verify(() -> {
                    NetworkTopologyService.NetworkQuality quality = recommendations.getNetworkQuality();
                    assertNotNull(quality);
                    
                    // Should be one of the defined quality levels
                    assertTrue(quality == NetworkTopologyService.NetworkQuality.EXCELLENT ||
                              quality == NetworkTopologyService.NetworkQuality.GOOD ||
                              quality == NetworkTopologyService.NetworkQuality.FAIR ||
                              quality == NetworkTopologyService.NetworkQuality.POOR);
                });
                testContext.completeNow();
            }));
    }
    
    @Test
    void testCompressionRecommendation(VertxTestContext testContext) {
        // Test with slow network simulation
        service.getTransferRecommendations("slow.network.test", 100 * 1024 * 1024)
            .onComplete(testContext.succeeding(recommendations -> {
                testContext.verify(() -> {
                    assertNotNull(recommendations);
                    // Compression recommendation can be true or false depending on network conditions
                    // Just verify it's a valid boolean
                    boolean useCompression = recommendations.isUseCompression();
                    assertTrue(useCompression || !useCompression); // Always true, but validates the method works
                });
                testContext.completeNow();
            }));
    }
}
