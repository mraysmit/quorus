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
import dev.mars.quorus.controller.state.JobAssignmentCommand;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.TransferAttemptCommand;
import dev.mars.quorus.controller.state.TransferJobCommand;
import dev.mars.quorus.core.JobAssignment;
import dev.mars.quorus.core.JobAssignmentStatus;
import dev.mars.quorus.core.TransferAttempt;
import dev.mars.quorus.core.TransferAttemptOutcome;
import dev.mars.quorus.core.TransferAttemptStatus;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
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

import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static dev.mars.quorus.testing.TestFutureUtils.eventually;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** External agent-protocol contract for attempt identity, fencing, and ordered reports. */
@ExtendWith(VertxExtension.class)
class TransferAttemptAgentProtocolIntegrationTest {

    private static final String NODE_ID = "attempt-agent-protocol-node";
    private static final String TENANT_ID = "payments-operations";
    private static final String AGENT_ID = "agent-payments-01";
    private static final String ATTEMPT_ID = "attempt-payments-001";
    private static final Instant CREATED = Instant.parse("2026-09-02T03:00:00Z");

    private static Vertx vertx;
    private static RaftNode raftNode;
    private static HttpApiServer httpServer;
    private static WebClient webClient;
    private static String jobId;
    private static Instant leaseExpiresAt;

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
                .requestId("attempt-agent-protocol-job")
                .sourceUri(URI.create("sftp://payments.example.test/out/payment.dat"))
                .destinationPath(Path.of("target", "payment.dat"))
                .expectedSize(1_024L)
                .build();
        TransferJob job = new TransferJob(request);
        jobId = job.getJobId();
        awaitSuccess(raftNode.submitCommand(TransferJobCommand.create(job, TENANT_ID)), Duration.ofSeconds(5));

        AgentInfo agent = new AgentInfo(AGENT_ID, "agent-payments.example.test", "10.0.0.20", 8080);
        agent.setTenantId(TENANT_ID);
        agent.setStatus(AgentStatus.HEALTHY);
        awaitSuccess(raftNode.submitCommand(AgentCommand.register(agent)), Duration.ofSeconds(5));

        JobAssignment assignment = new JobAssignment.Builder()
                .jobId(jobId)
                .agentId(AGENT_ID)
                .tenantId(TENANT_ID)
                .status(JobAssignmentStatus.ASSIGNED)
                .assignedAt(CREATED)
                .build();
        awaitSuccess(raftNode.submitCommand(JobAssignmentCommand.assign(assignment)), Duration.ofSeconds(5));

        leaseExpiresAt = Instant.now().plusSeconds(300);
        TransferAttempt attempt = new TransferAttempt.Builder()
                .attemptId(ATTEMPT_ID)
                .jobId(jobId)
                .agentId(AGENT_ID)
                .tenantId(TENANT_ID)
                .attemptNumber(1)
                .fencingGeneration(1)
                .leaseExpiresAt(leaseExpiresAt)
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
    void agentPollAndStatusReportCarryTheAuthoritativeAttemptFence(VertxTestContext context) {
        webClient.get(httpServer.actualPort(), "localhost", "/api/v1/agents/" + AGENT_ID + "/jobs")
                .send()
                .compose(pollResponse -> {
                    context.verify(() -> {
                        assertEquals(200, pollResponse.statusCode());
                        JsonObject pending = pollResponse.bodyAsJsonObject()
                                .getJsonArray("pendingJobs").getJsonObject(0);
                        assertEquals(ATTEMPT_ID, pending.getString("attemptId"));
                        assertEquals(1L, pending.getLong("fencingGeneration"));
                        assertEquals(0L, pending.getLong("lastReportSequence"));
                        assertEquals(leaseExpiresAt.toString(), pending.getString("leaseExpiresAt"));
                    });
                    JsonObject missingExpectedState = acceptedReport(1);
                    missingExpectedState.remove("expectedState");
                    return webClient.post(httpServer.actualPort(), "localhost", "/api/v1/jobs/" + jobId + "/status")
                            .sendJsonObject(missingExpectedState);
                })
                .compose(missingExpectedState -> {
                    context.verify(() -> assertEquals(400, missingExpectedState.statusCode()));
                    return webClient.post(httpServer.actualPort(), "localhost", "/api/v1/jobs/" + jobId + "/status")
                            .sendJsonObject(acceptedReport(0));
                })
                .compose(staleResponse -> {
                    context.verify(() -> assertEquals(409, staleResponse.statusCode()));
                    return webClient.get(httpServer.actualPort(), "localhost",
                                    "/api/v1/transfers/" + jobId + "/attempts/" + ATTEMPT_ID)
                            .send();
                })
                .compose(unchangedResponse -> {
                    context.verify(() -> {
                        JsonObject unchanged = unchangedResponse.bodyAsJsonObject();
                        assertEquals("OFFERED", unchanged.getString("status"));
                        assertEquals(0L, unchanged.getLong("lastReportSequence"));
                    });
                    return webClient.post(httpServer.actualPort(), "localhost", "/api/v1/jobs/" + jobId + "/status")
                            .sendJsonObject(acceptedReport(1));
                })
                .compose(statusResponse -> {
                    context.verify(() -> assertEquals(200, statusResponse.statusCode()));
                    return webClient.get(httpServer.actualPort(), "localhost",
                                    "/api/v1/transfers/" + jobId + "/attempts/" + ATTEMPT_ID)
                            .send();
                })
                .onComplete(context.succeeding(attemptResponse -> context.verify(() -> {
                    assertEquals(200, attemptResponse.statusCode());
                    JsonObject attempt = attemptResponse.bodyAsJsonObject();
                    assertEquals("ACCEPTED", attempt.getString("status"));
                    assertEquals(1L, attempt.getLong("lastReportSequence"));
                    context.completeNow();
                })));
    }

    private static JsonObject acceptedReport(long fencingGeneration) {
        return new JsonObject()
                .put("agentId", AGENT_ID)
                .put("status", "ACCEPTED")
                .put("expectedState", "OFFERED")
                .put("attemptId", ATTEMPT_ID)
                .put("fencingGeneration", fencingGeneration)
                .put("reportSequence", 1L);
    }
}
