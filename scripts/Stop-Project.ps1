[CmdletBinding()]
param([switch]$IncludeDatabases,[string]$MySqlHome='D:\Tools\mysql-9.3.0-winx64')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Project-Environment.ps1')
Import-ProjectEnvironment -Root $root
$canonical=Resolve-ProjectPath $root
$processes=@(Get-CimInstance Win32_Process)
function Find-Process([int]$ProcessId) {return $processes|Where-Object ProcessId -eq $ProcessId|Select-Object -First 1}
function Test-Owner([int]$ProcessId) {
    for($depth=0;$depth -lt 6 -and $ProcessId -gt 0;$depth++) {
        $item=Find-Process $ProcessId
        if(-not $item){return $false}
        if($item.CommandLine -and ($item.CommandLine.Contains($root,[StringComparison]::OrdinalIgnoreCase) -or $item.CommandLine.Contains($canonical,[StringComparison]::OrdinalIgnoreCase))){return $true}
        $ProcessId=[int]$item.ParentProcessId
    }
    return $false
}
$targets=@()
foreach($port in @([int]$env:SERVER_PORT,5173)) {
    $listeners=@(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)
    foreach($listener in $listeners) {
        if(-not(Test-Owner $listener.OwningProcess)){throw "Port $port belongs to an unverified process. Nothing was stopped."}
        $targets+=[int]$listener.OwningProcess
    }
}
# Refuse to interrupt imports or collection replacement. No real collection is deleted.
if($targets.Count -or $IncludeDatabases) {
    $previousPassword=$env:MYSQL_PWD
    $env:MYSQL_PWD=$env:MYSQL_PASSWORD
    try {
        $active=& (Join-Path $MySqlHome 'bin/mysql.exe') --protocol=TCP --host=127.0.0.1 "--port=$($env:MYSQL_PORT)" "--user=$($env:MYSQL_USER)" "--database=$($env:MYSQL_DATABASE)" --batch --skip-column-names --execute="SELECT (SELECT count(*) FROM import_task WHERE status IN ('PENDING','RUNNING'))+(SELECT count(*) FROM viewer_binding WHERE sync_status='RUNNING')" 2>$null
        if($LASTEXITCODE -ne 0 -or [int]$active -ne 0){throw 'Cannot confirm idle databases. Finish active import/sync tasks before stopping.'}
        if($IncludeDatabases) {
            # MySQL on Windows may expose the filesystem path as ANSI bytes,
            # even with a UTF-8 connection. Read hex to avoid lossy transcoding.
            $dataHex=& (Join-Path $MySqlHome 'bin/mysql.exe') --protocol=TCP --host=127.0.0.1 "--port=$($env:MYSQL_PORT)" "--user=$($env:MYSQL_USER)" --batch --raw --skip-column-names --execute='SELECT HEX(@@datadir)' 2>$null
            if($LASTEXITCODE -ne 0){throw 'MySQL data directory could not be read. Nothing was stopped.'}
            $bytes=[Convert]::FromHexString($dataHex.Trim())
            [Text.Encoding]::RegisterProvider([Text.CodePagesEncodingProvider]::Instance)
            $encodings=@([Text.Encoding]::UTF8,[Text.Encoding]::GetEncoding((Get-WinSystemLocale).TextInfo.ANSICodePage))
            $expected=Resolve-ProjectPath (Join-Path $root '.local/mysql/data')
            $matches=@($encodings|Where-Object {(Resolve-ProjectPath ($_.GetString($bytes))) -eq $expected})
            if(-not $matches.Count){throw 'MySQL data directory ownership is unverified. Nothing was stopped.'}
        }
    } finally {$env:MYSQL_PWD=$previousPassword}
}
$neoTarget=$null
if($IncludeDatabases) {
    $neoId=[int](Get-Content -LiteralPath (Join-Path $root '.local/neo4j-launcher.pid'))
    $neoTarget=Find-Process $neoId
    if(-not $neoTarget -or -not(Test-Owner $neoId) -or $neoTarget.CommandLine -notmatch 'org\.neo4j\.server\.CommunityEntryPoint'){throw 'Local Neo4j ownership is unverified. Nothing was stopped.'}
    # mysqladmin connects specifically to the configured project database instance.
    $mysqlConfig=Get-Content -LiteralPath (Join-Path $root '.local/mysql/my.ini') -Raw
    if($mysqlConfig -notmatch "(?m)^port=$($env:MYSQL_PORT)\s*$"){throw 'Project MySQL configuration does not match. Nothing was stopped.'}
}
foreach($target in ($targets|Select-Object -Unique)) {Stop-Process -Id $target; Write-Output "Stopped project application process $target"}
# Launcher trees are checked independently; never terminate unrelated shells.
foreach($name in @('backend','frontend')) {
    $file=Join-Path $root ".local/$name-launcher.pid"
    if(Test-Path -LiteralPath $file) {
        $launcherId=[int](Get-Content -LiteralPath $file)
        if((Find-Process $launcherId) -and (Test-Owner $launcherId)) {
            $descendants=@($launcherId)
            for($depth=0;$depth -lt 5;$depth++) {$children=@($processes|Where-Object {$_.ParentProcessId -in $descendants -and $_.ProcessId -notin $descendants}|ForEach-Object ProcessId);if(-not $children.Count){break};$descendants+=$children}
            foreach($childId in ($descendants|Sort-Object -Descending)) {Stop-Process -Id $childId -ErrorAction SilentlyContinue}
        }
    }
}
if($IncludeDatabases) {
    $previousPassword=$env:MYSQL_PWD;$env:MYSQL_PWD=$env:MYSQL_ROOT_PASSWORD
    try {
        & (Join-Path $MySqlHome 'bin/mysqladmin.exe') --protocol=TCP --host=127.0.0.1 "--port=$($env:MYSQL_PORT)" --user=root shutdown
        if($LASTEXITCODE -ne 0){throw 'Project MySQL shutdown failed.'}
    } finally {$env:MYSQL_PWD=$previousPassword}
    Stop-Process -Id $neoTarget.ProcessId
    Write-Output 'Project-local databases stopped; data directories retained.'
}
Write-Output 'Application stop complete. Compose databases, if used, are managed separately with docker compose stop.'
