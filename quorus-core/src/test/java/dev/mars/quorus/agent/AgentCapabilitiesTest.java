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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for {@link AgentCapabilities}.
 * Tests constructors, getters/setters, business logic, and JSON serialization.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-27
 * @version 1.0
 */
class AgentCapabilitiesTest {

    @Test
    void testDefaultConstructor() {
        AgentCapabilities capabilities = new AgentCapabilities();
        
        assertNotNull(capabilities.getSupportedProtocols());
        assertTrue(capabilities.getSupportedProtocols().isEmpty());
        assertNotNull(capabilities.getAvailableRegions());
        assertTrue(capabilities.getAvailableRegions().isEmpty());
        assertNotNull(capabilities.getSupportedCompressionTypes());
        assertTrue(capabilities.getSupportedCompressionTypes().isEmpty());
        assertNotNull(capabilities.getSupportedEncryptionTypes());
        assertTrue(capabilities.getSupportedEncryptionTypes().isEmpty());
        assertNotNull(capabilities.getCustomCapabilities());
        assertTrue(capabilities.getCustomCapabilities().isEmpty());
        
        // Default values
        assertEquals(10, capabilities.getMaxConcurrentTransfers());
        assertEquals(Long.MAX_VALUE, capabilities.getMaxTransferSize());
        assertEquals(Long.MAX_VALUE, capabilities.getMaxBandwidth());
    }

    @Test
    void testSupportedProtocols() {
        AgentCapabilities capabilities = new AgentCapabilities();
        
        Set<String> protocols = new HashSet<>();
        protocols.add("HTTP");
        protocols.add("FTP");
        protocols.add("SMB");
        
        capabilities.setSupportedProtocols(protocols);
        assertEquals(3, capabilities.getSupportedProtocols().size());
        assertTrue(capabilities.getSupportedProtocols().contains("HTTP"));
        assertTrue(capabilities.getSupportedProtocols().contains("FTP"));
        assertTrue(capabilities.getSupportedProtocols().contains("SMB"));
    }

    @Test
    void testAddSupportedProtocol() {
        AgentCapabilities capabilities = new AgentCapabilities();
        
        capabilities.addSupportedProtocol("HTTP");
        capabilities.addSupportedProtocol("S3");
        
        assertEquals(2, capabilities.getSupportedProtocols().size());
        assertTrue(capabilities.supportsProtocol("HTTP"));
        assertTrue(capabilities.supportsProtocol("S3"));
        assertFalse(capabilities.supportsProtocol("FTP"));
    }

    @Test
    void testSupportsProtocol() {
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.addSupportedProtocol("HTTP");
        capabilities.addSupportedProtocol("SFTP");
        
        assertTrue(capabilities.supportsProtocol("HTTP"));
        assertTrue(capabilities.supportsProtocol("SFTP"));
        assertFalse(capabilities.supportsProtocol("FTP"));
        assertFalse(capabilities.supportsProtocol("S3"));
    }

    @Test
    void testMaxConcurrentTransfers() {
        AgentCapabilities capabilities = new AgentCapabilities();
        
        capabilities.setMaxConcurrentTransfers(50);
        assertEquals(50, capabilities.getMaxConcurrentTransfers());
        
        capabilities.setMaxConcurrentTransfers(1);
        assertEquals(1, capabilities.getMaxConcurrentTransfers());
    }

    @Test
    void testMaxTransferSize() {
        AgentCapabilities capabilities = new AgentCapabilities();
        
        capabilities.setMaxTransferSize(1024L * 1024 * 1024 * 10); // 10 GB
        assertEquals(1024L * 1024 * 1024 * 10, capabilities.getMaxTransferSize());
    }

    @Test
    void testMaxBandwidth() {
        AgentCapabilities capabilities = new AgentCapabilities();
        
        capabilities.setMaxBandwidth(125_000_000L); // 1 Gbps in bytes/sec
        assertEquals(125_000_000L, capabilities.getMaxBandwidth());
    }

    @Test
    void testAvailableRegions() {
        AgentCapabilities capabilities = new AgentCapabilities();
        
        Set<String> regions = new HashSet<>();
        regions.add("us-east-1");
        regions.add("us-west-2");
        regions.add("eu-central-1");
        
        capabilities.setAvailableRegions(regions);
        assertEquals(3, capabilities.getAvailableRegions().size());
        assertTrue(capabilities.getAvailableRegions().contains("us-east-1"));
    }

    @Test
    void testAddAvailableRegion() {
        AgentCapabilities capabilities = new AgentCapabilities();
        
        capabilities.addAvailableRegion("us-east-1");
        capabilities.addAvailableRegion("eu-west-1");
        
        assertEquals(2, capabilities.getAvailableRegions().size());
        assertTrue(capabilities.isAvailableInRegion("us-east-1"));
        assertTrue(capabilities.isAvailableInRegion("eu-west-1"));
    }

    @Test
    void testCompressionTypes() {
        AgentCapabilities capabilities = new AgentCapabilities();
        
        Set<String> compressionTypes = new HashSet<>();
        compressionTypes.add("gzip");
        compressionTypes.add("lz4");
        
        capabilities.setSupportedCompressionTypes(compressionTypes);
        assertEquals(2, capabilities.getSupportedCompressionTypes().size());
        assertTrue(capabilities.getSupportedCompressionTypes().contains("gzip"));
    }

    @Test
    void testEncryptionTypes() {
        AgentCapabilities capabilities = new AgentCapabilities();
        
        Set<String> encryptionTypes = new HashSet<>();
        encryptionTypes.add("AES-256");
        encryptionTypes.add("TLS");
        
        capabilities.setSupportedEncryptionTypes(encryptionTypes);
        assertEquals(2, capabilities.getSupportedEncryptionTypes().size());
        assertTrue(capabilities.getSupportedEncryptionTypes().contains("AES-256"));
    }

    @Test
    void testCustomCapabilities() {
        AgentCapabilities capabilities = new AgentCapabilities();
        
        Map<String, Object> custom = new HashMap<>();
        custom.put("feature1", true);
        custom.put("threshold", 42);
        
        capabilities.setCustomCapabilities(custom);
        assertEquals(2, capabilities.getCustomCapabilities().size());
        assertEquals(true, capabilities.getCustomCapabilities().get("feature1"));
        assertEquals(42, capabilities.getCustomCapabilities().get("threshold"));
    }

    @Test
    void testAddCustomCapability() {
        AgentCapabilities capabilities = new AgentCapabilities();
        
        capabilities.addCustomCapability("gpu-enabled", true);
        capabilities.addCustomCapability("max-threads", 16);
        
        assertEquals(2, capabilities.getCustomCapabilities().size());
        assertEquals(true, capabilities.getCustomCapabilities().get("gpu-enabled"));
        assertEquals(16, capabilities.getCustomCapabilities().get("max-threads"));
    }

    @Test
    void testSystemInfo() {
        AgentCapabilities capabilities = new AgentCapabilities();
        AgentSystemInfo systemInfo = new AgentSystemInfo();
        systemInfo.setCpuCores(8);
        
        capabilities.setSystemInfo(systemInfo);
        assertNotNull(capabilities.getSystemInfo());
        assertEquals(8, capabilities.getSystemInfo().getCpuCores());
    }

    @Test
    void testNetworkInfo() {
        AgentCapabilities capabilities = new AgentCapabilities();
        AgentNetworkInfo networkInfo = new AgentNetworkInfo();
        networkInfo.setPublicIpAddress("203.0.113.42");
        
        capabilities.setNetworkInfo(networkInfo);
        assertNotNull(capabilities.getNetworkInfo());
        assertEquals("203.0.113.42", capabilities.getNetworkInfo().getPublicIpAddress());
    }

    @Test
    void testCanHandleTransferSize() {
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setMaxTransferSize(1024L * 1024 * 100); // 100 MB
        
        assertTrue(capabilities.canHandleTransferSize(1024L * 1024 * 50)); // 50 MB - within limit
        assertTrue(capabilities.canHandleTransferSize(1024L * 1024 * 100)); // 100 MB - at limit
        assertFalse(capabilities.canHandleTransferSize(1024L * 1024 * 101)); // 101 MB - exceeds limit
    }

    @Test
    void testIsAvailableInRegion() {
        AgentCapabilities capabilities = new AgentCapabilities();
        
        // Empty regions means available everywhere
        assertTrue(capabilities.isAvailableInRegion("us-east-1"));
        assertTrue(capabilities.isAvailableInRegion("eu-west-1"));
        
        // Add specific regions
        capabilities.addAvailableRegion("us-east-1");
        capabilities.addAvailableRegion("us-west-2");
        
        assertTrue(capabilities.isAvailableInRegion("us-east-1"));
        assertTrue(capabilities.isAvailableInRegion("us-west-2"));
        assertFalse(capabilities.isAvailableInRegion("eu-west-1"));
    }

    @Test
    void testCalculateCompatibilityScore() {
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.addSupportedProtocol("HTTP");
        capabilities.setMaxTransferSize(1024L * 1024 * 1024); // 1 GB
        capabilities.addAvailableRegion("us-east-1");
        
        // Perfect match: protocol supported, size acceptable, region available
        double perfectScore = capabilities.calculateCompatibilityScore("HTTP", 1024L * 1024 * 500, "us-east-1");
        assertEquals(1.0, perfectScore, 0.01);
        
        // Protocol and region match, size too large (40% + 30% = 70%)
        double partialScore1 = capabilities.calculateCompatibilityScore("HTTP", 1024L * 1024 * 2000, "us-east-1");
        assertEquals(0.7, partialScore1, 0.01);
        
        // Only region matches (30%)
        double partialScore2 = capabilities.calculateCompatibilityScore("FTP", 1024L * 1024 * 2000, "us-east-1");
        assertEquals(0.3, partialScore2, 0.01);
        
        // No matches
        double noMatch = capabilities.calculateCompatibilityScore("FTP", 1024L * 1024 * 2000, "eu-west-1");
        assertEquals(0.0, noMatch, 0.01);
    }

    @Test
    void testToString() {
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.addSupportedProtocol("HTTP");
        capabilities.setMaxConcurrentTransfers(20);
        capabilities.addAvailableRegion("us-east-1");
        
        String str = capabilities.toString();
        assertNotNull(str);
        assertTrue(str.contains("AgentCapabilities"));
        assertTrue(str.contains("HTTP"));
        assertTrue(str.contains("20"));
        assertTrue(str.contains("us-east-1"));
    }

    @Test
    void testJsonSerialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.addSupportedProtocol("HTTP");
        capabilities.addSupportedProtocol("S3");
        capabilities.setMaxConcurrentTransfers(25);
        capabilities.setMaxTransferSize(1024L * 1024 * 1024 * 5); // 5 GB
        capabilities.setMaxBandwidth(125_000_000L);
        capabilities.addAvailableRegion("us-east-1");
        capabilities.addAvailableRegion("eu-west-1");
        capabilities.addCustomCapability("gpu", true);
        
        // Serialize to JSON
        String json = mapper.writeValueAsString(capabilities);
        assertNotNull(json);
        assertTrue(json.contains("HTTP"));
        assertTrue(json.contains("S3"));
        assertTrue(json.contains("25"));
        
        // Deserialize from JSON
        AgentCapabilities deserialized = mapper.readValue(json, AgentCapabilities.class);
        assertNotNull(deserialized);
        assertEquals(2, deserialized.getSupportedProtocols().size());
        assertTrue(deserialized.supportsProtocol("HTTP"));
        assertTrue(deserialized.supportsProtocol("S3"));
        assertEquals(25, deserialized.getMaxConcurrentTransfers());
        assertEquals(1024L * 1024 * 1024 * 5, deserialized.getMaxTransferSize());
        assertEquals(125_000_000L, deserialized.getMaxBandwidth());
        assertEquals(2, deserialized.getAvailableRegions().size());
        assertEquals(true, deserialized.getCustomCapabilities().get("gpu"));
    }

    @Test
    void testNullProtocolsHandling() {
        AgentCapabilities capabilities = new AgentCapabilities();
        
        capabilities.setSupportedProtocols(null);
        assertNotNull(capabilities.getSupportedProtocols());
        assertTrue(capabilities.getSupportedProtocols().isEmpty());
    }

    @Test
    void testNullRegionsHandling() {
        AgentCapabilities capabilities = new AgentCapabilities();
        
        capabilities.setAvailableRegions(null);
        assertNotNull(capabilities.getAvailableRegions());
        assertTrue(capabilities.getAvailableRegions().isEmpty());
    }

    @Test
    void testNullCompressionTypesHandling() {
        AgentCapabilities capabilities = new AgentCapabilities();
        
        capabilities.setSupportedCompressionTypes(null);
        assertNotNull(capabilities.getSupportedCompressionTypes());
        assertTrue(capabilities.getSupportedCompressionTypes().isEmpty());
    }

    @Test
    void testNullEncryptionTypesHandling() {
        AgentCapabilities capabilities = new AgentCapabilities();
        
        capabilities.setSupportedEncryptionTypes(null);
        assertNotNull(capabilities.getSupportedEncryptionTypes());
        assertTrue(capabilities.getSupportedEncryptionTypes().isEmpty());
    }

    @Test
    void testNullCustomCapabilitiesHandling() {
        AgentCapabilities capabilities = new AgentCapabilities();
        
        capabilities.setCustomCapabilities(null);
        assertNotNull(capabilities.getCustomCapabilities());
        assertTrue(capabilities.getCustomCapabilities().isEmpty());
    }
}
