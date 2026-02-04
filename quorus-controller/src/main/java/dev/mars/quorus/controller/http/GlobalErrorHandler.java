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

        ErrorResponse errorResponse;

        if (failure == null) {
            // No exception, just a status code (e.g., 404 from router)
            errorResponse = mapStatusCodeToError(statusCode, path);
        } else if (failure instanceof QuorusApiException apiEx) {
            // Our custom API exception with error code
            errorResponse = ErrorResponse.withMessage(apiEx.getErrorCode(), path, apiEx.getMessage());
            logError(apiEx.getErrorCode(), failure, path);
        } else if (failure instanceof IllegalArgumentException) {
            // Validation/argument errors
            errorResponse = ErrorResponse.fromException(ErrorCode.VALIDATION_ERROR, failure, path);
            logError(ErrorCode.VALIDATION_ERROR, failure, path);
        } else if (failure instanceof DecodeException) {
            // JSON parsing errors
            errorResponse = ErrorResponse.withMessage(ErrorCode.BAD_REQUEST, path, "Invalid JSON: " + failure.getMessage());
            logError(ErrorCode.BAD_REQUEST, failure, path);
        } else if (failure instanceof NullPointerException) {
            // NPE - don't expose details to client
            errorResponse = ErrorResponse.withMessage(ErrorCode.INTERNAL_ERROR, path, "Unexpected error occurred");
            logger.error("NullPointerException at path {}", path, failure);
        } else {
            // Generic server error - don't expose internal details
            errorResponse = ErrorResponse.withMessage(ErrorCode.INTERNAL_ERROR, path, "An unexpected error occurred");
            logger.error("Unhandled exception at path {}: {}", path, failure.getMessage(), failure);
        }

        sendErrorResponse(ctx, errorResponse);
    }

    /**
     * Maps HTTP status codes (from router) to appropriate ErrorResponse.
     */
    private ErrorResponse mapStatusCodeToError(int statusCode, String path) {
        return switch (statusCode) {
            case 400 -> ErrorResponse.withMessage(ErrorCode.BAD_REQUEST, path, "Bad request");
            case 401 -> ErrorResponse.of(ErrorCode.UNAUTHORIZED, path);
            case 403 -> ErrorResponse.withMessage(ErrorCode.FORBIDDEN, path, "Access denied");
            case 404 -> ErrorResponse.of(ErrorCode.NOT_FOUND, path, path);
            case 405 -> ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED, path, "unknown");
            case 409 -> ErrorResponse.withMessage(ErrorCode.CONFLICT, path, "Resource conflict");
            case 429 -> ErrorResponse.of(ErrorCode.TENANT_QUOTA_EXCEEDED, path, "unknown", "rate limit");
            case 500 -> ErrorResponse.withMessage(ErrorCode.INTERNAL_ERROR, path, "Internal server error");
            case 503 -> ErrorResponse.withMessage(ErrorCode.SERVICE_UNAVAILABLE, path, "Service unavailable");
            case 504 -> ErrorResponse.withMessage(ErrorCode.TIMEOUT, path, "Request timeout");
            default -> ErrorResponse.withMessage(ErrorCode.INTERNAL_ERROR, path, "Error " + statusCode);
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
     */
    private void logError(ErrorCode code, Throwable failure, String path) {
        String requestId = ""; // Would be extracted from response in real implementation
        
        if (code.httpStatus() >= 500) {
            logger.error("Server error [{}] at {}: {}", code.code(), path, failure.getMessage(), failure);
        } else if (code.httpStatus() >= 400) {
            logger.warn("Client error [{}] at {}: {}", code.code(), path, failure.getMessage());
        } else {
            logger.debug("Error [{}] at {}: {}", code.code(), path, failure.getMessage());
        }
    }
}
