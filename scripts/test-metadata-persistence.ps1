# Metadata Persistence Test Script
# This script proves that metadata is transferred during leader changes
# by testing with the live 5-node Raft cluster

param(
    [switch]$Verbose = $false
)

Write-Host "=== QUORUS METADATA PERSISTENCE TEST ===" -ForegroundColor Cyan
Write-Host ""

# Test configuration
$CONTROLLERS = @(
    @{Name="controller1"; Port=8081},
    @{Name="controller2"; Port=8082},
    @{Name="controller3"; Port=8083},
    @{Name="controller4"; Port=8084},
    @{Name="controller5"; Port=8085}
)

$TEST_JOBS = @(
    @{Id="test-job-001"; Description="Metadata persistence test job 1"},
    @{Id="test-job-002"; Description="Metadata persistence test job 2"},
    @{Id="test-job-003"; Description="Metadata persistence test job 3"}
)

function Write-TestStep {
    param([string]$Message)
    Write-Host "🔍 $Message" -ForegroundColor Yellow
}

function Write-Success {
    param([string]$Message)
    Write-Host "✅ $Message" -ForegroundColor Green
}

function Write-Error {
    param([string]$Message)
    Write-Host "❌ $Message" -ForegroundColor Red
}

function Get-ClusterStatus {
    Write-TestStep "Getting cluster status..."
    $status = @()
    
    foreach ($controller in $CONTROLLERS) {
        try {
            $response = Invoke-RestMethod -Uri "http://localhost:$($controller.Port)/health" -Method GET -TimeoutSec 5
            $raftInfo = $response.checks.raft
            $status += @{
                Name = $controller.Name
                Port = $controller.Port
                State = $raftInfo.state
                NodeId = $raftInfo.nodeId
                Term = $raftInfo.currentTerm
                Available = $true
            }
        } catch {
            $status += @{
                Name = $controller.Name
                Port = $controller.Port
                State = "UNAVAILABLE"
                Available = $false
            }
        }
    }
    
    return $status
}

function Find-Leader {
    param([array]$ClusterStatus)
    
    $leader = $ClusterStatus | Where-Object { $_.State -eq "LEADER" -and $_.Available }
    return $leader
}

function Submit-TestMetadata {
    param(
        [string]$LeaderPort,
        [string]$JobId,
        [string]$Description
    )
    
    $metadata = @{
        jobId = $JobId
        sourceUri = "file:///test/source/$JobId.txt"
        destinationPath = "/test/dest/$JobId.txt"
        description = $Description
        testTimestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        testType = "metadata-persistence"
    }
    
    try {
        # Submit via system metadata endpoint (simulating transfer job metadata)
        $response = Invoke-RestMethod -Uri "http://localhost:$LeaderPort/system/metadata" -Method POST -Body ($metadata | ConvertTo-Json) -ContentType "application/json" -TimeoutSec 10
        return $true
    } catch {
        Write-Error "Failed to submit metadata to leader on port $LeaderPort : $($_.Exception.Message)"
        return $false
    }
}

function Verify-MetadataOnNode {
    param(
        [string]$NodePort,
        [string]$JobId
    )
    
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:$NodePort/system/metadata/$JobId" -Method GET -TimeoutSec 5
        return $response -ne $null
    } catch {
        if ($Verbose) {
            Write-Host "  Metadata check failed for job $JobId on port $NodePort : $($_.Exception.Message)" -ForegroundColor Gray
        }
        return $false
    }
}

function Test-BasicMetadataReplication {
    Write-Host ""
    Write-Host "=== TEST 1: Basic Metadata Replication ===" -ForegroundColor Magenta
    
    # Get initial cluster status
    $clusterStatus = Get-ClusterStatus
    $leader = Find-Leader -ClusterStatus $clusterStatus
    
    if (-not $leader) {
        Write-Error "No leader found in cluster"
        return $false
    }
    
    Write-Success "Found leader: $($leader.Name) on port $($leader.Port)"
    
    # Submit test metadata to leader
    $testJob = $TEST_JOBS[0]
    Write-TestStep "Submitting test metadata: $($testJob.Id)"
    
    $submitted = Submit-TestMetadata -LeaderPort $leader.Port -JobId $testJob.Id -Description $testJob.Description
    if (-not $submitted) {
        Write-Error "Failed to submit test metadata"
        return $false
    }
    
    Write-Success "Metadata submitted successfully"
    
    # Wait for replication
    Write-TestStep "Waiting for replication (5 seconds)..."
    Start-Sleep -Seconds 5
    
    # Verify metadata on all nodes
    Write-TestStep "Verifying metadata replication across all nodes..."
    $replicationSuccess = $true
    
    foreach ($controller in $CONTROLLERS) {
        if ($controller.Port -eq $leader.Port) {
            Write-Host "  ✓ $($controller.Name) (LEADER) - skipping verification" -ForegroundColor Green
            continue
        }
        
        $hasMetadata = Verify-MetadataOnNode -NodePort $controller.Port -JobId $testJob.Id
        if ($hasMetadata) {
            Write-Host "  ✓ $($controller.Name) has metadata for $($testJob.Id)" -ForegroundColor Green
        } else {
            Write-Host "  ❌ $($controller.Name) missing metadata for $($testJob.Id)" -ForegroundColor Red
            $replicationSuccess = $false
        }
    }
    
    if ($replicationSuccess) {
        Write-Success "Basic metadata replication test PASSED"
        return $true
    } else {
        Write-Error "Basic metadata replication test FAILED"
        return $false
    }
}

function Test-LeaderChangeMetadataPersistence {
    Write-Host ""
    Write-Host "=== TEST 2: Leader Change Metadata Persistence ===" -ForegroundColor Magenta
    
    # Get initial cluster status
    $clusterStatus = Get-ClusterStatus
    $originalLeader = Find-Leader -ClusterStatus $clusterStatus
    
    if (-not $originalLeader) {
        Write-Error "No leader found in cluster"
        return $false
    }
    
    Write-Success "Original leader: $($originalLeader.Name) on port $($originalLeader.Port)"
    
    # Submit multiple metadata entries to original leader
    Write-TestStep "Submitting multiple metadata entries to original leader..."
    $submittedJobs = @()
    
    for ($i = 1; $i -lt $TEST_JOBS.Count; $i++) {
        $job = $TEST_JOBS[$i]
        $submitted = Submit-TestMetadata -LeaderPort $originalLeader.Port -JobId $job.Id -Description $job.Description
        if ($submitted) {
            $submittedJobs += $job
            Write-Host "  ✓ Submitted $($job.Id)" -ForegroundColor Green
        } else {
            Write-Host "  ❌ Failed to submit $($job.Id)" -ForegroundColor Red
        }
    }
    
    if ($submittedJobs.Count -eq 0) {
        Write-Error "No metadata was successfully submitted"
        return $false
    }
    
    # Wait for replication
    Write-TestStep "Waiting for initial replication (3 seconds)..."
    Start-Sleep -Seconds 3
    
    # Force leader change by stopping original leader
    Write-TestStep "Forcing leader change by stopping $($originalLeader.Name)..."
    try {
        docker stop "quorus-$($originalLeader.Name.ToLower())" | Out-Null
        Write-Success "Stopped $($originalLeader.Name)"
    } catch {
        Write-Error "Failed to stop $($originalLeader.Name): $($_.Exception.Message)"
        return $false
    }
    
    # Wait for new leader election
    Write-TestStep "Waiting for new leader election (10 seconds)..."
    Start-Sleep -Seconds 10
    
    # Find new leader
    $newClusterStatus = Get-ClusterStatus
    $newLeader = Find-Leader -ClusterStatus $newClusterStatus
    
    if (-not $newLeader) {
        Write-Error "No new leader elected after original leader failure"
        # Restart original leader for cleanup
        docker start "quorus-$($originalLeader.Name.ToLower())" | Out-Null
        return $false
    }
    
    if ($newLeader.Name -eq $originalLeader.Name) {
        Write-Error "New leader is the same as original leader (unexpected)"
        return $false
    }
    
    Write-Success "New leader elected: $($newLeader.Name) on port $($newLeader.Port)"
    
    # Verify metadata persistence on remaining nodes
    Write-TestStep "Verifying metadata persistence after leader change..."
    $persistenceSuccess = $true
    
    $remainingControllers = $CONTROLLERS | Where-Object { $_.Name -ne $originalLeader.Name }
    
    foreach ($controller in $remainingControllers) {
        Write-Host "  Checking $($controller.Name)..." -ForegroundColor Cyan
        
        foreach ($job in $submittedJobs) {
            $hasMetadata = Verify-MetadataOnNode -NodePort $controller.Port -JobId $job.Id
            if ($hasMetadata) {
                Write-Host "    ✓ Has metadata for $($job.Id)" -ForegroundColor Green
            } else {
                Write-Host "    ❌ Missing metadata for $($job.Id)" -ForegroundColor Red
                $persistenceSuccess = $false
            }
        }
    }
    
    # Test new leader functionality by submitting new metadata
    Write-TestStep "Testing new leader functionality..."
    $newJobId = "post-leader-change-job"
    $newJobSubmitted = Submit-TestMetadata -LeaderPort $newLeader.Port -JobId $newJobId -Description "Post leader change test"
    
    if ($newJobSubmitted) {
        Write-Success "New leader successfully accepted new metadata"
    } else {
        Write-Error "New leader failed to accept new metadata"
        $persistenceSuccess = $false
    }
    
    # Restart original leader for cleanup
    Write-TestStep "Restarting original leader for cleanup..."
    try {
        docker start "quorus-$($originalLeader.Name.ToLower())" | Out-Null
        Write-Success "Restarted $($originalLeader.Name)"
    } catch {
        Write-Host "Warning: Failed to restart $($originalLeader.Name)" -ForegroundColor Yellow
    }
    
    if ($persistenceSuccess) {
        Write-Success "Leader change metadata persistence test PASSED"
        return $true
    } else {
        Write-Error "Leader change metadata persistence test FAILED"
        return $false
    }
}

function Show-FinalClusterStatus {
    Write-Host ""
    Write-Host "=== FINAL CLUSTER STATUS ===" -ForegroundColor Magenta
    
    $finalStatus = Get-ClusterStatus
    
    Write-Host "Node Status:" -ForegroundColor Cyan
    foreach ($node in $finalStatus) {
        if ($node.Available) {
            $stateColor = if ($node.State -eq "LEADER") { "Green" } else { "Yellow" }
            Write-Host "  $($node.Name): $($node.State) (Term: $($node.Term))" -ForegroundColor $stateColor
        } else {
            Write-Host "  $($node.Name): UNAVAILABLE" -ForegroundColor Red
        }
    }
}

# Main test execution
try {
    # Verify cluster is running
    Write-TestStep "Verifying cluster is running..."
    $initialStatus = Get-ClusterStatus
    $availableNodes = ($initialStatus | Where-Object { $_.Available }).Count
    
    if ($availableNodes -lt 3) {
        Write-Error "Insufficient nodes available ($availableNodes/5). Need at least 3 nodes for testing."
        exit 1
    }
    
    Write-Success "$availableNodes/5 nodes are available"
    
    # Run tests
    $test1Result = Test-BasicMetadataReplication
    $test2Result = Test-LeaderChangeMetadataPersistence
    
    # Show final status
    Show-FinalClusterStatus
    
    # Summary
    Write-Host ""
    Write-Host "=== TEST SUMMARY ===" -ForegroundColor Cyan
    Write-Host "Basic Metadata Replication: $(if ($test1Result) { '✅ PASSED' } else { '❌ FAILED' })" -ForegroundColor $(if ($test1Result) { 'Green' } else { 'Red' })
    Write-Host "Leader Change Persistence: $(if ($test2Result) { '✅ PASSED' } else { '❌ FAILED' })" -ForegroundColor $(if ($test2Result) { 'Green' } else { 'Red' })
    
    if ($test1Result -and $test2Result) {
        Write-Host ""
        Write-Success "🎉 ALL TESTS PASSED - Metadata persistence during leader changes is PROVEN!"
        exit 0
    } else {
        Write-Host ""
        Write-Error "❌ SOME TESTS FAILED - Metadata persistence needs investigation"
        exit 1
    }
    
} catch {
    Write-Error "Test execution failed: $($_.Exception.Message)"
    Show-FinalClusterStatus
    exit 1
}
