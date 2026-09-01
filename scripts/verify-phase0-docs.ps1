param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
$failures = [System.Collections.Generic.List[string]]::new()
$activeRoots = @(
    (Join-Path $RepositoryRoot 'docs'),
    (Join-Path $RepositoryRoot 'docs-design/design'),
    (Join-Path $RepositoryRoot 'docs-design/task'),
    (Join-Path $RepositoryRoot 'docs-design/reference'),
    (Join-Path $RepositoryRoot 'docs-design/architecture-decisions')
)
$documents = $activeRoots | Where-Object { Test-Path -LiteralPath $_ } |
    ForEach-Object { Get-ChildItem -LiteralPath $_ -Filter '*.md' -File -Recurse }

foreach ($document in $documents) {
    $text = Get-Content -LiteralPath $document.FullName -Raw
    $lines = Get-Content -LiteralPath $document.FullName
    $relative = [IO.Path]::GetRelativePath($RepositoryRoot, $document.FullName)

    $headerPattern = '(?s)^<img src="[^"]*quorus-logo\.png" alt="Quorus" width="120"/>\r?\n\r?\n# .+?\r?\n\r?\n\*\*Version:\*\* .+?  \r?\n\*\*Date:\*\* \d{4}-\d{2}-\d{2}  \r?\n\*\*Author:\*\* Mark Ray-Smith — Cityline Ltd  \r?\n\*\*License:\*\* Apache 2\.0'
    if ($text -notmatch $headerPattern) {
        $failures.Add("Header does not match the project standard: $relative")
    }

    $fenceCount = ($lines | Where-Object { $_ -match '^\s*```' }).Count
    if (($fenceCount % 2) -ne 0) {
        $failures.Add("Unbalanced fenced code block: $relative")
    }

    foreach ($match in [regex]::Matches($text, '\[[^\]]+\]\((?!https?://|#|mailto:)([^)]+)\)')) {
        $target = $match.Groups[1].Value.Split('#')[0]
        if ([string]::IsNullOrWhiteSpace($target)) { continue }
        $resolved = Join-Path $document.DirectoryName ([Uri]::UnescapeDataString($target))
        if (-not (Test-Path -LiteralPath $resolved)) {
            $failures.Add("Broken local link in ${relative}: $target")
        }
    }
}

$design = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'docs-design/design/QUORUS_SYSTEM_DESIGN.md') -Raw
if ($design -notmatch 'Non-normative target-state vision' -or $design -notmatch 'canonical specifications take precedence') {
    $failures.Add('System design must distinguish target-state material from the current normative contracts')
}

$openApi = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'quorus-controller/src/main/resources/openapi/quorus-controller-v1.yaml') -Raw
$server = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'quorus-controller/src/main/java/dev/mars/quorus/controller/http/HttpApiServer.java') -Raw
if ($openApi -notmatch 'openapi:\s+3\.1\.0') { $failures.Add('OpenAPI contract is not version 3.1.0') }
if ($server -notmatch '/api/v1/openapi\.yaml' -or $openApi -notmatch '(?m)^  /api/v1/openapi\.yaml:') {
    $failures.Add('Bundled OpenAPI endpoint is missing from implementation or contract')
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Host "ERROR: $_" -ForegroundColor Red }
    throw "Phase 0 documentation checks failed with $($failures.Count) error(s)."
}

Write-Host "Phase 0 documentation checks passed for $($documents.Count) active documents."
