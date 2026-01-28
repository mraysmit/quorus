package dev.mars.quorus.controller.observability;

import dev.mars.quorus.controller.config.AppConfig;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for OpenTelemetry integration.
 */
public class TelemetryConfig {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryConfig.class);
    private static int configuredPrometheusPort;
    private static String configuredOtlpEndpoint;

    public static VertxOptions configure(VertxOptions options) {
        AppConfig config = AppConfig.get();
        
        if (!config.isTelemetryEnabled()) {
            logger.info("Telemetry is disabled");
            return options;
        }

        configuredPrometheusPort = config.getPrometheusPort();
        configuredOtlpEndpoint = config.getOtlpEndpoint();
        String serviceName = config.getServiceName();

        // 1. Configure Resource
        Resource resource = Resource.getDefault().toBuilder()
                .put("service.name", serviceName)
                .build();

        // 2. Configure Tracing (OTLP Exporter)
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(configuredOtlpEndpoint)
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .setResource(resource)
                .build();

        // 3. Configure Metrics (Prometheus)
        PrometheusHttpServer prometheusReader = PrometheusHttpServer.builder()
                .setPort(configuredPrometheusPort)
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

        logger.info("OpenTelemetry configured: service={}, otlp={}, prometheus={}",
                serviceName, configuredOtlpEndpoint, configuredPrometheusPort);

        // 5. Configure Vert.x Options
        return options.setTracingOptions(new OpenTelemetryOptions(openTelemetry));
    }

    /**
     * Gets the configured Prometheus metrics port.
     */
    public static int getPrometheusPort() {
        return configuredPrometheusPort;
    }

    /**
     * Gets the configured OTLP endpoint.
     */
    public static String getOtlpEndpoint() {
        return configuredOtlpEndpoint;
    }
}
