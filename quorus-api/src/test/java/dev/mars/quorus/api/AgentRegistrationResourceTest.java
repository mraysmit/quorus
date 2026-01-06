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

package dev.mars.quorus.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.mars.quorus.agent.AgentCapabilities;
import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.api.dto.AgentRegistrationRequest;
import dev.mars.quorus.api.service.AgentRegistryService;
import dev.mars.quorus.api.service.HeartbeatProcessor;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for AgentRegistrationResource using Vert.x testing framework.
 * Uses real HTTP server - no mocking of Vert.x components.
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-12-17
 */
@ExtendWith(VertxExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentRegistrationResourceTest {

    private static final int TEST_PORT = 8083;
    private WebClient client;
    private AgentRegistryService mockAgentRegistry;
    private HeartbeatProcessor mockHeartbeatProcessor;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        // Configure Jackson for Java 8 date/time support
        ObjectMapper mapper = DatabindCodec.mapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Create mock services
        mockAgentRegistry = mock(AgentRegistryService.class);
        mockHeartbeatProcessor = mock(HeartbeatProcessor.class);

        // Create AgentRegistrationResource with mocked dependencies
        AgentRegistrationResource agentResource = new AgentRegistrationResource();
        agentResource.agentRegistryService = mockAgentRegistry;
        agentResource.heartbeatProcessor = mockHeartbeatProcessor;

        // Create router and register routes
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        agentResource.registerRoutes(router);

        // Start HTTP server
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(TEST_PORT)
            .onSuccess(server -> {
                client = WebClient.create(vertx);
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/agents/register should register agent")
    void testRegisterAgent(Vertx vertx, VertxTestContext testContext) {
        // Mock agent registration
        AgentInfo mockAgentInfo = new AgentInfo("test-agent-001", "test-host", "192.168.1.100", 8080);
        mockAgentInfo.setVersion("1.0.0");
        mockAgentInfo.setStatus(AgentStatus.ACTIVE);
        mockAgentInfo.setRegistrationTime(Instant.now());
        mockAgentInfo.setLastHeartbeat(Instant.now());

        when(mockAgentRegistry.registerAgent(any(AgentRegistrationRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(mockAgentInfo));

        JsonObject requestBody = new JsonObject()
            .put("agentId", "test-agent-001")
            .put("hostname", "test-host")
            .put("address", "192.168.1.100")
            .put("port", 8080)
            .put("version", "1.0.0");

        client.post(TEST_PORT, "localhost", "/api/v1/agents/register")
            .sendJsonObject(requestBody)
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(201, response.statusCode());
                JsonObject body = response.bodyAsJsonObject();
                assertNotNull(body);
                assertTrue(body.getBoolean("success"));
                testContext.completeNow();
            })));
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/v1/agents should list all agents")
    void testListAgents(Vertx vertx, VertxTestContext testContext) {
        // Mock agent list
        AgentInfo agent1 = new AgentInfo("agent-1", "host-1", "192.168.1.1", 8080);
        agent1.setStatus(AgentStatus.ACTIVE);

        AgentInfo agent2 = new AgentInfo("agent-2", "host-2", "192.168.1.2", 8080);
        agent2.setStatus(AgentStatus.ACTIVE);

        when(mockAgentRegistry.getAllAgents()).thenReturn(Arrays.asList(agent1, agent2));

        client.get(TEST_PORT, "localhost", "/api/v1/agents")
            .send()
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(200, response.statusCode());
                List<?> agents = response.bodyAsJsonArray().getList();
                assertEquals(2, agents.size());
                testContext.completeNow();
            })));
    }

    @AfterEach
    void tearDown(Vertx vertx, VertxTestContext testContext) {
        if (client != null) {
            client.close();
        }
        testContext.completeNow();
    }
}

