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
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reactive HTTP API Server using Vert.x Web.
 *
 * <p>Single Responsibility: This class owns only server lifecycle (start/stop)
 * and route wiring. All request handling is delegated to dedicated handler classes:</p>
 * <ul>
 *   <li>{@link CorrelationIdHandler} — request ID propagation</li>
 *   <li>{@link DrainModeHandler} — graceful shutdown gating</li>
 *   <li>{@link LeaderGuardHandler} — Raft leader enforcement for writes</li>
 *   <li>{@link GlobalErrorHandler} — consistent error envelope</li>
 *   <li>Domain handlers in {@code handlers/} package — per-resource CRUD</li>
 * </ul>
 *
 * <p>Open/Closed: New endpoints can be added by creating new handler classes
 * and registering routes here — no modification to existing handlers required.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2025-08-26
 */
public class HttpApiServer {

    private static final Logger logger = LoggerFactory.getLogger(HttpApiServer.class);
    private static final String VERSION = "1.0.0-alpha";

    private final Vertx vertx;
    private final int port;
    private final RaftNode raftNode;
    private final int prometheusPort;
    private final DrainModeHandler drainModeHandler;
    private HttpServer httpServer;

    /**
     * Creates an HttpApiServer with default Prometheus port from configuration.
     */
    public HttpApiServer(Vertx vertx, int port, RaftNode raftNode) {
        this(vertx, port, raftNode, -1);
    }

    /**
     * Creates an HttpApiServer with a specific Prometheus port.
     *
     * @param prometheusPort the port where Prometheus metrics are exposed, or -1 to use config default
     */
    public HttpApiServer(Vertx vertx, int port, RaftNode raftNode, int prometheusPort) {
        this.vertx = vertx;
        this.port = port;
        this.raftNode = raftNode;
        this.prometheusPort = prometheusPort;
        this.drainModeHandler = new DrainModeHandler();
    }

    public Future<Void> start() {
        Router router = Router.router(vertx);

        // ==================== Middleware Pipeline ====================
        router.route().handler(BodyHandler.create());
        router.route().handler(new CorrelationIdHandler());
        router.route().handler(drainModeHandler);
        router.route().handler(new LeaderGuardHandler(raftNode));
        router.route().failureHandler(new GlobalErrorHandler());

        // ==================== Infrastructure Endpoints ====================
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

        // ==================== Job Status Endpoint ====================
        router.post("/api/v1/jobs/:jobId/status").handler(new JobStatusHandler(raftNode));

        // ==================== Job Assignment Endpoints ====================
        JobAssignmentHandler assignmentHandler = new JobAssignmentHandler(raftNode);
        router.post("/api/v1/assignments").handler(assignmentHandler.handleAssign());
        router.get("/api/v1/assignments").handler(assignmentHandler.handleList());
        router.get("/api/v1/assignments/:assignmentId").handler(assignmentHandler.handleGet());
        router.put("/api/v1/assignments/:assignmentId/accept").handler(assignmentHandler.handleAccept());
        router.put("/api/v1/assignments/:assignmentId/reject").handler(assignmentHandler.handleReject());
        router.put("/api/v1/assignments/:assignmentId/status").handler(assignmentHandler.handleUpdateStatus());
        router.put("/api/v1/assignments/:assignmentId/cancel").handler(assignmentHandler.handleCancel());
        router.delete("/api/v1/assignments/:assignmentId").handler(assignmentHandler.handleRemove());

        // ==================== Route Endpoints ====================
        RouteHandler routeHandler = new RouteHandler(raftNode);
        router.post("/api/v1/routes").handler(routeHandler.handleCreate());
        router.get("/api/v1/routes").handler(routeHandler.handleList());
        router.get("/api/v1/routes/:routeId").handler(routeHandler.handleGet());
        router.put("/api/v1/routes/:routeId").handler(routeHandler.handleUpdate());
        router.delete("/api/v1/routes/:routeId").handler(routeHandler.handleDelete());
        router.put("/api/v1/routes/:routeId/suspend").handler(routeHandler.handleSuspend());
        router.put("/api/v1/routes/:routeId/resume").handler(routeHandler.handleResume());

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
     * Enters drain mode — stops accepting new API requests but allows health probes.
     *
     * @return a Future that completes immediately when drain mode is activated
     */
    public Future<Void> enterDrainMode() {
        drainModeHandler.enterDrainMode();
        return Future.succeededFuture();
    }

    /**
     * Checks if the server is in drain mode.
     *
     * @return true if drain mode is active
     */
    public boolean isDraining() {
        return drainModeHandler.isDraining();
    }
}
