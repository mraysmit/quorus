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
 * Unit tests for JobStatusReportingService.
 * Uses real HTTP server (no mocking) following project testing principles.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-05
 * @version 1.0
 */
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobStatusReportingServiceTest {

    private HttpServer testServer;
    private int serverPort;
    private AgentConfiguration config;
    
    private AtomicInteger reportCount;
    private AtomicReference<String> lastJobId;
    private AtomicReference<JsonObject> lastRequest;
    private AtomicInteger responseStatus;

    @BeforeAll
    void setUp(Vertx vertx, VertxTestContext testContext) {
        reportCount = new AtomicInteger(0);
        lastJobId = new AtomicReference<>();
        lastRequest = new AtomicReference<>();
        responseStatus = new AtomicInteger(200);

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // Status reporting endpoint
        router.post("/jobs/:jobId/status").handler(ctx -> {
            reportCount.incrementAndGet();
            lastJobId.set(ctx.pathParam("jobId"));
            lastRequest.set(ctx.body().asJsonObject());
            
            int status = responseStatus.get();
            ctx.response()
                .setStatusCode(status)
                .putHeader("content-type", "application/json")
                .end(new JsonObject().put("status", "received").encode());
        });

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(0)
            .onSuccess(server -> {
                testServer = server;
                serverPort = server.actualPort();
                
                config = new AgentConfiguration.Builder()
                    .agentId("test-agent-status")
                    .controllerUrl("http://localhost:" + serverPort)
                    .region("test-region")
                    .datacenter("test-dc")
                    .build();
                
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }

    @BeforeEach
    void resetCounters() {
        reportCount.set(0);
        lastJobId.set(null);
        lastRequest.set(null);
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
    @DisplayName("Should report ACCEPTED status")
    void testReportAccepted(Vertx vertx, VertxTestContext testContext) {
        JobStatusReportingService service = new JobStatusReportingService(vertx, config);
        
        service.reportAccepted("job-123")
            .onComplete(testContext.succeeding(v -> {
                testContext.verify(() -> {
                    assertEquals(1, reportCount.get(), "One report should be sent");
                    assertEquals("job-123", lastJobId.get());
                    
                    JsonObject request = lastRequest.get();
                    assertEquals("test-agent-status", request.getString("agentId"));
                    assertEquals("ACCEPTED", request.getString("status"));
                    assertNull(request.getLong("bytesTransferred"));
                    assertNull(request.getString("errorMessage"));
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should report IN_PROGRESS status with bytes transferred")
    void testReportInProgress(Vertx vertx, VertxTestContext testContext) {
        JobStatusReportingService service = new JobStatusReportingService(vertx, config);
        
        service.reportInProgress("job-456", 512000L)
            .onComplete(testContext.succeeding(v -> {
                testContext.verify(() -> {
                    assertEquals(1, reportCount.get());
                    assertEquals("job-456", lastJobId.get());
                    
                    JsonObject request = lastRequest.get();
                    assertEquals("IN_PROGRESS", request.getString("status"));
                    assertEquals(512000L, request.getLong("bytesTransferred").longValue());
                    assertNull(request.getString("errorMessage"));
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should report COMPLETED status with bytes transferred")
    void testReportCompleted(Vertx vertx, VertxTestContext testContext) {
        JobStatusReportingService service = new JobStatusReportingService(vertx, config);
        
        service.reportCompleted("job-789", 1048576L)
            .onComplete(testContext.succeeding(v -> {
                testContext.verify(() -> {
                    assertEquals(1, reportCount.get());
                    assertEquals("job-789", lastJobId.get());
                    
                    JsonObject request = lastRequest.get();
                    assertEquals("COMPLETED", request.getString("status"));
                    assertEquals(1048576L, request.getLong("bytesTransferred").longValue());
                    assertNull(request.getString("errorMessage"));
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should report FAILED status with error message")
    void testReportFailed(Vertx vertx, VertxTestContext testContext) {
        JobStatusReportingService service = new JobStatusReportingService(vertx, config);
        
        service.reportFailed("job-err", "Connection timeout")
            .onComplete(testContext.succeeding(v -> {
                testContext.verify(() -> {
                    assertEquals(1, reportCount.get());
                    assertEquals("job-err", lastJobId.get());
                    
                    JsonObject request = lastRequest.get();
                    assertEquals("FAILED", request.getString("status"));
                    assertEquals("Connection timeout", request.getString("errorMessage"));
                    assertNull(request.getLong("bytesTransferred"));
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should handle HTTP error gracefully (no exception)")
    void testHttpErrorGraceful(Vertx vertx, VertxTestContext testContext) {
        responseStatus.set(500);
        
        JobStatusReportingService service = new JobStatusReportingService(vertx, config);
        
        service.reportCompleted("job-fail", 1000L)
            .onComplete(testContext.succeeding(v -> {
                testContext.verify(() -> {
                    // Should complete without exception even on HTTP 500
                    assertEquals(1, reportCount.get(), "Request should still be made");
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should handle HTTP 404 gracefully")
    void testHttp404Graceful(Vertx vertx, VertxTestContext testContext) {
        responseStatus.set(404);
        
        JobStatusReportingService service = new JobStatusReportingService(vertx, config);
        
        service.reportFailed("nonexistent-job", "Some error")
            .onComplete(testContext.succeeding(v -> {
                testContext.verify(() -> {
                    assertEquals(1, reportCount.get());
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should handle connection error gracefully")
    void testConnectionErrorGraceful(Vertx vertx, VertxTestContext testContext) {
        AgentConfiguration badConfig = new AgentConfiguration.Builder()
            .agentId("test-agent-bad")
            .controllerUrl("http://localhost:59999") // Non-existent port
            .region("test-region")
            .datacenter("test-dc")
            .httpConnectionTimeout(1000)
            .build();
        
        JobStatusReportingService service = new JobStatusReportingService(vertx, badConfig);
        
        service.reportCompleted("job-conn-err", 500L)
            .onComplete(testContext.succeeding(v -> {
                // Should complete without exception
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should send multiple status reports for same job")
    void testMultipleReportsForSameJob(Vertx vertx, VertxTestContext testContext) {
        JobStatusReportingService service = new JobStatusReportingService(vertx, config);
        
        service.reportAccepted("job-multi")
            .compose(v -> service.reportInProgress("job-multi", 500L))
            .compose(v -> service.reportInProgress("job-multi", 1000L))
            .compose(v -> service.reportCompleted("job-multi", 1500L))
            .onComplete(testContext.succeeding(v -> {
                testContext.verify(() -> {
                    assertEquals(4, reportCount.get(), "Should send 4 status reports");
                    assertEquals("job-multi", lastJobId.get());
                    assertEquals("COMPLETED", lastRequest.get().getString("status"));
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should include agent ID in all requests")
    void testAgentIdIncluded(Vertx vertx, VertxTestContext testContext) {
        JobStatusReportingService service = new JobStatusReportingService(vertx, config);
        
        service.reportAccepted("job-agent")
            .compose(v -> {
                assertEquals("test-agent-status", lastRequest.get().getString("agentId"));
                return service.reportInProgress("job-agent", 100L);
            })
            .compose(v -> {
                assertEquals("test-agent-status", lastRequest.get().getString("agentId"));
                return service.reportFailed("job-agent", "error");
            })
            .onComplete(testContext.succeeding(v -> {
                testContext.verify(() -> {
                    assertEquals("test-agent-status", lastRequest.get().getString("agentId"));
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should shutdown gracefully")
    void testShutdown(Vertx vertx, VertxTestContext testContext) {
        JobStatusReportingService service = new JobStatusReportingService(vertx, config);
        
        service.shutdown()
            .onComplete(testContext.succeeding(v -> {
                testContext.completeNow();
            }));
    }
}
