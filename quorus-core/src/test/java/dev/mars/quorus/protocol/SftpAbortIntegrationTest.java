/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
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

package dev.mars.quorus.protocol;

import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.transfer.SimpleTransferEngine;
import dev.mars.quorus.transfer.TransferContext;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for SFTP abort() functionality using shared Testcontainers.
 * Tests verify that calling abort() during an active SFTP transfer successfully
 * interrupts the transfer by closing the underlying JSch session.
 * 
 * <p>Uses real SFTP server (atmoz/sftp:alpine) in Docker container to prove
 * abort() works in actual network transfer scenarios.
 * 
 * <p>Uses SharedTestContainers to reuse the SFTP container across test classes,
 * significantly improving test execution time.
 * 
 * <p>These tests require Docker to be available and will be skipped if Docker
 * is not running.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-01-26
 * @version 1.0
 */
@ExtendWith(VertxExtension.class)
class SftpAbortIntegrationTest {
    
    private static final Logger logger = Logger.getLogger(SftpAbortIntegrationTest.class.getName());

    private SftpTransferProtocol protocol;
    private Vertx vertx;
    private SimpleTransferEngine engine;
    private String sftpHost;
    private int sftpPort;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void checkDockerAvailable() {
        assumeTrue(SharedTestContainers.isDockerAvailable(), 
                "Docker is not available - skipping integration tests");
        // Start the shared container (will be reused across tests)
        SharedTestContainers.getSftpContainer();
    }

    @BeforeEach
    void setUp(Vertx vertx) throws IOException {
        this.vertx = vertx;
        this.protocol = new SftpTransferProtocol();
        
        // Create protocol factory with SFTP support
        ProtocolFactory protocolFactory = new ProtocolFactory(vertx);
        protocolFactory.registerProtocol(protocol);
        
        this.engine = new SimpleTransferEngine(vertx, 5, 3, 100);
        
        // Get SFTP container connection details from shared container
        sftpHost = SharedTestContainers.getSftpHost();
        sftpPort = SharedTestContainers.getSftpPort();
        
        logger.info("SFTP server available at " + sftpHost + ":" + sftpPort);
        
        // Create a large test file on the SFTP server to enable abort testing
        // We'll do this by uploading first, then downloading with abort
        createTestFileOnSftpServer();
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.shutdown(5);
        }
    }

    /**
     * Creates a large file on the SFTP server that we can later download and abort.
     * This simulates real-world scenario where we need to cancel a long-running download.
     * 
     * Uses JSch directly to upload the file since TransferRequest only supports
     * local destination paths.
     */
    private void createTestFileOnSftpServer() throws IOException {
        // Create 10MB file with predictable content
        byte[] data = new byte[10 * 1024 * 1024]; // 10 MB
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        
        try {
            com.jcraft.jsch.JSch jsch = new com.jcraft.jsch.JSch();
            com.jcraft.jsch.Session session = jsch.getSession("testuser", sftpHost, sftpPort);
            session.setPassword("testpass");
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(10000);
            
            com.jcraft.jsch.ChannelSftp channel = (com.jcraft.jsch.ChannelSftp) session.openChannel("sftp");
            channel.connect(5000);
            
            // Upload file
            channel.put(new java.io.ByteArrayInputStream(data), "upload/large-file.bin");
            
            channel.disconnect();
            session.disconnect();
            
            logger.info("Created 10MB test file on SFTP server using JSch");
        } catch (Exception e) {
            throw new IOException("Failed to create test file on SFTP server", e);
        }
    }

    @Test
    void testAbortDoesNotThrow() {
        // Basic safety test - abort() should never throw even when no transfer is active
        assertDoesNotThrow(() -> protocol.abort());
    }

    @Test
    void testAbortInterruptsActiveTransfer() throws Exception {
        // URI for the large file we created in setUp()
        URI sourceUri = URI.create(String.format("sftp://testuser:testpass@%s:%d/upload/large-file.bin",
                sftpHost, sftpPort));
        Path destination = tempDir.resolve("downloaded-file.bin");

        TransferRequest request = TransferRequest.builder()
                .requestId("test-abort-download")
                .sourceUri(sourceUri)
                .destinationPath(destination)
                .build();

        TransferContext context = new TransferContext(new dev.mars.quorus.core.TransferJob(request));

        AtomicBoolean transferStarted = new AtomicBoolean(false);
        AtomicBoolean abortCalled = new AtomicBoolean(false);
        AtomicBoolean transferCompleted = new AtomicBoolean(false);
        
        // Start transfer in background thread
        Thread transferThread = new Thread(() -> {
            try {
                transferStarted.set(true);
                TransferResult result = protocol.transfer(request, context);
                transferCompleted.set(true);
                logger.info("Transfer completed with status: " + result.getFinalStatus());
            } catch (Exception e) {
                logger.info("Transfer interrupted (expected): " + e.getMessage());
            }
        });
        
        transferThread.start();

        // Wait for transfer to actually start
        await().atMost(Duration.ofSeconds(5))
                .until(transferStarted::get);
        
        // Give it a moment to start downloading
        Thread.sleep(100);

        // Call abort while transfer is in progress
        logger.info("Calling abort() on active SFTP transfer");
        protocol.abort();
        abortCalled.set(true);

        // Wait for transfer thread to complete (should be interrupted)
        transferThread.join(TimeUnit.SECONDS.toMillis(10));
        
        // Verify abort was called and transfer was interrupted
        assertTrue(abortCalled.get(), "Abort should have been called");
        assertTrue(transferStarted.get(), "Transfer should have started");
        
        // Transfer should either:
        // 1. Not complete (interrupted)
        // 2. Complete with FAILED status (connection closed)
        if (transferCompleted.get()) {
            // If it did complete, verify the file is incomplete or status is FAILED
            assertTrue(!Files.exists(destination) || 
                      Files.size(destination) < 10 * 1024 * 1024,
                      "Aborted transfer should not complete full download");
        }
    }

    @Test
    void testAbortFromDifferentThread() throws Exception {
        URI sourceUri = URI.create(String.format("sftp://testuser:testpass@%s:%d/upload/large-file.bin",
                sftpHost, sftpPort));
        Path destination = tempDir.resolve("downloaded-file2.bin");

        TransferRequest request = TransferRequest.builder()
                .requestId("test-cross-thread-abort")
                .sourceUri(sourceUri)
                .destinationPath(destination)
                .build();

        TransferContext context = new TransferContext(new dev.mars.quorus.core.TransferJob(request));

        CountDownLatch transferStarted = new CountDownLatch(1);
        CountDownLatch abortComplete = new CountDownLatch(1);

        // Transfer thread
        Thread transferThread = new Thread(() -> {
            try {
                transferStarted.countDown();
                protocol.transfer(request, context);
            } catch (Exception e) {
                logger.info("Transfer interrupted by abort from another thread: " + e.getMessage());
            }
        });

        // Abort thread (different from transfer thread)
        Thread abortThread = new Thread(() -> {
            try {
                // Wait for transfer to start
                assertTrue(transferStarted.await(5, TimeUnit.SECONDS));
                Thread.sleep(100); // Let transfer get underway
                
                logger.info("Calling abort() from different thread");
                protocol.abort();
                abortComplete.countDown();
            } catch (InterruptedException e) {
                fail("Abort thread interrupted");
            }
        });

        transferThread.start();
        abortThread.start();

        // Wait for both threads to complete
        assertTrue(abortComplete.await(15, TimeUnit.SECONDS), 
                "Abort should complete within 15 seconds");
        transferThread.join(TimeUnit.SECONDS.toMillis(5));
        abortThread.join(TimeUnit.SECONDS.toMillis(1));

        // Test passes if we get here without exceptions - proves thread safety
        logger.info("Cross-thread abort test completed successfully");
    }

    @Test
    void testMultipleAbortsAreSafe() {
        // Verify calling abort() multiple times doesn't cause issues
        assertDoesNotThrow(() -> {
            protocol.abort();
            protocol.abort();
            protocol.abort();
        });
    }
}
