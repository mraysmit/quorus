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

import java.util.concurrent.CompletableFuture;

/**
 * Interface for executing workflows with different execution modes.
 * Supports normal execution, dry run, and virtual run modes.
 */
public interface WorkflowEngine {
    
    /**
     * Executes a workflow definition.
     * 
     * @param definition the workflow definition to execute
     * @param context the execution context
     * @return future containing the workflow execution result
     */
    CompletableFuture<WorkflowExecution> execute(WorkflowDefinition definition, ExecutionContext context);
    
    /**
     * Performs a dry run of the workflow without executing transfers.
     * Validates the workflow and shows what would be executed.
     * 
     * @param definition the workflow definition to validate
     * @param context the execution context
     * @return future containing the dry run result
     */
    CompletableFuture<WorkflowExecution> dryRun(WorkflowDefinition definition, ExecutionContext context);
    
    /**
     * Performs a virtual run of the workflow with simulated execution.
     * Executes the workflow logic but uses mock transfers.
     * 
     * @param definition the workflow definition to simulate
     * @param context the execution context
     * @return future containing the virtual run result
     */
    CompletableFuture<WorkflowExecution> virtualRun(WorkflowDefinition definition, ExecutionContext context);
    
    /**
     * Gets the status of a running workflow execution.
     * 
     * @param executionId the execution ID
     * @return the current workflow status
     */
    WorkflowStatus getStatus(String executionId);
    
    /**
     * Pauses a running workflow execution.
     * 
     * @param executionId the execution ID
     * @return true if the workflow was paused successfully
     */
    boolean pause(String executionId);
    
    /**
     * Resumes a paused workflow execution.
     * 
     * @param executionId the execution ID
     * @return true if the workflow was resumed successfully
     */
    boolean resume(String executionId);
    
    /**
     * Cancels a running workflow execution.
     * 
     * @param executionId the execution ID
     * @return true if the workflow was cancelled successfully
     */
    boolean cancel(String executionId);
    
    /**
     * Shuts down the workflow engine and cleans up resources.
     */
    void shutdown();
}
