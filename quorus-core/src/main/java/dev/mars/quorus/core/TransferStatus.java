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


/**
 * Represents the current status of a file transfer operation.
 * This enum tracks the lifecycle of a transfer from initiation to completion.
 */
public enum TransferStatus {
    /**
     * Transfer has been created but not yet started
     */
    PENDING,
    
    /**
     * Transfer is currently in progress
     */
    IN_PROGRESS,
    
    /**
     * Transfer completed successfully
     */
    COMPLETED,
    
    /**
     * Transfer failed due to an error
     */
    FAILED,
    
    /**
     * Transfer was cancelled by user or system
     */
    CANCELLED,
    
    /**
     * Transfer is paused and can be resumed
     */
    PAUSED;
    
    /**
     * Check if the transfer is in a terminal state (completed, failed, or cancelled)
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
    
    /**
     * Check if the transfer is currently active (in progress or paused)
     */
    public boolean isActive() {
        return this == IN_PROGRESS || this == PAUSED;
    }
    
    /**
     * Check if the transfer can be resumed (paused or failed)
     */
    public boolean canResume() {
        return this == PAUSED || this == FAILED;
    }
}
