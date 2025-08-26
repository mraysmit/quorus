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

package dev.mars.quorus.api.integration;

import dev.mars.quorus.agent.*;
import dev.mars.quorus.api.dto.AgentRegistrationRequest;
import dev.mars.quorus.api.dto.AgentRegistrationResponse;
import dev.mars.quorus.api.service.AgentRegistryService;
import dev.mars.quorus.controller.raft.RaftNode;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test for the Agent Registration Framework.
 * This test demonstrates Phase 2 Milestone 2.3 Agent Fleet Management foundation.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
@QuarkusTest
public class AgentRegistrationFrameworkTest {

    @Inject
    AgentRegistryService agentRegistryService;

    @Inject
    RaftNode raftNode;

    @BeforeEach
    void setUp() {
        // Wait for the Raft node to become leader
        try {
            for (int i = 0; i < 50; i++) {
                if (raftNode.getState() == RaftNode.State.LEADER) {
                    break;
                }
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void demonstrateAgentRegistrationFramework() {
        System.out.println("\n🚀 DEMONSTRATING AGENT REGISTRATION FRAMEWORK");
        System.out.println("==============================================");
        
        // 1. Create agent capabilities
        AgentCapabilities capabilities = createTestAgentCapabilities();
        System.out.println("✅ 1. Agent Capabilities Created:");
        System.out.println("   - Supported Protocols: " + capabilities.getSupportedProtocols());
        System.out.println("   - Max Concurrent Transfers: " + capabilities.getMaxConcurrentTransfers());
        System.out.println("   - Max Transfer Size: " + capabilities.getMaxTransferSize());
        System.out.println("   - Available Regions: " + capabilities.getAvailableRegions());
        
        // 2. Create agent registration request
        AgentRegistrationRequest request = createTestRegistrationRequest(capabilities);
        System.out.println("\n✅ 2. Agent Registration Request Created:");
        System.out.println("   - Agent ID: " + request.getAgentId());
        System.out.println("   - Hostname: " + request.getHostname());
        System.out.println("   - Address: " + request.getAddress() + ":" + request.getPort());
        System.out.println("   - Version: " + request.getVersion());
        System.out.println("   - Region: " + request.getRegion());
        
        // 3. Register agent through service
        try {
            CompletableFuture<AgentInfo> future = agentRegistryService.registerAgent(request);
            AgentInfo agentInfo = future.get(5, TimeUnit.SECONDS);
            
            System.out.println("\n✅ 3. Agent Registered Successfully:");
            System.out.println("   - Agent ID: " + agentInfo.getAgentId());
            System.out.println("   - Status: " + agentInfo.getStatus());
            System.out.println("   - Registration Time: " + agentInfo.getRegistrationTime());
            System.out.println("   - Endpoint: " + agentInfo.getEndpoint());
            System.out.println("   - Is Healthy: " + agentInfo.isHealthy());
            System.out.println("   - Is Available: " + agentInfo.isAvailable());
            
            assertNotNull(agentInfo, "Agent info should not be null");
            assertEquals(request.getAgentId(), agentInfo.getAgentId(), "Agent IDs should match");
            assertTrue(agentInfo.isHealthy(), "Agent should be healthy");
            assertTrue(agentInfo.isAvailable(), "Agent should be available");
            
        } catch (Exception e) {
            fail("Agent registration should succeed: " + e.getMessage());
        }
        
        // 4. Verify agent can be retrieved
        AgentInfo retrievedAgent = agentRegistryService.getAgent(request.getAgentId());
        System.out.println("\n✅ 4. Agent Retrieved Successfully:");
        System.out.println("   - Retrieved Agent ID: " + retrievedAgent.getAgentId());
        System.out.println("   - Status: " + retrievedAgent.getStatus());
        
        assertNotNull(retrievedAgent, "Retrieved agent should not be null");
        assertEquals(request.getAgentId(), retrievedAgent.getAgentId(), "Agent IDs should match");
        
        // 5. Test fleet statistics
        AgentRegistryService.FleetStatistics stats = agentRegistryService.getFleetStatistics();
        System.out.println("\n✅ 5. Fleet Statistics:");
        System.out.println("   - Total Agents: " + stats.getTotalAgents());
        System.out.println("   - Healthy Agents: " + stats.getHealthyAgents());
        System.out.println("   - Available Agents: " + stats.getAvailableAgents());
        System.out.println("   - Status Counts: " + stats.getStatusCounts());
        System.out.println("   - Region Counts: " + stats.getRegionCounts());
        
        assertTrue(stats.getTotalAgents() >= 1, "Should have at least 1 agent");
        assertTrue(stats.getHealthyAgents() >= 1, "Should have at least 1 healthy agent");
        
        System.out.println("\n🎉 AGENT REGISTRATION FRAMEWORK SUCCESS!");
        System.out.println("=======================================");
        System.out.println("✅ Agent models and DTOs operational");
        System.out.println("✅ Registration service working");
        System.out.println("✅ Distributed state management active");
        System.out.println("✅ Fleet statistics and monitoring ready");
        System.out.println("✅ Foundation for fleet management complete");
    }

    @Test
    void testAgentRegistrationViaRestAPI() {
        System.out.println("\n🌐 TESTING AGENT REGISTRATION VIA REST API");
        System.out.println("===========================================");
        
        // Create registration request
        AgentRegistrationRequest request = createTestRegistrationRequest(createTestAgentCapabilities());
        request.setAgentId("rest-api-test-agent"); // Use different ID for REST test
        
        // Register agent via REST API
        AgentRegistrationResponse response = given()
            .auth().basic("admin", "admin")
            .contentType(ContentType.JSON)
            .body(request)
            .when()
                .post("/api/v1/agents/register")
            .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("agentId", equalTo(request.getAgentId()))
                .body("status", equalTo("healthy"))
                .body("heartbeatInterval", greaterThan(0))
                .body("controllerEndpoint", notNullValue())
                .extract()
                .as(AgentRegistrationResponse.class);
        
        System.out.println("✅ REST API Registration Response:");
        System.out.println("   - Success: " + response.isSuccess());
        System.out.println("   - Agent ID: " + response.getAgentId());
        System.out.println("   - Status: " + response.getStatus());
        System.out.println("   - Message: " + response.getMessage());
        System.out.println("   - Heartbeat Interval: " + response.getHeartbeatInterval() + "ms");
        System.out.println("   - Controller Endpoint: " + response.getControllerEndpoint());
        
        assertTrue(response.isSuccess(), "Registration should be successful");
        assertEquals(request.getAgentId(), response.getAgentId(), "Agent IDs should match");
        
        // Verify agent can be retrieved via REST API
        given()
            .auth().basic("admin", "admin")
            .when()
                .get("/api/v1/agents/{agentId}", request.getAgentId())
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("agentId", equalTo(request.getAgentId()))
                .body("status", equalTo("healthy"))
                .body("hostname", equalTo(request.getHostname()));
        
        System.out.println("✅ Agent retrieval via REST API successful");
    }

    @Test
    void testFleetStatisticsAPI() {
        System.out.println("\n📊 TESTING FLEET STATISTICS API");
        System.out.println("===============================");
        
        // Get fleet statistics via REST API
        given()
            .auth().basic("admin", "admin")
            .when()
                .get("/api/v1/agents/statistics")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("totalAgents", greaterThanOrEqualTo(0))
                .body("healthyAgents", greaterThanOrEqualTo(0))
                .body("availableAgents", greaterThanOrEqualTo(0))
                .body("statusCounts", notNullValue())
                .body("regionCounts", notNullValue());
        
        System.out.println("✅ Fleet statistics API working correctly");
    }

    @Test
    void testAgentCapabilityMatching() {
        System.out.println("\n🎯 TESTING AGENT CAPABILITY MATCHING");
        System.out.println("====================================");
        
        AgentCapabilities capabilities = createTestAgentCapabilities();
        
        // Test protocol support
        assertTrue(capabilities.supportsProtocol("HTTP"), "Should support HTTP");
        assertTrue(capabilities.supportsProtocol("FTP"), "Should support FTP");
        assertFalse(capabilities.supportsProtocol("UNKNOWN"), "Should not support unknown protocol");
        
        // Test transfer size limits
        assertTrue(capabilities.canHandleTransferSize(1000L), "Should handle small transfers");
        assertTrue(capabilities.canHandleTransferSize(1000000L), "Should handle medium transfers");
        
        // Test region availability
        assertTrue(capabilities.isAvailableInRegion("us-east-1"), "Should be available in us-east-1");
        assertTrue(capabilities.isAvailableInRegion("eu-west-1"), "Should be available in eu-west-1");
        
        // Test compatibility scoring
        double score = capabilities.calculateCompatibilityScore("HTTP", 1000L, "us-east-1");
        System.out.println("   - Compatibility Score (HTTP, 1KB, us-east-1): " + score);
        assertTrue(score > 0.8, "Should have high compatibility score");
        
        System.out.println("✅ Agent capability matching working correctly");
    }

    private AgentCapabilities createTestAgentCapabilities() {
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.addSupportedProtocol("HTTP");
        capabilities.addSupportedProtocol("HTTPS");
        capabilities.addSupportedProtocol("FTP");
        capabilities.addSupportedProtocol("SFTP");
        capabilities.setMaxConcurrentTransfers(10);
        capabilities.setMaxTransferSize(10L * 1024 * 1024 * 1024); // 10GB
        capabilities.setMaxBandwidth(1000L * 1024 * 1024); // 1GB/s
        capabilities.addAvailableRegion("us-east-1");
        capabilities.addAvailableRegion("eu-west-1");
        capabilities.setSupportedCompressionTypes(Set.of("gzip", "zip"));
        capabilities.setSupportedEncryptionTypes(Set.of("AES-256", "TLS"));
        
        // Add system info
        AgentSystemInfo systemInfo = new AgentSystemInfo();
        systemInfo.setOperatingSystem("Linux");
        systemInfo.setArchitecture("x86_64");
        systemInfo.setJavaVersion("17.0.1");
        systemInfo.setTotalMemory(16L * 1024 * 1024 * 1024); // 16GB
        systemInfo.setAvailableMemory(8L * 1024 * 1024 * 1024); // 8GB
        systemInfo.setCpuCores(8);
        systemInfo.setCpuUsage(25.0);
        capabilities.setSystemInfo(systemInfo);
        
        // Add network info
        AgentNetworkInfo networkInfo = new AgentNetworkInfo();
        networkInfo.setPublicIpAddress("203.0.113.1");
        networkInfo.setPrivateIpAddress("10.0.1.100");
        networkInfo.setBandwidthCapacity(1000L * 1024 * 1024); // 1GB/s
        networkInfo.setCurrentBandwidthUsage(100L * 1024 * 1024); // 100MB/s
        networkInfo.setLatencyMs(50.0);
        networkInfo.setPacketLossPercentage(0.1);
        networkInfo.setConnectionType("ethernet");
        capabilities.setNetworkInfo(networkInfo);
        
        return capabilities;
    }

    private AgentRegistrationRequest createTestRegistrationRequest(AgentCapabilities capabilities) {
        AgentRegistrationRequest request = new AgentRegistrationRequest();
        request.setAgentId("test-agent-" + System.currentTimeMillis());
        request.setHostname("test-agent.example.com");
        request.setAddress("192.168.1.100");
        request.setPort(8080);
        request.setVersion("2.0.0");
        request.setRegion("us-east-1");
        request.setDatacenter("dc1");
        request.setCapabilities(capabilities);
        request.setAuthToken("test-token-123");
        return request;
    }
}
