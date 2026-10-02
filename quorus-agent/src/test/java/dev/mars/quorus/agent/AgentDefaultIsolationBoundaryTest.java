/* Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache-2.0. */
package dev.mars.quorus.agent;

import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.agent.testing.FakeController;
import dev.mars.quorus.agent.testing.FakeController.Reply;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = 30, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class AgentDefaultIsolationBoundaryTest {
    @Test
    void defaultAgentStopsAfterOneForeignAssignment() throws Exception {
        AtomicBoolean offered = new AtomicBoolean();
        try (FakeController controller = FakeController.start()
                .on("POST", "/api/v1/agents/register", Reply.json(200, "{\"status\":\"registered\"}").always())
                .on("DELETE", "/api/v1/agents/.+", Reply.status(204).always())
                .on("GET", "/api/v1/agents/.+/jobs", request -> Reply.json(200, "{\"pendingJobs\":["
                        + (offered.getAndSet(true) ? "" : "{\"assignmentId\":\"foreign:other\",\"jobId\":\"foreign\","
                        + "\"agentId\":\"other\",\"sourceUri\":\"https://example.test/file\","
                        + "\"destinationUri\":\"file:///unused\"}") + "]}"))) {
            var agent = new QuorusAgent(new AgentConfiguration.Builder()
                    .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                    .agentId("local").tenantId("tenant").agentPort(0).telemetryEnabled(false)
                    .controllerUrl(controller.url() + "/api/v1")
                    .jobPollingInitialDelayMs(1).jobPollingIntervalMs(20).build());
            try {
                agent.start();
                assertTrue(agent.awaitShutdown(Duration.ofSeconds(5)),
                        "Packaged default is fail-fast on the first foreign assignment");
                assertTrue(offered.get());
                assertFalse(agent.isRunning());
            } finally {
                agent.shutdown();
                agent.awaitShutdown();
            }
        }
    }
}
