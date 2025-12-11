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
import dev.mars.quorus.workflow.*;

/**
 * Demonstrates the new comprehensive YAML schema validation system.
 * Shows how to validate workflows against the standard metadata schema.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
public class SchemaValidationExample {

    public static void main(String[] args) throws Exception {
        SchemaValidationExample example = new SchemaValidationExample();
        example.runExample();
    }

    public void runExample() throws Exception {
        TestResultLogger.logTestHeader("Quorus Workflow Schema Validation Example");
        
        WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
        
        TestResultLogger.logTestSection("1. Testing valid schema-compliant workflow", false);
        testValidSchemaCompliantWorkflow(parser);
        
        TestResultLogger.logTestSection("2. Testing workflow with missing required metadata", true);
        testMissingRequiredMetadata(parser);
        
        TestResultLogger.logTestSection("3. Testing workflow with invalid field formats", true);
        testInvalidFieldFormats(parser);
        
        TestResultLogger.logTestSection("4. Testing workflow with deprecated 'kind' field", false);
        testDeprecatedKindField(parser);
        
        TestResultLogger.logTestSection("5. Testing comprehensive metadata validation", false);
        testComprehensiveMetadataValidation(parser);
        
        TestResultLogger.logTestFooter("Schema validation examples completed successfully!");
    }
    
    private void testValidSchemaCompliantWorkflow(WorkflowDefinitionParser parser) {
        String validWorkflow = """
                metadata:
                  name: "Schema Validation Test Workflow"
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
                TestResultLogger.logSuccess("✓ Schema validation passed");
                System.out.println("   No validation errors found");
            } else {
                TestResultLogger.logUnexpectedResult("Schema validation failed unexpectedly:");
                schemaResult.getErrors().forEach(error ->
                    System.out.println("     - " + error));
            }
            
            // Test full parsing and validation
            WorkflowDefinition workflow = parser.parseFromString(validWorkflow);
            ValidationResult parseResult = parser.validate(workflow);
            
            if (parseResult.isValid()) {
                TestResultLogger.logSuccess("✓ Full workflow validation passed");
                System.out.println("   Workflow: " + workflow.getMetadata().getName());
                System.out.println("   Type: " + workflow.getMetadata().getType());
                System.out.println("   Tags: " + workflow.getMetadata().getTags());
                System.out.println("   Created: " + workflow.getMetadata().getCreated());
            } else {
                TestResultLogger.logUnexpectedResult("Full validation failed:");
                parseResult.getErrors().forEach(error ->
                    System.out.println("     - " + error));
            }
            
        } catch (Exception e) {
            TestResultLogger.logUnexpectedException("Unexpected error during validation", e);
        }
    }
    
    private void testMissingRequiredMetadata(WorkflowDefinitionParser parser) {
        String incompleteWorkflow = """
                metadata:
                  name: "Incomplete Workflow"
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
                TestResultLogger.logExpectedFailure("✓ Correctly detected missing required fields:");
                result.getErrors().forEach(error ->
                    System.out.println("     - " + error.getFieldPath() + ": " + error.getMessage()));
                    
                // Check for specific missing fields
                String[] requiredFields = {"description", "type", "author", "created", "tags"};
                for (String field : requiredFields) {
                    boolean found = result.getErrors().stream()
                        .anyMatch(e -> e.getFieldPath().contains(field));
                    if (found) {
                        System.out.println("   ✓ Detected missing: " + field);
                    } else {
                        System.out.println("   ✗ Failed to detect missing: " + field);
                    }
                }
            } else {
                TestResultLogger.logUnexpectedResult("Failed to detect missing required fields - this indicates a bug!");
            }
            
        } catch (Exception e) {
            TestResultLogger.logExpectedFailure("Correctly caught validation error", e);
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
                TestResultLogger.logExpectedFailure("✓ Correctly detected invalid field formats:");
                result.getErrors().forEach(error ->
                    System.out.println("     - " + error.getFieldPath() + ": " + error.getMessage()));
                    
                System.out.println("   Total validation errors: " + result.getErrorCount());
            } else {
                TestResultLogger.logUnexpectedResult("Failed to detect invalid field formats - this indicates a bug!");
            }
            
        } catch (Exception e) {
            TestResultLogger.logExpectedFailure("Correctly caught validation error", e);
        }
    }
    
    private void testDeprecatedKindField(WorkflowDefinitionParser parser) {
        String workflowWithKind = """
                apiVersion: v1
                kind: TransferWorkflow  # Deprecated field
                metadata:
                  name: "Deprecated Kind Test"
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
                TestResultLogger.logSuccess("✓ Correctly detected deprecated 'kind' field:");
                result.getWarnings().forEach(warning ->
                    System.out.println("     - " + warning.getFieldPath() + ": " + warning.getMessage()));
            } else if (!result.hasWarnings()) {
                TestResultLogger.logUnexpectedResult("Failed to generate warning for deprecated 'kind' field");
            } else {
                TestResultLogger.logUnexpectedResult("Validation failed when it should have passed with warnings");
                result.getErrors().forEach(error ->
                    System.out.println("     - " + error));
            }
            
        } catch (Exception e) {
            TestResultLogger.logUnexpectedException("Unexpected error during deprecated field test", e);
        }
    }
    
    private void testComprehensiveMetadataValidation(WorkflowDefinitionParser parser) {
        System.out.println("   Testing various metadata validation scenarios...");
        
        // Test valid email vs name author formats
        testAuthorFormats(parser);
        
        // Test date validation
        testDateValidation(parser);
        
        // Test tag validation
        testTagValidation(parser);
        
        System.out.println("   ✓ Comprehensive metadata validation tests completed");
    }
    
    private void testAuthorFormats(WorkflowDefinitionParser parser) {
        // Valid email format
        String emailWorkflow = createTestWorkflow("test@company.com");
        ValidationResult emailResult = parser.validateSchema(emailWorkflow);
        System.out.println("   Email author format: " + (emailResult.isValid() ? "✓ Valid" : "✗ Invalid"));
        
        // Valid name format
        String nameWorkflow = createTestWorkflow("John Doe");
        ValidationResult nameResult = parser.validateSchema(nameWorkflow);
        System.out.println("   Name author format: " + (nameResult.isValid() ? "✓ Valid" : "✗ Invalid"));
    }
    
    private void testDateValidation(WorkflowDefinitionParser parser) {
        String validDateWorkflow = createTestWorkflowWithDate("2025-08-21");
        ValidationResult validResult = parser.validateSchema(validDateWorkflow);
        System.out.println("   Valid date format: " + (validResult.isValid() ? "✓ Valid" : "✗ Invalid"));
        
        String invalidDateWorkflow = createTestWorkflowWithDate("21-08-2025");
        ValidationResult invalidResult = parser.validateSchema(invalidDateWorkflow);
        System.out.println("   Invalid date format: " + (!invalidResult.isValid() ? "✓ Correctly rejected" : "✗ Incorrectly accepted"));
    }
    
    private void testTagValidation(WorkflowDefinitionParser parser) {
        String validTagsWorkflow = createTestWorkflowWithTags("[\"test\", \"validation\", \"example\"]");
        ValidationResult validResult = parser.validateSchema(validTagsWorkflow);
        System.out.println("   Valid tags format: " + (validResult.isValid() ? "✓ Valid" : "✗ Invalid"));
        
        String invalidTagsWorkflow = createTestWorkflowWithTags("[\"INVALID\", \"invalid_underscore\"]");
        ValidationResult invalidResult = parser.validateSchema(invalidTagsWorkflow);
        System.out.println("   Invalid tags format: " + (!invalidResult.isValid() ? "✓ Correctly rejected" : "✗ Incorrectly accepted"));
    }
    
    private String createTestWorkflow(String author) {
        return String.format("""
                metadata:
                  name: "Author Test Workflow"
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
                  name: "Date Test Workflow"
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
                  name: "Tags Test Workflow"
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
