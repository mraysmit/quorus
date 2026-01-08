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

import java.io.PrintStream;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Centralized logging utility for Quorus integration examples.
 * 
 * <p>This class provides consistent, formatted console output for examples
 * while also logging to java.util.logging for file-based logging when configured.
 * 
 * <p>Features:
 * <ul>
 *   <li>Consistent formatting across all examples</li>
 *   <li>Section headers and step indicators</li>
 *   <li>Success/failure markers with Unicode symbols</li>
 *   <li>Indented output for hierarchical information</li>
 *   <li>Dual output: console (formatted) + java.util.logging (structured)</li>
 * </ul>
 * 
 * <p>Usage:
 * <pre>
 * private static final ExampleLogger log = ExampleLogger.getLogger(MyExample.class);
 * 
 * log.header("My Example");
 * log.step(1, "Initializing...");
 * log.info("Configuration loaded");
 * log.success("Initialization complete");
 * </pre>
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-18
 * @version 2.0
 */
public class ExampleLogger {
    
    private final Logger logger;
    private final String className;
    private final PrintStream out;
    private final PrintStream err;
    
    // Unicode symbols for visual feedback
    private static final String SYMBOL_SUCCESS = "✓";
    private static final String SYMBOL_FAILURE = "✗";
    private static final String SYMBOL_WARNING = "⚠";
    private static final String SYMBOL_INFO = "•";
    private static final String SYMBOL_ARROW = "→";
    
    // Indentation constants
    private static final String INDENT = "   ";
    private static final String DOUBLE_INDENT = "      ";
    private static final String TRIPLE_INDENT = "         ";
    
    /**
     * Private constructor - use factory method.
     */
    private ExampleLogger(Class<?> clazz) {
        this.className = clazz.getSimpleName();
        this.logger = Logger.getLogger(clazz.getName());
        this.out = System.out;
        this.err = System.err;
        
        // Configure console handler with simple format if not already configured
        configureLogger();
    }
    
    /**
     * Factory method to get a logger for a class.
     */
    public static ExampleLogger getLogger(Class<?> clazz) {
        return new ExampleLogger(clazz);
    }
    
    /**
     * Configure the underlying Java logger with a simple formatter.
     */
    private void configureLogger() {
        // Only add handler if none exist
        if (logger.getHandlers().length == 0) {
            Handler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new SimpleExampleFormatter());
            consoleHandler.setLevel(Level.ALL);
            logger.addHandler(consoleHandler);
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(false);
        }
    }
    
    // ========== Header/Section Methods ==========
    
    /**
     * Print a major section header.
     * Example: === My Example ===
     */
    public void header(String title) {
        out.println();
        out.println("=== " + title + " ===");
        logger.info("Starting: " + title);
    }
    
    /**
     * Print a minor section header.
     * Example: --- Sub Section ---
     */
    public void section(String title) {
        out.println();
        out.println("--- " + title + " ---");
        logger.fine("Section: " + title);
    }
    
    /**
     * Print a numbered step.
     * Example: 1. Initializing...
     */
    public void step(int stepNumber, String description) {
        out.println(stepNumber + ". " + description);
        logger.info("Step " + stepNumber + ": " + description);
    }
    
    /**
     * Print a step description (without step number).
     * Example: → Initializing...
     */
    public void step(String description) {
        out.println(SYMBOL_ARROW + " " + description);
        logger.info("Step: " + description);
    }
    
    /**
     * Print a separator line.
     */
    public void separator() {
        out.println("─".repeat(60));
    }
    
    /**
     * Print a separator line with custom character.
     */
    public void separator(String character, int length) {
        out.println(character.repeat(length));
    }
    
    // ========== Standard Logging Methods ==========
    
    /**
     * Print an info message.
     */
    public void info(String message) {
        out.println(message);
        logger.info(message);
    }
    
    /**
     * Print an info message with formatting.
     */
    public void info(String format, Object... args) {
        String message = String.format(format, args);
        out.println(message);
        logger.info(message);
    }
    
    /**
     * Print an indented info message.
     */
    public void detail(String message) {
        out.println(INDENT + message);
        logger.fine(message);
    }
    
    /**
     * Print a double-indented detail message.
     */
    public void subDetail(String message) {
        out.println(DOUBLE_INDENT + message);
        logger.finer(message);
    }
    
    /**
     * Print a triple-indented detail message.
     */
    public void deepDetail(String message) {
        out.println(TRIPLE_INDENT + message);
        logger.finest(message);
    }
    
    /**
     * Print an empty line.
     */
    public void blank() {
        out.println();
    }
    
    // ========== Status Methods ==========
    
    /**
     * Print a success message with checkmark.
     */
    public void success(String message) {
        out.println(INDENT + SYMBOL_SUCCESS + " " + message);
        logger.info("SUCCESS: " + message);
    }
    
    /**
     * Print an expected success (for test scenarios).
     */
    public void expectedSuccess(String message) {
        out.println(INDENT + SYMBOL_SUCCESS + " EXPECTED: " + message);
        logger.info("EXPECTED SUCCESS: " + message);
    }
    
    /**
     * Print a failure message with X mark.
     */
    public void failure(String message) {
        out.println(INDENT + SYMBOL_FAILURE + " " + message);
        logger.warning("FAILURE: " + message);
    }
    
    /**
     * Print an expected failure (for test scenarios demonstrating error handling).
     */
    public void expectedFailure(String message) {
        out.println(INDENT + SYMBOL_SUCCESS + " EXPECTED: " + message);
        logger.info("EXPECTED FAILURE: " + message);
    }
    
    /**
     * Print an expected failure with exception details (for test scenarios).
     */
    public void expectedFailure(String message, Exception e) {
        out.println(INDENT + SYMBOL_SUCCESS + " EXPECTED: " + message + " - " + e.getMessage());
        logger.info("EXPECTED FAILURE: " + message + " - " + e.getMessage());
    }
    
    /**
     * Print a warning message.
     */
    public void warning(String message) {
        out.println(INDENT + SYMBOL_WARNING + " " + message);
        logger.warning(message);
    }
    
    /**
     * Print a bullet point item.
     */
    public void bullet(String message) {
        out.println(INDENT + SYMBOL_INFO + " " + message);
        logger.fine(message);
    }
    
    /**
     * Print an arrow item (for showing flow/transformation).
     */
    public void arrow(String from, String to) {
        out.println(INDENT + from + " " + SYMBOL_ARROW + " " + to);
        logger.fine(from + " -> " + to);
    }
    
    // ========== Key-Value Methods ==========
    
    /**
     * Print a key-value pair.
     */
    public void keyValue(String key, Object value) {
        out.println(INDENT + key + ": " + value);
        logger.fine(key + "=" + value);
    }
    
    /**
     * Print an indented key-value pair.
     */
    public void indentedKeyValue(String key, Object value) {
        out.println(DOUBLE_INDENT + key + ": " + value);
        logger.finer(key + "=" + value);
    }
    
    // ========== Error Methods ==========
    
    /**
     * Print an error message to stderr.
     */
    public void error(String message) {
        err.println("ERROR: " + message);
        logger.severe(message);
    }
    
    /**
     * Print an error message with exception details.
     */
    public void error(String message, Throwable t) {
        err.println("ERROR: " + message + ": " + t.getMessage());
        logger.log(Level.SEVERE, message, t);
    }
    
    /**
     * Print an unexpected error (for example failures).
     */
    public void unexpectedError(String context, Throwable t) {
        err.println();
        err.println("UNEXPECTED ERROR occurred during " + context + ":");
        err.println("Error: " + t.getMessage());
        err.println("This indicates a real problem with the example execution.");
        logger.log(Level.SEVERE, "Unexpected error in " + context, t);
    }
    
    // ========== List Methods ==========
    
    /**
     * Print an item with a dash prefix.
     */
    public void listItem(String item) {
        out.println(INDENT + "- " + item);
        logger.fine("  - " + item);
    }
    
    /**
     * Print a numbered item.
     */
    public void numberedItem(int number, String item) {
        out.println(INDENT + number + ". " + item);
        logger.fine("  " + number + ". " + item);
    }
    
    /**
     * Print a sub-item (double indented with dash).
     */
    public void subListItem(String item) {
        out.println(DOUBLE_INDENT + "- " + item);
        logger.finer("    - " + item);
    }
    
    // ========== Table-like Output ==========
    
    /**
     * Print a simple table header.
     */
    public void tableHeader(String... columns) {
        StringBuilder sb = new StringBuilder();
        for (String col : columns) {
            sb.append(String.format("%-20s", col));
        }
        out.println(sb.toString());
        out.println("-".repeat(20 * columns.length));
        logger.fine("Table: " + String.join(", ", columns));
    }
    
    /**
     * Print a table row.
     */
    public void tableRow(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (Object val : values) {
            sb.append(String.format("%-20s", val));
        }
        out.println(sb.toString());
    }
    
    // ========== Example Lifecycle ==========
    
    /**
     * Log example start.
     */
    public void exampleStart(String exampleName) {
        header("Quorus " + exampleName);
        logger.info("Example started: " + exampleName);
    }
    
    /**
     * Log example start with description.
     */
    public void exampleStart(String exampleName, String description) {
        header("Quorus " + exampleName);
        detail(description);
        logger.info("Example started: " + exampleName + " - " + description);
    }
    
    /**
     * Log example completion.
     */
    public void exampleComplete(String exampleName) {
        out.println();
        out.println("=== " + exampleName + " completed successfully! ===");
        logger.info("Example completed: " + exampleName);
    }
    
    /**
     * Log test section (for intentional failure tests).
     */
    public void testSection(String sectionName, boolean isIntentionalFailureTest) {
        out.println();
        if (isIntentionalFailureTest) {
            out.println(sectionName + " (INTENTIONAL FAILURE TEST)...");
            logger.info("Starting intentional failure test: " + sectionName);
        } else {
            out.println(sectionName + "...");
            logger.info("Starting test: " + sectionName);
        }
    }
    
    /**
     * Simple formatter for the underlying Java logger.
     */
    private static class SimpleExampleFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            return String.format("[%s] %s: %s%n",
                    record.getLevel().getName(),
                    record.getLoggerName().substring(record.getLoggerName().lastIndexOf('.') + 1),
                    record.getMessage());
        }
    }
}
