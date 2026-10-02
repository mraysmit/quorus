/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent;

import com.fasterxml.jackson.databind.JsonNode;
import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.agent.testing.FakeController;
import dev.mars.quorus.agent.testing.FakeController.Reply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Register item ENG-10: while a transfer runs, the agent reports its growing byte count to the
 * controller, so progress, freshness and stall detection see the transfer between start and end.
 * Before ENG-10 the agent sent IN_PROGRESS with 0 bytes once and then only the final report.
 *
 * <p>Real agent against a fake controller that also serves the file. The file handler sends half the
 * file and holds the rest until the controller has received a progress report, so the order is
 * established by handshakes; nothing sleeps or polls.
 */
@Timeout(value = 60, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class TransferProgressReportingIntegrationTest {

    private static final int SIZE = 512 * 1024;
    private static final String STATUS = "/api/v1/jobs/.+/status";

    @TempDir Path root;
    private QuorusAgent agent;
    private FakeController controller;

    @AfterEach
    void stop() throws Exception {
        if (agent != null) {
            agent.shutdown();
            agent.awaitShutdown();
        }
        if (controller != null) controller.close();
    }

    @Test
    void aRunningTransferReportsItsProgressBeforeItCompletes() throws Exception {
        Path downloadRoot = Files.createDirectory(root.resolve("downloads"));
        Path destination = downloadRoot.resolve("settlement.dat");
        List<JsonNode> reports = new CopyOnWriteArrayList<>();
        CompletableFuture<JsonNode> progressed = new CompletableFuture<>();
        CompletableFuture<Void> completed = new CompletableFuture<>();

        controller = controller(destination);
        controller.on("POST", STATUS, request -> {
            JsonNode report = request.json();
            reports.add(report);
            if ("IN_PROGRESS".equals(report.path("status").asText()) && report.path("bytesTransferred").asLong(0) > 0) {
                progressed.complete(report);
            }
            if ("COMPLETED".equals(report.path("status").asText())) completed.complete(null);
            return Reply.json(200, "{\"success\":true}");
        });
        serveHalfUntil(progressed);

        agent = agent(downloadRoot);
        agent.start();

        JsonNode progress = progressed.get(10, TimeUnit.SECONDS);
        completed.get(10, TimeUnit.SECONDS);

        assertEquals("IN_PROGRESS", progress.get("expectedState").asText(),
                "a progress report follows the start report, so it expects IN_PROGRESS");
        // The file handler withholds the second half until this report arrives, so any report with bytes
        // moved was made mid-transfer and cannot exceed the first half. No lower bound beyond "some bytes":
        // the first report may come while the first half is still being read.
        long reported = progress.get("bytesTransferred").asLong();
        assertTrue(reported > 0 && reported <= SIZE / 2,
                () -> "the report carries the bytes moved so far, mid-transfer: " + progress);

        List<String> statuses = reports.stream().map(r -> r.get("status").asText()).toList();
        assertEquals("ACCEPTED", statuses.getFirst());
        assertEquals("COMPLETED", statuses.getLast());
        assertEquals(SIZE, reports.getLast().get("bytesTransferred").asLong());
        long previousSequence = 0;
        long previousBytes = -1;
        for (JsonNode report : reports) {
            long sequence = report.get("reportSequence").asLong();
            assertEquals(previousSequence + 1, sequence, () -> "report sequences must be contiguous: " + reports);
            previousSequence = sequence;
            if (report.has("bytesTransferred")) {
                assertTrue(report.get("bytesTransferred").asLong() >= previousBytes, () -> "bytes must not go back: " + reports);
                previousBytes = report.get("bytesTransferred").asLong();
            }
        }
        assertEquals(SIZE, Files.size(destination));
    }

    /**
     * Retrospective characterization (plan §6.1): written after the code. A progress report that stays
     * unresolved may or may not have been applied, so it is resent exactly before the final report;
     * otherwise a successful transfer's COMPLETED report could meet a sequence gap and be rejected.
     */
    @Test
    void anUnresolvedProgressReportIsResentExactlyBeforeTheFinalReport() throws Exception {
        Path downloadRoot = Files.createDirectory(root.resolve("downloads"));
        Path destination = downloadRoot.resolve("settlement.dat");
        List<JsonNode> reports = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> progressSeen = new CompletableFuture<>();
        CompletableFuture<Void> completed = new CompletableFuture<>();
        AtomicInteger dropped = new AtomicInteger();

        controller = controller(destination);
        controller.on("POST", STATUS, request -> {
            JsonNode report = request.json();
            reports.add(report);
            if ("IN_PROGRESS".equals(report.path("status").asText()) && report.path("bytesTransferred").asLong(0) > 0) {
                progressSeen.complete(null);
                if (dropped.getAndIncrement() < 3) {        // every send of the first attempt is unresolved
                    return Reply.status(503);
                }
            }
            if ("COMPLETED".equals(report.path("status").asText())) completed.complete(null);
            return Reply.json(200, "{\"success\":true}");
        });
        serveHalfUntil(progressSeen);

        agent = agent(downloadRoot);
        agent.start();

        completed.get(15, TimeUnit.SECONDS);

        List<JsonNode> progress = reports.stream()
                .filter(r -> "IN_PROGRESS".equals(r.get("status").asText()) && r.path("bytesTransferred").asLong(0) > 0)
                .toList();
        assertTrue(progress.size() >= 4, () -> "three unresolved sends and at least one resend: " + reports);
        assertTrue(progress.stream().allMatch(r -> r.equals(progress.getFirst())),
                () -> "every send and the resend carry the identical report: " + progress);
        JsonNode completedReport = reports.getLast();
        assertEquals("COMPLETED", completedReport.get("status").asText());
        assertEquals(progress.getFirst().get("reportSequence").asLong() + 1, completedReport.get("reportSequence").asLong(),
                "the final report follows the resent report without a gap");
    }

    /** A controller that offers one attempt-aware job for {@code /files/settlement.dat}, once. */
    private static FakeController controller(Path destination) throws Exception {
        FakeController controller = FakeController.start();
        AtomicBoolean offered = new AtomicBoolean();
        String job = "{\"assignmentId\":\"settlement:payments-agent\",\"jobId\":\"settlement\","
                + "\"agentId\":\"payments-agent\",\"tenantId\":\"bank-a\",\"attemptId\":\"settlement-attempt-1\","
                + "\"fencingGeneration\":1,\"lastReportSequence\":0,\"leaseExpiresAt\":\""
                + Instant.now().plusSeconds(60) + "\",\"sourceUri\":\"" + controller.url()
                + "/files/settlement.dat\",\"destinationUri\":\"" + destination.toUri() + "\"}";
        return controller
                .on("POST", "/api/v1/agents/register", Reply.json(200, "{\"status\":\"registered\"}").always())
                .on("DELETE", "/api/v1/agents/.+", Reply.status(204).always())
                .on("GET", "/api/v1/agents/.+/jobs", request -> Reply.json(200,
                        "{\"pendingJobs\":[" + (offered.getAndSet(true) ? "" : job) + "]}"));
    }

    /** Serves the file's first half, and the rest only once {@code release} completes. */
    private void serveHalfUntil(CompletableFuture<?> release) {
        byte[] body = new byte[SIZE];
        controller.onExchange("GET", "/files/settlement.dat", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, SIZE);
                OutputStream out = exchange.getResponseBody();
                out.write(body, 0, SIZE / 2);
                out.flush();
                try {
                    release.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    return;
                }
                out.write(body, SIZE / 2, SIZE / 2);
                out.close();
            }
        });
    }

    private QuorusAgent agent(Path downloadRoot) {
        return new QuorusAgent(new AgentConfiguration.Builder()
                .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                .agentId("payments-agent").tenantId("bank-a").agentPort(0)
                .controllerUrl(controller.url() + "/api/v1")
                .downloadRoot(downloadRoot).uploadRoot(root).agentPool("payments").networkZone("restricted")
                .jobPollingInitialDelayMs(1).jobPollingIntervalMs(20).progressReportIntervalMs(20).build());
    }
}
