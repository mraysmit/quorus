/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.security;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CredentialBearingUriDetectorTest {

    @Test
    void rejectsUsernamePasswordAndUsernameOnlyWithoutInspectingContents() {
        IllegalArgumentException sourceFailure = assertThrows(IllegalArgumentException.class, () ->
                CredentialBearingUriDetector.requireCredentialFree(
                        URI.create("sftp://settlement-user:synthetic-secret@payments.example.test/out.dat"),
                        "Source"));
        assertEquals("Source URI must not contain user-info; use a governed service connection and secret reference",
                sourceFailure.getMessage());

        IllegalArgumentException destinationFailure = assertThrows(IllegalArgumentException.class, () ->
                CredentialBearingUriDetector.requireCredentialFree(
                        URI.create("sftp://settlement-user@payments.example.test/in/out.dat"), "Destination"));
        assertEquals("Destination URI must not contain user-info; use a governed service connection and secret reference",
                destinationFailure.getMessage());

        assertDoesNotThrow(() -> CredentialBearingUriDetector.requireCredentialFree(
                URI.create("sftp://payments.example.test/out.dat"), "Source"));
    }
}
