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

package dev.mars.quorus.agent.integration;

import dev.mars.quorus.agent.observability.AgentMetrics;
import dev.mars.quorus.agent.observability.AgentTelemetryConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agent observability integration test with real OpenTelemetry collector.
 *
 * Validates:
 * - Prometheus endpoint is exposed by agent telemetry config
 * - Agent metrics are exported in Prometheus format
 * - OTLP collector is reachable and healthy while traces are emitted
 */
@ExtendWith(VertxExtension.class)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Agent Telemetry Integration")
class AgentTelemetryIntegrationTest {

    private static final String PROMETHEUS_PORT_PROPERTY = "quorus.agent.telemetry.prometheus.port";
    private static final String OTLP_ENDPOINT_PROPERTY = "quorus.agent.telemetry.otlp.endpoint";

    @Container
    static GenericContainer<?> otelCollector = new GenericContainer<>(
            DockerImageName.parse("otel/opentelemetry-collector-contrib:0.96.0"))
            .withExposedPorts(4317, 13133)
            .withCommand("--config=/etc/otelcol-contrib/config.yaml")
            .withClasspathResourceMapping(
                    "otel-collector-config.yaml",
                    "/etc/otelcol-contrib/config.yaml",
                    BindMode.READ_ONLY)
            .waitingFor(Wait.forHttp("/").forPort(13133).forStatusCode(200));

    private Vertx vertx;
    private HttpClient httpClient;
    private int prometheusPort;

    @BeforeAll
    void setUp() {
        GlobalOpenTelemetry.resetForTest();

        prometheusPort = findAvailablePort();
        String otlpEndpoint = "http://localhost:" + otelCollector.getMappedPort(4317);

        System.setProperty(PROMETHEUS_PORT_PROPERTY, String.valueOf(prometheusPort));
        System.setProperty(OTLP_ENDPOINT_PROPERTY, otlpEndpoint);

        VertxOptions options = AgentTelemetryConfig.configure(new VertxOptions(), "agent-telemetry-test");
        vertx = Vertx.vertx(options);
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        AgentMetrics metrics = new AgentMetrics("agent-telemetry-test", System.currentTimeMillis());
        metrics.setStatusRunning();
        metrics.recordHeartbeat(true);
        metrics.recordRegistration(true);
        metrics.recordJobStarted();
        metrics.recordJobCompleted(false, "https", "upload", 2048, 3);
        metrics.recordForeignAssignmentMismatch("controller-assigned-agent");

        Tracer tracer = GlobalOpenTelemetry.getTracer("quorus-agent-telemetry-test");
        Span span = tracer.spanBuilder("agent.telemetry.integration.startup").startSpan();
        span.end();
    }

    @AfterAll
    void tearDown(VertxTestContext testContext) {
        if (vertx != null) {
            vertx.close().onComplete(testContext.succeeding(v -> {
                cleanupTelemetryState();
                testContext.completeNow();
            }));
            return;
        }

        cleanupTelemetryState();
        testContext.completeNow();
    }

    private void cleanupTelemetryState() {
        if (GlobalOpenTelemetry.get() instanceof OpenTelemetrySdk sdk) {
            sdk.close();
        }
        GlobalOpenTelemetry.resetForTest();

        System.clearProperty(PROMETHEUS_PORT_PROPERTY);
        System.clearProperty(OTLP_ENDPOINT_PROPERTY);
    }

    @Test
    @DisplayName("Prometheus endpoint exports agent metrics")
    void testPrometheusEndpointExportsAgentMetrics() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + prometheusPort + "/metrics"))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "Prometheus endpoint should return HTTP 200");
        String body = response.body();
        assertNotNull(body, "Prometheus response body must not be null");
        assertFalse(body.isBlank(), "Prometheus response body must not be blank");
        assertTrue(body.contains("quorus_agent_"),
                "Prometheus export should include quorus_agent_* metrics");
        assertTrue(body.contains("quorus_agent_assignments_foreign"),
                "Prometheus export should include foreign assignment metric");
    }

    @Test
    @DisplayName("OTel collector health endpoint is reachable")
    void testCollectorHealthEndpoint() throws Exception {
        int collectorHealthPort = otelCollector.getMappedPort(13133);
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + collectorHealthPort + "/"))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "OTel collector health check should return HTTP 200");
    }

    @Test
    @DisplayName("OTel collector receives emitted startup span")
    void testCollectorReceivesEmittedSpan() {
        String spanName = "agent.telemetry.integration.startup";
        Instant deadline = Instant.now().plusSeconds(10);

        while (Instant.now().isBefore(deadline)) {
            String logs = otelCollector.getLogs();
            if (logs.contains(spanName)) {
                return;
            }

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for collector to export span logs", e);
            }
        }

        fail("Collector logs did not contain expected span name: " + spanName);
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to allocate free TCP port", e);
        }
    }
}
