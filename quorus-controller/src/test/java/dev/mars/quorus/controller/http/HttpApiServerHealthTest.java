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
import dev.mars.quorus.controller.state.QuorusStateMachine;
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
 * Dedicated tests for HTTP API health endpoints.
 *
 * <p>Tests all three health probe endpoints:
 * <ul>
 *   <li>{@code GET /health/live} — Liveness (always UP)</li>
 *   <li>{@code GET /health/ready} — Readiness (Raft running + leader elected)</li>
 *   <li>{@code GET /health} — Full health with Raft state, disk, memory checks</li>
 * </ul>
 *
 * <p>Uses a real single-node Raft cluster (no mocking) for accurate behavior testing.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-13
 */
@ExtendWith(VertxExtension.class)
@DisplayName("HttpApiServer Health Endpoint Tests")
class HttpApiServerHealthTest {

    private static final int HTTP_PORT = 18096;

    private static Vertx vertx;
    private static RaftNode raftNode;
    private static HttpApiServer httpServer;
    private static WebClient webClient;

    @BeforeAll
    static void setUp() throws Exception {
        vertx = Vertx.vertx();

        QuorusStateMachine stateMachine = new QuorusStateMachine();
        RaftTransport transport = new InMemoryTransportSimulator("health-test-node");
        Set<String> clusterNodes = Set.of("health-test-node");

        raftNode = new RaftNode(vertx, "health-test-node", clusterNodes, transport, stateMachine, 500, 100);
        raftNode.start();

        // Wait for single-node cluster to elect itself as leader
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
    }

    // ==================== Liveness Probe Tests ====================

    @Nested
    @DisplayName("GET /health/live")
    class LivenessTests {

        @Test
        @DisplayName("Should always return 200 with status UP")
        void shouldReturnUp(VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/health/live")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonObject body = response.bodyAsJsonObject();
                        assertEquals("UP", body.getString("status"));
                        assertNotNull(body.getString("timestamp"),
                                "Response should include timestamp");
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("Should include X-Request-ID header in response")
        void shouldIncludeRequestId(VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/health/live")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertNotNull(response.getHeader("X-Request-ID"),
                                "Response should include X-Request-ID header");
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("Should echo client-provided X-Request-ID")
        void shouldEchoClientRequestId(VertxTestContext ctx) {
            String clientRequestId = "test-req-12345";
            webClient.get(HTTP_PORT, "localhost", "/health/live")
                    .putHeader("X-Request-ID", clientRequestId)
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(clientRequestId, response.getHeader("X-Request-ID"),
                                "Should echo back client-provided request ID");
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== Readiness Probe Tests ====================

    @Nested
    @DisplayName("GET /health/ready")
    class ReadinessTests {

        @Test
        @DisplayName("Should return 200 UP when Raft is running and leader elected")
        void shouldReturnUpWhenReady(VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/health/ready")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonObject body = response.bodyAsJsonObject();
                        assertEquals("UP", body.getString("status"));

                        JsonObject checks = body.getJsonObject("checks");
                        assertNotNull(checks, "Response should include checks");
                        assertEquals("UP", checks.getString("raftRunning"));
                        assertEquals("UP", checks.getString("clusterHasLeader"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("Should include timestamp in response")
        void shouldIncludeTimestamp(VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/health/ready")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        JsonObject body = response.bodyAsJsonObject();
                        assertNotNull(body.getString("timestamp"),
                                "Response should include timestamp");
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== Full Health Check Tests ====================

    @Nested
    @DisplayName("GET /health")
    class FullHealthTests {

        @Test
        @DisplayName("Should return comprehensive health with all required fields")
        void shouldReturnFullHealth(VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/health")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonObject body = response.bodyAsJsonObject();

                        // Top-level fields
                        assertEquals("UP", body.getString("status"));
                        assertNotNull(body.getString("version"), "Should include version");
                        assertNotNull(body.getString("timestamp"), "Should include timestamp");
                        assertEquals("health-test-node", body.getString("nodeId"),
                                "Should include correct nodeId");

                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("Should include Raft state with term and commitIndex")
        void shouldIncludeRaftState(VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/health")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        JsonObject body = response.bodyAsJsonObject();
                        JsonObject raft = body.getJsonObject("raft");
                        assertNotNull(raft, "Response should include raft section");

                        assertEquals("LEADER", raft.getString("state"),
                                "Single-node cluster should be LEADER");
                        assertNotNull(raft.getLong("term"),
                                "Should include term");
                        assertNotNull(raft.getLong("commitIndex"),
                                "Should include commitIndex");
                        assertTrue(raft.getBoolean("isLeader"),
                                "Should report isLeader=true");

                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("Should include dependency checks (raft, disk, memory)")
        void shouldIncludeDependencyChecks(VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/health")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        JsonObject body = response.bodyAsJsonObject();
                        JsonObject checks = body.getJsonObject("checks");
                        assertNotNull(checks, "Response should include checks section");

                        assertNotNull(checks.getString("raftCluster"),
                                "Should include raftCluster check");
                        assertNotNull(checks.getString("diskSpace"),
                                "Should include diskSpace check");
                        assertNotNull(checks.getString("memory"),
                                "Should include memory check");

                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("Should include version string")
        void shouldIncludeVersion(VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/health")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        JsonObject body = response.bodyAsJsonObject();
                        String version = body.getString("version");
                        assertNotNull(version, "Should include version");
                        assertFalse(version.isBlank(), "Version should not be blank");
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("Should include X-Request-ID in response")
        void shouldIncludeCorrelationId(VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/health")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        String requestId = response.getHeader("X-Request-ID");
                        assertNotNull(requestId, "Should include X-Request-ID header");
                        assertTrue(requestId.startsWith("req-"),
                                "Auto-generated request ID should start with 'req-'");
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== Error Response Correlation Tests ====================

    @Nested
    @DisplayName("Error Response Correlation ID Propagation")
    class ErrorCorrelationTests {

        @Test
        @DisplayName("Should propagate client X-Request-ID through error responses")
        void shouldPropagateRequestIdInErrorResponse(VertxTestContext ctx) {
            String clientRequestId = "trace-error-abc123";
            // Hit a valid route with a non-existent resource to trigger GlobalErrorHandler
            webClient.get(HTTP_PORT, "localhost", "/api/v1/transfers/nonexistent-job-xyz")
                    .putHeader("X-Request-ID", clientRequestId)
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        // Should get a 404 error from QuorusApiException.notFound
                        assertEquals(404, response.statusCode());

                        // Response header should echo the client request ID
                        assertEquals(clientRequestId, response.getHeader("X-Request-ID"),
                                "Response header should echo client X-Request-ID");

                        // Error JSON body should contain the same requestId
                        JsonObject body = response.bodyAsJsonObject();
                        assertNotNull(body, "Error response body should be JSON");
                        JsonObject error = body.getJsonObject("error");
                        assertNotNull(error, "Error body should contain 'error' object");
                        assertEquals(clientRequestId, error.getString("requestId"),
                                "Error JSON requestId must match client X-Request-ID");
                        assertEquals("TRANSFER_NOT_FOUND", error.getString("code"),
                                "Error code should be TRANSFER_NOT_FOUND");

                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("Should auto-generate requestId in error responses when no header sent")
        void shouldAutoGenerateRequestIdInErrorResponse(VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/api/v1/transfers/nonexistent-job-xyz")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(404, response.statusCode());

                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertNotNull(error, "Error body should contain 'error' object");
                        String requestId = error.getString("requestId");
                        assertNotNull(requestId, "Auto-generated requestId should be present");
                        assertTrue(requestId.startsWith("req-"),
                                "Auto-generated requestId should start with 'req-'");

                        // Header and JSON requestId must match
                        assertEquals(requestId, response.getHeader("X-Request-ID"),
                                "Header and JSON requestId must match");

                        ctx.completeNow();
                    })));
        }
    }
}
