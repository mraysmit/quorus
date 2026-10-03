/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Register item ENG-27: the agent is configured with every controller of its cluster. */
class AgentControllerUrlsTest {

    @Test
    void controllerUrlSettingIsACommaSeparatedListInOrder() {
        AgentConfiguration config = development()
                .controllerUrl("http://controller1:8080/api/v1, http://controller2:8080/api/v1/ ,"
                        + "http://controller3:8080/api/v1")
                .build();

        assertEquals(List.of("http://controller1:8080/api/v1", "http://controller2:8080/api/v1",
                "http://controller3:8080/api/v1"), config.getControllerUrls());
    }

    @Test
    void aSingleControllerUrlIsAListOfOne() {
        AgentConfiguration config = development().controllerUrl("http://controller1:8080/api/v1").build();

        assertEquals(List.of("http://controller1:8080/api/v1"), config.getControllerUrls());
    }

    @Test
    void everyControllerUrlMustBeHttpOrHttps() {
        for (String invalid : new String[]{"", " , ", "http://controller1:8080/api/v1,controller2:8080",
                "http://controller1:8080/api/v1,,http://controller2:8080/api/v1"}) {
            AgentConfiguration.Builder builder = development().controllerUrl(invalid);
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, builder::build, invalid);
            assertTrue(error.getMessage().contains("controller URL"), error.getMessage());
        }
    }

    @Test
    void aProductionAgentRejectsAListWithAPlaintextController(@TempDir Path directory) throws Exception {
        Path pem = Files.writeString(directory.resolve("material.pem"), "placeholder");
        AgentConfiguration.Builder builder = new AgentConfiguration.Builder()
                .agentId("agent-1").tenantId("tenant-a").securityProfile("production")
                .controllerTlsEnabled(true).allowInsecure(false)
                .tlsCertificatePath(pem.toString()).tlsPrivateKeyPath(pem.toString())
                .tlsTrustBundlePath(pem.toString())
                .controllerUrl("https://controller1:8443/api/v1,http://controller2:8080/api/v1");

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void startupValidationChecksEveryConfiguredControllerUrl() {
        Properties properties = new Properties();
        properties.setProperty("quorus.agent.controller.url", "https://controller1:8443/api/v1,controller2");
        AgentConfig config = new AgentConfig("default", properties, Map.of());

        IllegalStateException error = assertThrows(IllegalStateException.class, config::validate);
        assertTrue(error.getMessage().contains("controller2"), error.getMessage());
    }

    @Test
    void registrationRetryIntervalDefaultsToFiveSecondsAndMustBePositive() {
        assertEquals(5000, development().controllerUrl("http://controller1:8080/api/v1").build()
                .getRegistrationRetryIntervalMs());

        AgentConfiguration.Builder builder = development().controllerUrl("http://controller1:8080/api/v1")
                .registrationRetryIntervalMs(0);
        assertThrows(IllegalArgumentException.class, builder::build);

        Properties properties = new Properties();
        properties.setProperty("quorus.agent.registration.retry-interval-ms", "-1");
        AgentConfig config = new AgentConfig("default", properties, Map.of());
        IllegalStateException error = assertThrows(IllegalStateException.class, config::validate);
        assertTrue(error.getMessage().contains("Registration retry interval"), error.getMessage());
    }

    @Test
    void registrationRetryIntervalIsReadFromItsEnvironmentVariable() {
        AgentConfig config = new AgentConfig("default", new Properties(),
                Map.of("QUORUS_AGENT_REGISTRATION_RETRY_INTERVAL_MS", "750"));

        assertEquals(750, config.getRegistrationRetryIntervalMs());
    }

    private static AgentConfiguration.Builder development() {
        return new AgentConfiguration.Builder().agentId("agent-1").tenantId("tenant-a")
                .securityProfile("development").controllerTlsEnabled(false).allowInsecure(true);
    }
}
