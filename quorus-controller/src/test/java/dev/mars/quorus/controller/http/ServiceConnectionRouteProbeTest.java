/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.http;

import dev.mars.quorus.connection.ConnectionPolicyEnforcer;
import dev.mars.quorus.connection.DefaultPorts;
import dev.mars.quorus.connection.ServiceConnection;
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

    /**
     * Register item ENG-07: the default-port table maps SFTP to an ephemeral port, so the portless case is
     * tested end to end without binding the privileged port 22. The probe's connection waits in the
     * listener's backlog, and accepting it afterwards proves the probe reached this listener: a host
     * running sshd on port 22 would otherwise let a probe that ignores the table pass.
     */
    @Test
    void usesTheProtocolDefaultWhenTheApprovedEndpointOmitsItsPort() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            listener.setSoTimeout(2_000);
            DefaultPorts ports = DefaultPorts.wellKnown()
                    .with(ServiceConnection.Protocol.SFTP, listener.getLocalPort());
            var authorization = new ConnectionPolicyEnforcer.ConnectionAuthorization(
                    "connection-default-port", "tenant-1",
                    URI.create("sftp://localhost/outbound/file.dat"),
                    List.of(InetAddress.getLoopbackAddress().getHostAddress()), 1, "digest", Instant.now());

            ServiceConnectionRouteProbe.Result result = ServiceConnectionRouteProbe.probe(
                    authorization, Duration.ofSeconds(2), ports);

            assertEquals("PASS", result.status());
            try (var accepted = listener.accept()) {
                assertTrue(accepted.isConnected(), "the probe must connect to the table's SFTP port");
            }
        }
    }

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
