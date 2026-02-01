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

/**
 * Sealed interface for Raft protocol messages.
 * 
 * <p>This provides compile-time type safety for Raft message handling,
 * ensuring exhaustive pattern matching and preventing invalid message types.
 * 
 * <p>The Raft protocol defines a fixed set of message types:
 * <ul>
 *   <li>{@link Vote} - RequestVote RPC (request and response)</li>
 *   <li>{@link AppendEntries} - AppendEntries RPC (request and response)</li>
 * </ul>
 * 
 * <h2>Usage with Pattern Matching (Java 21+):</h2>
 * <pre>{@code
 * void handleMessage(RaftMessage message) {
 *     switch (message) {
 *         case RaftMessage.Vote vote -> handleVote(vote);
 *         case RaftMessage.AppendEntries ae -> handleAppendEntries(ae);
 *     }
 * }
 * }</pre>
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2026-02-02
 */
public sealed interface RaftMessage {

    /**
     * Wrapper for VoteRequest messages.
     * 
     * @param request the protobuf VoteRequest
     */
    record Vote(VoteRequest request) implements RaftMessage {}

    /**
     * Wrapper for AppendEntriesRequest messages.
     * 
     * @param request the protobuf AppendEntriesRequest
     */
    record AppendEntries(AppendEntriesRequest request) implements RaftMessage {}
}
