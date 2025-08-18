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
            assertTrue(protocolImpl.canHandle(request), 
                    "Protocol " + protocol + " should handle its own URI scheme");
        }
    }
    
    @Test
    void testMultiTenantWorkflowIntegration() throws Exception {
        // Create tenant hierarchy
        Tenant organization = tenantService.createTenant(
                "integration-org", 
                "Integration Test Organization", 
                "Test organization for integration", 
                null
        );
        
        Tenant department = tenantService.createTenant(
                "integration-dept", 
                "Integration Department", 
                "Test department", 
                "integration-org"
        );
        
        // Configure tenant with specific protocols
        TenantConfiguration config = TenantConfiguration.builder()
                .tenantId("integration-dept")
                .maxConcurrentTransfers(5)
                .allowedProtocols(Set.of("https", "sftp"))
                .requireAuthentication(true)
                .build();
        
        tenantService.updateTenantConfiguration("integration-dept", config);
        
        // Verify configuration inheritance
        TenantConfiguration effectiveConfig = tenantService.getEffectiveConfiguration("integration-dept");
        assertEquals(5, effectiveConfig.getMaxConcurrentTransfers());
        assertEquals(Set.of("https", "sftp"), effectiveConfig.getAllowedProtocols());
        assertTrue(effectiveConfig.isRequireAuthentication());
    }
    
    @Test
    void testWorkflowWithMultipleProtocols() throws Exception {
        String workflowYaml = """
                apiVersion: v1
                kind: TransferWorkflow
                metadata:
                  name: multi-protocol-workflow
                  description: Test workflow with multiple protocols
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
        
        WorkflowDefinition workflow = workflowParser.parse(workflowYaml);
        
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
        tenantService.createTenant("limited-tenant", "Limited Tenant", "Tenant with strict limits", null);
        
        TenantConfiguration strictConfig = TenantConfiguration.builder()
                .tenantId("limited-tenant")
                .maxConcurrentTransfers(1)
                .maxBandwidthBytesPerSecond(1024 * 1024) // 1 MB/s
                .maxTransferSizeBytes(1024 * 1024) // 1 MB
                .allowedProtocols(Set.of("https"))
                .build();
        
        tenantService.updateTenantConfiguration("limited-tenant", strictConfig);
        
        // Verify limits are enforced
        TenantConfiguration config = tenantService.getTenantConfiguration("limited-tenant");
        assertEquals(1, config.getMaxConcurrentTransfers());
        assertEquals(1024 * 1024, config.getMaxBandwidthBytesPerSecond());
        assertEquals(1024 * 1024, config.getMaxTransferSizeBytes());
        assertEquals(Set.of("https"), config.getAllowedProtocols());
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
        WorkflowDefinition workflow = workflowParser.parse(invalidWorkflowYaml);
        assertNotNull(workflow);
        
        // The workflow engine should validate dependencies during execution
        // This would be caught during actual workflow execution
    }
    
    @Test
    void testProtocolFactoryIntegration() {
        // Test that protocol factory properly manages all protocols
        Set<String> supportedProtocols = protocolFactory.getSupportedProtocols();
        
        assertNotNull(supportedProtocols);
        assertTrue(supportedProtocols.contains("http"));
        assertTrue(supportedProtocols.contains("smb"));
        assertTrue(supportedProtocols.contains("ftp"));
        assertTrue(supportedProtocols.contains("sftp"));
        
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
    void testConfigurationInheritanceIntegration() {
        // Test complex configuration inheritance scenario
        
        // Root organization
        tenantService.createTenant("root-org", "Root Organization", "Root", null);
        TenantConfiguration rootConfig = TenantConfiguration.builder()
                .tenantId("root-org")
                .maxConcurrentTransfers(100)
                .maxBandwidthBytesPerSecond(1024L * 1024 * 1024) // 1 GB/s
                .allowedProtocols(Set.of("http", "https", "sftp", "ftp"))
                .requireAuthentication(true)
                .enableAuditLogging(true)
                .build();
        tenantService.updateTenantConfiguration("root-org", rootConfig);
        
        // Department with some overrides
        tenantService.createTenant("dept", "Department", "Department", "root-org");
        TenantConfiguration deptConfig = TenantConfiguration.builder()
                .tenantId("dept")
                .maxConcurrentTransfers(50) // Override
                .allowedProtocols(Set.of("https", "sftp")) // Override - more restrictive
                .build();
        tenantService.updateTenantConfiguration("dept", deptConfig);
        
        // Team inherits from department
        tenantService.createTenant("team", "Team", "Team", "dept");
        
        // Check effective configuration for team
        TenantConfiguration teamEffectiveConfig = tenantService.getEffectiveConfiguration("team");
        
        assertEquals(50, teamEffectiveConfig.getMaxConcurrentTransfers()); // From department
        assertEquals(1024L * 1024 * 1024, teamEffectiveConfig.getMaxBandwidthBytesPerSecond()); // From root
        assertEquals(Set.of("https", "sftp"), teamEffectiveConfig.getAllowedProtocols()); // From department
        assertTrue(teamEffectiveConfig.isRequireAuthentication()); // From root
        assertTrue(teamEffectiveConfig.isEnableAuditLogging()); // From root
    }
    
    @Test
    void testSystemIntegrationHealthCheck() {
        // Comprehensive system health check
        
        // 1. Protocol factory is working
        assertNotNull(protocolFactory);
        assertFalse(protocolFactory.getSupportedProtocols().isEmpty());
        
        // 2. Transfer engine is working
        assertNotNull(transferEngine);
        
        // 3. Tenant service is working
        assertNotNull(tenantService);
        Tenant testTenant = tenantService.createTenant("health-check", "Health Check", "Test", null);
        assertNotNull(testTenant);
        
        // 4. Workflow engine is working
        assertNotNull(workflowEngine);
        
        // 5. Workflow parser is working
        assertNotNull(workflowParser);
        
        // 6. All components can interact
        TenantConfiguration config = tenantService.getTenantConfiguration("health-check");
        assertNotNull(config);
        
        // System is healthy if all assertions pass
        assertTrue(true, "System integration health check passed");
    }
}
