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

package dev.mars.quorus.controller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftTransport;
import dev.mars.quorus.controller.raft.GrpcRaftTransport;
import dev.mars.quorus.controller.raft.GrpcRaftServer;
import dev.mars.quorus.controller.state.QuorusStateMachine;
import dev.mars.quorus.controller.http.HttpApiServer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Main Verticle for the Quorus Controller.
 * Initializes the reactive stack, including Raft consensus and API.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-12-16
 */
public class QuorusControllerVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(QuorusControllerVerticle.class);

    private RaftTransport transport;
    private RaftNode raftNode;
    private HttpApiServer apiServer;
    private GrpcRaftServer grpcServer;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        logger.info("Starting QuorusControllerVerticle...");

        try {
            // 1. Configuration (Mock or Env)
            String nodeId = System.getenv().getOrDefault("NODE_ID", "controller1");
            int port = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "8080"));
            int raftPort = Integer.parseInt(System.getenv().getOrDefault("RAFT_PORT", "9080"));
            String clusterNodesEnv = System.getenv().getOrDefault("CLUSTER_NODES", nodeId + "=localhost:9080");

            // 2. Parse cluster configuration
            Map<String, String> peerAddresses = new HashMap<>();
            Set<String> clusterNodeIds = new HashSet<>();
            for (String entry : clusterNodesEnv.split(",")) {
                String[] parts = entry.trim().split("=");
                if (parts.length == 2) {
                    String peerNodeId = parts[0].trim();
                    String peerAddress = parts[1].trim();
                    clusterNodeIds.add(peerNodeId);
                    if (!peerNodeId.equals(nodeId)) {
                        peerAddresses.put(peerNodeId, peerAddress);
                    }
                }
            }
            logger.info("Cluster configuration: nodeId={}, peers={}", nodeId, peerAddresses);

            // 3. Setup Raft Transport (gRPC)
            this.transport = new GrpcRaftTransport(vertx, nodeId, peerAddresses);

            // 4. Create Raft Node
            Map<String, String> initialMetadata = new HashMap<>();
            initialMetadata.put("version", System.getenv().getOrDefault("QUORUS_VERSION", "2.0-ext"));

            QuorusStateMachine stateMachine = new QuorusStateMachine(initialMetadata);

            this.raftNode = new RaftNode(vertx, nodeId, clusterNodeIds, transport, stateMachine);

            transport.setRaftNode(raftNode);

            // 5. Create and start gRPC server for inter-node communication
            this.grpcServer = new GrpcRaftServer(vertx, raftPort, raftNode);

            grpcServer.start().onSuccess(v1 -> {
                logger.info("gRPC server started on port {}", raftPort);

                // 6. Start Raft
                raftNode.start().onSuccess(v2 -> {
                    // 7. Start HTTP API
                    this.apiServer = new HttpApiServer(vertx, port, raftNode);

                    apiServer.start()
                            .onSuccess(server -> {
                                logger.info("QuorusControllerVerticle started successfully");
                                startPromise.complete();
                            })
                            .onFailure(startPromise::fail);
                }).onFailure(startPromise::fail);
            }).onFailure(startPromise::fail);

        } catch (Exception e) {
            startPromise.fail(e);
        }
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
        logger.info("Stopping QuorusControllerVerticle...");

        try {
            if (apiServer != null) {
                apiServer.stop(); // Returns Future, we should ideally wait for it
            }
            if (raftNode != null) {
                raftNode.stop();
            }
            if (grpcServer != null) {
                grpcServer.stop();
            }
            // Transport stop is usually synchronous or handled by raftNode logic

            logger.info("QuorusControllerVerticle stopped successfully");
            stopPromise.complete();
        } catch (Exception e) {
            logger.warn("Error during shutdown", e);
            stopPromise.complete(); // Don't fail the stop promise for cleanup errors
        }
    }
}
