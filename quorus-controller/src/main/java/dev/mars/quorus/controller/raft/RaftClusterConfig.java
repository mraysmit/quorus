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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Configuration class for Raft cluster setup.
 * Manages node addresses, ports, and cluster topology.
 */
public class RaftClusterConfig {

    private final Map<String, NodeConfig> nodes;
    private final long electionTimeoutMs;
    private final long heartbeatIntervalMs;

    public RaftClusterConfig(Map<String, NodeConfig> nodes, long electionTimeoutMs, long heartbeatIntervalMs) {
        this.nodes = new HashMap<>(nodes);
        this.electionTimeoutMs = electionTimeoutMs;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public Set<String> getNodeIds() {
        return Collections.unmodifiableSet(nodes.keySet());
    }

    public NodeConfig getNodeConfig(String nodeId) {
        return nodes.get(nodeId);
    }

    public Map<String, String> getClusterAddresses() {
        Map<String, String> addresses = new HashMap<>();
        for (Map.Entry<String, NodeConfig> entry : nodes.entrySet()) {
            NodeConfig config = entry.getValue();
            addresses.put(entry.getKey(), config.getHost() + ":" + config.getPort());
        }
        return addresses;
    }

    public long getElectionTimeoutMs() {
        return electionTimeoutMs;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    /**
     * Configuration for a single node in the cluster.
     */
    public static class NodeConfig {
        private final String host;
        private final int port;

        public NodeConfig(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getAddress() {
            return host + ":" + port;
        }

        @Override
        public String toString() {
            return "NodeConfig{host='" + host + "', port=" + port + "}";
        }
    }

    /**
     * Builder for creating cluster configurations.
     */
    public static class Builder {
        private final Map<String, NodeConfig> nodes = new HashMap<>();
        private long electionTimeoutMs = 5000;
        private long heartbeatIntervalMs = 1000;

        public Builder addNode(String nodeId, String host, int port) {
            nodes.put(nodeId, new NodeConfig(host, port));
            return this;
        }

        public Builder setElectionTimeoutMs(long electionTimeoutMs) {
            this.electionTimeoutMs = electionTimeoutMs;
            return this;
        }

        public Builder setHeartbeatIntervalMs(long heartbeatIntervalMs) {
            this.heartbeatIntervalMs = heartbeatIntervalMs;
            return this;
        }

        public RaftClusterConfig build() {
            if (nodes.isEmpty()) {
                throw new IllegalStateException("At least one node must be configured");
            }
            return new RaftClusterConfig(nodes, electionTimeoutMs, heartbeatIntervalMs);
        }
    }

    /**
     * Creates a default 3-node cluster configuration for testing.
     */
    public static RaftClusterConfig createDefaultTestCluster() {
        return new Builder()
                .addNode("node1", "localhost", 8001)
                .addNode("node2", "localhost", 8002)
                .addNode("node3", "localhost", 8003)
                .setElectionTimeoutMs(3000)
                .setHeartbeatIntervalMs(500)
                .build();
    }

    /**
     * Creates a 5-node cluster configuration for testing.
     */
    public static RaftClusterConfig createFiveNodeTestCluster() {
        return new Builder()
                .addNode("node1", "localhost", 8001)
                .addNode("node2", "localhost", 8002)
                .addNode("node3", "localhost", 8003)
                .addNode("node4", "localhost", 8004)
                .addNode("node5", "localhost", 8005)
                .setElectionTimeoutMs(3000)
                .setHeartbeatIntervalMs(500)
                .build();
    }

    /**
     * Creates a Docker-based cluster configuration.
     */
    public static RaftClusterConfig createDockerCluster(int nodeCount, int basePort) {
        Builder builder = new Builder()
                .setElectionTimeoutMs(5000)
                .setHeartbeatIntervalMs(1000);

        for (int i = 1; i <= nodeCount; i++) {
            String nodeId = "controller" + i;
            String host = "controller" + i; // Docker service name
            int port = basePort;
            builder.addNode(nodeId, host, port);
        }

        return builder.build();
    }

    @Override
    public String toString() {
        return "RaftClusterConfig{" +
                "nodes=" + nodes +
                ", electionTimeoutMs=" + electionTimeoutMs +
                ", heartbeatIntervalMs=" + heartbeatIntervalMs +
                '}';
    }
}
