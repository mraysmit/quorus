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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the complete YAML schema validation pipeline,
 * testing the interaction between parsing, validation, and workflow creation.
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-21
 */
class SchemaValidationIntegrationTest {

    private YamlWorkflowDefinitionParser parser;

    @BeforeEach
    void setUp() {
        parser = new YamlWorkflowDefinitionParser();
    }

    @Nested
    @DisplayName("End-to-End Validation Pipeline Tests")
    class EndToEndValidationTests {

        @Test
        @DisplayName("Should validate and parse complete workflow successfully")
        void testCompleteWorkflowParsing() throws WorkflowParseException {
            String completeWorkflow = """
                    metadata:
                      name: "integration-test-workflow"
                      version: "2.1.0"
                      description: "Complete workflow for integration testing with all features"
                      type: "data-pipeline-workflow"
                      author: "integration@quorus.dev"
                      created: "2025-08-21"
                      tags: ["integration", "test", "pipeline", "complete"]
                      labels:
                        environment: "test"
                        team: "qa"
                        category: "integration"

                    spec:
                      variables:
                        apiUrl: "https://api.test.com"
                        dataPath: "/data/test"
                        batchSize: "1000"
                        timeout: "60s"
                        
                      execution:
                        dryRun: false
                        virtualRun: false
                        parallelism: 3
                        timeout: 3600s
                        strategy: parallel
                        
                      transferGroups:
                        - name: initialization
                          description: Initialize test environment
                          continueOnError: false
                          retryCount: 3
                          variables:
                            initTimeout: "30s"
                          transfers:
                            - name: setup-directories
                              source: "{{apiUrl}}/setup"
                              destination: "{{dataPath}}/setup.json"
                              protocol: https
                              options:
                                timeout: "{{initTimeout}}"
                                validateCertificate: true
                                
                        - name: data-ingestion
                          description: Ingest test data
                          dependsOn:
                            - initialization
                          continueOnError: true
                          retryCount: 2
                          transfers:
                            - name: ingest-batch-1
                              source: "{{apiUrl}}/data/batch1"
                              destination: "{{dataPath}}/batch1.json"
                              protocol: https
                              condition: "success(initialization)"
                              
                            - name: ingest-batch-2
                              source: "{{apiUrl}}/data/batch2"
                              destination: "{{dataPath}}/batch2.json"
                              protocol: https
                              
                        - name: data-processing
                          description: Process ingested data
                          dependsOn:
                            - data-ingestion
                          continueOnError: false
                          retryCount: 1
                          transfers:
                            - name: process-data
                              source: "{{dataPath}}/batch1.json"
                              destination: "{{dataPath}}/processed.json"
                              protocol: file
                              options:
                                chunkSize: "{{batchSize}}"
                                
                        - name: validation
                          description: Validate processed results
                          dependsOn:
                            - data-processing
                          continueOnError: false
                          transfers:
                            - name: validate-results
                              source: "{{dataPath}}/processed.json"
                              destination: "{{dataPath}}/validation-report.json"
                              protocol: file
                    """;

            // Test schema validation
            ValidationResult schemaResult = parser.validateSchema(completeWorkflow);
            assertTrue(schemaResult.isValid(), "Schema validation should pass");
            assertEquals(0, schemaResult.getErrorCount());

            // Test parsing
            WorkflowDefinition workflow = parser.parseFromString(completeWorkflow);
            assertNotNull(workflow);

            // Validate parsed metadata
            WorkflowDefinition.WorkflowMetadata metadata = workflow.getMetadata();
            assertEquals("integration-test-workflow", metadata.getName());
            assertEquals("2.1.0", metadata.getVersion());
            assertEquals("data-pipeline-workflow", metadata.getType());
            assertEquals("integration@quorus.dev", metadata.getAuthor());
            assertEquals("2025-08-21", metadata.getCreated());
            assertEquals(List.of("integration", "test", "pipeline", "complete"), metadata.getTags());
            assertEquals(3, metadata.getLabels().size());

            // Test semantic validation
            ValidationResult semanticResult = parser.validate(workflow);
            assertTrue(semanticResult.isValid(), "Semantic validation should pass");

            // Test dependency graph building
            DependencyGraph graph = parser.buildDependencyGraph(List.of(workflow));
            assertNotNull(graph);
            ValidationResult graphResult = graph.validate();
            assertTrue(graphResult.isValid(), "Dependency graph should be valid");
        }

        @Test
        @DisplayName("Should handle validation errors in parsing pipeline")
        void testValidationErrorsInPipeline() {
            String invalidWorkflow = """
                    metadata:
                      name: "A"  # Too short
                      version: "invalid"  # Invalid format
                      description: "Short"  # Too short
                      type: "Invalid_Type"  # Invalid format
                      author: "invalid-email"  # Invalid format
                      created: "invalid-date"  # Invalid format
                      tags: ["INVALID"]  # Invalid format

                    spec:
                      execution:
                        parallelism: 1
                      transferGroups: []
                    """;

            // Schema validation should fail
            ValidationResult schemaResult = parser.validateSchema(invalidWorkflow);
            assertFalse(schemaResult.isValid());
            assertTrue(schemaResult.getErrorCount() >= 6);

            // Parsing should still work (for backward compatibility)
            assertDoesNotThrow(() -> {
                WorkflowDefinition workflow = parser.parseFromString(invalidWorkflow);
                assertNotNull(workflow);
            });
        }

        @Test
        @DisplayName("Should validate existing workflow files")
        void testExistingWorkflowFiles() throws Exception {
            // Test the updated workflow files
            Path simpleWorkflow = Paths.get("src/test/resources/simple-workflow.yaml");
            
            // Parse and validate the file
            WorkflowDefinition workflow = parser.parse(simpleWorkflow);
            assertNotNull(workflow);

            // Validate metadata completeness
            WorkflowDefinition.WorkflowMetadata metadata = workflow.getMetadata();
            assertNotNull(metadata.getName());
            assertNotNull(metadata.getVersion());
            assertNotNull(metadata.getDescription());
            assertNotNull(metadata.getType());
            assertNotNull(metadata.getAuthor());
            assertNotNull(metadata.getCreated());
            assertNotNull(metadata.getTags());
            assertFalse(metadata.getTags().isEmpty());

            // Validate schema compliance
            ValidationResult result = parser.validate(workflow);
            assertTrue(result.isValid(), "Existing workflow file should be schema compliant");
        }
    }

    @Nested
    @DisplayName("Backward Compatibility Tests")
    class BackwardCompatibilityTests {

        @Test
        @DisplayName("Should handle standard workflow format")
        void testLegacyWorkflowCompatibility() throws WorkflowParseException {
            String legacyWorkflow = """
                    apiVersion: v1
                    metadata:
                      name: "legacy-workflow"
                      version: "1.0.0"
                      description: "Legacy workflow using old format for backward compatibility"
                      type: "transfer-workflow"
                      author: "legacy@quorus.dev"
                      created: "2025-08-21"
                      tags: ["legacy", "backward-compatibility"]

                    spec:
                      execution:
                        parallelism: 1
                      transferGroups:
                        - name: legacy-group
                          transfers:
                            - name: legacy-transfer
                              source: "http://legacy.example.com/data"
                              destination: "/tmp/legacy-data.json"
                              protocol: http
                    """;

            // Should parse successfully
            WorkflowDefinition workflow = parser.parseFromString(legacyWorkflow);
            assertNotNull(workflow);

            // Should validate without errors
            ValidationResult result = parser.validateSchema(legacyWorkflow);
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("Should migrate from old metadata format")
        void testMetadataMigration() throws WorkflowParseException {
            // Test workflow with minimal metadata
            String minimalWorkflow = """
                    metadata:
                      name: "minimal-legacy-workflow"
                      version: "1.0.0"
                      description: "Workflow with minimal metadata for migration testing"
                      type: "transfer-workflow"
                      author: "migration@quorus.dev"
                      created: "2025-08-21"
                      tags: ["migration", "minimal"]

                    spec:
                      execution:
                        parallelism: 1
                      transferGroups: []
                    """;

            WorkflowDefinition workflow = parser.parseFromString(minimalWorkflow);
            assertNotNull(workflow);

            // Verify all required fields are present
            WorkflowDefinition.WorkflowMetadata metadata = workflow.getMetadata();
            assertNotNull(metadata.getName());
            assertNotNull(metadata.getVersion());
            assertNotNull(metadata.getDescription());
            assertNotNull(metadata.getType());
            assertNotNull(metadata.getAuthor());
            assertNotNull(metadata.getCreated());
            assertNotNull(metadata.getTags());
        }
    }

    @Nested
    @DisplayName("Performance and Stress Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Should handle large workflows efficiently")
        void testLargeWorkflowPerformance() throws WorkflowParseException {
            StringBuilder largeWorkflow = new StringBuilder();
            largeWorkflow.append("""
                    metadata:
                      name: "large-performance-test-workflow"
                      version: "1.0.0"
                      description: "Large workflow for performance testing with many transfer groups and transfers"
                      type: "data-pipeline-workflow"
                      author: "performance@quorus.dev"
                      created: "2025-08-21"
                      tags: ["performance", "large", "stress-test"]

                    spec:
                      variables:
                        baseUrl: "https://api.example.com"
                        
                      execution:
                        parallelism: 10
                        timeout: 7200s
                        strategy: parallel
                        
                      transferGroups:
                    """);

            // Generate 50 transfer groups with 10 transfers each
            for (int i = 1; i <= 50; i++) {
                largeWorkflow.append(String.format(
                        "        - name: group-%d\n" +
                        "          description: \"Transfer group %d for performance testing\"\n" +
                        "          continueOnError: true\n" +
                        "          retryCount: 2\n" +
                        "          transfers:\n", i, i));

                for (int j = 1; j <= 10; j++) {
                    largeWorkflow.append(String.format(
                            "            - name: transfer-%d-%d\n" +
                            "              source: \"{{baseUrl}}/data/%d/%d\"\n" +
                            "              destination: \"/tmp/data-%d-%d.json\"\n" +
                            "              protocol: https\n", i, j, i, j, i, j));
                }
            }

            String workflowYaml = largeWorkflow.toString();

            // Measure validation performance
            long startTime = System.currentTimeMillis();
            ValidationResult result = parser.validateSchema(workflowYaml);
            long validationTime = System.currentTimeMillis() - startTime;

            assertTrue(result.isValid(), () -> "Large workflow should be valid. Errors: " + result.getErrors());
            assertTrue(validationTime < 5000, "Validation should complete within 5 seconds");

            // Measure parsing performance
            startTime = System.currentTimeMillis();
            WorkflowDefinition workflow = parser.parseFromString(workflowYaml);
            long parsingTime = System.currentTimeMillis() - startTime;

            assertNotNull(workflow);
            assertEquals(50, workflow.getSpec().getTransferGroups().size());
            assertTrue(parsingTime < 5000, "Parsing should complete within 5 seconds");
        }

        @Test
        @DisplayName("Should handle workflows with many tags efficiently")
        void testManyTagsPerformance() {
            StringBuilder manyTags = new StringBuilder();
            manyTags.append("[");
            for (int i = 1; i <= 20; i++) {
                if (i > 1) manyTags.append(", ");
                manyTags.append("\"tag-").append(i).append("\"");
            }
            manyTags.append("]");

            String workflowWithManyTags = String.format("""
                    metadata:
                      name: "many-tags-test-workflow"
                      version: "1.0.0"
                      description: "Workflow with maximum allowed tags for performance testing"
                      type: "validation-test-workflow"
                      author: "performance@quorus.dev"
                      created: "2025-08-21"
                      tags: %s

                    spec:
                      execution:
                        parallelism: 1
                      transferGroups: []
                    """, manyTags.toString());

            long startTime = System.currentTimeMillis();
            ValidationResult result = parser.validateSchema(workflowWithManyTags);
            long validationTime = System.currentTimeMillis() - startTime;

            assertTrue(result.isValid());
            assertTrue(validationTime < 1000, "Tag validation should be fast");
        }
    }

    @Nested
    @DisplayName("Real-world Scenario Tests")
    class RealWorldScenarioTests {

        @Test
        @DisplayName("Should validate ETL pipeline workflow")
        void testETLPipelineWorkflow() throws WorkflowParseException {
            String etlWorkflow = """
                    metadata:
                      name: "customer-data-etl-pipeline"
                      version: "3.2.1"
                      description: "Production ETL pipeline for customer data processing with error handling and monitoring"
                      type: "etl-workflow"
                      author: "data-engineering@company.com"
                      created: "2025-08-21"
                      tags: ["etl", "customer-data", "production", "monitoring"]
                      labels:
                        environment: "production"
                        team: "data-engineering"
                        criticality: "high"
                        schedule: "daily"

                    spec:
                      variables:
                        sourceDb: "postgresql://prod-db:5432/customers"
                        stagingPath: "/data/staging/customers"
                        warehousePath: "/data/warehouse/customers"
                        errorPath: "/data/errors/customers"
                        batchSize: "10000"
                        
                      execution:
                        dryRun: false
                        virtualRun: false
                        parallelism: 5
                        timeout: 14400s
                        strategy: sequential
                        
                      transferGroups:
                        - name: extract-customer-data
                          description: Extract customer data from source systems
                          continueOnError: false
                          retryCount: 3
                          transfers:
                            - name: extract-customer-profiles
                              source: "{{sourceDb}}/customer_profiles"
                              destination: "{{stagingPath}}/profiles.parquet"
                              protocol: database
                              
                        - name: transform-customer-data
                          description: Transform and clean customer data
                          dependsOn:
                            - extract-customer-data
                          continueOnError: false
                          retryCount: 2
                          transfers:
                            - name: clean-customer-data
                              source: "{{stagingPath}}/profiles.parquet"
                              destination: "{{stagingPath}}/profiles_clean.parquet"
                              protocol: file
                              
                        - name: load-customer-data
                          description: Load transformed data to warehouse
                          dependsOn:
                            - transform-customer-data
                          continueOnError: false
                          retryCount: 1
                          transfers:
                            - name: load-to-warehouse
                              source: "{{stagingPath}}/profiles_clean.parquet"
                              destination: "{{warehousePath}}/customer_profiles"
                              protocol: database
                    """;

            ValidationResult result = parser.validateSchema(etlWorkflow);
            assertTrue(result.isValid());

            WorkflowDefinition workflow = parser.parseFromString(etlWorkflow);
            assertNotNull(workflow);
            assertEquals("etl-workflow", workflow.getMetadata().getType());
            assertEquals(3, workflow.getSpec().getTransferGroups().size());
        }

        @Test
        @DisplayName("Should validate backup and sync workflow")
        void testBackupSyncWorkflow() throws WorkflowParseException {
            String backupWorkflow = """
                    metadata:
                      name: "database-backup-and-sync"
                      version: "1.5.2"
                      description: "Automated database backup with cross-region synchronization and verification"
                      type: "backup-workflow"
                      author: "devops@company.com"
                      created: "2025-08-21"
                      tags: ["backup", "sync", "database", "cross-region", "automated"]
                      labels:
                        environment: "production"
                        team: "devops"
                        schedule: "hourly"
                        retention: "30-days"

                    spec:
                      variables:
                        primaryDb: "postgresql://primary:5432/app"
                        backupPath: "/backups/hourly"
                        syncRegion: "us-west-2"
                        verificationPath: "/verification"
                        
                      execution:
                        dryRun: false
                        virtualRun: false
                        parallelism: 2
                        timeout: 3600s
                        strategy: sequential
                        
                      transferGroups:
                        - name: create-backup
                          description: Create database backup
                          continueOnError: false
                          retryCount: 3
                          transfers:
                            - name: dump-database
                              source: "{{primaryDb}}"
                              destination: "{{backupPath}}/backup-{{timestamp}}.sql"
                              protocol: database
                              
                        - name: sync-to-region
                          description: Sync backup to secondary region
                          dependsOn:
                            - create-backup
                          continueOnError: false
                          retryCount: 2
                          transfers:
                            - name: upload-backup
                              source: "{{backupPath}}/backup-{{timestamp}}.sql"
                              destination: "s3://backups-{{syncRegion}}/backup-{{timestamp}}.sql"
                              protocol: s3
                              
                        - name: verify-backup
                          description: Verify backup integrity
                          dependsOn:
                            - sync-to-region
                          continueOnError: true
                          retryCount: 1
                          transfers:
                            - name: restore-test
                              source: "s3://backups-{{syncRegion}}/backup-{{timestamp}}.sql"
                              destination: "{{verificationPath}}/test-restore"
                              protocol: s3
                    """;

            ValidationResult result = parser.validateSchema(backupWorkflow);
            assertTrue(result.isValid());

            WorkflowDefinition workflow = parser.parseFromString(backupWorkflow);
            assertNotNull(workflow);
            assertEquals("backup-workflow", workflow.getMetadata().getType());
            assertTrue(workflow.getMetadata().getTags().contains("automated"));
        }
    }
}
