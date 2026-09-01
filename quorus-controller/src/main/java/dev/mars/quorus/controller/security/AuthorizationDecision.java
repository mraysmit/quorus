/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security;

/** Stable, auditable policy result. */
public record AuthorizationDecision(boolean allowed, String code, String reason, String requiredScope) {
    public static AuthorizationDecision allow(String scope) {
        return new AuthorizationDecision(true, "Q-AUTHZ-ALLOW", "Policy requirements satisfied", scope);
    }

    public static AuthorizationDecision deny(String code, String reason, String scope) {
        return new AuthorizationDecision(false, code, reason, scope);
    }
}
