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

package dev.mars.quorus.controller.http;

import dev.mars.quorus.controller.raft.InMemoryTransportSimulator;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftTransport;
import dev.mars.quorus.controller.state.QuorusStateMachine;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.Set;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LeaderGuardHandler}.
 *
 * <p>Validates that:
 * <ul>
 *   <li>Write requests on the leader node pass through</li>
 *   <li>Read requests always pass through regardless of leadership</li>
 *   <li>Write requests on follower nodes are rejected with NOT_LEADER error</li>
 *   <li>Non-API paths (health, metrics) bypass the guard</li>
 * </ul>
 *
 * <p>Uses a real 3-node Raft cluster to obtain both leader and follower nodes.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-19
 */
@ExtendWith(VertxExtension.class)
@DisplayName("LeaderGuardHandler Tests")
class LeaderGuardHandlerTest {

    private static final int LEADER_PORT = 18098;
    private static final int FOLLOWER_PORT = 18099;

    private static Vertx vertx;
    private static RaftNode leaderNode;
    private static RaftNode followerNode;
    private static HttpApiServer leaderServer;
    private static HttpApiServer followerServer;
    private static WebClient webClient;

    @BeforeAll
    static void setUp() throws Exception {
        vertx = Vertx.vertx();

        Set<String> clusterNodes = Set.of("guard-node-1", "guard-node-2", "guard-node-3");

        RaftTransport transport1 = new InMemoryTransportSimulator("guard-node-1");
        RaftTransport transport2 = new InMemoryTransportSimulator("guard-node-2");
        RaftTransport transport3 = new InMemoryTransportSimulator("guard-node-3");

        QuorusStateMachine sm1 = new QuorusStateMachine();
        QuorusStateMachine sm2 = new QuorusStateMachine();
        QuorusStateMachine sm3 = new QuorusStateMachine();

        RaftNode node1 = new RaftNode(vertx, "guard-node-1", clusterNodes, transport1, sm1, 500, 100);
        RaftNode node2 = new RaftNode(vertx, "guard-node-2", clusterNodes, transport2, sm2, 500, 100);
        RaftNode node3 = new RaftNode(vertx, "guard-node-3", clusterNodes, transport3, sm3, 500, 100);

        node1.start();
        node2.start();
        node3.start();

        // Wait for leader election 
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> node1.isLeader() || node2.isLeader() || node3.isLeader());

        // Identify leader and a follower
        RaftNode[] allNodes = {node1, node2, node3};
        for (RaftNode node : allNodes) {
            if (node.isLeader()) {
                leaderNode = node;
            } else if (followerNode == null) {
                followerNode = node;
            }
        }

        assertNotNull(leaderNode, "Cluster should have a leader");
        assertNotNull(followerNode, "Cluster should have a follower");

        // Start HTTP servers on leader and follower
        leaderServer = new HttpApiServer(vertx, LEADER_PORT, leaderNode);
        leaderServer.start().toCompletionStage().toCompletableFuture()
                .get(5, java.util.concurrent.TimeUnit.SECONDS);

        followerServer = new HttpApiServer(vertx, FOLLOWER_PORT, followerNode);
        followerServer.start().toCompletionStage().toCompletableFuture()
                .get(5, java.util.concurrent.TimeUnit.SECONDS);

        webClient = WebClient.create(vertx);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (webClient != null) webClient.close();
        if (leaderServer != null) leaderServer.stop().toCompletionStage().toCompletableFuture()
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
        if (followerServer != null) followerServer.stop().toCompletionStage().toCompletableFuture()
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
        if (vertx != null) vertx.close().toCompletionStage().toCompletableFuture()
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
        InMemoryTransportSimulator.clearAllTransports();
    }

    // ==================== Leader: writes pass through ====================

    @Nested
    @DisplayName("Leader node — writes allowed")
    class LeaderWrites {

        @Test
        @DisplayName("POST to leader succeeds (201)")
        void testWriteOnLeader(VertxTestContext ctx) {
            JsonObject body = new JsonObject()
                    .put("jobId", "guard-leader-job")
                    .put("agentId", "guard-leader-agent");

            webClient.post(LEADER_PORT, "localhost", "/api/v1/assignments")
                    .sendJsonObject(body)
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(201, response.statusCode(),
                                "Write on leader should succeed");
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== Follower: writes rejected ====================

    @Nested
    @DisplayName("Follower node — writes rejected")
    class FollowerWrites {

        @Test
        @DisplayName("POST to follower returns NOT_LEADER error")
        void testWriteOnFollower(VertxTestContext ctx) {
            JsonObject body = new JsonObject()
                    .put("jobId", "guard-follower-job")
                    .put("agentId", "guard-follower-agent");

            webClient.post(FOLLOWER_PORT, "localhost", "/api/v1/assignments")
                    .sendJsonObject(body)
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(503, response.statusCode(),
                                "Write on follower should be rejected");
                        JsonObject json = response.bodyAsJsonObject();
                        JsonObject error = json.getJsonObject("error");
                        assertNotNull(error, "Error envelope expected");
                        String code = error.getString("code");
                        assertTrue("NOT_LEADER".equals(code) || "NO_LEADER".equals(code),
                                "Error code should be NOT_LEADER or NO_LEADER, got: " + code);
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("DELETE to follower returns NOT_LEADER error")
        void testDeleteOnFollower(VertxTestContext ctx) {
            webClient.delete(FOLLOWER_PORT, "localhost", "/api/v1/assignments/any-id")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(503, response.statusCode(),
                                "Delete on follower should be rejected");
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== Reads always pass through ====================

    @Nested
    @DisplayName("Reads pass through on any node")
    class ReadPassthrough {

        @Test
        @DisplayName("GET on follower succeeds (200)")
        void testReadOnFollower(VertxTestContext ctx) {
            webClient.get(FOLLOWER_PORT, "localhost", "/api/v1/assignments")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode(),
                                "Read on follower should succeed");
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("GET on leader succeeds (200)")
        void testReadOnLeader(VertxTestContext ctx) {
            webClient.get(LEADER_PORT, "localhost", "/api/v1/assignments")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode(),
                                "Read on leader should succeed");
                        ctx.completeNow();
                    })));
        }
    }

    // ==================== Non-API paths bypass guard ====================

    @Nested
    @DisplayName("Non-API paths bypass guard")
    class NonApiPaths {

        @Test
        @DisplayName("GET /health/live on follower always succeeds")
        void testHealthOnFollower(VertxTestContext ctx) {
            webClient.get(FOLLOWER_PORT, "localhost", "/health/live")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode(),
                                "Health probe on follower should succeed");
                        ctx.completeNow();
                    })));
        }

        @Test
        @DisplayName("GET /raft/status on follower always succeeds")
        void testRaftStatusOnFollower(VertxTestContext ctx) {
            webClient.get(FOLLOWER_PORT, "localhost", "/raft/status")
                    .send()
                    .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
                        assertEquals(200, response.statusCode(),
                                "Raft status on follower should succeed");
                        ctx.completeNow();
                    })));
        }
    }
}
