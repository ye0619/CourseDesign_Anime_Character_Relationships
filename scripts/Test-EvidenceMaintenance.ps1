[CmdletBinding()]
param([string]$JavaHome)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Project-Environment.ps1')
Import-ProjectEnvironment -Root $root
Select-ProjectJava -RequestedJavaHome $JavaHome
Push-Location (Join-Path $root 'backend')
try {
    & .\mvnw.cmd -B -ntp '-Dtest=EvidenceImportIntegrationTest' '-Devidence.integration=true' test
    if($LASTEXITCODE -ne 0){throw 'Evidence maintenance integration failed; inspect fixture cleanup diagnostics.'}
    $report=Join-Path $root 'backend/target/surefire-reports/TEST-com.animegraph.identity.EvidenceImportIntegrationTest.xml'
    [xml]$result=Get-Content -LiteralPath $report -Raw
    if([int]$result.testsuite.failures -ne 0 -or [int]$result.testsuite.errors -ne 0 -or [int]$result.testsuite.skipped -ne 0){throw 'Integration must execute successfully, not be skipped.'}
    $evidence=[ordered]@{
        checkedAt=[DateTime]::UtcNow.ToString('o');passed=$true
        scope='Actual local Neo4j, production importer and production UniverseService.network; isolated fixture world'
        tests=[int]$result.testsuite.tests;fixtureCleanup=$true;realSourceFactsChanged=$false
        checks=@('preview does not publish','identical commit is idempotent','shared evidence is deduplicated','explicit scope only appears in its declared season','strict scope includes reviewed explicit facts','disabling one shared batch retains another valid claim','disabling the last batch hides its evidence','same-evidence replay under a new batch remains blocked','changed preview state aborts before creating a batch','reviewed facts survive a generation refresh','assertion revocation hides the relation','ON CREATE refresh does not revive a revoked assertion','evidence revision changes on maintenance','exact fixture IDs cleaned in finally')
        sharedWorldChecks=@('separate maintenance instances reject stale shared-fact previews','global evidence reuses identity across fixture worlds','apply and disable invalidate every affected world revision','final shared-proof withdrawal hides the assertion in all worlds')
        limits=@('This is a synthetic transaction test, not a new external-source coverage sample.','Permission and CSRF boundaries are checked separately using the real Spring Security filter chain in MockMvc.')
    }
    $target=Join-Path $root 'data/samples/evidence-maintenance-acceptance.json'
    [IO.File]::WriteAllText($target,($evidence|ConvertTo-Json -Depth 6),[Text.UTF8Encoding]::new($false))
}finally{Pop-Location}
