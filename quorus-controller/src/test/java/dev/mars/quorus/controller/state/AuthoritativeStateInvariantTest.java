/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.mars.quorus.controller.state;

import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.core.JobAssignment;
import dev.mars.quorus.core.JobAssignmentStatus;
import dev.mars.quorus.core.RouteConfiguration;
import dev.mars.quorus.core.RouteStatus;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Verifies invariants at the replicated state-machine boundary, not at HTTP handlers. */
class AuthoritativeStateInvariantTest {

    private QuorusStateStore store;

    @BeforeEach
    void setUp() {
        store = new QuorusStateStore();
    }

    @Test
    void assignmentRequiresExistingTransferJob() {
        AgentInfo agent = agent("agent-a", "tenant-a", AgentStatus.HEALTHY);
        store.apply(AgentCommand.register(agent));

        CommandResult<?> result = store.apply(JobAssignmentCommand.assign(
                assignment("job-missing", "agent-a", "tenant-a")));

        CommandResult.NotFound<?> notFound = assertInstanceOf(CommandResult.NotFound.class, result);
        assertEquals("TransferJob", notFound.entityType());
        assertNull(store.getJobAssignment("job-missing:agent-a"));
    }

    @Test
    void assignmentRequiresExistingAgent() {
        store.apply(TransferJobCommand.create(job("job-a", 100), "tenant-a"));

        CommandResult<?> result = store.apply(JobAssignmentCommand.assign(
                assignment("job-a", "agent-missing", "tenant-a")));

        CommandResult.NotFound<?> notFound = assertInstanceOf(CommandResult.NotFound.class, result);
        assertEquals("Agent", notFound.entityType());
    }

    @Test
    void assignmentRejectsCrossTenantReferences() {
        store.apply(TransferJobCommand.create(job("job-a", 100), "tenant-a"));
        store.apply(AgentCommand.register(agent("agent-b", "tenant-b", AgentStatus.HEALTHY)));

        CommandResult<?> result = store.apply(JobAssignmentCommand.assign(
                assignment("job-a", "agent-b", "tenant-a")));

        assertRejected(result, "TENANT_MISMATCH");
        assertNull(store.getJobAssignment("job-a:agent-b"));
    }

    @Test
    void assignmentLifecycleCannotSkipAcceptedAndInProgress() {
        createValidAssignment("job-a", "agent-a", "tenant-a");

        CommandResult<?> result = store.apply(JobAssignmentCommand.updateStatus(
                "job-a:agent-a", JobAssignmentStatus.ASSIGNED, JobAssignmentStatus.COMPLETED));

        assertRejected(result, "INVALID_STATE_TRANSITION");
        assertEquals(JobAssignmentStatus.ASSIGNED,
                store.getJobAssignment("job-a:agent-a").getStatus());
    }

    @Test
    void assignmentLifecycleAcceptsLegalSuccessSequence() {
        createValidAssignment("job-a", "agent-a", "tenant-a");

        assertInstanceOf(CommandResult.Success.class, store.apply(JobAssignmentCommand.updateStatus(
                "job-a:agent-a", JobAssignmentStatus.ASSIGNED, JobAssignmentStatus.ACCEPTED)));
        assertInstanceOf(CommandResult.Success.class, store.apply(JobAssignmentCommand.updateStatus(
                "job-a:agent-a", JobAssignmentStatus.ACCEPTED, JobAssignmentStatus.IN_PROGRESS)));
        assertInstanceOf(CommandResult.Success.class, store.apply(JobAssignmentCommand.updateStatus(
                "job-a:agent-a", JobAssignmentStatus.IN_PROGRESS, JobAssignmentStatus.COMPLETED)));
    }

    @Test
    void referencedTransferAndAgentCannotBeDeleted() {
        createValidAssignment("job-a", "agent-a", "tenant-a");

        assertRejected(store.apply(TransferJobCommand.delete("job-a")), "DEPENDENT_ENTITY_EXISTS");
        assertRejected(store.apply(AgentCommand.deregister("agent-a")), "DEPENDENT_ENTITY_EXISTS");
    }

    @Test
    void transferStatusAndProgressMustRemainLegal() {
        store.apply(TransferJobCommand.create(job("job-a", 100), "tenant-a"));

        assertRejected(store.apply(TransferJobCommand.updateStatus(
                "job-a", TransferStatus.PENDING, TransferStatus.COMPLETED)), "INVALID_STATE_TRANSITION");
        assertInstanceOf(CommandResult.Success.class, store.apply(TransferJobCommand.updateProgress("job-a", 50)));
        assertRejected(store.apply(TransferJobCommand.updateProgress("job-a", 49)), "INVALID_PROGRESS");
        assertRejected(store.apply(TransferJobCommand.updateProgress("job-a", 101)), "INVALID_PROGRESS");
    }

    @Test
    void agentAndRouteTransitionsAreCheckedDuringApplication() {
        store.apply(AgentCommand.register(agent("agent-a", "tenant-a", AgentStatus.FAILED)));
        assertRejected(store.apply(AgentCommand.updateStatus(
                "agent-a", AgentStatus.FAILED, AgentStatus.HEALTHY)), "INVALID_STATE_TRANSITION");

        RouteConfiguration route = route("route-a");
        store.apply(RouteCommand.create(route));
        assertRejected(store.apply(RouteCommand.updateStatus(
                "route-a", RouteStatus.CONFIGURED, RouteStatus.TRIGGERED, "invalid skip")),
                "INVALID_STATE_TRANSITION");
    }

    @Test
    void duplicateCreatesAreDeterministicallyRejected() {
        TransferJob job = job("job-a", 100);
        store.apply(TransferJobCommand.create(job, "tenant-a"));

        assertRejected(store.apply(TransferJobCommand.create(job, "tenant-a")), "DUPLICATE_ENTITY");
    }

    private void createValidAssignment(String jobId, String agentId, String tenantId) {
        store.apply(TransferJobCommand.create(job(jobId, 100), tenantId));
        store.apply(AgentCommand.register(agent(agentId, tenantId, AgentStatus.HEALTHY)));
        assertInstanceOf(CommandResult.Success.class, store.apply(JobAssignmentCommand.assign(
                assignment(jobId, agentId, tenantId))));
    }

    private static TransferJob job(String jobId, long expectedSize) {
        TransferRequest request = TransferRequest.builder()
                .requestId(jobId)
                .sourceUri(URI.create("https://files.example.test/input.dat"))
                .destinationPath(Path.of("data", "input.dat"))
                .expectedSize(expectedSize)
                .build();
        return new TransferJob(request);
    }

    private static AgentInfo agent(String agentId, String tenantId, AgentStatus status) {
        AgentInfo agent = new AgentInfo(agentId, agentId + ".example.test", "127.0.0.1", 8080);
        agent.setTenantId(tenantId);
        agent.setStatus(status);
        return agent;
    }

    private static JobAssignment assignment(String jobId, String agentId, String tenantId) {
        return new JobAssignment.Builder()
                .jobId(jobId)
                .agentId(agentId)
                .tenantId(tenantId)
                .status(JobAssignmentStatus.ASSIGNED)
                .assignedAt(Instant.parse("2026-09-01T00:00:00Z"))
                .build();
    }

    private static RouteConfiguration route(String routeId) {
        return new RouteConfiguration(
                routeId, "Phase 0 route", "Invariant test route",
                "source-agent", "/source", "destination-agent", "/destination",
                null, RouteStatus.CONFIGURED, null,
                Instant.parse("2026-09-01T00:00:00Z"), null);
    }

    private static void assertRejected(CommandResult<?> result, String expectedCode) {
        CommandResult.Rejected<?> rejected = assertInstanceOf(CommandResult.Rejected.class, result);
        assertEquals(expectedCode, rejected.code());
    }
}
