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

import dev.mars.quorus.controller.http.HttpApiServer;
import dev.mars.quorus.controller.raft.InMemoryTransportSimulator;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftNodeMode;
import dev.mars.quorus.controller.raft.RaftTransport;
import dev.mars.quorus.controller.state.QuorusStateStore;
import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 10 integration tests: proves that invalid state transitions are rejected
 * with 409 Conflict through the full HTTP → Raft → state machine path.
 *
 * <p>These tests exercise the complete request lifecycle — no mocks, no shortcuts.
 * A real single-node Raft cluster processes every command. Pre-commit validation
 * in handlers fast-rejects illegal transitions before they reach the Raft log.</p>
 *
 * <p>Domains tested:
 * <ul>
 *   <li><b>JobAssignment</b> — {@code canTransitionTo()} pre-commit + CAS in apply()</li>
 *   <li><b>Route</b> — explicit status guards in suspend/resume/update/delete handlers</li>
 * </ul>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-25
 * @see dev.mars.quorus.controller.http.handlers.JobAssignmentHandler
 * @see dev.mars.quorus.controller.http.handlers.RouteHandler
 */
@ExtendWith(VertxExtension.class)
@DisplayName("State Transition Integration Tests (Phase 10)")
class StateTransitionIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(StateTransitionIntegrationTest.class);
    private static final int HTTP_PORT = 18098;
    private static final AtomicInteger counter = new AtomicInteger(0);

    private static Vertx vertx;
    private static RaftNode raftNode;
    private static HttpApiServer httpServer;
    private static WebClient webClient;

    @BeforeAll
    static void setUp() throws Exception {
        vertx = Vertx.vertx();

        QuorusStateStore stateMachine = new QuorusStateStore();
        RaftTransport transport = new InMemoryTransportSimulator("transition-test-node");
        Set<String> clusterNodes = Set.of("transition-test-node");

        raftNode = RaftNode.builder()
                .vertx(vertx)
                .nodeId("transition-test-node")
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
        awaitSuccess(httpServer.start(), Duration.ofSeconds(5));

        webClient = WebClient.create(vertx);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (webClient != null) webClient.close();
        if (httpServer != null) awaitSuccess(httpServer.stop(), Duration.ofSeconds(5));
        if (raftNode != null) raftNode.stop();
        if (vertx != null) awaitSuccess(vertx.close(), Duration.ofSeconds(5));
        InMemoryTransportSimulator.clearAllTransports();
    }

    private static <T> T awaitSuccess(io.vertx.core.Future<T> future, Duration timeout) {
        AtomicReference<AsyncResult<T>> outcomeRef = new AtomicReference<>();

        future.onComplete(outcomeRef::set);

        await().atMost(timeout)
                .pollInterval(Duration.ofMillis(10))
                .until(() -> outcomeRef.get() != null);

        AsyncResult<T> outcome = outcomeRef.get();
        if (outcome.failed()) {
            throw new AssertionError("Future failed", outcome.cause());
        }
        return outcome.result();
    }

    /** Generates unique IDs to avoid cross-test interference. */
    private static String uniqueId(String prefix) {
        return prefix + "-" + counter.incrementAndGet();
    }

    // ==================== JobAssignment: Terminal State Rejection ====================

    @Nested
    @DisplayName("JobAssignment — Terminal State Transitions")
    class JobAssignmentTerminalTransitions {

        @Test
        @DisplayName("reject accept on REJECTED assignment → 409")
        void rejectAcceptOnRejectedAssignment(VertxTestContext ctx) {
            String jobId = uniqueId("job-rej-accept");
            String agentId = uniqueId("agent-rej-accept");

            // 1. Create assignment (ASSIGNED)
            createAssignment(jobId, agentId)
                    .compose(id -> {
                        // 2. Reject it (ASSIGNED → REJECTED — valid terminal transition)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/reject")
                                .sendJsonObject(new JsonObject().put("reason", "capacity"))
                                .map(r -> id);
                    })
                    .compose(id -> {
                        // 3. Try to accept the REJECTED assignment — should fail
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/accept")
                                .send();
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(409, response.statusCode(),
                                "Accepting a REJECTED assignment should return 409 Conflict");
                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertNotNull(error, "Response should contain an error object");
                        assertEquals("ASSIGNMENT_STATE_CONFLICT", error.getString("code"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("reject cancel on CANCELLED assignment → 409")
        void rejectCancelOnCancelledAssignment(VertxTestContext ctx) {
            String jobId = uniqueId("job-canc-canc");
            String agentId = uniqueId("agent-canc-canc");

            createAssignment(jobId, agentId)
                    .compose(id -> {
                        // Cancel it (ASSIGNED → CANCELLED — valid)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/cancel")
                                .sendJsonObject(new JsonObject().put("reason", "first cancel"))
                                .map(r -> id);
                    })
                    .compose(id -> {
                        // Try to cancel again — should fail (terminal state)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/cancel")
                                .sendJsonObject(new JsonObject().put("reason", "second cancel"));
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(409, response.statusCode(),
                                "Cancelling a CANCELLED assignment should return 409");
                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertNotNull(error);
                        assertEquals("ASSIGNMENT_STATE_CONFLICT", error.getString("code"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("reject status update on COMPLETED assignment → 409")
        void rejectStatusUpdateOnCompletedAssignment(VertxTestContext ctx) {
            String jobId = uniqueId("job-comp-update");
            String agentId = uniqueId("agent-comp-update");

            createAssignment(jobId, agentId)
                    .compose(id -> {
                        // ASSIGNED → ACCEPTED (valid)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/accept")
                                .send().map(r -> id);
                    })
                    .compose(id -> {
                        // ACCEPTED → IN_PROGRESS (valid via status endpoint)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/status")
                                .sendJsonObject(new JsonObject().put("status", "IN_PROGRESS"))
                                .map(r -> id);
                    })
                    .compose(id -> {
                        // IN_PROGRESS → COMPLETED (valid via status endpoint)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/status")
                                .sendJsonObject(new JsonObject().put("status", "COMPLETED"))
                                .map(r -> id);
                    })
                    .compose(id -> {
                        // COMPLETED → ASSIGNED (illegal — terminal state)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/status")
                                .sendJsonObject(new JsonObject().put("status", "ASSIGNED"));
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(409, response.statusCode(),
                                "Transitioning COMPLETED assignment to ASSIGNED should return 409");
                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertNotNull(error);
                        assertEquals("ASSIGNMENT_STATE_CONFLICT", error.getString("code"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("reject accept on TIMED-OUT assignment → 409")
        void rejectAcceptOnTimedOutAssignment(VertxTestContext ctx) {
            String jobId = uniqueId("job-timeout-accept");
            String agentId = uniqueId("agent-timeout-accept");

            createAssignment(jobId, agentId)
                    .compose(id -> {
                        // ASSIGNED → TIMEOUT (valid via status endpoint)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/status")
                                .sendJsonObject(new JsonObject().put("status", "TIMEOUT"))
                                .map(r -> id);
                    })
                    .compose(id -> {
                        // TIMEOUT → ACCEPTED (illegal — terminal state)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/accept")
                                .send();
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(409, response.statusCode(),
                                "Accepting a TIMED-OUT assignment should return 409");
                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertNotNull(error);
                        assertEquals("ASSIGNMENT_STATE_CONFLICT", error.getString("code"));
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== JobAssignment: Invalid Non-Terminal Transitions ====================

    @Nested
    @DisplayName("JobAssignment — Invalid Non-Terminal Transitions")
    class JobAssignmentInvalidTransitions {

        @Test
        @DisplayName("reject ASSIGNED → COMPLETED directly (skipping ACCEPTED/IN_PROGRESS) → 409")
        void rejectDirectCompletionFromAssigned(VertxTestContext ctx) {
            String jobId = uniqueId("job-skip-complete");
            String agentId = uniqueId("agent-skip-complete");

            createAssignment(jobId, agentId)
                    .compose(id -> {
                        // ASSIGNED → COMPLETED (invalid — must go through ACCEPTED → IN_PROGRESS first)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/status")
                                .sendJsonObject(new JsonObject().put("status", "COMPLETED"));
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(409, response.statusCode(),
                                "ASSIGNED → COMPLETED directly should return 409");
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("reject ASSIGNED → IN_PROGRESS directly (must accept first) → 409")
        void rejectDirectInProgressFromAssigned(VertxTestContext ctx) {
            String jobId = uniqueId("job-skip-inprog");
            String agentId = uniqueId("agent-skip-inprog");

            createAssignment(jobId, agentId)
                    .compose(id -> {
                        // ASSIGNED → IN_PROGRESS (invalid — must go through ACCEPTED first)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/status")
                                .sendJsonObject(new JsonObject().put("status", "IN_PROGRESS"));
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(409, response.statusCode(),
                                "ASSIGNED → IN_PROGRESS directly should return 409");
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("reject ACCEPTED → ASSIGNED (backward transition) → 409")
        void rejectBackwardTransitionAcceptedToAssigned(VertxTestContext ctx) {
            String jobId = uniqueId("job-back-assign");
            String agentId = uniqueId("agent-back-assign");

            createAssignment(jobId, agentId)
                    .compose(id -> {
                        // ASSIGNED → ACCEPTED (valid)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/accept")
                                .send().map(r -> id);
                    })
                    .compose(id -> {
                        // ACCEPTED → ASSIGNED (invalid — cannot go backward)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/status")
                                .sendJsonObject(new JsonObject().put("status", "ASSIGNED"));
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(409, response.statusCode(),
                                "ACCEPTED → ASSIGNED backward transition should return 409");
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== JobAssignment: Valid Transitions (Positive) ====================

    @Nested
    @DisplayName("JobAssignment — Valid Transition Chain")
    class JobAssignmentValidTransitions {

        @Test
        @DisplayName("full happy path: ASSIGNED → ACCEPTED → IN_PROGRESS → COMPLETED")
        void fullHappyPath(VertxTestContext ctx) {
            String jobId = uniqueId("job-happy");
            String agentId = uniqueId("agent-happy");

            createAssignment(jobId, agentId)
                    .compose(id -> {
                        // ASSIGNED → ACCEPTED
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/accept")
                                .send().map(r -> {
                                    assertEquals(200, r.statusCode());
                                    return id;
                                });
                    })
                    .compose(id -> {
                        // ACCEPTED → IN_PROGRESS
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/status")
                                .sendJsonObject(new JsonObject().put("status", "IN_PROGRESS"))
                                .map(r -> {
                                    assertEquals(200, r.statusCode());
                                    return id;
                                });
                    })
                    .compose(id -> {
                        // IN_PROGRESS → COMPLETED
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/status")
                                .sendJsonObject(new JsonObject().put("status", "COMPLETED"))
                                .map(r -> {
                                    assertEquals(200, r.statusCode());
                                    return id;
                                });
                    })
                    .compose(id -> {
                        // Verify final state is COMPLETED
                        return webClient.get(HTTP_PORT, "localhost", "/api/v1/assignments/" + id)
                                .send();
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        assertEquals("COMPLETED", response.bodyAsJsonObject().getString("status"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("ASSIGNED → CANCELLED is valid")
        void assignedToCancelled(VertxTestContext ctx) {
            String jobId = uniqueId("job-assign-cancel");
            String agentId = uniqueId("agent-assign-cancel");

            createAssignment(jobId, agentId)
                    .compose(id -> {
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/cancel")
                                .sendJsonObject(new JsonObject().put("reason", "no longer needed"));
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        assertEquals("CANCELLED", response.bodyAsJsonObject().getString("status"));
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== JobAssignment: State Preserved After Rejection ====================

    @Nested
    @DisplayName("JobAssignment — State Preserved After Rejected Transition")
    class JobAssignmentStatePreservation {

        @Test
        @DisplayName("assignment status remains unchanged after rejected transition")
        void statePreservedAfterRejection(VertxTestContext ctx) {
            String jobId = uniqueId("job-preserve");
            String agentId = uniqueId("agent-preserve");

            createAssignment(jobId, agentId)
                    .compose(id -> {
                        // Reject it (ASSIGNED → REJECTED)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/reject")
                                .sendJsonObject(new JsonObject().put("reason", "overloaded"))
                                .map(r -> id);
                    })
                    .compose(id -> {
                        // Try invalid transition (REJECTED → IN_PROGRESS)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/" + id + "/status")
                                .sendJsonObject(new JsonObject().put("status", "IN_PROGRESS"))
                                .map(r -> {
                                    assertEquals(409, r.statusCode(), "Invalid transition should be rejected");
                                    return id;
                                });
                    })
                    .compose(id -> {
                        // Verify status is still REJECTED (not corrupted)
                        return webClient.get(HTTP_PORT, "localhost", "/api/v1/assignments/" + id)
                                .send();
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        assertEquals("REJECTED", response.bodyAsJsonObject().getString("status"),
                                "Status should remain REJECTED after invalid transition attempt");
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== Route: Suspend/Resume Transition Enforcement ====================

    @Nested
    @DisplayName("Route — Suspend/Resume Enforcement")
    class RouteSuspendResumeTransitions {

        @Test
        @DisplayName("reject resume on non-SUSPENDED route → 409")
        void rejectResumeOnConfiguredRoute(VertxTestContext ctx) {
            String routeId = uniqueId("route-resume-cfg");

            // 1. Create a route (starts in CONFIGURED status)
            createRoute(routeId)
                    .compose(id -> {
                        // 2. Try to resume a route that is not SUSPENDED — should fail
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/routes/" + id + "/resume")
                                .send();
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(409, response.statusCode(),
                                "Resuming a non-SUSPENDED route should return 409");
                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertNotNull(error);
                        assertEquals("ROUTE_STATE_CONFLICT", error.getString("code"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("reject double-suspend on already-SUSPENDED route → 409")
        void rejectDoubleSuspend(VertxTestContext ctx) {
            String routeId = uniqueId("route-dbl-susp");

            createRoute(routeId)
                    .compose(id -> {
                        // Suspend (CONFIGURED → SUSPENDED — note: handler allows this)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/routes/" + id + "/suspend")
                                .sendJsonObject(new JsonObject().put("reason", "maintenance"))
                                .map(r -> id);
                    })
                    .compose(id -> {
                        // Suspend again — should fail (already SUSPENDED)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/routes/" + id + "/suspend")
                                .sendJsonObject(new JsonObject().put("reason", "again"));
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(409, response.statusCode(),
                                "Suspending an already-SUSPENDED route should return 409");
                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertNotNull(error);
                        assertEquals("ROUTE_STATE_CONFLICT", error.getString("code"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("valid suspend → resume → suspend cycle")
        void validSuspendResumeCycle(VertxTestContext ctx) {
            String routeId = uniqueId("route-cycle");

            createRoute(routeId)
                    .compose(id -> {
                        // Suspend (CONFIGURED → SUSPENDED)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/routes/" + id + "/suspend")
                                .sendJsonObject(new JsonObject().put("reason", "maintenance window"))
                                .map(r -> {
                                    assertEquals(200, r.statusCode());
                                    return id;
                                });
                    })
                    .compose(id -> {
                        // Resume (SUSPENDED → ACTIVE)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/routes/" + id + "/resume")
                                .send()
                                .map(r -> {
                                    assertEquals(200, r.statusCode());
                                    return id;
                                });
                    })
                    .compose(id -> {
                        // Suspend again (ACTIVE → SUSPENDED — should work)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/routes/" + id + "/suspend")
                                .sendJsonObject(new JsonObject().put("reason", "second maintenance window"));
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode(),
                                "Second suspend after resume should succeed");
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== Route: State Preserved After Rejection ====================

    @Nested
    @DisplayName("Route — State Preserved After Rejected Transition")
    class RouteStatePreservation {

        @Test
        @DisplayName("route status remains unchanged after rejected resume")
        void statePreservedAfterRejectedResume(VertxTestContext ctx) {
            String routeId = uniqueId("route-preserve");

            createRoute(routeId)
                    .compose(id -> {
                        // Try invalid resume on CONFIGURED route
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/routes/" + id + "/resume")
                                .send()
                                .map(r -> {
                                    assertEquals(409, r.statusCode());
                                    return id;
                                });
                    })
                    .compose(id -> {
                        // Verify route still exists and is CONFIGURED
                        return webClient.get(HTTP_PORT, "localhost", "/api/v1/routes/" + id)
                                .send();
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode());
                        assertEquals("CONFIGURED", response.bodyAsJsonObject().getString("status"),
                                "Route status should remain CONFIGURED after rejected resume");
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== Route: Deleted State Enforcement ====================

    @Nested
    @DisplayName("Route — Deleted Route Enforcement")
    class RouteDeletedEnforcement {

        @Test
        @DisplayName("deleted route returns 404 on subsequent operations")
        void deletedRouteReturns404(VertxTestContext ctx) {
            String routeId = uniqueId("route-del-404");

            createRoute(routeId)
                    .compose(id -> {
                        // Delete the route
                        return webClient.delete(HTTP_PORT, "localhost", "/api/v1/routes/" + id)
                                .send()
                                .map(r -> {
                                    assertEquals(200, r.statusCode());
                                    return id;
                                });
                    })
                    .compose(id -> {
                        // Try to suspend the deleted route — should get 404 (removed from store)
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/routes/" + id + "/suspend")
                                .sendJsonObject(new JsonObject().put("reason", "too late"));
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(404, response.statusCode(),
                                "Operations on a deleted route should return 404");
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("deleted route cannot be resumed")
        void deletedRouteCannotBeResumed(VertxTestContext ctx) {
            String routeId = uniqueId("route-del-resume");

            createRoute(routeId)
                    .compose(id -> {
                        return webClient.delete(HTTP_PORT, "localhost", "/api/v1/routes/" + id)
                                .send().map(r -> id);
                    })
                    .compose(id -> {
                        return webClient.put(HTTP_PORT, "localhost", "/api/v1/routes/" + id + "/resume")
                                .send();
                    })
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(404, response.statusCode(),
                                "Resuming a deleted route should return 404");
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== Cross-Domain: Non-Existent Entity Operations ====================

    @Nested
    @DisplayName("Non-Existent Entity Operations")
    class NonExistentEntityOperations {

        @Test
        @DisplayName("accept non-existent assignment → 404")
        void acceptNonExistentAssignment(VertxTestContext ctx) {
            webClient.put(HTTP_PORT, "localhost", "/api/v1/assignments/does-not-exist/accept")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(404, response.statusCode());
                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertNotNull(error);
                        assertEquals("ASSIGNMENT_NOT_FOUND", error.getString("code"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("suspend non-existent route → 404")
        void suspendNonExistentRoute(VertxTestContext ctx) {
            webClient.put(HTTP_PORT, "localhost", "/api/v1/routes/does-not-exist/suspend")
                    .sendJsonObject(new JsonObject().put("reason", "just checking"))
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(404, response.statusCode());
                        JsonObject error = response.bodyAsJsonObject().getJsonObject("error");
                        assertNotNull(error);
                        assertEquals("ROUTE_NOT_FOUND", error.getString("code"));
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("resume non-existent route → 404")
        void resumeNonExistentRoute(VertxTestContext ctx) {
            webClient.put(HTTP_PORT, "localhost", "/api/v1/routes/does-not-exist/resume")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(404, response.statusCode());
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a job assignment via HTTP and returns a Future containing the assignmentId.
     */
    private io.vertx.core.Future<String> createAssignment(String jobId, String agentId) {
        JsonObject body = new JsonObject()
                .put("jobId", jobId)
                .put("agentId", agentId);

        return webClient.post(HTTP_PORT, "localhost", "/api/v1/assignments")
                .sendJsonObject(body)
                .map(response -> {
                    assertEquals(201, response.statusCode(),
                            "Assignment creation should return 201");
                    String id = response.bodyAsJsonObject().getString("assignmentId");
                    assertNotNull(id, "Assignment ID should be returned");
                    logger.debug("Created assignment: id={}, jobId={}, agentId={}", id, jobId, agentId);
                    return id;
                });
    }

    /**
     * Creates a route via HTTP and returns a Future containing the routeId.
     */
    private io.vertx.core.Future<String> createRoute(String routeId) {
        JsonObject trigger = new JsonObject()
                .put("type", "INTERVAL")
                .put("intervalMinutes", 60);

        JsonObject body = new JsonObject()
                .put("routeId", routeId)
                .put("name", "Test Route " + routeId)
                .put("description", "Integration test route")
                .put("sourceAgentId", "agent-src-01")
                .put("sourceLocation", "/data/source")
                .put("destinationAgentId", "agent-dst-01")
                .put("destinationLocation", "/data/destination")
                .put("trigger", trigger);

        return webClient.post(HTTP_PORT, "localhost", "/api/v1/routes")
                .sendJsonObject(body)
                .map(response -> {
                    assertEquals(201, response.statusCode(),
                            "Route creation should return 201");
                    String id = response.bodyAsJsonObject().getString("routeId");
                    assertNotNull(id, "Route ID should be returned");
                    logger.debug("Created route: id={}", id);
                    return id;
                });
    }
}
