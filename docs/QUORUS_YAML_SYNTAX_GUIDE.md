<img src="quorus-logo.png" alt="Quorus Logo" width="200"/>

# Quorus YAML Syntax Guide

**Version:** 1.1  
**Date:** 2026-02-11  
**Author:** Mark Andrew Ray-Smith Cityline Ltd  

This guide teaches you to write Quorus YAML workflow files, starting from the simplest possible example and progressively introducing features as you need them. Part I is a hands-on tutorial. Part II is a comprehensive reference for every field and option.

---

## Table of Contents

### Part I — Tutorial

1. [Introduction](#introduction)
2. [Your First Workflow](#your-first-workflow)
3. [Understanding the Structure](#understanding-the-structure)
4. [Step 2: Adding Variables](#step-2-adding-variables)
5. [Step 3: Multiple Transfers in a Group](#step-3-multiple-transfers-in-a-group)
6. [Step 4: Multi-Stage Pipelines with Dependencies](#step-4-multi-stage-pipelines-with-dependencies)
7. [Step 5: Controlling Execution Strategy](#step-5-controlling-execution-strategy)
8. [Step 6: Error Handling and Retries](#step-6-error-handling-and-retries)
9. [Step 7: Conditional Execution](#step-7-conditional-execution)
10. [Step 8: Notifications](#step-8-notifications)
11. [Step 9: Routes — The Simpler Alternative](#step-9-routes--the-simpler-alternative)
12. [What to Learn Next](#what-to-learn-next)

### Part II — Reference

13. [Document Types](#document-types)
14. [TransferWorkflow Reference](#transferworkflow-reference)
    - [Top-Level Structure](#top-level-structure)
    - [Metadata](#metadata)
    - [Spec](#spec)
    - [Variables](#variables)
    - [Execution](#execution)
    - [Conditions](#conditions)
    - [Transfer Groups](#transfer-groups)
    - [Transfers](#transfers)
    - [Transfer Options](#transfer-options)
    - [Credentials](#credentials)
    - [Integrity Verification](#integrity-verification)
    - [Retry Policy](#retry-policy)
    - [Progress Tracking](#progress-tracking)
    - [Notifications Reference](#notifications-reference)
    - [Cleanup](#cleanup)
    - [SLA](#sla)
15. [RouteConfiguration Reference](#routeconfiguration-reference)
    - [Route Structure](#route-structure)
    - [Source and Destination](#source-and-destination)
    - [Trigger Types](#trigger-types)
16. [TenantConfiguration](#tenantconfiguration)
17. [RoleBasedAccessControl](#rolebasedaccesscontrol)
18. [Variable Substitution Reference](#variable-substitution-reference)
    - [Basic Variables](#basic-variables)
    - [Environment Variables](#environment-variables)
    - [Variable Resolution Hierarchy](#variable-resolution-hierarchy)
    - [Built-in Date/Time Functions](#built-in-datetime-functions)
    - [System Functions](#system-functions)
    - [Utility and Generation Functions](#utility-and-generation-functions)
    - [String Manipulation Functions](#string-manipulation-functions)
    - [Mathematical and Logical Functions](#mathematical-and-logical-functions)
    - [Conditional and Control Flow Functions](#conditional-and-control-flow-functions)
    - [Data Structure Functions](#data-structure-functions)
    - [File and Path Functions](#file-and-path-functions)
    - [Pipe Operators](#pipe-operators)
    - [Variable Validation](#variable-validation)
19. [Dependency Management](#dependency-management)
20. [Error Handling Patterns](#error-handling-patterns)
21. [Properties Configuration](#properties-configuration)
22. [Appendix A — Keyword Reference](#appendix-a--keyword-reference)

---

# Part I — Tutorial

## Introduction

Quorus uses YAML files to describe file transfer operations declaratively. Instead of writing code to download, upload, copy, and move files, you write a YAML document that says *what* you want transferred and Quorus handles the *how*.

A Quorus YAML file answers three questions:

1. **What is this?** — the `metadata` section identifies and describes the workflow
2. **What should happen?** — the `spec` section defines the transfers, their order, and how to handle problems
3. **Where do things come from and go to?** — each transfer has a `source` and `destination`

There are two main types of YAML document you will write:

| Type | Use When |
|------|----------|
| **Workflow** | You need multi-step operations, dependencies between steps, variables, or conditional logic |
| **Route** | You need a simple, permanent "whenever X happens, copy files from A to B" rule |

Most users start with workflows. Routes are covered in [Step 9](#step-9-routes--the-simpler-alternative).

---

## Your First Workflow

Here is the smallest valid Quorus workflow. It downloads a single file:

```yaml
metadata:
  name: "my-first-workflow"
  version: "1.0.0"
  description: "Download a single file from an API endpoint"
  type: "download-workflow"
  author: "you@company.com"
  created: "2026-02-11"
  tags: ["tutorial", "simple"]

spec:
  transferGroups:
    - name: download
      transfers:
        - name: get-report
          source: "https://api.example.com/reports/daily.csv"
          destination: "/data/reports/daily.csv"
          protocol: https
```

That is a complete, working workflow. Let's break down what each piece does.

---

## Understanding the Structure

Every workflow has two top-level sections:

```yaml
metadata:      # WHO: identifies the workflow
  name: "..."
  version: "..."
  ...

spec:          # WHAT: defines the work to be done
  transferGroups:
    - ...
```

**Metadata** is the identity card — name, version, description, author, and tags. All seven fields are required:

| Field | What It Is | Example |
|-------|-----------|---------|
| `name` | Unique identifier (2–100 chars, no spaces; use kebab-case) | `"daily-sales-report"` |
| `version` | Semantic version | `"1.0.0"` |
| `description` | Human-readable explanation (10–500 chars) | `"Downloads daily sales data"` |
| `type` | A classification label | `"download-workflow"` |
| `author` | Who created it | `"ops@company.com"` |
| `created` | Creation date (ISO format) | `"2026-02-11"` |
| `tags` | Searchable labels | `["sales", "daily"]` |

**Spec** contains one or more **transfer groups**, each containing one or more **transfers**. Think of it as:

```
Workflow
  └── Transfer Group (a named stage)
        └── Transfer (a single file operation: source → destination)
```

Each transfer needs four things: a `name`, a `source` URI, a `destination` path, and a `protocol` (`https`, `http`, `ftp`, `sftp`, `smb`, or `file`).

---

## Step 2: Adding Variables

Hard-coding URLs and paths makes workflows fragile. Variables let you define values once and reuse them everywhere using `{{variableName}}` syntax:

```yaml
metadata:
  name: "daily-sales-report"
  version: "1.0.0"
  description: "Download daily sales data with variables for easy reconfiguration"
  type: "download-workflow"
  author: "ops@company.com"
  created: "2026-02-11"
  tags: ["sales", "daily", "variables"]

spec:
  variables:
    apiBase: "https://api.example.com"
    reportDir: "/data/reports"
    today: "{{today}}"                    # Built-in: resolves to current date

  transferGroups:
    - name: download-report
      transfers:
        - name: get-sales-data
          source: "{{apiBase}}/reports/sales/{{today}}.csv"
          destination: "{{reportDir}}/sales-{{today}}.csv"
          protocol: https
```

**What changed:** We added `spec.variables` and replaced the hard-coded URL with `{{apiBase}}` and `{{today}}`. Now you can change the API host in one place instead of hunting through every transfer.

Quorus provides many built-in variables. The most common:

| Variable | Resolves To |
|----------|------------|
| `{{today}}` | Current date (e.g., `2026-02-11`) |
| `{{yesterday}}` | Previous date |
| `{{timestamp}}` | Full ISO 8601 timestamp |
| `{{env.MY_VAR}}` | Value of environment variable `MY_VAR` |
| `{{env.MY_VAR \| default('fallback')}}` | Environment variable with a default |

The full list of 60+ functions is in [Variable Substitution Reference](#variable-substitution-reference).

---

## Step 3: Multiple Transfers in a Group

A transfer group can contain multiple transfers. Transfers within the same group run in the order listed (unless parallelism is enabled):

```yaml
spec:
  variables:
    apiBase: "https://api.example.com"
    reportDir: "/data/reports"
    today: "{{today}}"

  transferGroups:
    - name: download-reports
      transfers:
        - name: get-sales
          source: "{{apiBase}}/reports/sales/{{today}}.csv"
          destination: "{{reportDir}}/sales-{{today}}.csv"
          protocol: https

        - name: get-customers
          source: "{{apiBase}}/reports/customers/{{today}}.csv"
          destination: "{{reportDir}}/customers-{{today}}.csv"
          protocol: https

        - name: get-inventory
          source: "{{apiBase}}/reports/inventory/{{today}}.csv"
          destination: "{{reportDir}}/inventory-{{today}}.csv"
          protocol: https
```

**What changed:** Three transfers in one group. They share the same variables and execute as a single stage.

---

## Step 4: Multi-Stage Pipelines with Dependencies

Real-world workflows have stages that must run in order: download raw data, then process it, then load the results. Use multiple transfer groups with `dependsOn` to create a pipeline:

```yaml
metadata:
  name: "etl-data-pipeline"
  version: "1.0.0"
  description: "Extract, transform, and load customer data into the warehouse"
  type: "etl-workflow"
  author: "data-team@company.com"
  created: "2026-02-11"
  tags: ["etl", "pipeline", "dependencies"]

spec:
  variables:
    sourceApi: "https://erp.company.com/api"
    staging: "/staging/finance"
    warehouse: "/warehouse/finance"
    date: "{{today}}"

  transferGroups:
    # Stage 1: Extract — runs first, no dependencies
    - name: extract
      description: "Download raw data from ERP"
      transfers:
        - name: extract-customers
          source: "{{sourceApi}}/customers?date={{date}}"
          destination: "{{staging}}/raw-customers-{{date}}.json"
          protocol: https
        - name: extract-orders
          source: "{{sourceApi}}/orders?date={{date}}"
          destination: "{{staging}}/raw-orders-{{date}}.json"
          protocol: https

    # Stage 2: Transform — waits for extract to complete
    - name: transform
      description: "Clean and validate the extracted data"
      dependsOn: [extract]
      transfers:
        - name: clean-customers
          source: "{{staging}}/raw-customers-{{date}}.json"
          destination: "{{staging}}/clean-customers-{{date}}.json"
          protocol: file
        - name: clean-orders
          source: "{{staging}}/raw-orders-{{date}}.json"
          destination: "{{staging}}/clean-orders-{{date}}.json"
          protocol: file

    # Stage 3: Load — waits for transform to complete
    - name: load
      description: "Load processed data into the warehouse"
      dependsOn: [transform]
      transfers:
        - name: load-customers
          source: "{{staging}}/clean-customers-{{date}}.json"
          destination: "{{warehouse}}/customers/{{date}}.json"
          protocol: file
        - name: load-orders
          source: "{{staging}}/clean-orders-{{date}}.json"
          destination: "{{warehouse}}/orders/{{date}}.json"
          protocol: file
```

**What changed:** Three transfer groups form an `extract → transform → load` pipeline. The `dependsOn` field ensures each stage waits for its predecessor. Groups without dependencies (or with all dependencies satisfied) can run in parallel.

**Dependency rules:**
- `dependsOn` takes a list of group names: `dependsOn: [group-a, group-b]`
- A group waits for *all* listed dependencies before starting
- Groups with no dependencies start immediately
- Circular dependencies are detected and rejected

---

## Step 5: Controlling Execution Strategy

By default, Quorus runs groups sequentially. The `execution` section lets you control parallelism and timeouts:

```yaml
spec:
  execution:
    strategy: mixed        # Let Quorus optimize: parallel where safe, sequential where needed
    parallelism: 3         # Up to 3 transfers running at the same time
    timeout: 3600          # Abort the entire workflow after 1 hour (seconds)

  transferGroups:
    # ... same as before
```

| Strategy | Behaviour |
|----------|-----------|
| `sequential` | One group at a time, in order |
| `parallel` | All independent groups run simultaneously |
| `mixed` | Quorus optimizes automatically (recommended) |
| `adaptive` | Dynamically adjusts based on system load |

For most workflows, `mixed` with a sensible `parallelism` limit is the best choice.

---

## Step 6: Error Handling and Retries

Networks fail. Servers go down. Quorus provides retries and error handling at every level:

```yaml
spec:
  execution:
    strategy: mixed
    parallelism: 3
    timeout: 3600

  transferGroups:
    # Critical data — stop everything if this fails
    - name: extract-financial-data
      continueOnError: false             # Default: halt on failure
      transfers:
        - name: get-transactions
          source: "sftp://bank.com/transactions.csv"
          destination: "/finance/transactions.csv"
          protocol: sftp
          options:
            timeout: 600                 # Per-transfer timeout (seconds)
          retryPolicy:
            maxAttempts: 5               # Retry up to 5 times
          integrity:
            validateOnCompletion: true   # Verify file integrity

    # Nice-to-have data — continue even if this fails
    - name: download-optional-reports
      dependsOn: [extract-financial-data]
      continueOnError: true              # Keep going if some transfers fail
      retryCount: 3                      # Group-level retry count
      transfers:
        - name: get-marketing-data
          source: "https://analytics.com/export.csv"
          destination: "/data/marketing.csv"
          protocol: https
```

**Key error handling fields:**

| Field | Level | Purpose |
|-------|-------|---------|
| `continueOnError` | Group | `false` = halt pipeline on failure; `true` = skip and continue |
| `retryCount` | Group | How many times to retry the entire group |
| `retryPolicy.maxAttempts` | Transfer | How many times to retry this specific transfer |
| `options.timeout` | Transfer | Seconds before giving up on this transfer |
| `integrity.validateOnCompletion` | Transfer | Verify the file wasn't corrupted during transfer |

For advanced retry configuration (backoff strategies, jitter, error-type filtering), see [Retry Policy](#retry-policy) in Part II.

---

## Step 7: Conditional Execution

Some stages should only run in certain situations — month-end processing, production-only steps, or business-hours checks. Define named conditions and reference them in groups:

```yaml
spec:
  variables:
    environment: "production"

  conditions:
    - name: is-production
      expression: "{{environment}} == 'production'"
    - name: is-month-end
      expression: "{{dayOfMonth}} >= 28"

  transferGroups:
    - name: daily-extract
      condition: "is-production"
      transfers:
        - name: get-daily-data
          source: "https://erp.company.com/daily-export.csv"
          destination: "/data/daily.csv"
          protocol: https

    - name: month-end-reports
      dependsOn: [daily-extract]
      condition: "is-month-end && is-production"
      transfers:
        - name: get-monthly-summary
          source: "https://erp.company.com/monthly-summary.csv"
          destination: "/reports/monthly-summary.csv"
          protocol: https
```

**What changed:** The `conditions` section defines reusable boolean expressions. Groups reference them by name in the `condition` field. Combine conditions with `&&` (AND) and `||` (OR).

Built-in variables useful for conditions: `{{dayOfWeek}}` (1=Monday), `{{dayOfMonth}}`, `{{hour}}`, `{{isBusinessDay(today)}}`, `{{isQuarterEnd(today)}}`.

---

## Step 8: Notifications

Get notified when workflows succeed or fail:

```yaml
spec:
  variables:
    date: "{{today}}"

  transferGroups:
    - name: daily-sync
      transfers:
        - name: sync-data
          source: "https://api.company.com/export.csv"
          destination: "/data/export.csv"
          protocol: https

  notifications:
    onSuccess:
      - type: email
        recipients: ["team@company.com"]
        subject: "Daily sync completed - {{date}}"

    onFailure:
      - type: email
        recipients: ["team@company.com", "manager@company.com"]
        subject: "URGENT: Daily sync FAILED - {{date}}"
        priority: "high"
      - type: symphony
        channel: "#data-ops"
        message: "Daily sync failed for {{date}}"
```

Quorus supports three notification types: `email`, `symphony`, and `webhook`. See [Notifications Reference](#notifications-reference) in Part II for full details.

---

## Step 9: Routes — The Simpler Alternative

If you just need "whenever a new CSV appears in this folder, copy it to that server", you don't need a workflow. A **route** is simpler:

```yaml
apiVersion: v1
kind: RouteConfiguration
metadata:
  name: csv-backup

spec:
  source:
    agent: agent-nyc-01
    location: /data/incoming/

  destination:
    agent: agent-backup-01
    location: /backups/incoming/

  trigger:
    type: EVENT_BASED
    events: [FILE_CREATED]
    filters:
      pattern: "*.csv"
```

This route watches for new CSV files on one agent and automatically copies them to another. No variables, no stages, no dependencies — just a persistent transfer rule.

**Routes vs. Workflows — when to use which:**

| Scenario | Use |
|----------|-----|
| Copy files when they appear | Route |
| Transfer on a schedule (cron) | Route |
| Simple A-to-B replication | Route |
| Multi-step ETL pipeline | Workflow |
| Conditional business logic | Workflow |
| Fan-out to multiple destinations | Workflow |
| Dependencies between stages | Workflow |
| Combine routes with custom transfers | Workflow (can trigger routes) |

Routes support seven trigger types: `EVENT_BASED`, `TIME_BASED`, `INTERVAL_BASED`, `BATCH_BASED`, `SIZE_BASED`, `MANUAL`, and `COMPOSITE`. See [RouteConfiguration Reference](#routeconfiguration-reference) in Part II.

---

## What to Learn Next

You now know enough to write most Quorus workflows. For specific needs, jump into Part II:

| Need | Section |
|------|---------|
| Full list of every metadata, transfer, and option field | [TransferWorkflow Reference](#transferworkflow-reference) |
| Protocol-specific options (HTTP headers, SFTP keys, FTP auth) | [Transfer Options](#transfer-options) |
| OAuth2, key-based auth, vault secrets | [Credentials](#credentials) |
| Checksum validation and corruption recovery | [Integrity Verification](#integrity-verification) |
| All 7 route trigger types with examples | [Trigger Types](#trigger-types) |
| 60+ built-in variable functions | [Variable Substitution Reference](#variable-substitution-reference) |
| Multi-tenant configuration | [TenantConfiguration](#tenantconfiguration) |
| Role-based access control | [RoleBasedAccessControl](#rolebasedaccesscontrol) |
| Runtime properties and environment variables | [Properties Configuration](#properties-configuration) |

---

# Part II — Reference

## Document Types

Quorus uses four YAML document types, identified by the `kind` field:

| Kind | Purpose | apiVersion |
|------|---------|------------|
| `TransferWorkflow` | Multi-step file transfer orchestration | `v1` |
| `RouteConfiguration` | Agent-to-agent transfer routing with triggers | `v1` |
| `TenantConfiguration` | Multi-tenant resource management | `quorus.dev/v1` |
| `RoleBasedAccessControl` | Role and permission management | `quorus.dev/v1` |

---

## TransferWorkflow Reference

### Top-Level Structure

```yaml
apiVersion: v1
kind: TransferWorkflow
metadata:
  # Document metadata (required)
spec:
  # Workflow specification (required)
```

> **Note:** The `apiVersion` and `kind` fields are optional — the parser also accepts workflows that begin directly with `metadata:`.

### Metadata

Required fields for every workflow:

```yaml
metadata:
  name: "daily-financial-sync"           # Required: 2-100 characters, no spaces
  version: "2.1.0"                       # Required: semantic version (X.Y.Z)
  description: "Synchronizes financial data across regions"  # Required: 10-500 characters
  type: "financial-sync-workflow"        # Required: workflow classification
  author: "ops@company.com"             # Required: email or name
  created: "2026-02-11"                 # Required: ISO date YYYY-MM-DD
  tags: ["daily", "financial", "sync"]  # Required: lowercase, hyphen-separated
```

Optional metadata fields:

```yaml
metadata:
  # ... required fields ...
  tenant: acme-corporation               # Organization owner
  namespace: finance-operations          # Isolation within tenant
  labels:                                # Key-value categorization
    environment: production
    criticality: high
    compliance: sox-required
    dataClassification: confidential
  annotations:                           # Extended governance metadata
    owner: "finance-team@corp.com"
    businessOwner: "cfo@corp.com"
    documentation: "https://wiki.corp.com/pipeline"
    approvedBy: "director@corp.com"
    approvalDate: "2026-01-15"
    complianceReviewed: "2026-01-10"
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `name` | string | **Yes** | 2–100 characters, alphanumeric plus hyphens and underscores, no spaces |
| `version` | string | **Yes** | Semantic version `X.Y.Z` |
| `description` | string | **Yes** | 10–500 characters |
| `type` | string | **Yes** | Workflow classification |
| `author` | string | **Yes** | Email address or name |
| `created` | string | **Yes** | ISO date `YYYY-MM-DD` |
| `tags` | string[] | **Yes** | Lowercase, hyphen-separated |
| `tenant` | string | No | Owning tenant identifier |
| `namespace` | string | No | Tenant namespace partition |
| `labels` | map | No | Key-value categorization pairs |
| `annotations` | map | No | Extended governance metadata |

### Spec

The `spec` section contains the workflow configuration:

```yaml
spec:
  variables: { }          # Variable definitions
  variableDefinitions: [] # Variable type validation (optional)
  execution: { }          # Execution strategy configuration
  conditions: []          # Reusable boolean conditions (optional)
  transferGroups: []      # Ordered list of transfer groups (required)
  notifications: { }      # Notification configuration (optional)
  cleanup: { }            # Cleanup configuration (optional)
  sla: { }                # SLA targets (optional)
  monitoring: { }         # Monitoring configuration (optional)
```

### Variables

Define reusable values at workflow scope:

```yaml
spec:
  variables:
    sourceSystem: "https://erp.corp.internal"
    targetWarehouse: "/data-warehouse/finance"
    processingDate: "{{today}}"
    archivePath: "{{targetWarehouse}}/archive/{{processingDate}}"
```

Group-level variables override workflow-level variables:

```yaml
transferGroups:
  - name: extract
    variables:
      fileSize: "1024"
      schemaVersion: "v1.2.0"
    transfers:
      - name: download
        source: "https://schemas.example.com/{{schemaVersion}}/data.sql"
        destination: "/data/schema/{{schemaVersion}}.sql"
```

### Execution

```yaml
spec:
  execution:
    strategy: mixed          # sequential | parallel | mixed | adaptive
    parallelism: 3           # Max concurrent transfers
    timeout: 3600            # Workflow timeout in seconds (or "3600s")
    continueOnError: false   # Halt on first error?

    retryPolicy:             # Workflow-level retry defaults
      maxAttempts: 3
      initialDelay: "1s"     # or integer seconds: 60
      maxDelay: "30s"
      backoffStrategy: exponential  # exponential | fixed
      backoffMultiplier: 2.0
      jitter: true
      retryOn: ["network-error", "timeout", "server-error"]

    resources:               # Resource constraints (optional)
      maxMemoryMB: 2048
      maxCpuCores: 4
      maxNetworkBandwidthMbps: 100

    preferences:             # Agent preferences (optional)
      preferredAgents: ["agent-finance-01", "agent-finance-02"]
      avoidAgents: ["agent-maintenance"]
      requireDedicatedAgents: false
```

| Strategy | Description |
|----------|-------------|
| `sequential` | Run groups one at a time in order |
| `parallel` | Run all independent groups simultaneously |
| `mixed` | Intelligent optimization respecting dependencies (recommended) |
| `adaptive` | Dynamic runtime optimization based on resource monitoring |

Adaptive strategy settings:

```yaml
execution:
  strategy: adaptive
  adaptiveSettings:
    monitorPerformance: true
    adjustParallelism: true
    resourceThresholds:
      cpuUtilization: 80        # percent
      memoryUtilization: 75
      networkUtilization: 70
```

Execution mode flags:

```yaml
execution:
  dryRun: false       # Validate without executing
  virtualRun: false   # Simulate with mock transfers
```

### Conditions

Define reusable boolean conditions that transfer groups can reference:

```yaml
spec:
  conditions:
    - name: is-production
      expression: "{{environment}} == 'production'"
      description: "True when running in production"

    - name: is-business-day
      expression: "{{dayOfWeek}} >= 1 && {{dayOfWeek}} <= 5"
      description: "True on weekdays (Monday–Friday)"

    - name: is-month-end
      expression: "{{dayOfMonth}} >= 28"
      description: "True during the last few days of the month"

    - name: is-quarter-end
      expression: "{{isQuarterEnd(today)}}"

    - name: business-hours
      expression: "{{hour}} >= 6 && {{hour}} <= 22"

    - name: source-data-available
      expression: "{{checkDataAvailability(sourceERP, processingDate)}}"
```

Reference conditions in transfer groups:

```yaml
transferGroups:
  - name: daily-extraction
    condition: "is-business-day && is-production"
    transfers: [...]

  - name: month-end-processing
    condition: "is-month-end && is-production"
    transfers: [...]
```

Conditions support `&&` (AND) and `||` (OR) operators for combining named conditions.

### Transfer Groups

```yaml
transferGroups:
  - name: extract-data                     # Required: unique identifier
    description: "Extract from CRM"        # Optional: purpose description
    dependsOn: []                          # Optional: list of group names
    condition: "is-production"             # Optional: condition reference
    continueOnError: false                 # Optional: skip failures? (default: false)
    retryCount: 3                          # Optional: group-level retry count
    maxParallelTransfers: 2                # Optional: per-group parallelism limit
    variables:                             # Optional: group-scoped variables
      extractionDate: "{{today}}"
    retryPolicy:                           # Optional: group-level retry policy
      maxAttempts: 5
      initialDelay: "500ms"
      backoffStrategy: exponential
      backoffMultiplier: 2.0
    monitoring:                            # Optional: group monitoring
      priority: "high"
      detailedLogging: true
      realTimeAlerts: true
    integrity:                             # Optional: group integrity settings
      strictValidation: true
      corruptionTolerance: "zero"
    transfers: []                          # Required: list of transfers
```

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `name` | string | **Yes** | — | Unique group identifier |
| `description` | string | No | — | Human-readable purpose |
| `dependsOn` | string[] | No | `[]` | Groups that must complete first |
| `condition` | string | No | — | Condition expression or name |
| `continueOnError` | boolean | No | `false` | Continue on transfer failure |
| `retryCount` | integer | No | — | Max retry attempts |
| `maxParallelTransfers` | integer | No | — | Concurrent transfer limit |
| `variables` | map | No | — | Group-scoped variable overrides |
| `transfers` | object[] | **Yes** | — | List of transfer definitions |

### Transfers

```yaml
transfers:
  - name: extract-customers                # Required: transfer identifier
    source: "{{sourceSystem}}/customers"   # Required: source URI
    destination: "{{target}}/customers"    # Required: destination path
    protocol: https                        # Required: https | http | ftp | sftp | smb | file | database
    dependsOn: [extract-config]            # Optional: intra-group dependency
    condition: "success(extract-config)"   # Optional: conditional execution
    options: {}                            # Optional: protocol-specific options
    credentials: {}                        # Optional: authentication
    integrity: {}                          # Optional: checksum verification
    retryPolicy: {}                        # Optional: per-transfer retry
    progressTracking: {}                   # Optional: monitoring config
    monitoring: {}                         # Optional: thresholds and alerts
    metadata:                              # Optional: transfer metadata
      dataType: "customer-master"
      sensitivity: "confidential"
      retention: "2555"                    # days
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | **Yes** | Unique transfer identifier within group |
| `source` | string | **Yes** | Source URI (supports variable substitution) |
| `destination` | string | **Yes** | Destination path or URI |
| `protocol` | string | **Yes** | `https`, `http`, `ftp`, `sftp`, `smb`, `file`, `database` |
| `dependsOn` | string[] | No | Other transfer names within the same group |
| `condition` | string | No | Execution condition (e.g., `"success(other-transfer)"`) |
| `options` | object | No | Protocol-specific options |
| `credentials` | object | No | Authentication configuration |
| `integrity` | object | No | Checksum verification |
| `retryPolicy` | object | No | Per-transfer retry override |

### Transfer Options

Common options applicable to all protocols:

```yaml
options:
  timeout: 600                    # Seconds (default: 300)
  bandwidthMbps: 500              # Bandwidth limit
  compressionCodec: "gzip"        # gzip | lz4 | zstd
```

Per-transfer retry and integrity settings (structured objects, not option keys):

```yaml
retryPolicy:                      # Per-transfer retry override
  maxAttempts: 3
  backoffMultiplier: 2.0
  initialDelay: "60s"

integrity:                        # Checksum verification
  algorithm: "sha256"             # sha256 | sha1 | md5 | crc32
  validateOnCompletion: true
```

HTTP/HTTPS-specific options:

```yaml
options:
  method: "PUT"                          # HTTP method
  headers:
    Authorization: "Bearer {{env.TOKEN}}"
    Content-Type: "application/json"
  tlsVerification: true
  certificatePath: "/etc/ssl/certs/ca.crt"
```

SFTP-specific options:

```yaml
options:
  recursive: true
  preservePermissions: true
  sshKey: "/keys/transfer-key.pem"
```

FTP-specific options:

```yaml
options:
  username: "ftp_user"
  password: "{{env.FTP_PASSWORD}}"
```

Error handling within options:

```yaml
options:
  onError:
    action: "continue"          # fail | continue | retry | log
    logLevel: "WARN"
    notification: "admin@company.com"
    createTicket: true
    ticketPriority: "medium"
```

Data warehouse / transformation options:

```yaml
options:
  outputFormat: "parquet"
  compressionCodec: "snappy"
  partitionBy: ["region", "date"]
  loadStrategy: "upsert"         # append | upsert
  indexing: true
  statisticsUpdate: true
  generateLineage: true
```

### Credentials

```yaml
credentials:
  type: "oauth2"                           # oauth2 | key-based
  tokenEndpoint: "{{sourceERP}}/oauth/token"
  clientId: "quorus-finance-client"
  clientSecret: "{{vault:erp-client-secret}}"
  scope: "transactions:read"
```

```yaml
credentials:
  type: "key-based"
  keyPath: "/etc/quorus/keys/transfer-key"
```

The `{{vault:secret-name}}` pattern references secrets from a vault provider.

### Integrity Verification

Basic integrity checking:

```yaml
integrity:
  enabled: true
  algorithm: "sha256"              # sha256 | sha1 | md5 | crc32
  expectedChecksum: "a665a459..."
  validateOnTransfer: true         # Validate during transfer
  validateOnCompletion: true       # Validate after completion
  failOnMismatch: true
```

Chunk-level validation for large files:

```yaml
integrity:
  algorithm: "sha256"
  chunkValidation:
    enabled: true
    chunkSize: "100MB"
    parallelValidation: true
    resumeOnFailure: true
```

Multiple algorithms:

```yaml
integrity:
  algorithms: ["sha256", "crc32"]
  expectedChecksums:
    sha256: "{{source.metadata.sha256}}"
    crc32: "{{source.metadata.crc32}}"
```

Checksum sources using variable substitution:

| Pattern | Description |
|---------|-------------|
| `{{source.metadata.sha256}}` | Source file metadata |
| `{{response.headers.content-md5}}` | HTTP response header |
| `{{response.headers.etag}}` | HTTP ETag header |
| `{{sidecar.file('data.csv.sha256')}}` | Sidecar checksum file |
| `{{source.xattr.user.checksum.sha1}}` | Extended file attributes |
| `{{api.response.checksum.sha256}}` | API-provided checksum |

Corruption handling:

```yaml
integrity:
  errorHandling:
    onMismatch: "retry"                # retry | fail | quarantine | notify
  recovery:
    maxAttempts: 3
    backoffStrategy: "exponential"
    quarantineLocation: "/quarantine/"
    channels: ["email", "symphony"]
```

Alternative source failover on integrity failures:

```yaml
integrity:
  errorHandling:
    onMismatch: "retry-with-different-source"
    alternativeSources:
      - "https://backup-source.com/data.zip"
      - "ftp://mirror.company.com/data.zip"
    maxAttempts: 5
```

### Retry Policy

Retry policies can be set at workflow, group, or individual transfer level:

```yaml
retryPolicy:
  maxAttempts: 5
  initialDelay: "500ms"        # Duration string or integer seconds
  maxDelay: "2m"
  backoffStrategy: exponential  # exponential | fixed
  backoffMultiplier: 2.0
  jitter: true                  # Randomize delay to prevent thundering herd
  retryOn:                      # Error types that trigger retry
    - "network-error"
    - "timeout"
    - "connection-refused"
    - "server-error"
    - "authentication-error"
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `maxAttempts` | integer | 3 | Maximum retry attempts |
| `initialDelay` | duration | `"1s"` | First retry delay |
| `maxDelay` | duration | `"30s"` | Maximum delay cap |
| `backoffStrategy` | string | `exponential` | `exponential` or `fixed` |
| `backoffMultiplier` | float | 2.0 | Exponential multiplier |
| `jitter` | boolean | `false` | Add randomness to delays |
| `retryOn` | string[] | all errors | Error types triggering retries |

### Progress Tracking

Workflow-level progress configuration:

```yaml
spec:
  progressTracking:
    enabled: true
    reportingInterval: "1s"
    detailedMetrics: true
    predictiveETA: true
    anomalyDetection: true
```

Per-transfer progress metrics:

```yaml
transfers:
  - name: large-upload
    progressTracking:
      enabled: true
      reportingInterval: "500ms"
      metrics:
        - "bytes-transferred"
        - "transfer-rate"
        - "eta"
        - "completion-percentage"
        - "network-latency"
```

Advanced monitoring with alerting:

```yaml
spec:
  monitoring:
    progressTracking:
      rateSmoothing:
        enabled: true
        windowSize: "30s"
        algorithm: "exponential"   # simple | weighted | exponential

    customMetrics:
      - name: "transfer-efficiency"
        formula: "bytes-transferred / elapsed-time"
        unit: "bytes/second"

    alerts:
      - name: "slow-transfer-alert"
        condition: "transfer-rate < 1MB/s AND elapsed-time > 60s"
        action: "notify"            # notify | escalate | investigate
        channels: ["email", "symphony"]
```

### Notifications Reference

```yaml
notifications:
  onSuccess:
    - type: email
      recipients: ["ops@company.com"]
      subject: "Workflow completed - {{processingDate}}"
      template: "workflow-success-template"
      includeMetrics: true
      attachments:
        - "{{stagingArea}}/validation-report.json"
    - type: symphony
      channel: "#data-ops"
      message: "Pipeline completed for {{processingDate}}"
      includeMetrics: true

  onFailure:
    - type: email
      recipients: ["ops@company.com", "manager@company.com"]
      subject: "URGENT: Pipeline failed - {{processingDate}}"
      template: "workflow-failure-template"
      includeErrorDetails: true
      includeLogs: true
      priority: "high"
    - type: symphony
      channel: "#data-ops"
      message: "CRITICAL: Pipeline FAILED for {{processingDate}}"
      mentions: ["@oncall", "@team-lead"]
    - type: webhook
      url: "https://alerts.corp.com/api/incidents"
      method: "POST"
      headers:
        Authorization: "Bearer {{vault:alerting-token}}"
        Content-Type: "application/json"
      payload:
        severity: "high"
        service: "finance-pipeline"
        environment: "{{environment}}"
        description: "Pipeline execution failed"

  onProgress:
    enabled: true
    intervalMinutes: 30
    recipients: ["ops@company.com"]
    minimumDurationMinutes: 60
```

| Notification Type | Required Fields |
|-------------------|----------------|
| `email` | `recipients[]`, `subject` |
| `symphony` | `channel`, `message` |
| `webhook` | `url`, `method` |

### Cleanup

```yaml
cleanup:
  enabled: true
  retainStagingDays: 7
  retainLogsDays: 30
  archiveEnabled: true
  archiveLocation: "/archive/workflows"
  compressionEnabled: true
```

### SLA

```yaml
sla:
  maxExecutionTime: "PT4H"        # ISO 8601 duration
  targetExecutionTime: "PT2H"
  availabilityTarget: 99.5        # Percentage
```

---

## RouteConfiguration Reference

Routes define agent-to-agent transfer paths with automatic triggering.

### Route Structure

```yaml
apiVersion: v1
kind: RouteConfiguration
metadata:
  name: crm-to-warehouse

spec:
  source:
    agent: agent-crm-001
    location: /data/source/

  destination:
    agent: agent-wh-001
    location: /data/dest/

  trigger:
    type: EVENT_BASED
    events:
      - FILE_CREATED
      - FILE_MODIFIED
    filters:
      pattern: "*.csv"
      minSize: "1KB"

  options:
    timeout: 300
  retryPolicy:
    maxAttempts: 3
  integrity:
    validateOnCompletion: true
```

### Source and Destination

```yaml
source:
  agent: agent-crm-001             # Agent identifier
  location: /data/source/          # Directory on the agent

destination:
  agent: agent-wh-001
  location: /data/dest/
```

### Trigger Types

Quorus supports seven trigger types:

#### EVENT_BASED

Triggered by file system events:

```yaml
trigger:
  type: EVENT_BASED
  events:
    - FILE_CREATED
    - FILE_MODIFIED
    - FILE_DELETED
  filters:
    pattern: "*.csv"
    minSize: "1KB"
    maxAge: "1h"
```

#### TIME_BASED

Triggered by cron schedule:

```yaml
trigger:
  type: TIME_BASED
  schedule:
    cron: "0 2 * * *"          # Daily at 2 AM
    timezone: "America/New_York"
```

#### INTERVAL_BASED

Triggered at fixed intervals:

```yaml
trigger:
  type: INTERVAL_BASED
  interval:
    period: 30
    unit: MINUTES              # SECONDS | MINUTES | HOURS
```

#### BATCH_BASED

Triggered when files accumulate to a threshold:

```yaml
trigger:
  type: BATCH_BASED
  batch:
    fileCount: 10              # Trigger after 10 files
    maxWaitTime: 5M            # Or after 5 minutes, whichever first
```

#### SIZE_BASED

Triggered when accumulated file size reaches a threshold:

```yaml
trigger:
  type: SIZE_BASED
  size:
    threshold: "100MB"
    maxWaitTime: "1h"
```

#### MANUAL

No automatic trigger — explicitly triggered via API or workflow:

```yaml
trigger:
  type: MANUAL
```

#### COMPOSITE

Combines multiple trigger conditions:

```yaml
trigger:
  type: COMPOSITE
  logic: OR                    # OR | AND
  conditions:
    - type: TIME_BASED
      schedule:
        cron: "0 * * * *"
    - type: SIZE_BASED
      size:
        threshold: "500MB"
```

### Route Lifecycle States

```
CONFIGURED → VALIDATING → ACTIVE → TRIGGERED → TRANSFERRING → ACTIVE
                                                             ↓
                                              DEGRADED / SUSPENDED / FAILED
```

| State | Description |
|-------|-------------|
| `CONFIGURED` | Route definition loaded |
| `VALIDATING` | Verifying source/destination agents are reachable |
| `ACTIVE` | Ready and waiting for trigger |
| `TRIGGERED` | Trigger condition met, preparing transfer |
| `TRANSFERRING` | Transfer in progress |
| `DEGRADED` | Partially functional (e.g., agent unreachable) |
| `SUSPENDED` | Manually paused |
| `FAILED` | Unrecoverable error |

---

## TenantConfiguration

```yaml
apiVersion: quorus.dev/v1
kind: TenantConfiguration
metadata:
  name: acme-corp
  parent: enterprise-tenant

spec:
  displayName: "ACME Corporation"
  description: "Primary corporate tenant"

  resources:
    quotas:
      maxConcurrentTransfers: 50
      maxStorageGB: 1000
      maxBandwidthMbps: 100
    limits:
      maxFileSize: "10GB"
      maxTransferDuration: "24h"

  security:
    authentication:
      provider: "active-directory"
      domain: "corp.acme.com"
    authorization:
      defaultRole: "user"
      adminRole: "admin"

  storage:
    isolation: "logical"           # logical | physical | hybrid
    basePath: "/corporate-storage/acme-corp"
    encryption: true

  namespaces:
    - name: finance
      quotas:
        maxConcurrentTransfers: 20
        maxStorageGB: 500
    - name: hr
      quotas:
        maxConcurrentTransfers: 10
        maxStorageGB: 200
```

Tenant defaults for variable resolution:

```yaml
tenantDefaults:
  acme-corp:
    defaultTimeout: 1800
    maxAttempts: 5
    notificationEmail: "ops@acme-corp.com"
    dataRetentionDays: 2555
    encryptionRequired: true
```

---

## RoleBasedAccessControl

```yaml
apiVersion: quorus.dev/v1
kind: RoleBasedAccessControl

roles:
  - name: admin
    permissions:
      - "transfers:*"
      - "workflows:*"
      - "tenants:*"
      - "cluster:*"
  - name: tenant-admin
    permissions:
      - "tenants:*:own"
      - "transfers:*:tenant"
      - "workflows:*:tenant"
  - name: operator
    permissions:
      - "transfers:read"
      - "transfers:create"
      - "workflows:read"
      - "workflows:execute"
  - name: user
    permissions:
      - "transfers:read:own"
      - "transfers:create:own"
      - "workflows:execute:own"

bindings:
  - role: admin
    subjects:
      - "group:quorus-admins"
  - role: operator
    subjects:
      - "group:transfer-operators"
  - role: user
    subjects:
      - "group:all-users"
```

Permission format: `resource:action:scope` where scope is `own`, `tenant`, or omitted for global.

---

## Variable Substitution Reference

### Basic Variables

```yaml
spec:
  variables:
    baseUrl: "https://api.example.com"
    targetDir: "/storage/daily-imports"
    date: "{{today}}"

  transferGroups:
    - name: import
      transfers:
        - name: download
          source: "{{baseUrl}}/data.csv"
          destination: "{{targetDir}}/{{date}}.csv"
```

### Environment Variables

```yaml
variables:
  apiKey: "{{env.API_KEY}}"
  dbHost: "{{env.DB_HOST | default('localhost')}}"
  port: "{{env.PORT | default(8080) | toNumber}}"
```

### Variable Resolution Hierarchy

Variables resolve in this priority order (highest first):

1. **Runtime context** — CLI `--variable` flags or API `runtimeVariables`
2. **Group variables** — `transferGroups[].variables`
3. **Workflow variables** — `spec.variables`
4. **Tenant defaults** — Tenant-configured default values
5. **System defaults** — Global system defaults
6. **Built-in functions** — `{{today}}`, `{{timestamp}}`, etc.

### Built-in Date/Time Functions

```yaml
variables:
  today: "{{today}}"                           # 2026-02-11
  yesterday: "{{yesterday}}"                   # 2026-02-10
  tomorrow: "{{tomorrow}}"                     # 2026-02-12
  todayFormatted: "{{format(today, 'yyyy-MM-dd')}}"
  monthYear: "{{format(today, 'yyyy-MM')}}"
  yearOnly: "{{format(today, 'yyyy')}}"
  lastWeek: "{{addDays(today, -7)}}"
  nextMonth: "{{addMonths(today, 1)}}"
  quarterStart: "{{startOfQuarter(today)}}"
  monthEnd: "{{endOfMonth(today)}}"
  currentHour: "{{hour}}"
  dayOfWeek: "{{dayOfWeek}}"                   # 1=Monday, 7=Sunday
  dayOfMonth: "{{dayOfMonth}}"
  dayOfYear: "{{dayOfYear}}"
  weekOfYear: "{{weekOfYear}}"
  timestamp: "{{timestamp}}"                   # ISO 8601 timestamp
  timestampMillis: "{{timestampMillis}}"
  isBusinessDay: "{{isBusinessDay(today)}}"
  nextBusinessDay: "{{nextBusinessDay(today)}}"
  fiscalYear: "{{fiscalYear(today)}}"
  fiscalQuarter: "{{fiscalQuarter(today)}}"
  isQuarterEnd: "{{isQuarterEnd(today)}}"
  isYearEnd: "{{isYearEnd(today)}}"
```

### System Functions

```yaml
variables:
  hostname: "{{hostname}}"
  ipAddress: "{{ipAddress}}"
  region: "{{region}}"
  tenant: "{{tenant}}"
  namespace: "{{namespace}}"
  workflowName: "{{workflowName}}"
  executionId: "{{executionId}}"
  executedBy: "{{executedBy}}"
  executionMode: "{{executionMode}}"           # normal | dry-run | virtual-run
  environment: "{{environment}}"
  isProduction: "{{environment == 'production'}}"
```

### Utility and Generation Functions

```yaml
variables:
  uuid: "{{uuid}}"
  shortUuid: "{{shortUuid}}"
  correlationId: "{{correlationId}}"
  randomNumber: "{{random(1000)}}"
  randomString: "{{randomString(8)}}"
  base64Encode: "{{base64Encode('hello world')}}"
  base64Decode: "{{base64Decode('aGVsbG8gd29ybGQ=')}}"
  urlEncode: "{{urlEncode('hello world')}}"
  md5Hash: "{{md5('hello world')}}"
  sha256Hash: "{{sha256('hello world')}}"
```

### String Manipulation Functions

```yaml
variables:
  upperCase: "{{upper(department)}}"
  lowerCase: "{{lower(department)}}"
  titleCase: "{{title(department)}}"
  camelCase: "{{camel(department_name)}}"
  substring: "{{substring(sourceFile, 0, 10)}}"
  length: "{{length(sourceFile)}}"
  trim: "{{trim(inputString)}}"
  replace: "{{replace(sourceFile, '.csv', '.json')}}"
  replaceRegex: "{{replaceRegex(sourceFile, '[0-9]+', 'XXX')}}"
  split: "{{split(csvList, ',')}}"
  join: "{{join(arrayVar, '|')}}"
  contains: "{{contains(sourceFile, 'customer')}}"
  startsWith: "{{startsWith(sourceFile, 'data')}}"
  endsWith: "{{endsWith(sourceFile, '.csv')}}"
  matches: "{{matches(sourceFile, '[a-z]+\\.csv')}}"
```

### Mathematical and Logical Functions

```yaml
variables:
  sum: "{{add(batchSize, bufferSize)}}"
  difference: "{{subtract(totalRecords, processedRecords)}}"
  product: "{{multiply(batchSize, parallelism)}}"
  quotient: "{{divide(totalSize, chunkSize)}}"
  remainder: "{{mod(totalRecords, batchSize)}}"
  absolute: "{{abs(difference)}}"
  minimum: "{{min(timeout1, timeout2)}}"
  maximum: "{{max(retries1, retries2)}}"
  round: "{{round(averageSize)}}"
  and: "{{and(isProduction, isBusinessDay)}}"
  or: "{{or(isUrgent, isHighPriority)}}"
  not: "{{not(isTestMode)}}"
  equals: "{{eq(environment, 'production')}}"
  greaterThan: "{{gt(fileSize, maxSize)}}"
  lessThan: "{{lt(retryCount, maxRetries)}}"
```

### Conditional and Control Flow Functions

```yaml
variables:
  databaseUrl: "{{if(isProduction, 'prod-db.corp.com', 'test-db.corp.com')}}"
  logLevel: "{{switch(environment,
              'production', 'WARN',
              'staging', 'INFO',
              'development', 'DEBUG',
              'INFO')}}"
  timeout: "{{coalesce(customTimeout, defaultTimeout, 300)}}"
  batchSize: "{{default(configuredBatchSize, 1000)}}"
  enableAudit: "{{if(and(isProduction, isFinancialData), true, false)}}"
  processingMode: "{{if(isUrgent, 'fast',
                       if(isLargeDataset, 'batch', 'standard'))}}"
```

### Data Structure Functions

```yaml
variables:
  arrayLength: "{{size(fileList)}}"
  firstItem: "{{first(fileList)}}"
  lastItem: "{{last(fileList)}}"
  sortedArray: "{{sort(fileList)}}"
  uniqueArray: "{{unique(fileList)}}"
  mapKeys: "{{keys(configMap)}}"
  hasKey: "{{hasKey(configMap, 'timeout')}}"
  getValue: "{{get(configMap, 'timeout', 300)}}"
```

### File and Path Functions

```yaml
variables:
  fileName: "{{basename(sourceFile)}}"
  directory: "{{dirname(sourceFile)}}"
  extension: "{{extension(sourceFile)}}"
  nameWithoutExt: "{{nameWithoutExtension(sourceFile)}}"
  fullPath: "{{joinPath(baseDir, subDir, fileName)}}"
  relativePath: "{{relativePath(basePath, fullPath)}}"
  fileExists: "{{fileExists(sourceFile)}}"
  fileSize: "{{fileSize(sourceFile)}}"
  lastModified: "{{lastModified(sourceFile)}}"
```

### Pipe Operators

Variables support pipe operators for transformation and validation:

```yaml
variables:
  # Default values
  source: "{{env.PRIMARY_SOURCE | default('https://default-api.com')}}"

  # Type conversion
  port: "{{env.PORT | default(8080) | toNumber}}"
  enabled: "{{env.FEATURE_FLAG | default('true') | toBoolean}}"

  # Fallback chains
  apiUrl: "{{env.PRIMARY | fallback(env.BACKUP) | fallback('https://fallback.com')}}"

  # Required with error message
  token: "{{env.AUTH_TOKEN | required('AUTH_TOKEN must be set')}}"

  # Validation
  maxRetries: "{{env.MAX_RETRIES | toNumber | validate('must be 1-100', gte(1), lte(100))}}"

  # Try with fallback on error
  featureFlag: "{{try(env.FEATURE | toBoolean, false)}}"
```

### Variable Validation

Define type constraints for variables:

```yaml
spec:
  variableDefinitions:
    - name: "environment"
      type: "string"
      required: true
      pattern: "^(production|staging|development)$"
      description: "Deployment environment"

    - name: "maxConcurrentTransfers"
      type: "number"
      required: true
      minimum: 1
      maximum: 100

    - name: "enableRetries"
      type: "boolean"
      required: false
      default: true

    - name: "allowedProtocols"
      type: "array"
      required: true
      items:
        type: "string"
        enum: ["http", "https", "ftp", "sftp", "smb"]
```

Variable error handling strategy:

```yaml
spec:
  errorHandling:
    onVariableError: "fail-fast"   # fail-fast | use-default | skip-transfer
    logLevel: "ERROR"
    notifyOnError: true
```

**Important:** Circular variable references are detected and prevented. Variables must form a directed acyclic graph:

```yaml
# GOOD: Linear dependency chain
variables:
  baseUrl: "https://api.company.com"
  apiVersion: "v2"
  fullApiUrl: "{{baseUrl}}/{{apiVersion}}"
  dataEndpoint: "{{fullApiUrl}}/data"

# BAD: Circular reference (will be rejected)
# varA: "{{varB}}/path"
# varB: "{{varA}}/other"
```

---

## Dependency Management

### Explicit Dependencies

Groups execute after all named dependencies complete:

```yaml
transferGroups:
  - name: extract
    transfers: [...]

  - name: transform
    dependsOn: [extract]
    transfers: [...]

  - name: load
    dependsOn: [transform]
    transfers: [...]
```

### Fan-Out / Fan-In

Multiple groups can depend on the same group, and a group can wait for multiple:

```yaml
transferGroups:
  - name: extract-customers
    transfers: [...]

  - name: extract-orders
    transfers: [...]

  - name: merge-and-report
    dependsOn: [extract-customers, extract-orders]   # Fan-in: waits for both
    transfers: [...]
```

### Intra-Group Dependencies

Individual transfers within a group can depend on other transfers in the same group:

```yaml
transfers:
  - name: download-config
    source: "{{configUrl}}/settings.json"
    destination: "/config/settings.json"

  - name: download-data
    source: "{{dataUrl}}/export.csv"
    destination: "/data/export.csv"
    dependsOn: [download-config]           # Waits for config download
    condition: "success(download-config)"  # Only if config succeeded
```

### Conditional Dependencies

Combine `dependsOn` with `condition` for conditional execution:

```yaml
conditions:
  - name: month-end
    expression: "{{dayOfMonth}} >= 28"

transferGroups:
  - name: daily-processing
    transfers: [...]

  - name: monthly-aggregation
    dependsOn: [daily-processing]
    condition: month-end                   # Only runs at month-end
    transfers: [...]
```

### Resource Dependencies

Prevent resource conflicts between groups:

```yaml
transferGroups:
  - name: bandwidth-heavy-1
    transfers: [...]

  - name: bandwidth-heavy-2
    resourceDependencies:
      - type: bandwidth
        constraint: "total_bandwidth < 1000"
        conflictsWith: [bandwidth-heavy-1]
    transfers: [...]
```

---

## Error Handling Patterns

### Fail-Fast (Critical Data)

```yaml
transferGroups:
  - name: critical-data
    continueOnError: false
    transfers:
      - name: financial-data
        retryPolicy:
          maxAttempts: 5
        options:
          onError:
            action: fail
            notification: "finance-team@corp.com"
```

### Continue on Error (Non-Critical Data)

```yaml
transferGroups:
  - name: optional-data
    continueOnError: true
    transfers:
      - name: marketing-data
        options:
          onError:
            action: log
            notification: "marketing@corp.com"
```

### Quarantine on Integrity Failure

```yaml
integrity:
  errorHandling:
    onMismatch: "quarantine"
    quarantineLocation: "/quarantine/suspicious/"
    notifySecurityTeam: true
    generateIncident: true
```

---

## Properties Configuration

Quorus uses Java properties files for runtime configuration. These are not YAML but control system behavior that workflows interact with.

### Configuration Resolution Order

1. Environment variable: `QUORUS_HTTP_PORT=8080`
2. System property: `-Dquorus.http.port=8080`
3. Properties file: `quorus.http.port=8080`
4. Default value

### Key Properties by Module

**Core** (`quorus.properties`):

| Property | Default | Description |
|----------|---------|-------------|
| `quorus.transfer.max.concurrent` | 10 | Max concurrent transfers |
| `quorus.transfer.max.retries` | 3 | Default retry attempts |
| `quorus.transfer.retry.delay.ms` | 1000 | Retry delay (ms) |
| `quorus.transfer.buffer.size` | 8192 | Buffer size (bytes) |
| `quorus.network.connection.timeout.ms` | 30000 | Connection timeout (ms) |
| `quorus.network.read.timeout.ms` | 60000 | Read timeout (ms) |
| `quorus.file.max.size` | 10737418240 | Max file size (bytes, 10GB) |
| `quorus.file.checksum.algorithm` | SHA-256 | Default checksum algorithm |

**Controller** (`quorus-controller.properties`):

| Property | Default | Description |
|----------|---------|-------------|
| `quorus.node.id` | — | Unique node identifier |
| `quorus.http.port` | 8080 | HTTP API port |
| `quorus.raft.port` | 9080 | Raft consensus port |
| `quorus.cluster.nodes` | — | Format: `nodeId=host:port,...` |
| `quorus.telemetry.otlp.endpoint` | — | OTLP collector endpoint |
| `quorus.telemetry.prometheus.port` | 9464 | Prometheus metrics port |

**Agent** (`quorus-agent.properties`):

| Property | Default | Description |
|----------|---------|-------------|
| `quorus.agent.id` | — | Agent identifier |
| `quorus.agent.controller.url` | — | Controller API base URL |
| `quorus.agent.region` | — | Geographic region |
| `quorus.agent.datacenter` | — | Datacenter identifier |
| `quorus.agent.transfers.max-concurrent` | 5 | Max concurrent transfers |
| `quorus.agent.protocols` | — | Supported protocols |
| `quorus.agent.heartbeat.interval-ms` | 30000 | Heartbeat interval (ms) |
| `quorus.agent.jobs.polling.interval-ms` | 10000 | Job polling interval (ms) |

### Environment Variable Mapping

| Property | Environment Variable |
|----------|---------------------|
| `quorus.http.port` | `QUORUS_HTTP_PORT` |
| `quorus.node.id` | `QUORUS_NODE_ID` |
| `quorus.agent.id` | `QUORUS_AGENT_ID` or `AGENT_ID` |
| `quorus.cluster.nodes` | `QUORUS_CLUSTER_NODES` |
| `quorus.telemetry.enabled` | `QUORUS_TELEMETRY_ENABLED` |

---

## Appendix A — Keyword Reference

Complete alphabetical list of every YAML keyword used in this guide.
Keywords are categorised by their implementation status in the codebase:

- **Implemented** — Parsed by `YamlWorkflowDefinitionParser`, validated by `WorkflowSchemaValidator`, or mapped to a model field.
- **Planned** — Described in this guide but not yet handled by any parser, validator, or model class. The `options` map is parsed as opaque `Map<String, Object>` so option keys pass through but are not individually consumed by protocol adapters.

### Implemented Keywords (29)

These keywords are actively parsed, validated, or mapped to Java model fields.

---

#### `apiVersion`
- **Context:** Root
- **Type:** string
- **Default:** `"v1"`
- **Description:** Schema version identifier for the workflow document.
- **Valid values:** Must match pattern `v{N}` where N is an integer (e.g., `v1`, `v2`).

#### `author`
- **Context:** Metadata
- **Type:** string
- **Required:** Yes
- **Description:** Person or team responsible for the workflow.
- **Valid values:** Email address (e.g., `ops@company.com`) or plain name (e.g., `Data Engineering Team`). Max 100 characters.

#### `condition`
- **Context:** TransferGroup, Transfer
- **Type:** string
- **Default:** _(none — group/transfer always executes)_
- **Description:** Boolean expression evaluated at runtime to determine whether the group or transfer executes. Supports `{{variable}}` substitution.
- **Valid values:** Any string expression that resolves to a truthy/falsy value. Examples: `"{{env}} == 'production'"`, `"{{enableBackup}}"`.

#### `continueOnError`
- **Context:** TransferGroup
- **Type:** boolean
- **Default:** `false`
- **Description:** Controls whether remaining transfers in a group continue after one fails.
- **Valid values:**
  - `true` — skip the failed transfer and continue with the next transfer in the group.
  - `false` — abort the group immediately on the first transfer failure.

#### `created`
- **Context:** Metadata
- **Type:** string
- **Required:** Yes
- **Description:** Date the workflow definition was created.
- **Valid values:** ISO 8601 date in `YYYY-MM-DD` format (e.g., `"2026-02-11"`). Validated as a real calendar date.

#### `dependsOn`
- **Context:** TransferGroup
- **Type:** string[]
- **Default:** `[]` _(no dependencies)_
- **Description:** List of transfer-group `name` values that must complete successfully before this group starts. Drives the `DependencyGraph` execution order.
- **Valid values:** Array of strings, each matching a `name` of another transfer group in the same workflow. Example: `["extract-group", "validate-group"]`.

#### `description`
- **Context:** Metadata, TransferGroup
- **Type:** string
- **Required:** Yes (in Metadata)
- **Description:** Human-readable summary of the workflow or transfer group.
- **Valid values:** Free-text string. In Metadata context: minimum 10, maximum 500 characters.

#### `destination`
- **Context:** Transfer
- **Type:** string
- **Required:** Yes
- **Description:** Target path or URL where the transferred file is written.
- **Valid values:** Absolute file path or URL appropriate for the selected `protocol`. Supports `{{variable}}` substitution. Examples: `"/data/output/report.csv"`, `"sftp://backup.example.com/archive/"`.

#### `dryRun`
- **Context:** Execution
- **Type:** boolean
- **Default:** `false`
- **Description:** When enabled, the workflow validates structure, resolves variables, and evaluates conditions, but skips all actual file transfers.
- **Valid values:**
  - `true` — validate only, no transfers executed. Useful for testing workflow definitions.
  - `false` — normal execution.

#### `execution`
- **Context:** Spec
- **Type:** object
- **Description:** Container for execution settings. If omitted, defaults are applied for all child fields.
- **Child keywords:** `parallelism`, `strategy`, `timeout`, `dryRun`, `virtualRun`.

#### `labels`
- **Context:** Metadata
- **Type:** map (string → string)
- **Default:** `{}` _(empty map)_
- **Description:** Arbitrary key-value pairs for categorising, filtering, or annotating workflows.
- **Valid values:** Any string keys and string values. Example: `{ environment: "production", team: "data-engineering" }`.

#### `metadata`
- **Context:** Root
- **Type:** object
- **Required:** Yes
- **Description:** Container for workflow identity and descriptive fields.
- **Child keywords:** `name`, `version`, `description`, `type`, `author`, `created`, `tags`, `labels`.

#### `name`
- **Context:** Metadata, TransferGroup, Transfer
- **Type:** string
- **Required:** Yes (at all levels)
- **Description:** Unique identifier for the workflow, transfer group, or individual transfer.
- **Valid values:** Must match `^[a-zA-Z0-9][a-zA-Z0-9\-_]*[a-zA-Z0-9]$` — starts and ends with alphanumeric, may contain hyphens and underscores. Length 2–100 characters. Examples: `"daily-report-sync"`, `"extract_phase_1"`.

#### `options`
- **Context:** Transfer
- **Type:** map (string → any)
- **Default:** `{}` _(empty map)_
- **Description:** Protocol-specific key-value settings passed through to the transfer adapter. Values support `{{variable}}` substitution.
- **Valid values:** Any keys and values. Interpretation depends on the `protocol` adapter. Example: `{ headers: { Authorization: "Bearer {{token}}" }, compressionCodec: "gzip" }`.

> **Note:** The parser reads and variable-resolves the `options` map, but individual option keys are **not** currently consumed by protocol adapters. The `toTransferRequest()` conversion does not yet forward option entries to `TransferRequest`.

#### `parallelism`
- **Context:** Execution
- **Type:** integer
- **Default:** `1`
- **Description:** Maximum number of transfer groups that may execute concurrently.
- **Valid values:** Positive integer. `1` means sequential group execution; values > 1 enable concurrent groups up to the specified limit.

#### `protocol`
- **Context:** Transfer
- **Type:** string
- **Default:** `"http"`
- **Description:** File transfer protocol. Selects which protocol adapter handles the transfer.
- **Valid values:**
  - `"http"` — HTTP download (non-blocking, reactive via Vert.x WebClient).
  - `"https"` — HTTPS download (same adapter as `http` with TLS).
  - `"ftp"` — FTP transfer (blocking, runs on Vert.x WorkerExecutor).
  - `"sftp"` — SFTP transfer over SSH (blocking, runs on Vert.x WorkerExecutor).
  - `"smb"` — SMB/CIFS file share transfer (blocking, runs on Vert.x WorkerExecutor).

#### `retryCount`
- **Context:** TransferGroup
- **Type:** integer
- **Default:** `0`
- **Description:** Number of times to retry the entire group after a failure.
- **Valid values:** Non-negative integer. `0` means no retries; `3` means up to three retry attempts after the initial failure.

#### `source`
- **Context:** Transfer
- **Type:** string
- **Required:** Yes
- **Description:** Origin path or URL of the file to transfer.
- **Valid values:** Absolute file path or URL appropriate for the selected `protocol`. Supports `{{variable}}` substitution. Examples: `"https://api.example.com/data/{{date}}/export.csv"`, `"/mnt/share/input/data.json"`.

#### `spec`
- **Context:** Root
- **Type:** object
- **Required:** Yes
- **Description:** Container for the workflow's runtime definition.
- **Child keywords:** `variables`, `execution`, `transferGroups`.

#### `strategy`
- **Context:** Execution
- **Type:** string
- **Default:** `"sequential"`
- **Description:** Controls how transfer groups are scheduled for execution.
- **Valid values:**
  - `"sequential"` — groups execute one at a time in dependency order.
  - `"parallel"` — groups without unmet dependencies execute concurrently, up to the `parallelism` limit.

#### `tags`
- **Context:** Metadata
- **Type:** string[]
- **Required:** Yes
- **Description:** Classification labels for search and filtering of workflows.
- **Valid values:** Array of 1–20 strings. Each tag must be lowercase, start and end with a letter or digit, may contain hyphens, and be max 30 characters. Must match `^[a-z][a-z0-9\-]*[a-z0-9]$`. No duplicate tags allowed. Example: `["daily", "finance", "etl"]`.

#### `timeout`
- **Context:** Execution
- **Type:** duration
- **Default:** `"3600s"` (1 hour)
- **Description:** Maximum wall-clock time for the entire workflow execution. If exceeded, the workflow is terminated.
- **Valid values:** Numeric value with a suffix:
  - `"30s"` — 30 seconds
  - `"5m"` — 5 minutes
  - `"2h"` — 2 hours
  - `"3600"` — plain number treated as seconds

#### `transferGroups`
- **Context:** Spec
- **Type:** object[]
- **Description:** Ordered list of transfer groups that make up the workflow. Each group contains one or more `transfers` and may declare `dependsOn` relationships.
- **Child keywords per group:** `name`, `description`, `condition`, `dependsOn`, `continueOnError`, `retryCount`, `variables`, `transfers`.

#### `transfers`
- **Context:** TransferGroup
- **Type:** object[]
- **Description:** List of individual file transfer definitions within a group.
- **Child keywords per transfer:** `name`, `source`, `destination`, `protocol`, `condition`, `options`.

#### `type`
- **Context:** Metadata
- **Type:** string
- **Default:** `"workflow"`
- **Description:** Classifies the workflow by its purpose. Validated against a fixed set of allowed values (non-standard values produce a warning).
- **Valid values:**
  - `"transfer-workflow"` — general file transfer between systems.
  - `"data-pipeline-workflow"` — multi-stage data processing pipeline.
  - `"download-workflow"` — one-way download from remote sources.
  - `"validation-test-workflow"` — test harness for validating transfer configurations.
  - `"external-data-config"` — configuration for external data source integration.
  - `"etl-workflow"` — extract-transform-load data movement.
  - `"backup-workflow"` — scheduled backup operations.
  - `"sync-workflow"` — bidirectional or incremental synchronisation between systems.

#### `variables`
- **Context:** Spec, TransferGroup
- **Type:** map (string → string)
- **Default:** `{}` _(empty map)_
- **Description:** Key-value pairs for `{{variable}}` substitution. Referenced in `source`, `destination`, `condition`, and `options` fields. Group-level variables override spec-level variables of the same name.
- **Valid values:** Any string keys and string values. Keys become substitution tokens: `{ date: "2026-02-11" }` enables `{{date}}` in transfer fields.

#### `version`
- **Context:** Metadata
- **Type:** string
- **Required:** Yes
- **Default:** `"1.0.0"`
- **Description:** Semantic version of the workflow definition, used for tracking changes.
- **Valid values:** Must follow [Semantic Versioning](https://semver.org/) — `MAJOR.MINOR.PATCH` with optional pre-release suffix. Examples: `"1.0.0"`, `"2.1.0-beta"`, `"3.0.0-rc.1+build.42"`.

#### `virtualRun`
- **Context:** Execution
- **Type:** boolean
- **Default:** `false`
- **Description:** When enabled, simulates the full execution flow and reports what transfers *would* occur without actually moving any data. Unlike `dryRun`, `virtualRun` exercises the complete execution path including group scheduling and dependency resolution.
- **Valid values:**
  - `true` — simulate execution; no data is transferred.
  - `false` — normal execution.

### Planned Keywords (177)

These keywords appear in this guide's reference material for forward planning, but do not yet have parser, validator, or model support in the codebase.

#### Notifications (19)

Configures how Quorus alerts operators and stakeholders about workflow events. Notifications can be triggered on failure, success, or at regular intervals during long-running transfers. Each trigger type (`onFailure`, `onSuccess`, `onProgress`) supports multiple delivery channels including email, symphony, and webhooks, with templated messages that support `{{variable}}` substitution. This group also covers attachment handling, recipient management, and throttling controls to prevent notification flooding.

| Keyword | Context | Type | Description |
|---------|---------|------|-------------|
| `attachments` | onFailure, onSuccess | object[] | Files or reports to attach to the notification (e.g., transfer logs, error summaries). |
| `channels` | onFailure, onSuccess, onProgress | string[] | Delivery channels for the notification — e.g., `"email"`, `"symphony"`, `"webhook"`. |
| `includeErrorDetails` | onFailure | boolean | When `true`, append full error stack traces and context to the notification body. |
| `includeLogs` | onFailure, onSuccess | boolean | When `true`, attach relevant workflow execution logs to the notification. |
| `includeMetrics` | onSuccess, onProgress | boolean | When `true`, include transfer metrics (bytes, duration, throughput) in the notification. |
| `intervalMinutes` | onProgress | integer | Minimum interval between repeated progress notifications to avoid flooding. |
| `mentions` | onFailure, onSuccess | string[] | Users or groups to @-mention in chat-based notifications (e.g., symphony handles). |
| `message` | onFailure, onSuccess, onProgress | string | Body text of the notification. Supports `{{variable}}` substitution. |
| `minimumDurationMinutes` | onProgress | integer | Only send the notification if the workflow has been running longer than this threshold. |
| `notifications` | Spec | object | Container for all notification configuration within a workflow. |
| `onFailure` | notifications | object | Notification settings triggered when a transfer or workflow fails. |
| `onProgress` | notifications | object | Notification settings triggered at regular intervals during execution. |
| `onSuccess` | notifications | object | Notification settings triggered when a transfer or workflow completes successfully. |
| `payload` | onFailure, onSuccess | object | Custom JSON payload for webhook-based notifications. |
| `priority` | onFailure, onSuccess | string | Urgency level of the notification — e.g., `"low"`, `"normal"`, `"high"`, `"critical"`. |
| `recipients` | onFailure, onSuccess, onProgress | string[] | Email addresses or user identifiers to receive the notification. |
| `subject` | onFailure, onSuccess | string | Subject line for email notifications. Supports `{{variable}}` substitution. |
| `template` | onFailure, onSuccess, onProgress | string | Named template to use for formatting the notification body. |
| `url` | onFailure, onSuccess | string | Webhook endpoint URL for HTTP-based notifications. |

#### Integrity & Checksum (19)

Ensures data integrity throughout the transfer lifecycle by validating file checksums before, during, and after transit. Supports multiple hash algorithms (MD5, SHA-256, SHA-512) and both whole-file and chunked validation strategies. When mismatches are detected, configurable actions range from failing the transfer to quarantining the corrupted file for manual review. This group also includes fallback source URLs, resume-on-failure support, and optional incident ticket creation for compliance-critical pipelines.

| Keyword | Context | Type | Description |
|---------|---------|------|-------------|
| `algorithm` | integrity | string | Checksum algorithm to use — e.g., `"md5"`, `"sha256"`, `"sha512"`. |
| `algorithms` | integrity | string[] | Array of algorithms for multi-algorithm validation — e.g., `["sha256", "crc32"]`. |
| `alternativeSources` | Transfer | string[] | Fallback source URLs to try if the primary source fails integrity validation. |
| `chunkSize` | integrity | string | Size of each chunk for chunked validation — e.g., `"1MB"`, `"10MB"`. |
| `chunkValidation` | integrity | boolean\|object | When `true`, validate per-chunk. As an object, accepts `enabled`, `chunkSize`, `parallelValidation`, `resumeOnFailure` children for fine-grained control. |
| `enabled` | integrity, chunkValidation | boolean | Master toggle for integrity checking or chunk validation. |
| `expectedChecksum` | integrity | string | Pre-known checksum value to compare against after transfer. |
| `expectedChecksums` | integrity | map | Map of algorithm name → expected checksum value for multi-algorithm validation — e.g., `{ sha256: "...", crc32: "..." }`. |
| `failOnMismatch` | integrity | boolean | When `true`, mark the transfer as failed if the checksum does not match. When `false`, log a warning but continue. |
| `generateIncident` | integrity | boolean | When `true`, automatically create an incident ticket on integrity failure. |
| `integrity` | Transfer | object | Container for all integrity and checksum settings on a transfer. |
| `notifySecurityTeam` | integrity.errorHandling | boolean | When `true`, alert the security team on integrity failure. |
| `onMismatch` | integrity | string | Action to take when a checksum mismatch is detected — e.g., `"fail"`, `"retry"`, `"quarantine"`. |
| `parallelValidation` | integrity | boolean | When `true`, compute checksums in parallel with the transfer rather than after completion. |
| `quarantineLocation` | integrity | string | Destination path where files with integrity failures are moved for review. |
| `recovery` | integrity | object | Container for recovery actions on integrity failure — retries, backoff, quarantine, and notification. |
| `resumeOnFailure` | Transfer | boolean | When `true`, attempt to resume a partially transferred file after a transient failure. |
| `validateOnCompletion` | integrity | boolean | When `true`, perform checksum validation after the entire transfer finishes. |
| `validateOnTransfer` | integrity | boolean | When `true`, validate checksums incrementally during the transfer. |

#### Retry Policy (8)

Defines how Quorus recovers from transient failures during transfer execution. Retry policies are configured at the transfer group level and control the number of attempts, the delay strategy between retries (fixed, exponential, or linear backoff), and which error types are eligible for retry. Jitter support prevents thundering-herd effects when multiple transfers retry simultaneously against the same endpoint.

| Keyword | Context | Type | Description |
|---------|---------|------|-------------|
| `backoffMultiplier` | retryPolicy | number | Factor by which the delay increases after each retry — e.g., `2.0` doubles the wait each time. |
| `backoffStrategy` | retryPolicy | string | Algorithm for calculating retry delays — e.g., `"fixed"`, `"exponential"`, `"linear"`. |
| `initialDelay` | retryPolicy | string | Wait time before the first retry attempt — e.g., `"5s"`, `"1m"`. |
| `jitter` | retryPolicy | boolean/number | When `true` or a value between 0–1, adds randomness to retry delays to prevent thundering-herd effects. |
| `maxAttempts` | retryPolicy | integer | Maximum total retry attempts (including the initial attempt). |
| `maxDelay` | retryPolicy | string | Upper bound on retry delay regardless of backoff — e.g., `"5m"`. |
| `retryOn` | retryPolicy | string[] | Error types or HTTP status codes that trigger a retry — e.g., `["5xx", "timeout", "connection_reset"]`. |
| `retryPolicy` | TransferGroup | object | Container for all retry configuration on a transfer group. |

#### Progress Tracking & Monitoring (15)

Provides real-time visibility into workflow execution through metrics collection, alert rules, and progress reporting. Monitoring configuration lives at the workflow spec level and includes throughput measurement with sliding-window smoothing, predictive ETA calculations, and anomaly detection for stalled or degraded transfers. Alert rules trigger when metrics cross defined thresholds, enabling proactive intervention before SLA breaches occur.

| Keyword | Context | Type | Description |
|---------|---------|------|-------------|
| `alerts` | monitoring | object[] | Alert rules triggered when metrics cross defined thresholds (e.g., throughput drops below minimum). |
| `anomalyDetection` | monitoring | boolean | When `true`, enable automatic detection of unusual transfer patterns (stalls, sudden speed drops). |
| `customMetrics` | monitoring | object[] | User-defined metric formulas with name, formula expression, and unit — e.g., `{ name: "efficiency", formula: "bytes/time", unit: "bytes/s" }`. |
| `detailedLogging` | TransferGroup monitoring | boolean | When `true`, enable verbose logging for this transfer group's execution. |
| `detailedMetrics` | progressTracking | boolean | When `true`, collect fine-grained per-transfer metrics (bytes, rate, latency, ETA). |
| `metrics` | monitoring | object | Container for custom and built-in metric collection settings. |
| `monitorPerformance` | adaptiveSettings | boolean | When `true`, enable performance monitoring to feed adaptive execution decisions. |
| `monitoring` | Spec | object | Container for all monitoring configuration — metrics, alerts, and dashboards. |
| `predictiveETA` | progressTracking | boolean | When `true`, calculate and report estimated time to completion based on current throughput. |
| `progressTracking` | monitoring | object | Container for progress-reporting settings (granularity, callbacks). |
| `rateSmoothing` | progressTracking | boolean\|object | When `true`, smooth throughput calculations. As an object, accepts `enabled`, `windowSize`, `algorithm` children. |
| `realTimeAlerts` | TransferGroup monitoring | boolean | When `true`, enable immediate alert delivery for this transfer group. |
| `reportingInterval` | progressTracking | string | How often to emit progress updates — e.g., `"10s"`, `"1m"`. |
| `windowSize` | progressTracking, rateSmoothing | integer\|string | Number of samples for sliding-window calculation (integer), or duration string (`"30s"`) when used as a rateSmoothing child. |
| `strictValidation` | TransferGroup integrity | boolean | When `true`, enforce zero-tolerance integrity checks for this group. |

#### Transfer Options — Protocol-Specific (17)

Extends the existing `options` map on individual transfers with protocol-aware settings. These keywords control authentication (username/password, SSH keys, client certificates, TLS verification), transfer behaviour (HTTP method, headers, compression, recursive directory traversal), and post-transfer processing (output format conversion, partitioning, lineage tracking). Each keyword's description notes which protocols it applies to — some are universal while others are specific to HTTP, SFTP, FTP, or SMB.

| Keyword | Context | Type | Description |
|---------|---------|------|-------------|
| `bandwidthMbps` | options | number | Maximum transfer bandwidth in megabits per second. Throttles the transfer to this rate. |
| `certificatePath` | options | string | File path to a client certificate for mutual TLS authentication. Used with HTTPS and SFTP protocols. |
| `compressionCodec` | options | string | Compression algorithm applied during transfer — e.g., `"gzip"`, `"lz4"`, `"zstd"`. Used with HTTP/HTTPS protocols. |
| `generateLineage` | options | boolean | When `true`, record data-lineage metadata (source, destination, timestamps) for audit trails. Used with any protocol. |
| `headers` | options | map | HTTP headers to include in the request — e.g., `{ Authorization: "Bearer {{token}}" }`. Used with HTTP/HTTPS protocols. |
| `indexing` | options | boolean | When `true`, update search indexes or catalogs after writing the destination file. Used with any protocol. |
| `loadStrategy` | options | string | How data is written at the destination — e.g., `"overwrite"`, `"append"`, `"merge"`. Used with any protocol. |
| `method` | options | string | HTTP method to use — e.g., `"GET"`, `"POST"`, `"PUT"`. Default `"GET"`. Used with HTTP/HTTPS protocols. |
| `outputFormat` | options | string | Format conversion for the destination file — e.g., `"csv"`, `"parquet"`, `"json"`. Used with any protocol. |
| `partitionBy` | options | string[] | Columns or keys used to partition output into subdirectories — e.g., `["date", "region"]`. Used with any protocol. |
| `password` | options | string | Password for protocol authentication. Prefer `credentials` for production use. Used with FTP, SFTP, SMB protocols. |
| `preservePermissions` | options | boolean | When `true`, preserve source file permissions (owner, group, mode) at the destination. Used with SFTP and SMB protocols. |
| `recursive` | options | boolean | When `true`, transfer all files in subdirectories recursively. Used with FTP, SFTP, SMB protocols. |
| `sshKey` | options | string | File path to the SSH private key for SFTP authentication. Used with SFTP protocol. |
| `statisticsUpdate` | options | boolean | When `true`, emit per-transfer statistics (bytes, duration, rate) on completion. Used with any protocol. |
| `tlsVerification` | options | boolean | When `true`, verify the server's TLS certificate. Set to `false` for self-signed certs in dev environments. Used with HTTPS and SFTP protocols. |
| `username` | options | string | Username for protocol authentication. Used with FTP, SFTP, SMB protocols. |

#### Error Handling (9)

Configures workflow-level and group-level responses to errors beyond simple retry logic. This includes controlling log verbosity during failures, defining what happens when variable substitution fails (`{{variable}}` cannot be resolved), and optionally auto-creating incident tickets with configurable priority levels. Error handling settings can be applied at the workflow spec level or overridden per transfer group.

| Keyword | Context | Type | Description |
|---------|---------|------|-------------|
| `action` | onError | string | Error response action — `"fail"`, `"continue"`, `"retry"`, `"log"`. Used within per-transfer `onError` blocks. |
| `createTicket` | errorHandling | boolean | When `true`, automatically create a support/incident ticket on workflow failure. |
| `errorHandling` | Spec, TransferGroup | object | Container for all error-handling configuration on a workflow or transfer group. |
| `logLevel` | errorHandling | string | Logging verbosity for the workflow — e.g., `"debug"`, `"info"`, `"warn"`, `"error"`. |
| `notification` | onError | string | Email address or notification target for per-transfer error events. |
| `notifyOnError` | errorHandling | boolean | When `true`, send notifications when variable resolution or other non-transfer errors occur. |
| `onError` | options | object | Container for per-transfer error handling within `options` — action, logging, ticketing. |
| `onVariableError` | errorHandling | string | Action when a `{{variable}}` cannot be resolved — e.g., `"fail"`, `"skip"`, `"use-default"`. |
| `ticketPriority` | errorHandling | string | Priority level for auto-created tickets — e.g., `"low"`, `"medium"`, `"high"`, `"critical"`. |

#### Credentials (6)

Manages authentication credentials for transfers that require OAuth2 token-based or certificate-based access. Rather than embedding secrets directly in `options`, the `credentials` object provides a structured container for client IDs, secrets, token endpoints, scopes, and key paths. In production, values should reference a vault or environment variable rather than containing literal secrets. Credentials are scoped to individual transfers.

| Keyword | Context | Type | Description |
|---------|---------|------|-------------|
| `clientId` | credentials | string | OAuth2 client identifier for token-based authentication. |
| `clientSecret` | credentials | string | OAuth2 client secret. Should reference a vault or environment variable, not a literal value. |
| `credentials` | Transfer | object | Container for authentication credentials — references a named credential store or inline config. |
| `keyPath` | credentials | string | File path to a private key (PEM/PKCS8) for certificate-based authentication. |
| `scope` | credentials | string | OAuth2 scope string — e.g., `"read write"`, `"https://api.example.com/.default"`. |
| `tokenEndpoint` | credentials | string | URL of the OAuth2 token endpoint — e.g., `"https://auth.example.com/oauth/token"`. |

#### Execution — Advanced (15)

Extends the existing `execution` block with fine-grained resource control and agent affinity settings. This group governs CPU, memory, and network bandwidth limits per workflow, adaptive auto-tuning that adjusts parallelism based on real-time system load, and agent placement constraints (preferred agents, avoided agents, dedicated-agent requirements). Resource thresholds can gate workflow execution until sufficient capacity is available, and complex conditional expressions provide advanced gating beyond the simple `condition` strings already implemented.

| Keyword | Context | Type | Description |
|---------|---------|------|-------------|
| `adaptiveSettings` | Execution | object | Container for auto-tuning parameters that adjust execution behaviour based on runtime conditions. |
| `adjustParallelism` | adaptiveSettings | boolean | When `true`, dynamically increase or decrease parallelism based on system load. |
| `avoidAgents` | Execution | string[] | Agent IDs or labels that should **not** be assigned work from this workflow. |
| `conditions` | Spec | object[] | Array of named boolean condition definitions. Each has a `name`, `expression`, and optional `description`. Referenced by transfer groups via `condition`. |
| `cpuUtilization` | resourceThresholds | number | Target CPU utilisation percentage (0–100) for adaptive throttling. |
| `expression` | TransferGroup, Transfer | string | Complex conditional expression for advanced group/transfer gating beyond simple `condition` strings. |
| `maxCpuCores` | Execution | integer | Maximum number of CPU cores an agent may use for this workflow's transfers. |
| `maxMemoryMB` | Execution | integer | Maximum memory in megabytes an agent may allocate for this workflow. |
| `maxNetworkBandwidthMbps` | Execution | number | Maximum aggregate network bandwidth in Mbps for all concurrent transfers in this workflow. |
| `memoryUtilization` | resourceThresholds | number | Target memory utilisation percentage (0–100) for adaptive throttling. |
| `networkUtilization` | resourceThresholds | number | Target network utilisation percentage (0–100) for adaptive throttling. |
| `preferredAgents` | Execution | string[] | Agent IDs or labels preferred for executing this workflow's transfers. |
| `requireDedicatedAgents` | Execution | boolean | When `true`, transfers run only on agents not shared with other workflows. |
| `resourceDependencies` | Execution | object[] | Structured resource constraints that must be satisfied before execution — each with `type`, `constraint` expression, and optional `conflictsWith` group list. |
| `resourceThresholds` | Execution | object | Map of resource metrics to threshold values that gate workflow execution. |

#### Cleanup & SLA (9)

Manages post-execution housekeeping and service-level agreement enforcement. Cleanup rules control how long staging files, logs, and intermediate data are retained before automatic deletion, and whether completed files are archived to a dated location. SLA settings define availability targets and hard execution time limits — workflows exceeding `maxExecutionTime` are forcibly terminated to protect downstream dependencies.

| Keyword | Context | Type | Description |
|---------|---------|------|-------------|
| `archiveEnabled` | cleanup | boolean | When `true`, move completed transfer files to an archive location after success. |
| `archiveLocation` | cleanup | string | Destination path for archived files — e.g., `"/archive/{{year}}/{{month}}"`. |
| `availabilityTarget` | sla | number | SLA availability target as a percentage — e.g., `99.9`. |
| `cleanup` | Spec | object | Container for post-execution cleanup rules (staging files, temp files, logs). |
| `maxExecutionTime` | sla | string | Hard time limit for the workflow. If exceeded, the workflow is forcibly terminated — e.g., `"4h"`. |
| `retainLogsDays` | cleanup | integer | Number of days to keep workflow execution logs before automatic deletion. |
| `retainStagingDays` | cleanup | integer | Number of days to keep intermediate/staging files before cleanup. |
| `sla` | Spec | object | Container for service-level agreement targets and alerting thresholds. |
| `targetExecutionTime` | sla | string | Desired (non-binding) execution time target — used for alerting when exceeded, but does not terminate the workflow. E.g., `"PT2H"`. |

#### Route Configuration (22)

Defines event-driven and schedule-driven transfer routing — the mechanism by which Quorus automatically initiates transfers in response to external stimuli. Routes combine a trigger (file-system events, cron schedules, polling intervals, or batch accumulation) with file filters (by name pattern, minimum size, maximum age) and an assigned agent. This enables continuous integration-style workflows where new files are detected, filtered, batched, and transferred without manual intervention.

| Keyword | Context | Type | Description |
|---------|---------|------|-------------|
| `agent` | Route | string | Agent ID or label assigned to handle this route's transfers. |
| `batch` | trigger | object | Batching settings — accumulate files before triggering a transfer. |
| `cron` | schedule | string | Cron expression for schedule-based triggering — e.g., `"0 */6 * * *"` (every 6 hours). |
| `destination` | Route | object | Destination agent and location for the route's transfers. Contains `agent` and `location`. |
| `events` | trigger | string[] | Event types that trigger the route — e.g., `["file-created", "file-modified"]`. |
| `fileCount` | batch | integer | Number of files to accumulate before triggering a batched transfer. |
| `filters` | Route | object | Rules for including/excluding files from the route — by name pattern, size, age, etc. |
| `interval` | trigger | string\|object | Polling interval as duration string (`"30s"`) or structured object with `period` and `unit` fields. |
| `location` | source, destination | string | Filesystem directory path on the agent — e.g., `"/data/incoming/"`. |
| `logic` | COMPOSITE trigger | string | Logical operator for combining trigger conditions — `"OR"` or `"AND"`. |
| `maxAge` | filters | string | Maximum file age for inclusion in a transfer — e.g., `"24h"`. Older files are skipped. |
| `maxWaitTime` | batch | string | Maximum time to wait for a batch to fill before triggering with whatever is available. |
| `minSize` | filters | string | Minimum file size for inclusion — e.g., `"1KB"`. Smaller files are skipped. |
| `pattern` | filters | string | Glob pattern for matching files — e.g., `"*.csv"`, `"data-*.json"`. |
| `period` | interval | integer | Numeric interval value for INTERVAL_BASED triggers — combined with `unit`. |
| `schedule` | trigger | object | Container for time-based trigger configuration (wraps `cron`, `timezone`). |
| `size` | trigger | object | Container for SIZE_BASED trigger configuration (wraps `threshold`, `maxWaitTime`). |
| `source` | Route | object | Source agent and location for the route's transfers. Contains `agent` and `location`. |
| `threshold` | size | string | Accumulated file size that triggers a SIZE_BASED route — e.g., `"100MB"`, `"500MB"`. |
| `timezone` | schedule | string | IANA timezone for cron schedule evaluation — e.g., `"America/New_York"`, `"UTC"`. |
| `trigger` | Route | object | Container for trigger configuration — event-based, schedule-based, or file-watch. |
| `unit` | interval | string | Time unit for INTERVAL_BASED triggers — `"SECONDS"`, `"MINUTES"`, `"HOURS"`. |

#### Tenant Configuration (25)

Configures multi-tenancy isolation and resource governance. Each tenant operates within a dedicated namespace with its own quotas (bandwidth, storage, concurrent transfers, maximum file size), authentication and authorization settings, encryption policies, and default workflow parameters. Tenant configuration is a top-level document type separate from workflow definitions, managed by platform administrators rather than workflow authors.

| Keyword | Context | Type | Description |
|---------|---------|------|-------------|
| `adminRole` | tenant | string | Role name with full administrative privileges for this tenant — e.g., `"tenant-admin"`. |
| `authentication` | tenant | object | Authentication configuration for the tenant — method, provider, token settings. |
| `authorization` | tenant | object | Authorization rules governing what operations tenant users may perform. |
| `basePath` | tenant | string | Root filesystem or URL path scoped to this tenant — e.g., `"/tenants/acme-corp"`. |
| `dataRetentionDays` | tenant | integer | Number of days to retain transfer data and history for this tenant. |
| `defaultRole` | tenant | string | Role automatically assigned to new users in this tenant — e.g., `"viewer"`. |
| `displayName` | tenant | string | Human-readable name for the tenant shown in dashboards and logs — e.g., `"Acme Corporation"`. |
| `domain` | authentication | string | Authentication domain for identity provider integration — e.g., `"corp.acme.com"`. |
| `encryption` | tenant | object | Encryption settings for data at rest and in transit within the tenant's scope. |
| `isolation` | storage | string | Storage isolation model — `"logical"` (shared infrastructure), `"physical"` (dedicated storage), or `"hybrid"`. |
| `limits` | resources | object | Hard limits on tenant operations — e.g., `maxFileSize`, `maxTransferDuration`. |
| `maxBandwidthMbps` | quotas | number | Maximum aggregate bandwidth in Mbps available to all transfers in this tenant. |
| `maxConcurrentTransfers` | quotas | integer | Maximum number of transfers that may run simultaneously for this tenant. |
| `maxFileSize` | quotas | string | Largest file size this tenant is allowed to transfer — e.g., `"10GB"`. |
| `maxStorageGB` | quotas | number | Total storage quota in gigabytes allocated to this tenant. |
| `maxTransferDuration` | limits | string | Maximum allowed duration for a single transfer — e.g., `"24h"`. |
| `namespace` | tenant | string | Logical namespace isolating this tenant's workflows, agents, and data — e.g., `"acme"`. |
| `parent` | Metadata | string | Parent tenant identifier for hierarchical multi-tenancy — inherits defaults from parent. |
| `provider` | authentication | string | Authentication provider name — e.g., `"active-directory"`, `"okta"`, `"keycloak"`. |
| `quotas` | tenant | object | Container for all resource quotas (bandwidth, storage, concurrency) enforced on this tenant. |
| `resources` | tenant | object | Container for resource configuration, wrapping `quotas` and `limits`. |
| `security` | tenant | object | Container for authentication and authorization configuration within the tenant. |
| `storage` | tenant | object | Container for storage isolation, encryption, and base path configuration. |
| `tenant` | Root | object | Top-level container for a tenant configuration document. |
| `tenantDefaults` | tenant | object | Default values applied to all workflows created within this tenant (timeout, parallelism, etc.). |

#### RBAC (4)

Defines role-based access control for the Quorus platform. Roles are named collections of fine-grained permissions (e.g., `workflow:read`, `workflow:execute`, `transfer:cancel`), and bindings map those roles to subjects — individual users or groups. RBAC configuration is a top-level document type that governs who can view, create, modify, and execute workflows and transfers across the platform.

| Keyword | Context | Type | Description |
|---------|---------|------|-------------|
| `bindings` | RBAC Root | object[] | Maps roles to subjects — each binding associates one role with one or more users/groups. |
| `permissions` | roles | string[] | Allowed operations within a role — e.g., `["workflow:read", "workflow:execute", "transfer:cancel"]`. |
| `roles` | RBAC Root | object[] | Role definitions, each with a name and a set of `permissions`. |
| `subjects` | bindings | object[] | Users or groups assigned to a role binding — e.g., `{ kind: "user", name: "ops@company.com" }`. |

#### Variable Definitions (9)

Provides typed, validated schemas for workflow variables, replacing the free-form `variables` map when strict input validation is required. Each variable definition specifies a data type (`string`, `integer`, `boolean`, `date`), optional constraints (minimum/maximum for numbers, enum for fixed value sets), and serves as the contract between workflow authors and operators who supply runtime values. Invalid inputs are rejected at parse time rather than failing mid-execution.

| Keyword | Context | Type | Description |
|---------|---------|------|-------------|
| `dataType` | variableDefinitions | string | Expected type of the variable value — e.g., `"string"`, `"integer"`, `"boolean"`, `"date"`. |
| `default` | variableDefinitions | any | Default value used when the variable is not provided at runtime. |
| `enum` | variableDefinitions | any[] | Fixed set of allowed values for the variable — e.g., `["dev", "staging", "production"]`. |
| `items` | variableDefinitions | object | Schema for array-type variable elements — specifies the `type` and optional `enum` of each item. |
| `maximum` | variableDefinitions | number | Upper bound for numeric variable values. Validation fails if the supplied value exceeds this. |
| `minimum` | variableDefinitions | number | Lower bound for numeric variable values. Validation fails if the supplied value is below this. |
| `pattern` | variableDefinitions | string | Regex pattern for string variable validation — e.g., `"^(production|staging|development)$"`. |
| `required` | variableDefinitions | boolean | When `true`, the variable must be provided at runtime. Missing required variables cause a parse-time error. |
| `variableDefinitions` | Spec | object | Container for typed variable schemas with constraints, replacing free-form `variables` when strict validation is needed. |

### Vague / Unclear Keywords (7) — Needs Review

These keywords were extracted from the original system design but have overly generic or ambiguous meanings. Each needs specific scoping before it can be added to the YAML schema. They are listed here for future review rather than discarded.

| Keyword | Original Group | Issue |
|---------|---------------|-------|
| `annotations` | Variable Definitions | Metadata about what? Kubernetes-style labels? Comments? |
| `conflictsWith` | Other | No conflict-resolution mechanism is defined. |
| `constraint` | Other | Constraint on what? No target or rule format specified. |
| `formula` | Progress Tracking | Formula for calculating what? No inputs or output defined. |
| `preferences` | Execution | Preferences for *what*? Too vague for a configuration key. |
| `retention` | Variable Definitions | Data retention? Log retention? Overlaps with `retainLogsDays`. |
| `sensitivity` | Variable Definitions | Data classification? Needs formal sensitivity levels defined. |

**29 implemented + 177 planned + 7 under review = 213 keywords total.**

---

*Document Version: 1.2*
*Last Updated: February 11, 2026*
