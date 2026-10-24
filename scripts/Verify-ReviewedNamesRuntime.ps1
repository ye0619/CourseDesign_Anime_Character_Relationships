[CmdletBinding()]
param([string]$MySqlHome='D:\Tools\mysql-9.3.0-winx64')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Project-Environment.ps1')
Import-ProjectEnvironment -Root $root
if($env:LEGACY_ADMIN_ENABLED -eq 'true'){throw 'Verification requires default-disabled maintenance.'}
$base='http://127.0.0.1:'+ $env:SERVER_PORT
if((Invoke-RestMethod "$base/api/v1/health/ready" -TimeoutSec 8).data.status -ne 'READY'){throw 'Application not ready'}
$denied=Invoke-WebRequest "$base/api/v1/admin/external-identities/1/names" -SkipHttpErrorCheck -TimeoutSec 8
if([int]$denied.StatusCode -ne 401){throw 'Maintenance name boundary changed'}
$target=Invoke-WebRequest "$base/api/v1/characters/character%3Abangumi%3A87968" -TimeoutSec 8
$body=($target.Content|ConvertFrom-Json).data
if($body.nameCn -ne '后藤一里' -or $target.Headers['Cache-Control'] -notcontains 'no-store' -or $body.nameNotice){throw 'Public name projection failed or modified display name'}
$world=(Invoke-RestMethod "$base/api/v1/universes/universe%3Aproject%3Abocchi/network" -TimeoutSec 8).data
if($world.nameNotice -or $world.nodes.Count -ne 33 -or $world.edges.Count -ne 50){throw 'World projection changed the established graph baseline'}
$previousPassword=$env:MYSQL_PWD
try{
    $env:MYSQL_PWD=$env:MYSQL_PASSWORD
    $value=& (Join-Path $MySqlHome 'bin/mysql.exe') --protocol=TCP --host=127.0.0.1 "--port=$($env:MYSQL_PORT)" "--user=$($env:MYSQL_USER)" "--database=$($env:MYSQL_DATABASE)" --batch --raw --skip-column-names --execute="SELECT JSON_OBJECT('migrationV8',(SELECT count(*) FROM flyway_schema_history WHERE version='8' AND success=1),'fixtureIdentities',(SELECT count(*) FROM external_identity WHERE provider='PROJECT' AND provider_id LIKE 'identity-fixture-%'),'fixtureNames',(SELECT count(*) FROM external_identity_name n JOIN external_identity i ON i.id=n.identity_id WHERE i.provider='PROJECT' AND i.provider_id LIKE 'identity-fixture-%'),'storedNames',(SELECT count(*) FROM external_identity_name))" 2>$null
    if($LASTEXITCODE -ne 0){throw 'Name migration inspection failed'}
    $state=$value|ConvertFrom-Json
    if($state.migrationV8 -ne 1 -or $state.fixtureIdentities -ne 0 -or $state.fixtureNames -ne 0){throw 'Unexpected name migration or fixture cleanup state'}
}finally{$env:MYSQL_PWD=$previousPassword}
$result=[ordered]@{checkedAt=[DateTime]::UtcNow.ToString('o');passed=$true;maintenanceDefaultDisabled=$true;anonymousNamesStatus=401;displayName=$body.nameCn;publicNoStore=$true;worldNodes=$world.nodes.Count;worldEdges=$world.edges.Count;migrationV8=1;remainingFixtureIdentities=0;remainingFixtureNames=0;storedNames=$state.storedNames;realIdentityWrites=0}
[IO.File]::WriteAllText((Join-Path $root 'data/samples/reviewed-names-runtime.json'),($result|ConvertTo-Json -Depth 4),[Text.UTF8Encoding]::new($false))
Write-Output 'V8, default-disabled name maintenance, live projection, established world and zero remaining fixtures verified.'
