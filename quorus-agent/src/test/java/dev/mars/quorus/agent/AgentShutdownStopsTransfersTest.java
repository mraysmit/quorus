/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan item RT-05: agent transfers run on virtual threads, so stopping the agent interrupts a transfer
 * blocked in a socket read at once. On a platform thread the interrupt cannot break the read, and the
 * transfer ran on after the agent reported itself stopped.
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
        CountDownLatch streaming = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean offered = new AtomicBoolean();
        try (FakeController controller = FakeController.start()) {
            String job = "{\"jobId\":\"stalled\",\"agentId\":\"stall-agent\",\"sourceUri\":\"" + controller.url()
                    + "/files/stalled.dat\",\"destinationPath\":\"" + downloadRoot.resolve("stalled.dat").toUri()
                    + "\",\"totalBytes\":" + SIZE + "}";
            controller.on("POST", "/api/v1/agents/register", Reply.json(201, "{}").always())
                    .on("DELETE", "/api/v1/agents/.+", Reply.status(204).always())
                    .on("POST", "/api/v1/jobs/.+/status", Reply.json(200, "{}").always())
                    .on("GET", "/api/v1/agents/.+/jobs", request -> Reply.json(200,
                            "{\"pendingJobs\":[" + (offered.getAndSet(true) ? "" : job) + "]}"))
                    // Sends half the file, then holds the connection open without sending more.
                    .onExchange("GET", "/files/stalled.dat", exchange -> {
                        exchange.sendResponseHeaders(200, SIZE);
                        OutputStream out = exchange.getResponseBody();
                        out.write(new byte[SIZE / 2]);
                        out.flush();
                        streaming.countDown();
                        try {
                            release.await(60, TimeUnit.SECONDS);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        } finally {
                            exchange.close();
                        }
                    });
            QuorusAgent agent = new QuorusAgent(new AgentConfiguration.Builder()
                    .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                    .agentId("stall-agent").tenantId("bank-a").agentPort(0)
                    .controllerUrl(controller.url() + "/api/v1").downloadRoot(downloadRoot).uploadRoot(root)
                    .jobPollingInitialDelayMs(1).jobPollingIntervalMs(20).build());
            agent.start();
            assertTrue(streaming.await(10, TimeUnit.SECONDS), "the transfer should have started");
            awaitPartialFile(downloadRoot);

            agent.shutdown();

            assertTrue(agent.awaitShutdown(PROMPT), "the agent should stop within " + PROMPT);
            assertEquals(List.of(), filesIn(downloadRoot),
                    "shutdown returns only after the stopped transfer has removed its partial file");
        } finally {
            release.countDown();
        }
    }

    /** Waits, within the class timeout, for the running transfer to write its partial file. */
    private static void awaitPartialFile(Path directory) throws Exception {
        while (filesIn(directory).isEmpty()) {
            Thread.sleep(10);
        }
    }

    private static List<String> filesIn(Path directory) throws java.io.IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> file.getFileName().toString()).toList();
        }
    }
}
