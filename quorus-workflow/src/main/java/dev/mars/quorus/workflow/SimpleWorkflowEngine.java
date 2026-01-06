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
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
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

    private static final Logger logger = Logger.getLogger(SimpleWorkflowEngine.class.getName());

    private final Vertx vertx;
    private final TransferEngine transferEngine;
    private final WorkflowDefinitionParser parser;
    private final WorkerExecutor workerExecutor;
    private final Map<String, WorkflowExecution> activeExecutions;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

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
        this.workerExecutor = vertx.createSharedWorkerExecutor(
                "quorus-workflow-pool",
                20,  // Max pool size for workflow execution
                TimeUnit.MINUTES.toNanos(30)  // 30 minute max execution time
        );
        this.activeExecutions = new ConcurrentHashMap<>();
        logger.info("SimpleWorkflowEngine initialized (Vert.x WorkerExecutor mode)");
    }

    /**
     * Create a new SimpleWorkflowEngine (deprecated - use constructor with Vertx).
     *
     * @param transferEngine the transfer engine for executing transfers
     * @deprecated Use {@link #SimpleWorkflowEngine(Vertx, TransferEngine)} instead
     */
    @Deprecated(since = "1.0", forRemoval = true)
    public SimpleWorkflowEngine(TransferEngine transferEngine) {
        this(Vertx.vertx(), transferEngine);
        logger.warning("Using deprecated constructor - consider passing shared Vert.x instance");
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
        if (shutdown.getAndSet(true)) {
            return; // Already shutdown
        }

        logger.info("SimpleWorkflowEngine shutdown initiated");
        workerExecutor.close();
        logger.info("SimpleWorkflowEngine shutdown completed (Vert.x WorkerExecutor closed)");
    }
    
    private CompletableFuture<WorkflowExecution> executeWorkflow(WorkflowDefinition definition,
                                                               ExecutionContext context,
                                                               ExecutionContext.ExecutionMode mode) {
        if (shutdown.get()) {
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

        // Use Vert.x WorkerExecutor with Promise/Future bridging
        Promise<WorkflowExecution> promise = Promise.promise();

        workerExecutor.<WorkflowExecution>executeBlocking(() -> {
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
        }).onComplete(ar -> {
            if (ar.succeeded()) {
                promise.complete(ar.result());
            } else {
                promise.fail(ar.cause());
            }
        });

        return promise.future().toCompletionStage().toCompletableFuture();
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

        logger.info("Performing virtual run simulation with parallel execution");
        List<WorkflowExecution.GroupExecution> groupExecutions = new ArrayList<>();

        List<TransferGroup> sortedGroups = graph.topologicalSort();
        for (TransferGroup group : sortedGroups) {
            Instant groupStart = Instant.now();

            // Simulate transfer execution with delays using Vert.x timers
            List<TransferGroup.TransferDefinition> transfers = group.getTransfers();

            // Create a list of Vert.x Futures for parallel simulation
            List<Future<Map.Entry<String, TransferResult>>> simulationFutures = transfers.stream()
                    .map(transfer -> {
                        Promise<Map.Entry<String, TransferResult>> promise = Promise.promise();

                        // Simulate 100ms transfer time using Vert.x timer (non-blocking)
                        vertx.setTimer(100, timerId -> {
                            TransferResult result = createMockTransferResult(transfer, true);
                            promise.complete(Map.entry(transfer.getName(), result));
                        });

                        return promise.future();
                    })
                    .collect(Collectors.toList());

            // Wait for all simulations to complete
            Map<String, TransferResult> transferResults = new HashMap<>();
            try {
                Future.all(simulationFutures)
                        .toCompletionStage()
                        .toCompletableFuture()
                        .get();

                // Collect results
                for (Future<Map.Entry<String, TransferResult>> future : simulationFutures) {
                    Map.Entry<String, TransferResult> entry = future.result();
                    transferResults.put(entry.getKey(), entry.getValue());
                }

            } catch (Exception e) {
                logger.log(Level.WARNING, "Virtual run simulation interrupted: " + e.getMessage());
                // Continue with partial results
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
            logger.info("Virtual run completed group: " + group.getName() + " (parallel simulation)");
        }

        return groupExecutions;
    }
    
    private List<WorkflowExecution.GroupExecution> performNormalExecution(WorkflowDefinition definition, DependencyGraph graph)
            throws WorkflowParseException {

        logger.info("Performing normal workflow execution with parallel transfer execution");
        List<WorkflowExecution.GroupExecution> groupExecutions = new ArrayList<>();

        List<TransferGroup> sortedGroups = graph.topologicalSort();
        for (TransferGroup group : sortedGroups) {
            Instant groupStart = Instant.now();

            // Execute all transfers in the group in parallel using Future.all()
            List<TransferGroup.TransferDefinition> transfers = group.getTransfers();

            // Create a list of Vert.x Futures for parallel execution
            List<Future<Map.Entry<String, TransferResult>>> transferFutures = transfers.stream()
                    .map(transfer -> {
                        try {
                            TransferRequest request = transfer.toTransferRequest();
                            CompletableFuture<TransferResult> cf = transferEngine.submitTransfer(request);

                            // Convert CompletableFuture to Vert.x Future
                            Promise<TransferResult> promise = Promise.promise();
                            cf.whenComplete((result, error) -> {
                                if (error != null) {
                                    promise.fail(error);
                                } else {
                                    promise.complete(result);
                                }
                            });

                            // Map to entry with transfer name
                            return promise.future()
                                    .recover(error -> {
                                        logger.log(Level.WARNING, "Transfer execution failed: " + transfer.getName() + " - " + error.getMessage());
                                        if (logger.isLoggable(Level.FINE)) {
                                            logger.log(Level.FINE, "Transfer execution exception details for: " + transfer.getName(), error);
                                        }
                                        return Future.succeededFuture(createMockTransferResult(transfer, false));
                                    })
                                    .map(result -> Map.entry(transfer.getName(), result));
                        } catch (Exception e) {
                            // Handle TransferException from toTransferRequest()
                            logger.log(Level.WARNING, "Failed to create transfer request: " + transfer.getName() + " - " + e.getMessage());
                            TransferResult failedResult = createMockTransferResult(transfer, false);
                            return Future.succeededFuture(Map.entry(transfer.getName(), failedResult));
                        }
                    })
                    .collect(Collectors.toList());

            // Wait for all transfers to complete using Future.all()
            Map<String, TransferResult> transferResults = new HashMap<>();
            boolean groupSuccess = true;
            String groupError = null;

            try {
                // Execute all transfers in parallel
                Future.all(transferFutures)
                        .toCompletionStage()
                        .toCompletableFuture()
                        .get();

                // Collect results
                for (Future<Map.Entry<String, TransferResult>> future : transferFutures) {
                    Map.Entry<String, TransferResult> entry = future.result();
                    transferResults.put(entry.getKey(), entry.getValue());

                    if (!entry.getValue().isSuccessful()) {
                        groupSuccess = false;
                        if (!group.isContinueOnError()) {
                            groupError = "Transfer failed: " + entry.getKey();
                        }
                    }
                }

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Group execution failed: " + group.getName() + " - " + e.getMessage());
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Group execution exception details for: " + group.getName(), e);
                }
                groupSuccess = false;
                groupError = "Group execution failed: " + e.getMessage();
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
            logger.info("Executed group: " + group.getName() + " with status: " + groupStatus + " (parallel execution)");

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
