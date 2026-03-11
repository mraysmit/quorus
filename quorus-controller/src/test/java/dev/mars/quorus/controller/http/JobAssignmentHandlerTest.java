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

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link dev.mars.quorus.controller.http.handlers.JobAssignmentHandler}.
 *
 * <p>Exercises the full HTTP path for all 8 assignment endpoints:
 * POST, GET list, GET by ID, PUT accept/reject/status/cancel, DELETE.</p>
 *
 * <p>Uses a real single-node Raft cluster with no mocking.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-19
 */
@ExtendWith(VertxExtension.class)
@DisplayName("JobAssignmentHandler Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JobAssignmentHandlerTest {

    private static final int HTTP_PORT = 18097;

    private static Vertx vertx;
    private static RaftNode raftNode;
    private static HttpApiServer httpServer;
    private static WebClient webClient;

    /** Populated after the first assignment is created */
    private static String assignmentId;

    @BeforeAll
    static void setUp() throws Exception {
        vertx = Vertx.vertx();

        QuorusStateStore stateMachine = new QuorusStateStore();
        RaftTransport transport = new InMemoryTransportSimulator("assign-test-node");
        Set<String> clusterNodes = Set.of("assign-test-node");

        raftNode = RaftNode.builder().vertx(vertx).nodeId("assign-test-node").clusterNodes(clusterNodes).transport(transport).stateMachine(stateMachine).mode(RaftNodeMode.volatileMode())
                .electionTimeout(500).heartbeatInterval(100).build();
        raftNode.start();

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> raftNode.isLeader());

        httpServer = new HttpApiServer(vertx, HTTP_PORT, raftNode, stateMachine);
        httpServer.start().toCompletionStage().toCompletableFuture()
                .get(5, java.util.concurrent.TimeUnit.SECONDS);

        webClient = WebClient.create(vertx);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (webClient != null) webClient.close();
        if (httpServer != null) httpServer.stop().toCompletionStage().toCompletableFuture()
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
        if (raftNode != null) raftNode.stop();
        if (vertx != null) vertx.close().toCompletionStage().toCompletableFuture()
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
        InMemoryTransportSimulator.clearAllTransports();
    }

    // ==================== POST /api/v1/assignments ====================

    @Nested
    @DisplayName("POST /api/v1/assignments")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CreateAssignment {

        @Test
        @Order(1)
        @DisplayName("creates assignment and returns 201")
        void testCreateAssignment(VertxTestContext ctx) {
            JsonObject body = new JsonObject()
                    .put("jobId", "job-100")
                    .put("agentId", "agent-east-01");

            webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments")
                    .sendJsonObject(body)
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(201, response.statusCode());
                        JsonObject json = response.bodyAsJsonObject();
                        assertTrue(json.getBoolean("success"));
                        assertNotNull(json.getString("assignmentId"));
                        // Save for subsequent tests
                        assignmentId = json.getString("assignmentId");
                        ctx.completeNow();
                    })));
        }

        @Test
        @Order(2)
        @DisplayName("rejects empty body with 400")
        void testCreateAssignmentEmptyBody(VertxTestContext ctx) {
            webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(400, response.statusCode());
                        ctx.completeNow();
                    })));
        }

        @Test
        @Order(3)
        @DisplayName("rejects missing required fields with 400")
        void testCreateAssignmentMissingFields(VertxTestContext ctx) {
            JsonObject body = new JsonObject()
                    .put("someField", "someValue");

            webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments")
                    .sendJsonObject(body)
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        // Should fail because jobId and agentId are required
                        assertTrue(response.statusCode() >= 400, "Should be client error");
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== GET /api/v1/assignments ====================

    @Nested
    @DisplayName("GET /api/v1/assignments")
    class ListAssignments {

        @Test
        @DisplayName("lists all assignments")
        void testListAssignments(VertxTestContext ctx) {
            // First create an assignment to ensure the list is non-empty
            JsonObject body = new JsonObject()
                    .put("jobId", "job-list-test")
                    .put("agentId", "agent-list-test");

            webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments")
                    .sendJsonObject(body)
                    .compose(createResp -> webClient.get(HTTP_PORT, "localhost", "/api/v1/assignments")
                            .send())
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonObject json = response.bodyAsJsonObject();
                        assertNotNull(json.getJsonArray("assignments"));
                        assertTrue(json.getInteger("total") > 0, "Should have at least one assignment");
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== GET /api/v1/assignments/:assignmentId ====================

    @Nested
    @DisplayName("GET /api/v1/assignments/:id")
    class GetAssignment {

        @Test
        @DisplayName("retrieves assignment by ID")
        void testGetAssignment(VertxTestContext ctx) {
            // Create, then get
            JsonObject body = new JsonObject()
                    .put("jobId", "job-get-test")
                    .put("agentId", "agent-get-test");

            webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments")
                    .sendJsonObject(body)
                    .compose(createResp -> {
                        String id = createResp.bodyAsJsonObject().getString("assignmentId");
                        return webClient.get(HTTP_PORT, "localhost", "/api/v1/assignments/" + id)
                                .send();
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonObject json = response.bodyAsJsonObject();
                        assertEquals("job-get-test", json.getString("jobId"));
                        assertEquals("agent-get-test", json.getString("agentId"));
                        assertEquals("ASSIGNED", json.getString("status"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("returns 404 for non-existent assignment")
        void testGetAssignmentNotFound(VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/api/v1/assignments/nonexistent-id")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(404, response.statusCode());
                        JsonObject json = response.bodyAsJsonObject();
                        assertNotNull(json.getJsonObject("error"));
                        assertEquals("ASSIGNMENT_NOT_FOUND", json.getJsonObject("error").getString("code"));
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== PUT /api/v1/assignments/:id/accept ====================

    @Nested
    @DisplayName("PUT /api/v1/assignments/:id/accept")
    class AcceptAssignment {

        @Test
        @DisplayName("accepts assignment and returns 200")
        void testAcceptAssignment(VertxTestContext ctx) {
            JsonObject body = new JsonObject()
                    .put("jobId", "job-accept-test")
                    .put("agentId", "agent-accept-test");

            webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments")
                    .sendJsonObject(body)
                    .compose(createResp -> {
                        String id = createResp.bodyAsJsonObject().getString("assignmentId");
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/accept")
                                .send();
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonObject json = response.bodyAsJsonObject();
                        assertEquals("ACCEPTED", json.getString("status"));
                        assertEquals("Assignment accepted", json.getString("message"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("returns 404 for non-existent assignment")
        void testAcceptNonExistent(VertxTestContext ctx) {
            webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/nonexistent/accept")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(404, response.statusCode());
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== PUT /api/v1/assignments/:id/reject ====================

    @Nested
    @DisplayName("PUT /api/v1/assignments/:id/reject")
    class RejectAssignment {

        @Test
        @DisplayName("rejects assignment with reason")
        void testRejectAssignment(VertxTestContext ctx) {
            JsonObject body = new JsonObject()
                    .put("jobId", "job-reject-test")
                    .put("agentId", "agent-reject-test");

            webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments")
                    .sendJsonObject(body)
                    .compose(createResp -> {
                        String id = createResp.bodyAsJsonObject().getString("assignmentId");
                        JsonObject reason = new JsonObject().put("reason", "Agent at capacity");
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/reject")
                                .sendJsonObject(reason);
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonObject json = response.bodyAsJsonObject();
                        assertEquals("REJECTED", json.getString("status"));
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== PUT /api/v1/assignments/:id/status ====================

    @Nested
    @DisplayName("PUT /api/v1/assignments/:id/status")
    class UpdateStatus {

        @Test
        @DisplayName("updates assignment status")
        void testUpdateStatus(VertxTestContext ctx) {
            JsonObject createBody = new JsonObject()
                    .put("jobId", "job-status-test")
                    .put("agentId", "agent-status-test");

            // Flow: ASSIGNED → ACCEPTED (valid transition per canTransitionTo)
            webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments")
                    .sendJsonObject(createBody)
                    .compose(createResp -> {
                        String id = createResp.bodyAsJsonObject().getString("assignmentId");
                        JsonObject statusBody = new JsonObject().put("status", "ACCEPTED");
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/status")
                                .sendJsonObject(statusBody);
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonObject json = response.bodyAsJsonObject();
                        assertEquals("ACCEPTED", json.getString("status"));
                        assertEquals("Assignment status updated", json.getString("message"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("rejects missing status field with 400")
        void testUpdateStatusMissingField(VertxTestContext ctx) {
            JsonObject createBody = new JsonObject()
                    .put("jobId", "job-status-missing")
                    .put("agentId", "agent-status-missing");

            webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments")
                    .sendJsonObject(createBody)
                    .compose(createResp -> {
                        String id = createResp.bodyAsJsonObject().getString("assignmentId");
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/status")
                                .sendJsonObject(new JsonObject());
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(400, response.statusCode());
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("rejects invalid status with 400")
        void testUpdateStatusInvalidValue(VertxTestContext ctx) {
            JsonObject createBody = new JsonObject()
                    .put("jobId", "job-status-invalid")
                    .put("agentId", "agent-status-invalid");

            webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments")
                    .sendJsonObject(createBody)
                    .compose(createResp -> {
                        String id = createResp.bodyAsJsonObject().getString("assignmentId");
                        JsonObject statusBody = new JsonObject().put("status", "BOGUS_STATUS");
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/status")
                                .sendJsonObject(statusBody);
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(400, response.statusCode());
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== PUT /api/v1/assignments/:id/cancel ====================

    @Nested
    @DisplayName("PUT /api/v1/assignments/:id/cancel")
    class CancelAssignment {

        @Test
        @DisplayName("cancels assignment with reason")
        void testCancelAssignment(VertxTestContext ctx) {
            JsonObject createBody = new JsonObject()
                    .put("jobId", "job-cancel-test")
                    .put("agentId", "agent-cancel-test");

            webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments")
                    .sendJsonObject(createBody)
                    .compose(createResp -> {
                        String id = createResp.bodyAsJsonObject().getString("assignmentId");
                        JsonObject reason = new JsonObject().put("reason", "Operator cancelled");
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/cancel")
                                .sendJsonObject(reason);
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonObject json = response.bodyAsJsonObject();
                        assertEquals("CANCELLED", json.getString("status"));
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== DELETE /api/v1/assignments/:id ====================

    @Nested
    @DisplayName("DELETE /api/v1/assignments/:id")
    class RemoveAssignment {

        @Test
        @DisplayName("removes an assignment")
        void testRemoveAssignment(VertxTestContext ctx) {
            JsonObject createBody = new JsonObject()
                    .put("jobId", "job-remove-test")
                    .put("agentId", "agent-remove-test");

            webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments")
                    .sendJsonObject(createBody)
                    .compose(createResp -> {
                        String id = createResp.bodyAsJsonObject().getString("assignmentId");
                        return webClient.delete(HTTP_PORT, "localhost", "/api/v1/assignments/" + id)
                                .send();
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonObject json = response.bodyAsJsonObject();
                        assertEquals("Assignment removed", json.getString("message"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("returns 404 for non-existent assignment")
        void testRemoveNonExistent(VertxTestContext ctx) {
            webClient.delete(HTTP_PORT, "localhost", "/api/v1/assignments/nonexistent")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(404, response.statusCode());
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== Regression coverage moved from aggregate suite ====================

    @Nested
    @DisplayName("Assignment/Agent jobs regression behaviors")
    class AssignmentRegressionBehaviors {

        @Test
        @DisplayName("invalid assignment transition returns standard conflict envelope")
        void testInvalidTransitionReturnsConflictEnvelope(VertxTestContext ctx) {
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
                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertNotNull(error);
                        assertEquals("ASSIGNMENT_STATE_CONFLICT", error.getString("code"));
                        assertNotNull(error.getString("shortCode"));
                        assertNotNull(error.getString("timestamp"));
                        assertNotNull(error.getString("path"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("agent jobs endpoint returns envelope and colon-separated assignment ID")
        void testAgentJobsEnvelopeAndAssignmentId(VertxTestContext ctx) {
            String agentId = "agent-separator-test";
            String jobId = "job-separator-test";

            JsonObject createBody = new JsonObject()
                    .put("jobId", jobId)
                    .put("agentId", agentId);

            webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments")
                    .sendJsonObject(createBody)
                    .compose(createResp -> webClient.get(HTTP_PORT, "localhost", "/api/v1/agents/" + agentId + "/jobs")
                            .send())
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonObject body = response.bodyAsJsonObject();
                        assertNotNull(body.getJsonArray("pendingJobs"));
                        assertNotNull(body.getInteger("total"));
                        JsonArray pendingJobs = body.getJsonArray("pendingJobs");
                        assertTrue(pendingJobs.size() > 0);

                        JsonObject job = pendingJobs.getJsonObject(0);
                        String assignmentId = job.getString("assignmentId");
                        assertEquals(jobId + ":" + agentId, assignmentId);
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("agent jobs endpoint excludes terminal assignments from pending list")
        void testAgentJobsExcludesTerminalAssignments(VertxTestContext ctx) {
            String agentId = "agent-terminal-test";
            String jobId = "job-terminal-test";

            JsonObject createBody = new JsonObject()
                    .put("jobId", jobId)
                    .put("agentId", agentId);

            webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments")
                    .sendJsonObject(createBody)
                    .compose(createResp -> {
                        String id = createResp.bodyAsJsonObject().getString("assignmentId");
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/reject")
                                .sendJsonObject(new JsonObject().put("reason", "declined"));
                    })
                    .compose(r -> webClient.get(HTTP_PORT, "localhost", "/api/v1/agents/" + agentId + "/jobs")
                            .send())
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonObject body = response.bodyAsJsonObject();
                        assertEquals(0, body.getInteger("total"));
                        assertEquals(0, body.getJsonArray("pendingJobs").size());
                        ctx.completeNow();
                    })));
        }
    }

    @Nested
    @DisplayName("Cross-endpoint input validation")
    class CrossEndpointInputValidation {

        @Test
        @DisplayName("agent registration with empty body returns 400 envelope")
        void testAgentRegistrationEmptyBodyReturns400(VertxTestContext ctx) {
            webClient.post(HTTP_PORT, "localhost", "/api/v1/agents/register")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(400, response.statusCode());
                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertNotNull(error);
                        assertEquals("VALIDATION_ERROR", error.getString("code"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("heartbeat with empty body returns 400 envelope")
        void testHeartbeatEmptyBodyReturns400(VertxTestContext ctx) {
            webClient.post(HTTP_PORT, "localhost", "/api/v1/agents/heartbeat")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(400, response.statusCode());
                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertNotNull(error);
                        assertEquals("VALIDATION_ERROR", error.getString("code"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("transfer creation with empty body returns 400 envelope")
        void testTransferCreateEmptyBodyReturns400(VertxTestContext ctx) {
            webClient.post(HTTP_PORT, "localhost", "/api/v1/transfers")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(400, response.statusCode());
                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertNotNull(error);
                        assertEquals("VALIDATION_ERROR", error.getString("code"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("job status update with empty body returns 400 envelope")
        void testJobStatusEmptyBodyReturns400(VertxTestContext ctx) {
            webClient.post(HTTP_PORT, "localhost", "/api/v1/jobs/job-empty-body/status")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(400, response.statusCode());
                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertNotNull(error);
                        assertEquals("VALIDATION_ERROR", error.getString("code"));
                        ctx.completeNow();
                    })));
        }
    }

    @Nested
    @DisplayName("Job status/transfer status regression")
    class JobAndTransferStatusRegression {

        @Test
        @DisplayName("job status endpoint returns standard 404 envelope for unknown assignment")
        void testJobStatusNotFoundReturnsEnvelope(VertxTestContext ctx) {
            JsonObject body = new JsonObject()
                    .put("agentId", "no-such-agent")
                    .put("status", "ACCEPTED");

            webClient.post(HTTP_PORT, "localhost", "/api/v1/jobs/no-such-job/status")
                    .sendJsonObject(body)
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(404, response.statusCode());
                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertNotNull(error);
                        assertEquals("ASSIGNMENT_NOT_FOUND", error.getString("code"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("job status invalid transition returns standard 409 envelope")
        void testJobStatusInvalidTransitionReturnsEnvelope(VertxTestContext ctx) {
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
                    .compose(r -> webClient.post(HTTP_PORT, "localhost", "/api/v1/jobs/" + jobId + "/status")
                            .sendJsonObject(new JsonObject().put("agentId", agentId).put("status", "COMPLETED")))
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(409, response.statusCode());
                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertNotNull(error);
                        assertEquals("ASSIGNMENT_STATE_CONFLICT", error.getString("code"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("transfer status reflects most recently active assignment")
        void testTransferStatusUsesLatestAssignmentActivity(VertxTestContext ctx) {
            String jobId = "job-latest-assignment-status";
            String agentA = "agent-latest-a";
            String agentB = "agent-latest-b";

            JsonObject transferBody = new JsonObject()
                    .put("jobId", jobId)
                    .put("sourceUri", "https://example.com/latest.csv")
                    .put("destinationPath", "/data/latest.csv");

            JsonObject assignA = new JsonObject().put("jobId", jobId).put("agentId", agentA);
            JsonObject assignB = new JsonObject().put("jobId", jobId).put("agentId", agentB);

            webClient.post(HTTP_PORT, "localhost", "/api/v1/transfers")
                    .sendJsonObject(transferBody)
                    .compose(r -> webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments").sendJsonObject(assignA))
                    .compose(r -> webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments").sendJsonObject(assignB))
                    .compose(r -> webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + jobId + ":" + agentA + "/status")
                            .sendJsonObject(new JsonObject().put("status", "ACCEPTED")))
                    .compose(r -> webClient.get(HTTP_PORT, "localhost", "/api/v1/transfers/" + jobId).send())
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        assertEquals("ACCEPTED", response.bodyAsJsonObject().getString("status"));
                        ctx.completeNow();
                    })));
        }
    }
}
