/* Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache-2.0. */
package dev.mars.quorus.connection;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * The port a governed endpoint uses when its URI names none. One table serves the policy, the route probe
 * and the transfer adapters, so the port the policy approves is the port the adapter connects to (register
 * item ENG-07). Production uses {@link #wellKnown()}; a test substitutes an unprivileged port with
 * {@link #with(ServiceConnection.Protocol, int)} instead of binding a privileged one.
 */
public final class DefaultPorts {
    private static final DefaultPorts WELL_KNOWN = new DefaultPorts(wellKnownPorts());

    private final Map<ServiceConnection.Protocol, Integer> ports;

    private DefaultPorts(Map<ServiceConnection.Protocol, Integer> ports) {
        this.ports = ports;
    }

    /** The standard ports: SFTP 22, HTTPS 443, explicit FTPS 21, SMB 445, NFS 2049. */
    public static DefaultPorts wellKnown() {
        return WELL_KNOWN;
    }

    /** Returns a copy of this table with {@code protocol} mapped to {@code port}. */
    public DefaultPorts with(ServiceConnection.Protocol protocol, int port) {
        Objects.requireNonNull(protocol, "protocol");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Default port must be between 1 and 65535, got: " + port);
        }
        EnumMap<ServiceConnection.Protocol, Integer> changed = new EnumMap<>(ports);
        changed.put(protocol, port);
        return new DefaultPorts(changed);
    }

    public int portFor(ServiceConnection.Protocol protocol) {
        return ports.get(Objects.requireNonNull(protocol, "protocol"));
    }

    /** The endpoint's own port when it names one, otherwise this table's default for the protocol. */
    public int effectivePort(ServiceConnection.Protocol protocol, URI endpoint) {
        return endpoint.getPort() > 0 ? endpoint.getPort() : portFor(protocol);
    }

    private static Map<ServiceConnection.Protocol, Integer> wellKnownPorts() {
        EnumMap<ServiceConnection.Protocol, Integer> ports = new EnumMap<>(ServiceConnection.Protocol.class);
        for (ServiceConnection.Protocol protocol : ServiceConnection.Protocol.values()) {
            ports.put(protocol, protocol.defaultPort());
        }
        return ports;
    }
}
