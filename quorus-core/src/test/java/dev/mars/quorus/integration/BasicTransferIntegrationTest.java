package dev.mars.quorus.integration;

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


import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.transfer.SimpleTransferEngine;
import dev.mars.quorus.transfer.TransferEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import io.vertx.core.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the basic transfer engine functionality.
 * Tests end-to-end file transfer scenarios using a local HTTP test server.
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-17
 */
class BasicTransferIntegrationTest {

    @TempDir
    Path tempDir;

    private TransferEngine transferEngine;
    private LocalHttpTestServer testServer;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        transferEngine = new SimpleTransferEngine(5, 2, 500);
        testServer = new LocalHttpTestServer();
        baseUrl = testServer.getBaseUrl();
    }

    @AfterEach
    void tearDown() {
        if (transferEngine != null) {
            transferEngine.shutdown(5);
        }
        if (testServer != null) {
            testServer.stop();
        }
    }
    
    @Test
    void testBasicHttpTransfer() throws Exception {
        // Use local test server instead of external httpbin.org
        URI sourceUri = URI.create(baseUrl + "/bytes/1024"); // 1KB test file
        Path destinationPath = tempDir.resolve("test-file.bin");

        TransferRequest request = TransferRequest.builder()
                .sourceUri(sourceUri)
                .destinationPath(destinationPath)
                .protocol("http")
                .build();

        Future<TransferResult> future = transferEngine.submitTransfer(request);
        TransferResult result = future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);

        // Verify result
        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        assertTrue(result.isSuccessful());
        assertEquals(1024, result.getBytesTransferred());
        assertTrue(result.getActualChecksum().isPresent());
        assertTrue(result.getDuration().isPresent());
        assertTrue(result.getAverageRateBytesPerSecond().isPresent());

        // Verify file was created
        assertTrue(Files.exists(destinationPath));
        assertEquals(1024, Files.size(destinationPath));
    }
    
    @Test
    void testSmallFileTransfer() throws Exception {
        URI sourceUri = URI.create(baseUrl + "/bytes/100"); // 100 bytes
        Path destinationPath = tempDir.resolve("small-file.bin");

        TransferRequest request = TransferRequest.builder()
                .sourceUri(sourceUri)
                .destinationPath(destinationPath)
                .protocol("http")
                .build();

        Future<TransferResult> future = transferEngine.submitTransfer(request);
        TransferResult result = future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertTrue(result.isSuccessful());
        assertEquals(100, result.getBytesTransferred());
        assertTrue(Files.exists(destinationPath));
        assertEquals(100, Files.size(destinationPath));
    }

    @Test
    void testLargerFileTransfer() throws Exception {
        URI sourceUri = URI.create(baseUrl + "/bytes/10240"); // 10KB
        Path destinationPath = tempDir.resolve("larger-file.bin");

        TransferRequest request = TransferRequest.builder()
                .sourceUri(sourceUri)
                .destinationPath(destinationPath)
                .protocol("http")
                .build();

        Future<TransferResult> future = transferEngine.submitTransfer(request);
        TransferResult result = future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertTrue(result.isSuccessful());
        assertEquals(10240, result.getBytesTransferred());
        assertTrue(Files.exists(destinationPath));
        assertEquals(10240, Files.size(destinationPath));
    }

    @Test
    void testTransferWithProgressTracking() throws Exception {
        // Use a larger file to ensure we can observe IN_PROGRESS status
        URI sourceUri = URI.create(baseUrl + "/bytes/1048576"); // 1MB
        Path destinationPath = tempDir.resolve("progress-test.bin");

        TransferRequest request = TransferRequest.builder()
                .sourceUri(sourceUri)
                .destinationPath(destinationPath)
                .protocol("http")
                .build();

        Future<TransferResult> future = transferEngine.submitTransfer(request);

        // Monitor progress
        String jobId = request.getRequestId();
        boolean foundInProgress = false;
        boolean foundPending = false;

        // Check immediately for PENDING status
        var initialJob = transferEngine.getTransferJob(jobId);
        if (initialJob != null && initialJob.getStatus() == TransferStatus.PENDING) {
            foundPending = true;
        }

        while (!future.toCompletionStage().toCompletableFuture().isDone()) {
            var job = transferEngine.getTransferJob(jobId);
            if (job != null && job.getStatus() == TransferStatus.IN_PROGRESS) {
                foundInProgress = true;
                assertTrue(job.getBytesTransferred() >= 0);
                assertTrue(job.getProgressPercentage() >= 0.0);
                assertTrue(job.getProgressPercentage() <= 1.0);
            }
            Thread.sleep(10); // Check more frequently
        }

        // Should have observed either PENDING or IN_PROGRESS status
        assertTrue(foundPending || foundInProgress, "Should have observed PENDING or IN_PROGRESS status");

        TransferResult result = future.toCompletionStage().toCompletableFuture().get();
        assertTrue(result.isSuccessful());
        assertEquals(1048576, result.getBytesTransferred());
    }

    @Test
    void testInvalidUrlTransfer() throws Exception {
        URI sourceUri = URI.create(baseUrl + "/status/404"); // Returns 404
        Path destinationPath = tempDir.resolve("invalid-file.bin");

        TransferRequest request = TransferRequest.builder()
                .sourceUri(sourceUri)
                .destinationPath(destinationPath)
                .protocol("http")
                .build();

        Future<TransferResult> future = transferEngine.submitTransfer(request);
        TransferResult result = future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);

        // Should fail due to 404
        assertFalse(result.isSuccessful());
        assertEquals(TransferStatus.FAILED, result.getFinalStatus());
        assertTrue(result.getErrorMessage().isPresent());
        assertFalse(Files.exists(destinationPath));
    }

    @Test
    void testConcurrentTransfers() throws Exception {
        int numTransfers = 3;
        @SuppressWarnings("unchecked")
        Future<TransferResult>[] futures = new Future[numTransfers];

        for (int i = 0; i < numTransfers; i++) {
            URI sourceUri = URI.create(baseUrl + "/bytes/512"); // 512 bytes each
            Path destinationPath = tempDir.resolve("concurrent-" + i + ".bin");

            TransferRequest request = TransferRequest.builder()
                    .sourceUri(sourceUri)
                    .destinationPath(destinationPath)
                    .protocol("http")
                    .build();

            futures[i] = transferEngine.submitTransfer(request);
        }

        // Wait for all transfers to complete
        for (int i = 0; i < numTransfers; i++) {
            TransferResult result = futures[i].toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
            assertTrue(result.isSuccessful(), "Transfer " + i + " should succeed");
            assertEquals(512, result.getBytesTransferred());

            Path expectedFile = tempDir.resolve("concurrent-" + i + ".bin");
            assertTrue(Files.exists(expectedFile));
            assertEquals(512, Files.size(expectedFile));
        }
    }

    @Test
    void testTransferEngineShutdown() throws Exception {
        assertEquals(0, transferEngine.getActiveTransferCount());

        // Start a transfer
        URI sourceUri = URI.create(baseUrl + "/bytes/1024");
        Path destinationPath = tempDir.resolve("shutdown-test.bin");

        TransferRequest request = TransferRequest.builder()
                .sourceUri(sourceUri)
                .destinationPath(destinationPath)
                .protocol("http")
                .build();

        Future<TransferResult> future = transferEngine.submitTransfer(request);

        // Wait for transfer to complete
        TransferResult result = future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
        assertTrue(result.isSuccessful());

        // Shutdown should succeed
        assertTrue(transferEngine.shutdown(5));
        assertEquals(0, transferEngine.getActiveTransferCount());
    }
}
