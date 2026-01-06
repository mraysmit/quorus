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

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
/**
 * Description for DependencyGraphTest
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-18
 */

class DependencyGraphTest {
    
    private DependencyGraph graph;
    
    @BeforeEach
    void setUp() {
        graph = new DependencyGraph();
    }
    
    @Test
    void testEmptyGraph() throws WorkflowParseException {
        assertTrue(graph.getGroups().isEmpty());
        assertTrue(graph.topologicalSort().isEmpty());
        assertFalse(graph.hasCycles());
        
        ValidationResult result = graph.validate();
        assertTrue(result.isValid());
    }
    
    @Test
    void testSingleGroup() throws WorkflowParseException {
        TransferGroup group = createGroup("group1", List.of());
        graph.addGroup(group);
        
        assertEquals(1, graph.getGroups().size());
        assertTrue(graph.getGroups().containsKey("group1"));
        
        List<TransferGroup> sorted = graph.topologicalSort();
        assertEquals(1, sorted.size());
        assertEquals("group1", sorted.get(0).getName());
        
        assertFalse(graph.hasCycles());
    }
    
    @Test
    void testLinearDependency() throws WorkflowParseException {
        TransferGroup group1 = createGroup("group1", List.of());
        TransferGroup group2 = createGroup("group2", List.of("group1"));
        TransferGroup group3 = createGroup("group3", List.of("group2"));
        
        graph.addGroup(group1);
        graph.addGroup(group2);
        graph.addGroup(group3);
        
        List<TransferGroup> sorted = graph.topologicalSort();
        assertEquals(3, sorted.size());
        assertEquals("group1", sorted.get(0).getName());
        assertEquals("group2", sorted.get(1).getName());
        assertEquals("group3", sorted.get(2).getName());
        
        assertFalse(graph.hasCycles());
    }
    
    @Test
    void testParallelGroups() throws WorkflowParseException {
        TransferGroup group1 = createGroup("group1", List.of());
        TransferGroup group2 = createGroup("group2", List.of());
        TransferGroup group3 = createGroup("group3", List.of("group1", "group2"));
        
        graph.addGroup(group1);
        graph.addGroup(group2);
        graph.addGroup(group3);
        
        List<TransferGroup> sorted = graph.topologicalSort();
        assertEquals(3, sorted.size());
        assertEquals("group3", sorted.get(2).getName()); // group3 should be last
        
        // group1 and group2 can be in any order
        Set<String> firstTwo = Set.of(sorted.get(0).getName(), sorted.get(1).getName());
        assertTrue(firstTwo.contains("group1"));
        assertTrue(firstTwo.contains("group2"));
        
        assertFalse(graph.hasCycles());
    }
    
    @Test
    void testComplexDependencyGraph() throws WorkflowParseException {
        // Create a complex dependency graph:
        // group1 -> group3 -> group5
        // group2 -> group3 -> group5
        // group4 -> group5
        TransferGroup group1 = createGroup("group1", List.of());
        TransferGroup group2 = createGroup("group2", List.of());
        TransferGroup group3 = createGroup("group3", List.of("group1", "group2"));
        TransferGroup group4 = createGroup("group4", List.of());
        TransferGroup group5 = createGroup("group5", List.of("group3", "group4"));
        
        graph.addGroup(group1);
        graph.addGroup(group2);
        graph.addGroup(group3);
        graph.addGroup(group4);
        graph.addGroup(group5);
        
        List<TransferGroup> sorted = graph.topologicalSort();
        assertEquals(5, sorted.size());
        
        // Verify ordering constraints
        int pos1 = findPosition(sorted, "group1");
        int pos2 = findPosition(sorted, "group2");
        int pos3 = findPosition(sorted, "group3");
        int pos4 = findPosition(sorted, "group4");
        int pos5 = findPosition(sorted, "group5");
        
        assertTrue(pos1 < pos3);
        assertTrue(pos2 < pos3);
        assertTrue(pos3 < pos5);
        assertTrue(pos4 < pos5);
        
        assertFalse(graph.hasCycles());
    }
    
    @Test
    void testCircularDependency() {
        TransferGroup group1 = createGroup("group1", List.of("group2"));
        TransferGroup group2 = createGroup("group2", List.of("group1"));
        
        graph.addGroup(group1);
        graph.addGroup(group2);
        
        assertTrue(graph.hasCycles());
        
        assertThrows(WorkflowParseException.class, () -> {
            graph.topologicalSort();
        });
    }
    
    @Test
    void testSelfDependency() {
        TransferGroup group1 = createGroup("group1", List.of("group1"));
        graph.addGroup(group1);
        
        ValidationResult result = graph.validate();
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(error -> error.getMessage().contains("cannot depend on itself")));
    }
    
    @Test
    void testMissingDependency() {
        TransferGroup group1 = createGroup("group1", List.of("nonexistent"));
        graph.addGroup(group1);
        
        ValidationResult result = graph.validate();
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(error -> error.getMessage().contains("not found")));
    }
    
    @Test
    void testGetDependencies() {
        TransferGroup group1 = createGroup("group1", List.of());
        TransferGroup group2 = createGroup("group2", List.of("group1"));
        
        graph.addGroup(group1);
        graph.addGroup(group2);
        
        Set<String> deps1 = graph.getDependencies("group1");
        assertTrue(deps1.isEmpty());
        
        Set<String> deps2 = graph.getDependencies("group2");
        assertEquals(1, deps2.size());
        assertTrue(deps2.contains("group1"));
        
        Set<String> depsNonexistent = graph.getDependencies("nonexistent");
        assertTrue(depsNonexistent.isEmpty());
    }
    
    @Test
    void testParallelExecutionBatches() throws WorkflowParseException {
        // Create a graph with clear parallel execution opportunities
        TransferGroup group1 = createGroup("group1", List.of());
        TransferGroup group2 = createGroup("group2", List.of());
        TransferGroup group3 = createGroup("group3", List.of("group1"));
        TransferGroup group4 = createGroup("group4", List.of("group2"));
        TransferGroup group5 = createGroup("group5", List.of("group3", "group4"));
        
        graph.addGroup(group1);
        graph.addGroup(group2);
        graph.addGroup(group3);
        graph.addGroup(group4);
        graph.addGroup(group5);
        
        List<List<TransferGroup>> batches = graph.getParallelExecutionBatches();
        
        assertEquals(3, batches.size());
        
        // First batch: group1 and group2 (no dependencies)
        assertEquals(2, batches.get(0).size());
        Set<String> batch1Names = Set.of(
                batches.get(0).get(0).getName(),
                batches.get(0).get(1).getName()
        );
        assertTrue(batch1Names.contains("group1"));
        assertTrue(batch1Names.contains("group2"));
        
        // Second batch: group3 and group4 (depend on first batch)
        assertEquals(2, batches.get(1).size());
        Set<String> batch2Names = Set.of(
                batches.get(1).get(0).getName(),
                batches.get(1).get(1).getName()
        );
        assertTrue(batch2Names.contains("group3"));
        assertTrue(batch2Names.contains("group4"));
        
        // Third batch: group5 (depends on second batch)
        assertEquals(1, batches.get(2).size());
        assertEquals("group5", batches.get(2).get(0).getName());
    }
    
    @Test
    void testParallelExecutionBatchesWithCycle() {
        TransferGroup group1 = createGroup("group1", List.of("group2"));
        TransferGroup group2 = createGroup("group2", List.of("group1"));
        
        graph.addGroup(group1);
        graph.addGroup(group2);
        
        assertThrows(WorkflowParseException.class, () -> {
            graph.getParallelExecutionBatches();
        });
    }
    
    @Test
    void testValidationWithValidGraph() {
        TransferGroup group1 = createGroup("group1", List.of());
        TransferGroup group2 = createGroup("group2", List.of("group1"));
        
        graph.addGroup(group1);
        graph.addGroup(group2);
        
        ValidationResult result = graph.validate();
        assertTrue(result.isValid());
        assertEquals(0, result.getErrorCount());
        assertEquals(0, result.getWarningCount());
    }
    
    @Test
    void testToString() {
        TransferGroup group1 = createGroup("group1", List.of());
        graph.addGroup(group1);
        
        String toString = graph.toString();
        assertTrue(toString.contains("DependencyGraph"));
        assertTrue(toString.contains("group1"));
    }
    
    private TransferGroup createGroup(String name, List<String> dependsOn) {
        return new TransferGroup(
                name,
                "Description for " + name,
                dependsOn,
                null,
                Map.of(),
                List.of(),
                false,
                0
        );
    }
    
    private int findPosition(List<TransferGroup> groups, String name) {
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).getName().equals(name)) {
                return i;
            }
        }
        return -1;
    }
}
