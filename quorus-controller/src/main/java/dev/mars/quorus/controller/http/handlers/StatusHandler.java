/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 */

package dev.mars.quorus.controller.http.handlers;

import dev.mars.quorus.controller.raft.RaftNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Status handler - provides detailed controller status information.
 */
public class StatusHandler implements HttpHandler {
    
    private final RaftNode raftNode;

    public StatusHandler(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String response = "{\"status\":\"Controller running\",\"nodeId\":\"" + 
                         (raftNode != null ? raftNode.getNodeId() : "unknown") + "\"}";
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        sendResponse(exchange, 200, response);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
