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


import dev.mars.quorus.concurrent.TaskScope;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.transfer.SimpleTransferEngine;
import dev.mars.quorus.transfer.TransferEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the basic transfer engine functionality.
 * Tests end-to-end file transfer scenarios using a local HTTP test server.
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2025-08-17
 */
@Timeout(value = 60, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
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
        transferEngine.shutdown(Duration.ofSeconds(5));
        testServer.stop();
    }

    private TransferRequest request(String path, Path destination) {
        return TransferRequest.builder()
                .sourceUri(URI.create(baseUrl + path))
                .destinationPath(destination)
                .protocol("http")
                .build();
    }

    @Test
    void testBasicHttpTransfer() throws Exception {
        Path destinationPath = tempDir.resolve("test-file.bin");

        TransferResult result = transferEngine.transfer(request("/bytes/1024", destinationPath));

        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        assertTrue(result.isSuccessful());
        assertEquals(1024, result.getBytesTransferred());
        assertTrue(result.getActualChecksum().isPresent());
        assertTrue(result.getDuration().isPresent());
        assertTrue(result.getAverageRateBytesPerSecond().isPresent());
        assertTrue(Files.exists(destinationPath));
        assertEquals(1024, Files.size(destinationPath));
    }

    @Test
    void testSmallFileTransfer() throws Exception {
        Path destinationPath = tempDir.resolve("small-file.bin");

        TransferResult result = transferEngine.transfer(request("/bytes/100", destinationPath));

        assertTrue(result.isSuccessful());
        assertEquals(100, result.getBytesTransferred());
        assertEquals(100, Files.size(destinationPath));
    }

    @Test
    void testLargerFileTransfer() throws Exception {
        Path destinationPath = tempDir.resolve("larger-file.bin");

        TransferResult result = transferEngine.transfer(request("/bytes/10240", destinationPath));

        assertTrue(result.isSuccessful());
        assertEquals(10240, result.getBytesTransferred());
        assertEquals(10240, Files.size(destinationPath));
    }

    /**
     * A one-megabyte transfer. Progress of a running job is asserted deterministically in
     * SimpleTransferEngineBlockingTest, where the server holds the response; the former version
     * of this test polled the job every 10 ms, which the concurrency conventions prohibit.
     */
    @Test
    void testOneMegabyteTransfer() throws Exception {
        Path destinationPath = tempDir.resolve("progress-test.bin");

        TransferResult result = transferEngine.transfer(request("/bytes/1048576", destinationPath));

        assertTrue(result.isSuccessful());
        assertEquals(1048576, result.getBytesTransferred());
        assertEquals(1048576, Files.size(destinationPath));
    }

    @Test
    void testInvalidUrlTransfer() throws Exception {
        Path destinationPath = tempDir.resolve("invalid-file.bin");

        TransferResult result = transferEngine.transfer(request("/status/404", destinationPath));

        assertFalse(result.isSuccessful());
        assertEquals(TransferStatus.FAILED, result.getFinalStatus());
        assertTrue(result.getErrorMessage().isPresent());
        assertFalse(Files.exists(destinationPath));
    }

    /** Concurrent transfers are the caller's choice: here they are forked in a TaskScope. */
    @Test
    void testConcurrentTransfers() throws Exception {
        int numTransfers = 3;
        List<TaskScope.Subtask<TransferResult>> transfers = new ArrayList<>();
        try (TaskScope scope = TaskScope.open("concurrent-transfers", Duration.ofSeconds(30))) {
            for (int i = 0; i < numTransfers; i++) {
                TransferRequest request = request("/bytes/512", tempDir.resolve("concurrent-" + i + ".bin"));
                transfers.add(scope.fork(() -> transferEngine.transfer(request)));
            }
            scope.join();
        }

        for (int i = 0; i < numTransfers; i++) {
            TransferResult result = transfers.get(i).get();
            assertTrue(result.isSuccessful(), "Transfer " + i + " should succeed");
            assertEquals(512, result.getBytesTransferred());
            assertEquals(512, Files.size(tempDir.resolve("concurrent-" + i + ".bin")));
        }
    }

    @Test
    void testTransferEngineShutdown() throws Exception {
        assertEquals(0, transferEngine.getActiveTransferCount());
        TransferRequest request = request("/bytes/1024", tempDir.resolve("shutdown-test.bin"));
        CompletableFuture<TransferResult> outcome = new CompletableFuture<>();
        Thread transfer = Thread.ofVirtual().start(() -> {
            try {
                outcome.complete(transferEngine.transfer(request));
            } catch (Throwable failure) {
                outcome.completeExceptionally(failure);
            }
        });

        assertTrue(transferEngine.shutdown(Duration.ofSeconds(5)));
        transfer.join();

        // The transfer either finished before shutdown began, was cancelled by it, or was
        // rejected because shutdown had already begun.
        if (!outcome.isCompletedExceptionally()) {
            TransferStatus status = outcome.join().getFinalStatus();
            assertTrue(status == TransferStatus.COMPLETED || status == TransferStatus.CANCELLED,
                    "Transfer should reach a terminal state during shutdown: " + status);
        }
        assertEquals(0, transferEngine.getActiveTransferCount());
    }
}
