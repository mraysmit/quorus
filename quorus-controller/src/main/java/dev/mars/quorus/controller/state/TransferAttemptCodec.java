/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package dev.mars.quorus.controller.state;

import dev.mars.quorus.controller.raft.grpc.TransferAttemptCommandProto;
import dev.mars.quorus.controller.raft.grpc.TransferAttemptCommandType;
import dev.mars.quorus.controller.raft.grpc.TransferAttemptOutcomeProto;
import dev.mars.quorus.controller.raft.grpc.TransferAttemptProto;
import dev.mars.quorus.controller.raft.grpc.TransferAttemptStatusProto;
import dev.mars.quorus.controller.raft.grpc.JobAssignmentStatusProto;
import dev.mars.quorus.controller.raft.grpc.TransferStatusProto;
import dev.mars.quorus.core.JobAssignmentStatus;
import dev.mars.quorus.core.TransferAttempt;
import dev.mars.quorus.core.TransferAttemptOutcome;
import dev.mars.quorus.core.TransferAttemptStatus;
import dev.mars.quorus.core.TransferStatus;

import java.time.Instant;

/** Protobuf mapping for Phase 2 transfer-attempt commands and evidence. */
final class TransferAttemptCodec {

    private TransferAttemptCodec() { }

    static TransferAttemptCommandProto toProto(TransferAttemptCommand command) {
        TransferAttemptCommandProto.Builder builder = TransferAttemptCommandProto.newBuilder()
                .setAttemptId(command.attemptId())
                .setTimestampEpochMs(command.timestamp().toEpochMilli());
        switch (command) {
            case TransferAttemptCommand.Offer offer -> {
                builder.setType(TransferAttemptCommandType.TRANSFER_ATTEMPT_CMD_OFFER)
                        .setAttempt(toProto(offer.attempt()));
                if (offer.expectedActiveAttemptId() != null) {
                    builder.setExpectedActiveAttemptId(offer.expectedActiveAttemptId());
                }
            }
            case TransferAttemptCommand.Report report -> builder
                    .setType(TransferAttemptCommandType.TRANSFER_ATTEMPT_CMD_REPORT)
                    .setFencingGeneration(report.fencingGeneration())
                    .setReportSequence(report.reportSequence())
                    .setExpectedStatus(toProto(report.expectedStatus()))
                    .setNewStatus(toProto(report.newStatus()))
                    .setBytesTransferred(report.bytesTransferred())
                    .setOutcome(toProto(report.outcome()))
                    .setReason(report.reason() == null ? "" : report.reason());
            case TransferAttemptCommand.LifecycleReport report -> {
                builder.setType(TransferAttemptCommandType.TRANSFER_ATTEMPT_CMD_LIFECYCLE_REPORT)
                        .setFencingGeneration(report.fencingGeneration())
                        .setReportSequence(report.reportSequence())
                        .setExpectedStatus(toProto(report.expectedStatus()))
                        .setNewStatus(toProto(report.newStatus()))
                        .setBytesTransferred(report.bytesTransferred())
                        .setOutcome(toProto(report.outcome()))
                        .setReason(report.reason() == null ? "" : report.reason())
                        .setAssignmentId(report.assignmentId())
                        .setExpectedAssignmentStatus(toProto(report.expectedAssignmentStatus()))
                        .setNewAssignmentStatus(toProto(report.newAssignmentStatus()))
                        .setJobId(report.jobId());
                if (report.newTransferStatus() != null) {
                    builder.setHasTransferStatus(true)
                            .setExpectedTransferStatus(toProto(report.expectedTransferStatus()))
                            .setNewTransferStatus(toProto(report.newTransferStatus()));
                }
            }
            case TransferAttemptCommand.RenewLease renew -> builder
                    .setType(TransferAttemptCommandType.TRANSFER_ATTEMPT_CMD_RENEW_LEASE)
                    .setFencingGeneration(renew.fencingGeneration())
                    .setReportSequence(renew.reportSequence())
                    .setLeaseExpiresAtEpochMs(renew.leaseExpiresAt().toEpochMilli());
        }
        return builder.build();
    }

    static TransferAttemptCommand fromProto(TransferAttemptCommandProto proto) {
        Instant timestamp = Instant.ofEpochMilli(proto.getTimestampEpochMs());
        return switch (proto.getType()) {
            case TRANSFER_ATTEMPT_CMD_OFFER -> new TransferAttemptCommand.Offer(
                    proto.getAttemptId(), fromProto(proto.getAttempt()),
                    proto.getExpectedActiveAttemptId().isEmpty() ? null : proto.getExpectedActiveAttemptId(),
                    timestamp);
            case TRANSFER_ATTEMPT_CMD_REPORT -> new TransferAttemptCommand.Report(
                    proto.getAttemptId(), proto.getFencingGeneration(), proto.getReportSequence(),
                    fromProto(proto.getExpectedStatus()), fromProto(proto.getNewStatus()),
                    proto.getBytesTransferred(), fromProto(proto.getOutcome()),
                    proto.getReason().isEmpty() ? null : proto.getReason(), timestamp);
            case TRANSFER_ATTEMPT_CMD_LIFECYCLE_REPORT -> new TransferAttemptCommand.LifecycleReport(
                    proto.getAttemptId(), proto.getFencingGeneration(), proto.getReportSequence(),
                    fromProto(proto.getExpectedStatus()), fromProto(proto.getNewStatus()),
                    proto.getBytesTransferred(), fromProto(proto.getOutcome()),
                    proto.getReason().isEmpty() ? null : proto.getReason(),
                    proto.getAssignmentId(), fromProto(proto.getExpectedAssignmentStatus()),
                    fromProto(proto.getNewAssignmentStatus()), proto.getJobId(),
                    proto.getHasTransferStatus() ? fromProto(proto.getExpectedTransferStatus()) : null,
                    proto.getHasTransferStatus() ? fromProto(proto.getNewTransferStatus()) : null,
                    timestamp);
            case TRANSFER_ATTEMPT_CMD_RENEW_LEASE -> new TransferAttemptCommand.RenewLease(
                    proto.getAttemptId(), proto.getFencingGeneration(), proto.getReportSequence(),
                    Instant.ofEpochMilli(proto.getLeaseExpiresAtEpochMs()), timestamp);
            default -> throw new IllegalArgumentException("Unknown TransferAttemptCommandType: " + proto.getType());
        };
    }

    private static TransferAttemptProto toProto(TransferAttempt attempt) {
        TransferAttemptProto.Builder builder = TransferAttemptProto.newBuilder()
                .setAttemptId(attempt.getAttemptId())
                .setJobId(attempt.getJobId())
                .setAgentId(attempt.getAgentId())
                .setTenantId(attempt.getTenantId())
                .setAttemptNumber(attempt.getAttemptNumber())
                .setFencingGeneration(attempt.getFencingGeneration())
                .setLeaseExpiresAtEpochMs(attempt.getLeaseExpiresAt().toEpochMilli())
                .setStatus(toProto(attempt.getStatus()))
                .setOutcome(toProto(attempt.getOutcome()))
                .setLastReportSequence(attempt.getLastReportSequence())
                .setBytesTransferred(attempt.getBytesTransferred())
                .setCreatedAtEpochMs(attempt.getCreatedAt().toEpochMilli())
                .setUpdatedAtEpochMs(attempt.getUpdatedAt().toEpochMilli());
        if (attempt.getOutcomeReason() != null) {
            builder.setOutcomeReason(attempt.getOutcomeReason());
        }
        if (attempt.getCompletedAt() != null) {
            builder.setCompletedAtEpochMs(attempt.getCompletedAt().toEpochMilli());
        }
        return builder.build();
    }

    private static TransferAttempt fromProto(TransferAttemptProto proto) {
        TransferAttempt.Builder builder = new TransferAttempt.Builder()
                .attemptId(proto.getAttemptId())
                .jobId(proto.getJobId())
                .agentId(proto.getAgentId())
                .tenantId(proto.getTenantId())
                .attemptNumber(proto.getAttemptNumber())
                .fencingGeneration(proto.getFencingGeneration())
                .leaseExpiresAt(Instant.ofEpochMilli(proto.getLeaseExpiresAtEpochMs()))
                .status(fromProto(proto.getStatus()))
                .outcome(fromProto(proto.getOutcome()))
                .lastReportSequence(proto.getLastReportSequence())
                .bytesTransferred(proto.getBytesTransferred())
                .createdAt(Instant.ofEpochMilli(proto.getCreatedAtEpochMs()))
                .updatedAt(Instant.ofEpochMilli(proto.getUpdatedAtEpochMs()));
        if (!proto.getOutcomeReason().isEmpty()) {
            builder.outcomeReason(proto.getOutcomeReason());
        }
        if (proto.getCompletedAtEpochMs() > 0) {
            builder.completedAt(Instant.ofEpochMilli(proto.getCompletedAtEpochMs()));
        }
        return builder.build();
    }

    private static TransferAttemptStatusProto toProto(TransferAttemptStatus status) {
        return TransferAttemptStatusProto.valueOf("TRANSFER_ATTEMPT_STATUS_" + status.name());
    }

    private static TransferAttemptStatus fromProto(TransferAttemptStatusProto status) {
        if (status == TransferAttemptStatusProto.TRANSFER_ATTEMPT_STATUS_UNSPECIFIED) {
            throw new IllegalArgumentException("Transfer attempt status is unspecified");
        }
        return TransferAttemptStatus.valueOf(status.name().substring("TRANSFER_ATTEMPT_STATUS_".length()));
    }

    private static TransferAttemptOutcomeProto toProto(TransferAttemptOutcome outcome) {
        return TransferAttemptOutcomeProto.valueOf("TRANSFER_ATTEMPT_OUTCOME_" + outcome.name());
    }

    private static TransferAttemptOutcome fromProto(TransferAttemptOutcomeProto outcome) {
        if (outcome == TransferAttemptOutcomeProto.TRANSFER_ATTEMPT_OUTCOME_UNSPECIFIED) {
            throw new IllegalArgumentException("Transfer attempt outcome is unspecified");
        }
        return TransferAttemptOutcome.valueOf(outcome.name().substring("TRANSFER_ATTEMPT_OUTCOME_".length()));
    }

    private static JobAssignmentStatusProto toProto(JobAssignmentStatus status) {
        return JobAssignmentStatusProto.valueOf("JOB_ASSIGNMENT_STATUS_" + status.name());
    }

    private static JobAssignmentStatus fromProto(JobAssignmentStatusProto status) {
        if (status == JobAssignmentStatusProto.JOB_ASSIGNMENT_STATUS_UNSPECIFIED) {
            throw new IllegalArgumentException("Job assignment status is unspecified");
        }
        return JobAssignmentStatus.valueOf(status.name().substring("JOB_ASSIGNMENT_STATUS_".length()));
    }

    private static TransferStatusProto toProto(TransferStatus status) {
        return TransferStatusProto.valueOf("TRANSFER_STATUS_" + status.name());
    }

    private static TransferStatus fromProto(TransferStatusProto status) {
        if (status == TransferStatusProto.TRANSFER_STATUS_UNSPECIFIED) {
            throw new IllegalArgumentException("Transfer status is unspecified");
        }
        return TransferStatus.valueOf(status.name().substring("TRANSFER_STATUS_".length()));
    }
}
