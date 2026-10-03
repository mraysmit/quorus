# Test File Transfers in the Quorus full-network topology (docker-compose-full-network.yml)
#
# Development-only: the topology disables request security and TLS. The script:
#   1. finds the Raft leader among the three controllers (host ports 8081-8083), because only
#      the leader accepts writes;
#   2. lists the registered agents of the topology's tenant;
#   3. submits HTTP transfers whose source is the in-network HTTP server, as the agents see it;
#   4. assigns each transfer to an agent explicitly - the controller runs no assignment
#      scheduler (register item ENG-01), so an unassigned transfer stays PENDING;
#   5. polls the transfers until they finish or the timeout expires.

param(
    [string]$TenantId = "development",
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = "Stop"

Write-Host "=== Quorus Network Transfer Tests ===" -ForegroundColor Green
Write-Host ""

$controllerPorts = 8081..8083
# The agents reach the HTTP server by its Compose hostname, not through the host port 8090.
$inNetworkHttpServer = "http://http-server"
$hostHttpServer = "http://localhost:8090"

function Find-Leader {
    foreach ($port in $controllerPorts) {
        try {
            $status = Invoke-RestMethod -Uri "http://localhost:$port/raft/status" -TimeoutSec 5
            if ($status.isLeader) { return "http://localhost:$port" }
        } catch {
            Write-Host "  ⚠ Controller on ${port}: not responding" -ForegroundColor Yellow
        }
    }
    return $null
}

Write-Host "Finding the Raft leader..." -ForegroundColor Cyan
$leader = Find-Leader
if (-not $leader) {
    Write-Host "  ✗ No controller reports itself leader. Cannot proceed." -ForegroundColor Red
    exit 1
}
Write-Host "  ✓ Leader: $leader" -ForegroundColor Green
$apiUrl = "$leader/api/v1"
Write-Host ""

Write-Host "Checking registered agents..." -ForegroundColor Cyan
$agents = @((Invoke-RestMethod -Uri "$apiUrl/agents" -TimeoutSec 10).agents | Where-Object { $_.tenantId -eq $TenantId })
if ($agents.Count -eq 0) {
    Write-Host "  ✗ No agents registered for tenant '$TenantId'. Check the agent containers' logs." -ForegroundColor Red
    exit 1
}
foreach ($agent in $agents) {
    Write-Host "  ✓ $($agent.agentId) - $($agent.region) ($($agent.status))" -ForegroundColor Green
}
Write-Host ""

Write-Host "Checking the HTTP file server from the host..." -ForegroundColor Cyan
try {
    Invoke-WebRequest -Uri "$hostHttpServer/shared/timestamp.txt" -Method Head -TimeoutSec 5 | Out-Null
    Write-Host "  ✓ $hostHttpServer/shared/timestamp.txt is available" -ForegroundColor Green
} catch {
    Write-Host "  ⚠ $hostHttpServer/shared/timestamp.txt is not available yet (the file generator may still be running)" -ForegroundColor Yellow
}
Write-Host ""

$scenarios = @(
    @{ File = "timestamp.txt"; Description = "HTTP small file transfer" },
    @{ File = "random-1mb.bin"; Description = "HTTP 1MB file transfer" }
)
Write-Host "Governed FTP/SFTP scenarios require a service connection and an external secret-provider reference; credentials are never embedded in URIs." -ForegroundColor Gray

$jobIds = @()
$agentIndex = 0
foreach ($scenario in $scenarios) {
    $jobId = "network-test-$([guid]::NewGuid().ToString('N'))"
    $agent = $agents[$agentIndex % $agents.Count]
    $agentIndex++
    Write-Host "  $($scenario.Description) -> $($agent.agentId)" -ForegroundColor Cyan

    $transfer = @{
        jobId = $jobId
        tenantId = $TenantId
        sourceUri = "$inNetworkHttpServer/shared/$($scenario.File)"
        destinationPath = "/app/transfers/$($scenario.File)"
        description = $scenario.Description
    } | ConvertTo-Json
    $assignment = @{
        assignmentId = "assign-$jobId"
        jobId = $jobId
        agentId = $agent.agentId
        status = "ASSIGNED"
    } | ConvertTo-Json

    try {
        Invoke-RestMethod -Uri "$apiUrl/transfers" -Method POST -Body $transfer -ContentType "application/json" -TimeoutSec 10 | Out-Null
        Invoke-RestMethod -Uri "$apiUrl/assignments" -Method POST -Body $assignment -ContentType "application/json" -TimeoutSec 10 | Out-Null
        Write-Host "    ✓ Submitted and assigned: $jobId" -ForegroundColor Green
        $jobIds += $jobId
    } catch {
        Write-Host "    ✗ Submission failed: $_" -ForegroundColor Red
    }
}
Write-Host ""

if ($jobIds.Count -gt 0) {
    Write-Host "Monitoring transfers (up to $TimeoutSeconds s)..." -ForegroundColor Cyan
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $pending = [System.Collections.Generic.List[string]]::new([string[]]$jobIds)
    while ($pending.Count -gt 0 -and (Get-Date) -lt $deadline) {
        foreach ($jobId in @($pending)) {
            try {
                $status = (Invoke-RestMethod -Uri "$apiUrl/transfers/$jobId" -TimeoutSec 5).status
            } catch {
                $status = "UNKNOWN"
            }
            switch ($status) {
                "COMPLETED" { Write-Host "    ✓ ${jobId}: COMPLETED" -ForegroundColor Green; $pending.Remove($jobId) | Out-Null }
                "FAILED"    { Write-Host "    ✗ ${jobId}: FAILED" -ForegroundColor Red; $pending.Remove($jobId) | Out-Null }
                "CANCELLED" { Write-Host "    ✗ ${jobId}: CANCELLED" -ForegroundColor Red; $pending.Remove($jobId) | Out-Null }
                default     { }
            }
        }
        if ($pending.Count -gt 0) { Start-Sleep -Seconds 5 }
    }
    foreach ($jobId in $pending) {
        Write-Host "    ⚠ ${jobId}: not finished within $TimeoutSeconds s; see GET $apiUrl/transfers/$jobId/events" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "=== Transfer Tests Complete ===" -ForegroundColor Green
