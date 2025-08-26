$response = Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/agents'
foreach ($agent in $response) {
    Write-Host "Agent: $($agent.agentId)"
    Write-Host "  Status: $($agent.status)"
    Write-Host "  Healthy: $($agent.healthy)"
    Write-Host "  Available: $($agent.available)"
    Write-Host "  Last Heartbeat: $($agent.lastHeartbeat)"
    Write-Host ""
}
