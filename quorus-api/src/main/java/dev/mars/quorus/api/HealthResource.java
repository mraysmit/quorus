/*
 * Copyright 2024 Quorus Project
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

import dev.mars.quorus.transfer.TransferEngine;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * REST API Resource for health checks and service discovery.
 * Provides endpoints for monitoring service health and status.
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Health & Status", description = "Service health and status monitoring API")
public class HealthResource {

    private final LocalDateTime startTime = LocalDateTime.now();

    @Inject
    TransferEngine transferEngine;

    /**
     * Service discovery endpoint.
     */
    @GET
    @Path("/info")
    @Operation(summary = "Service Info", description = "Get service information for discovery")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Service information retrieved successfully")
    })
    public Response info() {
        Map<String, Object> info = new HashMap<>();
        
        info.put("name", "quorus-api");
        info.put("version", "2.0");
        info.put("description", "Enterprise-grade file transfer system REST API");
        info.put("phase", "2.1 - Basic Service Architecture");
        info.put("framework", "Quarkus");
        info.put("capabilities", new String[]{
            "file-transfer",
            "progress-tracking",
            "multi-protocol",
            "rest-api",
            "health-monitoring",
            "openapi-documentation"
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
            "/api/v1/info",
            "/api/v1/status",
            "/health",
            "/metrics"
        });
        
        return Response.ok(info).build();
    }

    /**
     * Detailed service status endpoint.
     */
    @GET
    @Path("/status")
    @Operation(summary = "Service Status", description = "Get detailed service status and metrics")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Service status retrieved successfully"),
        @APIResponse(responseCode = "401", description = "Unauthorized")
    })
    public Response status() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("service", "quorus-api");
        status.put("version", "2.0");
        status.put("phase", "2.1 - Basic Service Architecture");
        status.put("framework", "Quarkus");
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
        
        // System metrics
        Map<String, Object> systemMetrics = new HashMap<>();
        Runtime runtime = Runtime.getRuntime();
        systemMetrics.put("totalMemory", runtime.totalMemory());
        systemMetrics.put("freeMemory", runtime.freeMemory());
        systemMetrics.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
        systemMetrics.put("maxMemory", runtime.maxMemory());
        systemMetrics.put("availableProcessors", runtime.availableProcessors());
        status.put("system", systemMetrics);
        
        return Response.ok(status).build();
    }
}
