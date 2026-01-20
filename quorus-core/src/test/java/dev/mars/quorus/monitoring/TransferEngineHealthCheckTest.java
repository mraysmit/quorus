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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransferEngineHealthCheck.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-17
 * @version 1.0
 */
class TransferEngineHealthCheckTest {

    @Test
    void testBuilderWithMinimalConfiguration() {
        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .build();

        assertEquals(TransferEngineHealthCheck.Status.UP, check.getStatus());
        assertNotNull(check.getTimestamp());
        assertTrue(check.getProtocolHealthChecks().isEmpty());
        assertTrue(check.getSystemMetrics().isEmpty());
        assertNull(check.getMessage());
        assertTrue(check.isHealthy());
    }

    @Test
    void testBuilderWithAllFields() {
        Instant now = Instant.now();
        
        ProtocolHealthCheck httpCheck = ProtocolHealthCheck.builder("http")
                .up()
                .build();
        
        ProtocolHealthCheck sftpCheck = ProtocolHealthCheck.builder("sftp")
                .degraded()
                .build();

        Map<String, Object> sysMetrics = new HashMap<>();
        sysMetrics.put("cpu", 50.5);
        sysMetrics.put("memory", 75.2);

        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .status(TransferEngineHealthCheck.Status.DEGRADED)
                .timestamp(now)
                .message("Some protocols degraded")
                .addProtocolHealthCheck(httpCheck)
                .addProtocolHealthCheck(sftpCheck)
                .systemMetrics(sysMetrics)
                .build();

        assertEquals(TransferEngineHealthCheck.Status.DEGRADED, check.getStatus());
        assertEquals(now, check.getTimestamp());
        assertEquals("Some protocols degraded", check.getMessage());
        assertEquals(2, check.getProtocolHealthChecks().size());
        assertEquals(2, check.getSystemMetrics().size());
        assertFalse(check.isHealthy());
    }

    @Test
    void testStatusUp() {
        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .up()
                .build();

        assertEquals(TransferEngineHealthCheck.Status.UP, check.getStatus());
        assertTrue(check.isHealthy());
    }

    @Test
    void testStatusDown() {
        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .down()
                .message("System offline")
                .build();

        assertEquals(TransferEngineHealthCheck.Status.DOWN, check.getStatus());
        assertFalse(check.isHealthy());
    }

    @Test
    void testStatusDegraded() {
        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .degraded()
                .message("Performance issues")
                .build();

        assertEquals(TransferEngineHealthCheck.Status.DEGRADED, check.getStatus());
        assertFalse(check.isHealthy());
    }

    @Test
    void testAddProtocolHealthCheck() {
        ProtocolHealthCheck httpCheck = ProtocolHealthCheck.builder("http").up().build();
        ProtocolHealthCheck ftpCheck = ProtocolHealthCheck.builder("ftp").down().build();

        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .addProtocolHealthCheck(httpCheck)
                .addProtocolHealthCheck(ftpCheck)
                .build();

        List<ProtocolHealthCheck> protocols = check.getProtocolHealthChecks();
        assertEquals(2, protocols.size());
    }

    @Test
    void testSystemMetricSingle() {
        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .systemMetric("cpu", 45.5)
                .systemMetric("memory", 60.2)
                .build();

        Map<String, Object> metrics = check.getSystemMetrics();
        assertEquals(45.5, metrics.get("cpu"));
        assertEquals(60.2, metrics.get("memory"));
    }

    @Test
    void testSystemMetricsMap() {
        Map<String, Object> metricsToAdd = new HashMap<>();
        metricsToAdd.put("threads", 150);
        metricsToAdd.put("heap", 1024);
        metricsToAdd.put("disk", 80.5);

        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .systemMetrics(metricsToAdd)
                .build();

        Map<String, Object> metrics = check.getSystemMetrics();
        assertEquals(150, metrics.get("threads"));
        assertEquals(1024, metrics.get("heap"));
        assertEquals(80.5, metrics.get("disk"));
    }

    @Test
    void testToMap() {
        Instant timestamp = Instant.parse("2025-01-20T10:00:00Z");
        
        ProtocolHealthCheck httpCheck = ProtocolHealthCheck.builder("http")
                .up()
                .build();
        
        ProtocolHealthCheck ftpCheck = ProtocolHealthCheck.builder("ftp")
                .down()
                .build();

        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .status(TransferEngineHealthCheck.Status.DEGRADED)
                .timestamp(timestamp)
                .message("One protocol down")
                .addProtocolHealthCheck(httpCheck)
                .addProtocolHealthCheck(ftpCheck)
                .systemMetric("cpu", 50.0)
                .build();

        Map<String, Object> map = check.toMap();

        assertEquals("DEGRADED", map.get("status"));
        assertEquals("2025-01-20T10:00:00Z", map.get("timestamp"));
        assertEquals("One protocol down", map.get("message"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> protocols = (List<Map<String, Object>>) map.get("protocols");
        assertEquals(2, protocols.size());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> system = (Map<String, Object>) map.get("system");
        assertEquals(50.0, system.get("cpu"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) map.get("summary");
        assertEquals(2L, summary.get("totalProtocols"));
        assertEquals(1L, summary.get("healthyProtocols"));
        assertEquals(1L, summary.get("unhealthyProtocols"));
    }

    @Test
    void testToMapWithMinimalData() {
        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .build();

        Map<String, Object> map = check.toMap();

        assertEquals("UP", map.get("status"));
        assertNotNull(map.get("timestamp"));
        assertNull(map.get("message"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> protocols = (List<Map<String, Object>>) map.get("protocols");
        assertTrue(protocols.isEmpty());
        
        assertNull(map.get("system"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) map.get("summary");
        assertEquals(0L, summary.get("totalProtocols"));
        assertEquals(0L, summary.get("healthyProtocols"));
        assertEquals(0L, summary.get("unhealthyProtocols"));
    }

    @Test
    void testIsHealthyForUpStatus() {
        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .status(TransferEngineHealthCheck.Status.UP)
                .build();

        assertTrue(check.isHealthy());
    }

    @Test
    void testIsHealthyForDownStatus() {
        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .status(TransferEngineHealthCheck.Status.DOWN)
                .build();

        assertFalse(check.isHealthy());
    }

    @Test
    void testIsHealthyForDegradedStatus() {
        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .status(TransferEngineHealthCheck.Status.DEGRADED)
                .build();

        assertFalse(check.isHealthy());
    }

    @Test
    void testProtocolHealthChecksImmutability() {
        ProtocolHealthCheck httpCheck = ProtocolHealthCheck.builder("http").up().build();
        
        TransferEngineHealthCheck.Builder builder = TransferEngineHealthCheck.builder()
                .addProtocolHealthCheck(httpCheck);
        
        TransferEngineHealthCheck check = builder.build();
        
        // Try to modify returned list
        List<ProtocolHealthCheck> protocols1 = check.getProtocolHealthChecks();
        assertEquals(1, protocols1.size());
        
        // Get list again to verify immutability
        List<ProtocolHealthCheck> protocols2 = check.getProtocolHealthChecks();
        assertEquals(1, protocols2.size());
    }

    @Test
    void testSystemMetricsImmutability() {
        Map<String, Object> originalMetrics = new HashMap<>();
        originalMetrics.put("key1", "value1");

        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .systemMetrics(originalMetrics)
                .build();

        // Modify original map
        originalMetrics.put("key2", "value2");

        // Check should not be affected
        Map<String, Object> checkMetrics = check.getSystemMetrics();
        assertEquals(1, checkMetrics.size());
        assertTrue(checkMetrics.containsKey("key1"));
        assertFalse(checkMetrics.containsKey("key2"));
    }

    @Test
    void testGetSystemMetricsReturnsDefensiveCopy() {
        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .systemMetric("key1", "value1")
                .build();

        Map<String, Object> metrics1 = check.getSystemMetrics();
        metrics1.put("key2", "value2");

        Map<String, Object> metrics2 = check.getSystemMetrics();
        assertEquals(1, metrics2.size());
        assertFalse(metrics2.containsKey("key2"));
    }

    @Test
    void testTimestampDefaultsToNow() {
        Instant before = Instant.now();
        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder().build();
        Instant after = Instant.now();

        Instant timestamp = check.getTimestamp();
        assertFalse(timestamp.isBefore(before));
        assertFalse(timestamp.isAfter(after));
    }

    @Test
    void testTimestampCanBeSet() {
        Instant customTime = Instant.parse("2025-01-01T00:00:00Z");
        
        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .timestamp(customTime)
                .build();

        assertEquals(customTime, check.getTimestamp());
    }

    @Test
    void testSummaryStatisticsAllHealthy() {
        ProtocolHealthCheck http = ProtocolHealthCheck.builder("http").up().build();
        ProtocolHealthCheck ftp = ProtocolHealthCheck.builder("ftp").up().build();
        ProtocolHealthCheck sftp = ProtocolHealthCheck.builder("sftp").up().build();

        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .addProtocolHealthCheck(http)
                .addProtocolHealthCheck(ftp)
                .addProtocolHealthCheck(sftp)
                .build();

        Map<String, Object> map = check.toMap();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) map.get("summary");
        assertEquals(3L, summary.get("totalProtocols"));
        assertEquals(3L, summary.get("healthyProtocols"));
        assertEquals(0L, summary.get("unhealthyProtocols"));
    }

    @Test
    void testSummaryStatisticsAllUnhealthy() {
        ProtocolHealthCheck http = ProtocolHealthCheck.builder("http").down().build();
        ProtocolHealthCheck ftp = ProtocolHealthCheck.builder("ftp").degraded().build();

        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .addProtocolHealthCheck(http)
                .addProtocolHealthCheck(ftp)
                .build();

        Map<String, Object> map = check.toMap();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) map.get("summary");
        assertEquals(2L, summary.get("totalProtocols"));
        assertEquals(0L, summary.get("healthyProtocols"));
        assertEquals(2L, summary.get("unhealthyProtocols"));
    }

    @Test
    void testSummaryStatisticsMixed() {
        ProtocolHealthCheck http = ProtocolHealthCheck.builder("http").up().build();
        ProtocolHealthCheck ftp = ProtocolHealthCheck.builder("ftp").down().build();
        ProtocolHealthCheck sftp = ProtocolHealthCheck.builder("sftp").degraded().build();
        ProtocolHealthCheck smb = ProtocolHealthCheck.builder("smb").up().build();

        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .addProtocolHealthCheck(http)
                .addProtocolHealthCheck(ftp)
                .addProtocolHealthCheck(sftp)
                .addProtocolHealthCheck(smb)
                .build();

        Map<String, Object> map = check.toMap();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) map.get("summary");
        assertEquals(4L, summary.get("totalProtocols"));
        assertEquals(2L, summary.get("healthyProtocols"));
        assertEquals(2L, summary.get("unhealthyProtocols"));
    }

    @Test
    void testBuilderCanChainMethods() {
        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .up()
                .message("All systems go")
                .systemMetric("key", "value")
                .timestamp(Instant.now())
                .build();

        assertNotNull(check);
        assertEquals(TransferEngineHealthCheck.Status.UP, check.getStatus());
    }

    @Test
    void testStatusEnumValues() {
        assertEquals(3, TransferEngineHealthCheck.Status.values().length);
        assertEquals(TransferEngineHealthCheck.Status.UP, TransferEngineHealthCheck.Status.valueOf("UP"));
        assertEquals(TransferEngineHealthCheck.Status.DOWN, TransferEngineHealthCheck.Status.valueOf("DOWN"));
        assertEquals(TransferEngineHealthCheck.Status.DEGRADED, TransferEngineHealthCheck.Status.valueOf("DEGRADED"));
    }

    @Test
    void testEmptySystemMetrics() {
        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .systemMetrics(new HashMap<>())
                .build();

        assertTrue(check.getSystemMetrics().isEmpty());
        
        Map<String, Object> map = check.toMap();
        assertNull(map.get("system"));
    }

    @Test
    void testNullMessage() {
        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .message(null)
                .build();

        assertNull(check.getMessage());
    }

    @Test
    void testEmptyMessage() {
        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .message("")
                .build();

        assertEquals("", check.getMessage());
    }

    @Test
    void testOverwritingStatus() {
        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .down()
                .up()  // Should overwrite DOWN with UP
                .build();

        assertEquals(TransferEngineHealthCheck.Status.UP, check.getStatus());
    }

    @Test
    void testMultipleSystemMetricAdditions() {
        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .systemMetric("cpu", 50.0)
                .systemMetric("memory", 70.0)
                .systemMetric("disk", 80.0)
                .build();

        Map<String, Object> metrics = check.getSystemMetrics();
        assertEquals(3, metrics.size());
        assertEquals(50.0, metrics.get("cpu"));
        assertEquals(70.0, metrics.get("memory"));
        assertEquals(80.0, metrics.get("disk"));
    }

    @Test
    void testMixingSystemMetricMethodsAndMap() {
        Map<String, Object> metricsMap = new HashMap<>();
        metricsMap.put("heap", 512);
        metricsMap.put("nonHeap", 128);

        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .systemMetric("threads", 200)
                .systemMetrics(metricsMap)
                .systemMetric("connections", 50)
                .build();

        Map<String, Object> metrics = check.getSystemMetrics();
        assertEquals(4, metrics.size());
        assertEquals(200, metrics.get("threads"));
        assertEquals(512, metrics.get("heap"));
        assertEquals(128, metrics.get("nonHeap"));
        assertEquals(50, metrics.get("connections"));
    }

    @Test
    void testComplexSystemMetricValues() {
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("nested", "value");

        TransferEngineHealthCheck check = TransferEngineHealthCheck.builder()
                .systemMetric("string", "text")
                .systemMetric("integer", 42)
                .systemMetric("double", 3.14)
                .systemMetric("boolean", true)
                .systemMetric("map", nestedMap)
                .build();

        Map<String, Object> metrics = check.getSystemMetrics();
        assertEquals("text", metrics.get("string"));
        assertEquals(42, metrics.get("integer"));
        assertEquals(3.14, metrics.get("double"));
        assertEquals(true, metrics.get("boolean"));
        assertEquals(nestedMap, metrics.get("map"));
    }
}
