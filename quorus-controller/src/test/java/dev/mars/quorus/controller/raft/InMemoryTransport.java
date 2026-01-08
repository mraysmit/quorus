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
import dev.mars.quorus.controller.raft.grpc.VoteRequest;
import dev.mars.quorus.controller.raft.grpc.VoteResponse;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
/**
 * Description for InMemoryTransport
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-01-05
 */

public class InMemoryTransport implements RaftTransport {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryTransport.class);

    // Global registry of all transport instances
    private static final Map<String, InMemoryTransport> transports = new ConcurrentHashMap<>();

    private final String nodeId;
    private final Executor executor = Executors.newCachedThreadPool();
    private volatile Consumer<Object> messageHandler;
    private volatile boolean running = false;
    private RaftNode raftNode;
    
    // Chaos Configuration
    private final Random random = new Random();
    private volatile int minLatencyMs = 5;
    private volatile int maxLatencyMs = 15;
    private volatile double dropRate = 0.0; // 0.0 to 1.0

    public InMemoryTransport(String nodeId) {
        this.nodeId = nodeId;
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void setRaftNode(RaftNode node) {
        this.raftNode = node;
    }

    /**
     * Configure chaos parameters for this transport.
     * @param minLatencyMs minimum latency in milliseconds
     * @param maxLatencyMs maximum latency in milliseconds
     * @param dropRate probability of dropping a packet (0.0 to 1.0)
     */
    public void setChaosConfig(int minLatencyMs, int maxLatencyMs, double dropRate) {
        this.minLatencyMs = minLatencyMs;
        this.maxLatencyMs = maxLatencyMs;
        this.dropRate = dropRate;
    }

    @Override
    public void start(Consumer<Object> messageHandler) {
        this.messageHandler = messageHandler;
        this.running = true;
        transports.put(nodeId, this);
        logger.info("Started in-memory transport for node: {}", nodeId);
    }

    @Override
    public void stop() {
        this.running = false;
        transports.remove(nodeId);
        logger.info("Stopped in-memory transport for node: {}", nodeId);
    }

    @Override
    public Future<VoteResponse> sendVoteRequest(String targetNodeId, VoteRequest request) {
        Promise<VoteResponse> promise = Promise.promise();
        executor.execute(() -> {
            try {
                // Simulate Packet Drop
                if (dropRate > 0 && random.nextDouble() < dropRate) {
                    logger.debug("Dropped VoteRequest from {} to {}", nodeId, targetNodeId);
                    promise.fail(new RuntimeException("Network packet dropped (Chaos)"));
                    return;
                }

                InMemoryTransport targetTransport = transports.get(targetNodeId);
                if (targetTransport == null || !targetTransport.running) {
                    promise.fail(new RuntimeException("Target node not available: " + targetNodeId));
                    return;
                }

                // Simulate network delay
                try {
                    long delay = minLatencyMs + random.nextInt(Math.max(1, maxLatencyMs - minLatencyMs + 1));
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    promise.fail(new RuntimeException(e));
                    return;
                }

                // Process vote request
                VoteResponse response = targetTransport.handleVoteRequest(request);
                logger.debug("Vote request from {} to {}: {}", nodeId, targetNodeId, response.getVoteGranted());
                
                promise.complete(response);
            } catch (Exception e) {
                promise.fail(e);
            }
        });
        return promise.future();
    }

    @Override
    public Future<AppendEntriesResponse> sendAppendEntries(String targetNodeId, 
                                                                     AppendEntriesRequest request) {
        Promise<AppendEntriesResponse> promise = Promise.promise();
        executor.execute(() -> {
            try {
                // Simulate Packet Drop
                if (dropRate > 0 && random.nextDouble() < dropRate) {
                    logger.debug("Dropped AppendEntries from {} to {}", nodeId, targetNodeId);
                    promise.fail(new RuntimeException("Network packet dropped (Chaos)"));
                    return;
                }

                InMemoryTransport targetTransport = transports.get(targetNodeId);
                if (targetTransport == null || !targetTransport.running) {
                    promise.fail(new RuntimeException("Target node not available: " + targetNodeId));
                    return;
                }

                // Simulate network delay
                try {
                    long delay = minLatencyMs + random.nextInt(Math.max(1, maxLatencyMs - minLatencyMs + 1));
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    promise.fail(new RuntimeException(e));
                    return;
                }

                // Process append entries request
                AppendEntriesResponse response = targetTransport.handleAppendEntries(request);
                logger.debug("Append entries from {} to {}: {}", nodeId, targetNodeId, response.getSuccess());
                
                promise.complete(response);
            } catch (Exception e) {
                promise.fail(e);
            }
        });
        return promise.future();
    }


    private VoteResponse handleVoteRequest(VoteRequest request) {
        if (raftNode != null) {
            return raftNode.handleVoteRequest(request).toCompletionStage().toCompletableFuture().join();
        }
        
        if (messageHandler != null) {
            messageHandler.accept(request);
        }
        
        logger.warn("RaftNode not set for transport {}, returning failure", nodeId);
        return VoteResponse.newBuilder()
                .setTerm(request.getTerm())
                .setVoteGranted(false)
                .build();
    }

    private AppendEntriesResponse handleAppendEntries(AppendEntriesRequest request) {
        if (raftNode != null) {
            return raftNode.handleAppendEntriesRequest(request).toCompletionStage().toCompletableFuture().join();
        }

        if (messageHandler != null) {
            messageHandler.accept(request);
        }
        
        logger.warn("RaftNode not set for transport {}, returning failure", nodeId);
        return AppendEntriesResponse.newBuilder()
                .setTerm(request.getTerm())
                .setSuccess(false)
                .build();
    }

    public static Map<String, InMemoryTransport> getAllTransports() {
        return new ConcurrentHashMap<>(transports);
    }

    /**
     * Clear all registered transports (for testing).
     */
    public static void clearAllTransports() {
        transports.clear();
    }
}
