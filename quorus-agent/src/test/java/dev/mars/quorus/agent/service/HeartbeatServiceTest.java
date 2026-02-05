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

package dev.mars.quorus.agent.service;

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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HeartbeatService.
 * Uses real HTTP server (no mocking) following project testing principles.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-05
 * @version 1.0
 */
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HeartbeatServiceTest {

    private HttpServer testServer;
    private int serverPort;
    private AgentConfiguration config;
    private AgentRegistrationService registrationService;
    
    private AtomicInteger heartbeatCount;
    private AtomicReference<JsonObject> lastHeartbeatRequest;
    private AtomicInteger responseStatus;

    @BeforeAll
    void setUp(Vertx vertx, VertxTestContext testContext) {
        heartbeatCount = new AtomicInteger(0);
        lastHeartbeatRequest = new AtomicReference<>();
        responseStatus = new AtomicInteger(200);

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // Heartbeat endpoint
        router.post("/agents/heartbeat").handler(ctx -> {
            heartbeatCount.incrementAndGet();
            lastHeartbeatRequest.set(ctx.body().asJsonObject());
            
            int status = responseStatus.get();
            if (status == 200) {
                ctx.response()
                    .setStatusCode(200)
                    .putHeader("content-type", "application/json")
                    .end(new JsonObject().put("status", "ok").encode());
            } else {
                ctx.response()
                    .setStatusCode(status)
                    .end();
            }
        });

        // Registration endpoint (for AgentRegistrationService)
        router.post("/agents/register").handler(ctx -> {
            ctx.response()
                .setStatusCode(201)
                .putHeader("content-type", "application/json")
                .end(new JsonObject().put("status", "registered").encode());
        });

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(0)
            .onSuccess(server -> {
                testServer = server;
                serverPort = server.actualPort();
                
                config = new AgentConfiguration.Builder()
                    .agentId("test-agent-hb")
                    .controllerUrl("http://localhost:" + serverPort)
                    .region("test-region")
                    .datacenter("test-dc")
                    .maxConcurrentTransfers(5)
                    .build();
                
                registrationService = new AgentRegistrationService(vertx, config);
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }

    @BeforeEach
    void resetCounters() {
        heartbeatCount.set(0);
        lastHeartbeatRequest.set(null);
        responseStatus.set(200);
    }

    @AfterAll
    void tearDown(VertxTestContext testContext) {
        if (testServer != null) {
            testServer.close().onComplete(ar -> testContext.completeNow());
        } else {
            testContext.completeNow();
        }
    }

    @Test
    @DisplayName("Should return false when agent not registered")
    void testHeartbeatWhenNotRegistered(Vertx vertx, VertxTestContext testContext) {
        // Create a fresh registrationService that is NOT registered
        AgentRegistrationService freshRegistrationService = new AgentRegistrationService(vertx, config);
        assertFalse(freshRegistrationService.isRegistered(), "Fresh service should not be registered");
        
        HeartbeatService service = new HeartbeatService(vertx, config, freshRegistrationService);
        
        service.sendHeartbeat()
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertFalse(result, "Should return false when not registered");
                    assertEquals(0, heartbeatCount.get(), "No heartbeat should be sent");
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should send heartbeat successfully when registered")
    void testHeartbeatWhenRegistered(Vertx vertx, VertxTestContext testContext) {
        // First register the agent
        registrationService.register()
            .compose(registered -> {
                assertTrue(registered, "Registration should succeed");
                
                HeartbeatService service = new HeartbeatService(vertx, config, registrationService);
                return service.sendHeartbeat();
            })
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertTrue(result, "Heartbeat should succeed");
                    assertEquals(1, heartbeatCount.get(), "One heartbeat should be sent");
                    
                    JsonObject request = lastHeartbeatRequest.get();
                    assertNotNull(request, "Request body should be captured");
                    assertEquals("test-agent-hb", request.getString("agentId"));
                    assertNotNull(request.getString("timestamp"));
                    assertEquals(1, request.getLong("sequenceNumber").intValue());
                    assertEquals("active", request.getString("status"));
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should increment sequence number on each heartbeat")
    void testSequenceNumberIncrement(Vertx vertx, VertxTestContext testContext) {
        registrationService.register()
            .compose(registered -> {
                HeartbeatService service = new HeartbeatService(vertx, config, registrationService);
                return service.sendHeartbeat()
                    .compose(r1 -> service.sendHeartbeat())
                    .compose(r2 -> service.sendHeartbeat());
            })
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertEquals(3, heartbeatCount.get(), "Three heartbeats should be sent");
                    JsonObject lastRequest = lastHeartbeatRequest.get();
                    assertEquals(3, lastRequest.getLong("sequenceNumber").intValue(), 
                        "Sequence number should be 3");
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should return false on HTTP error response")
    void testHeartbeatHttpError(Vertx vertx, VertxTestContext testContext) {
        responseStatus.set(500);
        
        registrationService.register()
            .compose(registered -> {
                HeartbeatService service = new HeartbeatService(vertx, config, registrationService);
                return service.sendHeartbeat();
            })
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertFalse(result, "Should return false on HTTP 500");
                    assertEquals(1, heartbeatCount.get(), "Request should still be made");
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should return false on connection error")
    void testHeartbeatConnectionError(Vertx vertx, VertxTestContext testContext) {
        // Configure to connect to non-existent server
        AgentConfiguration badConfig = new AgentConfiguration.Builder()
            .agentId("test-agent-bad")
            .controllerUrl("http://localhost:59999") // Non-existent port
            .region("test-region")
            .datacenter("test-dc")
            .httpConnectionTimeout(1000)
            .build();
        
        // Create a fake registration service that says it's registered
        AgentRegistrationService fakeRegService = new AgentRegistrationService(vertx, badConfig) {
            @Override
            public boolean isRegistered() {
                return true;
            }
        };
        
        HeartbeatService service = new HeartbeatService(vertx, badConfig, fakeRegService);
        
        service.sendHeartbeat()
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertFalse(result, "Should return false on connection error");
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should include metrics in heartbeat request")
    void testHeartbeatIncludesMetrics(Vertx vertx, VertxTestContext testContext) {
        registrationService.register()
            .compose(registered -> {
                HeartbeatService service = new HeartbeatService(vertx, config, registrationService);
                return service.sendHeartbeat();
            })
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    JsonObject request = lastHeartbeatRequest.get();
                    JsonObject metrics = request.getJsonObject("metrics");
                    
                    assertNotNull(metrics, "Metrics should be included");
                    assertNotNull(metrics.getLong("memoryUsed"));
                    assertNotNull(metrics.getLong("memoryTotal"));
                    assertNotNull(metrics.getLong("memoryMax"));
                    assertNotNull(metrics.getInteger("cpuCores"));
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should shutdown gracefully")
    void testShutdown(Vertx vertx, VertxTestContext testContext) {
        HeartbeatService service = new HeartbeatService(vertx, config, registrationService);
        
        service.shutdown()
            .onComplete(testContext.succeeding(v -> {
                testContext.completeNow();
            }));
    }
}
