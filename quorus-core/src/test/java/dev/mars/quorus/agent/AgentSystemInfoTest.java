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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for {@link AgentSystemInfo}.
 * Tests constructors, getters/setters, business logic, and JSON serialization.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-27
 * @version 1.0
 */
class AgentSystemInfoTest {

    @Test
    void testDefaultConstructor() {
        AgentSystemInfo systemInfo = new AgentSystemInfo();
        assertNotNull(systemInfo);
    }

    @Test
    void testOperatingSystem() {
        AgentSystemInfo systemInfo = new AgentSystemInfo();
        
        systemInfo.setOperatingSystem("Linux");
        assertEquals("Linux", systemInfo.getOperatingSystem());
        
        systemInfo.setOperatingSystem("Windows 11");
        assertEquals("Windows 11", systemInfo.getOperatingSystem());
    }

    @Test
    void testArchitecture() {
        AgentSystemInfo systemInfo = new AgentSystemInfo();
        
        systemInfo.setArchitecture("x86_64");
        assertEquals("x86_64", systemInfo.getArchitecture());
        
        systemInfo.setArchitecture("aarch64");
        assertEquals("aarch64", systemInfo.getArchitecture());
    }

    @Test
    void testJavaVersion() {
        AgentSystemInfo systemInfo = new AgentSystemInfo();
        
        systemInfo.setJavaVersion("21.0.1");
        assertEquals("21.0.1", systemInfo.getJavaVersion());
    }

    @Test
    void testTotalMemory() {
        AgentSystemInfo systemInfo = new AgentSystemInfo();
        
        systemInfo.setTotalMemory(16L * 1024 * 1024 * 1024); // 16 GB
        assertEquals(16L * 1024 * 1024 * 1024, systemInfo.getTotalMemory());
    }

    @Test
    void testAvailableMemory() {
        AgentSystemInfo systemInfo = new AgentSystemInfo();
        
        systemInfo.setAvailableMemory(8L * 1024 * 1024 * 1024); // 8 GB
        assertEquals(8L * 1024 * 1024 * 1024, systemInfo.getAvailableMemory());
    }

    @Test
    void testTotalDiskSpace() {
        AgentSystemInfo systemInfo = new AgentSystemInfo();
        
        systemInfo.setTotalDiskSpace(1L * 1024 * 1024 * 1024 * 1024); // 1 TB
        assertEquals(1L * 1024 * 1024 * 1024 * 1024, systemInfo.getTotalDiskSpace());
    }

    @Test
    void testAvailableDiskSpace() {
        AgentSystemInfo systemInfo = new AgentSystemInfo();
        
        systemInfo.setAvailableDiskSpace(500L * 1024 * 1024 * 1024); // 500 GB
        assertEquals(500L * 1024 * 1024 * 1024, systemInfo.getAvailableDiskSpace());
    }

    @Test
    void testCpuCores() {
        AgentSystemInfo systemInfo = new AgentSystemInfo();
        
        systemInfo.setCpuCores(8);
        assertEquals(8, systemInfo.getCpuCores());
        
        systemInfo.setCpuCores(16);
        assertEquals(16, systemInfo.getCpuCores());
    }

    @Test
    void testCpuUsage() {
        AgentSystemInfo systemInfo = new AgentSystemInfo();
        
        systemInfo.setCpuUsage(45.5);
        assertEquals(45.5, systemInfo.getCpuUsage(), 0.01);
        
        systemInfo.setCpuUsage(100.0);
        assertEquals(100.0, systemInfo.getCpuUsage(), 0.01);
    }

    @Test
    void testLoadAverage() {
        AgentSystemInfo systemInfo = new AgentSystemInfo();
        
        systemInfo.setLoadAverage(2.5);
        assertEquals(2.5, systemInfo.getLoadAverage(), 0.01);
    }

    @Test
    void testMemoryUsagePercentage() {
        AgentSystemInfo systemInfo = new AgentSystemInfo();
        
        systemInfo.setTotalMemory(16L * 1024 * 1024 * 1024); // 16 GB
        systemInfo.setAvailableMemory(8L * 1024 * 1024 * 1024); // 8 GB
        
        assertEquals(50.0, systemInfo.getMemoryUsagePercentage(), 0.01);
        
        // Test with zero total memory
        systemInfo.setTotalMemory(0);
        assertEquals(0.0, systemInfo.getMemoryUsagePercentage(), 0.01);
    }

    @Test
    void testDiskUsagePercentage() {
        AgentSystemInfo systemInfo = new AgentSystemInfo();
        
        systemInfo.setTotalDiskSpace(1000L * 1024 * 1024 * 1024); // 1000 GB
        systemInfo.setAvailableDiskSpace(250L * 1024 * 1024 * 1024); // 250 GB
        
        assertEquals(75.0, systemInfo.getDiskUsagePercentage(), 0.01);
        
        // Test with zero total disk
        systemInfo.setTotalDiskSpace(0);
        assertEquals(0.0, systemInfo.getDiskUsagePercentage(), 0.01);
    }

    @Test
    void testHasSufficientResources() {
        AgentSystemInfo systemInfo = new AgentSystemInfo();
        
        // Sufficient resources
        systemInfo.setTotalMemory(16L * 1024 * 1024 * 1024);
        systemInfo.setAvailableMemory(8L * 1024 * 1024 * 1024); // 50% usage
        systemInfo.setTotalDiskSpace(1000L * 1024 * 1024 * 1024);
        systemInfo.setAvailableDiskSpace(500L * 1024 * 1024 * 1024); // 50% usage
        systemInfo.setCpuUsage(50.0);
        
        assertTrue(systemInfo.hasSufficientResources());
        
        // High memory usage (91%)
        systemInfo.setAvailableMemory((long) (16L * 1024 * 1024 * 1024 * 0.09));
        assertFalse(systemInfo.hasSufficientResources());
        
        // High disk usage (96%)
        systemInfo.setAvailableMemory(8L * 1024 * 1024 * 1024);
        systemInfo.setAvailableDiskSpace((long) (1000L * 1024 * 1024 * 1024 * 0.04));
        assertFalse(systemInfo.hasSufficientResources());
        
        // High CPU usage (91%)
        systemInfo.setAvailableDiskSpace(500L * 1024 * 1024 * 1024);
        systemInfo.setCpuUsage(91.0);
        assertFalse(systemInfo.hasSufficientResources());
    }

    @Test
    void testGetResourceAvailabilityScore() {
        AgentSystemInfo systemInfo = new AgentSystemInfo();
        
        // Excellent resources (10% usage across the board)
        systemInfo.setTotalMemory(16L * 1024 * 1024 * 1024);
        systemInfo.setAvailableMemory((long) (16L * 1024 * 1024 * 1024 * 0.9)); // 10% usage
        systemInfo.setTotalDiskSpace(1000L * 1024 * 1024 * 1024);
        systemInfo.setAvailableDiskSpace((long) (1000L * 1024 * 1024 * 1024 * 0.9)); // 10% usage
        systemInfo.setCpuUsage(10.0);
        
        double score = systemInfo.getResourceAvailabilityScore();
        assertTrue(score > 0.8, "Excellent resources should have score > 0.8, got: " + score);
        
        // Poor resources (95% usage)
        systemInfo.setAvailableMemory((long) (16L * 1024 * 1024 * 1024 * 0.05));
        systemInfo.setAvailableDiskSpace((long) (1000L * 1024 * 1024 * 1024 * 0.05));
        systemInfo.setCpuUsage(95.0);
        
        score = systemInfo.getResourceAvailabilityScore();
        assertTrue(score < 0.2, "Poor resources should have score < 0.2, got: " + score);
        
        // Medium resources (50% usage)
        systemInfo.setAvailableMemory(8L * 1024 * 1024 * 1024);
        systemInfo.setAvailableDiskSpace(500L * 1024 * 1024 * 1024);
        systemInfo.setCpuUsage(50.0);
        
        score = systemInfo.getResourceAvailabilityScore();
        assertTrue(score > 0.4 && score < 0.6, "Medium resources should have score between 0.4 and 0.6, got: " + score);
    }

    @Test
    void testToString() {
        AgentSystemInfo systemInfo = new AgentSystemInfo();
        systemInfo.setOperatingSystem("Linux");
        systemInfo.setArchitecture("x86_64");
        systemInfo.setJavaVersion("21.0.1");
        systemInfo.setCpuCores(8);
        
        String str = systemInfo.toString();
        assertNotNull(str);
        assertTrue(str.contains("AgentSystemInfo"));
        assertTrue(str.contains("Linux"));
        assertTrue(str.contains("x86_64"));
        assertTrue(str.contains("21.0.1"));
        assertTrue(str.contains("8"));
    }

    @Test
    void testJsonSerialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        AgentSystemInfo systemInfo = new AgentSystemInfo();
        systemInfo.setOperatingSystem("Linux");
        systemInfo.setArchitecture("x86_64");
        systemInfo.setJavaVersion("21.0.1");
        systemInfo.setTotalMemory(16L * 1024 * 1024 * 1024);
        systemInfo.setAvailableMemory(8L * 1024 * 1024 * 1024);
        systemInfo.setTotalDiskSpace(1L * 1024 * 1024 * 1024 * 1024);
        systemInfo.setAvailableDiskSpace(500L * 1024 * 1024 * 1024);
        systemInfo.setCpuCores(8);
        systemInfo.setCpuUsage(45.5);
        systemInfo.setLoadAverage(2.5);
        
        // Serialize to JSON
        String json = mapper.writeValueAsString(systemInfo);
        assertNotNull(json);
        assertTrue(json.contains("Linux"));
        assertTrue(json.contains("x86_64"));
        assertTrue(json.contains("21.0.1"));
        
        // Deserialize from JSON
        AgentSystemInfo deserialized = mapper.readValue(json, AgentSystemInfo.class);
        assertNotNull(deserialized);
        assertEquals("Linux", deserialized.getOperatingSystem());
        assertEquals("x86_64", deserialized.getArchitecture());
        assertEquals("21.0.1", deserialized.getJavaVersion());
        assertEquals(16L * 1024 * 1024 * 1024, deserialized.getTotalMemory());
        assertEquals(8L * 1024 * 1024 * 1024, deserialized.getAvailableMemory());
        assertEquals(1L * 1024 * 1024 * 1024 * 1024, deserialized.getTotalDiskSpace());
        assertEquals(500L * 1024 * 1024 * 1024, deserialized.getAvailableDiskSpace());
        assertEquals(8, deserialized.getCpuCores());
        assertEquals(45.5, deserialized.getCpuUsage(), 0.01);
        assertEquals(2.5, deserialized.getLoadAverage(), 0.01);
    }
}
