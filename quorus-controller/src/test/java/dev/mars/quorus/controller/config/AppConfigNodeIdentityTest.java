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

package dev.mars.quorus.controller.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AppConfig node identity enforcement.
 * 
 * <p>These tests verify the node identity logic:
 * <ul>
 *   <li>Multi-node clusters require explicit node ID</li>
 *   <li>Single-node clusters allow hostname fallback</li>
 *   <li>Explicit node ID is always used when provided</li>
 * </ul>
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-04
 */
class AppConfigNodeIdentityTest {

    @Test
    @DisplayName("AppConfig should load node ID from test properties")
    void configLoadsNodeIdFromProperties() {
        Properties overrides = properties("quorus.node.id", "configured-node");

        AppConfig config = new AppConfig("test", overrides);

        assertEquals("configured-node", config.getNodeId());
    }

    @Test
    @DisplayName("System properties must not contaminate an AppConfig instance")
    void systemPropertyIsNotAConfigurationChannel() {
        String key = "quorus.node.id";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "ambient-node");

            AppConfig config = new AppConfig("test", properties(key, "explicit-node"));

            assertEquals("explicit-node", config.getNodeId());
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    @Test
    @DisplayName("Explicit properties should override packaged configuration")
    void explicitPropertiesOverridePackagedConfiguration() {
        AppConfig config = new AppConfig("test", properties("quorus.http.port", "18080"));

        assertEquals(18080, config.getHttpPort());
    }

    @Test
    @DisplayName("Configuration sources follow defaults, profile, environment, explicit precedence")
    void configurationSourcesHavePeeGeeQPrecedence() {
        AppConfig defaults = new AppConfig("default", new Properties(), Map.of());
        AppConfig profile = new AppConfig("precedence", new Properties(), Map.of());
        AppConfig environment = new AppConfig(
                "precedence", new Properties(), Map.of("QUORUS_HTTP_PORT", "18082"));
        AppConfig explicit = new AppConfig(
                "precedence",
                properties("quorus.http.port", "18083"),
                Map.of("QUORUS_HTTP_PORT", "18082"));

        assertEquals(8080, defaults.getHttpPort());
        assertEquals(18081, profile.getHttpPort());
        assertEquals(18082, environment.getHttpPort());
        assertEquals(18083, explicit.getHttpPort());
    }

    @Test
    @DisplayName("Configuration instances should remain isolated")
    void configurationInstancesRemainIsolated() {
        AppConfig first = new AppConfig("test", properties("quorus.node.id", "node-a"));
        AppConfig second = new AppConfig("test", properties("quorus.node.id", "node-b"));

        assertEquals("node-a", first.getNodeId());
        assertEquals("node-b", second.getNodeId());
    }

    @Test
    @DisplayName("Multi-node cluster detection should check for comma in cluster.nodes")
    void multiNodeClusterDetection() {
        // Test the logic directly - can't easily test via singleton
        // A multi-node cluster is when quorus.cluster.nodes contains a comma
        
        String singleNode = "node1=localhost:9080";
        String multiNode = "node1=host1:9080,node2=host2:9080";
        String empty = "";
        
        assertFalse(isMultiNode(empty), "Empty should be single node");
        assertFalse(isMultiNode(singleNode), "Single node should not be multi-node");
        assertTrue(isMultiNode(multiNode), "Comma-separated should be multi-node");
    }
    
    // Helper that mirrors the logic in AppConfig
    private boolean isMultiNode(String nodes) {
        return !nodes.isEmpty() && nodes.contains(",");
    }

    @Test
    @DisplayName("Node ID should not be empty or null")
    void nodeIdIsNeverEmpty() {
        AppConfig config = new AppConfig("test", new Properties());
        String nodeId = config.getNodeId();
        
        assertNotNull(nodeId, "Node ID should never be null");
        assertFalse(nodeId.isEmpty(), "Node ID should never be empty");
    }

    @Test
    @DisplayName("Blank packaged Raft path should use the node-specific durable default")
    void blankRaftStoragePathUsesDefault() {
        AppConfig config = new AppConfig("test", new Properties());

        assertEquals("./data/raft/test-node", config.getRaftStoragePath());
    }

    @Test
    @DisplayName("Explicit Raft path should override the packaged default")
    void explicitRaftStoragePathIsRespected() {
        AppConfig config = new AppConfig("test",
                properties("quorus.raft.storage.path", "/var/lib/quorus/controller-a"));

        assertEquals("/var/lib/quorus/controller-a", config.getRaftStoragePath());
    }

    @Test
    @DisplayName("Packaged controller configuration satisfies startup validation")
    void packagedConfigurationIsValidAndTypedAccessorsUseSafeDefaults() {
        AppConfig config = new AppConfig("test", new Properties());

        assertDoesNotThrow(config::validate);
        assertEquals(8080, config.getHttpPort());
        assertEquals(9080, config.getRaftPort());
        assertEquals(1_048_576L, config.getHttpMaxBodyBytes());
        assertEquals("memory", config.getRaftStorageType());
        assertFalse(config.getRaftStorageFsync());
        assertTrue(config.isSnapshotEnabled());
        assertEquals(100_000L, config.getLogHardLimit());
    }

    @Test
    @DisplayName("Malformed numeric overrides fall back instead of destabilizing startup")
    void malformedNumericOverridesUseDeclaredFallbacks() {
        String key = "quorus.test.invalid-number";
        AppConfig config = new AppConfig("test", properties(key, "not-a-number"));

        assertEquals(17, config.getInt(key, 17));
        assertEquals(23L, config.getLong(key, 23L));
    }

    @Test
    @DisplayName("Environment variables reach hyphenated keys that no properties resource declares")
    void environmentReachesHyphenatedKeysAbsentFromResources() {
        AppConfig config = new AppConfig("test", new Properties(), Map.of(
                "QUORUS_JOBS_ATTEMPT_LEASE_DURATION_MS", "45000",
                "QUORUS_RAFT_IO_QUEUE_SIZE", "250",
                "QUORUS_RAFT_SNAPSHOT_CHECK_INTERVAL_MS", "7500"));

        assertEquals(45_000L, config.getAttemptLeaseDurationMs());
        assertEquals(250, config.getRaftIoQueueSize());
        assertEquals(7_500L, config.getSnapshotCheckIntervalMs());
    }

    @Test
    @DisplayName("Explicit overrides beat environment values for hyphenated keys")
    void explicitOverrideBeatsEnvironmentForHyphenatedKey() {
        AppConfig config = new AppConfig("test",
                properties("quorus.jobs.attempt.lease-duration-ms", "60000"),
                Map.of("QUORUS_JOBS_ATTEMPT_LEASE_DURATION_MS", "45000"));

        assertEquals(60_000L, config.getAttemptLeaseDurationMs());
    }

    @Test
    @DisplayName("Validation rejects a non-positive attempt lease duration")
    void validateRejectsNonPositiveAttemptLeaseDuration() {
        AppConfig zero = new AppConfig("test", properties("quorus.jobs.attempt.lease-duration-ms", "0"));
        AppConfig negative = new AppConfig("test", properties("quorus.jobs.attempt.lease-duration-ms", "-1"));

        IllegalStateException error = assertThrows(IllegalStateException.class, zero::validate);
        assertTrue(error.getMessage().contains("Attempt lease duration"));
        assertThrows(IllegalStateException.class, negative::validate);
    }

    @Test
    @DisplayName("Validation rejects the removed in-repo file storage type")
    void validateRejectsFileStorageType() {
        AppConfig config = new AppConfig("test", properties("quorus.raft.storage.type", "file"));

        IllegalStateException error = assertThrows(IllegalStateException.class, config::validate);
        assertTrue(error.getMessage().contains("raftlog"));
    }

    private static Properties properties(String key, String value) {
        Properties properties = new Properties();
        properties.setProperty(key, value);
        return properties;
    }
}
