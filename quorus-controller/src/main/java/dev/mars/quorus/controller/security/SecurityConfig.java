/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security;

import dev.mars.quorus.controller.config.AppConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Security and transport-trust configuration with production fail-closed validation. */
public record SecurityConfig(
        SecurityProfile profile,
        boolean enabled,
        boolean allowInsecure,
        boolean httpTlsEnabled,
        Path httpCertificate,
        Path httpPrivateKey,
        Path httpTrustBundle,
        Path httpCrl,
        Set<String> trustedGatewaySubjects,
        Set<String> revokedCertificateSerials,
        Map<String, SecurityIdentity> mtlsIdentities,
        String trustBundleVersion,
        Duration certificateExpiryWarningThreshold,
        Path auditEvidencePath,
        Path auditLogPath) {

    public SecurityConfig {
        trustedGatewaySubjects = Set.copyOf(trustedGatewaySubjects == null ? Set.of() : trustedGatewaySubjects);
        revokedCertificateSerials = Set.copyOf(revokedCertificateSerials == null ? Set.of() : revokedCertificateSerials);
        mtlsIdentities = Map.copyOf(mtlsIdentities == null ? Map.of() : mtlsIdentities);
        trustBundleVersion = trustBundleVersion == null ? "configuration" : trustBundleVersion.trim();
        certificateExpiryWarningThreshold = certificateExpiryWarningThreshold == null
                ? Duration.ofDays(30) : certificateExpiryWarningThreshold;
    }

    public SecurityConfig(SecurityProfile profile, boolean enabled, boolean allowInsecure, boolean httpTlsEnabled,
                          Path httpCertificate, Path httpPrivateKey, Path httpTrustBundle, Path httpCrl,
                          Set<String> trustedGatewaySubjects, Set<String> revokedCertificateSerials,
                          Map<String, SecurityIdentity> mtlsIdentities, Path auditLogPath) {
        this(profile, enabled, allowInsecure, httpTlsEnabled, httpCertificate, httpPrivateKey, httpTrustBundle,
                httpCrl, trustedGatewaySubjects, revokedCertificateSerials, mtlsIdentities, "configuration",
                Duration.ofDays(30), evidencePath(auditLogPath), auditLogPath);
    }

    public static SecurityConfig from(AppConfig config) {
        Set<String> gateways = subjects(config.getString("quorus.security.trusted-gateway-subjects", ""));
        Set<String> revoked = csv(config.getString("quorus.security.revoked-certificate-serials", ""));
        return new SecurityConfig(
                SecurityProfile.parse(config.getString("quorus.security.profile", "production")),
                config.getBoolean("quorus.security.enabled", true),
                config.getBoolean("quorus.security.allow-insecure", false),
                config.getBoolean("quorus.security.http.tls.enabled", true),
                path(config.getString("quorus.security.http.tls.certificate", "")),
                path(config.getString("quorus.security.http.tls.private-key", "")),
                path(config.getString("quorus.security.http.tls.trust-bundle", "")),
                path(config.getString("quorus.security.http.tls.crl", "")),
                gateways,
                revoked,
                parseBindings(config.getString("quorus.security.mtls-identities", "")),
                config.getString("quorus.security.trust-bundle.version", "configuration"),
                Duration.ofDays(config.getLong("quorus.security.certificate.expiry-warning-days", 30)),
                path(config.getString("quorus.security.audit.evidence-path", "")),
                Path.of(config.getString("quorus.security.audit.path", "./data/audit/security-audit.jsonl")));
    }

    /** Compatibility posture for unit/integration fixtures that predate Phase 1. */
    public static SecurityConfig developmentDisabled() {
        return new SecurityConfig(SecurityProfile.DEVELOPMENT, false, true, false,
                null, null, null, null, Set.of(), Set.of(), Map.of(), null);
    }

    public void validate() {
        if (profile == SecurityProfile.PRODUCTION) {
            if (!enabled) fail("Production cannot disable request security");
            if (allowInsecure) fail("Production cannot enable insecure transport");
            if (!httpTlsEnabled) fail("Production requires HTTP TLS");
            requireReadable(httpCertificate, "HTTP certificate");
            requireReadable(httpPrivateKey, "HTTP private key");
            requireReadable(httpTrustBundle, "HTTP trust bundle");
            if (trustedGatewaySubjects.isEmpty() && mtlsIdentities.isEmpty()) {
                fail("Production requires a trusted gateway subject or direct mTLS identity binding");
            }
            if (trustBundleVersion.isBlank()) fail("Production requires a trust-bundle version");
            if (certificateExpiryWarningThreshold.isNegative() || certificateExpiryWarningThreshold.isZero()) {
                fail("Certificate expiry warning threshold must be positive");
            }
            if (auditLogPath == null) fail("Production requires an immutable audit log path");
            if (auditEvidencePath == null) fail("Production requires a retained security evidence path");
            if (auditLogPath.toAbsolutePath().normalize().equals(auditEvidencePath.toAbsolutePath().normalize())) {
                fail("Operational audit and retained evidence paths must be distinct");
            }
        } else if ((!enabled || !httpTlsEnabled) && !allowInsecure) {
            fail("Development security or TLS may be disabled only with quorus.security.allow-insecure=true");
        }
    }

    private static Map<String, SecurityIdentity> parseBindings(String value) {
        Map<String, SecurityIdentity> bindings = new LinkedHashMap<>();
        if (value == null || value.isBlank()) return bindings;
        for (String entry : value.split(";")) {
            String[] binding = entry.trim().split("=>", 2);
            String[] fields = binding.length == 2 ? binding[1].split("\\|", -1) : new String[0];
            if (fields.length != 6) {
                fail("Invalid quorus.security.mtls-identities entry; expected subject=>principal|type|tenant|environment|roles|scopes");
            }
            Set<SecurityRole> roles = tokens(fields[4]).stream().map(SecurityRole::valueOf).collect(Collectors.toSet());
            SecurityIdentity identity = new SecurityIdentity(fields[0], IdentityType.valueOf(fields[1]),
                    fields[2], fields[3], roles, tokens(fields[5]), binding[0], Instant.now(), null, null);
            bindings.put(binding[0], identity);
        }
        return bindings;
    }

    private static Set<String> csv(String value) {
        return tokens(value);
    }

    private static Set<String> subjects(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(";"))
                .map(String::trim).filter(token -> !token.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> tokens(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split("[,+]"))
                .map(String::trim).filter(token -> !token.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    private static Path path(String value) {
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private static Path evidencePath(Path auditPath) {
        if (auditPath == null) return null;
        Path fileName = auditPath.getFileName();
        return auditPath.resolveSibling((fileName == null ? "security-audit" : fileName.toString()) + ".evidence");
    }

    private static void requireReadable(Path path, String description) {
        if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            fail(description + " must reference a readable file");
        }
    }

    private static void fail(String message) {
        throw new IllegalStateException(message);
    }
}
