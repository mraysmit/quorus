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
 * Algebraic data type for the result of applying a {@link RaftCommand} to the state machine.
 *
 * <p>Replaces the previous {@code Object} return (which used {@code null} to signal
 * "entity not found") with an explicit sum type. Callers pattern-match on the three
 * permitted subtypes instead of null-checking or {@code instanceof}-casting.
 *
 * <h3>Permitted subtypes</h3>
 * <ul>
 *   <li>{@link Success} — command applied; carries the resulting entity</li>
 *   <li>{@link NotFound} — the target entity does not exist in the state store</li>
 *   <li>{@link NoOp} — command was a no-op (e.g., null command, delete of absent metadata)</li>
 * </ul>
 *
 * @param <T> the type of entity produced on success
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-10-28
 */
public sealed interface CommandResult<T>
        permits CommandResult.Success,
                CommandResult.NotFound,
                CommandResult.NoOp,
                CommandResult.CasMismatch {

    /**
     * The command was applied successfully.
     *
     * @param entity the resulting entity (may be null for metadata deletes that return the old value)
     * @param <T>    entity type
     */
    record Success<T>(T entity) implements CommandResult<T> {
    }

    /**
     * The target entity was not found in the state store.
     *
     * @param id         the entity identifier that was looked up
     * @param entityType a human-readable entity type name (e.g. "TransferJob", "Agent")
     * @param <T>        entity type (phantom — no entity is available)
     */
    record NotFound<T>(String id, String entityType) implements CommandResult<T> {
        public NotFound {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(entityType, "entityType");
        }
    }

    /**
     * The command was a no-op (e.g., null command input).
     *
     * @param <T> entity type (phantom)
     */
    record NoOp<T>() implements CommandResult<T> {
    }

    /**
     * Compare-and-swap mismatch: the entity's current state did not match
     * the expected state carried by the command. The command was deterministically
     * rejected on all Raft nodes.
     *
     * <p>The {@code entity} field contains the entity in its <em>current</em> state,
     * allowing the caller to inspect the actual status and optionally retry.
     *
     * @param entity the entity in its current (mismatched) state
     * @param <T>    entity type
     */
    record CasMismatch<T>(T entity) implements CommandResult<T> {
        public CasMismatch {
            Objects.requireNonNull(entity, "entity");
        }
    }
}
