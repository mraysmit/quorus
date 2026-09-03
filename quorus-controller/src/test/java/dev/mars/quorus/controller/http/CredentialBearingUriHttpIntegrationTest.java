/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.http;

import dev.mars.quorus.controller.config.ControllerTestConfig;
import dev.mars.quorus.controller.raft.InMemoryTransportSimulator;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftNodeMode;
import dev.mars.quorus.controller.state.QuorusStateStore;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.Set;

import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static dev.mars.quorus.testing.TestFutureUtils.eventually;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 4 external security boundary for credential-free transfer endpoints. */
@ExtendWith(VertxExtension.class)
class CredentialBearingUriHttpIntegrationTest {

    private static final String NODE_ID = "credential-uri-node";
    private static final String JOB_ID = "credential-bearing-transfer";
    private static Vertx vertx;
    private static RaftNode raftNode;
    private static QuorusStateStore stateStore;
    private static HttpApiServer httpServer;
    private static WebClient webClient;

    @BeforeAll
    static void setUp() throws Exception {
        vertx = Vertx.vertx();
        stateStore = new QuorusStateStore();
        raftNode = RaftNode.builder()
                .vertx(vertx)
                .nodeId(NODE_ID)
                .clusterNodes(Set.of(NODE_ID))
                .transport(new InMemoryTransportSimulator(NODE_ID))
                .stateMachine(stateStore)
                .mode(RaftNodeMode.volatileMode())
                .electionTimeout(200)
                .heartbeatInterval(50)
                .build();
        awaitSuccess(raftNode.start(), Duration.ofSeconds(5));
        awaitSuccess(eventually(vertx, raftNode::isLeader, Duration.ofSeconds(5)), Duration.ofSeconds(6));
        httpServer = new HttpApiServer(vertx, 0, raftNode, stateStore, ControllerTestConfig.create());
        awaitSuccess(httpServer.start(), Duration.ofSeconds(5));
        webClient = WebClient.create(vertx);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (webClient != null) webClient.close();
        if (httpServer != null) awaitSuccess(httpServer.stop(), Duration.ofSeconds(5));
        if (raftNode != null) awaitSuccess(raftNode.stop(), Duration.ofSeconds(5));
        if (vertx != null) awaitSuccess(vertx.close(), Duration.ofSeconds(5));
    }

    @Test
    void rejectsCredentialBearingSourceBeforeReplicatedState(VertxTestContext context) {
        JsonObject transfer = new JsonObject()
                .put("jobId", JOB_ID)
                .put("sourceUri",
                        "sftp://settlement-user:synthetic-secret@payments.example.test/out/settlement.dat")
                .put("destinationPath", "target/settlement.dat")
                .put("totalBytes", 1_024L)
                .put("tenantId", "regulated-bank-a");

        webClient.post(httpServer.actualPort(), "localhost", "/api/v1/transfers")
                .sendJsonObject(transfer)
                .onSuccess(response -> context.verify(() -> {
                    assertEquals(400, response.statusCode());
                    JsonObject error = response.bodyAsJsonObject();
                    assertEquals("VALIDATION_ERROR", error.getString("code"));
                    assertTrue(error.getString("detail").contains("must not contain user-info"));
                    assertFalse(stateStore.hasTransferJob(JOB_ID),
                            "A rejected credential-bearing URI must not enter authoritative state");
                    context.completeNow();
                }))
                .onFailure(context::failNow);
    }
}
