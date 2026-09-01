/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.raft;

import dev.mars.quorus.controller.config.AppConfig;
import dev.mars.quorus.controller.security.SecurityProfile;

import java.nio.file.Files;
import java.nio.file.Path;

/** Mutual-TLS material for controller-to-controller Raft communication. */
public record RaftTlsConfig(
        SecurityProfile profile,
        boolean enabled,
        boolean allowInsecure,
        Path certificate,
        Path privateKey,
        Path trustBundle) {

    public static RaftTlsConfig from(AppConfig config) {
        return new RaftTlsConfig(
                SecurityProfile.parse(config.getString("quorus.security.profile", "production")),
                config.getBoolean("quorus.security.raft.tls.enabled", true),
                config.getBoolean("quorus.security.allow-insecure", false),
                path(config.getString("quorus.security.raft.tls.certificate", "")),
                path(config.getString("quorus.security.raft.tls.private-key", "")),
                path(config.getString("quorus.security.raft.tls.trust-bundle", "")));
    }

    public static RaftTlsConfig developmentDisabled() {
        return new RaftTlsConfig(SecurityProfile.DEVELOPMENT, false, true, null, null, null);
    }

    public void validate() {
        if (profile == SecurityProfile.PRODUCTION) {
            if (!enabled || allowInsecure) {
                throw new IllegalStateException("Production requires Raft mutual TLS and forbids insecure transport");
            }
            requireReadable(certificate, "Raft certificate");
            requireReadable(privateKey, "Raft private key");
            requireReadable(trustBundle, "Raft trust bundle");
        } else if (!enabled && !allowInsecure) {
            throw new IllegalStateException("Plaintext development Raft requires quorus.security.allow-insecure=true");
        }
    }

    private static Path path(String value) {
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private static void requireReadable(Path path, String description) {
        if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalStateException(description + " must reference a readable file");
        }
    }
}
