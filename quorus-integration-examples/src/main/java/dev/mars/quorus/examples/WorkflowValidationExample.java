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

package dev.mars.quorus.examples;

import dev.mars.quorus.examples.util.TestResultLogger;
import dev.mars.quorus.transfer.TransferEngine;
import dev.mars.quorus.transfer.SimpleTransferEngine;
import dev.mars.quorus.workflow.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Workflow validation example demonstrating:
 * 1. YAML schema validation
 * 2. Semantic workflow validation
 * 3. Dependency graph validation
 * 4. Dry run execution for validation
 * 5. Variable resolution validation
 * 6. Error handling and reporting
 *
 * IMPORTANT: This example intentionally tests failure scenarios to demonstrate
 * validation capabilities. Expected failures are clearly marked with ✓ symbols
 * and do not indicate actual problems with the system.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 1.0
 */
public class WorkflowValidationExample {
    
    public static void main(String[] args) {
        System.out.println("=== Quorus Workflow Validation Example ===");
        System.out.println("This example demonstrates validation capabilities by testing both");
        System.out.println("valid workflows and intentional failure scenarios.");
        System.out.println("Expected failures are marked with ✓ and are part of the demonstration.");
        System.out.println();

        try {
            WorkflowValidationExample example = new WorkflowValidationExample();
            example.runExample();
            TestResultLogger.logExampleCompletion("Workflow Validation Example");
        } catch (Exception e) {
            // This catch block is for UNEXPECTED errors only
            // Intentional test failures are handled within runExample()
            TestResultLogger.logUnexpectedError("Workflow Validation Example", e);
            System.err.println("\nFull stack trace:");
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public void runExample() throws Exception {
        // Setup
        TransferEngine transferEngine = new SimpleTransferEngine();
        SimpleWorkflowEngine workflowEngine = new SimpleWorkflowEngine(transferEngine);
        WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
        
        TestResultLogger.logTestSection("1. Testing valid workflow validation", false);
        testValidWorkflow(parser, workflowEngine);

        TestResultLogger.logTestSection("2. Testing invalid YAML syntax", true);
        testInvalidYamlSyntax(parser);

        TestResultLogger.logTestSection("3. Testing missing required fields", true);
        testMissingRequiredFields(parser);

        TestResultLogger.logTestSection("4. Testing circular dependencies", true);
        testCircularDependencies(parser);

        TestResultLogger.logTestSection("5. Testing missing dependencies", true);
        testMissingDependencies(parser);

        TestResultLogger.logTestSection("6. Testing variable resolution issues", true);
        testVariableResolutionIssues(parser, workflowEngine);

        TestResultLogger.logTestSection("7. Testing dry run validation", false);
        testDryRunValidation(parser, workflowEngine);

        // Cleanup
        workflowEngine.shutdown();
    }
    
    private void testValidWorkflow(WorkflowDefinitionParser parser, SimpleWorkflowEngine workflowEngine) 
            throws Exception {
        
        String validWorkflow = """
                apiVersion: v1
                kind: TransferWorkflow
                metadata:
                  name: valid-workflow
                  description: A valid workflow for testing
                spec:
                  variables:
                    baseUrl: "https://httpbin.org"
                    outputDir: "/tmp"
                  execution:
                    parallelism: 1
                  transferGroups:
                    - name: group1
                      transfers:
                        - name: transfer1
                          source: "{{baseUrl}}/json"
                          destination: "{{outputDir}}/data.json"
                          protocol: http
                """;
        
        try {
            // Parse workflow
            WorkflowDefinition workflow = parser.parseFromString(validWorkflow);
            System.out.println("   ✓ YAML parsing successful");
            
            // Validate workflow
            ValidationResult validation = parser.validate(workflow);
            if (validation.isValid()) {
                TestResultLogger.logExpectedSuccess("Workflow validation successful");
            } else {
                TestResultLogger.logUnexpectedResult("Workflow validation failed:");
                validation.getErrors().forEach(error ->
                    System.out.println("     - " + error.getMessage()));
            }

            // Test dependency graph
            DependencyGraph graph = parser.buildDependencyGraph(java.util.List.of(workflow));
            ValidationResult graphValidation = graph.validate();
            if (graphValidation.isValid()) {
                TestResultLogger.logExpectedSuccess("Dependency graph validation successful");
            } else {
                TestResultLogger.logUnexpectedResult("Dependency graph validation failed");
            }

        } catch (Exception e) {
            TestResultLogger.logUnexpectedException("Validation failed", e);
        }
    }
    
    private void testInvalidYamlSyntax(WorkflowDefinitionParser parser) {
        String invalidYaml = """
                apiVersion: v1
                kind: TransferWorkflow
                metadata:
                  name: invalid-yaml
                  description: [unclosed bracket
                spec:
                  invalid: yaml: syntax
                """;

        try {
            parser.parseFromString(invalidYaml);
            TestResultLogger.logUnexpectedResult("Should have failed but didn't - this indicates a bug!");
        } catch (WorkflowParseException e) {
            TestResultLogger.logExpectedFailure("Correctly caught YAML syntax error", e);
        } catch (Exception e) {
            TestResultLogger.logUnexpectedException("Got different exception type", e);
        }
    }
    
    private void testMissingRequiredFields(WorkflowDefinitionParser parser) {
        String missingFields = """
                apiVersion: v1
                kind: TransferWorkflow
                metadata:
                  name: ""
                spec:
                  execution:
                    parallelism: 1
                  transferGroups: []
                """;

        try {
            WorkflowDefinition workflow = parser.parseFromString(missingFields);
            TestResultLogger.logUnexpectedResult("Should have failed during parsing but didn't - this indicates a bug!");
        } catch (WorkflowParseException e) {
            TestResultLogger.logExpectedFailure("Correctly caught missing required fields", e);
        } catch (Exception e) {
            TestResultLogger.logUnexpectedException("Got different exception type", e);
        }
    }
    
    private void testCircularDependencies(WorkflowDefinitionParser parser) {
        String circularDeps = """
                apiVersion: v1
                kind: TransferWorkflow
                metadata:
                  name: circular-deps
                spec:
                  execution:
                    parallelism: 1
                  transferGroups:
                    - name: group1
                      dependsOn:
                        - group2
                      transfers:
                        - name: transfer1
                          source: "http://example.com/file1"
                          destination: "/tmp/file1"
                    - name: group2
                      dependsOn:
                        - group1
                      transfers:
                        - name: transfer2
                          source: "http://example.com/file2"
                          destination: "/tmp/file2"
                """;

        try {
            WorkflowDefinition workflow = parser.parseFromString(circularDeps);
            DependencyGraph graph = parser.buildDependencyGraph(java.util.List.of(workflow));

            if (graph.hasCycles()) {
                TestResultLogger.logExpectedFailure("Correctly detected circular dependencies");
            } else {
                TestResultLogger.logUnexpectedResult("Failed to detect circular dependencies - this indicates a bug!");
            }

        } catch (WorkflowParseException e) {
            TestResultLogger.logExpectedFailure("Correctly caught circular dependency error", e);
        } catch (Exception e) {
            TestResultLogger.logUnexpectedException("Got different exception type", e);
        }
    }
    
    private void testMissingDependencies(WorkflowDefinitionParser parser) {
        String missingDeps = """
                apiVersion: v1
                kind: TransferWorkflow
                metadata:
                  name: missing-deps
                spec:
                  execution:
                    parallelism: 1
                  transferGroups:
                    - name: group1
                      dependsOn:
                        - nonexistent-group
                      transfers:
                        - name: transfer1
                          source: "http://example.com/file1"
                          destination: "/tmp/file1"
                """;

        try {
            WorkflowDefinition workflow = parser.parseFromString(missingDeps);
            DependencyGraph graph = parser.buildDependencyGraph(java.util.List.of(workflow));
            ValidationResult validation = graph.validate();

            if (!validation.isValid()) {
                TestResultLogger.logExpectedFailure("Correctly detected missing dependencies:");
                validation.getErrors().forEach(error ->
                    System.out.println("     - " + error.getMessage()));
            } else {
                TestResultLogger.logUnexpectedResult("Failed to detect missing dependencies - this indicates a bug!");
            }

        } catch (Exception e) {
            TestResultLogger.logExpectedFailure("Correctly caught missing dependency error", e);
        }
    }
    
    /**
     * Tests variable resolution validation.
     * This is an INTENTIONAL FAILURE TEST - the system should detect and reject undefined variables.
     */
    private void testVariableResolutionIssues(WorkflowDefinitionParser parser, SimpleWorkflowEngine workflowEngine)
            throws Exception {

        String workflowWithVars = """
                apiVersion: v1
                kind: TransferWorkflow
                metadata:
                  name: variable-test
                spec:
                  execution:
                    parallelism: 1
                  transferGroups:
                    - name: group1
                      transfers:
                        - name: transfer1
                          source: "{{undefinedVariable}}/file"
                          destination: "{{anotherUndefinedVar}}/file"
                """;

        try {
            WorkflowDefinition workflow = parser.parseFromString(workflowWithVars);

            ExecutionContext context = ExecutionContext.builder()
                    .executionId("var-test-" + System.currentTimeMillis())
                    .mode(ExecutionContext.ExecutionMode.DRY_RUN)
                    .variables(Map.of()) // Empty variables - should cause resolution errors
                    .build();

            CompletableFuture<WorkflowExecution> future = workflowEngine.dryRun(workflow, context);
            WorkflowExecution execution = future.get();

            if (execution.getStatus() == WorkflowStatus.FAILED) {
                TestResultLogger.logExpectedFailure("Correctly detected variable resolution issues");
                if (execution.getErrorMessage().isPresent()) {
                    System.out.println("     Error: " + execution.getErrorMessage().get());
                }
            } else {
                TestResultLogger.logUnexpectedResult("Failed to detect variable resolution issues - this indicates a bug!");
            }

        } catch (Exception e) {
            TestResultLogger.logExpectedFailure("Correctly caught variable resolution error", e);
        }
    }
    
    private void testDryRunValidation(WorkflowDefinitionParser parser, SimpleWorkflowEngine workflowEngine) 
            throws Exception {
        
        String validWorkflow = """
                apiVersion: v1
                kind: TransferWorkflow
                metadata:
                  name: dry-run-test
                  description: Testing dry run validation
                spec:
                  variables:
                    baseUrl: "https://httpbin.org"
                    outputDir: "/tmp"
                  execution:
                    parallelism: 2
                  transferGroups:
                    - name: download-group
                      transfers:
                        - name: download-json
                          source: "{{baseUrl}}/json"
                          destination: "{{outputDir}}/test.json"
                          protocol: http
                        - name: download-xml
                          source: "{{baseUrl}}/xml"
                          destination: "{{outputDir}}/test.xml"
                          protocol: http
                    - name: process-group
                      dependsOn:
                        - download-group
                      transfers:
                        - name: process-data
                          source: "{{outputDir}}/test.json"
                          destination: "{{outputDir}}/processed.json"
                          protocol: file
                """;
        
        try {
            WorkflowDefinition workflow = parser.parseFromString(validWorkflow);
            
            ExecutionContext context = ExecutionContext.builder()
                    .executionId("dry-run-" + System.currentTimeMillis())
                    .mode(ExecutionContext.ExecutionMode.DRY_RUN)
                    .variables(Map.of(
                            "baseUrl", "https://httpbin.org",
                            "outputDir", "/tmp/dry-run-test"
                    ))
                    .build();
            
            System.out.println("   Performing dry run validation...");
            CompletableFuture<WorkflowExecution> future = workflowEngine.dryRun(workflow, context);
            WorkflowExecution execution = future.get();
            
            System.out.println("   Dry run status: " + execution.getStatus());
            System.out.println("   Groups validated: " + execution.getGroupExecutions().size());
            System.out.println("   Total transfers validated: " + execution.getTotalTransferCount());
            
            if (execution.isSuccessful()) {
                TestResultLogger.logExpectedSuccess("Dry run validation successful");

                // Show what would be executed
                System.out.println("   Execution plan:");
                execution.getGroupExecutions().forEach(group -> {
                    System.out.println("     Group: " + group.getGroupName());
                    group.getTransferResults().forEach((name, result) -> {
                        System.out.println("       Transfer: " + name + " -> " + result.getFinalStatus());
                    });
                });
            } else {
                TestResultLogger.logUnexpectedResult("Dry run validation failed");
                if (execution.getErrorMessage().isPresent()) {
                    System.out.println("     Error: " + execution.getErrorMessage().get());
                }
            }

        } catch (Exception e) {
            TestResultLogger.logUnexpectedException("Dry run failed", e);
        }
    }
}
