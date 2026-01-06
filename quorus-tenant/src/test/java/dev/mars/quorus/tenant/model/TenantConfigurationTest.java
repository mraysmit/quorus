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

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TenantConfigurationTest implementation for the Quorus file transfer system.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-19
 * @version 1.0
 */
public class TenantConfigurationTest {

    @Test
    public void testBuilderWithAllFields() {
        Set<String> protocols = Set.of("http", "https", "sftp");

        TenantConfiguration.ResourceLimits resourceLimits = TenantConfiguration.ResourceLimits.builder()
                .maxConcurrentTransfers(20)
                .maxBandwidthBytesPerSecond(100L * 1024 * 1024)
                .maxStorageBytes(1024L * 1024 * 1024 * 1024)
                .maxTransfersPerDay(1000)
                .maxTransferSizeBytes(10L * 1024 * 1024 * 1024)
                .build();

        TenantConfiguration.TransferPolicies transferPolicies = TenantConfiguration.TransferPolicies.builder()
                .allowedProtocols(protocols)
                .defaultTimeout(Duration.ofSeconds(3600))
                .defaultRetryAttempts(5)
                .build();

        TenantConfiguration.SecuritySettings securitySettings = TenantConfiguration.SecuritySettings.builder()
                .requireAuthentication(true)
                .enableAuditLogging(true)
                .build();

        TenantConfiguration config = TenantConfiguration.builder()
                .resourceLimits(resourceLimits)
                .transferPolicies(transferPolicies)
                .securitySettings(securitySettings)
                .build();

        assertEquals(20, config.getResourceLimits().getMaxConcurrentTransfers());
        assertEquals(100L * 1024 * 1024, config.getResourceLimits().getMaxBandwidthBytesPerSecond());
        assertEquals(1024L * 1024 * 1024 * 1024, config.getResourceLimits().getMaxStorageBytes());
        assertEquals(1000, config.getResourceLimits().getMaxTransfersPerDay());
        assertEquals(10L * 1024 * 1024 * 1024, config.getResourceLimits().getMaxTransferSizeBytes());
        assertEquals(protocols, config.getTransferPolicies().getAllowedProtocols());
        assertTrue(config.getSecuritySettings().isRequireAuthentication());
        assertTrue(config.getSecuritySettings().isEnableAuditLogging());
        assertEquals(Duration.ofSeconds(3600), config.getTransferPolicies().getDefaultTimeout());
        assertEquals(5, config.getTransferPolicies().getDefaultRetryAttempts());
    }

    @Test
    public void testBuilderWithMinimalFields() {
        TenantConfiguration config = TenantConfiguration.builder()
                .build();

        // Should have default values
        assertEquals(10, config.getResourceLimits().getMaxConcurrentTransfers());
        assertEquals(100L * 1024 * 1024, config.getResourceLimits().getMaxBandwidthBytesPerSecond()); // 100 MB/s
        assertEquals(10L * 1024 * 1024 * 1024, config.getResourceLimits().getMaxStorageBytes()); // 10 GB
        assertEquals(1000, config.getResourceLimits().getMaxTransfersPerDay());
        assertEquals(1024L * 1024 * 1024, config.getResourceLimits().getMaxTransferSizeBytes()); // 1 GB
        assertNotNull(config.getTransferPolicies().getAllowedProtocols());
        assertFalse(config.getTransferPolicies().getAllowedProtocols().isEmpty());
        assertTrue(config.getSecuritySettings().isRequireAuthentication());
        assertTrue(config.getSecuritySettings().isEnableAuditLogging());
        assertEquals(Duration.ofMinutes(30), config.getTransferPolicies().getDefaultTimeout());
        assertEquals(3, config.getTransferPolicies().getDefaultRetryAttempts());
    }

    @Test
    public void testBuilderRequiredFields() {
        // No required fields in the current implementation
        assertDoesNotThrow(() -> {
            TenantConfiguration.builder().build();
        });
    }

    @Test
    public void testDefaultProtocols() {
        TenantConfiguration config = TenantConfiguration.builder()
                .build();

        Set<String> protocols = config.getTransferPolicies().getAllowedProtocols();

        assertNotNull(protocols);
        assertTrue(protocols.contains("http"));
        assertTrue(protocols.contains("https"));
    }

    @Test
    public void testCustomProtocols() {
        Set<String> customProtocols = Set.of("https", "sftp");

        TenantConfiguration.TransferPolicies transferPolicies = TenantConfiguration.TransferPolicies.builder()
                .allowedProtocols(customProtocols)
                .build();

        TenantConfiguration config = TenantConfiguration.builder()
                .transferPolicies(transferPolicies)
                .build();

        assertEquals(customProtocols, config.getTransferPolicies().getAllowedProtocols());
        assertTrue(config.getTransferPolicies().getAllowedProtocols().contains("https"));
        assertTrue(config.getTransferPolicies().getAllowedProtocols().contains("sftp"));
        assertFalse(config.getTransferPolicies().getAllowedProtocols().contains("http"));
        assertFalse(config.getTransferPolicies().getAllowedProtocols().contains("ftp"));
    }

    @Test
    public void testResourceLimitsBuilder() {
        TenantConfiguration.ResourceLimits resourceLimits = TenantConfiguration.ResourceLimits.builder()
                .maxConcurrentTransfers(50)
                .maxBandwidthBytesPerSecond(500L * 1024 * 1024)
                .maxStorageBytes(2048L * 1024 * 1024 * 1024)
                .maxTransfersPerDay(2000)
                .maxTransferSizeBytes(5L * 1024 * 1024 * 1024)
                .build();

        assertEquals(50, resourceLimits.getMaxConcurrentTransfers());
        assertEquals(500L * 1024 * 1024, resourceLimits.getMaxBandwidthBytesPerSecond());
        assertEquals(2048L * 1024 * 1024 * 1024, resourceLimits.getMaxStorageBytes());
        assertEquals(2000, resourceLimits.getMaxTransfersPerDay());
        assertEquals(5L * 1024 * 1024 * 1024, resourceLimits.getMaxTransferSizeBytes());
    }

    @Test
    public void testTransferPoliciesBuilder() {
        Set<String> protocols = Set.of("https", "sftp");
        Set<String> sourcePatterns = Set.of("/allowed/source/*");
        Set<String> destPatterns = Set.of("/allowed/dest/*");

        TenantConfiguration.TransferPolicies transferPolicies = TenantConfiguration.TransferPolicies.builder()
                .allowedProtocols(protocols)
                .allowedSourcePatterns(sourcePatterns)
                .allowedDestinationPatterns(destPatterns)
                .defaultTimeout(Duration.ofHours(2))
                .defaultRetryAttempts(5)
                .requireChecksumVerification(false)
                .build();

        assertEquals(protocols, transferPolicies.getAllowedProtocols());
        assertEquals(sourcePatterns, transferPolicies.getAllowedSourcePatterns());
        assertEquals(destPatterns, transferPolicies.getAllowedDestinationPatterns());
        assertEquals(Duration.ofHours(2), transferPolicies.getDefaultTimeout());
        assertEquals(5, transferPolicies.getDefaultRetryAttempts());
        assertFalse(transferPolicies.isRequireChecksumVerification());
    }

    @Test
    public void testSecuritySettingsBuilder() {
        Set<String> roles = Set.of("admin", "user");

        TenantConfiguration.SecuritySettings securitySettings = TenantConfiguration.SecuritySettings.builder()
                .requireAuthentication(false)
                .allowedRoles(roles)
                .addAuthenticationProvider("ldap", "ldap://server")
                .enableAuditLogging(false)
                .build();

        assertFalse(securitySettings.isRequireAuthentication());
        assertEquals(roles, securitySettings.getAllowedRoles());
        assertTrue(securitySettings.getAuthenticationProviders().containsKey("ldap"));
        assertEquals("ldap://server", securitySettings.getAuthenticationProviders().get("ldap"));
        assertFalse(securitySettings.isEnableAuditLogging());
    }

    @Test
    public void testCustomSettings() {
        TenantConfiguration config = TenantConfiguration.builder()
                .addCustomSetting("maxRetentionDays", 30)
                .addCustomSetting("compressionEnabled", true)
                .addCustomSetting("notificationEmail", "admin@example.com")
                .build();

        assertEquals(30, config.getCustomSettings().get("maxRetentionDays"));
        assertEquals(true, config.getCustomSettings().get("compressionEnabled"));
        assertEquals("admin@example.com", config.getCustomSettings().get("notificationEmail"));
        assertEquals(3, config.getCustomSettings().size());
    }

    @Test
    public void testBandwidthLimits() {
        // Test various bandwidth limits
        long[] bandwidths = {
                1024 * 1024,           // 1 MB/s
                10L * 1024 * 1024,     // 10 MB/s
                100L * 1024 * 1024,    // 100 MB/s
                1024L * 1024 * 1024    // 1 GB/s
        };

        for (long bandwidth : bandwidths) {
            TenantConfiguration.ResourceLimits resourceLimits = TenantConfiguration.ResourceLimits.builder()
                    .maxBandwidthBytesPerSecond(bandwidth)
                    .build();

            TenantConfiguration config = TenantConfiguration.builder()
                    .resourceLimits(resourceLimits)
                    .build();

            assertEquals(bandwidth, config.getResourceLimits().getMaxBandwidthBytesPerSecond());
        }
    }

    @Test
    public void testStorageLimits() {
        // Test various storage limits
        long[] storageLimits = {
                1024L * 1024 * 1024,           // 1 GB
                10L * 1024 * 1024 * 1024,      // 10 GB
                100L * 1024 * 1024 * 1024,     // 100 GB
                1024L * 1024 * 1024 * 1024     // 1 TB
        };

        for (long storageLimit : storageLimits) {
            TenantConfiguration.ResourceLimits resourceLimits = TenantConfiguration.ResourceLimits.builder()
                    .maxStorageBytes(storageLimit)
                    .build();

            TenantConfiguration config = TenantConfiguration.builder()
                    .resourceLimits(resourceLimits)
                    .build();

            assertEquals(storageLimit, config.getResourceLimits().getMaxStorageBytes());
        }
    }

    @Test
    public void testConcurrencyLimits() {
        // Test various concurrency limits
        int[] concurrencyLimits = {1, 5, 10, 20, 50, 100};

        for (int limit : concurrencyLimits) {
            TenantConfiguration.ResourceLimits resourceLimits = TenantConfiguration.ResourceLimits.builder()
                    .maxConcurrentTransfers(limit)
                    .build();

            TenantConfiguration config = TenantConfiguration.builder()
                    .resourceLimits(resourceLimits)
                    .build();

            assertEquals(limit, config.getResourceLimits().getMaxConcurrentTransfers());
        }
    }

    @Test
    public void testTimeoutSettings() {
        // Test various timeout settings
        Duration[] timeouts = {
                Duration.ofMinutes(5),
                Duration.ofMinutes(30),
                Duration.ofHours(1),
                Duration.ofHours(2),
                Duration.ofHours(4)
        };

        for (Duration timeout : timeouts) {
            TenantConfiguration.TransferPolicies transferPolicies = TenantConfiguration.TransferPolicies.builder()
                    .defaultTimeout(timeout)
                    .build();

            TenantConfiguration config = TenantConfiguration.builder()
                    .transferPolicies(transferPolicies)
                    .build();

            assertEquals(timeout, config.getTransferPolicies().getDefaultTimeout());
        }
    }

    @Test
    public void testRetrySettings() {
        // Test various retry settings
        int[] retryAttempts = {0, 1, 3, 5, 10};

        for (int retries : retryAttempts) {
            TenantConfiguration.TransferPolicies transferPolicies = TenantConfiguration.TransferPolicies.builder()
                    .defaultRetryAttempts(retries)
                    .build();

            TenantConfiguration config = TenantConfiguration.builder()
                    .transferPolicies(transferPolicies)
                    .build();

            assertEquals(retries, config.getTransferPolicies().getDefaultRetryAttempts());
        }
    }
}
