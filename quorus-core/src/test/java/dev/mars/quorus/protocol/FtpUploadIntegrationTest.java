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

package dev.mars.quorus.protocol;

import dev.mars.quorus.core.TransferDirection;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.transfer.TransferContext;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for FTP upload functionality using shared Testcontainers.
 * 
 * These tests verify upload operations against a real FTP server running
 * in a Docker container (fauria/vsftpd), ensuring the implementation
 * works in actual network transfer scenarios.
 * 
 * Uses SharedTestContainers to reuse the FTP container across test classes,
 * significantly improving test execution time.
 * 
 * These tests require Docker to be available and will be skipped if Docker
 * is not running.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025
 */
@DisplayName("FTP Upload Integration Tests")
class FtpUploadIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(FtpUploadIntegrationTest.class);

    private FtpTransferProtocol protocol;
    private TransferContext context;
    private String ftpHost;
    private int ftpPort;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void checkDockerAvailable() {
        assumeTrue(SharedTestContainers.isDockerAvailable(), 
                "Docker is not available - skipping integration tests");
        // Start the shared container (will be reused across tests)
        SharedTestContainers.getFtpContainer();
    }

    @BeforeEach
    void setUp() {
        protocol = new FtpTransferProtocol();

        // Get FTP container connection details from shared container
        ftpHost = SharedTestContainers.getFtpHost();
        ftpPort = SharedTestContainers.getFtpPort();

        logger.info("FTP server available at {}:{}", ftpHost, ftpPort);

        // Create context for tests
        TransferRequest dummyRequest = TransferRequest.builder()
                .requestId("integration-test-setup")
                .sourceUri(URI.create("ftp://" + ftpHost + ":" + ftpPort + "/test.txt"))
                .destinationPath(tempDir.resolve("test.txt"))
                .build();
        context = new TransferContext(new dev.mars.quorus.core.TransferJob(dummyRequest));
    }

    @Test
    @DisplayName("Upload small file to real FTP server")
    void uploadSmallFile() throws IOException, TransferException {
        // Create local file to upload
        Path localFile = tempDir.resolve("small-upload.txt");
        String content = "Hello from FTP integration test!";
        Files.writeString(localFile, content);

        // Build upload request
        TransferRequest uploadRequest = TransferRequest.builder()
                .requestId("integration-ftp-upload-small")
                .sourceUri(localFile.toUri())
                .destinationUri(buildFtpUri("/small-upload.txt"))
                .build();

        // Verify direction detection
        assertEquals(TransferDirection.UPLOAD, uploadRequest.getDirection());
        assertTrue(protocol.canHandle(uploadRequest));

        // Execute upload
        TransferResult result = protocol.transfer(uploadRequest, context);

        // Verify result
        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        assertEquals(content.length(), result.getBytesTransferred());
    }

    @Test
    @DisplayName("Upload 1KB file to real FTP server")
    void upload1KBFile() throws IOException, TransferException {
        // Create 1KB file
        Path localFile = tempDir.resolve("1kb-upload.txt");
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            content.append("Line ").append(i).append(": This is test content for 1KB FTP upload\n");
        }
        Files.writeString(localFile, content.toString());
        long expectedSize = Files.size(localFile);

        // Build upload request
        TransferRequest uploadRequest = TransferRequest.builder()
                .requestId("integration-ftp-upload-1kb")
                .sourceUri(localFile.toUri())
                .destinationUri(buildFtpUri("/1kb-upload.txt"))
                .build();

        // Execute upload
        TransferResult result = protocol.transfer(uploadRequest, context);

        // Verify result
        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        assertEquals(expectedSize, result.getBytesTransferred());
        assertTrue(result.getStartTime().isPresent());
        assertTrue(result.getEndTime().isPresent());
    }

    @Test
    @DisplayName("Upload and download roundtrip via FTP")
    void uploadDownloadRoundtrip() throws IOException, TransferException {
        // Create local file with unique content
        Path localFile = tempDir.resolve("roundtrip-source.txt");
        String originalContent = "FTP Roundtrip test content - " + System.currentTimeMillis();
        Files.writeString(localFile, originalContent);

        // Upload
        TransferRequest uploadRequest = TransferRequest.builder()
                .requestId("integration-ftp-roundtrip-upload")
                .sourceUri(localFile.toUri())
                .destinationUri(buildFtpUri("/roundtrip.txt"))
                .build();

        TransferResult uploadResult = protocol.transfer(uploadRequest, context);
        assertEquals(TransferStatus.COMPLETED, uploadResult.getFinalStatus());

        // Download back
        Path downloadedFile = tempDir.resolve("roundtrip-downloaded.txt");
        TransferRequest downloadRequest = TransferRequest.builder()
                .requestId("integration-ftp-roundtrip-download")
                .sourceUri(buildFtpUri("/roundtrip.txt"))
                .destinationPath(downloadedFile)
                .build();

        TransferResult downloadResult = protocol.transfer(downloadRequest, context);
        assertEquals(TransferStatus.COMPLETED, downloadResult.getFinalStatus());

        // Verify content matches
        String downloadedContent = Files.readString(downloadedFile);
        assertEquals(originalContent, downloadedContent);
    }

    @Test
    @DisplayName("Upload binary file to real FTP server")
    void uploadBinaryFile() throws IOException, TransferException {
        // Create binary file
        Path localFile = tempDir.resolve("binary-upload.bin");
        byte[] binaryContent = new byte[512];
        for (int i = 0; i < binaryContent.length; i++) {
            binaryContent[i] = (byte) (i % 256);
        }
        Files.write(localFile, binaryContent);

        // Build upload request
        TransferRequest uploadRequest = TransferRequest.builder()
                .requestId("integration-ftp-upload-binary")
                .sourceUri(localFile.toUri())
                .destinationUri(buildFtpUri("/binary-upload.bin"))
                .build();

        // Execute upload
        TransferResult result = protocol.transfer(uploadRequest, context);

        // Verify result
        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        assertEquals(binaryContent.length, result.getBytesTransferred());
    }

    @Test
    @DisplayName("Upload with timing information")
    void uploadWithTimingInfo() throws IOException, TransferException {
        // Create file
        Path localFile = tempDir.resolve("timing-test.txt");
        String content = "Content for timing test";
        Files.writeString(localFile, content);

        // Build upload request
        TransferRequest uploadRequest = TransferRequest.builder()
                .requestId("integration-ftp-timing")
                .sourceUri(localFile.toUri())
                .destinationUri(buildFtpUri("/timing-test.txt"))
                .build();

        // Execute upload
        TransferResult result = protocol.transfer(uploadRequest, context);

        // Verify timing
        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        assertTrue(result.getStartTime().isPresent(), "Start time should be present");
        assertTrue(result.getEndTime().isPresent(), "End time should be present");
        assertTrue(result.getEndTime().get().isAfter(result.getStartTime().get()) ||
                        result.getEndTime().get().equals(result.getStartTime().get()),
                "End time should be at or after start time");
    }

    /**
     * Builds an FTP URI for the test server.
     */
    private URI buildFtpUri(String path) {
        return URI.create("ftp://testuser:testpass@" + ftpHost + ":" + ftpPort + path);
    }
}
