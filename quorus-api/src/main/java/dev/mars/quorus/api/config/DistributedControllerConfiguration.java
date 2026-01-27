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
import dev.mars.quorus.controller.raft.GrpcRaftTransport;
import dev.mars.quorus.controller.state.QuorusStateMachine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Configuration class for distributed controller components.
 * This class provides CDI beans for the Raft cluster configuration and node.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-26
 * @version 1.0
 */
@ApplicationScoped
public class DistributedControllerConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(DistributedControllerConfiguration.class);

    /**
     * Produces the Raft cluster configuration.
     * For now, this creates a simple single-node configuration for development.
     * 
     * @return the cluster configuration
     */
    @Produces
    @Singleton
    public RaftClusterConfig clusterConfig() {
        logger.debug("Creating Raft cluster configuration");
        logger.trace("Building cluster config with election timeout and heartbeat interval");

        // For development, create a simple single-node cluster
        // In production, this would be configured from external configuration
        RaftClusterConfig config = new RaftClusterConfig.Builder()
                .addNode("api-node", "localhost", 8080)
                .setElectionTimeoutMs(1000)  // Shorter timeout for single node
                .setHeartbeatIntervalMs(200)
                .build();
        
        logger.info("Raft cluster configuration created: electionTimeout={}ms, heartbeatInterval={}ms", 
                config.getElectionTimeoutMs(), config.getHeartbeatIntervalMs());
        return config;
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
    public RaftNode raftNode(io.vertx.core.Vertx vertx, RaftClusterConfig clusterConfig) {
        logger.debug("Creating Raft node with cluster configuration");
        
        // Use the configured node ID for single-node cluster
        String nodeId = "api-node";
        logger.debug("Using node ID: {}", nodeId);
        
        // Get cluster node IDs
        Set<String> clusterNodes = clusterConfig.getNodeIds();
        logger.debug("Cluster nodes: {}", clusterNodes);
        
        // Get local node configuration
        RaftClusterConfig.NodeConfig nodeConfig = clusterConfig.getNodeConfig(nodeId);
        logger.trace("Node config retrieved for nodeId={}", nodeId);
        
        // Create transport (gRPC for real communication)
        logger.debug("Creating gRPC transport for node: {}", nodeId);
        RaftTransport transport = new GrpcRaftTransport(
            vertx,
            nodeId, 
            clusterConfig.getClusterAddresses()
        );

        // Create state machine for transfer jobs
        logger.debug("Creating QuorusStateMachine for state management");
        RaftStateMachine stateMachine = new QuorusStateMachine();
        
        // Create and configure the Raft node
        logger.debug("Constructing RaftNode: nodeId={}, clusterSize={}", nodeId, clusterNodes.size());
        RaftNode node = new RaftNode(
            vertx,
            nodeId,
            clusterNodes,
            transport,
            stateMachine,
            clusterConfig.getElectionTimeoutMs(),
            clusterConfig.getHeartbeatIntervalMs()
        );
        
        // Start the node
        logger.debug("Starting Raft node: {}", nodeId);
        node.start();
        
        logger.info("Raft node created and started: nodeId={}, clusterNodes={}", nodeId, clusterNodes);
        return node;
    }
}
