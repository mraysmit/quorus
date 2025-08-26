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
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Set;

/**
 * Represents the current status of the Raft controller cluster.
 * This class provides information about cluster health, leadership, and node states.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
public class ClusterStatus {

    @JsonProperty("nodeId")
    private final String nodeId;

    @JsonProperty("state")
    private final RaftNode.State state;

    @JsonProperty("term")
    private final long term;

    @JsonProperty("isLeader")
    private final boolean isLeader;

    @JsonProperty("knownNodes")
    private final Set<String> knownNodes;

    @JsonProperty("timestamp")
    private final Instant timestamp;

    @JsonProperty("available")
    private final boolean available;

    /**
     * Create a cluster status instance.
     * 
     * @param nodeId the current node ID
     * @param state the current Raft state
     * @param term the current Raft term
     * @param isLeader whether this node is the leader
     * @param knownNodes set of known cluster nodes
     */
    public ClusterStatus(String nodeId, RaftNode.State state, long term, 
                        boolean isLeader, Set<String> knownNodes) {
        this.nodeId = nodeId;
        this.state = state;
        this.term = term;
        this.isLeader = isLeader;
        this.knownNodes = knownNodes;
        this.timestamp = Instant.now();
        this.available = true;
    }

    /**
     * Create an unavailable cluster status.
     */
    private ClusterStatus() {
        this.nodeId = "unknown";
        this.state = null;
        this.term = -1;
        this.isLeader = false;
        this.knownNodes = Set.of();
        this.timestamp = Instant.now();
        this.available = false;
    }

    /**
     * Create a cluster status indicating the cluster is unavailable.
     * 
     * @return unavailable cluster status
     */
    public static ClusterStatus unavailable() {
        return new ClusterStatus();
    }

    /**
     * Get the current node ID.
     * 
     * @return the node ID
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * Get the current Raft state of this node.
     * 
     * @return the Raft state
     */
    public RaftNode.State getState() {
        return state;
    }

    /**
     * Get the current Raft term.
     * 
     * @return the term number
     */
    public long getTerm() {
        return term;
    }

    /**
     * Check if this node is the current leader.
     * 
     * @return true if this node is the leader
     */
    public boolean isLeader() {
        return isLeader;
    }

    /**
     * Get the set of known cluster nodes.
     * 
     * @return set of node IDs
     */
    public Set<String> getKnownNodes() {
        return knownNodes;
    }

    /**
     * Get the timestamp when this status was created.
     * 
     * @return the timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Check if the cluster is available.
     * 
     * @return true if the cluster is available
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Check if the cluster appears to be healthy.
     * A healthy cluster has a leader and sufficient nodes.
     * 
     * @return true if the cluster is healthy
     */
    public boolean isHealthy() {
        return available && (isLeader || state != null);
    }

    /**
     * Get a human-readable status description.
     * 
     * @return status description
     */
    public String getStatusDescription() {
        if (!available) {
            return "Cluster unavailable";
        }
        
        if (isLeader) {
            return "Leader (term " + term + ")";
        } else if (state != null) {
            return state.toString().toLowerCase() + " (term " + term + ")";
        } else {
            return "Unknown state";
        }
    }

    @Override
    public String toString() {
        return "ClusterStatus{" +
                "nodeId='" + nodeId + '\'' +
                ", state=" + state +
                ", term=" + term +
                ", isLeader=" + isLeader +
                ", knownNodes=" + knownNodes.size() +
                ", available=" + available +
                ", timestamp=" + timestamp +
                '}';
    }
}
