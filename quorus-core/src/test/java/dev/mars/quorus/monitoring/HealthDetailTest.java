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

package dev.mars.quorus.monitoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HealthDetail record.
 */
@DisplayName("HealthDetail Record Tests")
class HealthDetailTest {

    @Test
    @DisplayName("should create HealthDetail with required fields only")
    void testMinimalCreation() {
        HealthDetail detail = HealthDetail.of("test-component", HealthStatus.UP);
        
        assertEquals("test-component", detail.component());
        assertEquals(HealthStatus.UP, detail.status());
        assertNull(detail.message());
        assertTrue(detail.metadata().isEmpty());
        assertNotNull(detail.timestamp());
    }
    
    @Test
    @DisplayName("should create HealthDetail with message")
    void testCreationWithMessage() {
        HealthDetail detail = HealthDetail.of("test-component", HealthStatus.DOWN, "Service unavailable");
        
        assertEquals("test-component", detail.component());
        assertEquals(HealthStatus.DOWN, detail.status());
        assertEquals("Service unavailable", detail.message());
    }
    
    @Test
    @DisplayName("should build HealthDetail with builder")
    void testBuilder() {
        Instant now = Instant.now();
        HealthDetail detail = HealthDetail.builder("my-service")
            .status(HealthStatus.DEGRADED)
            .message("Experiencing high latency")
            .timestamp(now)
            .metadata("latency", "500ms")
            .metadata("connections", 42L)
            .metadata("healthy", true)
            .build();
        
        assertEquals("my-service", detail.component());
        assertEquals(HealthStatus.DEGRADED, detail.status());
        assertEquals("Experiencing high latency", detail.message());
        assertEquals(now, detail.timestamp());
        assertEquals("500ms", detail.metadata().get("latency"));
        assertEquals("42", detail.metadata().get("connections"));
        assertEquals("true", detail.metadata().get("healthy"));
    }
    
    @Test
    @DisplayName("should create defensive copy of metadata")
    void testDefensiveCopy() {
        Map<String, String> mutableMap = new java.util.HashMap<>();
        mutableMap.put("key", "value");
        
        HealthDetail detail = new HealthDetail(
            "component", 
            HealthStatus.UP, 
            null, 
            mutableMap, 
            Instant.now()
        );
        
        // Modify original map
        mutableMap.put("key", "modified");
        mutableMap.put("newKey", "newValue");
        
        // Record should have original value
        assertEquals("value", detail.metadata().get("key"));
        assertNull(detail.metadata().get("newKey"));
    }
    
    @Test
    @DisplayName("should indicate healthy status correctly")
    void testIsHealthy() {
        assertTrue(HealthDetail.of("c", HealthStatus.UP).isHealthy());
        assertFalse(HealthDetail.of("c", HealthStatus.DOWN).isHealthy());
        assertFalse(HealthDetail.of("c", HealthStatus.DEGRADED).isHealthy());
    }
    
    @Test
    @DisplayName("should indicate operational status correctly")
    void testIsOperational() {
        assertTrue(HealthDetail.of("c", HealthStatus.UP).isOperational());
        assertFalse(HealthDetail.of("c", HealthStatus.DOWN).isOperational());
        assertTrue(HealthDetail.of("c", HealthStatus.DEGRADED).isOperational());
    }
    
    @Test
    @DisplayName("should convert to map for JSON serialization")
    void testToMap() {
        HealthDetail detail = HealthDetail.builder("http-protocol")
            .status(HealthStatus.UP)
            .message("All connections healthy")
            .metadata("activeConnections", 10L)
            .build();
        
        Map<String, Object> map = detail.toMap();
        
        assertEquals("http-protocol", map.get("component"));
        assertEquals("UP", map.get("status"));
        assertEquals("All connections healthy", map.get("message"));
        assertNotNull(map.get("timestamp"));
        
        @SuppressWarnings("unchecked")
        Map<String, String> metadata = (Map<String, String>) map.get("metadata");
        assertEquals("10", metadata.get("activeConnections"));
    }
    
    @Test
    @DisplayName("should reject null component")
    void testRejectNullComponent() {
        assertThrows(NullPointerException.class, () -> 
            HealthDetail.of(null, HealthStatus.UP));
    }
    
    @Test
    @DisplayName("should reject null status")
    void testRejectNullStatus() {
        assertThrows(NullPointerException.class, () -> 
            HealthDetail.of("component", null));
    }
    
    @Test
    @DisplayName("should use convenience builder methods")
    void testBuilderConvenienceMethods() {
        assertEquals(HealthStatus.UP, HealthDetail.builder("c").up().build().status());
        assertEquals(HealthStatus.DOWN, HealthDetail.builder("c").down().build().status());
        assertEquals(HealthStatus.DEGRADED, HealthDetail.builder("c").degraded().build().status());
    }
    
    @Test
    @DisplayName("should handle null metadata map")
    void testNullMetadataMap() {
        HealthDetail detail = new HealthDetail("c", HealthStatus.UP, null, null, null);
        
        assertNotNull(detail.metadata());
        assertTrue(detail.metadata().isEmpty());
    }
    
    @Test
    @DisplayName("should handle null timestamp")
    void testNullTimestamp() {
        HealthDetail detail = new HealthDetail("c", HealthStatus.UP, null, null, null);
        
        assertNotNull(detail.timestamp());
    }
}
