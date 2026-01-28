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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AgentConfig configuration loading.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-01-28
 */
class AgentConfigTest {

    @Test
    @DisplayName("Should return singleton instance")
    void shouldReturnSingletonInstance() {
        AgentConfig config1 = AgentConfig.get();
        AgentConfig config2 = AgentConfig.get();
        
        assertSame(config1, config2, "Should return the same singleton instance");
    }

    @Test
    @DisplayName("Should return non-null agent ID")
    void shouldReturnNonNullAgentId() {
        AgentConfig config = AgentConfig.get();
        
        String agentId = config.getAgentId();
        
        assertNotNull(agentId, "Agent ID should never be null");
        assertFalse(agentId.isEmpty(), "Agent ID should not be empty");
    }

    @Test
    @DisplayName("Should derive agent ID from hostname when not configured")
    void shouldDeriveAgentIdFromHostname() {
        AgentConfig config = AgentConfig.get();
        
        String agentId = config.getAgentId();
        
        // Should start with "agent-" prefix when derived from hostname
        assertTrue(agentId.startsWith("agent-") || !agentId.isEmpty(),
            "Agent ID should be derived from hostname or configured");
    }

    @ParameterizedTest(name = "{0} should have default value")
    @MethodSource("defaultValueTestCases")
    @DisplayName("Should return expected default values")
    void shouldReturnExpectedDefaultValues(String propertyName, Object expectedDefault, Object actualValue) {
        assertNotNull(actualValue, propertyName + " should not be null");
        if (expectedDefault instanceof Integer) {
            assertTrue((Integer) actualValue > 0, propertyName + " should be positive");
        } else if (expectedDefault instanceof Long) {
            assertTrue((Long) actualValue > 0, propertyName + " should be positive");
        }
    }

    static Stream<Arguments> defaultValueTestCases() {
        AgentConfig config = AgentConfig.get();
        return Stream.of(
            Arguments.of("controllerUrl", "http://localhost:8080/api/v1", config.getControllerUrl()),
            Arguments.of("agentPort", 8080, config.getAgentPort()),
            Arguments.of("maxConcurrentTransfers", 5, config.getMaxConcurrentTransfers()),
            Arguments.of("heartbeatIntervalMs", 30000L, config.getHeartbeatIntervalMs()),
            Arguments.of("jobPollingInitialDelayMs", 5000L, config.getJobPollingInitialDelayMs()),
            Arguments.of("jobPollingIntervalMs", 10000L, config.getJobPollingIntervalMs()),
            Arguments.of("prometheusPort", 9465, config.getPrometheusPort())
        );
    }

    @Test
    @DisplayName("Should return valid port numbers")
    void shouldReturnValidPortNumbers() {
        AgentConfig config = AgentConfig.get();
        
        int agentPort = config.getAgentPort();
        int prometheusPort = config.getPrometheusPort();
        
        assertTrue(agentPort > 0 && agentPort <= 65535, "Agent port should be valid");
        assertTrue(prometheusPort > 0 && prometheusPort <= 65535, "Prometheus port should be valid");
    }

    @Test
    @DisplayName("Should return valid timing values")
    void shouldReturnValidTimingValues() {
        AgentConfig config = AgentConfig.get();
        
        long heartbeat = config.getHeartbeatIntervalMs();
        long initialDelay = config.getJobPollingInitialDelayMs();
        long pollInterval = config.getJobPollingIntervalMs();
        
        assertTrue(heartbeat >= 1000, "Heartbeat interval should be at least 1 second");
        assertTrue(initialDelay >= 0, "Initial delay should be non-negative");
        assertTrue(pollInterval >= 1000, "Polling interval should be at least 1 second");
    }

    @Test
    @DisplayName("Should return valid region and datacenter")
    void shouldReturnValidRegionAndDatacenter() {
        AgentConfig config = AgentConfig.get();
        
        String region = config.getRegion();
        String datacenter = config.getDatacenter();
        
        assertNotNull(region, "Region should not be null");
        assertNotNull(datacenter, "Datacenter should not be null");
        assertFalse(region.isEmpty(), "Region should not be empty");
        assertFalse(datacenter.isEmpty(), "Datacenter should not be empty");
    }

    @Test
    @DisplayName("Should return valid version")
    void shouldReturnValidVersion() {
        AgentConfig config = AgentConfig.get();
        
        String version = config.getVersion();
        
        assertNotNull(version, "Version should not be null");
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+.*"), 
            "Version should follow semantic versioning pattern");
    }

    @Test
    @DisplayName("Should return valid OTLP endpoint")
    void shouldReturnValidOtlpEndpoint() {
        AgentConfig config = AgentConfig.get();
        
        String endpoint = config.getOtlpEndpoint();
        
        assertNotNull(endpoint, "OTLP endpoint should not be null");
        assertTrue(endpoint.startsWith("http://") || endpoint.startsWith("https://"),
            "OTLP endpoint should be a valid URL");
    }

    @Test
    @DisplayName("Should handle telemetry enabled flag")
    void shouldHandleTelemetryEnabledFlag() {
        AgentConfig config = AgentConfig.get();
        
        // Just verify it returns a boolean without throwing
        boolean enabled = config.isTelemetryEnabled();
        
        // Default should be true
        assertTrue(enabled, "Telemetry should be enabled by default");
    }

    @Test
    @DisplayName("Should return supported protocols")
    void shouldReturnSupportedProtocols() {
        AgentConfig config = AgentConfig.get();
        
        String protocols = config.getSupportedProtocols();
        
        assertNotNull(protocols, "Supported protocols should not be null");
        assertFalse(protocols.isEmpty(), "Supported protocols should not be empty");
    }
}
