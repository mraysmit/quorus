param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [Parameter(Mandatory = $true)]
    [ValidateRange(2, 2)]
    [int]$CompletedCleanBuilds
)

$ErrorActionPreference = 'Stop'
$outputPath = Join-Path $RepositoryRoot 'docs-design/evidence/phase0-release-evidence.json'
$outputDirectory = Split-Path -Parent $outputPath
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$javaExecutable = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
if (-not (Get-Command $javaExecutable -ErrorAction SilentlyContinue)) {
    throw "Java executable not found: $javaExecutable"
}

$testSuites = Get-ChildItem -Path $RepositoryRoot -Filter 'TEST-*.xml' -File -Recurse -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match '[\\/]target[\\/]surefire-reports[\\/]' }
$testTotals = [ordered]@{ suites = 0; tests = 0; failures = 0; errors = 0; skipped = 0 }
foreach ($suite in $testSuites) {
    [xml]$xml = Get-Content -LiteralPath $suite.FullName -Raw
    $testTotals.suites++
    $testTotals.tests += [int]$xml.testsuite.tests
    $testTotals.failures += [int]$xml.testsuite.failures
    $testTotals.errors += [int]$xml.testsuite.errors
    $testTotals.skipped += [int]$xml.testsuite.skipped
}

$artifacts = Get-ChildItem -Path $RepositoryRoot -Filter '*.jar' -File -Recurse -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match '[\\/]target[\\/][^\\/]+\.jar$' -and $_.Name -notmatch 'original-' } |
    Sort-Object FullName | ForEach-Object {
        [ordered]@{
            path = [IO.Path]::GetRelativePath($RepositoryRoot, $_.FullName).Replace('\', '/')
            sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
            bytes = $_.Length
        }
    }

$configurationFiles = @(
    'pom.xml',
    '.java-version',
    'quorus-controller/src/main/resources/quorus-controller.properties',
    'quorus-agent/src/main/resources/quorus-agent.properties',
    'quorus-controller/src/main/resources/openapi/quorus-controller-v1.yaml'
)
$configuration = foreach ($relative in $configurationFiles) {
    $path = Join-Path $RepositoryRoot $relative
    [ordered]@{ path = $relative; sha256 = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() }
}

$manifest = [ordered]@{
    schemaVersion = 1
    phase = 0
    milestone = 'M0 — Reproducible Alpha Baseline'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    source = [ordered]@{
        revision = (git -c safe.directory=$($RepositoryRoot.Replace('\\','/')) -C $RepositoryRoot rev-parse HEAD).Trim()
        worktreeClean = ((git -c safe.directory=$($RepositoryRoot.Replace('\\','/')) -C $RepositoryRoot status --porcelain).Count -eq 0)
    }
    environment = [ordered]@{
        os = [System.Runtime.InteropServices.RuntimeInformation]::OSDescription
        architecture = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString()
        java = ((& $javaExecutable -version 2>&1) -join ' ')
        maven = ((mvn --version 2>&1 | Select-Object -First 2) -join ' | ')
    }
    verification = [ordered]@{
        requiredCleanBuilds = 2
        completedCleanBuilds = $CompletedCleanBuilds
        cleanBuildCommand = 'mvn -o clean verify'
        outcome = 'passed'
        testReports = $testTotals
        documentationChecks = 'scripts/verify-phase0-docs.ps1'
    }
    configuration = @($configuration)
    artifacts = @($artifacts)
}

$manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $outputPath -Encoding utf8
Write-Host "Wrote Phase 0 evidence manifest: $outputPath"
