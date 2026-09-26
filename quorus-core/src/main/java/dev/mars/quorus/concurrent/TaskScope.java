/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.concurrent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Structured task scope for Quorus (ADR-0012, decision RT-Q1).
 *
 * <p>Quorus code forks concurrent subtasks inside a scope that owns them. Subtasks run on virtual
 * threads. The first failure cancels the others, the scope deadline bounds the whole unit of work,
 * and {@link #close()} never returns while a subtask is still running. The API mirrors the
 * {@code java.util.concurrent.StructuredTaskScope} preview API ({@code open}, {@code fork},
 * {@code join}, {@code close}, {@code Subtask}). This class is built on final APIs only, so
 * production code never depends on a preview feature; its implementation moves to
 * {@code StructuredTaskScope} once that API is final in an adopted Java release.
 *
 * <p>Semantics:
 * <ul>
 *   <li>Only the thread that opened the scope may {@link #fork}, {@link #join} or {@link #close}.
 *       Other threads receive {@link WrongThreadException}.</li>
 *   <li>{@link #join()} is called once, after all forks. It returns when every subtask has succeeded,
 *       throws {@link FailedException} with the first failure as its cause, or throws
 *       {@link TimeoutException} when the deadline expires. Failure and timeout cancel the
 *       remaining subtasks by interruption.</li>
 *   <li>{@link #close()} cancels unfinished subtasks and waits for every subtask thread to end.
 *       Closing a scope that forked subtasks without joining throws {@link IllegalStateException}
 *       after that wait.</li>
 *   <li>A subtask's result or failure is recorded only while the scope is not cancelled. Subtasks
 *       that end because of cancellation stay {@link Subtask.State#UNAVAILABLE}.</li>
 * </ul>
 *
 * <p>Known difference from {@code StructuredTaskScope}: {@code ScopedValue} bindings of the owner
 * are not inherited by subtasks. Context propagation is a separate, explicit design (plan item RT-02).
 */
public final class TaskScope implements AutoCloseable {

    private enum Phase { OPEN, JOINED, CLOSED }

    private final String name;
    private final Thread owner;
    private final long deadlineNanos;
    private final ThreadFactory threadFactory;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final List<Thread> threads = new ArrayList<>();   // guarded by lock
    private int running;                                      // guarded by lock
    private boolean cancelled;                                // guarded by lock
    private Throwable firstFailure;                           // guarded by lock

    private Phase phase = Phase.OPEN;                         // owner thread only
    private boolean forked;                                   // owner thread only

    private TaskScope(String name, Duration timeout) {
        this.name = name;
        this.owner = Thread.currentThread();
        this.deadlineNanos = System.nanoTime() + timeout.toNanos();
        this.threadFactory = Thread.ofVirtual().name(name + "-", 0).factory();
    }

    /**
     * Opens a scope owned by the calling thread.
     *
     * @param name    scope name, used for subtask thread names and diagnostics
     * @param timeout deadline for the whole scope, measured from now; must be positive
     */
    public static TaskScope open(String name, Duration timeout) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive: " + timeout);
        }
        return new TaskScope(name, timeout);
    }

    /**
     * Forks a subtask on a new virtual thread. If the scope is already cancelled, the subtask is not
     * started and stays {@link Subtask.State#UNAVAILABLE}.
     */
    public <T> Subtask<T> fork(Callable<? extends T> task) {
        Objects.requireNonNull(task, "task");
        ensureOwner();
        if (phase != Phase.OPEN) {
            throw new IllegalStateException("scope '" + name + "' has already been " + phase.name().toLowerCase());
        }
        forked = true;
        SubtaskImpl<T> subtask = new SubtaskImpl<>(task);
        Thread thread = threadFactory.newThread(subtask::run);
        lock.lock();
        try {
            if (cancelled) {
                return subtask;
            }
            threads.add(thread);
            running++;
        } finally {
            lock.unlock();
        }
        thread.start();
        return subtask;
    }

    /**
     * Waits until every subtask has succeeded, a subtask fails, or the deadline expires.
     *
     * @throws FailedException      if a subtask failed; the cause is the first failure
     * @throws TimeoutException     if the deadline expired first; unfinished subtasks are cancelled
     * @throws InterruptedException if the owner is interrupted; subtasks are cancelled
     */
    public void join() throws InterruptedException {
        ensureOwner();
        if (phase != Phase.OPEN) {
            throw new IllegalStateException("scope '" + name + "' has already been " + phase.name().toLowerCase());
        }
        phase = Phase.JOINED;
        lock.lock();
        try {
            while (running > 0 && !cancelled) {
                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0) {
                    cancelLocked();
                    throw new TimeoutException("scope '" + name + "' exceeded its deadline");
                }
                try {
                    changed.awaitNanos(remaining);
                } catch (InterruptedException e) {
                    cancelLocked();
                    throw e;
                }
            }
            if (firstFailure != null) {
                throw new FailedException(firstFailure);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Cancels unfinished subtasks and waits for every subtask thread to end.
     *
     * @throws IllegalStateException after the wait, if subtasks were forked but {@link #join()} was
     *                               never called
     */
    @Override
    public void close() {
        ensureOwner();
        if (phase == Phase.CLOSED) {
            return;
        }
        boolean joined = phase == Phase.JOINED;
        phase = Phase.CLOSED;
        List<Thread> toAwait;
        lock.lock();
        try {
            cancelLocked();
            toAwait = List.copyOf(threads);
        } finally {
            lock.unlock();
        }
        boolean interrupted = false;
        for (Thread thread : toAwait) {
            while (true) {
                try {
                    thread.join();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (forked && !joined) {
            throw new IllegalStateException("scope '" + name + "' was closed without join()");
        }
    }

    private void ensureOwner() {
        if (Thread.currentThread() != owner) {
            throw new WrongThreadException("scope '" + name + "' is owned by " + owner);
        }
    }

    /** Requires {@code lock}. Idempotent. */
    private void cancelLocked() {
        if (cancelled) {
            return;
        }
        cancelled = true;
        Thread current = Thread.currentThread();
        for (Thread thread : threads) {
            if (thread != current) {
                thread.interrupt();
            }
        }
        changed.signalAll();
    }

    private final class SubtaskImpl<T> implements Subtask<T> {

        private final Callable<? extends T> task;
        private volatile State state = State.UNAVAILABLE;
        private volatile T result;
        private volatile Throwable exception;

        private SubtaskImpl(Callable<? extends T> task) {
            this.task = task;
        }

        private void run() {
            try {
                if (isCancelled()) {
                    return;
                }
                T value = task.call();
                lock.lock();
                try {
                    if (!cancelled) {
                        result = value;
                        state = State.SUCCESS;
                    }
                } finally {
                    lock.unlock();
                }
            } catch (Throwable failure) {
                lock.lock();
                try {
                    if (!cancelled) {
                        exception = failure;
                        state = State.FAILED;
                        firstFailure = failure;
                        cancelLocked();
                    }
                } finally {
                    lock.unlock();
                }
            } finally {
                lock.lock();
                try {
                    running--;
                    changed.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        }

        private boolean isCancelled() {
            lock.lock();
            try {
                return cancelled;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public State state() {
            return state;
        }

        @Override
        public T get() {
            if (state != State.SUCCESS) {
                throw new IllegalStateException("subtask did not succeed: " + state);
            }
            return result;
        }

        @Override
        public Throwable exception() {
            if (state != State.FAILED) {
                throw new IllegalStateException("subtask did not fail: " + state);
            }
            return exception;
        }
    }

    /** A forked subtask. */
    public interface Subtask<T> extends Supplier<T> {

        /** Subtask outcome. */
        enum State {
            /** Not finished, or ended because the scope was cancelled. */
            UNAVAILABLE,
            /** Completed with a result. */
            SUCCESS,
            /** Failed; {@link #exception()} returns the failure. */
            FAILED
        }

        State state();

        /** @throws IllegalStateException unless the subtask succeeded */
        @Override
        T get();

        /** @throws IllegalStateException unless the subtask failed */
        Throwable exception();
    }

    /** Thrown by {@link #join()} when a subtask fails; the cause is the first failure. */
    public static final class FailedException extends RuntimeException {
        public FailedException(Throwable cause) {
            super(cause);
        }
    }

    /** Thrown by {@link #join()} when the scope's deadline expires before all subtasks complete. */
    public static final class TimeoutException extends RuntimeException {
        public TimeoutException(String message) {
            super(message);
        }
    }
}
