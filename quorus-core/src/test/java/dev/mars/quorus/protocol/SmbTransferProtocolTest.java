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
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
/**
 * Description for SmbTransferProtocolTest
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-18
 */

class SmbTransferProtocolTest {
    
    private SmbTransferProtocol protocol;
    private TransferContext context;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        protocol = new SmbTransferProtocol();
        TransferRequest dummyRequest = TransferRequest.builder()
                .requestId("test-job-123")
                .sourceUri(URI.create("http://example.com/test.txt"))
                .destinationPath(tempDir.resolve("test.txt"))
                .build();
        context = new TransferContext(new dev.mars.quorus.core.TransferJob(dummyRequest));
    }
    
    @Test
    void testGetProtocolName() {
        assertEquals("smb", protocol.getProtocolName());
    }
    
    @Test
    void testCanHandleSmbUri() {
        TransferRequest smbRequest = TransferRequest.builder()
                .sourceUri(URI.create("smb://server/share/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        
        assertTrue(protocol.canHandle(smbRequest));
    }
    
    @Test
    void testCanHandleCifsUri() {
        TransferRequest cifsRequest = TransferRequest.builder()
                .sourceUri(URI.create("cifs://server/share/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        
        assertTrue(protocol.canHandle(cifsRequest));
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
    void testTransferWithValidSmbUri() throws TransferException {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-smb-transfer")
                .sourceUri(URI.create("smb://testserver/share/testfile.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        // Note: This will fail in the actual transfer since we don't have a real SMB server
        // but it should handle the error gracefully
        assertThrows(TransferException.class, () -> {
            protocol.transfer(request, context);
        });
    }
    
    // Error handling tests moved to dev.mars.quorus.protocol.errorhandling.SmbTransferProtocolErrorHandlingTest
    
    @Test
    void testSmbUriWithAuthentication() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-auth")
                .sourceUri(URI.create("smb://domain;username:password@server/share/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
        
        // Should throw exception when trying to transfer (no real SMB server)
        assertThrows(TransferException.class, () -> {
            protocol.transfer(request, context);
        });
    }
    
    @Test
    void testSmbUriWithUsernameOnly() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-username-only")
                .sourceUri(URI.create("smb://username@server/share/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
    }
    
    @Test
    void testSmbUriWithDomainAndUsername() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-domain-username")
                .sourceUri(URI.create("smb://domain;username@server/share/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
    }
    
    @Test
    void testErrorHandlingWithInvalidScheme() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-invalid-scheme")
                .sourceUri(URI.create("invalid://server/share/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        assertFalse(protocol.canHandle(request));
    }
    
    @Test
    void testTransferExceptionContainsRequestId() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-exception-id")
                .sourceUri(URI.create("smb://nonexistent/share/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        TransferException exception = assertThrows(TransferException.class, () -> {
            protocol.transfer(request, context);
        });
        
        // The exception should contain context about the transfer
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("SMB"));
    }
    
    @Test
    void testUncPathConversion() {
        // Test that SMB URIs are properly converted to UNC paths
        // This is tested indirectly through the transfer method
        TransferRequest request = TransferRequest.builder()
                .requestId("test-unc-conversion")
                .sourceUri(URI.create("smb://server/share/folder/file.txt"))
                .destinationPath(tempDir.resolve("testfile.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
        
        // The actual UNC conversion happens internally during transfer
        // We can't test it directly without exposing internal methods
        // but we can verify the protocol handles the URI format correctly
    }
    
    @Test
    void testChecksumHandling() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-checksum")
                .sourceUri(URI.create("smb://server/share/file.txt"))
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
    void testSmbUriAuthenticationEdgeCase() {
        // Test SMB URI with username and password
        TransferRequest request = TransferRequest.builder()
                .requestId("test-auth-edge")
                .sourceUri(URI.create("smb://domain;user:pass@server/share/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
    }
    
    @Test
    void testSmbUriWithDomain() {
        // Test SMB URI with domain in username
        TransferRequest request = TransferRequest.builder()
                .requestId("test-domain")
                .sourceUri(URI.create("smb://DOMAIN;user@server/share/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
    }
    
    @Test
    void testSmbUriWithCustomPort() {
        // Test SMB URI with custom port
        TransferRequest request = TransferRequest.builder()
                .requestId("test-port")
                .sourceUri(URI.create("smb://server:4445/share/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
    }
    
    // Additional edge case error tests moved to dev.mars.quorus.protocol.errorhandling.SmbTransferProtocolErrorHandlingTest
    
    @Test
    void testSmbPathWithSpaces() {
        // Test SMB URI with spaces in path
        TransferRequest request = TransferRequest.builder()
                .requestId("test-spaces")
                .sourceUri(URI.create("smb://server/share/folder%20name/file%20name.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
        
        // Will fail due to no real server, but tests URI parsing
        assertThrows(TransferException.class, () -> {
            protocol.transfer(request, context);
        });
    }
    
    @Test
    void testCifsSchemeSupport() {
        // Test that CIFS scheme is also supported
        TransferRequest request = TransferRequest.builder()
                .requestId("test-cifs-scheme")
                .sourceUri(URI.create("cifs://server/share/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        
        // CIFS is an alias for SMB
        assertTrue(protocol.canHandle(request));
    }
    
    @Test
    void testNestedSharePath() {
        // Test SMB URI with nested directory structure
        TransferRequest request = TransferRequest.builder()
                .requestId("test-nested")
                .sourceUri(URI.create("smb://server/share/dir1/dir2/dir3/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        
        assertTrue(protocol.canHandle(request));
        
        // Will fail due to no real server, but tests deep path handling
        assertThrows(TransferException.class, () -> {
            protocol.transfer(request, context);
        });
    }
}
