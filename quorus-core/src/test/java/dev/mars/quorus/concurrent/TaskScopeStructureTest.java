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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structure rules of the Quorus task scope (plan item RT-02d).
 *
 * <p>{@code StructuredTaskScope}, which {@link TaskScope} will delegate to once it is final, rejects
 * two kinds of misuse with {@code StructureViolationException}: forking under different
 * {@code ScopedValue} bindings from those in force when the scope was opened, and closing a scope
 * while scopes opened inside it on the same thread are still open. {@link TaskScope} enforces the
 * same rules now, so code that runs today keeps running after the switch.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class TaskScopeStructureTest {

    private static final Duration GENEROUS = Duration.ofSeconds(5);
    private static final ScopedValue<String> TENANT = ScopedValue.newInstance();

    @Test
    void forkingUnderADifferentBindingOfADeclaredKeyIsAStructureViolation() throws Exception {
        ScopedValue.where(TENANT, "tenant-a").call(() -> {
            try (TaskScope scope = TaskScope.builder("rebound", GENEROUS).inherit(TENANT).open()) {
                ScopedValue.where(TENANT, "tenant-b").run(() ->
                        assertThrows(TaskScope.StructureViolationException.class, () -> scope.fork(() -> "x")));
                scope.join();
            }
            return null;
        });
    }

    @Test
    void forkingAfterADeclaredKeyBecameBoundIsAStructureViolation() throws Exception {
        try (TaskScope scope = TaskScope.builder("newly-bound", GENEROUS).inherit(TENANT).open()) {
            ScopedValue.where(TENANT, "tenant-a").run(() ->
                    assertThrows(TaskScope.StructureViolationException.class, () -> scope.fork(() -> "x")));
            scope.join();
        }
    }

    @Test
    void forkingUnderTheBindingsInForceAtOpenIsAllowed() throws Exception {
        String seen = ScopedValue.where(TENANT, "tenant-a").call(() -> {
            try (TaskScope scope = TaskScope.builder("same-bindings", GENEROUS).inherit(TENANT).open()) {
                Subtask<String> subtask = scope.fork(TENANT::get);
                scope.join();
                return subtask.get();
            }
        });

        assertEquals("tenant-a", seen);
    }

    @Test
    void closingAnOuterScopeWhileAnInnerScopeIsOpenClosesTheInnerFirstAndReportsAViolation() throws Exception {
        CompletableFuture<Void> innerStarted = new CompletableFuture<>();
        AtomicBoolean innerSubtaskEnded = new AtomicBoolean();
        TaskScope outer = TaskScope.open("outer", GENEROUS);
        TaskScope inner = TaskScope.open("inner", GENEROUS);
        inner.fork(() -> {
            innerStarted.complete(null);
            try {
                return blockUntilInterrupted();
            } finally {
                innerSubtaskEnded.set(true);
            }
        });
        innerStarted.join();

        assertThrows(TaskScope.StructureViolationException.class, outer::close);

        assertTrue(innerSubtaskEnded.get(), "closing the outer scope must first close the inner scope and await its subtasks");
        inner.close();                                  // already closed: a no-op
    }

    @Test
    void properlyNestedScopesCloseNormally() throws Exception {
        try (TaskScope outer = TaskScope.open("outer-ok", GENEROUS)) {
            try (TaskScope inner = TaskScope.open("inner-ok", GENEROUS)) {
                inner.fork(() -> "inner");
                inner.join();
            }
            outer.fork(() -> "outer");
            outer.join();
        }
    }

    // ---- Retrospective characterization (plan §6.1): behaviour implemented during the RT-02d green
    // ---- stage without a preceding failing test. Recorded as characterization, not TDD evidence.

    @Test
    void forkingAfterLeavingTheBindingInForceAtOpenIsAStructureViolation() throws Exception {
        TaskScope escaped = ScopedValue.where(TENANT, "tenant-a").call(
                () -> TaskScope.builder("escaped", GENEROUS).inherit(TENANT).open());

        assertThrows(TaskScope.StructureViolationException.class, () -> escaped.fork(() -> "x"));
        escaped.close();
    }

    @Test
    void outOfOrderCloseOfACleanInnerScopeReportsOnlyTheViolation() throws Exception {
        TaskScope outer = TaskScope.open("outer-clean", GENEROUS);
        TaskScope inner = TaskScope.open("inner-clean", GENEROUS);
        inner.fork(() -> "done");
        inner.join();

        TaskScope.StructureViolationException violation =
                assertThrows(TaskScope.StructureViolationException.class, outer::close);

        assertEquals(0, violation.getSuppressed().length, "a cleanly closed inner scope adds no suppressed exception");
        inner.close();
    }

    @Test
    void outOfOrderCloseAttachesTheOuterScopesMissingJoinAsSuppressed() throws Exception {
        TaskScope outer = TaskScope.open("outer-unjoined", GENEROUS);
        outer.fork(() -> "never joined");
        TaskScope inner = TaskScope.open("inner-after-fork", GENEROUS);

        TaskScope.StructureViolationException violation =
                assertThrows(TaskScope.StructureViolationException.class, outer::close);

        assertEquals(1, violation.getSuppressed().length);
        assertTrue(violation.getSuppressed()[0] instanceof IllegalStateException,
                "the outer scope's missing join() is reported as suppressed");
        inner.close();
    }

    private static Void blockUntilInterrupted() throws InterruptedException {
        try {
            new CompletableFuture<Void>().get();
            throw new AssertionError("an incomplete future cannot complete");
        } catch (ExecutionException e) {
            throw new AssertionError(e);
        }
    }
}
