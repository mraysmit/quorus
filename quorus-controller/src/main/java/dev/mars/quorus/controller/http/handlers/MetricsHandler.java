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

import dev.mars.quorus.controller.config.AppConfig;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP handler for system metrics.
 * Proxies the OpenTelemetry Prometheus exporter.
 * 
 * The Prometheus port is read from configuration (quorus.telemetry.prometheus.port),
 * defaulting to 9464.
 *
 * Endpoint: GET /metrics
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-11
 * @version 2.1 (Configurable port)
 */
public class MetricsHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(MetricsHandler.class);
    private static final int DEFAULT_PROMETHEUS_PORT = 9464;
    
    private final WebClient webClient;
    private final int prometheusPort;

    /**
     * Creates a MetricsHandler with the default Prometheus port from configuration.
     */
    public MetricsHandler(Vertx vertx) {
        this(vertx, AppConfig.get().getPrometheusPort());
    }

    /**
     * Creates a MetricsHandler with a specific Prometheus port.
     * Useful for testing with non-default ports.
     */
    public MetricsHandler(Vertx vertx, int prometheusPort) {
        this.webClient = WebClient.create(vertx);
        this.prometheusPort = prometheusPort;
    }

    @Override
    public void handle(RoutingContext ctx) {
        webClient.get(prometheusPort, "localhost", "/metrics")
                .send()
                .onSuccess(response -> {
                    if (response.statusCode() == 200) {
                        ctx.response()
                                .setStatusCode(200)
                                .putHeader("Content-Type", response.getHeader("Content-Type"))
                                .end(response.bodyAsString());
                    } else {
                        logger.warn("Metrics proxy failed: {}", response.statusMessage());
                        ctx.response().setStatusCode(response.statusCode()).end();
                    }
                })
                .onFailure(err -> {
                    logger.error("Failed to fetch metrics from OTel exporter on port {}", prometheusPort, err);
                    ctx.response().setStatusCode(500).end("Internal Server Error: Metrics unavailable");
                });
    }
}
