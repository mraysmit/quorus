/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.transfer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.protocol.ProtocolFactory;
import dev.mars.quorus.protocol.TransferProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blocking transfer engine (RT-03c, ADR-0012 option A): {@code transfer} runs on the caller's
 * thread with no Vert.x, the engine enforces its concurrency limit and retries, and cancellation
 * interrupts only the transfer it names.
 *
 * <p>Every transfer goes through a real HTTP server. Synchronisation uses handshakes only: the
 * server completes {@code started} after flushing the first part of a body and then holds the
 * response until the test releases it (concurrency conventions §6).
 */
@DisplayName("SimpleTransferEngine - blocking API")
@Timeout(value = 30, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class SimpleTransferEngineBlockingTest {

    private static final byte[] BODY = "quorus blocking engine payload".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    private final List<HttpServer> servers = new ArrayList<>();
    private final Map<String, Held> held = new ConcurrentHashMap<>();
    private final AtomicInteger requests = new AtomicInteger();
    private SimpleTransferEngine engine;

    @AfterEach
    void tearDown() {
        held.values().forEach(h -> h.release.complete(null));
        if (engine != null) {
            engine.shutdown(Duration.ofSeconds(5));
        }
        servers.forEach(server -> server.stop(0));
    }

    @Test
    @DisplayName("A transfer runs on the calling thread and needs no Vert.x instance")
    void transfersOnTheCallingThreadWithoutVertx() throws Exception {
        int port = server(this::respondWithBody);
        engine = new SimpleTransferEngine(2, 0, 1);

        TransferResult result = engine.transfer(request("plain", port));

        assertTrue(result.isSuccessful(), () -> "transfer failed: " + result.getErrorMessage());
        assertArrayEquals(BODY, Files.readAllBytes(destination("plain")));
        assertEquals(0, engine.getActiveTransferCount());
    }

    @Test
    @DisplayName("A transfer beyond the concurrency limit is rejected, and accepted once a slot frees")
    void rejectsBeyondTheConcurrencyLimitAndAcceptsOnceASlotFrees() throws Exception {
        int port = server(this::holdOrRespond);
        engine = new SimpleTransferEngine(1, 0, 1);

        Running slow = start(request("held-slow", port));
        awaitStarted("held-slow");

        TransferException rejected = assertThrows(TransferException.class, () -> engine.transfer(request("fast", port)));
        assertTrue(rejected.getMessage().contains("Maximum concurrent transfers"), rejected.getMessage());

        held("held-slow").release.complete(null);
        assertTrue(slow.result.get(10, SECONDS).isSuccessful());
        assertTrue(engine.transfer(request("fast", port)).isSuccessful(), "the freed slot must accept a new transfer");
    }

    @Test
    @DisplayName("A failed attempt is retried and the transfer can still succeed")
    void retriesAFailedAttemptAndSucceeds() throws Exception {
        int port = server(exchange -> {
            if (requests.incrementAndGet() <= 2) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            } else {
                respondWithBody(exchange);
            }
        });
        engine = new SimpleTransferEngine(1, 2, 1);

        TransferResult result = engine.transfer(request("retried", port));

        assertTrue(result.isSuccessful(), () -> "transfer failed: " + result.getErrorMessage());
        assertEquals(3, requests.get());
    }

    @Test
    @DisplayName("A transfer fails after its retries are exhausted")
    void failsAfterRetriesAreExhausted() throws Exception {
        int port = server(exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        engine = new SimpleTransferEngine(1, 2, 1);

        TransferResult result = engine.transfer(request("exhausted", port));

        assertFalse(result.isSuccessful());
        assertEquals(TransferStatus.FAILED, result.getFinalStatus());
        assertEquals(3, requests.get(), "one attempt plus two retries");
    }

    @Test
    @DisplayName("Cancelling a running transfer ends it promptly as CANCELLED, without a retry")
    void cancellingARunningTransferEndsItAsCancelled() throws Exception {
        int port = server(this::holdOrRespond);
        engine = new SimpleTransferEngine(2, 3, 1);

        Running running = start(request("held-cancel", port));
        awaitStarted("held-cancel");

        var job = engine.getTransferJob("held-cancel");
        assertEquals(TransferStatus.IN_PROGRESS, job.getStatus(), "the running job is visible while it runs");
        assertEquals(1, engine.getActiveTransferCount());

        assertTrue(engine.cancelTransfer("held-cancel"));

        TransferResult result = running.result.get(10, SECONDS);
        assertEquals(TransferStatus.CANCELLED, result.getFinalStatus());
        assertEquals("Transfer cancelled", result.getErrorMessage().orElse(null),
                "consumers such as the agent report the message when a transfer is not successful");
        assertEquals(1, requests.get(), "a cancelled transfer must not be retried");
        assertFalse(running.interruptedAfterReturn.join(),
                "cancellation through the engine must not leave the caller's thread interrupted");
        assertEquals(0, engine.getActiveTransferCount());
    }

    @Test
    @DisplayName("Cancelling one transfer leaves another transfer of the same protocol running")
    void cancellingOneTransferLeavesAnotherRunning() throws Exception {
        int port = server(this::holdOrRespond);
        engine = new SimpleTransferEngine(2, 0, 1);

        Running cancelled = start(request("held-a", port));
        Running survivor = start(request("held-b", port));
        awaitStarted("held-a");
        awaitStarted("held-b");

        engine.cancelTransfer("held-a");
        assertEquals(TransferStatus.CANCELLED, cancelled.result.get(10, SECONDS).getFinalStatus());

        held("held-b").release.complete(null);
        TransferResult result = survivor.result.get(10, SECONDS);
        assertTrue(result.isSuccessful(), () -> "the other transfer was affected: " + result.getErrorMessage());
        assertArrayEquals(BODY, Files.readAllBytes(destination("held-b")));
    }

    @Test
    @DisplayName("Shutdown cancels running transfers, waits for them to end, then rejects new work")
    void shutdownCancelsAndWaitsForRunningTransfers() throws Exception {
        int port = server(this::holdOrRespond);
        engine = new SimpleTransferEngine(2, 3, 1);

        Running running = start(request("held-shutdown", port));
        awaitStarted("held-shutdown");

        assertTrue(engine.shutdown(Duration.ofSeconds(10)), "running transfers must end within the timeout");

        assertTrue(running.result.isDone(), "shutdown returns only after the transfer has ended");
        assertEquals(TransferStatus.CANCELLED, running.result.join().getFinalStatus());
        assertThrows(TransferException.class, () -> engine.transfer(request("after", port)));
    }

    @Test
    @DisplayName("An interrupt from outside the engine ends the transfer and stays set for the caller")
    void anOutsideInterruptEndsTheTransferAndIsPreserved() throws Exception {
        int port = server(this::holdOrRespond);
        // No retries: the interrupt must be recognised when the attempt fails, not only by the
        // retry back-off, which also stops on an interrupt (mutation m5).
        engine = new SimpleTransferEngine(2, 0, 1);

        Running running = start(request("held-interrupt", port));
        awaitStarted("held-interrupt");

        running.thread.interrupt();

        assertEquals(TransferStatus.CANCELLED, running.result.get(10, SECONDS).getFinalStatus());
        assertEquals(1, requests.get(), "an interrupted transfer must not be retried");
        assertTrue(running.interruptedAfterReturn.join(),
                "an interrupt the engine did not send belongs to the caller and must be restored");
    }

    // Retrospective characterization (plan §6.1): the two tests below were written after the code.

    @Test
    @DisplayName("A transfer whose ID is already running is rejected without disturbing the running one")
    void aSecondTransferWithARunningIdIsRejected() throws Exception {
        int port = server(this::holdOrRespond);
        engine = new SimpleTransferEngine(2, 0, 1);

        Running first = start(request("held-duplicate", port));
        awaitStarted("held-duplicate");

        TransferException rejected = assertThrows(TransferException.class,
                () -> engine.transfer(request("held-duplicate", port)));
        assertTrue(rejected.getMessage().contains("already running"), rejected.getMessage());
        assertEquals(1, engine.getActiveTransferCount(), "the rejected call must not take or keep a slot");

        held("held-duplicate").release.complete(null);
        assertTrue(first.result.get(10, SECONDS).isSuccessful(), "the running transfer is unaffected");
    }

    @Test
    @DisplayName("A paused transfer is stopped and reported as FAILED, as before RT-03c")
    void aPausedTransferIsStoppedAsFailed() throws Exception {
        int port = server(this::holdOrRespond);
        engine = new SimpleTransferEngine(2, 3, 1);

        Running running = start(request("held-pause", port));
        awaitStarted("held-pause");

        assertTrue(engine.pauseTransfer("held-pause"));
        held("held-pause").release.complete(null);   // the adapter checks the context before its next buffer

        TransferResult result = running.result.get(10, SECONDS);
        assertEquals(TransferStatus.FAILED, result.getFinalStatus());
        assertTrue(result.getErrorMessage().orElse("").contains("paused"), () -> "message: " + result.getErrorMessage());
        assertEquals(1, requests.get(), "a paused transfer must not be retried");
    }

    @Test
    @DisplayName("Shutdown reports false when a transfer does not end within the timeout")
    void shutdownReportsATransferThatOutlivesTheTimeout() throws Exception {
        CompletableFuture<Void> entered = new CompletableFuture<>();
        CompletableFuture<Void> release = new CompletableFuture<>();
        ProtocolFactory protocols = new ProtocolFactory();
        protocols.registerProtocol(new InterruptIgnoringProtocol(entered, release));
        engine = new SimpleTransferEngine(1, 0, 1, protocols);
        TransferRequest request = TransferRequest.builder()
                .requestId("ignores-interrupt")
                .sourceUri(URI.create("stubborn://service/file"))
                .destinationPath(destination("ignores-interrupt"))
                .protocol("stubborn")
                .build();

        Running running = start(request);
        CompletableFuture.anyOf(entered, running.result).join();
        assertTrue(entered.isDone(), "the transfer must reach the adapter");

        assertFalse(engine.shutdown(Duration.ofMillis(200)),
                "the adapter ignores the interrupt, so the transfer is still running when the timeout expires");
        assertEquals(1, engine.getActiveTransferCount());

        release.complete(null);
        running.result.get(10, SECONDS);
        assertTrue(engine.shutdown(Duration.ofSeconds(5)), "nothing is running once the transfer has ended");
        assertEquals(0, engine.getActiveTransferCount());
    }

    /** An adapter that blocks in a join, which ignores interruption, until the test releases it. */
    private record InterruptIgnoringProtocol(CompletableFuture<Void> entered, CompletableFuture<Void> release)
            implements TransferProtocol {
        @Override public String getProtocolName() { return "stubborn"; }
        @Override public boolean canHandle(TransferRequest request) { return "stubborn".equals(request.getProtocol()); }
        @Override public boolean supportsResume() { return false; }
        @Override public boolean supportsPause() { return false; }
        @Override public long getMaxFileSize() { return -1; }

        @Override
        public TransferResult transfer(TransferRequest request, TransferContext context) {
            entered.complete(null);
            release.join();
            return TransferResult.builder().requestId(request.getRequestId())
                    .finalStatus(TransferStatus.COMPLETED).bytesTransferred(0).build();
        }
    }

    // ------------------------------------------------------------------ fixtures

    /** A transfer running on its own virtual thread, as a caller that wants concurrency would run it. */
    private record Running(Thread thread, CompletableFuture<TransferResult> result,
                           CompletableFuture<Boolean> interruptedAfterReturn) {
    }

    private final Map<String, Running> transfers = new ConcurrentHashMap<>();

    /**
     * Waits until the server has started the named response, or fails at once if the transfer
     * ended first, so a transfer that never reaches the server cannot hang the test.
     */
    private void awaitStarted(String name) {
        CompletableFuture.anyOf(held(name).started, transfers.get(name).result).join();
        assertTrue(held(name).started.isDone(), () -> "the transfer ended before the server responded: "
                + transfers.get(name).result.join().getErrorMessage());
    }

    private Running start(TransferRequest request) {
        CompletableFuture<TransferResult> result = new CompletableFuture<>();
        CompletableFuture<Boolean> interrupted = new CompletableFuture<>();
        Thread thread = Thread.ofVirtual().name("transfer-" + request.getRequestId()).start(() -> {
            try {
                result.complete(engine.transfer(request));
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            } finally {
                interrupted.complete(Thread.currentThread().isInterrupted());
            }
        });
        Running started = new Running(thread, result, interrupted);
        transfers.put(request.getRequestId(), started);
        return started;
    }

    /** A response that the test holds open after its first part, until released. */
    private record Held(CompletableFuture<Void> started, CompletableFuture<Void> release) {
    }

    private Held held(String name) {
        return held.computeIfAbsent(name, n -> new Held(new CompletableFuture<>(), new CompletableFuture<>()));
    }

    /** Paths named {@code held-*} send half the body, signal, and wait for release; others respond at once. */
    private void holdOrRespond(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        String name = exchange.getRequestURI().getPath().substring(1);
        if (!name.startsWith("held-")) {
            respondWithBody(exchange);
            return;
        }
        Held h = held(name);
        exchange.sendResponseHeaders(200, BODY.length);
        try (OutputStream out = exchange.getResponseBody()) {
            int half = BODY.length / 2;
            out.write(BODY, 0, half);
            out.flush();
            h.started.complete(null);
            awaitRelease(h.release);
            out.write(BODY, half, BODY.length - half);
        }
    }

    private void respondWithBody(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(200, BODY.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(BODY);
        }
    }

    /** Server-side wait with a bound, so a failing test can never leave a server thread blocked. */
    private static void awaitRelease(CompletableFuture<Void> release) throws IOException {
        try {
            release.get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IOException("test did not release the response", e);
        }
    }

    private int server(HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", handler);
        // One virtual thread per exchange, so a held response cannot block another request.
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        servers.add(server);
        return server.getAddress().getPort();
    }

    private TransferRequest request(String name, int port) {
        return TransferRequest.builder()
                .requestId(name)
                .sourceUri(URI.create("http://127.0.0.1:" + port + "/" + name))
                .destinationPath(destination(name))
                .build();
    }

    private Path destination(String name) {
        return directory.resolve(name + ".bin");
    }
}
