/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Runtime certificate revocation and trust-bundle observation shared by HTTP and Raft boundaries.
 * TLS chain validation remains in the transport; this state applies lifecycle policy to every request or RPC.
 */
public final class CertificateTrustState {
    private static final AttributeKey<String> TRUST_VERSION = AttributeKey.stringKey("trust.bundle.version");

    private final AtomicReference<Snapshot> snapshot;
    private final Duration expiryWarningThreshold;
    private final AtomicLong lastSecondsRemaining = new AtomicLong(Long.MAX_VALUE);
    private final LongHistogram expiryHistogram;
    private final LongCounter rejectionCounter;
    private final LongCounter updateCounter;

    public CertificateTrustState(String trustBundleVersion, Set<String> revokedCertificateSerials,
                                 Duration expiryWarningThreshold) {
        this.expiryWarningThreshold = Objects.requireNonNull(expiryWarningThreshold, "expiryWarningThreshold");
        if (expiryWarningThreshold.isNegative() || expiryWarningThreshold.isZero()) {
            throw new IllegalArgumentException("expiryWarningThreshold must be positive");
        }
        this.snapshot = new AtomicReference<>(new Snapshot(requireVersion(trustBundleVersion),
                normalize(revokedCertificateSerials), Instant.now()));
        var meter = GlobalOpenTelemetry.getMeter("quorus-controller-security");
        this.expiryHistogram = meter.histogramBuilder("quorus.security.certificate.seconds_remaining")
                .ofLongs().setUnit("s")
                .setDescription("Observed lifetime remaining for authenticated peer certificates")
                .build();
        this.rejectionCounter = meter.counterBuilder("quorus.security.certificate.rejection.total")
                .setUnit("1").setDescription("Certificate policy rejections").build();
        this.updateCounter = meter.counterBuilder("quorus.security.trust_bundle.update.total")
                .setUnit("1").setDescription("Runtime trust policy updates").build();
        meter.gaugeBuilder("quorus.security.certificate.current_seconds_remaining")
                .ofLongs().setUnit("s")
                .setDescription("Most recently observed peer-certificate lifetime")
                .buildWithCallback(measurement -> {
                    long value = lastSecondsRemaining.get();
                    if (value != Long.MAX_VALUE) {
                        measurement.record(value, Attributes.of(TRUST_VERSION, snapshot.get().trustBundleVersion()));
                    }
                });
        meter.gaugeBuilder("quorus.security.trust_bundle.info")
                .ofLongs().setUnit("1")
                .setDescription("Active trust-bundle version marker")
                .buildWithCallback(measurement -> measurement.record(1,
                        Attributes.of(TRUST_VERSION, snapshot.get().trustBundleVersion())));
    }

    public static CertificateTrustState from(SecurityConfig config) {
        return new CertificateTrustState(config.trustBundleVersion(), config.revokedCertificateSerials(),
                config.certificateExpiryWarningThreshold());
    }

    public Evaluation evaluate(X509Certificate certificate) {
        Objects.requireNonNull(certificate, "certificate");
        Snapshot current = snapshot.get();
        String serial = normalize(certificate.getSerialNumber().toString(16));
        long secondsRemaining = Duration.between(Instant.now(), certificate.getNotAfter().toInstant()).getSeconds();
        lastSecondsRemaining.set(secondsRemaining);
        expiryHistogram.record(secondsRemaining, Attributes.of(TRUST_VERSION, current.trustBundleVersion()));
        ExpiryAlertState alertState = secondsRemaining <= 0 ? ExpiryAlertState.EXPIRED
                : secondsRemaining <= expiryWarningThreshold.toSeconds()
                ? ExpiryAlertState.WARNING : ExpiryAlertState.OK;
        boolean revoked = current.revokedCertificateSerials().contains(serial);
        return new Evaluation(current.trustBundleVersion(), serial,
                certificate.getSubjectX500Principal().getName(), certificate.getNotAfter().toInstant(),
                secondsRemaining, alertState, revoked);
    }

    public Snapshot update(String trustBundleVersion, Set<String> revokedCertificateSerials) {
        Snapshot updated = new Snapshot(requireVersion(trustBundleVersion), normalize(revokedCertificateSerials),
                Instant.now());
        snapshot.set(updated);
        updateCounter.add(1, Attributes.of(TRUST_VERSION, updated.trustBundleVersion()));
        return updated;
    }

    public void recordRejection(String reason) {
        rejectionCounter.add(1, Attributes.builder()
                .put(TRUST_VERSION, snapshot.get().trustBundleVersion())
                .put("reason", reason)
                .build());
    }

    public Snapshot snapshot() {
        return snapshot.get();
    }

    public Duration expiryWarningThreshold() {
        return expiryWarningThreshold;
    }

    private static String requireVersion(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("trustBundleVersion is required");
        }
        return version.trim();
    }

    private static Set<String> normalize(Set<String> serials) {
        if (serials == null) return Set.of();
        return serials.stream().filter(Objects::nonNull).map(CertificateTrustState::normalize)
                .filter(value -> !value.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String serial) {
        return serial == null ? "" : serial.replace(":", "").trim().toUpperCase(Locale.ROOT);
    }

    public enum ExpiryAlertState { OK, WARNING, EXPIRED }

    public record Snapshot(String trustBundleVersion, Set<String> revokedCertificateSerials, Instant loadedAt) {
        public Snapshot {
            revokedCertificateSerials = Set.copyOf(revokedCertificateSerials);
        }
    }

    public record Evaluation(String trustBundleVersion, String certificateSerial, String certificateSubject,
                             Instant certificateExpiresAt, long certificateSecondsRemaining,
                             ExpiryAlertState expiryAlertState, boolean revoked) {
    }
}
