/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/** Deterministic RBAC/ABAC policy engine for HTTP resources. */
public final class AuthorizationPolicyEngine {

    private final Clock clock;

    public AuthorizationPolicyEngine() {
        this(Clock.systemUTC());
    }

    AuthorizationPolicyEngine(Clock clock) {
        this.clock = clock;
    }

    public AuthorizationDecision evaluate(SecurityIdentity identity, AuthorizationRequest request) {
        String scope = request.requiredScope();
        Instant now = clock.instant();
        if (identity.isExpired(now)) {
            return AuthorizationDecision.deny("Q-AUTHZ-IDENTITY-EXPIRED", "Identity has expired", scope);
        }
        if (request.resourceTenant() != null && !request.resourceTenant().equals(identity.tenantId())) {
            return AuthorizationDecision.deny("Q-AUTHZ-TENANT-MISMATCH", "Resource belongs to another tenant", scope);
        }
        if (request.resourceEnvironment() != null
                && !request.resourceEnvironment().equals(identity.environment())) {
            return AuthorizationDecision.deny("Q-AUTHZ-ENVIRONMENT-MISMATCH", "Resource belongs to another environment", scope);
        }
        if (scope == null || identity.hasScope(scope) || roleAllows(identity, scope)) {
            if (requiresElevation(scope) && !identity.hasActiveElevation(now)) {
                return AuthorizationDecision.deny("Q-AUTHZ-ELEVATION-REQUIRED",
                        "A current time-bounded elevation is required", scope);
            }
            return AuthorizationDecision.allow(scope);
        }
        return AuthorizationDecision.deny("Q-AUTHZ-SCOPE-MISSING", "Required permission is not granted", scope);
    }

    public String requiredScope(String method, String path) {
        String verb = method.toUpperCase(Locale.ROOT);
        if (path.startsWith("/api/v1/security/trust/revocations")) return "security:trust:write";
        if (path.startsWith("/api/v1/security/trust")) return "security:trust:read";
        if (path.startsWith("/api/v1/security/authorization")) return "security:explain";
        if (path.startsWith("/api/v1/security/me")) return "security:self:read";
        if (path.startsWith("/api/v1/security-events")) return "security-events:read";
        if (path.startsWith("/api/v1/service-connections")) return scope("service-connections", verb);
        if (path.startsWith("/api/v1/secret-references")) return scope("secret-references", verb);
        if (path.startsWith("/api/v1/agents/register")) return "agents:register";
        if (path.startsWith("/api/v1/agents/heartbeat")) return "agents:heartbeat";
        if (path.matches("/api/v1/agents/[^/]+/jobs.*")) return "agents:jobs:read";
        if (path.startsWith("/api/v1/agents")) return "agents:read";
        if (path.startsWith("/api/v1/transfers")) return scope("transfers", verb);
        if (path.startsWith("/api/v1/jobs")) return "transfers:status:update";
        if (path.startsWith("/api/v1/assignments")) return scope("assignments", verb);
        if (path.startsWith("/api/v1/routes")) return scope("routes", verb);
        if (path.startsWith("/raft/status")) return "cluster:read";
        if (path.startsWith("/metrics")) return "telemetry:read";
        if (path.startsWith("/status") || path.startsWith("/api/v1/info")) return "system:read";
        return "api:access";
    }

    private static String scope(String resource, String method) {
        return switch (method) {
            case "GET", "HEAD" -> resource + ":read";
            case "DELETE" -> resource + ":delete";
            default -> resource + ":write";
        };
    }

    private static boolean roleAllows(SecurityIdentity identity, String scope) {
        if (identity.roles().contains(SecurityRole.ADMINISTRATOR)) return true;
        if (identity.roles().contains(SecurityRole.SECURITY)) {
            return scope.startsWith("security:") || scope.startsWith("security-events:")
                    || scope.startsWith("service-connections:") || scope.startsWith("secret-references:")
                    || scope.equals("system:read") || scope.equals("telemetry:read");
        }
        if (identity.roles().contains(SecurityRole.AUDITOR)) {
            return scope.endsWith(":read") || scope.equals("telemetry:read") || scope.equals("security:explain");
        }
        if (identity.roles().contains(SecurityRole.OPERATOR)) {
            return scope.startsWith("transfers:") || scope.startsWith("assignments:")
                    || scope.startsWith("routes:") || scope.startsWith("agents:read")
                    || scope.equals("service-connections:read") || scope.equals("security-events:read")
                    || scope.equals("telemetry:read") || scope.equals("system:read")
                    || scope.equals("security:self:read");
        }
        if (identity.roles().contains(SecurityRole.AGENT)) {
            return scope.equals("agents:register") || scope.equals("agents:heartbeat")
                    || scope.equals("agents:jobs:read") || scope.equals("transfers:status:update")
                    || scope.equals("security:self:read");
        }
        if (identity.roles().contains(SecurityRole.SERVICE_INTEGRATION)) {
            return scope.startsWith("transfers:") || scope.startsWith("routes:")
                    || scope.equals("service-connections:read")
                    || scope.equals("security:self:read");
        }
        return false;
    }

    private static boolean requiresElevation(String scope) {
        return "security:policy:write".equals(scope) || "security:trust:write".equals(scope)
                || "cluster:membership:write".equals(scope)
                || "audit:purge".equals(scope)
                || scope.startsWith("secret-references:") && !scope.endsWith(":read")
                || scope.startsWith("service-connections:") && !scope.endsWith(":read");
    }
}
