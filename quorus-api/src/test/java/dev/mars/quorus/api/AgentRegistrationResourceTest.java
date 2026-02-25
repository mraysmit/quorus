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
import io.vertx.core.json.JsonArray;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AgentRegistrationResource using Vert.x testing framework.
 * Uses real HTTP server - no mocking of Vert.x components or service layer.
 * A minimal AgentRegistryService subclass provides in-memory agent storage
 * without requiring a live Raft cluster.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-12-17
 */
@ExtendWith(VertxExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentRegistrationResourceTest {

    private static final int TEST_PORT = 8083;
    private WebClient client;

    /**
     * In-memory AgentRegistryService that stores agents directly without Raft consensus.
     * Overrides methods that touch RaftNode to work purely in-memory.
     */
    static class InMemoryAgentRegistryService extends AgentRegistryService {
        private final ConcurrentHashMap<String, AgentInfo> agents = new ConcurrentHashMap<>();

        @Override
        public CompletableFuture<AgentInfo> registerAgent(AgentRegistrationRequest request) {
            return CompletableFuture.supplyAsync(() -> {
                if (request.getAgentId() == null || request.getAgentId().trim().isEmpty()) {
                    throw new IllegalArgumentException("Agent ID is required");
                }
                if (agents.containsKey(request.getAgentId())) {
                    throw new IllegalArgumentException("Agent ID already exists: " + request.getAgentId());
                }

                AgentInfo agentInfo = new AgentInfo(
                        request.getAgentId(),
                        request.getHostname(),
                        request.getAddress(),
                        request.getPort());
                agentInfo.setVersion(request.getVersion());
                agentInfo.setCapabilities(request.getCapabilities());
                agentInfo.setStatus(AgentStatus.ACTIVE);
                agentInfo.setRegistrationTime(Instant.now());
                agentInfo.setLastHeartbeat(Instant.now());

                agents.put(agentInfo.getAgentId(), agentInfo);
                return agentInfo;
            });
        }

        @Override
        public List<AgentInfo> getAllAgents() {
            return new ArrayList<>(agents.values());
        }

        @Override
        public AgentInfo getAgent(String agentId) {
            return agents.get(agentId);
        }
    }

    /**
     * Minimal HeartbeatProcessor subclass that does not require RaftNode or Vertx injection.
     * No heartbeat routes are exercised in these tests.
     */
    static class NoOpHeartbeatProcessor extends HeartbeatProcessor {
        // No overrides needed — heartbeat endpoints are not called in these tests
    }

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        // Configure Jackson for Java 8 date/time support (java.time.* via JavaTimeModule)
        ObjectMapper mapper = DatabindCodec.mapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Create real in-memory services — no Raft dependency
        InMemoryAgentRegistryService agentRegistry = new InMemoryAgentRegistryService();
        NoOpHeartbeatProcessor heartbeatProcessor = new NoOpHeartbeatProcessor();

        // Create AgentRegistrationResource with real in-memory services
        AgentRegistrationResource agentResource = new AgentRegistrationResource();
        agentResource.agentRegistryService = agentRegistry;
        agentResource.heartbeatProcessor = heartbeatProcessor;

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
        JsonObject requestBody = new JsonObject()
            .put("agentId", "test-agent-001")
            .put("hostname", "test-host")
            .put("address", "192.168.1.100")
            .put("port", 8080)
            .put("version", "1.0.0")
            .put("capabilities", new JsonObject()
                .put("supportedProtocols", new JsonArray().add("http").add("sftp"))
                .put("maxConcurrentTransfers", 5)
                .put("maxTransferSize", 1073741824));

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
        // Register two agents first, then list them
        JsonObject agent1Body = new JsonObject()
            .put("agentId", "agent-1")
            .put("hostname", "host-1")
            .put("address", "192.168.1.1")
            .put("port", 8080)
            .put("capabilities", new JsonObject()
                .put("supportedProtocols", new JsonArray().add("http"))
                .put("maxConcurrentTransfers", 5)
                .put("maxTransferSize", 1073741824));

        JsonObject agent2Body = new JsonObject()
            .put("agentId", "agent-2")
            .put("hostname", "host-2")
            .put("address", "192.168.1.2")
            .put("port", 8080)
            .put("capabilities", new JsonObject()
                .put("supportedProtocols", new JsonArray().add("sftp"))
                .put("maxConcurrentTransfers", 3)
                .put("maxTransferSize", 536870912));

        // Register agent-1
        client.post(TEST_PORT, "localhost", "/api/v1/agents/register")
            .sendJsonObject(agent1Body)
            .onComplete(testContext.succeeding(r1 -> {
                assertEquals(201, r1.statusCode());

                // Register agent-2
                client.post(TEST_PORT, "localhost", "/api/v1/agents/register")
                    .sendJsonObject(agent2Body)
                    .onComplete(testContext.succeeding(r2 -> {
                        assertEquals(201, r2.statusCode());

                        // Now list all agents
                        client.get(TEST_PORT, "localhost", "/api/v1/agents")
                            .send()
                            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                                assertEquals(200, response.statusCode());
                                List<?> agents = response.bodyAsJsonArray().getList();
                                assertEquals(2, agents.size());
                                testContext.completeNow();
                            })));
                    }));
            }));
    }

    @AfterEach
    void tearDown(Vertx vertx, VertxTestContext testContext) {
        if (client != null) {
            client.close();
        }
        testContext.completeNow();
    }
}

