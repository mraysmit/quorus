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
 */
public class WorkflowValidationExample {
    
    public static void main(String[] args) {
        System.out.println("=== Quorus Workflow Validation Example ===");
        
        try {
            WorkflowValidationExample example = new WorkflowValidationExample();
            example.runExample();
        } catch (Exception e) {
            System.err.println("Example failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public void runExample() throws Exception {
        // Setup
        TransferEngine transferEngine = new SimpleTransferEngine();
        SimpleWorkflowEngine workflowEngine = new SimpleWorkflowEngine(transferEngine);
        WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
        
        System.out.println("1. Testing valid workflow validation...");
        testValidWorkflow(parser, workflowEngine);
        
        System.out.println("\n2. Testing invalid YAML syntax...");
        testInvalidYamlSyntax(parser);
        
        System.out.println("\n3. Testing missing required fields...");
        testMissingRequiredFields(parser);
        
        System.out.println("\n4. Testing circular dependencies...");
        testCircularDependencies(parser);
        
        System.out.println("\n5. Testing missing dependencies...");
        testMissingDependencies(parser);
        
        System.out.println("\n6. Testing variable resolution issues...");
        testVariableResolutionIssues(parser, workflowEngine);
        
        System.out.println("\n7. Testing dry run validation...");
        testDryRunValidation(parser, workflowEngine);
        
        // Cleanup
        workflowEngine.shutdown();
        
        System.out.println("\n=== Validation example completed! ===");
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
                System.out.println("   ✓ Workflow validation successful");
            } else {
                System.out.println("   ✗ Workflow validation failed:");
                validation.getErrors().forEach(error -> 
                    System.out.println("     - " + error.getMessage()));
            }
            
            // Test dependency graph
            DependencyGraph graph = parser.buildDependencyGraph(java.util.List.of(workflow));
            ValidationResult graphValidation = graph.validate();
            if (graphValidation.isValid()) {
                System.out.println("   ✓ Dependency graph validation successful");
            } else {
                System.out.println("   ✗ Dependency graph validation failed");
            }
            
        } catch (Exception e) {
            System.out.println("   ✗ Validation failed: " + e.getMessage());
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
            System.out.println("   ✗ Should have failed but didn't");
        } catch (WorkflowParseException e) {
            System.out.println("   ✓ Correctly caught YAML syntax error: " + e.getMessage());
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
            System.out.println("   ✗ Should have failed during parsing but didn't");
        } catch (WorkflowParseException e) {
            System.out.println("   ✓ Correctly caught missing required fields: " + e.getMessage());
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
                System.out.println("   ✓ Correctly detected circular dependencies");
            } else {
                System.out.println("   ✗ Failed to detect circular dependencies");
            }
            
        } catch (WorkflowParseException e) {
            System.out.println("   ✓ Correctly caught circular dependency error: " + e.getMessage());
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
                System.out.println("   ✓ Correctly detected missing dependencies:");
                validation.getErrors().forEach(error -> 
                    System.out.println("     - " + error.getMessage()));
            } else {
                System.out.println("   ✗ Failed to detect missing dependencies");
            }
            
        } catch (Exception e) {
            System.out.println("   ✓ Correctly caught missing dependency error: " + e.getMessage());
        }
    }
    
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
                System.out.println("   ✓ Correctly detected variable resolution issues");
                if (execution.getErrorMessage().isPresent()) {
                    System.out.println("     Error: " + execution.getErrorMessage().get());
                }
            } else {
                System.out.println("   ✗ Failed to detect variable resolution issues");
            }
            
        } catch (Exception e) {
            System.out.println("   ✓ Correctly caught variable resolution error: " + e.getMessage());
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
                System.out.println("   ✓ Dry run validation successful");
                
                // Show what would be executed
                System.out.println("   Execution plan:");
                execution.getGroupExecutions().forEach(group -> {
                    System.out.println("     Group: " + group.getGroupName());
                    group.getTransferResults().forEach((name, result) -> {
                        System.out.println("       Transfer: " + name + " -> " + result.getFinalStatus());
                    });
                });
            } else {
                System.out.println("   ✗ Dry run validation failed");
                if (execution.getErrorMessage().isPresent()) {
                    System.out.println("     Error: " + execution.getErrorMessage().get());
                }
            }
            
        } catch (Exception e) {
            System.out.println("   ✗ Dry run failed: " + e.getMessage());
        }
    }
}
