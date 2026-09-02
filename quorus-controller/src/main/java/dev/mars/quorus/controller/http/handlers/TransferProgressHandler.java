/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package dev.mars.quorus.controller.http.handlers;

import dev.mars.quorus.controller.http.ErrorCode;
import dev.mars.quorus.controller.http.QuorusApiException;
import dev.mars.quorus.controller.security.SecurityContext;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.TransferJobSnapshot;
import dev.mars.quorus.controller.state.TransferOperationalContext;
import dev.mars.quorus.core.TransferAttempt;
import dev.mars.quorus.core.TransferStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Operator-facing transfer progress, telemetry freshness, and deadline condition. */
public final class TransferProgressHandler implements Handler<RoutingContext> {

    private final QuorusStateStore stateStore;
    private final Duration freshWindow;
    private final Duration stallWindow;

    public TransferProgressHandler(QuorusStateStore stateStore) {
        this(stateStore, Duration.ofSeconds(60), Duration.ofMinutes(2));
    }

    public TransferProgressHandler(
            QuorusStateStore stateStore, Duration freshWindow, Duration stallWindow) {
        this.stateStore = stateStore;
        this.freshWindow = requirePositive(freshWindow, "freshWindow");
        this.stallWindow = requirePositive(stallWindow, "stallWindow");
        if (stallWindow.compareTo(freshWindow) <= 0) {
            throw new IllegalArgumentException("stallWindow must be greater than freshWindow");
        }
    }

    @Override
    public void handle(RoutingContext context) {
        String jobId = context.pathParam("jobId");
        TransferJobSnapshot job = stateStore.findTransferJob(jobId)
                .orElseThrow(() -> QuorusApiException.notFound(ErrorCode.TRANSFER_NOT_FOUND, jobId));
        SecurityContext.trustedTenant(context, job.getTenantId());

        Instant observedAt = Instant.now();
        TransferAttempt attempt = stateStore.findActiveTransferAttempt(jobId).orElse(null);
        TransferOperationalContext operational = job.getOperationalContext();
        Duration telemetryAge = job.getLastProgressAt() == null
                ? null : Duration.between(job.getLastProgressAt(), observedAt);
        String telemetryState = telemetryState(telemetryAge);
        Condition condition = condition(job, operational, telemetryState, telemetryAge, observedAt);

        JsonObject response = new JsonObject()
                .put("jobId", jobId)
                .put("observedAt", observedAt.toString())
                .put("bytesTransferred", job.getBytesTransferred())
                .put("totalBytes", job.getTotalBytes())
                .put("sourceSizeState", job.getTotalBytes() < 0 ? "UNKNOWN" : "KNOWN")
                .put("telemetryState", telemetryState)
                .put("condition", condition.state())
                .put("conditionReason", condition.reason())
                .put("confidence", "INSUFFICIENT_DATA")
                .put("freshnessWindowSeconds", freshWindow.toSeconds())
                .put("stallWindowSeconds", stallWindow.toSeconds())
                .put("telemetryPolicySource", "CONTROLLER_CONFIGURATION")
                .put("retryCount", attempt == null ? 0 : attempt.getAttemptNumber() - 1);

        if (job.getTotalBytes() > 0) {
            double percent = 100.0 * job.getBytesTransferred() / job.getTotalBytes();
            response.put("percentComplete", Math.round(percent * 100.0) / 100.0);
        }
        if (job.getLastProgressAt() != null) {
            response.put("lastProgressAt", job.getLastProgressAt().toString());
        }
        if (telemetryAge != null) {
            response.put("telemetryAgeSeconds", Math.max(0, telemetryAge.toSeconds()));
        }
        if ("STALLED".equals(condition.state()) && job.getLastProgressAt() != null) {
            Instant conditionSince = job.getLastProgressAt().plus(stallWindow);
            response.put("conditionSince", conditionSince.toString())
                    .put("stallDurationSeconds",
                            Math.max(0, Duration.between(conditionSince, observedAt).toSeconds()));
        }
        if (attempt != null) {
            response.put("activeAttemptId", attempt.getAttemptId())
                    .put("agentId", attempt.getAgentId())
                    .put("attemptNumber", attempt.getAttemptNumber());
            addRateAndEstimate(response, job, attempt, operational, observedAt);
        }
        addOperationalContext(response, operational, observedAt);
        context.json(response);
    }

    private static void addOperationalContext(
            JsonObject response, TransferOperationalContext operational, Instant observedAt) {
        if (operational == null) return;
        response.put("businessService", operational.businessService())
                .put("owner", operational.owner())
                .put("criticality", operational.criticality())
                .put("environment", operational.environment())
                .put("processingDate", operational.processingDate())
                .put("runbookUrl", operational.runbookUrl());
        if (operational.expectedStartAt() != null) {
            response.put("expectedStartAt", operational.expectedStartAt().toString());
        }
        if (operational.requiredCompletionAt() != null) {
            response.put("requiredCompletionAt", operational.requiredCompletionAt().toString())
                    .put("timeRemainingSeconds",
                            Duration.between(observedAt, operational.requiredCompletionAt()).toSeconds());
        }
    }

    private static void addRateAndEstimate(
            JsonObject response, TransferJobSnapshot job, TransferAttempt attempt,
            TransferOperationalContext operational, Instant observedAt) {
        long elapsedSeconds = Duration.between(attempt.getCreatedAt(), observedAt).toSeconds();
        if (elapsedSeconds <= 0 || job.getBytesTransferred() <= 0) return;
        double averageBytesPerSecond = (double) job.getBytesTransferred() / elapsedSeconds;
        response.put("averageBytesPerSecond", averageBytesPerSecond)
                .put("confidence", "LOW");
        if (job.getTotalBytes() > job.getBytesTransferred()) {
            long remainingSeconds = (long) Math.ceil(
                    (job.getTotalBytes() - job.getBytesTransferred()) / averageBytesPerSecond);
            Instant estimatedCompletion = observedAt.plusSeconds(remainingSeconds);
            response.put("estimatedCompletionAt", estimatedCompletion.toString());
            if (operational != null && operational.requiredCompletionAt() != null) {
                response.put("estimatedDeadlineMarginSeconds",
                        Duration.between(estimatedCompletion, operational.requiredCompletionAt()).toSeconds());
            }
        }
    }

    private String telemetryState(Duration telemetryAge) {
        if (telemetryAge == null || telemetryAge.isNegative()) return "UNKNOWN";
        return telemetryAge.compareTo(freshWindow) <= 0 ? "FRESH" : "STALE";
    }

    private Condition condition(
            TransferJobSnapshot job, TransferOperationalContext operational, String telemetryState,
            Duration telemetryAge, Instant observedAt) {
        if (operational != null && operational.requiredCompletionAt() != null
                && observedAt.isAfter(operational.requiredCompletionAt())) {
            return new Condition("LATE", "REQUIRED_COMPLETION_MISSED");
        }
        if (job.getStatus() == TransferStatus.IN_PROGRESS && telemetryAge != null
                && telemetryAge.compareTo(stallWindow) > 0) {
            return new Condition("STALLED", "NO_PROGRESS_WITHIN_POLICY_WINDOW");
        }
        if ("UNKNOWN".equals(telemetryState)) {
            return new Condition("UNKNOWN", "NO_PROGRESS_TELEMETRY");
        }
        if ("STALE".equals(telemetryState)) {
            return new Condition("DEGRADED", "PROGRESS_TELEMETRY_STALE");
        }
        return new Condition("ON_TRACK", "CURRENT_PROGRESS_WITHIN_KNOWN_POLICY");
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private record Condition(String state, String reason) { }
}
