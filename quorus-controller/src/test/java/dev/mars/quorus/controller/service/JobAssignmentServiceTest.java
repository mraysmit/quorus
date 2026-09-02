/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.service;

import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.controller.raft.InMemoryTransportSimulator;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftNodeMode;
import dev.mars.quorus.controller.state.AgentCommand;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.TransferJobCommand;
import dev.mars.quorus.core.JobAssignmentStatus;
import dev.mars.quorus.core.JobPriority;
import dev.mars.quorus.core.JobRequirements;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferAttemptStatus;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URI;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end service tests using the real Raft command boundary. */
@ExtendWith(VertxExtension.class)
class JobAssignmentServiceTest {

    private static final String NODE_ID = "assignment-service-node";
    private static final String TENANT_ID = "payments-operations";

    private RaftNode raftNode;
    private JobAssignmentService service;
    private QuorusStateStore stateStore;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext context) {
        InMemoryTransportSimulator.clearAllTransports();
        InMemoryTransportSimulator transport = new InMemoryTransportSimulator(NODE_ID);
        stateStore = new QuorusStateStore();
        raftNode = RaftNode.builder()
                .vertx(vertx)
                .nodeId(NODE_ID)
                .clusterNodes(Set.of(NODE_ID))
                .transport(transport)
                .stateMachine(stateStore)
                .mode(RaftNodeMode.volatileMode())
                .electionTimeout(50)
                .heartbeatInterval(20)
                .build();

        raftNode.start()
                .compose(ignored -> awaitLeadership(vertx))
                .onComplete(context.succeeding(ignored -> {
                    service = new JobAssignmentService(vertx, raftNode, new AgentSelectionService());
                    context.completeNow();
                }));
    }

    @AfterEach
    void tearDown(VertxTestContext context) {
        if (service != null) {
            service.shutdown();
            service.shutdown();
        }
        Future<Void> stopped = raftNode == null ? Future.succeededFuture() : raftNode.stop();
        stopped.onComplete(result -> {
            InMemoryTransportSimulator.clearAllTransports();
            if (result.succeeded()) {
                context.completeNow();
            } else {
                context.failNow(result.cause());
            }
        });
    }

    @Test
    void submitAssignAndCompleteMaintainsServiceState(VertxTestContext context) {
        AgentInfo agent = healthyAgent("agent-payments-01");
        TransferRequest request = request("payment-file-001");
        JobRequirements requirements = new JobRequirements.Builder()
                .tenantId(TENANT_ID)
                .selectionStrategy(JobRequirements.SelectionStrategy.LEAST_LOADED)
                .build();

        service.updateAgentInfo(agent);
        service.submitJob(request, requirements, JobPriority.CRITICAL)
                .compose(queued -> raftNode.submitCommand(
                        TransferJobCommand.create(queued.getTransferJob(), TENANT_ID)).map(queued))
                .compose(queued -> raftNode.submitCommand(AgentCommand.register(agent)).map(queued))
                .compose(queued -> {
                    context.verify(() -> assertTrue(service.getJobQueue().containsKey(queued.getJobId())));
                    return service.assignJob(queued.getJobId(), agent.getAgentId());
                })
                .compose(assignment -> {
                    context.verify(() -> {
                        assertEquals(TENANT_ID, assignment.getTenantId());
                        assertTrue(service.getActiveAssignments().containsKey(assignment.getJobId()));
                        assertEquals(TransferAttemptStatus.OFFERED,
                                stateStore.findActiveTransferAttempt(assignment.getJobId()).orElseThrow().getStatus());
                        assertEquals(agent.getAgentId(),
                                stateStore.findActiveTransferAttempt(assignment.getJobId()).orElseThrow().getAgentId());
                    });
                    return service.updateAssignmentStatus(assignment.getJobId(), JobAssignmentStatus.ACCEPTED);
                })
                .compose(accepted -> service.updateAssignmentStatus(
                        accepted.getJobId(), JobAssignmentStatus.IN_PROGRESS))
                .compose(inProgress -> service.updateAssignmentStatus(
                        inProgress.getJobId(), JobAssignmentStatus.COMPLETED))
                .onComplete(context.succeeding(completed -> context.verify(() -> {
                    assertEquals(JobAssignmentStatus.COMPLETED, completed.getStatus());
                    assertTrue(service.getJobQueue().isEmpty());
                    assertTrue(service.getActiveAssignments().isEmpty());
                    context.completeNow();
                })));
    }

    @Test
    void invalidAssignmentsAndQueuedCancellationFailClosed(VertxTestContext context) {
        service.assignJob("missing-job", null).mapEmpty()
                .recover(error -> {
                    context.verify(() -> assertInstanceOf(IllegalArgumentException.class, error));
                    return Future.succeededFuture();
                })
                .compose(ignored -> service.updateAssignmentStatus(
                                "missing-job", JobAssignmentStatus.ACCEPTED).mapEmpty()
                        .recover(error -> {
                            context.verify(() -> assertInstanceOf(IllegalArgumentException.class, error));
                            return Future.succeededFuture();
                        }))
                .compose(ignored -> service.submitJob(request("payment-file-002"),
                        new JobRequirements.Builder().tenantId(TENANT_ID).build(), JobPriority.NORMAL))
                .compose(queued -> service.cancelAssignment(queued.getJobId()).map(queued.getJobId()))
                .compose(jobId -> {
                    context.verify(() -> assertFalse(service.getJobQueue().containsKey(jobId)));
                    service.removeAgent("unknown-agent");
                    return service.cancelAssignment("unknown-job");
                })
                .onComplete(context.succeedingThenComplete());
    }

    private Future<Void> awaitLeadership(Vertx vertx) {
        Promise<Void> ready = Promise.promise();
        long periodicId = vertx.setPeriodic(10, timerId -> {
            if (raftNode.isLeader() && !ready.future().isComplete()) {
                vertx.cancelTimer(timerId);
                ready.complete();
            }
        });
        vertx.setTimer(2_000, timerId -> {
            if (!ready.future().isComplete()) {
                vertx.cancelTimer(periodicId);
                ready.fail("single-node controller did not become leader");
            }
        });
        return ready.future();
    }

    private static AgentInfo healthyAgent(String agentId) {
        AgentInfo agent = new AgentInfo(agentId, agentId + ".example.test", "127.0.0.1", 8081);
        agent.setTenantId(TENANT_ID);
        agent.setStatus(AgentStatus.HEALTHY);
        return agent;
    }

    private static TransferRequest request(String requestId) {
        return TransferRequest.builder()
                .requestId(requestId)
                .sourceUri(URI.create("sftp://payments.example.test/outbound/" + requestId + ".dat"))
                .destinationPath(Path.of("target", "settlement", requestId + ".dat"))
                .expectedSize(1_024)
                .build();
    }
}
