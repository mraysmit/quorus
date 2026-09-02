/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package dev.mars.quorus.controller.state;

import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.core.TransferAttempt;
import dev.mars.quorus.core.TransferAttemptOutcome;
import dev.mars.quorus.core.TransferAttemptStatus;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.JobAssignment;
import dev.mars.quorus.core.JobAssignmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 2 authoritative attempt, fencing, ordering, and recovery behavior. */
class TransferAttemptStateStoreTest {

    private static final String TENANT = "tenant-a";
    private static final String AGENT = "agent-a";
    private static final Instant BASE_TIME = Instant.parse("2026-09-02T02:00:00Z");

    private QuorusStateStore store;
    private String jobId;

    @BeforeEach
    void setUp() {
        store = new QuorusStateStore();
        TransferRequest request = TransferRequest.builder()
                .requestId("phase2-job")
                .sourceUri(URI.create("sftp://source.example.test/out/report.dat"))
                .destinationPath(Path.of("target", "report.dat"))
                .expectedSize(1_024L)
                .build();
        TransferJob job = new TransferJob(request);
        jobId = job.getJobId();
        assertInstanceOf(CommandResult.Success.class,
                store.apply(TransferJobCommand.create(job, TENANT)));

        AgentInfo agent = new AgentInfo(AGENT, "agent-a.example.test", "10.0.0.10", 8080);
        agent.setTenantId(TENANT);
        agent.setStatus(AgentStatus.HEALTHY);
        assertInstanceOf(CommandResult.Success.class, store.apply(AgentCommand.register(agent)));
    }

    @Test
    void replacementAttemptFencesDelayedReportsFromThePreviousAgentLease() {
        TransferAttempt first = attempt("attempt-1", 1, 1);
        assertInstanceOf(CommandResult.Success.class,
                store.apply(TransferAttemptCommand.offer(first, null)));

        assertInstanceOf(CommandResult.Success.class, store.apply(TransferAttemptCommand.report(
                first.getAttemptId(), 1, 1, TransferAttemptStatus.OFFERED,
                TransferAttemptStatus.ACCEPTED, 0, TransferAttemptOutcome.NONE, null, BASE_TIME.plusSeconds(1))));

        TransferAttempt replacement = attempt("attempt-2", 2, 2);
        assertInstanceOf(CommandResult.Success.class,
                store.apply(TransferAttemptCommand.offer(replacement, first.getAttemptId())));

        CommandResult<?> delayed = store.apply(TransferAttemptCommand.report(
                first.getAttemptId(), 1, 2, TransferAttemptStatus.ACCEPTED,
                TransferAttemptStatus.IN_PROGRESS, 128, TransferAttemptOutcome.NONE, null,
                BASE_TIME.plusSeconds(2)));

        CommandResult.Rejected<?> rejected = assertInstanceOf(CommandResult.Rejected.class, delayed);
        assertEquals("STALE_FENCE", rejected.code());
        TransferAttempt fenced = store.findTransferAttempt(first.getAttemptId()).orElseThrow();
        assertEquals(TransferAttemptStatus.FENCED, fenced.getStatus());
        assertEquals(TransferAttemptOutcome.SUPERSEDED, fenced.getOutcome());
        assertEquals(replacement.getAttemptId(),
                store.findActiveTransferAttempt(jobId).orElseThrow().getAttemptId());
    }

    @Test
    void reportsAreOrderedAndExactDuplicatesAreIdempotent() {
        TransferAttempt attempt = attempt("attempt-1", 1, 1);
        store.apply(TransferAttemptCommand.offer(attempt, null));

        TransferAttemptCommand.Report accepted = TransferAttemptCommand.report(
                attempt.getAttemptId(), 1, 1, TransferAttemptStatus.OFFERED,
                TransferAttemptStatus.ACCEPTED, 0, TransferAttemptOutcome.NONE, null,
                BASE_TIME.plusSeconds(1));
        assertInstanceOf(CommandResult.Success.class, store.apply(accepted));
        assertInstanceOf(CommandResult.Success.class, store.apply(accepted),
                "An identical retry must return the existing authoritative result");

        CommandResult<?> stale = store.apply(TransferAttemptCommand.report(
                attempt.getAttemptId(), 1, 1, TransferAttemptStatus.ACCEPTED,
                TransferAttemptStatus.IN_PROGRESS, 64, TransferAttemptOutcome.NONE, null,
                BASE_TIME.plusSeconds(2)));
        CommandResult.Rejected<?> rejected = assertInstanceOf(CommandResult.Rejected.class, stale);
        assertEquals("STALE_REPORT_SEQUENCE", rejected.code());

        CommandResult<?> gap = store.apply(TransferAttemptCommand.report(
                attempt.getAttemptId(), 1, 3, TransferAttemptStatus.ACCEPTED,
                TransferAttemptStatus.IN_PROGRESS, 64, TransferAttemptOutcome.NONE, null,
                BASE_TIME.plusSeconds(3)));
        assertEquals("REPORT_SEQUENCE_GAP",
                assertInstanceOf(CommandResult.Rejected.class, gap).code());
    }

    @Test
    void attemptLifecycleAndProgressAreValidatedAuthoritatively() {
        TransferAttempt attempt = attempt("attempt-1", 1, 1);
        store.apply(TransferAttemptCommand.offer(attempt, null));
        store.apply(TransferAttemptCommand.report(attempt.getAttemptId(), 1, 1,
                TransferAttemptStatus.OFFERED, TransferAttemptStatus.ACCEPTED,
                0, TransferAttemptOutcome.NONE, null, BASE_TIME.plusSeconds(1)));
        store.apply(TransferAttemptCommand.report(attempt.getAttemptId(), 1, 2,
                TransferAttemptStatus.ACCEPTED, TransferAttemptStatus.IN_PROGRESS,
                256, TransferAttemptOutcome.NONE, null, BASE_TIME.plusSeconds(2)));

        CommandResult<?> mismatchedTerminalOutcome = store.apply(TransferAttemptCommand.report(
                attempt.getAttemptId(), 1, 3, TransferAttemptStatus.IN_PROGRESS,
                TransferAttemptStatus.CANCELLED, 256, TransferAttemptOutcome.SUPERSEDED,
                "operator cancelled", BASE_TIME.plusSeconds(3)));
        assertEquals("INVALID_ATTEMPT_OUTCOME",
                assertInstanceOf(CommandResult.Rejected.class, mismatchedTerminalOutcome).code());

        CommandResult<?> regressedProgress = store.apply(TransferAttemptCommand.report(
                attempt.getAttemptId(), 1, 3, TransferAttemptStatus.IN_PROGRESS,
                TransferAttemptStatus.IN_PROGRESS, 128, TransferAttemptOutcome.NONE, null,
                BASE_TIME.plusSeconds(3)));
        assertEquals("PROGRESS_REGRESSION",
                assertInstanceOf(CommandResult.Rejected.class, regressedProgress).code());

        CommandResult<?> completed = store.apply(TransferAttemptCommand.report(
                attempt.getAttemptId(), 1, 3, TransferAttemptStatus.IN_PROGRESS,
                TransferAttemptStatus.COMPLETED, 1_024, TransferAttemptOutcome.SUCCEEDED, null,
                BASE_TIME.plusSeconds(4)));
        TransferAttempt terminal = (TransferAttempt) assertInstanceOf(
                CommandResult.Success.class, completed).entity();
        assertEquals(TransferAttemptStatus.COMPLETED, terminal.getStatus());
        assertEquals(TransferAttemptOutcome.SUCCEEDED, terminal.getOutcome());
        assertTrue(store.findActiveTransferAttempt(jobId).isEmpty());
    }

    @Test
    void terminalReportRetryIsIdempotentAfterTheActiveFenceIsReleased() {
        TransferAttempt attempt = attempt("attempt-1", 1, 1);
        store.apply(TransferAttemptCommand.offer(attempt, null));
        store.apply(TransferAttemptCommand.report(attempt.getAttemptId(), 1, 1,
                TransferAttemptStatus.OFFERED, TransferAttemptStatus.ACCEPTED,
                0, TransferAttemptOutcome.NONE, null, BASE_TIME.plusSeconds(1)));
        store.apply(TransferAttemptCommand.report(attempt.getAttemptId(), 1, 2,
                TransferAttemptStatus.ACCEPTED, TransferAttemptStatus.IN_PROGRESS,
                1_024, TransferAttemptOutcome.NONE, null, BASE_TIME.plusSeconds(2)));

        TransferAttemptCommand.Report completion = TransferAttemptCommand.report(
                attempt.getAttemptId(), 1, 3, TransferAttemptStatus.IN_PROGRESS,
                TransferAttemptStatus.COMPLETED, 1_024, TransferAttemptOutcome.SUCCEEDED,
                null, BASE_TIME.plusSeconds(3));

        TransferAttempt firstResult = (TransferAttempt) assertInstanceOf(
                CommandResult.Success.class, store.apply(completion)).entity();
        TransferAttempt retryResult = (TransferAttempt) assertInstanceOf(
                CommandResult.Success.class, store.apply(completion),
                "A lost completion response must be safely recoverable by retrying").entity();
        assertEquals(firstResult.getUpdatedAt(), retryResult.getUpdatedAt());
        assertEquals(TransferAttemptStatus.COMPLETED, retryResult.getStatus());
    }

    @Test
    void leaseRenewalRequiresTheCurrentFenceAndNextReportSequence() {
        TransferAttempt attempt = attempt("attempt-1", 1, 1);
        store.apply(TransferAttemptCommand.offer(attempt, null));

        Instant renewedUntil = BASE_TIME.plusSeconds(120);
        CommandResult<?> renewed = store.apply(TransferAttemptCommand.renewLease(
                attempt.getAttemptId(), 1, 1, renewedUntil, BASE_TIME.plusSeconds(1)));
        TransferAttempt updated = (TransferAttempt) assertInstanceOf(
                CommandResult.Success.class, renewed).entity();
        assertEquals(renewedUntil, updated.getLeaseExpiresAt());
        assertEquals(1, updated.getLastReportSequence());

        CommandResult<?> staleFence = store.apply(TransferAttemptCommand.renewLease(
                attempt.getAttemptId(), 0, 2, renewedUntil.plusSeconds(30), BASE_TIME.plusSeconds(2)));
        assertEquals("STALE_FENCE",
                assertInstanceOf(CommandResult.Rejected.class, staleFence).code());
    }

    @Test
    void expiredLeaseRejectsAReportWithoutChangingAuthoritativeState() {
        TransferAttempt attempt = attempt("attempt-expired", 1, 1);
        store.apply(TransferAttemptCommand.offer(attempt, null));

        CommandResult<?> result = store.apply(TransferAttemptCommand.report(
                attempt.getAttemptId(), 1, 1, TransferAttemptStatus.OFFERED,
                TransferAttemptStatus.ACCEPTED, 0, TransferAttemptOutcome.NONE, null,
                attempt.getLeaseExpiresAt().plusMillis(1)));

        assertEquals("LEASE_EXPIRED", assertInstanceOf(CommandResult.Rejected.class, result).code());
        TransferAttempt unchanged = store.findTransferAttempt(attempt.getAttemptId()).orElseThrow();
        assertEquals(TransferAttemptStatus.OFFERED, unchanged.getStatus());
        assertEquals(0, unchanged.getLastReportSequence());

        CommandResult<?> renewal = store.apply(TransferAttemptCommand.renewLease(
                attempt.getAttemptId(), 1, 1, attempt.getLeaseExpiresAt().plusSeconds(30),
                attempt.getLeaseExpiresAt().plusMillis(1)));
        assertEquals("LEASE_EXPIRED", assertInstanceOf(CommandResult.Rejected.class, renewal).code());
    }

    @Test
    void snapshotRoundtripRetainsAttemptHistoryAndTheActiveFence() {
        TransferAttempt first = attempt("attempt-1", 1, 1);
        store.apply(TransferAttemptCommand.offer(first, null));
        TransferAttempt second = attempt("attempt-2", 2, 2);
        store.apply(TransferAttemptCommand.offer(second, first.getAttemptId()));

        byte[] snapshot = store.takeSnapshot();
        QuorusStateStore restored = new QuorusStateStore();
        restored.restoreSnapshot(snapshot);

        assertEquals(2, restored.getTransferAttempts().size());
        assertEquals(TransferAttemptStatus.FENCED,
                restored.findTransferAttempt(first.getAttemptId()).orElseThrow().getStatus());
        TransferAttempt active = restored.findActiveTransferAttempt(jobId).orElseThrow();
        assertEquals(second.getAttemptId(), active.getAttemptId());
        assertEquals(2, active.getFencingGeneration());
    }

    @Test
    void assignmentAndFirstAttemptAreCommittedAtomically() {
        JobAssignment assignment = new JobAssignment.Builder()
                .jobId(jobId)
                .agentId(AGENT)
                .tenantId(TENANT)
                .status(JobAssignmentStatus.ASSIGNED)
                .assignedAt(BASE_TIME)
                .build();

        CommandResult<?> invalid = store.apply(JobAssignmentCommand.assignWithAttempt(
                assignment, "attempt-atomic", BASE_TIME));
        assertEquals("INVALID_LEASE", assertInstanceOf(CommandResult.Rejected.class, invalid).code());
        assertTrue(store.getJobAssignments().isEmpty());
        assertTrue(store.getTransferAttempts().isEmpty());

        CommandResult<?> result = store.apply(JobAssignmentCommand.assignWithAttempt(
                assignment, "attempt-atomic", BASE_TIME.plusSeconds(60)));
        assertInstanceOf(CommandResult.Success.class, result);
        assertEquals(1, store.getJobAssignments().size());
        TransferAttempt active = store.findActiveTransferAttempt(jobId).orElseThrow();
        assertEquals("attempt-atomic", active.getAttemptId());
        assertEquals(1, active.getAttemptNumber());
        assertEquals(1, active.getFencingGeneration());
        assertEquals(BASE_TIME.plusSeconds(60), active.getLeaseExpiresAt());
    }

    private TransferAttempt attempt(String attemptId, int attemptNumber, long fencingGeneration) {
        return new TransferAttempt.Builder()
                .attemptId(attemptId)
                .jobId(jobId)
                .agentId(AGENT)
                .tenantId(TENANT)
                .attemptNumber(attemptNumber)
                .fencingGeneration(fencingGeneration)
                .leaseExpiresAt(BASE_TIME.plusSeconds(30))
                .status(TransferAttemptStatus.OFFERED)
                .outcome(TransferAttemptOutcome.NONE)
                .createdAt(BASE_TIME)
                .updatedAt(BASE_TIME)
                .build();
    }
}
