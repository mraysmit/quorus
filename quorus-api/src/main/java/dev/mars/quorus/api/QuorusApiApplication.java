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

package dev.mars.quorus.api;

import dev.mars.quorus.api.config.ApiConfig;
import dev.mars.quorus.api.config.VertxProducer;
import dev.mars.quorus.api.service.AgentFleetStartupService;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application class for Quorus API.
 * Bootstraps Weld CDI container and starts Vert.x HTTP server.
 *
 * This replaces the Quarkus runtime with a standalone Vert.x 5.x + Weld CDI application.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-12-17
 */
public class QuorusApiApplication {

    private static final Logger logger = LoggerFactory.getLogger(QuorusApiApplication.class);

    // Configuration loaded from application.properties
    private static final ApiConfig config = ApiConfig.get();

    private SeContainer container;
    private HttpServer server;

    public static void main(String[] args) {
        QuorusApiApplication app = new QuorusApiApplication();

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received");
            app.stop();
        }));

        try {
            app.start();
        } catch (Exception e) {
            logger.error("Failed to start application", e);
            System.exit(1);
        }
    }

    /**
     * Start the application.
     */
    public void start() throws Exception {
        logger.info("Starting Quorus API application...");

        // Initialize Weld CDI container
        logger.info("Initializing Weld CDI container...");
        SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        container = initializer.initialize();
        logger.info("Weld CDI container initialized");

        // Configure Jackson (must be done before any JSON serialization)
        logger.info("Configuring Jackson...");
        container.select(dev.mars.quorus.api.config.JacksonConfig.class).get();
        logger.info("Jackson configured");

        // Get Vert.x instance from CDI
        Vertx vertx = container.select(Vertx.class).get();
        logger.info("Vert.x instance obtained from CDI");

        // Create router
        Router router = Router.router(vertx);

        // Add body handler for POST/PUT requests
        router.route().handler(BodyHandler.create());

        // Register REST resource routes
        logger.info("Registering REST resource routes...");
        HealthResource healthResource = container.select(HealthResource.class).get();
        healthResource.registerRoutes(router);

        TransferResource transferResource = container.select(TransferResource.class).get();
        transferResource.registerRoutes(router);

        AgentRegistrationResource agentResource = container.select(AgentRegistrationResource.class).get();
        agentResource.registerRoutes(router);

        logger.info("REST resource routes registered");

        // Start agent fleet services
        logger.info("Starting agent fleet services...");
        AgentFleetStartupService fleetService = container.select(AgentFleetStartupService.class).get();
        fleetService.start();
        logger.info("Agent fleet services started");

        // Get port and host from configuration
        int port = config.getHttpPort();
        String host = config.getHttpHost();

        // Create and start HTTP server
        logger.info("Starting HTTP server on {}:{}...", host, port);
        server = vertx.createHttpServer()
            .requestHandler(router)
            .listen(port, host)
            .toCompletionStage()
            .toCompletableFuture()
            .get();

        logger.info("Quorus API application started successfully on http://{}:{}", host, port);
        logger.info("Available endpoints:");
        logger.info("  - GET  /api/v1/info");
        logger.info("  - GET  /api/v1/status");
        logger.info("  - POST /api/v1/transfers");
        logger.info("  - GET  /api/v1/transfers/:jobId");
        logger.info("  - DELETE /api/v1/transfers/:jobId");
        logger.info("  - GET  /api/v1/transfers/count");
        logger.info("  - POST /api/v1/agents/register");
        logger.info("  - POST /api/v1/agents/heartbeat");
        logger.info("  - DELETE /api/v1/agents/:agentId");
        logger.info("  - GET  /api/v1/agents/:agentId");
        logger.info("  - GET  /api/v1/agents");
        logger.info("  - PUT  /api/v1/agents/:agentId/capabilities");
        logger.info("  - GET  /api/v1/agents/count");
        logger.info("  - GET  /api/v1/agents/status/:status");
    }

    /**
     * Stop the application.
     */
    public void stop() {
        logger.info("Stopping Quorus API application...");

        // Stop HTTP server
        if (server != null) {
            try {
                server.close().toCompletionStage().toCompletableFuture().get();
                logger.info("HTTP server stopped");
            } catch (Exception e) {
                logger.error("Error stopping HTTP server", e);
            }
        }

        // Stop agent fleet services
        if (container != null) {
            try {
                AgentFleetStartupService fleetService = container.select(AgentFleetStartupService.class).get();
                fleetService.stop();
                logger.info("Agent fleet services stopped");
            } catch (Exception e) {
                logger.error("Error stopping agent fleet services", e);
            }
        }

        // Close Vert.x
        if (container != null) {
            try {
                VertxProducer vertxProducer = container.select(VertxProducer.class).get();
                vertxProducer.shutdown();
                logger.info("Vert.x instance closed");
            } catch (Exception e) {
                logger.error("Error closing Vert.x", e);
            }
        }

        // Shutdown CDI container
        if (container != null) {
            try {
                container.close();
                logger.info("Weld CDI container closed");
            } catch (Exception e) {
                logger.error("Error closing CDI container", e);
            }
        }

        logger.info("Quorus API application stopped");
    }
}
