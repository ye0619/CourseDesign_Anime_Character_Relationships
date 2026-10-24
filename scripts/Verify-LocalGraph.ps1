[CmdletBinding()]
param([long]$SubjectId=373267,[switch]$RefreshOfficial,[string]$Base='http://127.0.0.1:8080/api/v1')
$ErrorActionPreference='Stop'
if($Base -notmatch '^http://(?:127\.0\.0\.1|localhost):[0-9]+/api/v1$'){throw 'Local application only'}
$root=Split-Path $PSScriptRoot -Parent
$endpoint="$Base/explore/anime/$SubjectId/local-graph"
$session=[Microsoft.PowerShell.Commands.WebRequestSession]::new()
$csrf=(Invoke-RestMethod "$Base/session/csrf" -WebSession $session).data
$headers=@{};$headers[$csrf.headerName]=$csrf.token
function Read-Local { (Invoke-RestMethod $endpoint -WebSession $session).data }
function Write-Local($document) { (Invoke-RestMethod $endpoint -WebSession $session -Headers $headers -Method Put -ContentType 'application/json' -Body ($document|ConvertTo-Json -Depth 20 -Compress)).data }
$before=Read-Local
$backup=Join-Path $root ('.local/backups/local-graph-acceptance-before-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'.json')
$before|ConvertTo-Json -Depth 25|Set-Content -LiteralPath $backup -Encoding utf8
$graph=(Invoke-RestMethod "$Base/explore/anime/$SubjectId/graph" -WebSession $session).data
$official=@($graph.nodes|Where-Object { $_.characterType -ne 4 })[0].id
if(-not $official){throw 'A real official character is required for mixed-endpoint acceptance'}
$suffix=[Guid]::NewGuid().ToString('N').Substring(0,8)
$character='local:entity:'+ [Guid]::NewGuid().ToString();$second='local:entity:'+ [Guid]::NewGuid().ToString();$organization='local:entity:'+ [Guid]::NewGuid().ToString()
function New-Entity($id,$kind,$name) { @{id=$id;kind=$kind;name=$name;imageUrl='';aliases=@();gender='';description='自动化功能验收，非原作资料，结束后恢复原状态';notes='temporary acceptance';organizationType=''} }
function New-Link($source,$target,$type,$label,$directed) { @{id=('local:link:'+ [Guid]::NewGuid().ToString());source=$source;target=$target;relationType=$type;label=$label;description='功能验收，不代表原作剧情';evidence='自动化功能验收，结束后恢复原状态';spoiler=$false;directed=$directed;notes='temporary acceptance'} }
$records=@{startedAt=[DateTime]::UtcNow.ToString('o');subjectId=$SubjectId;passed=$false}
try {
    $next=@{version=$before.version;schemaVersion=$before.schemaVersion;workUid=$before.workUid;nodes=@($before.nodes)+@((New-Entity $character 'CHARACTER' "验收临时角色-$suffix"),(New-Entity $second 'CHARACTER' "验收第二角色-$suffix"),(New-Entity $organization 'ORGANIZATION' "验收临时组织-$suffix"));edges=@($before.edges)+@((New-Link $character $organization 'MEMBER' '队长' $true),(New-Link $second $organization 'MEMBER' '成员' $true),(New-Link $character $second 'OTHER' '验收自定义' $false),(New-Link $character $official 'OTHER' '验收官方连接' $true))}
    $saved=Write-Local $next
    $read=Read-Local
    if(@($read.nodes).Count -ne @($before.nodes).Count+3 -or @($read.edges).Count -ne @($before.edges).Count+4){throw 'Local records were not persisted'}
    $records.crudCreated=$true;$records.multipleMembers=$true;$records.mixedOfficialEndpoint=$true
    $bad=@{version=$saved.version;schemaVersion=$before.schemaVersion;workUid=$saved.workUid;nodes=$saved.nodes;edges=@($saved.edges)+@((New-Link $character $official 'OTHER' '验收官方连接' $true))}
    $rejected=Invoke-WebRequest $endpoint -WebSession $session -Headers $headers -Method Put -ContentType 'application/json' -Body ($bad|ConvertTo-Json -Depth 20 -Compress) -SkipHttpErrorCheck
    if([int]$rejected.StatusCode -ne 400){throw 'Identical duplicate relationship was accepted'}
    $records.duplicateRejected=$true
    $stale=Invoke-WebRequest $endpoint -WebSession $session -Headers $headers -Method Put -ContentType 'application/json' -Body ($next|ConvertTo-Json -Depth 20 -Compress) -SkipHttpErrorCheck
    if([int]$stale.StatusCode -ne 409){throw 'Stale version was accepted'}
    $records.conflictRejected=$true
    if($RefreshOfficial){
        [void](Invoke-RestMethod "$Base/explore/anime/$SubjectId/saved?refresh=true" -WebSession $session -Headers $headers -Method Post)
        for($i=0;$i -lt 100;$i++) { $state=(Invoke-RestMethod "$Base/explore/anime/$SubjectId/saved" -WebSession $session).data;if($state.status -notin @('PENDING','RUNNING')){break};Start-Sleep -Milliseconds 750 }
        if($state.status -notin @('COMPLETE','PARTIAL')){throw 'Official refresh failed'}
        $afterRefresh=Read-Local
        if(($afterRefresh|ConvertTo-Json -Depth 20 -Compress) -ne ($read|ConvertTo-Json -Depth 20 -Compress)){throw 'Official refresh changed local extension'}
        $records.officialRefreshRetained=$true;$records.refreshStatus=$state.status
    }
    $current=Read-Local
    $deleted=Write-Local @{version=$current.version;schemaVersion=$before.schemaVersion;workUid=$current.workUid;nodes=$before.nodes;edges=$before.edges}
    $backups=(Invoke-RestMethod "$endpoint/backups" -WebSession $session).data
    $latest=@($backups)[0]
    $restore=(Invoke-RestMethod "$endpoint/backups/$($latest.id)" -WebSession $session).data
    if(@($restore.nodes).Count -ne @($saved.nodes).Count){throw 'Pre-delete backup was not preserved'}
    $restore.version=$deleted.version
    $restored=Write-Local $restore
    if(@($restored.nodes).Count -ne @($saved.nodes).Count){throw 'Restore failed'}
    $records.backupRestore=$true;$records.passed=$true
} finally {
    $current=Read-Local
    [void](Write-Local @{version=$current.version;schemaVersion=$before.schemaVersion;workUid=$current.workUid;nodes=$before.nodes;edges=$before.edges})
    $records.originalLocalStateRestored=$true;$records.finishedAt=[DateTime]::UtcNow.ToString('o')
    $records|ConvertTo-Json -Depth 8|Set-Content (Join-Path $root '.local/local-graph-http-acceptance.json') -Encoding utf8
}
$records|ConvertTo-Json -Depth 8
