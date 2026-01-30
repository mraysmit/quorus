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
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
/**
 * Description for SftpTransferProtocolTest
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-18
 */

class SftpTransferProtocolTest {
    
    private SftpTransferProtocol protocol;
    private TransferContext context;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        protocol = new SftpTransferProtocol();
        TransferRequest dummyRequest = TransferRequest.builder()
                .requestId("test-job-123")
                .sourceUri(URI.create("http://example.com/test.txt"))
                .destinationPath(tempDir.resolve("test.txt"))
                .build();
        context = new TransferContext(new dev.mars.quorus.core.TransferJob(dummyRequest));
    }
    
    @Test
    void testGetProtocolName() {
        assertEquals("sftp", protocol.getProtocolName());
    }
    
    @Test
    void testCanHandleSftpUri() {
        TransferRequest sftpRequest = TransferRequest.builder()
                .sourceUri(URI.create("sftp://server/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        
        assertTrue(protocol.canHandle(sftpRequest));
    }
    
    @Test
    void testCannotHandleFtpUri() {
        TransferRequest ftpRequest = TransferRequest.builder()
                .sourceUri(URI.create("ftp://server/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        
        assertFalse(protocol.canHandle(ftpRequest));
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
    void testTransferWithValidSftpUri() throws TransferException {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-sftp-transfer")
                .sourceUri(URI.create("sftp://testserver/path/testfile.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        // This should succeed as it's a simulation
        TransferResult result = protocol.transfer(request, context);
        
        assertNotNull(result);
        assertEquals("test-sftp-transfer", result.getRequestId());
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        assertTrue(result.getBytesTransferred() > 0);
        assertTrue(Files.exists(tempDir.resolve("testfile.txt")));
    }
    
    // Error handling tests moved to dev.mars.quorus.protocol.errorhandling.SftpTransferProtocolErrorHandlingTest
    
    @Test
    void testSftpUriWithAuthentication() throws TransferException {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-auth")
                .sourceUri(URI.create("sftp://username:password@server/path/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
        
        // Should succeed with simulation
        TransferResult result = protocol.transfer(request, context);
        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
    }
    
    @Test
    void testSftpUriWithUsernameOnly() throws TransferException {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-username-only")
                .sourceUri(URI.create("sftp://username@server/path/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
        
        // Should succeed with simulation
        TransferResult result = protocol.transfer(request, context);
        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
    }
    
    @Test
    void testSftpUriWithCustomPort() throws TransferException {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-custom-port")
                .sourceUri(URI.create("sftp://server:2222/path/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
        
        // Should succeed with simulation
        TransferResult result = protocol.transfer(request, context);
        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
    }
    
    @Test
    void testSftpUriWithDefaultPort() throws TransferException {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-default-port")
                .sourceUri(URI.create("sftp://server/path/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
        // Default port 22 should be used internally
        
        TransferResult result = protocol.transfer(request, context);
        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
    }
    
    // Additional error handling tests moved to dev.mars.quorus.protocol.errorhandling.SftpTransferProtocolErrorHandlingTest
    
    @Test
    void testChecksumHandling() throws TransferException {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-checksum")
                .sourceUri(URI.create("sftp://server/path/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .expectedChecksum("abc123")
                .build();
        
        assertTrue(protocol.canHandle(request));
        
        // Should handle checksum verification in simulation
        TransferResult result = protocol.transfer(request, context);
        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        assertNotNull(result.getActualChecksum());
    }
    
    @Test
    void testProgressTracking() throws TransferException {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-progress")
                .sourceUri(URI.create("sftp://server/path/largefile.txt"))
                .destinationPath(tempDir.resolve("largefile.txt"))
                .build();
        
        // Should track progress during simulation
        TransferResult result = protocol.transfer(request, context);
        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        assertTrue(result.getBytesTransferred() > 0);
    }
    
    @Test
    void testEncryptedTransfer() throws TransferException {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-encrypted")
                .sourceUri(URI.create("sftp://secure.server.com/confidential/data.txt"))
                .destinationPath(tempDir.resolve("data.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
        
        // Should handle encrypted transfer (simulation)
        TransferResult result = protocol.transfer(request, context);
        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
    }
    
    @Test
    void testKeyBasedAuthentication() throws TransferException {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-key-auth")
                .sourceUri(URI.create("sftp://keyuser@server/path/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
        
        // Should handle key-based authentication (simulation)
        TransferResult result = protocol.transfer(request, context);
        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
    }
    
    @Test
    void testTransferResultTiming() throws TransferException {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-timing")
                .sourceUri(URI.create("sftp://server/path/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        TransferResult result = protocol.transfer(request, context);
        
        assertNotNull(result);
        assertTrue(result.getStartTime().isPresent());
        assertTrue(result.getEndTime().isPresent());
        assertTrue(result.getEndTime().get().isAfter(result.getStartTime().get()) || 
                  result.getEndTime().get().equals(result.getStartTime().get()));
    }
    
    @Test
    void testSimulationCreatesFile() throws TransferException {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-file-creation")
                .sourceUri(URI.create("sftp://server/path/testfile.txt"))
                .destinationPath(tempDir.resolve("created-file.txt"))
                .build();
        
        assertFalse(Files.exists(tempDir.resolve("created-file.txt")));
        
        TransferResult result = protocol.transfer(request, context);
        
        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        assertTrue(Files.exists(tempDir.resolve("created-file.txt")));
        
        // Verify file content
        try {
            String content = Files.readString(tempDir.resolve("created-file.txt"));
            assertTrue(content.contains("SFTP Transfer Simulation"));
            assertTrue(content.contains("Quorus"));
        } catch (Exception e) {
            fail("Failed to read created file: " + e.getMessage());
        }
    }
    
    @Test
    void testSftpUriAuthenticationEdgeCase() {
        // Test SFTP URI with username and password
        TransferRequest request = TransferRequest.builder()
                .requestId("test-auth-edge")
                .sourceUri(URI.create("sftp://user:pass@server.com:22/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
    }
    
    @Test
    void testSftpUriCustomPortEdgeCase() {
        // Test SFTP URI with custom port
        TransferRequest request = TransferRequest.builder()
                .requestId("test-port-edge")
                .sourceUri(URI.create("sftp://server.com:2222/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
    }
    
    @Test
    void testSftpUriUsernameOnlyEdgeCase() {
        // Test SFTP URI with username but no password
        TransferRequest request = TransferRequest.builder()
                .requestId("test-username-edge")
                .sourceUri(URI.create("sftp://user@server.com/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
    }
    
    // Additional edge case error tests moved to dev.mars.quorus.protocol.errorhandling.SftpTransferProtocolErrorHandlingTest
    
    @Test
    void testIsTestHostnameDetection() {
        // Test that simulation mode is triggered for test hostnames
        TransferRequest request = TransferRequest.builder()
                .requestId("test-hostname-detection")
                .sourceUri(URI.create("sftp://test.example.com/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        
        // Should use simulation mode for test hostnames
        TransferResult result = assertDoesNotThrow(() -> protocol.transfer(request, context));
        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
    }
    
}

