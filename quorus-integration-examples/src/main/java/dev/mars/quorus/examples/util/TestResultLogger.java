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

/**
 * Utility class for logging test results in examples.
 * Helps distinguish between expected test failures and actual errors.
 * 
 * <p>This class now delegates to {@link ExampleLogger} for consistent formatting.
 * It is maintained for backward compatibility with existing example code.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-18
 * @version 2.0
 * @see ExampleLogger
 */
public class TestResultLogger {
    
    private static final ExampleLogger log = ExampleLogger.getLogger(TestResultLogger.class);
    
    public static void logExpectedSuccess(String message) {
        log.expectedSuccess(message);
    }
    
    public static void logExpectedFailure(String message) {
        log.expectedFailure(message);
    }
    
    public static void logUnexpectedResult(String message) {
        log.failure("UNEXPECTED: " + message);
    }
    
    public static void logExpectedFailure(String message, Exception e) {
        log.expectedFailure(message + ": " + e.getMessage());
    }
    
    public static void logUnexpectedException(String message, Exception e) {
        log.failure("UNEXPECTED: " + message + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
    }
    
    public static void logTestSection(String sectionName, boolean isIntentionalFailureTest) {
        log.testSection(sectionName, isIntentionalFailureTest);
    }
    
    public static void logExampleCompletion(String exampleName) {
        log.exampleComplete(exampleName);
    }
    
    /**
     * Log an unexpected error in example execution.
     */
    public static void logUnexpectedError(String exampleName, Exception e) {
        log.unexpectedError(exampleName, e);
    }

    /**
     * Log a test header for starting a test suite.
     */
    public static void logTestHeader(String header) {
        log.header(header);
    }

    /**
     * Log a test footer for completing a test suite.
     */
    public static void logTestFooter(String footer) {
        log.info(footer);
    }

    /**
     * Log a simple success message.
     */
    public static void logSuccess(String message) {
        log.success(message);
    }
}
