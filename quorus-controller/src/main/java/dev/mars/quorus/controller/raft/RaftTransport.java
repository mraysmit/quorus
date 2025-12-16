package dev.mars.quorus.controller.raft;

import dev.mars.quorus.controller.raft.grpc.AppendEntriesRequest;
import dev.mars.quorus.controller.raft.grpc.AppendEntriesResponse;
import dev.mars.quorus.controller.raft.grpc.VoteRequest;
import dev.mars.quorus.controller.raft.grpc.VoteResponse;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Interface for Raft transport layer.
 */
public interface RaftTransport {

    void start(Consumer<Object> messageHandler);

    void stop();

    CompletableFuture<VoteResponse> sendVoteRequest(String targetId, VoteRequest request);

    CompletableFuture<AppendEntriesResponse> sendAppendEntries(String targetId, AppendEntriesRequest request);

    // Helper to set RaftNode reference
    default void setRaftNode(RaftNode node) {
    }
}
