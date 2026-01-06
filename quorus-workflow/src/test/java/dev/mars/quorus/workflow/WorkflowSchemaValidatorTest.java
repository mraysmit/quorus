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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the WorkflowSchemaValidator.
 * Tests the new standard metadata schema validation.
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-21
 */
class WorkflowSchemaValidatorTest {

    private WorkflowSchemaValidator validator;

    @BeforeEach
    void setUp() {
        validator = new WorkflowSchemaValidator();
    }

    @Test
    void testValidCompleteMetadata() {
        Map<String, Object> metadata = Map.of(
            "name", "Test Workflow",
            "version", "1.0.0",
            "description", "A comprehensive test workflow for validation",
            "type", "validation-test-workflow",
            "author", "test@quorus.dev",
            "created", "2025-08-21",
            "tags", List.of("test", "validation", "example")
        );

        ValidationResult result = validator.validateMetadataSchema(metadata);
        
        assertTrue(result.isValid(), "Valid metadata should pass validation");
        assertEquals(0, result.getErrorCount(), "Should have no errors");
    }

    @Test
    void testMissingRequiredFields() {
        Map<String, Object> metadata = Map.of(
            "name", "Incomplete Workflow",
            "version", "1.0.0"
            // Missing: description, type, author, created, tags
        );

        ValidationResult result = validator.validateMetadataSchema(metadata);
        
        assertFalse(result.isValid(), "Incomplete metadata should fail validation");
        assertTrue(result.getErrorCount() >= 4, "Should have errors for missing required fields");
        
        // Check specific missing fields
        assertTrue(result.getErrors().stream().anyMatch(e -> 
            e.getFieldPath().equals("metadata.description")), "Should report missing description");
        assertTrue(result.getErrors().stream().anyMatch(e -> 
            e.getFieldPath().equals("metadata.type")), "Should report missing type");
        assertTrue(result.getErrors().stream().anyMatch(e -> 
            e.getFieldPath().equals("metadata.author")), "Should report missing author");
        assertTrue(result.getErrors().stream().anyMatch(e -> 
            e.getFieldPath().equals("metadata.created")), "Should report missing created");
        assertTrue(result.getErrors().stream().anyMatch(e -> 
            e.getFieldPath().equals("metadata.tags")), "Should report missing tags");
    }

    @Test
    void testInvalidFieldFormats() {
        Map<String, Object> metadata = Map.of(
            "name", "A", // Too short
            "version", "invalid-version", // Invalid format
            "description", "Short", // Too short
            "type", "Invalid_Type", // Invalid format (uppercase, underscore)
            "author", "invalid-email", // Invalid email format
            "created", "2025-13-45", // Invalid date
            "tags", List.of("Invalid_Tag", "UPPERCASE") // Invalid tag formats
        );

        ValidationResult result = validator.validateMetadataSchema(metadata);
        
        assertFalse(result.isValid(), "Invalid formats should fail validation");
        assertTrue(result.getErrorCount() >= 6, "Should have multiple format errors");
    }

    @Test
    void testValidEmailAndNameAuthors() {
        // Test valid email
        Map<String, Object> metadataWithEmail = Map.of(
            "name", "Email Test Workflow",
            "version", "1.0.0",
            "description", "Testing email validation for author field",
            "type", "validation-test-workflow",
            "author", "developer@company.com",
            "created", "2025-08-21",
            "tags", List.of("test", "email")
        );

        ValidationResult result = validator.validateMetadataSchema(metadataWithEmail);
        assertTrue(result.isValid(), "Valid email author should pass");

        // Test valid name
        Map<String, Object> metadataWithName = Map.of(
            "name", "Name Test Workflow",
            "version", "1.0.0",
            "description", "Testing name validation for author field",
            "type", "validation-test-workflow",
            "author", "John Doe",
            "created", "2025-08-21",
            "tags", List.of("test", "name")
        );

        result = validator.validateMetadataSchema(metadataWithName);
        assertTrue(result.isValid(), "Valid name author should pass");
    }

    @Test
    void testDateValidation() {
        // Test valid date
        Map<String, Object> validDate = Map.of(
            "name", "Date Test Workflow",
            "version", "1.0.0",
            "description", "Testing date validation for created field",
            "type", "validation-test-workflow",
            "author", "test@quorus.dev",
            "created", "2025-08-21",
            "tags", List.of("test", "date")
        );

        ValidationResult result = validator.validateMetadataSchema(validDate);
        assertTrue(result.isValid(), "Valid date should pass");

        // Test invalid date format
        Map<String, Object> invalidDate = Map.of(
            "name", "Invalid Date Test",
            "version", "1.0.0",
            "description", "Testing invalid date format",
            "type", "validation-test-workflow",
            "author", "test@quorus.dev",
            "created", "21-08-2025", // Wrong format
            "tags", List.of("test", "invalid-date")
        );

        result = validator.validateMetadataSchema(invalidDate);
        assertFalse(result.isValid(), "Invalid date format should fail");
    }

    @Test
    void testTagsValidation() {
        // Test valid tags
        Map<String, Object> validTags = Map.of(
            "name", "Tags Test Workflow",
            "version", "1.0.0",
            "description", "Testing tags validation with proper format",
            "type", "validation-test-workflow",
            "author", "test@quorus.dev",
            "created", "2025-08-21",
            "tags", List.of("test", "validation", "tags-example", "workflow")
        );

        ValidationResult result = validator.validateMetadataSchema(validTags);
        assertTrue(result.isValid(), "Valid tags should pass");

        // Test invalid tags
        Map<String, Object> invalidTags = Map.of(
            "name", "Invalid Tags Test",
            "version", "1.0.0",
            "description", "Testing invalid tags format",
            "type", "validation-test-workflow",
            "author", "test@quorus.dev",
            "created", "2025-08-21",
            "tags", List.of("UPPERCASE", "invalid_underscore", "123invalid", "valid-tag")
        );

        result = validator.validateMetadataSchema(invalidTags);
        assertFalse(result.isValid(), "Invalid tags should fail");
        assertTrue(result.getErrorCount() >= 3, "Should have errors for invalid tag formats");
    }

    @Test
    void testDuplicateTags() {
        Map<String, Object> duplicateTags = Map.of(
            "name", "Duplicate Tags Test",
            "version", "1.0.0",
            "description", "Testing duplicate tags detection",
            "type", "validation-test-workflow",
            "author", "test@quorus.dev",
            "created", "2025-08-21",
            "tags", List.of("test", "validation", "test") // Duplicate "test"
        );

        ValidationResult result = validator.validateMetadataSchema(duplicateTags);
        assertFalse(result.isValid(), "Duplicate tags should fail validation");
        assertTrue(result.getErrors().stream().anyMatch(e -> 
            e.getMessage().contains("Duplicate tags")), "Should report duplicate tags error");
    }

    @Test
    void testEmptyTags() {
        Map<String, Object> emptyTags = Map.of(
            "name", "Empty Tags Test",
            "version", "1.0.0",
            "description", "Testing empty tags array",
            "type", "validation-test-workflow",
            "author", "test@quorus.dev",
            "created", "2025-08-21",
            "tags", List.of() // Empty tags array
        );

        ValidationResult result = validator.validateMetadataSchema(emptyTags);
        assertFalse(result.isValid(), "Empty tags should fail validation");
        assertTrue(result.getErrors().stream().anyMatch(e -> 
            e.getFieldPath().equals("metadata.tags")), "Should report empty tags error");
    }

    @Test
    void testWorkflowTypeWarning() {
        Map<String, Object> customType = Map.of(
            "name", "Custom Type Test",
            "version", "1.0.0",
            "description", "Testing custom workflow type warning",
            "type", "custom-workflow-type", // Not in recommended list
            "author", "test@quorus.dev",
            "created", "2025-08-21",
            "tags", List.of("test", "custom-type")
        );

        ValidationResult result = validator.validateMetadataSchema(customType);
        assertTrue(result.isValid(), "Custom type should be valid but generate warning");
        assertTrue(result.hasWarnings(), "Should have warnings for non-standard type");
        assertTrue(result.getWarnings().stream().anyMatch(w -> 
            w.getMessage().contains("not in the list of recommended")), "Should warn about non-standard type");
    }

    @Test
    void testCompleteWorkflowValidation() {
        Map<String, Object> completeWorkflow = Map.of(
            "metadata", Map.of(
                "name", "Complete Workflow Test",
                "version", "1.0.0",
                "description", "Testing complete workflow validation",
                "type", "validation-test-workflow",
                "author", "test@quorus.dev",
                "created", "2025-08-21",
                "tags", List.of("test", "complete", "validation")
            ),
            "spec", Map.of(
                "execution", Map.of("parallelism", 1),
                "transferGroups", List.of()
            )
        );

        ValidationResult result = validator.validateWorkflowSchema(completeWorkflow);
        assertTrue(result.isValid(), "Complete valid workflow should pass");
    }
}
