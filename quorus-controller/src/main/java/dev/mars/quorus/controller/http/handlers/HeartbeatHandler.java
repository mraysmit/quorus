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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.state.AgentCommand;
import dev.mars.quorus.controller.state.QuorusStateMachine;
import io.vertx.core.Future;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP handler for agent heartbeats.
 * 
 * Endpoint: POST /api/v1/agents/heartbeat
 * 
 * Accepts heartbeat data from agents and submits heartbeat command to Raft for distributed consensus.
 * Updates the agent's last heartbeat timestamp and optionally status.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-11
 * @version 1.0
 */
public class HeartbeatHandler implements HttpHandler {

    private static final Logger logger = Logger.getLogger(HeartbeatHandler.class.getName());
    private final RaftNode raftNode;
    private final ObjectMapper objectMapper;

    public HeartbeatHandler(RaftNode raftNode) {
        this.raftNode = raftNode;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJsonResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        try {
            // Read and parse request body
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> heartbeatData = objectMapper.readValue(requestBody, Map.class);

            // Validate required fields
            String agentId = (String) heartbeatData.get("agentId");
            if (agentId == null || agentId.isEmpty()) {
                sendJsonResponse(exchange, 400, Map.of("error", "Missing required field: agentId"));
                return;
            }

            // Check if agent exists
            QuorusStateMachine stateMachine = (QuorusStateMachine) raftNode.getStateMachine();
            AgentInfo existingAgent = stateMachine.getAgent(agentId);
            if (existingAgent == null) {
                sendJsonResponse(exchange, 404, Map.of(
                        "error", "Agent not found",
                        "agentId", agentId,
                        "message", "Agent must be registered before sending heartbeats"
                ));
                return;
            }

            logger.fine("Heartbeat received from agent: " + agentId);

            // Extract optional status from heartbeat
            AgentStatus status = null;
            if (heartbeatData.containsKey("status")) {
                String statusStr = (String) heartbeatData.get("status");
                try {
                    status = AgentStatus.valueOf(statusStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid status in heartbeat: " + statusStr);
                }
            }

            // Create and submit heartbeat command to Raft
            AgentCommand command;
            if (status != null) {
                command = AgentCommand.heartbeat(agentId, status, Instant.now());
            } else {
                command = AgentCommand.heartbeat(agentId);
            }
            
            Future<Object> future = raftNode.submitCommand(command);

            // Wait for consensus (with timeout)
            Object result = future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);

            if (result instanceof AgentInfo) {
                AgentInfo updatedAgent = (AgentInfo) result;
                logger.fine("Heartbeat processed for agent: " + updatedAgent.getAgentId());
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("agentId", updatedAgent.getAgentId());
                response.put("status", updatedAgent.getStatus().toString());
                response.put("lastHeartbeat", updatedAgent.getLastHeartbeat().toString());
                response.put("message", "Heartbeat received");
                
                // Include sequence number if provided
                if (heartbeatData.containsKey("sequenceNumber")) {
                    response.put("acknowledgedSequenceNumber", heartbeatData.get("sequenceNumber"));
                }
                
                sendJsonResponse(exchange, 200, response);
            } else {
                logger.warning("Unexpected result from heartbeat: " + result);
                sendJsonResponse(exchange, 500, Map.of("error", "Failed to process heartbeat"));
            }

        } catch (IllegalStateException e) {
            // Not the leader - redirect to leader
            logger.warning("Not the leader, cannot process heartbeat: " + e.getMessage());
            sendJsonResponse(exchange, 503, Map.of(
                    "error", "Not the leader",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing heartbeat", e);
            sendJsonResponse(exchange, 500, Map.of(
                    "error", "Internal server error",
                    "message", e.getMessage()
            ));
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = objectMapper.writeValueAsString(data);
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}

