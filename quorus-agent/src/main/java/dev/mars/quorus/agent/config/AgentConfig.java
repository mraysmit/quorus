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

package dev.mars.quorus.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

/**
 * Centralized configuration loader for Quorus Agent.
 * 
 * <p>Loads configuration from application.properties with environment variable override support.
 * Environment variables take precedence using legacy naming convention (AGENT_ID, CONTROLLER_URL, etc.)
 * and new convention (QUORUS_AGENT_ID, QUORUS_AGENT_CONTROLLER_URL, etc.).
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-01-28
 */
public final class AgentConfig {

    private static final Logger logger = LoggerFactory.getLogger(AgentConfig.class);
    private static final String CONFIG_FILE = "quorus-agent.properties";
    private static final AgentConfig INSTANCE = new AgentConfig();

    private final Properties properties;

    private AgentConfig() {
        this.properties = new Properties();
        loadProperties();
        logConfiguration();
    }

    /**
     * Gets the singleton configuration instance.
     */
    public static AgentConfig get() {
        return INSTANCE;
    }

    // ==================== Agent Identity ====================

    /**
     * Gets the agent ID. Required - must be set via property or environment variable.
     * @throws IllegalStateException if agent ID is not configured
     */
    public String getAgentId() {
        String agentId = getString("quorus.agent.id", "");
        if (agentId.isEmpty()) {
            // Try legacy env var
            agentId = System.getenv("AGENT_ID");
        }
        if (agentId == null || agentId.isEmpty()) {
            // Derive from hostname as fallback
            agentId = deriveAgentIdFromHostname();
            logger.info("Agent ID not configured, derived from hostname: {}", agentId);
        }
        return agentId;
    }

    public String getVersion() {
        return getString("quorus.agent.version", "1.0.0");
    }

    // ==================== Controller Connection ====================

    public String getControllerUrl() {
        return getString("quorus.agent.controller.url", "http://localhost:8080/api/v1");
    }

    // ==================== Network Configuration ====================

    public int getAgentPort() {
        return getInt("quorus.agent.port", 8080);
    }

    public String getRegion() {
        return getString("quorus.agent.region", "default");
    }

    public String getDatacenter() {
        return getString("quorus.agent.datacenter", "default");
    }

    // ==================== Transfer Configuration ====================

    public int getMaxConcurrentTransfers() {
        return getInt("quorus.agent.transfers.max-concurrent", 5);
    }

    public String getSupportedProtocols() {
        return getString("quorus.agent.protocols", "HTTP,HTTPS");
    }

    // ==================== Heartbeat Configuration ====================

    public long getHeartbeatIntervalMs() {
        return getLong("quorus.agent.heartbeat.interval-ms", 30000);
    }

    // ==================== Job Polling Configuration ====================

    public long getJobPollingInitialDelayMs() {
        return getLong("quorus.agent.jobs.polling.initial-delay-ms", 5000);
    }

    public long getJobPollingIntervalMs() {
        return getLong("quorus.agent.jobs.polling.interval-ms", 10000);
    }

    // ==================== Telemetry Configuration ====================

    public boolean isTelemetryEnabled() {
        return getBoolean("quorus.agent.telemetry.enabled", true);
    }

    public int getPrometheusPort() {
        return getInt("quorus.agent.telemetry.prometheus.port", 9465);
    }

    public String getOtlpEndpoint() {
        return getString("quorus.agent.telemetry.otlp.endpoint", "http://localhost:4317");
    }

    // ==================== Core Property Accessors ====================

    /**
     * Gets a string property with environment variable override.
     */
    public String getString(String key, String defaultValue) {
        // 1. Check environment variable (QUORUS_AGENT_XXX format)
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
                logger.warn("Configuration file {} not found, using defaults and environment variables", CONFIG_FILE);
            }
        } catch (IOException e) {
            logger.error("Error loading configuration file", e);
        }
    }

    private String deriveAgentIdFromHostname() {
        try {
            return "agent-" + InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            logger.warn("Could not determine hostname, using fallback agent ID");
            return "agent-" + ProcessHandle.current().pid();
        }
    }

    private void logConfiguration() {
        logger.info("=== Quorus Agent Configuration ===");
        logger.info("  Agent ID:             {}", getAgentId());
        logger.info("  Agent Port:           {}", getAgentPort());
        logger.info("  Controller URL:       {}", getControllerUrl());
        logger.info("  Region:               {}", getRegion());
        logger.info("  Datacenter:           {}", getDatacenter());
        logger.info("  Supported Protocols:  {}", getSupportedProtocols());
        logger.info("  Version:              {}", getVersion());
        logger.info("  --- Transfer ---");
        logger.info("  Max Concurrent:       {}", getMaxConcurrentTransfers());
        logger.info("  --- Heartbeat ---");
        logger.info("  Interval:             {}ms", getHeartbeatIntervalMs());
        logger.info("  --- Job Polling ---");
        logger.info("  Initial Delay:        {}ms", getJobPollingInitialDelayMs());
        logger.info("  Poll Interval:        {}ms", getJobPollingIntervalMs());
        logger.info("  --- Telemetry ---");
        logger.info("  Enabled:              {}", isTelemetryEnabled());
        logger.info("  Prometheus Port:      {}", getPrometheusPort());
        logger.info("  OTLP Endpoint:        {}", getOtlpEndpoint());
        logger.info("==================================");
    }
}
