package dev.mars.quorus.core;

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


public enum TransferStatus {
    PENDING,

    IN_PROGRESS,

    COMPLETED,

    FAILED,

    CANCELLED,

    PAUSED;
    
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public boolean isActive() {
        return this == IN_PROGRESS || this == PAUSED;
    }

    /**
     * Determines if the transfer can be resumed from its current state.
     *
     * <p>Resumable states allow the transfer to continue from where it left off,
     * either immediately (for paused transfers) or after addressing the underlying
     * issue (for failed transfers). This capability is essential for handling
     * network interruptions and implementing retry logic.</p>
     *
     * <p><strong>Resumable States:</strong></p>
     * <ul>
     *   <li>{@link #PAUSED} - Can resume immediately from current position</li>
     *   <li>{@link #FAILED} - Can retry after addressing the failure cause</li>
     * </ul>
     *
     * <p><strong>Resume Behavior:</strong></p>
     * <ul>
     *   <li><strong>PAUSED:</strong> Resume from exact byte position where paused</li>
     *   <li><strong>FAILED:</strong> May resume from last checkpoint or restart completely</li>
     * </ul>
     *
     * <p><strong>Protocol Support:</strong> Resume capability depends on the underlying
     * transfer protocol supporting range requests or partial transfers (HTTP Range,
     * FTP REST command, etc.).</p>
     *
     * @return {@code true} if the transfer can be resumed (PAUSED or FAILED),
     *         {@code false} if the transfer cannot be resumed from its current state
     *
     * @see #isActive()
     * @see #isTerminal()
     */
    public boolean canResume() {
        return this == PAUSED || this == FAILED;
    }
}
