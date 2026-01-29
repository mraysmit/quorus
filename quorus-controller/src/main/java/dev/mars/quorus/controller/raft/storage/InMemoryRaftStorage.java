/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.mars.quorus.controller.raft.storage;

import io.vertx.core.Future;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * In-memory implementation of {@link RaftStorage} for unit testing.
 *
 * <p><b>WARNING: NOT FOR PRODUCTION USE.</b> This implementation provides
 * no durability - all data is lost when the process terminates.</p>
 *
 * <p>This implementation is useful for:</p>
 * <ul>
 *   <li>Unit testing RaftNode logic without disk I/O</li>
 *   <li>Testing error handling (via {@link #setFailOnSync(boolean)})</li>
 *   <li>Fast integration tests that don't require persistence</li>
 * </ul>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-01-29
 */
public final class InMemoryRaftStorage implements RaftStorage {

    private long currentTerm = 0;
    private Optional<String> votedFor = Optional.empty();
    private final List<LogEntryData> log = new ArrayList<>();

    private boolean opened = false;
    private boolean closed = false;
    private boolean failOnSync = false;
    private boolean failOnAppend = false;
    private boolean failOnMetadataUpdate = false;

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    public Future<Void> open(Path dataDir) {
        if (closed) {
            return Future.failedFuture(new IllegalStateException("Storage has been closed"));
        }
        opened = true;
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> close() {
        opened = false;
        closed = true;
        log.clear();
        return Future.succeededFuture();
    }

    // =========================================================================
    // Metadata Operations
    // =========================================================================

    @Override
    public Future<Void> updateMetadata(long currentTerm, Optional<String> votedFor) {
        if (!opened || closed) {
            return Future.failedFuture(new IllegalStateException("Storage not open"));
        }
        if (failOnMetadataUpdate) {
            return Future.failedFuture(new IOException("Simulated metadata update failure"));
        }
        this.currentTerm = currentTerm;
        this.votedFor = votedFor;
        return Future.succeededFuture();
    }

    @Override
    public Future<PersistentMeta> loadMetadata() {
        if (closed) {
            return Future.failedFuture(new IllegalStateException("Storage has been closed"));
        }
        return Future.succeededFuture(new PersistentMeta(currentTerm, votedFor));
    }

    // =========================================================================
    // Log Operations
    // =========================================================================

    @Override
    public Future<Void> appendEntries(List<LogEntryData> entries) {
        if (!opened || closed) {
            return Future.failedFuture(new IllegalStateException("Storage not open"));
        }
        if (failOnAppend) {
            return Future.failedFuture(new IOException("Simulated append failure"));
        }
        log.addAll(entries);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> truncateSuffix(long fromIndex) {
        if (!opened || closed) {
            return Future.failedFuture(new IllegalStateException("Storage not open"));
        }
        log.removeIf(e -> e.index() >= fromIndex);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> sync() {
        if (!opened || closed) {
            return Future.failedFuture(new IllegalStateException("Storage not open"));
        }
        if (failOnSync) {
            return Future.failedFuture(new IOException("Simulated sync failure"));
        }
        // No-op for in-memory storage
        return Future.succeededFuture();
    }

    @Override
    public Future<List<LogEntryData>> replayLog() {
        if (closed) {
            return Future.failedFuture(new IllegalStateException("Storage has been closed"));
        }
        return Future.succeededFuture(new ArrayList<>(log));
    }

    // =========================================================================
    // Test Helpers
    // =========================================================================

    /**
     * Configures the storage to fail on sync operations.
     *
     * <p>This is useful for testing the "Ghost ACK Prevention" scenario
     * where a follower must not acknowledge entries it failed to persist.</p>
     *
     * @param fail true to simulate sync failures, false for normal operation
     */
    public void setFailOnSync(boolean fail) {
        this.failOnSync = fail;
    }

    /**
     * Configures the storage to fail on append operations.
     *
     * @param fail true to simulate append failures, false for normal operation
     */
    public void setFailOnAppend(boolean fail) {
        this.failOnAppend = fail;
    }

    /**
     * Configures the storage to fail on metadata update operations.
     *
     * @param fail true to simulate metadata update failures, false for normal operation
     */
    public void setFailOnMetadataUpdate(boolean fail) {
        this.failOnMetadataUpdate = fail;
    }

    /**
     * Returns an unmodifiable view of the current log.
     *
     * @return the log entries
     */
    public List<LogEntryData> getLog() {
        return Collections.unmodifiableList(log);
    }

    /**
     * Returns the current term.
     *
     * @return the current term
     */
    public long getCurrentTerm() {
        return currentTerm;
    }

    /**
     * Returns the voted-for candidate.
     *
     * @return the voted-for candidate, or empty if none
     */
    public Optional<String> getVotedFor() {
        return votedFor;
    }

    /**
     * Clears all stored data (useful between tests).
     */
    public void reset() {
        currentTerm = 0;
        votedFor = Optional.empty();
        log.clear();
        failOnSync = false;
        failOnAppend = false;
        failOnMetadataUpdate = false;
    }

    /**
     * Returns whether the storage is currently open.
     *
     * @return true if open, false otherwise
     */
    public boolean isOpen() {
        return opened && !closed;
    }
}
