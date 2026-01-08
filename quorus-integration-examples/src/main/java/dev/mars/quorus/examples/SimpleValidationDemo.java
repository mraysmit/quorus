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

/**
 * Simple demonstration of the improved error handling approach.
 * This shows how intentional failures are now clearly distinguished from actual errors.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-18
 * @version 1.0
 */
public class SimpleValidationDemo {
    private static final ExampleLogger log = ExampleLogger.getLogger(SimpleValidationDemo.class);
    
    public static void main(String[] args) {
        log.exampleStart("Quorus Error Handling Demonstration",
                "This example demonstrates the improved error handling approach.\n" +
                "Expected failures are marked with ✓ and are part of the demonstration.");
        
        try {
            SimpleValidationDemo demo = new SimpleValidationDemo();
            demo.runDemo();
            log.exampleComplete("Error Handling Demonstration");
        } catch (Exception e) {
            // This catch block is for UNEXPECTED errors only
            log.unexpectedError("Error Handling Demonstration", e);
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public void runDemo() {
        log.testSection("1. Testing successful validation", false);
        testSuccessfulValidation();
        
        log.testSection("2. Testing invalid input handling", true);
        testInvalidInputHandling();
        
        log.testSection("3. Testing missing required data", true);
        testMissingRequiredData();
        
        log.testSection("4. Testing boundary conditions", true);
        testBoundaryConditions();
        
        log.testSection("5. Testing successful operation", false);
        testSuccessfulOperation();
    }
    
    private void testSuccessfulValidation() {
        try {
            // Simulate successful validation
            String input = "valid-input";
            if (input != null && !input.isEmpty()) {
                log.expectedSuccess("Input validation passed");
            } else {
                log.warning("Valid input was rejected");
            }
        } catch (Exception e) {
            log.unexpectedError("Unexpected error in validation", e);
        }
    }
    
    private void testInvalidInputHandling() {
        try {
            // Simulate invalid input that should be rejected
            String invalidInput = null;
            if (invalidInput == null) {
                // This is expected - null input should be rejected
                log.expectedFailure("Correctly rejected null input");
            } else {
                log.warning("Should have rejected null input but didn't");
            }
        } catch (Exception e) {
            log.expectedFailure("Correctly caught invalid input error", e);
        }
    }
    
    private void testMissingRequiredData() {
        try {
            // Simulate missing required field
            String requiredField = "";
            if (requiredField.isEmpty()) {
                log.expectedFailure("Correctly detected missing required field");
            } else {
                log.warning("Failed to detect missing required field");
            }
        } catch (Exception e) {
            log.expectedFailure("Correctly caught missing data error", e);
        }
    }
    
    private void testBoundaryConditions() {
        try {
            // Simulate boundary condition test
            int value = -1;
            if (value < 0) {
                log.expectedFailure("Correctly rejected negative value");
            } else {
                log.warning("Should have rejected negative value");
            }
        } catch (Exception e) {
            log.expectedFailure("Correctly caught boundary condition error", e);
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
                log.expectedSuccess("Operation completed successfully");
            } else {
                log.warning("Operation failed unexpectedly");
            }
        } catch (Exception e) {
            log.unexpectedError("Unexpected error in operation", e);
        }
    }
    
    private boolean performOperation() {
        // Simulate a successful operation
        return true;
    }
}
