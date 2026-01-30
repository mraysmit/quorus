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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
    @DisplayName("Upload Transfer Execution Tests")
    class UploadTransferExecutionTests {

        @Test
        @DisplayName("Upload should succeed with valid local source file")
        void upload_succeedsWithValidSourceFile() throws IOException, TransferException {
            Path localFile = tempDir.resolve("upload-source.txt");
            String content = "This is test content for SFTP upload";
            Files.writeString(localFile, content);

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("test-upload-valid")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("sftp://testserver/remote/upload-target.txt"))
                    .build();

            TransferResult result = protocol.transfer(uploadRequest, context);

            assertNotNull(result, "Result should not be null");
            assertEquals("test-upload-valid", result.getRequestId());
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus(),
                    "Upload should complete successfully");
            assertTrue(result.getBytesTransferred() > 0,
                    "Bytes transferred should be positive");
        }

        @Test
        @DisplayName("Upload should transfer correct number of bytes")
        void upload_transfersCorrectByteCount() throws IOException, TransferException {
            Path localFile = tempDir.resolve("byte-count-test.txt");
            String content = "Exact content to measure bytes";
            Files.writeString(localFile, content, StandardCharsets.UTF_8);
            long expectedBytes = content.getBytes(StandardCharsets.UTF_8).length;

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("test-upload-bytes")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("sftp://testserver/remote/byte-test.txt"))
                    .build();

            TransferResult result = protocol.transfer(uploadRequest, context);

            assertNotNull(result);
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
            assertEquals(expectedBytes, result.getBytesTransferred(),
                    "Should transfer exact byte count from source file");
        }

        @Test
        @DisplayName("Upload should include timing information")
        void upload_includesTimingInfo() throws IOException, TransferException {
            Path localFile = tempDir.resolve("timing-test.txt");
            Files.writeString(localFile, "Timing test content");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("test-upload-timing")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("sftp://testserver/remote/timing-test.txt"))
                    .build();

            TransferResult result = protocol.transfer(uploadRequest, context);

            assertNotNull(result);
            assertTrue(result.getStartTime().isPresent(), "Start time should be present");
            assertTrue(result.getEndTime().isPresent(), "End time should be present");
            assertTrue(result.getEndTime().get().isAfter(result.getStartTime().get()) ||
                            result.getEndTime().get().equals(result.getStartTime().get()),
                    "End time should be at or after start time");
        }

        @Test
        @DisplayName("Upload should work with authentication in destination URI")
        void upload_worksWithAuthentication() throws IOException, TransferException {
            Path localFile = tempDir.resolve("auth-test.txt");
            Files.writeString(localFile, "Auth test content");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("test-upload-auth")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("sftp://user:pass@testserver/remote/auth-test.txt"))
                    .build();

            TransferResult result = protocol.transfer(uploadRequest, context);

            assertNotNull(result);
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus(),
                    "Upload with authentication should complete");
        }

        @Test
        @DisplayName("Upload should work with custom port in destination URI")
        void upload_worksWithCustomPort() throws IOException, TransferException {
            Path localFile = tempDir.resolve("port-test.txt");
            Files.writeString(localFile, "Custom port test content");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("test-upload-port")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("sftp://testserver:2222/remote/port-test.txt"))
                    .build();

            TransferResult result = protocol.transfer(uploadRequest, context);

            assertNotNull(result);
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus(),
                    "Upload with custom port should complete");
        }

        @Test
        @DisplayName("Upload should handle large file simulation")
        void upload_handlesLargeFile() throws IOException, TransferException {
            Path localFile = tempDir.resolve("large-file.txt");
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                content.append("Line ").append(i).append(": This is test content for large file simulation\n");
            }
            Files.writeString(localFile, content.toString());

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("test-upload-large")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("sftp://testserver/remote/large-file.txt"))
                    .build();

            TransferResult result = protocol.transfer(uploadRequest, context);

            assertNotNull(result);
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus(),
                    "Large file upload should complete");
            assertTrue(result.getBytesTransferred() > 50000,
                    "Large file should transfer substantial bytes");
        }
    }

    // Upload error handling tests have been moved to:
    // dev.mars.quorus.protocol.errorhandling.SftpTransferProtocolErrorHandlingTest
    // This keeps test output clean by isolating intentional failure tests.

    @Nested
    @DisplayName("Upload with Checksum Tests")
    class UploadChecksumTests {

        @Test
        @DisplayName("Upload should generate checksum when requested")
        void upload_generatesChecksumWhenRequested() throws IOException, TransferException {
            Path localFile = tempDir.resolve("checksum-test.txt");
            Files.writeString(localFile, "Content for checksum verification");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("test-upload-checksum")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("sftp://testserver/remote/checksum-test.txt"))
                    .expectedChecksum("expected-checksum-value")
                    .build();

            TransferResult result = protocol.transfer(uploadRequest, context);

            assertNotNull(result);
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
            assertNotNull(result.getActualChecksum(),
                    "Checksum should be generated when expected checksum is provided");
        }
    }

    @Nested
    @DisplayName("Upload Directory Handling Tests")
    class UploadDirectoryTests {

        @Test
        @DisplayName("Upload should handle nested destination paths")
        void upload_handlesNestedDestinationPath() throws IOException, TransferException {
            Path localFile = tempDir.resolve("nested-test.txt");
            Files.writeString(localFile, "Content for nested path test");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("test-upload-nested")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("sftp://testserver/deep/nested/path/to/file.txt"))
                    .build();

            TransferResult result = protocol.transfer(uploadRequest, context);

            assertNotNull(result);
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus(),
                    "Upload to nested path should complete");
        }

        @Test
        @DisplayName("Upload should handle root-level destination")
        void upload_handlesRootLevelDestination() throws IOException, TransferException {
            Path localFile = tempDir.resolve("root-test.txt");
            Files.writeString(localFile, "Content for root path test");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("test-upload-root")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("sftp://testserver/file.txt"))
                    .build();

            TransferResult result = protocol.transfer(uploadRequest, context);

            assertNotNull(result);
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus(),
                    "Upload to root level should complete");
        }
    }

    @Nested
    @DisplayName("Upload and Download Coexistence Tests")
    class UploadDownloadCoexistenceTests {

        @Test
        @DisplayName("Protocol should handle both upload and download in same session")
        void protocol_handlesBothUploadAndDownload() throws IOException, TransferException {
            // First, do a download
            TransferRequest downloadRequest = TransferRequest.builder()
                    .requestId("test-coexist-download")
                    .sourceUri(URI.create("sftp://testserver/remote/source.txt"))
                    .destinationPath(tempDir.resolve("downloaded.txt"))
                    .build();

            TransferResult downloadResult = protocol.transfer(downloadRequest, context);
            assertEquals(TransferStatus.COMPLETED, downloadResult.getFinalStatus());

            // Then, do an upload
            Path localFile = tempDir.resolve("upload-source.txt");
            Files.writeString(localFile, "Upload after download");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("test-coexist-upload")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("sftp://testserver/remote/uploaded.txt"))
                    .build();

            TransferResult uploadResult = protocol.transfer(uploadRequest, context);
            assertEquals(TransferStatus.COMPLETED, uploadResult.getFinalStatus(),
                    "Upload should work after download");
        }

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
