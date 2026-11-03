/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security;

import dev.mars.quorus.controller.config.ControllerTestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {
    private static final String BREAK_GLASS_SUBJECT = "CN=Incident Responder,OU=Break Glass,O=Cityline Ltd,C=GB";

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

    @Test
    void breakGlassBindingsParseFromConfiguration() {
        Properties properties = new Properties();
        properties.setProperty("quorus.security.break-glass-identities",
                BREAK_GLASS_SUBJECT + "=>incident-responder|security:trust:write+security:trust:read");
        properties.setProperty("quorus.security.break-glass.issuer-sha256", "ab:cd:01, EF02");
        properties.setProperty("quorus.security.break-glass.max-certificate-lifetime", "PT30M");

        BreakGlassConfig breakGlass = SecurityConfig.from(ControllerTestConfig.create(properties)).breakGlass();

        assertEquals(Map.of(BREAK_GLASS_SUBJECT, new BreakGlassConfig.Binding("incident-responder",
                Set.of("security:trust:write", "security:trust:read"))), breakGlass.bindings());
        assertEquals(Set.of("ABCD01", "EF02"), breakGlass.issuerSha256());
        assertEquals(Duration.ofMinutes(30), breakGlass.maxCertificateLifetime());
    }

    @Test
    void breakGlassIsOffAndOneHourByDefault() {
        BreakGlassConfig breakGlass = SecurityConfig.from(ControllerTestConfig.create()).breakGlass();
        assertTrue(breakGlass.bindings().isEmpty());
        assertTrue(breakGlass.issuerSha256().isEmpty());
        assertEquals(Duration.ofHours(1), breakGlass.maxCertificateLifetime());
    }

    @Test
    void aBreakGlassBindingWithTheOldSixFieldFormatIsRejected() {
        Properties properties = new Properties();
        properties.setProperty("quorus.security.break-glass-identities",
                BREAK_GLASS_SUBJECT + "=>incident-responder|HUMAN|platform|platform||security:trust:write");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> SecurityConfig.from(ControllerTestConfig.create(properties)));
        assertTrue(failure.getMessage().contains("break-glass-identities"), failure.getMessage());
    }

    @Test
    void aCompleteBreakGlassConfigurationIsAccepted() throws Exception {
        TestCertificates.Issued ca = TestCertificates.certificateAuthority("CN=Break Glass CA,O=Cityline Ltd");
        SecurityConfig config = production(bundle(ca), Set.of(), Map.of(),
                breakGlass(Set.of(ca.sha256()), BreakGlassConfig.ALLOWED_SCOPES, Duration.ofHours(4)));
        assertDoesNotThrow(config::validate);
    }

    @Test
    void startupFailsWhenASubjectIsInMoreThanOneIdentityList() throws Exception {
        TestCertificates.Issued ca = TestCertificates.certificateAuthority("CN=Break Glass CA,O=Cityline Ltd");
        Path trust = bundle(ca);
        BreakGlassConfig breakGlass = breakGlass(Set.of(ca.sha256()), Set.of("security:trust:write"),
                Duration.ofHours(1));
        SecurityIdentity ordinary = new SecurityIdentity("responder", IdentityType.HUMAN, "bank", "production",
                Set.of(), Set.of("security:self:read"), BREAK_GLASS_SUBJECT, Instant.now(), null, null);

        assertBreakGlassFailure(production(trust, Set.of(BREAK_GLASS_SUBJECT), Map.of(), breakGlass));
        assertBreakGlassFailure(production(trust, Set.of(), Map.of(BREAK_GLASS_SUBJECT, ordinary), breakGlass));
    }

    @Test
    void startupFailsWhenAGatewaySubjectIsAlsoAnOrdinaryBinding() throws Exception {
        TestCertificates.Issued ca = TestCertificates.certificateAuthority("CN=Ordinary CA,O=Cityline Ltd");
        SecurityIdentity ordinary = new SecurityIdentity("gateway-operator", IdentityType.HUMAN, "bank",
                "production", Set.of(), Set.of("security:self:read"), "CN=gateway", Instant.now(), null, null);
        SecurityConfig config = production(bundle(ca), Set.of("CN=gateway"), Map.of("CN=gateway", ordinary),
                BreakGlassConfig.disabled());
        IllegalStateException failure = assertThrows(IllegalStateException.class, config::validate);
        assertTrue(failure.getMessage().contains("CN=gateway"), failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"security-events:read", "transfers:write", "*", "security:*"})
    void startupFailsForABreakGlassScopeOutsideTheIncidentSet(String scope) throws Exception {
        TestCertificates.Issued ca = TestCertificates.certificateAuthority("CN=Break Glass CA,O=Cityline Ltd");
        assertBreakGlassFailure(production(bundle(ca), Set.of(), Map.of(),
                breakGlass(Set.of(ca.sha256()), Set.of("security:trust:write", scope), Duration.ofHours(1))));
    }

    @Test
    void startupFailsWhenAPinnedIssuerIsMissingFromTheTrustBundle() throws Exception {
        TestCertificates.Issued pinned = TestCertificates.certificateAuthority("CN=Break Glass CA,O=Cityline Ltd");
        TestCertificates.Issued other = TestCertificates.certificateAuthority("CN=Other CA,O=Cityline Ltd");
        assertBreakGlassFailure(production(bundle(other), Set.of(), Map.of(),
                breakGlass(Set.of(pinned.sha256()), Set.of("security:trust:write"), Duration.ofHours(1))));
    }

    @Test
    void startupFailsForBreakGlassBindingsWithoutAPinnedIssuer() throws Exception {
        TestCertificates.Issued ca = TestCertificates.certificateAuthority("CN=Break Glass CA,O=Cityline Ltd");
        assertBreakGlassFailure(production(bundle(ca), Set.of(), Map.of(),
                breakGlass(Set.of(), Set.of("security:trust:write"), Duration.ofHours(1))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT4H1S", "PT0S", "-PT1H"})
    void startupFailsForALifetimeLimitOutsideZeroToFourHours(String limit) throws Exception {
        TestCertificates.Issued ca = TestCertificates.certificateAuthority("CN=Break Glass CA,O=Cityline Ltd");
        assertBreakGlassFailure(production(bundle(ca), Set.of(), Map.of(),
                breakGlass(Set.of(ca.sha256()), Set.of("security:trust:write"), Duration.parse(limit))));
    }

    private static void assertBreakGlassFailure(SecurityConfig config) {
        IllegalStateException failure = assertThrows(IllegalStateException.class, config::validate);
        assertTrue(failure.getMessage().toLowerCase(java.util.Locale.ROOT).contains("break-glass"),
                failure.getMessage());
    }

    private static BreakGlassConfig breakGlass(Set<String> pins, Set<String> scopes, Duration limit) {
        return new BreakGlassConfig(Map.of(BREAK_GLASS_SUBJECT, new BreakGlassConfig.Binding("incident-responder",
                scopes)), pins, limit);
    }

    private Path bundle(TestCertificates.Issued... authorities) throws Exception {
        return TestCertificates.writeBundle(tempDir.resolve("trust-" + System.nanoTime() + ".pem"),
                java.util.Arrays.stream(authorities).map(TestCertificates.Issued::certificate)
                        .toArray(java.security.cert.X509Certificate[]::new));
    }

    private SecurityConfig production(Path trust, Set<String> gateways, Map<String, SecurityIdentity> identities,
                                      BreakGlassConfig breakGlass) throws Exception {
        Path certificate = Files.writeString(tempDir.resolve("controller.crt"), "test");
        Path key = Files.writeString(tempDir.resolve("controller.key"), "test");
        SecurityIdentity fallback = new SecurityIdentity("operator", IdentityType.HUMAN, "bank", "production",
                Set.of(), Set.of("security:self:read"), "CN=operator", Instant.now(), null, null);
        Map<String, SecurityIdentity> bindings = new java.util.HashMap<>(identities);
        if (gateways.isEmpty() && bindings.isEmpty()) bindings.put("CN=operator", fallback);
        return new SecurityConfig(SecurityProfile.PRODUCTION, true, false, true, certificate, key, trust, null,
                gateways, Set.of(), bindings, tempDir.resolve("audit.jsonl")).withBreakGlass(breakGlass);
    }
}
