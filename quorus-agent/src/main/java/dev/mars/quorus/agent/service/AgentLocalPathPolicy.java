/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent.service;

import dev.mars.quorus.core.TransferDirection;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Agent-side filesystem boundary for governed transfers. */
public final class AgentLocalPathPolicy {
    private final Path uploadRoot;
    private final Path downloadRoot;

    public AgentLocalPathPolicy(Path uploadRoot, Path downloadRoot) {
        this.uploadRoot = absolute(uploadRoot, "uploadRoot");
        this.downloadRoot = absolute(downloadRoot, "downloadRoot");
    }

    public Path authorize(URI localUri, TransferDirection direction) {
        Objects.requireNonNull(localUri, "localUri");
        Objects.requireNonNull(direction, "direction");
        if (!"file".equalsIgnoreCase(localUri.getScheme())) {
            throw new SecurityException("Q-LOCAL-PATH-SCHEME: local endpoint must use file URI scheme");
        }
        final Path candidate;
        try {
            candidate = Path.of(localUri).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            throw new SecurityException("Q-LOCAL-PATH-INVALID: local endpoint is invalid", e);
        }
        return switch (direction) {
            case UPLOAD -> authorizeUpload(candidate);
            case DOWNLOAD -> authorizeDownload(candidate);
            default -> throw new SecurityException("Q-LOCAL-PATH-DIRECTION: remote-to-remote is not supported");
        };
    }

    private Path authorizeUpload(Path candidate) {
        try {
            Path root = uploadRoot.toRealPath();
            Path real = candidate.toRealPath();
            if (!real.startsWith(root) || !Files.isRegularFile(real)) {
                throw denied("upload source is outside the configured upload root or is not a regular file");
            }
            return real;
        } catch (IOException e) {
            throw denied("upload source or configured upload root is unavailable", e);
        }
    }

    private Path authorizeDownload(Path candidate) {
        if (!candidate.startsWith(downloadRoot)) {
            throw denied("download destination is outside the configured download root");
        }
        try {
            Path root = downloadRoot.toRealPath();
            Path existing = candidate;
            while (existing != null && !Files.exists(existing)) existing = existing.getParent();
            if (existing == null || !existing.toRealPath().startsWith(root)) {
                throw denied("download destination escapes the configured download root");
            }
            if (Files.exists(candidate) && !candidate.toRealPath().startsWith(root)) {
                throw denied("download destination escapes the configured download root");
            }
            return candidate;
        } catch (IOException e) {
            throw denied("download destination or configured download root is unavailable", e);
        }
    }

    private static Path absolute(Path value, String name) {
        return Objects.requireNonNull(value, name).toAbsolutePath().normalize();
    }

    private static SecurityException denied(String message) {
        return new SecurityException("Q-LOCAL-PATH: " + message);
    }

    private static SecurityException denied(String message, Exception cause) {
        return new SecurityException("Q-LOCAL-PATH: " + message, cause);
    }
}
