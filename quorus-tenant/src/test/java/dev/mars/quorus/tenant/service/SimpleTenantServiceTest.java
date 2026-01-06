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
import dev.mars.quorus.tenant.service.TenantService.TenantServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
/**
 * Description for SimpleTenantServiceTest
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-18
 */

class SimpleTenantServiceTest {
    
    private SimpleTenantService tenantService;
    
    @BeforeEach
    void setUp() {
        tenantService = new SimpleTenantService();
    }
    
    @Test
    void testCreateRootTenant() throws TenantServiceException {
        Tenant tenantToCreate = Tenant.builder()
                .tenantId("acme-corp")
                .name("ACME Corporation")
                .description("Root organization tenant")
                .build();

        Tenant tenant = tenantService.createTenant(tenantToCreate);

        assertNotNull(tenant);
        assertEquals("acme-corp", tenant.getTenantId());
        assertEquals("ACME Corporation", tenant.getName());
        assertEquals("Root organization tenant", tenant.getDescription());
        assertNull(tenant.getParentTenantId());
        assertTrue(tenant.isActive());
        assertNotNull(tenant.getCreatedAt());
    }

    @Test
    void testCreateChildTenant() throws TenantServiceException {
        // Create parent tenant
        Tenant parentToCreate = Tenant.builder()
                .tenantId("acme-corp")
                .name("ACME Corporation")
                .description("Root organization")
                .build();
        Tenant parent = tenantService.createTenant(parentToCreate);

        // Create child tenant
        Tenant childToCreate = Tenant.builder()
                .tenantId("acme-engineering")
                .name("Engineering Department")
                .description("Engineering team")
                .parentTenantId("acme-corp")
                .build();
        Tenant child = tenantService.createTenant(childToCreate);

        assertNotNull(child);
        assertEquals("acme-engineering", child.getTenantId());
        assertEquals("Engineering Department", child.getName());
        assertEquals("acme-corp", child.getParentTenantId());
        assertTrue(child.isActive());
    }
    
    @Test
    void testCreateTenantWithInvalidParent() {
        assertThrows(TenantServiceException.class, () -> {
            Tenant tenantToCreate = Tenant.builder()
                    .tenantId("invalid-child")
                    .name("Invalid Child")
                    .description("Child with non-existent parent")
                    .parentTenantId("non-existent-parent")
                    .build();
            tenantService.createTenant(tenantToCreate);
        });
    }

    @Test
    void testCreateTenantWithDuplicateId() throws TenantServiceException {
        Tenant firstTenant = Tenant.builder()
                .tenantId("duplicate-id")
                .name("First Tenant")
                .description("First tenant with this ID")
                .build();
        tenantService.createTenant(firstTenant);

        assertThrows(TenantServiceException.class, () -> {
            Tenant secondTenant = Tenant.builder()
                    .tenantId("duplicate-id")
                    .name("Second Tenant")
                    .description("Second tenant with same ID")
                    .build();
            tenantService.createTenant(secondTenant);
        });
    }
    
    @Test
    void testGetTenant() throws TenantServiceException {
        Tenant tenantToCreate = Tenant.builder()
                .tenantId("test-tenant")
                .name("Test Tenant")
                .description("Test description")
                .build();
        Tenant created = tenantService.createTenant(tenantToCreate);

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
    void testGetAllTenants() throws TenantServiceException {
        Tenant tenant1 = Tenant.builder().tenantId("tenant1").name("Tenant 1").description("First tenant").build();
        Tenant tenant2 = Tenant.builder().tenantId("tenant2").name("Tenant 2").description("Second tenant").build();
        Tenant tenant3 = Tenant.builder().tenantId("tenant3").name("Tenant 3").description("Third tenant").parentTenantId("tenant1").build();

        tenantService.createTenant(tenant1);
        tenantService.createTenant(tenant2);
        tenantService.createTenant(tenant3);

        List<Tenant> allTenants = tenantService.getActiveTenants();

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
    void testGetChildTenants() throws TenantServiceException {
        // Create parent and children
        Tenant parent = Tenant.builder()
                .tenantId("parent")
                .name("Parent")
                .description("Parent tenant")
                .build();
        tenantService.createTenant(parent);

        Tenant child1 = Tenant.builder()
                .tenantId("child1")
                .name("Child 1")
                .description("First child")
                .parentTenantId("parent")
                .build();
        tenantService.createTenant(child1);

        Tenant child2 = Tenant.builder()
                .tenantId("child2")
                .name("Child 2")
                .description("Second child")
                .parentTenantId("parent")
                .build();
        tenantService.createTenant(child2);

        Tenant grandchild = Tenant.builder()
                .tenantId("grandchild")
                .name("Grandchild")
                .description("Grandchild")
                .parentTenantId("child1")
                .build();
        tenantService.createTenant(grandchild);

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
    void testUpdateTenant() throws TenantServiceException {
        Tenant original = Tenant.builder()
                .tenantId("update-test")
                .name("Original Name")
                .description("Original description")
                .build();
        Tenant created = tenantService.createTenant(original);

        // Add a small delay to ensure different timestamps
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Tenant updated = created.toBuilder()
                .name("Updated Name")
                .description("Updated description")
                .status(Tenant.TenantStatus.INACTIVE)
                .build();

        Tenant result = tenantService.updateTenant(updated);

        assertNotNull(result);
        assertEquals("update-test", result.getTenantId());
        assertEquals("Updated Name", result.getName());
        assertEquals("Updated description", result.getDescription());
        assertEquals(Tenant.TenantStatus.INACTIVE, result.getStatus());
        assertTrue(result.getUpdatedAt().isAfter(created.getUpdatedAt()));
    }
    
    @Test
    void testUpdateNonExistentTenant() {
        Tenant nonExistent = Tenant.builder()
                .tenantId("non-existent")
                .name("New Name")
                .description("New description")
                .build();

        assertThrows(TenantServiceException.class, () -> {
            tenantService.updateTenant(nonExistent);
        });
    }
    
    @Test
    void testDeleteTenant() throws TenantServiceException {
        Tenant tenant = Tenant.builder()
                .tenantId("delete-test")
                .name("Delete Test")
                .description("To be deleted")
                .build();
        tenantService.createTenant(tenant);

        assertTrue(tenantService.getTenant("delete-test").isPresent());

        tenantService.deleteTenant("delete-test");

        assertFalse(tenantService.getTenant("delete-test").isPresent());
    }
    
    @Test
    void testDeleteTenantWithChildren() throws TenantServiceException {
        Tenant parent = Tenant.builder()
                .tenantId("parent-delete")
                .name("Parent")
                .description("Parent to delete")
                .build();
        tenantService.createTenant(parent);

        Tenant child = Tenant.builder()
                .tenantId("child-delete")
                .name("Child")
                .description("Child tenant")
                .parentTenantId("parent-delete")
                .build();
        tenantService.createTenant(child);

        // Should not be able to delete parent with children
        assertThrows(TenantServiceException.class, () -> {
            tenantService.deleteTenant("parent-delete");
        });
    }
    
    @Test
    void testDeleteNonExistentTenant() {
        // Should throw exception for non-existent tenant
        assertThrows(TenantServiceException.class, () -> {
            tenantService.deleteTenant("non-existent");
        });
    }
    
    @Test
    void testGetEffectiveConfiguration() throws TenantServiceException {
        Tenant tenantToCreate = Tenant.builder()
                .tenantId("config-test")
                .name("Config Test")
                .description("Test config")
                .build();
        tenantService.createTenant(tenantToCreate);

        TenantConfiguration config = tenantService.getEffectiveConfiguration("config-test");

        assertNotNull(config);
        // Should have default values
        assertTrue(config.getResourceLimits().getMaxConcurrentTransfers() > 0);
        assertTrue(config.getResourceLimits().getMaxBandwidthBytesPerSecond() > 0);
        assertNotNull(config.getTransferPolicies().getAllowedProtocols());
        assertFalse(config.getTransferPolicies().getAllowedProtocols().isEmpty());
    }
    
    @Test
    void testGetEffectiveConfigurationForNonExistentTenant() {
        TenantConfiguration config = tenantService.getEffectiveConfiguration("non-existent");
        assertNull(config);
    }
    
    @Test
    void testUpdateTenantConfiguration() throws TenantServiceException {
        Tenant tenantToCreate = Tenant.builder()
                .tenantId("config-update")
                .name("Config Update")
                .description("Test config update")
                .build();
        tenantService.createTenant(tenantToCreate);

        TenantConfiguration.ResourceLimits resourceLimits = TenantConfiguration.ResourceLimits.builder()
                .maxConcurrentTransfers(20)
                .maxBandwidthBytesPerSecond(200L * 1024 * 1024) // 200 MB/s
                .maxStorageBytes(1024L * 1024 * 1024 * 1024) // 1 TB
                .maxTransfersPerDay(1000)
                .maxTransferSizeBytes(10L * 1024 * 1024 * 1024) // 10 GB
                .build();

        TenantConfiguration.TransferPolicies transferPolicies = TenantConfiguration.TransferPolicies.builder()
                .allowedProtocols(Set.of("http", "https", "sftp"))
                .defaultTimeout(Duration.ofSeconds(7200))
                .defaultRetryAttempts(5)
                .build();

        TenantConfiguration.SecuritySettings securitySettings = TenantConfiguration.SecuritySettings.builder()
                .requireAuthentication(true)
                .enableAuditLogging(true)
                .build();

        TenantConfiguration newConfig = TenantConfiguration.builder()
                .resourceLimits(resourceLimits)
                .transferPolicies(transferPolicies)
                .securitySettings(securitySettings)
                .build();

        Tenant updatedTenant = tenantService.updateTenantConfiguration("config-update", newConfig);

        assertNotNull(updatedTenant);
        assertEquals("config-update", updatedTenant.getTenantId());

        TenantConfiguration config = updatedTenant.getConfiguration();
        assertNotNull(config);
        assertEquals(20, config.getResourceLimits().getMaxConcurrentTransfers());
        assertEquals(200L * 1024 * 1024, config.getResourceLimits().getMaxBandwidthBytesPerSecond());
        assertEquals(1024L * 1024 * 1024 * 1024, config.getResourceLimits().getMaxStorageBytes());
        assertEquals(1000, config.getResourceLimits().getMaxTransfersPerDay());
        assertEquals(10L * 1024 * 1024 * 1024, config.getResourceLimits().getMaxTransferSizeBytes());
        assertEquals(Set.of("http", "https", "sftp"), config.getTransferPolicies().getAllowedProtocols());
        assertTrue(config.getSecuritySettings().isRequireAuthentication());
        assertTrue(config.getSecuritySettings().isEnableAuditLogging());
        assertEquals(Duration.ofSeconds(7200), config.getTransferPolicies().getDefaultTimeout());
        assertEquals(5, config.getTransferPolicies().getDefaultRetryAttempts());
    }
    
    @Test
    void testGetEffectiveConfigurationWithInheritance() throws TenantServiceException {
        // Create parent with custom config
        Tenant parent = Tenant.builder()
                .tenantId("parent-config")
                .name("Parent Config")
                .description("Parent")
                .build();
        tenantService.createTenant(parent);

        TenantConfiguration.ResourceLimits resourceLimits = TenantConfiguration.ResourceLimits.builder()
                .maxConcurrentTransfers(50)
                .maxBandwidthBytesPerSecond(500L * 1024 * 1024)
                .build();

        TenantConfiguration.TransferPolicies transferPolicies = TenantConfiguration.TransferPolicies.builder()
                .allowedProtocols(Set.of("http", "https", "smb"))
                .build();

        TenantConfiguration.SecuritySettings securitySettings = TenantConfiguration.SecuritySettings.builder()
                .requireAuthentication(true)
                .build();

        TenantConfiguration parentConfig = TenantConfiguration.builder()
                .resourceLimits(resourceLimits)
                .transferPolicies(transferPolicies)
                .securitySettings(securitySettings)
                .build();
        tenantService.updateTenantConfiguration("parent-config", parentConfig);

        // Create child tenant
        Tenant child = Tenant.builder()
                .tenantId("child-config")
                .name("Child Config")
                .description("Child")
                .parentTenantId("parent-config")
                .build();
        tenantService.createTenant(child);

        // Get effective configuration for child (should inherit from parent)
        TenantConfiguration effectiveConfig = tenantService.getEffectiveConfiguration("child-config");

        assertNotNull(effectiveConfig);
        assertEquals(50, effectiveConfig.getResourceLimits().getMaxConcurrentTransfers()); // Inherited from parent
        assertEquals(500L * 1024 * 1024, effectiveConfig.getResourceLimits().getMaxBandwidthBytesPerSecond()); // Inherited
        assertEquals(Set.of("http", "https", "smb"), effectiveConfig.getTransferPolicies().getAllowedProtocols()); // Inherited
        assertTrue(effectiveConfig.getSecuritySettings().isRequireAuthentication()); // Inherited
    }
    
    @Test
    void testGetEffectiveConfigurationWithOverride() throws TenantServiceException {
        // Create parent
        Tenant parent = Tenant.builder()
                .tenantId("parent-override")
                .name("Parent Override")
                .description("Parent")
                .build();
        tenantService.createTenant(parent);

        TenantConfiguration.ResourceLimits parentResourceLimits = TenantConfiguration.ResourceLimits.builder()
                .maxConcurrentTransfers(50)
                .maxBandwidthBytesPerSecond(500L * 1024 * 1024)
                .build();

        TenantConfiguration parentConfig = TenantConfiguration.builder()
                .resourceLimits(parentResourceLimits)
                .build();
        tenantService.updateTenantConfiguration("parent-override", parentConfig);

        // Create child with custom config
        Tenant child = Tenant.builder()
                .tenantId("child-override")
                .name("Child Override")
                .description("Child")
                .parentTenantId("parent-override")
                .build();
        tenantService.createTenant(child);

        TenantConfiguration.ResourceLimits childResourceLimits = TenantConfiguration.ResourceLimits.builder()
                .maxConcurrentTransfers(25) // Override parent value
                .build();

        TenantConfiguration childConfig = TenantConfiguration.builder()
                .resourceLimits(childResourceLimits)
                .build();
        tenantService.updateTenantConfiguration("child-override", childConfig);

        // Get effective configuration
        TenantConfiguration effectiveConfig = tenantService.getEffectiveConfiguration("child-override");

        assertNotNull(effectiveConfig);
        assertEquals(25, effectiveConfig.getResourceLimits().getMaxConcurrentTransfers()); // Child override
        // The child config only overrides maxConcurrentTransfers, so bandwidth should be the default value
        assertEquals(100L * 1024 * 1024, effectiveConfig.getResourceLimits().getMaxBandwidthBytesPerSecond()); // Default value
    }
}
