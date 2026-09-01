/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security.audit;

import java.util.Arrays;
import java.util.List;

/** Durable destination for security decision evidence. */
@FunctionalInterface
public interface AuditSink extends AutoCloseable {
    void append(AuditEvent event);

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
                // Retained evidence is configured first, so a downstream operational failure cannot erase evidence.
                for (AuditSink delegate : delegates) delegate.append(event);
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
