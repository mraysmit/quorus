/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

import java.net.InetAddress;
import java.net.URI;
import java.util.Objects;

/** Converts governed endpoints into socket targets pinned to an approved DNS result. */
public final class PinnedEndpoint {
    private PinnedEndpoint() { }

    public static URI connectUri(URI authority, RuntimeCredential credential)
            throws ConnectionPolicyException {
        Objects.requireNonNull(authority, "authority");
        if (credential == null || credential.approvedResolvedAddresses().isEmpty()) return authority;
        String address = credential.approvedResolvedAddresses().getFirst();
        try {
            String socketHost = address.indexOf(':') >= 0 ? "[" + address + "]" : address;
            StringBuilder value = new StringBuilder(authority.getScheme()).append("://").append(socketHost);
            if (authority.getPort() >= 0) value.append(':').append(authority.getPort());
            if (authority.getRawPath() != null) value.append(authority.getRawPath());
            if (authority.getRawQuery() != null) value.append('?').append(authority.getRawQuery());
            if (authority.getRawFragment() != null) value.append('#').append(authority.getRawFragment());
            return URI.create(value.toString());
        } catch (IllegalArgumentException e) {
            throw new ConnectionPolicyException("Q-EGRESS-SOCKET-BIND", "Approved endpoint address is invalid");
        }
    }

    public static String virtualHost(URI authority) {
        // Vert.x uses this value for TLS peer verification, then adds the socket port to Host.
        return authority.getHost();
    }

    public static void requireApprovedAddress(InetAddress connectedAddress, RuntimeCredential credential)
            throws ConnectionPolicyException {
        if (credential == null || credential.approvedResolvedAddresses().isEmpty()) return;
        if (connectedAddress == null || !credential.approvedResolvedAddresses().contains(
                connectedAddress.getHostAddress())) {
            throw new ConnectionPolicyException("Q-EGRESS-SOCKET-BIND",
                    "Connected socket address is not in the agent-approved DNS set");
        }
    }
}
