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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.monitoring.HealthDetail;
import dev.mars.quorus.monitoring.HealthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The agent's local health endpoints, {@code GET /health} and {@code GET /status}, on the JDK HTTP
 * server (RT-05b). Each request is handled on its own virtual thread.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-09-04
 * @version 2.0
 */
public class HealthService {

    private static final Logger logger = LoggerFactory.getLogger(HealthService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AgentConfiguration config;
    private final Instant startTime;
    private HttpServer server;
    private ExecutorService executor;

    public HealthService(AgentConfiguration config) {
        this.config = config;
        this.startTime = Instant.now();
    }

    /**
     * Starts serving on the configured agent port (0 picks a free port).
     *
     * @throws IOException if the port cannot be bound
     */
    public synchronized void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.getAgentPort()), 0);
        server.createContext("/", this::handle);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.start();
        logger.info("Health service started on port {}", server.getAddress().getPort());
    }

    /** The bound port, or -1 if the service is not running. */
    public synchronized int port() {
        return server == null ? -1 : server.getAddress().getPort();
    }

    /** Stops serving; does nothing if not started. */
    public synchronized void shutdown() {
        if (server != null) {
            server.stop(0);
            executor.close();
            server = null;
            logger.info("Health service stopped");
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
            } else if ("/health".equals(path)) {
                respond(exchange, health());
            } else if ("/status".equals(path)) {
                respond(exchange, status());
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        }
    }

    private String health() throws IOException {
        HealthDetail health = HealthDetail.builder("agent")
            .status(HealthStatus.UP)
            .timestamp(Instant.now())
            .metadata("agentId", config.getAgentId())
            .metadata("uptime", Instant.now().toEpochMilli() - startTime.toEpochMilli())
            .build();
        return JSON.writeValueAsString(health.toMap());
    }

    private String status() throws IOException {
        Runtime runtime = Runtime.getRuntime();
        ObjectNode status = JSON.createObjectNode()
            .put("agentId", config.getAgentId())
            .put("hostname", config.getHostname())
            .put("region", config.getRegion())
            .put("datacenter", config.getDatacenter())
            .put("version", config.getVersion());
        config.getSupportedProtocols().forEach(status.putArray("supportedProtocols")::add);
        status.put("maxConcurrentTransfers", config.getMaxConcurrentTransfers())
            .put("startTime", startTime.toString())
            .put("currentTime", Instant.now().toString());
        status.putObject("runtime")
            .put("totalMemory", runtime.totalMemory())
            .put("freeMemory", runtime.freeMemory())
            .put("maxMemory", runtime.maxMemory())
            .put("availableProcessors", runtime.availableProcessors());
        return JSON.writeValueAsString(status);
    }

    private static void respond(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
