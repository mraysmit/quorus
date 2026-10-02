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
import org.junit.jupiter.api.Timeout;

import dev.mars.quorus.testing.ExpectsError;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SimpleWorkflowEngine functionality. The engine is blocking (RT-04): each call returns
 * the finished execution on the test thread.
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
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2025-08-18
 */
@Timeout(value = 30, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
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

        WorkflowExecution execution = workflowEngine.execute(testWorkflow, testContext);

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
        WorkflowExecution execution = workflowEngine.dryRun(testWorkflow, testContext);

        assertNotNull(execution);
        assertEquals(WorkflowStatus.COMPLETED, execution.getStatus());
        assertTrue(execution.isSuccessful());
        assertEquals(1, execution.getGroupExecutions().size());
        assertEquals(0, testTransferEngine.getMaxConcurrentTransfers(), "a dry run starts no transfer");
    }

    @Test
    void testVirtualRun() throws Exception {
        WorkflowExecution execution = workflowEngine.virtualRun(testWorkflow, testContext);

        assertNotNull(execution);
        assertEquals(WorkflowStatus.COMPLETED, execution.getStatus());
        assertTrue(execution.isSuccessful());
        assertEquals(1, execution.getGroupExecutions().size());
        assertEquals(0, testTransferEngine.getMaxConcurrentTransfers(), "a virtual run starts no transfer");
        assertTrue(execution.getDuration().isPresent());
        assertTrue(execution.getDuration().get().toMillis() >= 100);
    }

    @Test
    @ExpectsError("Simulated transfer failure -- verifies workflow marks group as FAILED")
    void testFailedTransfer() throws Exception {
        testTransferEngine.simulateFailure();

        WorkflowExecution execution = workflowEngine.execute(testWorkflow, testContext);

        assertNotNull(execution);
        assertEquals(WorkflowStatus.FAILED, execution.getStatus());
        assertFalse(execution.isSuccessful());

        WorkflowExecution.GroupExecution groupExecution = execution.getGroupExecutions().get(0);
        assertEquals(WorkflowStatus.FAILED, groupExecution.getStatus());
        assertFalse(groupExecution.isSuccessful());
    }

    @Test
    @ExpectsError("Simulated transfer exception -- verifies workflow catches and marks FAILED")
    void testTransferException() throws Exception {
        testTransferEngine.simulateException(new RuntimeException("Transfer failed"));

        WorkflowExecution execution = workflowEngine.execute(testWorkflow, testContext);

        assertNotNull(execution);
        assertEquals(WorkflowStatus.FAILED, execution.getStatus());
        assertFalse(execution.isSuccessful());
    }

    @Test
    void testGetStatus() throws Exception {
        testTransferEngine.simulateSuccess();

        // Status should be null for unknown execution
        assertNull(workflowEngine.getStatus("unknown-execution"));

        workflowEngine.execute(testWorkflow, testContext);

        assertNull(workflowEngine.getStatus("test-execution-123"), "a finished execution is no longer active");
    }

    @Test
    void testCancel() {
        // Cancel should return false for unknown execution
        assertFalse(workflowEngine.cancel("unknown-execution"));
    }

    @Test
    void cancelStopsARunningWorkflowAndReportsItCancelled() throws Exception {
        testTransferEngine.simulateDelay(Duration.ofSeconds(20));
        CompletableFuture<WorkflowExecution> result = new CompletableFuture<>();
        CompletableFuture<Boolean> interruptLeaked = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try {
                result.complete(workflowEngine.execute(testWorkflow, testContext));
                interruptLeaked.complete(Thread.currentThread().isInterrupted());
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        });
        assertTrue(testTransferEngine.awaitStarted(1, Duration.ofSeconds(10)), "the transfer should start");

        assertTrue(workflowEngine.cancel("test-execution-123"));

        WorkflowExecution execution = result.get(10, SECONDS);
        assertEquals(WorkflowStatus.CANCELLED, execution.getStatus());
        assertFalse(interruptLeaked.get(), "the caller's thread is not left interrupted");
        assertEquals(0, testTransferEngine.getActiveTransferCount(), "the running transfer was stopped");
        assertNull(workflowEngine.getStatus("test-execution-123"));
        assertFalse(workflowEngine.cancel("test-execution-123"), "a finished execution cannot be cancelled");
    }

    @Test
    void independentGroupsRunUpToTheParallelismLimit() throws Exception {
        testTransferEngine.simulateDelay(Duration.ofMillis(300));

        WorkflowExecution execution = workflowEngine.execute(workflow(2,
                group("a", List.of(), 1), group("b", List.of(), 1), group("c", List.of(), 1)), testContext);

        assertTrue(execution.isSuccessful());
        assertEquals(3, execution.getGroupExecutions().size());
        assertEquals(2, testTransferEngine.getMaxConcurrentTransfers(), "two groups at a time, never three");
    }

    @Test
    void parallelismOfOneRunsGroupsOneAtATime() throws Exception {
        testTransferEngine.simulateDelay(Duration.ofMillis(100));

        WorkflowExecution execution = workflowEngine.execute(workflow(1,
                group("a", List.of(), 1), group("b", List.of(), 1), group("c", List.of(), 1)), testContext);

        assertTrue(execution.isSuccessful());
        assertEquals(1, testTransferEngine.getMaxConcurrentTransfers());
    }

    @Test
    void aGroupStartsOnlyAfterTheGroupsItDependsOn() throws Exception {
        testTransferEngine.simulateDelay(Duration.ofMillis(200));

        WorkflowExecution execution = workflowEngine.execute(workflow(3,
                group("a", List.of(), 1), group("b", List.of("a"), 1), group("c", List.of(), 1)), testContext);

        assertTrue(execution.isSuccessful());
        WorkflowExecution.GroupExecution a = groupExecution(execution, "a");
        WorkflowExecution.GroupExecution b = groupExecution(execution, "b");
        assertFalse(b.getStartTime().isBefore(a.getEndTime().orElseThrow()), "b waits for a");
        assertEquals(2, testTransferEngine.getMaxConcurrentTransfers(), "a and c run together; b waits");
    }

    @Test
    void testShutdown() {
        workflowEngine.shutdown();

        // After shutdown, new executions are refused
        assertThrows(IllegalStateException.class, () -> workflowEngine.execute(testWorkflow, testContext));
    }

    @Test
    void transfersInAGroupRunInParallel() throws Exception {
        testTransferEngine.simulateDelay(Duration.ofMillis(300));

        WorkflowExecution execution = workflowEngine.execute(createWorkflow(Duration.ofHours(1), 3), testContext);

        assertTrue(execution.isSuccessful());
        assertEquals(3, execution.getGroupExecutions().get(0).getTransferResults().size());
        assertEquals(3, testTransferEngine.getMaxConcurrentTransfers(), "all three transfers overlap");
    }

    @Test
    @ExpectsError("Workflow timeout -- verifies an overrunning workflow fails and its transfers are stopped")
    void theWorkflowTimeoutFailsTheExecutionAndStopsItsTransfers() throws Exception {
        testTransferEngine.simulateDelay(Duration.ofSeconds(20));
        long started = System.nanoTime();

        WorkflowExecution execution = workflowEngine.execute(createWorkflow(Duration.ofMillis(200), 1), testContext);

        assertEquals(WorkflowStatus.FAILED, execution.getStatus());
        String error = execution.getErrorMessage().orElse("");
        assertTrue(error.contains("timed out"), error);
        assertTrue(Duration.ofNanos(System.nanoTime() - started).toSeconds() < 10, "the transfer was not waited for");
        assertEquals(0, testTransferEngine.getActiveTransferCount(), "the overrunning transfer was stopped");
        assertNull(workflowEngine.getStatus("test-execution-123"));
    }

    @Test
    void anInterruptedCallerStopsTheWorkflow() {
        testTransferEngine.simulateDelay(Duration.ofSeconds(20));
        Thread caller = Thread.currentThread();
        // Interrupt the caller once its transfer has started (a handshake, not a timed sleep).
        Thread.ofVirtual().start(() -> {
            try {
                if (testTransferEngine.awaitStarted(1, Duration.ofSeconds(10))) {
                    caller.interrupt();
                }
            } catch (InterruptedException ignored) {
                // Test ended.
            }
        });

        assertThrows(InterruptedException.class, () -> workflowEngine.execute(testWorkflow, testContext));

        assertEquals(0, testTransferEngine.getActiveTransferCount(), "the transfer was stopped");
        assertNull(workflowEngine.getStatus("test-execution-123"));
    }

    @Test
    void theDefinitionsDryRunFlagMakesExecuteADryRun() throws Exception {
        WorkflowExecution execution = workflowEngine.execute(workflow(
                new WorkflowDefinition.ExecutionConfig(true, false, 1, Duration.ofHours(1), "sequential"),
                group("g", List.of(), 2)), testContext);

        assertEquals(WorkflowStatus.COMPLETED, execution.getStatus());
        assertEquals(ExecutionContext.ExecutionMode.DRY_RUN, execution.getContext().getMode());
        assertEquals(0, testTransferEngine.getAttempts(), "a workflow declared dryRun must start no transfer");
    }

    @Test
    void theDefinitionsVirtualRunFlagMakesExecuteAVirtualRun() throws Exception {
        WorkflowExecution execution = workflowEngine.execute(workflow(
                new WorkflowDefinition.ExecutionConfig(false, true, 1, Duration.ofHours(1), "sequential"),
                group("g", List.of(), 2)), testContext);

        assertEquals(WorkflowStatus.COMPLETED, execution.getStatus());
        assertEquals(ExecutionContext.ExecutionMode.VIRTUAL_RUN, execution.getContext().getMode());
        assertEquals(0, testTransferEngine.getAttempts(), "a workflow declared virtualRun must start no transfer");
    }

    @Test
    void dryRunWinsWhenBothFlagsAreSet() throws Exception {
        WorkflowExecution execution = workflowEngine.virtualRun(workflow(
                new WorkflowDefinition.ExecutionConfig(true, true, 1, Duration.ofHours(1), "sequential"),
                group("g", List.of(), 1)), testContext);

        assertEquals(ExecutionContext.ExecutionMode.DRY_RUN, execution.getContext().getMode());
    }

    @Test
    void aGroupsRetryCountRetriesAFailedTransfer() throws Exception {
        testTransferEngine.simulateFailuresBeforeSuccess(2);

        WorkflowExecution execution = workflowEngine.execute(workflow(1, group("g", List.of(), 1, 2)), testContext);

        assertEquals(WorkflowStatus.COMPLETED, execution.getStatus());
        assertEquals(3, testTransferEngine.getAttempts(), "two failures, then the third attempt succeeds");
    }

    @Test
    @ExpectsError("Retries exhausted -- verifies the group fails after 1 + retryCount attempts")
    void aTransferFailsOnceItsRetriesAreSpent() throws Exception {
        testTransferEngine.simulateFailure();

        WorkflowExecution execution = workflowEngine.execute(workflow(1, group("g", List.of(), 1, 2)), testContext);

        assertEquals(WorkflowStatus.FAILED, execution.getStatus());
        assertEquals(3, testTransferEngine.getAttempts(), "one attempt and two retries");
    }

    @Test
    @ExpectsError("No retries -- verifies a retryCount of 0 means a single attempt")
    void aRetryCountOfZeroMeansOneAttempt() throws Exception {
        testTransferEngine.simulateFailuresBeforeSuccess(1);

        WorkflowExecution execution = workflowEngine.execute(workflow(1, group("g", List.of(), 1, 0)), testContext);

        assertEquals(WorkflowStatus.FAILED, execution.getStatus());
        assertEquals(1, testTransferEngine.getAttempts());
    }

    @Test
    void testVariableResolution() throws Exception {
        testTransferEngine.simulateSuccess();

        // Create workflow with variables
        WorkflowDefinition workflowWithVars = createWorkflowWithVariables();

        assertTrue(workflowEngine.execute(workflowWithVars, testContext).isSuccessful());
    }

    @Test
    @ExpectsError("Empty workflow name -- verifies validation rejects and returns FAILED status")
    void testInvalidWorkflow() throws Exception {
        // Create invalid workflow (missing required fields)
        WorkflowDefinition invalidWorkflow = createInvalidWorkflow();

        WorkflowExecution execution = workflowEngine.execute(invalidWorkflow, testContext);

        assertEquals(WorkflowStatus.FAILED, execution.getStatus());
        assertTrue(execution.getErrorMessage().isPresent());
        assertTrue(execution.getErrorMessage().get().contains("validation failed"));
    }

    /** A valid one-group workflow of {@code transfers} transfers with the given workflow timeout. */
    private WorkflowDefinition createWorkflow(Duration timeout, int transfers) {
        return workflow(timeout, 1, group("test-group", List.of(), transfers));
    }

    private WorkflowDefinition workflow(int parallelism, TransferGroup... groups) {
        return workflow(Duration.ofHours(1), parallelism, groups);
    }

    private WorkflowDefinition workflow(Duration timeout, int parallelism, TransferGroup... groups) {
        return workflow(new WorkflowDefinition.ExecutionConfig(false, false, parallelism, timeout, "parallel"), groups);
    }

    private WorkflowDefinition workflow(WorkflowDefinition.ExecutionConfig execution, TransferGroup... groups) {
        WorkflowDefinition base = createTestWorkflow();
        return new WorkflowDefinition("v1", base.getMetadata(),
                new WorkflowDefinition.WorkflowSpec(Map.of(), execution, List.of(groups)));
    }

    private static TransferGroup group(String name, List<String> dependsOn, int transfers) {
        return group(name, dependsOn, transfers, 0);
    }

    private static TransferGroup group(String name, List<String> dependsOn, int transfers, int retryCount) {
        List<TransferGroup.TransferDefinition> definitions = IntStream.rangeClosed(1, transfers)
                .mapToObj(i -> new TransferGroup.TransferDefinition(name + "-transfer-" + i,
                        "https://example.com/" + name + "-" + i + ".txt", "/tmp/" + name + "-" + i + ".txt",
                        "http", Map.of(), null))
                .toList();
        return new TransferGroup(name, "Test group " + name, dependsOn, null, Map.of(), definitions, false, retryCount);
    }

    private static WorkflowExecution.GroupExecution groupExecution(WorkflowExecution execution, String name) {
        return execution.getGroupExecutions().stream().filter(g -> g.getGroupName().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError("group " + name + " did not run"));
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
                "test-workflow-engine",                    // name - required, alphanumeric with hyphens
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

        return new WorkflowDefinition("v1", metadata, spec);
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
                "variable-resolution-test-workflow",       // name - required, alphanumeric with hyphens
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

        return new WorkflowDefinition("v1", metadata, spec);
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

        return new WorkflowDefinition("v1", metadata, spec);
    }
}
