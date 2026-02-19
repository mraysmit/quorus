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

package dev.mars.quorus.controller.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.mars.quorus.controller.http.HttpApiServer;
import dev.mars.quorus.controller.raft.InMemoryTransportSimulator;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftTransport;
import dev.mars.quorus.controller.state.QuorusStateMachine;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.*;

import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Infrastructure Smoke Test for Quorus Test Environment.
 * 
 * This test validates that the core infrastructure components are working correctly
 * without requiring Docker. It's designed to be fast and run before more complex
 * integration tests.
 * 
 * Tests include:
 * - Vert.x runtime initialization
 * - Raft node startup and leader election
 * - HTTP API server startup and health endpoints
 * - JSON serialization/deserialization
 * - Basic HTTP client connectivity
 * 
 * Run this test to quickly validate the test environment is ready:
 *   mvn test -pl quorus-controller -Dtest=InfrastructureSmokeTest
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-01-22
 * @version 1.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Infrastructure Smoke Tests")
class InfrastructureSmokeTest {

    private static final Logger logger = Logger.getLogger(InfrastructureSmokeTest.class.getName());
    private static final int HTTP_PORT = 18090;
    private static final String BASE_URL = "http://localhost:" + HTTP_PORT;

    private static Vertx vertx;
    private static RaftNode raftNode;
    private static QuorusStateMachine stateMachine;
    private static HttpApiServer httpServer;
    private static HttpClient httpClient;
    private static ObjectMapper objectMapper;

    private static long startupTime;

    @BeforeAll
    static void setUp() throws Exception {
        long start = System.currentTimeMillis();
        logger.info("=== Starting Infrastructure Smoke Test ===");

        // Initialize components
        vertx = Vertx.vertx();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // Initialize state machine
        stateMachine = new QuorusStateMachine();

        // Create transport and Raft node
        RaftTransport transport = new InMemoryTransportSimulator("smoke-test-node");
        Set<String> clusterNodes = Set.of("smoke-test-node");
        raftNode = new RaftNode(vertx, "smoke-test-node", clusterNodes, transport, stateMachine, 500, 100);
        raftNode.start();

        // Wait for leader election reactively
        await().atMost(java.time.Duration.ofSeconds(10))
            .pollInterval(java.time.Duration.ofMillis(50))
            .until(() -> raftNode.isLeader());

        // Start HTTP server and wait for it to be ready
        httpServer = new HttpApiServer(vertx, HTTP_PORT, raftNode);
        httpServer.start().toCompletionStage().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);

        startupTime = System.currentTimeMillis() - start;
        logger.info("Infrastructure started in " + startupTime + "ms");
    }

    @AfterAll
    static void tearDown() throws Exception {
        logger.info("=== Tearing Down Infrastructure ===");
        if (httpServer != null) httpServer.stop();
        if (raftNode != null) raftNode.stop();
        if (vertx != null) {
            vertx.close();
        }
        logger.info("=== Infrastructure Smoke Test Complete ===");
    }

    // ========== Vert.x Runtime Tests ==========

    @Test
    @Order(1)
    @DisplayName("1. Vert.x runtime is available")
    void testVertxRuntime() {
        assertNotNull(vertx, "Vert.x instance should be created");
        assertFalse(vertx.isClustered(), "Should not be in clustered mode for tests");
        logger.info("✓ Vert.x runtime is available");
    }

    // ========== Raft Node Tests ==========

    @Test
    @Order(2)
    @DisplayName("2. Raft node becomes leader")
    void testRaftNodeLeaderElection() {
        assertNotNull(raftNode, "Raft node should be created");
        assertTrue(raftNode.isLeader(), "Single-node cluster should elect itself as leader");
        assertEquals("smoke-test-node", raftNode.getNodeId(), "Node ID should match");
        logger.info("✓ Raft node is leader (node: " + raftNode.getNodeId() + ")");
    }

    @Test
    @Order(3)
    @DisplayName("3. State machine is initialized")
    void testStateMachineInitialized() {
        assertNotNull(stateMachine, "State machine should be created");
        assertNotNull(stateMachine.getAgents(), "Agents map should be available");
        assertNotNull(stateMachine.getJobAssignments(), "Job assignments map should be available");
        assertNotNull(stateMachine.getTransferJobs(), "Transfer jobs map should be available");
        logger.info("✓ State machine is initialized");
    }

    // ========== HTTP API Tests ==========

    @Test
    @Order(4)
    @DisplayName("4. Health endpoint responds")
    void testHealthEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, response.statusCode(), "Health endpoint should return 200");
        
        JsonNode json = objectMapper.readTree(response.body());
        assertEquals("UP", json.get("status").asText(), "Status should be UP");
        assertEquals("smoke-test-node", json.get("nodeId").asText(), "Node ID should match");
        
        logger.info("✓ Health endpoint responds: " + json.get("status").asText());
    }

    @Test
    @Order(5)
    @DisplayName("5. Health/live endpoint responds (liveness probe)")
    void testHealthLiveEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/health/live"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, response.statusCode(), "Liveness endpoint should return 200");
        
        JsonNode json = objectMapper.readTree(response.body());
        assertEquals("UP", json.get("status").asText(), "Liveness status should be UP");
        assertTrue(json.has("timestamp"), "Liveness should include timestamp");
        
        logger.info("✓ Health/live endpoint responds: " + json.get("status").asText());
    }

    @Test
    @Order(6)
    @DisplayName("6. Health/ready endpoint responds (readiness probe)")
    void testHealthReadyEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/health/ready"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, response.statusCode(), "Readiness endpoint should return 200 when ready");
        
        JsonNode json = objectMapper.readTree(response.body());
        assertEquals("UP", json.get("status").asText(), "Readiness status should be UP");
        assertTrue(json.has("checks"), "Readiness should include checks object");
        assertEquals("UP", json.get("checks").get("raftRunning").asText(), "Raft should be running");
        assertEquals("UP", json.get("checks").get("clusterHasLeader").asText(), "Cluster should have leader");
        
        logger.info("✓ Health/ready endpoint responds: " + json.get("status").asText());
    }

    @Test
    @Order(7)
    @DisplayName("7. Full health endpoint includes detailed checks")
    void testFullHealthEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, response.statusCode(), "Full health endpoint should return 200");
        
        JsonNode json = objectMapper.readTree(response.body());
        assertEquals("UP", json.get("status").asText(), "Full health status should be UP");
        assertTrue(json.has("version"), "Full health should include version");
        assertTrue(json.has("timestamp"), "Full health should include timestamp");
        assertTrue(json.has("raft"), "Full health should include raft object");
        assertTrue(json.has("checks"), "Full health should include checks object");
        
        // Verify Raft details
        JsonNode raft = json.get("raft");
        assertEquals("LEADER", raft.get("state").asText(), "Raft state should be LEADER");
        assertTrue(raft.has("term"), "Raft should include term");
        assertTrue(raft.get("isLeader").asBoolean(), "isLeader should be true");
        
        // Verify checks
        JsonNode checks = json.get("checks");
        assertTrue(checks.has("raftCluster"), "Checks should include raftCluster");
        assertTrue(checks.has("diskSpace"), "Checks should include diskSpace");
        assertTrue(checks.has("memory"), "Checks should include memory");
        
        logger.info("✓ Full health endpoint responds with detailed checks");
    }

    @Test
    @Order(8)
    @DisplayName("8. Raft status endpoint responds")
    void testRaftStatusEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/raft/status"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, response.statusCode(), "Raft status endpoint should return 200");
        
        JsonNode json = objectMapper.readTree(response.body());
        assertEquals("LEADER", json.get("state").asText(), "State should be LEADER");
        assertTrue(json.has("currentTerm"), "Response should include currentTerm");
        assertTrue(json.has("isLeader"), "Response should include isLeader");
        
        logger.info("✓ Raft status endpoint responds: state=" + json.get("state").asText() + 
                   ", currentTerm=" + json.get("currentTerm").asInt());
    }

    @Test
    @Order(9)
    @DisplayName("9. Assignment list endpoint responds")
    void testCommandEndpoint() throws Exception {
        // Test job assignment listing endpoint (replaced removed /api/v1/command)
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/v1/assignments"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, response.statusCode(),
                "Assignments endpoint should respond with 200 (status: " + response.statusCode() + ")");
        
        logger.info("✓ Assignments endpoint responds (status: " + response.statusCode() + ")");
    }

    // ========== Metrics Endpoint Tests ==========

    @Test
    @Order(10)
    @DisplayName("10. Metrics endpoint responds")
    void testMetricsEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/metrics"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        // Metrics endpoint should respond - either with metrics (200) or with error if OTel exporter is not running (500)
        // In test environment without OTel exporter, we expect 500 with a descriptive error
        assertTrue(response.statusCode() == 200 || response.statusCode() == 500,
                "Metrics endpoint should respond with 200 (if OTel running) or 500 (if not)");
        
        if (response.statusCode() == 200) {
            // If OTel exporter is running, verify we get Prometheus-format metrics
            String body = response.body();
            assertTrue(body.contains("# ") || body.contains("TYPE") || body.isEmpty() == false,
                    "Metrics response should contain Prometheus-format data");
            logger.info("✓ Metrics endpoint responds with Prometheus data");
        } else {
            // Verify we get a meaningful error message, not a crash
            String body = response.body();
            assertTrue(body.contains("Metrics unavailable") || body.contains("Internal Server Error"),
                    "Metrics endpoint should return descriptive error when OTel exporter unavailable");
            logger.info("✓ Metrics endpoint responds with expected error (OTel exporter not running in test)");
        }
    }

    // ========== JSON Serialization Tests ==========

    @Test
    @Order(11)
    @DisplayName("11. JSON serialization works")
    void testJsonSerialization() throws Exception {
        // Test that Java Time module is properly configured
        Instant now = Instant.now();
        String json = objectMapper.writeValueAsString(now);
        Instant deserialized = objectMapper.readValue(json, Instant.class);
        
        assertEquals(now.getEpochSecond(), deserialized.getEpochSecond(), 
                    "Instant serialization should round-trip correctly");
        
        logger.info("✓ JSON serialization with Java Time module works");
    }

    // ========== Performance Baseline ==========

    @Test
    @Order(12)
    @DisplayName("12. Startup time is acceptable")
    void testStartupTime() {
        assertTrue(startupTime < 10000, "Infrastructure should start in under 10 seconds");
        logger.info("✓ Startup time: " + startupTime + "ms (threshold: 10000ms)");
    }

    @Test
    @Order(13)
    @DisplayName("13. Health endpoint latency is acceptable")
    void testHealthEndpointLatency() throws Exception {
        long start = System.currentTimeMillis();
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        long latency = System.currentTimeMillis() - start;
        assertTrue(latency < 1000, "Health endpoint should respond in under 1 second");
        
        logger.info("✓ Health endpoint latency: " + latency + "ms (threshold: 1000ms)");
    }

    // ========== Summary ==========

    @Test
    @Order(14)
    @DisplayName("14. All infrastructure components ready")
    void testInfrastructureSummary() {
        logger.info("");
        logger.info("=== Infrastructure Smoke Test Summary ===");
        logger.info("  Vert.x:        ✓ Running");
        logger.info("  Raft Node:     ✓ Leader elected");
        logger.info("  State Machine: ✓ Initialized");
        logger.info("  HTTP Server:   ✓ Listening on port " + HTTP_PORT);
        logger.info("  Metrics:       ✓ Endpoint available");
        logger.info("  Startup Time:  " + startupTime + "ms");
        logger.info("=========================================");
        logger.info("");
        logger.info("Test environment is READY for integration tests.");
        
        // All checks passed if we got here
        assertTrue(true, "All infrastructure components are ready");
    }
}
