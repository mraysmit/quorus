# Quorus Cluster Startup Guide

Complete step-by-step guide for starting a 3-node Quorus Raft cluster with full observability (Prometheus, Grafana, Loki, Tempo) and nodeGraph visualization.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Directory Structure](#directory-structure)
3. [Step 1: Build Local Dependencies](#step-1-build-local-dependencies)
4. [Step 2: Set Up M2_REPO Environment Variable](#step-2-set-up-m2_repo-environment-variable)
5. [Step 3: Build Docker Images](#step-3-build-docker-images)
6. [Step 4: Start Observability Stack](#step-4-start-observability-stack)
7. [Step 5: Start Controller Cluster](#step-5-start-controller-cluster)
8. [Step 6: Connect Networks](#step-6-connect-networks)
9. [Step 7: Reload Prometheus Configuration](#step-7-reload-prometheus-configuration)
10. [Step 8: Verify Cluster Health](#step-8-verify-cluster-health)
11. [Step 9: Verify Metrics Collection](#step-9-verify-metrics-collection)
12. [Step 10: Access Grafana Dashboard](#step-10-access-grafana-dashboard)
13. [Troubleshooting](#troubleshooting)
14. [Shutdown Procedure](#shutdown-procedure)

---

## Prerequisites

### Required Software

| Software | Version | Purpose |
|----------|---------|---------|
| Java JDK | 21+ | Building Quorus |
| Maven | 3.9+ | Dependency management |
| Docker Desktop | 4.x+ | Container runtime |
| PowerShell | 7.x+ | Scripting (Windows) |

### Required Ports

Ensure these ports are available on your host:

| Port | Service | Description |
|------|---------|-------------|
| 3000 | Grafana | Dashboard UI |
| 3100 | Loki | Log aggregation |
| 3200 | Tempo | Distributed tracing |
| 4317 | OTLP gRPC | OpenTelemetry collector |
| 4318 | OTLP HTTP | OpenTelemetry collector |
| 8081 | Controller 1 | HTTP API |
| 8082 | Controller 2 | HTTP API |
| 8083 | Controller 3 | HTTP API |
| 8888 | OTel Collector | Metrics |
| 9090 | Prometheus | Metrics storage |

### Check Port Availability

```powershell
# Windows PowerShell
netstat -ano | Select-String ":3000|:8081|:8082|:8083|:9090"

# If ports are in use, identify and stop the processes
Get-Process -Id <PID> | Stop-Process -Force
```

---

## Directory Structure

```
quorus/
├── docker/
│   └── compose/
│       ├── .env                                   # M2_REPO environment variable
│       ├── docker-compose-controller-first.yml   # 3-node controller cluster
│       ├── docker-compose-observability.yml      # Prometheus, Grafana, Loki, Tempo
│       ├── prometheus-observability.yml          # Prometheus scrape config
│       └── grafana/
│           └── provisioning/
│               └── dashboards/
│                   └── json/
│                       └── quorus-controller.json
├── quorus-controller/
│   ├── Dockerfile
│   └── src/
└── pom.xml
```

---

## Step 1: Build Local Dependencies

The `raftlog-core` library is a local dependency not available in Maven Central. Build and install it first.

### 1.1 Clone and Build raftlog (if not already done)

```powershell
# Navigate to raftlog project (separate repository)
cd c:\Users\markr\dev\java\corejava\raftlog

# Build and install to local Maven repository
mvn clean install -DskipTests
```

### 1.2 Verify Installation

```powershell
# Check artifact exists in local .m2 repository
Test-Path "$env:USERPROFILE\.m2\repository\dev\mars\raftlog\raftlog-core\1.0\raftlog-core-1.0.jar"
# Should return: True
```

---

## Step 2: Set Up M2_REPO Environment Variable

Docker BuildKit can access your host's Maven repository via **additional build contexts**, but requires the `M2_REPO` environment variable to be set.

### Why This Matters

| Environment | Maven Repository | Can Docker Access? |
|-------------|-----------------|---------------------|
| Host machine | `~/.m2/repository` | ❌ Not by default |
| Docker build | `/root/.m2/repository` | ✅ Empty container cache |
| With `M2_REPO` | `${M2_REPO}` via BuildKit | ✅ Yes, via named context |

The `raftlog-core` dependency is not on Maven Central. BuildKit's `additional_contexts` feature allows us to reference your host's Maven cache during builds **without** copying files into the build context.

### 2.1 Set M2_REPO Permanently (Windows)

```powershell
# Set permanent user environment variable
[System.Environment]::SetEnvironmentVariable("M2_REPO", "$env:USERPROFILE/.m2/repository", "User")

# Also set for current session
$env:M2_REPO = "$env:USERPROFILE/.m2/repository"

# Verify
Write-Host "M2_REPO = $env:M2_REPO"
```

### 2.2 Set M2_REPO Permanently (Linux/Mac)

```bash
# Add to ~/.bashrc or ~/.zshrc
echo 'export M2_REPO="$HOME/.m2/repository"' >> ~/.bashrc
source ~/.bashrc

# Verify
echo "M2_REPO = $M2_REPO"
```

### 2.3 How BuildKit Uses M2_REPO

The `docker-compose-controller-first.yml` references `M2_REPO` in the build configuration:

```yaml
services:
  controller1:
    build:
      context: ../..
      dockerfile: quorus-controller/Dockerfile
      additional_contexts:
        m2cache: ${M2_REPO}   # ← References your host's Maven cache
```

The Dockerfile then uses `COPY --from=m2cache` to pull artifacts:

```dockerfile
# Copy local Maven artifacts from named build context
# BuildKit pulls from host's ~/.m2/repository via --build-context m2cache=...
COPY --from=m2cache /dev/mars /root/.m2/repository/dev/mars
```

### 2.4 Verify M2_REPO is Set

Before running docker-compose, verify the variable is set:

```powershell
# Windows
if ($env:M2_REPO) { Write-Host "✓ M2_REPO = $env:M2_REPO" } else { Write-Host "✗ M2_REPO not set!" }

# Verify raftlog-core exists
Test-Path "$env:M2_REPO/dev/mars/raftlog/raftlog-core/1.0/raftlog-core-1.0.jar"
```

### How It Works: BuildKit Additional Contexts

BuildKit's `--build-context` (or `additional_contexts` in docker-compose) creates a **named context** that the Dockerfile can reference:

```
┌────────────────────────────────────────────────────────────────┐
│  docker-compose build                                          │
│                                                                │
│  additional_contexts:                                          │
│    m2cache: ${M2_REPO}  ──────────────────────────────────────┼──► Host: C:\Users\markr\.m2\repository
│                                                                │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  Dockerfile                                              │ │
│  │                                                          │ │
│  │  COPY --from=m2cache /dev/mars /root/.m2/.../dev/mars   │ │
│  │              │                                           │ │
│  │              └─ References the named context             │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

**Benefits over the old `m2-repo/` directory hack:**

| Aspect | Old: m2-repo/ directory | New: BuildKit additional_contexts |
|--------|-------------------------|-----------------------------------|
| **Disk usage** | Duplicates artifacts | References host cache directly |
| **Git pollution** | Needs `.gitignore` | Nothing to ignore |
| **Staleness** | Manual sync required | Always current |
| **Setup** | Copy files manually | Set env var once |
| **Team sharing** | Committed to repo | Each dev uses their own cache |

### Why We Still Need This (vs just using cache mounts)

BuildKit cache mounts (`--mount=type=cache`) are great for Maven Central dependencies, but they start **empty**. On first build, Maven would fail because `raftlog-core` isn't on Maven Central.

The `additional_contexts` approach:
1. Makes your local `raftlog-core` available to the build
2. Doesn't require copying files into the build context
3. Keeps the source clean (no `m2-repo/` directory)

### Alternative: Publish raftlog-core to GitHub Packages

For team environments, publishing `raftlog-core` to GitHub Packages eliminates the need for local artifacts entirely:

**1. Publish raftlog-core to GitHub Packages**

In `raftlog/pom.xml`:
```xml
<distributionManagement>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/mraysmit/raftlog</url>
  </repository>
</distributionManagement>
```

Publish:
```bash
cd raftlog
mvn deploy -DskipTests
```

**2. Configure Quorus to pull from GitHub Packages**

In `quorus/pom.xml`:
```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/mraysmit/raftlog</url>
  </repository>
</repositories>
```

**3. Docker build with authentication**

Create `settings-docker.xml`:
```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>${env.GITHUB_ACTOR}</username>
      <password>${env.GITHUB_TOKEN}</password>
    </server>
  </servers>
</settings>
```

In Dockerfile:
```dockerfile
ARG GITHUB_TOKEN
RUN --mount=type=secret,id=github_token \
    mvn dependency:go-offline -s settings-docker.xml
```

Once configured, you can remove the `M2_REPO` dependency entirely.

---

## Step 3: Build Docker Images

### 3.1 Build from Repository Root

```powershell
cd c:\Users\markr\dev\java\corejava\quorus

# Build with no cache to ensure fresh build
docker build --no-cache -t quorus-controller:latest -f quorus-controller/Dockerfile .
```

### 3.2 Monitor Build Progress

The build has these key phases:
1. **POMs layer**: Copy all pom.xml files (cached)
2. **Local artifacts**: `COPY --from=m2cache` pulls raftlog-core from host's M2_REPO
3. **Dependencies**: `mvn dependency:go-offline` with BuildKit cache mounts
4. **Source code**: Copy all src/ directories
5. **Build**: `mvn package` compiles and creates JAR

### 3.3 Expected Build Time

| Phase | Duration |
|-------|----------|
| Dependency download | ~2-3 minutes (first time) |
| Compilation | ~30 seconds |
| Total (cached) | ~40 seconds |
| Total (no cache) | ~3-4 minutes |

### 3.4 Verify Image Created

```powershell
docker images | Select-String "quorus-controller"
```

---

## Step 4: Start Observability Stack

The observability stack provides metrics, logging, and tracing infrastructure.

### 4.1 Start Observability Services

```powershell
cd c:\Users\markr\dev\java\corejava\quorus\docker\compose

docker-compose -f docker-compose-observability.yml up -d
```

### 4.2 Services Started

| Container | Image | Port | Purpose |
|-----------|-------|------|---------|
| quorus-otel-collector | otel/opentelemetry-collector-contrib | 4317, 4318, 8888 | Telemetry collection |
| quorus-prometheus | prom/prometheus | 9090 | Metrics storage |
| quorus-grafana | grafana/grafana | 3000 | Visualization |
| quorus-loki | grafana/loki | 3100 | Log aggregation |
| quorus-tempo | grafana/tempo | 3200 | Distributed tracing |

### 4.3 Wait for Health Checks

```powershell
# Wait for all services to be healthy (approximately 30 seconds)
Start-Sleep -Seconds 30

# Verify all containers are healthy
docker ps --filter "name=quorus" --format "table {{.Names}}\t{{.Status}}"
```

Expected output:
```
NAMES                   STATUS
quorus-grafana          Up 30 seconds (healthy)
quorus-prometheus       Up 30 seconds (healthy)
quorus-tempo            Up 30 seconds (healthy)
quorus-otel-collector   Up 30 seconds (healthy)
quorus-loki             Up 30 seconds (healthy)
```

---

## Step 5: Start Controller Cluster

### 5.1 Verify No Port Conflicts

```powershell
# Check ports 8081-8083 are free
netstat -ano | Select-String ":808[123]"

# If occupied, kill the processes
# Get-Process -Id <PID> | Stop-Process -Force
```

### 5.2 Start 3-Node Cluster

```powershell
cd c:\Users\markr\dev\java\corejava\quorus

docker-compose -f docker/compose/docker-compose-controller-first.yml up -d
```

### 5.3 Services Started

| Container | Hostname | HTTP Port | Raft Port | IP Address |
|-----------|----------|-----------|-----------|------------|
| quorus-controller1 | controller1 | 8081→8080 | 9080 | 172.20.0.11 |
| quorus-controller2 | controller2 | 8082→8080 | 9080 | 172.20.0.12 |
| quorus-controller3 | controller3 | 8083→8080 | 9080 | 172.20.0.13 |
| quorus-loadbalancer | loadbalancer | 8080→80 | - | 172.20.0.10 |

### 5.4 Environment Variables

Each controller receives these environment variables:

```yaml
QUORUS_NODE_ID: controller1          # Unique node identifier
QUORUS_RAFT_HOST: 0.0.0.0            # Bind address for Raft
QUORUS_RAFT_PORT: 9080               # Raft consensus port
QUORUS_HTTP_PORT: 8080               # HTTP API port
QUORUS_CLUSTER_NODES: controller1=controller1:9080,controller2=controller2:9080,controller3=controller3:9080
```

### 5.5 Wait for Cluster Formation

```powershell
# Wait for leader election (approximately 15 seconds)
Start-Sleep -Seconds 15

# Check container health
docker ps --filter "name=quorus-controller" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

Expected output:
```
NAMES                STATUS                    PORTS
quorus-controller3   Up 15 seconds (healthy)   0.0.0.0:8083->8080/tcp
quorus-controller2   Up 15 seconds (healthy)   0.0.0.0:8082->8080/tcp
quorus-controller1   Up 15 seconds (healthy)   0.0.0.0:8081->8080/tcp
```

### 5.6 Verify Cluster Configuration

```powershell
# Check controller1 logs for peer configuration
docker logs quorus-controller1 2>&1 | Select-String "peers="
```

Expected output:
```
Cluster configuration: nodeId=controller1, peers={controller2=controller2:9080, controller3=controller3:9080}
```

**CRITICAL**: If you see `peers={}`, the environment variables are not being read correctly. See [Troubleshooting](#troubleshooting).

---

## Step 6: Connect Networks

The observability stack and controller cluster run on separate Docker networks. Prometheus must be connected to the controller network to scrape metrics.

### 6.1 List Docker Networks

```powershell
docker network ls | Select-String "quorus"
```

Expected output:
```
compose_quorus-cluster   bridge    local
quorus-observability     bridge    local
```

### 6.2 Connect Prometheus to Controller Network

```powershell
docker network connect compose_quorus-cluster quorus-prometheus
```

### 6.3 Verify Connection

```powershell
docker network inspect compose_quorus-cluster --format '{{range .Containers}}{{.Name}} {{end}}'
```

Should include: `quorus-controller1 quorus-controller2 quorus-controller3 quorus-prometheus`

---

## Step 7: Reload Prometheus Configuration

The Prometheus configuration file (`prometheus-observability.yml`) must include scrape targets for the controllers.

### 7.1 Required Scrape Configuration

Ensure `docker/compose/prometheus-observability.yml` contains:

```yaml
  # Quorus Controller cluster (controller-first architecture in Docker)
  - job_name: 'quorus-controllers-compose'
    scrape_interval: 5s
    static_configs:
      - targets: ['controller1:8080']
        labels:
          service: 'quorus-controller'
          deployment: 'compose-cluster'
          node: 'controller1'
      - targets: ['controller2:8080']
        labels:
          service: 'quorus-controller'
          deployment: 'compose-cluster'
          node: 'controller2'
      - targets: ['controller3:8080']
        labels:
          service: 'quorus-controller'
          deployment: 'compose-cluster'
          node: 'controller3'
```

### 7.2 Reload Prometheus

```powershell
curl -X POST http://localhost:9090/-/reload
```

### 7.3 Verify Targets

```powershell
# Wait for scrape
Start-Sleep -Seconds 10

# Check target health via API
curl -s "http://localhost:9090/api/v1/targets" | ConvertFrom-Json | ForEach-Object { 
    $_.data.activeTargets 
} | Where-Object { 
    $_.labels.job -eq "quorus-controllers-compose" 
} | Select-Object @{N='Target';E={$_.scrapeUrl}}, health
```

Expected output:
```
Target                           health
------                           ------
http://controller1:8080/metrics  up
http://controller2:8080/metrics  up
http://controller3:8080/metrics  up
```

---

## Step 8: Verify Cluster Health

### 8.1 Check Leader Election

```powershell
docker logs quorus-controller1 2>&1 | Select-String "LEADER"
```

Expected output:
```
Node controller1 became LEADER for term 1
```

### 8.2 Query Cluster State via API

```powershell
# Check which node is leader
curl -s http://localhost:8081/api/v1/cluster/status | ConvertFrom-Json
```

### 8.3 Health Endpoint

```powershell
# Each controller exposes /health
curl -s http://localhost:8081/health
curl -s http://localhost:8082/health
curl -s http://localhost:8083/health
```

---

## Step 9: Verify Metrics Collection

### 9.1 Check Metrics Endpoint Directly

```powershell
# Query controller1's metrics endpoint
curl -s http://localhost:8081/metrics | Select-String "quorus"
```

Key metrics to look for:
```
quorus_cluster_state         # 0=FOLLOWER, 1=CANDIDATE, 2=LEADER
quorus_cluster_term          # Current Raft term
quorus_cluster_is_leader     # 1 if leader, 0 otherwise
quorus_raft_rpc_ratio_total  # Edge metrics for nodeGraph
```

### 9.2 Query Edge Metrics in Prometheus

```powershell
curl -s "http://localhost:9090/api/v1/query?query=quorus_raft_rpc_ratio_total" | ConvertFrom-Json | ConvertTo-Json -Depth 10
```

Expected result shows RPC counts between nodes:
```json
{
  "metric": {
    "source": "controller1",
    "target": "controller2",
    "type": "append_entries"
  },
  "value": ["timestamp", "170"]
}
```

### 9.3 Verify All Quorus Metrics

```powershell
curl -s "http://localhost:9090/api/v1/label/__name__/values" | ConvertFrom-Json | Select-Object -ExpandProperty data | Where-Object { $_ -like "*quorus*" }
```

Expected metrics:
```
quorus_agents
quorus_cluster_commit_index
quorus_cluster_is_leader
quorus_cluster_last_applied
quorus_cluster_log_size
quorus_cluster_state
quorus_cluster_term
quorus_jobs
quorus_jobs_assignments
quorus_jobs_queued
quorus_raft_rpc_ratio_total
```

---

## Step 10: Access Grafana Dashboard

### 10.1 Open Grafana

Open in browser: **http://localhost:3000**

### 10.2 Login Credentials

| Field | Value |
|-------|-------|
| Username | admin |
| Password | admin |

### 10.3 Navigate to Dashboard

1. Click **Dashboards** in the left sidebar
2. Select **Quorus Controller** dashboard

### 10.4 NodeGraph Panel Configuration

For the nodeGraph visualization of Raft cluster topology:

#### Nodes Data Query
```promql
# Get node state information
quorus_cluster_state{job="quorus-controllers-compose"}
```

#### Edges Data Query
```promql
# Get RPC rate between nodes (edges)
sum by (source, target, type) (
  rate(quorus_raft_rpc_ratio_total{job="quorus-controllers-compose"}[1m])
)
```

#### Label Mappings
| Field | Label |
|-------|-------|
| Node ID | `node` or `source` |
| Source | `source` |
| Target | `target` |

---

## Troubleshooting

### Issue: `peers={}` in Controller Logs

**Symptom**: Controllers start but don't see each other.

**Cause**: Environment variables not matching config property names.

**Solution**: Ensure docker-compose uses `QUORUS_` prefix:
```yaml
environment:
  - QUORUS_NODE_ID=controller1           # NOT: NODE_ID
  - QUORUS_CLUSTER_NODES=...             # NOT: CLUSTER_NODES
  - QUORUS_RAFT_PORT=9080                # NOT: RAFT_PORT
```

### Issue: Docker Build Fails - "Could not find artifact raftlog-core"

**Symptom**: Maven cannot find `dev.mars.raftlog:raftlog-core:1.0`

**Cause**: m2-repo directory structure is incorrect.

**Solution**: Ensure path is `m2-repo/raftlog/raftlog-core/1.0/` (not `m2-repo/raftlog-core/1.0/`)

```powershell
# Correct structure
Remove-Item -Recurse -Force "m2-repo" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "m2-repo"
Copy-Item -Recurse "$env:USERPROFILE\.m2\repository\dev\mars\raftlog\*" "m2-repo\" -Force
```

### Issue: Prometheus Cannot Scrape Controllers

**Symptom**: Targets show "connection refused"

**Cause**: Prometheus not on the same Docker network as controllers.

**Solution**:
```powershell
docker network connect compose_quorus-cluster quorus-prometheus
curl -X POST http://localhost:9090/-/reload
```

### Issue: Port Already in Use

**Symptom**: "bind: Only one usage of each socket address"

**Solution**:
```powershell
# Find process using port
netstat -ano | Select-String ":8081"
# Kill it
Stop-Process -Id <PID> -Force
```

### Issue: Edge Metrics Not Appearing

**Symptom**: `quorus_raft_rpc_ratio_total` returns empty

**Cause**: 
1. Cluster not formed (peers empty)
2. No heartbeats sent yet
3. Prometheus not scraping correct targets

**Solution**:
1. Verify peers in logs
2. Wait 10-15 seconds for heartbeats
3. Check Prometheus targets page: http://localhost:9090/targets

---

## Shutdown Procedure

### Graceful Shutdown

```powershell
# 1. Stop controller cluster
docker-compose -f docker/compose/docker-compose-controller-first.yml down

# 2. Stop observability stack (optional - keeps data)
docker-compose -f docker/compose/docker-compose-observability.yml down

# 3. Full cleanup including volumes
docker-compose -f docker/compose/docker-compose-controller-first.yml down --volumes
docker-compose -f docker/compose/docker-compose-observability.yml down --volumes
```

### Quick Restart

```powershell
cd c:\Users\markr\dev\java\corejava\quorus\docker\compose

# Restart controllers only
docker-compose -f docker-compose-controller-first.yml restart

# Reconnect Prometheus after restart
docker network connect compose_quorus-cluster quorus-prometheus
```

---

## Quick Start Script

Save as `scripts/start-cluster-with-observability.ps1`:

```powershell
#!/usr/bin/env pwsh
# Quorus Cluster Quick Start Script

$ErrorActionPreference = "Stop"
Set-Location "c:\Users\markr\dev\java\corejava\quorus"

Write-Host "=== Step 1: Checking ports ===" -ForegroundColor Cyan
$usedPorts = netstat -ano | Select-String ":808[123]|:9090|:3000"
if ($usedPorts) {
    Write-Warning "Some ports may be in use. Check manually if startup fails."
}

Write-Host "=== Step 2: Starting observability stack ===" -ForegroundColor Cyan
docker-compose -f docker/compose/docker-compose-observability.yml up -d
Start-Sleep -Seconds 10

Write-Host "=== Step 3: Starting controller cluster ===" -ForegroundColor Cyan
docker-compose -f docker/compose/docker-compose-controller-first.yml up -d
Start-Sleep -Seconds 15

Write-Host "=== Step 4: Connecting networks ===" -ForegroundColor Cyan
docker network connect compose_quorus-cluster quorus-prometheus 2>$null

Write-Host "=== Step 5: Reloading Prometheus ===" -ForegroundColor Cyan
curl -X POST http://localhost:9090/-/reload 2>$null
Start-Sleep -Seconds 5

Write-Host "=== Step 6: Verifying cluster ===" -ForegroundColor Cyan
docker ps --filter "name=quorus" --format "table {{.Names}}\t{{.Status}}"

Write-Host ""
Write-Host "=== Cluster Started ===" -ForegroundColor Green
Write-Host "Grafana:    http://localhost:3000 (admin/admin)"
Write-Host "Prometheus: http://localhost:9090"
Write-Host "Controller1: http://localhost:8081/health"
Write-Host "Controller2: http://localhost:8082/health"
Write-Host "Controller3: http://localhost:8083/health"
```

---

## Appendix: Key Configuration Files

### docker-compose-controller-first.yml (excerpt)

```yaml
services:
  controller1:
    build:
      context: ../..
      dockerfile: quorus-controller/Dockerfile
    container_name: quorus-controller1
    hostname: controller1
    environment:
      - QUORUS_NODE_ID=controller1
      - QUORUS_RAFT_HOST=0.0.0.0
      - QUORUS_RAFT_PORT=9080
      - QUORUS_HTTP_PORT=8080
      - QUORUS_CLUSTER_NODES=controller1=controller1:9080,controller2=controller2:9080,controller3=controller3:9080
    ports:
      - "8081:8080"
    networks:
      quorus-cluster:
        ipv4_address: 172.20.0.11
```

### prometheus-observability.yml (excerpt)

```yaml
scrape_configs:
  - job_name: 'quorus-controllers-compose'
    scrape_interval: 5s
    static_configs:
      - targets: ['controller1:8080', 'controller2:8080', 'controller3:8080']
```

### Dockerfile Copy Command

```dockerfile
# Copy local Maven artifacts for raftlog dependency
COPY m2-repo /root/.m2/repository/dev/mars
```

---

*Document Version: 1.0*  
*Last Updated: January 30, 2026*  
*Tested On: Windows 11, Docker Desktop 4.x, Java 21*
