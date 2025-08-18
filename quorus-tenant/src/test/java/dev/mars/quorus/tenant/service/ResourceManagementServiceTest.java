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
import dev.mars.quorus.tenant.model.TenantConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResourceManagementServiceTest {
    
    private ResourceManagementService resourceService;
    private SimpleTenantService tenantService;
    
    @BeforeEach
    void setUp() {
        tenantService = new SimpleTenantService();
        resourceService = new ResourceManagementService(tenantService);
        
        // Create test tenant
        tenantService.createTenant("test-tenant", "Test Tenant", "Test tenant for resource management", null);
    }
    
    @Test
    void testRecordResourceUsage() {
        resourceService.recordResourceUsage(
                "test-tenant",
                5,
                50L * 1024 * 1024, // 50 MB/s
                10L * 1024 * 1024 * 1024 // 10 GB
        );
        
        ResourceUsage usage = resourceService.getCurrentResourceUsage("test-tenant");
        
        assertNotNull(usage);
        assertEquals("test-tenant", usage.getTenantId());
        assertEquals(5, usage.getCurrentConcurrentTransfers());
        assertEquals(50L * 1024 * 1024, usage.getCurrentBandwidthUsage());
        assertEquals(10L * 1024 * 1024 * 1024, usage.getCurrentStorageUsage());
        assertNotNull(usage.getLastUpdated());
    }
    
    @Test
    void testCheckResourceLimitsWithinLimits() {
        TenantConfiguration config = TenantConfiguration.builder()
                .tenantId("test-tenant")
                .maxConcurrentTransfers(10)
                .maxBandwidthBytesPerSecond(100L * 1024 * 1024)
                .maxTransferSizeBytes(10L * 1024 * 1024 * 1024)
                .build();
        tenantService.updateTenantConfiguration("test-tenant", config);
        
        resourceService.recordResourceUsage("test-tenant", 5, 50L * 1024 * 1024, 500L * 1024 * 1024 * 1024);
        
        List<String> violations = resourceService.checkResourceLimits("test-tenant", 2, 25L * 1024 * 1024, 5L * 1024 * 1024 * 1024);
        
        assertTrue(violations.isEmpty());
    }
    
    @Test
    void testCheckResourceLimitsExceedsConcurrentTransfers() {
        TenantConfiguration config = TenantConfiguration.builder()
                .tenantId("test-tenant")
                .maxConcurrentTransfers(5)
                .build();
        tenantService.updateTenantConfiguration("test-tenant", config);
        
        resourceService.recordResourceUsage("test-tenant", 4, 0, 0);
        
        List<String> violations = resourceService.checkResourceLimits("test-tenant", 2, 0, 0);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.contains("concurrent transfers")));
    }
    
    @Test
    void testGetResourceUtilization() {
        TenantConfiguration config = TenantConfiguration.builder()
                .tenantId("test-tenant")
                .maxConcurrentTransfers(10)
                .maxBandwidthBytesPerSecond(100L * 1024 * 1024)
                .maxStorageBytes(1024L * 1024 * 1024 * 1024)
                .build();
        tenantService.updateTenantConfiguration("test-tenant", config);
        
        resourceService.recordResourceUsage("test-tenant", 5, 50L * 1024 * 1024, 512L * 1024 * 1024 * 1024);
        
        double utilization = resourceService.getResourceUtilization("test-tenant");
        
        assertEquals(50.0, utilization, 5.0); // Allow 5% tolerance
    }
}
