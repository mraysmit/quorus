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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Storage Provider Interface (SPI) for Raft persistence.
 *
 * <p>This interface defines the contract for all Raft storage backends.
 * Implementations can range from a simple file-based WAL to high-performance
 * key-value stores like RocksDB.</p>
 *
 * <p><b>Durability Contract:</b> All implementations MUST guarantee:</p>
 * <ul>
 *   <li><b>Durability:</b> Data survives process crash after {@link #sync()} returns</li>
 *   <li><b>Atomicity:</b> Metadata updates are all-or-nothing</li>
 *   <li><b>Ordering:</b> Log entries maintain strict index ordering</li>
 * </ul>
 *
 * <p><b>Implementations:</b></p>
 * <ul>
 *   <li>{@code FileRaftStorage} - Custom WAL (zero dependencies, default)</li>
 *   <li>{@code RocksDbRaftStorage} - RocksDB adapter (high performance)</li>
 *   <li>{@code InMemoryRaftStorage} - In-memory storage (testing only)</li>
 * </ul>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-01-29
 * @see <a href="docs-design/design/QUORUS_RAFT_WAL_DESIGN.md">WAL Design Document</a>
 */
public interface RaftStorage {

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Opens the storage backend at the specified directory.
     *
     * <p>Creates the directory if it does not exist. This method is idempotent:
     * calling {@code open()} on an already-open storage is a no-op.</p>
     *
     * @param dataDir the directory for storage files (will be created if needed)
     * @return a Future that completes when the storage is ready for operations
     */
    Future<Void> open(Path dataDir);

    /**
     * Closes the storage backend and releases all resources.
     *
     * <p>Must be called before JVM shutdown to ensure data integrity.
     * After close, any further operations will fail.</p>
     *
     * @return a Future that completes when the storage is closed
     */
    Future<Void> close();

    // =========================================================================
    // Metadata Operations (Term & Vote)
    // =========================================================================

    /**
     * Atomically persists the current term and voted-for candidate.
     *
     * <p><b>Raft Safety (CRITICAL):</b> This method MUST ensure durability
     * (fsync equivalent) before the Future completes. The caller will grant
     * a vote or update term only after this Future succeeds.</p>
     *
     * <p>This is the "Persist-before-Grant" rule from the Raft paper.</p>
     *
     * @param currentTerm the current Raft term (must be non-negative)
     * @param votedFor    the candidate voted for in this term (empty if none)
     * @return a Future that completes after durable persistence
     */
    Future<Void> updateMetadata(long currentTerm, Optional<String> votedFor);

    /**
     * Loads persisted metadata on startup.
     *
     * <p>Returns default values (term=0, votedFor=empty) if no state exists.
     * This is called during node recovery before any RPCs are processed.</p>
     *
     * @return a Future containing the persisted metadata
     */
    Future<PersistentMeta> loadMetadata();

    /**
     * Immutable record for persisted Raft metadata.
     *
     * @param currentTerm the persisted Raft term
     * @param votedFor    the candidate voted for in the current term (empty if none)
     */
    record PersistentMeta(long currentTerm, Optional<String> votedFor) {
        /**
         * Returns empty metadata for fresh nodes.
         *
         * @return metadata with term=0 and no vote
         */
        public static PersistentMeta empty() {
            return new PersistentMeta(0L, Optional.empty());
        }
    }

    // =========================================================================
    // Log Operations
    // =========================================================================

    /**
     * Appends a batch of log entries to storage.
     *
     * <p><b>Note:</b> Entries are NOT guaranteed to be durable until {@link #sync()}
     * is called. This allows batching multiple appends before a single fsync,
     * improving throughput.</p>
     *
     * @param entries the entries to append (must have sequential indices)
     * @return a Future that completes when entries are written (but not synced)
     */
    Future<Void> appendEntries(List<LogEntryData> entries);

    /**
     * Deletes all log entries with index &gt;= fromIndex.
     *
     * <p>Used to resolve log conflicts when a follower diverges from the leader.
     * Per Raft: "If an existing entry conflicts with a new one (same index but
     * different terms), delete the existing entry and all that follow it."</p>
     *
     * <p><b>Note:</b> Truncation is NOT guaranteed to be durable until {@link #sync()}
     * is called.</p>
     *
     * @param fromIndex the first index to delete (inclusive)
     * @return a Future that completes when truncation is recorded
     */
    Future<Void> truncateSuffix(long fromIndex);

    /**
     * Forces all pending writes to durable storage.
     *
     * <p><b>THE DURABILITY BARRIER (CRITICAL):</b> This is the single most
     * important method for Raft correctness. After this Future completes
     * successfully, all prior appends and truncations are guaranteed to
     * survive a crash.</p>
     *
     * <p>The RaftNode MUST call this before acknowledging AppendEntries RPCs.
     * This enforces the "Persist-before-Response" rule.</p>
     *
     * @return a Future that completes after fsync (or equivalent)
     */
    Future<Void> sync();

    /**
     * Replays the entire log from storage on startup.
     *
     * <p>Returns entries in index order. For file-based storage, this involves
     * scanning the WAL and handling any truncation markers. For key-value stores,
     * this is a range scan.</p>
     *
     * <p>Any corrupt or incomplete records at the tail are silently discarded
     * (this handles "torn writes" from crashes during append).</p>
     *
     * @return a Future containing all valid log entries in index order
     */
    Future<List<LogEntryData>> replayLog();

    /**
     * Immutable record for a log entry.
     *
     * <p>This is the storage representation of a log entry. The payload is
     * an opaque byte array - the storage layer does not interpret its contents.</p>
     *
     * @param index   the log index (1-based, 0 is reserved for the sentinel entry)
     * @param term    the term when the entry was created
     * @param payload the serialized command (opaque to storage)
     */
    record LogEntryData(long index, long term, byte[] payload) {
        /**
         * Creates a log entry with validation.
         */
        public LogEntryData {
            if (index < 0) {
                throw new IllegalArgumentException("Index must be non-negative: " + index);
            }
            if (term < 0) {
                throw new IllegalArgumentException("Term must be non-negative: " + term);
            }
            // payload may be null for no-op entries
        }

        /**
         * Returns the payload length, or 0 if payload is null.
         *
         * @return payload length in bytes
         */
        public int payloadLength() {
            return payload != null ? payload.length : 0;
        }
    }
}
