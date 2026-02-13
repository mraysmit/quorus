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

import io.vertx.ext.web.RoutingContext;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * HTTP request handler that assigns and propagates a correlation ID (request ID)
 * for every incoming request.
 *
 * <p>If the client sends an {@code X-Request-ID} header, that value is used.
 * Otherwise, a new UUID-based ID is generated. The ID is:</p>
 * <ul>
 *   <li>Placed in SLF4J MDC as {@code requestId} for log correlation</li>
 *   <li>Added to the response as {@code X-Request-ID} header</li>
 *   <li>Stored in the routing context under the key {@code requestId}</li>
 * </ul>
 *
 * <p>MDC is cleaned up after the request completes to prevent leaking
 * into subsequent requests on the same thread.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-13
 */
public class CorrelationIdHandler implements io.vertx.core.Handler<RoutingContext> {

    /** HTTP header name for correlation ID */
    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    /** MDC key name */
    public static final String MDC_REQUEST_ID = "requestId";

    /** Routing context data key */
    public static final String CTX_REQUEST_ID = "requestId";

    @Override
    public void handle(RoutingContext ctx) {
        // Use client-provided ID or generate a new one
        String requestId = ctx.request().getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = generateRequestId();
        }

        // Set in MDC for SLF4J log correlation
        MDC.put(MDC_REQUEST_ID, requestId);

        // Store in routing context for downstream handlers
        ctx.put(CTX_REQUEST_ID, requestId);

        // Echo back in response header
        ctx.response().putHeader(REQUEST_ID_HEADER, requestId);

        // Clean up MDC after response is sent
        final String id = requestId;
        ctx.addEndHandler(v -> {
            MDC.remove(MDC_REQUEST_ID);
        });

        ctx.next();
    }

    /**
     * Retrieves the correlation ID from a routing context.
     * Returns null if no correlation ID was set (e.g., outside HTTP pipeline).
     */
    public static String getRequestId(RoutingContext ctx) {
        return ctx.get(CTX_REQUEST_ID);
    }

    /**
     * Generates a compact request ID in the format {@code req-XXXXXXXX}.
     */
    private static String generateRequestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
