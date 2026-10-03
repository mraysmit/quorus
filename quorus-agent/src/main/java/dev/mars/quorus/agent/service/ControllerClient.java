/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.function.UnaryOperator;

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
 * <p>Request paths are relative to the controller API base URL. The agent is configured with every
 * controller of its cluster (ENG-27). A write a follower refuses with {@code 503 NOT_LEADER} is sent
 * again to the leader it names in {@code X-Quorus-Leader}, if that is a configured controller, or
 * else to the next configured one; each controller is asked at most once per request, and later
 * requests go straight to the controller that answered. A transport failure is not resent, because
 * the request may have been applied; the next request goes to the next configured controller.
 *
 * <p>Each attempt is an OpenTelemetry client span, and the current trace context is sent in the
 * request headers using the globally registered propagators. This replaces the Vert.x tracing
 * integration the agent had before RT-05b.
 */
public final class ControllerClient implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ControllerClient.class);
    private static final String USER_AGENT = "Quorus-Agent/1.0";
    private static final String INSTRUMENTATION = "dev.mars.quorus.agent.controller-client";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient client;
    private final Duration responseDeadline;
    private static final String LEADER_HEADER = "X-Quorus-Leader";

    private final ControllerEndpoints endpoints;

    private ControllerClient(HttpClient client, Duration responseDeadline, List<String> controllerUrls) {
        this.client = client;
        this.responseDeadline = responseDeadline;
        this.endpoints = new ControllerEndpoints(controllerUrls);
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
        return new ControllerClient(builder.build(), Duration.ofMillis(config.getHttpIdleTimeout()),
                config.getControllerUrls());
    }

    /** Sends a GET to {@code path}, which is relative to the controller API base URL, e.g. {@code /agents}. */
    public Response get(String path) throws IOException, InterruptedException {
        return send("GET", path, request -> request.header("Accept", "application/json").GET());
    }

    /** Sends a JSON POST to {@code path}, which is relative to the controller API base URL. */
    public Response postJson(String path, String json) throws IOException, InterruptedException {
        return send("POST", path, request -> request.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)));
    }

    /** Sends a DELETE to {@code path}, which is relative to the controller API base URL. */
    public Response delete(String path) throws IOException, InterruptedException {
        return send("DELETE", path, HttpRequest.Builder::DELETE);
    }

    private Response send(String method, String path, UnaryOperator<HttpRequest.Builder> shape)
            throws IOException, InterruptedException {
        // A refused write was not applied, so it is safe to send again. Each controller is tried at most once.
        for (int attempt = 1; ; attempt++) {
            String base = endpoints.current();
            String url = base + path;
            Exchange exchange;
            try {
                exchange = sendTo(method, url, shape.apply(HttpRequest.newBuilder(URI.create(url))
                        .timeout(responseDeadline).header("User-Agent", USER_AGENT)));
            } catch (IOException e) {
                endpoints.leaveUnreachable(base);
                throw e;
            }
            if (!refusedByFollower(exchange.response()) || attempt >= endpoints.size()
                    || !endpoints.leaveFollower(base, exchange.leaderHint())) {
                return exchange.response();
            }
            logger.info("Controller {} is not the leader; retrying on {}", base, endpoints.current());
        }
    }

    /** Whether the controller refused the request because it is a follower: 503 with code NOT_LEADER. */
    private static boolean refusedByFollower(Response response) {
        if (response.status() != 503) {
            return false;
        }
        try {
            return "NOT_LEADER".equals(JSON.readTree(response.body()).path("code").asText());
        } catch (IOException e) {
            return false;
        }
    }

    /** A response and the leader's API base URL it named in {@code X-Quorus-Leader}, if any. */
    private record Exchange(Response response, String leaderHint) { }

    private Exchange sendTo(String method, String url, HttpRequest.Builder request)
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
            return new Exchange(new Response(response.statusCode(), response.body()),
                    response.headers().firstValue(LEADER_HEADER).orElse(null));
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
