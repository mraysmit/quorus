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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.Set;

import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static dev.mars.quorus.testing.TestFutureUtils.eventually;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Register item ENG-25: {@code DELETE /api/v1/agents/{agentId}} through the real HTTP server and Raft. */
@ExtendWith(VertxExtension.class)
class AgentDeregistrationHttpIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String NODE_ID = "agent-deregistration-http-node";
    private static final String TENANT_ID = "payments";

    private static Vertx vertx;
    private static RaftNode raftNode;
    private static QuorusStateStore stateStore;
    private static HttpApiServer httpServer;
    private static WebClient webClient;

    @BeforeAll
    static void setUp() {
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
        awaitSuccess(raftNode.start(), TIMEOUT);
        awaitSuccess(eventually(vertx, raftNode::isLeader, TIMEOUT), TIMEOUT.plusSeconds(1));
        httpServer = new HttpApiServer(vertx, 0, raftNode, stateStore, ControllerTestConfig.create());
        awaitSuccess(httpServer.start(), TIMEOUT);
        webClient = WebClient.create(vertx);
    }

    @AfterAll
    static void tearDown() {
        if (webClient != null) webClient.close();
        if (httpServer != null) awaitSuccess(httpServer.stop(), TIMEOUT);
        if (raftNode != null) awaitSuccess(raftNode.stop(), TIMEOUT);
        if (vertx != null) awaitSuccess(vertx.close(), TIMEOUT);
        InMemoryTransportSimulator.clearAllTransports();
    }

    @Test
    void deregisteringARegisteredAgentRemovesItFromReplicatedState() {
        register("agent-leaving");

        HttpResponse<Buffer> response = delete("agent-leaving");

        assertEquals(204, response.statusCode());
        assertTrue(stateStore.findAgent("agent-leaving").isEmpty(), "The agent record must be gone");
        assertEquals(404, delete("agent-leaving").statusCode(), "A second deregistration finds nothing");
    }

    @Test
    void deregisteringAnUnknownAgentIsNotFound() {
        HttpResponse<Buffer> response = delete("agent-never-registered");

        assertEquals(404, response.statusCode());
        assertTrue(response.bodyAsString().contains("AGENT_NOT_FOUND"), response::bodyAsString);
    }

    @Test
    void anAgentHoldingAnActiveAssignmentCannotDeregister() {
        register("agent-busy");
        assertEquals(201, awaitSuccess(webClient.post(httpServer.actualPort(), "localhost", "/api/v1/transfers")
                .sendJsonObject(new JsonObject().put("jobId", "job-held-by-busy-agent")
                        .put("sourceUri", "sftp://payments.example.test/out/held.dat")
                        .put("destinationPath", "target/held.dat").put("totalBytes", 500L)
                        .put("tenantId", TENANT_ID)), TIMEOUT).statusCode());
        assertEquals(201, awaitSuccess(webClient.post(httpServer.actualPort(), "localhost", "/api/v1/assignments")
                .sendJsonObject(new JsonObject().put("jobId", "job-held-by-busy-agent")
                        .put("agentId", "agent-busy")), TIMEOUT).statusCode());

        HttpResponse<Buffer> response = delete("agent-busy");

        assertEquals(409, response.statusCode());
        assertTrue(stateStore.findAgent("agent-busy").isPresent(), "A refused deregistration changes nothing");
    }

    private static void register(String agentId) {
        assertEquals(201, awaitSuccess(webClient.post(httpServer.actualPort(), "localhost", "/api/v1/agents/register")
                .sendJsonObject(new JsonObject().put("agentId", agentId)
                        .put("hostname", agentId + ".example.test").put("address", "10.0.0.51")
                        .put("port", 8080).put("tenantId", TENANT_ID)), TIMEOUT).statusCode());
    }

    private static HttpResponse<Buffer> delete(String agentId) {
        return awaitSuccess(webClient.delete(httpServer.actualPort(), "localhost", "/api/v1/agents/" + agentId)
                .send(), TIMEOUT);
    }
}
