/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.http.handlers;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Serves the exact OpenAPI contract bundled with the running controller. */
public final class OpenApiHandler implements Handler<RoutingContext> {

    private static final String RESOURCE = "/openapi/quorus-controller-v1.yaml";

    @Override
    public void handle(RoutingContext context) {
        try (InputStream input = OpenApiHandler.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Bundled OpenAPI contract is missing");
            }
            context.response()
                    .putHeader("Content-Type", "application/vnd.oai.openapi;version=3.1.0")
                    .end(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            context.fail(exception);
        }
    }
}
