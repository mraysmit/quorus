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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Centralized configuration loader for Quorus API.
 * 
 * <p>Loads configuration from application.properties with environment variable 
 * and system property override support. Priority order:
 * <ol>
 *   <li>System property (e.g., -Dquorus.http.port=8080)</li>
 *   <li>Environment variable (e.g., QUORUS_HTTP_PORT=8080)</li>
 *   <li>Properties file value</li>
 *   <li>Default value</li>
 * </ol>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-01-28
 */
public final class ApiConfig {

    private static final Logger logger = LoggerFactory.getLogger(ApiConfig.class);
    private static final String CONFIG_FILE = "quorus-api.properties";
    private static final ApiConfig INSTANCE = new ApiConfig();

    private final Properties properties;

    private ApiConfig() {
        this.properties = new Properties();
        loadProperties();
        logConfiguration();
    }

    /**
     * Gets the singleton configuration instance.
     */
    public static ApiConfig get() {
        return INSTANCE;
    }

    // ==================== HTTP Server Configuration ====================

    public int getHttpPort() {
        return getInt("quorus.http.port", 8080);
    }

    public String getHttpHost() {
        return getString("quorus.http.host", "0.0.0.0");
    }

    // ==================== Application Info ====================

    public String getApiName() {
        return getString("quorus.api.name", "quorus-api");
    }

    public String getApiVersion() {
        return getString("quorus.api.version", "2.0");
    }

    // ==================== Agent Fleet Configuration ====================

    public long getAgentHeartbeatIntervalMs() {
        return getLong("quorus.agent.heartbeat.interval-ms", 30000);
    }

    public long getAgentTimeoutMs() {
        return getLong("quorus.agent.timeout-ms", 90000);
    }

    public long getAgentFailureCheckIntervalMs() {
        return getLong("quorus.agent.failure-check.interval-ms", 15000);
    }

    // ==================== Transfer Service Configuration ====================

    public int getMaxConcurrentTransfers() {
        return getInt("quorus.transfer.max-concurrent-transfers", 10);
    }

    public int getMaxRetryAttempts() {
        return getInt("quorus.transfer.max-retry-attempts", 3);
    }

    public long getRetryDelayMs() {
        return getLong("quorus.transfer.retry-delay-ms", 1000);
    }

    public int getTransferTimeoutSeconds() {
        return getInt("quorus.transfer.timeout-seconds", 30);
    }

    // ==================== Controller Discovery Configuration ====================

    public int getControllerDiscoveryTimeoutSeconds() {
        return getInt("quorus.controller.discovery.timeout-seconds", 3);
    }

    public int getLeaderDiscoveryTimeoutSeconds() {
        return getInt("quorus.controller.leader.discovery.timeout-seconds", 5);
    }

    public int getControllerHealthCheckIntervalSeconds() {
        return getInt("quorus.controller.health-check.interval-seconds", 10);
    }

    // ==================== Telemetry Configuration ====================

    public boolean isTelemetryEnabled() {
        return getBoolean("quorus.telemetry.enabled", true);
    }

    public String getOtlpEndpoint() {
        return getString("quorus.telemetry.otlp.endpoint", "http://localhost:4317");
    }

    // ==================== CLI Configuration ====================

    public String getCliDefaultUrl() {
        return getString("quorus.cli.default-url", "http://localhost:8080");
    }

    // ==================== Core Property Accessors ====================

    /**
     * Gets a string property with system property and environment variable override.
     * Priority: system property > env var > properties file > default.
     */
    public String getString(String key, String defaultValue) {
        // 1. Check system property
        String sysValue = System.getProperty(key);
        if (sysValue != null && !sysValue.isEmpty()) {
            return sysValue;
        }

        // 2. Check environment variable (QUORUS_XXX format)
        String envKey = key.toUpperCase().replace('.', '_').replace('-', '_');
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
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

    private void logConfiguration() {
        logger.info("=== Quorus API Configuration ===");
        logger.info("  API Name:             {}", getApiName());
        logger.info("  API Version:          {}", getApiVersion());
        logger.info("  HTTP Host:            {}", getHttpHost());
        logger.info("  HTTP Port:            {}", getHttpPort());
        logger.info("  CLI Default URL:      {}", getCliDefaultUrl());
        logger.info("  --- Agent Fleet ---");
        logger.info("  Heartbeat Interval:   {}ms", getAgentHeartbeatIntervalMs());
        logger.info("  Agent Timeout:        {}ms", getAgentTimeoutMs());
        logger.info("  Failure Check:        {}ms", getAgentFailureCheckIntervalMs());
        logger.info("  --- Transfer ---");
        logger.info("  Max Concurrent:       {}", getMaxConcurrentTransfers());
        logger.info("  Max Retries:          {}", getMaxRetryAttempts());
        logger.info("  Retry Delay:          {}ms", getRetryDelayMs());
        logger.info("  Timeout:              {}s", getTransferTimeoutSeconds());
        logger.info("  --- Controller Discovery ---");
        logger.info("  Discovery Timeout:    {}s", getControllerDiscoveryTimeoutSeconds());
        logger.info("  Leader Timeout:       {}s", getLeaderDiscoveryTimeoutSeconds());
        logger.info("  Health Check:         {}s", getControllerHealthCheckIntervalSeconds());
        logger.info("  --- Telemetry ---");
        logger.info("  Enabled:              {}", isTelemetryEnabled());
        logger.info("  OTLP Endpoint:        {}", getOtlpEndpoint());
        logger.info("================================");
    }
}
