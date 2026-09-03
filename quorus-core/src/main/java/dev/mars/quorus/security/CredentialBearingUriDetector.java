/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.security;

import java.net.URI;
import java.util.Objects;

/** Detects URI user-info without reading, parsing, or exposing its contents. */
public final class CredentialBearingUriDetector {

    private CredentialBearingUriDetector() {
    }

    public static boolean containsUserInfo(URI endpoint) {
        return Objects.requireNonNull(endpoint, "Endpoint URI cannot be null").getRawUserInfo() != null;
    }

    public static void requireCredentialFree(URI endpoint, String endpointName) {
        if (containsUserInfo(endpoint)) {
            throw new IllegalArgumentException(endpointName
                    + " URI must not contain user-info; use a governed service connection and secret reference");
        }
    }
}
