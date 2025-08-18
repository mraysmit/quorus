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

import java.util.*;

/**
 * Represents a dependency graph for transfer groups within workflows.
 * Provides methods for topological sorting and cycle detection.
 */
public class DependencyGraph {
    
    private final Map<String, Set<String>> dependencies;
    private final Map<String, TransferGroup> groups;
    
    public DependencyGraph() {
        this.dependencies = new HashMap<>();
        this.groups = new HashMap<>();
    }
    
    /**
     * Adds a transfer group to the dependency graph.
     * 
     * @param group the transfer group to add
     */
    public void addGroup(TransferGroup group) {
        Objects.requireNonNull(group, "Transfer group cannot be null");
        
        String groupName = group.getName();
        groups.put(groupName, group);
        dependencies.put(groupName, new HashSet<>(group.getDependsOn()));
    }
    
    /**
     * Gets all transfer groups in the graph.
     * 
     * @return map of group names to transfer groups
     */
    public Map<String, TransferGroup> getGroups() {
        return Map.copyOf(groups);
    }
    
    /**
     * Gets the dependencies for a specific group.
     * 
     * @param groupName the name of the group
     * @return set of dependency group names
     */
    public Set<String> getDependencies(String groupName) {
        return dependencies.getOrDefault(groupName, Set.of());
    }
    
    /**
     * Performs topological sort to determine execution order.
     * 
     * @return list of transfer groups in execution order
     * @throws WorkflowParseException if circular dependencies are detected
     */
    public List<TransferGroup> topologicalSort() throws WorkflowParseException {
        // Kahn's algorithm for topological sorting
        Map<String, Integer> inDegree = calculateInDegree();
        Queue<String> queue = new LinkedList<>();
        List<TransferGroup> result = new ArrayList<>();
        
        // Find all nodes with no incoming edges
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }
        
        while (!queue.isEmpty()) {
            String current = queue.poll();
            TransferGroup group = groups.get(current);
            if (group != null) {
                result.add(group);
            }
            
            // Remove edges from current node
            for (String dependent : findDependents(current)) {
                inDegree.put(dependent, inDegree.get(dependent) - 1);
                if (inDegree.get(dependent) == 0) {
                    queue.offer(dependent);
                }
            }
        }
        
        // Check for circular dependencies
        if (result.size() != groups.size()) {
            List<String> remaining = new ArrayList<>();
            for (String groupName : groups.keySet()) {
                if (result.stream().noneMatch(g -> g.getName().equals(groupName))) {
                    remaining.add(groupName);
                }
            }
            throw new WorkflowParseException("Circular dependency detected among groups: " + remaining);
        }
        
        return result;
    }
    
    /**
     * Detects circular dependencies in the graph.
     * 
     * @return true if circular dependencies exist
     */
    public boolean hasCycles() {
        try {
            topologicalSort();
            return false;
        } catch (WorkflowParseException e) {
            return true;
        }
    }
    
    /**
     * Validates the dependency graph for consistency.
     * 
     * @return validation result
     */
    public ValidationResult validate() {
        ValidationResult result = new ValidationResult();
        
        // Check for missing dependencies
        for (Map.Entry<String, Set<String>> entry : dependencies.entrySet()) {
            String groupName = entry.getKey();
            for (String dependency : entry.getValue()) {
                if (!groups.containsKey(dependency)) {
                    result.addError("groups." + groupName + ".dependsOn", 
                                  "Dependency '" + dependency + "' not found");
                }
            }
        }
        
        // Check for self-dependencies
        for (Map.Entry<String, Set<String>> entry : dependencies.entrySet()) {
            String groupName = entry.getKey();
            if (entry.getValue().contains(groupName)) {
                result.addError("groups." + groupName + ".dependsOn", 
                              "Group cannot depend on itself");
            }
        }
        
        // Check for circular dependencies
        if (hasCycles()) {
            result.addError("Circular dependencies detected in transfer groups");
        }
        
        return result;
    }
    
    /**
     * Gets all groups that can be executed in parallel (no dependencies between them).
     * 
     * @return list of parallel execution batches
     */
    public List<List<TransferGroup>> getParallelExecutionBatches() throws WorkflowParseException {
        List<List<TransferGroup>> batches = new ArrayList<>();
        Map<String, Integer> inDegree = calculateInDegree();
        Set<String> processed = new HashSet<>();
        
        while (processed.size() < groups.size()) {
            List<TransferGroup> currentBatch = new ArrayList<>();
            
            // Find all groups with no remaining dependencies
            for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
                String groupName = entry.getKey();
                if (entry.getValue() == 0 && !processed.contains(groupName)) {
                    currentBatch.add(groups.get(groupName));
                    processed.add(groupName);
                }
            }
            
            if (currentBatch.isEmpty()) {
                throw new WorkflowParseException("Circular dependency detected - cannot create execution batches");
            }
            
            batches.add(currentBatch);
            
            // Update in-degree for next iteration
            for (TransferGroup group : currentBatch) {
                for (String dependent : findDependents(group.getName())) {
                    inDegree.put(dependent, inDegree.get(dependent) - 1);
                }
            }
        }
        
        return batches;
    }
    
    private Map<String, Integer> calculateInDegree() {
        Map<String, Integer> inDegree = new HashMap<>();

        // Initialize all nodes with 0 in-degree
        for (String groupName : groups.keySet()) {
            inDegree.put(groupName, 0);
        }

        // Calculate in-degree for each node
        for (Map.Entry<String, Set<String>> entry : dependencies.entrySet()) {
            String dependent = entry.getKey();
            for (String dependency : entry.getValue()) {
                if (groups.containsKey(dependency)) {
                    inDegree.put(dependent, inDegree.get(dependent) + 1);
                }
            }
        }

        return inDegree;
    }
    
    private Set<String> findDependents(String groupName) {
        Set<String> dependents = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : dependencies.entrySet()) {
            if (entry.getValue().contains(groupName)) {
                dependents.add(entry.getKey());
            }
        }
        return dependents;
    }
    
    @Override
    public String toString() {
        return "DependencyGraph{" +
               "groups=" + groups.keySet() +
               ", dependencies=" + dependencies +
               '}';
    }
}
