/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent;

import dev.mars.quorus.agent.config.AgentConfiguration;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Register item ENG-10: while a transfer runs, the agent reports its growing byte count to the
 * controller, so progress, freshness and stall detection see the transfer between start and end.
 * Before ENG-10 the agent sent IN_PROGRESS with 0 bytes once and then only the final report.
 *
 * <p>Real agent against a fake controller and a file server. The file server sends half the file and
 * holds the rest until the fake controller has received a progress report, so the order is
 * established by handshakes; nothing sleeps or polls.
 */
@ExtendWith(VertxExtension.class)
class TransferProgressReportingIntegrationTest {

    private static final int SIZE = 512 * 1024;

    @TempDir Path root;
    private QuorusAgent agent;
    private HttpServer server;

    @AfterEach
    void stop() throws Exception {
        if (agent != null) {
            agent.shutdown();
            agent.awaitShutdown();
        }
        if (server != null) awaitSuccess(server.close(), Duration.ofSeconds(5));
    }

    @Test
    void aRunningTransferReportsItsProgressBeforeItCompletes(Vertx vertx) throws Exception {
        Path downloadRoot = Files.createDirectory(root.resolve("downloads"));
        Path destination = downloadRoot.resolve("settlement.dat");
        List<JsonObject> reports = new CopyOnWriteArrayList<>();
        Promise<JsonObject> progressed = Promise.promise();
        Promise<Void> completed = Promise.promise();
        AtomicBoolean offered = new AtomicBoolean();

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.post("/api/v1/agents/register").handler(ctx -> ctx.json(new JsonObject().put("status", "registered")));
        router.delete("/api/v1/agents/:id").handler(ctx -> ctx.response().setStatusCode(204).end());
        router.post("/api/v1/jobs/:id/status").handler(ctx -> {
            JsonObject report = ctx.body().asJsonObject();
            reports.add(report.copy());
            ctx.json(new JsonObject().put("success", true));
            if ("IN_PROGRESS".equals(report.getString("status")) && report.getLong("bytesTransferred", 0L) > 0) {
                progressed.tryComplete(report);
            }
            if ("COMPLETED".equals(report.getString("status"))) completed.tryComplete();
        });
        byte[] body = new byte[SIZE];
        router.get("/files/settlement.dat").handler(ctx -> {
            ctx.response().putHeader("Content-Length", String.valueOf(SIZE));
            ctx.response().write(Buffer.buffer(body).slice(0, SIZE / 2));
            // Hold the second half until the controller has seen progress for the first.
            progressed.future().onComplete(ignored -> ctx.response().end(Buffer.buffer(body).slice(SIZE / 2, SIZE)));
        });
        server = awaitSuccess(vertx.createHttpServer().requestHandler(router).listen(0), Duration.ofSeconds(5));
        int port = server.actualPort();
        JsonObject job = new JsonObject()
                .put("assignmentId", "settlement:payments-agent")
                .put("jobId", "settlement").put("agentId", "payments-agent")
                .put("tenantId", "bank-a").put("attemptId", "settlement-attempt-1")
                .put("fencingGeneration", 1L).put("lastReportSequence", 0L)
                .put("leaseExpiresAt", Instant.now().plusSeconds(60).toString())
                .put("sourceUri", "http://localhost:" + port + "/files/settlement.dat")
                .put("destinationUri", destination.toUri().toString());
        router.get("/api/v1/agents/:id/jobs").handler(ctx -> ctx.json(new JsonObject()
                .put("pendingJobs", offered.getAndSet(true) ? new JsonArray() : new JsonArray().add(job))));

        agent = new QuorusAgent(vertx, new AgentConfiguration.Builder()
                .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                .agentId("payments-agent").tenantId("bank-a").agentPort(0)
                .controllerUrl("http://localhost:" + port + "/api/v1")
                .downloadRoot(downloadRoot).uploadRoot(root).agentPool("payments").networkZone("restricted")
                .jobPollingInitialDelayMs(1).jobPollingIntervalMs(20).progressReportIntervalMs(20).build());
        agent.start();

        JsonObject progress = awaitSuccess(progressed.future(), Duration.ofSeconds(10));
        awaitSuccess(completed.future(), Duration.ofSeconds(10));

        assertEquals("IN_PROGRESS", progress.getString("expectedState"),
                "a progress report follows the start report, so it expects IN_PROGRESS");
        assertTrue(progress.getLong("bytesTransferred") >= SIZE / 2 - 64 * 1024,
                () -> "the report carries the bytes already moved: " + progress);
        assertTrue(progress.getLong("bytesTransferred") < SIZE, "reported while the transfer was still running");

        List<String> statuses = reports.stream().map(r -> r.getString("status")).toList();
        assertEquals("ACCEPTED", statuses.getFirst());
        assertEquals("COMPLETED", statuses.getLast());
        assertEquals(SIZE, reports.getLast().getLong("bytesTransferred"));
        long previousSequence = 0;
        long previousBytes = -1;
        for (JsonObject report : reports) {
            long sequence = report.getLong("reportSequence");
            assertEquals(previousSequence + 1, sequence, () -> "report sequences must be contiguous: " + reports);
            previousSequence = sequence;
            if (report.containsKey("bytesTransferred")) {
                assertTrue(report.getLong("bytesTransferred") >= previousBytes, () -> "bytes must not go back: " + reports);
                previousBytes = report.getLong("bytesTransferred");
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
    void anUnresolvedProgressReportIsResentExactlyBeforeTheFinalReport(Vertx vertx) throws Exception {
        Path downloadRoot = Files.createDirectory(root.resolve("downloads"));
        Path destination = downloadRoot.resolve("settlement.dat");
        List<JsonObject> reports = new CopyOnWriteArrayList<>();
        Promise<Void> progressSeen = Promise.promise();
        Promise<Void> completed = Promise.promise();
        AtomicBoolean offered = new AtomicBoolean();
        java.util.concurrent.atomic.AtomicInteger dropped = new java.util.concurrent.atomic.AtomicInteger();

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.post("/api/v1/agents/register").handler(ctx -> ctx.json(new JsonObject().put("status", "registered")));
        router.delete("/api/v1/agents/:id").handler(ctx -> ctx.response().setStatusCode(204).end());
        router.post("/api/v1/jobs/:id/status").handler(ctx -> {
            JsonObject report = ctx.body().asJsonObject();
            reports.add(report.copy());
            boolean progress = "IN_PROGRESS".equals(report.getString("status"))
                    && report.getLong("bytesTransferred", 0L) > 0;
            if (progress) {
                progressSeen.tryComplete();
                if (dropped.getAndIncrement() < 3) {        // every send of the first attempt is lost
                    ctx.request().connection().close();
                    return;
                }
            }
            ctx.json(new JsonObject().put("success", true));
            if ("COMPLETED".equals(report.getString("status"))) completed.tryComplete();
        });
        byte[] body = new byte[SIZE];
        router.get("/files/settlement.dat").handler(ctx -> {
            ctx.response().putHeader("Content-Length", String.valueOf(SIZE));
            ctx.response().write(Buffer.buffer(body).slice(0, SIZE / 2));
            progressSeen.future().onComplete(ignored -> ctx.response().end(Buffer.buffer(body).slice(SIZE / 2, SIZE)));
        });
        server = awaitSuccess(vertx.createHttpServer().requestHandler(router).listen(0), Duration.ofSeconds(5));
        int port = server.actualPort();
        JsonObject job = new JsonObject()
                .put("assignmentId", "settlement:payments-agent")
                .put("jobId", "settlement").put("agentId", "payments-agent")
                .put("tenantId", "bank-a").put("attemptId", "settlement-attempt-1")
                .put("fencingGeneration", 1L).put("lastReportSequence", 0L)
                .put("leaseExpiresAt", Instant.now().plusSeconds(60).toString())
                .put("sourceUri", "http://localhost:" + port + "/files/settlement.dat")
                .put("destinationUri", destination.toUri().toString());
        router.get("/api/v1/agents/:id/jobs").handler(ctx -> ctx.json(new JsonObject()
                .put("pendingJobs", offered.getAndSet(true) ? new JsonArray() : new JsonArray().add(job))));

        agent = new QuorusAgent(vertx, new AgentConfiguration.Builder()
                .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                .agentId("payments-agent").tenantId("bank-a").agentPort(0)
                .controllerUrl("http://localhost:" + port + "/api/v1")
                .downloadRoot(downloadRoot).uploadRoot(root).agentPool("payments").networkZone("restricted")
                .jobPollingInitialDelayMs(1).jobPollingIntervalMs(20).progressReportIntervalMs(20).build());
        agent.start();

        awaitSuccess(completed.future(), Duration.ofSeconds(15));

        List<JsonObject> progress = reports.stream()
                .filter(r -> "IN_PROGRESS".equals(r.getString("status")) && r.getLong("bytesTransferred", 0L) > 0)
                .toList();
        assertTrue(progress.size() >= 4, () -> "three lost sends and at least one resend: " + reports);
        assertTrue(progress.stream().allMatch(r -> r.equals(progress.getFirst())),
                () -> "every send and the resend carry the identical report: " + progress);
        JsonObject completedReport = reports.getLast();
        assertEquals("COMPLETED", completedReport.getString("status"));
        assertEquals(progress.getFirst().getLong("reportSequence") + 1, completedReport.getLong("reportSequence"),
                "the final report follows the resent report without a gap");
    }
}
