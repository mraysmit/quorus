/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.vertx.core.VertxOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies both disabled and enabled telemetry bootstrap behavior. */
class TelemetryConfigTest {

    private final Map<String, String> previousValues = new LinkedHashMap<>();

    @BeforeEach
    void resetGlobalTelemetry() {
        GlobalOpenTelemetry.resetForTest();
    }

    @AfterEach
    void restoreConfiguration() {
        previousValues.forEach((key, value) -> {
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        });
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void disabledTelemetryLeavesVertxOptionsUntouched() {
        setProperty("quorus.telemetry.enabled", "false");
        VertxOptions options = new VertxOptions();

        assertSame(options, TelemetryConfig.configure(options));
        assertNull(options.getTracingOptions());
    }

    @Test
    void enabledTelemetryConfiguresTracingAndExportEndpoints() throws IOException {
        int prometheusPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            prometheusPort = socket.getLocalPort();
        }
        setProperty("quorus.telemetry.enabled", "true");
        setProperty("quorus.telemetry.prometheus.port", Integer.toString(prometheusPort));
        setProperty("quorus.telemetry.otlp.endpoint", "http://127.0.0.1:4317");
        setProperty("quorus.telemetry.service.name", "quorus-controller-test");

        VertxOptions configured = TelemetryConfig.configure(new VertxOptions());

        assertNotNull(configured.getTracingOptions());
        assertEquals(prometheusPort, TelemetryConfig.getPrometheusPort());
        assertEquals("http://127.0.0.1:4317", TelemetryConfig.getOtlpEndpoint());
    }

    private void setProperty(String key, String value) {
        previousValues.putIfAbsent(key, System.getProperty(key));
        System.setProperty(key, value);
    }
}
