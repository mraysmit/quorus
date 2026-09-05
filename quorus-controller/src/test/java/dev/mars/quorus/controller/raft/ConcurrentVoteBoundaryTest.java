package dev.mars.quorus.controller.raft;

import dev.mars.quorus.controller.raft.grpc.VoteRequest;
import dev.mars.quorus.controller.raft.grpc.VoteResponse;
import dev.mars.quorus.controller.raft.storage.RaftStorageFactory;
import dev.mars.quorus.controller.state.QuorusStateStore;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;

import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class ConcurrentVoteBoundaryTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    @TempDir Path directory;
    private RaftNode node;

    @AfterEach
    void close() {
        if (node != null) awaitSuccess(node.stop(), TIMEOUT);
        InMemoryTransportSimulator.clearAllTransports();
    }

    @Test
    void incomingVotesUseTheNodeContext(Vertx vertx) {
        node = builder(vertx).mode(RaftNodeMode.volatileMode()).build();
        Context startedOn = awaitSuccess(node.start().map(v -> Vertx.currentContext()), TIMEOUT);
        Context votedOn = awaitSuccess(node.handleVoteRequest(vote("candidate-a"))
                .map(v -> Vertx.currentContext()), TIMEOUT);
        assertSame(startedOn, votedOn, "External RPC callers must enter the node's owning context");
    }

    @Test
    void overlappingDurableVotesGrantOnlyOneCandidateAndSurviveReopen(Vertx vertx) {
        var storage = awaitSuccess(RaftStorageFactory.create(vertx, "raftlog", directory, true), TIMEOUT);
        node = builder(vertx).mode(RaftNodeMode.durable(storage)).build();
        awaitSuccess(node.start(), TIMEOUT);
        var votes = new ArrayList<Future<VoteResponse>>();
        for (int i = 0; i < 32; i++) votes.add(node.handleVoteRequest(vote("candidate-" + i)));
        awaitSuccess(Future.all(votes), TIMEOUT);
        var granted = votes.stream().map(Future::result).filter(VoteResponse::getVoteGranted).count();
        assertEquals(1, granted, "A pending fsync must not allow another candidate to receive the same-term vote");
        String winner = node.getVotedFor();
        awaitSuccess(node.stop(), TIMEOUT);
        node = null;
        var reopened = awaitSuccess(RaftStorageFactory.create(vertx, "raftlog", directory, true), TIMEOUT);
        try {
            var metadata = awaitSuccess(reopened.loadMetadata(), TIMEOUT);
            assertEquals(1, metadata.currentTerm());
            assertEquals(winner, metadata.votedFor().orElseThrow());
        } finally {
            awaitSuccess(reopened.close(), TIMEOUT);
        }
    }

    private RaftNode.Builder builder(Vertx vertx) {
        return RaftNode.builder().vertx(vertx).nodeId("voter")
                .clusterNodes(Set.of("voter", "candidate-a", "candidate-b"))
                .transport(new InMemoryTransportSimulator("voter"))
                .stateMachine(new QuorusStateStore()).electionTimeout(60_000).heartbeatInterval(1_000);
    }

    private VoteRequest vote(String candidate) {
        return VoteRequest.newBuilder().setTerm(1).setCandidateId(candidate).build();
    }
}
