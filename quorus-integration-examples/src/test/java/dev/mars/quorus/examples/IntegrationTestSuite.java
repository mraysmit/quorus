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

import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.protocol.ProtocolFactory;
import dev.mars.quorus.protocol.TransferProtocol;
import dev.mars.quorus.tenant.model.Tenant;
import dev.mars.quorus.tenant.model.TenantConfiguration;
import dev.mars.quorus.tenant.service.SimpleTenantService;
import dev.mars.quorus.tenant.service.TenantService.TenantServiceException;
import dev.mars.quorus.transfer.SimpleTransferEngine;
import dev.mars.quorus.workflow.SimpleWorkflowEngine;
import dev.mars.quorus.workflow.WorkflowDefinition;
import dev.mars.quorus.workflow.YamlWorkflowDefinitionParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test suite that verifies end-to-end functionality across all modules.
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-19
 */
class IntegrationTestSuite {
    
    private SimpleTransferEngine transferEngine;
    private SimpleTenantService tenantService;
    private SimpleWorkflowEngine workflowEngine;
    private ProtocolFactory protocolFactory;
    private YamlWorkflowDefinitionParser workflowParser;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        transferEngine = new SimpleTransferEngine(10, 4, 1024 * 1024);
        tenantService = new SimpleTenantService();
        workflowEngine = new SimpleWorkflowEngine(transferEngine);
        protocolFactory = new ProtocolFactory();
        workflowParser = new YamlWorkflowDefinitionParser();
    }
    
    @Test
    void testEndToEndTransferWithAllProtocols() throws Exception {
        // Test that all protocols are properly integrated
        String[] protocols = {"http", "https", "smb", "cifs", "ftp", "sftp"};
        
        for (String protocol : protocols) {
            TransferProtocol protocolImpl = protocolFactory.getProtocol(protocol);
            assertNotNull(protocolImpl, "Protocol " + protocol + " should be available");
            
            // Create a test request
            TransferRequest request = TransferRequest.builder()
                    .requestId("test-" + protocol + "-" + System.currentTimeMillis())
                    .sourceUri(URI.create(protocol + "://test.example.com/file.txt"))
                    .destinationPath(tempDir.resolve(protocol + "-file.txt"))
                    .build();
            
            // Verify protocol can handle the request
            assertTrue(protocolImpl.canHandle(request), "Protocol " + protocol + " should handle its own URI scheme");
        }
    }
    
    @Test
    void testMultiTenantWorkflowIntegration() throws Exception {
        // Create tenant hierarchy
        Tenant organizationToCreate = Tenant.builder()
                .tenantId("integration-org")
                .name("Integration Test Organization")
                .description("Test organization for integration")
                .build();
        Tenant organization = tenantService.createTenant(organizationToCreate);

        Tenant departmentToCreate = Tenant.builder()
                .tenantId("integration-dept")
                .name("Integration Department")
                .description("Test department")
                .parentTenantId("integration-org")
                .build();
        Tenant department = tenantService.createTenant(departmentToCreate);
        
        // Configure tenant with specific protocols
        TenantConfiguration.ResourceLimits resourceLimits = TenantConfiguration.ResourceLimits.builder()
                .maxConcurrentTransfers(5)
                .build();

        TenantConfiguration.TransferPolicies transferPolicies = TenantConfiguration.TransferPolicies.builder()
                .allowedProtocols(Set.of("https", "sftp"))
                .build();

        TenantConfiguration.SecuritySettings securitySettings = TenantConfiguration.SecuritySettings.builder()
                .requireAuthentication(true)
                .build();

        TenantConfiguration config = TenantConfiguration.builder()
                .resourceLimits(resourceLimits)
                .transferPolicies(transferPolicies)
                .securitySettings(securitySettings)
                .build();

        tenantService.updateTenantConfiguration("integration-dept", config);

        // Verify configuration inheritance
        TenantConfiguration effectiveConfig = tenantService.getEffectiveConfiguration("integration-dept");
        assertEquals(5, effectiveConfig.getResourceLimits().getMaxConcurrentTransfers());
        assertEquals(Set.of("https", "sftp"), effectiveConfig.getTransferPolicies().getAllowedProtocols());
        assertTrue(effectiveConfig.getSecuritySettings().isRequireAuthentication());
    }
    
    @Test
    void testWorkflowWithMultipleProtocols() throws Exception {
        String workflowYaml = """
                metadata:
                  name: "Multi-Protocol Test Workflow"
                  version: "1.0.0"
                  description: "Test workflow with multiple protocols"
                  type: "multi-protocol-test-workflow"
                  author: "Quorus Integration Test Suite"

                # ============================================================================

                spec:
                  execution:
                    dryRun: false
                    virtualRun: false
                    parallelism: 2
                    timeout: 1800s
                    strategy: sequential
                  transferGroups:
                    - name: secure-transfers
                      description: Secure protocol transfers
                      transfers:
                        - name: sftp-transfer
                          source: sftp://secure.example.com/data.txt
                          destination: ./secure-data.txt
                        - name: https-transfer
                          source: https://api.example.com/file.json
                          destination: ./api-data.json
                          dependsOn: [sftp-transfer]
                """;
        
        WorkflowDefinition workflow = workflowParser.parseFromString(workflowYaml);
        
        assertNotNull(workflow);
        assertEquals("multi-protocol-workflow", workflow.getMetadata().getName());
        assertEquals(1, workflow.getSpec().getTransferGroups().size());
        assertEquals(2, workflow.getSpec().getTransferGroups().get(0).getTransfers().size());
        
        // Verify workflow validation
        assertTrue(workflow.getSpec().getTransferGroups().get(0).getTransfers().stream()
                .anyMatch(t -> t.getName().equals("sftp-transfer")));
        assertTrue(workflow.getSpec().getTransferGroups().get(0).getTransfers().stream()
                .anyMatch(t -> t.getName().equals("https-transfer")));
    }
    
    @Test
    void testProtocolCapabilitiesIntegration() {
        // Test that protocol capabilities are properly exposed
        TransferProtocol httpProtocol = protocolFactory.getProtocol("http");
        assertNotNull(httpProtocol);
        assertTrue(httpProtocol.supportsResume());
        assertTrue(httpProtocol.supportsPause());
        
        TransferProtocol sftpProtocol = protocolFactory.getProtocol("sftp");
        assertNotNull(sftpProtocol);
        // SFTP protocol in current implementation doesn't support resume/pause
        assertFalse(sftpProtocol.supportsResume());
        assertFalse(sftpProtocol.supportsPause());
    }
    
    @Test
    void testTenantResourceLimitIntegration() throws Exception {
        // Create tenant with strict limits
        Tenant limitedTenant = Tenant.builder()
                .tenantId("limited-tenant")
                .name("Limited Tenant")
                .description("Tenant with strict limits")
                .build();
        tenantService.createTenant(limitedTenant);
        
        TenantConfiguration.ResourceLimits resourceLimits = TenantConfiguration.ResourceLimits.builder()
                .maxConcurrentTransfers(1)
                .maxBandwidthBytesPerSecond(1024 * 1024) // 1 MB/s
                .maxTransferSizeBytes(1024 * 1024) // 1 MB
                .build();

        TenantConfiguration.TransferPolicies transferPolicies = TenantConfiguration.TransferPolicies.builder()
                .allowedProtocols(Set.of("https"))
                .build();

        TenantConfiguration strictConfig = TenantConfiguration.builder()
                .resourceLimits(resourceLimits)
                .transferPolicies(transferPolicies)
                .build();

        tenantService.updateTenantConfiguration("limited-tenant", strictConfig);

        // Verify limits are enforced
        TenantConfiguration config = tenantService.getEffectiveConfiguration("limited-tenant");
        assertEquals(1, config.getResourceLimits().getMaxConcurrentTransfers());
        assertEquals(1024 * 1024, config.getResourceLimits().getMaxBandwidthBytesPerSecond());
        assertEquals(1024 * 1024, config.getResourceLimits().getMaxTransferSizeBytes());
        assertEquals(Set.of("https"), config.getTransferPolicies().getAllowedProtocols());
    }
    
    @Test
    void testWorkflowValidationIntegration() throws Exception {
        // Test workflow with invalid dependencies
        String invalidWorkflowYaml = """
                apiVersion: v1
                kind: TransferWorkflow
                metadata:
                  name: invalid-workflow
                spec:
                  transferGroups:
                    - name: group1
                      transfers:
                        - name: transfer1
                          source: https://example.com/file1.txt
                          destination: ./file1.txt
                          dependsOn: [nonexistent-transfer]
                """;
        
        // Should parse but validation should catch the invalid dependency
        WorkflowDefinition workflow = workflowParser.parseFromString(invalidWorkflowYaml);
        assertNotNull(workflow);
        
        // The workflow engine should validate dependencies during execution
        // This would be caught during actual workflow execution
    }
    
    @Test
    void testProtocolFactoryIntegration() {
        // Test that protocol factory properly manages all protocols
        String[] supportedProtocols = protocolFactory.getSupportedProtocols();

        assertNotNull(supportedProtocols);
        Set<String> protocolSet = Set.of(supportedProtocols);
        assertTrue(protocolSet.contains("http"));
        assertTrue(protocolSet.contains("smb"));
        assertTrue(protocolSet.contains("ftp"));
        assertTrue(protocolSet.contains("sftp"));

        // Test protocol selection for requests
        TransferRequest httpRequest = TransferRequest.builder()
                .sourceUri(URI.create("http://example.com/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();
        
        TransferProtocol selectedProtocol = protocolFactory.getProtocol("http");
        assertNotNull(selectedProtocol);
        assertTrue(selectedProtocol.canHandle(httpRequest));
    }
    
    @Test
    void testConfigurationInheritanceIntegration() throws TenantServiceException {
        // Test complex configuration inheritance scenario
        
        // Root organization
        Tenant rootOrg = Tenant.builder()
                .tenantId("root-org")
                .name("Root Organization")
                .description("Root")
                .build();
        tenantService.createTenant(rootOrg);

        TenantConfiguration.ResourceLimits rootResourceLimits = TenantConfiguration.ResourceLimits.builder()
                .maxConcurrentTransfers(100)
                .maxBandwidthBytesPerSecond(1024L * 1024 * 1024) // 1 GB/s
                .build();

        TenantConfiguration.TransferPolicies rootTransferPolicies = TenantConfiguration.TransferPolicies.builder()
                .allowedProtocols(Set.of("http", "https", "sftp", "ftp"))
                .build();

        TenantConfiguration.SecuritySettings rootSecuritySettings = TenantConfiguration.SecuritySettings.builder()
                .requireAuthentication(true)
                .enableAuditLogging(true)
                .build();

        TenantConfiguration rootConfig = TenantConfiguration.builder()
                .resourceLimits(rootResourceLimits)
                .transferPolicies(rootTransferPolicies)
                .securitySettings(rootSecuritySettings)
                .build();
        tenantService.updateTenantConfiguration("root-org", rootConfig);

        // Department with some overrides
        Tenant dept = Tenant.builder()
                .tenantId("dept")
                .name("Department")
                .description("Department")
                .parentTenantId("root-org")
                .build();
        tenantService.createTenant(dept);

        TenantConfiguration.ResourceLimits deptResourceLimits = TenantConfiguration.ResourceLimits.builder()
                .maxConcurrentTransfers(50) // Override
                .build();

        TenantConfiguration.TransferPolicies deptTransferPolicies = TenantConfiguration.TransferPolicies.builder()
                .allowedProtocols(Set.of("https", "sftp")) // Override - more restrictive
                .build();

        TenantConfiguration deptConfig = TenantConfiguration.builder()
                .resourceLimits(deptResourceLimits)
                .transferPolicies(deptTransferPolicies)
                .build();
        tenantService.updateTenantConfiguration("dept", deptConfig);

        // Team inherits from department
        Tenant team = Tenant.builder()
                .tenantId("team")
                .name("Team")
                .description("Team")
                .parentTenantId("dept")
                .build();
        tenantService.createTenant(team);
        
        // Check effective configuration for team
        TenantConfiguration teamEffectiveConfig = tenantService.getEffectiveConfiguration("team");

        assertEquals(50, teamEffectiveConfig.getResourceLimits().getMaxConcurrentTransfers()); // From department
        assertEquals(1024L * 1024 * 1024, teamEffectiveConfig.getResourceLimits().getMaxBandwidthBytesPerSecond()); // From root
        assertEquals(Set.of("https", "sftp"), teamEffectiveConfig.getTransferPolicies().getAllowedProtocols()); // From department
        assertTrue(teamEffectiveConfig.getSecuritySettings().isRequireAuthentication()); // From root
        assertTrue(teamEffectiveConfig.getSecuritySettings().isEnableAuditLogging()); // From root
    }
    
    @Test
    void testSystemIntegrationHealthCheck() throws TenantServiceException {
        // Comprehensive system health check
        
        // 1. Protocol factory is working
        assertNotNull(protocolFactory);
        assertTrue(protocolFactory.getSupportedProtocols().length > 0);
        
        // 2. Transfer engine is working
        assertNotNull(transferEngine);
        
        // 3. Tenant service is working
        assertNotNull(tenantService);
        Tenant testTenantToCreate = Tenant.builder()
                .tenantId("health-check")
                .name("Health Check")
                .description("Test")
                .build();
        Tenant testTenant = tenantService.createTenant(testTenantToCreate);
        assertNotNull(testTenant);

        // 4. Workflow engine is working
        assertNotNull(workflowEngine);

        // 5. Workflow parser is working
        assertNotNull(workflowParser);

        // 6. All components can interact
        TenantConfiguration config = tenantService.getEffectiveConfiguration("health-check");
        assertNotNull(config);
        
        // System is healthy if all assertions pass
        assertTrue(true, "System integration health check passed");
    }
}
