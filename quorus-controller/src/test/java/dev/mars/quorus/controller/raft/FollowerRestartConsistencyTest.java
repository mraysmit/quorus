/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.raft;

import dev.mars.quorus.controller.raft.grpc.AppendEntriesRequest;
import dev.mars.quorus.controller.raft.grpc.AppendEntriesResponse;
import dev.mars.quorus.controller.raft.grpc.VoteRequest;
import dev.mars.quorus.controller.raft.storage.RaftStorage;
import dev.mars.quorus.controller.raft.storage.RaftStorage.LogEntryData;
import dev.mars.quorus.controller.raft.storage.RaftStorageFactory;
import dev.mars.quorus.controller.state.CommandResult;
import dev.mars.quorus.controller.state.ProtobufCommandCodec;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.SystemMetadataCommand;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static dev.mars.quorus.testing.TestFutureUtils.eventually;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the defects behind the intermittent {@link ThreeControllerDurableRestartTest}
 * failure: log mutations that overlapped an in-flight storage write reserved or persisted the same
 * index twice, recovery reproduced such duplicates positionally, and the election timer of a follower
 * was reset by vote requests it rejected. Storage is obtained through {@link RaftStorageFactory}, the
 * same boundary the controller uses, so every test runs against the production log implementation.
 */
@ExtendWith(VertxExtension.class)
class FollowerRestartConsistencyTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Set<String> CLUSTER = Set.of("leader", "follower", "third");

    @TempDir
    Path tempDir;

    @AfterEach
    void clearNetwork() {
        InMemoryTransportSimulator.clearAllTransports();
    }

    @Test
    @DisplayName("Overlapping AppendEntries for the same index must not duplicate WAL records")
    void overlappingAppendEntriesDoNotDuplicateWalRecords(Vertx vertx) {
        Path dataDir = tempDir.resolve("follower-raft");
        RaftNode follower = follower(vertx, openStorage(vertx, dataDir), 10_000);
        awaitSuccess(follower.start(), TIMEOUT);

        var first = entry(1, 1, "first");
        var second = entry(2, 1, "second");

        // The leader retransmits entry 1 together with entry 2 before it has processed the
        // acknowledgement for entry 1, so the second request reaches the follower while the
        // first persist is still in flight.
        Future<AppendEntriesResponse> initial =
                follower.handleAppendEntriesRequest(appendEntries(1, 0, 0, first));
        Future<AppendEntriesResponse> retransmit =
                follower.handleAppendEntriesRequest(appendEntries(1, 0, 0, first, second));

        assertTrue(awaitSuccess(initial, TIMEOUT).getSuccess());
        assertTrue(awaitSuccess(retransmit, TIMEOUT).getSuccess());
        assertEquals(2, follower.getLastLogIndex(), "in-memory log deduplicates the retransmitted entry");

        awaitSuccess(follower.stop(), TIMEOUT);
        InMemoryTransportSimulator.clearAllTransports();

        assertEquals(List.of(1L, 2L), replayedIndices(vertx, dataDir), "WAL must hold each index exactly once");

        RaftNode recovered = follower(vertx, openStorage(vertx, dataDir), 10_000);
        awaitSuccess(recovered.start(), TIMEOUT);
        assertEquals(2, recovered.getLastLogIndex(), "recovered log must end at the last replicated index");
        awaitSuccess(recovered.stop(), TIMEOUT);
    }

    @Test
    @DisplayName("Overlapping leader submits must reserve distinct indices")
    void overlappingLeaderSubmitsReserveDistinctIndices(Vertx vertx) {
        Path dataDir = tempDir.resolve("leader-raft");
        RaftNode leader = RaftNode.builder()
                .vertx(vertx)
                .nodeId("leader")
                .clusterNodes(Set.of("leader"))
                .transport(new InMemoryTransportSimulator("leader"))
                .stateMachine(new QuorusStateStore())
                .mode(RaftNodeMode.durable(openStorage(vertx, dataDir)))
                .electionTimeout(200)
                .heartbeatInterval(50)
                .build();
        awaitSuccess(leader.start(), TIMEOUT);
        awaitSuccess(eventually(vertx, leader::isLeader, TIMEOUT), TIMEOUT.plusSeconds(1));

        // Two requests arrive on the event loop before the first WAL write has completed, the
        // way two concurrent HTTP requests do on a controller.
        Future<CommandResult<?>> first = leader.submitCommand(SystemMetadataCommand.set("first", "1"));
        Future<CommandResult<?>> second = leader.submitCommand(SystemMetadataCommand.set("second", "2"));

        assertInstanceOf(CommandResult.Success.class, awaitSuccess(first, TIMEOUT));
        assertInstanceOf(CommandResult.Success.class, awaitSuccess(second, TIMEOUT));
        assertEquals(3, leader.getLastLogIndex(), "no-op plus two commands occupy indices 1 to 3");

        awaitSuccess(leader.stop(), TIMEOUT);
        assertEquals(List.of(1L, 2L, 3L), replayedIndices(vertx, dataDir), "WAL indices must be contiguous and unique");
    }

    @Test
    @DisplayName("Recovery keys repeated storage records by index instead of position")
    void recoveryKeysRepeatedRecordsByIndex(Vertx vertx) {
        Path dataDir = tempDir.resolve("journal");
        RaftStorage storage = openStorage(vertx, dataDir);
        awaitSuccess(storage.appendEntries(List.of(record(1, 1))), TIMEOUT);
        awaitSuccess(storage.appendEntries(List.of(record(2, 1))), TIMEOUT);
        // A duplicate written by an older release: same index, same term, therefore the same entry.
        awaitSuccess(storage.appendEntries(List.of(record(1, 1))), TIMEOUT);
        // A genuine overwrite from a newer leader supersedes the tail from that index.
        awaitSuccess(storage.appendEntries(List.of(record(2, 2))), TIMEOUT);
        awaitSuccess(storage.sync(), TIMEOUT);
        awaitSuccess(storage.close(), TIMEOUT);

        RaftNode recovered = follower(vertx, openStorage(vertx, dataDir), 10_000);
        awaitSuccess(recovered.start(), TIMEOUT);
        assertEquals(2, recovered.getLastLogIndex(), "the duplicate is ignored and index 2 is replaced in place");
        assertEquals(3, recovered.getLogSize(), "sentinel plus indices 1 and 2");

        // Index 2 must now carry term 2: a candidate whose last entry is (index 2, term 1) is
        // behind this log and is refused, one whose last entry is (index 2, term 2) is granted.
        assertFalse(awaitSuccess(recovered.handleVoteRequest(voteRequest(5, 2, 1)), TIMEOUT).getVoteGranted());
        assertTrue(awaitSuccess(recovered.handleVoteRequest(voteRequest(6, 2, 2)), TIMEOUT).getVoteGranted());
        awaitSuccess(recovered.stop(), TIMEOUT);
    }

    @Test
    @DisplayName("Rejected vote requests must not keep resetting the election timer of a follower")
    void rejectedVoteRequestsDoNotSuppressElectionTimeout(Vertx vertx) {
        RaftNode follower = follower(vertx, null, 300);
        awaitSuccess(follower.start(), TIMEOUT);

        // Seed one entry from a term-1 leader so that a candidate with an empty log is never
        // up to date and every one of its vote requests is rejected.
        AppendEntriesResponse seeded = awaitSuccess(
                follower.handleAppendEntriesRequest(appendEntries(1, 0, 0, entry(1, 1, "seed"))), TIMEOUT);
        assertTrue(seeded.getSuccess());

        AtomicInteger candidacies = new AtomicInteger();
        follower.addStateChangeListener(state -> {
            if (state == RaftNode.State.CANDIDATE) {
                candidacies.incrementAndGet();
            }
        });

        // A stale peer with a shorter timeout asks for votes every 100 ms with a higher term.
        // Each request is rejected. The own 300 to 600 ms timeout of the follower must still fire.
        AtomicLong term = new AtomicLong(follower.getCurrentTerm());
        AtomicReference<Throwable> unexpected = new AtomicReference<>();
        long pestering = vertx.setPeriodic(100, id -> {
            long nextTerm = Math.max(term.get(), follower.getCurrentTerm()) + 1;
            term.set(nextTerm);
            follower.handleVoteRequest(voteRequest(nextTerm, 0, 0))
                    .onSuccess(response -> {
                        if (response.getVoteGranted()) {
                            unexpected.compareAndSet(null,
                                    new AssertionError("a candidate with an empty log must be rejected"));
                        }
                    })
                    .onFailure(err -> unexpected.compareAndSet(null, err));
        });
        try {
            awaitSuccess(eventually(vertx, () -> candidacies.get() > 0 || unexpected.get() != null,
                    Duration.ofSeconds(3)).recover(timedOut -> Future.succeededFuture()), Duration.ofSeconds(4));
        } finally {
            vertx.cancelTimer(pestering);
        }

        assertNull(unexpected.get(), "every vote request from the stale candidate must be rejected");
        assertTrue(candidacies.get() > 0,
                "follower never started its own election while rejecting a stale candidate for 3 s");
        awaitSuccess(follower.stop(), TIMEOUT);
    }

    private static RaftNode follower(Vertx vertx, RaftStorage storage, long electionTimeoutMs) {
        return RaftNode.builder()
                .vertx(vertx)
                .nodeId("follower")
                .clusterNodes(CLUSTER)
                .transport(new InMemoryTransportSimulator("follower"))
                .stateMachine(new QuorusStateStore())
                .mode(storage == null ? RaftNodeMode.volatileMode() : RaftNodeMode.durable(storage))
                .electionTimeout(electionTimeoutMs)
                .heartbeatInterval(100)
                .build();
    }

    /** Obtains the production storage type through the same factory call the controller makes. */
    private static RaftStorage openStorage(Vertx vertx, Path dataDir) {
        return awaitSuccess(RaftStorageFactory.create(vertx, "raftlog", dataDir, true), TIMEOUT);
    }

    private static List<Long> replayedIndices(Vertx vertx, Path dataDir) {
        RaftStorage storage = openStorage(vertx, dataDir);
        List<Long> indices = awaitSuccess(storage.replayLog(), TIMEOUT).stream()
                .map(LogEntryData::index)
                .toList();
        awaitSuccess(storage.close(), TIMEOUT);
        return indices;
    }

    private static VoteRequest voteRequest(long term, long lastLogIndex, long lastLogTerm) {
        return VoteRequest.newBuilder()
                .setTerm(term)
                .setCandidateId("third")
                .setLastLogIndex(lastLogIndex)
                .setLastLogTerm(lastLogTerm)
                .build();
    }

    private static AppendEntriesRequest appendEntries(long term, long prevLogIndex, long prevLogTerm,
                                                      dev.mars.quorus.controller.raft.grpc.LogEntry... entries) {
        AppendEntriesRequest.Builder builder = AppendEntriesRequest.newBuilder()
                .setTerm(term)
                .setLeaderId("leader")
                .setPrevLogIndex(prevLogIndex)
                .setPrevLogTerm(prevLogTerm)
                .setLeaderCommit(0);
        for (dev.mars.quorus.controller.raft.grpc.LogEntry entry : entries) {
            builder.addEntries(entry);
        }
        return builder.build();
    }

    private static dev.mars.quorus.controller.raft.grpc.LogEntry entry(long index, long term, String value) {
        return dev.mars.quorus.controller.raft.grpc.LogEntry.newBuilder()
                .setIndex(index)
                .setTerm(term)
                .setData(ProtobufCommandCodec.serialize(SystemMetadataCommand.set("key-" + index, value)))
                .build();
    }

    private static LogEntryData record(long index, long term) {
        return new LogEntryData(index, term,
                ProtobufCommandCodec.serialize(SystemMetadataCommand.set("key-" + index, "term-" + term)).toByteArray());
    }
}
