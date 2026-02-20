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

package dev.mars.quorus.core.exceptions;

/**
 * Thrown when an invalid state transition is attempted on a status enum.
 *
 * <p>This exception captures the current state, the requested target state,
 * and the set of valid transitions from the current state, enabling
 * informative error messages and HTTP 409 Conflict responses.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-20
 * @version 1.0
 */
public class InvalidTransitionException extends QuorusException {

    private final String entityId;
    private final Enum<?> currentState;
    private final Enum<?> requestedState;
    private final Enum<?>[] validTransitions;

    /**
     * Constructs an InvalidTransitionException with full context.
     *
     * @param entityId         the identifier of the entity whose transition was rejected
     * @param currentState     the current state of the entity
     * @param requestedState   the state that was requested but is not valid
     * @param validTransitions the set of states that are valid targets from the current state
     */
    public InvalidTransitionException(String entityId, Enum<?> currentState,
                                      Enum<?> requestedState, Enum<?>[] validTransitions) {
        super(String.format("Invalid transition for '%s': %s → %s. Valid targets: %s",
                entityId, currentState, requestedState, formatTransitions(validTransitions)));
        this.entityId = entityId;
        this.currentState = currentState;
        this.requestedState = requestedState;
        this.validTransitions = validTransitions;
    }

    /**
     * Constructs an InvalidTransitionException without an entity ID.
     *
     * @param currentState     the current state
     * @param requestedState   the state that was requested but is not valid
     * @param validTransitions the set of states that are valid targets from the current state
     */
    public InvalidTransitionException(Enum<?> currentState, Enum<?> requestedState,
                                      Enum<?>[] validTransitions) {
        this("unknown", currentState, requestedState, validTransitions);
    }

    public String getEntityId() {
        return entityId;
    }

    public Enum<?> getCurrentState() {
        return currentState;
    }

    public Enum<?> getRequestedState() {
        return requestedState;
    }

    public Enum<?>[] getValidTransitions() {
        return validTransitions;
    }

    private static String formatTransitions(Enum<?>[] transitions) {
        if (transitions == null || transitions.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < transitions.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(transitions[i].name());
        }
        sb.append("]");
        return sb.toString();
    }
}
