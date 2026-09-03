/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Opaque metadata pointing to an externally managed secret; it can never hold the secret value. */
public record SecretReference(
        String secretReferenceId,
        String tenantId,
        String provider,
        String path,
        String key,
        String version,
        Status status,
        Instant expiresAt,
        Instant lastRotatedAt) implements Serializable {

    public SecretReference {
        secretReferenceId = required(secretReferenceId, "secretReferenceId");
        tenantId = required(tenantId, "tenantId");
        provider = required(provider, "provider");
        path = required(path, "path");
        key = required(key, "key");
        version = required(version, "version");
        status = Objects.requireNonNull(status, "status");
        if (path.contains("@") || path.contains("://")) {
            throw new IllegalArgumentException("Secret path must be an opaque provider path, not a URI");
        }
    }

    public enum Status { ACTIVE, ROTATING, REVOKED, EXPIRED }

    public boolean usableAt(Instant instant) {
        return status == Status.ACTIVE && (expiresAt == null || instant.isBefore(expiresAt));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    @Override
    public String toString() {
        return "SecretReference[id=" + secretReferenceId + ", tenant=" + tenantId
                + ", provider=" + provider + ", version=" + version + ", status=" + status + "]";
    }
}
