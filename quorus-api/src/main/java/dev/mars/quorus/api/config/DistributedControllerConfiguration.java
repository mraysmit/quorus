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

package dev.mars.quorus.api.config;

import dev.mars.quorus.controller.raft.RaftClusterConfig;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftStateMachine;
import dev.mars.quorus.controller.raft.RaftTransport;
import dev.mars.quorus.controller.raft.InMemoryTransport;
import dev.mars.quorus.controller.state.QuorusStateMachine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Configuration class for distributed controller components.
 * This class provides CDI beans for the Raft cluster configuration and node.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
@ApplicationScoped
public class DistributedControllerConfiguration {

    private static final Logger logger = Logger.getLogger(DistributedControllerConfiguration.class.getName());

    /**
     * Produces the Raft cluster configuration.
     * For now, this creates a simple single-node configuration for development.
     * 
     * @return the cluster configuration
     */
    @Produces
    @Singleton
    public RaftClusterConfig clusterConfig() {
        logger.info("Creating Raft cluster configuration");

        // For development, create a simple single-node cluster
        // In production, this would be configured from external configuration
        return new RaftClusterConfig.Builder()
                .addNode("api-node", "localhost", 8080)
                .setElectionTimeoutMs(1000)  // Shorter timeout for single node
                .setHeartbeatIntervalMs(200)
                .build();
    }

    /**
     * Produces the Raft node instance.
     * This creates a local Raft node that can participate in the cluster.
     * 
     * @param clusterConfig the cluster configuration
     * @return the Raft node
     */
    @Produces
    @Singleton
    public RaftNode raftNode(RaftClusterConfig clusterConfig) {
        logger.info("Creating Raft node");
        
        // Use the configured node ID for single-node cluster
        String nodeId = "api-node";
        
        // Get cluster node IDs
        Set<String> clusterNodes = clusterConfig.getNodeIds();
        
        // Create transport (in-memory for now)
        RaftTransport transport = new InMemoryTransport(nodeId);

        // Create state machine for transfer jobs
        RaftStateMachine stateMachine = new QuorusStateMachine();
        
        // Create and configure the Raft node
        RaftNode node = new RaftNode(
            nodeId,
            clusterNodes,
            transport,
            stateMachine,
            clusterConfig.getElectionTimeoutMs(),
            clusterConfig.getHeartbeatIntervalMs()
        );
        
        // Start the node
        node.start();
        
        logger.info("Raft node created and started: " + nodeId);
        return node;
    }
}
