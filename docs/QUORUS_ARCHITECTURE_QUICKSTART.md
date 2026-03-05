<img src="quorus-logo.png" alt="Quorus Logo" width="120"/>

# Quorus Architecture Quickstart

**Version:** 1.0  
**Date:** 2026-03-05  
**Author:** Mark Andrew Ray-Smith Cityline Ltd  

---

## Table of Contents

1. [What is Quorus?](#what-is-quorus)
2. [Controller-First Architecture](#controller-first-architecture)
3. [What Lives Inside Each Controller](#what-lives-inside-each-controller)
4. [Controller Startup Sequence](#controller-startup-sequence)
5. [Transfer Execution Paths](#transfer-execution-paths)
6. [Workflow YAML Examples](#workflow-yaml-examples)
7. [How Agents Fit In](#how-agents-fit-in)
8. [Module Map](#module-map)
9. [Configuration](#configuration)
10. [Observability](#observability)
11. [Further Reading](#further-reading)

---

## What is Quorus?

Quorus is an enterprise-grade distributed file transfer system built with Java 25 and Vert.x 5.0.8. It orchestrates reliable, large-scale file transfers across multiple regions and datacenters using a variety of protocols — HTTP, HTTPS, SFTP, FTP, and SMB. Workflows defined in YAML allow complex multi-step transfer pipelines with dependency graphs, variable substitution, and retry logic. Multi-tenancy support enables resource isolation and quota management across teams or business units.

Quorus supports two transfer execution modes:

- **Direct execution** — The workflow engine or any application can instantiate `SimpleTransferEngine` and execute transfers immediately, with no controller or agents required. This is the simplest way to run transfer workloads.
- **Distributed execution** — For large-scale deployments, a controller cluster manages a centralized job queue with Raft consensus, and distributed agents poll for work, execute transfers, and report progress. This adds fault tolerance, geographic distribution, and centralized coordination.

---

## Controller-First Architecture

In the **controller-first** pattern, each controller is a **self-contained deployable unit** that embeds the HTTP API, Raft consensus, and state machine in the same JVM. There is no separate API gateway — the controller *is* the API server.

```
┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────────┐
│ Controller-1 LEADER  │  │ Controller-2         │  │ Controller-3         │
├──────────────────────┤  ├──────────────────────┤  ├──────────────────────┤
│ HttpApiServer  :8080 │  │ HttpApiServer  :8080 │  │ HttpApiServer  :8080 │
│ RaftNode       :9080 │  │ RaftNode       :9080 │  │ RaftNode       :9080 │
│ StateMachine         │  │ StateMachine         │  │ StateMachine         │
└──────────────────────┘  └──────────────────────┘  └──────────────────────┘
         ↑ Raft replication ↓      ↑                  ↑
         └──────────────────┴►gRPC◄─┴──────────────────┘
```

### Key Properties

| Property | Value |
|----------|-------|
| Fault tolerance | Any 1 of 3 nodes can fail |
| Failover | Automatic leader election (<300ms) |
| Deployment | One self-contained artifact per node |
| Consistency | Strong (Raft quorum — 2 of 3 must agree) |
| Scaling | Add more controller nodes |

## What Lives Inside Each Controller

Each controller node runs three subsystems:

### 1. HTTP API Server (`:8080`)

REST endpoints for agent management, transfer CRUD, job assignment, and health checks. Middleware handles:

- **Correlation IDs** — request tracking across services
- **Drain mode** — graceful shutdown support
- **Leader guarding** — redirecting writes to the current Raft leader

### 2. Raft Consensus Engine (`:9080`)

Leader election, log replication, and fault tolerance using gRPC transport via Protocol Buffers.

- **Election timeout:** 5 seconds
- **Heartbeat interval:** 1 second
- **Snapshots:** enabled, threshold at 10,000 entries
- **Quorum:** 2 of 3 nodes must agree for a write to commit

### 3. Replicated State Machine (`QuorusStateStore`)

The "brain" of the system. All mutations go through Raft, so state is consistent across all nodes:

- **Transfer job metadata** — job ID, status, source/destination, progress
- **Agent fleet registry** — agent ID, capabilities, location, health, capacity
- **Job assignments** — which agent is assigned to which job
- **Pending job queue** — jobs waiting for agent availability

## Controller Startup Sequence

`QuorusControllerVerticle` orchestrates startup in a deliberate order:

```
1. Load Configuration
   └─ Read appConfig (node ID, ports, cluster nodes)

2. Parse Cluster Topology
   └─ Extract peer node addresses from QUORUS_CLUSTER_NODES
   └─ Build peer address map for Raft communication

3. Initialize Raft Transport (gRPC)
   └─ Create GrpcRaftTransport for port 9080

4. Initialize Raft Storage (Write-Ahead Log)
   └─ Create storage via RaftStorageFactory (file-based or in-memory)

5. Create Raft Node
   └─ Build RaftNode with nodeId, clusterNodes, transport,
      stateMachine, storage, election/heartbeat config

6. Start gRPC Server (Port 9080)
   └─ Accept AppendEntries and RequestVote RPCs

7. Start Raft Engine
   └─ Recover log entries from WAL
   └─ Restore snapshots if available
   └─ Become FOLLOWER, listen for leader heartbeats
   └─ If no heartbeat received, trigger leader election

8. Start HTTP API Server (Port 8080)
   └─ Only after Raft is operational — ensures consensus
      is ready before accepting client requests

9. Register Graceful Shutdown
   └─ DRAIN → AWAIT → STOP_SERVICES → CLOSE_RESOURCES
```

> **Key invariant:** Raft consensus is fully operational *before* the HTTP API begins accepting requests.

## Transfer Execution Paths

The transfer engine (`SimpleTransferEngine` in `quorus-core`) is the heart of all file transfer execution. It can be used in two ways:

### Path 1: Direct Execution (No Agents Required)

The workflow engine or any Java application instantiates `SimpleTransferEngine` directly and calls `submitTransfer()`. Transfers execute immediately on the calling JVM — no controller, no agents, no job queue.

```
Workflow YAML → SimpleWorkflowEngine → SimpleTransferEngine → Protocol Adapter → Transfer
```

This is how the `quorus-workflow` module and `quorus-integration-examples` work. It's the simplest deployment: just `quorus-core` and optionally `quorus-workflow` as library dependencies.

### Path 2: Distributed Execution (Via Controller + Agents)

For deployments where transfers need to be coordinated across multiple machines or regions, the controller manages a job queue and agents execute the work:

```
Client → POST /api/v1/transfers → Controller (Raft) → Job Queue
                                                         ↓
Agent polls → GET /agents/{id}/jobs → SimpleTransferEngine → Protocol Adapter → Transfer
                                                         ↓
Agent reports → POST /agents/{id}/status → Controller updates state
```

The controller never executes transfers itself — it only manages state. Agents each run their own `SimpleTransferEngine` instance.

## Workflow YAML Examples

Transfer workloads are defined in YAML workflow files. Each workflow has metadata, variables, execution settings, and one or more transfer groups containing individual transfers.

### Example 1: Simple HTTP Download

A minimal workflow that downloads three files from an HTTP source:

```yaml
metadata:
  name: "simple-download-workflow"
  version: "1.0.0"
  description: "Download files from HTTP sources"
  tags: ["download", "http", "simple"]

spec:
  variables:
    baseUrl: "https://data.example.com"
    outputDir: "/data/downloads"
    timeout: "30s"

  execution:
    parallelism: 1
    timeout: 300s
    strategy: sequential

  transferGroups:
    - name: download-files
      description: Download data files from HTTP source
      continueOnError: false
      retryCount: 3
      transfers:
        - name: download-json-data
          source: "{{baseUrl}}/reports/daily.json"
          destination: "{{outputDir}}/daily-report.json"
          protocol: http
          options:
            timeout: "{{timeout}}"

        - name: download-csv-data
          source: "{{baseUrl}}/exports/transactions.csv"
          destination: "{{outputDir}}/transactions.csv"
          protocol: http
          options:
            timeout: "{{timeout}}"
```

Key points:
- **`{{variable}}`** syntax for substitution — keeps URLs and paths configurable
- **`retryCount: 3`** — automatic retry on transient failures
- **`continueOnError: false`** — stop the group if any transfer fails

### Example 2: Multi-Stage Pipeline with Dependencies

A more complex workflow where transfer groups depend on each other, forming a directed acyclic graph:

```yaml
metadata:
  name: "data-processing-pipeline"
  version: "2.1.0"
  description: "Multi-stage ETL pipeline with dependencies"
  tags: ["data-pipeline", "etl", "parallel", "dependencies"]

spec:
  variables:
    sourceUrl: "https://data.example.com"
    inputDir: "/data/input"
    outputDir: "/data/output"
    chunkSize: "2048"

  execution:
    parallelism: 3
    timeout: 1800s
    strategy: parallel

  transferGroups:
    # Stage 1: Download configuration (runs first)
    - name: download-configuration
      description: Download processing configuration and schemas
      retryCount: 3
      transfers:
        - name: download-config
          source: "{{sourceUrl}}/config/pipeline.json"
          destination: "{{inputDir}}/config.json"
          protocol: http

        - name: download-schema
          source: "{{sourceUrl}}/schemas/validation.xml"
          destination: "{{inputDir}}/schema.xml"
          protocol: http

    # Stage 2: Download raw data (runs in parallel with Stage 1)
    - name: download-raw-data
      description: Download raw data files for processing
      continueOnError: true
      retryCount: 2
      transfers:
        - name: download-dataset-a
          source: "{{sourceUrl}}/datasets/customers.bin"
          destination: "{{inputDir}}/dataset-a.bin"
          protocol: http
          options:
            chunkSize: "{{chunkSize}}"

        - name: download-dataset-b
          source: "{{sourceUrl}}/datasets/orders.bin"
          destination: "{{inputDir}}/dataset-b.bin"
          protocol: http
          options:
            chunkSize: "{{chunkSize}}"

    # Stage 3: Validate (depends on BOTH previous stages)
    - name: validate-data
      description: Validate downloaded data against schema
      dependsOn:
        - download-configuration
        - download-raw-data
      transfers:
        - name: run-validation
          source: "{{inputDir}}/dataset-a.bin"
          destination: "{{outputDir}}/validated-a.bin"
          protocol: http
```

Key points:
- **`dependsOn`** — Stage 3 waits for both Stage 1 and Stage 2 to complete before starting
- **`strategy: parallel`** with **`parallelism: 3`** — independent groups run concurrently
- **`continueOnError: true`** on Stage 2 — partial data failures don't block the pipeline
- The workflow engine resolves the dependency graph automatically using `DependencyGraph`

> See the full set of example workflows in `quorus-integration-examples/src/main/resources/workflows/` and the complete syntax reference in [QUORUS_YAML_SYNTAX_GUIDE.md](QUORUS_YAML_SYNTAX_GUIDE.md).

## How Agents Fit In

Agents are **optional, stateless workers** used in distributed deployments. They poll the controller cluster for assigned jobs and execute transfers locally. They don't need to know which controller is the leader — any controller can serve reads, and writes are forwarded to the leader automatically.

### Agent Lifecycle

```
Agent startup:     POST /agents/register     → controller replicates via Raft
Every 30s:         POST /agents/heartbeat    → capacity + status update
Every 10s:         GET  /agents/{id}/jobs    → fetch assigned work
On transfer:       POST /agents/{id}/status  → report progress/completion
Shutdown:          DELETE /agents/{id}        → deregister
```

### Agent Services

| Service | Responsibility | Interval |
|---------|----------------|----------|
| `AgentRegistrationService` | Initial registration with capabilities | Once at startup |
| `HeartbeatService` | Keep-alive with capacity updates | 30s (configurable) |
| `JobPollingService` | Fetch pending job assignments | 10s (configurable) |
| `JobStatusReportingService` | Report transfer progress/completion | Per-job events |
| `TransferExecutionService` | Execute transfers via protocol adapters | On job receipt |

### Transfer Execution

When an agent receives a job, it delegates to `SimpleTransferEngine` which selects the appropriate protocol adapter:

| Protocol | Adapter | Blocking? |
|----------|---------|-----------|
| HTTP/HTTPS | `HttpTransferProtocol` | No (reactive) |
| SFTP | `SftpTransferProtocol` | Yes (WorkerExecutor) |
| FTP | `FtpTransferProtocol` | Yes (WorkerExecutor) |
| SMB | `SmbTransferProtocol` | Yes (WorkerExecutor) |

## Module Map

```
quorus-core         Transfer engine, protocol adapters, exceptions
quorus-workflow     YAML workflow parsing, dependency graphs, execution engine
quorus-controller   Raft node, gRPC transport, HTTP API server
quorus-agent        Job polling, transfer execution, heartbeat
quorus-tenant       Multi-tenancy, quotas, resource management
```

## Configuration

Each module has `src/main/resources/<module>.properties`. Resolution order (highest priority first):

1. **Environment variable:** `QUORUS_HTTP_PORT=8080`
2. **System property:** `-Dquorus.http.port=8080`
3. **Properties file:** `quorus.http.port=8080`
4. **Default value**

## Observability

- **Prometheus metrics:** `:9464/metrics` (controller), `:9465/metrics` (agent)
- **OTLP traces:** `http://localhost:4317` (gRPC)
- **Metric naming:** `quorus.<module>.<metric>.<unit>` (e.g., `quorus.transfer.bytes`)

## Further Reading

- [QUORUS_SYSTEM_DESIGN.md](QUORUS_SYSTEM_DESIGN.md) — comprehensive architecture documentation
- [QUORUS_USER_GUIDE.md](QUORUS_USER_GUIDE.md) — user-facing guide
- [QUORUS_CLUSTER_STARTUP_GUIDE.md](QUORUS_CLUSTER_STARTUP_GUIDE.md) — cluster deployment instructions
- [QUORUS_YAML_SYNTAX_GUIDE.md](QUORUS_YAML_SYNTAX_GUIDE.md) — workflow YAML reference
