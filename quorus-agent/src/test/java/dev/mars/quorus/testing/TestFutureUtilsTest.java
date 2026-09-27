/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.testing;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.mars.quorus.testing.TestFutureUtils.awaitFailure;
import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static dev.mars.quorus.testing.TestFutureUtils.eventually;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@ExtendWith(VertxExtension.class)
class TestFutureUtilsTest {

    @Test
    void observesSuccessfulAndFailedVertxFutures() {
        assertEquals("ok", awaitSuccess(Future.succeededFuture("ok"), Duration.ofSeconds(1)));
        assertInstanceOf(IllegalStateException.class,
                awaitFailure(Future.failedFuture(new IllegalStateException("expected")), Duration.ofSeconds(1)));
    }

    @Test
    void eventuallyUsesVertxTimersForConditions(Vertx vertx) {
        AtomicBoolean ready = new AtomicBoolean();
        vertx.setTimer(20, ignored -> ready.set(true));

        awaitSuccess(eventually(vertx, ready::get, Duration.ofSeconds(1)), Duration.ofSeconds(2));
        assertInstanceOf(AssertionError.class,
                awaitFailure(eventually(vertx, () -> false, Duration.ofMillis(30)), Duration.ofSeconds(1)));
    }
}
