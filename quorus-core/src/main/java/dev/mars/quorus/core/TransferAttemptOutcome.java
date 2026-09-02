/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package dev.mars.quorus.core;

/** Classified authoritative outcome of a transfer attempt. */
public enum TransferAttemptOutcome {
    NONE,
    SUCCEEDED,
    RETRYABLE_FAILURE,
    TERMINAL_FAILURE,
    REJECTED,
    CANCELLED,
    LEASE_EXPIRED,
    SUPERSEDED,
    RECONCILIATION_REQUIRED
}
