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

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TenantTest {
    
    @Test
    void testBuilderWithAllFields() {
        Instant now = Instant.now();
        
        Tenant tenant = Tenant.builder()
                .tenantId("test-tenant")
                .name("Test Tenant")
                .description("Test description")
                .parentTenantId("parent-tenant")
                .status(Tenant.TenantStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        
        assertEquals("test-tenant", tenant.getTenantId());
        assertEquals("Test Tenant", tenant.getName());
        assertEquals("Test description", tenant.getDescription());
        assertEquals("parent-tenant", tenant.getParentTenantId());
        assertTrue(tenant.isActive());
        assertEquals(now, tenant.getCreatedAt());
        assertEquals(now, tenant.getUpdatedAt());
    }
    
    @Test
    void testBuilderWithMinimalFields() {
        Tenant tenant = Tenant.builder()
                .tenantId("minimal-tenant")
                .name("Minimal Tenant")
                .build();
        
        assertEquals("minimal-tenant", tenant.getTenantId());
        assertEquals("Minimal Tenant", tenant.getName());
        assertNull(tenant.getDescription());
        assertNull(tenant.getParentTenantId());
        assertTrue(tenant.isActive()); // Default value
        assertNotNull(tenant.getCreatedAt()); // Should be set automatically
        assertNull(tenant.getUpdatedAt()); // Not set automatically, only when updated
    }
    
    @Test
    void testBuilderRequiredFields() {
        // TenantId is required
        assertThrows(NullPointerException.class, () -> {
            Tenant.builder()
                    .name("Test")
                    .build();
        });
        
        // Name is required
        assertThrows(NullPointerException.class, () -> {
            Tenant.builder()
                    .tenantId("test")
                    .build();
        });
    }
    
    @Test
    void testIsRootTenant() {
        Tenant rootTenant = Tenant.builder()
                .tenantId("root")
                .name("Root Tenant")
                .parentTenantId(null)
                .build();
        
        assertTrue(rootTenant.isRootTenant());
        
        Tenant childTenant = Tenant.builder()
                .tenantId("child")
                .name("Child Tenant")
                .parentTenantId("root")
                .build();
        
        assertFalse(childTenant.isRootTenant());
    }
    
    @Test
    void testToBuilder() {
        Instant now = Instant.now();
        
        Tenant original = Tenant.builder()
                .tenantId("original")
                .name("Original Name")
                .description("Original description")
                .parentTenantId("parent")
                .status(Tenant.TenantStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        
        Tenant modified = original.toBuilder()
                .name("Modified Name")
                .description("Modified description")
                .status(Tenant.TenantStatus.INACTIVE)
                .build();
        
        // Modified fields
        assertEquals("Modified Name", modified.getName());
        assertEquals("Modified description", modified.getDescription());
        assertFalse(modified.isActive());
        
        // Unchanged fields
        assertEquals("original", modified.getTenantId());
        assertEquals("parent", modified.getParentTenantId());
        assertEquals(now, modified.getCreatedAt());
        assertEquals(now, modified.getUpdatedAt());
    }
    
    @Test
    void testEqualsAndHashCode() {
        Tenant tenant1 = Tenant.builder()
                .tenantId("test-tenant")
                .name("Test Tenant")
                .build();
        
        Tenant tenant2 = Tenant.builder()
                .tenantId("test-tenant")
                .name("Different Name")
                .description("Different description")
                .build();
        
        Tenant tenant3 = Tenant.builder()
                .tenantId("different-tenant")
                .name("Test Tenant")
                .build();
        
        // Tenants with same ID should be equal
        assertEquals(tenant1, tenant2);
        assertEquals(tenant1.hashCode(), tenant2.hashCode());
        
        // Tenants with different IDs should not be equal
        assertNotEquals(tenant1, tenant3);
        assertNotEquals(tenant1.hashCode(), tenant3.hashCode());
        
        // Test with null
        assertNotEquals(tenant1, null);
        
        // Test with different class
        assertNotEquals(tenant1, "string");
        
        // Test reflexivity
        assertEquals(tenant1, tenant1);
    }
    
    @Test
    void testToString() {
        Tenant tenant = Tenant.builder()
                .tenantId("test-tenant")
                .name("Test Tenant")
                .description("Test description")
                .parentTenantId("parent")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        
        String toString = tenant.toString();
        
        assertNotNull(toString);
        assertTrue(toString.contains("test-tenant"));
        assertTrue(toString.contains("Test Tenant"));
        assertTrue(toString.contains("parent"));
        assertTrue(toString.contains("ACTIVE")); // Status enum value, not boolean
    }
    
    @Test
    void testDefaultValues() {
        Tenant tenant = Tenant.builder()
                .tenantId("default-test")
                .name("Default Test")
                .build();
        
        // Should have default values
        assertTrue(tenant.isActive());
        assertNotNull(tenant.getCreatedAt());

        // Should be close to current time
        Instant now = Instant.now();
        assertTrue(tenant.getCreatedAt().isBefore(now.plusSeconds(1)));
        assertTrue(tenant.getCreatedAt().isAfter(now.minusSeconds(1)));
    }
    
    @Test
    void testInactivetenant() {
        Tenant tenant = Tenant.builder()
                .tenantId("inactive-tenant")
                .name("Inactive Tenant")
                .status(Tenant.TenantStatus.INACTIVE)
                .build();
        
        assertFalse(tenant.isActive());
    }
    
    @Test
    void testTenantHierarchy() {
        // Test a three-level hierarchy
        Tenant organization = Tenant.builder()
                .tenantId("org")
                .name("Organization")
                .build();
        
        Tenant department = Tenant.builder()
                .tenantId("dept")
                .name("Department")
                .parentTenantId("org")
                .build();
        
        Tenant team = Tenant.builder()
                .tenantId("team")
                .name("Team")
                .parentTenantId("dept")
                .build();
        
        assertTrue(organization.isRootTenant());
        assertFalse(department.isRootTenant());
        assertFalse(team.isRootTenant());
        
        assertEquals("org", department.getParentTenantId());
        assertEquals("dept", team.getParentTenantId());
    }
    
    @Test
    void testTenantIdValidation() {
        // Valid tenant IDs
        assertDoesNotThrow(() -> {
            Tenant.builder()
                    .tenantId("valid-tenant-123")
                    .name("Valid Tenant")
                    .build();
        });
        
        assertDoesNotThrow(() -> {
            Tenant.builder()
                    .tenantId("org.dept.team")
                    .name("Dotted Tenant")
                    .build();
        });
        
        assertDoesNotThrow(() -> {
            Tenant.builder()
                    .tenantId("UPPERCASE")
                    .name("Uppercase Tenant")
                    .build();
        });
    }
    
    @Test
    void testTimestampBehavior() {
        Instant before = Instant.now();
        
        Tenant tenant = Tenant.builder()
                .tenantId("timestamp-test")
                .name("Timestamp Test")
                .build();
        
        Instant after = Instant.now();
        
        // Created timestamp should be between before and after
        assertTrue(tenant.getCreatedAt().isAfter(before.minusSeconds(1)));
        assertTrue(tenant.getCreatedAt().isBefore(after.plusSeconds(1)));
    }
}
