# PowerShell script to send heartbeat with current timestamp
param(
    [int]$SequenceNumber = 3,
    [string]$AgentId = "test-agent-002"
)

# Get current timestamp in ISO format
$timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

# Create JSON payload
$json = @{
    agentId = $AgentId
    timestamp = $timestamp
    sequenceNumber = $SequenceNumber
    status = "active"
    currentJobs = 0
    availableCapacity = 5
    transferMetrics = @{
        active = 0
        completed = 50
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

Write-Host "Sending heartbeat with timestamp: $timestamp, sequence: $SequenceNumber"

# Send the request
$response = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/agents/heartbeat" -Method POST -Body $json -ContentType "application/json"

Write-Host "Response: $($response | ConvertTo-Json -Compress)"
