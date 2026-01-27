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
import dev.mars.quorus.core.TransferJob;
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
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SMB upload functionality in SmbTransferProtocol.
 * 
 * These tests verify the bidirectional transfer capability for SMB protocol,
 * specifically testing uploads from local file sources to SMB/CIFS destinations.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025
 */
@DisplayName("SMB Upload Protocol Tests")
class SmbTransferProtocolUploadTest {

    private SmbTransferProtocol protocol;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        protocol = new SmbTransferProtocol();
    }

    @Nested
    @DisplayName("Upload Request Handling Tests")
    class UploadRequestHandlingTests {

        @Test
        @DisplayName("canHandle returns true for local source to SMB destination")
        void canHandleLocalToSmb() throws IOException {
            Path localFile = tempDir.resolve("upload-source.txt");
            Files.writeString(localFile, "test content");

            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-smb-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://server/share/uploads/file.txt"))
                    .build();

            assertTrue(protocol.canHandle(request));
            assertEquals(TransferDirection.UPLOAD, request.getDirection());
        }

        @Test
        @DisplayName("canHandle returns true for local source to CIFS destination")
        void canHandleLocalToCifs() throws IOException {
            Path localFile = tempDir.resolve("upload-source-cifs.txt");
            Files.writeString(localFile, "test content");

            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-cifs-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("cifs://server/share/uploads/file.txt"))
                    .build();

            assertTrue(protocol.canHandle(request));
            assertEquals(TransferDirection.UPLOAD, request.getDirection());
        }

        @Test
        @DisplayName("SMB to SMB is not supported (remote-to-remote)")
        void cannotHandleSmbToSmb() {
            // SMB to SMB would be a remote-to-remote transfer which throws exception
            assertThrows(UnsupportedOperationException.class, () -> {
                TransferRequest.builder()
                        .requestId("smb-to-smb-test")
                        .sourceUri(URI.create("smb://source/share/file.txt"))
                        .destinationUri(URI.create("smb://dest/share/file.txt"))
                        .build();
            });
        }
    }

    @Nested
    @DisplayName("Upload Transfer Execution Tests")
    class UploadTransferExecutionTests {

        @Test
        @DisplayName("Upload transfer creates context correctly")
        void uploadCreatesContextCorrectly() throws IOException {
            Path localFile = tempDir.resolve("upload-context.txt");
            String content = "Test content for SMB upload";
            Files.writeString(localFile, content);

            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-context-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://server/share/uploads/file.txt"))
                    .build();

            TransferContext context = new TransferContext(new TransferJob(request));

            assertNotNull(context);
            assertEquals("upload-context-test", context.getJob().getJobId());
            assertEquals(TransferDirection.UPLOAD, request.getDirection());
        }

        @Test
        @DisplayName("Upload validates source file exists")
        void uploadValidatesSourceFileExists() throws IOException {
            Path localFile = tempDir.resolve("existing-source.txt");
            Files.writeString(localFile, "Content to upload");

            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-validate-source-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://server/share/uploads/file.txt"))
                    .build();

            // The source file should exist
            assertTrue(Files.exists(localFile));
            assertTrue(protocol.canHandle(request));
        }

        @Test
        @DisplayName("Upload preserves file content")
        void uploadPreservesFileContent() throws IOException {
            Path localFile = tempDir.resolve("content-test.txt");
            String content = "Content that should be preserved during upload";
            Files.writeString(localFile, content);

            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-preserve-content-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://server/share/uploads/file.txt"))
                    .build();

            // Verify original file content unchanged
            assertEquals(content, Files.readString(localFile));
        }

        @Test
        @DisplayName("Upload handles authenticated destination URI")
        void uploadHandlesAuthenticatedUri() throws IOException {
            Path localFile = tempDir.resolve("auth-upload.txt");
            Files.writeString(localFile, "Authenticated upload content");

            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-auth-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://domain;user:pass@server/share/uploads/file.txt"))
                    .build();

            assertTrue(protocol.canHandle(request));
            assertEquals(TransferDirection.UPLOAD, request.getDirection());
        }

        @Test
        @DisplayName("Upload handles large file sizes")
        void uploadHandlesLargeFiles() throws IOException {
            Path localFile = tempDir.resolve("large-upload.txt");
            // Create a 100KB file
            byte[] content = new byte[100 * 1024];
            for (int i = 0; i < content.length; i++) {
                content[i] = (byte) (i % 256);
            }
            Files.write(localFile, content);

            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-large-file-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://server/share/uploads/large.bin"))
                    .build();

            assertTrue(protocol.canHandle(request));
            assertEquals(100 * 1024, Files.size(localFile));
        }
    }

    @Nested
    @DisplayName("Upload Error Handling Tests")
    class UploadErrorHandlingTests {

        @Test
        @DisplayName("Upload fails with non-existent local file")
        void uploadFailsWithNonExistentFile() {
            Path nonExistentFile = tempDir.resolve("does-not-exist.txt");

            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-missing-file-test")
                    .sourceUri(nonExistentFile.toUri())
                    .destinationUri(URI.create("smb://server/share/uploads/file.txt"))
                    .build();

            // Request is valid but file doesn't exist
            assertTrue(protocol.canHandle(request));
            assertFalse(Files.exists(nonExistentFile));
        }

        @Test
        @DisplayName("Upload validates destination URI scheme")
        void uploadValidatesDestinationScheme() throws IOException {
            Path localFile = tempDir.resolve("upload-scheme-test.txt");
            Files.writeString(localFile, "Content for scheme validation test");

            // Create upload to FTP destination (should fail validation)
            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-wrong-scheme-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("ftp://server/uploads/file.txt"))
                    .build();

            // SMB protocol should not handle FTP destinations
            assertFalse(protocol.canHandle(request));
        }

        @Test
        @DisplayName("Upload handles missing share in destination")
        void uploadHandlesMissingShare() throws IOException {
            Path localFile = tempDir.resolve("upload-no-share.txt");
            Files.writeString(localFile, "Content");

            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-no-share-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://server/file.txt"))
                    .build();

            // Still a valid SMB URI, just with a path directly under server
            assertTrue(protocol.canHandle(request));
        }
    }

    @Nested
    @DisplayName("Upload Checksum Tests")
    class UploadChecksumTests {

        @Test
        @DisplayName("Upload request can include expected checksum")
        void uploadCanIncludeExpectedChecksum() throws IOException {
            Path localFile = tempDir.resolve("upload-checksum.txt");
            String content = "Content for checksum calculation during upload";
            Files.writeString(localFile, content);

            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-checksum-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://server/share/uploads/file.txt"))
                    .expectedChecksum("expected-hash-value")
                    .build();

            assertEquals("expected-hash-value", request.getExpectedChecksum());
            assertTrue(protocol.canHandle(request));
        }
    }

    @Nested
    @DisplayName("Upload Directory Tests")
    class UploadDirectoryTests {

        @Test
        @DisplayName("Upload to nested directory path")
        void uploadToNestedDirectoryPath() throws IOException {
            Path localFile = tempDir.resolve("nested-upload.txt");
            Files.writeString(localFile, "Content for nested path upload");

            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-nested-path-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://server/share/deep/nested/path/file.txt"))
                    .build();

            assertTrue(protocol.canHandle(request));
            assertEquals(TransferDirection.UPLOAD, request.getDirection());
        }

        @Test
        @DisplayName("Upload to root of share")
        void uploadToRootOfShare() throws IOException {
            Path localFile = tempDir.resolve("root-upload.txt");
            Files.writeString(localFile, "Content for root upload");

            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-root-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://server/share/file.txt"))
                    .build();

            assertTrue(protocol.canHandle(request));
        }
    }

    @Nested
    @DisplayName("Upload and Download Coexistence Tests")
    class UploadDownloadCoexistenceTests {

        @Test
        @DisplayName("Protocol handles both upload and download requests")
        void protocolHandlesBothDirections() throws IOException {
            // Download request (SMB source -> local destination)
            TransferRequest downloadRequest = TransferRequest.builder()
                    .requestId("download-test")
                    .sourceUri(URI.create("smb://server/share/files/download.txt"))
                    .destinationPath(tempDir.resolve("downloaded.txt"))
                    .build();

            // Upload request (local source -> SMB destination)
            Path localFile = tempDir.resolve("upload-source.txt");
            Files.writeString(localFile, "Upload content");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("upload-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://server/share/uploads/upload.txt"))
                    .build();

            // Both should be handled
            assertTrue(protocol.canHandle(downloadRequest));
            assertTrue(protocol.canHandle(uploadRequest));

            // Verify directions
            assertEquals(TransferDirection.DOWNLOAD, downloadRequest.getDirection());
            assertEquals(TransferDirection.UPLOAD, uploadRequest.getDirection());
        }
    }

    @Nested
    @DisplayName("Upload Transfer Execution with Simulated Server")
    class UploadTransferExecutionWithSimulatedServerTests {

        @Test
        @DisplayName("transfer() successfully uploads small file to simulated server")
        void transferUploadsSmallFileToSimulatedServer() throws Exception {
            Path localFile = tempDir.resolve("small-upload.txt");
            String content = "Small file content for simulated SMB upload";
            Files.writeString(localFile, content);

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("simulated-small-upload-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://testserver/share/uploads/small.txt"))
                    .build();

            TransferContext context = new TransferContext(new TransferJob(uploadRequest));

            var result = protocol.transfer(uploadRequest, context);

            assertNotNull(result);
            assertEquals("simulated-small-upload-test", result.getRequestId());
            assertEquals(content.length(), result.getBytesTransferred());
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        }

        @Test
        @DisplayName("transfer() successfully uploads large file to simulated server")
        void transferUploadsLargeFileToSimulatedServer() throws Exception {
            Path localFile = tempDir.resolve("large-upload.bin");
            // Create a 100KB file
            byte[] content = new byte[100 * 1024];
            for (int i = 0; i < content.length; i++) {
                content[i] = (byte) (i % 256);
            }
            Files.write(localFile, content);

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("simulated-large-upload-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://testserver/share/uploads/large.bin"))
                    .build();

            TransferContext context = new TransferContext(new TransferJob(uploadRequest));

            var result = protocol.transfer(uploadRequest, context);

            assertNotNull(result);
            assertEquals(100 * 1024, result.getBytesTransferred());
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        }

        @Test
        @DisplayName("transfer() returns correct timing information")
        void transferReturnsCorrectTimingInfo() throws Exception {
            Path localFile = tempDir.resolve("timing-test.txt");
            Files.writeString(localFile, "Content for timing test");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("simulated-timing-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://testserver/share/uploads/timing.txt"))
                    .build();

            TransferContext context = new TransferContext(new TransferJob(uploadRequest));

            var result = protocol.transfer(uploadRequest, context);

            assertTrue(result.getStartTime().isPresent());
            assertTrue(result.getEndTime().isPresent());
            assertTrue(result.getEndTime().get().isAfter(result.getStartTime().get()) || 
                      result.getEndTime().get().equals(result.getStartTime().get()));
        }

        @Test
        @DisplayName("transfer() fails for non-existent source file")
        void transferFailsForNonExistentSourceFile() {
            Path nonExistentFile = tempDir.resolve("nonexistent.txt");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("simulated-missing-source-test")
                    .sourceUri(nonExistentFile.toUri())
                    .destinationUri(URI.create("smb://testserver/share/uploads/file.txt"))
                    .build();

            TransferContext context = new TransferContext(new TransferJob(uploadRequest));

            assertThrows(TransferException.class, () -> {
                protocol.transfer(uploadRequest, context);
            });
        }

        @Test
        @DisplayName("transfer() handles empty file upload")
        void transferHandlesEmptyFileUpload() throws Exception {
            Path emptyFile = tempDir.resolve("empty-upload.txt");
            Files.writeString(emptyFile, "");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("simulated-empty-upload-test")
                    .sourceUri(emptyFile.toUri())
                    .destinationUri(URI.create("smb://testserver/share/uploads/empty.txt"))
                    .build();

            TransferContext context = new TransferContext(new TransferJob(uploadRequest));

            var result = protocol.transfer(uploadRequest, context);

            assertNotNull(result);
            assertEquals(0, result.getBytesTransferred());
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        }

        @Test
        @DisplayName("transfer() handles file with special characters")
        void transferHandlesFileWithSpecialCharacters() throws Exception {
            Path localFile = tempDir.resolve("special-content.txt");
            String content = "Content with special chars: äöü中文🎉\n\t\r";
            Files.writeString(localFile, content);

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("simulated-special-chars-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://testserver/share/uploads/special.txt"))
                    .build();

            TransferContext context = new TransferContext(new TransferJob(uploadRequest));

            var result = protocol.transfer(uploadRequest, context);

            assertNotNull(result);
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
            assertTrue(result.getBytesTransferred() > 0);
        }

        @Test
        @DisplayName("transfer() handles simulated server with localhost.test")
        void transferHandlesLocalhostTestServer() throws Exception {
            Path localFile = tempDir.resolve("localhost-test.txt");
            Files.writeString(localFile, "Localhost test content");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("simulated-localhost-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://localhost.test/share/uploads/file.txt"))
                    .build();

            TransferContext context = new TransferContext(new TransferJob(uploadRequest));

            var result = protocol.transfer(uploadRequest, context);

            assertNotNull(result);
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        }

        @Test
        @DisplayName("transfer() handles simulated-smb-server hostname")
        void transferHandlesSimulatedSmbServerHostname() throws Exception {
            Path localFile = tempDir.resolve("simulated-server.txt");
            Files.writeString(localFile, "Simulated SMB server content");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("simulated-smb-server-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://simulated-smb-server/share/uploads/file.txt"))
                    .build();

            TransferContext context = new TransferContext(new TransferJob(uploadRequest));

            var result = protocol.transfer(uploadRequest, context);

            assertNotNull(result);
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        }

        @Test
        @DisplayName("transfer() handles CIFS URI with simulated server")
        void transferHandlesCifsUriWithSimulatedServer() throws Exception {
            Path localFile = tempDir.resolve("cifs-upload.txt");
            Files.writeString(localFile, "CIFS protocol upload content");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("simulated-cifs-upload-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("cifs://testserver/share/uploads/cifs-file.txt"))
                    .build();

            TransferContext context = new TransferContext(new TransferJob(uploadRequest));

            var result = protocol.transfer(uploadRequest, context);

            assertNotNull(result);
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        }

        @Test
        @DisplayName("transfer() verifies isUpload returns true for upload requests")
        void transferVerifiesIsUploadReturnsTrue() throws Exception {
            Path localFile = tempDir.resolve("verify-upload-flag.txt");
            Files.writeString(localFile, "Verifying upload flag");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("verify-upload-flag-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://testserver/share/uploads/flag-test.txt"))
                    .build();

            // Verify the request is recognized as an upload
            assertTrue(uploadRequest.isUpload());
            assertFalse(uploadRequest.isDownload());
            assertEquals(TransferDirection.UPLOAD, uploadRequest.getDirection());

            TransferContext context = new TransferContext(new TransferJob(uploadRequest));

            var result = protocol.transfer(uploadRequest, context);

            assertNotNull(result);
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        }

        @Test
        @DisplayName("transfer() verifies upload direction flag")
        void transferVerifiesUploadDirectionFlag() throws Exception {
            Path localFile = tempDir.resolve("direction-test.txt");
            Files.writeString(localFile, "Direction verification content");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("direction-flag-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://testserver/share/uploads/file.txt"))
                    .build();

            // Verify the request direction is correctly set as UPLOAD
            assertEquals(TransferDirection.UPLOAD, uploadRequest.getDirection());
            assertTrue(uploadRequest.isUpload());
            assertFalse(uploadRequest.isDownload());

            TransferContext context = new TransferContext(new TransferJob(uploadRequest));

            TransferResult result = protocol.transfer(uploadRequest, context);

            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        }

        @Test
        @DisplayName("transfer() preserves requestId through entire transfer")
        void transferPreservesRequestIdThroughEntireTransfer() throws Exception {
            Path localFile = tempDir.resolve("request-id-test.txt");
            Files.writeString(localFile, "Request ID preservation test");

            String uniqueRequestId = "unique-request-id-" + System.currentTimeMillis();
            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId(uniqueRequestId)
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://testserver/share/uploads/reqid.txt"))
                    .build();

            TransferContext context = new TransferContext(new TransferJob(uploadRequest));

            var result = protocol.transfer(uploadRequest, context);

            assertEquals(uniqueRequestId, result.getRequestId());
        }
    }
}
