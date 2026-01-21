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

package dev.mars.quorus.agent.service;

import dev.mars.quorus.agent.config.AgentConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple HTTP health check service for the agent.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-09-04
 * @version 1.0
 */
public class HealthService {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthService.class);
    
    private final AgentConfiguration config;
    private final ObjectMapper objectMapper;
    private HttpServer server;
    private final Instant startTime;
    
    public HealthService(AgentConfiguration config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.startTime = Instant.now();
    }
    
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.getAgentPort()), 0);
        server.createContext("/health", new HealthHandler());
        server.createContext("/status", new StatusHandler());
        server.setExecutor(null); // Use default executor
        server.start();
        
        logger.info("Health service started on port {}", config.getAgentPort());
    }
    
    public void shutdown() {
        if (server != null) {
            server.stop(5);
            logger.info("Health service stopped");
        }
    }
    
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                Map<String, Object> health = new HashMap<>();
                health.put("status", "UP");
                health.put("timestamp", Instant.now());
                health.put("agentId", config.getAgentId());
                health.put("uptime", Instant.now().toEpochMilli() - startTime.toEpochMilli());
                
                String response = objectMapper.writeValueAsString(health);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } else {
                exchange.sendResponseHeaders(405, -1); // Method not allowed
            }
        }
    }
    
    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                Map<String, Object> status = new HashMap<>();
                status.put("agentId", config.getAgentId());
                status.put("hostname", config.getHostname());
                status.put("region", config.getRegion());
                status.put("datacenter", config.getDatacenter());
                status.put("version", config.getVersion());
                status.put("supportedProtocols", config.getSupportedProtocols());
                status.put("maxConcurrentTransfers", config.getMaxConcurrentTransfers());
                status.put("startTime", startTime);
                status.put("currentTime", Instant.now());
                
                // Add runtime information
                Runtime runtime = Runtime.getRuntime();
                Map<String, Object> runtime_info = new HashMap<>();
                runtime_info.put("totalMemory", runtime.totalMemory());
                runtime_info.put("freeMemory", runtime.freeMemory());
                runtime_info.put("maxMemory", runtime.maxMemory());
                runtime_info.put("availableProcessors", runtime.availableProcessors());
                status.put("runtime", runtime_info);
                
                String response = objectMapper.writeValueAsString(status);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } else {
                exchange.sendResponseHeaders(405, -1); // Method not allowed
            }
        }
    }
}
