[CmdletBinding()]
param([string]$RecordPath)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Project-Environment.ps1')
Import-ProjectEnvironment -Root $root
$stamp=Get-Content -LiteralPath (Join-Path $root '.local/artifact-build.json') -Raw|ConvertFrom-Json
$jar=Join-Path $root 'backend/target/animegraph-backend-0.1.0-SNAPSHOT.jar'
$jarHash=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
if($jarHash -ne ($stamp.artifacts|Where-Object path -eq 'backend/target/animegraph-backend-0.1.0-SNAPSHOT.jar').sha256){throw 'Running build source artifact changed.'}
$backendOwner=(Get-NetTCPConnection -State Listen -LocalPort ([int]$env:SERVER_PORT)|Select-Object -First 1).OwningProcess
$frontendOwner=(Get-NetTCPConnection -State Listen -LocalPort 5173|Select-Object -First 1).OwningProcess
$backendProcess=Get-CimInstance Win32_Process -Filter "ProcessId=$backendOwner"
$frontendProcess=Get-CimInstance Win32_Process -Filter "ProcessId=$frontendOwner"
if($backendProcess.CommandLine -notmatch '\s-jar\s' -or $backendProcess.CommandLine -notmatch 'animegraph-backend-0.1.0-SNAPSHOT.jar' -or $frontendProcess.CommandLine -notmatch '\bpreview\b'){throw 'Runtime is not packaged backend plus built frontend preview.'}
$page=Invoke-WebRequest 'http://127.0.0.1:5173/anime' -TimeoutSec 10
$pageHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($page.Content))).ToLowerInvariant()
if($pageHash -ne ($stamp.artifacts|Where-Object path -eq 'frontend/dist/index.html').sha256 -or $page.Content -match '/@vite/client'){throw 'Frontend does not match the stamped production index.'}
$ready=(Invoke-RestMethod 'http://127.0.0.1:5173/api/v1/health/ready' -TimeoutSec 10).data
if($ready.status -ne 'READY'){throw 'Packaged application is not ready.'}
$names=Invoke-WebRequest 'http://127.0.0.1:5173/api/v1/admin/external-identities/1/names' -SkipHttpErrorCheck -TimeoutSec 10
if([int]$names.StatusCode -ne 401){throw 'Default-disabled maintenance boundary changed.'}
Add-Type -AssemblyName System.IO.Compression
$archive=[IO.Compression.ZipFile]::OpenRead($jar)
try {
    foreach($entry in @('BOOT-INF/classes/db/migration/V9__saved_anime.sql','BOOT-INF/classes/com/animegraph/explore/SavedAnimeService.class','BOOT-INF/classes/com/animegraph/explore/BangumiArchiveRelationProvider.class','BOOT-INF/classes/com/animegraph/identity/IdentityNameService.class')) {
        if(-not $archive.GetEntry($entry)){throw 'Unified feature missing from packaged JAR.'}
    }
    if(@($archive.Entries|Where-Object {$_.FullName -match '^BOOT-INF/classes/com/animegraph/curated/'}).Count){throw 'Retired curated module remains in JAR.'}
}finally{$archive.Dispose()}
$detail=(Invoke-RestMethod 'http://127.0.0.1:5173/api/v1/explore/anime/373267' -TimeoutSec 15).data
$graph=(Invoke-RestMethod 'http://127.0.0.1:5173/api/v1/explore/anime/373267/graph' -TimeoutSec 15).data
if($detail.dataSource -ne 'LOCAL' -or $graph.nodes.Count -eq 0 -or $graph.edges.Count -eq 0){throw 'Saved work is not available through unified runtime.'}
$removed=Invoke-WebRequest 'http://127.0.0.1:5173/api/v1/curated' -SkipHttpErrorCheck -TimeoutSec 10
if([int]$removed.StatusCode -ne 404){throw 'Retired curated business API still exists.'}
$record=[ordered]@{checkedAt=[DateTime]::UtcNow.ToString('o');passed=$true;mode='Release';backendExecutableJar=$true;frontendBuiltPreview=$true;backendSha256=$jarHash;indexMatchesBuild=$true;ready='READY';anonymousNameMaintenance=401;mysqlMigrationV9Bundled=$true;curatedModuleRemoved=$true;unifiedSavedSubject=373267;characters=$graph.nodes.Count;relations=$graph.edges.Count;scope='Current local runtimes and existing databases; not fresh-machine or Docker acceptance'}
if(-not $RecordPath){$RecordPath=Join-Path $root '.local/delivery-runtime.json'}
[IO.File]::WriteAllText([IO.Path]::GetFullPath($RecordPath),($record|ConvertTo-Json -Depth 5),[Text.UTF8Encoding]::new($false))
Write-Output 'Current executable JAR and stamped built frontend are running; proxy READY and maintenance default disabled.'
