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

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgentRegistrationService, against a real HTTP controller stand-in (no mocking).
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-05
 * @version 2.0
 */
@Timeout(value = 30, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class AgentRegistrationServiceTest {

    private static final String REGISTER = "/agents/register";
    private static final String AGENT = "/agents/[^/]+";

    private FakeController controller;
    private ControllerClient client;
    private AgentConfiguration config;
    private final AtomicInteger registerResponseStatus = new AtomicInteger(201);
    private final AtomicInteger deregisterResponseStatus = new AtomicInteger(200);

    @BeforeEach
    void setUp() throws Exception {
        controller = FakeController.start()
                .on("POST", REGISTER, request -> registerResponseStatus.get() == 201 || registerResponseStatus.get() == 200
                        ? Reply.json(registerResponseStatus.get(), "{\"status\":\"registered\"}")
                        : Reply.status(registerResponseStatus.get()))
                .on("DELETE", AGENT, request -> Reply.status(deregisterResponseStatus.get()));
        config = new AgentConfiguration.Builder()
                .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                .agentId("test-agent-reg")
                .tenantId("test-tenant")
                .controllerUrl(controller.url())
                .region("test-region")
                .datacenter("test-dc")
                .hostname("test-host")
                .address("192.168.1.100")
                .agentPort(9090)
                .version("1.0.0-TEST")
                .maxConcurrentTransfers(10)
                .supportedProtocols(Set.of("HTTP", "SFTP"))
                .build();
        client = ControllerClient.create(config);
    }

    @AfterEach
    void tearDown() {
        client.close();
        controller.close();
    }

    @Test
    @DisplayName("Should register successfully with HTTP 201")
    void testRegisterSuccess201() throws Exception {
        registerResponseStatus.set(201);
        AgentRegistrationService service = new AgentRegistrationService(client, config);
        assertFalse(service.isRegistered(), "Should not be registered initially");

        assertTrue(service.register(), "Registration should succeed");

        assertTrue(service.isRegistered(), "Should be registered after success");
        assertEquals(1, controller.requests("POST", REGISTER).size(), "One registration request should be made");
    }

    @Test
    @DisplayName("Should register successfully with HTTP 200")
    void testRegisterSuccess200() throws Exception {
        registerResponseStatus.set(200);
        AgentRegistrationService service = new AgentRegistrationService(client, config);

        assertTrue(service.register(), "Registration should succeed with 200");
        assertTrue(service.isRegistered());
    }

    @Test
    @DisplayName("Should fail registration on HTTP 400")
    void testRegisterFailure400() throws Exception {
        registerResponseStatus.set(400);
        AgentRegistrationService service = new AgentRegistrationService(client, config);

        assertFalse(service.register(), "Registration should fail on 400");
        assertFalse(service.isRegistered(), "Should not be registered after failure");
    }

    @Test
    @DisplayName("Should fail registration on HTTP 500")
    void testRegisterFailure500() throws Exception {
        registerResponseStatus.set(500);
        AgentRegistrationService service = new AgentRegistrationService(client, config);

        assertFalse(service.register(), "Registration should fail on 500");
        assertFalse(service.isRegistered());
    }

    @Test
    @DisplayName("Should fail registration on connection error")
    void testRegisterConnectionError() throws Exception {
        AgentConfiguration badConfig = unreachableControllerConfig();
        try (ControllerClient badClient = ControllerClient.create(badConfig)) {
            AgentRegistrationService service = new AgentRegistrationService(badClient, badConfig);

            assertFalse(service.register(), "Registration should fail on connection error");
            assertFalse(service.isRegistered());
        }
    }

    @Test
    @DisplayName("Should create correct registration request JSON")
    void testRegistrationRequestFormat() throws Exception {
        new AgentRegistrationService(client, config).register();

        JsonNode request = controller.requests("POST", REGISTER).getFirst().json();
        assertEquals("test-agent-reg", request.get("agentId").asText());
        assertEquals("test-host", request.get("hostname").asText());
        assertEquals("192.168.1.100", request.get("address").asText());
        assertEquals(9090, request.get("port").asInt());
        assertEquals("1.0.0-TEST", request.get("version").asText());
        assertEquals("test-region", request.get("region").asText());
        assertEquals("test-dc", request.get("datacenter").asText());

        JsonNode capabilities = request.get("capabilities");
        assertNotNull(capabilities, "Capabilities should be included");
        assertEquals(10, capabilities.get("maxConcurrentTransfers").asInt());
        List<String> protocols = capabilities.get("supportedProtocols").valueStream().map(JsonNode::asText).toList();
        assertTrue(protocols.contains("HTTP"));
        assertTrue(protocols.contains("SFTP"));
    }

    @Test
    @DisplayName("Should deregister successfully with HTTP 200")
    void testDeregisterSuccess200() throws Exception {
        deregisterResponseStatus.set(200);
        AgentRegistrationService service = new AgentRegistrationService(client, config);
        service.register();
        assertTrue(service.isRegistered());

        assertTrue(service.deregister(), "Deregistration should succeed");

        assertFalse(service.isRegistered(), "Should not be registered after deregister");
        List<FakeController.Request> deregistrations = controller.requests("DELETE", AGENT);
        assertEquals(1, deregistrations.size());
        assertEquals("/agents/test-agent-reg", deregistrations.getFirst().path());
    }

    @Test
    @DisplayName("Should deregister successfully with HTTP 204")
    void testDeregisterSuccess204() throws Exception {
        deregisterResponseStatus.set(204);
        AgentRegistrationService service = new AgentRegistrationService(client, config);
        service.register();

        assertTrue(service.deregister(), "Deregistration should succeed with 204");
        assertFalse(service.isRegistered());
    }

    @Test
    @DisplayName("Should fail deregister on HTTP 404: it once hid a controller with no deregistration route (ENG-25)")
    void testDeregister404IsAFailure() throws Exception {
        deregisterResponseStatus.set(404);
        AgentRegistrationService service = new AgentRegistrationService(client, config);
        service.register();

        assertFalse(service.deregister(), "A 404 must be reported, not treated as success");
        assertTrue(service.isRegistered(), "Should stay registered after a failed deregistration");
    }

    @Test
    @DisplayName("Should skip deregister if not registered")
    void testDeregisterWhenNotRegistered() throws Exception {
        AgentRegistrationService service = new AgentRegistrationService(client, config);
        assertFalse(service.isRegistered(), "Should not be registered");

        assertTrue(service.deregister(), "Should return true (no-op)");
        assertEquals(0, controller.requests("DELETE", AGENT).size(), "No request should be made");
    }

    @Test
    @DisplayName("Should fail deregister on HTTP 500 and stay registered")
    void testDeregisterFailure500() throws Exception {
        deregisterResponseStatus.set(500);
        AgentRegistrationService service = new AgentRegistrationService(client, config);
        service.register();

        assertFalse(service.deregister(), "Deregistration should fail on 500");
        assertTrue(service.isRegistered(), "a refused deregistration leaves the agent registered");
    }

    @Test
    @DisplayName("Should fail deregister on a connection error and stay registered")
    void testDeregisterConnectionError() throws Exception {
        AgentRegistrationService service = new AgentRegistrationService(client, config);
        assertTrue(service.register(), "Should register successfully first");
        controller.close();

        assertFalse(service.deregister(), "Deregistration should fail when the controller is gone");
        assertTrue(service.isRegistered());
    }

    @Test
    @DisplayName("Should maintain registration state correctly through lifecycle")
    void testRegistrationStateLifecycle() throws Exception {
        AgentRegistrationService service = new AgentRegistrationService(client, config);
        assertFalse(service.isRegistered(), "Initial state: not registered");

        service.register();
        assertTrue(service.isRegistered(), "After register: registered");
        service.deregister();
        assertFalse(service.isRegistered(), "After deregister: not registered");
        service.register();

        assertTrue(service.isRegistered(), "After re-register: registered again");
        assertEquals(2, controller.requests("POST", REGISTER).size(), "Two registration requests");
        assertEquals(1, controller.requests("DELETE", AGENT).size(), "One deregistration request");
    }

    private static AgentConfiguration unreachableControllerConfig() {
        return new AgentConfiguration.Builder()
                .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                .agentId("test-agent-bad")
                .tenantId("test-tenant")
                .controllerUrl("http://localhost:59999") // Non-existent port
                .region("test-region")
                .datacenter("test-dc")
                .httpConnectionTimeout(1000)
                .build();
    }
}
