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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
/**
 * Description for WorkflowDefinitionTest
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */

class WorkflowDefinitionTest {
    
    @Test
    void testBasicWorkflowDefinition() {
        WorkflowDefinition.WorkflowMetadata metadata = new WorkflowDefinition.WorkflowMetadata(
                "test-workflow", "Test workflow description", Map.of("env", "test"));
        
        WorkflowDefinition.ExecutionConfig execution = new WorkflowDefinition.ExecutionConfig(
                false, false, 2, Duration.ofHours(1), "sequential");
        
        WorkflowDefinition.WorkflowSpec spec = new WorkflowDefinition.WorkflowSpec(
                Map.of("var1", "value1"), execution, List.of());
        
        WorkflowDefinition definition = new WorkflowDefinition("v1", "TransferWorkflow", metadata, spec);
        
        assertEquals("v1", definition.getApiVersion());
        assertEquals("TransferWorkflow", definition.getKind());
        assertEquals(metadata, definition.getMetadata());
        assertEquals(spec, definition.getSpec());
    }
    
    @Test
    void testWorkflowDefinitionRequiredFields() {
        WorkflowDefinition.WorkflowMetadata metadata = new WorkflowDefinition.WorkflowMetadata(
                "test", "desc", Map.of());
        WorkflowDefinition.ExecutionConfig execution = new WorkflowDefinition.ExecutionConfig(
                false, false, 1, Duration.ofMinutes(30), "sequential");
        WorkflowDefinition.WorkflowSpec spec = new WorkflowDefinition.WorkflowSpec(
                Map.of(), execution, List.of());
        
        assertThrows(NullPointerException.class, () -> {
            new WorkflowDefinition(null, "TransferWorkflow", metadata, spec);
        });
        
        assertThrows(NullPointerException.class, () -> {
            new WorkflowDefinition("v1", null, metadata, spec);
        });
        
        assertThrows(NullPointerException.class, () -> {
            new WorkflowDefinition("v1", "TransferWorkflow", null, spec);
        });
        
        assertThrows(NullPointerException.class, () -> {
            new WorkflowDefinition("v1", "TransferWorkflow", metadata, null);
        });
    }
    
    @Test
    void testWorkflowMetadata() {
        String name = "backup-workflow";
        String description = "Daily backup workflow";
        Map<String, String> labels = Map.of("schedule", "daily", "priority", "high");
        
        WorkflowDefinition.WorkflowMetadata metadata = new WorkflowDefinition.WorkflowMetadata(
                name, description, labels);
        
        assertEquals(name, metadata.getName());
        assertEquals(description, metadata.getDescription());
        assertEquals(labels, metadata.getLabels());
    }
    
    @Test
    void testWorkflowMetadataWithNulls() {
        WorkflowDefinition.WorkflowMetadata metadata = new WorkflowDefinition.WorkflowMetadata(
                "test", null, null);
        
        assertEquals("test", metadata.getName());
        assertNull(metadata.getDescription());
        assertTrue(metadata.getLabels().isEmpty());
    }
    
    @Test
    void testWorkflowMetadataRequiredFields() {
        assertThrows(NullPointerException.class, () -> {
            new WorkflowDefinition.WorkflowMetadata(null, "desc", Map.of());
        });
    }
    
    @Test
    void testWorkflowMetadataImmutability() {
        Map<String, String> labels = Map.of("key", "value");
        WorkflowDefinition.WorkflowMetadata metadata = new WorkflowDefinition.WorkflowMetadata(
                "test", "desc", labels);
        
        assertThrows(UnsupportedOperationException.class, () -> {
            metadata.getLabels().put("new", "label");
        });
    }
    
    @Test
    void testExecutionConfig() {
        boolean dryRun = true;
        boolean virtualRun = false;
        int parallelism = 5;
        Duration timeout = Duration.ofMinutes(45);
        String strategy = "parallel";
        
        WorkflowDefinition.ExecutionConfig config = new WorkflowDefinition.ExecutionConfig(
                dryRun, virtualRun, parallelism, timeout, strategy);
        
        assertEquals(dryRun, config.isDryRun());
        assertEquals(virtualRun, config.isVirtualRun());
        assertEquals(parallelism, config.getParallelism());
        assertEquals(timeout, config.getTimeout());
        assertEquals(strategy, config.getStrategy());
    }
    
    @Test
    void testExecutionConfigDefaults() {
        WorkflowDefinition.ExecutionConfig config = new WorkflowDefinition.ExecutionConfig(
                false, false, 1, null, null);
        
        assertFalse(config.isDryRun());
        assertFalse(config.isVirtualRun());
        assertEquals(1, config.getParallelism());
        assertNull(config.getTimeout());
        assertEquals("sequential", config.getStrategy()); // Default strategy
    }
    
    @Test
    void testExecutionConfigParallelismValidation() {
        // Negative values should be normalized to 1
        WorkflowDefinition.ExecutionConfig config1 = new WorkflowDefinition.ExecutionConfig(
                false, false, -5, Duration.ofMinutes(30), "sequential");
        assertEquals(1, config1.getParallelism());
        
        // Zero should be normalized to 1
        WorkflowDefinition.ExecutionConfig config2 = new WorkflowDefinition.ExecutionConfig(
                false, false, 0, Duration.ofMinutes(30), "sequential");
        assertEquals(1, config2.getParallelism());
        
        // Positive values should be preserved
        WorkflowDefinition.ExecutionConfig config3 = new WorkflowDefinition.ExecutionConfig(
                false, false, 10, Duration.ofMinutes(30), "sequential");
        assertEquals(10, config3.getParallelism());
    }
    
    @Test
    void testWorkflowSpec() {
        Map<String, Object> variables = Map.of("env", "production", "retries", 3);
        WorkflowDefinition.ExecutionConfig execution = new WorkflowDefinition.ExecutionConfig(
                false, false, 2, Duration.ofHours(2), "mixed");
        
        TransferGroup group = new TransferGroup("test-group", "Test group", 
                List.of(), null, Map.of(), List.of(), false, 0);
        List<TransferGroup> transferGroups = List.of(group);
        
        WorkflowDefinition.WorkflowSpec spec = new WorkflowDefinition.WorkflowSpec(
                variables, execution, transferGroups);
        
        assertEquals(variables, spec.getVariables());
        assertEquals(execution, spec.getExecution());
        assertEquals(transferGroups, spec.getTransferGroups());
    }
    
    @Test
    void testWorkflowSpecWithNulls() {
        WorkflowDefinition.ExecutionConfig execution = new WorkflowDefinition.ExecutionConfig(
                false, false, 1, Duration.ofMinutes(30), "sequential");
        
        WorkflowDefinition.WorkflowSpec spec = new WorkflowDefinition.WorkflowSpec(
                null, execution, null);
        
        assertTrue(spec.getVariables().isEmpty());
        assertEquals(execution, spec.getExecution());
        assertTrue(spec.getTransferGroups().isEmpty());
    }
    
    @Test
    void testWorkflowSpecRequiredFields() {
        assertThrows(NullPointerException.class, () -> {
            new WorkflowDefinition.WorkflowSpec(Map.of(), null, List.of());
        });
    }
    
    @Test
    void testWorkflowSpecImmutability() {
        Map<String, Object> variables = Map.of("key", "value");
        WorkflowDefinition.ExecutionConfig execution = new WorkflowDefinition.ExecutionConfig(
                false, false, 1, Duration.ofMinutes(30), "sequential");
        List<TransferGroup> groups = List.of();
        
        WorkflowDefinition.WorkflowSpec spec = new WorkflowDefinition.WorkflowSpec(
                variables, execution, groups);
        
        assertThrows(UnsupportedOperationException.class, () -> {
            spec.getVariables().put("new", "variable");
        });
        
        assertThrows(UnsupportedOperationException.class, () -> {
            spec.getTransferGroups().add(new TransferGroup("new", null, null, null, null, null, false, 0));
        });
    }
    
    @Test
    void testCompleteWorkflowDefinition() {
        // Create a complete workflow definition with all components
        Map<String, String> labels = Map.of("team", "data", "environment", "production");
        WorkflowDefinition.WorkflowMetadata metadata = new WorkflowDefinition.WorkflowMetadata(
                "data-sync-workflow", "Production data synchronization workflow", labels);
        
        WorkflowDefinition.ExecutionConfig execution = new WorkflowDefinition.ExecutionConfig(
                false, true, 3, Duration.ofHours(4), "parallel");
        
        TransferGroup.TransferDefinition transfer1 = new TransferGroup.TransferDefinition(
                "sync-users", "{{source}}/users.csv", "{{dest}}/users.csv", "sftp", 
                Map.of("compression", true), null);
        
        TransferGroup.TransferDefinition transfer2 = new TransferGroup.TransferDefinition(
                "sync-orders", "{{source}}/orders.csv", "{{dest}}/orders.csv", "sftp", 
                Map.of("compression", true), null);
        
        TransferGroup group1 = new TransferGroup("data-sync", "Synchronize data files", 
                List.of(), "env == 'production'", Map.of("batch_size", 1000), 
                List.of(transfer1, transfer2), true, 2);
        
        TransferGroup group2 = new TransferGroup("cleanup", "Cleanup temporary files", 
                List.of("data-sync"), null, Map.of(), List.of(), false, 0);
        
        Map<String, Object> variables = Map.of(
                "source", "sftp://source.example.com/data",
                "dest", "/var/data/sync",
                "env", "production"
        );
        
        WorkflowDefinition.WorkflowSpec spec = new WorkflowDefinition.WorkflowSpec(
                variables, execution, List.of(group1, group2));
        
        WorkflowDefinition definition = new WorkflowDefinition("v1", "TransferWorkflow", metadata, spec);
        
        // Verify all components
        assertEquals("v1", definition.getApiVersion());
        assertEquals("TransferWorkflow", definition.getKind());
        assertEquals("data-sync-workflow", definition.getMetadata().getName());
        assertEquals("data", definition.getMetadata().getLabels().get("team"));
        assertTrue(definition.getSpec().getExecution().isVirtualRun());
        assertEquals(3, definition.getSpec().getExecution().getParallelism());
        assertEquals(2, definition.getSpec().getTransferGroups().size());
        assertEquals("data-sync", definition.getSpec().getTransferGroups().get(0).getName());
        assertEquals("cleanup", definition.getSpec().getTransferGroups().get(1).getName());
        assertEquals("production", definition.getSpec().getVariables().get("env"));
    }
    
    @Test
    void testToString() {
        WorkflowDefinition.WorkflowMetadata metadata = new WorkflowDefinition.WorkflowMetadata(
                "test-workflow", "Test", Map.of());
        WorkflowDefinition.ExecutionConfig execution = new WorkflowDefinition.ExecutionConfig(
                false, false, 1, Duration.ofMinutes(30), "sequential");
        WorkflowDefinition.WorkflowSpec spec = new WorkflowDefinition.WorkflowSpec(
                Map.of(), execution, List.of());
        
        WorkflowDefinition definition = new WorkflowDefinition("v1", "TransferWorkflow", metadata, spec);
        
        String toString = definition.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("v1"));
        assertTrue(toString.contains("TransferWorkflow"));
        assertTrue(toString.contains("test-workflow"));
    }
    
    @Test
    void testEquals() {
        WorkflowDefinition.WorkflowMetadata metadata1 = new WorkflowDefinition.WorkflowMetadata(
                "same-name", "desc", Map.of());
        WorkflowDefinition.ExecutionConfig execution1 = new WorkflowDefinition.ExecutionConfig(
                false, false, 1, Duration.ofMinutes(30), "sequential");
        WorkflowDefinition.WorkflowSpec spec1 = new WorkflowDefinition.WorkflowSpec(
                Map.of(), execution1, List.of());
        WorkflowDefinition definition1 = new WorkflowDefinition("v1", "TransferWorkflow", metadata1, spec1);
        
        WorkflowDefinition.WorkflowMetadata metadata2 = new WorkflowDefinition.WorkflowMetadata(
                "same-name", "desc", Map.of());
        WorkflowDefinition.ExecutionConfig execution2 = new WorkflowDefinition.ExecutionConfig(
                false, false, 1, Duration.ofMinutes(30), "sequential");
        WorkflowDefinition.WorkflowSpec spec2 = new WorkflowDefinition.WorkflowSpec(
                Map.of(), execution2, List.of());
        WorkflowDefinition definition2 = new WorkflowDefinition("v1", "TransferWorkflow", metadata2, spec2);
        
        WorkflowDefinition.WorkflowMetadata metadata3 = new WorkflowDefinition.WorkflowMetadata(
                "different-name", "desc", Map.of());
        WorkflowDefinition.WorkflowSpec spec3 = new WorkflowDefinition.WorkflowSpec(
                Map.of(), execution1, List.of());
        WorkflowDefinition definition3 = new WorkflowDefinition("v1", "TransferWorkflow", metadata3, spec3);
        
        assertEquals(definition1, definition2);
        assertNotEquals(definition1, definition3);
        assertEquals(definition1.hashCode(), definition2.hashCode());
    }
}
