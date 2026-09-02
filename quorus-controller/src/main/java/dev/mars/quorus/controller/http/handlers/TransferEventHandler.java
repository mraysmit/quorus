/* Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache 2.0. */
package dev.mars.quorus.controller.http.handlers;

import dev.mars.quorus.controller.http.ErrorCode;
import dev.mars.quorus.controller.http.QuorusApiException;
import dev.mars.quorus.controller.security.SecurityContext;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.TransferEvent;
import dev.mars.quorus.controller.state.TransferJobSnapshot;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/** Tenant-checked read boundary for the durable transfer event ledger. */
public final class TransferEventHandler implements Handler<RoutingContext> {
    private final QuorusStateStore stateStore;

    public TransferEventHandler(QuorusStateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Override
    public void handle(RoutingContext context) {
        String jobId = context.pathParam("jobId");
        TransferJobSnapshot job = stateStore.findTransferJob(jobId)
                .orElseThrow(() -> QuorusApiException.notFound(ErrorCode.TRANSFER_NOT_FOUND, jobId));
        SecurityContext.trustedTenant(context, job.getTenantId());
        JsonArray events = new JsonArray();
        for (TransferEvent event : stateStore.findTransferEvents(jobId)) {
            events.add(JsonObject.mapFrom(event));
        }
        context.json(new JsonObject().put("jobId", jobId).put("events", events));
    }
}
