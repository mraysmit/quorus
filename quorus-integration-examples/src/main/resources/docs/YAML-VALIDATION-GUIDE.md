# Quorus YAML Schema Validation Guide

## Overview

Quorus now includes a comprehensive YAML schema validation system that ensures workflow definitions are consistent, complete, and follow best practices. This guide covers the new validation features, requirements, and migration from legacy formats.

## Table of Contents

1. [Standard Metadata Schema](#standard-metadata-schema)
2. [Validation Features](#validation-features)
3. [Required Fields](#required-fields)
4. [Field Formats and Constraints](#field-formats-and-constraints)
5. [Best Practices](#best-practices)
6. [Migration Guide](#migration-guide)
7. [Validation Tools](#validation-tools)
8. [Examples](#examples)

## Standard Metadata Schema

All Quorus workflow YAML files must now include a complete metadata section with the following required fields:

```yaml
metadata:
  name: "Workflow Name"                    # Required - workflow identifier
  version: "1.0.0"                        # Required - semantic version
  description: "Workflow description"      # Required - human readable description  
  type: "workflow-type"                    # Required - workflow type classification
  author: "author@company.com"             # Required - contact information
  created: "2025-08-21"                    # Required - ISO date format (YYYY-MM-DD)
  tags: ["tag1", "tag2", "category"]       # Required - array of classification tags
  labels:                                  # Optional - key-value metadata
    environment: "production"
    team: "data-engineering"
```

## Validation Features

### Schema Validation
- **JSON Schema**: Formal schema definition for structure validation
- **Field Format Validation**: Regex patterns for all metadata fields
- **Business Rule Validation**: Date validation, tag uniqueness, etc.
- **Deprecation Warnings**: Alerts for deprecated fields like `kind`

### Error Reporting
- **Detailed Error Messages**: Specific field paths and descriptions
- **Warning System**: Non-blocking issues and recommendations
- **Validation Result Objects**: Programmatic access to validation results

### Performance
- **Fast Validation**: Optimized for large workflows
- **Batch Processing**: Validate multiple files efficiently
- **Memory Efficient**: Streaming validation for large files

## Required Fields

### name
- **Type**: String
- **Length**: 2-100 characters
- **Format**: Must start and end with alphanumeric characters
- **Allowed**: Letters, numbers, spaces, hyphens, underscores
- **Example**: `"Customer Data ETL Pipeline"`

### version
- **Type**: String
- **Format**: Semantic versioning (MAJOR.MINOR.PATCH)
- **Pattern**: `^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9\-\.]+)?(\+[a-zA-Z0-9\-\.]+)?$`
- **Examples**: `"1.0.0"`, `"2.1.3-beta"`, `"1.0.0+build.123"`

### description
- **Type**: String
- **Length**: 10-500 characters
- **Purpose**: Human-readable workflow description
- **Example**: `"Production ETL pipeline for customer data processing"`

### type
- **Type**: String
- **Format**: Lowercase with hyphens
- **Pattern**: `^[a-z][a-z0-9\-]*[a-z0-9]$`
- **Recommended Types**:
  - `transfer-workflow`: File transfer operations
  - `data-pipeline-workflow`: ETL and data processing
  - `download-workflow`: Download operations
  - `validation-test-workflow`: Testing and validation
  - `external-data-config`: External data configurations
  - `etl-workflow`: Extract, Transform, Load operations
  - `backup-workflow`: Backup and archival operations
  - `sync-workflow`: Synchronization operations

### author
- **Type**: String
- **Format**: Email address (preferred) or name
- **Length**: 1-100 characters
- **Examples**: `"data-team@company.com"`, `"John Doe"`

### created
- **Type**: String
- **Format**: ISO date format (YYYY-MM-DD)
- **Pattern**: `^[0-9]{4}-[0-9]{2}-[0-9]{2}$`
- **Validation**: Must be a valid date, warning if future date
- **Example**: `"2025-08-21"`

### tags
- **Type**: Array of strings
- **Count**: 1-20 tags
- **Tag Format**: Lowercase with hyphens
- **Tag Pattern**: `^[a-z][a-z0-9\-]*[a-z0-9]$`
- **Tag Length**: 1-30 characters each
- **Uniqueness**: No duplicate tags allowed
- **Examples**: `["production", "etl", "customer-data"]`

## Field Formats and Constraints

### Tag Guidelines
- Use lowercase letters and numbers only
- Separate words with hyphens (not underscores or spaces)
- Keep tags short and descriptive
- Include environment, team, and category tags
- Examples of good tags: `production`, `data-pipeline`, `customer-data`
- Examples of bad tags: `PRODUCTION`, `data_pipeline`, `Customer Data`

### Version Guidelines
- Follow semantic versioning strictly
- Use pre-release identifiers for testing: `1.0.0-beta`, `1.0.0-alpha.1`
- Include build metadata when needed: `1.0.0+build.123`
- Increment versions appropriately:
  - MAJOR: Breaking changes
  - MINOR: New features (backward compatible)
  - PATCH: Bug fixes (backward compatible)

### Name Guidelines
- Use descriptive, human-readable names
- Include the purpose and scope
- Avoid abbreviations unless widely understood
- Examples: `"Customer Data ETL Pipeline"`, `"Monthly Financial Reporting"`

## Best Practices

### Metadata Organization
```yaml
metadata:
  # Core identification
  name: "Production Customer ETL"
  version: "2.1.0"
  description: "Production ETL pipeline for customer data with error handling"
  type: "etl-workflow"
  
  # Ownership and tracking
  author: "data-engineering@company.com"
  created: "2025-08-21"
  
  # Classification and discovery
  tags: ["production", "etl", "customer-data", "error-handling"]
  
  # Additional metadata
  labels:
    environment: "production"
    team: "data-engineering"
    criticality: "high"
    schedule: "daily"
```

### Tag Strategy
- **Environment**: `production`, `staging`, `development`, `test`
- **Team**: `data-engineering`, `finance`, `operations`, `marketing`
- **Category**: `etl`, `backup`, `reporting`, `monitoring`, `validation`
- **Technology**: `postgresql`, `kafka`, `s3`, `api`, `file-transfer`
- **Schedule**: `daily`, `hourly`, `weekly`, `monthly`, `on-demand`

### Versioning Strategy
- Start with `1.0.0` for production workflows
- Use `0.x.x` for development/testing
- Increment MINOR for new features
- Increment PATCH for bug fixes
- Use pre-release for testing: `1.1.0-beta`

## Migration Guide

### From Legacy Format

**Old Format (Deprecated):**
```yaml
kind: TransferWorkflow  # Deprecated
metadata:
  name: "Legacy Workflow"
  description: "Old format"
  # Missing required fields
```

**New Format (Required):**
```yaml
metadata:
  name: "Legacy Workflow"
  version: "1.0.0"                        # Added
  description: "Migrated workflow with complete metadata"  # Enhanced
  type: "transfer-workflow"                # Added (replaces kind)
  author: "migration@company.com"          # Added
  created: "2025-08-21"                    # Added
  tags: ["migrated", "transfer"]           # Added
```

### Migration Steps
1. **Remove** deprecated `kind` field
2. **Add** missing required fields: `version`, `type`, `author`, `created`, `tags`
3. **Enhance** description to meet minimum length (10 characters)
4. **Validate** using the CLI tool or programmatic validation
5. **Test** the migrated workflow

### Backward Compatibility
- Legacy workflows with `kind` field still work but generate warnings
- Missing optional fields don't break existing workflows
- Gradual migration is supported

## Validation Tools

### Command Line Interface
```bash
# Validate a single file
java WorkflowValidationCLI workflow.yaml

# Validate multiple files
java WorkflowValidationCLI workflow1.yaml workflow2.yaml

# Validate all YAML files in a directory
java WorkflowValidationCLI --validate-directory ./workflows/

# Strict validation (warnings as errors)
java WorkflowValidationCLI --strict workflow.yaml

# Quiet mode (only errors)
java WorkflowValidationCLI --quiet workflow.yaml

# Schema validation only
java WorkflowValidationCLI --schema-only workflow.yaml
```

### Programmatic Validation
```java
WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();

// Schema validation
ValidationResult schemaResult = parser.validateSchema(yamlContent);

// Full validation (schema + semantic)
WorkflowDefinition workflow = parser.parseFromString(yamlContent);
ValidationResult fullResult = parser.validate(workflow);

// Check results
if (fullResult.isValid()) {
    System.out.println("Workflow is valid");
} else {
    fullResult.getErrors().forEach(error -> 
        System.out.println("Error: " + error.getFieldPath() + " - " + error.getMessage()));
}
```

## Examples

### Complete Production Workflow
See: `workflows/ecommerce-order-processing.yaml`

### Financial Reporting Workflow  
See: `workflows/financial-reporting.yaml`

### Schema Compliant Example
See: `workflows/schema-compliant-example.yaml`

### Validation Examples
Run: `java ValidationExamplesRunner`

## Error Reference

### Common Validation Errors

| Error | Cause | Solution |
|-------|-------|----------|
| `Required field 'name' is missing` | Missing name field | Add `name` field to metadata |
| `Name must be at least 2 characters long` | Name too short | Use descriptive name (2+ chars) |
| `Version must follow semantic versioning` | Invalid version format | Use format like `1.0.0` |
| `Description must be at least 10 characters` | Description too short | Provide meaningful description |
| `Type must be lowercase with hyphens` | Invalid type format | Use `data-pipeline-workflow` not `Data_Pipeline_Workflow` |
| `Author should be an email address` | Invalid author format | Use email: `user@company.com` |
| `Created date must be in ISO format` | Invalid date format | Use `YYYY-MM-DD` format |
| `Tags must be lowercase with hyphens` | Invalid tag format | Use `customer-data` not `Customer_Data` |
| `Duplicate tags are not allowed` | Duplicate tags | Remove duplicate entries |
| `Field 'kind' is deprecated` | Using legacy field | Replace `kind` with `metadata.type` |

### Getting Help

- Check this guide for field requirements
- Use the CLI tool with `--verbose` for detailed information
- Run validation examples: `java ValidationExamplesRunner`
- Review example workflows in `workflows/` directory

## Conclusion

The new YAML schema validation system ensures consistency, completeness, and best practices across all Quorus workflows. By following this guide, you can create robust, maintainable workflow definitions that integrate seamlessly with the Quorus platform.

For additional support or questions, contact the development team or refer to the comprehensive test suite in the codebase.
