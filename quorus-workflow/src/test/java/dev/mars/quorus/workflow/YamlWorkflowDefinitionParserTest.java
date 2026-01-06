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

import static org.junit.jupiter.api.Assertions.*;
/**
 * Description for YamlWorkflowDefinitionParserTest
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-18
 */

class YamlWorkflowDefinitionParserTest {
    
    private YamlWorkflowDefinitionParser parser;
    
    @BeforeEach
    void setUp() {
        parser = new YamlWorkflowDefinitionParser();
    }
    
    @Test
    void testParseSimpleWorkflow() throws WorkflowParseException {
        String yaml = """
                metadata:
                  name: "Simple Test Workflow"
                  version: "1.0.0"
                  description: "A simple test workflow"
                  type: "test-workflow"
                  author: "Quorus Test Suite"

                # ============================================================================

                spec:
                  execution:
                    dryRun: false
                    virtualRun: false
                    parallelism: 1
                    timeout: 3600s
                    strategy: sequential
                  transferGroups:
                    - name: group1
                      description: First transfer group
                      transfers:
                        - name: transfer1
                          source: http://example.com/file1.txt
                          destination: /tmp/file1.txt
                          protocol: http
                """;
        
        WorkflowDefinition definition = parser.parseFromString(yaml);
        
        assertNotNull(definition);
        assertEquals("v1", definition.getApiVersion()); // Still supported for backward compatibility
        assertEquals("TransferWorkflow", definition.getKind()); // Still supported for backward compatibility
        assertEquals("Simple Test Workflow", definition.getMetadata().getName());
        assertEquals("1.0.0", definition.getMetadata().getVersion());
        assertEquals("A simple test workflow", definition.getMetadata().getDescription());
        assertEquals("test-workflow", definition.getMetadata().getType());
        assertEquals("Quorus Test Suite", definition.getMetadata().getAuthor());
        
        assertEquals(1, definition.getSpec().getTransferGroups().size());
        TransferGroup group = definition.getSpec().getTransferGroups().get(0);
        assertEquals("group1", group.getName());
        assertEquals("First transfer group", group.getDescription());
        assertEquals(1, group.getTransfers().size());
        
        TransferGroup.TransferDefinition transfer = group.getTransfers().get(0);
        assertEquals("transfer1", transfer.getName());
        assertEquals("http://example.com/file1.txt", transfer.getSource());
        assertEquals("/tmp/file1.txt", transfer.getDestination());
        assertEquals("http", transfer.getProtocol());
    }
    
    @Test
    void testParseWorkflowWithDependencies() throws WorkflowParseException {
        String yaml = """
                metadata:
                  name: "Dependency Test Workflow"
                  version: "1.0.0"
                  description: "Test workflow with dependencies"
                  type: "dependency-test-workflow"
                  author: "Quorus Test Suite"

                # ============================================================================

                spec:
                  execution:
                    parallelism: 2
                  transferGroups:
                    - name: group1
                      transfers:
                        - name: transfer1
                          source: http://example.com/file1.txt
                          destination: /tmp/file1.txt
                    - name: group2
                      dependsOn:
                        - group1
                      transfers:
                        - name: transfer2
                          source: http://example.com/file2.txt
                          destination: /tmp/file2.txt
                """;
        
        WorkflowDefinition definition = parser.parseFromString(yaml);
        
        assertEquals(2, definition.getSpec().getTransferGroups().size());
        
        TransferGroup group2 = definition.getSpec().getTransferGroups().get(1);
        assertEquals("group2", group2.getName());
        assertEquals(1, group2.getDependsOn().size());
        assertEquals("group1", group2.getDependsOn().get(0));
    }
    
    @Test
    void testParseWorkflowWithVariables() throws WorkflowParseException {
        String yaml = """
                metadata:
                  name: "Variable Test Workflow"
                  version: "1.0.0"
                  description: "Test workflow with variables"
                  type: "variable-test-workflow"
                  author: "Quorus Test Suite"

                # ============================================================================

                spec:
                  variables:
                    baseUrl: http://example.com
                    outputDir: /tmp/downloads
                  execution:
                    parallelism: 1
                  transferGroups:
                    - name: group1
                      variables:
                        fileName: file1.txt
                      transfers:
                        - name: transfer1
                          source: "{{baseUrl}}/{{fileName}}"
                          destination: "{{outputDir}}/{{fileName}}"
                """;
        
        WorkflowDefinition definition = parser.parseFromString(yaml);
        
        assertEquals(2, definition.getSpec().getVariables().size());
        assertEquals("http://example.com", definition.getSpec().getVariables().get("baseUrl"));
        assertEquals("/tmp/downloads", definition.getSpec().getVariables().get("outputDir"));
        
        TransferGroup group = definition.getSpec().getTransferGroups().get(0);
        assertEquals(1, group.getVariables().size());
        assertEquals("file1.txt", group.getVariables().get("fileName"));
    }
    
    @Test
    void testValidateWorkflow() throws WorkflowParseException {
        String yaml = """
                metadata:
                  name: "Validation Test Workflow"
                  version: "1.0.0"
                  description: "Test workflow for validation with comprehensive metadata"
                  type: "validation-test-workflow"
                  author: "test@quorus.dev"
                  created: "2025-08-21"
                  tags: ["test", "validation", "workflow"]

                # ============================================================================

                spec:
                  execution:
                    parallelism: 1
                  transferGroups:
                    - name: group1
                      transfers:
                        - name: transfer1
                          source: http://example.com/file1.txt
                          destination: /tmp/file1.txt
                """;
        
        WorkflowDefinition definition = parser.parseFromString(yaml);
        ValidationResult result = parser.validate(definition);
        
        assertTrue(result.isValid());
        assertEquals(0, result.getErrorCount());
    }
    
    @Test
    void testValidateInvalidWorkflow() {
        String yaml = """
                apiVersion: v1
                kind: TransferWorkflow
                metadata:
                  name: ""
                spec:
                  execution:
                    parallelism: 1
                  transferGroups: []
                """;
        
        assertThrows(WorkflowParseException.class, () -> {
            parser.parseFromString(yaml);
        });
    }
    
    @Test
    void testParseInvalidYaml() {
        String invalidYaml = """
                metadata:
                  name: "Invalid Test"
                  invalid: [unclosed
                """;
        
        assertThrows(WorkflowParseException.class, () -> {
            parser.parseFromString(invalidYaml);
        });
    }
    
    @Test
    void testBuildDependencyGraph() throws WorkflowParseException {
        String yaml = """
                metadata:
                  name: "Dependency Graph Test Workflow"
                  version: "1.0.0"
                  description: "Test workflow for dependency graph building with complete metadata"
                  type: "dependency-graph-test-workflow"
                  author: "test@quorus.dev"
                  created: "2025-08-21"
                  tags: ["test", "dependency", "graph"]

                # ============================================================================

                spec:
                  execution:
                    parallelism: 1
                  transferGroups:
                    - name: group1
                      transfers:
                        - name: transfer1
                          source: http://example.com/file1.txt
                          destination: /tmp/file1.txt
                    - name: group2
                      dependsOn:
                        - group1
                      transfers:
                        - name: transfer2
                          source: http://example.com/file2.txt
                          destination: /tmp/file2.txt
                    - name: group3
                      dependsOn:
                        - group1
                        - group2
                      transfers:
                        - name: transfer3
                          source: http://example.com/file3.txt
                          destination: /tmp/file3.txt
                """;
        
        WorkflowDefinition definition = parser.parseFromString(yaml);
        DependencyGraph graph = parser.buildDependencyGraph(java.util.List.of(definition));
        
        assertNotNull(graph);
        assertEquals(3, graph.getGroups().size());
        
        // Test topological sort
        var sortedGroups = graph.topologicalSort();
        assertEquals(3, sortedGroups.size());
        assertEquals("group1", sortedGroups.get(0).getName());
        assertEquals("group2", sortedGroups.get(1).getName());
        assertEquals("group3", sortedGroups.get(2).getName());
    }
    
    @Test
    void testSchemaValidation() {
        String validYaml = """
                metadata:
                  name: "Schema Validation Test"
                  version: "1.0.0"
                  description: "Test workflow for schema validation with complete metadata"
                  type: "schema-test-workflow"
                  author: "test@quorus.dev"
                  created: "2025-08-21"
                  tags: ["test", "schema", "validation"]

                # ============================================================================

                spec:
                  execution:
                    parallelism: 1
                  transferGroups: []
                """;
        
        ValidationResult result = parser.validateSchema(validYaml);
        assertTrue(result.isValid());
        
        String invalidYaml = """
                invalid: yaml: content
                """;
        
        result = parser.validateSchema(invalidYaml);
        assertFalse(result.isValid());
        assertTrue(result.getErrorCount() > 0);
    }
}
