/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security.audit;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HashChainedAuditLogTest {
    @TempDir
    Path tempDir;

    @Test
    void recordsRemainHashChainedAcrossReopen() throws Exception {
        Path path = tempDir.resolve("security-audit.jsonl");
        try (HashChainedAuditLog log = new HashChainedAuditLog(path)) {
            log.append(event("ALLOW"));
        }
        try (HashChainedAuditLog log = new HashChainedAuditLog(path)) {
            log.append(event("DENY"));
        }
        List<String> lines = Files.readAllLines(path);
        JsonObject first = new JsonObject(lines.get(0));
        JsonObject second = new JsonObject(lines.get(1));
        assertEquals("GENESIS", first.getString("previousHash"));
        assertEquals(first.getString("hash"), second.getString("previousHash"));
        assertNotEquals(first.getString("hash"), second.getString("hash"));
    }

    @Test
    void refusesToAppendWhenRetainedAuditChainWasModified() throws Exception {
        Path path = tempDir.resolve("tampered-security-audit.jsonl");
        try (HashChainedAuditLog log = new HashChainedAuditLog(path)) {
            log.append(event("ALLOW"));
            log.append(event("DENY"));
        }
        List<String> lines = Files.readAllLines(path);
        lines.set(0, lines.get(0).replace("ALLOW", "ALTERED"));
        Files.write(path, lines);

        assertThrows(IllegalStateException.class, () -> new HashChainedAuditLog(path));
    }

    @Test
    void writesEveryRecordToOperationalAndRetainedEvidenceChains() throws Exception {
        Path operational = tempDir.resolve("operational.jsonl");
        Path retained = tempDir.resolve("retained.jsonl");
        try (AuditSink sink = AuditSink.composite(
                new HashChainedAuditLog(operational), new HashChainedAuditLog(retained))) {
            sink.append(event("ALLOW"));
        }
        assertEquals(Files.readString(operational), Files.readString(retained));
    }

    private static AuditEvent event(String outcome) {
        return new AuditEvent(Instant.now(), "AUTHORIZATION", outcome, "Q-AUTHZ-TEST",
                "principal", "HUMAN", "tenant-a", "production", "CN=gateway",
                "GET", "/api/v1/info", "request-1", Map.of());
    }
}
