/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.mars.quorus.controller.http;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HTTP middleware that rejects non-health requests when the server is draining.
 *
 * <p>During drain mode (graceful shutdown), all requests except {@code /health/*}
 * receive a {@code 503 Service Unavailable} response with a {@code Retry-After}
 * header. Health probes continue to function so load balancers can detect
 * the server is shutting down.</p>
 *
 * <p>Error responses use the standard {@link ErrorResponse} envelope via
 * {@link ErrorCode#SERVICE_UNAVAILABLE} for consistency with {@link GlobalErrorHandler}.</p>
 *
 * <p>Single Responsibility: This handler owns only drain-mode gating logic.
 * The drain state itself is managed via {@link #enterDrainMode()} / {@link #isDraining()}.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-19
 */
public class DrainModeHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(DrainModeHandler.class);

    /** Default number of seconds suggested for client retry. */
    private static final String DEFAULT_RETRY_AFTER = "30";

    private final AtomicBoolean draining = new AtomicBoolean(false);

    @Override
    public void handle(RoutingContext ctx) {
        if (!draining.get()) {
            ctx.next();
            return;
        }

        String path = ctx.request().path();

        // Allow health probes during drain so load balancers can observe shutdown
        if (path.startsWith("/health")) {
            ctx.next();
            return;
        }

        // Reject all other requests with a standard error envelope
        logger.debug("Rejecting request during drain: {} {}", ctx.request().method(), path);

        String requestId = CorrelationIdHandler.getRequestId(ctx);
        ErrorResponse errorResponse = ErrorResponse.withMessage(
                ErrorCode.SERVICE_SHUTTING_DOWN, path, "Server is shutting down", requestId);

        ctx.response()
                .setStatusCode(errorResponse.httpStatus())
                .putHeader("Retry-After", DEFAULT_RETRY_AFTER)
                .putHeader("Content-Type", "application/json")
                .end(errorResponse.toJson().encode());
    }

    /**
     * Enters drain mode — stops accepting new API requests but allows health probes.
     *
     * <p>This method is idempotent; calling it multiple times has no additional effect.</p>
     */
    public void enterDrainMode() {
        if (draining.compareAndSet(false, true)) {
            logger.info("HTTP API Server entered drain mode — rejecting new requests");
        } else {
            logger.debug("Already in drain mode");
        }
    }

    /**
     * Checks if the server is currently in drain mode.
     *
     * @return {@code true} if drain mode is active
     */
    public boolean isDraining() {
        return draining.get();
    }
}
