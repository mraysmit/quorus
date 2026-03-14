<div align="center">
  <img src="docs/quorus-logo.png" alt="Quorus Logo" width="300"/>

# Quorus File Transfer System

  [![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)
  [![Vert.x](https://img.shields.io/badge/Vert.x-5.0.8-purple.svg)](https://vertx.io/)
  [![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)
  [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
</div>

Quorus is a Java 25 and Vert.x 5 based file transfer platform with two practical execution modes:

- direct execution with `quorus-core` and `quorus-workflow`
- distributed execution with `quorus-controller` and `quorus-agent`

The current implementation centers on a controller-first architecture with embedded HTTP, Raft-backed replicated state, reactive transfer execution, and YAML workflow parsing and execution.

## Current Capabilities

### Transfer Execution

- direct transfer execution through `SimpleTransferEngine`
- protocol registration for HTTP, HTTPS, FTP, FTPS, SFTP, SMB, CIFS, and NFS
- progress tracking, retry handling, cancellation, and engine-level pause/resume operations
- integrity and transfer metrics instrumentation

### Workflow Execution

- YAML parsing through `YamlWorkflowDefinitionParser`
- variable substitution with `{{variable}}` syntax
- dependency graph construction and validation
- `NORMAL`, `DRY_RUN`, and `VIRTUAL_RUN` execution modes

### Distributed Control Plane

- embedded controller HTTP API
- Raft-backed controller state
- agent registration and heartbeat handling
- transfer, assignment, and route CRUD endpoints
- health, readiness, metrics, and cluster status endpoints

## Important Implementation Boundaries

- The repository build targets Java 25.
- The active controller runtime is the embedded Vert.x HTTP server in `quorus-controller`, not the deprecated `quorus-api` Quarkus path.
- Route CRUD and route lifecycle endpoints are implemented, but controller startup does not currently show an autonomous route trigger evaluator being wired in.
- Adapter-level resume support should be treated as not implemented in the current protocol adapters.
- The workflow parser supports a narrower YAML vocabulary than older documentation claimed.

## Module Layout

| Module | Purpose |
|---|---|
| `quorus-core` | Transfer models, protocol adapters, transfer engine, core services |
| `quorus-workflow` | YAML parsing, validation, variable resolution, workflow execution |
| `quorus-controller` | Embedded HTTP API, Raft transport, replicated controller state |
| `quorus-agent` | Agent registration, heartbeat, job polling, execution reporting |
| `quorus-tenant` | Tenant and quota related services |
| `quorus-integration-examples` | Runnable examples for transfers, workflows, tenants, and agent models |

## Build

Use JDK 25 for all builds and tests.

```powershell
$env:JAVA_HOME = "C:\Users\mraysmit\.jdks\openjdk-25"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn clean verify 2>&1 | Tee-Object -FilePath temp\build-output.txt
```

## Quick Start

### Direct Usage

Build the project, then run an example from `quorus-integration-examples`:

```powershell
mvn compile exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.BasicTransferExample"
```

Workflow-focused entry points are also available, including `BasicWorkflowExample` and `ComplexWorkflowExample`.

### Controller Cluster

Start a controller environment with the compose files in `docker/compose`:

```powershell
docker compose -f docker/compose/docker-compose-single-controller.yml up -d
```

For a multi-node setup:

```powershell
docker compose -f docker/compose/docker-compose-controller-first.yml up -d
```

Then verify:

```powershell
curl http://localhost:8080/health/live
curl http://localhost:8080/health/ready
curl http://localhost:8080/raft/status
curl http://localhost:8080/api/v1/info
curl http://localhost:8080/metrics
```

## Example Workflow

```yaml
metadata:
  name: "daily-sync"
  version: "1.0.0"
  description: "Download and stage a daily dataset"

spec:
  variables:
    sourceBase: "https://example.com"
    outputDir: "/data/out"

  transferGroups:
    - name: fetch
      transfers:
        - name: dataset
          source: "{{sourceBase}}/dataset.csv"
          destination: "{{outputDir}}/dataset.csv"
          protocol: http
```

For the currently supported YAML fields, use the YAML guide rather than relying on older examples.

## Key Documentation

- [docs/QUORUS_ARCHITECTURE_QUICKSTART.md](docs/QUORUS_ARCHITECTURE_QUICKSTART.md)
- [docs/QUORUS_API_REFERENCE.md](docs/QUORUS_API_REFERENCE.md)
- [docs/QUORUS_USER_GUIDE.md](docs/QUORUS_USER_GUIDE.md)
- [docs/QUORUS_WORKFLOWS_README.md](docs/QUORUS_WORKFLOWS_README.md)
- [docs/QUORUS_YAML_SYNTAX_GUIDE.md](docs/QUORUS_YAML_SYNTAX_GUIDE.md)
- [docs/QUORUS_INTEGRATION_EXAMPLES_README.md](docs/QUORUS_INTEGRATION_EXAMPLES_README.md)
- [docs/QUORUS_CLUSTER_STARTUP_GUIDE.md](docs/QUORUS_CLUSTER_STARTUP_GUIDE.md)
- [docs/QUORUS-DOCKER-TESTING-README.md](docs/QUORUS-DOCKER-TESTING-README.md)
- [docs/QUORUS_SYSTEM_DESIGN.md](docs/QUORUS_SYSTEM_DESIGN.md)

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).