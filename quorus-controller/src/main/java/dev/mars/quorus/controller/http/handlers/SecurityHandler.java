/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.http.handlers;

import dev.mars.quorus.controller.security.AuthorizationDecision;
import dev.mars.quorus.controller.security.AuthorizationPolicyEngine;
import dev.mars.quorus.controller.security.AuthorizationRequest;
import dev.mars.quorus.controller.security.CertificateTrustState;
import dev.mars.quorus.controller.security.SecurityContext;
import dev.mars.quorus.controller.security.SecurityIdentity;
import dev.mars.quorus.controller.http.CorrelationIdHandler;
import dev.mars.quorus.controller.security.audit.AuditEvent;
import dev.mars.quorus.controller.security.audit.AuditSink;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** REST representation of the effective identity and explainable authorization decisions. */
public final class SecurityHandler {
    private final AuthorizationPolicyEngine policyEngine;
    private final CertificateTrustState trustState;
    private final AuditSink auditSink;

    public SecurityHandler(AuthorizationPolicyEngine policyEngine) {
        this(policyEngine, null, AuditSink.noOp());
    }

    public SecurityHandler(AuthorizationPolicyEngine policyEngine, CertificateTrustState trustState,
                           AuditSink auditSink) {
        this.policyEngine = policyEngine;
        this.trustState = trustState;
        this.auditSink = auditSink;
    }

    public Handler<RoutingContext> handleMe() {
        return context -> {
            SecurityIdentity identity = SecurityContext.identity(context);
            context.json(identityJson(identity));
        };
    }

    public Handler<RoutingContext> handleExplain() {
        return context -> {
            SecurityIdentity identity = SecurityContext.identity(context);
            String method = value(context, "method", "GET");
            String path = value(context, "path", "/api/v1/info");
            String scope = policyEngine.requiredScope(method, path);
            AuthorizationDecision decision = policyEngine.evaluate(identity,
                    new AuthorizationRequest(method, path, scope, context.queryParam("tenantId").stream().findFirst().orElse(null),
                            context.queryParam("environment").stream().findFirst().orElse(null), null,
                            context.queryParam("classification").stream().findFirst().orElse(null)));
            context.json(new JsonObject()
                    .put("allowed", decision.allowed())
                    .put("decisionCode", decision.code())
                    .put("reason", decision.reason())
                    .put("requiredScope", decision.requiredScope())
                    .put("effectiveIdentity", identityJson(identity)));
        };
    }

    public Handler<RoutingContext> handleCheck() {
        return context -> {
            JsonObject body = context.body().asJsonObject();
            if (body == null) body = new JsonObject();
            explain(context, SecurityContext.identity(context), body.getString("method", "GET"),
                    body.getString("path", "/api/v1/info"), body.getString("tenantId"),
                    body.getString("environment"), body.getString("classification"));
        };
    }

    public Handler<RoutingContext> handleTrustStatus() {
        return context -> {
            CertificateTrustState.Evaluation evaluation = trustState.evaluate(peerCertificate(context));
            CertificateTrustState.Snapshot snapshot = trustState.snapshot();
            context.json(new JsonObject()
                    .put("trustBundleVersion", snapshot.trustBundleVersion())
                    .put("trustBundleLoadedAt", snapshot.loadedAt().toString())
                    .put("revokedCertificateCount", snapshot.revokedCertificateSerials().size())
                    .put("certificateSubject", evaluation.certificateSubject())
                    .put("certificateExpiresAt", evaluation.certificateExpiresAt().toString())
                    .put("certificateSecondsRemaining", evaluation.certificateSecondsRemaining())
                    .put("expiryWarningThresholdSeconds", trustState.expiryWarningThreshold().toSeconds())
                    .put("expiryAlertState", evaluation.expiryAlertState().name()));
        };
    }

    public Handler<RoutingContext> handleRevocationUpdate() {
        return context -> {
            JsonObject body = context.body().asJsonObject();
            if (body == null) throw new IllegalArgumentException("Request body is required");
            String version = body.getString("trustBundleVersion");
            JsonArray serials = body.getJsonArray("revokedCertificateSerials", new JsonArray());
            Set<String> revoked = serials.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
            CertificateTrustState.Snapshot previous = trustState.snapshot();
            CertificateTrustState.Snapshot updated = trustState.update(version, revoked);
            SecurityIdentity identity = SecurityContext.identity(context);
            auditSink.append(new AuditEvent(Instant.now(), "SECURITY_CONFIGURATION_CHANGE", "SUCCESS",
                    "Q-TRUST-REVOCATIONS-UPDATED", identity.principalId(), identity.type().name(),
                    identity.tenantId(), identity.environment(), identity.certificateSubject(),
                    context.request().method().name(), context.request().path(),
                    CorrelationIdHandler.getRequestId(context), Map.of(
                            "previousTrustBundleVersion", previous.trustBundleVersion(),
                            "trustBundleVersion", updated.trustBundleVersion(),
                            "previousRevokedCertificateCount",
                            Integer.toString(previous.revokedCertificateSerials().size()),
                            "revokedCertificateCount",
                            Integer.toString(updated.revokedCertificateSerials().size()))));
            context.json(new JsonObject()
                    .put("trustBundleVersion", updated.trustBundleVersion())
                    .put("loadedAt", updated.loadedAt().toString())
                    .put("revokedCertificateCount", updated.revokedCertificateSerials().size()));
        };
    }

    private void explain(RoutingContext context, SecurityIdentity identity, String method, String path,
                         String tenantId, String environment, String classification) {
        String scope = policyEngine.requiredScope(method, path);
        AuthorizationDecision decision = policyEngine.evaluate(identity,
                new AuthorizationRequest(method, path, scope, tenantId, environment, null, classification));
        context.json(new JsonObject()
                .put("allowed", decision.allowed())
                .put("decisionCode", decision.code())
                .put("reason", decision.reason())
                .put("requiredScope", decision.requiredScope())
                .put("effectiveIdentity", identityJson(identity)));
    }

    private static JsonObject identityJson(SecurityIdentity identity) {
        return new JsonObject()
                .put("principalId", identity.principalId())
                .put("identityType", identity.type().name())
                .put("tenantId", identity.tenantId())
                .put("environment", identity.environment())
                .put("roles", new JsonArray(identity.roles().stream().map(Enum::name).sorted().toList()))
                .put("scopes", new JsonArray(identity.scopes().stream().sorted().toList()))
                .put("authenticatedAt", identity.authenticatedAt().toString())
                .put("expiresAt", identity.expiresAt() == null ? null : identity.expiresAt().toString())
                .put("elevationExpiresAt", identity.elevationExpiresAt() == null ? null : identity.elevationExpiresAt().toString());
    }

    private static String value(RoutingContext context, String name, String defaultValue) {
        return context.queryParam(name).stream().findFirst().orElse(defaultValue);
    }

    private static X509Certificate peerCertificate(RoutingContext context) {
        try {
            for (Certificate certificate : context.request().connection().peerCertificates()) {
                if (certificate instanceof X509Certificate x509) return x509;
            }
        } catch (javax.net.ssl.SSLPeerUnverifiedException exception) {
            throw new IllegalStateException("Verified client certificate is unavailable", exception);
        }
        throw new IllegalStateException("Verified client certificate is unavailable");
    }
}
