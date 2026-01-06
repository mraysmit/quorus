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
import dev.mars.quorus.api.service.ClusterStatus;
import dev.mars.quorus.api.service.DistributedTransferService;
import dev.mars.quorus.transfer.TransferEngine;
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

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for HealthResource using Vert.x testing framework.
 * Uses real HTTP server - no mocking of Vert.x components.
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */
@ExtendWith(VertxExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HealthResourceTest {

    private static final int TEST_PORT = 8081;
    private WebClient client;
    private TransferEngine mockTransferEngine;
    private DistributedTransferService mockDistributedService;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        // Configure Jackson for Java 8 date/time support
        ObjectMapper mapper = DatabindCodec.mapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Create mock services (allowed for unit tests)
        mockTransferEngine = mock(TransferEngine.class);
        when(mockTransferEngine.getActiveTransferCount()).thenReturn(5);

        mockDistributedService = mock(DistributedTransferService.class);
        ClusterStatus mockStatus = mock(ClusterStatus.class);
        when(mockStatus.isAvailable()).thenReturn(true);
        when(mockStatus.isHealthy()).thenReturn(true);
        when(mockStatus.getNodeId()).thenReturn("test-node");
        when(mockStatus.getState()).thenReturn(dev.mars.quorus.controller.raft.RaftNode.State.FOLLOWER);
        when(mockStatus.getTerm()).thenReturn(1L);
        when(mockStatus.isLeader()).thenReturn(false);
        when(mockStatus.getKnownNodes()).thenReturn(Collections.emptySet());
        when(mockStatus.getStatusDescription()).thenReturn("Healthy");
        when(mockDistributedService.getClusterStatus()).thenReturn(mockStatus);

        // Create HealthResource with mocked dependencies
        HealthResource healthResource = new HealthResource();
        healthResource.transferEngine = mockTransferEngine;
        healthResource.distributedTransferService = mockDistributedService;

        // Create router and register routes
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        healthResource.registerRoutes(router);

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
    @DisplayName("GET /api/v1/info should return service information")
    void testGetInfo(Vertx vertx, VertxTestContext testContext) {
        client.get(TEST_PORT, "localhost", "/api/v1/info")
            .send()
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(200, response.statusCode());
                JsonObject body = response.bodyAsJsonObject();
                assertNotNull(body);
                assertEquals("quorus-api", body.getString("name"));
                assertEquals("2.0", body.getString("version"));
                assertTrue(body.containsKey("capabilities"));
                assertTrue(body.containsKey("protocols"));
                assertTrue(body.containsKey("endpoints"));
                testContext.completeNow();
            })));
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/v1/status should return detailed status")
    void testGetStatus(Vertx vertx, VertxTestContext testContext) {
        client.get(TEST_PORT, "localhost", "/api/v1/status")
            .send()
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(200, response.statusCode());
                JsonObject body = response.bodyAsJsonObject();
                assertNotNull(body);
                assertEquals("quorus-api", body.getString("service"));
                assertTrue(body.containsKey("transferEngine"));
                assertTrue(body.containsKey("cluster"));
                assertTrue(body.containsKey("system"));
                
                // Verify transfer engine metrics
                JsonObject transferEngine = body.getJsonObject("transferEngine");
                assertEquals(5, transferEngine.getInteger("activeTransfers"));
                assertEquals("operational", transferEngine.getString("engineStatus"));
                
                // Verify cluster metrics
                JsonObject cluster = body.getJsonObject("cluster");
                assertTrue(cluster.getBoolean("available"));
                assertTrue(cluster.getBoolean("healthy"));
                assertEquals("test-node", cluster.getString("nodeId"));
                
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

