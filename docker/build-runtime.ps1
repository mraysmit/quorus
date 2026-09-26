# Builds the Quorus runtime jars on the host. Container images only package these artifacts;
# nothing is compiled inside Docker.
$ErrorActionPreference = 'Stop'

$repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot '..')

Write-Host 'Building the Quorus controller and agent jars on the host with Maven...'
mvn -B -f (Join-Path $repositoryRoot 'pom.xml') clean package -pl quorus-controller,quorus-agent -am -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

foreach ($artifact in @(
        (Join-Path $repositoryRoot 'quorus-controller/target/quorus-controller-1.0-SNAPSHOT.jar'),
        (Join-Path $repositoryRoot 'quorus-agent/target/quorus-agent-1.0-SNAPSHOT.jar'))) {
    if (-not (Test-Path $artifact)) {
        Write-Error "Expected host-built artifact was not created: $artifact"
        exit 1
    }
    Write-Host "Host-built artifact: $artifact"
}
