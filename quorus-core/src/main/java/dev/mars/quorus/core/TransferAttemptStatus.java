/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package dev.mars.quorus.core;

/** Canonical lifecycle of one immutable transfer execution attempt. */
public enum TransferAttemptStatus {
    OFFERED(false),
    ACCEPTED(false),
    IN_PROGRESS(false),
    COMPLETED(true),
    FAILED(true),
    REJECTED(true),
    CANCELLED(true),
    EXPIRED(true),
    FENCED(true),
    RECONCILIATION_REQUIRED(true);

    private final boolean terminal;

    TransferAttemptStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public boolean canTransitionTo(TransferAttemptStatus target) {
        if (target == null || terminal) {
            return false;
        }
        return switch (this) {
            case OFFERED -> target == ACCEPTED || target == REJECTED || target == CANCELLED
                    || target == EXPIRED || target == FENCED;
            case ACCEPTED -> target == IN_PROGRESS || target == FAILED || target == CANCELLED
                    || target == EXPIRED || target == FENCED;
            case IN_PROGRESS -> target == IN_PROGRESS || target == COMPLETED || target == FAILED
                    || target == CANCELLED || target == EXPIRED || target == FENCED
                    || target == RECONCILIATION_REQUIRED;
            case COMPLETED, FAILED, REJECTED, CANCELLED, EXPIRED, FENCED,
                    RECONCILIATION_REQUIRED -> false;
        };
    }
}
