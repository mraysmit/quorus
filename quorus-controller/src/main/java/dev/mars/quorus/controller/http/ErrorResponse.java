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

import io.vertx.core.json.JsonObject;

import java.time.Instant;
import java.util.UUID;

/**
 * Standardized error response for all API endpoints.
 * 
 * <p>Provides consistent error formatting across the entire HTTP API,
 * including error codes, messages, timestamps, and request tracking.</p>
 * 
 * <p>Example JSON output:</p>
 * <pre>{@code
 * {
 *   "error": {
 *     "shortCode": "Q-2001",
 *     "code": "TRANSFER_NOT_FOUND",
 *     "message": "Transfer job with ID 'xyz' not found",
 *     "timestamp": "2026-02-04T10:00:00Z",
 *     "path": "/api/v1/transfers/xyz",
 *     "requestId": "req-123-456"
 *   }
 * }
 * }</pre>
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-04
 */
public record ErrorResponse(
    String shortCode,
    String code,
    String message,
    Instant timestamp,
    String path,
    String requestId
) {
    /**
     * Creates an ErrorResponse with an explicit message.
     * Use this when you have a custom message that doesn't come from ErrorCode.
     */
    public static ErrorResponse withMessage(ErrorCode code, String path, String message) {
        return new ErrorResponse(
            code.shortCode(),
            code.code(),
            message,
            Instant.now(),
            path,
            generateRequestId()
        );
    }

    /**
     * Creates an ErrorResponse with an explicit message and a pre-existing request ID.
     * Use when the correlation ID has been assigned by the {@link CorrelationIdHandler}.
     */
    public static ErrorResponse withMessage(ErrorCode code, String path, String message, String requestId) {
        return new ErrorResponse(
            code.shortCode(),
            code.code(),
            message,
            Instant.now(),
            path,
            requestId != null ? requestId : generateRequestId()
        );
    }

    /**
     * Creates an ErrorResponse with a formatted message from ErrorCode's template.
     * The messageArgs are substituted into the ErrorCode's messageTemplate.
     */
    public static ErrorResponse of(ErrorCode code, String path, Object... messageArgs) {
        return new ErrorResponse(
            code.shortCode(),
            code.code(),
            String.format(code.messageTemplate(), messageArgs),
            Instant.now(),
            path,
            generateRequestId()
        );
    }

    /**
     * Creates an ErrorResponse from an exception.
     */
    public static ErrorResponse fromException(ErrorCode code, Throwable cause, String path) {
        String message = cause.getMessage() != null 
            ? cause.getMessage() 
            : code.messageTemplate();
        return new ErrorResponse(
            code.shortCode(),
            code.code(),
            message,
            Instant.now(),
            path,
            generateRequestId()
        );
    }

    /**
     * Creates an ErrorResponse from an exception with a pre-existing request ID.
     */
    public static ErrorResponse fromException(ErrorCode code, Throwable cause, String path, String requestId) {
        String message = cause.getMessage() != null 
            ? cause.getMessage() 
            : code.messageTemplate();
        return new ErrorResponse(
            code.shortCode(),
            code.code(),
            message,
            Instant.now(),
            path,
            requestId != null ? requestId : generateRequestId()
        );
    }

    /**
     * Converts this error response to a JSON object suitable for HTTP response body.
     */
    public JsonObject toJson() {
        return new JsonObject()
            .put("error", new JsonObject()
                .put("shortCode", shortCode)
                .put("code", code)
                .put("message", message)
                .put("timestamp", timestamp.toString())
                .put("path", path)
                .put("requestId", requestId));
    }

    /**
     * Gets the HTTP status code for this error based on the error code.
     */
    public int httpStatus() {
        return ErrorCode.fromCode(code)
            .map(ErrorCode::httpStatus)
            .orElse(500);
    }

    private static String generateRequestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
