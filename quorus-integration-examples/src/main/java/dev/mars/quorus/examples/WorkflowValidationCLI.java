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

import dev.mars.quorus.workflow.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Command-line tool for validating Quorus workflow YAML files.
 * Provides comprehensive validation with detailed error reporting.
 * 
 * Usage:
 *   java WorkflowValidationCLI <file1.yaml> [file2.yaml] [...]
 *   java WorkflowValidationCLI --help
 *   java WorkflowValidationCLI --validate-directory <directory>
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
public class WorkflowValidationCLI {

    private static final String VERSION = "2.0.0";
    private static final String USAGE = """
            Quorus Workflow Validation CLI v%s
            
            USAGE:
              java WorkflowValidationCLI <file1.yaml> [file2.yaml] [...]
              java WorkflowValidationCLI --validate-directory <directory>
              java WorkflowValidationCLI --help
              java WorkflowValidationCLI --version
            
            OPTIONS:
              --help                    Show this help message
              --version                 Show version information
              --validate-directory DIR  Validate all YAML files in directory
              --strict                  Treat warnings as errors
              --quiet                   Only show errors and summary
              --verbose                 Show detailed validation information
              --schema-only             Only validate schema, skip semantic validation
            
            EXAMPLES:
              # Validate a single workflow file
              java WorkflowValidationCLI workflow.yaml
              
              # Validate multiple files
              java WorkflowValidationCLI workflow1.yaml workflow2.yaml
              
              # Validate all YAML files in a directory
              java WorkflowValidationCLI --validate-directory ./workflows/
              
              # Strict validation (warnings treated as errors)
              java WorkflowValidationCLI --strict workflow.yaml
            
            EXIT CODES:
              0  All validations passed
              1  Validation errors found
              2  Invalid command line arguments
              3  File not found or IO error
            """.formatted(VERSION);

    private boolean strict = false;
    private boolean quiet = false;
    private boolean verbose = false;
    private boolean schemaOnly = false;

    public static void main(String[] args) {
        WorkflowValidationCLI cli = new WorkflowValidationCLI();
        System.exit(cli.run(args));
    }

    public int run(String[] args) {
        if (args.length == 0) {
            System.err.println("Error: No files specified");
            System.err.println();
            System.err.println(USAGE);
            return 2;
        }

        try {
            return processArguments(args);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 3;
        }
    }

    private int processArguments(String[] args) throws IOException {
        List<String> argList = Arrays.asList(args);
        
        // Handle help and version
        if (argList.contains("--help") || argList.contains("-h")) {
            System.out.println(USAGE);
            return 0;
        }
        
        if (argList.contains("--version") || argList.contains("-v")) {
            System.out.println("Quorus Workflow Validation CLI v" + VERSION);
            return 0;
        }

        // Parse options
        parseOptions(argList);

        // Handle directory validation
        int directoryIndex = argList.indexOf("--validate-directory");
        if (directoryIndex >= 0) {
            if (directoryIndex + 1 >= argList.size()) {
                System.err.println("Error: --validate-directory requires a directory path");
                return 2;
            }
            String directory = argList.get(directoryIndex + 1);
            return validateDirectory(directory);
        }

        // Validate individual files
        return validateFiles(getFileArguments(argList));
    }

    private void parseOptions(List<String> args) {
        strict = args.contains("--strict");
        quiet = args.contains("--quiet");
        verbose = args.contains("--verbose");
        schemaOnly = args.contains("--schema-only");
    }

    private List<String> getFileArguments(List<String> args) {
        return args.stream()
            .filter(arg -> !arg.startsWith("--"))
            .toList();
    }

    private int validateDirectory(String directoryPath) throws IOException {
        Path dir = Paths.get(directoryPath);
        
        if (!Files.exists(dir)) {
            System.err.println("Error: Directory does not exist: " + directoryPath);
            return 3;
        }
        
        if (!Files.isDirectory(dir)) {
            System.err.println("Error: Path is not a directory: " + directoryPath);
            return 3;
        }

        List<Path> yamlFiles = Files.walk(dir)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().toLowerCase().endsWith(".yaml") || 
                           path.toString().toLowerCase().endsWith(".yml"))
            .sorted()
            .toList();

        if (yamlFiles.isEmpty()) {
            if (!quiet) {
                System.out.println("No YAML files found in directory: " + directoryPath);
            }
            return 0;
        }

        if (!quiet) {
            System.out.println("Validating " + yamlFiles.size() + " YAML files in: " + directoryPath);
            System.out.println();
        }

        List<String> filePaths = yamlFiles.stream()
            .map(Path::toString)
            .toList();

        return validateFiles(filePaths);
    }

    private int validateFiles(List<String> filePaths) {
        if (filePaths.isEmpty()) {
            System.err.println("Error: No files to validate");
            return 2;
        }

        WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
        int totalFiles = filePaths.size();
        int validFiles = 0;
        int filesWithWarnings = 0;
        int invalidFiles = 0;
        int errorFiles = 0;

        for (String filePath : filePaths) {
            try {
                ValidationResult result = validateFile(parser, filePath);
                
                if (result.isValid() && (!strict || !result.hasWarnings())) {
                    validFiles++;
                    if (result.hasWarnings()) {
                        filesWithWarnings++;
                    }
                } else {
                    invalidFiles++;
                }
                
            } catch (Exception e) {
                errorFiles++;
                if (!quiet) {
                    System.err.println("✗ " + filePath + " - Error: " + e.getMessage());
                    if (verbose) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // Print summary
        if (!quiet) {
            System.out.println();
            System.out.println("=".repeat(60));
            System.out.println("VALIDATION SUMMARY");
            System.out.println("=".repeat(60));
            System.out.println("Total files:      " + totalFiles);
            System.out.println("Valid files:      " + validFiles);
            System.out.println("Files with warnings: " + filesWithWarnings);
            System.out.println("Invalid files:    " + invalidFiles);
            System.out.println("Error files:      " + errorFiles);
            
            if (strict && filesWithWarnings > 0) {
                System.out.println();
                System.out.println("Note: Running in strict mode - warnings treated as errors");
            }
        }

        // Determine exit code
        if (errorFiles > 0 || invalidFiles > 0) {
            return 1;
        } else if (strict && filesWithWarnings > 0) {
            return 1;
        } else {
            return 0;
        }
    }

    private ValidationResult validateFile(WorkflowDefinitionParser parser, String filePath) throws Exception {
        Path path = Paths.get(filePath);
        
        if (!Files.exists(path)) {
            throw new IOException("File does not exist: " + filePath);
        }

        if (!quiet) {
            System.out.println("Validating: " + filePath);
        }

        // Read file content for schema validation
        String content = Files.readString(path);
        ValidationResult schemaResult = parser.validateSchema(content);

        ValidationResult semanticResult = new ValidationResult();
        
        if (!schemaOnly && schemaResult.isValid()) {
            try {
                // Parse and perform semantic validation
                WorkflowDefinition workflow = parser.parse(path);
                semanticResult = parser.validate(workflow);
                
                if (verbose && semanticResult.isValid()) {
                    printWorkflowInfo(workflow);
                }
                
            } catch (Exception e) {
                semanticResult.addError("parsing", "Failed to parse workflow: " + e.getMessage());
            }
        }

        // Combine results
        ValidationResult combinedResult = new ValidationResult();
        
        // Add schema errors and warnings
        schemaResult.getErrors().forEach(error -> 
            combinedResult.addError(error.getFieldPath(), error.getMessage()));
        schemaResult.getWarnings().forEach(warning -> 
            combinedResult.addWarning(warning.getFieldPath(), warning.getMessage()));
            
        // Add semantic errors and warnings
        semanticResult.getErrors().forEach(error -> 
            combinedResult.addError(error.getFieldPath(), error.getMessage()));
        semanticResult.getWarnings().forEach(warning -> 
            combinedResult.addWarning(warning.getFieldPath(), warning.getMessage()));

        // Print results
        printValidationResult(filePath, combinedResult);
        
        return combinedResult;
    }

    private void printValidationResult(String filePath, ValidationResult result) {
        if (quiet && result.isValid() && (!strict || !result.hasWarnings())) {
            return; // Don't print anything for successful validations in quiet mode
        }

        String status;
        if (result.isValid()) {
            if (result.hasWarnings()) {
                status = strict ? "✗ FAILED (warnings in strict mode)" : "⚠ VALID (with warnings)";
            } else {
                status = "✓ VALID";
            }
        } else {
            status = "✗ INVALID";
        }

        System.out.println("  " + status);

        // Print errors
        if (!result.getErrors().isEmpty()) {
            System.out.println("  Errors:");
            result.getErrors().forEach(error -> {
                String fieldPath = error.getFieldPath().isEmpty() ? "root" : error.getFieldPath();
                System.out.println("    - " + fieldPath + ": " + error.getMessage());
            });
        }

        // Print warnings
        if (!result.getWarnings().isEmpty() && !quiet) {
            System.out.println("  Warnings:");
            result.getWarnings().forEach(warning -> {
                String fieldPath = warning.getFieldPath().isEmpty() ? "root" : warning.getFieldPath();
                System.out.println("    - " + fieldPath + ": " + warning.getMessage());
            });
        }

        if (verbose || !result.isValid()) {
            System.out.println("  Summary: " + result.getErrorCount() + " errors, " + 
                             result.getWarningCount() + " warnings");
        }
        
        System.out.println();
    }

    private void printWorkflowInfo(WorkflowDefinition workflow) {
        WorkflowDefinition.WorkflowMetadata metadata = workflow.getMetadata();
        System.out.println("  Workflow Info:");
        System.out.println("    Name: " + metadata.getName());
        System.out.println("    Version: " + metadata.getVersion());
        System.out.println("    Type: " + metadata.getType());
        System.out.println("    Author: " + metadata.getAuthor());
        System.out.println("    Created: " + metadata.getCreated());
        System.out.println("    Tags: " + metadata.getTags());
        System.out.println("    Transfer Groups: " + workflow.getSpec().getTransferGroups().size());
    }
}
