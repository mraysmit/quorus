/* Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache-2.0. */
package dev.mars.quorus.connection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Canonical JSON contract shared by controllers and agents for governed connection authority.
 *
 * <p>The API is JSON text, so callers need no JSON library of their own (RT-03e). The controller's
 * registry stores this text, so the encoding is stable: fields in a fixed order, sets sorted, and
 * non-ASCII characters written as UTF-8 rather than escaped. {@code ServiceConnectionJsonCodecTest}
 * pins it byte for byte.
 */
public final class ServiceConnectionJsonCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ServiceConnectionJsonCodec() { }

    /** Encodes a service connection as canonical JSON text. */
    public static String encodeConnection(ServiceConnection connection) {
        ServiceConnection.TrustPolicy trust = connection.trustPolicy();
        ServiceConnection.EgressPolicy egress = connection.egressPolicy();
        ObjectNode json = MAPPER.createObjectNode()
                .put("serviceConnectionId", connection.serviceConnectionId()).put("tenantId", connection.tenantId())
                .put("protocol", connection.protocol().name()).put("endpoint", connection.endpoint().toString())
                .put("networkZone", connection.networkZone());
        json.set("allowedPaths", sorted(connection.allowedPaths()));
        json.set("allowedDirections", sorted(connection.allowedDirections().stream().map(Enum::name).toList()));
        json.set("allowedAgentPools", sorted(connection.allowedAgentPools()));
        json.put("owner", connection.owner()).put("environment", connection.environment())
                .put("classification", connection.classification())
                .put("secretReferenceId", connection.secretReferenceId())
                .put("serviceIdentity", connection.serviceIdentity())
                .put("authenticationType", connection.authenticationType().name());
        ObjectNode trustJson = json.putObject("trustPolicy")
                .put("tlsRequired", trust.tlsRequired()).put("hostnameVerification", trust.hostnameVerification());
        trustJson.set("approvedCaIds", sorted(trust.approvedCaIds()));
        trustJson.set("sshHostKeyFingerprints", sorted(trust.sshHostKeyFingerprints()));
        trustJson.put("minimumTlsVersion", trust.minimumTlsVersion());
        trustJson.set("tlsPeerFingerprints", sorted(trust.tlsPeerFingerprints()));
        trustJson.put("transportEncryptionRequired", trust.transportEncryptionRequired());
        ObjectNode egressJson = json.putObject("egressPolicy");
        egressJson.set("allowedHostnames", sorted(egress.allowedHostnames()));
        egressJson.set("allowedCidrs", sorted(egress.allowedCidrs()));
        ArrayNode ports = egressJson.putArray("allowedPorts");
        egress.allowedPorts().stream().sorted().forEach(ports::add);
        egressJson.put("allowRedirects", egress.allowRedirects())
                .put("pinResolvedAddresses", egress.pinResolvedAddresses());
        json.put("policyVersion", connection.policyVersion()).put("status", connection.status().name())
                .put("createdAt", connection.createdAt().toString()).put("updatedAt", connection.updatedAt().toString());
        return write(json);
    }

    /** Decodes canonical JSON text; optional fields take their defaults and enum values are case-insensitive. */
    public static ServiceConnection decodeConnection(String text) {
        JsonNode json = read(text);
        JsonNode trust = json.path("trustPolicy");
        JsonNode egress = json.path("egressPolicy");
        boolean tlsRequired = trust.path("tlsRequired").asBoolean(false);
        return new ServiceConnection(text(json, "serviceConnectionId"), text(json, "tenantId"),
                enumValue(ServiceConnection.Protocol.class, text(json, "protocol")),
                URI.create(text(json, "endpoint")), text(json, "networkZone"),
                set(json.path("allowedPaths"), JsonNode::asText),
                set(json.path("allowedDirections"), node -> enumValue(ServiceConnection.Direction.class, node.asText())),
                set(json.path("allowedAgentPools"), JsonNode::asText), text(json, "owner"), text(json, "environment"),
                text(json, "classification"), text(json, "secretReferenceId"), text(json, "serviceIdentity"),
                enumValue(ServiceConnection.AuthenticationType.class, text(json, "authenticationType")),
                new ServiceConnection.TrustPolicy(tlsRequired, trust.path("hostnameVerification").asBoolean(false),
                        set(trust.path("approvedCaIds"), JsonNode::asText),
                        set(trust.path("sshHostKeyFingerprints"), JsonNode::asText),
                        trust.path("minimumTlsVersion").asText("TLSv1.3"),
                        set(trust.path("tlsPeerFingerprints"), JsonNode::asText),
                        trust.path("transportEncryptionRequired").asBoolean(tlsRequired)),
                new ServiceConnection.EgressPolicy(set(egress.path("allowedHostnames"), JsonNode::asText),
                        set(egress.path("allowedCidrs"), JsonNode::asText),
                        set(egress.path("allowedPorts"), JsonNode::asInt),
                        egress.path("allowRedirects").asBoolean(false),
                        egress.path("pinResolvedAddresses").asBoolean(true)),
                json.path("policyVersion").asInt(1),
                enumValue(ServiceConnection.Status.class, json.path("status").asText("ACTIVE")),
                Instant.parse(text(json, "createdAt")), Instant.parse(text(json, "updatedAt")));
    }

    /** Encodes a secret reference as canonical JSON text; unset expiry and rotation times are omitted. */
    public static String encodeSecret(SecretReference reference) {
        ObjectNode json = MAPPER.createObjectNode()
                .put("secretReferenceId", reference.secretReferenceId()).put("tenantId", reference.tenantId())
                .put("provider", reference.provider()).put("path", reference.path()).put("key", reference.key())
                .put("version", reference.version()).put("status", reference.status().name());
        if (reference.expiresAt() != null) json.put("expiresAt", reference.expiresAt().toString());
        if (reference.lastRotatedAt() != null) json.put("lastRotatedAt", reference.lastRotatedAt().toString());
        return write(json);
    }

    /** Decodes canonical JSON text for a secret reference. */
    public static SecretReference decodeSecret(String text) {
        JsonNode json = read(text);
        return new SecretReference(text(json, "secretReferenceId"), text(json, "tenantId"), text(json, "provider"),
                text(json, "path"), text(json, "key"), text(json, "version"),
                enumValue(SecretReference.Status.class, json.path("status").asText("ACTIVE")),
                instant(json, "expiresAt"), instant(json, "lastRotatedAt"));
    }

    private static ArrayNode sorted(Collection<String> values) {
        ArrayNode array = MAPPER.createArrayNode();
        values.stream().sorted(Comparator.naturalOrder()).forEach(array::add);
        return array;
    }

    private static <T> Set<T> set(JsonNode array, Function<JsonNode, T> element) {
        return array.isArray()
                ? StreamSupport.stream(array.spliterator(), false).map(element).collect(Collectors.toUnmodifiableSet())
                : Set.of();
    }

    private static String text(JsonNode json, String field) {
        JsonNode value = json.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Instant instant(JsonNode json, String field) {
        String value = text(json, field);
        return value == null ? null : Instant.parse(value);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value == null) throw new IllegalArgumentException(type.getSimpleName() + " is required");
        return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    }

    private static String write(ObjectNode json) {
        try {
            return MAPPER.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Connection JSON could not be written", e);
        }
    }

    private static JsonNode read(String text) {
        try {
            JsonNode json = MAPPER.readTree(text);
            if (json == null || !json.isObject()) {
                throw new IllegalArgumentException("Connection JSON must be an object");
            }
            return json;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Connection JSON is malformed: " + e.getOriginalMessage(), e);
        }
    }
}
