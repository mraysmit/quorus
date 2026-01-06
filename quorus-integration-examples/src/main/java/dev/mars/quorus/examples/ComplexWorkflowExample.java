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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Complex workflow example demonstrating advanced features:
 * 1. Multiple transfer groups with dependencies
 * 2. Variable substitution and inheritance
 * 3. Parallel execution capabilities
 * 4. Error handling and retry logic
 * 5. Dependency graph analysis
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-18
 * @version 1.0
 */
public class ComplexWorkflowExample {
    
    public static void main(String[] args) {
        System.out.println("=== Quorus Complex Workflow Example ===");
        System.out.println("Demonstrating advanced workflow features with multi-stage processing pipeline.");
        System.out.println();

        try {
            ComplexWorkflowExample example = new ComplexWorkflowExample();
            example.runExample();
            TestResultLogger.logExampleCompletion("Complex Workflow Example");
        } catch (Exception e) {
            // This catch block is for UNEXPECTED errors only
            TestResultLogger.logUnexpectedError("Complex Workflow Example", e);
            System.err.println("\nFull stack trace:");
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public void runExample() throws Exception {
        // 1. Setup
        System.out.println("1. Setting up transfer and workflow engines...");
        TransferEngine transferEngine = new SimpleTransferEngine();
        SimpleWorkflowEngine workflowEngine = new SimpleWorkflowEngine(transferEngine);
        
        // 2. Create complex workflow
        System.out.println("2. Creating complex workflow definition...");
        String yamlWorkflow = createComplexWorkflow();
        
        // 3. Parse and validate
        System.out.println("3. Parsing and validating workflow...");
        WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
        WorkflowDefinition workflow = parser.parseFromString(yamlWorkflow);
        
        ValidationResult validation = parser.validate(workflow);
        if (!validation.isValid()) {
            System.err.println("Workflow validation failed:");
            validation.getErrors().forEach(error -> 
                System.err.println("  - " + error.getMessage()));
            return;
        }
        
        // 4. Analyze dependency graph
        System.out.println("4. Analyzing dependency graph...");
        analyzeDependencyGraph(parser, workflow);
        
        // 5. Execute with different modes
        System.out.println("5. Executing workflow in different modes...");
        
        // Create execution context
        ExecutionContext baseContext = ExecutionContext.builder()
                .variables(Map.of(
                        "environment", "production",
                        "baseUrl", "https://httpbin.org",
                        "outputDir", createTempDirectory().toString(),
                        "batchId", "batch-" + System.currentTimeMillis()
                ))
                .userId("complex-demo-user")
                .build();
        
        // 5a. Dry run
        System.out.println("5a. Performing dry run...");
        ExecutionContext dryRunContext = ExecutionContext.builder()
                .executionId("complex-dry-run-" + System.currentTimeMillis())
                .mode(ExecutionContext.ExecutionMode.DRY_RUN)
                .variables(baseContext.getVariables())
                .userId(baseContext.getUserId())
                .build();
        
        CompletableFuture<WorkflowExecution> dryRunFuture = workflowEngine.dryRun(workflow, dryRunContext);
        WorkflowExecution dryRunResult = dryRunFuture.get();
        
        System.out.println("   Dry run completed: " + dryRunResult.getStatus());
        System.out.println("   Groups validated: " + dryRunResult.getGroupExecutions().size());
        
        // 5b. Virtual run
        System.out.println("5b. Performing virtual run...");
        ExecutionContext virtualRunContext = ExecutionContext.builder()
                .executionId("complex-virtual-run-" + System.currentTimeMillis())
                .mode(ExecutionContext.ExecutionMode.VIRTUAL_RUN)
                .variables(baseContext.getVariables())
                .userId(baseContext.getUserId())
                .build();
        
        CompletableFuture<WorkflowExecution> virtualRunFuture = workflowEngine.virtualRun(workflow, virtualRunContext);
        WorkflowExecution virtualRunResult = virtualRunFuture.get();
        
        System.out.println("   Virtual run completed: " + virtualRunResult.getStatus());
        displayDetailedResults(virtualRunResult);
        
        // 6. Cleanup
        System.out.println("6. Cleaning up...");
        workflowEngine.shutdown();
        
        System.out.println("\n=== Complex workflow example completed! ===");
    }
    
    private String createComplexWorkflow() {
        return """
                apiVersion: v1
                kind: TransferWorkflow
                metadata:
                  name: complex-data-pipeline
                  description: Complex data processing pipeline with dependencies and parallel execution
                  labels:
                    environment: "{{environment}}"
                    pipeline: data-processing
                    batch: "{{batchId}}"
                
                spec:
                  variables:
                    # Global configuration
                    maxRetries: "3"
                    timeout: "60s"
                    chunkSize: "1024"
                    
                    # Data sources
                    rawDataUrl: "{{baseUrl}}/bytes"
                    configUrl: "{{baseUrl}}/json"
                    
                    # Processing directories
                    rawDir: "{{outputDir}}/raw"
                    processedDir: "{{outputDir}}/processed"
                    archiveDir: "{{outputDir}}/archive"
                    
                  execution:
                    dryRun: false
                    virtualRun: false
                    parallelism: 3
                    timeout: 600s
                    strategy: parallel
                    
                  transferGroups:
                    # Stage 1: Download configuration and initial data
                    - name: download-config
                      description: Download configuration files required for processing
                      continueOnError: false
                      retryCount: 3
                      variables:
                        configFile: "pipeline-config.json"
                      transfers:
                        - name: download-pipeline-config
                          source: "{{configUrl}}"
                          destination: "{{rawDir}}/{{configFile}}"
                          protocol: http
                          options:
                            timeout: "{{timeout}}"
                            
                        - name: download-schema-config
                          source: "{{baseUrl}}/xml"
                          destination: "{{rawDir}}/schema.xml"
                          protocol: http
                    
                    # Stage 2: Download raw data files (parallel with config)
                    - name: download-raw-data
                      description: Download raw data files for processing
                      continueOnError: true
                      retryCount: 2
                      variables:
                        dataSize: "2048"
                      transfers:
                        - name: download-dataset-1
                          source: "{{rawDataUrl}}/{{dataSize}}"
                          destination: "{{rawDir}}/dataset-1.bin"
                          protocol: http
                          options:
                            chunkSize: "{{chunkSize}}"
                            maxRetries: "{{maxRetries}}"
                            
                        - name: download-dataset-2
                          source: "{{rawDataUrl}}/4096"
                          destination: "{{rawDir}}/dataset-2.bin"
                          protocol: http
                          
                        - name: download-dataset-3
                          source: "{{rawDataUrl}}/8192"
                          destination: "{{rawDir}}/dataset-3.bin"
                          protocol: http
                    
                    # Stage 3: Process data (depends on both config and raw data)
                    - name: process-data
                      description: Process downloaded data using configuration
                      dependsOn:
                        - download-config
                        - download-raw-data
                      continueOnError: false
                      retryCount: 1
                      transfers:
                        - name: process-dataset-1
                          source: "{{rawDir}}/dataset-1.bin"
                          destination: "{{processedDir}}/processed-dataset-1.bin"
                          protocol: file
                          
                        - name: process-dataset-2
                          source: "{{rawDir}}/dataset-2.bin"
                          destination: "{{processedDir}}/processed-dataset-2.bin"
                          protocol: file
                    
                    # Stage 4: Generate reports (depends on processed data)
                    - name: generate-reports
                      description: Generate processing reports and summaries
                      dependsOn:
                        - process-data
                      continueOnError: true
                      transfers:
                        - name: generate-summary-report
                          source: "{{baseUrl}}/html"
                          destination: "{{processedDir}}/summary-report.html"
                          protocol: http
                          
                        - name: generate-metrics-report
                          source: "{{configUrl}}"
                          destination: "{{processedDir}}/metrics.json"
                          protocol: http
                    
                    # Stage 5: Archive results (depends on both processing and reports)
                    - name: archive-results
                      description: Archive processed data and reports
                      dependsOn:
                        - process-data
                        - generate-reports
                      continueOnError: false
                      variables:
                        archiveTimestamp: "{{batchId}}"
                      transfers:
                        - name: archive-processed-data
                          source: "{{processedDir}}/processed-dataset-1.bin"
                          destination: "{{archiveDir}}/{{archiveTimestamp}}-dataset-1.bin"
                          protocol: file
                          
                        - name: archive-reports
                          source: "{{processedDir}}/summary-report.html"
                          destination: "{{archiveDir}}/{{archiveTimestamp}}-report.html"
                          protocol: file
                    
                    # Stage 6: Cleanup (depends on archiving)
                    - name: cleanup
                      description: Clean up temporary files after successful archiving
                      dependsOn:
                        - archive-results
                      continueOnError: true
                      transfers:
                        - name: cleanup-raw-data
                          source: "{{rawDir}}/dataset-1.bin"
                          destination: "/dev/null"
                          protocol: file
                """;
    }
    
    private void analyzeDependencyGraph(WorkflowDefinitionParser parser, WorkflowDefinition workflow) 
            throws WorkflowParseException {
        
        DependencyGraph graph = parser.buildDependencyGraph(List.of(workflow));
        
        System.out.println("   Total groups: " + graph.getGroups().size());
        
        // Show topological order
        List<TransferGroup> sortedGroups = graph.topologicalSort();
        System.out.println("   Execution order:");
        for (int i = 0; i < sortedGroups.size(); i++) {
            TransferGroup group = sortedGroups.get(i);
            System.out.println("     " + (i + 1) + ". " + group.getName() + 
                             " (depends on: " + group.getDependsOn() + ")");
        }
        
        // Show parallel execution batches
        List<List<TransferGroup>> batches = graph.getParallelExecutionBatches();
        System.out.println("   Parallel execution batches: " + batches.size());
        for (int i = 0; i < batches.size(); i++) {
            List<TransferGroup> batch = batches.get(i);
            System.out.println("     Batch " + (i + 1) + ": " + 
                             batch.stream().map(TransferGroup::getName).toList());
        }
    }
    
    private Path createTempDirectory() throws Exception {
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "quorus-complex-demo");
        Files.createDirectories(tempDir);
        
        // Create subdirectories
        Files.createDirectories(tempDir.resolve("raw"));
        Files.createDirectories(tempDir.resolve("processed"));
        Files.createDirectories(tempDir.resolve("archive"));
        
        return tempDir;
    }
    
    private void displayDetailedResults(WorkflowExecution execution) {
        System.out.println("\n=== Detailed Execution Results ===");
        System.out.println("Execution ID: " + execution.getExecutionId());
        System.out.println("Status: " + execution.getStatus());
        System.out.println("Mode: " + execution.getContext().getMode());
        
        if (execution.getDuration().isPresent()) {
            System.out.println("Total Duration: " + execution.getDuration().get().toMillis() + "ms");
        }
        
        System.out.println("Groups Executed: " + execution.getGroupExecutions().size());
        System.out.println("Total Transfers: " + execution.getTotalTransferCount());
        System.out.println("Successful Transfers: " + execution.getSuccessfulTransferCount());
        
        // Show execution timeline
        System.out.println("\n--- Execution Timeline ---");
        execution.getGroupExecutions().forEach(group -> {
            System.out.println("Group: " + group.getGroupName());
            System.out.println("  Status: " + group.getStatus());
            System.out.println("  Start: " + group.getStartTime());
            if (group.getEndTime().isPresent()) {
                System.out.println("  End: " + group.getEndTime().get());
                System.out.println("  Duration: " + group.getDuration().get().toMillis() + "ms");
            }
            System.out.println("  Transfers: " + group.getTransferResults().size());
            System.out.println();
        });
    }
}
