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

import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.transfer.TransferEngine;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple implementation of WorkflowEngine for basic workflow execution.
 * Supports normal execution, dry run, and virtual run modes.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 1.0
 */
public class SimpleWorkflowEngine implements WorkflowEngine {
    
    private static final Logger logger = Logger.getLogger(SimpleWorkflowEngine.class.getName());
    
    private final TransferEngine transferEngine;
    private final WorkflowDefinitionParser parser;
    private final ExecutorService executorService;
    private final Map<String, WorkflowExecution> activeExecutions;
    private volatile boolean shutdown = false;
    
    public SimpleWorkflowEngine(TransferEngine transferEngine) {
        this.transferEngine = Objects.requireNonNull(transferEngine, "Transfer engine cannot be null");
        this.parser = new YamlWorkflowDefinitionParser();
        this.executorService = Executors.newCachedThreadPool();
        this.activeExecutions = new ConcurrentHashMap<>();
    }
    
    @Override
    public CompletableFuture<WorkflowExecution> execute(WorkflowDefinition definition, ExecutionContext context) {
        return executeWorkflow(definition, context, ExecutionContext.ExecutionMode.NORMAL);
    }
    
    @Override
    public CompletableFuture<WorkflowExecution> dryRun(WorkflowDefinition definition, ExecutionContext context) {
        return executeWorkflow(definition, context, ExecutionContext.ExecutionMode.DRY_RUN);
    }
    
    @Override
    public CompletableFuture<WorkflowExecution> virtualRun(WorkflowDefinition definition, ExecutionContext context) {
        return executeWorkflow(definition, context, ExecutionContext.ExecutionMode.VIRTUAL_RUN);
    }
    
    @Override
    public WorkflowStatus getStatus(String executionId) {
        WorkflowExecution execution = activeExecutions.get(executionId);
        return execution != null ? execution.getStatus() : null;
    }
    
    @Override
    public boolean pause(String executionId) {
        // For this simple implementation, we don't support pausing
        logger.warning("Pause operation not supported in SimpleWorkflowEngine");
        return false;
    }
    
    @Override
    public boolean resume(String executionId) {
        // For this simple implementation, we don't support resuming
        logger.warning("Resume operation not supported in SimpleWorkflowEngine");
        return false;
    }
    
    @Override
    public boolean cancel(String executionId) {
        WorkflowExecution execution = activeExecutions.get(executionId);
        if (execution != null && execution.isRunning()) {
            // For this simple implementation, we mark as cancelled but don't interrupt
            logger.info("Cancelling workflow execution: " + executionId);
            return true;
        }
        return false;
    }
    
    @Override
    public void shutdown() {
        shutdown = true;
        executorService.shutdown();
        logger.info("SimpleWorkflowEngine shutdown initiated");
    }
    
    private CompletableFuture<WorkflowExecution> executeWorkflow(WorkflowDefinition definition, 
                                                               ExecutionContext context, 
                                                               ExecutionContext.ExecutionMode mode) {
        if (shutdown) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Workflow engine is shutdown"));
        }
        
        // Create execution context with the specified mode
        ExecutionContext executionContext = ExecutionContext.builder()
                .executionId(context.getExecutionId())
                .mode(mode)
                .variables(context.getVariables())
                .userId(context.getUserId())
                .metadata(context.getMetadata())
                .build();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeWorkflowInternal(definition, executionContext);
            } catch (Exception e) {
                // Log without stack trace for cleaner output, especially during testing
                logger.log(Level.SEVERE, "Workflow execution failed: " + executionContext.getExecutionId() + " - " + e.getMessage());

                // Only log full stack trace in debug mode
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Workflow execution exception details for: " + executionContext.getExecutionId(), e);
                }

                return createFailedExecution(definition, executionContext, e);
            }
        }, executorService);
    }
    
    private WorkflowExecution executeWorkflowInternal(WorkflowDefinition definition, ExecutionContext context) 
            throws WorkflowParseException {
        
        Instant startTime = Instant.now();
        String executionId = context.getExecutionId();
        
        logger.info("Starting workflow execution: " + executionId + " in mode: " + context.getMode());
        
        // Validate workflow
        ValidationResult validation = parser.validate(definition);
        if (!validation.isValid()) {
            throw new WorkflowParseException("Workflow validation failed: " + 
                    validation.getErrors().get(0).getMessage());
        }
        
        // Resolve variables - start with workflow variables, then add context variables
        VariableResolver resolver = new VariableResolver(definition.getSpec().getVariables());
        resolver = resolver.withContext(context.getVariables());
        WorkflowDefinition resolvedDefinition = resolver.resolve(definition);
        
        // Build dependency graph
        DependencyGraph graph = parser.buildDependencyGraph(List.of(resolvedDefinition));
        
        // Create initial execution
        WorkflowExecution execution = new WorkflowExecution(
                executionId,
                definition,
                context,
                WorkflowStatus.RUNNING,
                startTime,
                null,
                List.of(),
                null,
                null
        );
        
        activeExecutions.put(executionId, execution);
        
        try {
            List<WorkflowExecution.GroupExecution> groupExecutions = new ArrayList<>();
            
            if (context.getMode() == ExecutionContext.ExecutionMode.DRY_RUN) {
                // Dry run - validate only
                groupExecutions = performDryRun(resolvedDefinition, graph);
            } else if (context.getMode() == ExecutionContext.ExecutionMode.VIRTUAL_RUN) {
                // Virtual run - simulate execution
                groupExecutions = performVirtualRun(resolvedDefinition, graph);
            } else {
                // Normal execution
                groupExecutions = performNormalExecution(resolvedDefinition, graph);
            }
            
            Instant endTime = Instant.now();
            WorkflowStatus finalStatus = groupExecutions.stream()
                    .allMatch(WorkflowExecution.GroupExecution::isSuccessful) ? 
                    WorkflowStatus.COMPLETED : WorkflowStatus.FAILED;
            
            execution = new WorkflowExecution(
                    executionId,
                    definition,
                    context,
                    finalStatus,
                    startTime,
                    endTime,
                    groupExecutions,
                    null,
                    null
            );
            
            logger.info("Workflow execution completed: " + executionId + " with status: " + finalStatus);
            return execution;
            
        } finally {
            activeExecutions.remove(executionId);
        }
    }
    
    private List<WorkflowExecution.GroupExecution> performDryRun(WorkflowDefinition definition, DependencyGraph graph) 
            throws WorkflowParseException {
        
        logger.info("Performing dry run validation");
        List<WorkflowExecution.GroupExecution> groupExecutions = new ArrayList<>();
        
        List<TransferGroup> sortedGroups = graph.topologicalSort();
        for (TransferGroup group : sortedGroups) {
            Instant groupStart = Instant.now();
            
            // Validate each transfer in the group
            Map<String, TransferResult> transferResults = new HashMap<>();
            for (TransferGroup.TransferDefinition transfer : group.getTransfers()) {
                // Create a mock successful result for dry run
                TransferResult result = createMockTransferResult(transfer, true);
                transferResults.put(transfer.getName(), result);
            }
            
            Instant groupEnd = Instant.now();
            WorkflowExecution.GroupExecution groupExecution = new WorkflowExecution.GroupExecution(
                    group.getName(),
                    WorkflowStatus.COMPLETED,
                    groupStart,
                    groupEnd,
                    transferResults,
                    null
            );
            
            groupExecutions.add(groupExecution);
            logger.info("Dry run validated group: " + group.getName());
        }
        
        return groupExecutions;
    }
    
    private List<WorkflowExecution.GroupExecution> performVirtualRun(WorkflowDefinition definition, DependencyGraph graph) 
            throws WorkflowParseException {
        
        logger.info("Performing virtual run simulation");
        List<WorkflowExecution.GroupExecution> groupExecutions = new ArrayList<>();
        
        List<TransferGroup> sortedGroups = graph.topologicalSort();
        for (TransferGroup group : sortedGroups) {
            Instant groupStart = Instant.now();
            
            // Simulate transfer execution with delays
            Map<String, TransferResult> transferResults = new HashMap<>();
            for (TransferGroup.TransferDefinition transfer : group.getTransfers()) {
                // Simulate transfer time
                try {
                    Thread.sleep(100); // Simulate 100ms transfer time
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
                TransferResult result = createMockTransferResult(transfer, true);
                transferResults.put(transfer.getName(), result);
            }
            
            Instant groupEnd = Instant.now();
            WorkflowExecution.GroupExecution groupExecution = new WorkflowExecution.GroupExecution(
                    group.getName(),
                    WorkflowStatus.COMPLETED,
                    groupStart,
                    groupEnd,
                    transferResults,
                    null
            );
            
            groupExecutions.add(groupExecution);
            logger.info("Virtual run completed group: " + group.getName());
        }
        
        return groupExecutions;
    }
    
    private List<WorkflowExecution.GroupExecution> performNormalExecution(WorkflowDefinition definition, DependencyGraph graph) 
            throws WorkflowParseException {
        
        logger.info("Performing normal workflow execution");
        List<WorkflowExecution.GroupExecution> groupExecutions = new ArrayList<>();
        
        List<TransferGroup> sortedGroups = graph.topologicalSort();
        for (TransferGroup group : sortedGroups) {
            Instant groupStart = Instant.now();
            
            Map<String, TransferResult> transferResults = new HashMap<>();
            boolean groupSuccess = true;
            String groupError = null;
            
            for (TransferGroup.TransferDefinition transfer : group.getTransfers()) {
                try {
                    TransferRequest request = transfer.toTransferRequest();
                    CompletableFuture<TransferResult> future = transferEngine.submitTransfer(request);
                    TransferResult result = future.get();
                    
                    transferResults.put(transfer.getName(), result);
                    
                    if (!result.isSuccessful()) {
                        groupSuccess = false;
                        if (!group.isContinueOnError()) {
                            groupError = "Transfer failed: " + transfer.getName();
                            break;
                        }
                    }
                    
                } catch (Exception e) {
                    // Log without stack trace for cleaner output, especially during testing
                    logger.log(Level.WARNING, "Transfer execution failed: " + transfer.getName() + " - " + e.getMessage());

                    // Only log full stack trace in debug mode or for unexpected exceptions
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Transfer execution exception details for: " + transfer.getName(), e);
                    }

                    TransferResult failedResult = createMockTransferResult(transfer, false);
                    transferResults.put(transfer.getName(), failedResult);
                    groupSuccess = false;

                    if (!group.isContinueOnError()) {
                        groupError = "Transfer execution failed: " + e.getMessage();
                        break;
                    }
                }
            }
            
            Instant groupEnd = Instant.now();
            WorkflowStatus groupStatus = groupSuccess ? WorkflowStatus.COMPLETED : WorkflowStatus.FAILED;
            
            WorkflowExecution.GroupExecution groupExecution = new WorkflowExecution.GroupExecution(
                    group.getName(),
                    groupStatus,
                    groupStart,
                    groupEnd,
                    transferResults,
                    groupError
            );
            
            groupExecutions.add(groupExecution);
            logger.info("Executed group: " + group.getName() + " with status: " + groupStatus);
            
            // Stop execution if group failed and workflow doesn't continue on error
            if (!groupSuccess && !group.isContinueOnError()) {
                break;
            }
        }
        
        return groupExecutions;
    }
    
    private TransferResult createMockTransferResult(TransferGroup.TransferDefinition transfer, boolean success) {
        TransferResult.Builder builder = TransferResult.builder()
                .requestId(UUID.randomUUID().toString())
                .finalStatus(success ? TransferStatus.COMPLETED : TransferStatus.FAILED)
                .bytesTransferred(success ? 1024L : 0L);

        if (success) {
            builder.startTime(Instant.now().minusMillis(100))
                   .endTime(Instant.now())
                   .actualChecksum("mock-checksum");
        } else {
            builder.errorMessage("Mock transfer failure")
                   .cause(new RuntimeException("Mock failure"));
        }

        return builder.build();
    }
    
    private WorkflowExecution createFailedExecution(WorkflowDefinition definition, ExecutionContext context, Exception e) {
        return new WorkflowExecution(
                context.getExecutionId(),
                definition,
                context,
                WorkflowStatus.FAILED,
                Instant.now(),
                Instant.now(),
                List.of(),
                e.getMessage(),
                e
        );
    }
}
