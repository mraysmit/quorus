<img src="quorus-logo.png" alt="Quorus" width="120"/>

# Quorus YAML Syntax Guide

**Version:** 2.6  
**Date:** 2026-10-18  
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
| `transfers[].options` | Only `checksum` and `expectedSize`, each with a valid literal value; see [Options Map](#options-map) | Engine |
| `condition` (group or transfer) | In the condition grammar, naming only upstream groups and their transfers; see [Conditions](#conditions) | Engine |

Validation does not check protocol names or `retryCount` bounds. The JSON schema file in `quorus-workflow/src/main/resources/schema/` is not loaded by any code, so its stricter rules are not enforced.

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
    reportDir: "{{outputDir}}/reports"
```

A reference inside a variable's value is resolved too, so `reportDir` above becomes `/tmp/downloads/reports`. See [Variable Nesting](#variable-nesting).

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
| `condition` | No | `null` | string | The group runs only if this holds when its dependencies have finished; otherwise it is skipped. See [Conditions](#conditions). |
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
| `options` | No | `{}` | map | `checksum` and `expectedSize` only; any other key fails validation. String values are variable-resolved. See [Options Map](#options-map). |
| `condition` | No | `null` | string | The transfer starts only if this holds when its group starts; otherwise it is skipped. See [Conditions](#conditions). |

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

A transfer accepts two options. Each one sets a field of the transfer request that the transfer engine or every protocol adapter acts on, so no option is accepted and then ignored:

| Option | Value | Effect |
|--------|-------|--------|
| `checksum` | The expected SHA-256 of the file, as 64 hexadecimal digits in either case | Every adapter computes the SHA-256 of the transferred file and fails the transfer if it differs. The value is passed in lower case, which is the form the adapters compute |
| `expectedSize` | The file's size in bytes, a whole number of zero or more, as a number or a quoted string | The engine reports it as the transfer's total size, so progress has a known end. It is not compared with the bytes transferred |

Any other key fails validation with an error naming the key, for example `spec.transferGroups[0].transfers[1].options.timeout`, and no transfer of that workflow starts. Options that other tools or older versions of these examples used, such as `timeout`, `chunkSize`, `maxRetries` or `validateCertificate`, are refused for that reason: nothing would act on them.

A literal value is checked at validation. A value that contains a variable reference is resolved first and checked when the transfer request is built. If it is then invalid, that transfer fails before it starts, and the transfer engine is never called for it.

**Example — transfer with options from `simple-download.yaml`:**

```yaml
transfers:
  - name: download-binary-data
    source: "{{baseUrl}}/bytes/1024"
    destination: "{{outputDir}}/sample-data.bin"
    protocol: http
    options:
      expectedSize: 1024
```

**Example — a checksum from a variable:**

```yaml
spec:
  variables:
    ledgerSha256: "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
  transferGroups:
    - name: fetch-ledger
      transfers:
        - name: download-ledger
          source: "https://files.example.com/ledger.csv"
          destination: "/data/in/ledger.csv"
          protocol: https
          options:
            checksum: "{{ledgerSha256}}"
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
- `transfers[].options` (string values only)
- `transferGroups[].condition`

Variables in `metadata` fields, group `name`, and transfer `name` are **not** resolved.

### Group-Scoped Variables

Groups can declare their own variables that override global variables within that group's scope:

```yaml
# From data-pipeline.yaml:
spec:
  variables:
    sourceUrl: "https://httpbin.org"

  transferGroups:
    - name: download-raw-data
      variables:
        dataSize: "4096"          # only visible to transfers in this group
      transfers:
        - name: download-dataset-a
          source: "{{sourceUrl}}/bytes/{{dataSize}}"
          destination: "{{inputDir}}/dataset-a.bin"
          protocol: http
          options:
            expectedSize: "{{dataSize}}"   # resolves to 4096
```

### Variable Nesting

A reference inside a variable's value is resolved in turn, from the same sources and in the same precedence order. For example, `financial-reporting.yaml` declares:

```yaml
spec:
  variables:
    reportPath: "/reports/monthly"
    reportMonth: "{{current_month}}"
```

A destination of `"{{reportPath}}/raw/general-ledger-{{reportMonth}}.csv"` resolves `reportMonth`, then `current_month`. `current_month` is not declared in the file, so the execution context or the environment must supply it; if nothing does, the run fails with `Variable not found: current_month`.

Nesting is limited to 10 levels: a reference inside a value inside a value, and so on, ten times. Resolution fails with an error naming the chain when:

- the nesting is deeper than 10 levels; or
- the references form a cycle, for example `a: "{{b}}"` and `b: "{{a}}"`, reported as `Variable references form a cycle: a -> b -> a`.

Using the same variable more than once in one field is not a cycle.

---

## Conditions

A transfer group and a transfer may each have a `condition`. A group's condition is evaluated when its dependencies have finished, just before it would start. A transfer's condition is evaluated when its group starts. If the condition holds, the group or transfer runs; if not, it is **skipped**.

### Grammar

| Form | True when |
|------|-----------|
| `success(name)` | The group or transfer `name` ran and succeeded |
| `failure(name)` | The group or transfer `name` ran and failed |
| `file_exists('path')` | The path exists on the filesystem of the machine running the workflow engine. Double quotes also work: `file_exists("path")` |
| `not X` | `X` is false |
| `X and Y` | Both hold |
| `X or Y` | Either holds |
| `( X )` | Grouping |

`not` binds tightest, then `and`, then `or`, so `not a and b or c` means `((not a) and b) or c`. Keywords are lower case, and spaces are free. A name is letters, digits, `-`, `_` and `.`. Both `success` and `failure` are false for something that was skipped.

A variable reference is allowed only inside a `file_exists` path, which is resolved before the condition is evaluated. A relative path is relative to the engine's working directory. Names must be literal, so that validation can check them.

### What a condition may name

Engine validation refuses a condition that is outside the grammar, and one that names anything except:

- a group that this group depends on, directly or through other groups; or
- a transfer of such a group.

A transfer of the **same** group cannot be named, because the transfers of a group run in parallel: when the condition is evaluated, the other transfer has not finished. To make one transfer wait for another, put it in a later group that depends on the first. A name must also be unambiguous: a name used by both a group and a transfer, or by transfers in two groups, cannot be named.

`failure(x)` can be true only if `x`'s group sets `continueOnError: true`. Without it, the workflow stops when that group fails, and no later group runs. Validation gives a warning for such a condition.

### Skipping

- A skipped group's status is `SKIPPED` and none of its transfers run. A skipped transfer has no result and is listed in its group's `skippedTransfers`.
- A skip is not a failure: the workflow can still complete, and the groups that depend on a skipped group still run, subject to their own conditions.
- A condition that cannot be evaluated at run time fails its group or transfer, which does not start. This happens when a resolved `file_exists` path contains the quote that encloses it, or is not a valid path.
- A virtual run evaluates conditions against its simulated outcomes, in which every transfer succeeds. A dry run does not evaluate conditions: it reports every transfer as succeeded.

**Examples from the example workflows:**

```yaml
# ecommerce-order-processing.yaml: a transfer that runs only if an upstream transfer succeeded
- name: reserve-inventory
  condition: "success(verify-payment-status)"   # verify-payment-status is in process-payments, a dependency

# data-pipeline.yaml: a transfer that runs only if a file is present
- name: validate-dataset-a
  condition: "file_exists('{{inputDir}}/config.json')"
```

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
| `simple-download.yaml` | HTTP file downloads | 1 | The `expectedSize` option, variable substitution |
| `simple-workflow.yaml` | Dependency chains | 3 | `dependsOn`, group variables, conditions, `expectedSize` from a variable |
| `data-pipeline.yaml` | Multi-stage ETL | 5+ | Parallel strategy, group-scoped variables, `continueOnError`, `expectedSize` |
| `schema-compliant-example.yaml` | Database config setup | 4 | HTTPS downloads, `condition` with `file_exists` |
| `ecommerce-order-processing.yaml` | Order processing pipeline | 7 | `success` and `file_exists` conditions, a group split so that one transfer waits for another, non-adapter protocols |
| `financial-reporting.yaml` | Monthly reporting | 8 | Nested variable references that the execution context must complete (see [Variable Nesting](#variable-nesting)), `success` conditions on upstream transfers, non-adapter protocols |

These files are in `quorus-integration-examples/src/main/resources/workflows/` and `quorus-workflow/src/test/resources/`.

---

## Source of Truth

- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/YamlWorkflowDefinitionParser.java`
- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/VariableResolver.java`
- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/WorkflowDefinition.java`
- `quorus-workflow/src/main/java/dev/mars/quorus/workflow/TransferGroup.java`
- `quorus-integration-examples/src/main/resources/workflows/`
- `quorus-workflow/src/test/resources/simple-workflow.yaml`
