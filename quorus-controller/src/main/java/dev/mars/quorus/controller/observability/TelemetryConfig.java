package dev.mars.quorus.controller.observability;

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
 * Configuration for OpenTelemetry integration.
 */
public class TelemetryConfig {

    public static VertxOptions configure(VertxOptions options) {
        // 1. Configure Resource
        Resource resource = Resource.getDefault().toBuilder()
                .put("service.name", "quorus-controller")
                .build();

        // 2. Configure Tracing (OTLP Exporter)
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .setResource(resource)
                .build();

        // 3. Configure Metrics (Prometheus)
        // Note: PrometheusHttpServer starts a separate HTTP server on port 9464 by
        // default
        PrometheusHttpServer prometheusReader = PrometheusHttpServer.builder()
                .setPort(9464) // Default Prometheus port
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
}
