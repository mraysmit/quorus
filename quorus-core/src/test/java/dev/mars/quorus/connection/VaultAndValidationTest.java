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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 4 production-provider and redacted staged-validation contract. */
class VaultAndValidationTest {

    @Test
    void vaultKvV2ResolvesOnlyTheRequestedFieldWithoutExposingIt() throws Exception {
        AtomicReference<URI> requested = new AtomicReference<>();
        VaultKvV2SecretProvider provider = new VaultKvV2SecretProvider(
                URI.create("https://vault.example.test"),
                () -> "synthetic-vault-token".toCharArray(),
                (uri, headers) -> {
                    requested.set(uri);
                    assertTrue(headers.containsKey("X-Vault-Token"));
                    return new VaultKvV2SecretProvider.VaultResponse(200,
                            "{\"data\":{\"data\":{\"password\":\"synthetic-runtime-password\"},"
                                    + "\"metadata\":{\"version\":7}}}");
                });
        SecretReference reference = new SecretReference("vault-payments-sftp", "regulated-bank-a",
                "VAULT_KV_V2", "quorus/data/payments/sftp", "password", "7",
                SecretReference.Status.ACTIVE, null, null);

        try (SecretLease lease = provider.resolve(reference)) {
            assertEquals('s', lease.copyValue()[0]);
            assertFalse(lease.toString().contains("synthetic-runtime-password"));
        }
        assertEquals("https://vault.example.test/v1/quorus/data/payments/sftp?version=7",
                requested.get().toString());
        assertFalse(requested.get().toString().contains("synthetic-vault-token"));
    }

    @Test
    void stagedValidationIsRedactedAndClosesTheSecretLease() throws Exception {
        ServiceConnection connection = connection();
        SecretReference reference = new SecretReference("vault-payments-sftp", "regulated-bank-a",
                "VAULT_KV_V2", "quorus/data/payments/sftp", "password", "7",
                SecretReference.Status.ACTIVE, null, null);
        HostResolver resolver = host -> List.of(InetAddress.getByName("192.0.2.10"));
        SecretProvider provider = new SecretProvider() {
            @Override public String providerId() { return "VAULT_KV_V2"; }
            @Override public SecretLease resolve(SecretReference ignored) {
                return new SecretLease(ignored.secretReferenceId(), "never-report-this".toCharArray(),
                        Instant.now().plusSeconds(30));
            }
        };
        GovernedConnectionResolver governed = new GovernedConnectionResolver(
                new ConnectionPolicyEnforcer(), resolver, Map.of(provider.providerId(), provider));
        ConnectionValidationService validation = new ConnectionValidationService(governed,
                resolved -> List.of(
                        new ConnectionValidationService.Stage("ROUTE", "PASS", "Approved address reachable"),
                        new ConnectionValidationService.Stage("NEGOTIATION", "PASS", "Protocol negotiation succeeded"),
                        new ConnectionValidationService.Stage("IDENTITY", "PASS", "Pinned identity matched"),
                        new ConnectionValidationService.Stage("AUTHENTICATION", "PASS", "External secret accepted"),
                        new ConnectionValidationService.Stage("AUTHORIZATION", "PASS", "Path access allowed")));
        ConnectionAccessRequest request = new ConnectionAccessRequest("regulated-bank-a",
                "/outbound/settlement.dat", ServiceConnection.Direction.DOWNLOAD,
                "payments-agents", "payments-dmz", List.of("192.0.2.10"));

        ConnectionValidationService.Result result = validation.validateAtAgent(connection, reference, request);
        assertEquals("VALID", result.status());
        assertEquals(7, result.stages().size());
        assertFalse(result.toString().contains("never-report-this"));
        assertFalse(result.toString().contains("synthetic-vault-token"));
    }

    private static ServiceConnection connection() {
        return new ServiceConnection("payments-sftp", "regulated-bank-a", ServiceConnection.Protocol.SFTP,
                URI.create("sftp://192.0.2.10:22"), "payments-dmz", Set.of("/outbound"),
                Set.of(ServiceConnection.Direction.DOWNLOAD), Set.of("payments-agents"),
                "payments-platform", "PRODUCTION", "CONFIDENTIAL", "vault-payments-sftp",
                "payments-batch", ServiceConnection.AuthenticationType.PASSWORD,
                new ServiceConnection.TrustPolicy(false, false, Set.of(),
                        Set.of("SHA256:synthetic-host-key-pin"), "TLSv1.3"),
                new ServiceConnection.EgressPolicy(Set.of("192.0.2.10"), Set.of("192.0.2.0/24"),
                        Set.of(22), false, true), 1, ServiceConnection.Status.ACTIVE,
                Instant.parse("2026-09-03T00:00:00Z"), Instant.parse("2026-09-03T00:00:00Z"));
    }
}
