[CmdletBinding()]
param([int]$SubjectId = 12)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$rawDir = Join-Path $root "data/raw/bangumi-$SubjectId"
$sampleDir = Join-Path $root 'data/samples'
New-Item -ItemType Directory -Force -Path $rawDir, $sampleDir | Out-Null
$headers = @{ 'User-Agent' = 'AnimeGraphGraduation/0.1 (local graduation project; manual source verification)' }
$requests = @(
    @{ Name = 'subject'; Path = "/v0/subjects/$SubjectId" },
    @{ Name = 'staff'; Path = "/v0/subjects/$SubjectId/persons" },
    @{ Name = 'characters'; Path = "/v0/subjects/$SubjectId/characters" }
)
$results = @{}
$provenance = @()
foreach ($request in $requests) {
    $url = 'https://api.bgm.tv' + $request.Path
    $response = Invoke-WebRequest -Uri $url -Headers $headers -TimeoutSec 30
    $response.Content | Set-Content -LiteralPath (Join-Path $rawDir ($request.Name + '.json')) -Encoding utf8
    $results[$request.Name] = $response.Content | ConvertFrom-Json
    $provenance += @{ name = $request.Name; url = $url; status = [int]$response.StatusCode; fetchedAt = [DateTime]::UtcNow.ToString('o') }
    Start-Sleep -Seconds 1
}
$staff = @($results['staff'])
$characters = @($results['characters'])
if ($staff.Count -gt 0) {
    $personId = $staff[0].id
    $url = "https://api.bgm.tv/v0/persons/$personId"
    $response = Invoke-WebRequest -Uri $url -Headers $headers -TimeoutSec 30
    $response.Content | Set-Content -LiteralPath (Join-Path $rawDir 'person.json') -Encoding utf8
    $person = $response.Content | ConvertFrom-Json
    $provenance += @{ name = 'person'; url = $url; status = [int]$response.StatusCode; fetchedAt = [DateTime]::UtcNow.ToString('o') }
}
$cast = @($characters | Where-Object { @($_.actors).Count -gt 0 } | Select-Object -First 1)
$snapshot = [ordered]@{
    source = 'bangumi'
    subject = @{ id = $results['subject'].id; name = $results['subject'].name; nameCn = $results['subject'].name_cn; type = $results['subject'].type; date = $results['subject'].date; platform = $results['subject'].platform }
    staffCount = $staff.Count
    characterCount = $characters.Count
    staffSample = @($staff | Select-Object -First 3 id, name, relation, career)
    personSample = if ($person) { @{ id = $person.id; name = $person.name; type = $person.type } } else { $null }
    castSample = @($cast | ForEach-Object { @{ id = $_.id; name = $_.name; relation = $_.relation; actors = @($_.actors | Select-Object id, name, type) } })
    provenance = $provenance
}
$snapshot | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $sampleDir 'bangumi-verification.json') -Encoding utf8
$snapshot | ConvertTo-Json -Depth 12

