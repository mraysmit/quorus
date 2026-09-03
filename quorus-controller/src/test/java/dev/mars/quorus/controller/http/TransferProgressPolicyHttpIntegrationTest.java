/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package dev.mars.quorus.controller.http;

import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.controller.raft.InMemoryTransportSimulator;
import dev.mars.quorus.controller.config.AppConfig;
import dev.mars.quorus.controller.config.ControllerTestConfig;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftNodeMode;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.AgentCommand;
import dev.mars.quorus.controller.state.TransferAttemptCommand;
import dev.mars.quorus.controller.state.TransferJobCommand;
import dev.mars.quorus.core.TransferAttempt;
import dev.mars.quorus.core.TransferAttemptOutcome;
import dev.mars.quorus.core.TransferAttemptStatus;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferStatus;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.Properties;

import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static dev.mars.quorus.testing.TestFutureUtils.eventually;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** External-path proof that transfer telemetry windows are governed configuration. */
@ExtendWith(VertxExtension.class)
class TransferProgressPolicyHttpIntegrationTest {

    private static final String FRESH_PROPERTY = "quorus.telemetry.transfer.fresh-window-ms";
    private static final String STALL_PROPERTY = "quorus.telemetry.transfer.stall-window-ms";
    private static final String NODE_ID = "progress-policy-node";
    private static final String TENANT_ID = "payments";
    private static final String JOB_ID = "configured-stale-progress";
    private static final String STALLED_JOB_ID = "configured-stalled-progress";
    private static final String STALLED_AGENT_ID = "agent-configured-stall";
    private static final Instant STALLED_LAST_PROGRESS = Instant.now().minusSeconds(20);

    private static Vertx vertx;
    private static RaftNode raftNode;
    private static HttpApiServer httpServer;
    private static WebClient webClient;

    @BeforeAll
    static void setUp() throws Exception {
        Properties overrides = new Properties();
        overrides.setProperty(FRESH_PROPERTY, "5000");
        overrides.setProperty(STALL_PROPERTY, "15000");
        AppConfig config = ControllerTestConfig.create(overrides);
        {
            vertx = Vertx.vertx();
            QuorusStateStore stateStore = new QuorusStateStore();
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

            TransferRequest request = TransferRequest.builder()
                    .requestId(JOB_ID)
                    .sourceUri(URI.create("sftp://payments.example.test/out/stale.dat"))
                    .destinationPath(Path.of("target", "stale.dat"))
                    .expectedSize(1_000)
                    .build();
            awaitSuccess(raftNode.submitCommand(
                    new TransferJobCommand.Create(JOB_ID, new TransferJob(request), Instant.now(), TENANT_ID)),
                    Duration.ofSeconds(5));
            awaitSuccess(raftNode.submitCommand(
                    new TransferJobCommand.UpdateProgress(JOB_ID, 100, Instant.now().minusSeconds(10))),
                    Duration.ofSeconds(5));

            TransferRequest stalledRequest = TransferRequest.builder()
                    .requestId(STALLED_JOB_ID)
                    .sourceUri(URI.create("sftp://payments.example.test/out/stalled.dat"))
                    .destinationPath(Path.of("target", "stalled.dat"))
                    .expectedSize(1_000)
                    .build();
            awaitSuccess(raftNode.submitCommand(new TransferJobCommand.Create(
                    STALLED_JOB_ID, new TransferJob(stalledRequest), Instant.now(), TENANT_ID)),
                    Duration.ofSeconds(5));
            awaitSuccess(raftNode.submitCommand(new TransferJobCommand.UpdateStatus(
                    STALLED_JOB_ID, TransferStatus.PENDING, TransferStatus.IN_PROGRESS,
                    STALLED_LAST_PROGRESS)), Duration.ofSeconds(5));
            awaitSuccess(raftNode.submitCommand(new TransferJobCommand.UpdateProgress(
                    STALLED_JOB_ID, 100, STALLED_LAST_PROGRESS)), Duration.ofSeconds(5));

            AgentInfo stalledAgent = new AgentInfo(
                    STALLED_AGENT_ID, "agent-configured-stall.example.test", "10.0.0.51", 8080);
            stalledAgent.setTenantId(TENANT_ID);
            stalledAgent.setStatus(AgentStatus.HEALTHY);
            awaitSuccess(raftNode.submitCommand(AgentCommand.register(stalledAgent)), Duration.ofSeconds(5));
            TransferAttempt stalledAttempt = new TransferAttempt.Builder()
                    .attemptId("attempt-configured-stall")
                    .jobId(STALLED_JOB_ID)
                    .agentId(STALLED_AGENT_ID)
                    .tenantId(TENANT_ID)
                    .attemptNumber(1)
                    .fencingGeneration(1)
                    .leaseExpiresAt(Instant.now().plusSeconds(300))
                    .status(TransferAttemptStatus.OFFERED)
                    .outcome(TransferAttemptOutcome.NONE)
                    .createdAt(STALLED_LAST_PROGRESS.minusSeconds(5))
                    .updatedAt(STALLED_LAST_PROGRESS.minusSeconds(5))
                    .build();
            awaitSuccess(raftNode.submitCommand(TransferAttemptCommand.offer(stalledAttempt, null)),
                    Duration.ofSeconds(5));

            httpServer = new HttpApiServer(vertx, 0, raftNode, stateStore, config);
            awaitSuccess(httpServer.start(), Duration.ofSeconds(5));
            webClient = WebClient.create(vertx);
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (webClient != null) webClient.close();
        if (httpServer != null) awaitSuccess(httpServer.stop(), Duration.ofSeconds(5));
        if (raftNode != null) awaitSuccess(raftNode.stop(), Duration.ofSeconds(5));
        if (vertx != null) awaitSuccess(vertx.close(), Duration.ofSeconds(5));
    }

    @Test
    void configuredFreshnessWindowDrivesStaleConditionAndIsDisclosed(VertxTestContext context) {
        webClient.get(httpServer.actualPort(), "localhost", "/api/v1/transfers/" + JOB_ID + "/progress")
                .send()
                .onComplete(context.succeeding(response -> context.verify(() -> {
                    assertEquals(200, response.statusCode());
                    JsonObject progress = response.bodyAsJsonObject();
                    assertEquals("STALE", progress.getString("telemetryState"));
                    assertEquals("DEGRADED", progress.getString("condition"));
                    assertEquals("PROGRESS_TELEMETRY_STALE", progress.getString("conditionReason"));
                    assertEquals(5L, progress.getLong("freshnessWindowSeconds"));
                    assertEquals(15L, progress.getLong("stallWindowSeconds"));
                    assertEquals("CONTROLLER_CONFIGURATION", progress.getString("telemetryPolicySource"));
                    assertTrue(progress.getLong("telemetryAgeSeconds") >= 10);
                    context.completeNow();
                })));
    }

    @Test
    void activeTransferPastStallWindowDisclosesStableConditionTiming(VertxTestContext context) {
        webClient.get(httpServer.actualPort(), "localhost",
                        "/api/v1/transfers/" + STALLED_JOB_ID + "/progress")
                .send()
                .onComplete(context.succeeding(response -> context.verify(() -> {
                    assertEquals(200, response.statusCode());
                    JsonObject progress = response.bodyAsJsonObject();
                    assertEquals("STALE", progress.getString("telemetryState"));
                    assertEquals("STALLED", progress.getString("condition"));
                    assertEquals("NO_PROGRESS_WITHIN_POLICY_WINDOW", progress.getString("conditionReason"));
                    assertEquals(STALLED_LAST_PROGRESS.plusSeconds(15).toString(),
                            progress.getString("conditionSince"));
                    assertTrue(progress.getLong("stallDurationSeconds") >= 5);
                    assertEquals("attempt-configured-stall", progress.getString("activeAttemptId"));
                    context.completeNow();
                })));
    }
}
