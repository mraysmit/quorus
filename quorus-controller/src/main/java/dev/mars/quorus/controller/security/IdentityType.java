/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security;

/** Distinct enterprise identities at Quorus trust boundaries. */
public enum IdentityType {
    HUMAN,
    SERVICE_INTEGRATION,
    CONTROLLER,
    AGENT,
    DEPLOYMENT
}
