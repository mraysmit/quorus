/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security;

/** Normalized request evaluated by the policy engine. */
public record AuthorizationRequest(
        String method,
        String path,
        String requiredScope,
        String resourceTenant,
        String resourceEnvironment,
        String resourceOwner,
        String classification) {
}
