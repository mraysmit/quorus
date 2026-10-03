/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.http;

import dev.mars.quorus.config.PomVersion;
import dev.mars.quorus.controller.config.ControllerTestConfig;
import dev.mars.quorus.controller.raft.InMemoryTransportSimulator;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftNodeMode;
import dev.mars.quorus.controller.state.QuorusStateStore;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.Set;

import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static dev.mars.quorus.testing.TestFutureUtils.eventually;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Register decision DR-Q5: the controller reports one product version, the root pom version, and it
 * is not a setting.
 */
@ExtendWith(VertxExtension.class)
class ProductVersionReportingTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String NODE_ID = "product-version-node";

    private static Vertx vertx;
    private static RaftNode raftNode;
    private static HttpApiServer httpServer;
    private static WebClient webClient;
    private static String pomVersion;

    @BeforeAll
    static void setUp() throws Exception {
        pomVersion = PomVersion.of(Path.of("..", "pom.xml"));
        vertx = Vertx.vertx();
        QuorusStateStore stateStore = new QuorusStateStore();
        raftNode = RaftNode.builder().vertx(vertx).nodeId(NODE_ID).clusterNodes(Set.of(NODE_ID))
                .transport(new InMemoryTransportSimulator(NODE_ID)).stateMachine(stateStore)
                .mode(RaftNodeMode.volatileMode()).electionTimeout(200).heartbeatInterval(50).build();
        awaitSuccess(raftNode.start(), TIMEOUT);
        awaitSuccess(eventually(vertx, raftNode::isLeader, TIMEOUT), TIMEOUT.plusSeconds(1));
        // A leftover version setting must change nothing.
        Properties leftover = new Properties();
        leftover.setProperty("quorus.version", "9.9.9-setting");
        httpServer = new HttpApiServer(vertx, 0, raftNode, stateStore, ControllerTestConfig.create(leftover));
        awaitSuccess(httpServer.start(), TIMEOUT);
        webClient = WebClient.create(vertx);
    }

    @AfterAll
    static void tearDown() {
        if (webClient != null) webClient.close();
        if (httpServer != null) awaitSuccess(httpServer.stop(), TIMEOUT);
        if (raftNode != null) awaitSuccess(raftNode.stop(), TIMEOUT);
        if (vertx != null) awaitSuccess(vertx.close(), TIMEOUT);
        InMemoryTransportSimulator.clearAllTransports();
    }

    @Test
    void apiInfoReportsThePomVersion() {
        var response = awaitSuccess(webClient.get(httpServer.actualPort(), "localhost", "/api/v1/info").send(),
                TIMEOUT);

        assertEquals(pomVersion, response.bodyAsJsonObject().getJsonObject("api").getString("quorusVersion"));
    }

    @Test
    void healthReportsThePomVersion() {
        var response = awaitSuccess(webClient.get(httpServer.actualPort(), "localhost", "/health").send(), TIMEOUT);

        assertEquals(pomVersion, response.bodyAsJsonObject().getString("version"));
    }

    @Test
    void thePackagedConfigurationDeclaresNoVersionSetting() throws Exception {
        Properties packaged = new Properties();
        // The file that ships, not the test classpath's copy.
        try (InputStream in = Files.newInputStream(
                Path.of("src", "main", "resources", "quorus-controller.properties"))) {
            packaged.load(in);
        }

        assertFalse(packaged.containsKey("quorus.version"), "the product version is not a setting");
    }
}
