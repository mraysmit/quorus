<img src="quorus-logo.png" alt="Quorus" width="120"/>

# Quorus YAML Syntax Guide

**Version:** 2.4  
**Date:** 2026-10-03  
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

The parser also reads a legacy form where `variables`, `execution`, and `transferGroups` appear at the root instead of inside `spec`, but schema validation rejects a document without `spec`. Always use the explicit `spec` block.

---

## Minimal Workflow

The parser accepts much less than validation does, and the workflow engine validates every definition before it runs it. The smallest workflow that passes both validation paths is:

```yaml
metadata:
  name: "minimal-workflow"
  version: "1.0.0"
  description: "Download one file over HTTPS"
  type: "download-workflow"
  author: "ops@example.com"
  created: "2026-10-03"
  tags: ["example"]

spec:
  execution:
    strategy: sequential

  transferGroups:
    - name: download
      transfers:
        - name: fetch-file
          source: "https://example.com/file.csv"
          destination: "/data/file.csv"
          protocol: https
```

---

## Validation Requirements

There are two validation paths, and a workflow should pass both:

- **Engine validation**, `YamlWorkflowDefinitionParser.validate(definition)`. `SimpleWorkflowEngine` runs it before every execution, dry run and virtual run, and fails the execution on the first error.
- **Schema validation**, `YamlWorkflowDefinitionParser.validateSchema(yamlContent)`, which works on the raw YAML. The validation CLI and examples use it.

Both require all seven metadata fields below; schema validation also requires `spec` and `spec.execution`.

| Field | Rule | Checked by |
|-------|------|------------|
| `metadata.name` | 2 to 100 characters: letters, digits, `-` and `_`, starting and ending with a letter or digit. No spaces. | Both |
| `metadata.version` | Semantic version, such as `1.0.0` or `2.1.0-beta` | Both |
| `metadata.description` | 10 to 500 characters | Both |
| `metadata.type` | Lower case, at most 50 characters, letters, digits and `-`. A type outside the recommended list is a warning, not an error: `transfer-workflow`, `data-pipeline-workflow`, `download-workflow`, `validation-test-workflow`, `external-data-config`, `etl-workflow`, `backup-workflow`, `sync-workflow` | Both |
| `metadata.author` | An email address, or a name of letters and spaces only, at most 100 characters | Both |
| `metadata.created` | A quoted `YYYY-MM-DD` date. Quote it: YAML reads an unquoted date as a timestamp, which fails this check. A future date is a warning | Both |
| `metadata.tags` | 1 to 20 unique tags; each lower case, 2 to 30 characters, letters, digits and `-` | Both |
| `spec` | Present | Schema |
| `spec.execution` | Present (it may contain only defaults) | Schema |
| `spec.execution.strategy` | `sequential` or `parallel` | Engine |
| `spec.transferGroups` | Group names unique | Engine |
| `spec.transferGroups` | An empty or missing list is a warning | Both |

Validation does not check option keys, protocol names, or `retryCount` bounds. The JSON schema file in `quorus-workflow/src/main/resources/schema/` is not loaded by any code, so its stricter rules are not enforced.

---

## `apiVersion`

| Property | Detail |
|----------|--------|
| Required | No |
| Default | `"v1"` |
| Parser line | `getStringValue(data, "apiVersion", "v1")` |

---

## `metadata`

All metadata fields are parsed as strings. The parser itself requires only `name`, and fills the defaults below for the rest, but validation requires `name`, `version`, `description`, `type`, `author`, `created` and `tags` (see [Validation Requirements](#validation-requirements)). A parser default does not satisfy validation.

| Field | Required for validation | Parser default | Description |
|-------|----------|---------|-------------|
| `name` | **Yes** | — | Workflow identifier. Parser throws `WorkflowParseException` if empty. |
| `version` | **Yes** | `"1.0.0"` | Semantic version string. |
| `description` | **Yes** | `null` | Free-text description. |
| `type` | **Yes** | `"workflow"` | Workflow type label (e.g., `"download-workflow"`, `"etl-workflow"`, `"data-pipeline-workflow"`). |
| `author` | **Yes** | `null` | Author identifier. |
| `created` | **Yes** | `null` | Creation date as a quoted string (e.g., `"2025-08-21"`). |
| `tags` | **Yes** | `[]` | List of string tags for categorization. |
| `labels` | No | `{}` | Key-value map of string labels. |

**Example from `financial-reporting.yaml`:**

```yaml
metadata:
  name: "monthly-financial-reporting-pipeline"
  version: "2.0.1"
  description: "Automated monthly financial report generation with regulatory compliance checks and multi-format output"
  type: "etl-workflow"
  author: "finance-automation@company.com"
  created: "2025-08-21"
  tags: ["finance", "reporting", "compliance", "monthly", "automated", "regulatory"]
  labels:
    environment: "production"
    team: "finance"
    schedule: "monthly"
    compliance: "sox-required"
    retention: "7-years"
```

---

## `spec`

The spec block contains three sections:

| Section | Required | Description |
|---------|----------|-------------|
| `variables` | No | Global variables available to all transfer groups. |
| `execution` | **Yes** for schema validation (its fields all have defaults) | Execution configuration (parallelism, timeout, strategy). |
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

A variable's value is inserted as it is written: references inside a variable's value are not resolved. See [Variable Nesting](#variable-nesting).

---

## `spec.execution`

Controls how the workflow engine runs transfer groups.

| Field | Required | Default | Type | Description |
|-------|----------|---------|------|-------------|
| `dryRun` | No | `false` | boolean | When `true`, every run of this workflow is a dry run: it is validated and planned, and no transfer starts, even when it is started as a normal execution. Wins over `virtualRun`. |
| `virtualRun` | No | `false` | boolean | When `true`, every run of this workflow is a virtual run: each transfer is simulated (about 100 ms each, in parallel within a group), and no transfer starts. |
| `parallelism` | No | `1` | integer | Maximum concurrent transfer groups. Minimum is 1 (enforced by `Math.max(1, parallelism)`). |
| `timeout` | No | `"3600s"` (1 hour) | duration | Overall workflow timeout. When it expires, running transfers are stopped and the run fails. |
| `strategy` | No | `"sequential"` | string | `"sequential"` or `"parallel"`; any other value fails validation. It is recorded with the workflow but does not change scheduling: groups run in dependency order, up to `parallelism` at a time, and the transfers of a group always run in parallel. |

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
| `retryCount` | No | `0` | integer | How many more times each failed transfer of the group is run before it counts as failed. A negative value is treated as 0; there is no upper bound. Each run is a full transfer, with the transfer engine's own retries inside it. |
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

**Example — multi-stage financial reporting pipeline from `financial-reporting.yaml`:**

```yaml
transferGroups:
  - name: extract-financial-data
    description: Extract monthly financial data from various sources
    continueOnError: false
    retryCount: 3
    transfers: [...]

  - name: transform-and-validate
    dependsOn:
      - extract-financial-data
    continueOnError: false
    retryCount: 2
    transfers: [...]

  - name: compliance-validation
    dependsOn:
      - transform-and-validate
    continueOnError: false
    retryCount: 2
    transfers: [...]

  - name: generate-reports
    dependsOn:
      - compliance-validation
    continueOnError: false
    retryCount: 1
    transfers: [...]

  - name: audit-and-archive
    dependsOn:
      - generate-reports
    continueOnError: true
    retryCount: 1
    transfers: [...]

  - name: distribute-reports
    dependsOn:
      - audit-and-archive
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
| `options` | No | `{}` | map | Arbitrary key-value options map. String values are variable-resolved. **Options currently have no effect**: they are not passed to the transfer engine or protocol adapter (see [Options Map](#options-map)). |
| `condition` | No | `null` | string | Condition expression. Parsed and variable-resolved but not evaluated by the current engine. |

### Protocol Values Used in Real YAML

The following protocol values appear in the example workflows:

| Value | Description | Source Example |
|-------|-------------|----------------|
| `http` | HTTP protocol adapter | `simple-download.yaml` |
| `https` | HTTPS protocol adapter | `financial-reporting.yaml` |
| `database` | Used in example YAML but not backed by a registered `ProtocolFactory` adapter | `financial-reporting.yaml` |
| `file` | Used in example YAML but not backed by a registered `ProtocolFactory` adapter | `financial-reporting.yaml` |
| `email` | Used in example YAML but not backed by a registered `ProtocolFactory` adapter | `financial-reporting.yaml` |

The actually registered protocol adapters in `ProtocolFactory.registerDefaultProtocols()` are: `http`, `https`, `ftp`, `ftps`, `sftp`, `smb`, `cifs`, `nfs`. Any protocol value not registered will fail at transfer execution time, not at parse time.

### Options Map

The `options` map is freeform — the parser does not validate option keys. String values go through variable resolution, so an option that names an undefined variable still fails the run. After that, the options are dropped: `TransferGroup.toTransferRequest()` builds the transfer request from `source`, `destination` and `protocol` only. Setting `timeout`, `chunkSize`, `maxRetries` or any other option therefore changes nothing. Whether options will be passed through is open decision `DR-Q1` in the [Outstanding Work Register](../docs-design/task/QUORUS_OUTSTANDING_WORK_REGISTER.md).

These are the option keys used in the real YAML files:

| Option Key | Example Value | Appears In |
|------------|---------------|------------|
| `timeout` | `"30s"`, `"{{timeout}}"` | `simple-download.yaml`, `data-pipeline.yaml` |
| `chunkSize` | `256`, `"{{chunkSize}}"` | `simple-download.yaml`, `data-pipeline.yaml` |
| `maxRetries` | `5`, `"{{maxRetries}}"` | `simple-workflow.yaml`, `data-pipeline.yaml` |
| `batchSize` | `"{{batchSize}}"` | `ecommerce-order-processing.yaml` |
| `validateCertificate` | `true` | `schema-compliant-example.yaml` |
| `executable` | `true` | `schema-compliant-example.yaml` |
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

None of these keys has any effect today. Treat them as documentation of intent in the example files.

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

Variables use `{{variableName}}` syntax (double curly braces). When the workflow engine runs a workflow, `VariableResolver` resolves variables in the following precedence order (highest to lowest):

1. **Group variables** — from `transferGroups[].variables`
2. **Workflow variables** — from `spec.variables`
3. **Runtime variables** — the `ExecutionContext` variables the caller passes to the engine
4. **Environment variables** — `System.getenv(variableName)`
5. **System properties** — `System.getProperty(variableName)`

Because workflow variables rank above runtime variables, a caller cannot override a value that the YAML declares in `spec.variables`. To make a value overridable at run time, leave it out of `spec.variables` and supply it through the execution context.

If a variable is not found in any of these sources, `VariableResolver` throws `VariableResolutionException`.

### Where Variables Are Resolved

The resolver processes these fields:

- `transfers[].source`
- `transfers[].destination`
- `transfers[].condition`
- `transfers[].options` (string values only; options have no effect after resolution)
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

Resolution is a single pass. A reference in a field is replaced by the variable's value exactly as written, and any `{{...}}` inside that value is left as literal text, with no error. For example, `financial-reporting.yaml` declares:

```yaml
spec:
  variables:
    reportMonth: "{{current_month}}"
```

A destination of `"{{reportPath}}/raw/general-ledger-{{reportMonth}}.csv"` therefore resolves to a path containing the literal text `{{current_month}}`, even if `current_month` is set in the environment. Supply the final value directly, for example through the execution context or the environment, and reference that variable in the field. Whether nested references will be resolved is open decision `DR-Q1`.

---

## Condition Strings

Both transfer groups and individual transfers support a `condition` field. The parser reads and variable-resolves the condition string, but the current workflow engine **does not evaluate conditions**. The resolved string is carried through execution unchanged.

Condition patterns used in real YAML files:

| Pattern | Example | Source File |
|---------|---------|-------------|
| `success(group-or-transfer-name)` | `"success(download-base-files)"` | `simple-workflow.yaml` |
| `file_exists('path')` | `"file_exists('{{configDir}}/postgresql.conf')"` | `schema-compliant-example.yaml`, `data-pipeline.yaml`, `ecommerce-order-processing.yaml` |

These are conventions in the YAML files, not evaluated expressions: a guarded transfer runs whether or not its condition would hold. Whether conditions will be evaluated or rejected is open item `ENG-14` in the [Outstanding Work Register](../docs-design/task/QUORUS_OUTSTANDING_WORK_REGISTER.md).

---

## Complete Real-World Example

This is `simple-download.yaml` from `quorus-integration-examples`, with its comments omitted. It passes both validation paths:

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
3. Run engine validation via `validate(definition)`; the engine also runs it before every execution
4. Resolve variables with `VariableResolver`
5. Build the dependency graph via `buildDependencyGraph(definitions)`
6. Execute in `DRY_RUN` or `VIRTUAL_RUN` before normal execution

The examples module's `WorkflowValidationCLI` runs schema validation from the command line; see the [integration examples guide](QUORUS_INTEGRATION_EXAMPLES_README.md) for how to run it.

---

## Available Example Workflows

| File | Description | Groups | Key Features |
|------|-------------|--------|--------------|
| `simple-download.yaml` | HTTP file downloads | 1 | Basic options, variable substitution |
| `simple-workflow.yaml` | Dependency chains | 3 | `dependsOn`, group variables, conditions, `maxRetries` |
| `data-pipeline.yaml` | Multi-stage ETL | 5+ | Parallel strategy, group-scoped variables, `continueOnError` |
| `schema-compliant-example.yaml` | Database config setup | 4 | HTTPS with `validateCertificate`, `condition` with `file_exists` |
| `ecommerce-order-processing.yaml` | Order processing pipeline | 5+ | `batchSize` option from a variable, `file_exists` conditions |
| `financial-reporting.yaml` | Monthly reporting | 6 | Nested variable references (left literal; see [Variable Nesting](#variable-nesting)), compliance options, non-adapter protocols |

These files are in `quorus-integration-examples/src/main/resources/workflows/` and `quorus-workflow/src/test/resources/`.

---

## Source of Truth

- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/YamlWorkflowDefinitionParser.java`
- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/VariableResolver.java`
- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/WorkflowDefinition.java`
- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/TransferGroup.java`
- `quorus-integration-examples/src/main/resources/workflows/`
- `quorus-workflow/src/test/resources/simple-workflow.yaml`
