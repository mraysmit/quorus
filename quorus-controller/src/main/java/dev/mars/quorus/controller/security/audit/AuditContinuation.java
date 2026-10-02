/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security.audit;

import io.vertx.core.Context;
import io.vertx.ext.web.RoutingContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Continues a request once its audit events are durable, without blocking the event loop (register item
 * ENG-16). The request still waits for its audit: nothing after the audit runs until the events are on disk,
 * and a failed audit fails the request, as before.
 */
public final class AuditContinuation {

    private AuditContinuation() {
    }

    /** Runs {@code then} on the request's context once {@code durable} completes; fails the request if it fails. */
    public static void afterDurable(RoutingContext context, CompletableFuture<Void> durable, Runnable then) {
        if (durable.isDone()) {
            continueWith(context, durable, then);
            return;
        }
        // The body is read by the body handler later in the chain; pausing keeps a body that arrives while the
        // audit is pending. The body handler resumes the request; a request answered without reaching it is
        // resumed below so the connection can drain.
        context.request().pause();
        Context vertxContext = context.vertx().getOrCreateContext();
        durable.whenComplete((ignored, failure) -> vertxContext.runOnContext(unused -> {
            continueWith(context, durable, then);
            if (context.response().ended()) {
                context.request().resume();
            }
        }));
    }

    private static void continueWith(RoutingContext context, CompletableFuture<Void> durable, Runnable then) {
        try {
            durable.join();
        } catch (CompletionException exception) {
            context.fail(exception.getCause());
            return;
        } catch (RuntimeException exception) {
            context.fail(exception);
            return;
        }
        try {
            then.run();
        } catch (RuntimeException exception) {
            // Off the router's call stack, so an escaping exception would not reach the failure handler.
            context.fail(exception);
        }
    }
}
