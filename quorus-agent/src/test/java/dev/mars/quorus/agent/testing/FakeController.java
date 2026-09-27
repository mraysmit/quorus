/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent.testing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * A controller stand-in for agent tests, on the JDK HTTP server: real HTTP, no mocking. Routes match a
 * method and a path regex; the most recently added matching route answers, so a test can override a
 * default. Every request is recorded. Unmatched requests get 404.
 */
public final class FakeController implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;
    private final String scheme;
    private final List<Route> routes = new CopyOnWriteArrayList<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    private FakeController(HttpServer server, String scheme) {
        this.server = server;
        this.scheme = scheme;
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    /** A plain-HTTP controller on a free loopback port. */
    public static FakeController start() throws IOException {
        return new FakeController(HttpServer.create(loopback(), 0), "http");
    }

    /**
     * An HTTPS controller on a free loopback port that presents the context's identity, allows only
     * {@code protocols}, and requires a client certificate when {@code requireClientCertificate}.
     */
    public static FakeController startTls(SSLContext context, boolean requireClientCertificate,
                                          String... protocols) throws IOException {
        HttpsServer server = HttpsServer.create(loopback(), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(context) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters parameters = context.getDefaultSSLParameters();
                parameters.setProtocols(protocols);
                parameters.setNeedClientAuth(requireClientCertificate);
                params.setSSLParameters(parameters);
            }
        });
        return new FakeController(server, "https");
    }

    /** The base URL, for example {@code http://localhost:43127}. */
    public String url() {
        return scheme + "://localhost:" + port();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    /** Answers requests whose method and whole path match. */
    public FakeController on(String method, String pathRegex, Responder responder) {
        routes.addFirst(new Route(method, Pattern.compile(pathRegex), responder));
        return this;
    }

    /** Every request received, in arrival order. */
    public List<Request> requests() {
        return List.copyOf(requests);
    }

    /** The requests whose method and whole path match. */
    public List<Request> requests(String method, String pathRegex) {
        Pattern path = Pattern.compile(pathRegex);
        List<Request> matching = new ArrayList<>();
        for (Request request : requests) {
            if (request.method().equals(method) && path.matcher(request.path()).matches()) {
                matching.add(request);
            }
        }
        return matching;
    }

    public void clearRequests() {
        requests.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requests.add(request);
            Reply reply = Reply.status(404);
            for (Route route : routes) {
                if (route.method().equals(request.method()) && route.path().matcher(request.path()).matches()) {
                    try {
                        reply = route.responder().reply(request);
                    } catch (Exception e) {
                        reply = Reply.status(500);
                    }
                    break;
                }
            }
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            if (!reply.body().isEmpty()) {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
            }
            exchange.sendResponseHeaders(reply.status(), body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        }
    }

    private static InetSocketAddress loopback() {
        return new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
    }

    private record Route(String method, Pattern path, Responder responder) { }

    /** Produces the reply to one request. */
    @FunctionalInterface
    public interface Responder {
        Reply reply(Request request) throws Exception;
    }

    /** A received request. */
    public record Request(String method, String path, String body) {
        /** The body parsed as JSON. */
        public JsonNode json() {
            try {
                return JSON.readTree(body);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** A reply: a status and an optional JSON body. */
    public record Reply(int status, String body) {
        public static Reply status(int status) {
            return new Reply(status, "");
        }

        public static Reply json(int status, String body) {
            return new Reply(status, body);
        }

        /** Answers every request with this reply. */
        public Responder always() {
            return request -> this;
        }
    }
}
