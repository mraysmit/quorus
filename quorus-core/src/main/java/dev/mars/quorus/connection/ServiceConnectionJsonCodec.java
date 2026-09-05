/* Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache-2.0. */
package dev.mars.quorus.connection;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Canonical JSON contract shared by controllers and agents for governed connection authority. */
public final class ServiceConnectionJsonCodec {
    private ServiceConnectionJsonCodec() { }

    public static JsonObject connectionToJson(ServiceConnection connection) {
        ServiceConnection.TrustPolicy trust = connection.trustPolicy();
        ServiceConnection.EgressPolicy egress = connection.egressPolicy();
        return new JsonObject()
                .put("serviceConnectionId", connection.serviceConnectionId()).put("tenantId", connection.tenantId())
                .put("protocol", connection.protocol().name()).put("endpoint", connection.endpoint().toString())
                .put("networkZone", connection.networkZone()).put("allowedPaths", array(connection.allowedPaths()))
                .put("allowedDirections", array(connection.allowedDirections().stream().map(Enum::name).toList()))
                .put("allowedAgentPools", array(connection.allowedAgentPools())).put("owner", connection.owner())
                .put("environment", connection.environment()).put("classification", connection.classification())
                .put("secretReferenceId", connection.secretReferenceId()).put("serviceIdentity", connection.serviceIdentity())
                .put("authenticationType", connection.authenticationType().name())
                .put("trustPolicy", new JsonObject()
                        .put("tlsRequired", trust.tlsRequired()).put("hostnameVerification", trust.hostnameVerification())
                        .put("approvedCaIds", array(trust.approvedCaIds()))
                        .put("sshHostKeyFingerprints", array(trust.sshHostKeyFingerprints()))
                        .put("minimumTlsVersion", trust.minimumTlsVersion())
                        .put("tlsPeerFingerprints", array(trust.tlsPeerFingerprints()))
                        .put("transportEncryptionRequired", trust.transportEncryptionRequired()))
                .put("egressPolicy", new JsonObject()
                        .put("allowedHostnames", array(egress.allowedHostnames()))
                        .put("allowedCidrs", array(egress.allowedCidrs()))
                        .put("allowedPorts", new JsonArray(egress.allowedPorts().stream().sorted().toList()))
                        .put("allowRedirects", egress.allowRedirects())
                        .put("pinResolvedAddresses", egress.pinResolvedAddresses()))
                .put("policyVersion", connection.policyVersion()).put("status", connection.status().name())
                .put("createdAt", connection.createdAt().toString()).put("updatedAt", connection.updatedAt().toString());
    }

    public static ServiceConnection connectionFromJson(JsonObject json) {
        JsonObject trust = json.getJsonObject("trustPolicy", new JsonObject());
        JsonObject egress = json.getJsonObject("egressPolicy", new JsonObject());
        return new ServiceConnection(json.getString("serviceConnectionId"), json.getString("tenantId"),
                enumValue(ServiceConnection.Protocol.class, json.getString("protocol")),
                URI.create(json.getString("endpoint")), json.getString("networkZone"), strings(json.getJsonArray("allowedPaths")),
                strings(json.getJsonArray("allowedDirections")).stream()
                        .map(value -> enumValue(ServiceConnection.Direction.class, value)).collect(Collectors.toUnmodifiableSet()),
                strings(json.getJsonArray("allowedAgentPools")), json.getString("owner"), json.getString("environment"),
                json.getString("classification"), json.getString("secretReferenceId"), json.getString("serviceIdentity"),
                enumValue(ServiceConnection.AuthenticationType.class, json.getString("authenticationType")),
                new ServiceConnection.TrustPolicy(trust.getBoolean("tlsRequired", false),
                        trust.getBoolean("hostnameVerification", false), strings(trust.getJsonArray("approvedCaIds")),
                        strings(trust.getJsonArray("sshHostKeyFingerprints")), trust.getString("minimumTlsVersion", "TLSv1.3"),
                        strings(trust.getJsonArray("tlsPeerFingerprints")),
                        trust.getBoolean("transportEncryptionRequired", trust.getBoolean("tlsRequired", false))),
                new ServiceConnection.EgressPolicy(strings(egress.getJsonArray("allowedHostnames")),
                        strings(egress.getJsonArray("allowedCidrs")), integers(egress.getJsonArray("allowedPorts")),
                        egress.getBoolean("allowRedirects", false), egress.getBoolean("pinResolvedAddresses", true)),
                json.getInteger("policyVersion", 1),
                enumValue(ServiceConnection.Status.class, json.getString("status", "ACTIVE")),
                Instant.parse(json.getString("createdAt")), Instant.parse(json.getString("updatedAt")));
    }

    public static JsonObject secretToJson(SecretReference reference) {
        JsonObject json = new JsonObject().put("secretReferenceId", reference.secretReferenceId())
                .put("tenantId", reference.tenantId()).put("provider", reference.provider())
                .put("path", reference.path()).put("key", reference.key()).put("version", reference.version())
                .put("status", reference.status().name());
        if (reference.expiresAt() != null) json.put("expiresAt", reference.expiresAt().toString());
        if (reference.lastRotatedAt() != null) json.put("lastRotatedAt", reference.lastRotatedAt().toString());
        return json;
    }

    public static SecretReference secretFromJson(JsonObject json) {
        return new SecretReference(json.getString("secretReferenceId"), json.getString("tenantId"),
                json.getString("provider"), json.getString("path"), json.getString("key"), json.getString("version"),
                enumValue(SecretReference.Status.class, json.getString("status", "ACTIVE")),
                instant(json.getString("expiresAt")), instant(json.getString("lastRotatedAt")));
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value == null) throw new IllegalArgumentException(type.getSimpleName() + " is required");
        return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    }
    private static JsonArray array(java.util.Collection<?> values) {
        return new JsonArray(values.stream().sorted(Comparator.comparing(Object::toString)).toList());
    }
    private static Set<String> strings(JsonArray values) {
        return values == null ? Set.of() : values.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }
    private static Set<Integer> integers(JsonArray values) {
        return values == null ? Set.of() : values.stream().map(value -> ((Number) value).intValue())
                .collect(Collectors.toUnmodifiableSet());
    }
    private static Instant instant(String value) { return value == null ? null : Instant.parse(value); }
}
