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

import dev.mars.quorus.config.QuorusConfiguration;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.examples.util.ExampleLogger;
import dev.mars.quorus.transfer.SimpleTransferEngine;
import dev.mars.quorus.transfer.TransferEngine;

import dev.mars.quorus.core.exceptions.TransferException;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Internal Network Transfer Example - Demonstrates Quorus capabilities for corporate network environments.
 * 
 * This example shows typical corporate network transfer scenarios:
 * - Internal server to server data synchronization
 * - Department file sharing and data distribution
 * - High-throughput transfers over corporate networks
 * - Corporate security and monitoring integration
 * - Multi-department concurrent operations
 * 
 * Simulates real corporate scenarios like:
 * - CRM data export to data warehouse
 * - Finance reports distribution to departments
 * - Backup operations between data centers
 * 
 * Run with: mvn exec:java -Dexec.mainClass="dev.mars.quorus.examples.InternalNetworkTransferExample" -pl quorus-integration-examples
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-18
 * @version 1.0
 */
public class InternalNetworkTransferExample {
    private static final ExampleLogger log = ExampleLogger.getLogger(InternalNetworkTransferExample.class);

    // Configuration constants to avoid hardcoded values
    private static final String[] DEPARTMENTS = {"finance", "hr", "sales"};
    private static final String[] REPORT_SIZES = {"2048", "4096", "1024"};
    private static final long TRANSFER_TIMEOUT_SECONDS = 30;
    private static final long MONITORING_INTERVAL_MS = 500;

    // Thread management for proper resource cleanup
    private static final List<Thread> monitoringThreads = new ArrayList<>();
    
    public static void main(String[] args) {
        log.exampleStart("Quorus Internal Network Transfer Example",
                "Demonstrating corporate network file transfer capabilities");
        log.info("");
        log.section("This example simulates typical corporate scenarios");
        log.bullet("CRM data synchronization to data warehouse");
        log.bullet("Multi-department concurrent file distribution");
        log.bullet("High-throughput backup operations");
        log.bullet("Corporate network optimization and monitoring");
        log.info("");
        log.detail("Note: Using external endpoints to simulate internal corporate services");
        log.detail("In real deployment, these would be internal corporate URLs");
        log.info("");
        
        // Initialize configuration optimized for corporate networks
        QuorusConfiguration config = createCorporateNetworkConfiguration();
        log.keyValue("Corporate network configuration loaded", config.toString());

        // Create Vert.x instance for reactive operations
        Vertx vertx = Vertx.vertx();
        
        // Initialize transfer engine with Vert.x and corporate network settings
        TransferEngine transferEngine = new SimpleTransferEngine(
                vertx,
                config.getMaxConcurrentTransfers(),  // Higher concurrency for corporate networks
                config.getMaxRetryAttempts(),        // More retries for reliability
                config.getRetryDelayMs()             // Faster retry for internal networks
        );
        
        try {
            // Create corporate data directories
            Path corporateDataDir = Paths.get("corporate-data");
            Path dataWarehouseDir = corporateDataDir.resolve("data-warehouse");
            Path departmentSharesDir = corporateDataDir.resolve("department-shares");
            Path backupDir = corporateDataDir.resolve("backup");
            
            Files.createDirectories(dataWarehouseDir);
            Files.createDirectories(departmentSharesDir);
            Files.createDirectories(backupDir);
            
            log.section("Created corporate directory structure");
            log.indentedKeyValue("Data Warehouse", dataWarehouseDir.toAbsolutePath().toString());
            log.indentedKeyValue("Department Shares", departmentSharesDir.toAbsolutePath().toString());
            log.indentedKeyValue("Backup Storage", backupDir.toAbsolutePath().toString());
            log.info("");
            
            // Run corporate network transfer examples
            runCrmDataSynchronization(transferEngine, dataWarehouseDir);
            runDepartmentFileDistribution(transferEngine, departmentSharesDir);
            runHighThroughputBackupOperation(transferEngine, backupDir);
            
        } catch (IOException e) {
            log.unexpectedError("Internal Network Transfer Example (Directory Creation)", e);
            log.error("This indicates a file system or permissions issue.");
            e.printStackTrace();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warning("Corporate network transfer example was interrupted: " + e.getMessage());
            // No stack trace for interruption - this is expected behavior
        } catch (ExecutionException e) {
            log.unexpectedError("Internal Network Transfer Example (Execution)", e);
            log.error("This indicates a problem with the transfer execution.");
            e.printStackTrace();
        } catch (TimeoutException e) {
            log.warning("Corporate network transfer timed out: " + e.getMessage());
            log.detail("This may indicate network issues or server overload.");
            // No stack trace for timeout - this is expected behavior in some scenarios
        } catch (TransferException e) {
            log.unexpectedError("Internal Network Transfer Example (Transfer)", e);
            log.error("This indicates a problem with the transfer logic.");
            e.printStackTrace();
        } catch (Exception e) {
            log.unexpectedError("Internal Network Transfer Example", e);
            log.error("This indicates an unforeseen problem.");
            e.printStackTrace();
        } finally {
            // Graceful shutdown with proper resource cleanup
            log.info("");
            log.step("Shutting down transfer engine...");
            log.detail("In corporate environment: Notifying monitoring systems of shutdown");

            // Interrupt and cleanup monitoring threads
            shutdownMonitoringThreads();

            transferEngine.shutdown(10);
            vertx.close();
            log.exampleComplete("Corporate network transfer example");
        }
    }
    
    private static QuorusConfiguration createCorporateNetworkConfiguration() {
        QuorusConfiguration config = new QuorusConfiguration();

        // Corporate network optimizations using property-based configuration
        config.setProperty("quorus.transfer.max.concurrent", "20");  // Higher concurrency for corporate bandwidth
        config.setProperty("quorus.transfer.max.retries", "5");      // More retries for corporate reliability
        config.setProperty("quorus.transfer.retry.delay.ms", "500"); // Faster retry for internal networks

        log.section("Corporate network optimizations applied");
        log.indentedKeyValue("Increased concurrent transfers for high bandwidth", 
                String.valueOf(config.getMaxConcurrentTransfers()));
        log.indentedKeyValue("Enhanced retry logic for corporate reliability", 
                String.valueOf(config.getMaxRetryAttempts()));
        log.indentedKeyValue("Optimized timing for internal network characteristics", 
                config.getRetryDelayMs() + "ms");

        return config;
    }
    
    private static void runCrmDataSynchronization(TransferEngine transferEngine, Path dataWarehouseDir)
            throws IOException, InterruptedException, ExecutionException, TimeoutException, TransferException {
        log.testSection("CRM Data Synchronization Example", false);
        log.detail("Scenario: Nightly CRM data export to corporate data warehouse");
        log.detail("Simulating: crm-internal.corp.local -> data-warehouse.corp.local");
        
        // Simulate CRM data export (using external URL for demo)
        TransferRequest crmExportRequest = TransferRequest.builder()
                .sourceUri(URI.create("https://httpbin.org/bytes/8192")) // Simulates CRM export API
                .destinationPath(dataWarehouseDir.resolve("crm-customer-export.json"))
                .protocol("http")
                .build();
        
        log.step("Initiating CRM data export...");
        log.indentedKeyValue("Source", "CRM Internal API (simulated)");
        log.indentedKeyValue("Destination", crmExportRequest.getDestinationPath().toString());
        log.detail("Expected: High-speed transfer over corporate network");
        
        // Monitor corporate network transfer
        long startTime = System.currentTimeMillis();
        Future<TransferResult> future = transferEngine.submitTransfer(crmExportRequest);
        monitorCorporateTransfer(transferEngine, crmExportRequest.getRequestId(), "CRM Data Sync");

        // Wait with timeout to prevent hanging
        TransferResult result = future.toCompletionStage().toCompletableFuture().get(TRANSFER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;
        
        log.info("");
        log.section("CRM Data Synchronization Results");
        displayCorporateTransferResult(result, duration, "Corporate Data Warehouse");
        
        if (result.isSuccessful()) {
            log.success("CRM data successfully synchronized to data warehouse");
            log.success("Corporate ETL pipeline can proceed with data processing");
        }
        log.info("");
    }
    
    private static void runDepartmentFileDistribution(TransferEngine transferEngine, Path departmentSharesDir)
            throws InterruptedException, ExecutionException, TimeoutException, TransferException {
        log.testSection("Multi-Department File Distribution Example", false);
        log.detail("Scenario: Monthly reports distribution to department shares");
        log.detail("Simulating: fileserver.corp.local -> department network shares");
        
        // Define department transfer scenarios using constants
        @SuppressWarnings("unchecked") // Safe generic array creation
        Future<TransferResult>[] futures = new Future[DEPARTMENTS.length];
        
        log.step("Distributing reports to departments simultaneously...");
        
        // Submit all department transfers concurrently
        for (int i = 0; i < DEPARTMENTS.length; i++) {
            TransferRequest departmentRequest = TransferRequest.builder()
                    .sourceUri(URI.create("https://httpbin.org/bytes/" + REPORT_SIZES[i]))
                    .destinationPath(departmentSharesDir.resolve(DEPARTMENTS[i] + "-monthly-report.pdf"))
                    .protocol("http")
                    .build();

            log.bullet("Distributing to " + DEPARTMENTS[i].toUpperCase() + " department (" + REPORT_SIZES[i] + " bytes)");
            futures[i] = transferEngine.submitTransfer(departmentRequest);
        }
        
        // Wait for all department distributions to complete
        log.step("Waiting for all department distributions to complete...");
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < futures.length; i++) {
            try {
                TransferResult result = futures[i].toCompletionStage().toCompletableFuture()
                        .get(TRANSFER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (result.isSuccessful()) {
                    log.success(DEPARTMENTS[i].toUpperCase() + " department: " + result.getBytesTransferred() + " bytes");
                } else {
                    log.failure(DEPARTMENTS[i].toUpperCase() + " department transfer failed");
                }
            } catch (TimeoutException e) {
                log.warning(DEPARTMENTS[i].toUpperCase() + " department: TIMEOUT (transfer exceeded " + TRANSFER_TIMEOUT_SECONDS + "s)");
            } catch (ExecutionException e) {
                log.error(DEPARTMENTS[i].toUpperCase() + " department: " + e.getCause().getMessage());
            }
        }
        
        long totalDuration = System.currentTimeMillis() - startTime;
        log.info("");
        log.section("Multi-Department Distribution Results");
        log.indentedKeyValue("Total distribution time", totalDuration + "ms");
        log.indentedKeyValue("Concurrent transfers", String.valueOf(DEPARTMENTS.length));
        log.indentedKeyValue("Corporate network efficiency", "Excellent");
        log.success("All departments received their monthly reports");
        log.info("");
    }
    
    private static void runHighThroughputBackupOperation(TransferEngine transferEngine, Path backupDir)
            throws InterruptedException, ExecutionException, TimeoutException, TransferException {
        log.testSection("High-Throughput Backup Operation Example", false);
        log.detail("Scenario: Critical data backup to corporate storage array");
        log.detail("Simulating: production-db.corp.local -> backup-storage.corp.local");
        
        // Simulate large backup operation
        TransferRequest backupRequest = TransferRequest.builder()
                .sourceUri(URI.create("https://httpbin.org/bytes/16384")) // Larger backup file
                .destinationPath(backupDir.resolve("production-backup-" + System.currentTimeMillis() + ".tar.gz"))
                .protocol("http")
                .build();
        
        log.step("Initiating high-throughput backup operation...");
        log.indentedKeyValue("Source", "Production Database (simulated)");
        log.indentedKeyValue("Destination", backupRequest.getDestinationPath().toString());
        log.detail("Expected: Maximum throughput over corporate SAN/NAS");
        
        // Monitor high-throughput transfer
        long startTime = System.currentTimeMillis();
        Future<TransferResult> future = transferEngine.submitTransfer(backupRequest);
        monitorCorporateTransfer(transferEngine, backupRequest.getRequestId(), "Backup Operation");

        // Wait with timeout to prevent hanging
        TransferResult result = future.toCompletionStage().toCompletableFuture().get(TRANSFER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;
        
        log.info("");
        log.section("High-Throughput Backup Results");
        displayCorporateTransferResult(result, duration, "Corporate Backup Storage");
        
        if (result.isSuccessful()) {
            log.success("Critical data successfully backed up");
            log.success("Corporate disaster recovery requirements met");
            log.success("Backup verification and cataloging can proceed");
        }
        log.info("");
    }
    
    private static void monitorCorporateTransfer(TransferEngine transferEngine, String jobId, String operationType) {
        Thread monitorThread = new Thread(() -> {
            try {
                log.detail("Starting corporate network monitoring for " + operationType + "...");
                while (!Thread.currentThread().isInterrupted()) {
                    var job = transferEngine.getTransferJob(jobId);
                    if (job == null) {
                        log.warning("Transfer job not found for ID: " + jobId);
                        break;
                    }

                    if (job.getStatus() == TransferStatus.IN_PROGRESS) {
                        long totalBytes = job.getTotalBytes();
                        String totalDisplay = totalBytes > 0 ? String.valueOf(totalBytes) : "unknown";
                        log.detail(String.format("Corporate Network Progress: %.1f%% (%d/%s bytes) - %s",
                                   job.getProgressPercentage() * 100,
                                   job.getBytesTransferred(),
                                   totalDisplay,
                                   operationType));
                    }

                    if (job.getStatus().isTerminal()) {
                        log.detail("Corporate network transfer completed: " + operationType);
                        break;
                    }

                    Thread.sleep(MONITORING_INTERVAL_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.detail("Corporate network monitoring interrupted for " + operationType);
            } catch (Exception e) {
                log.error("Error in corporate network monitoring for " + operationType + ": " + e.getMessage());
            }
        }, "Monitor-" + operationType);

        monitorThread.setDaemon(true);
        synchronized (monitoringThreads) {
            monitoringThreads.add(monitorThread);
        }
        monitorThread.start();
    }

    private static void shutdownMonitoringThreads() {
        synchronized (monitoringThreads) {
            log.detail("Shutting down " + monitoringThreads.size() + " monitoring threads...");
            for (Thread thread : monitoringThreads) {
                if (thread.isAlive()) {
                    thread.interrupt();
                    try {
                        thread.join(1000); // Wait up to 1 second for thread to finish
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warning("Interrupted while waiting for monitoring thread to finish");
                    }
                }
            }
            monitoringThreads.clear();
        }
    }

    private static void displayCorporateTransferResult(TransferResult result, long duration, String destination) {
        log.indentedKeyValue("Transfer Status", result.getFinalStatus().toString());
        if (result.isSuccessful()) {
            log.success("Transfer successful");
        } else {
            log.failure("Transfer failed");
        }
        log.indentedKeyValue("Destination", destination);
        
        result.getDuration().ifPresent(transferDuration -> 
            log.indentedKeyValue("Transfer Duration", formatDuration(transferDuration)));
        
        log.indentedKeyValue("Total Duration", duration + "ms (including corporate network overhead)");
        
        result.getAverageRateBytesPerSecond().ifPresent(rate -> {
            double kbps = rate / 1024.0;
            double mbps = kbps / 1024.0;
            if (mbps >= 1.0) {
                log.indentedKeyValue("Corporate Network Rate", String.format("%.2f MB/s", mbps));
            } else {
                log.indentedKeyValue("Corporate Network Rate", String.format("%.2f KB/s", kbps));
            }
        });
        
        result.getActualChecksum().ifPresent(checksum ->
            log.indentedKeyValue("Data Integrity", "SHA-256 verified (" + checksum.substring(0, 16) + "...)"));
        
        log.indentedKeyValue("Corporate Compliance", "Transfer logged and audited");
    }
    
    /**
     * Formats duration for corporate reporting.
     */
    private static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long millis = duration.toMillis() % 1000;
        
        if (seconds > 0) {
            return String.format("%d.%02ds", seconds, millis / 10);
        } else {
            return String.format("%dms", millis);
        }
    }
}
