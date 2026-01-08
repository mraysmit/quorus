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

package dev.mars.quorus.examples;

import dev.mars.quorus.examples.util.ExampleLogger;
import dev.mars.quorus.tenant.model.Tenant;
import dev.mars.quorus.tenant.model.TenantConfiguration;
import dev.mars.quorus.tenant.model.ResourceUsage;
import dev.mars.quorus.tenant.service.SimpleTenantService;
import dev.mars.quorus.tenant.service.SimpleResourceManagementService;
import dev.mars.quorus.tenant.service.TenantService;
import dev.mars.quorus.tenant.service.ResourceManagementService;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Multi-Tenant Example - Demonstrates Quorus multi-tenant capabilities.
 * 
 * This example shows:
 * - Creating hierarchical tenant structures
 * - Configuring resource limits and policies per tenant
 * - Resource usage tracking and quota enforcement
 * - Tenant isolation and security boundaries
 * - Configuration inheritance in tenant hierarchies
 * 
 * Simulates a corporate environment with:
 * - Root organization tenant
 * - Department-level tenants (Engineering, Sales, Marketing)
 * - Team-level tenants within departments
 * - Different resource limits and policies per tenant level
 * 
 * Run with: mvn exec:java -Dexec.mainClass="dev.mars.quorus.examples.MultiTenantExample" -pl quorus-integration-examples
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-18
 * @version 1.0
 */
public class MultiTenantExample {
    private static final ExampleLogger log = ExampleLogger.getLogger(MultiTenantExample.class);
    
    private TenantService tenantService;
    private ResourceManagementService resourceService;
    
    public static void main(String[] args) {
        log.exampleStart("Quorus Multi-Tenant Example",
                "Demonstrating enterprise multi-tenancy with hierarchical structure,\n" +
                "resource management, and tenant isolation capabilities.");
        
        try {
            MultiTenantExample example = new MultiTenantExample();
            example.runExample();
            log.exampleComplete("Multi-Tenant Example");
        } catch (Exception e) {
            log.unexpectedError("Multi-Tenant Example", e);
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public void runExample() throws Exception {
        // Initialize services
        tenantService = new SimpleTenantService();
        resourceService = new SimpleResourceManagementService(tenantService);
        
        log.testSection("1. Creating hierarchical tenant structure", false);
        createTenantHierarchy();
        
        log.testSection("2. Configuring tenant-specific resource limits", false);
        configureTenantLimits();
        
        log.testSection("3. Demonstrating resource usage tracking", false);
        demonstrateResourceTracking();
        
        log.testSection("4. Testing quota enforcement", true);
        testQuotaEnforcement();
        
        log.testSection("5. Showing configuration inheritance", false);
        demonstrateConfigurationInheritance();
        
        log.testSection("6. Monitoring tenant resource utilization", false);
        monitorResourceUtilization();
    }
    
    private void createTenantHierarchy() throws Exception {
        log.step("Creating corporate tenant hierarchy...");
        
        // Create root organization tenant
        Tenant rootTenant = Tenant.builder()
                .tenantId("acme-corp")
                .name("ACME Corporation")
                .description("Root organization tenant")
                .configuration(createEnterpriseConfiguration())
                .build();
        
        tenantService.createTenant(rootTenant);
        log.expectedSuccess("Created root tenant: " + rootTenant.getName());
        
        // Create department-level tenants
        String[] departments = {"engineering", "sales", "marketing"};
        String[] departmentNames = {"Engineering Department", "Sales Department", "Marketing Department"};
        
        for (int i = 0; i < departments.length; i++) {
            Tenant deptTenant = Tenant.builder()
                    .tenantId("acme-" + departments[i])
                    .name(departmentNames[i])
                    .description(departmentNames[i] + " - Department level tenant")
                    .parentTenantId("acme-corp")
                    .configuration(createDepartmentConfiguration())
                    .build();
            
            tenantService.createTenant(deptTenant);
            log.expectedSuccess("Created department tenant: " + deptTenant.getName());
        }
        
        // Create team-level tenants within Engineering
        String[] teams = {"backend", "frontend", "devops"};
        String[] teamNames = {"Backend Team", "Frontend Team", "DevOps Team"};
        
        for (int i = 0; i < teams.length; i++) {
            Tenant teamTenant = Tenant.builder()
                    .tenantId("acme-eng-" + teams[i])
                    .name(teamNames[i])
                    .description(teamNames[i] + " - Team level tenant")
                    .parentTenantId("acme-engineering")
                    .configuration(createTeamConfiguration())
                    .build();
            
            tenantService.createTenant(teamTenant);
            log.expectedSuccess("Created team tenant: " + teamTenant.getName());
        }
        
        // Display hierarchy
        log.section("Tenant Hierarchy Created");
        displayTenantHierarchy("acme-corp", 0);
    }
    
    private void configureTenantLimits() throws Exception {
        log.step("Configuring tenant-specific resource limits...");
        
        // Update Engineering department with higher limits
        TenantConfiguration engConfig = TenantConfiguration.builder()
                .resourceLimits(TenantConfiguration.ResourceLimits.builder()
                        .maxConcurrentTransfers(50)
                        .maxBandwidthBytesPerSecond(500L * 1024 * 1024) // 500 MB/s
                        .maxStorageBytes(100L * 1024 * 1024 * 1024) // 100 GB
                        .maxTransfersPerDay(5000)
                        .maxTransferSizeBytes(5L * 1024 * 1024 * 1024) // 5 GB
                        .build())
                .transferPolicies(TenantConfiguration.TransferPolicies.builder()
                        .allowedProtocols(new HashSet<>(Arrays.asList("http", "https", "sftp")))
                        .defaultTimeout(Duration.ofHours(2))
                        .defaultRetryAttempts(5)
                        .requireChecksumVerification(true)
                        .build())
                .build();
        
        tenantService.updateTenantConfiguration("acme-engineering", engConfig);
        log.expectedSuccess("Updated Engineering department limits (high-performance)");
        
        // Update Sales department with moderate limits
        TenantConfiguration salesConfig = TenantConfiguration.builder()
                .resourceLimits(TenantConfiguration.ResourceLimits.builder()
                        .maxConcurrentTransfers(20)
                        .maxBandwidthBytesPerSecond(100L * 1024 * 1024) // 100 MB/s
                        .maxStorageBytes(20L * 1024 * 1024 * 1024) // 20 GB
                        .maxTransfersPerDay(1000)
                        .maxTransferSizeBytes(1L * 1024 * 1024 * 1024) // 1 GB
                        .build())
                .build();
        
        tenantService.updateTenantConfiguration("acme-sales", salesConfig);
        log.expectedSuccess("Updated Sales department limits (moderate)");
        
        log.bullet("Engineering: High-performance limits for development workloads");
        log.bullet("Sales: Moderate limits for business document transfers");
        log.bullet("Marketing: Default limits inherited from root tenant");
    }
    
    private void demonstrateResourceTracking() throws Exception {
        log.step("Demonstrating resource usage tracking...");
        
        // Simulate resource usage for different tenants
        String[] tenants = {"acme-engineering", "acme-sales", "acme-eng-backend"};
        long[] concurrentTransfers = {15, 5, 8};
        long[] bandwidthUsage = {200L * 1024 * 1024, 50L * 1024 * 1024, 100L * 1024 * 1024};
        long[] storageUsage = {30L * 1024 * 1024 * 1024, 5L * 1024 * 1024 * 1024, 10L * 1024 * 1024 * 1024};
        
        for (int i = 0; i < tenants.length; i++) {
            ResourceUsage usage = ResourceUsage.builder()
                    .tenantId(tenants[i])
                    .currentConcurrentTransfers(concurrentTransfers[i])
                    .currentBandwidthBytesPerSecond(bandwidthUsage[i])
                    .currentStorageBytes(storageUsage[i])
                    .dailyTransferCount(100 + i * 50)
                    .dailyBytesTransferred((1L + i) * 1024 * 1024 * 1024)
                    .totalTransferCount(1000 + i * 500)
                    .totalBytesTransferred((10L + i * 5) * 1024 * 1024 * 1024)
                    .build();
            
            resourceService.recordUsage(usage);
            log.expectedSuccess("Recorded usage for " + tenants[i]);
        }
        
        // Display usage statistics
        log.section("Current Resource Usage");
        for (String tenantId : tenants) {
            ResourceUsage usage = resourceService.getCurrentUsage(tenantId).orElse(null);
            if (usage != null) {
                log.indentedKeyValue(tenantId, 
                        String.format("%d transfers, %.1f MB/s, %.1f GB storage",
                                usage.getCurrentConcurrentTransfers(),
                                usage.getCurrentBandwidthBytesPerSecond() / (1024.0 * 1024.0),
                                usage.getCurrentStorageBytes() / (1024.0 * 1024.0 * 1024.0)));
            }
        }
    }
    
    private void testQuotaEnforcement() throws Exception {
        log.step("Testing quota enforcement (INTENTIONAL FAILURE SCENARIOS)...");
        
        // Test 1: Try to exceed concurrent transfer limit
        try {
            ResourceManagementService.ResourceValidationResult result = 
                    resourceService.validateTransferRequest("acme-sales", 1024 * 1024, 10 * 1024 * 1024);
            
            if (!result.isAllowed()) {
                log.expectedFailure("Correctly blocked transfer - concurrent limit reached");
                log.subDetail("Reason: " + result.getReason());
            } else {
                log.warning("Should have blocked transfer due to concurrent limit");
            }
        } catch (Exception e) {
            log.expectedFailure("Correctly caught quota enforcement error", e);
        }
        
        // Test 2: Try to exceed bandwidth limit
        try {
            ResourceManagementService.ResourceValidationResult result = 
                    resourceService.validateTransferRequest("acme-sales", 1024 * 1024, 200L * 1024 * 1024);
            
            if (!result.isAllowed()) {
                log.expectedFailure("Correctly blocked transfer - bandwidth limit exceeded");
                log.subDetail("Violations: " + result.getViolations());
            } else {
                log.warning("Should have blocked transfer due to bandwidth limit");
            }
        } catch (Exception e) {
            log.expectedFailure("Correctly caught bandwidth limit error", e);
        }
        
        // Test 3: Valid transfer should be allowed
        try {
            ResourceManagementService.ResourceValidationResult result = 
                    resourceService.validateTransferRequest("acme-engineering", 100 * 1024 * 1024, 50 * 1024 * 1024);
            
            if (result.isAllowed()) {
                log.expectedSuccess("Valid transfer correctly allowed for Engineering");
            } else {
                log.warning("Valid transfer was incorrectly blocked: " + result.getReason());
            }
        } catch (Exception e) {
            log.unexpectedError("Unexpected error validating transfer", e);
        }
    }
    
    private void demonstrateConfigurationInheritance() throws Exception {
        log.step("Demonstrating configuration inheritance...");
        
        // Show effective configuration for team tenant (inherits from department and root)
        TenantConfiguration effectiveConfig = tenantService.getEffectiveConfiguration("acme-eng-backend");
        
        if (effectiveConfig != null) {
            log.expectedSuccess("Retrieved effective configuration for Backend Team");
            log.detail("Effective limits for Backend Team (inherited from Engineering):");
            log.subDetail("Max concurrent transfers: " + 
                    effectiveConfig.getResourceLimits().getMaxConcurrentTransfers());
            log.subDetail("Max bandwidth: " + 
                    effectiveConfig.getResourceLimits().getMaxBandwidthBytesPerSecond() / (1024 * 1024) + " MB/s");
            log.subDetail("Allowed protocols: " + 
                    effectiveConfig.getTransferPolicies().getAllowedProtocols());
        } else {
            log.warning("Failed to retrieve effective configuration");
        }
        
        // Show configuration inheritance path
        List<Tenant> path = tenantService.getTenantPath("acme-eng-backend");
        log.section("Configuration inheritance path");
        for (Tenant tenant : path) {
            log.bullet(tenant.getTenantId() + " (" + tenant.getName() + ")");
        }
    }
    
    private void monitorResourceUtilization() throws Exception {
        log.step("Monitoring tenant resource utilization...");
        
        // Get utilization for all active tenants
        List<Tenant> activeTenants = tenantService.getActiveTenants();
        
        log.section("Resource Utilization Summary");
        for (Tenant tenant : activeTenants) {
            ResourceManagementService.ResourceUtilization utilization = 
                    resourceService.getResourceUtilization(tenant.getTenantId());
            
            log.indentedKeyValue(tenant.getName(), 
                    String.format("%.1f%% max utilization", utilization.getMaxUtilization()));
            
            if (utilization.isApproachingLimits(80.0)) {
                log.warning("WARNING: " + tenant.getName() + " approaching resource limits");
            }
        }
        
        // Get aggregated statistics
        ResourceManagementService.AggregatedUsageStats stats = resourceService.getAggregatedUsageStats();
        log.section("System-wide Statistics");
        log.indentedKeyValue("Total tenants", String.valueOf(stats.getTotalTenants()));
        log.indentedKeyValue("Active tenants", String.valueOf(stats.getActiveTenants()));
        log.indentedKeyValue("Total concurrent transfers", String.valueOf(stats.getTotalConcurrentTransfers()));
        log.indentedKeyValue("Total bandwidth usage", 
                stats.getTotalBandwidthBytesPerSecond() / (1024 * 1024) + " MB/s");
        log.indentedKeyValue("Average success rate", 
                String.format("%.2f%%", stats.getAverageSuccessRate() * 100));
        
        log.expectedSuccess("Resource monitoring completed successfully");
    }
    
    private void displayTenantHierarchy(String tenantId, int depth) {
        Tenant tenant = tenantService.getTenant(tenantId).orElse(null);
        if (tenant == null) return;
        
        String indent = "  ".repeat(depth);
        log.info(indent + "├─ " + tenant.getName() + " (" + tenant.getTenantId() + ")");
        
        List<Tenant> children = tenantService.getChildTenants(tenantId);
        for (Tenant child : children) {
            displayTenantHierarchy(child.getTenantId(), depth + 1);
        }
    }
    
    private TenantConfiguration createEnterpriseConfiguration() {
        return TenantConfiguration.builder()
                .resourceLimits(TenantConfiguration.ResourceLimits.builder()
                        .maxConcurrentTransfers(100)
                        .maxBandwidthBytesPerSecond(1024L * 1024 * 1024) // 1 GB/s
                        .maxStorageBytes(1024L * 1024 * 1024 * 1024) // 1 TB
                        .maxTransfersPerDay(10000)
                        .maxTransferSizeBytes(10L * 1024 * 1024 * 1024) // 10 GB
                        .build())
                .transferPolicies(TenantConfiguration.TransferPolicies.builder()
                        .allowedProtocols(new HashSet<>(Arrays.asList("http", "https")))
                        .defaultTimeout(Duration.ofMinutes(30))
                        .defaultRetryAttempts(3)
                        .requireChecksumVerification(true)
                        .build())
                .securitySettings(TenantConfiguration.SecuritySettings.builder()
                        .requireAuthentication(true)
                        .enableAuditLogging(true)
                        .build())
                .build();
    }
    
    private TenantConfiguration createDepartmentConfiguration() {
        return TenantConfiguration.builder()
                .resourceLimits(TenantConfiguration.ResourceLimits.builder()
                        .maxConcurrentTransfers(30)
                        .maxBandwidthBytesPerSecond(200L * 1024 * 1024) // 200 MB/s
                        .maxStorageBytes(50L * 1024 * 1024 * 1024) // 50 GB
                        .maxTransfersPerDay(2000)
                        .build())
                .build();
    }
    
    private TenantConfiguration createTeamConfiguration() {
        return TenantConfiguration.builder()
                .resourceLimits(TenantConfiguration.ResourceLimits.builder()
                        .maxConcurrentTransfers(10)
                        .maxBandwidthBytesPerSecond(50L * 1024 * 1024) // 50 MB/s
                        .maxStorageBytes(10L * 1024 * 1024 * 1024) // 10 GB
                        .maxTransfersPerDay(500)
                        .build())
                .build();
    }
}
