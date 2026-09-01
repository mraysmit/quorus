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
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Append-only, fsync'd JSONL audit log whose records form a SHA-256 hash chain. */
public final class HashChainedAuditLog implements AuditSink {
    private final FileChannel channel;
    private String previousHash;

    public HashChainedAuditLog(Path path) {
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

    @Override
    public synchronized void append(AuditEvent event) {
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
        try {
            channel.write(ByteBuffer.wrap(bytes));
            channel.force(true);
            previousHash = hash;
        } catch (IOException exception) {
            throw new IllegalStateException("Security audit record could not be persisted", exception);
        }
    }

    @Override
    public synchronized void close() {
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
