[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Preview','Apply','List','Disable','Revoke')][string]$Action,
    [string]$Path,[string]$PreviewId,[string]$BatchId,[string]$AssertionUid,[string]$Reason,
    [string]$BaseUrl='http://127.0.0.1:8080'
)
$ErrorActionPreference='Stop'
$origin=[Uri]$BaseUrl
if($origin.Scheme -notin @('http','https') -or $origin.Host -notin @('localhost','127.0.0.1','[::1]') -or $origin.UserInfo -or $origin.AbsolutePath -ne '/'){throw 'This local maintenance tool requires a loopback BaseUrl with no user info or path.'}
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Project-Environment.ps1')
Import-ProjectEnvironment -Root $root
if($Action -eq 'Preview'){
    if(-not $Path -or -not(Test-Path -LiteralPath $Path -PathType Leaf)){throw 'Preview requires an existing JSON file via -Path.'}
    $packageFile=Get-Item -LiteralPath $Path
    if($packageFile.Length -gt 512KB){throw 'Evidence package must not exceed 512KB.'}
}
if($Action -eq 'Apply' -and $PreviewId -notmatch '^[a-f0-9-]{36}$'){throw 'Apply requires the previewId returned by Preview.'}
if($Action -in @('Disable','Revoke') -and (-not $Reason -or $Reason.Length -gt 500)){throw 'Provide a 1-500 character -Reason.'}
if($Action -eq 'Disable' -and $BatchId -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'){throw 'Provide a valid -BatchId.'}
if($Action -eq 'Revoke' -and $AssertionUid -notmatch '^assertion:[a-f0-9]{64}$'){throw 'Provide a valid -AssertionUid.'}
if(-not $env:ADMIN_USERNAME -or -not $env:ADMIN_PASSWORD){throw 'Local maintenance credentials are not configured; no login request was sent.'}
# Session and CSRF values stay in memory and are never printed or saved.
try{
    $csrf=(Invoke-RestMethod "$BaseUrl/api/v1/auth/csrf" -SessionVariable evidenceSession -TimeoutSec 30).data
    $login=(Invoke-RestMethod "$BaseUrl/api/v1/auth/login" -Method Post -WebSession $evidenceSession -Headers @{$csrf.headerName=$csrf.token} -Body @{username=$env:ADMIN_USERNAME;password=$env:ADMIN_PASSWORD} -TimeoutSec 30).data
    if(-not $login.roles.Contains('ADMIN')){throw 'Administrator role required.'}
    $csrf=(Invoke-RestMethod "$BaseUrl/api/v1/auth/csrf" -WebSession $evidenceSession -TimeoutSec 30).data
    $headers=@{$csrf.headerName=$csrf.token}
    $url="$BaseUrl/api/v1/admin/evidence-imports"
    switch($Action){
        'Preview' {$response=Invoke-RestMethod "$url/preview" -Method Post -WebSession $evidenceSession -Headers $headers -Form @{file=$packageFile} -TimeoutSec 30}
        'Apply' {$response=Invoke-RestMethod $url -Method Post -WebSession $evidenceSession -Headers $headers -ContentType 'application/json; charset=utf-8' -Body (@{previewId=$PreviewId}|ConvertTo-Json) -TimeoutSec 30}
        'List' {$response=Invoke-RestMethod "$url`?limit=30" -WebSession $evidenceSession -TimeoutSec 30}
        'Disable' {$response=Invoke-RestMethod "$url/$BatchId/disable" -Method Post -WebSession $evidenceSession -Headers $headers -ContentType 'application/json; charset=utf-8' -Body (@{reason=$Reason}|ConvertTo-Json) -TimeoutSec 30}
        'Revoke' {$response=Invoke-RestMethod "$BaseUrl/api/v1/admin/relationship-assertions/$AssertionUid/revoke" -Method Post -WebSession $evidenceSession -Headers $headers -ContentType 'application/json; charset=utf-8' -Body (@{reason=$Reason}|ConvertTo-Json) -TimeoutSec 30}
    }
    $response.data|ConvertTo-Json -Depth 15
}catch{
    $status=if($_.Exception.Response){[int]$_.Exception.Response.StatusCode}else{0}
    throw "Evidence maintenance request failed (HTTP $status). Check the existing admin switch/role, preview expiry and application logs. Credentials were not printed or persisted."
}finally{
    if($evidenceSession){try{$csrf=(Invoke-RestMethod "$BaseUrl/api/v1/auth/csrf" -WebSession $evidenceSession -TimeoutSec 5).data;Invoke-RestMethod "$BaseUrl/api/v1/auth/logout" -Method Post -WebSession $evidenceSession -Headers @{$csrf.headerName=$csrf.token} -TimeoutSec 5|Out-Null}catch{}}
}
