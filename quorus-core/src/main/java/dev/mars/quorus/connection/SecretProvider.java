/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

/** Provider SPI for resolving short-lived secrets outside authoritative Quorus state. */
public interface SecretProvider {
    String providerId();
    SecretLease resolve(SecretReference reference) throws Exception;
}
