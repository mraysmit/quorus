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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
/**
 * Description for NetworkNodeTest
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-18
 */

class NetworkNodeTest {
    
    @Test
    void testBuilderWithAllFields() {
        Instant now = Instant.now();
        Duration latency = Duration.ofMillis(50);
        
        NetworkNode node = NetworkNode.builder()
                .hostname("test.example.com")
                .ipAddress("192.168.1.100")
                .reachable(true)
                .latency(latency)
                .estimatedBandwidth(100 * 1024 * 1024) // 100 MB/s
                .networkType(NetworkTopologyService.NetworkType.CORPORATE_NETWORK)
                .lastUpdated(now)
                .build();
        
        assertEquals("test.example.com", node.getHostname());
        assertEquals("192.168.1.100", node.getIpAddress());
        assertTrue(node.isReachable());
        assertEquals(latency, node.getLatency());
        assertEquals(100 * 1024 * 1024, node.getEstimatedBandwidth());
        assertEquals(NetworkTopologyService.NetworkType.CORPORATE_NETWORK, node.getNetworkType());
        assertEquals(now, node.getLastUpdated());
    }
    
    @Test
    void testBuilderWithMinimalFields() {
        NetworkNode node = NetworkNode.builder()
                .hostname("minimal.test")
                .build();
        
        assertEquals("minimal.test", node.getHostname());
        assertNull(node.getIpAddress());
        assertFalse(node.isReachable()); // Default value
        assertEquals(Duration.ofSeconds(1), node.getLatency()); // Default value
        assertEquals(1024 * 1024, node.getEstimatedBandwidth()); // Default value
        assertEquals(NetworkTopologyService.NetworkType.UNKNOWN, node.getNetworkType()); // Default value
        assertNotNull(node.getLastUpdated()); // Should be set to now
    }
    
    @Test
    void testBuilderRequiredFields() {
        // Hostname is required
        assertThrows(NullPointerException.class, () -> {
            NetworkNode.builder().build();
        });
        
        // Latency is required
        assertThrows(NullPointerException.class, () -> {
            NetworkNode.builder()
                    .hostname("test")
                    .latency(null)
                    .build();
        });
        
        // NetworkType is required
        assertThrows(NullPointerException.class, () -> {
            NetworkNode.builder()
                    .hostname("test")
                    .networkType(null)
                    .build();
        });
        
        // LastUpdated is required
        assertThrows(NullPointerException.class, () -> {
            NetworkNode.builder()
                    .hostname("test")
                    .lastUpdated(null)
                    .build();
        });
    }
    
    @Test
    void testIsStale() {
        // Fresh node should not be stale
        NetworkNode freshNode = NetworkNode.builder()
                .hostname("fresh.test")
                .lastUpdated(Instant.now())
                .build();
        
        assertFalse(freshNode.isStale());
        
        // Old node should be stale
        NetworkNode oldNode = NetworkNode.builder()
                .hostname("old.test")
                .lastUpdated(Instant.now().minus(Duration.ofMinutes(10)))
                .build();
        
        assertTrue(oldNode.isStale());
    }
    
    @Test
    void testWithUpdatedBandwidth() {
        Instant originalTime = Instant.now().minus(Duration.ofMinutes(1));
        
        NetworkNode originalNode = NetworkNode.builder()
                .hostname("test.example.com")
                .estimatedBandwidth(50 * 1024 * 1024) // 50 MB/s
                .lastUpdated(originalTime)
                .build();
        
        NetworkNode updatedNode = originalNode.withUpdatedBandwidth(100 * 1024 * 1024); // 100 MB/s
        
        // Original node should be unchanged
        assertEquals(50 * 1024 * 1024, originalNode.getEstimatedBandwidth());
        assertEquals(originalTime, originalNode.getLastUpdated());
        
        // Updated node should have new bandwidth and timestamp
        assertEquals(100 * 1024 * 1024, updatedNode.getEstimatedBandwidth());
        assertTrue(updatedNode.getLastUpdated().isAfter(originalTime));
        
        // Other fields should be the same
        assertEquals(originalNode.getHostname(), updatedNode.getHostname());
        assertEquals(originalNode.getIpAddress(), updatedNode.getIpAddress());
        assertEquals(originalNode.isReachable(), updatedNode.isReachable());
        assertEquals(originalNode.getLatency(), updatedNode.getLatency());
        assertEquals(originalNode.getNetworkType(), updatedNode.getNetworkType());
    }
    
    @Test
    void testGetPerformanceScoreReachableNode() {
        // High performance node
        NetworkNode highPerfNode = NetworkNode.builder()
                .hostname("high.perf")
                .reachable(true)
                .latency(Duration.ofMillis(5)) // Low latency
                .estimatedBandwidth(1024L * 1024 * 1024) // 1 GB/s
                .build();
        
        double highScore = highPerfNode.getPerformanceScore();
        assertTrue(highScore >= 0.0 && highScore <= 1.0);
        assertTrue(highScore > 0.8); // Should be high performance
        
        // Low performance node
        NetworkNode lowPerfNode = NetworkNode.builder()
                .hostname("low.perf")
                .reachable(true)
                .latency(Duration.ofSeconds(2)) // High latency
                .estimatedBandwidth(1024 * 1024) // 1 MB/s
                .build();
        
        double lowScore = lowPerfNode.getPerformanceScore();
        assertTrue(lowScore >= 0.0 && lowScore <= 1.0);
        assertTrue(lowScore < highScore); // Should be lower than high performance
    }
    
    @Test
    void testGetPerformanceScoreUnreachableNode() {
        NetworkNode unreachableNode = NetworkNode.builder()
                .hostname("unreachable.test")
                .reachable(false)
                .latency(Duration.ofMillis(10))
                .estimatedBandwidth(100 * 1024 * 1024)
                .build();
        
        double score = unreachableNode.getPerformanceScore();
        assertEquals(0.0, score); // Unreachable nodes should have 0 score
    }
    
    @Test
    void testToBuilder() {
        Instant now = Instant.now();
        
        NetworkNode originalNode = NetworkNode.builder()
                .hostname("original.test")
                .ipAddress("10.0.0.1")
                .reachable(true)
                .latency(Duration.ofMillis(25))
                .estimatedBandwidth(200 * 1024 * 1024)
                .networkType(NetworkTopologyService.NetworkType.LOCAL_NETWORK)
                .lastUpdated(now)
                .build();
        
        NetworkNode modifiedNode = originalNode.toBuilder()
                .hostname("modified.test")
                .estimatedBandwidth(300 * 1024 * 1024)
                .build();
        
        // Modified fields
        assertEquals("modified.test", modifiedNode.getHostname());
        assertEquals(300 * 1024 * 1024, modifiedNode.getEstimatedBandwidth());
        
        // Unchanged fields
        assertEquals("10.0.0.1", modifiedNode.getIpAddress());
        assertTrue(modifiedNode.isReachable());
        assertEquals(Duration.ofMillis(25), modifiedNode.getLatency());
        assertEquals(NetworkTopologyService.NetworkType.LOCAL_NETWORK, modifiedNode.getNetworkType());
        assertEquals(now, modifiedNode.getLastUpdated());
    }
    
    @Test
    void testEqualsAndHashCode() {
        NetworkNode node1 = NetworkNode.builder()
                .hostname("test.example.com")
                .build();
        
        NetworkNode node2 = NetworkNode.builder()
                .hostname("test.example.com")
                .ipAddress("different.ip")
                .estimatedBandwidth(999999)
                .build();
        
        NetworkNode node3 = NetworkNode.builder()
                .hostname("different.example.com")
                .build();
        
        // Nodes with same hostname should be equal
        assertEquals(node1, node2);
        assertEquals(node1.hashCode(), node2.hashCode());
        
        // Nodes with different hostnames should not be equal
        assertNotEquals(node1, node3);
        assertNotEquals(node1.hashCode(), node3.hashCode());
        
        // Test with null
        assertNotEquals(node1, null);
        
        // Test with different class
        assertNotEquals(node1, "string");
        
        // Test reflexivity
        assertEquals(node1, node1);
    }
    
    @Test
    void testToString() {
        NetworkNode node = NetworkNode.builder()
                .hostname("test.example.com")
                .reachable(true)
                .latency(Duration.ofMillis(50))
                .estimatedBandwidth(100 * 1024 * 1024)
                .networkType(NetworkTopologyService.NetworkType.CORPORATE_NETWORK)
                .build();
        
        String toString = node.toString();
        
        assertNotNull(toString);
        assertTrue(toString.contains("test.example.com"));
        assertTrue(toString.contains("reachable=true"));
        assertTrue(toString.contains("50ms"));
        assertTrue(toString.contains("100MB/s"));
        assertTrue(toString.contains("CORPORATE_NETWORK"));
    }
    
    @Test
    void testNetworkTypes() {
        // Test all network types
        for (NetworkTopologyService.NetworkType type : NetworkTopologyService.NetworkType.values()) {
            NetworkNode node = NetworkNode.builder()
                    .hostname("test." + type.name().toLowerCase())
                    .networkType(type)
                    .build();
            
            assertEquals(type, node.getNetworkType());
        }
    }
    
    @Test
    void testBandwidthRanges() {
        // Test various bandwidth values
        long[] bandwidths = {
                1024,                    // 1 KB/s
                1024 * 1024,            // 1 MB/s
                10 * 1024 * 1024,       // 10 MB/s
                100 * 1024 * 1024,      // 100 MB/s
                1024L * 1024 * 1024     // 1 GB/s
        };
        
        for (long bandwidth : bandwidths) {
            NetworkNode node = NetworkNode.builder()
                    .hostname("test.bandwidth")
                    .estimatedBandwidth(bandwidth)
                    .build();
            
            assertEquals(bandwidth, node.getEstimatedBandwidth());
        }
    }
    
    @Test
    void testLatencyRanges() {
        // Test various latency values
        Duration[] latencies = {
                Duration.ofMillis(1),
                Duration.ofMillis(10),
                Duration.ofMillis(100),
                Duration.ofSeconds(1),
                Duration.ofSeconds(10)
        };
        
        for (Duration latency : latencies) {
            NetworkNode node = NetworkNode.builder()
                    .hostname("test.latency")
                    .latency(latency)
                    .build();
            
            assertEquals(latency, node.getLatency());
        }
    }
}
