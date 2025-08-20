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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration management for Raft cluster testing scenarios.
 * Provides predefined configurations for different test types.
 */
public class TestClusterConfiguration {

    private final int nodeCount;
    private final Duration electionTimeout;
    private final Duration heartbeatInterval;
    private final Duration startupTimeout;
    private final Duration testTimeout;
    private final String composeFile;
    private final Map<String, String> environmentVariables;
    private final NetworkConfiguration networkConfig;

    private TestClusterConfiguration(Builder builder) {
        this.nodeCount = builder.nodeCount;
        this.electionTimeout = builder.electionTimeout;
        this.heartbeatInterval = builder.heartbeatInterval;
        this.startupTimeout = builder.startupTimeout;
        this.testTimeout = builder.testTimeout;
        this.composeFile = builder.composeFile;
        this.environmentVariables = new HashMap<>(builder.environmentVariables);
        this.networkConfig = builder.networkConfig;
    }

    // Getters
    public int getNodeCount() { return nodeCount; }
    public Duration getElectionTimeout() { return electionTimeout; }
    public Duration getHeartbeatInterval() { return heartbeatInterval; }
    public Duration getStartupTimeout() { return startupTimeout; }
    public Duration getTestTimeout() { return testTimeout; }
    public String getComposeFile() { return composeFile; }
    public Map<String, String> getEnvironmentVariables() { return new HashMap<>(environmentVariables); }
    public NetworkConfiguration getNetworkConfig() { return networkConfig; }

    /**
     * Network configuration for testing scenarios.
     */
    public static class NetworkConfiguration {
        private final Duration networkLatency;
        private final int packetLossPercent;
        private final boolean enablePartitionTesting;
        private final Duration partitionDuration;

        public NetworkConfiguration(Duration networkLatency, int packetLossPercent, 
                                  boolean enablePartitionTesting, Duration partitionDuration) {
            this.networkLatency = networkLatency;
            this.packetLossPercent = packetLossPercent;
            this.enablePartitionTesting = enablePartitionTesting;
            this.partitionDuration = partitionDuration;
        }

        public Duration getNetworkLatency() { return networkLatency; }
        public int getPacketLossPercent() { return packetLossPercent; }
        public boolean isPartitionTestingEnabled() { return enablePartitionTesting; }
        public Duration getPartitionDuration() { return partitionDuration; }
    }

    /**
     * Builder for test cluster configurations.
     */
    public static class Builder {
        private int nodeCount = 3;
        private Duration electionTimeout = Duration.ofSeconds(3);
        private Duration heartbeatInterval = Duration.ofMillis(500);
        private Duration startupTimeout = Duration.ofMinutes(5);
        private Duration testTimeout = Duration.ofMinutes(10);
        private String composeFile = "docker-compose.yml";
        private Map<String, String> environmentVariables = new HashMap<>();
        private NetworkConfiguration networkConfig = new NetworkConfiguration(
                Duration.ofMillis(10), 0, false, Duration.ofSeconds(30));

        public Builder nodeCount(int nodeCount) {
            this.nodeCount = nodeCount;
            return this;
        }

        public Builder electionTimeout(Duration electionTimeout) {
            this.electionTimeout = electionTimeout;
            return this;
        }

        public Builder heartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
            return this;
        }

        public Builder startupTimeout(Duration startupTimeout) {
            this.startupTimeout = startupTimeout;
            return this;
        }

        public Builder testTimeout(Duration testTimeout) {
            this.testTimeout = testTimeout;
            return this;
        }

        public Builder composeFile(String composeFile) {
            this.composeFile = composeFile;
            return this;
        }

        public Builder addEnvironmentVariable(String key, String value) {
            this.environmentVariables.put(key, value);
            return this;
        }

        public Builder networkConfig(NetworkConfiguration networkConfig) {
            this.networkConfig = networkConfig;
            return this;
        }

        public TestClusterConfiguration build() {
            return new TestClusterConfiguration(this);
        }
    }

    // Predefined configurations for common test scenarios

    /**
     * Fast test configuration for unit testing.
     * Small timeouts, minimal startup time.
     */
    public static TestClusterConfiguration fastTest() {
        return new Builder()
                .nodeCount(3)
                .electionTimeout(Duration.ofSeconds(1))
                .heartbeatInterval(Duration.ofMillis(200))
                .startupTimeout(Duration.ofMinutes(2))
                .testTimeout(Duration.ofMinutes(3))
                .composeFile("docker-compose.yml")
                .addEnvironmentVariable("JAVA_OPTS", "-Xmx128m -Xms64m")
                .networkConfig(new NetworkConfiguration(
                        Duration.ofMillis(5), 0, false, Duration.ofSeconds(10)))
                .build();
    }

    /**
     * Standard test configuration for integration testing.
     * Realistic timeouts, moderate startup time.
     */
    public static TestClusterConfiguration standardTest() {
        return new Builder()
                .nodeCount(3)
                .electionTimeout(Duration.ofSeconds(3))
                .heartbeatInterval(Duration.ofMillis(500))
                .startupTimeout(Duration.ofMinutes(5))
                .testTimeout(Duration.ofMinutes(10))
                .composeFile("docker-compose.yml")
                .addEnvironmentVariable("JAVA_OPTS", "-Xmx256m -Xms128m")
                .networkConfig(new NetworkConfiguration(
                        Duration.ofMillis(10), 0, false, Duration.ofSeconds(30)))
                .build();
    }

    /**
     * Large cluster configuration for scalability testing.
     * 5 nodes, longer timeouts.
     */
    public static TestClusterConfiguration largeClusterTest() {
        return new Builder()
                .nodeCount(5)
                .electionTimeout(Duration.ofSeconds(5))
                .heartbeatInterval(Duration.ofSeconds(1))
                .startupTimeout(Duration.ofMinutes(8))
                .testTimeout(Duration.ofMinutes(15))
                .composeFile("docker-compose-5node.yml")
                .addEnvironmentVariable("JAVA_OPTS", "-Xmx256m -Xms128m")
                .networkConfig(new NetworkConfiguration(
                        Duration.ofMillis(20), 0, false, Duration.ofSeconds(45)))
                .build();
    }

    /**
     * Network partition testing configuration.
     * Enables partition testing with appropriate timeouts.
     */
    public static TestClusterConfiguration partitionTest() {
        return new Builder()
                .nodeCount(5)
                .electionTimeout(Duration.ofSeconds(4))
                .heartbeatInterval(Duration.ofMillis(800))
                .startupTimeout(Duration.ofMinutes(6))
                .testTimeout(Duration.ofMinutes(20))
                .composeFile("docker-compose-5node.yml")
                .addEnvironmentVariable("JAVA_OPTS", "-Xmx256m -Xms128m")
                .networkConfig(new NetworkConfiguration(
                        Duration.ofMillis(50), 2, true, Duration.ofMinutes(2)))
                .build();
    }

    /**
     * Stress test configuration with network issues.
     * High latency, packet loss, frequent partitions.
     */
    public static TestClusterConfiguration stressTest() {
        return new Builder()
                .nodeCount(5)
                .electionTimeout(Duration.ofSeconds(8))
                .heartbeatInterval(Duration.ofSeconds(2))
                .startupTimeout(Duration.ofMinutes(10))
                .testTimeout(Duration.ofMinutes(30))
                .composeFile("docker-compose-5node.yml")
                .addEnvironmentVariable("JAVA_OPTS", "-Xmx512m -Xms256m")
                .networkConfig(new NetworkConfiguration(
                        Duration.ofMillis(200), 5, true, Duration.ofMinutes(1)))
                .build();
    }

    /**
     * Minimal resource configuration for CI/CD environments.
     * Reduced memory usage, faster timeouts.
     */
    public static TestClusterConfiguration ciTest() {
        return new Builder()
                .nodeCount(3)
                .electionTimeout(Duration.ofSeconds(2))
                .heartbeatInterval(Duration.ofMillis(400))
                .startupTimeout(Duration.ofMinutes(3))
                .testTimeout(Duration.ofMinutes(5))
                .composeFile("docker-compose.yml")
                .addEnvironmentVariable("JAVA_OPTS", "-Xmx96m -Xms48m")
                .addEnvironmentVariable("STARTUP_DELAY", "5")
                .networkConfig(new NetworkConfiguration(
                        Duration.ofMillis(5), 0, false, Duration.ofSeconds(15)))
                .build();
    }

    /**
     * Development configuration for local testing.
     * Balanced settings for development workflow.
     */
    public static TestClusterConfiguration developmentTest() {
        return new Builder()
                .nodeCount(3)
                .electionTimeout(Duration.ofSeconds(3))
                .heartbeatInterval(Duration.ofMillis(600))
                .startupTimeout(Duration.ofMinutes(4))
                .testTimeout(Duration.ofMinutes(8))
                .composeFile("docker-compose.yml")
                .addEnvironmentVariable("JAVA_OPTS", "-Xmx256m -Xms128m")
                .addEnvironmentVariable("LOG_LEVEL", "DEBUG")
                .networkConfig(new NetworkConfiguration(
                        Duration.ofMillis(15), 0, false, Duration.ofSeconds(20)))
                .build();
    }

    @Override
    public String toString() {
        return "TestClusterConfiguration{" +
                "nodeCount=" + nodeCount +
                ", electionTimeout=" + electionTimeout +
                ", heartbeatInterval=" + heartbeatInterval +
                ", startupTimeout=" + startupTimeout +
                ", testTimeout=" + testTimeout +
                ", composeFile='" + composeFile + '\'' +
                ", environmentVariables=" + environmentVariables +
                ", networkConfig=" + networkConfig +
                '}';
    }
}
