/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent.service;

import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.security.PemTls;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;

/**
 * The agent's one approved client for its controller (RT-05a), on the JDK HTTP client. It replaces the
 * Vert.x WebClient factory with the same posture:
 * <ul>
 *   <li>With controller TLS enabled: TLS 1.3 only, the agent's certificate presented for mutual TLS,
 *       only the configured trust bundle trusted, and the controller's hostname verified.</li>
 *   <li>Without it (development only): plaintext, logged as insecure.</li>
 * </ul>
 * HTTP/1.1 is used so that a plaintext request is never an HTTP/2 upgrade attempt. Every request has
 * the agent's HTTP idle timeout as its response deadline, as documented in the Security Deployment
 * Guide. Calls block the calling thread; the agent calls them from virtual threads.
 *
 * <p>Each request is an OpenTelemetry client span, and the current trace context is sent in the
 * request headers using the globally registered propagators. This replaces the Vert.x tracing
 * integration the agent had before RT-05b.
 */
public final class ControllerClient implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ControllerClient.class);
    private static final String USER_AGENT = "Quorus-Agent/1.0";
    private static final String INSTRUMENTATION = "dev.mars.quorus.agent.controller-client";

    private final HttpClient client;
    private final Duration responseDeadline;

    private ControllerClient(HttpClient client, Duration responseDeadline) {
        this.client = client;
        this.responseDeadline = responseDeadline;
    }

    /**
     * Creates the client for this agent's configuration.
     *
     * @throws IllegalStateException if the configured TLS material cannot be loaded
     */
    public static ControllerClient create(AgentConfiguration config) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(config.getHttpConnectionTimeout()))
                .followRedirects(HttpClient.Redirect.NEVER);
        if (config.isControllerTlsEnabled()) {
            try {
                builder.sslContext(PemTls.sslContext(Path.of(config.getTlsCertificatePath()),
                        Path.of(config.getTlsPrivateKeyPath()), Path.of(config.getTlsTrustBundlePath())));
            } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
                throw new IllegalStateException("Cannot load the agent's controller TLS material: " + e.getMessage(), e);
            }
            SSLParameters parameters = new SSLParameters();
            parameters.setProtocols(new String[]{"TLSv1.3"});
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            builder.sslParameters(parameters);
        } else {
            logger.warn("INSECURE DEVELOPMENT MODE: agent-to-controller traffic is plaintext");
        }
        return new ControllerClient(builder.build(), Duration.ofMillis(config.getHttpIdleTimeout()));
    }

    public Response get(String url) throws IOException, InterruptedException {
        return send("GET", url, request(url).header("Accept", "application/json").GET());
    }

    public Response postJson(String url, String json) throws IOException, InterruptedException {
        return send("POST", url, request(url).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)));
    }

    public Response delete(String url) throws IOException, InterruptedException {
        return send("DELETE", url, request(url).DELETE());
    }

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(responseDeadline).header("User-Agent", USER_AGENT);
    }

    private Response send(String method, String url, HttpRequest.Builder request)
            throws IOException, InterruptedException {
        URI uri = URI.create(url);
        Span span = GlobalOpenTelemetry.getTracer(INSTRUMENTATION).spanBuilder(method)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("http.request.method", method)
                .setAttribute("url.full", url)
                .setAttribute("server.address", uri.getHost())
                .setAttribute("server.port", (long) uri.getPort())
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                    .inject(Context.current(), request, (builder, name, value) -> builder.setHeader(name, value));
            HttpResponse<String> response = client.send(request.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            span.setAttribute("http.response.status_code", (long) response.statusCode());
            if (response.statusCode() >= 500) {
                span.setStatus(StatusCode.ERROR);
            }
            return new Response(response.statusCode(), response.body());
        } catch (IOException | RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }

    @Override
    public void close() {
        client.close();
    }

    /** A controller response: its status and body. */
    public record Response(int status, String body) {
        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }
    }
}
