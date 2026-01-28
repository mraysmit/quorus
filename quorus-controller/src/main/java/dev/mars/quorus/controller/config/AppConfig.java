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

    public String getNodeId() {
        String nodeId = getString("quorus.node.id", "");
        if (nodeId.isEmpty()) {
            nodeId = deriveNodeIdFromHostname();
        }
        return nodeId;
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
     * Gets a string property with environment variable override.
     */
    public String getString(String key, String defaultValue) {
        // 1. Check environment variable (QUORUS_HTTP_PORT format)
        String envKey = key.toUpperCase().replace('.', '_').replace('-', '_');
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }

        // 2. Check properties file
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

    private void loadProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                properties.load(input);
                logger.info("Loaded configuration from {}", CONFIG_FILE);
            } else {
                logger.warn("Configuration file {} not found, using defaults", CONFIG_FILE);
            }
        } catch (IOException e) {
            logger.error("Error loading configuration file", e);
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
        logger.info("  --- Job Assignment ---");
        logger.info("  Initial Delay:        {}ms", getAssignmentInitialDelayMs());
        logger.info("  Assignment Interval:  {}ms", getAssignmentIntervalMs());
        logger.info("  Timeout Initial:      {}ms", getTimeoutInitialDelayMs());
        logger.info("  Timeout Interval:     {}ms", getTimeoutIntervalMs());
        logger.info("  --- Telemetry ---");
        logger.info("  Enabled:              {}", isTelemetryEnabled());
        logger.info("  OTLP Endpoint:        {}", getOtlpEndpoint());
        logger.info("  Prometheus Port:      {}", getPrometheusPort());
        logger.info("========================================");
    }
}
