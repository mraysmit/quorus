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
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.TransferJobCommand;
import dev.mars.quorus.controller.state.TransferJobSnapshot;
import dev.mars.quorus.core.JobAssignment;
import dev.mars.quorus.core.TransferJob;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * HTTP handler for transfer job operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/transfers} — Create a new transfer job</li>
 *   <li>{@code GET /api/v1/transfers/:jobId} — Get transfer job status</li>
 *   <li>{@code DELETE /api/v1/transfers/:jobId} — Cancel a transfer job</li>
 * </ul>
 *
 * <p>All write operations are submitted to Raft for distributed consensus.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0 (Vert.x reactive)
 * @since 2025-12-11
 */
public class TransferHandler {

    private static final Logger logger = LoggerFactory.getLogger(TransferHandler.class);
    private final RaftNode raftNode;
    private final QuorusStateStore stateStore;

    public TransferHandler(RaftNode raftNode, QuorusStateStore stateStore) {
        this.raftNode = raftNode;
        this.stateStore = stateStore;
    }

    /**
     * Handles {@code POST /api/v1/transfers} — creates a new transfer job.
     */
    public Handler<RoutingContext> handleCreate() {
        return ctx -> {
            try {
                JsonObject body = ctx.body().asJsonObject();
                if (body == null) {
                    ctx.fail(400, new IllegalArgumentException("Request body is required"));
                    return;
                }

                // Extract tenantId BEFORE mapTo — TransferJob has no tenantId field and
                // Jackson would throw "Unrecognized field" if we leave it in the body.
                String tenantId = body.getString("tenantId");
                if (tenantId == null || tenantId.isBlank()) {
                    ctx.fail(400, new IllegalArgumentException("Missing required field: tenantId"));
                    return;
                }

                JsonObject jobBody = body.copy();
                jobBody.remove("tenantId");
                TransferJob job = jobBody.mapTo(TransferJob.class);

                logger.info("Creating transfer job: jobId={}, tenantId={}", job.getJobId(), tenantId);
                TransferJobCommand command = TransferJobCommand.create(job, tenantId);

                raftNode.submitCommand(command)
                        .onSuccess(result -> {
                            if (result instanceof CommandResult.NotFound<?> nf) {
                                logger.warn("Transfer job disappeared during creation (race condition): jobId={}", nf.id());
                                ctx.fail(QuorusApiException.notFound(ErrorCode.TRANSFER_NOT_FOUND, nf.id()));
                            } else {
                                logger.info("Transfer job created: jobId={}", job.getJobId());
                                ctx.response().setStatusCode(201);
                                ctx.json(new JsonObject()
                                        .put("success", true)
                                        .put("jobId", job.getJobId()));
                            }
                        })
                        .onFailure(ctx::fail);
            } catch (Exception e) {
                logger.error("Failed to create transfer job: {}", e.getMessage());
                logger.debug("Stack trace for transfer job creation failure", e);
                ctx.fail(e);
            }
        };
    }

    /**
     * Handles {@code GET /api/v1/transfers/:jobId} — gets transfer job status.
     */
    public Handler<RoutingContext> handleGet() {
        return ctx -> {
            String jobId = ctx.pathParam("jobId");
            logger.debug("Getting transfer job: jobId={}", jobId);

            TransferJobSnapshot job = stateStore.findTransferJob(jobId)
                    .orElseThrow(() -> QuorusApiException.notFound(ErrorCode.TRANSFER_NOT_FOUND, jobId));

                // Get the latest assignment status for this job
            JobAssignment latestAssignment = stateStore.getJobAssignments().values().stream()
                    .filter(a -> a.getJobId().equals(jobId))
                    .max((a, b) -> assignmentLastActivity(a).compareTo(assignmentLastActivity(b)))
                    .orElse(null);

            JsonObject response = new JsonObject()
                    .put("jobId", job.getJobId())
                    .put("sourceUri", job.getSourceUri())
                    .put("destinationPath", job.getDestinationPath())
                    .put("totalBytes", job.getTotalBytes())
                    .put("bytesTransferred", job.getBytesTransferred());

            if (latestAssignment != null) {
                response.put("status", latestAssignment.getStatus().name());
            } else {
                response.put("status", job.getStatus().name());
            }

            if (job.getStartTime() != null) {
                response.put("startTime", job.getStartTime().toString());
            }
            if (job.getLastUpdateTime() != null) {
                response.put("lastUpdateTime", job.getLastUpdateTime().toString());
            }
            if (job.getErrorMessage() != null) {
                response.put("errorMessage", job.getErrorMessage());
            }
            if (job.getDescription() != null) {
                response.put("description", job.getDescription());
            }
            if (job.getTotalBytes() > 0) {
                double progress = (double) job.getBytesTransferred() / job.getTotalBytes() * 100.0;
                response.put("progressPercentage", Math.round(progress * 100.0) / 100.0);
            }

            ctx.json(response);
        };
    }

    private static Instant assignmentLastActivity(JobAssignment assignment) {
        if (assignment.getCompletedAt() != null) {
            return assignment.getCompletedAt();
        }
        if (assignment.getStartedAt() != null) {
            return assignment.getStartedAt();
        }
        if (assignment.getAcceptedAt() != null) {
            return assignment.getAcceptedAt();
        }
        return assignment.getAssignedAt();
    }

    /**
     * Handles {@code DELETE /api/v1/transfers/:jobId} — cancels a transfer job.
     */
    public Handler<RoutingContext> handleDelete() {
        return ctx -> {
            try {
                String jobId = ctx.pathParam("jobId");
                logger.info("Deleting transfer job: jobId={}", jobId);
                QuorusStateStore stateMachine = this.stateStore;

                if (!stateMachine.hasTransferJob(jobId)) {
                    throw QuorusApiException.notFound(ErrorCode.TRANSFER_NOT_FOUND, jobId);
                }

                TransferJobCommand command = TransferJobCommand.delete(jobId);
                raftNode.submitCommand(command)
                        .onSuccess(result -> {
                            if (result instanceof CommandResult.NotFound<?> nf) {
                                logger.warn("Transfer job disappeared during deletion (race condition): jobId={}", nf.id());
                                ctx.fail(QuorusApiException.notFound(ErrorCode.TRANSFER_NOT_FOUND, nf.id()));
                            } else {
                                logger.info("Transfer job deleted: jobId={}", jobId);
                                ctx.json(new JsonObject()
                                        .put("jobId", jobId)
                                        .put("message", "Transfer job cancelled and deleted successfully"));
                            }
                        })
                        .onFailure(ctx::fail);
            } catch (Exception e) {
                logger.error("Failed to delete transfer job: {}", e.getMessage());
                logger.debug("Stack trace for transfer job deletion failure", e);
                ctx.fail(e);
            }
        };
    }
}

