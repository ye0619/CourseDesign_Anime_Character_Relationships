[CmdletBinding()]
param([string]$JavaHome,[string]$MySqlHome='D:/Tools/mysql-9.3.0-winx64')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Project-Environment.ps1')
Import-ProjectEnvironment -Root $root
Select-ProjectJava -RequestedJavaHome $JavaHome
if($env:MYSQL_DATABASE -notmatch '^[A-Za-z_][A-Za-z0-9_]*$' -or $env:MYSQL_URL -notmatch '^jdbc:mysql://(?:127\.0\.0\.1|localhost):[0-9]+/' -or $env:NEO4J_URI -notmatch '^bolt://(?:127\.0\.0\.1|localhost):[0-9]+/?$'){
    throw 'Recovery accepts only explicitly configured local databases with simple schema names.'
}
if(-not $env:MYSQL_ROOT_PASSWORD){throw 'Private local MySQL administrative credentials are required for an isolated recovery schema.'}
$sourceDatabase=$env:MYSQL_DATABASE
$run='recovery-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$backup=Join-Path $root ('.local/backups/'+$run)
$testRoot=Join-Path $root '.local/recovery-tests'
$test=Join-Path $testRoot $run
$testDatabase='animegraph_restore_'+[Guid]::NewGuid().ToString('N').Substring(0,12)
$neoHome=Join-Path $root '.local/tools/neo4j-community-5.26.16'
$runtimeJava=Join-Path $root '.local/tools/jdk-21.0.12.1+1-jre/bin/java.exe'
$classes=Join-Path $root '.local/recovery-helper'
foreach($path in @($backup,$test)){
    $resolved=[IO.Path]::GetFullPath($path)
    $allowed=[IO.Path]::GetFullPath((Join-Path $root '.local'))+[IO.Path]::DirectorySeparatorChar
    if(-not $resolved.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $resolved)){throw 'Recovery output must be a new directory inside this workspace private area.'}
}
foreach($path in @((Join-Path $MySqlHome 'bin/mysql.exe'),(Join-Path $MySqlHome 'bin/mysqldump.exe'),$runtimeJava,(Join-Path $neoHome 'lib/neo4j-5.26.16.jar'))){
    if(-not(Test-Path -LiteralPath $path -PathType Leaf)){throw 'Install the documented local MySQL, Neo4j and Java runtimes before recovery.'}
}
$recoveryLock=$null
try{$recoveryLock=[IO.File]::Open((Join-Path $root '.local/recovery.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)}
catch{throw 'Another recovery exercise is active; no application or database was changed.'}
try{
New-Item -ItemType Directory -Path $backup,$test,$classes,(Join-Path $test 'tmp') -Force|Out-Null
function Write-Json([string]$Path,$Value){[IO.File]::WriteAllText($Path,($Value|ConvertTo-Json -Depth 18),[Text.UTF8Encoding]::new($false))}
function Run-MySql([string[]]$Arguments,[string]$InputPath,[string]$InputText){
    $info=[Diagnostics.ProcessStartInfo]::new()
    $info.FileName=Join-Path $MySqlHome 'bin/mysql.exe'
    $info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $info.Environment['MYSQL_PWD']=$env:MYSQL_ROOT_PASSWORD
    foreach($item in @('--protocol=TCP','--host=127.0.0.1',("--port="+$env:MYSQL_PORT),'--user=root','--default-character-set=utf8mb4','--batch','--raw','--skip-column-names')+$Arguments){$info.ArgumentList.Add($item)}
    $process=[Diagnostics.Process]::new();$process.StartInfo=$info
    [void]$process.Start()
    $output=$process.StandardOutput.ReadToEndAsync();$errors=$process.StandardError.ReadToEndAsync()
    try{
        if($InputPath){
            $stream=[IO.File]::OpenRead($InputPath)
            try{$stream.CopyTo($process.StandardInput.BaseStream)}finally{$stream.Dispose()}
        }elseif($InputText){$process.StandardInput.Write($InputText)}
        $process.StandardInput.Close()
        if(-not $process.WaitForExit(120000)){$process.Kill();throw 'Isolated SQL operation exceeded its deadline.'}
        $out=$output.GetAwaiter().GetResult();$err=$errors.GetAwaiter().GetResult()
        if($process.ExitCode -ne 0){[IO.File]::WriteAllText((Join-Path $test 'mysql-private-error.log'),$err);throw 'Local SQL recovery failed; inspect the private recovery log.'}
        return $out.Trim()
    }finally{$process.Dispose()}
}
function Sql([string]$Statement){return Run-MySql -Arguments @(('--execute='+$Statement))}
function Run-Graph([string[]]$Arguments,[string]$Name){
    $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=$runtimeJava
    $info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    foreach($item in @('-Xmx1024m',("-Djava.io.tmpdir="+(Join-Path $test 'tmp')),("-Djna.tmpdir="+(Join-Path $test 'tmp')),'--add-opens=java.base/java.nio=ALL-UNNAMED','--add-opens=java.base/java.io=ALL-UNNAMED','--add-opens=java.base/sun.nio.ch=ALL-UNNAMED','-cp',$classpath,'GraphRecovery')+$Arguments){$info.ArgumentList.Add($item)}
    $process=[Diagnostics.Process]::new();$process.StartInfo=$info;[void]$process.Start()
    $output=$process.StandardOutput.ReadToEndAsync();$errors=$process.StandardError.ReadToEndAsync()
    try{
        if(-not $process.WaitForExit(180000)){$process.Kill();throw 'The isolated graph operation exceeded its deadline'}
        [IO.File]::WriteAllText((Join-Path $test ($Name+'-output.log')),$output.GetAwaiter().GetResult())
        [IO.File]::WriteAllText((Join-Path $test ($Name+'-error.log')),$errors.GetAwaiter().GetResult())
        if($process.ExitCode -ne 0){throw 'Graph recovery operation failed; inspect the private test logs. Source store was not opened or changed.'}
    }finally{$process.Dispose()}
}
function Statistics([string]$Database){
    if($Database -notmatch '^[A-Za-z_][A-Za-z0-9_]*$'){throw 'Invalid statistics schema'}
    $tables=(Sql ("SHOW TABLES FROM "+$Database)) -split '[\r\n]+'|Where-Object {$_}
    $result=[Collections.Generic.List[object]]::new()
    foreach($table in $tables){
        if($table -notmatch '^[A-Za-z_][A-Za-z0-9_]*$'){throw 'Unsupported table identifier in recovery'}
        $count=[long](Sql ("SELECT count(*) FROM "+$Database+'.'+$table))
        $checksum=(Sql ("CHECKSUM TABLE "+$Database+'.'+$table+" EXTENDED")).Split([char]9)[-1]
        if($checksum -notmatch '^[0-9]+$'){throw 'An actual table checksum is required'}
        $result.Add([ordered]@{table=$table;rows=$count;checksum=$checksum})
    }
    return $result.ToArray()
}
$hadApplication=@(Get-NetTCPConnection -State Listen -LocalPort @([int]$env:SERVER_PORT,5173) -ErrorAction SilentlyContinue).Count -gt 0
$createdSchema=$false
$record=$null
try{
    $busy=[int](Sql ("SELECT count(*) FROM "+$sourceDatabase+".public_metadata_task WHERE status IN ('PENDING','RUNNING')"))
    if($busy -ne 0){throw 'Finish active public metadata tasks before making a coordinated backup.'}
    & (Join-Path $PSScriptRoot 'Stop-Project.ps1') -MySqlHome $MySqlHome
    $unsupported=[int](Sql ("SELECT (SELECT count(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='"+$sourceDatabase+"' AND (TABLE_TYPE<>'BASE TABLE' OR ENGINE<>'InnoDB'))+(SELECT count(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA='"+$sourceDatabase+"')+(SELECT count(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA='"+$sourceDatabase+"')"))
    if($unsupported -ne 0){throw 'This logical recovery supports current InnoDB base tables; views, routines, triggers or other engines require a separate audited backup.'}
    $before=@(Statistics $sourceDatabase)
    $dump=Join-Path $backup 'mysql.sql'
    $previousPassword=$env:MYSQL_PWD
    try{
        $env:MYSQL_PWD=$env:MYSQL_ROOT_PASSWORD
        & (Join-Path $MySqlHome 'bin/mysqldump.exe') --protocol=TCP --host=127.0.0.1 ("--port="+$env:MYSQL_PORT) --user=root --default-character-set=utf8mb4 --single-transaction --skip-lock-tables --skip-add-locks --skip-triggers --set-gtid-purged=OFF --no-tablespaces --hex-blob ("--result-file="+$dump) $sourceDatabase 2> (Join-Path $test 'dump-private-error.log')
        if($LASTEXITCODE -ne 0){throw 'Private database dump failed'}
    }finally{$env:MYSQL_PWD=$previousPassword}
    $dumpText=[IO.File]::ReadAllText($dump)
    if($dumpText -match '(?im)^\s*(USE\s|CREATE\s+DATABASE\s|DROP\s+DATABASE\s|ALTER\s+DATABASE\s)'){throw 'Dump may switch schemas; isolated restore was refused'}
    & (Join-Path $env:JAVA_HOME 'bin/javac.exe') --release 21 -encoding UTF-8 -cp (Join-Path $neoHome 'lib/*') -d $classes (Join-Path $PSScriptRoot 'recovery/GraphRecovery.java')
    if($LASTEXITCODE -ne 0){throw 'Recovery helper compilation failed'}
    $classpath=$classes+[IO.Path]::PathSeparator+(Join-Path $neoHome 'lib/*')
    $graphFile=Join-Path $backup 'graph.json'
    Run-Graph -Arguments @('export',$graphFile) -Name 'graph-export'
    Copy-Item -LiteralPath (Join-Path $root 'data/curated/local-library.v1.json') -Destination (Join-Path $backup 'local-library.v1.json')
    Copy-Item -LiteralPath (Join-Path $root 'data/curated/local-library.archive.v1.json') -Destination (Join-Path $backup 'local-library.archive.v1.json')
    $files=@('mysql.sql','graph.json','local-library.v1.json','local-library.archive.v1.json')|ForEach-Object {
        [ordered]@{path=$_;bytes=(Get-Item -LiteralPath (Join-Path $backup $_)).Length;sha256=(Get-FileHash -LiteralPath (Join-Path $backup $_) -Algorithm SHA256).Hash.ToLowerInvariant()}
    }
    Write-Json (Join-Path $backup 'private-backup-manifest.json') ([ordered]@{schemaVersion=1;createdAt=[DateTime]::UtcNow.ToString('o');applicationStopped=$true;files=@($files);mysqlTables=$before;policy='Private full logical backup; contains account-scoped data and must never enter a public delivery package.'})
    foreach($file in $files){if((Get-FileHash -LiteralPath (Join-Path $backup $file.path) -Algorithm SHA256).Hash.ToLowerInvariant() -ne $file.sha256){throw 'Backup integrity changed before restore'}}
    if([int](Sql ("SELECT count(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='"+$testDatabase+"'")) -ne 0){throw 'Generated recovery schema already exists; refusing overwrite'}
    [void](Sql ("CREATE DATABASE "+$testDatabase+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"));$createdSchema=$true
    [void](Run-MySql -Arguments @('--binary-mode=1',('--database='+$testDatabase)) -InputPath $dump)
    $after=@(Statistics $testDatabase)
    if(($before|ConvertTo-Json -Compress) -ne ($after|ConvertTo-Json -Compress)){throw 'Restored SQL row counts or extended checksums differ'}
    Run-Graph -Arguments @('restore',$graphFile,(Join-Path $test 'neo4j-restored'),$testRoot) -Name 'graph-restore'
    Run-Graph -Arguments @('compare-source',$graphFile) -Name 'graph-source-unchanged'
    if(($before|ConvertTo-Json -Compress) -ne (@(Statistics $sourceDatabase)|ConvertTo-Json -Compress)){throw 'Source SQL changed during the recovery exercise'}
    $graph=Get-Content -LiteralPath (Join-Path $test 'graph-restore-result.json') -Raw|ConvertFrom-Json
    $record=[ordered]@{checkedAt=[DateTime]::UtcNow.ToString('o');passed=$true;mysqlTables=$before.Count;mysqlCountsAndChecksumsMatch=$true;graph=$graph;backupFolder=$backup;testFolder=$test;sourceDatabaseOverwritten=$false;sourceContentsUnchanged=$true;scope='Same-machine independent MySQL schema and isolated embedded Neo4j. Includes persisted graph reopen; not a second machine, empty-database application bootstrap or Docker test.'}
}finally{
    if($createdSchema){
        if($testDatabase -notmatch '^animegraph_restore_[a-f0-9]{12}$' -or $testDatabase -eq $sourceDatabase){throw 'Recovery schema cleanup target failed validation'}
        [void](Sql ("DROP DATABASE "+$testDatabase))
    }
    if($hadApplication){& (Join-Path $PSScriptRoot 'Start-Project.ps1') -Mode Release -SkipDatabases -JavaHome $env:JAVA_HOME -MySqlHome $MySqlHome}
}
if($record){
    $record.testSqlSchemaRemoved=$true;$record.applicationRestarted=$hadApplication
    if($hadApplication){
        $ready=(Invoke-RestMethod ("http://127.0.0.1:"+$env:SERVER_PORT+"/api/v1/health/ready") -TimeoutSec 10).data.status
        if($ready -ne 'READY'){throw 'Original application did not return READY after recovery'}
        $record.applicationReady='READY'
    }
    Push-Location $root
    try{$record.codeFingerprint=(& node --input-type=module -e 'import {codeFingerprint} from "./scripts/Prepare-Delivery.mjs"; console.log(codeFingerprint(process.cwd()))').Trim()}
    finally{Pop-Location}
    Write-Json (Join-Path $test 'private-recovery-result.json') $record
    Write-Json (Join-Path $root '.local/data-recovery-result.json') $record
    Write-Output 'SQL and full logical graph recovery verified independently; source contents unchanged, test SQL schema removed, original application READY.'
}
}finally{if($recoveryLock){$recoveryLock.Dispose()}}
