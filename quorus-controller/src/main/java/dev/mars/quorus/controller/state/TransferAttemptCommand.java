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

import java.time.Instant;
import java.util.Objects;

/** Replicated commands for immutable attempt history, fencing, reports, and leases. */
public sealed interface TransferAttemptCommand extends RaftCommand
        permits TransferAttemptCommand.Offer, TransferAttemptCommand.Report,
                TransferAttemptCommand.LifecycleReport, TransferAttemptCommand.RenewLease {

    String attemptId();
    Instant timestamp();

    record Offer(String attemptId, TransferAttempt attempt, String expectedActiveAttemptId,
                 Instant timestamp) implements TransferAttemptCommand {
        public Offer {
            Objects.requireNonNull(attemptId, "attemptId");
            Objects.requireNonNull(attempt, "attempt");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    record Report(String attemptId, long fencingGeneration, long reportSequence,
                  TransferAttemptStatus expectedStatus, TransferAttemptStatus newStatus,
                  long bytesTransferred, TransferAttemptOutcome outcome, String reason,
                  Instant timestamp) implements TransferAttemptCommand {
        public Report {
            Objects.requireNonNull(attemptId, "attemptId");
            Objects.requireNonNull(expectedStatus, "expectedStatus");
            Objects.requireNonNull(newStatus, "newStatus");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    /** One authoritative agent report spanning attempt, assignment, transfer status, and progress. */
    record LifecycleReport(
            String attemptId, long fencingGeneration, long reportSequence,
            TransferAttemptStatus expectedStatus, TransferAttemptStatus newStatus,
            long bytesTransferred, TransferAttemptOutcome outcome, String reason,
            String assignmentId, JobAssignmentStatus expectedAssignmentStatus,
            JobAssignmentStatus newAssignmentStatus, String jobId,
            TransferStatus expectedTransferStatus, TransferStatus newTransferStatus,
            Instant timestamp) implements TransferAttemptCommand {
        public LifecycleReport {
            Objects.requireNonNull(attemptId, "attemptId");
            Objects.requireNonNull(expectedStatus, "expectedStatus");
            Objects.requireNonNull(newStatus, "newStatus");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(assignmentId, "assignmentId");
            Objects.requireNonNull(expectedAssignmentStatus, "expectedAssignmentStatus");
            Objects.requireNonNull(newAssignmentStatus, "newAssignmentStatus");
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(timestamp, "timestamp");
            if ((expectedTransferStatus == null) != (newTransferStatus == null)) {
                throw new IllegalArgumentException("Transfer statuses must both be present or absent");
            }
        }
    }

    record RenewLease(String attemptId, long fencingGeneration, long reportSequence,
                      Instant leaseExpiresAt, Instant timestamp) implements TransferAttemptCommand {
        public RenewLease {
            Objects.requireNonNull(attemptId, "attemptId");
            Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    static Offer offer(TransferAttempt attempt, String expectedActiveAttemptId) {
        return new Offer(attempt.getAttemptId(), attempt, expectedActiveAttemptId, attempt.getCreatedAt());
    }

    static Report report(String attemptId, long fencingGeneration, long reportSequence,
                         TransferAttemptStatus expectedStatus, TransferAttemptStatus newStatus,
                         long bytesTransferred, TransferAttemptOutcome outcome, String reason,
                         Instant timestamp) {
        return new Report(attemptId, fencingGeneration, reportSequence, expectedStatus, newStatus,
                bytesTransferred, outcome, reason, timestamp);
    }

    static LifecycleReport lifecycleReport(
            String attemptId, long fencingGeneration, long reportSequence,
            TransferAttemptStatus expectedStatus, TransferAttemptStatus newStatus,
            long bytesTransferred, TransferAttemptOutcome outcome, String reason,
            String assignmentId, JobAssignmentStatus expectedAssignmentStatus,
            JobAssignmentStatus newAssignmentStatus, String jobId,
            TransferStatus expectedTransferStatus, TransferStatus newTransferStatus,
            Instant timestamp) {
        return new LifecycleReport(attemptId, fencingGeneration, reportSequence,
                expectedStatus, newStatus, bytesTransferred, outcome, reason,
                assignmentId, expectedAssignmentStatus, newAssignmentStatus, jobId,
                expectedTransferStatus, newTransferStatus, timestamp);
    }

    static RenewLease renewLease(String attemptId, long fencingGeneration, long reportSequence,
                                 Instant leaseExpiresAt, Instant timestamp) {
        return new RenewLease(attemptId, fencingGeneration, reportSequence, leaseExpiresAt, timestamp);
    }
}
