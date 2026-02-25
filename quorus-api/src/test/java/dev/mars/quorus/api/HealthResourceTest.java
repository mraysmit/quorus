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
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.transfer.SimpleTransferEngine;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HealthResource using Vert.x testing framework.
 * Uses real HTTP server - no mocking of Vert.x components or service layer.
 * Real SimpleTransferEngine provides actual transfer metrics. A minimal
 * DistributedTransferService subclass supplies a real ClusterStatus without
 * requiring a live Raft cluster.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */
@ExtendWith(VertxExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HealthResourceTest {

    private static final int TEST_PORT = 8081;
    private WebClient client;
    private SimpleTransferEngine transferEngine;

    /**
     * A minimal DistributedTransferService that returns a fixed ClusterStatus
     * without requiring a live RaftNode or ControllerDiscoveryService.
     */
    static class StandaloneDistributedTransferService extends DistributedTransferService {
        private final ClusterStatus clusterStatus;

        StandaloneDistributedTransferService(ClusterStatus clusterStatus) {
            this.clusterStatus = clusterStatus;
        }

        @Override
        public ClusterStatus getClusterStatus() {
            return clusterStatus;
        }

        @Override
        public boolean isControllerAvailable() {
            return clusterStatus.isAvailable();
        }
    }

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        // Configure Jackson for Java 8 date/time support (java.time.* via JavaTimeModule)
        ObjectMapper mapper = DatabindCodec.mapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Create a real SimpleTransferEngine — no active transfers, so count is 0
        transferEngine = new SimpleTransferEngine(vertx, 5, 3, 100);

        // Create a real ClusterStatus representing a healthy follower node
        ClusterStatus clusterStatus = new ClusterStatus(
                "test-node",
                RaftNode.State.FOLLOWER,
                1L,
                false,
                Set.of("test-node")
        );

        // Wire up a DistributedTransferService subclass with fixed cluster status
        StandaloneDistributedTransferService distributedService =
                new StandaloneDistributedTransferService(clusterStatus);

        // Create HealthResource with real dependencies
        HealthResource healthResource = new HealthResource();
        healthResource.transferEngine = transferEngine;
        healthResource.distributedTransferService = distributedService;

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
                
                // Verify transfer engine metrics — real engine with no active transfers
                JsonObject transferEngineJson = body.getJsonObject("transferEngine");
                assertEquals(0, transferEngineJson.getInteger("activeTransfers"));
                assertEquals("operational", transferEngineJson.getString("engineStatus"));
                
                // Verify cluster metrics from the real ClusterStatus
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
        if (transferEngine != null) {
            transferEngine.shutdown(3)
                    .onComplete(ar -> testContext.completeNow());
        } else {
            testContext.completeNow();
        }
    }
}

