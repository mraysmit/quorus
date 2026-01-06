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
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.state.AgentCommand;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP handler for agent registration.
 * 
 * Endpoint: POST /api/v1/agents/register
 * 
 * Accepts AgentInfo JSON and submits registration command to Raft for distributed consensus.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-11
 * @version 1.0
 */
public class AgentRegistrationHandler implements HttpHandler {

    private static final Logger logger = Logger.getLogger(AgentRegistrationHandler.class.getName());
    private final RaftNode raftNode;
    private final ObjectMapper objectMapper;

    public AgentRegistrationHandler(RaftNode raftNode) {
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
            AgentInfo agentInfo = objectMapper.readValue(requestBody, AgentInfo.class);

            // Validate required fields
            if (agentInfo.getAgentId() == null || agentInfo.getAgentId().isEmpty()) {
                sendJsonResponse(exchange, 400, Map.of("error", "Missing required field: agentId"));
                return;
            }

            logger.info("Registering agent: " + agentInfo.getAgentId());

            // Create and submit registration command to Raft
            AgentCommand command = AgentCommand.register(agentInfo);
            CompletableFuture<Object> future = raftNode.submitCommand(command).toCompletionStage().toCompletableFuture();

            // Wait for consensus (with timeout)
            Object result = future.get(5, TimeUnit.SECONDS);

            if (result instanceof AgentInfo) {
                AgentInfo registeredAgent = (AgentInfo) result;
                logger.info("Agent registered successfully: " + registeredAgent.getAgentId());
                
                Map<String, Object> response = Map.of(
                        "agentId", registeredAgent.getAgentId(),
                        "status", registeredAgent.getStatus().toString(),
                        "message", "Agent registered successfully"
                );
                sendJsonResponse(exchange, 201, response);
            } else {
                logger.warning("Unexpected result from agent registration: " + result);
                sendJsonResponse(exchange, 500, Map.of("error", "Failed to register agent"));
            }

        } catch (IllegalStateException e) {
            // Not the leader - redirect to leader
            logger.warning("Not the leader, cannot register agent: " + e.getMessage());
            sendJsonResponse(exchange, 503, Map.of(
                    "error", "Not the leader",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error registering agent", e);
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

