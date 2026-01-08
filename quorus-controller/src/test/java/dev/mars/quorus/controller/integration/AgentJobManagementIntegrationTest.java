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
import dev.mars.quorus.controller.raft.TestInMemoryTransport;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftTransport;
import dev.mars.quorus.controller.state.QuorusStateMachine;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Agent Job Management flow.
 * Tests the complete flow from job assignment to status reporting.
 *
 * This test uses real implementations without mocking.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-11
 * @version 1.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentJobManagementIntegrationTest {

    private static final Logger logger = Logger.getLogger(AgentJobManagementIntegrationTest.class.getName());
    private static final int HTTP_PORT = 18080;
    private static final String BASE_URL = "http://localhost:" + HTTP_PORT;

    private static RaftNode raftNode;
    private static QuorusStateMachine stateMachine;
    private static HttpApiServer httpServer;
    private static HttpClient httpClient;
    private static ObjectMapper objectMapper;
    private static io.vertx.core.Vertx vertx;

    private static String testAgentId;
    private static String testJobId;
    private static String testAssignmentId;

    @BeforeAll
    static void setUp() throws Exception {
        logger.info("Setting up Agent Job Management Integration Test");

        vertx = io.vertx.core.Vertx.vertx();

        // Initialize object mapper
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Initialize HTTP client
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // Initialize state machine
        stateMachine = new QuorusStateMachine();

        // Create an in-memory transport for testing (supports single-node clusters)
        RaftTransport transport = new TestInMemoryTransport("test-node-1");

        // Initialize Raft node (single node cluster for testing with short election timeout)
        Set<String> clusterNodes = Set.of("test-node-1");
        raftNode = new RaftNode(vertx, "test-node-1", clusterNodes, transport, stateMachine, 500, 100);
        raftNode.start();

        // Wait for node to become leader (election timeout is 500-1000ms)
        for (int i = 0; i < 30; i++) {
            if (raftNode.isLeader()) {
                break;
            }
            Thread.sleep(100);
        }

        assertTrue(raftNode.isLeader(), "Node should become leader");

        // Start HTTP API server
        httpServer = new HttpApiServer(vertx, HTTP_PORT, raftNode);
        httpServer.start();

        // Wait for server to be ready
        Thread.sleep(1000);

        logger.info("Test environment ready");
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (httpServer != null) {
            httpServer.stop();
        }
        if (raftNode != null) {
            raftNode.stop();
        }
        if (vertx != null) {
            vertx.close();
        }
        // Clear the TestInMemoryTransport registry
        TestInMemoryTransport.clearAllTransports();
        logger.info("Test environment cleaned up");
    }

    @Test
    @Order(1)
    void testRegisterAgent() throws Exception {
        logger.info("TEST 1: Register Agent");

        testAgentId = "test-agent-" + System.currentTimeMillis();

        String requestBody = """
            {
                "agentId": "%s",
                "hostname": "test-host",
                "address": "192.168.1.100",
                "port": 8080,
                "version": "1.0.0",
                "region": "us-east-1",
                "datacenter": "dc1",
                "capabilities": {
                    "supportedProtocols": ["http", "https", "ftp"],
                    "maxConcurrentTransfers": 5,
                    "maxTransferSize": 1073741824,
                    "maxBandwidth": 104857600
                }
            }
            """.formatted(testAgentId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/v1/agents/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode(), "Agent registration should succeed");

        JsonNode responseJson = objectMapper.readTree(response.body());
        assertTrue(responseJson.get("success").asBoolean(), "Response should indicate success");
        assertEquals(testAgentId, responseJson.get("agentId").asText(), "Agent ID should match");

        logger.info("✅ Agent registered: " + testAgentId);
    }

    @Test
    @Order(2)
    void testCreateTransferJob() throws Exception {
        logger.info("TEST 2: Create Transfer Job");

        testJobId = "test-job-" + System.currentTimeMillis();

        String requestBody = """
            {
                "jobId": "%s",
                "sourceUri": "https://example.com/file.txt",
                "destinationPath": "/data/file.txt",
                "totalBytes": 1048576,
                "description": "Test transfer job"
            }
            """.formatted(testJobId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/v1/transfers"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode(), "Transfer job creation should succeed");

        JsonNode responseJson = objectMapper.readTree(response.body());
        assertTrue(responseJson.get("success").asBoolean(), "Response should indicate success");

        logger.info("✅ Transfer job created: " + testJobId);
    }

    @Test
    @Order(3)
    void testAssignJobToAgent() throws Exception {
        logger.info("TEST 3: Assign Job to Agent");

        testAssignmentId = "assignment-" + System.currentTimeMillis();

        String requestBody = """
            {
                "assignmentId": "%s",
                "jobId": "%s",
                "agentId": "%s"
            }
            """.formatted(testAssignmentId, testJobId, testAgentId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/v1/jobs/assign"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode(), "Job assignment should succeed");

        JsonNode responseJson = objectMapper.readTree(response.body());
        assertTrue(responseJson.get("success").asBoolean(), "Response should indicate success");

        logger.info("✅ Job assigned to agent: " + testAssignmentId);
    }

    @Test
    @Order(4)
    void testAgentPollsForJobs() throws Exception {
        logger.info("TEST 4: Agent Polls for Jobs");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/v1/agents/" + testAgentId + "/jobs"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "Job polling should succeed");

        JsonNode responseJson = objectMapper.readTree(response.body());
        assertTrue(responseJson.isArray(), "Response should be an array");
        assertTrue(responseJson.size() > 0, "Should have at least one pending job");

        JsonNode firstJob = responseJson.get(0);
        assertEquals(testJobId, firstJob.get("jobId").asText(), "Job ID should match");
        assertEquals(testAgentId, firstJob.get("agentId").asText(), "Agent ID should match");

        logger.info("✅ Agent polled and found " + responseJson.size() + " pending job(s)");
    }

    @Test
    @Order(5)
    void testAgentReportsAccepted() throws Exception {
        logger.info("TEST 5: Agent Reports Job Accepted");

        String requestBody = """
            {
                "agentId": "%s",
                "status": "ACCEPTED"
            }
            """.formatted(testAgentId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/v1/jobs/" + testJobId + "/status"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "Status update should succeed");

        JsonNode responseJson = objectMapper.readTree(response.body());
        assertTrue(responseJson.get("success").asBoolean(), "Response should indicate success");

        logger.info("✅ Agent reported job accepted");
    }

    @Test
    @Order(6)
    void testAgentReportsInProgress() throws Exception {
        logger.info("TEST 6: Agent Reports Job In Progress");

        String requestBody = """
            {
                "agentId": "%s",
                "status": "IN_PROGRESS",
                "bytesTransferred": 524288
            }
            """.formatted(testAgentId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/v1/jobs/" + testJobId + "/status"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "Status update should succeed");

        JsonNode responseJson = objectMapper.readTree(response.body());
        assertTrue(responseJson.get("success").asBoolean(), "Response should indicate success");

        logger.info("✅ Agent reported job in progress (50% complete)");
    }


    @Test
    @Order(7)
    void testAgentReportsCompleted() throws Exception {
        logger.info("TEST 7: Agent Reports Job Completed");

        String requestBody = """
            {
                "agentId": "%s",
                "status": "COMPLETED",
                "bytesTransferred": 1048576
            }
            """.formatted(testAgentId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/v1/jobs/" + testJobId + "/status"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "Status update should succeed");

        JsonNode responseJson = objectMapper.readTree(response.body());
        assertTrue(responseJson.get("success").asBoolean(), "Response should indicate success");

        logger.info("✅ Agent reported job completed");
    }

    @Test
    @Order(8)
    void testVerifyJobCompletion() throws Exception {
        logger.info("TEST 8: Verify Job Completion");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/v1/transfers/" + testJobId))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "Job retrieval should succeed");

        JsonNode responseJson = objectMapper.readTree(response.body());
        assertEquals("COMPLETED", responseJson.get("status").asText(), "Job status should be COMPLETED");
        assertEquals(1048576, responseJson.get("bytesTransferred").asLong(), "Bytes transferred should match");

        logger.info("✅ Job completion verified");
    }

    @Test
    @Order(9)
    void testAgentPollsForJobsAfterCompletion() throws Exception {
        logger.info("TEST 9: Agent Polls for Jobs After Completion");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/v1/agents/" + testAgentId + "/jobs"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "Job polling should succeed");

        JsonNode responseJson = objectMapper.readTree(response.body());
        assertTrue(responseJson.isArray(), "Response should be an array");
        assertEquals(0, responseJson.size(), "Should have no pending jobs after completion");

        logger.info("✅ No pending jobs found (as expected)");
    }

    @Test
    @Order(10)
    void testCompleteFlowSummary() {
        logger.info("\n" + "=".repeat(60));
        logger.info("🎉 AGENT JOB MANAGEMENT INTEGRATION TEST COMPLETE!");
        logger.info("=".repeat(60));
        logger.info("✅ Agent Registration - PASSED");
        logger.info("✅ Transfer Job Creation - PASSED");
        logger.info("✅ Job Assignment - PASSED");
        logger.info("✅ Agent Job Polling - PASSED");
        logger.info("✅ Status Reporting (ACCEPTED) - PASSED");
        logger.info("✅ Status Reporting (IN_PROGRESS) - PASSED");
        logger.info("✅ Status Reporting (COMPLETED) - PASSED");
        logger.info("✅ Job Completion Verification - PASSED");
        logger.info("✅ Post-Completion Polling - PASSED");
        logger.info("=".repeat(60));
        logger.info("Complete flow tested successfully!");
        logger.info("Agent ID: " + testAgentId);
        logger.info("Job ID: " + testJobId);
        logger.info("Assignment ID: " + testAssignmentId);
        logger.info("=".repeat(60));
    }
}


