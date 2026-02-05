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
import dev.mars.quorus.controller.state.QuorusStateMachine;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP handler for listing registered agents.
 * 
 * Endpoint: GET /api/v1/agents
 * 
 * Returns a list of all registered agents from the Raft state machine.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-11
 * @version 1.0
 */
public class AgentListHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(AgentListHandler.class);
    private final RaftNode raftNode;
    private final ObjectMapper objectMapper;

    public AgentListHandler(RaftNode raftNode) {
        this.raftNode = raftNode;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        logger.debug("AgentListHandler initialized with raftNode={}", raftNode.getNodeId());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        logger.debug("handle() entry: method={}, path={}", method, path);
        
        if (!"GET".equals(method)) {
            logger.debug("Method not allowed: {}", method);
            sendJsonResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        try {
            // Get the state machine from RaftNode
            logger.debug("Querying state machine for agents list");
            QuorusStateMachine stateMachine = (QuorusStateMachine) raftNode.getStateMachine();
            
            // Get all agents
            Map<String, AgentInfo> agents = stateMachine.getAgents();
            logger.debug("Retrieved {} agents from state machine", agents.size());
            
            // Convert to list for JSON response
            List<Map<String, Object>> agentList = new ArrayList<>();
            for (AgentInfo agent : agents.values()) {
                Map<String, Object> agentData = new HashMap<>();
                agentData.put("agentId", agent.getAgentId());
                agentData.put("hostname", agent.getHostname());
                agentData.put("address", agent.getAddress());
                agentData.put("port", agent.getPort());
                agentData.put("status", agent.getStatus().toString());
                agentData.put("version", agent.getVersion());
                agentData.put("region", agent.getRegion());
                agentData.put("datacenter", agent.getDatacenter());
                agentData.put("registrationTime", agent.getRegistrationTime());
                agentData.put("lastHeartbeat", agent.getLastHeartbeat());
                agentData.put("healthy", agent.isHealthy());
                agentData.put("available", agent.isAvailable());
                agentList.add(agentData);
                logger.debug("Agent included: agentId={}, status={}, healthy={}, available={}", 
                        agent.getAgentId(), agent.getStatus(), agent.isHealthy(), agent.isAvailable());
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("agents", agentList);
            response.put("count", agentList.size());
            
            logger.info("Returning {} agents", agentList.size());
            sendJsonResponse(exchange, 200, response);

        } catch (Exception e) {
            logger.error("Error listing agents: {}", e.getMessage());
            logger.trace("Stack trace for agent listing error", e);
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

