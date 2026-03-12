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
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
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
    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("quorus-controller");

    private final Vertx vertx;
    private final String selfId;
    private final Map<String, String> clusterNodes; // nodeId -> host:port
    private final Map<String, RaftServiceGrpc.RaftServiceFutureStub> clients = new ConcurrentHashMap<>();
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final int poolSize;
    private final int queueSize;

    private RaftNode raftNode; // Circular dependency injection

    /**
     * Creates a GrpcRaftTransport with default pool configuration.
     * 
     * @param vertx Vert.x instance
     * @param selfId this node's ID
     * @param clusterNodes map of nodeId to host:port
     */
    public GrpcRaftTransport(Vertx vertx, String selfId, Map<String, String> clusterNodes) {
        this(vertx, selfId, clusterNodes, 10, 1000);
    }

    /**
     * Creates a GrpcRaftTransport with custom pool configuration.
     * 
     * @param vertx Vert.x instance
     * @param selfId this node's ID
     * @param clusterNodes map of nodeId to host:port
     * @param poolSize maximum number of worker threads for gRPC callbacks
     * @param queueSize maximum number of queued tasks before back-pressure
     */
    public GrpcRaftTransport(Vertx vertx, String selfId, Map<String, String> clusterNodes,
                              int poolSize, int queueSize) {
        this.vertx = vertx;
        this.selfId = selfId;
        this.clusterNodes = clusterNodes;
        this.poolSize = poolSize;
        this.queueSize = queueSize;
        
        // Create a bounded thread pool with named threads for gRPC callbacks
        AtomicInteger threadCounter = new AtomicInteger(0);
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
                poolSize,           // core pool size
                poolSize,           // max pool size (fixed)
                60L, TimeUnit.SECONDS,  // keep-alive for idle threads
                new LinkedBlockingQueue<>(queueSize),  // bounded work queue
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
        
        logger.debug("GrpcRaftTransport created with bounded ThreadPoolExecutor (poolSize={}, queueSize={})",
                poolSize, queueSize);
    }
    
    /**
     * Gets the current pool size configuration.
     * 
     * @return the pool size
     */
    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Gets the current queue size configuration.
     * 
     * @return the queue size
     */
    public int getQueueSize() {
        return queueSize;
    }

    public void setRaftNode(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    @Override
    public void start(Consumer<RaftMessage> messageHandler) {
        // Server side should be started in the Verticle separately (GrpcRaftServer)
        // Client side just needs to be ready
        logger.info("GrpcRaftTransport initialized for node: {} (poolSize={}, queueSize={})", selfId, poolSize, queueSize);
    }

    @Override
    public void stop() {
        // Unregister from metrics
        RaftMetrics.getInstance().unregisterThreadPool();
        
        // Clean up stubs immediately; channel and executor shutdown is handled asynchronously below.
        clients.clear();

        // Avoid blocking the Vert.x event loop during shutdown.
        vertx.executeBlocking(() -> {
            for (ManagedChannel channel : channels.values()) {
                channel.shutdown();
            }

            for (Map.Entry<String, ManagedChannel> entry : channels.entrySet()) {
                ManagedChannel channel = entry.getValue();
                try {
                    if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                        channel.shutdownNow();
                        logger.warn("Forced gRPC channel shutdown for peer: {}", entry.getKey());
                    }
                } catch (InterruptedException e) {
                    channel.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            channels.clear();

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

            return null;
        }, false).onFailure(err -> {
            logger.warn("Error during GrpcRaftTransport shutdown: {}", err.getMessage());
            logger.debug("Stack trace for GrpcRaftTransport shutdown failure", err);
        });
    }

    @Override
    public Future<VoteResponse> sendVoteRequest(String targetId, VoteRequest request) {
        Span span = tracer.spanBuilder("raft.RequestVote")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("rpc.system", "grpc")
                .setAttribute("rpc.method", "RequestVote")
                .setAttribute("raft.target", targetId)
                .setAttribute("raft.term", request.getTerm())
                .setAttribute("raft.candidate", request.getCandidateId())
                .startSpan();
        return toVertxFuture(getStub(targetId).requestVote(request))
                .onSuccess(r -> {
                    span.setAttribute("raft.vote_granted", r.getVoteGranted());
                    span.end();
                })
                .onFailure(e -> {
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    span.recordException(e);
                    span.end();
                });
    }

    @Override
    public Future<AppendEntriesResponse> sendAppendEntries(String targetId, AppendEntriesRequest request) {
        Span span = tracer.spanBuilder("raft.AppendEntries")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("rpc.system", "grpc")
                .setAttribute("rpc.method", "AppendEntries")
                .setAttribute("raft.target", targetId)
                .setAttribute("raft.term", request.getTerm())
                .setAttribute("raft.entries_count", request.getEntriesCount())
                .startSpan();
        return toVertxFuture(getStub(targetId).appendEntries(request))
                .onSuccess(r -> {
                    span.setAttribute("raft.success", r.getSuccess());
                    span.end();
                })
                .onFailure(e -> {
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    span.recordException(e);
                    span.end();
                });
    }

    @Override
    public Future<InstallSnapshotResponse> sendInstallSnapshot(String targetId, InstallSnapshotRequest request) {
        Span span = tracer.spanBuilder("raft.InstallSnapshot")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("rpc.system", "grpc")
                .setAttribute("rpc.method", "InstallSnapshot")
                .setAttribute("raft.target", targetId)
                .setAttribute("raft.term", request.getTerm())
                .startSpan();
        return toVertxFuture(getStub(targetId).installSnapshot(request))
                .onSuccess(r -> {
                    span.setAttribute("raft.success", r.getSuccess());
                    span.end();
                })
                .onFailure(e -> {
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    span.recordException(e);
                    span.end();
                });
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
            channels.put(id, channel);
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
