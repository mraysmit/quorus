/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.state;

import dev.mars.quorus.connection.SecretReference;
import dev.mars.quorus.connection.ServiceConnection;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Typed projection over namespaced Raft-replicated system metadata. */
public final class ServiceConnectionRegistry {
    private static final String CONNECTION_PREFIX = "phase4.service-connection.";
    private static final String SECRET_PREFIX = "phase4.secret-reference.";
    private static final String EVENT_PREFIX = "phase4.security-event.";
    private final QuorusStateStore stateStore;

    public ServiceConnectionRegistry(QuorusStateStore stateStore) {
        this.stateStore = stateStore;
    }

    public String connectionKey(String tenantId, String id) {
        return CONNECTION_PREFIX + safe(tenantId) + "." + safe(id);
    }

    public String secretKey(String tenantId, String id) {
        return SECRET_PREFIX + safe(tenantId) + "." + safe(id);
    }

    public String eventKey(String tenantId, Instant timestamp) {
        return EVENT_PREFIX + safe(tenantId) + "." + timestamp.toEpochMilli() + "." + UUID.randomUUID();
    }

    public ServiceConnection findConnection(String tenantId, String id) {
        String encoded = stateStore.getMetadata(connectionKey(tenantId, id));
        return encoded == null ? null : connectionFromJson(new JsonObject(encoded));
    }

    public SecretReference findSecret(String tenantId, String id) {
        String encoded = stateStore.getMetadata(secretKey(tenantId, id));
        return encoded == null ? null : secretFromJson(new JsonObject(encoded));
    }

    public List<ServiceConnection> listConnections(String tenantId) {
        String prefix = CONNECTION_PREFIX + safe(tenantId) + ".";
        return stateStore.getSystemMetadata().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .map(entry -> connectionFromJson(new JsonObject(entry.getValue())))
                .sorted(Comparator.comparing(ServiceConnection::serviceConnectionId))
                .toList();
    }

    public List<SecretReference> listSecrets(String tenantId) {
        String prefix = SECRET_PREFIX + safe(tenantId) + ".";
        return stateStore.getSystemMetadata().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .map(entry -> secretFromJson(new JsonObject(entry.getValue())))
                .sorted(Comparator.comparing(SecretReference::secretReferenceId))
                .toList();
    }

    public List<SecurityEvent> listEvents(String tenantId) {
        String prefix = EVENT_PREFIX + safe(tenantId) + ".";
        return stateStore.getSystemMetadata().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .map(entry -> eventFromJson(new JsonObject(entry.getValue())))
                .sorted(Comparator.comparing(SecurityEvent::timestamp))
                .toList();
    }

    public boolean secretIsReferenced(String tenantId, String secretReferenceId) {
        return listConnections(tenantId).stream()
                .anyMatch(connection -> connection.secretReferenceId().equals(secretReferenceId));
    }

    public static String encode(ServiceConnection value) { return connectionToJson(value).encode(); }
    public static String encode(SecretReference value) { return secretToJson(value).encode(); }
    public static String encode(SecurityEvent value) { return eventToJson(value).encode(); }

    public static JsonObject connectionToJson(ServiceConnection connection) {
        ServiceConnection.TrustPolicy trust = connection.trustPolicy();
        ServiceConnection.EgressPolicy egress = connection.egressPolicy();
        return new JsonObject()
                .put("serviceConnectionId", connection.serviceConnectionId())
                .put("tenantId", connection.tenantId())
                .put("protocol", connection.protocol().name())
                .put("endpoint", connection.endpoint().toString())
                .put("networkZone", connection.networkZone())
                .put("allowedPaths", array(connection.allowedPaths()))
                .put("allowedDirections", array(connection.allowedDirections().stream().map(Enum::name).toList()))
                .put("allowedAgentPools", array(connection.allowedAgentPools()))
                .put("owner", connection.owner())
                .put("environment", connection.environment())
                .put("classification", connection.classification())
                .put("secretReferenceId", connection.secretReferenceId())
                .put("serviceIdentity", connection.serviceIdentity())
                .put("authenticationType", connection.authenticationType().name())
                .put("trustPolicy", new JsonObject()
                        .put("tlsRequired", trust.tlsRequired())
                        .put("hostnameVerification", trust.hostnameVerification())
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
                .put("policyVersion", connection.policyVersion())
                .put("status", connection.status().name())
                .put("createdAt", connection.createdAt().toString())
                .put("updatedAt", connection.updatedAt().toString());
    }

    public static ServiceConnection connectionFromJson(JsonObject json) {
        JsonObject trust = json.getJsonObject("trustPolicy", new JsonObject());
        JsonObject egress = json.getJsonObject("egressPolicy", new JsonObject());
        return new ServiceConnection(
                json.getString("serviceConnectionId"), json.getString("tenantId"),
                ServiceConnection.Protocol.valueOf(json.getString("protocol").toUpperCase(Locale.ROOT)),
                URI.create(json.getString("endpoint")), json.getString("networkZone"),
                strings(json.getJsonArray("allowedPaths")),
                strings(json.getJsonArray("allowedDirections")).stream()
                        .map(value -> ServiceConnection.Direction.valueOf(value.toUpperCase(Locale.ROOT)))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                strings(json.getJsonArray("allowedAgentPools")), json.getString("owner"),
                json.getString("environment"), json.getString("classification"),
                json.getString("secretReferenceId"),
                json.getString("serviceIdentity"),
                ServiceConnection.AuthenticationType.valueOf(json.getString("authenticationType")),
                new ServiceConnection.TrustPolicy(
                        trust.getBoolean("tlsRequired", false),
                        trust.getBoolean("hostnameVerification", false),
                        strings(trust.getJsonArray("approvedCaIds")),
                        strings(trust.getJsonArray("sshHostKeyFingerprints")),
                        trust.getString("minimumTlsVersion", "TLSv1.3"),
                        strings(trust.getJsonArray("tlsPeerFingerprints")),
                        trust.getBoolean("transportEncryptionRequired", trust.getBoolean("tlsRequired", false))),
                new ServiceConnection.EgressPolicy(
                        strings(egress.getJsonArray("allowedHostnames")),
                        strings(egress.getJsonArray("allowedCidrs")),
                        integers(egress.getJsonArray("allowedPorts")),
                        egress.getBoolean("allowRedirects", false),
                        egress.getBoolean("pinResolvedAddresses", true)),
                json.getInteger("policyVersion", 1),
                ServiceConnection.Status.valueOf(json.getString("status", "ACTIVE")),
                Instant.parse(json.getString("createdAt")), Instant.parse(json.getString("updatedAt")));
    }

    public static JsonObject secretToJson(SecretReference reference) {
        JsonObject json = new JsonObject()
                .put("secretReferenceId", reference.secretReferenceId())
                .put("tenantId", reference.tenantId())
                .put("provider", reference.provider())
                .put("path", reference.path())
                .put("key", reference.key())
                .put("version", reference.version())
                .put("status", reference.status().name());
        if (reference.expiresAt() != null) json.put("expiresAt", reference.expiresAt().toString());
        if (reference.lastRotatedAt() != null) json.put("lastRotatedAt", reference.lastRotatedAt().toString());
        return json;
    }

    public static SecretReference secretFromJson(JsonObject json) {
        return new SecretReference(json.getString("secretReferenceId"), json.getString("tenantId"),
                json.getString("provider"), json.getString("path"), json.getString("key"),
                json.getString("version"), SecretReference.Status.valueOf(json.getString("status")),
                instant(json.getString("expiresAt")), instant(json.getString("lastRotatedAt")));
    }

    public static JsonObject eventToJson(SecurityEvent event) {
        JsonObject json = new JsonObject()
                .put("eventId", event.eventId()).put("tenantId", event.tenantId())
                .put("eventType", event.eventType()).put("resourceType", event.resourceType())
                .put("resourceId", event.resourceId()).put("outcome", event.outcome())
                .put("reasonCode", event.reasonCode()).put("timestamp", event.timestamp().toString());
        if (event.policyVersion() != null) json.put("policyVersion", event.policyVersion());
        return json;
    }

    private static SecurityEvent eventFromJson(JsonObject json) {
        return new SecurityEvent(json.getString("eventId"), json.getString("tenantId"),
                json.getString("eventType"), json.getString("resourceType"), json.getString("resourceId"),
                json.getString("outcome"), json.getString("reasonCode"), json.getInteger("policyVersion"),
                Instant.parse(json.getString("timestamp")));
    }

    private static JsonArray array(java.util.Collection<?> values) {
        return new JsonArray(values.stream().sorted(Comparator.comparing(Object::toString)).toList());
    }
    private static Set<String> strings(JsonArray values) {
        if (values == null) return Set.of();
        return values.stream().map(String::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private static Set<Integer> integers(JsonArray values) {
        if (values == null) return Set.of();
        return values.stream().map(value -> ((Number) value).intValue())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private static Instant instant(String value) { return value == null ? null : Instant.parse(value); }
    private static String safe(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new IllegalArgumentException("Resource identifiers must use letters, digits, dot, underscore, or dash");
        }
        return value;
    }

    /** Redacted lifecycle and policy decision evidence stored through Raft. */
    public record SecurityEvent(String eventId, String tenantId, String eventType, String resourceType,
                                String resourceId, String outcome, String reasonCode, Integer policyVersion,
                                Instant timestamp) { }
}
