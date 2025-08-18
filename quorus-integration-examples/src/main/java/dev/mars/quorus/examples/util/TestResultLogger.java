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

package dev.mars.quorus.examples.util;

import java.util.logging.Logger;

/**
 * Utility class for logging test results in examples.
 * Helps distinguish between expected test failures and actual errors.
 */
public class TestResultLogger {
    
    private static final Logger logger = Logger.getLogger(TestResultLogger.class.getName());
    
    /**
     * Log an expected test success.
     */
    public static void logExpectedSuccess(String message) {
        System.out.println("   ✓ EXPECTED: " + message);
        logger.info("Expected test success: " + message);
    }
    
    /**
     * Log an expected test failure (this is good - the validation worked).
     */
    public static void logExpectedFailure(String message) {
        System.out.println("   ✓ EXPECTED: " + message);
        logger.info("Expected test failure: " + message);
    }
    
    /**
     * Log an unexpected test result (this indicates a bug).
     */
    public static void logUnexpectedResult(String message) {
        System.out.println("   ✗ UNEXPECTED: " + message);
        logger.warning("Unexpected test result: " + message);
    }
    
    /**
     * Log an expected failure with exception details (no stack trace).
     */
    public static void logExpectedFailure(String message, Exception e) {
        System.out.println("   ✓ EXPECTED: " + message + ": " + e.getMessage());
        logger.info("Expected test failure: " + message + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
    }
    
    /**
     * Log an unexpected exception (this indicates a real problem).
     */
    public static void logUnexpectedException(String message, Exception e) {
        System.out.println("   ✗ UNEXPECTED: " + message + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
        logger.severe("Unexpected exception in test: " + message + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
        // Note: Stack trace should be printed by the caller if needed for debugging
    }
    
    /**
     * Log a test section header.
     */
    public static void logTestSection(String sectionName, boolean isIntentionalFailureTest) {
        if (isIntentionalFailureTest) {
            System.out.println("\n" + sectionName + " (INTENTIONAL FAILURE TEST)...");
            logger.info("Starting intentional failure test: " + sectionName);
        } else {
            System.out.println("\n" + sectionName + "...");
            logger.info("Starting test: " + sectionName);
        }
    }
    
    /**
     * Log an example completion.
     */
    public static void logExampleCompletion(String exampleName) {
        System.out.println("\n=== " + exampleName + " completed successfully! ===");
        logger.info(exampleName + " completed successfully");
    }
    
    /**
     * Log an unexpected error in example execution.
     */
    public static void logUnexpectedError(String exampleName, Exception e) {
        System.err.println("UNEXPECTED ERROR occurred during " + exampleName + " execution:");
        System.err.println("Error: " + e.getMessage());
        System.err.println("This indicates a real problem with the example execution.");
        logger.severe("Unexpected error in " + exampleName + ": " + e.getMessage());
    }
}
