<img src="quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Workflow README

**Version:** 2.4  
**Date:** 2026-10-18  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Scope:** Current in-process workflow parser and execution behavior

This document reflects the workflow functionality implemented in `quorus-workflow` today.

## What the Workflow Module Does

The workflow module provides:

- YAML parsing with `YamlWorkflowDefinitionParser`
- schema validation
- semantic validation
- variable resolution
- dependency graph construction and topological sorting
- workflow execution through `SimpleWorkflowEngine`
- dry run and virtual run modes

## Current YAML Shape

Recommended structure:

```yaml
apiVersion: v1
metadata:
  name: "daily-settlement-report"
  version: "1.0.0"
  description: "Receive and stage a daily settlement position report"
  type: "financial-reporting-workflow"
  author: "settlement-operations@example.com"
  created: "2026-03-14"
  tags: ["settlement", "reporting", "daily"]

spec:
  variables:
    sourceBase: "https://clearing.example.com/reports"
    outputDir: "/data/settlement/incoming"

  execution:
    dryRun: false
    virtualRun: false
    parallelism: 1
    timeout: 3600s
    strategy: sequential

  transferGroups:
    - name: receive-settlement-report
      description: "Receive the clearing-house position report"
      dependsOn: []
      continueOnError: false
      retryCount: 2
      transfers:
        - name: settlement-positions
          source: "{{sourceBase}}/settlement-positions.csv"
          destination: "{{outputDir}}/settlement-positions.csv"
          protocol: https
```

Every metadata field shown is required by validation, and so is the `execution` block; the [YAML Syntax Guide](QUORUS_YAML_SYNTAX_GUIDE.md#validation-requirements) gives the exact rules. The engine validates a workflow before every run, so a workflow that fails validation never starts.

## Parser-Supported Fields

### Metadata

The parser supports these metadata fields:

- `name`
- `version`
- `description`
- `type`
- `author`
- `created`
- `tags`
- `labels`

### Spec

The parser supports these spec fields:

- `variables`
- `execution`
- `transferGroups`

### Execution

The parser supports:

- `dryRun`
- `virtualRun`
- `parallelism`
- `timeout`
- `strategy`

### Transfer Groups

The parser supports:

- `name`
- `description`
- `dependsOn`
- `condition`
- `variables`
- `continueOnError`
- `retryCount`
- `transfers`

### Transfers

The parser supports:

- `name`
- `source`
- `destination`
- `protocol`
- `options`
- `condition`

## Execution Modes

`SimpleWorkflowEngine` exposes three modes:

- `NORMAL`
- `DRY_RUN`
- `VIRTUAL_RUN`

## Important Current Limitations

- A `condition` uses `success(name)`, `failure(name)`, `file_exists('path')`, `not`, `and` and `or`, and may name only upstream groups and their transfers; a false condition skips its group or transfer (the [YAML Syntax Guide](QUORUS_YAML_SYNTAX_GUIDE.md#conditions) gives the rules). A transfer cannot wait for another transfer of the same group.
- Transfer `options` accept only `checksum` (verified by every adapter) and `expectedSize` (the progress total). Any other key fails validation (decision `DR-Q1`); the [YAML Syntax Guide](QUORUS_YAML_SYNTAX_GUIDE.md#options-map) gives the rules.
- A reference inside a variable's value is resolved too, up to 10 levels; a cycle or deeper nesting fails the run.
- `spec.variables` rank above the runtime variables a caller passes in the execution context, so a caller cannot override a value the YAML declares.
- Older documentation that described rich workflow notifications, cleanup policies, SLA sections, or advanced credential models does not match the current parser.
- The current parser does not accept a broad workflow spec vocabulary beyond the fields listed above.
- The YAML model accepts URI strings, but production distributed execution must use approved service aliases and opaque secret references when that canonical connectivity contract is implemented. Credentials must not be embedded in workflow URIs.

## Current Source of Truth

- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/YamlWorkflowDefinitionParser.java`
- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/SimpleWorkflowEngine.java`
- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/VariableResolver.java`

## Examples Available Now

The `quorus-integration-examples` module currently includes workflow examples such as:

- `BasicWorkflowExample`
- `ComplexWorkflowExample`
- `WorkflowValidationExample`
- `SchemaValidationExample`
- `WorkflowValidationCLI`

See `docs/QUORUS_INTEGRATION_EXAMPLES_README.md` for the current example list.
