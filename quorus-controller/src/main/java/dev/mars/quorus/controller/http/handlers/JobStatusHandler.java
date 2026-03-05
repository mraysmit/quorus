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
import dev.mars.quorus.controller.state.CommandResult;
import dev.mars.quorus.controller.state.JobAssignmentCommand;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.TransferJobCommand;
import dev.mars.quorus.core.JobAssignment;
import dev.mars.quorus.core.JobAssignmentStatus;
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

            String agentId = body.getString("agentId");
            String statusStr = body.getString("status");
            Long bytesTransferred = body.getLong("bytesTransferred", 0L);

            if (agentId == null || statusStr == null) {
                ctx.fail(400, new IllegalArgumentException("Missing required fields: agentId, status"));
                return;
            }

            logger.info("Updating job status: jobId={}, agentId={}, status={}, bytesTransferred={}",
                    jobId, agentId, statusStr, bytesTransferred);

            JobAssignmentStatus status = JobAssignmentStatus.valueOf(statusStr);

            // Reconstruct assignment ID based on convention: jobId:agentId
            String assignmentId = jobId + ":" + agentId;

            // Look up current assignment status for pre-commit validation and CAS protection
            JobAssignment existing = stateStore.findJobAssignment(assignmentId)
                    .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));

            // Pre-commit transition validation
            if (!existing.getStatus().canTransitionTo(status)) {
                ctx.fail(409, new IllegalArgumentException(String.format(
                        "Invalid transition from %s to %s for assignment %s",
                        existing.getStatus(), status, assignmentId)));
                return;
            }

            // Update job assignment status with CAS
            JobAssignmentCommand assignmentCommand = JobAssignmentCommand.updateStatus(
                    assignmentId, existing.getStatus(), status);
            Future<CommandResult<?>> assignmentFuture = raftNode.submitCommand(assignmentCommand)
                    .compose(assignmentResult -> {
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

            // Also update transfer job progress if bytes were reported
            if (bytesTransferred > 0) {
                TransferJobCommand jobCommand = TransferJobCommand.updateProgress(jobId, bytesTransferred);
                assignmentFuture
                        .compose(res -> raftNode.submitCommand(jobCommand))
                        .onSuccess(res -> {
                            if (res instanceof CommandResult.NotFound<?> nf) {
                                logger.warn("Transfer job not found during progress update: jobId={}", nf.id());
                                ctx.fail(QuorusApiException.notFound(ErrorCode.TRANSFER_NOT_FOUND, nf.id()));
                            } else {
                                logger.info("Job status updated: jobId={}, agentId={}, status={}, bytesTransferred={}",
                                        jobId, agentId, status, bytesTransferred);
                                ctx.json(new JsonObject().put("success", true));
                            }
                        })
                        .onFailure(ctx::fail);
            } else {
                assignmentFuture
                        .onSuccess(res -> {
                            logger.info("Job status updated: jobId={}, agentId={}, status={}",
                                    jobId, agentId, status);
                            ctx.json(new JsonObject().put("success", true));
                        })
                        .onFailure(ctx::fail);
            }
        } catch (Exception e) {
            logger.warn("Failed to update status: {}", e.getMessage());
            logger.debug("Stack trace for status update failure", e);
            ctx.fail(e);
        }
    }
}

