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

package dev.mars.quorus.controller;

import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftTransport;
import dev.mars.quorus.controller.raft.HttpRaftTransport;
import dev.mars.quorus.controller.raft.RaftStateMachine;
import dev.mars.quorus.controller.http.HttpApiServer;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Main application class for Quorus Controller.
 * 
 * This is the primary entry point for the distributed file transfer controller.
 * It manages the Raft consensus engine and provides HTTP API interfaces for
 * agent management, file transfer coordination, and cluster operations.
 * 
 * Architecture:
 * - Raft consensus for distributed coordination
 * - HTTP API for external communication
 * - State machine for transfer job management
 * - Agent fleet management
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
public class QuorusControllerApplication {

    private static final Logger logger = Logger.getLogger(QuorusControllerApplication.class.getName());
    
    // Configuration from environment variables
    private final String nodeId;
    private final String raftHost;
    private final int raftPort;
    private final int httpPort;
    private final Set<String> clusterNodes;
    private final long electionTimeoutMs;
    private final long heartbeatIntervalMs;
    
    // Core components
    private RaftNode raftNode;
    private HttpApiServer httpApiServer;
    private volatile boolean running = false;

    public QuorusControllerApplication() {
        // Load configuration from environment
        this.nodeId = getEnvOrDefault("NODE_ID", "controller1");
        this.raftHost = getEnvOrDefault("RAFT_HOST", "0.0.0.0");
        this.raftPort = Integer.parseInt(getEnvOrDefault("RAFT_PORT", "8080"));
        this.httpPort = Integer.parseInt(getEnvOrDefault("HTTP_PORT", "8080"));
        this.electionTimeoutMs = Long.parseLong(getEnvOrDefault("ELECTION_TIMEOUT_MS", "5000"));
        this.heartbeatIntervalMs = Long.parseLong(getEnvOrDefault("HEARTBEAT_INTERVAL_MS", "1000"));
        
        // Parse cluster nodes
        this.clusterNodes = parseClusterNodes(getEnvOrDefault("CLUSTER_NODES", nodeId + "=" + raftHost + ":" + raftPort));
        
        logger.info("Quorus Controller Configuration:");
        logger.info("  Node ID: " + nodeId);
        logger.info("  Raft Host: " + raftHost);
        logger.info("  Raft Port: " + raftPort);
        logger.info("  HTTP Port: " + httpPort);
        logger.info("  Cluster Nodes: " + clusterNodes);
        logger.info("  Election Timeout: " + electionTimeoutMs + "ms");
        logger.info("  Heartbeat Interval: " + heartbeatIntervalMs + "ms");
    }

    /**
     * Main entry point for the Quorus Controller application.
     */
    public static void main(String[] args) {
        QuorusControllerApplication app = new QuorusControllerApplication();
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received, stopping Quorus Controller...");
            app.stop();
        }));
        
        try {
            app.start();
            
            // Keep the application running
            synchronized (app) {
                while (app.running) {
                    app.wait();
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start Quorus Controller", e);
            System.exit(1);
        }
    }

    /**
     * Start the Quorus Controller.
     * This initializes the Raft consensus engine and HTTP API server.
     */
    public synchronized void start() throws Exception {
        if (running) {
            logger.warning("Quorus Controller is already running");
            return;
        }

        logger.info("Starting Quorus Controller...");

        try {
            // Create proper Raft node with HTTP transport
            raftNode = createRaftNode();
            
            logger.info("Starting Raft consensus engine...");
            raftNode.start();
            
            // 4. Create and start HTTP API server
            httpApiServer = new HttpApiServer(httpPort, raftNode);
            
            logger.info("Starting HTTP API server on port " + httpPort + "...");
            httpApiServer.start();
            
            running = true;
            logger.info("Quorus Controller started successfully");
            logger.info("  - Raft consensus: " + raftHost + ":" + raftPort);
            logger.info("  - HTTP API: http://" + raftHost + ":" + httpPort);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start Quorus Controller", e);
            stop();
            throw e;
        }
    }

    /**
     * Stop the Quorus Controller.
     * This shuts down the HTTP API server and Raft consensus engine.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        logger.info("Stopping Quorus Controller...");

        try {
            // Stop HTTP API server
            if (httpApiServer != null) {
                logger.info("Stopping HTTP API server...");
                httpApiServer.stop();
            }

            // Stop Raft node
            if (raftNode != null) {
                logger.info("Stopping Raft consensus engine...");
                raftNode.stop();
            }

            running = false;
            notifyAll();
            
            logger.info("Quorus Controller stopped successfully");
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during shutdown", e);
        }
    }

    /**
     * Check if the controller is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get the Raft node instance.
     */
    public RaftNode getRaftNode() {
        return raftNode;
    }

    /**
     * Get environment variable with default value.
     */
    private String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Parse cluster nodes from environment variable.
     * Format: node1=host1:port1,node2=host2:port2,node3=host3:port3
     */
    private Set<String> parseClusterNodes(String clusterNodesStr) {
        Set<String> nodes = new HashSet<>();

        if (clusterNodesStr != null && !clusterNodesStr.trim().isEmpty()) {
            String[] nodeEntries = clusterNodesStr.split(",");
            for (String entry : nodeEntries) {
                String[] parts = entry.trim().split("=");
                if (parts.length == 2) {
                    nodes.add(parts[0].trim());
                }
            }
        }

        return nodes;
    }

    /**
     * Create a proper RaftNode with HTTP transport and state machine.
     */
    private RaftNode createRaftNode() {
        // Create cluster node mapping for HTTP transport (using Raft port 9080)
        Map<String, String> clusterNodeMap = new HashMap<>();
        for (String node : clusterNodes) {
            if (node.equals("controller1")) {
                clusterNodeMap.put(node, "controller1:9080");
            } else if (node.equals("controller2")) {
                clusterNodeMap.put(node, "controller2:9080");
            } else if (node.equals("controller3")) {
                clusterNodeMap.put(node, "controller3:9080");
            } else if (node.equals("controller4")) {
                clusterNodeMap.put(node, "controller4:9080");
            } else if (node.equals("controller5")) {
                clusterNodeMap.put(node, "controller5:9080");
            }
        }

        // Create HTTP transport
        HttpRaftTransport transport = new HttpRaftTransport(nodeId, raftHost, raftPort, clusterNodeMap);

        // Create simple state machine
        RaftStateMachine stateMachine = new SimpleStateMachine();

        // Create RaftNode with proper configuration
        RaftNode raftNode = new RaftNode(nodeId, clusterNodes, transport, stateMachine, electionTimeoutMs, heartbeatIntervalMs);

        // Set the RaftNode reference in the transport for proper message handling
        transport.setRaftNode(raftNode);

        return raftNode;
    }

    /**
     * Simple state machine implementation for Quorus.
     */
    private static class SimpleStateMachine implements RaftStateMachine {
        private volatile long lastAppliedIndex = 0;
        private final Map<String, Object> state = new HashMap<>();

        @Override
        public Object apply(Object command) {
            logger.info("Applying command: " + command);
            // Simple command processing - just store in state
            if (command instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cmd = (Map<String, Object>) command;
                state.putAll(cmd);
            }
            return "OK";
        }

        @Override
        public byte[] takeSnapshot() {
            return state.toString().getBytes();
        }

        @Override
        public void restoreSnapshot(byte[] snapshot) {
            // Simple restore - just log it
            logger.info("Restoring snapshot: " + new String(snapshot));
        }

        @Override
        public long getLastAppliedIndex() {
            return lastAppliedIndex;
        }

        @Override
        public void setLastAppliedIndex(long index) {
            this.lastAppliedIndex = index;
        }

        @Override
        public void reset() {
            state.clear();
            lastAppliedIndex = 0;
        }
    }
}
