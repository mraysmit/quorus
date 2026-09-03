/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.observability;

import dev.mars.quorus.controller.config.AppConfig;
import dev.mars.quorus.controller.config.ControllerTestConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.vertx.core.VertxOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies both disabled and enabled telemetry bootstrap behavior. */
class TelemetryConfigTest {

    @BeforeEach
    void resetGlobalTelemetry() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void disabledTelemetryLeavesVertxOptionsUntouched() {
        Properties overrides = new Properties();
        overrides.setProperty("quorus.telemetry.enabled", "false");
        AppConfig config = ControllerTestConfig.create(overrides);
        VertxOptions options = new VertxOptions();

        assertSame(options, TelemetryConfig.configure(options, config));
        assertNull(options.getTracingOptions());
    }

    @Test
    void enabledTelemetryConfiguresTracingAndExportEndpoints() throws IOException {
        int prometheusPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            prometheusPort = socket.getLocalPort();
        }
        Properties overrides = new Properties();
        overrides.setProperty("quorus.telemetry.enabled", "true");
        overrides.setProperty("quorus.telemetry.prometheus.port", Integer.toString(prometheusPort));
        overrides.setProperty("quorus.telemetry.otlp.endpoint", "http://127.0.0.1:4317");
        overrides.setProperty("quorus.telemetry.service.name", "quorus-controller-test");
        AppConfig config = ControllerTestConfig.create(overrides);

        VertxOptions configured = TelemetryConfig.configure(new VertxOptions(), config);

        assertNotNull(configured.getTracingOptions());
        assertEquals(prometheusPort, config.getPrometheusPort());
        assertEquals("http://127.0.0.1:4317", config.getOtlpEndpoint());
    }
}
