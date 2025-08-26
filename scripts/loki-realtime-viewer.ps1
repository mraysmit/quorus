# Loki Real-time Log Viewer
# This script provides real-time log viewing and export using Loki API
# with filtering and highlighting for Raft events

param(
    [string]$Query = '{job="quorus"}',           # LogQL query
    [int]$TailLines = 100,                       # Number of lines to tail
    [switch]$RealTime = $false,                  # Enable real-time streaming
    [switch]$RaftOnly = $false,                  # Show only Raft events
    [switch]$Elections = $false,                 # Show only election events
    [switch]$Export = $false,                    # Export to file
    [string]$ExportPath = "logs/loki-export.json", # Export file path
    [int]$RefreshSeconds = 2                     # Refresh interval for real-time
)

# Loki configuration
$LOKI_URL = "http://localhost:3100"

function Write-Header {
    param([string]$Title)
    Write-Host ""
    Write-Host "=" * 80 -ForegroundColor Cyan
    Write-Host "  $Title" -ForegroundColor Cyan
    Write-Host "=" * 80 -ForegroundColor Cyan
}

function Write-LogEntry {
    param([object]$Entry, [bool]$Colored = $true)

    $timestamp = $Entry.timestamp
    $message = $Entry.line

    if ($Colored) {
        $color = "White"
        $icon = "📝"

        if ($message -match "became leader") {
            $color = "Green"
            $icon = "👑"
        } elseif ($message -match "Starting election") {
            $color = "Magenta"
            $icon = "🗳️"
        } elseif ($message -match "Granted vote") {
            $color = "Cyan"
            $icon = "✅"
        } elseif ($message -match "stepping down") {
            $color = "Yellow"
            $icon = "⬇️"
        } elseif ($message -match "Handling vote request") {
            $color = "Blue"
            $icon = "🤝"
        } elseif ($message -match "ERROR") {
            $color = "Red"
            $icon = "❌"
        } elseif ($message -match "WARN") {
            $color = "Yellow"
            $icon = "⚠️"
        }

        # Convert nanosecond timestamp to DateTime
        try {
            $unixTimeNano = [long]$timestamp
            $unixTimeSeconds = $unixTimeNano / 1000000000
            $dateTime = [DateTimeOffset]::FromUnixTimeSeconds($unixTimeSeconds).DateTime
            $formattedTime = $dateTime.ToString("HH:mm:ss.fff")
        } catch {
            $formattedTime = $timestamp
        }

        Write-Host "$icon [$formattedTime] $message" -ForegroundColor $color
    } else {
        Write-Host "[$timestamp] $message"
    }
}

function Get-LokiLogs {
    param(
        [string]$LogQuery,
        [int]$Limit = 100,
        [string]$Start = "",
        [string]$End = ""
    )
    
    try {
        # Build query parameters
        $params = @{
            query = $LogQuery
            limit = $Limit
        }
        
        if ($Start) { $params.start = $Start }
        if ($End) { $params.end = $End }
        
        # Build URL
        $queryString = ($params.GetEnumerator() | ForEach-Object { "$($_.Key)=$([System.Web.HttpUtility]::UrlEncode($_.Value))" }) -join "&"
        $url = "$LOKI_URL/loki/api/v1/query_range?$queryString"
        
        # Make request
        $response = Invoke-RestMethod -Uri $url -Method GET -TimeoutSec 10
        
        if ($response.status -eq "success") {
            $entries = @()
            foreach ($stream in $response.data.result) {
                foreach ($entry in $stream.values) {
                    $entries += @{
                        timestamp = $entry[0]
                        line = $entry[1]
                        labels = $stream.stream
                    }
                }
            }
            
            # Sort by timestamp
            return $entries | Sort-Object { [long]$_.timestamp }
        } else {
            Write-Host "Loki query failed: $($response.status)" -ForegroundColor Red
            return @()
        }
    } catch {
        Write-Host "Error querying Loki: $($_.Exception.Message)" -ForegroundColor Red
        return @()
    }
}

function Filter-RaftLogs {
    param([array]$Entries)
    
    return $Entries | Where-Object {
        $_.line -match "RaftNode|election|leader|vote|term|stepping|granted|handling|consensus"
    }
}

function Filter-ElectionLogs {
    param([array]$Entries)
    
    return $Entries | Where-Object {
        $_.line -match "Starting election|became leader|Granted vote|stepping down|Handling vote request|Election timeout"
    }
}

function Export-Logs {
    param([array]$Entries, [string]$FilePath)
    
    try {
        # Create directory if it doesn't exist
        $dir = Split-Path $FilePath -Parent
        if ($dir -and -not (Test-Path $dir)) {
            New-Item -ItemType Directory -Path $dir -Force | Out-Null
        }
        
        # Export as JSON
        $exportData = @{
            timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
            query = $Query
            count = $Entries.Count
            entries = $Entries
        }
        
        $exportData | ConvertTo-Json -Depth 10 | Out-File -FilePath $FilePath -Encoding UTF8
        Write-Host "✅ Exported $($Entries.Count) log entries to: $FilePath" -ForegroundColor Green
        
    } catch {
        Write-Host "❌ Export failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Start-RealTimeViewing {
    param([string]$LogQuery)
    
    Write-Header "REAL-TIME LOG STREAMING"
    Write-Host "Query: $LogQuery" -ForegroundColor Gray
    Write-Host "Refresh: Every $RefreshSeconds seconds" -ForegroundColor Gray
    Write-Host "Press Ctrl+C to stop" -ForegroundColor Gray
    Write-Host ""
    
    $lastTimestamp = ""
    
    while ($true) {
        try {
            # Get recent logs
            $startTime = if ($lastTimestamp) { 
                ([long]$lastTimestamp + 1000000).ToString() # Add 1ms in nanoseconds
            } else { 
                ((Get-Date).AddMinutes(-5).ToUniversalTime().Ticks - 621355968000000000) * 100 # Last 5 minutes
            }
            
            $entries = Get-LokiLogs -LogQuery $LogQuery -Limit 1000 -Start $startTime
            
            # Apply filters
            if ($RaftOnly) {
                $entries = Filter-RaftLogs -Entries $entries
            }
            
            if ($Elections) {
                $entries = Filter-ElectionLogs -Entries $entries
            }
            
            # Display new entries
            if ($entries.Count -gt 0) {
                foreach ($entry in $entries) {
                    Write-LogEntry -Entry $entry -Colored $true
                    $lastTimestamp = $entry.timestamp
                }
            }
            
            Start-Sleep -Seconds $RefreshSeconds
            
        } catch {
            Write-Host "Error in real-time viewing: $($_.Exception.Message)" -ForegroundColor Red
            Start-Sleep -Seconds $RefreshSeconds
        }
    }
}

# Main execution
try {
    # Add System.Web for URL encoding
    Add-Type -AssemblyName System.Web
    
    Write-Header "LOKI REAL-TIME LOG VIEWER"
    
    # Test Loki connectivity
    Write-Host "Testing Loki connectivity..." -ForegroundColor Gray
    try {
        $healthCheck = Invoke-RestMethod -Uri "$LOKI_URL/ready" -Method GET -TimeoutSec 5
        Write-Host "✅ Loki is ready" -ForegroundColor Green
    } catch {
        Write-Host "❌ Loki is not accessible: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "Make sure Loki is running on $LOKI_URL" -ForegroundColor Yellow
        exit 1
    }
    
    # Build query based on filters
    $finalQuery = $Query
    
    if ($RaftOnly -or $Elections) {
        # We'll filter after getting the logs since LogQL regex is complex
        Write-Host "Filters will be applied after retrieving logs" -ForegroundColor Gray
    }
    
    Write-Host "Using query: $finalQuery" -ForegroundColor Gray
    
    if ($RealTime) {
        Start-RealTimeViewing -LogQuery $finalQuery
    } else {
        # One-time log retrieval
        Write-Host "Retrieving last $TailLines log entries..." -ForegroundColor Gray
        
        $entries = Get-LokiLogs -LogQuery $finalQuery -Limit $TailLines
        
        if ($entries.Count -eq 0) {
            Write-Host "No logs found matching query: $finalQuery" -ForegroundColor Yellow
            Write-Host ""
            Write-Host "Available labels:" -ForegroundColor Gray
            try {
                $labels = Invoke-RestMethod -Uri "$LOKI_URL/loki/api/v1/labels" -Method GET
                $labels.data | ForEach-Object { Write-Host "  $_" -ForegroundColor Cyan }
            } catch {
                Write-Host "Could not retrieve labels" -ForegroundColor Red
            }
            exit 0
        }
        
        # Apply filters
        if ($RaftOnly) {
            $entries = Filter-RaftLogs -Entries $entries
            Write-Host "Applied Raft filter: $($entries.Count) entries" -ForegroundColor Gray
        }
        
        if ($Elections) {
            $entries = Filter-ElectionLogs -Entries $entries
            Write-Host "Applied Election filter: $($entries.Count) entries" -ForegroundColor Gray
        }
        
        # Display logs
        Write-Header "LOG ENTRIES ($($entries.Count) entries)"
        
        foreach ($entry in $entries) {
            Write-LogEntry -Entry $entry -Colored $true
        }
        
        # Export if requested
        if ($Export) {
            Export-Logs -Entries $entries -FilePath $ExportPath
        }
    }
    
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Log viewing complete." -ForegroundColor Green
