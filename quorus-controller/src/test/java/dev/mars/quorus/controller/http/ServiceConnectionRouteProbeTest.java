/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.http;

import dev.mars.quorus.connection.ConnectionPolicyEnforcer;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceConnectionRouteProbeTest {

    @Test
    void connectsOnlyToTheControllerApprovedAddress() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread.startVirtualThread(() -> {
                try (var ignored = listener.accept()) { }
                catch (Exception ignored) { }
            });
            var authorization = new ConnectionPolicyEnforcer.ConnectionAuthorization(
                    "connection-1", "tenant-1",
                    URI.create("sftp://localhost:" + listener.getLocalPort() + "/outbound/file.dat"),
                    List.of(InetAddress.getLoopbackAddress().getHostAddress()), 1, "digest", Instant.now());

            ServiceConnectionRouteProbe.Result result = ServiceConnectionRouteProbe.probe(
                    authorization, Duration.ofSeconds(2));

            assertEquals("PASS", result.status());
            assertTrue(result.latencyMillis() >= 0);
        }
    }
}
