/* Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache-2.0. */
package dev.mars.quorus.controller.http;

import dev.mars.quorus.connection.ConnectionAccessRequest;
import dev.mars.quorus.connection.ConnectionPolicyEnforcer;
import dev.mars.quorus.connection.HostResolver;
import dev.mars.quorus.connection.ServiceConnection;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs blocking name resolution and its policy checks away from HTTP event loops. */
public final class ControllerConnectionAuthorizer {
    private final HostResolver resolver;
    private final ConnectionPolicyEnforcer policy = new ConnectionPolicyEnforcer();
    private final int maxConcurrent;
    private final long timeoutMillis;
    private final AtomicInteger outstanding = new AtomicInteger();

    public ControllerConnectionAuthorizer(HostResolver resolver) {
        this(resolver, 8, 5_000);
    }

    public ControllerConnectionAuthorizer(HostResolver resolver, int maxConcurrent, long timeoutMillis) {
        this.resolver = Objects.requireNonNull(resolver);
        if (maxConcurrent < 1 || timeoutMillis < 1) throw new IllegalArgumentException("DNS limits must be positive");
        this.maxConcurrent = maxConcurrent;
        this.timeoutMillis = timeoutMillis;
    }

    public Future<ConnectionPolicyEnforcer.ConnectionAuthorization> authorize(
            Vertx vertx, ServiceConnection connection, ConnectionAccessRequest request) {
        if (!admit()) {
            return Future.failedFuture(new QuorusApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "DNS authorization capacity is exhausted"));
        }
        Promise<ConnectionPolicyEnforcer.ConnectionAuthorization> result = Promise.promise();
        long started = System.nanoTime();
        long timer;
        try {
            timer = vertx.setTimer(timeoutMillis, ignored -> result.tryFail(timeout()));
        } catch (RuntimeException error) {
            outstanding.decrementAndGet();
            return Future.failedFuture(error);
        }
        try {
            vertx.executeBlocking(() -> {
                // Do not start a native lookup if it expired while awaiting a worker.
                if (result.future().isComplete()) return null;
                return policy.authorizeController(connection, request, resolver);
            }, false).onComplete(outcome -> {
                // Release only on worker completion (or rejected scheduling), never on caller timeout.
                outstanding.decrementAndGet();
                vertx.cancelTimer(timer);
                if (System.nanoTime() - started >= TimeUnit.MILLISECONDS.toNanos(timeoutMillis)) {
                    result.tryFail(timeout());
                } else if (outcome.failed()) {
                    result.tryFail(outcome.cause());
                } else {
                    result.tryComplete(outcome.result());
                }
            });
        } catch (RuntimeException error) {
            vertx.cancelTimer(timer);
            outstanding.decrementAndGet();
            result.tryFail(error);
        }
        return result.future();
    }

    private boolean admit() {
        int active;
        do {
            active = outstanding.get();
            if (active >= maxConcurrent) return false;
        } while (!outstanding.compareAndSet(active, active + 1));
        return true;
    }

    private QuorusApiException timeout() {
        return new QuorusApiException(ErrorCode.TIMEOUT, "DNS authorization timed out");
    }
}
