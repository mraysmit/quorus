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

import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.state.*;
import dev.mars.quorus.controller.http.handlers.MetricsHandler;
import dev.mars.quorus.core.JobAssignment;
import dev.mars.quorus.core.JobAssignmentStatus;
import dev.mars.quorus.core.TransferJob;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Reactive HTTP API Server using Vert.x Web.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.1
 * @since 2025-08-26
 */
public class HttpApiServer {

    private static final Logger logger = LoggerFactory.getLogger(HttpApiServer.class);

    private final Vertx vertx;
    private final int port;
    private final RaftNode raftNode;
    private final int prometheusPort;
    private HttpServer httpServer;

    /**
     * Creates an HttpApiServer with default Prometheus port from configuration.
     */
    public HttpApiServer(Vertx vertx, int port, RaftNode raftNode) {
        this(vertx, port, raftNode, -1); // -1 means use default from config
    }

    /**
     * Creates an HttpApiServer with a specific Prometheus port.
     * Useful for testing with non-default ports.
     * 
     * @param prometheusPort the port where Prometheus metrics are exposed, or -1 to use config default
     */
    public HttpApiServer(Vertx vertx, int port, RaftNode raftNode, int prometheusPort) {
        this.vertx = vertx;
        this.port = port;
        this.raftNode = raftNode;
        this.prometheusPort = prometheusPort;
    }

    public Future<Void> start() {
        Router router = Router.router(vertx);

        // Enable body parsing
        router.route().handler(BodyHandler.create());

        // Metrics endpoint (OpenTelemetry via Proxy)
        if (prometheusPort > 0) {
            router.get("/metrics").handler(new MetricsHandler(vertx, prometheusPort));
        } else {
            router.get("/metrics").handler(new MetricsHandler(vertx));
        }

        // Health check
        router.get("/health")
                .respond(ctx -> {
                    JsonObject health = new JsonObject()
                            .put("status", "UP")
                            .put("nodeId", raftNode.getNodeId())
                            .put("raftState", raftNode.getState().toString())
                            .put("isLeader", raftNode.isLeader());
                    return Future.succeededFuture(health);
                });

        // Raft status endpoint
        router.get("/raft/status").respond(ctx -> {
            JsonObject status = new JsonObject()
                    .put("nodeId", raftNode.getNodeId())
                    .put("state", raftNode.getState().toString())
                    .put("currentTerm", raftNode.getCurrentTerm())
                    .put("isLeader", raftNode.isLeader())
                    .put("isRunning", raftNode.isRunning());

            String leaderId = raftNode.getLeaderId();
            if (leaderId != null) {
                status.put("leaderId", leaderId);
            }

            return Future.succeededFuture(status);
        });

        // Generic command endpoint
        router.post("/api/v1/command").respond(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            return raftNode.submitCommand(body.getMap())
                    .map(res -> new JsonObject().put("result", "OK").put("data", res));
        });

        // Agent Registration
        router.post("/api/v1/agents/register").respond(ctx -> {
            try {
                JsonObject body = ctx.body().asJsonObject();
                AgentInfo agentInfo = body.mapTo(AgentInfo.class);
                AgentCommand command = AgentCommand.register(agentInfo);

                return raftNode.submitCommand(command)
                        .map(res -> {
                            ctx.response().setStatusCode(201);
                            return new JsonObject()
                                    .put("success", true)
                                    .put("agentId", agentInfo.getAgentId());
                        });
            } catch (Exception e) {
                logger.error("Failed to register agent", e);
                return Future.failedFuture(e);
            }
        });

        // Create Transfer Job
        router.post("/api/v1/transfers").respond(ctx -> {
            try {
                JsonObject body = ctx.body().asJsonObject();
                TransferJob job = body.mapTo(TransferJob.class);
                TransferJobCommand command = TransferJobCommand.create(job);

                return raftNode.submitCommand(command)
                        .map(res -> {
                            ctx.response().setStatusCode(201);
                            return new JsonObject()
                                    .put("success", true)
                                    .put("jobId", job.getJobId());
                        });
            } catch (Exception e) {
                logger.error("Failed to create transfer job", e);
                return Future.failedFuture(e);
            }
        });

        // Assign Job
        router.post("/api/v1/jobs/assign").respond(ctx -> {
            try {
                JsonObject body = ctx.body().asJsonObject();
                JobAssignment assignment = body.mapTo(JobAssignment.class);
                JobAssignmentCommand command = JobAssignmentCommand.assign(assignment);

                return raftNode.submitCommand(command)
                        .map(res -> {
                            ctx.response().setStatusCode(201);
                            return new JsonObject()
                                    .put("success", true)
                                    .put("assignmentId", command.getAssignmentId());
                        });
            } catch (Exception e) {
                logger.error("Failed to assign job", e);
                return Future.failedFuture(e);
            }
        });

        // Get Transfer Job by ID
        router.get("/api/v1/transfers/:jobId").respond(ctx -> {
            String jobId = ctx.pathParam("jobId");
            QuorusStateMachine stateMachine = (QuorusStateMachine) raftNode.getStateMachine();

            TransferJobSnapshot job = stateMachine.getTransferJobs().get(jobId);
            if (job == null) {
                ctx.response().setStatusCode(404);
                return Future.succeededFuture(new JsonObject().put("error", "Job not found"));
            }

            // Get the latest assignment status for this job
            JobAssignment latestAssignment = stateMachine.getJobAssignments().values().stream()
                    .filter(a -> a.getJobId().equals(jobId))
                    .findFirst()
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

            return Future.succeededFuture(response);
        });

        // Get Agent Jobs (pending only)
        router.get("/api/v1/agents/:agentId/jobs").handler(ctx -> {
            String agentId = ctx.pathParam("agentId");
            QuorusStateMachine stateMachine = (QuorusStateMachine) raftNode.getStateMachine();

            // Only return assignments that are NOT completed or failed
            List<JobAssignment> assignments = stateMachine.getJobAssignments().values().stream()
                    .filter(a -> a.getAgentId().equals(agentId))
                    .filter(a -> a.getStatus() != JobAssignmentStatus.COMPLETED
                            && a.getStatus() != JobAssignmentStatus.FAILED)
                    .collect(Collectors.toList());

            JsonArray jsonArray = new JsonArray();
            assignments.forEach(jsonArray::add);
            ctx.response().setStatusCode(200).end(jsonArray.encode());
        });

        // Update Job Status (Assignment Status)
        router.post("/api/v1/jobs/:jobId/status").respond(ctx -> {
            try {
                String jobId = ctx.pathParam("jobId");
                JsonObject body = ctx.body().asJsonObject();
                String agentId = body.getString("agentId");
                String statusStr = body.getString("status");
                Long bytesTransferred = body.getLong("bytesTransferred", 0L);
                JobAssignmentStatus status = JobAssignmentStatus.valueOf(statusStr);

                // Reconstruct assignment ID based on convention: jobId:agentId
                String assignmentId = jobId + ":" + agentId;

                // Update job assignment status
                JobAssignmentCommand assignmentCommand = JobAssignmentCommand.updateStatus(assignmentId, status);

                // Also update the transfer job progress if bytes transferred was provided
                Future<Object> assignmentFuture = raftNode.submitCommand(assignmentCommand);

                if (bytesTransferred > 0) {
                    TransferJobCommand jobCommand = TransferJobCommand.updateProgress(jobId, bytesTransferred);
                    return assignmentFuture
                            .compose(res -> raftNode.submitCommand(jobCommand))
                            .map(res -> new JsonObject().put("success", true));
                } else {
                    return assignmentFuture
                            .map(res -> new JsonObject().put("success", true));
                }
            } catch (Exception e) {
                logger.error("Failed to update status", e);
                return Future.failedFuture(e);
            }
        });

        httpServer = vertx.createHttpServer()
                .requestHandler(router);

        return httpServer.listen(port)
                .onSuccess(server -> logger.info("HTTP API Server listening on port {}", port))
                .onFailure(err -> logger.error("Failed to start HTTP API Server", err))
                .mapEmpty();
    }

    public Future<Void> stop() {
        if (httpServer != null) {
            return httpServer.close()
                    .onSuccess(v -> logger.info("HTTP API Server stopped"));
        }
        return Future.succeededFuture();
    }
}
