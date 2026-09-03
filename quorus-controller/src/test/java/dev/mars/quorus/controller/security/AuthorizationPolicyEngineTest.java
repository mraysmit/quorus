/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationPolicyEngineTest {
    private final AuthorizationPolicyEngine policy = new AuthorizationPolicyEngine();

    @org.junit.jupiter.api.Test
    void serviceConnectionSecretAndSecurityEventRoutesHaveExplicitScopes() {
        org.junit.jupiter.api.Assertions.assertEquals("service-connections:read",
                policy.requiredScope("GET", "/api/v1/service-connections"));
        org.junit.jupiter.api.Assertions.assertEquals("service-connections:write",
                policy.requiredScope("POST", "/api/v1/service-connections/payments/validate"));
        org.junit.jupiter.api.Assertions.assertEquals("secret-references:delete",
                policy.requiredScope("DELETE", "/api/v1/secret-references/payments"));
        org.junit.jupiter.api.Assertions.assertEquals("security-events:read",
                policy.requiredScope("GET", "/api/v1/security-events"));
    }

    @Test
    void operatorMayControlTransfersWithinAuthenticatedTenant() {
        SecurityIdentity identity = identity(Set.of(SecurityRole.OPERATOR), Set.of());
        AuthorizationDecision decision = policy.evaluate(identity,
                new AuthorizationRequest("POST", "/api/v1/transfers", "transfers:write",
                        "tenant-a", "production", null, "CONFIDENTIAL"));
        assertTrue(decision.allowed());
        assertEquals("Q-AUTHZ-ALLOW", decision.code());
    }

    @Test
    void crossTenantAccessFailsWithStableDecisionCode() {
        AuthorizationDecision decision = policy.evaluate(identity(Set.of(SecurityRole.ADMINISTRATOR), Set.of("*")),
                new AuthorizationRequest("GET", "/api/v1/transfers/job-1", "transfers:read",
                        "tenant-b", "production", null, null));
        assertFalse(decision.allowed());
        assertEquals("Q-AUTHZ-TENANT-MISMATCH", decision.code());
    }

    @Test
    void expiredIdentityFailsClosed() {
        SecurityIdentity expired = new SecurityIdentity("operator-1", IdentityType.HUMAN, "tenant-a",
                "production", Set.of(SecurityRole.ADMINISTRATOR), Set.of("*"), "CN=gateway",
                Instant.now().minusSeconds(60), Instant.now().minusSeconds(1), null);
        AuthorizationDecision decision = policy.evaluate(expired,
                new AuthorizationRequest("GET", "/api/v1/info", "system:read",
                        null, null, null, null));
        assertFalse(decision.allowed());
        assertEquals("Q-AUTHZ-IDENTITY-EXPIRED", decision.code());
    }

    private static SecurityIdentity identity(Set<SecurityRole> roles, Set<String> scopes) {
        return new SecurityIdentity("operator-1", IdentityType.HUMAN, "tenant-a", "production",
                roles, scopes, "CN=gateway", Instant.now(), Instant.now().plusSeconds(300), null);
    }
}
