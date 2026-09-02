/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package dev.mars.quorus.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferAttemptTest {

    private static final Instant CREATED = Instant.parse("2026-09-02T02:00:00Z");

    @Test
    void reportAndLeaseCopiesPreserveImmutableAttemptIdentity() {
        TransferAttempt offered = offeredAttempt();

        TransferAttempt accepted = offered.withReport(1, TransferAttemptStatus.ACCEPTED,
                0, TransferAttemptOutcome.NONE, null, CREATED.plusSeconds(1));
        TransferAttempt renewed = accepted.withLease(2, CREATED.plusSeconds(120),
                CREATED.plusSeconds(2));
        TransferAttempt completed = renewed.withReport(3, TransferAttemptStatus.COMPLETED,
                1_024, TransferAttemptOutcome.SUCCEEDED, "checksum verified",
                CREATED.plusSeconds(3));

        assertEquals("attempt-1", completed.getAttemptId());
        assertEquals("job-1", completed.getJobId());
        assertEquals("agent-1", completed.getAgentId());
        assertEquals("tenant-1", completed.getTenantId());
        assertEquals(1, completed.getAttemptNumber());
        assertEquals(1, completed.getFencingGeneration());
        assertEquals(3, completed.getLastReportSequence());
        assertEquals(1_024, completed.getBytesTransferred());
        assertEquals("checksum verified", completed.getOutcomeReason());
        assertEquals(CREATED, completed.getCreatedAt());
        assertEquals(CREATED.plusSeconds(3), completed.getUpdatedAt());
        assertEquals(CREATED.plusSeconds(3), completed.getCompletedAt());
        assertEquals(CREATED.plusSeconds(120), completed.getLeaseExpiresAt());

        assertEquals(TransferAttemptStatus.OFFERED, offered.getStatus());
        assertEquals(0, offered.getLastReportSequence());
        assertNull(offered.getCompletedAt());
    }

    @Test
    void fencingProducesTerminalSupersededEvidence() {
        TransferAttempt fenced = offeredAttempt().fenced(CREATED.plusSeconds(10));

        assertEquals(TransferAttemptStatus.FENCED, fenced.getStatus());
        assertEquals(TransferAttemptOutcome.SUPERSEDED, fenced.getOutcome());
        assertEquals("Superseded by a newer fencing generation", fenced.getOutcomeReason());
        assertEquals(CREATED.plusSeconds(10), fenced.getCompletedAt());
    }

    @Test
    void lifecycleAllowsOnlyCanonicalTransitions() {
        assertTrue(TransferAttemptStatus.OFFERED.canTransitionTo(TransferAttemptStatus.ACCEPTED));
        assertTrue(TransferAttemptStatus.ACCEPTED.canTransitionTo(TransferAttemptStatus.IN_PROGRESS));
        assertTrue(TransferAttemptStatus.IN_PROGRESS.canTransitionTo(TransferAttemptStatus.IN_PROGRESS));
        assertTrue(TransferAttemptStatus.IN_PROGRESS.canTransitionTo(TransferAttemptStatus.COMPLETED));
        assertFalse(TransferAttemptStatus.RECONCILIATION_REQUIRED
                .canTransitionTo(TransferAttemptStatus.COMPLETED));
        assertFalse(TransferAttemptStatus.OFFERED.canTransitionTo(TransferAttemptStatus.COMPLETED));
        assertFalse(TransferAttemptStatus.COMPLETED.canTransitionTo(TransferAttemptStatus.IN_PROGRESS));
        assertTrue(TransferAttemptStatus.CANCELLED.isTerminal());
        assertFalse(TransferAttemptStatus.ACCEPTED.isTerminal());
    }

    @Test
    void invalidIdentityAndCountersAreRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> copyBuilder().attemptId(" ").build());
        assertThrows(IllegalArgumentException.class,
                () -> copyBuilder().attemptNumber(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> copyBuilder().fencingGeneration(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> copyBuilder().lastReportSequence(-1).build());
        assertThrows(IllegalArgumentException.class,
                () -> copyBuilder().bytesTransferred(-1).build());
        assertThrows(NullPointerException.class,
                () -> copyBuilder().leaseExpiresAt(null).build());
        assertThrows(NullPointerException.class,
                () -> copyBuilder().status(null).build());
        assertThrows(NullPointerException.class,
                () -> copyBuilder().outcome(null).build());
    }

    private TransferAttempt offeredAttempt() {
        return copyBuilder().build();
    }

    private TransferAttempt.Builder copyBuilder() {
        return new TransferAttempt.Builder()
                .attemptId("attempt-1")
                .jobId("job-1")
                .agentId("agent-1")
                .tenantId("tenant-1")
                .attemptNumber(1)
                .fencingGeneration(1)
                .leaseExpiresAt(CREATED.plusSeconds(60))
                .status(TransferAttemptStatus.OFFERED)
                .outcome(TransferAttemptOutcome.NONE)
                .createdAt(CREATED)
                .updatedAt(CREATED);
    }
}
