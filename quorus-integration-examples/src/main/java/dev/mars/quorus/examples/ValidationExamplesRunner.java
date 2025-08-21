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

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Comprehensive examples demonstrating the new YAML schema validation system.
 * Shows practical usage scenarios and best practices for workflow validation.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
public class ValidationExamplesRunner {

    public static void main(String[] args) throws Exception {
        ValidationExamplesRunner runner = new ValidationExamplesRunner();
        runner.runAllExamples();
    }

    public void runAllExamples() throws Exception {
        TestResultLogger.logTestHeader("Quorus YAML Schema Validation Examples");
        
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
        
        TestResultLogger.logTestFooter("All validation examples completed successfully!");
    }

    private void validateExistingWorkflows() throws Exception {
        TestResultLogger.logTestSection("1. Validating Existing Workflow Files", false);
        
        WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
        
        // Validate the schema-compliant example
        Path schemaCompliantPath = Paths.get("src/main/resources/workflows/schema-compliant-example.yaml");
        System.out.println("   Validating: " + schemaCompliantPath.getFileName());
        
        try {
            // Parse and validate
            WorkflowDefinition workflow = parser.parse(schemaCompliantPath);
            ValidationResult result = parser.validate(workflow);
            
            if (result.isValid()) {
                TestResultLogger.logSuccess("✓ Schema-compliant example is valid");
                System.out.println("     Name: " + workflow.getMetadata().getName());
                System.out.println("     Type: " + workflow.getMetadata().getType());
                System.out.println("     Tags: " + workflow.getMetadata().getTags());
                System.out.println("     Created: " + workflow.getMetadata().getCreated());
                System.out.println("     Transfer Groups: " + workflow.getSpec().getTransferGroups().size());
            } else {
                TestResultLogger.logUnexpectedResult("Validation failed:");
                result.getErrors().forEach(error ->
                    System.out.println("       - " + error));
            }
            
        } catch (Exception e) {
            TestResultLogger.logUnexpectedException("Error validating workflow", e);
        }
        
        // Validate other example workflows
        String[] workflowFiles = {
            "data-pipeline.yaml",
            "simple-download.yaml"
        };
        
        for (String filename : workflowFiles) {
            Path workflowPath = Paths.get("src/main/resources/workflows/" + filename);
            System.out.println("   Validating: " + filename);
            
            try {
                WorkflowDefinition workflow = parser.parse(workflowPath);
                ValidationResult result = parser.validate(workflow);
                
                if (result.isValid()) {
                    System.out.println("     ✓ Valid - " + workflow.getMetadata().getName());
                } else {
                    System.out.println("     ✗ Invalid:");
                    result.getErrors().forEach(error ->
                        System.out.println("       - " + error));
                }
                
                if (result.hasWarnings()) {
                    System.out.println("     Warnings:");
                    result.getWarnings().forEach(warning ->
                        System.out.println("       - " + warning));
                }
                
            } catch (Exception e) {
                System.out.println("     ✗ Error: " + e.getMessage());
            }
        }
    }

    private void demonstrateValidationErrors() throws Exception {
        TestResultLogger.logTestSection("2. Demonstrating Validation Error Handling", true);
        
        WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
        
        // Example with missing required fields
        String incompleteWorkflow = """
                metadata:
                  name: "Incomplete Example"
                  version: "1.0.0"
                  # Missing: description, type, author, created, tags

                spec:
                  execution:
                    parallelism: 1
                  transferGroups: []
                """;

        System.out.println("   Testing workflow with missing required fields...");
        ValidationResult result = parser.validateSchema(incompleteWorkflow);
        
        if (!result.isValid()) {
            TestResultLogger.logExpectedFailure("✓ Correctly detected missing fields:");
            result.getErrors().forEach(error ->
                System.out.println("     - " + error.getFieldPath() + ": " + error.getMessage()));
        } else {
            TestResultLogger.logUnexpectedResult("Failed to detect missing fields!");
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

        System.out.println("   Testing workflow with invalid field formats...");
        result = parser.validateSchema(invalidFormatsWorkflow);
        
        if (!result.isValid()) {
            TestResultLogger.logExpectedFailure("✓ Correctly detected format errors:");
            result.getErrors().forEach(error ->
                System.out.println("     - " + error.getFieldPath() + ": " + error.getMessage()));
        } else {
            TestResultLogger.logUnexpectedResult("Failed to detect format errors!");
        }
    }

    private void demonstrateBestPractices() throws Exception {
        TestResultLogger.logTestSection("3. Schema Compliance Best Practices", false);
        
        System.out.println("   Best practices for YAML workflow definitions:");
        System.out.println();
        
        System.out.println("   ✓ Required Metadata Fields:");
        System.out.println("     - name: Descriptive workflow name (2-100 chars)");
        System.out.println("     - version: Semantic versioning (e.g., '1.0.0')");
        System.out.println("     - description: Clear description (10-500 chars)");
        System.out.println("     - type: Workflow type (lowercase-with-hyphens)");
        System.out.println("     - author: Email address or name");
        System.out.println("     - created: ISO date format (YYYY-MM-DD)");
        System.out.println("     - tags: Array of lowercase tags");
        System.out.println();
        
        System.out.println("   ✓ Recommended Workflow Types:");
        System.out.println("     - transfer-workflow: File transfer operations");
        System.out.println("     - data-pipeline-workflow: ETL and data processing");
        System.out.println("     - download-workflow: Download operations");
        System.out.println("     - validation-test-workflow: Testing and validation");
        System.out.println("     - external-data-config: External data configurations");
        System.out.println("     - etl-workflow: Extract, Transform, Load operations");
        System.out.println("     - backup-workflow: Backup and archival operations");
        System.out.println("     - sync-workflow: Synchronization operations");
        System.out.println();
        
        System.out.println("   ✓ Tag Guidelines:");
        System.out.println("     - Use lowercase letters and numbers");
        System.out.println("     - Separate words with hyphens (not underscores)");
        System.out.println("     - Keep tags short and descriptive");
        System.out.println("     - Include environment, team, and category tags");
        System.out.println("     - Maximum 20 tags per workflow");
        System.out.println();
        
        System.out.println("   ✓ Version Guidelines:");
        System.out.println("     - Follow semantic versioning (MAJOR.MINOR.PATCH)");
        System.out.println("     - Use pre-release identifiers for testing (1.0.0-beta)");
        System.out.println("     - Include build metadata when needed (1.0.0+build.1)");
        System.out.println();
        
        // Show a perfect example
        String perfectExample = """
                metadata:
                  name: "Production ETL Pipeline"
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
            TestResultLogger.logSuccess("✓ Perfect example validates successfully");
            System.out.println("     No errors or warnings");
        } else {
            TestResultLogger.logUnexpectedResult("Perfect example failed validation:");
            result.getErrors().forEach(error ->
                System.out.println("     - " + error));
        }
    }

    private void demonstrateLegacyMigration() throws Exception {
        TestResultLogger.logTestSection("4. Legacy Format Migration", false);
        
        System.out.println("   Migrating from legacy format to new schema...");
        
        // Show legacy format with deprecation warnings
        String legacyWorkflow = """
                apiVersion: v1
                kind: TransferWorkflow  # Deprecated - use metadata.type instead
                metadata:
                  name: "Legacy Workflow"
                  version: "1.0.0"
                  description: "Legacy workflow format for backward compatibility demonstration"
                  type: "transfer-workflow"
                  author: "legacy@company.com"
                  created: "2025-08-21"
                  tags: ["legacy", "migration", "backward-compatibility"]

                spec:
                  execution:
                    parallelism: 1
                  transferGroups:
                    - name: legacy-transfer
                      transfers:
                        - name: legacy-file-copy
                          source: "http://legacy.example.com/data.txt"
                          destination: "/tmp/legacy-data.txt"
                          protocol: http
                """;

        WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
        ValidationResult result = parser.validateSchema(legacyWorkflow);
        
        if (result.isValid()) {
            System.out.println("   ✓ Legacy format still validates");
            
            if (result.hasWarnings()) {
                System.out.println("   Deprecation warnings:");
                result.getWarnings().forEach(warning ->
                    System.out.println("     - " + warning.getFieldPath() + ": " + warning.getMessage()));
            }
            
            // Parse and show migration
            WorkflowDefinition workflow = parser.parseFromString(legacyWorkflow);
            System.out.println("   Migration guidance:");
            System.out.println("     - Remove 'kind' field (value: " + workflow.getKind() + ")");
            System.out.println("     - Use 'metadata.type' instead: " + workflow.getMetadata().getType());
            System.out.println("     - Ensure all required metadata fields are present");
            
        } else {
            TestResultLogger.logUnexpectedResult("Legacy format validation failed:");
            result.getErrors().forEach(error ->
                System.out.println("     - " + error));
        }
    }

    private void demonstrateRealWorldExamples() throws Exception {
        TestResultLogger.logTestSection("5. Real-World Workflow Examples", false);
        
        System.out.println("   Validating real-world workflow scenarios...");
        
        // E-commerce order processing workflow
        validateRealWorldExample("E-commerce Order Processing", """
                metadata:
                  name: "E-commerce Order Processing Pipeline"
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
                  name: "Monthly Financial Reporting"
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
                  name: "IoT Sensor Data Processing Pipeline"
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
                System.out.println("   ✓ " + name + " - Valid");
                
                WorkflowDefinition workflow = parser.parseFromString(yaml);
                System.out.println("     Type: " + workflow.getMetadata().getType());
                System.out.println("     Tags: " + workflow.getMetadata().getTags().size() + " tags");
                System.out.println("     Labels: " + workflow.getMetadata().getLabels().size() + " labels");
                
            } else {
                System.out.println("   ✗ " + name + " - Invalid:");
                result.getErrors().forEach(error ->
                    System.out.println("       - " + error));
            }
            
            if (result.hasWarnings()) {
                System.out.println("     Warnings: " + result.getWarningCount());
            }
            
        } catch (Exception e) {
            System.out.println("   ✗ " + name + " - Error: " + e.getMessage());
        }
        
        System.out.println();
    }
}
