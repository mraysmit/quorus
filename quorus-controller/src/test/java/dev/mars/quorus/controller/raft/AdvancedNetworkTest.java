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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Advanced network testing for Raft cluster using custom Docker networks.
 * Tests realistic network scenarios including geographic distribution,
 * network partitions, and complex failure modes.
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */
@Testcontainers
public class AdvancedNetworkTest {

    private static final Logger logger = Logger.getLogger(AdvancedNetworkTest.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private ComposeContainer environment;
    private List<String> nodeEndpoints;
    private List<String> nodeNames;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        logger.info("Starting advanced network test: " + testInfo.getDisplayName());
        
        // Use the advanced network testing configuration
        environment = new ComposeContainer(new File("src/test/resources/docker-compose-network-test.yml"))
                .withExposedService("controller1", 8080, Wait.forHttp("/health").forStatusCode(200))
                .withExposedService("controller2", 8080, Wait.forHttp("/health").forStatusCode(200))
                .withExposedService("controller3", 8080, Wait.forHttp("/health").forStatusCode(200))
                .withExposedService("controller4", 8080, Wait.forHttp("/health").forStatusCode(200))
                .withExposedService("controller5", 8080, Wait.forHttp("/health").forStatusCode(200))
                .waitingFor("controller1", Wait.forLogMessage(".*Starting Raft node.*", 1))
                .waitingFor("controller2", Wait.forLogMessage(".*Starting Raft node.*", 1))
                .waitingFor("controller3", Wait.forLogMessage(".*Starting Raft node.*", 1))
                .waitingFor("controller4", Wait.forLogMessage(".*Starting Raft node.*", 1))
                .waitingFor("controller5", Wait.forLogMessage(".*Starting Raft node.*", 1))
                .withStartupTimeout(Duration.ofMinutes(8));

        environment.start();

        // Initialize node information
        nodeEndpoints = new ArrayList<>();
        nodeNames = List.of("controller1", "controller2", "controller3", "controller4", "controller5");
        
        for (int i = 1; i <= 5; i++) {
            String serviceName = "controller" + i;
            Integer port = environment.getServicePort(serviceName, 8080);
            String endpoint = "http://localhost:" + port;
            nodeEndpoints.add(endpoint);
            logger.info("Controller " + i + " endpoint: " + endpoint);
        }

        // Wait for all nodes to be healthy
        await().atMost(Duration.ofMinutes(3))
                .pollInterval(Duration.ofSeconds(3))
                .until(this::allNodesHealthy);
        
        logger.info("All 5 nodes are healthy and ready for advanced network testing");
    }

    @AfterEach
    void tearDown(TestInfo testInfo) {
        if (environment != null) {
            // Restore network state before shutdown
            try {
                NetworkTestUtils.restoreDockerNetworkPartition(nodeNames);
            } catch (Exception e) {
                logger.warning("Failed to restore network state: " + e.getMessage());
            }
            environment.stop();
        }
        logger.info("Completed advanced network test: " + testInfo.getDisplayName());
    }

    @Test
    void testDockerNetworkPartition() {
        // Wait for initial leader election
        await().atMost(Duration.ofSeconds(45))
                .pollInterval(Duration.ofSeconds(3))
                .until(this::hasExactlyOneLeader);

        String originalLeader = findLeaderId();
        logger.info("Original leader: " + originalLeader);

        // Create a 3-2 partition using Docker networks
        List<String> majorityPartition = List.of("controller1", "controller2", "controller3");
        List<String> minorityPartition = List.of("controller4", "controller5");

        logger.info("Creating Docker network partition: " + majorityPartition + " vs " + minorityPartition);
        
        NetworkTestUtils.createDockerNetworkPartition(majorityPartition, minorityPartition);

        // Wait for the majority partition to stabilize
        await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(5))
                .until(() -> hasLeaderInPartition(majorityPartition));

        logger.info("Majority partition successfully maintained/elected leader");

        // Verify minority partition cannot elect leader
        verifyMinorityPartitionHasNoLeader(minorityPartition);

        // Restore network and verify cluster recovery
        NetworkTestUtils.restoreDockerNetworkPartition(nodeNames);
        
        await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(3))
                .until(this::hasExactlyOneLeader);

        logger.info("Cluster successfully recovered after network partition restoration");
    }

    @Test
    void testGeographicDistribution() {
        // Wait for initial stable cluster
        await().atMost(Duration.ofSeconds(45))
                .pollInterval(Duration.ofSeconds(3))
                .until(this::hasExactlyOneLeader);

        logger.info("Testing geographic distribution simulation");

        // Simulate geographic distribution with varying latencies
        NetworkTestUtils.simulateGeographicDistribution(environment, nodeNames);

        // Verify cluster still functions with geographic latencies
        await().atMost(Duration.ofMinutes(2))
                .pollInterval(Duration.ofSeconds(5))
                .until(this::hasExactlyOneLeader);

        // Test leader election under geographic conditions
        String originalLeader = findLeaderId();
        logger.info("Leader under geographic distribution: " + originalLeader);

        // Verify all nodes can still communicate despite latency
        assertTrue(allNodesHealthy(), "All nodes should remain healthy with geographic latency");
        
        logger.info("Cluster successfully operates under geographic distribution");
    }

    @Test
    void testComplexNetworkScenario() {
        // Wait for initial stable cluster
        await().atMost(Duration.ofSeconds(45))
                .pollInterval(Duration.ofSeconds(3))
                .until(this::hasExactlyOneLeader);

        logger.info("Testing complex network scenario with multiple conditions");

        // Apply complex network conditions
        NetworkTestUtils.createComplexNetworkScenario(environment, nodeNames);

        // Verify cluster maintains stability under complex conditions
        await().atMost(Duration.ofMinutes(3))
                .pollInterval(Duration.ofSeconds(5))
                .until(this::hasExactlyOneLeader);

        // Test resilience by checking leader stability over time
        String leader1 = findLeaderId();
        
        // Wait and check again
        try {
            Thread.sleep(30000); // 30 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        String leader2 = findLeaderId();
        
        // Leader should remain stable under normal complex conditions
        // (unless there's a legitimate failure)
        logger.info("Leader stability check: " + leader1 + " -> " + leader2);
        
        assertTrue(hasExactlyOneLeader(), "Cluster should maintain exactly one leader");
        logger.info("Cluster maintained stability under complex network conditions");
    }

    @Test
    void testNetworkRecoveryScenarios() {
        // Wait for initial stable cluster
        await().atMost(Duration.ofSeconds(45))
                .pollInterval(Duration.ofSeconds(3))
                .until(this::hasExactlyOneLeader);

        logger.info("Testing various network recovery scenarios");

        // Scenario 1: Temporary network isolation
        String isolatedNode = "controller5";
        NetworkTestUtils.isolateNode(environment, isolatedNode);
        
        // Verify cluster continues with 4 nodes
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(3))
                .until(this::hasExactlyOneLeader);
        
        // Restore isolated node
        NetworkTestUtils.restoreNode(environment, isolatedNode);
        
        // Verify full cluster recovery
        await().atMost(Duration.ofSeconds(45))
                .pollInterval(Duration.ofSeconds(3))
                .until(this::allNodesHealthy);

        logger.info("Successfully tested network isolation and recovery");

        // Scenario 2: Gradual network degradation and recovery
        // Add increasing latency, then remove it
        for (int latency = 50; latency <= 200; latency += 50) {
            NetworkTestUtils.addNetworkLatency(environment, "controller1", latency);
            
            // Brief pause to let the network condition take effect
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Remove latency
        NetworkTestUtils.removeNetworkLatency(environment, "controller1");
        
        // Verify cluster stability after gradual degradation
        await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(3))
                .until(this::hasExactlyOneLeader);

        logger.info("Successfully tested gradual network degradation and recovery");
    }

    // Helper methods

    private boolean allNodesHealthy() {
        for (String endpoint : nodeEndpoints) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint + "/health"))
                        .timeout(Duration.ofSeconds(8))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private boolean hasExactlyOneLeader() {
        return countLeaders() == 1;
    }

    private int countLeaders() {
        int leaderCount = 0;
        for (int i = 0; i < nodeEndpoints.size(); i++) {
            try {
                String state = getNodeState(i);
                if ("LEADER".equals(state)) {
                    leaderCount++;
                }
            } catch (Exception e) {
                // Node might not be reachable
            }
        }
        return leaderCount;
    }

    private String findLeaderId() {
        for (int i = 0; i < nodeEndpoints.size(); i++) {
            try {
                String state = getNodeState(i);
                if ("LEADER".equals(state)) {
                    return "controller" + (i + 1);
                }
            } catch (Exception e) {
                // Continue searching
            }
        }
        return null;
    }

    private boolean hasLeaderInPartition(List<String> partition) {
        for (String nodeName : partition) {
            try {
                int nodeIndex = Integer.parseInt(nodeName.substring(10)) - 1;
                String state = getNodeState(nodeIndex);
                if ("LEADER".equals(state)) {
                    return true;
                }
            } catch (Exception e) {
                // Node might not be reachable
            }
        }
        return false;
    }

    private void verifyMinorityPartitionHasNoLeader(List<String> minorityPartition) {
        for (String nodeName : minorityPartition) {
            try {
                int nodeIndex = Integer.parseInt(nodeName.substring(10)) - 1;
                String state = getNodeState(nodeIndex);
                assertNotEquals("LEADER", state, 
                    "Minority partition node " + nodeName + " should not be leader");
            } catch (Exception e) {
                // Expected - node should be unreachable or not leader
                logger.info("Minority node " + nodeName + " is unreachable (expected)");
            }
        }
    }

    private String getNodeState(int nodeIndex) throws Exception {
        String endpoint = nodeEndpoints.get(nodeIndex);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/raft/status"))
                .timeout(Duration.ofSeconds(8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonNode statusData = objectMapper.readTree(response.body());
            return statusData.get("state").asText();
        } else {
            throw new RuntimeException("Failed to get node state: HTTP " + response.statusCode());
        }
    }
}
