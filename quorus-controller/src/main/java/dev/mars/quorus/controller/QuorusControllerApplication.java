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

package dev.mars.quorus.controller;

import dev.mars.quorus.controller.config.AppConfig;
import dev.mars.quorus.controller.observability.TelemetryConfig;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application class for Quorus Controller.
 *
 * Bootstraps the Vert.x reactive runtime with OpenTelemetry tracing and deploys the main Verticle.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-26
 * @version 2.0 (OpenTelemetry)
 */
public class QuorusControllerApplication {

    private static final Logger logger = LoggerFactory.getLogger(QuorusControllerApplication.class);

    /**
     * Main entry point for the Quorus Controller application.
     */
    public static void main(String[] args) {
        logger.info("Initializing Quorus Controller with OpenTelemetry (Vert.x 5)...");

        // Load configuration first (this logs all config values)
        AppConfig config = AppConfig.get();

        // Create Vert.x instance with OpenTelemetry tracing enabled
        VertxOptions options = new VertxOptions();
        options = TelemetryConfig.configure(options);
        Vertx vertx = Vertx.vertx(options);
        
        if (config.isTelemetryEnabled()) {
            logger.info("OpenTelemetry tracing enabled - OTLP endpoint: {}, Prometheus metrics port: {}",
                    TelemetryConfig.getOtlpEndpoint(), TelemetryConfig.getPrometheusPort());
        }

        // Deploy the main verticle
        vertx.deployVerticle(new QuorusControllerVerticle())
                .onSuccess(id -> {
                    logger.info("QuorusControllerVerticle deployed successfully (Deployment ID: {})", id);
                })
                .onFailure(err -> {
                    logger.error("Failed to deploy QuorusControllerVerticle", err);
                    System.exit(1);
                });

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received, closing Vert.x...");
            vertx.close()
                    .onSuccess(v -> logger.info("Vert.x closed successfully"))
                    .onFailure(err -> logger.error("Error closing Vert.x", err));
        }));
    }
}
