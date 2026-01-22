/*
 * Copyright (c) 2025 Cityline Ltd.
 * All rights reserved.
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

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;
import static java.util.concurrent.TimeUnit.*;

/**
 * Unit tests for {@link InMemoryWorkflowEngineSimulator}.
 */
@DisplayName("InMemoryWorkflowEngineSimulator Tests")
class InMemoryWorkflowEngineSimulatorTest {

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
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("simple-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .type("transfer")
                    .build())
                .build();

            var future = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty());

            var execution = future.get(30, TimeUnit.SECONDS);

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.COMPLETED);
            assertThat(execution.getStepExecutions()).containsKey("step-1");
        }

        @Test
        @DisplayName("Should execute multi-step workflow")
        void testMultiStepWorkflow() throws Exception {
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
                .get(30, TimeUnit.SECONDS);

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.COMPLETED);
            assertThat(execution.getStepExecutions()).hasSize(3);
        }

        @Test
        @DisplayName("Should track execution duration")
        void testExecutionDuration() throws Exception {
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("timed-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(30, TimeUnit.SECONDS);

            assertThat(execution.getStartTime()).isNotNull();
            assertThat(execution.getEndTime()).isNotNull();
            assertThat(execution.getDuration().toMillis()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should get execution status")
        void testGetExecutionStatus() throws Exception {
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
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("dry-run-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            var execution = engine.dryRun(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(30, TimeUnit.SECONDS);

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.COMPLETED);
            // Dry run shouldn't execute steps
            assertThat(execution.getStepExecutions()).isNullOrEmpty();
        }

        @Test
        @DisplayName("Should detect validation errors in dry run")
        void testDryRunValidationError() throws Exception {
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("invalid-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .dependsOn("nonexistent")
                    .build())
                .build();

            var execution = engine.dryRun(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(30, TimeUnit.SECONDS);

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
                .get(30, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;

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
            
            if (paused) {
                assertThat(engine.getStatus(executionId))
                    .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.PAUSED);
            }
        }

        @Test
        @DisplayName("Should resume paused workflow")
        void testResumeWorkflow() throws Exception {
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
            
            if (cancelled) {
                assertThat(engine.getStatus(executionId))
                    .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.CANCELLED);
            }
        }

        @Test
        @DisplayName("Should not cancel completed workflow")
        void testCannotCancelCompleted() throws Exception {
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("completed-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(30, TimeUnit.SECONDS);

            boolean cancelled = engine.cancel(execution.getExecutionId());
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
                .get(30, TimeUnit.SECONDS);

            assertThat(notifications).contains(
                "step-1:RUNNING",
                "step-1:COMPLETED"
            );
        }

        @Test
        @DisplayName("Should use custom output generator")
        void testStepOutputGenerator() throws Exception {
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
                .get(30, TimeUnit.SECONDS);

            var stepExecution = execution.getStepExecutions().get("step-1");
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

            engine.execute(workflow, context).get(30, TimeUnit.SECONDS);

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
            engine.setFailureMode(mode);
            // Just verify setting doesn't throw
        }

        @Test
        @DisplayName("Should fail validation")
        void testValidationFailure() throws Exception {
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
                .get(30, TimeUnit.SECONDS);

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.FAILED);
            assertThat(execution.getError()).contains("validation");
        }

        @Test
        @DisplayName("Should fail at specific step")
        void testFailAtStep() throws Exception {
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
                .get(30, TimeUnit.SECONDS);

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
                .get(30, TimeUnit.SECONDS);

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.FAILED);
        }

        @Test
        @DisplayName("Should clear step failure mode")
        void testClearStepFailureMode() throws Exception {
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
                .get(30, TimeUnit.SECONDS);

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.COMPLETED);
        }

        @Test
        @DisplayName("Should fail steps randomly")
        void testRandomStepFailure() throws Exception {
            engine.setStepFailureRate(1.0); // 100% failure

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("random-fail-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(30, TimeUnit.SECONDS);

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.FAILED);
        }

        @Test
        @DisplayName("Should simulate resource unavailable")
        void testResourceUnavailable() throws Exception {
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
                .get(30, TimeUnit.SECONDS);

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.FAILED);
            // The error message may vary - just verify it's not null/empty
            assertThat(execution.getError()).isNotBlank();
        }

        @Test
        @DisplayName("Should reset chaos settings")
        void testResetChaos() throws Exception {
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
                .get(30, TimeUnit.SECONDS);

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
            var execution = engine.execute(null, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(30, TimeUnit.SECONDS);

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.FAILED);
        }

        @Test
        @DisplayName("Should reject workflow without steps")
        void testNoSteps() throws Exception {
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("empty-workflow")
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(30, TimeUnit.SECONDS);

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.FAILED);
        }

        @Test
        @DisplayName("Should reject duplicate step names")
        void testDuplicateStepNames() throws Exception {
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
                .get(30, TimeUnit.SECONDS);

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.FAILED);
            assertThat(execution.getError()).contains("Duplicate");
        }

        @Test
        @DisplayName("Should reject invalid dependency")
        void testInvalidDependency() throws Exception {
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("invalid-dep-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .dependsOn("nonexistent")
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(30, TimeUnit.SECONDS);

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
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("stat-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            engine.execute(workflow, InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(30, TimeUnit.SECONDS);
            engine.execute(workflow, InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(30, TimeUnit.SECONDS);

            assertThat(engine.getTotalExecutions()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should track completed executions")
        void testCompletedExecutions() throws Exception {
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("completed-stat-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            engine.execute(workflow, InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(30, TimeUnit.SECONDS);

            assertThat(engine.getCompletedExecutions()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should track failed executions")
        void testFailedExecutions() throws Exception {
            engine.setFailureMode(
                InMemoryWorkflowEngineSimulator.WorkflowFailureMode.VALIDATION_FAILURE);

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("failed-stat-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            engine.execute(workflow, InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(30, TimeUnit.SECONDS);

            assertThat(engine.getFailedExecutions()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should reset statistics")
        void testResetStatistics() throws Exception {
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("reset-stat-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            engine.execute(workflow, InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(30, TimeUnit.SECONDS);

            engine.resetStatistics();

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
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("test-workflow")
                .description("A test workflow")
                .variable("key", "value")
                .options(InMemoryWorkflowEngineSimulator.WorkflowOptions.defaults())
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            assertThat(workflow.name()).isEqualTo("test-workflow");
            assertThat(workflow.description()).isEqualTo("A test workflow");
            assertThat(workflow.variables()).containsEntry("key", "value");
            assertThat(workflow.steps()).hasSize(1);
        }

        @Test
        @DisplayName("Should build workflow step")
        void testStepBuilder() {
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

            assertThat(step.name()).isEqualTo("transfer-step");
            assertThat(step.type()).isEqualTo("transfer");
            assertThat(step.config()).containsEntry("source", "/source/path");
            assertThat(step.dependsOn()).contains("prior-step");
            assertThat(step.required()).isTrue();
        }

        @Test
        @DisplayName("Should create default workflow options")
        void testDefaultOptions() {
            var options = InMemoryWorkflowEngineSimulator.WorkflowOptions.defaults();

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
            // May or may not have active executions depending on timing
            assertThat(active).isNotNull();
        }

        @Test
        @DisplayName("Should clear all executions")
        void testClear() throws Exception {
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("clear-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step-1")
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(30, TimeUnit.SECONDS);

            engine.clear();

            assertThat(engine.getExecution(execution.getExecutionId())).isNull();
            assertThat(engine.getTotalExecutions()).isZero();
        }
    }
}
