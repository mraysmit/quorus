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

import dev.mars.quorus.simulator.SimulatorTestLoggingExtension;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link InMemoryControllerClientSimulator}.
 */
@ExtendWith(SimulatorTestLoggingExtension.class)
@DisplayName("InMemoryControllerClientSimulator Tests")
class InMemoryControllerClientSimulatorTest {

    private static final Logger log = LoggerFactory.getLogger(InMemoryControllerClientSimulatorTest.class);

    private InMemoryControllerClientSimulator client;

    @BeforeEach
    void setUp() {
        client = new InMemoryControllerClientSimulator();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.shutdown();
        }
    }

    // ==================== HTTP Operations ====================

    @Nested
    @DisplayName("HTTP Operations")
    class HttpOperationsTests {

        @Test
        @DisplayName("Should perform GET request")
        void testGetRequest() throws Exception {
            log.info("Testing GET /health request");
            var response = client.get("/health").get(5, TimeUnit.SECONDS);
            log.info("GET response: status={}, successful={}", 
                response.statusCode(), response.isSuccessful());

            assertThat(response.isSuccessful()).isTrue();
            assertThat(response.statusCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("Should perform GET request with query params")
        void testGetWithQueryParams() throws Exception {
            log.info("Testing GET request with query parameters");
            client.registerHandler("GET", "/search", (req, params) -> {
                String query = req.queryParams().get("q");
                return InMemoryControllerClientSimulator.HttpResponse.ok(
                    Map.of("query", query != null ? query : ""));
            });

            var response = client.get("/search", Map.of("q", "test"))
                .get(5, TimeUnit.SECONDS);
            log.info("Query params GET successful: {}", response.isSuccessful());

            assertThat(response.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("Should perform POST request")
        void testPostRequest() throws Exception {
            log.info("Testing POST /agents/register request");
            var response = client.post("/agents/register", 
                Map.of("agentId", "test-agent"))
                .get(5, TimeUnit.SECONDS);
            log.info("POST response: status={}", response.statusCode());

            assertThat(response.isSuccessful()).isTrue();
            assertThat(response.statusCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("Should perform POST request with headers")
        void testPostWithHeaders() throws Exception {
            log.info("Testing POST request with Authorization header");
            client.registerHandler("POST", "/protected", (req, params) -> {
                String auth = req.headers().get("Authorization");
                if ("Bearer token".equals(auth)) {
                    return InMemoryControllerClientSimulator.HttpResponse.ok(Map.of("success", true));
                }
                return InMemoryControllerClientSimulator.HttpResponse.unauthorized("Missing auth");
            });

            var response = client.post("/protected", Map.of(), 
                Map.of("Authorization", "Bearer token"))
                .get(5, TimeUnit.SECONDS);
            log.info("POST with auth header successful: {}", response.isSuccessful());

            assertThat(response.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("Should perform PUT request")
        void testPutRequest() throws Exception {
            log.info("Testing PUT /agents/{agentId} request");
            client.registerHandler("PUT", "/agents/{agentId}", (req, params) -> 
                InMemoryControllerClientSimulator.HttpResponse.ok(
                    Map.of("agentId", params.get("agentId"), "updated", true)));

            var response = client.put("/agents/agent-1", Map.of("status", "active"))
                .get(5, TimeUnit.SECONDS);
            log.info("PUT response successful: {}", response.isSuccessful());

            assertThat(response.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("Should perform DELETE request")
        void testDeleteRequest() throws Exception {
            log.info("Testing DELETE /agents/{agentId} request");
            client.registerHandler("DELETE", "/agents/{agentId}", (req, params) -> 
                InMemoryControllerClientSimulator.HttpResponse.noContent());

            var response = client.delete("/agents/agent-1").get(5, TimeUnit.SECONDS);
            log.info("DELETE response: status={}", response.statusCode());

            assertThat(response.statusCode()).isEqualTo(204);
        }

        @Test
        @DisplayName("Should perform PATCH request")
        void testPatchRequest() throws Exception {
            log.info("Testing PATCH /agents/{agentId} request");
            client.registerHandler("PATCH", "/agents/{agentId}", (req, params) -> 
                InMemoryControllerClientSimulator.HttpResponse.ok(Map.of("patched", true)));

            var response = client.patch("/agents/agent-1", Map.of("field", "value"))
                .get(5, TimeUnit.SECONDS);
            log.info("PATCH response successful: {}", response.isSuccessful());

            assertThat(response.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("Should return 404 for unknown path")
        void testUnknownPath() throws Exception {
            log.info("Testing 404 response for unknown path");
            var response = client.get("/unknown/path").get(5, TimeUnit.SECONDS);
            log.info("Unknown path response: status={}", response.statusCode());

            assertThat(response.statusCode()).isEqualTo(404);
        }
    }

    // ==================== Handler Registration ====================

    @Nested
    @DisplayName("Handler Registration")
    class HandlerRegistrationTests {

        @Test
        @DisplayName("Should register custom handler")
        void testRegisterHandler() throws Exception {
            log.info("Testing custom handler registration");
            client.registerHandler("GET", "/custom", (req, params) -> 
                InMemoryControllerClientSimulator.HttpResponse.ok(Map.of("custom", true)));

            var response = client.get("/custom").get(5, TimeUnit.SECONDS);
            log.info("Custom handler response: {}", response.isSuccessful());

            assertThat(response.isSuccessful()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.body();
            assertThat(body).containsEntry("custom", true);
        }

        @Test
        @DisplayName("Should support path parameters")
        void testPathParameters() throws Exception {
            log.info("Testing path parameter extraction");
            client.registerHandler("GET", "/users/{userId}/orders/{orderId}", (req, params) -> 
                InMemoryControllerClientSimulator.HttpResponse.ok(Map.of(
                    "userId", params.get("userId"),
                    "orderId", params.get("orderId")
                )));

            var response = client.get("/users/123/orders/456").get(5, TimeUnit.SECONDS);
            log.info("Path parameters extracted successfully");

            assertThat(response.isSuccessful()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.body();
            assertThat(body).containsEntry("userId", "123");
            assertThat(body).containsEntry("orderId", "456");
        }

        @Test
        @DisplayName("Should register simple handler")
        void testRegisterSimpleHandler() throws Exception {
            log.info("Testing simple handler registration");
            client.registerSimpleHandler("GET", "/simple", 200, Map.of("simple", true));

            var response = client.get("/simple").get(5, TimeUnit.SECONDS);
            log.info("Simple handler response: {}", response.isSuccessful());

            assertThat(response.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("Should clear handlers")
        void testClearHandlers() throws Exception {
            log.info("Testing handler clearing");
            client.registerHandler("GET", "/custom", (req, params) -> 
                InMemoryControllerClientSimulator.HttpResponse.ok(Map.of("custom", true)));

            client.clearHandlers();

            var response = client.get("/custom").get(5, TimeUnit.SECONDS);
            log.info("After clearing handlers, /custom returns: {}", response.statusCode());
            assertThat(response.statusCode()).isEqualTo(404);
        }
    }

    // ==================== Default Handlers ====================

    @Nested
    @DisplayName("Default Handlers")
    class DefaultHandlerTests {

        @Test
        @DisplayName("Should handle health check")
        void testHealthCheck() throws Exception {
            log.info("Testing default health check handler");
            var response = client.get("/health").get(5, TimeUnit.SECONDS);

            assertThat(response.isSuccessful()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.body();
            log.info("Health check response: {}", body);
            assertThat(body).containsEntry("status", "UP");
        }

        @Test
        @DisplayName("Should handle agent registration")
        void testAgentRegistration() throws Exception {
            log.info("Testing default agent registration handler");
            var response = client.post("/agents/register", 
                Map.of("agentId", "my-agent"))
                .get(5, TimeUnit.SECONDS);

            assertThat(response.isSuccessful()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.body();
            log.info("Agent registration response: {}", body);
            assertThat(body).containsEntry("registered", true);
        }

        @Test
        @DisplayName("Should handle heartbeat")
        void testHeartbeat() throws Exception {
            log.info("Testing default heartbeat handler");
            var response = client.post("/agents/agent-1/heartbeat", Map.of())
                .get(5, TimeUnit.SECONDS);

            assertThat(response.isSuccessful()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.body();
            log.info("Heartbeat response: {}", body);
            assertThat(body).containsEntry("acknowledged", true);
        }

        @Test
        @DisplayName("Should handle job polling")
        void testJobPolling() throws Exception {
            log.info("Testing default job polling handler");
            var response = client.get("/agents/agent-1/jobs/pending")
                .get(5, TimeUnit.SECONDS);
            log.info("Job polling response: status={}", response.statusCode());

            assertThat(response.isSuccessful()).isTrue();
        }
    }

    // ==================== Latency Simulation ====================

    @Nested
    @DisplayName("Latency Simulation")
    class LatencySimulationTests {

        @Test
        @DisplayName("Should simulate fixed latency")
        void testFixedLatency() throws Exception {
            log.info("Testing fixed latency simulation with 100ms delay");
            client.setDefaultLatencyMs(100);

            long start = System.currentTimeMillis();
            client.get("/health").get(5, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;
            log.info("Fixed latency elapsed time: {}ms (expected >= 100ms)", elapsed);

            assertThat(elapsed).isGreaterThanOrEqualTo(100);
        }

        @Test
        @DisplayName("Should simulate latency range")
        void testLatencyRange() throws Exception {
            log.info("Testing latency range simulation with 50-150ms range");
            client.setLatencyRange(50, 150);

            long start = System.currentTimeMillis();
            client.get("/health").get(5, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;
            log.info("Latency range elapsed time: {}ms (expected >= 50ms)", elapsed);

            assertThat(elapsed).isGreaterThanOrEqualTo(50);
        }
    }

    // ==================== Chaos Engineering ====================

    @Nested
    @DisplayName("Chaos Engineering")
    class ChaosEngineeringTests {

        @ParameterizedTest
        @EnumSource(InMemoryControllerClientSimulator.ClientFailureMode.class)
        @DisplayName("Should support all failure modes")
        void testAllFailureModes(InMemoryControllerClientSimulator.ClientFailureMode mode) {
            log.info("Testing failure mode: {}", mode);
            client.setFailureMode(mode);
            // Just verify setting doesn't throw
        }

        @Test
        @DisplayName("Should simulate connection refused")
        void testConnectionRefused() {
            log.info("Testing CONNECTION_REFUSED chaos mode");
            client.setFailureMode(
                InMemoryControllerClientSimulator.ClientFailureMode.CONNECTION_REFUSED);

            assertThatThrownBy(() -> client.get("/health").get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(InMemoryControllerClientSimulator.ClientException.class)
                .hasMessageContaining("Connection refused");
        }

        @Test
        @DisplayName("Should simulate connection timeout")
        void testConnectionTimeout() {
            log.info("Testing CONNECTION_TIMEOUT chaos mode");
            client.setFailureMode(
                InMemoryControllerClientSimulator.ClientFailureMode.CONNECTION_TIMEOUT);

            assertThatThrownBy(() -> client.get("/health").get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(InMemoryControllerClientSimulator.ClientException.class)
                .hasMessageContaining("timed out");
        }

        @Test
        @DisplayName("Should simulate server error 500")
        void testServerError500() {
            log.info("Testing SERVER_ERROR_500 chaos mode");
            client.setFailureMode(
                InMemoryControllerClientSimulator.ClientFailureMode.SERVER_ERROR_500);

            assertThatThrownBy(() -> client.get("/health").get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("500");
        }

        @Test
        @DisplayName("Should simulate service unavailable 503")
        void testServiceUnavailable503() {
            log.info("Testing SERVICE_UNAVAILABLE_503 chaos mode");
            client.setFailureMode(
                InMemoryControllerClientSimulator.ClientFailureMode.SERVICE_UNAVAILABLE_503);

            assertThatThrownBy(() -> client.get("/health").get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("503");
        }

        @Test
        @DisplayName("Should simulate network unreachable")
        void testNetworkUnreachable() {
            log.info("Testing NETWORK_UNREACHABLE chaos mode");
            client.setFailureMode(
                InMemoryControllerClientSimulator.ClientFailureMode.NETWORK_UNREACHABLE);

            assertThatThrownBy(() -> client.get("/health").get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("Network unreachable");
        }

        @Test
        @DisplayName("Should simulate SSL error")
        void testSslError() {
            log.info("Testing SSL_ERROR chaos mode");
            client.setFailureMode(
                InMemoryControllerClientSimulator.ClientFailureMode.SSL_ERROR);

            assertThatThrownBy(() -> client.get("/health").get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("SSL");
        }

        @Test
        @DisplayName("Should set path-specific failure")
        void testPathSpecificFailure() throws Exception {
            log.info("Testing path-specific failure mode on /failing path");
            client.setPathFailureMode("/failing", 
                InMemoryControllerClientSimulator.ClientFailureMode.SERVER_ERROR_500);
            client.registerSimpleHandler("GET", "/failing", 200, Map.of());
            client.registerSimpleHandler("GET", "/working", 200, Map.of());

            // Working path should succeed
            var response = client.get("/working").get(5, TimeUnit.SECONDS);
            log.info("/working path response: status={}", response.statusCode());
            assertThat(response.isSuccessful()).isTrue();

            // Failing path should fail
            log.info("Verifying /failing path triggers error");
            assertThatThrownBy(() -> client.get("/failing").get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
        }

        @Test
        @DisplayName("Should clear path-specific failure")
        void testClearPathFailure() throws Exception {
            log.info("Testing clearing of path-specific failure mode");
            client.setPathFailureMode("/path", 
                InMemoryControllerClientSimulator.ClientFailureMode.SERVER_ERROR_500);
            client.registerSimpleHandler("GET", "/path", 200, Map.of());

            client.clearPathFailureMode("/path");
            log.info("Path failure mode cleared for /path");

            var response = client.get("/path").get(5, TimeUnit.SECONDS);
            log.info("After clearing: status={}", response.statusCode());
            assertThat(response.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("Should simulate random failures")
        void testRandomFailures() {
            log.info("Testing random failure mode with 100% failure rate");
            client.setFailureRate(1.0); // 100% failure

            assertThatThrownBy(() -> client.get("/health").get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
            log.info("Random failure triggered as expected");
        }

        @Test
        @DisplayName("Should reset chaos settings")
        void testResetChaos() throws Exception {
            log.info("Testing reset of chaos engineering settings");
            client.setFailureMode(
                InMemoryControllerClientSimulator.ClientFailureMode.CONNECTION_REFUSED);
            client.setFailureRate(1.0);

            client.reset();
            log.info("Chaos settings reset");

            var response = client.get("/health").get(5, TimeUnit.SECONDS);
            log.info("After reset: request succeeded with status={}", response.statusCode());
            assertThat(response.isSuccessful()).isTrue();
        }
    }

    // ==================== Request Recording ====================

    @Nested
    @DisplayName("Request Recording")
    class RequestRecordingTests {

        @Test
        @DisplayName("Should record requests")
        void testRecordRequests() throws Exception {
            log.info("Testing request recording for GET /health");
            client.get("/health").get(5, TimeUnit.SECONDS);

            var recorded = client.getRecordedRequests();
            log.info("Recorded {} request(s): method={}, path={}", 
                recorded.size(), recorded.get(0).request().method(), recorded.get(0).request().path());
            assertThat(recorded).hasSize(1);
            assertThat(recorded.get(0).request().method()).isEqualTo("GET");
            assertThat(recorded.get(0).request().path()).isEqualTo("/health");
        }

        @Test
        @DisplayName("Should get requests by path")
        void testGetRequestsByPath() throws Exception {
            log.info("Testing request filtering by path");
            client.get("/health").get(5, TimeUnit.SECONDS);
            client.post("/agents/register", Map.of()).get(5, TimeUnit.SECONDS);

            var healthRequests = client.getRecordedRequests("/health");
            var agentRequests = client.getRecordedRequests("/agents");
            log.info("Path filter results: /health={} requests, /agents={} requests", 
                healthRequests.size(), agentRequests.size());
            assertThat(healthRequests).hasSize(1);
            assertThat(agentRequests).hasSize(1);
        }

        @Test
        @DisplayName("Should get last request")
        void testGetLastRequest() throws Exception {
            log.info("Testing retrieval of last recorded request");
            client.get("/health").get(5, TimeUnit.SECONDS);
            client.get("/agents/agent-1/jobs/pending").get(5, TimeUnit.SECONDS);

            var last = client.getLastRequest();
            log.info("Last request path: {}", last.request().path());
            assertThat(last).isNotNull();
            assertThat(last.request().path()).contains("jobs/pending");
        }

        @Test
        @DisplayName("Should clear recorded requests")
        void testClearRecordedRequests() throws Exception {
            log.info("Testing clearing of recorded requests");
            client.get("/health").get(5, TimeUnit.SECONDS);
            client.clearRecordedRequests();
            log.info("Recorded requests cleared, count={}", client.getRecordedRequests().size());

            assertThat(client.getRecordedRequests()).isEmpty();
        }

        @Test
        @DisplayName("Should await requests")
        void testAwaitRequests() throws Exception {
            log.info("Testing async request awaiting");
            // Start async requests
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(50);
                    client.get("/health").get(5, TimeUnit.SECONDS);
                    client.get("/health").get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // Ignore
                }
            });

            boolean reached = client.awaitRequests(2, Duration.ofSeconds(5));
            log.info("Await result: reached={}, recorded={}", reached, client.getRecordedRequests().size());
            assertThat(reached).isTrue();
            assertThat(client.getRecordedRequests()).hasSize(2);
        }

        @Test
        @DisplayName("Should record request duration")
        void testRecordDuration() throws Exception {
            log.info("Testing request duration recording with 50ms latency");
            client.setDefaultLatencyMs(50);
            client.get("/health").get(5, TimeUnit.SECONDS);

            var recorded = client.getLastRequest();
            log.info("Recorded duration: {}ms (expected >= 50ms)", recorded.getDuration().toMillis());
            assertThat(recorded.getDuration().toMillis()).isGreaterThanOrEqualTo(50);
        }

        @Test
        @DisplayName("Should disable recording")
        void testDisableRecording() throws Exception {
            log.info("Testing recording disable functionality");
            client.setRecordingEnabled(false);
            client.get("/health").get(5, TimeUnit.SECONDS);
            log.info("Recording disabled, recorded count={}", client.getRecordedRequests().size());

            assertThat(client.getRecordedRequests()).isEmpty();
        }

        @Test
        @DisplayName("Should track successful requests")
        void testTrackSuccessfulRecords() throws Exception {
            log.info("Testing successful request tracking");
            client.get("/health").get(5, TimeUnit.SECONDS);

            var recorded = client.getLastRequest();
            log.info("Request successful: {}", recorded.isSuccessful());
            assertThat(recorded.isSuccessful()).isTrue();
        }
    }

    // ==================== Statistics ====================

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("Should track total requests")
        void testTotalRequests() throws Exception {
            log.info("Testing total request tracking");
            client.get("/health").get(5, TimeUnit.SECONDS);
            client.get("/health").get(5, TimeUnit.SECONDS);
            log.info("Total requests tracked: {}", client.getTotalRequests());

            assertThat(client.getTotalRequests()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should track successful requests")
        void testSuccessfulRequests() throws Exception {
            log.info("Testing successful request tracking");
            client.get("/health").get(5, TimeUnit.SECONDS);
            log.info("Successful requests tracked: {}", client.getSuccessfulRequests());

            assertThat(client.getSuccessfulRequests()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should track failed requests")
        void testFailedRequests() throws Exception {
            log.info("Testing failed request tracking");
            client.get("/nonexistent").get(5, TimeUnit.SECONDS); // 404
            log.info("Failed requests tracked: {}", client.getFailedRequests());

            assertThat(client.getFailedRequests()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should reset statistics")
        void testResetStatistics() throws Exception {
            log.info("Testing statistics reset");
            client.get("/health").get(5, TimeUnit.SECONDS);
            client.resetStatistics();
            log.info("After reset: total={}, successful={}", 
                client.getTotalRequests(), client.getSuccessfulRequests());

            assertThat(client.getTotalRequests()).isZero();
            assertThat(client.getSuccessfulRequests()).isZero();
        }
    }

    // ==================== Configuration ====================

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("Should set base URL")
        void testSetBaseUrl() {
            log.info("Testing base URL configuration");
            client.setBaseUrl("http://custom:9000");
            log.info("Base URL set to http://custom:9000");
            // Base URL doesn't affect behavior in simulator
        }

        @Test
        @DisplayName("Should construct with custom base URL")
        void testConstructWithBaseUrl() {
            log.info("Testing constructor with custom base URL");
            var customClient = new InMemoryControllerClientSimulator("http://custom:9000");
            try {
                // Should still work
                customClient.get("/health");
                log.info("Custom client created and functional");
            } finally {
                customClient.shutdown();
            }
        }

        @Test
        @DisplayName("Method chaining should work")
        void testMethodChaining() {
            log.info("Testing fluent method chaining");
            var result = client
                .setBaseUrl("http://test:8080")
                .setDefaultLatencyMs(10)
                .setFailureMode(InMemoryControllerClientSimulator.ClientFailureMode.NONE)
                .setRecordingEnabled(true);
            log.info("Method chaining successful: returns same instance={}", result == client);

            assertThat(result).isSameAs(client);
        }
    }

    // ==================== HTTP Response Helpers ====================

    @Nested
    @DisplayName("HTTP Response Helpers")
    class HttpResponseHelperTests {

        @Test
        @DisplayName("Should create OK response")
        void testOkResponse() {
            log.info("Testing HTTP 200 OK response helper");
            var response = InMemoryControllerClientSimulator.HttpResponse.ok(Map.of("data", "value"));
            log.info("OK response: status={}, successful={}", response.statusCode(), response.isSuccessful());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("Should create created response")
        void testCreatedResponse() {
            log.info("Testing HTTP 201 Created response helper");
            var response = InMemoryControllerClientSimulator.HttpResponse.created(Map.of("id", "123"));
            log.info("Created response: status={}, successful={}", response.statusCode(), response.isSuccessful());

            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(response.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("Should create no content response")
        void testNoContentResponse() {
            log.info("Testing HTTP 204 No Content response helper");
            var response = InMemoryControllerClientSimulator.HttpResponse.noContent();
            log.info("No content response: status={}, body={}", response.statusCode(), response.body());

            assertThat(response.statusCode()).isEqualTo(204);
            assertThat(response.body()).isNull();
        }

        @Test
        @DisplayName("Should create bad request response")
        void testBadRequestResponse() {
            log.info("Testing HTTP 400 Bad Request response helper");
            var response = InMemoryControllerClientSimulator.HttpResponse.badRequest("Invalid input");
            log.info("Bad request response: status={}, successful={}", response.statusCode(), response.isSuccessful());

            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.isSuccessful()).isFalse();
        }

        @Test
        @DisplayName("Should create unauthorized response")
        void testUnauthorizedResponse() {
            log.info("Testing HTTP 401 Unauthorized response helper");
            var response = InMemoryControllerClientSimulator.HttpResponse.unauthorized("No auth");
            log.info("Unauthorized response: status={}", response.statusCode());

            assertThat(response.statusCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("Should create not found response")
        void testNotFoundResponse() {
            log.info("Testing HTTP 404 Not Found response helper");
            var response = InMemoryControllerClientSimulator.HttpResponse.notFound("Resource not found");
            log.info("Not found response: status={}", response.statusCode());

            assertThat(response.statusCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("Should create server error response")
        void testServerErrorResponse() {
            log.info("Testing HTTP 500 Server Error response helper");
            var response = InMemoryControllerClientSimulator.HttpResponse.serverError("Internal error");
            log.info("Server error response: status={}", response.statusCode());

            assertThat(response.statusCode()).isEqualTo(500);
        }
    }
}
