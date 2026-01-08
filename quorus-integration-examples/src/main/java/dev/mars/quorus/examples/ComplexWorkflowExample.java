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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

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
    
    private static final ExampleLogger log = ExampleLogger.getLogger(ComplexWorkflowExample.class);
    
    public static void main(String[] args) {
        log.exampleStart("Complex Workflow Example");
        log.info("Demonstrating advanced workflow features with multi-stage processing pipeline.");
        log.blank();

        try {
            ComplexWorkflowExample example = new ComplexWorkflowExample();
            example.runExample();
            log.exampleComplete("Complex Workflow Example");
        } catch (Exception e) {
            // This catch block is for UNEXPECTED errors only
            log.unexpectedError("Complex Workflow Example", e);
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public void runExample() throws Exception {
        // 1. Setup with Vert.x
        log.step(1, "Creating Vert.x instance and engines...");
        Vertx vertx = Vertx.vertx();
        
        try {
            TransferEngine transferEngine = new SimpleTransferEngine(vertx, 10, 3, 1024 * 1024);
            SimpleWorkflowEngine workflowEngine = new SimpleWorkflowEngine(vertx, transferEngine);
            
            // 2. Create complex workflow
            log.step(2, "Creating complex workflow definition...");
            String yamlWorkflow = createComplexWorkflow();
        
            // 3. Parse and validate
            log.step(3, "Parsing and validating workflow...");
            WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
            WorkflowDefinition workflow = parser.parseFromString(yamlWorkflow);
            
            ValidationResult validation = parser.validate(workflow);
            if (!validation.isValid()) {
                log.error("Workflow validation failed:");
                validation.getErrors().forEach(error -> 
                    log.listItem(error.getMessage()));
                return;
            }
        
            // 4. Analyze dependency graph
            log.step(4, "Analyzing dependency graph...");
            analyzeDependencyGraph(parser, workflow);
            
            // 5. Execute with different modes
            log.step(5, "Executing workflow in different modes...");
            
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
            log.detail("5a. Performing dry run...");
            ExecutionContext dryRunContext = ExecutionContext.builder()
                    .executionId("complex-dry-run-" + System.currentTimeMillis())
                    .mode(ExecutionContext.ExecutionMode.DRY_RUN)
                    .variables(baseContext.getVariables())
                    .userId(baseContext.getUserId())
                    .build();
            
            Future<WorkflowExecution> dryRunFuture = workflowEngine.dryRun(workflow, dryRunContext);
            WorkflowExecution dryRunResult = dryRunFuture.toCompletionStage().toCompletableFuture().get();
            
            log.keyValue("Dry run completed", dryRunResult.getStatus());
            log.keyValue("Groups validated", dryRunResult.getGroupExecutions().size());
        
            // 5b. Virtual run
            log.detail("5b. Performing virtual run...");
            ExecutionContext virtualRunContext = ExecutionContext.builder()
                    .executionId("complex-virtual-run-" + System.currentTimeMillis())
                    .mode(ExecutionContext.ExecutionMode.VIRTUAL_RUN)
                    .variables(baseContext.getVariables())
                    .userId(baseContext.getUserId())
                    .build();
            
            Future<WorkflowExecution> virtualRunFuture = workflowEngine.virtualRun(workflow, virtualRunContext);
            WorkflowExecution virtualRunResult = virtualRunFuture.toCompletionStage().toCompletableFuture().get();
            
            log.keyValue("Virtual run completed", virtualRunResult.getStatus());
            displayDetailedResults(virtualRunResult);
            
            // 6. Cleanup
            log.step(6, "Cleaning up...");
            workflowEngine.shutdown();
            
            log.exampleComplete("Complex Workflow Example");
        } finally {
            vertx.close();
        }
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
        
        log.keyValue("Total groups", graph.getGroups().size());
        
        // Show topological order
        List<TransferGroup> sortedGroups = graph.topologicalSort();
        log.detail("Execution order:");
        for (int i = 0; i < sortedGroups.size(); i++) {
            TransferGroup group = sortedGroups.get(i);
            log.subDetail((i + 1) + ". " + group.getName() + 
                         " (depends on: " + group.getDependsOn() + ")");
        }
        
        // Show parallel execution batches
        List<List<TransferGroup>> batches = graph.getParallelExecutionBatches();
        log.keyValue("Parallel execution batches", batches.size());
        for (int i = 0; i < batches.size(); i++) {
            List<TransferGroup> batch = batches.get(i);
            log.subDetail("Batch " + (i + 1) + ": " + 
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
        log.section("Detailed Execution Results");
        log.keyValue("Execution ID", execution.getExecutionId());
        log.keyValue("Status", execution.getStatus());
        log.keyValue("Mode", execution.getContext().getMode());
        
        if (execution.getDuration().isPresent()) {
            log.keyValue("Total Duration", execution.getDuration().get().toMillis() + "ms");
        }
        
        log.keyValue("Groups Executed", execution.getGroupExecutions().size());
        log.keyValue("Total Transfers", execution.getTotalTransferCount());
        log.keyValue("Successful Transfers", execution.getSuccessfulTransferCount());
        
        // Show execution timeline
        log.section("Execution Timeline");
        execution.getGroupExecutions().forEach(group -> {
            log.info("Group: " + group.getGroupName());
            log.keyValue("Status", group.getStatus());
            log.keyValue("Start", group.getStartTime());
            if (group.getEndTime().isPresent()) {
                log.keyValue("End", group.getEndTime().get());
                log.keyValue("Duration", group.getDuration().get().toMillis() + "ms");
            }
            log.keyValue("Transfers", group.getTransferResults().size());
            log.blank();
        });
    }
}
