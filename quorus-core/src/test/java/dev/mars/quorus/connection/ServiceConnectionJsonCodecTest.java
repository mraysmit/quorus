/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Plan item RT-03e: the connection codec has a JDK-typed API (JSON text) and no Vert.x. Its output is
 * stored by the controller's registry and read by agents, so it must stay byte-identical to the output
 * of the Vert.x implementation it replaces. The golden strings below were produced by that
 * implementation on 2026-09-27 from these same samples.
 */
@DisplayName("ServiceConnectionJsonCodec")
class ServiceConnectionJsonCodecTest {

    private static final String CONNECTION_JSON = "{\"serviceConnectionId\":\"payments-sftp\",\"tenantId\":\"bank-a\","
            + "\"protocol\":\"SFTP\",\"endpoint\":\"sftp://files.example.test:2222\",\"networkZone\":\"restricted\","
            + "\"allowedPaths\":[\"/a b\",\"/in\",\"/out/déjà\"],\"allowedDirections\":[\"DOWNLOAD\",\"UPLOAD\"],"
            + "\"allowedAgentPools\":[\"pool-a\",\"pool-b\"],\"owner\":\"payments-ops\",\"environment\":\"PROD\","
            + "\"classification\":\"CONFIDENTIAL\",\"secretReferenceId\":\"payments-key\","
            + "\"serviceIdentity\":\"svc \\\"batch\\\"\",\"authenticationType\":\"PASSWORD\","
            + "\"trustPolicy\":{\"tlsRequired\":true,\"hostnameVerification\":true,"
            + "\"approvedCaIds\":[\"SHA256:ca1\",\"SHA256:ca2\"],\"sshHostKeyFingerprints\":[\"SHA256:hk\"],"
            + "\"minimumTlsVersion\":\"TLSv1.2\",\"tlsPeerFingerprints\":[\"SHA256:leaf\"],"
            + "\"transportEncryptionRequired\":true},"
            + "\"egressPolicy\":{\"allowedHostnames\":[\"files.example.test\"],"
            + "\"allowedCidrs\":[\"10.0.0.0/8\",\"192.168.1.0/24\"],\"allowedPorts\":[22,990,2222],"
            + "\"allowRedirects\":false,\"pinResolvedAddresses\":true},"
            + "\"policyVersion\":7,\"status\":\"ACTIVE\",\"createdAt\":\"2026-09-01T09:00:00Z\","
            + "\"updatedAt\":\"2026-09-02T10:30:00.123456Z\"}";
    private static final String SECRET_JSON = "{\"secretReferenceId\":\"payments-key\",\"tenantId\":\"bank-a\","
            + "\"provider\":\"VAULT_KV_V2\",\"path\":\"secret/data/payments\",\"key\":\"password\",\"version\":\"3\","
            + "\"status\":\"ACTIVE\",\"expiresAt\":\"2027-01-01T00:00:00Z\",\"lastRotatedAt\":\"2026-09-01T00:00:00Z\"}";
    private static final String MINIMAL_SECRET_JSON = "{\"secretReferenceId\":\"k2\",\"tenantId\":\"bank-a\","
            + "\"provider\":\"VAULT_KV_V2\",\"path\":\"secret/data/x\",\"key\":\"token\",\"version\":\"1\","
            + "\"status\":\"REVOKED\"}";

    static ServiceConnection connection() {
        return new ServiceConnection("payments-sftp", "bank-a", ServiceConnection.Protocol.SFTP,
                URI.create("sftp://files.example.test:2222"), "restricted", Set.of("/in", "/out/déjà", "/a b"),
                Set.of(ServiceConnection.Direction.UPLOAD, ServiceConnection.Direction.DOWNLOAD),
                Set.of("pool-b", "pool-a"), "payments-ops", "PROD", "CONFIDENTIAL", "payments-key", "svc \"batch\"",
                ServiceConnection.AuthenticationType.PASSWORD,
                new ServiceConnection.TrustPolicy(true, true, Set.of("SHA256:ca2", "SHA256:ca1"),
                        Set.of("SHA256:hk"), "TLSv1.2", Set.of("SHA256:leaf"), true),
                new ServiceConnection.EgressPolicy(Set.of("files.example.test"), Set.of("10.0.0.0/8", "192.168.1.0/24"),
                        Set.of(2222, 22, 990), false, true),
                7, ServiceConnection.Status.ACTIVE, Instant.parse("2026-09-01T09:00:00Z"),
                Instant.parse("2026-09-02T10:30:00.123456Z"));
    }

    static SecretReference secret() {
        return new SecretReference("payments-key", "bank-a", "VAULT_KV_V2", "secret/data/payments", "password", "3",
                SecretReference.Status.ACTIVE, Instant.parse("2027-01-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"));
    }

    static SecretReference minimalSecret() {
        return new SecretReference("k2", "bank-a", "VAULT_KV_V2", "secret/data/x", "token", "1",
                SecretReference.Status.REVOKED, null, null);
    }

    @Test
    @DisplayName("A connection encodes to the same text as the Vert.x implementation")
    void connectionEncodingIsUnchanged() {
        assertEquals(CONNECTION_JSON, ServiceConnectionJsonCodec.encodeConnection(connection()));
    }

    @Test
    @DisplayName("Secret references encode to the same text as the Vert.x implementation")
    void secretEncodingIsUnchanged() {
        assertEquals(SECRET_JSON, ServiceConnectionJsonCodec.encodeSecret(secret()));
        assertEquals(MINIMAL_SECRET_JSON, ServiceConnectionJsonCodec.encodeSecret(minimalSecret()));
    }

    @Test
    @DisplayName("Stored text decodes to the original records")
    void storedTextDecodesToTheOriginalRecords() {
        assertEquals(connection(), ServiceConnectionJsonCodec.decodeConnection(CONNECTION_JSON));
        assertEquals(secret(), ServiceConnectionJsonCodec.decodeSecret(SECRET_JSON));
        SecretReference minimal = ServiceConnectionJsonCodec.decodeSecret(MINIMAL_SECRET_JSON);
        assertEquals(minimalSecret(), minimal);
        assertNull(minimal.expiresAt());
    }

    @Test
    @DisplayName("Omitted optional fields decode to the documented defaults")
    void omittedOptionalFieldsTakeTheirDefaults() {
        ServiceConnection decoded = ServiceConnectionJsonCodec.decodeConnection("{\"serviceConnectionId\":\"c\","
                + "\"tenantId\":\"t\",\"protocol\":\"https\",\"endpoint\":\"https://h.example.test\","
                + "\"networkZone\":\"z\",\"allowedPaths\":[\"/\"],\"allowedDirections\":[\"download\"],"
                + "\"allowedAgentPools\":[\"p\"],\"owner\":\"o\",\"environment\":\"e\",\"classification\":\"c\","
                + "\"secretReferenceId\":\"s\",\"serviceIdentity\":\"i\",\"authenticationType\":\"bearer\","
                + "\"trustPolicy\":{\"tlsRequired\":true,\"hostnameVerification\":true,\"approvedCaIds\":[\"SHA256:ca\"]},\"egressPolicy\":{\"allowedHostnames\":[\"h.example.test\"],"
                + "\"allowedCidrs\":[\"10.0.0.0/8\"],\"allowedPorts\":[443]},"
                + "\"createdAt\":\"2026-09-01T09:00:00Z\",\"updatedAt\":\"2026-09-01T09:00:00Z\"}");

        assertEquals(ServiceConnection.Protocol.HTTPS, decoded.protocol(), "enum values are case-insensitive");
        assertEquals(1, decoded.policyVersion());
        assertEquals(ServiceConnection.Status.ACTIVE, decoded.status());
        assertEquals("TLSv1.3", decoded.trustPolicy().minimumTlsVersion());
        assertEquals(true, decoded.trustPolicy().transportEncryptionRequired(), "defaults to tlsRequired");
        assertEquals(false, decoded.egressPolicy().allowRedirects());
        assertEquals(true, decoded.egressPolicy().pinResolvedAddresses());
        assertEquals(SecretReference.Status.ACTIVE, ServiceConnectionJsonCodec.decodeSecret(
                "{\"secretReferenceId\":\"k\",\"tenantId\":\"t\",\"provider\":\"p\",\"path\":\"x\",\"key\":\"y\","
                        + "\"version\":\"1\"}").status());
    }
}
