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
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftNodeMode;
import dev.mars.quorus.controller.state.AgentCommand;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.TransferAttemptCommand;
import dev.mars.quorus.controller.state.TransferJobCommand;
import dev.mars.quorus.core.TransferAttempt;
import dev.mars.quorus.core.TransferAttemptOutcome;
import dev.mars.quorus.core.TransferAttemptStatus;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
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

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static dev.mars.quorus.testing.TestFutureUtils.eventually;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Phase 2 external-path contract for authoritative attempt history and active fencing evidence. */
@ExtendWith(VertxExtension.class)
class TransferAttemptHttpIntegrationTest {

    private static final String NODE_ID = "attempt-http-node";
    private static final String TENANT_ID = "tenant-a";
    private static final String AGENT_ID = "agent-a";
    private static final Instant CREATED = Instant.parse("2026-09-02T02:00:00Z");

    private static Vertx vertx;
    private static RaftNode raftNode;
    private static HttpApiServer httpServer;
    private static WebClient webClient;
    private static String jobId;

    @BeforeAll
    static void setUp() throws Exception {
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
                .requestId("attempt-http-job")
                .sourceUri(URI.create("sftp://source.example.test/out/report.dat"))
                .destinationPath(Path.of("target", "attempt-http-report.dat"))
                .expectedSize(1_024L)
                .build();
        TransferJob job = new TransferJob(request);
        jobId = job.getJobId();
        awaitSuccess(raftNode.submitCommand(TransferJobCommand.create(job, TENANT_ID)), Duration.ofSeconds(5));

        AgentInfo agent = new AgentInfo(AGENT_ID, "agent-a.example.test", "10.0.0.10", 8080);
        agent.setTenantId(TENANT_ID);
        agent.setStatus(AgentStatus.HEALTHY);
        awaitSuccess(raftNode.submitCommand(AgentCommand.register(agent)), Duration.ofSeconds(5));

        TransferAttempt attempt = new TransferAttempt.Builder()
                .attemptId("attempt-http-1")
                .jobId(jobId)
                .agentId(AGENT_ID)
                .tenantId(TENANT_ID)
                .attemptNumber(1)
                .fencingGeneration(1)
                .leaseExpiresAt(CREATED.plusSeconds(60))
                .status(TransferAttemptStatus.OFFERED)
                .outcome(TransferAttemptOutcome.NONE)
                .createdAt(CREATED)
                .updatedAt(CREATED)
                .build();
        awaitSuccess(raftNode.submitCommand(TransferAttemptCommand.offer(attempt, null)), Duration.ofSeconds(5));

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
    void committedAttemptHistoryAndActiveFenceAreVisibleThroughHttp(VertxTestContext context) {
        webClient.get(httpServer.actualPort(), "localhost", "/api/v1/transfers/" + jobId + "/attempts")
                .send()
                .compose(historyResponse -> {
                    context.verify(() -> {
                        assertEquals(200, historyResponse.statusCode());
                        JsonObject history = historyResponse.bodyAsJsonObject();
                        assertEquals(jobId, history.getString("jobId"));
                        assertEquals("attempt-http-1", history.getString("activeAttemptId"));
                        JsonArray items = history.getJsonArray("items");
                        assertEquals(1, items.size());
                        assertEquals(1L, items.getJsonObject(0).getLong("fencingGeneration"));
                    });
                    return webClient.get(httpServer.actualPort(), "localhost",
                                    "/api/v1/transfers/" + jobId + "/attempts/attempt-http-1")
                            .send();
                })
                .onComplete(context.succeeding(attemptResponse -> context.verify(() -> {
                    assertEquals(200, attemptResponse.statusCode());
                    JsonObject attempt = attemptResponse.bodyAsJsonObject();
                    assertEquals("attempt-http-1", attempt.getString("attemptId"));
                    assertEquals("OFFERED", attempt.getString("status"));
                    assertEquals("NONE", attempt.getString("outcome"));
                    context.completeNow();
                })));
    }
}
