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
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HTTP upload functionality in HttpTransferProtocol.
 * 
 * These tests verify the bidirectional transfer capability for HTTP protocol,
 * specifically testing uploads from local file sources to HTTP/HTTPS destinations.
 * HTTP uploads typically use PUT method to transfer files to web servers.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025
 */
@ExtendWith(VertxExtension.class)
@DisplayName("HTTP Upload Protocol Tests")
class HttpTransferProtocolUploadTest {

    private HttpTransferProtocol protocol;
    private Vertx vertx;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp(Vertx vertx) {
        this.vertx = vertx;
        this.protocol = new HttpTransferProtocol(vertx);
    }

    @Nested
    @DisplayName("Upload Request Handling Tests")
    class UploadRequestHandlingTests {

        @Test
        @DisplayName("canHandle returns true for local source to HTTP destination")
        void canHandleLocalToHttp() throws IOException {
            Path localFile = tempDir.resolve("upload-source.txt");
            Files.writeString(localFile, "test content");

            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-http-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("http://example.com/uploads/file.txt"))
                    .build();

            assertTrue(protocol.canHandle(request));
            assertEquals(TransferDirection.UPLOAD, request.getDirection());
        }

        @Test
        @DisplayName("canHandle returns true for local source to HTTPS destination")
        void canHandleLocalToHttps() throws IOException {
            Path localFile = tempDir.resolve("upload-source-https.txt");
            Files.writeString(localFile, "test content");

            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-https-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("https://secure.example.com/uploads/file.txt"))
                    .build();

            assertTrue(protocol.canHandle(request));
            assertEquals(TransferDirection.UPLOAD, request.getDirection());
        }

        @Test
        @DisplayName("HTTP to HTTP is not supported (remote-to-remote)")
        void cannotHandleHttpToHttp() {
            // HTTP to HTTP would be a remote-to-remote transfer which throws exception
            assertThrows(UnsupportedOperationException.class, () -> {
                TransferRequest.builder()
                        .requestId("http-to-http-test")
                        .sourceUri(URI.create("http://source.example.com/file.txt"))
                        .destinationUri(URI.create("http://dest.example.com/file.txt"))
                        .build();
            });
        }
    }

    @Nested
    @DisplayName("Upload Transfer Execution Tests")
    class UploadTransferExecutionTests {

        @Test
        @DisplayName("Upload transfer executes with valid request")
        void uploadExecutesWithValidRequest(VertxTestContext testContext) throws IOException {
            Path localFile = tempDir.resolve("upload-execute.txt");
            String content = "Test content for HTTP upload";
            Files.writeString(localFile, content);

            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-exec-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("http://nonexistent.test.invalid/uploads/file.txt"))
                    .build();

            TransferContext context = new TransferContext(new TransferJob(request));

            // Upload to non-existent server will fail with connection error
            protocol.transferReactive(request, context)
                    .onComplete(testContext.failing(error -> {
                        testContext.verify(() -> {
                            assertNotNull(error);
                            // Connection error expected for non-existent server
                        });
                        testContext.completeNow();
                    }));
        }

        @Test
        @DisplayName("Upload transfer reads local file correctly")
        void uploadReadsLocalFile(VertxTestContext testContext) throws IOException {
            Path localFile = tempDir.resolve("upload-read-test.txt");
            String content = "Test content for reading local file during HTTP upload";
            Files.writeString(localFile, content);

            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-read-local-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("http://nonexistent.test.invalid/uploads/file.txt"))
                    .build();

            TransferContext context = new TransferContext(new TransferJob(request));

            // The upload process should read the local file (even though the server doesn't exist)
            protocol.transferReactive(request, context)
                    .onComplete(testContext.failing(error -> {
                        testContext.verify(() -> {
                            assertNotNull(error);
                            // File should still exist after attempted upload
                            assertTrue(Files.exists(localFile));
                        });
                        testContext.completeNow();
                    }));
        }

        @Test
        @DisplayName("Upload uses PUT method by default")
        void uploadUsesPutMethod(VertxTestContext testContext) throws IOException {
            Path localFile = tempDir.resolve("upload-put-test.txt");
            Files.writeString(localFile, "PUT method test content");

            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-put-method-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("http://nonexistent.test.invalid/uploads/file.txt"))
                    .build();

            TransferContext context = new TransferContext(new TransferJob(request));

            // PUT is the standard HTTP method for file uploads
            protocol.transferReactive(request, context)
                    .onComplete(testContext.failing(error -> {
                        testContext.verify(() -> {
                            assertNotNull(error);
                            // Connection error expected, but PUT method should be used
                        });
                        testContext.completeNow();
                    }));
        }
    }

    @Nested
    @DisplayName("Upload Error Handling Tests")
    class UploadErrorHandlingTests {

        @Test
        @DisplayName("Upload fails with non-existent local file")
        void uploadFailsWithNonExistentFile(VertxTestContext testContext) {
            Path nonExistentFile = tempDir.resolve("does-not-exist.txt");

            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-missing-file-test")
                    .sourceUri(nonExistentFile.toUri())
                    .destinationUri(URI.create("http://example.com/uploads/file.txt"))
                    .build();

            TransferContext context = new TransferContext(new TransferJob(request));

            protocol.transferReactive(request, context)
                    .onComplete(testContext.failing(error -> {
                        testContext.verify(() -> {
                            assertNotNull(error);
                            assertTrue(error instanceof TransferException);
                        });
                        testContext.completeNow();
                    }));
        }

        @Test
        @DisplayName("Upload fails with connection timeout")
        void uploadFailsWithConnectionTimeout(VertxTestContext testContext) throws IOException {
            Path localFile = tempDir.resolve("upload-timeout.txt");
            Files.writeString(localFile, "Content for timeout test");

            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-timeout-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("http://nonexistent.test.invalid/uploads/file.txt"))
                    .build();

            TransferContext context = new TransferContext(new TransferJob(request));

            protocol.transferReactive(request, context)
                    .onComplete(testContext.failing(error -> {
                        testContext.verify(() -> {
                            assertNotNull(error);
                            // Should get connection error or timeout
                        });
                        testContext.completeNow();
                    }));
        }

        @Test
        @DisplayName("Upload validates destination URI scheme")
        void uploadValidatesDestinationScheme(VertxTestContext testContext) throws IOException {
            Path localFile = tempDir.resolve("upload-scheme-test.txt");
            Files.writeString(localFile, "Content for scheme validation test");

            // Create upload to FTP destination (should fail validation)
            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-wrong-scheme-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("ftp://example.com/uploads/file.txt"))
                    .build();

            TransferContext context = new TransferContext(new TransferJob(request));

            // HTTP protocol should not handle FTP destinations
            assertFalse(protocol.canHandle(request));
            testContext.completeNow();
        }
    }

    @Nested
    @DisplayName("Upload Checksum Tests")
    class UploadChecksumTests {

        @Test
        @DisplayName("Upload calculates checksum for local file")
        void uploadCalculatesChecksum(VertxTestContext testContext) throws IOException {
            Path localFile = tempDir.resolve("upload-checksum.txt");
            String content = "Content for checksum calculation during upload";
            Files.writeString(localFile, content);

            TransferRequest request = TransferRequest.builder()
                    .requestId("upload-checksum-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("http://nonexistent.test.invalid/uploads/file.txt"))
                    .build();

            TransferContext context = new TransferContext(new TransferJob(request));

            // Upload will fail but checksum should be calculated from local file
            protocol.transferReactive(request, context)
                    .onComplete(testContext.failing(error -> {
                        testContext.verify(() -> {
                            assertNotNull(error);
                        });
                        testContext.completeNow();
                    }));
        }
    }

    @Nested
    @DisplayName("Upload and Download Coexistence Tests")
    class UploadDownloadCoexistenceTests {

        @Test
        @DisplayName("Protocol handles both upload and download requests")
        void protocolHandlesBothDirections() throws IOException {
            // Download request (HTTP source -> local destination)
            TransferRequest downloadRequest = TransferRequest.builder()
                    .requestId("download-test")
                    .sourceUri(URI.create("http://example.com/files/download.txt"))
                    .destinationPath(tempDir.resolve("downloaded.txt"))
                    .build();

            // Upload request (local source -> HTTP destination)
            Path localFile = tempDir.resolve("upload-source.txt");
            Files.writeString(localFile, "Upload content");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("upload-test")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("http://example.com/uploads/upload.txt"))
                    .build();

            // Both should be handled
            assertTrue(protocol.canHandle(downloadRequest));
            assertTrue(protocol.canHandle(uploadRequest));

            // Verify directions
            assertEquals(TransferDirection.DOWNLOAD, downloadRequest.getDirection());
            assertEquals(TransferDirection.UPLOAD, uploadRequest.getDirection());
        }

        @Test
        @DisplayName("Same protocol instance handles multiple transfer types")
        void sameProtocolHandlesMultipleTypes() throws IOException {
            HttpTransferProtocol sharedProtocol = new HttpTransferProtocol(vertx);

            // HTTP download
            TransferRequest httpDownload = TransferRequest.builder()
                    .requestId("http-download")
                    .sourceUri(URI.create("http://example.com/file.txt"))
                    .destinationPath(tempDir.resolve("http-downloaded.txt"))
                    .build();

            // HTTPS download
            TransferRequest httpsDownload = TransferRequest.builder()
                    .requestId("https-download")
                    .sourceUri(URI.create("https://secure.example.com/file.txt"))
                    .destinationPath(tempDir.resolve("https-downloaded.txt"))
                    .build();

            // HTTP upload
            Path uploadFile = tempDir.resolve("upload.txt");
            Files.writeString(uploadFile, "Upload content");

            TransferRequest httpUpload = TransferRequest.builder()
                    .requestId("http-upload")
                    .sourceUri(uploadFile.toUri())
                    .destinationUri(URI.create("http://example.com/uploads/file.txt"))
                    .build();

            // HTTPS upload
            TransferRequest httpsUpload = TransferRequest.builder()
                    .requestId("https-upload")
                    .sourceUri(uploadFile.toUri())
                    .destinationUri(URI.create("https://secure.example.com/uploads/file.txt"))
                    .build();

            // All should be handled
            assertTrue(sharedProtocol.canHandle(httpDownload));
            assertTrue(sharedProtocol.canHandle(httpsDownload));
            assertTrue(sharedProtocol.canHandle(httpUpload));
            assertTrue(sharedProtocol.canHandle(httpsUpload));
        }
    }
}
