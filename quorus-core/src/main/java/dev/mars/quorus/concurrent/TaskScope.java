/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.concurrent;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * <p>Context propagation (plan item RT-02b). A subtask runs on a fresh virtual thread, so anything
 * the owner holds per thread is propagated explicitly:
 * <ul>
 *   <li><b>Structured tracing.</b> Opening the scope starts a span {@code taskscope <name>} as a
 *       child of the owner's current OpenTelemetry context. Each subtask runs inside a child span
 *       {@code taskscope <name> subtask} that is current while the subtask runs, so spans the
 *       subtask starts nest under it. Every span records {@value #OUTCOME_ATTRIBUTE}:
 *       {@code success}, {@code failed} (with the exception and error status), {@code cancelled}
 *       or, for the scope span only, {@code timeout} (error status).</li>
 *   <li><b>MDC.</b> Each fork copies the owner's SLF4J MDC into the subtask, and adds the subtask
 *       span's {@value #MDC_TRACE_ID} and {@value #MDC_SPAN_ID}. The subtask's MDC is cleared when it
 *       ends; subtask changes never reach the owner or other subtasks.</li>
 *   <li><b>ScopedValue.</b> Only the keys declared with {@link Builder#inherit} are propagated,
 *       captured when the scope opens (as {@code StructuredTaskScope} captures bindings). Once the
 *       JDK scope is adopted it inherits all bindings, and the declarations can be removed.</li>
 * </ul>
 */
public final class TaskScope implements AutoCloseable {

    /** Span attribute recording how a scope or subtask ended. */
    public static final String OUTCOME_ATTRIBUTE = "quorus.taskscope.outcome";
    /** Span attribute carrying the scope name. */
    public static final String NAME_ATTRIBUTE = "quorus.taskscope.name";
    /** MDC key for the current trace ID; matches the controller's request correlation. */
    public static final String MDC_TRACE_ID = "traceId";
    /** MDC key for the current span ID; matches the controller's request correlation. */
    public static final String MDC_SPAN_ID = "spanId";

    private static final AttributeKey<String> OUTCOME = AttributeKey.stringKey(OUTCOME_ATTRIBUTE);
    private static final AttributeKey<String> NAME = AttributeKey.stringKey(NAME_ATTRIBUTE);

    private enum Phase { OPEN, JOINED, CLOSED }

    private final String name;
    private final Thread owner;
    private final long deadlineNanos;
    private final ThreadFactory threadFactory;
    private final Tracer tracer;
    private final Span scopeSpan;
    private final Context scopeContext;
    private final List<Binding<?>> bindings;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final List<Thread> threads = new ArrayList<>();   // guarded by lock
    private int running;                                      // guarded by lock
    private boolean cancelled;                                // guarded by lock
    private boolean timedOut;                                 // guarded by lock
    private Throwable firstFailure;                           // guarded by lock

    private Phase phase = Phase.OPEN;                         // owner thread only
    private boolean forked;                                   // owner thread only
    private boolean joinedSuccessfully;                       // owner thread only

    private TaskScope(String name, Duration timeout, Tracer tracer, List<ScopedValue<?>> inherited) {
        this.name = name;
        this.owner = Thread.currentThread();
        this.deadlineNanos = System.nanoTime() + timeout.toNanos();
        this.threadFactory = Thread.ofVirtual().name(name + "-", 0).factory();
        this.tracer = tracer;
        Context parent = Context.current();
        this.scopeSpan = tracer.spanBuilder("taskscope " + name)
                .setParent(parent)
                .setAttribute(NAME, name)
                .startSpan();
        this.scopeContext = parent.with(scopeSpan);
        this.bindings = captureBindings(inherited);
    }

    /**
     * Opens a scope owned by the calling thread, traced with the global OpenTelemetry tracer and
     * inheriting no scoped values.
     *
     * @param name    scope name, used for span and subtask thread names
     * @param timeout deadline for the whole scope, measured from now; must be positive
     */
    public static TaskScope open(String name, Duration timeout) {
        return builder(name, timeout).open();
    }

    /** Starts configuring a scope; {@link Builder#open()} opens it on the calling thread. */
    public static Builder builder(String name, Duration timeout) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive: " + timeout);
        }
        return new Builder(name, timeout);
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
        SubtaskImpl<T> subtask = new SubtaskImpl<>(task, MDC.getCopyOfContextMap());
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
                    timedOut = true;
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
            joinedSuccessfully = true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Cancels unfinished subtasks, waits for every subtask thread to end, and ends the scope span.
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
        endScopeSpan();
        if (forked && !joined) {
            throw new IllegalStateException("scope '" + name + "' was closed without join()");
        }
    }

    private void endScopeSpan() {
        Throwable failure;
        boolean timeout;
        lock.lock();
        try {
            failure = firstFailure;
            timeout = timedOut;
        } finally {
            lock.unlock();
        }
        if (failure != null) {
            scopeSpan.recordException(failure);
            scopeSpan.setStatus(StatusCode.ERROR, "subtask failed");
            scopeSpan.setAttribute(OUTCOME, "failed");
        } else if (timeout) {
            scopeSpan.setStatus(StatusCode.ERROR, "deadline exceeded");
            scopeSpan.setAttribute(OUTCOME, "timeout");
        } else {
            scopeSpan.setAttribute(OUTCOME, joinedSuccessfully ? "success" : "cancelled");
        }
        scopeSpan.end();
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

    private static List<Binding<?>> captureBindings(List<ScopedValue<?>> keys) {
        List<Binding<?>> captured = new ArrayList<>(keys.size());
        for (ScopedValue<?> key : keys) {
            if (key.isBound()) {
                captured.add(Binding.of(key));
            }
        }
        return List.copyOf(captured);
    }

    private <T> T callWithBindings(Callable<? extends T> task) throws Exception {
        if (bindings.isEmpty()) {
            return task.call();
        }
        ScopedValue.Carrier carrier = null;
        for (Binding<?> binding : bindings) {
            carrier = binding.bind(carrier);
        }
        return carrier.call(task::call);
    }

    /** A captured scoped-value binding, re-applied in each subtask. */
    private record Binding<V>(ScopedValue<V> key, V value) {

        static <V> Binding<V> of(ScopedValue<V> key) {
            return new Binding<>(key, key.get());
        }

        ScopedValue.Carrier bind(ScopedValue.Carrier carrier) {
            return carrier == null ? ScopedValue.where(key, value) : carrier.where(key, value);
        }
    }

    private final class SubtaskImpl<T> implements Subtask<T> {

        private final Callable<? extends T> task;
        private final Map<String, String> ownerMdc;
        private volatile State state = State.UNAVAILABLE;
        private volatile T result;
        private volatile Throwable exception;

        private SubtaskImpl(Callable<? extends T> task, Map<String, String> ownerMdc) {
            this.task = task;
            this.ownerMdc = ownerMdc;
        }

        private void run() {
            try {
                if (isCancelled()) {
                    return;
                }
                Span span = tracer.spanBuilder("taskscope " + name + " subtask")
                        .setParent(scopeContext)
                        .setAttribute(NAME, name)
                        .startSpan();
                String outcome = "cancelled";
                try (Scope ignored = scopeContext.with(span).makeCurrent()) {
                    installMdc(span);
                    T value = callWithBindings(task);
                    if (recordSuccess(value)) {
                        outcome = "success";
                    }
                } catch (Throwable failure) {
                    if (recordFailure(failure)) {
                        outcome = "failed";
                        span.recordException(failure);
                        span.setStatus(StatusCode.ERROR, String.valueOf(failure.getMessage()));
                    }
                } finally {
                    span.setAttribute(OUTCOME, outcome);
                    span.end();
                    MDC.clear();
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

        private void installMdc(Span span) {
            if (ownerMdc != null) {
                MDC.setContextMap(ownerMdc);
            } else {
                MDC.clear();
            }
            SpanContext context = span.getSpanContext();
            if (context.isValid()) {
                MDC.put(MDC_TRACE_ID, context.getTraceId());
                MDC.put(MDC_SPAN_ID, context.getSpanId());
            }
        }

        private boolean recordSuccess(T value) {
            lock.lock();
            try {
                if (cancelled) {
                    return false;
                }
                result = value;
                state = State.SUCCESS;
                return true;
            } finally {
                lock.unlock();
            }
        }

        private boolean recordFailure(Throwable failure) {
            lock.lock();
            try {
                if (cancelled) {
                    return false;
                }
                exception = failure;
                state = State.FAILED;
                firstFailure = failure;
                cancelLocked();
                return true;
            } finally {
                lock.unlock();
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

    /** Scope configuration. */
    public static final class Builder {
        private final String name;
        private final Duration timeout;
        private Tracer tracer = GlobalOpenTelemetry.getTracer("dev.mars.quorus.concurrent");
        private final List<ScopedValue<?>> inherited = new ArrayList<>();

        private Builder(String name, Duration timeout) {
            this.name = name;
            this.timeout = timeout;
        }

        /** Tracer for the scope and subtask spans; defaults to the global OpenTelemetry tracer. */
        public Builder tracer(Tracer tracer) {
            this.tracer = Objects.requireNonNull(tracer, "tracer");
            return this;
        }

        /** Scoped values whose bindings in the owner, captured at open, are re-bound in every subtask. */
        public Builder inherit(ScopedValue<?>... keys) {
            for (ScopedValue<?> key : keys) {
                inherited.add(Objects.requireNonNull(key, "key"));
            }
            return this;
        }

        /** Opens the scope, owned by the calling thread. */
        public TaskScope open() {
            return new TaskScope(name, timeout, tracer, List.copyOf(inherited));
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
