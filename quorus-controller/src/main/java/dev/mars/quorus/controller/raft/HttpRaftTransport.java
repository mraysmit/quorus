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

package dev.mars.quorus.controller.raft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP-based implementation of RaftTransport for real network communication.
 * This transport uses HTTP REST endpoints for inter-node communication.
 */
public class HttpRaftTransport implements RaftTransport {

    private static final Logger logger = Logger.getLogger(HttpRaftTransport.class.getName());

    private final String nodeId;
    private final String host;
    private final int port;
    private final Map<String, String> clusterNodes; // nodeId -> host:port
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final Executor executor;

    private HttpServer httpServer;
    private volatile Consumer<Object> messageHandler;
    private volatile boolean running = false;
    private volatile RaftNode raftNode; // Will be set when transport is started

    /**
     * Creates a new HTTP-based Raft transport.
     *
     * @param nodeId       The unique identifier for this node
     * @param host         The host address to bind to
     * @param port         The port to listen on
     * @param clusterNodes Map of nodeId to "host:port" for all cluster nodes
     */
    public HttpRaftTransport(String nodeId, String host, int port, Map<String, String> clusterNodes) {
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;
        this.clusterNodes = new ConcurrentHashMap<>(clusterNodes);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.httpClient = HttpClients.createDefault();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "HttpRaftTransport-" + nodeId);
            t.setDaemon(true);
            return t;
        });
    }

    public void setRaftNode(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    @Override
    public void start(Consumer<Object> messageHandler) {
        if (running) {
            logger.warning("Transport already running for node: " + nodeId);
            return;
        }

        this.messageHandler = messageHandler;

        try {
            // Create and start HTTP server
            httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
            httpServer.setExecutor(executor);

            // Register endpoints
            httpServer.createContext("/raft/vote", new VoteRequestHandler());
            httpServer.createContext("/raft/append", new AppendEntriesHandler());
            httpServer.createContext("/health", new HealthHandler());

            httpServer.start();
            running = true;

            logger.info("HTTP Raft transport started for node " + nodeId + " on " + host + ":" + port);

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to start HTTP server for node " + nodeId, e);
            throw new RuntimeException("Failed to start HTTP transport", e);
        }
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        running = false;

        if (httpServer != null) {
            httpServer.stop(1); // 1 second grace period
        }

        try {
            httpClient.close();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error closing HTTP client", e);
        }

        logger.info("HTTP Raft transport stopped for node: " + nodeId);
    }

    @Override
    public CompletableFuture<VoteResponse> sendVoteRequest(String targetNodeId, VoteRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String targetAddress = clusterNodes.get(targetNodeId);
                if (targetAddress == null) {
                    throw new RuntimeException("Unknown target node: " + targetNodeId);
                }

                String url = "http://" + targetAddress + "/raft/vote";
                String requestJson = objectMapper.writeValueAsString(request);

                HttpPost httpPost = new HttpPost(url);
                httpPost.setEntity(new StringEntity(requestJson, ContentType.APPLICATION_JSON));

                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    if (response.getCode() == 200) {
                        String responseJson = new String(response.getEntity().getContent().readAllBytes(),
                                StandardCharsets.UTF_8);
                        return objectMapper.readValue(responseJson, VoteResponse.class);
                    } else {
                        throw new RuntimeException("HTTP error: " + response.getCode());
                    }
                }

            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to send vote request to " + targetNodeId, e);
                throw new RuntimeException("Vote request failed", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<AppendEntriesResponse> sendAppendEntries(String targetNodeId,
            AppendEntriesRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String targetAddress = clusterNodes.get(targetNodeId);
                if (targetAddress == null) {
                    throw new RuntimeException("Unknown target node: " + targetNodeId);
                }

                String url = "http://" + targetAddress + "/raft/append";
                String requestJson = objectMapper.writeValueAsString(request);

                HttpPost httpPost = new HttpPost(url);
                httpPost.setEntity(new StringEntity(requestJson, ContentType.APPLICATION_JSON));

                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    if (response.getCode() == 200) {
                        String responseJson = new String(response.getEntity().getContent().readAllBytes(),
                                StandardCharsets.UTF_8);
                        return objectMapper.readValue(responseJson, AppendEntriesResponse.class);
                    } else {
                        throw new RuntimeException("HTTP error: " + response.getCode());
                    }
                }

            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to send append entries to " + targetNodeId, e);
                throw new RuntimeException("Append entries failed", e);
            }
        }, executor);
    }

    @Override
    public String getLocalNodeId() {
        return nodeId;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * HTTP handler for vote requests.
     */
    private class VoteRequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, 0);
                exchange.close();
                return;
            }

            try (InputStream is = exchange.getRequestBody()) {
                String requestJson = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                VoteRequest request = objectMapper.readValue(requestJson, VoteRequest.class);

                // Get response from RaftNode
                VoteResponse response;
                if (raftNode != null) {
                    try {
                        response = raftNode.handleVoteRequest(request);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to invoke handleVoteRequest", e);
                        response = new VoteResponse(request.getTerm(), false, nodeId);
                    }
                } else {
                    response = new VoteResponse(request.getTerm(), false, nodeId);
                }

                String responseJson = objectMapper.writeValueAsString(response);

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseJson.getBytes(StandardCharsets.UTF_8).length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseJson.getBytes(StandardCharsets.UTF_8));
                }

            } catch (Exception e) {
                logger.log(Level.WARNING, "Error handling vote request", e);
                exchange.sendResponseHeaders(500, 0);
            } finally {
                exchange.close();
            }
        }
    }

    /**
     * HTTP handler for append entries requests.
     */
    private class AppendEntriesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, 0);
                exchange.close();
                return;
            }

            try (InputStream is = exchange.getRequestBody()) {
                String requestJson = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                AppendEntriesRequest request = objectMapper.readValue(requestJson, AppendEntriesRequest.class);

                // Get response from RaftNode
                AppendEntriesResponse response;
                if (raftNode != null) {
                    try {
                        response = raftNode.handleAppendEntriesRequest(request);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to invoke handleAppendEntriesRequest", e);
                        response = new AppendEntriesResponse(request.getTerm(), false, nodeId, 0);
                    }
                } else {
                    response = new AppendEntriesResponse(request.getTerm(), false, nodeId, 0);
                }

                String responseJson = objectMapper.writeValueAsString(response);

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseJson.getBytes(StandardCharsets.UTF_8).length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseJson.getBytes(StandardCharsets.UTF_8));
                }

            } catch (Exception e) {
                logger.log(Level.WARNING, "Error handling append entries request", e);
                exchange.sendResponseHeaders(500, 0);
            } finally {
                exchange.close();
            }
        }
    }

    /**
     * Simple health check endpoint.
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "{\"status\":\"healthy\",\"nodeId\":\"" + nodeId + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
            exchange.close();
        }
    }
}
