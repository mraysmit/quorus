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

package dev.mars.quorus.workflow;

/**
 * Enumeration of workflow execution statuses.
 */
public enum WorkflowStatus {
    
    /**
     * Workflow is pending execution.
     */
    PENDING,
    
    /**
     * Workflow is currently running.
     */
    RUNNING,
    
    /**
     * Workflow execution has been paused.
     */
    PAUSED,
    
    /**
     * Workflow has completed successfully.
     */
    COMPLETED,
    
    /**
     * Workflow execution has failed.
     */
    FAILED,
    
    /**
     * Workflow execution has been cancelled.
     */
    CANCELLED;
    
    /**
     * Checks if the status represents a terminal state.
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
    
    /**
     * Checks if the status represents an active state.
     */
    public boolean isActive() {
        return this == RUNNING || this == PAUSED;
    }
    
    /**
     * Checks if the status represents a successful completion.
     */
    public boolean isSuccessful() {
        return this == COMPLETED;
    }
}
