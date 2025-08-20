#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Fixes corrupted Java class headers and standardizes copyright information.

.DESCRIPTION
    This script fixes Java files in the Quorus file transfer system project that have
    corrupted headers, inconsistent copyright notices, or incorrect author information.
    It standardizes all files to use "Mark Andrew Ray-Smith Cityline Ltd" copyright.

.PARAMETER DryRun
    If specified, shows what changes would be made without actually modifying files.

.PARAMETER Verbose
    Enables verbose output showing detailed processing information.

.EXAMPLE
    .\update-java-headers.ps1
    Fixes all Java files with corrupted headers.

.EXAMPLE
    .\update-java-headers.ps1 -DryRun
    Shows what changes would be made without modifying files.
#>

param(
    [switch]$DryRun,
    [switch]$Verbose
)

# Configuration
$AUTHOR_NAME = "Mark Andrew Ray-Smith Cityline Ltd"
$COPYRIGHT_YEAR = 2025
$PROJECT_NAME = "Quorus"



# Function to repair corrupted Java file headers
function Repair-JavaFile {
    param(
        [string]$FilePath,
        [switch]$DryRun
    )

    if ($Verbose) {
        Write-Host "Processing: $FilePath" -ForegroundColor Cyan
    }

    try {
        $content = Get-Content -Path $FilePath -Raw -Encoding UTF8
        $changed = $false

        # Fix copyright header from "2024 Quorus Project" to "2025 Mark Andrew Ray-Smith Cityline Ltd"
        if ($content -match "Copyright 2024 Quorus Project") {
            $content = $content -replace "Copyright 2024 Quorus Project", "Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd"
            $changed = $true
            if ($Verbose) {
                Write-Host "  Fixed copyright header" -ForegroundColor Green
            }
        }

        # Fix author tag from "Mark A Ra-Smith" to "Mark Andrew Ray-Smith"
        if ($content -match "@author Mark A Ra-Smith") {
            $content = $content -replace "@author Mark A Ra-Smith", "@author Mark Andrew Ray-Smith"
            $changed = $true
            if ($Verbose) {
                Write-Host "  Fixed author tag" -ForegroundColor Green
            }
        }

        # Remove duplicate JavaDoc comments (common corruption issue)
        # Look for pattern where there are two /** */ blocks before class declaration
        $lines = $content -split "`r?`n"
        $javadocBlocks = @()
        $inJavadoc = $false
        $currentBlock = @()

        for ($i = 0; $i -lt $lines.Length; $i++) {
            $line = $lines[$i].Trim()

            if ($line -eq "/**") {
                $inJavadoc = $true
                $currentBlock = @($i)
            } elseif ($inJavadoc -and $line -eq "*/") {
                $currentBlock += $i
                $javadocBlocks += ,@($currentBlock[0], $currentBlock[1])
                $inJavadoc = $false
                $currentBlock = @()
            }
        }

        # If there are multiple JavaDoc blocks, remove duplicates
        if ($javadocBlocks.Count -gt 1) {
            # Keep the last JavaDoc block (usually the most complete one)
            $newLines = @()
            $skipRanges = @()

            # Mark all JavaDoc blocks except the last one for removal
            for ($i = 0; $i -lt $javadocBlocks.Count - 1; $i++) {
                $skipRanges += ,@($javadocBlocks[$i][0], $javadocBlocks[$i][1])
            }

            for ($i = 0; $i -lt $lines.Length; $i++) {
                $shouldSkip = $false
                foreach ($range in $skipRanges) {
                    if ($i -ge $range[0] -and $i -le $range[1]) {
                        $shouldSkip = $true
                        break
                    }
                }
                if (-not $shouldSkip) {
                    $newLines += $lines[$i]
                }
            }

            $content = $newLines -join "`n"
            $changed = $true
            if ($Verbose) {
                Write-Host "  Removed duplicate JavaDoc blocks" -ForegroundColor Green
            }
        }

        # Write the updated content if changes were made
        if ($changed) {
            if ($DryRun) {
                Write-Host "  Would update: $FilePath" -ForegroundColor Green
            } else {
                Set-Content -Path $FilePath -Value $content -Encoding UTF8 -NoNewline
                Write-Host "  Updated: $FilePath" -ForegroundColor Green
            }
            return $true
        } else {
            if ($Verbose) {
                Write-Host "  No changes needed" -ForegroundColor Yellow
            }
            return $false
        }

    } catch {
        Write-Error "Error processing $FilePath`: $_"
        return $false
    }
}

# Main execution
Write-Host "Java Header Fix Script" -ForegroundColor Magenta
Write-Host "Author: $AUTHOR_NAME" -ForegroundColor Magenta
Write-Host "=========================" -ForegroundColor Magenta
Write-Host ""

if ($DryRun) {
    Write-Host "DRY RUN MODE - No files will be modified" -ForegroundColor Yellow
    Write-Host ""
}

# Find all Java files in Quorus project
Write-Host "Scanning for Java files in Quorus project..." -ForegroundColor Blue
$javaFiles = Get-ChildItem -Recurse -Filter "*.java" | Where-Object {
    $_.FullName -notmatch "\\target\\" -and
    $_.FullName -match "\\src\\(main|test)\\java\\"
}

Write-Host "Found $($javaFiles.Count) Java files" -ForegroundColor Blue
Write-Host ""

# Process each file
$updatedCount = 0
$skippedCount = 0

foreach ($file in $javaFiles) {
    $result = Repair-JavaFile -FilePath $file.FullName -DryRun:$DryRun
    if ($result) {
        $updatedCount++
    } else {
        $skippedCount++
    }
}

# Summary
Write-Host ""
Write-Host "Summary:" -ForegroundColor Magenta
Write-Host "  Files processed: $($javaFiles.Count)" -ForegroundColor White
Write-Host "  Files updated: $updatedCount" -ForegroundColor Green
Write-Host "  Files skipped: $skippedCount" -ForegroundColor Yellow

if ($DryRun) {
    Write-Host ""
    Write-Host "Run without -DryRun to apply changes" -ForegroundColor Cyan
}
