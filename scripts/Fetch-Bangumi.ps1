[CmdletBinding()]
param(
    [int[]]$SubjectIds = @(12,253,10440,302,9717,899,1609,265,2734,24790,326868,326895,160209,302286,330055,429644,400602,219200,273843,876),
    [switch]$Refresh,
    [ValidatePattern('^[a-z0-9][a-z0-9-]{0,79}$')][string]$PackageName = 'bangumi-sample',
    [string]$Proxy
)
$ErrorActionPreference='Stop'
if($SubjectIds.Count -gt 50){throw 'At most 50 explicit subject IDs are allowed.'}
$root=Split-Path $PSScriptRoot -Parent
$raw=Join-Path $root 'data/raw/bangumi'
$output=Join-Path $root 'data/normalized'
New-Item -ItemType Directory -Force -Path $raw,$output | Out-Null
$headers=@{'User-Agent'='AnimeGraphGraduation/0.3 (local graduation project; finite dataset import)'}
$entities=@{}
$relations=[Collections.Generic.List[object]]::new()
$provenance=[Collections.Generic.List[object]]::new()
$excluded=0
function Get-BangumiJson([string]$ApiPath) {
    $cache=Join-Path $raw (($ApiPath.Trim('/') -replace '/','-')+'.json')
    if((Test-Path -LiteralPath $cache) -and -not $Refresh){
        $provenance.Add(@{url=('https://api.bgm.tv'+$ApiPath);cacheHit=$true;cacheWrittenAt=(Get-Item -LiteralPath $cache).LastWriteTimeUtc.ToString('o');status=200})
        return Get-Content -LiteralPath $cache -Raw -Encoding utf8|ConvertFrom-Json
    }
    for($attempt=0;$attempt -lt 3;$attempt++) {
        try {
            $request=@{Uri=('https://api.bgm.tv'+$ApiPath);Headers=$headers;TimeoutSec=25}
            if($Proxy){$request.Proxy=$Proxy}
            $response=Invoke-WebRequest @request
            [IO.File]::WriteAllText($cache,$response.Content,[Text.UTF8Encoding]::new($false))
            $provenance.Add(@{url=('https://api.bgm.tv'+$ApiPath);fetchedAt=[DateTime]::UtcNow.ToString('o');status=[int]$response.StatusCode})
            Start-Sleep -Seconds 1
            return $response.Content|ConvertFrom-Json
        }catch{
            if($attempt -eq 2){throw}
            $delay=[Math]::Pow(2,$attempt+1)
            if($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 429){
                $delay=10
                $retryAfter=$_.Exception.Response.Headers.RetryAfter
                if($retryAfter.Delta){$delay=[Math]::Max($delay,[int]$retryAfter.Delta.TotalSeconds)}
                elseif($retryAfter.Date){$delay=[Math]::Max($delay,[int]($retryAfter.Date.UtcDateTime-[DateTime]::UtcNow).TotalSeconds)}
            }
            Start-Sleep -Seconds ([int]$delay)
        }
    }
}
function Add-Entity([string]$Type,$Item) {
    $id=[string]$Item.id
    $key="$Type`:$id"
    if($entities.ContainsKey($key)){return}
    $node=@{type=$Type;sourceId=$id;name=[string]$Item.name}
    if($Item.name_cn){$node.nameCn=[string]$Item.name_cn}
    if($Item.short_summary){$node.summary=([string]$Item.short_summary).Substring(0,[Math]::Min(20000,([string]$Item.short_summary).Length))}
    if($Item.images -and $Item.images.medium){$node.imageUrl=[string]$Item.images.medium}
    $entities[$key]=$node
}
function Reference([string]$Type,$Id){return @{type=$Type;sourceId=[string]$Id}}
$roleMap=@{'监督'='DIRECTOR';'脚本'='SCRIPT';'演出'='EPISODE_DIRECTOR';'音乐'='MUSIC';'人物设定'='CHARACTER_DESIGN';'原作'='ORIGINAL_CREATOR';'系列构成'='SERIES_COMPOSITION'}
$workCount=0
$failures=[Collections.Generic.List[object]]::new()
foreach($subjectId in ($SubjectIds|Select-Object -Unique)) {
    try {
        $subject=Get-BangumiJson "/v0/subjects/$subjectId"
        if($subject.type -ne 2){Write-Output "Skipped non-anime subject $subjectId";continue}
        $staff=Get-BangumiJson "/v0/subjects/$subjectId/persons"
        $characters=Get-BangumiJson "/v0/subjects/$subjectId/characters"
        Add-Entity 'Anime' $subject
        $anime=$entities["Anime:$subjectId"]
        if($subject.summary){$anime.summary=([string]$subject.summary).Substring(0,[Math]::Min(20000,([string]$subject.summary).Length))}
        if($subject.date -match '^\d{4}-\d{2}-\d{2}$'){$anime.airDate=[string]$subject.date;$anime.year=[int]$subject.date.Substring(0,4)}
        if($subject.platform){$anime.format=[string]$subject.platform}
        foreach($person in $staff) {
            if($person.type -ne 1){$excluded++;continue}
            Add-Entity 'Person' $person
            $role=[string]$person.relation
            if(-not $role){continue}
            if($roleMap.ContainsKey($role)){$code=$roleMap[$role]}else{$hash=[Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($role));$code='BGM_'+[Convert]::ToHexString($hash).Substring(0,16)}
            $relations.Add(@{type='PARTICIPATED_IN';from=(Reference 'Person' $person.id);to=(Reference 'Anime' $subjectId);properties=@{roleCode=$code;roleName=$role}})
        }
        foreach($character in $characters) {
            Add-Entity 'Character' $character
            $relations.Add(@{type='APPEARS_IN';from=(Reference 'Character' $character.id);to=(Reference 'Anime' $subjectId);properties=@{characterRole=[string]$character.relation}})
            foreach($actor in @($character.actors)) {
                if(-not $actor -or $actor.type -ne 1){continue}
                Add-Entity 'Person' $actor
                $relations.Add(@{type='VOICED';from=(Reference 'Person' $actor.id);to=(Reference 'Character' $character.id);properties=@{animeUid="anime:bangumi:$subjectId";scopeKnown=$true;sourceName='Bangumi作品内角色配音'}})
            }
        }
        $workCount++
        Write-Output "Collected anime $subjectId ($workCount works)"
    } catch {$failures.Add(@{subjectId=$subjectId;message=$_.Exception.Message});Write-Output "Failed subject $subjectId"}
}
$package=@{schemaVersion=1;source='bangumi';generatedAt=[DateTime]::UtcNow.ToString('o');entities=@($entities.Values|Sort-Object type,sourceId);relations=@($relations.ToArray())}
$packagePath=Join-Path $output "$PackageName.json"
[IO.File]::WriteAllText($packagePath,($package|ConvertTo-Json -Depth 15),[Text.UTF8Encoding]::new($false))
$provenanceName=if($PackageName -eq 'bangumi-sample'){'provenance.json'}else{"$PackageName-provenance.json"}
@{workCount=$workCount;entityCount=$entities.Count;relationCount=$relations.Count;excludedOrganizations=$excluded;failures=@($failures.ToArray());requests=@($provenance.ToArray())}|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $raw $provenanceName) -Encoding utf8
Write-Output "Saved $workCount works, $($entities.Count) entities and $($relations.Count) relations to data/normalized/$PackageName.json"
if($workCount -eq 0){throw 'No anime was collected.'}
