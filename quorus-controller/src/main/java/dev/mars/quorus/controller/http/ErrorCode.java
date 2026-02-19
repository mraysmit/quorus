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

import java.util.Arrays;
import java.util.Optional;

/**
 * Standardized error codes for the Quorus HTTP API.
 * 
 * <p>Each error code includes:</p>
 * <ul>
 *   <li>A unique string code (e.g., "TRANSFER_NOT_FOUND")</li>
 *   <li>An HTTP status code</li>
 *   <li>A message template for consistent error messages</li>
 * </ul>
 * 
 * <p>Error code naming conventions:</p>
 * <ul>
 *   <li>{@code *_NOT_FOUND} - Resource does not exist (404)</li>
 *   <li>{@code *_INVALID} - Invalid input/request (400)</li>
 *   <li>{@code *_CONFLICT} - Resource state conflict (409)</li>
 *   <li>{@code *_FORBIDDEN} - Permission denied (403)</li>
 *   <li>{@code *_UNAVAILABLE} - Service temporarily unavailable (503)</li>
 *   <li>{@code INTERNAL_*} - Server-side errors (500)</li>
 * </ul>
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-04
 */
public enum ErrorCode {

    // ==================== General Errors (400-499) ====================
    
    /** Request body is missing or malformed */
    BAD_REQUEST("BAD_REQUEST", 400, "Invalid request: %s"),
    
    /** Request validation failed */
    VALIDATION_ERROR("VALIDATION_ERROR", 400, "Validation failed: %s"),
    
    /** Required field is missing */
    MISSING_REQUIRED_FIELD("MISSING_REQUIRED_FIELD", 400, "Required field '%s' is missing"),
    
    /** Authentication required but not provided */
    UNAUTHORIZED("UNAUTHORIZED", 401, "Authentication required"),
    
    /** Authenticated but not authorized for this action */
    FORBIDDEN("FORBIDDEN", 403, "Access denied: %s"),
    
    /** Generic resource not found */
    NOT_FOUND("NOT_FOUND", 404, "Resource not found: %s"),
    
    /** HTTP method not supported for this endpoint */
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", 405, "Method %s not allowed"),
    
    /** Resource state conflict */
    CONFLICT("CONFLICT", 409, "Conflict: %s"),
    
    // ==================== Transfer Errors ====================
    
    /** Transfer job not found */
    TRANSFER_NOT_FOUND("TRANSFER_NOT_FOUND", 404, "Transfer job '%s' not found"),
    
    /** Transfer request is invalid */
    TRANSFER_INVALID("TRANSFER_INVALID", 400, "Invalid transfer request: %s"),
    
    /** Transfer already exists */
    TRANSFER_DUPLICATE("TRANSFER_DUPLICATE", 409, "Transfer job '%s' already exists"),
    
    /** Transfer is in a state that doesn't allow this operation */
    TRANSFER_STATE_CONFLICT("TRANSFER_STATE_CONFLICT", 409, "Transfer '%s' is in state '%s', cannot %s"),
    
    // ==================== Agent Errors ====================
    
    /** Agent not found */
    AGENT_NOT_FOUND("AGENT_NOT_FOUND", 404, "Agent '%s' not found"),
    
    /** Agent registration is invalid */
    AGENT_INVALID("AGENT_INVALID", 400, "Invalid agent registration: %s"),
    
    /** Agent already registered */
    AGENT_DUPLICATE("AGENT_DUPLICATE", 409, "Agent '%s' is already registered"),
    
    /** Agent is offline or unresponsive */
    AGENT_UNAVAILABLE("AGENT_UNAVAILABLE", 503, "Agent '%s' is unavailable"),
    
    // ==================== Job Assignment Errors ====================
    
    /** Job assignment not found */
    ASSIGNMENT_NOT_FOUND("ASSIGNMENT_NOT_FOUND", 404, "Job assignment '%s' not found"),
    
    /** Job assignment is invalid */
    ASSIGNMENT_INVALID("ASSIGNMENT_INVALID", 400, "Invalid job assignment: %s"),
    
    /** No agents available to accept the job */
    NO_AVAILABLE_AGENTS("NO_AVAILABLE_AGENTS", 503, "No agents available to process job '%s'"),
    
    // ==================== Cluster/Raft Errors ====================
    
    /** Not the cluster leader, cannot process write request */
    NOT_LEADER("NOT_LEADER", 503, "Not the cluster leader. Current leader: %s"),
    
    /** Cluster has no elected leader */
    NO_LEADER("NO_LEADER", 503, "Cluster has no elected leader"),
    
    /** Cluster is not ready to accept requests */
    CLUSTER_NOT_READY("CLUSTER_NOT_READY", 503, "Cluster is not ready: %s"),
    
    /** Raft command failed to commit */
    RAFT_COMMIT_FAILED("RAFT_COMMIT_FAILED", 500, "Failed to commit operation: %s"),
    
    // ==================== Route Errors ====================
    
    /** Route not found */
    ROUTE_NOT_FOUND("ROUTE_NOT_FOUND", 404, "Route '%s' not found"),
    
    /** Route configuration is invalid */
    ROUTE_INVALID("ROUTE_INVALID", 400, "Invalid route configuration: %s"),
    
    /** Route already exists */
    ROUTE_DUPLICATE("ROUTE_DUPLICATE", 409, "Route '%s' already exists"),
    
    /** Route is in a state that doesn't allow this operation */
    ROUTE_STATE_CONFLICT("ROUTE_STATE_CONFLICT", 409, "Route '%s' is in state '%s', cannot %s"),
    
    // ==================== Workflow Errors ====================
    
    /** Workflow not found */
    WORKFLOW_NOT_FOUND("WORKFLOW_NOT_FOUND", 404, "Workflow '%s' not found"),
    
    /** Workflow definition is invalid */
    WORKFLOW_INVALID("WORKFLOW_INVALID", 400, "Invalid workflow definition: %s"),
    
    /** Workflow execution not found */
    WORKFLOW_EXECUTION_NOT_FOUND("WORKFLOW_EXECUTION_NOT_FOUND", 404, "Workflow execution '%s' not found"),
    
    // ==================== Tenant Errors ====================
    
    /** Tenant not found */
    TENANT_NOT_FOUND("TENANT_NOT_FOUND", 404, "Tenant '%s' not found"),
    
    /** Tenant quota exceeded */
    TENANT_QUOTA_EXCEEDED("TENANT_QUOTA_EXCEEDED", 429, "Tenant '%s' quota exceeded: %s"),
    
    // ==================== Server Errors (500+) ====================
    
    /** Unexpected internal error */
    INTERNAL_ERROR("INTERNAL_ERROR", 500, "Internal server error: %s"),
    
    /** Service temporarily unavailable */
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", 503, "Service temporarily unavailable: %s"),
    
    /** Request timeout */
    TIMEOUT("TIMEOUT", 504, "Request timed out: %s");

    private final String code;
    private final int httpStatus;
    private final String messageTemplate;

    ErrorCode(String code, int httpStatus, String messageTemplate) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.messageTemplate = messageTemplate;
    }

    /**
     * Returns the string error code.
     */
    public String code() {
        return code;
    }

    /**
     * Returns the HTTP status code for this error.
     */
    public int httpStatus() {
        return httpStatus;
    }

    /**
     * Returns the message template (may contain %s placeholders).
     */
    public String messageTemplate() {
        return messageTemplate;
    }

    /**
     * Formats the message template with the provided arguments.
     */
    public String formatMessage(Object... args) {
        return String.format(messageTemplate, args);
    }

    /**
     * Looks up an ErrorCode by its string code.
     */
    public static Optional<ErrorCode> fromCode(String code) {
        return Arrays.stream(values())
            .filter(e -> e.code.equals(code))
            .findFirst();
    }
}
