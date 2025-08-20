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

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/**
 * YAML-based implementation of WorkflowDefinitionParser.
 * Parses YAML workflow definitions using SnakeYAML.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 1.0
 */
public class YamlWorkflowDefinitionParser implements WorkflowDefinitionParser {
    
    private final Yaml yaml;
    
    public YamlWorkflowDefinitionParser() {
        LoaderOptions loaderOptions = new LoaderOptions();
        this.yaml = new Yaml(new SafeConstructor(loaderOptions));
    }
    
    @Override
    public WorkflowDefinition parse(Path yamlFile) throws WorkflowParseException {
        try {
            String content = Files.readString(yamlFile);
            return parseFromString(content);
        } catch (IOException e) {
            throw new WorkflowParseException("Failed to read YAML file: " + yamlFile, e);
        }
    }
    
    @Override
    public WorkflowDefinition parseFromString(String yamlContent) throws WorkflowParseException {
        try {
            Map<String, Object> data = yaml.load(yamlContent);
            if (data == null) {
                throw new WorkflowParseException("Empty or invalid YAML content");
            }
            
            return parseWorkflowDefinition(data);
        } catch (YAMLException e) {
            throw new WorkflowParseException("YAML parsing failed", e);
        }
    }
    
    @Override
    public ValidationResult validate(WorkflowDefinition definition) {
        ValidationResult result = new ValidationResult();
        
        // Validate metadata
        validateMetadata(definition.getMetadata(), result);
        
        // Validate spec
        validateSpec(definition.getSpec(), result);
        
        return result;
    }
    
    @Override
    public DependencyGraph buildDependencyGraph(List<WorkflowDefinition> definitions) throws WorkflowParseException {
        DependencyGraph graph = new DependencyGraph();
        
        for (WorkflowDefinition definition : definitions) {
            for (TransferGroup group : definition.getSpec().getTransferGroups()) {
                graph.addGroup(group);
            }
        }
        
        ValidationResult validation = graph.validate();
        if (!validation.isValid()) {
            StringBuilder sb = new StringBuilder("Dependency graph validation failed:");
            for (ValidationResult.ValidationIssue error : validation.getErrors()) {
                sb.append("\n  - ").append(error.getMessage());
            }
            throw new WorkflowParseException(sb.toString());
        }
        
        return graph;
    }
    
    @Override
    public ValidationResult validateSchema(String yamlContent) {
        ValidationResult result = new ValidationResult();
        
        try {
            Map<String, Object> data = yaml.load(yamlContent);
            if (data == null) {
                result.addError("Empty or invalid YAML content");
                return result;
            }
            
            // Basic schema validation
            validateRequiredFields(data, result);
            
        } catch (YAMLException e) {
            result.addError("YAML syntax error: " + e.getMessage());
        }
        
        return result;
    }
    
    private WorkflowDefinition parseWorkflowDefinition(Map<String, Object> data) throws WorkflowParseException {
        // For backward compatibility, support both old and new formats
        String apiVersion = getStringValue(data, "apiVersion", "v1");
        String kind = getStringValue(data, "kind", "TransferWorkflow");

        Map<String, Object> metadataMap = getMapValue(data, "metadata");
        WorkflowDefinition.WorkflowMetadata metadata = parseMetadata(metadataMap);

        // Check if spec exists (new format) or if we need to use root level (legacy)
        Map<String, Object> specMap;
        if (data.containsKey("spec")) {
            specMap = getMapValue(data, "spec");
        } else {
            // Legacy format - spec fields are at root level
            specMap = data;
        }
        WorkflowDefinition.WorkflowSpec spec = parseSpec(specMap);

        return new WorkflowDefinition(apiVersion, kind, metadata, spec);
    }
    
    private WorkflowDefinition.WorkflowMetadata parseMetadata(Map<String, Object> data) throws WorkflowParseException {
        String name = getStringValue(data, "name");
        if (name == null || name.trim().isEmpty()) {
            throw new WorkflowParseException("metadata.name", "Workflow name is required");
        }

        String version = getStringValue(data, "version", "1.0.0");
        String description = getStringValue(data, "description");
        String type = getStringValue(data, "type", "workflow");
        String author = getStringValue(data, "author");
        Map<String, String> labels = parseLabels(getMapValue(data, "labels"));

        return new WorkflowDefinition.WorkflowMetadata(name, version, description, type, author, labels);
    }
    
    private WorkflowDefinition.WorkflowSpec parseSpec(Map<String, Object> data) throws WorkflowParseException {
        Map<String, Object> variables = getMapValue(data, "variables");
        
        Map<String, Object> executionMap = getMapValue(data, "execution");
        WorkflowDefinition.ExecutionConfig execution = parseExecutionConfig(executionMap);
        
        List<Map<String, Object>> transferGroupsList = getListValue(data, "transferGroups");
        List<TransferGroup> transferGroups = parseTransferGroups(transferGroupsList);
        
        return new WorkflowDefinition.WorkflowSpec(variables, execution, transferGroups);
    }
    
    private WorkflowDefinition.ExecutionConfig parseExecutionConfig(Map<String, Object> data) {
        if (data == null) {
            return new WorkflowDefinition.ExecutionConfig(false, false, 1, Duration.ofHours(1), "sequential");
        }
        
        boolean dryRun = getBooleanValue(data, "dryRun", false);
        boolean virtualRun = getBooleanValue(data, "virtualRun", false);
        int parallelism = getIntValue(data, "parallelism", 1);
        Duration timeout = parseDuration(getStringValue(data, "timeout", "3600s"));
        String strategy = getStringValue(data, "strategy", "sequential");
        
        return new WorkflowDefinition.ExecutionConfig(dryRun, virtualRun, parallelism, timeout, strategy);
    }
    
    private List<TransferGroup> parseTransferGroups(List<Map<String, Object>> groupsList) throws WorkflowParseException {
        if (groupsList == null) {
            return List.of();
        }
        
        List<TransferGroup> groups = new ArrayList<>();
        for (int i = 0; i < groupsList.size(); i++) {
            Map<String, Object> groupData = groupsList.get(i);
            try {
                groups.add(parseTransferGroup(groupData));
            } catch (WorkflowParseException e) {
                throw new WorkflowParseException("transferGroups[" + i + "]", e.getMessage(), e);
            }
        }
        
        return groups;
    }
    
    private TransferGroup parseTransferGroup(Map<String, Object> data) throws WorkflowParseException {
        String name = getStringValue(data, "name");
        if (name == null || name.trim().isEmpty()) {
            throw new WorkflowParseException("name", "Transfer group name is required");
        }
        
        String description = getStringValue(data, "description");
        List<String> dependsOn = parseStringList(getListValue(data, "dependsOn"));
        String condition = getStringValue(data, "condition");
        Map<String, Object> variables = getMapValue(data, "variables");
        boolean continueOnError = getBooleanValue(data, "continueOnError", false);
        int retryCount = getIntValue(data, "retryCount", 0);
        
        List<Map<String, Object>> transfersList = getListValue(data, "transfers");
        List<TransferGroup.TransferDefinition> transfers = parseTransferDefinitions(transfersList);
        
        return new TransferGroup(name, description, dependsOn, condition, variables, transfers, continueOnError, retryCount);
    }
    
    private List<TransferGroup.TransferDefinition> parseTransferDefinitions(List<Map<String, Object>> transfersList) throws WorkflowParseException {
        if (transfersList == null) {
            return List.of();
        }
        
        List<TransferGroup.TransferDefinition> transfers = new ArrayList<>();
        for (int i = 0; i < transfersList.size(); i++) {
            Map<String, Object> transferData = transfersList.get(i);
            try {
                transfers.add(parseTransferDefinition(transferData));
            } catch (WorkflowParseException e) {
                throw new WorkflowParseException("transfers[" + i + "]", e.getMessage(), e);
            }
        }
        
        return transfers;
    }
    
    private TransferGroup.TransferDefinition parseTransferDefinition(Map<String, Object> data) throws WorkflowParseException {
        String name = getStringValue(data, "name");
        if (name == null || name.trim().isEmpty()) {
            throw new WorkflowParseException("name", "Transfer name is required");
        }
        
        String source = getStringValue(data, "source");
        if (source == null || source.trim().isEmpty()) {
            throw new WorkflowParseException("source", "Transfer source is required");
        }
        
        String destination = getStringValue(data, "destination");
        if (destination == null || destination.trim().isEmpty()) {
            throw new WorkflowParseException("destination", "Transfer destination is required");
        }
        
        String protocol = getStringValue(data, "protocol", "http");
        Map<String, Object> options = getMapValue(data, "options");
        String condition = getStringValue(data, "condition");
        
        return new TransferGroup.TransferDefinition(name, source, destination, protocol, options, condition);
    }
    
    // Utility methods for safe type conversion
    @SuppressWarnings("unchecked")
    private String getStringValue(Map<String, Object> data, String key) {
        return getStringValue(data, key, null);
    }
    
    @SuppressWarnings("unchecked")
    private String getStringValue(Map<String, Object> data, String key, String defaultValue) {
        if (data == null) return defaultValue;
        Object value = data.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getMapValue(Map<String, Object> data, String key) {
        if (data == null) return null;
        Object value = data.get(key);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }
    
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getListValue(Map<String, Object> data, String key) {
        if (data == null) return null;
        Object value = data.get(key);
        return value instanceof List ? (List<Map<String, Object>>) value : null;
    }
    
    private boolean getBooleanValue(Map<String, Object> data, String key, boolean defaultValue) {
        if (data == null) return defaultValue;
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
    
    private int getIntValue(Map<String, Object> data, String key, int defaultValue) {
        if (data == null) return defaultValue;
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    private Map<String, String> parseLabels(Map<String, Object> data) {
        if (data == null) return Map.of();
        
        Map<String, String> labels = new HashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            labels.put(entry.getKey(), entry.getValue().toString());
        }
        return labels;
    }
    
    private List<String> parseStringList(List<Map<String, Object>> data) {
        if (data == null) return List.of();
        
        List<String> result = new ArrayList<>();
        for (Object item : data) {
            if (item != null) {
                result.add(item.toString());
            }
        }
        return result;
    }
    
    private Duration parseDuration(String durationStr) {
        if (durationStr == null || durationStr.trim().isEmpty()) {
            return Duration.ofHours(1);
        }
        
        try {
            // Simple duration parsing (e.g., "30s", "5m", "2h")
            String trimmed = durationStr.trim().toLowerCase();
            if (trimmed.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(trimmed.substring(0, trimmed.length() - 1)));
            } else if (trimmed.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(trimmed.substring(0, trimmed.length() - 1)));
            } else if (trimmed.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(trimmed.substring(0, trimmed.length() - 1)));
            } else {
                return Duration.ofSeconds(Long.parseLong(trimmed));
            }
        } catch (NumberFormatException e) {
            return Duration.ofHours(1);
        }
    }
    
    private void validateRequiredFields(Map<String, Object> data, ValidationResult result) {
        if (!data.containsKey("metadata")) {
            result.addError("metadata", "Required field 'metadata' is missing");
        }
        
        if (!data.containsKey("spec")) {
            result.addError("spec", "Required field 'spec' is missing");
        }
    }
    
    private void validateMetadata(WorkflowDefinition.WorkflowMetadata metadata, ValidationResult result) {
        if (metadata.getName() == null || metadata.getName().trim().isEmpty()) {
            result.addError("metadata.name", "Workflow name cannot be empty");
        }
    }
    
    private void validateSpec(WorkflowDefinition.WorkflowSpec spec, ValidationResult result) {
        if (spec.getTransferGroups().isEmpty()) {
            result.addWarning("spec.transferGroups", "No transfer groups defined");
        }
        
        // Validate transfer group names are unique
        Set<String> groupNames = new HashSet<>();
        for (TransferGroup group : spec.getTransferGroups()) {
            if (!groupNames.add(group.getName())) {
                result.addError("spec.transferGroups", "Duplicate transfer group name: " + group.getName());
            }
        }
    }
}
