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

import dev.mars.quorus.concurrent.TaskScope;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.transfer.TransferEngine;
import dev.mars.quorus.workflow.observability.WorkflowMetrics;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple implementation of WorkflowEngine for basic workflow execution.
 * Supports normal execution, dry run, and virtual run modes.
 *
 * <p>Blocking (RT-04): each call runs the workflow on the calling thread and returns the finished
 * execution. Groups run in dependency order, up to {@code execution.parallelism} groups at a time; the
 * transfers of one group run in parallel, each on its own virtual thread inside a {@link TaskScope}; a
 * failed transfer is run again up to the group's {@code retryCount} times. A definition that declares
 * {@code execution.dryRun} or {@code execution.virtualRun} never starts a transfer, whichever method
 * runs it.
 * The workflow's {@code execution.timeout} bounds the whole run: when it expires, the running
 * transfers are interrupted and the execution fails. {@link #cancel} stops a run the same way and it
 * ends {@code CANCELLED}. Interrupting the caller also stops the run, and {@link InterruptedException}
 * is thrown.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-18
 * @version 2.0
 */
public class SimpleWorkflowEngine implements WorkflowEngine {

    private static final Logger logger = LoggerFactory.getLogger(SimpleWorkflowEngine.class);

    /** Used when a definition has no execution timeout; the YAML parser's default is the same. */
    static final Duration DEFAULT_TIMEOUT = Duration.ofHours(1);

    /** How long a virtual run pretends each transfer takes. */
    private static final Duration VIRTUAL_TRANSFER_TIME = Duration.ofMillis(100);

    private final TransferEngine transferEngine;
    private final WorkflowDefinitionParser parser;
    private final Map<String, WorkflowExecution> activeExecutions;
    private final Map<String, Run> runs = new ConcurrentHashMap<>();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    // OpenTelemetry metrics (Phase 9 - Jan 2026)
    private final WorkflowMetrics metrics = WorkflowMetrics.getInstance();

    /**
     * Create a new SimpleWorkflowEngine.
     *
     * @param transferEngine the transfer engine for executing transfers
     */
    public SimpleWorkflowEngine(TransferEngine transferEngine) {
        this.transferEngine = Objects.requireNonNull(transferEngine, "Transfer engine cannot be null");
        this.parser = new YamlWorkflowDefinitionParser();
        this.activeExecutions = new ConcurrentHashMap<>();
        logger.info("SimpleWorkflowEngine initialized");
    }

    @Override
    public WorkflowExecution execute(WorkflowDefinition definition, ExecutionContext context) throws InterruptedException {
        return executeWorkflow(definition, context, ExecutionContext.ExecutionMode.NORMAL);
    }

    @Override
    public WorkflowExecution dryRun(WorkflowDefinition definition, ExecutionContext context) throws InterruptedException {
        return executeWorkflow(definition, context, ExecutionContext.ExecutionMode.DRY_RUN);
    }

    @Override
    public WorkflowExecution virtualRun(WorkflowDefinition definition, ExecutionContext context) throws InterruptedException {
        return executeWorkflow(definition, context, ExecutionContext.ExecutionMode.VIRTUAL_RUN);
    }

    @Override
    public WorkflowStatus getStatus(String executionId) {
        WorkflowExecution execution = activeExecutions.get(executionId);
        return execution != null ? execution.getStatus() : null;
    }

    /**
     * Stops a running execution: its running transfers are interrupted, no further group starts, and
     * the call that started it returns the execution with status {@code CANCELLED}.
     *
     * @return {@code false} if no execution with this id is running
     */
    @Override
    public boolean cancel(String executionId) {
        Run run = runs.get(executionId);
        if (run == null || !run.cancel()) {
            return false;
        }
        logger.info("Cancelling workflow execution: {}", executionId);
        WorkflowExecution execution = activeExecutions.get(executionId);
        if (execution != null) {
            // Record cancel metric (Phase 9 - Jan 2026)
            String workflowName = execution.getDefinition().getMetadata() != null
                    ? execution.getDefinition().getMetadata().getName() : executionId;
            metrics.recordWorkflowCancelled(workflowName, execution.getContext().getMode().name());
        }
        return true;
    }

    /**
     * The thread running one execution, so that {@link #cancel} can interrupt it. {@code cancel} and
     * {@code finish} are synchronized so that a cancel never interrupts the thread after its run ended.
     */
    private static final class Run {
        private final Thread runner = Thread.currentThread();
        private boolean cancelled;
        private boolean finished;

        synchronized boolean cancel() {
            if (finished || cancelled) {
                return false;
            }
            cancelled = true;
            runner.interrupt();
            return true;
        }

        /**
         * Ends the run on the runner thread and reports whether it was cancelled. The interrupt sent
         * by a cancel is cleared, so it does not reach the caller.
         */
        synchronized boolean finish() {
            finished = true;
            if (cancelled) {
                Thread.interrupted();
            }
            return cancelled;
        }
    }

    /** Refuses new executions. Executions already running finish on their callers' threads. */
    @Override
    public void shutdown() {
        if (shutdown.getAndSet(true)) {
            return; // Already shutdown
        }
        logger.info("SimpleWorkflowEngine shutdown completed");
    }

    private WorkflowExecution executeWorkflow(WorkflowDefinition definition,
                                              ExecutionContext context,
                                              ExecutionContext.ExecutionMode mode) throws InterruptedException {
        if (shutdown.get()) {
            throw new IllegalStateException("Workflow engine is shutdown");
        }

        // Create execution context with the effective mode
        ExecutionContext executionContext = ExecutionContext.builder()
                .executionId(context.getExecutionId())
                .mode(effectiveMode(definition, mode))
                .variables(context.getVariables())
                .userId(context.getUserId())
                .metadata(context.getMetadata())
                .build();

        return executeWorkflowInternal(definition, executionContext);
    }

    /**
     * The mode a run actually uses: the definition's {@code execution.dryRun} or
     * {@code execution.virtualRun} flag can only make a run safer, never start transfers. A dry run
     * wins over a virtual run, and both over a normal run.
     */
    static ExecutionContext.ExecutionMode effectiveMode(WorkflowDefinition definition,
                                                        ExecutionContext.ExecutionMode requested) {
        WorkflowDefinition.ExecutionConfig config = definition.getSpec() != null
                ? definition.getSpec().getExecution() : null;
        if (requested == ExecutionContext.ExecutionMode.DRY_RUN || (config != null && config.isDryRun())) {
            return ExecutionContext.ExecutionMode.DRY_RUN;
        }
        if (requested == ExecutionContext.ExecutionMode.VIRTUAL_RUN || (config != null && config.isVirtualRun())) {
            return ExecutionContext.ExecutionMode.VIRTUAL_RUN;
        }
        return requested;
    }

    private WorkflowExecution executeWorkflowInternal(WorkflowDefinition definition, ExecutionContext context)
            throws InterruptedException {

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
            return createFailedExecution(definition, context, e);
        }
        if (!validation.isValid()) {
            return createFailedExecution(definition, context,
                    new WorkflowParseException("Workflow validation failed: " +
                        validation.getErrors().get(0).getMessage()));
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
            return createFailedExecution(definition, context, e);
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
        Run run = new Run();
        runs.put(executionId, run);
        Duration timeout = timeoutOf(definition);
        Instant deadline = startTime.plus(timeout);
        int parallelism = parallelismOf(definition);
        try {
            // Select execution mode
            List<WorkflowExecution.GroupExecution> groupExecutions;
            if (context.getMode() == ExecutionContext.ExecutionMode.DRY_RUN) {
                groupExecutions = performDryRun(resolvedDefinition, sortedGroups);
            } else if (context.getMode() == ExecutionContext.ExecutionMode.VIRTUAL_RUN) {
                logger.info("Performing virtual run simulation, up to {} groups at a time", parallelism);
                groupExecutions = executeGroups(sortedGroups, parallelism, null, true, deadline);
            } else {
                logger.info("Performing normal workflow execution, up to {} groups at a time", parallelism);
                groupExecutions = executeGroups(sortedGroups, parallelism, workflowName, false, deadline);
            }
            if (run.finish()) {
                return createCancelledExecution(definition, context, startTime, groupExecutions);
            }

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
        } catch (InterruptedException e) {
            if (run.finish()) {
                return createCancelledExecution(definition, context, startTime, List.of());
            }
            logger.warn("Workflow execution interrupted: {}", executionId);
            metrics.recordWorkflowCancelled(workflowName, executionMode);
            throw e;
        } catch (RuntimeException e) {
            if (run.finish()) {
                return createCancelledExecution(definition, context, startTime, List.of());
            }
            if (isTimeout(e)) {
                logger.error("Workflow execution timed out: {} after {}", executionId, timeout);
                return createFailedExecution(definition, context,
                        new java.util.concurrent.TimeoutException("Workflow timed out after " + timeout));
            }
            logger.error("Workflow execution failed: {} - {}", executionId, e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Workflow execution exception details for: {}", executionId, e);
            }
            return createFailedExecution(definition, context, e);
        } finally {
            run.finish();
            runs.remove(executionId, run);
            activeExecutions.remove(executionId);
        }
    }

    /** A group running in parallel with others fails with the timeout as its cause, so look through causes. */
    private static boolean isTimeout(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof TaskScope.TimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static Duration timeoutOf(WorkflowDefinition definition) {
        WorkflowDefinition.ExecutionConfig config = definition.getSpec() != null
                ? definition.getSpec().getExecution() : null;
        return config != null && config.getTimeout() != null ? config.getTimeout() : DEFAULT_TIMEOUT;
    }

    /** The most groups that run at once; the YAML parser's default, like an absent setting, is one. */
    private static int parallelismOf(WorkflowDefinition definition) {
        WorkflowDefinition.ExecutionConfig config = definition.getSpec() != null
                ? definition.getSpec().getExecution() : null;
        return config != null ? config.getParallelism() : 1;
    }

    /** Time left before the deadline; throws when none is left. */
    private static Duration remaining(Instant deadline, String before) {
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isNegative() || remaining.isZero()) {
            throw new TaskScope.TimeoutException("Workflow deadline passed before " + before);
        }
        return remaining;
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

    /**
     * Runs the groups in rounds. Each round starts, in dependency order, up to {@code parallelism}
     * groups whose dependencies have all run, and waits for them. After a round with a failed group
     * that does not continue on error, no further group starts. With a parallelism of one this is the
     * dependency order, one group at a time.
     *
     * @return the groups that ran, in dependency order
     * @throws TaskScope.TimeoutException if the deadline passes
     * @throws InterruptedException       if the caller is interrupted or the execution is cancelled
     */
    private List<WorkflowExecution.GroupExecution> executeGroups(
            List<TransferGroup> sortedGroups,
            int parallelism,
            String workflowName,
            boolean virtualRun,
            Instant deadline) throws InterruptedException {

        Set<String> inWorkflow = sortedGroups.stream().map(TransferGroup::getName).collect(Collectors.toSet());
        Map<String, WorkflowExecution.GroupExecution> ran = new HashMap<>();
        List<TransferGroup> pending = new ArrayList<>(sortedGroups);
        boolean stop = false;
        while (!pending.isEmpty() && !stop) {
            List<TransferGroup> round = pending.stream()
                    .filter(group -> group.getDependsOn().stream()
                            .allMatch(dependency -> !inWorkflow.contains(dependency) || ran.containsKey(dependency)))
                    .limit(parallelism)
                    .toList();
            if (round.isEmpty()) {
                break; // Unreachable after a topological sort; guards against a loop that never ends.
            }
            List<WorkflowExecution.GroupExecution> results = new ArrayList<>();
            if (round.size() == 1) {
                results.add(executeGroup(round.getFirst(), workflowName, virtualRun, deadline));
            } else {
                List<TaskScope.Subtask<WorkflowExecution.GroupExecution>> subtasks = new ArrayList<>();
                try (TaskScope scope = TaskScope.open("workflow-groups", remaining(deadline, "the next groups"))) {
                    for (TransferGroup group : round) {
                        subtasks.add(scope.fork(() -> executeGroup(group, workflowName, virtualRun, deadline)));
                    }
                    scope.join();
                }
                subtasks.forEach(subtask -> results.add(subtask.get()));
            }
            for (int i = 0; i < round.size(); i++) {
                TransferGroup group = round.get(i);
                WorkflowExecution.GroupExecution groupExecution = results.get(i);
                ran.put(group.getName(), groupExecution);
                pending.remove(group);
                // Stop execution if group failed and workflow doesn't continue on error
                if (!groupExecution.isSuccessful() && !group.isContinueOnError()) {
                    stop = true;
                }
            }
        }
        return sortedGroups.stream().map(group -> ran.get(group.getName())).filter(Objects::nonNull).toList();
    }

    /**
     * Runs one group's transfers in parallel and waits for all of them. A transfer that fails or
     * throws gives a failed result; it does not stop the other transfers of the group.
     */
    private WorkflowExecution.GroupExecution executeGroup(TransferGroup group, String workflowName,
                                                          boolean virtualRun, Instant deadline)
            throws InterruptedException {
        Instant groupStart = Instant.now();
        Map<String, TaskScope.Subtask<TransferResult>> subtasks = new LinkedHashMap<>();
        try (TaskScope scope = TaskScope.open("workflow-group-" + group.getName(),
                remaining(deadline, "group " + group.getName()))) {
            for (TransferGroup.TransferDefinition transfer : group.getTransfers()) {
                subtasks.put(transfer.getName(), scope.fork(virtualRun
                        ? () -> simulateTransfer(transfer)
                        : () -> runTransfer(transfer, group.getRetryCount())));
            }
            scope.join();
        }

        if (virtualRun) {
            Map<String, TransferResult> transferResults = new HashMap<>();
            subtasks.forEach((name, subtask) -> transferResults.put(name, subtask.get()));
            logger.info("Virtual run completed group: {} (parallel simulation)", group.getName());
            return new WorkflowExecution.GroupExecution(group.getName(), WorkflowStatus.COMPLETED,
                    groupStart, Instant.now(), transferResults, null);
        }

        Map<String, TransferResult> transferResults = new HashMap<>();
        boolean groupSuccess = true;
        String groupError = null;
        for (Map.Entry<String, TaskScope.Subtask<TransferResult>> entry : subtasks.entrySet()) {
            TransferResult result = entry.getValue().get();
            transferResults.put(entry.getKey(), result);

            if (workflowName != null) {
                metrics.recordStepExecuted(workflowName, "transfer");
            }

            if (!result.isSuccessful()) {
                groupSuccess = false;
                if (workflowName != null) {
                    metrics.recordStepFailed(workflowName, "transfer",
                        result.getErrorMessage().orElse("Unknown error"));
                }
                if (!group.isContinueOnError()) {
                    groupError = "Transfer failed: " + entry.getKey();
                }
            }
        }

        WorkflowStatus groupStatus = groupSuccess ? WorkflowStatus.COMPLETED : WorkflowStatus.FAILED;
        logger.info("Executed group: {} with status: {} (parallel execution)", group.getName(), groupStatus);
        return new WorkflowExecution.GroupExecution(group.getName(), groupStatus, groupStart, Instant.now(),
                transferResults, groupError);
    }

    /**
     * Runs one transfer on the current (virtual) thread, and runs it again up to {@code retries} more
     * times while it fails (the group's {@code retryCount}). Each run is a separate engine transfer,
     * which applies its own retries inside. Any failure becomes a failed result.
     */
    private TransferResult runTransfer(TransferGroup.TransferDefinition transfer, int retries)
            throws InterruptedException {
        TransferResult result = runTransfer(transfer);
        for (int retry = 1; retry <= retries && !result.isSuccessful(); retry++) {
            logger.warn("Transfer {} failed; group retry {} of {}", transfer.getName(), retry, retries);
            result = runTransfer(transfer);
        }
        return result;
    }

    /** Runs one transfer on the current (virtual) thread; any failure becomes a failed result. */
    private TransferResult runTransfer(TransferGroup.TransferDefinition transfer) throws InterruptedException {
        TransferRequest request;
        try {
            request = transfer.toTransferRequest();
        } catch (Exception e) {
            logger.error("Failed to create transfer request: {} - {}", transfer.getName(), e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Transfer request creation exception details for: {}", transfer.getName(), e);
            }
            return createMockTransferResult(transfer, false);
        }
        try {
            return transferEngine.transfer(request);
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                // The scope is cancelling this transfer (deadline or caller interrupt); let it end.
                throw new InterruptedException("Transfer " + transfer.getName() + " was cancelled");
            }
            logger.error("Transfer execution failed: {} - {}", transfer.getName(), e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Transfer execution exception details for: {}", transfer.getName(), e);
            }
            return createMockTransferResult(transfer, false);
        }
    }

    private TransferResult simulateTransfer(TransferGroup.TransferDefinition transfer) throws InterruptedException {
        Thread.sleep(VIRTUAL_TRANSFER_TIME);
        return createMockTransferResult(transfer, true);
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
    
    private WorkflowExecution createCancelledExecution(WorkflowDefinition definition, ExecutionContext context,
                                                       Instant startTime,
                                                       List<WorkflowExecution.GroupExecution> groupExecutions) {
        logger.info("Workflow execution cancelled: {}", context.getExecutionId());
        return new WorkflowExecution(context.getExecutionId(), definition, context, WorkflowStatus.CANCELLED,
                startTime, Instant.now(), groupExecutions, "Workflow cancelled", null);
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
