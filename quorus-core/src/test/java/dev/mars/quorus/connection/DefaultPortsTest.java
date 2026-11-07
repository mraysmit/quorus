/* Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache-2.0. */
package dev.mars.quorus.connection;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static dev.mars.quorus.connection.ServiceConnection.Protocol.*;
import static org.junit.jupiter.api.Assertions.*;

/** The one default-port table shared by the policy, the route probe and the adapters (register item ENG-07). */
class DefaultPortsTest {

    @Test
    void wellKnownTableUsesTheStandardPorts() {
        DefaultPorts ports = DefaultPorts.wellKnown();

        assertEquals(22, ports.portFor(SFTP));
        assertEquals(443, ports.portFor(HTTPS));
        assertEquals(21, ports.portFor(FTPS), "explicit FTPS, AUTH TLS on the FTP control port");
        assertEquals(445, ports.portFor(SMB));
        assertEquals(2049, ports.portFor(NFS));
    }

    @Test
    void anExplicitPortInTheEndpointWins() {
        DefaultPorts ports = DefaultPorts.wellKnown();

        assertEquals(2222, ports.effectivePort(SFTP, URI.create("sftp://host:2222/path")));
        assertEquals(22, ports.effectivePort(SFTP, URI.create("sftp://host/path")));
    }

    @Test
    void withReturnsAChangedCopy() {
        DefaultPorts wellKnown = DefaultPorts.wellKnown();
        DefaultPorts changed = wellKnown.with(FTPS, 40021);

        assertEquals(40021, changed.portFor(FTPS));
        assertEquals(22, changed.portFor(SFTP));
        assertEquals(21, wellKnown.portFor(FTPS), "the well-known table must not change");
    }

    @Test
    void rejectsAPortOutsideTheTcpRange() {
        DefaultPorts ports = DefaultPorts.wellKnown();

        assertThrows(IllegalArgumentException.class, () -> ports.with(SFTP, 0));
        assertThrows(IllegalArgumentException.class, () -> ports.with(SFTP, 65536));
    }
}
