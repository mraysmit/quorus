package dev.mars.quorus.monitoring;

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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProtocolHealthCheck.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-17
 * @version 1.0
 */
class ProtocolHealthCheckTest {

    @Test
    void testBuilderWithMinimalConfiguration() {
        ProtocolHealthCheck check = ProtocolHealthCheck.builder("http")
                .build();

        assertEquals("http", check.getProtocolName());
        assertEquals(ProtocolHealthCheck.Status.UP, check.getStatus());
        assertNotNull(check.getTimestamp());
        assertTrue(check.getDetails().isEmpty());
        assertNull(check.getMessage());
        assertTrue(check.isHealthy());
    }

    @Test
    void testBuilderWithAllFields() {
        Instant now = Instant.now();
        Map<String, Object> details = new HashMap<>();
        details.put("connections", 10);
        details.put("latency", 50.5);

        ProtocolHealthCheck check = ProtocolHealthCheck.builder("sftp")
                .status(ProtocolHealthCheck.Status.DEGRADED)
                .timestamp(now)
                .message("High latency detected")
                .details(details)
                .build();

        assertEquals("sftp", check.getProtocolName());
        assertEquals(ProtocolHealthCheck.Status.DEGRADED, check.getStatus());
        assertEquals(now, check.getTimestamp());
        assertEquals("High latency detected", check.getMessage());
        assertEquals(10, check.getDetails().get("connections"));
        assertEquals(50.5, check.getDetails().get("latency"));
        assertFalse(check.isHealthy());
    }

    @Test
    void testStatusUp() {
        ProtocolHealthCheck check = ProtocolHealthCheck.builder("ftp")
                .up()
                .build();

        assertEquals(ProtocolHealthCheck.Status.UP, check.getStatus());
        assertTrue(check.isHealthy());
    }

    @Test
    void testStatusDown() {
        ProtocolHealthCheck check = ProtocolHealthCheck.builder("smb")
                .down()
                .message("Service unavailable")
                .build();

        assertEquals(ProtocolHealthCheck.Status.DOWN, check.getStatus());
        assertFalse(check.isHealthy());
    }

    @Test
    void testStatusDegraded() {
        ProtocolHealthCheck check = ProtocolHealthCheck.builder("nfs")
                .degraded()
                .message("Slow response times")
                .build();

        assertEquals(ProtocolHealthCheck.Status.DEGRADED, check.getStatus());
        assertFalse(check.isHealthy());
    }

    @Test
    void testAddSingleDetail() {
        ProtocolHealthCheck check = ProtocolHealthCheck.builder("http")
                .detail("activeConnections", 42)
                .detail("queueSize", 15)
                .build();

        Map<String, Object> details = check.getDetails();
        assertEquals(42, details.get("activeConnections"));
        assertEquals(15, details.get("queueSize"));
    }

    @Test
    void testAddMultipleDetailsViaMap() {
        Map<String, Object> detailsToAdd = new HashMap<>();
        detailsToAdd.put("cpu", 75.5);
        detailsToAdd.put("memory", 80.2);
        detailsToAdd.put("diskIO", 90);

        ProtocolHealthCheck check = ProtocolHealthCheck.builder("sftp")
                .details(detailsToAdd)
                .build();

        Map<String, Object> details = check.getDetails();
        assertEquals(75.5, details.get("cpu"));
        assertEquals(80.2, details.get("memory"));
        assertEquals(90, details.get("diskIO"));
    }

    @Test
    void testToMap() {
        Instant timestamp = Instant.parse("2025-01-20T10:00:00Z");
        
        ProtocolHealthCheck check = ProtocolHealthCheck.builder("http")
                .status(ProtocolHealthCheck.Status.UP)
                .timestamp(timestamp)
                .message("All systems operational")
                .detail("connections", 100)
                .build();

        Map<String, Object> map = check.toMap();

        assertEquals("http", map.get("protocol"));
        assertEquals("UP", map.get("status"));
        assertEquals("2025-01-20T10:00:00Z", map.get("timestamp"));
        assertEquals("All systems operational", map.get("message"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) map.get("details");
        assertEquals(100, details.get("connections"));
    }

    @Test
    void testToMapWithoutOptionalFields() {
        ProtocolHealthCheck check = ProtocolHealthCheck.builder("ftp")
                .build();

        Map<String, Object> map = check.toMap();

        assertEquals("ftp", map.get("protocol"));
        assertEquals("UP", map.get("status"));
        assertNotNull(map.get("timestamp"));
        assertNull(map.get("message"));
        assertNull(map.get("details"));
    }

    @Test
    void testIsHealthyForUpStatus() {
        ProtocolHealthCheck check = ProtocolHealthCheck.builder("http")
                .status(ProtocolHealthCheck.Status.UP)
                .build();

        assertTrue(check.isHealthy());
    }

    @Test
    void testIsHealthyForDownStatus() {
        ProtocolHealthCheck check = ProtocolHealthCheck.builder("http")
                .status(ProtocolHealthCheck.Status.DOWN)
                .build();

        assertFalse(check.isHealthy());
    }

    @Test
    void testIsHealthyForDegradedStatus() {
        ProtocolHealthCheck check = ProtocolHealthCheck.builder("http")
                .status(ProtocolHealthCheck.Status.DEGRADED)
                .build();

        assertFalse(check.isHealthy());
    }

    @Test
    void testProtocolNameRequired() {
        assertThrows(NullPointerException.class, () -> {
            ProtocolHealthCheck.builder(null).build();
        });
    }

    @Test
    void testDetailsImmutability() {
        Map<String, Object> originalDetails = new HashMap<>();
        originalDetails.put("key1", "value1");

        ProtocolHealthCheck check = ProtocolHealthCheck.builder("http")
                .details(originalDetails)
                .build();

        // Modify original map
        originalDetails.put("key2", "value2");

        // Check should not be affected
        Map<String, Object> checkDetails = check.getDetails();
        assertEquals(1, checkDetails.size());
        assertTrue(checkDetails.containsKey("key1"));
        assertFalse(checkDetails.containsKey("key2"));
    }

    @Test
    void testGetDetailsReturnsDefensiveCopy() {
        ProtocolHealthCheck check = ProtocolHealthCheck.builder("http")
                .detail("key1", "value1")
                .build();

        Map<String, Object> details1 = check.getDetails();
        details1.put("key2", "value2");

        Map<String, Object> details2 = check.getDetails();
        assertEquals(1, details2.size());
        assertFalse(details2.containsKey("key2"));
    }

    @Test
    void testTimestampDefaultsToNow() {
        Instant before = Instant.now();
        ProtocolHealthCheck check = ProtocolHealthCheck.builder("http").build();
        Instant after = Instant.now();

        Instant timestamp = check.getTimestamp();
        assertFalse(timestamp.isBefore(before));
        assertFalse(timestamp.isAfter(after));
    }

    @Test
    void testTimestampCanBeSet() {
        Instant customTime = Instant.parse("2025-01-01T00:00:00Z");
        
        ProtocolHealthCheck check = ProtocolHealthCheck.builder("http")
                .timestamp(customTime)
                .build();

        assertEquals(customTime, check.getTimestamp());
    }

    @Test
    void testMultipleDetailAdditions() {
        ProtocolHealthCheck check = ProtocolHealthCheck.builder("http")
                .detail("key1", "value1")
                .detail("key2", 42)
                .detail("key3", true)
                .build();

        Map<String, Object> details = check.getDetails();
        assertEquals(3, details.size());
        assertEquals("value1", details.get("key1"));
        assertEquals(42, details.get("key2"));
        assertEquals(true, details.get("key3"));
    }

    @Test
    void testBuilderCanChainMethods() {
        ProtocolHealthCheck check = ProtocolHealthCheck.builder("http")
                .up()
                .message("Service operational")
                .detail("key1", "value1")
                .detail("key2", 100)
                .timestamp(Instant.now())
                .build();

        assertNotNull(check);
        assertEquals(ProtocolHealthCheck.Status.UP, check.getStatus());
    }

    @Test
    void testStatusEnumValues() {
        assertEquals(3, ProtocolHealthCheck.Status.values().length);
        assertEquals(ProtocolHealthCheck.Status.UP, ProtocolHealthCheck.Status.valueOf("UP"));
        assertEquals(ProtocolHealthCheck.Status.DOWN, ProtocolHealthCheck.Status.valueOf("DOWN"));
        assertEquals(ProtocolHealthCheck.Status.DEGRADED, ProtocolHealthCheck.Status.valueOf("DEGRADED"));
    }

    @Test
    void testEmptyDetailsMap() {
        ProtocolHealthCheck check = ProtocolHealthCheck.builder("http")
                .details(new HashMap<>())
                .build();

        assertTrue(check.getDetails().isEmpty());
    }

    @Test
    void testNullMessage() {
        ProtocolHealthCheck check = ProtocolHealthCheck.builder("http")
                .message(null)
                .build();

        assertNull(check.getMessage());
    }

    @Test
    void testEmptyMessage() {
        ProtocolHealthCheck check = ProtocolHealthCheck.builder("http")
                .message("")
                .build();

        assertEquals("", check.getMessage());
    }

    @Test
    void testOverwritingStatus() {
        ProtocolHealthCheck check = ProtocolHealthCheck.builder("http")
                .down()
                .up()  // Should overwrite DOWN with UP
                .build();

        assertEquals(ProtocolHealthCheck.Status.UP, check.getStatus());
    }

    @Test
    void testComplexDetailsValues() {
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("nested", "value");

        ProtocolHealthCheck check = ProtocolHealthCheck.builder("http")
                .detail("string", "text")
                .detail("integer", 42)
                .detail("double", 3.14)
                .detail("boolean", true)
                .detail("map", nestedMap)
                .build();

        Map<String, Object> details = check.getDetails();
        assertEquals("text", details.get("string"));
        assertEquals(42, details.get("integer"));
        assertEquals(3.14, details.get("double"));
        assertEquals(true, details.get("boolean"));
        assertEquals(nestedMap, details.get("map"));
    }
}
