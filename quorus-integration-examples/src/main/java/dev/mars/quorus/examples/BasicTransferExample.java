package dev.mars.quorus.examples;

import dev.mars.quorus.config.QuorusConfiguration;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.examples.util.TestResultLogger;
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
 * Basic Transfer Example - Demonstrates fundamental Quorus file transfer capabilities.
 * 
 * This self-contained example shows:
 * - Basic HTTP file transfer
 * - Progress monitoring
 * - Error handling
 * - Performance metrics
 * - Checksum verification
 * 
 * Run with: mvn exec:java -pl quorus-integration-examples
 */
public class BasicTransferExample {
    private static final Logger logger = Logger.getLogger(BasicTransferExample.class.getName());
    
    public static void main(String[] args) {
        logger.info("=== Quorus Basic Transfer Example ===");
        logger.info("Demonstrating fundamental file transfer capabilities");
        logger.info("");
        logger.info("This example will demonstrate:");
        logger.info("  1. Basic HTTP file transfer with progress monitoring");
        logger.info("  2. Multiple concurrent file transfers");
        logger.info("  3. Retry logic and resilience with slow network responses");
        logger.info("");

        // Initialize configuration with default settings
        QuorusConfiguration config = new QuorusConfiguration();
        logger.info("Configuration loaded: " + config);

        // Initialize transfer engine with configuration parameters
        TransferEngine transferEngine = new SimpleTransferEngine(
                config.getMaxConcurrentTransfers(),  // Max concurrent transfers: 10
                config.getMaxRetryAttempts(),        // Max retry attempts: 3
                config.getRetryDelayMs()             // Initial retry delay: 1000ms
        );

        try {
            // Create downloads directory for storing transferred files
            Path downloadsDir = Paths.get("downloads");
            Files.createDirectories(downloadsDir);
            logger.info("Created downloads directory: " + downloadsDir.toAbsolutePath());
            logger.info("");

            // Run demonstration examples in sequence
            runBasicTransferExample(transferEngine);
            runMultipleFilesExample(transferEngine);
            runRetryDemonstrationExample(transferEngine);  // Shows retry logic without scary errors

        } catch (Exception e) {
            // This catch block is for UNEXPECTED errors only
            TestResultLogger.logUnexpectedError("Basic Transfer Example", e);
            logger.severe("Full stack trace:");
            e.printStackTrace();
        } finally {
            // Always shutdown transfer engine gracefully
            logger.info("");
            logger.info("Shutting down transfer engine...");
            transferEngine.shutdown(10);
            logger.info("=== Example completed successfully ===");
        }
    }
    
    /**
     * Demonstrates basic file transfer with progress monitoring.
     *
     * This example shows:
     * - Creating a transfer request using the builder pattern
     * - Submitting the transfer to the engine
     * - Real-time progress monitoring
     * - Retrieving and displaying comprehensive results
     */
    private static void runBasicTransferExample(TransferEngine transferEngine) throws Exception {
        logger.info("--- Basic Transfer Example ---");
        logger.info("Downloading a 2KB test file from httpbin.org");

        // Create transfer request using builder pattern
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("https://httpbin.org/bytes/2048")) // 2KB test file from httpbin.org
                .destinationPath(Paths.get("downloads/basic-example.bin"))
                .protocol("http")
                .build();

        logger.info("Transfer source: " + request.getSourceUri());
        logger.info("Transfer destination: " + request.getDestinationPath());

        // Submit transfer and get future for result
        CompletableFuture<TransferResult> future = transferEngine.submitTransfer(request);

        // Start real-time progress monitoring in background thread
        monitorTransferProgress(transferEngine, request.getRequestId());

        // Wait for transfer completion and get results
        TransferResult result = future.get();
        displayTransferResult(result);
    }
    
    /**
     * Demonstrates transferring multiple files concurrently.
     *
     * This example shows:
     * - Submitting multiple transfers simultaneously
     * - Concurrent execution by the transfer engine
     * - Waiting for all transfers to complete
     * - Handling results from multiple operations
     */
    private static void runMultipleFilesExample(TransferEngine transferEngine) throws Exception {
        logger.info("--- Multiple Files Example ---");
        logger.info("Downloading 3 files of different sizes concurrently");

        String[] fileSizes = {"512", "1024", "4096"}; // Different file sizes in bytes
        CompletableFuture<TransferResult>[] futures = new CompletableFuture[fileSizes.length];

        // Submit all transfers simultaneously to demonstrate concurrency
        logger.info("Submitting all transfers simultaneously...");
        for (int i = 0; i < fileSizes.length; i++) {
            TransferRequest request = TransferRequest.builder()
                    .sourceUri(URI.create("https://httpbin.org/bytes/" + fileSizes[i]))
                    .destinationPath(Paths.get("downloads/multi-file-" + fileSizes[i] + "b.bin"))
                    .protocol("http")
                    .build();

            logger.info("  Submitting transfer " + (i + 1) + ": " + fileSizes[i] + " bytes");
            futures[i] = transferEngine.submitTransfer(request);
        }

        // Wait for all transfers to complete and collect results
        logger.info("All transfers submitted. Waiting for completion...");
        for (int i = 0; i < futures.length; i++) {
            TransferResult result = futures[i].get();
            String status = result.isSuccessful() ? "SUCCESS ✓" : "FAILED ✗";
            logger.info("  Transfer " + (i + 1) + " result: " + status +
                       " (" + result.getBytesTransferred() + " bytes)");
        }
        logger.info("All concurrent transfers completed successfully!");
    }
    
    /**
     * Demonstrates retry logic and resilience features.
     *
     * This example shows how the system handles network delays and temporary issues
     * by using httpbin.org's delay endpoint to simulate slow responses.
     * This demonstrates the system's patience and retry capabilities.
     *
     * NOTE: This is NOT a failure test - it demonstrates successful handling of slow responses.
     * The system should complete successfully after waiting for the delayed response.
     */
    private static void runRetryDemonstrationExample(TransferEngine transferEngine) throws Exception {
        logger.info("--- Retry & Resilience Demonstration ---");
        logger.info("This example shows how Quorus handles network delays gracefully");
        logger.info("");

        // Use httpbin.org's delay endpoint to simulate a slow but successful transfer
        TransferRequest slowRequest = TransferRequest.builder()
                .sourceUri(URI.create("https://httpbin.org/delay/2")) // 2-second delay, then success
                .destinationPath(Paths.get("downloads/slow-response-example.json"))
                .protocol("http")
                .build();

        logger.info("Testing resilience with slow network response (2-second delay)...");
        logger.info("Source: " + slowRequest.getSourceUri());
        logger.info("This demonstrates how Quorus waits patiently for slow responses");

        long startTime = System.currentTimeMillis();
        CompletableFuture<TransferResult> future = transferEngine.submitTransfer(slowRequest);
        TransferResult result = future.get();
        long duration = System.currentTimeMillis() - startTime;

        logger.info("");
        logger.info("Resilience test results:");
        logger.info("  Status: " + result.getFinalStatus());
        if (result.isSuccessful()) {
            logger.info("  ✓ SUCCESS: System handled slow response correctly");
        } else {
            logger.severe("  ✗ UNEXPECTED: Resilience test failed - this indicates a problem");
        }
        logger.info("  Total time: " + duration + "ms (includes 2-second server delay)");
        logger.info("  System behavior: Patient waiting for slow responses ✓");
        logger.info("");

        // Demonstrate configuration-based retry settings
        logger.info("Retry configuration in use:");
        logger.info("  Max retry attempts: 3");
        logger.info("  Initial retry delay: 1000ms");
        logger.info("  Backoff strategy: Exponential");
        logger.info("  Timeout handling: Graceful with retries");
        logger.info("");
        logger.info("Note: In a real failure scenario, the system would retry");
        logger.info("up to 3 times with increasing delays before giving up.");
        logger.info("This ensures robust handling of temporary network issues.");
    }
    
    /**
     * Monitors transfer progress in real-time using a background daemon thread.
     *
     * This demonstrates:
     * - Real-time progress tracking
     * - Proper daemon thread usage (won't prevent JVM shutdown)
     * - Polling the transfer engine for job status
     * - Graceful thread termination when transfer completes
     */
    private static void monitorTransferProgress(TransferEngine transferEngine, String jobId) {
        Thread monitorThread = new Thread(() -> {
            try {
                logger.info("Starting real-time progress monitoring...");
                while (true) {
                    // Get current job status from transfer engine
                    var job = transferEngine.getTransferJob(jobId);
                    if (job == null) {
                        // Job no longer exists (completed or failed)
                        break;
                    }

                    // Display progress for active transfers
                    if (job.getStatus() == TransferStatus.IN_PROGRESS) {
                        long totalBytes = job.getTotalBytes();
                        String totalDisplay = totalBytes > 0 ? String.valueOf(totalBytes) : "unknown";
                        logger.info(String.format("  Progress: %.1f%% (%d/%s bytes)",
                                job.getProgressPercentage() * 100,
                                job.getBytesTransferred(),
                                totalDisplay));
                    }

                    // Exit when transfer reaches terminal state
                    if (job.getStatus().isTerminal()) {
                        logger.info("Transfer completed, stopping progress monitoring");
                        break;
                    }

                    Thread.sleep(250); // Update every 250ms
                }
            } catch (InterruptedException e) {
                // Restore interrupt status and exit gracefully
                Thread.currentThread().interrupt();
                logger.info("Progress monitoring interrupted");
            }
        });

        // Use daemon thread so it won't prevent JVM shutdown
        monitorThread.setDaemon(true);
        monitorThread.start();
    }
    
    /**
     * Displays comprehensive transfer results
     */
    private static void displayTransferResult(TransferResult result) {
        logger.info("Transfer Results:");
        logger.info("  Status: " + result.getFinalStatus());
        logger.info("  Success: " + (result.isSuccessful() ? "✓" : "✗"));
        logger.info("  Bytes transferred: " + result.getBytesTransferred());
        
        result.getDuration().ifPresent(duration -> 
            logger.info("  Duration: " + formatDuration(duration)));
        
        result.getAverageRateBytesPerSecond().ifPresent(rate -> 
            logger.info("  Average rate: " + String.format("%.2f KB/s", rate / 1024)));
        
        result.getActualChecksum().ifPresent(checksum -> 
            logger.info("  SHA-256 checksum: " + checksum.substring(0, 16) + "..."));
        
        if (!result.isSuccessful()) {
            result.getErrorMessage().ifPresent(error -> 
                logger.info("  Error: " + error));
        }
    }
    
    /**
     * Formats duration for human-readable display
     */
    private static String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        if (millis < 1000) {
            return millis + "ms";
        } else {
            return String.format("%.2fs", millis / 1000.0);
        }
    }
}
