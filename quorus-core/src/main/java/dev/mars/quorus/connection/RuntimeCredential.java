/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Agent-memory-only authentication and trust context. Never serialize this object. */
public final class RuntimeCredential implements AutoCloseable {
    private final String identity;
    private final ServiceConnection.AuthenticationType authenticationType;
    private final char[] secret;
    private final Set<String> sshHostKeyFingerprints;
    private final Set<String> approvedCaIds;
    private final Set<String> tlsPeerFingerprints;
    private final String minimumTlsVersion;
    private final List<String> approvedResolvedAddresses;
    private final AtomicBoolean closed = new AtomicBoolean();

    public RuntimeCredential(String identity, ServiceConnection.AuthenticationType authenticationType,
                             char[] secret, Set<String> sshHostKeyFingerprints,
                             Set<String> tlsPeerFingerprints, String minimumTlsVersion) {
        this(identity, authenticationType, secret, sshHostKeyFingerprints, Set.of(),
                tlsPeerFingerprints, minimumTlsVersion, List.of());
    }

    public RuntimeCredential(String identity, ServiceConnection.AuthenticationType authenticationType,
                             char[] secret, Set<String> sshHostKeyFingerprints,
                             Set<String> approvedCaIds, Set<String> tlsPeerFingerprints,
                             String minimumTlsVersion) {
        this(identity, authenticationType, secret, sshHostKeyFingerprints, approvedCaIds,
                tlsPeerFingerprints, minimumTlsVersion, List.of());
    }

    public RuntimeCredential(String identity, ServiceConnection.AuthenticationType authenticationType,
                             char[] secret, Set<String> sshHostKeyFingerprints,
                             Set<String> approvedCaIds, Set<String> tlsPeerFingerprints,
                             String minimumTlsVersion, List<String> approvedResolvedAddresses) {
        this.identity = identity;
        this.authenticationType = authenticationType;
        this.secret = Arrays.copyOf(secret, secret.length);
        this.sshHostKeyFingerprints = Set.copyOf(sshHostKeyFingerprints);
        this.approvedCaIds = Set.copyOf(approvedCaIds);
        this.tlsPeerFingerprints = Set.copyOf(tlsPeerFingerprints);
        this.minimumTlsVersion = minimumTlsVersion;
        this.approvedResolvedAddresses = List.copyOf(approvedResolvedAddresses);
    }

    public String identity() { return identity; }
    public ServiceConnection.AuthenticationType authenticationType() { return authenticationType; }
    public Set<String> sshHostKeyFingerprints() { return sshHostKeyFingerprints; }
    public Set<String> approvedCaIds() { return approvedCaIds; }
    public Set<String> tlsPeerFingerprints() { return tlsPeerFingerprints; }
    public String minimumTlsVersion() { return minimumTlsVersion; }
    public List<String> approvedResolvedAddresses() { return approvedResolvedAddresses; }
    public char[] copySecret() {
        if (closed.get()) throw new IllegalStateException("Runtime credential is closed");
        return Arrays.copyOf(secret, secret.length);
    }
    @Override public void close() {
        if (closed.compareAndSet(false, true)) Arrays.fill(secret, '\0');
    }
    @Override public String toString() {
        return "RuntimeCredential[identity=" + identity + ", authenticationType=" + authenticationType
                + ", redacted=true]";
    }
}
