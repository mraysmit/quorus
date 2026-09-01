/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security;

/** Runtime security posture. Production is deliberately fail-closed. */
public enum SecurityProfile {
    DEVELOPMENT,
    PRODUCTION;

    public static SecurityProfile parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("quorus.security.profile must be DEVELOPMENT or PRODUCTION", exception);
        }
    }
}
