[CmdletBinding()]
param([string]$MySqlHome = 'D:\Tools\mysql-9.3.0-winx64')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Project-Environment.ps1')
Import-ProjectEnvironment -Root $root
if (-not $env:MYSQL_PASSWORD -or -not $env:MYSQL_ROOT_PASSWORD -or -not $env:NEO4J_PASSWORD) { throw 'Initialize the project .env first.' }
if ($env:MYSQL_USER -notmatch '^[a-zA-Z0-9_]+$' -or $env:MYSQL_DATABASE -notmatch '^[a-zA-Z0-9_]+$') { throw 'Local database/user names must contain only letters, digits and underscores.' }
$local = Join-Path $root '.local'
$mysqlDir = Join-Path $local 'mysql'
$dataDir = Join-Path $mysqlDir 'data'
New-Item -ItemType Directory -Force -Path $mysqlDir,$dataDir | Out-Null
$config = Join-Path $mysqlDir 'my.ini'
$initFile = Join-Path $mysqlDir 'first-start.sql'
$readyMarker = Join-Path $mysqlDir 'configured'
$mysqlPort = if ($env:MYSQL_PORT) { [int]$env:MYSQL_PORT } else { 3307 }
function Test-LocalPort([int]$Port) {
    $client = [Net.Sockets.TcpClient]::new()
    try { return $client.ConnectAsync('127.0.0.1',$Port).Wait(500) -and $client.Connected } catch { return $false } finally { $client.Dispose() }
}
function Write-Utf8([string]$Path,[string]$Text) { [IO.File]::WriteAllText($Path,$Text,[Text.UTF8Encoding]::new($false)) }
function Sql-Quote([string]$Value) { return $Value.Replace('\','\\').Replace("'","''") }
if (-not (Test-LocalPort $mysqlPort)) {
    $configuration = @"
[mysqld]
basedir=$($MySqlHome.Replace('\','/'))
datadir=./data
port=$mysqlPort
bind-address=127.0.0.1
mysqlx=0
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
default-time-zone=+00:00
log-error=mysql-error.log
pid-file=mysql.pid
"@
    Write-Utf8 $config $configuration
    if (-not (Test-Path -LiteralPath (Join-Path $dataDir 'mysql'))) {
        Push-Location $mysqlDir
        try { & (Join-Path $MySqlHome 'bin/mysqld.exe') --defaults-file=my.ini --initialize-insecure } finally { Pop-Location }
        if ($LASTEXITCODE -ne 0) { throw 'Project-local MySQL initialization failed; see .local/mysql/mysql-error.log.' }
    }
    $pidFile=Join-Path $mysqlDir 'launcher.pid'
    if(Test-Path -LiteralPath $pidFile){
        $previousId=[int](Get-Content -LiteralPath $pidFile)
        $previous=Get-Process -Id $previousId -ErrorAction SilentlyContinue
        if($previous -and $previous.Path -eq (Join-Path $MySqlHome 'bin/mysqld.exe')){
            Write-Output 'Waiting for the previous project MySQL process to finish shutdown.'
            Wait-Process -Id $previousId -Timeout 30
        }
    }
    $arguments = @('--defaults-file=my.ini')
    if (-not (Test-Path -LiteralPath $readyMarker)) {
        $rootPassword = Sql-Quote $env:MYSQL_ROOT_PASSWORD
        $appPassword = Sql-Quote $env:MYSQL_PASSWORD
        $user = $env:MYSQL_USER
        $database = $env:MYSQL_DATABASE
        $sql = "ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';`nCREATE DATABASE IF NOT EXISTS ``$database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`n"
        foreach ($hostName in @('localhost','127.0.0.1')) {
            $sql += "CREATE USER IF NOT EXISTS '$user'@'$hostName' IDENTIFIED BY '$appPassword';`nGRANT ALL PRIVILEGES ON ``$database``.* TO '$user'@'$hostName';`n"
        }
        Write-Utf8 $initFile $sql
        $arguments += '--init-file=../first-start.sql'
    }
    $process = Start-Process -FilePath (Join-Path $MySqlHome 'bin/mysqld.exe') -ArgumentList $arguments -WorkingDirectory $mysqlDir -WindowStyle Hidden -PassThru
    $process.Id | Set-Content -LiteralPath (Join-Path $mysqlDir 'launcher.pid')
    for ($attempt=0;$attempt -lt 40;$attempt++) { if (Test-LocalPort $mysqlPort) { break }; Start-Sleep -Milliseconds 500 }
    if (-not (Test-LocalPort $mysqlPort)) { throw 'Project-local MySQL did not start; inspect its error log.' }
    $env:MYSQL_PWD = $env:MYSQL_PASSWORD
    try {
        $connected = $false
        for ($attempt=0;$attempt -lt 30;$attempt++) {
            $probeOutput = & (Join-Path $MySqlHome 'bin/mysql.exe') --protocol=TCP --host=127.0.0.1 "--port=$mysqlPort" "--user=$($env:MYSQL_USER)" "--database=$($env:MYSQL_DATABASE)" --execute='SELECT 1 AS connected' --silent 2>&1
            if ($LASTEXITCODE -eq 0) { $connected=$true; break }
            if ($process.HasExited) { break }
            Start-Sleep -Milliseconds 500
        }
        if (-not $connected) { throw 'Project MySQL credentials failed validation; inspect the private error log.' }
    } finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    'configured' | Set-Content -LiteralPath $readyMarker
    if (Test-Path -LiteralPath $initFile) { Remove-Item -LiteralPath $initFile }
    Write-Output "Project MySQL ready on 127.0.0.1:$mysqlPort"
} else { Write-Output "Port $mysqlPort already listens; no MySQL process or data was modified." }

$neo4jDir = Join-Path $local 'tools/neo4j-community-5.26.16'
$jreDir = Join-Path $local 'tools/jdk-21.0.12.1+1-jre'
if (-not (Test-Path -LiteralPath (Join-Path $neo4jDir 'bin/neo4j.ps1'))) { throw 'Run Install-LocalDatabases.ps1 before starting Neo4j.' }
if (-not (Test-LocalPort 7687)) {
    $env:JAVA_HOME = $jreDir
    $env:NEO4J_HOME = $neo4jDir
    $neoConfig = @"
server.default_listen_address=127.0.0.1
server.bolt.listen_address=127.0.0.1:7687
server.http.listen_address=127.0.0.1:7474
server.memory.heap.initial_size=256m
server.memory.heap.max_size=512m
server.memory.pagecache.size=256m
dbms.security.auth_enabled=true
"@
    Write-Utf8 (Join-Path $neo4jDir 'conf/neo4j.conf') $neoConfig
    if (-not (Test-Path -LiteralPath (Join-Path $neo4jDir 'data/dbms/auth.ini'))) {
        & (Join-Path $jreDir 'bin/java.exe') -cp (Join-Path $neo4jDir 'lib/*') org.neo4j.cli.AdminTool dbms set-initial-password $env:NEO4J_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Neo4j initial password setup failed.' }
    }
    # Direct entry point avoids the vendor Windows launcher's escaped Unicode argfile paths.
    $argsList = @('-Xms256m','-Xmx512m','--add-opens=java.base/java.nio=ALL-UNNAMED','--add-opens=java.base/java.io=ALL-UNNAMED','--add-opens=java.base/sun.nio.ch=ALL-UNNAMED','-Dfile.encoding=UTF-8','-cp',('"{0}"' -f (Join-Path $neo4jDir 'lib/*')),'org.neo4j.server.CommunityEntryPoint',('"--home-dir={0}"' -f $neo4jDir),('"--config-dir={0}"' -f (Join-Path $neo4jDir 'conf')))
    $tempDir=Join-Path $local 'tmp'
    New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
    $argsList=@(('"-Djava.io.tmpdir={0}"' -f $tempDir),('"-Djna.tmpdir={0}"' -f $tempDir))+$argsList
    $process = Start-Process -FilePath (Join-Path $jreDir 'bin/java.exe') -ArgumentList $argsList -WorkingDirectory $neo4jDir -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $local 'neo4j-console.log') -RedirectStandardError (Join-Path $local 'neo4j-error.log')
    $process.Id | Set-Content -LiteralPath (Join-Path $local 'neo4j-launcher.pid')
    for ($attempt=0;$attempt -lt 60;$attempt++) { if (Test-LocalPort 7687) { break }; Start-Sleep -Milliseconds 500 }
    if (-not (Test-LocalPort 7687)) { throw 'Neo4j did not start; inspect .local/neo4j-error.log and neo4j-console.log.' }
    Write-Output 'Project Neo4j ready on 127.0.0.1:7687'
} else { Write-Output 'Port 7687 already listens; no Neo4j process or data was modified.' }
