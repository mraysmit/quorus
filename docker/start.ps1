# Simple Quorus Docker Starter
param(
    [string]$Service = "help"
)

switch ($Service) {
    "cluster" {
        Write-Host "Starting Quorus single-controller development environment..." -ForegroundColor Green
        docker-compose -f compose/docker-compose-single-controller.yml up -d
        Write-Host "Controller with embedded HTTP API available at http://localhost:8080" -ForegroundColor Cyan
    }
    "multinode" {
        Write-Host "Starting Quorus multi-node cluster..." -ForegroundColor Green
        docker-compose -f compose/docker-compose-cluster.yml up -d
        Write-Host "Multi-node cluster available at:" -ForegroundColor Cyan
        Write-Host "  - API Node 1: http://localhost:8081" -ForegroundColor White
        Write-Host "  - API Node 2: http://localhost:8082" -ForegroundColor White
        Write-Host "  - API Node 3: http://localhost:8083" -ForegroundColor White
    }
    "controllers" {
        Write-Host "Starting Quorus controller-first cluster..." -ForegroundColor Green
        docker-compose -f compose/docker-compose-controller-first.yml up -d
        Write-Host "Controller cluster available at:" -ForegroundColor Cyan
        Write-Host "  - Load Balanced API: http://localhost:8080" -ForegroundColor Yellow
        Write-Host "  - Controller 1: http://localhost:8081" -ForegroundColor White
        Write-Host "  - Controller 2: http://localhost:8082" -ForegroundColor White
        Write-Host "  - Controller 3: http://localhost:8083" -ForegroundColor White
    }
    "logging" {
        Write-Host "Starting log aggregation..." -ForegroundColor Green
        docker-compose -f compose/docker-compose-loki.yml up -d
        Write-Host "Grafana available at http://localhost:3000 (admin/admin)" -ForegroundColor Cyan
    }
    "stop" {
        Write-Host "Stopping services..." -ForegroundColor Yellow
        docker-compose -f compose/docker-compose-controller-first.yml down 2>$null
        docker-compose -f compose/docker-compose-corrected.yml down 2>$null
        docker-compose -f compose/docker-compose-cluster.yml down 2>$null
        docker-compose -f compose/docker-compose.yml down 2>$null
        docker-compose -f compose/docker-compose-loki.yml down 2>$null
        Write-Host "Services stopped." -ForegroundColor Green
    }
    "status" {
        Write-Host "Quorus Services:" -ForegroundColor Green
        docker ps --filter "name=quorus-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    }
    default {
        Write-Host "Quorus Docker Starter - Controller-First Architecture" -ForegroundColor Green
        Write-Host "Usage: .\start.ps1 <service>" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "Production Configurations:" -ForegroundColor Cyan
        Write-Host "  controllers  - Start controller-first cluster (3 controllers + load balancer) [RECOMMENDED]"
        Write-Host ""
        Write-Host "Development Configurations:" -ForegroundColor Cyan
        Write-Host "  cluster      - Start single controller for development"
        Write-Host ""
        Write-Host "Legacy Configurations:" -ForegroundColor Yellow
        Write-Host "  multinode    - Start multi-node cluster (3 API instances) [DEPRECATED]"
        Write-Host ""
        Write-Host "Monitoring:" -ForegroundColor Cyan
        Write-Host "  logging      - Start log aggregation (Grafana, Loki, Prometheus)"
        Write-Host ""
        Write-Host "Management:" -ForegroundColor Cyan
        Write-Host "  stop         - Stop all services"
        Write-Host "  status       - Show service status"
        Write-Host ""
        Write-Host "Examples:" -ForegroundColor Green
        Write-Host "  .\start.ps1 controllers  # Production cluster"
        Write-Host "  .\start.ps1 cluster      # Development"
        Write-Host "  .\start.ps1 logging      # Monitoring stack"
    }
}
