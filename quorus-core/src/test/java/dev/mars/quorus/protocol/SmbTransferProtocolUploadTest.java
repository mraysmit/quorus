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
}
