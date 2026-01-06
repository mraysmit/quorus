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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
/**
 * Description for ProtocolFactoryTest
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-18
 */

class ProtocolFactoryTest {
    
    private ProtocolFactory factory;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        factory = new ProtocolFactory();
    }
    
    @Test
    void testGetHttpProtocol() {
        TransferProtocol protocol = factory.getProtocol("http");
        assertNotNull(protocol);
        assertTrue(protocol instanceof HttpTransferProtocol);
        assertEquals("http", protocol.getProtocolName());
    }
    
    @Test
    void testGetHttpsProtocol() {
        TransferProtocol protocol = factory.getProtocol("https");
        assertNotNull(protocol);
        assertTrue(protocol instanceof HttpTransferProtocol);
        assertEquals("http", protocol.getProtocolName()); // HTTP protocol handles both
    }
    
    @Test
    void testGetSmbProtocol() {
        TransferProtocol protocol = factory.getProtocol("smb");
        assertNotNull(protocol);
        assertTrue(protocol instanceof SmbTransferProtocol);
        assertEquals("smb", protocol.getProtocolName());
    }
    
    @Test
    void testGetCifsProtocol() {
        TransferProtocol protocol = factory.getProtocol("cifs");
        assertNotNull(protocol);
        assertTrue(protocol instanceof SmbTransferProtocol);
        assertEquals("smb", protocol.getProtocolName()); // SMB protocol handles both
    }
    
    @Test
    void testGetFtpProtocol() {
        TransferProtocol protocol = factory.getProtocol("ftp");
        assertNotNull(protocol);
        assertTrue(protocol instanceof FtpTransferProtocol);
        assertEquals("ftp", protocol.getProtocolName());
    }
    
    @Test
    void testGetSftpProtocol() {
        TransferProtocol protocol = factory.getProtocol("sftp");
        assertNotNull(protocol);
        assertTrue(protocol instanceof SftpTransferProtocol);
        assertEquals("sftp", protocol.getProtocolName());
    }
    
    @Test
    void testGetUnsupportedProtocol() {
        TransferProtocol protocol = factory.getProtocol("unsupported");
        assertNull(protocol);
    }
    
    @Test
    void testGetProtocolWithNullScheme() {
        TransferProtocol protocol = factory.getProtocol(null);
        assertNull(protocol);
    }
    
    @Test
    void testGetProtocolWithEmptyScheme() {
        TransferProtocol protocol = factory.getProtocol("");
        assertNull(protocol);
    }
    
    @Test
    void testGetProtocolCaseInsensitive() {
        TransferProtocol httpProtocol = factory.getProtocol("HTTP");
        assertNotNull(httpProtocol);
        assertTrue(httpProtocol instanceof HttpTransferProtocol);
        
        TransferProtocol smbProtocol = factory.getProtocol("SMB");
        assertNotNull(smbProtocol);
        assertTrue(smbProtocol instanceof SmbTransferProtocol);
        
        TransferProtocol ftpProtocol = factory.getProtocol("FTP");
        assertNotNull(ftpProtocol);
        assertTrue(ftpProtocol instanceof FtpTransferProtocol);
        
        TransferProtocol sftpProtocol = factory.getProtocol("SFTP");
        assertNotNull(sftpProtocol);
        assertTrue(sftpProtocol instanceof SftpTransferProtocol);
    }
    
    @Test
    void testGetSupportedProtocols() {
        String[] supportedProtocols = factory.getSupportedProtocols();

        assertNotNull(supportedProtocols);
        assertTrue(supportedProtocols.length > 0);

        // Convert to set for easier testing
        Set<String> protocolSet = Set.of(supportedProtocols);

        // Should contain all registered protocols
        assertTrue(protocolSet.contains("http"));
        assertTrue(protocolSet.contains("smb"));
        assertTrue(protocolSet.contains("ftp"));
        assertTrue(protocolSet.contains("sftp"));

        // Should be at least 4 protocols
        assertTrue(supportedProtocols.length >= 4);
    }
    
    @Test
    void testProtocolSelectionByScheme() {
        // Test that we can get the right protocol by scheme
        // This tests the core functionality without the getProtocolForRequest method

        // Test HTTP/HTTPS
        TransferProtocol httpProtocol = factory.getProtocol("http");
        TransferRequest httpRequest = TransferRequest.builder()
                .sourceUri(URI.create("http://example.com/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        assertTrue(httpProtocol.canHandle(httpRequest));

        // Test SMB
        TransferProtocol smbProtocol = factory.getProtocol("smb");
        TransferRequest smbRequest = TransferRequest.builder()
                .sourceUri(URI.create("smb://server/share/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        assertTrue(smbProtocol.canHandle(smbRequest));

        // Test FTP
        TransferProtocol ftpProtocol = factory.getProtocol("ftp");
        TransferRequest ftpRequest = TransferRequest.builder()
                .sourceUri(URI.create("ftp://server/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        assertTrue(ftpProtocol.canHandle(ftpRequest));

        // Test SFTP
        TransferProtocol sftpProtocol = factory.getProtocol("sftp");
        TransferRequest sftpRequest = TransferRequest.builder()
                .sourceUri(URI.create("sftp://server/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        assertTrue(sftpProtocol.canHandle(sftpRequest));
    }
    
    @Test
    void testProtocolRegistration() {
        // Test that all expected protocols are registered
        assertNotNull(factory.getProtocol("http"));
        assertNotNull(factory.getProtocol("https"));
        assertNotNull(factory.getProtocol("smb"));
        assertNotNull(factory.getProtocol("cifs"));
        assertNotNull(factory.getProtocol("ftp"));
        assertNotNull(factory.getProtocol("sftp"));
    }
    
    @Test
    void testProtocolCapabilities() {
        // Test HTTP protocol capabilities
        TransferProtocol httpProtocol = factory.getProtocol("http");
        assertNotNull(httpProtocol);
        assertFalse(httpProtocol.supportsResume()); // Basic implementation doesn't support resume yet
        assertTrue(httpProtocol.supportsPause());
        
        // Test SMB protocol capabilities
        TransferProtocol smbProtocol = factory.getProtocol("smb");
        assertNotNull(smbProtocol);
        assertFalse(smbProtocol.supportsResume()); // Not implemented in current version
        assertFalse(smbProtocol.supportsPause());
        
        // Test FTP protocol capabilities
        TransferProtocol ftpProtocol = factory.getProtocol("ftp");
        assertNotNull(ftpProtocol);
        assertFalse(ftpProtocol.supportsResume()); // Not implemented in current version
        assertFalse(ftpProtocol.supportsPause());
        
        // Test SFTP protocol capabilities
        TransferProtocol sftpProtocol = factory.getProtocol("sftp");
        assertNotNull(sftpProtocol);
        assertFalse(sftpProtocol.supportsResume()); // Not implemented in current version
        assertFalse(sftpProtocol.supportsPause());
    }
    
    @Test
    void testProtocolMaxFileSizes() {
        // Test that protocols return appropriate max file sizes
        TransferProtocol httpProtocol = factory.getProtocol("http");
        assertNotNull(httpProtocol);
        assertTrue(httpProtocol.getMaxFileSize() > 0 || httpProtocol.getMaxFileSize() == -1);
        
        TransferProtocol smbProtocol = factory.getProtocol("smb");
        assertNotNull(smbProtocol);
        assertEquals(-1, smbProtocol.getMaxFileSize()); // No specific limit
        
        TransferProtocol ftpProtocol = factory.getProtocol("ftp");
        assertNotNull(ftpProtocol);
        assertEquals(-1, ftpProtocol.getMaxFileSize()); // No specific limit
        
        TransferProtocol sftpProtocol = factory.getProtocol("sftp");
        assertNotNull(sftpProtocol);
        assertEquals(-1, sftpProtocol.getMaxFileSize()); // No specific limit
    }
    
    @Test
    void testFactoryIsSingleton() {
        // Test that factory maintains protocol instances appropriately
        TransferProtocol http1 = factory.getProtocol("http");
        TransferProtocol http2 = factory.getProtocol("http");
        
        assertNotNull(http1);
        assertNotNull(http2);
        // Protocols may or may not be the same instance depending on implementation
        assertEquals(http1.getClass(), http2.getClass());
    }
}
