/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Exercises the real controller bootstrap and graceful shutdown boundary. */
@ExtendWith(VertxExtension.class)
class QuorusControllerVerticleTest {

    private final Map<String, String> previousValues = new LinkedHashMap<>();

    @BeforeEach
    void configureDevelopmentRuntime() {
        setProperty("quorus.security.profile", "development");
        setProperty("quorus.security.enabled", "false");
        setProperty("quorus.security.allow-insecure", "true");
        setProperty("quorus.security.http.tls.enabled", "false");
        setProperty("quorus.security.raft.tls.enabled", "false");
        setProperty("quorus.raft.storage.type", "memory");
        setProperty("quorus.http.port", "0");
        setProperty("quorus.raft.port", "0");
        setProperty("quorus.cluster.nodes", "test-node=localhost:0");
        setProperty("quorus.raft.election-timeout-ms", "50");
        setProperty("quorus.raft.heartbeat-interval-ms", "20");
    }

    @AfterEach
    void restoreConfiguration() {
        previousValues.forEach((key, value) -> {
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        });
    }

    @Test
    void deploysCompleteControllerStackAndUndeploysGracefully(Vertx vertx, VertxTestContext context) {
        vertx.deployVerticle(new QuorusControllerVerticle())
                .compose(deploymentId -> {
                    context.verify(() -> {
                        assertNotNull(deploymentId);
                        assertFalse(deploymentId.isBlank());
                    });
                    return vertx.undeploy(deploymentId);
                })
                .onComplete(context.succeedingThenComplete());
    }

    private void setProperty(String key, String value) {
        previousValues.putIfAbsent(key, System.getProperty(key));
        System.setProperty(key, value);
    }
}
