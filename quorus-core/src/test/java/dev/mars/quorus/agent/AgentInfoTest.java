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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for AgentInfo.
 */
class AgentInfoTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Test
    void testDefaultConstructor() {
        AgentInfo agentInfo = new AgentInfo();
        
        assertNotNull(agentInfo.getMetadata());
        assertEquals(AgentStatus.REGISTERING, agentInfo.getStatus());
        assertNotNull(agentInfo.getRegistrationTime());
        assertTrue(agentInfo.getMetadata().isEmpty());
    }

    @Test
    void testParameterizedConstructor() {
        AgentInfo agentInfo = new AgentInfo("agent-001", "host1.example.com", "192.168.1.100", 8080);
        
        assertEquals("agent-001", agentInfo.getAgentId());
        assertEquals("host1.example.com", agentInfo.getHostname());
        assertEquals("192.168.1.100", agentInfo.getAddress());
        assertEquals(8080, agentInfo.getPort());
        assertEquals(AgentStatus.REGISTERING, agentInfo.getStatus());
        assertNotNull(agentInfo.getMetadata());
    }

    @Test
    void testSettersAndGetters() {
        AgentInfo agentInfo = new AgentInfo();
        AgentCapabilities capabilities = new AgentCapabilities();
        Instant now = Instant.now();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key1", "value1");

        agentInfo.setAgentId("agent-002");
        agentInfo.setHostname("host2.example.com");
        agentInfo.setAddress("10.0.0.1");
        agentInfo.setPort(9090);
        agentInfo.setCapabilities(capabilities);
        agentInfo.setStatus(AgentStatus.HEALTHY);
        agentInfo.setRegistrationTime(now);
        agentInfo.setLastHeartbeat(now);
        agentInfo.setVersion("1.2.3");
        agentInfo.setRegion("us-east-1");
        agentInfo.setDatacenter("dc1");
        agentInfo.setMetadata(metadata);

        assertEquals("agent-002", agentInfo.getAgentId());
        assertEquals("host2.example.com", agentInfo.getHostname());
        assertEquals("10.0.0.1", agentInfo.getAddress());
        assertEquals(9090, agentInfo.getPort());
        assertEquals(capabilities, agentInfo.getCapabilities());
        assertEquals(AgentStatus.HEALTHY, agentInfo.getStatus());
        assertEquals(now, agentInfo.getRegistrationTime());
        assertEquals(now, agentInfo.getLastHeartbeat());
        assertEquals("1.2.3", agentInfo.getVersion());
        assertEquals("us-east-1", agentInfo.getRegion());
        assertEquals("dc1", agentInfo.getDatacenter());
        assertEquals(metadata, agentInfo.getMetadata());
    }

    @Test
    void testAddMetadata() {
        AgentInfo agentInfo = new AgentInfo();
        
        agentInfo.addMetadata("environment", "production");
        agentInfo.addMetadata("tier", "premium");

        Map<String, String> metadata = agentInfo.getMetadata();
        assertEquals(2, metadata.size());
        assertEquals("production", metadata.get("environment"));
        assertEquals("premium", metadata.get("tier"));
    }

    @Test
    void testEqualsAndHashCode() {
        AgentInfo agent1 = new AgentInfo("agent-001", "host1", "192.168.1.1", 8080);
        AgentInfo agent2 = new AgentInfo("agent-001", "host1", "192.168.1.1", 8080);
        AgentInfo agent3 = new AgentInfo("agent-002", "host2", "192.168.1.2", 8081);

        assertEquals(agent1, agent2);
        assertEquals(agent1.hashCode(), agent2.hashCode());
        assertNotEquals(agent1, agent3);
        assertNotEquals(agent1.hashCode(), agent3.hashCode());
    }

    @Test
    void testToString() {
        AgentInfo agentInfo = new AgentInfo("agent-001", "host1", "192.168.1.1", 8080);
        String toString = agentInfo.toString();
        
        assertNotNull(toString);
        assertTrue(toString.contains("agent-001"));
        assertTrue(toString.contains("host1"));
    }

    @Test
    void testJsonSerialization() throws Exception {
        AgentInfo agentInfo = new AgentInfo("agent-001", "host1", "192.168.1.1", 8080);
        agentInfo.setVersion("1.0.0");
        agentInfo.setRegion("us-west-2");
        agentInfo.setStatus(AgentStatus.HEALTHY);

        String json = objectMapper.writeValueAsString(agentInfo);
        assertNotNull(json);
        assertTrue(json.contains("agent-001"));
        assertTrue(json.contains("1.0.0"));
        assertTrue(json.contains("us-west-2"));
        
        AgentInfo deserialized = objectMapper.readValue(json, AgentInfo.class);
        assertEquals("agent-001", deserialized.getAgentId());
        assertEquals("host1", deserialized.getHostname());
        assertEquals("192.168.1.1", deserialized.getAddress());
        assertEquals(8080, deserialized.getPort());
        assertEquals("1.0.0", deserialized.getVersion());
        assertEquals("us-west-2", deserialized.getRegion());
    }

    @Test
    void testIsHealthy() {
        AgentInfo agentInfo = new AgentInfo();
        
        agentInfo.setStatus(AgentStatus.HEALTHY);
        assertTrue(agentInfo.isHealthy());
        
        agentInfo.setStatus(AgentStatus.ACTIVE);
        assertTrue(agentInfo.isHealthy());
        
        agentInfo.setStatus(AgentStatus.IDLE);
        assertFalse(agentInfo.isHealthy()); // IDLE is available but not healthy
        
        agentInfo.setStatus(AgentStatus.FAILED);
        assertFalse(agentInfo.isHealthy());
        
        agentInfo.setStatus(AgentStatus.UNREACHABLE);
        assertFalse(agentInfo.isHealthy());
    }

    @Test
    void testIsAvailable() {
        AgentInfo agentInfo = new AgentInfo();
        
        agentInfo.setStatus(AgentStatus.HEALTHY);
        assertTrue(agentInfo.isAvailable());
        
        agentInfo.setStatus(AgentStatus.IDLE);
        assertTrue(agentInfo.isAvailable());
        
        agentInfo.setStatus(AgentStatus.OVERLOADED);
        assertFalse(agentInfo.isAvailable());
        
        agentInfo.setStatus(AgentStatus.MAINTENANCE);
        assertFalse(agentInfo.isAvailable());
    }
}
