[CmdletBinding()]
param([string]$Base='http://127.0.0.1:8080/api/v1',[switch]$Rebuild)
$ErrorActionPreference='Stop'
if($Base -notmatch '^http://(?:127\.0\.0\.1|localhost):[0-9]+/api/v1$'){throw 'Local application only'}
$root=Split-Path $PSScriptRoot -Parent
$out=Join-Path $root '.local/iteration-1024-2'
New-Item -ItemType Directory -Path $out -Force|Out-Null
$session=[Microsoft.PowerShell.Commands.WebRequestSession]::new()
$csrf=(Invoke-RestMethod "$Base/session/csrf" -WebSession $session).data
$headers=@{};$headers[$csrf.headerName]=$csrf.token
function Read-Api([string]$Path){(Invoke-RestMethod "$Base$Path" -WebSession $session).data}
function Write-Api([string]$Path,[string]$Method,$Body){(Invoke-RestMethod "$Base$Path" -WebSession $session -Headers $headers -Method $Method -ContentType 'application/json' -Body ($Body|ConvertTo-Json -Depth 40 -Compress)).data}
function Expect-Status([scriptblock]$Action,[int]$Status){try{& $Action;throw "Expected HTTP $Status"}catch{if([int]$_.Exception.Response.StatusCode -ne $Status){throw}}}
$coverage=Read-Api '/connections/coverage'
if(-not $coverage.graphRevision){throw 'Construct a public connection scope first'}
$records=[ordered]@{checkedAt=[DateTime]::UtcNow.ToString('o');scope=$coverage;checks=@{}}
$start=@(Read-Api ('/connections/characters?q='+[Uri]::EscapeDataString('后藤一里')))|Select-Object -First 1
if(-not $start){throw 'Actual source character not found'}
$other=@(Read-Api '/connections/characters?subjectId=1424')|Where-Object {$_.characterUid -ne $start.characterUid}|Select-Object -First 1
if(-not $other){throw 'A second actual work is required'}
$body=@{from=@{characterUid=$start.characterUid;subjectId=$start.subjectId};to=@{characterUid=$other.characterUid;subjectId=$other.subjectId};maxHops=8;limit=10;spoilers=$false;storyDirection='CONNECTIVITY';includeManualRelations=$false;includeEnded=$true;graphRevision=$coverage.graphRevision}
$result=Write-Api '/connections/searches' 'Post' $body
for($i=0;$i -lt 40 -and $result.status -eq 'RUNNING';$i++){Start-Sleep -Milliseconds 250;$result=Read-Api "/connections/searches/$($result.queryId)"}
if($result.status -eq 'RUNNING'){throw 'Search exceeded its advertised bounded window'}
if($result.graphRevision -ne $coverage.graphRevision){throw 'Revision changed during search'}
foreach($path in $result.paths){if(@($path.nodes|Where-Object {$_.kind -notin @('CHARACTER','VOICE_ACTOR')}).Count){throw 'Forbidden node in path'};if(@($path.nodes.uid|Sort-Object -Unique).Count -ne @($path.nodes).Count){throw 'Repeated logical UID'};if($path.hopCount -ne @($path.edges).Count){throw 'Step count mismatch'}}
if(-not $result.searchExhausted -and $null -ne $result.exactPathCount){throw 'False exact count'}
$records.checks.pathsAndScope=$true;$records.example=$result
$foreign=[Microsoft.PowerShell.Commands.WebRequestSession]::new()
Expect-Status {Invoke-RestMethod "$Base/connections/searches/$($result.queryId)" -WebSession $foreign} 404
$records.checks.sessionIsolation=$true
$same=$body.Clone();$same.to=$same.from
Expect-Status {Write-Api '/connections/searches' 'Post' $same} 400
$records.checks.sameCharacterRejected=$true
$endpoint='/character-edits/'+$start.characterUid
$before=Read-Api $endpoint
$before|ConvertTo-Json -Depth 30|Set-Content -LiteralPath (Join-Path $out 'character-edit-before.json') -Encoding utf8
$tag='acceptance-'+[Guid]::NewGuid().ToString('N')
try {
    $fields=@{};$before.fields.PSObject.Properties|ForEach-Object {$fields[$_.Name]=$_.Value}
    $fields.notes=$tag
    $fields.names=@(@{id='acceptance-main';text=$start.name;locale='und';kind='PRIMARY';source='LOCAL';reviewStatus='MANUAL'},@{id='acceptance-alias';text=$tag;locale='und';kind='ALIAS';source='LOCAL';reviewStatus='MANUAL'})
    $fields.preferredNameId='acceptance-main'
    $next=@{uid=$before.uid;version=$before.version;schemaVersion=1;sourceVersion='acceptance-public-source';fields=$fields;portraits=$before.portraits}
    $changed=Write-Api $endpoint 'Put' $next
    if($changed.version -ne $before.version+1){throw 'Optimistic version not advanced'}
    $matches=@(Read-Api ('/connections/characters?q='+$tag))
    if(-not @($matches|Where-Object {$_.characterUid -eq $start.characterUid}).Count){throw 'Global alias not searchable'}
    Expect-Status {Write-Api $endpoint 'Put' $next} 409
    $backups=@(Read-Api ($endpoint+'/backups'))
    if(-not $backups.Count){throw 'Character backup missing'}
    $records.checks.globalAlias=$true;$records.checks.conflict=$true;$records.checks.backup=$true
} finally {
    $latest=Read-Api $endpoint
    if($changed -and $latest.version -ne $changed.version){throw 'Concurrent edit detected; current character data preserved, original backup available in the private acceptance folder'}
    $restore=@{uid=$before.uid;version=$latest.version;schemaVersion=1;sourceVersion=$before.sourceVersion;fields=$before.fields;portraits=$before.portraits}
    $restored=Write-Api $endpoint 'Put' $restore
    if(($restored.fields|ConvertTo-Json -Depth 20 -Compress) -ne ($before.fields|ConvertTo-Json -Depth 20 -Compress)){throw 'Character original override not restored'}
    $records.checks.characterRestored=$true
}
if($Rebuild){
    $watch=[Diagnostics.Stopwatch]::StartNew();$job=Write-Api '/connections/coverage/jobs' 'Post' @{subjectIds=@($coverage.subjects.subjectId)}
    for($i=0;$i -lt 600 -and $job.status -in @('PENDING','RUNNING');$i++){Start-Sleep -Milliseconds 200;$job=Read-Api "/connections/coverage/jobs/$($job.id)"}
    $watch.Stop();if($job.status -ne 'COMPLETE'){throw 'Projection rebuild did not complete'}
    $newCoverage=Read-Api '/connections/coverage';$old=Read-Api "/connections/searches/$($result.queryId)"
    if($old.graphRevision -ne $coverage.graphRevision -or $newCoverage.graphRevision -eq $coverage.graphRevision){throw 'Query did not retain its fixed revision'}
    $records.rebuiltScope=$newCoverage;$records.rebuildElapsedMillis=$watch.ElapsedMilliseconds;$records.checks.queryRetainedOldRevision=$true
}
$records.passed=$true
$records|ConvertTo-Json -Depth 50|Set-Content -LiteralPath (Join-Path $out 'runtime-acceptance.json') -Encoding utf8
Write-Output 'Real scope, bounded paths, session isolation, alias search, conflicts, independent backup and restoration passed.'
