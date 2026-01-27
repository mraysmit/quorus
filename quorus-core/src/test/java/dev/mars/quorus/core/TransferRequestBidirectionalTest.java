package dev.mars.quorus.core;

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Unit tests for bidirectional transfer functionality in TransferRequest.
 * 
 * <p>Tests cover:</p>
 * <ul>
 *   <li>TransferDirection enum functionality</li>
 *   <li>Direction detection methods (isDownload, isUpload, isRemoteToRemote)</li>
 *   <li>getDirection() method with all URI combinations</li>
 *   <li>Builder methods (destinationPath and destinationUri)</li>
 *   <li>Validation logic for URI combinations</li>
 * </ul>
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2026-01-27
 */
@DisplayName("TransferRequest Bidirectional Support Tests")
class TransferRequestBidirectionalTest {
    
    // ========================================================================
    // TransferDirection Enum Values and Logic
    // ========================================================================
    
    @Test
    @DisplayName("TransferDirection enum has correct values")
    void testTransferDirectionEnumValues() {
        assertEquals(3, TransferDirection.values().length);
        assertNotNull(TransferDirection.DOWNLOAD);
        assertNotNull(TransferDirection.UPLOAD);
        assertNotNull(TransferDirection.REMOTE_TO_REMOTE);
    }
    
    @Test
    @DisplayName("TransferDirection.DOWNLOAD has correct properties")
    void testDownloadDirectionProperties() {
        TransferDirection direction = TransferDirection.DOWNLOAD;
        
        assertTrue(direction.isDownload());
        assertFalse(direction.isUpload());
        assertFalse(direction.isRemoteToRemote());
        assertEquals("Remote -> Local", direction.getDescription());
        assertTrue(direction.toString().contains("DOWNLOAD"));
    }
    
    @Test
    @DisplayName("TransferDirection.UPLOAD has correct properties")
    void testUploadDirectionProperties() {
        TransferDirection direction = TransferDirection.UPLOAD;
        
        assertFalse(direction.isDownload());
        assertTrue(direction.isUpload());
        assertFalse(direction.isRemoteToRemote());
        assertEquals("Local -> Remote", direction.getDescription());
        assertTrue(direction.toString().contains("UPLOAD"));
    }
    
    @Test
    @DisplayName("TransferDirection.REMOTE_TO_REMOTE has correct properties")
    void testRemoteToRemoteDirectionProperties() {
        TransferDirection direction = TransferDirection.REMOTE_TO_REMOTE;
        
        assertFalse(direction.isDownload());
        assertFalse(direction.isUpload());
        assertTrue(direction.isRemoteToRemote());
        assertEquals("Remote -> Remote", direction.getDescription());
        assertTrue(direction.toString().contains("REMOTE_TO_REMOTE"));
    }
    
    // ========================================================================
    // isDownload(), isUpload(), isRemoteToRemote() Methods
    // ========================================================================
    
    @Test
    @DisplayName("isDownload() returns true for remote source to local destination")
    void testIsDownloadForRemoteToLocal() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("sftp://server/file.dat"))
                .destinationUri(URI.create("file:///tmp/file.dat"))
                .build();
        
        assertTrue(request.isDownload());
        assertFalse(request.isUpload());
        assertFalse(request.isRemoteToRemote());
    }
    
    @Test
    @DisplayName("isUpload() returns true for local source to remote destination")
    void testIsUploadForLocalToRemote() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("file:///tmp/file.dat"))
                .destinationUri(URI.create("sftp://server/file.dat"))
                .build();
        
        assertFalse(request.isDownload());
        assertTrue(request.isUpload());
        assertFalse(request.isRemoteToRemote());
    }
    
    @Test
    @DisplayName("Remote-to-remote transfers are not yet supported")
    void testIsRemoteToRemoteForRemoteToRemote() {
        assertThrows(UnsupportedOperationException.class, () ->
                TransferRequest.builder()
                        .sourceUri(URI.create("sftp://server1/file.dat"))
                        .destinationUri(URI.create("ftp://server2/file.dat"))
                        .build()
        );
    }
    
    // ========================================================================
    // getDirection() with All URI Scheme Combinations
    // ========================================================================
    
    @Test
    @DisplayName("getDirection() returns DOWNLOAD for HTTP to file://")
    void testGetDirectionHttpDownload() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("http://example.com/file.dat"))
                .destinationUri(URI.create("file:///tmp/file.dat"))
                .build();
        
        assertEquals(TransferDirection.DOWNLOAD, request.getDirection());
    }
    
    @Test
    @DisplayName("getDirection() returns DOWNLOAD for HTTPS to file://")
    void testGetDirectionHttpsDownload() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("https://example.com/file.dat"))
                .destinationUri(URI.create("file:///tmp/file.dat"))
                .build();
        
        assertEquals(TransferDirection.DOWNLOAD, request.getDirection());
    }
    
    @Test
    @DisplayName("getDirection() returns DOWNLOAD for FTP to file://")
    void testGetDirectionFtpDownload() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("ftp://server/file.dat"))
                .destinationUri(URI.create("file:///tmp/file.dat"))
                .build();
        
        assertEquals(TransferDirection.DOWNLOAD, request.getDirection());
    }
    
    @Test
    @DisplayName("getDirection() returns DOWNLOAD for SFTP to file://")
    void testGetDirectionSftpDownload() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("sftp://server/file.dat"))
                .destinationUri(URI.create("file:///tmp/file.dat"))
                .build();
        
        assertEquals(TransferDirection.DOWNLOAD, request.getDirection());
    }
    
    @Test
    @DisplayName("getDirection() returns DOWNLOAD for SMB to file://")
    void testGetDirectionSmbDownload() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("smb://server/share/file.dat"))
                .destinationUri(URI.create("file:///tmp/file.dat"))
                .build();
        
        assertEquals(TransferDirection.DOWNLOAD, request.getDirection());
    }
    
    @Test
    @DisplayName("getDirection() returns UPLOAD for file:// to SFTP")
    void testGetDirectionSftpUpload() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("file:///tmp/file.dat"))
                .destinationUri(URI.create("sftp://server/file.dat"))
                .build();
        
        assertEquals(TransferDirection.UPLOAD, request.getDirection());
    }
    
    @Test
    @DisplayName("getDirection() returns UPLOAD for file:// to FTP")
    void testGetDirectionFtpUpload() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("file:///tmp/file.dat"))
                .destinationUri(URI.create("ftp://server/file.dat"))
                .build();
        
        assertEquals(TransferDirection.UPLOAD, request.getDirection());
    }
    
    @Test
    @DisplayName("getDirection() returns UPLOAD for file:// to HTTP")
    void testGetDirectionHttpUpload() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("file:///tmp/file.dat"))
                .destinationUri(URI.create("http://server/upload"))
                .build();
        
        assertEquals(TransferDirection.UPLOAD, request.getDirection());
    }
    
    @Test
    @DisplayName("getDirection() for REMOTE_TO_REMOTE is not yet supported")
    void testGetDirectionRemoteToRemote() {
        assertThrows(UnsupportedOperationException.class, () ->
                TransferRequest.builder()
                        .sourceUri(URI.create("sftp://server1/file.dat"))
                        .destinationUri(URI.create("ftp://server2/file.dat"))
                        .build()
        );
    }
    
    // ========================================================================
    // Builder Methods
    // ========================================================================
    
    @Test
    @DisplayName("Builder accepts destinationPath")
    void testBuilderWithDestinationPath() {
        Path expectedPath = Paths.get("/tmp/file.dat");
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("sftp://server/file.dat"))
                .destinationPath(expectedPath)
                .build();
        
        assertNotNull(request);
        assertEquals(expectedPath.toAbsolutePath(), request.getDestinationPath());
        assertEquals("file", request.getDestinationUri().getScheme());
        assertTrue(request.isDownload());
    }
    
    @Test
    @DisplayName("Builder accepts destinationUri")
    void testBuilderWithDestinationUri() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("file:///tmp/file.dat"))
                .destinationUri(URI.create("sftp://server/file.dat"))
                .build();
        
        assertNotNull(request);
        assertEquals(URI.create("sftp://server/file.dat"), request.getDestinationUri());
        assertTrue(request.isUpload());
    }
    
    @Test
    @DisplayName("Both builder methods work (destinationPath and destinationUri)")
    void testBuilderBothMethodsWork() {
        TransferRequest request1 = TransferRequest.builder()
                .sourceUri(URI.create("http://server/file.dat"))
                .destinationPath(Paths.get("/tmp/file.dat"))
                .build();
        
        TransferRequest request2 = TransferRequest.builder()
                .sourceUri(URI.create("http://server/file.dat"))
                .destinationUri(URI.create("file:///tmp/file.dat"))
                .build();
        
        assertEquals(request1.getDirection(), request2.getDirection());
        assertEquals(TransferDirection.DOWNLOAD, request1.getDirection());
    }
    
    @Test
    @DisplayName("getDestinationPath() works when destination is file://")
    void testGetDestinationPathWithFileScheme() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("sftp://server/file.dat"))
                .destinationUri(URI.create("file:///tmp/file.dat"))
                .build();
        
        assertEquals(Paths.get("/tmp/file.dat"), request.getDestinationPath());
    }
    
    @Test
    @DisplayName("getDestinationPath() throws exception when destination is remote")
    void testGetDestinationPathThrowsForRemoteDestination() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("file:///tmp/file.dat"))
                .destinationUri(URI.create("sftp://server/file.dat"))
                .build();
        
        assertThrows(UnsupportedOperationException.class, request::getDestinationPath);
    }
    
    // ========================================================================
    // Validation Logic
    // ========================================================================
    
    @Test
    @DisplayName("Validation accepts download (remote to file://)")
    void testValidationAcceptsDownload() {
        assertDoesNotThrow(() ->
                TransferRequest.builder()
                        .sourceUri(URI.create("sftp://server/file.dat"))
                        .destinationUri(URI.create("file:///tmp/file.dat"))
                        .build()
        );
    }
    
    @Test
    @DisplayName("Validation accepts upload (file:// to remote)")
    void testValidationAcceptsUpload() {
        assertDoesNotThrow(() ->
                TransferRequest.builder()
                        .sourceUri(URI.create("file:///tmp/file.dat"))
                        .destinationUri(URI.create("sftp://server/file.dat"))
                        .build()
        );
    }
    
    @Test
    @DisplayName("Validation rejects both file:// endpoints")
    void testValidationRejectsBothFileSchemes() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                TransferRequest.builder()
                        .sourceUri(URI.create("file:///tmp/source.dat"))
                        .destinationUri(URI.create("file:///tmp/dest.dat"))
                        .build()
        );
        
        assertTrue(ex.getMessage().contains("Files.copy"));
    }
    
    @Test
    @DisplayName("Validation rejects remote-to-remote (not yet supported)")
    void testValidationRejectsRemoteToRemote() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class, () ->
                TransferRequest.builder()
                        .sourceUri(URI.create("sftp://server1/file.dat"))
                        .destinationUri(URI.create("ftp://server2/file.dat"))
                        .build()
        );
        
        assertTrue(ex.getMessage().toLowerCase().contains("at least one endpoint must be file://"));
    }
    
    @Test
    @DisplayName("Validation rejects null sourceUri")
    void testValidationRejectsNullSource() {
        assertThrows(NullPointerException.class, () ->
                TransferRequest.builder()
                        .destinationUri(URI.create("file:///tmp/file.dat"))
                        .build()
        );
    }
    
    @Test
    @DisplayName("Validation rejects null destinationUri and destinationPath")
    void testValidationRejectsNullDestination() {
        assertThrows(NullPointerException.class, () ->
                TransferRequest.builder()
                        .sourceUri(URI.create("sftp://server/file.dat"))
                        .build()
        );
    }
    
    // ========================================================================
    // Regression Tests
    // ========================================================================
    
    @Test
    @DisplayName("Existing builder with destinationPath still works")
    void testRegressionExistingBuilderStillWorks() {
        URI sourceUri = URI.create("http://example.com/file.txt");
        var destinationPath = Paths.get("/tmp/file.txt");
        
        TransferRequest request = TransferRequest.builder()
                .sourceUri(sourceUri)
                .destinationPath(destinationPath)
                .build();
        
        assertNotNull(request.getRequestId());
        assertEquals(sourceUri, request.getSourceUri());
        assertEquals(destinationPath.toAbsolutePath(), request.getDestinationPath());
        assertEquals("http", request.getProtocol());
    }
    
    @Test
    @DisplayName("Equals and hashCode still work")
    void testRegressionEqualsAndHashCode() {
        String requestId = "test-request-123";
        
        TransferRequest request1 = TransferRequest.builder()
                .requestId(requestId)
                .sourceUri(URI.create("http://example.com/file.txt"))
                .destinationPath(Paths.get("/tmp/file.txt"))
                .build();
        
        TransferRequest request2 = TransferRequest.builder()
                .requestId(requestId)
                .sourceUri(URI.create("http://different.com/file.txt"))
                .destinationPath(Paths.get("/different/file.txt"))
                .build();
        
        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
    }
}
