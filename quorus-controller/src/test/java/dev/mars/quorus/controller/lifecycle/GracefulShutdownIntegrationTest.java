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

package dev.mars.quorus.controller.lifecycle;

import dev.mars.quorus.controller.http.HttpApiServer;
import dev.mars.quorus.controller.raft.InMemoryTransportSimulator;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftTransport;
import dev.mars.quorus.controller.state.QuorusStateMachine;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the graceful shutdown flow.
 *
 * <p>Validates the complete shutdown sequence:
 * <ol>
 *   <li>ShutdownCoordinator enters drain mode → API rejects non-health requests</li>
 *   <li>Health endpoints remain accessible during drain</li>
 *   <li>Services stop in reverse startup order</li>
 *   <li>Shutdown completes without errors</li>
 * </ol>
 *
 * <p>Uses real RaftNode and HttpApiServer instances (no mocking).</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-13
 */
@ExtendWith(VertxExtension.class)
@DisplayName("Graceful Shutdown Integration Tests")
class GracefulShutdownIntegrationTest {

    private static final int HTTP_PORT = 18097;

    @Nested
    @DisplayName("Full Shutdown Sequence")
    class FullShutdownSequenceTests {

        @Test
        @DisplayName("Should complete full 4-phase shutdown without errors")
        void shouldCompleteFullShutdown(Vertx vertx, VertxTestContext ctx) throws Exception {
            QuorusStateMachine stateMachine = new QuorusStateMachine();
            RaftTransport transport = new InMemoryTransportSimulator("shutdown-test");
            Set<String> clusterNodes = Set.of("shutdown-test");

            RaftNode raftNode = new RaftNode(vertx, "shutdown-test", clusterNodes, 
                    transport, stateMachine, 500, 100);
            raftNode.start();

            await().atMost(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(50))
                    .until(raftNode::isLeader);

            HttpApiServer apiServer = new HttpApiServer(vertx, HTTP_PORT, raftNode);
            apiServer.start().toCompletionStage().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Configure shutdown coordinator with real services
            ShutdownCoordinator coordinator = new ShutdownCoordinator(vertx, 2000, 10000);
            
            coordinator.onDrain("http-drain", apiServer::enterDrainMode);
            coordinator.onServiceStop("http-stop", apiServer::stop);
            coordinator.onServiceStop("raft-stop", () -> {
                raftNode.stop();
                return Future.succeededFuture();
            });

            // Verify running state
            assertTrue(coordinator.isAcceptingWork());
            assertFalse(apiServer.isDraining());

            // Execute shutdown
            coordinator.shutdown()
                    .onComplete(ctx.succeeding(v -> ctx.verify(() -> {
                        // Verify final state
                        assertEquals(ShutdownCoordinator.State.STOPPED, coordinator.getState());
                        assertFalse(coordinator.isAcceptingWork());
                        assertTrue(apiServer.isDraining(),
                                "API server should be in drain mode after shutdown");
                        ctx.completeNow();
                    })));
        }
    }

    @Nested
    @DisplayName("Drain Mode During Shutdown")
    class DrainModeBehaviorTests {

        @Test
        @DisplayName("Should reject API requests but allow health during drain")
        void shouldRejectApiButAllowHealth(Vertx vertx, VertxTestContext ctx) throws Exception {
            QuorusStateMachine stateMachine = new QuorusStateMachine();
            RaftTransport transport = new InMemoryTransportSimulator("drain-seq-test");
            Set<String> clusterNodes = Set.of("drain-seq-test");
            
            int port = 18098;
            RaftNode raftNode = new RaftNode(vertx, "drain-seq-test", clusterNodes,
                    transport, stateMachine, 500, 100);
            raftNode.start();

            await().atMost(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(50))
                    .until(raftNode::isLeader);

            HttpApiServer apiServer = new HttpApiServer(vertx, port, raftNode);
            apiServer.start().toCompletionStage().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            WebClient webClient = WebClient.create(vertx);

            // Enter drain mode
            apiServer.enterDrainMode().toCompletionStage().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            // Health should still work
            webClient.get(port, "localhost", "/health/live")
                    .send()
                    .compose(healthResponse -> {
                        ctx.verify(() -> {
                            assertEquals(200, healthResponse.statusCode(),
                                    "Health should be accessible during drain");
                            assertEquals("UP", healthResponse.bodyAsJsonObject().getString("status"));
                        });

                        // API should be rejected
                        return webClient.get(port, "localhost", "/raft/status").send();
                    })
                    .onComplete(ctx.succeeding(apiResponse -> ctx.verify(() -> {
                        assertEquals(503, apiResponse.statusCode(),
                                "API requests should be rejected during drain");
                        assertEquals("30", apiResponse.getHeader("Retry-After"),
                                "Should include Retry-After header");

                        // Cleanup
                        webClient.close();
                        apiServer.stop();
                        raftNode.stop();
                        ctx.completeNow();
                    })));
        }
    }

    @Nested
    @DisplayName("Shutdown Idempotency")
    class ShutdownIdempotencyTests {

        @Test
        @DisplayName("Multiple shutdown calls should not cause errors")
        void shouldBeIdempotent(Vertx vertx, VertxTestContext ctx) {
            ShutdownCoordinator coordinator = new ShutdownCoordinator(vertx, 1000, 5000);

            coordinator.onDrain("test-drain", () -> Future.succeededFuture());

            // First shutdown
            coordinator.shutdown()
                    .compose(v -> {
                        // Second shutdown should also succeed
                        return coordinator.shutdown();
                    })
                    .compose(v -> {
                        // Third shutdown should also succeed
                        return coordinator.shutdown();
                    })
                    .onComplete(ctx.succeeding(v -> ctx.verify(() -> {
                        assertEquals(ShutdownCoordinator.State.STOPPED, coordinator.getState());
                        ctx.completeNow();
                    })));
        }
    }
}
