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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for YAML schema validation covering all edge cases,
 * error conditions, and validation scenarios.
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-21
 */
class ComprehensiveSchemaValidationTest {

    private WorkflowSchemaValidator validator;
    private YamlWorkflowDefinitionParser parser;

    @BeforeEach
    void setUp() {
        validator = new WorkflowSchemaValidator();
        parser = new YamlWorkflowDefinitionParser();
    }

    @Nested
    @DisplayName("Metadata Field Validation Tests")
    class MetadataFieldValidationTests {

        @Test
        @DisplayName("Should validate name field constraints")
        void testNameFieldValidation() {
            // Test minimum length
            Map<String, Object> shortName = createMutableMetadata();
            shortName.put("name", "A");
            ValidationResult result = validator.validateMetadataSchema(shortName);
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e ->
                e.getFieldPath().equals("metadata.name") && e.getMessage().contains("at least 2 characters")));

            // Test maximum length
            Map<String, Object> longName = createMutableMetadata();
            longName.put("name", "A".repeat(101));
            result = validator.validateMetadataSchema(longName);
            assertFalse(result.isValid());

            // Test invalid characters
            Map<String, Object> invalidName = createMutableMetadata();
            invalidName.put("name", "Invalid@Name#");
            result = validator.validateMetadataSchema(invalidName);
            assertFalse(result.isValid());

            // Test valid name
            Map<String, Object> validName = createMutableMetadata();
            validName.put("name", "valid-workflow-name-123");
            result = validator.validateMetadataSchema(validName);
            assertTrue(result.isValid());

            // Test name with spaces (should be rejected)
            Map<String, Object> spacedName = createMutableMetadata();
            spacedName.put("name", "Invalid Name With Spaces");
            result = validator.validateMetadataSchema(spacedName);
            assertFalse(result.isValid(), "Name with spaces should fail validation");
        }

        @Test
        @DisplayName("Should validate version field semantic versioning")
        void testVersionFieldValidation() {
            String[] validVersions = {
                "1.0.0", "2.1.3", "10.20.30", "1.0.0-alpha", "1.0.0-beta.1", 
                "1.0.0+build.1", "1.0.0-alpha+build.1"
            };
            
            for (String version : validVersions) {
                Map<String, Object> metadata = createMutableMetadata();
                metadata.put("version", version);
                ValidationResult result = validator.validateMetadataSchema(metadata);
                assertTrue(result.isValid(), "Version " + version + " should be valid");
            }

            String[] invalidVersions = {
                "1.0", "1", "v1.0.0", "1.0.0.0", "1.0.0-", "1.0.0+"
            };

            for (String version : invalidVersions) {
                Map<String, Object> metadata = createMutableMetadata();
                metadata.put("version", version);
                ValidationResult result = validator.validateMetadataSchema(metadata);
                assertFalse(result.isValid(), "Version " + version + " should be invalid");
            }
        }

        @Test
        @DisplayName("Should validate description field length constraints")
        void testDescriptionFieldValidation() {
            // Test minimum length
            Map<String, Object> shortDesc = createMutableMetadata();
            shortDesc.put("description", "Short");
            ValidationResult result = validator.validateMetadataSchema(shortDesc);
            assertFalse(result.isValid());

            // Test maximum length
            Map<String, Object> longDesc = createMutableMetadata();
            longDesc.put("description", "A".repeat(501));
            result = validator.validateMetadataSchema(longDesc);
            assertFalse(result.isValid());

            // Test valid description
            Map<String, Object> validDesc = createMutableMetadata();
            validDesc.put("description", "This is a valid description that meets the minimum length requirement");
            result = validator.validateMetadataSchema(validDesc);
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("Should validate type field format and recommendations")
        void testTypeFieldValidation() {
            // Test valid recommended types
            String[] recommendedTypes = {
                "transfer-workflow", "data-pipeline-workflow", "download-workflow",
                "validation-test-workflow", "external-data-config"
            };
            
            for (String type : recommendedTypes) {
                Map<String, Object> metadata = createMutableMetadata();
                metadata.put("type", type);
                ValidationResult result = validator.validateMetadataSchema(metadata);
                assertTrue(result.isValid());
                assertFalse(result.hasWarnings());
            }

            // Test valid but non-recommended type
            Map<String, Object> customType = createMutableMetadata();
            customType.put("type", "custom-workflow-type");
            ValidationResult result = validator.validateMetadataSchema(customType);
            assertTrue(result.isValid());
            assertTrue(result.hasWarnings());

            // Test invalid type formats
            String[] invalidTypes = {
                "Invalid_Type", "UPPERCASE", "123invalid", "invalid-", "-invalid"
            };

            for (String type : invalidTypes) {
                Map<String, Object> metadata = createMutableMetadata();
                metadata.put("type", type);
                result = validator.validateMetadataSchema(metadata);
                assertFalse(result.isValid(), "Type " + type + " should be invalid");
            }
        }

        @Test
        @DisplayName("Should validate author field email and name formats")
        void testAuthorFieldValidation() {
            // Test valid email formats
            String[] validEmails = {
                "user@example.com", "test.user@company.co.uk", "developer+test@domain.org"
            };
            
            for (String email : validEmails) {
                Map<String, Object> metadata = createMutableMetadata();
                metadata.put("author", email);
                ValidationResult result = validator.validateMetadataSchema(metadata);
                assertTrue(result.isValid(), "Email " + email + " should be valid");
            }

            // Test valid name formats
            String[] validNames = {
                "John Doe", "Jane Smith", "Development Team"
            };

            for (String name : validNames) {
                Map<String, Object> metadata = createMutableMetadata();
                metadata.put("author", name);
                ValidationResult result = validator.validateMetadataSchema(metadata);
                assertTrue(result.isValid(), "Name " + name + " should be valid");
            }

            // Test invalid formats
            String[] invalidAuthors = {
                "invalid-email", "@invalid.com", "user@", "123numbers"
            };

            for (String author : invalidAuthors) {
                Map<String, Object> metadata = createMutableMetadata();
                metadata.put("author", author);
                ValidationResult result = validator.validateMetadataSchema(metadata);
                assertFalse(result.isValid(), "Author " + author + " should be invalid");
            }
        }

        @Test
        @DisplayName("Should validate created date field format and business rules")
        void testCreatedDateValidation() {
            // Test valid date format
            Map<String, Object> validDate = createMutableMetadata();
            validDate.put("created", "2025-08-21");
            ValidationResult result = validator.validateMetadataSchema(validDate);
            assertTrue(result.isValid());

            // Test invalid date formats
            String[] invalidDates = {
                "21-08-2025", "2025/08/21", "08-21-2025", "2025-13-01", "2025-08-32"
            };

            for (String date : invalidDates) {
                Map<String, Object> metadata = createMutableMetadata();
                metadata.put("created", date);
                result = validator.validateMetadataSchema(metadata);
                assertFalse(result.isValid(), "Date " + date + " should be invalid");
            }

            // Test future date warning
            Map<String, Object> futureDate = createMutableMetadata();
            futureDate.put("created", "2030-01-01");
            result = validator.validateMetadataSchema(futureDate);
            assertTrue(result.isValid());
            assertTrue(result.hasWarnings());
        }
    }

    @Nested
    @DisplayName("Tags Validation Tests")
    class TagsValidationTests {

        @Test
        @DisplayName("Should validate tags array constraints")
        void testTagsArrayValidation() {
            // Test empty tags array
            Map<String, Object> emptyTags = createMutableMetadata();
            emptyTags.put("tags", List.of());
            ValidationResult result = validator.validateMetadataSchema(emptyTags);
            assertFalse(result.isValid());

            // Test too many tags
            Map<String, Object> tooManyTags = createMutableMetadata();
            List<String> manyTags = List.of(
                "tag1", "tag2", "tag3", "tag4", "tag5", "tag6", "tag7", "tag8", "tag9", "tag10",
                "tag11", "tag12", "tag13", "tag14", "tag15", "tag16", "tag17", "tag18", "tag19", "tag20", "tag21"
            );
            tooManyTags.put("tags", manyTags);
            result = validator.validateMetadataSchema(tooManyTags);
            assertFalse(result.isValid());

            // Test valid tags count
            Map<String, Object> validTags = createMutableMetadata();
            validTags.put("tags", List.of("test", "validation", "example"));
            result = validator.validateMetadataSchema(validTags);
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("Should validate individual tag formats")
        void testIndividualTagValidation() {
            // Test valid tag formats
            String[] validTags = {
                "test", "validation", "data-pipeline", "example123", "workflow-engine"
            };
            
            Map<String, Object> metadata = createMutableMetadata();
            metadata.put("tags", List.of(validTags));
            ValidationResult result = validator.validateMetadataSchema(metadata);
            assertTrue(result.isValid());

            // Test invalid tag formats
            String[] invalidTags = {
                "UPPERCASE", "invalid_underscore", "123invalid", "invalid-", "-invalid", "tag with spaces"
            };

            for (String tag : invalidTags) {
                metadata = createMutableMetadata();
                metadata.put("tags", List.of("valid-tag", tag));
                result = validator.validateMetadataSchema(metadata);
                assertFalse(result.isValid(), "Tag " + tag + " should be invalid");
            }
        }

        @Test
        @DisplayName("Should detect duplicate tags")
        void testDuplicateTagsValidation() {
            Map<String, Object> duplicateTags = createMutableMetadata();
            duplicateTags.put("tags", List.of("test", "validation", "test", "example"));
            ValidationResult result = validator.validateMetadataSchema(duplicateTags);
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e ->
                e.getMessage().contains("Duplicate tags")));
        }

        @Test
        @DisplayName("Should validate tag length constraints")
        void testTagLengthValidation() {
            // Test tag too long
            Map<String, Object> longTag = createMutableMetadata();
            longTag.put("tags", List.of("test", "a".repeat(31)));
            ValidationResult result = validator.validateMetadataSchema(longTag);
            assertFalse(result.isValid());

            // Test valid tag length
            Map<String, Object> validTag = createMutableMetadata();
            validTag.put("tags", List.of("test", "a".repeat(30)));
            result = validator.validateMetadataSchema(validTag);
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("Should handle non-string tags")
        void testNonStringTagsValidation() {
            Map<String, Object> nonStringTags = createMutableMetadata();
            nonStringTags.put("tags", List.of("valid-tag", 123, true));
            ValidationResult result = validator.validateMetadataSchema(nonStringTags);
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e ->
                e.getMessage().contains("Tag must be a string")));
        }
    }

    @Nested
    @DisplayName("Complete Workflow Validation Tests")
    class CompleteWorkflowValidationTests {

        @Test
        @DisplayName("Should validate complete workflow with all components")
        void testCompleteWorkflowValidation() {
            String completeWorkflow = """
                    metadata:
                      name: "complete-test-workflow"
                      version: "1.2.3"
                      description: "A comprehensive test workflow with all required fields and proper structure"
                      type: "validation-test-workflow"
                      author: "test@quorus.dev"
                      created: "2025-08-21"
                      tags: ["test", "complete", "validation", "comprehensive"]
                      labels:
                        environment: "test"
                        team: "qa"
                        priority: "high"

                    spec:
                      variables:
                        baseUrl: "https://api.example.com"
                        timeout: "30s"
                        retries: "3"
                        
                      execution:
                        dryRun: false
                        virtualRun: false
                        parallelism: 2
                        timeout: 1800s
                        strategy: parallel
                        
                      transferGroups:
                        - name: setup-phase
                          description: Initial setup and configuration
                          continueOnError: false
                          retryCount: 3
                          transfers:
                            - name: download-config
                              source: "{{baseUrl}}/config"
                              destination: "/tmp/config.json"
                              protocol: https
                              
                        - name: processing-phase
                          description: Main data processing
                          dependsOn:
                            - setup-phase
                          continueOnError: true
                          retryCount: 2
                          transfers:
                            - name: process-data
                              source: "/tmp/config.json"
                              destination: "/tmp/processed.json"
                              protocol: file
                    """;

            ValidationResult result = parser.validateSchema(completeWorkflow);
            assertTrue(result.isValid(), "Complete workflow should be valid");
            assertEquals(0, result.getErrorCount());
        }

        @Test
        @DisplayName("Should validate workflow with minimal required fields")
        void testMinimalWorkflowValidation() {
            String minimalWorkflow = """
                    metadata:
                      name: "minimal-workflow"
                      version: "1.0.0"
                      description: "Minimal workflow with only required fields"
                      type: "transfer-workflow"
                      author: "minimal@quorus.dev"
                      created: "2025-08-21"
                      tags: ["minimal"]

                    spec:
                      execution:
                        parallelism: 1
                      transferGroups: []
                    """;

            ValidationResult result = parser.validateSchema(minimalWorkflow);
            assertTrue(result.isValid());
            assertEquals(0, result.getErrorCount());
        }
    }

    @Nested
    @DisplayName("Error Handling and Edge Cases")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle null and empty values gracefully")
        void testNullAndEmptyValueHandling() {
            // Test null metadata
            ValidationResult result = validator.validateMetadataSchema(null);
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> 
                e.getMessage().contains("cannot be null")));

            // Test empty metadata
            result = validator.validateMetadataSchema(Map.of());
            assertFalse(result.isValid());
            assertTrue(result.getErrorCount() >= 7); // All required fields missing

            // Test null field values
            Map<String, Object> nullFields = new java.util.HashMap<>();
            nullFields.put("name", null);
            nullFields.put("version", null);
            nullFields.put("description", null);
            nullFields.put("type", null);
            nullFields.put("author", null);
            nullFields.put("created", null);
            nullFields.put("tags", null);
            result = validator.validateMetadataSchema(nullFields);
            assertFalse(result.isValid());
            assertTrue(result.getErrorCount() >= 7);
        }

        @Test
        @DisplayName("Should handle malformed YAML gracefully")
        void testMalformedYamlHandling() {
            String malformedYaml = """
                    metadata:
                      name: "malformed-workflow"
                      invalid: [unclosed bracket
                      description: "This YAML has syntax errors"
                    """;

            ValidationResult result = parser.validateSchema(malformedYaml);
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> 
                e.getMessage().contains("YAML syntax error")));
        }

        @Test
        @DisplayName("Should validate complex nested structures")
        void testComplexNestedStructures() {
            Map<String, Object> complexWorkflow = Map.of(
                "metadata", createBaseMetadata(),
                "spec", Map.of(
                    "variables", Map.of(
                        "nested", Map.of(
                            "deep", Map.of(
                                "value", "test"
                            )
                        )
                    ),
                    "execution", Map.of("parallelism", 1),
                    "transferGroups", List.of(
                        Map.of(
                            "name", "complex-group",
                            "transfers", List.of(
                                Map.of(
                                    "name", "complex-transfer",
                                    "source", "https://example.com",
                                    "destination", "/tmp/test",
                                    "options", Map.of(
                                        "nested", Map.of(
                                            "option", "value"
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            );

            ValidationResult result = validator.validateWorkflowSchema(complexWorkflow);
            assertTrue(result.isValid());
        }
    }

    private Map<String, Object> createBaseMetadata() {
        return Map.of(
            "name", "test-workflow",
            "version", "1.0.0",
            "description", "A test workflow for validation purposes",
            "type", "validation-test-workflow",
            "author", "test@quorus.dev",
            "created", "2025-08-21",
            "tags", List.of("test", "validation")
        );
    }

    private Map<String, Object> createMutableMetadata() {
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("name", "test-workflow");
        metadata.put("version", "1.0.0");
        metadata.put("description", "A test workflow for validation purposes");
        metadata.put("type", "validation-test-workflow");
        metadata.put("author", "test@quorus.dev");
        metadata.put("created", "2025-08-21");
        metadata.put("tags", List.of("test", "validation"));
        return metadata;
    }
}
