/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security.audit;

import java.time.Instant;
import java.util.Map;

/** Redacted security audit event. Request bodies and credentials are never fields. */
public record AuditEvent(
        Instant timestamp,
        String eventType,
        String outcome,
        String decisionCode,
        String principalId,
        String identityType,
        String tenantId,
        String environment,
        String certificateSubject,
        String method,
        String path,
        String requestId,
        Map<String, String> attributes) {

    public AuditEvent {
        timestamp = timestamp == null ? Instant.now() : timestamp;
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }
}
