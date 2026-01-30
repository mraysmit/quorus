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

import dev.mars.quorus.controller.raft.storage.file.FileRaftStorage;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Factory for creating {@link RaftStorage} instances based on configuration.
 *
 * <p>This factory supports the following storage backends:</p>
 * <ul>
 *   <li><b>raftlog</b> (default) - Production WAL from raftlog-core library</li>
 *   <li><b>file</b> - Custom WAL implementation (Quorus-internal)</li>
 *   <li><b>rocksdb</b> - High-performance RocksDB adapter (requires rocksdbjni on classpath)</li>
 *   <li><b>memory</b> - In-memory storage for testing (not durable)</li>
 * </ul>
 *
 * <p><b>Configuration:</b> The storage type is determined by the system property
 * or environment variable {@code quorus.raft.storage.type}.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-01-29
 */
public final class RaftStorageFactory {

    private static final Logger LOG = LoggerFactory.getLogger(RaftStorageFactory.class);

    /**
     * Supported storage backend types.
     */
    public enum StorageType {
        /** Production WAL from raftlog-core library (default) */
        RAFTLOG,
        /** Custom WAL implementation (Quorus-internal) */
        FILE,
        /** RocksDB key-value store (high performance) */
        ROCKSDB,
        /** In-memory storage (testing only, not durable) */
        MEMORY
    }

    private RaftStorageFactory() {
        // Utility class
    }

    /**
     * Creates and opens a {@link RaftStorage} instance based on configuration.
     *
     * <p>This is the preferred factory method for production use. It:</p>
     * <ul>
     *   <li>Creates a dedicated worker executor for serialized I/O</li>
     *   <li>Initializes the storage with the specified path and fsync setting</li>
     *   <li>Opens the storage (replays WAL, loads metadata)</li>
     * </ul>
     *
     * @param vertx       the Vert.x instance
     * @param storageType the storage type ("file", "rocksdb", or "memory")
     * @param storagePath the path for persistent storage (ignored for memory)
     * @param fsync       whether to fsync after writes (ignored for memory)
     * @return a Future completing with the opened RaftStorage
     */
    public static Future<RaftStorage> create(Vertx vertx, String storageType, 
                                              Path storagePath, boolean fsync) {
        StorageType type = parseStorageType(storageType);
        
        LOG.info("Creating RaftStorage: type={}, path={}, fsync={}", type, storagePath, fsync);

        return switch (type) {
            case RAFTLOG -> {
                LOG.info("Using RaftLogStorageAdapter (raftlog-core library)");
                var config = dev.mars.raftlog.storage.RaftStorageConfig.builder()
                        .dataDir(storagePath)
                        .syncEnabled(fsync)
                        .build();
                RaftLogStorageAdapter storage = new RaftLogStorageAdapter(vertx, config);
                yield storage.open(storagePath).map(v -> (RaftStorage) storage);
            }
            case FILE -> {
                // Create a dedicated single-threaded executor for serialized WAL I/O
                WorkerExecutor executor = vertx.createSharedWorkerExecutor(
                        "raft-wal-executor", 1, 60_000_000_000L); // 60s max exec time
                FileRaftStorage storage = new FileRaftStorage(vertx, executor, storagePath, fsync);
                yield storage.open(storagePath).map(v -> (RaftStorage) storage);
            }
            case ROCKSDB -> {
                validateRocksDbAvailable();
                WorkerExecutor executor = vertx.createSharedWorkerExecutor(
                        "raft-rocksdb-executor", 1, 60_000_000_000L);
                yield createRocksDbStorageAsync(vertx, executor, storagePath, fsync);
            }
            case MEMORY -> {
                LOG.warn("Using InMemoryRaftStorage - DATA WILL NOT SURVIVE RESTART!");
                InMemoryRaftStorage storage = new InMemoryRaftStorage();
                yield storage.open(storagePath).map(v -> (RaftStorage) storage);
            }
        };
    }

    /**
     * Creates a {@link RaftStorage} instance based on configuration.
     *
     * <p>The storage type is determined by:</p>
     * <ol>
     *   <li>System property {@code quorus.raft.storage.type}</li>
     *   <li>Environment variable {@code QUORUS_RAFT_STORAGE_TYPE}</li>
     *   <li>Default: {@code file}</li>
     * </ol>
     *
     * @param vertx    the Vert.x instance
     * @param executor the worker executor for blocking I/O operations
     * @return the configured RaftStorage implementation
     * @throws IllegalArgumentException if the storage type is unknown
     * @throws IllegalStateException if RocksDB is requested but not on classpath
     */
    public static RaftStorage create(Vertx vertx, WorkerExecutor executor) {
        String storageType = getConfiguredStorageType();
        return create(vertx, executor, storageType);
    }

    /**
     * Creates a {@link RaftStorage} instance of the specified type.
     *
     * @param vertx       the Vert.x instance
     * @param executor    the worker executor for blocking I/O operations
     * @param storageType the storage type ("file", "rocksdb", or "memory")
     * @return the requested RaftStorage implementation
     * @throws IllegalArgumentException if the storage type is unknown
     * @throws IllegalStateException if RocksDB is requested but not on classpath
     */
    public static RaftStorage create(Vertx vertx, WorkerExecutor executor, String storageType) {
        StorageType type = parseStorageType(storageType);
        return create(vertx, executor, type);
    }

    /**
     * Creates a {@link RaftStorage} instance of the specified type.
     *
     * @param vertx    the Vert.x instance
     * @param executor the worker executor for blocking I/O operations
     * @param type     the storage type enum
     * @return the requested RaftStorage implementation
     * @throws IllegalStateException if RocksDB is requested but not on classpath
     */
    public static RaftStorage create(Vertx vertx, WorkerExecutor executor, StorageType type) {
        LOG.info("Creating RaftStorage: type={}", type);

        return switch (type) {
            case RAFTLOG -> {
                LOG.info("Using RaftLogStorageAdapter (raftlog-core library)");
                yield new RaftLogStorageAdapter(vertx);
            }
            case FILE -> {
                LOG.info("Using FileRaftStorage (custom WAL, zero dependencies)");
                yield new FileRaftStorage(vertx, executor);
            }
            case ROCKSDB -> {
                validateRocksDbAvailable();
                LOG.info("Using RocksDbRaftStorage (high-performance key-value store)");
                yield createRocksDbStorage(vertx, executor);
            }
            case MEMORY -> {
                LOG.warn("Using InMemoryRaftStorage - DATA WILL NOT SURVIVE RESTART!");
                yield new InMemoryRaftStorage();
            }
        };
    }

    /**
     * Creates an in-memory storage for testing.
     *
     * <p>This is a convenience method that doesn't require Vert.x or an executor.</p>
     *
     * @return a new InMemoryRaftStorage instance
     */
    public static InMemoryRaftStorage createInMemory() {
        return new InMemoryRaftStorage();
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    private static String getConfiguredStorageType() {
        // Check system property first
        String type = System.getProperty("quorus.raft.storage.type");
        if (type != null && !type.isBlank()) {
            return type.trim();
        }

        // Check environment variable
        type = System.getenv("QUORUS_RAFT_STORAGE_TYPE");
        if (type != null && !type.isBlank()) {
            return type.trim();
        }

        // Default to file (raftlog-core requires local build)
        return "file";
    }

    private static StorageType parseStorageType(String type) {
        if (type == null || type.isBlank()) {
            return StorageType.FILE;
        }

        return switch (type.toLowerCase().trim()) {
            case "raftlog", "wal" -> StorageType.RAFTLOG;
            case "file" -> StorageType.FILE;
            case "rocksdb", "rocks" -> StorageType.ROCKSDB;
            case "memory", "inmemory", "in-memory", "test" -> StorageType.MEMORY;
            default -> throw new IllegalArgumentException(
                    "Unknown storage type: '" + type + "'. Valid options: raftlog, file, rocksdb, memory");
        };
    }

    private static void validateRocksDbAvailable() {
        try {
            Class.forName("org.rocksdb.RocksDB");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "RocksDB storage requested but org.rocksdb:rocksdbjni is not on classpath. " +
                    "Add the dependency to your pom.xml or use storage.type=file instead.\n" +
                    "Maven dependency:\n" +
                    "  <dependency>\n" +
                    "    <groupId>org.rocksdb</groupId>\n" +
                    "    <artifactId>rocksdbjni</artifactId>\n" +
                    "    <version>9.0.0</version>\n" +
                    "  </dependency>");
        }
    }

    private static RaftStorage createRocksDbStorage(Vertx vertx, WorkerExecutor executor) {
        // Use reflection to avoid compile-time dependency on RocksDB
        try {
            Class<?> clazz = Class.forName(
                    "dev.mars.quorus.controller.raft.storage.rocksdb.RocksDbRaftStorage");
            return (RaftStorage) clazz
                    .getConstructor(Vertx.class, WorkerExecutor.class)
                    .newInstance(vertx, executor);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "RocksDbRaftStorage class not found. Ensure quorus-controller is compiled " +
                    "with RocksDB support.");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to instantiate RocksDbRaftStorage", e);
        }
    }

    private static Future<RaftStorage> createRocksDbStorageAsync(Vertx vertx, WorkerExecutor executor,
                                                                  Path storagePath, boolean fsync) {
        // Use reflection to avoid compile-time dependency on RocksDB
        try {
            Class<?> clazz = Class.forName(
                    "dev.mars.quorus.controller.raft.storage.rocksdb.RocksDbRaftStorage");
            RaftStorage storage = (RaftStorage) clazz
                    .getConstructor(Vertx.class, WorkerExecutor.class, Path.class, boolean.class)
                    .newInstance(vertx, executor, storagePath, fsync);
            return storage.open(storagePath).map(v -> storage);
        } catch (ClassNotFoundException e) {
            return Future.failedFuture(new IllegalStateException(
                    "RocksDbRaftStorage class not found. Ensure quorus-controller is compiled " +
                    "with RocksDB support."));
        } catch (Exception e) {
            return Future.failedFuture(new IllegalStateException(
                    "Failed to instantiate RocksDbRaftStorage", e));
        }
    }
}
