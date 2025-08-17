#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Builds P2P Java modules in dependency order with comprehensive error handling.

.DESCRIPTION
    This script builds all P2P Java modules in the correct dependency order, handling
    circular dependencies and providing detailed build status reporting. It supports
    both full builds and incremental builds with test execution control.

.PARAMETER SkipTests
    If specified, skips test execution during the build process.

.PARAMETER Clean
    If specified, performs a clean build by removing target directories first.

.PARAMETER Parallel
    If specified, builds modules in parallel where dependencies allow.

.PARAMETER ModuleFilter
    Optional filter to build only specific modules (comma-separated list).

.PARAMETER Verbose
    Enables verbose output showing detailed build information.

.EXAMPLE
    .\build-p2p-modules.ps1
    Builds all modules with tests.

.EXAMPLE
    .\build-p2p-modules.ps1 -SkipTests -Clean
    Performs a clean build without running tests.

.EXAMPLE
    .\build-p2p-modules.ps1 -ModuleFilter "p2p-common-api,p2p-util"
    Builds only the specified modules.
#>

param(
    [switch]$SkipTests,
    [switch]$Clean,
    [switch]$Parallel,
    [string]$ModuleFilter,
    [switch]$Verbose
)

# Configuration
$P2P_MODULES = @(
    # Foundation Layer (no dependencies)
    "p2p-common-api",
    "p2p-util",
    
    # Infrastructure Layer (depends on foundation)
    "p2p-config",
    "p2p-health",
    "p2p-monitoring",
    
    # Core Services Layer (depends on infrastructure)
    "p2p-discovery",
    "p2p-storage",
    "p2p-connection",
    "p2p-cache",
    "p2p-circuit",
    "p2p-auth",
    "p2p-bootstrap",
    
    # Application Layer (depends on core services)
    "p2p-tracker",
    "p2p-indexserver",
    "p2p-peer",
    "p2p-client"
)

# Function to check if module exists
function Test-ModuleExists {
    param([string]$ModuleName)
    return Test-Path $ModuleName
}

# Function to build a single module
function Build-Module {
    param(
        [string]$ModuleName,
        [switch]$SkipTests,
        [switch]$Clean,
        [switch]$Verbose
    )
    
    if (-not (Test-ModuleExists -ModuleName $ModuleName)) {
        Write-Warning "Module $ModuleName does not exist, skipping..."
        return $false
    }
    
    Write-Host "Building module: $ModuleName" -ForegroundColor Cyan
    
    try {
        $buildArgs = @()
        
        if ($Clean) {
            $buildArgs += "clean"
        }
        
        $buildArgs += "install"
        
        if ($SkipTests) {
            $buildArgs += "-DskipTests"
        }
        
        $buildArgs += "-pl"
        $buildArgs += $ModuleName
        
        if ($Verbose) {
            $buildArgs += "-X"
        } else {
            $buildArgs += "-q"
        }
        
        $startTime = Get-Date
        
        if ($Verbose) {
            Write-Host "  Command: mvn $($buildArgs -join ' ')" -ForegroundColor Gray
        }
        
        $result = & mvn $buildArgs
        $exitCode = $LASTEXITCODE
        
        $duration = (Get-Date) - $startTime
        
        if ($exitCode -eq 0) {
            Write-Host "  SUCCESS - Built $ModuleName in $($duration.TotalSeconds.ToString('F1'))s" -ForegroundColor Green
            return $true
        } else {
            Write-Host "  FAILED - $ModuleName build failed (exit code: $exitCode)" -ForegroundColor Red
            if ($Verbose -and $result) {
                Write-Host "  Error output:" -ForegroundColor Red
                $result | ForEach-Object { Write-Host "    $_" -ForegroundColor Red }
            }
            return $false
        }
        
    } catch {
        Write-Host "  ERROR - Exception building $ModuleName`: $_" -ForegroundColor Red
        return $false
    }
}

# Function to build modules in parallel
function Build-ModulesParallel {
    param(
        [string[]]$Modules,
        [switch]$SkipTests,
        [switch]$Clean,
        [switch]$Verbose
    )
    
    Write-Host "Building modules in parallel..." -ForegroundColor Blue
    
    $jobs = @()
    foreach ($module in $Modules) {
        $job = Start-Job -ScriptBlock {
            param($ModuleName, $SkipTests, $Clean, $Verbose)
            
            # Import the function into the job context
            function Build-Module {
                param(
                    [string]$ModuleName,
                    [switch]$SkipTests,
                    [switch]$Clean,
                    [switch]$Verbose
                )
                
                $buildArgs = @()
                if ($Clean) { $buildArgs += "clean" }
                $buildArgs += "install"
                if ($SkipTests) { $buildArgs += "-DskipTests" }
                $buildArgs += "-pl", $ModuleName
                if (-not $Verbose) { $buildArgs += "-q" }
                
                $result = & mvn $buildArgs
                return @{
                    Module = $ModuleName
                    Success = ($LASTEXITCODE -eq 0)
                    ExitCode = $LASTEXITCODE
                }
            }
            
            return Build-Module -ModuleName $ModuleName -SkipTests:$SkipTests -Clean:$Clean -Verbose:$Verbose
            
        } -ArgumentList $module, $SkipTests, $Clean, $Verbose
        
        $jobs += $job
    }
    
    # Wait for all jobs and collect results
    $results = @()
    foreach ($job in $jobs) {
        $result = Receive-Job -Job $job -Wait
        $results += $result
        Remove-Job -Job $job
    }
    
    return $results
}

# Main execution
Write-Host "P2P Java Module Build Script" -ForegroundColor Magenta
Write-Host "============================" -ForegroundColor Magenta
Write-Host ""

# Filter modules if specified
$modulesToBuild = if ($ModuleFilter) {
    $filterList = $ModuleFilter -split ","
    $P2P_MODULES | Where-Object { $filterList -contains $_ }
} else {
    $P2P_MODULES
}

if ($modulesToBuild.Count -eq 0) {
    Write-Error "No modules to build. Check your filter: $ModuleFilter"
    exit 1
}

Write-Host "Modules to build: $($modulesToBuild -join ', ')" -ForegroundColor Blue
Write-Host "Skip tests: $SkipTests" -ForegroundColor Blue
Write-Host "Clean build: $Clean" -ForegroundColor Blue
Write-Host "Parallel build: $Parallel" -ForegroundColor Blue
Write-Host ""

# Check Maven availability
try {
    $mavenVersion = & mvn --version 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Maven not found"
    }
    Write-Host "Maven version: $($mavenVersion[0])" -ForegroundColor Green
} catch {
    Write-Error "Maven is not available. Please install Maven and ensure it's in your PATH."
    exit 1
}

Write-Host ""

# Build modules
$startTime = Get-Date
$successCount = 0
$failureCount = 0
$failedModules = @()

if ($Parallel) {
    # Parallel build (ignores dependency order)
    $results = Build-ModulesParallel -Modules $modulesToBuild -SkipTests:$SkipTests -Clean:$Clean -Verbose:$Verbose
    
    foreach ($result in $results) {
        if ($result.Success) {
            $successCount++
        } else {
            $failureCount++
            $failedModules += $result.Module
        }
    }
} else {
    # Sequential build (respects dependency order)
    foreach ($module in $modulesToBuild) {
        $success = Build-Module -ModuleName $module -SkipTests:$SkipTests -Clean:$Clean -Verbose:$Verbose
        
        if ($success) {
            $successCount++
        } else {
            $failureCount++
            $failedModules += $module
            
            # Stop on first failure in sequential mode
            Write-Host ""
            Write-Host "Build failed for $module. Stopping sequential build." -ForegroundColor Red
            break
        }
    }
}

$totalTime = (Get-Date) - $startTime

# Summary
Write-Host ""
Write-Host "Build Summary:" -ForegroundColor Magenta
Write-Host "  Total modules: $($modulesToBuild.Count)" -ForegroundColor White
Write-Host "  Successful: $successCount" -ForegroundColor Green
Write-Host "  Failed: $failureCount" -ForegroundColor Red
Write-Host "  Total time: $($totalTime.TotalMinutes.ToString('F1')) minutes" -ForegroundColor White

if ($failedModules.Count -gt 0) {
    Write-Host ""
    Write-Host "Failed modules:" -ForegroundColor Red
    $failedModules | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    exit 1
} else {
    Write-Host ""
    Write-Host "All modules built successfully!" -ForegroundColor Green
    exit 0
}
