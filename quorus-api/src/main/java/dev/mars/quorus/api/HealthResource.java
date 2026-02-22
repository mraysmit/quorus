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

import dev.mars.quorus.api.service.DistributedTransferService;
import dev.mars.quorus.api.service.ClusterStatus;
import dev.mars.quorus.transfer.TransferEngine;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Health and status monitoring endpoints.
 * Converted from JAX-RS to Vert.x Web for Vert.x 5.x migration.
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */
@ApplicationScoped
public class HealthResource {

    private static final Logger logger = LoggerFactory.getLogger(HealthResource.class);
    private final LocalDateTime startTime = LocalDateTime.now();

    @Inject
    TransferEngine transferEngine;

    @Inject
    DistributedTransferService distributedTransferService;

    /**
     * Register routes with the Vert.x router.
     */
    public void registerRoutes(Router router) {
        router.get("/api/v1/info").handler(this::handleInfo);
        router.get("/api/v1/status").handler(this::handleStatus);
    }

    /**
     * GET /api/v1/info - Service information for discovery
     */
    private void handleInfo(RoutingContext ctx) {
        try {
            Map<String, Object> info = new HashMap<>();

            info.put("name", "quorus-api");
            info.put("version", "2.0");
            info.put("description", "Enterprise-grade file transfer system REST API");
            info.put("phase", "2.1 - Vert.x 5.x Migration");
            info.put("framework", "Vert.x 5.x + Weld CDI");
            info.put("capabilities", new String[]{
                "file-transfer",
                "progress-tracking",
                "multi-protocol",
                "rest-api",
                "health-monitoring",
                "reactive-architecture"
            });
            info.put("protocols", new String[]{
                "http",
                "https",
                "ftp",
                "sftp",
                "smb"
            });
            info.put("endpoints", new String[]{
                "/api/v1/transfers",
                "/api/v1/agents",
                "/api/v1/info",
                "/api/v1/status"
            });

            ctx.json(info);
        } catch (Exception e) {
            logger.error("Error generating service info: {}", e.getMessage());
            logger.debug("Stack trace", e);
            ctx.response()
                .setStatusCode(500)
                .end(new JsonObject()
                    .put("error", "Internal server error")
                    .put("message", e.getMessage())
                    .encode());
        }
    }

    /**
     * GET /api/v1/status - Detailed service status and metrics
     */
    private void handleStatus(RoutingContext ctx) {
        try {
            Map<String, Object> status = new HashMap<>();

            status.put("service", "quorus-api");
            status.put("version", "2.0");
            status.put("phase", "2.1 - Vert.x 5.x Migration");
            status.put("framework", "Vert.x 5.x + Weld CDI");
            status.put("timestamp", LocalDateTime.now());
            status.put("startTime", startTime);
            status.put("uptime", java.time.Duration.between(startTime, LocalDateTime.now()).toString());

            // Transfer engine metrics
            try {
                Map<String, Object> transferMetrics = new HashMap<>();
                transferMetrics.put("activeTransfers", transferEngine.getActiveTransferCount());
                transferMetrics.put("engineStatus", "operational");
                status.put("transferEngine", transferMetrics);
            } catch (Exception e) {
                Map<String, Object> transferMetrics = new HashMap<>();
                transferMetrics.put("engineStatus", "error");
                transferMetrics.put("error", e.getMessage());
                status.put("transferEngine", transferMetrics);
            }

            // Distributed controller status
            try {
                ClusterStatus clusterStatus = distributedTransferService.getClusterStatus();
                Map<String, Object> clusterMetrics = new HashMap<>();
                clusterMetrics.put("available", clusterStatus.isAvailable());
                clusterMetrics.put("healthy", clusterStatus.isHealthy());
                clusterMetrics.put("nodeId", clusterStatus.getNodeId());
                clusterMetrics.put("state", clusterStatus.getState());
                clusterMetrics.put("term", clusterStatus.getTerm());
                clusterMetrics.put("isLeader", clusterStatus.isLeader());
                clusterMetrics.put("knownNodes", clusterStatus.getKnownNodes().size());
                clusterMetrics.put("statusDescription", clusterStatus.getStatusDescription());
                status.put("cluster", clusterMetrics);
            } catch (Exception e) {
                Map<String, Object> clusterMetrics = new HashMap<>();
                clusterMetrics.put("available", false);
                clusterMetrics.put("error", e.getMessage());
                status.put("cluster", clusterMetrics);
            }

            // System metrics
            Map<String, Object> systemMetrics = new HashMap<>();
            Runtime runtime = Runtime.getRuntime();
            systemMetrics.put("totalMemory", runtime.totalMemory());
            systemMetrics.put("freeMemory", runtime.freeMemory());
            systemMetrics.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
            systemMetrics.put("maxMemory", runtime.maxMemory());
            systemMetrics.put("availableProcessors", runtime.availableProcessors());
            status.put("system", systemMetrics);

            ctx.json(status);
        } catch (Exception e) {
            logger.warn("Error generating service status: {}", e.getMessage());
            logger.debug("Stack trace", e);
            ctx.response()
                .setStatusCode(500)
                .end(new JsonObject()
                    .put("error", "Internal server error")
                    .put("message", e.getMessage())
                    .encode());
        }
    }
}
