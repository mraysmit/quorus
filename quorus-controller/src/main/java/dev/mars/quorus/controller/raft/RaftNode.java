package dev.mars.quorus.controller.raft;

import dev.mars.quorus.controller.raft.grpc.AppendEntriesRequest;
import dev.mars.quorus.controller.raft.grpc.AppendEntriesResponse;
import dev.mars.quorus.controller.raft.grpc.VoteRequest;
import dev.mars.quorus.controller.raft.grpc.VoteResponse;
import com.google.protobuf.ByteString;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reactive Raft Node implementation.
 * Runs on the Vert.x Event Loop (Single Threaded), removing the need for
 * synchronization.
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
    private final Map<Long, CompletableFuture<Object>> pendingCommands = new ConcurrentHashMap<>();

    // ========== TIMING AND CONTROL ==========
    private volatile boolean running = false;
    private long electionTimerId = -1;
    private long heartbeatTimerId = -1;

    // ========== CONFIGURATION PARAMETERS ==========
    private final long electionTimeoutMs;
    private final long heartbeatIntervalMs;

    public RaftNode(Vertx vertx, String nodeId, Set<String> clusterNodes, RaftTransport transport,
            RaftStateMachine stateMachine) {
        this(vertx, nodeId, clusterNodes, transport, stateMachine, 5000, 1000);
    }

    public RaftNode(Vertx vertx, String nodeId, Set<String> clusterNodes, RaftTransport transport,
            RaftStateMachine stateMachine, long electionTimeoutMs, long heartbeatIntervalMs) {
        this.vertx = vertx;
        this.nodeId = nodeId;
        this.clusterNodes = new HashSet<>(clusterNodes);
        this.transport = transport; // This should be GrpcRaftTransport
        this.stateMachine = stateMachine;
        this.electionTimeoutMs = electionTimeoutMs;
        this.heartbeatIntervalMs = heartbeatIntervalMs;

        // Initialize log with a dummy entry
        log.add(new LogEntry(0, 0, null));
    }

    public CompletableFuture<Void> start() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        vertx.runOnContext(v -> {
            try {
                if (running) {
                    future.complete(null);
                    return;
                }

                logger.info("Starting Raft node: {}", nodeId);

                // Set reference to this node in transport
                transport.setRaftNode(this);

                // Start transport listener
                transport.start(this::handleMessage);

                // Start election timer
                resetElectionTimer();
                
                running = true;
                future.complete(null);
            } catch (Exception e) {
                logger.error("Failed to start Raft node", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Void> stop() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        vertx.runOnContext(v -> {
            try {
                if (!running) {
                    future.complete(null);
                    return;
                }
                running = false;
                state = State.FOLLOWER;

                cancelTimers();
                transport.stop();
                logger.info("Raft node stopped: {}", nodeId);
                future.complete(null);
            } catch (Exception e) {
                logger.error("Failed to stop Raft node", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Object> submitCommand(Object command) {
        CompletableFuture<Object> future = new CompletableFuture<>();

        vertx.runOnContext(v -> {
            if (state != State.LEADER) {
                future.completeExceptionally(
                        new IllegalStateException("Not the leader. Current state: " + state));
                return;
            }

            // Create log entry
            LogEntry entry = new LogEntry(currentTerm, log.size(), command);
            log.add(entry);
            
            // Register future
            pendingCommands.put(entry.getIndex(), future);

            logger.info("Command submitted at index {} term {}", entry.getIndex(), entry.getTerm());

            // Trigger replication
            for (String peer : clusterNodes) {
                if (!peer.equals(nodeId)) {
                    sendAppendEntries(peer, false);
                }
            }
            
            // Try to commit immediately (crucial for single-node clusters)
            updateCommitIndex();
        });

        return future;
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

    private void handleMessage(Object message) {
        // Ensure we are on the Context
        if (Vertx.currentContext() != vertx.getOrCreateContext()) {
            vertx.runOnContext(v -> handleMessage(message));
            return;
        }

        if (message instanceof VoteRequest) {
            handleVoteRequest((VoteRequest) message);
        } else if (message instanceof AppendEntriesRequest) {
            handleAppendEntriesRequest((AppendEntriesRequest) message);
        }
    }

    public Future<VoteResponse> handleVoteRequest(VoteRequest request) {
        Promise<VoteResponse> promise = Promise.promise();
        vertx.runOnContext(v -> {
            try {
                long reqTerm = request.getTerm();
                boolean grant = false;

                if (reqTerm > currentTerm) {
                    stepDown(reqTerm);
                }

                if (reqTerm == currentTerm && (votedFor == null || votedFor.equals(request.getCandidateId()))) {
                    votedFor = request.getCandidateId();
                    grant = true;
                    resetElectionTimer(); // Reset election timer if we vote
                }

                promise.complete(VoteResponse.newBuilder()
                        .setTerm(currentTerm)
                        .setVoteGranted(grant)
                        .build());
            } catch (Exception e) {
                logger.error("Error handling vote request", e);
                promise.fail(e);
            }
        });
        return promise.future();
    }

    public Future<AppendEntriesResponse> handleAppendEntriesRequest(AppendEntriesRequest request) {
        Promise<AppendEntriesResponse> promise = Promise.promise();
        vertx.runOnContext(v -> {
            try {
                if (request.getTerm() < currentTerm) {
                    promise.complete(AppendEntriesResponse.newBuilder()
                            .setTerm(currentTerm)
                            .setSuccess(false)
                            .build());
                    return;
                }

                if (request.getTerm() > currentTerm) {
                    stepDown(request.getTerm());
                }
                
                resetElectionTimer();

                // Consistency check
                if (log.size() <= request.getPrevLogIndex() ||
                    log.get((int) request.getPrevLogIndex()).getTerm() != request.getPrevLogTerm()) {
                    promise.complete(AppendEntriesResponse.newBuilder()
                            .setTerm(currentTerm)
                            .setSuccess(false)
                            .setMatchIndex(log.size() - 1) // Hint for leader
                            .build());
                    return;
                }

                // Append new entries
                long currentIndex = request.getPrevLogIndex() + 1;
                for (dev.mars.quorus.controller.raft.grpc.LogEntry entryProto : request.getEntriesList()) {
                    // Deserialize command
                    Object command = deserialize(entryProto.getData());
                    LogEntry newEntry = new LogEntry(entryProto.getTerm(), currentIndex, command);
                    
                    if (log.size() > currentIndex) {
                        if (log.get((int) currentIndex).getTerm() != entryProto.getTerm()) {
                            // Conflict, delete existing and following
                            log.subList((int) currentIndex, log.size()).clear();
                            log.add(newEntry);
                        }
                        // Else matches, skip
                    } else {
                        log.add(newEntry);
                    }
                    currentIndex++;
                }

                // Update commit index
                if (request.getLeaderCommit() > commitIndex) {
                    commitIndex = Math.min(request.getLeaderCommit(), log.size() - 1);
                    applyLog();
                }

                promise.complete(AppendEntriesResponse.newBuilder()
                        .setTerm(currentTerm)
                        .setSuccess(true)
                        .setMatchIndex(log.size() - 1)
                        .build());
            } catch (Exception e) {
                logger.error("Error handling append entries request", e);
                promise.fail(e);
            }
        });
        return promise.future();
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
    }

    private void handleAppendEntriesResponse(String peerId, AppendEntriesResponse response) {
        if (state != State.LEADER) return;

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
        // If there exists an N such that N > commitIndex, a majority of matchIndex[i] >= N, 
        // and log[N].term == currentTerm: set commitIndex = N
        
        List<Long> indices = new ArrayList<>(matchIndex.values());
        indices.add(log.size() - 1L); // Leader's match index
        Collections.sort(indices);
        long N = indices.get(indices.size() / 2); // Majority index
        
        if (N > commitIndex && log.get((int) N).getTerm() == currentTerm) {
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
                    logger.error("Failed to apply command at index {}", lastApplied, e);
                    exception = e;
                }
            }
            
            // Complete future if this node is leader
            CompletableFuture<Object> future = pendingCommands.remove(lastApplied);
            if (future != null) {
                if (exception != null) {
                    future.completeExceptionally(exception);
                } else {
                    future.complete(result);
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
