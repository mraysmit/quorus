# Test File Transfers in Quorus Network
# This script demonstrates various file transfer scenarios

param(
    [string]$TestType = "all"
)

$ErrorActionPreference = "Stop"

Write-Host "=== Quorus Network Transfer Tests ===" -ForegroundColor Green
Write-Host "Testing file transfers across the network" -ForegroundColor Green
Write-Host ""

# Test configuration
$apiUrl = "http://localhost:8080/api/v1"
$httpServerUrl = "http://localhost:8090"
$ftpServerUrl = "ftp://localhost:21"
$sftpServerUrl = "sftp://localhost:2222"

function Test-ServiceHealth {
    param([string]$Url, [string]$Name)
    
    try {
        $response = Invoke-RestMethod -Uri "$Url/health" -TimeoutSec 5
        Write-Host "  ✓ $Name: Healthy" -ForegroundColor Green
        return $true
    } catch {
        Write-Host "  ✗ $Name: Not responding" -ForegroundColor Red
        return $false
    }
}

function Test-AgentRegistration {
    Write-Host "Testing agent registration..." -ForegroundColor Cyan
    
    try {
        $agents = Invoke-RestMethod -Uri "$apiUrl/agents" -TimeoutSec 10
        
        if ($agents.Count -gt 0) {
            Write-Host "  ✓ Found $($agents.Count) registered agents:" -ForegroundColor Green
            foreach ($agent in $agents) {
                $status = if ($agent.status -eq "HEALTHY") { "✓" } else { "⚠" }
                Write-Host "    $status $($agent.agentId) - $($agent.region) ($($agent.status))" -ForegroundColor $(if ($agent.status -eq "HEALTHY") { "Green" } else { "Yellow" })
            }
            return $true
        } else {
            Write-Host "  ⚠ No agents registered" -ForegroundColor Yellow
            return $false
        }
    } catch {
        Write-Host "  ✗ Failed to check agent registration: $_" -ForegroundColor Red
        return $false
    }
}

function Test-FileAvailability {
    Write-Host "Testing file server availability..." -ForegroundColor Cyan
    
    # Test HTTP server file listing
    try {
        $httpResponse = Invoke-WebRequest -Uri "$httpServerUrl/shared/" -TimeoutSec 5
        if ($httpResponse.StatusCode -eq 200) {
            Write-Host "  ✓ HTTP Server: File listing available" -ForegroundColor Green
        }
    } catch {
        Write-Host "  ⚠ HTTP Server: File listing not available" -ForegroundColor Yellow
    }
    
    # Test specific test files
    $testFiles = @(
        "$httpServerUrl/shared/random-1mb.bin",
        "$httpServerUrl/shared/random-10mb.bin",
        "$httpServerUrl/shared/timestamp.txt"
    )
    
    foreach ($file in $testFiles) {
        try {
            $response = Invoke-WebRequest -Uri $file -Method Head -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                $fileName = Split-Path $file -Leaf
                $size = if ($response.Headers.'Content-Length') { 
                    [math]::Round($response.Headers.'Content-Length'[0] / 1MB, 2) 
                } else { "Unknown" }
                Write-Host "  ✓ $fileName available (${size}MB)" -ForegroundColor Green
            }
        } catch {
            $fileName = Split-Path $file -Leaf
            Write-Host "  ⚠ $fileName not available" -ForegroundColor Yellow
        }
    }
}

function Submit-TransferRequest {
    param(
        [string]$SourceUri,
        [string]$DestinationPath,
        [string]$Protocol,
        [string]$Description
    )
    
    Write-Host "  Testing: $Description" -ForegroundColor Cyan
    Write-Host "    Source: $SourceUri" -ForegroundColor Gray
    Write-Host "    Destination: $DestinationPath" -ForegroundColor Gray
    Write-Host "    Protocol: $Protocol" -ForegroundColor Gray
    
    $transferRequest = @{
        sourceUri = $SourceUri
        destinationPath = $DestinationPath
        protocol = $Protocol
        metadata = @{
            testType = "automated"
            description = $Description
        }
    } | ConvertTo-Json
    
    try {
        $response = Invoke-RestMethod -Uri "$apiUrl/transfers" -Method POST -Body $transferRequest -ContentType "application/json" -TimeoutSec 10
        
        if ($response.success) {
            Write-Host "    ✓ Transfer submitted: $($response.transferId)" -ForegroundColor Green
            return $response.transferId
        } else {
            Write-Host "    ✗ Transfer failed: $($response.message)" -ForegroundColor Red
            return $null
        }
    } catch {
        Write-Host "    ✗ Transfer submission failed: $_" -ForegroundColor Red
        return $null
    }
}

function Test-TransferScenarios {
    Write-Host "Testing transfer scenarios..." -ForegroundColor Cyan
    
    $scenarios = @(
        @{
            Source = "$httpServerUrl/shared/timestamp.txt"
            Destination = "/tmp/downloads/http-timestamp.txt"
            Protocol = "http"
            Description = "HTTP small file transfer"
        },
        @{
            Source = "$httpServerUrl/shared/random-1mb.bin"
            Destination = "/tmp/downloads/http-1mb.bin"
            Protocol = "http"
            Description = "HTTP 1MB file transfer"
        },
        @{
            Source = "ftp://testuser:testpass@ftp-server/shared/timestamp.txt"
            Destination = "/tmp/downloads/ftp-timestamp.txt"
            Protocol = "ftp"
            Description = "FTP small file transfer"
        },
        @{
            Source = "sftp://testuser:testpass@sftp-server/shared/timestamp.txt"
            Destination = "/tmp/downloads/sftp-timestamp.txt"
            Protocol = "sftp"
            Description = "SFTP small file transfer"
        }
    )
    
    $transferIds = @()
    
    foreach ($scenario in $scenarios) {
        $transferId = Submit-TransferRequest -SourceUri $scenario.Source -DestinationPath $scenario.Destination -Protocol $scenario.Protocol -Description $scenario.Description
        if ($transferId) {
            $transferIds += $transferId
        }
        Start-Sleep -Seconds 2
    }
    
    if ($transferIds.Count -gt 0) {
        Write-Host ""
        Write-Host "Monitoring transfer progress..." -ForegroundColor Cyan
        
        # Monitor transfers for up to 2 minutes
        $timeout = 120
        $elapsed = 0
        
        while ($elapsed -lt $timeout) {
            $allComplete = $true
            
            foreach ($transferId in $transferIds) {
                try {
                    $status = Invoke-RestMethod -Uri "$apiUrl/transfers/$transferId" -TimeoutSec 5
                    
                    switch ($status.status) {
                        "COMPLETED" { 
                            Write-Host "    ✓ $transferId: Completed" -ForegroundColor Green 
                        }
                        "FAILED" { 
                            Write-Host "    ✗ $transferId: Failed - $($status.errorMessage)" -ForegroundColor Red 
                        }
                        "IN_PROGRESS" { 
                            Write-Host "    ⏳ $transferId: In Progress ($($status.progress)%)" -ForegroundColor Yellow
                            $allComplete = $false
                        }
                        "PENDING" { 
                            Write-Host "    ⏳ $transferId: Pending" -ForegroundColor Yellow
                            $allComplete = $false
                        }
                        default { 
                            Write-Host "    ? $transferId: $($status.status)" -ForegroundColor Gray
                            $allComplete = $false
                        }
                    }
                } catch {
                    Write-Host "    ⚠ $transferId: Status check failed" -ForegroundColor Yellow
                }
            }
            
            if ($allComplete) {
                break
            }
            
            Start-Sleep -Seconds 5
            $elapsed += 5
        }
        
        if ($elapsed -ge $timeout) {
            Write-Host "  ⚠ Transfer monitoring timed out after $timeout seconds" -ForegroundColor Yellow
        }
    }
}

# Main test execution
Write-Host "Checking service health..." -ForegroundColor Cyan
$apiHealthy = Test-ServiceHealth -Url $apiUrl -Name "API Service"
$httpHealthy = Test-ServiceHealth -Url $httpServerUrl -Name "HTTP Server"

if (-not $apiHealthy) {
    Write-Host "API service is not healthy. Cannot proceed with tests." -ForegroundColor Red
    exit 1
}

Write-Host ""

# Test agent registration
$agentsRegistered = Test-AgentRegistration
Write-Host ""

# Test file availability
Test-FileAvailability
Write-Host ""

# Test transfer scenarios
if ($agentsRegistered) {
    Test-TransferScenarios
} else {
    Write-Host "Skipping transfer tests - no agents registered" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Transfer Tests Complete ===" -ForegroundColor Green
Write-Host ""
Write-Host "Additional Commands:" -ForegroundColor Yellow
Write-Host "  - View logs: docker-compose -f compose/docker-compose-full-network.yml logs -f"
Write-Host "  - Check agents: .\scripts\check-agents.ps1"
Write-Host "  - Stop environment: docker-compose -f compose/docker-compose-full-network.yml down"
