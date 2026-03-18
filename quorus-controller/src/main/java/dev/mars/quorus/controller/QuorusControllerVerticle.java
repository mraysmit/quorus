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

import dev.mars.quorus.controller.config.AppConfig;
import dev.mars.quorus.controller.lifecycle.ShutdownCoordinator;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftNodeMode;
import dev.mars.quorus.controller.raft.RaftTransport;
import dev.mars.quorus.controller.raft.GrpcRaftTransport;
import dev.mars.quorus.controller.raft.GrpcRaftServer;
import dev.mars.quorus.controller.raft.storage.RaftStorage;
import dev.mars.quorus.controller.raft.storage.RaftStorageFactory;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.http.HttpApiServer;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
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
    private Optional<RaftNode> raftNode = Optional.empty();
    private RaftStorage raftStorage;
    private Optional<HttpApiServer> apiServer = Optional.empty();
    private Optional<GrpcRaftServer> grpcServer = Optional.empty();
    private Optional<ShutdownCoordinator> shutdownCoordinator = Optional.empty();

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        logger.info("Starting QuorusControllerVerticle...");

        try {
            // 1. Load configuration
            AppConfig config = AppConfig.get();
            String nodeId = config.getNodeId();

            // Set process-lifetime MDC context for all controller logs
            MDC.put("nodeId", nodeId);

            int port = config.getHttpPort();
            int raftPort = config.getRaftPort();
            String clusterNodesEnv = config.getClusterNodes();

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
            int raftPoolSize = config.getRaftIoPoolSize();
            int raftQueueSize = config.getRaftIoQueueSize();
            this.transport = new GrpcRaftTransport(vertx, nodeId, peerAddresses, raftPoolSize, raftQueueSize);

            // 4. Create Raft Storage (WAL)
            String storageType = config.getRaftStorageType();
            Path storagePath = Path.of(config.getRaftStoragePath());
            boolean fsyncEnabled = config.getRaftStorageFsync();
            
            logger.info("Initializing Raft storage: type={}, path={}, fsync={}", 
                       storageType, storagePath, fsyncEnabled);

            // Create storage via factory
            RaftStorageFactory.create(vertx, storageType, storagePath, fsyncEnabled)
                .onSuccess(storage -> {
                    this.raftStorage = storage;
                    continueStartup(startPromise, config, nodeId, port, raftPort, clusterNodeIds);
                })
                .onFailure(err -> {
                    logger.error("Failed to initialize Raft storage: {}", err.getMessage());
                    logger.debug("Stack trace for Raft storage initialization failure", err);
                    startPromise.fail(err);
                });

        } catch (Exception e) {
            startPromise.fail(e);
        }
    }

    /**
     * Continues the startup sequence after storage is initialized.
     */
    private void continueStartup(Promise<Void> startPromise, AppConfig config, 
                                 String nodeId, int port, int raftPort, 
                                 Set<String> clusterNodeIds) {
        try {
            // 5. Create Raft Node with storage
            Map<String, String> initialMetadata = new HashMap<>();
            initialMetadata.put("version", config.getVersion());

            QuorusStateStore stateMachine = new QuorusStateStore(initialMetadata);

            // Use the builder with storage and snapshot configuration
            RaftNode node = RaftNode.builder()
                    .vertx(vertx)
                    .nodeId(nodeId)
                    .clusterNodes(clusterNodeIds)
                    .transport(transport)
                    .stateMachine(stateMachine)
                    .mode(RaftNodeMode.durable(raftStorage))
                    .electionTimeout(5000)
                    .heartbeatInterval(1000)
                    .snapshotEnabled(config.isSnapshotEnabled())
                    .snapshotThreshold(config.getSnapshotThreshold())
                    .snapshotCheckInterval(config.getSnapshotCheckIntervalMs())
                    .logHardLimit(config.getLogHardLimit())
                    .build();
            this.raftNode = Optional.of(node);

            transport.setRaftNode(node);

            // 6. Create and start gRPC server for inter-node communication
            GrpcRaftServer grpc = new GrpcRaftServer(vertx, raftPort, node);
            this.grpcServer = Optional.of(grpc);

            grpc.start().onSuccess(v1 -> {
                logger.info("gRPC server started on port {}", raftPort);

                // 7. Start Raft (includes recovery from WAL)
                node.start().onSuccess(v2 -> {
                    // 8. Start HTTP API
                    HttpApiServer api = new HttpApiServer(vertx, port, node, stateMachine);
                    this.apiServer = Optional.of(api);

                    api.start()
                            .onSuccess(server -> {
                                // 9. Setup shutdown coordinator for graceful shutdown
                                setupShutdownCoordinator();
                                
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

    /**
     * Configures the shutdown coordinator with graceful shutdown hooks.
     * 
     * <p>Shutdown sequence:
     * <ol>
     *   <li>DRAIN: Stop accepting new HTTP requests</li>
     *   <li>AWAIT: Wait for any active operations to complete</li>
     *   <li>STOP_SERVICES: Stop HTTP server, Raft node, gRPC server</li>
     *   <li>CLOSE_RESOURCES: Close storage and other resources</li>
     * </ol>
     */
    private void setupShutdownCoordinator() {
        AppConfig config = AppConfig.get();
        long drainTimeoutMs = config.getLong("quorus.shutdown.drain.timeout.ms", 5000L);
        long shutdownTimeoutMs = config.getLong("quorus.shutdown.timeout.ms", 30000L);
        
        ShutdownCoordinator coordinator = new ShutdownCoordinator(vertx, drainTimeoutMs, shutdownTimeoutMs);
        this.shutdownCoordinator = Optional.of(coordinator);
        
        // Stop accepting new requests
        coordinator.onDrain("http-api-drain", () -> 
            apiServer.map(HttpApiServer::enterDrainMode).orElseGet(Future::succeededFuture)
        );
        
        // Phase 2: AWAIT_COMPLETION - No active jobs tracked at controller level yet
        // (Agents track their own transfers - controller just routes requests)
        
        // Phase 3: STOP_SERVICES - Stop in reverse order of startup
        coordinator.onServiceStop("http-api-stop", () -> 
            apiServer.map(HttpApiServer::stop).orElseGet(Future::succeededFuture)
        );
        
        coordinator.onServiceStop("raft-node-stop", () -> {
            return raftNode.map(RaftNode::stop).orElseGet(Future::succeededFuture);
        });
        
        coordinator.onServiceStop("grpc-server-stop", () -> {
            return grpcServer.map(GrpcRaftServer::stop).orElseGet(Future::succeededFuture);
        });
        
        // Phase 4: CLOSE_RESOURCES - Storage is closed by raftNode.stop()
        
        logger.info("Shutdown coordinator configured (drain={}ms, timeout={}ms)", 
                   drainTimeoutMs, shutdownTimeoutMs);
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
        logger.info("Stopping QuorusControllerVerticle...");

        shutdownCoordinator.ifPresentOrElse(
            coordinator -> coordinator.shutdown()
                    .onSuccess(v -> {
                        logger.info("QuorusControllerVerticle stopped successfully (graceful)");
                        stopPromise.complete();
                    })
                    .onFailure(err -> {
                        logger.warn("Error during graceful shutdown: {}", err.getMessage());
                        logger.debug("Stack trace for graceful shutdown failure", err);
                        // Still complete - we tried our best
                        stopPromise.complete();
                    }),
            () -> {
                // Fallback to immediate shutdown if coordinator wasn't initialized
                try {
                    Future<Void> apiStop = apiServer.map(HttpApiServer::stop).orElseGet(Future::succeededFuture);
                    Future<Void> raftStop = raftNode.map(RaftNode::stop).orElseGet(Future::succeededFuture);
                    Future<Void> grpcStop = grpcServer.map(GrpcRaftServer::stop).orElseGet(Future::succeededFuture);

                    Future.all(apiStop, raftStop, grpcStop)
                            .onSuccess(v -> {
                                logger.info("QuorusControllerVerticle stopped successfully (immediate)");
                                stopPromise.complete();
                            })
                            .onFailure(err -> {
                                logger.warn("Error during immediate shutdown: {}", err.getMessage());
                                logger.debug("Stack trace for immediate shutdown failure", err);
                                stopPromise.complete();
                            });
                } catch (Exception e) {
                    logger.warn("Error during shutdown: {}", e.getMessage());
                    logger.debug("Stack trace for shutdown error", e);
                    stopPromise.complete();
                }
            }
        );
    }
}
