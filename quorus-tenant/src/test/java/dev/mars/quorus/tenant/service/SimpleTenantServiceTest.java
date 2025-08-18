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

package dev.mars.quorus.tenant.service;

import dev.mars.quorus.tenant.model.Tenant;
import dev.mars.quorus.tenant.model.TenantConfiguration;
import dev.mars.quorus.tenant.model.ResourceUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SimpleTenantServiceTest {
    
    private SimpleTenantService tenantService;
    
    @BeforeEach
    void setUp() {
        tenantService = new SimpleTenantService();
    }
    
    @Test
    void testCreateRootTenant() {
        Tenant tenant = tenantService.createTenant(
                "acme-corp",
                "ACME Corporation",
                "Root organization tenant",
                null
        );
        
        assertNotNull(tenant);
        assertEquals("acme-corp", tenant.getTenantId());
        assertEquals("ACME Corporation", tenant.getName());
        assertEquals("Root organization tenant", tenant.getDescription());
        assertNull(tenant.getParentTenantId());
        assertTrue(tenant.isActive());
        assertNotNull(tenant.getCreatedAt());
        assertNotNull(tenant.getUpdatedAt());
    }
    
    @Test
    void testCreateChildTenant() {
        // Create parent tenant
        Tenant parent = tenantService.createTenant(
                "acme-corp",
                "ACME Corporation",
                "Root organization",
                null
        );
        
        // Create child tenant
        Tenant child = tenantService.createTenant(
                "acme-engineering",
                "Engineering Department",
                "Engineering team",
                "acme-corp"
        );
        
        assertNotNull(child);
        assertEquals("acme-engineering", child.getTenantId());
        assertEquals("Engineering Department", child.getName());
        assertEquals("acme-corp", child.getParentTenantId());
        assertTrue(child.isActive());
    }
    
    @Test
    void testCreateTenantWithInvalidParent() {
        assertThrows(IllegalArgumentException.class, () -> {
            tenantService.createTenant(
                    "invalid-child",
                    "Invalid Child",
                    "Child with non-existent parent",
                    "non-existent-parent"
            );
        });
    }
    
    @Test
    void testCreateTenantWithDuplicateId() {
        tenantService.createTenant(
                "duplicate-id",
                "First Tenant",
                "First tenant with this ID",
                null
        );
        
        assertThrows(IllegalArgumentException.class, () -> {
            tenantService.createTenant(
                    "duplicate-id",
                    "Second Tenant",
                    "Second tenant with same ID",
                    null
            );
        });
    }
    
    @Test
    void testGetTenant() {
        Tenant created = tenantService.createTenant(
                "test-tenant",
                "Test Tenant",
                "Test description",
                null
        );
        
        Optional<Tenant> retrieved = tenantService.getTenant("test-tenant");
        
        assertTrue(retrieved.isPresent());
        assertEquals(created.getTenantId(), retrieved.get().getTenantId());
        assertEquals(created.getName(), retrieved.get().getName());
    }
    
    @Test
    void testGetNonExistentTenant() {
        Optional<Tenant> tenant = tenantService.getTenant("non-existent");
        assertFalse(tenant.isPresent());
    }
    
    @Test
    void testGetAllTenants() {
        tenantService.createTenant("tenant1", "Tenant 1", "First tenant", null);
        tenantService.createTenant("tenant2", "Tenant 2", "Second tenant", null);
        tenantService.createTenant("tenant3", "Tenant 3", "Third tenant", "tenant1");
        
        List<Tenant> allTenants = tenantService.getAllTenants();
        
        assertEquals(3, allTenants.size());
        
        Set<String> tenantIds = Set.of(
                allTenants.get(0).getTenantId(),
                allTenants.get(1).getTenantId(),
                allTenants.get(2).getTenantId()
        );
        
        assertTrue(tenantIds.contains("tenant1"));
        assertTrue(tenantIds.contains("tenant2"));
        assertTrue(tenantIds.contains("tenant3"));
    }
    
    @Test
    void testGetChildTenants() {
        // Create parent and children
        tenantService.createTenant("parent", "Parent", "Parent tenant", null);
        tenantService.createTenant("child1", "Child 1", "First child", "parent");
        tenantService.createTenant("child2", "Child 2", "Second child", "parent");
        tenantService.createTenant("grandchild", "Grandchild", "Grandchild", "child1");
        
        List<Tenant> children = tenantService.getChildTenants("parent");
        
        assertEquals(2, children.size());
        
        Set<String> childIds = Set.of(children.get(0).getTenantId(), children.get(1).getTenantId());
        assertTrue(childIds.contains("child1"));
        assertTrue(childIds.contains("child2"));
    }
    
    @Test
    void testGetChildTenantsForNonExistentParent() {
        List<Tenant> children = tenantService.getChildTenants("non-existent");
        assertTrue(children.isEmpty());
    }
    
    @Test
    void testUpdateTenant() {
        Tenant original = tenantService.createTenant(
                "update-test",
                "Original Name",
                "Original description",
                null
        );
        
        Tenant updated = tenantService.updateTenant(
                "update-test",
                "Updated Name",
                "Updated description",
                false
        );
        
        assertNotNull(updated);
        assertEquals("update-test", updated.getTenantId());
        assertEquals("Updated Name", updated.getName());
        assertEquals("Updated description", updated.getDescription());
        assertFalse(updated.isActive());
        assertTrue(updated.getUpdatedAt().isAfter(original.getUpdatedAt()));
    }
    
    @Test
    void testUpdateNonExistentTenant() {
        assertThrows(IllegalArgumentException.class, () -> {
            tenantService.updateTenant(
                    "non-existent",
                    "New Name",
                    "New description",
                    true
            );
        });
    }
    
    @Test
    void testDeleteTenant() {
        tenantService.createTenant("delete-test", "Delete Test", "To be deleted", null);
        
        assertTrue(tenantService.getTenant("delete-test").isPresent());
        
        boolean deleted = tenantService.deleteTenant("delete-test");
        
        assertTrue(deleted);
        assertFalse(tenantService.getTenant("delete-test").isPresent());
    }
    
    @Test
    void testDeleteTenantWithChildren() {
        tenantService.createTenant("parent-delete", "Parent", "Parent to delete", null);
        tenantService.createTenant("child-delete", "Child", "Child tenant", "parent-delete");
        
        // Should not be able to delete parent with children
        assertThrows(IllegalStateException.class, () -> {
            tenantService.deleteTenant("parent-delete");
        });
    }
    
    @Test
    void testDeleteNonExistentTenant() {
        boolean deleted = tenantService.deleteTenant("non-existent");
        assertFalse(deleted);
    }
    
    @Test
    void testGetTenantConfiguration() {
        tenantService.createTenant("config-test", "Config Test", "Test config", null);
        
        TenantConfiguration config = tenantService.getTenantConfiguration("config-test");
        
        assertNotNull(config);
        assertEquals("config-test", config.getTenantId());
        // Should have default values
        assertTrue(config.getMaxConcurrentTransfers() > 0);
        assertTrue(config.getMaxBandwidthBytesPerSecond() > 0);
        assertNotNull(config.getAllowedProtocols());
        assertFalse(config.getAllowedProtocols().isEmpty());
    }
    
    @Test
    void testGetTenantConfigurationForNonExistentTenant() {
        assertThrows(IllegalArgumentException.class, () -> {
            tenantService.getTenantConfiguration("non-existent");
        });
    }
    
    @Test
    void testUpdateTenantConfiguration() {
        tenantService.createTenant("config-update", "Config Update", "Test config update", null);
        
        TenantConfiguration newConfig = TenantConfiguration.builder()
                .tenantId("config-update")
                .maxConcurrentTransfers(20)
                .maxBandwidthBytesPerSecond(200L * 1024 * 1024) // 200 MB/s
                .maxStorageBytes(1024L * 1024 * 1024 * 1024) // 1 TB
                .maxDailyTransfers(1000)
                .maxTransferSizeBytes(10L * 1024 * 1024 * 1024) // 10 GB
                .allowedProtocols(Set.of("http", "https", "sftp"))
                .requireAuthentication(true)
                .enableAuditLogging(true)
                .transferTimeoutSeconds(7200)
                .retryAttempts(5)
                .build();
        
        TenantConfiguration updated = tenantService.updateTenantConfiguration("config-update", newConfig);
        
        assertNotNull(updated);
        assertEquals("config-update", updated.getTenantId());
        assertEquals(20, updated.getMaxConcurrentTransfers());
        assertEquals(200L * 1024 * 1024, updated.getMaxBandwidthBytesPerSecond());
        assertEquals(1024L * 1024 * 1024 * 1024, updated.getMaxStorageBytes());
        assertEquals(1000, updated.getMaxDailyTransfers());
        assertEquals(10L * 1024 * 1024 * 1024, updated.getMaxTransferSizeBytes());
        assertEquals(Set.of("http", "https", "sftp"), updated.getAllowedProtocols());
        assertTrue(updated.isRequireAuthentication());
        assertTrue(updated.isEnableAuditLogging());
        assertEquals(7200, updated.getTransferTimeoutSeconds());
        assertEquals(5, updated.getRetryAttempts());
    }
    
    @Test
    void testGetEffectiveConfiguration() {
        // Create parent with custom config
        tenantService.createTenant("parent-config", "Parent Config", "Parent", null);
        TenantConfiguration parentConfig = TenantConfiguration.builder()
                .tenantId("parent-config")
                .maxConcurrentTransfers(50)
                .maxBandwidthBytesPerSecond(500L * 1024 * 1024)
                .allowedProtocols(Set.of("http", "https", "smb"))
                .requireAuthentication(true)
                .build();
        tenantService.updateTenantConfiguration("parent-config", parentConfig);
        
        // Create child tenant
        tenantService.createTenant("child-config", "Child Config", "Child", "parent-config");
        
        // Get effective configuration for child (should inherit from parent)
        TenantConfiguration effectiveConfig = tenantService.getEffectiveConfiguration("child-config");
        
        assertNotNull(effectiveConfig);
        assertEquals("child-config", effectiveConfig.getTenantId());
        assertEquals(50, effectiveConfig.getMaxConcurrentTransfers()); // Inherited from parent
        assertEquals(500L * 1024 * 1024, effectiveConfig.getMaxBandwidthBytesPerSecond()); // Inherited
        assertEquals(Set.of("http", "https", "smb"), effectiveConfig.getAllowedProtocols()); // Inherited
        assertTrue(effectiveConfig.isRequireAuthentication()); // Inherited
    }
    
    @Test
    void testGetEffectiveConfigurationWithOverride() {
        // Create parent
        tenantService.createTenant("parent-override", "Parent Override", "Parent", null);
        TenantConfiguration parentConfig = TenantConfiguration.builder()
                .tenantId("parent-override")
                .maxConcurrentTransfers(50)
                .maxBandwidthBytesPerSecond(500L * 1024 * 1024)
                .build();
        tenantService.updateTenantConfiguration("parent-override", parentConfig);
        
        // Create child with custom config
        tenantService.createTenant("child-override", "Child Override", "Child", "parent-override");
        TenantConfiguration childConfig = TenantConfiguration.builder()
                .tenantId("child-override")
                .maxConcurrentTransfers(25) // Override parent value
                .build();
        tenantService.updateTenantConfiguration("child-override", childConfig);
        
        // Get effective configuration
        TenantConfiguration effectiveConfig = tenantService.getEffectiveConfiguration("child-override");
        
        assertNotNull(effectiveConfig);
        assertEquals(25, effectiveConfig.getMaxConcurrentTransfers()); // Child override
        assertEquals(500L * 1024 * 1024, effectiveConfig.getMaxBandwidthBytesPerSecond()); // Parent value
    }
}
