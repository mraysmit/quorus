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

import dev.mars.quorus.examples.util.ExampleLogger;
import dev.mars.quorus.transfer.TransferEngine;
import dev.mars.quorus.transfer.SimpleTransferEngine;
import dev.mars.quorus.workflow.*;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.Map;

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
 * @since 2025-08-18
 * @version 1.0
 */
public class WorkflowValidationExample {
    
    private static final ExampleLogger log = ExampleLogger.getLogger(WorkflowValidationExample.class);
    
    public static void main(String[] args) {
        log.exampleStart("Workflow Validation Example");
        log.info("This example demonstrates validation capabilities by testing both");
        log.info("valid workflows and intentional failure scenarios.");
        log.info("Expected failures are marked with ✓ and are part of the demonstration.");
        log.blank();

        try {
            WorkflowValidationExample example = new WorkflowValidationExample();
            example.runExample();
            log.exampleComplete("Workflow Validation Example");
        } catch (Exception e) {
            // This catch block is for UNEXPECTED errors only
            // Intentional test failures are handled within runExample()
            log.unexpectedError("Workflow Validation Example", e);
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public void runExample() throws Exception {
        // Setup with Vert.x
        Vertx vertx = Vertx.vertx();
        
        try {
            TransferEngine transferEngine = new SimpleTransferEngine(vertx, 10, 3, 1024 * 1024);
            SimpleWorkflowEngine workflowEngine = new SimpleWorkflowEngine(vertx, transferEngine);
            WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
            
            log.testSection("1. Testing valid workflow validation", false);
            testValidWorkflow(parser, workflowEngine);

            log.testSection("2. Testing invalid YAML syntax", true);
            testInvalidYamlSyntax(parser);

            log.testSection("3. Testing missing required fields", true);
            testMissingRequiredFields(parser);

            log.testSection("4. Testing circular dependencies", true);
            testCircularDependencies(parser);

            log.testSection("5. Testing missing dependencies", true);
            testMissingDependencies(parser);

            log.testSection("6. Testing variable resolution issues", true);
            testVariableResolutionIssues(parser, workflowEngine);

            log.testSection("7. Testing dry run validation", false);
            testDryRunValidation(parser, workflowEngine);

            // Cleanup
            workflowEngine.shutdown();
        } finally {
            vertx.close();
        }
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
            log.success("YAML parsing successful");
            
            // Validate workflow
            ValidationResult validation = parser.validate(workflow);
            if (validation.isValid()) {
                log.expectedSuccess("Workflow validation successful");
            } else {
                log.failure("UNEXPECTED: Workflow validation failed:");
                validation.getErrors().forEach(error ->
                    log.subDetail(error.getMessage()));
            }

            // Test dependency graph
            DependencyGraph graph = parser.buildDependencyGraph(java.util.List.of(workflow));
            ValidationResult graphValidation = graph.validate();
            if (graphValidation.isValid()) {
                log.expectedSuccess("Dependency graph validation successful");
            } else {
                log.failure("UNEXPECTED: Dependency graph validation failed");
            }

        } catch (Exception e) {
            log.failure("UNEXPECTED: Validation failed: " + e.getMessage());
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
            log.failure("UNEXPECTED: Should have failed but didn't - this indicates a bug!");
        } catch (WorkflowParseException e) {
            log.expectedFailure("Correctly caught YAML syntax error: " + e.getMessage());
        } catch (Exception e) {
            log.failure("UNEXPECTED: Got different exception type: " + e.getClass().getSimpleName());
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
            log.failure("UNEXPECTED: Should have failed during parsing but didn't - this indicates a bug!");
        } catch (WorkflowParseException e) {
            log.expectedFailure("Correctly caught missing required fields: " + e.getMessage());
        } catch (Exception e) {
            log.failure("UNEXPECTED: Got different exception type: " + e.getClass().getSimpleName());
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
                log.expectedFailure("Correctly detected circular dependencies");
            } else {
                log.failure("UNEXPECTED: Failed to detect circular dependencies - this indicates a bug!");
            }

        } catch (WorkflowParseException e) {
            log.expectedFailure("Correctly caught circular dependency error: " + e.getMessage());
        } catch (Exception e) {
            log.failure("UNEXPECTED: Got different exception type: " + e.getClass().getSimpleName());
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
                log.expectedFailure("Correctly detected missing dependencies:");
                validation.getErrors().forEach(error ->
                    log.subDetail(error.getMessage()));
            } else {
                log.failure("UNEXPECTED: Failed to detect missing dependencies - this indicates a bug!");
            }

        } catch (Exception e) {
            log.expectedFailure("Correctly caught missing dependency error: " + e.getMessage());
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

            Future<WorkflowExecution> future = workflowEngine.dryRun(workflow, context);
            WorkflowExecution execution = future.toCompletionStage().toCompletableFuture().get();

            if (execution.getStatus() == WorkflowStatus.FAILED) {
                log.expectedFailure("Correctly detected variable resolution issues");
                if (execution.getErrorMessage().isPresent()) {
                    log.subDetail("Error: " + execution.getErrorMessage().get());
                }
            } else {
                log.failure("UNEXPECTED: Failed to detect variable resolution issues - this indicates a bug!");
            }

        } catch (Exception e) {
            log.expectedFailure("Correctly caught variable resolution error: " + e.getMessage());
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
            
            log.detail("Performing dry run validation...");
            Future<WorkflowExecution> future = workflowEngine.dryRun(workflow, context);
            WorkflowExecution execution = future.toCompletionStage().toCompletableFuture().get();
            
            log.keyValue("Dry run status", execution.getStatus());
            log.keyValue("Groups validated", execution.getGroupExecutions().size());
            log.keyValue("Total transfers validated", execution.getTotalTransferCount());
            
            if (execution.isSuccessful()) {
                log.expectedSuccess("Dry run validation successful");

                // Show what would be executed
                log.detail("Execution plan:");
                execution.getGroupExecutions().forEach(group -> {
                    log.subDetail("Group: " + group.getGroupName());
                    group.getTransferResults().forEach((name, result) -> {
                        log.deepDetail("Transfer: " + name + " -> " + result.getFinalStatus());
                    });
                });
            } else {
                log.failure("UNEXPECTED: Dry run validation failed");
                if (execution.getErrorMessage().isPresent()) {
                    log.subDetail("Error: " + execution.getErrorMessage().get());
                }
            }

        } catch (Exception e) {
            log.failure("UNEXPECTED: Dry run failed: " + e.getMessage());
        }
    }
}
