/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package dev.mars.quorus.agent.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the isolated, layered agent configuration contract. */
class AgentConfigTest {

    @Test
    @DisplayName("Configuration instances remain isolated")
    void configurationInstancesRemainIsolated() {
        AgentConfig first = configWith("quorus.agent.id", "agent-one");
        AgentConfig second = configWith("quorus.agent.id", "agent-two");

        assertEquals("agent-one", first.getAgentId());
        assertEquals("agent-two", second.getAgentId());
        assertNotSame(first, second);
    }

    @Test
    @DisplayName("Explicit properties override packaged configuration")
    void explicitPropertiesOverridePackagedConfiguration() {
        AgentConfig config = configWith("quorus.agent.telemetry.prometheus.port", "19465");
        assertEquals(19465, config.getPrometheusPort());
    }

    @Test
    @DisplayName("NFS mount root is an explicit per-agent setting")
    void nfsMountRootIsExplicitConfiguration() {
        Properties overrides = new Properties();
        overrides.setProperty("quorus.agent.nfs.mount-root", "D:/quorus/nfs");
        overrides.setProperty("quorus.agent.nfs.encrypted-authenticated-mount", "true");
        overrides.setProperty("quorus.agent.smb.encrypted-authenticated-mount", "true");
        AgentConfig config = new AgentConfig("test", overrides);

        assertEquals("D:/quorus/nfs", config.getNfsMountRoot());
        assertTrue(config.isNfsMountSecurityVerified());
        assertTrue(config.isSmbMountSecurityVerified());
    }

    @Test
    @DisplayName("JVM system properties are not a configuration channel")
    void systemPropertiesAreNotAConfigurationChannel() {
        String key = "quorus.agent.id";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "ambient-agent");
            assertEquals("explicit-agent", configWith(key, "explicit-agent").getAgentId());
        } finally {
            if (previous == null) System.clearProperty(key);
            else System.setProperty(key, previous);
        }
    }

    @Test
    void packagedDefaultsAreTypedAndValid() {
        AgentConfig config = testConfig();

        assertNotNull(config.getAgentId());
        assertFalse(config.getAgentId().isEmpty());
        assertTrue(config.getControllerUrl().startsWith("http"));
        assertTrue(config.getAgentPort() > 0 && config.getAgentPort() <= 65535);
        assertTrue(config.getMaxConcurrentTransfers() > 0);
        assertTrue(config.getHeartbeatIntervalMs() > 0);
        assertTrue(config.getJobPollingInitialDelayMs() >= 0);
        assertTrue(config.getJobPollingIntervalMs() > 0);
        assertTrue(config.getForeignAssignmentMismatchThreshold() > 0);
        assertTrue(config.getPrometheusPort() > 0 && config.getPrometheusPort() <= 65535);
        assertTrue(config.getOtlpEndpoint().startsWith("http"));
        assertFalse(config.getRegion().isEmpty());
        assertFalse(config.getDatacenter().isEmpty());
        assertFalse(config.getSupportedProtocols().isEmpty());
        assertTrue(config.getVersion().matches("\\d+\\.\\d+\\.\\d+.*"));
        assertTrue(config.isTelemetryEnabled());
    }

    @Test
    void malformedNumericOverridesUseDeclaredFallbacks() {
        assertEquals(8080, configWith("quorus.agent.port", "invalid").getAgentPort());
        assertEquals(10_000L,
                configWith("quorus.agent.jobs.polling.interval-ms", "invalid").getJobPollingIntervalMs());
    }

    @Test
    @DisplayName("Typed agent configuration validates its source instance")
    void typedConfigurationRejectsInvalidSource() {
        Properties overrides = new Properties();
        overrides.setProperty("quorus.agent.id", "validation-agent");
        overrides.setProperty("quorus.agent.tenant.id", "validation-tenant");
        overrides.setProperty("quorus.agent.port", "70000");
        overrides.setProperty("quorus.agent.security.profile", "development");
        overrides.setProperty("quorus.agent.security.allow-insecure", "true");
        overrides.setProperty("quorus.agent.tls.enabled", "false");

        AgentConfig source = new AgentConfig("test", overrides);

        assertThrows(IllegalStateException.class, () -> AgentConfiguration.from(source));
    }

    private static AgentConfig testConfig() {
        return new AgentConfig("test", new Properties());
    }

    private static AgentConfig configWith(String key, String value) {
        Properties overrides = new Properties();
        overrides.setProperty(key, value);
        return new AgentConfig("test", overrides);
    }
}
