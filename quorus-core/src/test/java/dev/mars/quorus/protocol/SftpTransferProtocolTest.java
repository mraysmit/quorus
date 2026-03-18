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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
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
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        protocol = new SftpTransferProtocol();
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
    
    // Error handling tests moved to dev.mars.quorus.protocol.errorhandling.SftpTransferProtocolErrorHandlingTest
    
    // Additional error handling tests moved to dev.mars.quorus.protocol.errorhandling.SftpTransferProtocolErrorHandlingTest
    
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
    
}
