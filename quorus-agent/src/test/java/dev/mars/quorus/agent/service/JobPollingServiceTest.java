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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JobPollingService.
 * Uses real HTTP server (no mocking) following project testing principles.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-05
 * @version 1.0
 */
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobPollingServiceTest {

    private HttpServer testServer;
    private int serverPort;
    private AgentConfiguration config;
    
    private AtomicInteger pollCount;
    private AtomicReference<JsonObject> responseBody;
    private AtomicInteger responseStatus;

    @BeforeAll
    void setUp(Vertx vertx, VertxTestContext testContext) {
        pollCount = new AtomicInteger(0);
        responseBody = new AtomicReference<>(new JsonObject().put("pendingJobs", new JsonArray()));
        responseStatus = new AtomicInteger(200);

        Router router = Router.router(vertx);

        // Job polling endpoint
        router.get("/agents/:agentId/jobs").handler(ctx -> {
            pollCount.incrementAndGet();
            
            int status = responseStatus.get();
            if (status == 200) {
                ctx.response()
                    .setStatusCode(200)
                    .putHeader("content-type", "application/json")
                    .end(responseBody.get().encode());
            } else {
                ctx.response()
                    .setStatusCode(status)
                    .end();
            }
        });

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(0)
            .onSuccess(server -> {
                testServer = server;
                serverPort = server.actualPort();
                
                config = new AgentConfiguration.Builder()
                    .agentId("test-agent-poll")
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
        pollCount.set(0);
        responseBody.set(new JsonObject().put("pendingJobs", new JsonArray()));
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
    @DisplayName("Should return empty list when no jobs pending")
    void testPollNoJobs(Vertx vertx, VertxTestContext testContext) {
        JobPollingService service = new JobPollingService(vertx, config);
        
        service.pollForJobs()
            .onComplete(testContext.succeeding(jobs -> {
                testContext.verify(() -> {
                    assertNotNull(jobs);
                    assertTrue(jobs.isEmpty(), "Should return empty list");
                    assertEquals(1, pollCount.get(), "One poll request should be made");
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should parse single pending job correctly")
    void testPollSingleJob(Vertx vertx, VertxTestContext testContext) {
        JsonArray jobs = new JsonArray()
            .add(new JsonObject()
                .put("assignmentId", "assign-001")
                .put("jobId", "job-001")
                .put("agentId", "test-agent-poll")
                .put("sourceUri", "https://example.com/file.txt")
                .put("destinationPath", "/data/file.txt")
                .put("totalBytes", 1024L)
                .put("description", "Test transfer"));
        
        responseBody.set(new JsonObject().put("pendingJobs", jobs));
        
        JobPollingService service = new JobPollingService(vertx, config);
        
        service.pollForJobs()
            .onComplete(testContext.succeeding(pendingJobs -> {
                testContext.verify(() -> {
                    assertEquals(1, pendingJobs.size(), "Should return one job");
                    
                    JobPollingService.PendingJob job = pendingJobs.get(0);
                    assertEquals("assign-001", job.getAssignmentId());
                    assertEquals("job-001", job.getJobId());
                    assertEquals("test-agent-poll", job.getAgentId());
                    assertEquals("https://example.com/file.txt", job.getSourceUri());
                    assertEquals("/data/file.txt", job.getDestinationPath());
                    assertEquals(1024L, job.getTotalBytes());
                    assertEquals("Test transfer", job.getDescription());
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should parse multiple pending jobs correctly")
    void testPollMultipleJobs(Vertx vertx, VertxTestContext testContext) {
        JsonArray jobs = new JsonArray()
            .add(new JsonObject()
                .put("assignmentId", "assign-001")
                .put("jobId", "job-001")
                .put("agentId", "test-agent-poll")
                .put("sourceUri", "https://example.com/file1.txt")
                .put("destinationPath", "/data/file1.txt")
                .put("totalBytes", 1024L))
            .add(new JsonObject()
                .put("assignmentId", "assign-002")
                .put("jobId", "job-002")
                .put("agentId", "test-agent-poll")
                .put("sourceUri", "https://example.com/file2.txt")
                .put("destinationPath", "/data/file2.txt")
                .put("totalBytes", 2048L))
            .add(new JsonObject()
                .put("assignmentId", "assign-003")
                .put("jobId", "job-003")
                .put("agentId", "test-agent-poll")
                .put("sourceUri", "https://example.com/file3.txt")
                .put("destinationPath", "/data/file3.txt")
                .put("totalBytes", 4096L));
        
        responseBody.set(new JsonObject().put("pendingJobs", jobs));
        
        JobPollingService service = new JobPollingService(vertx, config);
        
        service.pollForJobs()
            .onComplete(testContext.succeeding(pendingJobs -> {
                testContext.verify(() -> {
                    assertEquals(3, pendingJobs.size(), "Should return three jobs");
                    assertEquals("job-001", pendingJobs.get(0).getJobId());
                    assertEquals("job-002", pendingJobs.get(1).getJobId());
                    assertEquals("job-003", pendingJobs.get(2).getJobId());
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should handle malformed job JSON gracefully")
    void testPollMalformedJob(Vertx vertx, VertxTestContext testContext) {
        // Mix of valid and invalid jobs
        JsonArray jobs = new JsonArray()
            .add(new JsonObject()
                .put("assignmentId", "assign-001")
                .put("jobId", "job-001")
                .put("agentId", "test-agent-poll")
                .put("sourceUri", "https://example.com/file1.txt")
                .put("destinationPath", "/data/file1.txt"))
            .add("not-a-json-object") // Malformed entry
            .add(new JsonObject()
                .put("assignmentId", "assign-003")
                .put("jobId", "job-003")
                .put("agentId", "test-agent-poll")
                .put("sourceUri", "https://example.com/file3.txt")
                .put("destinationPath", "/data/file3.txt"));
        
        responseBody.set(new JsonObject().put("pendingJobs", jobs));
        
        JobPollingService service = new JobPollingService(vertx, config);
        
        service.pollForJobs()
            .onComplete(testContext.succeeding(pendingJobs -> {
                testContext.verify(() -> {
                    // Should skip malformed entry and return valid ones
                    assertEquals(2, pendingJobs.size(), "Should return two valid jobs");
                    assertEquals("job-001", pendingJobs.get(0).getJobId());
                    assertEquals("job-003", pendingJobs.get(1).getJobId());
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should return empty list on HTTP error")
    void testPollHttpError(Vertx vertx, VertxTestContext testContext) {
        responseStatus.set(500);
        
        JobPollingService service = new JobPollingService(vertx, config);
        
        service.pollForJobs()
            .onComplete(testContext.succeeding(jobs -> {
                testContext.verify(() -> {
                    assertNotNull(jobs);
                    assertTrue(jobs.isEmpty(), "Should return empty list on error");
                    assertEquals(1, pollCount.get(), "Request should still be made");
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should return empty list on HTTP 404")
    void testPollNotFound(Vertx vertx, VertxTestContext testContext) {
        responseStatus.set(404);
        
        JobPollingService service = new JobPollingService(vertx, config);
        
        service.pollForJobs()
            .onComplete(testContext.succeeding(jobs -> {
                testContext.verify(() -> {
                    assertTrue(jobs.isEmpty(), "Should return empty list on 404");
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should return empty list on connection error")
    void testPollConnectionError(Vertx vertx, VertxTestContext testContext) {
        AgentConfiguration badConfig = new AgentConfiguration.Builder()
            .agentId("test-agent-bad")
            .controllerUrl("http://localhost:59999") // Non-existent port
            .region("test-region")
            .datacenter("test-dc")
            .httpConnectionTimeout(1000)
            .build();
        
        JobPollingService service = new JobPollingService(vertx, badConfig);
        
        service.pollForJobs()
            .onComplete(testContext.succeeding(jobs -> {
                testContext.verify(() -> {
                    assertNotNull(jobs);
                    assertTrue(jobs.isEmpty(), "Should return empty list on connection error");
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should handle null pendingJobs array")
    void testPollNullArray(Vertx vertx, VertxTestContext testContext) {
        responseBody.set(new JsonObject()); // No pendingJobs field
        
        JobPollingService service = new JobPollingService(vertx, config);
        
        service.pollForJobs()
            .onComplete(testContext.succeeding(jobs -> {
                testContext.verify(() -> {
                    assertTrue(jobs.isEmpty(), "Should return empty list when array is null");
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should handle job with missing optional fields")
    void testPollJobWithMissingOptionalFields(Vertx vertx, VertxTestContext testContext) {
        JsonArray jobs = new JsonArray()
            .add(new JsonObject()
                .put("assignmentId", "assign-001")
                .put("jobId", "job-001")
                .put("agentId", "test-agent-poll")
                .put("sourceUri", "https://example.com/file.txt")
                .put("destinationPath", "/data/file.txt"));
                // Missing totalBytes and description
        
        responseBody.set(new JsonObject().put("pendingJobs", jobs));
        
        JobPollingService service = new JobPollingService(vertx, config);
        
        service.pollForJobs()
            .onComplete(testContext.succeeding(pendingJobs -> {
                testContext.verify(() -> {
                    assertEquals(1, pendingJobs.size());
                    JobPollingService.PendingJob job = pendingJobs.get(0);
                    assertEquals(0L, job.getTotalBytes(), "Missing totalBytes should default to 0");
                    assertNull(job.getDescription(), "Missing description should be null");
                });
                testContext.completeNow();
            }));
    }

    @Test
    @DisplayName("Should shutdown gracefully")
    void testShutdown(Vertx vertx, VertxTestContext testContext) {
        JobPollingService service = new JobPollingService(vertx, config);
        
        service.shutdown()
            .onComplete(testContext.succeeding(v -> {
                testContext.completeNow();
            }));
    }
}
