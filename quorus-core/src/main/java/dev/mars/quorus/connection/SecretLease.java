/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Closeable in-memory secret lease whose backing characters are wiped on close. */
public final class SecretLease implements AutoCloseable {
    private final String referenceId;
    private final char[] value;
    private final Instant expiresAt;
    private final AtomicBoolean closed = new AtomicBoolean();

    public SecretLease(String referenceId, char[] value, Instant expiresAt) {
        this.referenceId = Objects.requireNonNull(referenceId, "referenceId");
        this.value = Arrays.copyOf(Objects.requireNonNull(value, "value"), value.length);
        this.expiresAt = expiresAt;
    }

    public String referenceId() { return referenceId; }
    public Instant expiresAt() { return expiresAt; }
    public char[] copyValue() {
        if (closed.get()) throw new IllegalStateException("Secret lease is closed");
        return Arrays.copyOf(value, value.length);
    }
    @Override public void close() {
        if (closed.compareAndSet(false, true)) Arrays.fill(value, '\0');
    }
    @Override public String toString() { return "SecretLease[referenceId=" + referenceId + ", redacted=true]"; }
}
