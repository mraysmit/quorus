/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

/** Agent-only authorized endpoint plus short-lived secret lease. */
public record ResolvedConnection(ConnectionPolicyEnforcer.ConnectionAuthorization authorization,
                                 SecretLease secret) implements AutoCloseable {
    @Override public void close() { secret.close(); }
}
