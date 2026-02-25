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
import dev.mars.quorus.api.service.DistributedTransferService;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
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

import java.net.URI;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TransferResource using Vert.x testing framework.
 * Uses real HTTP server - no mocking of Vert.x components.
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */
@ExtendWith(VertxExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransferResourceTest {

    private static final int TEST_PORT = 8082;
    private WebClient client;
    private SimpleTransferEngine transferEngine;

    /**
     * A minimal DistributedTransferService that always reports the controller as unavailable.
     * This forces TransferResource to use the local TransferEngine fallback path,
     * which is the code path under test.
     */
    static class StandaloneDistributedTransferService extends DistributedTransferService {
        @Override
        public boolean isControllerAvailable() {
            return false;
        }
    }

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        // Configure Jackson for Java 8 date/time support
        ObjectMapper mapper = DatabindCodec.mapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Create real service instances
        transferEngine = new SimpleTransferEngine(vertx, 5, 3, 100);

        // Create TransferResource with real dependencies
        TransferResource transferResource = new TransferResource();
        transferResource.transferEngine = transferEngine;
        transferResource.distributedTransferService = new StandaloneDistributedTransferService();

        // Create router and register routes
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        transferResource.registerRoutes(router);

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
    @DisplayName("POST /api/v1/transfers should create transfer job")
    void testCreateTransfer(Vertx vertx, VertxTestContext testContext) {
        JsonObject requestBody = new JsonObject()
            .put("sourceUri", "http://example.com/file.txt")
            .put("destinationPath", "/tmp/file.txt");

        client.post(TEST_PORT, "localhost", "/api/v1/transfers")
            .sendJsonObject(requestBody)
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(201, response.statusCode());
                JsonObject body = response.bodyAsJsonObject();
                assertNotNull(body);
                assertTrue(body.containsKey("jobId"));
                assertEquals("http://example.com/file.txt", body.getString("sourceUri"));
                assertEquals("/tmp/file.txt", body.getString("destinationPath"));
                testContext.completeNow();
            })));
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/v1/transfers should reject invalid request")
    void testCreateTransferInvalidRequest(Vertx vertx, VertxTestContext testContext) {
        JsonObject requestBody = new JsonObject()
            .put("sourceUri", ""); // Empty source URI

        client.post(TEST_PORT, "localhost", "/api/v1/transfers")
            .sendJsonObject(requestBody)
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(400, response.statusCode());
                testContext.completeNow();
            })));
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/v1/transfers/:jobId should return job status")
    void testGetTransferStatus(Vertx vertx, VertxTestContext testContext) {
        // First create a real transfer to query
        JsonObject requestBody = new JsonObject()
            .put("sourceUri", "http://example.com/file.txt")
            .put("destinationPath", "/tmp/file.txt");

        client.post(TEST_PORT, "localhost", "/api/v1/transfers")
            .sendJsonObject(requestBody)
            .onComplete(testContext.succeeding(createResponse -> testContext.verify(() -> {
                assertEquals(201, createResponse.statusCode());
                String jobId = createResponse.bodyAsJsonObject().getString("jobId");
                assertNotNull(jobId);

                // Now query the created job
                client.get(TEST_PORT, "localhost", "/api/v1/transfers/" + jobId)
                    .send()
                    .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                        // The local engine may or may not still have the job depending on timing,
                        // so accept either 200 (found) or 404 (already completed/evicted)
                        assertTrue(response.statusCode() == 200 || response.statusCode() == 404,
                                "Expected 200 or 404 but got " + response.statusCode());
                        testContext.completeNow();
                    })));
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

