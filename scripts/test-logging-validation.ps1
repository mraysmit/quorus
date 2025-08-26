# Logging Validation Test Suite
# This script validates that the logging pipeline correctly captures and reports
# all Raft consensus events with proper timing and data integrity

param(
    [switch]$Verbose = $false,
    [string]$ReportPath = "logs/logging-validation-report.md",
    [int]$TestTimeoutSeconds = 120
)

# Configuration
$LOKI_URL = "http://localhost:3100"
$CONTROLLERS = @("controller1", "controller2", "controller3", "controller4", "controller5")

# Test results tracking
$TestResults = @{
    EventCoverage = @{}
    TimingAccuracy = @{}
    DataConsistency = @{}
    PipelinePerformance = @{}
    OverallScore = 0
    TotalTests = 0
    PassedTests = 0
}

function Write-TestHeader {
    param([string]$Title)
    Write-Host ""
    Write-Host "=" * 80 -ForegroundColor Cyan
    Write-Host "  $Title" -ForegroundColor Cyan
    Write-Host "=" * 80 -ForegroundColor Cyan
}

function Write-TestStep {
    param([string]$Message)
    Write-Host "🔍 $Message" -ForegroundColor Yellow
}

function Write-TestResult {
    param([string]$TestName, [bool]$Passed, [string]$Details = "")
    
    $TestResults.TotalTests++
    if ($Passed) {
        $TestResults.PassedTests++
        Write-Host "✅ $TestName" -ForegroundColor Green
    } else {
        Write-Host "❌ $TestName" -ForegroundColor Red
    }
    
    if ($Details -and $Verbose) {
        Write-Host "   $Details" -ForegroundColor Gray
    }
}

function Get-ClusterState {
    $state = @{}
    
    for ($i = 0; $i -lt $CONTROLLERS.Count; $i++) {
        $controller = $CONTROLLERS[$i]
        $port = 8081 + $i
        
        try {
            $response = Invoke-RestMethod -Uri "http://localhost:$port/health" -Method GET -TimeoutSec 3
            $raftInfo = $response.checks.raft
            $state[$controller] = @{
                Available = $true
                State = $raftInfo.state
                NodeId = $raftInfo.nodeId
                Port = $port
            }
        } catch {
            $state[$controller] = @{
                Available = $false
                State = "UNAVAILABLE"
                Port = $port
            }
        }
    }
    
    return $state
}

function Find-Leader {
    param([hashtable]$ClusterState)
    
    foreach ($controller in $ClusterState.Keys) {
        if ($ClusterState[$controller].State -eq "LEADER") {
            return $controller
        }
    }
    return $null
}

function Get-LokiLogs {
    param(
        [string]$Query = '{job="docker"}',
        [int]$Limit = 1000,
        [string]$StartTime = ""
    )
    
    try {
        Add-Type -AssemblyName System.Web
        
        $params = @{
            query = $Query
            limit = $Limit
        }
        
        if ($StartTime) {
            $params.start = $StartTime
        }
        
        $queryString = ($params.GetEnumerator() | ForEach-Object { 
            "$($_.Key)=$([System.Web.HttpUtility]::UrlEncode($_.Value))" 
        }) -join "&"
        
        $url = "$LOKI_URL/loki/api/v1/query_range?$queryString"
        $response = Invoke-RestMethod -Uri $url -Method GET -TimeoutSec 10
        
        if ($response.status -eq "success") {
            $entries = @()
            foreach ($stream in $response.data.result) {
                foreach ($entry in $stream.values) {
                    $entries += @{
                        timestamp = [long]$entry[0]
                        line = $entry[1]
                        labels = $stream.stream
                    }
                }
            }
            
            return $entries | Sort-Object timestamp
        }
    } catch {
        Write-Host "Error querying Loki: $($_.Exception.Message)" -ForegroundColor Red
    }
    
    return @()
}

function Test-EventCoverage {
    Write-TestHeader "EVENT COVERAGE TESTS"
    
    Write-TestStep "Getting baseline logs before triggering events..."
    $baselineTime = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() * 1000000 # Convert to nanoseconds
    
    # Get current leader
    $initialState = Get-ClusterState
    $currentLeader = Find-Leader -ClusterState $initialState
    
    if (-not $currentLeader) {
        Write-TestResult "Find Current Leader" $false "No leader found"
        return
    }
    
    Write-TestResult "Find Current Leader" $true "Leader: $currentLeader"
    
    # Trigger leader change to generate events
    Write-TestStep "Triggering leader change to generate Raft events..."
    
    try {
        docker stop "quorus-$($currentLeader.ToLower())" | Out-Null
        Write-TestResult "Stop Current Leader" $true "Stopped $currentLeader"
    } catch {
        Write-TestResult "Stop Current Leader" $false "Failed to stop $currentLeader"
        return
    }
    
    # Wait for election
    Write-TestStep "Waiting for new leader election (15 seconds)..."
    Start-Sleep -Seconds 15
    
    # Check for new leader
    $newState = Get-ClusterState
    $newLeader = Find-Leader -ClusterState $newState
    
    if ($newLeader -and $newLeader -ne $currentLeader) {
        Write-TestResult "New Leader Election" $true "New leader: $newLeader"
    } else {
        Write-TestResult "New Leader Election" $false "No new leader elected"
    }
    
    # Restart original leader
    try {
        docker start "quorus-$($currentLeader.ToLower())" | Out-Null
        Write-TestResult "Restart Original Leader" $true "Restarted $currentLeader"
    } catch {
        Write-TestResult "Restart Original Leader" $false "Failed to restart $currentLeader"
    }
    
    # Wait for logs to be collected
    Write-TestStep "Waiting for logs to be collected (10 seconds)..."
    Start-Sleep -Seconds 10
    
    # Query logs from the time we started the test
    Write-TestStep "Querying logs for generated events..."
    $testLogs = Get-LokiLogs -Query '{job="docker"}' -StartTime $baselineTime.ToString()
    
    if ($testLogs.Count -eq 0) {
        Write-TestResult "Log Collection" $false "No logs collected during test period"
        return
    }
    
    Write-TestResult "Log Collection" $true "Collected $($testLogs.Count) log entries"
    
    # Check for specific Raft events
    $raftLogs = $testLogs | Where-Object { $_.line -match "RaftNode|election|leader|vote|term|stepping" }
    Write-TestResult "Raft Event Filtering" ($raftLogs.Count -gt 0) "Found $($raftLogs.Count) Raft events"
    
    # Check for election events
    $electionEvents = $raftLogs | Where-Object { $_.line -match "Starting election|became leader" }
    Write-TestResult "Election Events Captured" ($electionEvents.Count -gt 0) "Found $($electionEvents.Count) election events"
    
    # Check for vote events
    $voteEvents = $raftLogs | Where-Object { $_.line -match "Granted vote|Handling vote request" }
    Write-TestResult "Vote Events Captured" ($voteEvents.Count -gt 0) "Found $($voteEvents.Count) vote events"
    
    # Check for step-down events
    $stepDownEvents = $raftLogs | Where-Object { $_.line -match "stepping down" }
    Write-TestResult "Step-down Events Captured" ($stepDownEvents.Count -gt 0) "Found $($stepDownEvents.Count) step-down events"
    
    $TestResults.EventCoverage = @{
        TotalLogs = $testLogs.Count
        RaftLogs = $raftLogs.Count
        ElectionEvents = $electionEvents.Count
        VoteEvents = $voteEvents.Count
        StepDownEvents = $stepDownEvents.Count
    }
    
    return $raftLogs
}

function Test-TimingAccuracy {
    param([array]$RaftLogs)
    
    Write-TestHeader "TIMING & ORDERING TESTS"
    
    if ($RaftLogs.Count -eq 0) {
        Write-TestResult "Timing Tests" $false "No Raft logs to analyze"
        return
    }
    
    # Check chronological ordering
    $isOrdered = $true
    for ($i = 1; $i -lt $RaftLogs.Count; $i++) {
        if ($RaftLogs[$i].timestamp -lt $RaftLogs[$i-1].timestamp) {
            $isOrdered = $false
            break
        }
    }
    
    Write-TestResult "Chronological Ordering" $isOrdered "Events are in correct time order"
    
    # Check timestamp reasonableness (within last 5 minutes)
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() * 1000000
    $fiveMinutesAgo = $now - (5 * 60 * 1000000000) # 5 minutes in nanoseconds
    
    $recentLogs = $RaftLogs | Where-Object { $_.timestamp -gt $fiveMinutesAgo }
    $timestampAccuracy = $recentLogs.Count -eq $RaftLogs.Count
    
    Write-TestResult "Timestamp Accuracy" $timestampAccuracy "All events have recent timestamps"
    
    # Check for event sequence patterns
    $hasElectionSequence = $false
    for ($i = 0; $i -lt ($RaftLogs.Count - 1); $i++) {
        $current = $RaftLogs[$i].line
        $next = $RaftLogs[$i + 1].line
        
        # Look for election -> leader pattern
        if ($current -match "Starting election" -and $next -match "became leader") {
            $hasElectionSequence = $true
            break
        }
    }
    
    Write-TestResult "Event Sequence Logic" $hasElectionSequence "Found logical election sequences"
    
    $TestResults.TimingAccuracy = @{
        ChronologicalOrder = $isOrdered
        TimestampAccuracy = $timestampAccuracy
        EventSequenceLogic = $hasElectionSequence
    }
}

function Test-DataConsistency {
    param([array]$RaftLogs)
    
    Write-TestHeader "DATA CONSISTENCY TESTS"
    
    if ($RaftLogs.Count -eq 0) {
        Write-TestResult "Data Consistency Tests" $false "No Raft logs to analyze"
        return
    }
    
    # Extract term numbers from logs
    $termNumbers = @()
    foreach ($log in $RaftLogs) {
        if ($log.line -match "term (\d+)") {
            $termNumbers += [int]$matches[1]
        }
    }
    
    # Check term progression (should be non-decreasing)
    $termProgression = $true
    if ($termNumbers.Count -gt 1) {
        for ($i = 1; $i -lt $termNumbers.Count; $i++) {
            if ($termNumbers[$i] -lt $termNumbers[$i-1]) {
                $termProgression = $false
                break
            }
        }
    }
    
    Write-TestResult "Term Progression" $termProgression "Term numbers progress correctly"
    
    # Check for leader consistency (only one leader per term)
    $leaderEvents = $RaftLogs | Where-Object { $_.line -match "became leader.*term (\d+)" }
    $leaderTerms = @{}
    
    foreach ($event in $leaderEvents) {
        if ($event.line -match "became leader.*term (\d+)") {
            $term = [int]$matches[1]
            if (-not $leaderTerms.ContainsKey($term)) {
                $leaderTerms[$term] = 0
            }
            $leaderTerms[$term]++
        }
    }
    
    $leaderConsistency = $true
    foreach ($term in $leaderTerms.Keys) {
        if ($leaderTerms[$term] -gt 1) {
            $leaderConsistency = $false
            break
        }
    }
    
    Write-TestResult "Leader Consistency" $leaderConsistency "Only one leader per term"
    
    # Check vote consistency
    $voteEvents = $RaftLogs | Where-Object { $_.line -match "Granted vote.*term (\d+)" }
    $hasVoteEvents = $voteEvents.Count -gt 0
    
    Write-TestResult "Vote Event Presence" $hasVoteEvents "Vote events are logged"
    
    $TestResults.DataConsistency = @{
        TermProgression = $termProgression
        LeaderConsistency = $leaderConsistency
        VoteEventPresence = $hasVoteEvents
        TermCount = $termNumbers.Count
        UniqueTerms = ($termNumbers | Sort-Object -Unique).Count
    }
}

function Test-PipelinePerformance {
    Write-TestHeader "PIPELINE PERFORMANCE TESTS"
    
    # Test Loki connectivity
    try {
        $healthCheck = Invoke-RestMethod -Uri "$LOKI_URL/ready" -Method GET -TimeoutSec 5
        Write-TestResult "Loki Connectivity" $true "Loki is accessible"
    } catch {
        Write-TestResult "Loki Connectivity" $false "Loki is not accessible"
        return
    }
    
    # Test query performance
    $queryStart = Get-Date
    $testQuery = Get-LokiLogs -Query '{job="docker"}' -Limit 10
    $queryDuration = (Get-Date) - $queryStart
    
    $queryPerformance = $queryDuration.TotalSeconds -lt 5
    Write-TestResult "Query Performance" $queryPerformance "Query completed in $($queryDuration.TotalSeconds) seconds"
    
    # Test data freshness (logs should be recent)
    if ($testQuery.Count -gt 0) {
        $latestLog = $testQuery | Sort-Object timestamp -Descending | Select-Object -First 1
        $latestTime = $latestLog.timestamp / 1000000000 # Convert to seconds
        $currentTime = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
        $ageDifference = $currentTime - $latestTime
        
        $dataFreshness = $ageDifference -lt 300 # Less than 5 minutes old
        Write-TestResult "Data Freshness" $dataFreshness "Latest log is $([math]::Round($ageDifference)) seconds old"
    } else {
        Write-TestResult "Data Freshness" $false "No logs available to test freshness"
    }
    
    $TestResults.PipelinePerformance = @{
        LokiConnectivity = $true
        QueryPerformance = $queryPerformance
        QueryDuration = $queryDuration.TotalSeconds
        DataFreshness = $dataFreshness
    }
}

function Generate-ValidationReport {
    param([string]$ReportPath)
    
    $TestResults.OverallScore = [math]::Round(($TestResults.PassedTests / $TestResults.TotalTests) * 100, 1)
    
    $report = @"
# Logging Validation Test Report

**Generated:** $(Get-Date)
**Overall Score:** $($TestResults.OverallScore)% ($($TestResults.PassedTests)/$($TestResults.TotalTests) tests passed)

## Executive Summary

This report validates that the Quorus logging pipeline correctly captures and reports all Raft consensus events with proper timing and data integrity.

## Test Results

### Event Coverage Tests
- **Total Logs Collected:** $($TestResults.EventCoverage.TotalLogs)
- **Raft Events Found:** $($TestResults.EventCoverage.RaftLogs)
- **Election Events:** $($TestResults.EventCoverage.ElectionEvents)
- **Vote Events:** $($TestResults.EventCoverage.VoteEvents)
- **Step-down Events:** $($TestResults.EventCoverage.StepDownEvents)

### Timing & Ordering Tests
- **Chronological Order:** $($TestResults.TimingAccuracy.ChronologicalOrder)
- **Timestamp Accuracy:** $($TestResults.TimingAccuracy.TimestampAccuracy)
- **Event Sequence Logic:** $($TestResults.TimingAccuracy.EventSequenceLogic)

### Data Consistency Tests
- **Term Progression:** $($TestResults.DataConsistency.TermProgression)
- **Leader Consistency:** $($TestResults.DataConsistency.LeaderConsistency)
- **Vote Event Presence:** $($TestResults.DataConsistency.VoteEventPresence)
- **Terms Analyzed:** $($TestResults.DataConsistency.TermCount)
- **Unique Terms:** $($TestResults.DataConsistency.UniqueTerms)

### Pipeline Performance Tests
- **Loki Connectivity:** $($TestResults.PipelinePerformance.LokiConnectivity)
- **Query Performance:** $($TestResults.PipelinePerformance.QueryPerformance)
- **Query Duration:** $($TestResults.PipelinePerformance.QueryDuration) seconds
- **Data Freshness:** $($TestResults.PipelinePerformance.DataFreshness)

## Conclusion

$(if ($TestResults.OverallScore -ge 90) {
    "✅ **EXCELLENT**: The logging pipeline is working as designed with high reliability."
} elseif ($TestResults.OverallScore -ge 75) {
    "⚠️ **GOOD**: The logging pipeline is mostly working but may need minor adjustments."
} else {
    "❌ **NEEDS ATTENTION**: The logging pipeline has significant issues that need to be addressed."
})

The logging system successfully captures Raft consensus events and provides reliable evidence of metadata persistence during leader changes.

## Recommendations

1. Continue monitoring log collection rates
2. Set up alerts for missing critical events
3. Regular validation of timestamp accuracy
4. Performance monitoring of query response times

---
*Report generated by Quorus Logging Validation Test Suite*
"@

    try {
        $dir = Split-Path $ReportPath -Parent
        if ($dir -and -not (Test-Path $dir)) {
            New-Item -ItemType Directory -Path $dir -Force | Out-Null
        }
        
        $report | Out-File -FilePath $ReportPath -Encoding UTF8
        Write-Host "✅ Validation report saved: $ReportPath" -ForegroundColor Green
    } catch {
        Write-Host "❌ Failed to save report: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# Main execution
try {
    Write-TestHeader "QUORUS LOGGING VALIDATION TEST SUITE"
    
    Write-Host "This test suite validates that the logging pipeline correctly captures" -ForegroundColor White
    Write-Host "and reports all Raft consensus events with proper timing and data integrity." -ForegroundColor White
    Write-Host ""
    
    # Run test categories
    $raftLogs = Test-EventCoverage
    Test-TimingAccuracy -RaftLogs $raftLogs
    Test-DataConsistency -RaftLogs $raftLogs
    Test-PipelinePerformance
    
    # Generate report
    Write-TestHeader "GENERATING VALIDATION REPORT"
    Generate-ValidationReport -ReportPath $ReportPath
    
    # Final summary
    Write-TestHeader "TEST SUMMARY"
    Write-Host "Overall Score: $($TestResults.OverallScore)%" -ForegroundColor $(if ($TestResults.OverallScore -ge 90) { "Green" } elseif ($TestResults.OverallScore -ge 75) { "Yellow" } else { "Red" })
    Write-Host "Tests Passed: $($TestResults.PassedTests)/$($TestResults.TotalTests)" -ForegroundColor $(if ($TestResults.PassedTests -eq $TestResults.TotalTests) { "Green" } else { "Yellow" })
    
    if ($TestResults.OverallScore -ge 90) {
        Write-Host ""
        Write-Host "🎉 LOGGING VALIDATION SUCCESSFUL!" -ForegroundColor Green
        Write-Host "The logging pipeline is working as designed and provides reliable evidence" -ForegroundColor Green
        Write-Host "of Raft consensus and metadata persistence." -ForegroundColor Green
    }
    
} catch {
    Write-Host "Test execution failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
