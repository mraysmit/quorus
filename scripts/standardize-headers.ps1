# scripts/standardize-headers.ps1

param(
    [string]$TargetDir = "$PSScriptRoot/../",
    [switch]$DryRun,
    [switch]$Verbose
)

$AUTHOR = "Mark Andrew Ray-Smith Cityline Ltd"
$VERSION_DEFAULT = "1.0"
$DATE_DEFAULT = (Get-Date).ToString("yyyy-MM-dd")
$LICENSE_TEXT = @"
/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
"@

function Get-GitCreationDate {
    param([string]$FilePath)
    
    # Get creation date (oldest commit date)
    $date = git log --diff-filter=A --format=%ad --date=format:'%Y-%m-%d' -- $FilePath | Select-Object -Last 1
    
    if (-not $date) {
        # Fallback to file creation timestamp if not in git
        $date = (Get-Item $FilePath).CreationTime.ToString("yyyy-MM-dd")
    }
    
    return $date
}

function Process-JavaFile {
    param(
        [string]$FilePath,
        [switch]$Verbose
    )

    if (-not (Test-Path $FilePath)) {
        Write-Error "File not found: $FilePath"
        return
    }

    $content = Get-Content $FilePath -Raw -Encoding UTF8
    $originalContent = $content
    
    # 1. Handle License (Always at top)
    if ($content -notmatch "Licensed under the Apache License") {
        if ($Verbose) { Write-Host "  Adding missing license..." -ForegroundColor Cyan }
        $content = $LICENSE_TEXT + "`r`n`r`n" + $content.TrimStart()
    }

    # 2. Handle Class JavaDoc
    $classRegexPattern = "(?m)^(?>\s*@[a-zA-Z0-9_().]+\r?\n)*\s*(public|protected|private)?\s*(abstract|final|static)?\s*(class|interface|enum|record)\s+(\w+)"
    $regex = [regex]::new($classRegexPattern)
    $match = $regex.Match($content)
    
    if ($match.Success) {
        $msgMatch = $match.Value
        $className = $match.Groups[4].Value
        $matchIndex = $match.Index
        
        if ($Verbose) { 
            Write-Host "  Found class ${className} at index ${matchIndex}" -ForegroundColor DarkGray 
            Write-Host "  Matched text: ${msgMatch}" -ForegroundColor DarkGray
        }

        $precedingText = $content.Substring(0, $matchIndex)
        $trimmedPre = $precedingText.TrimEnd()
        
        $hasJavadoc = $false
        $docStartIndex = -1
        $docEndIndex = -1
        
        if ($trimmedPre.EndsWith("*/")) {
            $potentialEnd = $trimmedPre.Length
            $lastJavadocStart = $trimmedPre.LastIndexOf("/**")
            
            if ($lastJavadocStart -ge 0) {
                 $blockContent = $trimmedPre.Substring($lastJavadocStart)
                 $innerEnd = $blockContent.Substring(0, $blockContent.Length - 2).IndexOf("*/")
                 
                 if ($innerEnd -eq -1) {
                     $hasJavadoc = $true
                     $docStartIndex = $lastJavadocStart
                     $docEndIndex = $potentialEnd
                 }
            }
        }
        
        if ($hasJavadoc) {
            if ($Verbose) { Write-Host "  Existing JavaDoc found, updating..." -ForegroundColor Cyan }
            
            $docBlock = $trimmedPre.Substring($docStartIndex)
            $oldDocBlock = $docBlock
            
            # Normalize indentation for tags (handle 2 spaces)
            $docBlock = $docBlock -replace "(?m)^\s+\* @", " * @"

            if ($docBlock -notmatch "@author") {
                 $docBlock = $docBlock -replace "\*/", "* @author $AUTHOR`r`n */"
            } else {
                 $docBlock = $docBlock -replace "(?m)^\s*\* @author.*", " * @author $AUTHOR"
            }

            if ($docBlock -notmatch "@version") {
                 $docBlock = $docBlock -replace "\*/", "* @version $VERSION_DEFAULT`r`n */"
            } else {
                 $docBlock = $docBlock -replace "(?m)^\s*\* @version.*", " * @version $VERSION_DEFAULT"
            }

            $gitDate = Get-GitCreationDate -FilePath $FilePath
            if ($docBlock -notmatch "@since") {
                 $docBlock = $docBlock -replace "\*/", "* @since $gitDate`r`n */"
            } else {
                 $docBlock = $docBlock -replace "(?m)^\s*\* @since.*", " * @since $gitDate"
            }

            if ($docBlock -ne $oldDocBlock) {
                $gap = $precedingText.Substring($trimmedPre.Length)
                $before = $precedingText.Substring(0, $docStartIndex)
                $after = $content.Substring($matchIndex)
                $content = $before + $docBlock + $gap + $after
            }

        } else {
             if ($Verbose) { Write-Host "  No JavaDoc found, inserting new block at $matchIndex..." -ForegroundColor Cyan }
             
             $gitDate = Get-GitCreationDate -FilePath $FilePath
             
             $doc = @"
/**
 * Description for $className
 *
 * @author $AUTHOR
 * @version $VERSION_DEFAULT
 * @since $gitDate
 */
"@
            $content = $content.Substring(0, $matchIndex) + $doc + "`r`n" + $content.Substring($matchIndex)
        }

    } else {
        if ($Verbose) { Write-Host "  WARNING: No class match found in $FilePath" -ForegroundColor Red }
    }

    if ($content -ne $originalContent) {
        if ($DryRun) {
            Write-Host "Would update $FilePath" -ForegroundColor Yellow
        } else {
            Set-Content -Path $FilePath -Value $content -Encoding UTF8 -NoNewline
            Write-Host "Updated $FilePath" -ForegroundColor Green
        }
    }
}

if ($MyInvocation.InvocationName -ne '.') {
    Write-Host "Scanning $TargetDir..."
    $files = Get-ChildItem -Path $TargetDir -Recurse -Filter "*.java" | 
        Where-Object { 
            $_.FullName -notmatch "\\target\\" -and 
            $_.FullName -notmatch "\\.history\\" -and
            $_.FullName -notmatch "\\node_modules\\" 
        }

    foreach ($file in $files) {
        Process-JavaFile -FilePath $file.FullName -Verbose:$Verbose
    }
}
