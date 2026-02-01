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
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
/**
 * Test utility for in-memory Raft transport.
 * Allows Raft nodes to communicate without real network connections.
 * Supports advanced chaos testing including:
 * - Configurable latency and packet drop
 * - Network partitions (isolate nodes)
 * - Message reordering
 * - Bandwidth throttling
 * - Byzantine and crash-recovery failure modes
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2026-01-05
 */

public class InMemoryTransportSimulator implements RaftTransport {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryTransportSimulator.class);

    // Global registry of all transport instances
    private static final Map<String, InMemoryTransportSimulator> transports = new ConcurrentHashMap<>();
    
    // Network partition state (set of isolated node groups)
    private static final Set<Set<String>> networkPartitions = ConcurrentHashMap.newKeySet();

    private final String nodeId;
    private final Executor executor = Executors.newCachedThreadPool();
    private volatile Consumer<RaftMessage> messageHandler;
    private volatile boolean running = false;
    private RaftNode raftNode;
    
    // Chaos Configuration
    private final Random random = new Random();
    private volatile int minLatencyMs = 5;
    private volatile int maxLatencyMs = 15;
    private volatile double dropRate = 0.0; // 0.0 to 1.0
    
    // Message Reordering Configuration
    private volatile boolean reorderingEnabled = false;
    private volatile double reorderProbability = 0.0; // 0.0 to 1.0
    private volatile int maxReorderDelayMs = 100;
    private final PriorityBlockingQueue<DelayedMessage> messageQueue = new PriorityBlockingQueue<>();
    private volatile ScheduledExecutorService reorderExecutor;
    
    // Bandwidth Throttling Configuration
    private volatile boolean throttlingEnabled = false;
    private volatile long maxBytesPerSecond = Long.MAX_VALUE;
    private final AtomicLong bytesSentThisSecond = new AtomicLong(0);
    private volatile long lastResetTime = System.currentTimeMillis();
    
    // Failure Mode Configuration
    private volatile FailureMode failureMode = FailureMode.NONE;
    private volatile double byzantineCorruptionRate = 0.0;
    private volatile boolean crashed = false;
    
    /**
     * Failure modes for sophisticated chaos testing.
     */
    public enum FailureMode {
        NONE,           // Normal operation
        CRASH,          // Node crashes (stops responding)
        BYZANTINE,      // Node sends corrupted/malicious responses
        SLOW,           // Node responds very slowly
        FLAKY           // Node intermittently fails
    }

    public InMemoryTransportSimulator(String nodeId) {
        this.nodeId = nodeId;
        startReorderProcessor();
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void setRaftNode(RaftNode node) {
        this.raftNode = node;
    }

    /**
     * Configure chaos parameters for this transport.
     * @param minLatencyMs minimum latency in milliseconds
     * @param maxLatencyMs maximum latency in milliseconds
     * @param dropRate probability of dropping a packet (0.0 to 1.0)
     */
    public void setChaosConfig(int minLatencyMs, int maxLatencyMs, double dropRate) {
        this.minLatencyMs = minLatencyMs;
        this.maxLatencyMs = maxLatencyMs;
        this.dropRate = dropRate;
    }
    
    /**
     * Enable message reordering with specified probability.
     * @param enabled whether reordering is enabled
     * @param reorderProbability probability that a message will be reordered (0.0 to 1.0)
     * @param maxReorderDelayMs maximum delay to apply to reordered messages
     */
    public void setReorderingConfig(boolean enabled, double reorderProbability, int maxReorderDelayMs) {
        this.reorderingEnabled = enabled;
        this.reorderProbability = reorderProbability;
        this.maxReorderDelayMs = maxReorderDelayMs;
    }
    
    /**
     * Enable bandwidth throttling.
     * @param enabled whether throttling is enabled
     * @param maxBytesPerSecond maximum bytes per second to transmit
     */
    public void setThrottlingConfig(boolean enabled, long maxBytesPerSecond) {
        this.throttlingEnabled = enabled;
        this.maxBytesPerSecond = maxBytesPerSecond;
    }
    
    /**
     * Set the failure mode for this transport.
     * @param mode the failure mode to use
     * @param byzantineCorruptionRate for BYZANTINE mode, probability of corrupting a response
     */
    public void setFailureMode(FailureMode mode, double byzantineCorruptionRate) {
        this.failureMode = mode;
        this.byzantineCorruptionRate = byzantineCorruptionRate;
        if (mode == FailureMode.CRASH) {
            this.crashed = true;
        } else {
            this.crashed = false;
        }
    }
    
    /**
     * Recover from a crash failure mode.
     */
    public void recoverFromCrash() {
        this.crashed = false;
        if (this.failureMode == FailureMode.CRASH) {
            this.failureMode = FailureMode.NONE;
        }
    }
    
    /**
     * Create a network partition. Nodes in different partitions cannot communicate.
     * @param partition1 first partition of node IDs
     * @param partition2 second partition of node IDs
     */
    public static void createPartition(Set<String> partition1, Set<String> partition2) {
        networkPartitions.add(new HashSet<>(partition1));
        networkPartitions.add(new HashSet<>(partition2));
        logger.info("Created network partition: {} | {}", partition1, partition2);
    }
    
    /**
     * Heal all network partitions.
     */
    public static void healPartitions() {
        networkPartitions.clear();
        logger.info("Healed all network partitions");
    }
    
    /**
     * Check if two nodes can communicate (not partitioned).
     */
    private static boolean canCommunicate(String sourceId, String targetId) {
        if (networkPartitions.isEmpty()) {
            return true;
        }
        
        // Find which partitions the nodes belong to
        for (Set<String> partition : networkPartitions) {
            boolean sourceInPartition = partition.contains(sourceId);
            boolean targetInPartition = partition.contains(targetId);
            
            // If source is in partition and target is not, or vice versa, they cannot communicate
            if (sourceInPartition && !targetInPartition) {
                return false;
            }
            if (!sourceInPartition && targetInPartition) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Start the background processor for message reordering.
     */
    private void startReorderProcessor() {
        if (reorderExecutor == null || reorderExecutor.isShutdown()) {
            reorderExecutor = Executors.newSingleThreadScheduledExecutor();
            reorderExecutor.scheduleAtFixedRate(this::processReorderedMessages, 
                10, 10, TimeUnit.MILLISECONDS);
        }
    }
    
    /**
     * Process messages in the reorder queue.
     */
    private void processReorderedMessages() {
        long now = System.currentTimeMillis();
        DelayedMessage message;
        
        while ((message = messageQueue.peek()) != null && message.deliveryTime <= now) {
            message = messageQueue.poll();
            if (message != null) {
                message.deliver();
            }
        }
    }
    
    /**
     * Apply bandwidth throttling if enabled.
     */
    private void applyThrottling(int messageSize) throws InterruptedException {
        if (!throttlingEnabled) {
            return;
        }
        
        long now = System.currentTimeMillis();
        if (now - lastResetTime >= 1000) {
            bytesSentThisSecond.set(0);
            lastResetTime = now;
        }
        
        long currentBytes = bytesSentThisSecond.addAndGet(messageSize);
        if (currentBytes > maxBytesPerSecond) {
            long delayMs = 1000 - (now - lastResetTime);
            if (delayMs > 0) {
                Thread.sleep(delayMs);
                bytesSentThisSecond.set(0);
                lastResetTime = System.currentTimeMillis();
            }
        }
    }

    @Override
    public void start(Consumer<RaftMessage> messageHandler) {
        this.messageHandler = messageHandler;
        this.running = true;
        transports.put(nodeId, this);
        logger.info("Started in-memory transport for node: {}", nodeId);
    }

    @Override
    public void stop() {
        this.running = false;
        transports.remove(nodeId);
        if (reorderExecutor != null && !reorderExecutor.isShutdown()) {
            reorderExecutor.shutdown();
        }
        logger.info("Stopped in-memory transport for node: {}", nodeId);
    }

    @Override
    public Future<VoteResponse> sendVoteRequest(String targetNodeId, VoteRequest request) {
        Promise<VoteResponse> promise = Promise.promise();
        executor.execute(() -> {
            try {
                // Check if crashed
                if (crashed) {
                    promise.fail(new RuntimeException("Node crashed"));
                    return;
                }
                
                // Check for network partition
                if (!canCommunicate(nodeId, targetNodeId)) {
                    logger.debug("Network partition prevents communication from {} to {}", nodeId, targetNodeId);
                    promise.fail(new RuntimeException("Network partition"));
                    return;
                }
                
                // Simulate Packet Drop
                if (dropRate > 0 && random.nextDouble() < dropRate) {
                    logger.debug("Dropped VoteRequest from {} to {}", nodeId, targetNodeId);
                    promise.fail(new RuntimeException("Network packet dropped (Chaos)"));
                    return;
                }

                InMemoryTransportSimulator targetTransport = transports.get(targetNodeId);
                if (targetTransport == null || !targetTransport.running) {
                    promise.fail(new RuntimeException("Target node not available: " + targetNodeId));
                    return;
                }

                // Apply bandwidth throttling
                int messageSize = request.getSerializedSize();
                applyThrottling(messageSize);

                // Simulate network delay based on failure mode
                long delay = calculateDelay();
                Thread.sleep(delay);

                // Check for message reordering
                if (reorderingEnabled && random.nextDouble() < reorderProbability) {
                    int reorderDelay = random.nextInt(maxReorderDelayMs);
                    DelayedMessage delayed = new DelayedMessage(
                        System.currentTimeMillis() + delay + reorderDelay,
                        () -> {
                            VoteResponse response = targetTransport.handleVoteRequest(request);
                            // Apply Byzantine corruption if enabled
                            if (failureMode == FailureMode.BYZANTINE && random.nextDouble() < byzantineCorruptionRate) {
                                response = corruptVoteResponse(response);
                            }
                            promise.complete(response);
                        }
                    );
                    messageQueue.offer(delayed);
                    logger.debug("Reordered VoteRequest from {} to {} (delay: {}ms)", nodeId, targetNodeId, reorderDelay);
                    return;
                }

                // Process vote request
                VoteResponse response = targetTransport.handleVoteRequest(request);
                
                // Apply Byzantine corruption if enabled
                if (failureMode == FailureMode.BYZANTINE && random.nextDouble() < byzantineCorruptionRate) {
                    response = corruptVoteResponse(response);
                    logger.debug("Corrupted VoteResponse from {} to {} (Byzantine)", targetNodeId, nodeId);
                }
                
                logger.debug("Vote request from {} to {}: {}", nodeId, targetNodeId, response.getVoteGranted());
                
                promise.complete(response);
            } catch (Exception e) {
                promise.fail(e);
            }
        });
        return promise.future();
    }

    @Override
    public Future<AppendEntriesResponse> sendAppendEntries(String targetNodeId, 
                                                                     AppendEntriesRequest request) {
        Promise<AppendEntriesResponse> promise = Promise.promise();
        executor.execute(() -> {
            try {
                // Check if crashed
                if (crashed) {
                    promise.fail(new RuntimeException("Node crashed"));
                    return;
                }
                
                // Check for network partition
                if (!canCommunicate(nodeId, targetNodeId)) {
                    logger.debug("Network partition prevents communication from {} to {}", nodeId, targetNodeId);
                    promise.fail(new RuntimeException("Network partition"));
                    return;
                }
                
                // Simulate Packet Drop
                if (dropRate > 0 && random.nextDouble() < dropRate) {
                    logger.debug("Dropped AppendEntries from {} to {}", nodeId, targetNodeId);
                    promise.fail(new RuntimeException("Network packet dropped (Chaos)"));
                    return;
                }

                InMemoryTransportSimulator targetTransport = transports.get(targetNodeId);
                if (targetTransport == null || !targetTransport.running) {
                    promise.fail(new RuntimeException("Target node not available: " + targetNodeId));
                    return;
                }

                // Apply bandwidth throttling
                int messageSize = request.getSerializedSize();
                applyThrottling(messageSize);

                // Simulate network delay based on failure mode
                long delay = calculateDelay();
                Thread.sleep(delay);

                // Check for message reordering
                if (reorderingEnabled && random.nextDouble() < reorderProbability) {
                    int reorderDelay = random.nextInt(maxReorderDelayMs);
                    DelayedMessage delayed = new DelayedMessage(
                        System.currentTimeMillis() + delay + reorderDelay,
                        () -> {
                            AppendEntriesResponse response = targetTransport.handleAppendEntries(request);
                            // Apply Byzantine corruption if enabled
                            if (failureMode == FailureMode.BYZANTINE && random.nextDouble() < byzantineCorruptionRate) {
                                response = corruptAppendEntriesResponse(response);
                            }
                            promise.complete(response);
                        }
                    );
                    messageQueue.offer(delayed);
                    logger.debug("Reordered AppendEntries from {} to {} (delay: {}ms)", nodeId, targetNodeId, reorderDelay);
                    return;
                }

                // Process append entries request
                AppendEntriesResponse response = targetTransport.handleAppendEntries(request);
                
                // Apply Byzantine corruption if enabled
                if (failureMode == FailureMode.BYZANTINE && random.nextDouble() < byzantineCorruptionRate) {
                    response = corruptAppendEntriesResponse(response);
                    logger.debug("Corrupted AppendEntriesResponse from {} to {} (Byzantine)", targetNodeId, nodeId);
                }
                
                logger.debug("Append entries from {} to {}: {}", nodeId, targetNodeId, response.getSuccess());
                
                promise.complete(response);
            } catch (Exception e) {
                promise.fail(e);
            }
        });
        return promise.future();
    }
    
    /**
     * Calculate delay based on current failure mode.
     */
    private long calculateDelay() {
        if (failureMode == FailureMode.SLOW) {
            // SLOW mode: 10x normal latency
            return (minLatencyMs + random.nextInt(Math.max(1, maxLatencyMs - minLatencyMs + 1))) * 10;
        } else if (failureMode == FailureMode.FLAKY && random.nextDouble() < 0.5) {
            // FLAKY mode: 50% chance of high latency
            return (minLatencyMs + random.nextInt(Math.max(1, maxLatencyMs - minLatencyMs + 1))) * 5;
        } else {
            // Normal latency
            return minLatencyMs + random.nextInt(Math.max(1, maxLatencyMs - minLatencyMs + 1));
        }
    }
    
    /**
     * Corrupt a VoteResponse for Byzantine testing.
     */
    private VoteResponse corruptVoteResponse(VoteResponse original) {
        return VoteResponse.newBuilder()
            .setTerm(original.getTerm() + random.nextInt(10)) // Wrong term
            .setVoteGranted(!original.getVoteGranted()) // Flip vote
            .build();
    }
    
    /**
     * Corrupt an AppendEntriesResponse for Byzantine testing.
     */
    private AppendEntriesResponse corruptAppendEntriesResponse(AppendEntriesResponse original) {
        return AppendEntriesResponse.newBuilder()
            .setTerm(original.getTerm() + random.nextInt(10)) // Wrong term
            .setSuccess(!original.getSuccess()) // Flip success
            .setMatchIndex(random.nextInt(100)) // Wrong match index
            .build();
    }


    private VoteResponse handleVoteRequest(VoteRequest request) {
        if (raftNode != null) {
            return raftNode.handleVoteRequest(request).toCompletionStage().toCompletableFuture().join();
        }
        
        if (messageHandler != null) {
            messageHandler.accept(new RaftMessage.Vote(request));
        }
        
        logger.warn("RaftNode not set for transport {}, returning failure", nodeId);
        return VoteResponse.newBuilder()
                .setTerm(request.getTerm())
                .setVoteGranted(false)
                .build();
    }

    private AppendEntriesResponse handleAppendEntries(AppendEntriesRequest request) {
        if (raftNode != null) {
            return raftNode.handleAppendEntriesRequest(request).toCompletionStage().toCompletableFuture().join();
        }

        if (messageHandler != null) {
            messageHandler.accept(new RaftMessage.AppendEntries(request));
        }
        
        logger.warn("RaftNode not set for transport {}, returning failure", nodeId);
        return AppendEntriesResponse.newBuilder()
                .setTerm(request.getTerm())
                .setSuccess(false)
                .build();
    }

    public static Map<String, InMemoryTransportSimulator> getAllTransports() {
        return new ConcurrentHashMap<>(transports);
    }

    /**
     * Clear all registered transports (for testing).
     */
    public static void clearAllTransports() {
        transports.clear();
        healPartitions();
    }
    
    /**
     * Helper class for delayed message delivery (message reordering).
     */
    private static class DelayedMessage implements Comparable<DelayedMessage> {
        final long deliveryTime;
        final Runnable action;
        
        DelayedMessage(long deliveryTime, Runnable action) {
            this.deliveryTime = deliveryTime;
            this.action = action;
        }
        
        void deliver() {
            try {
                action.run();
            } catch (Exception e) {
                logger.error("Error delivering delayed message", e);
            }
        }
        
        @Override
        public int compareTo(DelayedMessage other) {
            return Long.compare(this.deliveryTime, other.deliveryTime);
        }
    }
}
