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
 * Description for WorkflowStatus
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-18
 */

public enum WorkflowStatus {
    
    PENDING,
    
    RUNNING,
    
    PAUSED,
    
    COMPLETED,
    
    FAILED,
    
    CANCELLED;
    
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
    
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
