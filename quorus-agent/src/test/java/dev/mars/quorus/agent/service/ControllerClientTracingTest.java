/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent.service;

import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.agent.testing.FakeController;
import dev.mars.quorus.agent.testing.FakeController.Reply;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RT-05b: without the Vert.x tracing integration, the controller client makes each request an
 * OpenTelemetry client span and sends the trace context to the controller.
 */
@Timeout(value = 30, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ControllerClientTracingTest {

    private final InMemorySpanExporter spans = InMemorySpanExporter.create();
    private OpenTelemetrySdk sdk;

    @BeforeEach
    void registerTracing() {
        GlobalOpenTelemetry.resetForTest();
        sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spans)).build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();
    }

    @AfterEach
    void resetTracing() {
        sdk.close();
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void eachRequestIsAClientSpanWhoseContextReachesTheController() throws Exception {
        try (FakeController controller = FakeController.start()
                .on("POST", "/api/v1/agents/heartbeat", Reply.json(200, "{}").always());
             ControllerClient client = ControllerClient.create(config(controller.url()))) {

            client.postJson(controller.url() + "/api/v1/agents/heartbeat", "{}");

            SpanData span = spans.getFinishedSpanItems().getFirst();
            assertEquals("POST", span.getName());
            assertEquals(SpanKind.CLIENT, span.getKind());
            assertEquals("POST", span.getAttributes().get(AttributeKey.stringKey("http.request.method")));
            assertEquals(controller.url() + "/api/v1/agents/heartbeat",
                    span.getAttributes().get(AttributeKey.stringKey("url.full")));
            assertEquals(200L, span.getAttributes().get(AttributeKey.longKey("http.response.status_code")));
            String traceparent = controller.requests().getFirst().headers().get("traceparent");
            assertNotNull(traceparent, "the controller receives the W3C trace context");
            assertTrue(traceparent.contains(span.getTraceId()) && traceparent.contains(span.getSpanId()),
                    () -> traceparent + " should carry " + span.getTraceId() + "/" + span.getSpanId());
        }
    }

    @Test
    void aFailedRequestIsAnErrorSpan() {
        try (ControllerClient client = ControllerClient.create(config("http://localhost:59999"))) {
            assertThrows(IOException.class, () -> client.get("http://localhost:59999/api/v1/agents/a/jobs"));

            SpanData span = spans.getFinishedSpanItems().getFirst();
            assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
            assertFalse(span.getEvents().isEmpty(), "the exception is recorded on the span");
        }
    }

    private static AgentConfiguration config(String controllerUrl) {
        return new AgentConfiguration.Builder()
                .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                .agentId("traced-agent").tenantId("tenant").controllerUrl(controllerUrl + "/api/v1")
                .httpConnectionTimeout(1000).build();
    }
}
