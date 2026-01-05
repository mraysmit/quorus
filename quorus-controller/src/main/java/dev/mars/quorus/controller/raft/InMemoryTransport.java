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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class InMemoryTransport implements RaftTransport {

    private static final Logger logger = Logger.getLogger(InMemoryTransport.class.getName());

    // Global registry of all transport instances
    private static final Map<String, InMemoryTransport> transports = new ConcurrentHashMap<>();

    private final String nodeId;
    private final Executor executor = Executors.newCachedThreadPool();
    private volatile Consumer<Object> messageHandler;
    private volatile boolean running = false;

    public InMemoryTransport(String nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public void start(Consumer<Object> messageHandler) {
        this.messageHandler = messageHandler;
        this.running = true;
        transports.put(nodeId, this);
        logger.info("Started in-memory transport for node: " + nodeId);
    }

    @Override
    public void stop() {
        this.running = false;
        transports.remove(nodeId);
        logger.info("Stopped in-memory transport for node: " + nodeId);
    }

    @Override
    public CompletableFuture<VoteResponse> sendVoteRequest(String targetNodeId, VoteRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            InMemoryTransport targetTransport = transports.get(targetNodeId);
            if (targetTransport == null || !targetTransport.running) {
                throw new RuntimeException("Target node not available: " + targetNodeId);
            }

            // Simulate network delay
            try {
                Thread.sleep(10 + (long) (Math.random() * 20)); // 10-30ms delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }

            // Process vote request
            VoteResponse response = targetTransport.handleVoteRequest(request);
            logger.fine("Vote request from " + nodeId + " to " + targetNodeId + 
                       ": " + response.getVoteGranted());
            
            return response;
        }, executor);
    }

    @Override
    public CompletableFuture<AppendEntriesResponse> sendAppendEntries(String targetNodeId, 
                                                                     AppendEntriesRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            InMemoryTransport targetTransport = transports.get(targetNodeId);
            if (targetTransport == null || !targetTransport.running) {
                throw new RuntimeException("Target node not available: " + targetNodeId);
            }

            // Simulate network delay
            try {
                Thread.sleep(5 + (long) (Math.random() * 10)); // 5-15ms delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }

            // Process append entries request
            AppendEntriesResponse response = targetTransport.handleAppendEntries(request);
            logger.fine("Append entries from " + nodeId + " to " + targetNodeId + 
                       ": " + response.getSuccess());
            
            return response;
        }, executor);
    }

    private VoteResponse handleVoteRequest(VoteRequest request) {
        if (messageHandler != null) {
            messageHandler.accept(request);
        }
        
        // Simple vote granting logic for testing
        // In a real implementation, this would be handled by the RaftNode
        boolean voteGranted = Math.random() > 0.1; // Grant 90% of votes for testing
        
        return VoteResponse.newBuilder()
                .setTerm(request.getTerm())
                .setVoteGranted(voteGranted)
                .build();
    }

    private AppendEntriesResponse handleAppendEntries(AppendEntriesRequest request) {
        if (messageHandler != null) {
            messageHandler.accept(request);
        }
        
        // Simple success logic for testing
        // In a real implementation, this would be handled by the RaftNode
        boolean success = Math.random() > 0.05; // 95% success rate for testing
        long matchIndex = request.getPrevLogIndex() + 
                         (request.getEntriesCount());
        
        return AppendEntriesResponse.newBuilder()
                .setTerm(request.getTerm())
                .setSuccess(success)
                .setMatchIndex(matchIndex)
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
