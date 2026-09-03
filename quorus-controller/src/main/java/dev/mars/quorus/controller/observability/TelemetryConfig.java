package dev.mars.quorus.controller.observability;

import dev.mars.quorus.controller.config.AppConfig;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.prometheus.PrometheusHttpServer;
import io.vertx.core.VertxOptions;
import io.vertx.tracing.opentelemetry.OpenTelemetryOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for OpenTelemetry integration.
 */
public class TelemetryConfig {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryConfig.class);
    public static VertxOptions configure(VertxOptions options, AppConfig config) {
        if (!config.isTelemetryEnabled()) {
            logger.info("Telemetry is disabled");
            return options;
        }

        String serviceName = config.getServiceName();

        // 1. Configure Resource
        Resource resource = Resource.getDefault().toBuilder()
                .put("service.name", serviceName)
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

        // 4. Initialize OpenTelemetry SDK (registered globally)
        OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .buildAndRegisterGlobal();

        logger.info("OpenTelemetry configured: service={}, otlp={}, prometheus={}",
                serviceName, config.getOtlpEndpoint(), config.getPrometheusPort());

        // 5. Configure Vert.x Options (picks up globally registered SDK)
        return options.setTracingOptions(new OpenTelemetryOptions());
    }

}
