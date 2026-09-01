/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security;

import dev.mars.quorus.controller.http.CorrelationIdHandler;
import dev.mars.quorus.controller.http.ErrorCode;
import dev.mars.quorus.controller.http.QuorusApiException;
import dev.mars.quorus.controller.security.audit.AuditEvent;
import dev.mars.quorus.controller.security.audit.AuditSink;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

import java.time.Instant;

/** Applies the canonical policy engine to every protected HTTP request. */
public final class AuthorizationHandler implements Handler<RoutingContext> {
    private final SecurityConfig config;
    private final AuthorizationPolicyEngine policyEngine;
    private final AuditSink auditSink;

    public AuthorizationHandler(SecurityConfig config, AuthorizationPolicyEngine policyEngine, AuditSink auditSink) {
        this.config = config;
        this.policyEngine = policyEngine;
        this.auditSink = auditSink;
    }

    @Override
    public void handle(RoutingContext context) {
        if (!config.enabled() || isPublic(context.request().path())) {
            context.next();
            return;
        }
        SecurityIdentity identity = SecurityContext.identity(context);
        if (identity == null) {
            context.fail(new QuorusApiException(ErrorCode.UNAUTHORIZED, "Authenticated identity is required"));
            return;
        }
        String scope = policyEngine.requiredScope(context.request().method().name(), context.request().path());
        AuthorizationDecision decision = policyEngine.evaluate(identity,
                new AuthorizationRequest(context.request().method().name(), context.request().path(), scope,
                        null, null, null, null));
        auditSink.append(new AuditEvent(Instant.now(), "AUTHORIZATION",
                decision.allowed() ? "ALLOW" : "DENY", decision.code(), identity.principalId(),
                identity.type().name(), identity.tenantId(), identity.environment(), identity.certificateSubject(),
                context.request().method().name(), context.request().path(),
                CorrelationIdHandler.getRequestId(context), java.util.Map.of("requiredScope", scope)));
        if (!decision.allowed()) {
            context.fail(new QuorusApiException(ErrorCode.FORBIDDEN,
                    decision.reason() + " [" + decision.code() + "]"));
            return;
        }
        context.next();
    }

    private static boolean isPublic(String path) {
        return path.equals("/health/live") || path.equals("/health/ready")
                || path.equals("/api/v1/openapi.yaml");
    }
}
