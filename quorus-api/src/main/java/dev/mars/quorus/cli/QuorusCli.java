/*
 * Copyright 2024 Quorus Project
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

package dev.mars.quorus.cli;

import dev.mars.quorus.api.dto.TransferJobResponseDto;
import dev.mars.quorus.client.QuorusClient;
import dev.mars.quorus.client.QuorusClientException;

import java.util.Scanner;

/**
 * Command Line Interface for Quorus File Transfer System.
 * Provides a simple CLI for interacting with the Quorus API.
 */
public class QuorusCli {

    private static final String DEFAULT_URL = "http://localhost:8080";
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin123";

    private QuorusClient client;
    private Scanner scanner;

    public QuorusCli(String url, String username, String password) {
        this.client = new QuorusClient(url, username, password);
        this.scanner = new Scanner(System.in);
    }

    public static void main(String[] args) {
        String url = DEFAULT_URL;
        String username = DEFAULT_USERNAME;
        String password = DEFAULT_PASSWORD;

        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--url":
                case "-u":
                    if (i + 1 < args.length) {
                        url = args[++i];
                    }
                    break;
                case "--username":
                    if (i + 1 < args.length) {
                        username = args[++i];
                    }
                    break;
                case "--password":
                    if (i + 1 < args.length) {
                        password = args[++i];
                    }
                    break;
                case "--help":
                case "-h":
                    printUsage();
                    return;
            }
        }

        QuorusCli cli = new QuorusCli(url, username, password);
        cli.run();
    }

    public void run() {
        System.out.println("=== Quorus File Transfer CLI ===");
        System.out.println("Type 'help' for available commands or 'quit' to exit.");
        System.out.println();

        // Test connection
        try {
            String info = client.getServiceInfo();
            System.out.println("✓ Connected to Quorus API");
        } catch (QuorusClientException e) {
            System.err.println("✗ Failed to connect to Quorus API: " + e.getMessage());
            System.err.println("Please check the URL and credentials.");
            return;
        }

        while (true) {
            System.out.print("quorus> ");
            String input = scanner.nextLine().trim();
            
            if (input.isEmpty()) {
                continue;
            }

            String[] parts = input.split("\\s+");
            String command = parts[0].toLowerCase();

            try {
                switch (command) {
                    case "help":
                        printHelp();
                        break;
                    case "info":
                        handleInfo();
                        break;
                    case "status":
                        if (parts.length > 1) {
                            handleStatus(parts[1]);
                        } else {
                            System.out.println("Usage: status <job-id>");
                        }
                        break;
                    case "create":
                        handleCreate(parts);
                        break;
                    case "cancel":
                        if (parts.length > 1) {
                            handleCancel(parts[1]);
                        } else {
                            System.out.println("Usage: cancel <job-id>");
                        }
                        break;
                    case "count":
                        handleCount();
                        break;
                    case "quit":
                    case "exit":
                        System.out.println("Goodbye!");
                        return;
                    default:
                        System.out.println("Unknown command: " + command);
                        System.out.println("Type 'help' for available commands.");
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    private void printHelp() {
        System.out.println("Available commands:");
        System.out.println("  help                          - Show this help message");
        System.out.println("  info                          - Show service information");
        System.out.println("  create <source> <destination> - Create a new transfer");
        System.out.println("  status <job-id>               - Get transfer status");
        System.out.println("  cancel <job-id>               - Cancel a transfer");
        System.out.println("  count                         - Get active transfer count");
        System.out.println("  quit                          - Exit the CLI");
        System.out.println();
    }

    private void handleInfo() throws QuorusClientException {
        String info = client.getServiceInfo();
        System.out.println("Service Information:");
        System.out.println(info);
    }

    private void handleStatus(String jobId) throws QuorusClientException {
        TransferJobResponseDto job = client.getTransferStatus(jobId);
        System.out.println("Transfer Status:");
        System.out.println("  Job ID: " + job.getJobId());
        System.out.println("  Source: " + job.getSourceUri());
        System.out.println("  Destination: " + job.getDestinationPath());
        System.out.println("  Status: " + job.getStatus());
        System.out.println("  Progress: " + String.format("%.1f%%", job.getProgressPercentage()));
        System.out.println("  Bytes Transferred: " + formatBytes(job.getBytesTransferred()));
        System.out.println("  Total Bytes: " + formatBytes(job.getTotalBytes()));
        if (job.getErrorMessage() != null) {
            System.out.println("  Error: " + job.getErrorMessage());
        }
    }

    private void handleCreate(String[] parts) throws QuorusClientException {
        if (parts.length < 3) {
            System.out.println("Usage: create <source-uri> <destination-path> [description]");
            return;
        }

        String sourceUri = parts[1];
        String destinationPath = parts[2];
        String description = parts.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(parts, 3, parts.length)) : null;

        TransferJobResponseDto job = client.createTransfer(sourceUri, destinationPath, description);
        System.out.println("Transfer created successfully:");
        System.out.println("  Job ID: " + job.getJobId());
        System.out.println("  Status: " + job.getStatus());
        System.out.println("  Source: " + job.getSourceUri());
        System.out.println("  Destination: " + job.getDestinationPath());
    }

    private void handleCancel(String jobId) throws QuorusClientException {
        String result = client.cancelTransfer(jobId);
        System.out.println(result);
    }

    private void handleCount() throws QuorusClientException {
        int count = client.getActiveTransferCount();
        System.out.println("Active transfers: " + count);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar quorus-cli.jar [options]");
        System.out.println("Options:");
        System.out.println("  --url, -u <url>       Quorus API URL (default: http://localhost:8080)");
        System.out.println("  --username <user>     Username for authentication (default: admin)");
        System.out.println("  --password <pass>     Password for authentication (default: admin123)");
        System.out.println("  --help, -h            Show this help message");
    }
}
