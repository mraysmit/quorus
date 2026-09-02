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
import dev.mars.quorus.core.TransferStatus;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
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

/** External proof that a rejected lifecycle report cannot leave partial authoritative state. */
@ExtendWith(VertxExtension.class)
class TransferAttemptLifecycleAtomicityIntegrationTest {

    private Vertx vertx;
    private RaftNode raftNode;
    private HttpApiServer httpServer;
    private WebClient webClient;

    @AfterEach
    void tearDown() throws Exception {
        if (webClient != null) webClient.close();
        if (httpServer != null) awaitSuccess(httpServer.stop(), Duration.ofSeconds(5));
        if (raftNode != null) awaitSuccess(raftNode.stop(), Duration.ofSeconds(5));
        if (vertx != null) awaitSuccess(vertx.close(), Duration.ofSeconds(5));
    }

    @Test
    void rejectedTransferTransitionLeavesAttemptAndAssignmentUnchanged(VertxTestContext context) throws Exception {
        String nodeId = "attempt-lifecycle-atomicity-node";
        String tenantId = "payments-operations";
        String agentId = "agent-payments-atomicity";
        String attemptId = "attempt-payments-atomicity";
        Instant created = Instant.parse("2026-09-02T04:00:00Z");

        vertx = Vertx.vertx();
        QuorusStateStore stateStore = new QuorusStateStore();
        raftNode = RaftNode.builder()
                .vertx(vertx)
                .nodeId(nodeId)
                .clusterNodes(Set.of(nodeId))
                .transport(new InMemoryTransportSimulator(nodeId))
                .stateMachine(stateStore)
                .mode(RaftNodeMode.volatileMode())
                .electionTimeout(200)
                .heartbeatInterval(50)
                .build();
        awaitSuccess(raftNode.start(), Duration.ofSeconds(5));
        awaitSuccess(eventually(vertx, raftNode::isLeader, Duration.ofSeconds(5)), Duration.ofSeconds(6));

        TransferRequest request = TransferRequest.builder()
                .requestId("attempt-lifecycle-atomicity-job")
                .sourceUri(URI.create("sftp://payments.example.test/out/atomicity.dat"))
                .destinationPath(Path.of("target", "atomicity.dat"))
                .expectedSize(2_048L)
                .build();
        TransferJob job = new TransferJob(request);
        String jobId = job.getJobId();
        awaitSuccess(raftNode.submitCommand(TransferJobCommand.create(job, tenantId)), Duration.ofSeconds(5));

        AgentInfo agent = new AgentInfo(agentId, "agent-payments.example.test", "10.0.0.21", 8080);
        agent.setTenantId(tenantId);
        agent.setStatus(AgentStatus.HEALTHY);
        awaitSuccess(raftNode.submitCommand(AgentCommand.register(agent)), Duration.ofSeconds(5));

        JobAssignment assignment = new JobAssignment.Builder()
                .jobId(jobId)
                .agentId(agentId)
                .tenantId(tenantId)
                .status(JobAssignmentStatus.ASSIGNED)
                .assignedAt(created)
                .build();
        awaitSuccess(raftNode.submitCommand(JobAssignmentCommand.assign(assignment)), Duration.ofSeconds(5));

        TransferAttempt attempt = new TransferAttempt.Builder()
                .attemptId(attemptId)
                .jobId(jobId)
                .agentId(agentId)
                .tenantId(tenantId)
                .attemptNumber(1)
                .fencingGeneration(1)
                .leaseExpiresAt(Instant.now().plusSeconds(300))
                .createdAt(created)
                .updatedAt(created)
                .build();
        awaitSuccess(raftNode.submitCommand(TransferAttemptCommand.offer(attempt, null)), Duration.ofSeconds(5));
        awaitSuccess(raftNode.submitCommand(TransferAttemptCommand.report(
                attemptId, 1, 1, TransferAttemptStatus.OFFERED, TransferAttemptStatus.ACCEPTED,
                0, TransferAttemptOutcome.NONE, null, created.plusSeconds(1))), Duration.ofSeconds(5));
        awaitSuccess(raftNode.submitCommand(JobAssignmentCommand.accept(jobId + ":" + agentId)), Duration.ofSeconds(5));
        awaitSuccess(raftNode.submitCommand(TransferJobCommand.updateStatus(
                jobId, TransferStatus.PENDING, TransferStatus.CANCELLED)), Duration.ofSeconds(5));

        httpServer = new HttpApiServer(vertx, 0, raftNode, stateStore);
        awaitSuccess(httpServer.start(), Duration.ofSeconds(5));
        webClient = WebClient.create(vertx);

        JsonObject report = new JsonObject()
                .put("agentId", agentId)
                .put("status", "IN_PROGRESS")
                .put("attemptId", attemptId)
                .put("expectedState", "ACCEPTED")
                .put("fencingGeneration", 1L)
                .put("reportSequence", 2L)
                .put("bytesTransferred", 256L);

        webClient.post(httpServer.actualPort(), "localhost", "/api/v1/jobs/" + jobId + "/status")
                .sendJsonObject(report)
                .onComplete(context.succeeding(response -> context.verify(() -> {
                    assertEquals(409, response.statusCode());
                    TransferAttempt unchangedAttempt = stateStore.findTransferAttempt(attemptId).orElseThrow();
                    assertEquals(TransferAttemptStatus.ACCEPTED, unchangedAttempt.getStatus());
                    assertEquals(1L, unchangedAttempt.getLastReportSequence());
                    assertEquals(0L, unchangedAttempt.getBytesTransferred());
                    assertEquals(JobAssignmentStatus.ACCEPTED,
                            stateStore.findJobAssignment(jobId + ":" + agentId).orElseThrow().getStatus());
                    assertEquals(TransferStatus.CANCELLED, stateStore.findTransferJob(jobId).orElseThrow().getStatus());
                    context.completeNow();
                })));
    }
}
