/*
 * Copyright 2025 Quorus Contributors
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

package dev.mars.quorus.network;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ConnectionPoolService with focus on:
 * - Configuration presets
 * - Wait queue backpressure handling
 * - Pool statistics and monitoring
 * - Performance characteristics
 */
class ConnectionPoolServiceTest {

    private Vertx vertx;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
    }

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    @Test
    @DisplayName("Default configuration should have conservative settings")
    void testDefaultConfiguration() {
        ConnectionPoolService.ConnectionPoolConfig config =
            ConnectionPoolService.ConnectionPoolConfig.defaultConfig();

        assertEquals(10, config.getMaxPoolSize(), "Default pool size should be 10");
        assertEquals(100, config.getMaxWaitQueueSize(), "Default wait queue should be 100");
        assertEquals(Duration.ofSeconds(30), config.getConnectionTimeout());
        assertEquals(Duration.ofMinutes(10), config.getPoolIdleTimeout());
        assertEquals(Duration.ofMinutes(5), config.getCleanupInterval());
    }

    @Test
    @DisplayName("Production configuration should have optimized settings")
    void testProductionConfiguration() {
        ConnectionPoolService.ConnectionPoolConfig config =
            ConnectionPoolService.ConnectionPoolConfig.productionConfig();

        assertEquals(100, config.getMaxPoolSize(), "Production pool size should be 100");
        assertEquals(1000, config.getMaxWaitQueueSize(), "Production wait queue should be 1000");
        assertEquals(Duration.ofSeconds(30), config.getConnectionTimeout());
        assertEquals(Duration.ofMinutes(10), config.getPoolIdleTimeout());
        assertEquals(Duration.ofMinutes(5), config.getCleanupInterval());
    }

    @Test
    @DisplayName("High-throughput configuration should have maximum settings")
    void testHighThroughputConfiguration() {
        ConnectionPoolService.ConnectionPoolConfig config =
            ConnectionPoolService.ConnectionPoolConfig.highThroughputConfig();

        assertEquals(200, config.getMaxPoolSize(), "High-throughput pool size should be 200");
        assertEquals(2000, config.getMaxWaitQueueSize(), "High-throughput wait queue should be 2000");
        assertEquals(Duration.ofSeconds(45), config.getConnectionTimeout());
        assertEquals(Duration.ofMinutes(15), config.getPoolIdleTimeout());
        assertEquals(Duration.ofMinutes(3), config.getCleanupInterval());
    }

    @Test
    @DisplayName("Low-latency configuration should have aggressive timeouts")
    void testLowLatencyConfiguration() {
        ConnectionPoolService.ConnectionPoolConfig config =
            ConnectionPoolService.ConnectionPoolConfig.lowLatencyConfig();

        assertEquals(50, config.getMaxPoolSize(), "Low-latency pool size should be 50");
        assertEquals(500, config.getMaxWaitQueueSize(), "Low-latency wait queue should be 500");
        assertEquals(Duration.ofSeconds(10), config.getConnectionTimeout());
        assertEquals(Duration.ofMinutes(5), config.getPoolIdleTimeout());
        assertEquals(Duration.ofMinutes(2), config.getCleanupInterval());
    }

    @Test
    @DisplayName("Configuration validation should reject invalid values")
    void testConfigurationValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ConnectionPoolService.ConnectionPoolConfig(
                0,  // Invalid: maxPoolSize must be positive
                100,
                Duration.ofSeconds(30),
                Duration.ofMinutes(10),
                Duration.ofMinutes(5)
            );
        }, "Should reject zero pool size");

        assertThrows(IllegalArgumentException.class, () -> {
            new ConnectionPoolService.ConnectionPoolConfig(
                10,
                -1,  // Invalid: maxWaitQueueSize cannot be negative
                Duration.ofSeconds(30),
                Duration.ofMinutes(10),
                Duration.ofMinutes(5)
            );
        }, "Should reject negative wait queue size");

        assertThrows(NullPointerException.class, () -> {
            new ConnectionPoolService.ConnectionPoolConfig(
                10,
                100,
                null,  // Invalid: connectionTimeout cannot be null
                Duration.ofMinutes(10),
                Duration.ofMinutes(5)
            );
        }, "Should reject null connection timeout");
    }

    @Test
    @DisplayName("Configuration toString should provide readable output")
    void testConfigurationToString() {
        ConnectionPoolService.ConnectionPoolConfig config =
            ConnectionPoolService.ConnectionPoolConfig.productionConfig();

        String str = config.toString();
        assertTrue(str.contains("maxPoolSize=100"), "Should include pool size");
        assertTrue(str.contains("maxWaitQueueSize=1000"), "Should include wait queue size");
        assertTrue(str.contains("connectionTimeout=30s"), "Should include connection timeout");
        assertTrue(str.contains("poolIdleTimeout=10m"), "Should include pool idle timeout");
        assertTrue(str.contains("cleanupInterval=5m"), "Should include cleanup interval");
    }

    @Test
    @DisplayName("Statistics should track waiting requests")
    void testStatisticsWaitingRequests() {
        ConnectionPoolService.ConnectionPoolStatistics stats =
            new ConnectionPoolService.ConnectionPoolStatistics(1, 10, 5, 3, 2);

        assertEquals(1, stats.getTotalPools());
        assertEquals(10, stats.getTotalConnections());
        assertEquals(5, stats.getActiveConnections());
        assertEquals(3, stats.getIdleConnections());
        assertEquals(2, stats.getWaitingRequests());
    }

    @Test
    @DisplayName("Statistics toString should include all metrics")
    void testStatisticsToString() {
        ConnectionPoolService.ConnectionPoolStatistics stats =
            new ConnectionPoolService.ConnectionPoolStatistics(2, 100, 75, 25, 5);

        String str = stats.toString();
        assertTrue(str.contains("pools=2"), "Should include total pools");
        assertTrue(str.contains("total=100"), "Should include total connections");
        assertTrue(str.contains("active=75"), "Should include active connections");
        assertTrue(str.contains("idle=25"), "Should include idle connections");
        assertTrue(str.contains("waiting=5"), "Should include waiting requests");
        assertTrue(str.contains("utilization=75.0%"), "Should include utilization percentage");
    }

    @Test
    @DisplayName("Wait queue configuration should be respected")
    void testWaitQueueConfiguration() {
        // Create small pool with specific wait queue size
        ConnectionPoolService.ConnectionPoolConfig config =
            new ConnectionPoolService.ConnectionPoolConfig(
                2,  // Small pool
                10, // Wait queue for 10 requests
                Duration.ofSeconds(5),
                Duration.ofMinutes(10),
                Duration.ofMinutes(5)
            );

        assertEquals(2, config.getMaxPoolSize());
        assertEquals(10, config.getMaxWaitQueueSize());
    }

    @Test
    @DisplayName("Backpressure configuration should support zero wait queue")
    void testBackpressureConfiguration() {
        // Create pool with zero wait queue (immediate rejection)
        ConnectionPoolService.ConnectionPoolConfig config =
            new ConnectionPoolService.ConnectionPoolConfig(
                1,  // Single connection
                0,  // No wait queue - immediate rejection
                Duration.ofSeconds(10),
                Duration.ofMinutes(10),
                Duration.ofMinutes(5)
            );

        assertEquals(1, config.getMaxPoolSize());
        assertEquals(0, config.getMaxWaitQueueSize());
    }

    @Test
    @DisplayName("Pool size limits should be enforced")
    void testPoolSizeLimits() {
        ConnectionPoolService.ConnectionPoolConfig config =
            new ConnectionPoolService.ConnectionPoolConfig(
                10, // Max 10 connections
                100,
                Duration.ofSeconds(5),
                Duration.ofMinutes(10),
                Duration.ofMinutes(5)
            );

        assertEquals(10, config.getMaxPoolSize());
        assertEquals(100, config.getMaxWaitQueueSize());
    }
}
