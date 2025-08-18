# Quorus Workflow Examples

This directory contains comprehensive examples demonstrating the YAML workflow capabilities of Quorus. These examples show how to create, validate, and execute complex file transfer workflows using declarative YAML definitions.

## Overview

The Quorus workflow system provides:
- **YAML-based workflow definitions** with variable substitution
- **Dependency management** with topological sorting
- **Multiple execution modes** (normal, dry run, virtual run)
- **Error handling and retry logic**
- **Progress tracking and monitoring**

## Examples

### 1. BasicWorkflowExample.java

**Purpose**: Demonstrates basic workflow execution with YAML parsing and variable substitution.

**Features**:
- Simple YAML workflow parsing
- Variable substitution with `{{variable}}` syntax
- Virtual run execution for safe demonstration
- Progress monitoring and result reporting

**Run**:
```bash
mvn compile exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.BasicWorkflowExample"
```

### 2. ComplexWorkflowExample.java

**Purpose**: Shows advanced workflow features with complex dependencies and parallel execution.

**Features**:
- Multi-stage data processing pipeline
- Complex dependency graphs with parallel execution
- Multiple execution modes (dry run, virtual run)
- Dependency graph analysis and visualization
- Error handling with continue-on-error logic

**Run**:
```bash
mvn compile exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.ComplexWorkflowExample"
```

### 3. WorkflowValidationExample.java

**Purpose**: Demonstrates comprehensive workflow validation and error handling.

**Features**:
- YAML schema validation
- Semantic workflow validation
- Dependency graph validation (circular dependencies, missing dependencies)
- Variable resolution validation
- Dry run validation for safe testing

**Run**:
```bash
mvn compile exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.WorkflowValidationExample"
```

## Sample YAML Workflows

### Simple Download Workflow (`workflows/simple-download.yaml`)

A basic workflow for downloading files from HTTP sources:

```yaml
apiVersion: v1
kind: TransferWorkflow
metadata:
  name: simple-download-workflow
  description: Simple workflow for downloading files from HTTP sources

spec:
  variables:
    baseUrl: "https://httpbin.org"
    outputDir: "/tmp/downloads"
    
  execution:
    parallelism: 1
    strategy: sequential
    
  transferGroups:
    - name: download-files
      transfers:
        - name: download-json-data
          source: "{{baseUrl}}/json"
          destination: "{{outputDir}}/sample-data.json"
          protocol: http
```

### Data Processing Pipeline (`workflows/data-pipeline.yaml`)

A complex multi-stage data processing pipeline with dependencies:

```yaml
apiVersion: v1
kind: TransferWorkflow
metadata:
  name: data-processing-pipeline
  description: Multi-stage data processing pipeline with dependencies

spec:
  variables:
    environment: "production"
    sourceUrl: "https://httpbin.org"
    inputDir: "/data/input"
    outputDir: "/data/output"
    
  execution:
    parallelism: 3
    strategy: parallel
    
  transferGroups:
    - name: download-configuration
      description: Download processing configuration
      transfers:
        - name: download-config
          source: "{{sourceUrl}}/json"
          destination: "{{inputDir}}/config.json"
          
    - name: download-raw-data
      description: Download raw data files
      transfers:
        - name: download-dataset-a
          source: "{{sourceUrl}}/bytes/4096"
          destination: "{{inputDir}}/dataset-a.bin"
          
    - name: process-data
      description: Process downloaded data
      dependsOn:
        - download-configuration
        - download-raw-data
      transfers:
        - name: process-dataset-a
          source: "{{inputDir}}/dataset-a.bin"
          destination: "{{outputDir}}/processed-a.bin"
```

## Key Concepts

### 1. Variable Substitution

Variables can be defined at multiple levels and use `{{variableName}}` syntax:

- **Global variables**: Defined in `spec.variables`
- **Group variables**: Defined in `transferGroups[].variables`
- **Context variables**: Provided at execution time
- **Environment variables**: Automatically available

**Precedence**: Context > Group > Global > Environment

### 2. Dependencies

Transfer groups can depend on other groups using the `dependsOn` field:

```yaml
transferGroups:
  - name: group1
    transfers: [...]
    
  - name: group2
    dependsOn:
      - group1
    transfers: [...]
```

### 3. Execution Modes

- **Normal**: Execute actual transfers
- **Dry Run**: Validate workflow without executing transfers
- **Virtual Run**: Simulate execution with mock transfers

### 4. Error Handling

Configure error handling behavior:

```yaml
transferGroups:
  - name: resilient-group
    continueOnError: true
    retryCount: 3
    transfers: [...]
```

## Testing Your Workflows

### 1. Schema Validation

```java
WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
ValidationResult result = parser.validateSchema(yamlContent);
```

### 2. Semantic Validation

```java
WorkflowDefinition workflow = parser.parseFromString(yamlContent);
ValidationResult result = parser.validate(workflow);
```

### 3. Dependency Validation

```java
DependencyGraph graph = parser.buildDependencyGraph(List.of(workflow));
ValidationResult result = graph.validate();
```

### 4. Dry Run Testing

```java
ExecutionContext context = ExecutionContext.builder()
    .mode(ExecutionContext.ExecutionMode.DRY_RUN)
    .variables(variables)
    .build();
    
WorkflowExecution result = workflowEngine.dryRun(workflow, context).get();
```

## Best Practices

1. **Start with dry runs** to validate workflows before execution
2. **Use meaningful variable names** and organize them logically
3. **Design for failure** with appropriate retry counts and error handling
4. **Test dependency graphs** to ensure correct execution order
5. **Use virtual runs** for development and testing
6. **Monitor execution progress** and handle errors gracefully

## Integration with Transfer Engine

The workflow system integrates seamlessly with the Quorus transfer engine:

```java
// Create transfer engine
TransferEngine transferEngine = new SimpleTransferEngine();

// Create workflow engine
SimpleWorkflowEngine workflowEngine = new SimpleWorkflowEngine(transferEngine);

// Execute workflow
CompletableFuture<WorkflowExecution> future = workflowEngine.execute(workflow, context);
WorkflowExecution result = future.get();
```

## Next Steps

- Explore the example code to understand implementation details
- Create your own YAML workflows using the provided templates
- Experiment with different execution modes and error handling strategies
- Integrate workflow execution into your applications

For more information, see the main project documentation and the comprehensive system design document.
