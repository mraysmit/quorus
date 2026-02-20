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

package dev.mars.quorus.controller.state;

import java.util.Objects;

/**
 * Sealed interface for system metadata key-value operations.
 *
 * <p>Each permitted record carries exactly the fields it needs — no nullable
 * fields, no type tags. The type IS the class.
 *
 * <ul>
 *   <li>{@link Set} — upsert a metadata key-value pair</li>
 *   <li>{@link Delete} — remove a metadata key</li>
 * </ul>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2025-08-20
 */
public sealed interface SystemMetadataCommand extends RaftCommand
        permits SystemMetadataCommand.Set, SystemMetadataCommand.Delete {

    /** Common accessor — every variant identifies itself by key. */
    String key();

    // ── Records ─────────────────────────────────────────────────

    /**
     * Upsert a metadata key-value pair.
     */
    record Set(String key, String value) implements SystemMetadataCommand {
        private static final long serialVersionUID = 1L;

        public Set {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * Remove a metadata key.
     */
    record Delete(String key) implements SystemMetadataCommand {
        private static final long serialVersionUID = 1L;

        public Delete {
            Objects.requireNonNull(key, "key");
        }
    }

    // ── Factory methods (preserves existing API) ────────────────

    static SystemMetadataCommand set(String key, String value) {
        return new Set(key, value);
    }

    static SystemMetadataCommand delete(String key) {
        return new Delete(key);
    }
}
