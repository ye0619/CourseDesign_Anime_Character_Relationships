[CmdletBinding()]
param([string]$JavaHome,[switch]$BuildOnly,[switch]$DatabaseIntegration)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'Test-Project.ps1') -JavaHome $JavaHome -DatabaseIntegration:$DatabaseIntegration
. (Join-Path $PSScriptRoot 'Project-Environment.ps1')
Select-ProjectJava -RequestedJavaHome $JavaHome
Push-Location (Join-Path $root 'backend')
try {
    & .\mvnw.cmd -B -ntp -DskipTests package
    if($LASTEXITCODE -ne 0){throw 'Executable backend packaging failed.'}
} finally {Pop-Location}
& node (Join-Path $PSScriptRoot 'Prepare-Delivery.mjs') --attest
if($LASTEXITCODE -ne 0){throw 'Artifact build stamp failed.'}
if($BuildOnly){Write-Output 'Build checked. Run Seal-Delivery.ps1 after runtime acceptance; changed source files require checks again.';return}
& (Join-Path $PSScriptRoot 'Seal-Delivery.ps1')
