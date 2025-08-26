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

import dev.mars.quorus.controller.raft.RaftNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Health check handler for Quorus Controller.
 * 
 * Provides health status information including:
 * - Overall controller health
 * - Raft node status
 * - Cluster connectivity
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
public class HealthHandler implements HttpHandler {

    private static final Logger logger = Logger.getLogger(HealthHandler.class.getName());
    
    private final RaftNode raftNode;

    public HealthHandler(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            // Check overall health
            boolean isHealthy = checkHealth();
            
            String response = buildHealthResponse(isHealthy);
            int statusCode = isHealthy ? 200 : 503;
            
            sendJsonResponse(exchange, statusCode, response);
            
        } catch (Exception e) {
            logger.warning("Error checking health: " + e.getMessage());
            sendResponse(exchange, 500, "{\"error\":\"Internal server error\"}");
        }
    }

    private boolean checkHealth() {
        try {
            // Check if Raft node is running
            if (raftNode == null || !raftNode.isRunning()) {
                return false;
            }

            // Additional health checks can be added here
            // - Database connectivity
            // - Disk space
            // - Memory usage
            // - Network connectivity to cluster peers

            return true;
            
        } catch (Exception e) {
            logger.warning("Health check failed: " + e.getMessage());
            return false;
        }
    }

    private String buildHealthResponse(boolean isHealthy) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"status\":\"").append(isHealthy ? "UP" : "DOWN").append("\",");
        json.append("\"timestamp\":\"").append(java.time.Instant.now().toString()).append("\",");
        json.append("\"checks\":{");
        
        // Raft node check
        boolean raftHealthy = raftNode != null && raftNode.isRunning();
        json.append("\"raft\":{");
        json.append("\"status\":\"").append(raftHealthy ? "UP" : "DOWN").append("\"");
        if (raftHealthy) {
            json.append(",\"nodeId\":\"").append(raftNode.getNodeId()).append("\"");
            json.append(",\"state\":\"").append(raftNode.getState()).append("\"");
        }
        json.append("}");
        
        json.append("}");
        json.append("}");
        
        return json.toString();
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        sendResponse(exchange, statusCode, response);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
