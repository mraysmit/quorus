<img src="quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Workflow README

**Version:** 2.0  
**Date:** 2026-03-14  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

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
  name: "daily-sync"
  version: "1.0.0"
  description: "Download and stage a daily dataset"
  type: "workflow"
  author: "ops@example.com"
  created: "2026-03-14"
  tags: ["daily", "sync"]

spec:
  variables:
    sourceBase: "https://example.com"
    outputDir: "/data/out"

  execution:
    dryRun: false
    virtualRun: false
    parallelism: 1
    timeout: 3600s
    strategy: sequential

  transferGroups:
    - name: fetch
      description: "Download source files"
      dependsOn: []
      continueOnError: false
      retryCount: 2
      transfers:
        - name: dataset
          source: "{{sourceBase}}/dataset.csv"
          destination: "{{outputDir}}/dataset.csv"
          protocol: http
          options:
            timeout: 30s
```

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

- `condition` values are parsed and variable-resolved, but the current workflow engine does not expose a dedicated condition evaluator in execution flow.
- Older documentation that described rich workflow notifications, cleanup policies, SLA sections, or advanced credential models does not match the current parser.
- The current parser does not accept a broad workflow spec vocabulary beyond the fields listed above.

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