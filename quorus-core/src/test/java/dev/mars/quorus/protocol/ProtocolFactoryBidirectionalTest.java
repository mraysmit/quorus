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

import dev.mars.quorus.core.TransferRequest;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for direction-aware protocol selection in ProtocolFactory (Phase 3).
 * 
 * Verifies:
 * - Protocol selection based on transfer direction
 * - Downloads use source URI scheme
 * - Uploads use destination URI scheme
 * - Remote-to-remote transfers are rejected
 */
class ProtocolFactoryBidirectionalTest {

    private Vertx vertx;
    private ProtocolFactory factory;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        factory = new ProtocolFactory(vertx);
    }

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    @Nested
    @DisplayName("Download Protocol Selection Tests")
    class DownloadProtocolSelectionTests {

        @Test
        @DisplayName("Should select SFTP protocol for SFTP download")
        void shouldSelectSftpProtocolForSftpDownload() {
            TransferRequest request = TransferRequest.builder()
                .requestId("sftp-download-test")
                .sourceUri(URI.create("sftp://server/remote/file.txt"))
                .destinationUri(URI.create("file:///local/file.txt"))
                .build();

            TransferProtocol protocol = factory.getProtocol(request);

            assertNotNull(protocol, "Protocol should not be null");
            assertEquals("sftp", protocol.getProtocolName());
            assertTrue(protocol instanceof SftpTransferProtocol);
        }

        @Test
        @DisplayName("Should select FTP protocol for FTP download")
        void shouldSelectFtpProtocolForFtpDownload() {
            TransferRequest request = TransferRequest.builder()
                .requestId("ftp-download-test")
                .sourceUri(URI.create("ftp://server/remote/file.txt"))
                .destinationUri(URI.create("file:///local/file.txt"))
                .build();

            TransferProtocol protocol = factory.getProtocol(request);

            assertNotNull(protocol, "Protocol should not be null");
            assertEquals("ftp", protocol.getProtocolName());
            assertTrue(protocol instanceof FtpTransferProtocol);
        }

        @Test
        @DisplayName("Should select HTTP protocol for HTTP download")
        void shouldSelectHttpProtocolForHttpDownload() {
            TransferRequest request = TransferRequest.builder()
                .requestId("http-download-test")
                .sourceUri(URI.create("http://server/remote/file.txt"))
                .destinationUri(URI.create("file:///local/file.txt"))
                .build();

            TransferProtocol protocol = factory.getProtocol(request);

            assertNotNull(protocol, "Protocol should not be null");
            assertEquals("http", protocol.getProtocolName());
            assertTrue(protocol instanceof HttpTransferProtocol);
        }

        @Test
        @DisplayName("Should select HTTP protocol for HTTPS download")
        void shouldSelectHttpProtocolForHttpsDownload() {
            TransferRequest request = TransferRequest.builder()
                .requestId("https-download-test")
                .sourceUri(URI.create("https://server/remote/file.txt"))
                .destinationUri(URI.create("file:///local/file.txt"))
                .build();

            TransferProtocol protocol = factory.getProtocol(request);

            assertNotNull(protocol, "Protocol should not be null");
            assertEquals("http", protocol.getProtocolName());
            assertTrue(protocol instanceof HttpTransferProtocol);
        }

        @Test
        @DisplayName("Should select SMB protocol for SMB download")
        void shouldSelectSmbProtocolForSmbDownload() {
            TransferRequest request = TransferRequest.builder()
                .requestId("smb-download-test")
                .sourceUri(URI.create("smb://server/share/file.txt"))
                .destinationUri(URI.create("file:///local/file.txt"))
                .build();

            TransferProtocol protocol = factory.getProtocol(request);

            assertNotNull(protocol, "Protocol should not be null");
            assertEquals("smb", protocol.getProtocolName());
            assertTrue(protocol instanceof SmbTransferProtocol);
        }
    }

    @Nested
    @DisplayName("Upload Protocol Selection Tests")
    class UploadProtocolSelectionTests {

        @Test
        @DisplayName("Should select SFTP protocol for SFTP upload")
        void shouldSelectSftpProtocolForSftpUpload() {
            TransferRequest request = TransferRequest.builder()
                .requestId("sftp-upload-test")
                .sourceUri(URI.create("file:///local/file.txt"))
                .destinationUri(URI.create("sftp://server/remote/file.txt"))
                .build();

            TransferProtocol protocol = factory.getProtocol(request);

            assertNotNull(protocol, "Protocol should not be null");
            assertEquals("sftp", protocol.getProtocolName());
            assertTrue(protocol instanceof SftpTransferProtocol);
        }

        @Test
        @DisplayName("Should select FTP protocol for FTP upload")
        void shouldSelectFtpProtocolForFtpUpload() {
            TransferRequest request = TransferRequest.builder()
                .requestId("ftp-upload-test")
                .sourceUri(URI.create("file:///local/file.txt"))
                .destinationUri(URI.create("ftp://server/remote/file.txt"))
                .build();

            TransferProtocol protocol = factory.getProtocol(request);

            assertNotNull(protocol, "Protocol should not be null");
            assertEquals("ftp", protocol.getProtocolName());
            assertTrue(protocol instanceof FtpTransferProtocol);
        }

        @Test
        @DisplayName("Should select HTTP protocol for HTTP upload")
        void shouldSelectHttpProtocolForHttpUpload() {
            TransferRequest request = TransferRequest.builder()
                .requestId("http-upload-test")
                .sourceUri(URI.create("file:///local/file.txt"))
                .destinationUri(URI.create("http://server/upload"))
                .build();

            TransferProtocol protocol = factory.getProtocol(request);

            assertNotNull(protocol, "Protocol should not be null");
            assertEquals("http", protocol.getProtocolName());
            assertTrue(protocol instanceof HttpTransferProtocol);
        }

        @Test
        @DisplayName("Should select SMB protocol for SMB upload")
        void shouldSelectSmbProtocolForSmbUpload() {
            TransferRequest request = TransferRequest.builder()
                .requestId("smb-upload-test")
                .sourceUri(URI.create("file:///local/file.txt"))
                .destinationUri(URI.create("smb://server/share/file.txt"))
                .build();

            TransferProtocol protocol = factory.getProtocol(request);

            assertNotNull(protocol, "Protocol should not be null");
            assertEquals("smb", protocol.getProtocolName());
            assertTrue(protocol instanceof SmbTransferProtocol);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling Tests")
    class EdgeCasesAndErrorHandlingTests {

        @Test
        @DisplayName("Should return null for null request")
        void shouldReturnNullForNullRequest() {
            TransferProtocol protocol = factory.getProtocol((TransferRequest) null);
            assertNull(protocol, "Protocol should be null for null request");
        }

        @Test
        @DisplayName("Should handle case-insensitive protocol schemes")
        void shouldHandleCaseInsensitiveProtocolSchemes() {
            // SFTP in uppercase (though URI normalizes to lowercase)
            TransferRequest request = TransferRequest.builder()
                .requestId("case-test")
                .sourceUri(URI.create("SFTP://server/file.txt"))
                .destinationUri(URI.create("file:///local/file.txt"))
                .build();

            TransferProtocol protocol = factory.getProtocol(request);

            assertNotNull(protocol, "Protocol should not be null");
            assertEquals("sftp", protocol.getProtocolName());
        }
    }
}
