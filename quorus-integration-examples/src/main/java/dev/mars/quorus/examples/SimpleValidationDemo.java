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

/**
 * Simple demonstration of the improved error handling approach.
 * This shows how intentional failures are now clearly distinguished from actual errors.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-18
 * @version 1.0
 */
public class SimpleValidationDemo {
    
    public static void main(String[] args) {
        System.out.println("=== Quorus Error Handling Demonstration ===");
        System.out.println("This example demonstrates the improved error handling approach.");
        System.out.println("Expected failures are marked with ✓ and are part of the demonstration.");
        System.out.println();
        
        try {
            SimpleValidationDemo demo = new SimpleValidationDemo();
            demo.runDemo();
            TestResultLogger.logExampleCompletion("Error Handling Demonstration");
        } catch (Exception e) {
            // This catch block is for UNEXPECTED errors only
            TestResultLogger.logUnexpectedError("Error Handling Demonstration", e);
            System.err.println("\nFull stack trace:");
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public void runDemo() {
        TestResultLogger.logTestSection("1. Testing successful validation", false);
        testSuccessfulValidation();
        
        TestResultLogger.logTestSection("2. Testing invalid input handling", true);
        testInvalidInputHandling();
        
        TestResultLogger.logTestSection("3. Testing missing required data", true);
        testMissingRequiredData();
        
        TestResultLogger.logTestSection("4. Testing boundary conditions", true);
        testBoundaryConditions();
        
        TestResultLogger.logTestSection("5. Testing successful operation", false);
        testSuccessfulOperation();
    }
    
    private void testSuccessfulValidation() {
        try {
            // Simulate successful validation
            String input = "valid-input";
            if (input != null && !input.isEmpty()) {
                TestResultLogger.logExpectedSuccess("Input validation passed");
            } else {
                TestResultLogger.logUnexpectedResult("Valid input was rejected");
            }
        } catch (Exception e) {
            TestResultLogger.logUnexpectedException("Unexpected error in validation", e);
        }
    }
    
    private void testInvalidInputHandling() {
        try {
            // Simulate invalid input that should be rejected
            String invalidInput = null;
            if (invalidInput == null) {
                // This is expected - null input should be rejected
                TestResultLogger.logExpectedFailure("Correctly rejected null input");
            } else {
                TestResultLogger.logUnexpectedResult("Should have rejected null input but didn't");
            }
        } catch (Exception e) {
            TestResultLogger.logExpectedFailure("Correctly caught invalid input error", e);
        }
    }
    
    private void testMissingRequiredData() {
        try {
            // Simulate missing required field
            String requiredField = "";
            if (requiredField.isEmpty()) {
                TestResultLogger.logExpectedFailure("Correctly detected missing required field");
            } else {
                TestResultLogger.logUnexpectedResult("Failed to detect missing required field");
            }
        } catch (Exception e) {
            TestResultLogger.logExpectedFailure("Correctly caught missing data error", e);
        }
    }
    
    private void testBoundaryConditions() {
        try {
            // Simulate boundary condition test
            int value = -1;
            if (value < 0) {
                TestResultLogger.logExpectedFailure("Correctly rejected negative value");
            } else {
                TestResultLogger.logUnexpectedResult("Should have rejected negative value");
            }
        } catch (Exception e) {
            TestResultLogger.logExpectedFailure("Correctly caught boundary condition error", e);
        }
    }
    
    /**
     * Tests successful operation - this should work correctly.
     */
    private void testSuccessfulOperation() {
        try {
            // Simulate successful operation
            boolean operationResult = performOperation();
            if (operationResult) {
                TestResultLogger.logExpectedSuccess("Operation completed successfully");
            } else {
                TestResultLogger.logUnexpectedResult("Operation failed unexpectedly");
            }
        } catch (Exception e) {
            TestResultLogger.logUnexpectedException("Unexpected error in operation", e);
        }
    }
    
    private boolean performOperation() {
        // Simulate a successful operation
        return true;
    }
}
