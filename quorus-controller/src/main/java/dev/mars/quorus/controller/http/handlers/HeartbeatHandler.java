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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatHandler.class);
    private final RaftNode raftNode;
    private final ObjectMapper objectMapper;

    public HeartbeatHandler(RaftNode raftNode) {
        this.raftNode = raftNode;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        logger.debug("HeartbeatHandler initialized with raftNode={}", raftNode.getNodeId());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        logger.debug("handle() entry: method={}, path={}", method, path);
        
        if (!"POST".equals(method)) {
            logger.debug("Method not allowed: {}", method);
            sendJsonResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        try {
            // Read and parse request body
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            logger.debug("Request body received: length={}", requestBody.length());
            
            Map<String, Object> heartbeatData = objectMapper.readValue(requestBody, Map.class);

            // Validate required fields
            String agentId = (String) heartbeatData.get("agentId");
            if (agentId == null || agentId.isEmpty()) {
                logger.debug("Validation failed: missing agentId");
                sendJsonResponse(exchange, 400, Map.of("error", "Missing required field: agentId"));
                return;
            }
            logger.debug("Processing heartbeat for agentId={}", agentId);

            // Check if agent exists
            logger.debug("Querying state machine for agent: agentId={}", agentId);
            QuorusStateMachine stateMachine = (QuorusStateMachine) raftNode.getStateMachine();
            AgentInfo existingAgent = stateMachine.getAgent(agentId);
            if (existingAgent == null) {
                logger.debug("Agent not found: agentId={}", agentId);
                sendJsonResponse(exchange, 404, Map.of(
                        "error", "Agent not found",
                        "agentId", agentId,
                        "message", "Agent must be registered before sending heartbeats"
                ));
                return;
            }

            logger.debug("Heartbeat received from agent: agentId={}, currentStatus={}", 
                    agentId, existingAgent.getStatus());

            // Extract optional status from heartbeat
            AgentStatus status = null;
            if (heartbeatData.containsKey("status")) {
                String statusStr = (String) heartbeatData.get("status");
                try {
                    status = AgentStatus.valueOf(statusStr.toUpperCase());
                    logger.debug("Status provided in heartbeat: agentId={}, status={}", agentId, status);
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid status in heartbeat: agentId={}, status={}", agentId, statusStr);
                }
            }

            // Create and submit heartbeat command to Raft
            AgentCommand command;
            if (status != null) {
                command = AgentCommand.heartbeat(agentId, status, Instant.now());
                logger.debug("Submitting heartbeat command with status to Raft: agentId={}, status={}", agentId, status);
            } else {
                command = AgentCommand.heartbeat(agentId);
                logger.debug("Submitting heartbeat command to Raft: agentId={}", agentId);
            }
            
            Future<Object> future = raftNode.submitCommand(command);

            // Wait for consensus (with timeout)
            logger.debug("Waiting for Raft consensus on heartbeat: agentId={}", agentId);
            Object result = future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            logger.debug("Raft consensus completed: agentId={}, resultType={}", 
                    agentId, result != null ? result.getClass().getSimpleName() : "null");

            if (result instanceof AgentInfo) {
                AgentInfo updatedAgent = (AgentInfo) result;
                logger.debug("Heartbeat processed successfully: agentId={}, status={}, lastHeartbeat={}", 
                        updatedAgent.getAgentId(), updatedAgent.getStatus(), updatedAgent.getLastHeartbeat());
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("agentId", updatedAgent.getAgentId());
                response.put("status", updatedAgent.getStatus().toString());
                response.put("lastHeartbeat", updatedAgent.getLastHeartbeat().toString());
                response.put("message", "Heartbeat received");
                
                // Include sequence number if provided
                if (heartbeatData.containsKey("sequenceNumber")) {
                    response.put("acknowledgedSequenceNumber", heartbeatData.get("sequenceNumber"));
                    logger.debug("Acknowledging sequence number: {}", heartbeatData.get("sequenceNumber"));
                }
                
                sendJsonResponse(exchange, 200, response);
            } else {
                logger.warn("Unexpected result from heartbeat: agentId={}, resultType={}", 
                        agentId, result != null ? result.getClass().getSimpleName() : "null");
                sendJsonResponse(exchange, 500, Map.of("error", "Failed to process heartbeat"));
            }

        } catch (IllegalStateException e) {
            // Not the leader - redirect to leader
            logger.warn("Not the leader, cannot process heartbeat: {}", e.getMessage());
            sendJsonResponse(exchange, 503, Map.of(
                    "error", "Not the leader",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            logger.error("Error processing heartbeat: {}", e.getMessage());
            logger.trace("Stack trace for heartbeat processing error", e);
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

