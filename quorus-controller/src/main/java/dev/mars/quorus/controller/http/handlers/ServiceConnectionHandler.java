/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.http.handlers;

import dev.mars.quorus.connection.ConnectionAccessRequest;
import dev.mars.quorus.connection.ConnectionPolicyEnforcer;
import dev.mars.quorus.connection.ConnectionPolicyException;
import dev.mars.quorus.connection.HostResolver;
import dev.mars.quorus.controller.http.ControllerConnectionAuthorizer;
import dev.mars.quorus.connection.SecretReference;
import dev.mars.quorus.connection.ServiceConnection;
import dev.mars.quorus.controller.http.ErrorCode;
import dev.mars.quorus.controller.http.QuorusApiException;
import dev.mars.quorus.controller.http.ServiceConnectionRouteProbe;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.security.SecurityContext;
import dev.mars.quorus.controller.state.CommandResult;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.ServiceConnectionRegistry;
import dev.mars.quorus.controller.state.SystemMetadataCommand;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.net.URI;
import java.time.Instant;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Phase 4 REST authority for service connections, secret references, validation, and events. */
public final class ServiceConnectionHandler {
    private static final Set<String> FORBIDDEN_SECRET_FIELDS = Set.of(
            "secretvalue", "value", "password", "token", "privatekey", "credential", "credentials");

    private final RaftNode raftNode;
    private final ServiceConnectionRegistry registry;

    private final ControllerConnectionAuthorizer authorizer;

    public ServiceConnectionHandler(RaftNode raftNode, QuorusStateStore stateStore) {
        this(raftNode, stateStore, HostResolver.system());
    }

    public ServiceConnectionHandler(RaftNode raftNode, QuorusStateStore stateStore, HostResolver hostResolver) {
        this(raftNode, stateStore, new ControllerConnectionAuthorizer(hostResolver));
    }

    public ServiceConnectionHandler(RaftNode raftNode, QuorusStateStore stateStore, ControllerConnectionAuthorizer authorizer) {
        this.raftNode = raftNode;
        this.registry = new ServiceConnectionRegistry(stateStore);
        this.authorizer = java.util.Objects.requireNonNull(authorizer);
    }

    public Handler<RoutingContext> createSecret() {
        return ctx -> execute(ctx, () -> {
            JsonObject body = requireBody(ctx);
            if (containsForbiddenSecretField(body)) {
                recordEvent(ctx, tenant(ctx, body), "SECRET_REFERENCE_REJECTED", "SECRET_REFERENCE",
                        body.getString("secretReferenceId", "unknown"), "DENIED", "Q-SECRET-VALUE-FORBIDDEN", null)
                        .onComplete(ignored -> ctx.fail(validation(
                                "Secret values are forbidden; submit only an opaque external reference")));
                return;
            }
            SecretReference reference = parseSecret(body, null, tenant(ctx, body));
            if (registry.findSecret(reference.tenantId(), reference.secretReferenceId()) != null) {
                throw conflict("Secret reference already exists");
            }
            submit(new SystemMetadataCommand.Set(registry.secretKey(reference.tenantId(), reference.secretReferenceId()),
                    ServiceConnectionRegistry.encode(reference)))
                    .compose(result -> recordEvent(ctx, reference.tenantId(), "SECRET_REFERENCE_CREATED",
                            "SECRET_REFERENCE", reference.secretReferenceId(), "SUCCESS", "Q-SECRET-CREATED", null))
                    .onSuccess(result -> created(ctx, ServiceConnectionRegistry.secretToJson(reference)))
                    .onFailure(ctx::fail);
        });
    }

    public Handler<RoutingContext> listSecrets() {
        return ctx -> execute(ctx, () -> {
            String tenant = tenant(ctx, null);
            JsonArray values = new JsonArray(registry.listSecrets(tenant).stream()
                    .map(ServiceConnectionRegistry::secretToJson).toList());
            ctx.json(new JsonObject().put("secretReferences", values).put("total", values.size()));
        });
    }

    public Handler<RoutingContext> getSecret() {
        return ctx -> execute(ctx, () -> ctx.json(ServiceConnectionRegistry.secretToJson(
                requireSecret(tenant(ctx, null), ctx.pathParam("secretReferenceId")))));
    }

    public Handler<RoutingContext> updateSecret() {
        return ctx -> execute(ctx, () -> {
            JsonObject body = requireBody(ctx);
            if (containsForbiddenSecretField(body)) throw validation("Secret values are forbidden");
            String tenant = tenant(ctx, body);
            String id = ctx.pathParam("secretReferenceId");
            SecretReference existing = requireSecret(tenant, id);
            body.put("secretReferenceId", id).put("tenantId", tenant);
            SecretReference updated = parseSecret(body, existing, tenant);
            submit(new SystemMetadataCommand.Set(registry.secretKey(tenant, id),
                    ServiceConnectionRegistry.encode(updated)))
                    .compose(result -> recordEvent(ctx, tenant, eventForSecretChange(existing, updated),
                            "SECRET_REFERENCE", id, "SUCCESS", "Q-SECRET-UPDATED", null))
                    .onSuccess(result -> ctx.json(ServiceConnectionRegistry.secretToJson(updated)))
                    .onFailure(ctx::fail);
        });
    }

    public Handler<RoutingContext> deleteSecret() {
        return ctx -> execute(ctx, () -> {
            String tenant = tenant(ctx, null);
            String id = ctx.pathParam("secretReferenceId");
            requireSecret(tenant, id);
            if (registry.secretIsReferenced(tenant, id)) throw conflict("Secret reference is in use");
            submit(new SystemMetadataCommand.Delete(registry.secretKey(tenant, id)))
                    .compose(result -> recordEvent(ctx, tenant, "SECRET_REFERENCE_DELETED",
                            "SECRET_REFERENCE", id, "SUCCESS", "Q-SECRET-DELETED", null))
                    .onSuccess(result -> ctx.json(new JsonObject().put("secretReferenceId", id).put("deleted", true)))
                    .onFailure(ctx::fail);
        });
    }

    public Handler<RoutingContext> createConnection() {
        return ctx -> execute(ctx, () -> {
            JsonObject body = requireBody(ctx);
            ServiceConnection connection = parseConnection(body, null, null, tenant(ctx, body));
            if (registry.findConnection(connection.tenantId(), connection.serviceConnectionId()) != null) {
                throw conflict("Service connection already exists");
            }
            requireSecret(connection.tenantId(), connection.secretReferenceId());
            persistConnection(ctx, connection, true, false);
        });
    }

    public Handler<RoutingContext> listConnections() {
        return ctx -> execute(ctx, () -> {
            String tenant = tenant(ctx, null);
            JsonArray values = new JsonArray(registry.listConnections(tenant).stream()
                    .map(ServiceConnectionRegistry::connectionToJson).toList());
            ctx.json(new JsonObject().put("serviceConnections", values).put("total", values.size()));
        });
    }

    public Handler<RoutingContext> getConnection() {
        return ctx -> execute(ctx, () -> ctx.json(ServiceConnectionRegistry.connectionToJson(
                requireConnection(tenant(ctx, null), ctx.pathParam("serviceConnectionId")))));
    }

    public Handler<RoutingContext> updateConnection() {
        return ctx -> execute(ctx, () -> {
            JsonObject body = requireBody(ctx);
            String tenant = tenant(ctx, body);
            String id = ctx.pathParam("serviceConnectionId");
            ServiceConnection existing = requireConnection(tenant, id);
            ServiceConnection updated = parseConnection(body, existing, id, tenant);
            requireSecret(tenant, updated.secretReferenceId());
            persistConnection(ctx, updated, false, !existing.trustPolicy().equals(updated.trustPolicy()));
        });
    }

    public Handler<RoutingContext> deleteConnection() {
        return ctx -> execute(ctx, () -> {
            String tenant = tenant(ctx, null);
            String id = ctx.pathParam("serviceConnectionId");
            requireConnection(tenant, id);
            submit(new SystemMetadataCommand.Delete(registry.connectionKey(tenant, id)))
                    .compose(result -> recordEvent(ctx, tenant, "SERVICE_CONNECTION_DELETED",
                            "SERVICE_CONNECTION", id, "SUCCESS", "Q-CONNECTION-DELETED", null))
                    .onSuccess(result -> ctx.json(new JsonObject().put("serviceConnectionId", id).put("deleted", true)))
                    .onFailure(ctx::fail);
        });
    }

    public Handler<RoutingContext> validateConnection() {
        return ctx -> execute(ctx, () -> {
            JsonObject body = requireBody(ctx);
            String tenant = tenant(ctx, body);
            ServiceConnection connection = requireConnection(tenant, ctx.pathParam("serviceConnectionId"));
            ConnectionAccessRequest request = new ConnectionAccessRequest(tenant,
                    body.getString("remotePath"), ServiceConnection.Direction.valueOf(
                    body.getString("direction").toUpperCase(Locale.ROOT)), body.getString("agentPool"), null);
            authorizer.authorize(ctx.vertx(), connection, request).onSuccess(authorization -> execute(ctx, () -> {
                if (!connection.equals(registry.findConnection(tenant, connection.serviceConnectionId()))) {
                    throw new QuorusApiException(ErrorCode.CONFLICT,
                            "Service connection changed during DNS authorization; retry");
                }
                JsonArray stages = new JsonArray()
                        .add(stage("POLICY", "PASS", "Connection policy approved"))
                        .add(new JsonObject().put("stage", "DNS").put("status", "PASS")
                                .put("addressCount", authorization.resolvedAddresses().size())
                                .put("detail", "All resolved addresses are within approved CIDRs"));
                boolean probeNetwork = body.getBoolean("probeNetwork", false);
                if (probeNetwork) {
                    long timeoutMillis = Math.min(10_000L, Math.max(100L,
                            body.getLong("probeTimeoutMillis", 3_000L)));
                    ctx.vertx().executeBlocking(() -> ServiceConnectionRouteProbe.probe(
                                    authorization, Duration.ofMillis(timeoutMillis)), false)
                            .onSuccess(probe -> {
                                stages.add(new JsonObject().put("stage", "ROUTE").put("status", probe.status())
                                        .put("latencyMillis", probe.latencyMillis())
                                        .put("detail", "TCP route reached through an approved address"));
                                appendNonRouteStages(stages, true);
                                respondValidation(ctx, tenant, connection, authorization, stages, "ROUTE_VERIFIED",
                                        "Q-CONNECTION-ROUTE-VERIFIED");
                            })
                            .onFailure(error -> recordEvent(ctx, tenant,
                                    "SERVICE_CONNECTION_VALIDATION_FAILED", "SERVICE_CONNECTION",
                                    connection.serviceConnectionId(), "DENIED", "Q-CONNECTION-ROUTE-FAILED",
                                    connection.policyVersion()).onComplete(ignored -> ctx.fail(validation(
                                            "Active route probe failed: " + error.getMessage()))));
                    return;
                }
                stages.add(stage("ROUTE", "NOT_EXECUTED", "Set probeNetwork=true for active route probing"));
                appendNonRouteStages(stages, false);
                respondValidation(ctx, tenant, connection, authorization, stages, "POLICY_APPROVED",
                        "Q-CONNECTION-POLICY-APPROVED");
            })).onFailure(error -> {
                if (error instanceof ConnectionPolicyException e) {
                    recordEvent(ctx, tenant, "SERVICE_CONNECTION_VALIDATION_FAILED", "SERVICE_CONNECTION",
                            connection.serviceConnectionId(), "DENIED", e.decisionCode(), connection.policyVersion())
                            .onComplete(ignored -> ctx.fail(validation(e.getMessage())));
                } else {
                    ctx.fail(error);
                }
            });
        });
    }

    private static void appendNonRouteStages(JsonArray stages, boolean routeProbed) {
        stages.add(stage("NEGOTIATION", "NOT_EXECUTED", routeProbed
                        ? "Route probing does not open an application session"
                        : "No application session was opened"))
                .add(stage("IDENTITY", "POLICY_CONFIGURED", "Peer identity requirements are configured"))
                .add(stage("AUTHENTICATION", "NOT_EXECUTED",
                        "Controller validation never retrieves an agent-side secret"))
                .add(stage("AUTHORIZATION", "POLICY_CONFIGURED",
                        "Path, direction, agent-pool, and network-zone scopes approved"));
    }

    private void respondValidation(RoutingContext ctx, String tenant, ServiceConnection connection,
                                   ConnectionPolicyEnforcer.ConnectionAuthorization authorization,
                                   JsonArray stages, String status, String reasonCode) {
        recordEvent(ctx, tenant, "SERVICE_CONNECTION_VALIDATED", "SERVICE_CONNECTION",
                connection.serviceConnectionId(), "SUCCESS", reasonCode, connection.policyVersion())
                .onSuccess(result -> ctx.json(new JsonObject().put("status", status)
                        .put("serviceConnectionId", connection.serviceConnectionId())
                        .put("policyVersion", connection.policyVersion())
                        .put("policyDigest", authorization.policyDigest()).put("stages", stages)))
                .onFailure(ctx::fail);
    }

    public Handler<RoutingContext> listEvents() {
        return ctx -> execute(ctx, () -> {
            String tenant = tenant(ctx, null);
            int limit;
            try {
                limit = Integer.parseInt(ctx.request().getParam("limit", "100"));
            } catch (NumberFormatException error) {
                throw validation("limit must be an integer");
            }
            if (limit < 1 || limit > 1_000) throw validation("limit must be between 1 and 1000");
            ServiceConnectionRegistry.EventPage page;
            try {
                page = registry.listEvents(tenant, limit, ctx.request().getParam("cursor"));
            } catch (IllegalArgumentException error) {
                throw validation(error.getMessage());
            }
            JsonArray events = new JsonArray(page.events().stream()
                    .map(ServiceConnectionRegistry::eventToJson).toList());
            ctx.json(new JsonObject().put("events", events).put("total", events.size())
                    .put("nextCursor", page.nextCursor()));
        });
    }

    public Future<CommandResult<?>> recordEvent(RoutingContext ctx, String tenant, String eventType,
                                                 String resourceType, String resourceId, String outcome,
                                                 String reasonCode, Integer policyVersion) {
        Instant now = Instant.now();
        ServiceConnectionRegistry.SecurityEvent event = new ServiceConnectionRegistry.SecurityEvent(
                UUID.randomUUID().toString(), tenant, eventType, resourceType, resourceId,
                outcome, reasonCode, policyVersion, now);
        return submit(new SystemMetadataCommand.Set(registry.eventKey(tenant, now),
                ServiceConnectionRegistry.encode(event)));
    }

    private void persistConnection(RoutingContext ctx, ServiceConnection connection, boolean create,
                                   boolean trustChanged) {
        submit(new SystemMetadataCommand.Set(registry.connectionKey(connection.tenantId(),
                connection.serviceConnectionId()), ServiceConnectionRegistry.encode(connection)))
                .compose(result -> recordEvent(ctx, connection.tenantId(),
                        create ? "SERVICE_CONNECTION_CREATED" : "SERVICE_CONNECTION_UPDATED",
                        "SERVICE_CONNECTION", connection.serviceConnectionId(), "SUCCESS",
                        create ? "Q-CONNECTION-CREATED" : "Q-CONNECTION-UPDATED", connection.policyVersion()))
                .compose(result -> trustChanged ? recordEvent(ctx, connection.tenantId(),
                        "SERVICE_TRUST_CHANGED", "SERVICE_CONNECTION", connection.serviceConnectionId(),
                        "SUCCESS", "Q-CONNECTION-TRUST-CHANGED", connection.policyVersion())
                        : Future.succeededFuture(result))
                .onSuccess(result -> {
                    if (create) ctx.response().setStatusCode(201);
                    ctx.json(ServiceConnectionRegistry.connectionToJson(connection));
                }).onFailure(ctx::fail);
    }

    private Future<CommandResult<?>> submit(SystemMetadataCommand command) {
        return raftNode.submitCommand(command).map(result -> {
            if (result instanceof CommandResult.Rejected<?> rejected) {
                throw validation(rejected.message());
            }
            return result;
        });
    }

    private ServiceConnection parseConnection(JsonObject body, ServiceConnection existing, String forcedId,
                                              String tenant) {
        String id = forcedId == null ? body.getString("serviceConnectionId") : forcedId;
        JsonObject trust = body.getJsonObject("trustPolicy",
                existing == null ? new JsonObject() : ServiceConnectionRegistry.connectionToJson(existing)
                        .getJsonObject("trustPolicy"));
        JsonObject egress = body.getJsonObject("egressPolicy",
                existing == null ? new JsonObject() : ServiceConnectionRegistry.connectionToJson(existing)
                        .getJsonObject("egressPolicy"));
        Instant now = Instant.now();
        return new ServiceConnection(id, tenant,
                ServiceConnection.Protocol.valueOf(value(body, "protocol", existing == null ? null : existing.protocol().name())),
                URI.create(value(body, "endpoint", existing == null ? null : existing.endpoint().toString())),
                value(body, "networkZone", existing == null ? null : existing.networkZone()),
                strings(body.getJsonArray("allowedPaths"), existing == null ? Set.of() : existing.allowedPaths()),
                strings(body.getJsonArray("allowedDirections"), existing == null ? Set.of() : existing.allowedDirections()
                        .stream().map(Enum::name).collect(Collectors.toSet())).stream()
                        .map(ServiceConnection.Direction::valueOf).collect(Collectors.toUnmodifiableSet()),
                strings(body.getJsonArray("allowedAgentPools"), existing == null ? Set.of() : existing.allowedAgentPools()),
                value(body, "owner", existing == null ? null : existing.owner()),
                value(body, "environment", existing == null ? null : existing.environment()),
                value(body, "classification", existing == null ? null : existing.classification()),
                value(body, "secretReferenceId", existing == null ? null : existing.secretReferenceId()),
                value(body, "serviceIdentity", existing == null ? null : existing.serviceIdentity()),
                ServiceConnection.AuthenticationType.valueOf(value(body, "authenticationType",
                        existing == null ? null : existing.authenticationType().name())),
                new ServiceConnection.TrustPolicy(
                        trust.getBoolean("tlsRequired", existing != null && existing.trustPolicy().tlsRequired()),
                        trust.getBoolean("hostnameVerification", existing != null && existing.trustPolicy().hostnameVerification()),
                        strings(trust.getJsonArray("approvedCaIds"), existing == null
                                ? Set.of() : existing.trustPolicy().approvedCaIds()),
                        strings(trust.getJsonArray("sshHostKeyFingerprints"), existing == null
                                ? Set.of() : existing.trustPolicy().sshHostKeyFingerprints()),
                        trust.getString("minimumTlsVersion", existing == null
                                ? "TLSv1.3" : existing.trustPolicy().minimumTlsVersion()),
                        strings(trust.getJsonArray("tlsPeerFingerprints"), existing == null
                                ? Set.of() : existing.trustPolicy().tlsPeerFingerprints()),
                        trust.getBoolean("transportEncryptionRequired", existing != null
                                && existing.trustPolicy().transportEncryptionRequired())),
                new ServiceConnection.EgressPolicy(
                        strings(egress.getJsonArray("allowedHostnames"), Set.of()),
                        strings(egress.getJsonArray("allowedCidrs"), Set.of()),
                        integers(egress.getJsonArray("allowedPorts"), Set.of()),
                        egress.getBoolean("allowRedirects", false),
                        egress.getBoolean("pinResolvedAddresses", true)),
                existing == null ? 1 : existing.policyVersion() + 1,
                ServiceConnection.Status.valueOf(value(body, "status", existing == null ? "ACTIVE" : existing.status().name())),
                existing == null ? now : existing.createdAt(), now);
    }

    private SecretReference parseSecret(JsonObject body, SecretReference existing, String tenant) {
        return new SecretReference(body.getString("secretReferenceId"), tenant,
                value(body, "provider", existing == null ? null : existing.provider()),
                value(body, "path", existing == null ? null : existing.path()),
                value(body, "key", existing == null ? null : existing.key()),
                value(body, "version", existing == null ? null : existing.version()),
                SecretReference.Status.valueOf(value(body, "status", existing == null ? "ACTIVE" : existing.status().name())),
                instant(body.getString("expiresAt"), existing == null ? null : existing.expiresAt()),
                instant(body.getString("lastRotatedAt"), existing == null ? null : existing.lastRotatedAt()));
    }

    private String tenant(RoutingContext ctx, JsonObject body) {
        String requested = body != null ? body.getString("tenantId") : ctx == null ? null : ctx.request().getParam("tenantId");
        if (ctx != null) requested = SecurityContext.trustedTenant(ctx, requested);
        if (requested == null || requested.isBlank()) throw validation("tenantId is required");
        return requested;
    }

    private ServiceConnection requireConnection(String tenant, String id) {
        ServiceConnection value = registry.findConnection(tenant, id);
        if (value == null) throw new QuorusApiException(ErrorCode.NOT_FOUND, "Service connection not found");
        return value;
    }
    private SecretReference requireSecret(String tenant, String id) {
        SecretReference value = registry.findSecret(tenant, id);
        if (value == null) throw new QuorusApiException(ErrorCode.NOT_FOUND, "Secret reference not found");
        return value;
    }

    private static JsonObject requireBody(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) throw validation("Request body is required");
        return body;
    }
    private static void execute(RoutingContext ctx, Runnable action) {
        try { action.run(); } catch (Exception e) { ctx.fail(e); }
    }
    private static void created(RoutingContext ctx, JsonObject body) {
        ctx.response().setStatusCode(201); ctx.json(body);
    }
    private static QuorusApiException validation(String message) {
        return new QuorusApiException(ErrorCode.VALIDATION_ERROR, message);
    }
    private static QuorusApiException conflict(String message) {
        return new QuorusApiException(ErrorCode.CONFLICT, message);
    }
    private static String value(JsonObject body, String name, String fallback) {
        String value = body.getString(name, fallback);
        if (value == null) throw validation(name + " is required");
        return value.toUpperCase(Locale.ROOT).equals(value) || name.equals("status") || name.equals("protocol")
                ? value.toUpperCase(Locale.ROOT) : value;
    }
    private static Set<String> strings(JsonArray values, Set<String> fallback) {
        if (values == null) return fallback;
        return values.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }
    private static Set<Integer> integers(JsonArray values, Set<Integer> fallback) {
        if (values == null) return fallback;
        return values.stream().map(value -> ((Number) value).intValue()).collect(Collectors.toUnmodifiableSet());
    }
    private static Instant instant(String value, Instant fallback) { return value == null ? fallback : Instant.parse(value); }
    private static JsonObject stage(String name, String status, String detail) {
        return new JsonObject().put("stage", name).put("status", status).put("detail", detail);
    }
    private static boolean containsForbiddenSecretField(Object value) {
        if (value instanceof JsonObject object) {
            return object.stream().anyMatch(entry -> FORBIDDEN_SECRET_FIELDS.contains(
                    entry.getKey().replace("_", "").replace("-", "").toLowerCase(Locale.ROOT))
                    || containsForbiddenSecretField(entry.getValue()));
        }
        if (value instanceof JsonArray array) return array.stream().anyMatch(ServiceConnectionHandler::containsForbiddenSecretField);
        return false;
    }
    private static String eventForSecretChange(SecretReference before, SecretReference after) {
        if (after.status() == SecretReference.Status.REVOKED) return "SECRET_REFERENCE_REVOKED";
        if (!before.version().equals(after.version())) return "SECRET_REFERENCE_ROTATED";
        if (after.status() == SecretReference.Status.EXPIRED) return "SECRET_REFERENCE_EXPIRED";
        return "SECRET_REFERENCE_UPDATED";
    }
}
