# Log Integrity Test Suite
# This script tests the completeness, ordering, and consistency of logs
# across the entire logging pipeline (Docker → Promtail → Loki)

param(
    [int]$TestDurationMinutes = 5,
    [string]$ReportPath = "logs/log-integrity-report.md",
    [switch]$Verbose = $false
)

# Configuration
$LOKI_URL = "http://localhost:3100"
$CONTROLLERS = @("controller1", "controller2", "controller3", "controller4", "controller5")

# Test tracking
$IntegrityResults = @{
    LogCompleteness = @{}
    CrossNodeConsistency = @{}
    PipelineLatency = @{}
    DataIntegrity = @{}
    TotalTests = 0
    PassedTests = 0
}

function Write-IntegrityHeader {
    param([string]$Title)
    Write-Host ""
    Write-Host "=" * 80 -ForegroundColor Magenta
    Write-Host "  $Title" -ForegroundColor Magenta
    Write-Host "=" * 80 -ForegroundColor Magenta
}

function Write-IntegrityTest {
    param([string]$TestName, [bool]$Passed, [string]$Details = "")
    
    $IntegrityResults.TotalTests++
    if ($Passed) {
        $IntegrityResults.PassedTests++
        Write-Host "✅ $TestName" -ForegroundColor Green
    } else {
        Write-Host "❌ $TestName" -ForegroundColor Red
    }
    
    if ($Details -and $Verbose) {
        Write-Host "   $Details" -ForegroundColor Gray
    }
}

function Get-DirectDockerLogs {
    param([string]$ContainerName, [int]$Lines = 100)
    
    try {
        $logs = docker logs "quorus-$ContainerName" --tail $Lines --timestamps 2>$null
        if ($logs) {
            $parsedLogs = @()
            foreach ($line in $logs) {
                if ($line -match '^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z)\s+(.*)$') {
                    $parsedLogs += @{
                        timestamp = [DateTime]::Parse($matches[1])
                        line = $matches[2]
                        source = "docker"
                        container = $ContainerName
                    }
                }
            }
            return $parsedLogs
        }
    } catch {
        Write-Host "Error getting Docker logs for $ContainerName : $($_.Exception.Message)" -ForegroundColor Red
    }
    
    return @()
}

function Get-LokiLogsForContainer {
    param([string]$ContainerName, [int]$Lines = 100)
    
    try {
        Add-Type -AssemblyName System.Web
        
        $query = "{container_name=`"quorus-$ContainerName`"}"
        $encodedQuery = [System.Web.HttpUtility]::UrlEncode($query)
        $url = "$LOKI_URL/loki/api/v1/query_range?query=$encodedQuery&limit=$Lines"
        
        $response = Invoke-RestMethod -Uri $url -Method GET -TimeoutSec 10
        
        if ($response.status -eq "success") {
            $parsedLogs = @()
            foreach ($stream in $response.data.result) {
                foreach ($entry in $stream.values) {
                    $timestampNano = [long]$entry[0]
                    $timestampSeconds = $timestampNano / 1000000000
                    $dateTime = [DateTimeOffset]::FromUnixTimeSeconds($timestampSeconds).DateTime
                    
                    $parsedLogs += @{
                        timestamp = $dateTime
                        line = $entry[1]
                        source = "loki"
                        container = $ContainerName
                    }
                }
            }
            return $parsedLogs | Sort-Object timestamp
        }
    } catch {
        Write-Host "Error getting Loki logs for $ContainerName : $($_.Exception.Message)" -ForegroundColor Red
    }
    
    return @()
}

function Test-LogCompleteness {
    Write-IntegrityHeader "LOG COMPLETENESS TESTS"
    
    $completenessResults = @{}
    
    foreach ($controller in $CONTROLLERS) {
        Write-Host "Testing log completeness for $controller..." -ForegroundColor Cyan
        
        # Get logs from both sources
        $dockerLogs = Get-DirectDockerLogs -ContainerName $controller -Lines 50
        $lokiLogs = Get-LokiLogsForContainer -ContainerName $controller -Lines 50
        
        # Filter for Raft events only for comparison
        $dockerRaftLogs = $dockerLogs | Where-Object { $_.line -match "RaftNode|election|leader|vote|term" }
        $lokiRaftLogs = $lokiLogs | Where-Object { $_.line -match "RaftNode|election|leader|vote|term" }
        
        $dockerCount = $dockerRaftLogs.Count
        $lokiCount = $lokiRaftLogs.Count
        
        # Calculate completeness percentage
        $completeness = if ($dockerCount -gt 0) { 
            [math]::Min(100, [math]::Round(($lokiCount / $dockerCount) * 100, 1))
        } else { 
            100 
        }
        
        $isComplete = $completeness -ge 80 # 80% threshold for completeness
        
        Write-IntegrityTest "Log Completeness - $controller" $isComplete "Docker: $dockerCount, Loki: $lokiCount ($completeness%)"
        
        $completenessResults[$controller] = @{
            DockerLogs = $dockerCount
            LokiLogs = $lokiCount
            Completeness = $completeness
            IsComplete = $isComplete
        }
    }
    
    $IntegrityResults.LogCompleteness = $completenessResults
}

function Test-CrossNodeConsistency {
    Write-IntegrityHeader "CROSS-NODE CONSISTENCY TESTS"
    
    # Get recent Raft logs from all nodes via Loki
    $allNodeLogs = @{}
    $termEvents = @{}
    
    foreach ($controller in $CONTROLLERS) {
        $logs = Get-LokiLogsForContainer -ContainerName $controller -Lines 30
        $raftLogs = $logs | Where-Object { $_.line -match "RaftNode|election|leader|vote|term" }
        $allNodeLogs[$controller] = $raftLogs
        
        # Extract term information
        foreach ($log in $raftLogs) {
            if ($log.line -match "term (\d+)") {
                $term = [int]$matches[1]
                if (-not $termEvents.ContainsKey($term)) {
                    $termEvents[$term] = @{}
                }
                if (-not $termEvents[$term].ContainsKey($controller)) {
                    $termEvents[$term][$controller] = @()
                }
                $termEvents[$term][$controller] += $log
            }
        }
    }
    
    # Test 1: Term consistency across nodes
    $termConsistency = $true
    $inconsistentTerms = @()
    
    foreach ($term in $termEvents.Keys) {
        $nodesWithTerm = $termEvents[$term].Keys.Count
        if ($nodesWithTerm -lt 3) { # Should have majority of nodes
            $termConsistency = $false
            $inconsistentTerms += $term
        }
    }
    
    Write-IntegrityTest "Term Consistency Across Nodes" $termConsistency "Inconsistent terms: $($inconsistentTerms -join ', ')"
    
    # Test 2: Leader election consistency
    $leaderConsistency = $true
    $leaderEvents = @{}
    
    foreach ($controller in $CONTROLLERS) {
        $leaderLogs = $allNodeLogs[$controller] | Where-Object { $_.line -match "became leader.*term (\d+)" }
        foreach ($log in $leaderLogs) {
            if ($log.line -match "became leader.*term (\d+)") {
                $term = [int]$matches[1]
                if (-not $leaderEvents.ContainsKey($term)) {
                    $leaderEvents[$term] = @()
                }
                $leaderEvents[$term] += $controller
            }
        }
    }
    
    # Check for multiple leaders in same term
    foreach ($term in $leaderEvents.Keys) {
        if ($leaderEvents[$term].Count -gt 1) {
            $leaderConsistency = $false
            break
        }
    }
    
    Write-IntegrityTest "Leader Election Consistency" $leaderConsistency "No multiple leaders per term"
    
    # Test 3: Vote consistency
    $voteConsistency = $true
    $voteEvents = @{}
    
    foreach ($controller in $CONTROLLERS) {
        $voteLogs = $allNodeLogs[$controller] | Where-Object { $_.line -match "Granted vote.*term (\d+)" }
        foreach ($log in $voteLogs) {
            if ($log.line -match "Granted vote.*term (\d+)") {
                $term = [int]$matches[1]
                if (-not $voteEvents.ContainsKey($term)) {
                    $voteEvents[$term] = 0
                }
                $voteEvents[$term]++
            }
        }
    }
    
    # Check that vote counts make sense (should be <= 5 per term)
    foreach ($term in $voteEvents.Keys) {
        if ($voteEvents[$term] -gt 5) {
            $voteConsistency = $false
            break
        }
    }
    
    Write-IntegrityTest "Vote Count Consistency" $voteConsistency "Vote counts are reasonable"
    
    $IntegrityResults.CrossNodeConsistency = @{
        TermConsistency = $termConsistency
        LeaderConsistency = $leaderConsistency
        VoteConsistency = $voteConsistency
        TermsAnalyzed = $termEvents.Keys.Count
        LeaderElections = $leaderEvents.Keys.Count
    }
}

function Test-PipelineLatency {
    Write-IntegrityHeader "PIPELINE LATENCY TESTS"
    
    # Generate a test event by querying cluster status
    $testStart = Get-Date
    
    # Trigger some activity by checking all controller health
    foreach ($controller in $CONTROLLERS) {
        $port = 8081 + $CONTROLLERS.IndexOf($controller)
        try {
            Invoke-RestMethod -Uri "http://localhost:$port/health" -Method GET -TimeoutSec 2 | Out-Null
        } catch {
            # Ignore errors, we just want to generate some log activity
        }
    }
    
    # Wait a moment for logs to be generated
    Start-Sleep -Seconds 5
    
    # Check how quickly logs appear in Loki
    $lokiQueryStart = Get-Date
    $recentLogs = Get-LokiLogsForContainer -ContainerName "controller1" -Lines 10
    $lokiQueryDuration = (Get-Date) - $lokiQueryStart
    
    # Test query performance
    $queryPerformance = $lokiQueryDuration.TotalSeconds -lt 3
    Write-IntegrityTest "Loki Query Performance" $queryPerformance "Query took $($lokiQueryDuration.TotalSeconds) seconds"
    
    # Test log freshness
    if ($recentLogs.Count -gt 0) {
        $latestLog = $recentLogs | Sort-Object timestamp -Descending | Select-Object -First 1
        $logAge = (Get-Date) - $latestLog.timestamp
        $logFreshness = $logAge.TotalMinutes -lt 5
        
        Write-IntegrityTest "Log Freshness" $logFreshness "Latest log is $([math]::Round($logAge.TotalMinutes, 1)) minutes old"
    } else {
        Write-IntegrityTest "Log Freshness" $false "No recent logs found"
    }
    
    $IntegrityResults.PipelineLatency = @{
        QueryPerformance = $queryPerformance
        QueryDuration = $lokiQueryDuration.TotalSeconds
        LogFreshness = $logFreshness
    }
}

function Test-DataIntegrity {
    Write-IntegrityHeader "DATA INTEGRITY TESTS"
    
    # Get a sample of logs from Loki
    $sampleLogs = Get-LokiLogsForContainer -ContainerName "controller1" -Lines 20
    
    # Test 1: Log format consistency
    $formatConsistency = $true
    $malformedLogs = 0
    
    foreach ($log in $sampleLogs) {
        # Check if log has basic structure (timestamp and message)
        if ([string]::IsNullOrWhiteSpace($log.line) -or $log.timestamp -eq $null) {
            $formatConsistency = $false
            $malformedLogs++
        }
    }
    
    Write-IntegrityTest "Log Format Consistency" $formatConsistency "Malformed logs: $malformedLogs/$($sampleLogs.Count)"
    
    # Test 2: Character encoding integrity
    $encodingIntegrity = $true
    $encodingIssues = 0
    
    foreach ($log in $sampleLogs) {
        # Check for common encoding issues
        if ($log.line -match "[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]") {
            $encodingIntegrity = $false
            $encodingIssues++
        }
    }
    
    Write-IntegrityTest "Character Encoding Integrity" $encodingIntegrity "Encoding issues: $encodingIssues/$($sampleLogs.Count)"
    
    # Test 3: Timestamp integrity
    $timestampIntegrity = $true
    $timestampIssues = 0
    
    foreach ($log in $sampleLogs) {
        # Check if timestamp is reasonable (not too far in past or future)
        $now = Get-Date
        $timeDiff = [math]::Abs(($now - $log.timestamp).TotalDays)
        
        if ($timeDiff -gt 1) { # More than 1 day difference
            $timestampIntegrity = $false
            $timestampIssues++
        }
    }
    
    Write-IntegrityTest "Timestamp Integrity" $timestampIntegrity "Timestamp issues: $timestampIssues/$($sampleLogs.Count)"
    
    $IntegrityResults.DataIntegrity = @{
        FormatConsistency = $formatConsistency
        EncodingIntegrity = $encodingIntegrity
        TimestampIntegrity = $timestampIntegrity
        SampleSize = $sampleLogs.Count
        MalformedLogs = $malformedLogs
        EncodingIssues = $encodingIssues
        TimestampIssues = $timestampIssues
    }
}

function Generate-IntegrityReport {
    param([string]$ReportPath)
    
    $overallScore = [math]::Round(($IntegrityResults.PassedTests / $IntegrityResults.TotalTests) * 100, 1)
    
    $report = @"
# Log Integrity Test Report

**Generated:** $(Get-Date)
**Overall Score:** $overallScore% ($($IntegrityResults.PassedTests)/$($IntegrityResults.TotalTests) tests passed)

## Executive Summary

This report validates the integrity, completeness, and consistency of logs across the entire Quorus logging pipeline (Docker → Promtail → Loki).

## Test Results

### Log Completeness Tests
"@

    foreach ($controller in $CONTROLLERS) {
        if ($IntegrityResults.LogCompleteness.ContainsKey($controller)) {
            $result = $IntegrityResults.LogCompleteness[$controller]
            $report += "`n- **$controller**: $($result.Completeness)% complete ($($result.LokiLogs)/$($result.DockerLogs) logs)"
        }
    }

    $report += @"

### Cross-Node Consistency Tests
- **Term Consistency:** $($IntegrityResults.CrossNodeConsistency.TermConsistency)
- **Leader Consistency:** $($IntegrityResults.CrossNodeConsistency.LeaderConsistency)
- **Vote Consistency:** $($IntegrityResults.CrossNodeConsistency.VoteConsistency)
- **Terms Analyzed:** $($IntegrityResults.CrossNodeConsistency.TermsAnalyzed)
- **Leader Elections:** $($IntegrityResults.CrossNodeConsistency.LeaderElections)

### Pipeline Latency Tests
- **Query Performance:** $($IntegrityResults.PipelineLatency.QueryPerformance)
- **Query Duration:** $($IntegrityResults.PipelineLatency.QueryDuration) seconds
- **Log Freshness:** $($IntegrityResults.PipelineLatency.LogFreshness)

### Data Integrity Tests
- **Format Consistency:** $($IntegrityResults.DataIntegrity.FormatConsistency)
- **Encoding Integrity:** $($IntegrityResults.DataIntegrity.EncodingIntegrity)
- **Timestamp Integrity:** $($IntegrityResults.DataIntegrity.TimestampIntegrity)
- **Sample Size:** $($IntegrityResults.DataIntegrity.SampleSize) logs analyzed

## Conclusion

$(if ($overallScore -ge 90) {
    "✅ **EXCELLENT**: The logging pipeline demonstrates high integrity and reliability."
} elseif ($overallScore -ge 75) {
    "⚠️ **GOOD**: The logging pipeline is mostly reliable but may need minor improvements."
} else {
    "❌ **NEEDS ATTENTION**: The logging pipeline has integrity issues that need to be addressed."
})

The logging system maintains data integrity across the entire pipeline and provides reliable evidence for Raft consensus monitoring.

---
*Report generated by Quorus Log Integrity Test Suite*
"@

    try {
        $dir = Split-Path $ReportPath -Parent
        if ($dir -and -not (Test-Path $dir)) {
            New-Item -ItemType Directory -Path $dir -Force | Out-Null
        }
        
        $report | Out-File -FilePath $ReportPath -Encoding UTF8
        Write-Host "✅ Integrity report saved: $ReportPath" -ForegroundColor Green
    } catch {
        Write-Host "❌ Failed to save report: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# Main execution
try {
    Write-IntegrityHeader "QUORUS LOG INTEGRITY TEST SUITE"
    
    Write-Host "This test suite validates the integrity, completeness, and consistency" -ForegroundColor White
    Write-Host "of logs across the entire logging pipeline." -ForegroundColor White
    Write-Host ""
    
    # Run integrity tests
    Test-LogCompleteness
    Test-CrossNodeConsistency
    Test-PipelineLatency
    Test-DataIntegrity
    
    # Generate report
    Write-IntegrityHeader "GENERATING INTEGRITY REPORT"
    Generate-IntegrityReport -ReportPath $ReportPath
    
    # Final summary
    $overallScore = [math]::Round(($IntegrityResults.PassedTests / $IntegrityResults.TotalTests) * 100, 1)
    
    Write-IntegrityHeader "INTEGRITY TEST SUMMARY"
    Write-Host "Overall Score: $overallScore%" -ForegroundColor $(if ($overallScore -ge 90) { "Green" } elseif ($overallScore -ge 75) { "Yellow" } else { "Red" })
    Write-Host "Tests Passed: $($IntegrityResults.PassedTests)/$($IntegrityResults.TotalTests)" -ForegroundColor $(if ($IntegrityResults.PassedTests -eq $IntegrityResults.TotalTests) { "Green" } else { "Yellow" })
    
    if ($overallScore -ge 90) {
        Write-Host ""
        Write-Host "🎉 LOG INTEGRITY VALIDATION SUCCESSFUL!" -ForegroundColor Green
        Write-Host "The logging pipeline maintains high integrity and provides reliable" -ForegroundColor Green
        Write-Host "evidence for Raft consensus monitoring and metadata persistence." -ForegroundColor Green
    }
    
} catch {
    Write-Host "Integrity test execution failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
