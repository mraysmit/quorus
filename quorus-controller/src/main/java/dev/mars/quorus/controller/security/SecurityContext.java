/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security;

import dev.mars.quorus.controller.http.ErrorCode;
import dev.mars.quorus.controller.http.QuorusApiException;
import io.vertx.ext.web.RoutingContext;

/** Typed access to the verified identity attached to a request. */
public final class SecurityContext {
    private static final String IDENTITY_KEY = SecurityContext.class.getName() + ".identity";

    private SecurityContext() {
    }

    public static void setIdentity(RoutingContext context, SecurityIdentity identity) {
        context.put(IDENTITY_KEY, identity);
    }

    public static SecurityIdentity identity(RoutingContext context) {
        return context.get(IDENTITY_KEY);
    }

    public static String trustedTenant(RoutingContext context, String callerTenant) {
        SecurityIdentity identity = identity(context);
        if (identity == null) {
            return callerTenant;
        }
        if (callerTenant != null && !callerTenant.isBlank() && !identity.tenantId().equals(callerTenant)) {
            throw new QuorusApiException(ErrorCode.FORBIDDEN,
                    "Caller tenant does not match the authenticated tenant");
        }
        return identity.tenantId();
    }
}
