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

import java.util.function.Consumer;

/**
 * Interface for Raft transport layer.
 * 
 * <p>Implementations handle network communication between Raft nodes,
 * supporting both request-response RPCs and message-based notifications.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2025-08-20
 */
public interface RaftTransport {

    /**
     * Starts the transport with a type-safe message handler.
     * 
     * @param messageHandler consumer for incoming {@link RaftMessage} instances
     */
    void start(Consumer<RaftMessage> messageHandler);

    void stop();

    Future<VoteResponse> sendVoteRequest(String targetId, VoteRequest request);

    Future<AppendEntriesResponse> sendAppendEntries(String targetId, AppendEntriesRequest request);

    /**
     * Sets the RaftNode reference for direct method invocation.
     * <p>This allows transports to bypass the message handler and call
     * RaftNode methods directly for request-response patterns.
     * 
     * @param node the RaftNode instance
     */
    default void setRaftNode(RaftNode node) {
    }
}
