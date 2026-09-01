/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Verified identity used for tenant derivation and authorization decisions. */
public record SecurityIdentity(
        String principalId,
        IdentityType type,
        String tenantId,
        String environment,
        Set<SecurityRole> roles,
        Set<String> scopes,
        String certificateSubject,
        Instant authenticatedAt,
        Instant expiresAt,
        Instant elevationExpiresAt) {

    public SecurityIdentity {
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(environment, "environment");
        roles = Set.copyOf(roles == null ? Set.of() : roles);
        scopes = Set.copyOf(scopes == null ? Set.of() : scopes);
        authenticatedAt = authenticatedAt == null ? Instant.now() : authenticatedAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public boolean hasScope(String required) {
        return scopes.contains("*") || scopes.contains(required)
                || scopes.stream().anyMatch(scope -> scope.endsWith(":*")
                && required.startsWith(scope.substring(0, scope.length() - 1)));
    }

    public boolean hasActiveElevation(Instant now) {
        return elevationExpiresAt != null && elevationExpiresAt.isAfter(now);
    }
}
