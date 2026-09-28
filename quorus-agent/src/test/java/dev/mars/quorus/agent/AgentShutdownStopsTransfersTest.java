/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent;

import com.fasterxml.jackson.databind.JsonNode;
import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.agent.testing.FakeController;
import dev.mars.quorus.agent.testing.FakeController.Reply;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan item RT-05: agent transfers run on virtual threads, so stopping the agent interrupts a transfer
 * blocked in a socket read at once. On a platform thread the interrupt cannot break the read, and the
 * transfer ran on after the agent reported itself stopped.
 *
 * <p>Order comes from handshakes: the file handler sends half the file and blocks until the test
 * releases it; a progress report with bytes moved shows the transfer is in its read.
 */
@Timeout(value = 90, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class AgentShutdownStopsTransfersTest {

    private static final int SIZE = 512 * 1024;
    private static final Duration PROMPT = Duration.ofSeconds(10);

    @TempDir
    Path root;

    @Test
    void shutdownStopsATransferBlockedInASocketReadAtOnce() throws Exception {
        Path downloadRoot = Files.createDirectory(root.resolve("downloads"));
        CompletableFuture<Void> release = new CompletableFuture<>();
        CompletableFuture<Void> reading = new CompletableFuture<>();
        AtomicBoolean offered = new AtomicBoolean();
        try (FakeController controller = FakeController.start()) {
            String job = "{\"jobId\":\"stalled\",\"agentId\":\"stall-agent\",\"attemptId\":\"stalled-1\","
                    + "\"fencingGeneration\":1,\"leaseExpiresAt\":\"" + Instant.now().plusSeconds(120) + "\","
                    + "\"sourceUri\":\"" + controller.url() + "/files/stalled.dat\",\"destinationUri\":\""
                    + downloadRoot.resolve("stalled.dat").toUri() + "\",\"totalBytes\":" + SIZE + "}";
            controller.on("POST", "/api/v1/agents/register", Reply.json(201, "{}").always())
                    .on("DELETE", "/api/v1/agents/.+", Reply.status(204).always())
                    .on("POST", "/api/v1/jobs/.+/status", request -> {
                        JsonNode report = request.json();
                        if ("IN_PROGRESS".equals(report.path("status").asText())
                                && report.path("bytesTransferred").asLong(0) > 0) {
                            reading.complete(null);
                        }
                        return Reply.json(200, "{}");
                    })
                    .on("GET", "/api/v1/agents/.+/jobs", request -> Reply.json(200,
                            "{\"pendingJobs\":[" + (offered.getAndSet(true) ? "" : job) + "]}"))
                    // Sends half the file, then holds the connection open until the test releases it.
                    .onExchange("GET", "/files/stalled.dat", exchange -> {
                        try (exchange) {
                            exchange.sendResponseHeaders(200, SIZE);
                            OutputStream out = exchange.getResponseBody();
                            out.write(new byte[SIZE / 2]);
                            out.flush();
                            release.get(60, TimeUnit.SECONDS);
                        } catch (Exception ignored) {
                            // Released, or the test ended.
                        }
                    });
            QuorusAgent agent = new QuorusAgent(new AgentConfiguration.Builder()
                    .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                    .agentId("stall-agent").tenantId("bank-a").agentPort(0)
                    .controllerUrl(controller.url() + "/api/v1").downloadRoot(downloadRoot).uploadRoot(root)
                    .jobPollingInitialDelayMs(1).jobPollingIntervalMs(20).progressReportIntervalMs(20).build());
            agent.start();
            reading.get(10, TimeUnit.SECONDS);
            assertFalse(filesIn(downloadRoot).isEmpty(), "the running transfer has written a partial file");

            agent.shutdown();

            assertTrue(agent.awaitShutdown(PROMPT), "the agent should stop within " + PROMPT);
            assertEquals(List.of(), filesIn(downloadRoot),
                    "shutdown returns only after the stopped transfer has removed its partial file");
        } finally {
            release.complete(null);
        }
    }

    private static List<String> filesIn(Path directory) throws java.io.IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> file.getFileName().toString()).toList();
        }
    }
}
