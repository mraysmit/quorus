/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent.service;

import dev.mars.quorus.connection.ConnectionAccessRequest;
import dev.mars.quorus.connection.ConnectionPolicyException;
import dev.mars.quorus.connection.HostResolver;
import dev.mars.quorus.connection.SecretLease;
import dev.mars.quorus.connection.SecretProvider;
import dev.mars.quorus.connection.SecretReference;
import dev.mars.quorus.connection.ServiceConnection;
import org.junit.jupiter.api.Test;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.net.InetAddress;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AgentGovernedConnectionTest {

    @Test
    void agentDecoderAcceptsControllerContractCaseAndDefaults() {
        Instant now = Instant.parse("2026-09-05T00:00:00Z");
        JsonObject json = new JsonObject()
                .put("serviceConnectionId", "codec-connection").put("tenantId", "bank-a")
                .put("protocol", "sftp").put("endpoint", "sftp://192.0.2.10")
                .put("networkZone", "restricted-egress")
                .put("allowedPaths", new JsonArray().add("/in"))
                .put("allowedDirections", new JsonArray().add("download"))
                .put("allowedAgentPools", new JsonArray().add("payments"))
                .put("owner", "payments-ops").put("environment", "production")
                .put("classification", "confidential").put("secretReferenceId", "payments-key")
                .put("serviceIdentity", "payments-batch").put("authenticationType", "password")
                .put("trustPolicy", new JsonObject()
                        .put("sshHostKeyFingerprints", new JsonArray().add("SHA256:known")))
                .put("egressPolicy", new JsonObject()
                        .put("allowedHostnames", new JsonArray().add("192.0.2.10"))
                        .put("allowedCidrs", new JsonArray().add("192.0.2.0/24"))
                        .put("allowedPorts", new JsonArray().add(22)))
                .put("createdAt", now.toString()).put("updatedAt", now.toString());

        ServiceConnection decoded = AgentConnectionPolicyService.parseConnection(json);

        assertEquals(ServiceConnection.Protocol.SFTP, decoded.protocol());
        assertEquals(Set.of(ServiceConnection.Direction.DOWNLOAD), decoded.allowedDirections());
        assertEquals(ServiceConnection.AuthenticationType.PASSWORD, decoded.authenticationType());
        assertEquals(ServiceConnection.Status.ACTIVE, decoded.status());
        assertEquals(1, decoded.policyVersion());
        assertTrue(decoded.egressPolicy().pinResolvedAddresses());
        assertEquals("TLSv1.3", decoded.trustPolicy().minimumTlsVersion());
    }

    @Test
    void agentRepeatsPolicyBeforeSecretResolutionAndReturnsCloseableRuntimeCredential() throws Exception {
        AtomicInteger resolutions = new AtomicInteger();
        SecretProvider vault = new SecretProvider() {
            @Override public String providerId() { return "VAULT_KV_V2"; }
            @Override public SecretLease resolve(SecretReference reference) {
                resolutions.incrementAndGet();
                return new SecretLease(reference.secretReferenceId(), "never-log-this".toCharArray(), null);
            }
        };
        HostResolver resolver = host -> List.of(InetAddress.getByName("192.0.2.10"));
        AgentConnectionPolicyService service = new AgentConnectionPolicyService(
                resolver, List.of(vault));
        ServiceConnection connection = connection();
        SecretReference reference = new SecretReference("payments-key", "bank-a", "VAULT_KV_V2",
                "secret/data/payments", "password", "7", SecretReference.Status.ACTIVE,
                Instant.now().plusSeconds(600), Instant.now());

        ConnectionAccessRequest denied = new ConnectionAccessRequest("bank-a", "/forbidden/x.dat",
                ServiceConnection.Direction.DOWNLOAD, "payments", "restricted-egress",
                List.of("192.0.2.10"));
        assertThrows(ConnectionPolicyException.class,
                () -> service.authorize(connection, reference, denied));
        assertEquals(0, resolutions.get(), "policy denial must happen before Vault is contacted");

        ConnectionAccessRequest allowed = new ConnectionAccessRequest("bank-a", "/in/x.dat",
                ServiceConnection.Direction.DOWNLOAD, "payments", "restricted-egress",
                List.of("192.0.2.10"));
        try (AgentConnectionPolicyService.AuthorizedConnection authorized =
                     service.authorize(connection, reference, allowed)) {
            assertEquals("payments-batch", authorized.runtimeCredential().identity());
            assertArrayEquals("never-log-this".toCharArray(), authorized.runtimeCredential().copySecret());
            assertFalse(authorized.toString().contains("never-log-this"));
        }
        assertEquals(1, resolutions.get());
    }

    private static ServiceConnection connection() {
        Instant now = Instant.now();
        return new ServiceConnection("payments-sftp", "bank-a", ServiceConnection.Protocol.SFTP,
                URI.create("sftp://payments.example.test:22"), "restricted-egress",
                Set.of("/in"), Set.of(ServiceConnection.Direction.DOWNLOAD), Set.of("payments"),
                "payments-ops", "PRODUCTION", "CONFIDENTIAL", "payments-key",
                "payments-batch", ServiceConnection.AuthenticationType.PASSWORD,
                new ServiceConnection.TrustPolicy(false, false, Set.of(), Set.of("SHA256:known"),
                        "TLSv1.3", Set.of(), true),
                new ServiceConnection.EgressPolicy(Set.of("payments.example.test"), Set.of("192.0.2.0/24"),
                        Set.of(22), false, true),
                4, ServiceConnection.Status.ACTIVE, now, now);
    }
}
