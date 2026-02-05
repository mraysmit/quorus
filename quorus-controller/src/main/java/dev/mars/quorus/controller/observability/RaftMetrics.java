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

package dev.mars.quorus.controller.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenTelemetry metrics for Raft subsystem including thread pool monitoring.
 * 
 * <p>Provides the following metrics:
 * <ul>
 *   <li>quorus.raft.threadpool.active (gauge) - Currently active threads in Raft I/O pool</li>
 *   <li>quorus.raft.threadpool.queued (gauge) - Tasks waiting in queue</li>
 *   <li>quorus.raft.threadpool.completed (counter) - Total completed tasks</li>
 *   <li>quorus.raft.threadpool.pool_size (gauge) - Current pool size</li>
 *   <li>quorus.raft.rpc.vote_requests (counter) - Vote requests sent</li>
 *   <li>quorus.raft.rpc.append_entries (counter) - AppendEntries RPCs sent</li>
 * </ul>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0 (T3.2: Bounded Thread Pools)
 * @since 2026-01-30
 */
public class RaftMetrics {

    private static final Logger logger = LoggerFactory.getLogger(RaftMetrics.class);
    private static final String METER_NAME = "quorus-controller-raft";

    // Singleton instance
    private static RaftMetrics instance;

    // Counters
    private final LongCounter voteRequestsCounter;
    private final LongCounter appendEntriesCounter;
    private final LongCounter completedTasksCounter;

    // AtomicLong for gauges
    private final AtomicLong activeThreads = new AtomicLong(0);
    private final AtomicLong queuedTasks = new AtomicLong(0);
    private final AtomicLong poolSize = new AtomicLong(0);

    // Reference to thread pool for monitoring
    private final AtomicReference<ThreadPoolExecutor> threadPoolRef = new AtomicReference<>();

    // Attribute keys
    private static final AttributeKey<String> NODE_ID_KEY = AttributeKey.stringKey("node.id");
    private static final AttributeKey<String> TARGET_ID_KEY = AttributeKey.stringKey("target.id");
    private static final AttributeKey<String> RESULT_KEY = AttributeKey.stringKey("result");

    private RaftMetrics() {
        Meter meter = GlobalOpenTelemetry.getMeter(METER_NAME);

        // Initialize counters
        voteRequestsCounter = meter.counterBuilder("quorus.raft.rpc.vote_requests")
                .setDescription("Total number of vote requests sent")
                .setUnit("1")
                .build();

        appendEntriesCounter = meter.counterBuilder("quorus.raft.rpc.append_entries")
                .setDescription("Total number of AppendEntries RPCs sent")
                .setUnit("1")
                .build();

        completedTasksCounter = meter.counterBuilder("quorus.raft.threadpool.completed")
                .setDescription("Total completed tasks in Raft I/O thread pool")
                .setUnit("1")
                .build();

        // Initialize gauges
        meter.gaugeBuilder("quorus.raft.threadpool.active")
                .setDescription("Currently active threads in Raft I/O pool")
                .ofLongs()
                .buildWithCallback(measurement -> {
                    updateFromThreadPool();
                    measurement.record(activeThreads.get());
                });

        meter.gaugeBuilder("quorus.raft.threadpool.queued")
                .setDescription("Tasks waiting in Raft I/O thread pool queue")
                .ofLongs()
                .buildWithCallback(measurement -> {
                    updateFromThreadPool();
                    measurement.record(queuedTasks.get());
                });

        meter.gaugeBuilder("quorus.raft.threadpool.pool_size")
                .setDescription("Current pool size of Raft I/O thread pool")
                .ofLongs()
                .buildWithCallback(measurement -> {
                    updateFromThreadPool();
                    measurement.record(poolSize.get());
                });

        logger.info("RaftMetrics initialized with thread pool monitoring");
    }

    /**
     * Gets the singleton RaftMetrics instance.
     */
    public static synchronized RaftMetrics getInstance() {
        if (instance == null) {
            instance = new RaftMetrics();
        }
        return instance;
    }

    /**
     * Registers a ThreadPoolExecutor for monitoring.
     * 
     * @param executor the thread pool to monitor
     */
    public void registerThreadPool(ThreadPoolExecutor executor) {
        threadPoolRef.set(executor);
        logger.debug("Registered ThreadPoolExecutor for Raft metrics monitoring");
    }

    /**
     * Unregisters the current ThreadPoolExecutor.
     */
    public void unregisterThreadPool() {
        threadPoolRef.set(null);
        activeThreads.set(0);
        queuedTasks.set(0);
        poolSize.set(0);
        logger.debug("Unregistered ThreadPoolExecutor from Raft metrics");
    }

    private void updateFromThreadPool() {
        ThreadPoolExecutor executor = threadPoolRef.get();
        if (executor != null) {
            activeThreads.set(executor.getActiveCount());
            queuedTasks.set(executor.getQueue().size());
            poolSize.set(executor.getPoolSize());
        }
    }

    /**
     * Records a vote request sent.
     * 
     * @param sourceNode the node sending the request
     * @param targetNode the target node
     * @param success true if successful
     */
    public void recordVoteRequest(String sourceNode, String targetNode, boolean success) {
        voteRequestsCounter.add(1, Attributes.of(
                NODE_ID_KEY, sourceNode,
                TARGET_ID_KEY, targetNode,
                RESULT_KEY, success ? "success" : "failure"
        ));
    }

    /**
     * Records an AppendEntries RPC sent.
     * 
     * @param sourceNode the node sending the request
     * @param targetNode the target node
     * @param success true if successful
     */
    public void recordAppendEntries(String sourceNode, String targetNode, boolean success) {
        appendEntriesCounter.add(1, Attributes.of(
                NODE_ID_KEY, sourceNode,
                TARGET_ID_KEY, targetNode,
                RESULT_KEY, success ? "success" : "failure"
        ));
    }

    /**
     * Records a completed task in the thread pool.
     */
    public void recordTaskCompleted() {
        completedTasksCounter.add(1);
    }

    /**
     * Gets the current active thread count (for testing).
     */
    public long getActiveThreadCount() {
        updateFromThreadPool();
        return activeThreads.get();
    }

    /**
     * Gets the current queue size (for testing).
     */
    public long getQueuedTaskCount() {
        updateFromThreadPool();
        return queuedTasks.get();
    }

    /**
     * Gets the current pool size (for testing).
     */
    public long getPoolSize() {
        updateFromThreadPool();
        return poolSize.get();
    }
}
