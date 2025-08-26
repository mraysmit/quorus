# Quorus Docker Quick Start Script
# Provides easy commands for common Docker operations

param(
    [Parameter(Position=0)]
    [ValidateSet("cluster", "logging", "test", "stop", "clean", "status", "help")]
    [string]$Action = "help",
    
    [Parameter(Position=1)]
    [ValidateSet("3node", "5node", "network-test")]
    [string]$ClusterType = "3node"
)

function Show-Help {
    Write-Host "Quorus Docker Quick Start" -ForegroundColor Green
    Write-Host "=========================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Usage: .\quick-start.ps1 <action> [cluster-type]" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Actions:" -ForegroundColor Yellow
    Write-Host "  cluster [3node|5node|network-test]  - Start Quorus cluster" -ForegroundColor White
    Write-Host "  logging                             - Start log aggregation stack" -ForegroundColor White
    Write-Host "  test                                - Run test scenarios" -ForegroundColor White
    Write-Host "  stop                                - Stop all services" -ForegroundColor White
    Write-Host "  clean                               - Clean up containers and volumes" -ForegroundColor White
    Write-Host "  status                              - Show service status" -ForegroundColor White
    Write-Host "  help                                - Show this help" -ForegroundColor White
    Write-Host ""
    Write-Host "Examples:" -ForegroundColor Yellow
    Write-Host "  .\quick-start.ps1 cluster           # Start 3-node cluster" -ForegroundColor Gray
    Write-Host "  .\quick-start.ps1 cluster 5node     # Start 5-node cluster" -ForegroundColor Gray
    Write-Host "  .\quick-start.ps1 logging           # Start log aggregation" -ForegroundColor Gray
    Write-Host "  .\quick-start.ps1 test              # Run test scenarios" -ForegroundColor Gray
    Write-Host "  .\quick-start.ps1 status            # Check service status" -ForegroundColor Gray
}

function Start-Cluster {
    param([string]$Type)
    
    $composeFile = switch ($Type) {
        "3node" { "compose/docker-compose.yml" }
        "5node" { "compose/docker-compose-5node.yml" }
        "network-test" { "compose/docker-compose-network-test.yml" }
        default { "compose/docker-compose.yml" }
    }
    
    Write-Host "Starting $Type cluster..." -ForegroundColor Green
    docker-compose -f $composeFile up -d
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Cluster started successfully!" -ForegroundColor Green
        Write-Host ""
        Write-Host "Services available at:" -ForegroundColor Yellow
        Write-Host "  - Quorus API: http://localhost:8080" -ForegroundColor White
        Write-Host "  - Controller 1: http://localhost:8081" -ForegroundColor White
        Write-Host "  - Controller 2: http://localhost:8082" -ForegroundColor White
        Write-Host "  - Controller 3: http://localhost:8083" -ForegroundColor White
        
        if ($Type -eq "5node" -or $Type -eq "network-test") {
            Write-Host "  - Controller 4: http://localhost:8084" -ForegroundColor White
            Write-Host "  - Controller 5: http://localhost:8085" -ForegroundColor White
        }
    } else {
        Write-Host "Failed to start cluster!" -ForegroundColor Red
    }
}

function Start-Logging {
    Write-Host "Starting log aggregation stack..." -ForegroundColor Green
    docker-compose -f compose/docker-compose-loki.yml up -d
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Log aggregation started successfully!" -ForegroundColor Green
        Write-Host ""
        Write-Host "Services available at:" -ForegroundColor Yellow
        Write-Host "  - Grafana: http://localhost:3000 (admin/admin)" -ForegroundColor White
        Write-Host "  - Loki API: http://localhost:3100" -ForegroundColor White
        Write-Host "  - Prometheus: http://localhost:9090" -ForegroundColor White
    } else {
        Write-Host "Failed to start logging stack!" -ForegroundColor Red
    }
}

function Run-Tests {
    Write-Host "Running test scenarios..." -ForegroundColor Green
    
    # Register test agent
    Write-Host "Registering test agent..." -ForegroundColor Cyan
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/agents/register" -Method POST -Body (Get-Content test-data/test-registration.json -Raw) -ContentType "application/json"
        Write-Host "✓ Agent registered: $($response.agentId)" -ForegroundColor Green
    } catch {
        Write-Host "⚠ Registration failed (may already exist)" -ForegroundColor Yellow
    }
    
    # Send test heartbeats
    Write-Host "Sending test heartbeats..." -ForegroundColor Cyan
    & "test-data/send-heartbeat.ps1" -SequenceNumber 1
    
    # Check agent status
    Write-Host "Checking agent status..." -ForegroundColor Cyan
    & "test-data/check-agents.ps1"
}

function Stop-Services {
    Write-Host "Stopping all Quorus services..." -ForegroundColor Yellow
    
    # Stop all possible compose configurations
    docker-compose -f compose/docker-compose.yml down 2>$null
    docker-compose -f compose/docker-compose-5node.yml down 2>$null
    docker-compose -f compose/docker-compose-network-test.yml down 2>$null
    docker-compose -f compose/docker-compose-loki.yml down 2>$null
    
    Write-Host "All services stopped." -ForegroundColor Green
}

function Clean-Environment {
    Write-Host "Cleaning up Docker environment..." -ForegroundColor Yellow
    
    # Stop services first
    Stop-Services
    
    # Remove volumes
    Write-Host "Removing volumes..." -ForegroundColor Cyan
    docker volume prune -f
    
    # Remove unused networks
    Write-Host "Removing unused networks..." -ForegroundColor Cyan
    docker network prune -f
    
    Write-Host "Environment cleaned." -ForegroundColor Green
}

function Show-Status {
    Write-Host "Quorus Service Status" -ForegroundColor Green
    Write-Host "=====================" -ForegroundColor Green
    Write-Host ""
    
    # Check containers
    Write-Host "Running containers:" -ForegroundColor Yellow
    docker ps --filter "name=quorus-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    
    Write-Host ""
    
    # Check networks
    Write-Host "Networks:" -ForegroundColor Yellow
    docker network ls --filter "name=quorus" --format "table {{.Name}}\t{{.Driver}}\t{{.Scope}}"
    
    Write-Host ""
    
    # Check volumes
    Write-Host "Volumes:" -ForegroundColor Yellow
    docker volume ls --filter "name=quorus" --format "table {{.Name}}\t{{.Driver}}"
}

# Main execution
switch ($Action) {
    "cluster" { Start-Cluster $ClusterType }
    "logging" { Start-Logging }
    "test" { Run-Tests }
    "stop" { Stop-Services }
    "clean" { Clean-Environment }
    "status" { Show-Status }
    "help" { Show-Help }
    default { Show-Help }
}
