/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.mars.quorus.testing;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared test utilities for observing Vert.x {@link Future} results using
 * Vert.x JUnit facilities rather than Java concurrency or polling libraries.
 */
public final class TestFutureUtils {

    private TestFutureUtils() {}

    /**
     * Blocks the calling thread until the given future succeeds or the timeout expires.
     *
     * @param future  the Vert.x future to await
     * @param timeout maximum time to wait
     * @param <T>     result type
     * @return the successful result
     * @throws AssertionError if the future fails or times out
     */
    public static <T> T awaitSuccess(Future<T> future, Duration timeout) {
        VertxTestContext context = new VertxTestContext();
        AtomicReference<T> result = new AtomicReference<>();
        future.onComplete(outcome -> {
            if (outcome.succeeded()) {
                result.set(outcome.result());
                context.completeNow();
            } else {
                context.failNow(outcome.cause());
            }
        });
        awaitContext(context, timeout);
        return result.get();
    }

    /**
     * Blocks the calling thread until the given future fails or the timeout expires.
     *
     * @param future  the Vert.x future to await
     * @param timeout maximum time to wait
     * @return the cause of failure
     * @throws AssertionError if the future succeeds or times out
     */
    public static Throwable awaitFailure(Future<?> future, Duration timeout) {
        VertxTestContext context = new VertxTestContext();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        future.onComplete(outcome -> {
            if (outcome.failed()) {
                failure.set(outcome.cause());
                context.completeNow();
            } else {
                context.failNow(new AssertionError("Expected future to fail"));
            }
        });
        awaitContext(context, timeout);
        assertTrue(failure.get() != null, "Expected future to fail");
        return failure.get();
    }

    /**
     * Returns a future that completes when a condition becomes true, using only
     * a Vert.x periodic timer for asynchronous coordination.
     */
    public static Future<Void> eventually(Vertx vertx, BooleanSupplier condition, Duration timeout) {
        Promise<Void> promise = Promise.promise();
        long deadline = System.nanoTime() + timeout.toNanos();
        if (condition.getAsBoolean()) {
            return Future.succeededFuture();
        }
        long timerId = vertx.setPeriodic(10, id -> {
            try {
                if (condition.getAsBoolean()) {
                    vertx.cancelTimer(id);
                    promise.tryComplete();
                } else if (System.nanoTime() >= deadline) {
                    vertx.cancelTimer(id);
                    promise.tryFail(new AssertionError("Condition was not satisfied within " + timeout));
                }
            } catch (Throwable failure) {
                vertx.cancelTimer(id);
                promise.tryFail(failure);
            }
        });
        promise.future().onComplete(ignored -> vertx.cancelTimer(timerId));
        return promise.future();
    }

    private static void awaitContext(VertxTestContext context, Duration timeout) {
        try {
            if (!context.awaitCompletion(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("Vert.x future did not complete within " + timeout);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while awaiting Vert.x future", exception);
        }
        if (context.failed()) {
            throw new AssertionError("Future failed", context.causeOfFailure());
        }
    }
}
