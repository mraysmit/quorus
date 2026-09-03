package dev.mars.quorus.config;

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

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for QuorusConfiguration.
 * Validates configuration loading, property access, type conversion, and default values.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-01-20
 * @version 1.0
 */
class QuorusConfigurationTest {

    private QuorusConfiguration config;

    @BeforeEach
    void setUp() {
        config = new QuorusConfiguration("test", new Properties());
    }

    // ========== Default Configuration Tests ==========

    @Test
    void testDefaultMaxConcurrentTransfers() {
        assertEquals(10, config.getMaxConcurrentTransfers());
    }

    @Test
    void testDefaultMaxRetryAttempts() {
        assertEquals(3, config.getMaxRetryAttempts());
    }

    @Test
    void testDefaultRetryDelayMs() {
        assertEquals(1000, config.getRetryDelayMs());
    }

    @Test
    void testDefaultBufferSize() {
        assertEquals(8192, config.getBufferSize());
    }

    @Test
    void testDefaultConnectionTimeoutMs() {
        assertEquals(30000, config.getConnectionTimeoutMs());
    }

    @Test
    void testDefaultReadTimeoutMs() {
        assertEquals(60000, config.getReadTimeoutMs());
    }

    @Test
    void testDefaultMaxFileSize() {
        assertEquals(10L * 1024 * 1024 * 1024, config.getMaxFileSize()); // 10GB
    }

    @Test
    void testDefaultChecksumAlgorithm() {
        assertEquals("SHA-256", config.getChecksumAlgorithm());
    }

    @Test
    void testDefaultTempDirectory() {
        assertEquals(System.getProperty("java.io.tmpdir"), config.getTempDirectory());
    }

    @Test
    void testDefaultMetricsEnabled() {
        assertTrue(config.isMetricsEnabled());
    }

    @Test
    void testDefaultHealthCheckEnabled() {
        assertTrue(config.isHealthCheckEnabled());
    }

    @Test
    void testDefaultStateCleanupIntervalMs() {
        assertEquals(3600000, config.getStateCleanupIntervalMs()); // 1 hour
    }

    @Test
    void testDefaultMaxStateAgeMs() {
        assertEquals(86400000, config.getMaxStateAgeMs()); // 24 hours
    }

    // ========== Constructor with Properties Tests ==========

    @Test
    void testConstructorWithProperties() {
        Properties props = new Properties();
        props.setProperty("quorus.transfer.max.concurrent", "20");
        props.setProperty("quorus.transfer.max.retries", "5");
        props.setProperty("quorus.file.checksum.algorithm", "MD5");

        QuorusConfiguration customConfig = new QuorusConfiguration("test", props);

        assertEquals(20, customConfig.getMaxConcurrentTransfers());
        assertEquals(5, customConfig.getMaxRetryAttempts());
        assertEquals("MD5", customConfig.getChecksumAlgorithm());
    }

    @Test
    void testConstructorRejectsNullProperties() {
        assertThrows(NullPointerException.class, () -> new QuorusConfiguration("test", null));
    }

    @Test
    void testConstructorWithEmptyProperties() {
        Properties props = new Properties();
        QuorusConfiguration customConfig = new QuorusConfiguration("test", props);

        // Should use defaults
        assertEquals(10, customConfig.getMaxConcurrentTransfers());
        assertEquals(3, customConfig.getMaxRetryAttempts());
    }

    // ========== Property Override Tests ==========

    @Test
    void testSetAndGetProperty() {
        assertEquals("custom-value", configWith("custom.property", "custom-value")
                .getProperty("custom.property"));
    }

    @Test
    void testGetPropertyWithDefault() {
        assertNull(config.getProperty("nonexistent.property"));
        assertEquals("default-value", config.getProperty("nonexistent.property", "default-value"));
    }

    @Test
    void testOverrideMaxConcurrentTransfers() {
        assertEquals(25, configWith("quorus.transfer.max.concurrent", "25").getMaxConcurrentTransfers());
    }

    @Test
    void testOverrideRetryDelayMs() {
        assertEquals(2500, configWith("quorus.transfer.retry.delay.ms", "2500").getRetryDelayMs());
    }

    @Test
    void testOverrideMaxFileSize() {
        assertEquals(5368709120L, configWith("quorus.file.max.size", "5368709120").getMaxFileSize());
    }

    @Test
    void testOverrideChecksumAlgorithm() {
        assertEquals("SHA-512", configWith("quorus.file.checksum.algorithm", "SHA-512")
                .getChecksumAlgorithm());
    }

    @Test
    void testOverrideTempDirectory() {
        assertEquals("/custom/temp", configWith("quorus.file.temp.dir", "/custom/temp").getTempDirectory());
    }

    @Test
    void testOverrideMetricsEnabled() {
        assertFalse(configWith("quorus.monitoring.metrics.enabled", "false").isMetricsEnabled());
    }

    @Test
    void testOverrideHealthCheckEnabled() {
        assertFalse(configWith("quorus.monitoring.health.enabled", "false").isHealthCheckEnabled());
    }

    // ========== Type Conversion Tests ==========

    @Test
    void testInvalidIntegerPropertyUsesDefault() {
        assertEquals(10, configWith("quorus.transfer.max.concurrent", "not-a-number")
                .getMaxConcurrentTransfers());
    }

    @Test
    void testInvalidLongPropertyUsesDefault() {
        assertEquals(10L * 1024 * 1024 * 1024,
                configWith("quorus.file.max.size", "invalid-long").getMaxFileSize());
    }

    @Test
    void testBooleanPropertyTrueVariations() {
        assertTrue(configWith("quorus.monitoring.metrics.enabled", "true").isMetricsEnabled());
        assertTrue(configWith("quorus.monitoring.metrics.enabled", "TRUE").isMetricsEnabled());
        assertTrue(configWith("quorus.monitoring.metrics.enabled", "True").isMetricsEnabled());
    }

    @Test
    void testBooleanPropertyFalseVariations() {
        assertFalse(configWith("quorus.monitoring.metrics.enabled", "false").isMetricsEnabled());
        assertFalse(configWith("quorus.monitoring.metrics.enabled", "FALSE").isMetricsEnabled());
        assertFalse(configWith("quorus.monitoring.metrics.enabled", "anything-else").isMetricsEnabled());
    }

    @Test
    void testIntegerPropertyWithWhitespace() {
        assertEquals(15, configWith("quorus.transfer.max.concurrent", "  15  ").getMaxConcurrentTransfers());
    }

    @Test
    void testLongPropertyWithWhitespace() {
        assertEquals(3000, configWith("quorus.transfer.retry.delay.ms", "  3000  ").getRetryDelayMs());
    }

    @Test
    void testBooleanPropertyWithWhitespace() {
        assertTrue(configWith("quorus.monitoring.metrics.enabled", "  true  ").isMetricsEnabled());
    }

    // ========== Boundary Value Tests ==========

    @Test
    void testMaximumIntValue() {
        assertEquals(Integer.MAX_VALUE,
                configWith("quorus.transfer.max.concurrent", String.valueOf(Integer.MAX_VALUE))
                        .getMaxConcurrentTransfers());
    }

    @Test
    void testMinimumIntValue() {
        assertEquals(0, configWith("quorus.transfer.max.retries", "0").getMaxRetryAttempts());
    }

    @Test
    void testNegativeIntValue() {
        assertEquals(-5, configWith("quorus.transfer.max.concurrent", "-5").getMaxConcurrentTransfers());
    }

    @Test
    void testMaximumLongValue() {
        assertEquals(Long.MAX_VALUE, configWith("quorus.file.max.size", String.valueOf(Long.MAX_VALUE))
                .getMaxFileSize());
    }

    @Test
    void testZeroLongValue() {
        assertEquals(0, configWith("quorus.transfer.retry.delay.ms", "0").getRetryDelayMs());
    }

    // ========== toString() Tests ==========

    @Test
    void testToStringContainsKeyInformation() {
        String configStr = config.toString();

        assertTrue(configStr.contains("QuorusConfiguration"));
        assertTrue(configStr.contains("maxConcurrentTransfers=10"));
        assertTrue(configStr.contains("maxRetryAttempts=3"));
        assertTrue(configStr.contains("checksumAlgorithm='SHA-256'"));
        assertTrue(configStr.contains("metricsEnabled=true"));
    }

    @Test
    void testToStringWithCustomValues() {
        Properties overrides = new Properties();
        overrides.setProperty("quorus.transfer.max.concurrent", "50");
        overrides.setProperty("quorus.transfer.max.retries", "7");
        overrides.setProperty("quorus.monitoring.metrics.enabled", "false");
        String configStr = new QuorusConfiguration("test", overrides).toString();

        assertTrue(configStr.contains("maxConcurrentTransfers=50"));
        assertTrue(configStr.contains("maxRetryAttempts=7"));
        assertTrue(configStr.contains("metricsEnabled=false"));
    }

    // ========== Configuration isolation tests ==========

    @Test
    void testSystemPropertyIsNotAConfigurationChannel() {
        String key = "quorus.transfer.max.concurrent";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "100");
            assertEquals(10, new QuorusConfiguration("test", new Properties()).getMaxConcurrentTransfers());
        } finally {
            if (previous == null) System.clearProperty(key);
            else System.setProperty(key, previous);
        }
    }

    @Test
    void testConfigurationInstancesRemainIsolated() {
        QuorusConfiguration first = configWith("quorus.transfer.max.concurrent", "99");
        QuorusConfiguration second = configWith("quorus.transfer.max.concurrent", "7");
        assertEquals(99, first.getMaxConcurrentTransfers());
        assertEquals(7, second.getMaxConcurrentTransfers());
    }

    // ========== Edge Cases ==========

    @Test
    void testEmptyStringProperty() {
        assertEquals("", configWith("quorus.file.checksum.algorithm", "").getChecksumAlgorithm());
    }

    @Test
    void testNullStringPropertyUsesDefault() {
        // Setting null should use default
        String result = config.getProperty("nonexistent.key", "default");
        assertEquals("default", result);
    }

    @Test
    void testAllConfigurationGettersInvoked() {
        // Ensure all getters can be called without exceptions
        assertNotNull(config.getMaxConcurrentTransfers());
        assertNotNull(config.getMaxRetryAttempts());
        assertNotNull(config.getRetryDelayMs());
        assertNotNull(config.getBufferSize());
        assertNotNull(config.getConnectionTimeoutMs());
        assertNotNull(config.getReadTimeoutMs());
        assertNotNull(config.getMaxFileSize());
        assertNotNull(config.getChecksumAlgorithm());
        assertNotNull(config.getTempDirectory());
        assertNotNull(config.isMetricsEnabled());
        assertNotNull(config.isHealthCheckEnabled());
        assertNotNull(config.getStateCleanupIntervalMs());
        assertNotNull(config.getMaxStateAgeMs());
    }

    @Test
    void testConfigurationDefensivelyCopiesOverrides() {
        Properties overrides = new Properties();
        overrides.setProperty("quorus.transfer.max.concurrent", "9");
        QuorusConfiguration isolated = new QuorusConfiguration("test", overrides);
        overrides.setProperty("quorus.transfer.max.concurrent", "999");
        assertEquals(9, isolated.getMaxConcurrentTransfers());
    }

    private static QuorusConfiguration configWith(String key, String value) {
        Properties overrides = new Properties();
        overrides.setProperty(key, value);
        return new QuorusConfiguration("test", overrides);
    }
}
