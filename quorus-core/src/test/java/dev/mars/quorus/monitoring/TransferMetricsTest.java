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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransferMetrics.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-17
 * @version 1.0
 */
class TransferMetricsTest {

    private TransferMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new TransferMetrics("test-protocol");
    }

    @Test
    void testInitialState() {
        Map<String, Object> map = metrics.toMap();
        
        assertEquals("test-protocol", map.get("protocol"));
        assertEquals(0L, map.get("totalTransfers"));
        assertEquals(0L, map.get("successfulTransfers"));
        assertEquals(0L, map.get("failedTransfers"));
        assertEquals(0L, map.get("activeTransfers"));
        assertEquals(0L, map.get("totalBytesTransferred"));
        assertEquals("N/A", map.get("successRate"));
    }

    @Test
    void testRecordTransferStart() {
        metrics.recordTransferStart();
        
        Map<String, Object> map = metrics.toMap();
        assertEquals(1L, map.get("totalTransfers"));
        assertEquals(1L, map.get("activeTransfers"));
    }

    @Test
    void testMultipleTransferStarts() {
        metrics.recordTransferStart();
        metrics.recordTransferStart();
        metrics.recordTransferStart();
        
        Map<String, Object> map = metrics.toMap();
        assertEquals(3L, map.get("totalTransfers"));
        assertEquals(3L, map.get("activeTransfers"));
    }

    @Test
    void testRecordTransferSuccess() {
        metrics.recordTransferStart();
        metrics.recordTransferSuccess(1024L, Duration.ofMillis(100));
        
        Map<String, Object> map = metrics.toMap();
        assertEquals(1L, map.get("totalTransfers"));
        assertEquals(1L, map.get("successfulTransfers"));
        assertEquals(0L, map.get("failedTransfers"));
        assertEquals(0L, map.get("activeTransfers"));
        assertEquals(1024L, map.get("totalBytesTransferred"));
    }

    @Test
    void testRecordTransferFailure() {
        metrics.recordTransferStart();
        metrics.recordTransferFailure("TIMEOUT");
        
        Map<String, Object> map = metrics.toMap();
        assertEquals(1L, map.get("totalTransfers"));
        assertEquals(0L, map.get("successfulTransfers"));
        assertEquals(1L, map.get("failedTransfers"));
        assertEquals(0L, map.get("activeTransfers"));
    }

    @Test
    void testSuccessRate() {
        metrics.recordTransferStart();
        metrics.recordTransferSuccess(1024L, Duration.ofMillis(100));
        
        metrics.recordTransferStart();
        metrics.recordTransferSuccess(2048L, Duration.ofMillis(200));
        
        metrics.recordTransferStart();
        metrics.recordTransferFailure("ERROR");
        
        Map<String, Object> map = metrics.toMap();
        assertEquals(3L, map.get("totalTransfers"));
        assertEquals(2L, map.get("successfulTransfers"));
        assertEquals(1L, map.get("failedTransfers"));
        
        String successRate = (String) map.get("successRate");
        assertTrue(successRate.contains("66.67"));
    }

    @Test
    void testBytesTransferred() {
        metrics.recordTransferStart();
        metrics.recordTransferSuccess(1024L * 1024L, Duration.ofMillis(100)); // 1 MB
        
        metrics.recordTransferStart();
        metrics.recordTransferSuccess(2 * 1024L * 1024L, Duration.ofMillis(200)); // 2 MB
        
        Map<String, Object> map = metrics.toMap();
        assertEquals(3L * 1024L * 1024L, map.get("totalBytesTransferred"));
        
        String mbTransferred = (String) map.get("totalBytesTransferredMB");
        assertTrue(mbTransferred.contains("3.00"));
    }

    @Test
    void testAverageDuration() {
        metrics.recordTransferStart();
        metrics.recordTransferSuccess(1024L, Duration.ofMillis(100));
        
        metrics.recordTransferStart();
        metrics.recordTransferSuccess(2048L, Duration.ofMillis(200));
        
        Map<String, Object> map = metrics.toMap();
        assertEquals(150L, map.get("averageDurationMs"));
    }

    @Test
    void testMinMaxDuration() {
        metrics.recordTransferStart();
        metrics.recordTransferSuccess(1024L, Duration.ofMillis(50));
        
        metrics.recordTransferStart();
        metrics.recordTransferSuccess(2048L, Duration.ofMillis(200));
        
        metrics.recordTransferStart();
        metrics.recordTransferSuccess(4096L, Duration.ofMillis(100));
        
        Map<String, Object> map = metrics.toMap();
        assertEquals(50L, map.get("minDurationMs"));
        assertEquals(200L, map.get("maxDurationMs"));
    }

    @Test
    void testErrorTracking() {
        metrics.recordTransferStart();
        metrics.recordTransferFailure("TIMEOUT");
        
        metrics.recordTransferStart();
        metrics.recordTransferFailure("TIMEOUT");
        
        metrics.recordTransferStart();
        metrics.recordTransferFailure("NETWORK_ERROR");
        
        Map<String, Object> map = metrics.toMap();
        
        @SuppressWarnings("unchecked")
        Map<String, Long> errors = (Map<String, Long>) map.get("errors");
        
        assertNotNull(errors);
        assertEquals(2L, errors.get("TIMEOUT"));
        assertEquals(1L, errors.get("NETWORK_ERROR"));
    }

    @Test
    void testLastTransferTracking() {
        metrics.recordTransferStart();
        metrics.recordTransferSuccess(1024L, Duration.ofMillis(100));
        
        Map<String, Object> map = metrics.toMap();
        assertNotNull(map.get("lastTransferTime"));
        assertEquals(true, map.get("lastTransferSuccess"));
    }

    @Test
    void testLastTransferFailure() {
        metrics.recordTransferStart();
        metrics.recordTransferFailure("ERROR");
        
        Map<String, Object> map = metrics.toMap();
        assertNotNull(map.get("lastTransferTime"));
        assertEquals(false, map.get("lastTransferSuccess"));
    }

    @Test
    void testThroughputCalculation() throws InterruptedException {
        // Wait a bit to ensure uptime > 0 seconds for throughput calculation
        Thread.sleep(1100);
        
        metrics.recordTransferStart();
        metrics.recordTransferSuccess(1024L, Duration.ofMillis(100));
        
        Map<String, Object> map = metrics.toMap();
        assertNotNull(map.get("transfersPerSecond"));
        assertNotNull(map.get("bytesPerSecond"));
    }

    @Test
    void testUptimeTracking() {
        Map<String, Object> map = metrics.toMap();
        
        assertNotNull(map.get("startTime"));
        assertNotNull(map.get("uptime"));
    }

    @Test
    void testConcurrentTransfers() {
        metrics.recordTransferStart();
        metrics.recordTransferStart();
        metrics.recordTransferStart();
        
        Map<String, Object> map1 = metrics.toMap();
        assertEquals(3L, map1.get("activeTransfers"));
        
        metrics.recordTransferSuccess(1024L, Duration.ofMillis(100));
        
        Map<String, Object> map2 = metrics.toMap();
        assertEquals(2L, map2.get("activeTransfers"));
        
        metrics.recordTransferFailure("ERROR");
        metrics.recordTransferSuccess(2048L, Duration.ofMillis(200));
        
        Map<String, Object> map3 = metrics.toMap();
        assertEquals(0L, map3.get("activeTransfers"));
    }

    @Test
    void testZeroSuccessfulTransfersDurationMetrics() {
        metrics.recordTransferStart();
        metrics.recordTransferFailure("ERROR");
        
        Map<String, Object> map = metrics.toMap();
        assertNull(map.get("averageDurationMs"));
        assertNull(map.get("minDurationMs"));
        assertNull(map.get("maxDurationMs"));
    }

    @Test
    void testNoErrorsWhenNoFailures() {
        metrics.recordTransferStart();
        metrics.recordTransferSuccess(1024L, Duration.ofMillis(100));
        
        Map<String, Object> map = metrics.toMap();
        assertNull(map.get("errors"));
    }

    @Test
    void testNoLastTransferInitially() {
        Map<String, Object> map = metrics.toMap();
        assertNull(map.get("lastTransferTime"));
        assertNull(map.get("lastTransferSuccess"));
    }

    @Test
    void testMixedSuccessAndFailure() {
        // Success
        metrics.recordTransferStart();
        metrics.recordTransferSuccess(1024L, Duration.ofMillis(100));
        
        // Failure
        metrics.recordTransferStart();
        metrics.recordTransferFailure("TIMEOUT");
        
        // Success
        metrics.recordTransferStart();
        metrics.recordTransferSuccess(2048L, Duration.ofMillis(150));
        
        Map<String, Object> map = metrics.toMap();
        assertEquals(3L, map.get("totalTransfers"));
        assertEquals(2L, map.get("successfulTransfers"));
        assertEquals(1L, map.get("failedTransfers"));
        assertEquals(3072L, map.get("totalBytesTransferred"));
        
        // Last transfer was successful
        assertEquals(true, map.get("lastTransferSuccess"));
    }

    @Test
    void testLargeByteCounts() {
        long oneGB = 1024L * 1024L * 1024L;
        
        metrics.recordTransferStart();
        metrics.recordTransferSuccess(oneGB, Duration.ofMillis(5000));
        
        metrics.recordTransferStart();
        metrics.recordTransferSuccess(2L * oneGB, Duration.ofMillis(10000));
        
        Map<String, Object> map = metrics.toMap();
        assertEquals(3L * oneGB, map.get("totalBytesTransferred"));
        
        String mbTransferred = (String) map.get("totalBytesTransferredMB");
        assertTrue(mbTransferred.contains("3072.00"));
    }

    @Test
    void testDurationEdgeCases() {
        // Very short duration
        metrics.recordTransferStart();
        metrics.recordTransferSuccess(1024L, Duration.ofMillis(1));
        
        Map<String, Object> map = metrics.toMap();
        assertEquals(1L, map.get("minDurationMs"));
        assertEquals(1L, map.get("maxDurationMs"));
        assertEquals(1L, map.get("averageDurationMs"));
    }

    @Test
    void testMultipleErrorTypes() {
        metrics.recordTransferStart();
        metrics.recordTransferFailure("TIMEOUT");
        
        metrics.recordTransferStart();
        metrics.recordTransferFailure("NETWORK_ERROR");
        
        metrics.recordTransferStart();
        metrics.recordTransferFailure("DISK_FULL");
        
        metrics.recordTransferStart();
        metrics.recordTransferFailure("TIMEOUT");
        
        Map<String, Object> map = metrics.toMap();
        
        @SuppressWarnings("unchecked")
        Map<String, Long> errors = (Map<String, Long>) map.get("errors");
        
        assertEquals(3, errors.size());
        assertEquals(2L, errors.get("TIMEOUT"));
        assertEquals(1L, errors.get("NETWORK_ERROR"));
        assertEquals(1L, errors.get("DISK_FULL"));
    }

    @Test
    void testSuccessRateWith100PercentSuccess() {
        metrics.recordTransferStart();
        metrics.recordTransferSuccess(1024L, Duration.ofMillis(100));
        
        metrics.recordTransferStart();
        metrics.recordTransferSuccess(2048L, Duration.ofMillis(200));
        
        Map<String, Object> map = metrics.toMap();
        String successRate = (String) map.get("successRate");
        assertTrue(successRate.contains("100.00"));
    }

    @Test
    void testSuccessRateWith0PercentSuccess() {
        metrics.recordTransferStart();
        metrics.recordTransferFailure("ERROR");
        
        metrics.recordTransferStart();
        metrics.recordTransferFailure("ERROR");
        
        Map<String, Object> map = metrics.toMap();
        String successRate = (String) map.get("successRate");
        assertTrue(successRate.contains("0.00"));
    }
}
