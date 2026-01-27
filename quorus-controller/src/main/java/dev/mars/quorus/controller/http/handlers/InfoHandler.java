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
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP handler for API information.
 * 
 * Endpoint: GET /api/v1/info
 * 
 * Provides information about the Quorus controller API including:
 * - API version
 * - Available endpoints
 * - Controller information
 * - System capabilities
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-11
 * @version 1.0
 */
public class InfoHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(InfoHandler.class);
    private final RaftNode raftNode;
    private final ObjectMapper objectMapper;
    
    private static final String API_VERSION = "v1";
    private static final String QUORUS_VERSION = "2.0-SNAPSHOT";

    public InfoHandler(RaftNode raftNode) {
        this.raftNode = raftNode;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        logger.debug("InfoHandler initialized with raftNode={}", raftNode.getNodeId());
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
            logger.debug("Building API info response");
            Map<String, Object> info = new HashMap<>();
            
            // API information
            Map<String, Object> apiInfo = new HashMap<>();
            apiInfo.put("version", API_VERSION);
            apiInfo.put("quorusVersion", QUORUS_VERSION);
            apiInfo.put("description", "Quorus Distributed File Transfer System API");
            apiInfo.put("documentation", "https://github.com/quorus/quorus/docs");
            info.put("api", apiInfo);
            logger.debug("API info added: version={}, quorusVersion={}", API_VERSION, QUORUS_VERSION);
            
            // Controller information
            logger.debug("Querying Raft node for controller info");
            Map<String, Object> controllerInfo = new HashMap<>();
            controllerInfo.put("nodeId", raftNode.getNodeId());
            controllerInfo.put("state", raftNode.getState().toString());
            controllerInfo.put("isLeader", raftNode.isLeader());
            controllerInfo.put("currentTerm", raftNode.getCurrentTerm());
            info.put("controller", controllerInfo);
            logger.debug("Controller info added: nodeId={}, state={}, isLeader={}, term={}", 
                    raftNode.getNodeId(), raftNode.getState(), raftNode.isLeader(), raftNode.getCurrentTerm());
            
            // Available endpoints
            logger.debug("Building endpoints list");
            Map<String, List<Map<String, String>>> endpoints = new HashMap<>();
            
            // Health endpoints
            endpoints.put("health", List.of(
                    Map.of("method", "GET", "path", "/health", "description", "Overall system health"),
                    Map.of("method", "GET", "path", "/health/ready", "description", "Readiness probe"),
                    Map.of("method", "GET", "path", "/health/live", "description", "Liveness probe"),
                    Map.of("method", "GET", "path", "/status", "description", "Detailed status information")
            ));
            
            // Agent endpoints
            endpoints.put("agents", List.of(
                    Map.of("method", "POST", "path", "/api/v1/agents/register", "description", "Register new agent"),
                    Map.of("method", "POST", "path", "/api/v1/agents/heartbeat", "description", "Send agent heartbeat"),
                    Map.of("method", "GET", "path", "/api/v1/agents", "description", "List all registered agents")
            ));
            
            // Transfer endpoints
            endpoints.put("transfers", List.of(
                    Map.of("method", "POST", "path", "/api/v1/transfers", "description", "Create new transfer job"),
                    Map.of("method", "GET", "path", "/api/v1/transfers/{jobId}", "description", "Get transfer job status"),
                    Map.of("method", "DELETE", "path", "/api/v1/transfers/{jobId}", "description", "Cancel transfer job")
            ));
            
            // Cluster endpoints
            endpoints.put("cluster", List.of(
                    Map.of("method", "GET", "path", "/api/v1/cluster", "description", "Get cluster status")
            ));
            
            // Metrics endpoints
            endpoints.put("metrics", List.of(
                    Map.of("method", "GET", "path", "/metrics", "description", "Prometheus metrics (Accept: text/plain) or JSON metrics (Accept: application/json)")
            ));
            
            // Info endpoints
            endpoints.put("info", List.of(
                    Map.of("method", "GET", "path", "/api/v1/info", "description", "API information and available endpoints")
            ));
            
            info.put("endpoints", endpoints);
            logger.debug("Endpoints added: count={}", endpoints.size());
            
            // Capabilities
            Map<String, Object> capabilities = new HashMap<>();
            capabilities.put("raftConsensus", true);
            capabilities.put("distributedState", true);
            capabilities.put("agentFleetManagement", true);
            capabilities.put("transferJobCoordination", true);
            capabilities.put("prometheusMetrics", true);
            capabilities.put("healthChecks", true);
            info.put("capabilities", capabilities);
            logger.debug("Capabilities added");
            
            // Timestamp
            info.put("timestamp", Instant.now().toString());
            
            logger.debug("Returning API info response");
            sendJsonResponse(exchange, 200, info);

        } catch (Exception e) {
            logger.error("Error generating API info", e);
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

