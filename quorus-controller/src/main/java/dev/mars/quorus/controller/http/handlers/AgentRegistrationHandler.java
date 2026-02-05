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
import io.vertx.core.Future;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(AgentRegistrationHandler.class);
    private final RaftNode raftNode;
    private final ObjectMapper objectMapper;

    public AgentRegistrationHandler(RaftNode raftNode) {
        this.raftNode = raftNode;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        logger.debug("AgentRegistrationHandler initialized with raftNode={}", raftNode.getNodeId());
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
            
            AgentInfo agentInfo = objectMapper.readValue(requestBody, AgentInfo.class);
            logger.debug("Parsed AgentInfo: agentId={}, hostname={}, address={}", 
                    agentInfo.getAgentId(), agentInfo.getHostname(), agentInfo.getAddress());

            // Validate required fields
            if (agentInfo.getAgentId() == null || agentInfo.getAgentId().isEmpty()) {
                logger.debug("Validation failed: missing agentId");
                sendJsonResponse(exchange, 400, Map.of("error", "Missing required field: agentId"));
                return;
            }

            logger.info("Registering agent: agentId={}, hostname={}, address={}", 
                    agentInfo.getAgentId(), agentInfo.getHostname(), agentInfo.getAddress());

            // Create and submit registration command to Raft
            logger.debug("Submitting agent registration command to Raft: agentId={}", agentInfo.getAgentId());
            AgentCommand command = AgentCommand.register(agentInfo);
            Future<Object> future = raftNode.submitCommand(command);

            // Wait for consensus (with timeout)
            logger.debug("Waiting for Raft consensus: agentId={}", agentInfo.getAgentId());
            Object result = future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            logger.debug("Raft consensus completed: agentId={}, resultType={}", 
                    agentInfo.getAgentId(), result != null ? result.getClass().getSimpleName() : "null");

            if (result instanceof AgentInfo) {
                AgentInfo registeredAgent = (AgentInfo) result;
                logger.info("Agent registered successfully: agentId={}, status={}", 
                        registeredAgent.getAgentId(), registeredAgent.getStatus());
                
                Map<String, Object> response = Map.of(
                        "agentId", registeredAgent.getAgentId(),
                        "status", registeredAgent.getStatus().toString(),
                        "message", "Agent registered successfully"
                );
                sendJsonResponse(exchange, 201, response);
            } else {
                logger.warn("Unexpected result from agent registration: agentId={}, resultType={}", 
                        agentInfo.getAgentId(), result != null ? result.getClass().getSimpleName() : "null");
                sendJsonResponse(exchange, 500, Map.of("error", "Failed to register agent"));
            }

        } catch (IllegalStateException e) {
            // Not the leader - redirect to leader
            logger.warn("Not the leader, cannot register agent: {}", e.getMessage());
            sendJsonResponse(exchange, 503, Map.of(
                    "error", "Not the leader",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            logger.error("Error registering agent: {}", e.getMessage());
            logger.trace("Stack trace for agent registration error", e);
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

