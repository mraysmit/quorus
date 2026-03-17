<img src="quorus-logo.png" alt="Quorus" width="120"/>

# Quorus YAML Syntax Guide

**Version:** 2.1  
**Date:** 2026-03-17  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

This guide documents the YAML fields that the current `YamlWorkflowDefinitionParser` accepts.

## Scope

This guide covers the syntax implemented in:

- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/YamlWorkflowDefinitionParser.java`
- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/VariableResolver.java`
- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/WorkflowDefinition.java`
- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/TransferGroup.java`

Every field, default, and behavior described here is verified against parser source code and the real YAML files in `quorus-integration-examples/src/main/resources/workflows/` and `quorus-workflow/src/test/resources/`.

It does **not** describe older, broader proposals such as workflow notifications, cleanup policies, SLA sections, OAuth credential blocks, tenant YAML documents, RBAC YAML documents, or large built-in function catalogs that are not backed by the current parser.

## Document Structure

A workflow YAML has three top-level keys:

```yaml
apiVersion: v1      # optional — defaults to "v1"
metadata:           # required — workflow identity
  ...
spec:               # required — variables, execution config, transfer groups
  ...
```

The parser also supports a legacy form where `variables`, `execution`, and `transferGroups` appear at the root instead of inside `spec`, but new YAML should always use the explicit `spec` block.

---

## Minimal Workflow

The smallest valid workflow needs only `metadata.name` and at least one transfer group with one transfer:

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

---

## `apiVersion`

| Property | Detail |
|----------|--------|
| Required | No |
| Default | `"v1"` |
| Parser line | `getStringValue(data, "apiVersion", "v1")` |

---

## `metadata`

All metadata fields are parsed as strings. Only `name` is required.

| Field | Required | Default | Description |
|-------|----------|---------|-------------|
| `name` | **Yes** | — | Workflow identifier. Parser throws `WorkflowParseException` if empty. |
| `version` | No | `"1.0.0"` | Semantic version string. |
| `description` | No | `null` | Free-text description. |
| `type` | No | `"workflow"` | Workflow type label (e.g., `"download-workflow"`, `"etl-workflow"`, `"data-pipeline-workflow"`). |
| `author` | No | `null` | Author identifier. |
| `created` | No | `null` | Creation date string (e.g., `"2025-08-21"`). Not parsed as a date object. |
| `tags` | No | `[]` | List of string tags for categorization. |
| `labels` | No | `{}` | Key-value map of string labels. |

**Example from `ecommerce-order-processing.yaml`:**

```yaml
metadata:
  name: "ecommerce-order-processing-pipeline"
  version: "3.1.2"
  description: "Complete order processing pipeline from payment verification to fulfillment"
  type: "data-pipeline-workflow"
  author: "ecommerce-team@company.com"
  created: "2025-08-21"
  tags: ["ecommerce", "orders", "payment", "fulfillment", "inventory", "production"]
  labels:
    environment: "production"
    team: "ecommerce"
    sla: "15-minutes"
    criticality: "high"
    schedule: "continuous"
```

---

## `spec`

The spec block contains three sections:

| Section | Required | Description |
|---------|----------|-------------|
| `variables` | No | Global variables available to all transfer groups. |
| `execution` | No | Execution configuration (parallelism, timeout, strategy). |
| `transferGroups` | No (but an empty list triggers a validation warning) | Ordered list of transfer groups. |

---

## `spec.variables`

A flat key-value map. Values are stored as objects but typically strings. Variables are referenced elsewhere as `{{variableName}}`.

```yaml
spec:
  variables:
    baseUrl: "https://httpbin.org"
    outputDir: "/tmp/downloads"
    timeout: "30s"
    maxRetries: "3"
    chunkSize: "2048"
```

Variables can also be used for path construction with embedded references:

```yaml
# From financial-reporting.yaml:
spec:
  variables:
    reportMonth: "{{current_month}}"
    reportYear: "{{current_year}}"
    fiscalQuarter: "{{current_quarter}}"
```

Variable values that themselves contain `{{...}}` references are resolved when the variable is used, not when it is declared. Variables referencing other variables or environment variables will be resolved by `VariableResolver` at execution time.

---

## `spec.execution`

Controls how the workflow engine runs transfer groups.

| Field | Required | Default | Type | Description |
|-------|----------|---------|------|-------------|
| `dryRun` | No | `false` | boolean | When `true`, workflow runs without performing actual transfers. |
| `virtualRun` | No | `false` | boolean | Virtual run mode for testing. |
| `parallelism` | No | `1` | integer | Maximum concurrent transfer groups. Minimum is 1 (enforced by `Math.max(1, parallelism)`). |
| `timeout` | No | `"3600s"` (1 hour) | duration | Overall workflow timeout. |
| `strategy` | No | `"sequential"` | string | Execution strategy. Values seen in real YAML: `"sequential"`, `"parallel"`. |

**Duration format:**

The parser accepts simple suffix forms. The suffix is case-insensitive after `trim().toLowerCase()`:

| Suffix | Meaning | Example |
|--------|---------|---------|
| `s` | Seconds | `30s`, `300s`, `3600s` |
| `m` | Minutes | `5m`, `15m` |
| `h` | Hours | `1h`, `2h` |
| (none) | Seconds | `3600` |

If the duration string is empty, null, or unparseable, the parser defaults to 1 hour.

**Example from `data-pipeline.yaml`:**

```yaml
spec:
  execution:
    dryRun: false
    virtualRun: false
    parallelism: 3
    timeout: 1800s
    strategy: parallel
```

**Example from `financial-reporting.yaml`:**

```yaml
spec:
  execution:
    dryRun: false
    virtualRun: false
    parallelism: 2
    timeout: 7200s
    strategy: sequential
```

---

## `spec.transferGroups`

An ordered list of transfer groups. Each group contains transfers and can declare dependencies on other groups.

### Transfer Group Fields

| Field | Required | Default | Type | Description |
|-------|----------|---------|------|-------------|
| `name` | **Yes** | — | string | Group identifier. Must be non-empty. Used as the node name in the dependency graph. |
| `description` | No | `null` | string | Human-readable description. |
| `dependsOn` | No | `[]` | list of strings | Names of groups that must complete before this group runs. |
| `condition` | No | `null` | string | Condition expression. Parsed and variable-resolved, but the current engine does not evaluate conditions — it carries the resolved string through execution. |
| `variables` | No | `null` | map | Group-scoped variables. These are merged on top of global variables during resolution (group variables take precedence). |
| `continueOnError` | No | `false` | boolean | When `true`, workflow continues to dependent groups even if this group fails. |
| `retryCount` | No | `0` | integer | Number of retry attempts for the group. |
| `transfers` | No | `[]` | list | List of transfer definitions within this group. |

### Dependency Graph

`dependsOn` references group names. Groups form a directed acyclic graph. The parser's `buildDependencyGraph()` method validates:
- All referenced group names actually exist
- No circular dependencies

**Example — simple chain from `simple-workflow.yaml`:**

```yaml
transferGroups:
  - name: download-base-files
    description: Download basic test files
    continueOnError: false
    retryCount: 3
    variables:
      fileSize: "1024"
    transfers:
      - ...

  - name: download-additional-files
    description: Download additional files after base files complete
    dependsOn:
      - download-base-files
    continueOnError: true
    retryCount: 2
    transfers:
      - ...

  - name: download-final-files
    description: Final batch of files
    dependsOn:
      - download-base-files
      - download-additional-files
    transfers:
      - ...
```

**Example — multi-stage pipeline from `ecommerce-order-processing.yaml`:**

```yaml
transferGroups:
  - name: process-payments
    description: Process pending payment verifications
    continueOnError: false
    retryCount: 3
    transfers: [...]

  - name: manage-inventory
    dependsOn:
      - process-payments
    continueOnError: false
    retryCount: 2
    transfers: [...]

  - name: fulfill-orders
    dependsOn:
      - manage-inventory
    continueOnError: true
    retryCount: 2
    transfers: [...]

  - name: notify-customers
    dependsOn:
      - fulfill-orders
    continueOnError: true
    retryCount: 1
    transfers: [...]

  - name: update-analytics
    dependsOn:
      - notify-customers
    continueOnError: true
    retryCount: 1
    transfers: [...]

  - name: cleanup-and-archive
    dependsOn:
      - update-analytics
    continueOnError: true
    retryCount: 1
    transfers: [...]
```

---

## `transfers`

Individual transfer definitions within a group.

| Field | Required | Default | Type | Description |
|-------|----------|---------|------|-------------|
| `name` | **Yes** | — | string | Transfer identifier. Must be non-empty. |
| `source` | **Yes** | — | string | Source URI or path. Supports `{{variable}}` substitution. |
| `destination` | **Yes** | — | string | Destination path or URI. Supports `{{variable}}` substitution. |
| `protocol` | No | `"http"` | string | Protocol identifier used by `ProtocolFactory` to select the adapter. |
| `options` | No | `{}` | map | Arbitrary key-value options map. String values are variable-resolved. Non-string values are passed through as-is. |
| `condition` | No | `null` | string | Condition expression. Parsed and variable-resolved but not evaluated by the current engine. |

### Protocol Values Used in Real YAML

The following protocol values appear in the example workflows:

| Value | Description | Source Example |
|-------|-------------|----------------|
| `http` | HTTP protocol adapter | `simple-download.yaml` |
| `https` | HTTPS protocol adapter | `ecommerce-order-processing.yaml` |
| `database` | Used in example YAML but not backed by a registered `ProtocolFactory` adapter | `financial-reporting.yaml` |
| `file` | Used in example YAML but not backed by a registered `ProtocolFactory` adapter | `financial-reporting.yaml` |
| `email` | Used in example YAML but not backed by a registered `ProtocolFactory` adapter | `financial-reporting.yaml` |

The actually registered protocol adapters in `ProtocolFactory.registerDefaultProtocols()` are: `http`, `https`, `ftp`, `ftps`, `sftp`, `smb`, `cifs`, `nfs`. Any protocol value not registered will fail at transfer execution time, not at parse time.

### Options Map

The `options` map is freeform — the parser does not validate option keys. String values go through variable resolution; all others are passed through unchanged. These are the option keys used in the real YAML files:

| Option Key | Example Value | Appears In |
|------------|---------------|------------|
| `timeout` | `"30s"`, `"{{timeout}}"` | `simple-download.yaml`, `data-pipeline.yaml` |
| `chunkSize` | `256`, `"{{chunkSize}}"` | `simple-download.yaml`, `data-pipeline.yaml` |
| `maxRetries` | `5`, `"{{maxRetries}}"` | `simple-workflow.yaml`, `data-pipeline.yaml` |
| `batchSize` | `"{{batchSize}}"` | `ecommerce-order-processing.yaml` |
| `validateCertificate` | `true` | `schema-compliant-example.yaml` |
| `executable` | `true` | `schema-compliant-example.yaml` |
| `transactional` | `true` | `ecommerce-order-processing.yaml` |
| `upsert` | `true` | `ecommerce-order-processing.yaml` |
| `query` | SQL string | `financial-reporting.yaml` |
| `format` | `"pdf"` | `financial-reporting.yaml` |
| `template` | `"executive-summary"` | `financial-reporting.yaml` |
| `template_data` | file path | `financial-reporting.yaml` |
| `compliance_type` | `"sox"` | `financial-reporting.yaml` |
| `period` | date range string | `financial-reporting.yaml` |
| `audit_type` | `"financial_report"` | `financial-reporting.yaml` |
| `transformation` | `"reconciliation"` | `financial-reporting.yaml` |
| `recursive` | `true` | `financial-reporting.yaml` |
| `compress` | `true` | `financial-reporting.yaml` |
| `subject` | email subject string | `financial-reporting.yaml` |

These options are carried through to the protocol adapter. Their interpretation (if any) depends entirely on the adapter implementation. The parser and workflow engine do not validate or act on specific option keys.

**Example — transfer with options from `simple-download.yaml`:**

```yaml
transfers:
  - name: download-binary-data
    source: "{{baseUrl}}/bytes/1024"
    destination: "{{outputDir}}/sample-data.bin"
    protocol: http
    options:
      timeout: "{{timeout}}"
      chunkSize: 256
```

**Example — transfer with condition from `simple-workflow.yaml`:**

```yaml
transfers:
  - name: download-json-data
    source: "{{baseUrl}}/json"
    destination: "{{outputDir}}/sample-data.json"
    protocol: http
    condition: "success(download-base-files)"
```

---

## Variable Resolution

Variables use `{{variableName}}` syntax (double curly braces). The `VariableResolver` resolves variables in the following precedence order (highest to lowest):

1. **Context variables** — group-scoped variables (from `transferGroups[].variables`)
2. **Global variables** — workflow-scoped variables (from `spec.variables`)
3. **Environment variables** — `System.getenv(variableName)`
4. **System properties** — `System.getProperty(variableName)`

If a variable is not found in any of these sources, `VariableResolver` throws `VariableResolutionException`.

### Where Variables Are Resolved

The resolver processes these fields:

- `transfers[].source`
- `transfers[].destination`
- `transfers[].condition`
- `transfers[].options` (string values only — non-string values are passed through)
- `transferGroups[].condition`

Variables in `metadata` fields, group `name`, and transfer `name` are **not** resolved.

### Group-Scoped Variables

Groups can declare their own variables that override global variables within that group's scope:

```yaml
# From data-pipeline.yaml:
spec:
  variables:
    timeout: "120s"

  transferGroups:
    - name: download-configuration
      variables:
        configTimeout: "60s"      # only visible to transfers in this group
      transfers:
        - name: download-config
          source: "{{configUrl}}"
          destination: "{{inputDir}}/config.json"
          protocol: http
          options:
            timeout: "{{configTimeout}}"   # resolves to "60s"
```

### Variable Nesting

Variables can reference other variables. Resolution happens at the point of use:

```yaml
# From financial-reporting.yaml:
spec:
  variables:
    reportMonth: "{{current_month}}"    # expects current_month from env or runtime context
    reportYear: "{{current_year}}"

  transferGroups:
    - name: extract-financial-data
      transfers:
        - name: extract-general-ledger
          source: "{{financialDb}}/general_ledger"
          destination: "{{reportPath}}/raw/general-ledger-{{reportMonth}}.csv"
          protocol: database
```

---

## Condition Strings

Both transfer groups and individual transfers support a `condition` field. The parser reads and variable-resolves the condition string, but the current workflow engine **does not evaluate conditions**. The resolved string is carried through execution unchanged.

Condition patterns used in real YAML files:

| Pattern | Example | Source File |
|---------|---------|-------------|
| `success(group-or-transfer-name)` | `"success(download-base-files)"` | `simple-workflow.yaml` |
| `file_exists('path')` | `"file_exists('{{dataPath}}/pending-payments.json')"` | `ecommerce-order-processing.yaml` |

These are conventions in the YAML files, not evaluated expressions. If condition evaluation is needed, it would require implementing a condition evaluator in the workflow engine.

---

## Complete Real-World Example

This is `simple-download.yaml` from `quorus-integration-examples`, reproduced in full:

```yaml
metadata:
  name: "simple-download-workflow"
  version: "1.0.0"
  description: "Simple workflow for downloading files from HTTP sources with basic error handling"
  type: "download-workflow"
  author: "development@quorus.dev"
  created: "2025-08-21"
  tags: ["examples", "download", "http", "simple"]

spec:
  variables:
    baseUrl: "https://httpbin.org"
    outputDir: "/tmp/downloads"
    timeout: "30s"

  execution:
    dryRun: false
    virtualRun: false
    parallelism: 1
    timeout: 300s
    strategy: sequential

  transferGroups:
    - name: download-files
      description: Download test files from HTTP source
      continueOnError: false
      retryCount: 3
      transfers:
        - name: download-json-data
          source: "{{baseUrl}}/json"
          destination: "{{outputDir}}/sample-data.json"
          protocol: http
          options:
            timeout: "{{timeout}}"

        - name: download-xml-data
          source: "{{baseUrl}}/xml"
          destination: "{{outputDir}}/sample-data.xml"
          protocol: http
          options:
            timeout: "{{timeout}}"

        - name: download-binary-data
          source: "{{baseUrl}}/bytes/1024"
          destination: "{{outputDir}}/sample-data.bin"
          protocol: http
          options:
            timeout: "{{timeout}}"
            chunkSize: 256
```

---

## What Not to Use

Do not use the following YAML sections unless you implement matching parser and runtime support first:

- `notifications`
- `cleanup`
- `sla`
- `credentials` blocks with OAuth flows
- `conditions` collections at spec level
- `TenantConfiguration`
- `RoleBasedAccessControl`
- advanced pipe operators and large built-in function catalogs

Those constructs appeared in historical docs but are not part of the current parser contract.

---

## Validation Workflow

Recommended validation order:

1. Parse YAML with `YamlWorkflowDefinitionParser`
2. Run schema validation via `validateSchema(yamlContent)`
3. Run semantic validation via `validate(definition)`
4. Resolve variables with `VariableResolver`
5. Build the dependency graph via `buildDependencyGraph(definitions)`
6. Execute in `DRY_RUN` or `VIRTUAL_RUN` before normal execution

---

## Available Example Workflows

| File | Description | Groups | Key Features |
|------|-------------|--------|--------------|
| `simple-download.yaml` | HTTP file downloads | 1 | Basic options, variable substitution |
| `simple-workflow.yaml` | Dependency chains | 3 | `dependsOn`, group variables, conditions, `maxRetries` |
| `data-pipeline.yaml` | Multi-stage ETL | 5+ | Parallel strategy, group-scoped variables, `continueOnError` |
| `schema-compliant-example.yaml` | Database config setup | 4 | HTTPS with `validateCertificate`, `condition` with `file_exists` |
| `ecommerce-order-processing.yaml` | Order pipeline | 6 | Complex dependencies, multiple protocols, `transactional` option |
| `financial-reporting.yaml` | Monthly reporting | 6 | Nested variable references, compliance options, multi-format output |

These files are in `quorus-integration-examples/src/main/resources/workflows/` and `quorus-workflow/src/test/resources/`.

---

## Source of Truth

- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/YamlWorkflowDefinitionParser.java`
- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/VariableResolver.java`
- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/WorkflowDefinition.java`
- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/TransferGroup.java`
- `quorus-integration-examples/src/main/resources/workflows/`
- `quorus-workflow/src/test/resources/simple-workflow.yaml`
