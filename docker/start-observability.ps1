# Quorus Observability Stack Startup Script
# Starts Grafana + Tempo + Prometheus + Loki + OTel Collector

param(
    [switch]$Down,
    [switch]$Logs,
    [switch]$Status
)

$ComposeFile = "$PSScriptRoot\compose\docker-compose-observability.yml"

if ($Down) {
    Write-Host "Stopping observability stack..." -ForegroundColor Yellow
    docker-compose -f $ComposeFile down -v
    exit 0
}

if ($Logs) {
    docker-compose -f $ComposeFile logs -f
    exit 0
}

if ($Status) {
    docker-compose -f $ComposeFile ps
    exit 0
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Quorus Observability Stack Startup" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check Docker is running
$dockerStatus = docker info 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Docker is not running. Please start Docker Desktop." -ForegroundColor Red
    exit 1
}

Write-Host "[1/3] Starting observability stack..." -ForegroundColor Green
docker-compose -f $ComposeFile up -d

Write-Host ""
Write-Host "[2/3] Waiting for services to be healthy..." -ForegroundColor Green

$maxWait = 60
$waited = 0
$healthy = $false

while ($waited -lt $maxWait) {
    $grafanaHealth = docker inspect --format='{{.State.Health.Status}}' quorus-grafana 2>$null
    if ($grafanaHealth -eq "healthy") {
        $healthy = $true
        break
    }
    Start-Sleep -Seconds 2
    $waited += 2
    Write-Host "  Waiting... ($waited/$maxWait seconds)" -ForegroundColor Gray
}

if (-not $healthy) {
    Write-Host "[WARN] Services may not be fully ready. Check 'docker-compose ps'" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "[3/3] Stack is ready!" -ForegroundColor Green
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Access Points:" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Grafana:         http://localhost:3000" -ForegroundColor White
Write-Host "                   Login: admin / admin" -ForegroundColor Gray
Write-Host ""
Write-Host "  Prometheus:      http://localhost:9090" -ForegroundColor White
Write-Host "  Tempo (API):     http://localhost:3200" -ForegroundColor White
Write-Host "  Loki (API):      http://localhost:3100" -ForegroundColor White
Write-Host ""
Write-Host "  OTel Collector:" -ForegroundColor White
Write-Host "    OTLP gRPC:     localhost:4317" -ForegroundColor Gray
Write-Host "    OTLP HTTP:     localhost:4318" -ForegroundColor Gray
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Configure Quorus to send telemetry:" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Set environment variables:" -ForegroundColor White
Write-Host "    OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317" -ForegroundColor Gray
Write-Host "    OTEL_METRICS_EXPORTER=otlp" -ForegroundColor Gray
Write-Host "    OTEL_TRACES_EXPORTER=otlp" -ForegroundColor Gray
Write-Host ""
Write-Host "  Or for Java tests:" -ForegroundColor White
Write-Host "    -Dotel.exporter.otlp.endpoint=http://localhost:4317" -ForegroundColor Gray
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Commands:" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Stop stack:      .\start-observability.ps1 -Down" -ForegroundColor Gray
Write-Host "  View logs:       .\start-observability.ps1 -Logs" -ForegroundColor Gray
Write-Host "  Check status:    .\start-observability.ps1 -Status" -ForegroundColor Gray
Write-Host ""
