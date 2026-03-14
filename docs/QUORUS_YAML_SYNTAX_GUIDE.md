# Quorus YAML Syntax Guide

**Version:** 2.0  
**Date:** 2026-03-14

This guide documents the YAML fields that the current `YamlWorkflowDefinitionParser` accepts.

## Scope

This guide is intentionally narrow. It covers the syntax that is implemented today in:

- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/YamlWorkflowDefinitionParser.java`
- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/VariableResolver.java`

It does **not** describe older, broader proposals such as workflow notifications, cleanup policies, SLA sections, OAuth credential blocks, tenant YAML documents, RBAC YAML documents, or large built-in function catalogs that are not backed by the current parser.

## Minimal Workflow

```yaml
metadata:
  name: "minimal-workflow"
  version: "1.0.0"

spec:
  transferGroups:
    - name: download
      transfers:
        - name: fetch-file
          source: "https://example.com/file.csv"
          destination: "/data/file.csv"
          protocol: https
```

## Recommended Full Structure

```yaml
apiVersion: v1
metadata:
  name: "daily-ingest"
  version: "1.0.0"
  description: "Download and stage daily input data"
  type: "workflow"
  author: "ops@example.com"
  created: "2026-03-14"
  tags: ["daily", "ingest"]
  labels:
    team: "ops"
    environment: "dev"

spec:
  variables:
    baseUrl: "https://example.com"
    outputDir: "/data/out"

  execution:
    dryRun: false
    virtualRun: false
    parallelism: 1
    timeout: 3600s
    strategy: sequential

  transferGroups:
    - name: stage-1-download
      description: "Fetch input files"
      dependsOn: []
      condition: "always"
      variables:
        filename: "dataset.csv"
      continueOnError: false
      retryCount: 2
      transfers:
        - name: download-dataset
          source: "{{baseUrl}}/{{filename}}"
          destination: "{{outputDir}}/{{filename}}"
          protocol: http
          options:
            timeout: 30s
          condition: "always"
```

## Top-Level Fields

### `apiVersion`

- Optional
- Defaults to `v1`

### `metadata`

Supported fields:

- `name`
- `version`
- `description`
- `type`
- `author`
- `created`
- `tags`
- `labels`

### `spec`

Supported fields:

- `variables`
- `execution`
- `transferGroups`

The parser also supports a legacy form where spec fields are provided at the root instead of inside `spec`, but new YAML should use the explicit `spec` block.

## Execution Block

Supported fields:

- `dryRun`
- `virtualRun`
- `parallelism`
- `timeout`
- `strategy`

Duration values are parsed from simple suffix forms such as:

- `30s`
- `5m`
- `2h`

## Transfer Groups

Supported fields:

- `name`
- `description`
- `dependsOn`
- `condition`
- `variables`
- `continueOnError`
- `retryCount`
- `transfers`

`dependsOn` is used when building the dependency graph.

`condition` is parsed and variable-resolved. The current workflow engine does not expose a dedicated condition evaluation subsystem beyond carrying the resolved condition string.

## Transfers

Supported fields:

- `name`
- `source`
- `destination`
- `protocol`
- `options`
- `condition`

## Variables

Variable resolution is handled by `VariableResolver`.

Supported behavior documented by current code:

- workflow-level variables from `spec.variables`
- execution-context variables supplied at runtime
- variable resolution in transfer fields and conditions

Keep variable usage simple and explicit. This repository’s current parser and resolver are much narrower than older documentation claimed.

## What Not to Use

Do not rely on the following YAML sections unless you implement matching parser and runtime support first:

- `notifications`
- `cleanup`
- `sla`
- `credentials` blocks with OAuth flows
- `conditions` collections at spec level
- `TenantConfiguration`
- `RoleBasedAccessControl`
- advanced pipe operators and large built-in function catalogs

Those constructs appeared in historical docs but are not part of the current parser contract.

## Validation Workflow

Recommended validation order:

1. Parse YAML with `YamlWorkflowDefinitionParser`
2. Run schema validation
3. Run semantic validation
4. Resolve variables
5. Build the dependency graph
6. Execute in `DRY_RUN` or `VIRTUAL_RUN` before normal execution

## Source of Truth

- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/YamlWorkflowDefinitionParser.java`
- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/SimpleWorkflowEngine.java`
- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/TransferGroup.java`