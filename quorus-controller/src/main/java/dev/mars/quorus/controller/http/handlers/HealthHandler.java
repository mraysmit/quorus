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

package dev.mars.quorus.controller.http.handlers;

import dev.mars.quorus.controller.raft.RaftNode;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;

/**
 * Full health check handler for Quorus Controller.
 *
 * <p>Endpoint: {@code GET /health}
 *
 * <p>Provides comprehensive health status including:
 * <ul>
 *   <li>Raft node state, term, commitIndex, leader info</li>
 *   <li>Disk space check</li>
 *   <li>Memory usage check</li>
 * </ul>
 *
 * <p>Returns 503 when any check is degraded.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0 (Vert.x reactive)
 * @since 2025-08-26
 */
public class HealthHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(HealthHandler.class);
    private static final long MIN_FREE_DISK_MB = 100;
    private static final double MIN_FREE_MEMORY_RATIO = 0.1;

    private final RaftNode raftNode;
    private final String version;

    public HealthHandler(RaftNode raftNode, String version) {
        this.raftNode = raftNode;
        this.version = version;
    }

    @Override
    public void handle(RoutingContext ctx) {
        boolean raftOk = raftNode.isRunning();
        boolean diskOk = checkDiskSpace();
        boolean memoryOk = checkMemory();
        boolean allHealthy = raftOk && diskOk && memoryOk;

        JsonObject health = new JsonObject()
                .put("status", allHealthy ? "UP" : "DEGRADED")
                .put("version", version)
                .put("timestamp", Instant.now().toString())
                .put("nodeId", raftNode.getNodeId())
                .put("raft", new JsonObject()
                        .put("state", raftNode.getState().toString())
                        .put("term", raftNode.getCurrentTerm())
                        .put("commitIndex", raftNode.getCommitIndex())
                        .put("isLeader", raftNode.isLeader())
                        .put("leaderId", raftNode.getLeaderId()))
                .put("checks", new JsonObject()
                        .put("raftCluster", raftOk ? "UP" : "DOWN")
                        .put("diskSpace", diskOk ? "UP" : "WARNING")
                        .put("memory", memoryOk ? "UP" : "WARNING"));

        if (!allHealthy) {
            ctx.response().setStatusCode(503);
        }
        ctx.json(health);
    }

    private boolean checkDiskSpace() {
        try {
            File root = new File(".");
            long freeSpaceMb = root.getFreeSpace() / (1024 * 1024);
            return freeSpaceMb >= MIN_FREE_DISK_MB;
        } catch (Exception e) {
            logger.warn("Failed to check disk space: {}", e.getMessage());
            logger.debug("Stack trace for disk space check failure", e);
            return true; // Assume OK if we can't check
        }
    }

    private boolean checkMemory() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long availableMemory = maxMemory - usedMemory;
            return (double) availableMemory / maxMemory >= MIN_FREE_MEMORY_RATIO;
        } catch (Exception e) {
            logger.warn("Failed to check memory: {}", e.getMessage());
            logger.debug("Stack trace for memory check failure", e);
            return true; // Assume OK if we can't check
        }
    }
}
