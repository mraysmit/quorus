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

package dev.mars.quorus.simulator.client;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

/**
 * In-memory simulator for controller HTTP client operations.
 * 
 * <p>This simulator replaces real HTTP communication between agents and controllers,
 * enabling testing of agent services without network dependencies. It supports:
 * <ul>
 *   <li>Request/response routing for API endpoints</li>
 *   <li>Latency simulation</li>
 *   <li>Failure injection (timeouts, connection errors, HTTP errors)</li>
 *   <li>Request interception and recording</li>
 * </ul>
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * InMemoryControllerClientSimulator client = new InMemoryControllerClientSimulator();
 * 
 * // Register endpoint handler
 * client.registerHandler("POST", "/agents/register", (request) -> {
 *     return HttpResponse.ok(Map.of("agentId", request.body().get("agentId")));
 * });
 * 
 * // Make simulated request
 * HttpResponse response = client.post("/agents/register", body).join();
 * }</pre>
 * 
 * @author Mark Andrew Ray-Smith
 * @since 1.0
 */
public class InMemoryControllerClientSimulator {

    // Configuration
    private String baseUrl = "http://localhost:8080";
    private long defaultLatencyMs = 0;
    private long maxLatencyMs = 0;
    
    // Endpoint handlers
    private final Map<String, BiFunction<HttpRequest, Map<String, String>, HttpResponse>> handlers 
        = new ConcurrentHashMap<>();
    
    // Request recording
    private final List<RecordedRequest> recordedRequests = Collections.synchronizedList(new ArrayList<>());
    private boolean recordingEnabled = true;
    
    // Chaos engineering
    private ClientFailureMode failureMode = ClientFailureMode.NONE;
    private double failureRate = 0.0;
    private final Map<String, ClientFailureMode> pathFailures = new ConcurrentHashMap<>();
    
    // Statistics
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    
    // Executor for async operations
    private final ScheduledExecutorService scheduler;

    /**
     * Failure modes for chaos engineering.
     */
    public enum ClientFailureMode {
        /** Normal operation */
        NONE,
        /** Connection refused */
        CONNECTION_REFUSED,
        /** Connection timeout */
        CONNECTION_TIMEOUT,
        /** Request timeout */
        REQUEST_TIMEOUT,
        /** Server error (500) */
        SERVER_ERROR_500,
        /** Service unavailable (503) */
        SERVICE_UNAVAILABLE_503,
        /** Bad gateway (502) */
        BAD_GATEWAY_502,
        /** Network unreachable */
        NETWORK_UNREACHABLE,
        /** SSL/TLS error */
        SSL_ERROR,
        /** Random failures */
        RANDOM_FAILURE
    }

    /**
     * Creates a new controller client simulator.
     */
    public InMemoryControllerClientSimulator() {
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "controller-client-simulator");
            t.setDaemon(true);
            return t;
        });
        
        // Register default handlers
        registerDefaultHandlers();
    }

    /**
     * Creates a new controller client simulator with a custom base URL.
     *
     * @param baseUrl the base URL for the controller
     */
    public InMemoryControllerClientSimulator(String baseUrl) {
        this();
        this.baseUrl = baseUrl;
    }

    // ==================== HTTP Operations ====================

    /**
     * Performs a GET request.
     *
     * @param path the request path
     * @return a future containing the response
     */
    public CompletableFuture<HttpResponse> get(String path) {
        return get(path, Map.of());
    }

    /**
     * Performs a GET request with query parameters.
     *
     * @param path the request path
     * @param queryParams query parameters
     * @return a future containing the response
     */
    public CompletableFuture<HttpResponse> get(String path, Map<String, String> queryParams) {
        return executeRequest(new HttpRequest("GET", path, null, Map.of(), queryParams));
    }

    /**
     * Performs a POST request.
     *
     * @param path the request path
     * @param body the request body
     * @return a future containing the response
     */
    public CompletableFuture<HttpResponse> post(String path, Object body) {
        return post(path, body, Map.of());
    }

    /**
     * Performs a POST request with headers.
     *
     * @param path the request path
     * @param body the request body
     * @param headers request headers
     * @return a future containing the response
     */
    public CompletableFuture<HttpResponse> post(String path, Object body, Map<String, String> headers) {
        return executeRequest(new HttpRequest("POST", path, body, headers, Map.of()));
    }

    /**
     * Performs a PUT request.
     *
     * @param path the request path
     * @param body the request body
     * @return a future containing the response
     */
    public CompletableFuture<HttpResponse> put(String path, Object body) {
        return executeRequest(new HttpRequest("PUT", path, body, Map.of(), Map.of()));
    }

    /**
     * Performs a DELETE request.
     *
     * @param path the request path
     * @return a future containing the response
     */
    public CompletableFuture<HttpResponse> delete(String path) {
        return executeRequest(new HttpRequest("DELETE", path, null, Map.of(), Map.of()));
    }

    /**
     * Performs a PATCH request.
     *
     * @param path the request path
     * @param body the request body
     * @return a future containing the response
     */
    public CompletableFuture<HttpResponse> patch(String path, Object body) {
        return executeRequest(new HttpRequest("PATCH", path, body, Map.of(), Map.of()));
    }

    // ==================== Handler Registration ====================

    /**
     * Registers a request handler for a specific endpoint.
     *
     * @param method HTTP method (GET, POST, etc.)
     * @param path the path pattern
     * @param handler the handler function
     * @return this simulator for chaining
     */
    public InMemoryControllerClientSimulator registerHandler(
            String method, 
            String path, 
            BiFunction<HttpRequest, Map<String, String>, HttpResponse> handler) {
        String key = method.toUpperCase() + ":" + normalizePath(path);
        handlers.put(key, handler);
        return this;
    }

    /**
     * Registers a simple handler that returns a fixed response.
     *
     * @param method HTTP method
     * @param path the path pattern
     * @param statusCode response status code
     * @param body response body
     * @return this simulator for chaining
     */
    public InMemoryControllerClientSimulator registerSimpleHandler(
            String method, String path, int statusCode, Object body) {
        return registerHandler(method, path, (req, params) -> 
            new HttpResponse(statusCode, body, Map.of()));
    }

    /**
     * Clears all registered handlers.
     *
     * @return this simulator for chaining
     */
    public InMemoryControllerClientSimulator clearHandlers() {
        handlers.clear();
        registerDefaultHandlers();
        return this;
    }

    // ==================== Configuration ====================

    /**
     * Sets the base URL.
     *
     * @param baseUrl the base URL
     * @return this simulator for chaining
     */
    public InMemoryControllerClientSimulator setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    /**
     * Sets the default response latency.
     *
     * @param latencyMs latency in milliseconds
     * @return this simulator for chaining
     */
    public InMemoryControllerClientSimulator setDefaultLatencyMs(long latencyMs) {
        this.defaultLatencyMs = latencyMs;
        return this;
    }

    /**
     * Sets the latency range for random latency simulation.
     *
     * @param minMs minimum latency
     * @param maxMs maximum latency
     * @return this simulator for chaining
     */
    public InMemoryControllerClientSimulator setLatencyRange(long minMs, long maxMs) {
        this.defaultLatencyMs = minMs;
        this.maxLatencyMs = maxMs;
        return this;
    }

    /**
     * Sets the global failure mode.
     *
     * @param mode the failure mode
     * @return this simulator for chaining
     */
    public InMemoryControllerClientSimulator setFailureMode(ClientFailureMode mode) {
        this.failureMode = mode;
        return this;
    }

    /**
     * Sets a failure mode for a specific path.
     *
     * @param path the path
     * @param mode the failure mode
     * @return this simulator for chaining
     */
    public InMemoryControllerClientSimulator setPathFailureMode(String path, ClientFailureMode mode) {
        pathFailures.put(normalizePath(path), mode);
        return this;
    }

    /**
     * Clears failure mode for a specific path.
     *
     * @param path the path
     * @return this simulator for chaining
     */
    public InMemoryControllerClientSimulator clearPathFailureMode(String path) {
        pathFailures.remove(normalizePath(path));
        return this;
    }

    /**
     * Sets the random failure rate.
     *
     * @param rate failure rate (0.0 to 1.0)
     * @return this simulator for chaining
     */
    public InMemoryControllerClientSimulator setFailureRate(double rate) {
        this.failureRate = Math.max(0.0, Math.min(1.0, rate));
        return this;
    }

    /**
     * Enables or disables request recording.
     *
     * @param enabled true to enable recording
     * @return this simulator for chaining
     */
    public InMemoryControllerClientSimulator setRecordingEnabled(boolean enabled) {
        this.recordingEnabled = enabled;
        return this;
    }

    /**
     * Resets all chaos engineering settings.
     *
     * @return this simulator for chaining
     */
    public InMemoryControllerClientSimulator reset() {
        this.failureMode = ClientFailureMode.NONE;
        this.failureRate = 0.0;
        pathFailures.clear();
        return this;
    }

    // ==================== Request Recording ====================

    /**
     * Gets all recorded requests.
     *
     * @return list of recorded requests
     */
    public List<RecordedRequest> getRecordedRequests() {
        return new ArrayList<>(recordedRequests);
    }

    /**
     * Gets recorded requests for a specific path.
     *
     * @param path the path to filter by
     * @return matching recorded requests
     */
    public List<RecordedRequest> getRecordedRequests(String path) {
        String normalizedPath = normalizePath(path);
        return recordedRequests.stream()
            .filter(r -> r.request().path().startsWith(normalizedPath))
            .toList();
    }

    /**
     * Gets the last recorded request.
     *
     * @return the last request, or null if none
     */
    public RecordedRequest getLastRequest() {
        if (recordedRequests.isEmpty()) {
            return null;
        }
        return recordedRequests.get(recordedRequests.size() - 1);
    }

    /**
     * Clears all recorded requests.
     *
     * @return this simulator for chaining
     */
    public InMemoryControllerClientSimulator clearRecordedRequests() {
        recordedRequests.clear();
        return this;
    }

    /**
     * Waits for a specific number of requests to be recorded.
     *
     * @param count expected request count
     * @param timeout maximum wait time
     * @return true if the count was reached
     */
    public boolean awaitRequests(int count, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (recordedRequests.size() >= count) {
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return recordedRequests.size() >= count;
    }

    // ==================== Statistics ====================

    /**
     * Gets the total request count.
     *
     * @return total requests
     */
    public long getTotalRequests() {
        return totalRequests.get();
    }

    /**
     * Gets the successful request count.
     *
     * @return successful requests
     */
    public long getSuccessfulRequests() {
        return successfulRequests.get();
    }

    /**
     * Gets the failed request count.
     *
     * @return failed requests
     */
    public long getFailedRequests() {
        return failedRequests.get();
    }

    /**
     * Resets statistics.
     */
    public void resetStatistics() {
        totalRequests.set(0);
        successfulRequests.set(0);
        failedRequests.set(0);
    }

    // ==================== Lifecycle ====================

    /**
     * Shuts down the simulator.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ==================== Private Implementation ====================

    private CompletableFuture<HttpResponse> executeRequest(HttpRequest request) {
        totalRequests.incrementAndGet();
        Instant startTime = Instant.now();
        
        return CompletableFuture.supplyAsync(() -> {
            // Simulate latency
            simulateLatency();
            
            // Check for failures
            try {
                checkForFailures(request.path());
            } catch (ClientException e) {
                failedRequests.incrementAndGet();
                recordRequest(request, null, startTime, e.getMessage());
                throw new CompletionException(e);
            }
            
            // Find handler
            HttpResponse response = routeRequest(request);
            
            // Record
            recordRequest(request, response, startTime, null);
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                successfulRequests.incrementAndGet();
            } else {
                failedRequests.incrementAndGet();
            }
            
            return response;
        }, scheduler);
    }

    private void simulateLatency() {
        long latency = defaultLatencyMs;
        if (maxLatencyMs > defaultLatencyMs) {
            latency = defaultLatencyMs + 
                ThreadLocalRandom.current().nextLong(maxLatencyMs - defaultLatencyMs + 1);
        }
        if (latency > 0) {
            try {
                Thread.sleep(latency);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void checkForFailures(String path) throws ClientException {
        // Check path-specific failure
        ClientFailureMode pathMode = pathFailures.get(normalizePath(path));
        if (pathMode != null && pathMode != ClientFailureMode.NONE) {
            throwFailure(pathMode);
        }
        
        // Check global failure
        if (failureMode != ClientFailureMode.NONE) {
            throwFailure(failureMode);
        }
        
        // Check random failure
        if (failureRate > 0 && ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new ClientException("Random connection failure");
        }
    }

    private void throwFailure(ClientFailureMode mode) throws ClientException {
        switch (mode) {
            case CONNECTION_REFUSED -> throw new ClientException("Connection refused");
            case CONNECTION_TIMEOUT -> throw new ClientException("Connection timed out");
            case REQUEST_TIMEOUT -> throw new ClientException("Request timed out");
            case SERVER_ERROR_500 -> throw new ClientException("Internal server error (500)");
            case SERVICE_UNAVAILABLE_503 -> throw new ClientException("Service unavailable (503)");
            case BAD_GATEWAY_502 -> throw new ClientException("Bad gateway (502)");
            case NETWORK_UNREACHABLE -> throw new ClientException("Network unreachable");
            case SSL_ERROR -> throw new ClientException("SSL/TLS handshake failed");
            case RANDOM_FAILURE -> {
                if (ThreadLocalRandom.current().nextBoolean()) {
                    throw new ClientException("Random failure");
                }
            }
            default -> {}
        }
    }

    private HttpResponse routeRequest(HttpRequest request) {
        String normalizedPath = normalizePath(request.path());
        String key = request.method().toUpperCase() + ":" + normalizedPath;
        
        // Try exact match
        BiFunction<HttpRequest, Map<String, String>, HttpResponse> handler = handlers.get(key);
        
        // Try pattern matching
        if (handler == null) {
            for (Map.Entry<String, BiFunction<HttpRequest, Map<String, String>, HttpResponse>> entry 
                    : handlers.entrySet()) {
                String pattern = entry.getKey();
                if (pattern.startsWith(request.method().toUpperCase() + ":")) {
                    String pathPattern = pattern.substring(pattern.indexOf(':') + 1);
                    Map<String, String> pathParams = matchPath(pathPattern, normalizedPath);
                    if (pathParams != null) {
                        handler = entry.getValue();
                        return handler.apply(request, pathParams);
                    }
                }
            }
        }
        
        if (handler != null) {
            return handler.apply(request, Map.of());
        }
        
        // No handler found
        return new HttpResponse(404, Map.of("error", "Not found: " + request.path()), Map.of());
    }

    private Map<String, String> matchPath(String pattern, String path) {
        // Simple path parameter matching (e.g., /agents/{agentId}/status)
        String[] patternParts = pattern.split("/");
        String[] pathParts = path.split("/");
        
        if (patternParts.length != pathParts.length) {
            return null;
        }
        
        Map<String, String> params = new HashMap<>();
        
        for (int i = 0; i < patternParts.length; i++) {
            String patternPart = patternParts[i];
            String pathPart = pathParts[i];
            
            if (patternPart.startsWith("{") && patternPart.endsWith("}")) {
                // Path parameter
                String paramName = patternPart.substring(1, patternPart.length() - 1);
                params.put(paramName, pathPart);
            } else if (!patternPart.equals(pathPart)) {
                return null;
            }
        }
        
        return params;
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        // Remove query string
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        // Remove trailing slash
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private void recordRequest(HttpRequest request, HttpResponse response, 
            Instant startTime, String error) {
        if (recordingEnabled) {
            recordedRequests.add(new RecordedRequest(
                request,
                response,
                startTime,
                Instant.now(),
                error
            ));
        }
    }

    private void registerDefaultHandlers() {
        // Health check
        registerHandler("GET", "/health", (req, params) -> 
            HttpResponse.ok(Map.of("status", "UP")));
        
        // Agent registration
        registerHandler("POST", "/agents/register", (req, params) -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) req.body();
            return HttpResponse.ok(Map.of(
                "agentId", body.getOrDefault("agentId", "agent-" + System.nanoTime()),
                "registered", true
            ));
        });
        
        // Heartbeat
        registerHandler("POST", "/agents/{agentId}/heartbeat", (req, params) -> 
            HttpResponse.ok(Map.of("acknowledged", true)));
        
        // Job polling
        registerHandler("GET", "/agents/{agentId}/jobs/pending", (req, params) -> 
            HttpResponse.ok(List.of()));
        
        // Job status update
        registerHandler("POST", "/agents/{agentId}/jobs/{jobId}/status", (req, params) -> 
            HttpResponse.ok(Map.of("accepted", true)));
    }

    // ==================== Inner Classes ====================

    /**
     * HTTP request.
     */
    public record HttpRequest(
        String method,
        String path,
        Object body,
        Map<String, String> headers,
        Map<String, String> queryParams
    ) {}

    /**
     * HTTP response.
     */
    public record HttpResponse(
        int statusCode,
        Object body,
        Map<String, String> headers
    ) {
        public boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }
        
        public static HttpResponse ok(Object body) {
            return new HttpResponse(200, body, Map.of());
        }
        
        public static HttpResponse created(Object body) {
            return new HttpResponse(201, body, Map.of());
        }
        
        public static HttpResponse noContent() {
            return new HttpResponse(204, null, Map.of());
        }
        
        public static HttpResponse badRequest(String error) {
            return new HttpResponse(400, Map.of("error", error), Map.of());
        }
        
        public static HttpResponse unauthorized(String error) {
            return new HttpResponse(401, Map.of("error", error), Map.of());
        }
        
        public static HttpResponse notFound(String error) {
            return new HttpResponse(404, Map.of("error", error), Map.of());
        }
        
        public static HttpResponse serverError(String error) {
            return new HttpResponse(500, Map.of("error", error), Map.of());
        }
    }

    /**
     * Recorded request for testing verification.
     */
    public record RecordedRequest(
        HttpRequest request,
        HttpResponse response,
        Instant requestTime,
        Instant responseTime,
        String error
    ) {
        public Duration getDuration() {
            return Duration.between(requestTime, responseTime);
        }
        
        public boolean isSuccessful() {
            return error == null && response != null && response.isSuccessful();
        }
    }

    /**
     * Client exception for simulated failures.
     */
    public static class ClientException extends Exception {
        public ClientException(String message) {
            super(message);
        }
    }
}
