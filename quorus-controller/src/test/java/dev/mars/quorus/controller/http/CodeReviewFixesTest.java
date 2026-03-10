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

package dev.mars.quorus.controller.http;

import dev.mars.quorus.controller.raft.InMemoryTransportSimulator;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftNodeMode;
import dev.mars.quorus.controller.raft.RaftTransport;
import dev.mars.quorus.controller.state.QuorusStateStore;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for code review fixes across the controller.http package.
 *
 * <p>Covers:
 * <ul>
 *   <li>H1: CAS conflict responses use standard error envelope</li>
 *   <li>H2: AgentJobsHandler returns envelope (not raw array)</li>
 *   <li>M2: ErrorResponse.httpStatus() stored directly (not reverse-looked-up)</li>
 *   <li>M3: Request ID format consistency (full UUID)</li>
 *   <li>M5: InfoHandler lists all endpoints</li>
 *   <li>R2-1: JobStatusHandler uses QuorusApiException consistently</li>
 *   <li>R2-2: MetricsHandler error uses standard envelope</li>
 *   <li>R2-4: AgentRegistrationHandler validates hostname/address</li>
 *   <li>R2-FIX: AgentJobsHandler assignment ID separator consistency</li>
 * </ul>
 *
 * <p>Uses a real single-node Raft cluster (no mocking).</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-03-10
 */
@ExtendWith(VertxExtension.class)
@DisplayName("Code Review Fixes Tests")
class CodeReviewFixesTest {

    private static final int HTTP_PORT = 18098;

    private static Vertx vertx;
    private static RaftNode raftNode;
    private static HttpApiServer httpServer;
    private static WebClient webClient;

    @BeforeAll
    static void setUp() throws Exception {
        vertx = Vertx.vertx();

        QuorusStateStore stateMachine = new QuorusStateStore();
        RaftTransport transport = new InMemoryTransportSimulator("review-fix-node");
        Set<String> clusterNodes = Set.of("review-fix-node");

        raftNode = RaftNode.builder()
                .vertx(vertx)
                .nodeId("review-fix-node")
                .clusterNodes(clusterNodes)
                .transport(transport)
                .stateMachine(stateMachine)
                .mode(RaftNodeMode.volatileMode())
                .electionTimeout(500)
                .heartbeatInterval(100)
                .build();
        raftNode.start();

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> raftNode.isLeader());

        httpServer = new HttpApiServer(vertx, HTTP_PORT, raftNode, stateMachine);
        httpServer.start().toCompletionStage().toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        webClient = WebClient.create(vertx);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (webClient != null) webClient.close();
        if (httpServer != null) httpServer.stop().toCompletionStage().toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        if (raftNode != null) raftNode.stop();
        if (vertx != null) vertx.close().toCompletionStage().toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        InMemoryTransportSimulator.clearAllTransports();
    }

    // ==================== H1: CAS conflict uses standard error envelope ====================

    @Nested
    @DisplayName("H1: Assignment CAS conflict uses standard error envelope")
    class CasConflictEnvelopeTests {

        @Test
        @DisplayName("Status update with invalid transition returns standard error envelope with 409")
        void testInvalidTransitionReturnsStandardEnvelope(VertxTestContext ctx) {
            // Create an assignment, then try an invalid transition (ASSIGNED → COMPLETED is invalid)
            JsonObject createBody = new JsonObject()
                    .put("jobId", "job-cas-envelope")
                    .put("agentId", "agent-cas-envelope");

            webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments")
                    .sendJsonObject(createBody)
                    .compose(createResp -> {
                        String id = createResp.bodyAsJsonObject().getString("assignmentId");
                        JsonObject statusBody = new JsonObject().put("status", "COMPLETED");
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/status")
                                .sendJsonObject(statusBody);
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(409, response.statusCode());
                        JsonObject body = response.bodyAsJsonObject();
                        // Must use standard { "error": { ... } } envelope
                        JsonObject error = body.getJsonObject("error");
                        assertNotNull(error, "Response must use standard error envelope with 'error' key");
                        assertEquals("ASSIGNMENT_STATE_CONFLICT", error.getString("code"),
                                "Error code must be ASSIGNMENT_STATE_CONFLICT");
                        assertNotNull(error.getString("shortCode"), "Must include shortCode");
                        assertNotNull(error.getString("timestamp"), "Must include timestamp");
                        assertNotNull(error.getString("path"), "Must include path");
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== H2: AgentJobsHandler envelope ====================

    @Nested
    @DisplayName("H2: Agent jobs endpoint returns envelope")
    class AgentJobsEnvelopeTests {

        @Test
        @DisplayName("GET /api/v1/agents/:agentId/jobs returns JSON object envelope (not raw array)")
        void testAgentJobsReturnsEnvelope(VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/api/v1/agents/nonexistent-agent/jobs")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonObject body = response.bodyAsJsonObject();
                        assertNotNull(body, "Response must be a JSON object, not a raw array");
                        assertNotNull(body.getJsonArray("pendingJobs"),
                                "Must have 'pendingJobs' array");
                        assertNotNull(body.getInteger("total"),
                                "Must have 'total' count");
                        assertEquals(0, body.getInteger("total"),
                                "Non-existent agent should have 0 pending jobs");
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("GET /api/v1/agents/:agentId/jobs returns enriched job data in envelope")
        void testAgentJobsWithAssignmentReturnsEnvelope(VertxTestContext ctx) {
            // Create an assignment, then poll for jobs — must come in envelope
            String agentId = "agent-envelope-test";
            JsonObject createBody = new JsonObject()
                    .put("jobId", "job-envelope-test")
                    .put("agentId", agentId);

            webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments")
                    .sendJsonObject(createBody)
                    .compose(createResp ->
                            webClient.get(HTTP_PORT, "localhost", "/api/v1/agents/" + agentId + "/jobs")
                                    .send())
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonObject body = response.bodyAsJsonObject();
                        assertNotNull(body.getJsonArray("pendingJobs"));
                        assertTrue(body.getInteger("total") > 0,
                                "Agent with assignment should have pending jobs");
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== M2: ErrorResponse.httpStatus() stored directly ====================

    @Nested
    @DisplayName("M2: ErrorResponse httpStatus stored directly")
    class ErrorResponseHttpStatusTests {

        @Test
        @DisplayName("httpStatus returns correct value from record field")
        void testHttpStatusFromField() {
            ErrorResponse response = ErrorResponse.of(ErrorCode.TRANSFER_NOT_FOUND, "/test", "x");
            assertEquals(404, response.httpStatus());
        }

        @Test
        @DisplayName("httpStatus is consistent across all factory methods")
        void testHttpStatusConsistency() {
            ErrorResponse withMsg = ErrorResponse.withMessage(ErrorCode.BAD_REQUEST, "/x", "msg");
            ErrorResponse withMsgId = ErrorResponse.withMessage(ErrorCode.BAD_REQUEST, "/x", "msg", "req-1");
            ErrorResponse of = ErrorResponse.of(ErrorCode.BAD_REQUEST, "/x", "arg");
            ErrorResponse fromEx = ErrorResponse.fromException(ErrorCode.BAD_REQUEST, new RuntimeException("e"), "/x");
            ErrorResponse fromExId = ErrorResponse.fromException(ErrorCode.BAD_REQUEST, new RuntimeException("e"), "/x", "req-2");

            assertEquals(400, withMsg.httpStatus());
            assertEquals(400, withMsgId.httpStatus());
            assertEquals(400, of.httpStatus());
            assertEquals(400, fromEx.httpStatus());
            assertEquals(400, fromExId.httpStatus());
        }

        @Test
        @DisplayName("httpStatus covers all severity levels")
        void testHttpStatusAllLevels() {
            assertEquals(404, ErrorResponse.of(ErrorCode.NOT_FOUND, "/x", "res").httpStatus());
            assertEquals(409, ErrorResponse.of(ErrorCode.CONFLICT, "/x", "res").httpStatus());
            assertEquals(500, ErrorResponse.of(ErrorCode.INTERNAL_ERROR, "/x", "err").httpStatus());
            assertEquals(503, ErrorResponse.of(ErrorCode.NOT_LEADER, "/x", "node").httpStatus());
        }
    }

    // ==================== M3: Request ID format consistency ====================

    @Nested
    @DisplayName("M3: Request ID format consistency")
    class RequestIdFormatTests {

        @Test
        @DisplayName("ErrorResponse.generateRequestId uses full UUID format")
        void testErrorResponseRequestIdFormat() {
            ErrorResponse response = ErrorResponse.of(ErrorCode.BAD_REQUEST, "/test", "msg");
            String requestId = response.requestId();
            assertNotNull(requestId);
            assertTrue(requestId.startsWith("req-"), "Must start with 'req-'");
            // Full UUID: "req-" + 36 chars = 40 chars
            assertTrue(requestId.length() > 12,
                    "Request ID must use full UUID, not truncated: " + requestId);
        }

        @Test
        @DisplayName("Auto-generated request IDs from HTTP responses match full UUID format")
        void testHttpAutoGeneratedRequestIdFormat(VertxTestContext ctx) {
            // Hit a 404 endpoint without providing X-Request-ID
            webClient.get(HTTP_PORT, "localhost", "/api/v1/transfers/nonexistent-format-test")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(404, response.statusCode());
                        String headerRequestId = response.getHeader("X-Request-ID");
                        assertNotNull(headerRequestId);
                        assertTrue(headerRequestId.startsWith("req-"));
                        // CorrelationIdHandler uses full UUID
                        assertTrue(headerRequestId.length() > 12,
                                "Header request ID must use full UUID: " + headerRequestId);

                        // Error body requestId must match
                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertEquals(headerRequestId, error.getString("requestId"),
                                "Body and header request IDs must match");
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== M5: InfoHandler lists all endpoints ====================

    @Nested
    @DisplayName("M5: InfoHandler lists all endpoints")
    class InfoHandlerEndpointsTests {

        @Test
        @DisplayName("Info endpoint includes all endpoint categories")
        void testInfoEndpointCategories(VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/api/v1/info")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonObject body = response.bodyAsJsonObject();
                        JsonObject endpoints = body.getJsonObject("endpoints");
                        assertNotNull(endpoints);

                        // All categories must be present
                        assertNotNull(endpoints.getJsonArray("health"), "Must list health endpoints");
                        assertNotNull(endpoints.getJsonArray("agents"), "Must list agent endpoints");
                        assertNotNull(endpoints.getJsonArray("transfers"), "Must list transfer endpoints");
                        assertNotNull(endpoints.getJsonArray("jobs"), "Must list job endpoints");
                        assertNotNull(endpoints.getJsonArray("assignments"), "Must list assignment endpoints");
                        assertNotNull(endpoints.getJsonArray("routes"), "Must list route endpoints");
                        assertNotNull(endpoints.getJsonArray("cluster"), "Must list cluster endpoints");
                        assertNotNull(endpoints.getJsonArray("metrics"), "Must list metrics endpoints");
                        assertNotNull(endpoints.getJsonArray("info"), "Must list info endpoints");
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("Info endpoint lists all 8 assignment endpoints")
        void testInfoEndpointAssignments(VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/api/v1/info")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        JsonObject endpoints = response.bodyAsJsonObject().getJsonObject("endpoints");
                        JsonArray assignments = endpoints.getJsonArray("assignments");
                        assertEquals(8, assignments.size(),
                                "Must list all 8 assignment endpoints");
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("Info endpoint lists all 7 route endpoints")
        void testInfoEndpointRoutes(VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/api/v1/info")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        JsonObject endpoints = response.bodyAsJsonObject().getJsonObject("endpoints");
                        JsonArray routes = endpoints.getJsonArray("routes");
                        assertEquals(7, routes.size(),
                                "Must list all 7 route endpoints");
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("Info endpoint lists agent jobs polling endpoint")
        void testInfoEndpointAgentJobs(VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/api/v1/info")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        JsonObject endpoints = response.bodyAsJsonObject().getJsonObject("endpoints");
                        JsonArray agents = endpoints.getJsonArray("agents");
                        // Should have 4 entries: register, heartbeat, list, and agent jobs
                        assertEquals(4, agents.size(),
                                "Must list all 4 agent endpoints including agent jobs polling");
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("Info endpoint lists job status update endpoint")
        void testInfoEndpointJobStatus(VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/api/v1/info")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        JsonObject endpoints = response.bodyAsJsonObject().getJsonObject("endpoints");
                        JsonArray jobs = endpoints.getJsonArray("jobs");
                        assertEquals(1, jobs.size());
                        JsonObject jobEndpoint = jobs.getJsonObject(0);
                        assertEquals("POST", jobEndpoint.getString("method"));
                        assertTrue(jobEndpoint.getString("path").contains("status"));
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== M1: HealthHandler non-blocking checks ====================

    @Nested
    @DisplayName("M1: Health endpoint still works with cached checks")
    class HealthCachedChecksTests {

        @Test
        @DisplayName("Health endpoint returns disk and memory checks from cache")
        void testHealthEndpointWithCachedChecks(VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/health")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonObject body = response.bodyAsJsonObject();
                        JsonObject checks = body.getJsonObject("checks");
                        assertNotNull(checks.getString("diskSpace"),
                                "Disk space check should still be present");
                        assertNotNull(checks.getString("memory"),
                                "Memory check should still be present");
                        // If the periodic check ran, values should be UP
                        assertTrue(
                                "UP".equals(checks.getString("diskSpace")) ||
                                "WARNING".equals(checks.getString("diskSpace")),
                                "Disk space should be UP or WARNING");
                        assertTrue(
                                "UP".equals(checks.getString("memory")) ||
                                "WARNING".equals(checks.getString("memory")),
                                "Memory should be UP or WARNING");
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== R2-1: JobStatusHandler uses QuorusApiException ====================

    @Nested
    @DisplayName("R2-1: JobStatusHandler uses standard error patterns")
    class JobStatusHandlerErrorPatternTests {

        @Test
        @DisplayName("POST /jobs/:jobId/status with unknown assignment returns standard 404 envelope")
        void testJobStatusNotFoundReturnsStandardEnvelope(VertxTestContext ctx) {
            JsonObject body = new JsonObject()
                    .put("agentId", "no-such-agent")
                    .put("status", "ACCEPTED");

            webClient.post(HTTP_PORT, "localhost", "/api/v1/jobs/no-such-job/status")
                    .sendJsonObject(body)
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(404, response.statusCode());
                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertNotNull(error, "Must use standard error envelope");
                        assertEquals("ASSIGNMENT_NOT_FOUND", error.getString("code"));
                        assertNotNull(error.getString("shortCode"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("POST /jobs/:jobId/status with invalid transition returns standard 409 envelope")
        void testJobStatusInvalidTransitionReturnsStandardEnvelope(VertxTestContext ctx) {
            // Create a transfer job and assignment first
            String jobId = "job-status-transition";
            String agentId = "agent-status-transition";

            JsonObject transferBody = new JsonObject()
                    .put("jobId", jobId)
                    .put("sourceUri", "https://example.com/file.csv")
                    .put("destinationPath", "/data/file.csv");

            JsonObject assignBody = new JsonObject()
                    .put("jobId", jobId)
                    .put("agentId", agentId);

            webClient.post(HTTP_PORT, "localhost", "/api/v1/transfers")
                    .sendJsonObject(transferBody)
                    .compose(r -> webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments")
                            .sendJsonObject(assignBody))
                    .compose(r -> {
                        // Try invalid transition: ASSIGNED -> COMPLETED (skip ACCEPTED, IN_PROGRESS)
                        JsonObject statusBody = new JsonObject()
                                .put("agentId", agentId)
                                .put("status", "COMPLETED");
                        return webClient.post(HTTP_PORT, "localhost", "/api/v1/jobs/" + jobId + "/status")
                                .sendJsonObject(statusBody);
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(409, response.statusCode());
                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertNotNull(error, "Must use standard error envelope for transition conflict");
                        assertEquals("ASSIGNMENT_STATE_CONFLICT", error.getString("code"));
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== R2-4: AgentRegistrationHandler validates hostname/address ====================

    @Nested
    @DisplayName("R2-4: Agent registration validates required contact fields")
    class AgentRegistrationValidationTests {

        @Test
        @DisplayName("Missing hostname returns 400")
        void testMissingHostnameReturns400(VertxTestContext ctx) {
            JsonObject body = new JsonObject()
                    .put("agentId", "agent-no-hostname")
                    .put("address", "10.0.0.1")
                    .put("port", 8080);

            webClient.post(HTTP_PORT, "localhost", "/api/v1/agents/register")
                    .sendJsonObject(body)
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(400, response.statusCode());
                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertNotNull(error, "Must return standard error envelope");
                        assertTrue(error.getString("message").contains("hostname"),
                                "Error must mention 'hostname': " + error.getString("message"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("Missing address returns 400")
        void testMissingAddressReturns400(VertxTestContext ctx) {
            JsonObject body = new JsonObject()
                    .put("agentId", "agent-no-address")
                    .put("hostname", "server1.local")
                    .put("port", 8080);

            webClient.post(HTTP_PORT, "localhost", "/api/v1/agents/register")
                    .sendJsonObject(body)
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(400, response.statusCode());
                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertNotNull(error, "Must return standard error envelope");
                        assertTrue(error.getString("message").contains("address"),
                                "Error must mention 'address': " + error.getString("message"));
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== R2-FIX: Assignment ID separator consistency ====================

    @Nested
    @DisplayName("R2-FIX: Assignment ID uses colon separator consistently")
    class AssignmentIdSeparatorTests {

        @Test
        @DisplayName("Agent jobs endpoint returns assignmentId with colon separator matching state store convention")
        void testAgentJobsAssignmentIdUsesColon(VertxTestContext ctx) {
            String agentId = "agent-separator-test";
            String jobId = "job-separator-test";

            JsonObject createBody = new JsonObject()
                    .put("jobId", jobId)
                    .put("agentId", agentId);

            webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments")
                    .sendJsonObject(createBody)
                    .compose(createResp ->
                            webClient.get(HTTP_PORT, "localhost", "/api/v1/agents/" + agentId + "/jobs")
                                    .send())
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonArray pendingJobs = response.bodyAsJsonObject().getJsonArray("pendingJobs");
                        assertTrue(pendingJobs.size() > 0, "Should have at least one pending job");

                        JsonObject job = pendingJobs.getJsonObject(0);
                        String assignmentId = job.getString("assignmentId");
                        assertTrue(assignmentId.contains(":"),
                                "Assignment ID must use ':' separator: " + assignmentId);
                        assertFalse(assignmentId.contains("-") && !assignmentId.contains(":"),
                                "Assignment ID must NOT use '-' separator only: " + assignmentId);
                        assertEquals(jobId + ":" + agentId, assignmentId,
                                "Assignment ID must be jobId:agentId");
                        ctx.completeNow();
                    })));
        }
    }
}
