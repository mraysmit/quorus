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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final Object arrivals = new Object();

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
        routes.addFirst(new Route(method, Pattern.compile(pathRegex), responder, null));
        return this;
    }

    /**
     * Handles matching requests with full control of the exchange, for example to stream part of a
     * body and stall. The request is recorded, but its body is not read for the record.
     */
    public FakeController onExchange(String method, String pathRegex, ExchangeHandler handler) {
        routes.addFirst(new Route(method, Pattern.compile(pathRegex), null, handler));
        return this;
    }

    /**
     * Waits until at least {@code count} matching requests have arrived, and returns them. Woken by
     * each arrival; nothing polls.
     *
     * @throws AssertionError if they have not arrived within {@code timeout}
     */
    public List<Request> awaitRequests(String method, String pathRegex, int count, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        synchronized (arrivals) {
            while (true) {
                List<Request> matching = requests(method, pathRegex);
                if (matching.size() >= count) {
                    return matching;
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new AssertionError("expected " + count + " " + method + " " + pathRegex
                            + " request(s) within " + timeout + ", got " + matching.size() + ": " + requests);
                }
                arrivals.wait(Math.max(1, remaining / 1_000_000));
            }
        }
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
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        for (Route route : routes) {
            if (route.exchangeHandler() != null && route.method().equals(method) && route.path().matcher(path).matches()) {
                record(new Request(method, path, "", headers(exchange)));
                route.exchangeHandler().handle(exchange);
                return;
            }
        }
        try (exchange) {
            Request request = new Request(method, path,
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), headers(exchange));
            record(request);
            Reply reply = Reply.status(404);
            for (Route route : routes) {
                if (route.responder() != null && route.method().equals(request.method())
                        && route.path().matcher(request.path()).matches()) {
                    try {
                        reply = route.responder().reply(request);
                    } catch (Exception e) {
                        reply = Reply.status(500);
                    }
                    break;
                }
            }
            if (reply.status() == Reply.DROP) {
                return;                               // closing without a response drops the connection
            }
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            if (!reply.body().isEmpty()) {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
            }
            reply.headers().forEach(exchange.getResponseHeaders()::add);
            exchange.sendResponseHeaders(reply.status(), body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        }
    }

    private void record(Request request) {
        synchronized (arrivals) {
            requests.add(request);
            arrivals.notifyAll();
        }
    }

    private static Map<String, String> headers(HttpExchange exchange) {
        Map<String, String> headers = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, values.getFirst()));
        return headers;
    }

    private static InetSocketAddress loopback() {
        return new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
    }

    private record Route(String method, Pattern path, Responder responder, ExchangeHandler exchangeHandler) { }

    /** Handles a whole exchange, including sending the response and closing it. */
    @FunctionalInterface
    public interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    /** Produces the reply to one request. */
    @FunctionalInterface
    public interface Responder {
        Reply reply(Request request) throws Exception;
    }

    /** A received request; header names are case-insensitive. */
    public record Request(String method, String path, String body, Map<String, String> headers) {
        /** The body parsed as JSON. */
        public JsonNode json() {
            try {
                return JSON.readTree(body);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** A reply: a status, an optional JSON body and optional response headers. */
    public record Reply(int status, String body, Map<String, String> headers) {
        private static final int DROP = -1;

        /** No response at all: the connection is closed, so the client sees a transport failure. */
        public static Reply drop() {
            return new Reply(DROP, "", Map.of());
        }

        public static Reply status(int status) {
            return new Reply(status, "", Map.of());
        }

        public static Reply json(int status, String body) {
            return new Reply(status, body, Map.of());
        }

        /** This reply with one more response header. */
        public Reply header(String name, String value) {
            Map<String, String> extended = new java.util.LinkedHashMap<>(headers);
            extended.put(name, value);
            return new Reply(status, body, Map.copyOf(extended));
        }

        /** Answers every request with this reply. */
        public Responder always() {
            return request -> this;
        }
    }
}
