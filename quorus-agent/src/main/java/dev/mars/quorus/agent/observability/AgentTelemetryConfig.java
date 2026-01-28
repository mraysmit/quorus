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

package dev.mars.quorus.agent.observability;

import dev.mars.quorus.agent.config.AgentConfig;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.prometheus.PrometheusHttpServer;
import io.vertx.core.VertxOptions;
import io.vertx.tracing.opentelemetry.OpenTelemetryOptions;

/**
 * OpenTelemetry configuration for Quorus Agent.
 * Phase 6 of the OpenTelemetry migration.
 * 
 * Provides:
 * - OTLP trace export for distributed tracing
 * - Prometheus metrics export (configurable port, default 9465)
 * - Vert.x tracing integration
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-01-27
 * @version 1.0 (OpenTelemetry)
 */
public class AgentTelemetryConfig {

    private static final AgentConfig config = AgentConfig.get();

    /**
     * Configure Vert.x options with OpenTelemetry tracing.
     *
     * @param options the VertxOptions to configure
     * @param agentId the agent ID for service identification
     * @return configured VertxOptions
     */
    public static VertxOptions configure(VertxOptions options, String agentId) {
        // 1. Configure Resource with agent-specific attributes
        Resource resource = Resource.getDefault().toBuilder()
                .put("service.name", "quorus-agent")
                .put("service.instance.id", agentId)
                .build();

        // 2. Configure Tracing (OTLP Exporter)
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(config.getOtlpEndpoint())
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .setResource(resource)
                .build();

        // 3. Configure Metrics (Prometheus)
        PrometheusHttpServer prometheusReader = PrometheusHttpServer.builder()
                .setPort(config.getPrometheusPort())
                .build();

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(prometheusReader)
                .build();

        // 4. Initialize OpenTelemetry SDK
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .buildAndRegisterGlobal();

        // 5. Configure Vert.x Options
        return options.setTracingOptions(new OpenTelemetryOptions(openTelemetry));
    }

    /**
     * Get the Prometheus metrics port.
     */
    public static int getPrometheusPort() {
        return config.getPrometheusPort();
    }
}
