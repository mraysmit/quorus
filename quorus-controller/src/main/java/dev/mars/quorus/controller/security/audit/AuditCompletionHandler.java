/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security.audit;

import dev.mars.quorus.controller.http.CorrelationIdHandler;
import dev.mars.quorus.controller.security.AuthorizationPolicyEngine;
import dev.mars.quorus.controller.security.SecurityConfig;
import dev.mars.quorus.controller.security.SecurityContext;
import dev.mars.quorus.controller.security.SecurityIdentity;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

/** Records the completed outcome of protected mutations and privileged reads. */
public final class AuditCompletionHandler implements Handler<RoutingContext> {
    private static final Logger logger = LoggerFactory.getLogger(AuditCompletionHandler.class);

    private final SecurityConfig config;
    private final AuthorizationPolicyEngine policyEngine;
    private final AuditSink auditSink;

    public AuditCompletionHandler(SecurityConfig config, AuthorizationPolicyEngine policyEngine, AuditSink auditSink) {
        this.config = config;
        this.policyEngine = policyEngine;
        this.auditSink = auditSink;
    }

    @Override
    public void handle(RoutingContext context) {
        SecurityIdentity identity = SecurityContext.identity(context);
        String method = context.request().method().name();
        String path = context.request().path();
        String eventType = eventType(method, path);
        if (!config.enabled() || identity == null || eventType == null) {
            context.next();
            return;
        }

        String requiredScope = policyEngine.requiredScope(method, path);
        context.addEndHandler(ignored -> {
            int statusCode = context.response().getStatusCode();
            String outcome = statusCode < 400 ? "SUCCESS" : "FAILURE";
            try {
                auditSink.append(new AuditEvent(Instant.now(), eventType, outcome,
                        statusCode < 400 ? "Q-AUDIT-HTTP-COMPLETED" : "Q-AUDIT-HTTP-FAILED",
                        identity.principalId(), identity.type().name(), identity.tenantId(), identity.environment(),
                        identity.certificateSubject(), method, path, CorrelationIdHandler.getRequestId(context),
                        Map.of("statusCode", Integer.toString(statusCode), "requiredScope", requiredScope)));
            } catch (RuntimeException exception) {
                // The response has already completed. Preserve the failure in the operational log so it is alertable.
                logger.error("Failed to persist HTTP completion audit: requestId={}, method={}, path={}",
                        CorrelationIdHandler.getRequestId(context), method, path, exception);
            }
        });
        context.next();
    }

    static String eventType(String method, String path) {
        if (isPublic(path) || "OPTIONS".equals(method)) return null;
        if (!"GET".equals(method) && !"HEAD".equals(method)) return "MUTATION";
        if ("/api/v1/security/me".equals(path)) return null;
        return "PRIVILEGED_READ";
    }

    private static boolean isPublic(String path) {
        return path.equals("/health/live") || path.equals("/health/ready")
                || path.equals("/api/v1/openapi.yaml");
    }
}
