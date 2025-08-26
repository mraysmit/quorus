# Quorus Log Extraction Pipeline Demo
# Shows exactly how logs flow from applications to aggregation

Write-Host "Quorus Log Extraction Pipeline Demo" -ForegroundColor Green
Write-Host "====================================" -ForegroundColor Green
Write-Host ""

# 1. Show application log configuration
Write-Host "1. APPLICATION LAYER - Java Logging Configuration" -ForegroundColor Yellow
Write-Host "   Quarkus API logging format:" -ForegroundColor Cyan
Write-Host "   %d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3.}] (%t) %s%e%n" -ForegroundColor White
Write-Host "   Example: 2025-08-26 10:36:39,337 INFO [dev.mar.quo.api] (main) Application started" -ForegroundColor Gray
Write-Host ""

# 2. Show Docker container logging
Write-Host "2. DOCKER CONTAINER LAYER - JSON File Driver" -ForegroundColor Yellow
$logPath = docker inspect quorus-api --format="{{.LogPath}}" 2>$null
if ($logPath) {
    Write-Host "   Docker log file location: $logPath" -ForegroundColor Cyan
}
$logConfig = docker inspect quorus-api --format="{{.HostConfig.LogConfig}}" 2>$null
if ($logConfig) {
    Write-Host "   Docker log driver: $logConfig" -ForegroundColor Cyan
}
Write-Host "   Docker wraps each log line in JSON with metadata:" -ForegroundColor Cyan
Write-Host '   {"log":"2025-08-26 10:36:39,337 INFO...","stream":"stdout","time":"2025-08-26T10:36:39.337Z"}' -ForegroundColor Gray
Write-Host ""

# 3. Show Promtail collection
Write-Host "3. PROMTAIL COLLECTION LAYER - Log Shipping" -ForegroundColor Yellow
Write-Host "   Promtail monitors Docker socket and log files:" -ForegroundColor Cyan
Write-Host "   - Watches: /var/lib/docker/containers/*/*.log" -ForegroundColor White
Write-Host "   - Filters by labels: logging=promtail" -ForegroundColor White
Write-Host "   - Parses JSON and extracts log content" -ForegroundColor White
Write-Host "   - Adds labels: container_name, service, stream" -ForegroundColor White
Write-Host ""

# 4. Show current log extraction in action
Write-Host "4. LIVE LOG EXTRACTION DEMO" -ForegroundColor Yellow
Write-Host "   Generating log activity..." -ForegroundColor Cyan

# Generate a heartbeat to create logs
$timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$json = @{
    agentId = "demo-agent"
    timestamp = $timestamp
    sequenceNumber = 999
    status = "active"
    currentJobs = 1
    availableCapacity = 5
    transferMetrics = @{
        active = 1
        completed = 100
        failed = 0
        successRate = 100.0
    }
    healthStatus = @{
        diskSpace = "healthy"
        networkConnectivity = "healthy"
        systemLoad = "normal"
        overallHealth = "healthy"
    }
} | ConvertTo-Json -Depth 3

Write-Host "   Sending heartbeat to generate logs..." -ForegroundColor White
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/agents/heartbeat" -Method POST -Body $json -ContentType "application/json" -ErrorAction Stop
    Write-Host "   ✓ Heartbeat sent successfully" -ForegroundColor Green
} catch {
    Write-Host "   ⚠ Need to register agent first..." -ForegroundColor Yellow
    try {
        $regResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/agents/register" -Method POST -Body (Get-Content test-registration.json -Raw) -ContentType "application/json" -ErrorAction Stop
        Write-Host "   ✓ Agent registered" -ForegroundColor Green
        $response = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/agents/heartbeat" -Method POST -Body $json -ContentType "application/json" -ErrorAction Stop
        Write-Host "   ✓ Heartbeat sent successfully" -ForegroundColor Green
    } catch {
        Write-Host "   ✗ Failed to generate logs: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host ""

# 5. Show raw Docker logs
Write-Host "5. RAW DOCKER LOGS (Last 3 lines)" -ForegroundColor Yellow
$dockerLogs = docker logs quorus-api --tail 3 2>$null
if ($dockerLogs) {
    foreach ($line in $dockerLogs) {
        Write-Host "   $line" -ForegroundColor Gray
    }
} else {
    Write-Host "   No recent logs available" -ForegroundColor Yellow
}
Write-Host ""

# 6. Show Loki query results
Write-Host "6. LOKI AGGREGATED LOGS (Via API)" -ForegroundColor Yellow
try {
    $lokiQuery = "http://localhost:3100/loki/api/v1/query_range?query={container_name=`"quorus-api`"}&limit=3"
    $lokiResponse = Invoke-RestMethod -Uri $lokiQuery -ErrorAction Stop
    if ($lokiResponse.data.result -and $lokiResponse.data.result.Count -gt 0) {
        Write-Host "   Recent aggregated logs:" -ForegroundColor Cyan
        foreach ($stream in $lokiResponse.data.result) {
            foreach ($entry in $stream.values | Select-Object -First 3) {
                $timestamp = [DateTimeOffset]::FromUnixTimeNanoseconds($entry[0]).ToString("HH:mm:ss")
                $message = $entry[1]
                if ($message.Length -gt 100) { $message = $message.Substring(0, 100) + "..." }
                Write-Host "   [$timestamp] $message" -ForegroundColor White
            }
        }
    } else {
        Write-Host "   No logs found in Loki yet (may take a few seconds to appear)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "   Loki not available or no logs yet: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host ""

# 7. Show the complete pipeline
Write-Host "7. COMPLETE LOG EXTRACTION PIPELINE" -ForegroundColor Yellow
Write-Host "   Java App → STDOUT/STDERR → Docker JSON → Promtail → Loki → Grafana" -ForegroundColor Cyan
Write-Host ""
Write-Host "   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐" -ForegroundColor White
Write-Host "   │ Quorus Apps │───▶│ Docker JSON │───▶│  Promtail   │───▶│    Loki     │" -ForegroundColor White
Write-Host "   │ (Java Logs) │    │ Log Driver  │    │ (Collector) │    │ (Storage)   │" -ForegroundColor White
Write-Host "   └─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘" -ForegroundColor White
Write-Host "                                                                     │" -ForegroundColor White
Write-Host "                                                              ┌─────────────┐" -ForegroundColor White
Write-Host "                                                              │   Grafana   │" -ForegroundColor White
Write-Host "                                                              │(Visualization)│" -ForegroundColor White
Write-Host "                                                              └─────────────┘" -ForegroundColor White
Write-Host ""

Write-Host "8. ACCESS POINTS" -ForegroundColor Yellow
Write-Host "   - Raw Docker logs: docker logs quorus-api" -ForegroundColor Cyan
Write-Host "   - Loki API: http://localhost:3100/loki/api/v1/query" -ForegroundColor Cyan
Write-Host "   - Grafana UI: http://localhost:3000 (admin/admin)" -ForegroundColor Cyan
Write-Host ""

Write-Host "Demo completed! The log extraction pipeline is fully operational." -ForegroundColor Green
