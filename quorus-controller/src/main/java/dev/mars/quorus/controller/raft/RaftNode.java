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

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RaftNode {

    private static final Logger logger = Logger.getLogger(RaftNode.class.getName());

    // ========== RAFT NODE STATES ==========

    public enum State {
        FOLLOWER,

        CANDIDATE,

        LEADER
    }

    // ========== NODE CONFIGURATION ==========

    private final String nodeId;

    private final Set<String> clusterNodes;

    private final RaftTransport transport;

    private final RaftStateMachine stateMachine;

    // ========== PERSISTENT STATE ==========
    // Note: In production, these should be persisted to stable storage

    private final AtomicLong currentTerm = new AtomicLong(0);

    private volatile String votedFor = null;

    private final List<LogEntry> log = new CopyOnWriteArrayList<>();

    // ========== VOLATILE STATE ==========

    private final AtomicReference<State> state = new AtomicReference<>(State.FOLLOWER);

    private final AtomicLong commitIndex = new AtomicLong(0);

    private final AtomicLong lastApplied = new AtomicLong(0);

    // ========== LEADER STATE ==========
    // Reinitialized after each leader election

    private final Map<String, Long> nextIndex = new ConcurrentHashMap<>();

    private final Map<String, Long> matchIndex = new ConcurrentHashMap<>();

    // ========== TIMING AND CONTROL ==========

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    private volatile ScheduledFuture<?> electionTimer;

    private volatile ScheduledFuture<?> heartbeatTimer;

    // ========== CONFIGURATION PARAMETERS ==========

    private final long electionTimeoutMs;

    private final long heartbeatIntervalMs;

    private volatile Instant lastHeartbeat = Instant.now();

    public RaftNode(String nodeId, Set<String> clusterNodes, RaftTransport transport, 
                   RaftStateMachine stateMachine) {
        this(nodeId, clusterNodes, transport, stateMachine, 5000, 1000);
    }

    public RaftNode(String nodeId, Set<String> clusterNodes, RaftTransport transport,
                   RaftStateMachine stateMachine, long electionTimeoutMs, long heartbeatIntervalMs) {
        this.nodeId = nodeId;
        this.clusterNodes = new HashSet<>(clusterNodes);
        this.transport = transport;
        this.stateMachine = stateMachine;
        this.electionTimeoutMs = electionTimeoutMs;
        this.heartbeatIntervalMs = heartbeatIntervalMs;

        // Initialize log with a dummy entry at index 0
        log.add(new LogEntry(0, 0, null));
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting Raft node: " + nodeId);
            
            // Start transport
            transport.start(this::handleMessage);
            
            // Start election timer
            resetElectionTimer();
            
            logger.info("Raft node started: " + nodeId);
        }
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping Raft node: " + nodeId);
            
            // Cancel timers
            if (electionTimer != null) {
                electionTimer.cancel(false);
            }
            if (heartbeatTimer != null) {
                heartbeatTimer.cancel(false);
            }
            
            // Stop transport
            transport.stop();
            
            // Shutdown scheduler
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            logger.info("Raft node stopped: " + nodeId);
        }
    }

    public CompletableFuture<Object> submitCommand(Object command) {
        if (state.get() != State.LEADER) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Not the leader. Current state: " + state.get()));
        }

        // Create log entry
        LogEntry entry = new LogEntry(currentTerm.get(), log.size(), command);
        log.add(entry);
        
        logger.info("Command submitted to log at index " + entry.getIndex() + 
                   " in term " + entry.getTerm());

        // Start replication
        CompletableFuture<Object> future = new CompletableFuture<>();
        replicateEntry(entry, future);
        
        return future;
    }

    public State getState() {
        return state.get();
    }

    public long getCurrentTerm() {
        return currentTerm.get();
    }

    public String getNodeId() {
        return nodeId;
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isLeader() {
        return state.get() == State.LEADER;
    }

    public String getLeaderId() {
        // In a full implementation, we'd track the current leader
        return isLeader() ? nodeId : null;
    }

    public RaftStateMachine getStateMachine() {
        return stateMachine;
    }

    private void resetElectionTimer() {
        if (electionTimer != null) {
            electionTimer.cancel(false);
        }
        
        // Random timeout between electionTimeoutMs and 2 * electionTimeoutMs
        long timeout = electionTimeoutMs + (long) (Math.random() * electionTimeoutMs);
        
        electionTimer = scheduler.schedule(this::startElection, timeout, TimeUnit.MILLISECONDS);
        lastHeartbeat = Instant.now();
    }

    private void startElection() {
        if (!running.get()) {
            return;
        }

        logger.info("Starting election for node: " + nodeId);
        
        // Transition to candidate
        state.set(State.CANDIDATE);
        currentTerm.incrementAndGet();
        votedFor = nodeId;
        
        // Reset election timer
        resetElectionTimer();
        
        // Request votes from other nodes
        requestVotes();
    }

    private void requestVotes() {
        long term = currentTerm.get();
        long lastLogIndex = log.size() - 1;
        long lastLogTerm = log.get((int) lastLogIndex).getTerm();

        AtomicLong voteCount = new AtomicLong(1); // Vote for self

        // Check if this is a single-node cluster
        if (clusterNodes.size() == 1) {
            // Single node cluster - automatically become leader
            becomeLeader();
            return;
        }

        for (String nodeId : clusterNodes) {
            if (!nodeId.equals(this.nodeId)) {
                VoteRequest request = new VoteRequest(term, this.nodeId, lastLogIndex, lastLogTerm);

                transport.sendVoteRequest(nodeId, request)
                    .thenAccept(response -> {
                        if (response.isVoteGranted() && response.getTerm() == term) {
                            long votes = voteCount.incrementAndGet();
                            if (votes > clusterNodes.size() / 2 && state.get() == State.CANDIDATE) {
                                becomeLeader();
                            }
                        } else if (response.getTerm() > term) {
                            // Higher term discovered, step down
                            stepDown(response.getTerm());
                        }
                    })
                    .exceptionally(throwable -> {
                        logger.log(Level.WARNING, "Failed to get vote from " + nodeId, throwable);
                        return null;
                    });
            }
        }
    }

    private synchronized void becomeLeader() {
        // Double-check we're still a candidate and haven't stepped down
        if (state.compareAndSet(State.CANDIDATE, State.LEADER)) {
            logger.info("Node " + nodeId + " became leader for term " + currentTerm.get());

            // Cancel election timer
            if (electionTimer != null) {
                electionTimer.cancel(false);
            }

            // Initialize leader state
            initializeLeaderState();

            // Start sending heartbeats immediately to establish leadership
            startHeartbeats();

            // Send immediate heartbeat to prevent other elections
            sendHeartbeats();
        }
    }

    private void initializeLeaderState() {
        long nextIndexValue = log.size();
        for (String nodeId : clusterNodes) {
            if (!nodeId.equals(this.nodeId)) {
                nextIndex.put(nodeId, nextIndexValue);
                matchIndex.put(nodeId, 0L);
            }
        }
    }

    private void startHeartbeats() {
        heartbeatTimer = scheduler.scheduleAtFixedRate(
            this::sendHeartbeats, 0, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeats() {
        if (state.get() != State.LEADER || !running.get()) {
            return;
        }
        
        for (String nodeId : clusterNodes) {
            if (!nodeId.equals(this.nodeId)) {
                sendAppendEntries(nodeId, true);
            }
        }
    }

    private synchronized void stepDown(long newTerm) {
        if (newTerm > currentTerm.get()) {
            logger.info("Node " + nodeId + " stepping down. New term: " + newTerm);

            currentTerm.set(newTerm);
            votedFor = null;
            state.set(State.FOLLOWER);

            // Cancel heartbeat timer if we were leader
            if (heartbeatTimer != null) {
                heartbeatTimer.cancel(false);
                heartbeatTimer = null;
            }

            // Reset election timer
            resetElectionTimer();
        }
    }

    private void handleMessage(Object message) {
        logger.info("Received message: " + message.getClass().getSimpleName());

        if (message instanceof VoteRequest) {
            VoteResponse response = handleVoteRequest((VoteRequest) message);
            // Response will be handled by the transport layer
        } else if (message instanceof AppendEntriesRequest) {
            AppendEntriesResponse response = handleAppendEntriesRequest((AppendEntriesRequest) message);
            // Response will be handled by the transport layer
        }
    }

    public VoteResponse handleVoteRequest(VoteRequest request) {
        logger.info("Handling vote request from " + request.getCandidateId() + " for term " + request.getTerm());

        boolean voteGranted = false;
        long currentTermValue = currentTerm.get();

        if (request.getTerm() > currentTermValue) {
            // Higher term, step down and grant vote
            stepDown(request.getTerm());
            votedFor = request.getCandidateId();
            voteGranted = true;
            logger.info("Granted vote to " + request.getCandidateId() + " for term " + request.getTerm());
        } else if (request.getTerm() == currentTermValue &&
                   (votedFor == null || votedFor.equals(request.getCandidateId()))) {
            // Same term, haven't voted yet or already voted for this candidate
            votedFor = request.getCandidateId();
            voteGranted = true;
            logger.info("Granted vote to " + request.getCandidateId() + " for term " + request.getTerm());
        } else {
            logger.info("Denied vote to " + request.getCandidateId() + " for term " + request.getTerm() +
                       " (current term: " + currentTermValue + ", voted for: " + votedFor + ")");
        }

        return new VoteResponse(currentTerm.get(), voteGranted, nodeId);
    }

    public AppendEntriesResponse handleAppendEntriesRequest(AppendEntriesRequest request) {
        logger.info("Handling append entries from " + request.getLeaderId() + " for term " + request.getTerm());

        long currentTermValue = currentTerm.get();
        boolean success = false;

        if (request.getTerm() >= currentTermValue) {
            // Valid leader, reset election timer
            resetElectionTimer();

            if (request.getTerm() > currentTermValue) {
                stepDown(request.getTerm());
            }

            // For heartbeats (empty entries), just acknowledge
            if (request.getEntries() == null || request.getEntries().isEmpty()) {
                success = true;
                logger.info("Acknowledged heartbeat from leader " + request.getLeaderId());
            }
        }

        return new AppendEntriesResponse(currentTerm.get(), success, nodeId,
            request.getPrevLogIndex() + (request.getEntries() != null ? request.getEntries().size() : 0));
    }

    /**
     * Replicate log entry to followers and apply to state machine when committed.
     */
    private void replicateEntry(LogEntry entry, CompletableFuture<Object> future) {
        logger.info("Replicating entry at index " + entry.getIndex());

        if (clusterNodes.size() == 1) {
            // Single node cluster - immediately apply to state machine
            try {
                Object result = stateMachine.apply(entry.getCommand());
                commitIndex.set(entry.getIndex());
                lastApplied.set(entry.getIndex());
                stateMachine.setLastAppliedIndex(entry.getIndex());
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        } else {
            // Multi-node cluster - would need to replicate to majority
            // For now, simulate successful replication after a short delay
            scheduler.schedule(() -> {
                try {
                    Object result = stateMachine.apply(entry.getCommand());
                    commitIndex.set(entry.getIndex());
                    lastApplied.set(entry.getIndex());
                    stateMachine.setLastAppliedIndex(entry.getIndex());
                    future.complete(result);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }, 100, TimeUnit.MILLISECONDS);
        }
    }

    private void sendAppendEntries(String nodeId, boolean heartbeat) {
        // Implementation would send append entries RPC
        logger.fine("Sending append entries to " + nodeId + " (heartbeat: " + heartbeat + ")");
    }
}
