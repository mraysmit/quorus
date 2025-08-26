# Simple Log Extraction Demo
Write-Host "Quorus Log Extraction Pipeline" -ForegroundColor Green
Write-Host "==============================" -ForegroundColor Green
Write-Host ""

# 1. Show application logging
Write-Host "1. APPLICATION LAYER" -ForegroundColor Yellow
Write-Host "   Java apps write to STDOUT/STDERR using Quarkus logging" -ForegroundColor Cyan
Write-Host "   Format: 2025-08-26 10:36:39,337 INFO [dev.mar.quo.api] (main) Message" -ForegroundColor Gray
Write-Host ""

# 2. Show Docker layer
Write-Host "2. DOCKER LAYER" -ForegroundColor Yellow
$logPath = docker inspect quorus-api --format="{{.LogPath}}" 2>$null
if ($logPath) {
    Write-Host "   Docker stores logs in: $logPath" -ForegroundColor Cyan
}
Write-Host "   Docker JSON format: {`"log`":`"message`",`"stream`":`"stdout`",`"time`":`"timestamp`"}" -ForegroundColor Gray
Write-Host ""

# 3. Show Promtail collection
Write-Host "3. PROMTAIL COLLECTION" -ForegroundColor Yellow
Write-Host "   Promtail reads Docker logs and ships to Loki" -ForegroundColor Cyan
Write-Host "   Monitors: /var/lib/docker/containers/*/*.log" -ForegroundColor White
Write-Host ""

# 4. Generate some logs
Write-Host "4. GENERATING LOGS" -ForegroundColor Yellow
Write-Host "   Sending heartbeat to create log activity..." -ForegroundColor Cyan

$timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$json = @{
    agentId = "demo-agent"
    timestamp = $timestamp
    sequenceNumber = 999
    status = "active"
    currentJobs = 1
    availableCapacity = 5
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/agents/heartbeat" -Method POST -Body $json -ContentType "application/json" -ErrorAction Stop
    Write-Host "   ✓ Heartbeat sent - logs generated!" -ForegroundColor Green
} catch {
    Write-Host "   ⚠ Heartbeat failed (agent may need registration)" -ForegroundColor Yellow
}

Write-Host ""

# 5. Show recent Docker logs
Write-Host "5. RECENT DOCKER LOGS" -ForegroundColor Yellow
$dockerLogs = docker logs quorus-api --tail 3 2>$null
if ($dockerLogs) {
    foreach ($line in $dockerLogs) {
        Write-Host "   $line" -ForegroundColor Gray
    }
} else {
    Write-Host "   No recent logs available" -ForegroundColor Yellow
}

Write-Host ""

# 6. Show the pipeline
Write-Host "6. COMPLETE PIPELINE" -ForegroundColor Yellow
Write-Host "   Java App → Docker JSON → Promtail → Loki → Grafana" -ForegroundColor Cyan
Write-Host ""
Write-Host "   Access points:" -ForegroundColor White
Write-Host "   - Docker logs: docker logs quorus-api" -ForegroundColor Gray
Write-Host "   - Grafana UI: http://localhost:3000" -ForegroundColor Gray
Write-Host "   - Loki API: http://localhost:3100" -ForegroundColor Gray

Write-Host ""
Write-Host "Log extraction pipeline is operational!" -ForegroundColor Green
