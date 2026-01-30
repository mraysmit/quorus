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

package dev.mars.quorus.protocol.errorhandling;

import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.protocol.SmbTransferProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Error handling tests for SmbTransferProtocol.
 * 
 * <p>These tests verify that the SMB/CIFS protocol correctly handles invalid inputs,
 * malformed URIs, and error conditions. Tests are separated from the main test
 * class to keep test output clean - these tests intentionally trigger errors.</p>
 * 
 * <p>All tests use request IDs with patterns like "test-missing-host" that allow
 * the protocol to suppress verbose error logging.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-18
 */
@Tag("negative")
@DisplayName("SMB Protocol Error Handling Tests")
class SmbTransferProtocolErrorHandlingTest extends ProtocolErrorHandlingTestBase {

    private SmbTransferProtocol protocol;

    @BeforeEach
    void setUp() {
        protocol = new SmbTransferProtocol();
    }

    @Nested
    @DisplayName("Download Error Handling")
    class DownloadErrorHandlingTests {

        @Test
        @DisplayName("Transfer should fail with invalid SMB URI (no path)")
        void transfer_failsWithInvalidSmbUri() {
            // URI.create("smb://") throws IllegalArgumentException due to missing authority
            assertThrows(IllegalArgumentException.class, () -> {
                URI.create("smb://");
            });

            // Test with a malformed but parseable URI that the protocol should reject
            TransferRequest request = TransferRequest.builder()
                    .requestId("test-invalid-smb")
                    .sourceUri(URI.create("smb://invalid-host-without-path"))
                    .destinationPath(tempDir.resolve("testfile.txt"))
                    .build();

            assertThrows(TransferException.class, () -> {
                protocol.transfer(request, context);
            });
        }

        @Test
        @DisplayName("Transfer should fail with SMB URI missing host")
        void transfer_failsWithSmbUriMissingHost() {
            TransferRequest request = TransferRequest.builder()
                    .requestId("test-missing-host")
                    .sourceUri(URI.create("smb:///share/file.txt"))  // Missing hostname
                    .destinationPath(tempDir.resolve("testfile.txt"))
                    .build();

            assertThrows(TransferException.class, () -> {
                protocol.transfer(request, context);
            });
        }

        @Test
        @DisplayName("Transfer should fail with SMB URI missing path")
        void transfer_failsWithSmbUriMissingPath() {
            TransferRequest request = TransferRequest.builder()
                    .requestId("test-missing-path")
                    .sourceUri(URI.create("smb://server"))  // Missing file path
                    .destinationPath(tempDir.resolve("testfile.txt"))
                    .build();

            assertThrows(TransferException.class, () -> {
                protocol.transfer(request, context);
            });
        }

        @Test
        @DisplayName("canHandle should return false for invalid scheme")
        void canHandle_returnsFalseForInvalidScheme() {
            TransferRequest request = TransferRequest.builder()
                    .requestId("test-invalid-scheme")
                    .sourceUri(URI.create("invalid://server/share/file.txt"))
                    .destinationPath(tempDir.resolve("testfile.txt"))
                    .build();

            assertFalse(protocol.canHandle(request));
        }

        @Test
        @DisplayName("Exception should contain protocol context information")
        void exception_containsProtocolContext() {
            TransferRequest request = TransferRequest.builder()
                    .requestId("test-exception-id")
                    .sourceUri(URI.create("smb://nonexistent/share/file.txt"))
                    .destinationPath(tempDir.resolve("testfile.txt"))
                    .build();

            TransferException exception = assertThrows(TransferException.class, () -> {
                protocol.transfer(request, context);
            });

            assertNotNull(exception.getMessage());
            assertTrue(exception.getMessage().contains("SMB"));
        }
    }

    @Nested
    @DisplayName("Upload Error Handling")
    class UploadErrorHandlingTests {

        @Test
        @DisplayName("Upload should fail when source file does not exist")
        void upload_failsWhenSourceNotExists() {
            Path nonExistentFile = tempDir.resolve("does-not-exist.txt");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("test-upload-missing-source")
                    .sourceUri(nonExistentFile.toUri())
                    .destinationUri(URI.create("smb://testserver/share/target.txt"))
                    .build();

            TransferException exception = assertThrows(TransferException.class, () -> {
                protocol.transfer(uploadRequest, context);
            }, "Should throw TransferException when source file doesn't exist");

            assertNotNull(exception.getMessage());
            String fullMessage = getFullExceptionMessage(exception);
            assertTrue(fullMessage.toLowerCase().contains("source") ||
                            fullMessage.toLowerCase().contains("file") ||
                            fullMessage.toLowerCase().contains("not found") ||
                            fullMessage.toLowerCase().contains("exist") ||
                            fullMessage.toLowerCase().contains("smb"),
                    "Error message should indicate source file or SMB issue. Got: " + fullMessage);
        }

        @Test
        @DisplayName("Upload should fail with empty destination host")
        void upload_failsWithEmptyDestinationHost() throws IOException {
            Path localFile = tempDir.resolve("test-file.txt");
            Files.writeString(localFile, "Test content");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("test-upload-no-host")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb:///share/path/file.txt"))
                    .build();

            TransferException exception = assertThrows(TransferException.class, () -> {
                protocol.transfer(uploadRequest, context);
            }, "Should throw TransferException when destination has no host");

            assertNotNull(exception.getMessage());
        }

        @Test
        @DisplayName("Upload should fail with empty destination path")
        void upload_failsWithEmptyDestinationPath() throws IOException {
            Path localFile = tempDir.resolve("test-file.txt");
            Files.writeString(localFile, "Test content");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("test-upload-no-path")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb://testserver"))
                    .build();

            TransferException exception = assertThrows(TransferException.class, () -> {
                protocol.transfer(uploadRequest, context);
            }, "Should throw TransferException when destination has no path");

            assertNotNull(exception.getMessage());
        }

        @Test
        @DisplayName("Upload exception should contain transfer ID from context")
        void upload_exceptionContainsTransferId() throws IOException {
            Path localFile = tempDir.resolve("test-file.txt");
            Files.writeString(localFile, "Test content");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("test-upload-exception-id")
                    .sourceUri(localFile.toUri())
                    .destinationUri(URI.create("smb:///no-host/share/file.txt"))
                    .build();

            TransferException exception = assertThrows(TransferException.class, () -> {
                protocol.transfer(uploadRequest, context);
            });

            assertNotNull(exception.getTransferId(),
                    "Exception should contain a transfer ID");
            assertEquals(context.getJobId(), exception.getTransferId(),
                    "Exception should contain the context job ID");
        }
    }
}
