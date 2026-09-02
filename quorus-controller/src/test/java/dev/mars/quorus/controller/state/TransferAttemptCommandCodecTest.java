/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package dev.mars.quorus.controller.state;

import dev.mars.quorus.core.TransferAttempt;
import dev.mars.quorus.core.TransferAttemptOutcome;
import dev.mars.quorus.core.TransferAttemptStatus;
import dev.mars.quorus.core.JobAssignmentStatus;
import dev.mars.quorus.core.TransferStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 2 persisted-command compatibility for attempt and fence operations. */
class TransferAttemptCommandCodecTest {

    private static final Instant NOW = Instant.parse("2026-09-02T02:00:00Z");

    @Test
    void offerRoundtripPreservesAttemptIdentityLeaseAndFence() {
        TransferAttempt attempt = new TransferAttempt.Builder()
                .attemptId("attempt-7")
                .jobId("job-7")
                .agentId("agent-7")
                .tenantId("tenant-7")
                .attemptNumber(3)
                .fencingGeneration(9)
                .leaseExpiresAt(NOW.plusSeconds(30))
                .status(TransferAttemptStatus.OFFERED)
                .outcome(TransferAttemptOutcome.NONE)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();

        TransferAttemptCommand.Offer restored = assertInstanceOf(TransferAttemptCommand.Offer.class,
                ProtobufCommandCodec.deserialize(ProtobufCommandCodec.serialize(
                        TransferAttemptCommand.offer(attempt, "attempt-6"))));

        assertEquals("attempt-6", restored.expectedActiveAttemptId());
        assertEquals("attempt-7", restored.attempt().getAttemptId());
        assertEquals(3, restored.attempt().getAttemptNumber());
        assertEquals(9, restored.attempt().getFencingGeneration());
        assertEquals(NOW.plusSeconds(30), restored.attempt().getLeaseExpiresAt());
    }

    @Test
    void reportAndLeaseRoundtripPreserveOrderingAndOutcome() {
        TransferAttemptCommand.Report report = TransferAttemptCommand.report(
                "attempt-7", 9, 4, TransferAttemptStatus.IN_PROGRESS,
                TransferAttemptStatus.FAILED, 512, TransferAttemptOutcome.RETRYABLE_FAILURE,
                "remote service unavailable", NOW);
        TransferAttemptCommand.Report restoredReport = assertInstanceOf(
                TransferAttemptCommand.Report.class,
                ProtobufCommandCodec.deserialize(ProtobufCommandCodec.serialize(report)));
        assertEquals(report, restoredReport);

        TransferAttemptCommand.RenewLease renew = TransferAttemptCommand.renewLease(
                "attempt-7", 9, 5, NOW.plusSeconds(60), NOW);
        TransferAttemptCommand.RenewLease restoredRenew = assertInstanceOf(
                TransferAttemptCommand.RenewLease.class,
                ProtobufCommandCodec.deserialize(ProtobufCommandCodec.serialize(renew)));
        assertEquals(renew, restoredRenew);
    }

    @Test
    void lifecycleReportRoundtripPreservesEveryExpectedAndTargetState() {
        TransferAttemptCommand.LifecycleReport report = TransferAttemptCommand.lifecycleReport(
                "attempt-7", 9, 5, TransferAttemptStatus.ACCEPTED,
                TransferAttemptStatus.IN_PROGRESS, 768, TransferAttemptOutcome.NONE, null,
                "job-7:agent-7", JobAssignmentStatus.ACCEPTED,
                JobAssignmentStatus.IN_PROGRESS, "job-7",
                TransferStatus.PENDING, TransferStatus.IN_PROGRESS, NOW);

        TransferAttemptCommand.LifecycleReport restored = assertInstanceOf(
                TransferAttemptCommand.LifecycleReport.class,
                ProtobufCommandCodec.deserialize(ProtobufCommandCodec.serialize(report)));

        assertEquals(report, restored);
    }
}
