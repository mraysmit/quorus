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

package dev.mars.quorus.api.service;

import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftClusterConfig;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service responsible for discovering and maintaining connections to Raft controller nodes.
 * This service helps the API layer find the current leader and handle failover scenarios.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
@ApplicationScoped
public class ControllerDiscoveryService {

    private static final Logger logger = Logger.getLogger(ControllerDiscoveryService.class.getName());
    
    private static final int DISCOVERY_TIMEOUT_SECONDS = 3;
    private static final int HEALTH_CHECK_INTERVAL_SECONDS = 10;

    @Inject
    RaftClusterConfig clusterConfig;

    private final ConcurrentHashMap<String, RaftNode> knownNodes = new ConcurrentHashMap<>();
    private volatile String lastKnownLeader;
    private volatile long lastDiscoveryTime = 0;

    /**
     * Discover the current leader in the Raft cluster.
     * This method will check known nodes and attempt to find which one is currently the leader.
     * 
     * @return a CompletableFuture containing the leader RaftNode
     */
    public CompletableFuture<RaftNode> discoverLeader() {
        logger.info("Starting leader discovery process");
        
        return CompletableFuture.supplyAsync(() -> {
            // First, check if we have a cached leader that's still valid
            if (lastKnownLeader != null) {
                RaftNode cachedLeader = knownNodes.get(lastKnownLeader);
                if (cachedLeader != null && cachedLeader.getState() == RaftNode.State.LEADER) {
                    logger.fine("Using cached leader: " + lastKnownLeader);
                    return cachedLeader;
                }
            }
            
            // Query all known nodes to find the current leader
            for (String nodeId : clusterConfig.getNodeIds()) {
                try {
                    RaftClusterConfig.NodeConfig nodeConfig = clusterConfig.getNodeConfig(nodeId);
                    RaftNode node = getOrCreateNode(nodeId, nodeConfig);
                    if (node != null && node.getState() == RaftNode.State.LEADER) {
                        lastKnownLeader = node.getNodeId();
                        lastDiscoveryTime = System.currentTimeMillis();
                        logger.info("Discovered leader: " + lastKnownLeader);
                        return node;
                    }
                } catch (Exception e) {
                    logger.log(Level.FINE, "Failed to check node: " + nodeId, e);
                }
            }
            
            throw new RuntimeException("No leader found in cluster");
        }).orTimeout(DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Get all known nodes in the cluster.
     * 
     * @return a set of node IDs
     */
    public Set<String> getKnownNodes() {
        return knownNodes.keySet();
    }

    /**
     * Check if we have a known leader (may be stale).
     * 
     * @return true if we have a cached leader
     */
    public boolean hasKnownLeader() {
        return lastKnownLeader != null && 
               knownNodes.containsKey(lastKnownLeader) &&
               (System.currentTimeMillis() - lastDiscoveryTime) < TimeUnit.SECONDS.toMillis(HEALTH_CHECK_INTERVAL_SECONDS);
    }

    /**
     * Get the last known leader node ID.
     * 
     * @return the leader node ID, or null if unknown
     */
    public String getLastKnownLeader() {
        return lastKnownLeader;
    }

    /**
     * Invalidate the cached leader information.
     * This should be called when a leader becomes unavailable.
     */
    public void invalidateLeader() {
        logger.info("Invalidating cached leader: " + lastKnownLeader);
        lastKnownLeader = null;
        lastDiscoveryTime = 0;
    }

    /**
     * Register a node failure and remove it from known nodes.
     * 
     * @param nodeId the failed node ID
     */
    public void reportNodeFailure(String nodeId) {
        logger.warning("Reporting node failure: " + nodeId);
        knownNodes.remove(nodeId);
        
        if (nodeId.equals(lastKnownLeader)) {
            invalidateLeader();
        }
    }

    /**
     * Get cluster health information.
     * 
     * @return cluster health status
     */
    public ClusterHealth getClusterHealth() {
        int totalNodes = clusterConfig.getNodeIds().size();
        int availableNodes = 0;
        int leaderCount = 0;

        for (String nodeId : clusterConfig.getNodeIds()) {
            try {
                RaftNode node = knownNodes.get(nodeId);
                if (node != null) {
                    RaftNode.State state = node.getState();
                    if (state != null) {
                        availableNodes++;
                        if (state == RaftNode.State.LEADER) {
                            leaderCount++;
                        }
                    }
                }
            } catch (Exception e) {
                logger.log(Level.FINE, "Failed to check node health: " + nodeId, e);
            }
        }
        
        boolean hasQuorum = availableNodes > totalNodes / 2;
        boolean hasLeader = leaderCount == 1;
        
        return new ClusterHealth(
            totalNodes,
            availableNodes,
            hasQuorum,
            hasLeader,
            lastKnownLeader
        );
    }

    /**
     * Initialize connections to all configured cluster nodes.
     * This method should be called during application startup.
     */
    public void initializeClusterConnections() {
        logger.info("Initializing cluster connections");

        for (String nodeId : clusterConfig.getNodeIds()) {
            try {
                RaftClusterConfig.NodeConfig nodeConfig = clusterConfig.getNodeConfig(nodeId);
                getOrCreateNode(nodeId, nodeConfig);
                logger.info("Connected to cluster node: " + nodeId);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to connect to cluster node: " + nodeId, e);
            }
        }
    }

    /**
     * Get or create a RaftNode connection for the specified node.
     *
     * @param nodeId the node ID
     * @param nodeConfig the node configuration
     * @return the RaftNode instance
     */
    private RaftNode getOrCreateNode(String nodeId, RaftClusterConfig.NodeConfig nodeConfig) {
        return knownNodes.computeIfAbsent(nodeId, id -> {
            // For now, return null to indicate remote connections not yet implemented
            // This allows the service to fall back to local operations
            logger.fine("Remote node connections not yet implemented for: " + id + " at " + nodeConfig.getAddress());
            return null;
        });
    }

    /**
     * Cluster health information.
     */
    public static class ClusterHealth {
        private final int totalNodes;
        private final int availableNodes;
        private final boolean hasQuorum;
        private final boolean hasLeader;
        private final String leaderId;

        public ClusterHealth(int totalNodes, int availableNodes, boolean hasQuorum, 
                           boolean hasLeader, String leaderId) {
            this.totalNodes = totalNodes;
            this.availableNodes = availableNodes;
            this.hasQuorum = hasQuorum;
            this.hasLeader = hasLeader;
            this.leaderId = leaderId;
        }

        public int getTotalNodes() { return totalNodes; }
        public int getAvailableNodes() { return availableNodes; }
        public boolean hasQuorum() { return hasQuorum; }
        public boolean hasLeader() { return hasLeader; }
        public String getLeaderId() { return leaderId; }
        
        public boolean isHealthy() {
            return hasQuorum && hasLeader;
        }
    }
}
