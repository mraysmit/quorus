/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent;

import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.agent.testing.FakeController;
import dev.mars.quorus.agent.testing.FakeController.Reply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Register item ENG-27, the whole agent: bound first to a follower, it registers with the leader the
 * follower names, and its heartbeats, polls and deregistration go to the leader. When the leader
 * changes, the agent follows it.
 */
@Timeout(value = 60, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class AgentFollowsLeaderIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final String REGISTER = "/api/v1/agents/register";
    private static final String HEARTBEAT = "/api/v1/agents/heartbeat";
    private static final String JOBS = "/api/v1/agents/agent-a/jobs";
    private static final String DEREGISTER = "/api/v1/agents/agent-a";
    private static final String NOT_LEADER = "{\"code\":\"NOT_LEADER\",\"status\":503}";

    private FakeController first;
    private FakeController second;
    private QuorusAgent agent;

    @BeforeEach
    void startControllers() throws Exception {
        first = FakeController.start();
        second = FakeController.start();
    }

    @AfterEach
    void stop() throws Exception {
        if (agent != null) {
            agent.shutdown();
            agent.awaitShutdown();
        }
        first.close();
        second.close();
    }

    @Test
    void anAgentBoundToAFollowerRegistersAndWorksWithTheLeader() throws Exception {
        follower(first, second);
        leader(second);
        agent = new QuorusAgent(config());

        agent.start();
        second.awaitRequests("POST", HEARTBEAT, 2, WAIT);
        second.awaitRequests("GET", JOBS, 2, WAIT);
        agent.shutdown();
        assertTrue(agent.awaitShutdown(WAIT));

        assertEquals(1, first.requests().size(), "the follower is asked once, for the registration");
        assertEquals(1, second.requests("POST", REGISTER).size());
        assertEquals(1, second.requests("DELETE", DEREGISTER).size(), "deregistration goes to the leader");
    }

    @Test
    void anAgentFollowsALeaderChange() throws Exception {
        leader(first);
        follower(second, first);
        agent = new QuorusAgent(config());
        agent.start();
        first.awaitRequests("POST", HEARTBEAT, 1, WAIT);

        // Leadership moves to the second controller.
        follower(first, second);
        leader(second);

        second.awaitRequests("POST", HEARTBEAT, 2, WAIT);
        assertTrue(agent.isRunning());
    }

    private static void leader(FakeController controller) {
        controller.on("POST", REGISTER, Reply.json(201, "{\"success\":true}").always())
                .on("POST", HEARTBEAT, Reply.json(200, "{\"success\":true}").always())
                .on("GET", JOBS, Reply.json(200, "{\"pendingJobs\":[]}").always())
                .on("DELETE", DEREGISTER, Reply.status(204).always());
    }

    /** Refuses every write as the controller's leader guard does, and serves reads. */
    private static void follower(FakeController controller, FakeController leader) {
        Reply refusal = Reply.json(503, NOT_LEADER).header("X-Quorus-Leader", leader.url());
        controller.on("POST", "/api/v1/.*", refusal.always())
                .on("DELETE", "/api/v1/.*", refusal.always())
                .on("GET", JOBS, Reply.json(200, "{\"pendingJobs\":[]}").always());
    }

    private AgentConfiguration config() {
        return new AgentConfiguration.Builder()
                .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                .agentId("agent-a").tenantId("tenant")
                .controllerUrl(first.url() + "/api/v1," + second.url() + "/api/v1")
                .agentPort(0).heartbeatInterval(50L).jobPollingInitialDelayMs(1).jobPollingIntervalMs(20)
                .registrationRetryIntervalMs(20).build();
    }
}
