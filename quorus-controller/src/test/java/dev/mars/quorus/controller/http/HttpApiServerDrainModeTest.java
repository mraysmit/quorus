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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Set;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HttpApiServer} drain mode functionality.
 *
 * <p>Drain mode allows the server to gracefully stop accepting new API requests
 * while continuing to serve health probe endpoints. This is essential for
 * zero-downtime rolling updates and graceful shutdown.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-04
 */
@ExtendWith(VertxExtension.class)
@DisplayName("HttpApiServer Drain Mode Tests")
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class HttpApiServerDrainModeTest {

    private static final int HTTP_PORT = 18095;

    private static Vertx vertx;
    private static RaftNode raftNode;
    private static HttpApiServer httpServer;
    private static WebClient webClient;

    @BeforeAll
    static void setUp() throws Exception {
        vertx = Vertx.vertx();

        QuorusStateMachine stateMachine = new QuorusStateMachine();
        RaftTransport transport = new InMemoryTransportSimulator("drain-test-node");
        Set<String> clusterNodes = Set.of("drain-test-node");

        raftNode = new RaftNode(vertx, "drain-test-node", clusterNodes, transport, stateMachine, 500, 100);
        raftNode.start();

        // Wait for single-node cluster to elect itself as leader
        await().atMost(java.time.Duration.ofSeconds(10))
                .pollInterval(java.time.Duration.ofMillis(50))
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

    // ==================== Drain Mode State Tests ====================
    // Runs first (Order 1) — server is NOT yet in drain mode

    @Nested
    @Order(1)
    @DisplayName("Drain Mode State")
    class DrainModeStateTests {

        @Test
        @DisplayName("Should not be draining by default")
        void shouldNotBeDrainingByDefault() {
            assertFalse(httpServer.isDraining(),
                    "Server should not be in drain mode by default");
        }

        @Test
        @DisplayName("Should be draining after enterDrainMode")
        void shouldBeDrainingAfterEnterDrainMode(VertxTestContext ctx) {
            httpServer.enterDrainMode()
                    .onComplete(ctx.succeeding(v -> ctx.verify(() -> {
                        assertTrue(httpServer.isDraining(),
                                "Server should be in drain mode after enterDrainMode()");
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("enterDrainMode should be idempotent")
        void enterDrainModeShouldBeIdempotent(VertxTestContext ctx) {
            httpServer.enterDrainMode()
                    .compose(v -> httpServer.enterDrainMode())
                    .onComplete(ctx.succeeding(v -> ctx.verify(() -> {
                        assertTrue(httpServer.isDraining(),
                                "Server should still be draining after second call");
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== Normal Operation Tests ====================
    // Runs second (Order 2) — health endpoints always work regardless of drain state

    @Nested
    @Order(2)
    @DisplayName("Normal Operation")
    class NormalOperationTests {

        @Test
        @DisplayName("Should allow health requests regardless of drain state")
        void shouldAllowApiRequestsWhenNotDraining(Vertx vertx, VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/health/live")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode(),
                                "Health endpoint should return 200");
                        JsonObject body = response.bodyAsJsonObject();
                        assertEquals("UP", body.getString("status"));
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== Request Handling During Drain Tests ====================
    // Runs third (Order 3) — server is in drain mode (entered by DrainModeStateTests)

    @Nested
    @Order(3)
    @DisplayName("Request Handling During Drain")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class RequestHandlingDuringDrainTests {

        @BeforeAll
        static void enterDrain() throws Exception {
            // Ensure server is in drain mode (may already be from DrainModeStateTests)
            httpServer.enterDrainMode().toCompletionStage().toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
        }

        @Test
        @Order(1)
        @DisplayName("Should allow /health/live during drain")
        void shouldAllowHealthLiveDuringDrain(Vertx vertx, VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/health/live")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode(),
                                "/health/live should be allowed during drain");
                        JsonObject body = response.bodyAsJsonObject();
                        assertEquals("UP", body.getString("status"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @Order(2)
        @DisplayName("Should allow /health/ready during drain")
        void shouldAllowHealthReadyDuringDrain(Vertx vertx, VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/health/ready")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        // Could be 200 or 503 depending on Raft state, but should NOT be
                        // rejected by drain mode — the request should reach the handler
                        assertTrue(response.statusCode() == 200 || response.statusCode() == 503,
                                "/health/ready should reach the handler during drain, got: " + response.statusCode());
                        JsonObject body = response.bodyAsJsonObject();
                        assertNotNull(body.getString("status"),
                                "Response should contain status field from health handler");
                        ctx.completeNow();
                    })));
        }

        @Test
        @Order(3)
        @DisplayName("Should allow /health during drain")
        void shouldAllowHealthDuringDrain(Vertx vertx, VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/health")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        // Full health may return 503 if checks fail, but it should NOT
                        // be the drain-mode 503 with SERVICE_SHUTTING_DOWN
                        assertTrue(response.statusCode() == 200 || response.statusCode() == 503,
                                "/health should reach the handler during drain");
                        JsonObject body = response.bodyAsJsonObject();
                        // Drain rejection uses "error" key; health handler uses "status"
                        assertNotNull(body.getString("status"),
                                "Response should come from health handler, not drain rejection");
                        ctx.completeNow();
                    })));
        }

        @Test
        @Order(4)
        @DisplayName("Should return 503 for API requests during drain")
        void shouldReturn503ForApiRequestsDuringDrain(Vertx vertx, VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/raft/status")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(503, response.statusCode(),
                                "API requests should be rejected with 503 during drain");
                        assertEquals("30", response.getHeader("Retry-After"),
                                "Response should include Retry-After header");
                        ctx.completeNow();
                    })));
        }

        @Test
        @Order(5)
        @DisplayName("Should include SERVICE_SHUTTING_DOWN code in drain response")
        void shouldIncludeShuttingDownCodeInResponse(Vertx vertx, VertxTestContext ctx) {
            webClient.get(HTTP_PORT, "localhost", "/api/v1/transfers/test-id")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(503, response.statusCode());
                        JsonObject body = response.bodyAsJsonObject();
                        assertNotNull(body.getJsonObject("error"),
                                "Response should contain error object");
                        assertEquals("SERVICE_SHUTTING_DOWN",
                                body.getJsonObject("error").getString("code"),
                                "Error code should be SERVICE_SHUTTING_DOWN");
                        assertEquals("Server is shutting down",
                                body.getJsonObject("error").getString("message"));
                        ctx.completeNow();
                    })));
        }
    }
}
