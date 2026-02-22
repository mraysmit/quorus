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
 *   <li>A short code for quick reference (e.g., "Q-4001")</li>
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
    BAD_REQUEST("Q-1001", "BAD_REQUEST", 400, "Invalid request: %s"),
    
    /** Request validation failed */
    VALIDATION_ERROR("Q-1002", "VALIDATION_ERROR", 400, "Validation failed: %s"),
    
    /** Required field is missing */
    MISSING_REQUIRED_FIELD("Q-1003", "MISSING_REQUIRED_FIELD", 400, "Required field '%s' is missing"),
    
    /** Authentication required but not provided */
    UNAUTHORIZED("Q-1004", "UNAUTHORIZED", 401, "Authentication required"),
    
    /** Authenticated but not authorized for this action */
    FORBIDDEN("Q-1005", "FORBIDDEN", 403, "Access denied: %s"),
    
    /** Generic resource not found */
    NOT_FOUND("Q-1006", "NOT_FOUND", 404, "Resource not found: %s"),
    
    /** HTTP method not supported for this endpoint */
    METHOD_NOT_ALLOWED("Q-1007", "METHOD_NOT_ALLOWED", 405, "Method %s not allowed"),
    
    /** Resource state conflict */
    CONFLICT("Q-1008", "CONFLICT", 409, "Conflict: %s"),
    
    // ==================== Transfer Errors ====================
    
    /** Transfer job not found */
    TRANSFER_NOT_FOUND("Q-2001", "TRANSFER_NOT_FOUND", 404, "Transfer job '%s' not found"),
    
    /** Transfer request is invalid */
    TRANSFER_INVALID("Q-2002", "TRANSFER_INVALID", 400, "Invalid transfer request: %s"),
    
    /** Transfer already exists */
    TRANSFER_DUPLICATE("Q-2003", "TRANSFER_DUPLICATE", 409, "Transfer job '%s' already exists"),
    
    /** Transfer is in a state that doesn't allow this operation */
    TRANSFER_STATE_CONFLICT("Q-2004", "TRANSFER_STATE_CONFLICT", 409, "Transfer '%s' is in state '%s', cannot %s"),
    
    // ==================== Agent Errors ====================
    
    /** Agent not found */
    AGENT_NOT_FOUND("Q-3001", "AGENT_NOT_FOUND", 404, "Agent '%s' not found"),
    
    /** Agent registration is invalid */
    AGENT_INVALID("Q-3002", "AGENT_INVALID", 400, "Invalid agent registration: %s"),
    
    /** Agent already registered */
    AGENT_DUPLICATE("Q-3003", "AGENT_DUPLICATE", 409, "Agent '%s' is already registered"),
    
    /** Agent is in a state that doesn't allow this operation */
    AGENT_STATE_CONFLICT("Q-3004", "AGENT_STATE_CONFLICT", 409, "Agent '%s' is in state '%s', cannot %s"),
    
    /** Agent is offline or unresponsive */
    AGENT_UNAVAILABLE("Q-3005", "AGENT_UNAVAILABLE", 503, "Agent '%s' is unavailable"),
    
    // ==================== Job Assignment Errors ====================
    
    /** Job assignment not found */
    ASSIGNMENT_NOT_FOUND("Q-4001", "ASSIGNMENT_NOT_FOUND", 404, "Job assignment '%s' not found"),
    
    /** Job assignment is invalid */
    ASSIGNMENT_INVALID("Q-4002", "ASSIGNMENT_INVALID", 400, "Invalid job assignment: %s"),
    
    /** No agents available to accept the job */
    NO_AVAILABLE_AGENTS("Q-4003", "NO_AVAILABLE_AGENTS", 503, "No agents available to process job '%s'"),
    
    /** Job assignment is in a state that doesn't allow this operation */
    ASSIGNMENT_STATE_CONFLICT("Q-4004", "ASSIGNMENT_STATE_CONFLICT", 409, "Assignment '%s' is in state '%s', cannot %s"),
    
    // ==================== Cluster/Raft Errors ====================
    
    /** Not the cluster leader, cannot process write request */
    NOT_LEADER("Q-5001", "NOT_LEADER", 503, "Not the cluster leader. Current leader: %s"),
    
    /** Cluster has no elected leader */
    NO_LEADER("Q-5002", "NO_LEADER", 503, "Cluster has no elected leader"),
    
    /** Cluster is not ready to accept requests */
    CLUSTER_NOT_READY("Q-5003", "CLUSTER_NOT_READY", 503, "Cluster is not ready: %s"),
    
    /** Raft command failed to commit */
    RAFT_COMMIT_FAILED("Q-5004", "RAFT_COMMIT_FAILED", 500, "Failed to commit operation: %s"),
    
    // ==================== Route Errors ====================
    
    /** Route not found */
    ROUTE_NOT_FOUND("Q-6001", "ROUTE_NOT_FOUND", 404, "Route '%s' not found"),
    
    /** Route configuration is invalid */
    ROUTE_INVALID("Q-6002", "ROUTE_INVALID", 400, "Invalid route configuration: %s"),
    
    /** Route already exists */
    ROUTE_DUPLICATE("Q-6003", "ROUTE_DUPLICATE", 409, "Route '%s' already exists"),
    
    /** Route is in a state that doesn't allow this operation */
    ROUTE_STATE_CONFLICT("Q-6004", "ROUTE_STATE_CONFLICT", 409, "Route '%s' is in state '%s', cannot %s"),
    
    // ==================== Workflow Errors ====================
    
    /** Workflow not found */
    WORKFLOW_NOT_FOUND("Q-7001", "WORKFLOW_NOT_FOUND", 404, "Workflow '%s' not found"),
    
    /** Workflow definition is invalid */
    WORKFLOW_INVALID("Q-7002", "WORKFLOW_INVALID", 400, "Invalid workflow definition: %s"),
    
    /** Workflow execution not found */
    WORKFLOW_EXECUTION_NOT_FOUND("Q-7003", "WORKFLOW_EXECUTION_NOT_FOUND", 404, "Workflow execution '%s' not found"),
    
    // ==================== Tenant Errors ====================
    
    /** Tenant not found */
    TENANT_NOT_FOUND("Q-8001", "TENANT_NOT_FOUND", 404, "Tenant '%s' not found"),
    
    /** Tenant quota exceeded */
    TENANT_QUOTA_EXCEEDED("Q-8002", "TENANT_QUOTA_EXCEEDED", 429, "Tenant '%s' quota exceeded: %s"),
    
    // ==================== Server Errors (500+) ====================
    
    /** Unexpected internal error */
    INTERNAL_ERROR("Q-9001", "INTERNAL_ERROR", 500, "Internal server error: %s"),
    
    /** Service temporarily unavailable */
    SERVICE_UNAVAILABLE("Q-9002", "SERVICE_UNAVAILABLE", 503, "Service temporarily unavailable: %s"),
    
    /** Server is shutting down (drain mode) */
    SERVICE_SHUTTING_DOWN("Q-9003", "SERVICE_SHUTTING_DOWN", 503, "Server is shutting down"),
    
    /** Request timeout */
    TIMEOUT("Q-9004", "TIMEOUT", 504, "Request timed out: %s");

    private final String shortCode;
    private final String code;
    private final int httpStatus;
    private final String messageTemplate;

    ErrorCode(String shortCode, String code, int httpStatus, String messageTemplate) {
        this.shortCode = shortCode;
        this.code = code;
        this.httpStatus = httpStatus;
        this.messageTemplate = messageTemplate;
    }

    /**
     * Returns the short error code for quick reference (e.g., "Q-4001").
     *
     * <p>Short code ranges:</p>
     * <ul>
     *   <li>Q-1xxx — General HTTP errors</li>
     *   <li>Q-2xxx — Transfer errors</li>
     *   <li>Q-3xxx — Agent errors</li>
     *   <li>Q-4xxx — Job assignment errors</li>
     *   <li>Q-5xxx — Cluster/Raft errors</li>
     *   <li>Q-6xxx — Route errors</li>
     *   <li>Q-7xxx — Workflow errors</li>
     *   <li>Q-8xxx — Tenant errors</li>
     *   <li>Q-9xxx — Server errors</li>
     * </ul>
     */
    public String shortCode() {
        return shortCode;
    }

    /**
     * Returns the string error code (e.g., "TRANSFER_NOT_FOUND").
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

    /**
     * Looks up an ErrorCode by its short code (e.g., "Q-4001").
     */
    public static Optional<ErrorCode> fromShortCode(String shortCode) {
        return Arrays.stream(values())
            .filter(e -> e.shortCode.equals(shortCode))
            .findFirst();
    }
}
