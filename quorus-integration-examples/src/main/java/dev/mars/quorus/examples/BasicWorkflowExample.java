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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Basic example demonstrating YAML workflow execution with the Quorus transfer engine.
 * This example shows how to:
 * 1. Parse a YAML workflow definition
 * 2. Execute the workflow with variable substitution
 * 3. Monitor execution progress and results
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 1.0
 */
public class BasicWorkflowExample {
    
    public static void main(String[] args) {
        System.out.println("=== Quorus Basic Workflow Example ===");
        
        try {
            // Create the workflow example
            BasicWorkflowExample example = new BasicWorkflowExample();
            example.runExample();
            TestResultLogger.logExampleCompletion("Basic Workflow Example");
        } catch (Exception e) {
            // This catch block is for UNEXPECTED errors only
            TestResultLogger.logUnexpectedError("Basic Workflow Example", e);
            System.err.println("\nFull stack trace:");
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public void runExample() throws Exception {
        // 1. Create transfer engine
        System.out.println("1. Creating transfer engine...");
        TransferEngine transferEngine = new SimpleTransferEngine();
        
        // 2. Create workflow engine
        System.out.println("2. Creating workflow engine...");
        SimpleWorkflowEngine workflowEngine = new SimpleWorkflowEngine(transferEngine);
        
        // 3. Create YAML workflow definition
        System.out.println("3. Creating workflow definition...");
        String yamlWorkflow = createSampleWorkflow();
        
        // 4. Parse the workflow
        System.out.println("4. Parsing workflow...");
        WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
        WorkflowDefinition workflow = parser.parseFromString(yamlWorkflow);
        
        System.out.println("   Workflow: " + workflow.getMetadata().getName());
        System.out.println("   Description: " + workflow.getMetadata().getDescription());
        System.out.println("   Transfer Groups: " + workflow.getSpec().getTransferGroups().size());
        
        // 5. Validate the workflow
        System.out.println("5. Validating workflow...");
        ValidationResult validation = parser.validate(workflow);
        if (!validation.isValid()) {
            System.err.println("Workflow validation failed:");
            validation.getErrors().forEach(error -> 
                System.err.println("  - " + error.getMessage()));
            return;
        }
        System.out.println("   Workflow is valid!");
        
        // 6. Create execution context with variables
        System.out.println("6. Creating execution context...");
        ExecutionContext context = ExecutionContext.builder()
                .executionId("basic-example-" + System.currentTimeMillis())
                .mode(ExecutionContext.ExecutionMode.VIRTUAL_RUN) // Use virtual run for demo
                .variables(Map.of(
                        "baseUrl", "https://httpbin.org",
                        "outputDir", createTempDirectory().toString(),
                        "environment", "demo"
                ))
                .userId("demo-user")
                .metadata(Map.of("example", "basic-workflow"))
                .build();
        
        System.out.println("   Execution ID: " + context.getExecutionId());
        System.out.println("   Mode: " + context.getMode());
        System.out.println("   Variables: " + context.getVariables());
        
        // 7. Execute the workflow
        System.out.println("7. Executing workflow...");
        CompletableFuture<WorkflowExecution> future = workflowEngine.execute(workflow, context);
        
        // 8. Wait for completion and display results
        System.out.println("8. Waiting for completion...");
        WorkflowExecution execution = future.get();
        
        displayResults(execution);
        
        // 9. Cleanup
        System.out.println("9. Cleaning up...");
        workflowEngine.shutdown();
        
        System.out.println("\n=== Example completed successfully! ===");
    }
    
    private String createSampleWorkflow() {
        return """
                apiVersion: v1
                kind: TransferWorkflow
                metadata:
                  name: basic-demo-workflow
                  description: Basic demonstration of Quorus workflow capabilities
                  labels:
                    environment: "{{environment}}"
                    example: basic
                
                spec:
                  variables:
                    fileSize: "1024"
                    timeout: "30s"
                    
                  execution:
                    dryRun: false
                    virtualRun: false
                    parallelism: 1
                    timeout: 300s
                    strategy: sequential
                    
                  transferGroups:
                    - name: download-test-files
                      description: Download test files for demonstration
                      continueOnError: false
                      retryCount: 2
                      transfers:
                        - name: download-small-file
                          source: "{{baseUrl}}/bytes/{{fileSize}}"
                          destination: "{{outputDir}}/test-file-small.bin"
                          protocol: http
                          options:
                            timeout: "{{timeout}}"
                            
                        - name: download-json-data
                          source: "{{baseUrl}}/json"
                          destination: "{{outputDir}}/sample-data.json"
                          protocol: http
                          
                    - name: download-additional-files
                      description: Download additional files after first group
                      dependsOn:
                        - download-test-files
                      continueOnError: true
                      transfers:
                        - name: download-large-file
                          source: "{{baseUrl}}/bytes/2048"
                          destination: "{{outputDir}}/test-file-large.bin"
                          protocol: http
                          options:
                            chunkSize: 512
                """;
    }
    
    private Path createTempDirectory() throws Exception {
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "quorus-demo");
        Files.createDirectories(tempDir);
        return tempDir;
    }
    
    private void displayResults(WorkflowExecution execution) {
        System.out.println("\n=== Workflow Execution Results ===");
        System.out.println("Execution ID: " + execution.getExecutionId());
        System.out.println("Status: " + execution.getStatus());
        System.out.println("Successful: " + execution.isSuccessful());
        
        if (execution.getDuration().isPresent()) {
            System.out.println("Duration: " + execution.getDuration().get().toMillis() + "ms");
        }
        
        System.out.println("Total Transfers: " + execution.getTotalTransferCount());
        System.out.println("Successful Transfers: " + execution.getSuccessfulTransferCount());
        System.out.println("Total Bytes: " + execution.getTotalBytesTransferred());
        
        if (execution.getErrorMessage().isPresent()) {
            System.err.println("Error: " + execution.getErrorMessage().get());
        }
        
        System.out.println("\n--- Transfer Group Results ---");
        for (WorkflowExecution.GroupExecution group : execution.getGroupExecutions()) {
            System.out.println("Group: " + group.getGroupName());
            System.out.println("  Status: " + group.getStatus());
            System.out.println("  Successful: " + group.isSuccessful());
            System.out.println("  Transfers: " + group.getTransferResults().size());
            
            if (group.getDuration().isPresent()) {
                System.out.println("  Duration: " + group.getDuration().get().toMillis() + "ms");
            }
            
            if (group.getErrorMessage().isPresent()) {
                System.out.println("  Error: " + group.getErrorMessage().get());
            }
            
            // Display individual transfer results
            group.getTransferResults().forEach((name, result) -> {
                System.out.println("    Transfer: " + name);
                System.out.println("      Status: " + result.getFinalStatus());
                System.out.println("      Bytes: " + result.getBytesTransferred());
                if (result.getErrorMessage().isPresent()) {
                    System.out.println("      Error: " + result.getErrorMessage().get());
                }
            });
        }
    }
}
