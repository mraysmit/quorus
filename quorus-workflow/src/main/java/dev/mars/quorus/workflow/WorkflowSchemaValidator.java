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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Comprehensive schema validator for workflow YAML definitions.
 * Validates metadata according to the standard schema requirements.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-21
 * @version 1.0
 */
public class WorkflowSchemaValidator {
    
    // Regex patterns for validation
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9\\-_]*[a-zA-Z0-9]$");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+(-[a-zA-Z0-9\\-\\.]+)?(\\+[a-zA-Z0-9\\-\\.]+)?$");
    private static final Pattern TYPE_PATTERN = Pattern.compile("^[a-z][a-z0-9\\-]*[a-z0-9]$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern TAG_PATTERN = Pattern.compile("^[a-z][a-z0-9\\-]*[a-z0-9]$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}$");
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    
    // Valid workflow types
    private static final Set<String> VALID_WORKFLOW_TYPES = Set.of(
        "transfer-workflow",
        "data-pipeline-workflow", 
        "download-workflow",
        "validation-test-workflow",
        "external-data-config",
        "etl-workflow",
        "backup-workflow",
        "sync-workflow"
    );
    
    /**
     * Validates the complete workflow schema including metadata and spec.
     */
    public ValidationResult validateWorkflowSchema(Map<String, Object> data) {
        ValidationResult result = new ValidationResult();
        
        // Validate root level structure
        validateRootStructure(data, result);
        
        // Validate metadata if present
        if (data.containsKey("metadata")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
            validateMetadataSchema(metadata, result);
        }
        
        // Validate spec if present
        if (data.containsKey("spec")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> spec = (Map<String, Object>) data.get("spec");
            validateSpecSchema(spec, result);
        }
        
        return result;
    }
    
    /**
     * Validates the root structure of the workflow definition.
     */
    private void validateRootStructure(Map<String, Object> data, ValidationResult result) {
        // Required fields
        if (!data.containsKey("metadata")) {
            result.addError("metadata", "Required field 'metadata' is missing");
        }
        
        if (!data.containsKey("spec")) {
            result.addError("spec", "Required field 'spec' is missing");
        }
        
        // Validate apiVersion if present
        if (data.containsKey("apiVersion")) {
            String apiVersion = getStringValue(data, "apiVersion");
            if (apiVersion != null && !apiVersion.matches("^v[0-9]+$")) {
                result.addError("apiVersion", "API version must follow pattern 'v{number}' (e.g., 'v1')");
            }
        }
    }
    
    /**
     * Validates the metadata section according to the standard schema.
     */
    public ValidationResult validateMetadataSchema(Map<String, Object> metadata) {
        ValidationResult result = new ValidationResult();
        validateMetadataSchema(metadata, result);
        return result;
    }
    
    private void validateMetadataSchema(Map<String, Object> metadata, ValidationResult result) {
        if (metadata == null) {
            result.addError("metadata", "Metadata cannot be null");
            return;
        }
        
        // Required fields validation
        validateRequiredMetadataFields(metadata, result);
        
        // Field format validation
        validateMetadataFieldFormats(metadata, result);
        
        // Business logic validation
        validateMetadataBusinessRules(metadata, result);
    }
    
    private void validateRequiredMetadataFields(Map<String, Object> metadata, ValidationResult result) {
        String[] requiredFields = {"name", "version", "description", "type", "author", "created", "tags"};
        
        for (String field : requiredFields) {
            if (!metadata.containsKey(field)) {
                result.addError("metadata." + field, "Required field '" + field + "' is missing");
            } else {
                Object value = metadata.get(field);
                if (value == null) {
                    result.addError("metadata." + field, "Field '" + field + "' cannot be null");
                } else if (value instanceof String && ((String) value).trim().isEmpty()) {
                    result.addError("metadata." + field, "Field '" + field + "' cannot be empty");
                } else if ("tags".equals(field) && value instanceof List && ((List<?>) value).isEmpty()) {
                    result.addError("metadata." + field, "Field 'tags' must contain at least one tag");
                }
            }
        }
    }
    
    private void validateMetadataFieldFormats(Map<String, Object> metadata, ValidationResult result) {
        // Validate name format
        String name = getStringValue(metadata, "name");
        if (name != null) {
            if (name.length() < 2) {
                result.addError("metadata.name", "Name must be at least 2 characters long");
            } else if (name.length() > 100) {
                result.addError("metadata.name", "Name must be 100 characters or less");
            } else if (!NAME_PATTERN.matcher(name).matches()) {
                result.addError("metadata.name", "Name must start and end with alphanumeric characters, can contain hyphens and underscores but not spaces");
            }
        }
        
        // Validate version format
        String version = getStringValue(metadata, "version");
        if (version != null && !VERSION_PATTERN.matcher(version).matches()) {
            result.addError("metadata.version", "Version must follow semantic versioning format (e.g., '1.0.0', '2.1.0-beta')");
        }
        
        // Validate description length
        String description = getStringValue(metadata, "description");
        if (description != null) {
            if (description.length() < 10) {
                result.addError("metadata.description", "Description must be at least 10 characters long");
            } else if (description.length() > 500) {
                result.addError("metadata.description", "Description must be 500 characters or less");
            }
        }
        
        // Validate type format
        String type = getStringValue(metadata, "type");
        if (type != null) {
            if (type.length() > 50) {
                result.addError("metadata.type", "Type must be 50 characters or less");
            } else if (!TYPE_PATTERN.matcher(type).matches()) {
                result.addError("metadata.type", "Type must be lowercase, start and end with letters/numbers, can contain hyphens");
            } else if (!VALID_WORKFLOW_TYPES.contains(type)) {
                result.addWarning("metadata.type", "Type '" + type + "' is not in the list of recommended workflow types");
            }
        }
        
        // Validate author format (email preferred)
        String author = getStringValue(metadata, "author");
        if (author != null) {
            if (author.length() > 100) {
                result.addError("metadata.author", "Author must be 100 characters or less");
            } else if (!EMAIL_PATTERN.matcher(author).matches() && !author.matches("^[a-zA-Z\\s]+$")) {
                result.addError("metadata.author", "Author should be an email address or a name");
            }
        }
        
        // Validate created date format
        String created = getStringValue(metadata, "created");
        if (created != null) {
            if (!DATE_PATTERN.matcher(created).matches()) {
                result.addError("metadata.created", "Created date must be in ISO format (YYYY-MM-DD)");
            } else {
                try {
                    LocalDate.parse(created, DATE_FORMATTER);
                } catch (DateTimeParseException e) {
                    result.addError("metadata.created", "Created date is not a valid date: " + created);
                }
            }
        }
        
        // Validate tags format
        Object tagsObj = metadata.get("tags");
        if (tagsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> tags = (List<Object>) tagsObj;
            
            if (tags.size() > 20) {
                result.addError("metadata.tags", "Maximum 20 tags allowed");
            }
            
            for (int i = 0; i < tags.size(); i++) {
                Object tag = tags.get(i);
                if (!(tag instanceof String)) {
                    result.addError("metadata.tags[" + i + "]", "Tag must be a string");
                } else {
                    String tagStr = (String) tag;
                    if (tagStr.length() > 30) {
                        result.addError("metadata.tags[" + i + "]", "Tag must be 30 characters or less");
                    } else if (!TAG_PATTERN.matcher(tagStr).matches()) {
                        result.addError("metadata.tags[" + i + "]", "Tag '" + tagStr + "' must be lowercase, start and end with letters/numbers, can contain hyphens");
                    }
                }
            }
            
            // Check for duplicate tags
            Set<String> uniqueTags = Set.copyOf(tags.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList());
            if (uniqueTags.size() != tags.size()) {
                result.addError("metadata.tags", "Duplicate tags are not allowed");
            }
        } else if (tagsObj != null) {
            result.addError("metadata.tags", "Tags must be an array of strings");
        }
    }
    
    private void validateMetadataBusinessRules(Map<String, Object> metadata, ValidationResult result) {
        // Validate created date is not in the future
        String created = getStringValue(metadata, "created");
        if (created != null && DATE_PATTERN.matcher(created).matches()) {
            try {
                LocalDate createdDate = LocalDate.parse(created, DATE_FORMATTER);
                if (createdDate.isAfter(LocalDate.now())) {
                    result.addWarning("metadata.created", "Created date is in the future");
                }
            } catch (DateTimeParseException e) {
                // Already handled in format validation
            }
        }
    }
    
    private void validateSpecSchema(Map<String, Object> spec, ValidationResult result) {
        // Basic spec validation - can be extended
        if (!spec.containsKey("execution")) {
            result.addError("spec.execution", "Required field 'execution' is missing from spec");
        }
        
        if (!spec.containsKey("transferGroups")) {
            result.addWarning("spec.transferGroups", "No transfer groups defined in spec");
        }
    }
    
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : null;
    }
}
