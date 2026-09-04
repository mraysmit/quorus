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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Typed projection over namespaced Raft-replicated system metadata. */
public final class ServiceConnectionRegistry {
    private static final String VERSIONED_PREFIX = "phase4.v2.";
    private static final String SCHEMA_KEY = "phase4.registry.schema";
    private static final String OWNERSHIP_ERROR = "Registry ownership or migration conflict; operator review required";
    private final QuorusStateStore stateStore;

    public ServiceConnectionRegistry(QuorusStateStore stateStore) {
        this.stateStore = stateStore;
    }

    public String connectionKey(String tenantId, String id) {
        return key("service-connection", tenantId, id);
    }

    public String secretKey(String tenantId, String id) {
        return key("secret-reference", tenantId, id);
    }

    public String eventKey(String tenantId, Instant timestamp) {
        return key("security-event", tenantId, timestamp.toEpochMilli() + "." + UUID.randomUUID());
    }

    private static String key(String kind, String tenant, String id) {
        // ':' is excluded from both validated components, so dots cannot change ownership.
        return VERSIONED_PREFIX + kind + "." + safe(tenant) + ":" + safe(id);
    }

    public ServiceConnection findConnection(String tenantId, String id) {
        JsonObject value = find("service-connection", tenantId, id);
        return value == null ? null : connectionFromJson(value);
    }

    public SecretReference findSecret(String tenantId, String id) {
        JsonObject value = find("secret-reference", tenantId, id);
        return value == null ? null : secretFromJson(value);
    }

    private JsonObject find(String kind, String tenant, String id) {
        String address = key(kind, tenant, id);
        var metadata = stateStore.registryMetadataView();
        JsonObject result = null;
        for (String candidate : List.of(address, "phase4." + kind + "." + tenant + "." + id)) {
            String encoded = metadata.get(candidate);
            if (encoded == null) continue;
            JsonObject value = validated(candidate, encoded);
            if (!tenant.equals(value.getString("tenantId")) || !id.equals(resourceId(kind, value))) continue;
            if (result != null && !result.equals(value)) throw ownershipError();
            result = value;
        }
        return result;
    }

    public List<ServiceConnection> listConnections(String tenantId) {
        return rows("service-connection", tenantId).stream().map(ServiceConnectionRegistry::connectionFromJson)
                .sorted(Comparator.comparing(ServiceConnection::serviceConnectionId)).toList();
    }

    public List<SecretReference> listSecrets(String tenantId) {
        return rows("secret-reference", tenantId).stream().map(ServiceConnectionRegistry::secretFromJson)
                .sorted(Comparator.comparing(SecretReference::secretReferenceId)).toList();
    }

    public List<SecurityEvent> listEvents(String tenantId) {
        return rows("security-event", tenantId).stream().map(ServiceConnectionRegistry::eventFromJson)
                .sorted(Comparator.comparing(SecurityEvent::timestamp)).toList();
    }

    private List<JsonObject> rows(String kind, String tenant) {
        safe(tenant);
        String currentPrefix = VERSIONED_PREFIX + kind + "." + tenant + ":";
        String legacyPrefix = "phase4." + kind + "." + tenant + ".";
        var rows = stateStore.getSystemMetadata().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(currentPrefix) || entry.getKey().startsWith(legacyPrefix))
                .map(entry -> validated(entry.getKey(), entry.getValue()))
                .filter(value -> tenant.equals(value.getString("tenantId")))
                .toList();
        Map<String, JsonObject> unique = new LinkedHashMap<>();
        for (JsonObject row : rows) {
            String id = kind.equals("security-event") ? row.getString("eventId") : resourceId(kind, row);
            JsonObject prior = unique.putIfAbsent(id, row);
            if (prior != null && !prior.equals(row)) throw ownershipError();
        }
        return List.copyOf(unique.values());
    }

    static boolean versioned(String address) {
        return address.startsWith(VERSIONED_PREFIX);
    }

    static boolean legacy(String address) {
        return Stream.of("service-connection", "secret-reference", "security-event")
                .anyMatch(kind -> address.startsWith("phase4." + kind + "."));
    }

    static boolean forbiddenLegacyMutation(Map<String, String> metadata, String address) {
        return SCHEMA_KEY.equals(address) || (legacy(address) && metadata.containsKey(SCHEMA_KEY));
    }

    /**
     * Deterministic part of the replicated mutation. Validate everything before publishing
     * the migrated map; reads never migrate and a rejected command changes nothing.
     */
    static Map<String, String> prepareMutation(Map<String, String> metadata,
                                                 SystemMetadataCommand command) {
        String address = command.key();
        if (command instanceof SystemMetadataCommand.Set set) {
            validated(address, set.value());
        } else {
            validateAddress(address);
            if (metadata.containsKey(address)) validated(address, metadata.get(address));
        }
        if ("2".equals(metadata.get(SCHEMA_KEY))) return metadata;
        if (metadata.containsKey(SCHEMA_KEY)) throw ownershipError();
        Map<String, String> migrated = new ConcurrentHashMap<>(metadata);
        for (var entry : metadata.entrySet()) {
            if (versioned(entry.getKey())) validated(entry.getKey(), entry.getValue());
            if (!legacy(entry.getKey())) continue;
            JsonObject value = validated(entry.getKey(), entry.getValue());
            String kind = kind(entry.getKey());
            String id = kind.equals("security-event")
                    ? entry.getKey().substring(("phase4." + kind + "." + value.getString("tenantId") + ".").length())
                    : resourceId(kind, value);
            String target = key(kind, value.getString("tenantId"), id);
            String existing = migrated.putIfAbsent(target, entry.getValue());
            if (existing != null && !new JsonObject(existing).equals(value)) throw ownershipError();
            migrated.remove(entry.getKey());
        }
        migrated.put(SCHEMA_KEY, "2");
        return migrated;
    }

    private static String kind(String address) {
        String prefix = versioned(address) ? VERSIONED_PREFIX : "phase4.";
        for (String kind : List.of("service-connection", "secret-reference", "security-event")) {
            if (address.startsWith(prefix + kind + ".")) return kind;
        }
        throw ownershipError();
    }

    private static void validateAddress(String address) {
        String kind = kind(address);
        String tail = address.substring((VERSIONED_PREFIX + kind + ".").length());
        String[] parts = tail.split(":", -1);
        if (parts.length != 2) throw ownershipError();
        safe(parts[0]);
        safe(parts[1]);
    }

    private static JsonObject validated(String address, String encoded) {
        try {
            String kind = kind(address);
            JsonObject value = new JsonObject(encoded);
            String tenant = safe(value.getString("tenantId"));
            String id;
            if (kind.equals("security-event")) {
                SecurityEvent event = eventFromJson(value);
                safe(event.eventId());
                String prefix = (versioned(address) ? VERSIONED_PREFIX : "phase4.") + kind + "."
                        + tenant + (versioned(address) ? ":" : ".");
                if (!address.startsWith(prefix)) throw ownershipError();
                id = address.substring(prefix.length());
                String timePrefix = event.timestamp().toEpochMilli() + ".";
                if (!id.startsWith(timePrefix)) throw ownershipError();
                UUID.fromString(id.substring(timePrefix.length()));
            } else {
                id = safe(resourceId(kind, value));
                if (kind.equals("service-connection")) connectionFromJson(value);
                else secretFromJson(value);
            }
            String expected = versioned(address) ? key(kind, tenant, id)
                    : "phase4." + kind + "." + tenant + "." + id;
            if (!address.equals(expected)) throw ownershipError();
            return value;
        } catch (RuntimeException error) {
            // Do not propagate decoder messages that can contain secret-reference metadata.
            throw ownershipError();
        }
    }

    private static String resourceId(String kind, JsonObject value) {
        return value.getString(kind.equals("service-connection") ? "serviceConnectionId" : "secretReferenceId");
    }

    private static IllegalArgumentException ownershipError() {
        return new IllegalArgumentException(OWNERSHIP_ERROR);
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
