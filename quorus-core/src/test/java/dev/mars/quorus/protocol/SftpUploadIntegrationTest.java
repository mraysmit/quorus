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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for SFTP upload functionality using shared Testcontainers.
 * 
 * These tests verify upload operations against a real SFTP server running
 * in a Docker container (atmoz/sftp:alpine), ensuring the implementation
 * works in actual network transfer scenarios.
 * 
 * Uses SharedTestContainers to reuse the SFTP container across test classes,
 * significantly improving test execution time.
 * 
 * These tests require Docker to be available and will be skipped if Docker
 * is not running.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025
 */
@DisplayName("SFTP Upload Integration Tests")
class SftpUploadIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(SftpUploadIntegrationTest.class);

    private SftpTransferProtocol protocol;
    private TransferContext context;
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
    void setUp() {
        protocol = new SftpTransferProtocol();

        // Get SFTP container connection details from shared container
        sftpHost = SharedTestContainers.getSftpHost();
        sftpPort = SharedTestContainers.getSftpPort();

        logger.info("SFTP server available at {}:{}", sftpHost, sftpPort);

        // Create context for tests
        TransferRequest dummyRequest = TransferRequest.builder()
                .requestId("integration-test-setup")
                .sourceUri(URI.create("sftp://" + sftpHost + ":" + sftpPort + "/test.txt"))
                .destinationPath(tempDir.resolve("test.txt"))
                .build();
        context = new TransferContext(new dev.mars.quorus.core.TransferJob(dummyRequest));
    }

    @Test
    @DisplayName("Upload small file to real SFTP server")
    void uploadSmallFile() throws IOException, TransferException {
        // Create local file to upload
        Path localFile = tempDir.resolve("small-upload.txt");
        String content = "Hello from integration test!";
        Files.writeString(localFile, content);

        // Build upload request
        TransferRequest uploadRequest = TransferRequest.builder()
                .requestId("integration-upload-small")
                .sourceUri(localFile.toUri())
                .destinationUri(buildSftpUri("/upload/small-upload.txt"))
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

        // Verify file exists on server by downloading it back
        String downloadedContent = downloadFileFromServer("/upload/small-upload.txt");
        assertEquals(content, downloadedContent);
    }

    @Test
    @DisplayName("Upload 1KB file to real SFTP server")
    void upload1KBFile() throws IOException, TransferException {
        // Create 1KB file
        Path localFile = tempDir.resolve("1kb-upload.txt");
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            content.append("Line ").append(i).append(": This is test content for 1KB upload\n");
        }
        Files.writeString(localFile, content.toString());
        long expectedSize = Files.size(localFile);

        // Build upload request
        TransferRequest uploadRequest = TransferRequest.builder()
                .requestId("integration-upload-1kb")
                .sourceUri(localFile.toUri())
                .destinationUri(buildSftpUri("/upload/1kb-upload.txt"))
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
    @DisplayName("Upload to nested directory on real SFTP server")
    void uploadToNestedDirectory() throws IOException, TransferException {
        // Create local file
        Path localFile = tempDir.resolve("nested-upload.txt");
        String content = "Content for nested directory upload";
        Files.writeString(localFile, content);

        // Build upload request to nested path
        TransferRequest uploadRequest = TransferRequest.builder()
                .requestId("integration-upload-nested")
                .sourceUri(localFile.toUri())
                .destinationUri(buildSftpUri("/upload/deep/nested/path/nested-upload.txt"))
                .build();

        // Execute upload
        TransferResult result = protocol.transfer(uploadRequest, context);

        // Verify result
        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());

        // Verify file exists on server
        String downloadedContent = downloadFileFromServer("/upload/deep/nested/path/nested-upload.txt");
        assertEquals(content, downloadedContent);
    }

    @Test
    @DisplayName("Upload and download roundtrip")
    void uploadDownloadRoundtrip() throws IOException, TransferException {
        // Create local file with unique content
        Path localFile = tempDir.resolve("roundtrip-source.txt");
        String originalContent = "Roundtrip test content - " + System.currentTimeMillis();
        Files.writeString(localFile, originalContent);

        // Upload
        TransferRequest uploadRequest = TransferRequest.builder()
                .requestId("integration-roundtrip-upload")
                .sourceUri(localFile.toUri())
                .destinationUri(buildSftpUri("/upload/roundtrip.txt"))
                .build();

        TransferResult uploadResult = protocol.transfer(uploadRequest, context);
        assertEquals(TransferStatus.COMPLETED, uploadResult.getFinalStatus());

        // Download back
        Path downloadedFile = tempDir.resolve("roundtrip-downloaded.txt");
        TransferRequest downloadRequest = TransferRequest.builder()
                .requestId("integration-roundtrip-download")
                .sourceUri(buildSftpUri("/upload/roundtrip.txt"))
                .destinationPath(downloadedFile)
                .build();

        TransferResult downloadResult = protocol.transfer(downloadRequest, context);
        assertEquals(TransferStatus.COMPLETED, downloadResult.getFinalStatus());

        // Verify content matches
        String downloadedContent = Files.readString(downloadedFile);
        assertEquals(originalContent, downloadedContent);
    }

    @Test
    @DisplayName("Upload binary file to real SFTP server")
    void uploadBinaryFile() throws IOException, TransferException {
        // Create binary file
        Path localFile = tempDir.resolve("binary-upload.bin");
        byte[] binaryContent = new byte[1024];
        for (int i = 0; i < binaryContent.length; i++) {
            binaryContent[i] = (byte) (i % 256);
        }
        Files.write(localFile, binaryContent);

        // Build upload request
        TransferRequest uploadRequest = TransferRequest.builder()
                .requestId("integration-upload-binary")
                .sourceUri(localFile.toUri())
                .destinationUri(buildSftpUri("/upload/binary-upload.bin"))
                .build();

        // Execute upload
        TransferResult result = protocol.transfer(uploadRequest, context);

        // Verify result
        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        assertEquals(binaryContent.length, result.getBytesTransferred());

        // Verify binary content matches by downloading
        byte[] downloadedContent = downloadBinaryFileFromServer("/upload/binary-upload.bin");
        assertArrayEquals(binaryContent, downloadedContent);
    }

    /**
     * Builds an SFTP URI for the test server.
     */
    private URI buildSftpUri(String path) {
        return URI.create("sftp://testuser:testpass@" + sftpHost + ":" + sftpPort + path);
    }

    /**
     * Downloads a file from the SFTP server using JSch directly for verification.
     */
    private String downloadFileFromServer(String remotePath) throws IOException {
        byte[] content = downloadBinaryFileFromServer(remotePath);
        return new String(content, StandardCharsets.UTF_8);
    }

    /**
     * Downloads a binary file from the SFTP server using JSch directly for verification.
     */
    private byte[] downloadBinaryFileFromServer(String remotePath) throws IOException {
        try {
            com.jcraft.jsch.JSch jsch = new com.jcraft.jsch.JSch();
            com.jcraft.jsch.Session session = jsch.getSession("testuser", sftpHost, sftpPort);
            session.setPassword("testpass");
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(10000);

            com.jcraft.jsch.ChannelSftp channel = (com.jcraft.jsch.ChannelSftp) session.openChannel("sftp");
            channel.connect(5000);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            channel.get(remotePath, baos);

            channel.disconnect();
            session.disconnect();

            return baos.toByteArray();
        } catch (Exception e) {
            throw new IOException("Failed to download file from SFTP server: " + remotePath, e);
        }
    }
}
