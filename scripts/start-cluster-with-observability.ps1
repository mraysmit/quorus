#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Starts the Quorus 3-node Raft cluster with full observability stack.

.DESCRIPTION
    This script automates the complete startup process for Quorus:
    1. Starts the observability stack (Prometheus, Grafana, Loki, Tempo, OTel Collector)
    2. Starts the 3-node controller cluster
    3. Connects Docker networks for Prometheus scraping
    4. Reloads Prometheus configuration
    5. Verifies cluster health

.PARAMETER SkipObservability
    Skip starting the observability stack (useful if already running)

.PARAMETER Rebuild
    Force rebuild of Docker images with --no-cache

.PARAMETER Clean
    Stop and remove all containers before starting

.EXAMPLE
    .\start-cluster-with-observability.ps1

.EXAMPLE
    .\start-cluster-with-observability.ps1 -SkipObservability

.EXAMPLE
    .\start-cluster-with-observability.ps1 -Rebuild -Clean

.NOTES
    Author: Quorus Team
    Version: 1.0
    Date: January 30, 2026
#>

param(
    [switch]$SkipObservability,
    [switch]$Rebuild,
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptRoot
$ComposeDir = Join-Path $ProjectRoot "docker\compose"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "=== $Message ===" -ForegroundColor Cyan
}

function Write-Success {
    param([string]$Message)
    Write-Host $Message -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "WARNING: $Message" -ForegroundColor Yellow
}

function Test-PortAvailable {
    param([int]$Port)
    $result = netstat -ano | Select-String ":$Port\s"
    return $null -eq $result
}

function Wait-ForHealthy {
    param(
        [string]$ContainerName,
        [int]$TimeoutSeconds = 60
    )
    
    $elapsed = 0
    while ($elapsed -lt $TimeoutSeconds) {
        $status = docker inspect --format='{{.State.Health.Status}}' $ContainerName 2>$null
        if ($status -eq "healthy") {
            return $true
        }
        Start-Sleep -Seconds 2
        $elapsed += 2
        Write-Host "." -NoNewline
    }
    Write-Host ""
    return $false
}

# =============================================================================
# MAIN SCRIPT
# =============================================================================

Write-Host ""
Write-Host "╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Magenta
Write-Host "║        Quorus Cluster Startup Script                           ║" -ForegroundColor Magenta
Write-Host "║        3-Node Raft Cluster with Observability                  ║" -ForegroundColor Magenta
Write-Host "╚════════════════════════════════════════════════════════════════╝" -ForegroundColor Magenta

Set-Location $ProjectRoot
Write-Host "Working directory: $ProjectRoot"

# -----------------------------------------------------------------------------
# Step 0: Clean (optional)
# -----------------------------------------------------------------------------
if ($Clean) {
    Write-Step "Cleaning up existing containers"
    
    docker-compose -f "$ComposeDir\docker-compose-controller-first.yml" down --volumes 2>$null
    docker-compose -f "$ComposeDir\docker-compose-observability.yml" down --volumes 2>$null
    
    Write-Success "Cleanup complete"
}

# -----------------------------------------------------------------------------
# Step 1: Check Prerequisites
# -----------------------------------------------------------------------------
Write-Step "Step 1: Checking prerequisites"

# Check Docker is running
try {
    docker info | Out-Null
    Write-Success "Docker is running"
} catch {
    Write-Error "Docker is not running. Please start Docker Desktop."
    exit 1
}

# Check required ports
$requiredPorts = @(3000, 3100, 3200, 4317, 8081, 8082, 8083, 9090)
$blockedPorts = @()

foreach ($port in $requiredPorts) {
    if (-not (Test-PortAvailable $port)) {
        $blockedPorts += $port
    }
}

if ($blockedPorts.Count -gt 0) {
    Write-Warning "The following ports are in use: $($blockedPorts -join ', ')"
    Write-Host "You may need to stop conflicting processes."
    
    $continue = Read-Host "Continue anyway? (y/N)"
    if ($continue -ne "y") {
        exit 1
    }
}

# Check m2-repo exists
$m2RepoPath = Join-Path $ProjectRoot "m2-repo\raftlog\raftlog-core\1.0"
if (-not (Test-Path $m2RepoPath)) {
    Write-Warning "m2-repo directory not found. Creating from local Maven repository..."
    
    $sourceRepo = "$env:USERPROFILE\.m2\repository\dev\mars\raftlog"
    if (Test-Path $sourceRepo) {
        New-Item -ItemType Directory -Force -Path "m2-repo" | Out-Null
        Copy-Item -Recurse "$sourceRepo\*" "m2-repo\" -Force
        Write-Success "Copied raftlog artifacts to m2-repo"
    } else {
        Write-Error "raftlog-core not found in local Maven repository. Please build raftlog first."
        exit 1
    }
}

Write-Success "Prerequisites check complete"

# -----------------------------------------------------------------------------
# Step 2: Rebuild Images (optional)
# -----------------------------------------------------------------------------
if ($Rebuild) {
    Write-Step "Step 2: Rebuilding Docker images"
    
    docker build --no-cache -t quorus-controller:latest -f quorus-controller/Dockerfile .
    
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Docker build failed"
        exit 1
    }
    
    Write-Success "Docker images rebuilt"
} else {
    Write-Host "Skipping rebuild (use -Rebuild to force)" -ForegroundColor Gray
}

# -----------------------------------------------------------------------------
# Step 3: Start Observability Stack
# -----------------------------------------------------------------------------
if (-not $SkipObservability) {
    Write-Step "Step 3: Starting observability stack"
    
    docker-compose -f "$ComposeDir\docker-compose-observability.yml" up -d
    
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to start observability stack"
        exit 1
    }
    
    Write-Host "Waiting for services to become healthy" -NoNewline
    
    $services = @("quorus-prometheus", "quorus-grafana", "quorus-loki")
    foreach ($service in $services) {
        if (-not (Wait-ForHealthy $service 60)) {
            Write-Warning "$service did not become healthy in time"
        }
    }
    
    Write-Success "Observability stack started"
} else {
    Write-Host "Skipping observability stack (use without -SkipObservability to start)" -ForegroundColor Gray
}

# -----------------------------------------------------------------------------
# Step 4: Start Controller Cluster
# -----------------------------------------------------------------------------
Write-Step "Step 4: Starting 3-node controller cluster"

docker-compose -f "$ComposeDir\docker-compose-controller-first.yml" up -d

if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to start controller cluster"
    exit 1
}

Write-Host "Waiting for controllers to become healthy" -NoNewline

$controllers = @("quorus-controller1", "quorus-controller2", "quorus-controller3")
foreach ($controller in $controllers) {
    if (-not (Wait-ForHealthy $controller 60)) {
        Write-Warning "$controller did not become healthy in time"
    }
}

Write-Success "Controller cluster started"

# -----------------------------------------------------------------------------
# Step 5: Connect Docker Networks
# -----------------------------------------------------------------------------
Write-Step "Step 5: Connecting Docker networks"

# Connect Prometheus to controller network
$networkConnected = docker network connect compose_quorus-cluster quorus-prometheus 2>&1

if ($networkConnected -like "*already exists*") {
    Write-Host "Prometheus already connected to controller network" -ForegroundColor Gray
} else {
    Write-Success "Connected Prometheus to controller network"
}

# -----------------------------------------------------------------------------
# Step 6: Reload Prometheus Configuration
# -----------------------------------------------------------------------------
Write-Step "Step 6: Reloading Prometheus configuration"

try {
    Invoke-WebRequest -Method POST -Uri "http://localhost:9090/-/reload" -UseBasicParsing | Out-Null
    Write-Success "Prometheus configuration reloaded"
} catch {
    Write-Warning "Could not reload Prometheus (may not be ready yet)"
}

Start-Sleep -Seconds 5

# -----------------------------------------------------------------------------
# Step 7: Verify Cluster
# -----------------------------------------------------------------------------
Write-Step "Step 7: Verifying cluster"

# Check container status
Write-Host ""
Write-Host "Container Status:" -ForegroundColor White
docker ps --filter "name=quorus" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | ForEach-Object {
    if ($_ -like "*healthy*") {
        Write-Host $_ -ForegroundColor Green
    } elseif ($_ -like "*starting*") {
        Write-Host $_ -ForegroundColor Yellow
    } else {
        Write-Host $_
    }
}

# Check for leader
Write-Host ""
Write-Host "Checking for leader election..." -ForegroundColor White
$leaderLog = docker logs quorus-controller1 2>&1 | Select-String "LEADER"
if ($leaderLog) {
    Write-Success "Leader elected: $($leaderLog[-1])"
} else {
    Write-Warning "No leader elected yet (may need more time)"
}

# Check peers configuration
$peersLog = docker logs quorus-controller1 2>&1 | Select-String "peers="
if ($peersLog -and $peersLog[-1] -notlike "*peers={}*") {
    Write-Success "Cluster peers configured correctly"
} else {
    Write-Warning "Peers may not be configured correctly. Check environment variables."
}

# -----------------------------------------------------------------------------
# Step 8: Verify Metrics
# -----------------------------------------------------------------------------
Write-Step "Step 8: Verifying metrics collection"

Start-Sleep -Seconds 5

try {
    $targets = Invoke-RestMethod -Uri "http://localhost:9090/api/v1/targets" -Method GET
    $composeTargets = $targets.data.activeTargets | Where-Object { $_.labels.job -eq "quorus-controllers-compose" }
    
    $upCount = ($composeTargets | Where-Object { $_.health -eq "up" }).Count
    $totalCount = $composeTargets.Count
    
    if ($upCount -eq $totalCount -and $totalCount -gt 0) {
        Write-Success "All $totalCount controller targets are UP in Prometheus"
    } elseif ($totalCount -eq 0) {
        Write-Warning "No controller targets found. Check prometheus-observability.yml"
    } else {
        Write-Warning "$upCount of $totalCount controller targets are UP"
    }
} catch {
    Write-Warning "Could not query Prometheus targets"
}

# Check for edge metrics
try {
    $edgeMetrics = Invoke-RestMethod -Uri "http://localhost:9090/api/v1/query?query=quorus_raft_rpc_ratio_total" -Method GET
    $edgeCount = $edgeMetrics.data.result.Count
    
    if ($edgeCount -gt 0) {
        Write-Success "Edge metrics available: $edgeCount time series"
    } else {
        Write-Warning "Edge metrics not yet available (may need more heartbeats)"
    }
} catch {
    Write-Warning "Could not query edge metrics"
}

# -----------------------------------------------------------------------------
# Summary
# -----------------------------------------------------------------------------
Write-Host ""
Write-Host "╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║                    CLUSTER STARTED SUCCESSFULLY                ║" -ForegroundColor Green
Write-Host "╚════════════════════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""
Write-Host "Access Points:" -ForegroundColor White
Write-Host "  Grafana:      " -NoNewline; Write-Host "http://localhost:3000" -ForegroundColor Cyan; Write-Host " (admin/admin)"
Write-Host "  Prometheus:   " -NoNewline; Write-Host "http://localhost:9090" -ForegroundColor Cyan
Write-Host "  Controller 1: " -NoNewline; Write-Host "http://localhost:8081/health" -ForegroundColor Cyan
Write-Host "  Controller 2: " -NoNewline; Write-Host "http://localhost:8082/health" -ForegroundColor Cyan
Write-Host "  Controller 3: " -NoNewline; Write-Host "http://localhost:8083/health" -ForegroundColor Cyan
Write-Host "  Load Balancer:" -NoNewline; Write-Host "http://localhost:8080" -ForegroundColor Cyan
Write-Host ""
Write-Host "Useful Commands:" -ForegroundColor White
Write-Host "  View logs:     docker logs -f quorus-controller1"
Write-Host "  Check metrics: curl http://localhost:8081/metrics"
Write-Host "  Stop cluster:  docker-compose -f docker/compose/docker-compose-controller-first.yml down"
Write-Host ""
