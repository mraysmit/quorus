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

package dev.mars.quorus.examples;

import dev.mars.quorus.api.dto.TransferJobResponseDto;
import dev.mars.quorus.client.QuorusClient;
import dev.mars.quorus.client.QuorusClientException;

/**
 * Example demonstrating how to use the Quorus Java Client SDK.
 */
public class ClientExample {

    public static void main(String[] args) {
        // Create a client instance
        QuorusClient client = new QuorusClient("http://localhost:8080", "admin", "admin123");

        try {
            // Get service information
            System.out.println("=== Service Information ===");
            String serviceInfo = client.getServiceInfo();
            System.out.println(serviceInfo);
            System.out.println();

            // Get active transfer count
            System.out.println("=== Active Transfer Count ===");
            int activeCount = client.getActiveTransferCount();
            System.out.println("Active transfers: " + activeCount);
            System.out.println();

            // Create a new transfer
            System.out.println("=== Creating Transfer ===");
            String sourceUri = "https://httpbin.org/bytes/1024";
            String destinationPath = "/tmp/example-download.bin";
            String description = "Example transfer using Java SDK";

            TransferJobResponseDto transfer = client.createTransfer(sourceUri, destinationPath, description);
            System.out.println("Transfer created:");
            System.out.println("  Job ID: " + transfer.getJobId());
            System.out.println("  Status: " + transfer.getStatus());
            System.out.println("  Source: " + transfer.getSourceUri());
            System.out.println("  Destination: " + transfer.getDestinationPath());
            System.out.println();

            // Get transfer status
            System.out.println("=== Transfer Status ===");
            TransferJobResponseDto status = client.getTransferStatus(transfer.getJobId());
            System.out.println("Transfer status:");
            System.out.println("  Job ID: " + status.getJobId());
            System.out.println("  Status: " + status.getStatus());
            System.out.println("  Progress: " + String.format("%.1f%%", status.getProgressPercentage()));
            System.out.println("  Bytes Transferred: " + formatBytes(status.getBytesTransferred()));
            System.out.println("  Total Bytes: " + formatBytes(status.getTotalBytes()));
            System.out.println();

            // Wait a moment and check status again
            System.out.println("=== Waiting and Checking Status Again ===");
            Thread.sleep(2000); // Wait 2 seconds
            
            TransferJobResponseDto updatedStatus = client.getTransferStatus(transfer.getJobId());
            System.out.println("Updated transfer status:");
            System.out.println("  Status: " + updatedStatus.getStatus());
            System.out.println("  Progress: " + String.format("%.1f%%", updatedStatus.getProgressPercentage()));
            System.out.println("  Bytes Transferred: " + formatBytes(updatedStatus.getBytesTransferred()));
            System.out.println();

            // Example of async transfer creation
            System.out.println("=== Async Transfer Creation ===");
            client.createTransferAsync("https://httpbin.org/bytes/512", "/tmp/async-example.bin")
                    .thenAccept(asyncTransfer -> {
                        System.out.println("Async transfer created:");
                        System.out.println("  Job ID: " + asyncTransfer.getJobId());
                        System.out.println("  Status: " + asyncTransfer.getStatus());
                    })
                    .join(); // Wait for completion

            System.out.println();

            // Get updated active transfer count
            System.out.println("=== Updated Active Transfer Count ===");
            int newActiveCount = client.getActiveTransferCount();
            System.out.println("Active transfers: " + newActiveCount);

        } catch (QuorusClientException e) {
            System.err.println("Quorus API Error: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.err.println("Thread interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            // Clean up resources
            client.close();
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
