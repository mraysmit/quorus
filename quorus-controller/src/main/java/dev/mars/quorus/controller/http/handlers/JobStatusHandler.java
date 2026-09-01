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

import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.controller.http.ErrorCode;
import dev.mars.quorus.controller.http.QuorusApiException;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.state.CommandResult;
import dev.mars.quorus.controller.state.JobAssignmentCommand;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.TransferJobCommand;
import dev.mars.quorus.controller.state.TransferJobSnapshot;
import dev.mars.quorus.core.JobAssignment;
import dev.mars.quorus.core.JobAssignmentStatus;
import dev.mars.quorus.core.TransferStatus;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP handler for job status updates from agents.
 *
 * <p>Endpoint: {@code POST /api/v1/jobs/:jobId/status}
 *
 * <p>Allows agents to update the status of their assigned jobs.
 * Updates both the job assignment status and optionally the transfer progress.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0 (Vert.x reactive)
 * @since 2025-12-11
 */
public class JobStatusHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(JobStatusHandler.class);
    private final RaftNode raftNode;
    private final QuorusStateStore stateStore;

    public JobStatusHandler(RaftNode raftNode, QuorusStateStore stateStore) {
        this.raftNode = raftNode;
        this.stateStore = stateStore;
    }

    @Override
    public void handle(RoutingContext ctx) {
        try {
            String jobId = ctx.pathParam("jobId");
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) {
                ctx.fail(400, new IllegalArgumentException("Request body is required"));
                return;
            }

            String agentId = body.getString("agentId");
            String statusStr = body.getString("status");
            Long bytesTransferred = body.getLong("bytesTransferred", 0L);

            if (agentId == null || statusStr == null) {
                ctx.fail(400, new IllegalArgumentException("Missing required fields: agentId, status"));
                return;
            }

            logger.info("Updating job status: jobId={}, agentId={}, status={}, bytesTransferred={}",
                    jobId, agentId, statusStr, bytesTransferred);

            // Tenant isolation: verify the agent belongs to the same tenant as the job
            AgentInfo agent = stateStore.getAgent(agentId);
            if (agent == null) {
                ctx.fail(QuorusApiException.notFound(ErrorCode.AGENT_NOT_FOUND, agentId));
                return;
            }
            TransferJobSnapshot transferJobSnapshot = stateStore.findTransferJob(jobId)
                    .orElseThrow(() -> QuorusApiException.notFound(ErrorCode.TRANSFER_NOT_FOUND, jobId));
            if (agent.getTenantId() != null
                    && transferJobSnapshot.getTenantId() != null
                    && !agent.getTenantId().equals(transferJobSnapshot.getTenantId())) {
                logger.warn("Cross-tenant status update blocked: agentId={}, agentTenant={}, jobId={}, jobTenant={}",
                        agentId, agent.getTenantId(), jobId, transferJobSnapshot.getTenantId());
                ctx.fail(403, new IllegalArgumentException(
                        "Agent does not belong to the same tenant as the job"));
                return;
            }

            JobAssignmentStatus status = JobAssignmentStatus.valueOf(statusStr);

            // Reconstruct assignment ID based on convention: jobId:agentId
            String assignmentId = jobId + ":" + agentId;

            // Look up current assignment status for pre-commit validation and CAS protection
            JobAssignment existing = stateStore.findJobAssignment(assignmentId)
                    .orElseThrow(() -> QuorusApiException.notFound(ErrorCode.ASSIGNMENT_NOT_FOUND, assignmentId));

            // Pre-commit transition validation
            if (!existing.getStatus().canTransitionTo(status)) {
                ctx.fail(QuorusApiException.conflict(ErrorCode.ASSIGNMENT_STATE_CONFLICT,
                        assignmentId, existing.getStatus().name(), "transition to " + status));
                return;
            }

            // Update job assignment status with CAS
            JobAssignmentCommand assignmentCommand = JobAssignmentCommand.updateStatus(
                    assignmentId, existing.getStatus(), status);
            Future<CommandResult<?>> assignmentFuture = raftNode.submitCommand(assignmentCommand)
                    .compose(assignmentResult -> {
                        if (assignmentResult instanceof CommandResult.Rejected<?> rejected) {
                            return Future.failedFuture(CommandResultHandler.rejectionException(rejected));
                        }
                        if (assignmentResult instanceof CommandResult.CasMismatch<?>) {
                            logger.warn("Assignment state conflict during job status update: assignmentId={}, expected={}",
                                    assignmentId, existing.getStatus());
                            return Future.failedFuture(QuorusApiException.conflict(
                                    ErrorCode.ASSIGNMENT_STATE_CONFLICT,
                                    assignmentId, existing.getStatus().name(), "update (concurrent modification)"));
                        }
                        if (assignmentResult instanceof CommandResult.NotFound<?> nf) {
                            logger.warn("Assignment disappeared during job status update (race condition): assignmentId={}", nf.id());
                            return Future.failedFuture(QuorusApiException.notFound(
                                    ErrorCode.ASSIGNMENT_NOT_FOUND, nf.id()));
                        }
                        return Future.succeededFuture(assignmentResult);
                    });

            TransferStatus targetTransferStatus = transferStatus(status);
            Future<CommandResult<?>> lifecycle = assignmentFuture;

            // Enter IN_PROGRESS before accepting progress. For terminal reports,
            // persist the final checkpoint before closing the transfer lifecycle.
            if (targetTransferStatus == TransferStatus.IN_PROGRESS) {
                lifecycle = lifecycle.compose(ignored -> submitTransferStatus(
                        jobId, transferJobSnapshot.getStatus(), targetTransferStatus));
            }
            if (bytesTransferred > 0) {
                lifecycle = lifecycle.compose(ignored -> acceptedTransferResult(
                        raftNode.submitCommand(TransferJobCommand.updateProgress(jobId, bytesTransferred)), jobId));
            }
            if (targetTransferStatus != null && targetTransferStatus != TransferStatus.IN_PROGRESS) {
                lifecycle = lifecycle.compose(ignored -> submitTransferStatus(
                        jobId, transferJobSnapshot.getStatus(), targetTransferStatus));
            }

            lifecycle
                    .onSuccess(ignored -> {
                        logger.info("Job status updated: jobId={}, agentId={}, status={}, bytesTransferred={}",
                                jobId, agentId, status, bytesTransferred);
                        ctx.json(new JsonObject().put("success", true));
                    })
                    .onFailure(ctx::fail);
        } catch (Exception e) {
            logger.warn("Failed to update status: {}", e.getMessage());
            logger.debug("Stack trace for status update failure", e);
            ctx.fail(e);
        }
    }

    private Future<CommandResult<?>> submitTransferStatus(
            String jobId, TransferStatus expected, TransferStatus target) {
        if (expected == target) {
            return Future.succeededFuture(new CommandResult.Success<>(stateStore.getTransferJob(jobId)));
        }
        if (!expected.canTransitionTo(target)) {
            return Future.failedFuture(QuorusApiException.conflict(
                    ErrorCode.TRANSFER_STATE_CONFLICT, jobId, expected.name(), "transition to " + target));
        }
        return acceptedTransferResult(
                raftNode.submitCommand(TransferJobCommand.updateStatus(jobId, expected, target)), jobId);
    }

    private static Future<CommandResult<?>> acceptedTransferResult(
            Future<CommandResult<?>> command, String jobId) {
        return command.compose(result -> {
            if (result instanceof CommandResult.Rejected<?> rejected) {
                return Future.failedFuture(CommandResultHandler.rejectionException(rejected));
            }
            if (result instanceof CommandResult.CasMismatch<?>) {
                return Future.failedFuture(QuorusApiException.conflict(
                        ErrorCode.TRANSFER_STATE_CONFLICT, jobId, "changed", "update"));
            }
            if (result instanceof CommandResult.NotFound<?> notFound) {
                return Future.failedFuture(QuorusApiException.notFound(
                        ErrorCode.TRANSFER_NOT_FOUND, notFound.id()));
            }
            return Future.succeededFuture(result);
        });
    }

    private static TransferStatus transferStatus(JobAssignmentStatus status) {
        return switch (status) {
            case IN_PROGRESS -> TransferStatus.IN_PROGRESS;
            case COMPLETED -> TransferStatus.COMPLETED;
            case FAILED -> TransferStatus.FAILED;
            case CANCELLED -> TransferStatus.CANCELLED;
            case ASSIGNED, ACCEPTED, REJECTED, TIMEOUT -> null;
        };
    }
}

