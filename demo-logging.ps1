# Demo script to show log aggregation capabilities
Write-Host "Quorus Log Aggregation Demo" -ForegroundColor Green
Write-Host "=============================" -ForegroundColor Green
Write-Host ""

# 1. Show current services
Write-Host "1. Current Services Running:" -ForegroundColor Yellow
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" --filter "name=quorus"
Write-Host ""

# 2. Generate some activity
Write-Host "2. Generating log activity..." -ForegroundColor Yellow

# Register an agent
Write-Host "   - Registering agent..." -ForegroundColor Cyan
$regResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/agents/register" -Method POST -Body (Get-Content test-registration.json) -ContentType "application/json" -ErrorAction SilentlyContinue
if ($regResponse) {
    Write-Host "     Agent registered: $($regResponse.agentId)" -ForegroundColor Green
}

# Send heartbeats
Write-Host "   - Sending heartbeats..." -ForegroundColor Cyan
for ($i = 1; $i -le 3; $i++) {
    $timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    $json = @{
        agentId = "test-agent-002"
        timestamp = $timestamp
        sequenceNumber = $i
        status = "active"
        currentJobs = $i
        availableCapacity = 5
        transferMetrics = @{
            active = $i
            completed = 50 + $i
            failed = 1
            successRate = 98.0
        }
        healthStatus = @{
            diskSpace = "healthy"
            networkConnectivity = "healthy"
            systemLoad = "normal"
            overallHealth = "healthy"
        }
    } | ConvertTo-Json -Depth 3

    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/agents/heartbeat" -Method POST -Body $json -ContentType "application/json" -ErrorAction SilentlyContinue
    if ($response -and $response.success) {
        Write-Host "     Heartbeat $i acknowledged" -ForegroundColor Green
    }
    Start-Sleep -Seconds 1
}

# Get statistics
Write-Host "   - Getting agent statistics..." -ForegroundColor Cyan
$stats = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/agents/heartbeat/stats" -ErrorAction SilentlyContinue
if ($stats) {
    Write-Host "     Total agents: $($stats.totalAgents), Healthy: $($stats.healthyAgents)" -ForegroundColor Green
}

Write-Host ""

# 3. Show log aggregation endpoints
Write-Host "3. Log Aggregation Services:" -ForegroundColor Yellow
Write-Host "   - Grafana Dashboard: http://localhost:3000 (admin/admin)" -ForegroundColor Cyan
Write-Host "   - Loki API: http://localhost:3100" -ForegroundColor Cyan
Write-Host "   - Prometheus Metrics: http://localhost:9090" -ForegroundColor Cyan
Write-Host ""

# 4. Show sample LogQL queries
Write-Host "4. Sample LogQL Queries for Grafana:" -ForegroundColor Yellow
Write-Host "   - All API logs: {container_name=`"quorus-api`"}" -ForegroundColor White
Write-Host "   - Heartbeat logs: {container_name=`"quorus-api`"} |= `"heartbeat`"" -ForegroundColor White
Write-Host "   - Error logs: {container_name=`"quorus-api`"} |= `"ERROR`"" -ForegroundColor White
Write-Host "   - Registration logs: {container_name=`"quorus-api`"} |= `"registration`"" -ForegroundColor White
Write-Host ""

# 5. Show recent logs via Loki API
Write-Host "5. Recent Logs from Loki:" -ForegroundColor Yellow
try {
    $lokiQuery = "http://localhost:3100/loki/api/v1/query_range?query={container_name=`"quorus-api`"}&limit=5"
    $lokiResponse = Invoke-RestMethod -Uri $lokiQuery -ErrorAction Stop
    if ($lokiResponse.data.result) {
        Write-Host "   Recent log entries:" -ForegroundColor Cyan
        foreach ($stream in $lokiResponse.data.result) {
            foreach ($entry in $stream.values) {
                $timestamp = [DateTimeOffset]::FromUnixTimeNanoseconds($entry[0]).ToString("HH:mm:ss")
                $message = $entry[1]
                if ($message.Length -gt 80) { $message = $message.Substring(0, 80) + "..." }
                Write-Host "     [$timestamp] $message" -ForegroundColor White
            }
        }
    }
} catch {
    Write-Host "   Loki not ready yet or no logs available" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Demo completed! Check Grafana at http://localhost:3000 for full log visualization." -ForegroundColor Green
