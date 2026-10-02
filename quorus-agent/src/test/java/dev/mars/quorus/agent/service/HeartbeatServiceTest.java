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

import com.fasterxml.jackson.databind.JsonNode;
import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.agent.testing.FakeController;
import dev.mars.quorus.agent.testing.FakeController.Reply;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HeartbeatService, against a real HTTP controller stand-in (no mocking).
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-05
 * @version 2.0
 */
@Timeout(value = 30, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class HeartbeatServiceTest {

    private static final String HEARTBEAT = "/agents/heartbeat";

    private FakeController controller;
    private ControllerClient client;
    private AgentConfiguration config;
    private AgentRegistrationService registrationService;
    private final AtomicInteger responseStatus = new AtomicInteger(200);

    @BeforeEach
    void setUp() throws Exception {
        controller = FakeController.start()
                .on("POST", HEARTBEAT, request -> responseStatus.get() == 200
                        ? Reply.json(200, "{\"status\":\"ok\"}") : Reply.status(responseStatus.get()))
                .on("POST", "/agents/register", Reply.json(201, "{\"status\":\"registered\"}").always());
        config = new AgentConfiguration.Builder()
                .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                .agentId("test-agent-hb")
                .tenantId("test-tenant")
                .controllerUrl(controller.url())
                .region("test-region")
                .datacenter("test-dc")
                .maxConcurrentTransfers(5)
                .build();
        client = ControllerClient.create(config);
        registrationService = new AgentRegistrationService(client, config);
    }

    @AfterEach
    void tearDown() {
        client.close();
        controller.close();
    }

    @Test
    @DisplayName("Should return false when agent not registered")
    void testHeartbeatWhenNotRegistered() throws Exception {
        assertFalse(registrationService.isRegistered(), "Fresh service should not be registered");
        HeartbeatService service = new HeartbeatService(client, config, registrationService);

        assertFalse(service.sendHeartbeat(), "Should return false when not registered");
        assertEquals(0, controller.requests("POST", HEARTBEAT).size(), "No heartbeat should be sent");
    }

    @Test
    @DisplayName("Should send heartbeat successfully when registered")
    void testHeartbeatWhenRegistered() throws Exception {
        assertTrue(registrationService.register(), "Registration should succeed");
        HeartbeatService service = new HeartbeatService(client, config, registrationService);

        assertTrue(service.sendHeartbeat(), "Heartbeat should succeed");

        assertEquals(1, controller.requests("POST", HEARTBEAT).size(), "One heartbeat should be sent");
        JsonNode request = lastHeartbeat();
        assertEquals("test-agent-hb", request.get("agentId").asText());
        assertNotNull(request.get("timestamp").asText());
        assertEquals(1, request.get("sequenceNumber").asInt());
        assertEquals("active", request.get("status").asText());
    }

    @Test
    @DisplayName("Should increment sequence number on each heartbeat")
    void testSequenceNumberIncrement() throws Exception {
        registrationService.register();
        HeartbeatService service = new HeartbeatService(client, config, registrationService);

        service.sendHeartbeat();
        service.sendHeartbeat();
        service.sendHeartbeat();

        assertEquals(3, controller.requests("POST", HEARTBEAT).size(), "Three heartbeats should be sent");
        assertEquals(3, lastHeartbeat().get("sequenceNumber").asInt(), "Sequence number should be 3");
    }

    @Test
    @DisplayName("Should return false on HTTP error response")
    void testHeartbeatHttpError() throws Exception {
        responseStatus.set(500);
        registrationService.register();
        HeartbeatService service = new HeartbeatService(client, config, registrationService);

        assertFalse(service.sendHeartbeat(), "Should return false on HTTP 500");
        assertEquals(1, controller.requests("POST", HEARTBEAT).size(), "Request should still be made");
    }

    @Test
    @DisplayName("Should return false on connection error")
    void testHeartbeatConnectionError() throws Exception {
        registrationService.register();
        HeartbeatService service = new HeartbeatService(client, config, registrationService);
        controller.close();

        assertFalse(service.sendHeartbeat(), "Should return false on connection error");
    }

    @Test
    @DisplayName("Should include metrics in heartbeat request")
    void testHeartbeatIncludesMetrics() throws Exception {
        registrationService.register();
        new HeartbeatService(client, config, registrationService).sendHeartbeat();

        JsonNode metrics = lastHeartbeat().get("metrics");
        assertNotNull(metrics, "Metrics should be included");
        assertTrue(metrics.get("memoryUsed").isNumber());
        assertTrue(metrics.get("memoryTotal").isNumber());
        assertTrue(metrics.get("memoryMax").isNumber());
        assertTrue(metrics.get("cpuCores").isInt());
    }

    private JsonNode lastHeartbeat() {
        return controller.requests("POST", HEARTBEAT).getLast().json();
    }
}
