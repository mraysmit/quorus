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
import dev.mars.quorus.controller.raft.grpc.InstallSnapshotRequest;
import dev.mars.quorus.controller.raft.grpc.InstallSnapshotResponse;
import dev.mars.quorus.controller.raft.grpc.VoteRequest;
import dev.mars.quorus.controller.raft.grpc.VoteResponse;
import dev.mars.quorus.controller.raft.storage.RaftStorage;
import dev.mars.quorus.controller.raft.storage.RaftStorage.LogEntryData;
import dev.mars.quorus.controller.raft.storage.RaftStorage.SnapshotData;
import dev.mars.quorus.controller.state.ProtobufCommandCodec;
import dev.mars.quorus.controller.state.StateMachineCommand;
import com.google.protobuf.ByteString;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reactive Raft Node implementation with durable WAL storage.
 * Runs on the Vert.x Event Loop (Single Threaded), removing the need for
 * synchronization.
 * 
 * <p>Implements the "Persist-before-response" rule for all state-changing
 * Raft operations:
 * <ul>
 *   <li>Vote is never granted until metadata is durable</li>
 *   <li>AppendEntries is never ACK'd until log entries are durable</li>
 *   <li>In-memory state is only mutated AFTER durability is confirmed</li>
 * </ul>
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */
public class RaftNode {

    private static final Logger logger = LoggerFactory.getLogger(RaftNode.class);

    // ========== RAFT NODE STATES ==========

    public enum State {
        FOLLOWER, CANDIDATE, LEADER
    }

    // ========== NODE CONFIGURATION ==========

    private final Vertx vertx;
    private final String nodeId;
    private final Set<String> clusterNodes;
    private final RaftTransport transport;
    private final RaftStateMachine stateMachine;
    private final RaftStorage storage;  // WAL storage for durability

    // ========== PERSISTENT STATE ==========
    private volatile long currentTerm = 0;
    private String votedFor = null;
    private final List<LogEntry> log = new ArrayList<>();

    // ========== VOLATILE STATE ==========
    private volatile State state = State.FOLLOWER;
    private volatile long commitIndex = 0;
    private volatile long lastApplied = 0;

    // ========== SNAPSHOT STATE ==========
    /** The log index of the last entry included in the most recent snapshot.
     *  All in-memory log entries have indices > snapshotLastIndex.
     *  Array offset: log.get(logIndex - snapshotLastIndex). */
    private long snapshotLastIndex = 0;
    /** The term of the last entry included in the most recent snapshot. */
    private long snapshotLastTerm = 0;
    /** Timer ID for periodic snapshot eligibility checks. */
    private long snapshotTimerId = -1;

    // ========== LEADER STATE ==========
    private final Map<String, Long> nextIndex = new HashMap<>();
    private final Map<String, Long> matchIndex = new HashMap<>();
    private final Map<Long, Promise<Object>> pendingCommands = new ConcurrentHashMap<>();

    // ========== TIMING AND CONTROL ==========
    private volatile boolean running = false;
    private long electionTimerId = -1;
    private long heartbeatTimerId = -1;

    // ========== STATE CHANGE LISTENERS ==========
    private final List<io.vertx.core.Handler<State>> stateChangeListeners = new ArrayList<>();

    // ========== EDGE METRICS ==========
    private LongCounter rpcCounter;

    // ========== CONFIGURATION PARAMETERS ==========
    private final long electionTimeoutMs;
    private final long heartbeatIntervalMs;
    private final boolean snapshotEnabled;
    private final long snapshotThreshold;
    private final long snapshotCheckIntervalMs;

    // ========== INSTALL SNAPSHOT STATE ==========
    /** Maximum chunk size for InstallSnapshot RPC (default 1 MB). */
    static final int SNAPSHOT_CHUNK_SIZE = 1024 * 1024;
    /** Tracks in-progress snapshot installs from a leader (follower side). */
    private final Map<String, SnapshotChunkAssembler> pendingInstalls = new HashMap<>();
    /** Tracks in-progress snapshot sends to followers (leader side). */
    private final Set<String> installSnapshotInProgress = new HashSet<>();

    // ========== SNAPSHOT METRICS ==========
    private LongCounter snapshotCounter;
    private LongHistogram snapshotDuration;
    private LongCounter logCompactedEntries;
    private LongCounter installSnapshotSent;
    private LongCounter installSnapshotReceived;

    /**
     * Creates a RaftNode with default timing parameters and in-memory storage.
     * <p>Use this constructor for testing or single-node development only.
     * For production, use the constructor that accepts RaftStorage.
     */
    public RaftNode(Vertx vertx, String nodeId, Set<String> clusterNodes, RaftTransport transport,
            RaftStateMachine stateMachine) {
        this(vertx, nodeId, clusterNodes, transport, stateMachine, null, 5000, 1000,
                false, 10000, 60000);
    }

    /**
     * Creates a RaftNode with custom timing parameters and optional storage.
     * <p>If storage is null, runs in volatile mode (no persistence).
     */
    public RaftNode(Vertx vertx, String nodeId, Set<String> clusterNodes, RaftTransport transport,
            RaftStateMachine stateMachine, long electionTimeoutMs, long heartbeatIntervalMs) {
        this(vertx, nodeId, clusterNodes, transport, stateMachine, null, electionTimeoutMs, heartbeatIntervalMs,
                false, 10000, 60000);
    }

    /**
     * Creates a RaftNode with durable WAL storage.
     * <p>This is the recommended constructor for production use.
     *
     * @param vertx             The Vert.x instance
     * @param nodeId            Unique node identifier
     * @param clusterNodes      Set of all node IDs in the cluster
     * @param transport         Transport for inter-node communication
     * @param stateMachine      State machine for applying committed entries
     * @param storage           WAL storage for durability (null for volatile mode)
     * @param electionTimeoutMs Base election timeout in milliseconds
     * @param heartbeatIntervalMs Heartbeat interval in milliseconds
     */
    public RaftNode(Vertx vertx, String nodeId, Set<String> clusterNodes, RaftTransport transport,
            RaftStateMachine stateMachine, RaftStorage storage, long electionTimeoutMs, long heartbeatIntervalMs) {
        this(vertx, nodeId, clusterNodes, transport, stateMachine, storage, electionTimeoutMs, heartbeatIntervalMs,
                true, 10000, 60000);
    }

    /**
     * Creates a RaftNode with full configuration including snapshot scheduling.
     *
     * @param vertx                   The Vert.x instance
     * @param nodeId                  Unique node identifier
     * @param clusterNodes            Set of all node IDs in the cluster
     * @param transport               Transport for inter-node communication
     * @param stateMachine            State machine for applying committed entries
     * @param storage                 WAL storage for durability (null for volatile mode)
     * @param electionTimeoutMs       Base election timeout in milliseconds
     * @param heartbeatIntervalMs     Heartbeat interval in milliseconds
     * @param snapshotEnabled         Whether to enable automatic snapshot scheduling
     * @param snapshotThreshold       Number of entries between snapshots
     * @param snapshotCheckIntervalMs Interval between snapshot eligibility checks
     */
    public RaftNode(Vertx vertx, String nodeId, Set<String> clusterNodes, RaftTransport transport,
            RaftStateMachine stateMachine, RaftStorage storage, long electionTimeoutMs, long heartbeatIntervalMs,
            boolean snapshotEnabled, long snapshotThreshold, long snapshotCheckIntervalMs) {
        this.vertx = vertx;
        this.nodeId = nodeId;
        this.clusterNodes = new HashSet<>(clusterNodes);
        this.transport = transport;
        this.stateMachine = stateMachine;
        this.storage = storage;
        this.electionTimeoutMs = electionTimeoutMs;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.snapshotEnabled = snapshotEnabled && storage != null;
        this.snapshotThreshold = snapshotThreshold;
        this.snapshotCheckIntervalMs = snapshotCheckIntervalMs;

        // Initialize log with a dummy entry
        log.add(new LogEntry(0, 0, null));

        // Initialize OpenTelemetry Metrics
        Meter meter = GlobalOpenTelemetry.getMeter("quorus-controller");

        meter.gaugeBuilder("quorus.cluster.state")
                .setDescription("Current Raft state (0=FOLLOWER, 1=CANDIDATE, 2=LEADER)")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(state.ordinal()));

        meter.gaugeBuilder("quorus.cluster.term")
                .setDescription("Current Raft term")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(currentTerm));

        meter.gaugeBuilder("quorus.cluster.commit_index")
                .setDescription("Current Commit Index")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(commitIndex));

        meter.gaugeBuilder("quorus.cluster.last_applied")
                .setDescription("Last Applied Log Index")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(lastApplied));

        meter.gaugeBuilder("quorus.cluster.is_leader")
                .setDescription("Whether this node is the leader (1=Yes, 0=No)")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(state == State.LEADER ? 1 : 0));

        meter.gaugeBuilder("quorus.cluster.log_size")
                .setDescription("Number of entries in the Raft log")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(log.size()));

        // Snapshot metrics
        snapshotCounter = meter.counterBuilder("quorus.raft.snapshot.total")
                .setDescription("Total number of snapshots taken")
                .setUnit("1")
                .build();

        snapshotDuration = meter.histogramBuilder("quorus.raft.snapshot.duration")
                .setDescription("Time taken to create and persist a snapshot")
                .setUnit("ms")
                .ofLongs()
                .build();

        logCompactedEntries = meter.counterBuilder("quorus.raft.log.compacted.entries")
                .setDescription("Total number of log entries removed by compaction")
                .setUnit("1")
                .build();

        meter.gaugeBuilder("quorus.raft.snapshot.last_index")
                .setDescription("Log index of the last snapshot")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(snapshotLastIndex));

        installSnapshotSent = meter.counterBuilder("quorus.raft.install_snapshot.sent.total")
                .setDescription("Total InstallSnapshot RPCs sent by leader")
                .setUnit("1")
                .build();

        installSnapshotReceived = meter.counterBuilder("quorus.raft.install_snapshot.received.total")
                .setDescription("Total InstallSnapshot RPCs received by follower")
                .setUnit("1")
                .build();

        // Edge metrics for nodeGraph visualization
        rpcCounter = meter.counterBuilder("quorus.raft.rpc.total")
                .setDescription("Total Raft RPC calls between nodes (for nodeGraph edges)")
                .setUnit("1")
                .build();
    }

    public Future<Void> start() {
        Promise<Void> promise = Promise.promise();
        vertx.runOnContext(v -> {
            try {
                if (running) {
                    promise.complete();
                    return;
                }

                logger.info("Starting Raft node: {}", nodeId);

                // Set reference to this node in transport
                transport.setRaftNode(this);

                // Recover state from WAL if storage is configured
                recoverFromStorage()
                    .onSuccess(v2 -> {
                        // Start transport listener
                        transport.start(this::handleMessage);

                        // Start election timer
                        resetElectionTimer();

                        running = true;
                        logger.info("Raft node {} started successfully (term={}, logSize={})", 
                                    nodeId, currentTerm, log.size());
                        promise.complete();
                    })
                    .onFailure(err -> {
                        logger.error("Failed to recover Raft state from storage: {}", err.getMessage());
                        logger.trace("Stack trace for recovery failure", err);
                        promise.fail(err);
                    });

            } catch (Exception e) {
                logger.error("Failed to start Raft node: {}", e.getMessage());
                logger.trace("Stack trace for Raft node start failure", e);
                promise.fail(e);
            }
        });
        return promise.future();
    }

    /**
     * Recovers persistent state from WAL storage.
     * <p>Order: Metadata → Snapshot (if any) → Log Replay → State Machine Rebuild
     */
    private Future<Void> recoverFromStorage() {
        if (storage == null) {
            logger.info("No storage configured, running in volatile mode");
            return Future.succeededFuture();
        }

        logger.info("Recovering Raft state from storage...");

        return storage.loadMetadata()
            .compose(meta -> {
                this.currentTerm = meta.currentTerm();
                this.votedFor = meta.votedFor().orElse(null);
                logger.info("Recovered metadata: term={}, votedFor={}", currentTerm, votedFor);

                // Try to load snapshot
                return storage.loadSnapshot();
            })
            .compose(snapshotOpt -> {
                if (snapshotOpt.isPresent()) {
                    SnapshotData snapshot = snapshotOpt.get();
                    logger.info("Restoring from snapshot: lastIncludedIndex={}, lastIncludedTerm={}",
                            snapshot.lastIncludedIndex(), snapshot.lastIncludedTerm());

                    // Restore state machine from snapshot
                    stateMachine.restoreSnapshot(snapshot.data());

                    // Set snapshot boundaries
                    snapshotLastIndex = snapshot.lastIncludedIndex();
                    snapshotLastTerm = snapshot.lastIncludedTerm();
                    lastApplied = snapshot.lastIncludedIndex();
                    commitIndex = snapshot.lastIncludedIndex();

                    // Initialize log with sentinel at snapshot boundary
                    log.clear();
                    log.add(new LogEntry(snapshotLastTerm, snapshotLastIndex, null));
                } else {
                    logger.info("No snapshot found, will rebuild from full log replay");
                }

                return storage.replayLog();
            })
            .compose(entries -> {
                if (snapshotLastIndex > 0) {
                    // Snapshot recovery: only replay entries AFTER the snapshot
                    int replayedCount = 0;
                    for (LogEntryData entry : entries) {
                        if (entry.index() > snapshotLastIndex) {
                            StateMachineCommand command = deserialize(ByteString.copyFrom(entry.payload()));
                            log.add(new LogEntry(entry.term(), entry.index(), command));
                            replayedCount++;
                        }
                    }
                    logger.info("Replayed {} post-snapshot entries (skipped {} compacted entries)",
                            replayedCount, entries.size() - replayedCount);

                    // Apply post-snapshot entries to state machine
                    commitIndex = lastLogIndex() > snapshotLastIndex ? lastLogIndex() : snapshotLastIndex;
                    while (lastApplied < commitIndex) {
                        lastApplied++;
                        if (hasLogEntry(lastApplied)) {
                            LogEntry entry = log.get(toArrayIndex(lastApplied));
                            if (entry.getCommand() != null) {
                                stateMachine.apply(entry.getCommand());
                            }
                        }
                    }
                } else {
                    // Full rebuild: no snapshot, replay everything
                    log.clear();
                    log.add(new LogEntry(0, 0, null));
                    for (LogEntryData entry : entries) {
                        StateMachineCommand command = deserialize(ByteString.copyFrom(entry.payload()));
                        log.add(new LogEntry(entry.term(), entry.index(), command));
                    }
                    logger.info("Recovered {} log entries from storage", entries.size());
                    return rebuildStateMachine();
                }

                return Future.<Void>succeededFuture();
            })
            .onSuccess(v -> logger.info("Recovery complete: term={}, logSize={}, lastApplied={}, snapshotLastIndex={}",
                                        currentTerm, log.size(), lastApplied, snapshotLastIndex))
            .onFailure(err -> {
                logger.error("Recovery failed: {}", err.getMessage());
                logger.trace("Stack trace for recovery failure", err);
            });
    }

    /**
     * Rebuilds state machine by replaying all committed entries.
     * <p>Note: State machine operations should be idempotent.
     */
    private Future<Void> rebuildStateMachine() {
        logger.info("Rebuilding state machine from {} entries...", log.size() - 1);
        
        // Reset state machine to blank state
        stateMachine.reset();
        lastApplied = snapshotLastIndex;
        
        // Re-apply all entries (assume all recovered entries were committed)
        // In a more sophisticated implementation, we'd also persist commitIndex
        commitIndex = lastLogIndex() > snapshotLastIndex ? lastLogIndex() : snapshotLastIndex;
        
        applyLog();
        return Future.succeededFuture();
    }

    public Future<Void> stop() {
        Promise<Void> promise = Promise.promise();
        vertx.runOnContext(v -> {
            try {
                if (!running) {
                    promise.complete();
                    return;
                }
                running = false;
                state = State.FOLLOWER;

                cancelTimers();
                transport.stop();
                
                // Close storage
                if (storage != null) {
                    storage.close()
                        .onSuccess(v2 -> {
                            logger.info("Raft node stopped: {}", nodeId);
                            promise.complete();
                        })
                        .onFailure(err -> {
                            logger.warn("Error closing storage during shutdown: {}", err.getMessage());
                            logger.trace("Stack trace for storage close error", err);
                            promise.complete(); // Still complete, just log the warning
                        });
                } else {
                    logger.info("Raft node stopped: {}", nodeId);
                    promise.complete();
                }
            } catch (Exception e) {
                logger.error("Failed to stop Raft node: {}", e.getMessage());
                logger.trace("Stack trace for Raft node stop failure", e);
                promise.fail(e);
            }
        });
        return promise.future();
    }

    public Future<Object> submitCommand(StateMachineCommand command) {
        Promise<Object> promise = Promise.promise();

        vertx.runOnContext(v -> {
            if (state != State.LEADER) {
                promise.fail(
                        new IllegalStateException("Not the leader. Current state: " + state));
                return;
            }

            // Create log entry
            long entryIndex = lastLogIndex() + 1;
            LogEntry entry = new LogEntry(currentTerm, entryIndex, command);

            // Persist to WAL before adding to in-memory log
            persistLogEntry(entry)
                .onSuccess(v2 -> {
                    // Add to in-memory log AFTER durability confirmed
                    log.add(entry);

                    // Register promise for completion when committed
                    pendingCommands.put(entry.getIndex(), promise);

                    logger.info("Command submitted at index {} term {}", entry.getIndex(), entry.getTerm());

                    // Trigger replication
                    for (String peer : clusterNodes) {
                        if (!peer.equals(nodeId)) {
                            sendAppendEntries(peer, false);
                        }
                    }

                    // Try to commit immediately (crucial for single-node clusters)
                    updateCommitIndex();
                })
                .onFailure(err -> {
                    logger.error("Failed to persist command to WAL: {}", err.getMessage());
                    logger.trace("Stack trace for WAL persist failure", err);
                    promise.fail(err);
                });
        });

        return promise.future();
    }

    /**
     * Persists a log entry to the WAL with sync barrier.
     */
    private Future<Void> persistLogEntry(LogEntry entry) {
        if (storage == null) {
            return Future.succeededFuture();  // Volatile mode
        }

        ByteString serialized = serialize(entry.getCommand());
        LogEntryData entryData = new LogEntryData(entry.getIndex(), entry.getTerm(), serialized.toByteArray());
        
        return storage.appendEntries(List.of(entryData))
            .compose(v -> storage.sync());  // Durability barrier
    }

    // ... Getters ...
    public boolean isRunning() {
        return running;
    }

    public String getLeaderId() {
        return state == State.LEADER ? nodeId : votedFor;
    }

    public RaftStateMachine getStateMachine() {
        return stateMachine;
    }

    public State getState() {
        return state;
    }

    public long getCurrentTerm() {
        return currentTerm;
    }

    public String getNodeId() {
        return nodeId;
    }

    public boolean isLeader() {
        return state == State.LEADER;
    }

    /**
     * Returns the current size of the Raft log (number of entries).
     * Useful for monitoring replication progress across the cluster.
     */
    public int getLogSize() {
        return log.size();
    }

    /**
     * Returns the index of the last log entry applied to the state machine.
     * This trails commitIndex and indicates local state machine progress.
     */
    public long getLastApplied() {
        return lastApplied;
    }

    /**
     * Returns the index of the highest log entry known to be committed.
     * Entries up to this index are safe to apply to the state machine.
     */
    public long getCommitIndex() {
        return commitIndex;
    }

    /**
     * Returns the candidate ID this node voted for in the current term.
     * Returns null if no vote has been cast in this term.
     * Useful for debugging election issues and split vote scenarios.
     */
    public String getVotedFor() {
        return votedFor;
    }

    /**
     * Returns the log index of the last entry included in the most recent snapshot.
     * Returns 0 if no snapshot has been taken.
     */
    public long getSnapshotLastIndex() {
        return snapshotLastIndex;
    }

    /**
     * Returns the term of the last entry included in the most recent snapshot.
     * Returns 0 if no snapshot has been taken.
     */
    public long getSnapshotLastTerm() {
        return snapshotLastTerm;
    }

    /**
     * Returns the leader's nextIndex for a given peer.
     * Used in tests to verify index tracking after InstallSnapshot.
     *
     * @param peerId the peer node ID
     * @return the nextIndex for the peer, or -1 if not tracked
     */
    public long getNextIndex(String peerId) {
        return nextIndex.getOrDefault(peerId, -1L);
    }

    /**
     * Returns the index of the last entry in the log.
     * This is used during elections for log comparison (§5.4.1).
     * Returns 0 if the log only contains the sentinel entry.
     */
    public long getLastLogIndex() {
        return lastLogIndex();
    }

    /**
     * Returns the term of the last entry in the log.
     * This is used during elections for log comparison (§5.4.1).
     * Returns 0 if the log only contains the sentinel entry.
     */
    public long getLastLogTerm() {
        if (log.isEmpty()) {
            return snapshotLastTerm;
        }
        return log.get(log.size() - 1).getTerm();
    }

    // ========== STATE CHANGE OBSERVATION ==========

    /**
     * Registers a listener that is notified whenever this node's Raft state changes.
     * <p>Listeners are invoked on the Vert.x event loop context, making them safe
     * for use with Vert.x Futures and Promises. This enables reactive test patterns
     * instead of Thread.sleep polling loops.
     *
     * @param listener handler that receives the new {@link State}
     */
    public void addStateChangeListener(io.vertx.core.Handler<State> listener) {
        stateChangeListeners.add(listener);
    }

    /**
     * Removes a previously registered state change listener.
     *
     * @param listener the listener to remove
     */
    public void removeStateChangeListener(io.vertx.core.Handler<State> listener) {
        stateChangeListeners.remove(listener);
    }

    /**
     * Returns a Future that completes when this node transitions to the target state,
     * or fails if the timeout expires. This is the primary reactive alternative to
     * polling loops with Thread.sleep.
     *
     * <p>Usage in tests:
     * <pre>{@code
     * node.awaitState(State.LEADER, 10000)
     *     .onComplete(ctx.succeedingThenComplete());
     * }</pre>
     *
     * @param targetState the state to wait for
     * @param timeoutMs maximum time to wait in milliseconds
     * @return a Future that completes with the target state or fails on timeout
     */
    public Future<State> awaitState(State targetState, long timeoutMs) {
        // Already in target state
        if (state == targetState) {
            return Future.succeededFuture(targetState);
        }

        Promise<State> promise = Promise.promise();

        // Register listener
        io.vertx.core.Handler<State> listener = newState -> {
            if (newState == targetState && !promise.future().isComplete()) {
                promise.complete(targetState);
            }
        };
        addStateChangeListener(listener);

        // Set timeout
        long timerId = vertx.setTimer(timeoutMs, id -> {
            if (!promise.future().isComplete()) {
                removeStateChangeListener(listener);
                promise.fail("Timed out waiting for state " + targetState + " after " + timeoutMs + "ms (current: " + state + ")");
            }
        });

        // Clean up on completion
        promise.future().onComplete(ar -> {
            removeStateChangeListener(listener);
            vertx.cancelTimer(timerId);
        });

        return promise.future();
    }

    /**
     * Notifies all registered state change listeners of a state transition.
     * Invoked internally after every state change (becomeLeader, startElection, stepDown).
     */
    private void notifyStateChangeListeners(State newState) {
        for (io.vertx.core.Handler<State> listener : stateChangeListeners) {
            try {
                listener.handle(newState);
            } catch (Exception e) {
                logger.warn("State change listener threw exception", e);
            }
        }
    }

    // ========== LOG OFFSET HELPERS ==========

    /**
     * Converts a Raft log index to the in-memory array index.
     * The in-memory log starts at snapshotLastIndex (the sentinel/snapshot boundary).
     * Entry at snapshotLastIndex is at array position 0 (the sentinel).
     */
    private int toArrayIndex(long logIndex) {
        return (int) (logIndex - snapshotLastIndex);
    }

    /**
     * Returns the Raft log index of the last entry in the in-memory log.
     */
    private long lastLogIndex() {
        return snapshotLastIndex + log.size() - 1;
    }

    /**
     * Returns true if the given Raft log index is present in the in-memory log.
     */
    private boolean hasLogEntry(long logIndex) {
        int arrayIdx = toArrayIndex(logIndex);
        return arrayIdx >= 0 && arrayIdx < log.size();
    }

    private void cancelTimers() {
        if (electionTimerId != -1)
            vertx.cancelTimer(electionTimerId);
        if (heartbeatTimerId != -1)
            vertx.cancelTimer(heartbeatTimerId);
        if (snapshotTimerId != -1)
            vertx.cancelTimer(snapshotTimerId);
    }

    private void resetElectionTimer() {
        if (electionTimerId != -1) {
            vertx.cancelTimer(electionTimerId);
        }

        long timeout = electionTimeoutMs + (long) (Math.random() * electionTimeoutMs);

        electionTimerId = vertx.setTimer(timeout, id -> startElection());
    }

    private void startElection() {
        if (!running)
            return;

        logger.info("Starting election for node: {}", nodeId);

        state = State.CANDIDATE;
        currentTerm++;
        votedFor = nodeId;
        notifyStateChangeListeners(State.CANDIDATE);

        resetElectionTimer();

        requestVotes();
    }

    private void requestVotes() {
        long term = currentTerm;
        long lastLogIdx = lastLogIndex();
        long lastLogTrm = lastLogIdx > 0 && hasLogEntry(lastLogIdx)
                ? log.get(toArrayIndex(lastLogIdx)).getTerm()
                : snapshotLastTerm;

        AtomicLong voteCount = new AtomicLong(1); // Self vote

        if (clusterNodes.size() == 1) {
            becomeLeader();
            return;
        }

        for (String peerId : clusterNodes) {
            if (!peerId.equals(nodeId)) {
                VoteRequest request = VoteRequest.newBuilder()
                        .setTerm(term)
                        .setCandidateId(nodeId)
                        .setLastLogIndex(lastLogIdx)
                        .setLastLogTerm(lastLogTrm)
                        .build();

                // Using transport (Wait for Future integration)
                transport.sendVoteRequest(peerId, request)
                        .onSuccess(response -> vertx.runOnContext(v -> handleVoteResponse(response, term, voteCount)))
                        .onFailure(e -> logger.warn("Failed to retrieve vote from {}", peerId));

                // Record edge metric for nodeGraph visualization
                rpcCounter.add(1, Attributes.of(
                        AttributeKey.stringKey("source"), nodeId,
                        AttributeKey.stringKey("target"), peerId,
                        AttributeKey.stringKey("type"), "request_vote"
                ));
            }
        }
    }

    private void handleVoteResponse(VoteResponse response, long electionTerm, AtomicLong voteCount) {
        if (state != State.CANDIDATE || currentTerm != electionTerm)
            return;

        if (response.getTerm() > currentTerm) {
            stepDown(response.getTerm());
            return;
        }

        if (response.getVoteGranted()) {
            long votes = voteCount.incrementAndGet();
            if (votes > clusterNodes.size() / 2) {
                becomeLeader();
            }
        }
    }

    private void becomeLeader() {
        if (state != State.CANDIDATE)
            return;

        state = State.LEADER;
        logger.info("Node {} became LEADER for term {}", nodeId, currentTerm);
        notifyStateChangeListeners(State.LEADER);

        if (electionTimerId != -1)
            vertx.cancelTimer(electionTimerId);

        initializeLeaderState();
        startHeartbeats();
        sendHeartbeats(); // Immediate
        startSnapshotScheduler();
    }

    private void initializeLeaderState() {
        long nextIndexValue = lastLogIndex() + 1;
        for (String peer : clusterNodes) {
            if (!peer.equals(nodeId)) {
                nextIndex.put(peer, nextIndexValue);
                matchIndex.put(peer, 0L);
            }
        }
    }

    private void startHeartbeats() {
        heartbeatTimerId = vertx.setPeriodic(heartbeatIntervalMs, id -> sendHeartbeats());
    }

    private void sendHeartbeats() {
        if (state != State.LEADER)
            return;

        for (String peer : clusterNodes) {
            if (!peer.equals(nodeId)) {
                sendAppendEntries(peer, false);
            }
        }
    }

    private void stepDown(long newTerm) {
        if (newTerm > currentTerm) {
            currentTerm = newTerm;
            votedFor = null;
            state = State.FOLLOWER;
            notifyStateChangeListeners(State.FOLLOWER);

            cancelTimers();
            resetElectionTimer();
            logger.info("Stepped down to FOLLOWER. Term: {}", currentTerm);
        }
    }

    // Message Handlers running on Event Loop

    /**
     * Handles incoming Raft messages using pattern matching.
     * <p>Uses Java 21+ sealed interface pattern matching for type-safe,
     * exhaustive message handling.
     * 
     * @param message the incoming RaftMessage
     */
    private void handleMessage(RaftMessage message) {
        // Ensure we are on the Vert.x Context
        if (Vertx.currentContext() != vertx.getOrCreateContext()) {
            vertx.runOnContext(v -> handleMessage(message));
            return;
        }

        // Exhaustive pattern matching - compiler enforces all cases handled
        switch (message) {
            case RaftMessage.Vote vote -> handleVoteRequest(vote.request());
            case RaftMessage.AppendEntries ae -> handleAppendEntriesRequest(ae.request());
        }
    }

    /**
     * Handles RequestVote RPC from a Candidate.
     * <p>Implements Persist-before-Grant:
     * <ul>
     *   <li>Vote is only granted AFTER metadata is durable</li>
     *   <li>Prevents double-voting after crash/restart</li>
     * </ul>
     */
    public Future<VoteResponse> handleVoteRequest(VoteRequest request) {
        Promise<VoteResponse> promise = Promise.promise();
        vertx.runOnContext(v -> {
            try {
                long reqTerm = request.getTerm();

                // Step 1: Reject if stale term
                if (reqTerm < currentTerm) {
                    logger.debug("Rejecting vote: stale term {} < {}", reqTerm, currentTerm);
                    promise.complete(VoteResponse.newBuilder()
                            .setTerm(currentTerm)
                            .setVoteGranted(false)
                            .build());
                    return;
                }

                // Step 2: Step down if higher term
                if (reqTerm > currentTerm) {
                    stepDown(reqTerm);
                }

                // Step 3: Check if we can grant vote
                boolean canGrant = (votedFor == null || votedFor.equals(request.getCandidateId()));

                if (canGrant) {
                    // Step 4: Persist metadata BEFORE granting vote (Persist-before-Grant)
                    persistVote(reqTerm, request.getCandidateId())
                        .onSuccess(v2 -> {
                            // Step 5: Update in-memory state AFTER durability confirmed
                            votedFor = request.getCandidateId();
                            resetElectionTimer();
                            
                            logger.info("Vote granted to {} for term {}", request.getCandidateId(), reqTerm);
                            promise.complete(VoteResponse.newBuilder()
                                    .setTerm(currentTerm)
                                    .setVoteGranted(true)
                                    .build());
                        })
                        .onFailure(err -> {
                            logger.error("Failed to persist vote metadata: {}", err.getMessage());
                            logger.trace("Stack trace for vote metadata persist failure", err);
                            // Vote not granted if we can't persist
                            promise.complete(VoteResponse.newBuilder()
                                    .setTerm(currentTerm)
                                    .setVoteGranted(false)
                                    .build());
                        });
                } else {
                    logger.debug("Vote rejected: already voted for {} in term {}", votedFor, currentTerm);
                    promise.complete(VoteResponse.newBuilder()
                            .setTerm(currentTerm)
                            .setVoteGranted(false)
                            .build());
                }
            } catch (Exception e) {
                logger.error("Error handling vote request: {}", e.getMessage());
                logger.trace("Stack trace for vote request handling error", e);
                promise.fail(e);
            }
        });
        return promise.future();
    }

    /**
     * Persists vote metadata to WAL.
     */
    private Future<Void> persistVote(long term, String candidateId) {
        if (storage == null) {
            return Future.succeededFuture();  // Volatile mode
        }
        return storage.updateMetadata(term, Optional.of(candidateId));
    }

    /**
     * Handles AppendEntries RPC from the Leader.
     * <p>Follows the Prepare → Persist → Apply sequence:
     * <ol>
     *   <li>Consistency check (prevLogIndex/prevLogTerm)</li>
     *   <li>Prepare entries to persist (handle conflicts)</li>
     *   <li>Persist to WAL with sync barrier</li>
     *   <li>Apply to in-memory log</li>
     *   <li>Update commitIndex and trigger applier</li>
     * </ol>
     */
    public Future<AppendEntriesResponse> handleAppendEntriesRequest(AppendEntriesRequest request) {
        Promise<AppendEntriesResponse> promise = Promise.promise();
        vertx.runOnContext(v -> {
            try {
                // Step 1: Term check
                if (request.getTerm() < currentTerm) {
                    logger.debug("Rejecting AppendEntries: stale term {} < {}", request.getTerm(), currentTerm);
                    promise.complete(AppendEntriesResponse.newBuilder()
                            .setTerm(currentTerm)
                            .setSuccess(false)
                            .build());
                    return;
                }

                // Step 2: Step down if higher term
                if (request.getTerm() > currentTerm) {
                    stepDown(request.getTerm());
                }

                resetElectionTimer();

                // Step 3: Consistency check
                if (!hasLogEntry(request.getPrevLogIndex()) ||
                        log.get(toArrayIndex(request.getPrevLogIndex())).getTerm() != request.getPrevLogTerm()) {
                    logger.debug("Rejecting AppendEntries: log inconsistent at prevLogIndex={}",
                                request.getPrevLogIndex());
                    promise.complete(AppendEntriesResponse.newBuilder()
                            .setTerm(currentTerm)
                            .setSuccess(false)
                            .setMatchIndex(lastLogIndex()) // Hint for leader
                            .build());
                    return;
                }

                // Step 4: Prepare entries for persistence (handle conflicts)
                long startIndex = request.getPrevLogIndex() + 1;
                List<LogEntry> entriesToPersist = new ArrayList<>();
                Long truncateFromIndex = null;

                long currentIndex = startIndex;
                for (dev.mars.quorus.controller.raft.grpc.LogEntry entryProto : request.getEntriesList()) {
                    StateMachineCommand command = deserialize(entryProto.getData());
                    LogEntry newEntry = new LogEntry(entryProto.getTerm(), currentIndex, command);

                    if (hasLogEntry(currentIndex)) {
                        if (log.get(toArrayIndex(currentIndex)).getTerm() != entryProto.getTerm()) {
                            // Conflict detected - need to truncate
                            if (truncateFromIndex == null) {
                                truncateFromIndex = currentIndex;
                            }
                            entriesToPersist.add(newEntry);
                        }
                        // Else matches, skip (idempotent)
                    } else {
                        entriesToPersist.add(newEntry);
                    }
                    currentIndex++;
                }

                // Step 5: Persist to WAL (Durability Barrier)
                final Long finalTruncateFrom = truncateFromIndex;
                persistAppendEntries(truncateFromIndex, entriesToPersist)
                    .onSuccess(v2 -> {
                        // Step 6: Apply to in-memory log AFTER durability confirmed
                        if (finalTruncateFrom != null) {
                            int truncateArrayIdx = toArrayIndex(finalTruncateFrom);
                            log.subList(truncateArrayIdx, log.size()).clear();
                        }
                        for (LogEntry entry : entriesToPersist) {
                            if (!hasLogEntry(entry.getIndex())) {
                                log.add(entry);
                            }
                        }

                        // Step 7: Update commit index and apply
                        if (request.getLeaderCommit() > commitIndex) {
                            commitIndex = Math.min(request.getLeaderCommit(), lastLogIndex());
                            applyLog();
                        }

                        logger.debug("AppendEntries success: logSize={}, commitIndex={}", log.size(), commitIndex);
                        promise.complete(AppendEntriesResponse.newBuilder()
                                .setTerm(currentTerm)
                                .setSuccess(true)
                                .setMatchIndex(lastLogIndex())
                                .build());
                    })
                    .onFailure(err -> {
                        logger.error("AppendEntries failed during WAL persist: {}", err.getMessage());
                        logger.trace("Stack trace for AppendEntries WAL persist failure", err);
                        promise.complete(AppendEntriesResponse.newBuilder()
                                .setTerm(currentTerm)
                                .setSuccess(false)
                                .build());
                    });

            } catch (Exception e) {
                logger.error("Error handling append entries request: {}", e.getMessage());
                logger.trace("Stack trace for append entries handling error", e);
                promise.fail(e);
            }
        });
        return promise.future();
    }

    /**
     * Persists append entries to WAL with optional truncation.
     */
    private Future<Void> persistAppendEntries(Long truncateFromIndex, List<LogEntry> entries) {
        if (storage == null) {
            return Future.succeededFuture();  // Volatile mode
        }
        
        if (entries.isEmpty() && truncateFromIndex == null) {
            return Future.succeededFuture();  // Nothing to persist (heartbeat)
        }

        Future<Void> f = Future.succeededFuture();

        // Truncate if needed
        if (truncateFromIndex != null) {
            f = f.compose(v -> storage.truncateSuffix(truncateFromIndex));
        }

        // Append entries
        if (!entries.isEmpty()) {
            List<LogEntryData> entryDataList = entries.stream()
                .map(e -> new LogEntryData(e.getIndex(), e.getTerm(), 
                                           serialize(e.getCommand()).toByteArray()))
                .toList();
            f = f.compose(v -> storage.appendEntries(entryDataList));
        }

        // Sync for durability
        return f.compose(v -> storage.sync());
    }

    private void sendAppendEntries(String target, boolean heartbeat) {
        long nextIdx = nextIndex.getOrDefault(target, 1L);

        // If the follower needs entries we've already compacted, send a snapshot
        if (snapshotLastIndex > 0 && nextIdx <= snapshotLastIndex) {
            sendInstallSnapshot(target);
            return;
        }

        long prevLogIndex = nextIdx - 1;
        long prevLogTerm = 0;
        if (prevLogIndex >= 0 && hasLogEntry(prevLogIndex)) {
            prevLogTerm = log.get(toArrayIndex(prevLogIndex)).getTerm();
        } else if (prevLogIndex == snapshotLastIndex) {
            prevLogTerm = snapshotLastTerm;
        }

        AppendEntriesRequest.Builder builder = AppendEntriesRequest.newBuilder()
                .setTerm(currentTerm)
                .setLeaderId(nodeId)
                .setPrevLogIndex(prevLogIndex)
                .setPrevLogTerm(prevLogTerm)
                .setLeaderCommit(commitIndex);

        if (!heartbeat) {
            long lastIdx = lastLogIndex();
            for (long i = nextIdx; i <= lastIdx; i++) {
                if (hasLogEntry(i)) {
                    LogEntry entry = log.get(toArrayIndex(i));
                    builder.addEntries(dev.mars.quorus.controller.raft.grpc.LogEntry.newBuilder()
                            .setTerm(entry.getTerm())
                            .setIndex(entry.getIndex())
                            .setData(serialize(entry.getCommand()))
                            .build());
                }
            }
        }

        transport.sendAppendEntries(target, builder.build())
                .onSuccess(response -> vertx.runOnContext(v -> handleAppendEntriesResponse(target, response)))
                .onFailure(e -> logger.warn("Failed to send AppendEntries to {}", target));

        // Record edge metric for nodeGraph visualization
        rpcCounter.add(1, Attributes.of(
                AttributeKey.stringKey("source"), nodeId,
                AttributeKey.stringKey("target"), target,
                AttributeKey.stringKey("type"), heartbeat ? "heartbeat" : "append_entries"
        ));
    }

    private void handleAppendEntriesResponse(String peerId, AppendEntriesResponse response) {
        if (state != State.LEADER)
            return;

        if (response.getTerm() > currentTerm) {
            stepDown(response.getTerm());
            return;
        }

        if (response.getSuccess()) {
            matchIndex.put(peerId, response.getMatchIndex());
            nextIndex.put(peerId, response.getMatchIndex() + 1);
            updateCommitIndex();
        } else {
            // Backtrack
            long next = nextIndex.getOrDefault(peerId, 1L);
            nextIndex.put(peerId, Math.max(1, next - 1));
            // Retry immediately? Or wait for next heartbeat/trigger?
            // For simplicity, let next heartbeat handle it or trigger retry
        }
    }

    private void updateCommitIndex() {
        // If there exists an N such that N > commitIndex, a majority of matchIndex[i]
        // >= N,
        // and log[N].term == currentTerm: set commitIndex = N

        List<Long> indices = new ArrayList<>(matchIndex.values());
        indices.add(lastLogIndex()); // Leader's match index
        Collections.sort(indices);
        long N = indices.get(indices.size() / 2); // Majority index

        if (N > commitIndex && hasLogEntry(N) && log.get(toArrayIndex(N)).getTerm() == currentTerm) {
            commitIndex = N;
            applyLog();
        }
    }

    private void applyLog() {
        while (lastApplied < commitIndex) {
            lastApplied++;
            if (!hasLogEntry(lastApplied)) {
                // Entry already compacted by snapshot - skip
                continue;
            }
            LogEntry entry = log.get(toArrayIndex(lastApplied));
            Object result = null;
            Exception exception = null;

            if (entry.getCommand() != null) {
                try {
                    result = stateMachine.apply(entry.getCommand());
                } catch (Exception e) {
                    logger.error("Failed to apply command at index {}: {}", lastApplied, e.getMessage());
                    logger.trace("Stack trace for command apply failure at index {}", lastApplied, e);
                    exception = e;
                }
            }

            // Complete future if this node is leader
            Promise<Object> promise = pendingCommands.remove(lastApplied);
            if (promise != null) {
                if (exception != null) {
                    promise.fail(exception);
                } else {
                    promise.complete(result);
                }
            }
        }
    }

    // ========== SNAPSHOT SCHEDULING ==========

    /**
     * Starts periodic snapshot eligibility checks (leader only).
     * The check fires at the configured interval and triggers a snapshot
     * when (lastApplied - snapshotLastIndex) exceeds the threshold.
     */
    private void startSnapshotScheduler() {
        if (!snapshotEnabled) {
            return;
        }
        if (snapshotTimerId != -1) {
            vertx.cancelTimer(snapshotTimerId);
        }
        snapshotTimerId = vertx.setPeriodic(snapshotCheckIntervalMs, id -> checkAndTakeSnapshot());
        logger.info("Snapshot scheduler started: threshold={}, checkInterval={}ms",
                snapshotThreshold, snapshotCheckIntervalMs);
    }

    /**
     * Checks whether a snapshot is needed and takes one if the threshold is reached.
     * Only the leader takes snapshots to avoid redundant work.
     */
    private void checkAndTakeSnapshot() {
        if (state != State.LEADER || !snapshotEnabled) {
            return;
        }

        long entriesSinceSnapshot = lastApplied - snapshotLastIndex;
        if (entriesSinceSnapshot < snapshotThreshold) {
            logger.debug("Snapshot check: {} entries since last snapshot (threshold: {})",
                    entriesSinceSnapshot, snapshotThreshold);
            return;
        }

        logger.info("Snapshot threshold reached: {} entries since last snapshot, triggering snapshot",
                entriesSinceSnapshot);
        takeSnapshot();
    }

    /**
     * Takes a snapshot of the current state machine state, persists it, and
     * truncates the log prefix up to the snapshot index.
     *
     * <p>Flow: takeSnapshot() → saveSnapshot() → truncatePrefix() → trim in-memory log</p>
     *
     * @return a Future that completes when the snapshot is saved and log is compacted
     */
    public Future<Void> takeSnapshot() {
        if (storage == null) {
            return Future.failedFuture(new IllegalStateException("Cannot take snapshot in volatile mode"));
        }

        long snapshotIndex = lastApplied;
        if (snapshotIndex <= snapshotLastIndex) {
            logger.debug("No new entries to snapshot (lastApplied={}, snapshotLastIndex={})",
                    lastApplied, snapshotLastIndex);
            return Future.succeededFuture();
        }

        // Capture the term of the entry at lastApplied
        long snapshotTerm;
        if (hasLogEntry(snapshotIndex)) {
            snapshotTerm = log.get(toArrayIndex(snapshotIndex)).getTerm();
        } else {
            snapshotTerm = currentTerm;
        }

        long startTime = System.currentTimeMillis();

        // Step 1: Take state machine snapshot (synchronous - on event loop)
        byte[] snapshotData;
        try {
            snapshotData = stateMachine.takeSnapshot();
        } catch (Exception e) {
            logger.error("Failed to take state machine snapshot: {}", e.getMessage(), e);
            return Future.failedFuture(e);
        }

        logger.info("Snapshot taken at index={}, term={}, size={}bytes, compacting {} entries",
                snapshotIndex, snapshotTerm, snapshotData.length,
                snapshotIndex - snapshotLastIndex);

        // Step 2: Persist snapshot to storage (async)
        return storage.saveSnapshot(snapshotData, snapshotIndex, snapshotTerm)
                .compose(v -> {
                    // Step 3: Truncate log prefix in storage
                    return storage.truncatePrefix(snapshotIndex);
                })
                .compose(v -> {
                    // Step 4: Trim in-memory log
                    long entriesToRemove = snapshotIndex - snapshotLastIndex;
                    if (entriesToRemove > 0 && toArrayIndex(snapshotIndex) < log.size()) {
                        // Remove entries from front of list, keep entries after snapshotIndex
                        int removeCount = toArrayIndex(snapshotIndex);
                        if (removeCount > 0) {
                            log.subList(0, removeCount).clear();
                        }
                    }

                    // Step 5: Update snapshot state
                    long previousSnapshotIndex = snapshotLastIndex;
                    snapshotLastIndex = snapshotIndex;
                    snapshotLastTerm = snapshotTerm;

                    // Update sentinel entry at new position 0
                    if (!log.isEmpty()) {
                        log.set(0, new LogEntry(snapshotTerm, snapshotIndex, null));
                    }

                    long duration = System.currentTimeMillis() - startTime;

                    // Record metrics
                    snapshotCounter.add(1);
                    snapshotDuration.record(duration);
                    logCompactedEntries.add(snapshotIndex - previousSnapshotIndex);

                    logger.info("Snapshot complete: snapshotIndex={}, logSize={}, duration={}ms",
                            snapshotIndex, log.size(), duration);
                    return Future.<Void>succeededFuture();
                })
                .onFailure(err -> logger.error("Snapshot failed: {}", err.getMessage(), err));
    }

    /**
     * Returns whether snapshot scheduling is enabled for this node.
     */
    public boolean isSnapshotEnabled() {
        return snapshotEnabled;
    }

    // ========== INSTALL SNAPSHOT (Leader Side) ==========

    /**
     * Sends a snapshot to a lagging follower using chunked transfer.
     * Called from {@link #sendAppendEntries(String, boolean)} when the follower's
     * nextIndex is behind the compacted snapshot boundary.
     *
     * <p>Flow: load snapshot from storage → split into chunks → send sequentially →
     * on final ACK, update nextIndex/matchIndex for the follower.</p>
     *
     * @param target the follower node ID
     */
    private void sendInstallSnapshot(String target) {
        // Prevent concurrent snapshot installs to the same follower
        if (installSnapshotInProgress.contains(target)) {
            logger.debug("InstallSnapshot already in progress for {}, skipping", target);
            return;
        }
        if (storage == null) {
            logger.warn("Cannot send InstallSnapshot: no storage configured");
            return;
        }

        installSnapshotInProgress.add(target);
        logger.info("Sending InstallSnapshot to lagging follower {} (nextIndex={}, snapshotLastIndex={})",
                target, nextIndex.getOrDefault(target, 1L), snapshotLastIndex);

        storage.loadSnapshot()
                .onSuccess(snapshotOpt -> {
                    if (snapshotOpt.isEmpty()) {
                        logger.warn("No snapshot available to send to {}", target);
                        installSnapshotInProgress.remove(target);
                        return;
                    }
                    SnapshotData snapshot = snapshotOpt.get();
                    byte[] data = snapshot.data();
                    int totalChunks = Math.max(1, (int) Math.ceil((double) data.length / SNAPSHOT_CHUNK_SIZE));

                    logger.info("Sending snapshot to {}: {} bytes in {} chunk(s), lastIncludedIndex={}, lastIncludedTerm={}",
                            target, data.length, totalChunks, snapshot.lastIncludedIndex(), snapshot.lastIncludedTerm());

                    sendSnapshotChunk(target, snapshot, data, 0, totalChunks);
                })
                .onFailure(err -> {
                    logger.error("Failed to load snapshot for InstallSnapshot to {}: {}", target, err.getMessage());
                    installSnapshotInProgress.remove(target);
                });
    }

    /**
     * Sends a single snapshot chunk sequentially. On success, sends the next chunk
     * or completes the install if this was the last chunk.
     */
    private void sendSnapshotChunk(String target, SnapshotData snapshot, byte[] data,
                                    int chunkIndex, int totalChunks) {
        if (state != State.LEADER) {
            logger.debug("No longer leader, aborting InstallSnapshot to {}", target);
            installSnapshotInProgress.remove(target);
            return;
        }

        int offset = chunkIndex * SNAPSHOT_CHUNK_SIZE;
        int length = Math.min(SNAPSHOT_CHUNK_SIZE, data.length - offset);
        boolean isLast = (chunkIndex == totalChunks - 1);

        InstallSnapshotRequest request = InstallSnapshotRequest.newBuilder()
                .setTerm(currentTerm)
                .setLeaderId(nodeId)
                .setLastIncludedIndex(snapshot.lastIncludedIndex())
                .setLastIncludedTerm(snapshot.lastIncludedTerm())
                .setChunkIndex(chunkIndex)
                .setTotalChunks(totalChunks)
                .setData(com.google.protobuf.ByteString.copyFrom(data, offset, length))
                .setDone(isLast)
                .build();

        installSnapshotSent.add(1);

        transport.sendInstallSnapshot(target, request)
                .onSuccess(response -> vertx.runOnContext(v -> {
                    if (response.getTerm() > currentTerm) {
                        stepDown(response.getTerm());
                        installSnapshotInProgress.remove(target);
                        return;
                    }

                    if (!response.getSuccess()) {
                        logger.warn("InstallSnapshot chunk {}/{} rejected by {}, retrying from chunk {}",
                                chunkIndex + 1, totalChunks, target, response.getNextChunkIndex());
                        // Retry from the chunk the follower expects
                        sendSnapshotChunk(target, snapshot, data, response.getNextChunkIndex(), totalChunks);
                        return;
                    }

                    if (isLast) {
                        // Snapshot fully installed — update follower tracking
                        long snapIdx = snapshot.lastIncludedIndex();
                        nextIndex.put(target, snapIdx + 1);
                        matchIndex.put(target, snapIdx);
                        installSnapshotInProgress.remove(target);
                        logger.info("InstallSnapshot to {} complete: nextIndex={}, matchIndex={}",
                                target, snapIdx + 1, snapIdx);
                        updateCommitIndex();
                    } else {
                        // Send next chunk
                        sendSnapshotChunk(target, snapshot, data, chunkIndex + 1, totalChunks);
                    }
                }))
                .onFailure(err -> {
                    logger.warn("Failed to send InstallSnapshot chunk {}/{} to {}: {}",
                            chunkIndex + 1, totalChunks, target, err.getMessage());
                    installSnapshotInProgress.remove(target);
                });

        rpcCounter.add(1, Attributes.of(
                AttributeKey.stringKey("source"), nodeId,
                AttributeKey.stringKey("target"), target,
                AttributeKey.stringKey("type"), "install_snapshot"
        ));
    }

    // ========== INSTALL SNAPSHOT (Follower Side) ==========

    /**
     * Handles an incoming InstallSnapshot RPC from the leader.
     * Reassembles chunks, saves the snapshot to storage, restores the state machine,
     * and resets the in-memory log to the snapshot boundary.
     *
     * @param request the InstallSnapshot request (single chunk)
     * @return Future containing the response
     */
    public Future<InstallSnapshotResponse> handleInstallSnapshot(InstallSnapshotRequest request) {
        Promise<InstallSnapshotResponse> promise = Promise.promise();
        vertx.runOnContext(v -> {
            try {
                installSnapshotReceived.add(1);

                // Step 1: Term check
                if (request.getTerm() < currentTerm) {
                    logger.debug("Rejecting InstallSnapshot: stale term {} < {}",
                            request.getTerm(), currentTerm);
                    promise.complete(InstallSnapshotResponse.newBuilder()
                            .setTerm(currentTerm)
                            .setSuccess(false)
                            .setNextChunkIndex(0)
                            .build());
                    return;
                }

                // Step 2: Step down if higher or equal term (we're receiving from a leader)
                if (request.getTerm() > currentTerm) {
                    stepDown(request.getTerm());
                }
                resetElectionTimer();

                String leaderId = request.getLeaderId();
                int chunkIndex = request.getChunkIndex();
                int totalChunks = request.getTotalChunks();

                logger.debug("InstallSnapshot from {}: chunk {}/{}, lastIncludedIndex={}",
                        leaderId, chunkIndex + 1, totalChunks, request.getLastIncludedIndex());

                // Step 3: Get or create chunk assembler
                SnapshotChunkAssembler assembler = pendingInstalls.computeIfAbsent(leaderId,
                        k -> new SnapshotChunkAssembler(totalChunks));

                // Reset if a new snapshot transfer starts
                if (assembler.getTotalChunks() != totalChunks
                        || assembler.getLastIncludedIndex() != request.getLastIncludedIndex()) {
                    assembler = new SnapshotChunkAssembler(totalChunks);
                    pendingInstalls.put(leaderId, assembler);
                }
                assembler.setLastIncludedIndex(request.getLastIncludedIndex());

                // Step 4: Validate chunk sequence
                if (chunkIndex != assembler.getNextExpectedChunk()) {
                    logger.warn("Out-of-order chunk: expected {}, got {}",
                            assembler.getNextExpectedChunk(), chunkIndex);
                    promise.complete(InstallSnapshotResponse.newBuilder()
                            .setTerm(currentTerm)
                            .setSuccess(false)
                            .setNextChunkIndex(assembler.getNextExpectedChunk())
                            .build());
                    return;
                }

                // Step 5: Accept chunk
                assembler.addChunk(chunkIndex, request.getData().toByteArray());

                if (!request.getDone()) {
                    // More chunks expected
                    promise.complete(InstallSnapshotResponse.newBuilder()
                            .setTerm(currentTerm)
                            .setSuccess(true)
                            .setNextChunkIndex(chunkIndex + 1)
                            .build());
                    return;
                }

                // Step 6: All chunks received — assemble and apply
                byte[] snapshotData = assembler.assemble();
                long lastIncludedIndex = request.getLastIncludedIndex();
                long lastIncludedTerm = request.getLastIncludedTerm();
                pendingInstalls.remove(leaderId);

                logger.info("InstallSnapshot complete: assembling {} bytes at index={}, term={}",
                        snapshotData.length, lastIncludedIndex, lastIncludedTerm);

                // Step 7: Persist snapshot and restore state
                Future<Void> saveFuture = (storage != null)
                        ? storage.saveSnapshot(snapshotData, lastIncludedIndex, lastIncludedTerm)
                        : Future.succeededFuture();

                saveFuture
                    .compose(v2 -> {
                        // Truncate old log entries from storage
                        if (storage != null) {
                            return storage.truncatePrefix(lastIncludedIndex);
                        }
                        return Future.succeededFuture();
                    })
                    .onSuccess(v2 -> {
                        // Step 8: Restore state machine
                        stateMachine.restoreSnapshot(snapshotData);

                        // Step 9: Reset in-memory log to snapshot boundary
                        log.clear();
                        log.add(new LogEntry(lastIncludedTerm, lastIncludedIndex, null));

                        // Step 10: Update snapshot and index tracking
                        snapshotLastIndex = lastIncludedIndex;
                        snapshotLastTerm = lastIncludedTerm;
                        lastApplied = lastIncludedIndex;
                        commitIndex = Math.max(commitIndex, lastIncludedIndex);

                        logger.info("Snapshot installed: snapshotLastIndex={}, snapshotLastTerm={}, commitIndex={}",
                                snapshotLastIndex, snapshotLastTerm, commitIndex);

                        promise.complete(InstallSnapshotResponse.newBuilder()
                                .setTerm(currentTerm)
                                .setSuccess(true)
                                .setNextChunkIndex(totalChunks)
                                .build());
                    })
                    .onFailure(err -> {
                        logger.error("Failed to persist installed snapshot: {}", err.getMessage(), err);
                        pendingInstalls.remove(leaderId);
                        promise.complete(InstallSnapshotResponse.newBuilder()
                                .setTerm(currentTerm)
                                .setSuccess(false)
                                .setNextChunkIndex(0)
                                .build());
                    });

            } catch (Exception e) {
                logger.error("Error handling InstallSnapshot: {}", e.getMessage(), e);
                promise.fail(e);
            }
        });
        return promise.future();
    }

    // ========== SNAPSHOT CHUNK ASSEMBLER ==========

    /**
     * Reassembles snapshot chunks received via InstallSnapshot RPCs.
     * Tracks which chunks have been received and produces the complete
     * snapshot byte array when all chunks are present.
     */
    static class SnapshotChunkAssembler {
        private final int totalChunks;
        private final byte[][] chunks;
        private int nextExpectedChunk = 0;
        private long lastIncludedIndex = 0;

        SnapshotChunkAssembler(int totalChunks) {
            this.totalChunks = totalChunks;
            this.chunks = new byte[totalChunks][];
        }

        void addChunk(int index, byte[] data) {
            chunks[index] = data;
            nextExpectedChunk = index + 1;
        }

        byte[] assemble() {
            int totalSize = 0;
            for (byte[] chunk : chunks) {
                if (chunk != null) totalSize += chunk.length;
            }
            byte[] result = new byte[totalSize];
            int offset = 0;
            for (byte[] chunk : chunks) {
                if (chunk != null) {
                    System.arraycopy(chunk, 0, result, offset, chunk.length);
                    offset += chunk.length;
                }
            }
            return result;
        }

        int getTotalChunks() { return totalChunks; }
        int getNextExpectedChunk() { return nextExpectedChunk; }
        long getLastIncludedIndex() { return lastIncludedIndex; }
        void setLastIncludedIndex(long idx) { this.lastIncludedIndex = idx; }
    }

    // ========== SERIALIZATION ==========

    private ByteString serialize(StateMachineCommand cmd) {
        return ProtobufCommandCodec.serialize(cmd);
    }

    private StateMachineCommand deserialize(ByteString data) {
        return ProtobufCommandCodec.deserialize(data);
    }
}
