/* Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache 2.0. */
package dev.mars.quorus.controller.state;

import java.io.Serializable;
import java.time.Instant;

/** Immutable, deterministically ordered transfer-domain event. */
public record TransferEvent(
        String eventId,
        long sequence,
        String eventType,
        Instant occurredAt,
        Instant recordedAt,
        String jobId,
        String tenantId,
        String businessService,
        String attemptId,
        String agentId,
        Long bytesTransferred,
        Long totalBytes,
        Long reportSequence,
        String previousState,
        String currentState) implements Serializable { }
