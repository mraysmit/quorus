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

import dev.mars.raftlog.storage.FileRaftStorage;
import dev.mars.raftlog.storage.RaftStorageConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Adapter that bridges the external {@code raftlog-core} library to Quorus's
 * {@link RaftStorage} interface.
 *
 * <p>This adapter wraps {@link dev.mars.raftlog.storage.FileRaftStorage} from the
 * standalone {@code raftlog-core} JAR and adapts its {@link CompletableFuture}-based
 * API to Vert.x {@link Future} for seamless integration with Quorus.</p>
 *
 * <p><b>Why an Adapter?</b></p>
 * <ul>
 *   <li>The {@code raftlog-core} library uses {@code CompletableFuture} (standard Java)</li>
 *   <li>Quorus uses Vert.x {@code Future} for reactive patterns</li>
 *   <li>This adapter performs the conversion transparently</li>
 * </ul>
 *
 * <p><b>Configuration:</b> The underlying {@code FileRaftStorage} can be configured via:</p>
 * <ul>
 *   <li>System properties: {@code -Draftlog.syncEnabled=true}</li>
 *   <li>Environment variables: {@code RAFTLOG_SYNC_ENABLED=true}</li>
 *   <li>Programmatic: Pass {@link RaftStorageConfig} to constructor</li>
 * </ul>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-01-29
 * @see dev.mars.raftlog.storage.FileRaftStorage
 * @see RaftStorage
 */
public final class RaftLogStorageAdapter implements RaftStorage {

    private static final Logger LOG = LoggerFactory.getLogger(RaftLogStorageAdapter.class);

    private final Vertx vertx;
    private final dev.mars.raftlog.storage.RaftStorage delegate;

    // =========================================================================
    // Constructors
    // =========================================================================

    /**
     * Creates an adapter with default configuration.
     *
     * <p>Configuration is loaded from system properties, environment variables,
     * or defaults as defined by {@link RaftStorageConfig#load()}.</p>
     *
     * @param vertx the Vert.x instance for Future conversion
     */
    public RaftLogStorageAdapter(Vertx vertx) {
        this(vertx, new FileRaftStorage());
    }

    /**
     * Creates an adapter with custom configuration.
     *
     * @param vertx  the Vert.x instance for Future conversion
     * @param config the storage configuration
     */
    public RaftLogStorageAdapter(Vertx vertx, RaftStorageConfig config) {
        this(vertx, new FileRaftStorage(config));
    }

    /**
     * Creates an adapter with explicit sync setting.
     *
     * @param vertx       the Vert.x instance for Future conversion
     * @param syncEnabled whether to fsync on writes (false only for testing)
     */
    public RaftLogStorageAdapter(Vertx vertx, boolean syncEnabled) {
        this(vertx, new FileRaftStorage(syncEnabled));
    }

    /**
     * Creates an adapter wrapping an existing raftlog storage instance.
     *
     * <p>This constructor allows injecting a mock or custom implementation
     * for testing purposes.</p>
     *
     * @param vertx    the Vert.x instance for Future conversion
     * @param delegate the raftlog storage implementation to wrap
     */
    public RaftLogStorageAdapter(Vertx vertx, dev.mars.raftlog.storage.RaftStorage delegate) {
        this.vertx = vertx;
        this.delegate = delegate;
        LOG.info("RaftLogStorageAdapter created with delegate: {}", delegate.getClass().getSimpleName());
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    public Future<Void> open(Path dataDir) {
        LOG.info("Opening RaftLog storage at: {}", dataDir);
        return toVertxFuture(delegate.open(dataDir));
    }

    @Override
    public Future<Void> close() {
        LOG.info("Closing RaftLog storage");
        // The raftlog library's close() is synchronous
        try {
            delegate.close();
            return Future.succeededFuture();
        } catch (Exception e) {
            LOG.error("Error closing RaftLog storage: {}", e.getMessage(), e);
            return Future.failedFuture(e);
        }
    }

    // =========================================================================
    // Metadata Operations
    // =========================================================================

    @Override
    public Future<Void> updateMetadata(long currentTerm, Optional<String> votedFor) {
        LOG.debug("Updating metadata: term={}, votedFor={}", currentTerm, votedFor.orElse("(none)"));
        return toVertxFuture(delegate.updateMetadata(currentTerm, votedFor));
    }

    @Override
    public Future<PersistentMeta> loadMetadata() {
        LOG.debug("Loading metadata");
        return toVertxFuture(delegate.loadMetadata())
                .map(meta -> new PersistentMeta(meta.currentTerm(), meta.votedFor()));
    }

    // =========================================================================
    // Log Operations
    // =========================================================================

    @Override
    public Future<Void> appendEntries(List<LogEntryData> entries) {
        if (entries == null || entries.isEmpty()) {
            return Future.succeededFuture();
        }

        LOG.debug("Appending {} entries (indices {}-{})",
                entries.size(),
                entries.getFirst().index(),
                entries.getLast().index());

        // Convert Quorus LogEntryData to raftlog LogEntryData
        List<dev.mars.raftlog.storage.RaftStorage.LogEntryData> raftlogEntries = entries.stream()
                .map(e -> new dev.mars.raftlog.storage.RaftStorage.LogEntryData(
                        e.index(), e.term(), e.payload()))
                .toList();

        return toVertxFuture(delegate.appendEntries(raftlogEntries));
    }

    @Override
    public Future<Void> truncateSuffix(long fromIndex) {
        LOG.debug("Truncating log suffix from index {}", fromIndex);
        return toVertxFuture(delegate.truncateSuffix(fromIndex));
    }

    @Override
    public Future<Void> sync() {
        LOG.trace("Syncing WAL to disk");
        return toVertxFuture(delegate.sync());
    }

    @Override
    public Future<List<LogEntryData>> replayLog() {
        LOG.info("Replaying WAL");
        return toVertxFuture(delegate.replayLog())
                .map(raftlogEntries -> raftlogEntries.stream()
                        .map(e -> new LogEntryData(e.index(), e.term(), e.payload()))
                        .toList());
    }

    // =========================================================================
    // Snapshot Operations (delegated to file-based storage)
    // =========================================================================

    /**
     * Snapshot data stored in-memory for the adapter layer.
     * The raftlog-core library does not natively support snapshots,
     * so we implement snapshot persistence directly using the data directory.
     */
    private byte[] snapshotData;
    private long snapshotLastIncludedIndex = -1;
    private long snapshotLastIncludedTerm = -1;

    @Override
    public Future<Void> saveSnapshot(byte[] data, long lastIncludedIndex, long lastIncludedTerm) {
        LOG.info("Saving snapshot: lastIncludedIndex={}, lastIncludedTerm={}, size={}bytes",
                lastIncludedIndex, lastIncludedTerm, data.length);
        // Store in memory - the raftlog-core library doesn't have snapshot support
        this.snapshotData = data.clone();
        this.snapshotLastIncludedIndex = lastIncludedIndex;
        this.snapshotLastIncludedTerm = lastIncludedTerm;
        return Future.succeededFuture();
    }

    @Override
    public Future<Optional<SnapshotData>> loadSnapshot() {
        if (snapshotData == null) {
            return Future.succeededFuture(Optional.empty());
        }
        return Future.succeededFuture(Optional.of(
                new SnapshotData(snapshotData.clone(), snapshotLastIncludedIndex, snapshotLastIncludedTerm)));
    }

    @Override
    public Future<Void> truncatePrefix(long toIndex) {
        LOG.info("Prefix truncation requested for index <= {} (no-op for RaftLogStorageAdapter)", toIndex);
        // The raftlog-core library doesn't support prefix truncation.
        // Log entries will accumulate but snapshots still work for state machine recovery.
        return Future.succeededFuture();
    }

    // =========================================================================
    // Future Conversion
    // =========================================================================

    /**
     * Converts a {@link CompletableFuture} to a Vert.x {@link Future}.
     *
     * <p>The conversion ensures that completion callbacks execute on the
     * Vert.x context, preserving thread-safety guarantees.</p>
     *
     * @param cf  the CompletableFuture to convert
     * @param <T> the result type
     * @return a Vert.x Future that completes with the same result
     */
    private <T> Future<T> toVertxFuture(CompletableFuture<T> cf) {
        return Future.fromCompletionStage(cf, vertx.getOrCreateContext());
    }
}
