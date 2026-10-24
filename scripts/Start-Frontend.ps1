[CmdletBinding()]
param([switch]$Preview)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location (Join-Path $root 'frontend')
try {
    if (-not (Test-Path -LiteralPath 'node_modules')) { throw 'Run npm ci in frontend first.' }
    if($Preview) {
        if(-not(Test-Path -LiteralPath 'dist/index.html')){throw 'Run Build-Delivery.ps1 before starting preview mode.'}
        & npm.cmd run preview
    } else { & npm.cmd run dev }
    if ($LASTEXITCODE -ne 0) { throw "Frontend exited with code $LASTEXITCODE" }
} finally { Pop-Location }
