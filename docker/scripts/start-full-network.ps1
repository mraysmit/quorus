# Start Full Quorus Network Test Environment
# This script starts the complete Docker environment with controllers, agents, and file servers

param(
    [switch]$Build,
    [switch]$Clean,
    [string]$Profile = "all"
)

$ErrorActionPreference = "Stop"

Write-Host "=== Quorus Full Network Test Environment ===" -ForegroundColor Green
Write-Host "Starting comprehensive file transfer network simulation" -ForegroundColor Green
Write-Host ""

# Change to docker directory
$dockerDir = Join-Path $PSScriptRoot ".."
Set-Location $dockerDir

if ($Clean) {
    Write-Host "Cleaning up existing containers and volumes..." -ForegroundColor Yellow
    docker-compose -f compose/docker-compose-full-network.yml down -v --remove-orphans
    docker system prune -f
    Write-Host "Cleanup complete" -ForegroundColor Green
    Write-Host ""
}

# Build images if requested
if ($Build) {
    Write-Host "Building Docker images..." -ForegroundColor Yellow
    docker-compose -f compose/docker-compose-full-network.yml build --no-cache
    Write-Host "Build complete" -ForegroundColor Green
    Write-Host ""
}

# Start the environment
Write-Host "Starting Quorus network environment..." -ForegroundColor Yellow

try {
    # Start in phases for better startup coordination
    
    # Phase 1: Controllers
    Write-Host "Phase 1: Starting controllers..." -ForegroundColor Cyan
    docker-compose -f compose/docker-compose-full-network.yml up -d controller1 controller2 controller3
    
    # Wait for controllers to be ready
    Write-Host "Waiting for controllers to be ready..." -ForegroundColor Cyan
    Start-Sleep -Seconds 30
    
    # Phase 2: API Service
    Write-Host "Phase 2: Starting API service..." -ForegroundColor Cyan
    docker-compose -f compose/docker-compose-full-network.yml up -d api
    
    # Wait for API to be ready
    Write-Host "Waiting for API service to be ready..." -ForegroundColor Cyan
    Start-Sleep -Seconds 20
    
    # Phase 3: File Transfer Servers
    Write-Host "Phase 3: Starting file transfer servers..." -ForegroundColor Cyan
    docker-compose -f compose/docker-compose-full-network.yml up -d ftp-server sftp-server http-server
    
    # Wait for servers to be ready
    Write-Host "Waiting for file servers to be ready..." -ForegroundColor Cyan
    Start-Sleep -Seconds 15
    
    # Phase 4: Agents
    Write-Host "Phase 4: Starting agents..." -ForegroundColor Cyan
    docker-compose -f compose/docker-compose-full-network.yml up -d agent-nyc agent-london agent-tokyo
    
    # Phase 5: Utilities
    Write-Host "Phase 5: Starting utilities..." -ForegroundColor Cyan
    docker-compose -f compose/docker-compose-full-network.yml up -d file-generator
    
    Write-Host ""
    Write-Host "=== Quorus Network Started Successfully! ===" -ForegroundColor Green
    Write-Host ""
    
    # Display service information
    Write-Host "Service Endpoints:" -ForegroundColor Yellow
    Write-Host "  Controllers:"
    Write-Host "    - Controller 1: http://localhost:8081"
    Write-Host "    - Controller 2: http://localhost:8082"
    Write-Host "    - Controller 3: http://localhost:8083"
    Write-Host "  API Service:     http://localhost:8080"
    Write-Host "  File Servers:"
    Write-Host "    - FTP Server:    ftp://localhost:21 (testuser/testpass)"
    Write-Host "    - SFTP Server:   sftp://localhost:2222 (testuser/testpass)"
    Write-Host "    - HTTP Server:   http://localhost:8090"
    Write-Host ""
    
    Write-Host "Agent Information:" -ForegroundColor Yellow
    Write-Host "  - Agent NYC:     172.21.0.10 (US East)"
    Write-Host "  - Agent London:  172.21.0.11 (EU West)"
    Write-Host "  - Agent Tokyo:   172.21.0.12 (AP Northeast)"
    Write-Host ""
    
    Write-Host "Network Topology:" -ForegroundColor Yellow
    Write-Host "  - Control Plane: 172.20.0.0/16"
    Write-Host "  - Agent Network: 172.21.0.0/16"
    Write-Host "  - Transfer Servers: 172.22.0.0/16"
    Write-Host ""
    
    # Check service health
    Write-Host "Checking service health..." -ForegroundColor Cyan
    
    # Check API health
    try {
        $apiHealth = Invoke-RestMethod -Uri "http://localhost:8080/health" -TimeoutSec 5
        Write-Host "  ✓ API Service: Healthy" -ForegroundColor Green
    } catch {
        Write-Host "  ⚠ API Service: Not responding" -ForegroundColor Yellow
    }
    
    # Check HTTP server
    try {
        $httpHealth = Invoke-RestMethod -Uri "http://localhost:8090/health" -TimeoutSec 5
        Write-Host "  ✓ HTTP Server: Healthy" -ForegroundColor Green
    } catch {
        Write-Host "  ⚠ HTTP Server: Not responding" -ForegroundColor Yellow
    }
    
    Write-Host ""
    Write-Host "Next Steps:" -ForegroundColor Yellow
    Write-Host "  1. Check agent registration: .\scripts\check-agents.ps1"
    Write-Host "  2. Run transfer tests: .\scripts\test-transfers.ps1"
    Write-Host "  3. Monitor logs: docker-compose -f compose/docker-compose-full-network.yml logs -f"
    Write-Host "  4. Stop environment: docker-compose -f compose/docker-compose-full-network.yml down"
    Write-Host ""
    
} catch {
    Write-Host "Error starting environment: $_" -ForegroundColor Red
    Write-Host "Checking container status..." -ForegroundColor Yellow
    docker-compose -f compose/docker-compose-full-network.yml ps
    exit 1
}
