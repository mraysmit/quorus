/*
 * Copyright (c) 2024 Mark Andrew Ray-Smith Cityline Ltd
 * All rights reserved.
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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Mock implementation of RaftTransport for testing multi-node clusters.
 * Allows manual configuration of transport connections between nodes.
 */
public class MockRaftTransport implements RaftTransport {

    private static final Logger logger = Logger.getLogger(MockRaftTransport.class.getName());

    private final String nodeId;
    private final Executor executor = Executors.newCachedThreadPool();
    private volatile Consumer<Object> messageHandler;
    private volatile boolean running = false;
    private Map<String, MockRaftTransport> transports;

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
    public void start(Consumer<Object> messageHandler) {
        this.messageHandler = messageHandler;
        this.running = true;
        logger.info("Started mock transport for node: " + nodeId);
    }

    @Override
    public void stop() {
        this.running = false;
        logger.info("Stopped mock transport for node: " + nodeId);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public String getLocalNodeId() {
        return nodeId;
    }

    @Override
    public CompletableFuture<VoteResponse> sendVoteRequest(String targetNodeId, VoteRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            MockRaftTransport targetTransport = transports.get(targetNodeId);
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
                       ": " + response.isVoteGranted());
            
            return response;
        }, executor);
    }

    @Override
    public CompletableFuture<AppendEntriesResponse> sendAppendEntries(String targetNodeId, 
                                                                     AppendEntriesRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            MockRaftTransport targetTransport = transports.get(targetNodeId);
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
                       ": " + response.isSuccess());
            
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
        
        return new VoteResponse(request.getTerm(), voteGranted, nodeId);
    }

    private AppendEntriesResponse handleAppendEntries(AppendEntriesRequest request) {
        if (messageHandler != null) {
            messageHandler.accept(request);
        }
        
        // Simple success logic for testing
        // In a real implementation, this would be handled by the RaftNode
        boolean success = Math.random() > 0.05; // 95% success rate for testing
        long matchIndex = request.getPrevLogIndex() + 
                         (request.getEntries() != null ? request.getEntries().size() : 0);
        
        return new AppendEntriesResponse(request.getTerm(), success, nodeId, matchIndex);
    }
}

