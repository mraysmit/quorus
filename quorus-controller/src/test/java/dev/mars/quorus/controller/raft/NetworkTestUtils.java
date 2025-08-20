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

import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.Container;

import java.util.List;
import java.util.logging.Logger;

/**
 * Utility class for network manipulation in Raft cluster tests.
 * Provides methods to simulate network partitions, latency, and failures.
 */
public class NetworkTestUtils {

    private static final Logger logger = Logger.getLogger(NetworkTestUtils.class.getName());

    /**
     * Simulates network partition by blocking traffic between specified nodes.
     * Uses iptables to drop packets between containers.
     */
    public static void createNetworkPartition(ComposeContainer environment,
                                            List<String> partition1,
                                            List<String> partition2) {
        logger.info("Creating network partition between groups: " + partition1 + " and " + partition2);

        try {
            // Block traffic from partition1 to partition2
            for (String node1 : partition1) {
                for (String node2 : partition2) {
                    blockTrafficBetweenNodes(environment, node1, node2);
                    blockTrafficBetweenNodes(environment, node2, node1);
                }
            }

            logger.info("Network partition created successfully");

        } catch (Exception e) {
            logger.severe("Failed to create network partition: " + e.getMessage());
            throw new RuntimeException("Network partition creation failed", e);
        }
    }

    /**
     * Creates a network partition by moving containers to different Docker networks.
     * This provides true network isolation at the Docker level.
     */
    public static void createDockerNetworkPartition(List<String> partition1, List<String> partition2) {
        logger.info("Creating Docker network partition: " + partition1 + " vs " + partition2);

        try {
            // Move partition1 to raft-partition-a network
            for (String node : partition1) {
                disconnectFromNetwork(node, "raft-cluster");
                connectToNetwork(node, "raft-partition-a", getPartitionIpAddress(node, "a"));
            }

            // Move partition2 to raft-partition-b network
            for (String node : partition2) {
                disconnectFromNetwork(node, "raft-cluster");
                connectToNetwork(node, "raft-partition-b", getPartitionIpAddress(node, "b"));
            }

            logger.info("Docker network partition created successfully");

        } catch (Exception e) {
            logger.severe("Failed to create Docker network partition: " + e.getMessage());
            throw new RuntimeException("Docker network partition creation failed", e);
        }
    }

    /**
     * Restores the original network configuration by moving all containers back to the main network.
     */
    public static void restoreDockerNetworkPartition(List<String> allNodes) {
        logger.info("Restoring Docker network partition for nodes: " + allNodes);

        try {
            for (String node : allNodes) {
                // Disconnect from partition networks
                disconnectFromNetwork(node, "raft-partition-a");
                disconnectFromNetwork(node, "raft-partition-b");

                // Reconnect to main cluster network
                connectToNetwork(node, "raft-cluster", getOriginalIpAddress(node));
            }

            logger.info("Docker network partition restored successfully");

        } catch (Exception e) {
            logger.severe("Failed to restore Docker network partition: " + e.getMessage());
            throw new RuntimeException("Docker network partition restoration failed", e);
        }
    }

    /**
     * Removes network partition by restoring traffic between all nodes.
     */
    public static void removeNetworkPartition(ComposeContainer environment, List<String> allNodes) {
        logger.info("Removing network partition for nodes: " + allNodes);
        
        try {
            for (String node : allNodes) {
                clearNetworkRules(environment, node);
            }
            
            logger.info("Network partition removed successfully");
            
        } catch (Exception e) {
            logger.severe("Failed to remove network partition: " + e.getMessage());
            throw new RuntimeException("Network partition removal failed", e);
        }
    }

    /**
     * Simulates network latency by adding delay to packets.
     */
    public static void addNetworkLatency(ComposeContainer environment, String nodeName, int delayMs) {
        logger.info("Adding " + delayMs + "ms network latency to " + nodeName);
        
        try {
            Container.ExecResult result = environment.getContainerByServiceName(nodeName)
                    .orElseThrow(() -> new RuntimeException("Container not found: " + nodeName))
                    .execInContainer("tc", "qdisc", "add", "dev", "eth0", "root", "netem", "delay", delayMs + "ms");
            
            if (result.getExitCode() != 0) {
                logger.warning("Failed to add latency to " + nodeName + ": " + result.getStderr());
            } else {
                logger.info("Network latency added to " + nodeName);
            }
            
        } catch (Exception e) {
            logger.warning("Error adding network latency to " + nodeName + ": " + e.getMessage());
        }
    }

    /**
     * Removes network latency by clearing traffic control rules.
     */
    public static void removeNetworkLatency(ComposeContainer environment, String nodeName) {
        logger.info("Removing network latency from " + nodeName);
        
        try {
            Container.ExecResult result = environment.getContainerByServiceName(nodeName)
                    .orElseThrow(() -> new RuntimeException("Container not found: " + nodeName))
                    .execInContainer("tc", "qdisc", "del", "dev", "eth0", "root");
            
            if (result.getExitCode() != 0) {
                logger.warning("Failed to remove latency from " + nodeName + ": " + result.getStderr());
            } else {
                logger.info("Network latency removed from " + nodeName);
            }
            
        } catch (Exception e) {
            logger.warning("Error removing network latency from " + nodeName + ": " + e.getMessage());
        }
    }

    /**
     * Simulates packet loss between nodes.
     */
    public static void addPacketLoss(ComposeContainer environment, String nodeName, int lossPercent) {
        logger.info("Adding " + lossPercent + "% packet loss to " + nodeName);
        
        try {
            Container.ExecResult result = environment.getContainerByServiceName(nodeName)
                    .orElseThrow(() -> new RuntimeException("Container not found: " + nodeName))
                    .execInContainer("tc", "qdisc", "add", "dev", "eth0", "root", "netem", "loss", lossPercent + "%");
            
            if (result.getExitCode() != 0) {
                logger.warning("Failed to add packet loss to " + nodeName + ": " + result.getStderr());
            } else {
                logger.info("Packet loss added to " + nodeName);
            }
            
        } catch (Exception e) {
            logger.warning("Error adding packet loss to " + nodeName + ": " + e.getMessage());
        }
    }

    /**
     * Completely isolates a node from the network.
     */
    public static void isolateNode(ComposeContainer environment, String nodeName) {
        logger.info("Isolating node from network: " + nodeName);
        
        try {
            // Block all incoming traffic
            Container.ExecResult result1 = environment.getContainerByServiceName(nodeName)
                    .orElseThrow(() -> new RuntimeException("Container not found: " + nodeName))
                    .execInContainer("iptables", "-A", "INPUT", "-j", "DROP");
            
            // Block all outgoing traffic
            Container.ExecResult result2 = environment.getContainerByServiceName(nodeName)
                    .orElseThrow(() -> new RuntimeException("Container not found: " + nodeName))
                    .execInContainer("iptables", "-A", "OUTPUT", "-j", "DROP");
            
            if (result1.getExitCode() == 0 && result2.getExitCode() == 0) {
                logger.info("Node " + nodeName + " isolated successfully");
            } else {
                logger.warning("Failed to isolate node " + nodeName);
            }
            
        } catch (Exception e) {
            logger.warning("Error isolating node " + nodeName + ": " + e.getMessage());
        }
    }

    /**
     * Restores network connectivity for an isolated node.
     */
    public static void restoreNode(ComposeContainer environment, String nodeName) {
        logger.info("Restoring network connectivity for node: " + nodeName);
        
        try {
            clearNetworkRules(environment, nodeName);
            logger.info("Node " + nodeName + " connectivity restored");
            
        } catch (Exception e) {
            logger.warning("Error restoring node " + nodeName + ": " + e.getMessage());
        }
    }

    /**
     * Simulates a slow network by adding both latency and packet loss.
     */
    public static void simulateSlowNetwork(ComposeContainer environment, String nodeName, 
                                         int delayMs, int lossPercent) {
        logger.info("Simulating slow network for " + nodeName + 
                   " (delay: " + delayMs + "ms, loss: " + lossPercent + "%)");
        
        try {
            Container.ExecResult result = environment.getContainerByServiceName(nodeName)
                    .orElseThrow(() -> new RuntimeException("Container not found: " + nodeName))
                    .execInContainer("tc", "qdisc", "add", "dev", "eth0", "root", "netem", 
                                   "delay", delayMs + "ms", "loss", lossPercent + "%");
            
            if (result.getExitCode() == 0) {
                logger.info("Slow network simulation applied to " + nodeName);
            } else {
                logger.warning("Failed to simulate slow network for " + nodeName + ": " + result.getStderr());
            }
            
        } catch (Exception e) {
            logger.warning("Error simulating slow network for " + nodeName + ": " + e.getMessage());
        }
    }

    // Private helper methods

    private static void blockTrafficBetweenNodes(ComposeContainer environment, String sourceNode, String targetNode) {
        try {
            // Get target node IP address
            String targetIp = getNodeIpAddress(environment, targetNode);
            
            // Block traffic to target IP
            Container.ExecResult result = environment.getContainerByServiceName(sourceNode)
                    .orElseThrow(() -> new RuntimeException("Container not found: " + sourceNode))
                    .execInContainer("iptables", "-A", "OUTPUT", "-d", targetIp, "-j", "DROP");
            
            if (result.getExitCode() != 0) {
                logger.warning("Failed to block traffic from " + sourceNode + " to " + targetNode + 
                             ": " + result.getStderr());
            }
            
        } catch (Exception e) {
            logger.warning("Error blocking traffic from " + sourceNode + " to " + targetNode + 
                         ": " + e.getMessage());
        }
    }

    private static String getNodeIpAddress(ComposeContainer environment, String nodeName) {
        try {
            Container.ExecResult result = environment.getContainerByServiceName(nodeName)
                    .orElseThrow(() -> new RuntimeException("Container not found: " + nodeName))
                    .execInContainer("hostname", "-i");
            
            if (result.getExitCode() == 0) {
                return result.getStdout().trim();
            } else {
                throw new RuntimeException("Failed to get IP for " + nodeName);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Error getting IP for " + nodeName + ": " + e.getMessage());
        }
    }

    private static void clearNetworkRules(ComposeContainer environment, String nodeName) {
        try {
            // Clear iptables rules
            environment.getContainerByServiceName(nodeName)
                    .orElseThrow(() -> new RuntimeException("Container not found: " + nodeName))
                    .execInContainer("iptables", "-F");

            // Clear traffic control rules
            environment.getContainerByServiceName(nodeName)
                    .orElseThrow(() -> new RuntimeException("Container not found: " + nodeName))
                    .execInContainer("tc", "qdisc", "del", "dev", "eth0", "root");

        } catch (Exception e) {
            // Some commands might fail if rules don't exist - this is expected
            logger.fine("Clearing network rules for " + nodeName + ": " + e.getMessage());
        }
    }

    // Docker network manipulation methods

    private static void disconnectFromNetwork(String containerName, String networkName) {
        try {
            String command = String.format("docker network disconnect %s %s", networkName, containerName);
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                logger.info("Disconnected " + containerName + " from network " + networkName);
            } else {
                logger.warning("Failed to disconnect " + containerName + " from network " + networkName);
            }

        } catch (Exception e) {
            logger.warning("Error disconnecting " + containerName + " from network " + networkName + ": " + e.getMessage());
        }
    }

    private static void connectToNetwork(String containerName, String networkName, String ipAddress) {
        try {
            String command = String.format("docker network connect --ip %s %s %s",
                                         ipAddress, networkName, containerName);
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                logger.info("Connected " + containerName + " to network " + networkName + " with IP " + ipAddress);
            } else {
                logger.warning("Failed to connect " + containerName + " to network " + networkName);
            }

        } catch (Exception e) {
            logger.warning("Error connecting " + containerName + " to network " + networkName + ": " + e.getMessage());
        }
    }

    private static String getPartitionIpAddress(String nodeName, String partition) {
        // Map node names to IP addresses in partition networks
        String baseIp = partition.equals("a") ? "172.21.0." : "172.22.0.";

        switch (nodeName) {
            case "controller1": return baseIp + "10";
            case "controller2": return baseIp + "11";
            case "controller3": return baseIp + "12";
            case "controller4": return baseIp + "13";
            case "controller5": return baseIp + "14";
            default: throw new IllegalArgumentException("Unknown node: " + nodeName);
        }
    }

    private static String getOriginalIpAddress(String nodeName) {
        // Map node names to their original IP addresses in the main cluster network
        switch (nodeName) {
            case "controller1": return "172.20.0.10";
            case "controller2": return "172.20.0.11";
            case "controller3": return "172.20.0.12";
            case "controller4": return "172.20.0.13";
            case "controller5": return "172.20.0.14";
            default: throw new IllegalArgumentException("Unknown node: " + nodeName);
        }
    }

    /**
     * Simulates geographic distribution by adding different latencies to different nodes.
     */
    public static void simulateGeographicDistribution(ComposeContainer environment, List<String> nodes) {
        logger.info("Simulating geographic distribution with varying latencies");

        try {
            // Simulate different geographic regions with different latencies
            for (int i = 0; i < nodes.size(); i++) {
                String node = nodes.get(i);
                int latency = 20 + (i * 30); // 20ms, 50ms, 80ms, etc.
                addNetworkLatency(environment, node, latency);
                logger.info("Added " + latency + "ms latency to " + node + " (simulating region " + (i + 1) + ")");
            }

        } catch (Exception e) {
            logger.warning("Error simulating geographic distribution: " + e.getMessage());
        }
    }

    /**
     * Creates a complex network scenario with multiple partitions and varying conditions.
     */
    public static void createComplexNetworkScenario(ComposeContainer environment, List<String> allNodes) {
        logger.info("Creating complex network scenario with multiple conditions");

        try {
            // Apply different network conditions to different nodes
            if (allNodes.size() >= 5) {
                // Node 1: High latency (simulating distant region)
                addNetworkLatency(environment, allNodes.get(0), 200);

                // Node 2: Packet loss (simulating unreliable connection)
                addPacketLoss(environment, allNodes.get(1), 3);

                // Node 3: Slow network (latency + packet loss)
                simulateSlowNetwork(environment, allNodes.get(2), 100, 2);

                // Nodes 4-5: Normal conditions for comparison
                logger.info("Applied complex network conditions to simulate real-world scenarios");
            }

        } catch (Exception e) {
            logger.warning("Error creating complex network scenario: " + e.getMessage());
        }
    }
}
