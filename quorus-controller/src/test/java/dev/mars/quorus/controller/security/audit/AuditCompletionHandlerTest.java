/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuditCompletionHandlerTest {

    @Test
    void classifiesMutationsAndPrivilegedReads() {
        assertEquals("MUTATION", AuditCompletionHandler.eventType("POST", "/api/v1/transfers"));
        assertEquals("MUTATION", AuditCompletionHandler.eventType("DELETE", "/api/v1/routes/route-1"));
        assertEquals("PRIVILEGED_READ", AuditCompletionHandler.eventType("GET", "/api/v1/transfers/job-1"));
        assertEquals("PRIVILEGED_READ", AuditCompletionHandler.eventType("HEAD", "/metrics"));
    }

    @Test
    void excludesPublicAndSelfInspectionRequests() {
        assertNull(AuditCompletionHandler.eventType("GET", "/health/live"));
        assertNull(AuditCompletionHandler.eventType("GET", "/health/ready"));
        assertNull(AuditCompletionHandler.eventType("GET", "/api/v1/openapi.yaml"));
        assertNull(AuditCompletionHandler.eventType("GET", "/api/v1/security/me"));
        assertNull(AuditCompletionHandler.eventType("OPTIONS", "/api/v1/transfers"));
    }
}
