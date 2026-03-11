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

package dev.mars.quorus.agent.service;

import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.monitoring.HealthDetail;
import dev.mars.quorus.monitoring.HealthStatus;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

/**
 * Reactive HTTP health check service for the agent using Vert.x HTTP server.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-09-04
 * @version 1.0
 */
public class HealthService {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthService.class);
    
    private final Vertx vertx;
    private final AgentConfiguration config;
    private HttpServer server;
    private final Instant startTime;
    
    public HealthService(Vertx vertx, AgentConfiguration config) {
        this.vertx = vertx;
        this.config = config;
        this.startTime = Instant.now();
    }
    
    public Future<Void> start() {
        Router router = Router.router(vertx);
        router.get("/health").handler(this::handleHealth);
        router.get("/status").handler(this::handleStatus);

        return vertx.createHttpServer()
            .requestHandler(router)
            .listen(config.getAgentPort())
            .onSuccess(s -> {
                this.server = s;
                logger.info("Health service started on port {}", s.actualPort());
            })
            .onFailure(err -> {
                logger.error("Failed to start health service on port {}: {}", config.getAgentPort(), err.getMessage());
                logger.debug("Stack trace for health service start failure on port {}", config.getAgentPort(), err);
            })
            .mapEmpty();
    }
    
    public Future<Void> shutdown() {
        if (server != null) {
            return server.close()
                .onSuccess(v -> logger.info("Health service stopped"))
                .onFailure(err -> {
                    logger.warn("Error stopping health service: {}", err.getMessage());
                    logger.debug("Stack trace for health service shutdown failure", err);
                });
        }
        return Future.succeededFuture();
    }
    
    private void handleHealth(RoutingContext ctx) {
        HealthDetail health = HealthDetail.builder("agent")
            .status(HealthStatus.UP)
            .timestamp(Instant.now())
            .metadata("agentId", config.getAgentId())
            .metadata("uptime", Instant.now().toEpochMilli() - startTime.toEpochMilli())
            .build();

        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(JsonObject.mapFrom(health.toMap()).encode());
    }
    
    private void handleStatus(RoutingContext ctx) {
        Runtime runtime = Runtime.getRuntime();

        JsonObject status = new JsonObject()
            .put("agentId", config.getAgentId())
            .put("hostname", config.getHostname())
            .put("region", config.getRegion())
            .put("datacenter", config.getDatacenter())
            .put("version", config.getVersion())
            .put("supportedProtocols", JsonObject.mapFrom(Map.of("protocols", config.getSupportedProtocols())).getJsonArray("protocols"))
            .put("maxConcurrentTransfers", config.getMaxConcurrentTransfers())
            .put("startTime", startTime.toString())
            .put("currentTime", Instant.now().toString())
            .put("runtime", new JsonObject()
                .put("totalMemory", runtime.totalMemory())
                .put("freeMemory", runtime.freeMemory())
                .put("maxMemory", runtime.maxMemory())
                .put("availableProcessors", runtime.availableProcessors()));

        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(status.encode());
    }
}
