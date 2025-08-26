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
import dev.mars.quorus.api.dto.AgentRegistrationRequest;
import dev.mars.quorus.api.dto.AgentRegistrationResponse;
import dev.mars.quorus.api.dto.AgentHeartbeatRequest;
import dev.mars.quorus.api.dto.AgentHeartbeatResponse;
import dev.mars.quorus.api.dto.ErrorResponse;
import dev.mars.quorus.api.service.AgentRegistryService;
import dev.mars.quorus.api.service.HeartbeatProcessor;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST API for agent registration and fleet management.
 * Provides endpoints for agents to register, update capabilities, and deregister.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
@Path("/api/v1/agents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Agent Registration", description = "Agent fleet registration and management")
public class AgentRegistrationResource {

    private static final Logger logger = Logger.getLogger(AgentRegistrationResource.class.getName());
    
    private static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 30000; // 30 seconds

    @Inject
    AgentRegistryService agentRegistryService;

    @Inject
    HeartbeatProcessor heartbeatProcessor;

    /**
     * Register a new agent with the fleet.
     * 
     * @param request the agent registration request
     * @return the registration response
     */
    @POST
    @Path("/register")
    @Operation(summary = "Register a new agent", 
               description = "Register a new agent with the Quorus fleet")
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Agent registered successfully"),
        @APIResponse(responseCode = "400", description = "Invalid registration request"),
        @APIResponse(responseCode = "409", description = "Agent ID already exists"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    public Response registerAgent(@Valid AgentRegistrationRequest request) {
        try {
            logger.info("Received agent registration request: " + request.getAgentId());
            
            // Register the agent
            CompletableFuture<AgentInfo> future = agentRegistryService.registerAgent(request);
            AgentInfo agentInfo = future.get(); // Block for registration
            
            // Create successful response
            AgentRegistrationResponse response = AgentRegistrationResponse.success(
                agentInfo, 
                DEFAULT_HEARTBEAT_INTERVAL_MS,
                getControllerEndpoint()
            );
            
            logger.info("Agent registered successfully: " + agentInfo.getAgentId());
            return Response.status(Response.Status.CREATED).entity(response).build();
            
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Invalid registration request", e);
            AgentRegistrationResponse response = AgentRegistrationResponse.failure(
                e.getMessage(), "INVALID_REQUEST");
            return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Agent registration failed", e);
            
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                AgentRegistrationResponse response = AgentRegistrationResponse.failure(
                    "Agent ID already exists", "DUPLICATE_AGENT_ID");
                return Response.status(Response.Status.CONFLICT).entity(response).build();
            }
            
            AgentRegistrationResponse response = AgentRegistrationResponse.failure(
                "Internal server error", "INTERNAL_ERROR");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(response).build();
        }
    }

    /**
     * Deregister an agent from the fleet.
     * 
     * @param agentId the agent ID to deregister
     * @return the response
     */
    @DELETE
    @Path("/{agentId}")
    @Operation(summary = "Deregister an agent", 
               description = "Remove an agent from the Quorus fleet")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Agent deregistered successfully"),
        @APIResponse(responseCode = "404", description = "Agent not found"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    public Response deregisterAgent(
            @PathParam("agentId") @Parameter(description = "Agent ID") String agentId) {
        try {
            logger.info("Deregistering agent: " + agentId);
            
            // Check if agent exists
            AgentInfo agentInfo = agentRegistryService.getAgent(agentId);
            if (agentInfo == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Agent not found: " + agentId))
                        .build();
            }
            
            // Deregister the agent
            CompletableFuture<Void> future = agentRegistryService.deregisterAgent(agentId);
            future.get(); // Block for deregistration
            
            logger.info("Agent deregistered successfully: " + agentId);
            return Response.ok().entity(Map.of("message", "Agent deregistered successfully")).build();
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Agent deregistration failed", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Internal server error"))
                    .build();
        }
    }

    /**
     * Update agent capabilities.
     * 
     * @param agentId the agent ID
     * @param capabilities the new capabilities
     * @return the response
     */
    @PUT
    @Path("/{agentId}/capabilities")
    @Operation(summary = "Update agent capabilities", 
               description = "Update the capabilities of an existing agent")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Capabilities updated successfully"),
        @APIResponse(responseCode = "404", description = "Agent not found"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    public Response updateCapabilities(
            @PathParam("agentId") @Parameter(description = "Agent ID") String agentId,
            @Valid AgentCapabilities capabilities) {
        try {
            logger.info("Updating capabilities for agent: " + agentId);
            
            // Update capabilities
            CompletableFuture<AgentInfo> future = agentRegistryService.updateAgentCapabilities(agentId, capabilities);
            AgentInfo agentInfo = future.get(); // Block for update
            
            logger.info("Agent capabilities updated successfully: " + agentId);
            return Response.ok().entity(agentInfo).build();
            
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Agent not found: " + agentId))
                        .build();
            }
            
            logger.log(Level.SEVERE, "Agent capabilities update failed", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Internal server error"))
                    .build();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Agent capabilities update failed", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Internal server error"))
                    .build();
        }
    }

    /**
     * Get agent information.
     * 
     * @param agentId the agent ID
     * @return the agent information
     */
    @GET
    @Path("/{agentId}")
    @Operation(summary = "Get agent information", 
               description = "Retrieve information about a specific agent")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Agent information retrieved"),
        @APIResponse(responseCode = "404", description = "Agent not found")
    })
    public Response getAgent(
            @PathParam("agentId") @Parameter(description = "Agent ID") String agentId) {
        try {
            AgentInfo agentInfo = agentRegistryService.getAgent(agentId);
            if (agentInfo == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Agent not found: " + agentId))
                        .build();
            }
            
            return Response.ok().entity(agentInfo).build();
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to retrieve agent information", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Internal server error"))
                    .build();
        }
    }

    /**
     * Get all agents in the fleet.
     * 
     * @return list of all agents
     */
    @GET
    @Operation(summary = "Get all agents", 
               description = "Retrieve information about all agents in the fleet")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Agent list retrieved")
    })
    public Response getAllAgents() {
        try {
            List<AgentInfo> agents = agentRegistryService.getAllAgents();
            return Response.ok().entity(agents).build();
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to retrieve agent list", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Internal server error"))
                    .build();
        }
    }

    /**
     * Get fleet statistics.
     * 
     * @return fleet statistics
     */
    @GET
    @Path("/statistics")
    @Operation(summary = "Get fleet statistics", 
               description = "Retrieve statistics about the agent fleet")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Fleet statistics retrieved")
    })
    public Response getFleetStatistics() {
        try {
            AgentRegistryService.FleetStatistics stats = agentRegistryService.getFleetStatistics();
            return Response.ok().entity(stats).build();
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to retrieve fleet statistics", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Internal server error"))
                    .build();
        }
    }

    /**
     * Process agent heartbeat.
     *
     * @param request the heartbeat request
     * @return the heartbeat response
     */
    @POST
    @Path("/heartbeat")
    @Operation(summary = "Process agent heartbeat",
               description = "Process a heartbeat message from an agent and return acknowledgment")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Heartbeat processed successfully"),
        @APIResponse(responseCode = "400", description = "Invalid heartbeat request"),
        @APIResponse(responseCode = "404", description = "Agent not found"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    public Response processHeartbeat(@Valid AgentHeartbeatRequest request) {
        try {
            logger.info("Received heartbeat from agent: " + request.getAgentId());

            // Process the heartbeat
            CompletableFuture<AgentHeartbeatResponse> future = heartbeatProcessor.processHeartbeat(request);
            AgentHeartbeatResponse response = future.get(); // Block for processing

            if (response.isSuccess()) {
                logger.fine("Heartbeat processed successfully for agent: " + request.getAgentId());
                return Response.ok(response).build();
            } else {
                logger.warning("Heartbeat processing failed for agent: " + request.getAgentId() +
                             " - " + response.getMessage());
                return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to process heartbeat from agent: " +
                      (request != null ? request.getAgentId() : "unknown"), e);

            AgentHeartbeatResponse errorResponse = AgentHeartbeatResponse.failure(
                request != null ? request.getAgentId() : "unknown",
                "Internal server error: " + e.getMessage(),
                "INTERNAL_ERROR"
            );

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                          .entity(errorResponse)
                          .build();
        }
    }

    /**
     * Get heartbeat statistics.
     *
     * @return heartbeat statistics
     */
    @GET
    @Path("/heartbeat/stats")
    @Operation(summary = "Get heartbeat statistics",
               description = "Get statistics about agent heartbeats and health")
    @APIResponse(responseCode = "200", description = "Statistics retrieved successfully")
    public Response getHeartbeatStatistics() {
        try {
            HeartbeatProcessor.HeartbeatStatistics stats = heartbeatProcessor.getHeartbeatStatistics();
            return Response.ok(stats).build();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to get heartbeat statistics", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                          .entity(new ErrorResponse("Failed to get heartbeat statistics", e.getMessage()))
                          .build();
        }
    }

    /**
     * Get the controller endpoint for agent communication.
     *
     * @return the controller endpoint
     */
    private String getControllerEndpoint() {
        // TODO: Make this configurable
        return "http://localhost:8080/api/v1";
    }
}
