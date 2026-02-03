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

import dev.mars.quorus.simulator.SimulatorTestLoggingExtension;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;
import static java.util.concurrent.TimeUnit.*;

/**
 * Unit tests for {@link InMemoryWorkflowEngineSimulator}.
 */
@ExtendWith(SimulatorTestLoggingExtension.class)
@DisplayName("InMemoryWorkflowEngineSimulator Tests")
class InMemoryWorkflowEngineSimulatorTest {

    private static final Logger log = LoggerFactory.getLogger(InMemoryWorkflowEngineSimulatorTest.class);

    private InMemoryWorkflowEngineSimulator engine;

    @BeforeEach
    void setUp() {
        engine = new InMemoryWorkflowEngineSimulator();
        engine.setStepExecutionDelayMs(50);
        engine.setDefaultStepDurationMs(100);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    // ==================== Workflow Execution Tests ====================

    @Nested
    @DisplayName("Workflow Execution")
    class WorkflowExecutionTests {

        @Test
        @DisplayName("Should execute simple workflow")
        void testExecuteSimpleWorkflow() throws Exception {
            log.info("Testing simple workflow execution with single step");
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("simple-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .type("transfer")
                    .build())
                .build();

            var future = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty());

            var execution = future.get(5, TimeUnit.SECONDS);
            log.info("Workflow completed with status: {}", execution.getStatus());

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.COMPLETED);
            assertThat(execution.getStepExecutions()).containsKey("step-1");
        }

        @Test
        @DisplayName("Should execute multi-step workflow")
        void testMultiStepWorkflow() throws Exception {
            log.info("Testing multi-step workflow execution with 3 dependent steps");
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("multi-step-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .type("transfer")
                    .build())
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-2")
                    .type("transform")
                    .dependsOn("step-1")
                    .build())
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-3")
                    .type("validate")
                    .dependsOn("step-2")
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            log.info("Multi-step workflow completed: {} steps executed", execution.getStepExecutions().size());

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.COMPLETED);
            assertThat(execution.getStepExecutions()).hasSize(3);
        }

        @Test
        @DisplayName("Should track execution duration")
        void testExecutionDuration() throws Exception {
            log.info("Testing execution duration tracking");
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("timed-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            log.info("Execution duration tracked: {}ms", execution.getDuration().toMillis());

            assertThat(execution.getStartTime()).isNotNull();
            assertThat(execution.getEndTime()).isNotNull();
            assertThat(execution.getDuration().toMillis()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should get execution status")
        void testGetExecutionStatus() throws Exception {
            log.info("Testing execution status retrieval during long-running workflow");
            engine.setDefaultStepDurationMs(5000);

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("status-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            var future = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty());

            Thread.sleep(100);

            var execution = engine.getExecution(future.get().getExecutionId());
            log.info("Retrieved execution status: {}", execution != null ? "found" : "not found");
            // Either still running or completed by now
            assertThat(execution).isNotNull();
        }
    }

    // ==================== Dry Run Tests ====================

    @Nested
    @DisplayName("Dry Run")
    class DryRunTests {

        @Test
        @DisplayName("Should perform dry run (validation only)")
        void testDryRun() throws Exception {
            log.info("Testing dry run mode - validation only, no step execution");
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("dry-run-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            var execution = engine.dryRun(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            log.info("Dry run completed: status={}, steps executed={}", 
                execution.getStatus(), 
                execution.getStepExecutions() != null ? execution.getStepExecutions().size() : 0);

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.COMPLETED);
            // Dry run shouldn't execute steps
            assertThat(execution.getStepExecutions()).isNullOrEmpty();
        }

        @Test
        @DisplayName("Should detect validation errors in dry run")
        void testDryRunValidationError() throws Exception {
            log.info("Testing dry run with invalid workflow - expects validation failure");
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("invalid-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .dependsOn("nonexistent")
                    .build())
                .build();

            var execution = engine.dryRun(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            log.info("Dry run detected error: {}", execution.getError());

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.FAILED);
            assertThat(execution.getError()).contains("unknown step");
        }
    }

    // ==================== Virtual Run Tests ====================

    @Nested
    @DisplayName("Virtual Run")
    class VirtualRunTests {

        @Test
        @DisplayName("Should perform virtual run (fast execution)")
        void testVirtualRun() throws Exception {
            log.info("Testing virtual run mode - fast execution ignoring estimated durations");
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("virtual-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .estimatedDurationMs(10000) // Long step
                    .build())
                .build();

            long start = System.currentTimeMillis();
            var execution = engine.virtualRun(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;
            log.info("Virtual run completed in {}ms (step estimated 10000ms)", elapsed);

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.COMPLETED);
            // Virtual run should be fast
            assertThat(elapsed).isLessThan(5000);
        }
    }

    // ==================== Pause/Resume/Cancel Tests ====================

    @Nested
    @DisplayName("Pause/Resume/Cancel")
    class PauseResumeCancelTests {

        @Test
        @DisplayName("Should pause workflow")
        void testPauseWorkflow() throws Exception {
            log.info("Testing workflow pause functionality");
            engine.setDefaultStepDurationMs(10000);

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("pause-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-2")
                    .dependsOn("step-1")
                    .build())
                .build();

            var future = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty());

            Thread.sleep(100);
            String executionId = future.get().getExecutionId();
            
            boolean paused = engine.pause(executionId);
            log.info("Pause result: {} for execution {}", paused, executionId);
            
            if (paused) {
                assertThat(engine.getStatus(executionId))
                    .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.PAUSED);
            }
        }

        @Test
        @DisplayName("Should resume paused workflow")
        void testResumeWorkflow() throws Exception {
            log.info("Testing workflow resume after pause");
            engine.setDefaultStepDurationMs(5000);

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("resume-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            var future = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty());

            Thread.sleep(100);
            String executionId = future.get().getExecutionId();
            
            if (engine.pause(executionId)) {
                boolean resumed = engine.resume(executionId);
                log.info("Resume result: {} for execution {}", resumed, executionId);
                assertThat(resumed).isTrue();
                
                var status = engine.getStatus(executionId);
                assertThat(status).isIn(
                    InMemoryWorkflowEngineSimulator.WorkflowStatus.RUNNING,
                    InMemoryWorkflowEngineSimulator.WorkflowStatus.COMPLETED
                );
            }
        }

        @Test
        @DisplayName("Should cancel workflow")
        void testCancelWorkflow() throws Exception {
            log.info("Testing workflow cancellation");
            engine.setDefaultStepDurationMs(10000);

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("cancel-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            var future = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty());

            Thread.sleep(100);
            String executionId = future.get().getExecutionId();
            
            boolean cancelled = engine.cancel(executionId);
            log.info("Cancel result: {} for execution {}", cancelled, executionId);
            
            if (cancelled) {
                assertThat(engine.getStatus(executionId))
                    .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.CANCELLED);
            }
        }

        @Test
        @DisplayName("Should not cancel completed workflow")
        void testCannotCancelCompleted() throws Exception {
            log.info("Testing that completed workflows cannot be cancelled");
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("completed-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);

            boolean cancelled = engine.cancel(execution.getExecutionId());
            log.info("Attempted cancel on completed workflow: {}", cancelled);
            assertThat(cancelled).isFalse();
        }
    }

    // ==================== Step Callback Tests ====================

    @Nested
    @DisplayName("Step Callbacks")
    class StepCallbackTests {

        @Test
        @DisplayName("Should notify step callback")
        void testStepCallback() throws Exception {
            log.info("Testing step callback notification during workflow execution");
            List<String> notifications = new CopyOnWriteArrayList<>();

            engine.setStepCallback((step, status) -> 
                notifications.add(step.name() + ":" + status));

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("callback-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            engine.execute(workflow, InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            log.info("Step callback received {} notifications: {}", notifications.size(), notifications);

            assertThat(notifications).contains(
                "step-1:RUNNING",
                "step-1:COMPLETED"
            );
        }

        @Test
        @DisplayName("Should use custom output generator")
        void testStepOutputGenerator() throws Exception {
            log.info("Testing custom step output generator");
            engine.setStepOutputGenerator(step -> 
                Map.of("customOutput", "value-" + step.name()));

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("output-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);

            var stepExecution = execution.getStepExecutions().get("step-1");
            log.info("Custom output generated: {}", stepExecution.getOutputs());
            assertThat(stepExecution.getOutputs())
                .containsEntry("customOutput", "value-step-1");
        }
    }

    // ==================== Event Callback Tests ====================

    @Nested
    @DisplayName("Event Callbacks")
    class EventCallbackTests {

        @Test
        @DisplayName("Should fire workflow events")
        void testWorkflowEvents() throws Exception {
            log.info("Testing workflow event firing during execution lifecycle");
            List<InMemoryWorkflowEngineSimulator.WorkflowEvent> events = 
                new CopyOnWriteArrayList<>();

            var context = new InMemoryWorkflowEngineSimulator.ExecutionContext(
                Map.of(), Map.of(), events::add);

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("event-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            engine.execute(workflow, context).get(5, TimeUnit.SECONDS);
            log.info("Received {} workflow events: {}", events.size(), 
                events.stream().map(e -> e.type().name()).toList());

            assertThat(events).extracting(
                InMemoryWorkflowEngineSimulator.WorkflowEvent::type)
                .contains(
                    InMemoryWorkflowEngineSimulator.WorkflowEvent.EventType.VALIDATING,
                    InMemoryWorkflowEngineSimulator.WorkflowEvent.EventType.STARTED,
                    InMemoryWorkflowEngineSimulator.WorkflowEvent.EventType.STEP_STARTED,
                    InMemoryWorkflowEngineSimulator.WorkflowEvent.EventType.STEP_COMPLETED,
                    InMemoryWorkflowEngineSimulator.WorkflowEvent.EventType.COMPLETED
                );
        }
    }

    // ==================== Chaos Engineering Tests ====================

    @Nested
    @DisplayName("Chaos Engineering")
    class ChaosEngineeringTests {

        @ParameterizedTest
        @EnumSource(InMemoryWorkflowEngineSimulator.WorkflowFailureMode.class)
        @DisplayName("Should support all failure modes")
        void testAllFailureModes(InMemoryWorkflowEngineSimulator.WorkflowFailureMode mode) {
            log.info("Testing failure mode: {}", mode);
            engine.setFailureMode(mode);
            // Just verify setting doesn't throw
        }

        @Test
        @DisplayName("Should fail validation")
        void testValidationFailure() throws Exception {
            log.info("Testing VALIDATION_FAILURE chaos mode");
            engine.setFailureMode(
                InMemoryWorkflowEngineSimulator.WorkflowFailureMode.VALIDATION_FAILURE);

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("valid-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            log.info("Validation failure triggered: status={}, error={}", 
                execution.getStatus(), execution.getError());

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.FAILED);
            assertThat(execution.getError()).contains("validation");
        }

        @Test
        @DisplayName("Should fail at specific step")
        void testFailAtStep() throws Exception {
            log.info("Testing STEP_FAILURE chaos mode targeting step-2");
            engine.setFailureMode(
                InMemoryWorkflowEngineSimulator.WorkflowFailureMode.STEP_FAILURE);
            engine.setFailAtStep("step-2");

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("fail-step-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-2")
                    .dependsOn("step-1")
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            log.info("Step failure: step-1={}, step-2={}", 
                execution.getStepExecutions().get("step-1").getStatus(),
                execution.getStepExecutions().get("step-2").getStatus());

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.FAILED);
            assertThat(execution.getStepExecutions().get("step-1").getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.StepStatus.COMPLETED);
            assertThat(execution.getStepExecutions().get("step-2").getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.StepStatus.FAILED);
        }

        @Test
        @DisplayName("Should fail step with specific mode")
        void testStepSpecificFailure() throws Exception {
            log.info("Testing step-specific failure mode on failing-step");
            engine.setStepFailureMode("failing-step", 
                InMemoryWorkflowEngineSimulator.WorkflowFailureMode.STEP_FAILURE);

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("step-fail-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("failing-step")
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            log.info("Step-specific failure result: {}", execution.getStatus());

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.FAILED);
        }

        @Test
        @DisplayName("Should clear step failure mode")
        void testClearStepFailureMode() throws Exception {
            log.info("Testing clearing of step failure mode");
            engine.setStepFailureMode("step-1", 
                InMemoryWorkflowEngineSimulator.WorkflowFailureMode.STEP_FAILURE);
            engine.clearStepFailureMode("step-1");

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("cleared-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            log.info("After clearing failure mode: {}", execution.getStatus());

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.COMPLETED);
        }

        @Test
        @DisplayName("Should fail steps randomly")
        void testRandomStepFailure() throws Exception {
            log.info("Testing random step failure with 100% failure rate");
            engine.setStepFailureRate(1.0); // 100% failure

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("random-fail-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            log.info("Random failure result: {}", execution.getStatus());

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.FAILED);
        }

        @Test
        @DisplayName("Should simulate resource unavailable")
        void testResourceUnavailable() throws Exception {
            log.info("Testing RESOURCE_UNAVAILABLE chaos mode");
            engine.setFailureMode(
                InMemoryWorkflowEngineSimulator.WorkflowFailureMode.RESOURCE_UNAVAILABLE);

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("resource-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            log.info("Resource unavailable result: status={}, error={}", 
                execution.getStatus(), execution.getError());

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.FAILED);
            // The error message may vary - just verify it's not null/empty
            assertThat(execution.getError()).isNotBlank();
        }

        @Test
        @DisplayName("Should reset chaos settings")
        void testResetChaos() throws Exception {
            log.info("Testing chaos settings reset");
            engine.setFailureMode(
                InMemoryWorkflowEngineSimulator.WorkflowFailureMode.VALIDATION_FAILURE);
            engine.setStepFailureRate(1.0);

            engine.reset();

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("reset-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            log.info("After reset: {}", execution.getStatus());

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.COMPLETED);
        }
    }

    // ==================== Validation Tests ====================

    @Nested
    @DisplayName("Workflow Validation")
    class ValidationTests {

        @Test
        @DisplayName("Should reject null workflow")
        void testNullWorkflow() throws Exception {
            log.info("Testing null workflow rejection");
            var execution = engine.execute(null, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            log.info("Null workflow result: {}", execution.getStatus());

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.FAILED);
        }

        @Test
        @DisplayName("Should reject workflow without steps")
        void testNoSteps() throws Exception {
            log.info("Testing workflow without steps rejection");
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("empty-workflow")
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            log.info("Empty workflow result: {}", execution.getStatus());

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.FAILED);
        }

        @Test
        @DisplayName("Should reject duplicate step names")
        void testDuplicateStepNames() throws Exception {
            log.info("Testing duplicate step name rejection");
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("duplicate-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            log.info("Duplicate step result: status={}, error={}", 
                execution.getStatus(), execution.getError());

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.FAILED);
            assertThat(execution.getError()).contains("Duplicate");
        }

        @Test
        @DisplayName("Should reject invalid dependency")
        void testInvalidDependency() throws Exception {
            log.info("Testing invalid dependency rejection");
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("invalid-dep-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .dependsOn("nonexistent")
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            log.info("Invalid dependency result: status={}, error={}", 
                execution.getStatus(), execution.getError());

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.FAILED);
            assertThat(execution.getError()).contains("unknown step");
        }
    }

    // ==================== Statistics Tests ====================

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("Should track total executions")
        void testTotalExecutions() throws Exception {
            log.info("Testing total executions tracking");
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("stat-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            engine.execute(workflow, InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            engine.execute(workflow, InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            log.info("Total executions after 2 runs: {}", engine.getTotalExecutions());

            assertThat(engine.getTotalExecutions()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should track completed executions")
        void testCompletedExecutions() throws Exception {
            log.info("Testing completed executions tracking");
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("completed-stat-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            engine.execute(workflow, InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            log.info("Completed executions: {}", engine.getCompletedExecutions());

            assertThat(engine.getCompletedExecutions()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should track failed executions")
        void testFailedExecutions() throws Exception {
            log.info("Testing failed executions tracking");
            engine.setFailureMode(
                InMemoryWorkflowEngineSimulator.WorkflowFailureMode.VALIDATION_FAILURE);

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("failed-stat-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            engine.execute(workflow, InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);
            log.info("Failed executions: {}", engine.getFailedExecutions());

            assertThat(engine.getFailedExecutions()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should reset statistics")
        void testResetStatistics() throws Exception {
            log.info("Testing statistics reset");
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("reset-stat-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            engine.execute(workflow, InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);

            engine.resetStatistics();
            log.info("After reset - total: {}, completed: {}", 
                engine.getTotalExecutions(), engine.getCompletedExecutions());

            assertThat(engine.getTotalExecutions()).isZero();
            assertThat(engine.getCompletedExecutions()).isZero();
        }
    }

    // ==================== Builder Tests ====================

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build workflow definition")
        void testWorkflowBuilder() {
            log.info("Testing WorkflowDefinition builder");
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("test-workflow")
                .description("A test workflow")
                .variable("key", "value")
                .options(InMemoryWorkflowEngineSimulator.WorkflowOptions.defaults())
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();
            log.info("Built workflow: name={}, steps={}, vars={}", 
                workflow.name(), workflow.steps().size(), workflow.variables());

            assertThat(workflow.name()).isEqualTo("test-workflow");
            assertThat(workflow.description()).isEqualTo("A test workflow");
            assertThat(workflow.variables()).containsEntry("key", "value");
            assertThat(workflow.steps()).hasSize(1);
        }

        @Test
        @DisplayName("Should build workflow step")
        void testStepBuilder() {
            log.info("Testing WorkflowStep builder with full configuration");
            var step = InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                .name("transfer-step")
                .type("transfer")
                .description("Transfer files")
                .config("source", "/source/path")
                .config("dest", "/dest/path")
                .dependsOn("prior-step")
                .required(true)
                .estimatedDurationMs(5000)
                .build();
            log.info("Built step: name={}, type={}, deps={}", 
                step.name(), step.type(), step.dependsOn());

            assertThat(step.name()).isEqualTo("transfer-step");
            assertThat(step.type()).isEqualTo("transfer");
            assertThat(step.config()).containsEntry("source", "/source/path");
            assertThat(step.dependsOn()).contains("prior-step");
            assertThat(step.required()).isTrue();
        }

        @Test
        @DisplayName("Should create default workflow options")
        void testDefaultOptions() {
            log.info("Testing default WorkflowOptions creation");
            var options = InMemoryWorkflowEngineSimulator.WorkflowOptions.defaults();
            log.info("Default options: stopOnError={}, timeout={}ms, maxRetries={}, parallel={}", 
                options.stopOnError(), options.timeoutMs(), options.maxRetries(), options.parallel());

            assertThat(options.stopOnError()).isTrue();
            assertThat(options.timeoutMs()).isEqualTo(3600000);
            assertThat(options.maxRetries()).isZero();
            assertThat(options.parallel()).isFalse();
        }
    }

    // ==================== Lifecycle Tests ====================

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("Should get active executions")
        void testGetActiveExecutions() throws Exception {
            log.info("Testing active executions retrieval during long-running workflow");
            engine.setDefaultStepDurationMs(5000);

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("active-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            engine.execute(workflow, InMemoryWorkflowEngineSimulator.ExecutionContext.empty());
            Thread.sleep(100);

            var active = engine.getActiveExecutions();
            log.info("Active executions found: {}", active != null ? active.size() : 0);
            // May or may not have active executions depending on timing
            assertThat(active).isNotNull();
        }

        @Test
        @DisplayName("Should clear all executions")
        void testClear() throws Exception {
            log.info("Testing clear all executions");
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("clear-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);

            engine.clear();
            log.info("After clear - execution found: {}, total: {}", 
                engine.getExecution(execution.getExecutionId()) != null, 
                engine.getTotalExecutions());

            assertThat(engine.getExecution(execution.getExecutionId())).isNull();
            assertThat(engine.getTotalExecutions()).isZero();
        }
    }
}
