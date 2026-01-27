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

package dev.mars.quorus.workflow.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * OpenTelemetry metrics for Quorus Workflow Engine.
 * Phase 9 of the OpenTelemetry migration.
 * 
 * Provides 9 workflow-specific metrics:
 * - quorus.workflow.active (gauge) - Currently active workflow executions
 * - quorus.workflow.total (counter) - Total workflows started
 * - quorus.workflow.completed (counter) - Successfully completed workflows
 * - quorus.workflow.failed (counter) - Failed workflows
 * - quorus.workflow.cancelled (counter) - Cancelled workflows
 * - quorus.workflow.steps.total (counter) - Total workflow steps executed
 * - quorus.workflow.steps.failed (counter) - Failed workflow steps
 * - quorus.workflow.duration.seconds (histogram) - Workflow duration distribution
 * - quorus.workflow.transfers.per_workflow (histogram) - Transfers per workflow
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-01-27
 * @version 1.0 (OpenTelemetry)
 */
public class WorkflowMetrics {

    private static final Logger logger = Logger.getLogger(WorkflowMetrics.class.getName());
    private static final String METER_NAME = "quorus-workflow";

    // Singleton instance
    private static WorkflowMetrics instance;

    // Counters
    private final LongCounter workflowsTotal;
    private final LongCounter workflowsCompleted;
    private final LongCounter workflowsFailed;
    private final LongCounter workflowsCancelled;
    private final LongCounter stepsTotal;
    private final LongCounter stepsFailed;

    // Histograms
    private final DoubleHistogram workflowDuration;
    private final DoubleHistogram transfersPerWorkflow;

    // Gauges (backed by AtomicLong)
    private final AtomicLong activeWorkflows = new AtomicLong(0);

    // Attribute keys
    private static final AttributeKey<String> WORKFLOW_NAME_KEY = AttributeKey.stringKey("workflow.name");
    private static final AttributeKey<String> EXECUTION_MODE_KEY = AttributeKey.stringKey("execution.mode");
    private static final AttributeKey<String> STEP_TYPE_KEY = AttributeKey.stringKey("step.type");
    private static final AttributeKey<String> FAILURE_REASON_KEY = AttributeKey.stringKey("failure.reason");

    private WorkflowMetrics() {
        Meter meter = GlobalOpenTelemetry.getMeter(METER_NAME);

        // Initialize counters
        workflowsTotal = meter.counterBuilder("quorus.workflow.total")
                .setDescription("Total number of workflows started")
                .setUnit("1")
                .build();

        workflowsCompleted = meter.counterBuilder("quorus.workflow.completed")
                .setDescription("Number of successfully completed workflows")
                .setUnit("1")
                .build();

        workflowsFailed = meter.counterBuilder("quorus.workflow.failed")
                .setDescription("Number of failed workflows")
                .setUnit("1")
                .build();

        workflowsCancelled = meter.counterBuilder("quorus.workflow.cancelled")
                .setDescription("Number of cancelled workflows")
                .setUnit("1")
                .build();

        stepsTotal = meter.counterBuilder("quorus.workflow.steps.total")
                .setDescription("Total number of workflow steps executed")
                .setUnit("1")
                .build();

        stepsFailed = meter.counterBuilder("quorus.workflow.steps.failed")
                .setDescription("Number of failed workflow steps")
                .setUnit("1")
                .build();

        // Initialize histograms
        workflowDuration = meter.histogramBuilder("quorus.workflow.duration.seconds")
                .setDescription("Workflow duration in seconds")
                .setUnit("s")
                .build();

        transfersPerWorkflow = meter.histogramBuilder("quorus.workflow.transfers.per_workflow")
                .setDescription("Number of transfers per workflow")
                .setUnit("1")
                .build();

        // Initialize gauges
        meter.gaugeBuilder("quorus.workflow.active")
                .setDescription("Number of currently active workflow executions")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(activeWorkflows.get()));

        logger.info("WorkflowMetrics initialized");
    }

    /**
     * Get the singleton instance of WorkflowMetrics.
     */
    public static synchronized WorkflowMetrics getInstance() {
        if (instance == null) {
            instance = new WorkflowMetrics();
        }
        return instance;
    }

    /**
     * Record a workflow started.
     */
    public void recordWorkflowStarted(String workflowName, String executionMode) {
        Attributes attrs = Attributes.builder()
                .put(WORKFLOW_NAME_KEY, workflowName)
                .put(EXECUTION_MODE_KEY, executionMode)
                .build();
        workflowsTotal.add(1, attrs);
        activeWorkflows.incrementAndGet();
    }

    /**
     * Record a workflow completed successfully.
     */
    public void recordWorkflowCompleted(String workflowName, String executionMode, 
                                        double durationSeconds, int transferCount) {
        activeWorkflows.decrementAndGet();
        
        Attributes attrs = Attributes.builder()
                .put(WORKFLOW_NAME_KEY, workflowName)
                .put(EXECUTION_MODE_KEY, executionMode)
                .build();
        
        workflowsCompleted.add(1, attrs);
        workflowDuration.record(durationSeconds, attrs);
        transfersPerWorkflow.record(transferCount, attrs);
    }

    /**
     * Record a workflow failed.
     */
    public void recordWorkflowFailed(String workflowName, String executionMode, String failureReason) {
        activeWorkflows.decrementAndGet();
        
        Attributes attrs = Attributes.builder()
                .put(WORKFLOW_NAME_KEY, workflowName)
                .put(EXECUTION_MODE_KEY, executionMode)
                .put(FAILURE_REASON_KEY, failureReason != null ? failureReason : "unknown")
                .build();
        
        workflowsFailed.add(1, attrs);
    }

    /**
     * Record a workflow cancelled.
     */
    public void recordWorkflowCancelled(String workflowName, String executionMode) {
        activeWorkflows.decrementAndGet();
        
        Attributes attrs = Attributes.builder()
                .put(WORKFLOW_NAME_KEY, workflowName)
                .put(EXECUTION_MODE_KEY, executionMode)
                .build();
        
        workflowsCancelled.add(1, attrs);
    }

    /**
     * Record a workflow step executed.
     */
    public void recordStepExecuted(String workflowName, String stepType) {
        Attributes attrs = Attributes.builder()
                .put(WORKFLOW_NAME_KEY, workflowName)
                .put(STEP_TYPE_KEY, stepType)
                .build();
        
        stepsTotal.add(1, attrs);
    }

    /**
     * Record a workflow step failed.
     */
    public void recordStepFailed(String workflowName, String stepType, String failureReason) {
        Attributes attrs = Attributes.builder()
                .put(WORKFLOW_NAME_KEY, workflowName)
                .put(STEP_TYPE_KEY, stepType)
                .put(FAILURE_REASON_KEY, failureReason != null ? failureReason : "unknown")
                .build();
        
        stepsFailed.add(1, attrs);
    }

    /**
     * Get the current number of active workflows.
     */
    public long getActiveWorkflows() {
        return activeWorkflows.get();
    }
}
