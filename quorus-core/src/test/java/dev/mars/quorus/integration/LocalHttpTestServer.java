package dev.mars.quorus.integration;

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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Local HTTP test server to replace external dependencies like httpbin.org.
 * Provides endpoints for testing file transfers without requiring internet connectivity.
 *
 * <p>Supported endpoints:</p>
 * <ul>
 *   <li>/bytes/{n} - Returns n random bytes (max 10MB)</li>
 *   <li>/status/{code} - Returns the specified HTTP status code</li>
 *   <li>/delay/{seconds} - Delays response by specified seconds (max 30)</li>
 * </ul>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-11
 * @version 1.0
 */
public class LocalHttpTestServer {

    private static final Logger logger = Logger.getLogger(LocalHttpTestServer.class.getName());
    private static final SecureRandom random = new SecureRandom();

    private final HttpServer server;
    private final int port;

    /**
     * Creates and starts a local HTTP test server on an available port.
     *
     * @throws IOException if the server cannot be started
     */
    public LocalHttpTestServer() throws IOException {
        this(0); // Use any available port
    }

    /**
     * Creates and starts a local HTTP test server on the specified port.
     *
     * @param port the port to bind to (0 for any available port)
     * @throws IOException if the server cannot be started
     */
    public LocalHttpTestServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        this.port = server.getAddress().getPort();

        // Register handlers
        server.createContext("/bytes/", new BytesHandler());
        server.createContext("/status/", new StatusHandler());
        server.createContext("/delay/", new DelayHandler());

        // Use a thread pool for handling requests
        server.setExecutor(Executors.newFixedThreadPool(4));

        server.start();
        logger.info("Local HTTP test server started on port " + this.port);
    }

    /**
     * Gets the port the server is listening on.
     *
     * @return the server port
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets the base URL for the server.
     *
     * @return the base URL (e.g., "http://localhost:8080")
     */
    public String getBaseUrl() {
        return "http://localhost:" + port;
    }

    /**
     * Stops the server.
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            logger.info("Local HTTP test server stopped");
        }
    }

    /**
     * Handler for /bytes/{n} endpoint - returns n random bytes.
     */
    private static class BytesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");

            if (parts.length < 3) {
                sendError(exchange, 400, "Bad Request: Missing byte count");
                return;
            }

            try {
                int numBytes = Integer.parseInt(parts[2]);

                if (numBytes < 0 || numBytes > 10 * 1024 * 1024) { // Max 10MB
                    sendError(exchange, 400, "Bad Request: Invalid byte count (0-10485760)");
                    return;
                }

                // Generate random bytes
                byte[] data = new byte[numBytes];
                random.nextBytes(data);

                // Send response
                exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(numBytes));
                exchange.sendResponseHeaders(200, numBytes);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(data);
                }

            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Bad Request: Invalid byte count");
            }
        }
    }

    /**
     * Handler for /status/{code} endpoint - returns the specified HTTP status code.
     */
    private static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");

            if (parts.length < 3) {
                sendError(exchange, 400, "Bad Request: Missing status code");
                return;
            }

            try {
                int statusCode = Integer.parseInt(parts[2]);
                String message = "Status: " + statusCode;
                byte[] response = message.getBytes();

                exchange.sendResponseHeaders(statusCode, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }

            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Bad Request: Invalid status code");
            }
        }
    }

    /**
     * Handler for /delay/{seconds} endpoint - delays response by specified seconds.
     */
    private static class DelayHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");

            if (parts.length < 3) {
                sendError(exchange, 400, "Bad Request: Missing delay seconds");
                return;
            }

            try {
                int delaySeconds = Integer.parseInt(parts[2]);

                if (delaySeconds < 0 || delaySeconds > 30) { // Max 30 seconds
                    sendError(exchange, 400, "Bad Request: Invalid delay (0-30 seconds)");
                    return;
                }

                // Delay the response
                // NOTE: Thread.sleep is acceptable here because:
                // 1. This is test infrastructure, not production code
                // 2. Runs in HttpServer's own thread pool (not Vert.x event loop)
                // 3. Purpose is to simulate slow network responses for testing
                // (Phase 2 - Dec 2025: Reviewed and approved for test code)
                Thread.sleep(delaySeconds * 1000L);

                String message = "Delayed " + delaySeconds + " seconds";
                byte[] response = message.getBytes();

                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }

            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Bad Request: Invalid delay value");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sendError(exchange, 500, "Internal Server Error: Interrupted");
            }
        }
    }

    /**
     * Sends an error response.
     */
    private static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        byte[] response = message.getBytes();
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
}
