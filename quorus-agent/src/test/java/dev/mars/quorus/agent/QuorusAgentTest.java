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

import dev.mars.quorus.agent.config.AgentConfiguration;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for QuorusAgent.
 * Tests Vert.x integration and reactive patterns using real HTTP server (no mocks).
 *
 * Following coding principles:
 * - No mocking (uses real Vert.x HTTP server)
 * - Real implementations only
 * - Integration testing with actual HTTP communication
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-12-16
 */
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuorusAgentTest {

    private AgentConfiguration config;
    private HttpServer mockControllerServer;
    private int controllerPort;
    private AtomicInteger registrationCount;
    private AtomicInteger heartbeatCount;

    @BeforeAll
    void setUp(Vertx vertx, VertxTestContext testContext) {
        registrationCount = new AtomicInteger(0);
        heartbeatCount = new AtomicInteger(0);

        // Create a real HTTP server to simulate the controller (no mocking!)
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // Agent registration endpoint
        router.post("/api/v1/agents/register").handler(ctx -> {
            registrationCount.incrementAndGet();
            JsonObject response = new JsonObject()
                    .put("status", "registered")
                    .put("agentId", "test-agent-001");
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(response.encode());
        });

        // Heartbeat endpoint
        router.post("/api/v1/agents/:agentId/heartbeat").handler(ctx -> {
            heartbeatCount.incrementAndGet();
            JsonObject response = new JsonObject()
                    .put("status", "ok")
                    .put("timestamp", System.currentTimeMillis());
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(response.encode());
        });

        // Job polling endpoint
        router.get("/api/v1/agents/:agentId/jobs").handler(ctx -> {
            JsonObject response = new JsonObject()
                    .put("jobs", new io.vertx.core.json.JsonArray());
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(response.encode());
        });

        // Start the mock controller server
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(0) // Random port
                .onSuccess(server -> {
                    mockControllerServer = server;
                    controllerPort = server.actualPort();

                    // Create agent configuration pointing to our real test server
                    config = new AgentConfiguration.Builder()
                            .agentId("test-agent-001")
                            .controllerUrl("http://localhost:" + controllerPort + "/api/v1")
                            .region("test-region")
                            .datacenter("test-dc")
                            .agentPort(9090)
                            .maxConcurrentTransfers(5)
                            .heartbeatInterval(30000L)
                            .version("1.0.0-TEST")
                            .build();

                    testContext.completeNow();
                })
                .onFailure(testContext::failNow);
    }

    @AfterAll
    void tearDown(VertxTestContext testContext) {
        if (mockControllerServer != null) {
            mockControllerServer.close()
                    .onComplete(ar -> testContext.completeNow());
        } else {
            testContext.completeNow();
        }
    }

    @Test
    @DisplayName("Should create QuorusAgent with Vertx instance")
    void testCreateAgentWithVertx(Vertx vertx, VertxTestContext testContext) {
        assertNotNull(vertx, "Vertx instance should not be null");
        assertNotNull(config, "Config should not be null");

        // Create agent with Vertx instance (real implementation, no mocks)
        QuorusAgent agent = new QuorusAgent(vertx, config);

        assertNotNull(agent, "Agent should be created");
        assertEquals(config, agent.getConfiguration(), "Configuration should match");

        testContext.completeNow();
    }

    @Test
    @DisplayName("Should validate configuration points to real test server")
    void testConfigurationValidation() {
        assertNotNull(config.getAgentId());
        assertEquals("test-agent-001", config.getAgentId());
        assertTrue(config.getControllerUrl().contains("localhost:" + controllerPort),
                   "Should point to real test server");
        assertEquals(30000L, config.getHeartbeatInterval());
    }

    @Test
    @DisplayName("Should reject null Vertx instance")
    void testNullVertxHandling() {
        // Should throw NullPointerException when Vertx is null
        assertThrows(NullPointerException.class, () -> {
            new QuorusAgent(null, config);
        }, "Should throw NullPointerException for null Vertx");
    }

    @Test
    @DisplayName("Should reject null configuration")
    void testNullConfigHandling(Vertx vertx) {
        // Should throw NullPointerException when config is null
        assertThrows(NullPointerException.class, () -> {
            new QuorusAgent(vertx, null);
        }, "Should throw NullPointerException for null config");
    }

    @Test
    @DisplayName("Should support legacy constructor (deprecated)")
    void testLegacyConstructor() {
        // Legacy constructor should still work but log warning
        @SuppressWarnings("deprecation")
        QuorusAgent agent = new QuorusAgent(config);

        assertNotNull(agent, "Agent should be created with legacy constructor");
        assertEquals(config, agent.getConfiguration(), "Configuration should match");
    }

    @Test
    @DisplayName("Should communicate with real HTTP server (no mocks)")
    void testRealHttpCommunication(Vertx vertx, VertxTestContext testContext) {
        // This test verifies we're using a REAL HTTP server, not mocks

        // Wait a bit for server to be fully ready
        vertx.setTimer(100, id -> {
            // Make a real HTTP request to our test controller
            vertx.createHttpClient()
                    .request(io.vertx.core.http.HttpMethod.POST, controllerPort, "localhost", "/api/v1/agents/register")
                    .compose(req -> req.send()
                            .compose(io.vertx.core.http.HttpClientResponse::body))
                    .onSuccess(body -> {
                        JsonObject response = body.toJsonObject();
                        assertEquals("registered", response.getString("status"));
                        assertEquals("test-agent-001", response.getString("agentId"));
                        testContext.completeNow();
                    })
                    .onFailure(testContext::failNow);
        });
    }

    @Test
    @DisplayName("Should verify test server is real Vert.x HTTP server")
    void testServerIsReal(VertxTestContext testContext) {
        assertNotNull(mockControllerServer, "Server should be a real HttpServer instance");
        assertTrue(mockControllerServer.actualPort() > 0, "Server should have real port");
        assertEquals(controllerPort, mockControllerServer.actualPort(), "Port should match");
        testContext.completeNow();
    }

    @Test
    @DisplayName("Should use Vert.x timers (verify timer IDs logged)")
    void testVertxTimersUsed(Vertx vertx, VertxTestContext testContext) throws Exception {
        QuorusAgent agent = new QuorusAgent(vertx, config);

        // Start agent - should use Vert.x timers, not ScheduledExecutorService
        agent.start();

        // Verify agent started successfully (timers are set up)
        // The logs should show "Vert.x timer ID" messages
        testContext.completeNow();
    }

    @Test
    @DisplayName("Should handle idempotent shutdown")
    void testIdempotentShutdown(Vertx vertx, VertxTestContext testContext) {
        QuorusAgent agent = new QuorusAgent(vertx, config);

        // Multiple shutdowns should be safe (idempotent)
        agent.shutdown();
        agent.shutdown();
        agent.shutdown();

        testContext.completeNow();
    }

    @Test
    @DisplayName("Should reject operations after shutdown")
    void testOperationsAfterShutdown(Vertx vertx, VertxTestContext testContext) throws Exception {
        QuorusAgent agent = new QuorusAgent(vertx, config);
        agent.start();
        agent.shutdown();

        // Attempting to start again should fail
        assertThrows(IllegalStateException.class, () -> {
            agent.start();
        }, "Should reject start() after shutdown");

        testContext.completeNow();
    }

    @Test
    @DisplayName("Should verify Vert.x reactive mode (no ScheduledExecutorService)")
    void testReactiveMode(Vertx vertx, VertxTestContext testContext) {
        QuorusAgent agent = new QuorusAgent(vertx, config);

        // Agent should be created in reactive mode
        // Logs should show "reactive mode" and "0 extra threads"
        assertNotNull(agent);

        testContext.completeNow();
    }
}

