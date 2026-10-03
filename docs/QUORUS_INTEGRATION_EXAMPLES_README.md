<img src="quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Integration Examples

**Version:** 2.3  
**Date:** 2026-10-03  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Scope:** Current direct-execution and model examples

This document lists the example entry points that actually exist in `quorus-integration-examples` today.

## Module Dependencies

The examples module depends on:

- `quorus-core`
- `quorus-workflow`
- `quorus-tenant`

It does not currently depend on `quorus-controller`, so examples in this module focus on direct engine usage, workflow usage, tenant usage, and model demonstrations rather than running a controller cluster in-process.

The examples demonstrate APIs and parser behavior. They are not secure production connection or agent-deployment templates; production service connectivity, secret references, identity, authorization, and telemetry requirements are defined in the canonical architecture and REST API specifications.

## Current Example Classes

### Transfer and Protocol Examples

- `BasicTransferExample`
- `EnterpriseProtocolExample`
- `InternalNetworkTransferExample`
- `SftpFtpRealImplementationDemo`

### Workflow Examples

- `BasicWorkflowExample`
- `ComplexWorkflowExample`
- `WorkflowValidationExample`
- `WorkflowValidationCLI`
- `SchemaValidationExample`
- `SimpleValidationDemo`
- `ValidationExamplesRunner`

### Agent and Tenant Examples

- `AgentDiscoveryExample`
- `AgentCapabilitiesExample`
- `DynamicAgentPoolExample`
- `MultiTenantExample`

## Running an Example

Examples are executed with Maven and the `exec-maven-plugin`. With `-pl` alone, Maven takes `quorus-core`, `quorus-workflow` and `quorus-tenant` from the local repository, so install the reactor once first, and again after changing those modules:

```bash
mvn clean install -DskipTests
mvn compile exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.BasicTransferExample"
```

Replace the class name with any of the example entry points listed above. Without `-Dexec.mainClass`, the plugin runs its configured default, `SftpFtpRealImplementationDemo`.

### Validating workflow files

`WorkflowValidationCLI` runs schema validation (the rules in the [YAML Syntax Guide](QUORUS_YAML_SYNTAX_GUIDE.md#validation-requirements)) on files or a directory:

```bash
mvn compile exec:java -pl quorus-integration-examples \
  -Dexec.mainClass="dev.mars.quorus.examples.WorkflowValidationCLI" \
  -Dexec.args="--validate-directory quorus-integration-examples/src/main/resources/workflows --strict"
```

It accepts file paths and the options `--validate-directory <dir>`, `--strict` (warnings fail), `--quiet`, `--verbose`, `--schema-only`, `--help` and `--version`. `exec:java` runs inside the Maven process, so relative paths resolve against the directory you run Maven from.

## Tests

`CrossModuleIntegrationTest` exercises the transfer engine, workflow engine and tenant service together, and `ExamplesAreVertxFreeTest` keeps Vert.x out of the module. Both run in the default build. The module does not apply the parent's coverage gate.

## Java Baseline

Use JDK 27 for this repository. The root Maven build compiles with `maven.compiler.release` 27.

## Important Corrections from Older Docs

- `RouteBasedTransferExample` is **not** present in the module.
- The examples module is **not** the source of truth for autonomous controller-managed route triggering.
- For current workflow behavior, rely on the workflow examples and the workflow parser/runtime in `quorus-workflow`.

## Recommended Starting Points

- Start with `BasicTransferExample` for direct transfer execution
- Use `BasicWorkflowExample` and `ComplexWorkflowExample` for workflow execution
- Use `WorkflowValidationExample` and `SchemaValidationExample` when working on YAML validation
- Use `AgentCapabilitiesExample` and `DynamicAgentPoolExample` when working with the agent model
