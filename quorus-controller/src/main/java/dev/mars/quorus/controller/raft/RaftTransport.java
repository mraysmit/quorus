package dev.mars.quorus.controller.raft;

import dev.mars.quorus.controller.raft.grpc.AppendEntriesRequest;
import dev.mars.quorus.controller.raft.grpc.AppendEntriesResponse;
import dev.mars.quorus.controller.raft.grpc.VoteRequest;
import dev.mars.quorus.controller.raft.grpc.VoteResponse;
import io.vertx.core.Future;

import java.util.function.Consumer;

/**
 * Interface for Raft transport layer.
 */
public interface RaftTransport {

    void start(Consumer<Object> messageHandler);

    void stop();

    Future<VoteResponse> sendVoteRequest(String targetId, VoteRequest request);

    Future<AppendEntriesResponse> sendAppendEntries(String targetId, AppendEntriesRequest request);

    // Helper to set RaftNode reference
    default void setRaftNode(RaftNode node) {
    }
}
