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

package dev.mars.quorus.controller.http.handlers;

import dev.mars.quorus.controller.http.ErrorCode;
import dev.mars.quorus.controller.http.QuorusApiException;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.state.JobAssignmentCommand;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.core.JobAssignment;
import dev.mars.quorus.core.JobAssignmentStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * HTTP handler for job assignment operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/assignments} — Assign a job to an agent</li>
 *   <li>{@code GET /api/v1/assignments} — List all assignments</li>
 *   <li>{@code GET /api/v1/assignments/:assignmentId} — Get a specific assignment</li>
 *   <li>{@code PUT /api/v1/assignments/:assignmentId/accept} — Accept an assignment</li>
 *   <li>{@code PUT /api/v1/assignments/:assignmentId/reject} — Reject an assignment</li>
 *   <li>{@code PUT /api/v1/assignments/:assignmentId/status} — Update assignment status</li>
 *   <li>{@code PUT /api/v1/assignments/:assignmentId/cancel} — Cancel an assignment</li>
 *   <li>{@code DELETE /api/v1/assignments/:assignmentId} — Remove an assignment</li>
 * </ul>
 *
 * <p>All write operations are submitted to Raft for distributed consensus.
 * Read operations query the local state machine directly.</p>
 *
 * <p>Single Responsibility: This handler owns only job-assignment HTTP operations.
 * Raft consensus and state management are delegated to {@link RaftNode}.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-02-19
 */
public class JobAssignmentHandler {

    private static final Logger logger = LoggerFactory.getLogger(JobAssignmentHandler.class);

    private final RaftNode raftNode;

    public JobAssignmentHandler(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    /**
     * Handles {@code POST /api/v1/assignments} — assigns a job to an agent.
     */
    public Handler<RoutingContext> handleAssign() {
        return ctx -> {
            try {
                JsonObject body = ctx.body().asJsonObject();
                if (body == null) {
                    throw QuorusApiException.badRequest(ErrorCode.BAD_REQUEST, "Request body is required");
                }
                JobAssignment assignment = body.mapTo(JobAssignment.class);
                JobAssignmentCommand command = JobAssignmentCommand.assign(assignment);

                raftNode.submitCommand(command)
                        .onSuccess(result -> {
                            ctx.response().setStatusCode(201);
                            ctx.json(new JsonObject()
                                    .put("success", true)
                                    .put("assignmentId", command.assignmentId()));
                        })
                        .onFailure(ctx::fail);
            } catch (Exception e) {
                logger.error("Failed to assign job: {}", e.getMessage());
                logger.debug("Stack trace for job assignment failure", e);
                ctx.fail(e);
            }
        };
    }

    /**
     * Handles {@code GET /api/v1/assignments} — lists all assignments.
     */
    public Handler<RoutingContext> handleList() {
        return ctx -> {
            QuorusStateStore stateMachine = (QuorusStateStore) raftNode.getStateStore();
            Map<String, JobAssignment> assignments = stateMachine.getJobAssignments();

            JsonArray array = new JsonArray();
            for (Map.Entry<String, JobAssignment> entry : assignments.entrySet()) {
                array.add(toJson(entry.getKey(), entry.getValue()));
            }

            ctx.json(new JsonObject()
                    .put("assignments", array)
                    .put("total", assignments.size()));
        };
    }

    /**
     * Handles {@code GET /api/v1/assignments/:assignmentId} — gets a specific assignment.
     */
    public Handler<RoutingContext> handleGet() {
        return ctx -> {
            String assignmentId = ctx.pathParam("assignmentId");
            QuorusStateStore stateMachine = (QuorusStateStore) raftNode.getStateStore();

            JobAssignment assignment = stateMachine.getJobAssignment(assignmentId);
            if (assignment == null) {
                throw QuorusApiException.notFound(ErrorCode.ASSIGNMENT_NOT_FOUND, assignmentId);
            }

            ctx.json(toJson(assignmentId, assignment));
        };
    }

    /**
     * Handles {@code PUT /api/v1/assignments/:assignmentId/accept} — accepts an assignment.
     */
    public Handler<RoutingContext> handleAccept() {
        return ctx -> {
            String assignmentId = ctx.pathParam("assignmentId");
            verifyAssignmentExists(assignmentId);

            JobAssignmentCommand command = JobAssignmentCommand.accept(assignmentId);
            raftNode.submitCommand(command)
                    .onSuccess(result -> ctx.json(new JsonObject()
                            .put("assignmentId", assignmentId)
                            .put("status", "ACCEPTED")
                            .put("message", "Assignment accepted")))
                    .onFailure(ctx::fail);
        };
    }

    /**
     * Handles {@code PUT /api/v1/assignments/:assignmentId/reject} — rejects an assignment.
     */
    public Handler<RoutingContext> handleReject() {
        return ctx -> {
            String assignmentId = ctx.pathParam("assignmentId");
            verifyAssignmentExists(assignmentId);

            JsonObject body = ctx.body().asJsonObject();
            String reason = body != null ? body.getString("reason") : null;

            JobAssignmentCommand command = JobAssignmentCommand.reject(assignmentId, reason);
            raftNode.submitCommand(command)
                    .onSuccess(result -> ctx.json(new JsonObject()
                            .put("assignmentId", assignmentId)
                            .put("status", "REJECTED")
                            .put("message", "Assignment rejected")))
                    .onFailure(ctx::fail);
        };
    }

    /**
     * Handles {@code PUT /api/v1/assignments/:assignmentId/status} — updates assignment status.
     */
    public Handler<RoutingContext> handleUpdateStatus() {
        return ctx -> {
            try {
                String assignmentId = ctx.pathParam("assignmentId");
                verifyAssignmentExists(assignmentId);

                JsonObject body = ctx.body().asJsonObject();
                if (body == null || !body.containsKey("status")) {
                    throw QuorusApiException.badRequest(ErrorCode.MISSING_REQUIRED_FIELD, "status");
                }

                JobAssignmentStatus newStatus;
                try {
                    newStatus = JobAssignmentStatus.valueOf(body.getString("status"));
                } catch (IllegalArgumentException e) {
                    throw QuorusApiException.badRequest(ErrorCode.VALIDATION_ERROR,
                            "Invalid status: " + body.getString("status"));
                }

                JobAssignmentCommand command = JobAssignmentCommand.updateStatus(assignmentId, newStatus);
                raftNode.submitCommand(command)
                        .onSuccess(result -> ctx.json(new JsonObject()
                                .put("assignmentId", assignmentId)
                                .put("status", newStatus.name())
                                .put("message", "Assignment status updated")))
                        .onFailure(ctx::fail);
            } catch (Exception e) {
                ctx.fail(e);
            }
        };
    }

    /**
     * Handles {@code PUT /api/v1/assignments/:assignmentId/cancel} — cancels an assignment.
     */
    public Handler<RoutingContext> handleCancel() {
        return ctx -> {
            String assignmentId = ctx.pathParam("assignmentId");
            verifyAssignmentExists(assignmentId);

            JsonObject body = ctx.body().asJsonObject();
            String reason = body != null ? body.getString("reason") : null;

            JobAssignmentCommand command = JobAssignmentCommand.cancel(assignmentId, reason);
            raftNode.submitCommand(command)
                    .onSuccess(result -> ctx.json(new JsonObject()
                            .put("assignmentId", assignmentId)
                            .put("status", "CANCELLED")
                            .put("message", "Assignment cancelled")))
                    .onFailure(ctx::fail);
        };
    }

    /**
     * Handles {@code DELETE /api/v1/assignments/:assignmentId} — removes a completed/failed assignment.
     */
    public Handler<RoutingContext> handleRemove() {
        return ctx -> {
            String assignmentId = ctx.pathParam("assignmentId");
            verifyAssignmentExists(assignmentId);

            JobAssignmentCommand command = JobAssignmentCommand.remove(assignmentId);
            raftNode.submitCommand(command)
                    .onSuccess(result -> ctx.json(new JsonObject()
                            .put("assignmentId", assignmentId)
                            .put("message", "Assignment removed")))
                    .onFailure(ctx::fail);
        };
    }

    // ==================== Internal helpers ====================

    /**
     * Verifies that the assignment exists in the state machine.
     *
     * @throws QuorusApiException with ASSIGNMENT_NOT_FOUND if it doesn't exist
     */
    private void verifyAssignmentExists(String assignmentId) {
        QuorusStateStore stateMachine = (QuorusStateStore) raftNode.getStateStore();
        if (stateMachine.getJobAssignment(assignmentId) == null) {
            throw QuorusApiException.notFound(ErrorCode.ASSIGNMENT_NOT_FOUND, assignmentId);
        }
    }

    /**
     * Converts a JobAssignment to a JSON object for API responses.
     */
    private static JsonObject toJson(String assignmentId, JobAssignment assignment) {
        JsonObject json = new JsonObject()
                .put("assignmentId", assignmentId)
                .put("jobId", assignment.getJobId())
                .put("agentId", assignment.getAgentId())
                .put("status", assignment.getStatus().name())
                .put("retryCount", assignment.getRetryCount())
                .put("estimatedDurationMs", assignment.getEstimatedDurationMs());

        if (assignment.getAssignedAt() != null) {
            json.put("assignedAt", assignment.getAssignedAt().toString());
        }
        if (assignment.getAcceptedAt() != null) {
            json.put("acceptedAt", assignment.getAcceptedAt().toString());
        }
        if (assignment.getStartedAt() != null) {
            json.put("startedAt", assignment.getStartedAt().toString());
        }
        if (assignment.getCompletedAt() != null) {
            json.put("completedAt", assignment.getCompletedAt().toString());
        }
        if (assignment.getFailureReason() != null) {
            json.put("failureReason", assignment.getFailureReason());
        }
        if (assignment.getAssignmentStrategy() != null) {
            json.put("assignmentStrategy", assignment.getAssignmentStrategy());
        }
        if (assignment.isActive()) {
            json.put("active", true);
        }
        if (assignment.isTerminal()) {
            json.put("terminal", true);
        }
        return json;
    }
}
