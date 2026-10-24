[CmdletBinding()]
param([string]$JavaHome,[switch]$DatabaseIntegration)
$ErrorActionPreference='Stop'
$root = Split-Path $PSScriptRoot -Parent
New-Item -ItemType Directory -Path (Join-Path $root '.local') -Force|Out-Null
$frontendReport=Join-Path $root '.local/frontend-test-results.json'
. (Join-Path $PSScriptRoot 'Project-Environment.ps1')
Select-ProjectJava -RequestedJavaHome $JavaHome
$mavenArguments=@('-B','-ntp','test')
if($DatabaseIntegration){
    Import-ProjectEnvironment -Root $root
    if($env:NEO4J_URI -notmatch '^bolt://(?:127\.0\.0\.1|localhost):[0-9]+/?$' -or $env:MYSQL_URL -notmatch '^jdbc:mysql://(?:127\.0\.0\.1|localhost):[0-9]+/'){throw 'Database integration is restricted to explicitly configured local databases.'}
    $mavenArguments+=@('-Devidence.integration=true','-Didentity.integration=true','-Dreport.integration=true','-Dlocalgraph.integration=true','-Dconnections.integration=true',('-Dconnections.acceptance.path='+(Join-Path $root '.local/iteration-1024-2/performance.json')),("-Dreport.acceptance.path="+(Join-Path $root '.local/local-report-acceptance.json')))
}
foreach($script in Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1') {
    $parseErrors=$null;$tokens=$null
    [void][Management.Automation.Language.Parser]::ParseFile($script.FullName,[ref]$tokens,[ref]$parseErrors)
    if($parseErrors.Count){throw "PowerShell syntax error in $($script.Name): $($parseErrors.Message -join ', ')"}
}
Write-Output 'PowerShell scripts parsed successfully.'
Push-Location (Join-Path $root 'backend')
try {
    & .\mvnw.cmd @mavenArguments
    if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed.' }
} finally { Pop-Location }
Push-Location (Join-Path $root 'frontend')
try {
    & npm.cmd run check:contract
    if ($LASTEXITCODE -ne 0) { throw 'API contract validation failed.' }
    & node (Join-Path $PSScriptRoot 'Check-Documentation.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'Documentation validation failed.' }
    & node (Join-Path $PSScriptRoot 'Check-Deployment.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'Static deployment validation failed.' }
    & node --test (Join-Path $PSScriptRoot 'Normalize-ExternalCoverage.test.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'External coverage normalization validation failed.' }
    & node --test (Join-Path $PSScriptRoot 'Prepare-Delivery.test.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'Delivery packaging validation failed.' }
    & npm.cmd run test:unit -- --reporter=default --reporter=json "--outputFile=$frontendReport"
    if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed.' }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
} finally { Pop-Location }

& node (Join-Path $PSScriptRoot 'Write-ProjectCheckReport.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Structured project check report failed.' }
