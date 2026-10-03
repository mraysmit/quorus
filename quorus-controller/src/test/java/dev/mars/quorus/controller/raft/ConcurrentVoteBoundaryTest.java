package dev.mars.quorus.controller.raft;

import dev.mars.quorus.controller.raft.grpc.VoteRequest;
import dev.mars.quorus.controller.raft.grpc.VoteResponse;
import dev.mars.quorus.controller.raft.storage.RaftStorage;
import dev.mars.quorus.controller.raft.storage.RaftStorageFactory;
import dev.mars.quorus.controller.state.QuorusStateStore;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

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
        RaftStorage delegate = awaitSuccess(RaftStorageFactory.create(vertx, "raftlog", directory, true), TIMEOUT);
        AtomicReference<Context> metadataContext = new AtomicReference<>();
        RaftStorage observingStorage = observingMetadataContext(delegate, metadataContext);
        node = builder(vertx).mode(RaftNodeMode.durable(observingStorage)).build();
        Context startedOn = awaitSuccess(node.start().map(v -> Vertx.currentContext()), TIMEOUT);
        awaitSuccess(node.handleVoteRequest(vote("candidate-a")), TIMEOUT);
        assertSame(startedOn, metadataContext.get(),
                "External RPC callers must mutate durable vote state on the node's owning context");
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

    /**
     * Register item ENG-21: the controller starts its Raft gRPC server before {@link RaftNode#start()}
     * has recovered the persisted term and vote. A vote request handled in that window must not be
     * judged against the pre-recovery state. This node voted for candidate-a in term 3 before it
     * restarted; candidate-b's term-3 request must not be granted.
     */
    @Test
    void aVoteArrivingDuringRecoveryCannotGrantASecondVoteInARecoveredTerm(Vertx vertx) {
        RaftStorage seed = awaitSuccess(RaftStorageFactory.create(vertx, "raftlog", directory, true), TIMEOUT);
        awaitSuccess(seed.updateMetadata(3, Optional.of("candidate-a")), TIMEOUT);
        awaitSuccess(seed.close(), TIMEOUT);

        RaftStorage delegate = awaitSuccess(RaftStorageFactory.create(vertx, "raftlog", directory, true), TIMEOUT);
        Promise<Void> releaseRecovery = Promise.promise();
        RaftStorage held = holdingMetadataLoad(delegate, releaseRecovery.future(), releaseRecovery);
        node = builder(vertx).mode(RaftNodeMode.durable(held)).build();

        Future<Void> started = node.start();
        Future<VoteResponse> secondVote = node.handleVoteRequest(VoteRequest.newBuilder()
                .setTerm(3).setCandidateId("candidate-b").build());
        // A vote handled during recovery persists its decision, which releases recovery at once;
        // a correct node defers or rejects it, so recovery is released by the timer instead.
        vertx.setTimer(500, id -> releaseRecovery.tryComplete());

        awaitSuccess(started, TIMEOUT);
        VoteResponse response = awaitSuccess(secondVote, TIMEOUT);
        assertFalse(response.getVoteGranted(),
                "A node that voted for candidate-a in term 3 must not vote for candidate-b in term 3");
        assertEquals("candidate-a", node.getVotedFor(), "The recovered vote must stand");
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

    /**
     * Wraps real storage so that {@code loadMetadata} completes only once {@code release} completes,
     * and so that a metadata write made while it is held completes {@code onWrite}.
     */
    private static RaftStorage holdingMetadataLoad(RaftStorage delegate, Future<Void> release,
                                                   Promise<Void> onWrite) {
        return new RaftStorage() {
            @Override public Future<Void> open(Path path) { return delegate.open(path); }
            @Override public Future<Void> close() { return delegate.close(); }
            @Override public Future<Void> updateMetadata(long term, Optional<String> votedFor) {
                return delegate.updateMetadata(term, votedFor).onComplete(r -> onWrite.tryComplete());
            }
            @Override public Future<PersistentMeta> loadMetadata() {
                return release.compose(v -> delegate.loadMetadata());
            }
            @Override public Future<Void> appendEntries(List<LogEntryData> entries) {
                return delegate.appendEntries(entries);
            }
            @Override public Future<Void> truncateSuffix(long index) { return delegate.truncateSuffix(index); }
            @Override public Future<Void> sync() { return delegate.sync(); }
            @Override public Future<List<LogEntryData>> replayLog() { return delegate.replayLog(); }
            @Override public Future<Void> saveSnapshot(byte[] data, long index, long term) {
                return delegate.saveSnapshot(data, index, term);
            }
            @Override public Future<Optional<SnapshotData>> loadSnapshot() { return delegate.loadSnapshot(); }
            @Override public Future<Void> truncatePrefix(long index) { return delegate.truncatePrefix(index); }
        };
    }

    private static RaftStorage observingMetadataContext(RaftStorage delegate,
                                                         AtomicReference<Context> observed) {
        return new RaftStorage() {
            @Override public Future<Void> open(Path path) { return delegate.open(path); }
            @Override public Future<Void> close() { return delegate.close(); }
            @Override public Future<Void> updateMetadata(long term, Optional<String> votedFor) {
                observed.set(Vertx.currentContext());
                return delegate.updateMetadata(term, votedFor);
            }
            @Override public Future<PersistentMeta> loadMetadata() { return delegate.loadMetadata(); }
            @Override public Future<Void> appendEntries(List<LogEntryData> entries) {
                return delegate.appendEntries(entries);
            }
            @Override public Future<Void> truncateSuffix(long index) { return delegate.truncateSuffix(index); }
            @Override public Future<Void> sync() { return delegate.sync(); }
            @Override public Future<List<LogEntryData>> replayLog() { return delegate.replayLog(); }
            @Override public Future<Void> saveSnapshot(byte[] data, long index, long term) {
                return delegate.saveSnapshot(data, index, term);
            }
            @Override public Future<Optional<SnapshotData>> loadSnapshot() { return delegate.loadSnapshot(); }
            @Override public Future<Void> truncatePrefix(long index) { return delegate.truncatePrefix(index); }
        };
    }
}
