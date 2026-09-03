/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Enforces agent policy before selecting or invoking any external secret provider. */
public final class GovernedConnectionResolver {
    private final ConnectionPolicyEnforcer enforcer;
    private final HostResolver resolver;
    private final Map<String, SecretProvider> providers;

    public GovernedConnectionResolver(ConnectionPolicyEnforcer enforcer, HostResolver resolver,
                                      Map<String, SecretProvider> providers) {
        this.enforcer = Objects.requireNonNull(enforcer, "enforcer");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.providers = Map.copyOf(providers);
    }

    public ResolvedConnection resolveAtAgent(ServiceConnection connection, SecretReference reference,
                                               ConnectionAccessRequest request) throws Exception {
        return resolveAtAgent(connection, reference, request, null, null);
    }

    public ResolvedConnection resolveAtAgent(ServiceConnection connection, SecretReference reference,
                                               ConnectionAccessRequest request, Integer expectedPolicyVersion,
                                               String expectedPolicyDigest) throws Exception {
        if (expectedPolicyVersion != null && connection.policyVersion() != expectedPolicyVersion) {
            throw new ConnectionPolicyException("Q-CONNECTION-POLICY-VERSION",
                    "Connection policy changed after controller authorization");
        }
        ConnectionPolicyEnforcer.ConnectionAuthorization authorization =
                enforcer.authorizeAtAgent(connection, request, resolver);
        if (expectedPolicyDigest != null && !expectedPolicyDigest.equals(authorization.policyDigest())) {
            throw new ConnectionPolicyException("Q-CONNECTION-POLICY-DIGEST",
                    "Connection policy digest differs from controller authorization");
        }
        if (!connection.secretReferenceId().equals(reference.secretReferenceId())
                || !connection.tenantId().equals(reference.tenantId())) {
            throw new ConnectionPolicyException("Q-SECRET-REFERENCE",
                    "Secret reference is not authorized for this service connection");
        }
        if (!reference.usableAt(Instant.now())) {
            throw new ConnectionPolicyException("Q-SECRET-UNAVAILABLE", "Secret reference is revoked or expired");
        }
        SecretProvider provider = providers.get(reference.provider().toUpperCase(Locale.ROOT));
        if (provider == null) {
            throw new ConnectionPolicyException("Q-SECRET-PROVIDER", "Secret provider is not configured");
        }
        SecretLease secret = provider.resolve(reference);
        return new ResolvedConnection(authorization, secret);
    }
}
