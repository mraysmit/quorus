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

/**
 * Exception thrown by API handlers to indicate a known error condition.
 * 
 * <p>This exception carries an {@link ErrorCode} which determines the HTTP
 * status code and error format returned to the client. The 
 * {@link GlobalErrorHandler} will catch this and convert it to a standardized
 * {@link ErrorResponse}.</p>
 * 
 * <p>Usage in handlers:</p>
 * <pre>{@code
 * if (job == null) {
 *     throw QuorusApiException.notFound(ErrorCode.TRANSFER_NOT_FOUND, jobId);
 * }
 * }</pre>
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-04
 */
public class QuorusApiException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Creates a new API exception with the given error code and message.
     */
    public QuorusApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Creates a new API exception with the given error code, message, and cause.
     */
    public QuorusApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Returns the error code for this exception.
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the HTTP status code for this exception.
     */
    public int getHttpStatus() {
        return errorCode.httpStatus();
    }

    // ==================== Factory Methods ====================

    /**
     * Creates a "not found" exception.
     */
    public static QuorusApiException notFound(ErrorCode code, Object... args) {
        return new QuorusApiException(code, code.formatMessage(args));
    }

    /**
     * Creates a "bad request" exception.
     */
    public static QuorusApiException badRequest(ErrorCode code, Object... args) {
        return new QuorusApiException(code, code.formatMessage(args));
    }

    /**
     * Creates a "conflict" exception.
     */
    public static QuorusApiException conflict(ErrorCode code, Object... args) {
        return new QuorusApiException(code, code.formatMessage(args));
    }

    /**
     * Creates an "unavailable" exception (503).
     */
    public static QuorusApiException unavailable(ErrorCode code, Object... args) {
        return new QuorusApiException(code, code.formatMessage(args));
    }

    /**
     * Creates an "internal error" exception.
     */
    public static QuorusApiException internal(ErrorCode code, Throwable cause, Object... args) {
        return new QuorusApiException(code, code.formatMessage(args), cause);
    }

    /**
     * Creates an exception for "not leader" scenarios.
     */
    public static QuorusApiException notLeader(String currentLeader) {
        return new QuorusApiException(
            ErrorCode.NOT_LEADER, 
            ErrorCode.NOT_LEADER.formatMessage(currentLeader != null ? currentLeader : "unknown")
        );
    }

    /**
     * Creates an exception for "no leader" scenarios.
     */
    public static QuorusApiException noLeader() {
        return new QuorusApiException(ErrorCode.NO_LEADER, ErrorCode.NO_LEADER.messageTemplate());
    }
}
