# Regenerates THIRD-PARTY.txt at the repository root: every third-party runtime dependency of
# the default reactor, with its license, as resolved by Maven. Test, provided and system scopes
# and Quorus's own modules are excluded.
#
# Run it after any dependency change and commit the result with that change.
#
#   ./scripts/generate-third-party-inventory.ps1           # regenerate
#   ./scripts/generate-third-party-inventory.ps1 -Check    # fail if THIRD-PARTY.txt is out of date

param(
    [switch]$Check
)

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$generated = Join-Path $root 'target/generated-sources/license/THIRD-PARTY.txt'
$inventory = Join-Path $root 'THIRD-PARTY.txt'

Push-Location $root
try {
    mvn -B -q org.codehaus.mojo:license-maven-plugin:2.4.0:aggregate-add-third-party `
        '-Dlicense.excludedScopes=test,provided,system' `
        '-Dlicense.excludedGroups=^dev\.mars'
    if ($LASTEXITCODE -ne 0) { throw "license-maven-plugin failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

# The plugin writes platform line endings; the inventory is stored with LF.
$content = ((Get-Content -LiteralPath $generated -Raw) -replace "`r`n", "`n").Trim() + "`n"
if ($Check) {
    $current = if (Test-Path -LiteralPath $inventory) { (Get-Content -LiteralPath $inventory -Raw) -replace "`r`n", "`n" } else { '' }
    if ($current -ne $content) {
        throw 'THIRD-PARTY.txt is out of date; run scripts/generate-third-party-inventory.ps1 and commit the result.'
    }
    Write-Host 'THIRD-PARTY.txt is up to date.'
} else {
    [IO.File]::WriteAllText($inventory, $content, [Text.UTF8Encoding]::new($false))
    Write-Host "Wrote $inventory"
}
