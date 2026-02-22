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
import io.vertx.core.json.DecodeException;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global error handler for the HTTP API.
 * 
 * <p>Catches all unhandled exceptions and converts them to standardized
 * {@link ErrorResponse} JSON responses. This ensures consistent error
 * formatting across all API endpoints.</p>
 * 
 * <p>Exception mapping:</p>
 * <ul>
 *   <li>{@link IllegalArgumentException} → 400 BAD_REQUEST</li>
 *   <li>{@link DecodeException} → 400 BAD_REQUEST (JSON parsing)</li>
 *   <li>{@link QuorusApiException} → Uses exception's error code</li>
 *   <li>All others → 500 INTERNAL_ERROR</li>
 * </ul>
 * 
 * <p>Usage:</p>
 * <pre>{@code
 * Router router = Router.router(vertx);
 * router.route().failureHandler(new GlobalErrorHandler());
 * }</pre>
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-04
 */
public class GlobalErrorHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(GlobalErrorHandler.class);

    @Override
    public void handle(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        String path = ctx.request().path();
        int statusCode = ctx.statusCode();
        String requestId = CorrelationIdHandler.getRequestId(ctx);

        ErrorResponse errorResponse;

        if (failure == null) {
            // No exception, just a status code (e.g., 404 from router)
            errorResponse = mapStatusCodeToError(statusCode, path, requestId);
        } else if (failure instanceof QuorusApiException apiEx) {
            // Our custom API exception with error code
            errorResponse = ErrorResponse.withMessage(apiEx.getErrorCode(), path, apiEx.getMessage(), requestId);
            logError(apiEx.getErrorCode(), failure, path);
        } else if (failure instanceof IllegalArgumentException) {
            // Validation/argument errors
            errorResponse = ErrorResponse.fromException(ErrorCode.VALIDATION_ERROR, failure, path, requestId);
            logError(ErrorCode.VALIDATION_ERROR, failure, path);
        } else if (failure instanceof DecodeException) {
            // JSON parsing errors
            errorResponse = ErrorResponse.withMessage(ErrorCode.BAD_REQUEST, path, "Invalid JSON: " + failure.getMessage(), requestId);
            logError(ErrorCode.BAD_REQUEST, failure, path);
        } else if (failure instanceof NullPointerException) {
            // NPE - don't expose details to client
            errorResponse = ErrorResponse.withMessage(ErrorCode.INTERNAL_ERROR, path, "Unexpected error occurred", requestId);
            logger.error("NullPointerException at path {}", path, failure);
        } else {
            // Generic server error - don't expose internal details
            errorResponse = ErrorResponse.withMessage(ErrorCode.INTERNAL_ERROR, path, "An unexpected error occurred", requestId);
            logger.error("Unhandled exception at path {}: {}", path, failure.getMessage(), failure);
        }

        sendErrorResponse(ctx, errorResponse);
    }

    /**
     * Maps HTTP status codes (from router) to appropriate ErrorResponse.
     */
    private ErrorResponse mapStatusCodeToError(int statusCode, String path, String requestId) {
        return switch (statusCode) {
            case 400 -> ErrorResponse.withMessage(ErrorCode.BAD_REQUEST, path, "Bad request", requestId);
            case 401 -> ErrorResponse.withMessage(ErrorCode.UNAUTHORIZED, path, String.format(ErrorCode.UNAUTHORIZED.messageTemplate()), requestId);
            case 403 -> ErrorResponse.withMessage(ErrorCode.FORBIDDEN, path, "Access denied", requestId);
            case 404 -> ErrorResponse.withMessage(ErrorCode.NOT_FOUND, path, String.format(ErrorCode.NOT_FOUND.messageTemplate(), path), requestId);
            case 405 -> ErrorResponse.withMessage(ErrorCode.METHOD_NOT_ALLOWED, path, String.format(ErrorCode.METHOD_NOT_ALLOWED.messageTemplate(), "unknown"), requestId);
            case 409 -> ErrorResponse.withMessage(ErrorCode.CONFLICT, path, "Resource conflict", requestId);
            case 429 -> ErrorResponse.withMessage(ErrorCode.TENANT_QUOTA_EXCEEDED, path, String.format(ErrorCode.TENANT_QUOTA_EXCEEDED.messageTemplate(), "unknown", "rate limit"), requestId);
            case 500 -> ErrorResponse.withMessage(ErrorCode.INTERNAL_ERROR, path, "Internal server error", requestId);
            case 503 -> ErrorResponse.withMessage(ErrorCode.SERVICE_UNAVAILABLE, path, "Service unavailable", requestId);
            case 504 -> ErrorResponse.withMessage(ErrorCode.TIMEOUT, path, "Request timeout", requestId);
            default -> ErrorResponse.withMessage(ErrorCode.INTERNAL_ERROR, path, "Error " + statusCode, requestId);
        };
    }

    /**
     * Sends the error response to the client.
     */
    private void sendErrorResponse(RoutingContext ctx, ErrorResponse errorResponse) {
        ctx.response()
            .setStatusCode(errorResponse.httpStatus())
            .putHeader("Content-Type", "application/json")
            .end(errorResponse.toJson().encode());
    }

    /**
     * Logs the error with appropriate level based on status code.
     * The correlation ID is automatically included via SLF4J MDC (set by {@link CorrelationIdHandler}).
     */
    private void logError(ErrorCode code, Throwable failure, String path) {
        if (code.httpStatus() >= 500) {
            logger.error("Server error [{}] at {}: {}", code.code(), path, failure.getMessage(), failure);
        } else if (code.httpStatus() >= 400) {
            logger.error("Client error [{}] at {}: {}", code.code(), path, failure.getMessage());
        } else {
            logger.debug("Error [{}] at {}: {}", code.code(), path, failure.getMessage());
        }
    }
}
