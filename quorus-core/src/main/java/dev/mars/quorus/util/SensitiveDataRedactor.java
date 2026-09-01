/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.util;

import java.net.URI;
import java.util.regex.Pattern;

/** Central redaction policy for logs, telemetry and client-visible diagnostics. */
public final class SensitiveDataRedactor {
    private static final Pattern URI_USER_INFO = Pattern.compile(
            "(?i)([a-z][a-z0-9+.-]*://)[^/@\\s]+@");
    private static final Pattern SECRET_PARAMETER = Pattern.compile(
            "(?i)([?&](?:password|passwd|token|access_token|refresh_token|secret|api[_-]?key|credential|signature)=)[^&\\s]*");
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*(?:bearer|basic)\\s+)[^,;\\s]+");
    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)(\\b(?:password|passwd|token|access_token|refresh_token|secret|api[_-]?key|credential)\\b\\s*[:=]\\s*)[^,;\\s}]+");

    private SensitiveDataRedactor() {
    }

    public static String redact(String value) {
        if (value == null) {
            return null;
        }
        String redacted = URI_USER_INFO.matcher(value).replaceAll("$1[REDACTED]@");
        redacted = SECRET_PARAMETER.matcher(redacted).replaceAll("$1[REDACTED]");
        redacted = AUTHORIZATION.matcher(redacted).replaceAll("$1[REDACTED]");
        return NAMED_SECRET.matcher(redacted).replaceAll("$1[REDACTED]");
    }

    public static String redactUri(URI uri) {
        return uri == null ? null : redact(uri.toASCIIString());
    }
}
