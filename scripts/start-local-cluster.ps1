# =============================================================================
# Quorus Multi-Node Local Cluster Startup Script
# =============================================================================
# Starts a 3-node Raft cluster locally with separate data directories and ports.
# Each node runs with its own configuration via environment variables.
#
# Prerequisites:
#   - Java 21+ installed
#   - quorus-controller JAR built: mvn package -pl quorus-controller -DskipTests
#   - Observability stack running: docker-compose -f docker-compose-observability.yml up -d
#
# Usage:
#   .\start-local-cluster.ps1           # Start 3-node cluster
#   .\start-local-cluster.ps1 -Stop     # Stop all nodes
#   .\start-local-cluster.ps1 -Status   # Check node status
# =============================================================================

param(
    [switch]$Stop,
    [switch]$Status,
    [int]$Nodes = 3
)

$ErrorActionPreference = "Continue"
$ProjectRoot = "c:\Users\markr\dev\java\corejava\quorus"
$JarPath = "$ProjectRoot\quorus-controller\target\quorus-controller-1.0-SNAPSHOT.jar"
$DataRoot = "$ProjectRoot\data\cluster"
$LogRoot = "$ProjectRoot\logs\cluster"

# Node configuration (ports must be unique)
$NodeConfigs = @(
    @{ NodeId = "node-1"; HttpPort = 8081; RaftPort = 9081; PrometheusPort = 9471 },
    @{ NodeId = "node-2"; HttpPort = 8082; RaftPort = 9082; PrometheusPort = 9472 },
    @{ NodeId = "node-3"; HttpPort = 8083; RaftPort = 9083; PrometheusPort = 9473 }
)

# Build cluster nodes string (all nodes know about each other)
$ClusterNodes = ($NodeConfigs | ForEach-Object { "$($_.NodeId)=localhost:$($_.RaftPort)" }) -join ","

function Stop-Cluster {
    Write-Host "`n=== Stopping Quorus Cluster ===" -ForegroundColor Yellow
    
    Get-Job -Name "quorus-*" -ErrorAction SilentlyContinue | ForEach-Object {
        Write-Host "Stopping job: $($_.Name)..."
        Stop-Job $_ -ErrorAction SilentlyContinue
        Remove-Job $_ -ErrorAction SilentlyContinue
    }
    
    # Also kill any stray Java processes for quorus-controller
    Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
        $_.CommandLine -like "*quorus-controller*"
    } | ForEach-Object {
        Write-Host "Killing process: $($_.Id)..."
        Stop-Process $_ -Force -ErrorAction SilentlyContinue
    }
    
    Write-Host "Cluster stopped." -ForegroundColor Green
}

function Get-ClusterStatus {
    Write-Host "`n=== Quorus Cluster Status ===" -ForegroundColor Cyan
    
    # Check jobs
    $jobs = Get-Job -Name "quorus-*" -ErrorAction SilentlyContinue
    if ($jobs) {
        Write-Host "`nBackground Jobs:" -ForegroundColor Yellow
        $jobs | Format-Table Name, State, HasMoreData -AutoSize
    } else {
        Write-Host "No background jobs found." -ForegroundColor DarkGray
    }
    
    # Check node health
    Write-Host "`nNode Health:" -ForegroundColor Yellow
    foreach ($config in $NodeConfigs) {
        $url = "http://localhost:$($config.HttpPort)/health"
        try {
            $response = Invoke-RestMethod -Uri $url -TimeoutSec 2 -ErrorAction Stop
            $state = $response.raftState
            $color = if ($state -eq "LEADER") { "Green" } elseif ($state -eq "FOLLOWER") { "Blue" } else { "Yellow" }
            Write-Host "  $($config.NodeId): $state (term: $($response.term), leader: $($response.isLeader))" -ForegroundColor $color
        } catch {
            Write-Host "  $($config.NodeId): OFFLINE" -ForegroundColor Red
        }
    }
    
    # Check Prometheus metrics endpoints
    Write-Host "`nPrometheus Endpoints:" -ForegroundColor Yellow
    foreach ($config in $NodeConfigs) {
        $url = "http://localhost:$($config.PrometheusPort)/metrics"
        try {
            $null = Invoke-WebRequest -Uri $url -TimeoutSec 2 -ErrorAction Stop
            Write-Host "  $($config.NodeId): http://localhost:$($config.PrometheusPort)/metrics - OK" -ForegroundColor Green
        } catch {
            Write-Host "  $($config.NodeId): http://localhost:$($config.PrometheusPort)/metrics - UNAVAILABLE" -ForegroundColor Red
        }
    }
}

function Start-Cluster {
    Write-Host "`n=== Starting Quorus 3-Node Cluster ===" -ForegroundColor Cyan
    
    # Check if JAR exists
    if (-not (Test-Path $JarPath)) {
        Write-Host "ERROR: JAR not found at $JarPath" -ForegroundColor Red
        Write-Host "Run: mvn package -pl quorus-controller -DskipTests" -ForegroundColor Yellow
        exit 1
    }
    
    # Clean up existing jobs
    Stop-Cluster
    
    # Create data directories
    foreach ($config in $NodeConfigs) {
        $nodeDataDir = "$DataRoot\$($config.NodeId)"
        $nodeLogDir = "$LogRoot\$($config.NodeId)"
        
        if (-not (Test-Path $nodeDataDir)) {
            New-Item -ItemType Directory -Path $nodeDataDir -Force | Out-Null
        }
        if (-not (Test-Path $nodeLogDir)) {
            New-Item -ItemType Directory -Path $nodeLogDir -Force | Out-Null
        }
    }
    
    Write-Host "`nCluster Configuration:" -ForegroundColor Yellow
    Write-Host "  Nodes: $ClusterNodes"
    Write-Host "  Data Root: $DataRoot"
    Write-Host ""
    
    # Start each node with environment variables
    foreach ($config in $NodeConfigs) {
        $nodeId = $config.NodeId
        $httpPort = $config.HttpPort
        $raftPort = $config.RaftPort
        $prometheusPort = $config.PrometheusPort
        $nodeDataDir = "$DataRoot\$nodeId"
        $nodeLogFile = "$LogRoot\$nodeId\output.log"
        
        Write-Host "Starting $nodeId (HTTP: $httpPort, Raft: $raftPort, Prometheus: $prometheusPort)..."
        
        $scriptBlock = {
            param($jar, $nodeId, $httpPort, $raftPort, $prometheusPort, $clusterNodes, $dataDir, $logFile)
            
            # Set environment variables (these override properties file)
            $env:QUORUS_NODE_ID = $nodeId
            $env:QUORUS_HTTP_PORT = $httpPort
            $env:QUORUS_RAFT_PORT = $raftPort
            $env:QUORUS_CLUSTER_NODES = $clusterNodes
            $env:QUORUS_RAFT_STORAGE_PATH = $dataDir
            $env:QUORUS_TELEMETRY_PROMETHEUS_PORT = $prometheusPort
            
            # OTel configuration (send to collector)
            $env:OTEL_EXPORTER_OTLP_ENDPOINT = "http://localhost:4317"
            $env:OTEL_METRICS_EXPORTER = "otlp"
            $env:OTEL_TRACES_EXPORTER = "otlp"
            $env:OTEL_SERVICE_NAME = "quorus-controller"
            $env:OTEL_RESOURCE_ATTRIBUTES = "service.instance.id=$nodeId,deployment=local-cluster"
            
            # Start Java process and redirect output
            java -jar $jar 2>&1 | Tee-Object -FilePath $logFile
        }
        
        Start-Job -Name "quorus-$nodeId" -ScriptBlock $scriptBlock -ArgumentList @(
            $JarPath,
            $nodeId,
            $httpPort,
            $raftPort,
            $prometheusPort,
            $ClusterNodes,
            $nodeDataDir,
            $nodeLogFile
        ) | Out-Null
        
        # Stagger starts slightly to avoid race conditions
        Start-Sleep -Milliseconds 500
    }
    
    Write-Host "`nWaiting for nodes to start..." -ForegroundColor Yellow
    Start-Sleep -Seconds 5
    
    # Check status
    Get-ClusterStatus
    
    Write-Host "`n=== Cluster Started ===" -ForegroundColor Green
    Write-Host "`nUseful Commands:"
    Write-Host "  Check status:     .\start-local-cluster.ps1 -Status"
    Write-Host "  Stop cluster:     .\start-local-cluster.ps1 -Stop"
    Write-Host "  View node logs:   Get-Job quorus-node-1 | Receive-Job"
    Write-Host "  Grafana:          http://localhost:3000 (admin/admin)"
    Write-Host "  Node-1 health:    curl http://localhost:8081/health"
    Write-Host "  Node-2 health:    curl http://localhost:8082/health"
    Write-Host "  Node-3 health:    curl http://localhost:8083/health"
}

# Main execution
if ($Stop) {
    Stop-Cluster
} elseif ($Status) {
    Get-ClusterStatus
} else {
    Start-Cluster
}
