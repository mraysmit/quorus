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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * Description for VariableResolver
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-18
 */

public class VariableResolver {
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    
    private final Map<String, Object> globalVariables;
    private final Map<String, Object> contextVariables;
    
    public VariableResolver() {
        this.globalVariables = new HashMap<>();
        this.contextVariables = new HashMap<>();
    }
    
    public VariableResolver(Map<String, Object> globalVariables) {
        this.globalVariables = new HashMap<>(globalVariables != null ? globalVariables : Map.of());
        this.contextVariables = new HashMap<>();
    }
    
    public VariableResolver withContext(Map<String, Object> contextVariables) {
        VariableResolver resolver = new VariableResolver(this.globalVariables);
        resolver.contextVariables.putAll(this.contextVariables);
        if (contextVariables != null) {
            resolver.contextVariables.putAll(contextVariables);
        }
        return resolver;
    }
    
    public String resolve(String template) throws VariableResolutionException {
        if (template == null) {
            return null;
        }
        
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String variableName = matcher.group(1).trim();
            Object value = resolveVariable(variableName);
            
            if (value == null) {
                throw new VariableResolutionException("Variable not found: " + variableName);
            }
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(value.toString()));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    public TransferGroup resolve(TransferGroup group) throws VariableResolutionException {
        Objects.requireNonNull(group, "Transfer group cannot be null");
        
        // Create resolver with group-specific variables
        VariableResolver groupResolver = this.withContext(group.getVariables());
        
        // Resolve transfers
        var resolvedTransfers = group.getTransfers().stream()
                .map(transfer -> groupResolver.resolve(transfer))
                .toList();
        
        // Resolve condition if present
        String resolvedCondition = group.getCondition() != null ? 
                groupResolver.resolve(group.getCondition()) : null;
        
        return new TransferGroup(
                group.getName(),
                group.getDescription(),
                group.getDependsOn(),
                resolvedCondition,
                group.getVariables(), // Keep original variables
                resolvedTransfers,
                group.isContinueOnError(),
                group.getRetryCount()
        );
    }
    
    public TransferGroup.TransferDefinition resolve(TransferGroup.TransferDefinition transfer) throws VariableResolutionException {
        Objects.requireNonNull(transfer, "Transfer definition cannot be null");
        
        String resolvedSource = resolve(transfer.getSource());
        String resolvedDestination = resolve(transfer.getDestination());
        String resolvedCondition = transfer.getCondition() != null ? 
                resolve(transfer.getCondition()) : null;
        
        // Resolve options map
        Map<String, Object> resolvedOptions = new HashMap<>();
        for (Map.Entry<String, Object> entry : transfer.getOptions().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                resolvedOptions.put(entry.getKey(), resolve((String) value));
            } else {
                resolvedOptions.put(entry.getKey(), value);
            }
        }
        
        return new TransferGroup.TransferDefinition(
                transfer.getName(),
                resolvedSource,
                resolvedDestination,
                transfer.getProtocol(),
                resolvedOptions,
                resolvedCondition
        );
    }
    
    public WorkflowDefinition resolve(WorkflowDefinition workflow) throws VariableResolutionException {
        Objects.requireNonNull(workflow, "Workflow definition cannot be null");
        
        // Create resolver with workflow variables
        VariableResolver workflowResolver = this.withContext(workflow.getSpec().getVariables());
        
        // Resolve transfer groups
        var resolvedGroups = workflow.getSpec().getTransferGroups().stream()
                .map(group -> workflowResolver.resolve(group))
                .toList();
        
        // Create new spec with resolved groups
        var newSpec = new WorkflowDefinition.WorkflowSpec(
                workflow.getSpec().getVariables(), // Keep original variables
                workflow.getSpec().getExecution(),
                resolvedGroups
        );
        
        return new WorkflowDefinition(
                workflow.getApiVersion(),
                workflow.getMetadata(),
                newSpec
        );
    }
    
    public boolean hasVariables(String template) {
        if (template == null) {
            return false;
        }
        return VARIABLE_PATTERN.matcher(template).find();
    }
    
    public java.util.Set<String> getVariableNames(String template) {
        if (template == null) {
            return java.util.Set.of();
        }
        
        java.util.Set<String> variables = new java.util.HashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        
        while (matcher.find()) {
            variables.add(matcher.group(1).trim());
        }
        
        return variables;
    }
    
    public void setGlobalVariable(String name, Object value) {
        globalVariables.put(name, value);
    }
    
    public void setContextVariable(String name, Object value) {
        contextVariables.put(name, value);
    }
    
    public Map<String, Object> getAllVariables() {
        Map<String, Object> allVariables = new HashMap<>(globalVariables);
        allVariables.putAll(contextVariables);
        return Map.copyOf(allVariables);
    }
    
    private Object resolveVariable(String variableName) {
        // Check context variables first (higher precedence)
        if (contextVariables.containsKey(variableName)) {
            return contextVariables.get(variableName);
        }
        
        // Check global variables
        if (globalVariables.containsKey(variableName)) {
            return globalVariables.get(variableName);
        }
        
        // Check environment variables as fallback
        String envValue = System.getenv(variableName);
        if (envValue != null) {
            return envValue;
        }
        
        // Check system properties as final fallback
        return System.getProperty(variableName);
    }
    
    /**
     * Exception thrown when variable resolution fails.
     */
    public static class VariableResolutionException extends RuntimeException {
        public VariableResolutionException(String message) {
            super(message);
        }
        
        public VariableResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
