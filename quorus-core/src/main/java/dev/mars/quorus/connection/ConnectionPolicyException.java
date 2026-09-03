/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

/** Fail-closed policy decision with a stable, non-sensitive reason code. */
public final class ConnectionPolicyException extends Exception {
    private final String decisionCode;

    public ConnectionPolicyException(String decisionCode, String message) {
        super(message);
        this.decisionCode = decisionCode;
    }

    public String decisionCode() { return decisionCode; }
}
