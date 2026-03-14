# Quorus Integration Examples

**Version:** 2.0  
**Date:** 2026-03-14

This document lists the example entry points that actually exist in `quorus-integration-examples` today.

## Module Dependencies

The examples module depends on:

- `quorus-core`
- `quorus-workflow`
- `quorus-tenant`

It does not currently depend on `quorus-controller`, so examples in this module focus on direct engine usage, workflow usage, tenant usage, and model demonstrations rather than running a controller cluster in-process.

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

Examples are executed with Maven and the `exec-maven-plugin`.

```bash
mvn compile exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.BasicTransferExample"
```

Replace the class name with any of the example entry points listed above.

## Java Baseline

Use JDK 25 for this repository.

## Important Corrections from Older Docs

- `RouteBasedTransferExample` is **not** present in the module.
- The examples module is **not** the source of truth for autonomous controller-managed route triggering.
- For current workflow behavior, rely on the workflow examples and the workflow parser/runtime in `quorus-workflow`.

## Recommended Starting Points

- Start with `BasicTransferExample` for direct transfer execution
- Use `BasicWorkflowExample` and `ComplexWorkflowExample` for workflow execution
- Use `WorkflowValidationExample` and `SchemaValidationExample` when working on YAML validation
- Use `AgentCapabilitiesExample` and `DynamicAgentPoolExample` when working with the agent model