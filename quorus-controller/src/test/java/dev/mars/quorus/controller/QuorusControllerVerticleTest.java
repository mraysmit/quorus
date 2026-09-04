/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import dev.mars.quorus.controller.config.AppConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the real controller bootstrap and graceful shutdown boundary. */
@ExtendWith(VertxExtension.class)
class QuorusControllerVerticleTest {

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path tempDir;

    @Test
    void deploysCompleteControllerStackAndUndeploysGracefully(Vertx vertx, VertxTestContext context) {
        Properties properties = new Properties();
        properties.setProperty("quorus.security.profile", "development");
        properties.setProperty("quorus.security.enabled", "false");
        properties.setProperty("quorus.security.allow-insecure", "true");
        properties.setProperty("quorus.security.http.tls.enabled", "false");
        properties.setProperty("quorus.security.raft.tls.enabled", "false");
        properties.setProperty("quorus.raft.storage.type", "raftlog");
        properties.setProperty("quorus.raft.storage.path", tempDir.resolve("controller").toString());
        properties.setProperty("quorus.http.port", "0");
        properties.setProperty("quorus.raft.port", "0");
        properties.setProperty("quorus.cluster.nodes", "test-node=localhost:0");
        properties.setProperty("quorus.raft.election-timeout-ms", "50");
        properties.setProperty("quorus.raft.heartbeat-interval-ms", "20");
        AppConfig config = new AppConfig("test", properties);

        vertx.deployVerticle(new QuorusControllerVerticle(config))
                .compose(deploymentId -> {
                    context.verify(() -> {
                        assertNotNull(deploymentId);
                        assertFalse(deploymentId.isBlank());
                    });
                    return vertx.undeploy(deploymentId);
                })
                .onSuccess(ignored -> context.completeNow())
                .onFailure(context::failNow);
    }

    @Test
    void parallelDeploymentsUseTheirOwnPortsAndSettings(Vertx vertx, VertxTestContext context) throws IOException {
        int[] ports = availablePorts(4);
        AppConfig firstConfig = controllerConfig("parallel-node-a", ports[0], ports[1], 1_024L);
        AppConfig secondConfig = controllerConfig("parallel-node-b", ports[2], ports[3], 2_048L);

        var firstDeployment = vertx.deployVerticle(new QuorusControllerVerticle(firstConfig));
        var secondDeployment = vertx.deployVerticle(new QuorusControllerVerticle(secondConfig));

        io.vertx.core.Future.all(firstDeployment, secondDeployment)
                .compose(ignored -> {
                    context.verify(() -> {
                        assertNotEquals(firstDeployment.result(), secondDeployment.result());
                        assertNotEquals(firstConfig.getHttpPort(), secondConfig.getHttpPort());
                        assertNotEquals(firstConfig.getRaftPort(), secondConfig.getRaftPort());
                        assertEquals(1_024L, firstConfig.getHttpMaxBodyBytes());
                        assertEquals(2_048L, secondConfig.getHttpMaxBodyBytes());
                    });
                    return io.vertx.core.Future.all(
                            vertx.undeploy(firstDeployment.result()),
                            vertx.undeploy(secondDeployment.result())).mapEmpty();
                })
                .onSuccess(ignored -> context.completeNow())
                .onFailure(context::failNow);
    }

    @Test
    void failureInsideAsynchronousStartupFailsDeploymentInsteadOfHanging(Vertx vertx, VertxTestContext context)
            throws IOException {
        int[] ports = availablePorts(2);
        Properties properties = controllerProperties("failing-node", ports[0], ports[1], 1_024L);
        // Rejected by JobAssignmentHandler, which is only built after the Raft node has started asynchronously.
        properties.setProperty("quorus.jobs.attempt.lease-duration-ms", "0");
        AppConfig config = new AppConfig("test", properties);

        vertx.deployVerticle(new QuorusControllerVerticle(config))
                .onSuccess(deploymentId -> context.failNow(
                        new AssertionError("deployment must fail for a non-positive attempt lease")))
                .onFailure(cause -> {
                    context.verify(() -> assertTrue(
                            String.valueOf(cause.getMessage()).contains("attemptLeaseDurationMs"),
                            "unexpected startup failure: " + cause));
                    context.completeNow();
                });
    }

    private AppConfig controllerConfig(String nodeId, int httpPort, int raftPort, long maxBodyBytes) {
        return new AppConfig("test", controllerProperties(nodeId, httpPort, raftPort, maxBodyBytes));
    }

    private Properties controllerProperties(String nodeId, int httpPort, int raftPort, long maxBodyBytes) {
        Properties properties = new Properties();
        properties.setProperty("quorus.node.id", nodeId);
        properties.setProperty("quorus.security.profile", "development");
        properties.setProperty("quorus.security.enabled", "false");
        properties.setProperty("quorus.security.allow-insecure", "true");
        properties.setProperty("quorus.security.http.tls.enabled", "false");
        properties.setProperty("quorus.security.raft.tls.enabled", "false");
        properties.setProperty("quorus.raft.storage.type", "raftlog");
        properties.setProperty("quorus.raft.storage.path", tempDir.resolve(nodeId).toString());
        properties.setProperty("quorus.http.port", Integer.toString(httpPort));
        properties.setProperty("quorus.http.max-body-bytes", Long.toString(maxBodyBytes));
        properties.setProperty("quorus.raft.port", Integer.toString(raftPort));
        properties.setProperty("quorus.cluster.nodes", nodeId + "=localhost:" + raftPort);
        properties.setProperty("quorus.raft.election-timeout-ms", "50");
        properties.setProperty("quorus.raft.heartbeat-interval-ms", "20");
        return properties;
    }

    private static int[] availablePorts(int count) throws IOException {
        ServerSocket[] reservations = new ServerSocket[count];
        int[] ports = new int[count];
        try {
            for (int index = 0; index < count; index++) {
                reservations[index] = new ServerSocket(0);
                ports[index] = reservations[index].getLocalPort();
            }
            return ports;
        } finally {
            for (ServerSocket reservation : reservations) {
                if (reservation != null) {
                    reservation.close();
                }
            }
        }
    }
}
