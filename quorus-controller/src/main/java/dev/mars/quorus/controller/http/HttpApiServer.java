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

import dev.mars.quorus.controller.http.handlers.*;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.state.JobAssignmentCommand;
import dev.mars.quorus.core.JobAssignment;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private volatile boolean draining = false;

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

        // Correlation ID handler - assigns X-Request-ID to every request for log tracing
        router.route().handler(new CorrelationIdHandler());
        
        // Drain mode handler - reject new requests during shutdown (except health probes)
        router.route().handler(ctx -> {
            if (draining) {
                String path = ctx.request().path();
                // Allow health probes during drain
                if (path.startsWith("/health")) {
                    ctx.next();
                    return;
                }
                // Reject other requests with 503 Service Unavailable
                logger.debug("Rejecting request during drain: {} {}", ctx.request().method(), path);
                ctx.response()
                        .setStatusCode(503)
                        .putHeader("Retry-After", "30")
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject()
                                .put("error", new JsonObject()
                                        .put("code", "SERVICE_SHUTTING_DOWN")
                                        .put("message", "Server is shutting down")
                                        .put("path", path))
                                .encode());
                return;
            }
            ctx.next();
        });

        // Global error handler for consistent error responses
        router.route().failureHandler(new GlobalErrorHandler());

        // Metrics endpoint (OpenTelemetry via Proxy)
        if (prometheusPort > 0) {
            router.get("/metrics").handler(new MetricsHandler(vertx, prometheusPort));
        } else {
            router.get("/metrics").handler(new MetricsHandler(vertx));
        }

        // ==================== Health Endpoints ====================
        router.get("/health/live").handler(new LivenessHandler());
        router.get("/health/ready").handler(new ReadinessHandler(raftNode));
        router.get("/health").handler(new HealthHandler(raftNode, VERSION));
        router.get("/status").handler(new StatusHandler(raftNode));

        // ==================== Cluster / Info Endpoints ====================
        router.get("/raft/status").handler(new ClusterHandler(raftNode));
        router.get("/api/v1/info").handler(new InfoHandler(raftNode, VERSION));

        // ==================== Agent Endpoints ====================
        router.post("/api/v1/agents/register").handler(new AgentRegistrationHandler(raftNode));
        router.post("/api/v1/agents/heartbeat").handler(new HeartbeatHandler(raftNode));
        router.get("/api/v1/agents").handler(new AgentListHandler(raftNode));
        router.get("/api/v1/agents/:agentId/jobs").handler(new AgentJobsHandler(raftNode));

        // ==================== Transfer Endpoints ====================
        TransferHandler transferHandler = new TransferHandler(raftNode);
        router.post("/api/v1/transfers").handler(transferHandler.handleCreate());
        router.get("/api/v1/transfers/:jobId").handler(transferHandler.handleGet());
        router.delete("/api/v1/transfers/:jobId").handler(transferHandler.handleDelete());

        // ==================== Job Endpoints ====================
        router.post("/api/v1/jobs/:jobId/status").handler(new JobStatusHandler(raftNode));

        // ==================== Route Endpoints ====================
        RouteHandler routeHandler = new RouteHandler(raftNode);
        router.post("/api/v1/routes").handler(routeHandler.handleCreate());
        router.get("/api/v1/routes").handler(routeHandler.handleList());
        router.get("/api/v1/routes/:routeId").handler(routeHandler.handleGet());
        router.put("/api/v1/routes/:routeId").handler(routeHandler.handleUpdate());
        router.delete("/api/v1/routes/:routeId").handler(routeHandler.handleDelete());
        router.put("/api/v1/routes/:routeId/suspend").handler(routeHandler.handleSuspend());
        router.put("/api/v1/routes/:routeId/resume").handler(routeHandler.handleResume());

        // Job assignment — kept inline (no dedicated handler)
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
                logger.error("Failed to assign job: {}", e.getMessage());
                logger.trace("Stack trace for job assignment failure", e);
                return Future.failedFuture(e);
            }
        });

        // NOTE: Generic command endpoint (/api/v1/command) removed.
        // It passed raw Map objects to submitCommand(), which was incompatible
        // with Protobuf serialization and the StateMachineCommand sealed type.
        // Use the typed endpoints (transfers, agents, metadata, jobs, routes) instead.

        httpServer = vertx.createHttpServer()
                .requestHandler(router);

        return httpServer.listen(port)
                .onSuccess(server -> logger.info("HTTP API Server listening on port {}", port))
                .onFailure(err -> {
                    logger.error("Failed to start HTTP API Server: {}", err.getMessage());
                    logger.trace("Stack trace for HTTP API Server start failure", err);
                })
                .mapEmpty();
    }

    public Future<Void> stop() {
        if (httpServer != null) {
            return httpServer.close()
                    .onSuccess(v -> logger.info("HTTP API Server stopped"));
        }
        return Future.succeededFuture();
    }
    
    /**
     * Enters drain mode - stops accepting new API requests but allows health probes.
     * 
     * <p>During drain mode, all requests except /health/* will receive a 503 response
     * with a Retry-After header.
     *
     * @return a Future that completes immediately when drain mode is activated
     */
    public Future<Void> enterDrainMode() {
        if (draining) {
            logger.debug("Already in drain mode");
            return Future.succeededFuture();
        }
        
        draining = true;
        logger.info("HTTP API Server entered drain mode - rejecting new requests");
        return Future.succeededFuture();
    }
    
    /**
     * Checks if the server is in drain mode.
     *
     * @return true if drain mode is active
     */
    public boolean isDraining() {
        return draining;
    }

    // ==================== Constants ====================

    private static final String VERSION = "1.0.0-alpha";
}
