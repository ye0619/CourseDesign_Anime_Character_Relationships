[CmdletBinding()]
param([string]$MySqlHome='D:\Tools\mysql-9.3.0-winx64')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Project-Environment.ps1')
Import-ProjectEnvironment -Root $root
$folder=Join-Path $root '.local/backups'
New-Item -ItemType Directory -Path $folder -Force|Out-Null
$target=Join-Path $folder ('identity-maintenance-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)+'.sql')
$previousPassword=$env:MYSQL_PWD
try{
    $env:MYSQL_PWD=$env:MYSQL_PASSWORD
    $existing=& (Join-Path $MySqlHome 'bin/mysql.exe') --protocol=TCP --host=127.0.0.1 "--port=$($env:MYSQL_PORT)" "--user=$($env:MYSQL_USER)" "--database=$($env:MYSQL_DATABASE)" --batch --skip-column-names --execute="SHOW TABLES LIKE 'external_identity_name'" 2>$null
    if($LASTEXITCODE -ne 0){throw 'Could not inspect identity schema; no migration should be started.'}
    $tables=@('external_identity','external_identity_event','operation_log','flyway_schema_history')
    if($existing -eq 'external_identity_name'){$tables+='external_identity_name'}
    & (Join-Path $MySqlHome 'bin/mysqldump.exe') --protocol=TCP --host=127.0.0.1 "--port=$($env:MYSQL_PORT)" "--user=$($env:MYSQL_USER)" --no-tablespaces --single-transaction --skip-lock-tables --set-gtid-purged=OFF "--result-file=$target" $env:MYSQL_DATABASE @tables
    if($LASTEXITCODE -ne 0 -or (Get-Item -LiteralPath $target).Length -lt 1000){throw 'Identity backup failed; no migration should be started.'}
    Write-Output ('Identity maintenance backup saved privately: '+[IO.Path]::GetFileName($target))
}finally{$env:MYSQL_PWD=$previousPassword}
