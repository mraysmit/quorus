/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 4 controller/agent policy and secret-ordering contract. */
class GovernedConnectionSecurityTest {

    @Test
    void rejectsUnapprovedPathBeforeSecretRetrievalAndAllowsGovernedRequest() throws Exception {
        ServiceConnection connection = connection();
        SecretReference reference = new SecretReference("vault-payments-sftp", "regulated-bank-a",
                "VAULT_KV_V2", "quorus/data/payments/sftp", "password", "7",
                SecretReference.Status.ACTIVE, null, null);
        AtomicInteger resolutions = new AtomicInteger();
        SecretProvider provider = new SecretProvider() {
            @Override public String providerId() { return "VAULT_KV_V2"; }
            @Override public SecretLease resolve(SecretReference ignored) {
                resolutions.incrementAndGet();
                return new SecretLease("vault-payments-sftp", "synthetic-runtime-value".toCharArray(),
                        Instant.now().plusSeconds(60));
            }
        };
        HostResolver resolver = host -> List.of(InetAddress.getByName("192.0.2.10"));
        GovernedConnectionResolver governed = new GovernedConnectionResolver(
                new ConnectionPolicyEnforcer(), resolver, Map.of(provider.providerId(), provider));

        ConnectionAccessRequest denied = new ConnectionAccessRequest("regulated-bank-a", "/private/ledger.dat",
                ServiceConnection.Direction.DOWNLOAD, "payments-agents", "payments-dmz",
                List.of("192.0.2.10"));
        assertThrows(ConnectionPolicyException.class,
                () -> governed.resolveAtAgent(connection, reference, denied));
        assertEquals(0, resolutions.get(), "Policy must fail before secret retrieval");

        ConnectionAccessRequest allowed = new ConnectionAccessRequest("regulated-bank-a",
                "/outbound/settlement.dat", ServiceConnection.Direction.DOWNLOAD,
                "payments-agents", "payments-dmz", List.of("192.0.2.10"));
        try (ResolvedConnection resolved = governed.resolveAtAgent(connection, reference, allowed)) {
            assertEquals(1, resolutions.get());
            assertEquals(List.of("192.0.2.10"), resolved.authorization().resolvedAddresses());
            assertEquals('s', resolved.secret().copyValue()[0]);
        }
    }

    @Test
    void failsClosedForDnsRebindingChangedHostKeyAndUnsafeRedirectPolicy() throws Exception {
        ServiceConnection connection = connection();
        ConnectionAccessRequest request = new ConnectionAccessRequest("regulated-bank-a",
                "/outbound/settlement.dat", ServiceConnection.Direction.DOWNLOAD,
                "payments-agents", "payments-dmz", List.of("192.0.2.10"));
        ConnectionPolicyEnforcer enforcer = new ConnectionPolicyEnforcer();

        assertThrows(ConnectionPolicyException.class, () -> enforcer.authorizeAtAgent(
                connection, request, host -> List.of(InetAddress.getByName("192.0.2.11"))));
        assertThrows(ConnectionPolicyException.class,
                () -> enforcer.verifySshHostKey(connection, "SHA256:changed-host-key"));
        assertFalse(connection.egressPolicy().allowRedirects());
        assertTrue(connection.egressPolicy().pinResolvedAddresses());
    }

    @Test
    void executingAgentPoolAndNetworkZoneMustMatchTheConnectionPolicy() throws Exception {
        ServiceConnection connection = connection();
        ConnectionPolicyEnforcer enforcer = new ConnectionPolicyEnforcer();
        HostResolver resolver = host -> List.of(InetAddress.getByName("192.0.2.10"));

        ConnectionAccessRequest wrongPool = new ConnectionAccessRequest("regulated-bank-a",
                "/outbound/settlement.dat", ServiceConnection.Direction.DOWNLOAD,
                "untrusted-agents", "payments-dmz", List.of("192.0.2.10"));
        ConnectionAccessRequest wrongZone = new ConnectionAccessRequest("regulated-bank-a",
                "/outbound/settlement.dat", ServiceConnection.Direction.DOWNLOAD,
                "payments-agents", "corporate-lan", List.of("192.0.2.10"));

        assertEquals("Q-CONNECTION-AGENT-POOL", assertThrows(ConnectionPolicyException.class,
                () -> enforcer.authorizeAtAgent(connection, wrongPool, resolver)).decisionCode());
        assertEquals("Q-CONNECTION-NETWORK-ZONE", assertThrows(ConnectionPolicyException.class,
                () -> enforcer.authorizeAtAgent(connection, wrongZone, resolver)).decisionCode());
    }

    @Test
    void migrationFindingsNeverEchoCredentialContents() {
        List<CredentialUriMigrationScanner.Finding> findings = new CredentialUriMigrationScanner().scan(Map.of(
                "legacy-route", URI.create("sftp://operator:synthetic-password@files.example.test/out.dat"),
                "safe-route", URI.create("sftp://files.example.test/out.dat")));

        assertEquals(1, findings.size());
        assertEquals("legacy-route", findings.getFirst().resourceId());
        assertFalse(findings.toString().contains("synthetic-password"));
        assertFalse(findings.toString().contains("operator"));
    }

    private static ServiceConnection connection() {
        return new ServiceConnection(
                "payments-sftp", "regulated-bank-a", ServiceConnection.Protocol.SFTP,
                URI.create("sftp://192.0.2.10:22"), "payments-dmz", Set.of("/outbound"),
                Set.of(ServiceConnection.Direction.DOWNLOAD), Set.of("payments-agents"),
                "payments-platform", "PRODUCTION", "CONFIDENTIAL", "vault-payments-sftp",
                "payments-batch", ServiceConnection.AuthenticationType.PASSWORD,
                new ServiceConnection.TrustPolicy(true, true, Set.of(),
                        Set.of("SHA256:synthetic-host-key-pin"), "TLSv1.3"),
                new ServiceConnection.EgressPolicy(Set.of("192.0.2.10"), Set.of("192.0.2.0/24"),
                        Set.of(22), false, true),
                1, ServiceConnection.Status.ACTIVE, Instant.parse("2026-09-03T00:00:00Z"),
                Instant.parse("2026-09-03T00:00:00Z"));
    }
}
