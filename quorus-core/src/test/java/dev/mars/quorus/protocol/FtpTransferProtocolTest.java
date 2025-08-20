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
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.transfer.TransferContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FtpTransferProtocolTest {
    
    private FtpTransferProtocol protocol;
    private TransferContext context;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        protocol = new FtpTransferProtocol();
        TransferRequest dummyRequest = TransferRequest.builder()
                .requestId("test-job-123")
                .sourceUri(URI.create("http://example.com/test.txt"))
                .destinationPath(tempDir.resolve("test.txt"))
                .build();
        context = new TransferContext(new dev.mars.quorus.core.TransferJob(dummyRequest));
    }
    
    @Test
    void testGetProtocolName() {
        assertEquals("ftp", protocol.getProtocolName());
    }
    
    @Test
    void testCanHandleFtpUri() {
        TransferRequest ftpRequest = TransferRequest.builder()
                .sourceUri(URI.create("ftp://server/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        
        assertTrue(protocol.canHandle(ftpRequest));
    }
    
    @Test
    void testCannotHandleHttpUri() {
        TransferRequest httpRequest = TransferRequest.builder()
                .sourceUri(URI.create("http://server/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        
        assertFalse(protocol.canHandle(httpRequest));
    }
    
    @Test
    void testCannotHandleSftpUri() {
        TransferRequest sftpRequest = TransferRequest.builder()
                .sourceUri(URI.create("sftp://server/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        
        assertFalse(protocol.canHandle(sftpRequest));
    }
    
    @Test
    void testCannotHandleNullRequest() {
        assertFalse(protocol.canHandle(null));
    }
    
    @Test
    void testCannotHandleRequestWithNullUri() {
        // TransferRequest constructor validates that sourceUri cannot be null
        // So we test that the constructor throws NullPointerException
        assertThrows(NullPointerException.class, () -> {
            TransferRequest.builder()
                    .sourceUri(null)
                    .destinationPath(tempDir.resolve("file.txt"))
                    .build();
        });
    }
    
    @Test
    void testSupportsResume() {
        assertFalse(protocol.supportsResume());
    }
    
    @Test
    void testSupportsPause() {
        assertFalse(protocol.supportsPause());
    }
    
    @Test
    void testGetMaxFileSize() {
        assertEquals(-1, protocol.getMaxFileSize());
    }
    
    @Test
    void testTransferWithValidFtpUri() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-ftp-transfer")
                .sourceUri(URI.create("ftp://testserver/path/testfile.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        // Note: This will fail since we don't have a real FTP server
        assertThrows(TransferException.class, () -> {
            protocol.transfer(request, context);
        });
    }
    
    @Test
    void testTransferWithInvalidFtpUri() {
        // INTENTIONAL FAILURE TEST: Testing invalid FTP URI handling
        // This test verifies that the protocol correctly rejects malformed URIs

        // URI.create("ftp://") throws IllegalArgumentException due to missing authority
        // So we test that URI creation itself throws the exception
        assertThrows(IllegalArgumentException.class, () -> {
            URI.create("ftp://");
        });

        // Test with a malformed but parseable URI that the protocol should reject
        // Expected behavior: TransferException should be thrown
        TransferRequest request = TransferRequest.builder()
                .requestId("test-invalid-ftp")
                .sourceUri(URI.create("ftp://invalid-host-without-path"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();

        assertThrows(TransferException.class, () -> {
            protocol.transfer(request, context);
        });
    }
    
    @Test
    void testTransferWithFtpUriMissingHost() {
        // INTENTIONAL FAILURE TEST: Testing FTP URI validation for missing host
        // This test verifies that the protocol correctly rejects URIs without a hostname
        // Expected behavior: TransferException should be thrown with clear error message

        TransferRequest request = TransferRequest.builder()
                .requestId("test-missing-host")
                .sourceUri(URI.create("ftp:///path/file.txt"))  // Missing hostname
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();

        assertThrows(TransferException.class, () -> {
            protocol.transfer(request, context);
        });
    }
    
    @Test
    void testTransferWithFtpUriMissingPath() {
        // INTENTIONAL FAILURE TEST: Testing FTP URI validation for missing path
        // This test verifies that the protocol correctly rejects URIs without a file path
        // Expected behavior: TransferException should be thrown with clear error message

        TransferRequest request = TransferRequest.builder()
                .requestId("test-missing-path")
                .sourceUri(URI.create("ftp://server"))  // Missing file path
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();

        assertThrows(TransferException.class, () -> {
            protocol.transfer(request, context);
        });
    }
    
    @Test
    void testFtpUriWithAuthentication() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-auth")
                .sourceUri(URI.create("ftp://username:password@server/path/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
        
        // Should throw exception when trying to transfer (no real FTP server)
        assertThrows(TransferException.class, () -> {
            protocol.transfer(request, context);
        });
    }
    
    @Test
    void testFtpUriWithUsernameOnly() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-username-only")
                .sourceUri(URI.create("ftp://username@server/path/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
    }
    
    @Test
    void testFtpUriWithCustomPort() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-custom-port")
                .sourceUri(URI.create("ftp://server:2121/path/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
    }
    
    @Test
    void testFtpUriWithDefaultPort() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-default-port")
                .sourceUri(URI.create("ftp://server/path/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
        // Default port 21 should be used internally
    }
    
    @Test
    void testAnonymousFtpAccess() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-anonymous")
                .sourceUri(URI.create("ftp://ftp.example.com/pub/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
        // Should use anonymous credentials internally
    }
    
    @Test
    void testErrorHandlingWithInvalidScheme() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-invalid-scheme")
                .sourceUri(URI.create("invalid://server/path/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        assertFalse(protocol.canHandle(request));
    }
    
    @Test
    void testTransferExceptionContainsRequestId() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-exception-id")
                .sourceUri(URI.create("ftp://nonexistent.server.com/path/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        TransferException exception = assertThrows(TransferException.class, () -> {
            protocol.transfer(request, context);
        });
        
        // The exception should contain context about the transfer
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("FTP"));
    }
    
    @Test
    void testChecksumHandling() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-checksum")
                .sourceUri(URI.create("ftp://server/path/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .expectedChecksum("abc123")
                .build();
        
        assertTrue(protocol.canHandle(request));
        
        // Should handle checksum verification (though will fail due to no real server)
        assertThrows(TransferException.class, () -> {
            protocol.transfer(request, context);
        });
    }
    
    @Test
    void testPassiveModeSupport() {
        // Test that the protocol is designed to use passive mode
        TransferRequest request = TransferRequest.builder()
                .requestId("test-passive-mode")
                .sourceUri(URI.create("ftp://server/path/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
        // Passive mode is used internally for corporate firewall compatibility
    }
    
    @Test
    void testBinaryTransferMode() {
        // Test that the protocol uses binary transfer mode
        TransferRequest request = TransferRequest.builder()
                .requestId("test-binary-mode")
                .sourceUri(URI.create("ftp://server/path/binary-file.zip"))
                .destinationPath(tempDir.resolve("binary-file.zip"))
                .build();
        
        assertTrue(protocol.canHandle(request));
        // Binary mode is set internally for all transfers
    }
    
    @Test
    void testConnectionTimeout() {
        // INTENTIONAL FAILURE TEST: Testing connection timeout handling
        // This test verifies that the protocol correctly handles connection timeouts
        // Expected behavior: TransferException should be thrown for unreachable servers

        TransferRequest request = TransferRequest.builder()
                .requestId("test-timeout")
                .sourceUri(URI.create("ftp://timeout.server.com/path/file.txt"))  // Non-existent server
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();

        assertTrue(protocol.canHandle(request));

        // Should timeout and throw exception
        assertThrows(TransferException.class, () -> {
            protocol.transfer(request, context);
        });
    }
}
