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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dev.mars.quorus.controller.raft.grpc.AppendEntriesRequest;
import dev.mars.quorus.controller.raft.grpc.AppendEntriesResponse;
import dev.mars.quorus.controller.raft.grpc.RaftServiceGrpc;
import dev.mars.quorus.controller.raft.grpc.VoteRequest;
import dev.mars.quorus.controller.raft.grpc.VoteResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * gRPC implementation of RaftTransport using standard gRPC Netty client.
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-12-16
 */
public class GrpcRaftTransport implements RaftTransport {

    private static final Logger logger = LoggerFactory.getLogger(GrpcRaftTransport.class);

    private final Vertx vertx;
    private final String selfId;
    private final Map<String, String> clusterNodes; // nodeId -> host:port
    private final Map<String, RaftServiceGrpc.RaftServiceFutureStub> clients = new ConcurrentHashMap<>();
    private final Executor executor = Executors.newCachedThreadPool();

    private RaftNode raftNode; // Circular dependency injection

    public GrpcRaftTransport(Vertx vertx, String selfId, Map<String, String> clusterNodes) {
        this.vertx = vertx;
        this.selfId = selfId;
        this.clusterNodes = clusterNodes;
    }

    public void setRaftNode(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    @Override
    public void start(Consumer<RaftMessage> messageHandler) {
        // Server side should be started in the Verticle separately (GrpcRaftServer)
        // Client side just needs to be ready
        logger.info("GrpcRaftTransport initialized for node: {}", selfId);
    }

    @Override
    public void stop() {
        // Clean up clients
        clients.clear();
    }

    @Override
    public Future<VoteResponse> sendVoteRequest(String targetId, VoteRequest request) {
        return toVertxFuture(getStub(targetId).requestVote(request));
    }

    @Override
    public Future<AppendEntriesResponse> sendAppendEntries(String targetId, AppendEntriesRequest request) {
        return toVertxFuture(getStub(targetId).appendEntries(request));
    }

    private RaftServiceGrpc.RaftServiceFutureStub getStub(String targetId) {
        return clients.computeIfAbsent(targetId, id -> {
            String addr = clusterNodes.get(id);
            if (addr == null) {
                throw new IllegalArgumentException("Unknown node: " + id);
            }
            String[] parts = addr.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .build();
            return RaftServiceGrpc.newFutureStub(channel);
        });
    }

    private <T> Future<T> toVertxFuture(ListenableFuture<T> listenableFuture) {
        Promise<T> promise = Promise.promise();
        Futures.addCallback(listenableFuture, new FutureCallback<T>() {
            @Override
            public void onSuccess(T result) {
                vertx.runOnContext(v -> promise.complete(result));
            }

            @Override
            public void onFailure(Throwable t) {
                vertx.runOnContext(v -> promise.fail(t));
            }
        }, executor);
        return promise.future();
    }
}
