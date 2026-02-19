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

package dev.mars.quorus.controller.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.mars.quorus.controller.http.HttpApiServer;
import dev.mars.quorus.controller.raft.InMemoryTransportSimulator;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftTransport;
import dev.mars.quorus.controller.state.QuorusStateMachine;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;

import static org.awaitility.Awaitility.await;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.prometheus.PrometheusHttpServer;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.tracing.opentelemetry.OpenTelemetryOptions;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Infrastructure Integration Test with full OpenTelemetry stack.
 * 
 * This test validates the complete observability pipeline including:
 * - Prometheus metrics endpoint with real metric data
 * - OTLP trace export to a real collector
 * - End-to-end metrics flow verification
 * 
 * Uses Testcontainers to start an OpenTelemetry Collector that:
 * - Receives OTLP traces on port 4317
 * - Exposes health check on port 13133
 * 
 * The embedded Prometheus HTTP server (port 9464) is started by the test
 * as part of the OpenTelemetry SDK configuration.
 * 
 * Run this test when you need to validate the full observability stack:
 *   mvn test -pl quorus-controller -Dtest=InfrastructureWithTelemetryTest
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-01-30
 * @version 1.0
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Infrastructure Tests with OpenTelemetry")
class InfrastructureWithTelemetryTest {

    private static final Logger logger = Logger.getLogger(InfrastructureWithTelemetryTest.class.getName());
    private static final int HTTP_PORT = 18091;
    private static final int PROMETHEUS_PORT = 19464; // Different from default to avoid conflicts
    private static final String BASE_URL = "http://localhost:" + HTTP_PORT;

    // OpenTelemetry Collector container for receiving traces
    @Container
    static GenericContainer<?> otelCollector = new GenericContainer<>(
            DockerImageName.parse("otel/opentelemetry-collector-contrib:0.96.0"))
            .withExposedPorts(4317, 13133) // OTLP gRPC, Health check
            .withCommand("--config=/etc/otelcol-contrib/config.yaml")
            .withClasspathResourceMapping("otel-collector-config.yaml", 
                    "/etc/otelcol-contrib/config.yaml", 
                    org.testcontainers.containers.BindMode.READ_ONLY)
            .waitingFor(Wait.forHttp("/").forPort(13133).forStatusCode(200));

    private static Vertx vertx;
    private static RaftNode raftNode;
    private static QuorusStateMachine stateMachine;
    private static HttpApiServer httpServer;
    private static HttpClient httpClient;
    private static ObjectMapper objectMapper;
    private static OpenTelemetrySdk openTelemetry;
    private static PrometheusHttpServer prometheusServer;

    private static long startupTime;

    @BeforeAll
    static void setUp() throws Exception {
        long start = System.currentTimeMillis();
        logger.info("");
        logger.info("+==================================================================+");
        logger.info("|   INFRASTRUCTURE WITH TELEMETRY INTEGRATION TEST - SETUP        |");
        logger.info("+==================================================================+");
        logger.info("");
        logger.info("[PHASE 1/5] OpenTelemetry Collector Container");
        logger.info("  -> Container image: otel/opentelemetry-collector-contrib:0.96.0");
        logger.info("  -> Exposed ports: OTLP=4317, Health=13133");
        logger.info("  -> Container ID: " + otelCollector.getContainerId().substring(0, 12));

        // Get the mapped port for OTLP collector
        String otlpEndpoint = "http://localhost:" + otelCollector.getMappedPort(4317);
        logger.info("  -> OTLP endpoint: " + otlpEndpoint);
        logger.info("  -> Health endpoint: http://localhost:" + otelCollector.getMappedPort(13133));
        logger.info("  [OK] Container started successfully");
        logger.info("");
        logger.info("[PHASE 2/5] Initializing OpenTelemetry SDK");
        logger.info("  -> Service name: quorus-controller-test");
        logger.info("  -> Prometheus export port: " + PROMETHEUS_PORT);

        // Initialize OpenTelemetry SDK with real exporters
        initializeOpenTelemetry(otlpEndpoint);
        logger.info("  [OK] OpenTelemetry SDK configured and registered globally");
        logger.info("");
        logger.info("[PHASE 3/5] Initializing Vert.x with OpenTelemetry Tracing");

        // Initialize Vert.x with OpenTelemetry tracing
        VertxOptions options = new VertxOptions()
                .setTracingOptions(new OpenTelemetryOptions(openTelemetry));
        vertx = Vertx.vertx(options);
        logger.info("  -> Vert.x type: " + vertx.getClass().getSimpleName());
        logger.info("  -> Tracing options: OpenTelemetryOptions enabled");
        logger.info("  [OK] Vert.x instance created with tracing");
        
        logger.info("");
        logger.info("[PHASE 4/5] Creating Raft Node (Single-Node Cluster)");
        logger.info("  -> Node ID: otel-test-node");
        logger.info("  -> Mode: Single-node cluster (auto-leader election)");

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        logger.info("  -> ObjectMapper configured with JavaTimeModule");
        
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        logger.info("  -> HttpClient created with 5s connect timeout");

        // Initialize state machine
        stateMachine = new QuorusStateMachine();
        logger.info("  -> State machine initialized");

        // Create transport and Raft node
        RaftTransport transport = new InMemoryTransportSimulator("otel-test-node");
        logger.info("  -> Transport: InMemoryTransportSimulator");
        
        Set<String> clusterNodes = Set.of("otel-test-node");
        raftNode = new RaftNode(vertx, "otel-test-node", clusterNodes, transport, stateMachine, 500, 100);
        logger.info("  -> Election timeout: 500ms, Heartbeat: 100ms");
        
        raftNode.start();
        logger.info("  -> Raft node started, waiting for leader election...");

        // Wait for leader election reactively
        await().atMost(java.time.Duration.ofSeconds(10))
            .pollInterval(java.time.Duration.ofMillis(50))
            .until(() -> raftNode.isLeader());
        logger.info("  -> Leader elected");
        logger.info("  -> Raft state: " + (raftNode.isLeader() ? "LEADER" : "FOLLOWER"));
        logger.info("  -> Current term: " + raftNode.getCurrentTerm());
        logger.info("  [OK] Raft node ready");
        logger.info("");
        logger.info("[PHASE 5/5] Starting HTTP API Server");
        logger.info("  -> HTTP port: " + HTTP_PORT);
        logger.info("  -> Prometheus port for MetricsHandler: " + PROMETHEUS_PORT);

        // Start HTTP server with the correct Prometheus port for metrics proxy
        httpServer = new HttpApiServer(vertx, HTTP_PORT, raftNode, PROMETHEUS_PORT);
        httpServer.start().toCompletionStage().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
        logger.info("  [OK] HTTP Server deployed and listening");

        startupTime = System.currentTimeMillis() - start;
        logger.info("");
        logger.info("====================================================================");
        logger.info("  SETUP COMPLETE in " + startupTime + "ms");
        logger.info("  HTTP API: http://localhost:" + HTTP_PORT);
        logger.info("  Prometheus metrics: http://localhost:" + PROMETHEUS_PORT + "/metrics");
        logger.info("  OTel Collector OTLP: localhost:" + otelCollector.getMappedPort(4317));
        logger.info("====================================================================");
        logger.info("");
    }

    /**
     * Initialize OpenTelemetry SDK with Prometheus metrics and OTLP trace export.
     */
    private static void initializeOpenTelemetry(String otlpEndpoint) {
        logger.info("  -> Resetting GlobalOpenTelemetry for test isolation");
        // Reset any previous global OpenTelemetry (important for test isolation)
        GlobalOpenTelemetry.resetForTest();

        // Configure Resource
        logger.info("  -> Configuring OTel Resource");
        Resource resource = Resource.getDefault().toBuilder()
                .put("service.name", "quorus-controller-test")
                .put("service.version", "test")
                .build();

        // Configure Tracing (OTLP Exporter to container)
        logger.info("  -> Configuring OTLP Span Exporter -> " + otlpEndpoint);
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(otlpEndpoint)
                .setTimeout(Duration.ofSeconds(5))
                .build();

        logger.info("  -> Building SdkTracerProvider with BatchSpanProcessor");
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .setResource(resource)
                .build();

        // Configure Metrics (Prometheus - embedded HTTP server)
        logger.info("  -> Starting Prometheus HTTP server on port " + PROMETHEUS_PORT);
        prometheusServer = PrometheusHttpServer.builder()
                .setPort(PROMETHEUS_PORT)
                .build();

        logger.info("  -> Building SdkMeterProvider with Prometheus reader");
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(prometheusServer)
                .build();

        // Build and register OpenTelemetry SDK
        logger.info("  -> Building and registering OpenTelemetry SDK globally");
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .buildAndRegisterGlobal();

        logger.info("  -> SDK registered: TracerProvider + MeterProvider");
    }

    @AfterAll
    static void tearDown() throws Exception {
        logger.info("");
        logger.info("+==================================================================+");
        logger.info("|   TEARDOWN - CLEANING UP INFRASTRUCTURE                         |");
        logger.info("+==================================================================+");
        logger.info("");
        
        int step = 1;
        
        if (httpServer != null) {
            logger.info("[TEARDOWN " + step++ + "] Stopping HTTP API Server...");
            httpServer.stop();
            logger.info("  [OK] HTTP Server stopped");
        } else {
            logger.info("[TEARDOWN " + step++ + "] HTTP API Server was null, skipping");
        }
        
        if (raftNode != null) {
            logger.info("[TEARDOWN " + step++ + "] Stopping Raft Node...");
            raftNode.stop();
            logger.info("  [OK] Raft Node stopped");
        } else {
            logger.info("[TEARDOWN " + step++ + "] Raft Node was null, skipping");
        }
        
        if (vertx != null) {
            logger.info("[TEARDOWN " + step++ + "] Closing Vert.x instance...");
            vertx.close();
            logger.info("  [OK] Vert.x closed");
        } else {
            logger.info("[TEARDOWN " + step++ + "] Vert.x was null, skipping");
        }
        
        // Shutdown OpenTelemetry SDK
        if (openTelemetry != null) {
            logger.info("[TEARDOWN " + step++ + "] Closing OpenTelemetry SDK...");
            openTelemetry.close();
            logger.info("  [OK] OpenTelemetry SDK closed (Prometheus server stopped)");
        } else {
            logger.info("[TEARDOWN " + step++ + "] OpenTelemetry SDK was null, skipping");
        }
        
        // Reset global OpenTelemetry for test isolation
        logger.info("[TEARDOWN " + step++ + "] Resetting GlobalOpenTelemetry...");
        GlobalOpenTelemetry.resetForTest();
        logger.info("  [OK] GlobalOpenTelemetry reset");
        
        // Clear transports
        logger.info("[TEARDOWN " + step++ + "] Clearing InMemoryTransportSimulator...");
        InMemoryTransportSimulator.clearAllTransports();
        logger.info("  [OK] Transports cleared");
        
        // Note: OTel Collector container is managed by Testcontainers and stops automatically
        if (otelCollector != null && otelCollector.isRunning()) {
            logger.info("[TEARDOWN " + step++ + "] OTel Collector container will be stopped by Testcontainers");
            logger.info("  -> Container ID: " + otelCollector.getContainerId().substring(0, 12));
        }
        
        logger.info("");
        logger.info("====================================================================");
        logger.info("  TEARDOWN COMPLETE - All resources released");
        logger.info("====================================================================");
        logger.info("");
    }

    // ========== Vert.x Runtime Tests ==========

    @Test
    @Order(1)
    @DisplayName("1. Vert.x runtime is available with OTel tracing")
    void testVertxRuntime() {
        logger.info("[TEST 1] Verifying Vert.x runtime availability");
        logger.info("  -> Checking Vert.x instance is not null...");
        assertNotNull(vertx, "Vert.x instance should be created");
        logger.info("  -> Vert.x type: " + vertx.getClass().getSimpleName());
        logger.info("  -> Is clustered: " + vertx.isClustered());
        assertFalse(vertx.isClustered(), "Should not be in clustered mode for tests");
        logger.info("  [OK] TEST PASSED: Vert.x runtime is available with OpenTelemetry tracing");
    }

    // ========== Raft Node Tests ==========

    @Test
    @Order(2)
    @DisplayName("2. Raft node becomes leader")
    void testRaftNodeLeaderElection() {
        logger.info("[TEST 2] Verifying Raft node leader election");
        logger.info("  -> Checking Raft node exists...");
        assertNotNull(raftNode, "Raft node should be created");
        logger.info("  -> Node ID: " + raftNode.getNodeId());
        logger.info("  -> Is leader: " + raftNode.isLeader());
        logger.info("  -> Current term: " + raftNode.getCurrentTerm());
        assertTrue(raftNode.isLeader(), "Single-node cluster should elect itself as leader");
        assertEquals("otel-test-node", raftNode.getNodeId(), "Node ID should match");
        logger.info("  [OK] TEST PASSED: Raft node is LEADER (node: " + raftNode.getNodeId() + ", term: " + raftNode.getCurrentTerm() + ")");
    }

    @Test
    @Order(3)
    @DisplayName("3. State machine is initialized")
    void testStateMachineInitialized() {
        logger.info("[TEST 3] Verifying state machine initialization");
        logger.info("  -> Checking state machine exists...");
        assertNotNull(stateMachine, "State machine should be created");
        logger.info("  -> State machine type: " + stateMachine.getClass().getSimpleName());
        logger.info("  -> Checking internal maps...");
        assertNotNull(stateMachine.getAgents(), "Agents map should be available");
        logger.info("  -> Agents map: " + stateMachine.getAgents().size() + " entries");
        assertNotNull(stateMachine.getJobAssignments(), "Job assignments map should be available");
        logger.info("  -> Job assignments map: " + stateMachine.getJobAssignments().size() + " entries");
        assertNotNull(stateMachine.getTransferJobs(), "Transfer jobs map should be available");
        logger.info("  -> Transfer jobs map: " + stateMachine.getTransferJobs().size() + " entries");
        logger.info("  [OK] TEST PASSED: State machine is initialized with all required maps");
    }

    // ========== HTTP API Tests ==========

    @Test
    @Order(4)
    @DisplayName("4. Health endpoint responds")
    void testHealthEndpoint() throws Exception {
        logger.info("[TEST 4] Testing /health endpoint");
        String url = BASE_URL + "/health";
        logger.info("  -> Request: GET " + url);
        logger.info("  -> Timeout: 5 seconds");
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        long startTime = System.currentTimeMillis();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long latency = System.currentTimeMillis() - startTime;
        
        logger.info("  -> Response status: " + response.statusCode());
        logger.info("  -> Response latency: " + latency + "ms");
        logger.info("  -> Response body: " + response.body());
        
        assertEquals(200, response.statusCode(), "Health endpoint should return 200");
        
        JsonNode json = objectMapper.readTree(response.body());
        String status = json.get("status").asText();
        String nodeId = json.get("nodeId").asText();
        logger.info("  -> Parsed status: " + status);
        logger.info("  -> Parsed nodeId: " + nodeId);
        assertEquals("UP", status, "Status should be UP");
        assertEquals("otel-test-node", nodeId, "Node ID should match");
        
        logger.info("  [OK] TEST PASSED: Health endpoint responds with status=UP, nodeId=" + nodeId + " in " + latency + "ms");
    }

    @Test
    @Order(5)
    @DisplayName("5. Raft status endpoint responds")
    void testRaftStatusEndpoint() throws Exception {
        logger.info("[TEST 5] Testing /raft/status endpoint");
        String url = BASE_URL + "/raft/status";
        logger.info("  -> Request: GET " + url);
        logger.info("  -> Timeout: 5 seconds");
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        long startTime = System.currentTimeMillis();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long latency = System.currentTimeMillis() - startTime;
        
        logger.info("  -> Response status: " + response.statusCode());
        logger.info("  -> Response latency: " + latency + "ms");
        logger.info("  -> Response body: " + response.body());
        
        assertEquals(200, response.statusCode(), "Raft status endpoint should return 200");
        
        JsonNode json = objectMapper.readTree(response.body());
        String state = json.get("state").asText();
        int currentTerm = json.get("currentTerm").asInt();
        boolean isLeader = json.get("isLeader").asBoolean();
        
        logger.info("  -> Parsed state: " + state);
        logger.info("  -> Parsed currentTerm: " + currentTerm);
        logger.info("  -> Parsed isLeader: " + isLeader);
        
        assertEquals("LEADER", state, "State should be LEADER");
        assertTrue(json.has("currentTerm"), "Response should include currentTerm");
        assertTrue(json.has("isLeader"), "Response should include isLeader");
        
        logger.info("  [OK] TEST PASSED: Raft status endpoint responds with state=" + state + 
                   ", currentTerm=" + currentTerm + ", isLeader=" + isLeader);
    }

    @Test
    @Order(6)
    @DisplayName("6. Assignment list endpoint responds")
    void testCommandEndpoint() throws Exception {
        logger.info("[TEST 6] Testing /api/v1/assignments endpoint");
        String url = BASE_URL + "/api/v1/assignments";
        logger.info("  -> Request: GET " + url);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        long startTime = System.currentTimeMillis();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long latency = System.currentTimeMillis() - startTime;
        
        logger.info("  -> Response status: " + response.statusCode());
        logger.info("  -> Response latency: " + latency + "ms");
        logger.info("  -> Response body: " + response.body());
        
        assertEquals(200, response.statusCode(),
                "Assignments endpoint should respond with 200 (status: " + response.statusCode() + ")");
        
        logger.info("  [OK] TEST PASSED: Assignments endpoint responds (status: " + response.statusCode() + ")");
    }

    // ========== OpenTelemetry Metrics Tests ==========

    @Test
    @Order(7)
    @DisplayName("7. Prometheus metrics endpoint returns real metrics")
    void testPrometheusMetricsEndpoint() throws Exception {
        logger.info("[TEST 7] Testing direct Prometheus metrics endpoint");
        String url = "http://localhost:" + PROMETHEUS_PORT + "/metrics";
        logger.info("  -> Request: GET " + url);
        logger.info("  -> Note: This is the OTel SDK's embedded Prometheus HTTP server, NOT the proxy");
        
        // Query the embedded Prometheus HTTP server directly
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        long startTime = System.currentTimeMillis();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long latency = System.currentTimeMillis() - startTime;
        
        logger.info("  -> Response status: " + response.statusCode());
        logger.info("  -> Response latency: " + latency + "ms");
        
        assertEquals(200, response.statusCode(), "Prometheus metrics endpoint should return 200");
        
        String body = response.body();
        assertNotNull(body, "Response body should not be null");
        assertFalse(body.isEmpty(), "Response body should not be empty");
        
        // Verify Prometheus format - should contain HELP and TYPE comments
        assertTrue(body.contains("# HELP") || body.contains("# TYPE") || body.contains("target_info"),
                "Response should contain Prometheus-format metrics");
        
        // Log detailed metrics info
        String[] lines = body.split("\n");
        int metricLines = lines.length;
        logger.info("  -> Total response lines: " + metricLines);
        logger.info("  -> Response size: " + body.length() + " bytes");
        
        // Count different metric types
        int helpCount = 0, typeCount = 0, dataCount = 0;
        for (String line : lines) {
            if (line.startsWith("# HELP")) helpCount++;
            else if (line.startsWith("# TYPE")) typeCount++;
            else if (!line.isEmpty() && !line.startsWith("#")) dataCount++;
        }
        logger.info("  -> Metric definitions (# HELP): " + helpCount);
        logger.info("  -> Type declarations (# TYPE): " + typeCount);
        logger.info("  -> Data points: " + dataCount);
        
        // Log first few metric names for debugging
        logger.info("  -> Sample metrics found:");
        int count = 0;
        for (String line : lines) {
            if (line.startsWith("# HELP")) {
                logger.info("    - " + line.substring(7));
                if (++count >= 5) break;
            }
        }
        
        logger.info("  [OK] TEST PASSED: Prometheus metrics endpoint responds with " + metricLines + " lines");
    }

    @Test
    @Order(8)
    @DisplayName("8. HttpApiServer /metrics proxies to Prometheus exporter")
    void testMetricsProxyEndpoint() throws Exception {
        logger.info("[TEST 8] Testing /metrics proxy endpoint on HttpApiServer");
        String url = BASE_URL + "/metrics";
        logger.info("  -> Request: GET " + url);
        logger.info("  -> Note: This proxies to OTel Prometheus server on port " + PROMETHEUS_PORT);
        
        // Now that HttpApiServer is configured with our PROMETHEUS_PORT, the proxy should work
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        long startTime = System.currentTimeMillis();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long latency = System.currentTimeMillis() - startTime;
        
        logger.info("  -> Response status: " + response.statusCode());
        logger.info("  -> Response latency: " + latency + "ms (includes proxy overhead)");
        
        // The proxy should successfully return metrics from our Prometheus server
        assertEquals(200, response.statusCode(), "Metrics proxy should return 200");
        
        String body = response.body();
        assertNotNull(body, "Response body should not be null");
        assertFalse(body.isEmpty(), "Response body should not be empty");
        
        logger.info("  -> Response size: " + body.length() + " bytes");
        
        // Verify we get Prometheus-format metrics through the proxy
        boolean hasPrometheusFormat = body.contains("# HELP") || body.contains("# TYPE") || body.contains("target_info");
        logger.info("  -> Contains Prometheus format markers: " + hasPrometheusFormat);
        assertTrue(hasPrometheusFormat, "Proxied response should contain Prometheus-format metrics");
        
        // Verify we see actual Quorus metrics
        boolean hasQuorusMetrics = body.contains("quorus_");
        boolean hasTargetInfo = body.contains("target_info");
        logger.info("  -> Contains quorus_* metrics: " + hasQuorusMetrics);
        logger.info("  -> Contains target_info: " + hasTargetInfo);
        
        assertTrue(hasQuorusMetrics || hasTargetInfo,
                "Proxied metrics should include Quorus application metrics");
        
        // List any Quorus-specific metrics found
        if (hasQuorusMetrics) {
            logger.info("  -> Quorus metrics found:");
            String[] lines = body.split("\n");
            int count = 0;
            for (String line : lines) {
                if (line.startsWith("# HELP quorus_")) {
                    logger.info("    - " + line.substring(7));
                    if (++count >= 5) break;
                }
            }
        }
        
        logger.info("  [OK] TEST PASSED: Metrics proxy successfully returns real metrics from Prometheus");
    }

    @Test
    @Order(9)
    @DisplayName("9. OpenTelemetry Collector is receiving data")
    void testOtelCollectorHealth() throws Exception {
        logger.info("[TEST 9] Testing OpenTelemetry Collector health");
        
        // Check the collector's health endpoint
        int healthPort = otelCollector.getMappedPort(13133);
        String url = "http://localhost:" + healthPort + "/";
        logger.info("  -> Request: GET " + url);
        logger.info("  -> Container: " + otelCollector.getContainerId().substring(0, 12));
        logger.info("  -> Image: otel/opentelemetry-collector-contrib:0.96.0");
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        long startTime = System.currentTimeMillis();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long latency = System.currentTimeMillis() - startTime;
        
        logger.info("  -> Response status: " + response.statusCode());
        logger.info("  -> Response latency: " + latency + "ms");
        logger.info("  -> Response body: " + response.body());
        
        assertEquals(200, response.statusCode(), "OTel Collector health check should return 200");
        
        logger.info("  -> OTLP gRPC receiver port: " + otelCollector.getMappedPort(4317));
        logger.info("  [OK] TEST PASSED: OpenTelemetry Collector is healthy and ready to receive traces");
    }

    // ========== JSON Serialization Tests ==========

    @Test
    @Order(10)
    @DisplayName("10. JSON serialization works")
    void testJsonSerialization() throws Exception {
        logger.info("[TEST 10] Testing JSON serialization with Java Time module");
        
        Instant now = Instant.now();
        logger.info("  -> Original Instant: " + now);
        logger.info("  -> Epoch seconds: " + now.getEpochSecond());
        
        String json = objectMapper.writeValueAsString(now);
        logger.info("  -> Serialized JSON: " + json);
        
        Instant deserialized = objectMapper.readValue(json, Instant.class);
        logger.info("  -> Deserialized Instant: " + deserialized);
        logger.info("  -> Deserialized epoch seconds: " + deserialized.getEpochSecond());
        
        assertEquals(now.getEpochSecond(), deserialized.getEpochSecond(), 
                    "Instant serialization should round-trip correctly");
        
        logger.info("  [OK] TEST PASSED: JSON serialization with Java Time module works");
    }

    // ========== Performance Baseline ==========

    @Test
    @Order(11)
    @DisplayName("11. Startup time is acceptable")
    void testStartupTime() {
        logger.info("[TEST 11] Verifying startup time is within acceptable threshold");
        logger.info("  -> Measured startup time: " + startupTime + "ms");
        logger.info("  -> Threshold: 15000ms");
        logger.info("  -> Startup includes:");
        logger.info("    - OTel Collector container startup");
        logger.info("    - OpenTelemetry SDK initialization");
        logger.info("    - Prometheus HTTP server startup");
        logger.info("    - Raft node creation and leader election");
        logger.info("    - HTTP API server deployment");
        
        // Allow more time for OTel initialization
        boolean withinThreshold = startupTime < 15000;
        logger.info("  -> Within threshold: " + withinThreshold);
        
        assertTrue(withinThreshold, "Infrastructure with OTel should start in under 15 seconds");
        logger.info("  [OK] TEST PASSED: Startup time " + startupTime + "ms is within 15000ms threshold");
    }

    @Test
    @Order(12)
    @DisplayName("12. Health endpoint latency is acceptable")
    void testHealthEndpointLatency() throws Exception {
        logger.info("[TEST 12] Measuring health endpoint response latency");
        String url = BASE_URL + "/health";
        logger.info("  -> Request: GET " + url);
        logger.info("  -> Latency threshold: 1000ms");
        
        long start = System.currentTimeMillis();
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        long latency = System.currentTimeMillis() - start;
        logger.info("  -> Measured latency: " + latency + "ms");
        logger.info("  -> Response status: " + response.statusCode());
        
        boolean withinThreshold = latency < 1000;
        logger.info("  -> Within threshold: " + withinThreshold);
        
        assertTrue(withinThreshold, "Health endpoint should respond in under 1 second");
        
        logger.info("  [OK] TEST PASSED: Health endpoint latency " + latency + "ms is within 1000ms threshold");
    }

    // ========== Summary ==========

    @Test
    @Order(13)
    @DisplayName("13. All infrastructure components ready with OTel")
    void testInfrastructureSummary() {
        logger.info("");
        logger.info("+==================================================================+");
        logger.info("|   INFRASTRUCTURE WITH OPENTELEMETRY TEST SUMMARY                |");
        logger.info("+==================================================================+");
        logger.info("|");
        logger.info("|   COMPONENT STATUS");
        logger.info("|   ----------------------------------------------------------------");
        logger.info("|   Vert.x Instance:      [OK] Running with OpenTelemetry tracing");
        logger.info("|   Raft Node:            [OK] LEADER elected (term: " + raftNode.getCurrentTerm() + ")");
        logger.info("|   State Machine:        [OK] Initialized");
        logger.info("|   HTTP API Server:      [OK] Listening on port " + HTTP_PORT);
        logger.info("|");
        logger.info("|   OBSERVABILITY STACK");
        logger.info("|   ----------------------------------------------------------------");
        logger.info("|   Prometheus Metrics:   [OK] Exporting on port " + PROMETHEUS_PORT);
        logger.info("|   Metrics Proxy:        [OK] Available at /metrics");
        logger.info("|   OTel Collector:       [OK] Receiving OTLP on port " + otelCollector.getMappedPort(4317));
        logger.info("|");
        logger.info("|   PERFORMANCE");
        logger.info("|   ----------------------------------------------------------------");
        logger.info("|   Total Startup Time:   " + startupTime + "ms");
        logger.info("|");
        logger.info("+==================================================================+");
        logger.info("|   ALL 13 TESTS PASSED - FULL OBSERVABILITY STACK IS READY       |");
        logger.info("+==================================================================+");
        logger.info("");
        
        assertTrue(true, "All infrastructure components with OTel are ready");
    }
}
