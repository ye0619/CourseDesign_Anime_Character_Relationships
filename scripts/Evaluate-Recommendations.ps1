[CmdletBinding()]
param([string]$JavaHome,[string]$MySqlHome='D:\Tools\mysql-9.3.0-winx64')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Project-Environment.ps1')
Import-ProjectEnvironment -Root $root
Select-ProjectJava -RequestedJavaHome $JavaHome
$library=Join-Path $root '.local/10.21-evaluation-library.json'
if(-not(Test-Path -LiteralPath $library)){throw 'Run Verify-Iteration1021.mjs first to capture the intentionally public sai example.'}
$candidateFile=Join-Path $root '.local/10.21-evaluation-candidates.json'
$env:MYSQL_PWD=$env:MYSQL_PASSWORD
try {
    $rows=& (Join-Path $MySqlHome 'bin/mysql.exe') --protocol=TCP --host=127.0.0.1 "--port=$($env:MYSQL_PORT)" "--user=$($env:MYSQL_USER)" "--database=$($env:MYSQL_DATABASE)" --default-character-set=utf8mb4 --raw --batch --skip-column-names --execute="SELECT payload FROM recommendation_candidate WHERE JSON_UNQUOTE(JSON_EXTRACT(payload,'$.datasetVersion'))='bangumi-ranked-300/3' ORDER BY subject_id LIMIT 1000"
    if($LASTEXITCODE -ne 0){throw 'Public candidate snapshot read failed.'}
    [IO.File]::WriteAllText($candidateFile,('['+($rows -join ',')+']'),[Text.UTF8Encoding]::new($false))
} finally {Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue}
# Reuse the exact Jackson runtime already packaged by this project.
$lib=Join-Path $root '.local/evaluation-libs'
New-Item -ItemType Directory -Path $lib -Force|Out-Null
$jar=Join-Path $root 'backend/target/animegraph-backend-0.1.0-SNAPSHOT.jar'
$archive=[IO.Compression.ZipFile]::OpenRead($jar)
try {foreach($entry in $archive.Entries){if($entry.FullName -match '^BOOT-INF/lib/jackson-(core|annotations|databind)-[^/]+\.jar$'){[IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $lib $entry.Name),$true)}}}finally{$archive.Dispose()}
& (Join-Path $env:JAVA_HOME 'bin/java.exe') --class-path "$lib/*;$(Join-Path $root 'backend/target/classes')" (Join-Path $PSScriptRoot 'RecommendationEvaluation.java') $library $candidateFile (Join-Path $root 'data/samples/10.21-recommendation-evaluation.json')
if($LASTEXITCODE -ne 0){throw 'Recommendation evaluation failed.'}
