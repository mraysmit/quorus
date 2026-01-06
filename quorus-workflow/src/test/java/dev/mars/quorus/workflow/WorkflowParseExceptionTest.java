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

import static org.junit.jupiter.api.Assertions.*;
/**
 * Description for WorkflowParseExceptionTest
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */

class WorkflowParseExceptionTest {
    
    @Test
    void testBasicConstructor() {
        String message = "Failed to parse workflow definition";
        
        WorkflowParseException exception = new WorkflowParseException(message);
        
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
        assertNull(exception.getWorkflowName());
        assertEquals(-1, exception.getLineNumber());
        assertNull(exception.getFieldPath());
    }
    
    @Test
    void testConstructorWithCause() {
        String message = "YAML parsing failed";
        RuntimeException cause = new RuntimeException("Invalid YAML syntax");
        
        WorkflowParseException exception = new WorkflowParseException(message, cause);
        
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertNull(exception.getWorkflowName());
        assertEquals(-1, exception.getLineNumber());
        assertNull(exception.getFieldPath());
    }
    
    @Test
    void testConstructorWithWorkflowName() {
        String workflowName = "test-workflow";
        String message = "Workflow validation failed";
        
        WorkflowParseException exception = new WorkflowParseException(workflowName, message);
        
        assertTrue(exception.getMessage().contains(workflowName));
        assertTrue(exception.getMessage().contains(message));
        assertNull(exception.getCause());
        assertEquals(workflowName, exception.getWorkflowName());
        assertEquals(-1, exception.getLineNumber());
        assertNull(exception.getFieldPath());
    }
    
    @Test
    void testConstructorWithWorkflowNameAndCause() {
        String workflowName = "test-workflow";
        String message = "Complete parsing failure";
        RuntimeException cause = new RuntimeException("Underlying error");
        
        WorkflowParseException exception = new WorkflowParseException(workflowName, message, cause);
        
        assertTrue(exception.getMessage().contains(workflowName));
        assertTrue(exception.getMessage().contains(message));
        assertEquals(cause, exception.getCause());
        assertEquals(workflowName, exception.getWorkflowName());
        assertEquals(-1, exception.getLineNumber());
        assertNull(exception.getFieldPath());
    }
    
    @Test
    void testConstructorWithFullDetails() {
        String workflowName = "detailed-workflow";
        int lineNumber = 42;
        String fieldPath = "spec.transfers[0].name";
        String message = "Field validation failed";
        
        WorkflowParseException exception = new WorkflowParseException(
                workflowName, lineNumber, fieldPath, message);
        
        String fullMessage = exception.getMessage();
        assertTrue(fullMessage.contains(workflowName));
        assertTrue(fullMessage.contains(String.valueOf(lineNumber)));
        assertTrue(fullMessage.contains(fieldPath));
        assertTrue(fullMessage.contains(message));
        
        assertEquals(workflowName, exception.getWorkflowName());
        assertEquals(lineNumber, exception.getLineNumber());
        assertEquals(fieldPath, exception.getFieldPath());
        assertNull(exception.getCause());
    }
    
    @Test
    void testConstructorWithFullDetailsAndCause() {
        String workflowName = "detailed-workflow";
        int lineNumber = 25;
        String fieldPath = "spec.metadata.name";
        String message = "Invalid name format";
        RuntimeException cause = new RuntimeException("Validation error");
        
        WorkflowParseException exception = new WorkflowParseException(
                workflowName, lineNumber, fieldPath, message, cause);
        
        String fullMessage = exception.getMessage();
        assertTrue(fullMessage.contains(workflowName));
        assertTrue(fullMessage.contains(String.valueOf(lineNumber)));
        assertTrue(fullMessage.contains(fieldPath));
        assertTrue(fullMessage.contains(message));
        
        assertEquals(workflowName, exception.getWorkflowName());
        assertEquals(lineNumber, exception.getLineNumber());
        assertEquals(fieldPath, exception.getFieldPath());
        assertEquals(cause, exception.getCause());
    }
    
    @Test
    void testMessageFormatting() {
        // Test message formatting with workflow name only
        WorkflowParseException exception1 = new WorkflowParseException("test-workflow", "Error message");
        assertTrue(exception1.getMessage().startsWith("Workflow 'test-workflow': Error message"));
        
        // Test message formatting with line number
        WorkflowParseException exception2 = new WorkflowParseException("test-workflow", 10, null, "Line error");
        String message2 = exception2.getMessage();
        assertTrue(message2.contains("Workflow 'test-workflow'"));
        assertTrue(message2.contains("Line 10"));
        assertTrue(message2.contains("Line error"));
        
        // Test message formatting with field path
        WorkflowParseException exception3 = new WorkflowParseException("test-workflow", -1, "spec.name", "Field error");
        String message3 = exception3.getMessage();
        assertTrue(message3.contains("Workflow 'test-workflow'"));
        assertTrue(message3.contains("Field 'spec.name'"));
        assertTrue(message3.contains("Field error"));
        
        // Test message formatting with all details
        WorkflowParseException exception4 = new WorkflowParseException("test-workflow", 15, "spec.transfers", "Complete error");
        String message4 = exception4.getMessage();
        assertTrue(message4.contains("Workflow 'test-workflow'"));
        assertTrue(message4.contains("Line 15"));
        assertTrue(message4.contains("Field 'spec.transfers'"));
        assertTrue(message4.contains("Complete error"));
    }
    
    @Test
    void testExceptionInheritance() {
        WorkflowParseException exception = new WorkflowParseException("Test");

        assertTrue(exception instanceof Exception);
        // Note: WorkflowParseException extends Exception, not RuntimeException
    }
    
    @Test
    void testToString() {
        WorkflowParseException exception = new WorkflowParseException("test-workflow", "Test exception");
        
        String toString = exception.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("WorkflowParseException"));
        assertTrue(toString.contains("test-workflow"));
        assertTrue(toString.contains("Test exception"));
    }
    
    @Test
    void testExceptionChaining() {
        RuntimeException rootCause = new RuntimeException("Root cause");
        Exception intermediateCause = new Exception("Intermediate cause", rootCause);
        WorkflowParseException exception = new WorkflowParseException("Parse exception", intermediateCause);

        assertEquals(intermediateCause, exception.getCause());
        assertEquals(rootCause, exception.getCause().getCause());
    }
    
    @Test
    void testNullWorkflowName() {
        WorkflowParseException exception = new WorkflowParseException(null, "Test message");
        
        // Should not include workflow name in message when null
        assertEquals("Test message", exception.getMessage());
        assertNull(exception.getWorkflowName());
    }
    
    @Test
    void testZeroLineNumber() {
        WorkflowParseException exception = new WorkflowParseException("workflow", 0, null, "Test message");
        
        // Line number 0 or negative should not be included in message
        String message = exception.getMessage();
        assertFalse(message.contains("Line 0"));
        assertEquals(0, exception.getLineNumber());
    }
    
    @Test
    void testNullFieldPath() {
        WorkflowParseException exception = new WorkflowParseException("workflow", 10, null, "Test message");
        
        // Should not include field path in message when null
        String message = exception.getMessage();
        assertFalse(message.contains("Field"));
        assertNull(exception.getFieldPath());
    }
    
    @Test
    void testEmptyFieldPath() {
        WorkflowParseException exception = new WorkflowParseException("workflow", 10, "", "Test message");
        
        // Should handle empty field path gracefully
        assertEquals("", exception.getFieldPath());
    }
    
    @Test
    void testComplexScenario() {
        // Test a complex real-world scenario
        String workflowName = "production-data-sync";
        int lineNumber = 127;
        String fieldPath = "spec.transferGroups[2].transfers[0].destination";
        String message = "Destination path is not accessible";
        RuntimeException cause = new RuntimeException("Permission denied");
        
        WorkflowParseException exception = new WorkflowParseException(
                workflowName, lineNumber, fieldPath, message, cause);
        
        // Verify all components are preserved
        assertEquals(workflowName, exception.getWorkflowName());
        assertEquals(lineNumber, exception.getLineNumber());
        assertEquals(fieldPath, exception.getFieldPath());
        assertEquals(cause, exception.getCause());
        
        // Verify message formatting
        String fullMessage = exception.getMessage();
        assertTrue(fullMessage.contains("production-data-sync"));
        assertTrue(fullMessage.contains("127"));
        assertTrue(fullMessage.contains("spec.transferGroups[2].transfers[0].destination"));
        assertTrue(fullMessage.contains("Destination path is not accessible"));
    }
}
