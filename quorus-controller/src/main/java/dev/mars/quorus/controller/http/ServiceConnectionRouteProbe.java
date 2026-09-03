/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.http;

import dev.mars.quorus.connection.ConnectionPolicyEnforcer;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

/** Active TCP route probe bound to the controller-approved DNS result. */
public final class ServiceConnectionRouteProbe {
    private ServiceConnectionRouteProbe() { }

    public static Result probe(ConnectionPolicyEnforcer.ConnectionAuthorization authorization,
                               Duration timeout) throws Exception {
        if (authorization.resolvedAddresses().isEmpty()) {
            throw new IllegalArgumentException("Authorization has no approved addresses");
        }
        int port = authorization.endpoint().getPort();
        if (port < 1) throw new IllegalArgumentException("Authorization endpoint has no port");
        long started = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(authorization.resolvedAddresses().getFirst(), port),
                    Math.toIntExact(timeout.toMillis()));
            String connected = socket.getInetAddress().getHostAddress();
            if (!authorization.resolvedAddresses().contains(connected)) {
                throw new SecurityException("Q-EGRESS-SOCKET-BIND: route probe connected to an unapproved address");
            }
        }
        return new Result("PASS", Duration.ofNanos(System.nanoTime() - started).toMillis());
    }

    public record Result(String status, long latencyMillis) { }
}
