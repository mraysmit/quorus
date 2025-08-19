/*
 * Copyright 2024 Quorus Project
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

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Transport interface for Raft consensus communication.
 * Handles network communication between Raft nodes.
 */
public interface RaftTransport {

    /**
     * Start the transport layer.
     * 
     * @param messageHandler Handler for incoming messages
     */
    void start(Consumer<Object> messageHandler);

    /**
     * Stop the transport layer.
     */
    void stop();

    /**
     * Send a vote request to another node.
     * 
     * @param nodeId Target node ID
     * @param request Vote request
     * @return Future containing the vote response
     */
    CompletableFuture<VoteResponse> sendVoteRequest(String nodeId, VoteRequest request);

    /**
     * Send an append entries request to another node.
     * 
     * @param nodeId Target node ID
     * @param request Append entries request
     * @return Future containing the append entries response
     */
    CompletableFuture<AppendEntriesResponse> sendAppendEntries(String nodeId, AppendEntriesRequest request);

    /**
     * Get the local node ID.
     */
    String getLocalNodeId();

    /**
     * Check if the transport is running.
     */
    boolean isRunning();
}
