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
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;

class TransferRequestTest {
    
    @Test
    void testBuilderWithRequiredFields() {
        URI sourceUri = URI.create("http://example.com/file.txt");
        var destinationPath = Paths.get("/tmp/file.txt");
        
        TransferRequest request = TransferRequest.builder()
                .sourceUri(sourceUri)
                .destinationPath(destinationPath)
                .build();
        
        assertNotNull(request.getRequestId());
        assertEquals(sourceUri, request.getSourceUri());
        assertEquals(destinationPath, request.getDestinationPath());
        assertEquals("http", request.getProtocol()); // default
        assertTrue(request.getMetadata().isEmpty());
        assertNotNull(request.getCreatedAt());
        assertEquals(-1, request.getExpectedSize()); // default
        assertNull(request.getExpectedChecksum());
    }
    
    @Test
    void testBuilderWithAllFields() {
        URI sourceUri = URI.create("https://example.com/file.txt");
        var destinationPath = Paths.get("/tmp/file.txt");
        String requestId = "test-request-123";
        String protocol = "https";
        Map<String, String> metadata = Map.of("key1", "value1", "key2", "value2");
        Instant createdAt = Instant.now();
        long expectedSize = 1024;
        String expectedChecksum = "abc123";
        
        TransferRequest request = TransferRequest.builder()
                .requestId(requestId)
                .sourceUri(sourceUri)
                .destinationPath(destinationPath)
                .protocol(protocol)
                .metadata(metadata)
                .createdAt(createdAt)
                .expectedSize(expectedSize)
                .expectedChecksum(expectedChecksum)
                .build();
        
        assertEquals(requestId, request.getRequestId());
        assertEquals(sourceUri, request.getSourceUri());
        assertEquals(destinationPath, request.getDestinationPath());
        assertEquals(protocol, request.getProtocol());
        assertEquals(metadata, request.getMetadata());
        assertEquals(createdAt, request.getCreatedAt());
        assertEquals(expectedSize, request.getExpectedSize());
        assertEquals(expectedChecksum, request.getExpectedChecksum());
    }
    
    @Test
    void testBuilderValidation() {
        assertThrows(NullPointerException.class, () -> 
                TransferRequest.builder().destinationPath(Paths.get("/tmp/file.txt")).build());
        
        assertThrows(NullPointerException.class, () -> 
                TransferRequest.builder().sourceUri(URI.create("http://example.com/file.txt")).build());
    }
    
    @Test
    void testEqualsAndHashCode() {
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
        
        assertEquals(request1, request2); // Same request ID
        assertEquals(request1.hashCode(), request2.hashCode());
    }
    
    @Test
    void testToString() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("http://example.com/file.txt"))
                .destinationPath(Paths.get("/tmp/file.txt"))
                .expectedSize(1024)
                .build();
        
        String toString = request.toString();
        assertTrue(toString.contains("TransferRequest"));
        assertTrue(toString.contains(request.getRequestId()));
        assertTrue(toString.contains("http://example.com/file.txt"));
        assertTrue(toString.contains("1024"));
    }
    
    @Test
    void testMetadataImmutability() {
        Map<String, String> originalMetadata = Map.of("key1", "value1");
        
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("http://example.com/file.txt"))
                .destinationPath(Paths.get("/tmp/file.txt"))
                .metadata(originalMetadata)
                .build();
        
        Map<String, String> retrievedMetadata = request.getMetadata();
        assertThrows(UnsupportedOperationException.class, () -> 
                retrievedMetadata.put("key2", "value2"));
    }
}
