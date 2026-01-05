package dev.mars.quorus.controller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftTransport;
import dev.mars.quorus.controller.raft.GrpcRaftTransport;
import dev.mars.quorus.controller.raft.RaftStateMachine;
import dev.mars.quorus.controller.http.HttpApiServer;

import java.util.Collections;
import java.util.HashMap;

/**
 * Main Verticle for the Quorus Controller.
 * Initializes the reactive stack, including Raft consensus and API.
 */
public class QuorusControllerVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(QuorusControllerVerticle.class);

    private RaftTransport transport;
    private RaftNode raftNode;
    private HttpApiServer apiServer;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        logger.info("Starting QuorusControllerVerticle...");

        try {
            // 1. Configuration (Mock or Env)
            String nodeId = System.getenv().getOrDefault("NODE_ID", "controller1");
            int port = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "8080"));

            // 2. Setup Raft Transport (gRPC)
            // Ideally we parse cluster config from env, for now simplified
            this.transport = new GrpcRaftTransport(vertx, nodeId, new HashMap<>());

            // 3. Create Raft Node
            // Note: StateMachine implementation would be needed here.
            RaftStateMachine stateMachine = new RaftStateMachine() {
                public Object apply(Object command) {
                    return "OK";
                }

                public byte[] takeSnapshot() {
                    return new byte[0];
                }

                public void restoreSnapshot(byte[] data) {
                }

                public long getLastAppliedIndex() {
                    return 0;
                }

                public void setLastAppliedIndex(long index) {
                }

                public void reset() {
                }
            };

            this.raftNode = new RaftNode(vertx, nodeId, Collections.singleton(nodeId), transport, stateMachine);

            transport.setRaftNode(raftNode);

            // 4. Start Raft
            raftNode.start().onSuccess(v -> {
                // 5. Start HTTP API
                this.apiServer = new HttpApiServer(vertx, port, raftNode);

                apiServer.start()
                        .onSuccess(server -> {
                            logger.info("QuorusControllerVerticle started successfully");
                            startPromise.complete();
                        })
                        .onFailure(startPromise::fail);
            }).onFailure(e -> {
                startPromise.fail(e);
            });

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
            // Transport stop is usually synchronous or handled by raftNode logic

            logger.info("QuorusControllerVerticle stopped successfully");
            stopPromise.complete();
        } catch (Exception e) {
            logger.warn("Error during shutdown", e);
            stopPromise.complete(); // Don't fail the stop promise for cleanup errors
        }
    }
}
