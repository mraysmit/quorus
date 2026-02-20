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

package dev.mars.quorus.controller.raft;

import dev.mars.quorus.controller.raft.grpc.AppendEntriesRequest;
import dev.mars.quorus.controller.raft.grpc.AppendEntriesResponse;
import dev.mars.quorus.controller.raft.grpc.InstallSnapshotRequest;
import dev.mars.quorus.controller.raft.grpc.InstallSnapshotResponse;
import dev.mars.quorus.controller.raft.grpc.RaftServiceGrpc;
import dev.mars.quorus.controller.raft.grpc.VoteRequest;
import dev.mars.quorus.controller.raft.grpc.VoteResponse;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * gRPC server implementation for Raft inter-node communication.
 * Handles incoming RequestVote and AppendEntries RPC calls from peer nodes.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-01-08
 */
public class GrpcRaftServer {

    private static final Logger logger = LoggerFactory.getLogger(GrpcRaftServer.class);

    private final Vertx vertx;
    private final int port;
    private final RaftNode raftNode;
    private Server server;

    public GrpcRaftServer(Vertx vertx, int port, RaftNode raftNode) {
        this.vertx = vertx;
        this.port = port;
        this.raftNode = raftNode;
    }

    /**
     * Start the gRPC server.
     * 
     * @return Future that completes when server is started
     */
    public Future<Void> start() {
        Promise<Void> promise = Promise.promise();

        vertx.executeBlocking(() -> {
            try {
                server = ServerBuilder.forPort(port)
                        .addService(new RaftServiceImpl())
                        .build()
                        .start();
                logger.info("gRPC Raft server started on port {}", port);
                return null;
            } catch (IOException e) {
                throw new RuntimeException("Failed to start gRPC server on port " + port, e);
            }
        }).onSuccess(v -> promise.complete())
          .onFailure(promise::fail);

        return promise.future();
    }

    /**
     * Stop the gRPC server gracefully.
     * 
     * @return Future that completes when server is stopped
     */
    public Future<Void> stop() {
        Promise<Void> promise = Promise.promise();

        if (server == null) {
            promise.complete();
            return promise.future();
        }

        vertx.executeBlocking(() -> {
            try {
                server.shutdown();
                if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                    if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                        logger.warn("gRPC server did not terminate cleanly");
                    }
                }
                logger.info("gRPC Raft server stopped");
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                server.shutdownNow();
                throw new RuntimeException("Interrupted while stopping gRPC server", e);
            }
        }).onSuccess(v -> promise.complete())
          .onFailure(promise::fail);

        return promise.future();
    }

    /**
     * Implementation of the RaftService gRPC service.
     * Delegates all calls to the RaftNode's handlers.
     */
    private class RaftServiceImpl extends RaftServiceGrpc.RaftServiceImplBase {

        @Override
        public void requestVote(VoteRequest request, StreamObserver<VoteResponse> responseObserver) {
            logger.debug("Received RequestVote from {} for term {}", 
                    request.getCandidateId(), request.getTerm());

            raftNode.handleVoteRequest(request)
                    .onSuccess(response -> {
                        logger.debug("Responding to RequestVote: granted={}, term={}", 
                                response.getVoteGranted(), response.getTerm());
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                    })
                    .onFailure(e -> {
                        logger.error("Error handling RequestVote: {}", e.getMessage());
                        logger.debug("Stack trace for RequestVote handling error", e);
                        responseObserver.onError(e);
                    });
        }

        @Override
        public void appendEntries(AppendEntriesRequest request, StreamObserver<AppendEntriesResponse> responseObserver) {
            logger.debug("Received AppendEntries from {} for term {}, entries={}", 
                    request.getLeaderId(), request.getTerm(), request.getEntriesCount());

            raftNode.handleAppendEntriesRequest(request)
                    .onSuccess(response -> {
                        logger.debug("Responding to AppendEntries: success={}, term={}", 
                                response.getSuccess(), response.getTerm());
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                    })
                    .onFailure(e -> {
                        logger.error("Error handling AppendEntries: {}", e.getMessage());
                        logger.debug("Stack trace for AppendEntries handling error", e);
                        responseObserver.onError(e);
                    });
        }

        @Override
        public void installSnapshot(InstallSnapshotRequest request, StreamObserver<InstallSnapshotResponse> responseObserver) {
            logger.debug("Received InstallSnapshot from {} for term {}, lastIncludedIndex={}, chunk {}/{}",
                    request.getLeaderId(), request.getTerm(),
                    request.getLastIncludedIndex(), request.getChunkIndex() + 1, request.getTotalChunks());

            raftNode.handleInstallSnapshot(request)
                    .onSuccess(response -> {
                        logger.debug("Responding to InstallSnapshot: success={}, term={}",
                                response.getSuccess(), response.getTerm());
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                    })
                    .onFailure(e -> {
                        logger.error("Error handling InstallSnapshot: {}", e.getMessage());
                        logger.debug("Stack trace for InstallSnapshot handling error", e);
                        responseObserver.onError(e);
                    });
        }
    }
}
