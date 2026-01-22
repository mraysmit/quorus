/*
 * Copyright (c) 2025 Cityline Ltd.
 * All rights reserved.
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

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link InMemoryControllerClientSimulator}.
 */
@DisplayName("InMemoryControllerClientSimulator Tests")
class InMemoryControllerClientSimulatorTest {

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
            var response = client.get("/health").get(5, TimeUnit.SECONDS);

            assertThat(response.isSuccessful()).isTrue();
            assertThat(response.statusCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("Should perform GET request with query params")
        void testGetWithQueryParams() throws Exception {
            client.registerHandler("GET", "/search", (req, params) -> {
                String query = req.queryParams().get("q");
                return InMemoryControllerClientSimulator.HttpResponse.ok(
                    Map.of("query", query != null ? query : ""));
            });

            var response = client.get("/search", Map.of("q", "test"))
                .get(5, TimeUnit.SECONDS);

            assertThat(response.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("Should perform POST request")
        void testPostRequest() throws Exception {
            var response = client.post("/agents/register", 
                Map.of("agentId", "test-agent"))
                .get(5, TimeUnit.SECONDS);

            assertThat(response.isSuccessful()).isTrue();
            assertThat(response.statusCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("Should perform POST request with headers")
        void testPostWithHeaders() throws Exception {
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

            assertThat(response.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("Should perform PUT request")
        void testPutRequest() throws Exception {
            client.registerHandler("PUT", "/agents/{agentId}", (req, params) -> 
                InMemoryControllerClientSimulator.HttpResponse.ok(
                    Map.of("agentId", params.get("agentId"), "updated", true)));

            var response = client.put("/agents/agent-1", Map.of("status", "active"))
                .get(5, TimeUnit.SECONDS);

            assertThat(response.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("Should perform DELETE request")
        void testDeleteRequest() throws Exception {
            client.registerHandler("DELETE", "/agents/{agentId}", (req, params) -> 
                InMemoryControllerClientSimulator.HttpResponse.noContent());

            var response = client.delete("/agents/agent-1").get(5, TimeUnit.SECONDS);

            assertThat(response.statusCode()).isEqualTo(204);
        }

        @Test
        @DisplayName("Should perform PATCH request")
        void testPatchRequest() throws Exception {
            client.registerHandler("PATCH", "/agents/{agentId}", (req, params) -> 
                InMemoryControllerClientSimulator.HttpResponse.ok(Map.of("patched", true)));

            var response = client.patch("/agents/agent-1", Map.of("field", "value"))
                .get(5, TimeUnit.SECONDS);

            assertThat(response.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("Should return 404 for unknown path")
        void testUnknownPath() throws Exception {
            var response = client.get("/unknown/path").get(5, TimeUnit.SECONDS);

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
            client.registerHandler("GET", "/custom", (req, params) -> 
                InMemoryControllerClientSimulator.HttpResponse.ok(Map.of("custom", true)));

            var response = client.get("/custom").get(5, TimeUnit.SECONDS);

            assertThat(response.isSuccessful()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.body();
            assertThat(body).containsEntry("custom", true);
        }

        @Test
        @DisplayName("Should support path parameters")
        void testPathParameters() throws Exception {
            client.registerHandler("GET", "/users/{userId}/orders/{orderId}", (req, params) -> 
                InMemoryControllerClientSimulator.HttpResponse.ok(Map.of(
                    "userId", params.get("userId"),
                    "orderId", params.get("orderId")
                )));

            var response = client.get("/users/123/orders/456").get(5, TimeUnit.SECONDS);

            assertThat(response.isSuccessful()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.body();
            assertThat(body).containsEntry("userId", "123");
            assertThat(body).containsEntry("orderId", "456");
        }

        @Test
        @DisplayName("Should register simple handler")
        void testRegisterSimpleHandler() throws Exception {
            client.registerSimpleHandler("GET", "/simple", 200, Map.of("simple", true));

            var response = client.get("/simple").get(5, TimeUnit.SECONDS);

            assertThat(response.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("Should clear handlers")
        void testClearHandlers() throws Exception {
            client.registerHandler("GET", "/custom", (req, params) -> 
                InMemoryControllerClientSimulator.HttpResponse.ok(Map.of("custom", true)));

            client.clearHandlers();

            var response = client.get("/custom").get(5, TimeUnit.SECONDS);
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
            var response = client.get("/health").get(5, TimeUnit.SECONDS);

            assertThat(response.isSuccessful()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.body();
            assertThat(body).containsEntry("status", "UP");
        }

        @Test
        @DisplayName("Should handle agent registration")
        void testAgentRegistration() throws Exception {
            var response = client.post("/agents/register", 
                Map.of("agentId", "my-agent"))
                .get(5, TimeUnit.SECONDS);

            assertThat(response.isSuccessful()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.body();
            assertThat(body).containsEntry("registered", true);
        }

        @Test
        @DisplayName("Should handle heartbeat")
        void testHeartbeat() throws Exception {
            var response = client.post("/agents/agent-1/heartbeat", Map.of())
                .get(5, TimeUnit.SECONDS);

            assertThat(response.isSuccessful()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.body();
            assertThat(body).containsEntry("acknowledged", true);
        }

        @Test
        @DisplayName("Should handle job polling")
        void testJobPolling() throws Exception {
            var response = client.get("/agents/agent-1/jobs/pending")
                .get(5, TimeUnit.SECONDS);

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
            client.setDefaultLatencyMs(100);

            long start = System.currentTimeMillis();
            client.get("/health").get(5, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed).isGreaterThanOrEqualTo(100);
        }

        @Test
        @DisplayName("Should simulate latency range")
        void testLatencyRange() throws Exception {
            client.setLatencyRange(50, 150);

            long start = System.currentTimeMillis();
            client.get("/health").get(5, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;

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
            client.setFailureMode(mode);
            // Just verify setting doesn't throw
        }

        @Test
        @DisplayName("Should simulate connection refused")
        void testConnectionRefused() {
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
            client.setFailureMode(
                InMemoryControllerClientSimulator.ClientFailureMode.SERVER_ERROR_500);

            assertThatThrownBy(() -> client.get("/health").get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("500");
        }

        @Test
        @DisplayName("Should simulate service unavailable 503")
        void testServiceUnavailable503() {
            client.setFailureMode(
                InMemoryControllerClientSimulator.ClientFailureMode.SERVICE_UNAVAILABLE_503);

            assertThatThrownBy(() -> client.get("/health").get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("503");
        }

        @Test
        @DisplayName("Should simulate network unreachable")
        void testNetworkUnreachable() {
            client.setFailureMode(
                InMemoryControllerClientSimulator.ClientFailureMode.NETWORK_UNREACHABLE);

            assertThatThrownBy(() -> client.get("/health").get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("Network unreachable");
        }

        @Test
        @DisplayName("Should simulate SSL error")
        void testSslError() {
            client.setFailureMode(
                InMemoryControllerClientSimulator.ClientFailureMode.SSL_ERROR);

            assertThatThrownBy(() -> client.get("/health").get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("SSL");
        }

        @Test
        @DisplayName("Should set path-specific failure")
        void testPathSpecificFailure() throws Exception {
            client.setPathFailureMode("/failing", 
                InMemoryControllerClientSimulator.ClientFailureMode.SERVER_ERROR_500);
            client.registerSimpleHandler("GET", "/failing", 200, Map.of());
            client.registerSimpleHandler("GET", "/working", 200, Map.of());

            // Working path should succeed
            var response = client.get("/working").get(5, TimeUnit.SECONDS);
            assertThat(response.isSuccessful()).isTrue();

            // Failing path should fail
            assertThatThrownBy(() -> client.get("/failing").get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
        }

        @Test
        @DisplayName("Should clear path-specific failure")
        void testClearPathFailure() throws Exception {
            client.setPathFailureMode("/path", 
                InMemoryControllerClientSimulator.ClientFailureMode.SERVER_ERROR_500);
            client.registerSimpleHandler("GET", "/path", 200, Map.of());

            client.clearPathFailureMode("/path");

            var response = client.get("/path").get(5, TimeUnit.SECONDS);
            assertThat(response.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("Should simulate random failures")
        void testRandomFailures() {
            client.setFailureRate(1.0); // 100% failure

            assertThatThrownBy(() -> client.get("/health").get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
        }

        @Test
        @DisplayName("Should reset chaos settings")
        void testResetChaos() throws Exception {
            client.setFailureMode(
                InMemoryControllerClientSimulator.ClientFailureMode.CONNECTION_REFUSED);
            client.setFailureRate(1.0);

            client.reset();

            var response = client.get("/health").get(5, TimeUnit.SECONDS);
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
            client.get("/health").get(5, TimeUnit.SECONDS);

            var recorded = client.getRecordedRequests();
            assertThat(recorded).hasSize(1);
            assertThat(recorded.get(0).request().method()).isEqualTo("GET");
            assertThat(recorded.get(0).request().path()).isEqualTo("/health");
        }

        @Test
        @DisplayName("Should get requests by path")
        void testGetRequestsByPath() throws Exception {
            client.get("/health").get(5, TimeUnit.SECONDS);
            client.post("/agents/register", Map.of()).get(5, TimeUnit.SECONDS);

            var healthRequests = client.getRecordedRequests("/health");
            assertThat(healthRequests).hasSize(1);

            var agentRequests = client.getRecordedRequests("/agents");
            assertThat(agentRequests).hasSize(1);
        }

        @Test
        @DisplayName("Should get last request")
        void testGetLastRequest() throws Exception {
            client.get("/health").get(5, TimeUnit.SECONDS);
            client.get("/agents/agent-1/jobs/pending").get(5, TimeUnit.SECONDS);

            var last = client.getLastRequest();
            assertThat(last).isNotNull();
            assertThat(last.request().path()).contains("jobs/pending");
        }

        @Test
        @DisplayName("Should clear recorded requests")
        void testClearRecordedRequests() throws Exception {
            client.get("/health").get(5, TimeUnit.SECONDS);
            client.clearRecordedRequests();

            assertThat(client.getRecordedRequests()).isEmpty();
        }

        @Test
        @DisplayName("Should await requests")
        void testAwaitRequests() throws Exception {
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
            assertThat(reached).isTrue();
            assertThat(client.getRecordedRequests()).hasSize(2);
        }

        @Test
        @DisplayName("Should record request duration")
        void testRecordDuration() throws Exception {
            client.setDefaultLatencyMs(50);
            client.get("/health").get(5, TimeUnit.SECONDS);

            var recorded = client.getLastRequest();
            assertThat(recorded.getDuration().toMillis()).isGreaterThanOrEqualTo(50);
        }

        @Test
        @DisplayName("Should disable recording")
        void testDisableRecording() throws Exception {
            client.setRecordingEnabled(false);
            client.get("/health").get(5, TimeUnit.SECONDS);

            assertThat(client.getRecordedRequests()).isEmpty();
        }

        @Test
        @DisplayName("Should track successful requests")
        void testTrackSuccessfulRecords() throws Exception {
            client.get("/health").get(5, TimeUnit.SECONDS);

            var recorded = client.getLastRequest();
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
            client.get("/health").get(5, TimeUnit.SECONDS);
            client.get("/health").get(5, TimeUnit.SECONDS);

            assertThat(client.getTotalRequests()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should track successful requests")
        void testSuccessfulRequests() throws Exception {
            client.get("/health").get(5, TimeUnit.SECONDS);

            assertThat(client.getSuccessfulRequests()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should track failed requests")
        void testFailedRequests() throws Exception {
            client.get("/nonexistent").get(5, TimeUnit.SECONDS); // 404

            assertThat(client.getFailedRequests()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should reset statistics")
        void testResetStatistics() throws Exception {
            client.get("/health").get(5, TimeUnit.SECONDS);
            client.resetStatistics();

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
            client.setBaseUrl("http://custom:9000");
            // Base URL doesn't affect behavior in simulator
        }

        @Test
        @DisplayName("Should construct with custom base URL")
        void testConstructWithBaseUrl() {
            var customClient = new InMemoryControllerClientSimulator("http://custom:9000");
            try {
                // Should still work
                customClient.get("/health");
            } finally {
                customClient.shutdown();
            }
        }

        @Test
        @DisplayName("Method chaining should work")
        void testMethodChaining() {
            var result = client
                .setBaseUrl("http://test:8080")
                .setDefaultLatencyMs(10)
                .setFailureMode(InMemoryControllerClientSimulator.ClientFailureMode.NONE)
                .setRecordingEnabled(true);

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
            var response = InMemoryControllerClientSimulator.HttpResponse.ok(Map.of("data", "value"));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("Should create created response")
        void testCreatedResponse() {
            var response = InMemoryControllerClientSimulator.HttpResponse.created(Map.of("id", "123"));

            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(response.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("Should create no content response")
        void testNoContentResponse() {
            var response = InMemoryControllerClientSimulator.HttpResponse.noContent();

            assertThat(response.statusCode()).isEqualTo(204);
            assertThat(response.body()).isNull();
        }

        @Test
        @DisplayName("Should create bad request response")
        void testBadRequestResponse() {
            var response = InMemoryControllerClientSimulator.HttpResponse.badRequest("Invalid input");

            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.isSuccessful()).isFalse();
        }

        @Test
        @DisplayName("Should create unauthorized response")
        void testUnauthorizedResponse() {
            var response = InMemoryControllerClientSimulator.HttpResponse.unauthorized("No auth");

            assertThat(response.statusCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("Should create not found response")
        void testNotFoundResponse() {
            var response = InMemoryControllerClientSimulator.HttpResponse.notFound("Resource not found");

            assertThat(response.statusCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("Should create server error response")
        void testServerErrorResponse() {
            var response = InMemoryControllerClientSimulator.HttpResponse.serverError("Internal error");

            assertThat(response.statusCode()).isEqualTo(500);
        }
    }
}
