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
import dev.mars.quorus.workflow.*;

/**
 * Demonstrates the new comprehensive YAML schema validation system.
 * Shows how to validate workflows against the standard metadata schema.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-21
 * @version 1.0
 */
public class SchemaValidationExample {

    private static final ExampleLogger log = ExampleLogger.getLogger(SchemaValidationExample.class);

    public static void main(String[] args) throws Exception {
        SchemaValidationExample example = new SchemaValidationExample();
        example.runExample();
    }

    public void runExample() throws Exception {
        log.header("Quorus Workflow Schema Validation Example");
        
        WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
        
        log.testSection("1. Testing valid schema-compliant workflow", false);
        testValidSchemaCompliantWorkflow(parser);
        
        log.testSection("2. Testing workflow with missing required metadata", true);
        testMissingRequiredMetadata(parser);
        
        log.testSection("3. Testing workflow with invalid field formats", true);
        testInvalidFieldFormats(parser);
        
        log.testSection("4. Testing workflow with deprecated 'kind' field", false);
        testDeprecatedKindField(parser);
        
        log.testSection("5. Testing comprehensive metadata validation", false);
        testComprehensiveMetadataValidation(parser);
        
        log.info("Schema validation examples completed successfully!");
    }
    
    private void testValidSchemaCompliantWorkflow(WorkflowDefinitionParser parser) {
        String validWorkflow = """
                metadata:
                  name: "schema-validation-test-workflow"
                  version: "1.0.0"
                  description: "A comprehensive test workflow demonstrating proper schema compliance"
                  type: "validation-test-workflow"
                  author: "validation@quorus.dev"
                  created: "2025-08-21"
                  tags: ["test", "validation", "schema", "example"]
                  labels:
                    environment: "test"
                    category: "validation"

                spec:
                  variables:
                    testUrl: "https://httpbin.org"
                    outputDir: "/tmp/validation-test"
                    
                  execution:
                    dryRun: false
                    virtualRun: false
                    parallelism: 1
                    timeout: 300s
                    strategy: sequential
                    
                  transferGroups:
                    - name: validation-test-group
                      description: Test group for schema validation
                      transfers:
                        - name: test-transfer
                          source: "{{testUrl}}/json"
                          destination: "{{outputDir}}/test.json"
                          protocol: http
                """;

        try {
            // Test schema validation
            ValidationResult schemaResult = parser.validateSchema(validWorkflow);
            if (schemaResult.isValid()) {
                log.success("Schema validation passed");
                log.detail("No validation errors found");
            } else {
                log.failure("UNEXPECTED: Schema validation failed unexpectedly:");
                schemaResult.getErrors().forEach(error ->
                    log.subDetail(error.toString()));
            }
            
            // Test full parsing and validation
            WorkflowDefinition workflow = parser.parseFromString(validWorkflow);
            ValidationResult parseResult = parser.validate(workflow);
            
            if (parseResult.isValid()) {
                log.success("Full workflow validation passed");
                log.keyValue("Workflow", workflow.getMetadata().getName());
                log.keyValue("Type", workflow.getMetadata().getType());
                log.keyValue("Tags", workflow.getMetadata().getTags());
                log.keyValue("Created", workflow.getMetadata().getCreated());
            } else {
                log.failure("UNEXPECTED: Full validation failed:");
                parseResult.getErrors().forEach(error ->
                    log.subDetail(error.toString()));
            }
            
        } catch (Exception e) {
            log.failure("UNEXPECTED: Unexpected error during validation: " + e.getMessage());
        }
    }
    
    private void testMissingRequiredMetadata(WorkflowDefinitionParser parser) {
        String incompleteWorkflow = """
                metadata:
                  name: "incomplete-workflow"
                  version: "1.0.0"
                  # Missing: description, type, author, created, tags

                spec:
                  execution:
                    parallelism: 1
                  transferGroups: []
                """;

        try {
            ValidationResult result = parser.validateSchema(incompleteWorkflow);
            
            if (!result.isValid()) {
                log.expectedFailure("Correctly detected missing required fields:");
                result.getErrors().forEach(error ->
                    log.subDetail(error.getFieldPath() + ": " + error.getMessage()));
                    
                // Check for specific missing fields
                String[] requiredFields = {"description", "type", "author", "created", "tags"};
                for (String field : requiredFields) {
                    boolean found = result.getErrors().stream()
                        .anyMatch(e -> e.getFieldPath().contains(field));
                    if (found) {
                        log.success("Detected missing: " + field);
                    } else {
                        log.failure("Failed to detect missing: " + field);
                    }
                }
            } else {
                log.failure("UNEXPECTED: Failed to detect missing required fields - this indicates a bug!");
            }
            
        } catch (Exception e) {
            log.expectedFailure("Correctly caught validation error: " + e.getMessage());
        }
    }
    
    private void testInvalidFieldFormats(WorkflowDefinitionParser parser) {
        String invalidWorkflow = """
                metadata:
                  name: "A"  # Too short
                  version: "invalid-version"  # Invalid format
                  description: "Short"  # Too short
                  type: "Invalid_Type"  # Invalid format
                  author: "invalid-email"  # Invalid email
                  created: "2025-13-45"  # Invalid date
                  tags: ["Invalid_Tag", "UPPERCASE"]  # Invalid tag formats

                spec:
                  execution:
                    parallelism: 1
                  transferGroups: []
                """;

        try {
            ValidationResult result = parser.validateSchema(invalidWorkflow);
            
            if (!result.isValid()) {
                log.expectedFailure("Correctly detected invalid field formats:");
                result.getErrors().forEach(error ->
                    log.subDetail(error.getFieldPath() + ": " + error.getMessage()));
                    
                log.keyValue("Total validation errors", result.getErrorCount());
            } else {
                log.failure("UNEXPECTED: Failed to detect invalid field formats - this indicates a bug!");
            }
            
        } catch (Exception e) {
            log.expectedFailure("Correctly caught validation error: " + e.getMessage());
        }
    }
    
    private void testDeprecatedKindField(WorkflowDefinitionParser parser) {
        String workflowWithKind = """
                apiVersion: v1
                kind: TransferWorkflow  # Deprecated field
                metadata:
                  name: "deprecated-kind-test"
                  version: "1.0.0"
                  description: "Testing deprecated kind field warning"
                  type: "validation-test-workflow"
                  author: "test@quorus.dev"
                  created: "2025-08-21"
                  tags: ["test", "deprecated", "kind"]

                spec:
                  execution:
                    parallelism: 1
                  transferGroups: []
                """;

        try {
            ValidationResult result = parser.validateSchema(workflowWithKind);
            
            if (result.isValid() && result.hasWarnings()) {
                log.success("Correctly detected deprecated 'kind' field:");
                result.getWarnings().forEach(warning ->
                    log.subDetail(warning.getFieldPath() + ": " + warning.getMessage()));
            } else if (!result.hasWarnings()) {
                log.failure("UNEXPECTED: Failed to generate warning for deprecated 'kind' field");
            } else {
                log.failure("UNEXPECTED: Validation failed when it should have passed with warnings");
                result.getErrors().forEach(error ->
                    log.subDetail(error.toString()));
            }
            
        } catch (Exception e) {
            log.failure("UNEXPECTED: Unexpected error during deprecated field test: " + e.getMessage());
        }
    }
    
    private void testComprehensiveMetadataValidation(WorkflowDefinitionParser parser) {
        log.detail("Testing various metadata validation scenarios...");
        
        // Test valid email vs name author formats
        testAuthorFormats(parser);
        
        // Test date validation
        testDateValidation(parser);
        
        // Test tag validation
        testTagValidation(parser);
        
        log.success("Comprehensive metadata validation tests completed");
    }
    
    private void testAuthorFormats(WorkflowDefinitionParser parser) {
        // Valid email format
        String emailWorkflow = createTestWorkflow("test@company.com");
        ValidationResult emailResult = parser.validateSchema(emailWorkflow);
        log.keyValue("Email author format", (emailResult.isValid() ? "✓ Valid" : "✗ Invalid"));
        
        // Valid name format
        String nameWorkflow = createTestWorkflow("John Doe");
        ValidationResult nameResult = parser.validateSchema(nameWorkflow);
        log.keyValue("Name author format", (nameResult.isValid() ? "✓ Valid" : "✗ Invalid"));
    }
    
    private void testDateValidation(WorkflowDefinitionParser parser) {
        String validDateWorkflow = createTestWorkflowWithDate("2025-08-21");
        ValidationResult validResult = parser.validateSchema(validDateWorkflow);
        log.keyValue("Valid date format", (validResult.isValid() ? "✓ Valid" : "✗ Invalid"));
        
        String invalidDateWorkflow = createTestWorkflowWithDate("21-08-2025");
        ValidationResult invalidResult = parser.validateSchema(invalidDateWorkflow);
        log.keyValue("Invalid date format", (!invalidResult.isValid() ? "✓ Correctly rejected" : "✗ Incorrectly accepted"));
    }
    
    private void testTagValidation(WorkflowDefinitionParser parser) {
        String validTagsWorkflow = createTestWorkflowWithTags("[\"test\", \"validation\", \"example\"]");
        ValidationResult validResult = parser.validateSchema(validTagsWorkflow);
        log.keyValue("Valid tags format", (validResult.isValid() ? "✓ Valid" : "✗ Invalid"));
        
        String invalidTagsWorkflow = createTestWorkflowWithTags("[\"INVALID\", \"invalid_underscore\"]");
        ValidationResult invalidResult = parser.validateSchema(invalidTagsWorkflow);
        log.keyValue("Invalid tags format", (!invalidResult.isValid() ? "✓ Correctly rejected" : "✗ Incorrectly accepted"));
    }
    
    private String createTestWorkflow(String author) {
        return String.format("""
                metadata:
                  name: "author-test-workflow"
                  version: "1.0.0"
                  description: "Testing author field validation"
                  type: "validation-test-workflow"
                  author: "%s"
                  created: "2025-08-21"
                  tags: ["test", "author"]
                spec:
                  execution:
                    parallelism: 1
                  transferGroups: []
                """, author);
    }
    
    private String createTestWorkflowWithDate(String date) {
        return String.format("""
                metadata:
                  name: "date-test-workflow"
                  version: "1.0.0"
                  description: "Testing date field validation"
                  type: "validation-test-workflow"
                  author: "test@quorus.dev"
                  created: "%s"
                  tags: ["test", "date"]
                spec:
                  execution:
                    parallelism: 1
                  transferGroups: []
                """, date);
    }
    
    private String createTestWorkflowWithTags(String tags) {
        return String.format("""
                metadata:
                  name: "tags-test-workflow"
                  version: "1.0.0"
                  description: "Testing tags field validation"
                  type: "validation-test-workflow"
                  author: "test@quorus.dev"
                  created: "2025-08-21"
                  tags: %s
                spec:
                  execution:
                    parallelism: 1
                  transferGroups: []
                """, tags);
    }
}
