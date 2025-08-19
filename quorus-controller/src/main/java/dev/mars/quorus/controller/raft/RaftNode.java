/*
 * Copyright 2024 Quorus Project
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

/**
 * Core Raft consensus node implementation.
 * Implements the Raft consensus algorithm for distributed coordination.
 */
public class RaftNode {

    private static final Logger logger = Logger.getLogger(RaftNode.class.getName());

    // Raft node states
    public enum State {
        FOLLOWER,
        CANDIDATE, 
        LEADER
    }

    // Node configuration
    private final String nodeId;
    private final Set<String> clusterNodes;
    private final RaftTransport transport;
    private final RaftStateMachine stateMachine;

    // Persistent state (should be persisted to disk)
    private final AtomicLong currentTerm = new AtomicLong(0);
    private volatile String votedFor = null;
    private final List<LogEntry> log = new CopyOnWriteArrayList<>();

    // Volatile state
    private final AtomicReference<State> state = new AtomicReference<>(State.FOLLOWER);
    private final AtomicLong commitIndex = new AtomicLong(0);
    private final AtomicLong lastApplied = new AtomicLong(0);

    // Leader state (reinitialized after election)
    private final Map<String, Long> nextIndex = new ConcurrentHashMap<>();
    private final Map<String, Long> matchIndex = new ConcurrentHashMap<>();

    // Timing and control
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private volatile ScheduledFuture<?> electionTimer;
    private volatile ScheduledFuture<?> heartbeatTimer;

    // Configuration
    private final long electionTimeoutMs;
    private final long heartbeatIntervalMs;
    private volatile Instant lastHeartbeat = Instant.now();

    /**
     * Create a new Raft node.
     */
    public RaftNode(String nodeId, Set<String> clusterNodes, RaftTransport transport, 
                   RaftStateMachine stateMachine) {
        this(nodeId, clusterNodes, transport, stateMachine, 5000, 1000);
    }

    /**
     * Create a new Raft node with custom timing.
     */
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

    /**
     * Start the Raft node.
     */
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

    /**
     * Stop the Raft node.
     */
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

    /**
     * Submit a command to be replicated via Raft consensus.
     */
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

    /**
     * Get current node state.
     */
    public State getState() {
        return state.get();
    }

    /**
     * Get current term.
     */
    public long getCurrentTerm() {
        return currentTerm.get();
    }

    /**
     * Get node ID.
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * Check if this node is the leader.
     */
    public boolean isLeader() {
        return state.get() == State.LEADER;
    }

    /**
     * Get current leader ID (if known).
     */
    public String getLeaderId() {
        // In a full implementation, we'd track the current leader
        return isLeader() ? nodeId : null;
    }

    /**
     * Reset election timer with random timeout.
     */
    private void resetElectionTimer() {
        if (electionTimer != null) {
            electionTimer.cancel(false);
        }
        
        // Random timeout between electionTimeoutMs and 2 * electionTimeoutMs
        long timeout = electionTimeoutMs + (long) (Math.random() * electionTimeoutMs);
        
        electionTimer = scheduler.schedule(this::startElection, timeout, TimeUnit.MILLISECONDS);
        lastHeartbeat = Instant.now();
    }

    /**
     * Start leader election.
     */
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

    /**
     * Request votes from other nodes.
     */
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

    /**
     * Become the leader.
     */
    private void becomeLeader() {
        if (state.compareAndSet(State.CANDIDATE, State.LEADER)) {
            logger.info("Node " + nodeId + " became leader for term " + currentTerm.get());
            
            // Cancel election timer
            if (electionTimer != null) {
                electionTimer.cancel(false);
            }
            
            // Initialize leader state
            initializeLeaderState();
            
            // Start sending heartbeats
            startHeartbeats();
        }
    }

    /**
     * Initialize leader state.
     */
    private void initializeLeaderState() {
        long nextIndexValue = log.size();
        for (String nodeId : clusterNodes) {
            if (!nodeId.equals(this.nodeId)) {
                nextIndex.put(nodeId, nextIndexValue);
                matchIndex.put(nodeId, 0L);
            }
        }
    }

    /**
     * Start sending heartbeats.
     */
    private void startHeartbeats() {
        heartbeatTimer = scheduler.scheduleAtFixedRate(
            this::sendHeartbeats, 0, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Send heartbeats to all followers.
     */
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

    /**
     * Step down from leadership.
     */
    private void stepDown(long newTerm) {
        logger.info("Node " + nodeId + " stepping down. New term: " + newTerm);
        
        currentTerm.set(newTerm);
        votedFor = null;
        state.set(State.FOLLOWER);
        
        // Cancel heartbeat timer
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel(false);
        }
        
        // Reset election timer
        resetElectionTimer();
    }

    // Placeholder methods for message handling and replication
    private void handleMessage(Object message) {
        // Implementation would handle different message types
        logger.info("Received message: " + message.getClass().getSimpleName());
    }

    private void replicateEntry(LogEntry entry, CompletableFuture<Object> future) {
        // Implementation would replicate entry to followers
        logger.info("Replicating entry at index " + entry.getIndex());
    }

    private void sendAppendEntries(String nodeId, boolean heartbeat) {
        // Implementation would send append entries RPC
        logger.fine("Sending append entries to " + nodeId + " (heartbeat: " + heartbeat + ")");
    }
}
