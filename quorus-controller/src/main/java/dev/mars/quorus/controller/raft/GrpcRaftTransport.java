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
import dev.mars.quorus.controller.raft.grpc.InstallSnapshotRequest;
import dev.mars.quorus.controller.raft.grpc.InstallSnapshotResponse;
import dev.mars.quorus.controller.raft.grpc.RaftServiceGrpc;
import dev.mars.quorus.controller.raft.grpc.VoteRequest;
import dev.mars.quorus.controller.raft.grpc.VoteResponse;
import dev.mars.quorus.controller.observability.RaftMetrics;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * gRPC implementation of RaftTransport using standard gRPC Netty client.
 * <p>
 * Uses a bounded ThreadPoolExecutor for gRPC callbacks instead of
 * an unbounded CachedThreadPool to prevent resource exhaustion.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0 (T3.2: Bounded Thread Pools)
 * @since 2025-12-16
 */
public class GrpcRaftTransport implements RaftTransport {

    private static final Logger logger = LoggerFactory.getLogger(GrpcRaftTransport.class);
    private static final String THREAD_NAME_PREFIX = "raft-grpc-io-";

    private final Vertx vertx;
    private final String selfId;
    private final Map<String, String> clusterNodes; // nodeId -> host:port
    private final Map<String, RaftServiceGrpc.RaftServiceFutureStub> clients = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final int poolSize;

    private RaftNode raftNode; // Circular dependency injection

    /**
     * Creates a GrpcRaftTransport with default pool configuration.
     * 
     * @param vertx Vert.x instance
     * @param selfId this node's ID
     * @param clusterNodes map of nodeId to host:port
     */
    public GrpcRaftTransport(Vertx vertx, String selfId, Map<String, String> clusterNodes) {
        this(vertx, selfId, clusterNodes, 10);
    }

    /**
     * Creates a GrpcRaftTransport with custom pool configuration.
     * 
     * @param vertx Vert.x instance
     * @param selfId this node's ID
     * @param clusterNodes map of nodeId to host:port
     * @param poolSize maximum number of worker threads for gRPC callbacks
     */
    public GrpcRaftTransport(Vertx vertx, String selfId, Map<String, String> clusterNodes,
                              int poolSize) {
        this.vertx = vertx;
        this.selfId = selfId;
        this.clusterNodes = clusterNodes;
        this.poolSize = poolSize;
        
        // Create a bounded thread pool with named threads for gRPC callbacks
        AtomicInteger threadCounter = new AtomicInteger(0);
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
                poolSize,           // core pool size
                poolSize,           // max pool size (fixed)
                60L, TimeUnit.SECONDS,  // keep-alive for idle threads
                new LinkedBlockingQueue<>(1000),  // bounded work queue
                r -> {
                    Thread t = new Thread(r, THREAD_NAME_PREFIX + selfId + "-" + threadCounter.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()  // back-pressure when queue is full
        );
        this.executor = threadPool;
        
        // Register with metrics for monitoring
        RaftMetrics.getInstance().registerThreadPool(threadPool);
        
        logger.debug("GrpcRaftTransport created with bounded ThreadPoolExecutor (poolSize={}, queueSize=1000)",
                poolSize);
    }
    
    /**
     * Gets the current pool size configuration.
     * 
     * @return the pool size
     */
    public int getPoolSize() {
        return poolSize;
    }

    public void setRaftNode(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    @Override
    public void start(Consumer<RaftMessage> messageHandler) {
        // Server side should be started in the Verticle separately (GrpcRaftServer)
        // Client side just needs to be ready
        logger.info("GrpcRaftTransport initialized for node: {} (poolSize={})", selfId, poolSize);
    }

    @Override
    public void stop() {
        // Unregister from metrics
        RaftMetrics.getInstance().unregisterThreadPool();
        
        // Clean up clients
        clients.clear();
        // Shutdown the executor gracefully
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    logger.warn("GrpcRaftTransport executor forced shutdown for node: {}", selfId);
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.debug("GrpcRaftTransport executor closed for node: {}", selfId);
        }
    }

    @Override
    public Future<VoteResponse> sendVoteRequest(String targetId, VoteRequest request) {
        return toVertxFuture(getStub(targetId).requestVote(request));
    }

    @Override
    public Future<AppendEntriesResponse> sendAppendEntries(String targetId, AppendEntriesRequest request) {
        return toVertxFuture(getStub(targetId).appendEntries(request));
    }

    @Override
    public Future<InstallSnapshotResponse> sendInstallSnapshot(String targetId, InstallSnapshotRequest request) {
        return toVertxFuture(getStub(targetId).installSnapshot(request));
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
