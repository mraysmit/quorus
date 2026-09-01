/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentTransportSecurityTest {
    @Test
    void productionAgentRejectsPlaintextController() {
        AgentConfiguration.Builder builder = new AgentConfiguration.Builder()
                .agentId("agent-1")
                .tenantId("tenant-a")
                .controllerUrl("http://controller:8080/api/v1")
                .securityProfile("production")
                .controllerTlsEnabled(false)
                .allowInsecure(false);
        assertThrows(IllegalArgumentException.class, builder::build);
    }
}
