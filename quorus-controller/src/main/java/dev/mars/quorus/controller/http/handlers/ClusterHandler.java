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
import dev.mars.quorus.controller.raft.RaftNode;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP handler for cluster status and management.
 * 
 * Endpoint: GET /api/v1/cluster
 * 
 * Returns information about the Raft cluster including leader, term, and node status.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-11
 * @version 1.0
 */
public class ClusterHandler implements HttpHandler {

    private static final Logger logger = Logger.getLogger(ClusterHandler.class.getName());
    private final RaftNode raftNode;
    private final ObjectMapper objectMapper;

    public ClusterHandler(RaftNode raftNode) {
        this.raftNode = raftNode;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJsonResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        try {
            // Get cluster information from RaftNode
            Map<String, Object> clusterInfo = new HashMap<>();
            
            clusterInfo.put("nodeId", raftNode.getNodeId());
            clusterInfo.put("state", raftNode.getState().toString());
            clusterInfo.put("currentTerm", raftNode.getCurrentTerm());
            clusterInfo.put("isLeader", raftNode.isLeader());
            clusterInfo.put("isRunning", raftNode.isRunning());
            
            String leaderId = raftNode.getLeaderId();
            if (leaderId != null) {
                clusterInfo.put("leaderId", leaderId);
            }
            
            logger.info("Returning cluster status: " + raftNode.getState() + " (term " + raftNode.getCurrentTerm() + ")");
            sendJsonResponse(exchange, 200, clusterInfo);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error getting cluster status", e);
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

