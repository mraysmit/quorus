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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

/**
 * Centralized configuration for Quorus Controller.
 * 
 * <p>Loads configuration from application.properties with environment variable override support.
 * Environment variables take precedence and use uppercase with underscores
 * (e.g., quorus.http.port -> QUORUS_HTTP_PORT).
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-01-28
 */
public final class AppConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private static final String CONFIG_FILE = "quorus-controller.properties";
    private static final AppConfig INSTANCE = new AppConfig();

    private final Properties properties;

    private AppConfig() {
        this.properties = new Properties();
        loadProperties();
        logConfiguration();
    }

    /**
     * Gets the singleton configuration instance.
     */
    public static AppConfig get() {
        return INSTANCE;
    }

    // ==================== Node Configuration ====================

    /**
     * Gets the node ID for this controller instance.
     * 
     * <p>For multi-node clusters, an explicit node ID is REQUIRED to prevent split-brain.
     * For single-node clusters (local development), hostname fallback is allowed with a warning.
     * 
     * @return the node ID
     * @throws IllegalStateException if node ID is not set in a multi-node cluster
     */
    public String getNodeId() {
        String nodeId = getString("quorus.node.id", "");
        if (nodeId.isEmpty()) {
            if (isMultiNodeCluster()) {
                throw new IllegalStateException(
                    "quorus.node.id must be set for multi-node clusters to prevent split-brain. " +
                    "Set via environment variable QUORUS_NODE_ID, system property -Dquorus.node.id, " +
                    "or in quorus-controller.properties.");
            }
            // Single-node is safe to use hostname (local dev)
            nodeId = deriveNodeIdFromHostname();
            logger.warn("Using hostname '{}' as node ID. Set quorus.node.id explicitly for production.", nodeId);
        }
        return nodeId;
    }

    /**
     * Checks if this is a multi-node cluster configuration.
     * A multi-node cluster is detected when quorus.cluster.nodes contains multiple nodes (comma-separated).
     * 
     * @return true if multiple nodes are configured
     */
    private boolean isMultiNodeCluster() {
        String nodes = getString("quorus.cluster.nodes", "");
        return !nodes.isEmpty() && nodes.contains(",");
    }

    private String deriveNodeIdFromHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            logger.warn("Could not determine hostname, using fallback node ID");
            return "node-" + ProcessHandle.current().pid();
        }
    }

    // ==================== HTTP Configuration ====================

    public int getHttpPort() {
        return getInt("quorus.http.port", 8080);
    }

    public String getHttpHost() {
        return getString("quorus.http.host", "0.0.0.0");
    }

    // ==================== Raft Configuration ====================

    public int getRaftPort() {
        return getInt("quorus.raft.port", 9080);
    }

    public String getClusterNodes() {
        String nodes = getString("quorus.cluster.nodes", "");
        // If empty, default to single-node with current nodeId
        if (nodes.isEmpty()) {
            return getNodeId() + "=localhost:" + getRaftPort();
        }
        return nodes;
    }

    // ==================== Raft Storage Configuration ====================

    /**
     * Gets the Raft storage backend type.
     * Supported values: "file" (default), "rocksdb", "memory" (testing only).
     */
    public String getRaftStorageType() {
        return getString("quorus.raft.storage.type", "file");
    }

    /**
     * Gets the path for Raft persistent storage.
     * Defaults to ./data/raft/{nodeId} for local development.
     */
    public String getRaftStoragePath() {
        String defaultPath = "./data/raft/" + getNodeId();
        return getString("quorus.raft.storage.path", defaultPath);
    }

    /**
     * Whether to fsync after each WAL write.
     * Defaults to true for durability; set to false only for testing.
     */
    public boolean getRaftStorageFsync() {
        return getBoolean("quorus.raft.storage.fsync", true);
    }

    // ==================== Snapshot Configuration ====================

    /**
     * Whether snapshot-based log compaction is enabled.
     * When enabled, the leader periodically takes snapshots and truncates the log.
     */
    public boolean isSnapshotEnabled() {
        return getBoolean("quorus.raft.snapshot.enabled", true);
    }

    /**
     * Number of committed log entries between automatic snapshots.
     * A snapshot is triggered when (lastApplied - lastSnapshotIndex) exceeds this threshold.
     * Lower values reduce recovery time but increase I/O. Default: 10000.
     */
    public long getSnapshotThreshold() {
        return getLong("quorus.raft.snapshot.threshold", 10000);
    }

    /**
     * Interval in milliseconds between snapshot eligibility checks.
     * The leader checks whether the threshold has been reached at this interval.
     * Default: 60000 (1 minute).
     */
    public long getSnapshotCheckIntervalMs() {
        return getLong("quorus.raft.snapshot.check-interval-ms", 60000);
    }

    // ==================== Telemetry Configuration ====================

    public boolean isTelemetryEnabled() {
        return getBoolean("quorus.telemetry.enabled", true);
    }

    public String getOtlpEndpoint() {
        return getString("quorus.telemetry.otlp.endpoint", "http://localhost:4317");
    }

    public int getPrometheusPort() {
        return getInt("quorus.telemetry.prometheus.port", 9464);
    }

    public String getServiceName() {
        return getString("quorus.telemetry.service.name", "quorus-controller");
    }

    // ==================== Thread Pool Configuration ====================

    /**
     * Gets the maximum pool size for Raft I/O operations (gRPC callbacks).
     * This replaces the unbounded newCachedThreadPool for better resource control.
     * 
     * @return pool size (default: 10)
     */
    public int getRaftIoPoolSize() {
        return getInt("quorus.raft.io.pool-size", 10);
    }

    // ==================== Job Assignment Configuration ====================

    public long getAssignmentInitialDelayMs() {
        return getLong("quorus.jobs.assignment.initial-delay-ms", 5000);
    }

    public long getAssignmentIntervalMs() {
        return getLong("quorus.jobs.assignment.interval-ms", 10000);
    }

    public long getTimeoutInitialDelayMs() {
        return getLong("quorus.jobs.timeout.initial-delay-ms", 30000);
    }

    public long getTimeoutIntervalMs() {
        return getLong("quorus.jobs.timeout.interval-ms", 30000);
    }

    // ==================== Application Info ====================

    public String getVersion() {
        return getString("quorus.version", "2.0-ext");
    }

    // ==================== Core Property Accessors ====================

    /**
     * Gets a string property with environment variable and system property override.
     * 
     * <p>Resolution order (highest to lowest priority):
     * <ol>
     *   <li>Environment variable (e.g., QUORUS_HTTP_PORT)</li>
     *   <li>System property (e.g., -Dquorus.http.port=8080)</li>
     *   <li>Properties file (quorus-controller.properties)</li>
     *   <li>Default value</li>
     * </ol>
     * 
     * @param key the property key (e.g., "quorus.http.port")
     * @param defaultValue the default value if not found
     * @return the resolved property value
     */
    public String getString(String key, String defaultValue) {
        // 1. Check environment variable (QUORUS_HTTP_PORT format)
        String envKey = key.toUpperCase().replace('.', '_').replace('-', '_');
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }

        // 2. Check system property (-Dquorus.http.port format)
        String sysValue = System.getProperty(key);
        if (sysValue != null && !sysValue.isEmpty()) {
            return sysValue;
        }

        // 3. Check properties file
        return properties.getProperty(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for {}: '{}', using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid long value for {}: '{}', using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    // ==================== Private Helpers ====================

    /**
     * Validates that required configuration is present and values are sensible.
     * Called during startup to fail fast on misconfiguration.
     *
     * @throws IllegalStateException if required configuration is invalid
     */
    public void validate() {
        // Validate port ranges
        int httpPort = getHttpPort();
        if (httpPort < 1 || httpPort > 65535) {
            throw new IllegalStateException(
                    "HTTP port must be between 1 and 65535, got: " + httpPort);
        }

        int raftPort = getRaftPort();
        if (raftPort < 1 || raftPort > 65535) {
            throw new IllegalStateException(
                    "Raft port must be between 1 and 65535, got: " + raftPort);
        }

        if (httpPort == raftPort) {
            throw new IllegalStateException(
                    "HTTP port and Raft port must be different, both are: " + httpPort);
        }

        // Validate thread pool size
        int poolSize = getRaftIoPoolSize();
        if (poolSize < 1 || poolSize > 100) {
            throw new IllegalStateException(
                    "Raft I/O pool size must be between 1 and 100, got: " + poolSize);
        }

        // Validate positive intervals
        if (getAssignmentIntervalMs() <= 0) {
            throw new IllegalStateException(
                    "Assignment interval must be positive, got: " + getAssignmentIntervalMs());
        }
        if (getTimeoutIntervalMs() <= 0) {
            throw new IllegalStateException(
                    "Timeout interval must be positive, got: " + getTimeoutIntervalMs());
        }

        // Validate snapshot threshold
        if (getSnapshotThreshold() < 1) {
            throw new IllegalStateException(
                    "Snapshot threshold must be positive, got: " + getSnapshotThreshold());
        }
        if (getSnapshotCheckIntervalMs() < 1000) {
            throw new IllegalStateException(
                    "Snapshot check interval must be at least 1000ms, got: " + getSnapshotCheckIntervalMs());
        }

        // Validate storage type
        String storageType = getRaftStorageType();
        if (!storageType.equals("raftlog") && !storageType.equals("file") && !storageType.equals("memory")) {
            throw new IllegalStateException(
                    "Raft storage type must be 'raftlog', 'file', or 'memory', got: " + storageType);
        }

        logger.info("Controller configuration validated successfully");
    }

    private void loadProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                properties.load(input);
                logger.info("Loaded configuration from {}", CONFIG_FILE);
            } else {
                logger.warn("Configuration file {} not found, using defaults", CONFIG_FILE);
            }
        } catch (IOException e) {
            logger.error("Error loading configuration file: {}", e.getMessage());
            logger.debug("Stack trace for configuration load error", e);
        }
    }

    private void logConfiguration() {
        logger.info("=== Quorus Controller Configuration ===");
        logger.info("  Node ID:              {}", getNodeId());
        logger.info("  HTTP Host:            {}", getHttpHost());
        logger.info("  HTTP Port:            {}", getHttpPort());
        logger.info("  Raft Port:            {}", getRaftPort());
        logger.info("  Cluster Nodes:        {}", getClusterNodes());
        logger.info("  Service Name:         {}", getServiceName());
        logger.info("  Version:              {}", getVersion());
        logger.info("  --- Thread Pools ---");
        logger.info("  Raft I/O Pool Size:   {}", getRaftIoPoolSize());
        logger.info("  --- Job Assignment ---");
        logger.info("  Initial Delay:        {}ms", getAssignmentInitialDelayMs());
        logger.info("  Assignment Interval:  {}ms", getAssignmentIntervalMs());
        logger.info("  Timeout Initial:      {}ms", getTimeoutInitialDelayMs());
        logger.info("  Timeout Interval:     {}ms", getTimeoutIntervalMs());
        logger.info("  --- Snapshot ---");
        logger.info("  Enabled:              {}", isSnapshotEnabled());
        logger.info("  Threshold:            {} entries", getSnapshotThreshold());
        logger.info("  Check Interval:       {}ms", getSnapshotCheckIntervalMs());
        logger.info("  --- Telemetry ---");
        logger.info("  Enabled:              {}", isTelemetryEnabled());
        logger.info("  OTLP Endpoint:        {}", getOtlpEndpoint());
        logger.info("  Prometheus Port:      {}", getPrometheusPort());
        logger.info("========================================");
    }
}
