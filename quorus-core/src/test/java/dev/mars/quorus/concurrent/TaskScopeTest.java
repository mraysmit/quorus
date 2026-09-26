/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.concurrent;

import dev.mars.quorus.concurrent.TaskScope.Subtask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural contract of the Quorus task scope (plan workstream RT-02; ADR-0012 decision RT-Q1).
 *
 * <p>Synchronisation uses handshakes and interruption only, never sleeps. Every test has a
 * preemptive timeout, so a hang fails instead of stalling the build.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class TaskScopeTest {

    private static final Duration GENEROUS = Duration.ofSeconds(5);

    @Test
    void joinReturnsAfterAllSubtasksSucceedAndResultsAreAvailable() throws Exception {
        try (TaskScope scope = TaskScope.open("all-succeed", GENEROUS)) {
            Subtask<String> value = scope.fork(() -> "result");
            Subtask<Boolean> onVirtualThread = scope.fork(() -> Thread.currentThread().isVirtual());

            scope.join();

            assertEquals(Subtask.State.SUCCESS, value.state());
            assertEquals("result", value.get());
            assertTrue(onVirtualThread.get(), "subtasks must run on virtual threads");
        }
    }

    @Test
    void firstFailureFailsJoinWithItsCauseAndCancelsSiblings() throws Exception {
        CompletableFuture<Void> siblingStarted = new CompletableFuture<>();
        AtomicBoolean siblingInterrupted = new AtomicBoolean();
        IllegalStateException failure = new IllegalStateException("transfer step failed");
        TaskScope.FailedException thrown;
        Subtask<Void> failing;
        Subtask<Void> sibling;

        try (TaskScope scope = TaskScope.open("fail-fast", GENEROUS)) {
            sibling = scope.fork(() -> {
                siblingStarted.complete(null);
                return blockUntilInterrupted(siblingInterrupted);
            });
            failing = scope.fork(() -> {
                siblingStarted.join();
                throw failure;
            });

            thrown = assertThrows(TaskScope.FailedException.class, scope::join);
        }

        assertSame(failure, thrown.getCause());
        assertEquals(Subtask.State.FAILED, failing.state());
        assertSame(failure, failing.exception());
        assertThrows(IllegalStateException.class, failing::get);
        assertTrue(siblingInterrupted.get(), "the sibling must be cancelled by interruption");
        assertEquals(Subtask.State.UNAVAILABLE, sibling.state());
    }

    @Test
    void deadlineExpiryFailsJoinWithTimeoutAndCancelsUnfinishedSubtasks() throws Exception {
        AtomicBoolean interrupted = new AtomicBoolean();
        Subtask<Void> unfinished;

        try (TaskScope scope = TaskScope.open("deadline", Duration.ofMillis(200))) {
            unfinished = scope.fork(() -> blockUntilInterrupted(interrupted));

            assertThrows(TaskScope.TimeoutException.class, scope::join);
        }

        assertTrue(interrupted.get(), "the unfinished subtask must be cancelled by interruption");
        assertEquals(Subtask.State.UNAVAILABLE, unfinished.state());
    }

    @Test
    void closeWithoutJoinCancelsAndAwaitsSubtasksThenReportsTheMissingJoin() {
        CompletableFuture<Void> started = new CompletableFuture<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicBoolean ended = new AtomicBoolean();
        TaskScope scope = TaskScope.open("no-join", GENEROUS);
        scope.fork(() -> {
            started.complete(null);
            try {
                return blockUntilInterrupted(interrupted);
            } finally {
                ended.set(true);
            }
        });
        started.join();

        assertThrows(IllegalStateException.class, scope::close);

        assertTrue(interrupted.get(), "close must cancel the unfinished subtask");
        assertTrue(ended.get(), "close must wait for the subtask to end");
    }

    @Test
    void onlyTheOwnerThreadMayForkOrJoin() throws Exception {
        try (TaskScope scope = TaskScope.open("owner", GENEROUS)) {
            Throwable forkError = runOnAnotherThread(() -> scope.fork(() -> "not allowed"));
            Throwable joinError = runOnAnotherThread(scope::join);

            assertInstanceOf(WrongThreadException.class, forkError);
            assertInstanceOf(WrongThreadException.class, joinError);
            scope.join();
        }
    }

    @Test
    void joinHappensOnceAndNoSubtaskMayBeForkedAfterIt() throws Exception {
        try (TaskScope scope = TaskScope.open("join-once", GENEROUS)) {
            scope.fork(() -> "first");
            scope.join();

            assertThrows(IllegalStateException.class, () -> scope.fork(() -> "late"));
            assertThrows(IllegalStateException.class, scope::join);
        }
    }

    // ---- Retrospective characterization (plan §6.1): behaviour added during the green stage
    // ---- without a preceding failing test. Recorded as characterization, not TDD evidence.

    @Test
    void openRejectsMissingNameAndNonPositiveTimeout() {
        assertThrows(NullPointerException.class, () -> TaskScope.open(null, GENEROUS));
        assertThrows(NullPointerException.class, () -> TaskScope.open("no-timeout", null));
        assertThrows(IllegalArgumentException.class, () -> TaskScope.open("zero", Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> TaskScope.open("negative", Duration.ofMillis(-1)));
    }

    @Test
    void ownerInterruptedDuringJoinCancelsSubtasksAndPropagatesTheInterrupt() throws Exception {
        CompletableFuture<Void> started = new CompletableFuture<>();
        AtomicBoolean subtaskInterrupted = new AtomicBoolean();
        AtomicReference<Throwable> ownerOutcome = new AtomicReference<>();

        Thread owner = Thread.ofVirtual().start(() -> {
            try (TaskScope scope = TaskScope.open("owner-interrupt", GENEROUS)) {
                scope.fork(() -> {
                    started.complete(null);
                    return blockUntilInterrupted(subtaskInterrupted);
                });
                try {
                    scope.join();
                } catch (Throwable t) {
                    ownerOutcome.set(t);
                }
            }
        });
        started.join();
        owner.interrupt();
        owner.join();

        assertInstanceOf(InterruptedException.class, ownerOutcome.get());
        assertTrue(subtaskInterrupted.get(), "interrupting the owner must cancel the subtasks");
    }

    @Test
    void forkAfterCancellationReturnsAnUnstartedSubtask() throws Exception {
        AtomicBoolean ran = new AtomicBoolean();
        CompletableFuture<Void> siblingStarted = new CompletableFuture<>();
        CompletableFuture<Void> scopeCancelled = new CompletableFuture<>();
        try (TaskScope scope = TaskScope.open("fork-after-cancel", GENEROUS)) {
            scope.fork(() -> {
                siblingStarted.complete(null);
                try {
                    return blockUntilInterrupted(new AtomicBoolean());
                } finally {
                    scopeCancelled.complete(null);   // the scope marks itself cancelled before interrupting
                }
            });
            scope.fork(() -> {
                siblingStarted.join();
                throw new IllegalStateException("cancel the scope");
            });
            scopeCancelled.join();

            Subtask<Boolean> late = scope.fork(() -> ran.getAndSet(true));

            assertThrows(TaskScope.FailedException.class, scope::join);
            assertEquals(Subtask.State.UNAVAILABLE, late.state());
        }
        assertTrue(!ran.get(), "a subtask forked after cancellation must not run");
    }

    @Test
    void ownerInterruptedWhileCloseWaitsKeepsWaitingAndRestoresTheInterrupt() throws Exception {
        CompletableFuture<Void> subtaskStarted = new CompletableFuture<>();
        CompletableFuture<Void> subtaskCancelled = new CompletableFuture<>();
        CompletableFuture<Void> release = new CompletableFuture<>();
        AtomicBoolean subtaskEnded = new AtomicBoolean();
        AtomicBoolean endedBeforeCloseReturned = new AtomicBoolean();
        AtomicBoolean interruptRestored = new AtomicBoolean();

        Thread owner = Thread.ofVirtual().start(() -> {
            TaskScope scope = TaskScope.open("close-interrupted", GENEROUS);
            scope.fork(() -> {
                subtaskStarted.complete(null);
                try {
                    return blockUntilInterrupted(new AtomicBoolean());
                } catch (InterruptedException cancelled) {
                    subtaskCancelled.complete(null);
                    release.join();                 // outlives the cancellation until released
                    return null;
                } finally {
                    subtaskEnded.set(true);
                }
            });
            subtaskStarted.join();                   // close only once the subtask is running
            try {
                scope.close();                       // cancels, then waits for the subtask
            } catch (IllegalStateException closedWithoutJoin) {
                endedBeforeCloseReturned.set(subtaskEnded.get());
                interruptRestored.set(Thread.currentThread().isInterrupted());
            }
        });
        subtaskCancelled.join();
        owner.interrupt();                           // interrupt the owner while close() waits
        release.complete(null);
        owner.join();

        assertTrue(endedBeforeCloseReturned.get(), "close must keep waiting for the subtask despite the interrupt");
        assertTrue(interruptRestored.get(), "close must restore the owner's interrupt status");
    }

    @Test
    void aSubtaskThatSucceedsAfterCancellationHasItsResultDiscarded() throws Exception {
        CompletableFuture<Void> slowStarted = new CompletableFuture<>();
        CompletableFuture<Void> release = new CompletableFuture<>();
        Subtask<String> slow;
        try (TaskScope scope = TaskScope.open("late-success", GENEROUS)) {
            slow = scope.fork(() -> {
                slowStarted.complete(null);
                release.join();                      // ignores the cancellation interrupt
                return "too late";
            });
            scope.fork(() -> {
                slowStarted.join();
                throw new IllegalStateException("cancel the scope");
            });
            assertThrows(TaskScope.FailedException.class, scope::join);
            release.complete(null);
        }

        assertEquals(Subtask.State.UNAVAILABLE, slow.state());
    }

    @Test
    void exceptionOfASubtaskThatDidNotFailIsRejected() throws Exception {
        try (TaskScope scope = TaskScope.open("no-exception", GENEROUS)) {
            Subtask<String> ok = scope.fork(() -> "fine");
            scope.join();
            assertThrows(IllegalStateException.class, ok::exception);
        }
    }

    @Test
    void closingTwiceIsHarmless() throws Exception {
        TaskScope scope = TaskScope.open("close-twice", GENEROUS);
        scope.fork(() -> "done");
        scope.join();
        scope.close();
        scope.close();
    }

    private static Void blockUntilInterrupted(AtomicBoolean interrupted) throws InterruptedException {
        try {
            new CompletableFuture<Void>().get();
            throw new AssertionError("an incomplete future cannot complete");
        } catch (InterruptedException e) {
            interrupted.set(true);
            throw e;
        } catch (java.util.concurrent.ExecutionException e) {
            throw new AssertionError(e);
        }
    }

    private static Throwable runOnAnotherThread(ThrowingAction action) throws InterruptedException {
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread thread = Thread.ofVirtual().start(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            }
        });
        thread.join();
        return error.get();
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
