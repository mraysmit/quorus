/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.raft.storage;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The factory is the boundary through which the controller obtains its durable Raft log. The
 * only durable implementation is the raftlog-core library behind {@link RaftLogStorageAdapter}.
 */
@ExtendWith(VertxExtension.class)
class RaftStorageFactoryTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @ParameterizedTest
    @ValueSource(strings = {"memory", "inmemory", "in-memory", "test", "rocksdb", "rocks"})
    void internalBackendsAreRejectedBeforeOpeningStorage(String type, Vertx vertx) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> {
            // Close any incorrectly accepted backend before failing the assertion.
            RaftStorage accepted = awaitSuccess(RaftStorageFactory.create(vertx, type, tempDir, true), TIMEOUT);
            awaitSuccess(accepted.close(), TIMEOUT);
        });
        assertTrue(error.getMessage().contains("raftlog"));
    }

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("The raftlog type yields the library-backed storage")
    void raftlogTypeYieldsLibraryBackedStorage(Vertx vertx) {
        RaftStorage storage = awaitSuccess(
                RaftStorageFactory.create(vertx, "raftlog", tempDir.resolve("raftlog"), true), TIMEOUT);

        assertInstanceOf(RaftLogStorageAdapter.class, storage);
        awaitSuccess(storage.close(), TIMEOUT);
    }

    @Test
    @DisplayName("A blank type defaults to the library-backed storage")
    void blankTypeDefaultsToLibraryBackedStorage(Vertx vertx) {
        RaftStorage storage = awaitSuccess(
                RaftStorageFactory.create(vertx, "", tempDir.resolve("blank"), true), TIMEOUT);

        assertInstanceOf(RaftLogStorageAdapter.class, storage);
        awaitSuccess(storage.close(), TIMEOUT);
    }

    @Test
    @DisplayName("The removed in-repo file type is rejected")
    void fileTypeIsRejected(Vertx vertx) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RaftStorageFactory.create(vertx, "file", tempDir.resolve("file"), true));

        assertTrue(error.getMessage().contains("raftlog"), "the error names the supported types");
    }
}
