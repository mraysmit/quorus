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

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared test utilities for blocking on Vert.x {@link Future} results
 * without using {@code toCompletionStage().toCompletableFuture().get/join()}.
 *
 * <p>Uses Awaitility to poll for completion, which is safe from both
 * event-loop and worker threads.
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
        AtomicReference<AsyncResult<T>> outcomeRef = new AtomicReference<>();

        future.onComplete(outcomeRef::set);

        await().atMost(timeout)
                .pollInterval(Duration.ofMillis(10))
                .until(() -> outcomeRef.get() != null);

        AsyncResult<T> outcome = outcomeRef.get();
        if (outcome.failed()) {
            throw new AssertionError("Future failed", outcome.cause());
        }
        return outcome.result();
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
        AtomicReference<AsyncResult<?>> outcomeRef = new AtomicReference<>();

        future.onComplete(outcomeRef::set);

        await().atMost(timeout)
                .pollInterval(Duration.ofMillis(10))
                .until(() -> outcomeRef.get() != null);

        AsyncResult<?> outcome = outcomeRef.get();
        assertTrue(outcome.failed(), "Expected future to fail");
        return outcome.cause();
    }
}
