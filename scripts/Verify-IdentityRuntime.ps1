[CmdletBinding()]
param([string]$MySqlHome='D:\Tools\mysql-9.3.0-winx64')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Project-Environment.ps1')
Import-ProjectEnvironment -Root $root
if($env:LEGACY_ADMIN_ENABLED -eq 'true'){throw 'This verification targets the default-disabled maintenance configuration.'}
$base='http://127.0.0.1:'+ $env:SERVER_PORT
if((Invoke-RestMethod "$base/api/v1/health/ready" -TimeoutSec 8).data.status -ne 'READY'){throw 'Application not ready'}
$denied=Invoke-WebRequest "$base/api/v1/admin/external-identities" -SkipHttpErrorCheck -TimeoutSec 8
if([int]$denied.StatusCode -ne 401){throw 'Anonymous maintenance boundary changed'}
$target=(Invoke-RestMethod "$base/api/v1/characters/character%3Abangumi%3A87968" -TimeoutSec 8).data
if($target.uid -ne 'character:bangumi:87968' -or $target.nameCn -ne '后藤一里'){throw 'Public target lookup changed'}
$page=Invoke-WebRequest 'http://127.0.0.1:5173/maintenance/identities' -TimeoutSec 8
if([int]$page.StatusCode -ne 200 -or $page.Content -notmatch '<title>映间'){throw 'Maintenance page unavailable'}
$previousPassword=$env:MYSQL_PWD
try{
    $env:MYSQL_PWD=$env:MYSQL_PASSWORD
    $result=& (Join-Path $MySqlHome 'bin/mysql.exe') --protocol=TCP --host=127.0.0.1 "--port=$($env:MYSQL_PORT)" "--user=$($env:MYSQL_USER)" "--database=$($env:MYSQL_DATABASE)" --batch --raw --skip-column-names --execute="SELECT JSON_OBJECT('migrationV7',(SELECT count(*) FROM flyway_schema_history WHERE version='7' AND success=1),'remainingFixtureRows',(SELECT count(*) FROM external_identity WHERE provider='PROJECT' AND provider_id LIKE 'identity-fixture-%'),'caseSensitiveIds',(SELECT count(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='external_identity' AND column_name='provider_id' AND collation_name='utf8mb4_0900_as_cs'))" 2>$null
    if($LASTEXITCODE -ne 0){throw 'Migration verification failed'}
    $state=$result|ConvertFrom-Json
    if($state.migrationV7 -ne 1 -or $state.remainingFixtureRows -ne 0 -or $state.caseSensitiveIds -ne 1){throw 'Unexpected migration or fixture state'}
}finally{$env:MYSQL_PWD=$previousPassword}
$record=[ordered]@{checkedAt=[DateTime]::UtcNow.ToString('o');passed=$true;maintenanceDefaultDisabled=$true;anonymousStatus=401;maintenancePageStatus=200;publicTargetUid=$target.uid;publicTargetLabel=$target.nameCn;migrationV7=$state.migrationV7;caseSensitiveSourceIds=$true;remainingFixtureRows=$state.remainingFixtureRows}
[IO.File]::WriteAllText((Join-Path $root 'data/samples/identity-review-runtime.json'),($record|ConvertTo-Json -Depth 4),[Text.UTF8Encoding]::new($false))
Write-Output 'Default-disabled maintenance page, public target, V7 migration and fixture cleanup verified.'
