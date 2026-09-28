/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security.audit;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Durable destination for security decision evidence. */
@FunctionalInterface
public interface AuditSink extends AutoCloseable {
    /** Appends and returns once the event is durable. */
    void append(AuditEvent event);

    /**
     * Appends without blocking the caller; the future completes once the event is durable (ENG-16).
     * The default runs {@link #append} on the calling thread.
     */
    default CompletableFuture<Void> appendAsync(AuditEvent event) {
        try {
            append(event);
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    default void close() {
    }

    static AuditSink noOp() {
        return event -> { };
    }

    static AuditSink composite(AuditSink... sinks) {
        List<AuditSink> delegates = List.copyOf(Arrays.asList(sinks));
        if (delegates.isEmpty()) return noOp();
        return new AuditSink() {
            @Override
            public void append(AuditEvent event) {
                try {
                    appendAsync(event).join();
                } catch (CompletionException exception) {
                    throw exception.getCause() instanceof RuntimeException runtime ? runtime
                            : new IllegalStateException(exception.getCause());
                }
            }

            @Override
            public CompletableFuture<Void> appendAsync(AuditEvent event) {
                // Retained evidence is configured first and must be durable before the next sink is written,
                // so a downstream operational failure cannot erase evidence.
                CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
                for (AuditSink delegate : delegates) {
                    chain = chain.thenCompose(ignored -> delegate.appendAsync(event));
                }
                return chain;
            }

            @Override
            public void close() {
                RuntimeException failure = null;
                for (int index = delegates.size() - 1; index >= 0; index--) {
                    try {
                        delegates.get(index).close();
                    } catch (RuntimeException exception) {
                        if (failure == null) failure = exception;
                        else failure.addSuppressed(exception);
                    }
                }
                if (failure != null) throw failure;
            }
        };
    }
}
