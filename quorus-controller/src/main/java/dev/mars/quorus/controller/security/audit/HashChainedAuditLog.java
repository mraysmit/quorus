/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security.audit;

import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Append-only, fsync'd JSONL audit log whose records form a SHA-256 hash chain.
 *
 * <p>Group commit (register item ENG-16): {@link #appendAsync} writes the record in chain order at once
 * and completes only after a disk sync that covers it. A single sync thread, started when records are
 * waiting and ending when none are, syncs everything written before each sync began, so records that
 * arrive during a sync share the next one instead of each paying a sync in series. A failed write or sync
 * fails every waiting record and every later append: the log fails closed. {@link #append} waits for the
 * sync, so blocking callers keep "durable before return".
 */
public final class HashChainedAuditLog implements AuditSink {

    /** Makes written records durable. Replaceable in tests to control the order of syncs. */
    @FunctionalInterface
    interface Sync {
        void sync(FileChannel channel) throws IOException;
    }

    private record Waiter(long sequence, CompletableFuture<Void> durable) { }

    private final FileChannel channel;
    private final Sync sync;
    private final Object lock = new Object();
    private final Deque<Waiter> waiting = new ArrayDeque<>();
    private String previousHash;
    private long written;
    private boolean syncing;
    private IllegalStateException failure;
    private Thread syncThread;

    public HashChainedAuditLog(Path path) {
        this(path, channel -> channel.force(true));
    }

    HashChainedAuditLog(Path path, Sync sync) {
        this.sync = sync;
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            this.previousHash = verifyAndLastHash(path);
            this.channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot open security audit log " + path, exception);
        }
    }

    private static String verifyAndLastHash(Path path) throws IOException {
        if (!Files.exists(path) || Files.size(path) == 0) return "GENESIS";
        try (java.util.stream.Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            String expectedPrevious = "GENESIS";
            int lineNumber = 0;
            for (String line : (Iterable<String>) lines.filter(value -> !value.isBlank())::iterator) {
                lineNumber++;
                JsonObject record = new JsonObject(line);
                String previous = record.getString("previousHash");
                String storedHash = record.getString("hash");
                if (!expectedPrevious.equals(previous) || storedHash == null || storedHash.isBlank()) {
                    throw new IOException("Security audit chain link is invalid at record " + lineNumber);
                }
                record.remove("hash");
                String calculatedHash = sha256(record.encode());
                if (!calculatedHash.equals(storedHash)) {
                    throw new IOException("Security audit record hash is invalid at record " + lineNumber);
                }
                expectedPrevious = storedHash;
            }
            if (lineNumber == 0) throw new IOException("Audit log has no complete record");
            return expectedPrevious;
        }
    }

    /** Appends and waits until the record is durable. */
    @Override
    public void append(AuditEvent event) {
        try {
            appendAsync(event).join();
        } catch (CompletionException exception) {
            throw exception.getCause() instanceof RuntimeException runtime ? runtime
                    : new IllegalStateException("Security audit record could not be persisted", exception.getCause());
        }
    }

    /** Writes the record in chain order now; the future completes once a sync covers it. */
    @Override
    public CompletableFuture<Void> appendAsync(AuditEvent event) {
        CompletableFuture<Void> durable = new CompletableFuture<>();
        synchronized (lock) {
            if (failure != null) {
                durable.completeExceptionally(failure);
                return durable;
            }
            try {
                write(event);
            } catch (IOException exception) {
                fail(new IllegalStateException("Security audit record could not be persisted", exception));
                durable.completeExceptionally(failure);
                return durable;
            }
            waiting.addLast(new Waiter(++written, durable));
            if (!syncing) {
                syncing = true;
                syncThread = Thread.ofVirtual().name("security-audit-sync").start(this::syncWhileWaiting);
            }
        }
        return durable;
    }

    /** Syncs until no record is waiting; each sync covers every record written before it began. */
    private void syncWhileWaiting() {
        while (true) {
            long covered;
            synchronized (lock) {
                if (waiting.isEmpty() || failure != null) {
                    syncing = false;
                    return;
                }
                covered = written;
            }
            try {
                sync.sync(channel);
            } catch (IOException | RuntimeException exception) {
                synchronized (lock) {
                    fail(new IllegalStateException("Security audit record could not be persisted", exception));
                    syncing = false;
                }
                return;
            }
            List<CompletableFuture<Void>> durable = new ArrayList<>();
            synchronized (lock) {
                while (!waiting.isEmpty() && waiting.peekFirst().sequence() <= covered) {
                    durable.add(waiting.removeFirst().durable());
                }
            }
            durable.forEach(future -> future.complete(null));
        }
    }

    /** Records the failure and fails every waiting record. Call holding the lock. */
    private void fail(IllegalStateException cause) {
        failure = cause;
        while (!waiting.isEmpty()) {
            waiting.removeFirst().durable().completeExceptionally(cause);
        }
    }

    /** Builds, chains and writes one record. Call holding the lock. */
    private void write(AuditEvent event) throws IOException {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("timestamp", event.timestamp().toString());
        record.put("eventType", event.eventType());
        record.put("outcome", event.outcome());
        record.put("decisionCode", event.decisionCode());
        record.put("principalId", event.principalId());
        record.put("identityType", event.identityType());
        record.put("tenantId", event.tenantId());
        record.put("environment", event.environment());
        record.put("certificateSubject", event.certificateSubject());
        record.put("method", event.method());
        record.put("path", event.path());
        record.put("requestId", event.requestId());
        record.put("attributes", event.attributes());
        record.put("previousHash", previousHash);
        String canonical = new JsonObject(record).encode();
        String hash = sha256(canonical);
        record.put("hash", hash);
        byte[] bytes = (new JsonObject(record).encode() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        previousHash = hash;
    }

    /** Waits for the sync thread to finish the records already written, then closes the file. */
    @Override
    public void close() {
        Thread pending;
        synchronized (lock) {
            pending = syncing ? syncThread : null;
        }
        if (pending != null) {
            try {
                pending.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            channel.close();
        } catch (IOException exception) {
            throw new IllegalStateException("Security audit log could not be closed", exception);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
