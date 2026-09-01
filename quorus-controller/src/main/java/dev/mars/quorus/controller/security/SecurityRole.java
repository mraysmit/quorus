/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security;

/** Canonical enterprise roles; permissions remain policy-driven. */
public enum SecurityRole {
    OPERATOR,
    ADMINISTRATOR,
    SECURITY,
    AUDITOR,
    SERVICE_INTEGRATION,
    CONTROLLER,
    AGENT,
    DEPLOYMENT
}
