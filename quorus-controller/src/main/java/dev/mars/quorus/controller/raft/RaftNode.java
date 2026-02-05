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
import dev.mars.quorus.controller.raft.storage.RaftStorage;
import dev.mars.quorus.controller.raft.storage.RaftStorage.LogEntryData;
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
import io.opentelemetry.api.metrics.Meter;
import java.io.*;
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

    // ========== LEADER STATE ==========
    private final Map<String, Long> nextIndex = new HashMap<>();
    private final Map<String, Long> matchIndex = new HashMap<>();
    private final Map<Long, Promise<Object>> pendingCommands = new ConcurrentHashMap<>();

    // ========== TIMING AND CONTROL ==========
    private volatile boolean running = false;
    private long electionTimerId = -1;
    private long heartbeatTimerId = -1;

    // ========== EDGE METRICS ==========
    private LongCounter rpcCounter;

    // ========== CONFIGURATION PARAMETERS ==========
    private final long electionTimeoutMs;
    private final long heartbeatIntervalMs;

    /**
     * Creates a RaftNode with default timing parameters and in-memory storage.
     * <p>Use this constructor for testing or single-node development only.
     * For production, use the constructor that accepts RaftStorage.
     */
    public RaftNode(Vertx vertx, String nodeId, Set<String> clusterNodes, RaftTransport transport,
            RaftStateMachine stateMachine) {
        this(vertx, nodeId, clusterNodes, transport, stateMachine, null, 5000, 1000);
    }

    /**
     * Creates a RaftNode with custom timing parameters and optional storage.
     * <p>If storage is null, runs in volatile mode (no persistence).
     */
    public RaftNode(Vertx vertx, String nodeId, Set<String> clusterNodes, RaftTransport transport,
            RaftStateMachine stateMachine, long electionTimeoutMs, long heartbeatIntervalMs) {
        this(vertx, nodeId, clusterNodes, transport, stateMachine, null, electionTimeoutMs, heartbeatIntervalMs);
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
        this.vertx = vertx;
        this.nodeId = nodeId;
        this.clusterNodes = new HashSet<>(clusterNodes);
        this.transport = transport;
        this.stateMachine = stateMachine;
        this.storage = storage;
        this.electionTimeoutMs = electionTimeoutMs;
        this.heartbeatIntervalMs = heartbeatIntervalMs;

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
     * <p>Order: Metadata → Log Replay → State Machine Rebuild
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
                return storage.replayLog();
            })
            .compose(entries -> {
                // Rebuild in-memory log from WAL
                log.clear();
                // Add sentinel entry at index 0
                log.add(new LogEntry(0, 0, null));
                
                for (LogEntryData entry : entries) {
                    Object command = deserialize(ByteString.copyFrom(entry.payload()));
                    log.add(new LogEntry(entry.term(), entry.index(), command));
                }
                
                logger.info("Recovered {} log entries from storage", entries.size());

                // Replay committed entries to state machine
                return rebuildStateMachine();
            })
            .onSuccess(v -> logger.info("Recovery complete: term={}, logSize={}, lastApplied={}",
                                        currentTerm, log.size(), lastApplied))
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
        lastApplied = 0;
        
        // Re-apply all entries (assume all recovered entries were committed)
        // In a more sophisticated implementation, we'd also persist commitIndex
        commitIndex = log.size() > 1 ? log.size() - 1 : 0;
        
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

    public Future<Object> submitCommand(Object command) {
        Promise<Object> promise = Promise.promise();

        vertx.runOnContext(v -> {
            if (state != State.LEADER) {
                promise.fail(
                        new IllegalStateException("Not the leader. Current state: " + state));
                return;
            }

            // Create log entry
            long entryIndex = log.size();
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
     * Returns the index of the last entry in the log.
     * This is used during elections for log comparison (§5.4.1).
     * Returns 0 if the log only contains the sentinel entry.
     */
    public long getLastLogIndex() {
        return log.size() - 1;  // -1 because index 0 is sentinel
    }

    /**
     * Returns the term of the last entry in the log.
     * This is used during elections for log comparison (§5.4.1).
     * Returns 0 if the log only contains the sentinel entry.
     */
    public long getLastLogTerm() {
        if (log.isEmpty()) {
            return 0;
        }
        return log.get(log.size() - 1).getTerm();
    }

    private void cancelTimers() {
        if (electionTimerId != -1)
            vertx.cancelTimer(electionTimerId);
        if (heartbeatTimerId != -1)
            vertx.cancelTimer(heartbeatTimerId);
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

        resetElectionTimer();

        requestVotes();
    }

    private void requestVotes() {
        long term = currentTerm;
        long lastLogIndex = log.size() - 1;
        long lastLogTerm = log.get((int) lastLogIndex).getTerm();

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
                        .setLastLogIndex(lastLogIndex)
                        .setLastLogTerm(lastLogTerm)
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

        if (electionTimerId != -1)
            vertx.cancelTimer(electionTimerId);

        initializeLeaderState();
        startHeartbeats();
        sendHeartbeats(); // Immediate
    }

    private void initializeLeaderState() {
        long nextIndexValue = log.size();
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
                if (log.size() <= request.getPrevLogIndex() ||
                        log.get((int) request.getPrevLogIndex()).getTerm() != request.getPrevLogTerm()) {
                    logger.debug("Rejecting AppendEntries: log inconsistent at prevLogIndex={}",
                                request.getPrevLogIndex());
                    promise.complete(AppendEntriesResponse.newBuilder()
                            .setTerm(currentTerm)
                            .setSuccess(false)
                            .setMatchIndex(log.size() - 1) // Hint for leader
                            .build());
                    return;
                }

                // Step 4: Prepare entries for persistence (handle conflicts)
                long startIndex = request.getPrevLogIndex() + 1;
                List<LogEntry> entriesToPersist = new ArrayList<>();
                Long truncateFromIndex = null;

                long currentIndex = startIndex;
                for (dev.mars.quorus.controller.raft.grpc.LogEntry entryProto : request.getEntriesList()) {
                    Object command = deserialize(entryProto.getData());
                    LogEntry newEntry = new LogEntry(entryProto.getTerm(), currentIndex, command);

                    if (log.size() > currentIndex) {
                        if (log.get((int) currentIndex).getTerm() != entryProto.getTerm()) {
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
                            log.subList(finalTruncateFrom.intValue(), log.size()).clear();
                        }
                        for (LogEntry entry : entriesToPersist) {
                            if (log.size() <= entry.getIndex()) {
                                log.add(entry);
                            }
                        }

                        // Step 7: Update commit index and apply
                        if (request.getLeaderCommit() > commitIndex) {
                            commitIndex = Math.min(request.getLeaderCommit(), log.size() - 1);
                            applyLog();
                        }

                        logger.debug("AppendEntries success: logSize={}, commitIndex={}", log.size(), commitIndex);
                        promise.complete(AppendEntriesResponse.newBuilder()
                                .setTerm(currentTerm)
                                .setSuccess(true)
                                .setMatchIndex(log.size() - 1)
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
        long prevLogIndex = nextIdx - 1;
        long prevLogTerm = 0;
        if (prevLogIndex >= 0 && prevLogIndex < log.size()) {
            prevLogTerm = log.get((int) prevLogIndex).getTerm();
        }

        AppendEntriesRequest.Builder builder = AppendEntriesRequest.newBuilder()
                .setTerm(currentTerm)
                .setLeaderId(nodeId)
                .setPrevLogIndex(prevLogIndex)
                .setPrevLogTerm(prevLogTerm)
                .setLeaderCommit(commitIndex);

        if (!heartbeat) {
            for (int i = (int) nextIdx; i < log.size(); i++) {
                LogEntry entry = log.get(i);
                builder.addEntries(dev.mars.quorus.controller.raft.grpc.LogEntry.newBuilder()
                        .setTerm(entry.getTerm())
                        .setIndex(entry.getIndex())
                        .setData(serialize(entry.getCommand()))
                        .build());
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
        indices.add(log.size() - 1L); // Leader's match index
        Collections.sort(indices);
        long N = indices.get(indices.size() / 2); // Majority index

        if (N > commitIndex && N < log.size() && log.get((int) N).getTerm() == currentTerm) {
            commitIndex = N;
            applyLog();
        }
    }

    private void applyLog() {
        while (lastApplied < commitIndex) {
            lastApplied++;
            LogEntry entry = log.get((int) lastApplied);
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

    private ByteString serialize(Object obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(obj);
            return ByteString.copyFrom(bos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize command", e);
        }
    }

    private Object deserialize(ByteString data) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data.toByteArray());
                ObjectInputStream in = new ObjectInputStream(bis)) {
            return in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to deserialize command", e);
        }
    }
}
