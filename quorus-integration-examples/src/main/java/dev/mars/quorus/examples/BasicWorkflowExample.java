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


import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;

/**
 * Basic example demonstrating YAML workflow execution with the Quorus transfer engine.
 * This example shows how to:
 * 1. Parse a YAML workflow definition
 * 2. Execute the workflow with variable substitution
 * 3. Monitor execution progress and results
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-18
 * @version 1.0
 */
public class BasicWorkflowExample {
    
    private static final ExampleLogger log = ExampleLogger.getLogger(BasicWorkflowExample.class);
    
    public static void main(String[] args) {
        log.exampleStart("Basic Workflow Example");
        
        try {
            // Create the workflow example
            BasicWorkflowExample example = new BasicWorkflowExample();
            example.runExample();
            log.exampleComplete("Basic Workflow Example");
        } catch (Exception e) {
            // This catch block is for UNEXPECTED errors only
            log.unexpectedError("Basic Workflow Example", e);
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public void runExample() throws Exception {
        // 1. Create transfer engine
        log.step(1, "Creating transfer engine...");
        TransferEngine transferEngine = new SimpleTransferEngine(10, 3, 1000); // 10 concurrent, 3 retries, 1 s retry delay

        try {
            // 2. Create workflow engine
            log.step(2, "Creating workflow engine...");
            SimpleWorkflowEngine workflowEngine = new SimpleWorkflowEngine(transferEngine);
            
            // 3. Create YAML workflow definition
            log.step(3, "Creating workflow definition...");
            String yamlWorkflow = createSampleWorkflow();
        
            // 4. Parse the workflow
            log.step(4, "Parsing workflow...");
            WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
            WorkflowDefinition workflow = parser.parseFromString(yamlWorkflow);
            
            log.keyValue("Workflow", workflow.getMetadata().getName());
            log.keyValue("Description", workflow.getMetadata().getDescription());
            log.keyValue("Transfer Groups", workflow.getSpec().getTransferGroups().size());
        
            // 5. Validate the workflow
            log.step(5, "Validating workflow...");
            ValidationResult validation = parser.validate(workflow);
            if (!validation.isValid()) {
                log.error("Workflow validation failed:");
                validation.getErrors().forEach(error -> 
                    log.listItem(error.getMessage()));
                return;
            }
            log.success("Workflow is valid!");
        
            // 6. Create execution context with variables
            log.step(6, "Creating execution context...");
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
            
            log.keyValue("Execution ID", context.getExecutionId());
            log.keyValue("Mode", context.getMode());
            log.keyValue("Variables", context.getVariables());
        
            // 7. Execute the workflow; the call returns when it has finished
            log.step(7, "Executing workflow...");
            WorkflowExecution execution = workflowEngine.execute(workflow, context);
            
            displayResults(execution);
            
            // 8. Cleanup
            log.step(8, "Cleaning up...");
            workflowEngine.shutdown();
            
            log.exampleComplete("Basic Workflow Example");
        } finally {
            transferEngine.shutdown(Duration.ofSeconds(10));
}
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
        log.section("Workflow Execution Results");
        log.keyValue("Execution ID", execution.getExecutionId());
        log.keyValue("Status", execution.getStatus());
        log.keyValue("Successful", execution.isSuccessful());
        
        if (execution.getDuration().isPresent()) {
            log.keyValue("Duration", execution.getDuration().get().toMillis() + "ms");
        }
        
        log.keyValue("Total Transfers", execution.getTotalTransferCount());
        log.keyValue("Successful Transfers", execution.getSuccessfulTransferCount());
        log.keyValue("Total Bytes", execution.getTotalBytesTransferred());
        
        if (execution.getErrorMessage().isPresent()) {
            log.error("Error: " + execution.getErrorMessage().get());
        }
        
        log.section("Transfer Group Results");
        for (WorkflowExecution.GroupExecution group : execution.getGroupExecutions()) {
            log.info("Group: " + group.getGroupName());
            log.keyValue("Status", group.getStatus());
            log.keyValue("Successful", group.isSuccessful());
            log.keyValue("Transfers", group.getTransferResults().size());
            
            if (group.getDuration().isPresent()) {
                log.keyValue("Duration", group.getDuration().get().toMillis() + "ms");
            }
            
            if (group.getErrorMessage().isPresent()) {
                log.keyValue("Error", group.getErrorMessage().get());
            }
            
            // Display individual transfer results
            group.getTransferResults().forEach((name, result) -> {
                log.detail("Transfer: " + name);
                log.indentedKeyValue("Status", result.getFinalStatus());
                log.indentedKeyValue("Bytes", result.getBytesTransferred());
                if (result.getErrorMessage().isPresent()) {
                    log.indentedKeyValue("Error", result.getErrorMessage().get());
                }
            });
        }
    }
}
