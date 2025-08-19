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

import dev.mars.quorus.tenant.model.ResourceUsage;
import dev.mars.quorus.tenant.model.Tenant;
import dev.mars.quorus.tenant.model.TenantConfiguration;
import dev.mars.quorus.tenant.service.TenantService.TenantServiceException;
import dev.mars.quorus.tenant.service.ResourceManagementService.ResourceManagementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ResourceManagementServiceTest {

    private ResourceManagementService resourceService;
    private SimpleTenantService tenantService;

    @BeforeEach
    void setUp() throws TenantServiceException {
        tenantService = new SimpleTenantService();
        resourceService = new SimpleResourceManagementService(tenantService);

        // Create test tenant
        Tenant testTenant = Tenant.builder()
                .tenantId("test-tenant")
                .name("Test Tenant")
                .description("Test tenant for resource management")
                .build();
        tenantService.createTenant(testTenant);
    }
    
    @Test
    void testRecordResourceUsage() throws ResourceManagementException {
        ResourceUsage usage = ResourceUsage.builder()
                .tenantId("test-tenant")
                .currentConcurrentTransfers(5)
                .currentBandwidthBytesPerSecond(50L * 1024 * 1024) // 50 MB/s
                .currentStorageBytes(10L * 1024 * 1024 * 1024) // 10 GB
                .build();

        resourceService.recordUsage(usage);

        Optional<ResourceUsage> retrievedUsage = resourceService.getCurrentUsage("test-tenant");

        assertTrue(retrievedUsage.isPresent());
        ResourceUsage actualUsage = retrievedUsage.get();
        assertEquals("test-tenant", actualUsage.getTenantId());
        assertEquals(5, actualUsage.getCurrentConcurrentTransfers());
        assertEquals(50L * 1024 * 1024, actualUsage.getCurrentBandwidthBytesPerSecond());
        assertEquals(10L * 1024 * 1024 * 1024, actualUsage.getCurrentStorageBytes());
        assertNotNull(actualUsage.getTimestamp());
    }
    
    @Test
    void testValidateTransferRequestWithinLimits() throws TenantServiceException, ResourceManagementException {
        TenantConfiguration.ResourceLimits resourceLimits = TenantConfiguration.ResourceLimits.builder()
                .maxConcurrentTransfers(10)
                .maxBandwidthBytesPerSecond(100L * 1024 * 1024)
                .maxTransferSizeBytes(10L * 1024 * 1024 * 1024)
                .build();

        TenantConfiguration config = TenantConfiguration.builder()
                .resourceLimits(resourceLimits)
                .build();

        Tenant updatedTenant = tenantService.getTenant("test-tenant").get().toBuilder()
                .configuration(config)
                .build();
        tenantService.updateTenant(updatedTenant);

        // Record some current usage
        ResourceUsage usage = ResourceUsage.builder()
                .tenantId("test-tenant")
                .currentConcurrentTransfers(5)
                .currentBandwidthBytesPerSecond(50L * 1024 * 1024)
                .currentStorageBytes(500L * 1024 * 1024 * 1024)
                .build();
        resourceService.recordUsage(usage);

        ResourceManagementService.ResourceValidationResult result =
                resourceService.validateTransferRequest("test-tenant", 5L * 1024 * 1024 * 1024, 25L * 1024 * 1024);

        assertTrue(result.isAllowed());
    }
    
    @Test
    void testValidateTransferRequestExceedsConcurrentTransfers() throws TenantServiceException, ResourceManagementException {
        TenantConfiguration.ResourceLimits resourceLimits = TenantConfiguration.ResourceLimits.builder()
                .maxConcurrentTransfers(5)
                .build();

        TenantConfiguration config = TenantConfiguration.builder()
                .resourceLimits(resourceLimits)
                .build();

        Tenant updatedTenant = tenantService.getTenant("test-tenant").get().toBuilder()
                .configuration(config)
                .build();
        tenantService.updateTenant(updatedTenant);

        // Record current usage at the limit
        ResourceUsage usage = ResourceUsage.builder()
                .tenantId("test-tenant")
                .currentConcurrentTransfers(5)
                .build();
        resourceService.recordUsage(usage);

        ResourceManagementService.ResourceValidationResult result =
                resourceService.validateTransferRequest("test-tenant", 1024, 0);

        assertFalse(result.isAllowed());
        assertTrue(result.getViolations().stream().anyMatch(v -> v.contains("concurrent transfers")));
    }
    
    @Test
    void testGetResourceUtilization() throws TenantServiceException, ResourceManagementException {
        TenantConfiguration.ResourceLimits resourceLimits = TenantConfiguration.ResourceLimits.builder()
                .maxConcurrentTransfers(10)
                .maxBandwidthBytesPerSecond(100L * 1024 * 1024)
                .maxStorageBytes(1024L * 1024 * 1024 * 1024)
                .build();

        TenantConfiguration config = TenantConfiguration.builder()
                .resourceLimits(resourceLimits)
                .build();

        Tenant updatedTenant = tenantService.getTenant("test-tenant").get().toBuilder()
                .configuration(config)
                .build();
        tenantService.updateTenant(updatedTenant);

        ResourceUsage usage = ResourceUsage.builder()
                .tenantId("test-tenant")
                .currentConcurrentTransfers(5)
                .currentBandwidthBytesPerSecond(50L * 1024 * 1024)
                .currentStorageBytes(512L * 1024 * 1024 * 1024)
                .build();
        resourceService.recordUsage(usage);

        ResourceManagementService.ResourceUtilization utilization = resourceService.getResourceUtilization("test-tenant");

        assertEquals("test-tenant", utilization.getTenantId());
        assertEquals(50.0, utilization.getConcurrentTransfersUtilization(), 5.0); // Allow 5% tolerance
        assertEquals(50.0, utilization.getBandwidthUtilization(), 5.0);
        assertEquals(50.0, utilization.getStorageUtilization(), 5.0);
    }
}
