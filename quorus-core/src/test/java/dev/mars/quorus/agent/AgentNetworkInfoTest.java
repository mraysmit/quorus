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

package dev.mars.quorus.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for {@link AgentNetworkInfo}.
 * Tests constructors, getters/setters, business logic, and JSON serialization.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-27
 * @version 1.0
 */
class AgentNetworkInfoTest {

    @Test
    void testDefaultConstructor() {
        AgentNetworkInfo networkInfo = new AgentNetworkInfo();
        assertNotNull(networkInfo);
    }

    @Test
    void testPublicIpAddress() {
        AgentNetworkInfo networkInfo = new AgentNetworkInfo();
        
        networkInfo.setPublicIpAddress("203.0.113.42");
        assertEquals("203.0.113.42", networkInfo.getPublicIpAddress());
    }

    @Test
    void testPrivateIpAddress() {
        AgentNetworkInfo networkInfo = new AgentNetworkInfo();
        
        networkInfo.setPrivateIpAddress("192.168.1.100");
        assertEquals("192.168.1.100", networkInfo.getPrivateIpAddress());
    }

    @Test
    void testNetworkInterfaces() {
        AgentNetworkInfo networkInfo = new AgentNetworkInfo();
        
        List<String> interfaces = Arrays.asList("eth0", "eth1", "wlan0");
        networkInfo.setNetworkInterfaces(interfaces);
        
        assertEquals(3, networkInfo.getNetworkInterfaces().size());
        assertTrue(networkInfo.getNetworkInterfaces().contains("eth0"));
        assertTrue(networkInfo.getNetworkInterfaces().contains("wlan0"));
    }

    @Test
    void testBandwidthCapacity() {
        AgentNetworkInfo networkInfo = new AgentNetworkInfo();
        
        networkInfo.setBandwidthCapacity(125_000_000L); // 1 Gbps
        assertEquals(125_000_000L, networkInfo.getBandwidthCapacity());
    }

    @Test
    void testCurrentBandwidthUsage() {
        AgentNetworkInfo networkInfo = new AgentNetworkInfo();
        
        networkInfo.setCurrentBandwidthUsage(50_000_000L); // 400 Mbps
        assertEquals(50_000_000L, networkInfo.getCurrentBandwidthUsage());
    }

    @Test
    void testLatencyMs() {
        AgentNetworkInfo networkInfo = new AgentNetworkInfo();
        
        networkInfo.setLatencyMs(25.5);
        assertEquals(25.5, networkInfo.getLatencyMs(), 0.01);
    }

    @Test
    void testPacketLossPercentage() {
        AgentNetworkInfo networkInfo = new AgentNetworkInfo();
        
        networkInfo.setPacketLossPercentage(1.5);
        assertEquals(1.5, networkInfo.getPacketLossPercentage(), 0.01);
    }

    @Test
    void testConnectionType() {
        AgentNetworkInfo networkInfo = new AgentNetworkInfo();
        
        networkInfo.setConnectionType("ethernet");
        assertEquals("ethernet", networkInfo.getConnectionType());
        
        networkInfo.setConnectionType("wifi");
        assertEquals("wifi", networkInfo.getConnectionType());
    }

    @Test
    void testNatTraversal() {
        AgentNetworkInfo networkInfo = new AgentNetworkInfo();
        
        networkInfo.setNatTraversal(true);
        assertTrue(networkInfo.isNatTraversal());
        
        networkInfo.setNatTraversal(false);
        assertFalse(networkInfo.isNatTraversal());
    }

    @Test
    void testFirewallPorts() {
        AgentNetworkInfo networkInfo = new AgentNetworkInfo();
        
        List<Integer> ports = Arrays.asList(8080, 8443, 9000);
        networkInfo.setFirewallPorts(ports);
        
        assertEquals(3, networkInfo.getFirewallPorts().size());
        assertTrue(networkInfo.getFirewallPorts().contains(8080));
        assertTrue(networkInfo.getFirewallPorts().contains(8443));
    }

    @Test
    void testBandwidthUtilizationPercentage() {
        AgentNetworkInfo networkInfo = new AgentNetworkInfo();
        
        networkInfo.setBandwidthCapacity(100_000_000L); // 100 MB/s
        networkInfo.setCurrentBandwidthUsage(50_000_000L); // 50 MB/s
        
        assertEquals(50.0, networkInfo.getBandwidthUtilizationPercentage(), 0.01);
        
        // Test with zero capacity
        networkInfo.setBandwidthCapacity(0);
        assertEquals(0.0, networkInfo.getBandwidthUtilizationPercentage(), 0.01);
    }

    @Test
    void testAvailableBandwidth() {
        AgentNetworkInfo networkInfo = new AgentNetworkInfo();
        
        networkInfo.setBandwidthCapacity(100_000_000L); // 100 MB/s
        networkInfo.setCurrentBandwidthUsage(30_000_000L); // 30 MB/s
        
        assertEquals(70_000_000L, networkInfo.getAvailableBandwidth());
        
        // Test when usage exceeds capacity
        networkInfo.setCurrentBandwidthUsage(110_000_000L);
        assertEquals(0L, networkInfo.getAvailableBandwidth());
    }

    @Test
    void testIsNetworkHealthy() {
        AgentNetworkInfo networkInfo = new AgentNetworkInfo();
        
        // Healthy network
        networkInfo.setLatencyMs(50.0);
        networkInfo.setPacketLossPercentage(1.0);
        networkInfo.setBandwidthCapacity(100_000_000L);
        networkInfo.setCurrentBandwidthUsage(50_000_000L);
        
        assertTrue(networkInfo.isNetworkHealthy());
        
        // High latency
        networkInfo.setLatencyMs(1500.0);
        assertFalse(networkInfo.isNetworkHealthy());
        
        // High packet loss
        networkInfo.setLatencyMs(50.0);
        networkInfo.setPacketLossPercentage(10.0);
        assertFalse(networkInfo.isNetworkHealthy());
        
        // High bandwidth utilization
        networkInfo.setPacketLossPercentage(1.0);
        networkInfo.setCurrentBandwidthUsage(95_000_000L);
        assertFalse(networkInfo.isNetworkHealthy());
    }

    @Test
    void testGetNetworkQualityScore() {
        AgentNetworkInfo networkInfo = new AgentNetworkInfo();
        
        // Excellent network
        networkInfo.setLatencyMs(10.0);
        networkInfo.setPacketLossPercentage(0.1);
        networkInfo.setBandwidthCapacity(100_000_000L);
        networkInfo.setCurrentBandwidthUsage(10_000_000L);
        
        double score = networkInfo.getNetworkQualityScore();
        assertTrue(score > 0.9, "Excellent network should have score > 0.9, got: " + score);
        
        // Poor network
        networkInfo.setLatencyMs(900.0);
        networkInfo.setPacketLossPercentage(15.0);
        networkInfo.setCurrentBandwidthUsage(95_000_000L);
        
        score = networkInfo.getNetworkQualityScore();
        assertTrue(score < 0.35, "Poor network should have score < 0.35, got: " + score);
        
        // Medium network
        networkInfo.setLatencyMs(200.0);
        networkInfo.setPacketLossPercentage(2.0);
        networkInfo.setCurrentBandwidthUsage(50_000_000L);
        
        score = networkInfo.getNetworkQualityScore();
        assertTrue(score > 0.4 && score < 0.9, "Medium network should have score between 0.4 and 0.9, got: " + score);
    }

    @Test
    void testToString() {
        AgentNetworkInfo networkInfo = new AgentNetworkInfo();
        networkInfo.setPublicIpAddress("203.0.113.42");
        networkInfo.setPrivateIpAddress("192.168.1.100");
        networkInfo.setConnectionType("ethernet");
        
        String str = networkInfo.toString();
        assertNotNull(str);
        assertTrue(str.contains("AgentNetworkInfo"));
        assertTrue(str.contains("203.0.113.42"));
        assertTrue(str.contains("192.168.1.100"));
        assertTrue(str.contains("ethernet"));
    }

    @Test
    void testJsonSerialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        AgentNetworkInfo networkInfo = new AgentNetworkInfo();
        networkInfo.setPublicIpAddress("203.0.113.42");
        networkInfo.setPrivateIpAddress("192.168.1.100");
        networkInfo.setNetworkInterfaces(Arrays.asList("eth0", "eth1"));
        networkInfo.setBandwidthCapacity(125_000_000L);
        networkInfo.setCurrentBandwidthUsage(50_000_000L);
        networkInfo.setLatencyMs(25.5);
        networkInfo.setPacketLossPercentage(1.2);
        networkInfo.setConnectionType("ethernet");
        networkInfo.setNatTraversal(false);
        networkInfo.setFirewallPorts(Arrays.asList(8080, 8443));
        
        // Serialize to JSON
        String json = mapper.writeValueAsString(networkInfo);
        assertNotNull(json);
        assertTrue(json.contains("203.0.113.42"));
        assertTrue(json.contains("192.168.1.100"));
        assertTrue(json.contains("ethernet"));
        
        // Deserialize from JSON
        AgentNetworkInfo deserialized = mapper.readValue(json, AgentNetworkInfo.class);
        assertNotNull(deserialized);
        assertEquals("203.0.113.42", deserialized.getPublicIpAddress());
        assertEquals("192.168.1.100", deserialized.getPrivateIpAddress());
        assertEquals(2, deserialized.getNetworkInterfaces().size());
        assertEquals(125_000_000L, deserialized.getBandwidthCapacity());
        assertEquals(50_000_000L, deserialized.getCurrentBandwidthUsage());
        assertEquals(25.5, deserialized.getLatencyMs(), 0.01);
        assertEquals(1.2, deserialized.getPacketLossPercentage(), 0.01);
        assertEquals("ethernet", deserialized.getConnectionType());
        assertFalse(deserialized.isNatTraversal());
        assertEquals(2, deserialized.getFirewallPorts().size());
    }
}
