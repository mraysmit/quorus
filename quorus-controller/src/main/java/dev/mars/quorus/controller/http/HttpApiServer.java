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

import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.http.handlers.*;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpContext;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * HTTP API Server for Quorus Controller.
 * 
 * Provides REST API endpoints for:
 * - Agent registration and management
 * - Heartbeat processing
 * - Transfer job coordination
 * - Health and status monitoring
 * - Cluster management
 * 
 * This server is embedded within the controller and provides the external
 * interface for all controller operations.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
public class HttpApiServer {

    private static final Logger logger = Logger.getLogger(HttpApiServer.class.getName());
    
    private final int port;
    private final RaftNode raftNode;
    private HttpServer httpServer;
    private ExecutorService executor;
    private volatile boolean running = false;

    public HttpApiServer(int port, RaftNode raftNode) {
        this.port = port;
        this.raftNode = raftNode;
    }

    /**
     * Start the HTTP API server.
     */
    public synchronized void start() throws Exception {
        if (running) {
            logger.warning("HTTP API server is already running");
            return;
        }

        logger.info("Starting HTTP API server on port " + port);

        try {
            // Create HTTP server
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            
            // Create thread pool for handling requests
            executor = Executors.newFixedThreadPool(10);
            httpServer.setExecutor(executor);

            // Register API endpoints
            registerEndpoints();

            // Start the server
            httpServer.start();
            running = true;

            logger.info("HTTP API server started successfully on port " + port);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start HTTP API server", e);
            stop();
            throw e;
        }
    }

    /**
     * Stop the HTTP API server.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        logger.info("Stopping HTTP API server...");

        try {
            if (httpServer != null) {
                httpServer.stop(5); // 5 second grace period
            }

            if (executor != null) {
                executor.shutdown();
            }

            running = false;
            logger.info("HTTP API server stopped successfully");

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error stopping HTTP API server", e);
        }
    }

    /**
     * Register all HTTP endpoints.
     */
    private void registerEndpoints() {
        // Health and status endpoints
        httpServer.createContext("/health", new HealthHandler(raftNode));
        httpServer.createContext("/health/ready", new ReadinessHandler(raftNode));
        httpServer.createContext("/health/live", new LivenessHandler(raftNode));
        httpServer.createContext("/status", new StatusHandler(raftNode));

        // Agent management endpoints
        httpServer.createContext("/api/v1/agents/register", new AgentRegistrationHandler(raftNode));
        httpServer.createContext("/api/v1/agents/heartbeat", new HeartbeatHandler(raftNode));
        httpServer.createContext("/api/v1/agents/jobs", new AgentJobsHandler(raftNode));
        httpServer.createContext("/api/v1/agents", new AgentListHandler(raftNode));

        // Transfer management endpoints
        httpServer.createContext("/api/v1/transfers", new TransferHandler(raftNode));

        // Job status updates
        httpServer.createContext("/api/v1/jobs", new JobStatusHandler(raftNode));

        // Cluster management endpoints
        httpServer.createContext("/api/v1/cluster", new ClusterHandler(raftNode));

        // Metrics endpoint
        httpServer.createContext("/metrics", new MetricsHandler(raftNode));

        // API info endpoint
        httpServer.createContext("/api/v1/info", new InfoHandler(raftNode));

        logger.info("Registered HTTP API endpoints:");
        logger.info("  - Health: /health, /health/ready, /health/live");
        logger.info("  - Status: /status");
        logger.info("  - Agents: /api/v1/agents/register, /api/v1/agents/heartbeat, /api/v1/agents/{agentId}/jobs, /api/v1/agents");
        logger.info("  - Transfers: /api/v1/transfers");
        logger.info("  - Jobs: /api/v1/jobs/{jobId}/status");
        logger.info("  - Cluster: /api/v1/cluster");
        logger.info("  - Metrics: /metrics");
        logger.info("  - Info: /api/v1/info");
    }

    /**
     * Check if the server is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get the server port.
     */
    public int getPort() {
        return port;
    }
}
