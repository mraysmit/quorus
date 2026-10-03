/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent.service;

import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.agent.testing.FakeController;
import dev.mars.quorus.agent.testing.FakeController.Reply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Register item ENG-27: an agent configured with every controller of its cluster reaches the leader.
 * Each controller here is a real HTTP server; a follower answers writes as the controller's leader
 * guard does (REST Spec §3.8).
 */
@Timeout(value = 30, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ControllerClientLeaderFollowingTest {

    private static final String REGISTER = "/api/v1/agents/register";
    private static final String NOT_LEADER = "{\"code\":\"NOT_LEADER\",\"status\":503}";

    private FakeController follower;
    private FakeController leader;

    @BeforeEach
    void startControllers() throws Exception {
        follower = FakeController.start();
        leader = FakeController.start().on("POST", REGISTER, Reply.json(201, "{\"success\":true}").always());
    }

    @AfterEach
    void stopControllers() {
        follower.close();
        leader.close();
    }

    @Test
    void aWriteRefusedByAFollowerIsSentToTheLeaderItNames() throws Exception {
        follower.on("POST", REGISTER, Reply.json(503, NOT_LEADER).header("X-Quorus-Leader", leader.url()).always());
        try (ControllerClient client = client(follower, leader)) {

            ControllerClient.Response response = client.postJson("/agents/register", "{\"agentId\":\"a\"}");

            assertEquals(201, response.status());
            assertEquals(1, follower.requests("POST", REGISTER).size());
            assertEquals("{\"agentId\":\"a\"}", leader.requests("POST", REGISTER).getFirst().body(),
                    "the leader receives the same request");
        }
    }

    @Test
    void laterRequestsGoStraightToTheLeader() throws Exception {
        follower.on("POST", REGISTER, Reply.json(503, NOT_LEADER).header("X-Quorus-Leader", leader.url()).always());
        leader.on("GET", "/api/v1/agents/a/jobs", Reply.json(200, "{}").always());
        try (ControllerClient client = client(follower, leader)) {
            client.postJson("/agents/register", "{}");

            assertEquals(201, client.postJson("/agents/register", "{}").status());
            assertEquals(200, client.get("/agents/a/jobs").status());

            assertEquals(1, follower.requests().size(), "the follower is asked only once");
            assertEquals(3, leader.requests().size());
        }
    }

    @Test
    void aHintNamingAnUnconfiguredAddressIsNeverContacted() throws Exception {
        try (FakeController unconfigured = FakeController.start()
                .on("POST", REGISTER, Reply.json(201, "{}").always())) {
            follower.on("POST", REGISTER,
                    Reply.json(503, NOT_LEADER).header("X-Quorus-Leader", unconfigured.url()).always());
            try (ControllerClient client = client(follower, leader)) {

                assertEquals(201, client.postJson("/agents/register", "{}").status());

                assertTrue(unconfigured.requests().isEmpty(),
                        "a hint selects among configured controllers; it cannot add one");
                assertEquals(1, leader.requests("POST", REGISTER).size(), "the next configured controller is tried");
            }
        }
    }

    @Test
    void aRefusalWithoutAHintMovesToTheNextConfiguredController() throws Exception {
        follower.on("POST", REGISTER, Reply.json(503, NOT_LEADER).always());
        try (ControllerClient client = client(follower, leader)) {

            assertEquals(201, client.postJson("/agents/register", "{}").status());

            assertEquals(1, follower.requests("POST", REGISTER).size());
            assertEquals(1, leader.requests("POST", REGISTER).size());
        }
    }

    @Test
    void aMalformedOrSelfNamingHintMovesToTheNextConfiguredController() throws Exception {
        for (String hint : new String[]{"not a url", "", follower.url()}) {
            follower.on("POST", REGISTER, Reply.json(503, NOT_LEADER).header("X-Quorus-Leader", hint).always());
            leader.clearRequests();
            try (ControllerClient client = client(follower, leader)) {

                assertEquals(201, client.postJson("/agents/register", "{}").status(), "hint: '" + hint + "'");

                assertEquals(1, leader.requests("POST", REGISTER).size());
            }
        }
    }

    @Test
    void aHintIsMatchedByOriginWhateverItsPathOrCase() throws Exception {
        follower.on("POST", REGISTER, Reply.json(503, NOT_LEADER)
                .header("X-Quorus-Leader", leader.url().toUpperCase().replace("HTTP", "http") + "/api/v1/").always());
        try (FakeController other = FakeController.start();
             ControllerClient client = client(follower, other, leader)) {

            assertEquals(201, client.postJson("/agents/register", "{}").status());

            assertTrue(other.requests().isEmpty(), "the hint names the third controller, so the second is skipped");
        }
    }

    @Test
    void onlyANotLeaderRefusalIsSentToAnotherController() throws Exception {
        for (String body : new String[]{"{\"code\":\"NO_LEADER\",\"status\":503}",
                "{\"code\":\"SERVICE_UNAVAILABLE\",\"status\":503}", "", "not json"}) {
            follower.on("POST", REGISTER, Reply.json(503, body).always());
            leader.clearRequests();
            try (ControllerClient client = client(follower, leader)) {

                assertEquals(503, client.postJson("/agents/register", "{}").status(), body);

                assertTrue(leader.requests().isEmpty(), () -> "must not be resent after: " + body);
            }
        }
    }

    @Test
    void controllersThatNameEachOtherAreEachAskedOnce() throws Exception {
        follower.on("POST", REGISTER, Reply.json(503, NOT_LEADER).header("X-Quorus-Leader", leader.url()).always());
        leader.on("POST", REGISTER, Reply.json(503, NOT_LEADER).header("X-Quorus-Leader", follower.url()).always());
        try (ControllerClient client = client(follower, leader)) {

            assertEquals(503, client.postJson("/agents/register", "{}").status());

            assertEquals(1, follower.requests("POST", REGISTER).size());
            assertEquals(1, leader.requests("POST", REGISTER).size());
        }
    }

    @Test
    void anAgentWithOneControllerReturnsItsRefusal() throws Exception {
        follower.on("POST", REGISTER, Reply.json(503, NOT_LEADER).header("X-Quorus-Leader", leader.url()).always());
        try (ControllerClient client = client(follower)) {

            assertEquals(503, client.postJson("/agents/register", "{}").status());

            assertEquals(1, follower.requests("POST", REGISTER).size());
            assertTrue(leader.requests().isEmpty());
        }
    }

    @Test
    void aTransportFailureIsNotResentButTheNextRequestUsesTheNextController() throws Exception {
        follower.on("POST", REGISTER, Reply.drop().always());
        try (ControllerClient client = client(follower, leader)) {

            assertThrows(IOException.class, () -> client.postJson("/agents/register", "{}"));
            assertTrue(leader.requests().isEmpty(),
                    "the dropped request may have been applied, so it is not sent again");

            assertEquals(201, client.postJson("/agents/register", "{}").status());
            assertEquals(1, follower.requests("POST", REGISTER).size());
        }
    }

    private static ControllerClient client(FakeController... controllers) {
        StringBuilder urls = new StringBuilder();
        for (FakeController controller : controllers) {
            urls.append(urls.isEmpty() ? "" : ",").append(controller.url()).append("/api/v1");
        }
        return ControllerClient.create(new AgentConfiguration.Builder()
                .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                .agentId("agent-a").tenantId("tenant").controllerUrl(urls.toString())
                .httpConnectionTimeout(1000).build());
    }
}
