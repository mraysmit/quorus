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

import dev.mars.quorus.examples.util.TestResultLogger;
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
 */
public class MultiTenantExample {
    
    private TenantService tenantService;
    private ResourceManagementService resourceService;
    
    public static void main(String[] args) {
        System.out.println("=== Quorus Multi-Tenant Example ===");
        System.out.println("Demonstrating enterprise multi-tenancy with hierarchical structure,");
        System.out.println("resource management, and tenant isolation capabilities.");
        System.out.println();
        
        try {
            MultiTenantExample example = new MultiTenantExample();
            example.runExample();
            TestResultLogger.logExampleCompletion("Multi-Tenant Example");
        } catch (Exception e) {
            TestResultLogger.logUnexpectedError("Multi-Tenant Example", e);
            System.err.println("\nFull stack trace:");
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public void runExample() throws Exception {
        // Initialize services
        tenantService = new SimpleTenantService();
        resourceService = new SimpleResourceManagementService(tenantService);
        
        TestResultLogger.logTestSection("1. Creating hierarchical tenant structure", false);
        createTenantHierarchy();
        
        TestResultLogger.logTestSection("2. Configuring tenant-specific resource limits", false);
        configureTenantLimits();
        
        TestResultLogger.logTestSection("3. Demonstrating resource usage tracking", false);
        demonstrateResourceTracking();
        
        TestResultLogger.logTestSection("4. Testing quota enforcement", true);
        testQuotaEnforcement();
        
        TestResultLogger.logTestSection("5. Showing configuration inheritance", false);
        demonstrateConfigurationInheritance();
        
        TestResultLogger.logTestSection("6. Monitoring tenant resource utilization", false);
        monitorResourceUtilization();
    }
    
    private void createTenantHierarchy() throws Exception {
        System.out.println("Creating corporate tenant hierarchy...");
        
        // Create root organization tenant
        Tenant rootTenant = Tenant.builder()
                .tenantId("acme-corp")
                .name("ACME Corporation")
                .description("Root organization tenant")
                .configuration(createEnterpriseConfiguration())
                .build();
        
        tenantService.createTenant(rootTenant);
        TestResultLogger.logExpectedSuccess("Created root tenant: " + rootTenant.getName());
        
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
            TestResultLogger.logExpectedSuccess("Created department tenant: " + deptTenant.getName());
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
            TestResultLogger.logExpectedSuccess("Created team tenant: " + teamTenant.getName());
        }
        
        // Display hierarchy
        System.out.println("\nTenant Hierarchy Created:");
        displayTenantHierarchy("acme-corp", 0);
    }
    
    private void configureTenantLimits() throws Exception {
        System.out.println("Configuring tenant-specific resource limits...");
        
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
        TestResultLogger.logExpectedSuccess("Updated Engineering department limits (high-performance)");
        
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
        TestResultLogger.logExpectedSuccess("Updated Sales department limits (moderate)");
        
        System.out.println("   Engineering: High-performance limits for development workloads");
        System.out.println("   Sales: Moderate limits for business document transfers");
        System.out.println("   Marketing: Default limits inherited from root tenant");
    }
    
    private void demonstrateResourceTracking() throws Exception {
        System.out.println("Demonstrating resource usage tracking...");
        
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
            TestResultLogger.logExpectedSuccess("Recorded usage for " + tenants[i]);
        }
        
        // Display usage statistics
        System.out.println("\nCurrent Resource Usage:");
        for (String tenantId : tenants) {
            ResourceUsage usage = resourceService.getCurrentUsage(tenantId).orElse(null);
            if (usage != null) {
                System.out.printf("   %s: %d transfers, %.1f MB/s, %.1f GB storage%n",
                        tenantId,
                        usage.getCurrentConcurrentTransfers(),
                        usage.getCurrentBandwidthBytesPerSecond() / (1024.0 * 1024.0),
                        usage.getCurrentStorageBytes() / (1024.0 * 1024.0 * 1024.0));
            }
        }
    }
    
    private void testQuotaEnforcement() throws Exception {
        System.out.println("Testing quota enforcement (INTENTIONAL FAILURE SCENARIOS)...");
        
        // Test 1: Try to exceed concurrent transfer limit
        try {
            ResourceManagementService.ResourceValidationResult result = 
                    resourceService.validateTransferRequest("acme-sales", 1024 * 1024, 10 * 1024 * 1024);
            
            if (!result.isAllowed()) {
                TestResultLogger.logExpectedFailure("Correctly blocked transfer - concurrent limit reached");
                System.out.println("     Reason: " + result.getReason());
            } else {
                TestResultLogger.logUnexpectedResult("Should have blocked transfer due to concurrent limit");
            }
        } catch (Exception e) {
            TestResultLogger.logExpectedFailure("Correctly caught quota enforcement error", e);
        }
        
        // Test 2: Try to exceed bandwidth limit
        try {
            ResourceManagementService.ResourceValidationResult result = 
                    resourceService.validateTransferRequest("acme-sales", 1024 * 1024, 200L * 1024 * 1024);
            
            if (!result.isAllowed()) {
                TestResultLogger.logExpectedFailure("Correctly blocked transfer - bandwidth limit exceeded");
                System.out.println("     Violations: " + result.getViolations());
            } else {
                TestResultLogger.logUnexpectedResult("Should have blocked transfer due to bandwidth limit");
            }
        } catch (Exception e) {
            TestResultLogger.logExpectedFailure("Correctly caught bandwidth limit error", e);
        }
        
        // Test 3: Valid transfer should be allowed
        try {
            ResourceManagementService.ResourceValidationResult result = 
                    resourceService.validateTransferRequest("acme-engineering", 100 * 1024 * 1024, 50 * 1024 * 1024);
            
            if (result.isAllowed()) {
                TestResultLogger.logExpectedSuccess("Valid transfer correctly allowed for Engineering");
            } else {
                TestResultLogger.logUnexpectedResult("Valid transfer was incorrectly blocked: " + result.getReason());
            }
        } catch (Exception e) {
            TestResultLogger.logUnexpectedException("Unexpected error validating transfer", e);
        }
    }
    
    private void demonstrateConfigurationInheritance() throws Exception {
        System.out.println("Demonstrating configuration inheritance...");
        
        // Show effective configuration for team tenant (inherits from department and root)
        TenantConfiguration effectiveConfig = tenantService.getEffectiveConfiguration("acme-eng-backend");
        
        if (effectiveConfig != null) {
            TestResultLogger.logExpectedSuccess("Retrieved effective configuration for Backend Team");
            System.out.println("   Effective limits for Backend Team (inherited from Engineering):");
            System.out.println("     Max concurrent transfers: " + 
                    effectiveConfig.getResourceLimits().getMaxConcurrentTransfers());
            System.out.println("     Max bandwidth: " + 
                    effectiveConfig.getResourceLimits().getMaxBandwidthBytesPerSecond() / (1024 * 1024) + " MB/s");
            System.out.println("     Allowed protocols: " + 
                    effectiveConfig.getTransferPolicies().getAllowedProtocols());
        } else {
            TestResultLogger.logUnexpectedResult("Failed to retrieve effective configuration");
        }
        
        // Show configuration inheritance path
        List<Tenant> path = tenantService.getTenantPath("acme-eng-backend");
        System.out.println("\n   Configuration inheritance path:");
        for (Tenant tenant : path) {
            System.out.println("     " + tenant.getTenantId() + " (" + tenant.getName() + ")");
        }
    }
    
    private void monitorResourceUtilization() throws Exception {
        System.out.println("Monitoring tenant resource utilization...");
        
        // Get utilization for all active tenants
        List<Tenant> activeTenants = tenantService.getActiveTenants();
        
        System.out.println("\nResource Utilization Summary:");
        for (Tenant tenant : activeTenants) {
            ResourceManagementService.ResourceUtilization utilization = 
                    resourceService.getResourceUtilization(tenant.getTenantId());
            
            System.out.printf("   %s: %.1f%% max utilization%n",
                    tenant.getName(),
                    utilization.getMaxUtilization());
            
            if (utilization.isApproachingLimits(80.0)) {
                System.out.println("     ⚠️  WARNING: Approaching resource limits");
            }
        }
        
        // Get aggregated statistics
        ResourceManagementService.AggregatedUsageStats stats = resourceService.getAggregatedUsageStats();
        System.out.println("\nSystem-wide Statistics:");
        System.out.println("   Total tenants: " + stats.getTotalTenants());
        System.out.println("   Active tenants: " + stats.getActiveTenants());
        System.out.println("   Total concurrent transfers: " + stats.getTotalConcurrentTransfers());
        System.out.println("   Total bandwidth usage: " + 
                stats.getTotalBandwidthBytesPerSecond() / (1024 * 1024) + " MB/s");
        System.out.println("   Average success rate: " + 
                String.format("%.2f%%", stats.getAverageSuccessRate() * 100));
        
        TestResultLogger.logExpectedSuccess("Resource monitoring completed successfully");
    }
    
    private void displayTenantHierarchy(String tenantId, int depth) {
        Tenant tenant = tenantService.getTenant(tenantId).orElse(null);
        if (tenant == null) return;
        
        String indent = "  ".repeat(depth);
        System.out.println(indent + "├─ " + tenant.getName() + " (" + tenant.getTenantId() + ")");
        
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
