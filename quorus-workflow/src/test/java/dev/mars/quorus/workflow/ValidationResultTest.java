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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidationResultTest {
    
    @Test
    void testEmptyValidationResult() {
        ValidationResult result = new ValidationResult();
        
        assertTrue(result.isValid());
        assertFalse(result.hasWarnings());
        assertEquals(0, result.getErrorCount());
        assertEquals(0, result.getWarningCount());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
    }
    
    @Test
    void testConstructorWithLists() {
        List<ValidationResult.ValidationIssue> errors = List.of(
                new ValidationResult.ValidationIssue(ValidationResult.ValidationIssue.Severity.ERROR, "Error 1"),
                new ValidationResult.ValidationIssue(ValidationResult.ValidationIssue.Severity.ERROR, "Error 2")
        );
        
        List<ValidationResult.ValidationIssue> warnings = List.of(
                new ValidationResult.ValidationIssue(ValidationResult.ValidationIssue.Severity.WARNING, "Warning 1")
        );
        
        ValidationResult result = new ValidationResult(errors, warnings);
        
        assertFalse(result.isValid());
        assertTrue(result.hasWarnings());
        assertEquals(2, result.getErrorCount());
        assertEquals(1, result.getWarningCount());
        assertEquals(errors, result.getErrors());
        assertEquals(warnings, result.getWarnings());
    }
    
    @Test
    void testConstructorWithNullLists() {
        ValidationResult result = new ValidationResult(null, null);
        
        assertTrue(result.isValid());
        assertFalse(result.hasWarnings());
        assertEquals(0, result.getErrorCount());
        assertEquals(0, result.getWarningCount());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
    }
    
    @Test
    void testAddError() {
        ValidationResult result = new ValidationResult();
        
        result.addError("Simple error message");
        
        assertFalse(result.isValid());
        assertEquals(1, result.getErrorCount());
        assertEquals(0, result.getWarningCount());
        
        ValidationResult.ValidationIssue error = result.getErrors().get(0);
        assertEquals(ValidationResult.ValidationIssue.Severity.ERROR, error.getSeverity());
        assertEquals("Simple error message", error.getMessage());
        assertNull(error.getFieldPath());
        assertEquals(-1, error.getLineNumber());
    }
    
    @Test
    void testAddErrorWithFieldPath() {
        ValidationResult result = new ValidationResult();
        
        result.addError("spec.transfers[0].name", "Name is required");
        
        assertFalse(result.isValid());
        assertEquals(1, result.getErrorCount());
        
        ValidationResult.ValidationIssue error = result.getErrors().get(0);
        assertEquals(ValidationResult.ValidationIssue.Severity.ERROR, error.getSeverity());
        assertEquals("Name is required", error.getMessage());
        assertEquals("spec.transfers[0].name", error.getFieldPath());
        assertEquals(-1, error.getLineNumber());
    }
    
    @Test
    void testAddErrorWithLineNumber() {
        ValidationResult result = new ValidationResult();
        
        result.addError(42, "spec.metadata.name", "Invalid name format");
        
        assertFalse(result.isValid());
        assertEquals(1, result.getErrorCount());
        
        ValidationResult.ValidationIssue error = result.getErrors().get(0);
        assertEquals(ValidationResult.ValidationIssue.Severity.ERROR, error.getSeverity());
        assertEquals("Invalid name format", error.getMessage());
        assertEquals("spec.metadata.name", error.getFieldPath());
        assertEquals(42, error.getLineNumber());
    }
    
    @Test
    void testAddWarning() {
        ValidationResult result = new ValidationResult();
        
        result.addWarning("This is a warning");
        
        assertTrue(result.isValid()); // Still valid with warnings
        assertTrue(result.hasWarnings());
        assertEquals(0, result.getErrorCount());
        assertEquals(1, result.getWarningCount());
        
        ValidationResult.ValidationIssue warning = result.getWarnings().get(0);
        assertEquals(ValidationResult.ValidationIssue.Severity.WARNING, warning.getSeverity());
        assertEquals("This is a warning", warning.getMessage());
        assertNull(warning.getFieldPath());
        assertEquals(-1, warning.getLineNumber());
    }
    
    @Test
    void testAddWarningWithFieldPath() {
        ValidationResult result = new ValidationResult();
        
        result.addWarning("spec.execution.timeout", "Timeout is very high");
        
        assertTrue(result.isValid());
        assertTrue(result.hasWarnings());
        assertEquals(1, result.getWarningCount());
        
        ValidationResult.ValidationIssue warning = result.getWarnings().get(0);
        assertEquals(ValidationResult.ValidationIssue.Severity.WARNING, warning.getSeverity());
        assertEquals("Timeout is very high", warning.getMessage());
        assertEquals("spec.execution.timeout", warning.getFieldPath());
        assertEquals(-1, warning.getLineNumber());
    }
    
    @Test
    void testAddWarningWithLineNumber() {
        ValidationResult result = new ValidationResult();
        
        result.addWarning(15, "spec.variables.env", "Environment variable not set");
        
        assertTrue(result.isValid());
        assertTrue(result.hasWarnings());
        assertEquals(1, result.getWarningCount());
        
        ValidationResult.ValidationIssue warning = result.getWarnings().get(0);
        assertEquals(ValidationResult.ValidationIssue.Severity.WARNING, warning.getSeverity());
        assertEquals("Environment variable not set", warning.getMessage());
        assertEquals("spec.variables.env", warning.getFieldPath());
        assertEquals(15, warning.getLineNumber());
    }
    
    @Test
    void testMixedErrorsAndWarnings() {
        ValidationResult result = new ValidationResult();
        
        result.addError("Critical error");
        result.addError("spec.name", "Name is missing");
        result.addWarning("Minor warning");
        result.addWarning("spec.timeout", "Timeout is high");
        
        assertFalse(result.isValid());
        assertTrue(result.hasWarnings());
        assertEquals(2, result.getErrorCount());
        assertEquals(2, result.getWarningCount());
    }
    
    @Test
    void testImmutability() {
        ValidationResult result = new ValidationResult();
        result.addError("Error");
        result.addWarning("Warning");
        
        List<ValidationResult.ValidationIssue> errors = result.getErrors();
        List<ValidationResult.ValidationIssue> warnings = result.getWarnings();
        
        // Returned lists should be immutable
        assertThrows(UnsupportedOperationException.class, () -> {
            errors.add(new ValidationResult.ValidationIssue(
                    ValidationResult.ValidationIssue.Severity.ERROR, "New error"));
        });
        
        assertThrows(UnsupportedOperationException.class, () -> {
            warnings.add(new ValidationResult.ValidationIssue(
                    ValidationResult.ValidationIssue.Severity.WARNING, "New warning"));
        });
    }
    
    @Test
    void testValidationIssueBasic() {
        ValidationResult.ValidationIssue issue = new ValidationResult.ValidationIssue(
                ValidationResult.ValidationIssue.Severity.ERROR, "Test message");
        
        assertEquals(ValidationResult.ValidationIssue.Severity.ERROR, issue.getSeverity());
        assertEquals("Test message", issue.getMessage());
        assertNull(issue.getFieldPath());
        assertEquals(-1, issue.getLineNumber());
    }
    
    @Test
    void testValidationIssueWithFieldPath() {
        ValidationResult.ValidationIssue issue = new ValidationResult.ValidationIssue(
                ValidationResult.ValidationIssue.Severity.WARNING, "spec.name", "Invalid name");
        
        assertEquals(ValidationResult.ValidationIssue.Severity.WARNING, issue.getSeverity());
        assertEquals("Invalid name", issue.getMessage());
        assertEquals("spec.name", issue.getFieldPath());
        assertEquals(-1, issue.getLineNumber());
    }
    
    @Test
    void testValidationIssueWithLineNumber() {
        ValidationResult.ValidationIssue issue = new ValidationResult.ValidationIssue(
                ValidationResult.ValidationIssue.Severity.ERROR, 25, "spec.transfers", "Invalid transfer");
        
        assertEquals(ValidationResult.ValidationIssue.Severity.ERROR, issue.getSeverity());
        assertEquals("Invalid transfer", issue.getMessage());
        assertEquals("spec.transfers", issue.getFieldPath());
        assertEquals(25, issue.getLineNumber());
    }
    
    @Test
    void testValidationIssueToString() {
        ValidationResult.ValidationIssue issue = new ValidationResult.ValidationIssue(
                ValidationResult.ValidationIssue.Severity.ERROR, 10, "spec.name", "Name is required");
        
        String toString = issue.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("ERROR"));
        assertTrue(toString.contains("10"));
        assertTrue(toString.contains("spec.name"));
        assertTrue(toString.contains("Name is required"));
    }
    
    @Test
    void testValidationIssueEquals() {
        ValidationResult.ValidationIssue issue1 = new ValidationResult.ValidationIssue(
                ValidationResult.ValidationIssue.Severity.ERROR, 5, "field", "message");
        
        ValidationResult.ValidationIssue issue2 = new ValidationResult.ValidationIssue(
                ValidationResult.ValidationIssue.Severity.ERROR, 5, "field", "message");
        
        ValidationResult.ValidationIssue issue3 = new ValidationResult.ValidationIssue(
                ValidationResult.ValidationIssue.Severity.WARNING, 5, "field", "message");
        
        assertEquals(issue1, issue2);
        assertNotEquals(issue1, issue3);
        assertEquals(issue1.hashCode(), issue2.hashCode());
    }
    
    @Test
    void testSeverityEnum() {
        assertEquals(2, ValidationResult.ValidationIssue.Severity.values().length);
        assertEquals(ValidationResult.ValidationIssue.Severity.ERROR, 
                ValidationResult.ValidationIssue.Severity.valueOf("ERROR"));
        assertEquals(ValidationResult.ValidationIssue.Severity.WARNING, 
                ValidationResult.ValidationIssue.Severity.valueOf("WARNING"));
    }
}
