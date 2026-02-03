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

package dev.mars.quorus.simulator.workflow;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * In-memory simulator for workflow execution.
 * 
 * <p>This simulator enables testing of workflow definitions and execution
 * without real transfer operations. It supports:
 * <ul>
 *   <li>Step-by-step workflow execution</li>
 *   <li>Dry-run and virtual-run modes</li>
 *   <li>Pause/resume/cancel operations</li>
 *   <li>Step failure injection</li>
 *   <li>Workflow event callbacks</li>
 * </ul>
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * InMemoryWorkflowEngineSimulator engine = new InMemoryWorkflowEngineSimulator();
 * engine.setStepExecutionDelayMs(500);
 * 
 * engine.setStepCallback((step, status) -> {
 *     System.out.println("Step " + step.getName() + ": " + status);
 * });
 * 
 * CompletableFuture<WorkflowExecution> future = engine.execute(workflowDef, context);
 * }</pre>
 * 
 * @author Mark Andrew Ray-Smith
 * @since 1.0
 */
public class InMemoryWorkflowEngineSimulator {

    // Configuration
    private long stepExecutionDelayMs = 100;
    private long defaultStepDurationMs = 500;
    
    // Active executions
    private final Map<String, WorkflowExecution> executions = new ConcurrentHashMap<>();
    
    // Executor
    private final ScheduledExecutorService scheduler;
    
    // Chaos engineering
    private WorkflowFailureMode failureMode = WorkflowFailureMode.NONE;
    private String failAtStep = null;
    private double stepFailureRate = 0.0;
    private final Map<String, WorkflowFailureMode> stepFailures = new ConcurrentHashMap<>();
    
    // Statistics
    private final AtomicLong totalExecutions = new AtomicLong(0);
    private final AtomicLong completedExecutions = new AtomicLong(0);
    private final AtomicLong failedExecutions = new AtomicLong(0);
    
    // Callbacks
    private BiConsumer<WorkflowStep, StepStatus> stepCallback;
    private Function<WorkflowStep, Map<String, Object>> stepOutputGenerator;

    /**
     * Workflow failure modes.
     */
    public enum WorkflowFailureMode {
        /** Normal operation */
        NONE,
        /** Workflow validation fails */
        VALIDATION_FAILURE,
        /** Step execution fails */
        STEP_FAILURE,
        /** Dependency resolution fails */
        DEPENDENCY_FAILURE,
        /** Workflow times out */
        TIMEOUT,
        /** Resource unavailable */
        RESOURCE_UNAVAILABLE,
        /** Random step failures */
        RANDOM_FAILURE
    }

    /**
     * Workflow status.
     */
    public enum WorkflowStatus {
        PENDING, VALIDATING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
    }

    /**
     * Step status.
     */
    public enum StepStatus {
        PENDING, RUNNING, COMPLETED, FAILED, SKIPPED
    }

    /**
     * Creates a new workflow engine simulator.
     */
    public InMemoryWorkflowEngineSimulator() {
        this.scheduler = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "workflow-engine-simulator");
            t.setDaemon(true);
            return t;
        });
    }

    // ==================== Workflow Execution ====================

    /**
     * Executes a workflow.
     *
     * @param definition the workflow definition
     * @param context the execution context
     * @return a future containing the execution result
     */
    public CompletableFuture<WorkflowExecution> execute(
            WorkflowDefinition definition, 
            ExecutionContext context) {
        return executeInternal(definition, context, ExecutionMode.NORMAL);
    }

    /**
     * Performs a dry-run of a workflow (validation only, no actual execution).
     *
     * @param definition the workflow definition
     * @param context the execution context
     * @return a future containing the validation result
     */
    public CompletableFuture<WorkflowExecution> dryRun(
            WorkflowDefinition definition, 
            ExecutionContext context) {
        return executeInternal(definition, context, ExecutionMode.DRY_RUN);
    }

    /**
     * Performs a virtual-run of a workflow (simulated execution with no real transfers).
     *
     * @param definition the workflow definition
     * @param context the execution context
     * @return a future containing the execution result
     */
    public CompletableFuture<WorkflowExecution> virtualRun(
            WorkflowDefinition definition, 
            ExecutionContext context) {
        return executeInternal(definition, context, ExecutionMode.VIRTUAL);
    }

    /**
     * Gets the status of a workflow execution.
     *
     * @param executionId the execution ID
     * @return the workflow status, or null if not found
     */
    public WorkflowStatus getStatus(String executionId) {
        WorkflowExecution execution = executions.get(executionId);
        return execution != null ? execution.status : null;
    }

    /**
     * Gets a workflow execution by ID.
     *
     * @param executionId the execution ID
     * @return the workflow execution, or null if not found
     */
    public WorkflowExecution getExecution(String executionId) {
        return executions.get(executionId);
    }

    /**
     * Pauses a running workflow.
     *
     * @param executionId the execution ID
     * @return true if the workflow was paused
     */
    public boolean pause(String executionId) {
        WorkflowExecution execution = executions.get(executionId);
        if (execution == null || execution.status != WorkflowStatus.RUNNING) {
            return false;
        }
        
        execution.status = WorkflowStatus.PAUSED;
        fireEvent(execution, WorkflowEvent.paused(executionId));
        return true;
    }

    /**
     * Resumes a paused workflow.
     *
     * @param executionId the execution ID
     * @return true if the workflow was resumed
     */
    public boolean resume(String executionId) {
        WorkflowExecution execution = executions.get(executionId);
        if (execution == null || execution.status != WorkflowStatus.PAUSED) {
            return false;
        }
        
        execution.status = WorkflowStatus.RUNNING;
        synchronized (execution) {
            execution.notifyAll();
        }
        fireEvent(execution, WorkflowEvent.resumed(executionId));
        return true;
    }

    /**
     * Cancels a workflow execution.
     *
     * @param executionId the execution ID
     * @return true if the workflow was cancelled
     */
    public boolean cancel(String executionId) {
        WorkflowExecution execution = executions.get(executionId);
        if (execution == null) {
            return false;
        }
        
        if (execution.status == WorkflowStatus.COMPLETED || 
            execution.status == WorkflowStatus.FAILED ||
            execution.status == WorkflowStatus.CANCELLED) {
            return false;
        }
        
        execution.status = WorkflowStatus.CANCELLED;
        execution.endTime = Instant.now();
        synchronized (execution) {
            execution.notifyAll();
        }
        
        execution.completionFuture.complete(execution);
        fireEvent(execution, WorkflowEvent.cancelled(executionId));
        return true;
    }

    /**
     * Gets all active workflow executions.
     *
     * @return list of active executions
     */
    public List<WorkflowExecution> getActiveExecutions() {
        return executions.values().stream()
            .filter(e -> e.status == WorkflowStatus.RUNNING || e.status == WorkflowStatus.PAUSED)
            .toList();
    }

    // ==================== Configuration ====================

    /**
     * Sets the step execution delay.
     *
     * @param delayMs delay in milliseconds
     * @return this simulator for chaining
     */
    public InMemoryWorkflowEngineSimulator setStepExecutionDelayMs(long delayMs) {
        this.stepExecutionDelayMs = delayMs;
        return this;
    }

    /**
     * Sets the default step duration.
     *
     * @param durationMs duration in milliseconds
     * @return this simulator for chaining
     */
    public InMemoryWorkflowEngineSimulator setDefaultStepDurationMs(long durationMs) {
        this.defaultStepDurationMs = durationMs;
        return this;
    }

    /**
     * Sets the step callback.
     *
     * @param callback callback invoked for each step transition
     * @return this simulator for chaining
     */
    public InMemoryWorkflowEngineSimulator setStepCallback(
            BiConsumer<WorkflowStep, StepStatus> callback) {
        this.stepCallback = callback;
        return this;
    }

    /**
     * Sets the step output generator.
     *
     * @param generator function that generates step outputs
     * @return this simulator for chaining
     */
    public InMemoryWorkflowEngineSimulator setStepOutputGenerator(
            Function<WorkflowStep, Map<String, Object>> generator) {
        this.stepOutputGenerator = generator;
        return this;
    }

    /**
     * Sets the global failure mode.
     *
     * @param mode the failure mode
     * @return this simulator for chaining
     */
    public InMemoryWorkflowEngineSimulator setFailureMode(WorkflowFailureMode mode) {
        this.failureMode = mode;
        return this;
    }

    /**
     * Sets a specific step to fail.
     *
     * @param stepName the step name to fail
     * @return this simulator for chaining
     */
    public InMemoryWorkflowEngineSimulator setFailAtStep(String stepName) {
        this.failAtStep = stepName;
        return this;
    }

    /**
     * Sets a failure mode for a specific step.
     *
     * @param stepName the step name
     * @param mode the failure mode
     * @return this simulator for chaining
     */
    public InMemoryWorkflowEngineSimulator setStepFailureMode(
            String stepName, WorkflowFailureMode mode) {
        stepFailures.put(stepName, mode);
        return this;
    }

    /**
     * Clears a step-specific failure mode.
     *
     * @param stepName the step name
     * @return this simulator for chaining
     */
    public InMemoryWorkflowEngineSimulator clearStepFailureMode(String stepName) {
        stepFailures.remove(stepName);
        return this;
    }

    /**
     * Sets the step failure rate for random failures.
     *
     * @param rate failure rate (0.0 to 1.0)
     * @return this simulator for chaining
     */
    public InMemoryWorkflowEngineSimulator setStepFailureRate(double rate) {
        this.stepFailureRate = Math.max(0.0, Math.min(1.0, rate));
        return this;
    }

    /**
     * Resets all chaos settings.
     *
     * @return this simulator for chaining
     */
    public InMemoryWorkflowEngineSimulator reset() {
        this.failureMode = WorkflowFailureMode.NONE;
        this.failAtStep = null;
        this.stepFailureRate = 0.0;
        stepFailures.clear();
        return this;
    }

    // ==================== Statistics ====================

    /**
     * Gets total execution count.
     *
     * @return total executions
     */
    public long getTotalExecutions() {
        return totalExecutions.get();
    }

    /**
     * Gets completed execution count.
     *
     * @return completed executions
     */
    public long getCompletedExecutions() {
        return completedExecutions.get();
    }

    /**
     * Gets failed execution count.
     *
     * @return failed executions
     */
    public long getFailedExecutions() {
        return failedExecutions.get();
    }

    /**
     * Resets statistics.
     */
    public void resetStatistics() {
        totalExecutions.set(0);
        completedExecutions.set(0);
        failedExecutions.set(0);
    }

    // ==================== Lifecycle ====================

    /**
     * Shuts down the workflow engine.
     */
    public void shutdown() {
        // Cancel all running executions
        executions.values().stream()
            .filter(e -> e.status == WorkflowStatus.RUNNING || e.status == WorkflowStatus.PAUSED)
            .forEach(e -> cancel(e.executionId));
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Clears all executions.
     */
    public void clear() {
        executions.clear();
        resetStatistics();
    }

    // ==================== Private Implementation ====================

    private enum ExecutionMode {
        NORMAL, DRY_RUN, VIRTUAL
    }

    private CompletableFuture<WorkflowExecution> executeInternal(
            WorkflowDefinition definition,
            ExecutionContext context,
            ExecutionMode mode) {
        
        String executionId = generateExecutionId();
        WorkflowExecution execution = new WorkflowExecution(
            executionId,
            definition,
            context,
            mode
        );
        
        executions.put(executionId, execution);
        totalExecutions.incrementAndGet();
        
        CompletableFuture.runAsync(() -> {
            try {
                runWorkflow(execution);
            } catch (Exception e) {
                execution.status = WorkflowStatus.FAILED;
                execution.error = e.getMessage();
                execution.endTime = Instant.now();
                failedExecutions.incrementAndGet();
                execution.completionFuture.completeExceptionally(e);
            }
        }, scheduler);
        
        return execution.completionFuture;
    }

    private void runWorkflow(WorkflowExecution execution) {
        execution.startTime = Instant.now();
        
        // Validation phase
        execution.status = WorkflowStatus.VALIDATING;
        fireEvent(execution, WorkflowEvent.validating(execution.executionId));
        
        // Check for validation failure
        if (failureMode == WorkflowFailureMode.VALIDATION_FAILURE) {
            execution.status = WorkflowStatus.FAILED;
            execution.error = "Workflow validation failed (simulated)";
            execution.endTime = Instant.now();
            failedExecutions.incrementAndGet();
            execution.completionFuture.complete(execution);
            return;
        }
        
        // Validate workflow
        try {
            validateWorkflow(execution.definition);
        } catch (Exception e) {
            execution.status = WorkflowStatus.FAILED;
            execution.error = "Validation error: " + e.getMessage();
            execution.endTime = Instant.now();
            failedExecutions.incrementAndGet();
            execution.completionFuture.complete(execution);
            return;
        }
        
        // Dry run stops after validation
        if (execution.mode == ExecutionMode.DRY_RUN) {
            execution.status = WorkflowStatus.COMPLETED;
            execution.endTime = Instant.now();
            completedExecutions.incrementAndGet();
            execution.completionFuture.complete(execution);
            fireEvent(execution, WorkflowEvent.completed(execution.executionId));
            return;
        }
        
        // Execution phase
        execution.status = WorkflowStatus.RUNNING;
        fireEvent(execution, WorkflowEvent.started(execution.executionId));
        
        // Execute steps in order
        List<WorkflowStep> steps = execution.definition.steps();
        Map<String, StepExecution> stepExecutions = new LinkedHashMap<>();
        execution.stepExecutions = stepExecutions;
        
        for (WorkflowStep step : steps) {
            // Check for cancellation or pause
            while (execution.status == WorkflowStatus.PAUSED) {
                synchronized (execution) {
                    try {
                        execution.wait(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        execution.status = WorkflowStatus.CANCELLED;
                        break;
                    }
                }
            }
            
            if (execution.status == WorkflowStatus.CANCELLED) {
                break;
            }
            
            // Execute step
            StepExecution stepExecution = executeStep(execution, step);
            stepExecutions.put(step.name(), stepExecution);
            
            if (stepExecution.status == StepStatus.FAILED) {
                // Check if step is required
                if (step.required()) {
                    execution.status = WorkflowStatus.FAILED;
                    execution.error = "Required step failed: " + step.name();
                    execution.endTime = Instant.now();
                    failedExecutions.incrementAndGet();
                    execution.completionFuture.complete(execution);
                    fireEvent(execution, WorkflowEvent.failed(execution.executionId, 
                        execution.error));
                    return;
                }
            }
        }
        
        // Check final status
        if (execution.status == WorkflowStatus.CANCELLED) {
            execution.endTime = Instant.now();
            execution.completionFuture.complete(execution);
            return;
        }
        
        execution.status = WorkflowStatus.COMPLETED;
        execution.endTime = Instant.now();
        completedExecutions.incrementAndGet();
        execution.completionFuture.complete(execution);
        fireEvent(execution, WorkflowEvent.completed(execution.executionId));
    }

    private StepExecution executeStep(WorkflowExecution execution, WorkflowStep step) {
        StepExecution stepExecution = new StepExecution(step);
        stepExecution.startTime = Instant.now();
        stepExecution.status = StepStatus.RUNNING;
        
        // Notify callback
        notifyStepCallback(step, StepStatus.RUNNING);
        fireEvent(execution, WorkflowEvent.stepStarted(execution.executionId, step.name()));
        
        try {
            // Check for step-specific failure
            checkStepFailure(step.name());
            
            // Simulate step execution time
            long stepDuration = step.estimatedDurationMs() > 0 ? 
                step.estimatedDurationMs() : defaultStepDurationMs;
            
            if (execution.mode == ExecutionMode.VIRTUAL) {
                // Virtual mode - minimal delay
                Thread.sleep(stepExecutionDelayMs);
            } else {
                // Normal mode - full simulated duration
                Thread.sleep(stepDuration);
            }
            
            // Generate step output
            if (stepOutputGenerator != null) {
                stepExecution.outputs = stepOutputGenerator.apply(step);
            } else {
                stepExecution.outputs = generateDefaultOutput(step);
            }
            
            stepExecution.status = StepStatus.COMPLETED;
            stepExecution.endTime = Instant.now();
            
            notifyStepCallback(step, StepStatus.COMPLETED);
            fireEvent(execution, WorkflowEvent.stepCompleted(execution.executionId, step.name()));
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stepExecution.status = StepStatus.FAILED;
            stepExecution.error = "Step interrupted";
            stepExecution.endTime = Instant.now();
            notifyStepCallback(step, StepStatus.FAILED);
            
        } catch (Exception e) {
            stepExecution.status = StepStatus.FAILED;
            stepExecution.error = e.getMessage();
            stepExecution.endTime = Instant.now();
            notifyStepCallback(step, StepStatus.FAILED);
            fireEvent(execution, WorkflowEvent.stepFailed(execution.executionId, 
                step.name(), e.getMessage()));
        }
        
        return stepExecution;
    }

    private void validateWorkflow(WorkflowDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("Workflow definition cannot be null");
        }
        if (definition.name() == null || definition.name().isBlank()) {
            throw new IllegalArgumentException("Workflow name is required");
        }
        if (definition.steps() == null || definition.steps().isEmpty()) {
            throw new IllegalArgumentException("Workflow must have at least one step");
        }
        
        // Validate step dependencies
        Set<String> stepNames = new HashSet<>();
        for (WorkflowStep step : definition.steps()) {
            if (step.name() == null || step.name().isBlank()) {
                throw new IllegalArgumentException("Step name is required");
            }
            if (!stepNames.add(step.name())) {
                throw new IllegalArgumentException("Duplicate step name: " + step.name());
            }
        }
        
        // Check dependency references
        for (WorkflowStep step : definition.steps()) {
            if (step.dependsOn() != null) {
                for (String dependency : step.dependsOn()) {
                    if (!stepNames.contains(dependency)) {
                        throw new IllegalArgumentException(
                            "Step " + step.name() + " depends on unknown step: " + dependency);
                    }
                }
            }
        }
    }

    private void checkStepFailure(String stepName) throws Exception {
        // Check step-specific failure
        WorkflowFailureMode stepMode = stepFailures.get(stepName);
        if (stepMode != null && stepMode != WorkflowFailureMode.NONE) {
            throw new RuntimeException("Step failure (simulated): " + stepMode);
        }
        
        // Check global failure at step
        if (stepName.equals(failAtStep)) {
            if (failureMode == WorkflowFailureMode.STEP_FAILURE) {
                throw new RuntimeException("Step failure at " + stepName + " (simulated)");
            }
        }
        
        // Check random failure
        if (failureMode == WorkflowFailureMode.RANDOM_FAILURE ||
            stepFailureRate > 0) {
            if (ThreadLocalRandom.current().nextDouble() < stepFailureRate) {
                throw new RuntimeException("Random step failure (simulated)");
            }
        }
        
        // Check resource unavailable
        if (failureMode == WorkflowFailureMode.RESOURCE_UNAVAILABLE) {
            throw new RuntimeException("Resource unavailable (simulated)");
        }
    }

    private Map<String, Object> generateDefaultOutput(WorkflowStep step) {
        Map<String, Object> output = new HashMap<>();
        output.put("stepName", step.name());
        output.put("status", "completed");
        output.put("executedAt", Instant.now().toString());
        
        // Add step type specific outputs
        if (step.type() != null) {
            switch (step.type()) {
                case "transfer" -> {
                    output.put("bytesTransferred", 1000000L);
                    output.put("transferDuration", "PT1S");
                }
                case "transform" -> {
                    output.put("recordsProcessed", 1000);
                    output.put("transformDuration", "PT0.5S");
                }
                case "validate" -> {
                    output.put("validationPassed", true);
                    output.put("checksPerformed", 5);
                }
            }
        }
        
        return output;
    }

    private void notifyStepCallback(WorkflowStep step, StepStatus status) {
        if (stepCallback != null) {
            try {
                stepCallback.accept(step, status);
            } catch (Exception e) {
                // Ignore callback errors
            }
        }
    }

    private void fireEvent(WorkflowExecution execution, WorkflowEvent event) {
        if (execution.eventCallback != null) {
            try {
                execution.eventCallback.accept(event);
            } catch (Exception e) {
                // Ignore callback errors
            }
        }
    }

    private String generateExecutionId() {
        return "wf-" + System.nanoTime() + "-" + 
            ThreadLocalRandom.current().nextInt(10000);
    }

    // ==================== Inner Classes ====================

    /**
     * Workflow definition.
     */
    public record WorkflowDefinition(
        String name,
        String description,
        List<WorkflowStep> steps,
        Map<String, Object> variables,
        WorkflowOptions options
    ) {
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String name;
            private String description;
            private List<WorkflowStep> steps = new ArrayList<>();
            private Map<String, Object> variables = new HashMap<>();
            private WorkflowOptions options;
            
            public Builder name(String name) {
                this.name = name;
                return this;
            }
            
            public Builder description(String description) {
                this.description = description;
                return this;
            }
            
            public Builder step(WorkflowStep step) {
                this.steps.add(step);
                return this;
            }
            
            public Builder steps(List<WorkflowStep> steps) {
                this.steps = new ArrayList<>(steps);
                return this;
            }
            
            public Builder variable(String name, Object value) {
                this.variables.put(name, value);
                return this;
            }
            
            public Builder options(WorkflowOptions options) {
                this.options = options;
                return this;
            }
            
            public WorkflowDefinition build() {
                return new WorkflowDefinition(name, description, steps, variables, options);
            }
        }
    }

    /**
     * Workflow step definition.
     */
    public record WorkflowStep(
        String name,
        String type,
        String description,
        Map<String, Object> config,
        List<String> dependsOn,
        boolean required,
        long estimatedDurationMs
    ) {
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String name;
            private String type;
            private String description;
            private Map<String, Object> config = new HashMap<>();
            private List<String> dependsOn = new ArrayList<>();
            private boolean required = true;
            private long estimatedDurationMs = 0;
            
            public Builder name(String name) {
                this.name = name;
                return this;
            }
            
            public Builder type(String type) {
                this.type = type;
                return this;
            }
            
            public Builder description(String description) {
                this.description = description;
                return this;
            }
            
            public Builder config(String key, Object value) {
                this.config.put(key, value);
                return this;
            }
            
            public Builder dependsOn(String... steps) {
                this.dependsOn.addAll(Arrays.asList(steps));
                return this;
            }
            
            public Builder required(boolean required) {
                this.required = required;
                return this;
            }
            
            public Builder estimatedDurationMs(long duration) {
                this.estimatedDurationMs = duration;
                return this;
            }
            
            public WorkflowStep build() {
                return new WorkflowStep(name, type, description, config, 
                    dependsOn, required, estimatedDurationMs);
            }
        }
    }

    /**
     * Workflow options.
     */
    public record WorkflowOptions(
        boolean stopOnError,
        long timeoutMs,
        int maxRetries,
        boolean parallel
    ) {
        public static WorkflowOptions defaults() {
            return new WorkflowOptions(true, 3600000, 0, false);
        }
    }

    /**
     * Execution context.
     */
    public record ExecutionContext(
        Map<String, Object> variables,
        Map<String, Object> environment,
        java.util.function.Consumer<WorkflowEvent> eventCallback
    ) {
        public static ExecutionContext empty() {
            return new ExecutionContext(Map.of(), Map.of(), null);
        }
    }

    /**
     * Workflow execution state.
     */
    public static class WorkflowExecution {
        final String executionId;
        final WorkflowDefinition definition;
        final ExecutionContext context;
        final ExecutionMode mode;
        final CompletableFuture<WorkflowExecution> completionFuture = new CompletableFuture<>();
        final java.util.function.Consumer<WorkflowEvent> eventCallback;
        
        volatile WorkflowStatus status = WorkflowStatus.PENDING;
        volatile Instant startTime;
        volatile Instant endTime;
        volatile String error;
        volatile Map<String, StepExecution> stepExecutions;

        WorkflowExecution(String executionId, WorkflowDefinition definition, 
                ExecutionContext context, ExecutionMode mode) {
            this.executionId = executionId;
            this.definition = definition;
            this.context = context;
            this.mode = mode;
            this.eventCallback = context.eventCallback();
        }

        public String getExecutionId() { return executionId; }
        public WorkflowDefinition getDefinition() { return definition; }
        public WorkflowStatus getStatus() { return status; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public String getError() { return error; }
        
        public Map<String, StepExecution> getStepExecutions() {
            return stepExecutions != null ? new HashMap<>(stepExecutions) : Map.of();
        }

        public Duration getDuration() {
            if (startTime == null) return Duration.ZERO;
            Instant end = endTime != null ? endTime : Instant.now();
            return Duration.between(startTime, end);
        }

        public boolean isComplete() {
            return status == WorkflowStatus.COMPLETED || 
                   status == WorkflowStatus.FAILED || 
                   status == WorkflowStatus.CANCELLED;
        }
    }

    /**
     * Step execution state.
     */
    public static class StepExecution {
        final WorkflowStep step;
        volatile StepStatus status = StepStatus.PENDING;
        volatile Instant startTime;
        volatile Instant endTime;
        volatile String error;
        volatile Map<String, Object> outputs;

        StepExecution(WorkflowStep step) {
            this.step = step;
        }

        public WorkflowStep getStep() { return step; }
        public StepStatus getStatus() { return status; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public String getError() { return error; }
        public Map<String, Object> getOutputs() { return outputs; }

        public Duration getDuration() {
            if (startTime == null) return Duration.ZERO;
            Instant end = endTime != null ? endTime : Instant.now();
            return Duration.between(startTime, end);
        }
    }

    /**
     * Workflow event.
     */
    public record WorkflowEvent(
        String executionId,
        EventType type,
        String stepName,
        String message
    ) {
        public enum EventType {
            VALIDATING, STARTED, PAUSED, RESUMED, COMPLETED, FAILED, CANCELLED,
            STEP_STARTED, STEP_COMPLETED, STEP_FAILED
        }

        static WorkflowEvent validating(String executionId) {
            return new WorkflowEvent(executionId, EventType.VALIDATING, null, null);
        }

        static WorkflowEvent started(String executionId) {
            return new WorkflowEvent(executionId, EventType.STARTED, null, null);
        }

        static WorkflowEvent paused(String executionId) {
            return new WorkflowEvent(executionId, EventType.PAUSED, null, null);
        }

        static WorkflowEvent resumed(String executionId) {
            return new WorkflowEvent(executionId, EventType.RESUMED, null, null);
        }

        static WorkflowEvent completed(String executionId) {
            return new WorkflowEvent(executionId, EventType.COMPLETED, null, null);
        }

        static WorkflowEvent failed(String executionId, String error) {
            return new WorkflowEvent(executionId, EventType.FAILED, null, error);
        }

        static WorkflowEvent cancelled(String executionId) {
            return new WorkflowEvent(executionId, EventType.CANCELLED, null, null);
        }

        static WorkflowEvent stepStarted(String executionId, String stepName) {
            return new WorkflowEvent(executionId, EventType.STEP_STARTED, stepName, null);
        }

        static WorkflowEvent stepCompleted(String executionId, String stepName) {
            return new WorkflowEvent(executionId, EventType.STEP_COMPLETED, stepName, null);
        }

        static WorkflowEvent stepFailed(String executionId, String stepName, String error) {
            return new WorkflowEvent(executionId, EventType.STEP_FAILED, stepName, error);
        }
    }
}
