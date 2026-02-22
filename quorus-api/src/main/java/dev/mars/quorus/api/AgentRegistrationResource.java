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

import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.agent.AgentCapabilities;
import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.api.dto.AgentRegistrationRequest;
import dev.mars.quorus.api.dto.AgentRegistrationResponse;
import dev.mars.quorus.api.dto.AgentHeartbeatRequest;
import dev.mars.quorus.api.dto.AgentHeartbeatResponse;
import dev.mars.quorus.api.dto.ErrorResponse;
import dev.mars.quorus.api.service.AgentRegistryService;
import dev.mars.quorus.api.service.HeartbeatProcessor;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST API for agent registration and fleet management.
 * Converted from JAX-RS to Vert.x Web for Vert.x 5.x migration.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-26
 * @version 1.0
 */
@ApplicationScoped
public class AgentRegistrationResource {

    private static final Logger logger = LoggerFactory.getLogger(AgentRegistrationResource.class);

    private static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 30000; // 30 seconds

    @Inject
    AgentRegistryService agentRegistryService;

    @Inject
    HeartbeatProcessor heartbeatProcessor;

    /**
     * Register routes with the Vert.x router.
     */
    public void registerRoutes(Router router) {
        router.post("/api/v1/agents/register").handler(this::handleRegisterAgent);
        router.post("/api/v1/agents/heartbeat").handler(this::handleHeartbeat);
        router.delete("/api/v1/agents/:agentId").handler(this::handleDeregisterAgent);
        router.get("/api/v1/agents/:agentId").handler(this::handleGetAgent);
        router.get("/api/v1/agents").handler(this::handleListAgents);
        router.put("/api/v1/agents/:agentId/capabilities").handler(this::handleUpdateCapabilities);
        router.get("/api/v1/agents/count").handler(this::handleGetAgentCount);
        router.get("/api/v1/agents/status/:status").handler(this::handleGetAgentsByStatus);
    }

    /**
     * POST /api/v1/agents/register - Register a new agent with the fleet
     */
    private void handleRegisterAgent(RoutingContext ctx) {
        try {
            AgentRegistrationRequest request = ctx.body().asPojo(AgentRegistrationRequest.class);
            logger.info("Received agent registration request: {}", request.getAgentId());

            // Register the agent
            CompletableFuture<AgentInfo> future = agentRegistryService.registerAgent(request);
            AgentInfo agentInfo = future.get(); // Block for registration

            // Create successful response
            AgentRegistrationResponse response = AgentRegistrationResponse.success(
                agentInfo,
                DEFAULT_HEARTBEAT_INTERVAL_MS,
                getControllerEndpoint()
            );

            logger.info("Agent registered successfully: {}", agentInfo.getAgentId());
            ctx.response().setStatusCode(201).end(JsonObject.mapFrom(response).encode());

        } catch (IllegalArgumentException e) {
            logger.error("Invalid registration request: {}", e.getMessage());
            logger.debug("Stack trace", e);
            AgentRegistrationResponse response = AgentRegistrationResponse.failure(
                e.getMessage(), "INVALID_REQUEST");
            ctx.response().setStatusCode(400).end(JsonObject.mapFrom(response).encode());

        } catch (Exception e) {
            logger.error("Agent registration failed: {}", e.getMessage());
            logger.debug("Stack trace", e);

            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                AgentRegistrationResponse response = AgentRegistrationResponse.failure(
                    "Agent ID already exists", "DUPLICATE_AGENT_ID");
                ctx.response().setStatusCode(409).end(JsonObject.mapFrom(response).encode());
            } else {
                AgentRegistrationResponse response = AgentRegistrationResponse.failure(
                    "Internal server error", "INTERNAL_ERROR");
                ctx.response().setStatusCode(500).end(JsonObject.mapFrom(response).encode());
            }
        }
    }

    /**
     * POST /api/v1/agents/heartbeat - Process agent heartbeat
     */
    private void handleHeartbeat(RoutingContext ctx) {
        try {
            AgentHeartbeatRequest request = ctx.body().asPojo(AgentHeartbeatRequest.class);

            if (request == null || request.getAgentId() == null) {
                ctx.response().setStatusCode(400)
                    .end(JsonObject.mapFrom(new ErrorResponse("Invalid request", "Agent ID is required")).encode());
                return;
            }

            logger.debug("Processing heartbeat from agent: {}", request.getAgentId());

            // Process heartbeat
            heartbeatProcessor.processHeartbeat(request).join();

            // Create response
            AgentHeartbeatResponse response = new AgentHeartbeatResponse();
            response.setSuccess(true);
            response.setMessage("Heartbeat processed successfully");
            response.setNextHeartbeatInterval(DEFAULT_HEARTBEAT_INTERVAL_MS);

            ctx.json(response);

        } catch (Exception e) {
            logger.error("Error processing heartbeat", e);
            ctx.response().setStatusCode(500)
                .end(JsonObject.mapFrom(new ErrorResponse("Internal server error", e.getMessage())).encode());
        }
    }




    /**
     * DELETE /api/v1/agents/:agentId - Deregister an agent from the fleet
     */
    private void handleDeregisterAgent(RoutingContext ctx) {
        try {
            String agentId = ctx.pathParam("agentId");
            logger.info("Deregistering agent: {}", agentId);

            // Check if agent exists
            AgentInfo agentInfo = agentRegistryService.getAgent(agentId);
            if (agentInfo == null) {
                ctx.response().setStatusCode(404)
                    .end(JsonObject.mapFrom(new ErrorResponse("Agent not found: " + agentId)).encode());
                return;
            }

            // Deregister the agent
            agentRegistryService.deregisterAgent(agentId);

            logger.info("Agent deregistered successfully: {}", agentId);
            ctx.json(Map.of("message", "Agent deregistered successfully", "agentId", agentId));

        } catch (Exception e) {
            logger.error("Error deregistering agent", e);
            ctx.response().setStatusCode(500)
                .end(JsonObject.mapFrom(new ErrorResponse("Internal server error")).encode());
        }
    }

    /**
     * GET /api/v1/agents/:agentId - Get agent information
     */
    private void handleGetAgent(RoutingContext ctx) {
        try {
            String agentId = ctx.pathParam("agentId");
            AgentInfo agentInfo = agentRegistryService.getAgent(agentId);

            if (agentInfo == null) {
                ctx.response().setStatusCode(404)
                    .end(JsonObject.mapFrom(new ErrorResponse("Agent not found: " + agentId)).encode());
            } else {
                ctx.json(agentInfo);
            }
        } catch (Exception e) {
            logger.error("Error retrieving agent information", e);
            ctx.response().setStatusCode(500)
                .end(JsonObject.mapFrom(new ErrorResponse("Internal server error")).encode());
        }
    }

    /**
     * GET /api/v1/agents - Get all agents in the fleet
     */
    private void handleListAgents(RoutingContext ctx) {
        try {
            List<AgentInfo> agents = agentRegistryService.getAllAgents();
            ctx.json(agents);
        } catch (Exception e) {
            logger.error("Error retrieving agent list", e);
            ctx.response().setStatusCode(500)
                .end(JsonObject.mapFrom(new ErrorResponse("Internal server error")).encode());
        }
    }

    /**
     * PUT /api/v1/agents/:agentId/capabilities - Update agent capabilities
     */
    private void handleUpdateCapabilities(RoutingContext ctx) {
        try {
            String agentId = ctx.pathParam("agentId");
            AgentCapabilities capabilities = ctx.body().asPojo(AgentCapabilities.class);

            logger.info("Updating capabilities for agent: {}", agentId);

            // Update capabilities
            agentRegistryService.updateAgentCapabilities(agentId, capabilities).join();

            logger.info("Capabilities updated successfully for agent: {}", agentId);
            ctx.json(Map.of("message", "Capabilities updated successfully", "agentId", agentId));

        } catch (IllegalArgumentException e) {
            logger.error("Agent not found", e);
            ctx.response().setStatusCode(404)
                .end(JsonObject.mapFrom(new ErrorResponse("Agent not found")).encode());
        } catch (Exception e) {
            logger.error("Error updating capabilities", e);
            ctx.response().setStatusCode(500)
                .end(JsonObject.mapFrom(new ErrorResponse("Internal server error")).encode());
        }
    }

    /**
     * GET /api/v1/agents/count - Get agent count
     */
    private void handleGetAgentCount(RoutingContext ctx) {
        try {
            int count = agentRegistryService.getAllAgents().size();
            ctx.json(Map.of("count", count));
        } catch (Exception e) {
            logger.error("Error retrieving agent count", e);
            ctx.response().setStatusCode(500)
                .end(JsonObject.mapFrom(new ErrorResponse("Internal server error")).encode());
        }
    }

    /**
     * GET /api/v1/agents/status/:status - Get agents by status
     */
    private void handleGetAgentsByStatus(RoutingContext ctx) {
        try {
            String statusParam = ctx.pathParam("status");
            AgentStatus status = AgentStatus.valueOf(statusParam.toUpperCase());
            List<AgentInfo> agents = agentRegistryService.getAllAgents().stream()
                .filter(agent -> agent.getStatus() == status)
                .collect(java.util.stream.Collectors.toList());
            ctx.json(agents);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid status parameter: {}", ctx.pathParam("status"));
            ctx.response().setStatusCode(400)
                .end(JsonObject.mapFrom(new ErrorResponse("Invalid status")).encode());
        } catch (Exception e) {
            logger.error("Error retrieving agents by status", e);
            ctx.response().setStatusCode(500)
                .end(JsonObject.mapFrom(new ErrorResponse("Internal server error")).encode());
        }
    }

    /**
     * Get the controller endpoint URL.
     */
    private String getControllerEndpoint() {
        // TODO: Make this configurable
        return "http://localhost:8080";
    }
}
