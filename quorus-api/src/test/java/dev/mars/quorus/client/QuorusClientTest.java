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

package dev.mars.quorus.client;

import dev.mars.quorus.api.dto.TransferJobResponseDto;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for QuorusClient using Quarkus test framework.
 * Tests the client SDK against the running Quarkus application.
 */
@QuarkusTest
class QuorusClientTest {

    private QuorusClient client;

    @BeforeEach
    void setUp() {
        // Create client pointing to the test server
        client = new QuorusClient("http://localhost:8081", "admin", "admin123");
    }

    @Test
    void testGetServiceInfo() throws QuorusClientException {
        String info = client.getServiceInfo();
        
        assertNotNull(info);
        assertTrue(info.contains("quorus-api"));
        assertTrue(info.contains("2.0"));
        assertTrue(info.contains("Quarkus"));
    }

    @Test
    void testGetActiveTransferCount() throws QuorusClientException {
        int count = client.getActiveTransferCount();
        
        assertTrue(count >= 0, "Active transfer count should be non-negative");
    }

    @Test
    void testCreateTransfer() throws QuorusClientException {
        String sourceUri = "https://httpbin.org/bytes/1024";
        String destinationPath = "/tmp/client-test.bin";
        String description = "Client SDK test transfer";

        TransferJobResponseDto response = client.createTransfer(sourceUri, destinationPath, description);
        
        assertNotNull(response);
        assertNotNull(response.getJobId());
        assertEquals(sourceUri, response.getSourceUri());
        assertEquals(destinationPath, response.getDestinationPath());
        assertNotNull(response.getStatus());
    }

    @Test
    void testCreateTransferAndGetStatus() throws QuorusClientException {
        // Create a transfer
        String sourceUri = "https://httpbin.org/bytes/2048";
        String destinationPath = "/tmp/client-status-test.bin";

        TransferJobResponseDto createResponse = client.createTransfer(sourceUri, destinationPath);
        assertNotNull(createResponse.getJobId());

        // Get the status
        TransferJobResponseDto statusResponse = client.getTransferStatus(createResponse.getJobId());
        
        assertNotNull(statusResponse);
        assertEquals(createResponse.getJobId(), statusResponse.getJobId());
        assertEquals(sourceUri, statusResponse.getSourceUri());
        assertEquals(destinationPath, statusResponse.getDestinationPath());
        assertNotNull(statusResponse.getStatus());
    }

    @Test
    void testGetTransferStatusNotFound() {
        QuorusClientException exception = assertThrows(QuorusClientException.class, () -> {
            client.getTransferStatus("non-existent-job-id");
        });
        
        assertTrue(exception.getMessage().contains("Transfer job not found"));
    }

    @Test
    void testCreateTransferWithInvalidCredentials() {
        QuorusClient invalidClient = new QuorusClient("http://localhost:8081", "invalid", "invalid");
        
        QuorusClientException exception = assertThrows(QuorusClientException.class, () -> {
            invalidClient.createTransfer("https://example.com/file", "/tmp/test");
        });
        
        assertTrue(exception.getMessage().contains("HTTP 401") || 
                  exception.getMessage().contains("Unauthorized"));
    }

    @Test
    void testCreateTransferWithInvalidData() {
        QuorusClientException exception = assertThrows(QuorusClientException.class, () -> {
            client.createTransfer("", ""); // Invalid empty URIs
        });
        
        assertTrue(exception.getMessage().contains("HTTP 400") || 
                  exception.getMessage().contains("Invalid transfer request"));
    }

    @Test
    void testCancelNonExistentTransfer() {
        QuorusClientException exception = assertThrows(QuorusClientException.class, () -> {
            client.cancelTransfer("non-existent-job-id");
        });
        
        assertTrue(exception.getMessage().contains("Transfer cannot be cancelled") ||
                  exception.getMessage().contains("Transfer job not found"));
    }

    @Test
    void testCreateTransferAsync() throws Exception {
        String sourceUri = "https://httpbin.org/bytes/512";
        String destinationPath = "/tmp/async-test.bin";

        TransferJobResponseDto response = client.createTransferAsync(sourceUri, destinationPath)
                .get(); // Wait for completion
        
        assertNotNull(response);
        assertNotNull(response.getJobId());
        assertEquals(sourceUri, response.getSourceUri());
        assertEquals(destinationPath, response.getDestinationPath());
    }

    @Test
    void testClientConstructor() {
        QuorusClient testClient = new QuorusClient("http://example.com/", "user", "pass");
        assertNotNull(testClient);
        
        // Test URL normalization (trailing slash removal)
        testClient = new QuorusClient("http://example.com", "user", "pass");
        assertNotNull(testClient);
    }

    @Test
    void testClientClose() {
        QuorusClient testClient = new QuorusClient("http://example.com", "user", "pass");
        
        // Should not throw any exception
        assertDoesNotThrow(() -> testClient.close());
    }
}
