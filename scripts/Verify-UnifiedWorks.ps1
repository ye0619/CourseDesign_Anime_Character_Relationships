[CmdletBinding()]
param([long]$SubjectId=373267,[string]$Base='http://127.0.0.1:8080/api/v1')
$ErrorActionPreference='Stop'
if($Base -notmatch '^http://(?:127\.0\.0\.1|localhost):[0-9]+/api/v1$'){throw 'Local application only'}
$root=Split-Path $PSScriptRoot -Parent
$records=[Collections.Generic.List[object]]::new()
$session=[Microsoft.PowerShell.Commands.WebRequestSession]::new()
$before=Invoke-RestMethod "$Base/explore/anime/$SubjectId" -WebSession $session -TimeoutSec 20
$csrf=Invoke-RestMethod "$Base/session/csrf" -WebSession $session -TimeoutSec 10
$headers=@{};$headers[$csrf.data.headerName]=$csrf.data.token
$denied=Invoke-WebRequest "$Base/explore/anime/$SubjectId/saved" -Method Post -WebSession $session -SkipHttpErrorCheck -TimeoutSec 10
if([int]$denied.StatusCode -ne 403){throw 'Save without CSRF must fail'}
$request=Invoke-WebRequest "$Base/explore/anime/$SubjectId/saved?refresh=true" -Method Post -WebSession $session -Headers $headers -TimeoutSec 15
if([int]$request.StatusCode -ne 202){throw 'Save was not accepted'}
[void](Invoke-RestMethod "$Base/explore/anime/$SubjectId/saved?refresh=true" -Method Post -WebSession $session -Headers $headers -TimeoutSec 15)
$state=$null
for($attempt=0;$attempt -lt 100;$attempt++){
    $state=Invoke-RestMethod "$Base/explore/anime/$SubjectId/saved" -WebSession $session -TimeoutSec 10
    if($state.data.status -notin @('PENDING','RUNNING')){break}
    Start-Sleep -Milliseconds 1000
}
if(-not $state.data.saved -or $state.data.status -in @('PENDING','RUNNING','FAILED')){throw "Save did not produce usable data: $($state.data.status)"}
$detail=Invoke-RestMethod "$Base/explore/anime/$SubjectId" -WebSession $session -TimeoutSec 10
$graph=Invoke-RestMethod "$Base/explore/anime/$SubjectId/graph" -WebSession $session -TimeoutSec 20
$cast=Invoke-RestMethod "$Base/explore/anime/$SubjectId/credits?kind=CAST&page=1" -WebSession $session -TimeoutSec 10
$staff=Invoke-RestMethod "$Base/explore/anime/$SubjectId/credits?kind=STAFF&page=1" -WebSession $session -TimeoutSec 10
if($detail.data.dataSource -ne 'LOCAL' -or $detail.data.entity.uid -ne "anime:bangumi:$SubjectId"){throw 'Saved work must use unified local-first detail'}
$ids=@($graph.data.nodes|ForEach-Object id)
foreach($edge in $graph.data.edges){if($edge.source -notin $ids -or $edge.target -notin $ids -or -not $edge.evidence -or -not $edge.sourceUrl){throw 'Unscoped or untraceable edge'}}
$records.Add(@{subjectId=$SubjectId;before=$before.data.dataSource;after=$detail.data.dataSource;status=$state.data.status;notice=$state.data.error;characters=@($graph.data.nodes).Count;relations=@($graph.data.edges).Count;castRows=$cast.data.coverage.total;staffRows=$staff.data.coverage.total;csrfRejected=$true;duplicateRequestAccepted=$true;checkedAt=[DateTime]::UtcNow.ToString('o')})
$records|ConvertTo-Json -Depth 6|Set-Content (Join-Path $root '.local/unified-work-acceptance.json') -Encoding utf8
$records|ConvertTo-Json -Depth 6
