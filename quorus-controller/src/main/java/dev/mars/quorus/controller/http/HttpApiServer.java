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
import dev.mars.quorus.controller.config.AppConfig;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.security.AuthenticationHandler;
import dev.mars.quorus.controller.security.AuthorizationHandler;
import dev.mars.quorus.controller.security.AuthorizationPolicyEngine;
import dev.mars.quorus.controller.security.SecurityConfig;
import dev.mars.quorus.controller.security.CertificateTrustState;
import dev.mars.quorus.controller.security.SecurityProfile;
import dev.mars.quorus.controller.security.audit.AuditCompletionHandler;
import dev.mars.quorus.controller.security.audit.AuditSink;
import dev.mars.quorus.controller.security.audit.HashChainedAuditLog;
import dev.mars.quorus.controller.state.QuorusStateStore;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

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
    private final String host;
    private final int port;
    private final RaftNode raftNode;
    private final QuorusStateStore stateStore;
    private final int prometheusPort;
    private final DrainModeHandler drainModeHandler;
    private final SecurityConfig securityConfig;
    private final AuthorizationPolicyEngine policyEngine;
    private final AuditSink auditSink;
    private final CertificateTrustState trustState;
    private HealthHandler healthHandler;
    private HttpServer httpServer;

    /**
     * Creates an HttpApiServer with default Prometheus port from configuration.
     */
    public HttpApiServer(Vertx vertx, int port, RaftNode raftNode, QuorusStateStore stateStore) {
        this(vertx, "127.0.0.1", port, raftNode, stateStore, -1);
    }

    /**
     * Creates an HttpApiServer with a specific Prometheus port.
     *
     * @param prometheusPort the port where Prometheus metrics are exposed, or -1 to use config default
     */
    public HttpApiServer(Vertx vertx, int port, RaftNode raftNode, QuorusStateStore stateStore, int prometheusPort) {
        this(vertx, "127.0.0.1", port, raftNode, stateStore, prometheusPort);
    }

    public HttpApiServer(Vertx vertx, String host, int port, RaftNode raftNode,
                         QuorusStateStore stateStore, int prometheusPort) {
        this(vertx, host, port, raftNode, stateStore, prometheusPort,
                SecurityConfig.developmentDisabled(), AuditSink.noOp());
    }

    public HttpApiServer(Vertx vertx, String host, int port, RaftNode raftNode,
                         QuorusStateStore stateStore, int prometheusPort, SecurityConfig securityConfig) {
        this(vertx, host, port, raftNode, stateStore, prometheusPort, securityConfig,
                CertificateTrustState.from(securityConfig));
    }

    public HttpApiServer(Vertx vertx, String host, int port, RaftNode raftNode,
                         QuorusStateStore stateStore, int prometheusPort, SecurityConfig securityConfig,
                         CertificateTrustState trustState) {
        this(vertx, host, port, raftNode, stateStore, prometheusPort, securityConfig,
                createAuditSink(securityConfig), trustState);
    }

    HttpApiServer(Vertx vertx, String host, int port, RaftNode raftNode,
                  QuorusStateStore stateStore, int prometheusPort, SecurityConfig securityConfig,
                  AuditSink auditSink) {
        this(vertx, host, port, raftNode, stateStore, prometheusPort, securityConfig, auditSink,
                CertificateTrustState.from(securityConfig));
    }

    HttpApiServer(Vertx vertx, String host, int port, RaftNode raftNode,
                  QuorusStateStore stateStore, int prometheusPort, SecurityConfig securityConfig,
                  AuditSink auditSink, CertificateTrustState trustState) {
        this.vertx = vertx;
        this.host = host;
        this.port = port;
        this.raftNode = raftNode;
        this.stateStore = stateStore;
        this.prometheusPort = prometheusPort;
        this.drainModeHandler = new DrainModeHandler();
        this.securityConfig = securityConfig;
        this.policyEngine = new AuthorizationPolicyEngine();
        this.auditSink = auditSink;
        this.trustState = trustState;
    }

    public Future<Void> start() {
        Router router = Router.router(vertx);

        // ==================== Middleware Pipeline ====================
        router.route().handler(new CorrelationIdHandler());
        router.route().handler(new AuthenticationHandler(securityConfig, auditSink, trustState));
        router.route().handler(new AuthorizationHandler(securityConfig, policyEngine, auditSink));
        router.route().handler(new AuditCompletionHandler(securityConfig, policyEngine, auditSink));
        router.route().handler(BodyHandler.create()
                .setBodyLimit(AppConfig.get().getHttpMaxBodyBytes()));
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
        this.healthHandler = new HealthHandler(raftNode, VERSION);
        healthHandler.startPeriodicChecks(vertx);
        router.get("/health/live").handler(new LivenessHandler());
        router.get("/health/ready").handler(new ReadinessHandler(raftNode));
        router.get("/health").handler(healthHandler);
        router.get("/status").handler(new StatusHandler(raftNode));

        // ==================== Cluster / Info Endpoints ====================
        router.get("/raft/status").handler(new ClusterHandler(raftNode));
        router.get("/api/v1/info").handler(new InfoHandler(raftNode, VERSION));
        router.get("/api/v1/openapi.yaml").handler(new OpenApiHandler());

        // ==================== Security Endpoints ====================
        SecurityHandler securityHandler = new SecurityHandler(policyEngine, trustState, auditSink);
        router.get("/api/v1/security/me").handler(securityHandler.handleMe());
        router.get("/api/v1/security/authorization/explain").handler(securityHandler.handleExplain());
        router.post("/api/v1/security/authorization/check").handler(securityHandler.handleCheck());
        router.get("/api/v1/security/trust").handler(securityHandler.handleTrustStatus());
        router.put("/api/v1/security/trust/revocations").handler(securityHandler.handleRevocationUpdate());

        // ==================== Governed Service Connection Endpoints ====================
        ServiceConnectionHandler serviceConnectionHandler = new ServiceConnectionHandler(raftNode, stateStore);
        router.post("/api/v1/secret-references").handler(serviceConnectionHandler.createSecret());
        router.get("/api/v1/secret-references").handler(serviceConnectionHandler.listSecrets());
        router.get("/api/v1/secret-references/:secretReferenceId").handler(serviceConnectionHandler.getSecret());
        router.put("/api/v1/secret-references/:secretReferenceId").handler(serviceConnectionHandler.updateSecret());
        router.delete("/api/v1/secret-references/:secretReferenceId").handler(serviceConnectionHandler.deleteSecret());
        router.post("/api/v1/service-connections").handler(serviceConnectionHandler.createConnection());
        router.get("/api/v1/service-connections").handler(serviceConnectionHandler.listConnections());
        router.get("/api/v1/service-connections/:serviceConnectionId").handler(serviceConnectionHandler.getConnection());
        router.put("/api/v1/service-connections/:serviceConnectionId").handler(serviceConnectionHandler.updateConnection());
        router.delete("/api/v1/service-connections/:serviceConnectionId").handler(serviceConnectionHandler.deleteConnection());
        router.post("/api/v1/service-connections/:serviceConnectionId/validate")
                .handler(serviceConnectionHandler.validateConnection());
        router.get("/api/v1/security-events").handler(serviceConnectionHandler.listEvents());

        // ==================== Agent Endpoints ====================
        router.post("/api/v1/agents/register").handler(new AgentRegistrationHandler(raftNode));
        router.post("/api/v1/agents/heartbeat").handler(new HeartbeatHandler(raftNode, stateStore));
        router.get("/api/v1/agents").handler(new AgentListHandler(stateStore));
        router.get("/api/v1/agents/:agentId/jobs").handler(new AgentJobsHandler(stateStore));

        // ==================== Transfer Endpoints ====================
        TransferHandler transferHandler = new TransferHandler(raftNode, stateStore, securityConfig.profile());
        router.post("/api/v1/transfers").handler(transferHandler.handleCreate());
        router.get("/api/v1/transfers/:jobId").handler(transferHandler.handleGet());
        router.get("/api/v1/transfers/:jobId/progress").handler(new TransferProgressHandler(
                stateStore,
                Duration.ofMillis(AppConfig.get().getTransferFreshWindowMs()),
                Duration.ofMillis(AppConfig.get().getTransferStallWindowMs())));
        router.get("/api/v1/transfers/:jobId/events").handler(new TransferEventHandler(stateStore));
        router.delete("/api/v1/transfers/:jobId").handler(transferHandler.handleDelete());
        TransferAttemptHandler attemptHandler = new TransferAttemptHandler(stateStore);
        router.get("/api/v1/transfers/:jobId/attempts").handler(attemptHandler.handleListForTransfer());
        router.get("/api/v1/transfers/:jobId/attempts/:attemptId").handler(attemptHandler.handleGet());

        // ==================== Job Status Endpoint ====================
        router.post("/api/v1/jobs/:jobId/status").handler(new JobStatusHandler(raftNode, stateStore));

        // ==================== Job Assignment Endpoints ====================
        JobAssignmentHandler assignmentHandler = new JobAssignmentHandler(raftNode, stateStore);
        router.post("/api/v1/assignments").handler(assignmentHandler.handleAssign());
        router.get("/api/v1/assignments").handler(assignmentHandler.handleList());
        router.get("/api/v1/assignments/:assignmentId").handler(assignmentHandler.handleGet());
        router.put("/api/v1/assignments/:assignmentId/accept").handler(assignmentHandler.handleAccept());
        router.put("/api/v1/assignments/:assignmentId/reject").handler(assignmentHandler.handleReject());
        router.put("/api/v1/assignments/:assignmentId/status").handler(assignmentHandler.handleUpdateStatus());
        router.put("/api/v1/assignments/:assignmentId/cancel").handler(assignmentHandler.handleCancel());
        router.delete("/api/v1/assignments/:assignmentId").handler(assignmentHandler.handleRemove());

        // ==================== Route Endpoints ====================
        RouteHandler routeHandler = new RouteHandler(raftNode, stateStore);
        router.post("/api/v1/routes").handler(routeHandler.handleCreate());
        router.get("/api/v1/routes").handler(routeHandler.handleList());
        router.get("/api/v1/routes/:routeId").handler(routeHandler.handleGet());
        router.put("/api/v1/routes/:routeId").handler(routeHandler.handleUpdate());
        router.delete("/api/v1/routes/:routeId").handler(routeHandler.handleDelete());
        router.put("/api/v1/routes/:routeId/suspend").handler(routeHandler.handleSuspend());
        router.put("/api/v1/routes/:routeId/resume").handler(routeHandler.handleResume());

        HttpServerOptions serverOptions = new HttpServerOptions();
        if (securityConfig.httpTlsEnabled()) {
            serverOptions.setSsl(true)
                    .setClientAuth(ClientAuth.REQUIRED)
                    .setKeyCertOptions(new PemKeyCertOptions()
                            .setCertPath(securityConfig.httpCertificate().toString())
                            .setKeyPath(securityConfig.httpPrivateKey().toString()))
                    .setTrustOptions(new PemTrustOptions()
                            .addCertPath(securityConfig.httpTrustBundle().toString()))
                    .setEnabledSecureTransportProtocols(java.util.Set.of("TLSv1.3"));
            if (securityConfig.httpCrl() != null) {
                serverOptions.addCrlPath(securityConfig.httpCrl().toString());
            }
        } else if (securityConfig.profile() == SecurityProfile.DEVELOPMENT) {
            logger.warn("INSECURE DEVELOPMENT MODE: HTTP TLS and request authentication are not production-ready");
        }

        httpServer = vertx.createHttpServer(serverOptions)
                .requestHandler(router);

        return httpServer.listen(port, host)
                .onSuccess(server -> logger.info("HTTP API Server listening on {}:{}", host, server.actualPort()))
                .onFailure(err -> {
                    logger.error("Failed to start HTTP API Server: {}", err.getMessage());
                    logger.debug("Stack trace for HTTP API Server start failure", err);
                })
                .mapEmpty();
    }

    public Future<Void> stop() {
        if (healthHandler != null) {
            healthHandler.stopPeriodicChecks(vertx);
        }
        if (httpServer != null) {
            return httpServer.close()
                    .onSuccess(v -> {
                        auditSink.close();
                        logger.info("HTTP API Server stopped");
                    });
        }
        auditSink.close();
        return Future.succeededFuture();
    }

    private static AuditSink createAuditSink(SecurityConfig config) {
        if (config.auditLogPath() == null) return AuditSink.noOp();
        HashChainedAuditLog operational = new HashChainedAuditLog(config.auditLogPath());
        if (config.auditEvidencePath() == null) return operational;
        // Retained evidence is first so an operational-log failure cannot lose the security event.
        return AuditSink.composite(new HashChainedAuditLog(config.auditEvidencePath()), operational);
    }

    /**
     * Returns the bound HTTP port. This is primarily useful when the server is
     * started with port {@code 0} for an isolated integration test.
     */
    public int actualPort() {
        if (httpServer == null || httpServer.actualPort() < 0) {
            throw new IllegalStateException("HTTP API Server is not listening");
        }
        return httpServer.actualPort();
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
