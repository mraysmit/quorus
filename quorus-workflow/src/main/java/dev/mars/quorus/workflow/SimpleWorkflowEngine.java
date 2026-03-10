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
import dev.mars.quorus.workflow.observability.WorkflowMetrics;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Collectors;

/**
 * Simple implementation of WorkflowEngine for basic workflow execution.
 * Supports normal execution, dry run, and virtual run modes.
 *
 * <p>Vert.x 5 Migration (Phase 3): Converted to use Future.all() for parallel
 * transfer execution within groups, improving throughput and resource utilization.
 * Transfers within a group now execute in parallel using Vert.x reactive patterns.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-18
 * @version 1.0
 */
public class SimpleWorkflowEngine implements WorkflowEngine {

    private static final Logger logger = LoggerFactory.getLogger(SimpleWorkflowEngine.class);

    private final Vertx vertx;
    private final TransferEngine transferEngine;
    private final WorkflowDefinitionParser parser;
    private final Map<String, WorkflowExecution> activeExecutions;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    // OpenTelemetry metrics (Phase 9 - Jan 2026)
    private final WorkflowMetrics metrics = WorkflowMetrics.getInstance();

    /**
     * Create a new SimpleWorkflowEngine with Vert.x integration (recommended).
     *
     * @param vertx the Vert.x instance for reactive operations
     * @param transferEngine the transfer engine for executing transfers
     */
    public SimpleWorkflowEngine(Vertx vertx, TransferEngine transferEngine) {
        this.vertx = Objects.requireNonNull(vertx, "Vertx cannot be null");
        this.transferEngine = Objects.requireNonNull(transferEngine, "Transfer engine cannot be null");
        this.parser = new YamlWorkflowDefinitionParser();
        this.activeExecutions = new ConcurrentHashMap<>();
        logger.info("SimpleWorkflowEngine initialized (reactive mode)");
    }

    /**
     * Create a new SimpleWorkflowEngine with an internally-managed Vert.x instance.
     * Prefer {@link #SimpleWorkflowEngine(Vertx, TransferEngine)} when a shared Vert.x instance is available.
     *
     * @param transferEngine the transfer engine for executing transfers
     */
    public SimpleWorkflowEngine(TransferEngine transferEngine) {
        this(Vertx.vertx(), transferEngine);
    }
    
    @Override
    public Future<WorkflowExecution> execute(WorkflowDefinition definition, ExecutionContext context) {
        return executeWorkflow(definition, context, ExecutionContext.ExecutionMode.NORMAL);
    }
    
    @Override
    public Future<WorkflowExecution> dryRun(WorkflowDefinition definition, ExecutionContext context) {
        return executeWorkflow(definition, context, ExecutionContext.ExecutionMode.DRY_RUN);
    }
    
    @Override
    public Future<WorkflowExecution> virtualRun(WorkflowDefinition definition, ExecutionContext context) {
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
        logger.warn("Pause operation not supported in SimpleWorkflowEngine");
        return false;
    }
    
    @Override
    public boolean resume(String executionId) {
        // For this simple implementation, we don't support resuming
        logger.warn("Resume operation not supported in SimpleWorkflowEngine");
        return false;
    }
    
    @Override
    public boolean cancel(String executionId) {
        WorkflowExecution execution = activeExecutions.get(executionId);
        if (execution != null && execution.isRunning()) {
            // For this simple implementation, we mark as cancelled but don't interrupt
            logger.info("Cancelling workflow execution: {}", executionId);
            
            // Record cancel metric (Phase 9 - Jan 2026)
            String workflowName = execution.getDefinition().getMetadata() != null 
                    ? execution.getDefinition().getMetadata().getName() : executionId;
            String executionMode = execution.getContext() != null 
                    ? execution.getContext().getMode().name() : "NORMAL";
            metrics.recordWorkflowCancelled(workflowName, executionMode);
            
            return true;
        }
        return false;
    }
    
    @Override
    public void shutdown() {
        if (shutdown.getAndSet(true)) {
            return; // Already shutdown
        }

        logger.info("SimpleWorkflowEngine shutdown initiated");
        logger.info("SimpleWorkflowEngine shutdown completed");
    }
    
    private Future<WorkflowExecution> executeWorkflow(WorkflowDefinition definition,
                                                               ExecutionContext context,
                                                               ExecutionContext.ExecutionMode mode) {
        if (shutdown.get()) {
            return Future.failedFuture(
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

        return executeWorkflowInternal(definition, executionContext);
    }
    
    private Future<WorkflowExecution> executeWorkflowInternal(WorkflowDefinition definition, ExecutionContext context) {
        
        Instant startTime = Instant.now();
        String executionId = context.getExecutionId();
        String workflowName = definition.getMetadata() != null && definition.getMetadata().getName() != null 
                ? definition.getMetadata().getName() : executionId;
        String executionMode = context.getMode().name();
        
        logger.info("Starting workflow execution: {} in mode: {}", executionId, context.getMode());
        
        // Record workflow started (Phase 9 - Jan 2026)
        metrics.recordWorkflowStarted(workflowName, executionMode);
        
        // Validate workflow
        ValidationResult validation;
        try {
            validation = parser.validate(definition);
        } catch (Exception e) {
            return Future.succeededFuture(createFailedExecution(definition, context, e));
        }
        if (!validation.isValid()) {
            return Future.succeededFuture(createFailedExecution(definition, context,
                    new WorkflowParseException("Workflow validation failed: " + 
                        validation.getErrors().get(0).getMessage())));
        }
        
        // Resolve variables - start with workflow variables, then add context variables
        VariableResolver resolver;
        WorkflowDefinition resolvedDefinition;
        DependencyGraph graph;
        List<TransferGroup> sortedGroups;
        try {
            resolver = new VariableResolver(definition.getSpec().getVariables());
            resolver = resolver.withContext(context.getVariables());
            resolvedDefinition = resolver.resolve(definition);
            graph = parser.buildDependencyGraph(List.of(resolvedDefinition));
            sortedGroups = graph.topologicalSort();
        } catch (Exception e) {
            return Future.succeededFuture(createFailedExecution(definition, context, e));
        }
        
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
        
        // Select execution mode
        Future<List<WorkflowExecution.GroupExecution>> groupsFuture;
        if (context.getMode() == ExecutionContext.ExecutionMode.DRY_RUN) {
            groupsFuture = Future.succeededFuture(performDryRun(resolvedDefinition, sortedGroups));
        } else if (context.getMode() == ExecutionContext.ExecutionMode.VIRTUAL_RUN) {
            groupsFuture = performVirtualRun(resolvedDefinition, sortedGroups);
        } else {
            groupsFuture = performNormalExecution(resolvedDefinition, sortedGroups, workflowName);
        }
        
        return groupsFuture.map(groupExecutions -> {
            Instant endTime = Instant.now();
            WorkflowStatus finalStatus = groupExecutions.stream()
                    .allMatch(WorkflowExecution.GroupExecution::isSuccessful) ? 
                    WorkflowStatus.COMPLETED : WorkflowStatus.FAILED;
            
            // Record workflow completion (Phase 9 - Jan 2026)
            double durationSeconds = Duration.between(startTime, endTime).toMillis() / 1000.0;
            int transferCount = groupExecutions.stream()
                    .mapToInt(g -> g.getTransferResults() != null ? g.getTransferResults().size() : 0)
                    .sum();
            
            if (finalStatus == WorkflowStatus.COMPLETED) {
                metrics.recordWorkflowCompleted(workflowName, executionMode, durationSeconds, transferCount);
            } else {
                metrics.recordWorkflowFailed(workflowName, executionMode, "Step execution failed");
            }
            
            logger.info("Workflow execution completed: {} with status: {}", executionId, finalStatus);
            
            return new WorkflowExecution(
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
        }).recover(err -> {
            logger.error("Workflow execution failed: {} - {}", executionId, err.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Workflow execution exception details for: {}", executionId, err);
            }
            return Future.succeededFuture(createFailedExecution(definition, context, 
                    err instanceof Exception ex ? ex : new RuntimeException(err)));
        }).onComplete(ar -> activeExecutions.remove(executionId));
    }
    
    private List<WorkflowExecution.GroupExecution> performDryRun(WorkflowDefinition definition,
                                                                List<TransferGroup> sortedGroups) {
        
        logger.info("Performing dry run validation");
        List<WorkflowExecution.GroupExecution> groupExecutions = new ArrayList<>();
        
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
            logger.info("Dry run validated group: {}", group.getName());
        }
        
        return groupExecutions;
    }
    
    private Future<List<WorkflowExecution.GroupExecution>> performVirtualRun(WorkflowDefinition definition,
                                                                             List<TransferGroup> sortedGroups) {

        logger.info("Performing virtual run simulation with parallel execution");
        return executeGroupsSequentially(sortedGroups, 0, new ArrayList<>(), null, true);
    }
    
    private Future<List<WorkflowExecution.GroupExecution>> performNormalExecution(WorkflowDefinition definition,
                                                                                  List<TransferGroup> sortedGroups,
                                                                                  String workflowName) {

        logger.info("Performing normal workflow execution with parallel transfer execution");
        return executeGroupsSequentially(sortedGroups, 0, new ArrayList<>(), workflowName, false);
    }
    
    /**
     * Execute transfer groups sequentially using recursive Future composition.
     * Transfers within each group run in parallel via Future.all().
     */
    private Future<List<WorkflowExecution.GroupExecution>> executeGroupsSequentially(
            List<TransferGroup> sortedGroups,
            int index,
            List<WorkflowExecution.GroupExecution> results,
            String workflowName,
            boolean virtualRun) {
        
        if (index >= sortedGroups.size()) {
            return Future.succeededFuture(results);
        }
        
        TransferGroup group = sortedGroups.get(index);
        
        Future<WorkflowExecution.GroupExecution> groupFuture = virtualRun 
                ? executeVirtualGroup(group) 
                : executeNormalGroup(group, workflowName);
        
        return groupFuture.compose(groupExecution -> {
            results.add(groupExecution);
            
            // Stop execution if group failed and workflow doesn't continue on error
            if (!groupExecution.isSuccessful() && !group.isContinueOnError()) {
                return Future.succeededFuture(results);
            }
            
            return executeGroupsSequentially(sortedGroups, index + 1, results, workflowName, virtualRun);
        });
    }
    
    /**
     * Execute a single group's transfers in parallel (virtual run - simulated).
     */
    private Future<WorkflowExecution.GroupExecution> executeVirtualGroup(TransferGroup group) {
        Instant groupStart = Instant.now();
        List<TransferGroup.TransferDefinition> transfers = group.getTransfers();
        
        List<Future<Map.Entry<String, TransferResult>>> simulationFutures = transfers.stream()
                .map(transfer -> {
                    Promise<Map.Entry<String, TransferResult>> promise = Promise.promise();
                    vertx.setTimer(100, timerId -> {
                        TransferResult result = createMockTransferResult(transfer, true);
                        promise.complete(Map.entry(transfer.getName(), result));
                    });
                    return promise.future();
                })
                .collect(Collectors.toList());
        
        return Future.all(simulationFutures).map(cf -> {
            Map<String, TransferResult> transferResults = new HashMap<>();
            for (Future<Map.Entry<String, TransferResult>> future : simulationFutures) {
                Map.Entry<String, TransferResult> entry = future.result();
                transferResults.put(entry.getKey(), entry.getValue());
            }
            
            Instant groupEnd = Instant.now();
            logger.info("Virtual run completed group: {} (parallel simulation)", group.getName());
            
            return new WorkflowExecution.GroupExecution(
                    group.getName(),
                    WorkflowStatus.COMPLETED,
                    groupStart,
                    groupEnd,
                    transferResults,
                    null
            );
        });
    }
    
    /**
     * Execute a single group's transfers in parallel (normal execution).
     */
    private Future<WorkflowExecution.GroupExecution> executeNormalGroup(TransferGroup group, String workflowName) {
        Instant groupStart = Instant.now();
        List<TransferGroup.TransferDefinition> transfers = group.getTransfers();
        
        List<Future<Map.Entry<String, TransferResult>>> transferFutures = transfers.stream()
                .map(transfer -> {
                    try {
                        TransferRequest request = transfer.toTransferRequest();
                        Future<TransferResult> transferFuture = transferEngine.submitTransfer(request);
                        
                        return transferFuture
                                .<TransferResult>recover(error -> {
                                    logger.error("Transfer execution failed: {} - {}", transfer.getName(), error.getMessage());
                                    if (logger.isDebugEnabled()) {
                                        logger.debug("Transfer execution exception details for: {}", transfer.getName(), error);
                                    }
                                    return Future.succeededFuture(createMockTransferResult(transfer, false));
                                })
                                .<Map.Entry<String, TransferResult>>map(result -> Map.entry(transfer.getName(), result));
                    } catch (Exception e) {
                        logger.error("Failed to create transfer request: {} - {}", transfer.getName(), e.getMessage());
                        if (logger.isDebugEnabled()) {
                            logger.debug("Transfer request creation exception details for: {}", transfer.getName(), e);
                        }
                        TransferResult failedResult = createMockTransferResult(transfer, false);
                        return Future.succeededFuture(Map.entry(transfer.getName(), failedResult));
                    }
                })
                .collect(Collectors.toList());
        
        return Future.all(transferFutures).map(cf -> {
            Map<String, TransferResult> transferResults = new HashMap<>();
            boolean groupSuccess = true;
            String groupError = null;
            
            for (Future<Map.Entry<String, TransferResult>> future : transferFutures) {
                Map.Entry<String, TransferResult> entry = future.result();
                transferResults.put(entry.getKey(), entry.getValue());
                
                if (workflowName != null) {
                    metrics.recordStepExecuted(workflowName, "transfer");
                }
                
                if (!entry.getValue().isSuccessful()) {
                    groupSuccess = false;
                    if (workflowName != null) {
                        metrics.recordStepFailed(workflowName, "transfer", 
                            entry.getValue().getErrorMessage().orElse("Unknown error"));
                    }
                    if (!group.isContinueOnError()) {
                        groupError = "Transfer failed: " + entry.getKey();
                    }
                }
            }
            
            Instant groupEnd = Instant.now();
            WorkflowStatus groupStatus = groupSuccess ? WorkflowStatus.COMPLETED : WorkflowStatus.FAILED;
            
            logger.info("Executed group: {} with status: {} (parallel execution)", group.getName(), groupStatus);
            
            return new WorkflowExecution.GroupExecution(
                    group.getName(),
                    groupStatus,
                    groupStart,
                    groupEnd,
                    transferResults,
                    groupError
            );
        });
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
        // Record workflow failure (Phase 9 - Jan 2026)
        String workflowName = definition.getMetadata() != null && definition.getMetadata().getName() != null 
                ? definition.getMetadata().getName() : context.getExecutionId();
        metrics.recordWorkflowFailed(workflowName, context.getMode().name(), e.getMessage());
        
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
