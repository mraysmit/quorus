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
import dev.mars.quorus.core.TransferAttempt;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.time.Instant;
import java.util.Comparator;

/** HTTP read model for immutable transfer-attempt history and the authoritative active fence. */
public final class TransferAttemptHandler {

    private final QuorusStateStore stateStore;

    public TransferAttemptHandler(QuorusStateStore stateStore) {
        this.stateStore = stateStore;
    }

    public Handler<RoutingContext> handleListForTransfer() {
        return ctx -> {
            String jobId = ctx.pathParam("jobId");
            TransferJobSnapshot job = stateStore.findTransferJob(jobId)
                    .orElseThrow(() -> QuorusApiException.notFound(ErrorCode.TRANSFER_NOT_FOUND, jobId));
            SecurityContext.trustedTenant(ctx, job.getTenantId());

            JsonArray items = new JsonArray();
            stateStore.getTransferAttempts().values().stream()
                    .filter(attempt -> jobId.equals(attempt.getJobId()))
                    .sorted(Comparator.comparingInt(TransferAttempt::getAttemptNumber))
                    .map(TransferAttemptHandler::toJson)
                    .forEach(items::add);

            JsonObject response = new JsonObject()
                    .put("jobId", jobId)
                    .put("items", items)
                    .put("count", items.size());
            stateStore.findActiveTransferAttempt(jobId)
                    .ifPresent(attempt -> response.put("activeAttemptId", attempt.getAttemptId()));
            ctx.json(response);
        };
    }

    public Handler<RoutingContext> handleGet() {
        return ctx -> {
            String jobId = ctx.pathParam("jobId");
            String attemptId = ctx.pathParam("attemptId");
            TransferAttempt attempt = stateStore.findTransferAttempt(attemptId)
                    .orElseThrow(() -> QuorusApiException.notFound(ErrorCode.ATTEMPT_NOT_FOUND, attemptId));
            if (!jobId.equals(attempt.getJobId())) {
                throw QuorusApiException.notFound(ErrorCode.ATTEMPT_NOT_FOUND, attemptId);
            }
            SecurityContext.trustedTenant(ctx, attempt.getTenantId());
            ctx.json(toJson(attempt));
        };
    }

    private static JsonObject toJson(TransferAttempt attempt) {
        JsonObject json = new JsonObject()
                .put("attemptId", attempt.getAttemptId())
                .put("jobId", attempt.getJobId())
                .put("agentId", attempt.getAgentId())
                .put("tenantId", attempt.getTenantId())
                .put("attemptNumber", attempt.getAttemptNumber())
                .put("fencingGeneration", attempt.getFencingGeneration())
                .put("leaseExpiresAt", attempt.getLeaseExpiresAt().toString())
                .put("status", attempt.getStatus().name())
                .put("outcome", attempt.getOutcome().name())
                .put("lastReportSequence", attempt.getLastReportSequence())
                .put("bytesTransferred", attempt.getBytesTransferred())
                .put("createdAt", attempt.getCreatedAt().toString())
                .put("updatedAt", attempt.getUpdatedAt().toString());
        putInstant(json, "completedAt", attempt.getCompletedAt());
        if (attempt.getOutcomeReason() != null) {
            json.put("outcomeReason", attempt.getOutcomeReason());
        }
        return json;
    }

    private static void putInstant(JsonObject json, String field, Instant value) {
        if (value != null) {
            json.put(field, value.toString());
        }
    }
}
