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

package dev.mars.quorus.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.agent.testing.FakeController;
import dev.mars.quorus.agent.testing.FakeController.Reply;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for QuorusAgent against a real HTTP controller stand-in (no mocks). The agent is
 * driven only through its controller and its health port.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2025-12-16
 */
@Timeout(value = 60, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class QuorusAgentTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final String REGISTER = "/api/v1/agents/register";
    private static final String DEREGISTER = "/api/v1/agents/test-agent-001";
    private static final String HEARTBEAT = "/api/v1/agents/heartbeat";
    private static final String JOBS = "/api/v1/agents/test-agent-001/jobs";
    private static final String STATUS = "/api/v1/jobs/.+/status";
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path root;
    private FakeController controller;
    private QuorusAgent agent;

    @BeforeEach
    void startController() throws Exception {
        controller = FakeController.start()
                .on("POST", REGISTER, Reply.json(201, "{\"status\":\"registered\"}").always())
                .on("DELETE", DEREGISTER, Reply.status(204).always())
                .on("POST", HEARTBEAT, Reply.json(200, "{\"status\":\"ok\"}").always())
                .on("GET", JOBS, Reply.json(200, "{\"pendingJobs\":[]}").always())
                .on("POST", STATUS, Reply.json(200, "{\"status\":\"ok\"}").always())
                .on("GET", "/files/lifecycle-test.txt", Reply.json(200, "phase-0-lifecycle").always());
    }

    @AfterEach
    void stop() throws Exception {
        if (agent != null) {
            agent.shutdown();
            agent.awaitShutdown();
        }
        controller.close();
    }

    @Test
    @DisplayName("Creates an agent with its configuration")
    void testCreateAgent() {
        AgentConfiguration config = config(3);
        agent = new QuorusAgent(config);

        assertEquals(config, agent.getConfiguration());
        assertFalse(agent.isRunning(), "nothing runs before start()");
    }

    @Test
    @DisplayName("Rejects a null configuration")
    void testNullConfigHandling() {
        assertThrows(NullPointerException.class, () -> new QuorusAgent(null));
    }

    @Test
    @DisplayName("Registers, then heartbeats and polls until shut down, then deregisters")
    void testStartedAgentLifecycle() throws Exception {
        agent = new QuorusAgent(config(3));

        agent.start();

        assertTrue(agent.isRunning());
        controller.awaitRequests("POST", REGISTER, 1, WAIT);
        controller.awaitRequests("POST", HEARTBEAT, 2, WAIT);
        controller.awaitRequests("GET", JOBS, 2, WAIT);
        agent.shutdown();
        assertTrue(agent.awaitShutdown(WAIT), "a started agent should shut down cleanly");
        assertFalse(agent.isRunning());
        assertEquals(1, controller.requests("DELETE", DEREGISTER).size(), "the agent deregisters on shutdown");
    }

    @Test
    @DisplayName("Shutdown is idempotent and works before start")
    void testIdempotentShutdown() throws Exception {
        agent = new QuorusAgent(config(3));

        agent.shutdown();
        agent.shutdown();
        agent.shutdown();

        assertTrue(agent.awaitShutdown(WAIT));
        assertTrue(controller.requests().isEmpty(), "an agent that never started never calls its controller");
    }

    @Test
    @DisplayName("Rejects start after shutdown")
    void testOperationsAfterShutdown() throws Exception {
        agent = new QuorusAgent(config(3));
        agent.start();
        agent.shutdown();

        assertThrows(IllegalStateException.class, agent::start);
        assertTrue(agent.awaitShutdown(WAIT));
    }

    /** Register item ENG-27: a cluster with no leader yet, or a restarting controller, is not fatal. */
    @Test
    @DisplayName("Keeps retrying registration until the controller accepts it")
    void testRegistrationIsRetriedUntilAccepted() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        controller.on("POST", REGISTER, request -> switch (attempts.incrementAndGet()) {
            case 1 -> Reply.json(503, "{\"code\":\"NO_LEADER\",\"status\":503}");
            case 2 -> Reply.drop();
            default -> Reply.json(201, "{\"status\":\"registered\"}");
        });
        agent = new QuorusAgent(config(3));

        agent.start();

        controller.awaitRequests("POST", HEARTBEAT, 1, WAIT);
        assertEquals(3, controller.requests("POST", REGISTER).size(), "registered on the third attempt");
        assertTrue(agent.isRunning());
    }

    @Test
    @DisplayName("Can be shut down while it is still retrying registration")
    void testShutdownWhileRetryingRegistration() throws Exception {
        controller.on("POST", REGISTER, Reply.drop().always());
        agent = new QuorusAgent(config(3));

        agent.start();
        controller.awaitRequests("POST", REGISTER, 3, WAIT);
        assertTrue(agent.isRunning(), "an unreachable controller does not stop the agent");
        agent.shutdown();

        assertTrue(agent.awaitShutdown(WAIT));
        assertTrue(controller.requests("POST", HEARTBEAT).isEmpty(), "an unregistered agent sends no heartbeat");
        assertTrue(controller.requests("DELETE", DEREGISTER).isEmpty(), "and has nothing to deregister");
    }

    @Test
    @DisplayName("Shuts down when the controller rejects its registration")
    void testShutdownWhenRegistrationIsRejected() throws Exception {
        controller.on("POST", REGISTER, Reply.json(403, "{\"code\":\"FORBIDDEN\",\"status\":403}").always());
        agent = new QuorusAgent(config(3));

        agent.start();

        assertTrue(agent.awaitShutdown(WAIT), "a rejected registration cannot succeed by retrying");
        assertFalse(agent.isRunning());
        assertEquals(1, controller.requests("POST", REGISTER).size());
    }

    @Test
    @DisplayName("Refuses a job assigned to a different agent, without reporting it")
    void testRefuseForeignAssignedJob() throws Exception {
        AtomicBoolean offered = new AtomicBoolean();
        controller.on("GET", JOBS, request -> Reply.json(200, "{\"pendingJobs\":["
                + (offered.getAndSet(true) ? "" : foreignJob()) + "]}"));
        agent = new QuorusAgent(config(3));

        agent.start();
        controller.awaitRequests("GET", JOBS, 5, WAIT);             // several polls after the foreign offer

        assertTrue(controller.requests("POST", STATUS).isEmpty(),
                "Foreign-assigned job must not trigger any status reporting");
        assertTrue(agent.isRunning(), "one mismatch is below the threshold of three");
    }

    @Test
    @DisplayName("Fails fast after repeated foreign assignments")
    void testFailFastAfterRepeatedForeignAssignments() throws Exception {
        controller.on("GET", JOBS, Reply.json(200, "{\"pendingJobs\":[" + foreignJob() + "]}").always());
        agent = new QuorusAgent(config(3));

        agent.start();

        assertTrue(agent.awaitShutdown(WAIT), "Agent should shut down once the mismatch threshold is reached");
        assertEquals(3, controller.requests("GET", JOBS).size(), "the third mismatch stops the polling");
        assertThrows(IllegalStateException.class, agent::start, "Agent should be closed after fail-fast shutdown");
        assertTrue(controller.requests("POST", STATUS).isEmpty());
    }

    @Test
    @DisplayName("Acknowledges ACCEPTED and IN_PROGRESS before completing a transfer")
    void testDistributedTransferLifecycle() throws Exception {
        Path destination = root.resolve("lifecycle.txt");
        offerOnce("{\"assignmentId\":\"assign-lifecycle\",\"jobId\":\"job-lifecycle\","
                + "\"agentId\":\"test-agent-001\",\"sourceUri\":\"" + controller.url() + "/files/lifecycle-test.txt\","
                + "\"destinationPath\":" + JSON.writeValueAsString(destination.toString()) + ",\"totalBytes\":17}");
        agent = new QuorusAgent(config(3));

        agent.start();

        List<String> statuses = statusesAfter(3);
        assertEquals(List.of("ACCEPTED", "IN_PROGRESS", "COMPLETED"), statuses,
                "The controller must observe the legal lifecycle in order");
        assertEquals("phase-0-lifecycle", Files.readString(destination));
        assertTrue(controller.requests("POST", STATUS).stream().allMatch(r -> r.path().equals("/api/v1/jobs/job-lifecycle/status")));
    }

    @Test
    @DisplayName("Reports a failed transfer as FAILED")
    void testFailedTransferReporting() throws Exception {
        offerOnce("{\"assignmentId\":\"assign-missing\",\"jobId\":\"job-missing\","
                + "\"agentId\":\"test-agent-001\",\"sourceUri\":\"" + controller.url() + "/files/missing.txt\","
                + "\"destinationPath\":" + JSON.writeValueAsString(root.resolve("missing.txt").toString()) + "}");
        agent = new QuorusAgent(config(3));

        agent.start();

        List<FakeController.Request> reports = controller.awaitRequests("POST", STATUS, 3, Duration.ofSeconds(30));
        assertEquals(List.of("ACCEPTED", "IN_PROGRESS", "FAILED"),
                reports.stream().map(r -> r.json().get("status").asText()).toList());
        assertFalse(reports.getLast().json().path("errorMessage").asText().isBlank(), "the failure says why");
    }

    @Test
    @DisplayName("Serves /health and /status on the agent port")
    void testHealthEndpoints() throws Exception {
        agent = new QuorusAgent(config(3));
        agent.start();
        String base = "http://localhost:" + agent.healthPort();

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> health = get(client, base + "/health");
            HttpResponse<String> status = get(client, base + "/status");
            HttpResponse<String> unknown = get(client, base + "/unknown");
            HttpResponse<String> post = client.send(HttpRequest.newBuilder(URI.create(base + "/health"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());

            assertEquals(200, health.statusCode());
            JsonNode healthJson = JSON.readTree(health.body());
            assertEquals("UP", healthJson.get("status").asText());
            assertEquals("test-agent-001", healthJson.get("metadata").get("agentId").asText());
            assertEquals(200, status.statusCode());
            JsonNode statusJson = JSON.readTree(status.body());
            assertEquals("test-agent-001", statusJson.get("agentId").asText());
            assertEquals(5, statusJson.get("maxConcurrentTransfers").asInt());
            assertTrue(statusJson.get("runtime").get("availableProcessors").asInt() > 0);
            assertEquals(404, unknown.statusCode());
            assertEquals(405, post.statusCode());
        }
    }

    private AgentConfiguration config(int foreignAssignmentMismatchThreshold) {
        return new AgentConfiguration.Builder()
                .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                .foreignAssignmentMismatchThreshold(foreignAssignmentMismatchThreshold)
                .agentId("test-agent-001")
                .tenantId("test-tenant")
                .controllerUrl(controller.url() + "/api/v1")
                .region("test-region")
                .datacenter("test-dc")
                .agentPort(0)
                .maxConcurrentTransfers(5)
                .heartbeatInterval(50L)
                .jobPollingInitialDelayMs(1)
                .jobPollingIntervalMs(20)
                .registrationRetryIntervalMs(20)
                .build();
    }

    private void offerOnce(String job) {
        AtomicBoolean offered = new AtomicBoolean();
        controller.on("GET", JOBS, request -> Reply.json(200,
                "{\"pendingJobs\":[" + (offered.getAndSet(true) ? "" : job) + "]}"));
    }

    private static String foreignJob() {
        return "{\"assignmentId\":\"assign-foreign\",\"jobId\":\"job-foreign\",\"agentId\":\"another-agent\","
                + "\"sourceUri\":\"https://example.com/file.txt\",\"destinationPath\":\"/tmp/file.txt\",\"totalBytes\":128}";
    }

    private List<String> statusesAfter(int count) throws InterruptedException {
        return controller.awaitRequests("POST", STATUS, count, WAIT).stream()
                .map(r -> r.json().get("status").asText()).toList();
    }

    private static HttpResponse<String> get(HttpClient client, String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
