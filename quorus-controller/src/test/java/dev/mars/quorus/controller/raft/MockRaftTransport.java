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

/*
 * Copyright (c) 2024 Mark Andrew Ray-Smith Cityline Ltd
  *
 * This software is the confidential and proprietary information of
 * Mark Andrew Ray-Smith Cityline Ltd ("Confidential Information").
 * You shall not disclose such Confidential Information and shall use
 * it only in accordance with the terms of the license agreement you
 * entered into with Mark Andrew Ray-Smith Cityline Ltd.
 *
 * @author Mark Andrew Ray-Smith
 * @since 2.0
 */

package dev.mars.quorus.controller.raft;

import dev.mars.quorus.controller.raft.grpc.AppendEntriesRequest;
import dev.mars.quorus.controller.raft.grpc.AppendEntriesResponse;
import dev.mars.quorus.controller.raft.grpc.VoteRequest;
import dev.mars.quorus.controller.raft.grpc.VoteResponse;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Mock implementation of RaftTransport for testing multi-node clusters.
 * Allows manual configuration of transport connections between nodes.
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-12-11
 */
public class MockRaftTransport implements RaftTransport {

    private static final Logger logger = Logger.getLogger(MockRaftTransport.class.getName());

    private final String nodeId;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile Consumer<RaftMessage> messageHandler;
    private volatile boolean running = false;
    private Map<String, MockRaftTransport> transports;
    private RaftNode raftNode;

    public MockRaftTransport(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * Set the map of all transports in the cluster.
     * This allows this transport to communicate with other nodes.
     */
    public void setTransports(Map<String, MockRaftTransport> transports) {
        this.transports = transports;
    }

    @Override
    public void setRaftNode(RaftNode node) {
        this.raftNode = node;
    }

    @Override
    public void start(Consumer<RaftMessage> messageHandler) {
        this.messageHandler = messageHandler;
        this.running = true;
        logger.info("Started mock transport for node: " + nodeId);
    }

    @Override
    public void stop() {
        this.running = false;
        logger.info("Stopped mock transport for node: " + nodeId);
    }

    public boolean isRunning() {
        return running;
    }

    public String getLocalNodeId() {
        return nodeId;
    }

    @Override
    public Future<VoteResponse> sendVoteRequest(String targetNodeId, VoteRequest request) {
        Promise<VoteResponse> promise = Promise.promise();
        
        executor.submit(() -> {
            try {
                MockRaftTransport targetTransport = transports.get(targetNodeId);
                if (targetTransport == null || !targetTransport.running) {
                    promise.fail(new RuntimeException("Target node not available: " + targetNodeId));
                    return;
                }

                // Simulate network delay
                try {
                    Thread.sleep(10 + (long) (Math.random() * 20)); // 10-30ms delay
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    promise.fail(new RuntimeException(e));
                    return;
                }

                // Process vote request
                VoteResponse response = targetTransport.handleVoteRequest(request);
                logger.fine("Vote request from " + nodeId + " to " + targetNodeId + 
                           ": " + response.getVoteGranted());
                
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
        
        executor.submit(() -> {
            try {
                MockRaftTransport targetTransport = transports.get(targetNodeId);
                if (targetTransport == null || !targetTransport.running) {
                    promise.fail(new RuntimeException("Target node not available: " + targetNodeId));
                    return;
                }

                // Simulate network delay
                try {
                    Thread.sleep(5 + (long) (Math.random() * 10)); // 5-15ms delay
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    promise.fail(new RuntimeException(e));
                    return;
                }

                // Process append entries request
                AppendEntriesResponse response = targetTransport.handleAppendEntries(request);
                logger.fine("Append entries from " + nodeId + " to " + targetNodeId + 
                           ": " + response.getSuccess());
                
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
            messageHandler.accept(new RaftMessage.Vote(request));
        }
        
        // Fallback if RaftNode is not set (should not happen in correct setup)
        boolean voteGranted = Math.random() > 0.1; 
        
        return VoteResponse.newBuilder()
                .setTerm(request.getTerm())
                .setVoteGranted(voteGranted)
                .build();
    }

    private AppendEntriesResponse handleAppendEntries(AppendEntriesRequest request) {
        if (raftNode != null) {
            return raftNode.handleAppendEntriesRequest(request).toCompletionStage().toCompletableFuture().join();
        }

        if (messageHandler != null) {
            messageHandler.accept(new RaftMessage.AppendEntries(request));
        }
        
        // Fallback if RaftNode is not set
        boolean success = Math.random() > 0.05; 
        long matchIndex = request.getPrevLogIndex() + request.getEntriesCount();
        
        return AppendEntriesResponse.newBuilder()
                .setTerm(request.getTerm())
                .setSuccess(success)
                .setMatchIndex(matchIndex)
                .build();
    }
}

