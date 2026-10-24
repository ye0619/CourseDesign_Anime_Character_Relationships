[CmdletBinding()]
param([string]$MySqlHome='D:\Tools\mysql-9.3.0-winx64')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Project-Environment.ps1')
$canonicalRoot=Resolve-ProjectPath $root
Import-ProjectEnvironment -Root $root
$records=[Collections.Generic.List[object]]::new()
function Probe([int]$Expected,[string]$Dependency,[string]$State){
    $response=Invoke-WebRequest "http://127.0.0.1:$($env:SERVER_PORT)/api/v1/health/ready" -SkipHttpErrorCheck -TimeoutSec 10
    $body=$response.Content|ConvertFrom-Json
    if([int]$response.StatusCode -ne $Expected -or $body.data.dependencies.$Dependency -ne $State){throw "Readiness mismatch: $Dependency should be $State / HTTP $Expected"}
    $records.Add(@{dependency=$Dependency;state=$State;httpStatus=[int]$response.StatusCode;checkedAt=[DateTime]::UtcNow.ToString('o')})
    Write-Output "PASS $Dependency $State / HTTP $Expected"
}
function Wait-Ready {
    for($attempt=0;$attempt -lt 30;$attempt++){
        try{$result=Invoke-RestMethod "http://127.0.0.1:$($env:SERVER_PORT)/api/v1/health/ready" -TimeoutSec 6;if($result.data.status -eq 'READY'){return}}catch{}
        Start-Sleep -Milliseconds 500
    }
    throw 'Databases did not recover readiness.'
}
# Only run against the independently launched project instances, with no active import.
$env:MYSQL_PWD=$env:MYSQL_PASSWORD
try{$active=& (Join-Path $MySqlHome 'bin/mysql.exe') --protocol=TCP --host=127.0.0.1 "--port=$($env:MYSQL_PORT)" "--user=$($env:MYSQL_USER)" "--database=$($env:MYSQL_DATABASE)" --batch --skip-column-names --execute="SELECT (SELECT count(*) FROM import_task WHERE status IN ('PENDING','RUNNING'))+(SELECT count(*) FROM viewer_binding WHERE sync_status='RUNNING')";if($LASTEXITCODE -ne 0 -or [int]$active -ne 0){throw 'Finish active import and collection sync tasks before running recovery checks.'}}finally{Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue}
Probe 200 'neo4j' 'UP'
$neoPid=[int](Get-Content -LiteralPath (Join-Path $root '.local/neo4j-launcher.pid'))
$neoProcess=Get-CimInstance Win32_Process -Filter "ProcessId = $neoPid"
if(-not $neoProcess -or ($neoProcess.CommandLine -notlike "*$root*" -and $neoProcess.CommandLine -notlike "*$canonicalRoot*") -or $neoProcess.CommandLine -notmatch 'org\.neo4j\.server\.CommunityEntryPoint'){throw 'Project Neo4j process ownership could not be verified.'}
try{Stop-Process -Id $neoPid;Start-Sleep -Milliseconds 500;Probe 503 'neo4j' 'DOWN'}finally{& (Join-Path $PSScriptRoot 'Start-LocalDatabases.ps1') -MySqlHome $MySqlHome;Wait-Ready}
Probe 200 'neo4j' 'UP'
$mysqlPid=[int](Get-Content -LiteralPath (Join-Path $root '.local/mysql/launcher.pid'))
$mysqlProcess=Get-CimInstance Win32_Process -Filter "ProcessId = $mysqlPid"
if(-not $mysqlProcess -or $mysqlProcess.ExecutablePath -ne (Join-Path $MySqlHome 'bin/mysqld.exe') -or $mysqlProcess.CommandLine -notmatch 'defaults-file=my.ini'){throw 'Project MySQL process ownership could not be verified.'}
try{
    $env:MYSQL_PWD=$env:MYSQL_ROOT_PASSWORD
    & (Join-Path $MySqlHome 'bin/mysqladmin.exe') --protocol=TCP --host=127.0.0.1 "--port=$($env:MYSQL_PORT)" --user=root shutdown
    if($LASTEXITCODE -ne 0){throw 'Project MySQL shutdown failed.'}
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    Probe 503 'mysql' 'DOWN'
}finally{
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    # mysqladmin returns after shutdown begins; the datadir lock can remain held.
    if(Get-Process -Id $mysqlPid -ErrorAction SilentlyContinue){Wait-Process -Id $mysqlPid -Timeout 30}
    & (Join-Path $PSScriptRoot 'Start-LocalDatabases.ps1') -MySqlHome $MySqlHome
    Wait-Ready
}
Probe 200 'mysql' 'UP'
$records|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $root '.local/readiness-verification.json') -Encoding utf8
