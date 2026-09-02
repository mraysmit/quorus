/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package dev.mars.quorus.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable evidence for one execution attempt of a transfer job.
 * Identity, attempt number, and fencing generation never change after creation.
 */
@JsonDeserialize(builder = TransferAttempt.Builder.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class TransferAttempt implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String attemptId;
    private final String jobId;
    private final String agentId;
    private final String tenantId;
    private final int attemptNumber;
    private final long fencingGeneration;
    private final Instant leaseExpiresAt;
    private final TransferAttemptStatus status;
    private final TransferAttemptOutcome outcome;
    private final long lastReportSequence;
    private final long bytesTransferred;
    private final String outcomeReason;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant completedAt;

    private TransferAttempt(Builder builder) {
        attemptId = requireText(builder.attemptId, "Attempt ID");
        jobId = requireText(builder.jobId, "Job ID");
        agentId = requireText(builder.agentId, "Agent ID");
        tenantId = requireText(builder.tenantId, "Tenant ID");
        if (builder.attemptNumber < 1) {
            throw new IllegalArgumentException("Attempt number must be positive");
        }
        if (builder.fencingGeneration < 1) {
            throw new IllegalArgumentException("Fencing generation must be positive");
        }
        if (builder.lastReportSequence < 0 || builder.bytesTransferred < 0) {
            throw new IllegalArgumentException("Report sequence and bytes transferred cannot be negative");
        }
        attemptNumber = builder.attemptNumber;
        fencingGeneration = builder.fencingGeneration;
        leaseExpiresAt = Objects.requireNonNull(builder.leaseExpiresAt, "Lease expiry cannot be null");
        status = Objects.requireNonNull(builder.status, "Attempt status cannot be null");
        outcome = Objects.requireNonNull(builder.outcome, "Attempt outcome cannot be null");
        lastReportSequence = builder.lastReportSequence;
        bytesTransferred = builder.bytesTransferred;
        outcomeReason = builder.outcomeReason;
        createdAt = Objects.requireNonNull(builder.createdAt, "Created timestamp cannot be null");
        updatedAt = Objects.requireNonNull(builder.updatedAt, "Updated timestamp cannot be null");
        completedAt = builder.completedAt;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }

    public String getAttemptId() { return attemptId; }
    public String getJobId() { return jobId; }
    public String getAgentId() { return agentId; }
    public String getTenantId() { return tenantId; }
    public int getAttemptNumber() { return attemptNumber; }
    public long getFencingGeneration() { return fencingGeneration; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public TransferAttemptStatus getStatus() { return status; }
    public TransferAttemptOutcome getOutcome() { return outcome; }
    public long getLastReportSequence() { return lastReportSequence; }
    public long getBytesTransferred() { return bytesTransferred; }
    public String getOutcomeReason() { return outcomeReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public TransferAttempt withReport(long reportSequence, TransferAttemptStatus newStatus,
                                      long newBytesTransferred, TransferAttemptOutcome newOutcome,
                                      String reason, Instant timestamp) {
        return new Builder(this)
                .lastReportSequence(reportSequence)
                .status(newStatus)
                .bytesTransferred(newBytesTransferred)
                .outcome(newOutcome)
                .outcomeReason(reason)
                .updatedAt(timestamp)
                .completedAt(newStatus.isTerminal() ? timestamp : null)
                .build();
    }

    public TransferAttempt withLease(long reportSequence, Instant newLeaseExpiresAt, Instant timestamp) {
        return new Builder(this)
                .lastReportSequence(reportSequence)
                .leaseExpiresAt(newLeaseExpiresAt)
                .updatedAt(timestamp)
                .build();
    }

    public TransferAttempt fenced(Instant timestamp) {
        return new Builder(this)
                .status(TransferAttemptStatus.FENCED)
                .outcome(TransferAttemptOutcome.SUPERSEDED)
                .outcomeReason("Superseded by a newer fencing generation")
                .updatedAt(timestamp)
                .completedAt(timestamp)
                .build();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Builder {
        private String attemptId;
        private String jobId;
        private String agentId;
        private String tenantId;
        private int attemptNumber;
        private long fencingGeneration;
        private Instant leaseExpiresAt;
        private TransferAttemptStatus status = TransferAttemptStatus.OFFERED;
        private TransferAttemptOutcome outcome = TransferAttemptOutcome.NONE;
        private long lastReportSequence;
        private long bytesTransferred;
        private String outcomeReason;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = createdAt;
        private Instant completedAt;

        public Builder() { }

        public Builder(TransferAttempt existing) {
            attemptId = existing.attemptId;
            jobId = existing.jobId;
            agentId = existing.agentId;
            tenantId = existing.tenantId;
            attemptNumber = existing.attemptNumber;
            fencingGeneration = existing.fencingGeneration;
            leaseExpiresAt = existing.leaseExpiresAt;
            status = existing.status;
            outcome = existing.outcome;
            lastReportSequence = existing.lastReportSequence;
            bytesTransferred = existing.bytesTransferred;
            outcomeReason = existing.outcomeReason;
            createdAt = existing.createdAt;
            updatedAt = existing.updatedAt;
            completedAt = existing.completedAt;
        }

        public Builder attemptId(String value) { attemptId = value; return this; }
        public Builder jobId(String value) { jobId = value; return this; }
        public Builder agentId(String value) { agentId = value; return this; }
        public Builder tenantId(String value) { tenantId = value; return this; }
        public Builder attemptNumber(int value) { attemptNumber = value; return this; }
        public Builder fencingGeneration(long value) { fencingGeneration = value; return this; }
        public Builder leaseExpiresAt(Instant value) { leaseExpiresAt = value; return this; }
        public Builder status(TransferAttemptStatus value) { status = value; return this; }
        public Builder outcome(TransferAttemptOutcome value) { outcome = value; return this; }
        public Builder lastReportSequence(long value) { lastReportSequence = value; return this; }
        public Builder bytesTransferred(long value) { bytesTransferred = value; return this; }
        public Builder outcomeReason(String value) { outcomeReason = value; return this; }
        public Builder createdAt(Instant value) { createdAt = value; return this; }
        public Builder updatedAt(Instant value) { updatedAt = value; return this; }
        public Builder completedAt(Instant value) { completedAt = value; return this; }
        public TransferAttempt build() { return new TransferAttempt(this); }
    }
}
