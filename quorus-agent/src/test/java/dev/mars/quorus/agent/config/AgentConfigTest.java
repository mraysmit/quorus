/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package dev.mars.quorus.agent.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
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
        assertTrue(config.isTelemetryEnabled());
    }

    /** Register item CFG-03: a malformed number stops the agent instead of falling back to the default. */
    @Test
    @DisplayName("Typed agent configuration rejects a malformed number")
    void typedConfigurationRejectsAMalformedNumber() {
        for (String key : new String[]{"quorus.agent.port", "quorus.agent.jobs.polling.interval-ms"}) {
            Properties overrides = developmentOverrides();
            overrides.setProperty(key, "invalid");
            AgentConfig source = new AgentConfig("test", overrides);

            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> AgentConfiguration.from(source), key);
            assertTrue(error.getMessage().contains(key) && error.getMessage().contains("'invalid'"),
                    error.getMessage());
        }
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

    /** Register item CFG-06: the agent's typed configuration refuses a boolean other than true or false. */
    @Test
    @DisplayName("Typed agent configuration rejects a non-boolean value")
    void typedConfigurationRejectsANonBooleanValue() {
        Properties overrides = developmentOverrides();
        overrides.setProperty("quorus.agent.telemetry.enabled", "yes");

        AgentConfig source = new AgentConfig("test", overrides);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AgentConfiguration.from(source));
        assertTrue(error.getMessage().contains("quorus.agent.telemetry.enabled"), error.getMessage());
    }

    @Test
    @DisplayName("Documented QUORUS_AGENT_* names win over legacy unprefixed names")
    void documentedEnvironmentNamesWinOverLegacyNames() {
        AgentConfig config = new AgentConfig("test", new Properties(), Map.of(
                "AGENT_ID", "legacy-agent",
                "QUORUS_AGENT_ID", "documented-agent",
                "AGENT_REGION", "legacy-region",
                "QUORUS_AGENT_REGION", "documented-region",
                "MAX_CONCURRENT_TRANSFERS", "2",
                "QUORUS_AGENT_TRANSFERS_MAX_CONCURRENT", "7"));

        assertEquals("documented-agent", config.getAgentId());
        assertEquals("documented-region", config.getRegion());
        assertEquals(7, config.getMaxConcurrentTransfers());
    }

    @Test
    @DisplayName("Legacy unprefixed names still apply when no documented name is set")
    void legacyEnvironmentNamesRemainAFallback() {
        AgentConfig config = new AgentConfig("test", new Properties(), Map.of(
                "AGENT_ID", "legacy-agent",
                "AGENT_TENANT_ID", "legacy-tenant",
                "HEARTBEAT_INTERVAL", "15000"));

        assertEquals("legacy-agent", config.getAgentId());
        assertEquals("legacy-tenant", config.getTenantId());
        assertEquals(15_000L, config.getHeartbeatIntervalMs());
    }

    @Test
    @DisplayName("Explicit overrides beat both environment names")
    void explicitOverridesBeatEnvironmentNames() {
        Properties overrides = new Properties();
        overrides.setProperty("quorus.agent.id", "explicit-agent");
        AgentConfig config = new AgentConfig("test", overrides,
                Map.of("AGENT_ID", "legacy-agent", "QUORUS_AGENT_ID", "documented-agent"));

        assertEquals("explicit-agent", config.getAgentId());
    }

    @Test
    @DisplayName("Environment variables reach keys that no properties resource declares")
    void environmentReachesKeysAbsentFromResources() {
        AgentConfig config = new AgentConfig("test", new Properties(),
                Map.of("QUORUS_AGENT_UNDECLARED_SETTING_MS", "1234"));

        assertEquals(1234L, config.getLong("quorus.agent.undeclared.setting-ms", 0L));
    }

    /**
     * Register item CFG-02: each accessor's code default equals the value the agent ships with, so a
     * configuration that leaves a key unset (a blank override, or no packaged resource) runs with the
     * production value. Blanking every packaged key makes each accessor fall back to its code default.
     */
    @Test
    @DisplayName("Accessor defaults equal the packaged agent configuration")
    void accessorDefaultsEqualPackagedValues() throws java.io.IOException {
        Properties packaged = new Properties();
        try (var input = AgentConfig.class.getClassLoader().getResourceAsStream("quorus-agent.properties")) {
            assertNotNull(input, "quorus-agent.properties must be on the classpath");
            packaged.load(input);
        }
        Properties blanked = new Properties();
        packaged.stringPropertyNames().forEach(key -> blanked.setProperty(key, ""));

        AgentConfig shipped = new AgentConfig("default", new Properties(), Map.of());
        AgentConfig defaults = new AgentConfig("default", blanked, Map.of());

        Map<String, java.util.function.Function<AgentConfig, Object>> accessors = new java.util.LinkedHashMap<>();
        accessors.put("controllerUrl", AgentConfig::getControllerUrl);
        accessors.put("agentPort", AgentConfig::getAgentPort);
        accessors.put("region", AgentConfig::getRegion);
        accessors.put("datacenter", AgentConfig::getDatacenter);
        accessors.put("maxConcurrentTransfers", AgentConfig::getMaxConcurrentTransfers);
        accessors.put("supportedProtocols", AgentConfig::getSupportedProtocols);
        accessors.put("heartbeatIntervalMs", AgentConfig::getHeartbeatIntervalMs);
        accessors.put("jobPollingInitialDelayMs", AgentConfig::getJobPollingInitialDelayMs);
        accessors.put("jobPollingIntervalMs", AgentConfig::getJobPollingIntervalMs);
        accessors.put("progressReportIntervalMs", AgentConfig::getProgressReportIntervalMs);
        accessors.put("registrationRetryIntervalMs", AgentConfig::getRegistrationRetryIntervalMs);
        accessors.put("foreignAssignmentMismatchThreshold", AgentConfig::getForeignAssignmentMismatchThreshold);
        accessors.put("telemetryEnabled", AgentConfig::isTelemetryEnabled);
        accessors.put("prometheusPort", AgentConfig::getPrometheusPort);
        accessors.put("otlpEndpoint", AgentConfig::getOtlpEndpoint);
        accessors.put("httpConnectionTimeoutMs", AgentConfig::getHttpConnectionTimeoutMs);
        accessors.put("httpIdleTimeoutMs", AgentConfig::getHttpIdleTimeoutMs);
        accessors.put("securityProfile", AgentConfig::getSecurityProfile);
        accessors.put("allowInsecure", AgentConfig::isAllowInsecure);
        accessors.put("controllerTlsEnabled", AgentConfig::isControllerTlsEnabled);
        accessors.put("tlsCertificatePath", AgentConfig::getTlsCertificatePath);
        accessors.put("tlsPrivateKeyPath", AgentConfig::getTlsPrivateKeyPath);
        accessors.put("tlsTrustBundlePath", AgentConfig::getTlsTrustBundlePath);
        accessors.put("uploadRoot", AgentConfig::getUploadRoot);
        accessors.put("downloadRoot", AgentConfig::getDownloadRoot);
        accessors.put("nfsMountRoot", AgentConfig::getNfsMountRoot);
        accessors.put("nfsMountSecurityVerified", AgentConfig::isNfsMountSecurityVerified);
        accessors.put("smbMountSecurityVerified", AgentConfig::isSmbMountSecurityVerified);
        accessors.put("agentPool", AgentConfig::getAgentPool);
        accessors.put("networkZone", AgentConfig::getNetworkZone);
        accessors.put("vaultAddress", AgentConfig::getVaultAddress);
        accessors.put("vaultToken", AgentConfig::getVaultToken);

        Map<String, String> mismatches = new java.util.TreeMap<>();
        accessors.forEach((name, accessor) -> {
            Object packagedValue = accessor.apply(shipped);
            Object defaultValue = accessor.apply(defaults);
            if (!packagedValue.equals(defaultValue)) {
                mismatches.put(name, "packaged " + packagedValue + ", accessor default " + defaultValue);
            }
        });
        assertEquals(Map.of(), mismatches);
    }

    /** A source that passes agent validation without TLS material, so a test isolates one invalid value. */
    private static Properties developmentOverrides() {
        Properties overrides = new Properties();
        overrides.setProperty("quorus.agent.id", "validation-agent");
        overrides.setProperty("quorus.agent.tenant.id", "validation-tenant");
        overrides.setProperty("quorus.agent.security.profile", "development");
        overrides.setProperty("quorus.agent.security.allow-insecure", "true");
        overrides.setProperty("quorus.agent.tls.enabled", "false");
        return overrides;
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
