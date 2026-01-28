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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ApiConfig configuration loading.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-01-28
 */
class ApiConfigTest {

    @Test
    @DisplayName("Should return singleton instance")
    void shouldReturnSingletonInstance() {
        ApiConfig config1 = ApiConfig.get();
        ApiConfig config2 = ApiConfig.get();
        
        assertSame(config1, config2, "Should return the same singleton instance");
    }

    @ParameterizedTest(name = "{0} should have reasonable default")
    @MethodSource("defaultValueTestCases")
    @DisplayName("Should return expected default values")
    void shouldReturnExpectedDefaultValues(String propertyName, Object actualValue) {
        assertNotNull(actualValue, propertyName + " should not be null");
    }

    static Stream<Arguments> defaultValueTestCases() {
        ApiConfig config = ApiConfig.get();
        return Stream.of(
            Arguments.of("httpPort", config.getHttpPort()),
            Arguments.of("httpHost", config.getHttpHost()),
            Arguments.of("apiName", config.getApiName()),
            Arguments.of("apiVersion", config.getApiVersion()),
            Arguments.of("agentHeartbeatIntervalMs", config.getAgentHeartbeatIntervalMs()),
            Arguments.of("agentTimeoutMs", config.getAgentTimeoutMs()),
            Arguments.of("agentFailureCheckIntervalMs", config.getAgentFailureCheckIntervalMs()),
            Arguments.of("maxConcurrentTransfers", config.getMaxConcurrentTransfers()),
            Arguments.of("maxRetryAttempts", config.getMaxRetryAttempts()),
            Arguments.of("retryDelayMs", config.getRetryDelayMs()),
            Arguments.of("transferTimeoutSeconds", config.getTransferTimeoutSeconds()),
            Arguments.of("controllerDiscoveryTimeoutSeconds", config.getControllerDiscoveryTimeoutSeconds()),
            Arguments.of("leaderDiscoveryTimeoutSeconds", config.getLeaderDiscoveryTimeoutSeconds()),
            Arguments.of("controllerHealthCheckIntervalSeconds", config.getControllerHealthCheckIntervalSeconds())
        );
    }

    @Test
    @DisplayName("Should return valid HTTP port")
    void shouldReturnValidHttpPort() {
        ApiConfig config = ApiConfig.get();
        
        int port = config.getHttpPort();
        
        assertTrue(port > 0 && port <= 65535, "HTTP port should be valid");
    }

    @Test
    @DisplayName("Should return valid HTTP host")
    void shouldReturnValidHttpHost() {
        ApiConfig config = ApiConfig.get();
        
        String host = config.getHttpHost();
        
        assertNotNull(host, "HTTP host should not be null");
        assertFalse(host.isEmpty(), "HTTP host should not be empty");
    }

    @Test
    @DisplayName("Should return valid timing values")
    void shouldReturnValidTimingValues() {
        ApiConfig config = ApiConfig.get();
        
        long heartbeat = config.getAgentHeartbeatIntervalMs();
        long timeout = config.getAgentTimeoutMs();
        long failureCheck = config.getAgentFailureCheckIntervalMs();
        
        assertTrue(heartbeat >= 1000, "Heartbeat interval should be at least 1 second");
        assertTrue(timeout > heartbeat, "Timeout should be greater than heartbeat interval");
        assertTrue(failureCheck >= 1000, "Failure check interval should be at least 1 second");
    }

    @Test
    @DisplayName("Agent timeout should be multiple of heartbeat interval")
    void agentTimeoutShouldBeMultipleOfHeartbeat() {
        ApiConfig config = ApiConfig.get();
        
        long heartbeat = config.getAgentHeartbeatIntervalMs();
        long timeout = config.getAgentTimeoutMs();
        
        // Timeout should allow for at least 2 missed heartbeats
        assertTrue(timeout >= heartbeat * 2, 
            "Agent timeout should be at least 2x heartbeat interval");
    }

    @Test
    @DisplayName("Should return valid transfer configuration")
    void shouldReturnValidTransferConfiguration() {
        ApiConfig config = ApiConfig.get();
        
        int maxConcurrent = config.getMaxConcurrentTransfers();
        int maxRetries = config.getMaxRetryAttempts();
        long retryDelay = config.getRetryDelayMs();
        int timeout = config.getTransferTimeoutSeconds();
        
        assertTrue(maxConcurrent > 0, "Max concurrent transfers should be positive");
        assertTrue(maxRetries >= 0, "Max retry attempts should be non-negative");
        assertTrue(retryDelay >= 0, "Retry delay should be non-negative");
        assertTrue(timeout > 0, "Transfer timeout should be positive");
    }

    @Test
    @DisplayName("Should return valid controller discovery settings")
    void shouldReturnValidControllerDiscoverySettings() {
        ApiConfig config = ApiConfig.get();
        
        int discoveryTimeout = config.getControllerDiscoveryTimeoutSeconds();
        int leaderTimeout = config.getLeaderDiscoveryTimeoutSeconds();
        int healthCheckInterval = config.getControllerHealthCheckIntervalSeconds();
        
        assertTrue(discoveryTimeout > 0, "Discovery timeout should be positive");
        assertTrue(leaderTimeout > 0, "Leader discovery timeout should be positive");
        assertTrue(healthCheckInterval > 0, "Health check interval should be positive");
    }

    @Test
    @DisplayName("Should handle telemetry enabled flag")
    void shouldHandleTelemetryEnabledFlag() {
        ApiConfig config = ApiConfig.get();
        
        // Just verify it returns a boolean without throwing
        boolean enabled = config.isTelemetryEnabled();
        
        // Default should be true
        assertTrue(enabled, "Telemetry should be enabled by default");
    }

    @Test
    @DisplayName("Should return valid OTLP endpoint")
    void shouldReturnValidOtlpEndpoint() {
        ApiConfig config = ApiConfig.get();
        
        String endpoint = config.getOtlpEndpoint();
        
        assertNotNull(endpoint, "OTLP endpoint should not be null");
        assertTrue(endpoint.startsWith("http://") || endpoint.startsWith("https://"),
            "OTLP endpoint should be a valid URL");
    }

    @Test
    @DisplayName("Should return valid CLI default URL")
    void shouldReturnValidCliDefaultUrl() {
        ApiConfig config = ApiConfig.get();
        
        String url = config.getCliDefaultUrl();
        
        assertNotNull(url, "CLI default URL should not be null");
        assertTrue(url.startsWith("http://") || url.startsWith("https://"),
            "CLI default URL should be a valid URL");
    }

    @Test
    @DisplayName("Should return valid API version")
    void shouldReturnValidApiVersion() {
        ApiConfig config = ApiConfig.get();
        
        String version = config.getApiVersion();
        
        assertNotNull(version, "API version should not be null");
        assertFalse(version.isEmpty(), "API version should not be empty");
    }
}
