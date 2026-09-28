/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Register item ENG-16: the audit log syncs records in groups. A record is acknowledged only after a sync
 * that covers it, but one sync covers every record written before it began, so concurrent requests no
 * longer pay one disk sync each, in series. Syncs are controlled through the log's sync hook, so every order
 * below is fixed by handshakes.
 */
@Timeout(value = 30, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class HashChainedAuditLogGroupCommitTest {

    @TempDir
    Path directory;

    @Test
    void aRecordIsAcknowledgedOnlyAfterASyncCoversIt() throws Exception {
        GatedSync sync = new GatedSync();
        try (HashChainedAuditLog log = new HashChainedAuditLog(directory.resolve("audit.jsonl"), sync)) {
            CompletableFuture<Void> appended = log.appendAsync(event("ALLOW"));

            sync.started.get(10, TimeUnit.SECONDS);
            assertFalse(appended.isDone(), "not acknowledged while its sync is still running");

            sync.release.complete(null);
            appended.get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void recordsWrittenDuringASyncShareTheNextSync() throws Exception {
        GatedSync sync = new GatedSync();
        try (HashChainedAuditLog log = new HashChainedAuditLog(directory.resolve("audit.jsonl"), sync)) {
            List<CompletableFuture<Void>> appended = new ArrayList<>();
            appended.add(log.appendAsync(event("first")));
            sync.started.get(10, TimeUnit.SECONDS);                // the first sync is running and held
            for (int i = 0; i < 49; i++) {
                appended.add(log.appendAsync(event("queued-" + i)));
            }

            sync.release.complete(null);
            CompletableFuture.allOf(appended.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);

            assertEquals(2, sync.syncs.get(), "one sync for the first record, one for the 49 queued behind it");
        }
        assertEquals(50, Files.readAllLines(directory.resolve("audit.jsonl")).size());
    }

    @Test
    void aFailedSyncFailsItsRecordsAndEveryLaterAppend() throws Exception {
        HashChainedAuditLog.Sync failing = channel -> {
            throw new IOException("disk gone");
        };
        try (HashChainedAuditLog log = new HashChainedAuditLog(directory.resolve("audit.jsonl"), failing)) {
            CompletableFuture<Void> first = log.appendAsync(event("ALLOW"));

            CompletionException failure = assertThrows(CompletionException.class, first::join);
            assertInstanceOf(IllegalStateException.class, failure.getCause());
            assertThrows(CompletionException.class, () -> log.appendAsync(event("later")).join(),
                    "the log fails closed after a lost sync");
            assertThrows(IllegalStateException.class, () -> log.append(event("blocking")));
        }
    }

    @Test
    void concurrentAppendsKeepOneValidChain() throws Exception {
        Path path = directory.resolve("audit.jsonl");
        try (HashChainedAuditLog log = new HashChainedAuditLog(path)) {
            List<Thread> writers = new ArrayList<>();
            for (int w = 0; w < 10; w++) {
                int writer = w;
                writers.add(Thread.ofVirtual().start(() -> {
                    for (int i = 0; i < 20; i++) {
                        log.append(event("writer-" + writer + "-" + i));
                    }
                }));
            }
            for (Thread writer : writers) {
                writer.join();
            }
        }

        assertEquals(200, Files.readAllLines(path).size());
        assertDoesNotThrow(() -> new HashChainedAuditLog(path).close(), "reopening verifies the whole chain");
    }

    @Test
    void theCompositeWritesRetainedEvidenceBeforeTheOperationalLog() throws Exception {
        GatedSync evidenceSync = new GatedSync();
        Path evidence = directory.resolve("evidence.jsonl");
        Path operational = directory.resolve("operational.jsonl");
        try (AuditSink sink = AuditSink.composite(new HashChainedAuditLog(evidence, evidenceSync),
                new HashChainedAuditLog(operational))) {
            CompletableFuture<Void> appended = sink.appendAsync(event("ALLOW"));
            evidenceSync.started.get(10, TimeUnit.SECONDS);

            assertEquals(0, Files.size(operational), "nothing reaches the operational log before evidence is durable");

            evidenceSync.release.complete(null);
            appended.get(10, TimeUnit.SECONDS);
        }
        assertEquals(Files.readString(evidence), Files.readString(operational));
    }

    /** A sync that signals when the first one starts and holds it until released; later syncs pass. */
    private static final class GatedSync implements HashChainedAuditLog.Sync {
        final CompletableFuture<Void> started = new CompletableFuture<>();
        final CompletableFuture<Void> release = new CompletableFuture<>();
        final AtomicInteger syncs = new AtomicInteger();

        @Override
        public void sync(java.nio.channels.FileChannel channel) throws IOException {
            syncs.incrementAndGet();
            started.complete(null);
            release.join();
            channel.force(true);
        }
    }

    private static AuditEvent event(String outcome) {
        return new AuditEvent(Instant.now(), "AUTHORIZATION", outcome, "Q-AUTHZ-TEST",
                "principal", "HUMAN", "tenant-a", "production", "CN=gateway",
                "GET", "/api/v1/info", "request-1", Map.of());
    }
}
