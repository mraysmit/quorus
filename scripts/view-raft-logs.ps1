# Raft Log Viewer and Analyzer
# This script provides comprehensive log viewing and analysis for Raft leader election
# and data persistence evidence in the Quorus controller cluster

param(
    [string]$Node = "all",           # Specific node or "all"
    [int]$Lines = 50,                # Number of lines to show
    [switch]$Follow = $false,        # Follow logs in real-time
    [switch]$RaftOnly = $false,      # Show only Raft-related logs
    [switch]$Elections = $false,     # Show only election-related logs
    [switch]$Replication = $false,   # Show only log replication logs
    [switch]$Timestamps = $true,     # Include timestamps
    [switch]$Colored = $true         # Use colored output
)

# Controller configuration
$CONTROLLERS = @("controller1", "controller2", "controller3", "controller4", "controller5")

function Write-LogHeader {
    param([string]$Title)
    Write-Host ""
    Write-Host "=" * 80 -ForegroundColor Cyan
    Write-Host "  $Title" -ForegroundColor Cyan
    Write-Host "=" * 80 -ForegroundColor Cyan
}

function Write-NodeHeader {
    param([string]$NodeName)
    Write-Host ""
    Write-Host "--- $NodeName LOGS ---" -ForegroundColor Yellow
}

function Get-ColorForLogLevel {
    param([string]$LogLine)
    
    if ($LogLine -match "ERROR") { return "Red" }
    if ($LogLine -match "WARN") { return "Yellow" }
    if ($LogLine -match "became leader") { return "Green" }
    if ($LogLine -match "Starting election") { return "Magenta" }
    if ($LogLine -match "Granted vote") { return "Cyan" }
    if ($LogLine -match "stepping down") { return "Yellow" }
    if ($LogLine -match "Handling vote request") { return "Blue" }
    if ($LogLine -match "Log replication") { return "Green" }
    if ($LogLine -match "Command applied") { return "Green" }
    return "White"
}

function Filter-RaftLogs {
    param([string[]]$LogLines)
    
    $raftKeywords = @(
        "RaftNode",
        "election",
        "leader",
        "vote",
        "term",
        "follower",
        "candidate",
        "heartbeat",
        "log replication",
        "state machine",
        "consensus"
    )
    
    return $LogLines | Where-Object {
        $line = $_
        $raftKeywords | ForEach-Object {
            if ($line -match $_ -or $line -match $_.ToUpper()) {
                return $true
            }
        }
        return $false
    }
}

function Filter-ElectionLogs {
    param([string[]]$LogLines)
    
    $electionKeywords = @(
        "Starting election",
        "became leader",
        "Granted vote",
        "stepping down",
        "Handling vote request",
        "Election timeout",
        "Vote request"
    )
    
    return $LogLines | Where-Object {
        $line = $_
        $electionKeywords | ForEach-Object {
            if ($line -match $_) {
                return $true
            }
        }
        return $false
    }
}

function Filter-ReplicationLogs {
    param([string[]]$LogLines)
    
    $replicationKeywords = @(
        "Log replication",
        "Command applied",
        "State machine",
        "Snapshot",
        "Log entry",
        "Commit index",
        "Applied index"
    )
    
    return $LogLines | Where-Object {
        $line = $_
        $replicationKeywords | ForEach-Object {
            if ($line -match $_) {
                return $true
            }
        }
        return $false
    }
}

function Show-NodeLogs {
    param(
        [string]$NodeName,
        [int]$LineCount,
        [bool]$FollowLogs
    )
    
    Write-NodeHeader -NodeName $NodeName.ToUpper()
    
    try {
        if ($FollowLogs) {
            # For following logs, we'll show recent logs first, then follow
            Write-Host "Showing recent logs, then following new entries..." -ForegroundColor Gray
            $recentLogs = docker logs "quorus-$NodeName" --tail $LineCount 2>$null
        } else {
            $recentLogs = docker logs "quorus-$NodeName" --tail $LineCount 2>$null
        }
        
        if (-not $recentLogs) {
            Write-Host "No logs available for $NodeName" -ForegroundColor Red
            return
        }
        
        # Convert to array if it's a single string
        if ($recentLogs -is [string]) {
            $logLines = @($recentLogs)
        } else {
            $logLines = $recentLogs
        }
        
        # Apply filters
        if ($RaftOnly) {
            $logLines = Filter-RaftLogs -LogLines $logLines
        }
        
        if ($Elections) {
            $logLines = Filter-ElectionLogs -LogLines $logLines
        }
        
        if ($Replication) {
            $logLines = Filter-ReplicationLogs -LogLines $logLines
        }
        
        # Display logs
        foreach ($line in $logLines) {
            if ([string]::IsNullOrWhiteSpace($line)) { continue }
            
            if ($Colored) {
                $color = Get-ColorForLogLevel -LogLine $line
                Write-Host $line -ForegroundColor $color
            } else {
                Write-Host $line
            }
        }
        
        # If following, start following new logs
        if ($FollowLogs) {
            Write-Host ""
            Write-Host "--- FOLLOWING NEW LOGS FOR $($NodeName.ToUpper()) ---" -ForegroundColor Green
            Write-Host "Press Ctrl+C to stop following" -ForegroundColor Gray
            
            # Use docker logs --follow
            docker logs "quorus-$NodeName" --follow --tail 0
        }
        
    } catch {
        Write-Host "Error getting logs for $NodeName : $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Show-AllNodesLogs {
    param([int]$LineCount)
    
    foreach ($controller in $CONTROLLERS) {
        Show-NodeLogs -NodeName $controller -LineCount $LineCount -FollowLogs $false
    }
}

function Show-RaftSummary {
    Write-LogHeader "RAFT CLUSTER SUMMARY"
    
    Write-Host "Current Cluster State:" -ForegroundColor Cyan
    
    foreach ($controller in $CONTROLLERS) {
        try {
            $response = Invoke-RestMethod -Uri "http://localhost:808$($CONTROLLERS.IndexOf($controller) + 1)/health" -Method GET -TimeoutSec 3
            $raftInfo = $response.checks.raft
            
            $stateColor = switch ($raftInfo.state) {
                "LEADER" { "Green" }
                "FOLLOWER" { "Yellow" }
                "CANDIDATE" { "Magenta" }
                default { "Red" }
            }
            
            Write-Host "  $controller : $($raftInfo.state)" -ForegroundColor $stateColor
        } catch {
            Write-Host "  $controller : UNAVAILABLE" -ForegroundColor Red
        }
    }
    
    Write-Host ""
    Write-Host "Recent Raft Activity (last 10 lines per node):" -ForegroundColor Cyan
    
    foreach ($controller in $CONTROLLERS) {
        try {
            $recentLogs = docker logs "quorus-$controller" --tail 10 2>$null
            if ($recentLogs) {
                $raftLogs = Filter-RaftLogs -LogLines @($recentLogs)
                if ($raftLogs.Count -gt 0) {
                    Write-Host ""
                    Write-Host "  $controller :" -ForegroundColor Yellow
                    foreach ($log in $raftLogs | Select-Object -Last 3) {
                        Write-Host "    $log" -ForegroundColor Gray
                    }
                }
            }
        } catch {
            # Ignore errors for summary
        }
    }
}

# Main execution
try {
    Write-LogHeader "QUORUS RAFT LOG VIEWER"
    
    Write-Host "Configuration:" -ForegroundColor Gray
    Write-Host "  Node: $Node" -ForegroundColor Gray
    Write-Host "  Lines: $Lines" -ForegroundColor Gray
    Write-Host "  Follow: $Follow" -ForegroundColor Gray
    Write-Host "  Raft Only: $RaftOnly" -ForegroundColor Gray
    Write-Host "  Elections Only: $Elections" -ForegroundColor Gray
    Write-Host "  Replication Only: $Replication" -ForegroundColor Gray
    
    if ($Node -eq "all") {
        if ($Follow) {
            Write-Host ""
            Write-Host "Cannot follow logs for all nodes simultaneously." -ForegroundColor Red
            Write-Host "Please specify a single node with -Node parameter." -ForegroundColor Red
            exit 1
        }
        
        Show-RaftSummary
        Show-AllNodesLogs -LineCount $Lines
    } else {
        if ($Node -notin $CONTROLLERS) {
            Write-Host "Invalid node: $Node" -ForegroundColor Red
            Write-Host "Valid nodes: $($CONTROLLERS -join ', ')" -ForegroundColor Red
            exit 1
        }
        
        Show-NodeLogs -NodeName $Node -LineCount $Lines -FollowLogs $Follow
    }
    
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Log viewing complete." -ForegroundColor Green
