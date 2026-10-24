[CmdletBinding()]
param([string]$JavaHome)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Project-Environment.ps1')
Import-ProjectEnvironment -Root $root
Select-ProjectJava -RequestedJavaHome $JavaHome
Push-Location (Join-Path $root 'backend')
try {
    & .\mvnw.cmd -B -ntp '-Dtest=IdentityReviewIntegrationTest' '-Didentity.integration=true' test
    if($LASTEXITCODE -ne 0){throw 'Identity integration failed; inspect fixture cleanup diagnostics.'}
    [xml]$report=Get-Content -LiteralPath (Join-Path $root 'backend/target/surefire-reports/TEST-com.animegraph.identity.IdentityReviewIntegrationTest.xml') -Raw
    if([int]$report.testsuite.tests -ne 1 -or [int]$report.testsuite.skipped -ne 0 -or [int]$report.testsuite.errors -ne 0 -or [int]$report.testsuite.failures -ne 0){throw 'Identity integration must execute, not be skipped.'}
    $result=[ordered]@{checkedAt=[DateTime]::UtcNow.ToString('o');passed=$true;tests=1;scope='Actual MySQL with Spring transactions and MyBatis; real Neo4j targets read only';checks=@('candidate does not auto-approve','reviewed aliases bind source revision and identity version','names audit failure rolls back names identity version and history','normalized alias search preserves stable UID and pagination','same alias on different entities does not merge identities','revocation hides names without restoring on reopen or remap','explicit recheck binds names to new target and revision','concurrent name replacements accept one current version only','explicit empty name list clears search aliases','TITLE_ONLY is persisted and direct review is rejected','unchanged title metadata cannot satisfy amendment','new manual proof changes method but remains candidate until separate review','duplicate source key is rejected','remap returns to candidate','revoked identity hidden from public lookup','seed replay does not revive or change reviewer state','new revision explicitly reopens to candidate','audit failure rolls back identity and history together','successful change commits operation log and history together','case-sensitive source IDs remain distinct','concurrent stale decisions publish exactly one change','legacy candidate proof and provenance can be amended before review','rejection preserves the record for audit');fixtureCleanup=$true;realExistingIdentitiesModified=$false}
    [IO.File]::WriteAllText((Join-Path $root 'data/samples/identity-review-acceptance.json'),($result|ConvertTo-Json -Depth 5),[Text.UTF8Encoding]::new($false))
    $nameResult=[ordered]@{checkedAt=$result.checkedAt;passed=$true;testsIncludedInIdentityScenario=1;scope='Real MySQL and read-only Neo4j catalog, synthetic alias fixtures only';checks=@($result.checks|Where-Object {$_ -match 'alias|names|name replacement|empty name'});fixtureCleanup=$true;realExternalNamesReviewed=0;graphEntitiesChanged=$false}
    [IO.File]::WriteAllText((Join-Path $root 'data/samples/reviewed-names-acceptance.json'),($nameResult|ConvertTo-Json -Depth 5),[Text.UTF8Encoding]::new($false))
}finally{Pop-Location}
