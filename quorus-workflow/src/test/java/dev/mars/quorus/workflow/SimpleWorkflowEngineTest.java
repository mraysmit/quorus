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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SimpleWorkflowEngine functionality.
 *
 * NOTE: These tests have been updated to comply with the new YAML schema validation requirements.
 * All test workflows now include complete metadata with required fields:
 * - name: Descriptive workflow name (2-100 characters)
 * - version: Semantic version (e.g., "1.0.0")
 * - description: Workflow description (10-500 characters)
 * - type: Workflow type (e.g., "validation-test-workflow")
 * - author: Email address or name
 * - created: ISO date format (YYYY-MM-DD)
 * - tags: Array of lowercase tags with hyphens
 *
 * Tests that intentionally fail validation are clearly marked and documented.
 */
class SimpleWorkflowEngineTest {

    private TestTransferEngine testTransferEngine;
    private SimpleWorkflowEngine workflowEngine;
    private WorkflowDefinition testWorkflow;
    private ExecutionContext testContext;

    @BeforeEach
    void setUp() {
        testTransferEngine = new TestTransferEngine();
        testTransferEngine.simulateSuccess(); // Default to success behavior
        workflowEngine = new SimpleWorkflowEngine(testTransferEngine);
        
        // Create a test workflow
        testWorkflow = createTestWorkflow();
        testContext = ExecutionContext.builder()
                .executionId("test-execution-123")
                .mode(ExecutionContext.ExecutionMode.NORMAL)
                .variables(Map.of("baseUrl", "https://test.com", "outputDir", "/tmp"))
                .userId("test-user")
                .build();
    }
    
    @Test
    void testNormalExecution() throws Exception {
        testTransferEngine.simulateSuccess();

        Future<WorkflowExecution> future = workflowEngine.execute(testWorkflow, testContext);
        WorkflowExecution execution = future.toCompletionStage().toCompletableFuture().get();

        assertNotNull(execution);
        assertEquals("test-execution-123", execution.getExecutionId());
        assertEquals(WorkflowStatus.COMPLETED, execution.getStatus());
        assertTrue(execution.isSuccessful());
        assertEquals(1, execution.getGroupExecutions().size());

        WorkflowExecution.GroupExecution groupExecution = execution.getGroupExecutions().get(0);
        assertEquals("test-group", groupExecution.getGroupName());
        assertEquals(WorkflowStatus.COMPLETED, groupExecution.getStatus());
        assertTrue(groupExecution.isSuccessful());
        assertEquals(1, groupExecution.getTransferResults().size());
    }
    
    @Test
    void testDryRun() throws Exception {
        Future<WorkflowExecution> future = workflowEngine.dryRun(testWorkflow, testContext);
        WorkflowExecution execution = future.toCompletionStage().toCompletableFuture().get();

        assertNotNull(execution);
        assertEquals(WorkflowStatus.COMPLETED, execution.getStatus());
        assertTrue(execution.isSuccessful());
        assertEquals(1, execution.getGroupExecutions().size());

        // Verify no actual transfers were executed
        assertEquals(0, testTransferEngine.getActiveTransferCount());
    }
    
    @Test
    void testVirtualRun() throws Exception {
        Future<WorkflowExecution> future = workflowEngine.virtualRun(testWorkflow, testContext);
        WorkflowExecution execution = future.toCompletionStage().toCompletableFuture().get();

        assertNotNull(execution);
        assertEquals(WorkflowStatus.COMPLETED, execution.getStatus());
        assertTrue(execution.isSuccessful());
        assertEquals(1, execution.getGroupExecutions().size());

        // Verify no actual transfers were executed
        assertEquals(0, testTransferEngine.getActiveTransferCount());

        // Virtual run should take some time due to simulation
        assertTrue(execution.getDuration().isPresent());
        assertTrue(execution.getDuration().get().toMillis() >= 100);
    }
    
    @Test
    void testFailedTransfer() throws Exception {
        testTransferEngine.simulateFailure();

        Future<WorkflowExecution> future = workflowEngine.execute(testWorkflow, testContext);
        WorkflowExecution execution = future.toCompletionStage().toCompletableFuture().get();

        assertNotNull(execution);
        assertEquals(WorkflowStatus.FAILED, execution.getStatus());
        assertFalse(execution.isSuccessful());

        WorkflowExecution.GroupExecution groupExecution = execution.getGroupExecutions().get(0);
        assertEquals(WorkflowStatus.FAILED, groupExecution.getStatus());
        assertFalse(groupExecution.isSuccessful());
    }
    
    @Test
    void testTransferException() throws Exception {
        testTransferEngine.simulateException(new RuntimeException("Transfer failed"));

        Future<WorkflowExecution> future = workflowEngine.execute(testWorkflow, testContext);
        WorkflowExecution execution = future.toCompletionStage().toCompletableFuture().get();

        assertNotNull(execution);
        assertEquals(WorkflowStatus.FAILED, execution.getStatus());
        assertFalse(execution.isSuccessful());
    }
    
    @Test
    void testGetStatus() throws Exception {
        testTransferEngine.simulateSuccess();

        Future<WorkflowExecution> future = workflowEngine.execute(testWorkflow, testContext);

        // Status should be null for unknown execution
        assertNull(workflowEngine.getStatus("unknown-execution"));

        // Wait for completion
        WorkflowExecution execution = future.toCompletionStage().toCompletableFuture().get();

        // Status should be null after completion (execution removed from active list)
        assertNull(workflowEngine.getStatus("test-execution-123"));
    }
    
    @Test
    void testCancel() {
        // Cancel should return false for unknown execution
        assertFalse(workflowEngine.cancel("unknown-execution"));
    }
    
    @Test
    void testPauseAndResume() {
        // Pause and resume are not supported in SimpleWorkflowEngine
        assertFalse(workflowEngine.pause("test-execution-123"));
        assertFalse(workflowEngine.resume("test-execution-123"));
    }
    
    @Test
    void testShutdown() {
        workflowEngine.shutdown();
        
        // After shutdown, new executions should fail
        Future<WorkflowExecution> future = workflowEngine.execute(testWorkflow, testContext);
        
        assertThrows(Exception.class, () -> {
            future.toCompletionStage().toCompletableFuture().get();
        });
    }
    
    @Test
    void testVariableResolution() throws Exception {
        testTransferEngine.simulateSuccess();

        // Create workflow with variables
        WorkflowDefinition workflowWithVars = createWorkflowWithVariables();

        Future<WorkflowExecution> future = workflowEngine.execute(workflowWithVars, testContext);
        WorkflowExecution execution = future.toCompletionStage().toCompletableFuture().get();

        assertTrue(execution.isSuccessful());
    }
    
    @Test
    void testInvalidWorkflow() throws Exception {
        // Create invalid workflow (missing required fields)
        WorkflowDefinition invalidWorkflow = createInvalidWorkflow();
        
        Future<WorkflowExecution> future = workflowEngine.execute(invalidWorkflow, testContext);
        WorkflowExecution execution = future.toCompletionStage().toCompletableFuture().get();
        
        assertEquals(WorkflowStatus.FAILED, execution.getStatus());
        assertTrue(execution.getErrorMessage().isPresent());
        assertTrue(execution.getErrorMessage().get().contains("validation failed"));
    }
    
    /**
     * Creates a test workflow with complete metadata that satisfies the new schema validation requirements.
     * All required metadata fields are included to ensure validation passes.
     */
    private WorkflowDefinition createTestWorkflow() {
        TransferGroup.TransferDefinition transfer = new TransferGroup.TransferDefinition(
                "test-transfer",
                "https://example.com/file.txt",
                "/tmp/file.txt",
                "http",
                Map.of(),
                null
        );

        TransferGroup group = new TransferGroup(
                "test-group",
                "Test group",
                List.of(),
                null,
                Map.of(),
                List.of(transfer),
                false,
                0
        );

        // Create metadata with all required fields for schema validation
        WorkflowDefinition.WorkflowMetadata metadata = new WorkflowDefinition.WorkflowMetadata(
                "Test Workflow Engine",                    // name - required, descriptive
                "1.0.0",                                   // version - required, semantic versioning
                "Test workflow for SimpleWorkflowEngine unit tests", // description - required, min 10 chars
                "validation-test-workflow",                // type - required, standard type
                "test@quorus.dev",                         // author - required, email format
                "2025-08-21",                              // created - required, ISO date
                List.of("test", "unit-test", "engine"),    // tags - required, valid format
                Map.of("environment", "test", "suite", "unit") // labels - optional
        );

        WorkflowDefinition.ExecutionConfig execution = new WorkflowDefinition.ExecutionConfig(
                false, false, 1, Duration.ofHours(1), "sequential"
        );

        WorkflowDefinition.WorkflowSpec spec = new WorkflowDefinition.WorkflowSpec(
                Map.of(),
                execution,
                List.of(group)
        );

        return new WorkflowDefinition("v1", "TransferWorkflow", metadata, spec);
    }
    
    /**
     * Creates a test workflow with variables and complete metadata for variable resolution testing.
     */
    private WorkflowDefinition createWorkflowWithVariables() {
        TransferGroup.TransferDefinition transfer = new TransferGroup.TransferDefinition(
                "test-transfer",
                "{{baseUrl}}/file.txt",
                "{{outputDir}}/file.txt",
                "http",
                Map.of(),
                null
        );

        TransferGroup group = new TransferGroup(
                "test-group",
                "Test group",
                List.of(),
                null,
                Map.of(),
                List.of(transfer),
                false,
                0
        );

        // Create metadata with all required fields for schema validation
        WorkflowDefinition.WorkflowMetadata metadata = new WorkflowDefinition.WorkflowMetadata(
                "Variable Resolution Test Workflow",       // name - required, descriptive
                "1.0.0",                                   // version - required, semantic versioning
                "Test workflow for variable resolution functionality", // description - required
                "validation-test-workflow",                // type - required, standard type
                "test@quorus.dev",                         // author - required, email format
                "2025-08-21",                              // created - required, ISO date
                List.of("test", "variables", "resolution"), // tags - required, valid format
                Map.of("environment", "test", "feature", "variables") // labels - optional
        );

        WorkflowDefinition.ExecutionConfig execution = new WorkflowDefinition.ExecutionConfig(
                false, false, 1, Duration.ofHours(1), "sequential"
        );

        WorkflowDefinition.WorkflowSpec spec = new WorkflowDefinition.WorkflowSpec(
                Map.of("baseUrl", "https://default.com", "outputDir", "/default"),
                execution,
                List.of(group)
        );

        return new WorkflowDefinition("v1", "TransferWorkflow", metadata, spec);
    }
    
    /**
     * Creates an intentionally invalid workflow for testing validation failure scenarios.
     * This workflow has multiple validation issues:
     * - Empty name (fails minimum length requirement)
     * - Missing required metadata fields (version, type, author, created, tags)
     * - Empty transfer groups
     *
     * This test verifies that the validation system correctly rejects invalid workflows.
     */
    private WorkflowDefinition createInvalidWorkflow() {
        WorkflowDefinition.WorkflowMetadata metadata = new WorkflowDefinition.WorkflowMetadata(
                "",                                        // name - INTENTIONALLY INVALID (empty)
                "",                                        // version - INTENTIONALLY INVALID (empty)
                "Invalid workflow for testing",            // description - valid
                "",                                        // type - INTENTIONALLY INVALID (empty)
                "",                                        // author - INTENTIONALLY INVALID (empty)
                "",                                        // created - INTENTIONALLY INVALID (empty)
                List.of(),                                 // tags - INTENTIONALLY INVALID (empty)
                Map.of()                                   // labels - valid (optional)
        );

        WorkflowDefinition.ExecutionConfig execution = new WorkflowDefinition.ExecutionConfig(
                false, false, 1, Duration.ofHours(1), "sequential"
        );

        WorkflowDefinition.WorkflowSpec spec = new WorkflowDefinition.WorkflowSpec(
                Map.of(),
                execution,
                List.of() // Empty transfer groups - also invalid
        );

        return new WorkflowDefinition("v1", "TransferWorkflow", metadata, spec);
    }
}
