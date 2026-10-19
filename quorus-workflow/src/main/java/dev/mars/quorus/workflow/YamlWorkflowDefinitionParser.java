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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>Performance Optimization: Uses static singleton Yaml parser to eliminate
 * per-request instantiation overhead. SnakeYAML Yaml is thread-safe and can be
 * safely shared across all parser instances.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-18
 * @version 1.0
 */
public class YamlWorkflowDefinitionParser implements WorkflowDefinitionParser {

    private static final Logger logger = LoggerFactory.getLogger(YamlWorkflowDefinitionParser.class);

    /**
     * Static singleton Yaml parser - thread-safe and reused across all instances.
     * Eliminates expensive per-request instantiation (classpath scanning, reflection).
     * Based on APEX critical performance refactoring (Dec 2025) which achieved
     * 70-80% reduction in parsing overhead.
     */
    private static final Yaml YAML_PARSER = createYamlParser();

    /**
     * Static singleton schema validator - eliminates per-request instantiation.
     */
    private static final WorkflowSchemaValidator SCHEMA_VALIDATOR = new WorkflowSchemaValidator();

    /**
     * Creates the singleton Yaml parser with safe configuration.
     * Called once during class loading.
     */
    private static Yaml createYamlParser() {
        LoaderOptions loaderOptions = new LoaderOptions();
        return new Yaml(new SafeConstructor(loaderOptions));
    }

    /**
     * Default constructor - no per-instance initialization needed.
     * All parsing resources are static singletons for optimal performance.
     */
    public YamlWorkflowDefinitionParser() {
        // No initialization needed - using static singletons
    }
    
    @Override
    public WorkflowDefinition parse(Path yamlFile) throws WorkflowParseException {
        logger.info("Parsing workflow definition from file: {}", yamlFile);
        try {
            String content = Files.readString(yamlFile);
            WorkflowDefinition definition = parseFromString(content);
            logger.info("Successfully parsed workflow '{}' v{} from {}",
                    definition.getMetadata().getName(), definition.getMetadata().getVersion(), yamlFile);
            return definition;
        } catch (IOException e) {
            logger.warn("Failed to read YAML file: {}: {}", yamlFile, e.getMessage());
            logger.debug("Stack trace for YAML file read failure: {}", yamlFile, e);
            throw new WorkflowParseException("Failed to read YAML file: " + yamlFile, e);
        }
    }
    
    @Override
    public WorkflowDefinition parseFromString(String yamlContent) throws WorkflowParseException {
        try {
            Map<String, Object> data = YAML_PARSER.load(yamlContent);
            if (data == null) {
                logger.warn("Empty or invalid YAML content received");
                throw new WorkflowParseException("Empty or invalid YAML content");
            }

            return parseWorkflowDefinition(data);
        } catch (YAMLException e) {
            logger.warn("YAML syntax error: {}", e.getMessage());
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
            Map<String, Object> data = YAML_PARSER.load(yamlContent);
            if (data == null) {
                result.addError("Empty or invalid YAML content");
                return result;
            }

            // Use static singleton schema validator
            ValidationResult schemaResult = SCHEMA_VALIDATOR.validateWorkflowSchema(data);

            // Merge results
            schemaResult.getErrors().forEach(error ->
                result.addError(error.getFieldPath(), error.getMessage()));
            schemaResult.getWarnings().forEach(warning ->
                result.addWarning(warning.getFieldPath(), warning.getMessage()));

            if (!result.isValid()) {
                logger.warn("Schema validation failed with {} errors and {} warnings",
                        result.getErrors().size(), result.getWarnings().size());
            }

        } catch (YAMLException e) {
            logger.warn("YAML syntax error during schema validation: {}", e.getMessage());
            result.addError("YAML syntax error: " + e.getMessage());
        }

        return result;
    }
    
    private WorkflowDefinition parseWorkflowDefinition(Map<String, Object> data) throws WorkflowParseException {
        String apiVersion = getStringValue(data, "apiVersion", "v1");

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

        return new WorkflowDefinition(apiVersion, metadata, spec);
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
        String created = getStringValue(data, "created");
        List<String> tags = parseTagsList(getObjectListValue(data, "tags"));
        Map<String, String> labels = parseLabels(getMapValue(data, "labels"));

        return new WorkflowDefinition.WorkflowMetadata(name, version, description, type, author, created, tags, labels);
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
        List<String> dependsOn = parseStringList(getObjectListValue(data, "dependsOn"));
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
    private String getStringValue(Map<String, Object> data, String key) {
        return getStringValue(data, key, null);
    }
    
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
    
    private List<String> parseStringList(List<Object> data) {
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

        // Use static singleton schema validator for detailed validation
        Map<String, Object> metadataMap = Map.of(
            "name", metadata.getName() != null ? metadata.getName() : "",
            "version", metadata.getVersion() != null ? metadata.getVersion() : "",
            "description", metadata.getDescription() != null ? metadata.getDescription() : "",
            "type", metadata.getType() != null ? metadata.getType() : "",
            "author", metadata.getAuthor() != null ? metadata.getAuthor() : "",
            "created", metadata.getCreated() != null ? metadata.getCreated() : "",
            "tags", metadata.getTags() != null ? metadata.getTags() : List.of(),
            "labels", metadata.getLabels() != null ? metadata.getLabels() : Map.of()
        );

        ValidationResult metadataResult = SCHEMA_VALIDATOR.validateMetadataSchema(metadataMap);
        metadataResult.getErrors().forEach(error ->
            result.addError(error.getFieldPath(), error.getMessage()));
        metadataResult.getWarnings().forEach(warning ->
            result.addWarning(warning.getFieldPath(), warning.getMessage()));
    }
    
    private void validateSpec(WorkflowDefinition.WorkflowSpec spec, ValidationResult result) {
        if (spec.getTransferGroups().isEmpty()) {
            result.addWarning("spec.transferGroups", "No transfer groups defined");
        }

        // Only the documented strategies are accepted; group concurrency is set by parallelism
        WorkflowDefinition.ExecutionConfig execution = spec.getExecution();
        if (execution != null && execution.getStrategy() != null
                && !Set.of("sequential", "parallel").contains(execution.getStrategy())) {
            result.addError("spec.execution.strategy", "Unknown execution strategy '" + execution.getStrategy()
                    + "'; expected sequential or parallel");
        }

        // Validate transfer group names are unique
        Set<String> groupNames = new HashSet<>();
        for (TransferGroup group : spec.getTransferGroups()) {
            if (!groupNames.add(group.getName())) {
                result.addError("spec.transferGroups", "Duplicate transfer group name: " + group.getName());
            }
        }

        // Only the defined transfer options are accepted, with valid literal values (DR-Q1)
        List<TransferGroup> groups = spec.getTransferGroups();
        for (int g = 0; g < groups.size(); g++) {
            List<TransferGroup.TransferDefinition> transfers = groups.get(g).getTransfers();
            for (int t = 0; t < transfers.size(); t++) {
                TransferOptions.validate("spec.transferGroups[" + g + "].transfers[" + t + "]",
                        transfers.get(t).getOptions(), result);
            }
        }

        validateConditions(groups, result);
    }

    /**
     * Each group and transfer condition must be in the grammar (ENG-Q1), and every name it refers to
     * must be exactly one group or transfer that has finished when the condition is evaluated: a group
     * this group depends on, directly or through other groups, or a transfer of such a group. A
     * transfer of the same group runs in parallel with it, so it cannot be named.
     */
    private void validateConditions(List<TransferGroup> groups, ValidationResult result) {
        Map<String, TransferGroup> groupsByName = new HashMap<>();
        Map<String, List<String>> transferOwners = new HashMap<>();
        for (TransferGroup group : groups) {
            groupsByName.putIfAbsent(group.getName(), group);
            for (TransferGroup.TransferDefinition transfer : group.getTransfers()) {
                transferOwners.computeIfAbsent(transfer.getName(), name -> new ArrayList<>()).add(group.getName());
            }
        }
        for (int g = 0; g < groups.size(); g++) {
            TransferGroup group = groups.get(g);
            String path = "spec.transferGroups[" + g + "]";
            Set<String> upstream = upstreamOf(group.getName(), groupsByName);
            if (group.getCondition() != null) {
                validateCondition(path + ".condition", group.getCondition(), group.getName(), upstream,
                        groupsByName, transferOwners, result);
            }
            List<TransferGroup.TransferDefinition> transfers = group.getTransfers();
            for (int t = 0; t < transfers.size(); t++) {
                if (transfers.get(t).getCondition() != null) {
                    validateCondition(path + ".transfers[" + t + "].condition", transfers.get(t).getCondition(),
                            group.getName(), upstream, groupsByName, transferOwners, result);
                }
            }
        }
    }

    private static void validateCondition(String field, String condition, String groupName, Set<String> upstream,
                                          Map<String, TransferGroup> groupsByName,
                                          Map<String, List<String>> transferOwners, ValidationResult result) {
        WorkflowCondition.Expression expression;
        try {
            expression = WorkflowCondition.parse(condition);
        } catch (WorkflowCondition.InvalidConditionException e) {
            result.addError(field, "Invalid condition '" + condition + "': " + e.getMessage());
            return;
        }
        for (WorkflowCondition.Reference reference : WorkflowCondition.references(expression)) {
            String name = reference.name();
            boolean isGroup = groupsByName.containsKey(name);
            List<String> owners = transferOwners.getOrDefault(name, List.of());
            if (!isGroup && owners.isEmpty()) {
                result.addError(field, "Condition names '" + name + "', which is no group or transfer of this workflow");
                continue;
            }
            if ((isGroup && !owners.isEmpty()) || owners.size() > 1) {
                result.addError(field, "Condition names '" + name + "', which is ambiguous: it names "
                        + (isGroup ? "a group and " : "") + owners.size() + " transfer(s)");
                continue;
            }
            String target = isGroup ? name : owners.getFirst();
            if (!upstream.contains(target)) {
                result.addError(field, "Condition names '" + name + "', which has not finished when group '"
                        + groupName + "' starts: " + (target.equals(groupName)
                                ? "it is in the same group, whose transfers run in parallel"
                                : "group '" + groupName + "' does not depend on group '" + target + "'")
                        + ". A condition may name only groups this group depends on, directly or through other "
                        + "groups, and their transfers");
                continue;
            }
            if (reference.failure() && !groupsByName.get(target).isContinueOnError()) {
                result.addWarning(field, "failure(" + name + ") can be true only if group '" + target
                        + "' sets continueOnError: true; otherwise the workflow stops when it fails");
            }
        }
    }

    /** The groups that {@code groupName} depends on, directly or through other groups. */
    private static Set<String> upstreamOf(String groupName, Map<String, TransferGroup> groupsByName) {
        Set<String> upstream = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(dependenciesOf(groupName, groupsByName));
        while (!pending.isEmpty()) {
            String dependency = pending.pop();
            if (upstream.add(dependency)) {
                pending.addAll(dependenciesOf(dependency, groupsByName));
            }
        }
        upstream.remove(groupName); // a dependency cycle is reported by the dependency graph, not here
        return upstream;
    }

    private static List<String> dependenciesOf(String groupName, Map<String, TransferGroup> groupsByName) {
        TransferGroup group = groupsByName.get(groupName);
        return group == null ? List.of() : group.getDependsOn();
    }

    @SuppressWarnings("unchecked")
    private List<Object> getObjectListValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof List ? (List<Object>) value : null;
    }

    private List<String> parseTagsList(List<Object> tagsList) {
        if (tagsList == null) {
            return List.of();
        }

        return tagsList.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .toList();
    }
}
