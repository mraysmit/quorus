/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.util;

import dev.mars.quorus.core.TransferRequest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveDataRedactorTest {

    @Test
    void redactsUriCredentialsQuerySecretsAndAuthorization() {
        String input = "sftp://user:bank-secret@host/file?token=token-secret "
                + "Authorization: Bearer bearer-secret password=plain-secret";

        String redacted = SensitiveDataRedactor.redact(input);

        assertFalse(redacted.contains("bank-secret"));
        assertFalse(redacted.contains("token-secret"));
        assertFalse(redacted.contains("bearer-secret"));
        assertFalse(redacted.contains("plain-secret"));
        assertTrue(redacted.contains("[REDACTED]"));
    }

    @Test
    void transferRequestDiagnosticRepresentationCannotExposeUriCredentials() {
        TransferRequest request = TransferRequest.builder()
                .requestId("redaction-test")
                .sourceUri(URI.create("sftp://operator:super-secret@files.example.test/in.dat?api_key=key-secret"))
                .destinationPath(Path.of("target", "redaction-test.dat"))
                .build();

        String diagnostic = request.toString();

        assertFalse(diagnostic.contains("super-secret"));
        assertFalse(diagnostic.contains("key-secret"));
        assertTrue(diagnostic.contains("[REDACTED]"));
    }
}
