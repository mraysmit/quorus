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
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import io.vertx.core.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the basic transfer engine functionality.
 * Tests end-to-end file transfer scenarios using a local HTTP test server.
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-17
 */
@ExtendWith(VertxExtension.class)
class BasicTransferIntegrationTest {

    @TempDir
    Path tempDir;

    private TransferEngine transferEngine;
    private Vertx vertx;
    private LocalHttpTestServer testServer;
    private String baseUrl;

    @BeforeEach
    void setUp(Vertx vertx) throws Exception {
        this.vertx = vertx;
        transferEngine = new SimpleTransferEngine(vertx, 5, 2, 500);
        testServer = new LocalHttpTestServer();
        baseUrl = testServer.getBaseUrl();
    }

    @AfterEach
    void tearDown(VertxTestContext testContext) {
        if (transferEngine != null) {
            transferEngine.shutdown(5).onComplete(testContext.succeeding(v -> {
                if (testServer != null) {
                    testServer.stop();
                }
                testContext.completeNow();
            }));
            return;
        }

        if (testServer != null) {
            testServer.stop();
        }

        testContext.completeNow();
    }
    
    @Test
    void testBasicHttpTransfer(VertxTestContext testContext) throws Exception {
        // Use local test server instead of external httpbin.org
        URI sourceUri = URI.create(baseUrl + "/bytes/1024"); // 1KB test file
        Path destinationPath = tempDir.resolve("test-file.bin");

        TransferRequest request = TransferRequest.builder()
                .sourceUri(sourceUri)
                .destinationPath(destinationPath)
                .protocol("http")
                .build();

        transferEngine.submitTransfer(request).onComplete(testContext.succeeding(result ->
                testContext.verify(() -> {
                    assertNotNull(result);
                    assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
                    assertTrue(result.isSuccessful());
                    assertEquals(1024, result.getBytesTransferred());
                    assertTrue(result.getActualChecksum().isPresent());
                    assertTrue(result.getDuration().isPresent());
                    assertTrue(result.getAverageRateBytesPerSecond().isPresent());
                    assertTrue(Files.exists(destinationPath));
                    assertEquals(1024, Files.size(destinationPath));
                    testContext.completeNow();
                })));
    }
    
    @Test
    void testSmallFileTransfer(VertxTestContext testContext) throws Exception {
        URI sourceUri = URI.create(baseUrl + "/bytes/100"); // 100 bytes
        Path destinationPath = tempDir.resolve("small-file.bin");

        TransferRequest request = TransferRequest.builder()
                .sourceUri(sourceUri)
                .destinationPath(destinationPath)
                .protocol("http")
                .build();

        transferEngine.submitTransfer(request).onComplete(testContext.succeeding(result ->
                testContext.verify(() -> {
                    assertTrue(result.isSuccessful());
                    assertEquals(100, result.getBytesTransferred());
                    assertTrue(Files.exists(destinationPath));
                    assertEquals(100, Files.size(destinationPath));
                    testContext.completeNow();
                })));
    }

    @Test
    void testLargerFileTransfer(VertxTestContext testContext) throws Exception {
        URI sourceUri = URI.create(baseUrl + "/bytes/10240"); // 10KB
        Path destinationPath = tempDir.resolve("larger-file.bin");

        TransferRequest request = TransferRequest.builder()
                .sourceUri(sourceUri)
                .destinationPath(destinationPath)
                .protocol("http")
                .build();

        transferEngine.submitTransfer(request).onComplete(testContext.succeeding(result ->
                testContext.verify(() -> {
                    assertTrue(result.isSuccessful());
                    assertEquals(10240, result.getBytesTransferred());
                    assertTrue(Files.exists(destinationPath));
                    assertEquals(10240, Files.size(destinationPath));
                    testContext.completeNow();
                })));
    }

    @Test
    void testTransferWithProgressTracking(VertxTestContext testContext) throws Exception {
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

        final boolean[] observedPending = {foundPending};
        final boolean[] observedInProgress = {false};

        long timerId = vertx.setPeriodic(10, id -> {
            var job = transferEngine.getTransferJob(jobId);
            if (job != null && job.getStatus() == TransferStatus.IN_PROGRESS) {
                observedInProgress[0] = true;
                testContext.verify(() -> {
                    assertTrue(job.getBytesTransferred() >= 0);
                    assertTrue(job.getProgressPercentage() >= 0.0);
                    assertTrue(job.getProgressPercentage() <= 1.0);
                });
            }
        });

        future.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
            vertx.cancelTimer(timerId);
            assertTrue(observedPending[0] || observedInProgress[0],
                    "Should have observed PENDING or IN_PROGRESS status");
            assertTrue(result.isSuccessful());
            assertEquals(1048576, result.getBytesTransferred());
            testContext.completeNow();
        })));
    }

    @Test
    void testInvalidUrlTransfer(VertxTestContext testContext) throws Exception {
        URI sourceUri = URI.create(baseUrl + "/status/404"); // Returns 404
        Path destinationPath = tempDir.resolve("invalid-file.bin");

        TransferRequest request = TransferRequest.builder()
                .sourceUri(sourceUri)
                .destinationPath(destinationPath)
                .protocol("http")
                .build();

        transferEngine.submitTransfer(request).onComplete(testContext.succeeding(result ->
                testContext.verify(() -> {
                    assertFalse(result.isSuccessful());
                    assertEquals(TransferStatus.FAILED, result.getFinalStatus());
                    assertTrue(result.getErrorMessage().isPresent());
                    assertFalse(Files.exists(destinationPath));
                    testContext.completeNow();
                })));
    }

    @Test
    void testConcurrentTransfers(VertxTestContext testContext) throws Exception {
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

        final int[] completed = {0};

        for (int i = 0; i < numTransfers; i++) {
            Path expectedFile = tempDir.resolve("concurrent-" + i + ".bin");
            int transferIndex = i;
            futures[i].onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                assertTrue(result.isSuccessful(), "Transfer " + transferIndex + " should succeed");
                assertEquals(512, result.getBytesTransferred());
                assertTrue(Files.exists(expectedFile));
                assertEquals(512, Files.size(expectedFile));
                completed[0]++;
                if (completed[0] == numTransfers) {
                    testContext.completeNow();
                }
            })));
        }
    }

    @Test
    void testTransferEngineShutdown(VertxTestContext testContext) throws Exception {
        assertEquals(0, transferEngine.getActiveTransferCount());
        final boolean[] transferCompleted = {false};
        final boolean[] shutdownCompleted = {false};

        // Start a transfer
        URI sourceUri = URI.create(baseUrl + "/bytes/1024");
        Path destinationPath = tempDir.resolve("shutdown-test.bin");

        TransferRequest request = TransferRequest.builder()
                .sourceUri(sourceUri)
                .destinationPath(destinationPath)
                .protocol("http")
                .build();

        transferEngine.submitTransfer(request).onComplete(testContext.succeeding(result ->
                testContext.verify(() -> {
                    assertNotNull(result);
                    assertTrue(result.getFinalStatus() == TransferStatus.COMPLETED
                                    || result.getFinalStatus() == TransferStatus.FAILED,
                            "Transfer should reach a terminal state during shutdown");
                    transferCompleted[0] = true;
                    if (shutdownCompleted[0]) {
                        assertEquals(0, transferEngine.getActiveTransferCount());
                        testContext.completeNow();
                    }
                })));

        transferEngine.shutdown(5).onComplete(testContext.succeeding(v ->
                testContext.verify(() -> {
                    shutdownCompleted[0] = true;
                    if (transferCompleted[0]) {
                        assertEquals(0, transferEngine.getActiveTransferCount());
                        testContext.completeNow();
                    }
                })));
    }
}
