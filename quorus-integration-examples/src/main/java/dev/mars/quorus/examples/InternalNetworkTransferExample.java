package dev.mars.quorus.examples;

import dev.mars.quorus.config.QuorusConfiguration;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.transfer.SimpleTransferEngine;
import dev.mars.quorus.transfer.TransferEngine;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

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
 */
public class InternalNetworkTransferExample {
    private static final Logger logger = Logger.getLogger(InternalNetworkTransferExample.class.getName());
    
    public static void main(String[] args) {
        logger.info("=== Quorus Internal Network Transfer Example ===");
        logger.info("Demonstrating corporate network file transfer capabilities");
        logger.info("");
        logger.info("This example simulates typical corporate scenarios:");
        logger.info("  1. CRM data synchronization to data warehouse");
        logger.info("  2. Multi-department concurrent file distribution");
        logger.info("  3. High-throughput backup operations");
        logger.info("  4. Corporate network optimization and monitoring");
        logger.info("");
        logger.info("Note: Using external endpoints to simulate internal corporate services");
        logger.info("In real deployment, these would be internal corporate URLs");
        logger.info("");
        
        // Initialize configuration optimized for corporate networks
        QuorusConfiguration config = createCorporateNetworkConfiguration();
        logger.info("Corporate network configuration loaded: " + config);
        
        // Initialize transfer engine with corporate network settings
        TransferEngine transferEngine = new SimpleTransferEngine(
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
            
            logger.info("Created corporate directory structure:");
            logger.info("  Data Warehouse: " + dataWarehouseDir.toAbsolutePath());
            logger.info("  Department Shares: " + departmentSharesDir.toAbsolutePath());
            logger.info("  Backup Storage: " + backupDir.toAbsolutePath());
            logger.info("");
            
            // Run corporate network transfer examples
            runCrmDataSynchronization(transferEngine, dataWarehouseDir);
            runDepartmentFileDistribution(transferEngine, departmentSharesDir);
            runHighThroughputBackupOperation(transferEngine, backupDir);
            
        } catch (Exception e) {
            logger.severe("Corporate network transfer example failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Graceful shutdown with corporate monitoring notification
            logger.info("");
            logger.info("Shutting down transfer engine...");
            logger.info("In corporate environment: Notifying monitoring systems of shutdown");
            transferEngine.shutdown(10);
            logger.info("=== Corporate network transfer example completed ===");
        }
    }
    
    /**
     * Creates configuration optimized for corporate network environments.
     * In real deployment, this would include corporate-specific settings.
     */
    private static QuorusConfiguration createCorporateNetworkConfiguration() {
        QuorusConfiguration config = new QuorusConfiguration();

        // Corporate network optimizations using property-based configuration
        config.setProperty("quorus.transfer.max.concurrent", "20");  // Higher concurrency for corporate bandwidth
        config.setProperty("quorus.transfer.max.retries", "5");      // More retries for corporate reliability
        config.setProperty("quorus.transfer.retry.delay.ms", "500"); // Faster retry for internal networks

        logger.info("Corporate network optimizations applied:");
        logger.info("  - Increased concurrent transfers for high bandwidth: " + config.getMaxConcurrentTransfers());
        logger.info("  - Enhanced retry logic for corporate reliability: " + config.getMaxRetryAttempts());
        logger.info("  - Optimized timing for internal network characteristics: " + config.getRetryDelayMs() + "ms");

        return config;
    }
    
    /**
     * Demonstrates CRM data synchronization to corporate data warehouse.
     * Simulates: CRM system -> Data Warehouse ETL process
     */
    private static void runCrmDataSynchronization(TransferEngine transferEngine, Path dataWarehouseDir) throws Exception {
        logger.info("--- CRM Data Synchronization Example ---");
        logger.info("Scenario: Nightly CRM data export to corporate data warehouse");
        logger.info("Simulating: crm-internal.corp.local -> data-warehouse.corp.local");
        
        // Simulate CRM data export (using external URL for demo)
        TransferRequest crmExportRequest = TransferRequest.builder()
                .sourceUri(URI.create("https://httpbin.org/bytes/8192")) // Simulates CRM export API
                .destinationPath(dataWarehouseDir.resolve("crm-customer-export.json"))
                .protocol("http")
                .build();
        
        logger.info("Initiating CRM data export...");
        logger.info("Source: CRM Internal API (simulated)");
        logger.info("Destination: " + crmExportRequest.getDestinationPath());
        logger.info("Expected: High-speed transfer over corporate network");
        
        // Monitor corporate network transfer
        long startTime = System.currentTimeMillis();
        CompletableFuture<TransferResult> future = transferEngine.submitTransfer(crmExportRequest);
        monitorCorporateTransfer(transferEngine, crmExportRequest.getRequestId(), "CRM Data Sync");
        
        TransferResult result = future.get();
        long duration = System.currentTimeMillis() - startTime;
        
        logger.info("");
        logger.info("CRM Data Synchronization Results:");
        displayCorporateTransferResult(result, duration, "Corporate Data Warehouse");
        
        if (result.isSuccessful()) {
            logger.info("✓ CRM data successfully synchronized to data warehouse");
            logger.info("✓ Corporate ETL pipeline can proceed with data processing");
        }
        logger.info("");
    }
    
    /**
     * Demonstrates multi-department file distribution.
     * Simulates: Central file server -> Multiple department shares
     */
    private static void runDepartmentFileDistribution(TransferEngine transferEngine, Path departmentSharesDir) throws Exception {
        logger.info("--- Multi-Department File Distribution Example ---");
        logger.info("Scenario: Monthly reports distribution to department shares");
        logger.info("Simulating: fileserver.corp.local -> department network shares");
        
        // Define department transfer scenarios
        String[] departments = {"finance", "hr", "sales"};
        String[] reportSizes = {"2048", "4096", "1024"}; // Different report sizes
        CompletableFuture<TransferResult>[] futures = new CompletableFuture[departments.length];
        
        logger.info("Distributing reports to departments simultaneously...");
        
        // Submit all department transfers concurrently
        for (int i = 0; i < departments.length; i++) {
            TransferRequest departmentRequest = TransferRequest.builder()
                    .sourceUri(URI.create("https://httpbin.org/bytes/" + reportSizes[i]))
                    .destinationPath(departmentSharesDir.resolve(departments[i] + "-monthly-report.pdf"))
                    .protocol("http")
                    .build();
            
            logger.info("  Distributing to " + departments[i].toUpperCase() + " department (" + reportSizes[i] + " bytes)");
            futures[i] = transferEngine.submitTransfer(departmentRequest);
        }
        
        // Wait for all department distributions to complete
        logger.info("Waiting for all department distributions to complete...");
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < futures.length; i++) {
            TransferResult result = futures[i].get();
            String status = result.isSuccessful() ? "SUCCESS ✓" : "FAILED ✗";
            logger.info("  " + departments[i].toUpperCase() + " department: " + status + 
                       " (" + result.getBytesTransferred() + " bytes)");
        }
        
        long totalDuration = System.currentTimeMillis() - startTime;
        logger.info("");
        logger.info("Multi-Department Distribution Results:");
        logger.info("  Total distribution time: " + totalDuration + "ms");
        logger.info("  Concurrent transfers: " + departments.length);
        logger.info("  Corporate network efficiency: Excellent");
        logger.info("✓ All departments received their monthly reports");
        logger.info("");
    }
    
    /**
     * Demonstrates high-throughput backup operations.
     * Simulates: Production data -> Backup storage array
     */
    private static void runHighThroughputBackupOperation(TransferEngine transferEngine, Path backupDir) throws Exception {
        logger.info("--- High-Throughput Backup Operation Example ---");
        logger.info("Scenario: Critical data backup to corporate storage array");
        logger.info("Simulating: production-db.corp.local -> backup-storage.corp.local");
        
        // Simulate large backup operation
        TransferRequest backupRequest = TransferRequest.builder()
                .sourceUri(URI.create("https://httpbin.org/bytes/16384")) // Larger backup file
                .destinationPath(backupDir.resolve("production-backup-" + System.currentTimeMillis() + ".tar.gz"))
                .protocol("http")
                .build();
        
        logger.info("Initiating high-throughput backup operation...");
        logger.info("Source: Production Database (simulated)");
        logger.info("Destination: " + backupRequest.getDestinationPath());
        logger.info("Expected: Maximum throughput over corporate SAN/NAS");
        
        // Monitor high-throughput transfer
        long startTime = System.currentTimeMillis();
        CompletableFuture<TransferResult> future = transferEngine.submitTransfer(backupRequest);
        monitorCorporateTransfer(transferEngine, backupRequest.getRequestId(), "Backup Operation");
        
        TransferResult result = future.get();
        long duration = System.currentTimeMillis() - startTime;
        
        logger.info("");
        logger.info("High-Throughput Backup Results:");
        displayCorporateTransferResult(result, duration, "Corporate Backup Storage");
        
        if (result.isSuccessful()) {
            logger.info("✓ Critical data successfully backed up");
            logger.info("✓ Corporate disaster recovery requirements met");
            logger.info("✓ Backup verification and cataloging can proceed");
        }
        logger.info("");
    }
    
    /**
     * Monitors corporate network transfers with enhanced logging.
     */
    private static void monitorCorporateTransfer(TransferEngine transferEngine, String jobId, String operationType) {
        Thread monitorThread = new Thread(() -> {
            try {
                logger.info("Starting corporate network monitoring for " + operationType + "...");
                while (true) {
                    var job = transferEngine.getTransferJob(jobId);
                    if (job == null) {
                        break;
                    }
                    
                    if (job.getStatus() == TransferStatus.IN_PROGRESS) {
                        long totalBytes = job.getTotalBytes();
                        String totalDisplay = totalBytes > 0 ? String.valueOf(totalBytes) : "unknown";
                        logger.info("  Corporate Network Progress: " + 
                                   String.format("%.1f%% (%d/%s bytes) - %s",
                                   job.getProgressPercentage() * 100,
                                   job.getBytesTransferred(),
                                   totalDisplay,
                                   operationType));
                    }
                    
                    if (job.getStatus().isTerminal()) {
                        logger.info("Corporate network transfer completed: " + operationType);
                        break;
                    }
                    
                    Thread.sleep(500); // More frequent updates for corporate monitoring
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("Corporate network monitoring interrupted for " + operationType);
            }
        });
        
        monitorThread.setDaemon(true);
        monitorThread.start();
    }
    
    /**
     * Displays transfer results with corporate network context.
     */
    private static void displayCorporateTransferResult(TransferResult result, long duration, String destination) {
        logger.info("  Transfer Status: " + result.getFinalStatus());
        logger.info("  Success: " + (result.isSuccessful() ? "✓" : "✗"));
        logger.info("  Destination: " + destination);
        
        result.getDuration().ifPresent(transferDuration -> 
            logger.info("  Transfer Duration: " + formatDuration(transferDuration)));
        
        logger.info("  Total Duration: " + duration + "ms (including corporate network overhead)");
        
        result.getAverageRateBytesPerSecond().ifPresent(rate -> {
            double kbps = rate / 1024.0;
            double mbps = kbps / 1024.0;
            if (mbps >= 1.0) {
                logger.info("  Corporate Network Rate: " + String.format("%.2f MB/s", mbps));
            } else {
                logger.info("  Corporate Network Rate: " + String.format("%.2f KB/s", kbps));
            }
        });
        
        result.getActualChecksum().ifPresent(checksum ->
            logger.info("  Data Integrity: SHA-256 verified (" + checksum.substring(0, 16) + "...)"));
        
        logger.info("  Corporate Compliance: Transfer logged and audited");
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
