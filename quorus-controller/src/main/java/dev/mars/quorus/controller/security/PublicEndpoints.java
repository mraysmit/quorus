/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security;

import java.util.Set;

/**
 * The controller paths that are served without authentication or authorization.
 *
 * <p>This is the single definition used by {@link AuthenticationHandler} and
 * {@link AuthorizationHandler}. The OpenAPI contract declares {@code security: []} for exactly
 * these paths, which {@code OpenApiReferenceContractTest} verifies.
 */
public final class PublicEndpoints {

    private static final Set<String> PATHS = Set.of("/health/live", "/health/ready", "/api/v1/openapi.yaml");

    private PublicEndpoints() {
    }

    /** Returns whether {@code path} is served without authentication. The match is exact. */
    public static boolean isPublic(String path) {
        return PATHS.contains(path);
    }

    /** Returns the public paths. */
    public static Set<String> paths() {
        return PATHS;
    }
}
