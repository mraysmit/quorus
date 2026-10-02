/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.concurrent;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import dev.mars.quorus.concurrent.TaskScope.Subtask;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Context propagation contract of the Quorus task scope (plan item RT-02b; ADR-0012).
 *
 * <p>Subtasks run on fresh virtual threads, so everything the owner holds per thread would be
 * lost without explicit propagation: the OpenTelemetry context, the SLF4J MDC, and
 * {@code ScopedValue} bindings. Spans are checked with the real OpenTelemetry SDK and an in-memory
 * exporter, and MDC with real logback events. There are no mocks, sleeps or polling.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class TaskScopeContextPropagationTest {

    private static final Duration GENEROUS = Duration.ofSeconds(5);
    private static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("quorus.taskscope.outcome");
    private static final AttributeKey<String> SCOPE_NAME = AttributeKey.stringKey("quorus.taskscope.name");

    private InMemorySpanExporter exporter;
    private SdkTracerProvider tracerProvider;
    private Tracer tracer;

    @BeforeEach
    void installTracing() {
        exporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        tracer = tracerProvider.get("task-scope-test");
    }

    @AfterEach
    void removeTracingAndMdc() {
        tracerProvider.close();
        MDC.clear();
    }

    @Nested
    class StructuredTracing {

        @Test
        void scopeSpanIsAChildOfTheOwnerSpanAndSubtaskSpansAreChildrenOfTheScopeSpan() throws Exception {
            Span owner = tracer.spanBuilder("owner").startSpan();
            try (Scope ignored = owner.makeCurrent();
                 TaskScope scope = TaskScope.builder("fetch", GENEROUS).tracer(tracer).open()) {
                scope.fork(() -> "first");
                scope.fork(() -> "second");
                scope.join();
            } finally {
                owner.end();
            }

            SpanData scopeSpan = single("taskscope fetch");
            List<SpanData> subtasks = named("taskscope fetch subtask");
            assertEquals(owner.getSpanContext().getSpanId(), scopeSpan.getParentSpanId());
            assertEquals("fetch", scopeSpan.getAttributes().get(SCOPE_NAME));
            assertEquals(2, subtasks.size());
            for (SpanData subtask : subtasks) {
                assertEquals(scopeSpan.getSpanId(), subtask.getParentSpanId());
                assertEquals(owner.getSpanContext().getTraceId(), subtask.getTraceId());
            }
        }

        @Test
        void spansStartedInsideASubtaskAreChildrenOfTheSubtaskSpan() throws Exception {
            try (TaskScope scope = TaskScope.builder("nested", GENEROUS).tracer(tracer).open()) {
                scope.fork(() -> {
                    tracer.spanBuilder("protocol call").startSpan().end();
                    return null;
                });
                scope.join();
            }

            assertEquals(single("taskscope nested subtask").getSpanId(), single("protocol call").getParentSpanId());
        }

        @Test
        void successfulScopeAndSubtasksRecordASuccessOutcome() throws Exception {
            try (TaskScope scope = TaskScope.builder("ok", GENEROUS).tracer(tracer).open()) {
                scope.fork(() -> "done");
                scope.join();
            }

            assertEquals("success", single("taskscope ok").getAttributes().get(OUTCOME));
            assertNotEquals(StatusCode.ERROR, single("taskscope ok").getStatus().getStatusCode());
            assertEquals("success", single("taskscope ok subtask").getAttributes().get(OUTCOME));
        }

        @Test
        void failureIsRecordedOnTheFailingSubtaskAndScopeAndSiblingsAreMarkedCancelled() throws Exception {
            CompletableFuture<Void> siblingStarted = new CompletableFuture<>();
            IllegalStateException failure = new IllegalStateException("step failed");
            try (TaskScope scope = TaskScope.builder("fail", GENEROUS).tracer(tracer).open()) {
                scope.fork(() -> {
                    siblingStarted.complete(null);
                    return blockUntilInterrupted();
                });
                scope.fork(() -> {
                    siblingStarted.join();
                    throw failure;
                });
                assertThrows(TaskScope.FailedException.class, scope::join);
            }

            List<SpanData> subtasks = named("taskscope fail subtask");
            SpanData failed = subtasks.stream().filter(s -> "failed".equals(s.getAttributes().get(OUTCOME))).findFirst().orElseThrow();
            SpanData cancelled = subtasks.stream().filter(s -> "cancelled".equals(s.getAttributes().get(OUTCOME))).findFirst().orElseThrow();
            assertEquals(StatusCode.ERROR, failed.getStatus().getStatusCode());
            assertTrue(failed.getEvents().stream().anyMatch(e -> "exception".equals(e.getName())),
                    "the failing subtask span must record the exception");
            assertNotEquals(StatusCode.ERROR, cancelled.getStatus().getStatusCode());
            SpanData scopeSpan = single("taskscope fail");
            assertEquals("failed", scopeSpan.getAttributes().get(OUTCOME));
            assertEquals(StatusCode.ERROR, scopeSpan.getStatus().getStatusCode());
        }

        @Test
        void deadlineExpiryMarksTheScopeSpanAsTimedOutAndUnfinishedSubtasksAsCancelled() throws Exception {
            try (TaskScope scope = TaskScope.builder("slow", Duration.ofMillis(200)).tracer(tracer).open()) {
                scope.fork(TaskScopeContextPropagationTest::blockUntilInterrupted);
                assertThrows(TaskScope.TimeoutException.class, scope::join);
            }

            SpanData scopeSpan = single("taskscope slow");
            assertEquals("timeout", scopeSpan.getAttributes().get(OUTCOME));
            assertEquals(StatusCode.ERROR, scopeSpan.getStatus().getStatusCode());
            assertEquals("cancelled", single("taskscope slow subtask").getAttributes().get(OUTCOME));
        }
    }

    @Nested
    class MappedDiagnosticContext {

        private Logger logger;
        private CapturingAppender appender;

        @BeforeEach
        void captureLogs() {
            logger = (Logger) LoggerFactory.getLogger("task-scope-mdc-" + UUID.randomUUID());
            appender = new CapturingAppender();
            appender.start();
            logger.addAppender(appender);
            logger.setLevel(Level.INFO);
            logger.setAdditive(false);
        }

        @Test
        void subtaskLogEventsCarryTheOwnersMdc() throws Exception {
            MDC.put("requestId", "req-42");
            MDC.put("nodeId", "controller1");
            try (TaskScope scope = TaskScope.builder("mdc", GENEROUS).tracer(tracer).open()) {
                scope.fork(() -> {
                    logger.info("inside subtask");
                    return null;
                });
                scope.join();
            }

            Map<String, String> mdc = onlyEvent().getMDCPropertyMap();
            assertEquals("req-42", mdc.get("requestId"));
            assertEquals("controller1", mdc.get("nodeId"));
        }

        @Test
        void subtaskMdcTraceIdentifiersMatchTheSubtaskSpan() throws Exception {
            try (TaskScope scope = TaskScope.builder("mdc-trace", GENEROUS).tracer(tracer).open()) {
                scope.fork(() -> {
                    logger.info("inside subtask");
                    return null;
                });
                scope.join();
            }

            SpanData subtask = single("taskscope mdc-trace subtask");
            Map<String, String> mdc = onlyEvent().getMDCPropertyMap();
            assertEquals(subtask.getTraceId(), mdc.get("traceId"));
            assertEquals(subtask.getSpanId(), mdc.get("spanId"));
        }

        @Test
        void subtaskMdcChangesDoNotLeakIntoTheOwnerOrLaterSubtasks() throws Exception {
            MDC.put("requestId", "req-7");
            CompletableFuture<Void> firstDone = new CompletableFuture<>();
            try (TaskScope scope = TaskScope.builder("mdc-isolation", GENEROUS).tracer(tracer).open()) {
                scope.fork(() -> {
                    MDC.put("rpcType", "changed-in-subtask");
                    firstDone.complete(null);
                    return null;
                });
                firstDone.join();
                scope.fork(() -> {
                    logger.info("second subtask");
                    return null;
                });
                scope.join();
            }

            assertNull(onlyEvent().getMDCPropertyMap().get("rpcType"));
            assertNull(MDC.get("rpcType"), "the owner's MDC must be unchanged");
            assertEquals("req-7", MDC.get("requestId"));
        }

        private ILoggingEvent onlyEvent() {
            assertEquals(1, appender.events.size(), "expected exactly one log event");
            return appender.events.getFirst();
        }
    }

    /**
     * Real logback appender that freezes each event on the logging thread. Logback evaluates an
     * event's MDC lazily, so without {@code prepareForDeferredProcessing()} an assertion would read
     * the MDC of whichever thread inspects the event later, not the thread that logged it.
     */
    private static final class CapturingAppender extends AppenderBase<ILoggingEvent> {
        private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            events.add(event);
        }
    }

    @Nested
    class ScopedValues {

        private static final ScopedValue<String> TENANT = ScopedValue.newInstance();
        private static final ScopedValue<String> UNDECLARED = ScopedValue.newInstance();

        @Test
        void declaredScopedValuesAreReboundInSubtasks() throws Exception {
            String seen = ScopedValue.where(TENANT, "tenant-a").call(() -> {
                try (TaskScope scope = TaskScope.builder("sv", GENEROUS).tracer(tracer).inherit(TENANT).open()) {
                    Subtask<String> subtask = scope.fork(() -> TENANT.isBound() ? TENANT.get() : "unbound");
                    scope.join();
                    return subtask.get();
                }
            });

            assertEquals("tenant-a", seen);
        }

        @Test
        void undeclaredScopedValuesAreNotInherited() throws Exception {
            boolean bound = ScopedValue.where(UNDECLARED, "secret").call(() -> {
                try (TaskScope scope = TaskScope.builder("sv-undeclared", GENEROUS).tracer(tracer).inherit(TENANT).open()) {
                    Subtask<Boolean> subtask = scope.fork(UNDECLARED::isBound);
                    scope.join();
                    return subtask.get();
                }
            });

            assertFalse(bound, "only declared scoped values are propagated");
        }

        @Test
        void aDeclaredValueThatTheOwnerHasNotBoundStaysUnbound() throws Exception {
            try (TaskScope scope = TaskScope.builder("sv-unbound", GENEROUS).tracer(tracer).inherit(TENANT).open()) {
                Subtask<Boolean> subtask = scope.fork(TENANT::isBound);
                scope.join();
                assertFalse(subtask.get());
            }
        }
    }

    /**
     * Retrospective characterization (plan §6.1): behaviour implemented during the RT-02b green
     * stage without a preceding failing test. Recorded as characterization, not TDD evidence.
     */
    @Nested
    class Characterization {

        private static final ScopedValue<String> TENANT = ScopedValue.newInstance();
        private static final ScopedValue<String> REQUEST = ScopedValue.newInstance();

        @Test
        void scopeClosedWithoutASuccessfulJoinRecordsACancelledOutcome() {
            TaskScope scope = TaskScope.builder("abandoned", GENEROUS).tracer(tracer).open();
            scope.fork(() -> "never joined");

            assertThrows(IllegalStateException.class, scope::close);

            assertEquals("cancelled", single("taskscope abandoned").getAttributes().get(OUTCOME));
        }

        @Test
        void everyDeclaredScopedValueIsRebound() throws Exception {
            String seen = ScopedValue.where(TENANT, "tenant-b").where(REQUEST, "req-9").call(() -> {
                try (TaskScope scope = TaskScope.builder("sv-many", GENEROUS).tracer(tracer).inherit(TENANT, REQUEST).open()) {
                    Subtask<String> subtask = scope.fork(() -> TENANT.get() + "/" + REQUEST.get());
                    scope.join();
                    return subtask.get();
                }
            });

            assertEquals("tenant-b/req-9", seen);
        }

        @Test
        void withTracingDisabledTheSubtaskKeepsTheOwnersTraceIdentifiersInMdc() throws Exception {
            MDC.put(TaskScope.MDC_TRACE_ID, "owner-trace");
            MDC.put(TaskScope.MDC_SPAN_ID, "owner-span");
            Subtask<String> subtask;
            try (TaskScope scope = TaskScope.builder("no-tracing", GENEROUS)
                    .tracer(io.opentelemetry.api.OpenTelemetry.noop().getTracer("noop")).open()) {
                subtask = scope.fork(() -> MDC.get(TaskScope.MDC_TRACE_ID) + "/" + MDC.get(TaskScope.MDC_SPAN_ID));
                scope.join();
            }

            assertEquals("owner-trace/owner-span", subtask.get());
        }

        @Test
        void aSubtaskThatSucceedsAfterCancellationRecordsACancelledSpan() throws Exception {
            CompletableFuture<Void> slowStarted = new CompletableFuture<>();
            CompletableFuture<Void> release = new CompletableFuture<>();
            try (TaskScope scope = TaskScope.builder("late-success", GENEROUS).tracer(tracer).open()) {
                scope.fork(() -> {
                    slowStarted.complete(null);
                    release.join();                  // ignores the cancellation interrupt
                    return "too late";
                });
                scope.fork(() -> {
                    slowStarted.join();
                    throw new IllegalStateException("cancel the scope");
                });
                assertThrows(TaskScope.FailedException.class, scope::join);
                release.complete(null);
            }

            List<SpanData> subtasks = named("taskscope late-success subtask");
            assertTrue(subtasks.stream().anyMatch(s -> "cancelled".equals(s.getAttributes().get(OUTCOME))),
                    "a result produced after cancellation must be recorded as cancelled");
        }

        @Test
        void aSubtaskForkedAfterCancellationProducesNoSpan() throws Exception {
            CompletableFuture<Void> siblingStarted = new CompletableFuture<>();
            CompletableFuture<Void> scopeCancelled = new CompletableFuture<>();
            try (TaskScope scope = TaskScope.builder("late", GENEROUS).tracer(tracer).open()) {
                scope.fork(() -> {
                    siblingStarted.complete(null);
                    try {
                        return blockUntilInterrupted();
                    } finally {
                        scopeCancelled.complete(null);
                    }
                });
                scope.fork(() -> {
                    siblingStarted.join();
                    throw new IllegalStateException("cancel");
                });
                scopeCancelled.join();
                scope.fork(() -> "late");
                assertThrows(TaskScope.FailedException.class, scope::join);
            }

            assertEquals(2, named("taskscope late subtask").size(), "only the two started subtasks have spans");
        }
    }

    private SpanData single(String name) {
        List<SpanData> spans = named(name);
        assertEquals(1, spans.size(), "expected one span named '" + name + "' but found "
                + exporter.getFinishedSpanItems().stream().map(SpanData::getName).toList());
        return spans.getFirst();
    }

    private List<SpanData> named(String name) {
        return exporter.getFinishedSpanItems().stream().filter(s -> s.getName().equals(name)).toList();
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
