/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR-0013 item 3: the pinned issuer is identified by its key, not its name. Written after the code
 * (retrospective): the HTTP boundary tests use a second CA with a different name, so they could not catch
 * the signature check being removed.
 */
class BreakGlassAuthenticatorTest {
    private static final String SUBJECT = "CN=Incident Responder,OU=Break Glass,O=Cityline Ltd,C=GB";
    private static final String CA_NAME = "CN=Quorus Break Glass CA,O=Cityline Ltd,C=GB";

    @TempDir
    Path tempDir;

    @Test
    void aCaWithThePinnedNameButAnotherKeyIsNotThePinnedIssuer() throws Exception {
        TestCertificates.Issued pinned = TestCertificates.certificateAuthority(CA_NAME);
        TestCertificates.Issued impostor = TestCertificates.certificateAuthority(CA_NAME);
        BreakGlassAuthenticator authenticator = authenticator(pinned, impostor);
        Instant notBefore = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.SECONDS);

        BreakGlassAuthenticator.Outcome forged = authenticator.evaluate(TestCertificates.issue(impostor, SUBJECT,
                notBefore, notBefore.plusSeconds(1_800)).certificate());
        BreakGlassAuthenticator.Outcome genuine = authenticator.evaluate(TestCertificates.issue(pinned, SUBJECT,
                notBefore, notBefore.plusSeconds(1_800)).certificate());

        assertEquals(BreakGlassAuthenticator.UNPINNED_ISSUER, forged.refusal());
        assertTrue(genuine.accepted(), "positive control: the pinned CA's own certificate is accepted");
    }

    private BreakGlassAuthenticator authenticator(TestCertificates.Issued pinned, TestCertificates.Issued other)
            throws Exception {
        Path trust = TestCertificates.writeBundle(tempDir.resolve("trust.pem"), pinned.certificate(),
                other.certificate());
        Path certificate = Files.writeString(tempDir.resolve("controller.crt"), "test");
        Path key = Files.writeString(tempDir.resolve("controller.key"), "test");
        SecurityConfig config = new SecurityConfig(SecurityProfile.PRODUCTION, true, false, true, certificate, key,
                trust, null, Set.of(), Set.of(), Map.of(), tempDir.resolve("audit.jsonl"))
                .withBreakGlass(new BreakGlassConfig(Map.of(SUBJECT, new BreakGlassConfig.Binding(
                        "incident-responder", Set.of("security:trust:write"))), Set.of(pinned.sha256()), null));
        return BreakGlassAuthenticator.from(config);
    }
}
