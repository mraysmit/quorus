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

package dev.mars.quorus.workflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
/**
 * Description for VariableResolverTest
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-18
 */

class VariableResolverTest {
    
    private VariableResolver resolver;
    
    @BeforeEach
    void setUp() {
        Map<String, Object> globalVariables = Map.of(
                "baseUrl", "https://example.com",
                "outputDir", "/tmp/downloads",
                "version", "1.0"
        );
        resolver = new VariableResolver(globalVariables);
    }
    
    @Test
    void testSimpleVariableResolution() {
        String template = "{{baseUrl}}/file.txt";
        String result = resolver.resolve(template);
        assertEquals("https://example.com/file.txt", result);
    }
    
    @Test
    void testMultipleVariableResolution() {
        String template = "{{baseUrl}}/v{{version}}/{{outputDir}}/file.txt";
        String result = resolver.resolve(template);
        assertEquals("https://example.com/v1.0//tmp/downloads/file.txt", result);
    }
    
    @Test
    void testContextVariableOverride() {
        Map<String, Object> contextVars = Map.of("baseUrl", "https://test.com");
        VariableResolver contextResolver = resolver.withContext(contextVars);
        
        String template = "{{baseUrl}}/file.txt";
        String result = contextResolver.resolve(template);
        assertEquals("https://test.com/file.txt", result);
    }
    
    @Test
    void testNoVariables() {
        String template = "plain text without variables";
        String result = resolver.resolve(template);
        assertEquals("plain text without variables", result);
    }
    
    @Test
    void testNullTemplate() {
        String result = resolver.resolve((String) null);
        assertNull(result);
    }
    
    @Test
    void testEmptyTemplate() {
        String result = resolver.resolve("");
        assertEquals("", result);
    }
    
    @Test
    void testUnknownVariable() {
        String template = "{{unknownVariable}}";
        assertThrows(VariableResolver.VariableResolutionException.class, () -> {
            resolver.resolve(template);
        });
    }
    
    @Test
    void testHasVariables() {
        assertTrue(resolver.hasVariables("{{baseUrl}}/file.txt"));
        assertFalse(resolver.hasVariables("plain text"));
        assertFalse(resolver.hasVariables(null));
        assertFalse(resolver.hasVariables(""));
    }
    
    @Test
    void testGetVariableNames() {
        String template = "{{baseUrl}}/v{{version}}/{{outputDir}}/file.txt";
        Set<String> variables = resolver.getVariableNames(template);
        
        assertEquals(3, variables.size());
        assertTrue(variables.contains("baseUrl"));
        assertTrue(variables.contains("version"));
        assertTrue(variables.contains("outputDir"));
    }
    
    @Test
    void testGetVariableNamesEmpty() {
        Set<String> variables = resolver.getVariableNames("plain text");
        assertTrue(variables.isEmpty());
        
        variables = resolver.getVariableNames(null);
        assertTrue(variables.isEmpty());
    }
    
    @Test
    void testResolveTransferDefinition() {
        TransferGroup.TransferDefinition transfer = new TransferGroup.TransferDefinition(
                "test-transfer",
                "{{baseUrl}}/file.txt",
                "{{outputDir}}/file.txt",
                "http",
                Map.of("timeout", "{{version}}0s"),
                null
        );
        
        TransferGroup.TransferDefinition resolved = resolver.resolve(transfer);
        
        assertEquals("test-transfer", resolved.getName());
        assertEquals("https://example.com/file.txt", resolved.getSource());
        assertEquals("/tmp/downloads/file.txt", resolved.getDestination());
        assertEquals("http", resolved.getProtocol());
        assertEquals("1.00s", resolved.getOptions().get("timeout"));
    }
    
    @Test
    void testResolveTransferGroup() {
        TransferGroup.TransferDefinition transfer = new TransferGroup.TransferDefinition(
                "test-transfer",
                "{{baseUrl}}/{{fileName}}",
                "{{outputDir}}/{{fileName}}",
                "http",
                Map.of(),
                null
        );
        
        TransferGroup group = new TransferGroup(
                "test-group",
                "Test group",
                java.util.List.of(),
                null,
                Map.of("fileName", "test.txt"),
                java.util.List.of(transfer),
                false,
                0
        );
        
        TransferGroup resolved = resolver.resolve(group);
        
        assertEquals("test-group", resolved.getName());
        assertEquals(1, resolved.getTransfers().size());
        
        TransferGroup.TransferDefinition resolvedTransfer = resolved.getTransfers().get(0);
        assertEquals("https://example.com/test.txt", resolvedTransfer.getSource());
        assertEquals("/tmp/downloads/test.txt", resolvedTransfer.getDestination());
    }
    
    @Test
    void testResolveWorkflowDefinition() {
        TransferGroup.TransferDefinition transfer = new TransferGroup.TransferDefinition(
                "test-transfer",
                "{{baseUrl}}/file.txt",
                "{{outputDir}}/file.txt",
                "http",
                Map.of(),
                null
        );
        
        TransferGroup group = new TransferGroup(
                "test-group",
                "Test group",
                java.util.List.of(),
                null,
                Map.of(),
                java.util.List.of(transfer),
                false,
                0
        );
        
        WorkflowDefinition.WorkflowMetadata metadata = new WorkflowDefinition.WorkflowMetadata(
                "test-workflow", "Test workflow", Map.of()
        );
        
        WorkflowDefinition.ExecutionConfig execution = new WorkflowDefinition.ExecutionConfig(
                false, false, 1, java.time.Duration.ofHours(1), "sequential"
        );
        
        WorkflowDefinition.WorkflowSpec spec = new WorkflowDefinition.WorkflowSpec(
                Map.of("workflowVar", "workflowValue"),
                execution,
                java.util.List.of(group)
        );
        
        WorkflowDefinition workflow = new WorkflowDefinition("v1", "TransferWorkflow", metadata, spec);
        
        WorkflowDefinition resolved = resolver.resolve(workflow);
        
        assertEquals("test-workflow", resolved.getMetadata().getName());
        assertEquals(1, resolved.getSpec().getTransferGroups().size());
        
        TransferGroup resolvedGroup = resolved.getSpec().getTransferGroups().get(0);
        TransferGroup.TransferDefinition resolvedTransfer = resolvedGroup.getTransfers().get(0);
        assertEquals("https://example.com/file.txt", resolvedTransfer.getSource());
        assertEquals("/tmp/downloads/file.txt", resolvedTransfer.getDestination());
    }
    
    @Test
    void testSetGlobalVariable() {
        resolver.setGlobalVariable("newVar", "newValue");
        String result = resolver.resolve("{{newVar}}");
        assertEquals("newValue", result);
    }
    
    @Test
    void testSetContextVariable() {
        resolver.setContextVariable("contextVar", "contextValue");
        String result = resolver.resolve("{{contextVar}}");
        assertEquals("contextValue", result);
    }
    
    @Test
    void testGetAllVariables() {
        resolver.setContextVariable("contextVar", "contextValue");
        Map<String, Object> allVars = resolver.getAllVariables();
        
        assertTrue(allVars.containsKey("baseUrl"));
        assertTrue(allVars.containsKey("contextVar"));
        assertEquals("https://example.com", allVars.get("baseUrl"));
        assertEquals("contextValue", allVars.get("contextVar"));
    }
    
    @Test
    void testVariableWithWhitespace() {
        String template = "{{ baseUrl }}/file.txt";
        String result = resolver.resolve(template);
        assertEquals("https://example.com/file.txt", result);
    }
    
    @Test
    void testSpecialCharactersInVariableValue() {
        resolver.setGlobalVariable("specialChars", "value with $pecial ch@rs & symbols!");
        String result = resolver.resolve("{{specialChars}}");
        assertEquals("value with $pecial ch@rs & symbols!", result);
    }
}
