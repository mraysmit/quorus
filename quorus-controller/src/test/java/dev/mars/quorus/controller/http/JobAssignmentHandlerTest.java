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
import dev.mars.quorus.controller.raft.RaftTransport;
import dev.mars.quorus.controller.state.QuorusStateStore;
import io.vertx.core.Vertx;
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

        raftNode = new RaftNode(vertx, "assign-test-node", clusterNodes, transport, stateMachine, 500, 100);
        raftNode.start();

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> raftNode.isLeader());

        httpServer = new HttpApiServer(vertx, HTTP_PORT, raftNode);
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

            webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments")
                    .sendJsonObject(createBody)
                    .compose(createResp -> {
                        String id = createResp.bodyAsJsonObject().getString("assignmentId");
                        JsonObject statusBody = new JsonObject().put("status", "IN_PROGRESS");
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/status")
                                .sendJsonObject(statusBody);
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonObject json = response.bodyAsJsonObject();
                        assertEquals("IN_PROGRESS", json.getString("status"));
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
}
