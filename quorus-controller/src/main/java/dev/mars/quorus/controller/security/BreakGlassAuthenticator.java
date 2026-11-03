/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security;

import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Decides whether a client certificate authenticates as a break-glass identity (ADR-0013 items 3 and 4).
 * A certificate is break-glass only if its subject is a break-glass binding and a pinned CA signed it, and
 * its lifetime is within the limit; the identity and its elevation then both expire at the certificate's
 * {@code notAfter}. A certificate from a pinned CA with any other subject is refused, so it can never
 * authenticate as an ordinary binding or as the gateway. Holds no state, so every controller decides alike.
 */
final class BreakGlassAuthenticator {

    /** Not applicable, refused with a reason, or accepted as a break-glass identity. */
    record Outcome(SecurityIdentity identity, String issuerSha256, String refusal) {
        static final Outcome NOT_APPLICABLE = new Outcome(null, null, null);

        boolean refused() {
            return refusal != null;
        }

        boolean accepted() {
            return identity != null;
        }
    }

    static final String UNPINNED_ISSUER = "unpinned-issuer";
    static final String LIFETIME_EXCEEDED = "lifetime-exceeded";
    static final String UNBOUND_SUBJECT = "unbound-subject";

    private final Map<String, BreakGlassConfig.Binding> bindings;
    private final Map<String, X509Certificate> pinnedIssuers;
    private final Duration maxCertificateLifetime;

    private BreakGlassAuthenticator(Map<String, BreakGlassConfig.Binding> bindings,
                                    Map<String, X509Certificate> pinnedIssuers, Duration maxCertificateLifetime) {
        this.bindings = bindings;
        this.pinnedIssuers = pinnedIssuers;
        this.maxCertificateLifetime = maxCertificateLifetime;
    }

    static BreakGlassAuthenticator from(SecurityConfig config) {
        BreakGlassConfig breakGlass = config.breakGlass();
        if (!config.enabled() || !breakGlass.enabled()) {
            return new BreakGlassAuthenticator(Map.of(), Map.of(), breakGlass.maxCertificateLifetime());
        }
        return new BreakGlassAuthenticator(breakGlass.bindings(), breakGlass.pinnedIssuers(config.httpTrustBundle()),
                breakGlass.maxCertificateLifetime());
    }

    Outcome evaluate(X509Certificate certificate) {
        if (pinnedIssuers.isEmpty()) return Outcome.NOT_APPLICABLE;
        String subject = certificate.getSubjectX500Principal().getName();
        BreakGlassConfig.Binding binding = bindings.get(subject);
        String issuer = pinnedIssuer(certificate);
        if (binding == null) {
            return issuer == null ? Outcome.NOT_APPLICABLE : new Outcome(null, issuer, UNBOUND_SUBJECT);
        }
        if (issuer == null) return new Outcome(null, null, UNPINNED_ISSUER);
        Instant notBefore = certificate.getNotBefore().toInstant();
        Instant notAfter = certificate.getNotAfter().toInstant();
        if (Duration.between(notBefore, notAfter).compareTo(maxCertificateLifetime) > 0) {
            return new Outcome(null, issuer, LIFETIME_EXCEEDED);
        }
        SecurityIdentity identity = new SecurityIdentity(binding.principalId(), IdentityType.HUMAN,
                BreakGlassConfig.PLATFORM, BreakGlassConfig.PLATFORM, null, binding.scopes(), subject,
                Instant.now(), notAfter, notAfter);
        return new Outcome(identity, issuer, null);
    }

    /** The fingerprint of the pinned CA that signed the certificate, or null if none did. */
    private String pinnedIssuer(X509Certificate certificate) {
        for (Map.Entry<String, X509Certificate> pinned : pinnedIssuers.entrySet()) {
            X509Certificate authority = pinned.getValue();
            if (!authority.getSubjectX500Principal().equals(certificate.getIssuerX500Principal())) continue;
            try {
                certificate.verify(authority.getPublicKey());
                return pinned.getKey();
            } catch (GeneralSecurityException exception) {
                // Same issuer name, different key: another CA, so not pinned.
            }
        }
        return null;
    }
}
