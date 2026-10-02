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
 * Engine interface for workflow execution.
 *
 * <p>Blocking (RT-04): {@code execute}, {@code dryRun} and {@code virtualRun} run the workflow on the
 * calling thread and return the finished execution. A workflow that fails, including by exceeding its
 * timeout, is returned with status {@code FAILED}; nothing is thrown for it.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2025-08-18
 */
public interface WorkflowEngine {
    
    /**
     * Runs the workflow's transfers.
     *
     * @throws InterruptedException  if the calling thread is interrupted; running transfers are stopped
     * @throws IllegalStateException if the engine has been shut down
     */
    WorkflowExecution execute(WorkflowDefinition definition, ExecutionContext context) throws InterruptedException;
    
    /** Validates the workflow and plans it without starting any transfer. */
    WorkflowExecution dryRun(WorkflowDefinition definition, ExecutionContext context) throws InterruptedException;
    
    /** Simulates the workflow, taking time per transfer, without starting any transfer. */
    WorkflowExecution virtualRun(WorkflowDefinition definition, ExecutionContext context) throws InterruptedException;
    
    WorkflowStatus getStatus(String executionId);
    
    /**
     * Stops a running execution: its transfers are interrupted and the call running it returns the
     * execution with status {@code CANCELLED}.
     *
     * @return {@code false} if no execution with this id is running
     */
    boolean cancel(String executionId);
    
    /**
     * Shuts down the workflow engine and cleans up resources.
     */
    void shutdown();
}
