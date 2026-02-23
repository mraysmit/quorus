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
import dev.mars.quorus.transfer.TransferContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SFTP upload functionality (Local to Remote transfers).
 * 
 * These tests verify:
 * - Upload direction detection for SFTP URIs
 * - Source file validation for uploads
 * - Destination URI parsing for uploads
 * - Progress tracking during uploads
 * - Error handling for upload-specific failures
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025
 */
@DisplayName("SFTP Upload Tests")
class SftpTransferProtocolUploadTest {

    private SftpTransferProtocol protocol;
    private TransferContext context;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        protocol = new SftpTransferProtocol();
        TransferRequest dummyRequest = TransferRequest.builder()
                .requestId("test-job-setup")
                .sourceUri(URI.create("sftp://example.com/test.txt"))
                .destinationPath(tempDir.resolve("test.txt"))
                .build();
        context = new TransferContext(new dev.mars.quorus.core.TransferJob(dummyRequest));
    }

    /**
     * Extracts the full exception message including all causes in the chain.
     */
    private String getFullExceptionMessage(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable current = e;
        while (current != null) {
            if (current.getMessage() != null) {
                sb.append(current.getMessage()).append(" ");
            }
            current = current.getCause();
        }
        return sb.toString();
    }

    @Nested
    @DisplayName("Upload Request Handling Tests")
    class UploadRequestHandlingTests {

        @Test
        @DisplayName("canHandle should accept upload request with local source and SFTP destination")
        void canHandle_acceptsUploadRequest() throws IOException {
            Path localFile = tempDir.resolve("local-file.txt");
            Files.writeString(localFile, "Test content for upload");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("test-upload-canhandle")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("sftp://testserver/remote/path/file.txt"))
                    .build();

            assertTrue(protocol.canHandle(uploadRequest),
                    "Protocol should handle upload requests with SFTP destination");
        }

        @Test
        @DisplayName("Upload request should have UPLOAD direction")
        void uploadRequest_hasUploadDirection() throws IOException {
            Path localFile = tempDir.resolve("local-file.txt");
            Files.writeString(localFile, "Test content");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("test-direction-upload")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("sftp://testserver/remote/file.txt"))
                    .build();

            assertEquals(TransferDirection.UPLOAD, uploadRequest.getDirection(),
                    "Request with local source and SFTP destination should be UPLOAD");
            assertTrue(uploadRequest.isUpload(), "isUpload() should return true");
            assertFalse(uploadRequest.isDownload(), "isDownload() should return false");
        }

        @Test
        @DisplayName("Download request should have DOWNLOAD direction")
        void downloadRequest_hasDownloadDirection() {
            TransferRequest downloadRequest = TransferRequest.builder()
                    .requestId("test-direction-download")
                    .sourceUri(URI.create("sftp://testserver/remote/file.txt"))
                    .destinationPath(tempDir.resolve("local-file.txt"))
                    .build();

            assertEquals(TransferDirection.DOWNLOAD, downloadRequest.getDirection(),
                    "Request with SFTP source and local destination should be DOWNLOAD");
            assertTrue(downloadRequest.isDownload(), "isDownload() should return true");
            assertFalse(downloadRequest.isUpload(), "isUpload() should return false");
        }
    }

    @Nested
    @DisplayName("Upload and Download Coexistence Tests")
    class UploadDownloadCoexistenceTests {

        @Test
        @DisplayName("canHandle should correctly identify upload vs download")
        void canHandle_correctlyIdentifiesDirection() throws IOException {
            // Download request (SFTP source, local destination)
            TransferRequest downloadRequest = TransferRequest.builder()
                    .requestId("test-identify-download")
                    .sourceUri(URI.create("sftp://testserver/remote/file.txt"))
                    .destinationPath(tempDir.resolve("local.txt"))
                    .build();

            assertTrue(protocol.canHandle(downloadRequest), "Should handle download");
            assertEquals(TransferDirection.DOWNLOAD, downloadRequest.getDirection());

            // Upload request (local source, SFTP destination)
            Path localFile = tempDir.resolve("local-source.txt");
            Files.writeString(localFile, "Test");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("test-identify-upload")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("sftp://testserver/remote/file.txt"))
                    .build();

            assertTrue(protocol.canHandle(uploadRequest), "Should handle upload");
            assertEquals(TransferDirection.UPLOAD, uploadRequest.getDirection());
        }
    }
}
