/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

import dev.mars.quorus.security.CredentialBearingUriDetector;

import java.net.URI;
import java.util.List;
import java.util.Map;

/** Inventory-only migration helper that reports credential-bearing resources without echoing user-info. */
public final class CredentialUriMigrationScanner {
    public List<Finding> scan(Map<String, URI> resources) {
        return resources.entrySet().stream()
                .filter(entry -> entry.getValue() != null && CredentialBearingUriDetector.containsUserInfo(entry.getValue()))
                .map(entry -> new Finding(entry.getKey(), entry.getValue().getScheme(),
                        entry.getValue().getHost(), "URI_USER_INFO_PRESENT"))
                .toList();
    }

    public record Finding(String resourceId, String protocol, String host, String reasonCode) { }
}
