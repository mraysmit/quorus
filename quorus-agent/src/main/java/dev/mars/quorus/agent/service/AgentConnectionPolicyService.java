/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent.service;

import dev.mars.quorus.connection.ConnectionAccessRequest;
import dev.mars.quorus.connection.ConnectionPolicyEnforcer;
import dev.mars.quorus.connection.GovernedConnectionResolver;
import dev.mars.quorus.connection.HostResolver;
import dev.mars.quorus.connection.ResolvedConnection;
import dev.mars.quorus.connection.RuntimeCredential;
import dev.mars.quorus.connection.SecretProvider;
import dev.mars.quorus.connection.SecretReference;
import dev.mars.quorus.connection.ServiceConnection;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.URI;
import java.time.Instant;
import java.util.Set;

/** Agent-side enforcement boundary. Policy is evaluated before any provider is called. */
public final class AgentConnectionPolicyService {
    private final GovernedConnectionResolver resolver;

    public AgentConnectionPolicyService(HostResolver hostResolver, Collection<SecretProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        Map<String, SecretProvider> byId = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> provider.providerId().toUpperCase(java.util.Locale.ROOT), Function.identity()));
        this.resolver = new GovernedConnectionResolver(new ConnectionPolicyEnforcer(), hostResolver, byId);
    }

    public AuthorizedConnection authorize(ServiceConnection connection, SecretReference reference,
                                          ConnectionAccessRequest request) throws Exception {
        return authorize(connection, reference, request, null, null);
    }

    public AuthorizedConnection authorize(ServiceConnection connection, SecretReference reference,
                                          ConnectionAccessRequest request, Integer expectedPolicyVersion,
                                          String expectedPolicyDigest) throws Exception {
        ResolvedConnection resolved = resolver.resolveAtAgent(connection, reference, request,
                expectedPolicyVersion, expectedPolicyDigest);
        char[] secret = resolved.secret().copyValue();
        try {
            RuntimeCredential runtimeCredential = new RuntimeCredential(connection.serviceIdentity(),
                    connection.authenticationType(), secret,
                    connection.trustPolicy().sshHostKeyFingerprints(),
                    connection.trustPolicy().approvedCaIds(),
                    connection.trustPolicy().tlsPeerFingerprints(),
                    connection.trustPolicy().minimumTlsVersion(),
                    resolved.authorization().resolvedAddresses());
            return new AuthorizedConnection(resolved, runtimeCredential);
        } finally {
            java.util.Arrays.fill(secret, '\0');
        }
    }

    public static ServiceConnection parseConnection(JsonObject json) {
        JsonObject trust = json.getJsonObject("trustPolicy");
        JsonObject egress = json.getJsonObject("egressPolicy");
        return new ServiceConnection(json.getString("serviceConnectionId"), json.getString("tenantId"),
                ServiceConnection.Protocol.valueOf(json.getString("protocol")), URI.create(json.getString("endpoint")),
                json.getString("networkZone"), strings(json.getJsonArray("allowedPaths")),
                strings(json.getJsonArray("allowedDirections")).stream().map(ServiceConnection.Direction::valueOf)
                        .collect(Collectors.toUnmodifiableSet()),
                strings(json.getJsonArray("allowedAgentPools")), json.getString("owner"),
                json.getString("environment"), json.getString("classification"),
                json.getString("secretReferenceId"), json.getString("serviceIdentity"),
                ServiceConnection.AuthenticationType.valueOf(json.getString("authenticationType")),
                new ServiceConnection.TrustPolicy(trust.getBoolean("tlsRequired"),
                        trust.getBoolean("hostnameVerification"), strings(trust.getJsonArray("approvedCaIds")),
                        strings(trust.getJsonArray("sshHostKeyFingerprints")), trust.getString("minimumTlsVersion"),
                        strings(trust.getJsonArray("tlsPeerFingerprints")),
                        trust.getBoolean("transportEncryptionRequired")),
                new ServiceConnection.EgressPolicy(strings(egress.getJsonArray("allowedHostnames")),
                        strings(egress.getJsonArray("allowedCidrs")), integers(egress.getJsonArray("allowedPorts")),
                        egress.getBoolean("allowRedirects"), egress.getBoolean("pinResolvedAddresses")),
                json.getInteger("policyVersion"), ServiceConnection.Status.valueOf(json.getString("status")),
                Instant.parse(json.getString("createdAt")), Instant.parse(json.getString("updatedAt")));
    }

    public static SecretReference parseSecret(JsonObject json) {
        return new SecretReference(json.getString("secretReferenceId"), json.getString("tenantId"),
                json.getString("provider"), json.getString("path"), json.getString("key"), json.getString("version"),
                SecretReference.Status.valueOf(json.getString("status")), instant(json.getString("expiresAt")),
                instant(json.getString("lastRotatedAt")));
    }

    private static Set<String> strings(JsonArray values) {
        return values == null ? Set.of() : values.stream().map(String::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }
    private static Set<Integer> integers(JsonArray values) {
        return values == null ? Set.of() : values.stream().map(v -> ((Number) v).intValue())
                .collect(Collectors.toUnmodifiableSet());
    }
    private static Instant instant(String value) { return value == null ? null : Instant.parse(value); }

    public record AuthorizedConnection(ResolvedConnection resolved, RuntimeCredential runtimeCredential)
            implements AutoCloseable {
        @Override public void close() {
            runtimeCredential.close();
            resolved.close();
        }
        @Override public String toString() {
            return "AuthorizedConnection[serviceConnectionId="
                    + resolved.authorization().serviceConnectionId() + ", redacted=true]";
        }
    }
}
