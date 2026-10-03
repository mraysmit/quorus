# Lists the agents registered with a controller.
# The default is the single-controller development topology (host port 8080). In the multi-node
# topologies pass any controller, for example -BaseUrl http://localhost:8081.
param(
    [string]$BaseUrl = "http://localhost:8080"
)

$response = Invoke-RestMethod -Uri "$BaseUrl/api/v1/agents"
foreach ($agent in $response.agents) {
    Write-Host "Agent: $($agent.agentId)"
    Write-Host "  Tenant: $($agent.tenantId)"
    Write-Host "  Status: $($agent.status)"
    Write-Host "  Healthy: $($agent.healthy)"
    Write-Host "  Available: $($agent.available)"
    Write-Host "  Last Heartbeat: $($agent.lastHeartbeat)"
    Write-Host ""
}
Write-Host "Total: $($response.count)"
