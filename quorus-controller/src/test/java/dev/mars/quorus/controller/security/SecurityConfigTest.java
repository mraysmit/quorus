/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void productionCannotDisableSecurityOrTls() {
        SecurityConfig config = new SecurityConfig(SecurityProfile.PRODUCTION, false, true, false,
                null, null, null, null, Set.of(), Set.of(), Map.of(), null);
        assertThrows(IllegalStateException.class, config::validate);
    }

    @Test
    void productionAcceptsCompleteFailClosedConfiguration() throws Exception {
        Path certificate = Files.writeString(tempDir.resolve("controller.crt"), "test");
        Path key = Files.writeString(tempDir.resolve("controller.key"), "test");
        Path trust = Files.writeString(tempDir.resolve("ca.crt"), "test");
        SecurityConfig config = new SecurityConfig(SecurityProfile.PRODUCTION, true, false, true,
                certificate, key, trust, null, Set.of("CN=gateway"), Set.of(), Map.of(),
                tempDir.resolve("audit.jsonl"));
        assertDoesNotThrow(config::validate);
    }
}
