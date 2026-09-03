/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeProtocolSecurityTest {

    @Test
    void runtimeCredentialsAreRedactedAndWiped() {
        RuntimeCredential credential = new RuntimeCredential("user", ServiceConnection.AuthenticationType.PASSWORD,
                "top-secret".toCharArray(), Set.of("SHA256:known"), Set.of(), "TLSv1.3");
        assertFalse(credential.toString().contains("top-secret"));
        assertArrayEquals("top-secret".toCharArray(), credential.copySecret());
        credential.close();
        assertThrows(IllegalStateException.class, credential::copySecret);
    }

    @Test
    void governedRuntimeBindsConnectionsToAnAgentApprovedAddress() throws Exception {
        RuntimeCredential credential = new RuntimeCredential("user",
                ServiceConnection.AuthenticationType.PASSWORD, "secret".toCharArray(), Set.of(), Set.of(),
                Set.of(), "TLSv1.3", List.of("192.0.2.10"));
        URI authority = URI.create("https://payments.example.test:8443/outbound/file.dat?version=1");

        assertEquals(URI.create("https://192.0.2.10:8443/outbound/file.dat?version=1"),
                PinnedEndpoint.connectUri(authority, credential));
        assertEquals("payments.example.test:8443", PinnedEndpoint.virtualHost(authority));
        assertThrows(ConnectionPolicyException.class, () -> PinnedEndpoint.requireApprovedAddress(
                java.net.InetAddress.getByName("192.0.2.11"), credential));
    }

    @Test
    void pinnedConnectionUriPreservesRawPathQueryAndFragment() throws Exception {
        RuntimeCredential credential = new RuntimeCredential("user",
                ServiceConnection.AuthenticationType.PASSWORD, "secret".toCharArray(), Set.of(), Set.of(),
                Set.of(), "TLSv1.3", List.of("2001:db8::10"));
        URI authority = URI.create("https://payments.example.test:8443/outbound/account%2Fdaily.dat"
                + "?token=a%2Fb#batch%201");

        assertEquals("https://[2001:db8::10]:8443/outbound/account%2Fdaily.dat?token=a%2Fb#batch%201",
                PinnedEndpoint.connectUri(authority, credential).toASCIIString());
    }

    @Test
    void sftpHostKeyPinsFailClosedOnUnknownOrChangedKeys() {
        byte[] hostKey = "server-public-key".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String pin = SftpHostKeyPolicy.sha256Fingerprint(hostKey);
        assertDoesNotThrow(() -> SftpHostKeyPolicy.requireApproved(hostKey, Set.of(pin)));
        assertThrows(ConnectionPolicyException.class,
                () -> SftpHostKeyPolicy.requireApproved("changed-key".getBytes(), Set.of(pin)));
        assertThrows(ConnectionPolicyException.class,
                () -> SftpHostKeyPolicy.requireApproved(hostKey, Set.of()));
    }

    @Test
    void tlsPeerPinsFailClosedOnUnknownOrChangedCertificates() {
        byte[] certificate = "synthetic-peer-certificate".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String pin = TlsPeerPolicy.sha256Fingerprint(certificate);
        assertDoesNotThrow(() -> TlsPeerPolicy.requireApproved(certificate, Set.of(pin)));
        assertThrows(ConnectionPolicyException.class,
                () -> TlsPeerPolicy.requireApproved("changed-certificate".getBytes(), Set.of(pin)));
    }

    @Test
    void approvedCaPinsRestrictTheOtherwiseValidCertificateChain() {
        byte[] approvedCa = "synthetic-approved-ca".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] unapprovedCa = "synthetic-unapproved-ca".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String approvedCaId = TlsPeerPolicy.sha256Fingerprint(approvedCa);

        assertDoesNotThrow(() -> TlsPeerPolicy.requireApprovedCa(List.of(approvedCa), Set.of(approvedCaId)));
        assertThrows(ConnectionPolicyException.class,
                () -> TlsPeerPolicy.requireApprovedCa(List.of(unapprovedCa), Set.of(approvedCaId)));
        assertArrayEquals(new String[]{"TLSv1.2", "TLSv1.3"}, TlsPeerPolicy.enabledProtocols("TLSv1.2"));
        assertArrayEquals(new String[]{"TLSv1.3"}, TlsPeerPolicy.enabledProtocols("TLSv1.3"));
    }

    @Test
    void nfsAndSmbRequireEncryptedTransportAndClearFtpIsNotAProductionProtocol() {
        assertFalse(java.util.Arrays.stream(ServiceConnection.Protocol.values())
                .anyMatch(protocol -> protocol.name().equals("FTP")));
        assertThrows(IllegalArgumentException.class, () -> connection(ServiceConnection.Protocol.NFS, false));
        assertThrows(IllegalArgumentException.class, () -> connection(ServiceConnection.Protocol.SMB, false));
        assertThrows(ConnectionPolicyException.class,
                () -> MountedFileSystemSecurity.requireVerified("SMB", false));
        assertThrows(ConnectionPolicyException.class,
                () -> MountedFileSystemSecurity.requireVerified("NFS", false));
        assertDoesNotThrow(() -> MountedFileSystemSecurity.requireVerified("NFS", true));
    }

    @Test
    void protocolsRejectAuthenticationModesTheirAdaptersCannotEnforce() {
        assertDoesNotThrow(() -> connection(ServiceConnection.Protocol.SFTP, true,
                ServiceConnection.AuthenticationType.SSH_PRIVATE_KEY));
        assertThrows(IllegalArgumentException.class, () -> connection(ServiceConnection.Protocol.SFTP, true,
                ServiceConnection.AuthenticationType.BEARER));
        assertDoesNotThrow(() -> connection(ServiceConnection.Protocol.HTTPS, true,
                ServiceConnection.AuthenticationType.BEARER));
        assertThrows(IllegalArgumentException.class, () -> connection(ServiceConnection.Protocol.FTPS, true,
                ServiceConnection.AuthenticationType.BASIC));
    }

    private static ServiceConnection connection(ServiceConnection.Protocol protocol, boolean encrypted) {
        return connection(protocol, encrypted, ServiceConnection.AuthenticationType.PASSWORD);
    }

    private static ServiceConnection connection(ServiceConnection.Protocol protocol, boolean encrypted,
                                                ServiceConnection.AuthenticationType authenticationType) {
        Instant now = Instant.now();
        int port = protocol.defaultPort();
        Set<String> sshPins = protocol == ServiceConnection.Protocol.SFTP ? Set.of("SHA256:known") : Set.of();
        Set<String> caPins = protocol == ServiceConnection.Protocol.HTTPS || protocol == ServiceConnection.Protocol.FTPS
                ? Set.of("SHA256:approved-ca") : Set.of();
        return new ServiceConnection("alias", "tenant", protocol,
                URI.create(protocol.scheme() + "://host.example:" + port), "zone", Set.of("/approved"),
                Set.of(ServiceConnection.Direction.DOWNLOAD), Set.of("pool"), "owner", "PRODUCTION", "INTERNAL",
                "secret", "service-user", authenticationType,
                new ServiceConnection.TrustPolicy(protocol == ServiceConnection.Protocol.HTTPS
                        || protocol == ServiceConnection.Protocol.FTPS,
                        protocol == ServiceConnection.Protocol.HTTPS || protocol == ServiceConnection.Protocol.FTPS,
                        caPins, sshPins, "TLSv1.3", Set.of(), encrypted),
                new ServiceConnection.EgressPolicy(Set.of("host.example"), Set.of("192.0.2.0/24"),
                        Set.of(port), false, true), 1, ServiceConnection.Status.ACTIVE, now, now);
    }
}
