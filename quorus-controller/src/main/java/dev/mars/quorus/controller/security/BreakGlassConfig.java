/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security;

import dev.mars.quorus.controller.config.AppConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Break-glass certificate bindings (ADR-0013, register item SEC-06): certificate subjects that may elevate,
 * the pinned CA certificates that must issue them, and the longest certificate lifetime accepted, which is
 * also the elevation window.
 */
public record BreakGlassConfig(
        Map<String, Binding> bindings,
        Set<String> issuerSha256,
        Duration maxCertificateLifetime) {

    /** The only scopes a break-glass binding may hold (ADR-0013 item 2). */
    public static final Set<String> ALLOWED_SCOPES = Set.of(
            "security:trust:write", "security:trust:read", "security:self:read", "security:explain");
    public static final Duration DEFAULT_MAX_CERTIFICATE_LIFETIME = Duration.ofHours(1);
    public static final Duration HARD_MAX_CERTIFICATE_LIFETIME = Duration.ofHours(4);
    /** Tenant and environment of every break-glass identity; no allowed scope is tenant-filtered. */
    public static final String PLATFORM = "platform";

    /** The principal and scopes a break-glass certificate subject is bound to. */
    public record Binding(String principalId, Set<String> scopes) {
        public Binding {
            scopes = Set.copyOf(scopes == null ? Set.of() : scopes);
        }
    }

    public BreakGlassConfig {
        bindings = Map.copyOf(bindings == null ? Map.of() : bindings);
        issuerSha256 = issuerSha256 == null ? Set.of() : issuerSha256.stream()
                .map(BreakGlassConfig::normalizeFingerprint).filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        maxCertificateLifetime = maxCertificateLifetime == null
                ? DEFAULT_MAX_CERTIFICATE_LIFETIME : maxCertificateLifetime;
    }

    public static BreakGlassConfig disabled() {
        return new BreakGlassConfig(Map.of(), Set.of(), null);
    }

    static BreakGlassConfig from(AppConfig config) {
        String lifetime = config.getString("quorus.security.break-glass.max-certificate-lifetime",
                DEFAULT_MAX_CERTIFICATE_LIFETIME.toString());
        Duration maxLifetime;
        try {
            maxLifetime = Duration.parse(lifetime.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalStateException("Invalid quorus.security.break-glass.max-certificate-lifetime '"
                    + lifetime + "'; expected an ISO-8601 duration such as PT1H");
        }
        return new BreakGlassConfig(
                parseBindings(config.getString("quorus.security.break-glass-identities", "")),
                tokens(config.getString("quorus.security.break-glass.issuer-sha256", "")),
                maxLifetime);
    }

    public boolean enabled() {
        return !bindings.isEmpty();
    }

    /** Startup checks of ADR-0013 items 2 to 4; identity-list overlaps are checked by {@link SecurityConfig}. */
    void validate(Path trustBundle) {
        if (maxCertificateLifetime.isNegative() || maxCertificateLifetime.isZero()
                || maxCertificateLifetime.compareTo(HARD_MAX_CERTIFICATE_LIFETIME) > 0) {
            fail("Break-glass max-certificate-lifetime must be positive and at most "
                    + HARD_MAX_CERTIFICATE_LIFETIME + ", was " + maxCertificateLifetime);
        }
        if (!enabled()) return;
        bindings.forEach((subject, binding) -> {
            if (binding.principalId() == null || binding.principalId().isBlank()) {
                fail("Break-glass binding for " + subject + " has no principal");
            }
            if (binding.scopes().isEmpty()) fail("Break-glass binding for " + subject + " has no scopes");
            Set<String> outside = new TreeSet<>(binding.scopes());
            outside.removeAll(ALLOWED_SCOPES);
            if (!outside.isEmpty()) {
                fail("Break-glass binding for " + subject + " lists scopes outside " + new TreeSet<>(ALLOWED_SCOPES)
                        + ": " + outside);
            }
        });
        if (issuerSha256.isEmpty()) {
            fail("Break-glass bindings require quorus.security.break-glass.issuer-sha256");
        }
        pinnedIssuers(trustBundle);
    }

    /** The pinned CA certificates, found by fingerprint in the HTTP trust bundle. */
    Map<String, X509Certificate> pinnedIssuers(Path trustBundle) {
        if (trustBundle == null || !Files.isReadable(trustBundle)) {
            fail("Break-glass bindings require a readable HTTP trust bundle holding the pinned issuers");
        }
        Map<String, X509Certificate> found = new LinkedHashMap<>();
        try (InputStream input = Files.newInputStream(trustBundle)) {
            for (Certificate certificate : CertificateFactory.getInstance("X.509").generateCertificates(input)) {
                if (certificate instanceof X509Certificate x509) {
                    String fingerprint = fingerprint(x509);
                    if (issuerSha256.contains(fingerprint)) found.put(fingerprint, x509);
                }
            }
        } catch (IOException | GeneralSecurityException exception) {
            fail("Break-glass cannot read the HTTP trust bundle " + trustBundle + ": " + exception.getMessage());
        }
        Set<String> missing = new TreeSet<>(issuerSha256);
        missing.removeAll(found.keySet());
        if (!missing.isEmpty()) {
            fail("Break-glass pinned issuers are not in the HTTP trust bundle " + trustBundle + ": " + missing);
        }
        return Map.copyOf(found);
    }

    static String fingerprint(X509Certificate certificate) {
        try {
            return HexFormat.of().withUpperCase()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot fingerprint certificate", exception);
        }
    }

    private static Map<String, Binding> parseBindings(String value) {
        Map<String, Binding> bindings = new LinkedHashMap<>();
        if (value == null || value.isBlank()) return bindings;
        for (String entry : value.split(";")) {
            if (entry.isBlank()) continue;
            String[] binding = entry.trim().split("=>", 2);
            String[] fields = binding.length == 2 ? binding[1].split("\\|", -1) : new String[0];
            if (fields.length != 2) {
                fail("Invalid quorus.security.break-glass-identities entry; expected subject=>principal|scopes");
            }
            bindings.put(binding[0].trim(), new Binding(fields[0].trim(), tokens(fields[1])));
        }
        return bindings;
    }

    private static Set<String> tokens(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split("[,+]"))
                .map(String::trim).filter(token -> !token.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    private static String normalizeFingerprint(String value) {
        return value == null ? "" : value.replace(":", "").trim().toUpperCase(Locale.ROOT);
    }

    private static void fail(String message) {
        throw new IllegalStateException(message);
    }
}
