/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package dev.mars.quorus.controller.http;

import dev.mars.quorus.controller.raft.InMemoryTransportSimulator;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftNodeMode;
import dev.mars.quorus.controller.state.QuorusStateStore;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.Set;

import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static dev.mars.quorus.testing.TestFutureUtils.eventually;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Phase 3 external contract for operator-facing progress and deadline condition. */
@ExtendWith(VertxExtension.class)
class TransferProgressHttpIntegrationTest {

    private static final String NODE_ID = "progress-http-node";
    private static final String TENANT_ID = "payments";
    private static final String AGENT_ID = "agent-payments-progress";
    private static final String JOB_ID = "critical-settlement-progress";

    private static Vertx vertx;
    private static RaftNode raftNode;
    private static QuorusStateStore stateStore;
    private static HttpApiServer httpServer;
    private static WebClient webClient;

    @BeforeAll
    static void setUp() throws Exception {
        vertx = Vertx.vertx();
        stateStore = new QuorusStateStore();
        raftNode = RaftNode.builder()
                .vertx(vertx)
                .nodeId(NODE_ID)
                .clusterNodes(Set.of(NODE_ID))
                .transport(new InMemoryTransportSimulator(NODE_ID))
                .stateMachine(stateStore)
                .mode(RaftNodeMode.volatileMode())
                .electionTimeout(200)
                .heartbeatInterval(50)
                .build();
        awaitSuccess(raftNode.start(), Duration.ofSeconds(5));
        awaitSuccess(eventually(vertx, raftNode::isLeader, Duration.ofSeconds(5)), Duration.ofSeconds(6));

        httpServer = new HttpApiServer(vertx, 0, raftNode, stateStore);
        awaitSuccess(httpServer.start(), Duration.ofSeconds(5));
        webClient = WebClient.create(vertx);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (webClient != null) webClient.close();
        if (httpServer != null) awaitSuccess(httpServer.stop(), Duration.ofSeconds(5));
        if (raftNode != null) awaitSuccess(raftNode.stop(), Duration.ofSeconds(5));
        if (vertx != null) awaitSuccess(vertx.close(), Duration.ofSeconds(5));
    }

    @Test
    void criticalTransferProgressExposesOwnershipFreshnessAndLateCondition(VertxTestContext context) {
        JsonObject registration = new JsonObject()
                .put("agentId", AGENT_ID)
                .put("hostname", "agent-payments-progress.example.test")
                .put("address", "10.0.0.31")
                .put("port", 8080)
                .put("tenantId", TENANT_ID);
        JsonObject transfer = new JsonObject()
                .put("jobId", JOB_ID)
                .put("sourceUri", "sftp://payments.example.test/out/settlement.dat")
                .put("destinationPath", "target/settlement.dat")
                .put("totalBytes", 1_000L)
                .put("tenantId", TENANT_ID)
                .put("businessService", "settlement-reporting")
                .put("owner", "settlement-operations")
                .put("criticality", "CRITICAL")
                .put("environment", "PRODUCTION")
                .put("processingDate", "2026-09-02")
                .put("expectedStartAt", "2026-09-02T03:00:00Z")
                .put("requiredCompletionAt", "2026-09-02T03:10:00Z")
                .put("runbookUrl", "https://runbooks.example.test/settlement-transfer");

        webClient.post(httpServer.actualPort(), "localhost", "/api/v1/agents/register")
                .sendJsonObject(registration)
                .compose(response -> {
                    context.verify(() -> assertEquals(201, response.statusCode()));
                    return webClient.post(httpServer.actualPort(), "localhost", "/api/v1/transfers")
                            .sendJsonObject(transfer);
                })
                .compose(response -> {
                    context.verify(() -> assertEquals(201, response.statusCode()));
                    return webClient.post(httpServer.actualPort(), "localhost", "/api/v1/assignments")
                            .sendJsonObject(new JsonObject().put("jobId", JOB_ID).put("agentId", AGENT_ID));
                })
                .compose(response -> {
                    context.verify(() -> assertEquals(201, response.statusCode()));
                    return webClient.get(httpServer.actualPort(), "localhost",
                                    "/api/v1/agents/" + AGENT_ID + "/jobs")
                            .send();
                })
                .compose(response -> {
                    JsonArray jobs = response.bodyAsJsonObject().getJsonArray("pendingJobs");
                    JsonObject assignment = jobs.getJsonObject(0);
                    String attemptId = assignment.getString("attemptId");
                    long fence = assignment.getLong("fencingGeneration");
                    context.verify(() -> {
                        assertEquals(200, response.statusCode());
                        assertNotNull(attemptId);
                    });
                    JsonObject accepted = report(attemptId, fence, "OFFERED", "ACCEPTED", 1, 0);
                    return webClient.post(httpServer.actualPort(), "localhost", "/api/v1/jobs/" + JOB_ID + "/status")
                            .sendJsonObject(accepted)
                            .compose(acceptedResponse -> {
                                context.verify(() -> assertEquals(200, acceptedResponse.statusCode()));
                                return Future.succeededFuture(attemptId + ":" + fence);
                            });
                })
                .compose(attemptContext -> {
                    String[] values = attemptContext.split(":");
                    JsonObject progress = report(values[0], Long.parseLong(values[1]),
                            "ACCEPTED", "IN_PROGRESS", 2, 250);
                    return webClient.post(httpServer.actualPort(), "localhost", "/api/v1/jobs/" + JOB_ID + "/status")
                            .sendJsonObject(progress);
                })
                .compose(response -> {
                    context.verify(() -> assertEquals(200, response.statusCode()));
                    return webClient.get(httpServer.actualPort(), "localhost",
                                    "/api/v1/transfers/" + JOB_ID + "/progress")
                            .send();
                })
                .compose(response -> {
                    context.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonObject progress = response.bodyAsJsonObject();
                        assertEquals(JOB_ID, progress.getString("jobId"));
                        assertEquals("settlement-reporting", progress.getString("businessService"));
                        assertEquals("settlement-operations", progress.getString("owner"));
                        assertEquals("CRITICAL", progress.getString("criticality"));
                        assertEquals("PRODUCTION", progress.getString("environment"));
                        assertEquals("2026-09-02", progress.getString("processingDate"));
                        assertEquals("https://runbooks.example.test/settlement-transfer", progress.getString("runbookUrl"));
                        assertEquals(250L, progress.getLong("bytesTransferred"));
                        assertEquals(1_000L, progress.getLong("totalBytes"));
                        assertEquals(25.0, progress.getDouble("percentComplete"));
                        assertEquals("KNOWN", progress.getString("sourceSizeState"));
                        assertEquals("FRESH", progress.getString("telemetryState"));
                        assertEquals("LATE", progress.getString("condition"));
                        assertEquals("REQUIRED_COMPLETION_MISSED", progress.getString("conditionReason"));
                        assertEquals("2026-09-02T03:10:00Z", progress.getString("requiredCompletionAt"));
                        assertNotNull(progress.getString("observedAt"));
                        assertNotNull(progress.getString("lastProgressAt"));
                        assertNotNull(progress.getString("activeAttemptId"));
                        assertEquals(AGENT_ID, progress.getString("agentId"));
                    });
                    return webClient.get(httpServer.actualPort(), "localhost",
                            "/api/v1/transfers/" + JOB_ID + "/events").send();
                })
                .onComplete(context.succeeding(response -> context.verify(() -> {
                    assertEquals(200, response.statusCode());
                    JsonArray events = response.bodyAsJsonObject().getJsonArray("events");
                    assertEquals(5, events.size());
                    assertEquals("TRANSFER_SUBMITTED", events.getJsonObject(0).getString("eventType"));
                    assertEquals("TRANSFER_ASSIGNED", events.getJsonObject(1).getString("eventType"));
                    assertEquals("TRANSFER_ACCEPTED", events.getJsonObject(2).getString("eventType"));
                    assertEquals("TRANSFER_STARTED", events.getJsonObject(3).getString("eventType"));
                    assertEquals("TRANSFER_PROGRESS", events.getJsonObject(4).getString("eventType"));
                    assertEquals(5L, events.getJsonObject(4).getLong("sequence"));
                    assertEquals(250L, events.getJsonObject(4).getLong("bytesTransferred"));
                    assertEquals(2L, events.getJsonObject(4).getLong("reportSequence"));
                    assertEquals(AGENT_ID, events.getJsonObject(4).getString("agentId"));
                    assertNotNull(events.getJsonObject(4).getString("attemptId"));
                    context.completeNow();
                })));
    }

    @Test
    void submittedTransferWithoutProgressReportsTelemetryAsUnknown(VertxTestContext context) {
        String jobId = "submitted-without-progress";
        JsonObject transfer = new JsonObject()
                .put("jobId", jobId)
                .put("sourceUri", "sftp://payments.example.test/out/pending.dat")
                .put("destinationPath", "target/pending.dat")
                .put("totalBytes", 2_000L)
                .put("tenantId", TENANT_ID)
                .put("businessService", "settlement-reporting")
                .put("owner", "settlement-operations")
                .put("criticality", "CRITICAL")
                .put("environment", "PRODUCTION");

        webClient.post(httpServer.actualPort(), "localhost", "/api/v1/transfers")
                .sendJsonObject(transfer)
                .compose(response -> {
                    context.verify(() -> assertEquals(201, response.statusCode()));
                    return webClient.get(httpServer.actualPort(), "localhost",
                                    "/api/v1/transfers/" + jobId + "/progress")
                            .send();
                })
                .onComplete(context.succeeding(response -> context.verify(() -> {
                    assertEquals(200, response.statusCode());
                    JsonObject progress = response.bodyAsJsonObject();
                    assertEquals("UNKNOWN", progress.getString("telemetryState"));
                    assertEquals("UNKNOWN", progress.getString("condition"));
                    assertEquals("NO_PROGRESS_TELEMETRY", progress.getString("conditionReason"));
                    assertEquals(false, progress.containsKey("lastProgressAt"));
                    assertEquals(false, progress.containsKey("telemetryAgeSeconds"));
                    context.completeNow();
                })));
    }

    @Test
    void submittedTransferExposesFirstOrderedEvent(VertxTestContext context) {
        String jobId = "event-ledger-submission";
        JsonObject transfer = new JsonObject()
                .put("jobId", jobId)
                .put("sourceUri", "sftp://payments.example.test/out/event.dat")
                .put("destinationPath", "target/event.dat")
                .put("totalBytes", 500L)
                .put("tenantId", TENANT_ID)
                .put("businessService", "settlement-reporting");

        webClient.post(httpServer.actualPort(), "localhost", "/api/v1/transfers")
                .sendJsonObject(transfer)
                .compose(response -> {
                    context.verify(() -> assertEquals(201, response.statusCode()));
                    return webClient.get(httpServer.actualPort(), "localhost",
                                    "/api/v1/transfers/" + jobId + "/events")
                            .send();
                })
                .onComplete(context.succeeding(response -> context.verify(() -> {
                    assertEquals(200, response.statusCode());
                    JsonObject body = response.bodyAsJsonObject();
                    assertEquals(jobId, body.getString("jobId"));
                    JsonArray events = body.getJsonArray("events");
                    assertEquals(1, events.size());
                    JsonObject event = events.getJsonObject(0);
                    assertEquals(jobId + ":1", event.getString("eventId"));
                    assertEquals(1L, event.getLong("sequence"));
                    assertEquals("TRANSFER_SUBMITTED", event.getString("eventType"));
                    assertEquals(TENANT_ID, event.getString("tenantId"));
                    assertEquals("settlement-reporting", event.getString("businessService"));
                    assertEquals("PENDING", event.getString("currentState"));
                    assertNotNull(event.getString("occurredAt"));
                    context.completeNow();
                })));
    }

    @Test
    void assignmentEventRemainsOrderedAfterSnapshotRestore(VertxTestContext context) {
        String jobId = "event-ledger-offer";
        String agentId = "agent-event-ledger";
        JsonObject registration = new JsonObject().put("agentId", agentId)
                .put("hostname", "agent-event-ledger.example.test").put("address", "10.0.0.41")
                .put("port", 8080).put("tenantId", TENANT_ID);
        JsonObject transfer = new JsonObject().put("jobId", jobId)
                .put("sourceUri", "sftp://payments.example.test/out/offer.dat")
                .put("destinationPath", "target/offer.dat").put("totalBytes", 500L)
                .put("tenantId", TENANT_ID).put("businessService", "settlement-reporting");

        webClient.post(httpServer.actualPort(), "localhost", "/api/v1/agents/register")
                .sendJsonObject(registration)
                .compose(response -> webClient.post(httpServer.actualPort(), "localhost", "/api/v1/transfers")
                        .sendJsonObject(transfer))
                .compose(response -> webClient.post(httpServer.actualPort(), "localhost", "/api/v1/assignments")
                        .sendJsonObject(new JsonObject().put("jobId", jobId).put("agentId", agentId)))
                .compose(response -> {
                    context.verify(() -> assertEquals(201, response.statusCode()));
                    byte[] snapshot = stateStore.takeSnapshot();
                    stateStore.reset();
                    stateStore.restoreSnapshot(snapshot);
                    return webClient.get(httpServer.actualPort(), "localhost",
                            "/api/v1/transfers/" + jobId + "/events").send();
                })
                .onComplete(context.succeeding(response -> context.verify(() -> {
                    assertEquals(200, response.statusCode());
                    JsonArray events = response.bodyAsJsonObject().getJsonArray("events");
                    assertEquals(2, events.size());
                    assertEquals("TRANSFER_SUBMITTED", events.getJsonObject(0).getString("eventType"));
                    assertEquals("TRANSFER_ASSIGNED", events.getJsonObject(1).getString("eventType"));
                    assertEquals(2L, events.getJsonObject(1).getLong("sequence"));
                    assertNotNull(events.getJsonObject(1).getString("attemptId"));
                    assertEquals(agentId, events.getJsonObject(1).getString("agentId"));
                    context.completeNow();
                })));
    }

    private static JsonObject report(String attemptId, long fence, String expectedState,
                                     String status, long sequence, long bytesTransferred) {
        return new JsonObject()
                .put("agentId", AGENT_ID)
                .put("status", status)
                .put("attemptId", attemptId)
                .put("expectedState", expectedState)
                .put("fencingGeneration", fence)
                .put("reportSequence", sequence)
                .put("bytesTransferred", bytesTransferred);
    }
}
