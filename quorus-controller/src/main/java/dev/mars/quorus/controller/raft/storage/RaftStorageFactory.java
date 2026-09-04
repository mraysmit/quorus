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
import io.vertx.core.Vertx;

import java.nio.file.Path;

/** Creates the sole supported storage implementation: the external raftlog-core adapter. */
public final class RaftStorageFactory {
    private RaftStorageFactory() {
    }

    /**
     * Opens the library-backed WAL. Removed backend names fail explicitly; they must never
     * silently select a different on-disk format or a non-durable implementation.
     */
    public static Future<RaftStorage> create(Vertx vertx, String storageType,
                                           Path storagePath, boolean fsync) {
        if (storageType != null && !storageType.isBlank()
                && !storageType.trim().equalsIgnoreCase("raftlog")
                && !storageType.trim().equalsIgnoreCase("wal")) {
            throw new IllegalArgumentException(
                    "Unsupported Raft storage type: '" + storageType + "'. Only raftlog is supported");
        }
        var config = dev.mars.raftlog.storage.RaftStorageConfig.builder()
                .dataDir(storagePath)
                .syncEnabled(fsync)
                .build();
        RaftLogStorageAdapter storage = new RaftLogStorageAdapter(vertx, config);
        return storage.open(storagePath).map(v -> (RaftStorage) storage);
    }
}
