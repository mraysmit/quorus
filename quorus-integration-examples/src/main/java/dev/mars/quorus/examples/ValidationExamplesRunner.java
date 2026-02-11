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

import dev.mars.quorus.workflow.*;
import dev.mars.quorus.examples.util.ExampleLogger;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Comprehensive examples demonstrating the new YAML schema validation system.
 * Shows practical usage scenarios and best practices for workflow validation.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-21
 * @version 1.0
 */
public class ValidationExamplesRunner {

    private static final ExampleLogger log = ExampleLogger.getLogger(ValidationExamplesRunner.class);

    public static void main(String[] args) throws Exception {
        ValidationExamplesRunner runner = new ValidationExamplesRunner();
        runner.runAllExamples();
    }

    public void runAllExamples() throws Exception {
        log.header("Quorus YAML Schema Validation Examples");
        
        // Example 1: Validate existing workflow files
        validateExistingWorkflows();
        
        // Example 2: Demonstrate validation error handling
        demonstrateValidationErrors();
        
        // Example 3: Show schema compliance best practices
        demonstrateBestPractices();
        
        // Example 4: Migration from legacy format
        demonstrateLegacyMigration();
        
        // Example 5: Real-world workflow examples
        demonstrateRealWorldExamples();
        
        log.info("All validation examples completed successfully!");
    }

    private void validateExistingWorkflows() throws Exception {
        log.testSection("1. Validating Existing Workflow Files", false);
        
        WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
        
        // Validate the schema-compliant example
        Path schemaCompliantPath = Paths.get("src/main/resources/workflows/schema-compliant-example.yaml");
        log.detail("Validating: " + schemaCompliantPath.getFileName());
        
        try {
            // Parse and validate
            WorkflowDefinition workflow = parser.parse(schemaCompliantPath);
            ValidationResult result = parser.validate(workflow);
            
            if (result.isValid()) {
                log.success("Schema-compliant example is valid");
                log.indentedKeyValue("Name", workflow.getMetadata().getName());
                log.indentedKeyValue("Type", workflow.getMetadata().getType());
                log.indentedKeyValue("Tags", workflow.getMetadata().getTags());
                log.indentedKeyValue("Created", workflow.getMetadata().getCreated());
                log.indentedKeyValue("Transfer Groups", workflow.getSpec().getTransferGroups().size());
            } else {
                log.failure("UNEXPECTED: Validation failed:");
                result.getErrors().forEach(error ->
                    log.subDetail(error.toString()));
            }
            
        } catch (Exception e) {
            log.failure("UNEXPECTED: Error validating workflow: " + e.getMessage());
        }
        
        // Validate other example workflows
        String[] workflowFiles = {
            "data-pipeline.yaml",
            "simple-download.yaml"
        };
        
        for (String filename : workflowFiles) {
            Path workflowPath = Paths.get("src/main/resources/workflows/" + filename);
            log.detail("Validating: " + filename);
            
            try {
                WorkflowDefinition workflow = parser.parse(workflowPath);
                ValidationResult result = parser.validate(workflow);
                
                if (result.isValid()) {
                    log.subDetail("✓ Valid - " + workflow.getMetadata().getName());
                } else {
                    log.subDetail("✗ Invalid:");
                    result.getErrors().forEach(error ->
                        log.deepDetail(error.toString()));
                }
                
                if (result.hasWarnings()) {
                    log.subDetail("Warnings:");
                    result.getWarnings().forEach(warning ->
                        log.deepDetail(warning.toString()));
                }
                
            } catch (Exception e) {
                log.subDetail("✗ Error: " + e.getMessage());
            }
        }
    }

    private void demonstrateValidationErrors() throws Exception {
        log.testSection("2. Demonstrating Validation Error Handling", true);
        
        WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
        
        // Example with missing required fields
        String incompleteWorkflow = """
                metadata:
                  name: "incomplete-example"
                  version: "1.0.0"
                  # Missing: description, type, author, created, tags

                spec:
                  execution:
                    parallelism: 1
                  transferGroups: []
                """;

        log.detail("Testing workflow with missing required fields...");
        ValidationResult result = parser.validateSchema(incompleteWorkflow);
        
        if (!result.isValid()) {
            log.expectedFailure("Correctly detected missing fields:");
            result.getErrors().forEach(error ->
                log.subDetail(error.getFieldPath() + ": " + error.getMessage()));
        } else {
            log.failure("UNEXPECTED: Failed to detect missing fields!");
        }

        // Example with invalid field formats
        String invalidFormatsWorkflow = """
                metadata:
                  name: "A"  # Too short
                  version: "invalid"  # Invalid format
                  description: "Short"  # Too short
                  type: "Invalid_Type"  # Invalid format
                  author: "invalid-email"  # Invalid format
                  created: "2025-13-45"  # Invalid date
                  tags: ["INVALID", "invalid_underscore"]  # Invalid formats

                spec:
                  execution:
                    parallelism: 1
                  transferGroups: []
                """;

        log.detail("Testing workflow with invalid field formats...");
        result = parser.validateSchema(invalidFormatsWorkflow);
        
        if (!result.isValid()) {
            log.expectedFailure("Correctly detected format errors:");
            result.getErrors().forEach(error ->
                log.subDetail(error.getFieldPath() + ": " + error.getMessage()));
        } else {
            log.failure("UNEXPECTED: Failed to detect format errors!");
        }
    }

    private void demonstrateBestPractices() throws Exception {
        log.testSection("3. Schema Compliance Best Practices", false);
        
        log.detail("Best practices for YAML workflow definitions:");
        log.blank();
        
        log.detail("✓ Required Metadata Fields:");
        log.subDetail("name: Descriptive workflow name (2-100 chars)");
        log.subDetail("version: Semantic versioning (e.g., '1.0.0')");
        log.subDetail("description: Clear description (10-500 chars)");
        log.subDetail("type: Workflow type (lowercase-with-hyphens)");
        log.subDetail("author: Email address or name");
        log.subDetail("created: ISO date format (YYYY-MM-DD)");
        log.subDetail("tags: Array of lowercase tags");
        log.blank();
        
        log.detail("✓ Recommended Workflow Types:");
        log.subDetail("transfer-workflow: File transfer operations");
        log.subDetail("data-pipeline-workflow: ETL and data processing");
        log.subDetail("download-workflow: Download operations");
        log.subDetail("validation-test-workflow: Testing and validation");
        log.subDetail("external-data-config: External data configurations");
        log.subDetail("etl-workflow: Extract, Transform, Load operations");
        log.subDetail("backup-workflow: Backup and archival operations");
        log.subDetail("sync-workflow: Synchronization operations");
        log.blank();
        
        log.detail("✓ Tag Guidelines:");
        log.subDetail("Use lowercase letters and numbers");
        log.subDetail("Separate words with hyphens (not underscores)");
        log.subDetail("Keep tags short and descriptive");
        log.subDetail("Include environment, team, and category tags");
        log.subDetail("Maximum 20 tags per workflow");
        log.blank();
        
        log.detail("✓ Version Guidelines:");
        log.subDetail("Follow semantic versioning (MAJOR.MINOR.PATCH)");
        log.subDetail("Use pre-release identifiers for testing (1.0.0-beta)");
        log.subDetail("Include build metadata when needed (1.0.0+build.1)");
        log.blank();
        
        // Show a perfect example
        String perfectExample = """
                metadata:
                  name: "production-etl-pipeline"
                  version: "2.1.0"
                  description: "Production-ready ETL pipeline for customer data processing with comprehensive error handling and monitoring"
                  type: "etl-workflow"
                  author: "data-engineering@company.com"
                  created: "2025-08-21"
                  tags: ["production", "etl", "customer-data", "monitoring", "error-handling"]
                  labels:
                    environment: "production"
                    team: "data-engineering"
                    criticality: "high"
                    schedule: "daily"

                spec:
                  variables:
                    sourceDb: "postgresql://prod:5432/customers"
                    targetPath: "/data/warehouse/customers"
                    
                  execution:
                    dryRun: false
                    virtualRun: false
                    parallelism: 3
                    timeout: 3600s
                    strategy: sequential
                    
                  transferGroups:
                    - name: extract-data
                      description: Extract customer data from source
                      transfers:
                        - name: extract-customers
                          source: "{{sourceDb}}/customers"
                          destination: "{{targetPath}}/customers.parquet"
                          protocol: database
                """;

        WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
        ValidationResult result = parser.validateSchema(perfectExample);
        
        if (result.isValid()) {
            log.success("Perfect example validates successfully");
            log.subDetail("No errors or warnings");
        } else {
            log.failure("UNEXPECTED: Perfect example failed validation:");
            result.getErrors().forEach(error ->
                log.subDetail(error.toString()));
        }
    }

    private void demonstrateLegacyMigration() throws Exception {
        log.testSection("4. Workflow Format Validation", false);
        
        log.detail("Demonstrating standard workflow format validation...");
        
        // Show standard workflow format
        String standardWorkflow = """
                apiVersion: v1
                metadata:
                  name: "standard-workflow"
                  version: "1.0.0"
                  description: "Standard workflow format demonstration"
                  type: "transfer-workflow"
                  author: "dev@company.com"
                  created: "2025-08-21"
                  tags: ["standard", "validation"]

                spec:
                  execution:
                    parallelism: 1
                  transferGroups:
                    - name: standard-transfer
                      transfers:
                        - name: file-copy
                          source: "http://example.com/data.txt"
                          destination: "/tmp/data.txt"
                          protocol: http
                """;

        WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
        ValidationResult result = parser.validateSchema(standardWorkflow);
        
        if (result.isValid()) {
            log.success("Standard format validates successfully");
            
            // Parse and show details
            WorkflowDefinition workflow = parser.parseFromString(standardWorkflow);
            log.detail("Workflow details:");
            log.subDetail("Type: " + workflow.getMetadata().getType());
            log.subDetail("Name: " + workflow.getMetadata().getName());
            log.subDetail("All required metadata fields are present");
            
        } else {
            log.failure("UNEXPECTED: Standard format validation failed:");
            result.getErrors().forEach(error ->
                log.subDetail(error.toString()));
        }
    }

    private void demonstrateRealWorldExamples() throws Exception {
        log.testSection("5. Real-World Workflow Examples", false);
        
        log.detail("Validating real-world workflow scenarios...");
        
        // E-commerce order processing workflow
        validateRealWorldExample("E-commerce Order Processing", """
                metadata:
                  name: "ecommerce-order-processing-pipeline"
                  version: "3.1.2"
                  description: "Complete order processing pipeline from payment to fulfillment with inventory management"
                  type: "data-pipeline-workflow"
                  author: "ecommerce-team@company.com"
                  created: "2025-08-21"
                  tags: ["ecommerce", "orders", "payment", "fulfillment", "inventory"]
                  labels:
                    environment: "production"
                    team: "ecommerce"
                    sla: "15-minutes"

                spec:
                  variables:
                    ordersApi: "https://api.company.com/orders"
                    inventoryDb: "postgresql://inventory:5432/stock"
                    fulfillmentQueue: "sqs://fulfillment-queue"
                    
                  execution:
                    parallelism: 5
                    timeout: 900s
                    strategy: parallel
                    
                  transferGroups:
                    - name: process-payments
                      description: Process pending payments
                      transfers:
                        - name: fetch-pending-orders
                          source: "{{ordersApi}}/pending"
                          destination: "/tmp/pending-orders.json"
                          protocol: https
                """);

        // Financial reporting workflow
        validateRealWorldExample("Financial Reporting", """
                metadata:
                  name: "monthly-financial-reporting"
                  version: "2.0.1"
                  description: "Automated monthly financial report generation with regulatory compliance checks"
                  type: "etl-workflow"
                  author: "finance-automation@company.com"
                  created: "2025-08-21"
                  tags: ["finance", "reporting", "compliance", "monthly", "automated"]
                  labels:
                    environment: "production"
                    team: "finance"
                    schedule: "monthly"
                    compliance: "sox-required"

                spec:
                  variables:
                    financialDb: "postgresql://finance:5432/accounting"
                    reportPath: "/reports/monthly"
                    complianceApi: "https://compliance.company.com"
                    
                  execution:
                    parallelism: 2
                    timeout: 7200s
                    strategy: sequential
                    
                  transferGroups:
                    - name: extract-financial-data
                      description: Extract monthly financial data
                      transfers:
                        - name: extract-transactions
                          source: "{{financialDb}}/transactions"
                          destination: "{{reportPath}}/transactions.csv"
                          protocol: database
                """);

        // IoT sensor data processing
        validateRealWorldExample("IoT Sensor Data Processing", """
                metadata:
                  name: "iot-sensor-data-processing-pipeline"
                  version: "1.5.0"
                  description: "Real-time processing of IoT sensor data with anomaly detection and alerting"
                  type: "data-pipeline-workflow"
                  author: "iot-platform@company.com"
                  created: "2025-08-21"
                  tags: ["iot", "sensors", "real-time", "anomaly-detection", "alerting"]
                  labels:
                    environment: "production"
                    team: "iot-platform"
                    frequency: "continuous"
                    criticality: "high"

                spec:
                  variables:
                    sensorStream: "kafka://sensors:9092/sensor-data"
                    timeseriesDb: "influxdb://timeseries:8086/sensors"
                    alertingApi: "https://alerts.company.com"
                    
                  execution:
                    parallelism: 10
                    timeout: 300s
                    strategy: parallel
                    
                  transferGroups:
                    - name: ingest-sensor-data
                      description: Ingest and validate sensor data
                      transfers:
                        - name: consume-sensor-stream
                          source: "{{sensorStream}}"
                          destination: "{{timeseriesDb}}/raw_data"
                          protocol: kafka
                """);
    }

    private void validateRealWorldExample(String name, String yaml) {
        try {
            WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
            ValidationResult result = parser.validateSchema(yaml);
            
            if (result.isValid()) {
                log.success(name + " - Valid");
                
                WorkflowDefinition workflow = parser.parseFromString(yaml);
                log.indentedKeyValue("Type", workflow.getMetadata().getType());
                log.indentedKeyValue("Tags", workflow.getMetadata().getTags().size() + " tags");
                log.indentedKeyValue("Labels", workflow.getMetadata().getLabels().size() + " labels");
                
            } else {
                log.failure(name + " - Invalid:");
                result.getErrors().forEach(error ->
                    log.subDetail(error.toString()));
            }
            
            if (result.hasWarnings()) {
                log.indentedKeyValue("Warnings", result.getWarningCount());
            }
            
        } catch (Exception e) {
            log.failure(name + " - Error: " + e.getMessage());
        }
        
        log.blank();
    }
}
