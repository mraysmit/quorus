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

package dev.mars.quorus.tenant.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TenantConfigurationTest {
    
    @Test
    void testBuilderWithAllFields() {
        Set<String> protocols = Set.of("http", "https", "sftp");
        
        TenantConfiguration config = TenantConfiguration.builder()
                .tenantId("test-tenant")
                .maxConcurrentTransfers(20)
                .maxBandwidthBytesPerSecond(100L * 1024 * 1024)
                .maxStorageBytes(1024L * 1024 * 1024 * 1024)
                .maxDailyTransfers(1000)
                .maxTransferSizeBytes(10L * 1024 * 1024 * 1024)
                .allowedProtocols(protocols)
                .requireAuthentication(true)
                .enableAuditLogging(true)
                .transferTimeoutSeconds(3600)
                .retryAttempts(5)
                .build();
        
        assertEquals("test-tenant", config.getTenantId());
        assertEquals(20, config.getMaxConcurrentTransfers());
        assertEquals(100L * 1024 * 1024, config.getMaxBandwidthBytesPerSecond());
        assertEquals(1024L * 1024 * 1024 * 1024, config.getMaxStorageBytes());
        assertEquals(1000, config.getMaxDailyTransfers());
        assertEquals(10L * 1024 * 1024 * 1024, config.getMaxTransferSizeBytes());
        assertEquals(protocols, config.getAllowedProtocols());
        assertTrue(config.isRequireAuthentication());
        assertTrue(config.isEnableAuditLogging());
        assertEquals(3600, config.getTransferTimeoutSeconds());
        assertEquals(5, config.getRetryAttempts());
    }
    
    @Test
    void testBuilderWithMinimalFields() {
        TenantConfiguration config = TenantConfiguration.builder()
                .tenantId("minimal-tenant")
                .build();
        
        assertEquals("minimal-tenant", config.getTenantId());
        
        // Should have default values
        assertEquals(10, config.getMaxConcurrentTransfers());
        assertEquals(100L * 1024 * 1024, config.getMaxBandwidthBytesPerSecond()); // 100 MB/s
        assertEquals(1024L * 1024 * 1024 * 1024, config.getMaxStorageBytes()); // 1 TB
        assertEquals(1000, config.getMaxDailyTransfers());
        assertEquals(10L * 1024 * 1024 * 1024, config.getMaxTransferSizeBytes()); // 10 GB
        assertNotNull(config.getAllowedProtocols());
        assertFalse(config.getAllowedProtocols().isEmpty());
        assertFalse(config.isRequireAuthentication());
        assertTrue(config.isEnableAuditLogging());
        assertEquals(3600, config.getTransferTimeoutSeconds());
        assertEquals(3, config.getRetryAttempts());
    }
    
    @Test
    void testBuilderRequiredFields() {
        // TenantId is required
        assertThrows(NullPointerException.class, () -> {
            TenantConfiguration.builder().build();
        });
    }
    
    @Test
    void testDefaultProtocols() {
        TenantConfiguration config = TenantConfiguration.builder()
                .tenantId("protocol-test")
                .build();
        
        Set<String> protocols = config.getAllowedProtocols();
        
        assertNotNull(protocols);
        assertTrue(protocols.contains("http"));
        assertTrue(protocols.contains("https"));
        assertTrue(protocols.contains("sftp"));
        assertTrue(protocols.contains("ftp"));
    }
    
    @Test
    void testCustomProtocols() {
        Set<String> customProtocols = Set.of("https", "sftp");
        
        TenantConfiguration config = TenantConfiguration.builder()
                .tenantId("custom-protocols")
                .allowedProtocols(customProtocols)
                .build();
        
        assertEquals(customProtocols, config.getAllowedProtocols());
        assertTrue(config.getAllowedProtocols().contains("https"));
        assertTrue(config.getAllowedProtocols().contains("sftp"));
        assertFalse(config.getAllowedProtocols().contains("http"));
        assertFalse(config.getAllowedProtocols().contains("ftp"));
    }
    
    @Test
    void testMergeWithParent() {
        // Parent configuration
        TenantConfiguration parent = TenantConfiguration.builder()
                .tenantId("parent")
                .maxConcurrentTransfers(50)
                .maxBandwidthBytesPerSecond(500L * 1024 * 1024)
                .allowedProtocols(Set.of("http", "https", "sftp"))
                .requireAuthentication(true)
                .enableAuditLogging(true)
                .transferTimeoutSeconds(7200)
                .retryAttempts(5)
                .build();
        
        // Child configuration with some overrides
        TenantConfiguration child = TenantConfiguration.builder()
                .tenantId("child")
                .maxConcurrentTransfers(25) // Override
                .allowedProtocols(Set.of("https", "sftp")) // Override
                .build();
        
        TenantConfiguration merged = child.mergeWithParent(parent);
        
        assertEquals("child", merged.getTenantId());
        assertEquals(25, merged.getMaxConcurrentTransfers()); // Child override
        assertEquals(500L * 1024 * 1024, merged.getMaxBandwidthBytesPerSecond()); // Parent value
        assertEquals(Set.of("https", "sftp"), merged.getAllowedProtocols()); // Child override
        assertTrue(merged.isRequireAuthentication()); // Parent value
        assertTrue(merged.isEnableAuditLogging()); // Parent value
        assertEquals(7200, merged.getTransferTimeoutSeconds()); // Parent value
        assertEquals(5, merged.getRetryAttempts()); // Parent value
    }
    
    @Test
    void testMergeWithNullParent() {
        TenantConfiguration child = TenantConfiguration.builder()
                .tenantId("orphan")
                .maxConcurrentTransfers(15)
                .build();
        
        TenantConfiguration merged = child.mergeWithParent(null);
        
        // Should return the child configuration unchanged
        assertEquals(child.getTenantId(), merged.getTenantId());
        assertEquals(child.getMaxConcurrentTransfers(), merged.getMaxConcurrentTransfers());
        assertEquals(child.getMaxBandwidthBytesPerSecond(), merged.getMaxBandwidthBytesPerSecond());
    }
    
    @Test
    void testToBuilder() {
        TenantConfiguration original = TenantConfiguration.builder()
                .tenantId("original")
                .maxConcurrentTransfers(20)
                .maxBandwidthBytesPerSecond(200L * 1024 * 1024)
                .allowedProtocols(Set.of("http", "https"))
                .requireAuthentication(false)
                .build();
        
        TenantConfiguration modified = original.toBuilder()
                .maxConcurrentTransfers(30)
                .requireAuthentication(true)
                .build();
        
        // Modified fields
        assertEquals(30, modified.getMaxConcurrentTransfers());
        assertTrue(modified.isRequireAuthentication());
        
        // Unchanged fields
        assertEquals("original", modified.getTenantId());
        assertEquals(200L * 1024 * 1024, modified.getMaxBandwidthBytesPerSecond());
        assertEquals(Set.of("http", "https"), modified.getAllowedProtocols());
    }
    
    @Test
    void testEqualsAndHashCode() {
        TenantConfiguration config1 = TenantConfiguration.builder()
                .tenantId("test-config")
                .maxConcurrentTransfers(20)
                .build();
        
        TenantConfiguration config2 = TenantConfiguration.builder()
                .tenantId("test-config")
                .maxConcurrentTransfers(30) // Different value
                .build();
        
        TenantConfiguration config3 = TenantConfiguration.builder()
                .tenantId("different-config")
                .maxConcurrentTransfers(20)
                .build();
        
        // Configurations with same tenant ID should be equal
        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
        
        // Configurations with different tenant IDs should not be equal
        assertNotEquals(config1, config3);
        assertNotEquals(config1.hashCode(), config3.hashCode());
        
        // Test with null
        assertNotEquals(config1, null);
        
        // Test reflexivity
        assertEquals(config1, config1);
    }
    
    @Test
    void testToString() {
        TenantConfiguration config = TenantConfiguration.builder()
                .tenantId("test-config")
                .maxConcurrentTransfers(20)
                .maxBandwidthBytesPerSecond(100L * 1024 * 1024)
                .allowedProtocols(Set.of("http", "https"))
                .requireAuthentication(true)
                .build();
        
        String toString = config.toString();
        
        assertNotNull(toString);
        assertTrue(toString.contains("test-config"));
        assertTrue(toString.contains("20"));
        assertTrue(toString.contains("true"));
    }
    
    @Test
    void testBandwidthLimits() {
        // Test various bandwidth limits
        long[] bandwidths = {
                1024 * 1024,           // 1 MB/s
                10L * 1024 * 1024,     // 10 MB/s
                100L * 1024 * 1024,    // 100 MB/s
                1024L * 1024 * 1024    // 1 GB/s
        };
        
        for (long bandwidth : bandwidths) {
            TenantConfiguration config = TenantConfiguration.builder()
                    .tenantId("bandwidth-test")
                    .maxBandwidthBytesPerSecond(bandwidth)
                    .build();
            
            assertEquals(bandwidth, config.getMaxBandwidthBytesPerSecond());
        }
    }
    
    @Test
    void testStorageLimits() {
        // Test various storage limits
        long[] storageLimits = {
                1024L * 1024 * 1024,           // 1 GB
                10L * 1024 * 1024 * 1024,      // 10 GB
                100L * 1024 * 1024 * 1024,     // 100 GB
                1024L * 1024 * 1024 * 1024     // 1 TB
        };
        
        for (long storageLimit : storageLimits) {
            TenantConfiguration config = TenantConfiguration.builder()
                    .tenantId("storage-test")
                    .maxStorageBytes(storageLimit)
                    .build();
            
            assertEquals(storageLimit, config.getMaxStorageBytes());
        }
    }
    
    @Test
    void testConcurrencyLimits() {
        // Test various concurrency limits
        int[] concurrencyLimits = {1, 5, 10, 20, 50, 100};
        
        for (int limit : concurrencyLimits) {
            TenantConfiguration config = TenantConfiguration.builder()
                    .tenantId("concurrency-test")
                    .maxConcurrentTransfers(limit)
                    .build();
            
            assertEquals(limit, config.getMaxConcurrentTransfers());
        }
    }
    
    @Test
    void testTimeoutSettings() {
        // Test various timeout settings
        int[] timeouts = {300, 1800, 3600, 7200, 14400}; // 5min to 4hours
        
        for (int timeout : timeouts) {
            TenantConfiguration config = TenantConfiguration.builder()
                    .tenantId("timeout-test")
                    .transferTimeoutSeconds(timeout)
                    .build();
            
            assertEquals(timeout, config.getTransferTimeoutSeconds());
        }
    }
    
    @Test
    void testRetrySettings() {
        // Test various retry settings
        int[] retryAttempts = {0, 1, 3, 5, 10};
        
        for (int retries : retryAttempts) {
            TenantConfiguration config = TenantConfiguration.builder()
                    .tenantId("retry-test")
                    .retryAttempts(retries)
                    .build();
            
            assertEquals(retries, config.getRetryAttempts());
        }
    }
}
