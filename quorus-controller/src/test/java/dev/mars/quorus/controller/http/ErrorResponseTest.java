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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the standardized error response classes.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-04
 */
@DisplayName("Error Response Standardization Tests")
class ErrorResponseTest {

    // ==================== ErrorCode Tests ====================

    @Nested
    @DisplayName("ErrorCode")
    class ErrorCodeTests {

        @Test
        @DisplayName("Error codes have correct HTTP status codes")
        void testErrorCodeHttpStatus() {
            assertEquals(400, ErrorCode.BAD_REQUEST.httpStatus());
            assertEquals(401, ErrorCode.UNAUTHORIZED.httpStatus());
            assertEquals(403, ErrorCode.FORBIDDEN.httpStatus());
            assertEquals(404, ErrorCode.NOT_FOUND.httpStatus());
            assertEquals(404, ErrorCode.TRANSFER_NOT_FOUND.httpStatus());
            assertEquals(409, ErrorCode.CONFLICT.httpStatus());
            assertEquals(500, ErrorCode.INTERNAL_ERROR.httpStatus());
            assertEquals(503, ErrorCode.NOT_LEADER.httpStatus());
            assertEquals(503, ErrorCode.SERVICE_UNAVAILABLE.httpStatus());
        }

        @Test
        @DisplayName("Error codes have unique string codes")
        void testErrorCodesAreUnique() {
            var codes = java.util.Arrays.stream(ErrorCode.values())
                .map(ErrorCode::code)
                .toList();
            
            var uniqueCodes = new java.util.HashSet<>(codes);
            assertEquals(codes.size(), uniqueCodes.size(), "All error codes should be unique");
        }

        @Test
        @DisplayName("Short codes are unique and follow Q-xxxx convention")
        void testShortCodesAreUniqueAndFormatted() {
            var shortCodes = java.util.Arrays.stream(ErrorCode.values())
                .map(ErrorCode::shortCode)
                .toList();

            var uniqueShortCodes = new java.util.HashSet<>(shortCodes);
            assertEquals(shortCodes.size(), uniqueShortCodes.size(), "All short codes should be unique");

            for (String sc : shortCodes) {
                assertTrue(sc.matches("Q-\\d{4}"), "Short code '" + sc + "' should match Q-xxxx pattern");
            }
        }

        @Test
        @DisplayName("Short codes use correct category ranges")
        void testShortCodeCategoryRanges() {
            assertEquals("Q-1001", ErrorCode.BAD_REQUEST.shortCode());
            assertEquals("Q-2001", ErrorCode.TRANSFER_NOT_FOUND.shortCode());
            assertEquals("Q-3001", ErrorCode.AGENT_NOT_FOUND.shortCode());
            assertEquals("Q-4001", ErrorCode.ASSIGNMENT_NOT_FOUND.shortCode());
            assertEquals("Q-5001", ErrorCode.NOT_LEADER.shortCode());
            assertEquals("Q-6001", ErrorCode.ROUTE_NOT_FOUND.shortCode());
            assertEquals("Q-7001", ErrorCode.WORKFLOW_NOT_FOUND.shortCode());
            assertEquals("Q-8001", ErrorCode.TENANT_NOT_FOUND.shortCode());
            assertEquals("Q-9001", ErrorCode.INTERNAL_ERROR.shortCode());
        }

        @Test
        @DisplayName("fromShortCode returns correct ErrorCode")
        void testFromShortCode() {
            var code = ErrorCode.fromShortCode("Q-2001");
            assertTrue(code.isPresent());
            assertEquals(ErrorCode.TRANSFER_NOT_FOUND, code.get());

            var unknown = ErrorCode.fromShortCode("Q-0000");
            assertTrue(unknown.isEmpty());
        }

        @Test
        @DisplayName("formatMessage substitutes placeholders correctly")
        void testFormatMessage() {
            String message = ErrorCode.TRANSFER_NOT_FOUND.formatMessage("job-123");
            assertEquals("Transfer job 'job-123' not found", message);

            String message2 = ErrorCode.TRANSFER_STATE_CONFLICT.formatMessage("job-456", "COMPLETED", "cancel");
            assertEquals("Transfer 'job-456' is in state 'COMPLETED', cannot cancel", message2);
        }

        @Test
        @DisplayName("fromCode returns correct ErrorCode")
        void testFromCode() {
            var code = ErrorCode.fromCode("TRANSFER_NOT_FOUND");
            assertTrue(code.isPresent());
            assertEquals(ErrorCode.TRANSFER_NOT_FOUND, code.get());

            var unknown = ErrorCode.fromCode("UNKNOWN_CODE");
            assertTrue(unknown.isEmpty());
        }
    }

    // ==================== ErrorResponse Tests ====================

    @Nested
    @DisplayName("ErrorResponse")
    class ErrorResponseTests {

        @Test
        @DisplayName("ErrorResponse.of creates correct response with message args")
        void testErrorResponseOf() {
            // Use varargs version: of(code, path, messageArgs...)
            ErrorResponse response = ErrorResponse.of(
                ErrorCode.TRANSFER_NOT_FOUND, 
                "/api/v1/transfers/job-123", 
                "job-123"
            );

            assertEquals("Q-2001", response.shortCode());
            assertEquals("TRANSFER_NOT_FOUND", response.code());
            assertEquals("Transfer job 'job-123' not found", response.message());
            assertEquals("/api/v1/transfers/job-123", response.path());
            assertNotNull(response.timestamp());
            assertNotNull(response.requestId());
            assertTrue(response.requestId().startsWith("req-"));
        }

        @Test
        @DisplayName("ErrorResponse.of with explicit message")
        void testErrorResponseOfWithMessage() {
            // Use explicit message version: withMessage(code, path, message)
            ErrorResponse response = ErrorResponse.withMessage(
                ErrorCode.BAD_REQUEST, 
                "/api/v1/test",
                "Custom error message"
            );

            assertEquals("Q-1001", response.shortCode());
            assertEquals("BAD_REQUEST", response.code());
            assertEquals("Custom error message", response.message());
            assertEquals("/api/v1/test", response.path());
        }

        @Test
        @DisplayName("ErrorResponse.toJson produces correct structure")
        void testToJson() {
            ErrorResponse response = ErrorResponse.of(
                ErrorCode.AGENT_NOT_FOUND, 
                "/api/v1/agents/agent-1", 
                "agent-1"
            );

            JsonObject json = response.toJson();
            
            assertTrue(json.containsKey("error"));
            JsonObject error = json.getJsonObject("error");
            
            assertEquals("Q-3001", error.getString("shortCode"));
            assertEquals("AGENT_NOT_FOUND", error.getString("code"));
            assertEquals("Agent 'agent-1' not found", error.getString("message"));
            assertEquals("/api/v1/agents/agent-1", error.getString("path"));
            assertNotNull(error.getString("timestamp"));
            assertNotNull(error.getString("requestId"));
        }

        @Test
        @DisplayName("ErrorResponse.httpStatus returns correct status from code")
        void testHttpStatus() {
            ErrorResponse response404 = ErrorResponse.of(ErrorCode.TRANSFER_NOT_FOUND, "/test", "x");
            assertEquals(404, response404.httpStatus());

            ErrorResponse response503 = ErrorResponse.of(ErrorCode.NOT_LEADER, "/test", "node2");
            assertEquals(503, response503.httpStatus());

            ErrorResponse response400 = ErrorResponse.withMessage(ErrorCode.BAD_REQUEST, "/test", "Invalid JSON");
            assertEquals(400, response400.httpStatus());
        }

        @Test
        @DisplayName("ErrorResponse.fromException uses exception message")
        void testFromException() {
            Exception ex = new IllegalArgumentException("Invalid transfer request");
            ErrorResponse response = ErrorResponse.fromException(
                ErrorCode.TRANSFER_INVALID, 
                ex, 
                "/api/v1/transfers"
            );

            assertEquals("Q-2002", response.shortCode());
            assertEquals("TRANSFER_INVALID", response.code());
            assertEquals("Invalid transfer request", response.message());
            assertEquals(400, response.httpStatus());
        }
    }

    // ==================== QuorusApiException Tests ====================

    @Nested
    @DisplayName("QuorusApiException")
    class QuorusApiExceptionTests {

        @Test
        @DisplayName("notFound factory creates 404 exception")
        void testNotFoundFactory() {
            QuorusApiException ex = QuorusApiException.notFound(
                ErrorCode.TRANSFER_NOT_FOUND, 
                "job-999"
            );

            assertEquals(ErrorCode.TRANSFER_NOT_FOUND, ex.getErrorCode());
            assertEquals(404, ex.getHttpStatus());
            assertEquals("Transfer job 'job-999' not found", ex.getMessage());
        }

        @Test
        @DisplayName("badRequest factory creates 400 exception")
        void testBadRequestFactory() {
            QuorusApiException ex = QuorusApiException.badRequest(
                ErrorCode.MISSING_REQUIRED_FIELD, 
                "sourceUri"
            );

            assertEquals(ErrorCode.MISSING_REQUIRED_FIELD, ex.getErrorCode());
            assertEquals(400, ex.getHttpStatus());
            assertEquals("Required field 'sourceUri' is missing", ex.getMessage());
        }

        @Test
        @DisplayName("notLeader factory creates 503 exception with leader info")
        void testNotLeaderFactory() {
            QuorusApiException ex = QuorusApiException.notLeader("node-2");

            assertEquals(ErrorCode.NOT_LEADER, ex.getErrorCode());
            assertEquals(503, ex.getHttpStatus());
            assertTrue(ex.getMessage().contains("node-2"));
        }

        @Test
        @DisplayName("noLeader factory creates 503 exception")
        void testNoLeaderFactory() {
            QuorusApiException ex = QuorusApiException.noLeader();

            assertEquals(ErrorCode.NO_LEADER, ex.getErrorCode());
            assertEquals(503, ex.getHttpStatus());
        }

        @Test
        @DisplayName("internal factory includes cause")
        void testInternalFactory() {
            RuntimeException cause = new RuntimeException("Database connection failed");
            QuorusApiException ex = QuorusApiException.internal(
                ErrorCode.INTERNAL_ERROR, 
                cause, 
                "Unexpected error"
            );

            assertEquals(ErrorCode.INTERNAL_ERROR, ex.getErrorCode());
            assertEquals(500, ex.getHttpStatus());
            assertSame(cause, ex.getCause());
        }
    }
}
